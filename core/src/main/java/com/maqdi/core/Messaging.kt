package com.maqdi.core

import android.Manifest
import android.app.Notification
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Notifier {
    const val CHANNEL = "maqdi_orders"
    const val URGENT_CHANNEL = "maqdi_new_orders"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "الطلبات", NotificationManager.IMPORTANCE_HIGH))
            // قناة التنبيه القوي للطلبات الجديدة: صوت منبّه واهتزاز
            val urgent = NotificationChannel(URGENT_CHANNEL, "طلبات جديدة (تنبيه قوي)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(urgent)
        }
    }

    /** يوقف أي تنبيه معروض (يُستدعى عند فتح التطبيق فيتوقف الصوت المتكرر) */
    fun clearAll(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
    }
}

class MaqdiMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Notifier.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // طلب جديد للتاجر: تنبيه قوي يتكرر صوته حتى يفتح التاجر التطبيق (أو تنتهي دقيقتان)
        if (message.data["alert"] == "urgent") {
            val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val urgent = NotificationCompat.Builder(this, Notifier.URGENT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(message.data["title"] ?: "طلب جديد")
                .setContentText(message.data["body"] ?: "وصلك طلب جديد، راجعه الآن.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarm, android.media.AudioManager.STREAM_ALARM)
                .setVibrate(longArrayOf(0, 600, 300, 600, 300, 600))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(120_000)
                .setContentIntent(pi)
                .build()
            urgent.flags = urgent.flags or Notification.FLAG_INSISTENT
            getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), urgent)
            return
        }

        val n = message.notification ?: return
        val notif = NotificationCompat.Builder(this, Notifier.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), notif)
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try { Repo.addToken(uid, token) } catch (_: Exception) { }
        }
    }
}
