# Stage 2: Requirement & Test Specification - ATT-7159: Reject Altitude Spikes, Validate Workout Climbs via DEM & Clean Climbs.db

**Ticket**: [ATT-7159](https://rainerblind.atlassian.net/browse/ATT-7159)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-CLM-009` (*Climb Detection Spike Guards, DEM Cross-Validation & Self-Healing*)  
**Test Spec ID**: `TST-CLM-009` | **Branch**: `bugfix/ATT-7159`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-CLM-009)

### 1.1 Problem Statement & Rationale
`Climbs.db` contains corrupted climbs (e.g. `541m @ 104.6%`, `max_grade = 367.4%` on a flat street) and phantom climbs caused by cold-air barometer thermal drift on flat/downhill winter rides (`+50m` barometric drift vs `-9.6m` DEM). `ClimbDetector` lacks vertical jump and gradient ceiling checks, `WorkoutRepository` feeds raw `ALTITUDE` without DEM validation, and existing `Climbs.db` instances lack an automated cleanup pass.

### 1.2 Functional & Architectural Requirements
1. **Vertical Discontinuity & Plausibility Spike Guards (`REQ-CLM-009.1`)**: `ClimbDetector.detectClimbs()` SHALL reject single-step vertical jumps ($|\Delta\text{alt}| > 25.0\text{ m}$ or segment gradient $> 45.0\%$ over $d \ge 10\text{ m}$), terminate candidates with `avgGradePercent > 25.0%` or `maxGradePercent > 45.0%`, and reject calibration snap peaks where altitude drops $> 35\text{ m}$ within $< 50\text{ m}$ post-summit.
2. **DEM Ground-Truth Cross-Validation (`REQ-CLM-009.2`)**: In `WorkoutRepository.extractAndPersistClimbsFromWorkout()` (and backfill), candidate workout climbs (`routeId == null`) SHALL cross-check start, mid, and summit coordinates against `ElevationCacheDatabaseManager`. Candidates SHALL be rejected if DEM gain $< \text{minGain} \times 0.75$, DEM grade $< 2.2\%$, or barometric gain exceeds $\max(\text{demGain} \times 1.6 + 18.0, \text{demGain} + 35.0)$.
3. **Idempotent Corrupted Climbs Cleanup (`REQ-CLM-009.3`)**: `WorkoutRepository` SHALL execute a startup cleanup migration gated on `climbs_workout_cleanup_v2` (`PREF_CLIMBS_WORKOUT_CLEANUP_V2`). It SHALL purge all `route_id IS NULL` rows in `Climbs.db` violating plausibility bounds, mark the preference `true`, and trigger `LiveClimbsRepository.refreshClimbs()`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MAP-064` (*Workout Climb Ingestion*) & `REQ-MAP-066` (*Historical Climb Backfill*), targeting `ClimbDetector.kt` and `WorkoutRepository.kt`.
* **Historical Origin & Commit Trace**: Commit `c7438e78` (`ATT-2766`), `8d42cf31` (`ATT-6691`).
* **Root Reason for Existing Formulation**: Early ingestion assumed continuous, drift-free barometer readings without GPS altitude cold-start jumps.
* **Preservation of Core Invariants**: Scoring formula ($\text{Score} = \text{length} \times \text{gradient}$), category thresholds (`HC`..`CAT_4`), and 50m spatial deduplication remain preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a track with an altitude jump from 602m to 1,168m over 541m (104.6% grade), *When* `ClimbDetector` runs, *Then* zero climbs are detected.
* *Given* a flat commute with +52m barometric drift and -6m DEM gain, *When* `extractAndPersistClimbsFromWorkout()` validates against DEM, *Then* the phantom climb is rejected.
* *Given* `Climbs.db` with corrupted `route_id IS NULL` climbs (`avgGrade > 25%`), *When* `v2` cleanup runs, *Then* invalid climbs are purged and `LiveClimbsRepository` refreshed.

---

## 2. Test Specification (TST-CLM-009)

### Test Case 1: Altitude Step Discontinuity & Extreme Gradient Rejection (`TST-CLM-009.1`)
* **Scope**: JVM Unit Test (`ClimbDetectorTest.kt`)
* **Assertion**: Tracks with $|\Delta\text{alt}| > 25\text{ m}$ single-step jumps or average grade $> 25\%$ yield zero detected climbs.

### Test Case 2: Barometer Thermal Drift DEM Ground-Truth Cross-Validation (`TST-CLM-009.2`)
* **Scope**: JVM Unit Test (`WorkoutClimbIngestionTest.kt`)
* **Assertion**: Candidate climbs where barometric gain exceeds DEM ground-truth threshold are rejected from `Climbs.db`.

### Test Case 3: Climbs.db v2 Cleanup Migration (`TST-CLM-009.3`)
* **Scope**: JVM Unit Test (`WorkoutHistoricalClimbBackfillTest.kt`)
* **Assertion**: Corrupted `route_id IS NULL` climbs are deleted, `climbs_workout_cleanup_v2` is set to true, and subsequent runs are no-ops.

### Test Case 4: Tier 1 Targeted Climbs & Aftermath Suite (`TST-CLM-009.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CLM-009.1` | Unit | `ClimbDetector.detectClimbs` | `REQ-CLM-009.1` | Specified |
| `TST-CLM-009.2` | Integration | `WorkoutRepository.extractAndPersistClimbsFromWorkout` | `REQ-CLM-009.2` | Specified |
| `TST-CLM-009.3` | Integration | `WorkoutRepository.cleanupCorruptedWorkoutClimbs` | `REQ-CLM-009.3` | Specified |
| `TST-CLM-009.4` | Suite | Climbs & Aftermath Package Suites | `REQ-CLM-009.1..3` | Specified |
