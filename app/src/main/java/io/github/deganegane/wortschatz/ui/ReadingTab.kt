package io.github.deganegane.wortschatz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.Bucket
import io.github.deganegane.wortschatz.Gloss
import io.github.deganegane.wortschatz.LEVEL_SIZE
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.Story
import io.github.deganegane.wortschatz.StoryRating
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.Word
import io.github.deganegane.wortschatz.bucket
import io.github.deganegane.wortschatz.spoken

private enum class StoryFilter(val label: String) { ALL("Sve"), STORIES("Priče"), DIALOGS("Dijalozi") }

/** List of graded mini-stories and dialogues; tap one to read it. */
@Composable
fun ReadingTab(vm: AppViewModel, state: UiState, openStory: String?, onOpen: (String?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val story = openStory?.let { id -> state.stories.firstOrNull { it.id == id } }
    if (story != null) {
        StoryScreen(vm, state, story, onBack = { onOpen(null) }, onOpen = { onOpen(it) })
        return
    }

    var filter by rememberSaveable { mutableStateOf(StoryFilter.ALL) }
    val shown = state.stories.filter {
        when (filter) {
            StoryFilter.ALL -> true
            StoryFilter.STORIES -> !it.isDialog
            StoryFilter.DIALOGS -> it.isDialog
        }
    }
    val byLevel = shown.groupBy { it.level }.toSortedMap()
    val hasDialogs = state.stories.any { it.isDialog }
    val next = state.recommendedStory

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
                Text("Čitanje", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Priče i dijalozi od riječi koje učiš. Dodirni riječ za prijevod, zadrži prst na rečenici za njen prijevod. " +
                        "Pročitano: ${state.storiesRead} od ${state.stories.size}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
        if (next != null) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clickable { onOpen(next.id) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (state.ratings.isEmpty()) "ZA POČETAK" else "PREPORUKA ZA TEBE",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(next.title, style = MaterialTheme.typography.titleLarge)
                        Text(storyMeta(next), style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
        }
        if (hasDialogs) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StoryFilter.entries) { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
        }
        if (state.stories.isEmpty()) {
            item { Text("Nema priča.", modifier = Modifier.padding(20.dp), color = cs.onSurfaceVariant) }
        }
        byLevel.forEach { (level, stories) ->
            item(key = "h$level") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Razina $level · ${level * 1000} riječi", modifier = Modifier.weight(1f))
                    if (level > state.settings.level) {
                        Text("još zaključano", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            items(stories, key = { it.id }) { s ->
                StoryRow(s, read = s.id in state.readIds, rating = state.ratings[s.id], dim = s.level > state.settings.level) { onOpen(s.id) }
            }
        }
    }
}

private fun storyMeta(s: Story): String =
    (if (s.isDialog) "dijalog · " else "") + "${s.wordCount} riječi" + if (s.newWords.isNotEmpty()) " · ${s.newWords.size} nove" else ""

@Composable
private fun StoryRow(story: Story, read: Boolean, rating: StoryRating?, dim: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (read) cs.surfaceContainerLow else cs.surfaceContainer,
            contentColor = if (dim) cs.onSurfaceVariant else cs.onSurface
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(story.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    storyMeta(story) + (rating?.let { " · ${it.label.lowercase()}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            if (read) Icon(Icons.Filled.Check, contentDescription = "Pročitano", tint = a.good)
        }
    }
}

/** What the reader tapped or pressed: a word (with its list entry or gloss) or a whole sentence. */
private sealed class Picked {
    data class WordPick(val token: String) : Picked()
    data class SentencePick(val paragraph: Int, val index: Int, val de: String, val hr: String) : Picked()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryScreen(vm: AppViewModel, state: UiState, story: Story, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.ensureForms() }

    val strict = state.settings.strictReading
    var showHr by rememberSaveable(story.id) { mutableStateOf(false) }
    var picked by remember(story.id) { mutableStateOf<Picked?>(null) }
    val read = story.id in state.readIds
    val rating = state.ratings[story.id]
    val scroll = rememberScrollState()
    LaunchedEffect(story.id) { scroll.scrollTo(0) }

    // Paragraphs as sentence pairs; stories without them count each paragraph as one sentence.
    val paragraphs: List<List<Pair<String, String>>> = remember(story.id) {
        story.sentences.ifEmpty {
            val de = story.de.split("\n").filter { it.isNotBlank() }
            val hr = story.hr.split("\n").filter { it.isNotBlank() }
            de.mapIndexed { i, p -> listOf(p to (hr.getOrNull(i) ?: "")) }
        }
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Razina ${story.level} · " + storyMeta(story),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = strict,
                        onClick = { vm.updateSettings { it.copy(strictReading = !strict) } },
                        label = { Text("Bez pomoći") }
                    )
                }
                Text(story.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (strict) "Bez prijevoda: čitaj i pokušaj razumjeti iz konteksta."
                    else "Dodir na riječ: prijevod · zadrži na rečenici: prijevod rečenice",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                val selected = picked as? Picked.SentencePick
                paragraphs.forEachIndexed { pi, sentences ->
                    TappableParagraph(
                        sentences = sentences.map { it.first },
                        dialog = story.isDialog,
                        highlight = selected?.takeIf { it.paragraph == pi }?.index ?: -1,
                        enabled = !strict,
                        onWord = { picked = Picked.WordPick(it) },
                        onSentence = { si -> picked = Picked.SentencePick(pi, si, sentences[si].first, sentences[si].second) }
                    )
                    if (showHr && !strict) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            sentences.joinToString(" ") { it.second },
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(if (story.isDialog) 10.dp else 16.dp))
                }
                if (story.newWords.isNotEmpty() && !strict) {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("Nove riječi u priči")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        story.newWords.take(5).forEach { w ->
                            Pill(w, cs.surfaceContainerHigh, cs.onSurface, Modifier.clickable { picked = Picked.WordPick(w) })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                RatingCard(rating) { vm.rateStory(story.id, it) }
                val next = state.recommendedStory?.takeIf { it.id != story.id }
                if (read && next != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { picked = null; onOpen(next.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sljedeće: ${next.title}")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            ActionBar(Modifier.background(cs.surfaceContainerLow).padding(vertical = 4.dp)) {
                ActionItem(painterResource(R.drawable.ic_back), "Natrag") { onBack() }
                ActionItem(painterResource(R.drawable.ic_volume), "Poslušaj") { vm.speak(spokenStory(story)) }
                ActionItem(painterResource(R.drawable.ic_slow), "Sporije") { vm.speakSlow(spokenStory(story)) }
                ActionItem(painterResource(R.drawable.ic_translate), if (showHr) "Sakrij" else "Prijevod", enabled = !strict) { showHr = !showHr }
                ActionItem(
                    painterResource(R.drawable.ic_check),
                    if (read) "Pročitano" else "Gotovo",
                    tint = if (read) LocalArticles.current.good else cs.onSurfaceVariant
                ) { if (!read) vm.markRead(story.id) else onBack() }
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()) {
                when (p) {
                    is Picked.SentencePick -> SentenceSheet(vm, p, story.isDialog)
                    is Picked.WordPick -> {
                        // Looked up while showing, so a tap before the forms index loaded still resolves.
                        val word = if (state.formsReady) vm.lookup(p.token) else null
                        WordInfo(vm, state, p.token, word, story.gloss(p.token.trim { !it.isLetter() }), loading = !state.formsReady)
                    }
                }
            }
        }
    }
}

/** Dialogues are read without the "Name:" prefixes. */
private fun spokenStory(s: Story): String =
    if (!s.isDialog) s.de else s.de.lines().filter { it.isNotBlank() }.joinToString("\n") { it.substringAfter(": ", it) }

@Composable
private fun RatingCard(rating: StoryRating?, onRate: (StoryRating) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (rating == null) "Kako ti je bilo?" else "Hvala! Prema tome biram sljedeću.", style = MaterialTheme.typography.titleSmall)
            Text("Prema ocjeni se predlaže sljedeća priča.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryRating.entries.forEach { r ->
                    val on = rating == r
                    Button(
                        onClick = { onRate(r) },
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (on) cs.primary else cs.surfaceContainerHighest,
                            contentColor = if (on) cs.onPrimary else cs.onSurface
                        )
                    ) { Text(r.label, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun SentenceSheet(vm: AppViewModel, p: Picked.SentencePick, dialog: Boolean) {
    val cs = MaterialTheme.colorScheme
    val de = if (dialog) p.de.substringAfter(": ", p.de) else p.de
    Text("REČENICA", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(p.de, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        SpeakButton(onClick = { vm.speak(de) }, size = 48.dp)
    }
    Spacer(Modifier.height(12.dp))
    Text(p.hr.ifBlank { "Prijevod rečenice nije dostupan." }, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = cs.primary)
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = { vm.speakSlow(de) }) { Text("Polako") }
}

@Composable
private fun WordInfo(vm: AppViewModel, state: UiState, token: String, word: Word?, gloss: Gloss?, loading: Boolean) {
    val cs = MaterialTheme.colorScheme
    val clean = token.trim { !it.isLetter() }
    if (loading) {
        Text(clean, style = MaterialTheme.typography.headlineSmall)
        Text("Učitavam…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        return
    }
    if (word == null) {
        if (gloss != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("riječ iz priče · nije na popisu", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(gloss.de.ifBlank { clean }, style = MaterialTheme.typography.headlineSmall)
                }
                SpeakButton(onClick = { vm.speak(gloss.de.ifBlank { clean }) }, size = 48.dp)
            }
            Spacer(Modifier.height(12.dp))
            Text(gloss.hr, style = MaterialTheme.typography.titleLarge, color = cs.primary)
            return
        }
        Text(clean, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ova riječ nije na listi (ime, broj ili oblik koji ne prepoznajem). Zadrži prst na rečenici za njen prijevod.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { vm.speak(clean) }) { Text("Izgovori") }
        return
    }
    val card = state.cards[word.rank]
    val bucket = card.bucket()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${word.rank}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                StatusDot(bucket)
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
    Spacer(Modifier.height(16.dp))
    val laterLevel = (word.rank + LEVEL_SIZE - 1) / LEVEL_SIZE
    when (bucket) {
        Bucket.UNTESTED, Bucket.KNOWN -> Button(onClick = { vm.addToLearning(word.rank) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (bucket == Bucket.KNOWN) "Ipak je dodaj u učenje" else "Dodaj u učenje")
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(bucket)
            Spacer(Modifier.width(8.dp))
            Text(
                if (bucket == Bucket.TO_LEARN) "Dodana u učenje — dolazi među prvim novim riječima" else statusText(bucket, card?.due),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
    }
    if (bucket == Bucket.TO_LEARN && laterLevel > state.settings.level) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Riječ je s razine $laterLevel, pa stiže na red kad otključaš tu razinu.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

/**
 * A paragraph where a tap on a word opens it and a long press on a sentence shows its
 * translation. In dialogues the "Name:" prefix is shown as the speaker.
 */
@Composable
private fun TappableParagraph(
    sentences: List<String>,
    dialog: Boolean,
    highlight: Int,
    enabled: Boolean,
    onWord: (String) -> Unit,
    onSentence: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val text = sentences.joinToString(" ")
    // Character range of every sentence in [text].
    val ranges = remember(text) {
        var start = 0
        sentences.map { s -> (start until start + s.length).also { start += s.length + 1 } }
    }
    val speaker = remember(text, dialog) {
        if (!dialog) 0 else Regex("^[^:]{1,30}: ").find(text)?.range?.let { it.last + 1 } ?: 0
    }
    val words = remember(text) { Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*").findAll(text).filter { it.range.first >= speaker }.toList() }
    val selectedBg = cs.secondaryContainer
    val annotated: AnnotatedString = remember(text, highlight, selectedBg, speaker) {
        buildAnnotatedString {
            append(text)
            if (speaker > 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = cs.primary), 0, speaker)
            ranges.getOrNull(highlight)?.let { addStyle(SpanStyle(background = selectedBg), it.first, it.last + 1) }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp),
        color = cs.onSurface,
        onTextLayout = { layout = it },
        // Dialogue turns sit in soft bubbles; padding comes before the gestures so positions match the text.
        modifier = (if (dialog) Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(cs.surfaceContainer).padding(horizontal = 14.dp, vertical = 10.dp) else Modifier)
            .then(if (!enabled) Modifier else Modifier.pointerInput(text) {
            detectTapGestures(
                onTap = { pos ->
                    val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                    words.firstOrNull { off in it.range || off == it.range.last + 1 }?.let { onWord(it.value) }
                },
                onLongPress = { pos ->
                    val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                    val i = ranges.indexOfFirst { off in it }.takeIf { it >= 0 } ?: ranges.indexOfLast { off >= it.first }
                    if (i >= 0) onSentence(i)
                }
            )
        })
    )
}

