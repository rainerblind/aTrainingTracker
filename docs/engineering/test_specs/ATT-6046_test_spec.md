# Stage 2: Requirement & Test Specification - ATT-6046: BANALService ANT+ PCC Leak & Pedal Smoothness Fix (Teilpaket A)

**Ticket**: [ATT-6046](https://rainerblind.atlassian.net/browse/ATT-6046)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6046`  
**Requirement Mapping**: `REQ-CON-025` (*ANT+ Device Driver Resource Cleanup & Telemetry Routing Safety*)  
**Test Spec ID**: `TST-CON-025`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-CON-025)

### 1.1 Problem Statement & Rationale
Under the approved complexity split (REQ-PRO-029), this ticket addresses **Teilpaket A** (2 SP):
1. In `ANTBikeSpeedAndCadenceDevice.java`, `releaseAccess()` guarded `super.releaseAccess()` inside `if (pccReleaseHandle2 != null)`. If cadence search is cancelled or fails, the primary speed handle (`pccReleaseHandle`) leaks, causing subsequent reconnects to fail with `DEVICE_ALREADY_IN_USE` (`REQ-CON-020.4`).
2. In `ANTBikePowerDevice.java:360`, `onNewPedalSmoothness()` incorrectly checked `if (mPedalSmoothnessLeftSensor != null)` before dispatching combined pedal smoothness to `mPedalSmoothnessSensor.newValue(...)`, completely dropping combined telemetry.
3. In `ANTBikePowerDevice.java:75`, `mDevicesDatabaseManager` shadowed the inherited field from `MyDevice.java:55`.

### 1.2 Functional & Architectural Requirements
* **REQ-CON-025.1** (*Unconditional Primary & Secondary PCC Handle Release*): In `ANTBikeSpeedAndCadenceDevice.releaseAccess()`, if `pccReleaseHandle2 != null`, it SHALL be closed in a try-finally block and nulled. `super.releaseAccess()` MUST be invoked unconditionally, ensuring primary speed release handle cleanup.
* **REQ-CON-025.2** (*Combined Pedal Smoothness Routing Safety*): In `ANTBikePowerDevice.onNewPedalSmoothness()`, when `separatePedalSmoothnessSupport == false`, the system SHALL check `if (mPedalSmoothnessSensor != null)` before dispatching `newValue(...)`, restoring combined pedal smoothness telemetry.
* **REQ-CON-025.3** (*Inherited Field Shadowing Elimination*): Redundant declaration of `mDevicesDatabaseManager` in `ANTBikePowerDevice.java:75` SHALL be removed, routing all database manager access to `MyDevice.mDevicesDatabaseManager`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (PCC Handle Cleanup)**:
  - *Given* `ANTBikeSpeedAndCadenceDevice` with null or failed `pccReleaseHandle2`,
  - *When* `releaseAccess()` is called,
  - *Then* `super.releaseAccess()` executes unconditionally, closing and clearing primary `pccReleaseHandle`.
* **Criterion 2 (Combined Pedal Smoothness)**:
  - *Given* an `ANTBikePowerDevice` with combined pedal smoothness (`separatePedalSmoothnessSupport == false`),
  - *When* `onNewPedalSmoothness()` receives a valid value,
  - *Then* `mPedalSmoothnessSensor.newValue(...)` is invoked and published to subscribers.
* **Criterion 3 (Field Shadowing)**:
  - *Given* `ANTBikePowerDevice`,
  - *When* inspected via reflection,
  - *Then* it does not declare a shadow field for `mDevicesDatabaseManager`.

---

## 2. Test Specification (TST-CON-025)

### Test Case 1: Speed/Cadence Disconnect With Null Secondary Handle (`TST-CON-025.1`)
* **Scope**: Unit Test (`ANTBikeSpeedAndCadenceDeviceTest.kt`)
* **Assertion**: Verify `super.releaseAccess()` executes and clears primary handle when `pccReleaseHandle2 == null`.

### Test Case 2: Speed/Cadence Disconnect With Active Secondary Handle (`TST-CON-025.2`)
* **Scope**: Unit Test (`ANTBikeSpeedAndCadenceDeviceTest.kt`)
* **Assertion**: Verify both `pccReleaseHandle2` and primary handle are closed and nulled safely in try-finally.

### Test Case 3: Combined Pedal Smoothness Telemetry Routing (`TST-CON-025.3`)
* **Scope**: Unit Test (`ANTBikePowerDeviceTest.kt`)
* **Assertion**: Verify `mPedalSmoothnessSensor.newValue(...)` receives value when `mPedalSmoothnessLeftSensor == null`.

### Test Case 4: Field Shadowing Elimination (`TST-CON-025.4`)
* **Scope**: Contract / Reflection Test (`ANTBikePowerDeviceTest.kt`)
* **Assertion**: Verify `ANTBikePowerDevice` does not declare a duplicate `mDevicesDatabaseManager` field.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-CON-025.5`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-025.1` | Unit | `ANTBikeSpeedAndCadenceDeviceTest.kt` | `REQ-CON-025.1` | Planned |
| `TST-CON-025.2` | Unit | `ANTBikeSpeedAndCadenceDeviceTest.kt` | `REQ-CON-025.1` | Planned |
| `TST-CON-025.3` | Unit | `ANTBikePowerDeviceTest.kt` | `REQ-CON-025.2` | Planned |
| `TST-CON-025.4` | Contract | `ANTBikePowerDeviceTest.kt` | `REQ-CON-025.3` | Planned |
| `TST-CON-025.5` | Regression | `banalservice.devices.ant_plus.*` | `REQ-PRO-001` | Planned |
