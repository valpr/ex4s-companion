# Architecture Plan: Custom Echelon EX-4S App

## 1. System Overview
A bespoke, native Android application designed to directly control an Echelon EX-4S spin bike via Bluetooth Low Energy (BLE). The app will parse standard workout files, automatically adjust bike resistance, display a modern UI, and integrate seamlessly with the Pixel Watch and Google Health Connect ecosystem.

## 2. Technology Stack & Concurrency
* **Platform:** Native Android 14+ (Mobile) & Wear OS 4+ (Watch)
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose & Compose for Wear OS
* **Concurrency Engine:** Kotlin Coroutines & `StateFlow`/`SharedFlow`.
  * *Crucial Viability Note:* Hardware state (BLE, WearOS) must run on background dispatchers (`Dispatchers.IO`), exposing state via `StateFlow` to the UI thread.
* **Architecture Pattern:** MVVM (Model-View-ViewModel) + Foreground Service.

## 3. Core Modules & Implementation Details

### A. The BLE Hardware Layer (Echelon Service)

The EX-4S uses a **proprietary vendor BLE GATT profile** — not standard FTMS (`0x1826`), Cycling Speed & Cadence (`0x1816`), or Cycling Power (`0x1818`). All communication uses custom 128-bit UUIDs and framed byte packets.

**Reference Implementation:** [QZ (qdomyos-zwift) — `echelonconnectsport.cpp`](https://github.com/cagnulein/qdomyos-zwift/blob/master/src/devices/echelonconnectsport/echelonconnectsport.cpp)

#### A.1. GATT Service & Characteristic Map

| Role | UUID |
|:---|:---|
| Primary Service | `0bf669f1-45f2-11e7-9598-0800200c9a66` |
| Write Characteristic (App → Bike) | `0bf669f2-45f2-11e7-9598-0800200c9a66` |
| Notify Characteristic 1 | `0bf669f3-45f2-11e7-9598-0800200c9a66` |
| Notify Characteristic 2 (Bike → App telemetry) | `0bf669f4-45f2-11e7-9598-0800200c9a66` |

Both notify characteristics must have their Client Characteristic Configuration Descriptor (CCCD) written with `0x01 0x00` to enable notifications.

#### A.2. Connection & Initialization Sequence

After BLE connect and service discovery, the app must send the following handshake to the write characteristic before the bike will emit telemetry:

```
Step 1 (×4):  0xF0, 0xA1, 0x00, 0x91
Step 2 (×1):  0xF0, 0xA3, 0x00, 0x93
Step 1 interlude (×1): 0xF0, 0xA1, 0x00, 0x91  ← QZ 7-step sequence
Step 3 (×1):  0xF0, 0xB0, 0x01, 0x01, 0xA2   ← sensor activation
```

If this handshake is not sent, the bike remains completely silent.

#### A.3. Keep-Alive Polling

A poll command must be sent every **2 seconds** to maintain the connection:
```
0xF0, 0xA0, 0x01, <counter>, <checksum>
```
`counter` is a rolling uint8 starting at 1 (wrapping, skipping 0).

#### A.4. Command Format: Set Resistance

The resistance level (1–32) is set electronically via:
```
0xF0, 0xB1, 0x01, <resistance_level>, <checksum>
```

#### A.5. Checksum Calculation

All commands use the same pattern: the final byte is the sum of all preceding bytes, truncated to uint8:
```kotlin
checksum = (bytes[0] + bytes[1] + ... + bytes[n-1]).toByte()
```

#### A.6. Telemetry Parsing (Bike → App Notifications)

* **Resistance Frame** — 5 bytes, opcode `0xD2`:
  * `resistance_level = byte[3]` (integer 1–32)

* **Cadence Frame** — 13 bytes, opcode `0xD1`:
  * `cadence_rpm = byte[10]` (uint8)
  * `elapsed_seconds = (byte[3] << 8) | byte[4]` (uint16 big-endian)
  * `distance = ((byte[7] << 8) | byte[8]) / 100.0` (uint16 big-endian, in km)

#### A.7. Power Estimation

The bike has **no strain-gauge power meter**. Power must be estimated using a lookup table mapping `(resistance_level, cadence_bucket) → watts` with linear interpolation between cadence buckets. QZ provides a full 33×11 watt table for this purpose (rows = resistance 0–32, columns = cadence buckets of 10 RPM from 0–100+). This table should be embedded in the app and is the authoritative source for estimated power.

#### A.8. BLE Command Queue

A standard Kotlin `Mutex` alone is **insufficient** because `gatt.writeCharacteristic()` is asynchronous — the mutex releases before the peripheral acknowledges via `onCharacteristicWrite()`. The queue must use:

1. A Coroutine `Channel<BleCommand>(UNLIMITED)` processed sequentially on `Dispatchers.IO`
2. A `CompletableDeferred<Int>` per write, completed by the `onCharacteristicWrite` callback
3. A `withTimeout(2500ms)` guard per write
4. A **40ms post-write cooldown** (`delay(40)`) to give the bike's MCU processing time
5. `gatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH)` immediately after service discovery

On API 33+, use the modern `gatt.writeCharacteristic(characteristic, bytes, writeType)` signature. On older APIs, use the deprecated `characteristic.value = bytes` + `gatt.writeCharacteristic(characteristic)` path.

#### A.9. Foreground Service & Permissions
 
The entire BLE connection manager *must* reside within an Android `ForegroundService` bound to an ongoing system notification.
 
**Android 14+ mandatory requirements (field-verified):**
* Declare `foregroundServiceType="connectedDevice"` in manifest (do **not** use `health` or `dataSync`):
  ```xml
  <service
      android:name=".service.WorkoutTrackingService"
      android:foregroundServiceType="connectedDevice"
      android:exported="false" />
  ```
  *(Note: Declaring `health` under targetSdk 34 throws a fatal `SecurityException` at runtime unless Google Health Connect permissions like `ACTIVITY_RECOGNITION` or `READ_HEART_RATE` are granted. `connectedDevice` is the designated type for BLE fitness equipment).*
* Declare permissions in manifest:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
  <uses-permission android:name="android.permission.BLUETOOTH_SCAN" tools:targetApi="s" />
  <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" tools:targetApi="s" />
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  ```
* **Android 12+ BLE Scanning Rule:** Omitting `neverForLocation` requires `ACCESS_FINE_LOCATION` to be granted at runtime, or Android's BLE subsystem silently drops all scan results. Both `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` and `ACCESS_FINE_LOCATION` must be granted.
* Ensure `BLUETOOTH_CONNECT` runtime permission is granted **before** calling `startForeground()`, or a `SecurityException` is thrown.
* Pass the type when starting:
  ```kotlin
  ServiceCompat.startForeground(
      this, NOTIFICATION_ID, notification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
  )
  ```
* **Do NOT use `foregroundServiceType="dataSync"`** — Android 15 enforces a 6-hour cumulative timeout on `dataSync` services.
* Always start the service from a foreground Activity (user taps "Start Ride"). Background launch throws `ForegroundServiceStartNotAllowedException`.

#### A.10. Operational Quirks

1. **Single BLE connection:** The bike allows only one BLE master. If the official Echelon Fit app is running (even backgrounded), it captures the connection exclusively. The app should detect connection failure and prompt the user to force-close Echelon Fit.
2. **Firmware lockdown risk:** Recent Echelon firmware updates (2024–2025) added server-backed challenge-response authentication that blocks third-party BLE access. Onboarding should warn users: **do not update the bike's firmware** via the official app.
3. **Bluetooth name length bug:** On some Android versions, connection fails if the advertised device name is too long. Workaround: rename the bike in system Bluetooth settings to a short string (e.g., `bike`).
4. **Reconnection & Idle Sleep:** Echelon bikes enter low-power sleep after ~60s of inactivity. On unexpected disconnect, the app must automatically transition back to background scanning so that pedaling 2–3 times immediately wakes the bike and re-establishes the connection. Save the last target resistance and re-apply it after the 7-step handshake completes (as QZ does).
5. **No System Bluetooth Pairing:** Do **not** pair the bike in Android system Bluetooth settings. Doing so locks the peripheral GATT connection and prevents the app from opening the required communication channel.

#### A.11. Device Discovery & Connection Lifecycle (The QZ Pattern)

To achieve zero-friction onboarding, the app adheres to the **QZ (qdomyos-zwift) connection model**:
* **Zero-Click Auto-Scan:** The moment the app launches or returns to the foreground (`onResume`), it starts BLE scanning in `ScanSettings.SCAN_MODE_LOW_LATENCY` if Bluetooth & Location permissions are granted.
* **Smart Device Recognition:** Advertisements are parsed for:
  1. Names matching: `ECH*`, `ECHELON*`, `SPORT*`, `EX-*`, `EX4*`, `EX5*`, `BIKE`
  2. The Echelon 128-bit Service UUID: `0bf669f1-45f2-11e7-9598-0800200c9a66`
  3. Scan response preservation (prevents `null`/empty advertisement names from overwriting previously resolved device names).
* **Auto-Connect:** As soon as a matching Echelon device is detected during scanning, the scanner is stopped, and `device.connectGatt(context, false, callback, TRANSPORT_LE)` is initiated immediately without requiring user modal interaction.
* **Auto-Recovery Loop:** If the bike connection is lost or drops due to GATT errors, the manager pauses 1.5–2.0s, resets state, and resumes low-latency scanning so user pedaling restores telemetry transparently.
* **Manual Override:** A dedicated scan dialog remains available in the UI for debugging RSSI signal strength, displaying raw MAC addresses, and manual target selection.

---

### B. The Workout Engine (State Machine & ERG Controller)

#### B.1. User Profile
The app requires a basic local datastore (DataStore/Room) to hold the user's **FTP** and **weight**. FTP is mandatory — ZWO power targets are FTP fractions (e.g., `Power="0.90"` = 90% of FTP). If FTP is unset, the app must block workout start with a configuration prompt.

#### B.2. ERG Controller: Feedforward + PI Trim

A pure PID controller operating on raw power error is **unsuitable** because:
* The Derivative term amplifies natural pedaling cadence oscillation (±3–5 RPM), causing resistance jitter ("servo hunting"). **D must be 0.**
* The mechanical servo has 1.5–3s latency; pure feedback will overshoot or lag.

**Recommended architecture:**
1. **Feedforward base resistance:** Invert the power/watt table → `R_nominal = wattTable.inverse(P_target, RPM_smoothed)`
2. **PI trim:** A small correction offset clamped to ±3 resistance levels
3. **Integral anti-windup:** Clamp accumulated integral term to `[-I_max, +I_max]`
4. **Cadence smoothing:** Feed the controller a 3-second EMA or moving median of cadence, not raw 1Hz readings
5. **Deadband:** If `|P_actual - P_target| < 5W`, issue no resistance write commands (saves BLE traffic and motor wear)

The Settings UI should expose **P and I gain** sliders (not D), plus the cadence floor threshold.

#### B.3. Anti-Spiral Mechanics
* **Cadence Floor:** If RPM < 60, **immediately** suspend ERG and drop resistance to a safe recovery level (e.g., level 8).
* **Recovery gate:** Do not re-engage ERG until cadence sustains > 75 RPM for **≥ 3 consecutive seconds** (prevents yo-yo re-engagement).
* **Dynamic HR Capping:** Scale target FTP multiplier down by 10% if WearOS signals critical HR.

---

### C. The Wearable Layer (Pixel Watch)

#### C.1. HR Telemetry: Use `MessageClient`, NOT `ChannelClient`

`ChannelClient` continuous 1Hz streaming keeps the watch's Application Processor awake and the Bluetooth radio in high-power active mode, burning **25–40% battery per hour** (draining the watch in ~2.5 hours).

**Correct approach:**
* Use **Health Services API (`ExerciseClient`)** on the watch for HR sensing via the low-power co-processor.
* Use `MessageClient` to send HR updates to the phone, **batched at 2–3 second intervals** when the workout screen is active.
* When the watch enters **Ambient Mode**, reduce to 5–10 second batches to let the AP sleep.

#### C.2. Instant Commands: `MessageClient`
* **Rotary Crown Bailout** → `MessageClient.sendMessage(...)` for instant delivery to phone
* **HR Threshold Haptics** → Phone sends `MessageClient` packet to watch to trigger vibration
* **Resume Slap** → Watch sends `MessageClient` tap event to phone to re-engage ERG

---

### D. The Health Connect Layer

#### D.1. Permissions & Rationale Activity

Required manifest permissions:
```xml
<uses-permission android:name="android.permission.health.READ_EXERCISE" />
<uses-permission android:name="android.permission.health.WRITE_EXERCISE" />
<uses-permission android:name="android.permission.health.WRITE_HEART_RATE" />
<uses-permission android:name="android.permission.health.WRITE_POWER" />
<uses-permission android:name="android.permission.health.WRITE_ACTIVE_CALORIES_BURNED" />
```

**Mandatory rationale Activity** (required for Play Store and permission grants):
```xml
<activity-alias
    android:name=".HealthConnectRationaleActivity"
    android:exported="true"
    android:targetActivity=".MainActivity">
    <intent-filter>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
        <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
        <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
    </intent-filter>
</activity-alias>
```

#### D.2. Data Write Strategy: Batch on Completion

**Never write to Health Connect during the workout.** It is a cold-storage database with rate limits, not a real-time bus. Buffer all data in-memory (or Room) during the workout, then batch-write on session completion:

* 1× `ExerciseSessionRecord` (type: `EXERCISE_TYPE_BIKING_STATIONARY`)
* 1× `HeartRateRecord` containing all `HeartRateRecord.Sample` items (≥1s window guard)
* N× `PowerRecord` chunked ≤30 min (each interval clamped inside the parent session; out-of-window buckets dropped)
* 1× `ActiveCaloriesBurnedRecord`

#### D.3. PowerRecord Gotchas
* Every `PowerRecord.Sample.time` must satisfy `startTime ≤ sample.time ≤ endTime`. A single timestamp 1ms outside the window throws `IllegalArgumentException`.
* The `PowerRecord` interval must lie within the parent `ExerciseSessionRecord` time interval.
* For sessions > 1 hour, a single `PowerRecord` with 3,600+ samples can exceed the Binder transaction buffer (1 MB). Split into 15–30 minute chunks.
* Use `Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE))` (connect-client 1.1.0).

#### D.4. Calorie Calculation

Health Connect does **not** calculate calories internally — the app must compute and write the value. The industry-standard power-based approach is:

**Step 1: Calculate mechanical work (kJ)**
```
work_kJ = Σ (watts_i × interval_seconds_i) / 1000
```
Sum instantaneous power samples over the workout duration.

**Step 2: Convert to calories using the kJ ≈ kcal rule**

Human cycling efficiency is ~24%. Since 1 kcal = 4.184 kJ, the conversion factor is `1 / (4.184 × 0.24) ≈ 1.0`. This means **1 kJ of mechanical work ≈ 1 kcal of metabolic energy expenditure**. This is the same formula Garmin, Strava, and Zwift use when a power meter is present.

```kotlin
val totalCalories = workKj  // 1:1 approximation (total metabolic cost)
val activeCalories = totalCalories - (bmrPerSecond * durationSeconds)
// where bmrPerSecond ≈ weight_kg * 1.0 / 3600  (≈1 MET resting cost)
```

**What to write to Health Connect:**
* `ActiveCaloriesBurnedRecord` should contain **active calories** (total minus resting/BMR for the duration). This matches what Fitbit, Pixel Watch, and Google Fit report as "active calories."
* The `TotalCaloriesBurnedRecord` (if written) should contain the full metabolic cost including BMR.

**Why not the QZ formula?** QZ uses `(0.048 × watts + 1.19) × weight × 3.5 / 200 / 60` which is a simplified ACSM metabolic equation. It produces roughly similar results but diverges at high power outputs and doesn't cleanly separate active vs. total calories. The kJ ≈ kcal method is more widely adopted and produces values consistent with what users see on Garmin/Strava.

---

### E. ZWO Workout File Sourcing & Parser

#### E.1. File Import Methods

**Manual Import (SAF):** Use Android's Storage Access Framework with `ACTION_OPEN_DOCUMENT` to let the user pick `.zwo` files from any provider (local storage, Google Drive, Dropbox, etc.):
```kotlin
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "*/*"
    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/xml", "text/xml", "application/octet-stream"))
}
```
Since SAF doesn't filter by extension, validate the selected file's name (via `OpenableColumns.DISPLAY_NAME`) ends with `.zwo` before parsing. Use `contentResolver.takePersistableUriPermission()` if the user wants to re-open the same file later.

**Google Drive Folder Sync:** Allow the user to designate a Google Drive folder (e.g., `My Drive/Workouts/`) as a sync source. Use the Google Drive API (v3, REST) to periodically list files with `.zwo` extension in that folder, download new/modified files, and cache them locally in the app's internal storage (`filesDir/workouts/`). Sync should happen on app launch and be manually triggerable from the Dashboard. A `WorkManager` periodic task (e.g., every 6 hours) can handle background sync.

**Local Cache:** Regardless of import method, all `.zwo` files are copied into `filesDir/workouts/` with metadata (original source, last modified date) stored in Room. The Workout Library UI reads from this local cache.

#### E.2. Parser Details

The `.zwo` format is a **reverse-engineered Zwift XML format** with no official schema. Key elements:

| Tag | Purpose | Key Attributes |
|:---|:---|:---|
| `<Warmup>` / `<Cooldown>` | Linear power ramp | `Duration`, `PowerLow`, `PowerHigh`, `Cadence` (opt) |
| `<SteadyState>` | Flat power block | `Duration`, `Power`, `Cadence` (opt) |
| `<IntervalsT>` | Repeating work/rest | `Repeat`, `OnDuration`, `OffDuration`, `OnPower`, `OffPower` |
| `<Ramp>` | Mid-workout ramp | `Duration`, `PowerLow`, `PowerHigh` |
| `<FreeRide>` | ERG disabled | `Duration`, `FlatRoad` |
| `<MaxEffort>` | Sprint / ERG disabled | `Duration` |
| `<textevent>` | Coaching cue | `timeoffset`, `message` |

**Critical parsing edge cases:**
1. **Power values are FTP fractions, not watts.** `Power="0.90"` = 90% of FTP. Parser must validate FTP is set before workout start.
2. **Attribute casing varies.** Some generators produce `powerlow`/`powerhigh`; Zwift produces `PowerLow`/`PowerHigh`. Attribute lookups must be case-insensitive.
3. **`<textevent>` scoping.** Nested inside a segment → `timeoffset` is relative to segment start. Direct child of `<workout>` → `timeoffset` is cumulative from workout start.
4. **`<IntervalsT>` expansion.** `Repeat="5"` must be flattened into 10 discrete steps (5×On + 5×Off) for the canvas playhead.
5. **Missing cadence attributes.** `Cadence`, `CadenceResting`, etc. are optional. Default to null / no target.
6. **`<FreeRide>` and `<MaxEffort>` blocks.** These disable ERG entirely — the engine must handle the state transition.

Use `XmlPullParser` for streaming, zero-allocation parsing.

---

## 4. User Interface (UI) Experience & Views

To keep the development scope manageable while delivering a premium experience, the UI will be split into core functional views utilizing Jetpack Compose for a reactive, state-driven design.

### A. Android App (Phone/Tablet) Views
* **1. Dashboard / Home View**
  * **Status Header:** Live connection indicators for the EX-4S (BLE) and Pixel Watch (Wearable Data Layer).
  * **Quick Start:** A prominent button to just "Free Ride" without a structured workout.
  * **Workout Library:** A scrollable list of parsed `.zwo` files. Tapping one reveals the workout's power profile graph, total duration, and TSS (Training Stress Score).
* **2. The Active Workout View (Landscape Optimized)**
  * **The Canvas Profile:** The center of the screen features a drawn canvas of the ZWO power blocks. A vertical "playhead" moves across it, showing upcoming intervals.
  * **The Big Three (Top Row):** Massive, high-contrast typography displaying Live Power (W), Live Cadence (RPM), and Live Heart Rate (BPM). 
  * **Target vs. Actual:** A colored progress bar or gauge showing how closely the rider is matching the current Target Power. (e.g., Green = Spot on, Red = Too high/low).
  * **The "Clutch" Button:** A large, easy-to-hit digital button (or gross-motor swipe gesture area) to manually disable ERG mode (Bailout).
* **3. Post-Workout Summary View**
  * **Charts:** Line graphs plotting Power and Heart Rate over the duration of the workout. 
  * **Metrics:** Averages (Avg Power, Avg Cadence, Max HR) and total calories burned.
  * **Sync Status:** Visual confirmation that the `ExerciseSessionRecord` was successfully written to Google Health Connect.
* **4. Settings & Configuration View**
  * **Athlete Profile:** Inputs for weight and FTP (Functional Threshold Power) — essential for ZWO math.
  * **Hardware Setup:** BLE scanning, manual MAC address entry (fallback), and Health Connect permission toggles.
  * **Advanced Engine Tuning:** Sliders to adjust PI controller gain (P and I values) and the "Cadence Floor" threshold limit.

### B. Wear OS Companion App (Pixel Watch) Views
* **1. Standby View**
  * Simple text indicating "Waiting for Phone" or "Ready." Prevents accidental workout starts from the watch.
* **2. Active Telemetry View (Ambient Mode Supported)**
  * The screen goes black with minimal pixel usage to save battery.
  * Displays only current HR in a large font, colored based on the current HR Zone (e.g., Blue for Zone 2, Red for Zone 5).
  * Small text showing elapsed workout time.
* **3. The "Intervention" Overlays**
  * **Bailout State:** If the rotary crown is flicked backward, the screen flashes amber/red with the text "ERG SUSPENDED."
  * **Resume Slap Target:** If the phone detects a cadence crash and suspends ERG, the watch UI is entirely replaced by a massive, bright green "TAP TO RESUME" button, allowing for a gross-motor hand slap to re-engage the workout.

## 5. Phased Implementation Roadmap
* **Phase 1: BLE Spelunking & Sandbox App [COMPLETED & HARDWARE-VERIFIED]**
  * Deliverables:
    * [x] Connect to physical EX-4S over BLE GATT with 40ms sequenced Coroutine Channel queue.
    * [x] Complete 7-step handshake & 2-second rolling keep-alive polling (`0xF0 0xA0 0x01...`).
    * [x] Read real-time telemetry: Cadence (RPM), Speed (km/h), Distance, Elapsed Time (`0xD1` frames), and Resistance (`0xD2` frames).
    * [x] Actuate electronic resistance levels 1–32 (`0xF0 0xB1 0x01...`) via motor slider and presets.
    * [x] Calculate watts using the authoritative 33x11 matrix with linear interpolation.
    * [x] QZ-style auto-discovery, auto-connect, and auto-reconnect on bike idle/wake.
    * [x] 100% test coverage across protocol, packet parser, and watt table (13/13 passing).
* **Phase 2: The Workout Engine & Core Math [COMPLETED & VERIFIED]**
  * Deliverables:
    * [x] ZWO XML parser via streaming `XmlPullParser` supporting all Zwift elements (`<Warmup>`, `<SteadyState>`, `<IntervalsT>`, `<Ramp>`, `<FreeRide>`, `<MaxEffort>`, `<textevent>`) and casing quirks with exception-safe `parseSafe` methods.
    * [x] Automatic `<IntervalsT>` repeat flattening into discrete On/Off workout segments for linear playhead progression.
    * [x] Scoped text event handling (segment-relative vs workout-cumulative).
    * [x] User profile persistence via Jetpack DataStore Preferences for Athlete FTP, weight, and engine tuning parameters.
    * [x] Feedforward + PI trim ERG controller (`ErgController`) with 3-second cadence EMA smoothing, ±3 trim clamping, and 5W deadband guard.
    * [x] Anti-spiral mechanics: immediate cadence floor (<60 RPM bailout to level 8), recovery gate (≥75 RPM for 3 consecutive seconds), and Dynamic HR Capping (10% target power scale down on critical HR).
    * [x] Local workout file storage manager (`WorkoutRepository`) caching `.zwo` workouts in `filesDir/workouts/` with auto-seeded sample workouts ("Sweet Spot Intervals" and "FTP Ramp Test").
    * [x] 100% test coverage across models, ZWO parser, athlete profile, ERG controller, and workout repository (36/36 tests passing).
* **Phase 3: Android Architecture & UI Views [COMPLETED & VERIFIED]**
  * Deliverables:
    * [x] ForegroundService (`connectedDevice`) upgraded to bind active workouts, displaying live wattage, target wattage, cadence, and ERG state in persistent system notifications.
    * [x] Core `WorkoutSessionManager` coordinating playhead ticking, ERG loop actuation, sample telemetry recording, and summary metrics.
    * [x] Modern Jetpack Compose Material 3 UI architecture with full state-driven screen navigation (`Dashboard`, `ActiveWorkout`, `WorkoutSummary`, `Settings`, and `Sandbox`).
    * [x] **Dashboard View:** Live EX-4S connection card with instant scan dialog trigger, Pixel Watch status pill, Quick Start Free Ride card, and Workout Library with SAF `.zwo` import.
    * [x] **Active Workout View (Portrait & Landscape Optimized):** High-contrast "Big Three" tiles (Power, Cadence, Resistance/Speed), live Target vs Actual power gauge, on-screen `<textevent>` cue banner, gross-motor "The Clutch" bailout button, and workout pause/stop controls.
    * [x] **Interactive Canvas Profile:** Custom `WorkoutCanvasProfile` drawing FTP zone-colored workout interval ramps and steady states with a real-time glowing playhead.
    * [x] **Post-Workout Summary View:** Key session metrics (avg/max power, avg/max cadence, total kJ mechanical work, active kcal), canvas power + HR history charts, and return navigation.
    * [x] **Settings & Configuration View:** Athlete FTP and weight persistence, hardware auto-connect toggles, manual MAC fallback, live BLE scan dialog, and PI gain / cadence floor tuning sliders.
    * [x] Android 14+ permission flow handling for `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, and `POST_NOTIFICATIONS`.
    * [x] 100% test coverage across models, parser, ERG controller, repository, and session manager (44/44 tests passing; debug APK built successfully).
* **Phase 4: Wear OS & Remote Telemetry [COMPLETED & VERIFIED]**
  * Deliverables:
    * [x] Multi-module Wear OS companion architecture (`:shared`, `:wear`, `:app`).
    * [x] Compact binary serialization protocol (`WearableProtocol.kt`) for `/telemetry/hr`, `/workout/bailout`, `/workout/resume`, `/workout/state`, and `/workout/haptic`.
    * [x] Low-power Heart Rate telemetry using Wear Health Services API (`ExerciseClient`) running on watch co-processor with seamless fallback to `SensorManager`.
    * [x] Power-efficient `MessageClient` transmission: 2.5s active batching, 6.0s Ambient Mode batching (strictly avoiding battery-draining `ChannelClient`).
    * [x] Rotary crown flick gesture detection (`onRotaryScrollEvent`) for instantaneous mechanical bailout ("The Clutch") to phone.
    * [x] Ambient Mode lifecycle support (`AmbientLifecycleObserver`) on Wear OS for true-black OLED power conservation.
    * [x] Full-screen Wear OS "Resume Slap" green gross-motor overlay triggered during cadence floor bailout.
    * [x] Amber/red "ERG Suspended" overlay and multi-pattern haptics for critical HR, bailouts, and resumes.
    * [x] Phone-side dynamic HR capping with 5 BPM hysteresis in `WorkoutSessionManager` scaling target power down by 10%.
    * [x] Mobile UI integration: real-time Pixel Watch status card on Dashboard and Tile 3 HR with zone-based dynamic coloring on Active Workout screen.
    * [x] Post-review hardening: unconditional dispatch on cadence-floor bailout *entry* (emergency bypass), watch HR sensing gated on active workout, path-filtered watch wakeups, throttled (~0.5Hz + transition-burst) phone→watch state sync, backward-only rotary bailout, shared `:shared/HrZone` single source of truth with athlete `maxHr`, and declared `android_wear_capabilities` on both modules.
    * [x] 100% test coverage across shared protocol, shared HR zones, watch HR zones, ERG emergency bypass, and phone wearable session management (61/61 passing; `app-debug.apk` and `wear-debug.apk` built cleanly).
* **Phase 5: Health Connect & Release [COMPLETED & VERIFIED]**
  * Deliverables:
    * [x] Health Connect permissions (`READ_EXERCISE`, `WRITE_EXERCISE`, `WRITE_HEART_RATE`, `WRITE_POWER`, `WRITE_ACTIVE_CALORIES_BURNED`) & rationale `activity-alias`.
    * [x] `HealthConnectManager` batch write on workout completion: `ExerciseSessionRecord` (`BIKING_STATIONARY`), `HeartRateRecord` with samples, `PowerRecord` (chunked ≤30 min for the Binder limit), `ActiveCaloriesBurnedRecord` (1 kJ ≈ 1 kcal rule, active = total minus BMR).
    * [x] Post-workout summary Health Connect sync indicator with retry (`Grant` on permission-required, `Retry` on failure) and auto-sync on session completion.
    * [x] Settings Health Connect card: provider availability, permission status, grant flow.
    * [x] Specification metric auditing: avg & peak power, avg & peak cadence, avg & peak HR (when recorded), kJ & kcal, total distance, elapsed time.
    * [x] 100% test coverage for the planning math (timestamp clamping, 30-min chunking, calorie rule, empty/no-HR edge cases) — 68/68 tests passing across all modules (`app-debug.apk` and `wear-debug.apk` built cleanly).
* **Phase 6: Follow-up Usability, Reliability & Sensor Expansion [PLANNED / IN BACKLOG]**
  * Deliverables:
    * [ ] **In-Ride Ergonomics & Handlebar Experience:**
      * [ ] Screen Wake Lock (`FLAG_KEEP_SCREEN_ON`) on `ActiveWorkoutScreen` during active/paused rides to prevent display timeout on handlebars.
      * [ ] Interactive Notification Controls: Ongoing foreground service notification actions (`Pause/Resume`, `+1/-1 Resistance`, emergency `Bailout / The Clutch`) for media/split-screen riders.
      * [ ] Audio Cues & Text-to-Speech (TTS): `ToneGenerator` 3-2-1 countdown beeps before interval transitions and spoken `<textevent>` coaching cues.
      * [ ] Auto-Pause on Cadence Crash (0 RPM): Optional setting to pause session progression after sustained 0 RPM and auto-resume upon pedaling.
      * [ ] Target Cadence Guidance: Visual cue and target range badge on Cadence Tile (Tile 2) when `.zwo` segment specifies `Cadence`.
    * [ ] **Sensor Expansion & Athlete Profile Parity:**
      * [ ] Standard BLE Heart Rate Monitor (`0x180D` / `0x2A37`): BLE scanning & connection for chest straps / armbands (Polar H10, Garmin HRM, Wahoo TICKR) for riders without Wear OS.
      * [ ] Athlete Max HR & Critical HR in Settings: Expose editable numeric inputs in `SettingsScreen` and wire to `UserProfileRepository` to eliminate hardcoded 190/175 BPM defaults.
    * [ ] **Ride History Persistence & Activity Export:**
      * [ ] Local Workout History Persistence: Store completed `WorkoutSummary` records in local persistence (Room/JSON) to retain ride data when returning to Dashboard.
      * [ ] Activity Export (.TCX / .FIT): Share/export indoor cycling activity files from `WorkoutSummaryScreen` to Strava, TrainingPeaks, Garmin Connect, and Intervals.icu via Android share sheet.
      * [ ] Ride History View: Dashboard tab/list showing past completed workouts, personal bests, and weekly training volume.
    * [ ] **Workout Library Organization:**
      * [ ] Search & Filter: Filter workouts by duration (<30m, 30–60m, >60m), TSS, and search by title/description.
      * [ ] Interval Step Breakdown: Expandable interval list in the preview bottom sheet showing each step (e.g. *10m Warmup @ 135W, 5× [3m @ 220W / 2m @ 120W], 5m Cooldown*).
      * [ ] In-App Quick Interval Builder: Simple visual interval creator for custom workouts directly on the device without requiring XML authoring.
    * [ ] **Test Suite Hardening & CI Parity:**
      * [ ] Resolve OpenJDK 25 / Robolectric ASM classfile major version 69 conflict by extracting pure JUnit testable state models or updating test configurations per `AGENTS.md §8`.

---

## 6. Appendix: Field-Verified Build Environment

* **Target Device:** Physical Pixel phone running Android 17 (API 37 / Build `CP2A.260805.005`).
* **JDK Version:** Android Studio bundled OpenJDK 25 (`jbr` 25.0.3) configured via `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr`.
* **Gradle / AGP:** Gradle 9.8.0, Android Gradle Plugin 9.4.1.
* **Android SDK:** compileSdk = 37, targetSdk = 37, minSdk = 26.
* **Foreground Service Type:** Exclusively `connectedDevice`.
* **Runtime Permissions:** `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`.