# Stage 2 Test Specification: ATT-7547

**Ticket:** ATT-7547 (`[Bug] Fix ANT+ Bike Radar Discovery Scan Timeout, Channel Event Handling, and ExtendedData Parsing`)  
**Parent Epic:** ATT-1286 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-038.1` | `TST-CON-038.1` | Unit | `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` | Proposed |
| `REQ-CON-038.2` | `TST-CON-038.2` | Unit | `ANTBikeRadarDevice.kt` | Proposed |
| `REQ-CON-038.3` | `TST-CON-038.3` | Unit | `AntChannelHelper.kt` | Proposed |
| `REQ-CON-038.4` | `TST-CON-038.4` | Unit | `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt` | Proposed |
| `REQ-CON-038.5` | `TST-CON-038.5` | Unit | `AntChannelHelper.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` | Proposed |

---

## 2. Requirement Specification (`REQ-CON-038`)

### REQ-CON-038: ANT+ Bike Radar Continuous Discovery, Channel Event Re-Opening & ExtendedData SDK Parsing [Proposed]
- **Tests**: `TST-CON-038`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AntChannelHelper.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`, `ANTBikeRadarDevice.kt`
- **Rationale**: When searching for or tracking ANT+ bike radar units, slave channels automatically close after 25s (`LowPrioritySearchTimeout.TWENTY_FIVE_SECONDS`) with `EVENT_RX_SEARCH_TIMEOUT` and `EVENT_CHANNEL_CLOSED`. The system must observe `CHANNEL_EVENT` messages, automatically re-open the channel while search remains active, utilize `ExtendedAssignment(BACKGROUND_SCANNING)` where supported by adapter capabilities, gracefully handle `AntCommandFailedException` on `setAdapterWideLibConfig()`, and unpack extended data (`ChannelId` and `Rssi`) through authentic ANT SDK `DataMessage` abstractions (ATT-7547, Parent Epic ATT-1286, extending `REQ-CON-032` and `REQ-CON-037`):
- **Description**:
  1. *Continuous Radar Discovery & Channel Event Handling (`REQ-CON-038.1`)*:
     - In `ANTSearchForNewDevicesEngineMultiDeviceSearch`, incoming `MessageFromAntType.CHANNEL_EVENT` messages SHALL be decoded via `ChannelEventMessage`.
     - When `eventCode` is `EventCode.RX_SEARCH_TIMEOUT` or `EventCode.CHANNEL_CLOSED` and `mIsRadarSearchActive == true`, `mRadarAntChannel.open()` SHALL be invoked to maintain continuous discovery.
  2. *Tracking Channel Search Re-Opening (`REQ-CON-038.2`)*:
     - In `ANTBikeRadarDevice`, when `isSearching == true` and `CHANNEL_EVENT` reports `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED`, `antChannel.open()` SHALL be invoked so pairing and connecting to a newly switched-on radar unit is not silently abandoned after 25 seconds.
  3. *Adapter Background Scanning Assignment (`REQ-CON-038.3`)*:
     - When configuring the radar discovery channel, `AntChannelHelper` SHALL check if the adapter supports background scanning via `channel.getCapabilities().hasBackgroundScanning()`.
     - If supported, the channel SHALL be assigned with `ExtendedAssignment` enabling background scanning. If unsupported or rejected, assignment SHALL fall back gracefully to standard `ChannelType.BIDIRECTIONAL_SLAVE`.
  4. *Safe Adapter-Wide LibConfig Handling (`REQ-CON-038.4`)*:
     - In both `ANTSearchForNewDevicesEngineMultiDeviceSearch` and `ANTBikeRadarDevice`, calls to `setAdapterWideLibConfig()` SHALL catch `AntCommandFailedException`, logging a warning and proceeding with channel configuration rather than aborting connection setup when another channel already configured the adapter.
  5. *Standardized Extended Data & RSSI Extraction (`REQ-CON-038.5`)*:
     - Incoming ANT messages SHALL be handled uniformly for both `BROADCAST_DATA` and `ACKNOWLEDGED_DATA` using `DataMessage`.
     - When `msg.hasExtendedData()` is true, `ChannelId.getDeviceNumber()` SHALL be extracted, and if `msg.getExtendedData().hasRssi()` is true, `Rssi.getRssiValue()` SHALL be forwarded to `onNewDeviceFound(...)`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active ANT+ radar search or device tracking session,
• *When* the 25-second slave search timeout expires emitting `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED`,
• *Then* the channel event handler automatically re-opens the channel without user intervention while search is active.
• *Given* an ANT adapter supporting background scanning capabilities,
• *When* radar discovery starts,
• *Then* the channel is assigned with `ExtendedAssignment` enabling background scanning.
• *Given* an adapter where another channel already configured adapter-wide lib config,
• *When* `setAdapterWideLibConfig()` throws `AntCommandFailedException`,
• *Then* the exception is caught, logged as a warning, and channel configuration completes successfully.
• *Given* an incoming `BROADCAST_DATA` or `ACKNOWLEDGED_DATA` message containing extended data and RSSI,
• *When* parsed by the discovery engine,
• *Then* `deviceNumber` and real RSSI values are extracted and reported to `onNewDeviceFound(...)`.

**Invariants**: Predefined Network Slot 1 isolation; backward compatibility with adapters lacking background scanning; zero disruption to concurrently open ANT+ channels.

---

## 3. Test Specification (`TST-CON-038`)

### TST-CON-038: ANT+ Bike Radar Continuous Discovery, Channel Event Re-Opening & ExtendedData Parsing Test Suite [Proposed]
- **Verifies**: `REQ-CON-038`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Component**: `AntChannelHelperTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`, `ANTBikeRadarDeviceTest.kt`
- **Jira**: `ATT-7547`
- **Test Procedures**:
  1. *Discovery Channel Event Re-Open on 25s Timeout Test (`TST-CON-038.1`)*:
     - Verify delivering `CHANNEL_EVENT` with `RX_SEARCH_TIMEOUT` / `CHANNEL_CLOSED` while radar search is active triggers `channel.open()`.
     - Verify stopped radar search does not re-open channel.
  2. *Tracking Channel Search Re-Open on Timeout Test (`TST-CON-038.2`)*:
     - Verify delivering `CHANNEL_EVENT` with `RX_SEARCH_TIMEOUT` while `isSearching == true` triggers `channel.open()`.
     - Verify connected state does not re-open channel on event.
  3. *Background Scanning Assignment & Fallback Test (`TST-CON-038.3`)*:
     - Verify `assignScanChannel` assigns `ExtendedAssignment(BACKGROUND_SCANNING)` when capabilities support it.
     - Verify fallback to basic `BIDIRECTIONAL_SLAVE` when unsupported.
  4. *Safe LibConfig Exception Tolerance Test (`TST-CON-038.4`)*:
     - Verify `AntCommandFailedException` on `setAdapterWideLibConfig` is caught cleanly without aborting configuration.
  5. *DataMessage ExtendedData & RSSI Extraction Test (`TST-CON-038.5`)*:
     - Verify `AcknowledgedDataMessage` and `BroadcastDataMessage` extract device number and RSSI.
