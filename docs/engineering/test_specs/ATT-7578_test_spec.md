# Stage 2 Test Specification: ATT-7578

**Ticket:** ATT-7578 (`[Bug] Fix ANT+ Bike Radar slot-index track IDs and per-packet window reset`)  
**Parent Epic:** ATT-1286 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)  

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-040.1` | `TST-CON-040.1` | Unit | `ANTBikeRadarDevice.kt` | Proposed |
| `REQ-CON-040.2` | `TST-CON-040.2` | Unit | `ANTBikeRadarDevice.kt` | Proposed |
| `REQ-CON-040.3` | `TST-CON-040.3` | Unit | `RadarWindowAggregator.kt` | Proposed |
| `REQ-CON-040.4` | `TST-CON-040.4` | Unit | `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java` | Proposed |

---

## 2. Requirement Specification (`REQ-CON-040`)

### REQ-CON-040: ANT+ Radar Track Identity Continuity, 0-Bin Target Ingestion & Rolling Window Latch Preservation [Planned]
- **Tests**: `TST-CON-040`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ANTBikeRadarDevice.kt`, `RadarWindowAggregator.kt`, `BTLERadarDevice.java`
- **Rationale**: In the ANT+ Bike Radar device profile (0x28), targets are reported sorted by proximity across Data Pages 0x30/0x31. When a leading vehicle overtakes and passes the cyclist, trailing vehicles shift to lower slot indices (e.g., slot 1 -> slot 0). Using raw slot indices as track IDs causes track identity churn, leading `RadarWindowAggregator` to prematurely count passes and subsequently double-count vehicles. Furthermore, filtering out `rawRange == 0` truncates targets in the critical 0-bin ($< 3.125\text{ m}$) right at the overtake point and prevents close-pass lateral clearance ($\le 3.0\text{ m}$) from latching. In addition, calling `sampleAndResetWindow()` per packet wipes `latchedPassingDistance` before 1 Hz database sampling. The system must establish range-continuity track association, accept 0-bin targets at $0.0\text{ m}$, preserve latched passing clearances over the 1,000 ms rolling window, and gate scalar sensor sampling on a 1-second cadence (ATT-7578, Parent Epic ATT-1286, extending `REQ-CON-028` and `REQ-CON-032`):
- **Description**:
  1. *Stable Track Identity via Range-Continuity Matching (`REQ-CON-040.1`)*:
     - In `ANTBikeRadarDevice.kt`, incoming targets decoded from Data Pages `0x30` and `0x31` SHALL NOT use raw slot indices (`baseId + i`) as persistent track IDs.
     - The device SHALL maintain an active track registry and associate incoming targets to existing tracks using range-continuity matching: an incoming target matches an unassigned active track if its range $r_{new} \in [r_{old} - 16.0\text{ m}, r_{old} + 4.0\text{ m}]$, selecting the candidate with minimal $|r_{new} - r_{old}|$.
     - Unmatched incoming targets SHALL be assigned a novel unique `trackId`. Unmatched active tracks from the reporting page bank SHALL be removed. When a closer vehicle passes and slot indices shift, trailing vehicles SHALL retain their unique track ID, ensuring `RadarWindowAggregator` increments vehicle pass count strictly once per passing vehicle.
  2. *0-Bin Target Ingestion ($< 3.125\text{ m}$ Range) (`REQ-CON-040.2`)*:
     - In `ANTBikeRadarDevice.kt`, targets with `threatLevelBits > 0` and `rawRange == 0` SHALL be accepted as valid targets at $0.0\text{ m}$ distance rather than dropped by a defensive `rawRange > 0` filter.
     - Targets at $0.0\text{ m}$ with lateral offset SHALL satisfy the longitudinal proximity threshold ($\le 3.0\text{ m}$), enabling `minPassingLateral` to be latched in `RadarWindowAggregator`.
  3. *1,000 ms Window Latch Preservation for Passing Distance (`REQ-CON-040.3`)*:
     - In `RadarWindowAggregator.kt`, `latchedPassingDistance` SHALL be timestamped upon vehicle exit and preserved across successive frames for the duration of the 1,000 ms rolling latch window (`WINDOW_DURATION_MS = 1000L`).
     - Calling `sampleAndResetWindow(nowMs)` within 1,000 ms of target exit SHALL return `latchedPassingDistance` non-null, and `latchedPassingDistance` SHALL decay to `null` strictly when `nowMs - latchedPassingDistanceTimestampMs > WINDOW_DURATION_MS`.
  4. *1 Hz Driver Gating for Scalar Metric Sampling (`REQ-CON-040.4`)*:
     - In both `ANTBikeRadarDevice.kt` and `BTLERadarDevice.java`, invocation of `sampleAndResetWindow()` and scalar sensor `.newValue()` updates SHALL be gated on a 1-second cadence ($\Delta t \ge 1000\text{ ms}$) rather than executed per high-rate (4–8 Hz) packet.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-032.3` (Data Page 0x30/0x31 bit unpacking) and `REQ-CON-028.5` (Scalar 1 Hz latch aggregator) in `docs/requirements/req_tracking_cockpit.md`.
2. *Historical Origin & Commit Trace*: Sprints 2026-46.3 (`ATT-5083`, `ATT-5084`) and 2026-46.4 (`ATT-6700`).
3. *Root Reason for Existing Formulation*: In `ATT-5084`, `targetId = baseId + i` assumed slot assignments were static tracks, unaware that ANT+ devices dynamically sort targets by proximity. `rawRange > 0` was defensive against unpopulated payload bytes. In `ATT-6700`, `sampleAndResetWindow()` was intended for 1 Hz polling, but drivers called it per packet, while `latchedPassingDistance` lacked windowed decay.
4. *Preservation of Core Invariants*: Predefined Network Slot 1 isolation; 3.5s starvation watchdog; Rule 1.8 constructor parity; sensor decoupling; 100% 9-language localization parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* two approaching vehicles where the closest passes and slot indices shift,  
• *When* `ANTBikeRadarDevice` decodes consecutive frames,  
• *Then* the remaining vehicle retains its track identity and only one pass is counted.  
• *Given* an ANT+ target frame with `threatLevelBits > 0` and `rawRange == 0`,  
• *When* decoded by `ANTBikeRadarDevice`,  
• *Then* the vehicle is retained at `0.0m` range and its lateral passing clearance is recorded.  
• *Given* 8 Hz radar packets,  
• *When* `RadarWindowAggregator` is sampled,  
• *Then* `latchedPassingDistance` is preserved across frames within the 1-second window.  
• *Given* rapid 8 Hz radar packets in `ANTBikeRadarDevice` and `BTLERadarDevice`,  
• *When* packets arrive,  
• *Then* `sampleAndResetWindow()` and scalar sensor updates execute at a 1-second cadence.  

**Invariants**: Predefined Network Slot 1 isolation; thread safety via single-threaded MainLooper serialization; zero memory leaks or unhandled exceptions on shutdown; 100% test pass rate.

---

## 3. Test Specification (`TST-CON-040`)

### TST-CON-040: ANT+ Bike Radar Track Identity Continuity, 0-Bin Ingestion & Rolling Window Latch Preservation Test Suite [Planned]
- **Verifies**: `REQ-CON-040`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Component**: `ANTBikeRadarDeviceTest.kt`, `RadarWindowAggregatorTest.kt`, `BTLERadarDeviceTest.kt`
- **Jira**: `ATT-7578`
- **Test Procedures**:
  1. *ANT+ Track Stability Across Slot Shifts Test (`TST-CON-040.1`)*:
     - Feed `ANTBikeRadarDevice.decodeBroadcastData` with a frame containing Car A at 5m in slot 0 and Car B at 15m in slot 1.
     - In the subsequent frame, feed a packet where Car A has disappeared and Car B shifts to slot 0 at 12m.
     - Verify that Car B retains its stable `trackId` from the previous frame, and `RadarWindowAggregator` increments `cumulativeVehicleCount` by exactly 1 (for Car A).
  2. *0-Bin Target Ingestion and Lateral Latch Test (`TST-CON-040.2`)*:
     - Feed an ANT+ Page 0x30 payload with `threatLevelBits = 1`, `rawRange = 0`, and `threatSide = 1` (right lane, lateral offset = 1.5m).
     - Verify `RadarStateRepository.getState().targets` contains a `RadarTarget` with `rangeMeters = 0.0f` and `lateralOffsetMeters = 1.5f`.
     - Verify `passingDistanceMeters` in `RadarWindowAggregator` latches lateral clearance $\le 3.0\text{ m}$.
  3. *Rolling Window Latch Preservation Test (`TST-CON-040.3`)*:
     - In `RadarWindowAggregatorTest`, simulate a target overtaking with lateral clearance at $t = 1000\text{ ms}$, then exiting at $t = 1125\text{ ms}$.
     - Sample `sampleAndResetWindow()` at $t = 1125\text{ ms}$ and again at $t = 1500\text{ ms}$ (intermediate 8 Hz / sub-second frames).
     - Verify `passingDistanceMeters` remains non-null at both sample points, and decays to `null` strictly after $t = 2126\text{ ms}$ ($> 1000\text{ ms}$ window).
  4. *1 Hz Driver Gating Test (`TST-CON-040.4`)*:
     - Deliver 8 consecutive frames spaced 125 ms apart to `ANTBikeRadarDevice` and `BTLERadarDevice`.
     - Verify that `sampleAndResetWindow()` and scalar sensor updates execute at a 1-second cadence (once per 8 frames) rather than 8 times.
