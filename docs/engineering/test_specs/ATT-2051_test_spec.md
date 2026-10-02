# Stage 2: Requirement & Test Specification - ATT-2051: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard

**Ticket**: [ATT-2051](https://rainerblind.atlassian.net/browse/ATT-2051)  
**Sub-task**: [ATT-2108](https://rainerblind.atlassian.net/browse/ATT-2108) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-244` (*Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard*)  
**Test Spec ID**: `TST-UI-203`  
**Branch**: `feature/ATT-2051`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-244)

### 1.1 Problem Statement & Rationale
In aTrainingTracker, both *Lieblingsstrecken* (`WorkoutClusterComponents.kt`) and *Lieblingsorte* (`KnownLocationsScreen.kt`) display spatial list cards with embedded Google Maps lite-mode preview thumbnails.

Previously, `KnownLocationThumbnailMap` on `KnownLocationCard` rendered an **80dp** square thumbnail (`Modifier.size(80.dp)` per `REQ-UI-217`), whereas route cluster cards in `WorkoutClusterComponents.kt` render a **100dp** square thumbnail (`Modifier.size(100.dp)`).

This 20dp sizing discrepancy between peer spatial navigation screens created an uneven visual rhythm, broke aesthetic consistency, and violated the design harmony mandate of Epic `ATT-355`. Increasing the map thumbnail in `KnownLocationsScreen.kt` to **100dp** harmonizes the component hierarchy, provides richer spatial context for geofence landmarks, and maintains clean row wrapping for title and badge metrics.

### 1.2 Functional & Architectural Requirements
The system SHALL harmonize the map preview thumbnail dimensions on `KnownLocationCard` to 100dp x 100dp (`REQ-UI-244` / `ATT-2051`):

1. **Thumbnail Sizing Harmonization (`KnownLocationsScreen.kt`)**:
   - `KnownLocationThumbnailMap` SHALL render its root `Surface` container with dimensions `Modifier.size(100.dp)` (increasing from 80dp to 100dp).
   - Corner radius SHALL remain `RoundedCornerShape(12.dp)`.
   - Test tag `location_map_preview_${item.id}` SHALL be preserved.
   - KDoc SHALL be updated to document the 100dp square dimension.

2. **Visual Parity & Invariant Preservation**:
   - Google Maps lite mode (`liteMode(true)`), gesture isolation, dark/light theme styling, anti-flash overlay, heart pin marker, circular geofence boundary, and offline `LocalInspectionMode.current` fallback MUST remain intact.
   - Left-column layout (`Modifier.weight(1f)`) containing the title, altitude row, and `FlowRow` badge container (Starts and Routes) MUST adapt gracefully to the 100dp thumbnail without text clipping or layout overflow.
   - Card interactions (single-tap edit, long-press delete context menu `REQ-UI-061`, starts drill-down `onShowWorkouts`, routes drill-down `onShowRoutes`, and map thumbnail tap `onShowOnMap`) MUST remain 100% operational.

3. **Structural Contract Test Parity (`KnownLocationCardLayoutTest.kt`)**:
   - `KnownLocationCardLayoutTest.kt` SHALL be updated to assert `.size(100.dp)` instead of `.size(80.dp)`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Harmonized Dimensions)**:
  * *Given* an athlete browsing "Lieblingsorte" (`KnownLocationsScreen`),
  * *When* viewing any favorite location card,
  * *Then* the map preview thumbnail SHALL render with dimensions of exactly 100dp x 100dp with 12dp rounded corners.
* **AC-2 (Visual Parity with Lieblingsstrecken)**:
  * *Given* favorite location cards and route cluster cards,
  * *When* inspected across screens,
  * *Then* both embedded map preview thumbnails SHALL share identical 100dp square dimensions.
* **AC-3 (Test Parity)**:
  * *Given* the visual contract test suite (`KnownLocationCardLayoutTest.kt`),
  * *When* executed,
  * *Then* the test SHALL assert `.size(100.dp)` and pass 100%.

### 1.4 System Invariants
* Zero regressions across `KnownLocationsScreenTest`, `KnownLocationZoomMathTest`, and `KnownLocationsViewModelTest`.
* Single-tap edit dialog, long-press delete context menu (`REQ-UI-061`), starts drill-down, and routes drill-down MUST remain intact.
* Google Play Services lite mode and offline Compose preview fallback MUST remain intact.
* 9-language localization parity MUST NOT be broken.

---

## 2. Test Specification (TST-UI-203)

### Test Case 1: `KnownLocationCardLayoutTest` (`TST-UI-203.1`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: Checked out on `feature/ATT-2051`.
* **Action**:
  1. Inspect `KnownLocationsScreen.kt` source code to verify `KnownLocationThumbnailMap` declares `.size(100.dp)`.
  2. Verify 12dp rounded corners (`RoundedCornerShape(12.dp)`).
  3. Verify `location_map_preview_${item.id}` test tag.
  4. Verify `LocalInspectionMode.current` preview fallback.
* **Expected Result**: 100% assertions pass.

### Test Case 2: Invariant & ViewModel Regression Tests (`TST-UI-203.2`)
* **Scope**: Component & ViewModel Tests
* **Target Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationZoomMathTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelTest.kt`
* **Action**: Run targeted test suite verifying zero unintended side effects.
* **Expected Result**: 100% pass rate.

### Test Case 3: 9-Language Localization Audit (`TST-UI-203.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Run `TranslationParityTest` to confirm all localized strings remain 100% synchronized across all 9 locales.
* **Expected Result**: 100% pass rate.

### Test Case 4: Clean-Room Full Suite Regression (`TST-UI-203.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (1,400+ tests passing).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-203.1` | Contract | `KnownLocationCardLayoutTest` | `REQ-UI-244` | Specified |
| `TST-UI-203.2` | Component & ViewModel | `KnownLocationsScreenTest`, `KnownLocationZoomMathTest` | `REQ-UI-244` | Specified |
| `TST-UI-203.3` | Localization | `TranslationParityTest` | `REQ-UI-244`, `REQ-LOC-001` | Specified |
| `TST-UI-203.4` | Full Suite Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-014` | Specified |
