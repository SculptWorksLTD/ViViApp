package it.migliorati.viviapp

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askPerms.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        )
        setContent {
            BohoTheme {
                val st by Mixer.state.collectAsState()
                // Schermo sempre acceso durante la lezione
                LaunchedEffect(st.sessionOn) {
                    if (st.sessionOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                App()
            }
        }
    }
}

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Mixer", "Libreria", "Genera", "Impostazioni")
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
    ) {
        // Intestazione
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SunMotif(size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("ViViApp", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "voce e musica per lo yoga",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Schede a pillola
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { i, t ->
                val selected = tab == i
                Surface(
                    onClick = { tab = i },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(t, Modifier.padding(horizontal = 18.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> MixerTab()
                1 -> LibraryTab()
                2 -> GenerateTab()
                else -> SettingsTab()
            }
        }
    }
}

// ---------------------------------------------------------------- Mixer

@Composable
fun MixerTab() {
    val st by Mixer.state.collectAsState()
    val s by Mixer.settings.collectAsState()
    val m by Mixer.meters.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SessionCard(st, m)
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints {
            if (maxWidth > 700.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { VoiceCard(st, s, m) }
                    Box(Modifier.weight(1f)) { MusicCard(st, s, m) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    VoiceCard(st, s, m)
                    MusicCard(st, s, m)
                }
            }
        }
    }
}

@Composable
fun SessionCard(st: MixerState, m: Meters) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(active = st.engineRunning, size = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(st.status, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(4.dp))
                Text("Microfono: ${st.inputName ?: "—"}")
                Text("Uscita: ${st.outputName ?: "cavo non collegato"}")
                if (st.outputName == null) Text(
                    "Senza hardware? Attiva la Modalità prova in Impostazioni.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (st.engineRunning) Text("Buffer app: ${m.bufferMs} ms (+ ritardo Bluetooth)", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!st.sessionOn) {
                    Button(onClick = { Mixer.startSession() }, contentPadding = PaddingValues(24.dp, 16.dp)) {
                        Text("▶  Avvia lezione", fontSize = 18.sp)
                    }
                } else {
                    OutlinedButton(onClick = { Mixer.stopSession() }, contentPadding = PaddingValues(24.dp, 16.dp)) {
                        Text("■  Termina lezione", fontSize = 18.sp)
                    }
                    if (!st.engineRunning && st.outputName != null) {
                        TextButton(onClick = { Mixer.resumeNow() }) { Text("Riprendi") }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceCard(st: MixerState, s: Settings, m: Meters) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Voce", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                st.inputs.forEach { d ->
                    FilterChip(
                        selected = d.id == st.selectedInputId,
                        onClick = { Mixer.selectInput(d.id) },
                        label = { Text(d.label, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { m.level },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = if (m.level > 0.95f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LabeledSlider("Volume voce", s.voiceGainDb, -12f..12f, "${if (s.voiceGainDb > 0) "+" else ""}${s.voiceGainDb.roundToInt()} dB") { v ->
                Mixer.update { it.copy(voiceGainDb = v) }
            }
            SwitchRow("Voce pulita automatica", s.voiceClean) { v -> Mixer.update { it.copy(voiceClean = v) } }
            Text(
                if (s.voiceClean) "Riduzione rumore, antirombo, presenza, compressore e livello automatico: regola solo il volume."
                else "Voce diretta, senza elaborazione.",
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchRow("Microfono muto", s.voiceMuted) { v -> Mixer.update { it.copy(voiceMuted = v) } }
        }
    }
}

@Composable
fun MusicCard(st: MixerState, s: Settings, m: Meters) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Musica", style = MaterialTheme.typography.titleLarge)
            Text(
                st.nowPlaying ?: "Nessun brano: scegline uno in Libreria",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Mixer.togglePlay() }) { Text(if (st.musicPlaying) "⏸  Pausa" else "▶  Play") }
                OutlinedButton(onClick = { Mixer.stopMusic() }) { Text("■  Stop") }
                OutlinedButton(onClick = { Mixer.next() }) { Text("⏭  Avanti") }
            }
            Spacer(Modifier.height(8.dp))
            LabeledSlider("Volume musica", s.musicVolume, 0f..1f, "${(s.musicVolume * 100).roundToInt()}%") { v ->
                Mixer.update { it.copy(musicVolume = v) }
            }
            SwitchRow("Ripeti brano (loop)", s.loopTrack) { v -> Mixer.update { it.copy(loopTrack = v) } }
            SwitchRow("Passa al brano successivo", s.autoNext) { v -> Mixer.update { it.copy(autoNext = v) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow("Musica anche nell'auricolare dell'insegnante", s.monitorOn) { v -> Mixer.update { it.copy(monitorOn = v) } }
            if (s.monitorOn) {
                LabeledSlider("Volume in cuffia insegnante", s.monitorVolume, 0f..1f, "${(s.monitorVolume * 100).roundToInt()}%") { v ->
                    Mixer.update { it.copy(monitorVolume = v) }
                }
                Text(
                    if (st.inputName?.startsWith("Bluetooth") == true)
                        "Solo per lei: non cambia quello che sentono le clienti. Qualità telefonica, come le chiamate."
                    else "Si attiva quando il microfono in uso è l'headset Bluetooth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow("Ducking automatico", s.duckOn) { v -> Mixer.update { it.copy(duckOn = v) } }
            if (s.duckOn) {
                Text(
                    if (m.duckDb < -1f) "Musica abbassata di ${(-m.duckDb).roundToInt()} dB" else "Musica a volume pieno",
                    style = MaterialTheme.typography.bodySmall,
                )
                LabeledSlider("Quanto abbassare", s.duckDepthDb, -30f..-3f, "${s.duckDepthDb.roundToInt()} dB") { v ->
                    Mixer.update { it.copy(duckDepthDb = v) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow("Fade in / fade out", s.fadeOn) { v -> Mixer.update { it.copy(fadeOn = v) } }
            if (s.fadeOn) {
                LabeledSlider("Fade in", s.fadeInSec, 0.5f..10f, "%.1f s".format(s.fadeInSec)) { v ->
                    Mixer.update { it.copy(fadeInSec = v) }
                }
                LabeledSlider("Fade out", s.fadeOutSec, 0.5f..15f, "%.1f s".format(s.fadeOutSec)) { v ->
                    Mixer.update { it.copy(fadeOutSec = v) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Libreria

@Composable
fun LibraryTab() {
    val lib by Mixer.library.collectAsState()
    val st by Mixer.state.collectAsState()
    var toDelete by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) Mixer.import(uris)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${lib.size} brani salvati sul tablet", Modifier.weight(1f))
            Button(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("+  Importa brani") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lib, key = { it.absolutePath }) { f ->
                val playing = st.nowPlaying == f.nameWithoutExtension
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text("%.1f MB".format(f.length() / 1_048_576.0), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { Mixer.play(f) }) { Text("▶  Suona") }
                        TextButton(onClick = { toDelete = f }) { Text("Elimina") }
                    }
                }
            }
        }
    }

    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare il brano?") },
            text = { Text(f.nameWithoutExtension) },
            confirmButton = { TextButton(onClick = { Mixer.delete(f); toDelete = null }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }
}

// ---------------------------------------------------------------- Genera

private val presets = listOf(
    "Calma" to "ambient, meditation, calm, soft synth pad, warm drones, peaceful, slow, 60 BPM, instrumental",
    "Campane tibetane" to "tibetan singing bowls, meditation, ambient drone, resonant, peaceful, slow, instrumental",
    "Natura" to "ambient, nature sounds, flowing water, soft pad, gentle piano, relaxing, instrumental",
    "Flow / Vinyasa" to "downtempo, chillout, soft hand percussion, warm bass, flowing, uplifting, 90 BPM, instrumental",
    "Indiana" to "indian classical, bansuri flute, tanpura drone, slow raga, meditative, instrumental",
    "Savasana" to "deep ambient, slow drone, soft piano, sleepy, very relaxing, 50 BPM, instrumental",
)

@Composable
fun GenerateTab() {
    val st by Mixer.state.collectAsState()
    val s by Mixer.settings.collectAsState()
    var prompt by rememberSaveable { mutableStateOf(presets[0].second) }
    var seconds by rememberSaveable { mutableFloatStateOf(180f) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Account Hugging Face", style = MaterialTheme.typography.titleMedium)
                Text(
                    "La musica viene generata online, gratis, con il modello ACE-Step. " +
                        "Serve un token del tuo account huggingface.co (Settings → Access Tokens → tipo Read).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = s.serverToken, onValueChange = { v -> Mixer.update { it.copy(serverToken = v.trim()) } },
                    label = { Text("Token (hf_…)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { Mixer.testServer() }) { Text("Prova connessione") }
            }
        }
        Spacer(Modifier.height(16.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Nuovo brano strumentale", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Descrivi lo stile con parole chiave in inglese separate da virgole. Scegli uno stile e modificalo se vuoi.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (name, p) ->
                        FilterChip(selected = prompt == p, onClick = { prompt = p }, label = { Text(name) })
                    }
                }
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Stile della musica") }, minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                val mins = seconds.roundToInt() / 60
                val secs = seconds.roundToInt() % 60
                LabeledSlider("Durata", seconds, 30f..240f, "%d:%02d".format(mins, secs)) { seconds = (it / 10).roundToInt() * 10f }
                Text(
                    "La quota gratuita è di qualche brano al giorno. I brani scaricati restano sul tablet per sempre.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { Mixer.generate(prompt.trim(), seconds.roundToInt()) },
                    enabled = !st.generating && prompt.isNotBlank(),
                ) { Text(if (st.generating) "Generazione…" else "✦  Genera brano") }
                if (st.generating) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        if (st.genStatus.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(st.genStatus)
        }
        Spacer(Modifier.height(16.dp))
        Text("Avanzate: indirizzo del generatore", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = s.serverUrl, onValueChange = { v -> Mixer.update { it.copy(serverUrl = v.trim()) } },
            placeholder = { Text(Mixer.DEFAULT_SPACE) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Lascia vuoto per usare quello predefinito.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- Impostazioni

@Composable
fun SettingsTab() {
    val s by Mixer.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SwitchRow("Modalità prova (senza cavo e senza headset)", s.testMode) { v -> Mixer.update { it.copy(testMode = v) } }
        Text(
            "Per provare l'app sull'emulatore o senza hardware: la voce entra dal microfono del dispositivo " +
                "ed esce dall'altoparlante insieme alla musica. Usa le cuffie sul computer per evitare il fischio (larsen). " +
                "Spegnila per le lezioni vere.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SwitchRow("Riparti da solo quando ricolleghi il cavo", s.autoResume) { v -> Mixer.update { it.copy(autoResume = v) } }
        SwitchRow("Modalità chiamata per il microfono Bluetooth", s.commMode) { v -> Mixer.update { it.copy(commMode = v) } }
        Text(
            "Lasciala attiva. Spegnila solo se con il microfono Bluetooth la musica non esce dal cavo.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Ducking avanzato", style = MaterialTheme.typography.titleMedium)
        LabeledSlider("Sensibilità (soglia voce)", s.duckThresholdDb, -60f..-15f, "${s.duckThresholdDb.roundToInt()} dB") { v ->
            Mixer.update { it.copy(duckThresholdDb = v) }
        }
        Text("Più a sinistra = la musica si abbassa anche con la voce bassa.", style = MaterialTheme.typography.bodySmall)
        LabeledSlider("Ritorno della musica", s.duckReleaseMs, 300f..4000f, "%.1f s".format(s.duckReleaseMs / 1000f)) { v ->
            Mixer.update { it.copy(duckReleaseMs = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(
            "Consiglio per tablet Honor: Impostazioni → Batteria → Avvio app → ViViApp → gestione manuale, " +
                "e attiva tutte le voci. Così il sistema non chiude l'app durante la lezione.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text("I brani sono salvati in: ${Mixer.musicDir.absolutePath}", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- Componenti

@Composable
fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row {
            Text(label, Modifier.weight(1f))
            Text(valueText, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
