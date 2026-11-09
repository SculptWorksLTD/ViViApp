package it.migliorati.viviapp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val searchPresets = listOf(
    "meditation", "ambient", "relaxing piano", "yoga", "nature", "tibetan", "flute", "handpan", "lounge chill",
)

@Composable
fun SearchTab() {
    val f by MusicFinder.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var useOv by rememberSaveable { mutableStateOf(true) }
    var useIa by rememberSaveable { mutableStateOf(true) }
    val doSearch = { MusicFinder.search(query, useOv, useIa) }
    // L'anteprima si ferma quando si cambia scheda
    DisposableEffect(Unit) { onDispose { MusicFinder.stopPreview() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("Free music to download", "Besplatna glazba za preuzimanje", "Musica libera da scaricare"), style = MaterialTheme.typography.titleMedium)
                    Hint(tr(
                        "Only tracks with licences that allow use in paid lessons. Search in English.",
                        "Samo pjesme s licencama koje dopuštaju upotrebu na plaćenim satovima. Traži na engleskom.",
                        "Solo brani con licenze che permettono l'uso nelle lezioni a pagamento. Cerca in inglese.",
                    ))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text(tr("e.g. meditation, ambient, flute…", "npr. meditation, ambient, flute…", "es. meditation, ambient, flute…")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { doSearch() }, enabled = query.isNotBlank() && !f.loading) { Text(tr("Search", "Traži", "Cerca")) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        searchPresets.forEach { p ->
                            FilterChip(selected = query == p, onClick = { query = p; MusicFinder.search(p, useOv, useIa) }, label = { Text(p) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = useOv, onClick = { useOv = !useOv }, label = { Text("Openverse") })
                        FilterChip(selected = useIa, onClick = { useIa = !useIa }, label = { Text("Internet Archive") })
                    }
                    if (f.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    f.message?.let { msg -> Hint(msg(), Modifier.padding(top = 6.dp)) }
                }
            }
        }
        if (f.tracks.isNotEmpty()) item { SectionLabel(tr("Tracks", "Pjesme", "Brani")) }
        items(f.tracks, key = { it.id }) { t -> TrackRow(t, f) }
        if (f.albums.isNotEmpty()) item { SectionLabel(tr("Albums from Internet Archive", "Albumi s Internet Archivea", "Album da Internet Archive")) }
        f.albums.forEach { a ->
            item(key = "album:" + a.id) {
                val open = a.id in f.expanded
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${a.creator} · ${a.license}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { MusicFinder.toggleAlbum(a) }) {
                            Text(if (open) tr("Close", "Zatvori", "Chiudi") else tr("Tracks ▾", "Pjesme ▾", "Brani ▾"))
                        }
                    }
                }
            }
            if (a.id in f.expanded) {
                if (a.id in f.albumLoading) item(key = "load:" + a.id) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
                val tr0 = a.tracks.orEmpty()
                if (a.tracks != null && tr0.isEmpty()) item(key = "empty:" + a.id) {
                    Hint(tr("No mp3 in this album.", "Nema mp3 u ovom albumu.", "Nessun mp3 in questo album."), Modifier.padding(start = 24.dp))
                }
                items(tr0, key = { it.id }) { t -> Box(Modifier.padding(start = 16.dp)) { TrackRow(t, f) } }
            }
        }
    }
}

@Composable
private fun TrackRow(t: FoundTrack, f: FinderState) {
    val playing = f.previewId == t.id
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal)
                val dur = t.durationSec?.let { "%d:%02d".format(it / 60, it % 60) }
                Text(
                    listOfNotNull(t.creator, dur, t.license, t.source).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { MusicFinder.togglePreview(t) }) {
                Text(
                    when {
                        playing && f.previewLoading -> "…"
                        playing -> "■ Stop"
                        else -> "▶ " + tr("Listen", "Slušaj", "Ascolta")
                    }
                )
            }
            when {
                t.id in f.downloaded -> Text("✓ " + tr("In library", "U knjižnici", "In libreria"), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                t.id in f.downloading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else -> Button(onClick = { MusicFinder.download(t) }) { Text(tr("Download", "Preuzmi", "Scarica")) }
            }
        }
    }
}
