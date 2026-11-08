# Stage 1 Analysis: ATT-5872 - SportTypesTabsScreen Empty GC Mapping, Export Label Localization & ISO Date Formatting

**Ticket**: [ATT-5872](https://rainerblind.atlassian.net/browse/ATT-5872)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5872`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In `SportTypesTabsScreen.kt` (L474–536), three visual formatting and localization defects exist:
1. **Dangling `GC:` Label**: On sport cards where GoldenCheetah mapping is empty (notably default `Other`), `sport_type_file_mapping_format` unconditionally formats `TCX: %1$s, %2$s` with `mapping_format_gc`, producing a dangling trailing `"💾 TCX: Other, GC:"`.
2. **Untranslated Sport Name & Export Keys**: Default locked sport card renders raw database string `"Other"` instead of localized `R.string.sport_type_other` (`"Andere"` in German). Furthermore, `stravaName`, `tcxName`, and `gcName` display raw English technical keys (`"Workout"`, `"Other"`, `"walk"`) instead of localized display strings.
3. **Raw ISO-8601 Usage Dates**: *Erste Aktivität* and *Letzte Aktivität* display raw `"YYYY-MM-DD"` strings (`"2018-08-12"`) rather than locale-formatted dates (`"12.08.2018"` in `de_DE`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `SportTypesTabsScreen.kt` (commit `0e17d2a5`, migrating `2ef1bbdf`) faithfully preserved legacy `SimpleCursorAdapter` text-concatenation without checking `item.gcName.isNotBlank()`. Database `UI_NAME` stores the string from `TTSportType.OTHER.toString()` at SQLite creation time, preserving English `"Other"` across locale changes. Date strings extracted from `WorkoutSummaries` stats are passed unparsed to `UsageItem`.
* **Independent Peer Review & Verification**:
  * Live code audit confirms `SportTypesTabsScreen.kt:491-495` unconditionally wraps `item.gcName` in `mapping_format_gc` and joins via `sport_type_file_mapping_format`.
  * `EditSportTypeDialog.kt:94-101` already implements the correct Strava mapping pattern via `stravaDbValues.zip(stravaUiNames).toMap()`.
  * `R.array.TCX_Sport_Types` and `R.array.GC_Sport_Types` are identically translated across all 9 locales in canonical order (`Other/Walking/Running/Biking` and `walk/run/mtb/bike`).
  * `EquipmentTabsScreen.kt:548-561` (`ATT-5847`) exhibits the exact same raw ISO date rendering in `UsageItem`, proving high cross-ticket synergy for a shared utility (`Rule 36`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Omit `, GC: ...` or format cleanly when `item.gcName.isBlank()`, eliminating dangling colon.
  2. Map locked default sport names (`!item.isEditable`) to localized `BSportType` string resources, and map Strava, TCX, and GC export identifiers to localized UI array strings with clean fallbacks.
  3. Create `DateTimeUtils.formatIsoDate(dateString, locale)` in `com.atrainingtracker.trainingtracker.ui.common` and apply it to `UsageItem` in `SportTypesTabsScreen.kt` (and reusable by `ATT-5847`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying `SportTypeDatabaseManager` SQLite schema or `WorkoutSummaries` aggregation.
  - Refactoring `EditSportTypeDialog` layout (governed separately by `REQ-UI-365`).
* **Complexity Rating**: **2 SP (Standard)**. Multi-field Compose formatting and reusable date utility.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-061` (*Sport Types Management Screen*); establishes `REQ-UI-459` (*Sport Type Card Presentation, Localized Mappings & Locale-Aware Usage Dates*).
* **Historical Origin & Commit Trace**: Commit `2ef1bbdf` (2026-02-23) simplified file mapping labels; commit `0e17d2a5` migrated list to Jetpack Compose.
* **Root Reason for Existing Formulation**: Early Compose migration replicated legacy XML layout logic where TCX and GC were assumed to always have non-empty pairs.
* **Preservation of Core Invariants**: Technical export keys sent to Strava, TCX, and FIT exporters remain strictly unchanged in SQLite and export payloads.

---

## 5. Architectural Strategy & High-Level Solution
* `DateTimeUtils.kt` (`com.atrainingtracker.trainingtracker.ui.common`):
  - Pure function `formatIsoDate(dateString: String?, locale: Locale = Locale.getDefault()): String`.
  - Parses ISO-8601 `LocalDate` and formats via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)`. Gracefully returns empty string or original string on null/blank/error.
* `SportTypesTabsScreen.kt`:
  - Name resolution: If `!item.isEditable` and `item.bSportType == BSportType.UNKNOWN`, bind `stringResource(R.string.sport_type_other)` (and similarly for Run/Bike if default).
  - Export mapping: Resolve Strava via `Strava_Sport_Types_Strava_Names` -> `Strava_Sport_Types_UI_Names`; map TCX and GC via canonical list index lookup into `R.array.TCX_Sport_Types` and `R.array.GC_Sport_Types`.
  - Conditional GC formatting: Only append GC when `item.gcName.isNotBlank()`.
  - Dates: Pass `DateTimeUtils.formatIsoDate(it)` to `UsageItem`.
* Tests & Verification: Add `DateTimeUtilsTest.kt` covering multiple locales and edge cases, plus contract tests in `SportTypesTabsScreenContractTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Technical export keys in SQLite (`stravaName`, `tcxName`, `gcName`) and exporter payloads are completely unaffected.
  2. Custom user-defined sport types with editable names (`isEditable == true`) retain their exact user-specified names.
  3. `formatIsoDate` is fail-safe and never throws runtime exceptions on malformed dates.
* **Risk Rating**: **LOW** (Presentation-tier formatting and localization without database schema mutations).
