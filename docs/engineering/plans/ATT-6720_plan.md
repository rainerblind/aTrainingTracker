# Stage 3: Implementation Plan - ATT-6720: Unify Fitness, HRV Readiness, Body/Threshold & Cardio-Power graphs into Fitness & Physiology hub and move Training Zones to Settings

**Ticket**: [ATT-6720](https://rainerblind.atlassian.net/browse/ATT-6720)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-513` (`REQ-UI-513.1`..`REQ-UI-513.6`)  
**Test Mapping**: `TST-UI-513` (`TST-UI-513.1`..`TST-UI-513.5`)  
**Branch**: `feature/ATT-6720`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Longitudinal athlete physiology and recovery charts are currently scattered across Periods (`PeriodsTabsScreen.kt`), Morning HRV (`drawer_morning_hrv`), and Profile & Zones (`AthleteProfileScreen.kt`). This plan details the architectural refactoring to establish a unified 4-tab **Fitness & Physiology** hub with a shared `MetricsTimeRange` calendar X-axis, decouple `HrvReadinessTrendCard` from `PeriodsTabsScreen.kt`, extract **Training Zones** to a dedicated screen in the *Settings* drawer group, and streamline the *Training* drawer group to 4 clean items.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-513` (*Unified Fitness & Physiology Analytical Hub, Training Drawer Streamlining, and Dedicated Settings Training Zones*)
* **Test Mapping**: `TST-UI-513` (*Navigation Drawer, Periods Decoupling, Hub Architecture, and 9-Locale Parity Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **DataStore Zone Persistence**: `SettingsDataStore.edit {}` zone profile persistence and atomic save invariant (`REQ-UI-428`) MUST remain intact.
2. **Deep-Link Compatibility**: Route resolution for `training_zones` and `athlete_profile` deep links MUST NOT be broken.
3. **Database Integrity**: Zero SQLite schema mutations; all existing metric entries and HRV readings remain 100% accessible.
4. **UI Consistency**: Standard header height (`LayoutConstants.HEADER_TITLE_ROW_HEIGHT`), Material 3 color tokens, and 40dp high-density drawer items preserved.

---

## 4. Proposed Architectural Changes
* **Navigation Drawer (`AppNavigationDrawer.kt`)**:
  - Training group reduced to 4 items: `drawer_start_tracking`, `drawer_workouts`, `drawer_periods`, `drawer_athlete_profile` (title `drawer_fitness_physiology`). Standalone `drawer_morning_hrv` retired.
  - Settings group adds `drawer_training_zones` (`ic_speed`, `prefs_training_zones`) following `drawer_sport_types`.
  - Update `isDrawerItemSelected` to decouple `drawer_training_zones` from `drawer_athlete_profile`.
* **Periods Decoupling (`PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`)**:
  - Remove `HrvReadinessTrendCard` and eliminate `HrvReadinessRepository` loading and state flows from `PeriodsViewModel`.
* **Fitness & Physiology Hub (`AthleteProfileScreen.kt`, `AthleteProfileViewModel.kt`)**:
  - Re-architect into 4 tabs: Tab 1 (PMC), Tab 2 (HRV & Readiness with Morning HRV CTA & chronological log), Tab 3 (Body & Thresholds), Tab 4 (Cardio-Power when active).
  - Sync `MetricsTimeRange` across tabs; remove static zone tabs 1-3.
* **Dedicated Training Zones (`ZonesSettingsActivity.kt` / `ZoneSettingsScreen.kt`)**:
  - Host the 3 zone configuration tabs, Zone Calculator, and Zone History dialogs via `NavRoutes.TRAINING_ZONES`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Localization & Strings (`strings.xml` across all 9 locales)**
   - Add/update strings for `drawer_fitness_physiology`, tab headers (`fitness_tab_pmc`, `fitness_tab_hrv`, `fitness_tab_body`, `fitness_tab_cardio_power`), and Tab 2 Morning HRV CTA & empty-state copy.
2. **Step 2: Navigation Drawer & Route Configuration (`AppNavigationDrawer.kt`, `NavRoutes.kt`)**
   - Streamline Training group to 4 items; add Training Zones to Settings group; update `isDrawerItemSelected`.
3. **Step 3: Periods Screen Decoupling (`PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`)**
   - Strip HRV trend card and unused HRV repository dependencies from Periods.
4. **Step 4: Fitness & Physiology Hub Architecture (`AthleteProfileScreen.kt`, `AthleteProfileViewModel.kt`)**
   - Extract zone tabs; construct the 4-tab hub with shared `MetricsTimeRange`, Tab 2 Morning HRV measurement CTA, empty state, and history log.
5. **Step 5: Training Zones Destination Wiring (`ZonesSettingsActivity.kt`, `ATrainingTrackerApp.kt`)**
   - Validate `NavRoutes.TRAINING_ZONES` routes directly to `ZoneSettingsScreen`.
6. **Step 6: Unit & Contract Tests Execution (`docs/tests.md`)**
   - Run targeted test suite: `NavigationDrawerStateTest`, `AppNavigationDrawerTest`, `AthleteProfileContractAndLocalizationTest`, `PeriodsViewModelTest`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`.
* **Rollback**: Discard uncommitted changes on `feature/ATT-6720` via `git restore . && git clean -fd`.
