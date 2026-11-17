# Stage 2: Requirement & Test Specification - ATT-8462: EditSensorFieldDialog Logical Order

**Ticket**: [ATT-8462](https://rainerblind.atlassian.net/browse/ATT-8462) | **Sub-task**: `[Req & Test Spec]` | **Epic**: `ATT-5908` (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8462`  
**Requirement Mapping**: `REQ-UI-2782` (`REQ-UI-2782.1`..`REQ-UI-2782.3`) | **Test Spec ID**: `TST-UI-2782` (`TST-UI-2782.1`..`TST-UI-2782.4`)  
**Invariants Preserved**: `REQ-UI-437.3`, `REQ-UI-471.3`, `REQ-UI-395`, `REQ-TRK-059.3/.11`, `REQ-TRK-081.3` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-UI-2782`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. **Group 1 — Messdaten (Data & Signal) (`REQ-UI-2782.1`)**:
   - `EditSensorFieldDialog.kt` SHALL render Group 1 in strict top-to-bottom order: `SensorTypeSelectorSection` -> `Spinner` (*Quelle*, omitted when `deviceList.size == 1 && deviceList[0].first == -1L`) -> `OutlinedButton` (*Filter / Glättung*, rendered strictly when `uiState.selectedSensorType?.filteringPossible == true`).
2. **Group 2 — Kachel-Geometrie (Tile Geometry) (`REQ-UI-2782.2`)**:
   - Immediately below Group 1, `EditSensorFieldDialog.kt` SHALL render Group 2 in strict top-to-bottom order: `Spinner` (*Textgröße*) -> *Relative Breite* (`1x`, `2x`, `3x` `FilterChip`s, rendered strictly when `uiState.isMultiFieldRow == true`).
3. **Group 3 — Visuelle Darstellung & Metrik-Hinweise (`REQ-UI-2782.3`)**:
   - Immediately below Group 2, `EditSensorFieldDialog.kt` SHALL render Group 3 in strict top-to-bottom order: *Anzeigeelemente* (`FilterChip`s *Wert* / *Balken* / *Graph*, gated by `uiState.isBarSupported || uiState.isGraphSupported`) -> expanded Graph options (`uiState.isGraphSupported && uiState.showGraph`: Sparkline Time Horizon `Slider`, Graph Color Editor chips/swatches/`SingleChoiceSegmentedButtonRow`, and Live `SensorSparklineView` preview) -> metric guidance (`POWER_TO_WEIGHT` weight button + `AthleteWeightDialog`, `INTENSITY_FACTOR`/`TSS` FTP `Surface`, `isHrvMetric` info button + `HrvInfoBottomSheet`).
   - All `EditSensorFieldViewModel` callbacks, `ConfigureFilterDialog` overlay wiring, `AppModalBottomSheet(scrollable = true)` scroll safety, spacing (`12.dp` intra-group, `16.dp` inter-group, `8.dp` trailing buttons), and 9-locale parity MUST remain unchanged (zero new string keys).

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `EditSensorFieldDialog` with all groups active (`deviceList.size > 1`, `filteringPossible = true`, `isMultiFieldRow = true`, `isGraphSupported = true`, `showGraph = true`, metric guidance active), *When* rendered, *Then* `boundsInRoot().top` strictly satisfies `Sensortyp < Quelle < Filter < Textgröße < Relative Breite < Anzeigeelemente < Zeitfenster < Graph-Farbe < Vorschau < Metrik-Hinweis`.
* *Given* `filteringPossible == false`, `isMultiFieldRow == false`, single `-1L` `deviceList`, or `showGraph == false`, *When* rendered, *Then* disabled sections are omitted while active controls preserve strict Group 1 -> Group 2 -> Group 3 vertical order.
* *Given* `EditSensorFieldDialog` in Light and Dark themes (`Rule 8.5`), *When* controls in Groups 1–3 are tapped, *Then* ViewModel callbacks fire identically and the layout renders without clipping, overlap, or nested scrolling.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-2782`, complementing `REQ-UI-471.3`, `REQ-UI-437.3`, `REQ-UI-395`, `REQ-TRK-059.3/.11`, and `REQ-TRK-081.3` in `EditSensorFieldDialog.kt`.
* **Historical Origin & Commit Trace**: Controls accumulated sequentially across `ATT-3852` (sensor picker), `ATT-4861`/`ATT-5192` (relative width), `ATT-1294`/`ATT-6690`/`ATT-7314` (sparkline/slider/preview), and `ATT-7760` (`65d2c2d5`, color editor).
* **Root Reason for Existing Formulation**: Each ticket appended its UI block without regrouping earlier geometry/filter controls above the expanded graph editor.
* **Preservation of Core Invariants**: Preserves all conditional guards, `REQ-TRK-081.3` color editor below horizon slider, `REQ-UI-471.3` dialog polish, `AppModalBottomSheet` scroll safety, and 9-locale parity.

---

## 2. Test Specification (`TST-UI-2782`)

1. **`TST-UI-2782.1` (3-Group Vertical Ordering — `EditSensorFieldDialogOrderTest.kt`)**:
   - Render `EditSensorFieldDialog` with Robolectric `createComposeRule()` (multi-device, `filteringPossible = true`, `isMultiFieldRow = true`, `isGraphSupported = true`, `showGraph = true`, guidance metric) and assert `boundsInRoot().top` monotonicity: `Sensortyp < Quelle < Filter < Textgröße < Relative Breite < Anzeigeelemente < Zeitfenster < Graph-Farbe < Vorschau < Metrik-Hinweis`.
2. **`TST-UI-2782.2` (Conditional Guards & Callback Preservation — `EditSensorFieldDialogOrderTest.kt`)**:
   - Verify omission for `filteringPossible = false`, `isMultiFieldRow = false`, single `-1L` device, and `showGraph = false`; verify guidance placement for `POWER_TO_WEIGHT`, `INTENSITY_FACTOR`/`TSS`, and `HRV_DFA_A1`; verify `onConfigureFilterClicked`, `onColWeightChanged`, `onShowGraphChanged`, and `saveChanges` callbacks.
3. **`TST-UI-2782.3` (Rule 8.5 Light/Dark Screenshot Inspection — `EditSensorFieldDialogScreenshotTest.kt`)**:
   - Capture Light & Dark screenshots (`ATT-8462_dialog_light.png`, `ATT-8462_dialog_dark.png`) and inspect via `read_file` for spacing, alignment, wrapping, contrast, and touch ergonomics.
4. **`TST-UI-2782.4` (Targeted Regression & Governance)**:
   - Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabsPolishContractTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"` + `python3 tools/verify_requirement_governance.py`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-2782.1]` | Compose UI | Strict Group 1 -> Group 2 -> Group 3 `boundsInRoot().top` order | `REQ-UI-2782.1`, `REQ-UI-2782.2`, `REQ-UI-2782.3` | Planned |
| `[TST-UI-2782.2]` | Compose UI / Logic | Conditional guards (`filteringPossible`, `isMultiFieldRow`, `-1L`, `showGraph`), guidance, callbacks | `REQ-UI-2782.1`, `REQ-UI-2782.2`, `REQ-UI-2782.3` | Planned |
| `[TST-UI-2782.3]` | Visual (Rule 8.5) | Light & Dark `EditSensorFieldDialog` screenshot inspection | `REQ-UI-2782`, `REQ-UI-471.3` | Planned |
| `[TST-UI-2782.4]` | Regression / Docs | `editsensorfield.*` + contract tests + `verify_requirement_governance.py` | `REQ-UI-2782`, `REQ-PRO-001` | Planned |
