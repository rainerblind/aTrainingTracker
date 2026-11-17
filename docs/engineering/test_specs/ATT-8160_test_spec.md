# Stage 2: Requirement & Test Specification - ATT-8160: Architecture & Interface Drift

**Ticket**: [ATT-8160](https://rainerblind.atlassian.net/browse/ATT-8160) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `2026-11-17_01_Früh` | **SP**: 2 SP | **Status**: Planned | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-115` (`REQ-PRO-115.1..3`) | **Refines**: `REQ-PRO-044`, `REQ-PRO-052`, `REQ-PRO-049`
**Test Spec Mapping**: `TST-PRO-115` (`TST-PRO-115.1..3`)

---

## 1. Requirement Specification (`REQ-PRO-115`)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-115.1 (`Interface Signature & Nullability Remediation`)**: `docs/architecture/interfaces.md` SHALL match live `app/src/main/` declarations across all drifted signatures: `WorkoutRepository.updateExtremaValue` (5-arg + 6-arg overloads), `BANALServiceComm.getAllFilteredSensorData(): List<FilteredSensorData<?>>` and `getBestSensorData(SensorType): SensorData`, `AutoPauseManager.evaluateTick` (7 params), `PowerPhysiologyCalculator.calculateNormalizedPower(List<Double?>, Double = 1.0): Double?`, `PowerHrDynamicsCalculator.calculate` (5 params with `dt` and `timestampsSec`), `WorkoutClusterEngine.suggestCluster` (11 params), `ClimbDetector.detectClimbs` (6 params), `WorkoutRepository.recordLiveSample` (8 params returning `Boolean`), `HealthConnectManager.syncWorkout` (3 params), and `HealthConnectHrvImporter.importRecentHrv` (4 params).
* **REQ-PRO-115.2 (`Symbol Anchors, Prefix Allocation & ASPICE 4.0 BP Labels`)**: `docs/architecture.md` SHALL replace `TrackerService.java:842-857` with `TrackerService#acquireWakeLock` / `releaseWakeLock`, add `SPT` (plus `HRV`, `RAD`, `WKO`, `CLM`) to Section 2, and standardize all SWE.2 headings across `docs/architecture.md` and `docs/architecture/interfaces.md` to ASPICE 4.0 (`BP1` Static & Interfaces, `BP2` Dynamic, `BP3` Analysis & Budgets, `BP4` Traceability & Consistency, `BP5` Communication). `ArchitectureConformanceContractTest.kt` SHALL assert the WakeLock symbol anchors while preserving `PARTIAL_WAKE_LOCK`, `mScheduler`, `mDbExecutor`, and `REQ-NFR-003`..`014`.
* **REQ-PRO-115.3 (`JVM-Reflection Signature-Drift Contract Test`)**: `InterfaceSignatureDriftContractTest.kt` SHALL parse `docs/architecture/interfaces.md` signatures and verify via **JVM reflection** (`Class.forName`, `declaredMethods`, parameter count/arity, parameter/return types, `@Nullable`/`@NotNull` nullability) against compiled `app/src/main/` classes with ZERO `src/main/` file reads (`REQ-PRO-049`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-115` refining `REQ-PRO-044` (`interfaces.md` & `AspiceSweAuditContractTest.kt`), `REQ-PRO-052` (`docs/architecture.md` §4.9 & `ArchitectureConformanceContractTest.kt`), and `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`).
* **Historical Origin & Commit Trace**: `ATT-6180`/`ATT-6732` (`REQ-PRO-044` name-only regex checks), `0777baa6` (`ATT-6735`, `REQ-PRO-052` hardcoded `TrackerService.java:842-857`), and `ATT-6186` (`REQ-PRO-049` bans unallowlisted `src/main` file reads in tests).
* **Root Reason for Existing Formulation**: Regex method-name checks missed arity/type/nullability drift; `:842-857` predated `TrackerService.java` edits (`acquireWakeLock` L1079-1092, `releaseWakeLock` L1094-1102); ASPICE 3.1 BP numbering persisted in early SWE.2 headings.
* **Preservation of Core Invariants**: Preserves all `AspiceSweAuditContractTest` Section 2/5/6 checks, all `ArchitectureConformanceContractTest` `PARTIAL_WAKE_LOCK`/`mScheduler`/`mDbExecutor`/`REQ-NFR-003..014` checks, `DetailedDesignParityContractTest`, `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline), and `ANT-Android-SDKs` (`0e8cea71`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **AC-1 (`TST-PRO-115.1` — Interface Signature Drift Check)**: *Given* `docs/architecture/interfaces.md` and compiled `app/src/main/` classes, *When* `InterfaceSignatureDriftContractTest` runs via JVM reflection, *Then* all documented `IF-SWE-01..08` signatures match compiled names, parameter counts, types, and nullability.
* **AC-2 (`TST-PRO-115.2` — Symbol Anchors, Prefixes & ASPICE 4.0 BP Labels)**: *Given* `docs/architecture.md` and `docs/architecture/interfaces.md`, *When* `ArchitectureConformanceContractTest` and `AspiceSweAuditContractTest` execute, *Then* `TrackerService#acquireWakeLock` / `releaseWakeLock` replace `:842-857`, Section 2 includes `SPT`/`HRV`/`RAD`/`WKO`/`CLM`, and SWE.2 labels match ASPICE 4.0 `BP1`–`BP5`.
* **AC-3 (`TST-PRO-115.3` — Source-Reading & Governance Compliance)**: *Given* `SourceTextReadingAuditContractTest` and `verify_requirement_governance.py`, *When* executed, *Then* zero `src/main` file reads are flagged and governance exits `0`.

---

## 4. Test Specifications & Traceability Matrix (`Localization Parity: N/A - no UI strings`)

| Test ID | Scope / Method | Target Class / Symbol & Verification | REQ Link | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-115.1` | Unit | `InterfaceSignatureDriftContractTest.kt`: parses `interfaces.md` signatures (`IF-SWE-01..08`) and verifies via JVM reflection (`Class.forName`, `declaredMethods`, arity, param/return types, `@Nullable`/`@NotNull`) against `TrackingViewModel`, `ControlTrackingViewModel`, `TrackOnMapAftermathViewModel`, `WorkoutRepository`, `BANALServiceRepository`, `BANALService$BANALServiceComm`, `AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, `ClimbDetector`, `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `WorkoutClusterDatabaseManager`, `BaseExporter`, `HealthConnectManager`, `HealthConnectHrvImporter` | `REQ-PRO-115.1`, `REQ-PRO-115.3` | Planned |
| `TST-PRO-115.2` | Unit / Review | `ArchitectureConformanceContractTest.kt` + `AspiceSweAuditContractTest.kt`: verifies `TrackerService#acquireWakeLock` / `releaseWakeLock` symbol anchors (no `:line` citation), `PARTIAL_WAKE_LOCK`/`mScheduler`/`mDbExecutor`/`REQ-NFR-003..014`, Section 2 prefixes (`SPT`, `HRV`, `RAD`, `WKO`, `CLM`), and ASPICE 4.0 SWE.2 `BP1`–`BP5` labels | `REQ-PRO-115.2` | Planned |
| `TST-PRO-115.3` | Unit / Review | `SourceTextReadingAuditContractTest.kt` + `verify_requirement_governance.py`: confirms zero `src/main/` file reads (`22` guards / `105` baseline intact) and 100% shard/index parity | `REQ-PRO-115.3` | Planned |
