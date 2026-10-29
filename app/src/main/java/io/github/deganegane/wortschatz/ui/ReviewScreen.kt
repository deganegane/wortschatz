package io.github.deganegane.wortschatz.ui

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import io.github.deganegane.wortschatz.Mode
import io.github.deganegane.wortschatz.POS_LABELS
import io.github.deganegane.wortschatz.SpokenCheck
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.Bucket
import io.github.deganegane.wortschatz.Grade
import io.github.deganegane.wortschatz.QueueItem
import io.github.deganegane.wortschatz.Srs
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.Word
import io.github.deganegane.wortschatz.plural
import io.github.deganegane.wortschatz.spoken

@Composable
fun ReviewScreen(vm: AppViewModel, state: UiState) {
    val session = state.session ?: return
    val cs = MaterialTheme.colorScheme
    BackHandler { vm.closeSession() }

    val swipe = rememberSwipeState()
    val swipeKey = session.current to session.reviewed
    val swipeOn = session.current != null && (session.revealed || session.current?.isNew == true)
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // Once the answer is shown: swipe anywhere on the screen — left "Opet", right "Dobro".
                .swipeArea(swipe, enabled = swipeOn, key = swipeKey) { right ->
                    vm.answer(if (right) Grade.GOOD else Grade.AGAIN, expectReviewed = session.reviewed)
                }
        ) {
            // Top bar: close + progress
            val left = session.queue.size
            val done = session.reviewed
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeSession() }) { Icon(Icons.Filled.Close, contentDescription = "Zatvori") }
                LinearProgressIndicator(
                    progress = { if (done + left == 0) 1f else done.toFloat() / (done + left) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                    trackColor = cs.surfaceContainerHighest
                )
                Text(
                    "$left",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            val item = session.current
            if (item == null) {
                SessionSummary(vm, state)
                return@Column
            }
            val word = state.word(item.rank)
            if (word == null) {
                // Data changed under the session (shouldn't happen) — skip the card.
                LaunchedEffect(item) { vm.answer(Grade.GOOD) }
                return@Column
            }
            // New words are shown with their meaning straight away.
            val revealed = session.revealed || item.isNew
            val speaking = item.mode != Mode.RECOGNIZE && !item.isNew
            val useMic = speaking && state.settings.micAnswers && vm.recognitionAvailable
            val context = LocalContext.current
            val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            // Repeat aloud: recognition cards once the answer is shown (new words, or all if chosen).
            val canRepeat = !speaking && !state.settings.quiet && vm.recognitionAvailable
            val autoRepeat = canRepeat && revealed && micGranted &&
                ((item.isNew && state.settings.repeatNew) || state.settings.repeatAll)

            LaunchedEffect(item.rank, session.reviewed, revealed) {
                if (autoRepeat) {
                    vm.speakThenRepeat(word.spoken())
                    return@LaunchedEffect
                }
                if (!state.settings.autoSpeak) return@LaunchedEffect
                when {
                    !speaking -> vm.speak(word.spoken())
                    // After a spoken answer the correct version is already being played.
                    revealed && session.check == null ->
                        vm.speak(if (item.mode == Mode.CLOZE) word.example else word.spoken())
                }
            }

            AnimatedContent(
                targetState = item to session.reviewed,
                transitionSpec = { (fadeIn() + slideInHorizontally { it / 6 }) togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "card"
            ) { (qi, rev) ->
                val w = state.word(qi.rank) ?: word
                val current = rev == session.reviewed
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    CardFace(
                        vm = vm,
                        item = qi,
                        word = w,
                        revealed = if (current) revealed else true,
                        check = if (current) session.check else null,
                        isCurrent = current,
                        swipe = swipe
                    )
                }
            }

            // Bottom: microphone, reveal, or grade
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                when {
                    revealed -> {
                        if (canRepeat) {
                            val start = rememberMicStarter(onStart = vm::listen, onDenied = vm::micDenied)
                            RepeatRow(state.listen, session.repeat, word, onStart = start, onStop = vm::finishListening)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            "← Opet  ·  povuci bilo gdje  ·  Dobro →",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                        GradeButtons(
                            onGrade = {
                                swipe.clearExit()
                                vm.answer(it)
                            },
                            preview = Srs.preview(state.cards[item.rank], state.today, state.settings.retention),
                            suggested = session.check?.suggested
                        )
                    }
                    useMic -> {
                        val start = rememberMicStarter(onStart = vm::listen, onDenied = vm::micDenied)
                        MicRow(
                            listen = state.listen,
                            hint = if (item.mode == Mode.CLOZE) "Izgovori riječ koja fali (ili cijelu rečenicu)" else "Reci riječ na njemačkom",
                            onStart = start,
                            onStop = vm::finishListening
                        ) {
                            TextButton(onClick = { vm.reveal() }) { Text("Ne znam") }
                        }
                    }
                    else -> Button(onClick = { vm.reveal() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Prikaži odgovor", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CardFace(
    vm: AppViewModel,
    item: QueueItem,
    word: Word,
    revealed: Boolean,
    check: SpokenCheck?,
    isCurrent: Boolean,
    swipe: SwipeState
) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    val mode = if (item.isNew) Mode.RECOGNIZE else item.mode
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .swipeCard(swipe, current = isCurrent)
            .clickable(enabled = !revealed && mode == Mode.RECOGNIZE) { vm.reveal() }
    ) {
        Box {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    item.isNew -> Pill("NOVA RIJEČ", cs.primary, cs.onPrimary)
                    mode == Mode.RECALL -> Pill("RECI NA NJEMAČKOM", cs.secondaryContainer, cs.onSecondaryContainer)
                    mode == Mode.CLOZE -> Pill("DOPUNI REČENICU", cs.tertiaryContainer, cs.onTertiaryContainer)
                    else -> Pill("#${word.rank}", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (mode == Mode.RECOGNIZE || revealed) SpeakButton(onClick = { vm.speak(word.spoken()) })
            }
            Spacer(Modifier.height(24.dp))

            when (mode) {
                Mode.RECOGNIZE -> {
                    Headword(word, MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    GrammarLine(word, center = true)
                    AnimatedVisibility(visible = revealed, enter = fadeIn() + expandVertically()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(20.dp))
                            HorizontalDivider(color = cs.outlineVariant)
                            Spacer(Modifier.height(20.dp))
                            Text(word.hr, style = MaterialTheme.typography.titleLarge, color = cs.primary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(20.dp))
                            ExampleBlock(word, onSpeak = vm::speak)
                        }
                    }
                    if (!revealed) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "sjeti se značenja, pa provjeri",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Mode.RECALL -> {
                    Text(word.hr, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            word.article != null -> "imenica · reci je s članom"
                            word.cat != null -> "fraza · ${word.cat}"
                            else -> POS_LABELS[word.pos] ?: ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    AnimatedVisibility(visible = revealed, enter = fadeIn() + expandVertically()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(20.dp))
                            if (check != null) {
                                FeedbackBanner(check, word, cloze = false)
                                Spacer(Modifier.height(20.dp))
                            } else {
                                HorizontalDivider(color = cs.outlineVariant)
                                Spacer(Modifier.height(20.dp))
                            }
                            Headword(word, MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            GrammarLine(word, center = true)
                            Spacer(Modifier.height(20.dp))
                            ExampleBlock(word, onSpeak = vm::speak)
                        }
                    }
                }

                Mode.CLOZE -> {
                    val shown = if (revealed) highlighted(word, cs.primary) else AnnotatedString(word.clozeText ?: word.example)
                    Text(shown, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        word.exampleHr,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (!revealed) {
                        Spacer(Modifier.height(12.dp))
                        Pill("tražena riječ: ${word.hrShort}", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible = revealed, enter = fadeIn() + expandVertically()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(20.dp))
                            if (check != null) {
                                FeedbackBanner(check, word, cloze = true)
                                Spacer(Modifier.height(16.dp))
                            }
                            TextButton(onClick = { vm.speak(word.example) }) { Text("Poslušaj rečenicu") }
                            HorizontalDivider(color = cs.outlineVariant)
                            Spacer(Modifier.height(16.dp))
                            Headword(word, MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            GrammarLine(word, center = true)
                            Spacer(Modifier.height(8.dp))
                            Text(word.hr, style = MaterialTheme.typography.titleMedium, color = cs.primary, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (isCurrent) SwipeHint(swipe, left = "OPET" to a.bad, right = "DOBRO" to a.good)
        }
    }

    if (item.isNew && isCurrent) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            swipe.clearExit()
            vm.skipAsKnown()
        }, modifier = Modifier.align(Alignment.End)) {
            Text("Već znam ovu riječ")
        }
    }
}

@Composable
private fun GradeButtons(onGrade: (Grade) -> Unit, preview: Map<Grade, Int>, suggested: Grade?) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Grade.entries.forEach { g ->
            val (container, content) = when (g) {
                Grade.AGAIN -> a.bad.copy(alpha = 0.18f) to a.bad
                Grade.HARD -> cs.surfaceContainerHighest to cs.onSurface
                Grade.GOOD -> cs.primary to cs.onPrimary
                Grade.EASY -> cs.secondaryContainer to cs.onSecondaryContainer
            }
            GradeButton(
                grade = g,
                sub = Srs.label(preview[g] ?: 0),
                container = container,
                content = content,
                highlight = suggested == g,
                modifier = Modifier.weight(1f)
            ) { onGrade(g) }
        }
    }
}

@Composable
private fun GradeButton(
    grade: Grade,
    sub: String,
    container: Color,
    content: Color,
    highlight: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        border = if (highlight) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.label, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun SessionSummary(vm: AppViewModel, state: UiState) {
    val s = state.session ?: return
    val cs = MaterialTheme.colorScheme
    val moreAvailable = state.newAvailable > 0
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (s.reviewed == 0) {
            Text("Nema ničega za ponavljanje", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Za danas je sve gotovo. Ako želiš, dodaj još novih riječi.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text("Gut gemacht!", style = MaterialTheme.typography.displaySmall, color = cs.primary)
            Spacer(Modifier.height(8.dp))
            Text("Gotovo za danas.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SummaryStat(s.reviewed.toString(), plural(s.reviewed, "kartica", "kartice", "kartica"))
                SummaryStat(s.newSeen.toString(), plural(s.newSeen, "nova riječ", "nove riječi", "novih riječi"))
                SummaryStat(s.again.toString(), plural(s.again, "put", "puta", "puta") + " „opet”")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${state.streak} ${plural(state.streak, "dan", "dana", "dana")} zaredom",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { vm.closeSession() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Natrag") }
        if (moreAvailable) {
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { vm.startSession(extraNew = 5) }, modifier = Modifier.fillMaxWidth()) {
                Text("Još 5 novih riječi")
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Ponovi naglas": small microphone row under a shown answer; doesn't change the grade. */
@Composable
private fun RepeatRow(
    listen: io.github.deganegane.wortschatz.ListenState,
    result: SpokenCheck?,
    word: Word,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    val listening = listen.phase == io.github.deganegane.wortschatz.ListenPhase.LISTENING
    val (text, color) = when {
        listening -> (if (listen.partial.isBlank()) "Ponovi naglas…" else "„${listen.partial}“") to cs.primary
        result?.verdict == io.github.deganegane.wortschatz.Verdict.CORRECT -> "Dobro izgovoreno!" to a.good
        result?.verdict == io.github.deganegane.wortschatz.Verdict.ALMOST ->
            (if (word.article != null) "Skoro — reci i član: ${word.full}" else "Skoro — probaj još jednom") to cs.primary
        result != null -> (if (result.heard.isBlank()) "Nisam ništa čuo" else "Čuo sam „${result.heard}“ — probaj još jednom") to a.bad
        listen.phase == io.github.deganegane.wortschatz.ListenPhase.ERROR -> (listen.error ?: "Nešto je pošlo krivo.") to cs.error
        else -> "Ponovi riječ naglas" to cs.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = { if (listening) onStop() else onStart() }) {
            Icon(
                painterResource(if (listening) io.github.deganegane.wortschatz.R.drawable.ic_stop else io.github.deganegane.wortschatz.R.drawable.ic_mic),
                contentDescription = if (listening) "Gotovo" else "Ponovi naglas"
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
    }
}
