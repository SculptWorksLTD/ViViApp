package it.migliorati.viviapp

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

/**
 * Percorso della voce: microfono -> catena "voce pulita" (VoiceProcessor) -> uscita.
 *
 * Buffer: lettura a blocchi di almeno 5 ms, scrittura bloccante (niente campioni scartati =
 * niente click) e un buffer d'uscita che parte piccolo e cresce da solo se l'audio si interrompe.
 * Sull'emulatore i blocchi sono più grandi, perché l'audio virtuale arriva a scatti.
 */
class VoiceEngine(private val settings: () -> Settings) {

    @Volatile var envelopeDb = -100f; private set   // livello voce (per il ducking)
    @Volatile var peak = 0f; private set            // picco in uscita (per il VU meter)
    @Volatile var bufferMs = 0; private set         // latenza aggiunta dall'app
    @Volatile var running = false; private set
    var onError: ((String) -> Unit)? = null

    private var thread: Thread? = null

    // Suoni (campane, bip di prova) da mescolare nel canale verso le clienti
    private class Voice(val buf: FloatArray, val gain: Float) { var pos = 0 }
    private val pending = ConcurrentLinkedQueue<Voice>()

    /** Manda un suono nel mix (funziona mentre la lezione è in onda). */
    fun playSound(buf: FloatArray, gain: Float) { pending.add(Voice(buf, gain)) }

    fun start(input: AudioDeviceInfo?, output: AudioDeviceInfo, bluetooth: Boolean, burstHint: Int) {
        if (running) return
        running = true
        thread = Thread({ loop(input, output, bluetooth, burstHint) }, "ViViAppVoice").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(1500)
        thread = null
        envelopeDb = -100f
        peak = 0f
        bufferMs = 0
    }

    @SuppressLint("MissingPermission")
    private fun loop(input: AudioDeviceInfo?, output: AudioDeviceInfo, bluetooth: Boolean, burstHint: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val sr = 48000
        val emulator = isEmulator()
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            // Blocchi: almeno 5 ms sul tablet, 20 ms sull'emulatore
            val chunk = if (emulator) 960 else max(burstHint.coerceIn(64, 1024), 240)

            // --- Ingresso ---
            val source = when {
                bluetooth -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
                emulator -> MediaRecorder.AudioSource.MIC
                else -> MediaRecorder.AudioSource.VOICE_PERFORMANCE
            }
            val inFmt = AudioFormat.Builder()
                .setSampleRate(sr)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minIn = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            rec = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(inFmt)
                .setBufferSizeInBytes(max(minIn, chunk * 4 * 8))
                .build()
            if (input != null) rec.setPreferredDevice(input)

            // --- Uscita ---
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val outFmt = AudioFormat.Builder()
                .setSampleRate(sr)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val minOut = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(outFmt)
                .setBufferSizeInBytes(max(minOut, chunk * 2 * 4 * 12))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(
                    if (emulator) AudioTrack.PERFORMANCE_MODE_NONE else AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
                )
                .build()
            track.setPreferredDevice(output)

            if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("microfono non disponibile")
            if (track.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("uscita audio non disponibile")

            val capacity = track.bufferCapacityInFrames
            var target = (chunk * (if (emulator) 4 else 2)).coerceAtMost(capacity)
            track.setBufferSizeInFrames(target)
            var lastUnderruns = track.underrunCount

            val processor = VoiceProcessor(sr)
            val active = ArrayList<Voice>()
            pending.clear()
            val inBuf = FloatArray(chunk)
            val outBuf = FloatArray(chunk * 2)

            // Precarica un po' di silenzio, così il primo blocco non va subito "a secco"
            track.write(FloatArray(target * 2), 0, target * 2, AudioTrack.WRITE_BLOCKING)
            rec.startRecording()
            track.play()

            while (running) {
                // Legge un blocco intero (anche se il driver lo consegna a pezzi)
                var got = 0
                while (got < chunk && running) {
                    val r = rec.read(inBuf, got, chunk - got, AudioRecord.READ_BLOCKING)
                    if (r < 0) throw IllegalStateException("errore lettura microfono ($r)")
                    got += r
                }
                if (!running) break

                val s = settings()
                val pk = processor.process(inBuf, chunk, s.voiceClean, s.voiceGainDb, s.voiceMuted)
                envelopeDb = processor.levelDb
                peak = pk
                // Campane e suoni sopra la voce (dopo la catena voce, quindi non compressi)
                while (true) { active.add(pending.poll() ?: break) }
                if (active.isNotEmpty()) {
                    val iter = active.iterator()
                    while (iter.hasNext()) {
                        val v = iter.next()
                        val n = minOf(chunk, v.buf.size - v.pos)
                        for (i in 0 until n) inBuf[i] += v.buf[v.pos + i] * v.gain
                        v.pos += n
                        if (v.pos >= v.buf.size) iter.remove()
                    }
                    for (i in 0 until chunk) inBuf[i] = inBuf[i].coerceIn(-0.95f, 0.95f)
                }
                for (i in 0 until chunk) {
                    outBuf[2 * i] = inBuf[i]
                    outBuf[2 * i + 1] = inBuf[i]
                }
                track.write(outBuf, 0, chunk * 2, AudioTrack.WRITE_BLOCKING)

                // Se l'uscita è rimasta a secco, allarga un po' il buffer (una volta per evento)
                val ur = track.underrunCount
                if (ur > lastUnderruns) {
                    lastUnderruns = ur
                    if (target + chunk <= capacity) {
                        target += chunk
                        track.setBufferSizeInFrames(target)
                    }
                }
                val nrMs = if (s.voiceClean) 11 else 0
                bufferMs = ((target + chunk) * 1000L / sr).toInt() + nrMs
            }
        } catch (e: Exception) {
            if (running) onError?.invoke(e.message ?: e.toString())
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            rec?.release()
            try { track?.stop() } catch (_: Exception) {}
            track?.release()
            running = false
        }
    }

    private fun isEmulator(): Boolean =
        Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
            Build.FINGERPRINT.contains("generic") || Build.PRODUCT.contains("sdk")
}
