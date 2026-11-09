package it.migliorati.viviapp

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Process
import java.io.File

/**
 * Registra la lezione così come la sentono le clienti (voce + musica + campane),
 * catturando l'audio prodotto dall'app stessa, e lo salva in AAC (.m4a, circa 1 MB al minuto).
 * Android chiede il permesso ogni volta che si avvia una registrazione.
 */
object Recorder {
    @Volatile var recording = false; private set
    @Volatile var startedAt = 0L; private set
    var file: File? = null; private set
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(projection: MediaProjection, out: File, onDone: (File?, String?) -> Unit) {
        if (recording) return
        recording = true
        startedAt = System.currentTimeMillis()
        file = out
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var rec: AudioRecord? = null
            var codec: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var error: String? = null
            try {
                val sr = 48000
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUid(Process.myUid())
                    .build()
                val fmt = AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
                val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                val r = AudioRecord.Builder()
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(maxOf(minBuf, 32768))
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
                rec = r

                val mf = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, 2)
                mf.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                mf.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                mf.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                codec = c
                c.configure(mf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                c.start()
                val mx = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = mx

                r.startRecording()
                val pcm = ByteArray(8192)
                val info = MediaCodec.BufferInfo()
                var track = -1
                var muxing = false
                var totalFrames = 0L
                var eosSent = false

                fun drain(waitEos: Boolean) {
                    while (true) {
                        val idx = c.dequeueOutputBuffer(info, if (waitEos) 10_000L else 0L)
                        when {
                            idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!waitEos) return
                            idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                track = mx.addTrack(c.outputFormat)
                                mx.start()
                                muxing = true
                            }
                            idx >= 0 -> {
                                val buf = c.getOutputBuffer(idx)!!
                                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                                if (info.size > 0 && muxing) {
                                    buf.position(info.offset); buf.limit(info.offset + info.size)
                                    mx.writeSampleData(track, buf, info)
                                }
                                c.releaseOutputBuffer(idx, false)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                            }
                        }
                    }
                }

                while (!eosSent) {
                    val stop = !recording
                    val n = if (stop) 0 else r.read(pcm, 0, pcm.size)
                    if (n < 0) throw IllegalStateException("capture error $n")
                    val inIdx = c.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = c.getInputBuffer(inIdx)!!
                        ib.clear()
                        val len = minOf(n, ib.remaining())
                        ib.put(pcm, 0, len)
                        val pts = totalFrames * 1_000_000L / sr
                        totalFrames += len / 4
                        if (stop) {
                            c.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSent = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, len, pts, 0)
                        }
                    }
                    drain(waitEos = false)
                }
                drain(waitEos = true)
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                try { rec?.stop() } catch (_: Exception) {}
                rec?.release()
                try { codec?.stop() } catch (_: Exception) {}
                codec?.release()
                try { muxer?.stop() } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
                try { projection.stop() } catch (_: Exception) {}
                recording = false
                val f = out.takeIf { it.exists() && it.length() > 1024 }
                if (f == null) out.delete()
                onDone(f, error)
            }
        }, "ViViAppRecorder")
        thread?.start()
    }

    fun stop() { recording = false }
}
