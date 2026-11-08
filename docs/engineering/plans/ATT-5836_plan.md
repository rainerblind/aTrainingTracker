# Stage 3: Implementation Plan - ATT-5836: PeriodSummary (Zeiträume): Localize date range header ('Nov. 2 - Nov. 8') and chart decimal labels ('1.7h') according to active locale

**Ticket**: [ATT-5836](https://rainerblind.atlassian.net/browse/ATT-5836)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Zeiträume / Periods & Stats*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5836`  
**Requirement Mapping**: `REQ-PER-028` (`REQ-PER-028.1`–`REQ-PER-028.4`)  
**Test Mapping**: `TST-PER-036` (`TST-PER-036.1`–`TST-PER-036.5`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Scope
Visual UI audits of the **Periods (`Zeiträume`)** screen (`audit_03_zeitraeume.png`) identified two localization defects under German locale (`de_DE`):
1. **Hardcoded US Month-First Date Range Header**: Weekly period card headers display `"Nov. 2 - Nov. 8"` instead of German day-first order (`2. Nov. – 8. Nov.`). Caused by `rangeDateFormatter = DateTimeFormatter.ofPattern("MMM d")` in `PeriodsRepository.kt:96, 566, 766`.
2. **Hardcoded `Locale.US` Decimal Point in Bar Chart Labels**: `PeriodBarGraph.kt:249` `formatMetricValue` hardcodes `Locale.US` and embeds a literal dot (`%d.%dh` in line 271), preventing German decimal comma rendering (`1,7h`, `5,4km`, `5,3mi`).
3. **Cached Date Range Locale Invalidation**: SQLite cached rows (`PeriodSummariesDatabaseManager.kt:308, 338`) preserve historical strings unless dynamically formatted on read.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-028` (*Period Summary Date Range & Chart Metric Label Active Locale Formatting*)
  * `REQ-PER-028.1`: Weekly date range formatting in `DateTimeUtils.formatPeriodDateRange` respecting active locale (`d. MMM` in `de`, `MMM d` in `en`).
  * `REQ-PER-028.2`: Dynamic date range resolution for cached periods in `PeriodSummariesDatabaseManager.mapCursorToPeriod` and `PeriodSummaryCard`.
  * `REQ-PER-028.3`: Localized decimal separator in `formatMetricValue` for duration (`1h..10h`) and distance (`km`/`mi`) using `DecimalFormatSymbols`.
  * `REQ-PER-028.4`: Preservation of non-decimal durations (`<1m`, `${m}m`, `${h}h`), integer kilometer/mile values, and database schema.
* **Test Mapping**: `TST-PER-036` (*Period Date Range & Bar Graph Metric Decimal Localization Verification Suite*)
  * `TST-PER-036.1`: `DateTimeUtilsTest.kt` / `PeriodsRepositoryTest.kt` weekly date range locale parity test.
  * `TST-PER-036.2`: `PeriodBarGraphTest.kt` duration and distance decimal comma/dot localization test.
  * `TST-PER-036.3`: `PeriodSummaryCardTest.kt` / `PeriodsRepositoryTest.kt` dynamic cached period resolution test.
  * `TST-PER-036.4`: `PeriodBarGraphTest.kt` non-decimal and edge metric value preservation test.
  * `TST-PER-036.5`: Tier 1 targeted regression (`ui.aftermath.periodlist.*`, `ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & UI Consistency
1. **Database Schema & Version Invariance**: Zero alterations to `PeriodSummaries.db` table schema or database version (`v27`).
2. **Temporal & Sorting Invariance**: Monday 00:00 to Sunday 23:59:59 aggregation bounds and ISO week keys (`2026-W45`) preserved.
3. **UI Consistency**: Reuses existing `PeriodSummaryCard` and `PeriodBarGraph` text composables and typography tokens (`MaterialTheme.typography.bodySmall`, `labelSmall`). Zero new one-off styles.

---

## 4. Proposed Architectural Changes
* **`DateTimeUtils.kt`**: Add `formatPeriodDateRange(startS: Long, endS: Long, locale: Locale = Locale.getDefault(), zoneId: ZoneId = ZoneId.systemDefault()): String` with localized skeleton pattern resolution (`MMMd`) and try-catch fallback.
* **`PeriodsRepository.kt`**: Replace hardcoded `rangeDateFormatter` usages with `DateTimeUtils.formatPeriodDateRange`.
* **`PeriodSummariesDatabaseManager.kt`**: In `mapCursorToPeriod`, dynamically resolve `periodDateRange` for `PeriodType.WEEK` using `DateTimeUtils.formatPeriodDateRange(startTimestampS, endTimestampS)`.
* **`PeriodBarGraph.kt`**: Add `locale: Locale = Locale.getDefault()` parameter to `formatMetricValue` and format decimals using `DecimalFormatSymbols.getInstance(locale).decimalSeparator`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Date Range Utility & Dynamic Resolution**:
  * Implement `formatPeriodDateRange` in `DateTimeUtils.kt`.
  * Update `PeriodsRepository.kt:566, 766` and `PeriodSummariesDatabaseManager.kt:338`.
* **Step 2: Chart Decimal Separator Localization**:
  * Update `formatMetricValue` in `PeriodBarGraph.kt` with `locale` parameter and `DecimalFormatSymbols`.
* **Step 3: Automated Unit & Contract Tests**:
  * Add unit tests in `DateTimeUtilsTest.kt` and `PeriodBarGraphTest.kt` verifying German (`de_DE`) and US English (`en_US`) output.
* **Step 4: Tier 1 Verification**:
  * Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.common.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.
  * Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests in Stage 4; live UI walkthrough in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-5836`; revert via `git reset --hard` if unexpected regression occurs.
