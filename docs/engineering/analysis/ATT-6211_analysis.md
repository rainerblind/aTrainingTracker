# Stage 1 Analysis: ATT-6211 - Canonical Material Heart Marker Geometry for Known Locations

**Ticket**: [ATT-6211](https://atrainingtracker.atlassian.net/browse/ATT-6211)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6211`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Context**: Sprint Review 2026-45.12 (`ATT-5884`) introduced `createCenteredHeartGlyphMarker` in `MapUtils.kt` to replace the 36dp standing pin on `KnownLocationThumbnailMap`.
* **Identified Defects**:
  1. *Deformed Geometry*: The ad-hoc cubic Bézier curves in `MapUtils.kt:136-157` yield excessively flat/broad upper lobes (`top + height * 0.22f` with wide apexes) and a blunt bottom tip at `0.85f` height, making the marker look like a squished triangular shield with a shallow notch rather than a harmonious heart.
  2. *Circle & Marker Clashing*: Both marker fill and geofence circle share blue tones (`TTColor.GeofencePin = Color(0xFF2196F3)` and `TTColor.GeofenceStroke = Color(0x882196F3)`). For locations with larger geofence radii (e.g. *Zu Hause*, *IAV* in `att_5884_heart_marker.png`), the blue circle stroke directly coincides with the heart's upper lobes.
  3. *Translucent Halo Bleed-Through*: The white outline is drawn with `outlineColor.copy(alpha = alpha)` (85% opacity), eroding the white halo's contrast barrier so the blue circle stroke bleeds through into the marker.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  1. *Geometry*: Ad-hoc Bézier points attempted to approximate a heart on a unit square without canonical proportions (Material `Favorite` icon uses a 24x24 grid with circular lobe arcs, deep central notch to y=5.09, and smooth taper to point at y=21.35).
  2. *Contrast*: Halo opacity was coupled to marker alpha, preventing an opaque visual separation barrier against the geofence stroke and terrain.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `docs/attachments/att_5884_heart_marker.png`: Confirmed visual deformity and blue-on-blue stroke blending on *Zu Hause* and *IAV* thumbnails.
  * Inspected `MapUtils.kt:114-176`: Replacing the ad-hoc path with canonical Material `Favorite` geometry (`M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z`), scaled and centered at `(totalSize/2, totalSize/2)`, restores canonical Material aesthetics.
  * Inspected halo rendering: Rendering an opaque white halo (`Color.White`, alpha = 1.0f, stroke width ~3.0dp, round cap/join) provides a solid contrast boundary that cleanly isolates the heart marker from geofence strokes and terrain features in both light and dark map styles.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Update `createCenteredHeartGlyphMarker` in `MapUtils.kt` to construct/render the canonical Material Design Favorite path geometry.
  * Render a solid opaque white halo (`outlineColor = Color.White`, 100% alpha, stroke width ~3dp) to isolate the marker from the geofence circle.
  * Fine-tune marker size / contrast in `KnownLocationThumbnailMap` (`KnownLocationsScreen.kt`).
  * Add unit/contract tests for canonical geometry and halo properties.
* **Out-of-Scope**: Altering `KnownLocationZoomMath`, changing `KnownLocationCard` layouts, database changes, or modifying full-screen map pins (`createHeartPinMarker`).

---

## 4. Requirement Archaeology, Chesterton's Fence & Side-Effect Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-UI-470.2` (*Centered Heart Glyph Marker for Known Locations Map Preview*).
* **Historical Origin & Commit Trace**: `ce73d561` (`ATT-5884`) introduced `createCenteredHeartGlyphMarker` to replace the 36dp standing pin that occluded upper map terrain.
* **Root Reason for Existing Formulation**: Ad-hoc cubic Bézier curves were quickly hand-coded in ATT-5884 without importing canonical SVG path data; halo alpha was inadvertently coupled to marker alpha.
* **Preservation of Core Invariants**: Concentric anchor `Offset(0.5f, 0.5f)` strictly preserved; `KnownLocationsScreenTest.kt` contract preserved; zero overhead on map rendering.
* **Side-Effect Audit (Rule 68)**: `createCenteredHeartGlyphMarker` is exclusively invoked by `KnownLocationThumbnailMap` in `KnownLocationsScreen.kt`. Full-screen maps (`MapContentScope.kt`, `EditKnownLocationDialog.kt`) use `createHeartPinMarker`. Zero side-effects on background services, DB, or other screens.

---

## 5. Architectural Strategy & High-Level Solution
* **Canonical Geometry**: Implement Material `Favorite` path (`M12,21.35...`) scaled by `glyphSize / 24f` and centered within the bitmap canvas via transformation matrix.
* **Opaque Halo Shield**: Render solid opaque white stroke (~3dp) with `Paint.Cap.ROUND` and `Paint.Join.ROUND` before drawing the filled heart, completely preventing geofence circle stroke bleed-through.
* **Preview Parity**: 1:1 visual fidelity between `LocalInspectionMode` (`Icons.Default.Favorite`) and the runtime Google Maps `BitmapDescriptor`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Active Sprint `2026-45.13`; marker anchor `Offset(0.5f, 0.5f)` preserved; 100% test pass in `KnownLocationsScreenTest`.
* **Risk Rating**: **LOW** — Isolated leaf UI rendering enhancement in `MapUtils.kt` with zero schema or architectural risk.
