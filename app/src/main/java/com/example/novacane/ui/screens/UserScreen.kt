package com.example.novacane.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.novacane.viewmodel.MainViewModel

@Composable
fun UserScreen(viewModel: MainViewModel) {

    val distance by viewModel.distanceState.collectAsState()
    val alert by viewModel.alertState.collectAsState()
    val connection by viewModel.connectionStateText.collectAsState()

    // 🔴 STATUS LOGIC
    val statusColor = when {
        alert.contains("Very Close", true) -> Color.Red
        alert.contains("Obstacle", true) -> Color(0xFFFFA500)
        alert.contains("Clear", true) -> Color(0xFF2E7D32)
        else -> Color.DarkGray
    }

    val statusText = when {
        alert.contains("Very Close", true) -> "Very Close"
        alert.contains("Obstacle", true) -> "Obstacle Ahead"
        alert.contains("Clear", true) -> "Path Clear"
        else -> "Waiting"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // 🔵 CONNECTION STATUS
        Text(
            text = "Connection: $connection",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                contentDescription = "Bluetooth connection is $connection"
            }
        )

        // 🔴 BIG STATUS PANEL
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(statusColor, RoundedCornerShape(20.dp))
                .semantics {
                    contentDescription = "Obstacle status is $statusText"
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusText,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge
            )
        }

        // 📏 DISTANCE
        Text(
            text = distance?.let { "Distance: $it cm" } ?: "Distance: --",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                contentDescription = distance?.let {
                    "Distance is $it centimeters"
                } ?: "Distance not available"
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // 🚨 SOS BUTTON
        Button(
            onClick = { viewModel.triggerSOS("cane_test") },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .semantics {
                    contentDescription = "Emergency SOS button"
                },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "SOS",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

