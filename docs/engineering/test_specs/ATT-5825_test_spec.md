# Stage 2: Requirement & Test Specification - ATT-5825: KnownLocationCard UI Polish

**Ticket**: [ATT-5825](https://rainerblind.atlassian.net/browse/ATT-5825)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-UI-465` (*KnownLocationCard UI Polish, 48dp Gap Elimination & Compact Map Thumbnail*)  
**Test Spec ID**: `TST-UI-465`  
**Branch**: `feature/ATT-5825`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-UI-465)

### 1.1 Problem Statement & Rationale
In the "Lieblingsorte" screen, `KnownLocationCard` suffers from 48dp touch target bloat causing massive whitespace when badges wrap beside the 100x100dp map, displays a misleading ascent climbing arrow (`ic_ascent`, `↑`) in prominent primary blue, and uses a square map thumbnail where pins and watermarks cover half the preview. The system SHALL eliminate the 48dp phantom gap, replace the ascent arrow with a mountain terrain icon in secondary styling, and resize the map thumbnail to a compact 116x80dp landscape aspect ratio (refining `REQ-UI-195`, `REQ-UI-217`, `REQ-UI-244`).

### 1.2 Functional & Architectural Requirements
1. **48dp Min Touch Target Elimination (`REQ-UI-465.1`)**: In `KnownLocationsScreen.kt`, drill-down badge `Surface` components ("Starts" and "Routes") inside `FlowRow` SHALL be wrapped in `CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified)` to eliminate phantom vertical padding while preserving click actions. "Starts" badge SHALL receive subtle visual emphasis.
2. **Semantic Altitude Icon & Secondary Hierarchy (`REQ-UI-465.2`)**: The elevation display SHALL replace `ic_ascent` with `Icons.Default.Terrain` (or semantic altitude vector) and use `colorScheme.onSurfaceVariant` styling (`FontWeight.Medium` / `Normal`) rather than dominant primary blue.
3. **Compact Landscape Thumbnail Map (`REQ-UI-465.3`)**: In `KnownLocationThumbnailMap`, thumbnail size SHALL be changed from `100.dp × 100.dp` to `116.dp` width × `80.dp` height (4:3 ratio), expanding horizontal visibility and reducing card vertical footprint.
4. **Contract Assertion Parity (`REQ-UI-465.4`)**: `KnownLocationCardLayoutTest.kt` SHALL be updated to assert `width = 116.dp, height = 80.dp` and terrain icon usage.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Badge Gap)**: *Given* the athlete views `KnownLocationCard`, *When* badges wrap in `FlowRow`, *Then* they render with compact vertical spacing without the 48dp phantom gap.
* **Criterion 2 (Altitude Semantics)**: *Given* reference altitude is shown, *When* rendered, *Then* it displays `Icons.Default.Terrain` in `onSurfaceVariant` tint.
* **Criterion 3 (Thumbnail Dimensions)**: *Given* the thumbnail map, *When* rendered, *Then* its bounds measure `116.dp × 80.dp`.

### 1.4 System Invariants
Single-tap edit, long-press menu (`REQ-UI-061`), starts/routes drill-down navigation, Google Maps lite mode, test tags, and calibrated zoom math (`KnownLocationZoomMath`) MUST NOT be broken.

---

## 2. Test Specification (TST-UI-465)

### Test Case 1: 48dp Gap Elimination & Badge Styling (`TST-UI-465.1`)
* **Scope**: Contract Test | **Target File**: `app/src/test/.../ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source loaded.
* **Action**: Verify `LocalMinimumInteractiveComponentSize provides Dp.Unspecified` wraps badges, test tags are preserved, and "Starts" badge has distinct styling.
* **Expected Result**: Assertions pass; no 48dp gap bloat.

### Test Case 2: Semantic Altitude Terrain Icon (`TST-UI-465.2`)
* **Scope**: Contract Test | **Target File**: `app/src/test/.../ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source loaded.
* **Action**: Verify `Icons.Default.Terrain` replaces `R.drawable.ic_ascent` and color uses `onSurfaceVariant`.
* **Expected Result**: Assertions pass; primary blue ascent arrow eliminated.

### Test Case 3: Compact Landscape Map Thumbnail Dimensions (`TST-UI-465.3`)
* **Scope**: Contract Test | **Target File**: `app/src/test/.../ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source loaded.
* **Action**: Verify `KnownLocationThumbnailMap` asserts `.size(width = 116.dp, height = 80.dp)`.
* **Expected Result**: Assertions pass; 100dp square replaced by 116x80dp.

### Test Case 4: Targeted Regression Suite (`TST-UI-465.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass across all known locations, theme audit, and build contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-465.1` | Contract | `KnownLocationCardLayoutTest.testCompactBadgeDimensionsAndStyling` | `REQ-UI-465.1` | Specified |
| `TST-UI-465.2` | Contract | `KnownLocationCardLayoutTest.testAltitudeDecoupledFromBadgesRow` | `REQ-UI-465.2` | Specified |
| `TST-UI-465.3` | Contract | `KnownLocationCardLayoutTest.testMapPreviewThumbnailLayoutAndLiteMode` | `REQ-UI-465.3`, `REQ-UI-465.4` | Specified |
| `TST-UI-465.4` | Regression | Targeted Known Locations & Theme Audit Suite | `REQ-PRO-001` | Specified |
