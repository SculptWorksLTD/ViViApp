package it.migliorati.viviapp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Scalette: lezioni divise in fasi con durata, playlist e campana al cambio. */
@Composable
fun PlansTab() {
    val plans by Mixer.plans.collectAsState()
    val run by Mixer.planRun.collectAsState()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    val edit = editing
    if (edit != null && plans.any { it.name == edit }) {
        PlanEditor(edit, onClose = { editing = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Hint(tr(
            "A lesson plan divides the lesson into phases (e.g. warm-up 10′, flow 30′, savasana 10′). Each phase can have its own playlist: when the time is up the bell rings and the music changes by itself.",
            "Plan sata dijeli sat na faze (npr. zagrijavanje 10′, flow 30′, savasana 10′). Svaka faza može imati svoj popis: kad istekne vrijeme, zvono zazvoni i glazba se sama promijeni.",
            "Una scaletta divide la lezione in fasi (es. riscaldamento 10′, flow 30′, savasana 10′). Ogni fase può avere la sua playlist: allo scadere del tempo suona la campana e la musica cambia da sola.",
        ))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${plans.size} " + tr("lesson plans", "planova sata", "scalette"), Modifier.weight(1f))
            Button(onClick = { editing = Mixer.createPlan("") }) { Text("+  " + tr("New lesson plan", "Novi plan sata", "Nuova scaletta")) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(plans, key = { it.name }) { p ->
                val running = run?.plan?.name == p.name
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Hint(
                                p.phases.joinToString(" · ") { "${it.name} ${it.minutes}′" } +
                                    "  =  ${p.totalMinutes}′" +
                                    if (running) tr(" · running", " · u tijeku", " · in corso") else ""
                            )
                        }
                        if (!running) TextButton(onClick = { Mixer.startPlan(p.name) }) { Text("▶  " + tr("Start", "Pokreni", "Avvia")) }
                        else TextButton(onClick = { Mixer.stopPlan() }) { Text("■  " + tr("Stop", "Zaustavi", "Ferma")) }
                        TextButton(onClick = { editing = p.name }) { Text(tr("Edit", "Uredi", "Modifica")) }
                        TextButton(onClick = { toDelete = p.name }) { Text(tr("Delete", "Obriši", "Elimina")) }
                    }
                }
            }
        }
    }
    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("Delete the lesson plan?", "Obrisati plan sata?", "Eliminare la scaletta?")) },
            text = { Text(n) },
            confirmButton = { TextButton(onClick = { Mixer.deletePlan(n); toDelete = null }) { Text(tr("Delete", "Obriši", "Elimina")) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel", "Odustani", "Annulla")) } },
        )
    }
}

@Composable
fun PlanEditor(name: String, onClose: () -> Unit) {
    val plans by Mixer.plans.collectAsState()
    val pls by Mixer.playlists.collectAsState()
    val original = plans.firstOrNull { it.name == name } ?: return
    // Copia di lavoro: si salva con "Fatto"
    var draft by remember(name) { mutableStateOf(original) }

    fun setPhase(i: Int, ph: Phase) { draft = draft.copy(phases = draft.phases.toMutableList().also { it[i] = ph }) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.name, onValueChange = { draft = draft.copy(name = it) }, singleLine = true,
                    label = { Text(tr("Lesson plan name", "Naziv plana sata", "Nome della scaletta")) }, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { Mixer.savePlan(name, draft); onClose() }) { Text(tr("Done", "Gotovo", "Fatto")) }
                TextButton(onClick = onClose) { Text(tr("Cancel", "Odustani", "Annulla")) }
            }
            Spacer(Modifier.height(4.dp))
            SwitchRow(tr("Bell at each phase change", "Zvono pri svakoj promjeni faze", "Campana a ogni cambio di fase"), draft.bellOnChange) {
                draft = draft.copy(bellOnChange = it)
            }
            if (draft.bellOnChange) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bell.entries.forEach { b ->
                    FilterChip(selected = draft.bell == b.id, onClick = { draft = draft.copy(bell = b.id) }, label = { Text(b.label) })
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Phases", "Faze", "Fasi") + " · " + tr("total", "ukupno", "totale") + " ${draft.totalMinutes}′",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        itemsIndexed(draft.phases) { i, ph ->
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = ph.name, onValueChange = { setPhase(i, ph.copy(name = it)) }, singleLine = true,
                            label = { Text(tr("Phase", "Faza", "Fase")) }, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { setPhase(i, ph.copy(minutes = (ph.minutes - 1).coerceAtLeast(1))) }) { Text("−") }
                        Text("${ph.minutes}′", Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { setPhase(i, ph.copy(minutes = (ph.minutes + 1).coerceAtMost(180))) }) { Text("+") }
                    }
                    Hint(tr("Music for this phase", "Glazba za ovu fazu", "Musica di questa fase"), Modifier.padding(top = 6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = ph.playlist == null, onClick = { setPhase(i, ph.copy(playlist = null)) },
                            label = { Text(tr("Keep current music", "Zadrži trenutnu glazbu", "Continua la musica attuale")) })
                        pls.forEach { p ->
                            FilterChip(selected = ph.playlist == p.name, onClick = { setPhase(i, ph.copy(playlist = p.name)) }, label = { Text(p.name) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled = i > 0, onClick = {
                            draft = draft.copy(phases = draft.phases.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) })
                        }) { Text("↑") }
                        TextButton(enabled = i < draft.phases.size - 1, onClick = {
                            draft = draft.copy(phases = draft.phases.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) })
                        }) { Text("↓") }
                        TextButton(enabled = draft.phases.size > 1, onClick = {
                            draft = draft.copy(phases = draft.phases.toMutableList().also { l -> l.removeAt(i) })
                        }) { Text(tr("Remove phase", "Ukloni fazu", "Togli fase")) }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = {
                draft = draft.copy(phases = draft.phases + Phase(tr("New phase", "Nova faza", "Nuova fase"), 10, null))
            }) { Text("+  " + tr("Add phase", "Dodaj fazu", "Aggiungi fase")) }
            if (pls.isEmpty()) Hint(
                tr("Tip: create playlists in Library → Playlists to assign music to each phase.",
                    "Savjet: napravi popise u Knjižnica → Popisi kako bi svakoj fazi dodijelio glazbu.",
                    "Consiglio: crea delle playlist in Libreria → Playlist per assegnare la musica a ogni fase."),
                Modifier.padding(top = 8.dp),
            )
        }
    }
}
