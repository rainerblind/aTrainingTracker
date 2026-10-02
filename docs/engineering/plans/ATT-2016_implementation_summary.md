# Stage 4: Implementation Summary - ATT-2016: Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout

**Ticket**: [ATT-2016](https://rainerblind.atlassian.net/browse/ATT-2016)  
**Sub-task**: [ATT-2095](https://rainerblind.atlassian.net/browse/ATT-2095) (`[Implementation]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-241`  
**Test Mapping**: `TST-UI-200`  
**Branch**: `feature/ATT-2016`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Summary of Changes

In accordance with the Stage 3 Implementation Plan and ASPICE Stage 4 guidelines:

1. **`ElevationProfile.kt` (Decoupled Badge Gating)**:
   - Added parameter `showScrubbingBadge: Boolean = true` to both `ElevationProfile` composable overloads (`encodedAltitudes`/`encodedDistances` and `pathPoints`).
   - Gated internal invocation of `ScrubbingTelemetryBadge` with `if (showZoomControls && showScrubbingBadge && currentDistance != null)`.
   - Preserved all layout dimensions, top padding (`44.dp` / `16.dp`), canvas adaptive height, and default argument invariants.

2. **`MapDetailLayout.kt` (Persistent Viewport Badge Overlay & State Hoisting)**:
   - Memoized `hrThresholds` and `powerThresholds` at the top level of `MapDetailLayout` with `remember(bSportType, context)` and `remember(context)`.
   - Reused memoized thresholds across graph headers (`hrHeaderText`, `powerHeaderText`) and the floating badge.
   - Hoisted `activeScrubPoint` and interpolated `activeScrubAltitude` with `remember(selectedDistance, activeScrubPath, isTrackless)`:
     - For trackless sessions: matches closest point by `timeSec`.
     - For GPS sessions: matches closest point by `distance <= selectedDistance` and interpolates altitude between adjacent samples without overshoot.
   - Passed `showScrubbingBadge = false` to `ElevationProfile` inside `lowerColumn` to prevent duplicate overlapping badges.
   - Wrapped `lowerColumn` in container `Box`es (both in resizable split pane `showMap && hasScrollableContent` and full column `!showMap && hasScrollableContent` / `wrapContentHeight`).
   - Overlaid `ScrubbingTelemetryBadge` anchored at `Alignment.TopCenter` with `padding(top = 4.dp)` within the viewport container `Box`.

3. **Targeted Unit & Contract Tests**:
   - Authored `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubbingBadgeContractTest.kt` verifying:
     - Parameter declarations and default values (`showScrubbingBadge = true`).
     - Internal gating in `ElevationProfile`.
     - `showScrubbingBadge = false` pass-through from `MapDetailLayout`.
     - Viewport container `Box` and `Alignment.TopCenter` positioning of `ScrubbingTelemetryBadge`.
     - Hoisting of `activeScrubPoint`, `activeScrubAltitude`, and threshold memoization.

---

## 2. Verification & Test Execution Results

- `ElevationProfileLayoutTest`: **PASSED** (100% green, 0 regressions).
- `MapDetailLayoutTest`: **PASSED** (100% green, 0 regressions).
- `MapDetailLayoutScrubbingBadgeContractTest`: **PASSED** (100% green, all 6 assertions passed).
- `TelemetryMetricGraphZoneTest`: **PASSED** (100% green).
- `TranslationParityTest`: **PASSED** (100% parity across all 9 locales).

---

## 3. Deliverables

- `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
- `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubbingBadgeContractTest.kt`
- `docs/engineering/plans/ATT-2016_implementation_summary.md`
