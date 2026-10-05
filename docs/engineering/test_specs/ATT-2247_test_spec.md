# Stage 2: Requirement & Test Specification - ATT-2247: Append Distance to Auto-Generated Workout Names for Start-Location Sessions

**Ticket**: [ATT-2247](https://rainerblind.atlassian.net/browse/ATT-2247)  
**Sub-task**: [ATT-2442](https://rainerblind.atlassian.net/browse/ATT-2442) (`[Req & Test Spec] Append Distance to Auto-Generated Workout Names for Start-Location Sessions`)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*[Epic] Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-TRK-012`  
**Test Mapping**: `TST-TRK-004`  
**Branch**: `feature/ATT-2247`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Traceability Matrix

| Requirement ID | Requirement Title | Test Specification ID | Verification File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-TRK-012** | Distance-Enriched Auto-Generated Workout Names for Start-Location Sessions | **TST-TRK-004** | `WorkoutAutoNamingHelperTest.kt`, `TranslationParityTest.kt` | **In Progress** |

---

## 2. Requirement Specification: REQ-TRK-012

### 2.1 Formal Definition
The system SHALL append the formatted total workout distance to automatically generated workout names for unclustered activities when the title is derived solely from a recognized starting location (*Lieblingsort*) (ATT-2247):

1. **Total Distance Parameter Integration (`WorkoutAutoNamingHelper.kt`)**:
   - `WorkoutAutoNamingHelper.generateWorkoutName()` SHALL accept parameter `distanceTotalMeters: Double = 0.0` with `@JvmOverloads` to preserve binary and source compatibility for existing callers.
   - In `TrackerService.java:onStopTracking()`, `mDistanceTotal_m` SHALL be passed into `generateWorkoutName()`.
2. **Activation Heuristics & Distance Formatting**:
   - When `distanceTotalMeters > 0.0` and the naming condition is Case 2 (Round Trip / Loop: start matches end or returns within start geofence) or Case 3 (Start Location Only Known):
     - The system SHALL format total distance according to athlete unit preferences (`TrainingApplication.getUnit()`).
     - In Metric mode (`MyUnits.METRIC`): distance in kilometers (`km = distanceTotalMeters / 1000.0`) with unit suffix `km`.
     - In Imperial mode (`MyUnits.IMPERIAL`): distance in miles (`mi = distanceTotalMeters / BANALService.METER_PER_MILE`) with unit suffix `mi`.
     - Clean Decimal Precision:
       - For distances $< 10.0\text{ km/mi}$: formatted with exactly 1 decimal place (`%.1f`, e.g. `8.2 km`, `5.1 mi`).
       - For distances $\ge 10.0\text{ km/mi}$: formatted with 0 decimal places if within 0.05 of an integer (`%.0f`, e.g. `42 km`, `26 mi`), otherwise with 1 decimal place (`%.1f`, e.g. `42.5 km`, `26.2 mi`).
3. **Localized Naming Templates Across 9 Locales**:
   - Running (`BSportType.RUN`): `R.string.workout_name_run_from_with_distance` (EN: `Run from %1$s (%2$s)`, DE: `Lauf ab %1$s (%2$s)`).
   - Cycling (`BSportType.BIKE`): `R.string.workout_name_ride_from_with_distance` (EN: `Ride from %1$s (%2$s)`, DE: `Radfahrt ab %1$s (%2$s)`).
   - Activity / General (`BSportType.UNKNOWN` / `CONFLICT`): `R.string.workout_name_activity_from_with_distance` (EN: `Activity from %1$s (%2$s)`, DE: `Aktivität ab %1$s (%2$s)`).
   - Round Trip / Loop fallback: `R.string.workout_name_round_trip_with_distance` (EN: `Loop from %1$s (%2$s)`, DE: `Runde ab %1$s (%2$s)`).
   - All format strings SHALL maintain 100% translation coverage across EN, DE, ES, FR, IT, JA, NL, PL, PT with exact `%1$s` and `%2$s` format specifier parity.
4. **Exclusion Invariants**:
   - Point-to-Point sessions where both start and destination are recognized (`startLocation != null && endLocation != null && startLocation.id != endLocation.id`, Case 1: e.g. `Fahrt von Zuhause nach Büro`) SHALL NOT append distance.
   - Destination-Only sessions (Case 4: e.g. `Run to Office`) SHALL NOT append distance.
   - When `distanceTotalMeters <= 0.0`, the system SHALL generate standard titles without distance per `REQ-TRK-011`.
   - Route cluster suggestions (`suggestion != null`) and manual user edits (`WorkoutRepository.setWorkoutName`) SHALL take precedence and remain immutable.

### 2.2 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement extending `REQ-TRK-011` (*Intelligent Workout Auto-Naming based on Recognized Start and Destination Lieblingsorte*).
2. **Historical Origin & Commit Trace**: Ticket `ATT-2247`, Sprint `2026-40.16`, Target Release `V4.9.39`, Epic `ATT-1396` (*[Epic] Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. **Root Reason for Existing Formulation**: `REQ-TRK-011` established baseline location auto-naming, but sessions departing from the athlete's home or office produced identical titles (e.g. `Radfahrt ab Zuhause`), making it impossible to differentiate short recovery spins from gran fondos in list views, calendar, and Strava feeds without opening details.
4. **Preservation of Core Invariants**: Point-to-point naming, cluster suggestion precedence, user rename immutability, hardware sport type inference, single-thread DB serialization, and 9-language translation parity MUST NOT be broken. Backward-compatible calls omitting distance continue to generate standard titles without distance.

### 2.3 Acceptance Criteria (Given-When-Then)
- **AC-1 (Start-Location Distance Invariant)**:
  - *Given* an unclustered bike ride starting and finishing at "Zuhause" covering 42,000 meters in Metric mode,
  - *When* the workout is finalized in `TrackerService`,
  - *Then* `WORKOUT_NAME` SHALL be set to `"Radfahrt ab Zuhause (42 km)"` (DE) or `"Ride from Zuhause (42 km)"` (EN).
- **AC-2 (Decimal Precision Rules)**:
  - *Given* an unclustered run starting from "Büro" covering 8,200 meters in Metric mode,
  - *When* finalized,
  - *Then* `WORKOUT_NAME` SHALL be set to `"Lauf ab Büro (8,2 km)"` (DE) or `"Run from Büro (8.2 km)"` (EN).
- **AC-3 (Imperial Unit System Parity)**:
  - *Given* an athlete with Imperial units (`MyUnits.IMPERIAL`) running 26.2 miles (42,164.8 m),
  - *When* finalized,
  - *Then* `WORKOUT_NAME` SHALL be set to `"Run from Home (26.2 mi)"`.
- **AC-4 (Point-to-Point & Destination Exclusion)**:
  - *Given* a point-to-point ride from "Zuhause" to "Büro" covering 15,000 meters,
  - *When* finalized,
  - *Then* `WORKOUT_NAME` SHALL remain `"Fahrt von Zuhause nach Büro"` without distance suffix.
- **AC-5 (Backward Compatibility)**:
  - *Given* a call to `generateWorkoutName` without passing `distanceTotalMeters`,
  - *When* executed,
  - *Then* it SHALL format the workout name without distance, preserving 100% backward compatibility with `REQ-TRK-011`.

---

## 3. Test Specification: TST-TRK-004

### 3.1 Verification Scope
The test suite validates distance formatting precision, metric/imperial unit conversions, sport-type template selection, exclusion cases (point-to-point, destination-only, non-positive distance), backward compatibility, and 9-language translation parity.

### 3.2 Test Cases (`WorkoutAutoNamingHelperTest.kt`)

#### Case 1: Metric Distance Formatting Precision Tests
- **Objective**: Verify decimal precision formatting for metric distances.
- **Scenarios**:
  - Distance = 8,200 m -> `8.2 km` (1 decimal place for $< 10\text{ km}$).
  - Distance = 42,000 m -> `42 km` (0 decimal places for whole number $\ge 10\text{ km}$).
  - Distance = 42,500 m -> `42.5 km` (1 decimal place for non-integer $\ge 10\text{ km}$).
  - Distance = 500 m -> `0.5 km` (1 decimal place for sub-kilometer).

#### Case 2: Imperial Distance Formatting Precision Tests
- **Objective**: Verify distance formatting under `MyUnits.IMPERIAL`.
- **Scenarios**:
  - Distance = 8,207.65 m (5.1 miles) -> `5.1 mi`.
  - Distance = 40,233.6 m (25.0 miles) -> `25 mi`.
  - Distance = 42,164.81 m (26.2 miles) -> `26.2 mi`.

#### Case 3: Start-Location & Round-Trip Auto-Naming Integration Tests
- **Objective**: Verify formatted name composition across sport types.
- **Scenarios**:
  - **Round Trip (Run)**: Start = End = "Zuhause", 10 km -> `"Run from Zuhause (10 km)"`.
  - **Round Trip (Bike)**: Start = End = "Zuhause", 42 km -> `"Ride from Zuhause (42 km)"`.
  - **Round Trip (Other)**: Start = End = "Zuhause", 15 km -> `"Loop from Zuhause (15 km)"`.
  - **Start Only (Run)**: Start = "Park", End = null, 8.2 km -> `"Run from Park (8.2 km)"`.
  - **Start Only (Bike)**: Start = "Park", End = null, 30 km -> `"Ride from Park (30 km)"`.
  - **Start Only (Other)**: Start = "Park", End = null, 12 km -> `"Activity from Park (12 km)"`.

#### Case 4: Exclusion & Invariant Verification
- **Objective**: Verify that distance is NOT appended in excluded cases.
- **Scenarios**:
  - **Point-to-Point**: Start = "Zuhause", End = "Büro", 15 km -> `"Ride from Zuhause to Büro"` (no distance).
  - **Destination Only**: Start = null, End = "Büro", 10 km -> `"Ride to Büro"` (no distance).
  - **Non-positive Distance**: Start = "Zuhause", End = null, 0 m -> `"Ride from Zuhause"` (no distance).
  - **Backward-Compatible Overload**: `generateWorkoutName(context, sport, start, end, endpointDist)` without distance argument -> `"Ride from Zuhause"` (no distance).

#### Case 5: 9-Language Localization Audit (`TranslationParityTest.kt`)
- **Objective**: Verify all 4 new string keys exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with matching format specifiers:
  - `workout_name_run_from_with_distance` (`%1$s`, `%2$s`)
  - `workout_name_ride_from_with_distance` (`%1$s`, `%2$s`)
  - `workout_name_activity_from_with_distance` (`%1$s`, `%2$s`)
  - `workout_name_round_trip_with_distance` (`%1$s`, `%2$s`)

#### Case 6: Full Clean-Room Test Suite Regression
- **Objective**: Execute `./gradlew testDebugUnitTest` across all modules.
- **Assertions**: 100% test pass rate with zero regressions.
