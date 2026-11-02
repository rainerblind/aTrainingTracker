# Stage 5: Walkthrough & Verification - ATT-4923: Live tracking: make track append, live-track decimation and sensor-field combine O(1) per tick (ATT-4863 follow-up)

**Ticket**: [ATT-4923](https://rainerblind.atlassian.net/browse/ATT-4923) | **Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*) | **Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-042` (`REQ-TRK-042.1`, `REQ-TRK-042.2`, `REQ-TRK-042.4`)  
**Test Mapping**: `TST-TRK-042` (`TST-TRK-042.1`, `TST-TRK-042.2`, `TST-TRK-042.3`, `TST-TRK-042.4`)  
**Branch**: `feature/ATT-4923` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-02  

---

## 1. Executive Summary & Verification Overview

Follow-up to `ATT-4863` (`V4.9.41`, commit `06195f2a`). While `ATT-4863` stopped live-tracking allocations on stationary ticks, three 1-Hz allocation and CPU hotspots remained active while moving.
This implementation eliminates all 3 moving hotspots:
1. **Amortized O(1) Track Append (`SnapshotChunkedList`)**: Created `SnapshotChunkedList<E>` ($C = 256$, `RandomAccess`) in `com.atrainingtracker.trainingtracker.util` and wired into `BANALServiceRepository.kt` for `_currentTrack` and `_currentPathPoints`, eliminating $O(N)$ full-list copying upon every moving tick while maintaining `clearBreadcrumbs()` reset contracts and fast $O(1)$ size equality.
2. **Incremental Chunked Decimation (`IncrementalTrackDecimator`)**: Implemented `IncrementalTrackDecimator` in `MapLayers.kt` adhering to Rule 66 (plain holder in `remember` without Compose `MutableState` writes). Historical chunks ($C = 200$) freeze permanently once upon boundary crossing, eliminating coordinate jitter across ticks and bounding Douglas-Peucker to $O(1)$ per tick while preserving the 200-point unsimplified tail.
3. **Combine Decoupling (`TrackingViewModel.kt`)**: Decoupled `currentPathPoints` from `loadSensorFieldStates()` combine. Live path points are observed in a dedicated pipeline strictly when `showElevationProfile == true`, ensuring sensor fields update at most once per sensor tick ($\le 1$ Hz).
4. **UI Change Assessment**: Pure performance and robustness improvement; zero visual UI change intended.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-042.1` | `TST-TRK-042.1` | Unit Benchmark (`SnapshotChunkedListTest`, 15,000 points) | **PASSED** | `Verified` |
| `REQ-TRK-042.2` | `TST-TRK-042.2` | Unit Test (`LiveTrackDecimationTest`, vertex stability) | **PASSED** | `Verified` |
| `REQ-TRK-042.4` | `TST-TRK-042.3` | Unit Test (`TrackingViewModelMapStateTest`, combine decoupling) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-042.4` | Tier 1 Targeted Test Suites & Theme Audit | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Executed Test Scope
* `com.atrainingtracker.trainingtracker.util.SnapshotChunkedListTest`: 6 tests completed, 0 failed (15,000-point synthetic track benchmark appends in bounded time < 20 ms).
* `com.atrainingtracker.trainingtracker.ui.map.LiveTrackDecimationTest`: 7 tests completed, 0 failed (historical chunk freezing, vertex stability across ticks, and 15,000-point decimation benchmark).
* `com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewModelMapStateTest`: 12 tests completed, 0 failed (verified `currentPathPoints` emission does not leak into sensor field combine when elevation is hidden).
* `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`: PASSED.
* Full targeted package test run: 1,203 unit tests completed across `ui.map.*`, `ui.tracking.*`, `util.*`, `banalservice.*`, and `build.*` with 100% pass rate.
* Governance: `python3 tools/verify_requirement_governance.py` reported clean `PASS` with all 4 mandatory Chesterton's Fence archaeology fields verified.

---

## 4. Invariant & Governance Verification

1. **Rule 66 Adherence**: `IncrementalTrackDecimator` is a plain holder object; zero `MutableState` written during composition or inside `remember`.
2. **Living Documentation Synchronized**: `REQ-TRK-042` and `TST-TRK-042` in `req_tracking_cockpit.md` and `tst_tracking_cockpit.md` updated to `Verified`.
3. **Traceability**: All 4 acceptance criteria of ticket ATT-4923 met with automated test evidence.
