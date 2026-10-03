# Stage 2: Requirement & Test Specification - ATT-2079: Inform Athlete on Process Kill Reasons (Battery Saver, LMK, Permission Revocation) via ApplicationExitInfo

**Ticket**: [ATT-2079](https://rainerblind.atlassian.net/browse/ATT-2079)  
**Sub-task**: [ATT-2216](https://rainerblind.atlassian.net/browse/ATT-2216) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (`REQ-STB-012`)

| Field | Specification |
| :--- | :--- |
| **Requirement ID** | `REQ-STB-012` |
| **Title** | **Forensic Process Kill Diagnosis, Progressive Escalation & Battery Optimization Guidance.** |
| **Category** | Stability / Recovery / User Experience |
| **Scope** | `MainActivityWithNavigation.kt`, `StartOrResumeDialog.java` / `StartOrResumeDialog.kt`, `ProcessExitReasonHelper.kt`, `strings.xml` (all 9 locales) |
| **Status** | Specified |

### Clause Breakdown & Architectural Constraints

1. **Forensic Process Exit Reason Classification (`ProcessExitReasonHelper.kt`)**:
   - When an unfinalized workout exists in SQLite (`WorkoutSummariesDatabaseManager.hasUnfinishedWorkout() == true`), the system SHALL evaluate the underlying cause of process termination:
     - On Android 11+ (API Level 30+), it SHALL query `ActivityManager.getHistoricalProcessExitReasons(packageName, 0, 1)`.
     - If the exit reason is `ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE` or if `PowerManager.isIgnoringBatteryOptimizations(packageName)` is false, the system SHALL classify the termination as `BATTERY_KILL`.
     - If the exit reason is `ApplicationExitInfo.REASON_LOW_MEMORY`, it SHALL classify the termination as `LOW_MEMORY`.
     - If the exit reason is `ApplicationExitInfo.REASON_PERMISSION_CHANGE`, it SHALL classify the termination as `PERMISSION_REVOKED`.
     - On Android 10 and below (API < 30), it SHALL evaluate `PowerManager.isIgnoringBatteryOptimizations(packageName)`: if false, it SHALL classify as `BATTERY_KILL`; otherwise, it SHALL fall back to `GENERIC_UNFINISHED`.
2. **Progressive Escalation Ladder (Battery Kill)**:
   - For `BATTERY_KILL`, the system SHALL maintain a persistent counter `PREF_BATTERY_KILL_COUNT`.
   - Upon each unfinalized workout detected with `BATTERY_KILL`, the counter SHALL be incremented.
   - The diagnostic message presented in `StartOrResumeDialog` SHALL progressively escalate based on `battery_kill_count`:
     - **Stage 1 (`count == 1`)**: Sporty empathy & gentle nudge ("Tja, wer nicht hören will... 😉 Aber es tut mir wirklich leid um deine verlorenen Kilometer!"), plus conditional Strava reminder ("Und du weißt ja: 'If it's not on Strava, it didn't happen'... doppelt bitter! 🙈") if `TrainingApplication.uploadToStrava() == true`.
     - **Stage 2 (`count == 2`)**: Elevated exasperation & athletic sarcasm ("Nicht schon wieder... Schon der 2. Kill! Wie viele verlorene Kilometer brauchst du noch als Beweis?").
     - **Stage 3+ (`count >= 3`)**: Satirical resignation / humorous escalation ("Aller schlechten Dinge sind drei... Beratungsresistenter Athlet des Monats 🏆").
3. **Actionable Remediation & Counter Reset**:
   - For `BATTERY_KILL`, `StartOrResumeDialog` SHALL offer a direct action button: "Disable Battery Optimization" / "Akku-Optimierung deaktivieren", launching `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (or `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
   - When `PowerManager.isIgnoringBatteryOptimizations(packageName)` evaluates to `true` (e.g. upon return from settings or on subsequent resume), `PREF_BATTERY_KILL_COUNT` SHALL be reset to `0`.
4. **Transparent LMK & Permission Revocation Context**:
   - For `LOW_MEMORY`, the dialog SHALL explain that the system terminated background tracking due to acute device RAM starvation (e.g. foreground camera or 3D navigation) and assure the athlete that prior workout metrics were saved.
   - For `PERMISSION_REVOKED`, the dialog SHALL inform the athlete that tracking was halted because location or notification permissions were revoked during the session.
5. **9-Language Parity (`REQ-LOC-001`)**:
   - All diagnostic explanations, escalation stages, Strava tags, and settings action button labels SHALL exist with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
6. **Preserved Invariants**:
   - `REQ-STB-003`: Resumption and start fresh options (`chooseResume()`, `chooseStart()`), SQLite data recovery, and notification resumption (`EXTRA_RESUME_INTERRUPTED_WORKOUT`) MUST remain intact.
   - Clean-room unit test suite 100% pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Extends and enriches `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*) under Epic `ATT-355` (*Good and consistent UI*).
2. **Historical Origin & Commit Trace**:
   - Commit `5a47e7bc` for `ATT-635`.
3. **Root Reason for Existing Formulation**:
   - `REQ-STB-003` ensured that unfinished workouts in SQLite prompt the user for resumption rather than silently starting fresh or discarding prior GPS data. However, the dialog text was minimal and generic, leaving athletes unable to determine whether the app crashed or whether Android killed the process for power/memory saving.
4. **Preservation of Core Invariants**:
   - **Resumption Safety (`REQ-STB-003`)**: `chooseResume()` continues to set `cResumeFromCrash = true` and dispatch `REQUEST_START_TRACKING`. `chooseStart()` continues to discard/finalize the prior unfinished workout in SQLite.
   - **Notification Interception (`REQ-STB-003`)**: Resuming from notification extra (`EXTRA_RESUME_INTERRUPTED_WORKOUT`) bypasses the dialog and resumes immediately.
   - **Crash Immunity (`REQ-STB-008`, `REQ-STB-001`)**: `ApplicationExitInfo` reflection or system service calls must be guarded so devices on API < 30 or custom ROMs never crash.
   - **9-Language Parity (`REQ-LOC-001`)**: Multi-lingual parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Acceptance Criteria (Given-When-Then)

### AC-1: Forensic Process Exit Reason Classification
- **Given** an unfinalized workout in SQLite (`hasUnfinishedWorkout() == true`) on Android 11+ (API 30+),
- **When** `MainActivityWithNavigation.onResume()` triggers the recovery check,
- **Then** `ProcessExitReasonHelper` queries `getHistoricalProcessExitReasons()` and classifies the cause as `BATTERY_KILL`, `LOW_MEMORY`, `PERMISSION_REVOKED`, or `GENERIC_UNFINISHED`.

### AC-2: Progressive Escalation Ladder on Repeat Battery Kills
- **Given** an unfinalized workout classified as `BATTERY_KILL`,
- **When** `StartOrResumeDialog` is displayed,
- **Then** `battery_kill_count` is incremented and the corresponding escalation text is rendered:
  - Count = 1: Stage 1 sympathetic text (+ Strava text if `uploadToStrava == true`).
  - Count = 2: Stage 2 sarcastic text.
  - Count >= 3: Stage 3 satirical resignation text.

### AC-3: Remediation Shortcut & Counter Reset
- **Given** a `BATTERY_KILL` dialog displayed,
- **When** the athlete taps "Disable Battery Optimization",
- **Then** the application launches Android battery optimization settings; once `isIgnoringBatteryOptimizations == true`, `battery_kill_count` resets to 0.

### AC-4: Low Memory Killer (LMK) & Permission Change Context
- **Given** an exit info indicating `REASON_LOW_MEMORY` or `REASON_PERMISSION_CHANGE`,
- **When** `StartOrResumeDialog` is displayed,
- **Then** the dialog clearly communicates that RAM exhaustion or permission revocation halted tracking while reassuring the athlete that existing metrics are preserved.

### AC-5: Backward Compatibility (Android 10 and below)
- **Given** an Android device running API < 30,
- **When** an unfinished workout is detected,
- **Then** the app evaluates `isIgnoringBatteryOptimizations()` safely, rendering `BATTERY_KILL` or standard recovery without throwing `NoSuchMethodError` or crashing.

---

## 4. Test Specification (`TST-STB-012`)

| Field | Specification |
| :--- | :--- |
| **Test ID** | `TST-STB-012` |
| **Ticket** | `ATT-2079` |
| **Requirement Mapping** | `REQ-STB-012` |
| **Scope** | Unit, Helper, Localization Parity, Regression |

### Test Cases

1. **`ProcessExitReasonHelperTest.kt` (Forensic Classification Contract Test)**:
   - `testClassify_excessiveResourceUsage_returnsBatteryKill()`: Verify exit reason `REASON_EXCESSIVE_RESOURCE_USAGE` produces `KillReason.BATTERY_KILL`.
   - `testClassify_notIgnoringBatteryOptimizations_returnsBatteryKill()`: Verify unexempt battery optimization produces `BATTERY_KILL` even when exit info is null.
   - `testClassify_lowMemory_returnsLowMemory()`: Verify exit reason `REASON_LOW_MEMORY` produces `KillReason.LOW_MEMORY`.
   - `testClassify_permissionChange_returnsPermissionRevoked()`: Verify exit reason `REASON_PERMISSION_CHANGE` produces `KillReason.PERMISSION_REVOKED`.
   - `testClassify_apiBelow30_handlesGracefully()`: Verify safe execution without throwing `NoSuchMethodError` on simulated API < 30.

2. **`ProcessKillEscalationTest.kt` (Counter & Escalation Message Test)**:
   - `testBatteryKillCount_incrementsOnEachBatteryKill()`: Verify counter increments progressively (1 -> 2 -> 3).
   - `testBatteryKillCount_resetsToZeroWhenExempted()`: Verify counter is cleared to 0 when `isIgnoringBatteryOptimizations` becomes true.
   - `testStage1_includesStravaMessageWhenUploadEnabled()`: Verify Strava-specific text is appended only when `TrainingApplication.uploadToStrava() == true`.
   - `testStage1_omitsStravaMessageWhenUploadDisabled()`: Verify Strava text is absent when upload is disabled.
   - `testStage2AndStage3_renderAppropriateCopy()`: Verify string keys for Stage 2 and Stage 3+ resolve correctly.

3. **`StartOrResumeDialogContractTest.kt` (Dialog & Actions Contract Test)**:
   - `testDialogRendersDiagnosticTitleAndBody()`: Verify dialog presents formatted kill reason and advice.
   - `testDialogProvidesBatteryOptimizationButton_onBatteryKill()`: Verify "Disable Battery Optimization" action is present when `killReason == BATTERY_KILL`.
   - `testDialogRetainsResumeAndStartActions_REQ_STB_003()`: Verify `chooseResume()` and `chooseStart()` callbacks remain wired to the buttons.

4. **9-Language Localization Audit (`TranslationParityTest.kt`)**:
   - Verify all newly introduced string keys (diagnostic headers, escalation texts, Strava bonus copy, settings action) exist across all 9 locales:
     - `values/strings.xml` (EN)
     - `values-de/strings.xml` (DE)
     - `values-es/strings.xml` (ES)
     - `values-fr/strings.xml` (FR)
     - `values-it/strings.xml` (IT)
     - `values-ja/strings.xml` (JA)
     - `values-nl/strings.xml` (NL)
     - `values-pl/strings.xml` (PL)
     - `values-pt/strings.xml` (PT)

5. **Clean-Room Full Suite Regression Execution**:
   - `./gradlew testDebugUnitTest` verifying 100% pass rate.
