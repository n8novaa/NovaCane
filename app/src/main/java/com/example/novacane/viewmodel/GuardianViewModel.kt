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

    private val database = FirebaseDatabase.getInstance().reference
    private val firebaseManager = FirebaseManager(database)

    private val caneID = "cane_test"

    private val _sosActive = MutableStateFlow(false)
    val sosActive: StateFlow<Boolean> = _sosActive

    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude: StateFlow<Double?> = _latitude

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude: StateFlow<Double?> = _longitude

    private val _isUserActive = MutableStateFlow(false)
    val isUserActive: StateFlow<Boolean> = _isUserActive

    private val _locationTimestamp = MutableStateFlow<Long?>(null)
    val locationTimestamp: StateFlow<Long?> = _locationTimestamp

    private val _sosTimestamp = MutableStateFlow<Long?>(null)
    val sosTimestamp: StateFlow<Long?> = _sosTimestamp

    private var isListening = false

    fun startListening(context: Context) {

        if (isListening) return
        isListening = true

        firebaseManager.listenToSOS(caneID) { lat, lon ->

            _sosActive.value = true
            _latitude.value = lat
            _longitude.value = lon

            _sosTimestamp.value = System.currentTimeMillis()
            _isUserActive.value = true

            showLocalSOSNotification(context)
        }
    }

    fun fetchLatestLocation(
        onResult: (Double?, Double?) -> Unit
    ) {
        val ref = database.child("users").child(caneID).child("liveLocation")

        ref.get().addOnSuccessListener { snapshot ->

            val lat = snapshot.child("latitude").getValue(Double::class.java)
            val lon = snapshot.child("longitude").getValue(Double::class.java)
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java)

            _locationTimestamp.value = timestamp

            if (timestamp != null) {
                val diff = System.currentTimeMillis() - timestamp
                _isUserActive.value = diff < 5000
            }

            onResult(lat, lon)

        }.addOnFailureListener {
            onResult(null, null)
        }
    }

    private fun showLocalSOSNotification(context: Context) {

        val channelId = "sos_channel"

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

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
        firebaseManager.markSafe(caneID)

        _sosActive.value = false
        _latitude.value = null
        _longitude.value = null
    }
}