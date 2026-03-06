package com.example.novacane

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.*
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var vibrator: Vibrator
    private lateinit var database: DatabaseReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var mediaPlayer: MediaPlayer? = null
    private var isEmergencyActive = false

    private val deviceName = "NovaCane_ESP32"
    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private val alertState = mutableStateOf("Waiting for sensor data...")
    private val distanceState = mutableStateOf("--")

    private var lastSpokenTime = 0L
    private var lastZone = -1

    private val selectedRoleState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (selectedRoleState.value != null) {
                        selectedRoleState.value = null
                    } else finish()
                }
            })

        requestPermissions()

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

    // ================= PERMISSIONS =================

    private fun requestPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2)
        }
    }

    // ================= ENTRY =================

    @Composable
    fun AppEntryPoint() {

        val selectedRole = selectedRoleState.value

        if (selectedRole == null) {
            RoleSelectionScreen {
                selectedRoleState.value = it
            }
        } else {
            if (selectedRole == "USER")
                UserDashboard()
            else
                GuardianDashboard()
        }
    }

    // ================= ROLE SELECTION =================

    @Composable
    fun RoleSelectionScreen(onRoleSelected: (String) -> Unit) {

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
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

    // ================= USER =================

    @Composable
    fun UserDashboard() {

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
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
                onClick = { sendSOS() }
            ) {
                Text("SOS")
            }
        }
    }

    // ================= SEND SOS =================

    private fun sendSOS() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            alertState.value = "Location permission not granted"
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->

                if (location != null) {

                    val sosData = mapOf(
                        "status" to "SOS",
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "timestamp" to System.currentTimeMillis()
                    )

                    database.child("NovaCane")
                        .child("sos")
                        .setValue(sosData)

                    alertState.value = "SOS Sent"
                    speakSafe("Emergency alert sent")

                } else {
                    alertState.value = "Location unavailable"
                }
            }
    }

    // ================= ALERT SOUND =================

    private fun playAlertSound() {

        if (mediaPlayer == null) {

            mediaPlayer = MediaPlayer.create(
                this,
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            )

            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        }
    }

    private fun stopAlertSound() {

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // ================= GUARDIAN =================

    @Composable
    fun GuardianDashboard() {

        var guardianDistance by remember { mutableStateOf("--") }
        var guardianAlert by remember { mutableStateOf("Waiting...") }

        var emergencyActive by remember { mutableStateOf(false) }

        var sosLatitude by remember { mutableStateOf(0.0) }
        var sosLongitude by remember { mutableStateOf(0.0) }

        val liveDataRef = remember { database.child("NovaCane").child("liveData") }
        val sosRef = remember { database.child("NovaCane").child("sos") }

        DisposableEffect(Unit) {

            val liveListener = object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    guardianDistance =
                        snapshot.child("distance").getValue(Int::class.java)?.toString()
                            ?: "--"

                    guardianAlert =
                        snapshot.child("alert").getValue(String::class.java)
                            ?: "Waiting..."
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            val sosListener = object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val status =
                        snapshot.child("status").getValue(String::class.java)

                    val lat =
                        snapshot.child("latitude").getValue(Double::class.java)

                    val lon =
                        snapshot.child("longitude").getValue(Double::class.java)

                    if (status == "SOS" && !isEmergencyActive) {

                        isEmergencyActive = true
                        emergencyActive = true

                        if (lat != null && lon != null) {
                            sosLatitude = lat
                            sosLongitude = lon
                        }

                        playAlertSound()
                    }

                    if (status != "SOS") {
                        stopAlertSound()
                        isEmergencyActive = false
                        emergencyActive = false
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            liveDataRef.addValueEventListener(liveListener)
            sosRef.addValueEventListener(sosListener)

            onDispose {
                liveDataRef.removeEventListener(liveListener)
                sosRef.removeEventListener(sosListener)
                stopAlertSound()
            }
        }

        if (emergencyActive) {

            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text("🚨 EMERGENCY ALERT 🚨", fontSize = 26.sp)

                Spacer(modifier = Modifier.height(20.dp))

                Text("Latitude: $sosLatitude")
                Text("Longitude: $sosLongitude")

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        database.child("NovaCane")
                            .child("sos")
                            .child("status")
                            .setValue("SAFE")
                    }
                ) {
                    Text("MARK AS SAFE")
                }
            }

        } else {

            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text("Guardian Mode", fontSize = 24.sp)

                Spacer(modifier = Modifier.height(20.dp))

                Text("Distance: $guardianDistance cm")

                Spacer(modifier = Modifier.height(10.dp))

                Text("Alert: $guardianAlert")
            }
        }
    }

    // ================= BLUETOOTH =================

    private fun connectBluetooth() {

        val pairedDevices = bluetoothAdapter?.bondedDevices

        val device = pairedDevices?.firstOrNull { it.name == deviceName }

        if (device != null) {

            val socket: BluetoothSocket =
                device.createRfcommSocketToServiceRecord(uuid)

            thread {

                try {

                    socket.connect()

                    readData(socket.inputStream)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun readData(inputStream: InputStream) {

        val buffer = ByteArray(1024)

        while (true) {

            val bytes = inputStream.read(buffer)

            val receivedData = String(buffer, 0, bytes)

            val messages = receivedData.split("\n")

            for (msg in messages) {

                val cleanMsg = msg.trim()

                if (cleanMsg == "SOS") {

                    runOnUiThread {
                        alertState.value = "Emergency Triggered"
                        speakSafe("Emergency alert sent")
                        sendSOS()
                    }

                } else {

                    val distance = cleanMsg.toIntOrNull()

                    if (distance != null) {

                        runOnUiThread {

                            distanceState.value = distance.toString()

                            database.child("NovaCane")
                                .child("liveData")
                                .setValue(
                                    mapOf(
                                        "distance" to distance,
                                        "alert" to alertState.value
                                    )
                                )

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
    }

    // ================= UTILITIES =================

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

        if (currentTime - lastSpokenTime < 3000) return

        lastSpokenTime = currentTime

        if (language == "ml")
            tts.language = Locale.forLanguageTag("ml-IN")
        else
            tts.language = Locale.US

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        stopAlertSound()
        tts.shutdown()
        super.onDestroy()
    }
}