package com.example.novacane.alert

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class VibrationController(
    private val vibrator: Vibrator
) {

    fun vibrateVeryClose() {
        val pattern = longArrayOf(0, 300, 100, 300, 100, 300)
        vibratePattern(pattern)
    }

    fun vibrateObstacle() {
        val pattern = longArrayOf(0, 150, 100, 150)
        vibratePattern(pattern)
    }

    fun vibrateSOS() {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
        vibratePattern(pattern)
    }

    private fun vibratePattern(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }
}