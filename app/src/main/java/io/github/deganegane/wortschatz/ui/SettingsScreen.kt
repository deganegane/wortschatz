package io.github.deganegane.wortschatz.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, state: UiState, onBack: () -> Unit) {
    val s = state.settings
    val context = LocalContext.current
    var showTime by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(reminderOn = granted) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Postavke") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Natrag") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Group("Razina") {
                LevelSection(vm, state)
            }

            Group("Učenje") {
                Label("Novih riječi dnevno", "Koliko novih riječi uvodiš svaki dan. 10 je dobra mjera uz par minuta ponavljanja.")
                Choice(listOf(5, 10, 15, 20), s.newPerDay, { it.toString() }) { n -> vm.updateSettings { it.copy(newPerDay = n) } }
                Spacer(Modifier.height(16.dp))
                Label(
                    "Način vježbe",
                    when (s.practice) {
                        Practice.CLASSIC -> "Samo prepoznavanje: vidiš njemačku riječ i sjetiš se značenja."
                        Practice.SPEAKING -> "Samo govor: izgovaraš njemačku riječ iz hrvatskog ili dopunjavaš rečenicu naglas."
                        Practice.MIXED -> "Nove riječi prvo prepoznaješ, a kad ih par puta vidiš, sve češće ih izgovaraš (preporučeno)."
                    }
                )
                Choice(Practice.entries, s.practice, { it.label }) { p -> vm.updateSettings { it.copy(practice = p) } }
                Spacer(Modifier.height(16.dp))
                Label(
                    "Ciljano pamćenje",
                    "Koliko je vjerojatno da se sjetiš riječi kad dođe na red (FSRS). Više = češća ponavljanja."
                )
                Choice(listOf(0.85, 0.9, 0.95), s.retention, { "${(it * 100).toInt()} %" }) { r ->
                    vm.updateSettings { it.copy(retention = r) }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    "Fraze za razgovor",
                    "Svaka treća nova kartica je gotova rečenica (\"Das kommt darauf an.\")",
                    s.phrasesOn
                ) { v -> vm.updateSettings { it.copy(phrasesOn = v) } }
                ToggleRow(
                    "Odgovaraj glasom",
                    if (vm.recognitionAvailable) "Mikrofon provjerava izgovorenu riječ i član" else "Prepoznavanje govora nije dostupno na ovom mobitelu",
                    s.speakAnswers
                ) { v -> vm.updateSettings { it.copy(speakAnswers = v) } }
            }

            Group("Izgovor") {
                ToggleRow("Automatski izgovor", "Pročitaj riječ čim se kartica pokaže", s.autoSpeak) { v ->
                    vm.updateSettings { it.copy(autoSpeak = v) }
                }
                ToggleRow("Sporiji izgovor", "Za lakše razaznavanje glasova", s.slowSpeech) { v ->
                    vm.updateSettings { it.copy(slowSpeech = v) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (state.voice) {
                        VoiceState.READY -> "Njemački glas je spreman."
                        VoiceState.LOADING -> "Učitavam glas…"
                        VoiceState.NO_GERMAN -> "Na mobitelu nema njemačkog glasa. Instaliraj ga u postavkama govora."
                        VoiceState.NO_ENGINE -> "Nije pronađen pretvarač teksta u govor."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        // Not available on this phone.
                    }
                }) { Text("Postavke govora") }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { vm.speak("Guten Tag! Wie geht es dir?") }) { Text("Probaj: \"Guten Tag! Wie geht es dir?\"") }
            }

            Group("Podsjetnik") {
                ToggleRow(
                    "Dnevni podsjetnik",
                    "Samo ako taj dan još nisi vježbao",
                    s.reminderOn
                ) { on ->
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
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Group("Izgled") {
                Label("Tema", null)
                Choice(ThemeMode.entries, s.themeMode, { it.label }) { m -> vm.updateSettings { it.copy(themeMode = m) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Boje iz pozadine", "Material You umjesto zlatne palete", s.dynamicColor) { v ->
                        vm.updateSettings { it.copy(dynamicColor = v) }
                    }
                }
            }

            Group("Napredak") {
                Text(
                    "Briše rezultate testa, učenje i niz dana. Postavke ostaju.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { confirmReset = true }) {
                    Text("Resetiraj napredak", color = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                "Wortschatz ${BuildConfigInfo.versionName(context)} · ${state.allCount} najčešćih riječi s primjerima za razgovor. " +
                    "Font Space Grotesk (SIL Open Font License).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
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
            text = { Text("Ovo briše sve oznake \"znam / ne znam\", raspored ponavljanja i niz dana. Ne može se poništiti.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetProgress()
                    confirmReset = false
                }) { Text("Resetiraj", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Odustani") } }
        )
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

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    SectionTitle(title)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Label(title: String, sub: String?) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (sub != null) {
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
