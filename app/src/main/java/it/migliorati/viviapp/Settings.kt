package it.migliorati.viviapp

import android.content.Context
import org.json.JSONObject
import kotlin.math.pow

/** Tutte le impostazioni regolabili dall'app. Vengono salvate automaticamente. */
data class Settings(
    // Voce
    val voiceGainDb: Float = 0f,
    val voiceClean: Boolean = true,
    val voiceMuted: Boolean = false,
    val gateOn: Boolean = false,
    val gateThresholdDb: Float = -50f,
    // Musica
    val musicVolume: Float = 0.6f,
    val loopTrack: Boolean = true,
    val autoNext: Boolean = false,
    // Musica anche nell'auricolare Bluetooth dell'insegnante
    val monitorOn: Boolean = true,
    val monitorVolume: Float = 0.5f,
    // Ducking (musica che si abbassa quando lei parla)
    val duckOn: Boolean = true,
    val duckDepthDb: Float = -12f,
    val duckThresholdDb: Float = -40f,
    val duckReleaseMs: Float = 1200f,
    // Fade
    val fadeOn: Boolean = true,
    val fadeInSec: Float = 3f,
    val fadeOutSec: Float = 4f,
    // Comportamento
    val autoResume: Boolean = true,
    val commMode: Boolean = true,
    // Prova senza hardware (emulatore): usa altoparlante e microfono del dispositivo
    val testMode: Boolean = false,
    // Generatore AI
    val serverUrl: String = "",
    val serverToken: String = "",
)

fun dbToLin(db: Float): Float = 10f.pow(db / 20f)

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("viviapp", Context.MODE_PRIVATE)

    fun load(): Settings {
        val raw = prefs.getString("settings", null) ?: return Settings()
        return try {
            val j = JSONObject(raw)
            val d = Settings()
            fun f(k: String, def: Float) = j.optDouble(k, def.toDouble()).toFloat()
            fun b(k: String, def: Boolean) = j.optBoolean(k, def)
            fun s(k: String, def: String) = j.optString(k, def)
            Settings(
                voiceGainDb = f("userVoiceDb", d.voiceGainDb),
                voiceClean = b("voiceClean", d.voiceClean),
                voiceMuted = b("voiceMuted", d.voiceMuted),
                gateOn = b("gateOn", d.gateOn),
                gateThresholdDb = f("gateThresholdDb", d.gateThresholdDb),
                musicVolume = f("musicVolume", d.musicVolume),
                loopTrack = b("loopTrack", d.loopTrack),
                autoNext = b("autoNext", d.autoNext),
                monitorOn = b("monitorOn", d.monitorOn),
                monitorVolume = f("monitorVolume", d.monitorVolume),
                duckOn = b("duckOn", d.duckOn),
                duckDepthDb = f("duckDepthDb", d.duckDepthDb),
                duckThresholdDb = f("duckThresholdDb", d.duckThresholdDb),
                duckReleaseMs = f("duckReleaseMs", d.duckReleaseMs),
                fadeOn = b("fadeOn", d.fadeOn),
                fadeInSec = f("fadeInSec", d.fadeInSec),
                fadeOutSec = f("fadeOutSec", d.fadeOutSec),
                autoResume = b("autoResume", d.autoResume),
                commMode = b("commMode", d.commMode),
                testMode = b("testMode", d.testMode),
                serverUrl = s("serverUrl", d.serverUrl),
                serverToken = s("serverToken", d.serverToken),
            )
        } catch (e: Exception) {
            Settings()
        }
    }

    fun save(s: Settings) {
        val j = JSONObject()
            .put("userVoiceDb", s.voiceGainDb.toDouble())
            .put("voiceClean", s.voiceClean)
            .put("voiceMuted", s.voiceMuted)
            .put("gateOn", s.gateOn)
            .put("gateThresholdDb", s.gateThresholdDb.toDouble())
            .put("musicVolume", s.musicVolume.toDouble())
            .put("loopTrack", s.loopTrack)
            .put("autoNext", s.autoNext)
            .put("monitorOn", s.monitorOn)
            .put("monitorVolume", s.monitorVolume.toDouble())
            .put("duckOn", s.duckOn)
            .put("duckDepthDb", s.duckDepthDb.toDouble())
            .put("duckThresholdDb", s.duckThresholdDb.toDouble())
            .put("duckReleaseMs", s.duckReleaseMs.toDouble())
            .put("fadeOn", s.fadeOn)
            .put("fadeInSec", s.fadeInSec.toDouble())
            .put("fadeOutSec", s.fadeOutSec.toDouble())
            .put("autoResume", s.autoResume)
            .put("commMode", s.commMode)
            .put("testMode", s.testMode)
            .put("serverUrl", s.serverUrl)
            .put("serverToken", s.serverToken)
        prefs.edit().putString("settings", j.toString()).apply()
    }
}
