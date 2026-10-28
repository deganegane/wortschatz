package io.github.deganegane.wortschatz

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Scheduling with FSRS-5 (Free Spaced Repetition Scheduler, default parameters).
 *
 * Each card has a memory *stability* S (days until recall probability drops to 90 %) and a
 * *difficulty* D (1..10). After every answer both are updated from how much time has passed
 * and the grade; the next interval is the time until recall probability falls to the target
 * retention. "Opet" keeps the card in today's session until it is answered.
 */
object Srs {

    /** How many cards later a card answered "Opet" comes back in the same session. */
    const val AGAIN_GAP = 3

    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val MAX_DAYS = 365

    private fun g(grade: Grade): Int = grade.ordinal + 1 // AGAIN=1 … EASY=4

    private fun initStability(grade: Grade): Double = W[g(grade) - 1]

    private fun initDifficulty(grade: Grade): Double =
        (W[4] - exp(W[5] * (g(grade) - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, grade: Grade): Double {
        val delta = -W[6] * (g(grade) - 3)
        val damped = d + delta * (10 - d) / 9
        return (W[7] * initDifficulty(Grade.EASY) + (1 - W[7]) * damped).coerceIn(1.0, 10.0)
    }

    /** Probability of recall after [days] with stability [s]. */
    fun retrievability(days: Double, s: Double): Double = (1 + FACTOR * days / s).pow(DECAY)

    private fun intervalFor(s: Double, retention: Double): Double =
        s / FACTOR * (retention.pow(1 / DECAY) - 1)

    private fun recallStability(d: Double, s: Double, r: Double, grade: Grade): Double {
        val hard = if (grade == Grade.HARD) W[15] else 1.0
        val easy = if (grade == Grade.EASY) W[16] else 1.0
        return s * (exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hard * easy + 1)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        val f = W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r))
        return min(f, s / exp(W[17] * W[18]))
    }

    /** Same-day review (e.g. the repeat after "Opet"). */
    private fun shortTermStability(s: Double, grade: Grade): Double =
        s * exp(W[17] * (g(grade) - 3 + W[18]))

    /** Cards scheduled by the old SM-2 version get a stability from their interval. */
    fun migrate(card: Card): Card {
        if (card.status != Status.LEARNING || card.stability > 0 || card.reps == 0) return card
        val s = max(1.0, card.interval)
        return card.copy(stability = s, difficulty = 5.0, lastReview = card.due - card.interval.roundToInt())
    }

    fun schedule(card: Card, grade: Grade, today: Long, retention: Double): Card {
        val c = migrate(card)
        val first = c.status != Status.LEARNING || c.stability <= 0.0
        val elapsed = (today - c.lastReview).toDouble()

        val (s, d) = when {
            first -> initStability(grade) to initDifficulty(grade)
            elapsed < 1 -> shortTermStability(c.stability, grade) to nextDifficulty(c.difficulty, grade)
            else -> {
                val r = retrievability(elapsed, c.stability)
                val ns = if (grade == Grade.AGAIN) {
                    forgetStability(c.difficulty, c.stability, r)
                } else {
                    recallStability(c.difficulty, c.stability, r, grade)
                }
                ns to nextDifficulty(c.difficulty, grade)
            }
        }
        val stability = s.coerceIn(0.1, 36500.0)

        val days = when {
            grade == Grade.AGAIN -> 0
            // A brand-new word was only seen, not recalled: bring it back soon regardless.
            first -> when (grade) {
                Grade.EASY -> 3
                else -> 1
            }
            else -> intervalFor(stability, retention).roundToInt().coerceIn(1, MAX_DAYS)
        }
        val lapse = grade == Grade.AGAIN && !first && elapsed >= 1
        return c.copy(
            status = Status.LEARNING,
            interval = days.toDouble(),
            stability = stability,
            difficulty = d,
            due = today + days,
            reps = c.reps + 1,
            lapses = if (lapse) c.lapses + 1 else c.lapses,
            lastReview = today
        )
    }

    /** Days until the next review for each grade, kept in order (Teško ≤ Dobro < Lako). */
    fun preview(card: Card?, today: Long, retention: Double): Map<Grade, Int> {
        val c = card ?: Card(Status.TO_LEARN)
        val raw = Grade.entries.associateWith { schedule(c, it, today, retention).interval.roundToInt() }
        val good = raw.getValue(Grade.GOOD)
        val hard = min(raw.getValue(Grade.HARD), good)
        val easy = min(MAX_DAYS, max(raw.getValue(Grade.EASY), good + 1))
        return raw + mapOf(Grade.HARD to hard, Grade.EASY to easy)
    }

    /** Schedule an answer, respecting the ordering shown on the buttons. */
    fun answer(card: Card?, grade: Grade, today: Long, retention: Double): Card {
        val c = card ?: Card(Status.TO_LEARN)
        val result = schedule(c, grade, today, retention)
        if (grade == Grade.AGAIN) return result
        val days = preview(c, today, retention).getValue(grade)
        return result.copy(interval = days.toDouble(), due = today + days)
    }

    fun label(days: Int): String = when {
        days <= 0 -> "<1 min"
        days < 14 -> "$days d"
        days < 60 -> "${(days / 7.0).roundToInt()} tj."
        days < 365 -> "${(days / 30.0).roundToInt()} mj."
        else -> "1 g."
    }

    /**
     * Today's queue: everything due, with new words (rank order: first the ones marked
     * "ne znam", then untested ones) mixed in every fourth card.
     */
    fun buildQueue(
        words: List<Word>,
        cards: Map<Int, Card>,
        settings: Settings,
        newDoneToday: Int,
        today: Long,
        random: Random = Random.Default
    ): List<QueueItem> {
        val byRank = words.associateBy { it.rank }
        val due = cards.entries
            .filter { it.value.status == Status.LEARNING && it.value.due <= today }
            .sortedWith(compareBy({ it.value.due }, { it.key }))
            .map { it.key }

        val newCount = max(0, settings.newPerDay - newDoneToday)
        val newRanks = newCandidates(words, cards).take(newCount)

        val out = ArrayList<QueueItem>(due.size + newRanks.size)
        val dueIt = due.iterator()
        val newIt = newRanks.iterator()
        var pos = 0
        while (dueIt.hasNext() || newIt.hasNext()) {
            val takeNew = newIt.hasNext() && (!dueIt.hasNext() || pos % 4 == 3)
            if (takeNew) {
                // New words are always introduced German first, so you hear and see them.
                out += QueueItem(newIt.next(), mode = Mode.RECOGNIZE, isNew = true)
            } else {
                val rank = dueIt.next()
                out += QueueItem(rank, mode = pickMode(byRank[rank], cards[rank], settings.practice, random), isNew = false)
            }
            pos++
        }
        return out
    }

    /**
     * Young cards are first recognised; once a word has been seen a couple of times it is
     * increasingly produced (said aloud from Croatian, or filled into its sentence).
     */
    fun pickMode(word: Word?, card: Card?, practice: Practice, random: Random): Mode {
        val canCloze = word?.clozeText != null
        val reps = card?.reps ?: 0
        return when (practice) {
            Practice.CLASSIC -> Mode.RECOGNIZE
            Practice.SPEAKING -> if (canCloze && random.nextBoolean()) Mode.CLOZE else Mode.RECALL
            Practice.MIXED -> when {
                reps < 2 -> Mode.RECOGNIZE
                else -> when (random.nextInt(3)) {
                    0 -> Mode.RECOGNIZE
                    1 -> Mode.RECALL
                    else -> if (canCloze) Mode.CLOZE else Mode.RECALL
                }
            }
        }
    }

    /**
     * Words that could be introduced next: first the ones marked "ne znam", then untested ones,
     * in rank order — with a conversational phrase mixed in as every third new card.
     */
    fun newCandidates(words: List<Word>, cards: Map<Int, Card>): List<Int> {
        fun pending(list: List<Word>): List<Int> {
            val toLearn = list.filter { cards[it.rank]?.status == Status.TO_LEARN }.map { it.rank }
            val untested = list.filter { (cards[it.rank]?.status ?: Status.UNTESTED) == Status.UNTESTED }.map { it.rank }
            return toLearn + untested
        }
        val vocab = pending(words.filter { !it.isPhrase })
        val phrases = pending(words.filter { it.isPhrase })
        if (phrases.isEmpty()) return vocab
        if (vocab.isEmpty()) return phrases
        val out = ArrayList<Int>(vocab.size + phrases.size)
        val v = vocab.iterator()
        val p = phrases.iterator()
        var i = 0
        while (v.hasNext() || p.hasNext()) {
            val takePhrase = p.hasNext() && (!v.hasNext() || i % 3 == 2)
            out += if (takePhrase) p.next() else v.next()
            i++
        }
        return out
    }

    /** Learning words that keep failing: by lapses, then FSRS difficulty. */
    fun hardWords(cards: Map<Int, Card>, limit: Int = 20): List<Int> =
        cards.entries
            .filter { it.value.status == Status.LEARNING && (it.value.lapses >= 1 || it.value.difficulty >= 7.5) }
            .sortedWith(compareByDescending<Map.Entry<Int, Card>> { it.value.lapses }.thenByDescending { it.value.difficulty })
            .take(limit)
            .map { it.key }

    fun dueCount(cards: Map<Int, Card>, today: Long): Int =
        cards.values.count { it.status == Status.LEARNING && it.due <= today }

    /** Consecutive active days ending today (or yesterday, if today is still empty). */
    fun streak(days: Map<Long, DayStat>, today: Long): Int {
        var d = if (days[today]?.active == true) today else today - 1
        var n = 0
        while (days[d]?.active == true) {
            n++
            d--
        }
        return n
    }
}
