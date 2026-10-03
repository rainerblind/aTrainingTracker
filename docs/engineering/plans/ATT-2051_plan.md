# Stage 3: Implementation Plan - ATT-2051: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard

**Ticket**: [ATT-2051](https://rainerblind.atlassian.net/browse/ATT-2051)  
**Sub-task**: [ATT-2109](https://rainerblind.atlassian.net/browse/ATT-2109) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-244` (*Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard*)  
**Test Mapping**: `TST-UI-203` (*Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard Verification*)  
**Branch**: `feature/ATT-2051`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In aTrainingTracker, both *Lieblingsstrecken* ([WorkoutClusterComponents.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt)) and *Lieblingsorte* ([KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt)) display list cards featuring embedded Google Maps lite-mode preview thumbnails.

Previously, `KnownLocationThumbnailMap` on `KnownLocationCard` rendered an **80dp** square thumbnail (`Modifier.size(80.dp)` per `REQ-UI-217`), whereas route cluster cards in `WorkoutClusterComponents.kt` render a **100dp** square thumbnail (`Modifier.size(100.dp)` per `REQ-UI-218`).

This 20dp discrepancy between peer spatial navigation screens created an uneven visual rhythm across the application, broke aesthetic consistency, and violated the design harmony mandate of Epic `ATT-355`. Increasing the map thumbnail in `KnownLocationsScreen.kt` to **100dp** establishes 100dp as the universal standard for card-embedded map previews, enhances spatial context for geofence landmarks, and maintains clean row wrapping for title and badge metrics.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-244` (*Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard*)
* **Test Mapping**: `TST-UI-203` (*Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard Verification*)
  * `TST-UI-203.1`: Structural Contract Test (`KnownLocationCardLayoutTest.kt`)
  * `TST-UI-203.2`: Component & ViewModel Regression Tests (`KnownLocationsScreenTest.kt`, `KnownLocationZoomMathTest.kt`, `KnownLocationsViewModelTest.kt`)
  * `TST-UI-203.3`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-203.4`: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Lite Mode & Battery Efficiency**: The preview map MUST continue to use `GoogleMapOptions().liteMode(true)` without continuous tile streaming or background GPS drain.
2. **Gesture Isolation**: Map drag, pinch, and rotate gestures MUST remain disabled on the thumbnail; tapping the map container MUST invoke `onShowOnMap` to drill down to the central map view.
3. **Calibrated Zoom Bounds**: `KnownLocationZoomMath.kt` invariant bounds `[10.5f, 12.5f]` continue to ensure optimal framing for circular geofences spanning 50m to 1,000m within the 100dp square container.
4. **Layout Robustness & Left Column Flexibility**: The left information column (`Modifier.weight(1f)`) containing Title (`titleLarge`), Altitude metric, and `FlowRow` badge container (Starts and Routes) MUST adapt gracefully to the 100dp thumbnail without text clipping or layout overflow on compact screens.
5. **Universal Card Interactions**: Single-tap edit dialog, long-press delete context menu (`REQ-UI-061`), starts drill-down `onShowWorkouts`, routes drill-down `onShowRoutes`, and map thumbnail tap `onShowOnMap` MUST remain 100% operational.
6. **Programmatic Gate Check**: Stage 4 construction will not proceed until `python3 tools/jira_util.py check-gate ATT-2109` validates Gate 3 approval.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2051` remains strictly reserved for the human user in `Final Review (Human)`. AI agents must never transition parent tickets to `Erledigt`.

---

## 4. Software Construction Steps (Stage 4)

### Step 1: Update Composable Thumbnail Dimension in `KnownLocationsScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* **Target**: `KnownLocationThumbnailMap` composable.
* **Changes**:
  1. Update KDoc from `80dp` to `100dp` and reference `REQ-UI-244`.
  2. Change `modifier.size(80.dp)` to `modifier.size(100.dp)` on the root `Surface` container.
  3. Update comment at line 607 in `KnownLocationCard` to reference `REQ-UI-217 / REQ-UI-244`.

### Step 2: Update Structural Contract Test in `KnownLocationCardLayoutTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Target**: `testMapPreviewThumbnailLayoutAndLiteMode`
* **Changes**:
  1. Update KDoc to reference `REQ-UI-217 / REQ-UI-244` and 100dp map preview.
  2. Update assertion from `content.contains(".size(80.dp)")` to `content.contains(".size(100.dp)")`.
  3. Ensure failure message reads `"Map preview thumbnail must be 100.dp"`.

### Step 3: Targeted Verification & Local Regression
* Execute targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*"
  ```
* Verify:
  - `KnownLocationCardLayoutTest` passes 100%.
  - `KnownLocationsScreenTest` passes 100%.
  - `KnownLocationZoomMathTest` passes 100%.
  - `KnownLocationsViewModelTest` passes 100%.

### Step 4: Documentation & Stage 4 Sign-Off
* Author `docs/engineering/plans/ATT-2051_implementation_summary.md`.
* Update Stage 4 subtask description and move through Gate 4 audit.

---

## 5. Verification & Clean-Room Strategy (Stage 5)

1. **Clean-Room Regression Suite**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Verify 100% pass rate across the full test suite (~1,400+ tests).
2. **Requirement & Test Tracking Synchronization**:
   * Update `REQ-UI-244` status from `In Progress` to `Verified` in `docs/requirements.md`.
   * Update `TST-UI-203` status from `Specified` to `Verified` in `docs/tests.md`.
3. **Walkthrough & Visual Verification Artifact**:
   * Author `docs/engineering/walkthroughs/ATT-2051_walkthrough.md`.
4. **Gate 5 Audit & Integration**:
   * Create Stage 5 subtask, audit via `review_agent.py`, transition to `Erledigt`.
   * Transition parent `ATT-2051` to `Final Review (Human)`.
   * Checkout `sprint/2026-40.12`, merge `feature/ATT-2051` with `--no-ff`, and delete feature branch.
