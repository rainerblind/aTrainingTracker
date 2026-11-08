# Stage 1 Analysis: ATT-5844 - AthleteProfileScreen (Profil & Zonen): Fix truncated filter chips ('Gewi...', 'Ruhe...'), localize decimals, unify PMC card heights & add PMC time-range selector

**Ticket**: [ATT-5844](https://rainerblind.atlassian.net/browse/ATT-5844)  
**Sub-task**: [ATT-6059](https://rainerblind.atlassian.net/browse/ATT-6059) (`[Analysis]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5844`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During UI audits of the **Profile & Zones** screen (`screen_02_profil_zonen.png`, `screen_08_profil_pmc.png`), four UX/localization defects were identified:
1. **Truncated Filter Pills (`Gewi...`, `Ruhe...`)**: In *Verlauf* (`lines 437-459`) and *Messwert-Historie* (`lines 515-537`), each `CompactSelectorPill` is assigned `Modifier.weight(1f)`. Equal division gives each pill only ~45dp on standard displays, truncating `Gewicht` and `Ruhe-HF` while leaving excess empty space in short pills (`CP`, `W'`).
2. **Hardcoded `Locale.US` Decimals**: `AthleteMetricsPlotMath.kt` and `AthleteProfileScreen.kt` hardcode `Locale.US` for values (`80.0 kg`, `3.44 W/kg`, `20.0 kJ`, Y-axis `82.0`, Fitness `2.4`, Fatigue `0.8`, Form `+1.5`, ACWR `0.34`), conflicting with German locale conventions and adjacent localized strings (e.g. `-0,4/Wo.`).
3. **Unequal PMC Stat Badge Card Heights**: In the 3-card PMC row (`CTL | ATL | TSB`), only TSB contains a 3rd text line (`badgeText = Neutral`), making it taller than CTL and ATL. In Row 2, ACWR has 3 lines while Ramp Rate has 2.
4. **Unfiltered PMC Chart (10-Year Compression)**: While *Verlauf* offers a time-range selector (`1M | 3M | 6M | 1J | Alle`), `PerformanceManagementChartCard` has no selector and defaults to plotting all historical years (`10.11.16` to `07.11.26`), compressing recent curves into an unreadable narrow sliver at the right edge.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Pill Width Allocation (`AthleteProfileScreen.kt:456, 534`)**: Assigning `weight(1f)` uniformly across variable-length labels (`2` to `7` chars) forces pills with $\ge 7$ chars into ellipsis in 6-pill rows on `< 411.dp` screens.
2. **Default Parameter in `AthleteMetricsPlotMath.kt:180, 208, 214` & Inlined Call Sites**: Helper functions specify `locale: Locale = Locale.US`, and `AthleteProfileScreen.kt:896, 898, 1235, 1394, 1401, 1408, 1436` explicitly format via `Locale.US` instead of `Locale.getDefault()`.
3. **Missing Intrinsic Constraints (`AthleteProfileScreen.kt:1388, 1430`)**: PMC rows lack `Modifier.height(IntrinsicSize.Min)`, and `PmcStatBadge` lacks `.fillMaxHeight()`, preventing cards from equalizing to the tallest item in the row.
4. **Missing PMC Range State & Selector UI**: `AthleteProfileUiState` and `AthleteProfileViewModel` apply `_selectedTimeRange` across metrics, while `PerformanceManagementChartCard` provides no dedicated selector UI and defaults to `ALL`.
* **Independent Peer Review & Verification**:
  - Visually audited `screen_02_profil_zonen.png` and `screen_08_profil_pmc.png` via `read_file` (`Rule 71`): Confirmed `Gewi...` / `Ruhe...` truncation, decimal format mismatch (`-0,4/Wo.` vs `0.34`), unequal card heights, and 10-year PMC squashing.
  - Audited Design Guideline 5.14 (`Rule 67`): Verified that adding `.fillMaxHeight()` to `PmcStatBadge` inside scrollable `LazyColumn` strictly requires `Modifier.height(IntrinsicSize.Min)` on enclosing `Row`s to avoid `Constraints.Infinity` crash.
  - Audited EWMA math in `PowerPhysiologyCalculator.kt:1005`: Confirmed CTL/ATL/TSB are exponentially weighted across full history; slicing via `filterPmcPointsByTimeRange` must occur post-calculation to preserve boundary accuracy.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Size `CompactSelectorPill` proportionally (`Modifier.weight(label.length.coerceIn(3, 9).toFloat())`) and reduce horizontal padding from `4.dp` to `3.dp` so all labels fit without truncation.
  - Localize decimal rendering in `AthleteMetricsPlotMath` and `AthleteProfileScreen` using `Locale.getDefault()`, preserving comma/dot-agnostic parsing in `parseToCanonicalValue`.
  - Unify card heights in PMC rows via `Modifier.height(IntrinsicSize.Min)` on `Row` and `.fillMaxHeight()` on `PmcStatBadge`.
  - Add dedicated time-range selector (`1M | 3M | 6M | 1J | Alle`) to `PerformanceManagementChartCard`, defaulting to `6M` (`MetricsTimeRange.SIX_MONTHS`) while retaining full-history EWMA computation.
* **Out-of-Scope Non-Goals**:
  - Altering physiological EWMA decay constants ($42\text{d} / 7\text{d}$) or SQLite schema in `AthleteMetrics.db`.
  - Modifying time-range filtering logic of the upper *Verlauf* plot.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-UI-391.5` (*Athlete Profile UI & Parity*) and `REQ-UI-401.3` (*PMC UI & Charting*).
* **Historical Origin & Commit Trace**: `ATT-3704` (commit `b660ef43`) and `ATT-3859` (commit `e5d9225e`).
* **Root Reason for Existing Formulation**: `weight(1f)` was a naive layout placeholder; `Locale.US` was used during initial prototyping; PMC was initially deployed without dedicated range selector.
* **Preservation of Core Invariants**: `parseToCanonicalValue` retains comma/dot tolerance (`replace(',', '.')`); EWMA continues aggregating over complete history before time-range windowing.

---

## 5. Architectural Strategy & High-Level Solution
* **Proportional Pill Sizing**:
  - In `AthleteProfileScreen.kt`, replace `modifier = Modifier.weight(1f)` in metric and history rows with `modifier = Modifier.weight(label.length.coerceIn(3, 9).toFloat())`, and tighten `CompactSelectorPill` padding to `3.dp`.
* **Locale Decimal Formatting**:
  - In `AthleteMetricsPlotMath.kt`, update default parameters to `locale: Locale = Locale.getDefault()`.
  - In `AthleteProfileScreen.kt`, replace hardcoded `Locale.US` with `Locale.getDefault()` in chart tick formatters and stat card values.
* **Uniform Card Heights**:
  - Add `Modifier.height(IntrinsicSize.Min)` to both PMC stat `Row` containers and `.fillMaxHeight()` to `PmcStatBadge` per Design Guideline 5.14.
* **PMC Time-Range Selector**:
  - In `AthleteProfileViewModel`, add `_selectedPmcTimeRange = MutableStateFlow(MetricsTimeRange.SIX_MONTHS)` and `selectPmcTimeRange(range)`.
  - In `AthleteProfileUiState`, add `selectedPmcTimeRange: MetricsTimeRange = MetricsTimeRange.SIX_MONTHS`.
  - In `PerformanceManagementChartCard`, render `Row` with 5 selector pills (`1M`, `3M`, `6M`, `1J`, `Alle`) driving `onSelectTimeRange`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `Constraints.Infinity` crash prevention per Design Guideline 5.14 via `IntrinsicSize.Min`.
  2. Complete historical EWMA integrity for CTL/ATL/TSB (slicing post-EWMA).
  3. Metric input parsing supports both `,` and `.` decimal points without validation failure.
* **Risk Rating**: **LOW** (Targeted Compose layout and formatting refinement; zero DB or algorithmic changes).
