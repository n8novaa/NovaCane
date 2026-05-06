# NovaCane — Complete Team Project Report
### Smart Assistive Navigation System for the Visually Impaired
**Kerala Technological University (KTU) | B.Tech Final Year Project**

---

## 1. What Is NovaCane?

NovaCane is a **real IoT product** — not a simulation. It is an Android application that works together with a physical **ESP32-based smart cane** to help visually impaired users navigate safely. The cane has an ultrasonic distance sensor attached to it. When the sensor detects an obstacle, it sends the distance reading to the Android phone over **Bluetooth**. The phone app processes this data, alerts the user via **voice (TTS)** and **vibration**, and can also send emergency **SOS alerts** to a guardian's phone anywhere in the world via the internet (Firebase + FCM).

The project has two distinct personas baked into a single APK:
- **The User** — the visually impaired person carrying the cane.
- **The Guardian** — a trusted person (family/caretaker) who monitors the user remotely.

---

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Hardware Bridge | Bluetooth Classic SPP (RFCOMM) — UUID `00001101-0000-1000-8000-00805F9B34FB` |
| Cloud Database | Firebase Realtime Database |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Server-side Logic | Firebase Cloud Functions (Node.js) |
| Location | Google Fused Location Provider API |
| Text-to-Speech | Android `TextToSpeech` API (English + Malayalam) |
| Vibration | `VibrationEffect` API (Android 8+), legacy fallback |
| Build System | Gradle with Kotlin DSL |
| Min SDK | 24 (Android 7.0) — Target SDK 35 |

---

## 3. Project Structure (All Files Explained)

```
novacane/
├── app/src/main/java/com/example/novacane/
│   ├── MainActivity.kt              — App entry point, permission handling, DI wiring
│   ├── bluetooth/
│   │   └── BluetoothService.kt      — Bluetooth connect/read/reconnect state machine
│   ├── core/
│   │   └── AppState.kt              — Global mode tracker (USER / GUARDIAN / NONE)
│   ├── firebase/
│   │   ├── FirebaseManager.kt       — All Firebase read/write operations
│   │   └── MyFirebaseMessagingService.kt — FCM notification receiver + deep-link intent
│   ├── sensor/
│   │   ├── SensorData.kt            — Sealed class: Distance | SOS | Invalid
│   │   ├── MessageParser.kt         — Parses raw Bluetooth string → SensorData
│   │   └── SensorProcessor.kt       — ⚠️ Zone logic helper (UNUSED — see bugs)
│   ├── tts/
│   │   └── TTSManager.kt            — Speaks text in English or Malayalam
│   ├── alert/
│   │   ├── VibrationController.kt   — 3 vibration patterns: obstacle, very close, SOS
│   │   └── AlertManager.kt          — ⚠️ EMPTY STUB (package declaration only)
│   ├── location/
│   │   └── LocationManager.kt       — One-shot GPS fetch via FusedLocationProvider
│   ├── viewmodel/
│   │   ├── MainViewModel.kt         — Core logic: BT, SOS, TTS, vibration, location
│   │   └── GuardianViewModel.kt     — Guardian: SOS listener, history, safe marking
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── MainScreen.kt        — Navigation hub (HOME / USER / GUARDIAN)
│   │   │   ├── UserScreen.kt        — User's live obstacle dashboard + SOS button
│   │   │   └── GuardianScreen.kt    — Guardian monitoring dashboard + alarm UI
│   │   ├── components/
│   │   │   ├── AlertBanner.kt       — ⚠️ EMPTY STUB (package declaration only)
│   │   │   └── StatusCard.kt        — ⚠️ EMPTY STUB (package declaration only)
│   │   └── theme/                   — Material 3 theme
│   └── utils/                       — Empty (reserved)
├── functions/
│   └── index.js                     — Firebase Cloud Function: sendSOSNotification
└── firebase.json / .firebaserc       — Firebase project config
```

---

## 4. What Was Built — Feature by Feature

### 4.1 Bluetooth Communication Layer (`BluetoothService.kt`)

This is the most technically complex piece of the project. The ESP32 cane pairs with the phone and communicates over **Serial Port Profile (SPP)** Bluetooth Classic.

**What it does:**
- Searches the Android device's paired/bonded Bluetooth devices for one named `"NovaCane_ESP32"`.
- Opens an RFCOMM socket and starts reading from the input stream in a background thread.
- Handles **partial reads** correctly — Bluetooth can deliver data in chunks that don't align with message boundaries. The service accumulates data and splits on `\n` (newline) to extract complete messages.
- Exposes a `StateFlow<ConnectionState>` with 5 states: `CONNECTING → CONNECTED → RECONNECTING → DISCONNECTED → FAILED`.
- On drop, automatically enters reconnection loop retrying every **3 seconds** using `@Volatile shouldReconnect` flag to prevent race conditions between the reconnect loop and manual disconnect calls.

**Data format from ESP32:**
- Normal distance: a plain integer string like `"45\n"` (distance in cm).
- SOS trigger (button on cane pressed): the string `"SOS\n"`.

### 4.2 Message Parsing (`MessageParser.kt` + `SensorData.kt`)

`MessageParser.parse(message)` takes a raw cleaned string and returns a `SensorData` sealed class:
- `SensorData.SOS` — if message contains "SOS" (case-insensitive).
- `SensorData.Distance(value)` — if it parses to an integer between 0–400.
- `SensorData.Invalid` — anything else (noise, garbage).

The 0–400 cm range acts as a sanity filter to discard corrupted reads.

### 4.3 Obstacle Detection and User Feedback (`MainViewModel.kt`)

The ViewModel processes `SensorData.Distance` and maps it to 3 zones:

| Zone | Distance | Alert Text | TTS | Vibration |
|---|---|---|---|---|
| DANGER (0) | < 30 cm | "Very Close" | Malayalam: "മുന്നിൽ തടസം വളരെ അടുത്താണ്" | Long triple pulse |
| WARNING (1) | 30–70 cm | "Obstacle Ahead" | English: "Obstacle ahead" | Short double pulse |
| SAFE (2) | > 70 cm | "Path Clear" | Silent | None |

Zone changes are **edge-triggered** — TTS and vibration only fire when transitioning from one zone to another (not on every sensor reading). A `3-second speech cooldown` prevents repeat announcements on the same zone.

Every distance reading is also pushed to Firebase Realtime Database at `users/cane_test/liveData` so the guardian can see that the user is active.

### 4.4 SOS System — User Side

**Hardware-triggered SOS (cane button):** Two-press confirmation with a 3-second window:
1. First press → TTS says "Press again to confirm emergency". Single pulse vibration.
2. Second press within 3 seconds → SOS fires.
3. If second press is after 3 seconds → restart the window.

**Manual SOS (in-app button):** The large red "SOS" button in `UserScreen` calls `viewModel.triggerSOS()` directly (bypasses the two-press confirmation — intentional for in-app use).

When SOS fires:
- TTS: "Emergency alert sent"
- Vibration: long SOS pattern
- Firebase: sets `users/cane_test/sos/active = true` + current GPS coordinates + timestamp
- A separate listener in `MainViewModel` watches for the guardian to mark safe (`active = false`) and plays a "Guardian confirmed. Help is on the way" message when that happens.

### 4.5 Firebase Cloud Function (`functions/index.js`)

A Node.js Firebase Cloud Function `sendSOSNotification` listens on the path `/users/{userId}/sos/active`. It fires **only** when the value transitions `false → true` (edge-triggered):

```javascript
if (!before && after === true) { ... }
```

It then reads the guardian's FCM token from `/guardians/guardian_001/fcmToken`, and sends a push notification via the Firebase Admin SDK to the guardian's device. The notification wakes the guardian's phone even when the app is closed.

### 4.6 FCM Notification → Guardian Deep-Link (`MyFirebaseMessagingService.kt`)

When the guardian's phone receives the FCM push:
- `MyFirebaseMessagingService.onMessageReceived()` fires.
- It builds a notification with a `PendingIntent` pointing to `MainActivity` with extra `open_guardian = true`.
- Tapping the notification opens the app directly into Guardian Mode.
- `MainActivity.onNewIntent()` handles the case where the app is already open.
- `LaunchedEffect(openGuardian)` in `MainScreen` routes to `Screen.GUARDIAN` automatically.

### 4.7 Guardian Mode (`GuardianScreen.kt` + `GuardianViewModel.kt`)

The Guardian screen is a dark glassmorphism-style monitoring dashboard:

- **User Status card** — shows "Active" (green) or "Inactive" (gray) based on how recently liveData was updated in Firebase (< 5 seconds = Active). Shows "Last Seen: X seconds ago".
- **SOS Alert card** — When SOS is active, the card **blinks** red at 500ms intervals. A looping audio alarm (`R.raw.sos_alarm`) plays automatically.
- **Track User button** — Fetches latest location from `users/cane_test/liveLocation` and opens it in Google Maps via `Intent.ACTION_VIEW` with a `geo:` URI.
- **Mark as Safe button** — Only appears during active SOS. Sets `sos/active = false` in Firebase, which triggers the user-side "safe" TTS.
- **SOS History** — Displays last 5 SOS events (timestamp + coordinates) fetched from `users/cane_test/sosHistory`.

### 4.8 Location Tracking (`LocationManager.kt`)

One-shot GPS acquisition using `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`. Triggered:
1. At startup of User Mode.
2. Again when SOS is sent.

Location is written to `users/cane_test/liveLocation` (lat, lon, timestamp).

### 4.9 UI and Navigation (`MainScreen.kt`)

Simple in-compose navigation using a `var currentScreen by remember { mutableStateOf(Screen.HOME) }` — no NavGraph/Navigation Component used. Three states: `HOME`, `USER`, `GUARDIAN`.

- Back button on `HOME` uses double-press-to-exit (2-second window).
- `DisposableEffect` on `Screen.USER` calls `stopUserMode()` when the user navigates back, disconnecting Bluetooth.

---

## 5. Data Flow Diagram

```
User presses cane button
        │
        ▼
ESP32 sends "SOS\n" over Bluetooth RFCOMM
        │
        ▼
BluetoothService reads + parses → SensorData.SOS
        │
        ▼
MainViewModel.processSOS() → 2-press confirmation
        │
        ▼
handleSOS() → TTS + Vibration + Firebase write
        │
        ▼
Firebase RTDB: users/cane_test/sos/active = true
        │
        ▼
Cloud Function triggers (false → true edge)
        │
        ▼
Reads guardians/guardian_001/fcmToken
        │
        ▼
admin.messaging().send() → FCM push
        │
        ▼
Guardian phone receives notification
        │
        ▼
Tap → MainActivity (open_guardian=true) → GuardianScreen
        │
        ▼
Guardian taps "Mark as Safe"
        │
        ▼
Firebase: sos/active = false
        │
        ▼
User phone detects change → TTS: "Guardian confirmed"
```

---

## 6. Known Bugs (Worth Mentioning)

### 🔴 Bug 1 — FCM Token Key Mismatch (Critical — Will Break SOS Notification)

This is the most impactful live bug in the project.

`FirebaseManager.saveGuardianToken()` saves to the key `"token"`:
```kotlin
db.child("guardians").child(guardianId).child("token").setValue(token)
```

But the Cloud Function reads from `"fcmToken"`:
```javascript
db.ref("/guardians/guardian_001/fcmToken").once("value")
```

Since the key never matches, `tokenSnapshot.val()` is always `null`, and the function returns `null` without sending the notification. **The SOS push to the guardian's phone will silently fail.** Fix: make both use the same key, either `"token"` or `"fcmToken"`.

### 🟡 Bug 2 — `TTSManager.stop()` is a Silent No-Op

```kotlin
private var textToSpeech: TextToSpeech? = null  // never assigned

fun stop() {
    textToSpeech?.stop()  // always null → always does nothing
}
```

The actual TTS engine is the variable `tts` (not `textToSpeech`). So calling `stopUserMode()` → `ttsManager.stop()` does nothing. If an announcement is in progress when the user leaves User Mode, it will keep speaking.

### 🟡 Bug 3 — `startUserMode` Called Twice

In `MainActivity.startSystem()`, `viewModel.startUserMode("cane_test")` is called. Then in `MainScreen`, navigating to `Screen.USER` triggers a `LaunchedEffect` that calls `viewModel.startUserMode("cane_test")` again. The guard `if (isUserModeActive) return` prevents double-initialization, but it's a structural issue and can confuse someone reading the code.

---

## 7. Code Quality Issues (Not Bugs, But Worth Knowing)

### ⚠️ Issue 1 — `SensorProcessor.kt` Is Dead Code

`SensorProcessor` has correct zone logic (`DANGER/WARNING/SAFE` enum + `process()` returning a `Triple`) but it is **never instantiated or called** anywhere. `MainViewModel` duplicates the same zone logic inline with raw integer comparisons. The abstraction was designed but never wired up.

### ⚠️ Issue 2 — Empty Stub Files

Three files exist with only their package declaration (46–61 bytes each):
- `AlertManager.kt` — was meant to be an alert coordination layer.
- `AlertBanner.kt` — was meant to be a reusable Compose component.
- `StatusCard.kt` — was meant to be a reusable Compose component.

These represent architectural intent that was never implemented. They won't cause crashes but may invite questions during evaluation.

### ⚠️ Issue 3 — `AppState` Global Mutable Singleton

```kotlin
object AppState {
    var currentMode: String = "NONE"
}
```

This is a plain Kotlin `object` with a mutable `var`. Problems:
- Not a `StateFlow`, so it can't be observed reactively.
- Not persisted — clears on process death.
- Creates hidden coupling between `MainScreen` and `MainViewModel`.
- For a prototype this is acceptable, but a proper solution would use a `StateFlow` in the ViewModel.

### ⚠️ Issue 4 — Hard-coded IDs Everywhere

`cane_test` and `guardian_001` appear as string literals in multiple files:
- `MainActivity.kt` (line 36, 157)
- `MainViewModel.kt` (line 371)
- `GuardianViewModel.kt` (line 19)
- `UserScreen.kt` (line 90)
- `MainScreen.kt` (line 179)
- `functions/index.js` (line 22)

In a production system, these would come from authentication, QR pairing, or user preferences.

### ⚠️ Issue 5 — No UI Feedback on Permission Denial

If the user denies Bluetooth/Location permissions, `onRequestPermissionsResult` only logs `"Permission denied"`. There is no in-app guidance, Snackbar, or settings redirect. The app will appear to do nothing.

### ⚠️ Issue 6 — No Firebase Security Rules

The Firebase Realtime Database is likely open (`".read": true, ".write": true`). Any person who knows the project's Firebase URL can read or overwrite SOS data. There is no user authentication in the app.

---

## 8. Limitations of the System

These are architectural limitations of the prototype — not bugs, but honest constraints:

| Limitation | Details |
|---|---|
| **Single cane + single guardian** | Everything is hard-coded to one cane ID and one guardian ID. There is no multi-user or multi-device support. |
| **No user authentication** | No Firebase Auth. Anyone with the app can access any data. |
| **One-shot location** | GPS is fetched once at startup and once on SOS. There is no continuous live tracking of the user's movement. |
| **Bluetooth range** | Standard BT Classic range is ~10 meters. If the user's phone is in a bag far from the cane, connection may be unreliable. |
| **No offline capability** | If there is no internet, the SOS notification to the guardian will not work. Bluetooth obstacle detection still works offline. |
| **Battery drain** | High-accuracy GPS, active Bluetooth, and a polling Firebase listener running simultaneously will accelerate battery drain on both devices. No battery optimization has been implemented. |
| **No Android Doze mode handling** | Background services may be throttled by Android Doze. The app has not been tested for long-running background behavior. |
| **SOS history not written** | `GuardianViewModel` listens to `sosHistory` in Firebase, but `FirebaseManager` never writes to it. The history list will always be empty. |
| **Bluetooth Classic only** | BLE (Bluetooth Low Energy) would be more power-efficient and have better range characteristics for wearables, but SPP/RFCOMM (Classic) was used for simplicity. |
| **Malayalam TTS device-dependent** | `Locale.forLanguageTag("ml-IN")` requires the Malayalam TTS engine to be installed on the device. On many phones it won't be available by default. |

---

## 9. What Was NOT Built (Gaps vs Original Intent)

Based on the empty stubs and commented intent in the code:

- **`AlertBanner` component** — intended as a reusable floating alert banner, was never implemented. Guardian and User screens use inline `Box` layouts instead.
- **`StatusCard` component** — intended as a reusable status indicator card. Not implemented.
- **`AlertManager`** — intended to centralize all alert dispatching (TTS + vibration coordination). Never implemented; alert logic lives directly in `MainViewModel`.
- **`/model` package** — exists but is completely empty. Was likely intended for data classes shared across modules.
- **`/utils` package** — exists but is empty. May have been planned for extension functions or helpers.
- **Continuous location tracking** — The Firebase schema has `liveLocation`, but location is fetched once on startup, not on a timer. A guardian cannot watch the user move in real time.
- **SOS History writing** — The Guardian screen reads `sosHistory` from Firebase, but no code ever writes to it. The list will always render empty.

---

## 10. What the Project Does Well

- **Real hardware integration** — Not a mock. An actual ESP32 sends real sensor data.
- **Proper MVVM** — ViewModels hold all state and logic. Screens are pure state-collectors.
- **Resilient Bluetooth** — The reconnection loop with `@Volatile` flag is race-condition aware and professionally implemented.
- **Edge-triggered SOS** — Both the cane button and the Firebase Cloud Function are edge-triggered, preventing duplicate triggers.
- **Dual-mode in one APK** — User and Guardian experiences are cleanly separated with independent ViewModels.
- **Bilingual TTS** — Malayalam localization is a culturally thoughtful addition relevant to the Kerala deployment context.
- **Safety UX** — Double-press confirmation for cane button SOS mirrors commercial emergency app patterns.
- **Complete notification deep-link flow** — FCM → Android notification → tap → `onNewIntent` → Guardian screen is correctly implemented end-to-end.

---

## 11. Viva Q&A Preparation

| Question | Answer |
|---|---|
| What does NovaCane do? | It's a smart cane system for visually impaired users. An ESP32 on the cane measures distance with an ultrasonic sensor and sends data over Bluetooth to the phone. The phone speaks alerts and vibrates. If there's an emergency, it sends a push notification to a guardian anywhere. |
| Why Bluetooth Classic and not BLE? | SPP over RFCOMM provides a simple serial stream interface, similar to UART, which is easy to interface with on the ESP32 side using existing Arduino libraries. BLE would require a custom GATT profile and is more complex to implement. |
| How does Bluetooth data arrive correctly if TCP has no message boundaries? | Bluetooth RFCOMM can also deliver partial data. We accumulate data in a string buffer and split on newline characters. Only complete messages (before the last newline) are dispatched. |
| How is the SOS false alarm prevented? | The cane button requires two presses within 3 seconds. A single press only prompts a warning TTS. The 3-second window is tracked in `MainViewModel` and cleaned up by the timeout monitor coroutine. |
| Why Firebase Realtime DB and not Firestore? | RTDB has lower latency for streaming use cases. SOS and live sensor data require near-instant delivery. Firestore is better for complex queries and structured data. |
| How does the guardian get notified? | A Firebase Cloud Function watches the SOS path. When it changes from false to true, it reads the guardian's FCM token from the database and sends a push via the Firebase Admin SDK. The notification has a PendingIntent that opens Guardian Mode directly. |
| What is a StateFlow and why use it? | StateFlow is a Kotlin coroutines observable state holder that is lifecycle-aware. It replaces LiveData for Compose. The UI collects it with `collectAsState()` and recomposes only when the value changes. |
| What happens if Bluetooth disconnects mid-walk? | BluetoothService enters RECONNECTING state and retries every 3 seconds. MainViewModel's timeout monitor detects no sensor data after 3 seconds and shows "Sensor not responding" on screen and speaks the alert. |
| What are the biggest limitations? | Single hard-coded cane/guardian pair, no authentication, no Firebase Security Rules, one-shot GPS (no continuous tracking), SOS history is never written, Malayalam TTS is device-dependent. |
| What's the FCM token bug? | The app saves the guardian's token to `"token"` in Firebase but the Cloud Function reads from `"fcmToken"`. They never match so the push notification never reaches the guardian. Fix: use the same key in both places. |
| How does the guardian see the user's location? | The app fetches the user's GPS coordinates using Google's Fused Location API and stores them in Firebase. The guardian taps "Track User" which fetches this stored location and opens it in Google Maps via a geo URI intent. |
| What would a production version add? | Firebase Authentication, dynamic cane-guardian pairing via QR code, continuous GPS tracking, Firebase Security Rules, offline persistence, battery optimization, BLE migration, a web dashboard for guardians. |

---

## 12. Summary

NovaCane is a technically complete and socially meaningful B.Tech final year project. It successfully integrates hardware (ESP32 + ultrasonic sensor), Bluetooth communication, a reactive Android UI with clean MVVM, real-time Firebase cloud sync, server-side Cloud Functions, push notifications, GPS, bilingual TTS, and structured haptic feedback into a working assistive navigation product.

**The critical issue to fix before demo:** The FCM token key mismatch (`"token"` vs `"fcmToken"`) will silently break the guardian push notification — the most visible feature of the system. This is a one-line fix.

**Secondary issues to be aware of:** `TTSManager.stop()` is a no-op (phantom field bug), SOS history is never written despite the UI showing it, and three files are empty stubs.

**The project is well above the typical B.Tech prototype** in terms of architectural rigor, real hardware integration, and production-level patterns used throughout the codebase.
