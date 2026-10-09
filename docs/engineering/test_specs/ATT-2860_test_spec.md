# Stage 2: Requirement & Test Specification - ATT-2860: Visual and UX styling polish for Climb and Segment detail bottom sheets

**Ticket**: [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)  
**Sub-task**: [ATT-2891](https://atrainingtracker.atlassian.net/browse/ATT-2891) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-315` (*Harmonized Visual & UX Styling for Climb and Segment Detail Bottom Sheets*)  
**Amending**: `REQ-UI-300` (*Climb Detail Modal Bottom Sheet*) and `REQ-UI-303` (*Segment Detail Modal Bottom Sheet*)  
**Test Spec ID**: `TST-UI-275`  
**Branch**: `improvement/ATT-2860`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-315)

### 1.1 Problem Statement & Rationale
During Sprint Review 2026-41.4 on a physical Google Pixel 10 (ATT-2774 verification), athlete feedback requested follow-up visual and UX styling polish across both the `ClimbDetailSheet` and `SegmentDetailSheet` components to make them cohesive, beautiful, and consistent.

Inspection revealed:
1. `ClimbDetailMetricsCard` mislabels the Maximum Grade metric using `graph_heading_elevation` ("Höhe") instead of "Max. Steigung" and lacks iconography.
2. `SegmentDetailMapCard` renders the segment path via `MapTrack` (sport blue) instead of `MapSegment` (`TTColor.StravaOrange`).
3. `ClimbDetailMapCard` layers a redundant blue track beneath the climb category highlight.
4. `SegmentDetailElevationProfileCard` uses a flat route profile while `ClimbDetailSheet` features a vivid grade-colored slope profile.
5. Inconsistent card corner radii (12.dp) that deviate from the modern 16.dp Material 3 surface aesthetic (Rule 23).

### 1.2 Functional & Architectural Requirements

1. **REQ-UI-315.1: Mislabeled Metric Correction & Climb Metric Iconography**:
   - The Maximum Grade metric label in `ClimbDetailMetricsCard` SHALL be displayed as `climb_max_grade_label` ("Max. Steigung" / "Max Grade").
   - `ClimbDetailMetricsCard` SHALL feature icons (`ic_distance`, `ic_ascent`, `ic_grade`) for all key metrics.
2. **REQ-UI-315.2: Map Polyline Contrast & Theming Parity**:
   - `SegmentDetailMapCard` SHALL render the segment polyline via `MapSegment`, displaying in authentic `TTColor.StravaOrange`.
   - `ClimbDetailMapCard` SHALL render the climb polyline strictly in its category color without conflicting base polyline tracks.
3. **REQ-UI-315.3: Elevation Profile Visual Parity**:
   - Both `ClimbDetailSheet` and `SegmentDetailSheet` SHALL render a color-coded slope elevation profile with vertical fill reflecting grade steepness categories.
4. **REQ-UI-315.4: Material 3 Surface & Card Radius Harmony**:
   - All cards in `ClimbDetailSheet` and `SegmentDetailSheet` SHALL use `RoundedCornerShape(16.dp)` and `CardDefaults.elevatedCardColors()` for consistent depth and elevation.
5. **REQ-UI-315.5: 9-Language Localization Parity**:
   - Any new string resource (such as `climb_max_grade_label`) SHALL be translated across all 9 application locales with 100% token parity.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Climb Detail Metrics Labels & Icons)**:
  * *Given* the athlete opens `ClimbDetailSheet` for any climb
  * *When* viewing `ClimbDetailMetricsCard`
  * *Then* the 4th metric is clearly labeled "Max. Steigung" / "Max Grade" (not "Höhe"), and metrics display thematic vector icons.
* **Criterion 2 (Segment Map Polyline Theming)**:
  * *Given* the athlete opens `SegmentDetailSheet` for a matched segment
  * *When* inspecting the map viewport
  * *Then* the segment path is rendered in Strava Orange (`TTColor.StravaOrange`).
* **Criterion 3 (Climb Map Polyline Clarity)**:
  * *Given* the athlete opens `ClimbDetailSheet`
  * *When* inspecting the map viewport
  * *Then* the climb path is highlighted cleanly in its category color with start and summit markers, without conflicting blue track lines.
* **Criterion 4 (Segment Elevation Profile Slope Colors)**:
  * *Given* `SegmentDetailSheet`
  * *When* viewing the elevation profile card
  * *Then* the profile curve is color-coded by slope grade with gradient fill, matching `ClimbDetailSheet`.

---

## 2. Test Specification (TST-UI-275)

### Test Case 1: `ClimbDetailSheetContractTest` (`TST-UI-275.1`)
* **Scope**: Composable Contract & Resource Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheetContractTest.kt`
* **Assertions**:
  * Asserts `climb_max_grade_label` string resource exists and is used in `ClimbDetailMetricsCard`.
  * Asserts cards use 16.dp rounded corner shapes.

### Test Case 2: `SegmentDetailSheetContractTest` (`TST-UI-275.2`)
* **Scope**: Composable Contract & Map Model Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheetContractTest.kt`
* **Assertions**:
  * Asserts `SegmentDetailMapCard` constructs `MapSegment` (which provides `TTColor.StravaOrange`).
  * Asserts cards use 16.dp rounded corner shapes.

### Test Case 3: 9-Language Localization Audit (`TST-UI-275.3`)
* **Scope**: `TranslationParityTest`
* **Target**: Verify `climb_max_grade_label` across all 9 `strings.xml` files.

### Test Case 4: Full Suite Clean-Room Regression (`TST-UI-275.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-275.1` | Unit Contract | `ClimbDetailSheet.kt` | `REQ-UI-315.1`, `REQ-UI-315.4` | Specified |
| `TST-UI-275.2` | Unit Contract | `SegmentDetailSheet.kt` | `REQ-UI-315.2`, `REQ-UI-315.3` | Specified |
| `TST-UI-275.3` | Localization | `strings.xml` (9 locales) | `REQ-UI-315.5` | Specified |
| `TST-UI-275.4` | Full Suite | Application Regression | `REQ-UI-315` | Specified |
