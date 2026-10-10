# Stage 1 Analysis Report: ATT-2948 - Modernize StartOrResume workout recovery dialog with Material 3 Jetpack Compose

**Ticket**: [ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948)  
**Sub-task**: [ATT-3019](https://atrainingtracker.atlassian.net/browse/ATT-3019) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2948`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Forensic Root Cause Investigation

When an interrupted or unfinalized workout exists in SQLite (`hasUnfinishedWorkout() == true`), `MainActivityWithNavigation.kt` displays `StartOrResumeDialog.kt` to allow the athlete to either start a fresh session or resume the interrupted workout (`REQ-STB-003`).

In ATT-2079 (`REQ-STB-012`), forensic process termination diagnosis was integrated via `ProcessExitReasonHelper.kt`, evaluating Android 11+ `ApplicationExitInfo` and `PowerManager.isIgnoringBatteryOptimizations()`. This diagnosed the concrete cause of termination (`BATTERY_KILL`, `LOW_MEMORY`, `PERMISSION_REVOKED`, or `GENERIC_UNFINISHED`) and provided progressive escalation copy for repeated battery kills, alongside an action to disable battery optimization.

However, `StartOrResumeDialog.kt` remains implemented using the legacy platform `androidx.appcompat.app.AlertDialog.Builder`. Consequently:
1. **Dated Platform Aesthetics & Container Geometry**:
   - The dialog renders with legacy platform dialog frames lacking Material 3 rounded container geometry (`RoundedCornerShape(28.dp)`), tonal surface elevation (`surfaceContainerHigh`), and seamless dark/light theme tokens.
2. **Missing Contextual Iconography & Typographic Distinction**:
   - Despite diagnosing 4 distinct termination reasons, the dialog displays no contextual icons. Athletes see a flat text header rather than immediate visual indicators (e.g. Battery Alert, Memory chip, Security shield, or Running athlete).
   - The title is rendered in standard dialog title typography rather than bold, high-contrast M3 headline styles (`MaterialTheme.typography.titleLarge`, `FontWeight.Bold`).
3. **Unstructured String Concatenation**:
   - The kill diagnosis explanation and the user prompt ("The previous workout was not finished properly...") are concatenated into a single plain text string (`builder.setMessage(...)`), lacking visual separation or a dedicated explanation callout container.
4. **Lack of Action Button Hierarchy**:
   - `PositiveButton` ("Start new"), `NegativeButton` ("Resume prev."), and `NeutralButton` ("Disable Battery Optimization") share identical flat text styling, failing to visually emphasize the primary action (Resume as filled `Button`), secondary action (Start new as `OutlinedButton`), and contextual setting adjustment.

---

## 2. Chesterton's Fence Requirement Archaeology

1. **Original Requirement ID & Target**:
   - `REQ-STB-012` (*Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance*), targeting `StartOrResumeDialog.kt` and `ProcessExitReasonHelper.kt`.
   - `REQ-STB-003` (*Workout Resumption Contracts*), targeting `StartOrResumeInterface.java` and `MainActivityWithNavigation.kt`.
2. **Historical Origin & Commit Trace**:
   - Introduced in Sprint 2026-40.16 (`ATT-2079`, commit `95e0c511`).
3. **Root Reason for Existing Formulation**:
   - In ATT-2079, the technical objective was diagnosing background process kills using Android 11 `ApplicationExitInfo` and testing progressive escalation logic. The UI was wrapped inside `AlertDialog.Builder` as a quick expedient to ensure functionality without disrupting the sprint scope.
4. **Preservation of Core Invariants**:
   - **Resumption Interface Contracts**: `StartOrResumeInterface` callbacks (`chooseStart()`, `chooseResume()`) and `StartOrResumeDialog.TAG` MUST be preserved.
   - **Inheritance Contract**: `StartOrResumeDialog` MUST continue to extend `DialogFragment` to satisfy `StartOrResumeDialogContractTest.kt` and `MainActivityWithNavigation.kt` fragment management.
   - **Diagnostic Correctness**: All 4 `KillReason` states, progressive escalation stages (1, 2, 3), Strava activity note, and battery optimization button gating (`shouldShowBatteryButton`) MUST remain strictly intact.
   - **Direct Settings Intent**: Invoking `ProcessExitReasonHelper.openBatteryOptimizationSettings(context)` when tapping the battery button MUST be preserved.
   - **Localization Parity**: All 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) MUST maintain 100% translation parity.

---

## 3. Scope Bounding & Invariants Enforcement

### 3.1 In Scope
1. **Material 3 Jetpack Compose Migration**:
   - Refactor `StartOrResumeDialog.kt` (`DialogFragment`) to host a Compose UI via `ComposeView` with `ATrainingTrackerTheme`.
   - Build a reusable composable `StartOrResumeDialogContent` providing modern M3 dialog geometry (`RoundedCornerShape(28.dp)`), container styling (`surfaceContainerHigh`), and standard padding.
2. **Contextual Iconography & Header Styling**:
   - Render semantic contextual icons:
     * `BATTERY_KILL`: `Icons.Default.BatteryAlert` with warning/tertiary color accent.
     * `LOW_MEMORY`: `Icons.Default.Memory` with primary/secondary color accent.
     * `PERMISSION_REVOKED`: `Icons.Default.Security` with error/tertiary color accent.
     * `GENERIC_UNFINISHED`: `Icons.Default.DirectionsRun` with primary color accent.
   - Bold headline typography (`titleLarge`, `FontWeight.Bold`) reflecting the diagnostic title.
3. **Structured Message Container & Button Hierarchy**:
   - Format the kill cause explanation inside a subtle tinted container (`surfaceContainer` / `surfaceVariant` with `RoundedCornerShape(12.dp)`).
   - Follow with the user choice prompt (`start_or_resume_dialog_message`).
   - Clear button hierarchy:
     * Primary action: `Button` (Filled) for "Resume prev." (`R.string.resume_workout`).
     * Secondary action: `OutlinedButton` for "Start new" (`R.string.start_new_workout`).
     * Contextual action: `FilledTonalButton` or `Button` for "Disable Battery Optimization" (`R.string.action_disable_battery_optimization`) when `diagnosis.shouldShowBatteryButton == true`.
4. **Comprehensive Test Suite & Localization Parity**:
   - Update `StartOrResumeDialogContractTest.kt` and add `StartOrResumeDialogLayoutTest.kt` verifying all 4 kill reasons, progressive escalation texts, button clicks, and 9-language translation parity.

### 3.2 Out of Scope
* Changing `ProcessExitReasonHelper.resolveKillReason()` logic or DataStore preference storage.
* Modifying `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()` or workout recovery SQLite transactions.
* Altering `MainActivityWithNavigation.chooseStart()` or `chooseResume()` business logic.

---

## 4. Proposed Architecture & Design Specification

### 4.1 Component Layout Hierarchy (`StartOrResumeDialog.kt`)

```
StartOrResumeDialog (DialogFragment)
 └── ComposeView (setViewCompositionStrategy: DisposeOnViewTreeLifecycleDestroyed)
      └── ATrainingTrackerTheme
           └── Surface (shape: RoundedCornerShape(28.dp), color: surfaceContainerHigh, tonalElevation: 6.dp)
                └── Column (modifier: padding(24.dp), verticalArrangement: spacedBy(16.dp))
                     ├── Header Block (Column: horizontalAlignment = CenterHorizontally)
                     │    ├── Icon (contextual icon based on KillReason, size: 36.dp, tint: semantic)
                     │    ├── Spacer (height: 12.dp)
                     │    └── Text (dynamic title based on KillReason, style: titleLarge bold, align: Center)
                     │
                     ├── Message Content (Column: spacedBy(12.dp))
                     │    ├── If (KillReason != GENERIC_UNFINISHED):
                     │    │    └── Surface (shape: RoundedCornerShape(12.dp), color: surfaceContainer, padding: 12.dp)
                     │    │         └── Text (kill reason explanation + Strava note, style: bodyMedium)
                     │    └── Text (start_or_resume_dialog_message prompt, style: bodyMedium)
                     │
                     ├── Optional Contextual Action:
                     │    └── If (shouldShowBatteryButton):
                     │         └── FilledTonalButton (action_disable_battery_optimization, fullWidth)
                     │
                     └── Action Button Row (Row: spacedBy(8.dp), horizontalArrangement = End)
                          ├── OutlinedButton (start_new_workout)
                          └── Button (resume_workout)
```

---

## 5. Verification & Test Strategy

1. **Contract Tests (`StartOrResumeDialogContractTest.kt`)**:
   - Verify `StartOrResumeDialog` extends `DialogFragment`.
   - Verify `StartOrResumeDialog.TAG` equals class name.
   - Verify `attachInterface` enforces `StartOrResumeInterface`.
2. **Layout & State Verification Tests (`StartOrResumeDialogLayoutTest.kt`)**:
   - Verify icon and title for `BATTERY_KILL`, `LOW_MEMORY`, `PERMISSION_REVOKED`, and `GENERIC_UNFINISHED`.
   - Verify escalation copy for battery stages 1, 2, 3, and Strava active note.
   - Verify button visibility (`shouldShowBatteryButton == true` vs `false`).
   - Verify button click callbacks invoke `chooseStart()`, `chooseResume()`, and `openBatteryOptimizationSettings()`.
3. **9-Language Localization Audit**:
   - Verify all dialog string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
4. **Full Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
