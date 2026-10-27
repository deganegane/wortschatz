package io.github.deganegane.wortschatz.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.deganegane.wortschatz.ListenPhase
import io.github.deganegane.wortschatz.ListenState
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.SpokenCheck
import io.github.deganegane.wortschatz.Verdict
import io.github.deganegane.wortschatz.Word

/**
 * Returns a function that starts listening, asking for the microphone permission first if
 * needed. [onDenied] runs when the person refuses.
 */
@Composable
fun rememberMicStarter(onStart: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else onDenied()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onStart()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Big round microphone button; pulses with the input level while listening. */
@Composable
fun MicButton(listen: ListenState, onStart: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val listening = listen.phase == ListenPhase.LISTENING
    val pulse by animateFloatAsState(if (listening) 1f + listen.level * 0.35f else 1f, tween(120), label = "pulse")
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(80.dp)
                    .scale(pulse * 1.15f)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.22f))
            )
        }
        Surface(
            shape = CircleShape,
            color = if (listening) cs.primary else cs.primaryContainer,
            contentColor = if (listening) cs.onPrimary else cs.onPrimaryContainer,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .clickable { if (listening) onStop() else onStart() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (listening) R.drawable.ic_stop else R.drawable.ic_mic),
                    contentDescription = if (listening) "Gotovo" else "Govori",
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

/** What the recogniser is hearing right now, or an error. */
@Composable
fun ListenStatus(listen: ListenState, idleHint: String) {
    val cs = MaterialTheme.colorScheme
    val text = when (listen.phase) {
        ListenPhase.LISTENING -> if (listen.partial.isBlank()) "Slušam…" else "„${listen.partial}“"
        ListenPhase.ERROR -> listen.error ?: "Nešto je pošlo krivo."
        else -> idleHint
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (listen.phase == ListenPhase.ERROR) cs.error else cs.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    )
}

/** Verdict banner after a spoken answer. */
@Composable
fun FeedbackBanner(check: SpokenCheck, word: Word, cloze: Boolean) {
    val a = LocalArticles.current
    val cs = MaterialTheme.colorScheme
    val (color, title) = when (check.verdict) {
        Verdict.CORRECT -> a.good to "Točno!"
        Verdict.ALMOST -> cs.primary to when {
            check.wrongArticle != null -> "Skoro — ${word.article} ${word.de}, ne ${check.wrongArticle}"
            !cloze && word.article != null -> "Točno — ali reci i član: ${word.full}"
            else -> "Skoro — fali dio odgovora"
        }
        Verdict.WRONG -> a.bad to if (check.heard.isBlank()) "Nisam ništa čuo" else "Nije to"
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (check.heard.isNotBlank()) {
                Text(
                    "Čuo sam: „${check.heard}“",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** The example sentence with the cloze parts highlighted. */
fun highlighted(word: Word, color: Color): AnnotatedString {
    val text = word.example
    val ranges = word.cloze.mapNotNull { part ->
        Regex("(?<![\\p{L}])" + Regex.escape(part) + "(?![\\p{L}])").find(text)?.range
    }.sortedBy { it.first }
    return buildAnnotatedString {
        var i = 0
        for (r in ranges) {
            if (r.first < i) continue
            append(text.substring(i, r.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text.substring(r.first, r.last + 1)) }
            i = r.last + 1
        }
        append(text.substring(i))
    }
}

/** Shadowing: the sentence with heard words in green and missed ones in red. */
fun matched(sentence: String, match: List<Boolean>, good: Color, bad: Color): AnnotatedString {
    val words = sentence.split(' ')
    // Tokens are counted the same way as SpeechCheck.tokens (punctuation-only chunks don't count).
    var t = 0
    return buildAnnotatedString {
        words.forEachIndexed { i, w ->
            if (i > 0) append(' ')
            val counts = w.any { it.isLetterOrDigit() }
            if (!counts) {
                append(w)
            } else {
                val ok = match.getOrNull(t) ?: false
                t++
                withStyle(SpanStyle(color = if (ok) good else bad, fontWeight = FontWeight.Medium)) { append(w) }
            }
        }
    }
}

@Composable
fun MicRow(
    listen: ListenState,
    hint: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    left: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ListenStatus(listen, hint)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { left() }
            MicButton(listen, onStart = onStart, onStop = onStop)
            Spacer(Modifier.weight(1f))
        }
    }
}
