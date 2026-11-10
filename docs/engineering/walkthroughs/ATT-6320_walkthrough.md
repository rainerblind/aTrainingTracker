# ATT-6320 Walkthrough: Cross-Surface Centered Material Heart Marker Parity for Known Locations

**Ticket**: [ATT-6320](https://atrainingtracker.atlassian.net/browse/ATT-6320)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-MAP-063` | **Test Mapping**: `TST-MAP-065`  

---

## 1. Executive Summary

In [ATT-6211], `KnownLocationThumbnailMap` introduced a canonical centered Material heart glyph marker (`createCenteredHeartGlyphMarker`) with concentric `anchor = Offset(0.5f, 0.5f)`. However, `EditKnownLocationDialog.kt` (`LocationMiniMap`) and the global map screen (`MapContentScope.kt`) still used the legacy standing pin (`createHeartPinMarker`) with bottom-center anchor `(0.5f, 1.0f)`.

Under `ATT-6320` (governed by `REQ-MAP-063` / `TST-MAP-065`):
- **Edit/Detail Dialog Concentric Parity (`REQ-MAP-063.1`)**: In `EditKnownLocationDialog.kt` (`LocationMiniMap`), replaced `createHeartPinMarker` with `createCenteredHeartGlyphMarker(context, TTColor.GeofencePin, Color.White, alpha = 0.85f)` and added `anchor = Offset(0.5f, 0.5f)` to `Marker`. In `LocalInspectionMode` preview, replaced `R.drawable.my_locations` with centered `Icons.Default.Favorite` (tinted `TTColor.GeofencePin`).
- **Global Map Concentric Parity (`REQ-MAP-063.2`)**: In `MapContentScope.kt`, replaced `createHeartPinMarker` with `createCenteredHeartGlyphMarker(context, primaryColor, Color.White, alpha = 0.85f)` and added `anchor = Offset(0.5f, 0.5f)` for known location markers.
- **Legacy Pin Deprecation & Invariant Preservation (`REQ-MAP-063.3`)**: Annotated `createHeartPinMarker` in `MapUtils.kt` with `@Deprecated("Use createCenteredHeartGlyphMarker for concentric geofence alignment (REQ-MAP-063)")` preserving binary backwards compatibility. All geofence circles, click handlers, marker titles, and dark mode anti-flash overlays remain intact.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-063.1` | `TST-MAP-065.1` | `EditKnownLocationDialog.kt` | `MapScreenWithTrackTest.kt` | **Verified** |
| `REQ-MAP-063.2` | `TST-MAP-065.2` | `MapContentScope.kt` | `MapScreenWithTrackTest.kt` | **Verified** |
| `REQ-MAP-063.3` | `TST-MAP-065.3`, `TST-MAP-065.4` | `MapUtils.kt` | `MapScreenWithTrackTest.kt` | **Verified** |
| `REQ-MAP-063` | `TST-MAP-065.5` | Targeted Test Suite | Targeted Test Suite | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  - Scope: Database operations, map UI & marker contracts, aftermath workout lists, known locations screens, theme centralization audit, core components, and ASPICE SWE build hygiene tests.
  - Result: **100% PASS** (0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - `adb devices` reported no physical devices attached. Per `Rule 79`, emulator launch was omitted.
