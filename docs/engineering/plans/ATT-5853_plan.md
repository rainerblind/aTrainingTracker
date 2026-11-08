# Stage 3: Implementation Plan - ATT-5853: Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting

**Ticket**: [ATT-5853](https://atrainingtracker.atlassian.net/browse/ATT-5853) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Karten & Routen*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5853`  
**Requirement Mapping**: `REQ-UI-489` (`REQ-UI-489.1`..`4`) | **Test Mapping**: `TST-UI-489` (`TST-UI-489.1`..`5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
During Route Details UI inspection (`RouteOnMapScreen.kt`, `detail_05_route_detail.png`, `detail_06_route_climbs.png`), four presentation defects were identified:
1. Legacy `44.dp` top padding in `ElevationProfile.kt` wastes vertical space and causes summit category badges (`UC`, `Cat 4`) to clip under the toolbar boundary during lower-section scrolling.
2. Identical upward arrow `R.drawable.ic_ascent` is duplicated for both elevation gain and climb count in `RouteSummaryHeader.kt`.
3. Redundant section headers (`Anstiege (N)` / `Segmente (M)`) are rendered directly beneath active tab filter chips.
4. Hardcoded `Locale.US` in `ClimbDetector.kt` formats grade with decimal points (`3.7%`), conflicting with German comma conventions (`3,7%`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-489` (*Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting*)
  - `REQ-UI-489.1`: Calibrate `topPadding = if (showZoomControls) 24.dp else 16.dp` and `totalCanvasHeight = cachedData.adaptiveHeight + 8.dp`.
  - `REQ-UI-489.2`: Bind `iconRes = R.drawable.ic_altitude` for climb count in `RouteSummaryHeader.kt`.
  - `REQ-UI-489.3`: Introduce `showHeader: Boolean = true` in `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection` and wire in `RouteOnMapScreen.kt`.
  - `REQ-UI-489.4`: Use `Locale.getDefault()` in `ClimbDetector.kt` and format default climb titles dynamically via active runtime locale.
* **Test Mapping**: `TST-UI-489` (`TST-UI-489.1`..`5` in `docs/tests/tst_ui_design_system.md`).

---

## 3. System Invariants & Preserved Behavior
1. **Climb Detection Invariant**: Pure mathematical climb detection algorithms, category score thresholds (`CAT_4`..`HC`), and point indices remain completely intact (`REQ-MAP-027`).
2. **Scrubbing & Viewport Invariants**: Decoupled `GlobalTelemetryZoomToolbar`, scrubbing overlay, and synchronized split-pane dragging are 100% preserved (`REQ-UI-201`, `REQ-UI-246`).
3. **Localization Invariant**: 100% parity across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero hardcoded UI strings.

---

## 4. Proposed Architectural Changes
* `ElevationProfile.kt`: Set `topPadding = if (showZoomControls) 24.dp else 16.dp` and `totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 8.dp else cachedData.adaptiveHeight`.
* `RouteSummaryHeader.kt`: Set `iconRes = R.drawable.ic_altitude` for climb count `MetricItem`.
* `RouteOnMapScreen.kt` & `RouteSegmentsBreakdownSection.kt`: Parameterize `showHeader: Boolean = true` on breakdown composables, wrapping title `Text` in `if (showHeader)`. In `RouteOnMapScreen`, pass `showHeader = !(climbs.isNotEmpty() && matchedSegments.isNotEmpty())`.
* `ClimbDetector.kt`: Replace `Locale.US` with `Locale.getDefault()`. In `RouteClimbsBreakdownSection`, dynamically format default climb titles in the active locale.
* **UI Consistency**: Reuses existing `R.drawable.ic_altitude` mountain terrain icon and standardizes spacing across route inspection cards per `docs/design_guidelines.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Calibrate Top Padding & Total Canvas Height in `ElevationProfile.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* Action: Update `topPadding` to `if (showZoomControls) 24.dp else 16.dp` and `totalCanvasHeight` to `+ 8.dp` (`REQ-UI-489.1`).

### Step 2: Disambiguate Climb Count Icon in `RouteSummaryHeader.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSummaryHeader.kt`
* Action: Replace `R.drawable.ic_ascent` with `R.drawable.ic_altitude` for climb count (`REQ-UI-489.2`).

### Step 3: Deduplicate Section Headers in `RouteOnMapScreen.kt` & `RouteSegmentsBreakdownSection.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`, `RouteSegmentsBreakdownSection.kt`
* Action: Add `showHeader: Boolean = true` parameter; suppress header when filter chip row is present (`REQ-UI-489.3`).

### Step 4: Localize Climb Title Grade Formatting in `ClimbDetector.kt` & `RouteClimbsBreakdownSection`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt`, `RouteOnMapScreen.kt`
* Action: Use `Locale.getDefault()` in `ClimbDetector.kt` and format default climb titles on-the-fly (`REQ-UI-489.4`).

### Step 5: Synchronize Unit & Contract Tests
* Files: `ElevationProfileLayoutTest.kt`, `ElevationProfileClimbBadgeClippingTest.kt`, `ClimbDetectorTest.kt`, `RouteOnMapScreenClimbContractTest.kt`
* Action: Update assertions to match amended top padding, height formulas, and localized formatting (`TST-UI-489.1`..`4`).

### Step 6: Test Suite Execution & Governance Verification
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  - `python3 tools/verify_requirement_governance.py`
