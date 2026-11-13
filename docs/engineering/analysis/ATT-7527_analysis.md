# Stage 1 Analysis: ATT-7527 - Remove Guided DFA a1 Test Button from Start Tracking & HRV Readiness Screens and Refine Icon

**Ticket**: [ATT-7527](https://rainerblind.atlassian.net/browse/ATT-7527)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7527` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
The **Guided DFA a1 Threshold Test** launcher button currently appears in three disjoint screens:
1. **Start Tracking Screen (`TrackingTabsScreen.kt#L1022-L1038`)**: Displayed permanently below `RouteSelectionButton` (`hrv_test_cockpit_launcher`), cluttering every everyday workout launch.
2. **Athlete Profile -> HRV Readiness Tab (`AthleteProfileScreen.kt#L358-L377`)**: Displayed directly below Morning Readiness CTA (`btn_guided_hrv_test`), conflating a resting morning recovery check with a strenuous workout threshold test.
3. **Training Zones -> HRV Tab (`ZonesSettingsActivity.kt#L1391-L1405`)**: Uses a weightlifting dumbbell icon (`Icons.Default.FitnessCenter`) instead of a cardio/ramp progression icon.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Redundant Entry Points)**: During ATT-4969 rollout, launcher buttons were added across multiple screens for discoverability. With ATT-7317 introducing the dedicated **Training Zones -> HRV (DFA a1)** tab, threshold calibration belongs exclusively in Training Zones settings.
* **RCA 2 (Mismatched Vector Icon)**: `Icons.Default.FitnessCenter` was an interim icon in ATT-7317; `Icons.AutoMirrored.Filled.TrendingUp` accurately represents aerobic/anaerobic ramp step progression.
* **Independent Peer Review & Verification**:
  * Verified `TrackingTabsScreen.kt#L1022-L1038`: button is permanently rendered in `bottomContent` whenever tracking controls are shown.
  * Verified `AthleteProfileScreen.kt#L358-L377`: `btn_guided_hrv_test` sits inside the HRV readiness tab list.
  * Verified `ZonesSettingsActivity.kt#L1391-L1405`: `onOpenHrvTest` is correctly wired to `NavRoutes.HRV_TEST` via `ZoneSettingsScreen` in `ATrainingTrackerApp.kt#L622`.
  * Verified complexity: 2 SP (pure UI pruning & icon refinement, zero database or service impact).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Remove `hrv_test_cockpit_launcher` from `ControlTrackingScreen`'s `bottomContent` in `TrackingTabsScreen.kt`.
  2. Remove `btn_guided_hrv_test` from the HRV tab in `AthleteProfileScreen.kt`.
  3. Clean up obsolete `onNavigateToHrvTest` arguments from `TrackingTabsScreen` and `AthleteProfileScreen` call sites.
  4. Replace `Icons.Default.FitnessCenter` with `Icons.AutoMirrored.Filled.TrendingUp` in `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent`).
  5. Update and verify unit tests (`HrvRampTestScreenTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`).
* **Out-of-Scope Non-Goals**:
  * Modifying `HrvRampTestScreen.kt` or `HrvRampTestViewModel.kt` test logic.
  * Modifying morning readiness measurements or database models.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-TRK-072` (Guided Ramp Test Screen) and `REQ-TRK-061.4` (Training Zones HRV Tab).
* **Historical Origin & Commit Trace**: Added in `ATT-4969` (commits `424580a2`, `f1408eb5`) and `ATT-7317` (commit `fce6777b`).
* **Root Reason for Existing Formulation**: Early discovery required prominent buttons on primary screens.
* **Preservation of Core Invariants**: Consolidating the launcher into `ZonesSettingsActivity.kt` preserves Rule 81 host reachability via `NavRoutes.HRV_TEST` while decluttering tracking and resting readiness screens.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-072`, `REQ-TRK-061.4`.
* **Affected Architecture Elements**: `TrackingTabsScreen.kt`, `AthleteProfileScreen.kt`, `ZonesSettingsActivity.kt`, `ATrainingTrackerApp.kt`.
* **Verification Impact & Test Surface**: `HrvRampTestScreenTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`, targeted UI test suites.
* **Risk Assessment & Invariants**: Low risk. Navigation route `NavRoutes.HRV_TEST` remains intact and accessible from Training Zones.

---

## 6. Architectural Strategy & High-Level Solution
1. **`TrackingTabsScreen.kt` & `ATrainingTrackerApp.kt`**: Prune `hrv_test_cockpit_launcher` button and remove `onNavigateToHrvTest` parameter from `TrackingTabsScreen`.
2. **`AthleteProfileScreen.kt`**: Remove `btn_guided_hrv_test` item and prune `onNavigateToHrvTest` parameter.
3. **`ZonesSettingsActivity.kt`**: Update `Icons.Default.FitnessCenter` to `Icons.AutoMirrored.Filled.TrendingUp` in `HrvDfaA1SettingsContent`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `NavRoutes.HRV_TEST` remains mounted in `ATrainingTrackerApp.kt` and reachable via `ZonesSettingsActivity`.
  2. Zero regression in workout tracking or morning HRV measurement flows.
  3. 100% test pass rate across targeted UI suites.
* **Risk Rating**: **LOW** (Safe UI decluttering and icon polish).
