# Stage 2: Requirement & Test Specification - ATT-8577: ErtsPostWorkoutRecomputer VERTICAL_SPEED m/h Unit Fix

**Ticket**: `ATT-8577` (`1 SP`, Epic `ATT-166`) | **Branch**: `feature/ATT-8577` | **Date**: 2026-11-17  
**Requirement**: `REQ-FIL-021` (`REQ-FIL-021.1`..`REQ-FIL-021.2`) | **Test Spec**: `TST-FIL-021` (`TST-FIL-021.1`..`TST-FIL-021.3`)  
**Author**: AI Agent 1 (`--as agent1`) | **Localization Parity**: `N/A - no UI strings`

---

## 1. Requirement Specification (`REQ-FIL-021`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit | **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`

### 1.2 Functional & Architectural Requirements
1. **`m/s` to `m/h` Conversion, Clamping & Integer Binding (`REQ-FIL-021.1`)**:
   - In `ErtsPostWorkoutRecomputer.bindColumnValue()` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt:376-378`), when `columnName == SensorType.VERTICAL_SPEED.name` and `alt != null`, the system SHALL convert `alt.verticalSpeedMps * 3600.0`, clamp to `[-3000.0, 3000.0]` `m/h`, round via `Math.round(...)`, and bind via `stmt.bindLong(bindIdx, vamMph)`.
   - When `alt == null`, `bindColumnValue()` SHALL preserve `stmt.bindNull(bindIdx)`.
   - `SmoothedAltitudeSlopePoint.verticalSpeedMps` (`ExtendedRtsAltitudeSlopeSmoother.kt`) MUST remain in `m/s`; `ALLOWED_CANONICAL_COLUMNS`, device-attributed columns (`" ("`), `SLOPE`, `KEY_POST_WORKOUT_ERTS_SMOOTHING` (`false`), and `SensorType.ordinal()`/`name()` MUST remain unchanged.
2. **Canonical Unit Documentation Synchronization (`REQ-FIL-021.2`)**:
   - `SensorType.java:378` Javadoc and `docs/architecture/sensor_units.md:61,84` SHALL reflect that `ATT-8577` fixed the `ErtsPostWorkoutRecomputer` `m/s` mismatch while preserving the `"ATT-8577"` token checked by `SensorTypeUnitsContractTest.kt:213`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* positive climb (`alt.verticalSpeedMps = 0.25 m/s`), *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` updates `WorkoutSamples.db`, *Then* `VERTICAL_SPEED` stores integer `900` `m/h`.
* *Given* negative descent or fractional `m/h`, *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` runs, *Then* `VERTICAL_SPEED` stores `Math.round((alt.verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))` (`-900` for `-0.25 m/s`).
* *Given* extreme `ExtendedRtsAltitudeSlopeSmoother` bounds (`+3.0 m/s`, `-3.0 m/s`), *When* `bindColumnValue()` binds `VERTICAL_SPEED`, *Then* SQLite stores `+3000` and `-3000` `m/h` via `stmt.bindLong()`.
* *Given* `alt == null` and device-attributed columns (`"ALTITUDE (Höhe aus Luftdruck)"`, etc.), *When* `recomputeWorkout()` runs, *Then* `VERTICAL_SPEED` binds SQLite `NULL` when `alt == null`, device columns stay byte-for-byte untouched, and `SensorTypeUnitsContractTest` passes (`N/A - no UI strings`).

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-FIL-021` refining `REQ-FIL-017.3`, `REQ-FIL-012`, and `REQ-TRK-083`. Targets `ErtsPostWorkoutRecomputer.kt`, `SensorType.java`, `docs/architecture/sensor_units.md`, and `ErtsPostWorkoutRecomputerTest.kt`.
* **Historical Origin & Commit Trace**: `ed6acfee` (`ATT-7750`) added `ErtsPostWorkoutRecomputer.kt` & `ExtendedRtsAltitudeSlopeSmoother.kt`; `ed972bad` (`ATT-8337`) added null/sanity guards; `367a412f` (`ATT-8579`) formalized `VERTICAL_SPEED` `"m/h"`.
* **Root Reason for Existing Formulation**: `ExtendedRtsAltitudeSlopeSmoother` operates in `m/s` (`dh/dt`), and `ATT-7750` bound `alt.verticalSpeedMps` verbatim without `m/h` `INTEGER` conversion/clamping.
* **Preservation of Core Invariants**: Preserves `SmoothedAltitudeSlopePoint.verticalSpeedMps` (`m/s`), `ALLOWED_CANONICAL_COLUMNS` (`REQ-FIL-017.3`), device-column immutability, `KEY_POST_WORKOUT_ERTS_SMOOTHING = false`, `SensorType.ordinal()`/`name()`, and `"ATT-8577"` token in `sensor_units.md`.

---

## 2. Test Specification (`TST-FIL-021`)

1. **`TST-FIL-021.1` (`ErtsPostWorkoutRecomputerTest.kt` — Positive Climb, Negative Descent & Device Invariance)**:
   - Populate `WorkoutSamples.db` with climbing and descending altitude profiles + device columns; run `ErtsPostWorkoutRecomputer.recomputeWorkout()`; assert `VERTICAL_SPEED` stores rounded integer `m/h` matching `Math.round((verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))` and device columns remain untouched.
2. **`TST-FIL-021.2` (`ErtsPostWorkoutRecomputerTest.kt` — `[-3000, 3000]` Clamping & Null Safety)**:
   - Verify `+3.0 m/s` and `-3.0 m/s` clamp to `+3000` and `-3000` `m/h` via `stmt.bindLong()`, and `alt == null` binds SQLite `NULL`.
3. **`TST-FIL-021.3` (Targeted JVM Regression Suite)**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest" --tests "com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmootherTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-021.1` | Unit | `ErtsPostWorkoutRecomputerTest`: positive/negative `m/h` scaling, rounding, device invariance | `REQ-FIL-021.1` | Planned |
| `TST-FIL-021.2` | Unit | `ErtsPostWorkoutRecomputerTest`: `[-3000, 3000]` clamping at `±3.0 m/s` & `alt == null` | `REQ-FIL-021.1` | Planned |
| `TST-FIL-021.3` | Regression | `ErtsPostWorkoutRecomputerTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, `SensorTypeUnitsContractTest` | `REQ-FIL-021.1`..`REQ-FIL-021.2` | Planned |
