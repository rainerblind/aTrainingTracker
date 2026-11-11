# Stage 3: Implementation Plan - ATT-6713: Convert ANT+ estTimestamp to UTC epoch ms, re-register mRrIntervalSensor on reconnect, and refine BLE bundled RR drift guard

**Ticket**: [ATT-6713](https://rainerblind.atlassian.net/browse/ATT-6713)  
**Sub-task**: [ATT-6787](https://rainerblind.atlassian.net/browse/ATT-6787) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-069`  
**Test Mapping**: `TST-TRK-069`  
**Branch**: `feature/ATT-6713`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
Hardware beat-to-beat acquisition under `ATT-3540` and `ATT-6742` exhibits three critical defects: (1) ANT+ `estTimestamp` uses boot-relative `elapsedRealtime`, evicting all beats from `HRVMetricsDevice.pruneOldBeats()` and saving 1970 timestamps in `WorkoutRrIntervals`; (2) `unregisterSensors()` fails to null `mRrIntervalSensor`, breaking RR re-registration upon reconnect; and (3) BLE bundled packets forward-shift initial beats and accumulate integer rounding error over time, causing false `ARTIFACT_GAP` triggers after 30–40 min.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-069` (*Hardware RR Acquisition Lifecycle & Clock Domain Integrity*)
  * `REQ-TRK-069.1`: Convert ANT+ `estTimestamp` to UTC Unix epoch ms (`epochOffset + estTimestamp`).
  * `REQ-TRK-069.2`: Null `mRrIntervalSensor` in `unregisterSensors()` in `ANTHeartRateDevice` and `BTLEHeartRateDevice`.
  * `REQ-TRK-069.3`: BLE bundled backward anchoring on initial/re-anchor packets and double-precision continuous tracking.
  * `REQ-TRK-069.4`: End-to-end integration test wiring `ANTHeartRateDevice` to `HRVMetricsDevice` ensuring beats survive `pruneOldBeats()`.
* **Test Mapping**: `TST-TRK-069` (`TST-TRK-069.1` to `TST-TRK-069.5`).

---

## 3. System Invariants & Preserved Behavior
1. **Continuity & Lossless Storage**: Maintain strict ANT+ `deltaC == 1` continuity guard and `WorkoutRrIntervals` persistence.
2. **Optical HR Backward Compatibility**: Preserve 1-Hz HR calculation and null RR sensor behavior when flag `0x10` is absent.
3. **Thread Safety**: Sensor callbacks remain isolated to driver handler threads and synchronized `onRrBeat` listeners.
4. **Zero Database Migrations**: No changes to SQLite schema or database versions.

---

## 4. Proposed Architectural Changes
* **ANT+ Driver (`ANTHeartRateDevice.java`)**:
  * Implement UTC epoch conversion: `epochOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()`.
  * Reset `mPrevHeartBeatCount`, `mPrevHeartBeatEventTime`, and `mLastBeatTimestampMs` on `DataState.ZERO_DETECTED` or packet gaps $> 64\text{ s}$.
  * Reset `mRrIntervalSensor = null` in `unregisterSensors()` and `shutDown()`.
* **BLE Driver (`BTLEHeartRateDevice.java`)**:
  * Unpack bundled intervals before assigning timestamps; on initial/re-anchored packet, anchor newest beat to `packetArrivalMs` and back-calculate prior beats.
  * Track cumulative timestamp as `double mLastBeatTimestampMs` to eliminate $\approx 0.5\text{ ms/beat}$ integer quantization drift.
  * Reset `mRrIntervalSensor = null` in `unregisterSensors()` and `shutDown()`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`ANTHeartRateDevice.java`)**: Implement `convertEstTimestampToUtcEpochMs()` and update `parseHeartRateData()`; reset trackers on `ZERO_DETECTED`; null `mRrIntervalSensor` on unregister.
2. **Step 2 (`BTLEHeartRateDevice.java`)**: Implement bundled backward anchoring, sub-ms `double` timestamp accumulation, and null `mRrIntervalSensor` on unregister.
3. **Step 3 (Driver Unit Tests)**: Update `ANTHeartRateDeviceTest.kt` and `BTLEHeartRateDeviceTest.kt` covering UTC epoch timestamps, reconnect re-registration, backward anchoring, and 3600-beat drift bounding.
4. **Step 4 (End-to-End Integration Test)**: Create `ANTHeartRateDeviceHrvIntegrationTest.kt` verifying `ANTHeartRateDevice` $\to$ `HRVMetricsDevice` continuous telemetry calculation.
5. **Step 5 (Verification Execution)**:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.hrv.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and end-to-end integration tests; validation via `verify_requirement_governance.py`.
* **Rollback**: Clean git revert of feature branch commits without impacting main branch or database schema.
