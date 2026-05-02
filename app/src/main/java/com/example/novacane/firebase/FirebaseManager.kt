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
    }

    // 🔴 2. SEND SOS
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
    }

    // 🟡 3. UPDATE SOS LOCATION
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

        Log.d("SOS", "📍 SOS location updated → $lat, $lon")
    }

    // 🟣 4. UPDATE LIVE LOCATION (ON-DEMAND TRACKING SUPPORT)
    fun updateLiveLocation(userId: String, lat: Double, lon: Double) {

        val data = mapOf(
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to System.currentTimeMillis()
        )

        Log.d("LOCATION", "Updating liveLocation → $lat, $lon")

        db.child("users")
            .child(userId)
            .child("liveLocation")
            .setValue(data)
    }

    // 🟢 5. LISTEN TO SOS (EDGE-TRIGGERED)
    fun listenToSOS(
        userId: String,
        onSOSTriggered: (Double?, Double?) -> Unit
    ) {

        val ref = db.child("users").child(userId).child("sos")

        var lastState = false

        ref.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val active = snapshot.child("active").getValue(Boolean::class.java) ?: false
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lon = snapshot.child("longitude").getValue(Double::class.java)

                Log.d("SOS_LISTENER", "Active: $active, Lat: $lat, Lon: $lon")

                if (active && !lastState) {
                    Log.d("SOS_LISTENER", "🚨 NEW SOS TRIGGERED")
                    onSOSTriggered(lat, lon)
                }

                lastState = active
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SOS_LISTENER", "❌ Error: ${error.message}")
            }
        })
    }

    // 🟣 6. SAVE GUARDIAN TOKEN
    fun saveGuardianToken(guardianId: String, token: String) {

        Log.d("FCM", "Saving token for $guardianId")

        db.child("guardians")
            .child(guardianId)
            .child("token") // ✅ consistent naming
            .setValue(token)
    }

    // 🟢 7. MARK SAFE
    fun markSafe(userId: String) {

        db.child("users")
            .child(userId)
            .child("sos")
            .child("active")
            .setValue(false)

        Log.d("SOS", "✅ Marked as SAFE")
    }
}