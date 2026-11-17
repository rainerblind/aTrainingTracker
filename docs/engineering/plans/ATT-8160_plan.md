# Stage 3: Implementation Plan - ATT-8160: Fix Architecture & Interface Documentation Drift

**Ticket**: [ATT-8160](https://rainerblind.atlassian.net/browse/ATT-8160) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `sprint/2026-11-17_01_Früh` | **Branch**: `feature/ATT-8160` | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-115` (`REQ-PRO-115.1..3`) | **Test Mapping**: `TST-PRO-115` (`TST-PRO-115.1..3`)

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `Minor (SWE.2)` (`SWE.2 BP1/BP2/BP4`): `docs/architecture/interfaces.md` drifted from live `app/src/main/` method arities, parameter/return types, and nullability across 10 operations; `docs/architecture.md` cites brittle `TrackerService.java:842-857`, omits `SPT`/`HRV`/`RAD`/`WKO`/`CLM` prefixes in Section 2, and mixes ASPICE 3.1/4.0 BP labels. `ArchitectureConformanceContractTest` L352 hardcodes `:842-857`, and `AspiceSweAuditContractTest` only regex-checks method names (`fun <name>(`).

## 2. Traceability & Requirements Mapping
* `REQ-PRO-115.1` (Remediate all 10 `interfaces.md` signature/arity/return/nullability drifts) -> `TST-PRO-115.1` (`InterfaceSignatureDriftContractTest.kt`).
* `REQ-PRO-115.2` (Symbol anchors `TrackerService#acquireWakeLock` / `releaseWakeLock`, Section 2 prefixes `SPT`/`HRV`/`RAD`/`WKO`/`CLM`, ASPICE 4.0 `BP1`–`BP5` alignment, `ArchitectureConformanceContractTest.kt` update) -> `TST-PRO-115.2` (`ArchitectureConformanceContractTest.kt`, `AspiceSweAuditContractTest.kt`).
* `REQ-PRO-115.3` (JVM-reflection signature drift check with zero `app/src/main/` file reads) -> `TST-PRO-115.1`, `TST-PRO-115.3` (`InterfaceSignatureDriftContractTest.kt`, `SourceTextReadingAuditContractTest.kt`).

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Edits & Submodule Lock**: Zero edits to `app/src/main/`, `app/src/androidTest/`, or `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`).
2. **Source-Text Audit Compliance (`REQ-PRO-049`)**: `InterfaceSignatureDriftContractTest.kt` MUST inspect `app/src/main/` strictly via **JVM reflection (`Class.forName`)** — ZERO `.readText()`, `.readLines()`, `Files.readString()`, or text scanning of `app/src/main/`. `SourceTextReadingAuditContractTest` counts (`22` guards, `105` baseline) remain untouched.
3. **Contract Compatibility (Chesterton's Fence)**: Preserve all `AspiceSweAuditContractTest` Section 2/5/6 checks (`Presentation Layer`, `Domain & Scientific Calculators`, 9 shard names, backticked components, `Provider`/`Consumer`/`Data Payloads` types), all `ArchitectureConformanceContractTest` WakeLock/threading/NFR checks (`PARTIAL_WAKE_LOCK`, `mScheduler`, `mDbExecutor`, `REQ-NFR-003..014`), and `DetailedDesignParityContractTest` §3.5 anchors.
4. **UI Consistency**: N/A — zero UI, Compose, or `strings*.xml` modifications.

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-115.1` -> `TST-PRO-115.1`) — `docs/architecture/interfaces.md`**:
   * Align header/intro (L1–3) to ASPICE 4.0 `SWE.2.BP1` (*Specify static aspects of the software architecture*).
   * Fix all 10 drifted signatures without breaking `AspiceSweAuditContractTest` type/method extraction: `IF-SWE-02` `WorkoutRepository.updateExtremaValue(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType, value: Double, position: LatLng? = null)` (+ 6-param overload); `IF-SWE-03` `BANALServiceComm.getAllFilteredSensorData(): List<FilteredSensorData<?>>` and `getBestSensorData(sensorType: SensorType): SensorData`; `IF-SWE-05` `AutoPauseManager.evaluateTick` (7 params), `PowerPhysiologyCalculator.calculateNormalizedPower(powerSamplesWatts: List<Double?>, sampleIntervalSec: Double = 1.0): Double?`, `PowerHrDynamicsCalculator.calculate(powerSamples: List<Double?>?, hrSamples: List<Double?>?, workoutId: Long = 0L, dt: Double = 1.0, timestampsSec: List<Long>? = null): CardioPowerDynamicsRecord?`, `WorkoutClusterEngine.suggestCluster` (11 params), `ClimbDetector.detectClimbs` (6 params); `IF-SWE-06` `WorkoutRepository.recordLiveSample` (8 params returning `Boolean`); `IF-SWE-08` `HealthConnectManager.syncWorkout` (3 params) and `HealthConnectHrvImporter.importRecentHrv` (4 params).
2. **Step 2 (`REQ-PRO-115.2` -> `TST-PRO-115.2`) — `docs/architecture.md`**:
   * Replace `TrackerService.java:842-857` (L308) with symbol anchors `TrackerService#acquireWakeLock` / `releaseWakeLock`.
   * Add missing Section 2 prefixes: `SPT`, `HRV`, `RAD`, `WKO` (Post-Workout Analytics & Periods), `CLM`, `RAD` (Maps, Polylines & Navigation), `RAD` (Persistence, Migration & Cloud Export).
   * Align all SWE.2 labels (L3, L7, L82, L100, L253, L297–298, L317–319) to ASPICE 4.0 (`BP1` Static & Interfaces, `BP2` Dynamic Behavior, `BP3` Analyze Architecture & Resource/Timing Budgets, `BP4` Consistency & Bidirectional Traceability/Allocation, `BP5` Communicate Agreed Architecture), preserving `### 4.9 Resource and Timing Budgets`, `5. Software Interface Specifications`, and §3.5/§5.4.
3. **Step 3 (`REQ-PRO-115.2` -> `TST-PRO-115.2`) — `ArchitectureConformanceContractTest.kt`**:
   * Update `testArchitectureDoc_definesResourceAndTimingBudgets` (L352) to assert `acquireWakeLock` / `releaseWakeLock` symbol anchors (and absence of brittle `:842-857`) while keeping `PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`), `mScheduler`, `mDbExecutor`, and `REQ-NFR-003..014` intact.
4. **Step 4 (`REQ-PRO-115.3` -> `TST-PRO-115.1`, `TST-PRO-115.3`) — `InterfaceSignatureDriftContractTest.kt`**:
   * Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/InterfaceSignatureDriftContractTest.kt` (`// Linked test specs: TST-PRO-115`).
   * Parse method/property signatures from `docs/architecture/interfaces.md` (`IF-SWE-01..08`) and inspect provider classes strictly via JVM reflection (`Class.forName`, `declaredMethods`, parameter count/arity excluding `suspend` `Continuation`, parameter types, return type & `@Nullable`/`@NotNull` nullability; ZERO `src/main/` file reads).
5. **Step 5 (Verification)**:
   * Ensure worktree submodule (`git submodule update --init ANT-Android-SDKs` or verify `0e8cea71`) and root config symlinks (`local.properties`, `keystore.properties` if needed) are intact, then execute:
     `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.InterfaceSignatureDriftContractTest" --tests "*.ArchitectureConformanceContractTest" --tests "*.AspiceSweAuditContractTest" --tests "*.DetailedDesignParityContractTest" --tests "*.SourceTextReadingAuditContractTest"`
   * Run `python3 tools/verify_requirement_governance.py`.

## 5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-044` (`interfaces.md`, `AspiceSweAuditContractTest.kt`), `REQ-PRO-052` (`docs/architecture.md` §4.9, `ArchitectureConformanceContractTest.kt`), `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`), refined by `REQ-PRO-115`.
* **Historical Origin & Commit Trace**: `ATT-6180`/`ATT-6732` (`REQ-PRO-044`), `0777baa6` (`ATT-6735`, `REQ-PRO-052`), `ATT-6186` (`REQ-PRO-049`), remediated in `ATT-8160`.
* **Root Reason for Existing Formulation**: Regex name checks missed parameter arity/types/nullability; `:842-857` predated `TrackerService.java` edits; `REQ-PRO-049` prohibits unallowlisted `src/main/` text reads.
* **Preservation of Core Invariants**: Keeps all `AspiceSweAuditContractTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, and `SourceTextReadingAuditContractTest` invariants green.

## 6. Rollback Plan
* Isolated on `feature/ATT-8160`; `git checkout --` / `git reset` restores pre-change state with zero runtime risk.
