package com.example.novacane.alert

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class VibrationController(
    private val vibrator: Vibrator
) {

    private var isVibrating = false

    fun vibrateVeryClose() {
        if (isVibrating) return
        isVibrating = true

        val pattern = longArrayOf(0, 300, 100, 300, 100, 300)
        vibratePattern(pattern)
    }

    fun vibrateObstacle() {
        if (isVibrating) return
        isVibrating = true

        val pattern = longArrayOf(0, 150, 100, 150)
        vibratePattern(pattern)
    }

    fun vibrateSOS() {
        if (isVibrating) return
        isVibrating = true

        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
        vibratePattern(pattern)
    }

    fun stop() {
        vibrator.cancel()
        isVibrating = false
    }

    private fun vibratePattern(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            vibrator.vibrate(pattern, -1)
        }

        // Reset flag after pattern duration (approx)
        // Prevents permanent lock if stop() not called
        val totalDuration = pattern.sum()
        Thread {
            try {
                Thread.sleep(totalDuration)
            } catch (_: Exception) {}
            isVibrating = false
        }.start()
    }
}