# Stage 4 Implementation Report: ATT-2948 - Modernize StartOrResume workout recovery dialog with Material 3 Jetpack Compose

**Ticket**: [ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948)  
**Sub-task**: [ATT-3022](https://atrainingtracker.atlassian.net/browse/ATT-3022) (`[Implementation]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2948`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

This deliverable concludes Stage 4 (Software Construction & Implementation) for [ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948) in accordance with requirement `REQ-UI-329` and test specification `TST-UI-289`.

We modernized `StartOrResumeDialog.kt` from a legacy Android View dialog (`AlertDialog.Builder`) to Material 3 Jetpack Compose. The recovery dialog now renders inside `ComposeView` with `ATrainingTrackerTheme`, featuring contextual kill reason iconography, bold headline typography, structured diagnostic callout containerization, action button hierarchy, and strict preservation of `DialogFragment` and `StartOrResumeInterface` contracts.

---

## 2. Implemented Changes

### 2.1 UI Component Architecture (`StartOrResumeDialog.kt`)
* **DialogFragment Host & Compose Migration**:
  - Maintained `StartOrResumeDialog : DialogFragment()` inheritance, preserving `TAG`, `onAttach(Context)`, and `attachInterface(Context)`.
  - Configured `setStyle(STYLE_NO_TITLE, 0)` in `onCreate()`.
  - In `onCreateView()`, set `dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)` and returned a `ComposeView` with `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` wrapping `ATrainingTrackerTheme` and `StartOrResumeDialogContent`.
* **Contextual Iconography & Header Styling**:
  - Dynamically resolved contextual 36dp semantic icons and colors:
    * `BATTERY_KILL`: `Icons.Default.BatteryAlert` with `MaterialTheme.colorScheme.error`.
    * `LOW_MEMORY`: `Icons.Default.Memory` with `MaterialTheme.colorScheme.primary`.
    * `PERMISSION_REVOKED`: `Icons.Default.Security` with `MaterialTheme.colorScheme.error`.
    * `GENERIC_UNFINISHED`: `Icons.Default.DirectionsRun` with `MaterialTheme.colorScheme.primary`.
  - Rendered title with `MaterialTheme.typography.titleLarge` and `FontWeight.Bold`.
* **Structured Diagnostic Callout & Prompt Separation**:
  - When `reason != KillReason.GENERIC_UNFINISHED`, formatted forensic explanation (including progressive battery escalation stages and Strava note) inside a subtle tinted `Surface` container (`surfaceContainer`, `RoundedCornerShape(12.dp)`, `padding(12.dp)`).
  - Positioned prompt (`start_or_resume_dialog_message`) distinctly in `bodyMedium` with `onSurfaceVariant` color.
* **Action Button Hierarchy**:
  - Contextual Action: `FilledTonalButton` for "Disable Battery Optimization" (`action_disable_battery_optimization`) when `diagnosis.shouldShowBatteryButton == true`.
  - Secondary Action: `OutlinedButton` for "Start new" (`start_new_workout`).
  - Primary Action: Filled `Button` for "Resume prev." (`resume_workout`).
* **Standalone Composable Modal**:
  - Exported `StartOrResumeAlertDialog` wrapping `StartOrResumeDialogContent` in Compose `Dialog` for pure Compose screen composition.

### 2.2 Unit & Contract Tests (`StartOrResumeDialogLayoutTest.kt` & `StartOrResumeDialogContractTest.kt`)
* Maintained existing contract tests in `StartOrResumeDialogContractTest.kt` (inheritance, tag, interface attachment).
* Created `StartOrResumeDialogLayoutTest.kt`:
  - `testStartOrResumeDialog_eliminationOfLegacyAlertDialogBuilder`: Verifies elimination of `AlertDialog.Builder` and adoption of `ComposeView` and `ATrainingTrackerTheme`.
  - `testStartOrResumeDialog_contextualIconographyAndTokens`: Verifies 36dp icon size, semantic icons, `titleLarge` bold header, and button hierarchy (`Button`, `OutlinedButton`, `FilledTonalButton`).
  - `testKillDiagnosis_allStateCombinationsInstantiable`: Verifies `KillDiagnosis` model instantiation across all 4 `KillReason` values.
  - `testRecoveryDialog_9LanguageLocalizationParity`: Verifies all 14 recovery dialog strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Verification & Targeted Test Suite

Targeted unit and contract tests executed cleanly:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialog*"
```
Result: **BUILD SUCCESSFUL in 16s** (All tests green).
