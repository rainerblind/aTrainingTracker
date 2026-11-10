# Stage 3: Implementation Plan - ATT-6320: Cross-Surface Centered Heart Marker Parity for Known Locations

**Ticket**: [ATT-6320](https://atrainingtracker.atlassian.net/browse/ATT-6320)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6320`  
**Requirement Mapping**: `REQ-MAP-063` (`REQ-MAP-063.1`..`REQ-MAP-063.3`) | **Test Mapping**: `TST-MAP-065` (`TST-MAP-065.1`..`TST-MAP-065.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
In [ATT-6211], `KnownLocationThumbnailMap` (`KnownLocationsScreen.kt`) was updated to render a centered Material heart glyph (`createCenteredHeartGlyphMarker`) with concentric anchor `Offset(0.5f, 0.5f)`. However, the detail/edit dialog map (`EditKnownLocationDialog.kt` — `LocationMiniMap`) and the central map (`MapScreenWithTrack.kt` / `MapContentScope.kt`) still use the legacy standing pin (`createHeartPinMarker`), violating Rule 80 cross-surface marker parity. This plan details replacing `createHeartPinMarker` with `createCenteredHeartGlyphMarker` across both map contexts with concentric anchor `Offset(0.5f, 0.5f)`.

---

## 2. Traceability & Requirements Mapping
* **REQ-MAP-063.1**: Detail/edit dialog map marker parity (`LocationMiniMap` in `EditKnownLocationDialog.kt`) -> `TST-MAP-065.1`.
* **REQ-MAP-063.2**: Global map known locations marker parity (`MapContentScope.kt`) -> `TST-MAP-065.2`.
* **REQ-MAP-063.3**: Legacy pin deprecation in `MapUtils.kt` and invariant preservation -> `TST-MAP-065.3`, `TST-MAP-065.4`.

---

## 3. System Invariants & Preserved Behavior
1. **Concentricity & Geofence Accuracy**: The marker glyph sits concentrically in the exact center of the geofence circle on all zoom levels.
2. **Interaction & Callback Invariants**: Marker touch callbacks (`data.onClick(loc.id)`), location titles, and preview drag/zoom behavior remain intact.
3. **Database & Service Safety**: Zero alterations to SQLite databases, repositories, or tracking services.
4. **Dark Mode Protection**: `DarkMapAntiFlashOverlay` and dynamic AMOLED map styles remain 100% functional.

---

## 4. Proposed Architectural Changes
* **Edit/Detail Dialog (`EditKnownLocationDialog.kt`)**:
  * In `LocationMiniMap`, instantiate `createCenteredHeartGlyphMarker(context, TTColor.GeofencePin, Color.White, 0.85f)`.
  * Pass `anchor = Offset(0.5f, 0.5f)` to GoogleMap `Marker`.
  * In `LocalInspectionMode`, replace `painterResource(R.drawable.my_locations)` with `Icon(imageVector = Icons.Default.Favorite, ...)` to match list preview.
* **Global Map (`MapContentScope.kt`)**:
  * In `locationData.forEach`, instantiate `createCenteredHeartGlyphMarker(context, primaryColor, Color.White, 0.85f)`.
  * Pass `anchor = Offset(0.5f, 0.5f)` to GoogleMap `Marker`.
* **Utility Layer (`MapUtils.kt`)**:
  * Annotate `createHeartPinMarker` with `@Deprecated("Use createCenteredHeartGlyphMarker for concentric geofence alignment (REQ-MAP-063)")`.
* **UI Consistency**: Reuses `createCenteredHeartGlyphMarker`, `Icons.Default.Favorite`, `TTColor.GeofencePin`, and `TTColor.GeofenceFill`. Zero new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Refactor `EditKnownLocationDialog.kt`**:
  * Replace `createHeartPinMarker` with `createCenteredHeartGlyphMarker`, add `anchor = Offset(0.5f, 0.5f)`, update `LocalInspectionMode` to `Icons.Default.Favorite`.
* **Step 2: Refactor `MapContentScope.kt`**:
  * Replace `createHeartPinMarker` with `createCenteredHeartGlyphMarker` and add `anchor = Offset(0.5f, 0.5f)`.
* **Step 3: Deprecate `createHeartPinMarker` in `MapUtils.kt`**:
  * Add `@Deprecated` annotation explaining concentric replacement.
* **Step 4: Update & Add Unit Tests**:
  * Add `testLocationMiniMap_usesCenteredHeartGlyphMarker` in `EditKnownLocationDialogTest.kt` (`TST-MAP-065.1`).
  * Add `testMapContentScope_usesCenteredHeartGlyphMarker` in `MapScreenWithTrackTest.kt` (`TST-MAP-065.2`).
  * Add cross-surface parity assertion in `KnownLocationsScreenTest.kt` (`TST-MAP-065.3`).
* **Step 5: Run Targeted Unit Tests**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests during construction; verify 100% clean-room test pass in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-6320` allows instant revert without affecting `sprint/2026-46.1`.
