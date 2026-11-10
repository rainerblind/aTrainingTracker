# Stage 1 Analysis: ATT-6179 - Resynchronize Architecture Document with Code

**Ticket**: [ATT-6179](https://rainerblind.atlassian.net/browse/ATT-6179)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6179`  
**Requirement Mapping**: `REQ-PRO-043` | **Test Mapping**: `TST-PRO-043`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
`docs/architecture.md` (authored under `ATT-5431` for ASPICE SWE.2 compliance) exhibits drift against authentic production code:
1. **Drifted Component Names**: Names non-existent classes: `RouteLearningEngine` (codebase: `WorkoutClusterEngine`), `GpxExporter` / `TcxExporter` (codebase: `GPXFileWriter` / `TCXFileWriter`), `WorkoutSamplesDb` (codebase: `WorkoutSamplesDatabaseManager`), and `MilestoneDetector` / `InAppReviewManager` (codebase: `MilestoneAutoNamingManager` / `FirstJubileeActionHelper`).
2. **State Machine Discrepancy**: Section 3.1's `stateDiagram-v2` invents states (`AUTO_PAUSED`, `MANUAL_PAUSED`, `CRASH_RECOVERY`, `FINISHED`) instead of reflecting top-level `TrackingMode` (`IDLE`, `READY`, `TRACKING`, `PAUSED`) and `AutoPauseState`.
3. **Threading Claim Inaccuracy**: Section 4.1 claims a single global `dbDispatcher` handles all SQLite writes across all managers, whereas `KnownLocationsRepository` uses a dedicated single-threaded dispatcher while `WorkoutRepository` and other managers use `Dispatchers.IO` with WAL and synchronized transactions.

---

## 2. Root Cause Analysis & Independent Verification (4-Augen-Prinzip)
* **Codebase Grounding**:
  * Cluster Engine: `WorkoutClusterEngine.kt` at `database.WorkoutClusterEngine` handles clustering; `RouteLearningEngine` has zero code presence.
  * Exporters: `GPXFileWriter.java` and `TCXFileWriter.java` in `exporter.writer` are the authentic XML writers.
  * Milestones & Reviews: `MilestoneAutoNamingManager.kt` and `FirstJubileeActionHelper` handle detection and review triggering.
  * Tracking Modes: `TrackingMode.kt` defines `IDLE`, `READY`, `TRACKING`, `PAUSED`. `AutoPauseManager.kt` defines sub-states: `TRACKING`, `PAUSE_DEBOUNCE_PENDING`, `AUTO_PAUSED`, `MANUAL_PAUSED`, `SENSOR_LOST`.
  * Dispatchers: `KnownLocationsRepository.kt:47` defines single-threaded `dbDispatcher`, `ClimbsDatabaseManager.kt:39` defaults to `Dispatchers.IO`, and `WorkoutRepository.kt:111` offloads to `Dispatchers.IO`.
  * Contract Test Invariance: `AspiceSweAuditContractTest.testArchitectureDocument_fulfillsAspiceSwe2` requires: layer headings, all 9 shard names, `stateDiagram-v2`, $\ge 2$ `sequenceDiagram`s, `dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`, `location|health`, and `onUpgrade`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Resynchronize component names in `docs/architecture.md` (Mermaid diagrams and requirement allocation table).
  * Refactor Section 3.1 `stateDiagram-v2` to model authentic `TrackingMode` states and transitions, documenting `AutoPauseState` as sub-state.
  * Clarify Section 4.1 dispatcher usage while preserving all contract assertions (`dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`).
  * Extend `AspiceSweAuditContractTest.kt` to assert authentic component names and absence of drifted names.
  * Define `REQ-PRO-043` in `req_process_stability.md` and `TST-PRO-043` in `tst_process_stability.md`.
* **Out-of-Scope Non-Goals**: Modifying production code in `app/src/main/` or changing SQLite schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-043` (*SWE.2 Architecture & Codebase Resynchronization*), refining `REQ-PRO-034`. Targets `docs/architecture.md` and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Commit `053531cb` (`ATT-5431`) authored initial `docs/architecture.md` with preliminary component placeholders.
* **Root Reason for Existing Formulation**: Drafted rapidly during ASPICE pre-assessment before systematic code-grounding was enforced.
* **Preservation of Core Invariants**: Retain all ASPICE SWE.2 base practices (`BP.1`–`BP.5`), 9-shard traceability, and 100% contract test compliance.

---

## 5. Architectural Strategy & High-Level Solution
* Update `docs/architecture.md` components: `WorkoutClusterEngine`, `GPXFileWriter`, `TCXFileWriter`, `WorkoutSamplesDatabaseManager`, `MilestoneAutoNamingManager`, `FirstJubileeActionHelper`.
* Update Section 3.1 `stateDiagram-v2` with `IDLE`, `READY`, `TRACKING`, `PAUSED` and session save/recovery paths.
* Refine Section 4.1 threading description: accurately detail `dbDispatcher` and `Dispatchers.IO` usage.
* Add contract assertions in `AspiceSweAuditContractTest.kt` verifying authentic components and forbidding drifted names.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `AspiceSweAuditContractTest` passes 100%; zero Android production runtime code changes.
* **Risk Rating**: **LOW**. Documentation and build contract test updates only.
