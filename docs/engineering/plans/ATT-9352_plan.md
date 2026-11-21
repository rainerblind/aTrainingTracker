# Stage 3: Implementation Plan - ATT-9352: Headless Debug Heap Watchdog & Device Heap Hygiene

**Ticket**: [ATT-9352](https://issue-tracker/ATT-9352)  
**Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://issue-tracker/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-STB-022` (`REQ-STB-022.1`–`REQ-STB-022.4`)  
**Test Mapping**: `TST-STB-022` (`TST-STB-022.1`–`TST-STB-022.4`)  
**Branch**: `feature/ATT-9352`  
**Author**: AI Agent 1 (Lane C Flash)  
**Date**: 2026-11-21  

---

## 1. Architecture & Threading Design
* **`DebugHeapWatchdog.kt` (`com.atrainingtracker.trainingtracker.tracker`)**: Self-contained Kotlin helper class guarded by `isDebug: Boolean = BuildConfig.DEBUG`. When `false`, all methods return immediately with zero thread/executor/file allocation. When `true`, `onTick(baseFileName, activeSensorCount)` evaluates elapsed time (`>= 30_000 ms` since last sample) and dispatches CSV/HPROF I/O onto a lazily created single-thread daemon `ExecutorService` (`"DebugHeapWatchdog"`), never touching UI, `mScheduler`, or `mDbExecutor`.
* **Minimal `TrackerService.java` Hooks**: Holds a `DebugHeapWatchdog` collaborator (~10–15 LoC added total: `3,511 -> ~3,525 <= 3,559` LoC ceiling): `startSession(mBaseFileName)` on `START_NORMAL`, `onTick(mBaseFileName, count)` in `tracker` runnable (also handles deferred `mBaseFileName` after `RESUME_SERVICE_RECREATION` `applyResumedState()`), and `stopSession()`/`shutdown()` in `handleActionStop()`/`onDestroy()`.
* **Testability & JVM Safety (`coding_standards.md` §5.5/§5.9)**: Injects `MemorySnapshotProvider` (`Runtime` + `Debug.getNativeHeapAllocatedSize()`), `DiskSpaceProvider` (`filesDir.usableSpace`), `HprofDumper` (`Debug.dumpHprofData`), `Clock` (`System.currentTimeMillis`), and `Executor`; guards `filesDir.path.isNotEmpty()`.
* **UI Consistency**: **N/A** — strictly headless; zero UI, notifications, banners, or `strings*.xml` edits.

---

## 2. System Invariants & Preserved Behavior
1. **Release Zero-Overhead (`REQ-STB-022.1`)**: `BuildConfig.DEBUG == false` performs zero work, zero threads, zero I/O.
2. **Tracking Stability & LoC Cap (`REQ-STB-022.2/.3`)**: All watchdog operations wrap in `try/catch (Throwable)`; `TrackerService.java <= 3,559` LoC; 1-Hz sampling and `mDbExecutor` finalization remain unblocked.
3. **Backup & Device Boundaries (`REQ-STB-022.4`, `REQ-TST-004`)**: Preserves `auth_credentials_prefs.xml` exclusions in `data_extraction_rules.xml` & `full_backup_content.xml`, `.attbackup` (`BackupManager.kt`), `Rule 5.1` stop-on-bug-creation, and `emulator-5554`-only Phase 2 testing.

---

## 3. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-STB-022.1`–`.3` — `DebugHeapWatchdog.kt`)**:
   - Create `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/DebugHeapWatchdog.kt` with `SAMPLING_INTERVAL_MS = 30_000L`, `HPROF_HEAP_RATIO = 0.70`, `CONSECUTIVE_HIGH_WATER_REQUIRED = 2`, `MIN_FREE_SPACE_BYTES = 1_073_741_824L`, `MAX_HEAP_CSV_FILES = 20`, `MAX_HPROF_FILES = 2`.
   - Write CSV header `timestamp,elapsedSec,javaUsedMb,javaMaxMb,javaPct,nativeAllocatedMb,activeSensorCount` and append rows to `files/debug_heap/<fileBaseName>.heap.csv`; prune oldest `.heap.csv` files beyond 20.
   - Track consecutive `javaUsed / javaMax >= 0.70` samples; on `>= 2` consecutive samples, verify `!hasDumpedThisSession`, no existing `<fileBaseName>_*.hprof` in `files/debug_hprof/` (crash-restart idempotency), and `usableSpace >= 1 GB`, then call `Debug.dumpHprofData(files/debug_hprof/<fileBaseName>_<usedMb>MB.hprof)`, mark dumped, and prune oldest `.hprof` files beyond 2.
2. **Step 2 (`REQ-STB-022.1/.2` — `TrackerService.java`)**:
   - Wire minimal `DebugHeapWatchdog` hooks (`new DebugHeapWatchdog(getApplicationContext())`, `startSession`, `onTick` using null-safe `banalService.getActiveDevicesIncludingSpeedAndLocationDevices().size()`, `stopSession`, `shutdown`), keeping `TrackerService.java <= 3,559` LoC.
3. **Step 3 (`REQ-STB-022.4` — Backup Exclusions)**:
   - Update `app/src/main/res/xml/data_extraction_rules.xml` (`cloud-backup` & `device-transfer`) and `app/src/main/res/xml/full_backup_content.xml` to exclude `domain="file"` `debug_heap` and `debug_hprof` alongside `auth_credentials_prefs.xml`.
4. **Step 4 (`REQ-STB-022.4` — Governance Rules & Skills)**:
   - Update `.agents/rules/aspice_governance.md` (`Rule 2.2` / `Rule 7.6` note) and `.agents/skills/sprint-planner/SKILL.md` (Ceremony 1 / connected Pixel 10 hygiene) with the inspect `files/debug_hprof/` & `files/debug_heap/` -> pull/analyze anomaly -> file `ATT-235` Bug (stop per `Rule 5.1`) -> `run-as com.atrainingtracker.debug rm` cleanup workflow (including optional healthy `.heap.csv` pruning).
5. **Step 5 (`Rule 8.8` / `REQ-PRO-114` — Architecture & Detailed Design)**:
   - Update `docs/architecture.md` and `docs/architecture/detailed_design/tracker_service.md` (document `DebugHeapWatchdog` collaborator/executor, updated `TrackerService.java` LoC, and `DebugHeapWatchdogTest`).
6. **Step 6 (`TST-STB-022.1`–`.4` — Unit, Contract, Python & Emulator Verification)**:
   - Create `DebugHeapWatchdogTest.kt` and `TrackerServiceHeapWatchdogTest.kt` (`TST-STB-022.1`–`.4` tags), extend `BackupRulesXmlValidationTest.kt`, promote `REQ-STB-022`/`TST-STB-022` to `[Implemented]` (and `[Verified]` in Stage 5), run targeted Gradle tests + `ArchitectureConformanceContractTest` + `DetailedDesignParityContractTest` + `tools/verify_skills.py` + `tools/test_agent_emulator.py` + `tools/test_jira_accounts.py` + `verify_requirement_governance.py`, and smoke-test live tracking CSV creation on `emulator-5554`.

---

## 4. Verification & Rollback Plan
* **Verification**: Targeted `./gradlew :app:testDebugUnitTest --rerun --tests ...`, Python skill/tool tests, `verify_requirement_governance.py`, and `emulator-5554` smoke run (`66020DLCR002FL` untouched).
* **Rollback**: Isolated on `feature/ATT-9352`; `git revert` or deleting `feature/ATT-9352` cleanly restores `sprint/2026-11-20_03_Nacht`.
