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
    val session: Session? = null,
    val shadowing: Shadowing? = null,
    val listen: ListenState = ListenState(),
    /** Undo stack of the placement test: rank and the card it had before. */
    val testUndo: List<Triple<Int, Card?, Card>> = emptyList(),
    val today: Long = today()
) {
    private val byRank: Map<Int, Word> by lazy { words.associateBy { it.rank } }
    fun word(rank: Int): Word? = byRank[rank]

    val counts: Map<Bucket, Int> by lazy {
        val m = HashMap<Bucket, Int>()
        for (w in words) {
            val b = cards[w.rank].bucket()
            m[b] = (m[b] ?: 0) + 1
        }
        m
    }

    fun count(b: Bucket): Int = if (b == Bucket.ALL) words.size else counts[b] ?: 0

    /** Known outright, mastered, or currently being learned. */
    val familiar: Int get() = count(Bucket.KNOWN) + count(Bucket.MASTERED) + count(Bucket.LEARNING)

    val tested: Int get() = words.size - count(Bucket.UNTESTED)

    /** Next word in the placement test, or null when everything has been tested. */
    val nextTestRank: Int? by lazy {
        words.firstOrNull { (cards[it.rank]?.status ?: Status.UNTESTED) == Status.UNTESTED }?.rank
    }

    val todayStat: DayStat get() = days[today] ?: DayStat()
    val dueToday: Int get() = Srs.dueCount(cards, today)
    val newLeftToday: Int
        get() {
            val budget = (settings.newPerDay - todayStat.newCards).coerceAtLeast(0)
            val available = count(Bucket.TO_LEARN) + count(Bucket.UNTESTED)
            return minOf(budget, available)
        }
    val streak: Int get() = Srs.streak(days, today)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val _state = MutableStateFlow(UiState(settings = store.loadSettings()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val speech = Speech(app) { voice -> _state.update { it.copy(voice = voice) } }

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

    val recognitionAvailable: Boolean get() = listener.available

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Triple(WordRepo.load(getApplication()), store.loadCards(), store.loadDays())
            }
            _state.update {
                it.copy(loading = false, words = loaded.first, cards = loaded.second, days = loaded.third, today = today())
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
        speech.stop()
        listenFor = if (_state.value.shadowing != null && _state.value.session == null) ListenFor.SHADOW else ListenFor.REVIEW
        _state.update { it.copy(listen = ListenState(ListenPhase.LISTENING)) }
        listener.start()
    }

    fun micDenied() {
        _state.update {
            it.copy(listen = ListenState(ListenPhase.ERROR, error = "Bez dopuštenja za mikrofon ne mogu čuti odgovor. Dodirni \"Prikaži\" ili dopusti mikrofon u postavkama."))
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
                speak(if (item.mode == Mode.CLOZE) word.example else word.spoken())
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
        val familiar = s.words.filter {
            val st = s.cards[it.rank]?.status
            st == Status.KNOWN || st == Status.LEARNING
        }.shuffled().take(count).map { it.rank }
        val fill = s.words.map { it.rank }.filter { it !in familiar }.take(count - familiar.size)
        _state.update { it.copy(shadowing = Shadowing((familiar + fill).shuffled()), listen = ListenState()) }
    }

    fun shadowNext() {
        listener.stop()
        val s = _state.value
        val sh = s.shadowing ?: return
        val day = s.todayStat.let { it.copy(shadowed = it.shadowed + 1) }
        val days = s.days + (s.today to day)
        _state.update {
            it.copy(
                days = days,
                listen = ListenState(),
                shadowing = sh.copy(index = sh.index + 1, match = null, heard = "", attempts = 0, done = sh.done + 1)
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

    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(_state.value.settings)
        _state.update { it.copy(settings = next) }
        store.saveSettings(next)
        Reminders.reschedule(getApplication(), next)
    }

    fun resetProgress() {
        store.resetProgress()
        _state.update { it.copy(cards = emptyMap(), days = emptyMap(), testUndo = emptyList(), session = null) }
    }

    /** Serialising ~1000 small cards takes a few ms; SharedPreferences.apply() writes in order, off the main thread. */
    private fun persist(cards: Map<Int, Card>? = null, days: Map<Long, DayStat>? = null) {
        cards?.let { store.saveCards(it) }
        days?.let { store.saveDays(it) }
    }

    override fun onCleared() {
        speech.shutdown()
        listener.stop()
        super.onCleared()
    }
}
