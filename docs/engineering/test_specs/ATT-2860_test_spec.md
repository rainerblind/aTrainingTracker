# Stage 2: Requirement & Test Specification - ATT-2860: Visual and UX styling polish for Climb and Segment detail bottom sheets

**Ticket**: [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)  
**Sub-task**: [ATT-2950](https://atrainingtracker.atlassian.net/browse/ATT-2950) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.6`  
**Requirement Mapping**: `REQ-UI-315` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout*)  
**Test Spec ID**: `TST-UI-275`  
**Branch**: `feature/ATT-2860`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-315)

### 1.1 Problem Statement & Rationale
During Sprint 2026-41.5 review, the previous attempt to polish `SegmentDetailSheet` and `ClimbDetailSheet` within an ad-hoc 3-card stack was rejected by the human user:
> *"The segment popup must look identical to the one that pops up in the map. We should use the same code here. The route popup should follow this layout."*

Tapping an entity on the general map opens a rich, interactive `MapDetailLayout` surface (`SegmentOnMapScreen` / `RouteOnMapScreen`) featuring standardized header rows, draggable split-pane geometry, interactive map with zoom focus, and elevation profile/analytics. In contrast, modal sheets invoked from lists or route breakdowns rendered a disparate 3-card column layout that lacked gesture harmony, duplicated logic, and created visual inconsistency.

`REQ-UI-315` is amended to unify modal entity inspection across the entire application by reusing `SegmentOnMapScreen`, `RouteOnMapScreen`, and `MapDetailLayout` directly within modal bottom sheet containers.

### 1.2 Functional & Architectural Requirements
The system SHALL eliminate divergent card-stack implementations in modal detail sheets and enforce unified presentation and code reuse across segments, routes, and climbs:

1. **Segment Detail Sheet Unification (`SegmentDetailSheet.kt`)**:
   * `SegmentDetailSheet` SHALL directly host `SegmentOnMapScreen` within a non-scrolling modal bottom sheet container (`AppModalBottomSheet(scrollable = false, dragHandle = null, ...)` or equivalent modal container).
   * It SHALL render the canonical `SegmentHeader` (sport icon, title, category chip, PR badge, dismiss close action), `HorizontalDivider`, and `SegmentDetails` (distance, Strava branding, grade, elevation gain, min/max altitude).
   * It SHALL render `ATrainingTrackerMap` with bounding box focus (`MapZoomFocus.EXPLICIT_BOUNDS`) and interactive elevation profile via `MapDetailLayout`.
   * It SHALL present `SegmentRoutesSection` in `analyticsContent` when containing routes exist.
   * Obsolete standalone card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`) SHALL be completely removed.

2. **Route Detail Sheet Unification (`RouteDetailSheet.kt`)**:
   * `RouteDetailSheet` SHALL follow this unified architecture by directly hosting `RouteOnMapScreen` within a non-scrolling modal bottom sheet container.
   * It SHALL present `RouteHeader`, `RouteDetails`, and `MapDetailLayout` with route polyline, start/stop pins, and elevation profile.
   * Obsolete standalone card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`) SHALL be completely removed.

3. **Climb Detail Sheet Alignment (`ClimbDetailSheet.kt`)**:
   * `ClimbDetailSheet` SHALL be refactored to align with this unified layout pattern, presenting a structured `ClimbHeader` and `ClimbDetails` row over `MapDetailLayout` with category-colored climb polyline and gradient elevation profile.

4. **Dismissal & State Isolation**:
   * Dismissing a modal detail sheet via close button, drag handle gesture, scrim tap, or system Back press SHALL cleanly dismiss the modal overlay and return to the parent screen with scroll position, route selection, and map zoom state 100% intact.

5. **100% 9-Language Localization Parity**:
   * All string resources utilized by detail sheets and headers SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Segment Detail Sheet Reuses Map Popup)**:
  * *Given* an athlete viewing route details in `RouteOnMapScreen`,
  * *When* tapping any matched segment item in `RouteSegmentsBreakdownSection`,
  * *Then* `SegmentDetailSheet` SHALL open displaying the exact same layout, header (`SegmentHeader` + `SegmentDetails`), and `MapDetailLayout` as `SegmentOnMapScreen`.

* **Criterion 2 (Route Detail Sheet Reuses Route Map Screen)**:
  * *Given* an athlete viewing a segment in `SegmentOnMapScreen`,
  * *When* tapping a containing route in `SegmentRoutesSection`,
  * *Then* `RouteDetailSheet` SHALL open displaying the exact same layout and `MapDetailLayout` as `RouteOnMapScreen`.

* **Criterion 3 (Interactive Map & Elevation Profile Harmony)**:
  * *Given* `SegmentDetailSheet` or `RouteDetailSheet` open,
  * *When* interacting with the embedded map or scrubbing the elevation profile,
  * *Then* gestures SHALL operate smoothly via `MapDetailLayout` without vertical scroll competition or touch collisions.

* **Criterion 4 (Clean Non-Destructive Dismissal)**:
  * *Given* any modal detail sheet open,
  * *When* the athlete taps the close button, drags down, or taps the scrim,
  * *Then* the sheet SHALL dismiss immediately, returning to the caller screen with 100% preserved state.

* **Criterion 5 (9-Language Parity)**:
  * *Given* all 9 supported application locales,
  * *When* auditing string resources across all detail sheets,
  * *Then* zero missing translations and zero format specifier mismatches exist.

### 1.4 System Invariants
1. `MapDetailLayout` split-pane dragging, dynamic peek self-measurement, and zoom focus contracts MUST remain 100% preserved.
2. Route selection toggle and GPX export lifecycles MUST NOT be affected.
3. Clean-room unit regression test suite (`./gradlew testDebugUnitTest`) MUST pass with 100% success rate (0 failures).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-300` (*Climb Detail Sheet*), `REQ-UI-303` (*Segment Detail Sheet*), and `REQ-UI-316` (*Route Detail Sheet*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2860`, Sprint `2026-41.5` -> `2026-41.6`, target release `V4.9.40`, Epic `ATT-355` (*Good and consistent UI*).
* **Root Reason for Existing Formulation**: In sprint 2026-41.5, the initial implementation attempted to polish the 3-card stack within `AppModalBottomSheet`. The user rejected this during Sprint Review because having a separate 3-card column layout created a jarring visual and functional disconnect from what athletes see when tapping entities on the map (`SegmentOnMapScreen` / `RouteOnMapScreen` with `MapDetailLayout`). The user explicitly demanded: "The segment popup must look identical to the one that pops up in the map. We should use the same code here. The route popup should follow this layout."
* **Preservation of Core Invariants**: Bounding box calculation, entity dismiss gestures, sport type filtering, route index counters, and 100% test pass rate are strictly preserved.

---

## 2. Test Specification (TST-UI-275)

### Test Case 1: `SegmentDetailSheetContractTest` (`TST-UI-275.1`)
* **Scope**: Compose Contract & Structural Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheetContractTest.kt`
* **Preconditions**: Instantiated `MatchedRouteSegment` and mock dependencies.
* **Action**:
  1. Verify `SegmentDetailSheet` hosts `SegmentOnMapScreen` (or identical `MapDetailLayout` composite) rather than the obsolete 3-card stack.
  2. Verify dismissal callback resets selection state cleanly.
  3. Verify obsolete card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`) are deleted.
* **Expected Result**: Contract test asserts structural parity with map popup and compilation passes.

### Test Case 2: `RouteDetailSheetContractTest` (`TST-UI-275.2`)
* **Scope**: Compose Contract & Structural Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheetContractTest.kt`
* **Preconditions**: Instantiated `RouteWithPath`.
* **Action**:
  1. Verify `RouteDetailSheet` hosts `RouteOnMapScreen` (or identical `MapDetailLayout` composite) rather than the obsolete 3-card stack.
  2. Verify obsolete card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`) are deleted.
* **Expected Result**: Contract test asserts structural parity with route map screen.

### Test Case 3: `ClimbDetailSheetContractTest` (`TST-UI-275.3`)
* **Scope**: Compose Contract & Structural Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheetContractTest.kt`
* **Preconditions**: Instantiated `Climb` entity.
* **Action**:
  1. Verify `ClimbDetailSheet` structure conforms to the unified header + `MapDetailLayout` architecture.
* **Expected Result**: Contract test verifies visual and architectural alignment.

### Test Case 4: 9-Language Localization Audit (`TST-UI-275.4`)
* **Scope**: Localization Parity Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/TranslationParityTest.kt`
* **Goal**: Verify all string resources in detail sheets exist across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity, 0 missing strings.

### Test Case 5: Full Clean-Room Regression Suite (`TST-UI-275.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Execute all ~2,170 tests across the project.
* **Expected Result**: 100% pass rate, 0 failures, 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-275.1` | Contract | `SegmentDetailSheet.kt`, `SegmentOnMapScreen.kt` | `REQ-UI-315.1` | Specified |
| `TST-UI-275.2` | Contract | `RouteDetailSheet.kt`, `RouteOnMapScreen.kt` | `REQ-UI-315.2` | Specified |
| `TST-UI-275.3` | Contract | `ClimbDetailSheet.kt` | `REQ-UI-315.3` | Specified |
| `TST-UI-275.4` | Localization | `res/values-*/strings.xml` | `REQ-UI-315.5`, `REQ-UI-106` | Specified |
| `TST-UI-275.5` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
