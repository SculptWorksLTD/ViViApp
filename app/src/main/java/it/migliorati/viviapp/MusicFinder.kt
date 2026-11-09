package it.migliorati.viviapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un brano trovato online, scaricabile. */
data class FoundTrack(
    val id: String,
    val title: String,
    val creator: String,
    val url: String,
    val durationSec: Int?,
    val license: String,
    val source: String,
    val landing: String,
    val ext: String,
)

/** Un album di Internet Archive: i brani si caricano quando lo si apre. */
data class FoundAlbum(
    val id: String,
    val title: String,
    val creator: String,
    val license: String,
    val tracks: List<FoundTrack>? = null,
)

data class FinderState(
    val loading: Boolean = false,
    val tracks: List<FoundTrack> = emptyList(),
    val albums: List<FoundAlbum> = emptyList(),
    val message: String = "",
    val previewId: String? = null,
    val previewLoading: Boolean = false,
    val downloading: Set<String> = emptySet(),
    val downloaded: Set<String> = emptySet(),
    val expanded: Set<String> = emptySet(),
    val albumLoading: Set<String> = emptySet(),
)

/**
 * Ricerca di musica libera, senza registrazione né chiavi:
 *  - Openverse: brani singoli da Jamendo, Freesound, ccMixter, Wikimedia
 *  - Internet Archive: album di netlabel con licenza Creative Commons
 * Si cercano solo licenze che permettono l'uso commerciale (adatte alle lezioni a pagamento).
 */
object MusicFinder {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val state = MutableStateFlow(FinderState())
    private var preview: MediaPlayer? = null

    fun init(ctx: Context) { app = ctx.applicationContext }

    // ---------------- Ricerca ----------------

    fun search(query: String, useOpenverse: Boolean, useArchive: Boolean) {
        val q = query.trim()
        if (q.isEmpty()) return
        stopPreview()
        state.update { it.copy(loading = true, message = "Cerco \"$q\"…", tracks = emptyList(), albums = emptyList(), expanded = emptySet()) }
        scope.launch {
            val ov = if (useOpenverse) async(Dispatchers.IO) { runCatching { searchOpenverse(q) } } else null
            val ia = if (useArchive) async(Dispatchers.IO) { runCatching { searchArchive(q) } } else null
            val tracks = ov?.await()
            val albums = ia?.await()
            val errors = listOfNotNull(
                tracks?.exceptionOrNull()?.let { "Openverse: ${it.message}" },
                albums?.exceptionOrNull()?.let { "Internet Archive: ${it.message}" },
            )
            val t = tracks?.getOrNull().orEmpty()
            val a = albums?.getOrNull().orEmpty()
            val msg = when {
                errors.isNotEmpty() && t.isEmpty() && a.isEmpty() -> "Ricerca non riuscita. " + errors.joinToString(" · ")
                t.isEmpty() && a.isEmpty() -> "Nessun risultato. Prova con parole in inglese (es. meditation, ambient, relaxing piano)."
                else -> "${t.size} brani e ${a.size} album" + (if (errors.isNotEmpty()) " (" + errors.joinToString(" · ") + ")" else "")
            }
            state.update { it.copy(loading = false, tracks = t, albums = a, message = msg) }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "ViViApp")
        val code = c.responseCode
        if (code == 429) throw IllegalStateException("troppe ricerche, riprova tra un minuto")
        if (code != 200) throw IllegalStateException("HTTP $code")
        return c.inputStream.bufferedReader().readText()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun searchOpenverse(q: String): List<FoundTrack> {
        val j = JSONObject(
            get("https://api.openverse.org/v1/audio/?q=${enc(q)}&license_type=commercial&category=music&page_size=20&mature=false")
        )
        val res = j.getJSONArray("results")
        val out = mutableListOf<FoundTrack>()
        for (i in 0 until res.length()) {
            val r = res.getJSONObject(i)
            val url = r.optString("url")
            if (url.isEmpty()) continue
            val ft = r.optString("filetype", "mp3").lowercase()
            val ext = when {
                ft.startsWith("mp3") || ft == "mp32" -> "mp3"
                ft in setOf("ogg", "flac", "wav", "m4a", "opus") -> ft
                else -> "mp3"
            }
            val lic = r.optString("license")
            val ver = r.optString("license_version")
            out += FoundTrack(
                id = "ov:" + r.getString("id"),
                title = r.optString("title").ifBlank { "Senza titolo" },
                creator = r.optString("creator").ifBlank { "Autore sconosciuto" },
                url = url,
                durationSec = r.optLong("duration", 0).takeIf { it > 0 }?.let { (it / 1000).toInt() },
                license = licenseLabel(lic, ver),
                source = r.optString("source").replaceFirstChar { it.uppercase() },
                landing = r.optString("foreign_landing_url"),
                ext = ext,
            )
        }
        return out
    }

    private fun searchArchive(q: String): List<FoundAlbum> {
        val query = "($q) AND mediatype:audio AND collection:(netlabels OR opensource_audio) " +
            "AND licenseurl:*creativecommons* AND -licenseurl:*nc*"
        val url = "https://archive.org/advancedsearch.php?q=${enc(query)}" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=licenseurl&rows=15&output=json"
        val docs = JSONObject(get(url)).getJSONObject("response").getJSONArray("docs")
        val out = mutableListOf<FoundAlbum>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            out += FoundAlbum(
                id = d.getString("identifier"),
                title = textOf(d, "title").ifBlank { d.getString("identifier") },
                creator = textOf(d, "creator").ifBlank { "Autore sconosciuto" },
                license = licenseFromUrl(textOf(d, "licenseurl")),
            )
        }
        return out
    }

    /** Su Internet Archive alcuni campi possono essere testo o lista. */
    private fun textOf(o: JSONObject, key: String): String = when (val v = o.opt(key)) {
        is JSONArray -> if (v.length() > 0) v.optString(0) else ""
        null -> ""
        else -> v.toString()
    }

    fun toggleAlbum(album: FoundAlbum) {
        val s = state.value
        if (album.id in s.expanded) {
            state.update { it.copy(expanded = it.expanded - album.id) }
            return
        }
        state.update { it.copy(expanded = it.expanded + album.id) }
        if (album.tracks != null) return
        state.update { it.copy(albumLoading = it.albumLoading + album.id) }
        scope.launch {
            val tracks = withContext(Dispatchers.IO) { runCatching { archiveTracks(album) }.getOrDefault(emptyList()) }
            state.update { st ->
                st.copy(
                    albums = st.albums.map { if (it.id == album.id) it.copy(tracks = tracks) else it },
                    albumLoading = st.albumLoading - album.id,
                )
            }
        }
    }

    private fun archiveTracks(album: FoundAlbum): List<FoundTrack> {
        val files = JSONObject(get("https://archive.org/metadata/${album.id}")).optJSONArray("files") ?: return emptyList()
        val out = mutableListOf<FoundTrack>()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.optString("name")
            if (!name.lowercase().endsWith(".mp3")) continue
            out += FoundTrack(
                id = "ia:${album.id}/$name",
                title = f.optString("title").ifBlank { name.substringAfterLast('/').substringBeforeLast('.') },
                creator = f.optString("artist").ifBlank { album.creator },
                url = "https://archive.org/download/${album.id}/" + name.split('/').joinToString("/") { enc(it).replace("+", "%20") },
                durationSec = parseLength(f.optString("length")),
                license = album.license,
                source = "Internet Archive",
                landing = "https://archive.org/details/${album.id}",
                ext = "mp3",
            )
        }
        return out.take(40)
    }

    private fun parseLength(s: String): Int? {
        if (s.isBlank()) return null
        return if (':' in s) s.split(':').fold(0) { acc, p -> acc * 60 + (p.toDoubleOrNull()?.toInt() ?: 0) }
        else s.toDoubleOrNull()?.toInt()
    }

    private fun licenseLabel(code: String, version: String): String = when (code.lowercase()) {
        "cc0" -> "CC0 (libero)"
        "pdm" -> "Pubblico dominio"
        "" -> "Licenza libera"
        else -> "CC ${code.uppercase()} $version".trim()
    }

    private fun licenseFromUrl(u: String): String {
        val l = u.lowercase()
        return when {
            "publicdomain" in l || "/zero/" in l -> "CC0 / Pubblico dominio"
            "/licenses/" in l -> {
                val parts = l.substringAfter("/licenses/").trim('/').split('/')
                "CC " + parts.getOrElse(0) { "" }.uppercase() + (parts.getOrNull(1)?.let { " $it" } ?: "")
            }
            else -> "Creative Commons"
        }
    }

    // ---------------- Anteprima ----------------

    /** L'anteprima suona sempre dall'altoparlante del tablet, mai sul cavo verso le clienti. */
    fun togglePreview(t: FoundTrack) {
        if (state.value.previewId == t.id) { stopPreview(); return }
        stopPreview()
        if (Mixer.state.value.sessionOn) {
            state.update { it.copy(message = "L'anteprima è disponibile a lezione ferma.") }
            return
        }
        val am = app.getSystemService(AudioManager::class.java)
        val speaker = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        state.update { it.copy(previewId = t.id, previewLoading = true) }
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setDataSource(t.url)
            if (speaker != null) p.setPreferredDevice(speaker)
            p.setOnPreparedListener {
                state.update { it.copy(previewLoading = false) }
                it.start()
            }
            p.setOnCompletionListener { stopPreview() }
            p.setOnErrorListener { _, _, _ ->
                stopPreview()
                state.update { it.copy(message = "Anteprima non disponibile per questo brano.") }
                true
            }
            p.prepareAsync()
            preview = p
        } catch (e: Exception) {
            stopPreview()
            state.update { it.copy(message = "Anteprima non disponibile: ${e.message}") }
        }
    }

    fun stopPreview() {
        try { preview?.stop() } catch (_: Exception) {}
        preview?.release()
        preview = null
        state.update { it.copy(previewId = null, previewLoading = false) }
    }

    // ---------------- Download ----------------

    fun download(t: FoundTrack) {
        if (t.id in state.value.downloading || t.id in state.value.downloaded) return
        state.update { it.copy(downloading = it.downloading + t.id) }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val base = "${t.title} - ${t.creator}".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                    val dest = Mixer.newLibraryFile("$base.${t.ext}")
                    var c = URL(t.url).openConnection() as HttpURLConnection
                    c.connectTimeout = 15_000
                    c.readTimeout = 60_000
                    c.setRequestProperty("User-Agent", "ViViApp")
                    var hops = 0
                    while (c.responseCode in 301..308 && hops < 5) {
                        val next = c.getHeaderField("Location") ?: break
                        c.disconnect()
                        c = URL(URL(t.url), next).openConnection() as HttpURLConnection
                        c.setRequestProperty("User-Agent", "ViViApp")
                        hops++
                    }
                    if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                    c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    // Crediti accanto al file: le licenze CC BY chiedono di citare l'autore
                    File(dest.path + ".json").writeText(
                        JSONObject()
                            .put("title", t.title).put("creator", t.creator)
                            .put("license", t.license).put("source", t.source).put("url", t.landing)
                            .toString()
                    )
                    dest
                }
            }
            result.onSuccess { f ->
                Mixer.reloadLibrary()
                state.update { it.copy(downloading = it.downloading - t.id, downloaded = it.downloaded + t.id, message = "\"${t.title}\" è in Libreria.") }
            }.onFailure { e ->
                state.update { it.copy(downloading = it.downloading - t.id, message = "Download non riuscito: ${e.message}") }
            }
        }
    }

    /** Crediti salvati accanto a un brano scaricato (null se non ce ne sono). */
    fun creditOf(f: File): String? = try {
        val side = File(f.path + ".json")
        if (!side.exists()) null else JSONObject(side.readText()).let {
            "${it.optString("creator")} · ${it.optString("license")} · ${it.optString("source")}"
        }
    } catch (_: Exception) { null }
}
