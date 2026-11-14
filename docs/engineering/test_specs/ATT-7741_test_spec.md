# Stage 2: Requirement & Test Specification - ATT-7741: Invert Slider Orientation, Replace M3 Track/Dots, Fix Text Clipping

**Ticket**: [ATT-7741](https://rainerblind.atlassian.net/browse/ATT-7741)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-TRK-061` / `REQ-TRK-061.4`  
**Test Spec ID**: `TST-TRK-061` / `TST-TRK-061.4`  
**Branch**: `feat/ATT-7741` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-TRK-061.4 Refinement)

### 1.1 Problem Statement & Rationale
`ZonesSettingsActivity` (HRV tab) has backwards slider orientation (Red on left, Green on right), duplicate tracks with stray M3 orange stop-dots, and German text clipping ("zurücl") in the header reset button.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional / UI | **Verification Method**: Unit & Automated Contract Testing

### 1.3 Functional & Architectural Requirements
1. `HrvDfaA1RangeSlider` SHALL render an inverted dual-thumb slider over continuous domain `0.30f..1.20f` with Green (`TTColor.Zone2`, $\alpha_1 > T_1$) on the left, Orange (`TTColor.Zone3`, $T_2 \le \alpha_1 \le T_1$) in the middle, and Red (`TTColor.Zone4`, $\alpha_1 < T_2$) on the right, matching Preview buttons and SensorFieldView gauge bar.
2. The 3-zone colored bar SHALL be rendered directly in `RangeSlider`'s `track` slot lambda (`track = { rangeSliderState -> ... }`), eliminating duplicate track `Box` containers and M3 orange stop-indicator dots.
3. Summary header above the slider SHALL display $T_1$ in `TTColor.Zone2` (Green) on the left, `Übergang` in `TTColor.Zone3` (Orange) in the center, and $T_2$ in `TTColor.Zone4` (Red) on the right.
4. The Reset to Defaults button row SHALL use `maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis` with compact padding, preventing text truncation across all locales.
5. Invariant $T_1 \ge T_2 + 0.05f$ SHALL be preserved during all thumb drags and stepper clicks.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Slider Track Orientation & Thumbs)**:
  * *Given* `HrvDfaA1RangeSlider` with defaults $T_1 = 0.75f, T_2 = 0.50f$,
  * *When* rendered,
  * *Then* left segment is Green (`TTColor.Zone2`), middle segment is Orange (`TTColor.Zone3`), right segment is Red (`TTColor.Zone4`), and thumbs sit directly on the track without M3 stop dots.
* **Criterion 2 (Non-Clipping German Reset Button)**:
  * *Given* German locale with string "Auf Werkseinstellungen zurücksetzen",
  * *When* displayed on a 392dp screen width,
  * *Then* the string wraps cleanly across up to 2 lines without right-edge glyph truncation ("zurücl").

### 1.5 System Invariants
- Invariant gap $T_1 \ge T_2 + 0.05f$ strictly enforced in DataStore and UI handlers.
- Zero changes to DataStore keys or default values ($0.75 / 0.50$).

---

## 2. Test Specification (TST-TRK-061.4)

### Test Case 1: `testHrvDfaA1RangeSlider_invertedOrientationAndUnifiedTrack` (`TST-TRK-061.4a`)
* **Scope**: Contract Test (`HrvDfaA1SettingsContractTest.kt`)
* **Assertions**:
  - Assert `HrvDfaA1RangeSlider` renders `RangeSlider` with custom `track = {` slot.
  - Assert zero duplicate track `Box` container above `RangeSlider`.
  - Assert left label is $T_1$ with `TTColor.Zone2` and right label is $T_2$ with `TTColor.Zone4`.

### Test Case 2: `testHrvLayout_resetButtonWrappingAndAntiClipping` (`TST-TRK-061.4b`)
* **Scope**: Contract Test (`HrvDfaA1SettingsContractTest.kt`)
* **Assertions**:
  - Assert header reset button defines `maxLines = 2`, `softWrap = true`, and `overflow = TextOverflow.Ellipsis`.

### Test Case 3: Tier 1 Targeted Contract & Theme Suite (`TST-TRK-061.4c`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-061.4a]` | Unit / Contract | `HrvDfaA1RangeSlider` | `REQ-TRK-061.4` | Specified |
| `[TST-TRK-061.4b]` | Unit / Contract | `HrvDfaA1SettingsContent` | `REQ-TRK-061.4` | Specified |
| `[TST-TRK-061.4c]` | Tier 1 Targeted | Full zones & theme audit suite | `REQ-TRK-061` | Specified |
