# Stage 3: Implementation Plan - ATT-3173

## 1. Architectural Decomposition & Design Principles (SWE.2)
* **Issue**: [ATT-3173](https://atrainingtracker.atlassian.net/browse/ATT-3173): `[Bug] Sole elevation profile on tracking screen without map stretches to half screen instead of using canonical adaptive height`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Requirement**: `REQ-UI-346` (amending `REQ-UI-339` and aligning with `docs/design_guidelines.md` §2.3)
* **Test Specification**: `TST-UI-306`
* **Target Release**: `V4.9.39`

### Architectural Context & Component Boundaries
The live workout tracking cockpit (`SensorGridScreen.kt`) arranges cockpit components in a vertical `Column`:
```
Column (fillMaxSize)
 ├── 1. Sensor Grid Column (fillMaxWidth, verticalScroll, weight(1f, fill = false))
 ├── 2. Map Container Box (if showMap: fillMaxWidth, weight(1f))
 └── 3. Elevation Profile Surface (if showElevationProfile && pathPoints.size >= 2: fillMaxWidth, padding(top = 8.dp))
         └── ElevationProfile(pathPoints, currentDistance, modifier = Modifier.fillMaxWidth())
```

### Layout Dynamics
1. **With Map Enabled (`showMap == true`)**:
   - Sensor Grid Column wraps up to available space via `weight(1f, fill = false)`.
   - Map Container expands to fill remaining space via `weight(1f)`.
   - Elevation Profile renders below the map with its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
2. **With Map Disabled (`showMap == false`)**:
   - Map Container is not composed.
   - Elevation Profile Surface is unweighted, measuring and wrapping `ElevationProfile`'s canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
   - Sensor Grid Column absorbs the remaining available vertical space via `weight(1f, fill = false)`. When 1–2 sensor rows exist, it wraps snugly without stretching. When 4–6 sensor rows exist, it scrolls smoothly and never crowds out or pushes the elevation profile off-screen.

---

## 2. UI Consistency & Living Design Guidelines Audit (Rule 23)
* **Design Guidelines Anchor**: `docs/design_guidelines.md` §2.3 (*Elevation Profile Sizing & Adaptive Height Invariant*).
* **Reference Screen**:
  - `LiveSegmentSheet.kt` & `MapDetailLayout.kt` (lines 324–345):
    ```kotlin
    ElevationProfile(
        pathPoints = path,
        modifier = Modifier.fillMaxWidth()
    )
    ```
* **Theme Tokens & Spacing**:
  - Standard spacing: `8.dp` top padding (`Modifier.padding(top = 8.dp)`).
  - Background: Standard `Surface` with `MaterialTheme.colorScheme.surface`.
  - Zero custom ad-hoc styling.

---

## 3. Atomic Implementation Steps

### Step 1: Remove Weight & Height Expansion from `SensorGridScreen.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  - Replace lines 553–567:
    ```kotlin
    // 3. The Elevation Profile (Below the Map or anchored with canonical adaptive height when map is hidden, REQ-UI-346 / ATT-3173)
    if (state.showElevationProfile && state.pathPoints.size >= 2) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            ElevationProfile(
                pathPoints = state.pathPoints,
                currentDistance = state.pathPoints.lastOrNull()?.distance,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    ```
* **Targeted Verification**:
  - Code compiles cleanly without errors.

### Step 2: Align Contract Tests in `SensorGridScreenLayoutTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Changes**:
  - Update `testElevationProfile_appliesDynamicWeightWhenMapDisabled` -> `testElevationProfile_doesNotApplyWeightWhenMapDisabled`:
    Assert that the ElevationProfile Surface does NOT attach `Modifier.weight(1f)` or `Modifier.weight` when map is disabled.
  - Update `testElevationProfile_passesFillParentHeightAndFillMaxSizeWhenMapDisabled` -> `testElevationProfile_doesNotPassFillParentHeightOrFillMaxSize`:
    Assert that `ElevationProfile` does NOT pass `fillParentHeight` and uses `Modifier.fillMaxWidth()` rather than `fillMaxSize()`.
  - Verify that `testSensorGrid_appliesWeightConstraintWithFillFalse` and `testElevationProfile_guardedByPathPointsSize` remain intact.
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest
  ```

### Step 3: Run Elevation Profile Layout & Math Regression Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt`
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.ElevationProfileLayoutTest
  ```

---

## 4. Invariant Protection & Verification Checklist
- [x] Sensor grid retains `weight(1f, fill = false)` and remains smoothly scrollable.
- [x] Elevation profile renders with canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) matching `calculateElevationProfileHeight(range)`.
- [x] Elevation profile rendering is guarded by `state.showElevationProfile && state.pathPoints.size >= 2`.
- [x] Ambient navigation hints overlay remains anchored at `Alignment.TopCenter`.
- [x] 100% clean-room test pass rate across unit test suite.
