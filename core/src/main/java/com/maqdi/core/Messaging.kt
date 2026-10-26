package com.maqdi.core

import android.Manifest
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

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "الطلبات", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    /** يمسح الإشعارات المعروضة عند فتح التطبيق */
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
