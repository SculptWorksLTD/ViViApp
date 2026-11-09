package it.migliorati.viviapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.IBinder

/**
 * Servizio in primo piano: tiene viva la lezione anche a schermo spento
 * o se si passa a un'altra app. Ospita anche la registrazione della lezione.
 */
class MixerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECORD -> {
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                @Suppress("DEPRECATION")
                val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                // Su Android 14+ il servizio deve dichiarare la cattura prima di ottenere il permesso
                goForeground(withProjection = true)
                val mpm = getSystemService(MediaProjectionManager::class.java)
                val projection = if (data != null) mpm.getMediaProjection(code, data) else null
                if (projection == null) {
                    goForeground(withProjection = false)
                    Mixer.onRecordingFinished(null, "permission denied")
                } else {
                    Mixer.startRecorderWith(projection)
                }
            }
            ACTION_RECORD_DONE -> goForeground(withProjection = false)
            else -> goForeground(withProjection = false)
        }
        return START_NOT_STICKY
    }

    private fun goForeground(withProjection: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Lesson in progress", "Sat u tijeku", "Lezione in corso"), NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (withProjection) tr("Recording the lesson", "Snimanje sata", "Registrazione della lezione")
                   else tr("Lesson on air: voice and music on the cable", "Sat u eteru: glas i glazba na kabelu", "Lezione attiva: voce e musica sul cavo")
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("ViViApp")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (withProjection) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        startForeground(1, n, types)
    }

    companion object {
        private const val CHANNEL = "viviapp_lezione"
        const val ACTION_RECORD = "it.migliorati.viviapp.RECORD"
        const val ACTION_RECORD_DONE = "it.migliorati.viviapp.RECORD_DONE"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
    }
}
