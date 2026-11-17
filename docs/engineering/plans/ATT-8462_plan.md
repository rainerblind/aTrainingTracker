# Stage 3: Implementation Plan - ATT-8462: EditSensorFieldDialog Logical Section Ordering

**Ticket**: [ATT-8462](https://rainerblind.atlassian.net/browse/ATT-8462)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8462`  
**Requirement Mapping**: `REQ-UI-2782` (`REQ-UI-2782.1`..`REQ-UI-2782.3`)  
**Test Mapping**: `TST-UI-2782` (`TST-UI-2782.1`..`TST-UI-2782.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Problem Description & Architectural Context
`EditSensorFieldDialog.kt` (`L171–636`) currently interleaves data-source, tile-geometry, and visual-presentation blocks, pushing `Relative Breite` and `Filter / Glättung` below the expanded Sparkline Graph editor. Stage 4 regroups the dialog `Column` into three cohesive vertical tiers (**Messdaten** -> **Kachel-Geometrie** -> **Visuelle Darstellung**) without altering `EditSensorFieldViewModel`, repositories, SQLite schemas, or string resources.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-2782.1`** -> **`TST-UI-2782.1`, `TST-UI-2782.2`**: Group 1 (**Messdaten**) order: `SensorTypeSelectorSection` -> `Spinner` (*Quelle*, omitted for single `-1L` device) -> `OutlinedButton` (*Filter / Glättung*, gated by `filteringPossible == true`).
* **`REQ-UI-2782.2`** -> **`TST-UI-2782.1`, `TST-UI-2782.2`**: Group 2 (**Kachel-Geometrie**) order: `Spinner` (*Textgröße*) -> *Relative Breite* (`1x/2x/3x`, gated by `uiState.isMultiFieldRow == true`).
* **`REQ-UI-2782.3`** -> **`TST-UI-2782.1`..`TST-UI-2782.3`**: Group 3 (**Visuelle Darstellung**) order: *Anzeigeelemente* (`Wert/Balken/Graph`) -> expanded Graph block (Horizon `Slider`, Color Chips/Swatches, `SingleChoiceSegmentedButtonRow`, Live `SensorSparklineView` preview) -> metric guidance (`POWER_TO_WEIGHT`, `INTENSITY_FACTOR`/`TSS`, `isHrvMetric`), plus Light/Dark screenshot verification (`Rule 8.5`).

---

## 3. UI Consistency & System Invariants (`docs/design_guidelines.md`)
1. **UI Consistency**: Reuses existing M3 `OutlinedCard`/`SensorTypeSelectorSection`, `ExposedDropdownMenuBox` (`Spinner`), `OutlinedButton`, `FilterChip` (`1x/2x/3x`, `Wert/Balken/Graph`), `Slider`, and `SingleChoiceSegmentedButtonRow(icon = {})` (`§1.1`, `§1.4`). Spacing strictly adheres to `§5.2` (`8.dp`, `12.dp` intra-group, `16.dp` inter-group). Zero nested `verticalScroll`/`LazyColumn` inside `AppModalBottomSheet(scrollable = true)` (`§5.8`).
2. **State & Guard Invariants**: All conditional guards (`deviceList`, `filteringPossible`, `isMultiFieldRow`, `isBarSupported || isGraphSupported`, `isGraphSupported && showGraph`, metric checks), `EditSensorFieldViewModel` callbacks, and `ConfigureFilterDialog`/`AthleteWeightDialog`/`HrvInfoBottomSheet` overlays remain untouched.
3. **Localization & Persistence**: Zero new string keys (9-locale parity preserved); zero ViewModel, Repository, or SQLite DB modifications.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Reorder `EditSensorFieldDialog.kt` into 3 Logical Groups (`REQ-UI-2782.1`..`REQ-UI-2782.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt` (`L165–636`)
* **Changes**:
  - **Group 1 — Messdaten**: `SensorTypeSelectorSection` (`Spacer(12.dp)`) -> `Spinner` *Quelle* (when `!(deviceList.size == 1 && deviceList[0].first == -1L)`, `Spacer(12.dp)`) -> `OutlinedButton` *Filter / Glättung* (moved from `L490–516`, gated by `uiState.selectedSensorType?.filteringPossible == true`, `Spacer(12.dp)`). Add `Spacer(4.dp)` (or `16.dp` group transition) before Group 2.
  - **Group 2 — Kachel-Geometrie**: `Spinner` *Textgröße* (`Spacer(12.dp)` if `isMultiFieldRow` else `Spacer(16.dp)`) -> *Relative Breite* (`1x`/`2x`/`3x` `FilterChip` row moved from `L465–488`, gated by `uiState.isMultiFieldRow`, `Spacer(16.dp)`).
  - **Group 3 — Visuelle Darstellung**: *Anzeigeelemente* (`L214–247`) -> expanded Graph block (`L249–463`: Horizon `Slider`, Color Editor, Scope, Live Preview) -> Metric guidance (`POWER_TO_WEIGHT`, `INTENSITY_FACTOR`/`TSS`, `isHrvMetric`, `L518–635`).
  - Clean up shadowed `val context = LocalContext.current` at `L309` (outer `context` from `L148` already in scope).

### Step 2: Compose Vertical-Order, Guard & Callback Unit Tests (`TST-UI-2782.1`, `TST-UI-2782.2`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialogOrderTest.kt` (new Robolectric Compose test)
* **Changes**:
  - Render `EditSensorFieldDialog` with mocked `EditSensorFieldViewModel` (`MutableStateFlow<EditDialogUiState>`).
  - Assert strict `boundsInRoot().top` monotonicity (`// Traces to: REQ-UI-2782`): `Sensortyp < Quelle < Filter < Textgröße < Relative Breite < Anzeigeelemente < Zeitfenster < Graph-Farbe < Vorschau < Metrik-Hinweis`.
  - Verify omission for `filteringPossible = false`, `isMultiFieldRow = false`, single `-1L` device, `showGraph = false`; verify guidance (`POWER_TO_WEIGHT`, `INTENSITY_FACTOR`, `HRV_DFA_A1`) and callbacks (`onConfigureFilterClicked`, `onColWeightChanged`, `onShowGraphChanged`, `saveChanges`).

### Step 3: Light/Dark Visual Screenshot Verification (`TST-UI-2782.3`, Rule 8.5)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialogScreenshotTest.kt`
* **Changes**:
  - Keep existing `testCaptureColorEditorScreenshot()` intact; add `testCaptureReorderedDialogScreenshots_LightAndDark()` rendering `EditSensorFieldDialog` content (all 3 groups active) in Light & Dark themes, writing `docs/engineering/walkthroughs/img/ATT-8462_dialog_light.png` & `ATT-8462_dialog_dark.png`, and inspect both via `read_file`.

### Step 4: Targeted Regression & Governance Verification (`TST-UI-2782.4`)
* **Test Command**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabsPolishContractTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
* **Governance**: `python3 tools/verify_requirement_governance.py`

---

## 5. Verification & Rollback Plan
* **Verification**: 100% pass rate on targeted `editsensorfield.*` + contract tests, Rule 8.5 Light/Dark visual inspection, and `verify_requirement_governance.py`.
* **Rollback**: Single-file Compose UI reorder on `feature/ATT-8462`; trivially revertible with zero schema/data impact.
