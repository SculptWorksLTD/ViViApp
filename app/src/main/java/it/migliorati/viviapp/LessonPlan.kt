package it.migliorati.viviapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Una fase della lezione: nome, durata e playlist (null = continua la musica attuale). */
data class Phase(val name: String, val minutes: Int, val playlist: String?)

/** Scaletta: fasi in ordine, con campana al cambio di fase. */
data class LessonPlan(
    val name: String,
    val phases: List<Phase>,
    val bellOnChange: Boolean = true,
    val bell: String = Bell.BOWL.id,
) {
    val totalMinutes: Int get() = phases.sumOf { it.minutes }
}

/** Scaletta in corso. */
data class PlanRun(
    val plan: LessonPlan,
    val index: Int,
    val phaseStartedAt: Long,   // SystemClock.elapsedRealtime()
    val phaseEndsAt: Long,
) {
    val phase: Phase get() = plan.phases[index]
    val nextPhase: Phase? get() = plan.phases.getOrNull(index + 1)
}

class PlanStore(ctx: Context) {
    private val file = File(ctx.filesDir, "plans.json")

    fun load(): List<LessonPlan> = try {
        if (!file.exists()) emptyList() else fromJson(file.readText())
    } catch (_: Exception) { emptyList() }

    fun save(list: List<LessonPlan>) = file.writeText(toJson(list))

    companion object {
        fun toJson(list: List<LessonPlan>): String {
            val arr = JSONArray()
            list.forEach { p ->
                val ph = JSONArray()
                p.phases.forEach {
                    ph.put(JSONObject().put("name", it.name).put("minutes", it.minutes).put("playlist", it.playlist ?: JSONObject.NULL))
                }
                arr.put(JSONObject().put("name", p.name).put("bellOnChange", p.bellOnChange).put("bell", p.bell).put("phases", ph))
            }
            return arr.toString()
        }

        fun fromJson(raw: String): List<LessonPlan> {
            val arr = JSONArray(raw)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ph = o.getJSONArray("phases")
                LessonPlan(
                    name = o.getString("name"),
                    bellOnChange = o.optBoolean("bellOnChange", true),
                    bell = o.optString("bell", Bell.BOWL.id),
                    phases = (0 until ph.length()).map { k ->
                        val x = ph.getJSONObject(k)
                        Phase(
                            x.getString("name"),
                            x.optInt("minutes", 10),
                            if (x.isNull("playlist")) null else x.optString("playlist"),
                        )
                    },
                )
            }
        }
    }
}
