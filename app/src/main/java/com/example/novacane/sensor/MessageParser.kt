package com.example.novacane.sensor



object MessageParser {

    fun parse(message: String): SensorData {

        val clean = message.trim()

        // 🔴 ROBUST SOS DETECTION (FIX)
        if (clean.equals("SOS", ignoreCase = true) ||
            clean.startsWith("SOS", ignoreCase = true) ||
            clean.contains("SOS", ignoreCase = true)
        ) {
            return SensorData.SOS
        }

        // 🔵 DISTANCE PARSING (STRICT)
        val distance = clean.toIntOrNull()
        if (distance != null && distance in 0..400) {
            return SensorData.Distance(distance)
        }

        return SensorData.Invalid
    }
}