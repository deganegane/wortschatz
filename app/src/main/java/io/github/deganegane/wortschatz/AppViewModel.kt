package io.github.deganegane.wortschatz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = true,
    val words: List<Word> = emptyList(),
    val cards: Map<Int, Card> = emptyMap(),
    val days: Map<Long, DayStat> = emptyMap(),
    val settings: Settings = Settings(),
    val voice: VoiceState = VoiceState.LOADING,
    val voices: List<VoiceInfo> = emptyList(),
    val session: Session? = null,
    val shadowing: Shadowing? = null,
    val grammar: GrammarRound? = null,
    val stories: List<Story> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val listen: ListenState = ListenState(),
    /** Undo stack of the placement test: rank and the card it had before. */
    val testUndo: List<Triple<Int, Card?, Card>> = emptyList(),
    val today: Long = today(),
    /** Size of the whole bundled list (the active [words] are the first level × 1000). */
    val allCount: Int = 0
) {
    private val byRank: Map<Int, Word> by lazy { words.associateBy { it.rank } }
    fun word(rank: Int): Word? = byRank[rank]

    /** The active frequency list without the conversational phrases. */
    val vocab: List<Word> by lazy { words.filter { !it.isPhrase } }
    val phrases: List<Word> by lazy { words.filter { it.isPhrase } }

    val counts: Map<Bucket, Int> by lazy {
        val m = HashMap<Bucket, Int>()
        for (w in vocab) {
            val b = cards[w.rank].bucket()
            m[b] = (m[b] ?: 0) + 1
        }
        m
    }

    /** Bucket counts over the frequency list (phrases are counted separately). */
    fun count(b: Bucket): Int = if (b == Bucket.ALL) vocab.size else counts[b] ?: 0

    val phrasesLearning: Int by lazy { phrases.count { cards[it.rank]?.status == Status.LEARNING } }

    val hardWords: List<Int> by lazy { Srs.hardWords(cards).filter { word(it) != null } }

    val hasGaps: Boolean by lazy { vocab.any { it.gap != null } }

    /** Words and phrases that could still be introduced as new cards. */
    val newAvailable: Int by lazy {
        count(Bucket.TO_LEARN) + count(Bucket.UNTESTED) +
            phrases.count { (cards[it.rank]?.status ?: Status.UNTESTED).let { s -> s == Status.UNTESTED || s == Status.TO_LEARN } }
    }

    val storiesRead: Int get() = stories.count { it.id in readIds }

    /** Known outright, mastered, or currently being learned. */
    val familiar: Int get() = count(Bucket.KNOWN) + count(Bucket.MASTERED) + count(Bucket.LEARNING)

    val tested: Int get() = vocab.size - count(Bucket.UNTESTED)

    /** Next word in the placement test, or null when everything has been tested. */
    val nextTestRank: Int? by lazy {
        vocab.firstOrNull { (cards[it.rank]?.status ?: Status.UNTESTED) == Status.UNTESTED }?.rank
    }

    val todayStat: DayStat get() = days[today] ?: DayStat()
    /** Due cards that belong to the active list (phrases turned off or a lowered level are skipped). */
    val dueToday: Int get() = cards.count { (r, c) -> c.status == Status.LEARNING && c.due <= today && word(r) != null }
    val newLeftToday: Int
        get() {
            val budget = (settings.newPerDay - todayStat.newCards).coerceAtLeast(0)
            return minOf(budget, newAvailable)
        }
    val streak: Int get() = Srs.streak(days, today)

    /** Levels the bundled list supports (4 with 4000 words). */
    val maxLevel: Int get() = (allCount + LEVEL_SIZE - 1) / LEVEL_SIZE

    /** Words of the active list you know or have mastered. */
    val solid: Int get() = count(Bucket.KNOWN) + count(Bucket.MASTERED)

    /** Needed for the next level. */
    val unlockTarget: Int get() = (vocab.size * UNLOCK_SHARE).toInt()

    val canUnlock: Boolean get() = settings.level < maxLevel && solid >= unlockTarget
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    /** The whole bundled list; [UiState.words] holds the active levels. */
    private var allWords: List<Word> = emptyList()
    private val _state = MutableStateFlow(UiState(settings = store.loadSettings()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val speech: Speech = Speech(app) { voice ->
        if (voice == VoiceState.READY) speech.chooseVoice(_state.value.settings.voiceName)
        _state.update { it.copy(voice = voice, voices = speech.voices) }
    }

    /** German voices on this phone (empty until the engine is ready). */
    val voiceOptions: List<VoiceInfo> get() = speech.voices

    /** Inflected form → rank, loaded the first time a story is opened. */
    private var forms: Map<String, Int>? = null
    private var formsLoading = false

    private enum class ListenFor { REVIEW, SHADOW }
    private var listenFor = ListenFor.REVIEW

    private val listener = Listener(
        app,
        onLevel = { level, partial ->
            _state.update { it.copy(listen = ListenState(ListenPhase.LISTENING, level, partial)) }
        },
        onResult = { results -> onHeard(results) },
        onError = { msg -> _state.update { it.copy(listen = ListenState(ListenPhase.ERROR, error = msg)) } }
    )

    val recognitionAvailable: Boolean by lazy { listener.available }

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Triple(WordRepo.load(getApplication()), store.loadCards(), store.loadDays())
            }
            val stories = withContext(Dispatchers.IO) { WordRepo.loadStories(getApplication()) }
            val read = store.loadRead()
            _state.update {
                allWords = loaded.first
                it.copy(
                    loading = false,
                    words = activeWords(it.settings),
                    allCount = allWords.count { w -> !w.isPhrase },
                    cards = loaded.second,
                    days = loaded.third,
                    stories = stories,
                    readIds = read,
                    today = today()
                )
            }
            // Alarms vanish after a force-stop; re-arm on every start.
            Reminders.reschedule(getApplication(), _state.value.settings)
        }
    }

    /** The date may roll over while the app stays open. */
    fun refreshDay() {
        val t = today()
        if (t != _state.value.today) _state.update { it.copy(today = t) }
    }

    // ---- Speech -----------------------------------------------------------

    fun speak(text: String) = speech.speak(text, _state.value.settings.slowSpeech)

    fun speakSlow(text: String) = speech.speak(text, slow = true)

    // ---- Listening --------------------------------------------------------

    fun listen() {
        if (_state.value.listen.phase == ListenPhase.LISTENING) return
        speech.stop()
        listenFor = if (_state.value.shadowing != null && _state.value.session == null) ListenFor.SHADOW else ListenFor.REVIEW
        _state.update { it.copy(listen = ListenState(ListenPhase.LISTENING)) }
        listener.start()
    }

    fun micDenied() {
        _state.update {
            it.copy(listen = ListenState(ListenPhase.ERROR, error = "Bez dopuštenja za mikrofon ne mogu čuti odgovor. Dodirni „Ne znam” ili dopusti mikrofon u postavkama."))
        }
    }

    /** Stop the microphone early; whatever was said so far is still evaluated. */
    fun finishListening() = listener.finish()

    fun cancelListening() {
        listener.stop()
        _state.update { it.copy(listen = ListenState()) }
    }

    private fun onHeard(results: List<String>) {
        val s = _state.value
        when (listenFor) {
            ListenFor.REVIEW -> {
                val session = s.session ?: return
                val item = session.current ?: return
                val word = s.word(item.rank) ?: return
                val check = when (item.mode) {
                    Mode.CLOZE -> SpeechCheck.checkCloze(word, results)
                    else -> SpeechCheck.checkWord(word, results)
                }
                _state.update {
                    it.copy(
                        listen = ListenState(ListenPhase.DONE),
                        session = session.copy(revealed = true, check = check)
                    )
                }
                if (s.settings.autoSpeak) speak(if (item.mode == Mode.CLOZE) word.example else word.spoken())
            }
            ListenFor.SHADOW -> {
                val sh = s.shadowing ?: return
                val word = sh.current?.let { s.word(it) } ?: return
                val match = SpeechCheck.matchSentence(word.example, results)
                _state.update {
                    it.copy(
                        listen = ListenState(ListenPhase.DONE),
                        shadowing = sh.copy(match = match, heard = results.firstOrNull() ?: "", attempts = sh.attempts + 1)
                    )
                }
            }
        }
    }

    // ---- Review session ---------------------------------------------------

    /** [extraNew] lets you go past today's limit of new words ("Još 5 novih"). */
    fun startSession(extraNew: Int = 0) {
        refreshDay()
        val s = _state.value
        val settings = s.settings.copy(newPerDay = maxOf(s.settings.newPerDay, s.todayStat.newCards) + extraNew)
        val queue = Srs.buildQueue(s.words, s.cards, settings, s.todayStat.newCards, s.today)
        _state.update { it.copy(session = Session(queue)) }
    }

    fun closeSession() {
        speech.stop()
        listener.stop()
        _state.update { it.copy(session = null) }
    }

    fun reveal() {
        listener.stop()
        _state.update { st -> st.copy(session = st.session?.copy(revealed = true), listen = ListenState()) }
    }

    fun answer(grade: Grade) {
        val s = _state.value
        val session = s.session ?: return
        val item = session.current ?: return
        val old = s.cards[item.rank]
        val wasNew = old == null || old.status != Status.LEARNING
        listener.stop()
        val updated = Srs.answer(old, grade, s.today, s.settings.retention)

        val cards = s.cards + (item.rank to updated)
        val day = s.todayStat.let { d ->
            d.copy(reviews = d.reviews + 1, newCards = d.newCards + if (wasNew) 1 else 0)
        }
        val days = s.days + (s.today to day)

        val rest = session.queue.drop(1).toMutableList()
        if (grade == Grade.AGAIN) {
            rest.add(minOf(Srs.AGAIN_GAP, rest.size), item.copy(isNew = false))
        }
        val next = session.copy(
            queue = rest,
            revealed = false,
            check = null,
            reviewed = session.reviewed + 1,
            again = session.again + if (grade == Grade.AGAIN) 1 else 0,
            newSeen = session.newSeen + if (wasNew) 1 else 0
        )
        _state.update { it.copy(cards = cards, days = days, session = next, listen = ListenState()) }
        persist(cards = cards, days = days)
    }

    /** "Već znam ovo" on a new card: mark it known and bring in the next new word instead. */
    fun skipAsKnown() {
        val s = _state.value
        val session = s.session ?: return
        val item = session.current ?: return
        val cards = s.cards + (item.rank to Card(Status.KNOWN))
        val inQueue = session.queue.map { it.rank }.toSet()
        val replacement = Srs.newCandidates(s.words, cards).firstOrNull { it !in inQueue }
        val rest = session.queue.drop(1).toMutableList()
        if (replacement != null) rest.add(minOf(1, rest.size), QueueItem(replacement, mode = Mode.RECOGNIZE, isNew = true))
        val dropped = if (replacement == null) 1 else 0
        _state.update {
            it.copy(
                cards = cards,
                session = session.copy(
                    queue = rest,
                    revealed = false,
                    check = null,
                    plannedTotal = session.plannedTotal - dropped
                )
            )
        }
        persist(cards = cards)
    }

    // ---- Shadowing --------------------------------------------------------

    /** Ten sentences, mostly from words you already know or are learning. */
    fun startShadowing(count: Int = 10) {
        val s = _state.value
        val familiar = s.vocab.filter {
            val st = s.cards[it.rank]?.status
            st == Status.KNOWN || st == Status.LEARNING
        }.shuffled().take(count).map { it.rank }
        val fill = s.vocab.map { it.rank }.filter { it !in familiar }.take(count - familiar.size)
        _state.update { it.copy(shadowing = Shadowing((familiar + fill).shuffled()), listen = ListenState()) }
    }

    fun shadowNext() {
        listener.stop()
        val s = _state.value
        val sh = s.shadowing ?: return
        val tried = sh.attempts > 0
        val days = if (tried) s.days + (s.today to s.todayStat.let { it.copy(shadowed = it.shadowed + 1) }) else s.days
        _state.update {
            it.copy(
                days = days,
                listen = ListenState(),
                shadowing = sh.copy(index = sh.index + 1, match = null, heard = "", attempts = 0, done = sh.done + if (tried) 1 else 0)
            )
        }
        persist(days = days)
    }

    fun shadowRetry() {
        _state.update { st -> st.copy(shadowing = st.shadowing?.copy(match = null, heard = ""), listen = ListenState()) }
    }

    fun closeShadowing() {
        speech.stop()
        listener.stop()
        _state.update { it.copy(shadowing = null, listen = ListenState()) }
    }

    // ---- Placement test ---------------------------------------------------

    /** [rank] guards against a double answer (button tap during the swipe animation). */
    fun testAnswer(rank: Int, known: Boolean) {
        val s = _state.value
        if (s.nextTestRank != rank) return
        val prev = s.cards[rank]
        val set = Card(if (known) Status.KNOWN else Status.TO_LEARN)
        val cards = s.cards + (rank to set)
        val day = s.todayStat.let { it.copy(tested = it.tested + 1) }
        val days = s.days + (s.today to day)
        _state.update {
            it.copy(cards = cards, days = days, testUndo = (it.testUndo + Triple(rank, prev, set)).takeLast(50))
        }
        persist(cards = cards, days = days)
    }

    fun testUndo() {
        val s = _state.value
        val (rank, prev, set) = s.testUndo.lastOrNull() ?: return
        if (s.cards[rank] != set) {
            // The word has moved on since (learned, edited in the dictionary): don't undo that.
            _state.update { it.copy(testUndo = it.testUndo.dropLast(1)) }
            return
        }
        val cards = if (prev == null) s.cards - rank else s.cards + (rank to prev)
        val day = s.todayStat.let { it.copy(tested = (it.tested - 1).coerceAtLeast(0)) }
        val days = s.days + (s.today to day)
        _state.update { it.copy(cards = cards, days = days, testUndo = it.testUndo.dropLast(1)) }
        persist(cards = cards, days = days)
    }

    // ---- Dictionary -------------------------------------------------------

    fun setStatus(rank: Int, status: Status) {
        val s = _state.value
        val cards = when (status) {
            Status.UNTESTED -> s.cards - rank
            Status.LEARNING -> s.cards // only the scheduler puts words into LEARNING
            else -> s.cards + (rank to Card(status))
        }
        _state.update { it.copy(cards = cards) }
        persist(cards = cards)
    }

    // ---- Settings ---------------------------------------------------------

    private fun activeWords(s: Settings): List<Word> =
        allWords.filter { it.rank <= s.level * LEVEL_SIZE || (s.phrasesOn && it.isPhrase) }

    /** Switch the active list to the top [level] × 1000 words. Progress on every word is kept. */
    fun setLevel(level: Int) {
        val l = level.coerceIn(1, maxOf(1, _state.value.maxLevel))
        updateSettings { it.copy(level = l) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(_state.value.settings)
        _state.update {
            val changed = next.level != it.settings.level || next.phrasesOn != it.settings.phrasesOn
            it.copy(settings = next, words = if (changed) activeWords(next) else it.words)
        }
        store.saveSettings(next)
        Reminders.reschedule(getApplication(), next)
        speech.chooseVoice(next.voiceName)
    }

    fun resetProgress() {
        store.resetProgress()
        _state.update {
            it.copy(cards = emptyMap(), days = emptyMap(), readIds = emptySet(), testUndo = emptyList(), session = null)
        }
    }

    // ---- Hard words -------------------------------------------------------

    /** A short extra session with the words that keep failing, in speaking modes. */
    fun startHardSession() {
        refreshDay()
        val s = _state.value
        val random = kotlin.random.Random.Default
        val queue = s.hardWords.mapNotNull { rank ->
            val w = s.word(rank) ?: return@mapNotNull null
            val mode = when (s.settings.practice) {
                Practice.CLASSIC -> Mode.RECOGNIZE
                else -> if (w.clozeText != null && random.nextBoolean()) Mode.CLOZE else Mode.RECALL
            }
            QueueItem(rank, mode, isNew = false)
        }
        if (queue.isEmpty()) return
        _state.update { it.copy(session = Session(queue)) }
    }

    // ---- Stories ----------------------------------------------------------

    /** Starts loading the forms index in the background (idempotent). */
    fun ensureForms() {
        if (forms != null || formsLoading) return
        formsLoading = true
        viewModelScope.launch {
            forms = withContext(Dispatchers.IO) { WordRepo.loadForms(getApplication()) }
            formsLoading = false
        }
    }

    /** The list entry a word in a story belongs to, by inflected form; null if it isn't on the list. */
    fun lookup(token: String): Word? {
        val raw = token.lowercase().trim { !it.isLetterOrDigit() }
        if (raw.isEmpty()) return null
        val f = forms ?: return null
        // "geht's" → "geht"
        val key = raw.substringBefore('\'').substringBefore('’')
        val rank = f[raw] ?: f[key] ?: f[key.replace("ß", "ss")] ?: return null
        return allWords.firstOrNull { it.rank == rank }
    }

    fun markRead(storyId: String) {
        val s = _state.value
        if (storyId in s.readIds) return
        val read = s.readIds + storyId
        val days = s.days + (s.today to s.todayStat.let { it.copy(read = it.read + 1) })
        _state.update { it.copy(readIds = read, days = days) }
        store.saveRead(read)
        persist(days = days)
    }

    // ---- Grammar round ----------------------------------------------------

    /** Ten sentences with a marked gap, preferring words you already know or are learning. */
    fun startGrammar(count: Int = 10) {
        refreshDay()
        val s = _state.value
        val withGap = s.vocab.filter { it.gap != null }
        val familiar = withGap.filter { s.cards[it.rank]?.status.let { st -> st == Status.KNOWN || st == Status.LEARNING } }
        val pool = if (familiar.size >= count) familiar else familiar + withGap.filter { it !in familiar }
        val ranks = pool.shuffled().take(count).map { it.rank }
        if (ranks.isEmpty()) return
        _state.update { it.copy(grammar = GrammarRound(ranks)) }
    }

    fun grammarChoose(option: String) {
        val s = _state.value
        val g = s.grammar ?: return
        if (g.chosen != null) return
        val word = g.current?.let { s.word(it) } ?: return
        val ok = option == word.gap?.blank
        _state.update {
            it.copy(grammar = g.copy(chosen = option, correct = g.correct + if (ok) 1 else 0, wrong = g.wrong + if (ok) 0 else 1))
        }
        if (ok && s.settings.autoSpeak) speak(word.example)
    }

    fun grammarNext() {
        val s = _state.value
        val g = s.grammar ?: return
        val next = g.copy(index = g.index + 1, chosen = null)
        val days = if (next.finished) {
            s.days + (s.today to s.todayStat.let { it.copy(grammar = it.grammar + g.ranks.size) })
        } else s.days
        _state.update { it.copy(grammar = next, days = days) }
        if (next.finished) persist(days = days)
    }

    fun closeGrammar() {
        speech.stop()
        _state.update { it.copy(grammar = null) }
    }

    /** Serialising ~1000 small cards takes a few ms; SharedPreferences.apply() writes in order, off the main thread. */
    private fun persist(cards: Map<Int, Card>? = null, days: Map<Long, DayStat>? = null) {
        cards?.let { store.saveCards(it) }
        days?.let { store.saveDays(it) }
    }

    override fun onCleared() {
        speech.shutdown()
        listener.destroy()
        super.onCleared()
    }
}
