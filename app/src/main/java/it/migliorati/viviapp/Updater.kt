package it.migliorati.viviapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aggiornamenti dall'ultima Release pubblicata su GitHub.
 * Android non permette di installare in silenzio: l'app scarica il nuovo APK
 * e apre la schermata di installazione, dove basta premere "Aggiorna".
 */
object Updater {
    private const val REPO = "SculptWorksLTD/ViViApp"

    data class Update(val version: String, val url: String, val notes: String)

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val isDevBuild: Boolean get() = BuildConfig.VERSION_NAME.contains("dev")

    /** Restituisce l'aggiornamento se su GitHub c'è una versione più nuova, altrimenti null. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) throw IllegalStateException("GitHub risponde HTTP ${c.responseCode}")
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val version = j.getString("tag_name").removePrefix("v")
        val assets = j.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
        }
        if (url != null && isNewer(version, currentVersion)) Update(version, url, j.optString("body")) else null
    }

    /** Confronta versioni tipo "0.2" e "0.10.1" numero per numero. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { p -> p.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Scarica l'APK nella cache dell'app, riportando l'avanzamento (0..1). */
    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val dest = File(dir, "ViViApp-update.apk")
        var c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        // GitHub reindirizza al server dei file: seguiamo il redirect anche tra domini diversi
        var hops = 0
        while (c.responseCode in 301..308 && hops < 5) {
            val next = c.getHeaderField("Location") ?: break
            c.disconnect()
            c = URL(next).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            hops++
        }
        if (c.responseCode != 200) throw IllegalStateException("download fallito (HTTP ${c.responseCode})")
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                }
            }
        }
        dest
    }

    /** Può installare? Se no, la prima volta Android chiede di consentirlo. */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Apre la schermata di sistema per installare l'APK scaricato. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
