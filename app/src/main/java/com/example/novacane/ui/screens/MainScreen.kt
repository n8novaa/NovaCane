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

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    openGuardianState: StateFlow<Boolean> // 🔥 FIXED
) {

    val context = LocalContext.current

    val guardianViewModel: GuardianViewModel = viewModel()

    // 🔥 LISTEN TO STATEFLOW
    val openGuardian by openGuardianState.collectAsState()

    var isGuardianMode by remember { mutableStateOf(false) }

    // 🔥 REACT TO NOTIFICATION TRIGGER
    LaunchedEffect(openGuardian) {
        if (openGuardian) {
            isGuardianMode = true
        }
    }

    val distance by viewModel.distanceState.collectAsState()
    val alert by viewModel.alertState.collectAsState()
    val connection by viewModel.connectionStateText.collectAsState()
    val sensor by viewModel.sensorStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Button(
            onClick = {
                isGuardianMode = !isGuardianMode
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isGuardianMode) "Switch to User Mode"
                else "Switch to Guardian Mode"
            )
        }

        if (isGuardianMode) {

            GuardianScreen(viewModel = guardianViewModel)

        } else {

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
}