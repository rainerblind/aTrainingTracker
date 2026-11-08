# Stage 5: Walkthrough & Verification - ATT-6211: Canonical Material Heart Marker Geometry

**Ticket**: [ATT-6211](https://atrainingtracker.atlassian.net/browse/ATT-6211)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6211`  
**Requirement Mapping**: `REQ-UI-470` (`REQ-UI-470.2`) | **Test Spec ID**: `TST-UI-470` (`TST-UI-470.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Scope
Sprint Review 2026-45.12 (`ATT-5884`) identified that the centered heart marker in `KnownLocationThumbnailMap` appeared squished and flat-lobed (resembling a triangular shield), and its translucent outline allowed the blue geofence circle stroke to bleed through on larger radii. In this ticket, `createCenteredHeartGlyphMarker` in `MapUtils.kt` was upgraded to render canonical Material `Favorite` vector geometry with an opaque white halo barrier.

---

## 2. On-Device Visual Inspection (Rule 71 Certified)
* Verified on physical **Pixel 10** (`66020DLCR002FL`, Android 17):
  * Navigated to *Karten -> Lieblingsorte* (`KnownLocationsScreen`).
  * Inspected thumbnail maps for *Zu Hause*, *IAV*, and *Dammweg, Schönaich*.
  * **Result**: The centered heart marker renders canonical Material `Favorite` geometry with rounded upper lobes and an opaque white halo (`Color.White`, `alpha = 1.0f`, stroke `3f * density`) cleanly isolating the marker from geofence circle strokes (`TTColor.GeofenceStroke`).

![Known Location Canonical Heart Marker](file:///home/rainer/.gemini/antigravity/worktrees/b86ffd1a-5a6a-4e52-9976-b53aca20c1c8/subagent-Epic-ATT-1396-Pro-Agent-self-5415be16/docs/engineering/walkthroughs/img/ATT-6211_known_location_heart_marker.png)

---

## 3. Automated Test Execution & Scope
* **Tier 1 Targeted Test Scope**:
  * Command: `./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * **Result**: **666 / 666 tests PASS (100%)** executed under `parallel_sprint_lock.py` (23s runtime).
* **Living Documentation Governance**:
  * Command: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-45.13`
  * **Result**: **100% PASS** (all 9 domain shards synchronized, Chesterton's Fence 4-field audit verified for `REQ-UI-470.2`).

---

## 4. Acceptance Criteria Verification Table

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1 (Canonical Heart Geometry)**: Canonical Material `Favorite` heart geometry rendered in `KnownLocationThumbnailMap` | Unit Test + Device Inspection | **MET** | `KnownLocationsScreenTest.kt:440`, `ATT-6211_known_location_heart_marker.png` |
| **AC-2 (Opaque White Halo Shield)**: Opaque white halo outline (`alpha = 1.0f`, width `3dp`) prevents geofence stroke bleed-through | Device Inspection | **MET** | `MapUtils.kt:190-198`, `ATT-6211_known_location_heart_marker.png` (*IAV*, *Zu Hause*) |
| **AC-3 (Concentric Anchor Invariant)**: Concentric `anchor = Offset(0.5f, 0.5f)` preserved | Contract Test | **MET** | `KnownLocationsScreenTest.kt:425` |
| **AC-4 (Standing Pin Independence)**: Full-screen `createHeartPinMarker` untouched | Contract Test | **MET** | `KnownLocationsScreenTest.kt:419` |
| **AC-5 (Governance & Parity)**: Master tables synced, living docs verified | Governance Tool | **MET** | `verify_requirement_governance.py` PASS |
