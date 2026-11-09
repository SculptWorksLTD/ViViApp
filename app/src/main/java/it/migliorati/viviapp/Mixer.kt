package it.migliorati.viviapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.BatteryManager
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

data class DeviceOption(val id: Int, val label: UiText, val bluetooth: Boolean)

data class MixerState(
    val sessionOn: Boolean = false,
    val engineRunning: Boolean = false,
    val outputName: UiText? = null,
    val inputName: UiText? = null,
    val inputs: List<DeviceOption> = emptyList(),
    val selectedInputId: Int? = null,
    val status: UiText = { tr("Press \"Start lesson\"", "Pritisni \"Započni sat\"", "Premi \"Avvia lezione\"") },
    val btLost: Boolean = false,          // l'headset Bluetooth è caduto durante la lezione
    val nowPlaying: String? = null,
    val activePlaylist: String? = null,   // null = tutta la libreria
    val musicPlaying: Boolean = false,
    val generating: Boolean = false,
    val genStatus: UiText? = null,
    val battery: Int = -1,
    val charging: Boolean = false,
    val recording: Boolean = false,
    val recordingStartedAt: Long = 0L,
    val notice: UiText? = null,           // messaggi brevi (registrazione, backup…)
)

data class UpdateState(
    val available: Updater.Update? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: UiText? = null,
    val dismissed: Boolean = false,
)

data class Meters(val level: Float = 0f, val voiceDb: Float = -100f, val duckDb: Float = 0f, val bufferMs: Int = 0)

/** Cervello dell'app: dispositivi, voce, musica, ducking, scalette, libreria, registrazione. */
object Mixer {
    private lateinit var app: Context
    private lateinit var am: AudioManager
    private lateinit var store: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settings = MutableStateFlow(Settings())
    val state = MutableStateFlow(MixerState())
    val meters = MutableStateFlow(Meters())
    val library = MutableStateFlow<List<File>>(emptyList())
    val recordings = MutableStateFlow<List<File>>(emptyList())
    val updateState = MutableStateFlow(UpdateState())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val plans = MutableStateFlow<List<LessonPlan>>(emptyList())
    val planRun = MutableStateFlow<PlanRun?>(null)
    private lateinit var playlistStore: PlaylistStore
    private lateinit var planStore: PlanStore

    private lateinit var voice: VoiceEngine
    private lateinit var music: MusicPlayer
    private var buttons: MediaButtons? = null
    private var pausedByCable = false
    private var cableStopped = false      // il cavo è stato staccato a lezione in corso
    private var engineUsesBt = false
    private var lastVoiceAt = 0L
    private var duck = 1f
    private var tickCount = 0

    val musicDir: File by lazy {
        (app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(app.filesDir, "music")).also { it.mkdirs() }
    }
    val recordingsDir: File by lazy { File(musicDir, "Recordings").also { it.mkdirs() } }
    private val playlistsFile get() = File(app.filesDir, "playlists.json")
    private val plansFile get() = File(app.filesDir, "plans.json")

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        am = app.getSystemService(AudioManager::class.java)
        store = SettingsStore(app)
        settings.value = store.load()
        L.apply(settings.value.language)
        voice = VoiceEngine { settings.value }
        voice.onError = { msg -> scope.launch { onEngineError(msg) } }
        music = MusicPlayer(scope) { settings.value }
        music.onChanged = { publishMusic() }
        music.onCompleted = { if (settings.value.autoNext) next() }
        music.onNearEnd = { next(crossfade = true) }

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
        planStore = PlanStore(app)
        plans.value = planStore.load()
        reloadLibrary()
        reloadRecordings()
        updateBattery()
        refresh()
        Thread { Bells.preload() }.start()
        scope.launch { while (true) { tick(); delay(30) } }
    }

    fun update(f: (Settings) -> Settings) {
        val old = settings.value
        val new = f(old)
        settings.value = new
        store.save(new)
        if (old.language != new.language) L.apply(new.language)
        if (old.loopTrack != new.loopTrack) music.setLooping(new.loopTrack)
        if (old.musicVolume != new.musicVolume || old.monitorVolume != new.monitorVolume) music.applyVolume()
        if (old.monitorOn != new.monitorOn) music.refreshMonitor()
        if (old.commMode != new.commMode && state.value.engineRunning) restartEngine()
        if (old.mediaButtons != new.mediaButtons) updateButtons()
        if (old.testMode != new.testMode) {
            if (state.value.engineRunning) { if (music.isPlaying) music.pauseNow(); stopEngine() }
            refresh()
        }
    }

    private fun notice(t: UiText) = state.update { it.copy(notice = t) }
    fun clearNotice() = state.update { it.copy(notice = null) }

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

    private fun label(d: AudioDeviceInfo): UiText {
        val type = d.type
        val name = d.productName?.toString()?.trim().orEmpty()
        return {
            val kind = when (type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> tr("Tablet microphone", "Mikrofon tableta", "Microfono del tablet")
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB-C"
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Jack"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> tr("Speaker (test mode)", "Zvučnik (probni način)", "Altoparlante (modalità prova)")
                else -> tr("Line", "Linija", "Linea")
            }
            if (name.isEmpty() || type == AudioDeviceInfo.TYPE_BUILTIN_MIC) kind else "$kind · $name"
        }
    }

    private fun chooseInput(): AudioDeviceInfo? {
        val list = inputDevices()
        val sel = state.value.selectedInputId
        return list.firstOrNull { it.id == sel }
            ?: list.firstOrNull { isBtMic(it.type) }
            ?: list.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }

    fun selectInput(id: Int) {
        state.update { it.copy(selectedInputId = id, btLost = false) }
        if (state.value.engineRunning) restartEngine() else refresh()
    }

    /** Headset caduto: continua la lezione con il microfono del tablet. */
    fun useTabletMic() {
        val mic = inputDevices().firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC } ?: return
        selectInput(mic.id)
    }

    /** Ricontrolla i dispositivi e accende/spegne il motore di conseguenza. */
    fun refresh() {
        val out = cableOutput()
        val devs = inputDevices()
        val ins = devs.map { DeviceOption(it.id, label(it), isBtMic(it.type)) }
        state.update { it.copy(inputs = ins, outputName = out?.let { o -> label(o) }) }
        val st = state.value
        val btPresent = devs.any { isBtMic(it.type) }

        // 1) L'headset Bluetooth è caduto a lezione in corso: la musica continua, la voce si ferma
        if (st.engineRunning && engineUsesBt && !btPresent) {
            stopEngine()
            state.update {
                it.copy(
                    btLost = true,
                    status = { tr("Bluetooth headset disconnected: music keeps playing", "Bluetooth slušalice odspojene: glazba svira dalje", "Headset Bluetooth scollegato: la musica continua") },
                )
            }
            return
        }
        // 2) …ed è tornato: si riparte da soli
        if (st.btLost) {
            if (btPresent && st.sessionOn && out != null) {
                state.update { it.copy(btLost = false, selectedInputId = null) }
                startEngine(out)
            }
            if (out == null && music.isPlaying) { pausedByCable = true; music.pauseNow() }
            return
        }
        when {
            st.sessionOn && out != null && !st.engineRunning -> {
                if (cableStopped && !settings.value.autoResume) return
                cableStopped = false
                startEngine(out)
                if (pausedByCable && settings.value.autoResume && music.hasTrack()) {
                    pausedByCable = false
                    music.resume(out)
                }
            }
            st.engineRunning && out == null -> {
                if (music.isPlaying) { pausedByCable = true; music.pauseNow() }
                cableStopped = true
                stopEngine()
                state.update { it.copy(status = { tr("Cable unplugged: everything paused. Plug it back in to resume.", "Kabel odspojen: sve je pauzirano. Ponovno ga spoji za nastavak.", "Cavo scollegato: tutto in pausa. Ricollegalo per ripartire.") }) }
            }
            st.sessionOn && out == null ->
                state.update { it.copy(status = { tr("Connect the USB-C cable to the transmitter", "Spoji USB-C kabel na odašiljač", "Collega il cavo USB-C al trasmettitore") }) }
        }
    }

    // ---------------- Sessione / motore ----------------

    fun startSession() {
        if (state.value.sessionOn) return
        state.update { it.copy(sessionOn = true, btLost = false, status = { tr("Starting…", "Pokretanje…", "Avvio…") }) }
        ContextCompat.startForegroundService(app, Intent(app, MixerService::class.java))
        updateButtons()
        refresh()
    }

    fun stopSession() {
        stopPlan(fade = false)
        if (Recorder.recording) stopRecording()
        music.stop()
        stopEngine()
        pausedByCable = false
        cableStopped = false
        state.update { it.copy(sessionOn = false, btLost = false, status = { tr("Lesson ended", "Sat završen", "Lezione terminata") }) }
        updateButtons()
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
        engineUsesBt = bt
        val burst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
        voice.start(inp, out, bt, burst)
        // Ascolto insegnante: la musica va anche nell'auricolare Bluetooth
        val earpiece = if (bt) am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { isBtMic(it.type) } else null
        music.setMonitorDevice(earpiece)
        state.update {
            it.copy(
                engineRunning = true,
                inputName = inp?.let { d -> label(d) } ?: { tr("no microphone", "nema mikrofona", "nessun microfono") },
                outputName = label(out),
                selectedInputId = inp?.id ?: it.selectedInputId,
                status = { tr("On air", "U eteru", "In onda") },
            )
        }
    }

    private fun stopEngine() {
        voice.stop()
        engineUsesBt = false
        music.setMonitorDevice(null)
        try { am.clearCommunicationDevice() } catch (_: Exception) {}
        am.mode = AudioManager.MODE_NORMAL
        state.update { it.copy(engineRunning = false) }
    }

    /** Riparte a mano dopo che il cavo è stato ricollegato (se la ripresa automatica è spenta). */
    fun resumeNow() {
        val out = cableOutput() ?: return
        cableStopped = false
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
        state.update { it.copy(status = { tr("Audio error: ", "Audio greška: ", "Errore audio: ") + msg }) }
    }

    // ---------------- Ducking, VU meter, batteria, scaletta ----------------

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

        tickCount++
        if (tickCount % 33 == 0) {          // circa una volta al secondo
            planRun.value?.let { run -> if (now >= run.phaseEndsAt) advancePlan() }
            if (buttons != null && state.value.sessionOn) buttons?.update(music.isPlaying, music.current?.nameWithoutExtension, music.positionMs())
        }
        if (tickCount % 330 == 0) updateBattery()   // circa ogni 10 secondi
    }

    private fun updateBattery() {
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        state.update { it.copy(battery = pct, charging = charging) }
    }

    // ---------------- Campane e suoni ----------------

    fun ringBell(b: Bell) {
        if (!state.value.engineRunning) {
            notice { tr("Start the lesson to send sounds to the headphones.", "Započni sat da bi slao zvukove u slušalice.", "Avvia la lezione per mandare i suoni nelle cuffie.") }
            return
        }
        voice.playSound(Bells.get(b), settings.value.bellVolume)
    }

    fun testBeep() {
        if (!state.value.engineRunning) return
        voice.playSound(Bells.beep(), 0.8f)
    }

    // ---------------- Tasti dell'auricolare ----------------

    private fun updateButtons() {
        val want = settings.value.mediaButtons && state.value.sessionOn
        if (want && buttons == null) buttons = MediaButtons(app)
        buttons?.setActive(want)
        if (!want) { buttons?.release(); buttons = null }
    }

    fun mediaPlay() { if (!music.isPlaying) togglePlay() }
    fun mediaPause() { if (music.isPlaying) music.pause() }

    // ---------------- Musica ----------------

    private fun publishMusic() {
        state.update { it.copy(nowPlaying = music.current?.nameWithoutExtension, musicPlaying = music.isPlaying) }
        buttons?.update(music.isPlaying, music.current?.nameWithoutExtension, music.positionMs())
    }

    private fun needCable() = state.update {
        it.copy(status = { tr("Connect the USB-C cable: music only plays there", "Spoji USB-C kabel: glazba svira samo tamo", "Collega il cavo USB-C: la musica esce solo da lì") })
    }

    fun play(file: File, crossfade: Boolean = false) {
        val out = cableOutput() ?: run { needCable(); return }
        if (!state.value.sessionOn) startSession()
        try {
            pausedByCable = false
            val xf = if (crossfade && settings.value.crossfadeOn) settings.value.crossfadeSec else 0f
            music.play(file, out, xf)
        } catch (e: Exception) {
            state.update { it.copy(status = { tr("Cannot play ", "Ne mogu reproducirati ", "Impossibile riprodurre ") + file.name + ": " + e.message }) }
        }
    }

    fun togglePlay() {
        if (music.isPlaying) { music.pause(); return }
        val out = cableOutput() ?: run { needCable(); return }
        if (music.hasTrack()) music.resume(out)
        else queue().firstOrNull()?.let { play(it) }
    }

    fun stopMusic() = music.stop()

    fun next(crossfade: Boolean = false) {
        val q = queue()
        if (q.isEmpty()) return
        val i = q.indexOfFirst { it == music.current }
        play(q[(i + 1) % q.size], crossfade = crossfade || music.isPlaying)
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
        val base = name.trim().ifEmpty { tr("New lesson", "Novi sat", "Nuova lezione") }
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
        // aggiorna anche le scalette che la usano
        savePlans(plans.value.map { p -> p.copy(phases = p.phases.map { ph -> if (ph.playlist == old) ph.copy(playlist = target) else ph }) })
        return target
    }

    fun deletePlaylist(name: String) {
        savePlaylists(playlists.value.filterNot { it.name == name })
        if (state.value.activePlaylist == name) state.update { it.copy(activePlaylist = null) }
    }

    fun setPlaylistTracks(name: String, tracks: List<String>) {
        savePlaylists(playlists.value.map { if (it.name == name) it.copy(tracks = tracks) else it })
    }

    // ---------------- Scalette (lezione a fasi con timer) ----------------

    private fun savePlans(list: List<LessonPlan>) {
        plans.value = list
        try { planStore.save(list) } catch (_: Exception) {}
    }

    fun createPlan(name: String): String {
        val base = name.trim().ifEmpty { tr("New lesson plan", "Novi plan sata", "Nuova scaletta") }
        var n = base
        var i = 2
        while (plans.value.any { it.name.equals(n, ignoreCase = true) }) { n = "$base $i"; i++ }
        val default = listOf(
            Phase(tr("Warm-up", "Zagrijavanje", "Riscaldamento"), 10, null),
            Phase(tr("Practice", "Vježba", "Pratica"), 40, null),
            Phase("Savasana", 10, null),
        )
        savePlans(plans.value + LessonPlan(n, default))
        return n
    }

    fun savePlan(oldName: String, plan: LessonPlan): String {
        var name = plan.name.trim().ifEmpty { oldName }
        if (name != oldName && plans.value.any { it.name.equals(name, ignoreCase = true) && it.name != oldName }) name = oldName
        savePlans(plans.value.map { if (it.name == oldName) plan.copy(name = name) else it })
        return name
    }

    fun deletePlan(name: String) {
        if (planRun.value?.plan?.name == name) stopPlan(fade = false)
        savePlans(plans.value.filterNot { it.name == name })
    }

    fun startPlan(name: String) {
        val plan = plans.value.firstOrNull { it.name == name } ?: return
        if (plan.phases.isEmpty()) return
        if (!state.value.sessionOn) startSession()
        enterPhase(plan, 0, first = true)
    }

    fun nextPhase() { if (planRun.value != null) advancePlan() }

    fun stopPlan(fade: Boolean = true) {
        if (planRun.value == null) return
        planRun.value = null
        if (fade) music.stop()
    }

    private fun advancePlan() {
        val run = planRun.value ?: return
        val nextIndex = run.index + 1
        if (nextIndex >= run.plan.phases.size) {
            // fine della lezione: campana e musica che sfuma
            if (run.plan.bellOnChange) ringBellQuiet(Bell.of(run.plan.bell))
            planRun.value = null
            music.stop()
            notice { tr("Lesson plan finished", "Plan sata završen", "Scaletta conclusa") }
            return
        }
        enterPhase(run.plan, nextIndex, first = false)
    }

    private fun enterPhase(plan: LessonPlan, index: Int, first: Boolean) {
        val ph = plan.phases[index]
        val now = SystemClock.elapsedRealtime()
        planRun.value = PlanRun(plan, index, now, now + ph.minutes * 60_000L)
        if (plan.bellOnChange && !first) ringBellQuiet(Bell.of(plan.bell))
        if (ph.playlist != null) {
            loadPlaylist(ph.playlist)
            queue().firstOrNull()?.let { play(it, crossfade = music.isPlaying) }
        } else if (first && !music.isPlaying) {
            queue().firstOrNull()?.let { play(it) }
        }
    }

    private fun ringBellQuiet(b: Bell) {
        if (state.value.engineRunning) voice.playSound(Bells.get(b), settings.value.bellVolume)
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
                    var name = "track_${System.currentTimeMillis()}.mp3"
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

    private fun uniqueFile(name: String, dir: File = musicDir): File {
        var f = File(dir, name)
        var n = 2
        while (f.exists()) {
            f = File(dir, "${name.substringBeforeLast('.')}_$n.${name.substringAfterLast('.', "mp3")}")
            n++
        }
        return f
    }

    // ---------------- Registrazione della lezione ----------------

    fun reloadRecordings() {
        recordings.value = recordingsDir.listFiles()?.filter { it.isFile && it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** Dopo il consenso di Android: il servizio ottiene la cattura e chiama startRecorderWith. */
    fun startRecording(resultCode: Int, data: Intent) {
        if (!state.value.sessionOn) startSession()
        val i = Intent(app, MixerService::class.java)
            .setAction(MixerService.ACTION_RECORD)
            .putExtra(MixerService.EXTRA_CODE, resultCode)
            .putExtra(MixerService.EXTRA_DATA, data)
        ContextCompat.startForegroundService(app, i)
    }

    fun startRecorderWith(projection: MediaProjection) {
        // Android 14+: la cattura richiede un callback registrato; se il sistema la interrompe, chiudiamo il file
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { Recorder.stop() }
        }, Handler(Looper.getMainLooper()))
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())
        val out = uniqueFile("ViVi_$stamp.m4a", recordingsDir)
        Recorder.start(projection, out) { f, err -> scope.launch { onRecordingFinished(f, err) } }
        state.update { it.copy(recording = true, recordingStartedAt = System.currentTimeMillis()) }
    }

    fun stopRecording() { Recorder.stop() }

    fun onRecordingFinished(f: File?, err: String?) {
        state.update { it.copy(recording = false) }
        if (state.value.sessionOn) {
            app.startService(Intent(app, MixerService::class.java).setAction(MixerService.ACTION_RECORD_DONE))
        }
        reloadRecordings()
        notice(
            if (f != null) ({ tr("Recording saved: ", "Snimka spremljena: ", "Registrazione salvata: ") + f.name })
            else ({ tr("Recording not saved", "Snimka nije spremljena", "Registrazione non salvata") + (err?.let { " ($it)" } ?: "") })
        )
    }

    fun deleteRecording(f: File) { f.delete(); reloadRecordings() }

    // ---------------- Copia di sicurezza ----------------

    fun exportBackup(dest: Uri) {
        scope.launch {
            notice { tr("Creating backup…", "Izrada sigurnosne kopije…", "Creo la copia di sicurezza…") }
            val r = withContext(Dispatchers.IO) {
                runCatching { Backup.export(app, dest, musicDir, store.raw(), playlistsFile, plansFile) }
            }
            val t: UiText = r.fold(
                    { n -> { tr("Backup saved ($n tracks)", "Sigurnosna kopija spremljena ($n pjesama)", "Copia di sicurezza salvata ($n brani)") } },
                    { e -> { tr("Backup failed: ", "Sigurnosna kopija nije uspjela: ", "Copia di sicurezza non riuscita: ") + e.message } },
                )
            notice(t)
        }
    }

    fun importBackup(src: Uri) {
        scope.launch {
            notice { tr("Restoring…", "Vraćanje…", "Ripristino in corso…") }
            val r = withContext(Dispatchers.IO) {
                runCatching { Backup.import(app, src, musicDir, { store.restore(it) }, playlistsFile, plansFile) }
            }
            settings.value = store.load()
            L.apply(settings.value.language)
            playlists.value = playlistStore.load()
            plans.value = planStore.load()
            reloadLibrary()
            val t: UiText = r.fold(
                    { n -> { tr("Restored $n tracks, playlists and settings", "Vraćeno $n pjesama, popisa i postavki", "Ripristinati $n brani, playlist e impostazioni") } },
                    { e -> { tr("Restore failed: ", "Vraćanje nije uspjelo: ", "Ripristino non riuscito: ") + e.message } },
                )
            notice(t)
        }
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

    private fun gen(t: UiText) = state.update { it.copy(genStatus = t) }

    fun testServer() {
        scope.launch {
            gen { tr("Checking…", "Provjera…", "Controllo…") }
            val parts = withContext(Dispatchers.IO) {
                val parts = mutableListOf<UiText>()
                val token = settings.value.serverToken.trim()
                if (token.isEmpty()) parts += { tr("Hugging Face token missing (without it the free quota is almost zero)", "Nedostaje Hugging Face token (bez njega je besplatna kvota gotovo nula)", "Manca il token Hugging Face (senza token la quota gratuita è quasi nulla)") }
                else try {
                    val c = conn("https://huggingface.co/api/whoami-v2")
                    if (c.responseCode == 200) {
                        val who = JSONObject(c.inputStream.bufferedReader().readText()).optString("name")
                        parts += { tr("Token OK (account ", "Token OK (račun ", "Token OK (account ") + who + ")" }
                    } else {
                        val e = errorText(c)
                        parts += { tr("Invalid token", "Neispravan token", "Token non valido") + " ($e)" }
                    }
                } catch (e: Exception) { val m = e.message; parts += { tr("Internet unreachable: ", "Internet nedostupan: ", "Internet non raggiungibile: ") + m } }
                try {
                    val c = conn(spaceBase() + "/gradio_api/info")
                    if (c.responseCode == 200) parts += { tr("Generator online", "Generator dostupan", "Generatore online") }
                    else { val e = errorText(c); parts += { tr("Generator unavailable", "Generator nedostupan", "Generatore non disponibile") + " ($e)" } }
                } catch (e: Exception) { val m = e.message; parts += { tr("Generator unreachable: ", "Generator nedostupan: ", "Generatore non raggiungibile: ") + m } }
                parts
            }
            gen { parts.joinToString(" · ") { it() } }
        }
    }

    /** Genera un brano strumentale online e lo scarica in Libreria. */
    fun generate(prompt: String, seconds: Int) {
        if (state.value.generating) return
        scope.launch {
            state.update { it.copy(generating = true, genStatus = { tr("Sending the request…", "Slanje zahtjeva…", "Invio la richiesta…") }) }
            val msg: UiText = withContext(Dispatchers.IO) {
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

                    val c1 = conn("$base/gradio_api/call/__call__", "POST")
                    c1.doOutput = true
                    c1.setRequestProperty("Content-Type", "application/json")
                    c1.outputStream.use { it.write(JSONObject().put("data", data).toString().toByteArray()) }
                    if (c1.responseCode != 200) { val e = errorText(c1); return@withContext { tr("Generator error: ", "Greška generatora: ", "Errore dal generatore: ") + e } }
                    val eventId = JSONObject(c1.inputStream.bufferedReader().readText()).getString("event_id")

                    withContext(Dispatchers.Main) { gen { tr("Queued / generating… (usually under 2 minutes)", "U redu / generiranje… (obično manje od 2 minute)", "In coda / generazione in corso… (di solito meno di 2 minuti)") } }

                    val c2 = conn("$base/gradio_api/call/__call__/$eventId")
                    if (c2.responseCode != 200) { val e = errorText(c2); return@withContext { tr("Generator error: ", "Greška generatora: ", "Errore dal generatore: ") + e } }
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
                        return@withContext if (quota) ({ tr("Free GPU quota used up for today: try again tomorrow (or with a PRO account). ", "Besplatna GPU kvota za danas je potrošena: pokušaj sutra (ili s PRO računom). ", "Quota GPU gratuita esaurita per oggi: riprova domani (o con un account PRO). ") + f })
                        else ({ (tr("The generator returned an error. ", "Generator je vratio grešku. ", "Il generatore ha restituito un errore. ") + f.take(200)).trim() })
                    }

                    val out = JSONArray(result!!).get(0)
                    val fileObj = out as? JSONObject ?: return@withContext { tr("Unexpected reply from the generator", "Neočekivan odgovor generatora", "Risposta inattesa dal generatore") }
                    val url = fileObj.optString("url").ifEmpty { "$base/gradio_api/file=" + fileObj.optString("path") }
                    val ext = url.substringAfterLast('.', "wav").substringBefore('?').lowercase()
                        .takeIf { it in audioExt } ?: "wav"
                    withContext(Dispatchers.Main) { gen { tr("Downloading the track…", "Preuzimanje pjesme…", "Scarico il brano…") } }
                    val c3 = conn(url)
                    if (c3.responseCode != 200) { val e = errorText(c3); return@withContext { tr("Download failed: ", "Preuzimanje nije uspjelo: ", "Download fallito: ") + e } }
                    val slug = prompt.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifEmpty { "track" }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
                    val dest = uniqueFile("AI_${slug}_$stamp.$ext")
                    c3.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    val name = dest.name
                    return@withContext { tr("Done: $name is in the Library (also available offline)", "Gotovo: $name je u Knjižnici (dostupno i offline)", "Fatto: $name è in Libreria (disponibile anche offline)") }
                } catch (e: Exception) { val m = e.message; return@withContext { tr("Error: ", "Greška: ", "Errore: ") + m } }
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
            if (manual) updateState.value = UpdateState(message = { tr("Checking for updates…", "Provjera ažuriranja…", "Controllo aggiornamenti…") })
            try {
                val u = Updater.check()
                val v = Updater.currentVersion
                updateState.value = when {
                    u != null -> UpdateState(available = u)
                    manual -> UpdateState(message = { tr("You already have the latest version ($v).", "Već imaš najnoviju verziju ($v).", "Hai già l'ultima versione ($v).") })
                    else -> UpdateState()
                }
            } catch (e: Exception) {
                val m = e.message
                if (manual) updateState.value = UpdateState(message = { tr("Cannot check for updates: ", "Ne mogu provjeriti ažuriranja: ", "Impossibile controllare gli aggiornamenti: ") + m })
            }
        }
    }

    fun dismissUpdate() = updateState.update { it.copy(dismissed = true) }

    fun startUpdate() {
        val u = updateState.value.available ?: return
        if (updateState.value.downloading) return
        if (!Updater.canInstall(app)) {
            updateState.update { it.copy(message = { tr("Allow \"Install unknown apps\" for ViViApp, then come back and press Update again.", "Dopusti \"Instaliraj nepoznate aplikacije\" za ViViApp, zatim se vrati i ponovno pritisni Ažuriraj.", "Consenti \"Installa app sconosciute\" per ViViApp, poi torna qui e premi di nuovo Aggiorna.") }) }
            Updater.openInstallPermission(app)
            return
        }
        scope.launch {
            val v = u.version
            updateState.update { it.copy(downloading = true, progress = 0f, message = { tr("Downloading version $v…", "Preuzimanje verzije $v…", "Scarico la versione $v…") }) }
            try {
                val apk = Updater.download(app, u.url) { p -> updateState.update { it.copy(progress = p) } }
                updateState.update { it.copy(downloading = false, progress = 1f, message = { tr("Confirm the installation on the Android screen.", "Potvrdi instalaciju na Android zaslonu.", "Conferma l'installazione nella schermata di Android.") }) }
                Updater.install(app, apk)
            } catch (e: Exception) {
                val m = e.message
                updateState.update { it.copy(downloading = false, message = { tr("Update failed: ", "Ažuriranje nije uspjelo: ", "Aggiornamento non riuscito: ") + m }) }
            }
        }
    }
}
