package io.github.deganegane.wortschatz.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.Bucket
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.VoiceState
import io.github.deganegane.wortschatz.plural
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayTab(vm: AppViewModel, state: UiState, onOpenTest: () -> Unit, onOpenSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    val due = state.dueToday
    val fresh = state.newLeftToday
    val pinned = due + fresh > 0 || state.newAvailable > 0
    var showProgress by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Room for the start button pinned at the bottom.
                .padding(bottom = if (pinned) 88.dp else 16.dp)
        ) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Danas", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        LocalDate.ofEpochDay(state.today)
                            .format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale("hr", "HR")))
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Postavke", tint = cs.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            SummaryCard(state, due, fresh)

            if (state.canUnlock) {
                Spacer(Modifier.height(12.dp))
                Banner(
                    title = "Razina svladana!",
                    text = "Otključaj riječi ${state.vocab.size + 1}–${(state.settings.level + 1) * 1000}.",
                    action = "Otključaj",
                    color = cs.secondary
                ) { vm.setLevel(state.settings.level + 1) }
            }
            if (state.voice == VoiceState.NO_GERMAN || state.voice == VoiceState.NO_ENGINE) {
                Spacer(Modifier.height(12.dp))
                VoiceWarning()
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Vježbaj", modifier = Modifier.padding(start = 8.dp))
            val tiles = buildList {
                if (state.hasGaps) add(Tile(R.drawable.ic_grammar, "Gramatika", "10 rečenica", cs.primary) { vm.startGrammar() })
                add(Tile(R.drawable.ic_shadow, "Shadowing", "slušaj i ponovi", cs.secondary) { vm.startShadowing() })
                add(Tile(R.drawable.ic_quiz, "Test razine", "${state.tested} / ${state.vocab.size}", a.der, onOpenTest))
                if (state.hardWords.size >= 5) {
                    add(Tile(R.drawable.ic_hard, "Teške riječi", "${state.hardWords.size} ${plural(state.hardWords.size, "riječ", "riječi", "riječi")}", a.die) {
                        vm.startHardSession()
                    })
                }
            }
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { PracticeTile(it, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                modifier = Modifier.fillMaxWidth().clickable { showProgress = !showProgress }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Napredak", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "tvojih ${state.vocab.size} riječi · zadnja 2 tjedna",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (showProgress) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = showProgress, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            BreakdownBar(state)
                            Spacer(Modifier.height(12.dp))
                            Legend(
                                listOf(
                                    Bucket.KNOWN to state.count(Bucket.KNOWN),
                                    Bucket.MASTERED to state.count(Bucket.MASTERED),
                                    Bucket.LEARNING to state.count(Bucket.LEARNING),
                                    Bucket.TO_LEARN to state.count(Bucket.TO_LEARN),
                                    Bucket.UNTESTED to state.count(Bucket.UNTESTED)
                                )
                            )
                            Spacer(Modifier.height(20.dp))
                            ActivityChart(state)
                        }
                    }
                }
            }
        }

        // The day's main action, pinned within thumb reach above the navigation bar.
        if (pinned) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(cs.background)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (due + fresh > 0) {
                Button(onClick = { vm.startSession() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Kreni (${due + fresh})", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = { vm.startSession(extraNew = 5) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.surfaceContainerHigh, contentColor = cs.onSurface)
                ) { Text("Još 5 novih riječi", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** Progress ring plus today's numbers in one card. */
@Composable
private fun SummaryCard(state: UiState, due: Int, fresh: Int) {
    val cs = MaterialTheme.colorScheme
    val total = state.vocab.size.coerceAtLeast(1)
    val progress by animateFloatAsState(state.familiar.toFloat() / total, tween(900), label = "ring")
    val t = state.todayStat
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                val track = cs.onPrimaryContainer.copy(alpha = 0.14f)
                val fill = cs.primary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (progress > 0f) {
                        drawArc(fill, -90f, 360f * progress.coerceAtLeast(0.01f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.familiar.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("od ${state.vocab.size}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        due + fresh > 0 -> "Čeka te ${due + fresh} ${plural(due + fresh, "kartica", "kartice", "kartica")}"
                        else -> "Gotovo za danas!"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    MiniStat(t.reviews.toString(), "ponovljeno", Modifier.weight(1f))
                    MiniStat(t.newCards.toString(), "novih", Modifier.weight(1f))
                    MiniStat(state.streak.toString(), plural(state.streak, "dan", "dana", "dana") + " niz", Modifier.weight(1f), accent = state.streak > 0)
                }
                if (state.settings.level < state.maxLevel) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (state.solid.toFloat() / state.unlockTarget.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = cs.secondary,
                        trackColor = cs.onPrimaryContainer.copy(alpha = 0.14f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.solid} / ${state.unlockTarget} do razine ${state.settings.level + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = if (accent) cs.secondary else cs.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1)
    }
}

private class Tile(val icon: Int, val title: String, val sub: String, val color: Color, val onClick: () -> Unit)

/** A practice mode: tap anywhere on the tile to start. */
@Composable
private fun PracticeTile(t: Tile, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        modifier = modifier.clickable(onClick = t.onClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(t.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(t.icon), contentDescription = null, tint = t.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(t.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A slim notice with one action. */
@Composable
private fun Banner(title: String, text: String, action: String, color: Color, onAction: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun VoiceWarning() {
    val context = LocalContext.current
    Banner(
        title = "Nema njemačkog glasa",
        text = "Instaliraj Deutsch u postavkama govora na mobitelu.",
        action = "Otvori",
        color = MaterialTheme.colorScheme.error
    ) {
        try {
            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Some phones hide this screen; nothing else to open.
        }
    }
}

/** One horizontal bar split into the five buckets. */
@Composable
private fun BreakdownBar(state: UiState) {
    val order = listOf(Bucket.KNOWN, Bucket.MASTERED, Bucket.LEARNING, Bucket.TO_LEARN, Bucket.UNTESTED)
    val colors = order.map { bucketColor(it) }
    val total = state.vocab.size.coerceAtLeast(1).toFloat()
    val values = order.map { state.count(it) / total }
    Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))) {
        var x = 0f
        values.forEachIndexed { i, v ->
            val w = v * size.width
            if (w > 0f) {
                drawRect(colors[i], topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
    }
}

/** Reviews per day for the last 14 days. */
@Composable
private fun ActivityChart(state: UiState) {
    val cs = MaterialTheme.colorScheme
    val days = (13 downTo 0).map { state.today - it }
    val values = days.map { d ->
        val st = state.days[d]
        (st?.reviews ?: 0) + (st?.tested ?: 0) / 5 + (st?.shadowed ?: 0) + (st?.grammar ?: 0) + (st?.read ?: 0) * 5
    }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(10).toFloat()
    val bar = cs.primary
    val empty = cs.surfaceContainerHighest
    Column {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val gap = 6.dp.toPx()
            val w = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { i, v ->
                val h = if (v == 0) 4.dp.toPx() else (v / max).coerceIn(0.08f, 1f) * size.height
                drawRoundRect(
                    color = if (v == 0) empty else bar,
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 3, w / 3)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("prije 2 tj.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text("danas", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
