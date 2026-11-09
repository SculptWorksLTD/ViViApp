package it.migliorati.viviapp

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryTab() {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = section == 0, onClick = { section = 0 }, label = { Text(tr("Tracks", "Pjesme", "Brani")) })
            FilterChip(selected = section == 1, onClick = { section = 1 }, label = { Text(tr("Playlists", "Popisi", "Playlist")) })
            FilterChip(selected = section == 2, onClick = { section = 2 }, label = { Text(tr("Recordings", "Snimke", "Registrazioni")) })
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                0 -> TracksSection()
                1 -> PlaylistsSection()
                else -> RecordingsSection()
            }
        }
    }
}

// ---------------------------------------------------------------- Brani

@Composable
fun TracksSection() {
    val lib by Mixer.library.collectAsState()
    val st by Mixer.state.collectAsState()
    var toDelete by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) Mixer.import(uris)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("${lib.size} tracks saved on the tablet", "${lib.size} pjesama spremljeno na tabletu", "${lib.size} brani salvati sul tablet"), Modifier.weight(1f))
            Button(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("+  " + tr("Import tracks", "Uvezi pjesme", "Importa brani")) }
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
                            val credit = remember(f) { MusicFinder.creditOf(f) }
                            Text(
                                credit ?: "%.1f MB".format(f.length() / 1_048_576.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { Mixer.play(f) }) { Text("▶  " + tr("Play", "Sviraj", "Suona")) }
                        TextButton(onClick = { toDelete = f }) { Text(tr("Delete", "Obriši", "Elimina")) }
                    }
                }
            }
        }
    }

    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("Delete the track?", "Obrisati pjesmu?", "Eliminare il brano?")) },
            text = { Text(f.nameWithoutExtension) },
            confirmButton = { TextButton(onClick = { Mixer.delete(f); toDelete = null }) { Text(tr("Delete", "Obriši", "Elimina")) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel", "Odustani", "Annulla")) } },
        )
    }
}

// ---------------------------------------------------------------- Playlist

@Composable
fun PlaylistsSection() {
    val pls by Mixer.playlists.collectAsState()
    val st by Mixer.state.collectAsState()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    val edit = editing
    if (edit != null && pls.any { it.name == edit }) {
        PlaylistEditor(edit, onClose = { editing = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (pls.isEmpty()) tr("Create a playlist for each type of lesson.", "Napravi popis za svaku vrstu sata.", "Crea una playlist per ogni tipo di lezione.")
                else "${pls.size} " + tr("playlists", "popisa", "playlist"),
                Modifier.weight(1f),
            )
            Button(onClick = { creating = true }) { Text("+  " + tr("New playlist", "Novi popis", "Nuova playlist")) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(pls, key = { it.name }) { p ->
                val active = st.activePlaylist == p.name
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${p.tracks.size} " + tr("tracks", "pjesama", "brani") +
                                    if (active) tr(" · loaded in the mixer", " · učitan u mikser", " · caricata nel mixer") else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!active) TextButton(onClick = { Mixer.loadPlaylist(p.name) }) { Text(tr("Load in mixer", "Učitaj u mikser", "Carica nel mixer")) }
                        TextButton(onClick = { editing = p.name }) { Text(tr("Edit", "Uredi", "Modifica")) }
                        TextButton(onClick = { toDelete = p.name }) { Text(tr("Delete", "Obriši", "Elimina")) }
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(tr("New playlist", "Novi popis", "Nuova playlist")) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    placeholder = { Text(tr("e.g. Thursday Yin", "npr. Yin četvrtkom", "es. Yin del giovedì")) },
                )
            },
            confirmButton = { TextButton(onClick = { editing = Mixer.createPlaylist(name); creating = false }) { Text(tr("Create", "Napravi", "Crea")) } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(tr("Cancel", "Odustani", "Annulla")) } },
        )
    }
    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("Delete the playlist?", "Obrisati popis?", "Eliminare la playlist?")) },
            text = { Text(tr("\"$n\" will be deleted. The tracks stay in the Library.", "\"$n\" će biti obrisan. Pjesme ostaju u Knjižnici.", "\"$n\" viene eliminata. I brani restano in Libreria.")) },
            confirmButton = { TextButton(onClick = { Mixer.deletePlaylist(n); toDelete = null }) { Text(tr("Delete", "Obriši", "Elimina")) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel", "Odustani", "Annulla")) } },
        )
    }
}

@Composable
fun PlaylistEditor(name: String, onClose: () -> Unit) {
    val pls by Mixer.playlists.collectAsState()
    val lib by Mixer.library.collectAsState()
    val pl = pls.firstOrNull { it.name == name } ?: return
    var title by remember(name) { mutableStateOf(name) }
    val inList = pl.tracks.filter { t -> lib.any { it.name == t } }
    val available = lib.filter { it.name !in pl.tracks }

    fun setTracks(t: List<String>) = Mixer.setPlaylistTracks(name, t)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text(tr("Playlist name", "Naziv popisa", "Nome della playlist")) }, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { Mixer.renamePlaylist(name, title); onClose() }) { Text(tr("Done", "Gotovo", "Fatto")) }
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Track order", "Redoslijed pjesama", "Ordine dei brani") + " (${inList.size})", style = MaterialTheme.typography.titleMedium)
            if (inList.isEmpty()) Hint(tr("No tracks: add them from the list below.", "Nema pjesama: dodaj ih s popisa ispod.", "Nessun brano: aggiungili dall'elenco qui sotto."))
        }
        itemsIndexed(inList, key = { _, t -> "in:$t" }) { i, t ->
            SoftCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                    Text(t.substringBeforeLast('.'), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(enabled = i > 0, onClick = {
                        setTracks(inList.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) })
                    }) { Text("↑") }
                    TextButton(enabled = i < inList.size - 1, onClick = {
                        setTracks(inList.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) })
                    }) { Text("↓") }
                    TextButton(onClick = { setTracks(inList - t) }) { Text(tr("Remove", "Ukloni", "Togli")) }
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Text(tr("Add from the library", "Dodaj iz knjižnice", "Aggiungi dalla libreria"), style = MaterialTheme.typography.titleMedium)
            if (available.isEmpty()) Hint(tr("All library tracks are already in the playlist.", "Sve pjesme iz knjižnice već su na popisu.", "Tutti i brani della libreria sono già nella playlist."))
        }
        items(available, key = { "av:" + it.name }) { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.nameWithoutExtension, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { setTracks(inList + f.name) }) { Text("+ " + tr("Add", "Dodaj", "Aggiungi")) }
            }
        }
    }
}

// ---------------------------------------------------------------- Registrazioni

@Composable
fun RecordingsSection() {
    val recs by Mixer.recordings.collectAsState()
    val ctx = LocalContext.current
    var toDelete by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { Mixer.reloadRecordings() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Hint(tr(
            "Lessons recorded with \"Record lesson\" in the Mixer: voice, music and bells, as the clients hear them.",
            "Satovi snimljeni s \"Snimi sat\" u Mikseru: glas, glazba i zvona, kako ih čuju klijenti.",
            "Lezioni registrate con \"Registra lezione\" nel Mixer: voce, musica e campane, come le sentono le clienti.",
        ))
        Spacer(Modifier.height(8.dp))
        if (recs.isEmpty()) Text(tr("No recordings yet.", "Još nema snimki.", "Ancora nessuna registrazione."))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(recs, key = { it.absolutePath }) { f ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Hint(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified())) +
                                " · " + "%.1f MB".format(f.length() / 1_048_576.0))
                        }
                        TextButton(onClick = {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("audio/mp4")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(Intent.createChooser(send, tr("Share recording", "Podijeli snimku", "Condividi registrazione")))
                        }) { Text(tr("Share", "Podijeli", "Condividi")) }
                        TextButton(onClick = { toDelete = f }) { Text(tr("Delete", "Obriši", "Elimina")) }
                    }
                }
            }
        }
    }
    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("Delete the recording?", "Obrisati snimku?", "Eliminare la registrazione?")) },
            text = { Text(f.nameWithoutExtension) },
            confirmButton = { TextButton(onClick = { Mixer.deleteRecording(f); toDelete = null }) { Text(tr("Delete", "Obriši", "Elimina")) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel", "Odustani", "Annulla")) } },
        )
    }
}
