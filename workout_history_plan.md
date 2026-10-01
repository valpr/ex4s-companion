# Workout History — Implementation Plan

Agreed scope (2026-10-01): full incl. TCX export, JSON files, separate screen, full samples.

## 1. Current state
- `WorkoutSessionManager.generateSummary()` (`app/.../workout/WorkoutSessionManager.kt:447`) builds in-memory
  `WorkoutSummary` (avg/peak power/cadence/HR, kJ/kcal, distance, time, full 1 Hz `samples`,
  `startTimeEpochMs`). Nothing persists it.
- Save hook point is `MainNavigation.kt:107` `LaunchedEffect(summary)` — currently only Health Connect sync.
- `DashboardScreen.kt:678` calls `BeginnerPlan.recommendNext(emptySet())` ("without persisted ride history yet").
- Storage precedent: `WorkoutRepository` (`filesDir/workouts/`) + DataStore. No Room in app today.

## 2. Design
### 2.1 Data + repository (`app/.../history/`)
- `CompletedRide.kt` — `@Serializable` DTO mirroring `WorkoutSummary` + `sourceWorkoutFilename`,
  `completedAtEpochMs`. Pure `fromSummary()` mapper.
- `WorkoutHistoryRepository.kt` — `filesDir/history/ride_<startEpochMs>.json` per ride + `history_index.json`
  header list (so list screen never parses 3600-sample files).
  APIs: `save(summary, sourceFilename): Boolean` (idempotent on start epoch; coerce zero-length to ≥1 s),
  `listHeaders(): List<RideHeader>` newest-first, `loadRide(id)`, `delete(id)`, `completedFilenames()`.
  Corrupt JSON → null + skip, never crash list.
- New dep: `kotlinx-serialization-json` (pure JVM-testable, no Room/KSP overhead).

### 2.2 Save wiring
- `BikeApplication` owns `workoutHistoryRepository` next to `workoutRepository`.
- `MainNavigation` `LaunchedEffect(summary)` also calls `historyRepository.save(summary, sessionState.workout?.name)`.

### 2.3 History UI (separate screen)
- `AppScreen` += `RIDE_HISTORY`, `RIDE_DETAIL`. Dashboard gets compact "Recent Rides" entry card.
- `RideHistoryScreen` — rows (date, name, duration, avg W, kJ), delete with confirm, empty state.
- `RideDetailScreen` — reuses `PowerHistoryChart` / `HrHistoryChart` / `MetricCard`.
- `RideHistoryViewModel` — `headers`, `selectedRide`, stats; IO on `Dispatchers.IO`.

### 2.4 Stats + Beginner Path
- `HistoryStats.kt` — pure helpers: personal bests (best max power, best avg ≥20 min, longest ride,
  biggest kJ), weekly volume (last 7 d + last 4-week bars), totals.
- Dashboard `BeginnerPathCard` switches from `emptySet()` to `completedFilenames()`.

### 2.5 TCX export
- `TcxExporter.kt` — pure `summary → String`: TrainingCenterDatabase / Activity Sport="Biking",
  Id=ISO8601 start, one Lap + 1 Hz Trackpoints (Time, DistanceMeters, HR, Cadence, TPX/Watts).
  Timestamps clamped into `[start, end]`.
- Share from detail screen via `cacheDir/tcx/` + `FileProvider` + `ACTION_SEND`
  (`application/vnd.garmin.tcx+xml`, fallback `text/xml`). Works with Strava / Garmin / Intervals.icu.
  No FIT SDK in v1.

## 3. Tests (plain-JUnit per AGENTS.md §8)
- `HistoryStatsTest`, `TcxExporterTest`, `WorkoutHistoryRepositoryTest` (temp dir, idempotent
  double-save, corrupt file tolerated).

## 4. Order
1. DTO + repository + save hook + repo tests.
2. List/detail screens + nav + Dashboard entry.
3. Stats + Beginner wiring.
4. TCX builder + FileProvider share.
5. Full gate: `spotlessCheck`, `lintDebug`, `test --rerun-tasks`, `assembleDebug`.
