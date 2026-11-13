# Stage 3: Implementation Plan - ATT-7527: Consolidate Guided DFA a1 Launcher into Training Zones

**Ticket**: [ATT-7527](https://rainerblind.atlassian.net/browse/ATT-7527) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5` | **Branch**: `feature/ATT-7527`  
**Requirement Mapping**: `REQ-TRK-072` (`REQ-TRK-072.7a/b/c/d`), `REQ-TRK-061.4`  
**Test Mapping**: `TST-TRK-072` (`TST-TRK-072.9a/b/c/d`, `TST-TRK-072.8`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
Guided DFA $\alpha_1$ Threshold Test launcher buttons currently clutter the everyday workout start screen (`TrackingTabsScreen.kt`, `hrv_test_cockpit_launcher`) and conflate resting morning readiness checks with exercise testing in Athlete Profile (`AthleteProfileScreen.kt`, `btn_guided_hrv_test`). Furthermore, the dedicated launcher in Training Zones (`ZonesSettingsActivity.kt`) uses a weightlifting dumbbell icon (`Icons.Default.FitnessCenter`). Launcher access should be consolidated into Training Zones (`HRV (DFA a1)`) with an intuitive progressive cardio vector icon.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-072.7a**: Prune `hrv_test_cockpit_launcher` from `ControlTrackingScreen`'s `bottomContent` $\rightarrow$ `TST-TRK-072.9a`.
* **REQ-TRK-072.7b**: Prune `btn_guided_hrv_test` from `AthleteProfileScreen.kt` HRV tab $\rightarrow$ `TST-TRK-072.9b`.
* **REQ-TRK-072.7c**: Replace `Icons.Default.FitnessCenter` with `Icons.AutoMirrored.Filled.TrendingUp` in `ZonesSettingsActivity.kt` $\rightarrow$ `TST-TRK-072.9c`.
* **REQ-TRK-072.7d / REQ-TRK-061.4**: Preserve `NavRoutes.HRV_TEST` host route mounting in `ATrainingTrackerApp.kt` $\rightarrow$ `TST-TRK-072.9d`.

---

## 3. System Invariants & Preserved Behavior
1. **Rule 81 Host Reachability**: `NavRoutes.HRV_TEST` remains mounted in `ATrainingTrackerApp.kt` and accessible via `ZoneSettingsScreen`.
2. **Zero Functional Regressions**: Workout tracking controls and morning readiness measurement flows remain fully operational.
3. **Design Guidelines Compliance**: Consistent button shapes (`RoundedCornerShape(8.dp)`), Material 3 auto-mirrored vector icons, and clean layout hierarchy.

---

## 4. Proposed Architectural Changes

### Component 1: Start Tracking Cockpit (`TrackingTabsScreen.kt` & `ATrainingTrackerApp.kt`)
* Remove the `OutlinedButton` with `testTag("hrv_test_cockpit_launcher")` from `ControlTrackingScreen`'s `bottomContent`.
* Prune `onNavigateToHrvTest` parameter from `TrackingTabsScreen` signature and its call site in `ATrainingTrackerApp.kt`.

### Component 2: Athlete Profile Screen (`AthleteProfileScreen.kt` & `ATrainingTrackerApp.kt`)
* Remove item 1b (`btn_guided_hrv_test`) from `AthleteProfileScreen.kt` HRV tab.
* Prune `onNavigateToHrvTest` parameter from `AthleteProfileScreen` signature and its call site in `ATrainingTrackerApp.kt`.

### Component 3: Training Zones Activity (`ZonesSettingsActivity.kt`)
* In `HrvDfaA1SettingsContent`: replace `Icons.Default.FitnessCenter` with `Icons.AutoMirrored.Filled.TrendingUp` on the `hrv_start_guided_ramp_test` button.

### UI Consistency (`docs/design_guidelines.md` §1 & §5)
* **Reused Components**: Material 3 `OutlinedButton`, `Icons.AutoMirrored.Filled.TrendingUp`, `RoundedCornerShape(8.dp)`.
* **Layout Hierarchy**: Preserves clean bottom content in tracking and uncluttered list in resting morning readiness.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Prune Tracking Cockpit Launcher (`TrackingTabsScreen.kt`, `ATrainingTrackerApp.kt`)
* Remove `hrv_test_cockpit_launcher` button; remove `onNavigateToHrvTest` parameter.

### Step 2: Prune Athlete Profile Launcher (`AthleteProfileScreen.kt`, `ATrainingTrackerApp.kt`)
* Remove `btn_guided_hrv_test` item; remove `onNavigateToHrvTest` parameter.

### Step 3: Refine Icon in Training Zones (`ZonesSettingsActivity.kt`)
* Update icon to `Icons.AutoMirrored.Filled.TrendingUp` in `HrvDfaA1SettingsContent`.

### Step 4: Update Unit & Contract Tests
* Update `HrvRampTestScreenTest.kt` to verify launcher reachability through `ZoneSettingsScreen`.
* Add assertions confirming absence of removed test tags.
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests pass with 100% success rate; `verify_requirement_governance.py` passes.
* **Rollback**: Clean revert via `git reset --hard feature/ATT-7527`.
