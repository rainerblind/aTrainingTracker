# Stage 2: Requirement & Test Specification - ATT-8161: Retrospective ADRs (ADR-002..007)

**Ticket**: [ATT-8161](https://rainerblind.atlassian.net/browse/ATT-8161) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `2026-11-17_01_Früh` | **SP**: 2 SP | **Status**: Planned | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1..3`) | **Refines**: `REQ-ARC-005`, `REQ-PRO-043`, `REQ-PRO-049`
**Test Spec Mapping**: `TST-PRO-116` (`TST-PRO-116.1..3`)

---

## 1. Requirement Specification (`REQ-PRO-116`)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-116.1 (`Six Retrospective ADRs — ADR-002..ADR-007`)**: Author `adr_002_foreground_tracking_service.md` (`TrackerService` FGS + `PARTIAL_WAKE_LOCK` + `mScheduler`/`mDbExecutor` vs `WorkManager`/`JobScheduler`), `adr_003_banal_sensor_bus.md` (`BANALService` Local Binder + `BANALServiceRepository` `StateFlow` vs EventBus/AIDL), `adr_004_sqlite_managers_vs_room.md` (SQLite managers with dynamic `WorkoutSamplesDatabaseManager` tables + repositories vs Room ORM), `adr_005_hybrid_preferences_datastore.md` (hybrid `SharedPreferences` + `DataStore` + `SettingsDataStoreJavaHelper` vs pure SP or big-bang rewrite), `adr_006_incremental_compose_navigation.md` (`MainActivityWithNavigation` `NavHost` + incremental MVI vs multi-Fragment XML or big-bang Compose rewrite), and `adr_007_ble_ant_sensor_abstraction.md` (`MyDevice` -> `MyRemoteDevice` -> `MyANTDevice`/`MyBTLEDevice`/`PolarOnlineStreamingBridge` + `DeviceManager`/`ProxySensor` vs protocol-specific branches), each following `ADR-001` (`Options` with `>= 2` Evaluated Alternatives, `Criteria`, `Decision Outcome`, `Consequences`).
* **REQ-PRO-116.2 (`Honest PO Sign-Off, Code Grounding & Architecture Index`)**: Mark each new ADR `Status: Retrospective (Pending PO Sign-Off)` and `Deciders: Architecture Board, Product Owner (Pending Sign-Off)` (zero fabricated PO signature), cite only real `app/src/main/` classes (zero phantom `TrackPointsDatabaseManager`, `ANTDevice`, `BTLEDevice`), and index `ADR-001`..`ADR-007` in `docs/architecture.md`.
* **REQ-PRO-116.3 (`JVM-Reflection ADR Contract Test`)**: `AdrCompletenessContractTest.kt` SHALL verify `ADR-002`..`ADR-007` structure, `>= 2` evaluated alternatives, honest PO sign-off status, `docs/architecture.md` index links, and compiled production classes via `Class.forName` with ZERO `src/main/` `.readText()` scans (`REQ-PRO-049`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-116` refining `REQ-ARC-005` (`ADR-001` & `JavaCoreMigrationStrategyContractTest.kt`), `REQ-PRO-043` (`docs/architecture.md` & `AspiceSweAuditContractTest.kt`), and `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`).
* **Historical Origin & Commit Trace**: `ATT-7566`/`ATT-8115` (`REQ-ARC-005`), `ATT-5431`/`ATT-6727` (`REQ-PRO-043`), `ATT-6186` (`REQ-PRO-049`), extended via `ATT-8161`.
* **Root Reason for Existing Formulation**: `ADR-001` captured Java-to-Kotlin migration scope only, leaving the 6 foundational architecture choices without explicit evaluated alternatives.
* **Preservation of Core Invariants**: Preserves `ADR-001` (`JavaCoreMigrationStrategyContractTest`), `AspiceSweAuditContractTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline), `app/src/main/`, and `ANT-Android-SDKs` (`0e8cea71`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **AC-1 (`TST-PRO-116.1` — ADR Completeness & Honest PO Sign-Off)**: *Given* `docs/architecture/adr_002..007_*.md`, *When* audited by `AdrCompletenessContractTest`, *Then* each has `ADR-001` sections, `>= 2` evaluated alternatives, `Pending PO Sign-Off` status/decider metadata, and zero phantom tokens.
* **AC-2 (`TST-PRO-116.2` — JVM-Reflection Grounding & Index Parity)**: *Given* `docs/architecture.md` and compiled `app/src/main/` classes, *When* `AdrCompletenessContractTest` and `AspiceSweAuditContractTest` run, *Then* all cited classes resolve via `Class.forName` with zero `src/main/` reads and `docs/architecture.md` indexes `ADR-001`..`ADR-007`.
* **AC-3 (`TST-PRO-116.3` — ADR-001 & Governance Preservation)**: *Given* `JavaCoreMigrationStrategyContractTest`, `SourceTextReadingAuditContractTest`, and `verify_requirement_governance.py`, *When* executed, *Then* `ADR-001` stays intact, zero `ATT-8161` `src/main/` reads occur, and governance exits `0`.

---

## 4. Test Specifications & Traceability Matrix (`Localization Parity: N/A - no UI strings`)

| Test ID | Scope / Method | Target Class / Symbol & Verification | REQ Link | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-116.1` | Unit / Review | `AdrCompletenessContractTest.kt`: verifies `docs/architecture/adr_002..007_*.md` structure, `>= 2` evaluated alternatives, `Pending PO Sign-Off` status/decider honesty, and absence of phantom `TrackPointsDatabaseManager`, `ANTDevice`, `BTLEDevice` | `REQ-PRO-116.1`, `REQ-PRO-116.2` | Planned |
| `TST-PRO-116.2` | Unit | `AdrCompletenessContractTest.kt` + `AspiceSweAuditContractTest.kt`: validates cited classes (`TrackerService`, `BANALService`, `BANALServiceRepository`, `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `WorkoutRepository`, `SettingsDataStore`, `TuningPreferencesDataStore`, `MainActivityWithNavigation`, `MyDevice`, `MyRemoteDevice`, `MyANTDevice`, `MyBTLEDevice`, `PolarOnlineStreamingBridge`, `DeviceManager`) via `Class.forName` (zero `src/main/` reads) and `docs/architecture.md` ADR index | `REQ-PRO-116.2`, `REQ-PRO-116.3` | Planned |
| `TST-PRO-116.3` | Unit / Review | `JavaCoreMigrationStrategyContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `verify_requirement_governance.py`: confirms `ADR-001` parity, zero `ATT-8161` `src/main/` reads, and 100% shard/index governance exit `0` | `REQ-PRO-116.3` | Planned |
