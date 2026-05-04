package com.example.novacane.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.novacane.viewmodel.MainViewModel
import com.example.novacane.viewmodel.GuardianViewModel
import com.example.novacane.ui.screens.UserScreen

import kotlinx.coroutines.flow.StateFlow

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.widget.Toast

import androidx.compose.ui.Alignment

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

        // ================= HOME =================
        Screen.HOME -> {

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

                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {

                    // 🔷 HEADER
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                            Text(
                                text = "NOVACANE",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    letterSpacing = 4.sp
                                ),
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Assistive Navigation System",
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // USER MODE
                    GlassModeCard(
                        title = "User Mode",
                        subtitle = "Connected to smart cane",
                        onClick = {
                            currentScreen = Screen.USER
                            AppState.currentMode = "USER"
                        }
                    )

                    // GUARDIAN MODE
                    GlassModeCard(
                        title = "Guardian Mode",
                        subtitle = "Monitor & respond to alerts",
                        onClick = {
                            currentScreen = Screen.GUARDIAN
                            AppState.currentMode = "GUARDIAN"
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "System Ready",
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // ================= USER =================
        Screen.USER -> {

            LaunchedEffect(Unit) {
                viewModel.startUserMode("cane_test")
            }

            DisposableEffect(Unit) {
                onDispose {
                    viewModel.stopUserMode()
                }
            }

            UserScreen(viewModel = viewModel)
        }

        // ================= GUARDIAN =================
        Screen.GUARDIAN -> {
            GuardianScreen(viewModel = guardianViewModel)
        }
    }
}

@Composable
fun GlassModeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column {

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}