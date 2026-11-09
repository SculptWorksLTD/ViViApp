package it.migliorati.viviapp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Catena voce automatica ("voce pulita"), senza parametri da regolare:
 *
 *   filtro antirombo 90 Hz → riduzione rumore spettrale → EQ (meno rimbombo, più presenza)
 *   → compressore + espansore + livellatore automatico → volume utente → limitatore pulito
 *
 * Il livellatore porta da solo la voce a un livello costante: l'utente regola solo il volume.
 */
class VoiceProcessor(private val sr: Int) {

    private val highPass = Biquad.highPass(sr, 90.0, 0.707)
    private val mud = Biquad.peaking(sr, 250.0, 1.0, -2.5)
    private val presence = Biquad.peaking(sr, 3200.0, 0.9, 3.0)
    private val denoiser = SpectralDenoiser()

    // Dinamica
    private val atk = 1f - exp(-1f / (sr * 0.005f))
    private val rel = 1f - exp(-1f / (sr * 0.120f))
    private val speechCoef = 1f - exp(-1f / (sr * 1.0f))
    private val makeupCoef = 1f - exp(-1f / (sr * 0.4f))
    private val floorDown = 1f - exp(-1f / (sr * 0.05f))
    private val floorUpPerSample = 3f / sr            // il rumore di fondo stimato sale di 3 dB/s
    private val limRel = 1f - exp(-1f / (sr * 0.05f))

    private var det = 1e-6f
    private var floorDb = -60f
    private var speechDb = -30f
    private var makeupDb = 0f
    private var lim = 1f

    /** Livello della voce (dB) prima del volume utente: serve al ducking. */
    var levelDb = -100f; private set

    fun reset() {
        highPass.reset(); mud.reset(); presence.reset(); denoiser.reset()
        det = 1e-6f; floorDb = -60f; speechDb = -30f; makeupDb = 0f; lim = 1f
    }

    /** Elabora n campioni in place. Restituisce il picco in uscita. */
    fun process(buf: FloatArray, n: Int, clean: Boolean, userGainDb: Float, muted: Boolean): Float {
        val user = if (muted) 0f else dbToLin(userGainDb)
        val ceiling = 0.89f   // -1 dBFS
        var peak = 0f
        for (i in 0 until n) {
            var x = buf[i]
            if (clean) {
                x = highPass.process(x)
                x = denoiser.process(x)
                x = mud.process(x)
                x = presence.process(x)
            }

            // Rilevatore di livello
            val x2 = x * x
            det += (x2 - det) * (if (x2 > det) atk else rel)
            val lvl = 10f * log10(det + 1e-12f)
            levelDb = lvl

            var gainDb = 0f
            if (clean) {
                // Rumore di fondo (minimo che sale lentamente)
                if (lvl < floorDb) floorDb += (lvl - floorDb) * floorDown else floorDb += floorUpPerSample
                // Livello medio del parlato, aggiornato solo quando sta parlando
                if (lvl > floorDb + 10f && lvl > -60f) speechDb += (lvl - speechDb) * speechCoef
                // Compressore 3:1 morbido
                val gr = compGain(lvl)
                // Espansore: abbassa ancora un po' quello che resta del fruscio tra le parole
                val below = lvl - (floorDb + 8f)
                val ex = if (below < 0f) max(-12f, below) else 0f
                // Livellatore: porta il parlato compresso a circa -20 dBFS
                val target = (-20f - (speechDb + compGain(speechDb))).coerceIn(-6f, 18f)
                makeupDb += (target - makeupDb) * makeupCoef
                gainDb = gr + ex + makeupDb
            }

            var y = x * dbToLin(gainDb) * user

            // Limitatore di picco: niente distorsione
            val a = abs(y)
            val need = if (a > ceiling) ceiling / a else 1f
            if (need < lim) lim = need else lim += (1f - lim) * limRel
            y *= lim

            buf[i] = y
            val ay = abs(y)
            if (ay > peak) peak = ay
        }
        return peak
    }

    private fun compGain(lvl: Float): Float {
        val t = -28f; val ratio = 3f; val knee = 8f
        val over = lvl - t
        val slope = 1f / ratio - 1f
        return when {
            over <= -knee / 2 -> 0f
            over >= knee / 2 -> over * slope
            else -> slope * (over + knee / 2) * (over + knee / 2) / (2 * knee)
        }
    }

    private fun dbToLin(db: Float): Float = 10f.pow(db / 20f)
}

/** Filtro biquad (formule RBJ), forma diretta II trasposta. */
class Biquad(
    private val b0: Float, private val b1: Float, private val b2: Float,
    private val a1: Float, private val a2: Float,
) {
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() { z1 = 0f; z2 = 0f }

    companion object {
        fun highPass(sr: Int, f: Double, q: Double): Biquad {
            val w0 = 2 * PI * f / sr
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val a0 = 1 + alpha
            return Biquad(
                ((1 + c) / 2 / a0).toFloat(), (-(1 + c) / a0).toFloat(), ((1 + c) / 2 / a0).toFloat(),
                (-2 * c / a0).toFloat(), ((1 - alpha) / a0).toFloat(),
            )
        }

        fun peaking(sr: Int, f: Double, q: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * f / sr
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val a0 = 1 + alpha / a
            return Biquad(
                ((1 + alpha * a) / a0).toFloat(), (-2 * c / a0).toFloat(), ((1 - alpha * a) / a0).toFloat(),
                (-2 * c / a0).toFloat(), ((1 - alpha / a) / a0).toFloat(),
            )
        }
    }
}

/**
 * Riduzione del rumore spettrale (STFT 512 punti, sovrapposizione 50%).
 * Stima da sola il rumore di fondo (minimo che risale lentamente) e lo sottrae.
 * Latenza fissa: 512 campioni (circa 11 ms a 48 kHz).
 */
class SpectralDenoiser {
    private val n = 512
    private val hop = 256
    private val bins = n / 2 + 1
    private val window = FloatArray(n) { i -> sqrt(0.5 - 0.5 * cos(2 * PI * i / n)).toFloat() }

    private val inBuf = FloatArray(n)
    private var pos = n - hop
    private val out = FloatArray(hop)
    private var outPos = 0
    private val ola = FloatArray(hop)

    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val ps = FloatArray(bins)
    private val noise = FloatArray(bins)
    private val gRaw = FloatArray(bins)
    private val g = FloatArray(bins) { 1f }
    private var initialized = false

    private val fft = Fft(n)

    fun reset() {
        inBuf.fill(0f); pos = n - hop; out.fill(0f); outPos = 0; ola.fill(0f)
        g.fill(1f); initialized = false
    }

    fun process(x: Float): Float {
        inBuf[pos++] = x
        val y = out[outPos++]
        if (pos == n) processFrame()
        return y
    }

    private fun processFrame() {
        for (i in 0 until n) { re[i] = inBuf[i] * window[i]; im[i] = 0f }
        fft.transform(re, im, inverse = false)

        if (!initialized) {
            for (k in 0 until bins) { val p = re[k] * re[k] + im[k] * im[k]; ps[k] = p; noise[k] = p }
            initialized = true
        }
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            ps[k] = 0.6f * ps[k] + 0.4f * p
            if (ps[k] < noise[k]) noise[k] = 0.7f * noise[k] + 0.3f * ps[k] else noise[k] *= 1.003f
            val snr = ps[k] / (2.5f * noise[k] + 1e-12f)
            gRaw[k] = if (snr > 0f) max(0.1f, 1f - 2f / snr) else 0.1f
        }
        for (k in 0 until bins) {
            val a = gRaw[max(k - 1, 0)]
            val b = gRaw[k]
            val c = gRaw[min(k + 1, bins - 1)]
            val gs = (a + 2 * b + c) / 4f
            g[k] = if (gs > g[k]) gs else 0.7f * g[k] + 0.3f * gs
        }
        for (k in 0 until bins) {
            re[k] *= g[k]; im[k] *= g[k]
            if (k in 1 until n / 2) { re[n - k] *= g[k]; im[n - k] *= g[k] }
        }
        fft.transform(re, im, inverse = true)

        for (i in 0 until hop) out[i] = ola[i] + re[i] * window[i]
        for (i in 0 until hop) ola[i] = re[hop + i] * window[hop + i]
        System.arraycopy(inBuf, hop, inBuf, 0, n - hop)
        for (i in n - hop until n) inBuf[i] = 0f
        pos = n - hop
        outPos = 0
    }
}

/** FFT complessa radix-2 in place, con tabelle precalcolate. */
class Fft(private val n: Int) {
    private val rev = IntArray(n)
    private val cosT = FloatArray(n / 2)
    private val sinT = FloatArray(n / 2)

    init {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            rev[i] = j
        }
        for (i in 0 until n / 2) {
            cosT[i] = cos(2 * PI * i / n).toFloat()
            sinT[i] = sin(2 * PI * i / n).toFloat()
        }
    }

    fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        for (i in 1 until n) {
            val j = rev[i]
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        val sign = if (inverse) 1f else -1f
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]
                    val wi = sign * sinT[k * step]
                    val a = i + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            val inv = 1f / n
            for (i in 0 until n) { re[i] *= inv; im[i] *= inv }
        }
    }
}
