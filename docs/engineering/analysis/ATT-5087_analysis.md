# Stage 1 Analysis: ATT-5087 - Built-in Cycling Radar Traffic Simulator for Indoor Testing, Preview & Demo Mode

**Ticket**: [ATT-5087](https://rainerblind.atlassian.net/browse/ATT-5087)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-5087`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Under Epic ATT-1286, developing and verifying cycling rearview radar features (live edge overlay in `ATT-5085`, audio/haptic alerts in `ATT-5086`, and traffic recording in `ATT-5088`) typically requires riding on open roads with physical radar hardware (`ATT-5090`).
1. **Unblocking Downstream Development**: A built-in, scriptable `RadarTrafficSimulator` unblocks the entire radar software stack before physical hardware delivery, enabling indoor testing, UI previews, and automated clean-room CI validation.
2. **Scenario Coverage**: The simulator must model realistic traffic patterns:
   - *Single Overtaking Car*: 140 m approach at 12 m/s (43 km/h relative), passing at 0 m, incrementing `RADAR_VEHICLE_COUNT`.
   - *Multi-Vehicle Convoy*: 3 simultaneous vehicles at 140 m, 115 m, 85 m testing multi-dot rendering.
   - *High-Speed Threat*: Rapid approach at 25 m/s (90 km/h relative, `FAST_APPROACHING`) triggering urgent alerts.
   - *Mid-Ride Disconnect*: Simulates packet starvation $\rightarrow$ `DISCONNECTED_ERROR` $\rightarrow$ recovery after 5 seconds.
   - *Country Road Loop*: Continuous randomized traffic intervals for indoor trainer workouts.
3. **Hardware Arbitration & Preview UI**: Simulator must yield automatically when physical radar hardware connects or tracking stops, and expose interactive controls via `RadarSettingsSection.kt` with 9-language localization parity (`strings_radar.xml`).

---

## 2. Root Cause & Gap Analysis & Independent Peer Review
* **Architectural Gap**:
  - `RadarStateRepository` and `RadarWindowAggregator` currently depend solely on physical BLE frames from `BTLERadarDevice`. No synthetic stream generator exists to inject simulated target vectors at 8 Hz.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - *Repository Decoupling*: Verified that `RadarStateRepository.updateState()` and `RadarWindowAggregator.onTargetsUpdated()` accept synthetic frames directly. Simulating approach and departure cleanly exercises vehicle passing hysteresis.
  - *Hardware Precedence Invariant*: Physical hardware must always override simulated traffic. If a physical `BIKE_RADAR` peripheral connects (`isReceivingData() == true`), the simulator must immediately halt.
  - *Lifecycle Safety*: The simulator engine must run in a structured coroutine scope (`Job`), ensuring cancellation when settings exit or workout stops, preventing battery drain or leaked background loops.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (ATT-5087 - 2 SP)**:
  - Implement `RadarTrafficSimulator.kt` with 5 scriptable scenario presets and 8 Hz coroutine stream emission.
  - Wire simulator output to `RadarStateRepository` and `RadarWindowAggregator`.
  - Implement `RadarSettingsSection.kt` providing scenario selection and preview toggles.
  - Provide complete 9-language localization in `strings_radar.xml` (zero inline emojis).
  - Author comprehensive unit tests in `RadarTrafficSimulatorTest.kt`.
* **Out-of-Scope Non-Goals**:
  - `ATT-5085`: Live edge overlay rendering composables (separate ticket).
  - `ATT-5086`: AudioTrack synthesis and haptic alerts (separate ticket).
  - `ATT-5095`: BLE V2 lateral lane-offset and vehicle sizing decoder (separate ticket).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shards**: Living doc shard `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Introduces net-new `REQ-CON-029` (*Built-in Cycling Radar Traffic Simulator & Scenario Engine*) and `TST-CON-029`, complementing `REQ-CON-028` (BLE Radar Driver).
* **Preservation of Core Invariants**: Real radar hardware retains absolute precedence; zero SQLite schema modifications; 100% 9-language translation parity.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Automatic simulator termination upon physical radar connection or tracking stop; clean coroutine cancellation; zero phantom writes to DB during inactive tracking.
* **Risk Rating**: **LOW** (Self-contained simulation engine and settings UI; zero coupling to existing BLE sensor drivers or database schemas).
