# Stage 3: Implementation Plan - ATT-7902: Core Service Characterisation Scaffolding

**Ticket**: [ATT-7902](https://rainerblind.atlassian.net/browse/ATT-7902)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7`  
**Requirement Mapping**: `REQ-ARC-008` (*Core Services Characterisation Test Scaffolding*)  
**Test Mapping**: `TST-ARC-008` (`TST-ARC-008.1`..`TST-ARC-008.4`)  
**Risk Classification**: `LOW` (Strict Tests-Only Scaffolding; Invariant: Zero edits under `app/src/main/`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Under ADR-001 (ATT-7566) and the PO decision (ATT-7749, ATT-7902), `TrackerService` (3,307 LOC) and `BANALService` (864 LOC) constitute the critical recording and sensor telemetry backbone. This ticket establishes an exhaustive Golden Master characterisation test suite capturing existing runtime contracts, state machines, and concurrency invariants, strictly adhering to the PO constraint of **ZERO edits under `app/src/main/`**.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-008` (`REQ-ARC-008.1`..`REQ-ARC-008.3`)
* **Test Mapping**: `TST-ARC-008` (`TST-ARC-008.1`..`TST-ARC-008.4`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Code Edits**: Strict invariance on `app/src/main/` — zero modifications to `TrackerService.java`, `BANALService.java`, or any production classes.
2. **Behavioral Golden Master**: Fixtures assert exact legacy behaviors without relaxing assertions or masking runtime edge cases.
3. **Local Test Harnesses**: Test helpers and service stubs remain strictly encapsulated within the new test files.
4. **Submodule Invariance**: `ANT-Android-SDKs` submodule gitlink (`160000`) untouched.

---

## 4. Proposed Architectural Changes
* **Production Code**: **NONE** (0 files added, modified, or deleted in `app/src/main/`).
* **Test Code (`app/src/test/`)**:
  * `TrackerServiceStateMachineCharacterisationTest.kt`: Full lifecycle state transitions, 1-Hz tick dispatching, sample persistence, export gating (`ATT-7918`), and auto-pause.
  * `TrackerServiceCrashRecoveryCharacterisationTest.kt`: 3-Tier startup recovery hierarchy, resumed state application, and abort handling.
  * `BANALServiceArbitrationCharacterisationTest.kt`: Multi-device ANT+/BLE sensor arbitration, `ProxySensor` event dispatching, filter query integration, and timeout failover.
* **UI Consistency**: No UI changes (test scaffolding only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: TrackerService State Machine & 1-Hz Sampling Characterisation Fixture
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceStateMachineCharacterisationTest.kt`.
* Changes: Construct Robolectric test exercising `TrackingMode` transitions (`IDLE` -> `READY` -> `TRACKING` -> `PAUSED` -> `READY`), 1-Hz `tracker` runnable execution, sample writing, active/total duration accumulation, and export gating parity.

### Step 2: TrackerService Crash Recovery & Resumed State Characterisation Fixture
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceCrashRecoveryCharacterisationTest.kt`.
* Changes: Construct Robolectric test exercising startup recovery (Tiers 1–3), `applyResumedState` validation, and `onTrackingAborted` state reset with `TRACKING_ABORTED_INTENT`.

### Step 3: BANALService Multi-Sensor Arbitration & Dispatching Characterisation Fixture
* Files: `app/src/test/java/com/atrainingtracker/banalservice/BANALServiceArbitrationCharacterisationTest.kt`.
* Changes: Construct unit test exercising `MySensorManager` multi-sensor arbitration, `ProxySensor` listener updates, `FilterManager` query integration, and timeout handling.

### Step 4: Verification & Governance Audit
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerService*CharacterisationTest" --tests "com.atrainingtracker.banalservice.BANALServiceArbitrationCharacterisationTest"`
* Run: `git status` verifying 0 modifications under `app/src/main/`.
* Run: `python3 tools/verify_requirement_governance.py`
* Run: `./gradlew :app:lintDebug` and Detekt check on new test files.

---

## 6. Verification & Rollback Plan
* **Verification**: 100% test pass rate across new characterisation suites, zero production code diff, and governance pass.
* **Rollback**: Branch isolation allows full revert via `git checkout -- .` before merge.
