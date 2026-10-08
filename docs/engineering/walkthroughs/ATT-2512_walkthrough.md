# Stage 5 Walkthrough - ATT-2512

**Ticket**: [ATT-2512](https://atrainingtracker.atlassian.net/browse/ATT-2512)  
**Summary**: Smooth Elevation Profile Using Distance-Weighted Kernel to Eliminate Quantization Staircase on Segments  
**Requirement Mapping**: `REQ-UI-297`  
**Test Mapping**: `TST-UI-257`  
**Active Sprint**: `2026-41.3`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Domain & Root Cause Summary

In segment elevation profiles (e.g. *Kelterweg-Pfefferburg*, *K1000 Darmsheim Climb*), barometric and GPS sensors record altitude in discrete vertical steps (0.2m–0.4m). At low climbing speeds, multiple GPS points cluster within a few horizontal meters of each other on identical altitude readings.

The previous smoothing implementation in `ElevationProfile.kt:317-333` utilized an unweighted, index-based moving average over a fixed window of indices:
```kotlin
val smoothedAltitudes = pathPointsDownsampled.indices.map { i ->
    // unweighted average over [i - halfWindow .. i + halfWindow]
}
```
Because the index-based filter gave equal weight ($1/N$) to points regardless of horizontal distance, flat plateaus remained completely flat (0% grade, Zone 1 green), while the boundary step between plateaus produced sharp spikes into Zone 4/5 ($8\%-15\%+$ red/black), creating an unnatural zebra stripe artifact.

---

## 2. Implemented Architecture & Mathematical Engine

### 2.1 Pure Mathematical Engine (`ElevationSmoothingMath.kt`)
Created `com.atrainingtracker.trainingtracker.ui.map.ElevationSmoothingMath` featuring:
- **Distance-Weighted Gaussian Kernel**:
  $$w_{ij} = \exp\left(-\frac{(d_j - d_i)^2}{2\sigma^2}\right)$$
  with spatial bandwidth $\sigma = 35.0\text{ m}$ and cutoff radius $3\sigma = 105.0\text{ m}$.
- **Local Linear Kernel Regression (Loess)**:
  Minimizes local weighted linear error:
  $$\sum_{j \in W_i} w_{ij} \cdot \left(a_j - (\alpha_i + \beta_i (d_j - d_i))\right)^2$$
  In the symmetric track interior, $\alpha_i$ reduces to the standard Gaussian weighted average. At asymmetric track boundaries ($d \approx 0$ and $d \approx D_{total}$), the local slope term $\beta_i$ mathematically eliminates boundary sag, peaking, and edge distortion.
- **Linear-Time Bounded Complexity ($O(N)$)**:
  Implements a sliding two-pointer window over monotonically sorted distance coordinates. The inner convolution loop operates over primitive flat `DoubleArray`s with zero JVM object allocations.

### 2.2 Integration into `ElevationProfile.kt`
- Replaced lines 314–333 with:
  ```kotlin
  val smoothedAltitudes = ElevationSmoothingMath.smoothAltitudes(pathPointsDownsampled)
  ```
- Replaced manual grade calculation with `ElevationSmoothingMath.calculateGrade(p1.distance, sAlt1, p2.distance, sAlt2)`.
- Completely purged legacy index-based moving average loop and window constants.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Mathematical & Integration Tests
1. **`ElevationSmoothingMathTest`**:
   - `testStaircaseElimination_onQuantizedSteps`: PASSED (asserts strictly monotonic progression and eliminates 0% flat plateaus on synthetic 0.4m step data).
   - `testBoundaryWeightRenormalization_noSaggingOrPeaking`: PASSED (asserts start $d=0$ and end $d=D_{total}$ match terrain endpoints within 0.1%).
   - `testSummitAndSaddlePreservation`: PASSED (asserts peak height is preserved within 0.5m).
   - `testDegenerateAndEdgeCaseInputs`: PASSED (empty lists, single points, duplicate distances handled safely).
   - `testHighDensityBenchmark_10000Points`: PASSED (10,000 points sliding convolution completes in $< 20\text{ms}$).
2. **`ElevationProfileSmoothingContractTest`**:
   - PASSED (verifies `ElevationProfile.kt` delegates to `ElevationSmoothingMath` and legacy index loops are absent).
3. **`ElevationProfile*` Regression Suite**:
   - `ElevationProfileBoundsTest`: PASSED.
   - `ElevationProfileZoomMathTest`: PASSED.
   - `ElevationProfileGestureContractTest`: PASSED.
   - `ElevationProfileContractTest`: PASSED.

---

## 4. Preserved Invariants

1. **Topographic Bounds**: `calculateElevationBounds` remains untouched, guaranteeing global min/max and scale fidelity.
2. **Scrubbing Precision**: Cursor scrub interpolation on `ElevationProfile` remains locked to exact track coordinates.
3. **Database & Telemetry Isolation**: Zero changes to SQLite tables, raw sensor data, Strava stream uploaders, or workout summaries.
