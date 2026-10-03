# Stage 1 Analysis: ATT-1479 - Maps display light/white background and lack dark theme styling in dark mode

**Ticket**: [ATT-1479](https://atrainingtracker.atlassian.net/browse/ATT-1479)  
**Sub-task**: [ATT-1536](https://atrainingtracker.atlassian.net/browse/ATT-1536) (`[Analysis]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.2`  
**Branch**: `feature/ATT-1479`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Statement & Motivation

While [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt) received dark styling in ATT-1266, multiple secondary map screens and card components across the application continue to display a blinding light/white background in Dark Mode:
1. **Hardcoded Light MapType on Secondary Map Views**:
   Multiple key map components instantiate `GoogleMap` directly with hardcoded light terrain properties (`MapProperties(mapType = MapType.TERRAIN)`) or unstyled defaults, ignoring the active app/system theme:
   * [EditKnownLocationDialog.kt:446](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt#L446): Mini-map preview in favorite locations edit sheet.
   * [PathPreviewMap.kt:73](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt#L73): Route and Strava Segment preview cards in list views.
   * [WorkoutClusterComponents.kt:280, 495](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt#L280): Workout cluster heatmaps and cluster management cards.
   * [ManualClusterScreen.kt:238](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt#L238): Manual cluster creation and boundary adjustment.
   * [PeriodSummaryCard.kt:576](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt#L576): Period summary map cards.
   * [LapEditBottomSheet.kt:576](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt#L576): Lap detail map preview.
   * [ImportBackupTabsScreen.kt:610](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt#L610): Cluster naming backup import map.
2. **White Container Background & Tile Loading Flashes**:
   In both `ATrainingTrackerMap` and standalone `GoogleMap` composables, map containers omit an explicit dark background modifier. When a map composable first mounts, while vector tiles load over the network, or during panning/zooming gestures into uncached tile boundaries, the default light background of the underlying view hierarchy flashes white, causing night-blindness and glare for endurance athletes.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Gap Analysis
1. **Decoupled Architecture & Missing Shared Resolver**:
   In ATT-1266 (`REQ-MAP-021`), the dark map style pipeline was coupled primarily to `ATrainingTrackerMap`. While [DarkMapStyle.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyle.kt) cached `MapStyleOptions`, it did not expose a turnkey `resolveMapProperties(isDark: Boolean, context: Context)` helper. Secondary map composables were left with legacy hardcoded `MapProperties(mapType = MapType.TERRAIN)`.
2. **Google Maps SDK MapType Constraints (Terrain vs. Normal)**:
   Per Google Maps Android SDK specifications, custom JSON styling (`MapStyleOptions`) is strictly supported only on `MapType.NORMAL`. The SDK does *not* support custom vector styling on `MapType.TERRAIN` or `MapType.HYBRID`. Consequently, when the app enters Dark Mode, switching to `MapType.NORMAL` with `map_style_dark.json` is a technical prerequisite to eliminate blinding white backgrounds. When returning to Light Mode, the system cleanly reverts to `MapType.TERRAIN` (preserving topographic elevation shading as mandated by `REQ-MAP-021`).
3. **Missing Surface Background on Map Hosts & SurfaceView Masking**:
   Google Maps Android SDK renders vector tiles into a `SurfaceView` / `TextureView`. Until tiles are decoded and rasterized, the surface displays the host container's background. Because Compose map wrappers lacked `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)`, the transparent/unassigned host background defaults to the window canvas, causing bright flashes during tile loading.

### Call-Site Audit Matrix
| Call Site / Component | Current Properties | Current Container Background | Target Properties | Target Container Background |
| :--- | :--- | :--- | :--- | :--- |
| [DarkMapStyle.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyle.kt) | N/A (Helper) | N/A | Add `resolveMapProperties(isDark, context, isMyLocationEnabled, lightMapType)` | N/A |
| [ATrainingTrackerMap.kt:195](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt#L195) | Dynamic (ATT-1266) | Transparent / Unset | Dynamic (Preserved) | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |
| [PathPreviewMap.kt:73](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt#L73) | `MapType.TERRAIN` | Unset | `DarkMapStyle.resolveMapProperties(isDark, context)` | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |
| [EditKnownLocationDialog.kt:446](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt#L446) | `MapType.TERRAIN` | Unset | `DarkMapStyle.resolveMapProperties(isDark, context)` | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |
| [WorkoutClusterComponents.kt:280, 495](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt#L280) | `MapType.TERRAIN` | Unset | `DarkMapStyle.resolveMapProperties(isDark, context)` | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |
| [ManualClusterScreen.kt:238](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt#L238) | Default unstyled | Unset | `DarkMapStyle.resolveMapProperties(isDark, context, isMyLocationEnabled = true)` | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |
| [PeriodSummaryCard.kt:576](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt#L576) | `MapType.TERRAIN` | Unset | `DarkMapStyle.resolveMapProperties(isDark, context)` | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |
| [LapEditBottomSheet.kt:576](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt#L576) | `MapType.TERRAIN` | Unset | `DarkMapStyle.resolveMapProperties(isDark, context)` | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |
| [ImportBackupTabsScreen.kt:610](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt#L610) | Default unstyled | Unset | `DarkMapStyle.resolveMapProperties(isDark, context)` | `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Universal Dark Map Styling: When Dark Mode is active (via system or app theme), all map components throughout the application apply `DarkMapStyle` (`mapType = MapType.NORMAL` with `R.raw.map_style_dark`).
  2. Elimination of White Loading Flashes: Map composables and containers set an explicit dark background (`Color(0xFF121212)`) in dark mode so tile loading, initial composition, or edge panning never expose a white surface.
  3. Light Mode Baseline Preservation: In Light Mode, maps continue to use standard light styling (`MapType.TERRAIN` with null style options) as established in `REQ-MAP-021`.
  4. Single Source of Truth for Dark Detection: Standardize on `MaterialTheme.colorScheme.surface.luminance() < 0.5f` across all Compose map composables to uniformly respect both system and in-app AMOLED themes.
  5. Shared helper in `DarkMapStyle.kt` ensuring consistent resolution across all call sites with customizable `lightMapType`.
  6. Unit tests validating dynamic `MapProperties` resolution, terrain fallback, and container styling.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No modifications to track polyline decoding algorithms, map projection maths, or GPS coordinate providers.
  2. No restructuring of card layouts, buttons, or dialog actions surrounding the map views.
  3. No changes to Mapbox / third-party non-Google map engines (the app strictly uses Google Maps SDK).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

The forensic investigation identified `REQ-MAP-021` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) as the authoritative governing requirement:

* **Original Requirement ID & Target**: `REQ-MAP-021` targeting [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt) and [DarkMapStyle.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyle.kt).
* **Historical Origin & Commit Trace**: Commit `5d8a0c23` (ATT-1266, 2026-09-12).
* **Root Reason for Existing Formulation**: ATT-1266 focused specifically on live tracking and navigation on the central cockpit map (`ATrainingTrackerMap`). Secondary preview cards and dialog mini-maps were left using standard `MapType.TERRAIN` because shared helper functions were not yet published.
* **Preservation of Core Invariants**: Extending `REQ-MAP-021` to encompass all secondary map views preserves the light mode baseline (`MapType.TERRAIN`, null style options), singleton style caching (<10 KB RAM, <3ms parse), exception fallback to `MapType.NORMAL`, and custom route/segment polyline colors.

---

## 5. Architectural Strategy & High-Level Solution

### Architectural Principles (SWE.2)
1. **Centralized MapProperties Factory in `DarkMapStyle.kt`**:
   Expose a public, thread-safe helper accepting optional parameters:
   ```kotlin
   fun resolveMapProperties(
       isDark: Boolean,
       context: Context,
       isMyLocationEnabled: Boolean = false,
       lightMapType: MapType = MapType.TERRAIN
   ): MapProperties {
       val darkOptions = if (isDark) getMapStyleOptions(context) else null
       return if (isDark) {
           MapProperties(
               mapType = MapType.NORMAL,
               mapStyleOptions = darkOptions,
               isMyLocationEnabled = isMyLocationEnabled
           )
       } else {
           MapProperties(
               mapType = lightMapType,
               mapStyleOptions = null,
               isMyLocationEnabled = isMyLocationEnabled
           )
       }
   }
   ```
2. **Unified Theme Detection Standard**:
   Standardize on `val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f` across all map call sites, guaranteeing consistent reaction to AMOLED dark mode and system theme switches.
3. **Double-Layer Anti-Flash Protection**:
   * Parent container (`Box` or outer layout): `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)`.
   * `GoogleMap` composable itself: `modifier.background(if (isDark) Color(0xFF121212) else Color.White)`.
   * This guarantees that during initial mount, asynchronous tile fetching, and edge panning, the surface never flashes white.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Light Mode appearance remains 100% identical (`MapType.TERRAIN` with ambient styling).
  2. Singleton caching in `DarkMapStyle` (<10 KB RAM, <3ms parse) is preserved across all call sites.
  3. Polyline colors (cyan in dark mode for live tracks, user colors for routes/segments) remain strictly intact.
  4. 100% full-suite unit test pass rate (`./gradlew testDebugUnitTest`).

* **Risk Rating**: **LOW**  
  *Justification*: The refactoring is visual and styling-oriented (resolving `MapProperties` and setting container background color). Zero changes to database schemas, background threads, or GPS lifecycle operations.
