# Stage 3: Implementation Plan - ATT-7578: Fix ANT+ Bike Radar slot-index track IDs and per-packet window reset

**Ticket**: [ATT-7578](https://rainerblind.atlassian.net/browse/ATT-7578)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-040` (`REQ-CON-040.1`..`REQ-CON-040.4`)  
**Test Mapping**: `TST-CON-040` (`TST-CON-040.1`..`TST-CON-040.4`)  
**Branch**: `feat/ATT-7578`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`, and `RadarWindowAggregator.kt`:
1. `targetId = baseId + i` in `ANTBikeRadarDevice.kt` uses slot indices as track IDs. Because ANT+ devices sort targets by range, when a leading vehicle overtakes and passes, trailing vehicles shift slots (e.g., slot 1 -> slot 0). `RadarWindowAggregator` interprets this as slot 1 disappearing, falsely incrementing vehicle pass counts, and later counts slot 0 again when the trailing car passes.
2. `sampleAndResetWindow()` is called on every packet (4–8 Hz) in both `ANTBikeRadarDevice.kt` and `BTLERadarDevice.java`. In `RadarWindowAggregator.kt`, `latchedPassingDistance` was immediately cleared to `null` on each invocation, destroying latched passing clearance before 1 Hz database poll ticks.
3. `threatLevelBits > 0 && rawRange > 0` drops targets in the 0-bin ($< 3.125\text{ m}$) at the moment of overtake, preventing close-pass lateral clearance ($\le 3.0\text{ m}$) from being captured.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-040` (*ANT+ Radar Track Identity Continuity, 0-Bin Target Ingestion & Rolling Window Latch Preservation*)
* **Test Mapping**: `TST-CON-040` (*ANT+ Bike Radar Track Identity Continuity, 0-Bin Ingestion & Rolling Window Latch Preservation Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Predefined Network Slot 1 Isolation**: Channel parameters (RF 57, period 4084, device type 40) remain unchanged.
2. **MainLooper Affinity**: All ANT+ target matching and sensor updates execute strictly on `Looper.getMainLooper()`.
3. **1,000 ms Rolling Window Semantics**: Passing distances and peak threat metrics persist across frames until 1,000 ms decay.
4. **Rule 1.8 Parity**: `RadarWindowAggregator` maintains state parity between constructor and `resetSession()`.
5. **Parent Human Gate Invariance**: Terminal ticket progression remains reserved for human approval.

---

## 4. Proposed Architectural Changes

### Component 1: `ANTBikeRadarDevice.kt`
* Add `TrackRecord` internal model and active track registry (`activeTracks: MutableMap<Int, TrackRecord>`).
* Replace `targetId = baseId + i` with range-continuity track matching ($r_{new} \in [r_{old} - 16.0\text{m}, r_{old} + 4.0\text{m}]$ minimizing $|r_{new} - r_{old}|$).
* Remove `&& rawRange > 0` guard to accept 0-bin targets at $0.0\text{ m}$ distance.
* Gate `sampleAndResetWindow()` and scalar sensor updates on `nowMs - lastWindowSampleMs >= 1000L`.
* In `onWatchdogTimeout()`, `shutDown()`, and `resetSensorsToClear()`: clear `activeTracks`, reset `nextTrackId = 1`, and `lastWindowSampleMs = 0L`.

### Component 2: `RadarWindowAggregator.kt`
* Introduce `latchedPassingDistanceTimestampMs: Long`.
* Timestamp `latchedPassingDistance` when updated upon vehicle exit in `onTargetsUpdated()`.
* In `sampleAndResetWindow(nowMs)`, expire `latchedPassingDistance` strictly when `nowMs - latchedPassingDistanceTimestampMs > WINDOW_DURATION_MS` (1,000 ms), preserving clearance across intermediate sub-second frames.
* Reset `latchedPassingDistanceTimestampMs = 0L` in `resetSession()`.

### Component 3: `BTLERadarDevice.java`
* Add `mLastWindowSampleMs = 0L`.
* In `processTargets()`: gate `sampleAndResetWindow()` and scalar sensor updates on `nowMs - mLastWindowSampleMs >= 1000L`.
* In `resetSensorsToClear()`: reset `mLastWindowSampleMs = 0L`.

### UI Consistency (Rule 23)
* No UI components modified.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Range-Continuity Track Matching & 0-Bin Ingestion in `ANTBikeRadarDevice.kt` (`REQ-CON-040.1`, `REQ-CON-040.2`)
* Define `TrackRecord` data class in `ANTBikeRadarDevice.kt`.
* In `parseTargetsPage()`: decode raw targets including `rawRange == 0` ($0.0\text{ m}$).
* Associate raw targets to existing `activeTracks` by proximity window $[r_{old} - 16\text{m}, r_{old} + 4\text{m}]$, assign stable IDs, and evict unassigned tracks for the reporting bank.

### Step 2: 1,000 ms Latch Decay in `RadarWindowAggregator.kt` (`REQ-CON-040.3`)
* Add `latchedPassingDistanceTimestampMs`.
* Preserve `latchedPassingDistance` across sampling calls within `WINDOW_DURATION_MS` (1,000 ms).
* Update `resetSession()` to clear timestamp.

### Step 3: 1 Hz Driver Gating in `ANTBikeRadarDevice.kt` & `BTLERadarDevice.java` (`REQ-CON-040.4`)
* Gate `sampleAndResetWindow()` and scalar sensor updates on 1,000 ms elapsed time in `ANTBikeRadarDevice.kt` and `BTLERadarDevice.java`.

### Step 4: Unit Test Suite Construction in `ANTBikeRadarDeviceTest.kt`, `RadarWindowAggregatorTest.kt`, `BTLERadarDeviceTest.kt` (`TST-CON-040.1`..`TST-CON-040.4`)
* `ANTBikeRadarDeviceTest.kt`: Verify stable track matching across slot shift (2 cars -> 1 passes -> only 1 pass counted).
* `ANTBikeRadarDeviceTest.kt`: Verify 0-bin target decoding at 0.0m and passing clearance latching.
* `RadarWindowAggregatorTest.kt`: Verify passing clearance retention across intermediate 8 Hz samples within 1,000 ms.
* `BTLERadarDeviceTest.kt`: Verify 1 Hz gating of scalar sensor updates under 8 Hz stream.
* Verification Command:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --max-workers=2
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests (`ant_plus.*`, `radar.*`, `bluetooth_le.*`) and `verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feat/ATT-7578` allows full git revert.
