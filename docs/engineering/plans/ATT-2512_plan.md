# Stage 3 Implementation Plan - ATT-2512

**Ticket**: [ATT-2512](https://atrainingtracker.atlassian.net/browse/ATT-2512)  
**Summary**: Smooth Elevation Profile Using Distance-Weighted Kernel to Eliminate Quantization Staircase on Segments  
**Requirement Mapping**: `REQ-UI-297`  
**Test Mapping**: `TST-UI-257`  
**Active Sprint**: `2026-41.3`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Technical Architecture & SWE.2 Design

### 1.1 Structural Decomposition
```
app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/
├── ElevationSmoothingMath.kt             [NEW: Pure Gaussian kernel math engine]
└── ElevationProfile.kt                   [MODIFIED: Wire smoothAltitudes, remove index loop]

app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/
├── ElevationSmoothingMathTest.kt         [NEW: Kernel, boundary, and benchmark unit tests]
└── ElevationProfileSmoothingContractTest.kt [NEW: Architectural integration contract tests]
```

### 1.2 Mathematical Specifications & Invariants
1. **Gaussian Distance Kernel**:
   $$w_{ij} = \exp\left(-\frac{(d_j - d_i)^2}{2\sigma^2}\right)$$
   with $\sigma = 35.0\text{ m}$ and cutoff radius $3\sigma = 105.0\text{ m}$.
2. **Local Weight Renormalization**:
   $$\hat{a}_i = \frac{\sum_{j \in W_i} w_{ij} \cdot a_j}{\sum_{j \in W_i} w_{ij}}$$
   Denominator is dynamically accumulated over actual points present in $W_i = \{ j \mid |d_j - d_i| \le 105.0\text{ m} \}$.
   Start ($d=0$) and end ($d=D_{total}$) boundary points evaluate without edge sag or artificial distortion.
3. **Linear Sliding Two-Pointer Window ($O(N)$)**:
   Maintains `left` and `right` indices such that $d_i - d_{left} \le 105.0$ and $d_{right} - d_i \le 105.0$. Pointers only advance forward monotonically.
4. **Zero Inner-Loop Allocation**:
   Pre-allocates a single `DoubleArray(size)`. Inner loop executes primitive `Double` arithmetic without object instantiation.

---

## 2. Step-by-Step Implementation Work Breakdown

### Phase 1: Pure Mathematical Engine (`ElevationSmoothingMath.kt`)
- Create `com.atrainingtracker.trainingtracker.ui.map.ElevationSmoothingMath`.
- Define constants:
  - `DEFAULT_SIGMA = 35.0`
  - `DEFAULT_CUTOFF = 105.0`
- Implement:
  ```kotlin
  fun smoothAltitudes(
      pathPoints: List<PathPoint>,
      sigma: Double = DEFAULT_SIGMA,
      cutoff: Double = DEFAULT_CUTOFF
  ): DoubleArray
  ```
- Implement defensive handling for empty lists, single elements, and non-positive distances.

### Phase 2: Unit Testing (`ElevationSmoothingMathTest.kt`)
- Implement TST-UI-257.1: Synthetic step data (0.4m step per 20m) stair-step elimination.
- Implement TST-UI-257.2: Exact boundary weight renormalization at $d=0$ and $d=D_{total}$ ($\pm 0.1\%$).
- Implement TST-UI-257.3: Summit crest and saddle preservation ($\pm 0.5\text{m}$).
- Implement TST-UI-257.4: Degenerate and boundary inputs.
- Implement TST-UI-257.5: 10,000-point performance benchmark ($< 20\text{ms}$).

### Phase 3: Integration into `ElevationProfile.kt`
- In `ElevationProfile.kt`, delete lines 316–333 (the obsolete `targetWindowMeters`, `avgPointSpacing`, `calculatedWindow`, `halfWindow`, and index loop).
- Replace with:
  ```kotlin
  val smoothedAltitudes = remember(pathPointsDownsampled) {
      ElevationSmoothingMath.smoothAltitudes(pathPointsDownsampled)
  }
  ```
- In the `segments` generation loop (lines 368–387), read `smoothedAltitudes[i]` and `smoothedAltitudes[i + 1]`.

### Phase 4: Architectural Contract Tests (`ElevationProfileSmoothingContractTest.kt`)
- Verify `ElevationProfile.kt` invokes `ElevationSmoothingMath.smoothAltitudes`.
- Verify the legacy index-based moving average loop is completely eliminated.

### Phase 5: Verification & Regression Execution
- Execute targeted test suite via Gradle.
- Run full regression suite (`./gradlew testDebugUnitTest`).

---

## 3. Preserved Invariants & Safety Measures

1. **Topographic Bounds**: `calculateElevationBounds` remains unchanged, preserving exact global min, max, and elevation range.
2. **Scrubbing & Zoom Precision**: `currentDistance` scrubbing and marker positioning continue to interpolate between exact raw or smoothed path points without distortion.
3. **Database & Telemetry Isolation**: Zero database or domain entity modifications (`Segments.db`, `WorkoutSummaries.db`, `BANALService`).
