# Test Specification - ATT-1485: BLE GATT Measurement Packet Parsing Resilience

**Ticket**: [ATT-1485](https://rainerblind.atlassian.net/browse/ATT-1485)  
**Sub-task**: [ATT-1489](https://rainerblind.atlassian.net/browse/ATT-1489) (Stage 2 Test-Spec)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashes*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.3`  
**Components**:
* `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEBikePowerDevice.java`
* `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEBikeDevice.java`
* `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERunSpeedDevice.java`
* `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEHeartRateDevice.java`
* `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEMeasurementParsingTest.kt`
**Requirement**: `REQ-CON-014` (BLE GATT Measurement Packet Parsing Resilience & Null-Safe Telemetry Extraction)  
**Test Spec ID**: `TST-CON-005`  
**Branch**: `feature/ATT-1485`  

---

## 1. Overview & Verification Strategy

This test specification defines the verification strategy to ensure that all Bluetooth Low Energy (BLE) measurement characteristic parsers safely handle truncated, malformed, or hostile GATT notification packets without throwing `NullPointerException` or triggering application crashes on the UI Looper (`ATT-1485`).

The test suite validates:
1. **Truncated Crank Revolution Data Resilience (Production Crash Reproduction)**: Verifying that when a Cycling Power packet declares Crank Revolution Data present in its flags but provides fewer than the mandatory 4 bytes of crank payload, `BTLEBikePowerDevice` parses preceding fields (power), cleanly suppresses crank processing without throwing `NullPointerException`, and logs an advisory warning.
2. **Truncated Wheel Revolution Data Resilience**: Verifying that when Wheel Revolution Data is flagged present but the payload terminates before the 6-byte wheel field is complete, `BTLEBikePowerDevice` avoids out-of-bounds reads and does not crash.
3. **Truncated Pedal Power Balance & Torque Resilience**: Verifying that short packets missing declared power balance or accumulated torque bytes are safely handled.
4. **Header Bounds Verification**: Verifying that empty, 1-byte, 2-byte, or 3-byte packets (less than the 4-byte mandatory header: 2-byte flags + 2-byte instantaneous power) are safely discarded immediately.
5. **Full Conforming Packet Fidelity**: Verifying that valid, full-length Cycling Power packets parse power, balance, wheel revolutions/speed, and crank revolutions/cadence with 100% mathematical fidelity.
6. **Cross-Device BLE Parser Parity**: Verifying that sister BLE peripheral parsers (`BTLEBikeDevice`, `BTLERunSpeedDevice`, and `BTLEHeartRateDevice`) also reject truncated packets without throwing unhandled exceptions.
7. **Full Test Suite Regression**: Verifying that `./gradlew testDebugUnitTest` runs cleanly with zero failures.

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testBikePower_truncatedCrankData_doesNotCrashAndDiscardsGracefully` (Unit Test - `TST-CON-005.1`)
* **Goal**: Recreate the exact production crash from ATT-1485 where `getIntValue()` returned null on line 386 due to missing crank timestamp bytes, and verify complete immunity.
* **Preconditions**:
  * `BTLEBikePowerDevice` instance initialized with power and cadence sensors enabled.
  * Construct a characteristic byte array:
    * Flags: `0x0020` (Crank Revolution Data Present).
    * Power: `200` W (bytes: `0xC8, 0x00`).
    * Truncated crank data: only 2 bytes for cumulative crank revolutions (`0x0A, 0x00`), missing the 2 bytes of `crankEventTime`.
    * Total packet length: 6 bytes (should be 8 bytes).
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)`.
* **Expected Result**:
  * Zero exceptions thrown (`NullPointerException` completely eliminated).
  * `mPowerSensor.getValue()` equals `200`.
  * Cadence sensor is not updated with corrupted data.
  * Advisory warning logged.

### Test Case 2: `testBikePower_truncatedWheelData_doesNotCrash` (Unit Test - `TST-CON-005.2`)
* **Goal**: Verify that truncated wheel revolution data (which requires 6 bytes: 4 bytes revolutions + 2 bytes timestamp) does not crash `BTLEBikePowerDevice`.
* **Preconditions**:
  * `BTLEBikePowerDevice` initialized with speed and distance sensors enabled.
  * Construct characteristic bytes:
    * Flags: `0x0010` (Wheel Revolution Data Present).
    * Power: `150` W.
    * Only 3 bytes of wheel data provided (instead of 6).
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)`.
* **Expected Result**:
  * Zero exceptions thrown.
  * Power parsed cleanly (`150`).
  * Wheel data parsing safely aborted.

### Test Case 3: `testBikePower_truncatedPowerBalance_doesNotCrash` (Unit Test - `TST-CON-005.3`)
* **Goal**: Verify that flagged power balance with truncated payload does not crash.
* **Preconditions**:
  * Flags: `0x0001` (Power Balance Present).
  * Power: `220` W.
  * Total length: 4 bytes (missing the 1-byte balance value).
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)`.
* **Expected Result**:
  * Zero exceptions thrown.
  * Power parsed cleanly (`220`).
  * Power balance safely skipped.

### Test Case 4: `testBikePower_emptyOrTruncatedHeader_doesNotCrash` (Unit Test - `TST-CON-005.4`)
* **Goal**: Verify that notifications with payload length < 4 bytes (or null value) are immediately and cleanly discarded.
* **Preconditions**:
  * Byte arrays of length 0, 1, 2, and 3, plus null `getValue()`.
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)` for each payload.
* **Expected Result**:
  * Zero exceptions thrown for all cases.
  * No sensor updates emitted.

### Test Case 5: `testBikePower_validFullPacket_parsesAllSensorsCorrectly` (Unit Test - `TST-CON-005.5`)
* **Goal**: Verify that valid, well-formed packets continue to parse and dispatch telemetry accurately.
* **Preconditions**:
  * Valid packet with Flags: `0x0031` (Power Balance + Wheel Data + Crank Data).
  * Power: `250` W.
  * Balance: `100` (50% left balance).
  * Wheel Data: 1000 revs, event time 2048.
  * Crank Data: 500 revs, event time 1024.
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)` twice with advancing revolutions and timestamps.
* **Expected Result**:
  * Zero exceptions.
  * Power sensor receives `250`.
  * Power balance sensor receives `50.0`.
  * Speed, distance, and cadence sensors receive accurately computed positive metrics.

### Test Case 6: `testBikeDevice_truncatedPackets_doesNotCrash` (Unit Test - `TST-CON-005.6`)
* **Goal**: Verify that `BTLEBikeDevice` safely discards truncated wheel and crank revolution data.
* **Preconditions**:
  * Byte payload with wheel/crank flags set, but total length truncated to 3 bytes.
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)`.
* **Expected Result**:
  * Zero exceptions thrown.

### Test Case 7: `testRunSpeedDevice_truncatedPackets_doesNotCrash` (Unit Test - `TST-CON-005.7`)
* **Goal**: Verify that `BTLERunSpeedDevice` safely handles packets shorter than the flagged stride length or distance offsets.
* **Preconditions**:
  * Flag indicating distance present (`0x02`), but payload truncated to 4 bytes (offset 4 requires 4 additional bytes).
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)`.
* **Expected Result**:
  * Zero exceptions thrown.

### Test Case 8: `testHeartRateDevice_truncatedPackets_doesNotCrash` (Unit Test - `TST-CON-005.8`)
* **Goal**: Verify that `BTLEHeartRateDevice` safely handles 0-byte packets or UINT16 HR flag with only 1 byte of value.
* **Preconditions**:
  * Empty byte array and 1-byte array with flag `0x01` (UINT16).
* **Action**:
  * Invoke `measurementCharacteristicUpdate(mockGatt, mockCharacteristic)`.
* **Expected Result**:
  * Zero exceptions thrown.

### Test Case 9: `testFullRegression_cleanExecution` (Unit Test - `TST-CON-005.9`)
* **Goal**: Guarantee zero regressions across all other system modules.
* **Action**:
  * Execute `./gradlew testDebugUnitTest`.
* **Expected Result**:
  * All unit test suites pass with 0 failures.

---

## 3. Traceability Matrix

| Requirement | Test Spec ID | Test Method Name | Verification Target |
|:---|:---|:---|:---|
| `REQ-CON-014` | `TST-CON-005.1` | `testBikePower_truncatedCrankData_doesNotCrashAndDiscardsGracefully` | Production crash site in `BTLEBikePowerDevice.java:386` |
| `REQ-CON-014` | `TST-CON-005.2` | `testBikePower_truncatedWheelData_doesNotCrash` | Wheel data bounds checking |
| `REQ-CON-014` | `TST-CON-005.3` | `testBikePower_truncatedPowerBalance_doesNotCrash` | Power balance bounds checking |
| `REQ-CON-014` | `TST-CON-005.4` | `testBikePower_emptyOrTruncatedHeader_doesNotCrash` | Mandatory header length checking |
| `REQ-CON-014` | `TST-CON-005.5` | `testBikePower_validFullPacket_parsesAllSensorsCorrectly` | Specification compliance & calculation fidelity |
| `REQ-CON-014` | `TST-CON-005.6` | `testBikeDevice_truncatedPackets_doesNotCrash` | `BTLEBikeDevice` parity |
| `REQ-CON-014` | `TST-CON-005.7` | `testRunSpeedDevice_truncatedPackets_doesNotCrash` | `BTLERunSpeedDevice` parity |
| `REQ-CON-014` | `TST-CON-005.8` | `testHeartRateDevice_truncatedPackets_doesNotCrash` | `BTLEHeartRateDevice` parity |
| `REQ-CON-014` | `TST-CON-005.9` | `testFullRegression_cleanExecution` | Full project regression safety |
