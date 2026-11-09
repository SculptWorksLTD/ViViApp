package it.migliorati.viviapp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** I suoni che si possono mandare nelle cuffie delle clienti. */
enum class Bell(val id: String) {
    BOWL("bowl"), GONG("gong"), TINGSHA("tingsha"), CHIMES("chimes");

    val label: String get() = when (this) {
        BOWL -> tr("Singing bowl", "Tibetanska zdjela", "Campana tibetana")
        GONG -> tr("Gong", "Gong", "Gong")
        TINGSHA -> tr("Tingsha", "Tingsha", "Tingsha")
        CHIMES -> tr("Chimes", "Zvončići", "Carillon")
    }

    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: BOWL }
}

/**
 * Suoni sintetizzati dall'app (sintesi additiva): niente file esterni, niente diritti.
 * Mono, 48 kHz, picco circa -2 dBFS.
 */
object Bells {
    private const val SR = 48000
    private val cache = HashMap<Bell, FloatArray>()

    @Synchronized
    fun get(b: Bell): FloatArray = cache.getOrPut(b) {
        when (b) {
            Bell.BOWL -> bowl()
            Bell.GONG -> gong()
            Bell.TINGSHA -> tingsha()
            Bell.CHIMES -> chimes()
        }
    }

    /** Prepara tutti i suoni in anticipo (in un thread di sfondo). */
    fun preload() { Bell.entries.forEach { get(it) } }

    /** Bip di prova: 1 kHz, mezzo secondo, con attacco e rilascio morbidi. */
    fun beep(): FloatArray {
        val n = SR / 2
        return FloatArray(n) { i ->
            val env = min(1f, min(i / 480f, (n - i) / 480f))
            (0.5 * sin(2 * PI * 1000 * i / SR)).toFloat() * env
        }
    }

    private data class Partial(val ratio: Double, val amp: Double, val decay: Double, val beat: Double = 0.0)

    private fun additive(seconds: Double, f0: Double, partials: List<Partial>, attack: Double, into: FloatArray? = null, offset: Int = 0): FloatArray {
        val n = (seconds * SR).toInt()
        val out = into ?: FloatArray(n)
        for (p in partials) {
            val f = f0 * p.ratio
            if (f > SR / 2.2) continue
            val w = 2 * PI * f / SR
            val wb = 2 * PI * (f + p.beat) / SR
            for (i in 0 until n) {
                val idx = offset + i
                if (idx >= out.size) break
                val t = i.toDouble() / SR
                val env = p.amp * exp(-t / p.decay) * min(1.0, t / attack)
                val s = if (p.beat != 0.0) 0.5 * (sin(w * i) + sin(wb * i)) else sin(w * i)
                out[idx] += (env * s).toFloat()
            }
        }
        return out
    }

    private fun normalize(x: FloatArray, peak: Float = 0.8f): FloatArray {
        var m = 0f
        for (v in x) m = max(m, abs(v))
        if (m > 0f) { val g = peak / m; for (i in x.indices) x[i] *= g }
        // piccola dissolvenza finale per non avere un "click"
        val tail = min(x.size, SR / 10)
        for (i in 0 until tail) x[x.size - 1 - i] *= i.toFloat() / tail
        return x
    }

    private fun bowl(): FloatArray = normalize(
        additive(
            9.0, 196.0,
            listOf(
                Partial(1.0, 1.0, 4.5, 0.6),
                Partial(2.71, 0.55, 3.0, 1.1),
                Partial(5.15, 0.25, 1.8, 1.7),
                Partial(8.17, 0.12, 1.0),
                Partial(11.6, 0.05, 0.6),
            ),
            attack = 0.004,
        )
    )

    private fun gong(): FloatArray {
        val out = additive(
            8.0, 82.0,
            listOf(
                Partial(1.0, 1.0, 3.5, 0.3),
                Partial(1.52, 0.7, 3.0, 0.5),
                Partial(2.03, 0.55, 2.6),
                Partial(2.71, 0.45, 2.2, 0.8),
                Partial(3.42, 0.35, 1.8),
                Partial(4.21, 0.25, 1.4),
                Partial(5.37, 0.15, 1.0),
            ),
            attack = 0.25,
        )
        // fruscio metallico che cresce e svanisce
        val rnd = Random(7)
        var lp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / SR
            lp += (rnd.nextFloat() * 2 - 1 - lp) * 0.08f
            out[i] += lp * 0.25f * min(1f, t / 0.6f) * exp(-t / 1.8f)
        }
        return normalize(out)
    }

    private fun tingsha(): FloatArray {
        val out = FloatArray((5.0 * SR).toInt())
        // due piattini che si toccano a poca distanza
        for ((k, f0) in listOf(2490.0, 2532.0).withIndex()) {
            additive(5.0, f0, listOf(Partial(1.0, 1.0, 1.8, 3.0), Partial(2.4, 0.3, 0.7)), 0.002, out, k * (SR / 40))
        }
        return normalize(out, 0.7f)
    }

    private fun chimes(): FloatArray {
        val out = FloatArray((4.5 * SR).toInt())
        val notes = listOf(1046.5, 1318.5, 1568.0, 1760.0, 2093.0)  // pentatonica
        notes.forEachIndexed { k, f0 ->
            additive(3.0, f0, listOf(Partial(1.0, 1.0, 1.2), Partial(2.76, 0.35, 0.5), Partial(5.4, 0.12, 0.25)), 0.002, out, k * (SR * 32 / 100))
        }
        return normalize(out, 0.7f)
    }
}
