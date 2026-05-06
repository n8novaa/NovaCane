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
import com.google.firebase.database.*
import android.util.Log

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

    // ---------------- SOS CONFIRMATION ----------------

    private var awaitingConfirmation = false
    private var firstTriggerTime = 0L
    private val CONFIRM_WINDOW = 3000L

    // 🔴 ACK TRACKING
    private var lastSOSState: Boolean? = null

    // ---------------- MODE CONTROL ----------------

    private var isUserModeActive = false

    fun startUserMode(caneID: String) {

        if (isUserModeActive) return

        isUserModeActive = true
        _sensorStatus.value = "Active"

        updateLocationOnce(caneID)
        startBluetooth(caneID)
        listenToSOSStatus(caneID)
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

                if (awaitingConfirmation &&
                    System.currentTimeMillis() - firstTriggerTime > CONFIRM_WINDOW
                ) {
                    awaitingConfirmation = false
                    Log.d("SOS", "Confirmation expired")
                }
            }
        }
    }

    // ---------------- BLUETOOTH ----------------

    private fun startBluetooth(caneID: String) {

        bluetoothManager.connect { message ->

            Log.d("RAW_BT", "MESSAGE = [$message]")

            if (!isUserModeActive) return@connect

            when (val data = MessageParser.parse(message)) {

                is SensorData.SOS -> processSOS(caneID)

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

        awaitingConfirmation = false

        // 🔴 ALWAYS GIVE FEEDBACK (FIXED)
        _alertState.value = "Emergency Triggered"

        ttsManager.speak("Emergency alert sent")
        vibrationController.vibrateSOS()

        if (!isUserModeActive) {
            Log.d("SOS", "Mode inactive → skipping backend")
            return
        }

        firebaseManager.sendSOS(caneID)
        updateLocationOnce(caneID)
    }

    private fun processSOS(caneID: String) {

        val now = System.currentTimeMillis()

        if (!awaitingConfirmation) {

            awaitingConfirmation = true
            firstTriggerTime = now

            ttsManager.speak("Press again to confirm emergency")
            vibrationController.vibrateObstacle()
            return
        }

        if (now - firstTriggerTime <= CONFIRM_WINDOW) {

            handleSOS(caneID)

        } else {

            firstTriggerTime = now
            ttsManager.speak("Press again to confirm emergency")
            vibrationController.vibrateObstacle()
        }
    }

    fun triggerSOS(caneID: String) {
        handleSOS(caneID)
    }

    // ---------------- ACK ----------------

    private fun listenToSOSStatus(caneID: String) {

        val ref = FirebaseDatabase.getInstance()
            .getReference("users/$caneID/sos/active")

        ref.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val isActive = snapshot.getValue(Boolean::class.java)

                if (isActive != null) {

                    if (lastSOSState == true && isActive == false) {

                        Log.d("SOS_FLOW", "Guardian marked SAFE")
                        onSOSResolved()
                    }

                    lastSOSState = isActive
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun onSOSResolved() {

        _alertState.value = "Safe"

        ttsManager.speak("Guardian confirmed. help is on the way")
        vibrationController.vibrateObstacle()
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