package com.example.novacane.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.novacane.MainActivity
import com.example.novacane.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        Log.d("FCM", "📩 Message received")

        val title = remoteMessage.notification?.title ?: "SOS ALERT"
        val body = remoteMessage.notification?.body ?: "Emergency triggered"

        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "🔁 New Token: $token")
    }

    private fun showNotification(title: String, message: String) {

        val channelId = "sos_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 🔴 Notification Channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        // 🔥 CORRECT INTENT (IMPORTANT FIX)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_guardian", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // 🔥 KEY LINE
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}