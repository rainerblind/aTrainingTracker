# Stage 3: Implementation Plan - ATT-4878: Stretch Cockpit Sensor Field Card Height and Border

**Ticket**: [ATT-4878](https://rainerblind.atlassian.net/browse/ATT-4878)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-UI-438` (`REQ-UI-438.1`..`REQ-UI-438.4`)  
**Test Mapping**: `TST-UI-438` (`TST-UI-438.1`..`TST-UI-438.4`)  
**Branch**: `feature/ATT-4878`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
In `SensorGridScreen.kt`, multi-column cockpit rows use `Row(modifier = Modifier.height(IntrinsicSize.Min))`. However, the wrapping per-cell `Box` and `SensorFieldView` `Card` lack `.fillMaxHeight()`, and the inner card `Row` hardcodes `.height(IntrinsicSize.Min)`. When fields of differing heights (e.g. `SMALL` next to `HUGE`) share a row, the smaller card floats at intrinsic height with broken borders. This plan details stretching cards to full row height, spanning borders/indicator strips 100%, and vertically centering telemetry readouts.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-438.1` / `TST-UI-438.1`**: `SensorGridScreen.kt` cell `Box` and `SensorFieldView` `.fillMaxHeight()` propagation.
* **`REQ-UI-438.2` / `TST-UI-438.2`**: `SensorFieldView.kt` `Card` and inner `Row` `.fillMaxHeight()`, spanning border and indicator strip across 100% of row height.
* **`REQ-UI-438.3` / `TST-UI-438.3`**: Vertical telemetry centering inside expanded card body with anchored top label and bottom actions.
* **`REQ-UI-438.4` / `TST-UI-438.4`**: Visual regression guard for uniform/single-field rows and 9-language localization check.

---

## 3. System Invariants & Preserved Behavior
1. **Bounded Row Height**: `Row(modifier = Modifier.height(IntrinsicSize.Min))` strictly prevents infinite vertical expansion.
2. **Visual Parity**: Rows with identical `ViewSize` cards maintain existing visual rendering with zero regression.
3. **Typography Invariant**: Font sizes and badge layouts for each `ViewSize` are strictly preserved without unexpected scaling.
4. **Code Lock (Rule 4)**: No production source code modified until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes
* **Grid Layout (`SensorGridScreen.kt`)**:
  * In `SensorGridContent`: update `Box(modifier = Modifier.weight(effectiveWeight).fillMaxHeight())` and pass `modifier = Modifier.fillMaxHeight()` to `SensorFieldView`.
  * In configuration mode: update `Box(modifier = Modifier.weight(effectiveWeight).fillMaxHeight())` and pass `modifier = Modifier.fillMaxHeight()` to `SensorFieldView`.
* **Sensor Field Component (`SensorFieldView.kt`)**:
  * In `Card`: apply `modifier.fillMaxWidth().fillMaxHeight()`.
  * In inner `Row`: replace `.height(IntrinsicSize.Min)` with `.fillMaxHeight()`.
  * In inner `Column`: assign `Modifier.weight(1f).fillMaxHeight()`, anchor label `Row` at top, wrap telemetry value and gauge bars in a centered `Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center)`, and dock configuration `IconButton`s at bottom.

### UI Consistency (Rule 23)
* **Reference component**: `SensorFieldView.kt` and `SensorGridScreen.kt` (ATT-4861, ATT-4841).
* **Reused components**: Material 3 `Card`, `Row`, `Column`, `Box`, `Spacer`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `effectiveBorder`, standard `16.dp` horizontal / `12.dp` vertical padding.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`SensorGridScreen.kt`)**: Add `.fillMaxHeight()` to per-cell `Box` and pass `modifier = Modifier.fillMaxHeight()` to `SensorFieldView` across tracking, preview, and configuration rows.
2. **Step 2 (`SensorFieldView.kt`)**: Ensure `Card` and inner `Row` fill max height, span indicator strips continuously, and center value/gauge content vertically in body `weight(1f)`.
3. **Step 3 (Targeted Layout & Theme Tests)**: Extend `SensorGridScreenLayoutTest.kt` to verify `.fillMaxHeight()` propagation, run `ThemeColorCentralizationAuditTest`, and execute `verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Git branch isolation on `feature/ATT-4878` allows clean revert without affecting `sprint/2026-44.2`.
