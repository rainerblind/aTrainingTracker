# Stage 3: Implementation Plan - ATT-6264: Prioritize Topographic Altitude (DEM) Above Barometer When Explicitly Paired

**Ticket**: [ATT-6264](https://rainerblind.atlassian.net/browse/ATT-6264)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6264`  
**Requirement Mapping**: `REQ-TRK-024` (`REQ-TRK-024.1`, `REQ-TRK-024.5`) & `REQ-SEN-025` (`REQ-SEN-025.1`)  
**Test Mapping**: `TST-TRK-016` (`TST-TRK-016.1`, `TST-TRK-016.6`, `TST-TRK-016.9`) / `TST-SEN-025.1`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
*Topografische Höhe (DEM)* (`AltitudeFromPositionDevice`) provides absolute, weather- and pocket-pressure-independent elevation. Currently, in `MySensorManager.ALTITUDE_PRIORITY`, `DeviceType.ALTITUDE_FROM_PRESSURE` (rank 0) precedes `DeviceType.ALTITUDE_FROM_POSITION` (rank 1). Because `ALTITUDE_FROM_PRESSURE` is paired by default on all smartphones equipped with a barometric pressure sensor, explicitly pairing `Topografische Höhe (DEM)` in settings has no effect on primary altitude telemetry or `VerticalSpeedAndSlopeDevice` (ascent/descent/slope) unless the athlete also manually unpairs `Höhe aus Luftdruck`.

---

## 2. Traceability & Requirements Mapping
* **`REQ-TRK-024.1`**: Reorder `MySensorManager.ALTITUDE_PRIORITY` to place `DeviceType.ALTITUDE_FROM_POSITION` at rank 0 ahead of `DeviceType.ALTITUDE_FROM_PRESSURE` (rank 1).
* **`REQ-TRK-024.5`**: Ensure `MySensorManager.getPriorityRank(SensorType.ALTITUDE, ...)` returns rank `0` for `ALTITUDE_FROM_POSITION` and rank `1` for `ALTITUDE_FROM_PRESSURE`.
* **`REQ-SEN-025.1`**: Preserve default unpaired state (`PAIRED = false`) in `DeviceManager.java` and `DevicesDatabaseManager.java`.
* **Test Mapping**:
  * `TST-TRK-016.1`: `ALTITUDE_PRIORITY` array hierarchy verification in `MySensorManagerPriorityTest`.
  * `TST-SEN-025.1`: Dynamic source switching across DEM > Barometer > Google Fused > GPS.
  * `TST-TRK-016.6`: Canonical `getPriorityRank` rank verification for altitude sensors.
  * `TST-TRK-016.9`: Targeted test suite execution for sensor and device packages.

---

## 3. System Invariants & Preserved Behavior
1. **Default Out-of-the-Box Behavior**: `ALTITUDE_FROM_POSITION` remains default unpaired (`PAIRED = false`). Fresh installs and unconfigured users retain `ALTITUDE_FROM_PRESSURE` as the active default sensor.
2. **Indoor & Cold-Start Fallback**: Before GPS fix is acquired or when indoors, `AltitudeFromPositionDevice` does not register its sensor; `MySensorManager.getBestSensor(ALTITUDE)` falls back to `ALTITUDE_FROM_PRESSURE` without latency or null errors.
3. **Filter Re-seeding Continuity**: When DEM resolves its first fix and registers, `VerticalSpeedAndSlopeDevice.checkAltitudeSourceTransition()` safely re-seeds `cAltitudeFilter` and `mAltitudeHistory` (`REQ-FIL-012.5`), preventing slope or VAM glitches.
4. **Enum Ordinal Stability**: `DeviceType.values()` declaration order is strictly preserved.
5. **UI Consistency**: Zero UI changes (`docs/design_guidelines.md` §5). `SensorSourceDialog` sorts DEM above barometer via `getPriorityRank`.

---

## 4. Proposed Architectural Changes
* **`MySensorManager.java`**:
  * Reorder `ALTITUDE_PRIORITY`:
    ```java
    protected static final DeviceType[] ALTITUDE_PRIORITY = new DeviceType[]{
            DeviceType.ALTITUDE_FROM_POSITION,
            DeviceType.ALTITUDE_FROM_PRESSURE,
            DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
            DeviceType.SPEED_AND_LOCATION_GPS
    };
    ```
* **`MySensorManagerPriorityTest.kt`**:
  * Update `speedPriority_prioritizesGoogleFusedOverGps` to verify `ALTITUDE_PRIORITY` array order.
  * Update `altitudePriority_upgradesAndFallsBackAcrossPressurePositionAndGps` to verify DEM upgrades over barometer and unregistering DEM falls back to barometer.
  * Update `getPriorityRank_returnsCanonicalIndicesAcrossAllSensorTypes` to assert rank `0` for `ALTITUDE_FROM_POSITION` and rank `1` for `ALTITUDE_FROM_PRESSURE`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Verification
* Verify Gate 3 sign-off: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.

### Step 2: Update `MySensorManager.java`
* Reorder `ALTITUDE_PRIORITY` constant array elements (swap index 0 and 1).

### Step 3: Update Unit Tests in `MySensorManagerPriorityTest.kt`
* Update `speedPriority_prioritizesGoogleFusedOverGps`, `altitudePriority_upgradesAndFallsBackAcrossPressurePositionAndGps`, and `getPriorityRank_returnsCanonicalIndicesAcrossAllSensorTypes`.

### Step 4: Targeted Unit Test Execution
* `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.banalservice.devices.*"`

### Step 5: Verify Requirement Governance
* `python3 tools/verify_requirement_governance.py`
