# Stage 3: Implementation Plan - ATT-3451: Use warning colors instead of blue for sensor low battery snackbar alerts

**Ticket**: [ATT-3451](https://atrainingtracker.atlassian.net/browse/ATT-3451)  
**Sub-task**: [ATT-3464](https://atrainingtracker.atlassian.net/browse/ATT-3464) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-356` (*Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance & Semantic Severity Color Tiering*)  
**Test Mapping**: `TST-UI-316`  
**Branch**: `feature/ATT-3451`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt`, peripheral sensor low battery alert snackbars currently render with default blue primary theme colors (`primaryContainer`, `onPrimaryContainer`, `primary`).

This presents two major UI/UX defects:
1. **Semantic Color Misalignment**: Blue is used for normal informational status and route lines; using it for battery exhaustion warnings dilutes visual urgency and violates Material 3 semantic conventions.
2. **Absence of Severity Tiering**: Moderate battery depletion ($11\%\dots 20\%$) and critical battery depletion ($\le 10\%$, imminent hardware failure mid-workout) are styled identically, leaving the athlete unaware of how urgently the sensor requires replacement or recharging.

This plan details the construction of a two-tier semantic alert architecture:
* **Warning Tier ($> 10\%$ and $\le 20\%$)**: Dedicated warm amber container with high-contrast brown/dark text and action button ($\ge 7:1$ contrast ratio).
* **Critical Tier ($\le 10\%$)**: Red/error container (`errorContainer`, `onErrorContainer`, `error`).
* **Instantaneous Preemption**: If a `CRITICAL` alert arrives while a `WARNING` snackbar is currently displayed, the active snackbar is immediately dismissed to render the critical alert without delay.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-356`
  - Clause 1: Workout Start Evaluation & Severity Tiering ($\le 10\%$ Critical, $11\dots 20\%$ Warning).
  - Clause 2: In-Ride Real-Time Battery Drop & Escalation Evaluation (tracks warning alerts and permits critical escalation if $\le 10\%$).
  - Clause 3: Tracking & Aftermath Screen Presentation & Preemption (typed `BatterySnackbarVisuals`, semantic amber warning and red critical colors, instantaneous preemption, non-battery fallback).
  - Clause 4: Navigation Bar Inset & Floating Control Ergonomics (`navigationBarsPadding()`, `LapButton` clearance).
  - Clause 5: Normal Battery Neutrality ($> 20\%$).
  - Clause 6: 100% 9-Language Localization Parity.
* **Test Mapping**: `TST-UI-316`
  - `[TST-UI-316.1]`: Severity threshold boundary classification in `SensorBatteryAlertTest.kt`.
  - `[TST-UI-316.2]`: In-ride battery drop and critical escalation in `SensorBatteryAlertTest.kt`.
  - `[TST-UI-316.3]`: Snackbar preemption and typed visuals in `SensorBatteryAlertTest.kt`.
  - `[TST-UI-316.4]`: Semantic warning colors & WCAG contrast ratios in `SensorBatteryAlertColorsTest.kt`.
  - `[TST-UI-316.5]`: Dynamic snackbar styling in `TrackingTabsScreenLayoutTest.kt`.
  - `[TST-UI-316.6]`: 9-language localization audit in `TranslationParityTest.kt`.
  - `[TST-UI-316.7]`: Full clean-room regression suite pass (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Non-Blocking Ergonomics**: Low battery alerts remain non-blocking Material 3 snackbars (never modal dialogs during or after tracking).
2. **Navigation Bar Insets & Clearance**: System navigation bar padding (`Modifier.navigationBarsPadding()`) and `LapButton` clearance (`bottom = 64.dp` when lap button is visible, `8.dp` otherwise) remain strictly preserved.
3. **Action Button Routing**: Action `[Zu Sensoren]` continues to invoke `navigateToDrawerItem(R.id.drawer_my_sensors)`.
4. **Sticky Event Consumption**: Alerts continue to be cleared via `WorkoutNavigationEvents.consumeLowBatteryAlert()` to prevent duplicate popups upon recomposition.
5. **Non-Battery Snackbar Safety**: Non-battery snackbars displayed in `TrackingTabsScreen` or `WorkoutSummariesTabbedScreen` fall back to standard Material 3 colors (`inverseSurface` / `inverseOnSurface` / `primary`).
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutNavigationEvents.kt` (Severity Model & Typed Visuals)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/WorkoutNavigationEvents.kt`
* Changes:
  - Add `enum class BatteryAlertSeverity { WARNING, CRITICAL }`.
  - Update `data class LowBatteryAlert`:
    ```kotlin
    data class LowBatteryAlert(
        val message: String,
        val deviceNames: List<String> = emptyList(),
        val severity: BatteryAlertSeverity = BatteryAlertSeverity.WARNING,
        val lowestBatteryPercentage: Int? = null
    )
    ```
  - Implement `data class BatterySnackbarVisuals`:
    ```kotlin
    data class BatterySnackbarVisuals(
        override val message: String,
        override val actionLabel: String?,
        override val withDismissAction: Boolean = false,
        override val duration: SnackbarDuration = SnackbarDuration.Long,
        val severity: BatteryAlertSeverity = BatteryAlertSeverity.WARNING
    ) : SnackbarVisuals
    ```

### Component 2: `Color.kt` & Theme Helper
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`
* Changes:
  - Define semantic warning tokens:
    ```kotlin
    val LightWarning = Color(0xFFE65100)
    val LightWarningContainer = Color(0xFFFFE082)   // Amber 200
    val LightOnWarningContainer = Color(0xFF3E2723) // Deep Brown, contrast > 10:1
    val LightWarningAction = Color(0xFF6D3B00)      // Deep Amber, contrast > 7:1

    val DarkWarning = Color(0xFFFFB74D)
    val DarkWarningContainer = Color(0xFF4E2C00)    // Deep warm amber-brown
    val DarkOnWarningContainer = Color(0xFFFFE082)  // Soft pale amber, contrast > 9:1
    val DarkWarningAction = Color(0xFFFFD54F)       // Vibrant Amber 300, contrast > 8:1
    ```
  - Define composable color resolver:
    ```kotlin
    data class BatterySnackbarColors(
        val containerColor: Color,
        val contentColor: Color,
        val actionColor: Color
    )

    @Composable
    fun getBatterySnackbarColors(severity: WorkoutNavigationEvents.BatteryAlertSeverity): BatterySnackbarColors {
        val isDark = isSystemInDarkTheme()
        return when (severity) {
            WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL -> BatterySnackbarColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                actionColor = MaterialTheme.colorScheme.error
            )
            WorkoutNavigationEvents.BatteryAlertSeverity.WARNING -> if (isDark) {
                BatterySnackbarColors(DarkWarningContainer, DarkOnWarningContainer, DarkWarningAction)
            } else {
                BatterySnackbarColors(LightWarningContainer, LightOnWarningContainer, LightWarningAction)
            }
        }
    }
    ```

### Component 3: `MainActivityWithNavigation.kt` (Thresholds & Critical Escalation)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* Changes:
  - Add constants:
    ```kotlin
    const val LOW_BATTERY_WARNING_LEVEL = 20
    const val CRITICAL_BATTERY_LEVEL = 10
    ```
  - Add `internal val mInRideCriticalAlertedSensorIds: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())`.
  - In `checkSensorBatteryAfterWorkout()` and `checkSensorBatteryOnStart()`:
    - Query devices with battery $\le \text{LOW\_BATTERY\_WARNING\_LEVEL}$ ($20\%$).
    - Determine `severity`: `if (criticalDevices.any { it.batteryPercentage <= CRITICAL_BATTERY_LEVEL }) BatteryAlertSeverity.CRITICAL else BatteryAlertSeverity.WARNING`.
    - Pass `severity` to `WorkoutNavigationEvents.LowBatteryAlert`.
  - In `startInRideBatteryMonitoring()`:
    - If `percentage in 0..LOW_BATTERY_WARNING_LEVEL && devDbManager.isPaired(deviceId)`:
      - If `percentage <= CRITICAL_BATTERY_LEVEL`:
        - If `mInRideCriticalAlertedSensorIds.add(deviceId)`:
          - `mInRideAlertedSensorIds.add(deviceId)`
          - Emit `LowBatteryAlert` with `severity = CRITICAL`.
      - Else (`percentage in (CRITICAL_BATTERY_LEVEL + 1)..LOW_BATTERY_WARNING_LEVEL`):
        - If `mInRideAlertedSensorIds.add(deviceId)`:
          - Emit `LowBatteryAlert` with `severity = WARNING`.

### Component 4: `TrackingTabsScreen.kt` & `WorkoutSummariesTabbedScreen.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt`
* Changes:
  - In alert collection LaunchedEffect:
    - Check preemption: `if (alert.severity == WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL) { snackbarHostState.currentSnackbarData?.dismiss() }`.
    - Show `BatterySnackbarVisuals(message = alert.message, actionLabel = viewSensorsLabel, duration = SnackbarDuration.Long, severity = alert.severity)`.
  - In `SnackbarHost`:
    - Resolve colors via `getBatterySnackbarColors(visuals.severity)` if `data.visuals is BatterySnackbarVisuals`.
    - Fall back to `inverseSurface` / `inverseOnSurface` / `primary` for non-battery snackbars.

### UI Consistency (Rule 23 — mandatory for UI changes)
* **Reference screen / component**: `BatteryOptimizationWarningBanner.kt` (which uses `errorContainer` / `onErrorContainer`) and `ControlTrackingScreen.kt`.
* **Reused components**: Standard Material 3 `SnackbarHost` and `Snackbar`.
* **Theme tokens**:
  - Critical: `MaterialTheme.colorScheme.errorContainer`, `MaterialTheme.colorScheme.onErrorContainer`, `MaterialTheme.colorScheme.error`.
  - Warning: `LightWarningContainer` (`#FFE082`), `LightOnWarningContainer` (`#3E2723`), `LightWarningAction` (`#6D3B00`), `DarkWarningContainer` (`#4E2C00`), `DarkOnWarningContainer` (`#FFE082`), `DarkWarningAction` (`#FFD54F`).
* **WCAG Contrast Ratios**:
  - Light Mode: Text `#3E2723` on container `#FFE082` has contrast ratio $\approx 10.4:1$ (exceeds AAA requirement of $7:1$). Action `#6D3B00` on container `#FFE082` has contrast ratio $\approx 7.2:1$ (exceeds AAA requirement of $7:1$).
  - Dark Mode: Text `#FFE082` on container `#4E2C00` has contrast ratio $\approx 9.2:1$ (exceeds AAA requirement of $7:1$). Action `#FFD54F` on container `#4E2C00` has contrast ratio $\approx 10.1:1$ (exceeds AAA requirement of $7:1$).
* **New one-off styles & justification**: None. Semantic tokens are centralized in `Color.kt` and conform to the application's Material 3 design system.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Severity Model & Typed Visuals in `WorkoutNavigationEvents.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/WorkoutNavigationEvents.kt`
* Add `BatteryAlertSeverity`, update `LowBatteryAlert`, and add `BatterySnackbarVisuals`.

### Step 2: Semantic Warning Colors in `Color.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`
* Add `LightWarningContainer`, `LightOnWarningContainer`, `LightWarningAction`, `DarkWarningContainer`, `DarkOnWarningContainer`, `DarkWarningAction`, and helper `getBatterySnackbarColors`.

### Step 3: Thresholds & In-Ride Escalation in `MainActivityWithNavigation.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* Add `LOW_BATTERY_WARNING_LEVEL = 20`, `CRITICAL_BATTERY_LEVEL = 10`, `mInRideCriticalAlertedSensorIds`, and update alerting methods.

### Step 4: UI Preemption & Dynamic Styling in `TrackingTabsScreen.kt` & `WorkoutSummariesTabbedScreen.kt`
* Targets:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt`
* Wire `BatterySnackbarVisuals`, preemption check, and dynamic color resolution.

### Step 5: Unit Tests for Thresholds, In-Ride Escalation & Preemption
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/notifications/SensorBatteryAlertTest.kt`
* Add tests `[TST-UI-316.1]`, `[TST-UI-316.2]`, and `[TST-UI-316.3]`.

### Step 6: Unit Tests for Semantic Colors & WCAG Contrast
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SensorBatteryAlertColorsTest.kt`
* Add test `[TST-UI-316.4]`.

### Step 7: Contract / Layout Test Updates
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreenLayoutTest.kt`
* Verify dynamic color resolution and absence of static blue `primaryContainer` (`[TST-UI-316.5]`).

### Step 8: Targeted Verification & Regression Runs
* Run targeted tests via `./gradlew testDebugUnitTest --tests "*SensorBattery*"`.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted test suites during construction.
  - Run full clean-room regression suite (`./gradlew testDebugUnitTest`) during Stage 5.
  - Author comprehensive walkthrough in `docs/engineering/walkthroughs/ATT-3451_walkthrough.md`.
* **Rollback Plan**:
  - All changes reside on branch `feature/ATT-3451`.
  - In case of failure or regression, the branch can be cleanly reset or reverted without impacting `develop` or `sprint/2026-42.2`.
