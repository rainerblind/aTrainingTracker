# Stage 1 Analysis: ATT-6320 - Cross-Surface Centered Heart Marker Parity for Known Locations

**Ticket**: [ATT-6320](https://atrainingtracker.atlassian.net/browse/ATT-6320)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6320`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
* **Context**: In [ATT-6211], `KnownLocationThumbnailMap` (`KnownLocationsScreen.kt`) was updated to render a centered Material heart glyph (`createCenteredHeartGlyphMarker`) with an opaque white halo and concentric anchor `Offset(0.5f, 0.5f)`.
* **Defect**: The detail/edit dialog map (`EditKnownLocationDialog.kt` — `LocationMiniMap`) and the central map (`MapScreenWithTrack.kt` / `MapContentScope.kt`) still use the legacy standing pin (`createHeartPinMarker` / `R.drawable.my_locations`). This causes visual discordance and marker asymmetry across the 3 map surfaces of the app (Rule 80).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * In `EditKnownLocationDialog.kt:610-616`, `LocationMiniMap` instantiates `createHeartPinMarker` without concentric anchor, defaulting to bottom-center anchor `(0.5f, 1.0f)`. In `LocalInspectionMode`, it renders `R.drawable.my_locations` instead of `Icons.Default.Favorite`.
  * In `MapContentScope.kt:430-432`, the `knownLocations` layer instantiates `createHeartPinMarker` with default anchor `(0.5f, 1.0f)`.
* **Independent Peer Review & Verification (ATT-3534, Rule 80)**:
  * Inspected `KnownLocationsScreen.kt:703-758`: Uses `createCenteredHeartGlyphMarker` (`heartColor = TTColor.GeofencePin`, `outlineColor = Color.White`, `alpha = 0.85f`) with `anchor = Offset(0.5f, 0.5f)`.
  * Inspected `EditKnownLocationDialog.kt:610-641`: Directly calls `createHeartPinMarker` for GoogleMap and `R.drawable.my_locations` for preview.
  * Inspected `MapContentScope.kt:430-450`: Directly calls `createHeartPinMarker` for all Known Locations on the global map.
  * Verified `docs/coding_standards.md` §1.11 (Rule 80): Mandates concentric marker geometry parity across thumbnail, detail, and global inspection map surfaces.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Refactor `LocationMiniMap` in `EditKnownLocationDialog.kt` to use `createCenteredHeartGlyphMarker` with `anchor = Offset(0.5f, 0.5f)` and `Icons.Default.Favorite` for Compose preview.
  * Refactor `knownLocations` in `MapContentScope.kt` to use `createCenteredHeartGlyphMarker` with `anchor = Offset(0.5f, 0.5f)`.
  * Deprecate or isolate `createHeartPinMarker` in `MapUtils.kt` so no active UI screen uses the legacy standing pin for Lieblingsorte.
  * Add unit and contract tests in `EditKnownLocationDialogTest.kt` and `MapScreenWithTrackTest.kt`.
* **Out-of-Scope Non-Goals**: Modifying geofence circle math, altering route/segment map pins, or modifying database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-MAP-063` (*Cross-Surface Centered Heart Marker Parity*), refining `REQ-UI-470.2` (*Canonical Material Heart Glyph Marker & Opaque Halo*) and `REQ-MAP-021` (*Map Styling*).
* **Historical Origin & Commit Trace**: `44d51eac` (`ATT-1816`), `ce73d561` (`ATT-5884`), `9e49a75d` (`ATT-6211`).
* **Root Reason for Existing Formulation**: Standing pin was the legacy map pin for all POIs prior to the introduction of concentric geofence thumbnail markers in ATT-6211.
* **Preservation of Core Invariants**: Geofence circle radius, stroke color (`TTColor.GeofenceStroke`), fill color (`TTColor.GeofenceFill`), click callbacks, and dark mode anti-flash overlays strictly preserved.
* **Side-Effect Audit (Rule 68)**: Zero SQLite changes; zero navigation breakage; touch targets remain centered on true location coordinates.

---

## 5. Architectural Strategy & High-Level Solution
* **EditKnownLocationDialog**: Replace `createHeartPinMarker` with `createCenteredHeartGlyphMarker(context, TTColor.GeofencePin, Color.White, 0.85f)`, add `anchor = Offset(0.5f, 0.5f)` to `Marker`, and use `Icons.Default.Favorite` in `LocalInspectionMode`.
* **MapContentScope**: Replace `createHeartPinMarker` with `createCenteredHeartGlyphMarker(context, primaryColor, Color.White, 0.85f)` and add `anchor = Offset(0.5f, 0.5f)` to `Marker`.
* **MapUtils**: Retain `createHeartPinMarker` as `@Deprecated` for binary stability.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in `EditKnownLocationDialogTest`, `MapScreenWithTrackTest`, and `KnownLocationsScreenTest`.
  2. Concentric alignment of heart glyph with geofence center on all zoom levels.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Leaf UI marker and anchor replacement with zero data layer or concurrency side-effects.
