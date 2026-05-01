package com.example.novacane.viewmodel

import android.content.Context
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel

import com.example.novacane.firebase.FirebaseManager
import com.google.firebase.database.FirebaseDatabase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GuardianViewModel : ViewModel() {

    private val firebaseManager = FirebaseManager(
        FirebaseDatabase.getInstance().reference
    )

    private val _sosActive = MutableStateFlow(false)
    val sosActive: StateFlow<Boolean> = _sosActive

    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude: StateFlow<Double?> = _latitude

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude: StateFlow<Double?> = _longitude

    // 🔴 SINGLE SOURCE OF TRUTH (listener)
    fun startListening(context: Context, caneID: String) {

        firebaseManager.listenToSOS(caneID) { lat, lon ->

            // 🔴 Update UI state
            _sosActive.value = true
            _latitude.value = lat
            _longitude.value = lon

            // 🔴 Trigger notification ONLY on new SOS event
            showLocalSOSNotification(context)
        }
    }

    fun showLocalSOSNotification(context: Context) {

        val channelId = "sos_channel"

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        // 🔴 Create notification channel (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("🚨 SOS ALERT")
            .setContentText("Emergency triggered")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }

    fun markSafe() {
        firebaseManager.markSafe("cane_test")
        _sosActive.value = false
    }
}