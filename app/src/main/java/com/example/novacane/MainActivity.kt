package com.example.novacane

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        setContent {
            NovaCaneDashboard()
        }
    }

    @Composable
    fun NovaCaneDashboard() {
        var alertText by remember { mutableStateOf("No alerts") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Nova Cane – User Dashboard",
                fontSize = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Alert: $alertText",
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    // Near obstacle – Malayalam
                    alertText = "മുന്നിൽ തടസം വളരെ അടുത്താണ്"
                    speak("മുന്നിൽ തടസം വളരെ അടുത്താണ്", "ml")
                }
            ) {
                Text("Simulate Distance = 30 cm")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    alertText = "Obstacle ahead"
                    speak("Obstacle ahead")
                }
            ) {
                Text("Simulate Distance = 80 cm")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    alertText = "Emergency alert sent"
                    speak("Emergency alert sent")
                }
            ) {
                Text("SOS")
            }
        }
    }

    private fun speak(text: String, language: String = "en") {
        if (language == "ml") {
            tts.language = Locale.forLanguageTag("ml-IN")
        } else {
            tts.language = Locale.US
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }


    override fun onDestroy()
    {
        tts.shutdown()
        super.onDestroy()
    }
}
