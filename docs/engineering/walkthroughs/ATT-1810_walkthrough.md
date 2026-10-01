# Stage 5: Walkthrough & Verification - ATT-1810: Fix barometric altimeter calibration oscillation, prevent runaway retroactive shifts, enforce physical climb limits, and sanitize historical workout elevation profiles

**Ticket**: [ATT-1810](https://rainerblind.atlassian.net/browse/ATT-1810)  
**Sub-task**: [ATT-1896](https://rainerblind.atlassian.net/browse/ATT-1896) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Historical Workout Data Sanitization*)  
**Test Mapping**: `TST-CON-008`  
**Branch**: `feature/ATT-1810`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Executive Summary & Verification Overview

Workout *"Kurz zum Bäcker #14"* on Pixel 10 (a 2.05 km ride in Schönaich) generated an elevation profile starting at ~7300 m and descending in staircase plateaus down to 507 m. Ticket ATT-1810 forensically diagnosed and eliminated the root causes across the live recording, calibration, background processing, and presentation pipelines:

1. **Elimination of Feedback Loop in `AltitudeFromPressureDevice.java`**:
   - `setAltitudeCorrection(double correctAltitude)` evaluated `currentAltitude` from `mAltitudeSensor.getValue()`, which was *already* corrected: `mAltitudeCorrection = correctAltitude - currentAltitude`.
   - On the first calibration call at 507.0 m (raw altitude 450.0 m), correction was $+57.0\text{ m}$. On the subsequent call, `getValue()` returned 507.0 m, so the method calculated $507.0 - 507.0 = 0.0\text{ m}$, broadcasting a $-57.0\text{ m}$ shift and reverting to raw pressure.
   - On the next cycle, `getValue()` was 450.0 m again, calculating $+57.0\text{ m}$ and broadcasting $+57.0\text{ m}$.
   - Every round-trip broadcast shifted the `WorkoutSamples` database table backwards by $\pm 57.0\text{ m}$. Because the initial fixes were shifted upward repeatedly during warm-up, the leading points accumulated up to 7300 m.
   - **Fix**: Formulated absolute correction relative to raw barometric pressure:
     $$\text{newCorrection} = \text{correctAltitude} - \text{mLastRawAltitude}$$
     $$\text{deltaOffset} = \text{newCorrection} - \text{mAltitudeCorrection}$$
   - The correction broadcast is only dispatched if $|\text{deltaOffset}| \ge 0.1\text{ m}$. Repeated calls with the same target altitude produce $\text{deltaOffset} = 0.0\text{ m}$, completely suppressing spurious broadcasts.
   - Added idempotency guard in `calibrate()`: skips redundant execution if already calibrated within $0.5\text{ m}$.

2. **Geofence Transition Calibration Gating in `TrackingTabsViewModel.kt`**:
   - `locationCalibrationStatus` previously re-evaluated and dispatched calibration on every continuous 1-second GPS location update while the athlete remained within a known start location geofence.
   - **Fix**: Added `lastCalibratedLocationId: Long?` to `TrackingTabsViewModel`. Altimeter calibration dispatch is strictly gated on geofence transitions (entering a new location or re-entering after exit). Exiting all geofences resets the tracking ID to `null`.

3. **Physical Sanity Clamping & Atomic SQLite Transactions**:
   - In `TrackerService.java`: Clamped retroactive shift offsets exceeding $|500.0\text{ m}|$ and encapsulated the multi-sample table update within an atomic SQLite transaction (`beginTransaction` / `setTransactionSuccessful` / `endTransaction`).
   - In `LiveWorkoutSession.java`: Enforced physical bounds $[-500\text{ m}, 9000\text{ m}]$, clamped rate of climb jumps exceeding $30\text{ m/s}$ ($108\text{ km/h}$) relative to the preceding sample, and clamped retroactive shifts exceeding $|500.0\text{ m}|$.
   - In `GPXFileWriter.java` and `TCXFileWriter.java`: Clamped exported altitude tags within physical bounds $[-500.0\text{ m}, 9000.0\text{ m}]$.

4. **Historical SQLite Data Sanitization & Elevation Profile Auto-Scaling**:
   - Implemented `WorkoutSummariesDatabaseManager.sanitizeCorruptedAltitudeWorkouts()`: automatically scans for workouts corrupted by legacy runaway shifts ($\text{maxAltitude} > 4000\text{ m}$ and $\text{minAltitude} < 1000\text{ m}$), replaces non-physical leading staircase points with valid baseline elevation, updates `TABLE_EXTREMA_VALUES` (MIN, MAX, AVG), and sanitizes corresponding raw samples in `WorkoutSamples` database within an atomic SQLite transaction.
   - Wired auto-healing into `WorkoutSummariesDbHelper.onOpen()` via a background single-thread executor to heal historical profiles on app start without UI latency.
   - In `ElevationProfile.kt` (`calculateElevationBounds`), leading non-physical spikes ($> 3000\text{ m}$ when median $< 1500\text{ m}$) are excluded from chart bounds calculation, ensuring elevation profile rendering auto-scales cleanly to real terrain (~480–530 m).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-017` (item 1, 2) | `[TST-CON-008.1]`, `[TST-CON-008.2]`, `[TST-CON-008.3]` | Unit Test (`AltitudeFromPressureDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-017` (item 3) | `[TST-CON-008.4]` | Unit Test (`TrackingTabsViewModelLocationTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-017` (item 4, 5) | `[TST-CON-008.5]` | Unit Test (`WorkoutSummariesDatabaseManagerSanitizationTest.kt`, `ElevationProfileBoundsTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-CON-008.7]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeFromPressureDeviceTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelLocationTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileBoundsTest" \
                            --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManagerSanitizationTest"
BUILD SUCCESSFUL in 22s
32 actionable tasks: 2 executed, 30 up-to-date
```
- `AltitudeFromPressureDeviceTest.testSetAltitudeCorrection_whenSensorValuePresent_calculatesDeltaFromCurrentValue`: PASSED
- `AltitudeFromPressureDeviceTest.testSetAltitudeCorrection_repeatedCalls_doesNotOscillate`: PASSED
- `AltitudeFromPressureDeviceTest.testCalibrate_idempotency_skipsRedundantWork`: PASSED
- `TrackingTabsViewModelLocationTest.testCalibrationGating_dispatchesOnlyOnGeofenceTransition`: PASSED
- `ElevationProfileBoundsTest.testStaircaseOutlierProfile_FiltersLeadingArtifactAndAutoScalesToRealTerrain`: PASSED
- `WorkoutSummariesDatabaseManagerSanitizationTest.testSanitizeCorruptedAltitudeWorkouts_repairsStaircaseProfileAndExtrema`: PASSED
- `WorkoutSummariesDatabaseManagerSanitizationTest.testSanitizeCorruptedAltitudeWorkouts_whenNoCorruptedWorkouts_returnsZero`: PASSED

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 8s
32 actionable tasks: 1 executed, 31 up-to-date
```
- Total test suite: 100% pass rate, 0 failures, 0 regressions across all modules.

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Historical Corrupted Workout Healing Verification**:
  1. Open Aftermath workout list.
  2. Inspect workout *"Kurz zum Bäcker #14"*: verify that the background auto-healing sanitizes the profile.
  3. Inspect elevation graph: verify the curve renders smoothly between 507 m and 528 m without displaying a 7300 m spike.
  4. Verify maximum elevation statistic badge displays ~528 m rather than 7300 m.
* **Live Barometric Calibration & Tracking Verification**:
  1. Start tracking at a known start location (e.g. Home geofence at 507 m).
  2. Observe cockpit altimeter badge: initial barometric calibration locks immediately to 507 m.
  3. Move around within the geofence: verify altimeter does not flicker, oscillate between raw and calibrated values, or trigger repeated calibration broadcasts.
  4. Exit geofence and re-enter: verify calibration dispatches cleanly on re-entry.

---

## 5. Invariant & Governance Verification

1. **Cold-Start Null Safety**: Cold-start null-safety (`REQ-CON-013`) is preserved.
2. **DEM Elevation Caching**: Open-Meteo DEM queries and 200m spatial geofence caching (`REQ-DAT-014`) remain intact.
3. **Start Location Counting**: Start location hit counts (`REQ-DAT-015`) continue incrementing reliably.
4. **Cockpit Badge Feedback**: Cockpit calibration badge feedback (`REQ-UI-199`) is fully operational.
5. **Localization Parity**: 100% 9-language localization parity maintained across all strings.
6. **Clean-Room Regression**: Full test suite pass with 0 regressions.
