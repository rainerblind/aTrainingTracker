# Stage 5: Walkthrough & Verification - ATT-5844: AthleteProfileScreen Filter Pill Layout, Decimal Localization, PMC Card Heights & Time-Range Selector

**Ticket**: [ATT-5844](https://rainerblind.atlassian.net/browse/ATT-5844)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-495` (`REQ-UI-495.1`–`REQ-UI-495.4`)  
**Test Mapping**: `TST-UI-495` (`TST-UI-495.1`–`TST-UI-495.6`)  
**Branch**: `feature/ATT-5844`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
Resolved four presentation, localization, and UX defects identified on the Profile & Zones screen (`screen_02_profil_zonen.png`, `screen_08_profil_pmc.png`):
1. **Proportional Pill Widths**: Replaced equal `weight(1f)` with `Modifier.weight(label.length.coerceIn(3, 9).toFloat())` and tightened `CompactSelectorPill` padding to `3.dp` across *Verlauf* and *Messwert-Historie*, preventing ellipsis truncation of `Gewicht` and `Ruhe-HF`.
2. **Locale-Aware Decimal Formatting**: Updated `AthleteMetricsPlotMath` formatters and `AthleteProfileScreen` Y-axis ticks, estimate dialogs, and PMC stat badges to use `Locale.getDefault()`, resolving `,` vs `.` formatting conflicts with German locale.
3. **Uniform PMC Card Heights**: Applied `Modifier.height(IntrinsicSize.Min)` to both PMC stat `Row` containers and `.fillMaxHeight()` to `PmcStatBadge` per Design Guideline 5.14, equalizing card heights across uneven line counts.
4. **Dedicated PMC Time-Range Selector**: Added a 5-pill time-range selector (`1M | 3M | 6M | 1J | Alle`, defaulting to `6M`) to `PerformanceManagementChartCard` backed by `selectedPmcTimeRange` in `AthleteProfileViewModel`, slicing plotted points post-EWMA calculation.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-495.1` | `TST-UI-495.1` | Contract Test (`AthleteProfileContractAndLocalizationTest`) | **PASSED** | `Verified` |
| `REQ-UI-495.2` | `TST-UI-495.2` | Unit Test (`AthleteMetricsDtoAndMathTest`) | **PASSED** | `Verified` |
| `REQ-UI-495.3` | `TST-UI-495.3` | Contract Test (`AthleteProfileContractAndLocalizationTest`) | **PASSED** | `Verified` |
| `REQ-UI-495.4` | `TST-UI-495.4` | Unit & Contract Test (`AthleteMetricsDtoAndMathTest`, `AthleteProfileContractAndLocalizationTest`) | **PASSED** | `Verified` |
| `REQ-UI-106` | `TST-UI-495.5` | 9-Locale Parity Test (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-495.6` | Tier 1 Targeted Regression (`ui.athleteprofile.*`, `ui.theme.*`, `build.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
* **Command**: `./gradlew assembleDebug testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2 && python3 tools/verify_requirement_governance.py`
* **Output**:
  ```text
  BUILD SUCCESSFUL in 1m 5s
  50 actionable tasks: 5 executed, 45 up-to-date
  PASS: Living documentation master tables match all 9 domain shards with 100% parity.
  Net-new requirement(s) detected: REQ-UI-495. Bypassing archaeology check cleanly.
  ```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **On-Device Status**: No physical USB test device attached (`adb devices` reported no active targets). Live on-device inspection across `AthleteProfileScreen` (Profil & Zonen and PMC) is deferred to Ceremony 2 joint review.
* **Layout Invariant Audit**: Verified Compose layout tree structure, `IntrinsicSize.Min` row bounding, `fillMaxHeight()` constraints, proportional weight clamping, and post-EWMA time-series filtering via automated unit and contract tests.

---

## 5. Invariant & Governance Verification
1. **Design Guideline 5.14 / Rule 67 Invariant**: `Modifier.height(IntrinsicSize.Min)` on enclosing `Row`s protects `.fillMaxHeight()` from `Constraints.Infinity` in scroll containers.
2. **Historical EWMA Integrity**: CTL/ATL/TSB calculation processes complete multi-year workout history; time-range windowing filters strictly post-calculation.
3. **Flexible Input Parsing**: `parseToCanonicalValue` preserves comma/dot-agnostic parsing (`replace(',', '.')`).
4. **Living Documentation Parity**: `REQ-UI-495` and `TST-UI-495` marked `[Verified]` in `req_ui_design_system.md` and `tst_ui_design_system.md`; 100% parity verified in `docs/requirements.md` and `docs/tests.md`.
5. **Governance Verification**: `tools/verify_requirement_governance.py` confirmed 0 errors.
