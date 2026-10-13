# Stage 3: Implementation Plan - ATT-2075: Modernize Permission Flow (Rework Cycle 2: Direct Platform Intents)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2392](https://rainerblind.atlassian.net/browse/ATT-2392) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.16`  
**Requirement Mapping**: `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow*)  
**Test Mapping**: `TST-PRI-002`  
**Branch**: `feature/ATT-2075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

Sprint Review testing on Pixel 10 (Android 16) revealed that tapping setup buttons routed athletes to the top-level generic Application Details screen (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`), forcing athletes to click through nested submenus to configure permissions and battery optimizations.
In compliance with **ASPICE Rule 21**, this implementation plan details the atomic construction sequence to restore direct platform intents:
1. `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for battery optimization exemption.
2. `bgLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)` for direct OS background location escalation.
3. Strict containment of `ACTION_APPLICATION_DETAILS_SETTINGS` as an exceptional fallback only.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling*)
* **Test Mapping**: `TST-PRI-002` (Unit, Composable Contract, Localization Parity, Full Suite Regression)
* **Governance**: ASPICE Rule 21 (Specific Direct Platform Intents Over Generic App Settings)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing sensor grid, tracking, and export features continue to function without degradation.
2. **Cold-Start Decoupling (`REQ-STB-008`)**: `MainActivityWithNavigation.onCreate()` remains free of modal dialogs.
3. **Android 11+ Two-Step Cascade**: Background location is never requested simultaneously with foreground location.
4. **Human Gate Invariance (Rule 1)**: Moving ATT-2075 to `Erledigt` is reserved for human review; agent terminates at `Final Review (Human)`.
5. **No Version Assignment on Subtasks (Rule 6 & Rule 19)**: Subtasks have empty `fixVersions`; parent fixVersion is deferred until final acceptance.

---

## 4. Proposed Architectural Changes

### Component 1: `ControlTrackingScreen.kt`
* **Step-Specific Denial Evaluation**:
  - Replace the single global `isPermanentlyDenied` with step-specific logic:
    - `FOREGROUND`: Evaluates whether missing foreground permissions (`ACCESS_FINE_LOCATION`, etc.) were denied without rationale.
    - `BACKGROUND_LOCATION`: Only marks permanently denied if background permission was specifically denied after prompt.
    - `BATTERY_OPTIMIZATION`: Never permanently denied (always direct intent).
* **Battery Optimization Dispatch Clean-up**:
  - Ensure `launchBatteryOptimizationIntent` is triggered without immediate premature `onStart()`, allowing the athlete to review the platform dialog and returning gracefully via `ON_RESUME`.

### Component 2: `PermissionRationaleSheet.kt`
* **Action Button Disentanglement**:
  - Ensure `onContinue` is invoked when `isPermanentlyDenied == false` for each specific step.
  - When in `RationaleType.BATTERY_OPTIMIZATION`, the button always offers the direct continue action (*"Akku-Optimierung anpassen"* / *"Continue"*) mapping directly to `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Decouple Permanent Denial & Recalibrate Intent Dispatch in `ControlTrackingScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`
* Changes:
  - Separate `isPermanentlyDenied` calculation per active `rationaleStep`.
  - Ensure `RationaleStep.BACKGROUND_LOCATION` invokes `bgLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)`.
  - Ensure `RationaleStep.BATTERY_OPTIMIZATION` invokes `launchBatteryOptimizationIntent(context)`.

### Step 2: Validate Direct Fallback Hierarchy in `launchBatteryOptimizationIntent`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`
* Changes:
  - Tier 1: `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:$packageName`.
  - Tier 2 (Catch `ActivityNotFoundException` / `SecurityException`): `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
  - Tier 3 (Last resort fallback per Rule 21): `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.

### Step 3: Align `PermissionRationaleSheet.kt` Button Logic
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PermissionRationaleSheet.kt`
* Changes:
  - Ensure button label and click routing accurately reflect direct intent availability for each rationale type.

### Step 4: Update Unit & Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingPermissionTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PermissionRationaleSheetContractTest.kt`
* Changes:
  - Add tests validating direct intent generation and button actions for battery optimization and background location.
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit tests during Stage 4 construction, followed by full regression `./gradlew testDebugUnitTest` and APK assembly `./gradlew assembleDebug` in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-2075` permits clean revert to `sprint/2026-40.16` if regressions arise.
