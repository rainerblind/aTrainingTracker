# Stage 3 Implementation Plan: ATT-2621 - Background energy / battery optimization permission not prompted when revoked

**Ticket**: [ATT-2621](https://atrainingtracker.atlassian.net/browse/ATT-2621)  
**Sub-task**: [ATT-2712](https://atrainingtracker.atlassian.net/browse/ATT-2712) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://atrainingtracker.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2621`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Architectural Overview & Component Decomposition (SWE.2)

### 1.1 Architecture & Layers
The fix operates strictly in the UI layer of `ControlTrackingScreen.kt` with zero schema or service disruption:
```
  +-------------------------------------------------------+
  |              ControlTrackingScreen.kt                |
  |  +-------------------------------------------------+  |
  |  | isIgnoringBatteryOptimizations (State<Boolean>) |  |
  |  +-------------------------------------------------+  |
  |                           |                           |
  |       +-------------------+-------------------+       |
  |       v                                       v       |
  |  +---------------------------------+  +------------+  |
  |  | BatteryOptimizationWarningBanner|  |Start Button|  |
  |  | (Proactive M3 Warning Card)     |  |Badge Sync  |  |
  |  +---------------------------------+  +------------+  |
  |                           |                           |
  |                           v                           |
  |          +---------------------------------+          |
  |          | launchBatteryOptimizationIntent |          |
  |          +---------------------------------+          |
  +-------------------------------------------------------+
```

1. **State & Lifecycle Layer**:
   - `isIgnoringBatteryOptimizations` maintained as reactive Compose state in `ControlTrackingScreen.kt`.
   - `Lifecycle.Event.ON_RESUME` re-evaluates `checkIsIgnoringBatteryOptimizations()`.
   - `pendingStartAfterBatteryExemption` tracks intentional tracking start deferred during battery exemption flow.
2. **Presentation Layer**:
   - `BatteryOptimizationWarningBanner.kt`: A dedicated composable card rendered when `!isIgnoringBatteryOptimizations && !isBannerDismissed`.
   - `ControlTrackingButton.kt`: Start button warning badge reflects `!hasLocationPermission || !isIgnoringBatteryOptimizations`.
3. **Decoupled JIT Cascade**:
   - `proceedAfterPermissions` checks background location and battery optimization independently, preventing background location denials from swallowing battery optimization rationales.
4. **Intent Launcher & Fallback**:
   - `launchBatteryOptimizationIntent(context)` dispatches direct intent per Rule 21 with OEM fallback.

---

## 2. UI Consistency (Rule 23 & Design Guidelines Section 5)

| Design Guideline Aspect | Implementation Standard | Justification / Source |
|:---|:---|:---|
| **Closest Existing Reference** | `LocationCalibrationBadge.kt` & `PermissionRationaleSheet.kt` | Same screen, identical notification and rationale domain. |
| **Component Reuse** | `Card`, `Icon`, `Text`, `Button`, `IconButton` | Standard Material 3 components. |
| **Shape Scale** | `RoundedCornerShape(12.dp)` | In accordance with Design Guidelines 5.3 for cards/panels. |
| **Spacing Scale** | `8.dp`, `12.dp`, `16.dp` | Standard spacing scale (5.2). Content padding `12.dp`, outer padding `bottom = 8.dp`. |
| **Color Tokens** | `MaterialTheme.colorScheme.errorContainer` & `onErrorContainer` (or amber container fallback) | Adapts cleanly to Light, Dark, and AMOLED themes without hardcoded colors. |
| **Typography** | `MaterialTheme.typography.titleSmall` & `bodySmall` | Standard M3 type tokens (5.5). |
| **New One-Off Styles** | None | Adheres strictly to visual consistency baseline. |

---

## 3. Atomic Implementation Steps

### Step 1: 9-Language Localization Resources
- **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
- **Keys**:
  - `battery_optimization_warning_banner_title`: "Background Energy Restricted" / "Hintergrund-Energieverwaltung eingeschränkt"
  - `battery_optimization_warning_banner_desc`: "Android may stop workout recording when the screen turns off. Please disable battery optimization." / "Android kann das Tracking bei ausgeschaltetem Bildschirm beenden. Bitte Akku-Optimierung deaktivieren."
  - `battery_optimization_action_fix`: "Exempt" / "Aktivieren"
  - `battery_optimization_dismiss_banner`: "Dismiss" / "Schließen"
- **Test**: `BatteryOptimizationBannerLocalizationTest.kt`.

### Step 2: Create `BatteryOptimizationWarningBanner.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/BatteryOptimizationWarningBanner.kt`.
- **Implementation**:
  - Composable taking `visible: Boolean`, `onFixClick: () -> Unit`, `onDismissClick: () -> Unit`, `modifier: Modifier`.
  - Wraps in `AnimatedVisibility(visible = visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically())`.
  - Renders M3 `Card` with `RoundedCornerShape(12.dp)`, `errorContainer` colors, alert icon (`ic_battery_full`), localized title and description, "Aktivieren" button, and dismiss icon button ("X").
- **Test**: `BatteryOptimizationWarningBannerTest.kt`.

### Step 3: Wire Reactive State & Cascade in `ControlTrackingScreen.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`.
- **Changes**:
  1. Add reactive Compose state:
     ```kotlin
     var isIgnoringBatteryOptimizations by remember { mutableStateOf(checkIsIgnoringBatteryOptimizations()) }
     var isBatteryBannerDismissed by rememberSaveable { mutableStateOf(false) }
     var pendingStartAfterBatteryExemption by rememberSaveable { mutableStateOf(false) }
     ```
  2. In `DisposableEffect(lifecycleOwner)` on `ON_RESUME`:
     ```kotlin
     val batteryExempt = checkIsIgnoringBatteryOptimizations()
     isIgnoringBatteryOptimizations = batteryExempt
     if (batteryExempt && pendingStartAfterBatteryExemption) {
         pendingStartAfterBatteryExemption = false
         onStart()
     }
     ```
  3. Render `BatteryOptimizationWarningBanner` directly below `LocationCalibrationBadge`:
     ```kotlin
     BatteryOptimizationWarningBanner(
         visible = !isIgnoringBatteryOptimizations && !isBatteryBannerDismissed,
         onFixClick = { launchBatteryOptimizationIntent(context) },
         onDismissClick = { isBatteryBannerDismissed = true }
     )
     ```
  4. Update Start button warning badge:
     ```kotlin
     hasPermissionWarning = !hasLocationPermission || !isIgnoringBatteryOptimizations
     ```
  5. Decouple `proceedAfterPermissions` cascade:
     - When `!checkHasBackgroundLocation()`: if `rationaleStep` for background location is dismissed or handled, ensure `!checkIsIgnoringBatteryOptimizations()` is checked before invoking `onStart()`.
     - In `PermissionRationaleSheet.onContinue` for `BATTERY_OPTIMIZATION`:
       ```kotlin
       pendingStartAfterBatteryExemption = true
       launchBatteryOptimizationIntent(context)
       rationaleStep = RationaleStep.NONE
       ```
  - **Test**: `ControlTrackingScreenBatteryTest.kt`.

### Step 4: Verification & Clean-Room Regression
- Execute targeted unit tests:
  `./gradlew testDebugUnitTest --tests "*BatteryOptimization*" --tests "*ControlTrackingScreen*"`
- Execute full clean-room suite:
  `./gradlew testDebugUnitTest`

---

## 4. Invariant Protection & Governance

1. **Google Play Policy Compliance**: System intent `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is ONLY triggered by explicit user touch on the banner or Start rationale sheet. Never auto-dispatched from `ON_RESUME`.
2. **Infinite Loop Prevention**: If the athlete dismisses the sheet via "Not now", `rationaleStep` resets to `NONE` and tracking begins without modal loops.
3. **Cold Start Zero-Friction (`REQ-PRI-003`)**: No blocking modal dialogs are presented on startup; the banner is inline and non-modal.
4. **Precise Location Gating (`REQ-PRI-004`)**: Location permission logic remains strictly intact.
5. **Localization Parity**: 100% parity across all 9 locales.
