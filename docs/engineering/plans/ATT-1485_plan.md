# Implementation Plan - ATT-1485: BLE Measurement Packet Parsing Resilience

**Ticket**: [ATT-1485](https://rainerblind.atlassian.net/browse/ATT-1485)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashes*)  
**Sub-task**: [ATT-1490](https://rainerblind.atlassian.net/browse/ATT-1490) (Stage 3 Impl-Plan)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-CON-014` (BLE GATT Measurement Packet Parsing Resilience & Null-Safe Telemetry Extraction)  
**Test ID**: `TST-CON-005`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1485_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1485_test_spec.md`  
**Branch**: `feature/ATT-1485`  

---

## 1. Executive Summary & Defect Overview

Production crash ticket **ATT-1485** was triggered in `BTLEBikePowerDevice.measurementCharacteristicUpdate(BTLEBikePowerDevice.java:386)` on the Main Looper:
```text
java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.Integer.intValue()' on a null object reference
    at com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikePowerDevice.measurementCharacteristicUpdate(BTLEBikePowerDevice.java:386)
```
The crash occurred when a connected BLE power meter emitted a Cycling Power measurement characteristic notification with truncated payload bytes. Because `BluetoothGattCharacteristic.getIntValue(...)` returns `null` when requested bytes exceed the byte buffer, Java's implicit auto-unboxing of boxed `Integer` to primitive `long` threw an uncaught `NullPointerException`.

### Remediation Objective
Eliminate all unboxed null dereferences and enforce multi-layered defensive parsing across all BLE measurement parsers:
1. `BTLEBikePowerDevice.java` (Direct crash site)
2. `BTLEBikeDevice.java` (Identical vulnerability pattern)
3. `BTLERunSpeedDevice.java` (Stride and distance unboxing)
4. `BTLEHeartRateDevice.java` (Heart rate unboxing)

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: Defensive Parsing in `BTLEBikePowerDevice.java`
File: [BTLEBikePowerDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEBikePowerDevice.java)

1. **Header Validation**:
   - Check `characteristic == null || characteristic.getValue() == null`.
   - Verify `characteristic.getValue().length >= FLAGS_WIDTH + INSTANTANEOUS_POWER_WIDTH` (minimum 4 bytes: 2 bytes flags + 2 bytes instantaneous power).
   - If length < 4, log an advisory warning and return immediately.
2. **Flags & Power Null-Safety**:
   - Read `flags` via `characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)`. Verify `flags != null`.
   - Read `power` via `characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset)`. Verify `power != null`.
   - Advance `offset += INSTANTANEOUS_POWER_WIDTH`.
3. **Conditional Pedal Power Balance**:
   - Check `isPowerBalancePresent(flags)`.
   - Verify `value.length >= offset + PEDAL_POWER_BALANCE_WIDTH` (offset + 1). If false, log warning and stop further conditional parsing.
   - Read `value` via `getIntValue(FORMAT_UINT8, offset)` with `Integer != null` guard.
   - Advance `offset += PEDAL_POWER_BALANCE_WIDTH`.
4. **Conditional Accumulated Torque**:
   - Check `isAccumulatedTorquePresent(flags)`.
   - Verify `value.length >= offset + ACCUMULATED_TORQUE_WIDTH` (offset + 2). If false, log warning and stop.
   - Advance `offset += ACCUMULATED_TORQUE_WIDTH`.
5. **Conditional Wheel Revolution Data**:
   - Check `isWheelRevolutionDataPresent(flags)`.
   - Verify `value.length >= offset + WHEEL_REVOLUTION_DATA__CUMULATIVE_WHEEL_REVOLUTIONS_WIDTH + WHEEL_REVOLUTION_DATA__LAST_WHEEL_EVENT_TIME_WIDTH` (offset + 6). If false, log warning and break.
   - If supported (`mIsWheelRevolutionDataSupported`), read `cumulativeWheelRevolutions` (UINT32) and `wheelEventTime` (UINT16) with explicit `Integer != null` checks.
   - Advance `offset += 6`.
6. **Conditional Crank Revolution Data (Crash Site)**:
   - Check `isCrankRevolutionDataPresent(flags)`.
   - Verify `value.length >= offset + CRANK_REVOLUTION_DATA__CUMULATIVE_CRANK_REVOLUTIONS_WIDTH + CRANK_REVOLUTION_DATA__LAST_CRANK_EVENT_TIME_WIDTH` (offset + 4). If false, log warning and break.
   - If supported (`mIsCrankRevolutionDataSupported`), read `cumulativeCrankRevolutions` (UINT16) and `crankEventTime` (UINT16) with explicit `Integer != null` checks.
   - Advance `offset += 4`.
7. **Main Looper Exception Isolation**:
   - Wrap the method body in a `try-catch (Exception e)` block that logs `Log.w(TAG, "Malformed characteristic packet discarded", e)` to ensure unexpected vendor quirks never crash the process.

---

### Phase 2: Defensive Parsing in `BTLEBikeDevice.java`
File: [BTLEBikeDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEBikeDevice.java)

1. **Header Validation**:
   - Check `characteristic == null || characteristic.getValue() == null || characteristic.getValue().length < 1`.
2. **Wheel Revolution Data**:
   - If `wheelRevolutionDataPresent`, check `value.length >= 1 + 4 + 2 = 7`.
   - Extract `cumulativeWheelRevolutions` and `wheelEventTime` with length and null checks.
3. **Crank Revolution Data**:
   - If `crankRevolutionDataPresent`, check `value.length >= crankDataOffset + 2 + 2 = crankDataOffset + 4`.
   - Extract `cumulativeCrankRevolutions` and `crankEventTime` with length and null checks.
4. **Exception Guard**:
   - Enclose in top-level `try-catch` isolation.

---

### Phase 3: Defensive Parsing in `BTLERunSpeedDevice.java`
File: [BTLERunSpeedDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERunSpeedDevice.java)

1. **Header Validation**:
   - Require `characteristic != null && characteristic.getValue() != null && characteristic.getValue().length >= 4` (1 flag + 2 speed + 1 cadence).
2. **Conditional Stride & Distance Checks**:
   - If `stride_length_present`, check `value.length >= 6` before reading stride at offset 4.
   - If `mDistancePresent`, check `value.length >= distance_offset + 4` before reading distance at `distance_offset`.
3. **Exception Guard**:
   - Enclose in top-level `try-catch` isolation.

---

### Phase 4: Defensive Parsing in `BTLEHeartRateDevice.java`
File: [BTLEHeartRateDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEHeartRateDevice.java)

1. **Header & Length Validation**:
   - Check `characteristic != null && characteristic.getValue() != null && characteristic.getValue().length >= 2`.
   - If `(flag & 0x01) != 0` (UINT16 HR), verify `characteristic.getValue().length >= 3`.
2. **Null-Safe Extraction**:
   - Extract `heartRate` using boxed `Integer` and null guard before dispatching to `mHeartRateSensor.newValue(heartRate)`.
3. **Exception Guard**:
   - Enclose in top-level `try-catch` isolation.

---

### Phase 5: Automated Unit Test Suite `BTLEMeasurementParsingTest.kt`
File: [BTLEMeasurementParsingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEMeasurementParsingTest.kt)

Implement comprehensive unit tests verifying all 9 cases from `ATT-1485_test_spec.md`:
1. `testBikePower_truncatedCrankData_doesNotCrashAndDiscardsGracefully` (reproducing exact production crash: flags `0x0020`, power 200W, truncated 2-byte crank data missing timestamp).
2. `testBikePower_truncatedWheelData_doesNotCrash` (flags `0x0010`, only 4 bytes total).
3. `testBikePower_truncatedPowerBalance_doesNotCrash` (flags `0x0001`, only 4 bytes total).
4. `testBikePower_emptyOrTruncatedHeader_doesNotCrash` (null, 0, 1, 2, 3 bytes).
5. `testBikePower_validFullPacket_parsesAllSensorsCorrectly` (valid packet: power, balance, wheel, crank).
6. `testBikeDevice_truncatedPackets_doesNotCrash` (`BTLEBikeDevice` with truncated wheel/crank bytes).
7. `testRunSpeedDevice_truncatedPackets_doesNotCrash` (`BTLERunSpeedDevice` with truncated stride/distance bytes).
8. `testHeartRateDevice_truncatedPackets_doesNotCrash` (`BTLEHeartRateDevice` with 0 bytes or truncated UINT16).
9. `testFullRegression_cleanExecution` (`./gradlew testDebugUnitTest` clean execution).

---

## 3. Invariants & Chesterton's Fence Audit

1. **Sensor Math Invariance**: The mathematical conversion formulas for speed, cadence, distance, power balance, and pace remain strictly identical.
2. **Rollover & Event Time Invariance**: Logic tracking `mLastWheelRevolutionsValid`, `mIdenticalWheelTime`, `mLastCrankRevolutionsValid`, and `mIdenticalCrankTime` is untouched and continues to operate seamlessly on valid packets.
3. **GATT Threading & Lifecycle Invariance (`REQ-CON-012`)**: Asynchronous read queues, mutex locking under `mGattLock`, and battery checks remain unchanged.
4. **Zero-Lap & Session Invariance (`REQ-TRK-002`)**: Lap lifecycle and session recording are completely decoupled and preserved.

---

## 4. Verification & Validation Steps

1. **Execute Unit Tests**:
   - `./gradlew testDebugUnitTest --tests com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEMeasurementParsingTest`
   - `./gradlew testDebugUnitTest` (full suite regression).
2. **Review Gate 4 & Gate 5 Audits**:
   - `python3 tools/review_agent.py audit <subtask>`
3. **Device Health Verification**:
   - Confirm Google Pixel 10 is connected, healthy, and in Light Mode (`Night mode: no`).
