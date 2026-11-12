# Stage 1 Analysis: ATT-7160 - Prevent False 'Approaching' Climb Sheet While Stationary

**Ticket**: [ATT-7160](https://rainerblind.atlassian.net/browse/ATT-7160)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Branch**: `bugfix/ATT-7160` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In `LiveClimbsRepository.kt` (line 71), when `banalRepository.currentLocation` emits a location fix while stationary or indoors, `banalRepository.currentBearing.value ?: 0.0` substitutes `0.0°` (due North). Consequently, any climb stored in `Climbs.db` starting within 250m with an initial heading within `±45°` of North (`315°..45°`) falsely triggers `LiveClimbStatus.APPROACHING`, popping up `LiveClimbSheet` while the athlete is idle. Additionally, if an active climb is deleted or removed from `Climbs.db`, `refreshClimbs()` fails to reconcile `_activeLiveClimb`, leaving a stale climb reference.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Line 71: `val bearing = banalRepository.currentBearing.value ?: 0.0` injects synthetic North heading when `currentBearing` is `null`.
  * Line 145: `val bearingDiff = getBearingDifference(bearing, initialBearing)` evaluates `bearingDiff <= 45.0` against the phantom `0.0°` heading, latching candidate climbs.
  * In `refreshClimbs()`, `_allClimbs` is updated, but `_activeLiveClimb` is not validated against the new candidate IDs.
* **Independent Peer Review & Verification**:
  * Audited `LiveClimbsRepository.kt` (lines 68–75, 100–174) and `BANALServiceRepository.kt` (line 191, `_currentBearing` is `MutableStateFlow<Double?>(null)`).
  * Confirmed: `evaluateClimbProgress` does not rely on `bearing` for `ON_CLIMB` orthogonal polyline tracking (`PolylineProgressTracker.projectLocation`).
  * Therefore, passing nullable `bearing: Double?` and requiring non-null bearing only during candidate search preserves `ON_CLIMB` pause tracking while eliminating false approach activations.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Eliminate `?: 0.0` fallback; make `updateTracking(location: LatLng, bearing: Double?)` accept nullable bearing.
  * Require `bearing != null` before candidate climb heading evaluation (`bearingDiff <= 45.0`) for new approaches.
  * Preserve `ON_CLIMB` and active `APPROACHING` progress evaluation when `bearing == null` (e.g. pause mid-ascent or at red light).
  * Reconcile `_activeLiveClimb` in `refreshClimbs()`: reset `_activeLiveClimb.value = null` if its climb ID is absent from refreshed `_allClimbs`.
  * Add unit tests in `LiveClimbsRepositoryTest.kt` verifying null-bearing rejection, mid-climb pause retention, and deletion reconciliation.
* **Out-of-Scope Non-Goals**:
  * Modifying `ClimbDetector.kt` or `ClimbsDatabaseManager.kt` schema.
  * Altering `LiveClimbSheet.kt` UI composable or animations.
  * Modifying `PolylineProgressTracker.kt` projection logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-027` (Section 3: *Live Climb Tracking Engine*), targeting `LiveClimbsRepository.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-1281`, sprint `2026-40.14`, commit `045e14d4` (`V4.9.39`, Epic `ATT-66` / `ATT-2565`).
* **Root Reason for Existing Formulation**: `?: 0.0` was introduced as an Elvis fallback to satisfy non-null `Double` parameter in `updateTracking`, assuming GPS bearing would always accompany GPS fix.
* **Preservation of Core Invariants**: Spatial thresholds (approach 250m, start gate 30m, summit gate 35m) and heading divergence limit (45°) remain unchanged.

---

## 5. Architectural Strategy & High-Level Solution
1. **Nullable Bearing Signature**: Update `updateTracking(location: LatLng, bearing: Double?)`.
2. **Early Candidate Guard**: In candidate scanning (when `currentLive == null` or finished), return early without matching if `bearing == null`.
3. **Cache Reconciliation**: In `refreshClimbs(activeRouteId)`, if `_activeLiveClimb.value?.climb?.id` is not in `_allClimbs.value`, set `_activeLiveClimb.value = null`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No phantom `LiveClimbStatus.APPROACHING` when stationary / `currentBearing == null`.
  2. Zero regression in mid-climb tracking during pauses (`ON_CLIMB` status retained).
  3. Stale climbs cleared immediately on database refresh / route switch.
* **Risk Rating**: **LOW** (Scope confined to `LiveClimbsRepository.kt` state machine and unit tests; zero schema or UI changes).
