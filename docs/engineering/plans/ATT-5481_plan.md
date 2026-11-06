# Stage 3: Implementation Plan - ATT-5481: Eliminate 1-Hz Calendar Allocation

**Ticket**: [ATT-5481](https://rainerblind.atlassian.net/browse/ATT-5481)  
**Sub-task**: [ATT-5537](https://rainerblind.atlassian.net/browse/ATT-5537) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5481`  
**Requirement Mapping**: `REQ-TRK-052` | **Test Mapping**: `TST-TRK-052`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `ClockDevice$ClockTimeTask.run()`, `mTimeOfDaySensor.newValue(df.format(Calendar.getInstance().getTime()))` allocates fresh `GregorianCalendar` and `Date` instances on every 1-Hz tick ($\ge 43,200$ objects in a 4-hour workout). Mutating a pre-allocated `Date` via `setTime(System.currentTimeMillis())` eliminates all allocations while preserving identical output.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-052` (*Zero-Allocation 1-Hz ClockTimeTask Time-of-Day Formatting Architecture*)
* **Test Mapping**: `TST-TRK-052.1`..`TST-TRK-052.3` (`ClockDeviceResilienceTest.kt`)

---

## 3. System Invariants & Preserved Behavior
1. **Output Parity**: `mTimeOfDaySensor` value remains 100% identical in format, locale, and timezone.
2. **Thread Confinement**: `mReusableDate` is confined to the sequential `ClockDeviceTimer` thread (`Timer-0`).
3. **Sensor Pipeline**: Active/total/lap time increments and broadcast intents remain unchanged.
4. **Clean-Room Test Pass Rate**: Zero regressions in `ClockDeviceResilienceTest` and full suite.

---

## 4. Proposed Architectural Changes

### Component 1: `ClockDevice.java` (`app/src/main/java/com/atrainingtracker/banalservice/devices/`)
* Introduce `private final Date mReusableDate = new Date();`.
* In `ClockTimeTask.run()`, replace `df.format(Calendar.getInstance().getTime())` with `mReusableDate.setTime(System.currentTimeMillis())` passed to `df.format(mReusableDate)`.
* Replace `import java.util.Calendar;` with `import java.util.Date;`.

### Component 2: `ClockDeviceResilienceTest.kt` (`app/src/test/java/com/atrainingtracker/banalservice/devices/`)
* Add test `testClockTimeTask_formatsTimeOfDayWithoutCalendarAllocation` (`TST-TRK-052.1`).
* Add test `testClockDevice_sourceContractGuaranteesZeroAllocation` (`TST-TRK-052.2`).

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Allocated Date Holder & Zero-Allocation Mutation (`REQ-TRK-052.1`, `REQ-TRK-052.2`)
* File: `app/src/main/java/com/atrainingtracker/banalservice/devices/ClockDevice.java`
* Action: Add `private final Date mReusableDate = new Date();` and update `ClockTimeTask.run()` to use `mReusableDate.setTime(System.currentTimeMillis())`.

### Step 2: Automated Contract & Unit Tests (`TST-TRK-052.1`, `TST-TRK-052.2`)
* File: `app/src/test/java/com/atrainingtracker/banalservice/devices/ClockDeviceResilienceTest.kt`
* Action: Add formatting parity test and zero-allocation source contract audit test.

### Step 3: Targeted Verification & Governance (`TST-TRK-052.3`)
* Commands:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ClockDeviceResilienceTest"`
  `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification Command**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ClockDeviceResilienceTest" && python3 tools/verify_requirement_governance.py`
* **Rollback**: Atomic Git revert on `feature/ATT-5481`.
