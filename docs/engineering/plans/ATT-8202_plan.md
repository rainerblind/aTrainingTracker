# Stage 3: Implementation Plan - ATT-8202: SWE.2 Heap/Cache/Map Budgets & IF-SWE-05 Upward-Import Guard

**Ticket**: [ATT-8202](https://rainerblind.atlassian.net/browse/ATT-8202) (`2 SP`) | **Sub-task**: `[Impl-Plan]` (assigned on `submit-stage`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8202`  
**Requirement Mapping**: `REQ-PRO-130` (refining `REQ-PRO-050`, `REQ-PRO-052`, `REQ-PRO-115`) | **Test Mapping**: `TST-PRO-130`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
`docs/architecture.md` §4.9 quantifies memory only for `TrackerService` (`< 40 MB`, `REQ-NFR-005`), omitting bounds for singleton repositories (`WorkoutRepository` with unbounded `recentHrvThresholdsCache`, `PeriodsRepository`, `WorkoutClusterRepository`, `RadarStateRepository`) and `GoogleMap` instances (`liteMode` vs interactive GL). `docs/architecture/interfaces.md` (`IF-SWE-05`) mislabels `WorkoutClusterEngine` as a stateless pure-JVM calculator, omits Swim/RadarLight/TPMS/Polar PFTP components, and `ArchitectureConformanceContractTest.kt:55` misses 11 `database -> ui.*` files (including `WorkoutClusterEngine -> ui.aftermath.WorkoutRepository`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-130` (`REQ-PRO-130.1`..`REQ-PRO-130.4` in `docs/requirements/req_process_stability_part2.md`)
* **Test Mapping**: `TST-PRO-130` (`TST-PRO-130.1`..`TST-PRO-130.4` in `docs/tests/tst_process_stability_part2.md`)

---

## 3. System Invariants & Preserved Behavior
1. **Monolith LoC Ceilings (`REQ-PRO-129.2`)**: `WorkoutRepository.kt <= 5,117` LoC; `WorkoutClusterEngine.kt <= 1,635` LoC; zero net-new `> 1,500` LoC files.
2. **Governance & Baseline Parity**: `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `REQ-NFR-003`..`014` contract parity, `28/28` shards `<= 4,000` lines.
3. **Zero Schema / UI String Changes**: Zero SQLite migrations, `ANT-Android-SDKs` untouched, `Localization Parity: N/A - no UI strings`.
4. **Parent Human Gate**: Terminal parent transitions remain human-only in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
### Component 1: Bounded `recentHrvThresholdsCache` & `IF-SWE-05` Decoupling (`WorkoutRepository.kt`, `ClusterWorkoutDataPort.kt`, `WorkoutClusterEngine.kt`)
* Replace unbounded `ConcurrentHashMap<Long, HrvSessionThreshold>` in `WorkoutRepository.kt:1850` with bounded `LruCache<Long, HrvSessionThreshold>(64)` + eviction on `refreshWorkout()`, `deleteWorkout*()`, and `resetForTesting()`, keeping `WorkoutRepository.kt <= 5,117` LoC.
* Introduce `ClusterWorkoutDataPort` in `com.atrainingtracker.trainingtracker.database` (`ClusterWorkoutDataPort.kt`, `< 150` LoC) decoupling `WorkoutClusterEngine` from `ui.aftermath.WorkoutRepository` (implemented/registered by `WorkoutRepository`, preserving `WorkoutClusterEngine.kt <= 1,635` LoC).
### Component 2: SWE.2 Budgets & Subsystem Alignment (`docs/architecture.md`, `docs/architecture/interfaces.md`)
* Extend `docs/architecture.md` §4.9 with quantitative heap/cache/vertex/`GoogleMap` budgets (`MAX_OVERLAY_POINTS = 800`, `recentHrvThresholdsCache <= 64`, `MAX_SUMMARY_THUMBNAIL_TRACKS = 8`, `<= 5` anchors, `recentlyFinishedWorkoutIds <= 50`, `periodCardMapStateCache = LruCache(64)`, `2_000`/`8_000` vertices, `RadarStateRepository <= 4`, `WorkoutClusterRepository` `extraBufferCapacity = 16`, `liteMode(true)` thumbnails vs `beyondViewportPageCount = 0` GL isolation).
* Align `docs/architecture.md` (§2, §5.1) & `docs/architecture/interfaces.md` for Swim, RadarLight, TPMS, Polar PFTP, and honest `IF-SWE-05` (`Context`/SQLite-backed `WorkoutClusterEngine` + `ClusterWorkoutDataPort` vs pure-JVM calculators) with `OWNER_FQCNS`/`TYPE_FQCNS` parity in `InterfaceSignatureDriftContractTest.kt`.
### Component 3: Full `.ui.*` Database Upward-Import Ratchet (`ArchitectureConformanceContractTest.kt`)
* Replace suffix regex at line 55 with `\bimport\s+com\.atrainingtracker\..*\.ui\b` across `trainingtracker/database/` and `banalservice/database/`, enforced against the exact `11`-file legacy baseline (`banalservice/database/ == 0`), failing on any net-new upward import or stale baseline entry, plus explicit `WorkoutClusterEngine` zero-`WorkoutRepository` import check & negative `ui.aftermath` tripwire.
### UI Consistency (Rule 23)
* No UI changes (`Localization Parity: N/A - no UI strings`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Bound `recentHrvThresholdsCache` & Decouple `WorkoutClusterEngine` (`TST-PRO-130.1`, `TST-PRO-130.2`)
* Files: `WorkoutRepository.kt`, `ClusterWorkoutDataPort.kt` (new), `WorkoutClusterEngine.kt`, `WorkoutRepositoryHrvConsensusTest.kt`.
* Changes: Switch `recentHrvThresholdsCache` to `LruCache<Long, HrvSessionThreshold>(64)` with lifecycle eviction; extract `ClusterWorkoutDataPort` so `WorkoutClusterEngine` drops `ui.aftermath.WorkoutRepository` import; add unit test verifying eviction beyond 64 entries and reset cleanup.
### Step 2: Update SWE.2 Architecture & Interface Specs (`TST-PRO-130.1`, `TST-PRO-130.2`)
* Files: `docs/architecture.md`, `docs/architecture/interfaces.md`, `InterfaceSignatureDriftContractTest.kt`.
* Changes: Document §4.9 repository/cache/vertex/`GoogleMap` budgets, §2/§5.1 Swim/RadarLight/TPMS/Polar PFTP components, and truthful `IF-SWE-05` signatures; register new documented owner/type classes in `InterfaceSignatureDriftContractTest.kt`.
### Step 3: Broaden `IF-SWE-05` Upward-Import Guard (`TST-PRO-130.3`, `TST-PRO-130.4`)
* Files: `ArchitectureConformanceContractTest.kt`.
* Changes: Enforce `\bimport\s+com\.atrainingtracker\..*\.ui\b` against exact `11`-file legacy baseline (`banalservice/database/ == 0`), assert zero `WorkoutRepository` import in `WorkoutClusterEngine.kt`, and update tripwire test for `ui.aftermath`.
### Step 4: Targeted Verification
* Command: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.NonFunctionalRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryHrvConsensusTest" --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier-1 targeted unit/contract suites in Stage 4/5 plus `python3 tools/verify_requirement_governance.py --repo-root .` and `python3 tools/shard_living_docs.py --check-size --strict`.
* **Rollback**: Isolated on `feature/ATT-8202`; revert via `git reset` without touching `sprint/2026-11-18_01_Früh` or `develop`.
