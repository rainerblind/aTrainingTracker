# Stage 1 Analysis: ATT-7741 - ZonesSettingsActivity (HRV DFA α1): Invert Slider Orientation, Replace M3 Track/Dots, Fix Text Clipping

**Ticket**: [ATT-7741](https://rainerblind.atlassian.net/browse/ATT-7741)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Branch**: `feat/ATT-7741` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Physical on-device review of `ATT-7519` on Pixel 10 revealed three visual and layout defects in `ZonesSettingsActivity` (HRV tab, verified via attached screenshots `hrv_zones_review_1.png` and `hrv_zones_review_2.png`):
1. **Slider Orientation Mismatch**: The dual-thumb slider placed Red (T2: 0.50) on the left and Green (T1: 0.75) on the right, which contradicted the Preview buttons (`Aerob` [Green] | `Übergang` [Orange] | `Anaerob` [Red]) and the `SensorFieldView` gauge bar below.
2. **Duplicate Track & M3 Orange Stop Dots**: A separate custom 3-color bar was drawn above the `RangeSlider`, while the default M3 `RangeSlider` below rendered its default orange active segment and two orange stop-indicator dots at the far left and right edges.
3. **Header Button Clipping in German**: "Auf Werkseinstellungen zurücksetzen" was clipped as "Auf Werkseinstellungen zurücl" because `maxLines = 1, softWrap = false` without proper horizontal constraint and padding caused the string to overflow available width.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Slider Coordinate Mapping)**: The slider used default ascending values `0.30f..1.20f`. Because physiological $\alpha_1$ increases with aerobic relaxation ($\alpha_1 > T_1 \implies$ Green Zone 2) and decreases under anaerobic stress ($\alpha_1 < T_2 \implies$ Red Zone 4), an unreflected numeric range positioned Red at the left and Green at the right.
* **RCA 2 (Track Layer Duplication)**: `RangeSlider` was instantiated without a custom `track` slot lambda, leaving the default M3 `SliderDefaults.Track` active with its tick/stop dots, while a custom `Box { Canvas { ... } }` was drawn as a separate sibling component above.
* **RCA 3 (Row Overflow & Single-Line Constraint)**: In German, the 36-character string `Auf Werkseinstellungen zurücksetzen` plus 15-character title `Trainings Zonen` exceeded the single-line row width on standard mobile widths (392dp/412dp). Enforcing `softWrap = false, maxLines = 1` prevented wrapping and led to glyph truncation.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected live `feat/ATT-7741` code at lines 1164–1200 and 1530–1650 in `ZonesSettingsActivity.kt`.
  * Verified M3 `RangeSlider` provides `track: @Composable (RangeSliderState) -> Unit`, allowing direct unified rendering of the 3-zone colored bar and eliminating the separate `Box` and M3 stop dots.
  * Verified transforming slider domain $s = \text{domainEnd} + \text{domainStart} - \alpha_1$ cleanly maps the left thumb to $T_1$ (Green) and right thumb to $T_2$ (Red) while strictly preserving invariant $T_1 \ge T_2 + 0.05f$.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (2 SP - Standard)**:
  1. Invert `HrvDfaA1RangeSlider` so Green (Zone 2, $\alpha_1 > T_1$) is on the left, Orange (Zone 3, $T_2 \le \alpha_1 \le T_1$) is in the middle, and Red (Zone 4, $\alpha_1 < T_2$) is on the right.
  2. Implement unified 3-zone colored track inside `RangeSlider`'s `track` slot lambda and delete the redundant separate track `Box` and M3 end-dots.
  3. Fix German header button clipping by allowing clean wrapping (`softWrap = true, maxLines = 2, overflow = TextOverflow.Ellipsis`) and compact button padding.
  4. Update contract tests in `HrvDfaA1SettingsContractTest.kt` to enforce unified track and left-green/right-red orientation.
* **Out-of-Scope Non-Goals**:
  * No modification to DataStore schema, default values ($0.75 / 0.50$), or invariant gap ($0.05f$).
  * No changes to other zone tabs (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) or underlying math in `HrvZoneIndicator.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-TRK-061.4` (`ZonesSettingsActivity.kt` HRV DFA $\alpha_1$ tab).
* **Historical Origin & Commit Trace**: `ATT-7519` (`467f7663`) unified dual sliders into a `RangeSlider` but placed Red on the left, added a duplicate track bar above, and applied `maxLines = 1, softWrap = false` to prevent 3-line wrap, accidentally introducing right-edge truncation in German.
* **Root Reason for Existing Formulation**: `ATT-7519` prevented multi-line wrapping when the button was on a crowded row, but over-constrained the text.
* **Preservation of Core Invariants**: Invariant $T_1 \ge T_2 + 0.05f$ is strictly preserved during slider dragging and stepper clicks. DataStore keys and defaults are untouched.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-TRK-061.4`.
* **Affected Architecture Elements**: `ZonesSettingsActivity.kt`.
* **Verification Impact & Test Surface**: `HrvDfaA1SettingsContractTest.kt`, `ThemeColorCentralizationAuditTest`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low Risk. Confined entirely to Compose UI layout within `ZonesSettingsActivity.kt`.

---

## 6. Architectural Strategy & High-Level Solution
* Map $s = 1.50f - \alpha_1$ for range `[0.30f..1.20f]`.
* Start thumb represents $T_1$ (left, Green), end thumb represents $T_2$ (right, Red).
* Replace `track` slot in `RangeSlider` with custom `Canvas` drawing `TTColor.Zone2`, `TTColor.Zone3`, and `TTColor.Zone4`.
* Update header row with `maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. $T_1 \ge T_2 + 0.05f$ clamped at continuous boundaries $[0.30, 1.20]$.
  2. Zero regression in living documentation governance and unit tests.
* **Risk Rating**: **LOW** (strictly UI presentation refactoring in one activity).
