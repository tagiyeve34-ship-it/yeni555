package com.ailenezareti.panelapp.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.ui.MainActivity

object LocationNotificationManager {
    const val CHANNEL_ID = "important_events"
    const val EXTRA_OPEN_LOCATION = "open_location"
    const val EXTRA_CHILD_ID = "child_id"
    const val EXTRA_LATITUDE = "latitude"
    const val EXTRA_LONGITUDE = "longitude"
    const val EXTRA_RECORDED_AT = "recorded_at"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vacib hadisələr",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Zona, zəng, batareya və əlaqə xəbərdarlıqları"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showZoneEvent(context: Context, childId: Int, childName: String, message: String, lat: Double, lon: Double, recordedAt: String, id: Int) {
        createChannel(context); if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_LOCATION, true); putExtra(EXTRA_CHILD_ID, childId)
            putExtra(EXTRA_LATITUDE, lat); putExtra(EXTRA_LONGITUDE, lon); putExtra(EXTRA_RECORDED_AT, recordedAt)
        }
        val pending = PendingIntent.getActivity(context, 50_000 + id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_target)
            .setContentTitle(childName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message · ${recordedAt.replace("T", " ").take(16)}"))
            .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(context).notify(50_000 + id, n)
    }

    fun showCall(context: Context, childId: Int, childName: String, contact: String, type: String, duration: Int, occurredAt: String, idSeed: Int) {
        createChannel(context); if (!canNotify(context)) return
        val label = when(type) { "incoming" -> "Gələn zəng"; "outgoing" -> "Gedən zəng"; "missed" -> "Buraxılmış zəng"; else -> "Zəng" }
        val text = if (duration > 0) "$contact · ${duration / 60} dəq ${duration % 60} san" else contact
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone).setContentTitle("$childName · $label").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text · ${occurredAt.replace("T", " ").take(16)}"))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        NotificationManagerCompat.from(context).notify(60_000 + childId * 100 + (idSeed % 100), n)
    }

    fun showBatteryLow(context: Context, childId: Int, childName: String, battery: Int) {
        createChannel(context); if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Batareya aşağıdır").setContentText("$childName · batareya $battery%-dir")
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(20_000 + childId, n)
    }

    fun showOffline(context: Context, childId: Int, childName: String, recordedAt: String) {
        createChannel(context); if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("GPS məlumatı gecikir").setContentText("$childName · son GPS: ${recordedAt.replace("T", " ").take(16)}")
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(30_000 + childId, n)
    }

    /** Firebase push mesajlarından gələn ümumi bildirişlər üçün (server-dən title+body hazır gəlir) */
    fun showFromPush(context: Context, title: String, body: String, notifyId: Int) {
        createChannel(context); if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, notifyId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(context).notify(notifyId, n)
    }
}
