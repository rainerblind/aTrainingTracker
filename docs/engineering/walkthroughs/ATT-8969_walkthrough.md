# Stage 5: Walkthrough & Verification - ATT-8969: Polar Retry Sync Lifecycle Safety & Disconnected Stop Fast-Fail

**Ticket**: `ATT-8969` (`2 SP`, `severity-major`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-19_01_Früh`  
**Requirement Mapping**: `REQ-SET-119` (`REQ-SET-119.1`..`.3`) | **Test Mapping**: `TST-SET-121` (`TST-SET-121.1`..`.4`)  
**Branch**: `feature/ATT-8969` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-19

---

## 1. Executive Summary & Verification Overview
* **Lifecycle-Safe Aftermath Retry (`REQ-SET-119.1`)**: Added `PolarRetrySyncCoordinator` (`SupervisorJob() + Dispatchers.IO`, per-`workoutId` deduplication, reactive `RetrySyncState` `StateFlow`, `completedWorkoutEvents` `SharedFlow`) and refactored `PolarPendingRetrySyncSection` (`PolarH10RetrySyncBanner.kt`) to observe coordinator state via `collectAsState()`, eliminating `ForgottenCoroutineScopeException` and banner flicker; added `CancellationException` rethrow in `PolarVeritySenseOfflineController.kt` and `PolarH10OfflineController.kt`.
* **HR Telemetry Graph Refresh (`REQ-SET-119.2`)**: Wired `PolarRetrySyncCoordinator.completedWorkoutEvents` in `WorkoutSummariesTabbedScreen.kt` and `WorkoutSummariesListFragment.kt` to invoke `TrackOnMapAftermathViewModel.loadAftermathData(...)` for the active workout (`WorkoutRepository.kt` untouched per Lane A isolation).
* **Disconnected Stop Fast-Fail (`REQ-SET-119.3`)**: Added `PolarOfflineGattCoordinator.isTargetConfirmedDisconnected(...)` (`apiOverride == null`) and `STOP_CONNECTION_TIMEOUT_MS = 5_000L` bounded `connectWithinTimeout(...)` in `PolarVeritySenseOfflineController.kt` and `PolarH10OfflineController.kt`, preserving `setPolarSyncPending()` and `TrackerService.java = 3,556` LoC (`+0` LoC).
* **Verification Boundary (`Rule 7.5` & `Rule 7.6`)**: Production and test code are unchanged since Gate 4 (`4dbe0265`); Stage 4 (`155/155`) and Gate 4 (`112/112` targeted, `0` new warnings) Gradle results are reused directly. Live `emulator-5554` verification runs in Stage 6; physical Pixel 10 + Polar Verity Sense hardware verification remains PO acceptance per `Rule 7.6`.

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result (Reused Stage 4 / Gate 4) | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-119.1` | `TST-SET-121.1`, `.2` | Unit + Compose `createComposeRule()` (`PolarRetrySyncCoordinatorTest`, `PolarH10RetrySyncBannerTest`, `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`) | **PASSED** (`3/3`, `3/3`, `28/28`, `20/20`) | `Verified` |
| `REQ-SET-119.2` | `TST-SET-121.3` | Unit (`PolarRetrySyncCoordinatorTest`, `TrackOnMapAftermathViewModelTest`) | **PASSED** (`3/3`, `9/9`) | `Verified` |
| `REQ-SET-119.3` | `TST-SET-121.4` | Unit (`PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, `TrackerServiceExportGatingTest`) | **PASSED** (`28/28`, `20/20`, `1/1`) | `Verified` |

---

## 3. Automated Verification & Governance Evidence
* **Stage 4 Targeted + Contract Suite (`4dbe0265`, `--rerun-tasks --console=plain`)**: **155/155 PASSED** (`0` failed, `0` skipped, `0` new compiler warnings):
  * `polar.*`: `PolarVeritySenseOfflineControllerTest` (`28/28`), `PolarH10OfflineControllerTest` (`20/20`), `PolarRetrySyncCoordinatorTest` (`3/3`), `PolarOnlineStreamingBridgeTest` (`24/24`), `PolarOfflineGattCoordinatorCoexistenceTest` (`5/5`), `PolarRawTelemetryRecorderTest` (`6/6`), `PolarLiveRunningCadenceEstimatorTest` (`5/5`), `PolarH10RunningDynamicsEstimatorTest` (`4/4`), `PolarOfflineCapabilityStoreTest` (`4/4`), `PolarRawTelemetryPrunerTest` (`4/4`), `PolarRawTelemetryReaderTest` (`3/3`), `WorkoutSamplesHrBackfillTest` (`1/1`).
  * UI, Service & Localization/Contract: `PolarH10RetrySyncBannerTest` (`3/3`), `TrackOnMapAftermathViewModelTest` (`9/9`), `TrackerServiceExportGatingTest` (`1/1`), `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`), `SourceTextReadingAuditContractTest` (`4/4`), `UiReflectionOnlyTestAuditContractTest` (`4/4`), `TranslationParityTest` (`10/10`).
* **Gate 4 Targeted Re-Verification**: **112/112 PASSED** (`0` new warnings).
* **Governance & Safety Linters**: `verify_requirement_governance.py --sync-indexes` and `--base-ref sprint/2026-11-19_01_Früh`, `check_static_analysis_baselines.py` (`detekt` 9509/9509, `lint` 238/238), `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, and `check_secrets_hygiene.py` all **PASSED** (`exit 0`).
