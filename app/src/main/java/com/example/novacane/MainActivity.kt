package com.example.novacane

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var vibrator: Vibrator

    private val deviceName = "NovaCane_ESP32"
    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private val alertState = mutableStateOf("Waiting for sensor data...")
    private val distanceState = mutableStateOf("--")

    private var lastSpokenTime = 0L
    private val speechCooldown = 3000L
    private var lastZone = -1

    // ✅ ROLE STATE MOVED TO ACTIVITY LEVEL
    private val selectedRoleState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back button handling (modern)
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (selectedRoleState.value != null) {
                        selectedRoleState.value = null
                    } else {
                        finish()
                    }
                }
            })

        // Bluetooth permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    1
                )
            }
        }

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        setContent {
            AppEntryPoint()
        }

        connectBluetooth()
    }

    // ================= ENTRY =================

    @Composable
    fun AppEntryPoint() {
        val selectedRole = selectedRoleState.value

        if (selectedRole == null) {
            RoleSelectionScreen { role ->
                selectedRoleState.value = role
            }
        } else {
            if (selectedRole == "USER") {
                UserDashboard()
            } else {
                GuardianDashboard()
            }
        }
    }

    // ================= ROLE SCREEN =================

    @Composable
    fun RoleSelectionScreen(onRoleSelected: (String) -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text("Nova Cane", fontSize = 26.sp)

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onRoleSelected("USER") }
            ) {
                Text("USER")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onRoleSelected("GUARDIAN") }
            ) {
                Text("GUARDIAN")
            }
        }
    }

    // ================= USER DASHBOARD =================

    @Composable
    fun UserDashboard() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Nova Cane – User Dashboard", fontSize = 22.sp)

            Spacer(Modifier.height(20.dp))

            Text("Distance: ${distanceState.value} cm", fontSize = 20.sp)

            Spacer(Modifier.height(10.dp))

            Text("Alert: ${alertState.value}", fontSize = 18.sp)

            Spacer(Modifier.height(30.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    alertState.value = "Emergency alert sent"
                    speakSafe("Emergency alert sent")
                }
            ) {
                Text("SOS")
            }
        }
    }

    // ================= GUARDIAN DASHBOARD =================

    @Composable
    fun GuardianDashboard() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Guardian Mode", fontSize = 24.sp)

            Spacer(modifier = Modifier.height(20.dp))

            Text("Waiting for SOS alerts...")
        }
    }

    // ================= BLUETOOTH =================

    private fun connectBluetooth() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val pairedDevices = bluetoothAdapter?.bondedDevices
        val device = pairedDevices?.firstOrNull { it.name == deviceName }

        if (device != null) {

            val socket = device.createRfcommSocketToServiceRecord(uuid)

            thread {
                try {
                    socket.connect()
                    runOnUiThread {
                        alertState.value = "Bluetooth connected"
                    }
                    readData(socket.inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        alertState.value = "Bluetooth connection failed"
                    }
                }
            }

        } else {
            alertState.value = "ESP32 not paired"
        }
    }

    private fun readData(inputStream: InputStream) {
        val buffer = ByteArray(1024)

        while (true) {
            val bytes = inputStream.read(buffer)
            val receivedData = String(buffer, 0, bytes)
            Log.d("BT_DATA", receivedData)

            val messages = receivedData.split("\n")

            for (msg in messages) {
                val distance = msg.trim().toIntOrNull()

                if (distance != null) {
                    runOnUiThread {

                        distanceState.value = distance.toString()

                        val zone = when {
                            distance < 30 -> 0
                            distance < 70 -> 1
                            else -> 2
                        }

                        if (zone != lastZone) {
                            lastZone = zone

                            when (zone) {
                                0 -> {
                                    alertState.value = "Very close obstacle"
                                    speakSafe("മുന്നിൽ തടസം വളരെ അടുത്താണ്", "ml")
                                    vibrate(255)
                                }
                                1 -> {
                                    alertState.value = "Obstacle ahead"
                                    speakSafe("Obstacle ahead")
                                    vibrate(150)
                                }
                                2 -> {
                                    alertState.value = "Path clear"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun vibrate(intensity: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(200, intensity)
            )
        } else {
            vibrator.vibrate(200)
        }
    }

    private fun speakSafe(text: String, language: String = "en") {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpokenTime < speechCooldown) return

        lastSpokenTime = currentTime

        if (language == "ml") {
            tts.language = Locale.forLanguageTag("ml-IN")
        } else {
            tts.language = Locale.US
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}