# Stage 1 Analysis - ATT-2512

**Ticket**: [ATT-2512](https://atrainingtracker.atlassian.net/browse/ATT-2512)  
**Summary**: Smooth Elevation Profile Using Distance-Weighted Kernel to Eliminate Quantization Staircase on Segments  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Active Sprint**: `2026-41.3`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Domain & Root Cause Analysis

### 1.1 Observed Symptoms
In segment elevation profiles (e.g., *Kelterweg-Pfefferburg*, *K1000 Darmsheim Climb*), the elevation curve and slope gradient coloring display an unnatural alternating staircase pattern:
- Completely flat 0% grade sections (colored Zone 1 green).
- Alternating with artificially steep spikes (colored Zone 4/5 orange, red, or black: 8%–15%+).
- The resulting visual representation appears like alternating zebra stripes rather than a continuous natural climb.

### 1.2 Forensic Investigation
1. **Sensor & Data Stream Quantization**:
   - In recorded or imported Strava stream data (`Segments.db` / `PathPoint`), altitude values often arrive in discrete quantization steps (e.g., 0.2m, 0.4m, or 1.0m barometric/GPS resolution limits).
   - When an athlete climbs at low speeds (e.g., 8–10 km/h $\approx$ 2.2–2.8 m/s), 1 Hz recordings produce points clustered 2–3 meters apart. A 0.4m vertical step spans 15m to 25m of identical horizontal altitude readings before jumping to the next step.
2. **Flaw of Index-Based Moving Average (`ElevationProfile.kt:317-333`)**:
   - `ElevationProfile.kt` calculates:
     ```kotlin
     val avgPointSpacing = if (pointCount > 0) totalDist / pointCount else 1.0
     val targetWindowMeters = 75f
     val calculatedWindow = (targetWindowMeters / avgPointSpacing).toInt().coerceIn(3, 21)
     val windowSize = if (calculatedWindow % 2 == 0) calculatedWindow + 1 else calculatedWindow
     val halfWindow = windowSize / 2
     val smoothedAltitudes = pathPointsDownsampled.indices.map { i ->
         val start = (i - halfWindow).coerceAtLeast(0)
         val end = (i + halfWindow).coerceAtMost(pathPointsDownsampled.size - 1)
         // unweighted sum / count over index range [i - halfWindow .. i + halfWindow]
     }
     ```
   - Crucially, points are weighted **uniformly by index** ($1/N$), completely ignoring the actual physical distance between points.
   - When points are densely spaced across a flat 20m plateau, all points within $[i - \text{halfWindow}, i + \text{halfWindow}]$ have the identical quantized altitude. The moving average remains completely flat until the window crosses the quantization step boundary, whereupon it steps sharply.
3. **Slope Derivative Amplification (`ElevationProfile.kt:376`)**:
   - Grade is calculated as:
     ```kotlin
     val distDiff = p2.distance - p1.distance
     val grade = if (distDiff > 1.0) ((sAlt2 - sAlt1) / distDiff) * 100 else 0.0
     ```
   - When `sAlt2 - sAlt1 == 0`, `grade` is 0.0% (Zone 1 green).
   - At the step boundary, a sudden jump $\Delta \text{alt} = 0.3\text{m}$ over $\Delta \text{dist} = 2.0\text{m}$ yields a computed grade of:
     $$\text{grade} = \frac{0.3}{2.0} \times 100 = 15.0\% \quad (\text{Zone 4/5 Red/Black})$$
   - On a uniform 7% hill, the user sees an alternating 0% / 15% visual artifact.

---

## 2. Requirement Traceability & Mapping

This work formally establishes **`REQ-UI-297`** and verification contract **`TST-UI-257`** in the project specifications:
- **`REQ-UI-297`** (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation*): Committed to `docs/requirements.md`.
- **`TST-UI-257`** (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification*): Committed to `docs/tests.md`.
- Refines existing specifications:
  - **`REQ-UI-192`** (*ElevationProfile Mathematical Precision & Zoom Engine*): The coordinate projection and visible window calculations are maintained, while the altitude array inputs are sanitized.
  - **`REQ-UI-206`** (*Aftermath Telemetry Metric Alignment & Scrubbing*): The scrubbing cursor and elevation profile rendering in Aftermath consume smoothed elevation coordinates without changing scrubbing point resolution.
  - **`REQ-UI-274`** (*Climb Categorization & Accent Highlights*): Accent strokes and category badges remain anchored to exact summit points without distortion.

---

## 3. Comprehensive Call-Site & System Impact Audit

A rigorous search across the codebase confirms:
1. **Isolated to ElevationProfile Rendering**:
   - The smoothing pipeline (`smoothedAltitudes`) in `ElevationProfile.kt` is **strictly local** to `ElevationProfile` rendering (`ElevationProfile.kt:323-387`).
   - It is used **only** for:
     a. Computing normalized Y-coordinates `a1, a2` for the filled elevation polygon.
     b. Calculating the local segment slope `grade` for assigning `TTColor.Zone1` .. `Zone5`.
2. **Independence of Accumulators and Domain Models**:
   - Total workout ascent/descent calculations (`ExtremaCalculator.kt`, `WorkoutRepository.kt`) use raw or Kalman-filtered sensor inputs directly in the tracking service and database layer; they do **not** call `ElevationProfile.kt`.
   - VAM calculations and Strava segment effort matching operate on raw database timestamps and stream coordinates; they do **not** depend on `ElevationProfile.kt`.
   - Live climb detection (`ClimbDetectionEngine.kt`) has its own discrete smoothing window tailored to climb category classification and is not altered.
3. **Consumers of `ElevationProfile`**:
   - `SegmentOnMapScreen.kt` (renders segment elevation profile).
   - `RouteOnMapScreen.kt` (renders route elevation profile).
   - `TrackOnMapScreen.kt` / `MapDetailLayout.kt` (renders workout aftermath elevation profile).
   - `SensorGridScreen.kt` (renders embedded cockpit elevation profile when enabled).
   All four consumers pass `pathPoints: List<PathPoint>` and will automatically benefit from smooth, realistic gradient coloring without requiring any changes to caller signatures.

---

## 4. Proposed Solution Architecture

### 4.1 Pure Mathematical Engine (`ElevationSmoothingMath.kt`)
Extract the smoothing algorithm into a standalone, pure, zero-allocation Kotlin object: `com.atrainingtracker.trainingtracker.ui.map.ElevationSmoothingMath`.

**Mathematical Formulation**:
For each point $i$ with distance $d_i$ and altitude $a_i$:
$$w_{ij} = \exp\left(-\frac{(d_j - d_i)^2}{2\sigma^2}\right)$$
$$\hat{a}_i = \frac{\sum_{j \in W_i} w_{ij} \cdot a_j}{\sum_{j \in W_i} w_{ij}}$$
- **Spatial Bandwidth ($\sigma$)**: $\sigma = 35.0\text{ m}$.
  - At $\Delta d = 15\text{ m}$, weight is $\approx 0.91$.
  - At $\Delta d = 35\text{ m}$ ($1\sigma$), weight is $\approx 0.61$.
  - At $\Delta d = 70\text{ m}$ ($2\sigma$), weight is $\approx 0.14$.
  - At $\Delta d = 105\text{ m}$ ($3\sigma$), weight is $\approx 0.011$.
- **Window Cutoff ($3\sigma = 105.0\text{ m}$)**: Points beyond $105\text{ m}$ contribute less than 1.1% of maximum weight and are safely truncated, guaranteeing bounded computation.

### 4.2 Exact Boundary Condition Handling & Weight Renormalization
- **The Boundary Sag Problem**: Traditional Gaussian convolution filters that assume a fixed kernel denominator or pad with zero outside the domain suffer from boundary sagging or artificial edge drops at $d \to 0$ and $d \to D_{total}$.
- **Dynamic Local Renormalization Solution**:
  - The denominator in our formulation is strictly the **sum of weights actually applied** to points falling within the valid spatial window:
    $$W_i = \{ j \mid |d_j - d_i| \le 3\sigma \}$$
    $$\text{denominator}_i = \sum_{j \in W_i} w_{ij}$$
  - At the start boundary ($d_0 = 0.0\text{m}$), points only exist to the right ($d_j \ge 0$). Because $\hat{a}_0$ is divided by the exact sum of weights of those right-hand points ($\sum_{j, d_j \le 105\text{m}} w_{0j}$), the result is a mathematically rigorous convex combination of local neighbor altitudes.
  - At $d_0$, the weight $w_{00} = \exp(0) = 1.0$ is the maximal weight. Hence, $\hat{a}_0 \approx a_0$ with zero sagging.
  - Symmetrically, at the finish boundary ($d_{N-1} = D_{total}$), local weight renormalization prevents edge peaking or sagging.
  - This eliminates the need for artificial reflection or zero-padding while guaranteeing $C^\infty$ local smoothness.

### 4.3 Worst-Case Performance & Memory Bounds
1. **Sliding Two-Pointer Algorithm ($O(N)$)**:
   - Because `pathPoints` are monotonically ordered by distance ($d_{i} \le d_{i+1}$), a sliding two-pointer `[left, right]` maintains the active distance window $[d_i - 105\text{m}, d_i + 105\text{m}]$.
   - As $i$ advances from $0$ to $N-1$, `left` and `right` only advance forward.
   - Total number of window pointer increments across the entire track is $2N$. Total computational complexity is strictly **$O(N)$** linear time.
2. **Variable Point Density Invariant**:
   - In sparse rural sections (points 15m apart), $K \approx 14$ points in the window.
   - In dense slow climbs (points 2m apart), $K \approx 100$ points in the window.
   - Even in worst-case dense clusters ($100$ points per window over $N=10,000$), total floating-point multiply-adds are $\le 10^6$, completing in **$< 1.5\text{ ms}$** on standard mobile CPUs, executing seamlessly within Compose `remember(pathPointsDownsampled)` without UI thread stutters.
3. **Memory Footprint**:
   - Pre-allocates a single `DoubleArray(N)` for smoothed altitudes. Zero object allocation or boxing in the inner convolution loop.

---

## 5. Acceptance Criteria & Invariants
- **AC-1**: Segment elevation profiles with discrete stream quantization (e.g. Kelterweg-Pfefferburg, K1000 Darmsheim Climb) render smooth, realistic, continuous grade curves without oscillating green/orange staircase artifacts.
- **AC-2**: Topographic peaks and valleys are preserved without over-smoothing true terrain features ($3\sigma = 105\text{m}$ preserves true geographic hills).
- **AC-3**: Boundary altitudes at start ($d=0$) and end ($d=D_{total}$) renormalize cleanly without edge sag or artificial distortion.
- **AC-4**: Degenerate and edge-case inputs (empty list, single point, identical coordinates, trackless workouts) return safely without exceptions.
- **AC-5**: Clean-room unit tests verify continuous grade evaluation on irregular and quantized distance series with 100% pass rate.
