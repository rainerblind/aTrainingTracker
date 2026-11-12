# Stage 5: Walkthrough & Verification - ATT-7316: Fix ForgottenCoroutineScopeException showing false 'Failed to share' toast

**Ticket**: [ATT-7316](https://rainerblind.atlassian.net/browse/ATT-7316)  
**Sub-task**: Pending `submit-stage` (`[Test]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-REV-006` (subclause 5.d / `REQ-REV-006.5`)  
**Test Mapping**: `TST-REV-007` (subclause 6 / `TST-REV-007.6`)  
**Branch**: `subagent-Lane-3-Agent---ATT-7316-self-f893689e`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
During workout map snapshot sharing, launching Android's system share sheet (`ChooserActivity`) caused the hosting bottom sheet or composable to unmount, prompting `rememberCoroutineScope()` to cancel running jobs with `ForgottenCoroutineScopeException` (which inherits from `CancellationException`). The unspecialized `catch (t: Throwable)` block in `executeSnapshotCaptureAndShare` routed this cancellation to `onError`, posting a false failure toast (`R.string.error_sharing_failed`: *"Unable to generate share image. Please try again."*) despite the sharing operation succeeding.

### Resolution:
1. `MapDetailLayout.kt`:
   - Wrapped `shareAction` in `withContext(NonCancellable)` inside `executeSnapshotCaptureAndShare` to ensure file operations and share sheet launch complete without interruption.
   - Added an explicit `catch (_: CancellationException)` block in `executeSnapshotCaptureAndShare` to safely ignore coroutine cancellation (including `ForgottenCoroutineScopeException`), preventing false failure toasts.
   - Defensively guarded `onError` with `if (t !is CancellationException)`.
   - Guaranteed `onFinally` strictly resets `isSharing = false` and invokes `onSnapshotComplete?.invoke()` in the `finally` block under all execution paths.
2. `MapDetailLayoutSnapshotContractTest.kt`:
   - Added `testMapDetailLayout_onSnapshotReady_whenCancellationExceptionThrown_doesNotInvokeOnError_TST_REV_007_6`.
   - Added `testMapDetailLayout_onSnapshotReady_whenForgottenCoroutineScopeException_doesNotInvokeOnError_TST_REV_007_6`.
   - Added `testMapDetailLayout_onSnapshotReady_whenNonCancellationExceptionThrownInShareAction_invokesOnError_TST_REV_007_6`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-006.5.d` (NonCancellable Share Action) | `TST-REV-007.6` | Contract Test | **PASSED** | `Verified` |
| `REQ-REV-006.5.e` (Ignore CancellationException & ForgottenCoroutineScopeException) | `TST-REV-007.6` | Contract Test | **PASSED** | `Verified` |
| `REQ-REV-006.5.f` (Defensive onError Guard) | `TST-REV-007.6` | Contract Test | **PASSED** | `Verified` |
| `REQ-REV-006.5.g` (Guaranteed onFinally Cleanup) | `TST-REV-007.6` | Contract Test | **PASSED** | `Verified` |
| `REQ-REV-006.5` (Tier 1 Targeted Regression) | `TST-REV-007.6` | Targeted Gradle Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutSnapshotContractTest
BUILD SUCCESSFUL in 1m 47s
```

### Full Map & UI Component Regression Suite
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" \
                            --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"
BUILD SUCCESSFUL in 35s
33 actionable tasks: 1 executed, 32 up-to-date
```

---

## 4. Verification & Clean-Room Audit
* **Requirement Governance Audit**:
  ```text
  python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-7316_test_spec.md
  PASS: Living documentation master tables match all 9 domain shards with 100% parity.
  PASS: Zero unmounted Composable islands detected in git diff.
  PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
  PASS: Zero architecture or system specification modifications detected.
  Modified/Altered existing requirement(s) detected: REQ-REV-006.
  PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
  ```
* **Git Status Cleanliness**: Submodules verified, zero unexpected untracked artifacts.
