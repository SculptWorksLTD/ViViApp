package it.migliorati.viviapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Una playlist: nome e brani (per nome file, nell'ordine di riproduzione). */
data class Playlist(val name: String, val tracks: List<String>)

/** Salva le playlist in un piccolo file JSON nella memoria dell'app. */
class PlaylistStore(ctx: Context) {
    private val file = File(ctx.filesDir, "playlists.json")

    fun load(): List<Playlist> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val t = o.getJSONArray("tracks")
                Playlist(o.getString("name"), (0 until t.length()).map { t.getString(it) })
            }
        }
    } catch (_: Exception) { emptyList() }

    fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p -> arr.put(JSONObject().put("name", p.name).put("tracks", JSONArray(p.tracks))) }
        file.writeText(arr.toString())
    }
}
