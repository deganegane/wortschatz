package io.github.deganegane.wortschatz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.BLANK
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.Word

/** Ten sentences, each with one article / pronoun / ending to pick. */
@Composable
fun GrammarScreen(vm: AppViewModel, state: UiState) {
    val g = state.grammar ?: return
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    BackHandler { vm.closeGrammar() }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeGrammar() }) { Icon(Icons.Filled.Close, contentDescription = "Zatvori") }
                LinearProgressIndicator(
                    progress = { g.index.toFloat() / g.ranks.size.coerceAtLeast(1) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                    trackColor = cs.surfaceContainerHighest
                )
                Text(
                    "${(g.index + 1).coerceAtMost(g.ranks.size)} / ${g.ranks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            val word = g.current?.let { state.word(it) }
            val gap = word?.gap
            if (g.finished || word == null || gap == null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val total = g.correct + g.wrong
                    Text(
                        when {
                            total == 0 -> "Nema rečenica"
                            g.correct == total -> "Fehlerfrei!"
                            g.correct >= total * 0.7 -> "Gut gemacht!"
                            else -> "Weiter so!"
                        },
                        style = MaterialTheme.typography.displaySmall,
                        color = cs.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${g.correct} od $total točno",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { vm.closeGrammar() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Natrag") }
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { vm.startGrammar() }, modifier = Modifier.fillMaxWidth()) { Text("Još 10 rečenica") }
                }
                return@Column
            }

            val answered = g.chosen != null
            val correct = g.chosen == gap.blank

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "Koji oblik ide u prazninu?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            sentenceWithGap(word, if (answered) gap.blank else null, if (correct) a.good else cs.primary),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            word.exampleHr,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        Pill(gap.hint, cs.surfaceContainerHighest, cs.onSurfaceVariant)
                        if (answered) {
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                color = (if (correct) a.good else a.bad).copy(alpha = 0.14f),
                                contentColor = if (correct) a.good else a.bad,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (correct) "Točno!" else "Nije — ide „${gap.blank}“", style = MaterialTheme.typography.titleMedium)
                                    if (gap.explanation.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(gap.explanation, style = MaterialTheme.typography.bodySmall, color = cs.onSurface, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SpeakButton(onClick = { vm.speak(word.example) }, size = 40.dp)
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (!answered) {
                    gap.options.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { opt ->
                                Button(
                                    onClick = { vm.grammarChoose(opt) },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurface)
                                ) { Text(opt, style = MaterialTheme.typography.titleMedium) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    gap.options.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { opt ->
                                val isRight = opt == gap.blank
                                val isChosen = opt == g.chosen
                                val tint = when {
                                    isRight -> a.good
                                    isChosen -> a.bad
                                    else -> cs.onSurfaceVariant
                                }
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    border = if (isRight || isChosen) BorderStroke(2.dp, tint) else null,
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = if (isRight) tint.copy(alpha = 0.14f) else cs.surfaceContainerLow,
                                        disabledContentColor = tint
                                    )
                                ) { Text(opt) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = { vm.grammarNext() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (g.index + 1 >= g.ranks.size) "Rezultat" else "Dalje", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** The sentence with the gap blanked, or with the answer filled in and highlighted. */
private fun sentenceWithGap(word: Word, fill: String?, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val gap = word.gap
    val text = word.example
    val m = gap?.let { Regex("(?<![\\p{L}])" + Regex.escape(it.blank) + "(?![\\p{L}])").find(text) }
    if (gap == null || m == null) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, m.range.first))
    if (fill == null) {
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(BLANK) }
    } else {
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(fill) }
    }
    append(text.substring(m.range.last + 1))
}
