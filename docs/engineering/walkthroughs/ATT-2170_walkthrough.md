# Stage 5 Walkthrough: ATT-2170 - Place Lap Splits Between Extrema and Strava/Map in Detailed Workout View to Match Summary Order

**Ticket**: [ATT-2170](https://rainerblind.atlassian.net/browse/ATT-2170)  
**Sub-task**: [ATT-2175](https://rainerblind.atlassian.net/browse/ATT-2175) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2170`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Executive Summary & Verification Goals

The goal of ATT-2170 was to eliminate layout discrepancy between the workout list summary (`WorkoutSummary.kt`) and the detailed workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) by relocating `LapSplitVisualizerCard` into the upper collapsible metadata section immediately following `WorkoutExtrema` and prior to `StravaActivitySection` and the map viewport.

### Delivered Architecture & Implementation
1. **Canonical Section Order Parity**:
   - `metadataContent` in `TrackOnMapScreen.kt` now renders:
     1. `WorkoutDescription` (if `activeDetailPrefs.showDescription`)
     2. `WorkoutExtrema` (if `activeDetailPrefs.showExtrema && extremaData.dataRows.isNotEmpty()`)
     3. **`LapSplitVisualizerCard`** (if `activeDetailPrefs.showLaps && splitChartData != null`)
     4. `StravaActivitySection` (if `activeDetailPrefs.showStrava && !stravaActivityData.isNullOrBlank()`)
   - `analyticsContent` in `TrackOnMapScreen.kt` now renders only:
     - `HeartRateZoneDistributionCard` / `PowerZoneDistributionCard` (if `activeDetailPrefs.showZoneAnalysis`)
2. **Interactive Map Highlight Preservation**:
   - Tapping a lap in `LapSplitVisualizerCard` continues to update `selectedLapNr` and slice the corresponding GPS track segment via `LapSegmentUtils.sliceLapSegment`.
3. **Collapsible Header Integration**:
   - As part of `metadataContent`, `LapSplitVisualizerCard` smoothly slides off-screen on upward scroll gestures via `CollapsingAppBarNestedScrollConnection` (`REQ-UI-250`), avoiding any map squashing.

---

## 2. Test Execution & Verification Matrix

| Test Suite / Target | Description | Result |
| :--- | :--- | :--- |
| **`TrackOnMapScreenLapRoutingContractTest`** | Verified canonical slotting of `LapSplitVisualizerCard` in `metadataContent` between `WorkoutExtrema` and `StravaActivitySection`, preference gating, and absence in `analyticsContent`. | **PASS (100%)** |
| **`TrackOnMapScreenMetadataRoutingContractTest`** | Verified description and extrema upper slot routing. | **PASS (100%)** |
| **`MapDetailLayoutCollapsingHeaderContractTest`** | Verified collapsing nested-scroll behavior for all upper metadata. | **PASS (100%)** |
| **`MapDetailLayoutMetadataSlotContractTest`** | Verified upper metadata slot placement above viewport. | **PASS (100%)** |
| **Full Clean-Room Regression Suite** | `./gradlew testDebugUnitTest` across all unit tests | **PASS (100%)** |

---

## 3. Invariants & Backward Compatibility Verification

- **Preference Gating**: When `showLaps` is disabled in detail preferences, `LapSplitVisualizerCard` is completely omitted.
- **Custom Caller Invariant**: When `metadataContent != null`, custom callers override the default metadata slot without side effects.
- **Clean Layout**: Zero duplicate rendering; laps are rendered exactly once in the canonical position.
