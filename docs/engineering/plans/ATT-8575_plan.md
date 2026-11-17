# Stage 3: Implementation Plan - ATT-8575: 3-State EKF Barometer Initialization & Calibration Jump Guard

**Ticket**: `ATT-8575` (`2 SP`, Epic `ATT-166`) | **Branch**: `feature/ATT-8575` | **Date**: 2026-11-17  
**Requirement**: `REQ-FIL-022` (`REQ-FIL-022.1`..`REQ-FIL-022.3`) | **Test Spec**: `TST-FIL-022` (`TST-FIL-022.1`..`TST-FIL-022.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **UI Consistency**: `N/A - no UI strings`

---

## 1. Architecture & Layering Design (SWE.2)
- **Domain / Math Engine (`banalservice.odometry.KinematicAltitudeSlopeEkf`)**: Separates unanchored 3-state barometer startup (`p00 = DEFAULT_UNANCHORED_ALT_VAR = 400.0`) from 2-state mode (`p00 = varBaro = 0.25`) and performs exact first-reference bias absorption (`baroBias += oldAlt - refAltM` for Baro-before-Ref; `baroBias = baroAltM - altitude` for Ref-before-Baro) while keeping post-calibration Huber outlier rejection (`huberK = 3.0`) intact. All state mutations remain `@Synchronized`.
- **Device Driver (`banalservice.devices.AltitudeAndSlopeDevice_ATTFused`)**: Invokes `ekf.updateReferenceAltitude(...)` inside `@Synchronized` `handleReferenceAltitude()` (`:270`) and `checkReferenceAltitudeFromSensors()` (`:313, 337`), resetting `mAscentDescentAnchorAltitude` and `mLastAltitude` to `Double.NaN` whenever `updateReferenceAltitude` returns `true` (initial 3-state re-anchoring) before `publishAltitudeAndMetrics()` (`:352`).
- **Threading / Schema**: No new threads, schedulers, DB migrations, or UI resources (`N/A - no UI strings`).

## 2. Atomic Stage 4 Implementation Sequence & Traceability
| Step | Target File(s) | Action & Scope | REQ / TST Mapping |
| :--- | :--- | :--- | :--- |
| **Step 1** | `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt` | Add `DEFAULT_UNANCHORED_ALT_VAR = 400.0`, `@Volatile` `hasReferenceAnchor` & `hasBarometerSample` (reset in `reset()` & `setMode()`). In `updateBarometer()`: when `!isInitialized`, set `p00 = if (is3StateMode) DEFAULT_UNANCHORED_ALT_VAR else varBaro`; when `is3StateMode && hasReferenceAnchor && !hasBarometerSample`, seed `baroBias = baroAltM - altitude` and return. Change `updateReferenceAltitude()` to return `Boolean`: on first 3-state anchor (`is3StateMode && !hasReferenceAnchor`), set `baroBias += (altitude - refAltM)` (if `hasBarometerSample`), `altitude = refAltM`, `p00 = varRef`, zero `p01`/`p10`/`p02`/`p20`, set `hasReferenceAnchor = true`, return `true`; normal updates return `false`. | `REQ-FIL-022.1`, `REQ-FIL-022.2`, `TST-FIL-022.1`, `TST-FIL-022.2` |
| **Step 2** | `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt` | Add `private fun resetAscentDescentAnchor()` (`mAscentDescentAnchorAltitude = Double.NaN; mLastAltitude = Double.NaN`). Capture `Boolean` return of `ekf.updateReferenceAltitude(...)` in `handleReferenceAltitude()` (`:272`) and `checkReferenceAltitudeFromSensors()` (`:313, 337`); call `resetAscentDescentAnchor()` when `true` before `publishAltitudeAndMetrics()`. | `REQ-FIL-022.3`, `TST-FIL-022.3` |
| **Step 3** | `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`, `.../AltitudeAndSlopeDevice_ATTFusedTest.kt` | Add unit tests for: (a) 3-state Baro-before-Ref standstill `+130.4 m` (`568.4 -> 438.0 m`) anchoring & `p00 > 0.25`; (b) 3-state Ref-before-Baro `+130.4 m`; (c) 2-state `p00 = varBaro` (`< 10.0`) + `reset()`/`setMode()`; (d) device `0.0 m` bogus `ASCENT`/`DESCENT` guard on initial DEM jump followed by real `25 m` climb. | `TST-FIL-022.1`..`TST-FIL-022.3` |
| **Step 4** | Symlinks & Gradle Verification | Ensure `ANT-Android-SDKs` initialized + config symlinks (`local.properties`, `secrets.properties`, `app/google-services.json`), run targeted `./gradlew :app:testDebugUnitTest` via `parallel_sprint_lock.py`, and run `verify_requirement_governance.py`. | `TST-FIL-022.4` |

## 3. System Invariants & Constraints
1. **2-State & Runtime Huber Invariants**: `!is3StateMode` preserves `p00 = varBaro` (`0.25`) so `testPredict_clampsLargeDtSec` (`ekf.p00 < 10.0`) holds; post-anchor runtime Huber gating (`huberK = 3.0`) and standstill `dh = 0` clamping remain intact.
2. **Precedence & Hysteresis Invariants**: DEM-over-GPS precedence (`REQ-FIL-020.1`), `2.0 m` hysteresis (`REQ-FIL-020.2`), and `TECHNICAL_DEVICE_NAME` (`REQ-FIL-020.3`) remain untouched.
3. **Submodule & UI Invariants**: `ANT-Android-SDKs` stays at `0e8cea714a231fb9565170d4ed01eafa868f9798`; `N/A - no UI strings`.

## 4. Verification & Rollback Plan
- **Verification**: `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.KinematicAltitudeSlopeEkfTest" --tests "*.AltitudeAndSlopeDevice_ATTFusedTest"` (+ `MeasuredNfrBenchmarkTest`) and `python3 tools/verify_requirement_governance.py`.
- **Rollback**: Self-contained changes in 2 production files and 2 unit test files; cleanly revertible via `git checkout` with zero DB or preference migrations.

## 5. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Net-new `REQ-FIL-022` refining `REQ-FIL-016.1`/`.2` (`ATT-6279`) and `REQ-FIL-020.2` (`ATT-8081`).
- **Historical Origin & Commit Trace**: `75c1497e` (`ATT-6279`) added `KinematicAltitudeSlopeEkf`; `8b0eba41` (`ATT-8081`) added DEM precedence & `2.0 m` hysteresis.
- **Root Reason for Existing Formulation**: `p00 = varBaro` was shared across 2-state and 3-state modes and tested only with `0 m` initial baro-vs-DEM bias.
- **Preservation of Core Invariants**: 2-state `p00 = varBaro`, standstill clamping, DEM precedence, `2.0 m` hysteresis, and `@Synchronized` safety remain 100% intact.
