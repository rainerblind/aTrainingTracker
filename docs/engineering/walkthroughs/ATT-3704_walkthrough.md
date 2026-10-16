# Stage 5: Walkthrough & Verification - ATT-3704: Add dedicated Athlete Profile screen with timestamped history and time-series plots for Weight, Resting HR, FTP, and W/kg

**Ticket**: [ATT-3704](https://rainerblind.atlassian.net/browse/ATT-3704)  
**Sub-task**: `ATT-3834` (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Requirement Mapping**: `REQ-UI-391`  
**Test Mapping**: `TST-UI-354`  
**Branch**: `feature/ATT-3704`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Verification Overview

This ticket implements a dedicated **Athlete Profile** screen (`NavRoutes.ATHLETE_PROFILE`, accessible from `My Stuff` in the navigation drawer) backed by SQLite history persistence (`AthleteMetricsDatabaseManager`), unidirectional DataStore projection (`AthleteMetricsRepository`), and an interactive time-series chart (`AthleteProfileScreen`, `AthleteMetricsPlotMath`).

### Key Changes Verified:
1. **SQLite Timestamped History & Symmetric DTO Mappers (`REQ-UI-391.1`, Rule 20)**:
   - Created `AthleteMetricsDatabaseManager.kt` (`AthleteMetrics.db`, table `athlete_metrics`, index `idx_athlete_metrics_type_ts`).
   - Implemented `AthleteMetricType` (`WEIGHT_KG`, `HR_REST_BPM`, `FTP_WATTS`) with physiological bounds validation and `AthleteMetricEntry` with symmetric `fromCursor(cursor)` and `fromCursorBatch(cursor)` mappers.
2. **Unidirectional Latest-Value Projection & Transactional Seeding (`REQ-UI-391.2`)**:
   - Extended `SettingsDataStore.kt` with `KEY_ATHLETE_METRICS_SEEDED`, `setHrRest(hrRest: Int?)`, and `setFtp(ftp: Int?)`.
   - Implemented `AthleteMetricsRepository.kt` with idempotent initial seeding (`ensureSeededFromDataStore()`) and unidirectional `reconcileLatestToDataStore(metricType)` across `addOrUpdateMetric`, `deleteMetric`, `recordWeightFromExternalEditor`, `recordHrRestFromExternalCalculator`, and `recordFtpFromExternalCalculator`.
   - Wired `ZonesSettingsActivity.kt` and `EditSensorFieldDialog.kt` so weight, resting HR, and FTP edits from zone calculation and sensor dialogs automatically append timestamped history entries.
3. **Derived W/kg Time-Series Math & Range Filtering (`REQ-UI-391.3`, Rule 36)**:
   - Implemented `AthleteMetricsPlotMath.kt` (`computeDerivedPowerToWeightSeries`, `filterAndConvertSeries`, `formatMetricValue`, `formatDateLabel`, `formatDateIso`) using `SettingsDataStore.LBS_PER_KG` and `Locale.US` formatting (`%.2f` for W/kg, `%.1f` for weight).
4. **Athlete Profile UI, Interactive Canvas Plot & Navigation (`REQ-UI-391.4`, `REQ-UI-391.5`, Rules 23, 37, 38)**:
   - Created `AthleteProfileViewModel.kt` and `AthleteProfileScreen.kt` featuring a 2x2 current summary card grid, interactive time-series `Canvas` plot with compact non-wrapping metric and time-range (`1M`, `3M`, `6M`, `1Y`, `ALL`) selector pills (`maxLines = 1, softWrap = false`), chronological history list with `DatePickerDialog` add/edit modal and `DeleteConfirmationDialog`, plus Light/Dark/Empty `@Preview`s.
   - Created curved vector drawable `ic_athlete_profile.xml` (Rule 37) and wired `R.id.drawer_athlete_profile` -> `NavRoutes.ATHLETE_PROFILE` in `AppNavigationDrawer.kt` and `ATrainingTrackerApp.kt`.
   - Added 100% 9-locale string translations (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-391.1`, `REQ-UI-391.3` | `TST-UI-354.1` | `AthleteMetricsDtoAndMathTest` (DTO symmetry, W/kg step-hold, range filtering, bounds) | **PASSED** | `Verified` |
| `REQ-UI-391.2` | `TST-UI-354.2` | `AthleteMetricsRepositorySyncTest` (Seeding idempotency & unidirectional projection state machine) | **PASSED** | `Verified` |
| `REQ-UI-391.4`, `REQ-UI-391.5` | `TST-UI-354.3` | `AthleteProfileContractAndLocalizationTest` & `NavigationDrawerStateTest` (Drawer, Rule 37/38, 9-locale parity) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-354.4` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL
All unit & architectural contract tests executed with 0 failures.
```

### Targeted Unit & Integration Tests
```text
> Task :app:testDebugUnitTest
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsDtoAndMathTest > fromCursor and fromCursorBatch produce identical objects and do not close cursor PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsDtoAndMathTest > computeDerivedPowerToWeightSeries pairs FTP and Weight across timestamps PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsDtoAndMathTest > filterAndConvertSeries filters by range and converts imperial weight PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsDtoAndMathTest > metric physiological bounds validation enforces strict limits PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepositorySyncTest > ensureSeededFromDataStore seeds existing metrics once and is idempotent PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepositorySyncTest > unidirectional projection state machine handles newer, backdated, and deleted entries PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest > drawer item maps to ATHLETE_PROFILE route and appears in My Stuff group PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest > ic_athlete_profile vector exists and uses curved geometric primitives PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest > AthleteProfileScreen enforces Rule 38 compact pills and multi-state previews PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest > all 9 locales define athlete profile strings with matching format specifiers PASSED
com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerStateTest > createDrawerGroups contains expected structure PASSED
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
* **Checked against `docs/design_guidelines.md` §5**:
  * **Shapes** ☑: `RoundedCornerShape(12.dp)` for summary and chart cards; `RoundedCornerShape(8.dp)` for compact selector pills (`MetricSelectorPill`, `TimeRangePill`).
  * **Spacing** ☑: `16.dp` outer horizontal padding, `12.dp` card internal padding, `8.dp` grid/row spacing, `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` (`48.dp`) top header bar.
  * **Colors/Themes** ☑: `MaterialTheme.colorScheme` tokens (`surface`, `surfaceContainerLow`, `primary`, `primaryContainer`, `outlineVariant`) verified across Light and Dark/AMOLED `@Preview`s (`AthleteProfileScreenLightPreview`, `AthleteProfileScreenDarkPreview`, `AthleteProfileScreenEmptyPreview`).
  * **Typography/Icons** ☑: `MaterialTheme.typography` hierarchy; `ic_athlete_profile.xml` uses curved cubic Bezier/arc primitives (`C`/`A` commands) per Rule 37; compact selector pills enforce `maxLines = 1, softWrap = false` per Rule 38.
  * **Placement** ☑: Placed under `My Stuff` in `AppNavigationDrawer` alongside Zones, Equipment, Workout Summaries, and Export.
* **Deviations & justification**: None. On-device physical inspection on Google Pixel 10 will be performed at the start of Ceremony 2 (`./gradlew installDebug` per Rule 10).

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-UI-391` in `docs/requirements.md` and `TST-UI-354` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-3704` transitioned to `Final Review (Human)` and integrated into `sprint/2026-42.6`.
