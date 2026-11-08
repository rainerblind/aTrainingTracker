# Stage 2: Requirement & Test Specification - ATT-5853: Route Details Climb Badges & Polish

**Ticket**: [ATT-5853](https://atrainingtracker.atlassian.net/browse/ATT-5853)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Karten & Routen*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5853`  
**Requirement Mapping**: `REQ-UI-489` (*Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting*)  
**Test Spec ID**: `TST-UI-489` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-489)

### 1.1 Problem Statement & Rationale
During UI inspection of the Route Details screen (`RouteOnMapScreen.kt`, `detail_05_route_detail.png`, `detail_06_route_climbs.png`), four presentation defects were identified:
1. Excessive top padding (`44.dp`) in `ElevationProfile.kt` leaves an unneeded gap and causes summit climb badges to clip under the toolbar boundary.
2. Duplicate `ic_ascent` upward arrow used for both elevation gain and climb count in `RouteSummaryHeader.kt`.
3. Redundant section headers (`Anstiege (N)` / `Segmente (M)`) rendered directly beneath active tab filter chips.
4. Hardcoded `Locale.US` in `ClimbDetector.kt` generates decimal points (`0.7 km @ 3.7%`), conflicting with German comma conventions (`3,7%`).

### 1.2 Functional & Architectural Requirements
1. **Elevation Profile Top Clearance & Height Calibration (`REQ-UI-489.1`)**: In `ElevationProfile.kt`, `topPadding` SHALL be declared as `if (showZoomControls) 24.dp else 16.dp`, and `totalCanvasHeight` SHALL be declared as `fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 8.dp else cachedData.adaptiveHeight`. This eliminates the legacy 20dp white gap while preserving at least 24dp summit category badge clearance at 1.0x font scale without container clipping.
2. **Route Summary Header Iconography Disambiguation (`REQ-UI-489.2`)**: In `RouteSummaryHeader.kt`, the climb count metric item SHALL bind `iconRes = R.drawable.ic_altitude` (mountain silhouette) instead of `R.drawable.ic_ascent` (upward arrow).
3. **Route Details Section Heading Deduplication (`REQ-UI-489.3`)**: `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection` SHALL accept `showHeader: Boolean = true`, wrapping the title `Text` in `if (showHeader)`. `RouteOnMapScreen.kt` SHALL pass `showHeader = !(climbs.isNotEmpty() && matchedSegments.isNotEmpty())` to suppress the duplicate heading when filter chips are present.
4. **Locale-Aware Climb Title Grade Formatting (`REQ-UI-489.4`)**: In `ClimbDetector.kt` (`buildClimb`), the generated default climb name SHALL format average grade using `String.format(Locale.getDefault(), "%.1f%%", avgGrade)`. Default climb titles rendered in `RouteClimbsBreakdownSection` SHALL dynamically format the gradient using runtime locale.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Elevation Profile Top Clearance)**:
  * *Given* `ElevationProfile` rendered in detail view (`showZoomControls = true`),
  * *When* inspecting `topPadding` and `totalCanvasHeight`,
  * *Then* `topPadding` is `24.dp` and `totalCanvasHeight` adds `8.dp`, eliminating the 20dp gap while keeping summit climb badges completely visible without clipping.
* **Criterion 2 (Header Icon Disambiguation)**:
  * *Given* `RouteSummaryHeader` with climbs present,
  * *When* rendered on screen,
  * *Then* elevation gain displays `R.drawable.ic_ascent` and climb count displays `R.drawable.ic_altitude`.
* **Criterion 3 (Section Heading Deduplication)**:
  * *Given* a route with both climbs and matched segments,
  * *When* rendered on screen,
  * *Then* the breakdown section displays filter chips and suppresses the duplicate text heading immediately below.
* **Criterion 4 (Locale-Aware Grade Formatting)**:
  * *Given* the application running in German locale (`de`),
  * *When* an automated climb is detected or displayed,
  * *Then* its grade in the title row is formatted with a localized comma (`3,7%`) instead of an English dot (`3.7%`).

### 1.4 System Invariants & Chesterton's Fence
* Climb detection algorithms (`REQ-MAP-027`), segment matching, map polyline overlays, and scrubbing overlay synchronization remain unaltered.
* 100% 9-language translation parity maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* Contract tests `ElevationProfileLayoutTest.kt` and `ElevationProfileClimbBadgeClippingTest.kt` synchronized with amended top padding and canvas height formulas.

---

## 2. Test Specification (TST-UI-489)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-489.1]` | Contract | `ElevationProfileLayoutTest`, `ElevationProfileClimbBadgeClippingTest`: `topPadding = if (showZoomControls) 24.dp else 16.dp`, `totalCanvasHeight = cachedData.adaptiveHeight + 8.dp`, clearance at least 24dp | `REQ-UI-489.1` | Specified |
| `[TST-UI-489.2]` | Contract | `RouteSummaryHeaderContractTest` / `RouteDetailsContractTest`: `RouteSummaryHeader` binds `R.drawable.ic_altitude` for climb count | `REQ-UI-489.2` | Specified |
| `[TST-UI-489.3]` | Contract | `RouteDetailsContractTest`: `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection` accept `showHeader: Boolean = true`; heading suppressed when dual filter chips active | `REQ-UI-489.3` | Specified |
| `[TST-UI-489.4]` | Unit | `ClimbDetectorTest`: `buildClimb` uses `Locale.getDefault()` for grade percentage, rendering localized comma in DE and dot in US | `REQ-UI-489.4` | Specified |
| `[TST-UI-489.5]` | Regression | Targeted test suite (`com.atrainingtracker.trainingtracker.climbs.*`, `com.atrainingtracker.trainingtracker.ui.map.*`, `com.atrainingtracker.trainingtracker.ui.routes.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) | `REQ-UI-489.1`..`4` | Specified |
