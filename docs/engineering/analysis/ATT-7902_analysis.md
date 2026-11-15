# Stage 1 Analysis: ATT-7902 Core Service Characterisation Scaffolding

- **Ticket**: ATT-7902
- **Target Release**: V5.0.0
- **Risk Classification**: Low (Strict Tests-Only Scaffolding; Zero Production Edits)

## 1. Problem Statement & Motivation
Under ADR-001 (ATT-7566), `TrackerService.java` (3,307 LOC) and `BANALService.java` (864 LOC) constitute the core telemetry and recording engines. Per PO architectural decision (ATT-7749), these complex core services remain in Java for now, but require comprehensive Golden Master characterisation test scaffolding to capture their exact runtime contracts, state machines, and concurrency invariants prior to future maintenance.

## 2. Independent Peer Review & Verification
- **Hypothesis Audit**: Ticket proposes test fixtures for session lifecycle (`IDLE` -> `READY` -> `TRACKING` -> `PAUSED`), 1-Hz sample dispatching, crash recovery, and ANT+/BLE sensor arbitration.
- **Codebase Verification**: Audited live sprint branch (`6e7bdce2`). Existing tests (`TrackerServiceWorkoutStartTest`, `TrackerServiceResumeTest`, `BANALServiceLifecycleTest`) cover specific subsystems (resumption, altimeter calibration, lifecycle nullification) but lack unified Golden Master state machine contracts verifying:
  1. `TrackerService`: State transitions across `TrackingMode` and `AutoPauseState`, 1-Hz timer dispatching, sample persistence, export gating, and crash recovery.
  2. `BANALService`: Multi-device arbitration, `ProxySensor` event dispatching, filter query integration, and timeout handling.
- **PO Constraint Verification**: Confirmed PO directive: zero production edits; pure test scaffolding only.

## 3. Scope Bounding
- **In-Scope**: Automated characterisation test suites in `app/src/test/java/com/atrainingtracker/` exercising public API contracts of `TrackerService` and `BANALService` using Robolectric and MockK.
- **Out-of-Scope**: Modifications to `TrackerService.java`, `BANALService.java`, or any production code in `app/src/main/`.

## 4. Requirement Archaeology & Invariants
- Net-new requirement `REQ-ARC-008` (Characterisation Test Scaffolding for Core Services).
- Invariant: Zero production code changes; existing test suites remain 100% green; `ANT-Android-SDKs` submodule gitlink (`160000`) untouched.

## 5. Change Impact Analysis
- **Affected Requirements**: Net-new requirement `REQ-ARC-008` (Characterisation Test Scaffolding for Core Services); zero modifications to existing production requirements.
- **Affected Architecture Elements**: Zero changes to production code in `app/src/main/` (`TrackerService.java`, `BANALService.java`); test scaffolding additions strictly in `app/src/test/`.
- **Verification Impact & Test Surface**: Introduces comprehensive Golden Master characterisation test suite `TST-ARC-008` covering core service state machines, 1-Hz timer dispatching, and sensor arbitration.
- **Risk Assessment & Invariants**: Low risk (tests-only). Invariant: Zero production bytecode changes, 100% green regression test pass rate, submodule gitlink `160000` preserved.
