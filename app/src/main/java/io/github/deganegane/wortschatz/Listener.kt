package io.github.deganegane.wortschatz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * German speech recognition through the phone's recogniser (usually Google). One utterance
 * at a time; results and the mic level are reported through the callbacks on the main thread.
 */
class Listener(
    private val context: Context,
    private val onLevel: (level: Float, partial: String) -> Unit,
    private val onResult: (results: List<String>) -> Unit,
    private val onError: (message: String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        stop()
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            onError("Prepoznavanje govora nije dostupno.")
            return
        }
        recognizer = r
        var partial = ""
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onLevel(0f, "")
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2..10 dB in practice.
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f), partial)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onResult(emptyList())
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onError("Aplikacija nema dopuštenje za mikrofon.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        onError("Prepoznavanje treba internet (ili preuzeti njemački offline paket).")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> onError("Mikrofon je zauzet, pokušaj ponovno.")
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        onError("Njemački nije dostupan za prepoznavanje govora na ovom mobitelu.")
                    else -> onError("Prepoznavanje nije uspjelo (greška $error).")
                }
                release()
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                onResult(list.toList())
                release()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: partial
                onLevel(0.5f, partial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            onError("Prepoznavanje govora nije uspjelo pokrenuti.")
            release()
        }
    }

    /** Stop listening and let the recogniser deliver what it has. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}

/** Compares what was heard with what was expected. */
object SpeechCheck {

    private val ARTICLES = setOf("der", "die", "das")

    fun normalize(s: String): String =
        s.lowercase()
            .replace("ß", "ss")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    fun tokens(s: String): List<String> = normalize(s).split(' ').filter { it.isNotEmpty() }

    /** Same word, allowing one slip for longer words (recognisers mangle endings). */
    fun same(a: String, b: String): Boolean {
        if (a == b) return true
        val n = maxOf(a.length, b.length)
        return n >= 5 && distance(a, b) <= 1
    }

    /** True when [needle] (one or more words) occurs as consecutive tokens in [hay]. */
    private fun containsPhrase(hay: List<String>, needle: List<String>): Int {
        if (needle.isEmpty()) return -1
        for (i in 0..hay.size - needle.size) {
            if (needle.indices.all { same(hay[i + it], needle[it]) }) return i
        }
        return -1
    }

    /** Say the German word for the Croatian prompt. */
    fun checkWord(word: Word, results: List<String>): SpokenCheck {
        if (results.isEmpty()) return SpokenCheck(Verdict.WRONG, "")
        val target = tokens(word.de.replace(Regex("^sich "), ""))
        val alternatives = if (word.rank == 1) listOf(listOf("der"), listOf("die"), listOf("das")) else listOf(target)
        var almost: SpokenCheck? = null
        for (heard in results) {
            val hay = tokens(heard)
            for (alt in alternatives) {
                val at = containsPhrase(hay, alt)
                if (at < 0) continue
                val article = word.article
                if (article == null) return SpokenCheck(Verdict.CORRECT, heard)
                val said = hay.getOrNull(at - 1)?.takeIf { it in ARTICLES }
                when (said) {
                    article -> return SpokenCheck(Verdict.CORRECT, heard)
                    null -> if (almost == null) almost = SpokenCheck(Verdict.ALMOST, heard)
                    else -> if (almost == null || almost.wrongArticle == null) {
                        almost = SpokenCheck(Verdict.ALMOST, heard, wrongArticle = said)
                    }
                }
            }
        }
        return almost ?: SpokenCheck(Verdict.WRONG, results.first())
    }

    /** Say the missing word(s) of the sentence — or the whole sentence. */
    fun checkCloze(word: Word, results: List<String>): SpokenCheck {
        if (results.isEmpty()) return SpokenCheck(Verdict.WRONG, "")
        val parts = word.cloze.map { tokens(it) }
        for (heard in results) {
            val hay = tokens(heard)
            if (parts.all { containsPhrase(hay, it) >= 0 }) return SpokenCheck(Verdict.CORRECT, heard)
        }
        // Separable verb with only the stem said, etc.
        for (heard in results) {
            val hay = tokens(heard)
            if (parts.size > 1 && parts.any { containsPhrase(hay, it) >= 0 }) return SpokenCheck(Verdict.ALMOST, heard)
        }
        return SpokenCheck(Verdict.WRONG, results.first())
    }

    /** Which tokens of [sentence] were heard, in order (for shadowing). */
    fun matchSentence(sentence: String, results: List<String>): List<Boolean> {
        // Whole space-separated words (so "E-Mail" or "geht's" stay one word, as on screen).
        fun words(s: String) = s.split(' ').map { normalize(it).replace(" ", "") }.filter { it.isNotEmpty() }
        val target = words(sentence)
        var best = List(target.size) { false }
        for (heard in results) {
            val hay = words(heard)
            // Greedy in-order alignment: each target word may use a heard word after the previous match.
            var j = 0
            val m = target.map { t ->
                var k = j
                var found = false
                while (k < hay.size && k < j + 4) {
                    if (same(hay[k], t)) {
                        found = true
                        j = k + 1
                        break
                    }
                    k++
                }
                found
            }
            if (m.count { it } > best.count { it }) best = m
        }
        return best
    }

    private fun distance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            cur.copyInto(prev)
        }
        return prev[b.length]
    }
}
