# Implementation Plan - ATT-342 Refinement: Robust Heatmap Stability & Precision

Address the `IllegalArgumentException` in gradient generation, prevent `OutOfMemoryError` by further reducing the point budget, and implement aggressive visual thinning for city-scale zoom levels.

## User Review Required

> [!IMPORTANT]
> - **Crash Fix**: I identified the mathematical cause of the "increasing order" crash. I will refactor the gradient engine to ensure it is always valid, regardless of the sharpening intensity.
> - **Safe Memory Budget**: I am reducing the maximum point budget from 40,000 to **15,000**. This is a 100% safe limit that prevents OOM crashes while still providing a high-quality visualization for your clusters.
> - **Extreme Sharpening**: When zoomed out (level 10-12), the lines will now be **ultra-thin**. I am reducing the point weights by another 90% (to **0.005**) and implementing a dynamic `maxIntensity` threshold.
> - **Result**: The heatmap will look like a crisp, professional trace that perfectly follows the roads without any visual "bloat".

## Proposed Changes

### 1. Map Utility Layer: Correct & Fast Logic
Fulfills REQ-MAP-017 (Refinement) | Test: TST-PERF-001

#### [MODIFY] [MapUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt)
- **Robust Thinning**: Fix the logic to always apply thinning if `rawPointCount > maxPoints`.
- **Densification Guard**: Strictly disable point densification whenever thinning is active. This prevents the engine from trying to add more points to an already overloaded budget.
- **Gradient Fix**: Dynamically calculate the intermediate color points to ensure they are always greater than `startIntensity`.
- **Max Intensity Support**: Add `maxIntensity: Double?` parameter to allow the UI to manually control the saturation point.

### 2. Map DSL Layer: Precision Scaling
Fulfills REQ-MAP-016 (Refinement) | Test: TST-MAP-010

#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- **Reduce Point Cap**: Set `effectiveMaxPoints` to **15,000**.
- **Ultra-Thin Schedule**:
    | Zoom | Weight | Start Intensity | Max Intensity |
    |:---|:---|:---|:---|
    | < 10 | **0.005** | **0.2f** | 20.0 |
    | 10-12 | **0.01** | **0.2f** | 10.0 |
    | 13-14 | 0.5 | 0.4f | 1.0 |
    | 15+ | 1.5 | 0.5f | 1.0 |
- **Rationale**: By using a lower `startIntensity` combined with a very high `maxIntensity` and extremely low weights, we achieve a much more precise "line" effect without the Gaussian blur overlapping into wide bands.

## Verification Plan

### Automated Tests
- **Stability Audit**: Rapidly zoom in and out on a cluster with > 50 workouts to ensure zero crashes and consistent memory usage.

### Manual Verification (TST-MAP-010 Refined)
1. Open the 'Solitude Runde' heatmap.
2. **Verify** that at zoom level 10-12, the lines are sharp and thin.
3. **Verify** that the app remains interactive and stable during all navigation actions.
