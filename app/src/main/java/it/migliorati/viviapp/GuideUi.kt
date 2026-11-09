package it.migliorati.viviapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Manuale d'uso dentro l'app, nella lingua scelta. Le sezioni si aprono toccandole. */
@Composable
fun GuideTab() {
    var open by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(tr("User manual", "Korisnički priručnik", "Manuale d'uso"), style = MaterialTheme.typography.titleLarge)
            Hint(tr(
                "Tap a section to open it. The same manual is in the GitHub repository (docs folder), in English, Croatian and Italian.",
                "Dodirni odjeljak da ga otvoriš. Isti priručnik nalazi se u GitHub repozitoriju (mapa docs), na engleskom, hrvatskom i talijanskom.",
                "Tocca una sezione per aprirla. Lo stesso manuale è nella repository GitHub (cartella docs), in inglese, croato e italiano.",
            ))
            Hint("ViViApp " + Updater.currentVersion, Modifier.padding(top = 4.dp))
        }
        itemsIndexed(manual) { i, sec ->
            val expanded = open == i
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.clickable { open = if (expanded) -1 else i }.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.  " + sec.title(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (expanded) {
                        sec.paragraphs.forEach { p ->
                            Spacer(Modifier.height(10.dp))
                            p().split('\n').forEach { line ->
                                if (line.startsWith("• ")) {
                                    Row(Modifier.padding(top = 3.dp)) {
                                        Text("•", Modifier.width(18.dp), color = MaterialTheme.colorScheme.primary)
                                        Text(line.removePrefix("• "), Modifier.weight(1f))
                                    }
                                } else {
                                    Text(line)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
