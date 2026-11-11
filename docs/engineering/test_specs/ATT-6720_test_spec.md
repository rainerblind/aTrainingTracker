# Stage 2: Requirement & Test Specification - ATT-6720: Unify Fitness, HRV Readiness, Body/Threshold & Cardio-Power graphs into Fitness & Physiology hub and move Training Zones to Settings

**Ticket**: [ATT-6720](https://rainerblind.atlassian.net/browse/ATT-6720)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-513` (*Unified Fitness & Physiology Analytical Hub, Training Drawer Streamlining, and Dedicated Settings Training Zones*)  
**Test Spec ID**: `TST-UI-513` | **Branch**: `feature/ATT-6720`  
**Author**: AI Agent 1 (Implementer - Flash) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-UI-513)

### 1.1 Problem Statement & Rationale
Longitudinal athlete physiology and fitness charts became fragmented across Periods, Morning HRV, and Profile & Zones while mixing static zone configuration with time-series analytics. The system SHALL streamline the *Training* drawer group to 4 clean items, remove `HrvReadinessTrendCard` from `PeriodsTabsScreen.kt`, consolidate longitudinal fitness and recovery analytics into a 4-tab **Fitness & Physiology** hub with a shared `MetricsTimeRange` X-axis, and restore **Training Zones** as a dedicated top-level destination inside the *Settings* drawer group.

### 1.2 Functional & Architectural Requirements
1. **Training Drawer Streamlining (`REQ-UI-513.1`)**: The *Training* drawer group in `AppNavigationDrawer.kt` SHALL contain exactly 4 items in sequence: `Start Tracking`, `Workouts`, `Periods`, and `Fitness & Physiology` (`R.id.drawer_athlete_profile`, title `R.string.drawer_fitness_physiology` / `drawer_athlete_profile`). Standalone `drawer_morning_hrv` SHALL be retired from drawer items.
2. **Settings Drawer Training Zones Integration (`REQ-UI-513.2`)**: The *Settings* drawer group in `AppNavigationDrawer.kt` SHALL include Training Zones (`R.id.drawer_training_zones`, icon `R.drawable.ic_speed`, title `R.string.prefs_training_zones`), positioned following `drawer_sport_types`. `isDrawerItemSelected` SHALL recognize `R.id.drawer_training_zones` independently from `R.id.drawer_athlete_profile`.
3. **Clean Periods Screen Decoupling (`REQ-UI-513.3`)**: `PeriodsTabsScreen.kt` and `PeriodsViewModel.kt` SHALL strictly display calendar volume aggregations, removing `HrvReadinessTrendCard` and eliminating `HrvReadinessRepository` dependencies.
4. **Unified Fitness & Physiology Analytical Hub (`REQ-UI-513.4`)**: `AthleteProfileScreen.kt` (Fitness & Physiology) SHALL host 4 tabs in `PrimaryScrollableTabRow` + `HorizontalPager` with standard fixed header `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` and synchronized `MetricsTimeRange` (`1M | 3M | 6M | 1Y | All`) calendar X-axis:
   - **Tab 1: Fitness & Fatigue (PMC)**: `CTL / ATL / TSB / ACWR` + Ramp Rate KPI summary tiles and `PerformanceManagementChartCard` (with empty/disabled guidance when inactive).
   - **Tab 2: HRV & Readiness**: Primary "Measure Morning HRV" CTA button launching `MorningHrvCheckScreen`, empty-state onboarding card when 0 readings exist in `HrvReadiness.db`, `HrvReadinessTrendCard` (60d posture-isolated SWC corridor + 7d rolling trend + optional Training-Load overlay), and chronological history log of recorded morning HRV entries with delete support.
   - **Tab 3: Body & Thresholds**: Current KPI summary tiles (`Weight`, `HRrest`, `FTP`, `CP`, `W'`, `W/kg`), `AthleteMetricTimeSeriesChart`, `+` Add Entry action, and filterable History Log.
   - **Tab 4: Cardio-Power**: Longitudinal `CardioPowerTrendsCard` (`Fresh HR@200W`, Time Constant `τ`, Slope `1/K`), conditionally displayed when `enableCardioPowerDynamics` is active.
5. **Dedicated Settings Training Zones Hub (`REQ-UI-513.5`)**: `NavRoutes.TRAINING_ZONES` SHALL route directly to `ZoneSettingsScreen` in `ZonesSettingsActivity.kt` hosting the 3 zone configuration tabs (`HR Run`, `HR Bike`, `Power Bike`), Zone Calculator dialog, and Zone History dialog.
6. **9-Language Localization Parity (`REQ-UI-513.6`)**: All new and updated string resources SHALL be localized with 100% parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* the navigation drawer, *When* expanded, *Then* the Training group contains exactly 4 items (`Start Tracking`, `Workouts`, `Periods`, `Fitness & Physiology`) and Settings contains `Training Zones`.
* *Given* the Periods screen, *When* opened, *Then* it displays only calendar volume bars with zero HRV trend card overhead.
* *Given* the Fitness & Physiology hub, *When* navigating between tabs, *Then* Tab 1 renders PMC, Tab 2 renders HRV readiness with Morning HRV measurement CTA & log, Tab 3 renders Body & Thresholds, and Tab 4 renders Cardio-Power when enabled.
* *Given* selecting Training Zones from Settings, *When* opened, *Then* it loads `ZoneSettingsScreen` with the 3 zone tabs, calculator, and history dialogs.

### 1.4 System Invariants
* DataStore zone persistence and atomic editing MUST remain intact.
* Backward compatibility for `NavRoutes.ATHLETE_PROFILE` and `NavRoutes.TRAINING_ZONES` deep links MUST be preserved.
* Zero SQLite schema changes; WCAG AA contrast standards maintained.

---

## 2. Test Specification (TST-UI-513)

### Test Case 1: Navigation Drawer Structure & Route Resolution (`TST-UI-513.1`)
* **Scope**: JVM Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`, `AppNavigationDrawerTest.kt`)
* **Assertions**:
  - Training group contains exactly 4 items (`drawer_start_tracking`, `drawer_workouts`, `drawer_periods`, `drawer_athlete_profile`).
  - `drawer_morning_hrv` is absent from drawer groups.
  - Settings group includes `drawer_training_zones` with icon `ic_speed` and title `prefs_training_zones`.
  - `isDrawerItemSelected` separates `drawer_training_zones` and `drawer_athlete_profile`.

### Test Case 2: Periods Screen Clean Decoupling (`TST-UI-513.2`)
* **Scope**: JVM Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModelTest.kt`)
* **Assertions**:
  - `PeriodsViewModel` operates cleanly without `HrvReadinessRepository` dependency.
  - `PeriodsTabsScreen` contains zero HRV trend composables.

### Test Case 3: Fitness & Physiology Hub Architecture (`TST-UI-513.3`)
* **Scope**: Contract & Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`)
* **Assertions**:
  - 4-tab hub structure: PMC (Tab 1), HRV & Readiness (Tab 2), Body & Thresholds (Tab 3), Cardio-Power (Tab 4).
  - Tab 2 provides Morning HRV measurement CTA and empty state when 0 readings exist.

### Test Case 4: 9-Language Localization Parity Audit (`TST-UI-513.4`)
* **Scope**: Localization Parity Test
* **Assertions**: 100% presence and specifier parity of all drawer, tab, and empty state strings across all 9 locales.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-UI-513.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-513.1` | Unit | `NavigationDrawerStateTest`, `AppNavigationDrawerTest` | `REQ-UI-513.1`, `REQ-UI-513.2` | Specified |
| `TST-UI-513.2` | Unit | `PeriodsViewModelTest` | `REQ-UI-513.3` | Specified |
| `TST-UI-513.3` | Contract | `AthleteProfileContractAndLocalizationTest` | `REQ-UI-513.4`, `REQ-UI-513.5` | Specified |
| `TST-UI-513.4` | Localization | `TranslationParityTest` | `REQ-UI-513.6`, `REQ-UI-106` | Specified |
| `TST-UI-513.5` | Regression (Tier 1) | Targeted Navigation & Profile Suite | `REQ-PRO-001` | Specified |
