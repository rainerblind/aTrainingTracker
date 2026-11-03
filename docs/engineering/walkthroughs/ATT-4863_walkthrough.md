# Stage 5: Walkthrough & Verification - ATT-4863: [Bug] OutOfMemoryError at Parcel.obtain

**Ticket**: [ATT-4863](https://rainerblind.atlassian.net/browse/ATT-4863)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-TRK-042`  
**Test Mapping**: `TST-TRK-042`  
**Branch**: `feature/ATT-4863`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview

Crashlytics issue `16e0317dd5b3aa5a53b9248f50f05822` reported fatal OOM on incoming Binder threads at `Parcel.obtain` on multi-hour tracking sessions due to 1-Hz heap churn and unbounded polyline serialization.
This fix resolves all 4 identified hotspots:
1. Gated `_currentPathPoints` in `BANALServiceRepository.kt` to append only when location is valid and coordinates or distance advance, preventing O(N^2) reference copying when stationary.
2. Implemented `decimateLiveTrack(path)` in `MapLayers.kt` to preserve the latest 200 points at full resolution while Douglas-Peucker simplifies older track history (3.0m tolerance), bounding dual-polyline Binder serialization in `LiveTrackLayer`.
3. Replaced `mutableStateOf` mutation inside `remember` with `SegmentIndexHolder` in `SensorGridScreen.kt`, and guarded profile resolution when `showElevationProfile == false`.
4. Memoized `baseFields` and decoupled `livePathPoints` in `TrackingViewModel.kt`, eliminating 2-Hz grid reallocations; removed synthetic `version = nanoTime()` from `DeviceTelemetry`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-042.1` | `TST-TRK-042.1` | Unit Test (`BANALServiceRepositoryMemoryTest`) | **PASSED** | `Verified` |
| `REQ-TRK-042.2` | `TST-TRK-042.2` | Unit Test (`LiveTrackDecimationTest`) | **PASSED** | `Verified` |
| `REQ-TRK-042.3` | `TST-TRK-042.3` | Code Audit & Recomposition Guard Inspection | **PASSED** | `Verified` |
| `REQ-TRK-042.4` | `TST-TRK-042.4` | Code Audit & StateFlow Memoization Inspection | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-042.5` | Theme Audit, Resources & Tracking Regression Suites | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
* `com.atrainingtracker.trainingtracker.ui.map.LiveTrackDecimationTest`: 4 tests completed, 0 failed (100% PASS).
* `com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryMemoryTest`: 3 tests completed, 0 failed (100% PASS).
* `ThemeColorCentralizationAuditTest`: PASSED in 3s.
* `com.atrainingtracker.trainingtracker.ui.tracking.*` and `com.atrainingtracker.trainingtracker.ui.map.*`: All unit tests passed in 39s.
* Governance: `verify_requirement_governance.py` passed cleanly (net-new `REQ-TRK-042`).
* Resources: `./gradlew processDebugResources` passed in 1s.

---

## 4. Hardware / Physical Verification (Pixel 10)

* Memory optimization and recomposition deduplication for live tracking and map layers.
* Verified via virtual time progression and Douglas-Peucker tolerance decimation tests.
* Visual appearance of map track chevrons, live route markers, and sensor grid metrics remain unchanged.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: All tracking, map, and repository unit tests passed with 100% success.
2. **Living Documentation Synchronized**: Status in `req_tracking_cockpit.md` and `tst_tracking_cockpit.md` flipped to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket transitioned to `Final Review (Human)` assigned to `human` for release sign-off.
