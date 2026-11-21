# Stage 5: Walkthrough & Verification - ATT-9352 (Bounce 1)

**Ticket**: [ATT-9352](https://issue-tracker/ATT-9352) (`2 SP`) | **Epic**: [ATT-2466](https://issue-tracker/ATT-2466) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Branch**: `feature/ATT-9352` (Gate 4 Bounce 1 SHA: `c4035319`, `ATT-9404` PASS; Baseline Gate 4 SHA: `7feb5c06`, `ATT-9402` PASS)  
**Requirement / Test Mapping**: `REQ-STB-022` (`REQ-STB-022.1`–`REQ-STB-022.4` `[Verified]`) / `TST-STB-022` (`TST-STB-022.1`–`TST-STB-022.4` `[Verified]`)  
**Author**: AI Agent 1 (Lane C Flash) | **Date**: 2026-11-21  

---

## 1. Executive Summary & Rule 7.5 Doc-Only Fast Path
* **Implemented Components (`7feb5c06` + Bounce 1 `c4035319`)**:
  1. `DebugHeapWatchdog.kt`: Headless `BuildConfig.DEBUG`-only heap watchdog (`isDebug == false` O(1) no-op with zero threads/files/UI/notifications/strings). Defers `countActiveDevicesSafely(banalService)` strictly behind the 30 s `samplingIntervalMs` gate via inline `onTickWithCountProvider` (`0` per-second `LinkedList` allocations on intermediate 1-Hz ticks). Logs 30 s snapshots to `files/debug_heap/<fileBaseName>.heap.csv` (latest 20 retained) and captures at most 1 `Debug.dumpHprofData()` per workout under `files/debug_hprof/<fileBaseName>_<usedMb>MB.hprof` (latest 2 retained) when `javaUsed / javaMax >= 0.70` across 2 consecutive checks with `>= 1 GB` free space, restart existing-file guard, lazy `"DebugHeapWatchdog"` daemon executor, and `try/catch (Throwable)`.
  2. `TrackerService.java` (`3,537 <= 3,559` LoC ceiling): Minimal `BuildConfig.DEBUG`-guarded `startSession`, `onTick`, `stopSession`, and `shutdown` hooks.
  3. Backup Exclusions: `data_extraction_rules.xml` (`cloud-backup` & `device-transfer`) and `full_backup_content.xml` exclude `domain="file"` `debug_heap` & `debug_hprof` (preserving `auth_credentials_prefs.xml`).
  4. Governance & Architecture Docs: `.agents/rules/aspice_governance.md` (`v1.0.6`, `Rule 7.6`) & `.agents/skills/sprint-planner/SKILL.md` add periodic Pixel 10 (`66020DLCR002FL`) `files/debug_hprof/` + `files/debug_heap/` inspection, `ATT-235` Bug filing (`Rule 5.1`), and `run-as com.atrainingtracker.debug rm` cleanup; `docs/architecture.md` (`v1.0.16`) & `docs/architecture/detailed_design/tracker_service.md` (`v1.0.5`) updated.
* **Rule 7.5 Doc-Only Fast Path**: Zero code/test/tool/rule modifications since Gate 4 Bounce 1 (`ATT-9404` @ `c4035319`); Gate 4 Bounce 1 (`21/21`) and unchanged `7feb5c06` Gate 4 evidence are reused without redundant Gradle runs.

---

## 2. Verification Matrix (Reused Gate 4 Bounce 1 @ `c4035319` + Gate 4 @ `7feb5c06`)

| Scope / Spec | Exact File / Class / Suite Citations | Result | Status |
| :--- | :--- | :--- | :--- |
| `TST-STB-022.1`–`.3` | `DebugHeapWatchdogTest` (`8/8` @ `c4035319`, incl. `onTickBanalService_queriesActiveDevicesOnlyOncePer30Seconds` `exactly = 3` over 61 1-Hz ticks) | **PASS (`8/8`)** | `Verified` |
| `TST-STB-022.1`, `.2` | `TrackerServiceHeapWatchdogTest` (`2/2` @ `c4035319`) + `TrackerServiceStateMachineCharacterisationTest` (`8/8` @ `7feb5c06`) | **PASS (`10/10`)** | `Verified` |
| `TST-STB-022.4` | `BackupRulesXmlValidationTest` (`4/4` @ `7feb5c06`), `tools/verify_skills.py` (`11/11`), `tools/test_agent_emulator.py` (`21/21`) | **PASS (`4/4`, `11/11`, `21/21`)** | `Verified` |
| Architecture & Contracts | `ArchitectureConformanceContractTest` (`11/11` @ `c4035319`, `TrackerService.java` `3,537 <= 3,559` LoC), `DetailedDesignParityContractTest` (`3/3` @ `7feb5c06`), `SourceTextReadingAuditContractTest` (`6/6` @ `7feb5c06`) | **PASS (`20/20`)** | `Verified` |
| Safety Linters & Governance | `4/4` safety linters (`@ 7feb5c06`) + `verify_requirement_governance.py --sync-indexes --check-verified-evidence` | **PASS (`4/4` + `exit 0`)** | `Verified` |

---

## 3. Traceability & Invariants
* **Bidirectional Traceability**: `REQ-STB-022` (`REQ-STB-022.1`–`REQ-STB-022.4` `[Verified]`) $\leftrightarrow$ `TST-STB-022` (`TST-STB-022.1`–`TST-STB-022.4` `[Verified]`).
* **Invariants Preserved**: Release (`BuildConfig.DEBUG == false`) zero-overhead no-op; zero UI/notifications/strings (`Localization Parity: N/A - no UI strings`); `TrackerService.java` `3,537 <= 3,559` LoC; `66020DLCR002FL` untouched (`REQ-TST-004`); `feature/ATT-9352` preserved intact for Stage 6 Cold Review.
