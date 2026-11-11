# Stage 3: Implementation Plan - ATT-7160: Prevent False 'Approaching' Climb Sheet While Stationary

**Ticket**: [ATT-7160](https://rainerblind.atlassian.net/browse/ATT-7160)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-CLM-008` (`REQ-CLM-008.1`..`REQ-CLM-008.3`)  
**Test Mapping**: `TST-CLM-008` (`TST-CLM-008.1`..`TST-CLM-008.4`)  
**Branch**: `bugfix/ATT-7160` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
In `LiveClimbsRepository.kt`, `banalRepository.currentBearing.value ?: 0.0` introduces a synthetic North (`0.0°`) fallback when stationary or indoors. Any climb starting within 250m with initial heading `315°..45°` falsely triggers `LiveClimbStatus.APPROACHING`. In addition, `refreshClimbs()` fails to reconcile `_activeLiveClimb` when the active climb is deleted from `Climbs.db`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CLM-008` (*Live Climb Detection Movement-Bearing Gating & Cache Reconciliation*)
* **Test Mapping**: `TST-CLM-008` (`TST-CLM-008.1`..`TST-CLM-008.4` in `LiveClimbsRepositoryTest.kt`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero False Approach While Stationary**: Null movement bearing rejects approach candidate matching.
2. **Mid-Climb Pause Retention**: `ON_CLIMB` and active `APPROACHING` progress remains latched during pauses.
3. **Cache Synchronization**: Refreshed climb list purges stale `_activeLiveClimb` if deleted.
4. **No Schema / UI Regressions**: Climbs database schema and `LiveClimbSheet` composables remain untouched.

---

## 4. Proposed Architectural Changes
* **`LiveClimbsRepository.kt`**:
  * Line 71: Pass `banalRepository.currentBearing.value` directly without `?: 0.0` fallback into `updateTracking(location, bearing)`.
  * Update signature to `fun updateTracking(location: LatLng, bearing: Double?)`.
  * Candidate scan guard: When `currentLive == null` or finished, return early if `bearing == null`.
  * In `refreshClimbs(activeRouteId: Long?)`, check if `_activeLiveClimb.value?.climb?.id` is in refreshed `_allClimbs.value`; reset to `null` if missing.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Nullable Bearing Gating in `LiveClimbsRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt`
* Changes: Remove `?: 0.0` on collector; update `updateTracking` to `bearing: Double?`; guard candidate loop with `if (bearing == null) { _activeLiveClimb.value = null; return }`.

### Step 2: Mid-Climb Pause Progress Preservation
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt`
* Changes: Keep `currentLive != null` check prior to candidate scanning; `evaluateClimbProgress` updates `ON_CLIMB` via polyline projection even if `bearing == null`.

### Step 3: Active Climb Cache Reconciliation on Refresh
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt`
* Changes: In `refreshClimbs`, if `_activeLiveClimb.value?.climb?.id` is not present in `list`, clear `_activeLiveClimb.value = null`.

### Step 4: Comprehensive Unit Tests in `LiveClimbsRepositoryTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* Tests: Add `testApproachingClimb_rejectedWhenBearingIsNull`, `testOnClimb_retainsTrackingDuringMidClimbPauseWithNullBearing`, and `testRefreshClimbs_clearsActiveLiveClimbIfDeletedFromDatabase`.
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted climbs suite and `tools/verify_requirement_governance.py`.
* **Rollback**: `git checkout -- app/` restores previous tracking logic.
