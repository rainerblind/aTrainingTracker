# Stage 2: Requirement & Test Specification - ATT-5872: SportTypesTabsScreen Polish

**Ticket**: [ATT-5872](https://atrainingtracker.atlassian.net/browse/ATT-5872)  
**Parent Epic**: [ATT-5910](https://atrainingtracker.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5872`  
**Requirement Mapping**: `REQ-UI-475` (*Sport Type Card Presentation, Localized Mappings & Locale-Aware Usage Dates*)  
**Test Spec ID**: `TST-UI-475` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-475)

### 1.1 Problem Statement & Rationale
In `SportTypesTabsScreen.kt`, three visual presentation, mapping, and localization gaps existed: (1) sport cards with empty GoldenCheetah mappings unconditionally joined `mapping_format_gc`, producing dangling trailing text (`"💾 TCX: Other, GC:"`); (2) default locked sport types displayed raw English database names (`"Other"`) rather than localized string resources (`"Andere"` in German), and export identifiers displayed unlocalized technical keys; and (3) first and last activity usage dates displayed raw ISO-8601 strings (`"YYYY-MM-DD"`) instead of locale-formatted dates.

### 1.2 Functional & Architectural Requirements
1. **Conditional GoldenCheetah Mapping & File Export Format (`REQ-UI-475.1`)**: On sport type cards, when `item.gcName.isNullOrBlank()`, the UI SHALL display only the localized TCX mapping (`stringResource(R.string.mapping_format_tcx, localizedTcx)`), completely omitting `, GC:` and trailing empty colons. When `item.gcName` is present and non-blank, the UI SHALL format both mappings via `sport_type_file_mapping_format`.
2. **Default Sport Localization & Export Identifier Mapping (`REQ-UI-475.2`)**: For system default / locked sport types (`!item.isEditable`), the sport card header SHALL resolve the display name via `item.bSportType.stringResId` (`R.string.sport_type_other` for `BSportType.UNKNOWN`) rather than displaying raw database `item.name`. Strava export keys SHALL be mapped to localized UI names via `Strava_Sport_Types_Strava_Names` and `Strava_Sport_Types_UI_Names` array pairs. TCX and GC export keys SHALL be resolved to localized UI strings via array lookups (`R.array.TCX_Sport_Types` and `R.array.GC_Sport_Types`) with fallback to raw values if unmapped.
3. **Locale-Aware ISO-8601 Date Formatting (`REQ-UI-475.3`)**: `DateTimeUtils.kt` in `com.atrainingtracker.trainingtracker.ui.common` SHALL provide pure helper `formatIsoDate(dateString: String?, locale: Locale = Locale.getDefault()): String`. Valid ISO-8601 dates (e.g. `"2018-08-12"`) SHALL be parsed via `LocalDate.parse()` and formatted with `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)` (e.g. `"12.08.2018"` in `de_DE`, `"Aug 12, 2018"` in `en_US`). Null, blank, or malformed strings SHALL fail-safely return an empty string or the raw fallback string without throwing runtime exceptions. In `SportTypesTabsScreen.kt`, first and last activity dates SHALL be rendered using `DateTimeUtils.formatIsoDate(it)`.
4. **Contract & Unit Test Regression (`REQ-UI-475.4`)**: `DateTimeUtilsTest.kt` SHALL verify date parsing across locales (`de_DE`, `en_US`, `ja_JP`) and error tolerance. `SportTypesTabsScreenContractTest.kt` SHALL verify empty GC suppression, default sport localization, and formatted usage date rendering.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Empty GC Mapping Suppression)**:
  * *Given* a sport type with empty `gcName` (e.g. default "Other"),
  * *When* rendered in `SportTypesTabsScreen`,
  * *Then* the file format row displays `"💾 TCX: Andere"` (or locale equivalent) without trailing `, GC:`.
* **Criterion 2 (Default Sport & Export Key Localization)**:
  * *Given* a locked default sport type (`!item.isEditable`),
  * *When* rendered in `SportTypesTabsScreen`,
  * *Then* the header title displays localized name (`"Andere"` in German) and export keys map to localized UI array strings.
* **Criterion 3 (Locale-Aware Date Formatting)**:
  * *Given* sport usage dates `"2018-08-12"` and `"2024-05-01"`,
  * *When* rendered in `SportTypesTabsScreen` in locale `de_DE`,
  * *Then* `UsageItem` displays `"12.08.2018"` and `"01.05.2024"`.

### 1.4 System Invariants & Chesterton's Fence
* Technical export keys in SQLite and exporter payloads remain strictly unchanged.
* Custom user-defined sport types with `isEditable == true` retain user-defined names.
* `formatIsoDate` is fail-safe and never throws runtime exceptions on malformed dates.
* 100% 9-language translation parity maintained; 100% clean-room test pass rate.

---

## 2. Test Specification (TST-UI-475)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-475.1]` | Unit | `DateTimeUtilsTest`: ISO date parsing across locales (`de_DE`, `en_US`, `ja_JP`) and graceful fallback for null/blank/malformed strings | `REQ-UI-475.3` | Specified |
| `[TST-UI-475.2]` | Contract | `SportTypesTabsScreenContractTest`: empty GC mapping suppression vs. combined format | `REQ-UI-475.1` | Specified |
| `[TST-UI-475.3]` | Contract | `SportTypesTabsScreenContractTest`: default sport localization (`BSportType.stringResId`) & export key array mappings | `REQ-UI-475.2` | Specified |
| `[TST-UI-475.4]` | Integration | `SportTypesTabsScreenContractTest`: `UsageItem` date formatting integration | `REQ-UI-475.3` | Specified |
| `[TST-UI-475.5]` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.common.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.sporttypes.*" --tests "com.atrainingtracker.trainingtracker.localization.*"` | `REQ-UI-475.1`..`4` | Specified |
