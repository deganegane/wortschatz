package io.github.deganegane.wortschatz

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** German pronunciation through the phone's text-to-speech engine. */
class Speech(context: Context, private val onState: (VoiceState) -> Unit) {

    private var tts: TextToSpeech? = null
    var state: VoiceState = VoiceState.LOADING
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            state = if (status != TextToSpeech.SUCCESS || engine == null) {
                VoiceState.NO_ENGINE
            } else {
                val result = engine.setLanguage(Locale.GERMANY)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    VoiceState.NO_GERMAN
                } else {
                    VoiceState.READY
                }
            }
            onState(state)
        }
    }

    fun speak(text: String, slow: Boolean) {
        val engine = tts ?: return
        if (state != VoiceState.READY || text.isBlank()) return
        engine.setSpeechRate(if (slow) 0.7f else 0.95f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wortschatz")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

enum class VoiceState { LOADING, READY, NO_GERMAN, NO_ENGINE }

/**
 * What to say for a word: nouns with their article ("das Haus"); for the article entry and
 * phrases the headword as written.
 */
fun Word.spoken(): String = when {
    article != null -> "$article $de"
    else -> de.replace(" / ", ", ")
}
