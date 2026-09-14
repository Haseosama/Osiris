package com.osiris.app.map

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.Earthquake
import com.osiris.app.recon.WatchlistEntry

private const val CHANNEL_ID = "osiris_alerts"

/**
 * Local notifications for events worth interrupting the user for even while the app isn't in
 * the foreground — a strong earthquake, a conflict zone escalating, a watched RECON query
 * changing. [MapViewModel]/[com.osiris.app.recon.WatchlistWorker] each decide *when* one of
 * those actually happened (never on a first poll/check, since that's just the current state
 * rather than a new event); this only builds and posts the notification.
 */
object AlertNotifier {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertes OSIRIS",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Séismes majeurs, escalades de zones de conflit, watchlist RECON"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun notifyEarthquake(context: Context, quake: Earthquake) {
        val id = quake.id ?: return
        val magnitude = quake.magnitude
        notify(
            context,
            notificationId = id.hashCode(),
            title = if (magnitude != null) "Séisme M%.1f".format(magnitude) else "Séisme important",
            text = quake.place ?: "Localisation inconnue",
        )
    }

    fun notifyConflictEscalation(context: Context, zone: ConflictZone) {
        notify(
            context,
            notificationId = zone.id.hashCode(),
            title = "Escalade — ${zone.label}",
            text = "Niveau : ${zone.severity}",
        )
    }

    /** A watched RECON query's result changed since its last background check — see
     * [com.osiris.app.recon.WatchlistWorker]. Generic across all eleven tools (no per-tool
     * wording): the point is to send the user back into the app to look, not to summarize what
     * changed in the notification itself. */
    fun notifyWatchlistChange(context: Context, entry: WatchlistEntry) {
        notify(
            context,
            notificationId = entry.id.hashCode(),
            title = "Changement détecté",
            text = "${entry.tool.label} — ${entry.label}",
        )
    }

    private fun notify(context: Context, notificationId: Int, title: String, text: String) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
