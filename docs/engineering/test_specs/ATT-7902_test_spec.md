# Stage 2: Requirement & Test Specification - ATT-7902: Core Service Characterisation Scaffolding

**Ticket**: [ATT-7902](https://rainerblind.atlassian.net/browse/ATT-7902)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Requirement Mapping**: `REQ-ARC-008` (`REQ-ARC-008.1`..`REQ-ARC-008.3`)  
**Test Spec ID**: `TST-ARC-008` (`TST-ARC-008.1`..`TST-ARC-008.4`)  
**Branch**: `feature/ATT-7902` (Lane 3)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15  

---

## 1. Requirement Specification (`REQ-ARC-008`)

### 1.1 Problem Statement & Rationale
Under ADR-001 (ATT-7566), `TrackerService.java` (3,307 LOC) and `BANALService.java` (864 LOC) constitute the core recording and telemetry engines. Per PO directive (ATT-7749, ATT-7902), comprehensive Golden Master characterisation test fixtures must be established to assert public contracts and state machines, under a strict zero-production-edit constraint.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. **TrackerService State Machine & Lifecycle (`REQ-ARC-008.1`)**: Test fixtures SHALL exercise transitions across `TrackingMode` (`IDLE` -> `READY` -> `TRACKING` -> `PAUSED` -> `STOPPED`) and `AutoPauseState`, 1-Hz database tick dispatching, sample persistence, crash/resume recovery, and post-stop export gating parity (`ATT-7918`).
2. **BANALService Sensor Arbitration & Dispatching (`REQ-ARC-008.2`)**: Test fixtures SHALL exercise multi-device ANT+/BLE sensor arbitration, `ProxySensor` event listener registration and notification, filter query integration (`FilterManager`), and timeout failover.
3. **Zero Production Code Changes (`REQ-ARC-008.3`)**: All test fixtures SHALL reside strictly in `app/src/test/`; zero edits to `app/src/main/`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* `TrackerService` characterisation fixtures, *When* full session lifecycle and 1-Hz sample dispatching execute, *Then* state transitions match legacy Java behavior.
* **Criterion 2**: *Given* `BANALService` characterisation fixtures, *When* multi-sensor arbitration and `ProxySensor` dispatching execute, *Then* priority resolution matches legacy service behavior.
* **Criterion 3**: *Given* git working tree, *When* audited, *Then* zero modifications exist in `app/src/main/`.

### 1.5 System Invariants
Zero production bytecode modifications; 100% green test pass rate; `ANT-Android-SDKs` submodule gitlink (`160000`) untouched.

---

## 2. Test Specification (`TST-ARC-008`)

### Test Case 1: TrackerService State Machine & 1-Hz Tick Dispatching (`TST-ARC-008.1`)
* **Scope**: Robolectric Unit Test (`com.atrainingtracker.tracker.*`)
* **Procedure**: Initialize `TrackerService`, cycle through `TrackingMode` transitions, advance clock by 1-s intervals, and verify database sample persistence and export gating.

### Test Case 2: TrackerService Crash/Resume Recovery (`TST-ARC-008.2`)
* **Scope**: Robolectric Unit Test
* **Procedure**: Simulate service destruction during active tracking and verify state recovery from persistent preferences and DB.

### Test Case 3: BANALService Multi-Sensor Arbitration & Dispatching (`TST-ARC-008.3`)
* **Scope**: Unit Test (`com.atrainingtracker.banalservice.*`)
* **Procedure**: Register ANT+ and BLE sensor data feeds on `BANALService`, verify `ProxySensor` listener updates, filter application, and timeout failover.

### Test Case 4: Zero Production Code Invariance Audit (`TST-ARC-008.4`)
* **Scope**: Governance / Git Diff Audit
* **Procedure**: Verify `git status` reports 0 modifications under `app/src/main/`.

---

## 3. Traceability Matrix

| Requirement | Test Spec | Verification Method | Pass Target |
| :--- | :--- | :--- | :--- |
| `REQ-ARC-008.1` | `TST-ARC-008.1` | Unit / Robolectric | 100% Pass |
| `REQ-ARC-008.1` | `TST-ARC-008.2` | Unit / Robolectric | 100% Pass |
| `REQ-ARC-008.2` | `TST-ARC-008.3` | Unit / MockK | 100% Pass |
| `REQ-ARC-008.3` | `TST-ARC-008.4` | Git Status Audit | Clean (`main/` untouched) |
