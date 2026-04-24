package com.example.novacane.viewmodel

import androidx.lifecycle.ViewModel
import com.example.novacane.firebase.FirebaseManager
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GuardianViewModel : ViewModel() {

    private val firebaseManager = FirebaseManager(
        FirebaseDatabase.getInstance().reference
    )

    private val _sosActive = MutableStateFlow(false)
    val sosActive: StateFlow<Boolean> = _sosActive

    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude: StateFlow<Double?> = _latitude

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude: StateFlow<Double?> = _longitude

    fun startListening() {
        firebaseManager.listenToSOS("cane_test") { active, lat, lon ->
            _sosActive.value = active
            _latitude.value = lat
            _longitude.value = lon
        }
    }

    fun markSafe() {
        firebaseManager.markSafe("cane_test")
    }
}