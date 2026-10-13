# Stage 2: Requirement & Test Specification - ATT-2075: Modernize Permission Flow (Rework Cycle 2: Direct Platform Intents)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2391](https://rainerblind.atlassian.net/browse/ATT-2391) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.16`  
**Requirement Mapping**: `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow*)  
**Test Spec ID**: `TST-PRI-002`  
**Branch**: `feature/ATT-2075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-PRI-003)

### 1.1 Problem Statement & Rationale
Following Sprint 2026-40.15 Review, navigating the athlete to the generic top-level Application Details Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) was rejected because it forced the athlete to manually hunt through nested submenus for location permissions and battery optimization.
In accordance with **ASPICE Rule 21 (Specific Direct Platform Intents Over Generic App Settings)**, the system must target the most specific direct platform intent for each setup step.

### 1.2 Functional & Architectural Requirements

1. **Direct Platform Intent Governance (Rule 21)**:
   - For **Battery Optimization (`RationaleType.BATTERY_OPTIMIZATION`)**:
     - The primary action SHALL ALWAYS dispatch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:$packageName`, presenting the native platform exemption prompt directly.
     - Fallback to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` SHALL only occur if `ActivityNotFoundException` or `SecurityException` is caught on restricted OEM ROMs.
     - Fallback to generic `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` SHALL strictly be the last-resort catch.
   - For **Background Location (`RationaleType.BACKGROUND_LOCATION`)**:
     - On Android 11+ (API 30+), the primary action SHALL ALWAYS trigger the platform background location permission request (`bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)`), which natively prompts the OS dialog leading directly to the location permission selection page ("Allow all the time" / "Immer zulassen").
     - `ACTION_APPLICATION_DETAILS_SETTINGS` SHALL NOT be invoked prematurely on unrequested or first-time background location prompts.
   - For **Foreground Permissions (`RationaleType.FOREGROUND`)**:
     - The primary action SHALL trigger `permissionLauncher.launch(permissionsToRequest)`.
     - `ACTION_APPLICATION_DETAILS_SETTINGS` SHALL ONLY be invoked when foreground permissions are truly permanently denied (after user denial without rationale).
2. **Decoupled Permanent Denial State**:
   - `PermissionRationaleSheet` SHALL evaluate permanent denial contextually per active `RationaleType`, ensuring that battery optimization and fresh background requests are never misclassified as permanently denied.
3. **9-Language Localization & Format Parity**:
   - All rationale copy and button labels across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) SHALL remain 100% synchronized with literal `\n` linebreaks.

### 1.3 Acceptance Criteria (Given-When-Then)

* **AC-1 (Direct Battery Optimization Prompt)**:
  * *Given* battery optimization is active for the app,
  * *When* the athlete taps the primary action on `PermissionRationaleSheet(RationaleType.BATTERY_OPTIMIZATION)`,
  * *Then* `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is launched directly, displaying the native OS confirmation dialog without navigating to generic App Details Settings.

* **AC-2 (Direct Background Location Dispatch)**:
  * *Given* foreground location is granted and background location is missing on Android 11+,
  * *When* the athlete taps the primary action on `PermissionRationaleSheet(RationaleType.BACKGROUND_LOCATION)`,
  * *Then* `bgLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)` is invoked directly, allowing the OS to route the athlete straight to the Location Permission subscreen.

* **AC-3 (Fallback Exception Immunity)**:
  * *Given* an OEM device where `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` throws an exception,
  * *When* the intent is launched,
  * *Then* the app catches the error and cascades safely to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` without crashing.

---

## 2. Test Specification (TST-PRI-002)

### Test Case 1: `testBatteryOptimization_launchesDirectActionRequestIgnoreBatteryOptimizations` (`TST-PRI-002.1`)
* **Scope**: Unit Test / Intent Dispatch Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingPermissionTest.kt`
* **Preconditions**: Battery optimization is not yet ignored (`isIgnoringBatteryOptimizations == false`).
* **Action**: Invoke `launchBatteryOptimizationIntent(context)`.
* **Expected Result**: Verify primary intent action is `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:com.atrainingtracker`.

### Test Case 2: `testPermissionRationaleSheet_batteryOptimizationDoesNotUseOpenSettingsAction` (`TST-PRI-002.2`)
* **Scope**: Composable Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PermissionRationaleSheetContractTest.kt`
* **Action**: Verify that for `RationaleType.BATTERY_OPTIMIZATION`, the primary button triggers `onContinue` (direct intent) rather than `onOpenSettings` (generic app info).

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-PRI-002.3`)
* **Scope**: Localization Parity Test
* **Goal**: Audit all rationale strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Regression Suite (`TST-PRI-002.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the complete application test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRI-002.1` | Unit | `ControlTrackingScreen.launchBatteryOptimizationIntent` | `REQ-PRI-003`, Rule 21 | Specified |
| `TST-PRI-002.2` | Contract | `PermissionRationaleContent` button routing | `REQ-PRI-003`, Rule 21 | Specified |
| `TST-PRI-002.3` | Localization | `TranslationParityTest` | `REQ-PRI-003`, `REQ-LOC-001` | Specified |
| `TST-PRI-002.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
