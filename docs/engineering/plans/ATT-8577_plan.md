# Stage 3: Implementation Plan - ATT-8577: ErtsPostWorkoutRecomputer VERTICAL_SPEED m/h Unit Fix

**Ticket**: `ATT-8577` (`1 SP`, Epic `ATT-166`) | **Branch**: `feature/ATT-8577` | **Date**: 2026-11-17  
**Requirement**: `REQ-FIL-021` (`REQ-FIL-021.1`..`REQ-FIL-021.2`) | **Test Spec**: `TST-FIL-021` (`TST-FIL-021.1`..`TST-FIL-021.3`)  
**Author**: AI Agent 1 (`--as agent1`) | **UI Consistency**: `N/A - no UI strings`

---

## 1. Architecture & Layering Design (SWE.2)
- **Execution & Threading Flow**: `TrackerService.endWorkout()` (`:2490`, guarded by `KEY_POST_WORKOUT_ERTS_SMOOTHING = false`) invokes `ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking()` -> `withContext(Dispatchers.IO)` -> `ExtendedRtsAltitudeSlopeSmoother.smooth()` -> `updateSamplesDatabase()` -> `bindColumnValue()`. No new threads, dispatchers, UI layers, or SQLite schema migrations are introduced.
- **Layer Boundary Separation**: `ExtendedRtsAltitudeSlopeSmoother` (`banalservice.odometry`) stays purely in SI `m/s` (`SmoothedAltitudeSlopePoint.verticalSpeedMps`), while unit conversion (`m/s -> m/h`), clamping (`[-3000.0, 3000.0]`), rounding (`Math.round`), and integer SQLite binding (`stmt.bindLong`) occur at the `WorkoutSamples.db` persistence boundary inside `ErtsPostWorkoutRecomputer.bindColumnValue()` (`trainingtracker.database`), matching `SensorType.VERTICAL_SPEED` (`SensorValueType.INTEGER`, `internalUnit = "m/h"`).

## 2. Atomic Stage 4 Implementation Sequence & Traceability
| Step | Target File(s) | Action & Scope | REQ / TST Mapping |
| :--- | :--- | :--- | :--- |
| **Step 1** | `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt` (`:376-378`) | In `bindColumnValue()` for `SensorType.VERTICAL_SPEED.name`, when `alt != null`, compute `val vamMph = Math.round((alt.verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))` and call `stmt.bindLong(bindIdx, vamMph)`; when `alt == null`, preserve `stmt.bindNull(bindIdx)`. Keep `SLOPE`, `ALLOWED_CANONICAL_COLUMNS`, and device columns untouched. | `REQ-FIL-021.1`, `TST-FIL-021.1`, `TST-FIL-021.2` |
| **Step 2** | `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java` (`:378`), `docs/architecture/sensor_units.md` (`:61, 84`) | Update `VERTICAL_SPEED` Javadoc and `sensor_units.md` Range/Section 2 note to record that `ATT-8577` fixed `ErtsPostWorkoutRecomputer` to emit `[-3000, 3000]` `m/h` integers, strictly preserving the `"ATT-8577"` token asserted by `SensorTypeUnitsContractTest.kt:213`. | `REQ-FIL-021.2`, `TST-FIL-021.3` |
| **Step 3** | `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt` | Add targeted unit tests asserting: (a) positive climb & negative descent `m/h` scaling + rounding; (b) clamping of `±3.0 m/s` (`±10800 m/h`) to `±3000 m/h`; (c) `alt == null` binds SQLite `NULL`; (d) device-attributed columns remain byte-for-byte unchanged. | `REQ-FIL-021.1`, `TST-FIL-021.1`, `TST-FIL-021.2` |
| **Step 4** | Gradle & Governance | Execute targeted `./gradlew testDebugUnitTest` (via `parallel_sprint_lock.py`) covering `ErtsPostWorkoutRecomputerTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, and `SensorTypeUnitsContractTest`, plus `verify_requirement_governance.py`. | `TST-FIL-021.3` |

## 3. System Invariants & Constraints
1. **API & Data Invariants**: `SmoothedAltitudeSlopePoint.verticalSpeedMps` remains `m/s`; `ALLOWED_CANONICAL_COLUMNS` strict scope (`REQ-FIL-017.3`) & raw device columns (`" ("`) untouched; `SLOPE` binding (`:374`) and `KEY_POST_WORKOUT_ERTS_SMOOTHING` (`false`) unchanged.
2. **Enum & Schema Invariants**: `SensorType.ordinal()`, `SensorType.name()`, `WorkoutSamples.db` schema, and `"ATT-8577"` token in `docs/architecture/sensor_units.md` preserved.
3. **UI & Submodule Invariants**: `N/A - no UI strings`; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.

## 4. Verification & Rollback Plan
- **Verification**: Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest" --tests "com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmootherTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest"` and `python3 tools/verify_requirement_governance.py`.
- **Rollback**: Single-line SQLite binding change + test/doc updates; cleanly revertible via `git checkout` with zero DB schema or preference migrations.

## 5. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Net-new `REQ-FIL-021` refining `REQ-FIL-017.3`, `REQ-FIL-012`, and `REQ-TRK-083`. Targets `ErtsPostWorkoutRecomputer.kt`, `SensorType.java`, `docs/architecture/sensor_units.md`, and `ErtsPostWorkoutRecomputerTest.kt`.
- **Historical Origin & Commit Trace**: `ed6acfee` (`ATT-7750`) introduced ERTS smoothing; `ed972bad` (`ATT-8337`) added sanity/null guards; `367a412f` (`ATT-8579`) codified `VERTICAL_SPEED` `"m/h"`.
- **Root Reason for Existing Formulation**: `ExtendedRtsAltitudeSlopeSmoother` computes SI `m/s`, and `ErtsPostWorkoutRecomputer` bound `alt.verticalSpeedMps` verbatim without `m/h` `INTEGER` conversion/clamping.
- **Preservation of Core Invariants**: Keeps `ExtendedRtsAltitudeSlopeSmoother` SI contract intact, preserves device-column isolation and opt-in `false` default, and aligns persisted `VERTICAL_SPEED` with live `[-3000, 3000]` `m/h` producers.
