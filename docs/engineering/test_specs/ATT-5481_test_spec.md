# Stage 2: Requirement & Test Specification - ATT-5481: Eliminate 1-Hz Calendar Allocation

**Ticket**: [ATT-5481](https://rainerblind.atlassian.net/browse/ATT-5481)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5481`  
**Requirement Mapping**: `REQ-TRK-052` | **Test Spec ID**: `TST-TRK-052`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-TRK-052`)

### 1.1 Problem Statement & Rationale
In `ClockDevice$ClockTimeTask.run()`, `mTimeOfDaySensor.newValue(df.format(Calendar.getInstance().getTime()))` allocates fresh `GregorianCalendar` and `Date` instances on every 1-Hz tick ($\ge 43,200$ objects during a 4-hour workout), inducing unnecessary garbage collector churn and battery drain.

### 1.2 Functional & Architectural Requirements
1. **Pre-Allocated Date Holder (`REQ-TRK-052.1`)**: `ClockDevice.java` SHALL maintain a single private final `Date` instance (`mReusableDate = new Date()`) initialized at construction time.
2. **Zero-Allocation Mutation (`REQ-TRK-052.2`)**: In `ClockTimeTask.run()`, the task SHALL update `mReusableDate.setTime(System.currentTimeMillis())` and format via `df.format(mReusableDate)`. Calling `Calendar.getInstance()` or creating new `Date` objects inside `ClockTimeTask` SHALL be prohibited.
3. **Output Parity & Confinement Invariance (`REQ-TRK-052.3`)**: Emitted time-of-day strings SHALL remain 100% identical in format, locale, and timezone. Thread confinement on `Timer-0` guarantees safe single-threaded mutation.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Zero Allocation)**: *Given* active workout tracking, *When* `ClockTimeTask.run()` ticks at 1 Hz, *Then* time-of-day formatting executes with 0 bytes allocated on the heap.
* **AC-2 (Formatting Parity)**: *Given* `ClockDeviceResilienceTest`, *When* evaluated, *Then* `mTimeOfDaySensor` receives formatted time strings identical to `SimpleDateFormat.getTimeInstance().format(...)`.

### 1.4 System Invariants
Time-of-day format, timezone offset, 1-Hz broadcast cadence (`NEW_TIME_EVENT_INTENT`), active/total/lap time increments, and test suite pass rate strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-TRK-052`, extending `REQ-TRK-027` (*Clock & Sensor Resilience*) and `REQ-TRK-039` (*Subsystem Resource Hygiene*). Targets `ClockDevice.java` and `ClockDeviceResilienceTest.kt`.
* `Historical Origin & Commit Trace`: Initial commit `be30d9a2` (2019) introduced `Calendar.getInstance().getTime()` in `ClockDevice.java` as standard boilerplate without allocation profiling; `ATT-3981` (`REQ-TRK-027`) added top-level `try/catch` exception barriers; `ATT-4032` (`REQ-TRK-039`) added rate-limited error telemetry.
* `Root Reason for Existing Formulation`: `Calendar.getInstance()` was standard Java idiom for time queries; individual allocations were negligible in one-shot use cases but became a major churn hotspot when invoked at 1 Hz indefinitely.
* `Preservation of Core Invariants`: Time-of-day formatting format (`SimpleDateFormat.getTimeInstance()`), timezone alignment, 1-Hz broadcast cadence (`NEW_TIME_EVENT_INTENT`), active/total/lap sensor increments, and 100% clean-room test pass rate remain strictly preserved.

---

## 2. Test Specification (`TST-TRK-052`)

### Test Case 1: Time-of-Day Formatting Parity (`TST-TRK-052.1`)
* **Scope**: Unit test in `ClockDeviceResilienceTest.kt`.
* **Procedure**: Execute `ClockTimeTask.run()` and assert `mTimeOfDaySensor` value matches `SimpleDateFormat.getTimeInstance().format(...)`.

### Test Case 2: Zero-Allocation Source Contract Audit (`TST-TRK-052.2`)
* **Scope**: Contract audit in `ClockDeviceResilienceTest.kt`.
* **Procedure**: Inspect `ClockDevice.java` source asserting `mReusableDate.setTime(...)` is used and `Calendar.getInstance()` is absent from `ClockTimeTask`.

### Test Case 3: Clean-Room Governance & Regression (`TST-TRK-052.3`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ClockDeviceResilienceTest"`
* **Expected Result**: Exit code 0, 100% test pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-052.1` | Pre-allocated Date instance in ClockDevice | `TST-TRK-052.2` | Source Contract Test |
| `REQ-TRK-052.2` | Zero-allocation `setTime()` in ClockTimeTask | `TST-TRK-052.2` | Source Contract Test |
| `REQ-TRK-052.3` | Exact time string parity & thread confinement | `TST-TRK-052.1` | Automated Unit Test |
| `REQ-TRK-052` | System Invariants & Zero Regression | `TST-TRK-052.3` | Clean-Room Suite |
