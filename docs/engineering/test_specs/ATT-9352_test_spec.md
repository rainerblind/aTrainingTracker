# Stage 2: Requirement & Test Specification - ATT-9352: Headless Debug Heap Watchdog & Device Heap Hygiene

**Ticket**: [ATT-9352](https://issue-tracker/ATT-9352)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://issue-tracker/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-STB-022` (`REQ-STB-022.1`–`REQ-STB-022.4`)  
**Test Spec ID**: `TST-STB-022` (`TST-STB-022.1`–`TST-STB-022.4`)  
**Branch**: `feature/ATT-9352`  
**Author**: AI Agent 1 (Lane C Flash)  
**Date**: 2026-11-20  

---

## 1. Requirement Specification (`REQ-STB-022`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit / Integration / Review

### 1.2 Functional & Architectural Requirements
1. **Release-Build Zero-Overhead & Headless Guard (`REQ-STB-022.1`)**: When `BuildConfig.DEBUG == false`, `DebugHeapWatchdog` MUST NOT start, spawn threads, or write files (zero runtime overhead). Debug builds MUST remain headless: zero UI, notifications, banners, or `strings*.xml` entries. `Localization Parity: N/A - no UI strings`.
2. **Periodic Heap CSV Logging & Retention (`REQ-STB-022.2`)**: During active `BuildConfig.DEBUG` tracking, `DebugHeapWatchdog` SHALL sample every `30 s` on a dedicated off-UI/off-`mDbExecutor` background executor and append `timestamp,elapsedSec,javaUsedMb,javaMaxMb,javaPct,nativeAllocatedMb,activeSensorCount` to `files/debug_heap/<fileBaseName>.heap.csv`, auto-pruning oldest logs to keep at most `20` sessions. `TrackerService.java` hooks MUST preserve `TrackerService.java <= 3,559` LoC.
3. **One-Shot `.hprof` Capture & Safety Guards (`REQ-STB-022.3`)**: When `javaUsed / javaMax >= 0.70` across `2` consecutive `30 s` checks and internal storage usable space is `>= 1 GB` (`1_073_741_824` bytes), `DebugHeapWatchdog` SHALL capture at most `1` `.hprof` dump per workout session (including across `RESUME_SERVICE_RECREATION` restarts for the same `<fileBaseName>`) via `Debug.dumpHprofData()` to `files/debug_hprof/<fileBaseName>_<usedMb>MB.hprof`, auto-pruning beyond the `2` newest `.hprof` files, and wrapping all I/O, dumping, and pruning in `try/catch (Throwable)`.
4. **Backup Exclusions & Pixel 10 Hygiene Workflow (`REQ-STB-022.4`)**: `app/src/main/res/xml/data_extraction_rules.xml` (`cloud-backup` & `device-transfer`) and `full_backup_content.xml` SHALL exclude `domain="file"` `debug_heap` and `debug_hprof` (preserving `auth_credentials_prefs.xml`; `.attbackup` in `BackupManager.kt` already omits `files/`). `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md` SHALL specify periodic Pixel 10 (`com.atrainingtracker.debug`) inspection of `files/debug_hprof/` & `files/debug_heap/`, filing an `ATT-235` Bug on anomaly (stopping per `Rule 5.1`), and deleting processed files (`run-as com.atrainingtracker.debug rm ...`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (`TST-STB-022.1`)**: *Given* `BuildConfig.DEBUG == false`, *When* tracking executes, *Then* zero executors/files/logs are created and zero UI/notifications/strings exist.
* **AC 2 (`TST-STB-022.2`)**: *Given* `BuildConfig.DEBUG == true` active tracking, *When* running 5+ minutes, *Then* `files/debug_heap/<fileBaseName>.heap.csv` logs 30 s snapshots off-UI/off-`mDbExecutor`, retains `<= 20` CSVs, and `TrackerService.java <= 3,559` LoC.
* **AC 3 (`TST-STB-022.3`)**: *Given* `javaUsed / javaMax >= 0.70` across 2 consecutive checks and `>= 1 GB` free space, *When* triggered, *Then* exactly 1 `.hprof` writes to `files/debug_hprof/` per session, `<= 2` dumps are kept, low-disk/transient spikes skip, and `Throwable` never interrupts tracking.
* **AC 4 (`TST-STB-022.4`)**: *Given* updated backup XMLs and `.agents/` rules/skills, *When* `BackupRulesXmlValidationTest`, `verify_skills.py`, `test_*.py`, and `emulator-5554` smoke run, *Then* `debug_heap`/`debug_hprof` are excluded, Pixel 10 triage/delete workflow passes validation, and `emulator-5554` writes `<fileBaseName>.heap.csv`.

---

## 2. Test Specification (`TST-STB-022`)
* **`TST-STB-022.1` (`DebugHeapWatchdogTest.kt`, `TrackerServiceHeapWatchdogTest.kt`, `ArchitectureConformanceContractTest.kt`)**: Verify `isDebug = false` no-op, zero file/thread creation, and `TrackerService.java <= 3,559` LoC.
* **`TST-STB-022.2` (`DebugHeapWatchdogTest.kt`, `TrackerServiceHeapWatchdogTest.kt`)**: Verify 30 s CSV schema/append, deferred `mBaseFileName` on resume, and latest-20 `.heap.csv` pruning.
* **`TST-STB-022.3` (`DebugHeapWatchdogTest.kt`)**: Verify 2-sample debounce, 1-dump-per-session & restart cap, `>= 1 GB` disk check, `Throwable` shielding, and latest-2 `.hprof` pruning.
* **`TST-STB-022.4` (`BackupRulesXmlValidationTest.kt`, `tools/verify_skills.py`, `tools/test_*.py`, `emulator-5554`)**: Verify XML backup exclusions, skill/rule checks, and live `emulator-5554` CSV creation (`REQ-TST-004`).

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-STB-022.1` | `BuildConfig.DEBUG`-only no-op & headless invariant | `TST-STB-022.1` | Unit & Contract (`Rule 7.5`) |
| `REQ-STB-022.2` | 30 s heap CSV logging, 20-file prune, `TrackerService` LoC cap | `TST-STB-022.2` | Unit & Contract |
| `REQ-STB-022.3` | One-shot `>= 70%` 2-sample `.hprof` dump, disk/error/2-file guards | `TST-STB-022.3` | Unit |
| `REQ-STB-022.4` | Backup XML exclusions, Pixel 10 hygiene rules/skills & smoke | `TST-STB-022.4` | Unit, Skill Check & Emulator |
