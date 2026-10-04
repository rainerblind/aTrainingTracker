# Stage 3 Implementation Plan: ATT-2075 - Modernize Permission Flow: Contextual Just-in-Time Prompts & Graceful Settings Return Handling (Rework Cycle)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2379](https://rainerblind.atlassian.net/browse/ATT-2379) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Design & Component Decomposition (SWE.2)

```
                                  +---------------------------------------+
                                  |    ControlTrackingScreen.kt (UI)      |
                                  | - handleStartClick                    |
                                  | - DisposableEffect(ON_RESUME)         |
                                  | - Progressive RationaleStep State     |
                                  +---------------------------------------+
                                         |                         |
               +-------------------------+                         +--------------------------+
               |                                                                              |
               v                                                                              v
+-------------------------------+                                              +-------------------------------+
| PermissionRationaleSheet.kt   |                                              | ControlTrackingButton.kt      |
| - RationaleType.FOREGROUND    |                                              | - hasPermissionWarning:       |
| - RationaleType.BACKGROUND    |                                              |   !hasLocationPermission      |
| - RationaleType.BATTERY       |                                              | - Warning Badge (Amber)       |
+-------------------------------+                                              +-------------------------------+
               |
               v
+-------------------------------------------------------------------------------------------------------------+
| Android Platform Intents & Contracts:                                                                       |
| 1. ActivityResultContracts.RequestMultiplePermissions (Fine/Coarse Location, BLE, Notifications)           |
| 2. ActivityResultContracts.RequestPermission (ACCESS_BACKGROUND_LOCATION on API 29/30+)                      |
| 3. Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (with fail-safe cascading to Settings fallback)      |
+-------------------------------------------------------------------------------------------------------------+
```

### 1.1 State Machine: Progressive Setup Steps
```kotlin
enum class RationaleStep {
    NONE,
    FOREGROUND,
    BACKGROUND_LOCATION,
    BATTERY_OPTIMIZATION
}
```
* **Step Progression**:
  1. If `!checkHasLocation()`: `rationaleStep = RationaleStep.FOREGROUND`
  2. Else if `!checkHasBackgroundLocation()` (API 29+): `rationaleStep = RationaleStep.BACKGROUND_LOCATION`
  3. Else if `!checkIsIgnoringBatteryOptimizations()` (API 23+): `rationaleStep = RationaleStep.BATTERY_OPTIMIZATION`
  4. Else: `onStart()`

---

## 2. Step-by-Step Atomic Construction Sequence

### Step 1: Extend `PermissionRationaleSheet.kt` with Progressive Variants
* Define `enum class RationaleType { FOREGROUND, BACKGROUND_LOCATION, BATTERY_OPTIMIZATION }`.
* Enhance `PermissionRationaleSheet` and `PermissionRationaleContent` to accept `rationaleType: RationaleType`.
* Render appropriate titles, descriptions, and icons:
  * `FOREGROUND`: `R.string.permission_rationale_title`, 3 value cards (GPS, Bluetooth, Notifications).
  * `BACKGROUND_LOCATION`: `R.string.background_location_permission_title`, `R.string.background_location_permission_text`, `R.drawable.my_locations`.
  * `BATTERY_OPTIMIZATION`: `R.string.battery_optimization_title`, `R.string.battery_optimization_text`, `R.drawable.battery_full`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PermissionRationaleSheet.kt`

### Step 2: Implement Fail-Safe OEM Battery Optimization Dispatcher
* In a dedicated utility function or companion object in `ControlTrackingScreen.kt`:
  ```kotlin
  fun launchBatteryOptimizationIntent(context: Context) {
      try {
          val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
              data = Uri.parse("package:${context.packageName}")
          }
          context.startActivity(intent)
      } catch (e: Exception) {
          try {
              val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
              context.startActivity(fallback)
          } catch (e2: Exception) {
              val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
              }
              context.startActivity(details)
          }
      }
  }
  ```
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`

### Step 3: Implement Progressive State Machine & Dedicated Launchers in `ControlTrackingScreen.kt`
* Add `var rationaleStep by rememberSaveable { mutableStateOf(RationaleStep.NONE) }`.
* Add `bgLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> ... }`.
* Update `permissionLauncher` (foreground): on grant, transition to `checkHasBackgroundLocation()` or `checkIsIgnoringBatteryOptimizations()`.
* Update `handleStartClick` to evaluate the 3-step sequence.
* In `DisposableEffect(lifecycleOwner)` on `Lifecycle.Event.ON_RESUME`:
  * Re-evaluate `checkHasLocation()`, `checkHasBackgroundLocation()`, `checkIsIgnoringBatteryOptimizations()`.
  * Update `hasLocationPermission`.
  * If the active rationale step has been satisfied, advance to next step or dismiss.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`

### Step 4: Update Unit & Contract Tests
* Update `ControlTrackingPermissionTest.kt`:
  * Verify progressive step transitions: Foreground -> Background -> Battery Optimization -> `onStart()`.
  * Verify fail-safe battery intent execution without throwing.
  * Verify `ON_RESUME` lifecycle observer updates state reactively.
* Update `PermissionRationaleSheetContractTest.kt`:
  * Verify UI content rendering for `FOREGROUND`, `BACKGROUND_LOCATION`, and `BATTERY_OPTIMIZATION`.
  * Audit all 9 locales for string presence.
* Run targeted tests:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*"`

---

## 3. Invariant Protection & Verification Mapping

* **Invariant 1 (Cold Start Cleanliness)**: `MainActivityWithNavigation.onCreate()` remains 100% free of modal dialogs.
* **Invariant 2 (Android 11+ Two-Step Cascade)**: Background location is NEVER requested simultaneously with foreground location.
* **Invariant 3 (OEM Crash Immunity)**: Battery intent launch wrapped in 3-tier try-catch.
* **Invariant 4 (Localization Parity)**: 100% parity across all 9 languages.
* **Invariant 5 (Graceful Refusal)**: Athletes can dismiss rationales ("Not now") and start tracking or configure sensors without app lockups.
