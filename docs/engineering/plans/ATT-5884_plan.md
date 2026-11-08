# Stage 3: Implementation Plan - ATT-5884: Known Locations Map Orientation, FAB & Description Parity

**Ticket**: [ATT-5884](https://atrainingtracker.atlassian.net/browse/ATT-5884)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5884`  
**Requirement Mapping**: `REQ-UI-470` | **Test Mapping**: `TST-UI-470`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
`KnownLocationsScreen` thumbnail maps currently zoom in too tightly (`MAX_ZOOM = 12.5f`) and render a 36dp standing pin that covers 45% of the thumbnail height. Furthermore, the screen lacks a `+` FAB to create favorite locations, cards display stale "0 Starts" on initial load, and `EditKnownLocationDialog` pairs "Heim-Basis" with a redundant subtitle instead of explaining its utility.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-470` (*Known Locations Map Orientation, FAB & Description Parity*)
  * `REQ-UI-470.1`: Broadened thumbnail zoom math (`[9.7f, 11.7f]`, default `11.2f`).
  * `REQ-UI-470.2`: Centered heart glyph marker without pin body (`anchor = Offset(0.5f, 0.5f)`).
  * `REQ-UI-470.3`: Floating action button (`known_locations_add_fab`) and creation dialog flow (`id = 0L`).
  * `REQ-UI-470.4`: Proactive start count hydration via `reconcileHitCounts()` and `loadAllWorkouts()`.
  * `REQ-UI-470.5`: Informative `known_locations_home_base_desc` subtitle in `EditKnownLocationDialog`.
* **Test Mapping**: `TST-UI-470` (`TST-UI-470.1` through `TST-UI-470.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Harmonized Card Layout**: ATT-5887's unboxed card structure (`DirectionsRun` icon, `bodyMedium` bold starts count, unboxed routes cross-link) strictly preserved.
2. **Context Menu Purity**: Universal long-press context menu (`Home`, `Merge`, `Delete`) intact.
3. **UI Consistency**: FAB styled with M3 tokens matching `WorkoutClustersTabsScreen`; dialog retains `AppModalBottomSheet` and `AppDialogActions`.
4. **9-Language Parity**: All newly added strings in `strings.xml` maintain 100% parity across all 9 locales.

---

## 4. Proposed Architectural Changes

### Component 1: Zoom Range Expansion (`KnownLocationZoomMath.kt`)
* Shift zoom bounds by 0.8: `MIN_ZOOM = 9.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, `MAX_ZOOM = 11.7f`.

### Component 2: Centered Heart Glyph Marker (`MapUtils.kt` & `KnownLocationsScreen.kt`)
* Implement `createCenteredHeartGlyphMarker(context, heartColor, alpha)` in `MapUtils.kt`: draws a 16dp white-outlined semi-transparent heart glyph without pin body.
* In `KnownLocationThumbnailMap`, use this marker with `anchor = Offset(0.5f, 0.5f)`.

### Component 3: FAB & Creation Flow (`KnownLocationsScreen.kt` & `KnownLocationsViewModel.kt`)
* Wrap list in a `Box(Modifier.fillMaxSize())` and add `FloatingActionButton` (`known_locations_add_fab`) at `BottomEnd`.
* Add `openCreateDialog()` and `addLocation()` in `KnownLocationsViewModel` / `KnownLocationsRepository`.
* Update `EditKnownLocationDialog` to display "Neuer Lieblingsort" (`R.string.known_locations_new_location`) and `Icons.Default.Add` when `location.id == 0L`.

### Component 4: Starts Count Hydration & Subtitle (`KnownLocationsViewModel.kt` & `EditKnownLocationDialog.kt`)
* In `KnownLocationsViewModel.init`, launch `repository.reconcileHitCounts()` and `workoutRepo?.loadAllWorkouts()`.
* In `EditKnownLocationDialog.kt`, replace `known_locations_set_home` with `known_locations_home_base_desc`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Zoom Math Constants & Unit Tests
* Files: `KnownLocationZoomMath.kt`, `KnownLocationZoomMathTest.kt`.

### Step 2: Implement Centered Heart Glyph Marker & Thumbnail Anchor
* Files: `MapUtils.kt`, `KnownLocationsScreen.kt`.

### Step 3: Implement FAB, Location Creation, and Starts Count Hydration
* Files: `KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsRepository.kt`.

### Step 4: Wire Informative Home Base Subtitle in Edit Dialog
* File: `EditKnownLocationDialog.kt`.

### Step 5: Update Contract & Regression Tests
* Files: `KnownLocationsScreenTest.kt`, `EditKnownLocationDialogTest.kt`, `KnownLocationCardLayoutTest.kt`.

### Step 6: Targeted Verification & Governance Suite
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test suite and requirement governance check.
* **Rollback**: Branch isolation on `feature/ATT-5884` enables instant clean rollback.
