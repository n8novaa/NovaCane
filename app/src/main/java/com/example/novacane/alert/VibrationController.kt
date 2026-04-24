package com.example.novacane.alert


import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class VibrationController(private val vibrator: Vibrator) {

    fun vibrate(intensity: Int) {
        if (intensity == 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(200, intensity)
            )
        } else {
            vibrator.vibrate(200)
        }
    }
}