# Stage 3: Implementation Plan - ATT-2860: Visual and UX styling polish for Climb and Segment detail bottom sheets

**Ticket**: [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)  
**Sub-task**: [ATT-2892](https://atrainingtracker.atlassian.net/browse/ATT-2892) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-315` (*Harmonized Visual & UX Styling for Climb and Segment Detail Bottom Sheets*)  
**Test Spec ID**: `TST-UI-275`  
**Branch**: `improvement/ATT-2860`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

In response to human athlete feedback during Sprint Review 2026-41.4 (*"Approved. Please create a follow-up ticket to make this, and the one for the climbs, even more nicer"*), this plan details the precise atomic changes to achieve Material 3 styling polish, iconography harmony, polyline theming contrast, and elevation profile visual parity across `ClimbDetailSheet.kt` and `SegmentDetailSheet.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-315` (Harmonized Visual & UX Styling for Climb and Segment Detail Bottom Sheets)
* **Test Mapping**: `TST-UI-275`
  * `TST-UI-275.1`: `ClimbDetailSheetContractTest` (Correct Max Grade label, icon presence, 16.dp radii)
  * `TST-UI-275.2`: `SegmentDetailSheetContractTest` (`MapSegment` Strava Orange theming, slope-gradient profile, 16.dp radii)
  * `TST-UI-275.3`: `TranslationParityTest` (100% token & string parity across 9 languages)
  * `TST-UI-275.4`: Full clean-room regression suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Regression on Dismiss & Navigation Lifecycles**:
   Tapping the close icon, dragging down, or tapping the scrim MUST cleanly dismiss the bottom sheets without leaking state.
2. **Bounds & Zoom Stability**:
   Map viewport calculations (`calculateClimbBounds`, `calculateSegmentBounds`) using `MapZoomFocus.EXPLICIT_BOUNDS` MUST remain unchanged.
3. **Modularity Ceiling (< 500 lines)**:
   Both `ClimbDetailSheet.kt` and `SegmentDetailSheet.kt` MUST remain under 500 lines of code.
4. **9-Language Parity (Rule 14)**:
   All new strings translated across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. **No Broken Contracts**:
   No signature changes to public composables `ClimbDetailSheet` and `SegmentDetailSheet`.

---

## 4. Proposed Architectural Changes

### Step 1: Localization Resource Update
* Add `climb_max_grade_label` across all 9 `strings.xml` files:
  - English: "Max Grade"
  - German: "Max. Steigung"
  - Spanish: "Pendiente máx."
  - French: "Pente max."
  - Italian: "Pendenza max."
  - Japanese: "最大勾配"
  - Dutch: "Max. stijging"
  - Polish: "Maks. nachylenie"
  - Portuguese: "Inclinação máx."

### Step 2: Elevate `ClimbDetailSheet.kt`
* In `ClimbDetailMetricsCard`:
  - Enhance `ClimbMetricItem` with a clean 16.dp vector icon (`R.drawable.ic_distance`, `R.drawable.ic_ascent`, `R.drawable.ic_grade`).
  - Correct the 4th metric label to use `R.string.climb_max_grade_label`.
  - Update card shape to `RoundedCornerShape(16.dp)` with `CardDefaults.elevatedCardColors()`.
* In `ClimbDetailMapCard`:
  - Update card shape to `RoundedCornerShape(16.dp)`.
  - Ensure the climb highlight polyline renders cleanly without redundant conflicting blue track overlay.
* In `ClimbDetailElevationProfileCard`:
  - Update card shape to `RoundedCornerShape(16.dp)`.

### Step 3: Elevate `SegmentDetailSheet.kt`
* In `SegmentDetailMetricsCard`:
  - Update card shape to `RoundedCornerShape(16.dp)` with `CardDefaults.elevatedCardColors()`.
* In `SegmentDetailMapCard`:
  - Update card shape to `RoundedCornerShape(16.dp)`.
  - Replace `MapTrack` with `MapSegment`, rendering the polyline in authentic `TTColor.StravaOrange`.
* In `SegmentDetailElevationProfileCard`:
  - Update card shape to `RoundedCornerShape(16.dp)`.
  - Render segment elevation profile with grade-colored slope profile canvas for visual parity with `ClimbDetailSheet`.

### Step 4: Synchronize Living Documentation & Contracts
* Add `REQ-UI-315` to `docs/requirements.md` and `TST-UI-275` to `docs/tests.md`.
* Author/update contract tests in `ClimbDetailSheetContractTest.kt` and `SegmentDetailSheetContractTest.kt`.
* Run targeted tests and full regression suite.

---

## 5. UI Consistency (Rule 23)
* **Design system reference**: Standard Material 3 tokens, `MaterialTheme.colorScheme.surfaceVariant`, `TTColor.StravaOrange`, `ClimbCategory` color palette.
* **Component styling**: 16.dp rounded corners, elevated card styling, unified spacing (12.dp gaps).

---

## 6. Rollback Plan
* All edits are isolated on `improvement/ATT-2860`.
* In case of any unexpected regression, branch can be cleanly reverted or discarded prior to merge into `sprint/2026-41.5`.
