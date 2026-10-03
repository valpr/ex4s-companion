# AGENTS.md — Development Guidelines & Architectural Invariants

This document contains mandatory guidelines, invariants, and hard-learned lessons for autonomous agents working on the Custom Echelon EX-4S Companion app.

---

## 1. Hardware State vs. Session State Separation
* **Never wipe physical telemetry on session transitions:**
  * Hardware telemetry (`latestTelemetry`, cadence, power, resistance) belongs to the continuous BLE connection lifecycle, NOT to individual workout runs.
  * When constructing or updating `WorkoutSessionState` in `startWorkout()` or `resetToIdle()`, **always preserve live telemetry** (`latestTelemetry = it.latestTelemetry`) **and athlete context** (`currentHeartRate`, `athleteMaxHr`). Wiping telemetry to default zeroes causes immediate false triggers in anti-spiral checks (`CADENCE_FLOOR_BAILOUT`) upon workout launch; wiping athlete context silently resets HR zone coloring and critical-HR thresholds mid-session.
* **Emergency safety actions must bypass command-suppression caching:**
  * Performance optimizations like de-duplication checks (`lastCommandedResistance != target`) must **never** suppress emergency/bailout commands ("The Clutch", cadence floor drops).
  * Emergency actions must unconditionally dispatch (`shouldSendBleCommand = true`) to guarantee the physical bike hardware actuates immediately, regardless of what the local cache believes the bike is set to.
  * Scope clarification (hard-learned): the bypass applies to **entry** into a bailout state. Steady-state maintenance ticks *while already in* the bailout may suppress redundant re-sends to save BLE traffic / motor wear — but the entry transition itself must always send, even if the cache already reads the recovery level (stale-cache case covered by regression test).
* **Instant actuation on manual resume:**
  * When resuming from manual bailout, do not wait up to 1000ms for a timer tick. Compute target resistance and dispatch to the bike immediately (`dtSeconds = 0.0`) so the rider feels instant mechanical responsiveness.

---

## 2. Mode Asymmetry & Fallback UI/UX
* **Treat Free Ride as a first-class mode, not just `workout == null`:**
  * When designing views with structured vs. unstructured modes, explicitly design and verify *both* paths:
    * In Free Ride: structured canvas profiles and ERG target gauges are absent. Replace the empty space with manual electronic shifting controls (gross-motor `+`/`-` buttons, 1–32 slider, quick presets).
    * Never show non-functional structured controls (e.g. `±5%` intensity scale chips) during Free Ride; swap them for direct resistance shifters (`-1 Res` / `+1 Res`).
    * Suppress or replace ERG bailout buttons ("The Clutch") during open rides where ERG is already disabled.
* **Verify landscape and portrait parity:**
  * Exercise equipment screens are frequently mounted horizontally on bike handlebars. Verify both portrait and landscape orientations for both modes, ensuring neither leaves half the screen empty or controls inaccessible.

---

## 3. Bi-Directional Navigation & Lifecycle Resumability
* **No stranded active sessions:**
  * Whenever a background `ForegroundService` or session is active (`RUNNING` or `PAUSED`), the root `DashboardScreen` must display a prominent **Active Session Banner / Card** with live telemetry, a **"Resume Workout"** action, and an **"End"** action.
  * Tapping the system notification or returning to Dashboard must never leave the user unable to return to the active workout or force them to accidentally overwrite it via "Quick Start".
* **Service collector idempotency:**
  * Android Services can receive multiple `startService()` intents during an ongoing ride.
  * Always guard coroutine collectors against duplicate jobs:
    ```kotlin
    if (telemetryJob?.isActive == true) return
    telemetryJob = lifecycleScope.launch { ... }
    ```
* **Android 14+ FGS Type Invariant:**
  * In Android 14+ (targetSdk ≥ 34), `startForeground()` must explicitly pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` matching the manifest, or Android throws `MissingForegroundServiceTypeException`.

---

## 4. Specification Metric Auditing
* **Audit exact metrics before marking deliverables complete:**
  * Do not substitute averages when the specification requires peaks and cumulative work.
  * Specifically ensure summary screens display:
    1. Average **and** Peak Power (`avgWatts` and `maxWatts`)
    2. Average **and** Peak Cadence (`avgCadence` and `maxCadence`)
    3. Total Mechanical Work in **kJ** alongside metabolic expenditure in **kcal**
    4. Total Distance and Total Elapsed Time

---

## 5. Wear OS & Remote Telemetry Invariants
* **Strict Low-Power Transmission (No `ChannelClient` continuous streaming):**
  * Streaming 1Hz packets via `ChannelClient` keeps the watch Application Processor (AP) and Bluetooth radio in high-power state, draining 25–40% battery per hour.
  * Always use `MessageClient` with batched payloads: 2–3 seconds during active screen, 5–10 seconds in Ambient Mode (`AmbientLifecycleObserver`).
  * Telemetry sensing must prioritize Health Services API (`ExerciseClient`) to utilize the low-power co-processor, with automatic fallback to `SensorManager` (`Sensor.TYPE_HEART_RATE`) for emulators or unsupported runtimes.
* **Play Services Task Resolution in Coroutines:**
  * When resolving Google Play Services `Task<T>` (e.g., `Wearable.getNodeClient()`, `sendMessage()`), do not assume `kotlinx.coroutines.tasks.await` extension is present unless `kotlinx-coroutines-play-services` is explicitly declared.
  * Use `com.google.android.gms.tasks.Tasks.await(task, ...)` on `Dispatchers.IO` to ensure clean, dependency-free execution.
* **Threshold Alert Hysteresis:**
  * Any safety derating triggered by telemetry thresholds (e.g., dynamic 10% FTP scale-down on `criticalHeartRate`) must apply a hysteresis buffer (e.g., 5 BPM below threshold before re-enabling full target) to avoid rapid oscillation and mechanical servo hunting around the threshold.
* **Watch HR sensing lifecycle (gated on active workout):**
  * Never hold a watch `ExerciseClient` / HR-sensor session open in standby. Start HR tracking only when the phone reports `RUNNING` or `PAUSED`; stop on `IDLE`/`COMPLETED`/disconnect. An always-on session defeats the co-processor savings and drains the watch even when no workout exists.
* **Watch wake discipline (no 1Hz AP wakeups):**
  * The watch `WearableListenerService` must filter by message path. Plain `workout/state` telemetry (up to 1Hz) must NOT `startActivity()` — it is received by the in-app `MessageClient` listener while open. Wake the UI only for attention-requiring events: haptics, bailout / cadence-floor flags, pause, or completion.
* **Phone→watch sync throttling:**
  * `sessionState` emits at telemetry rate, so a naive `collect { sendMessage() }` spams the radio at 1Hz. Throttle steady telemetry (≈0.5Hz / 2s) and burst immediately on transitions (status, bailout, cadence-floor, HR-cap, target-watts changes).
* **Rotary bailout is backward-flick only:**
  * Accumulate negative scroll and trigger at ≤ −40px. Forward scroll must never bail out — decay/reset the accumulator instead — or normal list scrolling causes accidental ERG suspension.
* **HR zones live in `:shared`:**
  * Zone thresholds are a single source of truth (`shared/HrZone`, % of max HR). The watch enum and phone tile colors both delegate to it; the phone resolves zones with the athlete's actual `maxHr` from session state, never hardcoded BPM cutoffs.
* **Wear capabilities must be declared:**
  * `CAPABILITY_*` constants alone do nothing. Each module needs `res/values/wear.xml` with `android_wear_capabilities` (`bike_companion_wear` / `bike_companion_phone`), or capability discovery never resolves and the listener is dead code.
* **ExerciseClient single-session constraint:**
  * Health Services enforces exactly one active `ExerciseClient` session system-wide. `WearWorkoutTrackingService` exclusively owns the `ExerciseClient` start/stop lifecycle; UI Activities must never independently call `startExerciseAsync()` or `endExerciseAsync()`, but instead observe state flows from `HealthServicesManager`.
* **Wear OS 5+ FGS Health Type & Ongoing Activity:**
  * On Wear OS 5+ (targetSdk ≥ 34), background sensor tracking requires an active `ForegroundService` with `android:foregroundServiceType="health"` and `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />`, or Wear OS kills the session on navigation away. The service must attach an `androidx.wear.ongoing.OngoingActivity` indicator linking back to `MainActivity`.
* **Background batch interval discipline:**
  * When the watch UI is in the background (`onPause`), the batch interval defaults to `AMBIENT_INTERVAL_MS` (5–10s) to allow AP sleep; the foreground activity overrides to `ACTIVE_INTERVAL_MS` (2–3s) on `onResume` for responsive interaction.
* **Background haptic routing & direct dispatch:**
  * Incoming haptic triggers on Wear OS must be dispatched directly by `WearMessageListenerService` via `(application as WearBikeApplication).hapticManager.playAlert()` so critical threshold warnings vibrate immediately without relying on `MainActivity` to be open or in the foreground. Incoming workout state must update `WearMessageManager` immediately and route through pure `resolveServiceAction` to govern `WearWorkoutTrackingService`.
* **FGS null-state resilience:**
  * `WearWorkoutTrackingService` must treat `state == null` as an uninitialized transition window (waiting for first phone broadcast), never as a terminal state. Terminal teardown (`stopSelf()`) must only execute when state is explicitly `IDLE` or `COMPLETED`.
* **Application ID & Version Parity Across Companion Modules (Play Services Wearable Data Layer):**
  * The phone app (`:app`) and Wear OS app (`:wear`) **must share the exact same `applicationId`** across all build types (e.g. `com.valpr.bikecompanion`).
  * **Never** configure `applicationIdSuffix` (e.g. `.debug`) in `:app` without an identical configuration in `:wear`. Google Play Services Wearable Data Layer (`MessageClient`, `CapabilityClient`, `DataClient`) isolates cross-device routing strictly by package name / `applicationId`. Any discrepancy results in silent message drops and completely broken capability resolution across the phone and watch.
  * Keep `versionCode` and base `versionName` synchronized between `:app` and `:wear` so multi-APK releases, companion pairing, and Play Store dependency matching never reject or desync companion builds. Single source of truth is `gradle/libs.versions.toml` (`appVersionCode` / `appVersionName`); both modules read it — bump once at tag/release time, never per-module.
  * **Capability vs. Topology Separation:** Raw Bluetooth topology (`NodeClient.connectedNodes`) only indicates OS-level Bluetooth pairing. Never treat a connected node as companion-ready without verifying app capability advertisement (`CAPABILITY_WEAR_APP` / `CAPABILITY_PHONE_APP`) or active ping handshake.

---

## 6. Health Connect Invariants
* **Batch on completion, never during the workout:**
  * Health Connect is cold storage with rate limits, not a real-time bus. Buffer samples in-memory during the session; write once via `insertRecords()` when the summary is generated.
* **Clamp every sample timestamp into `[start, end]`:**
  * A single `PowerRecord`/`HeartRateRecord` sample 1ms outside the parent window throws `IllegalArgumentException` and fails the whole batch. Coerce all derived timestamps; coerce zero-length sessions to a ≥1s window.
* **Chunk `PowerRecord` by elapsed time (≤30 min per record):**
  * Multi-hour sessions exceed the ~1MB Binder buffer. Group samples by `elapsedSeconds / 1800` so each record stays small.
* **Calories follow the kJ ≈ kcal rule:**
  * `ActiveCaloriesBurnedRecord` = mechanical kJ (1:1 metabolic) minus resting burn (`weightKg × durationSeconds / 3600`, ≈1 MET), floored at 0. Never write negative energy.
* **connect-client 1.1.0 API gotchas (field-verified):**
  * Record/interval constructors put `metadata` before type-specific fields (e.g., `ExerciseSessionRecord(..., metadata, exerciseType, title)`); the legacy field order hits an `internal` constructor.
  * `Metadata` lives at `records.metadata.Metadata` and its constructor is internal — use `Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE))`.
  * `ActiveCaloriesBurnedRecord` takes `energy = Energy.kilocalories(...)` (not `calories`).
  * Keep record *planning* (plain `HrPoint`/`PowerPoint` math) separate from record *assembly* so planning stays JVM-unit-testable without Android stubs.

---

## 7. Linting, Formatting & Code Quality Invariants
* **Line Ending Discipline on Windows:**
  * Spotless must remain configured with `LineEnding.UNIX` and paired with `.gitattributes` (`* text=auto eol=lf`). On Windows, default Spotless behavior converts `\n` to `\r\n`, creating massive false-positive diff churn across checkouts.
* **No Machine-Specific Paths in Repository `.properties` Files:**
  * Machine-specific properties like `org.gradle.java.home` must never be committed to project `gradle.properties`. Instead, configure them via the `JAVA_HOME` environment variable or user-level `~/.gradle/gradle.properties` (`%USERPROFILE%\.gradle\gradle.properties` on Windows).
  * If a property with Windows drive colons is used in any `.properties` file, colons must be escaped (`C\:/...`) to avoid Android Lint `[PropertyEscape]`.
* **Wear Capabilities `UnusedResources` False Positive:**
  * `res/values/wear.xml` defining `android_wear_capabilities` is consumed dynamically by Google Play Services / Wear OS capability discovery at runtime, not referenced as an `R.array` symbol in Kotlin code. Android Lint flags this as `UnusedResources`. It must remain explicitly suppressed in `lint.xml`.
* **Jetpack Compose & StateFlow Naming Discipline:**
  * Jetpack Compose functions returning `Unit` use PascalCase (e.g., `@Composable fun ActiveTelemetryScreen(...)`). Ktlint's `standard:function-naming` must remain disabled in `.editorconfig` and `editorConfigOverride`.
  * Private `MutableStateFlow` fields must not use leading underscores (e.g. `_phoneNodeId`) unless a matching public property or getter exists, or ktlint flags `standard:backing-property-naming`.
* **Conventional Commits Invariant:**
  * Commits are validated by `.githooks/commit-msg` against `^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-zA-Z0-9_/-]+\))?: .+` (hyphen placed last in the class so BSD `grep` treats it literally — do not reintroduce `\-` escapes). Commits with vague subjects (e.g. `"wip"`, `"fixed"`) will be rejected.
* **Pre-Commit Latency Discipline (< 1.5s):**
  * The `.githooks/pre-commit` hook runs exclusively on staged files (`git diff --cached`). Never invoke heavy tasks (full test suite, Android Lint, or full-project builds) in `pre-commit`. Fast checks include: merge conflict markers, secret/key leaks, and [AGENTS.md](file:///C:/Users/Andrew/lw-bike-companion/AGENTS.md) banned imports (`android.util.Log` in `:shared`, hardcoded `Dispatchers.IO` in `BleCommandQueue.kt`).

---

## 8. Test Stability & Testability Invariants
* **Two-clock rule for infinite coroutines (runTest hang/OOM — thread-dump verified):**
  * `WorkoutSessionManager` (1s session loop, telemetry collectors) and `BleCommandQueue` (worker loop) never terminate. Never run them on `runTest`'s scheduler: `runTest`'s teardown `advanceUntilIdle` spins forever on their pending delays (busy-loop, 100% CPU), and calling `advanceUntilIdle` while the session loop runs schedules unbounded ticks until OOM.
  * Pattern: class under test gets its OWN `TestScope()` (separate scheduler); the test body uses plain `runTest {}`; drive manager time explicitly via `managerScope.testScheduler.advanceTimeBy(ms)` / `runCurrent()`. Settle init collectors with `advanceUntilIdle()` only *before* `startWorkout()`. Cancel the scope in `@After`.
* **`StandardTestDispatcher` is a factory function, not a class:**
  * `val x: StandardTestDispatcher` never compiles. Declare `TestDispatcher` as the property type and construct via `StandardTestDispatcher()`.
* **Inject dispatchers and loggers; never hardcode `Dispatchers.IO` / `android.util.Log` in queue-class internals:**
  * `BleCommandQueue(scope, gattProvider, onPacketSent, workDispatcher = Dispatchers.IO, logger = ...)` — defaults preserve production behavior; tests inject a `StandardTestDispatcher` and a list-collecting logger. Direct `Log.*` calls throw `RuntimeException("Stub!")` in plain JVM unit tests.
  * Same rule for any new background worker: no hardcoded dispatchers, no static Android log calls on hot paths.
* **Extract pure helpers for UI-adjacent logic so it stays plain-JUnit:**
  * Established pattern: `shared/MacValidator`, `shared/RotaryBailoutAccumulator`, `ui/summary/HrChartScaling`, `DashboardViewModel.resolveImportFilename`, `WearMessageListenerService.shouldWakeForMessage`. Chart scaling, gesture accumulators, filename sanitizers, and wake filters must live in framework-free functions/objects — never inlined in `@Composable` lambdas or Activities where they become untestable.
  * Keep MockK surface minimal (Gatt object only; prefer real constructors or Objenesis-safe mocks for characteristics/descriptors). Do not mix Robolectric runners with MockK tests; prefer plain JUnit + injected fakes to keep the suite fast (342 tests run in seconds, not minutes).
* **Fail loudly on misconfiguration; capture dispatches as lists:**
  * Engine guards (e.g., missing FTP for structured workouts) must return `Result.failure`, never silently fall back to phantom targets (200W) — UI-only gates are bypassable by service/watch callers.
  * Tests that assert BLE dispatches must record into a `MutableList<Int>` (not a single `lastSent` slot) so duplicate/unwanted writes are detectable.

---

## 9. Verification Protocol & Quality Gates Before Completing Any Phase
1. **Verify code formatting with Spotless:**
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat spotlessCheck
   ```
   *(If formatting issues are found, auto-format with `.\gradlew.bat spotlessApply`).*
2. **Verify static analysis with Android Lint:**
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat lintDebug
   ```
   *(Ensure 0 errors across `:app`, `:shared`, `:wear`; all expected suppressions live in `lint.xml`).*
3. **Rerun all tests cleanly (split gate — both commands; together they are the full gate):**
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test --rerun-tasks -Probolectric=exclude
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --rerun-tasks -Probolectric=only
   ```
   *(Robolectric UI classes share heavy android-all sandboxes and starve each other under shared forks — full-suite-only `AppNotIdle` timeouts while every class passes alone. The two commands together cover every test; either half alone is not the gate. New `@RunWith(RobolectricTestRunner)` classes must be added to `robolectricSpecs` in `app/build.gradle.kts`.)*
4. **Assemble the debug APK:**
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
   ```
5. **Verify compiler warnings:** Ensure no deprecated API icons or unused ViewModel bindings are introduced.
6. **Ensure Git hooks remain configured:**
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installGitHooks
   ```

**Build performance (measured 2026-09-29, warm daemon, full gate ~26s):**
* `gradle.properties` enables `parallel`, `caching`, and `configuration-cache` — all verified compatible with AGP 9 / Kotlin 2.0 (`Configuration cache entry reused` on repeat runs). Do not remove them without re-measuring.
* `:app` unit tests run classes across parallel forks (`maxParallelForks = cores/2`, `maxHeapSize = 2g`) — measured ~3s saving on the full gate, and isolates the heavy Robolectric UI class from the plain-JUnit suites.
* Inner loop (no `--rerun-tasks`): targeted plain-JUnit class ~4s, `WorkoutUiSemanticsTest` alone ~9s, no-op `test assembleDebug` ~2s. Iterate with:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.valpr.bikecompanion.MyTest"
  ```
  Keep both halves of the split gate above as the phase-completion check — never narrow it to green a phase (either half alone is not the gate).
* Robolectric is pinned to 4.17 (not 4.13): 4.13's bundled ASM cannot read Java 25 class files (this machine's JBR is 25.x) and crashes every test teardown in `RoboCookieManager` reset. Do not downgrade without re-verifying on this JDK.

---

## 10. Workout Editor & ZWO Round-Trip Invariants
* **Dirty drafts own navigation; navigation never silently drops edits:**
  * ViewModel-owned drafts survive screen changes, so `open()` must refuse a *different* file while dirty (return `Boolean`) and the navigation layer must confirm (discard/save) before `forceOpen`/`discardAndOpen`.
  * Discard-confirm must actually revert state (`discardChanges()`) before navigating — navigating alone leaves the dirty draft in the ViewModel and re-entry resurfaces rejected edits.
  * Hoist the shared editor ViewModel above `when (currentScreen)` so dashboard-initiated navigation can check `hasUnsavedChanges()`. Outer `BackHandler` must be `enabled = !isDirty` so the editor screen's discard dialog owns back while dirty.
* **Aggregation must be lossless — collapse only cue-consistent repeats:**
  * `collapseIntervalRows` may only merge repeats whose segment *and* cue lists are all identical (longest cue-consistent prefix). Distinct per-repeat messages stay as single rows; collapsing them deletes cues on save.
* **Writer/parser symmetry — never de-flatten block structure:**
  * Interval rows serialize as a single `<IntervalsT>` (work cues at on-offset, rest cues at `onDuration + offset`, mirroring the parser's per-repeat replication), never as N flattened `SteadyState` pairs.
  * Clamping in `buildDetailed` (validator trim counting) and in the writer must agree exactly, or trim warnings and written output disagree.
* **Validation owns range reporting — fields must not pre-clamp typed input:**
  * Stepper *buttons* may clamp at `min` as a convenience, but typed text input passes through raw so validator errors stay reachable. Pre-clamping makes lower-bound errors dead code that only tests can produce.
  * Same principle for representation switches (`changeSegmentType`): carry `restCues` (merge into work cues when the target has no rest slot) rather than dropping user data.
* **Dialog flags tied to ViewModel state must be `rememberSaveable`:**
  * When the condition being confirmed (e.g. `pendingOverwriteFilename`) lives in the rotation-proof ViewModel but visibility is local `remember`, rotation strands the state with no dialog. Persist visibility or derive it from the ViewModel state.
