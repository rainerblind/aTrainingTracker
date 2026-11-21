# Stage 5: Walkthrough & Verification - ATT-9244: Stop PebbleService When appMessages Unsupported & Close PebbleView.db Cleanly

**Ticket**: `ATT-9244` (`1 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-235` (*No Crashes & App Stability*) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` | **Requirement**: `REQ-STB-024` | **Test Spec**: `TST-STB-024`  
**Branch**: `feature/ATT-9244` (Verified Code SHA: `146eb843`) | **Author**: AI Agent 1 (`Flash`) | **Date**: 2026-11-21

## 1. Executive Summary & Verification Overview
* **Implemented Fix (`146eb843`)**:
  * `PebbleService.java`: Added testable `isAppMessagesSupported()`, early `stopSelf()` + `START_NOT_STICKY` in `onCreate()`/`onStartCommand()`/`startPebbleWatchApp()` before binding `BANALService`, registering receivers, or opening `PebbleView.db`; guarded `onDestroy()` & `stopPebbleWatchApp()` (`mIsBanalBound`, `mBaseReceiversRegistered`, `mReceiversRegistered` reset); routed `updateSensorTypeList()` through `PebbleDatabaseManager.getInstance(this).getSensorTypeList(mViewId)` preserving `mRows`; invoked `PebbleDatabaseManager.closeIfInitialized()` on destroy.
  * `PebbleDatabaseManager.java`: Added `synchronized` `isOpen()` reopen guard in `getDatabase()` plus `close()`, `closeIfInitialized()`, and test visibility helpers so `PebbleView.db` closes cleanly while `ConfigPebbleViews*` transparently reopen.
  * `TrainingApplication.java` untouched (`<= 2,135` LoC); `Localization Parity: N/A - no UI strings`.
* **Rule 7.5 Doc-Only Fast Path**: Zero production/test source changes since Gate 4 (`ATT-9419` PASS @ `146eb843`); reused Gate 4's verified test evidence directly.

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-024.1`, `.2` | `TST-STB-024.1` | `PebbleServiceLifecycleTest` (Robolectric) | **PASSED** (`2/2` unsupported/mid-session) | `Verified` |
| `REQ-STB-024.2`, `.3` | `TST-STB-024.2` | `PebbleServiceLifecycleTest` (Robolectric) | **PASSED** (`1/1` supported + teardown) | `Verified` |
| `REQ-STB-024.3` | `TST-STB-024.3` | `PebbleDatabaseManagerTest` (Robolectric SQLite) | **PASSED** (`2/2` close/reopen) | `Verified` |
| `REQ-STB-024.4`, `REQ-STB-016` | `TST-STB-024.4` | Lifecycle/Cursor/Arch/Manifest + Governance + 5 Linters | **PASSED** (`18/18` + `5/5` linters) | `Verified` |

## 3. Automated Test Evidence (Reused Gate 4 SHA `146eb843`)
* **Gradle Tier 1 Targeted Suite** (`./gradlew :app:testDebugUnitTest --rerun --tests "*Pebble*" --tests "com.atrainingtracker.architecture.LifecycleServiceReceiverSafetyAuditTest" --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`): **`23/23 PASS` (`0` fail, `0` skip, `BUILD SUCCESSFUL`)**:
  * `PebbleServiceLifecycleTest`: `3/3 PASS`
  * `PebbleDatabaseManagerTest`: `2/2 PASS`
  * `ArchitectureConformanceContractTest`: `11/11 PASS` (`TrainingApplication.java <= 2,135` LoC)
  * `TryWithResourcesCursorAuditTest`: `5/5 PASS`
  * `LifecycleServiceReceiverSafetyAuditTest`: `1/1 PASS`
  * `ManifestAndBuildConfigContractTest`: `1/1 PASS`
* **Safety & Baseline Linters (`5/5 PASS`)**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py` (`9,121` files), `check_static_analysis_baselines.py --base-ref sprint/2026-11-20_03_Nacht` (`detekt` 9509, `lint` 216, `0` `Recycle`).

## 4. Target Device & Visual Consistency
* **Target Verification**: Deferred to Stage 6 Cold Final Review on `emulator-5554` (`Rule 3.3`, `REQ-TST-004`).
* **Visual Consistency**: No UI changes (`Localization Parity: N/A - no UI strings`).

## 5. Invariant & Governance Verification
* `REQ-STB-024` & `TST-STB-024` promoted to `[Verified]`; `docs/requirements.md` and `docs/tests.md` synchronized (`--sync-indexes`).
* `verify_requirement_governance.py --base-ref a88a0672 --check-verified-evidence --test-results-dir /tmp/empty_check_9244`: **PASS** (exit `0`).
* `feature/ATT-9244` kept intact for Stage 6 Cold Final Review (`Rule 3.3`, `Rule 8.2`); Human Decision Gate (`Final Review (Human)`) preserved (`Rule 3.1`).
