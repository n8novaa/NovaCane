package com.example.novacane.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.novacane.R
import com.example.novacane.viewmodel.GuardianViewModel
import kotlinx.coroutines.delay

@Composable
fun GuardianScreen(viewModel: GuardianViewModel) {

    val sosActive by viewModel.sosActive.collectAsState()
    val lat by viewModel.latitude.collectAsState()
    val lon by viewModel.longitude.collectAsState()

    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    var blink by remember { mutableStateOf(true) }

    // 🔥 Start Firebase listener
    LaunchedEffect(Unit) {
        viewModel.startListening()
    }

    // 🔊 Alarm control (SIDE EFFECT ONLY)
    LaunchedEffect(sosActive) {
        if (sosActive) {
            mediaPlayer = MediaPlayer.create(context, R.raw.sos_alarm)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } else {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // 🔴 Blinking effect
    LaunchedEffect(sosActive) {
        while (sosActive) {
            blink = !blink
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "Guardian Mode",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 🔴 UI must be OUTSIDE LaunchedEffect
        if (sosActive) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (blink) Color.Red else Color.DarkGray)
                    .padding(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🚨 SOS ACTIVE",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Latitude: ${lat ?: "--"}")
            Text("Longitude: ${lon ?: "--"}")

            Spacer(modifier = Modifier.height(20.dp))

            // 📍 OPEN MAPS
            Button(
                onClick = {
                    if (lat != null && lon != null) {
                        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Location in Maps")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 🔥 MARK SAFE
            Button(
                onClick = { viewModel.markSafe() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Green
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Mark as Safe",
                    color = Color.White
                )
            }

        } else {

            Text(
                "No active SOS",
                color = Color.Gray,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}