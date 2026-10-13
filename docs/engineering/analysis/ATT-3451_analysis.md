# Stage 1 Analysis: ATT-3451 - Use warning colors instead of blue for sensor low battery snackbar alerts

**Ticket**: [ATT-3451](https://atrainingtracker.atlassian.net/browse/ATT-3451)  
**Sub-task**: [ATT-3462](https://atrainingtracker.atlassian.net/browse/ATT-3462) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Branch**: `feature/ATT-3451`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13 (Updated with Gate 1 Audit Revisions)  

---

## 1. Problem Statement & Motivation

In `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt`, peripheral sensor low battery alert snackbars (triggered from `MainActivityWithNavigation.kt` via `WorkoutNavigationEvents.lowBatteryAlert`) currently render with the application's default primary/blue container colors:
```kotlin
Snackbar(
    snackbarData = data,
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionColor = MaterialTheme.colorScheme.primary
)
```

This implementation presents two major UI/UX defects:
1. **Semantic Color Misalignment**:
   In Material 3 and throughout `aTrainingTracker`, primary blue tones (`BabyBlueEye` / `DarkPrimaryContainer`) communicate routine informational status, selected tabs, and active route lines. Presenting a battery exhaustion warning in calm blue dilutes perceived urgency and violates semantic color conventions.
2. **Absence of Severity Tiering**:
   A peripheral sensor with moderate low battery ($11\%\dots 20\%$) requires attention soon, whereas a sensor with critical battery ($\le 10\%$) is in immediate danger of shutdown mid-workout (leading to lost cadence, heart rate, or power telemetry). The existing snackbar mechanism renders both scenarios identically in blue without visual hierarchy or urgency distinction.

Ticket **ATT-3451** resolves these deficiencies by introducing dedicated semantic warning/alert colors and establishing two distinct severity tiers:
* **Warning (Low Battery, $> 10\%$ and $\le 20\%$)**: Amber/yellow warning container with high-contrast text and action button.
* **Critical (Very Low Battery, $\le 10\%$)**: Red/error container (`errorContainer` / `onErrorContainer` / `error`) with high-contrast text.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Call-Site & Architecture Audit

1. **Presentation Layer (`TrackingTabsScreen.kt` & `WorkoutSummariesTabbedScreen.kt`)**:
   - In both screens, `SnackbarHost` renders a custom `Snackbar` composable with hardcoded `primaryContainer`, `onPrimaryContainer`, and `primary`.
   - `SnackbarHost` receives a `SnackbarData` instance whose standard `visuals: SnackbarVisuals` interface carries only `message`, `actionLabel`, `withDismissAction`, and `duration`.
   - Neither screen currently receives or evaluates any severity metadata, preventing dynamic container color assignment.

2. **Event Dispatch Layer (`WorkoutNavigationEvents.kt`)**:
   - `WorkoutNavigationEvents.LowBatteryAlert` is currently declared as:
     ```kotlin
     data class LowBatteryAlert(
         val message: String,
         val deviceNames: List<String> = emptyList()
     )
     ```
   - It possesses no severity classification, lowest battery percentage, or visual styling hints.

3. **Evaluation & Triggering Layer (`MainActivityWithNavigation.kt`)**:
   - Evaluates sensor battery states in three workflows:
     - `checkSensorBatteryAfterWorkout(workoutId: Long)`: Post-workout check.
     - `checkSensorBatteryOnStart()`: Workout launch check.
     - `startInRideBatteryMonitoring()`: Real-time telemetry flow collector.
   - All three workflows use a single uniform threshold `CRITICAL_BATTERY_LEVEL = 20`.
   - In `startInRideBatteryMonitoring()`, `mInRideAlertedSensorIds` deduplicates alerts per sensor ID. If an alert fires when a sensor drops to $18\%$ (`Warning`), subsequent in-ride battery updates are suppressed, preventing the athlete from receiving a `Critical` escalation alert if the sensor further degrades to $\le 10\%$.

### 2.2 Semantic Color Tokens & WCAG Contrast Audit

- `MaterialTheme.colorScheme` provides native tokens for errors:
  - Light: `LightErrorContainer` (`#FFDAD6`), `LightOnErrorContainer` (`#410002`), `LightError` (`#B00020`).
  - Dark: `DarkErrorContainer` (`#93000A`), `DarkOnErrorContainer` (`#FFDAD6`), `DarkError` (`#FFB4AB`).
- However, Material 3 does not include built-in `warningContainer` tokens in `ColorScheme`.
- To maintain WCAG AAA / AA contrast ratios ($\ge 7:1$ for large text / icons, $\ge 4.5:1$ for body and action text) across Light, Dark, and AMOLED themes, dedicated semantic warning tokens must be introduced in `Color.kt`.
- Crucially, the **Action Color** for `WARNING` must NOT fall back to `primary` (blue), but must use a dedicated high-contrast action color:
  - `LightWarningAction = Color(0xFF6D3B00)` (Deep Amber / Burnt Umber, contrast $> 7:1$ on `#FFE082`).
  - `DarkWarningAction = Color(0xFFFFD54F)` (Bright Amber 300, contrast $> 7:1$ on `#4E2C00`).

### 2.3 Snackbar Preemption Architecture

In Material 3 `SnackbarHostState`, calling `showSnackbar()` while another snackbar is visible causes the new snackbar to wait until the current one dismisses, or requires explicitly dismissing `currentSnackbarData`.
When an alert escalates from `WARNING` to `CRITICAL` (e.g. sensor drops to 8%), the critical alert MUST immediately preempt and dismiss any active warning snackbar so the urgent alert is visible without delay.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Objectives:
1. **Severity Classification Model**:
   - Define `enum class BatteryAlertSeverity { WARNING, CRITICAL }` in `WorkoutNavigationEvents.kt`.
   - Enhance `LowBatteryAlert` with `val severity: BatteryAlertSeverity = BatteryAlertSeverity.WARNING` and `val lowestBatteryPercentage: Int? = null`.
   - Implement typed `BatterySnackbarVisuals : SnackbarVisuals` carrying `severity: BatteryAlertSeverity`.
2. **Semantic Warning & Critical Color Palette (`Color.kt`)**:
   - Define accessible amber/yellow tokens for Light, Dark, and AMOLED modes:
     - `LightWarningContainer = Color(0xFFFFE082)` (Warm Amber 200)
     - `LightOnWarningContainer = Color(0xFF3E2723)` (Deep Brown / near black, contrast $> 10:1$)
     - `LightWarningAction = Color(0xFF6D3B00)` (Deep Amber, contrast $> 7:1$)
     - `DarkWarningContainer = Color(0xFF4E2C00)` (Deep warm amber-brown)
     - `DarkOnWarningContainer = Color(0xFFFFE082)` (Soft pale amber, contrast $> 9:1$)
     - `DarkWarningAction = Color(0xFFFFD54F)` (Vibrant Amber 300, contrast $> 8:1$)
   - Map `CRITICAL` severity directly to Material 3 `errorContainer`, `onErrorContainer`, and `error`.
3. **Snackbar Presentation & Preemption Update**:
   - In `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt`, when a `CRITICAL` alert arrives while a snackbar is active, immediately dismiss `snackbarHostState.currentSnackbarData?.dismiss()` to ensure instantaneous preemption.
   - Resolve container, content, and action colors dynamically based on `visuals.severity`, preserving standard Material 3 colors for any non-battery `SnackbarVisuals`.
4. **Triggering & Escalation Logic (`MainActivityWithNavigation.kt`)**:
   - Establish `LOW_BATTERY_WARNING_LEVEL = 20` and `CRITICAL_BATTERY_LEVEL = 10`.
   - Classify alerts as `CRITICAL` if any alerted device has battery $\le 10\%$, otherwise `WARNING` if $\le 20\%$.
   - In-ride monitoring: Track `mInRideAlertedSensorIds` and `mInRideCriticalAlertedSensorIds` to allow alert escalation from `WARNING` to `CRITICAL` if an active sensor drops from $> 10\%$ to $\le 10\%$.
5. **Testing & Verification**:
   - Expand `SensorBatteryAlertTest.kt` to verify threshold classification, in-ride critical escalation, and deduplication.
   - Add preemption test: verify that triggering a `CRITICAL` alert immediately dismisses an active `WARNING` snackbar.
   - Update `TrackingTabsScreenLayoutTest.kt` to verify dynamic warning/critical snackbar styling and absence of static blue `primaryContainer`.

### Out-of-Scope Non-Goals (Scope Bounding):
1. No modifications to Android system notification channels or notification priority.
2. No modifications to SQLite schema in `DevicesDatabaseManager` or `ActiveDevicesDbHelper`.
3. No changes to BLE GATT battery reading intervals or protocol drivers.
4. No modification to existing localized string resources (text continues using `sensor_battery_snackbar_single` / `many`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends and refines `REQ-UI-356` (*Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance*, ATT-3313) and `REQ-CON-007` (*Post-Workout Alerting*, ATT-2192).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.16 (`ATT-2192`, commit `ac493d2b` baseline post-workout snackbar).
   - Sprint 2026-41.9 (`ATT-3167`, commit `53b219ac` navigation bar padding).
   - Sprint 2026-42.1 (`ATT-3313`, commit `479c7ee7` workout start & in-ride snackbars).
3. **Root Reason for Existing Formulation**:
   - ATT-3313 focused on wiring `SnackbarHost` into `TrackingTabsScreen` with navigation bar padding and lap button clearance. It used `primaryContainer` as a safe initial placeholder.
4. **Preservation of Core Invariants**:
   - Non-blocking Material 3 snackbar behavior (never blocking modal dialogs).
   - Edge-to-edge system navigation bar padding (`Modifier.navigationBarsPadding()`).
   - Floating control clearance above `LapButton` (`64.dp` vs `8.dp`).
   - Action button `[Zu Sensoren]` (`R.string.sensor_battery_action_view_sensors`) navigates to `R.id.drawer_my_sensors`.
   - Alert consumption via `WorkoutNavigationEvents.consumeLowBatteryAlert()`.
   - 100% clean-room test pass rate across the full test suite.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Severity Model & Typed Visuals
```kotlin
// In WorkoutNavigationEvents.kt
enum class BatteryAlertSeverity {
    WARNING,  // <= 20% and > 10%
    CRITICAL  // <= 10%
}

data class LowBatteryAlert(
    val message: String,
    val deviceNames: List<String> = emptyList(),
    val severity: BatteryAlertSeverity = BatteryAlertSeverity.WARNING,
    val lowestBatteryPercentage: Int? = null
)

data class BatterySnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Long,
    val severity: BatteryAlertSeverity = BatteryAlertSeverity.WARNING
) : SnackbarVisuals
```

### 5.2 Dedicated Semantic Warning Tokens (`Color.kt`)
```kotlin
// In Color.kt
val LightWarningContainer = Color(0xFFFFE082)   // Amber 200
val LightOnWarningContainer = Color(0xFF3E2723) // Deep Brown, contrast > 10:1
val LightWarningAction = Color(0xFF6D3B00)      // Deep Amber, contrast > 7:1

val DarkWarningContainer = Color(0xFF4E2C00)    // Deep warm amber-brown
val DarkOnWarningContainer = Color(0xFFFFE082)  // Soft pale amber, contrast > 9:1
val DarkWarningAction = Color(0xFFFFD54F)       // Vibrant Amber 300, contrast > 8:1

@Composable
fun getBatterySnackbarColors(severity: BatteryAlertSeverity): Triple<Color, Color, Color> {
    val isDark = isSystemInDarkTheme()
    return when (severity) {
        BatteryAlertSeverity.CRITICAL -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        BatteryAlertSeverity.WARNING -> if (isDark) {
            Triple(DarkWarningContainer, DarkOnWarningContainer, DarkWarningAction)
        } else {
            Triple(LightWarningContainer, LightOnWarningContainer, LightWarningAction)
        }
    }
}
```

### 5.3 Preemption & UI Presentation
In `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt`:
```kotlin
LaunchedEffect(Unit) {
    WorkoutNavigationEvents.lowBatteryAlert.collect { alert ->
        if (alert != null) {
            // Preemption: If incoming alert is CRITICAL and a snackbar is active, dismiss immediately
            if (alert.severity == BatteryAlertSeverity.CRITICAL) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val visuals = BatterySnackbarVisuals(
                message = alert.message,
                actionLabel = viewSensorsLabel,
                duration = SnackbarDuration.Long,
                severity = alert.severity
            )
            val result = snackbarHostState.showSnackbar(visuals)
            if (result == SnackbarResult.ActionPerformed) {
                (context as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)
            }
            WorkoutNavigationEvents.consumeLowBatteryAlert()
        }
    }
}

SnackbarHost(
    hostState = snackbarHostState,
    modifier = Modifier.navigationBarsPadding().padding(bottom = if (shouldShowLapButton) 64.dp else 8.dp)
) { data ->
    val visuals = data.visuals as? BatterySnackbarVisuals
    val (containerColor, contentColor, actionColor) = visuals?.let {
        getBatterySnackbarColors(it.severity)
    } ?: Triple(
        MaterialTheme.colorScheme.inverseSurface,
        MaterialTheme.colorScheme.inverseOnSurface,
        MaterialTheme.colorScheme.primary
    )
    Snackbar(
        snackbarData = data,
        containerColor = containerColor,
        contentColor = contentColor,
        actionColor = actionColor
    )
}
```
