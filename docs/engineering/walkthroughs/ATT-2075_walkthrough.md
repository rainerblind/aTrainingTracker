# Stage 5: Walkthrough & Verification - ATT-2075: Modernize Permission Flow (Rework Cycle 2: Direct Platform Intents)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2394](https://rainerblind.atlassian.net/browse/ATT-2394) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `2026-40.16`  
**Requirement Mapping**: `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow*)  
**Test Spec ID**: `TST-PRI-002`  
**Branch**: `feature/ATT-2075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the full verification and release qualification for [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075) (Modernize Permission Flow Rework Cycle 2: Direct Platform Intents).

### Problem Statement & Root Cause
In Sprint 2026-40.15 Joint Review, the previous implementation was rejected because:
1. On fresh installations and unrequested permission states, `isPermanentlyDenied = !shouldShowRequestPermissionRationale && !hasLocationPermission` evaluated to `true` (since `shouldShowRequestPermissionRationale` defaults to `false` prior to the first user denial).
2. Consequently, `PermissionRationaleSheet` immediately rendered the "Open Settings" button, which routed clicks directly to generic Application Details Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`).
3. This forced athletes to manually hunt through nested OS settings menus for location permissions and battery optimization, violating **ASPICE Rule 21 (Specific Direct Platform Intents Over Generic App Settings)**.
4. Furthermore, battery optimization was treated as permanently denied when location permissions were missing, bypassing direct OS prompts.

### Implemented Solution
1. **Unprompted State Tracking**: Introduced SharedPreferences tracking (`pref_has_requested_foreground_perms`, `pref_has_requested_bg_perms`) in `ControlTrackingScreen.kt`. If permissions have never been requested, `isPermanentlyDenied` is strictly guaranteed to be `false`.
2. **Specific Direct Platform Intents (Rule 21)**:
   - For **Battery Optimization** (`RationaleType.BATTERY_OPTIMIZATION`), the primary action ALWAYS dispatches `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:$packageName`, displaying the native system whitelist confirmation dialog directly.
   - For **Background Location** (`RationaleType.BACKGROUND_LOCATION`), the system directly invokes the platform launcher (`bgLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)`), presenting the OS permission dialog leading directly to the location permission selection page ("Allow all the time" / "Immer zulassen").
   - Generic Application Details Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) is reserved strictly for genuine permanent denial after the athlete has explicitly denied permissions.
3. **Reactive Resume Handling**: Deferred premature `onStart()` execution in the battery optimization sheet callback, allowing `ON_RESUME` lifecycle detection to reactively evaluate the granted state.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRI-003` | `TST-PRI-002.1` | Unit Test: `testFirstTimePermissionCheck_doesNotTreatAsPermanentlyDenied` | **PASSED** | `Verified` |
| `REQ-PRI-003` | `TST-PRI-002.2` | Unit Test: `testBatteryOptimization_launchesDirectActionRequestIgnoreBatteryOptimizations` | **PASSED** | `Verified` |
| `REQ-PRI-003` | `TST-PRI-002.3` | Unit Test: `testPermanentlyDenied_showsOpenSettingsButton` | **PASSED** | `Verified` |
| `REQ-PRI-003` | `TST-PRI-002.4` | Clean-Room Targeted Suite: `com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*` | **PASSED** (26/26 tests) | `Verified` |
| `REQ-PRO-001` | `TST-PRI-002.5` | Clean-Room Full Suite Regression: `./gradlew testDebugUnitTest` | **PASSED** (100% clean) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests (`com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*`)
```text
BUILD SUCCESSFUL in 2m 41s
32 actionable tasks: 32 executed
- ControlTrackingPermissionTest:
  • testFirstTimePermissionCheck_doesNotTreatAsPermanentlyDenied: PASSED
  • testBatteryOptimization_launchesDirectActionRequestIgnoreBatteryOptimizations: PASSED
  • testPermanentlyDenied_showsOpenSettingsButton: PASSED
  • testStartTracking_whenPermissionsMissing_triggersRationale: PASSED
  • testForegroundLocationGranted_escalatesToBackgroundRationale: PASSED
  • testBackgroundLocationGranted_escalatesToBatteryOptimizationRationale: PASSED
```

---

## 4. Invariant & Governance Verification

1. **Rule 21 Compliance**: Specific direct platform intents (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `ACCESS_BACKGROUND_LOCATION`) are prioritized over generic `ACTION_APPLICATION_DETAILS_SETTINGS`.
2. **Rule 1 Compliance**: Parent ticket `ATT-2075` is transitioned to `Final Review (Human)` (never `Erledigt`).
3. **Rule 6 Compliance**: Sub-tasks `ATT-2390`, `ATT-2391`, `ATT-2392`, `ATT-2393`, `ATT-2394` have empty `fixVersions`.
4. **Living Documentation Synchronized**: `docs/requirements.md` (`REQ-PRI-003`) and `docs/tests.md` (`TST-PRI-002`) confirmed in state `Verified`.
5. **Continuous Sprint Branch Integration (Strategy A)**: Branch `feature/ATT-2075` merged into `sprint/2026-40.16` via `--no-ff`.
