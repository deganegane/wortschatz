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
    /** One recogniser for the app's lifetime; recreating it each time makes Google's busy. */
    private var recognizer: SpeechRecognizer? = null
    private var partial = ""

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun obtain(): SpeechRecognizer? {
        recognizer?.let { return it }
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            return null
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (recognizer === r) onLevel(0f, "")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2..10 dB in practice.
                if (recognizer === r) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f), partial)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (recognizer !== r) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT ->
                        if (partial.isNotBlank()) onResult(listOf(partial)) else onError("Nisam ništa čuo — pokušaj ponovno.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onError("Aplikacija nema dopuštenje za mikrofon.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        onError("Za prepoznavanje treba internet (ili preuzet njemački offline paket).")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> onError("Prepoznavanje je zauzeto, pokušaj ponovno.")
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        onError("Njemački nije dostupan za prepoznavanje govora na ovom mobitelu.")
                    else -> onError("Prepoznavanje nije uspjelo (greška $error).")
                }
            }
            override fun onResults(results: Bundle?) {
                if (recognizer !== r) return
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.toList().orEmpty()
                when {
                    list.isNotEmpty() -> onResult(list)
                    partial.isNotBlank() -> onResult(listOf(partial))
                    else -> onError("Nisam ništa čuo — pokušaj ponovno.")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer !== r) return
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: partial
                onLevel(0.5f, partial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = r
        return r
    }

    fun start() {
        val r = obtain() ?: run {
            onError("Prepoznavanje govora nije dostupno.")
            return
        }
        r.cancel()
        partial = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            onError("Prepoznavanje govora nije se uspjelo pokrenuti.")
        }
    }

    /** Stop listening and let the recogniser deliver what it has. */
    fun finish() {
        recognizer?.stopListening()
    }

    /** Abandon the current utterance (no callbacks follow). */
    fun stop() {
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}

/** Compares what was heard with what was expected. */
object SpeechCheck {

    private val ARTICLES = setOf("der", "die", "das")

    private val NUMBERS = mapOf(
        "0" to "null", "1" to "eins", "2" to "zwei", "3" to "drei", "4" to "vier", "5" to "fünf",
        "6" to "sechs", "7" to "sieben", "8" to "acht", "9" to "neun", "10" to "zehn", "11" to "elf",
        "12" to "zwölf", "20" to "zwanzig", "30" to "dreißig", "50" to "fünfzig", "100" to "hundert",
        "1000" to "tausend"
    )

    /** Accents that aren't part of German spelling ("Café" → "cafe"); ä/ö/ü stay. */
    private fun stripForeign(s: String): String =
        s.replace('é', 'e').replace('è', 'e').replace('ê', 'e').replace('á', 'a').replace('à', 'a')
            .replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ç', 'c')

    fun normalize(s: String): String =
        stripForeign(s.lowercase())
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { NUMBERS[it] ?: it }
            .replace("ß", "ss")

    fun tokens(s: String): List<String> = normalize(s).split(' ').filter { it.isNotEmpty() }

    /** Same word, allowing one slip for longer words (recognisers mangle endings). */
    fun same(a: String, b: String): Boolean {
        if (a == b) return true
        if (a == "eins" && (b == "ein" || b == "eine")) return true
        if (b == "eins" && (a == "ein" || a == "eine")) return true
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
        if (word.isPhrase) return checkPhrase(word, results)
        val target = tokens(word.de.replace(Regex("^sich "), ""))
        val alternatives = when {
            word.rank == 1 -> listOf(listOf("der"), listOf("die"), listOf("das"))
            // "E-Mail" may come back as "Email", "wie viel" as "wieviel".
            target.size > 1 -> listOf(target, listOf(target.joinToString("")))
            else -> listOf(target)
        }
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

    /** A whole phrase: most of its words, in order, is good enough for the recogniser's slips. */
    private fun checkPhrase(word: Word, results: List<String>): SpokenCheck {
        var best = 0.0
        var bestHeard = results.first()
        for (heard in results) {
            val m = matchSentence(word.de, listOf(heard))
            val share = if (m.isEmpty()) 0.0 else m.count { it }.toDouble() / m.size
            if (share > best) {
                best = share
                bestHeard = heard
            }
        }
        return when {
            best >= 0.75 -> SpokenCheck(Verdict.CORRECT, bestHeard)
            best >= 0.5 -> SpokenCheck(Verdict.ALMOST, bestHeard)
            else -> SpokenCheck(Verdict.WRONG, results.first())
        }
    }

    /** Say the missing word(s) of the sentence — or the whole sentence. */
    fun checkCloze(word: Word, results: List<String>): SpokenCheck {
        if (results.isEmpty()) return SpokenCheck(Verdict.WRONG, "")
        val parts = word.cloze.map { tokens(it) }
        for (heard in results) {
            val hay = tokens(heard)
            if (parts.all { containsPhrase(hay, it) >= 0 }) return SpokenCheck(Verdict.CORRECT, heard)
        }
        // Separable verb with only the main part said ("stehe" without "auf").
        if (parts.size > 1) {
            val main = parts.maxBy { p -> p.sumOf { it.length } }
            for (heard in results) {
                if (containsPhrase(tokens(heard), main) >= 0) return SpokenCheck(Verdict.ALMOST, heard)
            }
        }
        // The dictionary form instead of the one the sentence needs ("aufstehen" for "stehe … auf").
        val base = tokens(word.de.removePrefix("sich "))
        for (heard in results) {
            if (containsPhrase(tokens(heard), base) >= 0) return SpokenCheck(Verdict.ALMOST, heard)
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
