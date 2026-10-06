# Stage 5 Walkthrough: ATT-2338 - FIT Workout Import Lacks Elevation Data Due to Missing Enhanced Altitude Parsing

**Ticket**: [ATT-2338](https://atrainingtracker.atlassian.net/browse/ATT-2338)  
**Sub-task**: [ATT-2529](https://atrainingtracker.atlassian.net/browse/ATT-2529) (`[Test]`)  
**Parent Epic**: [ATT-1117](https://atrainingtracker.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2338`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary

Garmin FIT files recorded on modern bike computers and sport watches (Garmin Edge, Wahoo ELEMNT, Hammerhead Karoo, Coros) record altitude in modern field 78 (`enhanced_altitude` / `getEnhancedAltitude()`) and speed in field 73 (`enhanced_speed` / `getEnhancedSpeed()`), while legacy fields 2 (`altitude`) and 6 (`speed`) are frequently null or out-of-range.
`LegacyImportEngine.kt` previously read only `record.altitude` and `record.speed`, resulting in modern imported FIT files losing all elevation telemetry (flat profile chart, 0m ascent/descent) and lacking enhanced speed resolution.

With ATT-2338:
1. `LegacyImportEngine.kt` extracts altitude via `(record.enhancedAltitude ?: record.altitude)?.toDouble()`, validated against physical plausibility bounds (`-500.0 m .. 10000.0 m`).
2. Extrema positions (`minAltPos`, `maxAltPos`) and summary ascent/descent metrics are accurately calculated and written to SQLite tables.
3. Enhanced speed is parsed via `(record.enhancedSpeed ?: record.speed)?.toDouble()` with boundary validation (`0.0 .. 100.0 m/s`).
4. Lap parsing similarly extracts `lapMaxSpeed = (lapMesg.enhancedMaxSpeed ?: lapMesg.maxSpeed)?.toDouble()`.
5. 100% backward compatibility for legacy devices is verified through dedicated unit testing.

---

## 2. Changes Implemented

| File | Nature of Change |
| :--- | :--- |
| `app/src/main/java/.../migration/LegacyImportEngine.kt` | Parse `enhancedMaxSpeed` in `LapMesg`, `enhancedAltitude` (with legacy `altitude` fallback and `[-500m, 10000m]` bounds filtering) and `enhancedSpeed` in `RecordMesg`. |
| `app/src/test/.../migration/LegacyImportEngineFitAltitudeTest.kt` | Comprehensive unit tests verifying enhanced vs legacy altitude extraction, precedence, speed fallback, and sentinel filtering. |
| `docs/requirements.md` | Registered `REQ-MIG-033` (*Enhanced Altitude & Speed Telemetry Ingestion Parity in FIT Workout Import*). |
| `docs/tests.md` | Registered `TST-MIG-030` (*FIT Enhanced Altitude & Speed Ingestion Verification*). |

---

## 3. Verification & Test Evidence

### Targeted Unit Tests (`LegacyImportEngineFitAltitudeTest`):
* `testEnhancedAltitude_parsedCorrectly_whenLegacyAltitudeIsNull`: PASSED
* `testLegacyAltitude_parsedCorrectly_whenEnhancedAltitudeIsNull`: PASSED
* `testEnhancedAltitude_takesPrecedence_whenBothPresent`: PASSED
* `testEnhancedSpeed_parsedCorrectly_whenLegacySpeedIsNull`: PASSED
* `testInvalidAltitudeSentinel_filteredOut`: PASSED

### Existing FIT Tests (`LegacyImportEngineFitTest`):
* Full backward compatibility verified with 100% passing test suite.

### Clean-Room Full Suite Regression:
* Executed `./gradlew testDebugUnitTest` across all modules: 100% passed with zero regressions.

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Specification | Implementation Files | Status |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-033` | `TST-MIG-030` | `LegacyImportEngine.kt`, `LegacyImportEngineFitAltitudeTest.kt` | **Verified** |
