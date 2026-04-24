package com.example.novacane.sensor

sealed class SensorData {

    data class Distance(val value: Int) : SensorData()

    object SOS : SensorData()

    object Invalid : SensorData()
}