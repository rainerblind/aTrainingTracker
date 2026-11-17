# Stage 3: Implementation Plan - ATT-8161: Retrospective ADRs (ADR-002..ADR-007)

**Ticket**: [ATT-8161](https://rainerblind.atlassian.net/browse/ATT-8161) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `sprint/2026-11-17_01_Früh` | **Branch**: `feature/ATT-8161` | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1..3`) | **Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1..3`)

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `Minor (SWE.2)` (`SWE.2 BP3/BP5`): only `ADR-001` exists (`docs/architecture/adr_001_java_core_services_migration.md`). The 6 foundational architectural decisions lack documented alternatives, selection criteria, and consequences to guard Chesterton's Fence invariants.

## 2. Traceability & Requirements Mapping
* `REQ-PRO-116.1` (`ADR-002..007` matching `ADR-001` template with `>= 2` evaluated alternatives & real code anchors) -> `TST-PRO-116.1` (`AdrCompletenessContractTest.kt`).
* `REQ-PRO-116.2` (Honest `Status: Retrospective (Pending PO Sign-Off)`, zero phantom classes, ADR index in `docs/architecture.md`) -> `TST-PRO-116.1`, `TST-PRO-116.2` (`AdrCompletenessContractTest.kt`, `AspiceSweAuditContractTest.kt`).
* `REQ-PRO-116.3` (JVM-reflection class verification via `Class.forName` with zero `src/main/` text reads) -> `TST-PRO-116.2`, `TST-PRO-116.3` (`AdrCompletenessContractTest.kt`, `SourceTextReadingAuditContractTest.kt`).

## 3. System Invariants & Preserved Behavior
1. **Zero Production Code Edits & Submodule Lock**: Zero edits to `app/src/main/`, `app/src/androidTest/`, `ADR-001` (`REQ-ARC-005`), or `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`).
2. **Honest PO Sign-Off (`ATT-8138` Integrity)**: Every new ADR records `Status: Retrospective (Pending PO Sign-Off)` and `Deciders: Architecture Board, Product Owner (Pending Sign-Off)` (zero fabricated PO signature).
3. **Source-Text Audit Compliance (`REQ-PRO-049`)**: `AdrCompletenessContractTest.kt` reads only `docs/architecture/` markdown files and validates cited Java/Kotlin classes via `Class.forName` JVM reflection — ZERO `.readText()` / `Files.readString()` scans on `app/src/main/`.
4. **SWE.2 & ADR-001 Contract Compatibility**: Preserve all `JavaCoreMigrationStrategyContractTest`, `AspiceSweAuditContractTest` (Section 2/5/6 anchors), and `ArchitectureConformanceContractTest` assertions.
5. **UI Consistency**: N/A — zero UI, Compose, or `strings*.xml` modifications.

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-116.1/2`) — `docs/architecture/adr_002_foreground_tracking_service.md`**: Document `TrackerService` (`foregroundServiceType="location|health"`, `START_STICKY`, `PARTIAL_WAKE_LOCK` `aTrainingTracker:TrackingLock`, `mScheduler`/`mDbExecutor`/`mRrFlushExecutor`) vs `WorkManager` / `JobScheduler` alternatives.
2. **Step 2 (`REQ-PRO-116.1/2`) — `docs/architecture/adr_003_banal_sensor_bus.md`**: Document `BANALService` Local Binder (`BANALServiceComm`), `BroadcastReceiver`s, `MySensorManager`/`ProxySensor`/`FilterManager` + `BANALServiceRepository` `StateFlow` bridge vs static EventBus or cross-process AIDL.
3. **Step 3 (`REQ-PRO-116.1/2`) — `docs/architecture/adr_004_sqlite_managers_vs_room.md`**: Document `SQLiteOpenHelper` managers (`WorkoutSamplesDatabaseManager` dynamic per-workout tables, `WorkoutSummariesDatabaseManager`, `LapsDatabaseManager`, `DevicesDatabaseManager`, WAL + Kotlin repositories `WorkoutRepository`, `DeviceDataRepository`, `KnownLocationsRepository`) vs full Room ORM or monolithic single-table schema (zero phantom `TrackPointsDatabaseManager`).
4. **Step 4 (`REQ-PRO-116.1/2`) — `docs/architecture/adr_005_hybrid_preferences_datastore.md`**: Document hybrid `PreferenceManager.getDefaultSharedPreferences` (`TrainingApplication`, backup/sync) + `androidx.datastore:datastore-preferences` (`SettingsDataStore`, `TuningPreferencesDataStore`, `MyPreferenceManager`, `AttFusedPreferences`, `SettingsDataStoreJavaHelper`) vs pure `SharedPreferences` or big-bang DataStore cutover.
5. **Step 5 (`REQ-PRO-116.1/2`) — `docs/architecture/adr_006_incremental_compose_navigation.md`**: Document `MainActivityWithNavigation` + `ATrainingTrackerApp` (`navigation-compose` `NavHost`, `AppNavigationDrawer`) + MVI `StateFlow` ViewModels coexisting with legacy XML/fragments (`ConfigViewsActivityClassic`, `ConfigViewsFragment`, `TrackOnMapHelper`) vs multi-Fragment XML or big-bang Compose rewrite.
6. **Step 6 (`REQ-PRO-116.1/2`) — `docs/architecture/adr_007_ble_ant_sensor_abstraction.md`**: Document `MyDevice` -> `MyRemoteDevice` -> `MyANTDevice` (`ANT-Android-SDKs` `0e8cea71` / `AntPluginPcc`) & `MyBTLEDevice` (`BluetoothGatt` + `mGattQueue`, plus `PolarOnlineStreamingBridge` via `polar-ble-sdk`) aggregated by `DeviceManager` into `MySensor`/`ProxySensor` (`SensorType`) vs protocol-specific branches (zero phantom `ANTDevice`/`BTLEDevice`).
7. **Step 7 (`REQ-PRO-116.2`) — `docs/architecture.md`**: Add an ADR Registry section/table indexing `ADR-001`..`ADR-007` (with relative links and `Pending PO Sign-Off` status for `ADR-002..007`) while preserving §2/§5.3 and all `AspiceSweAuditContractTest` / `ArchitectureConformanceContractTest` anchors.
8. **Step 8 (`REQ-PRO-116.3` -> `TST-PRO-116.1..3`) — `AdrCompletenessContractTest.kt`**: Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/AdrCompletenessContractTest.kt` (`// Linked test specs: TST-PRO-116`) verifying all 6 ADR files (`Context & Problem Statement`, `Decision Drivers & Evaluated Alternatives`, `>= 2` alternatives with `Rejected`/`Selected`, `Decision Outcome`, `Architectural Consequences & Trade-offs`, honest `Pending PO Sign-Off`, zero phantom tokens), `docs/architecture.md` index links, and compiled production classes via `Class.forName` (zero `src/main/` file reads).
9. **Step 9 (Verification)**: Ensure worktree submodule/symlinks (`local.properties`, `secrets.properties`, `app/google-services.json`) are intact, run:
   `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.AdrCompletenessContractTest" --tests "*.JavaCoreMigrationStrategyContractTest" --tests "*.ArchitectureConformanceContractTest" --tests "*.AspiceSweAuditContractTest"`
   plus `--tests "*.SourceTextReadingAuditContractTest"` (verifying `0` `ATT-8161` violations) and `python3 tools/verify_requirement_governance.py`.

## 5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-ARC-005` (`ADR-001`), `REQ-PRO-043` (`docs/architecture.md`), `REQ-PRO-049` (`SourceTextReadingAuditContractTest`), refined by `REQ-PRO-116`.
* **Historical Origin & Commit Trace**: `ATT-7566`/`ATT-8115` (`REQ-ARC-005`), `ATT-5431`/`ATT-6727` (`REQ-PRO-043`), `ATT-6186` (`REQ-PRO-049`), extended in `ATT-8161`.
* **Root Reason for Existing Formulation**: `ADR-001` focused strictly on Java-to-Kotlin migration scope; the 6 core platform/persistence/UI/HAL decisions remained implicit.
* **Preservation of Core Invariants**: Keeps `ADR-001` and all existing contract suites green with zero `app/src/main/` changes.

## 6. Rollback Plan
* Isolated on `feature/ATT-8161`; `git checkout --` / `git reset` restores prior state cleanly.
