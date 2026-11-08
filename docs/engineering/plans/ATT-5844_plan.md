# Stage 3: Implementation Plan - ATT-5844: AthleteProfileScreen Filter Pill Layout, Decimal Localization, PMC Card Heights & Time-Range Selector

**Ticket**: [ATT-5844](https://rainerblind.atlassian.net/browse/ATT-5844)  
**Sub-task**: [ATT-6061](https://rainerblind.atlassian.net/browse/ATT-6061) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5844`  
**Requirement Mapping**: `REQ-UI-495` (`REQ-UI-495.1`–`REQ-UI-495.4`)  
**Test Mapping**: `TST-UI-495` (`TST-UI-495.1`–`TST-UI-495.6`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
Audits of the Profile & Zones screen (`screen_02_profil_zonen.png`, `screen_08_profil_pmc.png`) revealed four defects:
1. Equal `Modifier.weight(1f)` across variable-length pill labels truncates `Gewicht` and `Ruhe-HF` to `Gewi...` and `Ruhe...` in *Verlauf* and *Messwert-Historie*.
2. Hardcoded `Locale.US` decimals (`80.0 kg`, `3.44 W/kg`, `2.4`, `0.8`, `+1.5`, `0.34`) conflict with German locale conventions and adjacent `-0,4/Wo.`.
3. Unequal card heights in PMC rows (TSB has 3 lines, CTL/ATL have 2; ACWR has 3 lines, Ramp Rate has 2).
4. The PMC chart lacks a time-range selector and squeezes 10 full years (`2016–2026`) into an unreadable sliver.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-495` (*AthleteProfileScreen Layout, Localization, PMC Heights & Time-Range Selector*)
  * `REQ-UI-495.1`: Proportional pill weighting (`Modifier.weight(label.length.coerceIn(3, 9).toFloat())`) and bounded `3.dp` padding in `CompactSelectorPill`.
  * `REQ-UI-495.2`: Active locale decimal formatting defaulting to `Locale.getDefault()`, preserving comma/dot parsing in `parseToCanonicalValue`.
  * `REQ-UI-495.3`: Uniform PMC stat badge card heights via `Modifier.height(IntrinsicSize.Min)` on `Row` and `.fillMaxHeight()` on `PmcStatBadge` (Design Guideline 5.14).
  * `REQ-UI-495.4`: Dedicated PMC time-range selector (`1M | 3M | 6M | 1J | Alle`, defaulting to `6M`) with post-EWMA slicing.
* **Test Mapping**: `TST-UI-495` (*AthleteProfileScreen Contract & Localization Test Suite*)
  * `TST-UI-495.1`–`TST-UI-495.4`: Contract and layout unit tests in `AthleteProfileContractAndLocalizationTest.kt` and `AthleteMetricsDtoAndMathTest.kt`.
  * `TST-UI-495.5`: 9-language localization parity in `TranslationParityTest.kt`.
  * `TST-UI-495.6`: Tier 1 targeted regression suite (`ui.athleteprofile.*`, `ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Design Guideline 5.14 / Rule 67 Invariant**: Enclosing `Row` containers MUST apply `Modifier.height(IntrinsicSize.Min)` so `.fillMaxHeight()` never encounters `Constraints.Infinity` in scrollable `LazyColumn`.
2. **Historical EWMA Integrity**: CTL/ATL/TSB are computed over complete workout history; time-range windowing strictly filters post-calculation.
3. **Flexible Input Parsing**: `parseToCanonicalValue` preserves `.replace(',', '.')` to accept both `,` and `.` decimal user inputs.

---

## 4. Proposed Architectural Changes & UI Consistency
* **`AthleteMetricsPlotMath.kt`**:
  * Set `locale: Locale = Locale.getDefault()` on `formatWeightValue`, `formatWPrimeValue`, and `formatPowerToWeightValue`.
* **`AthleteProfileViewModel.kt`**:
  * Add `_selectedPmcTimeRange = MutableStateFlow(MetricsTimeRange.SIX_MONTHS)` and `selectPmcTimeRange(range)`.
  * Expose `selectedPmcTimeRange: MetricsTimeRange` in `AthleteProfileUiState`.
  * Filter `pmcSummary.dailyPoints` using `selectedPmcTimeRange`.
* **`AthleteProfileScreen.kt`**:
  * In *Verlauf* and *Messwert-Historie*, allocate pill weight as `Modifier.weight(label.length.coerceIn(3, 9).toFloat())`.
  * In `CompactSelectorPill`, set horizontal padding to `3.dp`.
  * Replace `Locale.US` with `Locale.getDefault()` across chart Y-axis labels and PMC stat badge formatters.
  * In `PerformanceManagementChartCard`, wrap stat badge rows in `Modifier.height(IntrinsicSize.Min)` and apply `.fillMaxHeight()` to `PmcStatBadge`.
  * Render a time-range selector row (`1M | 3M | 6M | 1J | Alle`) in `PerformanceManagementChartCard`.
* **UI Consistency**:
  * Reference screen: `AthleteProfileScreen.kt`.
  * Reused tokens: `CompactSelectorPill`, `PmcStatBadge`, `Card`, `Row`, `RoundedCornerShape(8.dp)`, `TTColor.Pmc*`. No one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Math Formatting & ViewModel State (`AthleteMetricsPlotMath.kt`, `AthleteProfileViewModel.kt`)**:
   - Update `Locale.getDefault()` defaults in `AthleteMetricsPlotMath.kt`.
   - Add `selectedPmcTimeRange` flow and state in `AthleteProfileViewModel.kt`.
2. **Step 2: Profile Screen Layout & Time-Range Selector (`AthleteProfileScreen.kt`)**:
   - Apply proportional weights to pills, adjust `CompactSelectorPill` padding to `3.dp`, and switch decimal formatting to `Locale.getDefault()`.
   - Enforce `IntrinsicSize.Min` and `.fillMaxHeight()` on PMC badge rows.
   - Add time-range selector row to `PerformanceManagementChartCard`.
3. **Step 3: Unit & Contract Tests**:
   - Update and extend tests in `AthleteMetricsDtoAndMathTest.kt` and `AthleteProfileContractAndLocalizationTest.kt`.
4. **Step 4: Verification**:
   - Execute: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.
   - Execute: `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and Theme audit in Stage 4; full ADB on-device walkthrough in Stage 5.
* **Rollback Plan**: Atomic commit on `feature/ATT-5844`; clean revert via `git reset --hard` if tests fail.
