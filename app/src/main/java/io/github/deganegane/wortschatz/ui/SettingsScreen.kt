package io.github.deganegane.wortschatz.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.deganegane.wortschatz.UNLOCK_SHARE
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.deganegane.wortschatz.AppViewModel
import io.github.deganegane.wortschatz.BuildConfigInfo
import io.github.deganegane.wortschatz.Practice
import io.github.deganegane.wortschatz.ThemeMode
import io.github.deganegane.wortschatz.UiState
import io.github.deganegane.wortschatz.VoiceState

private enum class SettingsPage(val title: String) {
    LEVEL("Razina"),
    LEARNING("Učenje"),
    SPEAKING("Govor i mikrofon"),
    VOICE("Izgovor"),
    REMINDER("Podsjetnik"),
    LOOK("Izgled")
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, state: UiState, onBack: () -> Unit) {
    val s = state.settings
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val a = LocalArticles.current
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var showTime by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var voicePicker by remember { mutableStateOf(false) }

    BackHandler(enabled = page != null) { page = null }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(reminderOn = granted) }
    }

    val currentVoice = state.voices.firstOrNull { it.name == s.voiceName }
        ?: state.voices.firstOrNull { !it.network }
        ?: state.voices.firstOrNull()

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(page?.title ?: "Postavke") },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) page = null else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Natrag")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            when (page) {
                null -> {
                    Spacer(Modifier.height(8.dp))
                    Panel {
                        val rows = listOf(
                            Triple(SettingsPage.LEVEL, cs.secondary,
                                "${s.level * 1000} riječi" + if (s.level < state.maxLevel) " · ${state.solid} / ${state.unlockTarget} do sljedeće" else ""),
                            Triple(SettingsPage.LEARNING, cs.primary,
                                "${s.newPerDay} novih dnevno · ${s.practice.label} · pamćenje ${(s.retention * 100).toInt()} %"),
                            Triple(SettingsPage.SPEAKING, a.der,
                                when {
                                    s.quiet -> "Tihi način uključen"
                                    s.speakAnswers -> "Odgovori glasom" + if (s.repeatNew) " · ponovi nove" else ""
                                    else -> "Bez mikrofona"
                                }),
                            Triple(SettingsPage.VOICE, a.das,
                                (currentVoice?.label?.substringBefore(" · ") ?: "Zadani glas") +
                                    (if (currentVoice?.network == true) " (online)" else "") +
                                    if (s.autoSpeak) " · automatski" else ""),
                            Triple(SettingsPage.REMINDER, a.die,
                                if (s.reminderOn) "Svaki dan u %02d:%02d".format(s.reminderHour, s.reminderMinute) else "Isključen"),
                            Triple(SettingsPage.LOOK, cs.onSurfaceVariant,
                                "Tema: ${s.themeMode.label.lowercase()}" + if (s.dynamicColor) " · boje iz pozadine" else " · paleta Jadran")
                        )
                        rows.forEachIndexed { i, (p, color, summary) ->
                            if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 56.dp))
                            NavRow(p.title, summary, color) { page = p }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Panel {
                        Row(
                            Modifier.fillMaxWidth().clickable { confirmReset = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Resetiraj napredak", style = MaterialTheme.typography.titleSmall, color = cs.error)
                        }
                    }
                    Text(
                        "Wortschatz ${BuildConfigInfo.versionName(context)} · ${state.allCount} riječi i ${state.phrases.size} fraza · " +
                            "font Space Grotesk (SIL OFL)",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp)
                    )
                }

                SettingsPage.LEVEL -> Panel(padded = true) { LevelSection(vm, state) }

                SettingsPage.LEARNING -> {
                    Panel(padded = true) {
                        InfoLabel("Novih riječi dnevno", "10 je dobra mjera uz par minuta ponavljanja dnevno.")
                        Choice(listOf(5, 10, 15, 20), s.newPerDay, { it.toString() }) { n -> vm.updateSettings { it.copy(newPerDay = n) } }
                        Spacer(Modifier.height(18.dp))
                        InfoLabel(
                            "Način vježbe",
                            "Klasično: vidiš njemačku riječ i sjetiš se značenja. Govor: izgovaraš njemačku riječ ili dopunjavaš rečenicu. " +
                                "Mješovito: nove riječi prvo prepoznaješ, a zatim ih sve češće izgovaraš (preporučeno)."
                        )
                        Choice(Practice.entries, s.practice, { it.label }) { p -> vm.updateSettings { it.copy(practice = p) } }
                        Spacer(Modifier.height(18.dp))
                        InfoLabel(
                            "Ciljano pamćenje",
                            "Koliko je vjerojatno da se sjetiš riječi kad dođe na red (FSRS). Više znači češća ponavljanja."
                        )
                        Choice(listOf(0.85, 0.9, 0.95), s.retention, { "${(it * 100).toInt()} %" }) { r ->
                            vm.updateSettings { it.copy(retention = r) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Panel(padded = true) {
                        ToggleRow("Fraze za razgovor", "Svaka treća nova kartica", s.phrasesOn) { v -> vm.updateSettings { it.copy(phrasesOn = v) } }
                        ToggleRow("Provjera u testu razine", "Svaki deseti \"znam\"", s.verifyKnown) { v -> vm.updateSettings { it.copy(verifyKnown = v) } }
                    }
                }

                SettingsPage.SPEAKING -> {
                    Panel(padded = true) {
                        ToggleRow(
                            "Odgovaraj glasom",
                            if (vm.recognitionAvailable) "Mikrofon provjerava riječ i član" else "Nije dostupno na ovom mobitelu",
                            s.speakAnswers
                        ) { v -> vm.updateSettings { it.copy(speakAnswers = v) } }
                        ToggleRow("Ponovi nove riječi naglas", "Mikrofon se sam uključi", s.repeatNew) { v -> vm.updateSettings { it.copy(repeatNew = v) } }
                        ToggleRow("Uvijek ponovi naglas", "Na svakoj kartici prepoznavanja", s.repeatAll) { v -> vm.updateSettings { it.copy(repeatAll = v) } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Panel(padded = true) {
                        ToggleRow("Tihi način", "Bez mikrofona, za javna mjesta", s.quiet) { v -> vm.updateSettings { it.copy(quiet = v) } }
                    }
                }

                SettingsPage.VOICE -> {
                    Panel(padded = true) {
                        ToggleRow("Automatski izgovor", "Kad se kartica pokaže", s.autoSpeak) { v -> vm.updateSettings { it.copy(autoSpeak = v) } }
                        ToggleRow("Sporiji izgovor", null, s.slowSpeech) { v -> vm.updateSettings { it.copy(slowSpeech = v) } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Panel {
                        if (state.voices.size > 1) {
                            NavRow("Glas", currentVoice?.label ?: "Zadani", a.das) { voicePicker = true }
                            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 56.dp))
                        }
                        NavRow("Probaj glas", "„Guten Tag! Wie geht es dir?“", cs.primary) { vm.speak("Guten Tag! Wie geht es dir?") }
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 56.dp))
                        NavRow(
                            "Postavke govora na mobitelu",
                            when (state.voice) {
                                VoiceState.READY -> "Njemački glas je spreman"
                                VoiceState.LOADING -> "Učitavam glas…"
                                VoiceState.NO_GERMAN -> "Nema njemačkog glasa — instaliraj ga ovdje"
                                VoiceState.NO_ENGINE -> "Nije pronađen pretvarač teksta u govor"
                            },
                            cs.onSurfaceVariant
                        ) {
                            try {
                                context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: ActivityNotFoundException) {
                                // Not available on this phone.
                            }
                        }
                    }
                }

                SettingsPage.REMINDER -> Panel(padded = true) {
                    ToggleRow("Dnevni podsjetnik", "Samo ako taj dan još nisi vježbao", s.reminderOn) { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.updateSettings { it.copy(reminderOn = on) }
                        }
                    }
                    if (s.reminderOn) {
                        Row(
                            Modifier.fillMaxWidth().clickable { showTime = true }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vrijeme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                "%02d:%02d".format(s.reminderHour, s.reminderMinute),
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.primary
                            )
                        }
                    }
                }

                SettingsPage.LOOK -> Panel(padded = true) {
                    InfoLabel("Tema", null)
                    Choice(ThemeMode.entries, s.themeMode, { it.label }) { m -> vm.updateSettings { it.copy(themeMode = m) } }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(Modifier.height(10.dp))
                        ToggleRow("Boje iz pozadine", "Material You umjesto palete Jadran", s.dynamicColor) { v ->
                            vm.updateSettings { it.copy(dynamicColor = v) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (voicePicker) {
        ModalBottomSheet(
            onDismissRequest = { voicePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = cs.surfaceContainerLow
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text("Glas", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Online glasovi zvuče prirodnije, ali svaka kartica čeka internet. Dodirni za probu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                state.voices.forEach { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.updateSettings { it.copy(voiceName = v.name) }
                                vm.speak("Guten Tag! Wie geht es dir?")
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = v.name == currentVoice?.name, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(v.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showTime) {
        val picker = rememberTimePickerState(initialHour = s.reminderHour, initialMinute = s.reminderMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(reminderHour = picker.hour, reminderMinute = picker.minute) }
                    showTime = false
                }) { Text("U redu") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Odustani") } },
            text = { TimePicker(state = picker) }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Resetirati napredak?") },
            text = { Text("Ovo briše sve oznake \"znam / ne znam\", raspored ponavljanja i niz dana. Postavke ostaju. Ne može se poništiti.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetProgress()
                    confirmReset = false
                }) { Text("Resetiraj", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Odustani") } }
        )
    }
}

/** A rounded surface that groups rows. */
@Composable
private fun Panel(padded: Boolean = false, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().then(if (padded) Modifier.padding(16.dp) else Modifier)) { content() }
    }
}

/** A settings group row: coloured dot, title, one-line summary, chevron. */
@Composable
private fun NavRow(title: String, summary: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun LevelSection(vm: AppViewModel, state: UiState) {
    val s = state.settings
    var confirmEarly by remember { mutableStateOf(false) }
    Text("Aktivna lista: ${s.level * 1000} najčešćih riječi", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    if (s.level < state.maxLevel) {
        Text(
            "Sljedećih 1000 otključava se kad znaš ili svladaš ${(UNLOCK_SHARE * 100).toInt()} % trenutne liste: " +
                "${state.solid} / ${state.unlockTarget}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (state.solid.toFloat() / state.unlockTarget.coerceAtLeast(1)).coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.setLevel(s.level + 1) }, enabled = state.canUnlock) {
                Text("Otključaj ${(s.level + 1) * 1000}")
            }
            Spacer(Modifier.width(8.dp))
            if (!state.canUnlock) TextButton(onClick = { confirmEarly = true }) { Text("Ipak sada") }
        }
    } else {
        Text(
            "Otključane su sve riječi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (s.level > 1) {
        TextButton(onClick = { vm.setLevel(s.level - 1) }) { Text("Vrati na ${(s.level - 1) * 1000}") }
    }

    if (confirmEarly) {
        AlertDialog(
            onDismissRequest = { confirmEarly = false },
            title = { Text("Otključati prije vremena?") },
            text = {
                Text(
                    "Nove riječi dolaze tek kad potrošiš sve iz trenutne liste, pa se ništa ne gubi. " +
                        "Ali češće riječi donose više u razgovoru — preporuka je prvo svladati ovu razinu."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setLevel(s.level + 1)
                    confirmEarly = false
                }) { Text("Otključaj") }
            },
            dismissButton = { TextButton(onClick = { confirmEarly = false }) { Text("Odustani") } }
        )
    }
}

/** Section label; a longer explanation opens with the (i) button instead of always taking space. */
@Composable
private fun InfoLabel(title: String, info: String?) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (info != null) {
            IconButton(onClick = { open = !open }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Objašnjenje",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    if (open && info != null) {
        Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)
            ) { Text(label(o), maxLines = 1) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
