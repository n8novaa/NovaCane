package com.example.novacane.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import com.example.novacane.bluetooth.BluetoothService
import com.example.novacane.bluetooth.ConnectionState
import com.example.novacane.sensor.SensorData
import com.example.novacane.sensor.MessageParser
import com.example.novacane.firebase.FirebaseManager
import com.example.novacane.tts.TTSManager
import com.example.novacane.alert.VibrationController
import com.example.novacane.location.LocationManager
import com.google.firebase.messaging.FirebaseMessaging

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat


import com.example.novacane.core.AppState

class MainViewModel(
    private val bluetoothManager: BluetoothService,
    private val firebaseManager: FirebaseManager,
    private val ttsManager: TTSManager,
    private val vibrationController: VibrationController,
    private val locationManager: LocationManager
) : ViewModel() {

    // ---------------- STATE ----------------

    private val _distanceState = MutableStateFlow<Int?>(null)
    val distanceState: StateFlow<Int?> = _distanceState

    private val _alertState = MutableStateFlow("Waiting...")
    val alertState: StateFlow<String> = _alertState

    private val _connectionStateText = MutableStateFlow("Disconnected")
    val connectionStateText: StateFlow<String> = _connectionStateText

    private val _sensorStatus = MutableStateFlow("Inactive")
    val sensorStatus: StateFlow<String> = _sensorStatus

    // ---------------- MODE CONTROL ----------------

    private var isUserModeActive = false

    fun startUserMode(caneID: String) {

        if (isUserModeActive) return

        isUserModeActive = true
        _sensorStatus.value = "Active"

        updateLocationOnce(caneID) // ✅ on start
        startBluetooth(caneID)
    }

    fun stopUserMode() {
        isUserModeActive = false

        stopAllFeedback()
        bluetoothManager.disconnect()

        _sensorStatus.value = "Inactive"
        lastZone = -1
    }

    private fun stopAllFeedback() {
        ttsManager.stop()
        vibrationController.stop()
    }

    // ---------------- INTERNAL STATE ----------------

    private var lastDataTimestamp = System.currentTimeMillis()
    private var lastZone = -1

    private var lastSpokenTime = 0L
    private val SPEECH_COOLDOWN = 3000L

    private fun canSpeak(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSpokenTime < SPEECH_COOLDOWN) return false
        lastSpokenTime = now
        return true
    }

    // ---------------- INIT ----------------

    init {
        observeConnectionState()
        startTimeoutMonitor()
        registerFCMToken()
    }

    // ---------------- CONNECTION ----------------

    private fun observeConnectionState() {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->

                val statusText = when (state) {
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.RECONNECTING -> "Reconnecting..."
                    ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionState.FAILED -> "Connection Failed"
                }

                _connectionStateText.value = statusText

                if (state != ConnectionState.CONNECTED) {
                    _alertState.value = "Cane disconnected"
                    _distanceState.value = null
                    _sensorStatus.value = "Disconnected"
                    lastZone = -1
                }
            }
        }
    }

    // ---------------- TIMEOUT ----------------

    private fun startTimeoutMonitor() {
        viewModelScope.launch {
            while (true) {
                delay(2000)

                if (!isUserModeActive) continue

                val diff = System.currentTimeMillis() - lastDataTimestamp

                if (diff > 3000) {
                    _distanceState.value = null
                    _alertState.value = "Sensor not responding"
                    _sensorStatus.value = "Disconnected"
                    lastZone = -1
                }
            }
        }
    }

    // ---------------- BLUETOOTH ----------------

    private fun startBluetooth(caneID: String) {

        bluetoothManager.connect { message ->

            if (!isUserModeActive) return@connect

            when (val data = MessageParser.parse(message)) {

                is SensorData.SOS -> handleSOS(caneID)

                is SensorData.Distance -> {

                    val distance = data.value
                    if (distance <= 0 || distance > 400) return@connect

                    lastDataTimestamp = System.currentTimeMillis()
                    _sensorStatus.value = "Active"
                    _distanceState.value = distance

                    val newZone = when {
                        distance < 30 -> 0
                        distance < 70 -> 1
                        else -> 2
                    }

                    if (newZone != lastZone) {

                        lastZone = newZone

                        when (newZone) {

                            0 -> {
                                _alertState.value = "Very Close"
                                if (canSpeak()) {
                                    ttsManager.speak("മുന്നിൽ തടസം വളരെ അടുത്താണ്", "ml")
                                    vibrationController.vibrateVeryClose()
                                }
                            }

                            1 -> {
                                _alertState.value = "Obstacle Ahead"
                                if (canSpeak()) {
                                    ttsManager.speak("Obstacle ahead")
                                    vibrationController.vibrateObstacle()
                                }
                            }

                            2 -> {
                                _alertState.value = "Path Clear"
                            }
                        }
                    }

                    firebaseManager.updateLiveData(caneID, distance, _alertState.value)
                }

                is SensorData.Invalid -> {}
            }
        }
    }

    // ---------------- SOS ----------------

    private fun handleSOS(caneID: String) {

        if (!isUserModeActive) return

        _alertState.value = "Emergency Triggered"

        ttsManager.speak("Emergency alert sent")
        vibrationController.vibrateSOS()

        firebaseManager.sendSOS(caneID)

        updateLocationOnce(caneID) // ✅ update location during SOS
    }

    fun triggerSOS(caneID: String) {
        handleSOS(caneID)
    }

    // ---------------- LOCATION ----------------

    private fun updateLocationOnce(caneID: String) {
        locationManager.getLocation { loc ->
            if (loc != null) {
                firebaseManager.updateLiveLocation(
                    caneID,
                    loc.latitude,
                    loc.longitude
                )
            }
        }
    }

    // ---------------- NOTIFICATION ----------------

    fun showLocalSOSNotification(context: Context) {

        if (AppState.currentMode != "GUARDIAN") return

        val channelId = "sos_channel"

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ---------------- FCM ----------------

    private fun registerFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                firebaseManager.saveGuardianToken("guardian_001", task.result)
            }
        }
    }
}