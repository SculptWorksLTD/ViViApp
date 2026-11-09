package it.migliorati.viviapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Lingue disponibili, nell'ordine in cui compaiono in Impostazioni. */
enum class Lang(val code: String, val label: String) {
    EN("en", "English"),
    HR("hr", "Hrvatski"),
    IT("it", "Italiano"),
}

/**
 * Lingua corrente. È uno "stato osservabile": quando cambia, tutte le schermate
 * si ridisegnano subito nella nuova lingua, senza riavviare l'app.
 */
object L {
    var lang by mutableStateOf(systemLang())

    /** code vuoto = lingua del tablet (se è una delle tre, altrimenti inglese). */
    fun apply(code: String) {
        lang = Lang.entries.firstOrNull { it.code == code } ?: systemLang()
    }

    fun systemLang(): Lang {
        val l = Locale.getDefault().language
        return Lang.entries.firstOrNull { it.code == l } ?: Lang.EN
    }
}

/** Testo nelle tre lingue: inglese, croato, italiano. */
fun tr(en: String, hr: String, it: String): String = when (L.lang) {
    Lang.EN -> en
    Lang.HR -> hr
    Lang.IT -> it
}

/** Testo da mostrare più tardi: viene tradotto nel momento in cui si legge. */
typealias UiText = () -> String
