# Stage 2 Test Specification - ATT-2512

**Ticket**: [ATT-2512](https://atrainingtracker.atlassian.net/browse/ATT-2512)  
**Summary**: Smooth Elevation Profile Using Distance-Weighted Kernel to Eliminate Quantization Staircase on Segments  
**Requirement Mapping**: `REQ-UI-297`  
**Test Mapping**: `TST-UI-257`  
**Active Sprint**: `2026-41.3`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement & Architecture Traceability

| Requirement ID | Summary | Test Case ID | Test Implementation Target |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-297`** | Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation | **`TST-UI-257`** | `ElevationSmoothingMathTest.kt`<br>`ElevationProfileSmoothingContractTest.kt` |

---

## 2. Test Specifications & Verification Methodology

### 2.1 Pure Mathematical Kernel Unit Tests (`ElevationSmoothingMathTest.kt`)

#### TST-UI-257.1: Quantization Staircase Elimination
- **Given**: A synthetic track with discrete vertical steps (e.g. 0.4m elevation increase every 20m, sampled at 2m intervals, representing low-speed climbing with discrete sensor resolution).
- **When**: `ElevationSmoothingMath.smoothAltitudes(points, sigma = 35.0, cutoff = 105.0)` is invoked.
- **Then**:
  - The smoothed altitudes must form a strictly monotonically increasing sequence ($\hat{a}_{i+1} > \hat{a}_i$).
  - Zero consecutive identical altitude values occur (flat 0% plateaus are completely eliminated).
  - The local numerical grade $g_i = \frac{\hat{a}_{i+1} - \hat{a}_i}{d_{i+1} - d_i} \times 100$ remains strictly within $[1.5\%, 2.5\%]$ across the entire step region (true average slope is $2.0\%$), with zero spikes into Zone 4/5 ($> 8\%$).

#### TST-UI-257.2: Exact Boundary Weight Renormalization
- **Given**: A route with initial elevation $a_0 = 320.0\text{ m}$ at $d_0 = 0.0\text{ m}$ and finish elevation $a_N = 540.0\text{ m}$ at $d_N = 2500.0\text{ m}$.
- **When**: Smoothing is applied with local weight renormalization ($\hat{a}_i = \frac{\sum w_{ij} a_j}{\sum w_{ij}}$).
- **Then**:
  - The starting smoothed altitude $\hat{a}_0$ must equal $320.0\text{ m} \pm 0.1\%$ without artificial edge drop/sag.
  - The ending smoothed altitude $\hat{a}_N$ must equal $540.0\text{ m} \pm 0.1\%$ without artificial edge drop/sag.

#### TST-UI-257.3: Topographic Crest & Saddle Preservation
- **Given**: A synthetic hill profile with a sharp summit peak of height $500.0\text{ m}$ over a $150\text{ m}$ crest base.
- **When**: Gaussian smoothing is evaluated with $\sigma = 35.0\text{ m}$.
- **Then**:
  - The peak elevation is preserved within $0.5\text{ m}$ ($> 499.5\text{ m}$).
  - Summit position $d_{peak}$ remains exactly at the true geographic coordinate.

#### TST-UI-257.4: Degenerate and Boundary Inputs
- **Given**: Edge-case inputs:
  1. Empty list (`emptyList()`).
  2. Single point (`listOf(PathPoint(0.0, 0, 400.0, ...))`).
  3. Two identical points at the same distance ($d_1 = d_2$).
  4. Track with zero distance progression.
- **When**: `smoothAltitudes` is invoked.
- **Then**:
  - Returns empty list for empty input.
  - Returns single altitude without modification for single point.
  - Evaluates without `ArithmeticException`, `NaN`, or infinite values.

---

### 2.2 Linear-Time Complexity & Performance Benchmark (`ElevationSmoothingMathTest.kt`)

#### TST-UI-257.5: High-Density 10,000-Point Sliding Window Benchmark
- **Given**: A dense route cloud of $N = 10,000$ points with varying spatial point density (clustered 1m to 20m apart).
- **When**: Executing `ElevationSmoothingMath.smoothAltitudes` on the JVM test runner.
- **Then**:
  - Execution completes in $< 20\text{ ms}$ (comfortably within the 16.6ms UI frame budget).
  - Memory allocation is strictly $O(N)$ with primitive `DoubleArray` output and zero object creation in the sliding convolution loop.

---

### 2.3 Visual & Slope Integration Contract Tests (`ElevationProfileSmoothingContractTest.kt`)

#### TST-UI-257.6: `ElevationProfile.kt` Architectural Integration
- **Given**: The source code of `ElevationProfile.kt`.
- **When**: Auditing the smoothing and slope segments pipeline.
- **Then**:
  - `ElevationProfile.kt` invokes `ElevationSmoothingMath.smoothAltitudes`.
  - Unweighted index-based averaging loop (`for (j in start..end) sum += pathPointsDownsampled[j].altitude`) is completely removed.
  - Segment grade calculation `((sAlt2 - sAlt1) / distDiff) * 100` consumes the distance-weighted smoothed altitudes.

---

## 3. Acceptance Criteria & ASPICE Traceability

1. **Given-When-Then Verification**: All 6 scenarios defined in Section 2 map directly to `REQ-UI-297` clauses 1–4.
2. **Invariant Verification**: Zero regressions on `ElevationProfileBoundsTest`, `ElevationProfileZoomMathTest`, and `ElevationProfileGestureContractTest`.
3. **Full Clean-Room Pass**: 100% pass rate on `./gradlew testDebugUnitTest`.
