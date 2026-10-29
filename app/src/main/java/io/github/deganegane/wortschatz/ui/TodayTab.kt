package io.github.deganegane.wortschatz.ui

import android.content.ActivityNotFoundException
import android.content.Intent
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
    val due = state.dueToday
    val fresh = state.newLeftToday
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            // Room for the start button pinned at the bottom.
            .padding(bottom = 80.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wortschatz", style = MaterialTheme.typography.headlineMedium)
                Text(
                    LocalDate.ofEpochDay(state.today)
                        .format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale("hr", "HR")))
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Postavke")
            }
        }

        Spacer(Modifier.height(16.dp))
        HeroCard(state)

        Spacer(Modifier.height(16.dp))
        PlanCard(state)

        if (state.canUnlock) {
            Spacer(Modifier.height(16.dp))
            UnlockCard(vm, state)
        }

        if (state.hardWords.size >= 5) {
            Spacer(Modifier.height(16.dp))
            ExtraCard(
                icon = R.drawable.ic_hard,
                title = "Teške riječi (${state.hardWords.size})",
                text = "Riječi koje ti najčešće padaju, u govornim karticama. Kratko i ciljano.",
                container = cs.tertiaryContainer,
                content = cs.onTertiaryContainer,
                button = cs.tertiary,
                onButton = cs.onTertiary
            ) { vm.startHardSession() }
        }

        if (state.hasGaps) {
        Spacer(Modifier.height(16.dp))
        ExtraCard(
            icon = R.drawable.ic_grammar,
            title = "Gramatika",
            text = "10 rečenica s prazninom na članu, zamjenici ili nastavku. Padeži bez tablica.",
            container = cs.surfaceContainer,
            content = cs.onSurface,
            button = cs.primary,
            onButton = cs.onPrimary
        ) { vm.startGrammar() }
        }

        Spacer(Modifier.height(16.dp))
        ExtraCard(
            icon = R.drawable.ic_shadow,
            title = "Shadowing",
            text = "Slušaj i ponavljaj naglas 10 rečenica. Za izgovor i tečnost, oko 5 minuta.",
            container = cs.secondaryContainer,
            content = cs.onSecondaryContainer,
            button = cs.secondary,
            onButton = cs.onSecondary
        ) { vm.startShadowing() }

        if (state.voice == VoiceState.NO_GERMAN || state.voice == VoiceState.NO_ENGINE) {
            Spacer(Modifier.height(16.dp))
            VoiceWarning()
        }

        if (state.tested < state.vocab.size) {
            Spacer(Modifier.height(16.dp))
            TestCard(state, onOpenTest)
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Tvojih ${state.vocab.size} riječi")
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

        Spacer(Modifier.height(24.dp))
        SectionTitle("Zadnja dva tjedna")
        ActivityChart(state)
        Spacer(Modifier.height(32.dp))
    }

    // The day's main action, pinned within thumb reach above the navigation bar.
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(cs.background.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        when {
            due + fresh > 0 -> Button(onClick = { vm.startSession() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Kreni (${due + fresh})", style = MaterialTheme.typography.titleMedium)
            }
            state.newAvailable > 0 -> FilledTonalButton(
                onClick = { vm.startSession(extraNew = 5) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Još 5 novih riječi", style = MaterialTheme.typography.titleMedium) }
        }
    }
    }
}

@Composable
private fun HeroCard(state: UiState) {
    val cs = MaterialTheme.colorScheme
    val total = state.vocab.size.coerceAtLeast(1)
    val target = state.familiar.toFloat() / total
    val progress by animateFloatAsState(target, tween(900), label = "ring")
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                val track = cs.onPrimaryContainer.copy(alpha = 0.14f)
                val fill = cs.primary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (progress > 0f) {
                        drawArc(fill, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.familiar.toString(), style = MaterialTheme.typography.headlineLarge)
                    Text("od ${state.vocab.size}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("riječi koje znaš ili učiš", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_flame),
                        contentDescription = null,
                        tint = if (state.streak > 0) cs.tertiary else cs.onPrimaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${state.streak} ${plural(state.streak, "dan", "dana", "dana")} zaredom",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.height(6.dp))
                val t = state.todayStat
                Text(
                    "Danas: ${t.reviews} ${plural(t.reviews, "ponavljanje", "ponavljanja", "ponavljanja")}" +
                        if (t.newCards > 0) ", ${t.newCards} ${plural(t.newCards, "nova", "nove", "novih")}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun PlanCard(state: UiState) {
    val cs = MaterialTheme.colorScheme
    val due = state.dueToday
    val fresh = state.newLeftToday
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Za danas", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row {
                PlanStat("ponavljanje", due, Modifier.weight(1f))
                PlanStat("novih riječi", fresh, Modifier.weight(1f))
            }
            if (due + fresh == 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Sve je gotovo za danas. Bravo!",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun UnlockCard(vm: AppViewModel, state: UiState) {
    val cs = MaterialTheme.colorScheme
    val next = state.settings.level + 1
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Razina svladana!", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Znaš ili si svladao ${state.solid} od ${state.vocab.size} riječi. Otključaj riječi " +
                    "${state.vocab.size + 1}–${next * 1000}: idu u test i u dnevno učenje redom od najčešćih.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.setLevel(next) }) { Text("Otključaj ${next * 1000} riječi") }
        }
    }
}

@Composable
private fun ExtraCard(
    icon: Int,
    title: String,
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    button: androidx.compose.ui.graphics.Color,
    onButton: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(
                onClick = onClick,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = button, contentColor = onButton)
            ) { Text("Kreni") }
        }
    }
}

@Composable
private fun PlanStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value.toString(), style = MaterialTheme.typography.displaySmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TestCard(state: UiState, onOpenTest: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val total = state.vocab.size
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Provjera razine", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.tested == 0) {
                    "Prođi listu \"znam / ne znam\" da aplikacija zna što već znaš. Učit ćeš samo ono što ti treba."
                } else {
                    "Prošao si ${state.tested} od $total riječi. Nastavi kad uhvatiš pet minuta."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.tested.toFloat() / total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                trackColor = cs.surfaceContainerHighest
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenTest) {
                Text(if (state.tested == 0) "Započni test" else "Nastavi test")
            }
        }
    }
}

@Composable
private fun VoiceWarning() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Nema njemačkog glasa", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Za izgovor instaliraj njemački jezik u postavkama pretvaranja teksta u govor (Google govorne usluge → Instaliraj glasovne podatke → Deutsch).",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                try {
                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    // Some phones hide this screen; nothing else to open.
                }
            }) { Text("Otvori postavke govora") }
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
