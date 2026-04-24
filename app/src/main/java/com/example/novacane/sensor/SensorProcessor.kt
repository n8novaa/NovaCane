package com.example.novacane.sensor

enum class AlertZone {
    SAFE,
    WARNING,
    DANGER
}

class SensorProcessor {

    fun getZone(distance: Int): AlertZone {
        return when {
            distance < 30 -> AlertZone.DANGER
            distance < 70 -> AlertZone.WARNING
            else -> AlertZone.SAFE
        }
    }

    fun process(distance: Int): Triple<String, Int, AlertZone> {

        val zone = getZone(distance)

        return when (zone) {
            AlertZone.DANGER -> Triple(
                "Very close obstacle",
                255,
                zone
            )

            AlertZone.WARNING -> Triple(
                "Obstacle ahead",
                150,
                zone
            )

            AlertZone.SAFE -> Triple(
                "Path clear",
                0,
                zone
            )
        }
    }
}