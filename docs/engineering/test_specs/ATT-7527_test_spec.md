# Stage 2: Requirement & Test Specification - ATT-7527: Remove Guided DFA a1 Test Button from Start Tracking & HRV Readiness Screens and Refine Icon

**Ticket**: [ATT-7527](https://rainerblind.atlassian.net/browse/ATT-7527) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5` | **Branch**: `feature/ATT-7527`  
**Requirement Mapping**: `REQ-TRK-072` (`REQ-TRK-072.7a/b/c`), `REQ-TRK-061.4`  
**Test Spec ID**: `TST-TRK-072` (`TST-TRK-072.9a/b/c/d`) | **Author**: AI Agent 2 (Flash) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-TRK-072)

### 1.1 Problem Statement & Rationale
The Guided DFA $\alpha_1$ Threshold Test launcher button currently clutters everyday tracking starts (`TrackingTabsScreen.kt`, `hrv_test_cockpit_launcher`), conflates resting morning readiness checks with exercise threshold ramp protocols (`AthleteProfileScreen.kt`, `btn_guided_hrv_test`), and renders an unrepresentative weightlifting dumbbell icon (`Icons.Default.FitnessCenter`) in Training Zones (`ZonesSettingsActivity.kt`). Launcher access should be consolidated into the dedicated Training Zones `HRV (DFA a1)` tab with a progressive cardio vector icon.

### 1.2 Attributes & Functional Requirements
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
1. `TrackingTabsScreen.kt` (`ControlTrackingScreen`'s `bottomContent`) SHALL omit the Guided DFA a1 Threshold Test button (`hrv_test_cockpit_launcher`), displaying only `RouteSelectionButton` and optional Morning HRV check (`REQ-TRK-072.7a`).
2. `AthleteProfileScreen.kt` (HRV tab) SHALL omit the Guided DFA a1 Threshold Test button (`btn_guided_hrv_test`), separating resting Morning Readiness from exercise threshold testing (`REQ-TRK-072.7b`).
3. `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent`) SHALL serve as the dedicated launcher for the Guided DFA a1 Threshold Test (`hrv_start_guided_ramp_test`), rendering with leading icon `Icons.AutoMirrored.Filled.TrendingUp` instead of `Icons.Default.FitnessCenter` (`REQ-TRK-072.7c`).
4. Host navigation route `NavRoutes.HRV_TEST` SHALL remain mounted in `ATrainingTrackerApp.kt` and reachable via Training Zones (`REQ-TRK-072.7d`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (Start Tracking Clean Bottom Content)**: *Given* `TrackingTabsScreen.kt`, *When* `ControlTrackingScreen` renders, *Then* `hrv_test_cockpit_launcher` is absent from `bottomContent`.
* **AC2 (Athlete Profile Clean Resting Tab)**: *Given* `AthleteProfileScreen.kt`, *When* the HRV Readiness tab displays, *Then* `btn_guided_hrv_test` is absent.
* **AC3 (Training Zones Icon Refinement)**: *Given* `ZonesSettingsActivity.kt`, *When* viewing `HrvDfaA1SettingsContent`, *Then* `hrv_start_guided_ramp_test` displays `Icons.AutoMirrored.Filled.TrendingUp`.
* **AC4 (Host Route Mounting)**: *Given* `ATrainingTrackerApp.kt`, *When* navigating from `ZoneSettingsScreen`, *Then* `NavRoutes.HRV_TEST` is reachable.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-072` / `REQ-TRK-061.4`, targeting `TrackingTabsScreen.kt`, `AthleteProfileScreen.kt`, `ZonesSettingsActivity.kt`, and `ATrainingTrackerApp.kt`.
* **Historical Origin & Commit Trace**: Added in `ATT-4969` (commit `424580a2`) and `ATT-7317` (commit `fce6777b`).
* **Root Reason for Existing Formulation**: Early discovery required prominent buttons across everyday workout screens. Once the dedicated Training Zones tab was created in ATT-7317, these duplicated buttons became UI clutter.
* **Preservation of Core Invariants**: Host navigation reachability (`NavRoutes.HRV_TEST`) is preserved via the authoritative Training Zones entry point; resting morning readiness and everyday workout tracking are preserved with zero regression.

---

## 2. Test Specification (TST-TRK-072)
* `TST-TRK-072.9a` (Unit, `TrackingTabsScreenTest.kt`): Verify absence of `hrv_test_cockpit_launcher` in `ControlTrackingScreen`.
* `TST-TRK-072.9b` (Unit, `AthleteProfileScreenTest.kt`): Verify absence of `btn_guided_hrv_test` in Athlete Profile HRV tab.
* `TST-TRK-072.9c` (Unit, `HrvRampTestScreenTest.kt`): Verify `ZonesSettingsActivity.kt` renders `Icons.AutoMirrored.Filled.TrendingUp` for `hrv_start_guided_ramp_test`.
* `TST-TRK-072.9d` (Unit, `HrvRampTestScreenTest.kt`): Verify `NavRoutes.HRV_TEST` host route mounting in `ATrainingTrackerApp.kt`.
* `TST-TRK-072.8` (Tier 1 Regression): Targeted unit and resource suites pass with 100% success rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-072.9a` | Unit | `TrackingTabsScreenTest.kt` | `REQ-TRK-072.7a` | Specified |
| `TST-TRK-072.9b` | Unit | `AthleteProfileScreenTest.kt` | `REQ-TRK-072.7b` | Specified |
| `TST-TRK-072.9c` | Unit | `HrvRampTestScreenTest.kt` | `REQ-TRK-072.7c` | Specified |
| `TST-TRK-072.9d` | Unit | `HrvRampTestScreenTest.kt` | `REQ-TRK-072.7d` | Specified |
| `TST-TRK-072.8` | Regression | Targeted test suites | `REQ-PRO-001` | Specified |
