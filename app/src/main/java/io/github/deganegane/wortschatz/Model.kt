package io.github.deganegane.wortschatz

import java.time.LocalDate

/** One entry of the frequency list (assets/words.json). [rank] is the stable id. */
data class Word(
    val rank: Int,
    val de: String,
    /** "der" / "die" / "das" for nouns, otherwise null. */
    val article: String?,
    val plural: String?,
    val pos: String,
    /** Verb forms ("geht · ging · ist gegangen") or irregular comparison, if any. */
    val forms: String?,
    val hr: String,
    val example: String,
    val exampleHr: String,
    /** Parts of [example] that realise the headword (blanked out in cloze cards). */
    val cloze: List<String> = emptyList(),
    /** Situation of a conversational phrase ("u restoranu"), null for ordinary words. */
    val cat: String? = null,
    /** A grammar gap in [example] (article, pronoun or adjective ending), if one was marked. */
    val gap: Gap? = null
) {
    /** Ready-made conversational phrases live above rank [PHRASE_RANK]. */
    val isPhrase: Boolean get() = rank > PHRASE_RANK

    /** Headword with its article, e.g. "das Haus". */
    val full: String get() = if (article != null) "$article $de" else de

    /** First Croatian meaning, used as a hint ("planirati, namjeravati" -> "planirati"). */
    val hrShort: String get() = hr.substringBefore(',').substringBefore(';').substringBefore(" (").trim()

    /** The example with each cloze part replaced by a blank, or null when there is nothing to blank. */
    val clozeText: String?
        get() {
            if (cloze.isEmpty()) return null
            var t = example
            for (part in cloze) {
                val re = Regex("(?<![\\p{L}])" + Regex.escape(part) + "(?![\\p{L}])")
                val m = re.find(t) ?: return null
                t = t.replaceRange(m.range, BLANK)
            }
            return t
        }

    /** Lower-case text used by the dictionary search. */
    val searchKey: String = "${de.lowercase()} ${hr.lowercase()} ${(article ?: "")}"
}

val POS_LABELS = mapOf(
    "n" to "imenica",
    "v" to "glagol",
    "adj" to "pridjev",
    "adv" to "prilog",
    "prep" to "prijedlog",
    "konj" to "veznik",
    "pron" to "zamjenica",
    "art" to "član",
    "num" to "broj",
    "part" to "čestica",
    "phr" to "izraz",
    "intj" to "uzvik"
)

/** Where a word stands for this learner. Absent from the card map = [UNTESTED]. */
enum class Status(val code: Int) {
    UNTESTED(0),
    /** Marked as known (placement test or manually) — not studied. */
    KNOWN(1),
    /** Marked "ne znam": waiting to be introduced as a new card. */
    TO_LEARN(2),
    /** Introduced and scheduled by the spaced-repetition algorithm. */
    LEARNING(3);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: UNTESTED
    }
}

/** Interval (days) from which a learning word counts as "naučeno" (mastered). */
const val MASTERED_DAYS = 21.0

/** Words per level, and the share of a level that must be known or mastered to unlock the next. */
const val LEVEL_SIZE = 1000
const val UNLOCK_SHARE = 0.8

const val BLANK = "_____"
const val PHRASE_RANK = 10000

/** A fill-in-the-form exercise on a sentence: the blanked token, four options, hint and explanation. */
data class Gap(val blank: String, val options: List<String>, val hint: String, val explanation: String)

/** A graded mini-story (assets/stories.json). */
data class Story(
    val id: String,
    val level: Int,
    val title: String,
    val de: String,
    val hr: String,
    /** Words beyond the level's list that the story introduces. */
    val newWords: List<String>
) {
    val wordCount: Int get() = de.split(Regex("\\s+")).count { it.any(Char::isLetter) }
}

/** One grammar-gap round: ten sentences answered by tapping an option. */
data class GrammarRound(
    val ranks: List<Int>,
    val index: Int = 0,
    /** Option chosen on the current sentence, null until answered. */
    val chosen: String? = null,
    val correct: Int = 0,
    val wrong: Int = 0
) {
    val current: Int? get() = ranks.getOrNull(index)
    val finished: Boolean get() = index >= ranks.size
}

data class Card(
    val status: Status,
    /** Current interval in days (0 = still being learned today). */
    val interval: Double = 0.0,
    /** FSRS memory stability in days (0 = never reviewed). */
    val stability: Double = 0.0,
    /** FSRS difficulty, 1..10. */
    val difficulty: Double = 0.0,
    /** Epoch day when the card is due next. */
    val due: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** Epoch day of the last review. */
    val lastReview: Long = 0
) {
    val mastered: Boolean get() = status == Status.LEARNING && interval >= MASTERED_DAYS
}

/** Filter used in the dictionary; also the "bucket" a word is shown in. */
enum class Bucket(val label: String) {
    ALL("Sve"),
    UNTESTED("Netestirano"),
    TO_LEARN("Ne znam"),
    LEARNING("Učim"),
    MASTERED("Naučeno"),
    KNOWN("Znam")
}

fun Card?.bucket(): Bucket = when {
    this == null || status == Status.UNTESTED -> Bucket.UNTESTED
    status == Status.KNOWN -> Bucket.KNOWN
    status == Status.TO_LEARN -> Bucket.TO_LEARN
    mastered -> Bucket.MASTERED
    else -> Bucket.LEARNING
}

/** Answer buttons on a review card. */
enum class Grade(val label: String) { AGAIN("Opet"), HARD("Teško"), GOOD("Dobro"), EASY("Lako") }

/** How a card asks for the word. */
enum class Mode {
    /** German shown, recall the meaning. */
    RECOGNIZE,
    /** Croatian shown, say the German word. */
    RECALL,
    /** Example sentence with a blank, say the missing word. */
    CLOZE
}

enum class Practice(val label: String) {
    CLASSIC("Klasično"),
    SPEAKING("Govor"),
    MIXED("Mješovito")
}

enum class ThemeMode(val label: String) { SYSTEM("Sustav"), LIGHT("Svijetla"), DARK("Tamna") }

data class Settings(
    val newPerDay: Int = 10,
    val practice: Practice = Practice.MIXED,
    /** Answer speaking cards with the microphone (otherwise: think, then reveal). */
    val speakAnswers: Boolean = true,
    /** FSRS target probability of remembering a word when it comes up. */
    val retention: Double = 0.9,
    val autoSpeak: Boolean = true,
    val slowSpeech: Boolean = false,
    val reminderOn: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Active list size in thousands: 1 = top 1000 … 4 = top 4000. */
    val level: Int = 1,
    /** Mix ready-made conversational phrases into the new cards. */
    val phrasesOn: Boolean = true
)

/** Activity of one calendar day. */
data class DayStat(
    val reviews: Int = 0,
    val newCards: Int = 0,
    val tested: Int = 0,
    val shadowed: Int = 0,
    val read: Int = 0,
    val grammar: Int = 0
) {
    val active: Boolean get() = reviews > 0 || tested > 0 || shadowed > 0 || read > 0 || grammar > 0
}

/** One card waiting in a review session. */
data class QueueItem(
    val rank: Int,
    val mode: Mode,
    /** First time this word is studied. */
    val isNew: Boolean
)

data class Session(
    val queue: List<QueueItem>,
    val revealed: Boolean = false,
    /** Result of a spoken answer on the current card, if any. */
    val check: SpokenCheck? = null,
    val reviewed: Int = 0,
    val again: Int = 0,
    val newSeen: Int = 0,
    /** Cards in the session when it started (for the progress bar). */
    val plannedTotal: Int = queue.size
) {
    val current: QueueItem? get() = queue.firstOrNull()
    val finished: Boolean get() = queue.isEmpty()
}

fun today(): Long = LocalDate.now().toEpochDay()

/** Outcome of comparing what the recogniser heard with the expected answer. */
data class SpokenCheck(
    val verdict: Verdict,
    /** Best transcript, shown back to the learner. */
    val heard: String,
    /** For nouns: the article that was said, when it was the wrong one. */
    val wrongArticle: String? = null
) {
    val suggested: Grade
        get() = when (verdict) {
            Verdict.CORRECT -> Grade.GOOD
            Verdict.ALMOST -> Grade.HARD
            Verdict.WRONG -> Grade.AGAIN
        }
}

enum class Verdict { CORRECT, ALMOST, WRONG }

/** A shadowing round: listen to a sentence, repeat it aloud. */
data class Shadowing(
    val ranks: List<Int>,
    val index: Int = 0,
    /** Per-token match of the last attempt on the current sentence, null before trying. */
    val match: List<Boolean>? = null,
    val heard: String = "",
    val attempts: Int = 0,
    val done: Int = 0
) {
    val current: Int? get() = ranks.getOrNull(index)
    val finished: Boolean get() = index >= ranks.size
}

enum class ListenPhase { IDLE, LISTENING, DONE, ERROR }

data class ListenState(
    val phase: ListenPhase = ListenPhase.IDLE,
    /** 0..1 microphone level while listening. */
    val level: Float = 0f,
    val partial: String = "",
    val error: String? = null
)
