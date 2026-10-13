# Stage 2: Requirement & Test Specification - ATT-2079: Inform Athlete on Process Kill Reasons (Battery Saver, LMK, Permission Revocation) via ApplicationExitInfo (Rework Cycle 2)

**Ticket**: [ATT-2079](https://rainerblind.atlassian.net/browse/ATT-2079)  
**Sub-task**: [ATT-2396](https://rainerblind.atlassian.net/browse/ATT-2396) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per Rule 19: Lösungsversion added upon completion)  
**Active Sprint**: `Sprint 2026-40.16`  
**Branch**: `feature/ATT-2079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (`REQ-STB-012`)

| Field | Specification |
| :--- | :--- |
| **Requirement ID** | `REQ-STB-012` |
| **Title** | **Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance.** |
| **Category** | Stability / Recovery / User Experience |
| **Scope** | `MainActivityWithNavigation.kt`, `StartOrResumeDialog.kt`, `ProcessExitReasonHelper.kt`, `strings.xml` (all 9 locales) |
| **Status** | Specified |

### Clause Breakdown & Architectural Constraints

1. **Dynamic Dialog Title Based on Concrete Termination Cause**:
   - `StartOrResumeDialog` SHALL dynamically adapt its title to reflect the diagnosed kill reason:
     - `KillReason.BATTERY_KILL`: `R.string.unfinished_workout_title_battery` (*"Training unterbrochen: Akku-Optimierung"* / *"Workout Interrupted: Battery Optimization"*).
     - `KillReason.LOW_MEMORY`: `R.string.unfinished_workout_title_memory` (*"Training unterbrochen: Speicherengpass"* / *"Workout Interrupted: Low Memory"*).
     - `KillReason.PERMISSION_REVOKED`: `R.string.unfinished_workout_title_permission` (*"Training unterbrochen: Berechtigung entzigen"* / *"Workout Interrupted: Permission Revoked"*).
     - `KillReason.GENERIC_UNFINISHED`: `R.string.unfinished_workout_title` (*"Training unterbrochen"* / *"Workout Interrupted"*).

2. **Constructive Athletic-Partnership Tone & Copy Architecture**:
   - The diagnostic escalation copy across all stages SHALL be objective, constructive, and empathetic, strictly avoiding sarcasm, mockery, or condescending phrases (*"wer nicht hören will"*, *"Beratungsresistenter Athlet des Monats"*).
   - **Stage 1 (`count == 1`)**: Empathetic explanation of Android background battery management interrupting the service, advising exemption to protect future workouts.
   - **Strava Extension**: Sporty and motivational acknowledgment of the importance of complete workout telemetry for Strava.
   - **Stage 2 (`count == 2`)**: Clear notice of repeated interruption by system battery saver, urging the athlete to take a moment to configure exemption.
   - **Stage 3 (`count >= 3`)**: Objective statement explaining that modern Android OS strictly shuts down apps without an exemption, with direct link to fix it.

3. **Idempotent, Session-Bound Escalation Counter**:
   - The escalation counter `PREF_BATTERY_KILL_COUNT` SHALL be decoupled from read operations and screen rotation events.
   - `ProcessExitReasonHelper` SHALL track `PREF_LAST_EVALUATED_EXIT_TIMESTAMP`.
   - Repeated calls to `resolveKillReason(context)` (e.g. on configuration changes, screen rotation, activity re-creation) SHALL NOT increment `PREF_BATTERY_KILL_COUNT`.
   - `PREF_BATTERY_KILL_COUNT` SHALL ONLY increment when a new, distinct termination event timestamp is detected.
   - When `isIgnoringBatteryOptimizations(context)` evaluates to `true`, `PREF_BATTERY_KILL_COUNT` SHALL automatically reset to `0`.

4. **Direct Intent Governance (Rule 21)**:
   - When the athlete taps the battery optimization button, `ProcessExitReasonHelper.openBatteryOptimizationSettings` SHALL prioritize `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `Uri.parse("package:$packageName")`.
   - Fallback to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` SHALL only occur upon exception catch on restricted OEM ROMs.

5. **9-Language Parity (`REQ-LOC-001`)**:
   - All newly introduced titles, de-escalated copy variants, and action buttons SHALL exist with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

6. **Preserved Invariants (`REQ-STB-003`)**:
   - Resumption options (`chooseResume()`, `chooseStart()`), SQLite data recovery, and notification resumption (`EXTRA_RESUME_INTERRUPTED_WORKOUT`) MUST remain 100% intact.

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-STB-012` (*Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance*), which enriches `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*).
2. **Historical Origin & Commit Trace**:
   - Commit `5a47e7bc` for `ATT-635` and Sprint 2026-40.14 (`ATT-2079`).
3. **Root Reason for Existing Formulation**:
   - Cycle 1 established basic `ApplicationExitInfo` query and progressive escalation. However, Sprint 2026-40.15 review revealed that generic titles failed to explain the issue up front, the sarcastic tone alienated athletes, and non-idempotent resolution caused screen rotations to falsely advance the counter directly to Stage 3.
4. **Preservation of Core Invariants**:
   - Workout resumption contracts (`chooseResume()`, `chooseStart()`), SQLite data recovery, background notification resumption (`EXTRA_RESUME_INTERRUPTED_WORKOUT`), crash immunity (`REQ-STB-008`), and 9-language translation parity (`REQ-LOC-001`) remain 100% intact.

---

## 2. Acceptance Criteria (Given-When-Then)

### AC-1: Dynamic Rationale Dialog Title
- **Given** an unfinalized workout detected in SQLite,
- **When** `StartOrResumeDialog` is displayed,
- **Then** the dialog title dynamically renders the specific cause (`unfinished_workout_title_battery`, `unfinished_workout_title_memory`, `unfinished_workout_title_permission`, or generic `unfinished_workout_title`).

### AC-2: De-escalated Empathetic Copy
- **Given** an unfinalized workout classified as `BATTERY_KILL`,
- **When** the message body is constructed,
- **Then** the rendered text uses constructive, empathetic athletic-partnership phrasing free of sarcasm or scolding across all escalation stages (1, 2, and 3).

### AC-3: Idempotent Counter (Screen Rotation Immunity)
- **Given** `StartOrResumeDialog` is displayed with kill count $N$,
- **When** the athlete rotates the device or triggers an Activity recreation,
- **Then** `battery_kill_count` remains exactly $N$ and does not falsely jump to $N+1$ or Stage 3.

### AC-4: Direct Platform Intent (Rule 21)
- **Given** a `BATTERY_KILL` recovery dialog,
- **When** the athlete taps the battery optimization remediation button,
- **Then** `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is dispatched directly with package URI.

### AC-5: Counter Reset on Exemption
- **Given** a device that was exempted from battery optimization (`isIgnoringBatteryOptimizations == true`),
- **When** `resolveKillReason` or resume check is evaluated,
- **Then** `PREF_BATTERY_KILL_COUNT` is reset to 0.

---

## 3. Test Specification (`TST-STB-012`)

| Field | Specification |
| :--- | :--- |
| **Test ID** | `TST-STB-012` |
| **Ticket** | `ATT-2079` |
| **Requirement Mapping** | `REQ-STB-012` |
| **Scope** | Unit, Helper, Dialog, Localization Parity, Regression |

### Test Cases

1. **`ProcessExitReasonHelperTest.kt` (Forensic Classification & Idempotence)**:
   - `testClassify_excessiveResourceUsage_returnsBatteryKill()`: Validates `REASON_EXCESSIVE_RESOURCE_USAGE` -> `BATTERY_KILL`.
   - `testResolveKillReason_repeatedCalls_doesNotIncrementCounter()`: Simulates multiple invocations / screen rotations; asserts counter does not increase without a new timestamp.
   - `testResolveKillReason_newExitTimestamp_incrementsCounter()`: Simulates a subsequent process kill with newer timestamp; asserts counter increments by 1.
   - `testResolveKillReason_whenBatteryOptimizationIgnored_resetsCounter()`: Asserts counter resets to 0 when app is whitelisted.
   - `testOpenBatteryOptimizationSettings_launchesActionRequestIgnoreBatteryOptimizationsFirst()`: Asserts Rule 21 direct intent is dispatched with `package:` URI.

2. **`StartOrResumeDialogContractTest.kt` (Dynamic Title & Action Binding)**:
   - `testDialogTitle_batteryKill_showsBatteryTitle()`: Asserts `R.string.unfinished_workout_title_battery` is set as dialog title.
   - `testDialogTitle_lowMemory_showsMemoryTitle()`: Asserts `R.string.unfinished_workout_title_memory` is set as dialog title.
   - `testDialogTitle_permissionRevoked_showsPermissionTitle()`: Asserts `R.string.unfinished_workout_title_permission` is set as dialog title.
   - `testDialogTitle_generic_showsDefaultTitle()`: Asserts `R.string.unfinished_workout_title` is set as default dialog title.
   - `testDialogCopy_isDeescalatedAndEmpathetic()`: Asserts copy contains constructive partnership keywords and zero sarcasm.

3. **9-Language Localization Audit (`TranslationParityTest.kt`)**:
   - Verify `unfinished_workout_title_battery`, `unfinished_workout_title_memory`, `unfinished_workout_title_permission`, and updated `kill_reason_battery_stage1..3` exist with 100% parity across all 9 language directories.

4. **Full Clean-Room Suite Regression**:
   - Run `./gradlew testDebugUnitTest` asserting 100% pass rate.
