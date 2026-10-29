package io.github.deganegane.wortschatz.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Shared state of a left/right swipe decision. The gesture is detected on a large area (the
 * whole screen, via [swipeArea]) while only the card moves (via [swipeCard]), so you can swipe
 * with your thumb at the bottom of the screen.
 */
@Stable
class SwipeState internal constructor(private val scope: CoroutineScope) {
    val offset = Animatable(0f)

    /** Width of the swipe area in px; the decision threshold is a share of it. */
    var width by mutableFloatStateOf(1f)
        internal set

    /** Where the last card flew to, so the outgoing card can stay there while it fades. */
    var exit by mutableFloatStateOf(0f)
        internal set

    val threshold: Float get() = width * 0.22f

    /** -1..1: how far towards a decision the current drag is. */
    val progress: Float get() = (offset.value / threshold).coerceIn(-1f, 1f)

    internal fun drag(delta: Float) {
        scope.launch { offset.snapTo(offset.value + delta) }
    }

    internal fun release(onDecide: (right: Boolean) -> Unit) {
        scope.launch {
            if (abs(offset.value) > threshold) {
                val right = offset.value > 0
                val target = if (right) width * 1.3f else -width * 1.3f
                offset.animateTo(target, tween(140))
                exit = target
                onDecide(right)
                offset.snapTo(0f)
            } else {
                offset.animateTo(0f, tween(160))
            }
        }
    }

    internal fun cancel() {
        scope.launch { offset.animateTo(0f, tween(160)) }
    }
}

@Composable
fun rememberSwipeState(): SwipeState {
    val scope = rememberCoroutineScope()
    return remember { SwipeState(scope) }
}

/** Detects horizontal drags anywhere on this element (vertical scrolling inside keeps working). */
@Composable
fun Modifier.swipeArea(state: SwipeState, enabled: Boolean, key: Any?, onDecide: (right: Boolean) -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    return this
        .onSizeChanged { state.width = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(enabled, key) {
            if (!enabled) return@pointerInput
            var crossed = false
            detectHorizontalDragGestures(
                onDragStart = { crossed = false },
                onDragEnd = { state.release(onDecide) },
                onDragCancel = { state.cancel() }
            ) { change, delta ->
                change.consume()
                state.drag(delta)
                val over = abs(state.offset.value + delta) > state.threshold
                if (over != crossed) {
                    crossed = over
                    if (over) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
}

/** Moves and tilts the card with the drag. */
fun Modifier.swipeCard(state: SwipeState, current: Boolean = true): Modifier = graphicsLayer {
    val x = if (current) state.offset.value else state.exit
    translationX = x
    rotationZ = x / state.width.coerceAtLeast(1f) * 8f
}

/** Coloured wash and label shown over the card while dragging. Draw it last inside the card's Box. */
@Composable
fun BoxScope.SwipeHint(state: SwipeState, left: Pair<String, Color>, right: Pair<String, Color>) {
    val p = state.progress
    if (p == 0f) return
    val (label, color) = if (p > 0) right else left
    Box(Modifier.matchParentSize().background(color.copy(alpha = abs(p) * 0.22f)))
    Text(
        label,
        style = MaterialTheme.typography.titleLarge,
        color = color.copy(alpha = abs(p)),
        modifier = Modifier
            .align(if (p > 0) Alignment.BottomStart else Alignment.BottomEnd)
            .padding(24.dp)
    )
}
