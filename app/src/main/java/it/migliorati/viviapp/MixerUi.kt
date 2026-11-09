package it.migliorati.viviapp

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MixerTab() {
    val st by Mixer.state.collectAsState()
    val s by Mixer.settings.collectAsState()
    val m by Mixer.meters.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SessionCard(st, m)
        Spacer(Modifier.height(16.dp))
        PlanRunCard()
        BellsCard(st, s)
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

// ---------------------------------------------------------------- Stato della lezione

@Composable
fun SessionCard(st: MixerState, m: Meters) {
    val ctx = LocalContext.current
    var precheck by remember { mutableStateOf(false) }
    val recordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null) Mixer.startRecording(res.resultCode, data)
    }

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(active = st.engineRunning, size = 14.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(st.status(), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(tr("Microphone: ", "Mikrofon: ", "Microfono: ") + (st.inputName?.invoke() ?: "—"))
                    Text(tr("Output: ", "Izlaz: ", "Uscita: ") + (st.outputName?.invoke() ?: tr("cable not connected", "kabel nije spojen", "cavo non collegato")))
                    if (st.outputName == null) Hint(tr(
                        "No hardware? Turn on Test mode in Settings.",
                        "Nemaš opremu? Uključi Probni način u Postavkama.",
                        "Senza hardware? Attiva la Modalità prova in Impostazioni.",
                    ))
                    if (st.engineRunning) Hint(tr("App buffer: ", "Međuspremnik: ", "Buffer app: ") + "${m.bufferMs} ms (+ Bluetooth)")
                    BatteryLine(st)
                    if (st.recording) RecordingLine(st)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (!st.sessionOn) {
                        Button(onClick = { Mixer.startSession() }, contentPadding = PaddingValues(24.dp, 16.dp)) {
                            Text("▶  " + tr("Start lesson", "Započni sat", "Avvia lezione"), fontSize = 18.sp)
                        }
                    } else {
                        OutlinedButton(onClick = { Mixer.stopSession() }, contentPadding = PaddingValues(24.dp, 16.dp)) {
                            Text("■  " + tr("End lesson", "Završi sat", "Termina lezione"), fontSize = 18.sp)
                        }
                        if (!st.engineRunning && st.outputName != null && !st.btLost) {
                            TextButton(onClick = { Mixer.resumeNow() }) { Text(tr("Resume", "Nastavi", "Riprendi")) }
                        }
                    }
                }
            }

            if (st.btLost) {
                Spacer(Modifier.height(10.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr(
                                "The headset dropped out. Music continues for the clients; it reconnects by itself when the headset is back.",
                                "Slušalice su se odspojile. Glazba se nastavlja za klijente; spojit će se same kad se slušalice vrate.",
                                "L'headset si è scollegato. La musica continua per le clienti; si ricollega da solo quando l'headset torna.",
                            ),
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { Mixer.useTabletMic() }) { Text(tr("Use tablet mic", "Koristi mikrofon tableta", "Usa microfono del tablet")) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { precheck = true }) { Text("✓  " + tr("Pre-lesson check", "Provjera prije sata", "Controllo pre-lezione")) }
                OutlinedButton(onClick = {
                    if (!st.sessionOn) Mixer.startSession()
                    UiState.lessonScreen = true
                }) { Text("⛶  " + tr("Lesson screen", "Zaslon sata", "Schermata lezione")) }
                if (!st.recording) {
                    OutlinedButton(onClick = {
                        val mpm = ctx.getSystemService(MediaProjectionManager::class.java)
                        recordLauncher.launch(mpm.createScreenCaptureIntent())
                    }) { Text("●  " + tr("Record lesson", "Snimi sat", "Registra lezione")) }
                } else {
                    Button(onClick = { Mixer.stopRecording() }) { Text("■  " + tr("Stop recording", "Zaustavi snimanje", "Ferma registrazione")) }
                }
            }
        }
    }
    if (precheck) PreCheckDialog { precheck = false }
}

@Composable
fun BatteryLine(st: MixerState) {
    if (st.battery < 0) return
    val low = st.battery < 25 && !st.charging
    Text(
        tr("Battery ", "Baterija ", "Batteria ") + "${st.battery}%" +
            (if (st.charging) tr(" · charging", " · puni se", " · in carica") else "") +
            (if (low) tr(" · low!", " · slaba!", " · bassa!") else ""),
        style = MaterialTheme.typography.bodySmall,
        color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (low) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun RecordingLine(st: MixerState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Text(
        "● " + tr("Recording ", "Snimanje ", "Registrazione ") + clock((now - st.recordingStartedAt) / 1000),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
    )
}

// ---------------------------------------------------------------- Controllo pre-lezione

@Composable
fun PreCheckDialog(onClose: () -> Unit) {
    val st by Mixer.state.collectAsState()
    val s by Mixer.settings.collectAsState()
    val m by Mixer.meters.collectAsState()
    val lib by Mixer.library.collectAsState()
    var heardVoice by remember { mutableStateOf(false) }
    LaunchedEffect(m.voiceDb) { if (m.voiceDb > -50f) heardVoice = true }
    val queueSize = remember(st.activePlaylist, lib) { Mixer.queue().size }
    val btMic = st.inputs.any { it.bluetooth }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Pre-lesson check", "Provjera prije sata", "Controllo pre-lezione")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CheckRow(st.sessionOn, tr("Lesson started", "Sat je započeo", "Lezione avviata"),
                    tr("Press Start lesson", "Pritisni Započni sat", "Premi Avvia lezione"))
                CheckRow(st.outputName != null, tr("Cable to the transmitter connected", "Kabel prema odašiljaču spojen", "Cavo verso il trasmettitore collegato"),
                    tr("Connect the USB-C adapter", "Spoji USB-C adapter", "Collega l'adattatore USB-C"))
                CheckRow(btMic || s.testMode, tr("Bluetooth headset connected", "Bluetooth slušalice spojene", "Headset Bluetooth collegato"),
                    tr("Pair the headset in the tablet's Bluetooth settings", "Upari slušalice u Bluetooth postavkama tableta", "Accoppia l'headset nelle impostazioni Bluetooth del tablet"))
                CheckRow(heardVoice, tr("Voice detected", "Glas je otkriven", "Voce rilevata"),
                    tr("Say a few words into the microphone", "Reci nekoliko riječi u mikrofon", "Di' qualche parola nel microfono"))
                LinearProgressIndicator(progress = { m.level }, modifier = Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 6.dp))
                CheckRow(st.battery >= 30 || st.charging, tr("Battery OK", "Baterija u redu", "Batteria a posto") + if (st.battery >= 0) " (${st.battery}%)" else "",
                    tr("Charge the tablet or use an adapter with charging", "Napuni tablet ili koristi adapter s punjenjem", "Carica il tablet o usa un adattatore con ricarica"))
                CheckRow(s.musicVolume > 0.05f && queueSize > 0, tr("Music ready", "Glazba spremna", "Musica pronta") + " ($queueSize)",
                    tr("Add tracks or load a playlist", "Dodaj pjesme ili učitaj popis", "Aggiungi brani o carica una playlist"))
                CheckRow(!s.voiceMuted, tr("Microphone not muted", "Mikrofon nije utišan", "Microfono non muto"),
                    tr("Turn off Mute in the Voice section", "Isključi Utišano u odjeljku Glas", "Togli il Muto nella sezione Voce"))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { Mixer.testBeep() }, enabled = st.engineRunning) {
                    Text("♪  " + tr("Test beep in the headphones", "Probni zvuk u slušalicama", "Bip di prova nelle cuffie"))
                }
                Hint(tr(
                    "Put on one of the clients' headphones: you should hear the beep.",
                    "Stavi jedne slušalice klijenata: trebao bi čuti zvuk.",
                    "Indossa una cuffia delle clienti: devi sentire il bip.",
                ))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Done", "Gotovo", "Fatto")) } },
    )
}

@Composable
private fun CheckRow(ok: Boolean, label: String, fix: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            if (ok) "✓" else "✗", Modifier.width(32.dp),
            color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold, fontSize = 18.sp,
        )
        Column {
            Text(label)
            if (!ok) Hint(fix)
        }
    }
}

// ---------------------------------------------------------------- Scaletta in corso

@Composable
fun PlanRunCard() {
    val run by Mixer.planRun.collectAsState()
    val plans by Mixer.plans.collectAsState()
    val r = run
    if (r == null && plans.isEmpty()) return
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(tr("Lesson plan", "Plan sata", "Scaletta"), style = MaterialTheme.typography.titleLarge)
            if (r == null) {
                Hint(tr("Start a lesson plan: timed phases with bell and music changes.", "Pokreni plan sata: faze s vremenom, zvonom i promjenom glazbe.", "Avvia una scaletta: fasi a tempo con campana e cambio musica."))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEach { p ->
                        OutlinedButton(onClick = { Mixer.startPlan(p.name) }) { Text("▶  ${p.name} · ${p.totalMinutes}′") }
                    }
                }
            } else {
                PlanProgress(r, big = false)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Mixer.nextPhase() }) { Text("⏭  " + tr("Next phase", "Sljedeća faza", "Fase successiva")) }
                    TextButton(onClick = { Mixer.stopPlan() }) { Text(tr("Stop plan", "Zaustavi plan", "Ferma scaletta")) }
                }
            }
        }
    }
}

/** Fase attuale, tempo rimasto e fase successiva (ricalcolato ogni secondo). */
@Composable
fun PlanProgress(r: PlanRun, big: Boolean) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(r) { while (true) { now = SystemClock.elapsedRealtime(); delay(500) } }
    val left = (r.phaseEndsAt - now) / 1000
    val total = (r.phaseEndsAt - r.phaseStartedAt).coerceAtLeast(1)
    val done = ((now - r.phaseStartedAt).toFloat() / total).coerceIn(0f, 1f)
    Text(
        "${r.index + 1}/${r.plan.phases.size} · ${r.phase.name}",
        style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
    )
    Text(
        clock(left),
        fontSize = if (big) 64.sp else 32.sp,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.primary,
    )
    LinearProgressIndicator(progress = { done }, modifier = Modifier.fillMaxWidth().height(if (big) 10.dp else 6.dp))
    val nx = r.nextPhase
    Hint(
        if (nx != null) tr("Next: ", "Sljedeće: ", "Poi: ") + "${nx.name} (${nx.minutes}′)"
        else tr("Last phase", "Zadnja faza", "Ultima fase"),
        Modifier.padding(top = 4.dp),
    )
}

// ---------------------------------------------------------------- Campane

@Composable
fun BellsCard(st: MixerState, s: Settings) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(tr("Bells", "Zvona", "Campane"), style = MaterialTheme.typography.titleLarge)
            Hint(tr(
                "Sounds sent into the clients' headphones, to mark a change of pose or the end of savasana.",
                "Zvukovi u slušalice klijenata, za promjenu položaja ili kraj savasane.",
                "Suoni mandati nelle cuffie delle clienti, per il cambio posizione o la fine di savasana.",
            ))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bell.entries.forEach { b ->
                    FilledTonalButton(onClick = { Mixer.ringBell(b) }, enabled = st.engineRunning) { Text("🔔  " + b.label) }
                }
            }
            LabeledSlider(tr("Bell volume", "Glasnoća zvona", "Volume campane"), s.bellVolume, 0f..1f, "${(s.bellVolume * 100).roundToInt()}%") { v ->
                Mixer.update { it.copy(bellVolume = v) }
            }
        }
    }
}

// ---------------------------------------------------------------- Voce

@Composable
fun VoiceCard(st: MixerState, s: Settings, m: Meters) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(tr("Voice", "Glas", "Voce"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                st.inputs.forEach { d ->
                    FilterChip(
                        selected = d.id == st.selectedInputId,
                        onClick = { Mixer.selectInput(d.id) },
                        label = { Text(d.label(), maxLines = 1) },
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
            LabeledSlider(tr("Voice volume", "Glasnoća glasa", "Volume voce"), s.voiceGainDb, -12f..12f,
                "${if (s.voiceGainDb > 0) "+" else ""}${s.voiceGainDb.roundToInt()} dB") { v ->
                Mixer.update { it.copy(voiceGainDb = v) }
            }
            SwitchRow(tr("Automatic clean voice", "Automatski čist glas", "Voce pulita automatica"), s.voiceClean) { v -> Mixer.update { it.copy(voiceClean = v) } }
            Hint(
                if (s.voiceClean) tr(
                    "Noise reduction, rumble filter, presence, compressor and automatic level: just set the volume.",
                    "Smanjenje šuma, filtar brujanja, prisutnost, kompresor i automatska razina: podesi samo glasnoću.",
                    "Riduzione rumore, antirombo, presenza, compressore e livello automatico: regola solo il volume.",
                ) else tr("Direct voice, no processing.", "Izravan glas, bez obrade.", "Voce diretta, senza elaborazione.")
            )
            SwitchRow(tr("Microphone muted", "Mikrofon utišan", "Microfono muto"), s.voiceMuted) { v -> Mixer.update { it.copy(voiceMuted = v) } }
        }
    }
}

// ---------------------------------------------------------------- Musica

@Composable
fun MusicCard(st: MixerState, s: Settings, m: Meters) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(tr("Music", "Glazba", "Musica"), style = MaterialTheme.typography.titleLarge)
            Text(
                st.nowPlaying ?: tr("No track: pick one in the Library", "Nema pjesme: odaberi jednu u Knjižnici", "Nessun brano: scegline uno in Libreria"),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Mixer.togglePlay() }) {
                    Text(if (st.musicPlaying) "⏸  " + tr("Pause", "Pauza", "Pausa") else "▶  Play")
                }
                OutlinedButton(onClick = { Mixer.stopMusic() }) { Text("■  Stop") }
                OutlinedButton(onClick = { Mixer.next() }) { Text("⏭  " + tr("Next", "Sljedeća", "Avanti")) }
            }
            QueueSection(st)
            Spacer(Modifier.height(8.dp))
            LabeledSlider(tr("Music volume", "Glasnoća glazbe", "Volume musica"), s.musicVolume, 0f..1f, "${(s.musicVolume * 100).roundToInt()}%") { v ->
                Mixer.update { it.copy(musicVolume = v) }
            }
            SwitchRow(tr("Repeat track (loop)", "Ponavljaj pjesmu (petlja)", "Ripeti brano (loop)"), s.loopTrack) { v -> Mixer.update { it.copy(loopTrack = v) } }
            SwitchRow(tr("Go to the next track", "Prijeđi na sljedeću pjesmu", "Passa al brano successivo"), s.autoNext) { v -> Mixer.update { it.copy(autoNext = v) } }
            SwitchRow(tr("Crossfade between tracks", "Pretapanje između pjesama", "Dissolvenza tra i brani"), s.crossfadeOn) { v -> Mixer.update { it.copy(crossfadeOn = v) } }
            if (s.crossfadeOn) {
                LabeledSlider(tr("Crossfade", "Pretapanje", "Dissolvenza"), s.crossfadeSec, 2f..15f, "%.0f s".format(s.crossfadeSec)) { v ->
                    Mixer.update { it.copy(crossfadeSec = v) }
                }
                if (!s.autoNext || s.loopTrack) Hint(tr(
                    "Works when \"Go to the next track\" is on and loop is off.",
                    "Radi kad je uključeno \"Prijeđi na sljedeću pjesmu\" i isključena petlja.",
                    "Funziona con \"Passa al brano successivo\" attivo e loop spento.",
                ))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(tr("Music also in the teacher's earpiece", "Glazba i u slušalici instruktorice", "Musica anche nell'auricolare dell'insegnante"), s.monitorOn) { v ->
                Mixer.update { it.copy(monitorOn = v) }
            }
            if (s.monitorOn) {
                LabeledSlider(tr("Teacher earpiece volume", "Glasnoća slušalice instruktorice", "Volume in cuffia insegnante"), s.monitorVolume, 0f..1f, "${(s.monitorVolume * 100).roundToInt()}%") { v ->
                    Mixer.update { it.copy(monitorVolume = v) }
                }
                Hint(
                    if (st.inputs.any { it.bluetooth && it.id == st.selectedInputId }) tr(
                        "Only for her: it doesn't change what clients hear. Phone-call quality.",
                        "Samo za nju: ne mijenja ono što čuju klijenti. Kvaliteta telefonskog poziva.",
                        "Solo per lei: non cambia quello che sentono le clienti. Qualità telefonica, come le chiamate.",
                    ) else tr(
                        "Turns on when the Bluetooth headset is the microphone in use.",
                        "Uključuje se kad su Bluetooth slušalice mikrofon u upotrebi.",
                        "Si attiva quando il microfono in uso è l'headset Bluetooth.",
                    )
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(tr("Automatic ducking", "Automatsko stišavanje", "Ducking automatico"), s.duckOn) { v -> Mixer.update { it.copy(duckOn = v) } }
            if (s.duckOn) {
                Hint(
                    if (m.duckDb < -1f) tr("Music lowered by ", "Glazba stišana za ", "Musica abbassata di ") + "${(-m.duckDb).roundToInt()} dB"
                    else tr("Music at full volume", "Glazba punom glasnoćom", "Musica a volume pieno")
                )
                LabeledSlider(tr("How much to lower", "Koliko stišati", "Quanto abbassare"), s.duckDepthDb, -30f..-3f, "${s.duckDepthDb.roundToInt()} dB") { v ->
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

@Composable
fun QueueSection(st: MixerState) {
    val pls by Mixer.playlists.collectAsState()
    val lib by Mixer.library.collectAsState()
    val queue = remember(st.activePlaylist, pls, lib) { Mixer.queue() }
    Spacer(Modifier.height(12.dp))
    Text(tr("Lesson playlist", "Popis za sat", "Playlist della lezione"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = st.activePlaylist == null, onClick = { Mixer.loadPlaylist(null) },
            label = { Text(tr("Whole library", "Cijela knjižnica", "Tutta la libreria")) })
        pls.forEach { p ->
            FilterChip(selected = st.activePlaylist == p.name, onClick = { Mixer.loadPlaylist(p.name) }, label = { Text(p.name) })
        }
    }
    if (queue.isEmpty()) {
        Hint(
            if (st.activePlaylist != null) tr("This playlist is empty: add tracks in Library → Playlists.", "Ovaj popis je prazan: dodaj pjesme u Knjižnica → Popisi.", "Questa playlist è vuota: aggiungi brani da Libreria → Playlist.")
            else tr("The library is empty.", "Knjižnica je prazna.", "La libreria è vuota.")
        )
    } else {
        SoftCard(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                queue.forEachIndexed { i, f ->
                    val current = st.nowPlaying == f.nameWithoutExtension
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            f.nameWithoutExtension, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = { Mixer.play(f) }) { Text(if (current && st.musicPlaying) "♪" else "▶") }
                    }
                }
            }
        }
    }
}
