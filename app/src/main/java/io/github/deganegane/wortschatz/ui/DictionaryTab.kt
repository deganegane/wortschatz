package io.github.deganegane.wortschatz.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.Bucket
import io.github.deganegane.wortschatz.Status
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.Word
import io.github.deganegane.wortschatz.bucket
import io.github.deganegane.wortschatz.spoken
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryTab(vm: AppViewModel, state: UiState) {
    val cs = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Bucket.ALL) }
    var openRank by rememberSaveable { mutableStateOf<Int?>(null) }

    val needle = remember(query) { fold(query.trim()) }
    val folded = remember(state.words) { state.words.associate { it.rank to fold(it.searchKey) } }
    val list = remember(state.words, state.cards, needle, filter) {
        state.words.filter { w ->
            (filter == Bucket.ALL || state.cards[w.rank].bucket() == filter) &&
                (needle.isEmpty() || folded[w.rank]?.contains(needle) == true)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(needle, filter) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Rječnik", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Traži njemački ili hrvatski") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Obriši") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Bucket.entries) { b ->
                FilterChip(
                    selected = filter == b,
                    onClick = { filter = b },
                    label = { Text("${b.label} ${state.count(b)}") },
                    leadingIcon = if (b != Bucket.ALL) {
                        { StatusDot(b) }
                    } else null
                )
            }
        }
        if (list.isEmpty()) {
            Text(
                "Nema rezultata.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.rank }) { w ->
                WordRow(w, state.cards[w.rank].bucket()) { openRank = w.rank }
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 64.dp))
            }
        }
    }

    val rank = openRank
    if (rank != null) {
        val word = state.word(rank)
        if (word != null) {
            ModalBottomSheet(
                onDismissRequest = { openRank = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = cs.surfaceContainerLow
            ) {
                WordSheet(vm, state, word)
            }
        }
    }
}

@Composable
private fun WordRow(word: Word, bucket: Bucket, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            word.rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Column(Modifier.weight(1f)) {
            Headword(word, MaterialTheme.typography.titleMedium)
            Text(
                word.hr,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        StatusDot(bucket)
    }
}

@Composable
private fun WordSheet(vm: AppViewModel, state: UiState, word: Word) {
    val cs = MaterialTheme.colorScheme
    val card = state.cards[word.rank]
    val bucket = card.bucket()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("#${word.rank}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Headword(word, MaterialTheme.typography.headlineLarge)
            }
            SpeakButton(onClick = { vm.speak(word.spoken()) }, size = 52.dp)
        }
        Spacer(Modifier.height(6.dp))
        GrammarLine(word)
        Spacer(Modifier.height(16.dp))
        Text(word.hr, style = MaterialTheme.typography.titleLarge, color = cs.primary)
        Spacer(Modifier.height(16.dp))
        ExampleBlock(word, onSpeak = vm::speak)
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(bucket)
            Spacer(Modifier.width(8.dp))
            Text(statusText(bucket, card?.due), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (bucket) {
                Bucket.UNTESTED -> {
                    OutlinedButton(onClick = { vm.setStatus(word.rank, Status.KNOWN) }) { Text("Znam je") }
                    Button(onClick = { vm.setStatus(word.rank, Status.TO_LEARN) }) { Text("Želim je učiti") }
                }
                Bucket.KNOWN -> {
                    Button(onClick = { vm.setStatus(word.rank, Status.TO_LEARN) }) { Text("Ipak je želim učiti") }
                }
                Bucket.TO_LEARN -> {
                    OutlinedButton(onClick = { vm.setStatus(word.rank, Status.KNOWN) }) { Text("Ipak je znam") }
                }
                Bucket.LEARNING, Bucket.MASTERED, Bucket.ALL -> {
                    OutlinedButton(onClick = { vm.setStatus(word.rank, Status.KNOWN) }) { Text("Znam je, makni iz učenja") }
                }
            }
        }
        if (bucket != Bucket.UNTESTED) {
            TextButton(onClick = { vm.setStatus(word.rank, Status.UNTESTED) }) { Text("Vrati u test") }
        }
    }
}

private fun statusText(b: Bucket, due: Long?): String = when (b) {
    Bucket.UNTESTED -> "Još nije prošla test"
    Bucket.KNOWN -> "Označena kao poznata"
    Bucket.TO_LEARN -> "Čeka na učenje"
    Bucket.LEARNING, Bucket.MASTERED -> {
        val label = if (b == Bucket.MASTERED) "Naučena" else "Učiš je"
        if (due == null) label else "$label · sljedeće ponavljanje ${dueLabel(due)}"
    }
    Bucket.ALL -> ""
}

private fun dueLabel(due: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (val d = due - today) {
        in Long.MIN_VALUE..0L -> "danas"
        1L -> "sutra"
        in 2L..6L -> "za $d dana"
        else -> LocalDate.ofEpochDay(due).format(DateTimeFormatter.ofPattern("d. M. yyyy.", Locale("hr", "HR")))
    }
}

/** Lower-case and strip accents so "susse" finds "süß"-like spellings and "cas" finds "čaš". */
private fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace("ß", "ss")
        .replace("đ", "d")
