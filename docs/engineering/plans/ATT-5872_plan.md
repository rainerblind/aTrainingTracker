# Stage 3: Implementation Plan - ATT-5872: SportTypesTabsScreen Polish & DateTimeUtils

**Ticket**: [ATT-5872](https://atrainingtracker.atlassian.net/browse/ATT-5872) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-5910](https://atrainingtracker.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5872`  
**Requirement Mapping**: `REQ-UI-475` (`REQ-UI-475.1`..`4`) | **Test Mapping**: `TST-UI-475` (`TST-UI-475.1`..`5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `SportTypesTabsScreen.kt` (L474–536), three visual formatting and localization defects exist: (1) sport cards with empty GoldenCheetah mappings unconditionally join `mapping_format_gc`, producing dangling trailing text (`"💾 TCX: Other, GC:"`); (2) default locked sport types display raw English database names (`"Other"`) rather than localized string resources (`"Andere"` in German), and export identifiers display unlocalized technical keys; and (3) first and last activity usage dates display raw ISO-8601 strings (`"YYYY-MM-DD"`) instead of locale-formatted dates (`"12.08.2018"` in `de_DE`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-475` (*Sport Type Card Presentation, Localized Mappings & Locale-Aware Usage Dates*)
* **Test Mapping**: `TST-UI-475` (*Sport Types UI Presentation, Localization & Date Formatter Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Technical Export Invariance**: Export keys stored in SQLite (`stravaName`, `tcxName`, `gcName`) and written to export payloads (`StravaUploader`, `FitFileWriter`, `TCXFileWriter`) remain strictly unchanged.
2. **User Customization Preservation**: Custom user-defined sport types (`isEditable == true`) retain their exact custom names.
3. **Fail-Safe Date Parsing**: `DateTimeUtils.formatIsoDate` is pure and fail-safe, returning empty or fallback strings without throwing runtime exceptions.
4. **Cross-Ticket Synergy (Rule 36)**: `DateTimeUtils.formatIsoDate` is designed in `com.atrainingtracker.trainingtracker.ui.common` for direct reuse by `ATT-5847` (`EquipmentTabsScreen`).

---

## 4. Proposed Architectural Changes
* `DateTimeUtils.kt` (`com.atrainingtracker.trainingtracker.ui.common`): Pure utility object providing `formatIsoDate(dateString: String?, locale: Locale = Locale.getDefault()): String` using `LocalDate.parse` and `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)`.
* `SportTypesTabsScreen.kt`:
  - Default sport localization: If `!item.isEditable && item.bSportType == BSportType.UNKNOWN`, render `stringResource(R.string.sport_type_other)` (and `R.string.sport_type_run` / `R.string.sport_type_bike` for uneditable defaults).
  - Export array mapping: Map Strava via `Strava_Sport_Types_Strava_Names` to `Strava_Sport_Types_UI_Names`; map TCX and GC via canonical English key index lookups into `R.array.TCX_Sport_Types` and `R.array.GC_Sport_Types`.
  - Conditional GC formatting: If `item.gcName.isNotBlank()`, format `sport_type_file_mapping_format`; if blank, format only `mapping_format_tcx`.
  - Usage dates: Format `item.firstUsed` and `item.lastUsed` via `DateTimeUtils.formatIsoDate(it)`.
* **UI Consistency (Rule 23)**: Reuses `MappableListItem`, `UsageItem`, `StatsSummaryBlock`, and typography tokens from `MaterialTheme.typography` and `MaterialTheme.colorScheme`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `DateTimeUtils.kt` in `ui/common`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/DateTimeUtils.kt`
* Action: Author `formatIsoDate(dateString: String?, locale: Locale = Locale.getDefault()): String` (`REQ-UI-475.3`).

### Step 2: Implement Unit Tests in `DateTimeUtilsTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/common/DateTimeUtilsTest.kt`
* Action: Verify locale formatting (`de_DE`, `en_US`, `ja_JP`), edge cases (`null`, blank, invalid, leap year) (`TST-UI-475.1`).
* Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.common.DateTimeUtilsTest"`

### Step 3: Localize Sport Card Names & Export Mappings in `SportTypesTabsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt`
* Action: Suppress empty GC mapping, map default sport names to localized string resources, map Strava/TCX/GC keys to localized array labels (`REQ-UI-475.1`, `REQ-UI-475.2`).

### Step 4: Integrate `DateTimeUtils.formatIsoDate` into `SportTypesTabsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt`
* Action: Pass `DateTimeUtils.formatIsoDate(it)` to `UsageItem` for `firstUsed` and `lastUsed` (`REQ-UI-475.3`).

### Step 5: Implement `SportTypesTabsScreenContractTest.kt` & Run Full Regression
* File: `app/src/test/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreenContractTest.kt`
* Action: Verify empty GC suppression, default sport name resolution, export key lookups, and formatted usage dates (`TST-UI-475.2`..`5`).
* Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.common.*" --tests "com.atrainingtracker.banalservice.ui.sporttype.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests, full suite check, and living documentation governance audit (`python3 tools/verify_requirement_governance.py`).
* **Rollback**: Branch isolation (`feature/ATT-5872`) allows immediate rollback via `git restore .`.
