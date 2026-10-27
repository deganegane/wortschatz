package io.github.deganegane.wortschatz

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A small SM-2-style scheduler. Intervals are whole days; "Opet" keeps the card in today's
 * session until it is answered correctly.
 */
object Srs {

    /** How many cards later a card answered "Opet" comes back in the same session. */
    const val AGAIN_GAP = 3

    fun schedule(card: Card, grade: Grade, today: Long): Card {
        val fresh = card.status != Status.LEARNING || card.reps == 0
        // Already failed today and being repeated in the session: don't punish twice.
        val relearning = !fresh && card.interval < 1
        val i = card.interval
        var ease = card.ease
        val next: Double = when (grade) {
            Grade.AGAIN -> {
                if (!relearning) ease = max(1.3, ease - 0.2)
                0.0
            }
            Grade.HARD -> {
                ease = max(1.3, ease - 0.15)
                if (fresh || i < 1) 1.0 else max(i + 1, i * 1.2)
            }
            Grade.GOOD -> when {
                fresh || i < 1 -> 1.0
                i < 2 -> 3.0
                else -> max(i + 1, i * ease)
            }
            Grade.EASY -> {
                ease += 0.15
                if (fresh || i < 2) 4.0 else max(i + 2, i * ease * 1.3)
            }
        }
        val days = next.roundToInt().coerceAtMost(365)
        return card.copy(
            status = Status.LEARNING,
            interval = days.toDouble(),
            ease = ease,
            due = today + days,
            reps = card.reps + 1,
            lapses = if (grade == Grade.AGAIN && !fresh && !relearning) card.lapses + 1 else card.lapses
        )
    }

    /** Short label shown under each answer button, e.g. "<1 min", "1 d", "2 tj.", "3 mj.". */
    fun previewLabel(card: Card?, grade: Grade): String {
        val c = card ?: Card(Status.TO_LEARN)
        val days = schedule(c, grade, 0).interval.roundToInt()
        return when {
            days <= 0 -> "<1 min"
            days < 14 -> "$days d"
            days < 60 -> "${(days / 7.0).roundToInt()} tj."
            days < 365 -> "${(days / 30.0).roundToInt()} mj."
            else -> "1 g."
        }
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
        val due = cards.entries
            .filter { it.value.status == Status.LEARNING && it.value.due <= today }
            .sortedWith(compareBy({ it.value.due }, { it.key }))
            .map { it.key }

        val newCount = max(0, settings.newPerDay - newDoneToday)
        val newRanks = newCandidates(words, cards).take(newCount)

        fun reverse(): Boolean = when (settings.direction) {
            Direction.DE_HR -> false
            Direction.HR_DE -> true
            Direction.MIXED -> random.nextBoolean()
        }

        val out = ArrayList<QueueItem>(due.size + newRanks.size)
        val dueIt = due.iterator()
        val newIt = newRanks.iterator()
        var pos = 0
        while (dueIt.hasNext() || newIt.hasNext()) {
            val takeNew = newIt.hasNext() && (!dueIt.hasNext() || pos % 4 == 3)
            if (takeNew) {
                // New words are always introduced German first, so you hear and see them.
                out += QueueItem(newIt.next(), reverse = false, isNew = true)
            } else {
                out += QueueItem(dueIt.next(), reverse = reverse(), isNew = false)
            }
            pos++
        }
        return out
    }

    /** Words that could be introduced next, in rank order. */
    fun newCandidates(words: List<Word>, cards: Map<Int, Card>): List<Int> {
        val toLearn = words.filter { cards[it.rank]?.status == Status.TO_LEARN }.map { it.rank }
        val untested = words.filter { (cards[it.rank]?.status ?: Status.UNTESTED) == Status.UNTESTED }
            .map { it.rank }
        return toLearn + untested
    }

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
