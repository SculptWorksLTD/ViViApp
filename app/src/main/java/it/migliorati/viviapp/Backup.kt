package it.migliorati.viviapp

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia di sicurezza in un unico file .zip: brani (con i crediti), playlist, scalette e impostazioni.
 * Serve per cambiare tablet o ripartire dopo un reset.
 */
object Backup {
    fun export(ctx: Context, dest: Uri, musicDir: File, settingsJson: String, playlistsFile: File, plansFile: File): Int {
        var count = 0
        ctx.contentResolver.openOutputStream(dest)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                fun putText(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                putText("settings.json", settingsJson)
                if (playlistsFile.exists()) putText("playlists.json", playlistsFile.readText())
                if (plansFile.exists()) putText("plans.json", plansFile.readText())
                musicDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("music/" + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    if (!f.name.endsWith(".json")) count++
                }
            }
        } ?: throw IllegalStateException("cannot open destination")
        return count
    }

    /** Restituisce il numero di brani ripristinati. */
    fun import(ctx: Context, src: Uri, musicDir: File, onSettings: (String) -> Unit, playlistsFile: File, plansFile: File): Int {
        var count = 0
        ctx.contentResolver.openInputStream(src)?.use { ins ->
            ZipInputStream(ins.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    // Nessun percorso "furbo" che esca dalle cartelle dell'app
                    if (e.isDirectory || name.contains("..")) { zip.closeEntry(); continue }
                    when {
                        name == "settings.json" -> onSettings(zip.readBytes().decodeToString())
                        name == "playlists.json" -> playlistsFile.writeBytes(zip.readBytes())
                        name == "plans.json" -> plansFile.writeBytes(zip.readBytes())
                        name.startsWith("music/") -> {
                            val fname = name.removePrefix("music/")
                            if (fname.isNotEmpty() && !fname.contains('/')) {
                                File(musicDir, fname).outputStream().use { zip.copyTo(it) }
                                if (!fname.endsWith(".json")) count++
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("cannot open file")
        return count
    }
}
