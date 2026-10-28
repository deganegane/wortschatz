package io.github.deganegane.wortschatz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.Story
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.Word
import io.github.deganegane.wortschatz.bucket
import io.github.deganegane.wortschatz.spoken

/** List of graded mini-stories; tap one to read it. */
@Composable
fun ReadingTab(vm: AppViewModel, state: UiState, openStory: String?, onOpen: (String?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val story = openStory?.let { id -> state.stories.firstOrNull { it.id == id } }
    if (story != null) {
        StoryScreen(vm, state, story, onBack = { onOpen(null) })
        return
    }

    val byLevel = remember(state.stories) { state.stories.groupBy { it.level }.toSortedMap() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
                Text("Čitanje", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Kratke priče složene od riječi koje učiš. Dodirni riječ za prijevod; pročitano: ${state.storiesRead} od ${state.stories.size}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (state.stories.isEmpty()) {
            item { Text("Nema priča.", modifier = Modifier.padding(20.dp), color = cs.onSurfaceVariant) }
        }
        byLevel.forEach { (level, stories) ->
            item(key = "h$level") {
                val locked = level > state.settings.level
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Razina $level · ${level * 1000} riječi", modifier = Modifier.weight(1f))
                    if (locked) {
                        Text(
                            "još zaključano",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
            items(stories, key = { it.id }) { s ->
                StoryRow(s, read = s.id in state.readIds, dim = s.level > state.settings.level) { onOpen(s.id) }
            }
        }
    }
}

@Composable
private fun StoryRow(story: Story, read: Boolean, dim: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (read) cs.surfaceContainerLow else cs.surfaceContainer,
            contentColor = if (dim) cs.onSurfaceVariant else cs.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(story.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${story.wordCount} riječi" + if (story.newWords.isNotEmpty()) " · ${story.newWords.size} nove" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            if (read) {
                Icon(Icons.Filled.Check, contentDescription = "Pročitano", tint = a.good)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryScreen(vm: AppViewModel, state: UiState, story: Story, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.ensureForms() }

    var showHr by rememberSaveable(story.id) { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Pair<String, Word?>?>(null) }
    val read = story.id in state.readIds

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Natrag") }
                Text(
                    "Razina ${story.level} · ${story.wordCount} riječi",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                SpeakButton(onClick = { vm.speak(story.de) }, size = 40.dp)
                Spacer(Modifier.width(12.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Text(story.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                val paragraphsDe = story.de.split("\n").filter { it.isNotBlank() }
                val paragraphsHr = story.hr.split("\n").filter { it.isNotBlank() }
                paragraphsDe.forEachIndexed { i, p ->
                    TappableParagraph(p, onWord = { token -> picked = token to vm.lookup(token) })
                    if (showHr) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            paragraphsHr.getOrNull(i) ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (story.newWords.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("Nove riječi u priči")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        story.newWords.take(5).forEach { w ->
                            Pill(w, cs.surfaceContainerHigh, cs.onSurface, Modifier.clickable { picked = w to vm.lookup(w) })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showHr = !showHr }) { Text(if (showHr) "Sakrij prijevod" else "Prijevod") }
                    if (!read) {
                        Button(onClick = { vm.markRead(story.id) }) { Text("Pročitano") }
                    } else {
                        FilledTonalButton(onClick = onBack) { Text("Natrag na popis") }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    val p = picked
    if (p != null) {
        ModalBottomSheet(
            onDismissRequest = { picked = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = cs.surfaceContainerLow
        ) {
            val (token, word) = p
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()) {
                if (word == null) {
                    Text(token.trim { !it.isLetter() }, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ova riječ nije na listi (ime, broj ili oblik koji ne prepoznajem). Uključi prijevod odlomka.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { vm.speak(token.trim { !it.isLetter() }) }) { Text("Izgovori") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${word.rank}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                StatusDot(state.cards[word.rank].bucket())
                            }
                            Headword(word, MaterialTheme.typography.headlineMedium)
                        }
                        SpeakButton(onClick = { vm.speak(word.spoken()) }, size = 48.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    GrammarLine(word)
                    Spacer(Modifier.height(12.dp))
                    Text(word.hr, style = MaterialTheme.typography.titleLarge, color = cs.primary)
                    Spacer(Modifier.height(16.dp))
                    ExampleBlock(word, onSpeak = vm::speak)
                }
            }
        }
    }
}

/** A paragraph where every word is a tap target. */
@Composable
private fun TappableParagraph(text: String, onWord: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val annotated = remember(text, cs.primary) {
        buildAnnotatedString {
            val re = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*")
            var i = 0
            for (m in re.findAll(text)) {
                append(text.substring(i, m.range.first))
                withLink(
                    LinkAnnotation.Clickable(
                        tag = m.value,
                        styles = TextLinkStyles(
                            style = SpanStyle(),
                            pressedStyle = SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline)
                        )
                    ) { link -> onWord((link as LinkAnnotation.Clickable).tag) }
                ) { append(m.value) }
                i = m.range.last + 1
            }
            append(text.substring(i))
        }
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp),
        color = cs.onSurface
    )
}
