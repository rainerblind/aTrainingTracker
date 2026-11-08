# Stage 1 Analysis: ATT-5836 - PeriodSummary (Zeiträume): Localize date range header ('Nov. 2 - Nov. 8') and chart decimal labels ('1.7h') according to active locale

**Ticket**: [ATT-5836](https://rainerblind.atlassian.net/browse/ATT-5836)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Zeiträume / Periods & Stats*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5836`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
Visual inspection of `docs/attachments/audit_03_zeitraeume.png` (`Rule 71`/`Rule 72`) under German locale (`de_DE`) revealed two localization defects in the Periods view (`PeriodsScreen`, `PeriodSummaryCard`, `PeriodBarGraph`):
1. **Hardcoded US Month-First Date Range Header**: Weekly period card headers display `"Nov. 2 - Nov. 8"` instead of locale-appropriate day-first formatting (`2. Nov. – 8. Nov.` in German, `Nov 2 – Nov 8` in US English).
2. **Hardcoded `Locale.US` Decimal Point in Bar Chart Labels**: Top value labels on `PeriodBarGraph` display hardcoded US decimal points (`1.7h`, `5.7h`, `5.5h`, `5.3h`, `%.1fkm`, `%.1fmi`) instead of the active locale's decimal separator (`1,7h`, `5,7h`, `5,4km` in German).

---

## 2. Root Cause Analysis & Visual Inspection
1. **Date Range Pattern Hardcoding (`PeriodsRepository.kt:96, 566, 766`)**:
   - `rangeDateFormatter = DateTimeFormatter.ofPattern("MMM d")` hardcodes US month-day order without locale parameters.
   - Lines 566 and 766 concatenate `"${weekStart.format(rangeDateFormatter)} - ${weekEnd.format(rangeDateFormatter)}"`.
   - `periodDateRange` is stored in SQLite (`PeriodSummariesDatabaseManager.kt:308, 338`); reading cached rows locks in historical formatting on locale change unless dynamically formatted on read.
2. **Bar Value Decimal Hardcoding (`PeriodBarGraph.kt:249-292`)**:
   - `formatMetricValue` (cited in ticket as `formatPeriodBarValue`) hardcodes `Locale.US` across duration and distance.
   - Crucially, line 271 uses `String.format(Locale.US, "%d.%dh", hours, frac)` where `.` is a literal dot character; passing `Locale.GERMANY` to `String.format` would still output `.` without `DecimalFormatSymbols` or floating-point formatting.
   - Audited via `read_file` on `audit_03_zeitraeume.png`: Confirmed `"Nov. 2 - Nov. 8"` below `"2026-W45"` and `"1.7h"`, `"5.7h"` above bars.

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
- Discovered function is named `formatMetricValue` (line 249), not `formatPeriodBarValue`.
- Confirmed `formatPeriodXLabel` (line 308) already accepts `locale: Locale = Locale.getDefault()`, whereas `formatMetricValue` lacked `locale`.
- Identified that duration formatting in line 271 cannot rely on `String.format(locale, "%d.%dh", ...)` and requires `DecimalFormatSymbols.getInstance(locale).decimalSeparator`.
- Confirmed `PeriodSummaryCard.kt:169` and `PeriodMapScreen.kt:283` directly render `summary.periodDateRange`.

---

## 4. User Scope Grounding (ATT-1250)
* **In-Scope**:
  - Localized weekly date range formatter in `DateTimeUtils` / `PeriodsRepository` handling day/month order per locale (`d. MMM` in `de`, `MMM d` in `en`).
  - Wire dynamic date range resolution in `PeriodsRepository.kt` and `PeriodSummariesDatabaseManager.kt:338` so cached periods immediately adapt to the active locale.
  - Add `locale: Locale = Locale.getDefault()` to `formatMetricValue` in `PeriodBarGraph.kt` with localized decimal separators.
  - Unit tests covering German (`de_DE`) and US English (`en_US`) formatting for date ranges and bar values.
* **Out-of-Scope**: Bar chart layout/geometry, sport filtering, non-week period labels, and database schema migrations.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**:
  - `REQ-PER-018.2` (*Sub-Hour Duration & Value Formatting*): Refined to require active locale decimal separator for `1h..10h` durations (`1,5h` in German).
  - `REQ-PER-023` (*PeriodBarGraph Distance & Imperial Unit Parity*): Refined to require active locale decimal separator for `km` and `mi`.
  - `REQ-PER-011` & `REQ-LOC-001`: Metric display localized formatting and 9-language formatting parity.
* **Preservation of Core Invariants**: `PeriodSummaries.db` schema and table columns unchanged; period sort keys and aggregation boundaries preserved; zero runtime regressions.

---

## 6. Architectural Strategy & High-Level Solution
* Implement `formatPeriodDateRange(startS, endS, locale, zoneId)` with fallback skeleton resolution (`d. MMM` for de, `MMM d` for en).
* Update `formatMetricValue` in `PeriodBarGraph.kt` to accept `locale: Locale = Locale.getDefault()` and format decimals with `DecimalFormatSymbols`.
* Add targeted unit tests in `PeriodBarGraphTest.kt` and `PeriodsRepositoryTest.kt` / `DateTimeUtilsTest.kt`.

---

## 7. System Invariants & Risk Assessment
* **Invariants**: Aggregation logic, database schema, and existing non-decimal formatting (`<1m`, `${m}m`, `${h}h`) remain unchanged.
* **Risk Rating**: **LOW** (Pure formatting logic and UI presentation; zero database migration or threading impact).
