package com.example.novacane.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.novacane.viewmodel.MainViewModel
import com.example.novacane.viewmodel.GuardianViewModel

import kotlinx.coroutines.flow.StateFlow

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.widget.Toast

import com.example.novacane.core.AppState

enum class Screen {
    HOME,
    USER,
    GUARDIAN
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    openGuardianState: StateFlow<Boolean>
) {

    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var lastBackPressed by remember { mutableStateOf(0L) }

    val guardianViewModel: GuardianViewModel = viewModel()

    val openGuardian by openGuardianState.collectAsState()

    // 🔴 Handle notification → open guardian
    LaunchedEffect(openGuardian) {
        if (openGuardian) {
            currentScreen = Screen.GUARDIAN
            AppState.currentMode = "GUARDIAN"
        }
    }

    // 🔴 BACK BUTTON FIX
    BackHandler {
        when (currentScreen) {

            Screen.USER,
            Screen.GUARDIAN -> {
                currentScreen = Screen.HOME
            }

            Screen.HOME -> {
                val now = System.currentTimeMillis()

                if (now - lastBackPressed < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressed = now
                    Toast.makeText(context, "Press again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 🔴 STATE COLLECTION
    val distance by viewModel.distanceState.collectAsState()
    val alert by viewModel.alertState.collectAsState()
    val connection by viewModel.connectionStateText.collectAsState()
    val sensor by viewModel.sensorStatus.collectAsState()

    // 🔴 SCREEN RENDERING
    when (currentScreen) {

        Screen.HOME -> {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {

                Button(
                    onClick = {
                        currentScreen = Screen.USER
                        AppState.currentMode = "USER"
                              },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("User Mode")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        currentScreen = Screen.GUARDIAN
                        AppState.currentMode = "GUARDIAN"
                              },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardian Mode")
                }
            }
        }

        Screen.USER -> {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                Text(
                    text = "Bluetooth: $connection",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = distance?.let { "Distance: $it cm" } ?: "Distance: --",
                    style = MaterialTheme.typography.headlineMedium
                )

                val color = when {
                    alert.contains("close", true) -> Color.Red
                    alert.contains("ahead", true) -> Color(0xFFFFA500)
                    alert.contains("clear", true) -> Color(0xFF2E7D32)
                    alert.contains("not responding", true) -> Color.Gray
                    else -> Color.Gray
                }

                Text(
                    text = "Status: $alert",
                    color = color,
                    style = MaterialTheme.typography.titleLarge
                )

                val sensorColor =
                    if (sensor == "Active") Color(0xFF2E7D32) else Color.Red

                Text(
                    text = "Sensor: $sensor",
                    color = sensorColor,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = {
                        viewModel.triggerSOS("cane_test", context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        Screen.GUARDIAN -> {
            GuardianScreen(viewModel = guardianViewModel)
        }
    }
}