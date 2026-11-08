# Stage 3: Implementation Plan - ATT-6211: Canonical Material Heart Marker Geometry

**Ticket**: [ATT-6211](https://atrainingtracker.atlassian.net/browse/ATT-6211)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6211`  
**Requirement Mapping**: `REQ-UI-470` (`REQ-UI-470.2`) | **Test Mapping**: `TST-UI-470` (`TST-UI-470.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Objective
`createCenteredHeartGlyphMarker` in `MapUtils.kt` (introduced in `ATT-5884`) currently draws an ad-hoc cubic Bézier path resulting in flattened upper lobes and a blunt bottom tip (resembling a squished shield). Furthermore, coupling halo alpha to marker alpha allows the blue geofence circle stroke (`TTColor.GeofenceStroke = 0x882196F3`) to bleed into the marker on larger radii (e.g. *Zu Hause*, *IAV*). This plan replaces the ad-hoc path with canonical Material `Favorite` geometry and establishes a solid opaque white halo isolation shield.

---

## 2. Requirements & Living Documentation Mapping
* **Requirement**: `REQ-UI-470.2` (*Canonical Material Heart Glyph Marker & Opaque Halo Barrier*).
  * Canonical Material `Favorite` vector geometry: `M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z` on 24x24 grid, centered on canvas.
  * Opaque white halo barrier: `outlineColor = Color.White`, 100% opacity (`alpha = 1.0f`), stroke width $\ge 2.5\text{dp}$, `Paint.Cap.ROUND`, `Paint.Join.ROUND`.
  * Concentric anchoring: `anchor = Offset(0.5f, 0.5f)` preserved in `KnownLocationThumbnailMap`.
* **Test Mapping**: `TST-UI-470.3` (Contract and geometry tests in `KnownLocationsScreenTest.kt`) and `TST-UI-470.6` (Tier 1 regression).

---

## 3. System Invariants & UI Consistency
1. **Concentric Centering**: `KnownLocationThumbnailMap` marker anchor remains strictly `Offset(0.5f, 0.5f)`.
2. **Preview Parity**: `LocalInspectionMode` Compose preview continues using `Icons.Default.Favorite`.
3. **Standing Pin Independence**: Full-screen map pin `createHeartPinMarker` remains completely untouched.
4. **UI Consistency**: Reuses `TTColor.GeofencePin`, `TTColor.GeofenceStroke`, and `Color.White` with zero one-off styling additions.

---

## 4. Architectural Strategy & Component Design
* **Component 1 (`MapUtils.kt`)**:
  * Define canonical path constant `CANONICAL_MATERIAL_HEART_PATH_DATA`.
  * In `createCenteredHeartGlyphMarker`, construct path matching canonical Material `Favorite` coordinates scaled by `scale = glyphSize / 24f` and translated by `(cx - 12f * scale, cy - 12.175f * scale)`.
  * Render outer halo with solid `outlineColor.toArgb()` (100% alpha, stroke width `3f * density`) before drawing the filled heart path.
* **Component 2 (`KnownLocationsScreenTest.kt`)**:
  * Enhance `testKnownLocationsThumbnailMap_usesCenteredHeartGlyphMarker` to assert canonical Material heart path constants and opaque halo configuration in `MapUtils.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Check
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` to verify Gate 3 PASS before code edits.

### Step 2: Implement Canonical Material Heart Path & Opaque Halo in `MapUtils.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt`
* Replace ad-hoc Bézier curves with canonical Material `Favorite` path construction.
* Decouple halo opacity from marker fill alpha (`outlinePaint.color = outlineColor.toArgb()`).

### Step 3: Enhance Unit & Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
* Add assertions for `CANONICAL_MATERIAL_HEART_PATH_DATA`, opaque halo rendering, and concentric marker anchor.

### Step 4: Verification & Governance
* Execute targeted unit tests:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Execute governance audit: `python3 tools/verify_requirement_governance.py`
