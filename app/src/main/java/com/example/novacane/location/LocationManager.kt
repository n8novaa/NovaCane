package com.example.novacane.location

import android.annotation.SuppressLint
import android.location.Location
import com.google.android.gms.location.*

class LocationManager(
    private val fusedLocationClient: FusedLocationProviderClient
) {

    @SuppressLint("MissingPermission")
    fun getLocation(onResult: (Location?) -> Unit) {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).setMaxUpdates(1).build()

        fusedLocationClient.requestLocationUpdates(
            request,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    onResult(result.lastLocation)
                }
            },
            null
        )
    }
}