# Stage 5 Verification & Walkthrough: ATT-2941 - Align turn-by-turn navigation hints UI with updated design guidelines

**Ticket**: [ATT-2941](https://atrainingtracker.atlassian.net/browse/ATT-2941)  
**Sub-task**: [ATT-2988](https://atrainingtracker.atlassian.net/browse/ATT-2988) (`[Verification]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2941`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

Ticket `ATT-2941` eliminates cockpit telemetry tile displacement during turn-by-turn route navigation prompts and harmonizes `TurnPromptBanner.kt` card presentation with Design Guidelines §§ 5.2, 5.3, 5.4, and 5.7:
1. **Top-Level Non-Displacing Spatial Overlay Architecture**:
   - `TurnPromptBanner` is relocated from the in-flow `Column` of `SensorGridScreen.kt` to the top-level spatial overlay stack anchored at `Alignment.TopCenter`.
   - The appearance, animation, or dismissal of turn cues and off-route warnings no longer alters the vertical position or scroll offset of the underlying sensor grid tiles or live map.
2. **Strict Tab Navigation Hints Gating**:
   - `TurnPromptBanner` is conditionally gated behind `if (state.showNavigationHints)`, completely suppressing turn cues on tabs where navigation hints are disabled.
3. **Design Guidelines & Material 3 Harmonization**:
   - Card shape upgraded to `RoundedCornerShape(16.dp)`, matching `ForkDecisionCard` and Section 5.3.
   - Container background updated from `surfaceVariant` to `MaterialTheme.colorScheme.surfaceContainer` (normal approach) and `primaryContainer` (immediate turn execution `isTurnNow`).
   - Subtle Royal Blue border accent (`BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))`) applied to `TurnCueCard` per Design Guidelines Section 5.4 / 5.7.
   - Inner content padding conforms strictly to the standard spacing scale (`padding(horizontal = 16.dp, vertical = 12.dp)`).
   - Typography and icon tints updated to use `onPrimaryContainer` during active turn maneuvers.

---

## 2. Changes Implemented

### 2.1 SensorGridScreen Layout Integration
* [SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt):
  - Removed in-flow `TurnPromptBanner` from lines 467-472.
  - Inserted `TurnPromptBanner` into the top-level overlay `Column` inside `if (state.showNavigationHints)` at `Alignment.TopCenter`, positioned above `ReturnNavigationHud` and `ForkDecisionCard`.

### 2.2 TurnPromptBanner Styling Refinement
* [TurnPromptBanner.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt):
  - Updated `TurnCueCard` and `OffRouteCard` shapes to `RoundedCornerShape(16.dp)`.
  - Updated container colors to `surfaceContainer` (approaching) and `primaryContainer` (`isTurnNow`).
  - Added Royal Blue navigation border stroke `TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha.coerceAtLeast(0.4f))`.
  - Conformed inner padding to `16.dp` horizontal and `12.dp` vertical.
  - Aligned active turn text and icon tint to `onPrimaryContainer`.

### 2.3 Contract & Integration Tests
* [SensorGridScreenRouteIntegrationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt):
  - Added `testSensorGridScreen_turnPromptBanner_floatsAsTopCenterOverlayAndGatedByNavigationHints`: Verifies `TurnPromptBanner` is absent from the in-flow sensor grid column, hosted in the top-center overlay, gated by `showNavigationHints`, and bound to `navigationCueTransparency` and `navigationCueDismissDurationSec`.
  - Added `testTurnPromptBanner_stylingTokens_conformsToDesignGuidelines`: Verifies `RoundedCornerShape(16.dp)`, `surfaceContainer`/`primaryContainer`, Royal Blue navigation border, and `16.dp`/`12.dp` padding.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit Tests
* `SensorGridScreenRouteIntegrationTest`: **100% PASS** (all contract tests passing).
* `TrackingTabWysiwygContractTest`: **100% PASS** (runtime gating preserved).

### 3.2 Full Regression Suite
* Executed `./gradlew testDebugUnitTest` across the entire application codebase: **100% PASS RATE** (0 regressions).

---

## 4. Requirement & Test Specification Traceability

| Requirement | Test Specification | Target File | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-324` | `TST-UI-284` | `SensorGridScreen.kt`, `TurnPromptBanner.kt` | **Verified** |

---

## 5. Invariants Maintained

* **Telemetry Stationarity**: Underlying sensor tiles remain perfectly stationary with zero layout shifts.
* **Turn Detection Accuracy**: Geodesic countdown tracking, sharp/slight turn detection, and off-route detection remain 100% preserved.
* **Transient Auto-Dismiss**: Cues dismiss cleanly after `dismissDurationSec` (3–5 seconds) without requiring rider intervention.
* **Battery Saver & Audio Alerts**: AMOLED wake-up and chime triggers remain fully operational.
