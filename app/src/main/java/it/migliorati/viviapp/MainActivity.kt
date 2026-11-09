package it.migliorati.viviapp

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

/** Stato condiviso dell'interfaccia: la schermata lezione a tutto schermo. */
object UiState {
    var lessonScreen by mutableStateOf(false)
}

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val st by Mixer.state.collectAsState()
    // Le etichette si leggono qui: cambiando lingua si ridisegnano subito
    val tabs = listOf(
        tr("Mixer", "Mikser", "Mixer"),
        tr("Library", "Knjižnica", "Libreria"),
        tr("Lesson plans", "Planovi sata", "Scalette"),
        tr("Find music", "Traži glazbu", "Cerca musica"),
        tr("Generate", "Generiraj", "Genera"),
        tr("Settings", "Postavke", "Impostazioni"),
    )
    // Controllo aggiornamenti all'apertura
    LaunchedEffect(Unit) { if (Mixer.settings.value.autoUpdate) Mixer.checkUpdate(manual = false) }
    // Se la lezione finisce, si esce dalla schermata lezione
    LaunchedEffect(st.sessionOn) { if (!st.sessionOn) UiState.lessonScreen = false }

    if (UiState.lessonScreen && st.sessionOn) {
        LessonScreen()
        return
    }

    UpdateDialog()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViViHeader()
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
                    2 -> PlansTab()
                    3 -> SearchTab()
                    4 -> GenerateTab()
                    else -> SettingsTab()
                }
            }
        }
        NoticeBar(Modifier.align(Alignment.BottomCenter))
    }
}

/** Avvisi brevi in basso (registrazione salvata, copia di sicurezza…), spariscono da soli. */
@Composable
fun NoticeBar(modifier: Modifier = Modifier) {
    val st by Mixer.state.collectAsState()
    val n = st.notice ?: return
    LaunchedEffect(n) { delay(5000); Mixer.clearNotice() }
    Surface(
        modifier = modifier.padding(16.dp).navigationBarsPadding(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        onClick = { Mixer.clearNotice() },
    ) {
        Text(n(), Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
    }
}
