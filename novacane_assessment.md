# NovaCane — B.Tech Final Year Project Assessment
### Kerala Technological University (KTU) | Evaluator Report

---

## Project Overview

**NovaCane** is an IoT-integrated Android application that serves as a **smart assistive navigation system for visually impaired individuals**. The app communicates with a custom ESP32-based smart cane over Bluetooth Classic (SPP), processes real-time ultrasonic sensor data, and relays emergency alerts to a guardian's phone via Firebase Cloud Messaging (FCM). The app is built in **Kotlin** using **Jetpack Compose**, follows **MVVM architecture**, and integrates **Firebase Realtime Database**, **FCM**, and **Google Play Location Services**.

---

## Technical Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Hardware Bridge | Bluetooth Classic SPP (RFCOMM) |
| Cloud Backend | Firebase Realtime Database |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Server-side Logic | Firebase Cloud Functions (Node.js) |
| Location | Google Fused Location Provider API |
| TTS | Android TextToSpeech (English + Malayalam) |
| Feedback | VibrationEffect API |

---

## Scores by Category

| Criteria | Score | Max |
|---|---|---|
| Architecture & Code Quality | 17 | 20 |
| Feature Completeness | 18 | 20 |
| Complexity & Innovation | 17 | 20 |
| Hardware Integration | 18 | 20 |
| Real-world Applicability | 9 | 10 |
| Documentation & Code Clarity | 7 | 10 |
| **Total** | **86** | **100** |

---

## Detailed Evaluation

### ✅ Strengths

#### 1. Well-Structured MVVM Architecture
The codebase cleanly separates concerns:
- `MainViewModel` handles the full user-mode lifecycle (Bluetooth, SOS, location, FCM).
- `GuardianViewModel` independently handles the guardian-side listening, SOS history, and safe marking.
- `FirebaseManager` is a proper repository-layer abstraction over the database.
- Screens only collect state; they don't contain business logic.

This is a graduate-level architecture choice and demonstrates solid understanding of Android development principles.

#### 2. Real Hardware + Real Cloud Integration
This is not a simulated project. The cane communicates over Bluetooth RFCOMM using the SPP UUID. The `BluetoothService` implements:
- Auto-reconnection with a controlled loop and a `@Volatile shouldReconnect` flag (race-condition aware).
- Message accumulation and newline-delimited parsing — correctly handles partial Bluetooth reads.
- State machine with `ConnectionState` (CONNECTING → CONNECTED → RECONNECTING → DISCONNECTED → FAILED).

This hardware-software bridge is non-trivial and professionally implemented.

#### 3. Dual-Mode Design (User + Guardian)
The app correctly serves two user personas in a single APK:
- **User Mode**: Real-time obstacle detection, TTS in English and Malayalam, vibration feedback, manual SOS.
- **Guardian Mode**: Live SOS listener, blinking alert UI, audible alarm, location map deep-link, SOS history log.

The `FCM → Notification → PendingIntent → openGuardian=true → LaunchedEffect` flow is correctly implemented end-to-end, including `onNewIntent` handling for deep navigation.

#### 4. Bilingual TTS (Malayalam Support)
The use of `Locale.forLanguageTag("ml-IN")` for localized obstacle announcements is a thoughtful, culturally relevant design decision, appropriate for the Kerala user base. This demonstrates domain awareness.

#### 5. SOS Safety Mechanisms
The double-press confirmation window (`CONFIRM_WINDOW = 3000ms`) prevents accidental SOS triggers — a real UX safety pattern used in commercial emergency apps. The edge-triggered Firebase listener (`lastState` tracking) correctly avoids re-triggering on reconnects.

#### 6. Firebase Cloud Function (Server-side Trigger)
The `sendSOSNotification` Cloud Function is a correctly implemented edge-trigger:
```javascript
if (!before && after === true) { ... }
```
It only fires when SOS transitions `false → true`, preventing spam. It reads the guardian FCM token from a separate path and sends via `admin.messaging().send()`. This is production-level backend logic.

---

### ⚠️ Areas for Improvement

#### 1. `SensorProcessor` is Unused Dead Code
`SensorProcessor.kt` defines `getZone()` and `process()` correctly, but `MainViewModel` re-implements the same zone logic inline. The abstraction was created but never wired up — a missed opportunity for cleaner code.

```kotlin
// In MainViewModel — duplicated from SensorProcessor
val newZone = when {
    distance < 30 -> 0
    distance < 70 -> 1
    else -> 2
}
```

#### 2. TTSManager Has a Phantom `textToSpeech` Field
```kotlin
private var textToSpeech: TextToSpeech? = null  // never assigned

fun stop() {
    textToSpeech?.stop()  // always a no-op
}
```
The `stop()` function will silently do nothing. The active TTS engine is `tts`, not `textToSpeech`. This means stopping TTS on `stopUserMode()` is broken.

#### 3. `AlertManager.kt` and `StatusCard.kt` / `AlertBanner.kt` are Empty Stubs
These files exist with 46–61 bytes each (likely just the package declaration). They represent architectural intent that was never implemented. Stub files in a final prototype can raise questions during viva.

#### 4. `AppState` is a Global Mutable Singleton
```kotlin
object AppState {
    var currentMode: String = "NONE"
}
```
Sharing mode state via a global object is fragile — it doesn't survive process death, is not observable (not a `StateFlow`), and creates an implicit dependency between `MainScreen` and `MainViewModel`. In a prototype this is acceptable, but worth flagging.

#### 5. FCM Token Key Inconsistency
`FirebaseManager.saveGuardianToken()` saves to `.child("token")` but the Cloud Function reads from `.child("fcmToken")`. If both run, the notification will silently fail because the token is stored at the wrong path.

```kotlin
// FirebaseManager.kt (saves to "token")
db.child("guardians").child(guardianId).child("token").setValue(token)

// index.js (reads from "fcmToken")
db.ref("/guardians/guardian_001/fcmToken").once("value")
```
> **This is a live bug** that would cause the SOS push notification to fail in production.

#### 6. Hard-coded `caneID = "cane_test"` and `guardianId = "guardian_001"`
The cane ID and guardian ID are magic strings baked into the code. For a prototype this is expected, but you should be prepared to explain the multi-user/multi-cane scaling path during viva.

#### 7. Permission Handling — No Partial-Grant Feedback
If the user denies a permission, `startSystem()` is never called and the app silently does nothing (only logs `"Permission denied"`). There's no in-app UI to guide the user to grant permissions.

#### 8. `startUserMode` is Called Twice
In `onCreate`, `startSystem()` calls `viewModel.startUserMode("cane_test")`. Then in `MainScreen`, when navigating to `Screen.USER`, it calls `viewModel.startUserMode("cane_test")` again in a `LaunchedEffect`. The guard `if (isUserModeActive) return` prevents a true double-init, but this is a structural issue worth cleaning up.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│                  Android App (NovaCane)              │
│                                                     │
│  ┌──────────┐   ┌──────────────┐  ┌──────────────┐ │
│  │MainScreen│   │  UserScreen  │  │GuardianScreen│ │
│  └────┬─────┘   └──────┬───────┘  └──────┬───────┘ │
│       │                │                 │          │
│  ┌────▼────────────────▼──┐  ┌───────────▼────────┐ │
│  │     MainViewModel       │  │  GuardianViewModel │ │
│  └─┬──────┬──────┬────────┘  └──────┬─────────────┘ │
│    │      │      │                  │               │
│  BT   Firebase  TTS/Vibration    Firebase           │
│  Svc   Mgr     Controllers        Mgr               │
└──┼──────┼───────────────────────────┼───────────────┘
   │      │                           │
   ▼      ▼                           ▼
ESP32  Firebase Realtime DB  Firebase Cloud Functions
Cane   (liveData, sos,       (sendSOSNotification)
       liveLocation,                  │
       sosHistory)                    ▼
                               FCM → Guardian Phone
```

---

## Viva Preparation — Likely Questions

| Question | Suggested Answer |
|---|---|
| Why MVVM and not MVC? | MVVM separates UI state (ViewModel/StateFlow) from the View, enabling testability and lifecycle safety. MVC collapses these into the Activity. |
| How does Bluetooth data arrive? | The ESP32 sends newline-delimited strings over RFCOMM. `BluetoothService` reads in a loop, accumulates chunks, splits on `\n`, and dispatches complete messages. |
| What happens if Bluetooth drops? | `BluetoothService` enters a `RECONNECTING` state, retries every 3 seconds, and `startTimeoutMonitor()` in the ViewModel detects sensor silence after 3 seconds and updates the UI. |
| What prevents false SOS? | A 3-second double-press confirmation window. Single presses prompt "Press again to confirm emergency" via TTS. |
| Why Firebase Realtime DB over Firestore? | RTDB has lower latency for live streaming use cases (sensor updates, SOS events) and simpler listener semantics. Firestore is better for complex queries. |
| How does the Guardian get notified? | A Firebase Cloud Function listens on the SOS path, fetches the guardian's FCM token, and sends a push notification via Firebase Admin SDK. The notification has a PendingIntent that deep-links to Guardian Mode. |
| What are the limitations of this prototype? | Hard-coded cane/guardian IDs, single cane + guardian pair, no authentication, no Firebase Security Rules, no offline persistence, no battery optimization handling. |

---

## Summary Verdict

> [!IMPORTANT]
> **Recommendation: PASS with Distinction (conditional on fixing the FCM token key bug before demo)**

NovaCane is a **well-conceived, technically substantial final year project**. It integrates real hardware (ESP32 + ultrasonic sensor), a Bluetooth communication layer, a reactive Android frontend with proper MVVM, real-time cloud synchronization, push notifications via Cloud Functions, GPS tracking, bilingual TTS, and structured vibration feedback into a coherent, socially meaningful product for assistive technology.

The code quality is above the typical B.Tech prototype — the use of `StateFlow`, `sealed class`, `@Volatile`, `DisposableEffect`, and `LaunchedEffect` shows genuine understanding of Kotlin coroutines and Compose. The Cloud Function is correctly edge-triggered.

The critical FCM token path bug must be fixed before the demo. The unused `SensorProcessor` and the `TTSManager.stop()` no-op are code quality issues that an evaluator may probe during viva.

**This project is well-suited for future production integration** with minor architectural additions (authentication, multi-user support, Firebase Security Rules, production-grade UUID pairing).
