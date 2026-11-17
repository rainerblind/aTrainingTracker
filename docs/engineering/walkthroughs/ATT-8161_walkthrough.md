# Stage 5: Walkthrough & Verification - ATT-8161: Retrospective ADRs (ADR-002..ADR-007)

**Ticket**: [ATT-8161](https://rainerblind.atlassian.net/browse/ATT-8161) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `2026-11-17_01_Früh` | **Branch**: `feature/ATT-8161` (rebased onto `e97666255189e1d5d848d63cf01369b4ced45c87`) | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1..3`, `[Verified]`) | **Refines**: `REQ-ARC-005`, `REQ-PRO-043`, `REQ-PRO-049`
**Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1..3`, `[Verified]`)
**UI Consistency**: N/A - no UI, `androidTest/`, `strings*.xml`, `app/src/main/`, `ADR-001`, or `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`) modified.

---

## 1. Executive Summary & Delivered Artifacts
* **Six Retrospective ADRs (`REQ-PRO-116.1`, `REQ-PRO-116.2`)** following `ADR-001` structure (`Context & Problem Statement`, `Decision Drivers & Evaluated Alternatives` with `>= 2` rejected/selected alternatives, `Decision Outcome`, `Architectural Consequences & Trade-offs`, real `app/src/main/` anchors, honest `Status: Retrospective (Pending PO Sign-Off)` & `Deciders: Architecture Board, Product Owner (Pending Sign-Off)`):
  * `docs/architecture/adr_002_foreground_tracking_service.md`: `TrackerService` (`location|health` FGS, `START_STICKY`, `PARTIAL_WAKE_LOCK`, `mScheduler`/`mDbExecutor`/`mRrFlushExecutor`) vs `WorkManager` / unbound `Service`.
  * `docs/architecture/adr_003_banal_sensor_bus.md`: `BANALService` Local Binder (`BANALServiceComm`, `MySensorManager`, `ProxySensor`, `FilterManager`) + `BANALServiceRepository` `StateFlow` bridge vs global EventBus / out-of-process AIDL.
  * `docs/architecture/adr_004_sqlite_managers_vs_room.md`: `SQLiteOpenHelper` WAL managers (`WorkoutSamplesDatabaseManager` dynamic per-workout tables, `WorkoutSummariesDatabaseManager`, `LapsDatabaseManager`, `DevicesDatabaseManager`) behind Kotlin repositories (`WorkoutRepository`, `DeviceDataRepository`, `KnownLocationsRepository`) vs Room ORM / direct UI DB coupling.
  * `docs/architecture/adr_005_hybrid_preferences_datastore.md`: Hybrid `PreferenceManager.getDefaultSharedPreferences` (`TrainingApplication`, backup/sync) + Jetpack `DataStore` (`SettingsDataStore` eager `StateFlow<SettingsSnapshot>`, `TuningPreferencesDataStore`, `MyPreferenceManager`, `AttFusedPreferences`, `SettingsDataStoreJavaHelper`) vs pure `SharedPreferences` / big-bang cutover.
  * `docs/architecture/adr_006_incremental_compose_navigation.md`: Single-Activity `MainActivityWithNavigation` + `ATrainingTrackerApp` (`NavHost`, MVI `StateFlow` ViewModels) with incremental legacy migration (`ConfigViewsActivityClassic`, `ConfigViewsFragment`, `TrackOnMapHelper`) vs multi-Fragment XML / big-bang rewrite.
  * `docs/architecture/adr_007_ble_ant_sensor_abstraction.md`: Unified `MyDevice` -> `MyRemoteDevice` -> `MyANTDevice` (`ANT-Android-SDKs` `0e8cea71`) & `MyBTLEDevice` (`mGattQueue`) + `PolarOnlineStreamingBridge`, arbitrated by `DeviceManager` / `ProxySensor` (`SensorType`) vs protocol-specific pipelines / big-bang driver rewrite.
* **`docs/architecture.md` (§5.5 ADR Registry)**: Added ADR Registry indexing `ADR-001`..`ADR-007` with explicit `Pending PO Sign-Off` visibility for `ADR-002`..`ADR-007`.
* **`app/src/test/java/com/atrainingtracker/trainingtracker/build/AdrCompletenessContractTest.kt` (`REQ-PRO-116.3`)**: Enforces structure, `>= 2` evaluated alternatives, honest PO sign-off metadata, zero phantom tokens (`TrackPointsDatabaseManager`, `ANTDevice`, `BTLEDevice`), `docs/architecture.md` registry links, and `Class.forName` JVM-reflection class checks with zero `app/src/main/` text reads (`REQ-PRO-049`).

---

## 2. Clean-Room Verification Results (Post-Rebase Code SHA `af307847c1988fab2fa3f0464246f4a578b16550`)
* **Tier 1 Targeted Gradle Contract Suite** (`parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.AdrCompletenessContractTest" --tests "*.JavaCoreMigrationStrategyContractTest" --tests "*.ArchitectureConformanceContractTest" --tests "*.AspiceSweAuditContractTest" --tests "*.InterfaceSignatureDriftContractTest"`):
  * `AdrCompletenessContractTest`: **2/2 PASS** (`tests=2, failures=0, errors=0, skipped=0`)
  * `JavaCoreMigrationStrategyContractTest`: **4/4 PASS** (`tests=4, failures=0, errors=0, skipped=0`)
  * `ArchitectureConformanceContractTest`: **9/9 PASS** (`tests=9, failures=0, errors=0, skipped=0`)
  * `AspiceSweAuditContractTest`: **11/11 PASS** (`tests=11, failures=0, errors=0, skipped=0`)
  * `InterfaceSignatureDriftContractTest`: **3/3 PASS** (`tests=3, failures=0, errors=0, skipped=0`)
  * **Tier 1 Scope Note**: `androidTest` and `TranslationParityTest` skipped per Tier-1 policy (`androidTest/` and `strings*.xml` untouched). `SourceTextReadingAuditContractTest` verified at Gate 4 (`3/4 PASS`, `0` violations from `ATT-8161` files; `1` pre-existing `ATT-8631` failure on `AdvancedTuningZoneDisplayTest.kt` and `SensorTypeUnitsContractTest.kt`).
* **Living Documentation & Governance**:
  * `REQ-PRO-116` and `TST-PRO-116` promoted to `[Verified]`; `python3 tools/verify_requirement_governance.py --sync-indexes`, `python3 tools/verify_requirement_governance.py`, and `--text-file docs/engineering/walkthroughs/ATT-8161_walkthrough.md` all exited **0** (`Total=1215, Verified=971` REQs; `Total=1222, Verified=1000` TSTs).
