package it.migliorati.viviapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.log10

data class DeviceOption(val id: Int, val label: String, val bluetooth: Boolean)

data class MixerState(
    val sessionOn: Boolean = false,
    val engineRunning: Boolean = false,
    val outputName: String? = null,
    val inputName: String? = null,
    val inputs: List<DeviceOption> = emptyList(),
    val selectedInputId: Int? = null,
    val status: String = "Premi \"Avvia lezione\"",
    val nowPlaying: String? = null,
    val activePlaylist: String? = null,   // null = tutta la libreria
    val musicPlaying: Boolean = false,
    val generating: Boolean = false,
    val genStatus: String = "",
)

data class UpdateState(
    val available: Updater.Update? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val dismissed: Boolean = false,
)

data class Meters(val level: Float = 0f, val voiceDb: Float = -100f, val duckDb: Float = 0f, val bufferMs: Int = 0)

/** Cervello dell'app: gestisce dispositivi, voce, musica, ducking, libreria e generazione. */
object Mixer {
    private lateinit var app: Context
    private lateinit var am: AudioManager
    private lateinit var store: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settings = MutableStateFlow(Settings())
    val state = MutableStateFlow(MixerState())
    val meters = MutableStateFlow(Meters())
    val library = MutableStateFlow<List<File>>(emptyList())
    val updateState = MutableStateFlow(UpdateState())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private lateinit var playlistStore: PlaylistStore

    private lateinit var voice: VoiceEngine
    private lateinit var music: MusicPlayer
    private var pausedByCable = false
    private var lastVoiceAt = 0L
    private var duck = 1f

    val musicDir: File by lazy {
        (app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(app.filesDir, "music")).also { it.mkdirs() }
    }

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        am = app.getSystemService(AudioManager::class.java)
        store = SettingsStore(app)
        settings.value = store.load()
        voice = VoiceEngine { settings.value }
        voice.onError = { msg -> scope.launch { onEngineError(msg) } }
        music = MusicPlayer(scope) { settings.value }
        music.onChanged = { publishMusic() }
        music.onCompleted = { if (settings.value.autoNext) next() }

        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
        }, Handler(Looper.getMainLooper()))

        // Arriva PRIMA che Android sposti l'audio sugli altoparlanti quando si stacca il cavo.
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (music.isPlaying) { pausedByCable = true; music.pauseNow() }
            }
        }, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)

        playlistStore = PlaylistStore(app)
        playlists.value = playlistStore.load()
        reloadLibrary()
        refresh()
        scope.launch { while (true) { tick(); delay(30) } }
    }

    fun update(f: (Settings) -> Settings) {
        val old = settings.value
        val new = f(old)
        settings.value = new
        store.save(new)
        if (old.loopTrack != new.loopTrack) music.setLooping(new.loopTrack)
        if (old.musicVolume != new.musicVolume || old.monitorVolume != new.monitorVolume) music.applyVolume()
        if (old.monitorOn != new.monitorOn) music.refreshMonitor()
        if (old.commMode != new.commMode && state.value.engineRunning) restartEngine()
        if (old.testMode != new.testMode) {
            if (state.value.engineRunning) { if (music.isPlaying) music.pauseNow(); stopEngine() }
            refresh()
        }
    }

    // ---------------- Dispositivi ----------------

    private fun cableOutput(): AudioDeviceInfo? {
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val prio = listOf(
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
        for (t in prio) outs.firstOrNull { it.type == t }?.let { return it }
        // Modalità prova: senza cavo usa l'altoparlante (utile sull'emulatore)
        if (settings.value.testMode) return outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        return null
    }

    private fun isBtMic(t: Int) = t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_BLE_HEADSET

    private fun inputDevices(): List<AudioDeviceInfo> {
        val ok = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_MIC,
        )
        return am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in ok }
            .distinctBy { it.type to it.productName.toString() }
            .sortedBy { if (isBtMic(it.type)) 0 else if (it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) 2 else 1 }
    }

    private fun label(d: AudioDeviceInfo): String {
        val kind = when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfono del tablet"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB-C"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Jack"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altoparlante (modalità prova)"
            else -> "Linea"
        }
        val name = d.productName?.toString()?.trim().orEmpty()
        return if (name.isEmpty() || d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) kind else "$kind · $name"
    }

    private fun chooseInput(): AudioDeviceInfo? {
        val list = inputDevices()
        val sel = state.value.selectedInputId
        return list.firstOrNull { it.id == sel }
            ?: list.firstOrNull { isBtMic(it.type) }
            ?: list.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }

    fun selectInput(id: Int) {
        state.update { it.copy(selectedInputId = id) }
        if (state.value.engineRunning) restartEngine()
    }

    /** Ricontrolla i dispositivi e accende/spegne il motore di conseguenza. */
    fun refresh() {
        val out = cableOutput()
        val ins = inputDevices().map { DeviceOption(it.id, label(it), isBtMic(it.type)) }
        state.update { it.copy(inputs = ins, outputName = out?.let { label(it) }) }
        val st = state.value
        when {
            st.sessionOn && out != null && !st.engineRunning -> {
                if (st.status.startsWith("Cavo scollegato") && !settings.value.autoResume) return
                startEngine(out)
                if (pausedByCable && settings.value.autoResume && music.hasTrack()) {
                    pausedByCable = false
                    music.resume(out)
                }
            }
            st.engineRunning && out == null -> {
                if (music.isPlaying) { pausedByCable = true; music.pauseNow() }
                stopEngine()
                state.update { it.copy(status = "Cavo scollegato: tutto in pausa. Ricollegalo per ripartire.") }
            }
            st.sessionOn && out == null ->
                state.update { it.copy(status = "Collega il cavo USB-C al trasmettitore") }
        }
    }

    // ---------------- Sessione / motore ----------------

    fun startSession() {
        if (state.value.sessionOn) return
        state.update { it.copy(sessionOn = true, status = "Avvio…") }
        ContextCompat.startForegroundService(app, Intent(app, MixerService::class.java))
        refresh()
    }

    fun stopSession() {
        music.stop()
        stopEngine()
        pausedByCable = false
        state.update { it.copy(sessionOn = false, status = "Lezione terminata") }
        app.stopService(Intent(app, MixerService::class.java))
    }

    private fun startEngine(out: AudioDeviceInfo) {
        val inp = chooseInput()
        val bt = inp != null && isBtMic(inp.type)
        if (bt) {
            if (settings.value.commMode) am.mode = AudioManager.MODE_IN_COMMUNICATION
            val comm = am.availableCommunicationDevices.firstOrNull { isBtMic(it.type) }
            if (comm != null) am.setCommunicationDevice(comm)
        }
        val burst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
        voice.start(inp, out, bt, burst)
        // Ascolto insegnante: la musica va anche nell'auricolare Bluetooth
        val earpiece = if (bt) am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { isBtMic(it.type) } else null
        music.setMonitorDevice(earpiece)
        state.update {
            it.copy(
                engineRunning = true,
                inputName = inp?.let { label(it) } ?: "nessun microfono",
                outputName = label(out),
                selectedInputId = inp?.id ?: it.selectedInputId,
                status = "In onda",
            )
        }
    }

    private fun stopEngine() {
        voice.stop()
        music.setMonitorDevice(null)
        try { am.clearCommunicationDevice() } catch (_: Exception) {}
        am.mode = AudioManager.MODE_NORMAL
        state.update { it.copy(engineRunning = false) }
    }

    /** Riparte a mano dopo che il cavo è stato ricollegato (se la ripresa automatica è spenta). */
    fun resumeNow() {
        val out = cableOutput() ?: return
        if (!state.value.engineRunning) startEngine(out)
        if (pausedByCable && music.hasTrack()) { pausedByCable = false; music.resume(out) }
    }

    private fun restartEngine() {
        val out = cableOutput() ?: return
        stopEngine()
        startEngine(out)
    }

    private fun onEngineError(msg: String) {
        stopEngine()
        state.update { it.copy(status = "Errore audio: $msg") }
    }

    // ---------------- Ducking + VU meter ----------------

    private fun tick() {
        val s = settings.value
        val running = state.value.engineRunning
        val now = SystemClock.elapsedRealtime()
        val env = if (running) voice.envelopeDb else -100f
        if (env > s.duckThresholdDb) lastVoiceAt = now
        val talking = s.duckOn && running && now - lastVoiceAt < 300
        val target = if (talking) dbToLin(s.duckDepthDb) else 1f
        val tau = if (target < duck) 150f else s.duckReleaseMs
        duck += (target - duck) * (1f - exp(-30f / tau))
        music.setDuck(duck)

        val pk = if (running) voice.peak else 0f
        val pkDb = 20f * log10(pk + 1e-6f)
        meters.value = Meters(
            level = ((pkDb + 60f) / 60f).coerceIn(0f, 1f),
            voiceDb = env,
            duckDb = 20f * log10(duck),
            bufferMs = if (running) voice.bufferMs else 0,
        )
    }

    // ---------------- Musica ----------------

    private fun publishMusic() {
        state.update { it.copy(nowPlaying = music.current?.nameWithoutExtension, musicPlaying = music.isPlaying) }
    }

    fun play(file: File) {
        val out = cableOutput()
        if (out == null) {
            state.update { it.copy(status = "Collega il cavo USB-C: la musica esce solo da lì") }
            return
        }
        if (!state.value.sessionOn) startSession()
        try {
            pausedByCable = false
            music.play(file, out)
        } catch (e: Exception) {
            state.update { it.copy(status = "Impossibile riprodurre ${file.name}: ${e.message}") }
        }
    }

    fun togglePlay() {
        if (music.isPlaying) { music.pause(); return }
        val out = cableOutput() ?: run {
            state.update { it.copy(status = "Collega il cavo USB-C: la musica esce solo da lì") }
            return
        }
        if (music.hasTrack()) music.resume(out)
        else queue().firstOrNull()?.let { play(it) }
    }

    fun stopMusic() = music.stop()

    fun next() {
        val q = queue()
        if (q.isEmpty()) return
        val i = q.indexOfFirst { it == music.current }
        play(q[(i + 1) % q.size])
    }

    // ---------------- Playlist ----------------

    /** Coda di riproduzione: la playlist caricata nel mixer, oppure tutta la libreria. */
    fun queue(): List<File> {
        val name = state.value.activePlaylist ?: return library.value
        val pl = playlists.value.firstOrNull { it.name == name } ?: return library.value
        val byName = library.value.associateBy { it.name }
        return pl.tracks.mapNotNull { byName[it] }
    }

    /** Carica una playlist nel mixer (null = tutta la libreria). */
    fun loadPlaylist(name: String?) {
        state.update { it.copy(activePlaylist = name) }
    }

    private fun savePlaylists(list: List<Playlist>) {
        playlists.value = list
        try { playlistStore.save(list) } catch (_: Exception) {}
    }

    /** Crea una playlist; restituisce il nome effettivo (unico). */
    fun createPlaylist(name: String): String {
        val base = name.trim().ifEmpty { "Nuova lezione" }
        var n = base
        var i = 2
        while (playlists.value.any { it.name.equals(n, ignoreCase = true) }) { n = "$base $i"; i++ }
        savePlaylists(playlists.value + Playlist(n, emptyList()))
        return n
    }

    fun renamePlaylist(old: String, new: String): String {
        val target = new.trim()
        if (target.isEmpty() || target == old) return old
        if (playlists.value.any { it.name.equals(target, ignoreCase = true) && it.name != old }) return old
        savePlaylists(playlists.value.map { if (it.name == old) it.copy(name = target) else it })
        if (state.value.activePlaylist == old) state.update { it.copy(activePlaylist = target) }
        return target
    }

    fun deletePlaylist(name: String) {
        savePlaylists(playlists.value.filterNot { it.name == name })
        if (state.value.activePlaylist == name) state.update { it.copy(activePlaylist = null) }
    }

    fun setPlaylistTracks(name: String, tracks: List<String>) {
        savePlaylists(playlists.value.map { if (it.name == name) it.copy(tracks = tracks) else it })
    }

    // ---------------- Libreria ----------------

    private val audioExt = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus")

    fun reloadLibrary() {
        library.value = musicDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in audioExt }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun import(uris: List<Uri>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    var name = "brano_${System.currentTimeMillis()}.mp3"
                    app.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0)?.let { name = it }
                    }
                    val dest = uniqueFile(name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                    app.contentResolver.openInputStream(u)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            reloadLibrary()
        }
    }

    fun delete(file: File) {
        if (music.current == file) music.release().also { publishMusic() }
        file.delete()
        File(file.path + ".json").delete()   // eventuali crediti del brano
        // Toglie il brano anche dalle playlist
        if (playlists.value.any { file.name in it.tracks }) {
            savePlaylists(playlists.value.map { p -> p.copy(tracks = p.tracks.filterNot { it == file.name }) })
        }
        reloadLibrary()
    }

    /** Nuovo file in Libreria con nome libero (aggiunge _2, _3… se esiste già). */
    fun newLibraryFile(name: String): File = uniqueFile(name)

    private fun uniqueFile(name: String): File {
        var f = File(musicDir, name)
        var n = 2
        while (f.exists()) {
            f = File(musicDir, "${name.substringBeforeLast('.')}_$n.${name.substringAfterLast('.', "mp3")}")
            n++
        }
        return f
    }

    // ---------------- Generatore AI (online, ACE-Step su Hugging Face) ----------------

    const val DEFAULT_SPACE = "https://ace-step-ace-step.hf.space"

    private fun spaceBase(): String =
        settings.value.serverUrl.trim().trimEnd('/').ifEmpty { DEFAULT_SPACE }

    private fun conn(url: String, method: String = "GET"): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = 15 * 60_000   // coda + generazione
        val token = settings.value.serverToken.trim()
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        return c
    }

    private fun errorText(c: HttpURLConnection): String {
        val body = try { c.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
        val detail = try { body?.let { JSONObject(it).optString("detail", JSONObject(it).optString("error")) } } catch (_: Exception) { body?.take(200) }
        return "HTTP ${c.responseCode}" + (detail?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
    }

    fun testServer() {
        scope.launch {
            state.update { it.copy(genStatus = "Controllo…") }
            val msg = withContext(Dispatchers.IO) {
                val parts = mutableListOf<String>()
                // 1) token Hugging Face
                val token = settings.value.serverToken.trim()
                if (token.isEmpty()) parts += "Manca il token Hugging Face (senza token la quota gratuita è quasi nulla)"
                else try {
                    val c = conn("https://huggingface.co/api/whoami-v2")
                    parts += if (c.responseCode == 200)
                        "Token OK (account ${JSONObject(c.inputStream.bufferedReader().readText()).optString("name")})"
                    else "Token non valido (${errorText(c)})"
                } catch (e: Exception) { parts += "Internet non raggiungibile: ${e.message}" }
                // 2) generatore
                try {
                    val c = conn(spaceBase() + "/gradio_api/info")
                    parts += if (c.responseCode == 200) "Generatore online" else "Generatore non disponibile (${errorText(c)})"
                } catch (e: Exception) { parts += "Generatore non raggiungibile: ${e.message}" }
                parts.joinToString(" · ")
            }
            state.update { it.copy(genStatus = msg) }
        }
    }

    /** Genera un brano strumentale online e lo scarica in Libreria. */
    fun generate(prompt: String, seconds: Int) {
        if (state.value.generating) return
        scope.launch {
            state.update { it.copy(generating = true, genStatus = "Invio la richiesta…") }
            val msg = withContext(Dispatchers.IO) {
                try {
                    val base = spaceBase()
                    // Parametri dell'endpoint /__call__ di ACE-Step (Text2Music), nell'ordine dell'API.
                    val data = JSONArray()
                        .put(seconds.toDouble())   // audio_duration
                        .put(prompt)               // prompt (tag di stile)
                        .put("[inst]")             // lyrics: solo strumentale
                        .put(60)                   // infer_step
                        .put(15.0)                 // guidance_scale
                        .put("euler")              // scheduler_type
                        .put("apg")                // cfg_type
                        .put(10.0)                 // omega_scale
                        .put(JSONObject.NULL)      // manual_seeds
                        .put(0.5)                  // guidance_interval
                        .put(0.0)                  // guidance_interval_decay
                        .put(3.0)                  // min_guidance_scale
                        .put(true)                 // use_erg_tag
                        .put(false)                // use_erg_lyric
                        .put(true)                 // use_erg_diffusion
                        .put(JSONObject.NULL)      // oss_steps
                        .put(0.0)                  // guidance_scale_text
                        .put(0.0)                  // guidance_scale_lyric
                        .put(false)                // audio2audio_enable
                        .put(0.5)                  // ref_audio_strength
                        .put(JSONObject.NULL)      // ref_audio_input
                        .put("none")               // lora_name_or_path

                    // 1) mette in coda la richiesta
                    val c1 = conn("$base/gradio_api/call/__call__", "POST")
                    c1.doOutput = true
                    c1.setRequestProperty("Content-Type", "application/json")
                    c1.outputStream.use { it.write(JSONObject().put("data", data).toString().toByteArray()) }
                    if (c1.responseCode != 200) return@withContext "Errore dal generatore: ${errorText(c1)}"
                    val eventId = JSONObject(c1.inputStream.bufferedReader().readText()).getString("event_id")

                    withContext(Dispatchers.Main) { state.update { it.copy(genStatus = "In coda / generazione in corso… (di solito meno di 2 minuti)") } }

                    // 2) aspetta il risultato (stream di eventi)
                    val c2 = conn("$base/gradio_api/call/__call__/$eventId")
                    if (c2.responseCode != 200) return@withContext "Errore dal generatore: ${errorText(c2)}"
                    var event = ""
                    var result: String? = null
                    var failure: String? = null
                    c2.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            when {
                                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                                line.startsWith("data:") && event == "complete" -> { result = line.removePrefix("data:").trim(); break }
                                line.startsWith("data:") && event == "error" -> { failure = line.removePrefix("data:").trim(); break }
                            }
                        }
                    }
                    if (failure != null || result == null) {
                        val f = failure?.takeIf { it != "null" } ?: ""
                        val quota = f.contains("quota", true) || f.contains("GPU", true)
                        return@withContext if (quota) "Quota GPU gratuita esaurita per oggi: riprova domani (o con un account PRO). $f"
                                           else "Il generatore ha restituito un errore. ${f.take(200)}".trim()
                    }

                    // 3) scarica il file audio
                    val out = JSONArray(result!!).get(0)
                    val fileObj = out as? JSONObject ?: return@withContext "Risposta inattesa dal generatore"
                    val url = fileObj.optString("url").ifEmpty { "$base/gradio_api/file=" + fileObj.optString("path") }
                    val ext = url.substringAfterLast('.', "wav").substringBefore('?').lowercase()
                        .takeIf { it in audioExt } ?: "wav"
                    withContext(Dispatchers.Main) { state.update { it.copy(genStatus = "Scarico il brano…") } }
                    val c3 = conn(url)
                    if (c3.responseCode != 200) return@withContext "Download fallito: ${errorText(c3)}"
                    val slug = prompt.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifEmpty { "traccia" }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())
                    val dest = uniqueFile("AI_${slug}_$stamp.$ext")
                    c3.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    "Fatto: ${dest.name} è in Libreria (disponibile anche offline)"
                } catch (e: Exception) { "Errore: ${e.message}" }
            }
            reloadLibrary()
            state.update { it.copy(generating = false, genStatus = msg) }
        }
    }

    // ---------------- Aggiornamenti ----------------

    /** manual = premuto dall'utente; all'apertura il controllo è silenzioso se non c'è nulla. */
    fun checkUpdate(manual: Boolean) {
        if (!manual && Updater.isDevBuild) return
        scope.launch {
            if (manual) updateState.value = UpdateState(message = "Controllo aggiornamenti…")
            try {
                val u = Updater.check()
                updateState.value = when {
                    u != null -> UpdateState(available = u)
                    manual -> UpdateState(message = "Hai già l'ultima versione (${Updater.currentVersion}).")
                    else -> UpdateState()
                }
            } catch (e: Exception) {
                if (manual) updateState.value = UpdateState(message = "Impossibile controllare gli aggiornamenti: ${e.message}")
            }
        }
    }

    fun dismissUpdate() = updateState.update { it.copy(dismissed = true) }

    fun startUpdate() {
        val u = updateState.value.available ?: return
        if (updateState.value.downloading) return
        if (!Updater.canInstall(app)) {
            updateState.update { it.copy(message = "Consenti \"Installa app sconosciute\" per ViViApp, poi torna qui e premi di nuovo Aggiorna.") }
            Updater.openInstallPermission(app)
            return
        }
        scope.launch {
            updateState.update { it.copy(downloading = true, progress = 0f, message = "Scarico la versione ${u.version}…") }
            try {
                val apk = Updater.download(app, u.url) { p -> updateState.update { it.copy(progress = p) } }
                updateState.update { it.copy(downloading = false, progress = 1f, message = "Conferma l'installazione nella schermata di Android.") }
                Updater.install(app, apk)
            } catch (e: Exception) {
                updateState.update { it.copy(downloading = false, message = "Aggiornamento non riuscito: ${e.message}") }
            }
        }
    }
}
