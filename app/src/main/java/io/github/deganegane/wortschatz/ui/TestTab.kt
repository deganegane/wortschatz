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

    Box(
        Modifier
            .fillMaxSize()
            // The whole screen is the swipe area, so the thumb can stay at the bottom.
            .swipeArea(swipe, enabled = rank != null && verify == null, key = rank) { right ->
                rank?.let { vm.testAnswer(it, right) }
            }
    ) {
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
            IconButton(onClick = { vm.testUndo() }, enabled = state.testUndo.isNotEmpty() && verify == null) {
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
                    "Povuci bilo gdje na ekranu udesno (znam) ili ulijevo (ne znam), ili dodirni karticu za prijevod.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        if (verify != null) {
            VerifyCard(vm, word, verify)
            Spacer(Modifier.height(32.dp))
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
                SwipeHint(swipe, left = "NE ZNAM" to a.bad, right = "ZNAM" to a.good)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "← ne znam  ·  povuci bilo gdje  ·  znam →",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { vm.testAnswer(rank, false) },
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = a.bad)
            ) { Text("Ne znam") }
            OutlinedButton(
                onClick = { vm.testAnswer(rank, true) },
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = a.good)
            ) { Text("Znam") }
        }
        Spacer(Modifier.height(32.dp))
    }
    }
}

/** Double-check of a "znam": which of four meanings is right? */
@Composable
private fun VerifyCard(vm: AppViewModel, word: Word, v: TestVerify) {
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
            Spacer(Modifier.height(16.dp))
            v.options.forEachIndexed { i, opt ->
                val answered = v.chosen != null
                val tint = when {
                    !answered -> cs.onSurface
                    i == v.correct -> a.good
                    i == v.chosen -> a.bad
                    else -> cs.onSurfaceVariant
                }
                OutlinedButton(
                    onClick = { vm.verifyChoose(i) },
                    enabled = !answered,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    border = BorderStroke(if (answered && (i == v.correct || i == v.chosen)) 2.dp else 1.dp, tint.copy(alpha = if (answered) 1f else 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tint, disabledContentColor = tint)
                ) { Text(opt, textAlign = TextAlign.Center) }
            }
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
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.verifyContinue() }, modifier = Modifier.fillMaxWidth()) { Text("Dalje") }
            }
        }
    }
}
