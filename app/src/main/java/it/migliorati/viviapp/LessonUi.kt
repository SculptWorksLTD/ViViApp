package it.migliorati.viviapp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Schermata lezione: pochi pulsanti grandi, tutto il resto nascosto.
 * Bloccata di default: per usarla si tiene premuto il lucchetto.
 */
@Composable
fun LessonScreen() {
    val st by Mixer.state.collectAsState()
    val s by Mixer.settings.collectAsState()
    val m by Mixer.meters.collectAsState()
    val run by Mixer.planRun.collectAsState()
    var locked by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Intestazione: stato + lucchetto
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = st.engineRunning, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.status(), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        st.nowPlaying ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BatteryLine(st)
                    if (st.recording) RecordingLine(st)
                }
                LockButton(locked) { locked = !locked }
                if (!locked) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { UiState.lessonScreen = false }) { Text(tr("Exit", "Izlaz", "Esci")) }
                }
            }
            if (st.btLost) {
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(
                        tr("Headset disconnected — music continues", "Slušalice odspojene — glazba se nastavlja", "Headset scollegato — la musica continua"),
                        Modifier.fillMaxWidth().padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { m.level }, modifier = Modifier.fillMaxWidth().height(10.dp),
                color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Scaletta in corso
            run?.let { r ->
                Spacer(Modifier.height(20.dp))
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlanProgress(r, big = true)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            // Pulsanti grandi
            Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton(
                    if (st.musicPlaying) "⏸\n" + tr("Pause music", "Pauziraj glazbu", "Pausa musica") else "▶\n" + tr("Play music", "Pokreni glazbu", "Avvia musica"),
                    Modifier.weight(1f), enabled = !locked, primary = true,
                ) { Mixer.togglePlay() }
                BigButton(
                    if (s.voiceMuted) "🎙\n" + tr("Mic OFF", "Mikrofon ISKLJ.", "Microfono SPENTO") else "🎙\n" + tr("Mic on", "Mikrofon uklj.", "Microfono acceso"),
                    Modifier.weight(1f), enabled = !locked, primary = s.voiceMuted,
                ) { Mixer.update { it.copy(voiceMuted = !it.voiceMuted) } }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("⏭\n" + tr("Next track", "Sljedeća pjesma", "Brano successivo"), Modifier.weight(1f), enabled = !locked) { Mixer.next() }
                BigButton("🔔\n" + Bell.BOWL.label, Modifier.weight(1f), enabled = !locked && st.engineRunning) { Mixer.ringBell(Bell.BOWL) }
                if (run != null) BigButton("⏩\n" + tr("Next phase", "Sljedeća faza", "Fase successiva"), Modifier.weight(1f), enabled = !locked) { Mixer.nextPhase() }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (locked) tr("Locked: hold the padlock to unlock", "Zaključano: drži lokot za otključavanje", "Bloccato: tieni premuto il lucchetto per sbloccare")
                else tr("Unlocked: tap the padlock to lock again", "Otključano: dodirni lokot za zaključavanje", "Sbloccato: tocca il lucchetto per bloccare di nuovo"),
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Lucchetto: da bloccato si sblocca solo tenendolo premuto (niente tocchi accidentali). */
@Composable
private fun LockButton(locked: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (locked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(72.dp).pointerInput(locked) {
            detectTapGestures(
                onLongPress = { if (locked) onToggle() },
                onTap = { if (!locked) onToggle() },
            )
        },
    ) {
        Box(contentAlignment = Alignment.Center) { Text(if (locked) "🔒" else "🔓", fontSize = 30.sp) }
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    val colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large, colors = colors,
    ) {
        Text(text, fontSize = 22.sp, textAlign = TextAlign.Center, lineHeight = 30.sp)
    }
}
