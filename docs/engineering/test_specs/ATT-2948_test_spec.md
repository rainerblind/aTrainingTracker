# Stage 2 Requirement & Test Specification: ATT-2948 - Modernize StartOrResume workout recovery dialog with Material 3 Jetpack Compose

**Ticket**: [ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948)  
**Sub-task**: [ATT-3020](https://atrainingtracker.atlassian.net/browse/ATT-3020) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2948`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-329)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-UI-329`
* **Title**: Material 3 Jetpack Compose Workout Recovery Dialog Architecture & Contextual Kill Diagnosis Presentation
* **Type**: Architectural & UI Specification
* **Target Release**: `V4.9.39`
* **Status**: Specified
* **Amends/Complements**: Amends `REQ-STB-012` (*Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance*, ATT-2079) and harmonizes with `REQ-STB-003` (*Workout Resumption Contracts*) and Epic `ATT-355` (*Good and consistent UI*).
* **Parent Ticket**: ATT-2948

### 1.2 Description
The system shall modernize `StartOrResumeDialog.kt` from a legacy Android View dialog to a Material 3 Jetpack Compose dialog architecture, featuring contextual kill reason iconography, structured message containerization, clear action button hierarchy, and full theme integration:

1. *Compose Dialog Migration & DialogFragment Contract*:
   - `StartOrResumeDialog` SHALL continue to inherit `DialogFragment` (`androidx.fragment.app.DialogFragment`), strictly preserving `StartOrResumeDialog.TAG`, `attachInterface(Context)`, and `StartOrResumeInterface` callbacks (`chooseStart()`, `chooseResume()`).
   - In `onCreateView` (or `onCreateDialog`), the dialog view hierarchy SHALL be hosted inside `ComposeView` with `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, wrapped in `ATrainingTrackerTheme`.
   - The underlying dialog window SHALL configure a transparent background (`dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)`), delegating modal surface geometry, rounded corners (`RoundedCornerShape(28.dp)`), container coloring (`MaterialTheme.colorScheme.surfaceContainerHigh`), and tonal elevation (`6.dp`) to Material 3 `Surface`.
2. *Contextual Semantic Iconography & Header Hierarchy*:
   - The dialog header SHALL display a dedicated 36dp semantic icon and bold title (`MaterialTheme.typography.titleLarge`, `FontWeight.Bold`, centered alignment) dynamically mapped to `KillDiagnosis.reason`:
     * `KillReason.BATTERY_KILL`: `Icons.Default.BatteryAlert` with semantic warning/error accent, displaying `R.string.unfinished_workout_title_battery`.
     * `KillReason.LOW_MEMORY`: `Icons.Default.Memory` with primary accent, displaying `R.string.unfinished_workout_title_memory`.
     * `KillReason.PERMISSION_REVOKED`: `Icons.Default.Security` with semantic error accent, displaying `R.string.unfinished_workout_title_permission`.
     * `KillReason.GENERIC_UNFINISHED`: `Icons.Default.DirectionsRun` with primary accent, displaying `R.string.unfinished_workout_title`.
3. *Structured Message Container & Typographic Separation*:
   - When `diagnosis.reason != KillReason.GENERIC_UNFINISHED`, the forensic explanation (including progressive escalation stages 1, 2, 3, and Strava activity note) SHALL be formatted inside a subtle tinted callout container (`MaterialTheme.colorScheme.surfaceContainer` with `RoundedCornerShape(12.dp)` and `padding(12.dp)`).
   - The user decision prompt (`R.string.start_or_resume_dialog_message`) SHALL be rendered distinctly in `MaterialTheme.typography.bodyMedium` (`color = MaterialTheme.colorScheme.onSurfaceVariant`) below the diagnostic callout container.
4. *Action Button Hierarchy*:
   - Primary action: "Fortsetzen" (`R.string.resume_workout`) SHALL be rendered as a filled `Button`, invoking `startOrResumeInterface?.chooseResume()` and dismissing the dialog upon tap.
   - Secondary action: "Neues Training" (`R.string.start_new_workout`) SHALL be rendered as an `OutlinedButton`, invoking `startOrResumeInterface?.chooseStart()` and dismissing the dialog upon tap.
   - Contextual action: When `diagnosis.shouldShowBatteryButton == true`, the dialog SHALL render a full-width `FilledTonalButton` or `OutlinedButton` labeled "Akku-Optimierung deaktivieren" (`R.string.action_disable_battery_optimization`), invoking `ProcessExitReasonHelper.openBatteryOptimizationSettings(context)` and dismissing the dialog upon tap.
5. *9-Language Localization Parity*:
   - All string resources utilized in `StartOrResumeDialog` SHALL be maintained with 100% translation parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. *Preservation of System Invariants*:
   - `StartOrResumeInterface` contracts and `StartOrResumeDialogContractTest.kt` inheritance assertions MUST NOT be broken.
   - Direct system intent for battery optimization (`ProcessExitReasonHelper.openBatteryOptimizationSettings`) MUST remain fully functional.
   - 100% clean-room test pass rate across all unit tests.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Amends `REQ-STB-012` (*Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance*, ATT-2079).
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.16 (`ATT-2079`, commit `95e0c511`).
3. **Root Reason for Existing Formulation**: The initial implementation focused on integrating Android 11+ `ApplicationExitInfo` and `PowerManager` logic. The UI was wrapped inside `AlertDialog.Builder` as a quick expedient to ensure feature delivery without refactoring to Compose during that sprint.
4. **Preservation of Core Invariants**: Resumption contracts (`chooseStart()`, `chooseResume()`), direct battery optimization settings intent, idempotent escalation counting, and 100% 9-language localization parity remain strictly preserved.

---

## 2. Acceptance Criteria (Given-When-Then)

* **Scenario 1: Contextual Iconography & Header Styling**:
  - *Given* an interrupted workout detected with kill reason `BATTERY_KILL`,
  - *When* `StartOrResumeDialog` renders,
  - *Then* the dialog displays `Icons.Default.BatteryAlert` and title "Workout Interrupted: Battery Optimization" in `titleLarge` bold font.
  - *Given* an interrupted workout detected with kill reason `LOW_MEMORY`,
  - *When* `StartOrResumeDialog` renders,
  - *Then* the dialog displays `Icons.Default.Memory` and title "Workout Interrupted: Low Memory".
  - *Given* an interrupted workout detected with kill reason `PERMISSION_REVOKED`,
  - *When* `StartOrResumeDialog` renders,
  - *Then* the dialog displays `Icons.Default.Security` and title "Workout Interrupted: Permission Revoked".
  - *Given* an interrupted workout with `GENERIC_UNFINISHED`,
  - *When* `StartOrResumeDialog` renders,
  - *Then* the dialog displays `Icons.Default.DirectionsRun` and title "Workout Interrupted".

* **Scenario 2: Structured Diagnostic Callout & Prompt Separation**:
  - *Given* a termination reason with diagnostic details (`BATTERY_KILL`, `LOW_MEMORY`, or `PERMISSION_REVOKED`),
  - *When* `StartOrResumeDialog` renders,
  - *Then* the diagnostic explanation is enclosed within a tinted callout container,
  - *And* the resume prompt (`start_or_resume_dialog_message`) is rendered separately below the container.

* **Scenario 3: Action Button Hierarchy & Callbacks**:
  - *Given* `StartOrResumeDialog` displayed on screen,
  - *When* inspecting action buttons,
  - *Then* "Resume prev." is rendered as a filled `Button` and "Start new" is rendered as an `OutlinedButton`.
  - *When* the athlete taps "Resume prev.",
  - *Then* `startOrResumeInterface?.chooseResume()` is invoked and the dialog dismisses.
  - *When* the athlete taps "Start new",
  - *Then* `startOrResumeInterface?.chooseStart()` is invoked and the dialog dismisses.

* **Scenario 4: Contextual Battery Optimization Button**:
  - *Given* `diagnosis.shouldShowBatteryButton == true`,
  - *When* `StartOrResumeDialog` renders,
  - *Then* the "Disable Battery Optimization" button is visible,
  - *And* tapping it invokes `ProcessExitReasonHelper.openBatteryOptimizationSettings()`.
  - *Given* `diagnosis.shouldShowBatteryButton == false`,
  - *When* `StartOrResumeDialog` renders,
  - *Then* the "Disable Battery Optimization" button is hidden.

* **Scenario 5: 9-Language Localization Parity**:
  - *Given* all string resources utilized in `StartOrResumeDialog`,
  - *When* inspecting strings across EN, DE, ES, FR, IT, JA, NL, PL, and PT,
  - *Then* all strings are defined and non-blank.

---

## 3. Test Specification (TST-UI-289)

### 3.1 Unit & Contract Tests (`StartOrResumeDialogContractTest.kt` & `StartOrResumeDialogLayoutTest.kt`)

1. *Structural & Inheritance Contract Tests (`StartOrResumeDialogContractTest.kt`)*:
   - `testStartOrResumeDialog_inheritsDialogFragment`: Verifies `dialog is DialogFragment`.
   - `testStartOrResumeDialog_tagMatchesClassName`: Verifies `StartOrResumeDialog.TAG == StartOrResumeDialog::class.java.name`.
   - `testAttachInterface_throwsClassCastExceptionWhenInterfaceNotImplemented`: Verifies interface requirement.
   - `testAttachInterface_succeedsWhenInterfaceImplemented`: Verifies callback binding.

2. *Composable Layout & State Tests (`StartOrResumeDialogLayoutTest.kt`)*:
   - `testContextualIconAndTitle_batteryKill`: Asserts `Icons.Default.BatteryAlert` and `R.string.unfinished_workout_title_battery`.
   - `testContextualIconAndTitle_lowMemory`: Asserts `Icons.Default.Memory` and `R.string.unfinished_workout_title_memory`.
   - `testContextualIconAndTitle_permissionRevoked`: Asserts `Icons.Default.Security` and `R.string.unfinished_workout_title_permission`.
   - `testContextualIconAndTitle_genericUnfinished`: Asserts `Icons.Default.DirectionsRun` and `R.string.unfinished_workout_title`.
   - `testDiagnosticCallout_batteryStagesAndStrava`: Verifies text resolution for stages 1, 2, 3, and Strava note.
   - `testBatteryButton_visibilityGating`: Verifies `shouldShowBatteryButton` controls visibility.
   - `testButtonCallbacks_invocation`: Verifies `onStartNew`, `onResume`, and `onDisableBatteryOptimization` triggers.

3. *9-Language Localization Audit (`StartOrResumeLocalizationTest.kt`)*:
   - Verifies all recovery dialog string resources across EN, DE, ES, FR, IT, JA, NL, PL, PT.

4. *Clean-Room Full Regression Suite*:
   - Run `./gradlew testDebugUnitTest` verifying 100% test pass rate across all unit tests.

---

## 4. Traceability Matrix

| Requirement Clause | Test Specification ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-329` (Clause 1) | `TST-UI-289.1` | `StartOrResumeDialogContractTest` | Specified |
| `REQ-UI-329` (Clause 2) | `TST-UI-289.2` | `StartOrResumeDialogLayoutTest` | Specified |
| `REQ-UI-329` (Clause 3) | `TST-UI-289.3` | `StartOrResumeDialogLayoutTest` | Specified |
| `REQ-UI-329` (Clause 4) | `TST-UI-289.4` | `StartOrResumeDialogLayoutTest` | Specified |
| `REQ-UI-329` (Clause 5) | `TST-UI-289.5` | `StartOrResumeLocalizationTest` | Specified |
| `REQ-UI-329` (Clause 6) | `TST-UI-289.6` | Clean-room full test suite | Specified |
