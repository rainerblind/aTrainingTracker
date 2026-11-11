# Stage 2: Requirement & Test Specification - ATT-7160: Prevent False 'Approaching' Climb Sheet While Stationary

**Ticket**: [ATT-7160](https://rainerblind.atlassian.net/browse/ATT-7160)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-CLM-008` (*Live Climb Movement-Bearing Gating & Cache Reconciliation*)  
**Test Spec ID**: `TST-CLM-008` | **Branch**: `bugfix/ATT-7160`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-CLM-008)

### 1.1 Problem Statement & Rationale
In `LiveClimbsRepository.kt` (line 71), `currentBearing.value ?: 0.0` substitutes synthetic North (`0.0°`) when stationary. Any stored climb starting within 250m with initial heading `315°..45°` falsely triggers `APPROACHING` while the athlete is idle indoors. Furthermore, `refreshClimbs()` fails to reconcile `_activeLiveClimb` if the active climb was deleted from `Climbs.db`.

### 1.2 Functional & Architectural Requirements
1. **Movement-Bearing Gating (`REQ-CLM-008.1`)**: `LiveClimbsRepository.updateTracking(location: LatLng, bearing: Double?)` SHALL accept a nullable movement bearing, eliminating `?: 0.0`. Candidate scan SHALL evaluate heading alignment (`bearingDiff <= 45.0`) **only** when `bearing != null`. When `bearing == null`, candidate search SHALL return early with `_activeLiveClimb.value = null`.
2. **Pause Progress Retention (`REQ-CLM-008.2`)**: When `_activeLiveClimb` is already `ON_CLIMB` or `APPROACHING`, receiving `bearing == null` SHALL NOT clear the active climb; progress evaluation SHALL continue via orthogonal polyline projection.
3. **Cache Reconciliation (`REQ-CLM-008.3`)**: In `refreshClimbs(activeRouteId)`, if `_activeLiveClimb.value?.climb?.id` is not present in the refreshed `_allClimbs.value`, `LiveClimbsRepository` SHALL set `_activeLiveClimb.value = null`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-MAP-027` (Section 3: Live Climb Tracking Engine) in `LiveClimbsRepository.kt`.
* **Historical Origin & Commit Trace**: Commit `045e14d4` (`ATT-1281`, sprint `2026-40.14`).
* **Root Reason for Existing Formulation**: `?: 0.0` was an Elvis fallback to satisfy non-null `Double`, assuming bearing was always present.
* **Preservation of Core Invariants**: Spatial thresholds (approach 250m, start 30m, summit 35m) and heading divergence limit (45°) remain strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a climb starting 34m away with initial heading 18° and `currentBearing == null`, *When* a GPS fix arrives, *Then* `_activeLiveClimb.value` remains `null`.
* *Given* an active `ON_CLIMB` athlete who pauses (`currentBearing == null`), *When* a GPS fix arrives on polyline, *Then* `ON_CLIMB` status and progress are preserved.
* *Given* `_activeLiveClimb` references a climb deleted from `Climbs.db`, *When* `refreshClimbs()` runs, *Then* `_activeLiveClimb.value` is reset to `null`.

---

## 2. Test Specification (TST-CLM-008)

### Test Case 1: Stationary Null Bearing Rejection (`TST-CLM-008.1`)
* **Scope**: JVM Unit Test (`LiveClimbsRepositoryTest.kt`)
* **Assertion**: Position within approach threshold with `bearing = null` must not trigger `APPROACHING`.

### Test Case 2: Mid-Climb Pause Progress Preservation (`TST-CLM-008.2`)
* **Scope**: JVM Unit Test (`LiveClimbsRepositoryTest.kt`)
* **Assertion**: Active `ON_CLIMB` with subsequent `bearing = null` updates retains `ON_CLIMB` and projects progress.

### Test Case 3: Stale Active Climb Cleared on Refresh (`TST-CLM-008.3`)
* **Scope**: JVM Unit Test (`LiveClimbsRepositoryTest.kt`)
* **Assertion**: Deleted climb ID in `_activeLiveClimb` resets to `null` after `refreshClimbs()`.

### Test Case 4: Tier 1 Targeted Climbs Suite (`TST-CLM-008.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CLM-008.1` | Unit | `LiveClimbsRepository.updateTracking` | `REQ-CLM-008.1` | Specified |
| `TST-CLM-008.2` | Unit | `LiveClimbsRepository.evaluateClimbProgress` | `REQ-CLM-008.2` | Specified |
| `TST-CLM-008.3` | Unit | `LiveClimbsRepository.refreshClimbs` | `REQ-CLM-008.3` | Specified |
| `TST-CLM-008.4` | Suite | `LiveClimbsRepositoryTest` | `REQ-CLM-008.1..3` | Specified |
