# ATT-3173 Walkthrough: Restore Canonical Adaptive Height for Sole Elevation Profile on Tracking Screen

## 1. Overview
- **Issue**: [ATT-3173](https://atrainingtracker.atlassian.net/browse/ATT-3173): `[Bug] Sole elevation profile on tracking screen without map stretches to half screen instead of using canonical adaptive height`
- **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
- **Requirement**: `REQ-UI-346` (amending `REQ-UI-339` and aligning with Design Guidelines §2.3)
- **Test Specification**: `TST-UI-306`
- **Sprint**: `2026-41.9`
- **Fix Version**: `V4.9.39`
- **Status**: Verified

---

## 2. Problem Addressed
In Sprint 2026-41.8 (`ATT-3107`, `REQ-UI-339`), when the embedded map was disabled on tracking tabs, `Modifier.weight(1f)` and `fillParentHeight = true` / `Modifier.fillMaxSize()` were attached to the `ElevationProfile` surface.

While this prevented the elevation profile from being crushed by sensor tiles, field evaluation revealed an unintended layout defect:
1. **Vertical Oversizing**: On modern tall displays (e.g., Pixel 10), allocating `weight(1f)` together with the sensor grid `weight(1f, fill = false)` partitioned the viewport equally, expanding the elevation canvas to 400dp–600dp (50%+ of screen height).
2. **Visual Inconsistency**: In contrast to all other elevations across the app (`LiveSegmentSheet.kt`, `MapDetailLayout.kt`, and the tracking map tab), which strictly use `ElevationProfile`'s canonical adaptive height calculation ($70\text{dp} \dots 200\text{dp}$ based on total elevation range), the sole elevation profile rendered stretched and disproportionately tall.
3. **Empty Canvas Stretching**: When tracks had modest elevation variation (e.g., 20m–50m gain), the stretched canvas exaggerated minor variations and consumed precious vertical space that should have displayed additional sensor fields.

---

## 3. Architectural Changes & Solution

### 1. Decoupled Elevation Profile Surface Constraints (`SensorGridScreen.kt`)
In `SensorGridScreen.kt`, removed `Modifier.weight(1f)` and `fillParentHeight = !state.mapState.showMap`:
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

### 2. Canonical Adaptive Height Evaluation
- By removing `fillParentHeight = true`, `ElevationProfile` defaults `fillParentHeight = false`.
- The profile internally measures its height dynamically:
  ```kotlin
  val adaptiveHeight = calculateElevationProfileHeight(range) // [70.dp, 200.dp]
  val totalCanvasHeight = if (fillParentHeight) maxHeight else adaptiveHeight
  ```
- The elevation profile is thus cleanly bounded between 70dp and 200dp, matching every other elevation profile view in the app.

### 3. Preserved Flexible Sensor Grid Dynamics
- The sensor grid `Column` retains `Modifier.weight(1f, fill = false)`:
  - When few sensor fields exist (1–2 rows), the sensor grid wraps its content naturally without stretching.
  - When many sensor fields exist (4–6 rows), the sensor grid scrolls smoothly within the remaining space above the elevation profile and never pushes the elevation profile off-screen.
- When `showMap == true`, the map retains primary flexible space (`weight(1f)`), with `ElevationProfile` rendering below it.
- When `state.pathPoints.size < 2`, the elevation profile remains suppressed.

---

## 4. UI Consistency & Living Design Guidelines Compliance (Rule 23)
* **Design Guidelines Anchor**: `docs/design_guidelines.md` §2.3 (*Elevation Profile Sizing & Adaptive Height Invariant*).
* **Reference Screens**:
  - `LiveSegmentSheet.kt`
  - `MapDetailLayout.kt` (lines 324–345)
* **Visual Parity**:
  - `ElevationProfile` uses the exact same adaptive height calculation across all screen configurations.
  - Standard spacing: `8.dp` top padding (`Modifier.padding(top = 8.dp)`).
  - Background: Standard `Surface` with `MaterialTheme.colorScheme.surface`.
  - Zero custom ad-hoc styling.

---

## 5. Test Execution & Verification Results

### 1. Targeted Unit Tests
* **`SensorGridScreenLayoutTest`**:
  - `testSensorGrid_appliesWeightConstraintWithFillFalse`: PASSED
  - `testElevationProfile_doesNotApplyWeightWhenMapDisabled`: PASSED
  - `testElevationProfile_doesNotPassFillParentHeightOrFillMaxSize`: PASSED
  - `testElevationProfile_guardedByPathPointsSize`: PASSED
* **`ElevationProfileLayoutTest`**:
  - `testCalculateElevationProfileHeight_flatRoute_returnsMinimumHeight`: PASSED
  - `testCalculateElevationProfileHeight_rollingHills_returnsProportionalHeight`: PASSED
  - `testCalculateElevationProfileHeight_mountainPass_returnsMaximumHeight`: PASSED
  - `testCalculateElevationProfileHeight_subZeroRange_clampsToMinimum`: PASSED
  - `testAdaptiveHeightIsAppliedWhenFillParentHeightFalse`: PASSED
  - `testFillParentHeightOverridesAdaptiveHeight`: PASSED
  - `testElevationProfileDefaultParameters`: PASSED
  - `testEmptyOrSinglePointPathHandling`: PASSED

### 2. Clean-Room Full Suite Regression
- Executed `./gradlew testDebugUnitTest` across the entire codebase.
- **Result**: `BUILD SUCCESSFUL in 2m 31s`
- **Pass Rate**: 100% (2376+ unit tests passing, 0 failures, 0 regressions).

---

## 6. Living Documentation & Requirement Governance
- `docs/requirements.md`: `REQ-UI-346` updated to `Verified`.
- `docs/tests.md`: `TST-UI-306` updated to `Verified`.
- Requirement governance script verified with 0 violations:
  ```bash
  python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.9
  ```
