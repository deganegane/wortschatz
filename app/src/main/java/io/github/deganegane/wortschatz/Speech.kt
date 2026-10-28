package io.github.deganegane.wortschatz

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** A German voice the phone's engine offers. */
data class VoiceInfo(val name: String, val label: String, val network: Boolean, val quality: Int)

/** German pronunciation through the phone's text-to-speech engine. */
class Speech(context: Context, private val onState: (VoiceState) -> Unit) {

    private var tts: TextToSpeech? = null
    var state: VoiceState = VoiceState.LOADING
        private set

    /** German voices available on this phone, best first. */
    var voices: List<VoiceInfo> = emptyList()
        private set

    /** Name of the voice in use (after [chooseVoice]). */
    var currentVoice: String? = null
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
                    voices = listVoices(engine)
                    VoiceState.READY
                }
            }
            onState(state)
        }
    }

    private fun listVoices(engine: TextToSpeech): List<VoiceInfo> {
        val all: Set<Voice> = try {
            engine.voices ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        val german = all.filter { it.locale.language == "de" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        val sorted = german.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
        return sorted.mapIndexed { i, v ->
            val country = when (v.locale.country) {
                "AT" -> "austrijski"
                "CH" -> "švicarski"
                else -> "njemački"
            }
            val q = when {
                v.quality >= Voice.QUALITY_VERY_HIGH -> "vrhunska kvaliteta"
                v.quality >= Voice.QUALITY_HIGH -> "visoka kvaliteta"
                v.quality >= Voice.QUALITY_NORMAL -> "obična kvaliteta"
                else -> "niska kvaliteta"
            }
            VoiceInfo(
                name = v.name,
                label = "Glas ${i + 1} · $country · $q" + if (v.isNetworkConnectionRequired) " · online" else "",
                network = v.isNetworkConnectionRequired,
                quality = v.quality
            )
        }
    }

    /**
     * Use the named voice, or when null the best offline one (network voices sound better but
     * add a delay to every card).
     */
    fun chooseVoice(name: String?) {
        val engine = tts ?: return
        if (state != VoiceState.READY) return
        val pick = voices.firstOrNull { it.name == name }
            ?: voices.firstOrNull { !it.network }
            ?: voices.firstOrNull()
            ?: return
        val voice = try {
            engine.voices?.firstOrNull { it.name == pick.name }
        } catch (e: Exception) {
            null
        } ?: return
        if (engine.setVoice(voice) == TextToSpeech.SUCCESS) currentVoice = pick.name
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
