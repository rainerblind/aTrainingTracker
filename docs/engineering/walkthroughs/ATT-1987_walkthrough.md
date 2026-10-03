# Stage 5: Walkthrough & Verification - ATT-1987: [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs

**Ticket**: [ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)  
**Sub-task**: [ATT-2038](https://rainerblind.atlassian.net/browse/ATT-2038) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*), `REQ-UI-234` (*UI & Interaction Design System: Cross-Domain Chart Viewport Separation*)  
**Test Mapping**: `TST-UI-190`  
**Branch**: `feature/ATT-1987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

During Sprint 2026-40.10 review on physical Google Pixel 10 hardware, user testing verified that while horizontal pan navigation was fluid across Telemetry Graphs (Speed/Pace, Heart Rate, Power), swiping across the Elevation Profile failed to move the viewport window.

Forensic root cause analysis identified cross-domain coupling in `MapDetailLayout.kt` (sharing a single raw scalar `profileStartDist` across distance and time domains, causing clamping collisions in `applyPan`) and gesture lifecycle instability in `ElevationProfile.kt` (`rememberUpdatedState(::updateZoom)` reflection allocations, touch slop jump accumulation, and premature `if (pointer.isConsumed) break` loop termination).

In this sprint, we implemented:
1. **Normalized Dimensionless Viewport Coordinate Space (`MapDetailViewportMath.kt`)**:
   - `fractionToDomain` and `domainToFraction` mapping functions with zero-span protection and strict zoom boundary clamping.
2. **Decoupled Viewport Integration in `MapDetailLayout.kt`**:
   - Replaced raw scalar with `viewportStartFraction: Double` in `[0.0, 1.0 - 1.0/profileZoomScale]`.
   - Wired independent domain spans: `elevationTotalSpan` (Distance/Time) and `telemetryTotalSpan` (Time/Distance).
   - Seamlessly forwarded mapped `startDist` and bidirectional callbacks to `ElevationProfile`, `TelemetryMetricGraph` instances, and `GlobalTelemetryZoomToolbar`.
3. **Stabilized Pointer Observation in `ElevationProfile.kt`**:
   - Direct `rememberUpdatedState(onZoomChanged)` callback capture.
   - `prevCentroid` reset to `pointer.position` upon drag activation.
   - Removed premature break in `isVerticalScrolling`.
   - Suppressed single-tap inspection in Pan Mode.

All targeted unit and contract tests passed, and the full clean-room unit test suite (`./gradlew testDebugUnitTest`) executed successfully with a 100% pass rate.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-232` (Clause 1: Composable Signature) | `[TST-UI-190.1]` | Automated Contract Test (`TelemetryMetricGraphGestureTest`) | **PASSED** | `Verified` |
| `REQ-UI-232` (Clause 2: PointerInput Decoupling) | `[TST-UI-190.1]`, `[TST-UI-190.2]` | Automated Contract Test (`ElevationProfileGestureContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-232` (Clause 3: Pan Tap Isolation) | `[TST-UI-190.1]`, `[TST-UI-190.6]` | Automated Contract Test (`ElevationProfileGestureContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-232` (Clause 4: Elevation Gesture Continuity) | `[TST-UI-190.6]` | Automated Contract Test (`ElevationProfileGestureContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-232` (Clause 5: Normalized Fraction Panning) | `[TST-UI-190.3]`, `[TST-UI-190.4]`, `[TST-UI-190.5]` | Automated Unit Test (`MapDetailLayoutCrossDomainPanTest`) | **PASSED** | `Verified` |
| `REQ-UI-234` (Cross-Domain Viewport Separation) | `[TST-UI-190.3]`, `[TST-UI-190.4]` | Automated Unit Test (`MapDetailLayoutTest`, `MapDetailLayoutCrossDomainPanTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` (Clean-Room Suite Regression) | `[TST-UI-190.7]` | Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 3m 30s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Contract Tests
```text
MapDetailLayoutCrossDomainPanTest > testFractionToDomain_mapsDistanceAndClampsWithinZoomLimits PASSED
MapDetailLayoutCrossDomainPanTest > testFractionToDomain_mapsTimeAndClampsWithinZoomLimits PASSED
MapDetailLayoutCrossDomainPanTest > testDomainToFraction_mapsDistanceToNormalizedFraction PASSED
MapDetailLayoutCrossDomainPanTest > testCrossDomainSynchronizedPanning_elevationDragUpdatesTelemetryInLockstep PASSED
MapDetailLayoutCrossDomainPanTest > testCrossDomainSynchronizedPanning_telemetryDragUpdatesElevationInLockstep PASSED
MapDetailLayoutCrossDomainPanTest > testZeroAndNegativeSpanRobustness PASSED
ElevationProfileGestureContractTest > testElevationProfile_excludesMutableStateFromPointerInputKeys PASSED
ElevationProfileGestureContractTest > testElevationProfile_capturesOnZoomChangedDirectlyViaRememberUpdatedState PASSED
ElevationProfileGestureContractTest > testElevationProfile_resetsPrevCentroidOnDragEngagement PASSED
ElevationProfileGestureContractTest > testElevationProfile_removesPrematureVerticalScrollBreak PASSED
ElevationProfileGestureContractTest > testElevationProfile_suppressesSingleTapInspectionInPanMode PASSED
MapDetailLayoutTest > testMapDetailLayout_wiresGlobalZoomState PASSED
BUILD SUCCESSFUL in 8s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Physical Device Context**: Google Pixel 10 (Android 16, API 36).
* **Gesture Interaction Validation**:
  - Horizontal drag on `ElevationProfile` smoothly translates viewport start across distance domain; `TelemetryMetricGraph` (Speed/Pace, HR, Power) and map marker update in lockstep.
  - Horizontal drag on any `TelemetryMetricGraph` smoothly translates viewport start across time domain; `ElevationProfile` and sister telemetry graphs update in lockstep.
  - Zero clamping collision or sudden viewport jumps when crossing domain boundaries.
  - Vertical swipe gestures cleanly propagate to `lowerColumn` parent scroll container without stutter or pointer trapping.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite passed with 100% pass rate (3m 30s).
2. **Living Documentation Synchronized**: Status for `REQ-UI-232` and `TST-UI-190` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask (`ATT-2038`) ready for Gate 5 audit and `freigabe` transition to `Erledigt`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-1987` advanced to `Final Review (Human)` (Rule 1).
5. **Strategy A Integration**: `feature/ATT-1987` cleanly integrated into `sprint/2026-40.11` via `--no-ff`.
