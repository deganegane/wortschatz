package io.github.deganegane.wortschatz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.Bucket
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.spoken
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TestTab(vm: AppViewModel, state: UiState, onStartLearning: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val total = state.words.size
    val rank = state.nextTestRank

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Provjera razine", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Znaš ${state.count(Bucket.KNOWN)}  ·  ne znaš ${state.count(Bucket.TO_LEARN)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.testUndo() }, enabled = state.testUndo.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = "Poništi")
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.tested.toFloat() / total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            trackColor = cs.surfaceContainerHighest
        )
        Text(
            "${state.tested} / $total",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (rank == null) {
            Spacer(Modifier.height(40.dp))
            Text("Prošao si cijelu listu!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Od $total najčešćih riječi znaš ${state.count(Bucket.KNOWN)}. Ostale (${state.count(Bucket.TO_LEARN) + state.count(Bucket.LEARNING) + state.count(Bucket.MASTERED)}) " +
                    "uče se kroz dnevno ponavljanje, redom od najčešćih.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onStartLearning) { Text("Kreni učiti") }
            return@Column
        }

        val word = state.word(rank) ?: return@Column
        var revealed by remember(rank) { mutableStateOf(false) }

        LaunchedEffect(rank) {
            if (state.settings.autoSpeak) vm.speak(word.spoken())
        }

        Spacer(Modifier.height(20.dp))
        if (state.tested == 0) {
            Text(
                "Znaš li ovu riječ? Budi iskren: \"znam\" znači da je razumiješ bez razmišljanja. " +
                    "Povuci karticu udesno (znam) ili ulijevo (ne znam), ili dodirni za prijevod.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        SwipeCard(
            key = rank,
            onSwipe = { known -> vm.testAnswer(rank, known) }
        ) { tint ->
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().clickable { revealed = !revealed }
            ) {
                Box {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Pill("#${word.rank}", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            SpeakButton(onClick = { vm.speak(word.spoken()) })
                        }
                        Spacer(Modifier.height(24.dp))
                        Headword(word, MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        GrammarLine(word, center = true)
                        Spacer(Modifier.height(24.dp))
                        AnimatedVisibility(visible = revealed, enter = fadeIn() + expandVertically()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    word.hr,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = cs.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                ExampleBlock(word, onSpeak = vm::speak)
                            }
                        }
                        if (!revealed) {
                            Text(
                                "dodirni za prijevod",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    tint()
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        val a = LocalArticles.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { vm.testAnswer(rank, false) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = a.bad)
            ) { Text("Ne znam", style = MaterialTheme.typography.titleMedium) }
            Button(
                onClick = { vm.testAnswer(rank, true) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = a.good, contentColor = cs.surface)
            ) { Text("Znam", style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Horizontal swipe: right = known, left = not known. The content receives an overlay that
 * shows the coloured hint while dragging (draw it last inside the card's Box).
 */
@Composable
private fun SwipeCard(
    key: Any,
    onSwipe: (known: Boolean) -> Unit,
    content: @Composable (overlay: @Composable BoxScope.() -> Unit) -> Unit
) {
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    val a = LocalArticles.current

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / width * 8f
            }
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val threshold = width * 0.28f
                            if (abs(offset.value) > threshold) {
                                val known = offset.value > 0
                                offset.animateTo(if (known) width * 1.4f else -width * 1.4f)
                                onSwipe(known)
                            } else {
                                offset.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } }
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag) }
                }
            }
    ) {
        content {
            val p = (offset.value / (width * 0.28f)).coerceIn(-1f, 1f)
            if (p != 0f) {
                val color = if (p > 0) a.good else a.bad
                Box(
                    Modifier
                        .matchParentSize()
                        .background(color.copy(alpha = abs(p) * 0.22f))
                )
                Text(
                    if (p > 0) "ZNAM" else "NE ZNAM",
                    style = MaterialTheme.typography.titleLarge,
                    color = color.copy(alpha = abs(p)),
                    modifier = Modifier
                        .align(if (p > 0) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(24.dp)
                )
            }
        }
    }
}
