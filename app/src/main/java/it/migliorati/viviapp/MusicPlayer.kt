package it.migliorati.viviapp

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Canale musica.
 *
 * - Uscita principale (cavo → trasmettitore → clienti): volume utente × fade × ducking.
 * - Ascolto insegnante (auricolare Bluetooth): una seconda copia dello stesso brano,
 *   sincronizzata, con volume proprio. Segue i fade ma non il ducking, così lei sente
 *   sempre bene la musica anche mentre parla.
 */
class MusicPlayer(private val scope: CoroutineScope, private val settings: () -> Settings) {

    private var mp: MediaPlayer? = null
    private var mon: MediaPlayer? = null
    private var monitorDevice: AudioDeviceInfo? = null

    var current: File? = null; private set
    var isPlaying = false; private set
    var onCompleted: (() -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private var fadeGain = 1f
    private var duck = 1f
    private var fadeJob: Job? = null
    private var watchJob: Job? = null
    private var endFadeStarted = false

    fun setDuck(g: Float) {
        if (abs(g - duck) > 0.002f) { duck = g; applyVolume() }
    }

    fun applyVolume() {
        val s = settings()
        val v = s.musicVolume.let { it * it } * fadeGain * duck
        try { mp?.setVolume(v, v) } catch (_: Exception) {}
        val m = s.monitorVolume.let { it * it } * fadeGain
        try { mon?.setVolume(m, m) } catch (_: Exception) {}
    }

    // ---------------- Ascolto insegnante ----------------

    /** Imposta (o toglie, con null) l'uscita Bluetooth dell'auricolare dell'insegnante. */
    fun setMonitorDevice(device: AudioDeviceInfo?) {
        monitorDevice = device
        refreshMonitor()
    }

    /** Allinea il canale dell'insegnante allo stato attuale (acceso/spento, brano, posizione). */
    fun refreshMonitor() {
        val main = mp
        val dev = monitorDevice
        val file = current
        if (main == null || dev == null || file == null || !settings().monitorOn) {
            releaseMonitor()
            return
        }
        try {
            val m = mon ?: buildMonitor(file, dev).also { mon = it }
            m.isLooping = main.isLooping
            m.seekTo(main.currentPosition)
            if (isPlaying) { if (!m.isPlaying) m.start() } else if (m.isPlaying) m.pause()
            applyVolume()
        } catch (_: Exception) {
            releaseMonitor()
        }
    }

    private fun buildMonitor(file: File, dev: AudioDeviceInfo): MediaPlayer {
        val p = MediaPlayer()
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        p.setDataSource(file.absolutePath)
        p.setPreferredDevice(dev)
        p.prepare()
        return p
    }

    private fun releaseMonitor() {
        try { mon?.stop() } catch (_: Exception) {}
        mon?.release()
        mon = null
    }

    // ---------------- Trasporto ----------------

    fun play(file: File, output: AudioDeviceInfo) {
        release()
        val s = settings()
        val p = MediaPlayer()
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        p.setDataSource(file.absolutePath)
        p.isLooping = s.loopTrack
        p.setPreferredDevice(output)
        p.setOnCompletionListener {
            isPlaying = false
            try { mon?.pause() } catch (_: Exception) {}
            onChanged?.invoke()
            onCompleted?.invoke()
        }
        p.prepare()
        mp = p
        current = file
        endFadeStarted = false
        fadeGain = if (s.fadeOn) 0f else 1f
        applyVolume()
        p.start()
        isPlaying = true
        refreshMonitor()
        if (s.fadeOn) fadeTo(1f, s.fadeInSec)
        startWatch()
        onChanged?.invoke()
    }

    fun resume(output: AudioDeviceInfo) {
        val p = mp ?: return
        val s = settings()
        p.setPreferredDevice(output)
        fadeGain = if (s.fadeOn) 0f else 1f
        applyVolume()
        p.start()
        isPlaying = true
        refreshMonitor()
        if (s.fadeOn) fadeTo(1f, s.fadeInSec)
        onChanged?.invoke()
    }

    /** Pausa con fade (se attivo). */
    fun pause() {
        val p = mp ?: return
        if (!isPlaying) return
        isPlaying = false
        onChanged?.invoke()
        val s = settings()
        val doPause = {
            try { p.pause() } catch (_: Exception) {}
            try { mon?.pause() } catch (_: Exception) {}
        }
        if (s.fadeOn) fadeTo(0f, s.fadeOutSec) { doPause() } else doPause()
    }

    /** Pausa immediata, senza fade (es. cavo staccato: niente musica dagli altoparlanti). */
    fun pauseNow() {
        fadeJob?.cancel()
        try { if (mp?.isPlaying == true) mp?.pause() } catch (_: Exception) {}
        try { if (mon?.isPlaying == true) mon?.pause() } catch (_: Exception) {}
        isPlaying = false
        onChanged?.invoke()
    }

    fun stop() {
        val s = settings()
        if (isPlaying && s.fadeOn) {
            isPlaying = false
            onChanged?.invoke()
            fadeTo(0f, s.fadeOutSec) { release(); onChanged?.invoke() }
        } else {
            release()
            onChanged?.invoke()
        }
    }

    fun setLooping(loop: Boolean) {
        try { mp?.isLooping = loop } catch (_: Exception) {}
        try { mon?.isLooping = loop } catch (_: Exception) {}
    }

    fun hasTrack() = mp != null

    fun release() {
        fadeJob?.cancel(); watchJob?.cancel()
        releaseMonitor()
        try { mp?.stop() } catch (_: Exception) {}
        mp?.release()
        mp = null
        current = null
        isPlaying = false
    }

    private fun fadeTo(target: Float, seconds: Float, then: (() -> Unit)? = null) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = max(1, (seconds * 1000 / 30).toInt())
            val start = fadeGain
            for (i in 1..steps) {
                delay(30)
                fadeGain = start + (target - start) * i / steps
                applyVolume()
            }
            then?.invoke()
        }
    }

    /** Fade-out automatico prima della fine e riallineamento del canale insegnante. */
    private fun startWatch() {
        watchJob?.cancel()
        watchJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(200)
                val p = mp ?: break
                val s = settings()
                try {
                    if (isPlaying && s.fadeOn && !p.isLooping && !endFadeStarted) {
                        val left = p.duration - p.currentPosition
                        if (p.duration > 0 && left <= s.fadeOutSec * 1000) {
                            endFadeStarted = true
                            fadeTo(0f, left / 1000f)
                        }
                    }
                    // Ogni secondo: se le due copie si sono allontanate di oltre 150 ms, riallinea
                    tick++
                    val m = mon
                    if (tick % 5 == 0 && m != null && isPlaying && m.isPlaying) {
                        if (abs(m.currentPosition - p.currentPosition) > 150) m.seekTo(p.currentPosition)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
