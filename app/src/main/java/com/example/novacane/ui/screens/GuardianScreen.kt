package com.example.novacane.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.novacane.R
import com.example.novacane.viewmodel.GuardianViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GuardianScreen(viewModel: GuardianViewModel) {

    val sosActive by viewModel.sosActive.collectAsState()
    val isUserActive by viewModel.isUserActive.collectAsState()
    val lastSeen by viewModel.lastSeenText.collectAsState()
    val sosHistory by viewModel.sosHistory.collectAsState()

    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var blink by remember { mutableStateOf(true) }

    // 🔴 Start listeners
    LaunchedEffect(Unit) {
        viewModel.startListening(context)
        viewModel.startUserMonitoring()
        viewModel.listenToSOSHistory()
    }

    // 🔊 Alarm
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

    // 🔴 Blinking SOS
    LaunchedEffect(sosActive) {
        while (sosActive) {
            blink = !blink
            delay(500)
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF1E293B),
            Color(0xFF334155)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(20.dp)
    ) {

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

            // 🔷 HEADER
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text(
                        text = "GUARDIAN",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        ),
                        color = Color.White
                    )

                    Text(
                        text = "Monitoring Dashboard",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // 🟢 USER STATUS + LAST SEEN
            GlassCard {
                Column {

                    Text("User Status", color = Color.White.copy(alpha = 0.7f))

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isUserActive) "Active" else "Inactive",
                        color = if (isUserActive) Color.Green else Color.Gray,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Last Seen: $lastSeen",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 🔴 SOS STATUS
            if (sosActive) {
                GlassCard(
                    backgroundColor = if (blink)
                        Color.Red.copy(alpha = 0.6f)
                    else
                        Color.DarkGray.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🚨 SOS ACTIVE",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            } else {
                GlassCard {
                    Text("No active SOS", color = Color.LightGray)
                }
            }

            // 📍 ACTIONS
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Button(
                        onClick = {
                            viewModel.fetchLatestLocation { lat, lon ->
                                if (lat != null && lon != null) {
                                    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Location not available",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Track User")
                    }

                    if (sosActive) {
                        Button(
                            onClick = { viewModel.markSafe() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22C55E)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mark as Safe", color = Color.White)
                        }
                    }
                }
            }


        }
    }
}

@Composable
fun GlassCard(
    backgroundColor: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}