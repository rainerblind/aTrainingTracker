# Stage 3: Implementation Plan - ATT-2338: FIT Workout Import Lacks Elevation Data Due to Missing Enhanced Altitude Parsing

**Ticket**: [ATT-2338](https://atrainingtracker.atlassian.net/browse/ATT-2338)  
**Sub-task**: [ATT-2527](https://atrainingtracker.atlassian.net/browse/ATT-2527) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1117](https://atrainingtracker.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2338`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Architectural Design & SWE.2 Structure

### Component Flow Diagram:
```
           +---------------------------------------------+
           |        FIT Binary File Ingestion            |
           |   (com.garmin.fit.Decode / MesgBroadcaster) |
           +---------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|               LegacyImportEngine.importFromFitResult              |
|                                                                   |
| 1. LapMesg Parsing:                                               |
|    - lapMaxSpeed = (lapMesg.enhancedMaxSpeed ?:                   |
|                     lapMesg.maxSpeed)?.toDouble()                 |
|                                                                   |
| 2. RecordMesg Parsing:                                            |
|    - altVal = (record.enhancedAltitude ?:                         |
|                record.altitude)?.toDouble()                       |
|      -> Validation: altVal in -500.0..10000.0                     |
|      -> Extrema tracking: minAltVal, maxAltVal, min/maxAltPos     |
|      -> Ingestion: SensorType.ALTITUDE + altitudes.add(altVal)    |
|                                                                   |
|    - spdVal = (record.enhancedSpeed ?: record.speed)?.toDouble()  |
|      -> Validation: spdVal in 0.0..100.0                          |
|      -> Ingestion: SensorType.SPEED_mps                           |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|               recalculateStats & Database Persistence             |
|  - WorkoutSummaries.ALTITUDE_ASCENT / DESCENT calculated          |
|  - WorkoutSamples written with complete ALTITUDE & SPEED streams  |
|  - Route Clustering & Period Repositories Updated                 |
+-------------------------------------------------------------------+
```

---

## 2. Invariants & Guardrails

1. **Legacy Device Compatibility**:
   - `record.enhancedAltitude ?: record.altitude` ensures older devices that only output field 2 (`altitude`) continue parsing without error.
2. **Sentinel & Boundary Filtering**:
   - Altitudes outside `[-500.0, 10000.0]` and speeds outside `[0.0, 100.0]` are filtered to prevent barometric spikes or unscaled sentinel masks from corrupting telemetry databases.
3. **Preservation of Existing Public APIs**:
   - `importFromFit`, `importFromFitResult`, and `importFromFitInternal` retain their signatures and contracts.
4. **Clean Decoupling**:
   - Zero changes required to `MainActivityWithNavigation`, `WorkoutNavigationEvents`, or other screens.

---

## 3. Step-by-Step Implementation Steps

### Step 1: Update `LegacyImportEngine.kt` FIT Record & Lap Parsing
- In `importFromFitResult`:
  - Lap parsing: `val lapMaxSpeed = (lapMesg.enhancedMaxSpeed ?: lapMesg.maxSpeed)?.toDouble()`
  - Record parsing:
    - Extract `val altVal = (record.enhancedAltitude ?: record.altitude)?.toDouble()`
    - If `altVal != null && altVal in -500.0..10000.0`:
      - If GPS coordinate is valid, check against `minAltVal` / `maxAltVal` and update `minAltPos` / `maxAltPos`.
      - Store `values.put(SensorType.ALTITUDE.name, altVal)` and add to `altitudes`.
    - Extract `val spdVal = (record.enhancedSpeed ?: record.speed)?.toDouble()`
    - If `spdVal != null && spdVal in 0.0..100.0`:
      - Store `values.put(SensorType.SPEED_mps.name, spdVal)`.

### Step 2: Implement Unit Test Suite `LegacyImportEngineFitAltitudeTest.kt`
- Create `LegacyImportEngineFitAltitudeTest` in `app/src/test/java/com/atrainingtracker/trainingtracker/migration/`:
  - `testEnhancedAltitude_parsedCorrectly_whenLegacyAltitudeIsNull`
  - `testLegacyAltitude_parsedCorrectly_whenEnhancedAltitudeIsNull`
  - `testEnhancedAltitude_takesPrecedence_whenBothPresent`
  - `testEnhancedSpeed_parsedCorrectly`
  - `testInvalidAltitudeSentinel_filteredOut`

### Step 3: Run Targeted Tests & Validate
- Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.migration.LegacyImportEngineFitAltitudeTest`.

### Step 4: Full Regression & Gate Transitions
- Complete Gate 4 audit and proceed to clean-room regression.

---

## 4. Review Gates Checklist

- [x] SWE.2 architectural component flow formulated.
- [x] Invariants and boundary guardrails specified.
- [x] Step-by-step atomic implementation steps documented.
- [x] Target test coverage mapped to `TST-MIG-030`.
