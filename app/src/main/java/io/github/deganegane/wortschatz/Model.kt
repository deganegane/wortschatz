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
    val exampleHr: String
) {
    /** Headword with its article, e.g. "das Haus". */
    val full: String get() = if (article != null) "$article $de" else de

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

data class Card(
    val status: Status,
    /** Current interval in days (0 = still being learned today). */
    val interval: Double = 0.0,
    val ease: Double = 2.5,
    /** Epoch day when the card is due next. */
    val due: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0
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

enum class Direction(val label: String) {
    DE_HR("DE → HR"),
    HR_DE("HR → DE"),
    MIXED("Mješovito")
}

enum class ThemeMode(val label: String) { SYSTEM("Sustav"), LIGHT("Svijetla"), DARK("Tamna") }

data class Settings(
    val newPerDay: Int = 10,
    val direction: Direction = Direction.DE_HR,
    val autoSpeak: Boolean = true,
    val slowSpeech: Boolean = false,
    val reminderOn: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false
)

/** Activity of one calendar day. */
data class DayStat(val reviews: Int = 0, val newCards: Int = 0, val tested: Int = 0) {
    val active: Boolean get() = reviews > 0 || tested > 0
}

/** One card waiting in a review session. */
data class QueueItem(
    val rank: Int,
    /** true = show Croatian, recall German. */
    val reverse: Boolean,
    /** First time this word is studied. */
    val isNew: Boolean
)

data class Session(
    val queue: List<QueueItem>,
    val revealed: Boolean = false,
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
