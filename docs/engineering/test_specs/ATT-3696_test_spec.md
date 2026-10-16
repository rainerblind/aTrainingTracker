# Stage 2: Requirement & Test Specification - ATT-3696: Make Zone 6 color (TTColor.Zone6 / R.color.zone_6) distinctly browner and lighter for clear visual separation from Zone 7 (Black)

**Ticket**: [ATT-3696](https://rainerblind.atlassian.net/browse/ATT-3696)  
**Sub-task**: `ATT-3836` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Requirement Mapping**: `REQ-UI-392` (*High-Contrast Warm Earthy Brown Zone 6 Color Token & Zone 7 Visual Separation*)  
**Test Spec ID**: `TST-UI-355`  
**Branch**: `feature/ATT-3696`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (REQ-UI-392)

### 1.1 Problem Statement & Rationale
On physical OLED device screens (Google Pixel 10), `TTColor.Zone6` / `R.color.zone_6` was previously defined as `#4E342E` (Material Brown 800, relative luminance $L \approx 0.036$), yielding only a $1.72:1$ contrast ratio against `TTColor.Zone7` / `R.color.zone_7` (`#000000` Pure Black, $L = 0.0$) and appearing nearly indistinguishable in small legend dots, zone distribution bars, and live zone gauges.

### 1.2 Functional & Architectural Requirements
1. **Warm Earthy Brown Zone 6 Color Token (`REQ-UI-392.1`)**:
   - `TTColor.Zone6` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt` SHALL be defined as `Color(0xFF8B5A2B)` (*Warm Earthy Brown*).
   - `<color name="zone_6">` in `app/src/main/res/values/color.xml` SHALL be defined as `#8B5A2B` (*Warm Earthy Brown*), maintaining strict 1:1 hex parity with `TTColor.Zone6`.
2. **Non-Text Contrast Separation Against Zone 7 (`REQ-UI-392.2`)**:
   - The relative luminance of `TTColor.Zone6` (`#8B5A2B`, $L \approx 0.122$) SHALL provide a WCAG 2.1 AA non-text contrast ratio of at least $3.0:1$ ($3.44:1$) against `TTColor.Zone7` (`#000000`, $L = 0.0$).
3. **Downstream Propagation & Unit Test Synchronization (`REQ-UI-392.3`)**:
   - All downstream consumers of `TTColor.Zone6` and `R.color.zone_6` (`ZoneDistributionCalculator`, `ZoneGaugeMath`, `TelemetryZoneMath`, `ElevationProfile`, `ZonesSettingsActivity`, `ZoneCalculatorDialog`, `TrackingViewModel`) SHALL automatically inherit `#8B5A2B`.
   - `SensorFieldZoneDisplayTest.kt` and `SensorFieldZoneGaugeTest.kt` SHALL assert `Color(0xFF8B5A2B)` for Zone 6 and verify XML/Compose parity and contrast separation against Zone 7.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Token Parity & Hex Value)**:
  * *Given* `TTColor.Zone6` in `Color.kt` and `<color name="zone_6">` in `color.xml`,
  * *When* inspected at runtime and via DOM contract tests,
  * *Then* both resolve to `#8B5A2B` (`Color(0xFF8B5A2B)`).
* **Criterion 2 (Zone 6 vs. Zone 7 Contrast Ratio)**:
  * *Given* `TTColor.Zone6` (`0xFF8B5A2B`) and `TTColor.Zone7` (`0xFF000000`),
  * *When* computing their WCAG 2.1 relative luminance contrast ratio $(L_6 + 0.05) / (L_7 + 0.05)$,
  * *Then* the contrast ratio is $\ge 3.0:1$ ($\approx 3.44:1$).

### 1.4 System Invariants
* Strict 1:1 parity between `Color.kt` and `color.xml` across `Zone1`..`Zone7`.
* Zero changes to `Zone1`..`Zone5` or `Zone7` colors or zone cutoff math.
* Zero new user-facing string resources (existing 9-locale `zone_6_label` and `grade_legend_20_to_25` strings remain 100% intact).
* 100% pass rate on `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (TST-UI-355)

### Test Case 1: `sensorFieldState_zone6AndZone7Colors_arePreservedAndDistinct` (`TST-UI-355.1`)
* **Scope**: Unit & Resource Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldZoneDisplayTest.kt` and `SensorFieldZoneGaugeTest.kt`
* **Action**:
  1. Verify `TTColor.Zone6 == Color(0xFF8B5A2B)` and `TTColor.Zone7 == Color(0xFF000000)`.
  2. Parse `app/src/main/res/values/color.xml` and assert `<color name="zone_6">#8B5A2B</color>`.
  3. Compute WCAG relative luminance of `TTColor.Zone6` and `TTColor.Zone7` and assert contrast ratio $(L_6 + 0.05) / (L_7 + 0.05) \ge 3.0$.
* **Expected Result**: All assertions pass.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-UI-355.2`)
* **Scope**: Localization Parity Test
* **Goal**: Verify `PowerZoneLocalizationTest` (`zone_6_label`, `zone_7_label`) continues to pass across all 9 locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`).
* **Expected Result**: 100% parity.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-355.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-355.1` | Unit & Contract | `SensorFieldZoneDisplayTest`, `SensorFieldZoneGaugeTest` | `REQ-UI-392` | Specified |
| `TST-UI-355.2` | Localization | `PowerZoneLocalizationTest` | `REQ-UI-392`, `REQ-UI-106` | Specified |
| `TST-UI-355.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
