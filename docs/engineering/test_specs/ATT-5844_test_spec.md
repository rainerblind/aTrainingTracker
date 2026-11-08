# Stage 2: Requirement & Test Specification - ATT-5844: AthleteProfileScreen Filter Pill Layout, Decimal Localization, PMC Card Heights & Time-Range Selector

**Ticket**: [ATT-5844](https://rainerblind.atlassian.net/browse/ATT-5844)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5844`  
**Requirement Mapping**: `REQ-UI-495` (`REQ-UI-495.1`–`REQ-UI-495.4`) | **Test Spec ID**: `TST-UI-495` (`TST-UI-495.1`–`TST-UI-495.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-UI-391.5` (*Athlete Profile UI & Parity*) and `REQ-UI-401.3` (*PMC UI & Charting*).
* `Historical Origin & Commit Trace`: Introduced in `ATT-3704` (commit `b660ef43`) and `ATT-3859` (commit `e5d9225e`).
* `Root Reason for Existing Formulation`: Uniform `Modifier.weight(1f)` was a naive placeholder assuming short abbreviations; `Locale.US` was hardcoded during initial prototyping without auditing German decimal conventions; PMC was launched without a dedicated range selector.
* `Preservation of Core Invariants`: Comma/dot input parsing tolerance in `parseToCanonicalValue`, complete historical EWMA calculation prior to time-range slicing, and `Constraints.Infinity` crash prevention per Design Guideline 5.14 via `IntrinsicSize.Min` MUST be preserved.

---

## 1. Requirement Specification (`REQ-UI-495`)

### 1.1 Problem Statement & Rationale
UI audits of the Profile & Zones screen (`screen_02_profil_zonen.png`, `screen_08_profil_pmc.png`) identified 4 defects:
1. **Truncated Filter Pills (`Gewi...`, `Ruhe...`)**: Equal `Modifier.weight(1f)` across variable label lengths truncates `Gewicht` and `Ruhe-HF` on standard mobile screens ($\ge 360\,\text{dp}$).
2. **Hardcoded `Locale.US` Decimals**: Hardcoded `.` decimal formatting (`80.0 kg`, `3.44 W/kg`, `2.4`, `0.8`, `+1.5`, `0.34`) clashes with German locale and adjacent strings (`-0,4/Wo.`).
3. **Unequal PMC Stat Badge Card Heights**: TSB has 3 text lines while CTL/ATL have 2 lines; ACWR has 3 lines while Ramp Rate has 2, creating uneven card heights.
4. **Unfiltered PMC Chart (10-Year Compression)**: `PerformanceManagementChartCard` lacks a time-range selector, compressing 10 years into an unreadable sliver at the right edge.

### 1.2 Functional & Architectural Requirements
1. **Proportional Pill Width Allocation (`REQ-UI-495.1`)**: In `AthleteProfileScreen.kt`, `CompactSelectorPill` items in *Verlauf* and *Messwert-Historie* SHALL be weighted proportionally based on label text length (`Modifier.weight(label.length.coerceIn(3, 9).toFloat())`), with horizontal padding bounded to `3.dp` to prevent ellipsis truncation.
2. **Active Locale Decimal Formatting (`REQ-UI-495.2`)**: In `AthleteMetricsPlotMath.kt` and `AthleteProfileScreen.kt`, format functions and badge values SHALL default to `Locale.getDefault()`, preserving comma/dot-agnostic parsing in `parseToCanonicalValue`.
3. **Uniform PMC Card Heights (`REQ-UI-495.3`)**: PMC stat `Row` containers SHALL specify `Modifier.height(IntrinsicSize.Min)` and `PmcStatBadge` SHALL apply `Modifier.fillMaxHeight()` per Design Guideline 5.14.
4. **Dedicated PMC Chart Time-Range Selector (`REQ-UI-495.4`)**: `PerformanceManagementChartCard` SHALL render a time-range selector (`1M | 3M | 6M | 1J | Alle`, defaulting to `6M`), filtering plotted points post-EWMA calculation.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Non-Truncated Pills)**: *Given* metric/history filter pills on $\ge 360\,\text{dp}$ screens, *When* rendered in German, *Then* `Gewicht` and `Ruhe-HF` display fully without ellipsis (`...`).
* **AC-2 (Decimal Formatting)**: *Given* an active German locale (`de_DE`), *When* metric values and PMC badges render, *Then* numbers display comma decimal separators (e.g. `80,0 kg`, `3,44 W/kg`, `2,4`, `0,8`, `+1,5`, `0,34`).
* **AC-3 (Uniform Card Heights)**: *Given* PMC stat badge rows, *When* rendered, *Then* all cards in Row 1 (CTL, ATL, TSB) and Row 2 (ACWR, Ramp Rate) have matching heights.
* **AC-4 (PMC Time Range)**: *Given* `PerformanceManagementChartCard`, *When* a time range (`1M`, `3M`, `6M`, `1J`, `Alle`) is selected, *Then* plotted points update to that range while historical EWMA calculations remain intact.

---

## 2. Test Specification (`TST-UI-495`) & Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-495.1` | Proportional Pill Layout Contract | `AthleteProfilePillAllocationContractTest.kt` | `REQ-UI-495.1` | Specified |
| `TST-UI-495.2` | Locale Decimal Formatting Contract | `AthleteMetricsPlotMathLocaleContractTest.kt` | `REQ-UI-495.2` | Specified |
| `TST-UI-495.3` | PMC Card Heights Intrinsic Layout | `AthleteProfilePmcLayoutContractTest.kt` | `REQ-UI-495.3` | Specified |
| `TST-UI-495.4` | PMC Time-Range Filtering & EWMA Preservation | `AthleteProfilePmcTimeRangeContractTest.kt` | `REQ-UI-495.4` | Specified |
| `TST-UI-495.5` | 9-Language Localization Parity | `TranslationParityTest.kt` | `REQ-UI-106` | Specified |
| `TST-UI-495.6` | Tier 1 Targeted Unit Test Regression | `ui.athleteprofile.*`, `ThemeColorCentralizationAuditTest` | `REQ-PRO-001` | Specified |
