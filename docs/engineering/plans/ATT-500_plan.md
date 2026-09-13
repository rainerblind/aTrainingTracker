# Implementation Plan: ATT-500 - Detailed Map View Heatmap Visibility Fix

## 1. Goal Description
Resolve the issue where density heatmaps vanish in detailed map views (zoomed-in views for Workout Clusters and Period Detail Maps):
1. **Calibrate Heatmap Weight & Intensity (`MapContentScope.kt`)**: Remove the fractional `heatmapWeight = 0.0002` and excessive `heatmapMaxIntensity = 200.0` for zoom >= 13 that caused point density to evaluate as 0.000001 (below `startIntensity`), making heatmaps completely invisible.
2. **Auto-Normalized Intensity Scale**: Allow `HeatmapTileProvider` to automatically scale and normalize density across the dataset without artificial attenuation at high zoom levels.
3. **Smooth Provider Caching**: Preserve active heatmap providers across minor zoom stepped states to ensure continuous, jank-free rendering.

---

## 2. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt`
#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- Update `Render(currentZoom: Float)`:
  - Calibrate `heatmapWeight` to standard values (e.g. `1.0` or `0.8`) across all zoom levels.
  - Set `maxIntensity = null` (or proportional value) so `HeatmapTileProvider` automatically normalizes density and does not attenuate points to zero.
  - Adjust `heatmapStartIntensity` (e.g. `0.2f` to `0.4f`) so low-density trails render gracefully.

---

## 3. Verification Plan

### Automated Tests
- Execute `:app:testDebugUnitTest` to verify no regressions in map or UI components.

### Manual Verification Steps (`TST-MAP-020`)
1. **Cluster Heatmap Verification**:
   - Open a Workout Cluster in the map view.
   - Zoom in to detailed levels (zoom 13 to 18) over a route area.
   - Verify the translucent heatmap remains clearly visible without vanishing or turning blank.
2. **Period Map Verification**:
   - Open a Period Detail map (Month or Year).
   - Zoom in to detailed levels (zoom 13 to 18) over route areas.
   - Verify the heatmap remains clearly visible over all route areas.
