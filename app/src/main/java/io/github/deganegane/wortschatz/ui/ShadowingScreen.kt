package io.github.deganegane.wortschatz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.plural
import kotlinx.coroutines.delay

/**
 * Shadowing: hear a sentence, repeat it straight away with the same rhythm, see which words
 * came through. Swipe right for the next sentence, left to hear this one again and repeat it.
 */
@Composable
fun ShadowingScreen(vm: AppViewModel, state: UiState) {
    val sh = state.shadowing ?: return
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    BackHandler { vm.closeShadowing() }
    val swipe = rememberSwipeState()
    val rank = sh.current
    val word = rank?.let { state.word(it) }
    val finished = sh.finished || word == null
    val micOk = vm.recognitionAvailable && !state.settings.quiet

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .swipeArea(swipe, enabled = !finished, key = sh.index to sh.attempts) { right ->
                    if (right) vm.shadowNext() else vm.shadowAgain()
                }
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeShadowing() }) { Icon(Icons.Filled.Close, contentDescription = "Zatvori") }
                LinearProgressIndicator(
                    progress = { sh.index.toFloat() / sh.ranks.size.coerceAtLeast(1) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                    trackColor = cs.surfaceContainerHighest
                )
                Text(
                    "${(sh.index + 1).coerceAtMost(sh.ranks.size)} / ${sh.ranks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (finished || word == null) {
                EndScreen(
                    title = "Sehr gut!",
                    text = "Ponovio si ${sh.done} ${plural(sh.done, "rečenicu", "rečenice", "rečenica")} naglas.",
                    again = "Još 10 rečenica",
                    onAgain = { vm.startShadowing() },
                    onBack = { vm.closeShadowing() }
                )
                return@Column
            }

            // Play each new sentence once on arrival, a moment after the transition.
            LaunchedEffect(sh.index) {
                delay(250)
                vm.speak(word.example)
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "Poslušaj, pa odmah ponovi naglas istim ritmom i melodijom. Ne razmišljaj o gramatici — oponašaj.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
                    modifier = Modifier.swipeCard(swipe)
                ) {
                    Box {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val match = sh.match
                            if (match == null) {
                                Text(word.example, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            } else {
                                Text(matched(word.example, match, a.good, a.bad), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                word.exampleHr,
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (match != null) {
                                Spacer(Modifier.height(16.dp))
                                val pct = if (match.isEmpty()) 0 else match.count { it } * 100 / match.size
                                Text(
                                    when {
                                        pct >= 90 -> "Odlično — $pct % riječi"
                                        pct >= 60 -> "Dobro — $pct % riječi. Crvene probaj još jednom."
                                        else -> "$pct % riječi. Poslušaj sporije pa ponovi."
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (pct >= 60) a.good else a.bad,
                                    textAlign = TextAlign.Center
                                )
                                if (sh.heard.isNotBlank()) {
                                    Text("Čuo sam: „${sh.heard}“", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        SwipeHint(swipe, left = "PONOVI" to cs.primary, right = "DALJE" to a.good)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ActionBar {
                    ActionItem(painterResource(R.drawable.ic_close), "Završi") { vm.closeShadowing() }
                    ActionItem(painterResource(R.drawable.ic_volume), "Poslušaj") { vm.speak(word.example) }
                    ActionItem(painterResource(R.drawable.ic_slow), "Sporije") { vm.speakSlow(word.example) }
                }
                SwipeLegend("← Ponovi  ·  povuci bilo gdje  ·  Dalje →")
                if (micOk) {
                    val start = rememberMicStarter(onStart = vm::listen, onDenied = vm::micDenied)
                    MicRow(
                        listen = state.listen,
                        hint = if (sh.match == null) "Dodirni mikrofon i ponovi rečenicu" else "Ponovi još jednom ili idi dalje",
                        onStart = {
                            vm.shadowRetry()
                            start()
                        },
                        onStop = vm::finishListening,
                        secondary = if (sh.match == null) {
                            { TextButton(onClick = { vm.shadowNext() }) { Text("Preskoči") } }
                        } else null,
                        primary = if (sh.match != null) {
                            { NextButton { vm.shadowNext() } }
                        } else null
                    )
                } else {
                    Text(
                        if (state.settings.quiet) "Tihi način: ponovi rečenicu šapatom ili u sebi, pa idi dalje."
                        else "Prepoznavanje govora nije dostupno, pa ponavljaj sam i idi dalje kad zvuči kao original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.shadowNext() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Dalje", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** The "how to swipe" line shown above the bottom controls. */
@Composable
fun SwipeLegend(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    )
}

/** "Dalje" as a solid pill on the thumb side. */
@Composable
fun NextButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.height(52.dp)) { Text("Dalje", style = MaterialTheme.typography.titleMedium) }
}

/** End of a round: the message in the middle, the buttons at the bottom within thumb reach. */
@Composable
fun EndScreen(title: String, text: String, again: String?, onAgain: () -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = cs.primary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        if (again != null) {
            FilledTonalButton(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(again) }
            Spacer(Modifier.height(10.dp))
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Natrag", style = MaterialTheme.typography.titleMedium) }
    }
}
