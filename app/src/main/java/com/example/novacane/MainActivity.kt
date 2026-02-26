package com.example.novacane

import androidx.compose.runtime.Composable
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
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

    private val deviceName = "NovaCane_ESP32"
    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private val alertState = mutableStateOf("Waiting for sensor data...")

    // ✅ Voice cooldown variables
    private var lastSpokenTime = 0L
    private val speechCooldown = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setContent {
            novaCaneDashboard()
        }

        connectBluetooth()
    }

    @Composable
    fun novaCaneDashboard() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nova Cane – User Dashboard", fontSize = 22.sp)

            Spacer(Modifier.height(20.dp))

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

    private fun connectBluetooth() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val pairedDevices = bluetoothAdapter?.bondedDevices
        val device = pairedDevices?.firstOrNull { it.name == deviceName }

        if (device != null) {

            val socket: BluetoothSocket =
                device.createRfcommSocketToServiceRecord(uuid)

            thread {
                try {
                    socket.connect()
                    runOnUiThread { alertState.value = "Bluetooth connected" }

                    val inputStream: InputStream = socket.inputStream
                    readData(inputStream)

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

            val messages = receivedData.split("\n")

            for (msg in messages) {
                val distance = msg.trim().toIntOrNull()

                if (distance != null) {
                    runOnUiThread {
                        when {
                            distance < 30 -> {
                                alertState.value = "Very close obstacle"
                                speakSafe("മുന്നിൽ തടസം വളരെ അടുത്താണ്", "ml")
                            }

                            distance < 70 -> {
                                alertState.value = "Obstacle ahead"
                                speakSafe("Obstacle ahead")
                            }

                            else -> {
                                alertState.value = "Path clear"
                            }
                        }
                    }
                }
            }
        }
    }

    // ✅ Cooldown speech function
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