# Stage 1 Analysis: ATT-8462 - EditSensorFieldDialog: Logical Section Ordering

**Ticket**: [ATT-8462](https://rainerblind.atlassian.net/browse/ATT-8462)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8462`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`) | **Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
PO review of `ATT-7760` on Pixel 10 identified that `EditSensorFieldDialog.kt` (`L171–636`) interleaves data-source, geometry, and visual-presentation controls:
`Sensortyp` -> `Quelle` -> `Textgröße` -> `Anzeigeelemente` + expanded Graph block (slider, 8-color swatches, scope toggle, live preview) -> `Relative Breite` -> `Filter / Glättung` -> metric guidance.
When Graph is enabled, the expanded graph editor pushes `Relative Breite` and `Filter / Glättung` down below the fold, separating `Quelle` from `Filter / Glättung` and `Textgröße` from `Relative Breite`.

---

## 2. Forensic Analysis & Verification

### Independent Peer Review & Verification
Audited `EditSensorFieldDialog.kt` (`L142–649`) and `EditSensorFieldViewModel.kt` against the ticket hypothesis:
- **Sensortyp (`L171–183`) & Quelle (`L185–200`)**: Source spinner is hidden when `deviceList.size == 1 && deviceList[0].first == -1L`; otherwise rendered with `Spacer(12.dp)`.
- **Filter / Glättung (`L490–516`)**: Currently at bottom, gated by `uiState.selectedSensorType?.filteringPossible == true`. Opens `ConfigureFilterDialog` (`L640–648`) via `viewModel.onConfigureFilterClicked()`.
- **Textgröße (`L202–212`) & Relative Breite (`L465–488`)**: `Textgröße` (`Spinner`) is separated from `Relative Breite` (`1x / 2x / 3x`, gated by `uiState.isMultiFieldRow`) by the entire `Anzeigeelemente` + Graph block (`L214–463`). Note: `isMultiFieldRow` (`EditSensorFieldViewModel.kt:126–128`) disables `Relative Breite` for single-field full-width rows (*"sofern nicht Vollbreite"*).
- **Anzeigeelemente (`L214–247`), Graph block (`L249–463`), & Metric Guidance (`L518–635`)**: `Anzeigeelemente` (`isBarSupported || isGraphSupported`) and Graph options (`isGraphSupported && showGraph`: Horizon Slider, Color Chips/Swatches, Scope `THIS_TILE`/`ALL_METRIC`, Live `SensorSparklineView` preview) belong together under Visual Representation, followed by metric-specific guidance (`POWER_TO_WEIGHT` weight button + `AthleteWeightDialog`, `INTENSITY_FACTOR`/`TSS` FTP `Surface`, `isHrvMetric` info button + `HrvInfoBottomSheet`).
- **Spacing & Scroll Safety**: `AppModalBottomSheet` provides `scrollable = true` (`design_guidelines.md` §5.8); section spacing uses `12.dp`/`16.dp`/`8.dp` (`§5.2`). Note: `L309` shadows outer `context` (`L148`) cleanly without altering behavior.

### Side-Effect & Cross-Caller Impact Analysis
- **Callers**: `TrackingTabGridContent.kt` (`L199` edit field, `L207` add field) is the sole production caller; `EditSensorFieldDialog` signature, `EditSensorFieldViewModel`, `ConfigureFilterDialog`, `AthleteWeightDialog`, and `HrvInfoBottomSheet` state contracts are unchanged.
- **Test Surface**: `EditSensorFieldDialogScreenshotTest.kt` (`ATT-7760`) renders an isolated `color_picker_container` and does not invoke `EditSensorFieldDialog` directly; `SensorCategoryAndSelectorContractTest.kt`, `TrackingTabsPolishContractTest.kt`, and `ModalBottomSheetDialogsIntegrityTest.kt` use reflection/source checks unaffected by section order; `EditSensorFieldDialogSparklineTest.kt` and `EditSensorFieldViewModelTest.kt` test ViewModel logic only. New Compose UI/screenshot test should verify the full dialog's 3-group vertical ordering (`Rule 8.5`).

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**: Reorder existing UI blocks inside `EditSensorFieldDialog.kt` into 3 thematic groups: (1) **Messdaten** (`Sensortyp -> Quelle -> Filter/Glättung`), (2) **Kachel-Geometrie** (`Textgröße -> Relative Breite`), (3) **Visuelle Darstellung** (`Anzeigeelemente -> Zeitfenster/Graph-Farbe/Umfang/Live-Vorschau -> Metrik-spezifische Hinweise`); preserve all guards (`filteringPossible`, `isMultiFieldRow`, `isBarSupported`, `isGraphSupported`, `showGraph`, metric checks), callbacks, and `AppModalBottomSheet` scroll safety; update/add Compose UI ordering & screenshot test; update living docs.
- **Out-of-Scope**: New strings/headers (unless strictly required — none requested), ViewModel/DB changes, `ConfigureFilterDialog` changes, or `SensorTypeSelectorSection` internal changes. **Complexity**: `1 SP` (single composable reorder, **LOW** risk).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-UI-437.3` (`EditSensorFieldDialog` Relative Width gating), `REQ-UI-471.3` (Dialog polish), `REQ-TRK-059.3/.11` (Display elements, slider, preview), `REQ-TRK-081.3` (Graph color editor) in `req_ui_design_system.md` & `req_tracking_cockpit.md`.
- **Historical Origin & Commit Trace**: Incremental additions (`ATT-3852` `SensorTypeSelectorSection`, `ATT-4861`/`ATT-5192` Relative Width `isMultiFieldRow`, `ATT-1294`/`ATT-6690`/`ATT-7314` Sparkline/Slider/Preview, `ATT-7760` `65d2c2d5` Color Editor) appended controls sequentially without re-grouping.
- **Root Reason for Existing Formulation**: Each requirement specified local visibility/content within its own section; none mandated placing `Relative Breite` or `Filter` below `Anzeigeelemente`/Graph.
- **Preservation of Core Invariants**: All conditional guards (`filteringPossible`, `isMultiFieldRow`, `isBarSupported || isGraphSupported`, `isGraphSupported && showGraph`), `REQ-TRK-081.3` color editor placement below the time-horizon slider, `REQ-UI-471.3` button styling, and 9-locale parity remain 100% preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Amend `REQ-UI-471.3` (or refine cockpit edit-dialog grouping in `req_ui_design_system.md`) to specify the 3-group logical vertical order.
- **Affected Architecture Elements**: `EditSensorFieldDialog.kt` (`app/src/main/java/.../ui/tracking/editsensorfield/EditSensorFieldDialog.kt`). Zero ViewModel, repository, or DB schema changes.
- **Verification Impact & Test Surface**: Add Robolectric Compose UI ordering & Rule 8.5 screenshot verification in `EditSensorFieldDialogScreenshotTest.kt` (or sibling Compose test); run existing `editsensorfield` unit/contract tests (`EditSensorFieldViewModelTest`, `EditSensorFieldDialogSparklineTest`, `SensorCategoryAndSelectorContractTest`, `TrackingTabsPolishContractTest`, `ModalBottomSheetDialogsIntegrityTest`, `EditSensorFieldDialogScreenshotTest`).
- **Risk Assessment & Invariants**: **LOW** risk (1 SP). Pure Compose layout reordering; zero data/DB/API changes; `AppModalBottomSheet` scroll safety and `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`) preserved untouched.

---

## 6. High-Level Solution & System Invariants
1. Reorder `Column` children in `EditSensorFieldDialog.kt`:
   - **Messdaten**: `SensorTypeSelectorSection` -> `Spinner(Quelle)` -> `if (filteringPossible)` Filter button.
   - **Kachel-Geometrie**: `Spinner(Textgröße)` -> `if (isMultiFieldRow)` `Relative Breite` (`1x/2x/3x`).
   - **Visuelle Darstellung**: `Anzeigeelemente` (`Wert/Balken/Graph`) -> `if (isGraphSupported && showGraph)` Graph block (Slider, Color Editor, Scope, Live Preview) -> Metric guidance (`POWER_TO_WEIGHT`, `INTENSITY_FACTOR`/`TSS`, `isHrvMetric`).
2. Normalize `Spacer` heights (`12.dp` intra-group, `16.dp` inter-group, `8.dp` trailing metric buttons).
3. Verify via Compose UI vertical-bounds assertion + Rule 8.5 screenshot inspection.
