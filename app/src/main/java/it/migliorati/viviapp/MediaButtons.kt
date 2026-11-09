package it.migliorati.viviapp

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState

/**
 * Tasti dell'auricolare (e dei comandi multimediali in genere):
 * un tocco = play/pausa, due tocchi = brano successivo (gestiti da Android).
 */
class MediaButtons(ctx: Context) {
    private val session = MediaSession(ctx, "ViViApp")

    init {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { Mixer.mediaPlay() }
            override fun onPause() { Mixer.mediaPause() }
            override fun onSkipToNext() { Mixer.next() }
            override fun onStop() { Mixer.mediaPause() }
        })
        update(playing = false, title = null, positionMs = 0L)
    }

    fun setActive(active: Boolean) {
        if (session.isActive != active) session.isActive = active
    }

    fun update(playing: Boolean, title: String?, positionMs: Long) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs, if (playing) 1f else 0f,
                )
                .build()
        )
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title ?: "ViViApp")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "ViVi")
                .build()
        )
    }

    fun release() = session.release()
}
