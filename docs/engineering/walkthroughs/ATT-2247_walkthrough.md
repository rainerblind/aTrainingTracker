# Stage 5: Verification Walkthrough - ATT-2247: Append Distance to Auto-Generated Workout Names for Start-Location Sessions

**Ticket**: [ATT-2247](https://rainerblind.atlassian.net/browse/ATT-2247)  
**Sub-task**: [ATT-2445](https://rainerblind.atlassian.net/browse/ATT-2445) (`[Test] Append Distance to Auto-Generated Workout Names for Start-Location Sessions`)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*[Epic] Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-TRK-012`  
**Test Mapping**: `TST-TRK-004`  
**Branch**: `feature/ATT-2247`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary

This walkthrough document verifies the complete implementation of distance-enriched auto-naming for start-location workouts in [ATT-2247](https://rainerblind.atlassian.net/browse/ATT-2247), fulfilling requirement `REQ-TRK-012` and test specification `TST-TRK-004`.

### Problem Statement & User Value
Under baseline auto-naming (`REQ-TRK-011`, [ATT-1398](https://rainerblind.atlassian.net/browse/ATT-1398)), unclustered activities departing from an athlete's home or office received identical titles (e.g. *"Radfahrt ab Zuhause"* / *"Ride from Zuhause"*), regardless of session duration or mileage. Athletes could not differentiate short recovery spins from 120 km gran fondos in their workout history list, calendar, or Strava activity feeds without opening details.

### Remediation Architecture
1. **Dynamic Unit & Precision Formatting (`WorkoutAutoNamingHelper.kt`)**:
   - Added `formatSessionDistance(context, distanceMeters, unit)`:
     - Automatically adheres to the athlete's preferred unit system (`TrainingApplication.getUnit()`).
     - In Metric mode (`MyUnits.METRIC`): converted to kilometers (`km`), formatted with `"km"`.
     - In Imperial mode (`MyUnits.IMPERIAL`): converted to miles (`mi`), formatted with `"mi"`.
     - Decimal Precision: values $< 10.0$ format with 1 decimal place (e.g. `8.2 km`, `5.1 mi`); values $\ge 10.0$ format with 0 decimal places if integer (e.g. `42 km`, `25 mi`), otherwise 1 decimal place (e.g. `42.5 km`, `26.2 mi`).
2. **Signature Extension & Overloads**:
   - Extended `WorkoutAutoNamingHelper.generateWorkoutName` with `distanceTotalMeters: Double = 0.0` and `@JvmOverloads`, guaranteeing 100% backward compatibility for existing callers.
3. **Integration Wire (`TrackerService.java`)**:
   - Passed `mDistanceTotal_m` at line 1346 during tracking finalization.
4. **9-Language Format Strings**:
   - Added 4 format string keys across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with strict `%1$s` and `%2$s` format specifier parity.
5. **Invariants Preserved**:
   - Point-to-point sessions (Case 1) remain concise without distance (e.g. *"Fahrt von Zuhause nach Büro"*).
   - Destination-only sessions (Case 4) remain without distance (e.g. *"Lauf nach Büro"*).
   - Established cluster suggestions and manual user renames take absolute precedence.

---

## 2. Verification Matrix

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1: Metric Distance Formatting & Precision** | [WorkoutAutoNamingHelperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelperTest.kt) | **PASSED** | Verifies `<10 km` formats with 1 decimal place (`8.2 km`), `10 km` and `42 km` format with 0 decimals (`42 km`), and non-integers format with 1 decimal place (`42.5 km`). |
| **AC-2: Imperial Distance Formatting & Precision** | [WorkoutAutoNamingHelperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelperTest.kt) | **PASSED** | Verifies `<10 mi` formats with 1 decimal place (`5.1 mi`), `25 mi` formats with 0 decimals (`25 mi`), and non-integers format with 1 decimal place (`26.2 mi`). |
| **AC-3: Round-Trip Enrichment Across Sports** | [WorkoutAutoNamingHelperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelperTest.kt) | **PASSED** | Verifies `BSportType.RUN` formats to `"Run from Zuhause (42 km)"`, `BSportType.BIKE` formats to `"Ride from Zuhause (42.5 km)"`, and general activities format to `"Loop from Zuhause (15 km)"`. |
| **AC-4: Start-Location Only Enrichment** | [WorkoutAutoNamingHelperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelperTest.kt) | **PASSED** | Verifies RUN formats to `"Run from Büro (8.2 km)"`, BIKE formats to `"Ride from Büro (30 km)"`, and general activities format to `"Activity from Büro (12 km)"`. |
| **AC-5: Point-to-Point & Destination-Only Exclusions** | [WorkoutAutoNamingHelperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelperTest.kt) | **PASSED** | Verifies Point-to-Point remains `"Ride from Zuhause to Büro"` and Destination-Only remains `"Run to Büro"` without distance clutter. |
| **AC-6: Zero/Negative Distance & Backward Compatibility** | [WorkoutAutoNamingHelperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelperTest.kt) | **PASSED** | Verifies calls with `0.0m`, `-50.0m`, or omitted distance argument generate legacy titles without distance. |
| **AC-7: 9-Language Translation Parity** | `TranslationParityTest.kt` | **PASSED** | 100% translation coverage across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid specifiers. |
| **AC-8: Full Suite Clean-Room Regression** | `./gradlew testDebugUnitTest` | **PASSED** | Full unit test suite executes with 100% pass rate across entire codebase. |

---

## 3. Implementation Diff Highlights

### 1. Distance Formatting & Signature in `WorkoutAutoNamingHelper.kt`
```kotlin
@JvmStatic
@JvmOverloads
fun formatSessionDistance(
    context: Context,
    distanceMeters: Double,
    unit: MyUnits = TrainingApplication.getUnit()
): String {
    val isImperial = unit == MyUnits.IMPERIAL
    val dist = if (isImperial) distanceMeters / BANALService.METER_PER_MILE else distanceMeters / 1000.0
    val unitStr = if (isImperial) "mi" else "km"

    return if (dist < 10.0) {
        String.format(Locale.getDefault(), "%.1f %s", dist, unitStr)
    } else {
        val rounded = Math.round(dist)
        if (abs(dist - rounded) < 0.05) {
            String.format(Locale.getDefault(), "%d %s", rounded, unitStr)
        } else {
            String.format(Locale.getDefault(), "%.1f %s", dist, unitStr)
        }
    }
}
```

### 2. TrackerService Wire (`TrackerService.java`)
```java
String autoName = WorkoutAutoNamingHelper.generateWorkoutName(
    this,
    resolvedSport,
    startLoc,
    endLoc,
    endpointDist,
    mDistanceTotal_m
);
```

---

## 4. Conclusion
All acceptance criteria for `REQ-TRK-012` and `TST-TRK-004` are satisfied. The feature is verified and ready for sprint branch integration.
