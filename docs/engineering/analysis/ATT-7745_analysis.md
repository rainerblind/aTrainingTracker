# Stage 1 Analysis: ATT-7745 - Auto-name workout, trigger Warmup/Cooldown lap splits, and stop recording on Guided HRV Test completion

**Ticket**: [ATT-7745](https://rainerblind.atlassian.net/browse/ATT-7745)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7745`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Currently, completing a Guided DFA α1 Threshold Test (`HrvRampTestViewModel`) leaves tracking recording indefinitely until manually stopped, creates no lap boundaries between Warmup, Active Test, and Cooldown phases, and fails to persist a localized descriptive workout title (leaving it as a raw timestamp or allowing cluster/known-location auto-naming in `TrackerService.finalizeLiveSession()` to overwrite it).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing Phase Lap Splits**:
   - `HrvRampTestViewModel.processTick()` transitions `WARMUP -> TESTING` when `stepRemaining <= 0` without broadcasting `TrainingApplication.REQUEST_NEW_LAP`.
   - `HrvRampTestViewModel.transitionToCooldown()` switches phase to `COOLDOWN` without broadcasting `REQUEST_NEW_LAP`.
   - Consequently, the entire workout records as a single unsegmented lap, conflating warmup, active testing, and recovery.
2. **Missing Automated Workout Stop**:
   - Neither `processTick()` (upon `COOLDOWN` reaching 0) nor `finishTest()` broadcasts `TrainingApplication.REQUEST_STOP_TRACKING`, leaving the background service actively recording post-test.
3. **Missing & Overwritten Workout Name**:
   - `tagActiveWorkoutWithProtocol()` only populates `method` and `goal`, leaving `WORKOUT_NAME` null/empty.
   - `TrackerService.finalizeLiveSession()` (lines 3176, 3206) unconditionally executes cluster and known-location auto-naming without checking if the workout is a guided protocol (`method == GUIDED_STEP || GUIDED_RAMP`), overwriting any pre-assigned protocol title.
* **Independent Peer Review & Verification**:
   - Inspected `HrvRampTestViewModel.kt` lines 313–352 (`tagActiveWorkoutWithProtocol`) and 413–478 (`processTick`): verified absence of `REQUEST_NEW_LAP` and `REQUEST_STOP_TRACKING` broadcasts.
   - Inspected `TrackerService.java` lines 3170–3210: verified cluster suggestion and location naming overwrite `repository.updateWorkoutName()` without inspecting `method`.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Guardrail
* **In-Scope Goals**:
  1. Broadcast `REQUEST_NEW_LAP` on `WARMUP -> TESTING` transition and upon entering `COOLDOWN` (producing 3 canonical laps: Warmup, Active Test, Cooldown).
  2. Broadcast `REQUEST_STOP_TRACKING` when cooldown expires (`COOLDOWN -> FINISHED`) or `finishTest()` is invoked while tracking is active.
  3. Localized workout title persistence (`hrv_workout_name_step` / `hrv_workout_name_ramp` across 9 locales) in `tagActiveWorkoutWithProtocol()`.
  4. Guard `TrackerService.finalizeLiveSession()` to preserve the guided test workout name and suppress cluster/location name overwrites.
* **Out-of-Scope Non-Goals**:
  - Per-step lap splits during `GUIDED_STEP` (explicitly rejected to prevent lap clutter).
  - Altering cluster assignment or identity inference (sport and gear inference remain active).
* **Complexity Audit**: Standard 2 SP. No cross-service architectural split required.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Screen*).
* **Historical Origin & Commit Trace**: Added in `ATT-4969` (commit `424580a2`), updated in `ATT-7317` (`fce6777b`), refined in `ATT-7529`.
* **Root Reason for Existing Formulation**: Initial implementation focused on protocol state machine, pre-flight sensor checks, and UI pacing guidance. Workout lifecycle synchronization (laps and stop) was left manual.
* **Preservation of Core Invariants**: Manual tracking stop remains fully functional; pause/resume lap neutrality (`ATT-896`) is untouched; free workout auto-naming and clustering logic are completely preserved.

---

## 5. Change-Request Impact Analysis (SUP.10 / REQ-PRO-064)
* **Affected Requirements**: Extends `REQ-TRK-072` with `REQ-TRK-072.8` (*Lifecycle Automation: Auto-Naming, Lap Splits & Automated Stop*).
* **Affected Architecture Elements**: `HrvRampTestViewModel.kt`, `TrackerService.java`, `WorkoutRepository.kt`, `strings_hrv.xml` (9 locales).
* **Verification Impact & Test Surface**: `HrvRampTestViewModelTest.kt`, `TrackerServiceAutoNamingTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Broadcasts are guarded with `TrainingApplication.isTracking()` and null checks, preventing NPEs or unwanted broadcasts when running in preview/config modes.
