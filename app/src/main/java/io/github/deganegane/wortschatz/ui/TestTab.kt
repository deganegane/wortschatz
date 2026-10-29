package io.github.deganegane.wortschatz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.Bucket
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.TestVerify
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.Word
import io.github.deganegane.wortschatz.spoken

@Composable
fun TestTab(vm: AppViewModel, state: UiState, onStartLearning: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    val total = state.vocab.size
    val rank = state.nextTestRank
    val verify = state.testVerify?.takeIf { it.rank == rank }
    val swipe = rememberSwipeState()

    val word = rank?.let { state.word(it) }
    var revealed by remember(rank) { mutableStateOf(false) }

    LaunchedEffect(rank) {
        if (word != null && state.settings.autoSpeak) vm.speak(word.spoken())
    }

    Box(
        Modifier
            .fillMaxSize()
            // The whole screen is the swipe area, so the thumb can stay at the bottom.
            .swipeArea(swipe, enabled = rank != null && verify == null, key = rank) { right ->
                rank?.let { vm.testAnswer(it, right) }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text("Provjera razine", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Znaš ${state.count(Bucket.KNOWN)}  ·  ne znaš ${state.count(Bucket.TO_LEARN)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
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

                if (rank == null || word == null) {
                    Spacer(Modifier.height(40.dp))
                    Text("Prošao si cijelu listu!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Od $total najčešćih riječi znaš ${state.count(Bucket.KNOWN)}. Ostale (${state.count(Bucket.TO_LEARN) + state.count(Bucket.LEARNING) + state.count(Bucket.MASTERED)}) " +
                            "uče se kroz dnevno ponavljanje, redom od najčešćih.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant
                    )
                    return@Column
                }

                Spacer(Modifier.height(20.dp))
                if (state.tested == 0) {
                    Text(
                        "Znaš li ovu riječ? Budi iskren: \"znam\" znači da je razumiješ bez razmišljanja. " +
                            "Povuci bilo gdje na ekranu udesno (znam) ili ulijevo (ne znam).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (verify != null) {
                    VerifyCard(word, verify)
                    Spacer(Modifier.height(24.dp))
                    return@Column
                }

                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .swipeCard(swipe)
                        .clickable { revealed = !revealed }
                ) {
                    Box {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Pill("#${word.rank}", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            Headword(word, MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            GrammarLine(word, center = true)
                            AnimatedVisibility(visible = revealed, enter = fadeIn() + expandVertically()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(Modifier.height(24.dp))
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
                        }
                        SwipeHint(swipe, left = "NE ZNAM" to a.bad, right = "ZNAM" to a.good)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Everything you tap lives down here, within reach of the thumb.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    rank == null || word == null -> {
                        if (state.testUndo.isNotEmpty()) {
                            ActionBar { ActionItem(painterResource(R.drawable.ic_undo), "Poništi") { vm.testUndo() } }
                            Spacer(Modifier.height(6.dp))
                        }
                        Button(onClick = onStartLearning, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Kreni učiti") }
                    }
                    verify != null -> {
                        // The four meanings sit down here, within thumb reach.
                        verify.options.forEachIndexed { i, opt ->
                            val answered = verify.chosen != null
                            val tint = when {
                                !answered -> cs.onSurface
                                i == verify.correct -> a.good
                                i == verify.chosen -> a.bad
                                else -> cs.onSurfaceVariant
                            }
                            OutlinedButton(
                                onClick = { vm.verifyChoose(i) },
                                enabled = !answered,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                border = BorderStroke(
                                    if (answered && (i == verify.correct || i == verify.chosen)) 2.dp else 1.dp,
                                    tint.copy(alpha = if (answered) 1f else 0.4f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tint, disabledContentColor = tint)
                            ) { Text(opt, textAlign = TextAlign.Center) }
                        }
                        if (verify.chosen != null) {
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = { vm.verifyContinue() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Dalje") }
                        }
                    }
                    else -> {
                        val r: Int = rank
                        val w = word
                        ActionBar {
                            ActionItem(painterResource(R.drawable.ic_undo), "Poništi", enabled = state.testUndo.isNotEmpty()) { vm.testUndo() }
                            ActionItem(painterResource(R.drawable.ic_translate), if (revealed) "Sakrij" else "Prijevod") { revealed = !revealed }
                            ActionItem(painterResource(R.drawable.ic_volume), "Izgovor") { vm.speak(w.spoken()) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { vm.testAnswer(r, false) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = a.bad)
                            ) { Text("← Ne znam") }
                            OutlinedButton(
                                onClick = { vm.testAnswer(r, true) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = a.good)
                            ) { Text("Znam →") }
                        }
                    }
                }
            }
        }
    }
}

/** Double-check of a "znam": which of four meanings is right? */
@Composable
private fun VerifyCard(word: Word, v: TestVerify) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Pill("PROVJERA", cs.tertiaryContainer, cs.onTertiaryContainer)
            Spacer(Modifier.height(16.dp))
            Headword(word, MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Rekao si da znaš. Što znači?", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (v.chosen != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (v.passed) "Točno — stvarno je znaš." else "Ipak ne — riječ ide u učenje.",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (v.passed) a.good else a.bad
                )
                if (!v.passed) {
                    Text(word.hr, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
