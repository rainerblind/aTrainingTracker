# Stage 2: Requirement & Test Specification - ATT-5884: Known Locations Map Orientation, FAB & Description Parity

**Ticket**: [ATT-5884](https://atrainingtracker.atlassian.net/browse/ATT-5884)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5884`  
**Requirement Mapping**: `REQ-UI-470` (*Known Locations Map Orientation, FAB & Description Parity*)  
**Test Spec ID**: `TST-UI-470` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-470)

### 1.1 Problem Statement & Rationale
`KnownLocationsScreen` thumbnail maps zoom in too tightly (`MAX_ZOOM = 12.5f`) and render a 36dp standing pin that obscures 45% of the thumbnail height. Furthermore, the screen lacks a `+` FAB for manual creation, starts count shows "0 Starts" until workouts are loaded, and `EditKnownLocationDialog` pairs "Heim-Basis" with a tautological subtitle instead of explaining its utility.

### 1.2 Functional & Architectural Requirements
1. **Broadened Map Thumbnail Zoom Range (`REQ-UI-470.1`)**: In `KnownLocationZoomMath.kt`, constants SHALL be adjusted to `MIN_ZOOM = 9.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, and `MAX_ZOOM = 11.7f` (~0.8 levels wider) to preserve regional context while keeping geofences fully framed.
2. **Centered Heart Glyph Marker Without Pin Body (`REQ-UI-470.2`)**: In `KnownLocationThumbnailMap`, the 36dp standing pin SHALL be replaced by a compact, semi-transparent centered heart glyph marker (`anchor = Offset(0.5f, 0.5f)`, `createCenteredHeartGlyphMarker` with ~14-16dp glyph, subtle white outline, and `alpha ≈ 0.8f`) sitting concentrically inside the geofence circle.
3. **Floating Action Button for Manual Creation (`REQ-UI-470.3`)**: `KnownLocationsScreen` SHALL provide a `FloatingActionButton` (`Icons.Default.Add`, test tag `known_locations_add_fab`) anchored at bottom-end. Tapping it opens `EditKnownLocationDialog` with a template (`id = 0L`) to create a favorite location.
4. **Proactive Starts Count Hydration (`REQ-UI-470.4`)**: `KnownLocationsViewModel.init` SHALL proactively invoke `repository.reconcileHitCounts()` and `workoutRepo?.loadAllWorkouts()`, populating starts counts immediately.
5. **Informative Home Base Subtitle (`REQ-UI-470.5`)**: `EditKnownLocationDialog` SHALL replace `known_locations_set_home` with `known_locations_home_base_desc` explaining automatic naming and altitude calibration, with 100% 9-language translation parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zoom & Glyph)**:
  * *Given* `KnownLocationThumbnailMap` on `KnownLocationsScreen`,
  * *When* rendered for a location with 200m radius,
  * *Then* zoom is clamped to `11.7f` (showing surrounding road/regional context), and the marker is a centered heart glyph (`anchor = Offset(0.5f, 0.5f)`) without pin body.
* **Criterion 2 (FAB & Creation)**:
  * *Given* `KnownLocationsScreen`,
  * *When* displayed,
  * *Then* `+` FAB (`known_locations_add_fab`) is present at bottom-end, and tapping it opens `EditKnownLocationDialog` with `id = 0L`.
* **Criterion 3 (Starts Hydration)**:
  * *Given* historical workouts exist at a location,
  * *When* `KnownLocationsScreen` is opened,
  * *Then* `reconcileHitCounts()` and `loadAllWorkouts()` run proactively, displaying accurate start counts instead of stale "0 Starts".
* **Criterion 4 (Home Base Subtitle)**:
  * *Given* `EditKnownLocationDialog`,
  * *When* viewing the "Heim-Basis" toggle row,
  * *Then* subtitle displays `known_locations_home_base_desc` explaining automatic naming and calibration.

### 1.4 System Invariants
* Unboxed card layout from ATT-5887 (`REQ-UI-469`), context menu (Merge, Home, Delete), and radius adjustment remain intact.
* 100% 9-language localization parity with zero unescaped strings or format mismatches.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-470` refining `REQ-UI-165`, `REQ-UI-224`, and `REQ-SET-095`.
* **Historical Origin & Commit Trace**: `44d51eac` (ATT-1816), `d7b00bf8` (ATT-1878), `c6f65b71` (ATT-5889).
* **Root Reason for Existing Formulation**: Conservative zoom (`MAX_ZOOM = 12.5f`) and standing pin (`36dp`) were carried over from full-screen maps; FAB was omitted when locations relied only on GPS discovery; subtitle reused dropdown menu text.
* **Preservation of Core Invariants**: ATT-5887 unboxed layout, context menu (`Merge`, `Home`, `Delete`), geofence radius adjustment, and 9-language localization parity are 100% strictly preserved.

---

## 2. Test Specification (TST-UI-470)

### Test Case 1: Zoom Math Clamping & Shift Unit Tests (`TST-UI-470.1`)
* **Scope**: Unit Test (`KnownLocationZoomMathTest.kt`).
* **Assertions**: `MIN_ZOOM = 9.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, `MAX_ZOOM = 11.7f`; small radii clamp to `11.7f`; large radii clamp to `9.7f`.

### Test Case 2: FAB & Creation Flow Contract Tests (`TST-UI-470.2`)
* **Scope**: Contract Test (`KnownLocationsScreenTest.kt`).
* **Assertions**: `FloatingActionButton` exists with test tag `known_locations_add_fab`; tapping FAB triggers creation dialog flow.

### Test Case 3: Centered Heart Glyph Marker Tests (`TST-UI-470.3`)
* **Scope**: Unit / Contract Test (`KnownLocationsScreenTest.kt`).
* **Assertions**: `KnownLocationThumbnailMap` uses `createCenteredHeartGlyphMarker` with `anchor = Offset(0.5f, 0.5f)` and eliminates `R.drawable.my_locations`.

### Test Case 4: Home Base Subtitle Contract Tests (`TST-UI-470.4`)
* **Scope**: Contract Test (`EditKnownLocationDialogTest.kt`).
* **Assertions**: `EditKnownLocationDialog` contains `R.string.known_locations_home_base_desc` and eliminates `known_locations_set_home` in switch row.

### Test Case 5: 9-Language Localization Audit (`TST-UI-470.5`)
* **Scope**: `TranslationParityTest.kt`.
* **Assertions**: `known_locations_home_base_desc`, `known_locations_add_location`, and `known_locations_new_location` match across all 9 locales.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-UI-470.6`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass rate across touched package, localization, theme audit, and build rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-470.1]` | Unit | `KnownLocationZoomMathTest.kt`: Clamping bounds `[9.7f, 11.7f]`, default `11.2f` | `REQ-UI-470.1` | Specified |
| `[TST-UI-470.2]` | Contract | `KnownLocationsScreenTest.kt`: FAB `known_locations_add_fab` and creation flow | `REQ-UI-470.3` | Specified |
| `[TST-UI-470.3]` | Contract | `KnownLocationsScreenTest.kt`: `createCenteredHeartGlyphMarker`, `Offset(0.5f, 0.5f)` | `REQ-UI-470.2` | Specified |
| `[TST-UI-470.4]` | Contract | `EditKnownLocationDialogTest.kt`: `known_locations_home_base_desc` in toggle row | `REQ-UI-470.5` | Specified |
| `[TST-UI-470.5]` | Localization | `TranslationParityTest.kt`: Parity for new strings across 9 locales | `REQ-LOC-001`, `REQ-UI-470.5` | Specified |
| `[TST-UI-470.6]` | Regression | Tier 1 targeted suite (`ui.knownlocations.*`, `localization.*`, `ui.theme.*`, `build.*`) | `REQ-PRO-001` | Specified |
