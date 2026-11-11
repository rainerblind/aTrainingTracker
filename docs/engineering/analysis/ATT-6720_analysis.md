# Stage 1 Analysis: ATT-6720 - Unify Fitness, HRV Readiness, Body/Threshold & Cardio-Power graphs into Fitness & Physiology hub and move Training Zones to Settings

**Ticket**: [ATT-6720](https://issue-tracker/ATT-6720)  
**Parent Epic**: [ATT-355](https://issue-tracker/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6720`  
**Author**: AI Agent 1 (Implementer - Pro)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During Sprint 2026-46.1 review (`ATT-3557`), longitudinal athlete physiology and fitness charts became fragmented across three separate navigation destinations while mixing static configuration with longitudinal time-series analytics:
1. **Periods (`PeriodsTabsScreen.kt`)**: Intended strictly for calendar training volume buckets (`Days / Weeks / Months / Years`), yet `HrvReadinessTrendCard` was placed directly above `PeriodBarGraph`.
2. **Morning HRV (`drawer_morning_hrv`)**: Added as a standalone top-level item in the *Training* drawer group solely for the 60-120s morning check.
3. **Profile & Zones (`AthleteProfileScreen.kt`)**: Stacks 3 static zone settings tabs alongside a congested Tab 0 hosting three separate chart families (`Weight/FTP/CP`, `PMC`, and `Cardio-Power`) plus manual logs.

**Target**: Establish a unified analytical hub **Fitness & Physiology (*Fitness & Physiologie*)** with 4 tabs sharing a `MetricsTimeRange` X-axis (`PMC`, `HRV & Readiness`, `Body & Thresholds`, `Cardio-Power`), restore **Training Zones (*Trainingszonen*)** to the *Settings* drawer group, and streamline the *Training* drawer group to 4 clean items.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Call-Site Audit**:
    * `AppNavigationDrawer.kt`: Training section contains 5 items; `drawer_morning_hrv` is redundant once embedded in Tab 2; Settings section lacks `drawer_training_zones`.
    * `PeriodsTabsScreen.kt` (L208-219) & `PeriodsViewModel.kt`: `HrvReadinessTrendCard` and `hrvReadinessRepo` dependency contaminate pure calendar volume analysis.
    * `AthleteProfileScreen.kt`: Tab 0 stacks 3 separate chart cards; Tabs 1-3 host `ZoneProfileTabContent`.
    * `ZonesSettingsActivity.kt` / `ZoneSettingsScreen.kt`: Already implements a dedicated 3-tab zone screen with calculator and history dialogs, perfectly matching `NavRoutes.TRAINING_ZONES`.
    * `HrvReadinessRepository.kt`: Exposes `deleteEntry(id)` and `getAllEntries()`, ready to power Tab 2 chronological log with delete support.
  * **Hypothesis Validated**: Restructuring improves information architecture without changing underlying calculations or database models.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Clean `PeriodsTabsScreen.kt` / `PeriodsViewModel.kt`: Remove `HrvReadinessTrendCard` and HRV data loading.
  * Extract Training Zones from `AthleteProfileScreen.kt` to `ZoneSettingsScreen.kt` / `TrainingZonesScreen.kt` and mount `drawer_training_zones` in the *Settings* drawer section.
  * Rebuild `FitnessPhysiologyScreen.kt` (aliasing `AthleteProfileScreen.kt`) with 4 tabs:
    * Tab 1: Fitness & Fatigue (`PMC: CTL / ATL / TSB / ACWR` + Ramp Rate summary tiles + `PerformanceManagementChartCard`).
    * Tab 2: HRV & Readiness ("Measure Morning HRV" CTA + empty-state onboarding card + `HrvReadinessTrendCard` + chronological log with delete).
    * Tab 3: Body & Thresholds (KPI tiles + `AthleteMetricTimeSeriesChart` + add entry CTA + filterable history log).
    * Tab 4: Cardio-Power (`CardioPowerTrendsCard`, conditional on `enableCardioPowerDynamics`).
  * Harmonize `MetricsTimeRange` calendar X-axis across longitudinal charts.
  * Streamline *Training* drawer section in `AppNavigationDrawer.kt` to 4 items and update `isDrawerItemSelected` selection aliases.
  * 9-language localization parity across all strings.
* **Out-of-Scope Non-Goals**:
  * Altering mathematical algorithms in `PowerPhysiologyCalculator`, `HrvReadinessCalculator`, or `CardioPowerCalculator`.
  * Redesigning `MorningHrvCheckScreen` sensor acquisition pipeline.
  * SQLite schema alterations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-397` (*Profile & Zones Consolidation*), `REQ-PER-033` (*Longitudinal HRV in Periods*), `REQ-UI-405` (*Training Drawer Order*). Superseded/amended by net-new **`REQ-UI-492`**.
* **Historical Origin & Commit Trace**: Sprints 2026-42.7 (`ATT-3851`) and 2026-46.1 (`ATT-3557`).
* **Root Reason for Existing Formulation**: Zones were grouped with Athlete Profile as an interim consolidation before dedicated Settings integration; HRV trend was added to Periods as a rapid surface before a dedicated physiology hub was designed.
* **Preservation of Core Invariants**: All zone calculation and editing capabilities preserved; all historical athlete metric entries and HRV readings remain 100% accessible; backward-compatible route mappings (`NavRoutes.ATHLETE_PROFILE`, `NavRoutes.TRAINING_ZONES`) maintained.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**:
  * `SettingsDataStore` zone persistence and atomic editing MUST remain intact.
  * Deep-link and navigation compatibility for `training_zones` and `athlete_profile` MUST NOT break.
  * Navigation drawer 40dp high-density layout and WCAG AA contrast MUST be maintained.
* **Risk Rating**: **LOW** (pure UI/navigation information architecture refactoring; repository, calculation, and database layers remain untouched).
