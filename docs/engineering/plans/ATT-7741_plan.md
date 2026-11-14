# Stage 3: Implementation Plan - ATT-7741: Invert Slider Orientation, Replace M3 Track/Dots, Fix Text Clipping

**Ticket**: [ATT-7741](https://rainerblind.atlassian.net/browse/ATT-7741)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-TRK-061` / `REQ-TRK-061.4`  
**Test Mapping**: `TST-TRK-061` / `TST-TRK-061.4`  
**Branch**: `feat/ATT-7741` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Physical review of ATT-7519 revealed backwards slider orientation (Red-left, Green-right), duplicate track bars with stray M3 orange stop-dots, and German header reset button text truncation ("zurücl"). This plan details the atomic steps to invert the slider, unify the track directly in `RangeSlider`'s `track` slot, and allow graceful text wrapping without truncation.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-061.4` (HRV DFA $\alpha_1$ Training Zones Tab)
* **Test Mapping**: `TST-TRK-061.4` (`HrvDfaA1SettingsContractTest.kt`, `ThemeColorCentralizationAuditTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Mathematical Invariant**: $T_1 \ge T_2 + 0.05f$ strictly enforced across domain `0.30f..1.20f`.
2. **DataStore Invariance**: Keys, schemas, and defaults ($0.75 / 0.50$) remain untouched.
3. **Theme & Localization Parity**: Semantic `TTColor` tokens and 100% 9-language translation parity preserved.

---

## 4. Proposed Architectural Changes

### Component 1: `ZonesSettingsActivity.kt` (`HrvDfaA1RangeSlider`)
* Invert slider domain via coordinate transform $s = 1.50f - \alpha_1$, placing $T_1$ (Green) on the left and $T_2$ (Red) on the right.
* Implement unified 3-zone colored bar directly in `track = { rangeSliderState -> ... }`, clipping to `RoundedCornerShape(4.dp)`.
* Delete redundant external `Box { Canvas { ... } }` track bar above `RangeSlider`.
* Update summary row labels: $T_1$ (Green, left), Übergang (Orange, center), $T_2$ (Red, right).

### Component 2: `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent`)
* Update header reset button: add `weight(1f, fill = false)` and compact padding to `TextButton`.
* Set `maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis` on `Text(reset_to_defaults)`, eliminating clipping in German.

### Component 3: `HrvDfaA1SettingsContractTest.kt`
* Update contract tests to verify inverted orientation, unified `RangeSlider` track slot, zero duplicate track `Box`, and non-clipping multi-line header reset constraints.

### UI Consistency (Rule 23)
* **Reference component**: `CropWorkoutScreen.kt` (RangeSlider) and `ZoneGaugeBar.kt` (Green-left, Red-right).
* **Theme tokens**: `TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`, `MaterialTheme.colorScheme.primary`.
* **Zero new one-off styles**: Fully standard M3 Compose primitives.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Contract Tests in `HrvDfaA1SettingsContractTest.kt`
* Update `testHrvLayout_antiWrappingConstraints` for `maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis`.
* Update `testHrvDfaA1RangeSlider_usesRangeSliderAndSemanticTrackColors` to assert `track = {` slot and left-green/right-red orientation.
* Add assertion verifying zero duplicate track `Box` above `RangeSlider`.

### Step 2: Refactor `ZonesSettingsActivity.kt`
* Refactor `HrvDfaA1RangeSlider` with coordinate inversion $s = 1.50f - \alpha_1$ and unified `track` slot lambda.
* Remove the standalone `Box { Canvas { ... } }` track bar above `RangeSlider`.
* Refactor header reset button with `weight(1f, fill = false)`, `maxLines = 2`, `softWrap = true`, and `overflow = TextOverflow.Ellipsis`.

### Step 3: Targeted Verification & Governance Validation
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and living doc governance check.
* **Rollback**: Clean git branch `feat/ATT-7741` isolation.
