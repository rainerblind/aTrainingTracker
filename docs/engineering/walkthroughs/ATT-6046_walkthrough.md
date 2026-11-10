# Stage 5: Walkthrough & Verification - ATT-6046: BANALService Driver Leak & Telemetry Fixes (Teilpaket A)

**Ticket**: [ATT-6046](https://rainerblind.atlassian.net/browse/ATT-6046)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6046`  
**Requirement Mapping**: `REQ-CON-025` | **Test Spec ID**: `TST-CON-025`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Scope
Teilpaket A resolves critical connection leak and telemetry routing defects in BANALService ANT+ device drivers:
1. **PCC Handle Leak Elimination**: In `ANTBikeSpeedAndCadenceDevice.java`, `releaseAccess()` guarded `super.releaseAccess()` inside `if (pccReleaseHandle2 != null)`. If cadence search failed or cadence disconnected first, the primary speed handle leaked. `releaseAccess()` now closes `pccReleaseHandle2` in a try-finally block, nulls it, and unconditionally invokes `super.releaseAccess()`.
2. **Pedal Smoothness Routing Safety**: In `ANTBikePowerDevice.java:357`, inverted guard `if (mPedalSmoothnessLeftSensor != null)` blocked combined pedal smoothness dispatch. Corrected guard to `if (mPedalSmoothnessSensor != null)`, restoring combined pedal smoothness telemetry.
3. **Inherited Field Shadowing Removal**: Removed duplicate `mDevicesDatabaseManager` in `ANTBikePowerDevice.java:75`, routing all database operations cleanly through inherited `MyDevice.mDevicesDatabaseManager`.

---

## 2. Automated Test Execution & Scope (Rule 60)
* **Tier 1 Targeted Test Scope**:
  * Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * Scope: All BANALService ANT+ and Bluetooth LE device drivers, reconnect managers, sensor managers, and build/ASPICE governance contract tests.
  * Result: **100% PASS** (32 tasks, 0 failures under `parallel_sprint_lock.py`).
* **Living Documentation Governance**:
  * Shards: `docs/requirements/req_tracking_cockpit.md` (`REQ-CON-025`) and `docs/tests/tst_tracking_cockpit.md` (`TST-CON-025`) updated to `[Verified]`.

---

## 3. Acceptance Criteria Verification Table

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1 (Unconditional Primary Handle Release)**: Speed handle closes when `pccReleaseHandle2` is null | Unit Test | **MET** | `ANTBikeSpeedAndCadenceDeviceTest.kt:testReleaseAccess_whenSecondaryHandleNull_unconditionallyReleasesPrimaryHandle` |
| **AC-2 (Safe Dual Handle Release & Finally Resilience)**: Both handles close cleanly; primary handle closes even if secondary throws | Unit Test | **MET** | `ANTBikeSpeedAndCadenceDeviceTest.kt:testReleaseAccess_whenBothHandlesActive_releasesBothSafely`, `testReleaseAccess_whenSecondaryHandleThrows_primaryHandleStillCleanedUp` |
| **AC-3 (Combined Pedal Smoothness Routing)**: Combined pedal smoothness dispatches to `mPedalSmoothnessSensor` when left sensor is null | Unit Test | **MET** | `ANTBikePowerDeviceTest.kt:testANTBikePower_combinedPedalSmoothness_routesToCombinedSensor` |
| **AC-4 (Field Shadowing Elimination)**: `ANTBikePowerDevice` declares no shadow field for `mDevicesDatabaseManager` | Reflection Test | **MET** | `ANTBikePowerDeviceTest.kt:testANTBikePower_noFieldShadowingForDevicesDatabaseManager` |

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Status**: `adb devices` reports no physical device attached. Emulators prohibited per Rule 79 (`ATT-5890`). All driver lifecycle invariants and telemetry dispatch routing verified via targeted unit tests with MockK.
