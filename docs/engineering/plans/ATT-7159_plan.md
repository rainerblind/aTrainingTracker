# Stage 3: Implementation Plan - ATT-7159: Reject Altitude Spikes, Validate Workout Climbs via DEM & Clean Climbs.db

**Ticket**: [ATT-7159](https://rainerblind.atlassian.net/browse/ATT-7159)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-CLM-009` (`REQ-CLM-009.1`..`REQ-CLM-009.3`)  
**Test Mapping**: `TST-CLM-009` (`TST-CLM-009.1`..`TST-CLM-009.4`)  
**Branch**: `bugfix/ATT-7159` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
`Climbs.db` contains corrupted climbs (`104.6%` grade, `max_grade = 367.4%`) and phantom climbs caused by cold-air barometer thermal drift on flat/downhill winter rides (`+50m` barometric drift vs `-9.6m` DEM). `ClimbDetector` lacks vertical jump and gradient ceiling checks, `WorkoutRepository` feeds raw `ALTITUDE` without DEM validation, and existing `Climbs.db` instances lack an automated cleanup pass.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CLM-009` (*Climb Detection Spike Guards, DEM Ground-Truth Cross-Validation & Corrupted Climbs Self-Healing*)
* **Test Mapping**: `TST-CLM-009` (`TST-CLM-009.1`..`TST-CLM-009.4`)

---

## 3. System Invariants & Preserved Behavior
1. **Real Mountain & Hill Preservation**: Real ascents satisfying plausible limits (`<= 25%` avg grade) are never dropped.
2. **Planned GPX Route Invariance**: Route climb detection remains unaffected.
3. **Database Schema Stability**: Zero SQLite table alterations in `Climbs.db` or `ElevationCache.db`.
4. **Idempotent Self-Healing**: V2 cleanup runs once and is an O(1) no-op on subsequent launches.

---

## 4. Proposed Architectural Changes
* **`ClimbDetector.kt`**:
  * Step-jump guard: Break ascent candidate if $|\Delta\text{alt}| > 25\text{ m}$ in 1 step or segment grade $> 45.0\%$ over $d \ge 10\text{ m}$.
  * Ceiling limits: Reject candidates with `avgGrade > 25.0%` or `maxGrade > 45.0%`.
  * Post-summit calibration snap: Reject peak if altitude drops $> 35\text{ m}$ within $< 50\text{ m}$ after summit.
* **`WorkoutRepository.kt`**:
  * In `extractAndPersistClimbsFromWorkout()`: Query `ElevationCacheDatabaseManager` for start, mid, summit. Filter if DEM gain $< \text{minGain} \times 0.75$, DEM grade $< 2.2\%$, or barometric gain exceeds $\max(\text{demGain} \times 1.6 + 18.0, \text{demGain} + 35.0)$.
  * In `checkAndTriggerHistoricalClimbBackfill()`: Add `cleanupCorruptedWorkoutClimbs()` gated on `PREF_CLIMBS_WORKOUT_CLEANUP_V2`. Purge corrupted `route_id IS NULL` climbs.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Discontinuity & Plausibility Spike Guards in `ClimbDetector.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt`
* Changes: Add $|\Delta\text{alt}| > 25\text{ m}$ step filter, $45\%$ steep segment break, $25\%$ avg / $45\%$ max ceiling, and post-summit snap check.

### Step 2: DEM Ground-Truth Cross-Validation in `WorkoutRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Changes: Cross-check candidate workout climbs against `ElevationCacheDatabaseManager.getElevation()`; filter thermal drift phantoms.

### Step 3: Self-Healing Cleanup Migration (`PREF_CLIMBS_WORKOUT_CLEANUP_V2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Changes: Add `cleanupCorruptedWorkoutClimbs()`, purge invalid `route_id IS NULL` rows from `Climbs.db`, refresh `LiveClimbsRepository`.

### Step 4: Unit Tests in `ClimbDetectorTest.kt` & `WorkoutHistoricalClimbBackfillTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetectorTest.kt`, `WorkoutHistoricalClimbBackfillTest.kt`
* Tests: Test step-jump rejection, thermal drift filtering, and v2 database cleanup.
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted climbs and aftermath tests, followed by `tools/verify_requirement_governance.py`.
* **Rollback**: Revert `ClimbDetector.kt` and `WorkoutRepository.kt` edits via `git restore`.
