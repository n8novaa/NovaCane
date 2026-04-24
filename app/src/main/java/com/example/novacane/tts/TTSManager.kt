package com.example.novacane.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSManager(context: Context) {

    private var lastSpokenTime = 0L
    private val tts = TextToSpeech(context) {
        it == TextToSpeech.SUCCESS
    }

    fun speak(text: String, lang: String = "en") {
        val now = System.currentTimeMillis()
        if (now - lastSpokenTime < 3000) return
        lastSpokenTime = now

        if (lang == "ml")
            tts.language = Locale.forLanguageTag("ml-IN")
        else
            tts.language = Locale.US

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun shutdown() {
        tts.shutdown()
    }
}