# Stage 2: Requirement & Test Specification - ATT-7519: Unified 3-Zone RangeSlider & Layout Fixes

**Ticket**: [ATT-7519](https://rainerblind.atlassian.net/browse/ATT-7519)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-TRK-061.4` (*User-Configurable Individual Threshold Overrides & Invariant*)  
**Test Spec ID**: `TST-TRK-061.4` (`TST-TRK-061.4a`–`TST-TRK-061.4e`) | **Branch**: `feature/ATT-7519`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-TRK-061.4)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. `ZonesSettingsActivity.kt` HRV tab SHALL replace separate T1 and T2 sliders with a unified 3-zone colored dual-thumb `RangeSlider` spanning continuous domain `0.30f..1.20f` with thumbs for $T_2$ and $T_1$ enforcing invariant $T_1 \ge T_2 + 0.05f$ (`REQ-TRK-061.4a`).
2. Track segments of the `RangeSlider` SHALL display semantic zone colors conforming to Rule 71: `TTColor.Zone4` (Red, $< T_2$), `TTColor.Zone3` (Orange, $T_2..T_1$), and `TTColor.Zone2` (Green, $> T_1$) (`REQ-TRK-061.4b`).
3. Fine-tuning stepper rows (`-` / `+` with `0.01` step size) SHALL be retained for $T_1$ and $T_2$, keeping `HrvThresholdControlRow` public and backward-compatible (`REQ-TRK-061.4c`).
4. Header reset button and `HrvZoneBandCard` range badges SHALL enforce single-line layout (`maxLines = 1, softWrap = false`, `zoneLabel` with `weight(1f)`), eliminating multi-line text wrapping across all screen widths and font scales (`REQ-TRK-061.4d`).
5. All 5 guidance and helper descriptions in `ZonesSettingsActivity.kt` SHALL be externalized to localized string resources (`hrv_zone_aerobic_base_desc`, `hrv_zone_threshold_transition_desc`, `hrv_zone_anaerobic_desc`, `hrv_t1_aerobic_threshold_desc`, `hrv_t2_anaerobic_threshold_desc`) with 100% 9-language translation parity in `strings_hrv.xml` (`REQ-TRK-061.4e`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (Unified Dual-Thumb Slider)**: Given the HRV tab in `ZoneSettingsScreen`, When viewing threshold controls, Then a single dual-thumb slider displays on scale `0.30..1.20` with Red ($< T_2$), Orange ($T_2..T_1$), and Green ($> T_1$) track coloring.
* **AC2 (Invariant Enforcement)**: Given active $T_1$ and $T_2$, When dragging either thumb or clicking steppers, Then $T_1 \ge T_2 + 0.05f$ is enforced continuously within $[0.30, 1.20]$.
* **AC3 (Layout & Single-Line Hygiene)**: Given German or high-font-scale display, When rendering header row or zone band cards, Then reset action and range badges render on single lines without mid-word breaks.
* **AC4 (9-Locale Translation Parity)**: Given any of the 9 supported locales, When viewing HRV zone cards and threshold descriptions, Then all text resolves from localized string resources with zero hardcoded English literals.

### 1.4 System Invariants
- Zero modification to DataStore keys (`tuning_dfa_a1_threshold_*`, `hrv_dfa_a1_zone_*`) or population defaults ($0.75 / 0.50$).
- Strict invariant maintenance: $T_1 \ge T_2 + 0.05f$.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-TRK-061.4` and `REQ-UI-526` (`ZonesSettingsActivity.kt` HRV tab, `strings_hrv.xml`).
* **Historical Origin & Commit Trace**: `ATT-7317` (`fce6777b`) introduced the 4th tab with two separate sliders as a quick MVP; `ATT-7524` added persistent zone display toggles; `ATT-7521` unified typography to `α1`.
* **Root Reason for Existing Formulation**: Individual sliders were adopted because standard single-value sliders were already used in `TuningFormControls.kt`; dual-thumb coordination was postponed.
* **Preservation of Core Invariants**: Invariant $T_1 \ge T_2 + 0.05f$ is strictly preserved during slider dragging and stepper clicks. DataStore keys (`tuning_dfa_a1_threshold_*`, `hrv_dfa_a1_zone_*`) and population defaults ($0.75 / 0.50$) remain 100% unchanged.

---

## 3. Test Specification (TST-TRK-061.4)

### Test Case 1: Unified 3-Zone Dual-Thumb RangeSlider Contract (`TST-TRK-061.4a`)
* **Scope**: Unit (`HrvDfaA1SettingsContractTest.kt`)
* **Verification**: Verify `ZonesSettingsActivity.kt` declares `RangeSlider` with `0.30f..1.20f` domain and custom track drawing semantic tokens `TTColor.Zone4`, `TTColor.Zone3`, `TTColor.Zone2`.

### Test Case 2: Stepper Control & Invariant Clamping Verification (`TST-TRK-061.4b`)
* **Scope**: Unit (`HrvDfaA1SettingsContractTest.kt`)
* **Verification**: Verify `HrvThresholdControlRow` exists as public composable; verify math enforces $T_1 \ge T_2 + 0.05f$ with $0.01$ step increments across boundary conditions.

### Test Case 3: Layout Anti-Wrapping Contract Verification (`TST-TRK-061.4c`)
* **Scope**: Unit (`HrvDfaA1SettingsContractTest.kt`)
* **Verification**: Verify header Reset button and `HrvZoneBandCard` badge text contain `maxLines = 1` and `softWrap = false`, and `zoneLabel` has `Modifier.weight(1f)`.

### Test Case 4: 9-Language Parity & Zero Hardcoded Strings (`TST-TRK-061.4d`)
* **Scope**: Unit (`TranslationParityTest.kt`, `HrvDfaA1SettingsContractTest.kt`)
* **Verification**: Verify 100% parity across 9 locales for 5 new keys; assert zero hardcoded English guidance/helper text literals in `ZonesSettingsActivity.kt`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-TRK-061.4e`)
* **Scope**: Integration / Unit
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`

---

## 4. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-061.4a` | Unit | `HrvDfaA1SettingsContractTest.kt` | `REQ-TRK-061.4a`, `REQ-TRK-061.4b` | Specified |
| `TST-TRK-061.4b` | Unit | `HrvDfaA1SettingsContractTest.kt` | `REQ-TRK-061.4a`, `REQ-TRK-061.4c` | Specified |
| `TST-TRK-061.4c` | Unit | `HrvDfaA1SettingsContractTest.kt` | `REQ-TRK-061.4d` | Specified |
| `TST-TRK-061.4d` | Unit | `TranslationParityTest.kt` | `REQ-TRK-061.4e` | Specified |
| `TST-TRK-061.4e` | Tier 1 Regr | Targeted test suites | `REQ-PRO-001` | Specified |