# Stage 1 Analysis: ATT-5884 - UI Polish for Known Locations & Edit Dialog

**Ticket**: [ATT-5884](https://atrainingtracker.atlassian.net/browse/ATT-5884)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5884`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Map Thumbnail Zoom & Marker**: `KnownLocationZoomMath.calculateThumbnailZoom` clamps zoom to `[10.5f, 12.5f]` with default `12.0f`. For standard 200m radii, `12.5f` zooms in too tightly, losing regional context (neighboring towns/roads). Furthermore, the 36dp standing heart pin (`anchor = Offset(0.5f, 1.0f)`) covers ~45% of the 80dp thumbnail height.
* **Missing `+` FAB**: Unlike Routes, Favorite Tracks, and Gear, `KnownLocationsScreen` lacks a floating action button to create a new favorite location.
* **Stale '0 Starts' Count on Load**: Cards show `0 Starts` even when route clusters and historical workouts exist, because `workoutRepo.loadAllWorkouts()` and `repository.reconcileHitCounts()` are not triggered on screen load.
* **Redundant Heim-Basis Subtitle**: `EditKnownLocationDialog` pairs the title "Heim-Basis" with the tautological subtitle "Als Heim-Basis festlegen" instead of explaining its utility (automatic workout naming and barometric altitude calibration).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  1. *Zoom & Marker*: `KnownLocationZoomMath` was calibrated conservatively for tight geofence containment; standing pin graphic was shared from full-screen maps.
  2. *FAB*: `KnownLocationsScreen` historically relied on automatic GPS discovery and lacked manual addition entry points.
  3. *Starts Count*: `KnownLocationsViewModel` calculated starts in-memory from `workoutRepo.allWorkouts.value`, which defaults to `emptyList()` until `loadAllWorkouts()` is explicitly invoked, while SQLite `hitCount` remained unreconciled until background reconciliation.
  4. *Subtitle*: Reused the context menu action string `known_locations_set_home` rather than a descriptive summary.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `KnownLocationZoomMath.kt:32-34`: Reducing constants by ~0.8 levels (`MIN_ZOOM = 9.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, `MAX_ZOOM = 11.7f`) broadens geographic orientation while keeping geofences fully framed.
  * Inspected `MapUtils.kt:77-107`: Creating `createCenteredHeartGlyphMarker` (~14-16dp, `anchor = Offset(0.5f, 0.5f)`, subtle white halo) removes standing pin occlusion.
  * Inspected `KnownLocationsViewModel.kt:113` & `KnownLocationsRepository.kt:115`: Calling `repository.reconcileHitCounts()` and `workoutRepo.loadAllWorkouts()` on ViewModel initialization eliminates zero-count delays.
  * Preserved `ATT-5887`'s harmonized unboxed card layout and `DirectionsRun` starts link.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Shift `KnownLocationZoomMath` zoom clamping by ~0.8 levels (`9.7f` to `11.7f`) and update `KnownLocationZoomMathTest.kt`.
  * Implement centered heart glyph marker (`Offset(0.5f, 0.5f)`) for `KnownLocationThumbnailMap`.
  * Add `+` FAB (`known_locations_add_fab`) on `KnownLocationsScreen` to launch `EditKnownLocationDialog` for new locations (`id = 0L`).
  * Ensure `reconcileHitCounts()` and workout loading run on initialization in `KnownLocationsViewModel`.
  * Add `known_locations_home_base_desc` across all 9 locales and display it in `EditKnownLocationDialog`.
* **Out-of-Scope**: Altering merge logic (ATT-5889), card layout hierarchy (ATT-5887), or database schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new refinement `REQ-UI-470` (*Known Locations Map Orientation, FAB & Description Parity*), refining `REQ-UI-165`, `REQ-UI-224`, and `REQ-SET-095`.
* **Historical Origin & Commit Trace**: `44d51eac` (ATT-1816), `d7b00bf8` (ATT-1878), `c6f65b71` (ATT-5889).
* **Root Reason for Existing Formulation**: Standing pin and conservative zoom were carried over from initial map prototypes; "Als Heim-Basis festlegen" was reused from dropdown menu actions.
* **Preservation of Core Invariants**: `KnownLocationCard` harmonized unboxed hierarchy (ATT-5887) preserved; context menu (`Merge`, `Home`, `Delete`) intact; 100% 9-language localization parity preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Zoom & Glyph**: Update zoom math bounds; introduce centered heart marker helper in `MapUtils.kt` with `anchor = Offset(0.5f, 0.5f)`.
* **FAB & Creation**: Add `FloatingActionButton` to `KnownLocationsScreen`; wire `id = 0L` creation flow in `EditKnownLocationDialog` and `KnownLocationsRepository.addNewLocation`.
* **Starts Hydration**: Trigger `repository.reconcileHitCounts()` and `workoutRepo.loadAllWorkouts()` in `KnownLocationsViewModel.init`.
* **Localization**: Introduce `known_locations_home_base_desc` in all 9 `strings.xml` files.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% test pass rate in `KnownLocationZoomMathTest`, `KnownLocationCardLayoutTest`, and `TranslationParityTest`; zero regression in merge or edit flows.
* **Risk Rating**: **LOW** — Leaf UI enhancements, zoom constant refinement, and proactive count reconciliation with zero architectural disruption.
