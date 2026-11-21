# Stage 1 Analysis: ATT-9352 - Headless Debug Heap Watchdog & Periodic Device Heap Hygiene

**Ticket**: [ATT-9352](https://issue-tracker/ATT-9352)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://issue-tracker/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Branch**: `feature/ATT-9352` (from `sprint/2026-11-20_03_Nacht` @ `d60bf458`)  
**Author**: AI Agent 1 (Lane C Flash)  
**Date**: 2026-11-20  

---

## 1. Problem Statement & Motivation
* **Current Behavior**: Long endurance workouts on `com.atrainingtracker.debug` (Pixel 10) lack passive heap-growth telemetry and automatic `.hprof` capture when retained Java heap approaches `Runtime.maxMemory()`, forcing reactive post-OOM debugging. Additionally, sprint ceremonies lack a standard ADB hygiene loop to inspect/triage/delete device debug heap logs.
* **Expected Behavior**: A headless, `BuildConfig.DEBUG`-only `DebugHeapWatchdog` hooked minimally into `TrackerService` writes 30 s CSV snapshots (`files/debug_heap/<fileBaseName>.heap.csv`, max 20 retained), captures at most 1 `.hprof` dump per workout (`files/debug_hprof/<fileBaseName>_<usedMb>MB.hprof`, max 2 retained) when `javaUsed / javaMax >= 0.70` across 2 consecutive checks with `>= 1 GB` free space, incurs zero overhead in release builds (`BuildConfig.DEBUG == false`) with zero UI/notifications/strings, excludes `debug_heap`/`debug_hprof` from backups, and updates `.agents/rules/aspice_governance.md` + `.agents/skills/sprint-planner/SKILL.md` for periodic Pixel 10 triage & cleanup.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Independent Peer Review & Verification (Live Codebase Audit on `d60bf458`)**:
  * **Monolith LoC Ceiling (`ArchitectureConformanceContractTest.kt:119`)**: `TrackerService.java` is `3,511` LoC against a ratcheted ceiling of `3,559` LoC (`+48` LoC headroom). Putting watchdog logic inside `TrackerService.java` would breach the ratchet; extracting `DebugHeapWatchdog` into its own class keeps `TrackerService.java` changes to minimal start/tick/stop hooks (~10–15 lines).
  * **Thread & Lifecycle Isolation (`TrackerService.java:875, 1245, 1429, 1641, 1841`)**: `mScheduler` runs the 1 Hz `tracker` loop and `mDbExecutor` serializes SQLite writes/finalization. Running `Debug.dumpHprofData()` or file pruning on UI, `mScheduler`, or `mDbExecutor` would stall 1 Hz sample writes/stop finalization. `DebugHeapWatchdog` MUST execute CSV I/O and `Debug.dumpHprofData()` on a dedicated background single-thread executor off the UI/DB threads, safely waiting if `mBaseFileName` is not yet populated during `RESUME_SERVICE_RECREATION` (`applyResumedState():1841`), and checking existing `<fileBaseName>_*.hprof` files so crash-restarted sessions also honor the 1-dump-per-workout cap.
  * **Active Sensor Count (`BANALService.java:740, 1026`)**: `BANALServiceComm` exposes `getActiveDevicesIncludingSpeedAndLocationDevices()` and `getBestSensorData()` (skipping unbacked `ProxySensor`s); null-guarding `mBanalService` yields accurate `activeSensorCount` without blocking tracking.
  * **Backup Exclusion Gap (`BackupManager.kt:168-178`, `app/src/main/res/xml/`)**: `.attbackup` (`BackupManager.createBackup`) only zips `databases/`, `shared_prefs/`, and `datastore/`, so `files/debug_*` is already excluded from `.attbackup`. However, `data_extraction_rules.xml` and `full_backup_content.xml` currently exclude only `auth_credentials_prefs.xml` (`BackupRulesXmlValidationTest.kt`)—both MUST exclude `domain="file"` `debug_heap` and `debug_hprof` to prevent Android Auto Backup / cloud / D2D transfer bloat.
  * **JVM Test Isolation (`docs/coding_standards.md` §5.5/§5.9)**: Plain-JVM tests pass relaxed `Context` mocks (`filesDir` empty/unmocked). `DebugHeapWatchdog` must guard `filesDir.path.isNotEmpty()` and inject `HprofDumper`, `MemorySnapshotProvider`, `DiskSpaceProvider`, and `Executor` for deterministic offline JVM tests.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope (`2 SP`)**: `DebugHeapWatchdog` (`BuildConfig.DEBUG`-gated), minimal `TrackerService.java` lifecycle hooks, `data_extraction_rules.xml` & `full_backup_content.xml` exclusions, JVM unit tests + `TrackerService` release-no-op verification, `.agents/rules/aspice_governance.md` & `.agents/skills/sprint-planner/SKILL.md` Pixel 10 device-hygiene workflow, and living docs (`REQ-STB-022`/`TST-STB-022` in `part2`).
* **Out-of-Scope**: Zero UI screens, notifications, banners, or `strings*.xml` edits; zero `.attbackup` schema changes (`BackupManager.kt` already skips `files/`); no autonomous Phase 2 deployment to physical Pixel 10 (`Rule 7.6`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-STB-022` / `TST-STB-022` in `docs/requirements/req_process_stability_part2.md` (`2,611` lines vs `part1` `3,980/4,000` cap) & `docs/tests/tst_process_stability_part2.md`; refines operational guidance in `REQ-TST-004` (`Rule 7.6`, `Rule 2.2`).
* **Historical Origin & Commit Trace**: `REQ-TST-004` (`ATT-6506`) established physical-phone protection (`66020DLCR002FL` untouched in Phase 2); `Rule 2.7` (`ATT-8164`/`ATT-8223`) restricts `.agents/` edits to dedicated PO-approved tickets (`ATT-9352`); `BackupRulesXmlValidationTest` (`ATT-4023`) guards `auth_credentials_prefs.xml` backup exclusions.
* **Root Reason for Existing Formulation**: Separating `emulator-5554` Phase 2 execution from `66020DLCR002FL` prevents agents from disrupting PO field data; `ATT-9352` explicitly authorizes Ceremony 1 / connected-Pixel-10 read-and-prune hygiene for `files/debug_heap/` & `files/debug_hprof/` only.
* **Preservation of Core Invariants**: Preserves `TrackerService.java <= 3,559` LoC, `Rule 7.6` emulator-only Phase 2 build/test execution, `auth_credentials_prefs.xml` backup exclusions, `Rule 5.1` stop-on-bug-creation, and release build zero-overhead no-op.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-STB-022` (`REQ-STB-022.1`–`.4`, `req_process_stability_part2.md`); operational alignment note referencing `REQ-TST-004` / `Rule 2.2` / `Rule 7.6`. `SYS.2`/`SYS.3`: None (debug-only internal diagnostics & agent hygiene, no external sensor/cloud interface). Localization: N/A (`0` UI strings).
* **Affected Architecture Elements**: New `DebugHeapWatchdog.kt` (`tracker/`), minimal hooks in `TrackerService.java`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/full_backup_content.xml`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`.
* **Verification Impact & Test Surface**: New `DebugHeapWatchdogTest.kt` (`TST-STB-022`), `TrackerService` debug/release-noop test, `BackupRulesXmlValidationTest.kt`, `ArchitectureConformanceContractTest.kt`, `tools/verify_skills.py`, `tools/test_agent_emulator.py`, `tools/test_jira_accounts.py`, and Stage 4/6 live tracking verification on `emulator-5554`.
* **Risk Assessment & Invariants**: **LOW** (`2 SP`) — `try/catch (Throwable)` isolation + dedicated background executor protects 1 Hz workout recording; `BuildConfig.DEBUG` guard guarantees release no-op; `TrackerService.java` stays well under `3,559` LoC; `66020DLCR002FL` untouched during Phase 2.
