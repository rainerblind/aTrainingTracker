# Stage 3 Implementation Plan: ATT-2948 - Modernize StartOrResume workout recovery dialog with Material 3 Jetpack Compose

**Ticket**: [ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948)  
**Sub-task**: [ATT-3021](https://atrainingtracker.atlassian.net/browse/ATT-3021) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2948`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Architecture

This implementation plan defines the step-by-step engineering work to modernize `StartOrResumeDialog.kt` ([ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948)) from a legacy Android View dialog (`AlertDialog.Builder`) to Material 3 Jetpack Compose.

The modernization delivers:
1. **Material 3 Compose Dialog Architecture**: Wrapping dialog content inside `ComposeView` with `ATrainingTrackerTheme`, setting dialog window background to transparent, and rendering on a Material 3 `Surface` with `RoundedCornerShape(28.dp)` and `surfaceContainerHigh` container color.
2. **Contextual Kill Reason Iconography**: Distinct 36dp semantic icons (`BatteryAlert`, `Memory`, `Security`, `DirectionsRun`) with semantic color accents and `titleLarge` bold titles dynamically reflecting the 4 diagnosis states (`BATTERY_KILL`, `LOW_MEMORY`, `PERMISSION_REVOKED`, `GENERIC_UNFINISHED`).
3. **Structured Diagnostic Callout**: Distinct tinted callout container (`surfaceContainer` / `RoundedCornerShape(12.dp)`) formatting forensic termination explanations (including progressive battery escalation stages and Strava note), cleanly separated from the prompt to start anew or resume.
4. **Action Button Hierarchy**:
   - Primary: Filled `Button` for "Resume prev." (`R.string.resume_workout`).
   - Secondary: `OutlinedButton` for "Start new" (`R.string.start_new_workout`).
   - Contextual: `FilledTonalButton` or `OutlinedButton` for "Disable Battery Optimization" (`R.string.action_disable_battery_optimization`) when `diagnosis.shouldShowBatteryButton == true`.
5. **Contract & Interface Preservation**: Strict preservation of `StartOrResumeDialog : DialogFragment()`, `StartOrResumeDialog.TAG`, `attachInterface(Context)`, and `StartOrResumeInterface` callbacks (`chooseStart()`, `chooseResume()`).

---

## 2. UI Consistency (Rule 23 & Design Guidelines Section 5)

* **Closest Reference Dialogs**: `AntAlertDialogs.kt` (Material 3 dialogs with 36dp icons, headline typography, and button hierarchy) and `DeleteOldWorkoutsDialog.kt`.
* **Reused Components**: Material 3 `Surface`, `Button`, `OutlinedButton`, `FilledTonalButton`, `Text`, `Icon`.
* **Theme Tokens**:
  - Modal container shape: `RoundedCornerShape(28.dp)` (`AlertDialogDefaults.shape`).
  - Container color: `MaterialTheme.colorScheme.surfaceContainerHigh`.
  - Tonal elevation: `AlertDialogDefaults.TonalElevation` (6.dp).
  - Diagnostic callout container: `MaterialTheme.colorScheme.surfaceContainer` with `RoundedCornerShape(12.dp)` and `padding(12.dp)`.
  - Header icon size: 36.dp.
  - Spacing: 24.dp outer padding, 16.dp vertical block spacing, 8.dp horizontal button spacing.
  - Typography: `titleLarge` bold header, `bodyMedium` explanation and prompt, `labelLarge` bold buttons.

---

## 3. Atomic Implementation Steps

### Step 1: Composable Architecture Extraction (`StartOrResumeDialogContent.kt` or inside `StartOrResumeDialog.kt`)
* Implement reusable composable `@Composable fun StartOrResumeDialogContent(...)`:
  ```kotlin
  @Composable
  fun StartOrResumeDialogContent(
      diagnosis: KillDiagnosis,
      onStartNew: () -> Unit,
      onResume: () -> Unit,
      onDisableBatteryOptimization: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
* Structure the layout:
  - Outer `Surface`: `shape = RoundedCornerShape(28.dp)`, `color = MaterialTheme.colorScheme.surfaceContainerHigh`, `tonalElevation = 6.dp`.
  - Root `Column`: `Modifier.padding(24.dp).fillMaxWidth().wrapContentHeight()`.
  - Header `Column`:
    - Contextual `Icon` (36.dp):
      * `BATTERY_KILL` -> `Icons.Default.BatteryAlert`, tint: `MaterialTheme.colorScheme.error`
      * `LOW_MEMORY` -> `Icons.Default.Memory`, tint: `MaterialTheme.colorScheme.primary`
      * `PERMISSION_REVOKED` -> `Icons.Default.Security`, tint: `MaterialTheme.colorScheme.error`
      * `GENERIC_UNFINISHED` -> `Icons.Default.DirectionsRun`, tint: `MaterialTheme.colorScheme.primary`
    - Title `Text`: `style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center`.
  - Diagnostic Callout (if `reason != GENERIC_UNFINISHED`):
    - `Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer)`:
      - `Text(explanationText, style = MaterialTheme.typography.bodyMedium)`.
  - Prompt `Text`: `stringResource(R.string.start_or_resume_dialog_message)` in `bodyMedium` with `onSurfaceVariant` color.
  - Contextual Battery Button (if `diagnosis.shouldShowBatteryButton`):
    - `FilledTonalButton(onClick = onDisableBatteryOptimization, modifier = Modifier.fillMaxWidth())` with `R.string.action_disable_battery_optimization`.
  - Action Row (`Row(horizontalArrangement = Arrangement.End, horizontalArrangement = spacedBy(8.dp))`):
    - `OutlinedButton(onClick = onStartNew)` -> `stringResource(R.string.start_new_workout)`
    - `Button(onClick = onResume)` -> `stringResource(R.string.resume_workout)`
* *Verification*: Composable compiles with full M3 styling, semantic tokens, and callback bindings.

### Step 2: DialogFragment Integration (`StartOrResumeDialog.kt`)
* In `StartOrResumeDialog : DialogFragment()`:
  - Preserve `TAG` and `attachInterface(Context)`.
  - Override `onCreateView(inflater, container, savedInstanceState): View`:
    - Set `dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)`.
    - Resolve diagnosis: `val diagnosis = ProcessExitReasonHelper.resolveKillReason(requireContext())`.
    - Return `ComposeView(requireContext()).apply { ... }` rendering `ATrainingTrackerTheme { StartOrResumeDialogContent(...) }`.
  - Ensure callbacks invoke `startOrResumeInterface?.chooseStart()` / `chooseResume()` / `ProcessExitReasonHelper.openBatteryOptimizationSettings()` and call `dismiss()`.
* *Verification*: `StartOrResumeDialog` extends `DialogFragment`, satisfies all existing contract tests in `StartOrResumeDialogContractTest.kt`.

### Step 3: Layout & Contract Tests (`StartOrResumeDialogLayoutTest.kt`)
* Implement tests in `com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialogLayoutTest`:
  1. `testContextualIconAndTitle_allKillReasons`: Verifies correct icon and title mapped to `BATTERY_KILL`, `LOW_MEMORY`, `PERMISSION_REVOKED`, and `GENERIC_UNFINISHED`.
  2. `testDiagnosticExplanation_batteryEscalationStages`: Verifies stages 1, 2, 3, and Strava note text.
  3. `testBatteryButton_visibilityGating`: Verifies `shouldShowBatteryButton` visibility toggle.
  4. `testButtonHierarchy_buttonStyles`: Verifies resume is primary `Button` and start new is `OutlinedButton`.
* Run targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialog*"`.

### Step 4: 9-Language Localization Audit
* Verify all 8 string resources used in `StartOrResumeDialog` exist across all 9 locales:
  - `unfinished_workout_title`
  - `unfinished_workout_title_battery`
  - `unfinished_workout_title_memory`
  - `unfinished_workout_title_permission`
  - `kill_reason_battery_stage1`
  - `kill_reason_battery_stage1_strava`
  - `kill_reason_battery_stage2`
  - `kill_reason_battery_stage3`
  - `kill_reason_low_memory`
  - `kill_reason_permission_revoked`
  - `action_disable_battery_optimization`
  - `start_or_resume_dialog_message`
  - `start_new_workout`
  - `resume_workout`

### Step 5: Clean-Room Full Suite Regression
* Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 4. Invariants & Governance

1. **Chesterton's Fence Preservation**:
   - `StartOrResumeInterface` contracts and `StartOrResumeDialogContractTest.kt` inheritance assertions MUST NOT be broken.
   - `ProcessExitReasonHelper.openBatteryOptimizationSettings` intent handling MUST remain fully functional.
   - Idempotent battery escalation counting is preserved.
2. **Localization Parity**:
   - 100% translation parity across all 9 supported application locales.
3. **Clean-Room Test Pass Rate**:
   - Full test suite regression must execute with a 100% pass rate.

---

## 5. Gate 3 Readiness Checklist

- [x] All requirements mapped to atomic implementation steps.
- [x] UI component hierarchy matches Material 3 guidelines and `AntAlertDialogs.kt`.
- [x] Test specifications and assertions defined in `StartOrResumeDialogLayoutTest`.
- [x] Invariants and backward-compatibility verified.
