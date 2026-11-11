# Walkthrough: Unified Fitness & Physiology Hub & Settings Training Zones (ATT-6720)

* **Parent Ticket**: [ATT-6720](https://rainerblind.atlassian.net/browse/ATT-6720) (*[Verbesserung] [High] Unify Fitness, HRV Readiness, Body/Threshold & Cardio-Power graphs into Fitness & Physiology hub and move Training Zones to Settings*)
* **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)
* **Sub-Tasks**:
  * [ATT-7024](https://rainerblind.atlassian.net/browse/ATT-7024) (*[SWE.1] Problem Domain & Root Cause Analysis*) - `Erledigt`
  * [ATT-7027](https://rainerblind.atlassian.net/browse/ATT-7027) (*[SWE.4] Requirement & Test Specification*) - `Erledigt`
  * [ATT-7028](https://rainerblind.atlassian.net/browse/ATT-7028) (*[SWE.2 / SWE.3] Architecture & Implementation Plan*) - `Erledigt`
  * [ATT-7042](https://rainerblind.atlassian.net/browse/ATT-7042) (*[SWE.3 / Implementation] Construction & Implementation*) - `Erledigt`
* **Target Version**: `V5.0.0` | **Active Sprint**: `2026-46.2`
* **Requirement**: [`REQ-UI-513`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_ui_design_system.md) (*Unified Fitness & Physiology Analytical Hub, Training Drawer Streamlining, and Dedicated Settings Training Zones*)
* **Test Specification**: [`TST-UI-513`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_ui_design_system.md) (*Fitness & Physiology Hub, Training Zones Settings & Drawer Navigation Verification Suite*)
* **Branch**: `feature/ATT-6720` -> `sprint/2026-46.2`
* **Author**: AI Agent 1 (Implementer - Flash) | **Reviewer**: AI Agent 2 (Chief Inspector - Pro) | **Date**: 2026-11-11

---

## 1. Executive Summary & Architectural Overview

Prior to ATT-6720, athlete physiology and recovery charts were fragmented across three navigation destinations:
1. **`Periods` (*Zeiträume*, `PeriodsTabsScreen.kt`)**: Intended strictly for calendar training volume aggregations (`Days / Weeks / Months / Years`), yet hosted `HrvReadinessTrendCard` above `PeriodBarGraph`.
2. **`Morning HRV` (*Morgendliche HRV*)**: Added as a standalone top-level item in the *Training* drawer group purely for a 60–120s morning check.
3. **`Profile & Zones` (*Profil & Zonen*, `AthleteProfileScreen.kt`)**: Conflated static zone settings (3 tabs: `HR Run`, `HR Bike`, `Power Bike`) with longitudinal time-series analytics (Tab 0 stacked PMC, Body/Thresholds, Cardio-Power, and logs in one vertical scroll).

### Target Architecture Implemented
1. **Streamlined Training Drawer Group** (`AppNavigationDrawer.kt`):
   - Reduced to 4 canonical destinations:
     1. Start Tracking (`drawer_start_tracking`)
     2. Workouts (`drawer_workouts`)
     3. Periods (`drawer_periods`)
     4. Fitness & Physiology (`drawer_athlete_profile`, titled *Fitness & Physiologie* / *Fitness & Physiology*)
   - Standalone `drawer_morning_hrv` retired from the drawer.
2. **Settings Drawer Training Zones** (`AppNavigationDrawer.kt`):
   - Restored `drawer_training_zones` (`ic_speed`, `prefs_training_zones`) in the *Settings* drawer section directly following `drawer_sport_types`.
   - Updated `isDrawerItemSelected` to decouple `drawer_training_zones` from `drawer_athlete_profile`.
3. **Decoupled Periods Screen** (`PeriodsTabsScreen.kt`, `PeriodsScreen.kt`, `PeriodsViewModel.kt`):
   - Stripped all `HrvReadinessTrendCard` rendering, repository loading, and state flows, returning Periods strictly to calendar training volume aggregations.
4. **Unified 4-Tab Fitness & Physiology Hub** (`AthleteProfileScreen.kt`, `AthleteProfileViewModel.kt`):
   - Standard fixed header height (`LayoutConstants.HEADER_TITLE_ROW_HEIGHT`) with menu hamburger and tab-contextual `+` Add Entry action for body metrics.
   - Synchronized `MetricsTimeRange` (`1M | 3M | 6M | 1Y | All`) calendar X-axis across PMC, Body & Thresholds, and Cardio-Power.
   - **Tab 0: Fitness & Fatigue (PMC)**: `CTL / ATL / TSB / ACWR` + Ramp Rate summary tiles and `PerformanceManagementChartCard` (with PMC disabled guidance if inactive in settings).
   - **Tab 1: HRV & Readiness**: Primary "Measure Morning HRV" CTA button launching `MorningHrvCheckScreen`, empty-state onboarding card when 0 readings exist in `HrvReadiness.db`, 60d posture-isolated SWC corridor + 7d rolling trend + optional Training Load overlay, and chronological history log with entry deletion.
   - **Tab 2: Body & Thresholds**: 3x2 KPI summary tiles (`Weight`, `HRrest`, `FTP`, `CP`, `W'`, `W/kg`), `AthleteMetricTimeSeriesChart`, and filterable history log.
   - **Tab 3: Cardio-Power**: Longitudinal `CardioPowerTrendsCard` (`Fresh HR@200W`, `τ`, `1/K`), conditionally rendered when `enableCardioPowerDynamics` is active.
5. **Dedicated Training Zones Destination** (`ZonesSettingsActivity.kt`, `ATrainingTrackerApp.kt`):
   - Hosts the 3 zone configuration tabs (`HR Run`, `HR Bike`, `Power Bike`), Zone Calculator, and Zone History dialogs via `NavRoutes.TRAINING_ZONES`, equipped with a drawer hamburger toggle.

---

## 2. Summary of Implementation Changes

### 2.1 Navigation Drawer & Destination Routing
* [`AppNavigationDrawer.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt):
  - Training group now consists of 4 items: `drawer_start_tracking`, `drawer_workouts`, `drawer_periods`, `drawer_athlete_profile`.
  - Settings group includes `drawer_training_zones` with icon `R.drawable.ic_speed` and title `R.string.prefs_training_zones`.
  - In `isDrawerItemSelected`, added explicit mapping so `drawer_training_zones` routes to `NavRoutes.TRAINING_ZONES` while `drawer_athlete_profile` routes to `NavRoutes.ATHLETE_PROFILE`.
* [`ATrainingTrackerApp.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt):
  - Passed `onOpenDrawer = { activity.openDrawer() }` to `ZoneSettingsScreen`.
  - Passed `onOpenDrawer = { activity.openDrawer() }` and `onNavigateToMorningHrv = { navController.navigate(NavRoutes.MORNING_HRV) }` to `AthleteProfileScreen`.

### 2.2 Periods Decoupling
* [`PeriodsTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt):
  - Removed `HrvReadinessTrendCard` and all HRV posture/metric/overlay selection callbacks.
* [`PeriodsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsScreen.kt):
  - Removed unused parameters relating to HRV readiness.
* [`PeriodsViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt):
  - Stripped `HrvReadinessRepository` dependency, `hrvReadinessEntries`, `hrvTrendSeries`, posture/metric mode selection, and HRV-specific coroutine jobs.

### 2.3 Fitness & Physiology Hub
* [`AthleteProfileViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileViewModel.kt):
  - Integrated `HrvReadinessRepository`.
  - Added StateFlows: `hrvReadinessEntries`, `selectedHrvPosture`, `selectedHrvMetricMode`, `showHrvTrainingLoad`, `hrvTrendSeries`, `hasHrvReadings`.
  - Added functions: `loadHrvData()`, `deleteHrvReading(id)`, `selectHrvPosture(posture)`, `selectHrvMetricMode(mode)`, `toggleHrvTrainingLoad(enabled)`.
  - Unified `selectTimeRange(range)` to synchronize across PMC, Body & Thresholds, and Cardio-Power.
* [`AthleteProfileScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt):
  - Replaced legacy Zone tabs with 4 analytical tabs:
    - Tab 0: PMC (`PerformanceManagementChartCard`) with PMC-disabled fallback.
    - Tab 1: HRV & Readiness (`MorningHrvCheckScreen` CTA, onboarding card when 0 entries, `HrvReadinessTrendCard`, chronological reading log with `DeleteConfirmationDialog`).
    - Tab 2: Body & Thresholds (`AthleteMetricTimeSeriesChart`, KPI tiles, filterable history log, `+` Add Entry header icon).
    - Tab 3: Cardio-Power (`CardioPowerTrendsCard`).
  - Added header menu hamburger button (`onOpenDrawer`).
* [`ZonesSettingsActivity.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt):
  - Added `onOpenDrawer: (() -> Unit)? = null` parameter to `ZoneSettingsScreen` and header navigation icon.

### 2.4 Localization (9 Languages)
* Added and updated string resources across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
  - `drawer_fitness_physiology`, `fitness_tab_pmc`, `fitness_tab_hrv`, `fitness_tab_body`, `fitness_tab_cardio_power`
  - `hrv_measure_morning_cta`, `hrv_empty_onboarding_title`, `hrv_empty_onboarding_body`, `hrv_history_title`
  - `hrv_delete_dialog_title`, `hrv_delete_dialog_body`, `hrv_entry_deleted_snackbar`

---

## 3. Verification & Test Evidence

### 3.1 Unit Test Execution
Targeted test suites covering navigation, athlete profile, HRV hub, and periods:
```bash
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"
```
**Result**: `BUILD SUCCESSFUL in 20s`, all unit tests passed with 0 failures:
- `NavigationDrawerStateTest.kt` (Training drawer 4 items, Settings drawer adds `drawer_training_zones`, visible non-collapsible items count, decoupled `isDrawerItemSelected`).
- `AutoPauseNavigationTest.kt` (Ordered index of `drawer_auto_pause` following `drawer_training_zones`).
- `AthleteProfileContractAndLocalizationTest.kt` (4-tab hub structure, 9-locale parity for drawer and HRV onboarding strings).
- `AthleteProfileViewModelHrvTest.kt` (Empty state onboarding when 0 readings, populated trend series, posture & metric mode selection, reading deletion, synchronized time-range selection).
- `PeriodsViewModelTest.kt` (Periods volume calculations, zero HRV dependencies).

### 3.2 Living Documentation & Architecture Governance
```bash
python3 tools/verify_requirement_governance.py
```
**Result**:
- `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`
- `PASS: Zero unmounted Composable islands detected in git diff.`
- `PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).`
- `PASS: Zero architecture or system specification modifications detected.`
- Master index tables updated: Requirements Total=1024, Verified=1005; Tests Total=1014, Verified=1013.

---

## 4. Traceability Matrix

| Artifact / Entity | ID | Status | Notes |
|:---|:---|:---|:---|
| Requirement | `REQ-UI-513` (`REQ-UI-513.1`..`REQ-UI-513.6`) | **Verified** | Traceability updated in `docs/requirements.md` & `req_ui_design_system.md` |
| Test Case | `TST-UI-513` (`TST-UI-513.1`..`TST-UI-513.5`) | **Verified** | Traceability updated in `docs/tests.md` & `tst_ui_design_system.md` |
| Subtask (Stage 1) | `ATT-7024` | **Erledigt** | Stage 1 Forensic Analysis & Scope Bounding |
| Subtask (Stage 2) | `ATT-7027` | **Erledigt** | Stage 2 Requirement & Test Specification |
| Subtask (Stage 3) | `ATT-7028` | **Erledigt** | Stage 3 Architecture & Implementation Plan |
| Subtask (Stage 4) | `ATT-7042` | **Erledigt** | Stage 4 Construction & Unit Testing |
| Parent Ticket | `ATT-6720` | **Test** | In Stage 5 submission -> Gate 5 review -> Final Review (Human) |
