# Stage 2: Requirement & Test Specification - ATT-5071: Profile & Zones: Zone visualization preview tile (HR / Power) is vertically clipped/collapsed

**Ticket**: [[ATT-5071]](https://rainerblind.atlassian.net/browse/ATT-5071)  
**Sub-task**: [[ATT-5175]](https://rainerblind.atlassian.net/browse/ATT-5175) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-441` (`REQ-UI-441.1`–`REQ-UI-441.4`)  
**Test Spec ID**: `TST-UI-441` (`TST-UI-441.1`–`TST-UI-441.4`)  
**Branch**: `feature/ATT-5071`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-441)

### 1.1 Problem Statement & Rationale
Under *Zonendarstellung -> Vorschau* on the *Lauf HF*, *Rad HF*, and *Rad Leistung* tabs in `AthleteProfileScreen.kt` (hosted via `ZonesSettingsActivity.kt`), the live preview tile (`SensorFieldView`) at the bottom is vertically collapsed and clipped. Because `SensorFieldView` uses `.fillMaxHeight()` and `.weight(1f)` (from ATT-4878 / `REQ-UI-438`), rendering inside an unconstrained scrollable container (`verticalScroll`, `Constraints.Infinity`) causes `.fillMaxHeight()` to collapse to zero height. Wrapping the preview tile in `Modifier.height(IntrinsicSize.Min)` bounds its height to its minimum intrinsic size (~96 dp), restoring complete visibility of the numeric value, units, zone gauge bar, and vertical indicator strips without visual clipping.

### 1.2 Functional & Architectural Requirements
1. **Preview Container Height Bounding (`REQ-UI-441.1`)**: In `ZonesSettingsActivity.kt` (`SettingsScreenContent`), the container (`Box` / `Surface`) wrapping `SensorFieldView` SHALL apply `Modifier.height(IntrinsicSize.Min)`.
2. **Full-Height Card and Content Visibility (`REQ-UI-441.2`)**: Inside the bounded container, `SensorFieldView` SHALL render fully without clipping: header label anchored at top, centered numeric readout, unit label, zone gauge bar, and vertical indicator strips spanning 100% of card height.
3. **Scrollable Host Stability (`REQ-UI-441.3`)**: `Column(Modifier.verticalScroll(...))` in `ZonesSettingsActivity.kt` SHALL remain smoothly scrollable with zero layout measurement exceptions or constraint crashes.
4. **Cockpit Grid Preservation (`REQ-UI-441.4`)**: `SensorGridScreen.kt` multi-column equal-height behavior and `REQ-UI-438` invariants SHALL remain strictly unaltered.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-438` (*Vertical Card Height and Border Stretching in Cockpit Sensor Grid*) and targets `ZonesSettingsActivity.kt`.
* **Historical Origin & Commit Trace**: `ATT-4878` (commit `12682cfaf66a224127dbd5eb014ed1970f0f38e2`) added `.fillMaxHeight()` and inner `.weight(1f)` to `SensorFieldView` to match the tallest sibling card in cockpit rows.
* **Root Reason for Existing Formulation**: Cockpit rows in `SensorGridScreen.kt` use `Row(Modifier.height(IntrinsicSize.Min))`, but the standalone preview container in `ZonesSettingsActivity.kt` was not updated with `IntrinsicSize.Min`.
* **Preservation of Core Invariants**: `SensorFieldView`'s internal layout contract for `REQ-UI-438` and `SensorGridScreen` equal-height behavior remain strictly untouched; only the caller container in `ZonesSettingsActivity.kt` is bounded.

### 1.4 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Full Visibility in Settings Preview)**: *Given* an athlete viewing *Zonendarstellung -> Vorschau* in `AthleteProfileScreen`, *When* the preview tile is rendered, *Then* the full sensor card with header, numeric value, unit label, zone gauge bar, and vertical indicator strips is completely visible without vertical clipping.
* **Criterion 2 (Intrinsic Height Bounding)**: *Given* `SensorFieldView` embedded inside an unconstrained scrollable column, *When* wrapped in `Modifier.height(IntrinsicSize.Min)`, *Then* Compose measures the card's intrinsic minimum height (~96 dp) and renders all elements with non-zero vertical bounds.
* **Invariants**: Zero infinite height expansion, strict preservation of `IntrinsicSize.Min` on parent containers, zero regression on `SensorGridScreen.kt`, and 100% clean-room test pass rate.

---

## 2. Test Specification (TST-UI-441)

### Test Case 1: `testZonesSettingsPreview_enforcesIntrinsicMinHeight` (`[TST-UI-441.1]`)
* **Scope**: Contract / Layout Test (`ZonesSettingsPreviewLayoutTest.kt`)
* **Action**: Assert `ZonesSettingsActivity.kt` applies `Modifier.height(IntrinsicSize.Min)` on the preview container wrapping `SensorFieldView`.

### Test Case 2: `testSensorFieldView_inIntrinsicMinHeight_rendersNonZeroContent` (`[TST-UI-441.2]`)
* **Scope**: UI Layout Test (`SensorFieldViewLayoutTest.kt` / `ZonesSettingsPreviewLayoutTest.kt`)
* **Action**: Measure `SensorFieldView` inside `Modifier.height(IntrinsicSize.Min)` under unconstrained vertical constraints; assert measured card height $> 0$ and telemetry content is visible.

### Test Case 3: `testSensorGridScreen_multiColumnEqualHeight_preserved` (`[TST-UI-441.3]`)
* **Scope**: Regression Contract Test (`SensorGridScreenEqualHeightTest.kt`)
* **Action**: Assert `SensorGridScreen.kt` cockpit row equal-height stretching (`REQ-UI-438`) remains 100% regression-free.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-441.1]` | Contract | `ZonesSettingsActivity.kt` | `REQ-UI-441.1` | Specified |
| `[TST-UI-441.2]` | Layout | `SensorFieldView.kt` | `REQ-UI-441.2` | Specified |
| `[TST-UI-441.3]` | Regression | `SensorGridScreen.kt` | `REQ-UI-441.4` | Specified |
| `[TST-UI-441.4]` | Targeted Suite | `com.atrainingtracker.trainingtracker.ui.*` | `REQ-PRO-001` | Specified |
