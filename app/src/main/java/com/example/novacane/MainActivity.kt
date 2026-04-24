package com.example.novacane

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.location.LocationServices

import java.util.UUID

import com.example.novacane.bluetooth.BluetoothService
import com.example.novacane.firebase.FirebaseManager
import com.example.novacane.tts.TTSManager
import com.example.novacane.alert.VibrationController
import com.example.novacane.location.LocationManager
import com.example.novacane.viewmodel.MainViewModel
import com.example.novacane.ui.screens.MainScreen

import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private val REQUEST_PERMISSIONS = 1
    private val caneID = "cane_test"

    private val openGuardianState = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("TEST_LOG", "APP STARTED")

        handleIntent(intent)

        // 🔴 Bluetooth setup
        val bluetoothService = BluetoothService(
            "NovaCane_ESP32",
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        )

        val bluetoothAdapter =
            (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        bluetoothService.init(bluetoothAdapter)

        // 🔴 Vibrator setup
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val vibrationController = VibrationController(vibrator)

        // 🔴 Firebase setup
        val database = FirebaseDatabase.getInstance().reference
        val firebaseManager = FirebaseManager(database)

        // 🔴 TTS setup
        val ttsManager = TTSManager(this)

        // 🔴 Location setup
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationManager = LocationManager(fusedLocationClient)

        // 🔴 ViewModel (FIXED)
        viewModel = MainViewModel(
            bluetoothService,
            firebaseManager,
            ttsManager,
            vibrationController,
            locationManager
        )

        requestAllPermissions()

        setContent {
            MainScreen(viewModel, openGuardianState)
        }
    }

    private fun handleIntent(intent: Intent) {
        val openGuardian =
            intent?.getBooleanExtra("open_guardian", false) ?: false

        if (openGuardian) {
            Log.d("INTENT", "🔥 Open Guardian Mode (Intent)")
            openGuardianState.value = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        Log.d("INTENT_DEBUG", "🔥 onNewIntent called")
        Log.d("INTENT", "📩 New Intent Received")

        handleIntent(intent)
    }

    private fun requestAllPermissions() {

        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        } else {
            startSystem()
        }
    }

    private fun startSystem() {
        viewModel.start(caneID)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSIONS) {

            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            ) {
                startSystem()
            } else {
                Log.e("TEST_FLOW", "Permission denied")
            }
        }
    }
}