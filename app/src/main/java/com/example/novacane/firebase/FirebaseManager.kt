package com.example.novacane.firebase

import android.util.Log
import com.google.firebase.database.*

class FirebaseManager(private val db: DatabaseReference) {

    // 🔵 1. UPDATE LIVE SENSOR DATA
    fun updateLiveData(userId: String, distance: Int, alert: String) {

        val data = mapOf(
            "distance" to distance,
            "alert" to alert,
            "timestamp" to System.currentTimeMillis()
        )

        Log.d("FIREBASE", "Updating liveData → $userId")

        db.child("users")
            .child(userId)
            .child("liveData")
            .setValue(data)
            .addOnSuccessListener {
                Log.d("FIREBASE", "✅ liveData updated")
            }
            .addOnFailureListener {
                Log.e("FIREBASE", "❌ liveData failed", it)
            }
    }

    // 🔴 2. SEND SOS (FAIL-SAFE)
    fun sendSOS(userId: String) {

        val data = mapOf(
            "active" to true,
            "latitude" to 0.0,
            "longitude" to 0.0,
            "timestamp" to System.currentTimeMillis()
        )

        Log.d("SOS", "Sending SOS → $userId")

        db.child("users")
            .child(userId)
            .child("sos")
            .setValue(data)
            .addOnSuccessListener {
                Log.d("SOS", "✅ SOS sent")
            }
            .addOnFailureListener {
                Log.e("SOS", "❌ SOS failed", it)
            }
    }

    // 🟡 3. UPDATE SOS LOCATION (ASYNC)
    fun updateSOSLocation(userId: String, lat: Double, lon: Double) {

        db.child("users")
            .child(userId)
            .child("sos")
            .updateChildren(
                mapOf(
                    "latitude" to lat,
                    "longitude" to lon
                )
            )

        Log.d("SOS", "📍 Location updated → $lat, $lon")
    }

    // 🟢 3. LISTEN ONLY TO SOS (GUARDIAN SIDE)
    fun listenToSOS(
        userId: String,
        onSOSChange: (Boolean, Double?, Double?) -> Unit
    ) {
        val ref = db.child("users").child(userId).child("sos")

        ref.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val active = snapshot.child("active").getValue(Boolean::class.java) ?: false
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lon = snapshot.child("longitude").getValue(Double::class.java)

                Log.d("SOS_LISTENER", "Active: $active, Lat: $lat, Lon: $lon")

                onSOSChange(active, lat, lon)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SOS_LISTENER", "❌ Error: ${error.message}")
            }
        })
    }

    fun saveGuardianToken(guardianId: String, token: String) {

        db.child("guardians")
            .child(guardianId)
            .child("fcmToken")
            .setValue(token)
            .addOnSuccessListener {
                Log.d("FCM", "✅ Token saved")
            }
            .addOnFailureListener {
                Log.e("FCM", "❌ Token save failed", it)
            }
    }

    fun markSafe(userId: String) {

        db.child("users")
            .child(userId)
            .child("sos")
            .child("active")
            .setValue(false)
            .addOnSuccessListener {
                Log.d("SOS", "✅ Marked as SAFE")
            }
            .addOnFailureListener {
                Log.e("SOS", "❌ Failed to mark safe", it)
            }
    }
}