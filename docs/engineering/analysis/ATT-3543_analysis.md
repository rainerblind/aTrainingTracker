# Stage 1 Analysis: ATT-3543 - Lossless WorkoutRrIntervals, Centered Recompute & Garmin FIT HrvMesg

**Ticket**: [ATT-3543](https://rainerblind.atlassian.net/browse/ATT-3543)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3543`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-034` (`docs/requirements/req_aftermath_periods.md`)  
**Test Mapping**: `TST-PER-042` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: Live tracking computes DFA $\alpha_1$ and $\text{rMSSD}$ via causal trailing windows (~60s lag). Raw beat-to-beat intervals from BLE/ANT+ straps (`ATT-3540`) are currently transient and discarded after the workout.
* **Gap**: Discarding raw beats prevents centered-window post-workout recomputation, creating a ~60s phase lag on post-workout threshold charts (`ATT-3544`, `ATT-3554`) and blocking Garmin FIT `HrvMesg` export/import.
* **Objective**: Persist beat-to-beat intervals into a dedicated SQLite table `WorkoutRrIntervals`, implement `HrvPostWorkoutRecomputer` to evaluate lag-free centered-window DFA $\alpha_1$ ($t \pm 60\text{s}$) and $\text{rMSSD}$ ($t \pm 30\text{s}$), and support native Garmin FIT `HrvMesg` bidirectional export and import.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: High-frequency beats ($2\text{--}3\text{ beats/s}$) cannot be stored in 1-Hz `WorkoutSamples` rows without severe aliasing and information loss.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Beat Timestamp Reconstruction*: Live beats buffered via `RrBeatListener` use cumulative reconstruction $t_{\text{beat},k} = t_{\text{beat},k-1} + \text{round}(\text{rr\_ms}_k)$ with wall-clock drift resync when $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$, ensuring exact UTC epoch alignment with `WorkoutSamples`.
  * *Lag-Free Centered Windows*: Live trailing windows lag athletic changes by ~60s. Centered bidirectional filtering (Lipponen & Tarvainen 2019 centered filter) over $t \pm 60\text{s}$ (DFA $\alpha_1$) and $t \pm 30\text{s}$ ($\text{rMSSD}$) eliminates phase lag, aligning autonomic recovery with Power, Pace, and HR.
  * *Garmin FIT `HrvMesg`*: Packs up to 5 intervals (in seconds) per message. During FIT import, timestamps are reconstructed anchored to session start UTC with `RecordMesg` pause resync.
  * *Architecture Conformance*: Adheres to `docs/coding_standards.md` §2.1–2.3 and `ArchitectureConformanceContractTest.kt` via repository encapsulation; `WorkoutDeletionHelper` cascade deletion and `DatabaseSchemaRegistry` backup integration.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * `WorkoutRrDatabaseManager.kt` & `WorkoutRrInterval.kt`: Table `workout_rr_intervals` (`workout_id, beat_index, beat_timestamp_ms, rr_ms, artifact_flag, gap_beats`), indices, symmetric DTO mapping, deletion cascading, `DatabaseSchemaRegistry` registration.
  * `HrvPostWorkoutRecomputer.kt`: Centered-window DFA $\alpha_1$ & $\text{rMSSD}$ computation, updating `WorkoutSamples` and `WorkoutSummaries` session extrema.
  * `FitFileWriter.java`: Interleaving `HrvMesg` with `RecordMesg` trackpoints during FIT export.
  * `FitWorkoutParser.kt` & `LegacyImportEngine.kt`: Extracting `HrvMesg`, reconstructing beat timestamps, persisting beats, and triggering recompute.
  * `TrackerService.java`: Buffering and persisting `RrBeatEvent` stream during session and triggering recompute upon finalization.
* **Out-of-Scope**: Post-workout DFA $\alpha_1$ scatter plots (`ATT-3544`); session HRVT1/HRVT2 detection (`ATT-3554`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement (`REQ-PER-034`) and test specification (`TST-PER-042`). No existing requirements modified.
* **Side-Effect Audit (`Rule 68`)**:
  * Workouts without RR sensors incur zero overhead (0 rows in `WorkoutRrIntervals`, 0 `HrvMesg` written).
  * Recompute runs asynchronously during finalization without blocking UI or delaying completion intents.
  * `WorkoutDeletionHelper` ensures zero orphaned RR records upon workout deletion.

---

## 5. Architectural Strategy & High-Level Solution
* **Persistence Layer**: `WorkoutRrDatabaseManager` manages `WorkoutRr.db` with batch inserts and deletion cascades.
* **Recomputation Engine**: `HrvPostWorkoutRecomputer` runs post-workout on `ioDispatcher` via `RrArtifactFilter.filterBidirectional`.
* **Export/Import Integration**: `FitFileWriter` writes standard FIT `HrvMesg` arrays; `LegacyImportEngine` imports and triggers recomputation.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Monotonic beat timestamps: `beat_timestamp_ms` strictly UTC epoch ms.
  2. Lossless persistence: Raw RR values preserved unmodified alongside artifact flags.
  3. Backward compatibility: Non-HRV workouts and existing FIT exports remain completely unaffected.
* **Risk Rating**: **MEDIUM** (3 SP) — Multi-component database persistence, post-workout signal recomputation, and binary Garmin FIT message parsing.
