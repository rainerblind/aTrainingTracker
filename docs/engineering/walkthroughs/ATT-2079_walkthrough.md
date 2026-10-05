# Stage 5: Walkthrough & Verification - ATT-2079: Inform Athlete on Process Kill Reasons (Rework Cycle 2)

**Ticket**: [ATT-2079](https://rainerblind.atlassian.net/browse/ATT-2079)  
**Sub-task**: [ATT-2399](https://rainerblind.atlassian.net/browse/ATT-2399) (`[Test]`)  
**Parent Epic**: [ATT-354](https://rainerblind.atlassian.net/browse/ATT-354) (*Stability & Resilience*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `2026-40.16`  
**Requirement Mapping**: `REQ-STB-012` (*Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance*)  
**Test Spec ID**: `TST-STB-012`  
**Branch**: `feature/ATT-2079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the comprehensive verification and release qualification for [ATT-2079](https://rainerblind.atlassian.net/browse/ATT-2079) (Rework Cycle 2).

### Problem Statement & Root Cause (Cycle 2 Rework)
During Sprint 2026-40.15 Joint Review, the previous implementation was bounced for four specific defects:
1. **Generic Dialog Title**: `StartOrResumeDialog` statically rendered `"Unfinished Workout Detected"` regardless of whether the workout was interrupted by battery optimization kills, low memory (LMK), or runtime permission revocation.
2. **Punitive / Sarcastic Copy**: Escalation stages 2 and 3 contained condescending German copy (*"wer nicht hören will, muss fühlen"*, *"Beratungsresistenter Athlet des Monats 🏆"*) that degraded user trust.
3. **Non-Idempotent Escalation Counter**: The escalation counter incremented on every dialog resolution rather than on distinct process kill events, causing screen rotations, configuration changes, or activity recreations to artificially escalate the warning level. Furthermore, the counter failed to reset when the athlete granted battery exemptions.
4. **Violation of Rule 21**: Battery optimization settings navigation fell back to generic Application Details Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) instead of prioritizing specific direct system intent `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### Implemented Solution
1. **Dynamic Rationale Titles**:
   - Added specific dialog title resources across all 9 locales:
     - `unfinished_workout_title_battery` ("Workout Interrupted by Battery Saver")
     - `unfinished_workout_title_memory` ("Workout Interrupted by Low Memory")
     - `unfinished_workout_title_permission` ("Workout Interrupted by Missing Permission")
     - `unfinished_workout_title` ("Unfinished Workout Detected", generic fallback)
   - Updated `StartOrResumeDialog.kt` to dynamically select the title based on `KillReason`.
2. **Empathetic Athletic-Partnership Copy**:
   - Completely rewrote all 3 escalation tiers across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT) with supportive, sportingly constructive phrasing emphasizing athlete partnership.
3. **Idempotent Timestamp Tracking & Auto-Reset**:
   - Added `PREF_LAST_EVALUATED_EXIT_TIMESTAMP` to `ProcessExitReasonHelper.kt`. Repeated evaluations within the same process lifetime or across screen rotations do not re-increment the counter unless a genuinely newer exit timestamp is detected from `ApplicationExitInfo`.
   - Automatically resets `battery_kill_count` to 0 when `isIgnoringBatteryOptimizations` is true.
4. **Direct Platform Intent (Rule 21 Compliance)**:
   - Updated `openBatteryOptimizationSettings` to launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:$packageName` URI directly, falling back safely to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and `ACTION_APPLICATION_DETAILS_SETTINGS` only if the direct intent is blocked by OEM ROM restrictions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-012` | `TST-STB-012.1` | Unit Tests: `ProcessExitReasonHelperTest.kt` (Classification, Idempotency, Timestamp tracking, Auto-reset, Direct intent) | **PASSED** (10/10 tests) | `Verified` |
| `REQ-STB-012` | `TST-STB-012.2` | Unit Tests: `ProcessKillEscalationTest.kt` (Empathetic tone, escalation tiers, Strava context) & `StartOrResumeDialogContractTest.kt` | **PASSED** (9/9 tests) | `Verified` |
| `REQ-LOC-001` | `TST-STB-012.3` | 9-Language Localization Parity Audit: Dynamic titles, de-escalated copy, Strava text across all 9 locales | **PASSED** (100% parity) | `Verified` |
| `REQ-PRO-001` | `TST-STB-012.4` | Clean-Room Full Suite Regression: `./gradlew testDebugUnitTest` | **PASSED** (100% clean) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests (`helpers.*` & `dialogs.*`)
```text
BUILD SUCCESSFUL in 8s
32 actionable tasks: 1 executed, 31 up-to-date
- ProcessExitReasonHelperTest:
  • testClassify_excessiveResourceUsage_returnsBatteryKill: PASSED
  • testClassify_lowMemory_returnsLowMemory: PASSED
  • testClassify_userRequested_returnsGeneric: PASSED
  • testClassify_permissionRevocation_returnsPermissionRevoked: PASSED
  • testClassify_emptyHistoricalReasons_returnsGeneric: PASSED
  • testResolveKillReason_belowAndroidR_returnsGeneric: PASSED
  • testResolveKillReason_batteryKill_whenAlreadyExempt_returnsGeneric: PASSED
  • testResolveKillReason_batteryKill_escalatesStages: PASSED
  • testResolveKillReason_sameExitTimestamp_doesNotIncrementEscalation: PASSED
  • testOpenBatteryOptimizationSettings_launchesActionRequestIgnoreBatteryOptimizationsFirst: PASSED
- ProcessKillEscalationTest:
  • testEscalationTiers_doNotContainSarcasm: PASSED
  • testEscalationTone_isSportinglyConstructive: PASSED
  • testStage1_containsStravaAdviceWhenActive: PASSED
  • testStage1_omitsStravaAdviceWhenInactive: PASSED
  • testStage2_recommendsDisablingBatterySaver: PASSED
  • testStage3_warnsOfPersistentKills: PASSED
  • testEscalationLevel_capsAtThree: PASSED
  • testEscalationLevel_resetsWhenOptimizationsIgnored: PASSED
- StartOrResumeDialogContractTest:
  • testContractCompliance: PASSED
```

---

## 4. Invariant & Governance Verification

1. **Rule 21 Compliance**: Specific direct platform intent (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) with package URI is prioritized over generic application details settings.
2. **Rule 1 Compliance**: Parent ticket `ATT-2079` is transitioned to `Final Review (Human)` (never `Erledigt`).
3. **Rule 6 Compliance**: Sub-tasks `ATT-2395`, `ATT-2396`, `ATT-2397`, `ATT-2398`, `ATT-2399` have empty `fixVersions`.
4. **Living Documentation Synchronized**: `docs/requirements.md` (`REQ-STB-012`) and `docs/tests.md` (`TST-STB-012`) updated and confirmed in state `Verified`.
5. **Continuous Sprint Branch Integration (Strategy A)**: Feature branch `feature/ATT-2079` merged into `sprint/2026-40.16` via `--no-ff`.
