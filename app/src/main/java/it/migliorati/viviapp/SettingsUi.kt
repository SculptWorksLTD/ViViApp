package it.migliorati.viviapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsTab() {
    val s by Mixer.settings.collectAsState()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) Mixer.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Lingua
        Text(tr("Language", "Jezik", "Lingua"), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Lang.entries.forEach { l ->
                FilterChip(
                    selected = L.lang == l,
                    onClick = { Mixer.update { it.copy(language = l.code) } },
                    label = { Text(l.label) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        UpdateSection(s)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SwitchRow(tr("Test mode (no cable, no headset)", "Probni način (bez kabela i slušalica)", "Modalità prova (senza cavo e senza headset)"), s.testMode) { v ->
            Mixer.update { it.copy(testMode = v) }
        }
        Hint(tr(
            "To try the app on the emulator or without hardware: the voice comes from the device microphone and goes out of the speaker with the music. Use headphones on the computer to avoid feedback. Turn it off for real lessons.",
            "Za isprobavanje aplikacije na emulatoru ili bez opreme: glas ulazi kroz mikrofon uređaja i izlazi na zvučnik zajedno s glazbom. Koristi slušalice na računalu da izbjegneš mikrofoniju. Isključi za prave satove.",
            "Per provare l'app sull'emulatore o senza hardware: la voce entra dal microfono del dispositivo ed esce dall'altoparlante insieme alla musica. Usa le cuffie sul computer per evitare il fischio (larsen). Spegnila per le lezioni vere.",
        ))
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SwitchRow(tr("Resume by itself when the cable is plugged back in", "Nastavi sam kad se kabel ponovno spoji", "Riparti da solo quando ricolleghi il cavo"), s.autoResume) { v ->
            Mixer.update { it.copy(autoResume = v) }
        }
        SwitchRow(tr("Headset buttons control the music", "Tipke slušalica upravljaju glazbom", "I tasti dell'auricolare comandano la musica"), s.mediaButtons) { v ->
            Mixer.update { it.copy(mediaButtons = v) }
        }
        Hint(tr(
            "One press = play/pause, two presses = next track. With some headsets the button may not work while the microphone is in use.",
            "Jedan pritisak = sviraj/pauza, dva pritiska = sljedeća pjesma. Kod nekih slušalica tipka možda neće raditi dok je mikrofon u upotrebi.",
            "Un tocco = play/pausa, due tocchi = brano successivo. Con alcuni headset il tasto potrebbe non funzionare mentre il microfono è in uso.",
        ))
        SwitchRow(tr("Call mode for the Bluetooth microphone", "Način poziva za Bluetooth mikrofon", "Modalità chiamata per il microfono Bluetooth"), s.commMode) { v ->
            Mixer.update { it.copy(commMode = v) }
        }
        Hint(tr(
            "Leave it on. Turn it off only if, with the Bluetooth microphone, music doesn't come out of the cable.",
            "Ostavi uključeno. Isključi samo ako s Bluetooth mikrofonom glazba ne izlazi na kabel.",
            "Lasciala attiva. Spegnila solo se con il microfono Bluetooth la musica non esce dal cavo.",
        ))
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text(tr("Advanced ducking", "Napredno stišavanje", "Ducking avanzato"), style = MaterialTheme.typography.titleMedium)
        LabeledSlider(tr("Sensitivity (voice threshold)", "Osjetljivost (prag glasa)", "Sensibilità (soglia voce)"), s.duckThresholdDb, -60f..-15f, "${s.duckThresholdDb.roundToInt()} dB") { v ->
            Mixer.update { it.copy(duckThresholdDb = v) }
        }
        Hint(tr("Further left = music lowers even with a quiet voice.", "Više lijevo = glazba se stišava i uz tihi glas.", "Più a sinistra = la musica si abbassa anche con la voce bassa."))
        LabeledSlider(tr("Music return time", "Vrijeme povratka glazbe", "Ritorno della musica"), s.duckReleaseMs, 300f..4000f, "%.1f s".format(s.duckReleaseMs / 1000f)) { v ->
            Mixer.update { it.copy(duckReleaseMs = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text(tr("Backup", "Sigurnosna kopija", "Copia di sicurezza"), style = MaterialTheme.typography.titleMedium)
        Hint(tr(
            "A single .zip file with tracks, playlists, lesson plans and settings: to change tablet or start again after a reset.",
            "Jedna .zip datoteka s pjesmama, popisima, planovima sata i postavkama: za promjenu tableta ili nakon resetiranja.",
            "Un unico file .zip con brani, playlist, scalette e impostazioni: per cambiare tablet o ripartire dopo un reset.",
        ))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                exportLauncher.launch("ViViApp-backup-$stamp.zip")
            }) { Text(tr("Save backup", "Spremi kopiju", "Salva copia")) }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                Text(tr("Restore", "Vrati", "Ripristina"))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Hint(tr(
            "Tip for Honor tablets: Settings → Battery → App launch → ViViApp → manage manually, and turn everything on. This way the system won't close the app during the lesson. An adapter with USB-C charging passthrough keeps the tablet charged while the cable carries the audio.",
            "Savjet za Honor tablete: Postavke → Baterija → Pokretanje aplikacija → ViViApp → ručno upravljanje, i uključi sve. Tako sustav neće zatvoriti aplikaciju tijekom sata. Adapter s USB-C prolaznim punjenjem drži tablet napunjenim dok kabel prenosi zvuk.",
            "Consiglio per tablet Honor: Impostazioni → Batteria → Avvio app → ViViApp → gestione manuale, e attiva tutte le voci. Così il sistema non chiude l'app durante la lezione. Un adattatore USB-C con ricarica passante tiene carico il tablet mentre il cavo porta l'audio.",
        ))
        Spacer(Modifier.height(8.dp))
        Hint(tr("Tracks are saved in: ", "Pjesme su spremljene u: ", "I brani sono salvati in: ") + Mixer.musicDir.absolutePath)
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(tr("Restore the backup?", "Vratiti sigurnosnu kopiju?", "Ripristinare la copia?")) },
            text = { Text(tr(
                "Tracks with the same name, playlists, lesson plans and settings will be replaced by those in the backup.",
                "Pjesme istog naziva, popisi, planovi sata i postavke bit će zamijenjeni onima iz kopije.",
                "Brani con lo stesso nome, playlist, scalette e impostazioni verranno sostituiti da quelli della copia.",
            )) },
            confirmButton = { TextButton(onClick = { Mixer.importBackup(uri); confirmRestore = null }) { Text(tr("Restore", "Vrati", "Ripristina")) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(tr("Cancel", "Odustani", "Annulla")) } },
        )
    }
}

// ---------------------------------------------------------------- Aggiornamenti

@Composable
fun UpdateDialog() {
    val u by Mixer.updateState.collectAsState()
    val st by Mixer.state.collectAsState()
    val upd = u.available ?: return
    // Mai durante la lezione: comparirà alla prossima apertura o da Impostazioni
    if (u.dismissed || st.sessionOn) return
    AlertDialog(
        onDismissRequest = { if (!u.downloading) Mixer.dismissUpdate() },
        title = { Text(tr("New version ", "Nova verzija ", "Nuova versione ") + upd.version, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(tr(
                    "A ViViApp update is available (you have ${Updater.currentVersion}). Music, settings and tracks stay where they are.",
                    "Dostupno je ažuriranje ViViApp (imaš ${Updater.currentVersion}). Glazba, postavke i pjesme ostaju gdje jesu.",
                    "È disponibile un aggiornamento di ViViApp (ora hai la ${Updater.currentVersion}). Musica, impostazioni e brani restano dove sono.",
                ))
                if (u.downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
                }
                u.message?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Hint(msg())
                }
            }
        },
        confirmButton = {
            Button(onClick = { Mixer.startUpdate() }, enabled = !u.downloading) {
                Text(if (u.downloading) tr("Downloading…", "Preuzimanje…", "Scarico…") else tr("Update", "Ažuriraj", "Aggiorna"))
            }
        },
        dismissButton = {
            if (!u.downloading) TextButton(onClick = { Mixer.dismissUpdate() }) { Text(tr("Later", "Kasnije", "Più tardi")) }
        },
    )
}

@Composable
fun UpdateSection(s: Settings) {
    val u by Mixer.updateState.collectAsState()
    Text(tr("Updates", "Ažuriranja", "Aggiornamenti"), style = MaterialTheme.typography.titleMedium)
    Hint(tr("Installed version: ", "Instalirana verzija: ", "Versione installata: ") + Updater.currentVersion)
    SwitchRow(tr("Check for updates when the app opens", "Provjeri ažuriranja pri otvaranju", "Controlla gli aggiornamenti all'apertura"), s.autoUpdate) { v ->
        Mixer.update { it.copy(autoUpdate = v) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { Mixer.checkUpdate(manual = true) }, enabled = !u.downloading) { Text(tr("Check now", "Provjeri sada", "Controlla ora")) }
        val avail = u.available
        if (avail != null) Button(onClick = { Mixer.startUpdate() }, enabled = !u.downloading) {
            Text(tr("Install ", "Instaliraj ", "Installa ") + avail.version)
        }
    }
    if (u.downloading) LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    u.message?.let { msg -> Hint(msg(), Modifier.padding(top = 4.dp)) }
}
