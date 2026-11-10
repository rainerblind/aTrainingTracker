# Stage 2: Requirement & Test Specification - ATT-6320: Cross-Surface Centered Heart Marker Parity for Known Locations

**Ticket**: [ATT-6320](https://atrainingtracker.atlassian.net/browse/ATT-6320) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6320`  
**Requirement Mapping**: `REQ-MAP-063` (`REQ-MAP-063.1`..`REQ-MAP-063.3`) | **Test Spec ID**: `TST-MAP-065` (`TST-MAP-065.1`..`TST-MAP-065.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-MAP-063`)

### 1.1 Problem Statement & Rationale
In [ATT-6211], `KnownLocationThumbnailMap` (`KnownLocationsScreen.kt`) was enhanced to render a centered Material heart glyph (`createCenteredHeartGlyphMarker`) with an opaque halo and concentric anchor `Offset(0.5f, 0.5f)`. However, the detail/edit map (`EditKnownLocationDialog.kt`) and global map (`MapContentScope.kt`) still use the legacy standing pin (`createHeartPinMarker`), violating Rule 80 cross-surface marker parity.

### 1.2 Functional & Architectural Requirements
* **REQ-MAP-063.1** (*Edit/Detail Dialog Map Marker Parity*): In `EditKnownLocationDialog.kt` (`LocationMiniMap`), the system SHALL instantiate `createCenteredHeartGlyphMarker` (`heartColor = TTColor.GeofencePin`, `outlineColor = Color.White`, `alpha = 0.85f`) and specify `anchor = Offset(0.5f, 0.5f)` on `Marker`. In `LocalInspectionMode.current == true`, it SHALL render `Icons.Default.Favorite` centered within the geofence circle preview instead of `R.drawable.my_locations`.
* **REQ-MAP-063.2** (*Global Map Known Locations Marker Parity*): In `MapContentScope.kt` (`locationData.forEach`), the system SHALL render known location markers using `createCenteredHeartGlyphMarker(context, primaryColor, Color.White, alpha = 0.85f)` with `anchor = Offset(0.5f, 0.5f)` to ensure concentric positioning in the geofence circle.
* **REQ-MAP-063.3** (*Legacy Pin Deprecation & Invariant Preservation*): `createHeartPinMarker` in `MapUtils.kt` SHALL be annotated `@Deprecated` for binary stability. Geofence circle radius, stroke color, dark mode anti-flash overlay, titles, and touch callbacks MUST remain 100% intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Edit Dialog Marker Concentricity)**: *Given* `EditKnownLocationDialog` opened for a Lieblingsort, *When* `LocationMiniMap` renders, *Then* the marker is a centered Material heart glyph with `anchor = Offset(0.5f, 0.5f)` concentric with the geofence circle center.
* **AC-2 (Global Map Marker Concentricity)**: *Given* `MapScreenWithTrack` with the Lieblingsorte layer active, *When* locations are rendered, *Then* all markers use `createCenteredHeartGlyphMarker` with `anchor = Offset(0.5f, 0.5f)`.
* **AC-3 (Compose Preview Parity)**: *Given* `LocationMiniMap` in `@Preview` (`LocalInspectionMode`), *When* rendered, *Then* a centered `Icons.Default.Favorite` is displayed within the geofence circle.

---

## 2. Test Specification & Traceability Matrix (`TST-MAP-065`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-063.1` | `TST-MAP-065.1` | `EditKnownLocationDialogTest.kt` | `LocationMiniMap` creates centered heart glyph marker and sets `anchor = Offset(0.5f, 0.5f)` |
| `REQ-MAP-063.2` | `TST-MAP-065.2` | `MapScreenWithTrackTest.kt` | `MapContentScope` uses `createCenteredHeartGlyphMarker` with `anchor = Offset(0.5f, 0.5f)` |
| `REQ-MAP-063.1` | `TST-MAP-065.3` | `KnownLocationsScreenTest.kt` | Cross-surface marker parity verified across thumbnail, edit dialog, and global map |
| `REQ-MAP-063.3` | `TST-MAP-065.4` | `MapUtilsTest.kt` | `createHeartPinMarker` annotated with `@Deprecated` |
| `REQ-MAP-063` | `TST-MAP-065.5` | Targeted Test Suite | Targeted tests: `ui.knownlocations.*`, `ui.map.*`, `ui.theme.*` |
