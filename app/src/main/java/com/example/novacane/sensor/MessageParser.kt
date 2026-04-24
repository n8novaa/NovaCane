package com.example.novacane.sensor

object MessageParser {

    fun parse(message: String): SensorData {

        val clean = message.trim()

        if (clean == "SOS") {
            return SensorData.SOS
        }

        if (clean.matches(Regex("\\d+"))) {
            val value = clean.toInt()

            if (value in 0..400) {
                return SensorData.Distance(value)
            }
        }

        return SensorData.Invalid
    }
}