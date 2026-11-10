# Stage 3 Implementation Plan: ATT-6046 - BANALService (ANT+): PCC-Handle-Leak in ANTBikeSpeedAndCadenceDevice & Pedal-Smoothness-Bug in ANTBikePowerDevice (Teilpaket A)

**Ticket**: [ATT-6046](https://rainerblind.atlassian.net/browse/ATT-6046)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-CON-025` (`025.1`–`025.3`)  
**Test Spec Mapping**: `TST-CON-025` (`025.1`–`025.5`)  
**Branch**: `feature/ATT-6046`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Boundaries**:
  - `ANTBikeSpeedAndCadenceDevice.java`: Refactor `releaseAccess()` to close `pccReleaseHandle2` in a `try-catch-finally` block (nulling the reference in `finally`) and unconditionally invoke `super.releaseAccess()`, preventing primary `pccReleaseHandle` leaks when cadence is absent or disconnected. Expose package-private test hooks (`getPccReleaseHandle2ForTesting()`, `setPccReleaseHandle2ForTesting(...)`).
  - `ANTBikePowerDevice.java`: Fix inverted sensor guard on line 360 (`if (mPedalSmoothnessSensor != null)` instead of `mPedalSmoothnessLeftSensor != null`) when `separatePedalSmoothnessSupport == false`, restoring combined pedal smoothness event delivery. Remove shadowed `final DevicesDatabaseManager mDevicesDatabaseManager` declaration (line 75) and constructor assignment (line 85), relying on inherited `MyDevice.mDevicesDatabaseManager`.
* **Sensor Concurrency & Invariants**: PCC release lifecycle adheres to `REQ-CON-020.4` (`DEVICE_ALREADY_IN_USE` prevention); sensor listener dispatch runs without lock contention or memory leaks.

---

## 2. Atomic Implementation Steps

| Step | Component | Target File | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Speed & Cadence | `ANTBikeSpeedAndCadenceDevice.java` | In `releaseAccess()`, close and null `pccReleaseHandle2` in try/finally, then unconditionally call `super.releaseAccess()`. Add test hooks. | `REQ-CON-025.1`<br>`TST-CON-025.1-2` |
| **2** | Bike Power | `ANTBikePowerDevice.java` | Correct line 360 to `if (mPedalSmoothnessSensor != null)`. Remove shadowed `mDevicesDatabaseManager` field and assignment. | `REQ-CON-025.2-3`<br>`TST-CON-025.3-4` |
| **3** | Unit Tests | `ANTBikeSpeedAndCadenceDeviceTest.kt` | Author unit test suite verifying unconditional primary release with null/active `pccReleaseHandle2` and exception safety. | `REQ-CON-025.1`<br>`TST-CON-025.1-2` |
| **4** | Unit Tests | `ANTBikePowerDeviceTest.kt` | Add test cases for combined pedal smoothness dispatch and reflection assertion on removed shadow field. | `REQ-CON-025.2-3`<br>`TST-CON-025.3-4` |
| **5** | Verification | Test Suite | Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*"` and requirement governance. | `REQ-PRO-001`<br>`TST-CON-025.5` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source code modified until Gate 3 is marked `Erledigt` and `check-gate` exits 0.
* **Scope Boundary**: Strictly limited to Teilpaket A; Teilpaket B (`ATT-6381`) and C (`ATT-6382`) remain completely untouched.
* **Risk Rating**: **LOW** (Targeted fixes to device lifecycle methods and boolean guards; zero schema or cross-service changes).
