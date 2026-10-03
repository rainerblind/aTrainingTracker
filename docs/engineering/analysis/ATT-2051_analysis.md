# Stage 1: Problem Domain & Root Cause Analysis - ATT-2051: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard

**Ticket**: [ATT-2051](https://rainerblind.atlassian.net/browse/ATT-2051)  
**Sub-task**: [ATT-2107](https://rainerblind.atlassian.net/browse/ATT-2107) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Branch**: `feature/ATT-2051`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Domain & Root Cause Analysis

### 1.1 Context & Problem Statement
In aTrainingTracker, two primary screens display spatial navigation cards with embedded Google Maps lite-mode preview thumbnails:
1. **Lieblingsstrecken** ([WorkoutClusterComponents.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt)):
   - Card lists representing clustered route segments.
   - Hosts a map preview thumbnail configured as a **100dp** square (`Modifier.size(100.dp)`), with `RoundedCornerShape(12.dp)`.
2. **Lieblingsorte** ([KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt)):
   - Card lists representing user-defined favorite locations and geofence anchors.
   - Hosts `KnownLocationThumbnailMap` configured as an **80dp** square (`Modifier.size(80.dp)`), with `RoundedCornerShape(12.dp)`.

### 1.2 Root Cause
The 20dp discrepancy (`80dp` vs. `100dp`) stems from separate evolutionary sprints:
* `Lieblingsstrecken` cards (`WorkoutClusterComponents.kt`) adopted 100dp in Sprint 2026-40.6 (`ATT-1750`) to accommodate complex polylines and multiple elevation route bounds.
* `Lieblingsorte` cards (`KnownLocationsScreen.kt`) were given an initial 80dp thumbnail in Sprint 2026-40.7 (`ATT-1816` / `REQ-UI-217`) as a conservative dimension before route badges were added.

Because both screens serve peer spatial navigation workflows under the primary app drawer / tabs, having different thumbnail sizes produces an uneven visual rhythm, breaks layout symmetry, and impairs design consistency across the application.

---

## 2. Chesterton's Fence & Requirement Archaeology

### 2.1 Archaeology Trace
* **Preceding Requirement**: `REQ-UI-217` (*Lieblingsorte: Map Preview Thumbnail and Standardized Title Typography on KnownLocationCard*, Sprint 2026-40.7 / `ATT-1816`).
  - Added `KnownLocationThumbnailMap` using `Modifier.size(80.dp)`.
  - Added Google Maps lite mode, dark/light map styling, heart pin marker, geofence circle, and `LocalInspectionMode.current` fallback.
* **Calibrated Framing**: `REQ-UI-224` (*Lieblingsorte: Calibrated Zoom Level and Dynamic Geofence Framing on KnownLocationCard Map Thumbnail*, Sprint 2026-40.8 / `ATT-1878`).
  - Implemented `KnownLocationZoomMath.kt` with zoom range `[10.5f, 12.5f]` to prevent geofence boundary clipping.
* **Refined Badges Layout**: `REQ-UI-195` & `REQ-UI-207` (Sprint 2026-40.5 & 40.7).
  - Compacted Starts and Routes badges with `FlowRow` and `Modifier.weight(1f)` on the left metadata column.

### 2.2 Why 80dp Was Chosen (The Fence)
The original 80dp sizing in `REQ-UI-217` was chosen to preserve space for left-column text metrics on small screens (e.g. 360dp width devices) when the card header had just been enlarged to `titleLarge`.

### 2.3 Why Modern Sizing Permitted Harmonization (Why It Can Be Changed)
Subsequent sprint enhancements in `ATT-1950` and `ATT-1878` streamlined the left column:
- The left column uses `Modifier.weight(1f)` with vertical alignment.
- Badges use a flexible `FlowRow` that wraps gracefully.
- On standard 392dp–412dp devices (e.g. Pixel 8/9/10), increasing the thumbnail to 100dp still leaves 250dp+ for the left column, more than enough for title, altitude, and badge rows without truncation.
- 100dp creates identical proportions between `Lieblingsorte` and `Lieblingsstrecken`, fulfilling the visual consistency mandate of Epic `ATT-355`.

---

## 3. Scope Bounding & Impact Analysis

### 3.1 In-Scope
1. **`KnownLocationsScreen.kt`**:
   - Update `KnownLocationThumbnailMap` `Surface` modifier from `.size(80.dp)` to `.size(100.dp)`.
   - Update KDoc reference from 80dp to 100dp.
2. **`KnownLocationCardLayoutTest.kt`**:
   - Update contract test assertion from `.size(80.dp)` to `.size(100.dp)`.
3. **Living Documentation**:
   - Update `docs/requirements.md` (add `REQ-UI-244` / update `REQ-UI-217`).
   - Update `docs/tests.md` (add `TST-UI-203`).

### 3.2 Out-of-Scope (Invariants Protected)
1. **`WorkoutClusterComponents.kt`**: Must NOT be modified (already 100dp).
2. **`KnownLocationZoomMath.kt`**: Preserves zoom range `[10.5f, 12.5f]` which frames geofences cleanly within the 100dp container.
3. **Card Interactions**: Single-tap edit dialog (`onEdit`), long-press delete menu (`REQ-UI-061`), starts drill-down (`onShowWorkouts`), and routes drill-down (`onShowRoutes`) MUST remain intact.
4. **Google Maps Lite Mode**: `GoogleMapOptions().liteMode(true)`, dark mode styling, and `LocalInspectionMode` fallback MUST remain intact.

---

## 4. Target Architecture & Proposed Solution

```
+---------------------------------------------------------------+
| KnownLocationCard (fillMaxWidth, elevation 1.dp)              |
|                                                               |
| Location Title (titleLarge Bold)                              |
|                                                               |
| Row (fillMaxWidth, verticalAlignment = CenterVertically)      |
| +-----------------------------+ +---------------------------+ |
| | Left Column (weight 1f)     | | KnownLocationThumbnailMap | |
| | - Altitude Row (ic_ascent)  | | - size(100.dp) [HARMONIZED| |
| | - FlowRow:                  | | - RoundedCornerShape(12dp)| |
| |   [Starts Badge]            | | - LiteMode GoogleMap      | |
| |   [Routes Badge]            | | - HeartPin + Circle       | |
| +-----------------------------+ +---------------------------+ |
+---------------------------------------------------------------+
```

---

## 5. Risk Assessment & Verification Strategy

| Risk | Likelihood | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| Text wrapping on very narrow screens (<360dp) | Low | Low | Left column uses `Modifier.weight(1f)` and `FlowRow` for badges, preventing hard overflow |
| Visual regression in existing card layout tests | High | Low | Update `KnownLocationCardLayoutTest.kt` to assert `.size(100.dp)` and verify with `./gradlew testDebugUnitTest` |
| Google Maps lite mode crash in offline tests | Zero | High | `LocalInspectionMode.current` Canvas fallback is strictly preserved |

### Verification Suite
1. `KnownLocationCardLayoutTest`
2. `KnownLocationsScreenTest`
3. `KnownLocationZoomMathTest`
4. Clean-room regression: `./gradlew testDebugUnitTest` (1,400+ tests).
