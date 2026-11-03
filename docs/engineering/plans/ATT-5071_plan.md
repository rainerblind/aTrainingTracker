# Stage 3: Implementation Plan - ATT-5071: Profile & Zones Preview Tile Height Bounding

**Ticket**: [ATT-5071](https://rainerblind.atlassian.net/browse/ATT-5071)  
**Sub-task**: [ATT-5176](https://rainerblind.atlassian.net/browse/ATT-5176) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-441` (`REQ-UI-441.1`–`REQ-UI-441.4`)  
**Test Mapping**: `TST-UI-441` (`TST-UI-441.1`–`TST-UI-441.4`)  
**Branch**: `feature/ATT-5071`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
Under *Zonendarstellung -> Vorschau* on the *Lauf HF*, *Rad HF*, and *Rad Leistung* tabs in `AthleteProfileScreen.kt` (hosted via `ZonesSettingsActivity.kt`), the preview sensor tile (`SensorFieldView`) is vertically collapsed and clipped. Because `SensorFieldView` uses `Card(Modifier.fillMaxWidth().fillMaxHeight())` and inner `.weight(1f)` (introduced in ATT-4878 for cockpit row equal-height stretching), placing it inside `SettingsScreenContent`'s `Column(Modifier.verticalScroll)` with unconstrained height (`Constraints.Infinity`) causes `.fillMaxHeight()` to collapse to zero height, cutting off numeric values, units, zone gauge bars, and vertical indicator strips. Wrapping the preview container in `Modifier.height(IntrinsicSize.Min)` bounds its height to its minimum intrinsic size (~96 dp), restoring complete visibility without visual clipping.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-441` (*Zone Preview Sensor Field Height Bounding in Scrollable Settings*)
  * `REQ-UI-441.1`: Preview container height bounding (`height(IntrinsicSize.Min)`).
  * `REQ-UI-441.2`: Full-height card and centered telemetry visibility without clipping.
  * `REQ-UI-441.3`: Scrollable host stability under `verticalScroll`.
  * `REQ-UI-441.4`: Preservation of `SensorGridScreen.kt` multi-column equal-height behavior (`REQ-UI-438`).
* **Test Mapping**: `TST-UI-441` (`TST-UI-441.1`–`TST-UI-441.4`) across `ZonesSettingsPreviewLayoutTest.kt` and `SensorFieldViewLayoutTest.kt`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Layout Regressions in Cockpit Grid**: `SensorGridScreen.kt` multi-column equal-height stretching and `SensorFieldView.kt` internal modifiers remain 100% unaltered.
2. **Scroll Stability**: `Column(Modifier.verticalScroll)` in `ZonesSettingsActivity.kt` scrolls smoothly without layout crashes or constraint violations.
3. **DataStore Invariance**: Zone thresholds, monotonicity rules, and persistence flows remain completely unchanged.
4. **Theme Centralization**: Inherits effective cockpit dark theme and M3 semantic color tokens (`ThemeColorCentralizationAuditTest`).

---

## 4. Proposed Architectural Changes

### Component 1: Preview Tile Container Bounding (`ZonesSettingsActivity.kt`)
* In `SettingsScreenContent`: update the enclosing preview tile container `Box` to apply `Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp)` and pass `modifier = Modifier.fillMaxSize()` to `Surface` and `SensorFieldView`.
* Provides a finite bounded height (~96 dp) during Compose intrinsic measurement, enabling `SensorFieldView`'s `.fillMaxHeight()` and `.weight(1f)` to render fully.

### Component 2: Layout & Contract Test Suite (`ZonesSettingsPreviewLayoutTest.kt`)
* Implement contract test verifying `ZonesSettingsActivity.kt` declares and applies `height(IntrinsicSize.Min)` on the preview container wrapping `SensorFieldView`.
* Verify that `SensorFieldView` inside `height(IntrinsicSize.Min)` preserves non-zero height bounds under unconstrained parent constraints.

### UI Consistency (Rule 23)
* **Reference screen**: `ZonesSettingsActivity.kt` / `SensorGridScreen.kt`
* **Reused components**: `SensorFieldView`, `Surface`, `Box`, `ATrainingTrackerTheme`
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `MaterialTheme.shapes.medium`
* **New one-off styles & justification**: None (strictly bounded layout modifier).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Preview Container Constraints (`ZonesSettingsActivity.kt`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
* Add `.height(IntrinsicSize.Min)` to the preview container `Box` and propagate `.fillMaxSize()` to `Surface` and `SensorFieldView`.

### Step 2: Implement Contract & Layout Verification Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZonesSettingsPreviewLayoutTest.kt`
* Author `testZonesSettingsPreview_enforcesIntrinsicMinHeight` and verify contract adherence.

### Step 3: Targeted Unit & Audit Tests
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted tests, `verify_requirement_governance.py`, and inspect layout behavior.
* **Rollback**: Branch isolation (`feature/ATT-5071`); clean revert of `ZonesSettingsActivity.kt` and test file if needed.
