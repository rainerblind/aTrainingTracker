# Living Requirements: Tracking & Live Sensor Cockpit (Part 2 of 2)

**Document ID**: `DOC-REQ-TRK-P2`  
**Version**: 1.0.95  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `tracking_cockpit_part2`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.  
**Total Requirements**: 87

---

### REQ-CON-028: BLE Cycling Radar Protocol Driver, Serialized GATT Queue, Scalar Telemetry & Bike Equipment Integration [Verified]
- **Tests**: `TST-CON-028`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`, `SYSREQ-TRK-006`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceType.java`, `SensorType.java`, `ActivityType.java`, `BluetoothConstants.java`, `MyBTLEDevice.java`, `BTSearchForNewDevicesEngine.java`, `BTLERadarDevice.java`, `RadarStateRepository.kt`, `RadarWindowAggregator.kt`, `EquipmentSensorOrdering.kt`, `strings_radar.xml`
- **Rationale**: Cyclists operating in traffic require real-time rearward situational awareness. To integrate Garmin Varia cycling radar units (RearVue 820, RTL515, RCT715) into *aTrainingTracker*, BANALService requires a dedicated `DeviceType.BIKE_RADAR` and `BTLERadarDevice` protocol driver. Because radars interleave threat packets with heartbeat/sector frames on characteristic `6a4e3203` and require sequential CCCD configuration, `MyBTLEDevice` requires a serialized GATT operation queue. High-frequency (4–8 Hz) multi-target streams must feed reactive UI via `RadarStateRepository` (`StateFlow<RadarState>`) with a 3.5s fail-safe disconnect watchdog, while Cockpit and `TrackerService` require 1 Hz scalar metrics (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`) via a 1,000 ms rolling window aggregator with zero SQLite schema migrations and full 9-language localization parity (ATT-5083, ATT-6700, ATT-6701, ATT-7543, Parent Epic ATT-1286, extending `REQ-CON-002`, `REQ-CON-014`, and `REQ-CON-020`):
1. *DeviceType.BIKE_RADAR & Equipment Matrix (`REQ-CON-028.1`)*:
• The system SHALL define `DeviceType.BIKE_RADAR` associated with `BSportType.BIKE` and main sensor `SensorType.RADAR_THREAT_COUNT`.
• `DevicesDatabaseManager.isBikeSensor()` SHALL classify `BIKE_RADAR` as a bike sensor, automatically integrating it into `EquipmentSensorMatrixScreen` and bike equipment profiles.
2. *Serialized GATT Operation Queue in MyBTLEDevice (`REQ-CON-028.2`)*:
• `MyBTLEDevice` SHALL serialize GATT operations (CCCD descriptor writes, characteristic reads, and control-point writes) via a thread-safe FIFO queue.
• The system SHALL route all characteristic reads (including initial and periodic battery reads) through `mGattQueue` via `enqueueReadCharacteristic`, ensuring zero concurrent unqueued GATT operations.
• The system SHALL advance to the next queued GATT operation only upon receiving `onDescriptorWrite`, `onCharacteristicRead`, or `onCharacteristicWrite` callback confirmation, preventing dropped commands during multi-characteristic setup and eliminating split-brain queue races between `onCharacteristicRead` and inflight writes.
3. *Resilient BLE Radar Peripheral Discovery (`REQ-CON-028.3`)*:
• `BTSearchForNewDevicesEngine` SHALL match Garmin Varia Radar service `6a4e3200-667b-11e3-949a-0800200c9a66` and 16-bit Member Service UUID `0xFE1F` (`0000fe1f-0000-1000-8000-00805f9b34fb`) across primary advertisement UUIDs, scan response records, and post-connect service discovery.
• In `buildScanFilters(DeviceType.BIKE_RADAR)`, the discovery engine SHALL include scan filters matching both 128-bit `UUID_SERVICE_RADAR` and 16-bit `UUID_SERVICE_GARMIN_MEMBER` (service UUID and service data).
• In `onServicesDiscovered`, the discovery engine SHALL follow the unified peripheral flow by enqueuing Device Information and Battery Level metadata characteristics first, and validating the presence of `UUID_SERVICE_RADAR` in `readNextCharacteristic(address)` upon queue completion before invoking `newDeviceFound(address)`.
• Dead scan-record matching helpers (`matchesScanRecord`) SHALL be eliminated in compliance with dead-code prevention rules.
4. *BTLERadarDevice V1 Threat Decoding, GATT Lifecycle & Reactive Repository (`REQ-CON-028.4`)*:
• `BTLERadarDevice` SHALL transition `RadarStateRepository` to `RadarConnectionState.CONNECTING` when `startSearching()` is invoked.
• `BTLERadarDevice` SHALL transition `RadarStateRepository` to `RadarConnectionState.CONNECTED` (with empty targets and armed 3.5s watchdog) once radar service and characteristic CCCD subscription are configured in `configureServices(BluetoothGatt)`.
• `BTLERadarDevice` SHALL subscribe to V1 characteristic `6a4e3203-667b-11e3-949a-0800200c9a66`, inspect header `byte[0]` supporting sequence framing `(payload[0] & 0x0F) == 0x02` (cycling `0x02..0xF2`) and legacy `0x00`.
• 1-byte V1 heartbeats (`len == 1 && (payload[0] & 0x0F) == 0x02`) and 6-byte sector amplitude diagnostic frames (`len == 6 && payload[0] == 0x06`) SHALL be recognized as valid link-alive packets that reset the 3.5s watchdog, prune stale tracks (`STALE_MS = 2000L`), and publish `RadarConnectionState.CONNECTED`.
• Threat frames (`len >= 4 && (len - 1) % 3 == 0 && ((payload[0] & 0x0F) == 0x02 || payload[0] == 0x00)`) SHALL decode `1 + 3*N` bytes into triplets `[vid, dist, flag]`, discarding invalid tracks with `vid < 0x80`, `vid == 0xFD`, or `dist == 0xFF`. Valid tracks (`trackId = vid & 0x7F`, `rangeMeters = dist & 0xFF`) SHALL be maintained in a stateful track history with ageing (`STALE_MS = 2000L`) to derive closing speed (`approachSpeedMps`) from range deltas over time ($\Delta d / \Delta t$) and classify `ThreatLevel` (`FAST_APPROACHING` for closing speed $\ge 15.0\text{ m/s}$ or legacy threat bits, `APPROACHING` for closing speed $> 0.0\text{ m/s}$).
• Target updates SHALL be published to `RadarStateRepository` (`StateFlow<RadarState>`) at 4–8 Hz.
• A 3.5s watchdog timer SHALL transition `RadarState` to `DISCONNECTED_ERROR` if no valid packets arrive or GATT disconnects.
• Upon GATT disconnection (`disconnectFromGatt()`), `RadarStateRepository` SHALL transition to `RadarConnectionState.DISCONNECTED` with empty targets; late-arriving queued measurement runnables on `mHandler` SHALL be dropped under `mGattLock` when `gatt != mBluetoothGatt` or `mState != State.CONNECTED_WITH_SERVICE`, strictly preventing post-disconnect stale target revival and false reconnection (ATT-7579).
5. *Scalar 1 Hz Latch Aggregator, Vehicle Counter & Zero-Migration Persistence (`REQ-CON-028.5`)*:
• The system SHALL expose scalar metrics `RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, and `RADAR_VEHICLE_COUNT` in `SensorType.java`.
• `RadarWindowAggregator` SHALL maintain a 1,000 ms rolling latch window using timestamped target observations, retaining peak threat count, max approach speed, minimum closest distance, and minimum passing clearance across the last 1,000 ms so sub-second vehicle passes are preserved for 1 Hz `TrackerService` database sampling.
• When no active approaching targets exist in the 1,000 ms window (clear road), the aggregator SHALL return `null` for closest distance and passing distance, `0` for threat count, and `0.0` for approach speed; `BTLERadarDevice` SHALL forward `null` to `mClosestDistanceSensor` and `mPassingDistanceSensor` so live cockpit tiles return to `--` and database records do not persist stale distances across clear road segments.
• Upon receiving `BANALService.RESET_ACCUMULATORS_INTENT`, `BTLERadarDevice.onAccumulatorsReset()` SHALL invoke `mWindowAggregator.resetSession()` and publish `0` to `mVehicleCountSensor` and `mLargeVehicleCountSensor`.
• The system SHALL track cumulative passing vehicles using target-ID lifecycle and closing-range hysteresis.
• Database persistence in `WorkoutSamplesDatabaseManager` and `WorkoutSummariesDatabaseManager` SHALL require zero SQLite schema migrations (`DB_VERSION` unchanged).
6. *9-Language Localization Parity (`REQ-CON-028.6`)*:
• String resources for `DeviceType.BIKE_RADAR` and all radar scalar `SensorType`s SHALL be provided in `strings_radar.xml` across all 9 canonical languages (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-CON-028`, extending `REQ-CON-002` (*Bluetooth LE Sensor Support*), `REQ-CON-014` (*GATT Packet Resilience*), and `REQ-CON-020` (*GATT Queue Integrity*). Targets `DeviceType.java`, `SensorType.java`, `MyBTLEDevice.java`, `BTSearchForNewDevicesEngine.java`, `BTLERadarDevice.java`, `RadarWindowAggregator.kt`.
2. *Historical Origin & Commit Trace*: `ATT-1485` (`REQ-CON-014`) established safe BLE payload parsing; `ATT-3312` introduced chained battery CCCD writes; `ATT-3540` established multi-sensor push architecture; `ATT-6700` refined 1,000 ms rolling latch window, road-clear null distance reset, and workout-start accumulator reset; `ATT-6701` unified `MyBTLEDevice` GATT read queue and aligned `BTSearchForNewDevicesEngine` discovery; `ATT-7543` aligned V1 packet parsing with real Garmin Varia / RearVue 820 framing (`(seq << 4) | 0x02`), heartbeats, sector frames, track ageing, GATT connection states, and Member Service UUID `0xFE1F` discovery; refined in `ATT-7579` for post-disconnect stale target prevention.
3. *Root Reason for Existing Formulation*: Existing BLE devices relied on single-characteristic notifications or ad-hoc descriptor writes. Multi-target radar requires sequential CCCD setup, packet demuxing, and dual-rate (reactive 8 Hz vs. persisted 1 Hz) handling. In ATT-5083, synthetic simulator assumptions assumed `header = 0x00`, hardcoded approach speed in byte 3, and only updated connection state upon target reception. Real hardware uses sequence-nibble framing, heartbeats when no cars are present, stateful closing speed derivation ($\Delta d / \Delta t$), and requires explicit `CONNECTING`/`CONNECTED` state progression to render `RadarEdgeOverlay`. In ATT-5083, asynchronous main-thread handler execution was assumed to be cleared on shutdown, but mid-ride disconnects left queued measurement runnables in `mHandler` that revived `CONNECTED` state.
4. *Preservation of Core Invariants*: Existing BLE sensors (HRM, Power, Speed, Cadence) remain completely unaffected; zero SQLite schema migrations; 100% 9-language translation parity; vehicle counting hysteresis and Rule 1.8 constructor parity preserved. Unifying reads into `mGattQueue` and verifying radar service at the end of discovery preserves strict FIFO serialization without dropped callbacks, while ensuring Device Information and Battery Level are read consistently across all peripheral types. Real-hardware framing maintains full backward compatibility with legacy simulator packets (`0x00`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an initiated connection to a Garmin Varia radar peripheral in `BTLERadarDevice.startSearching()`,
• *When* the BLE connection sequence begins,
• *Then* `RadarStateRepository` immediately transitions to `RadarConnectionState.CONNECTING`.
• *Given* a discovered and subscribed Garmin Varia radar service in `configureServices()`,
• *When* CCCD descriptor configuration completes,
• *Then* `RadarStateRepository` transitions to `RadarConnectionState.CONNECTED` with an armed 3.5s watchdog, enabling `RadarEdgeOverlay` rendering prior to target packet arrival.
• *Given* a physical Garmin Varia emitting 1-byte heartbeats (`len == 1 && (b & 0x0F) == 0x02`) or 6-byte sector frames (`len == 6 && b == 0x06`) on `6a4e3203`,
• *When* received by `BTLERadarDevice`,
• *Then* the 3.5s watchdog is reset, stale tracks are pruned (`STALE_MS = 2000L`), and `CONNECTED` state is maintained without false disconnection.
• *Given* a BLE radar streaming interleaved threat, heartbeat, and sector packets on `6a4e3203`,
• *When* parsed by `BTLERadarDevice`,
• *Then* non-threat packets are filtered, invalid tracks (`vid < 0x80`, `vid == 0xFD`, `dist == 0xFF`) discarded, and valid targets are published to `RadarStateRepository` at 4–8 Hz with stateful closing speed and threat level.
• *Given* a BLE discovery scan for `DeviceType.BIKE_RADAR`,
• *When* `BTSearchForNewDevicesEngine.buildScanFilters()` is executed,
• *Then* scan filters include both Garmin Radar service UUID `6a4e3200-667b-11e3-949a-0800200c9a66` and Garmin Member Service UUID `0xFE1F` (`0000fe1f-0000-1000-8000-00805f9b34fb`).
• *Given* an active workout with 4–8 Hz radar stream,
• *When* sampled at 1 Hz by `TrackerService`,
• *Then* peak speed and closest distance within the 1,000 ms rolling window are latched and saved to `WorkoutSamples.db` with zero schema alterations.
• *Given* an overtaking vehicle that completes its pass and leaves the radar tracking field,
• *When* 1,000 ms elapse with no approaching targets,
• *Then* `RadarWindowAggregator` emits null for closest distance and `BTLERadarDevice` updates `mClosestDistanceSensor` with null so cockpit displays `--`.
• *Given* an active or connected radar before workout start,
• *When* `BANALService.RESET_ACCUMULATORS_INTENT` is broadcast,
• *Then* `BTLERadarDevice.onAccumulatorsReset()` resets aggregator session and sets vehicle count sensors to 0.
• *Given* a disconnected or out-of-range radar,
• *When* 3.5 seconds elapse without valid packets,
• *Then* `RadarStateRepository` transitions to `DISCONNECTED_ERROR`.
• *Given* `BTLERadarDevice.disconnectFromGatt()` has executed,
• *When* late measurement runnables execute on `mHandler`,
• *Then* they are ignored and `RadarStateRepository` remains in `DISCONNECTED` state with zero active targets (ATT-7579).
• *Given* a peripheral discovery session for `DeviceType.BIKE_RADAR` in `BTSearchForNewDevicesEngine`,
• *When* services are discovered,
• *Then* Device Information and Battery Level characteristics are enqueued first, and `UUID_SERVICE_RADAR` is verified upon queue completion before invoking `newDeviceFound`.
• *Given* a connected BLE device running periodic battery reads via `mBatteryReadRunnable` while a GATT descriptor write is queued,
• *When* the battery read is requested,
• *Then* it is enqueued on `mGattQueue` via `enqueueReadCharacteristic` and executes strictly after preceding GATT operations without releasing `mGattOperationPending` prematurely.

**Invariants**: Existing BLE sensors unaffected; zero schema migrations; 100% 9-language translation parity; Rule 1.8 constructor baseline state parity.

### REQ-CON-029: Built-in Cycling Radar Traffic Simulator & Scenario Engine [Verified]
- **Tests**: `TST-CON-029`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarTrafficSimulator.kt`, `SimulatedRadarDevice.kt`, `BANALService.java`, `BTLERadarDevice.java`, `TrackerService.java`, `RadarStateRepository.kt`, `RadarWindowAggregator.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Developing and testing cycling radar UI overlays (`ATT-5085`), audio/haptic alert synthesis (`ATT-5086`), and post-ride safety analytics (`ATT-5088`) without physical radar hardware on open roads requires a realistic, scriptable traffic simulator. `RadarTrafficSimulator` provides 5 scriptable scenario presets emitting synthetic target vectors at 8 Hz directly into `RadarStateRepository` and `RadarWindowAggregator`, enabling indoor workouts, UI preview, and automated clean-room CI validation. Physical radar hardware retains absolute precedence (ATT-5087, ATT-6704, Parent Epic ATT-1286, extending `REQ-CON-028`):
1. *Scriptable Scenario Presets (`REQ-CON-029.1`)*:
• The system SHALL provide 5 scriptable scenario presets: Single Overtaking Car (140m approach at 12 m/s, passing at 0m), Multi-Vehicle Convoy (3 simultaneous targets at 140m, 115m, 85m), High-Speed Threat (rapid approach at 25 m/s, `FAST_APPROACHING`), Mid-Ride Disconnect (starvation to `DISCONNECTED_ERROR` followed by recovery after 5s), and Continuous Country Road Loop (continuous randomized traffic intervals).
2. *8 Hz Stream Pipeline & Scalar MySensor Bridge (`REQ-CON-029.2`)*:
• The simulator SHALL emit synthetic target frames at 8 Hz into `RadarStateRepository` (`StateFlow<RadarState>`) and update `RadarWindowAggregator`. When `BANALService` is active, `SimulatedRadarDevice` (extending `MyDevice`) SHALL register and publish the 6 scalar radar `MySensor` channels (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`), displaying live metrics in cockpit tiles and recording them to `WorkoutSamples.db` during workouts.
3. *Hardware Preemption & Workout Lifecycle Cleanliness (`REQ-CON-029.3`)*:
• The simulator SHALL immediately halt when physical radar hardware connects or streams data (`onPhysicalHardwareConnected()`), wired directly into `BTLERadarDevice` measurement notifications. `TrackerService` SHALL invoke `RadarTrafficSimulator.stop()` in `handleActionStop()` and `onDestroy()`, guaranteeing zero lingering simulation or phantom DB writes post-workout.
4. *Reactive StateFlow, Monotonic Clock & ExposedDropdownMenuBox Controls (`REQ-CON-029.4`)*:
• `RadarTrafficSimulator` SHALL expose `isSimulatingFlow: StateFlow<Boolean>` for reactive UI observation and evaluate elapsed simulation timing using monotonic `SystemClock.elapsedRealtime()` under a structured `SupervisorJob` scope. `RadarSettingsSection.kt` SHALL render `RadarScenario` selection using a Material 3 `ExposedDropdownMenuBox` per Design Guidelines §1.3.
5. *9-Language Localization Parity (`REQ-CON-029.5`)*:
• All simulator UI strings, scenario names, and live target status indicators in `strings_radar.xml` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis or untranslated concatenations).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-029` (Built-in Cycling Radar Traffic Simulator & Scenario Engine), targeting `RadarTrafficSimulator.kt`, `SimulatedRadarDevice.kt`, `BANALService.java`, `BTLERadarDevice.java`, `TrackerService.java`, and `RadarSettingsSection.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-5087` (commit `69fec8e7`) as an indoor simulator and preview tool; refined in `ATT-6704` to wire scalar `MySensor` channels via `SimulatedRadarDevice`, wire physical preemption in `BTLERadarDevice`, stop on workout finish, expose `isSimulatingFlow`, switch to monotonic clock, and upgrade scenario selection to `ExposedDropdownMenuBox`.
3. *Root Reason for Existing Formulation*: The initial simulator was built as a rapid standalone prototype for visual edge overlay and audio testing prior to hardware arrival, leaving `BANALService` scalar integration and hardware preemption hooks as omissions. Wall-clock timing was used for simplicity, and `FlowRow` chips were used before options grew to 5.
4. *Preservation of Core Invariants*: Physical hardware retains 100% absolute precedence; zero phantom DB writes outside active workouts; zero SQLite schema modifications; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Single Overtaking Car scenario selected and an active indoor workout,
• *When* simulation runs and 1 Hz window latches,
• *Then* a vehicle approaches from 140m at 12 m/s, passes at 0m, increments `RADAR_VEHICLE_COUNT`, and records scalar metrics to `WorkoutSamples.db`.
• *Given* an active simulation,
• *When* physical radar connects and streams measurement data,
• *Then* `BTLERadarDevice` immediately triggers `onPhysicalHardwareConnected()`, halting the simulator and yielding control cleanly to physical hardware.
• *Given* `RadarSettingsSection.kt` displayed,
• *When* selecting a scenario,
• *Then* the scenario is selected via an `ExposedDropdownMenuBox` with localized status formatting and reactive button state driven by `isSimulatingFlow`.

**Invariants**: Real hardware priority; coroutine job cleanup on stop; zero DB writes outside active tracking; 100% 9-language translation parity.

### REQ-TRK-062: Live Rearview Radar Vertical Edge Overlay Across Tracking Tabs [Verified]
- **Tests**: `TST-TRK-062`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarEdgeOverlay.kt`, `TrackingTabsScreen.kt`, `BTLERadarDevice.java`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `AdvancedTuningAccordion.kt`, `strings_radar.xml`
- **Rationale**: Cyclists operating in traffic require visual awareness of approaching vehicles across all workout tabs (Cockpit grids, Live Map, Elevation/Climb charts, Zone screens). `RadarEdgeOverlay` observes `RadarStateRepository.radarState` to render a high-contrast vertical edge rail or full-screen vehicle overlay with animated vehicle dots/badges (0–140m / 175m) and semantic threat colors. The overlay must preserve full gesture pass-through for `HorizontalPager` swipes and Map gestures, enforce fail-safe disconnect visualization (strictly preventing false "All Clear" signals during packet loss or disconnect), permanently omit the green connected status circle (`RadarStatusBadgePill`) when road is clear across all tabs and edge modes, and integrate customizable edge/full-screen placement and clear-state behavior in `TuningPreferencesDataStore` (ATT-5085, ATT-6703, ATT-7537, ATT-8679, Parent Epic ATT-1286, extending `REQ-CON-028` and `REQ-CON-029`):
1. *Vertical Edge Rail & Full-Screen Kinematics (`REQ-TRK-062.1`)*:
• The system SHALL render a vertical rail (24–28 dp width) on the configured screen edge (`LEFT` or `RIGHT`), OR a full-screen vehicle overlay spanning the active Tracking screen (`FULL_SCREEN`), displaying approaching vehicle dots/badges with smooth 60 fps position interpolation entering at the bottom of the screen/rail ($140\text{ m} / 175\text{ m} \to y = 1.0$) and moving upward toward the rider at the top ($0\text{ m} \to y = 0.0$, $y = d / d_{\max}$). In `FULL_SCREEN` mode, approaching vehicles SHALL be mapped laterally across screen width according to lateral offset / lane state and traffic orientation (`RIGHT_HAND` vs `LEFT_HAND`).
2. *Semantic Threat Level Visualization & Conditional Pulse (`REQ-TRK-062.2`)*:
• Target dots and rail accents SHALL reflect semantic threat levels: translucent Green (`TTColor.RadarClearGreen.copy(alpha = 0.25f)`) for the slim vertical rail during clear road (`SLIM_RAIL` / active `AUTO_HIDE_3S`), Amber for `APPROACHING`, and pulsing Red for `FAST_APPROACHING`. The top `RadarStatusBadgePill` SHALL NOT render a green clear-state badge (`CONNECTED` + `!hasActiveThreats`, including `snoozeState.isSnoozed && isClear`). The pulsing red alert animation SHALL be evaluated and composed ONLY when `overallThreatLevel == ThreatLevel.FAST_APPROACHING`, halting all infinite animation frame scheduling and drawing when the road is clear or in standard approach to prevent battery drain.
3. *Clear-State Badge Suppression, Rail Alignment & Touch Pass-Through (`REQ-TRK-062.3`)*:
• The vertical rail and full-screen overlay container SHALL NOT consume touch or scroll gestures, allowing unhindered `HorizontalPager` swiping, Google Map panning/zooming, and Android system edge gestures.
• Whenever `!isDisconnectError && !hasActiveThreats` (`CONNECTED` + clear road, regardless of `audioSnoozeState.isSnoozed`), `RadarStatusBadgePill` SHALL NOT be rendered across `RadarEdgeSide.LEFT`, `RadarEdgeSide.RIGHT`, or `RadarEdgeSide.FULL_SCREEN` on either the Start tab (`page == 0`) or live tracking tabs (`page > 0`), ensuring zero green connected status circles or clear-state snooze circles appear.
• In `LEFT`/`RIGHT` edge rail mode, the top `Spacer(8.dp)` and post-badge `Spacer(6.dp)` MUST be guarded on badge visibility (`isDisconnectError || hasActiveThreats`) so `RadarVerticalRail` does not shift downward by `14.dp` when no badge is shown.
• When `FULL_SCREEN` is active and the road is clear, neither background rail nor `RadarStatusBadgePill` is rendered (zero touch-intercepting hit targets). In `AUTO_HIDE_3S` mode, auto-hiding after 3 seconds of all-clear road SHALL fade out the vertical rail leaving zero clear-state badge; `RadarStatusBadgePill` SHALL render and consume tap events strictly when `isDisconnectError` or `hasActiveThreats` is true, preventing invisible touch-intercepting hit targets.
4. *Fail-Safe Disconnect, Error State & Active-Threat Badge (`REQ-TRK-062.4`)*:
• `TrackingTabsScreen` SHALL render the `RadarEdgeOverlay` strictly when `radarState.connectionState == RadarConnectionState.CONNECTED`, `radarState.connectionState == RadarConnectionState.DISCONNECTED_ERROR` (mid-ride connection drop), or `RadarTrafficSimulator.isSimulating == true`. The overlay bar SHALL remain hidden while merely searching for unconnected sensors (`CONNECTING` or initial `DISCONNECTED`).
• When radar connection state is `DISCONNECTED_ERROR`, the overlay SHALL NOT render a green "All Clear" state; it SHALL display the amber hazard `RadarStatusBadgePill` with `ic_device_not_available`. Tapping `RadarStatusBadgePill` while in `DISCONNECTED_ERROR` SHALL trigger radar peripheral reconnection via `TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES`.
• When `hasActiveThreats` is true (`APPROACHING` or `FAST_APPROACHING`), `RadarStatusBadgePill` SHALL remain rendered with `targets.size` (plus muted-bell/countdown when `audioSnoozeState.isSnoozed` per `REQ-TRK-064.2`) and invoke `onBadgeClick` on tap.
• `BTLERadarDevice.startSearching()` SHALL preserve `RadarConnectionState.DISCONNECTED_ERROR` across reconnection attempts rather than overwriting it with `CONNECTING`, maintaining fail-safe amber indication until data resumes or device disconnects (`ATT-7744`).
5. *User Customization, System Navigation Clearance & DataStore Integration (`REQ-TRK-062.5`)*:
• `TuningPreferencesDataStore` SHALL persist `radarEdgeSide` (`LEFT`, `FULL_SCREEN`, `RIGHT`, default `RIGHT`) and `radarClearMode` (`SLIM_RAIL`, `BADGE_ONLY`, `AUTO_HIDE_3S`, default `SLIM_RAIL`), exposed in `RadarSettingsSection.kt` as a 3-way segmented button (`Links | Vollbild | Rechts`). When `FULL_SCREEN` is selected, `RadarClearMode` selection SHALL be hidden. Subtitle formatter in `AdvancedTuningAccordion.kt` SHALL format all three `RadarEdgeSide` variants correctly. The overlay layout SHALL apply `.navigationBarsPadding()` to guarantee bottom rail clearance above system 3-button or gesture navigation insets per Design Guidelines §5.6 Rule 41.
6. *9-Language Localization, Resource Hygiene & Accessibility Parity (`REQ-TRK-062.6`)*:
• All overlay settings (including `radar_overlay_mode_fullscreen`), status labels, and screen-reader accessibility descriptions in `strings_radar.xml` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis or untranslated string concatenations). Orphaned badge resources (`R.drawable.ic_device_available`, `R.string.radar_overlay_clear`, and `R.string.radar_overlay_connecting` across all 9 `values*/strings_radar.xml` locales) MUST be removed cleanly so `:app:lintDebug` (`UnusedResources`) and `TranslationParityTest` remain 100% compliant.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-TRK-062` (Live Rearview Radar Vertical Edge Overlay Across Tracking Tabs), targeting `RadarEdgeOverlay.kt`, `TrackingTabsScreen.kt`, `RadarOverlayMath`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `AdvancedTuningAccordion.kt`, `BTLERadarDevice.java`, `ic_device_available.xml`, and `strings_radar.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-5085` (commit `671c4c1a`); refined in `ATT-6703` (kinematics inversion, rail auto-hide scoping, nav bar insets); amended in `ATT-7537` to add 3-way position selection (`LEFT`, `FULL_SCREEN`, `RIGHT`), full-screen vehicle badges traversing directly over active tracking screen with 100% touch pass-through, and conditional `RadarClearMode` visibility; refined in `ATT-7744` to suppress overlay bar during initial unconnected sensor search and preserve mid-ride `DISCONNECTED_ERROR` fail-safe state across BLE reconnect attempts; refined in `ATT-8679` to permanently remove the green connected status circle (`RadarStatusBadgePill` during clear road, including snoozed clear road) per PO Ceremony 2 review (`ATT-8109`).
3. *Root Reason for Existing Formulation*: Originally, `RadarStatusBadgePill` served as the clear-road indicator (`BADGE_ONLY` / `AUTO_HIDE_3S`) and cockpit quick-switch trigger before `ATT-7972` introduced the dedicated `RadarLightButton` in the Cockpit action row; keeping a persistent green circle on Start and Live Tracking tabs created unwanted visual clutter ("Der grüne Kreis soll immer raus").
4. *Preservation of Core Invariants*: Cyclist safety through physical rearview perspective (upward approach $140\text{ m}/175\text{ m} \to 0\text{ m}$); 100% gesture pass-through over MapView and HorizontalPager; fail-safe amber disconnect indication & 1-tap reconnect (`REQ-TRK-062.4`); active-threat count & snooze indicator (`REQ-TRK-064.2`); `14.dp` top spacer guard on `LEFT`/`RIGHT` rail; system nav-bar clearance; 100% 9-language translation parity (`TranslationParityTest`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active tracking session (Start tab `page == 0` or live tracking tab `page > 0`) with a connected radar and clear road (`targets.isEmpty()`, `ThreatLevel.NONE`, with or without `audioSnoozeState.isSnoozed`),
• *When* `RadarEdgeOverlay` renders in `LEFT`, `RIGHT`, or `FULL_SCREEN` mode,
• *Then* `RadarStatusBadge` does NOT exist (zero green connected or snooze circle is rendered), and in `LEFT`/`RIGHT` mode `RadarVerticalRail` aligns at the top without a `14.dp` (`8.dp + 6.dp`) downward shift.
• *Given* an active tracking session with a connected radar,
• *When* vehicles approach at 80m and 30m,
• *Then* the edge overlay renders distinct animated dots at proportional vertical heights with threat-appropriate colors moving upward toward the rider ($y = d / d_{\max}$) and displays `RadarStatusBadge` with target count (plus muted-bell/countdown if snoozed) invoking `onBadgeClick` on tap.
• *Given* a user swiping horizontally across the vertical rail or full-screen overlay,
• *When* gestured,
• *Then* `HorizontalPager` page transitions and map pan/zoom occur without gesture interruption or drag locking.
• *Given* `radarEdgeSide == RadarEdgeSide.FULL_SCREEN` and 1 or more vehicles approaching,
• *When* viewing any Tracking Tab,
• *Then* vehicle icons travel upward from bottom to top over the tracking screen according to range and lateral lane offset, while tab swipes and map gestures underneath pass through unimpeded.
• *Given* `radarEdgeSide == RadarEdgeSide.FULL_SCREEN` and road is clear,
• *When* viewing any Tracking Tab,
• *Then* neither background track rail nor `RadarStatusBadgePill` is rendered (zero touch capture).
• *Given* the user navigates to Radar Settings,
• *When* viewing overlay position selector,
• *Then* the 3-way segmented button presents `Links | Vollbild | Rechts`, and selecting `Vollbild` (FULL_SCREEN) conditionally hides the `RadarClearMode` selector.
• *Given* a radar packet starvation timeout (3.5s) or disconnect,
• *When* `connectionState` transitions to `DISCONNECTED_ERROR`,
• *Then* the overlay displays the amber warning badge with `ic_device_not_available`, strictly prohibits green "All Clear" rendering, and initiates device reconnection broadcast when tapped.
• *Given* `AUTO_HIDE_3S` mode and 3 seconds of all-clear road,
• *When* the auto-hide timer elapses,
• *Then* the vertical rail fades out with zero clear-state `RadarStatusBadgePill` rendered and zero phantom click interception.

**Invariants**: Upward vehicle approach kinematics; touch pass-through; zero green connected status circle (`isClear`); `14.dp` top spacer guard; zero false green on disconnect; active-threat badge/snooze retained; zero SQLite schema modifications; 100% 9-language translation parity (`radar_overlay_clear` & `ic_device_available` cleanly removed).

### REQ-TRK-063: Radar Audio Chime and Haptic Alert Engine [Verified]
- **Tests**: `TST-TRK-063`
- **System Requirement(s)**: `SYSREQ-TRK-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `TrackerService.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Cyclists operating in traffic require eyes-free situational awareness through audible and tactile cues that cut through outdoor wind and road noise. `RadarAlertManager` observes `RadarStateRepository.radarState` during active tracking in `TrackerService` (including foreground and screen-off execution), synthesizing distinct PCM audio chimes via `AudioTrack` (`USAGE_MEDIA`, `MODE_STATIC` warm replay) and triggering `VibrationEffect` patterns on threat level transitions (`APPROACHING`, `FAST_APPROACHING`, `ALL_CLEAR`, `DISCONNECTED_ERROR`). The alert engine must support independent radar alert volume, transient background media audio ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), temporary hardware phone-speaker volume boost with guaranteed auto-restore, and configurable audio routing to connected headphones or forced phone speaker output (`AudioTrack.setPreferredDevice()`) without switching `AudioManager` into telephony communication mode (ATT-5086, Parent Epic ATT-1286, extending `REQ-CON-028` and `REQ-TRK-062`):
1. *Threat Transition Alerts, Target Selection & Debouncing (`REQ-TRK-063.1`)*:
• The system SHALL emit distinct acoustic chimes and vibration patterns on threat transitions: double ascending chime for initial approach (`NONE -> APPROACHING`), urgent triple high-pitch chime for high-speed threat (`-> FAST_APPROACHING`), soft single chime for all-clear (`APPROACHING/FAST -> NONE`), and descending warning chime with long vibration for disconnect (`-> DISCONNECTED_ERROR`). When multiple approaching targets exist during high-speed threat escalation, the system SHALL select the `FAST_APPROACHING` target with highest approach speed to drive alert pitch and spatial panning. State transitions SHALL be debounced to prevent alert spamming during continuous 4–8 Hz telemetry updates; however, high-speed escalations (`-> FAST_APPROACHING`) SHALL NOT be suppressed by routine debounce cooldown windows, and debounced frames SHALL NOT advance or mutate the tracked threat level unless the transition alert is actually emitted. Alert debounce cooldowns, chime playback intervals, and lane change cooldowns SHALL be tracked using a strictly monotonic time clock (`SystemClock.elapsedRealtime()`), ensuring wall-clock NTP adjustments or timezone jumps do not freeze or prematurely expire alert timers.
2. *AudioTrack USAGE_MEDIA Synthesis & Warm MODE_STATIC Replay (`REQ-TRK-063.2`)*:
• `RadarAudioSynthesizer` SHALL synthesize low-latency multi-tone PCM audio (2.2–3.2 kHz harmonics) via `AudioTrack` configured with `AudioAttributes.USAGE_MEDIA` (never sonification). Persistent static tracks SHALL use warm replay (`pause()` + `playbackHeadPosition = 0` + `play()`), strictly prohibiting `track.stop()` prior to replay.
3. *Independent Volume & Speaker Override Hardware Boost (`REQ-TRK-063.3`)*:
• The system SHALL provide an independent alert volume (10–100%, default 90%). When routed to the built-in phone speaker (`FORCE_SPEAKER` or `SYSTEM_DEFAULT` when no external wired or Bluetooth headphones are connected) with "Boost Speaker Volume" enabled, the system SHALL temporarily save the current stream volume, boost hardware speaker volume to the target level without system UI popups (`flags = 0`) for the duration of the chime (~300 ms), and restore the saved volume immediately in `finally`, upon cancellation in `stop()`, and service `onDestroy()`.
4. *Transient Audio Ducking (`REQ-TRK-063.4`)*:
• The system SHALL request transient audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) immediately prior to chime playback and abandon focus immediately upon chime completion, allowing background media playback to duck briefly and restore automatically.
5. *Headphone vs. Forced Phone Speaker Routing (`REQ-TRK-063.5`)*:
• The system SHALL configure `AudioTrack.setPreferredDevice(AudioDeviceInfo)`: `TYPE_BUILTIN_SPEAKER` when "Force Phone Speaker" is enabled, or `null` for system default / connected Bluetooth headphones, without placing `AudioManager` in `MODE_IN_COMMUNICATION`.
6. *User Customization & DataStore Integration (`REQ-TRK-063.6`)*:
• `TuningPreferencesDataStore` SHALL persist `radarAudioAlertMode` (`ALL_ALERTS`, `APPROACH_AND_FAST`, `FAST_ONLY`, `MUTED`), `radarAlertVolume` (10..100), `radarSpeakerBoost` (Boolean), `radarAudioRoute` (`SYSTEM_DEFAULT`, `FORCE_SPEAKER`), and `radarHapticAlert` (Boolean), exposed in `RadarSettingsSection.kt`. `RadarAlertManager` SHALL collect `tuningConfigFlow` reactively to eliminate blocking synchronous DataStore queries on 8 Hz telemetry frames, and `RadarAlertManager.stop()` SHALL remove pending `restoreRunnable` callbacks prior to restoring stream volume.
7. *9-Language Localization Parity & Compact Segmented Controls (`REQ-TRK-063.7`)*:
• All audio and alert settings and labels in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis). Audio alert mode `TuningSegmentedButtonRow` labels in `RadarSettingsSection.kt` SHALL use compact phrasing (`radar_alert_mode_all`, `radar_alert_mode_approach_fast`, `radar_alert_mode_fast_only`, `radar_alert_mode_muted`) fitting comfortably on 360–412dp viewports without ellipsizing, backed by dynamic helper text explaining the active alert mode. Half-width audio route `SegmentedButton` labels in `RadarSettingsSection.kt` SHALL use compact phrasing and enforce single-line rendering (`maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`) across all 9 locales (Design Guidelines §5.6 Rule 38).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-TRK-063` (Radar Audio Chime and Haptic Alert Engine), targeting `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `RadarSettingsSection.kt`, and `strings_radar.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-5086` (commit `85790a1b`) and refined in `ATT-6709`; refined in `ATT-6705` for escalation debouncing, `SYSTEM_DEFAULT` speaker boost, reactive caching, and single-line button rendering.
3. *Root Reason for Existing Formulation*: Debounce was added to avoid chime flutter during rapid signal oscillations, but naively overwriting `lastThreatLevel = currentThreat` permanently silenced `< 800 ms` high-speed escalations. `FORCE_SPEAKER` was the only explicit speaker route originally checked, neglecting phone-mounted cyclists on default routing without headphones.
4. *Preservation of Core Invariants*: Full volume boost safety guaranteed; high-speed escalation alert priority guaranteed; 8 Hz non-blocking processing preserved; zero SQLite schema modifications; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Audio Alert Mode set to `ALL_ALERTS`,
• *When* `RadarState` transitions from `NONE` to `APPROACHING`, escalates to `FAST_APPROACHING`, and returns to `NONE`,
• *Then* `RadarAlertManager` plays Approach chime once, Fast-Threat chime on escalation, and All-Clear chime on last vehicle clearance without repeating on 8 Hz range updates.
• *Given* an initial `APPROACHING` alert triggered at `t = 0 ms`,
• *When* a target escalates to `FAST_APPROACHING` at `t = 250 ms` (`< 800 ms`),
• *Then* `RadarAlertManager` immediately emits the urgent Fast-Threat chime and haptic pattern without debounce suppression.
• *Given* phone media volume set low (e.g. 2/15), Speaker Boost enabled, and route set to `SYSTEM_DEFAULT` with no external headphones connected,
• *When* an alert chime plays through the built-in speaker,
• *Then* hardware stream volume boosts to configured Radar Alert Volume during the chime and restores to 2/15 immediately upon completion.
• *Given* active background media playback over Bluetooth headphones and "Force Phone Speaker" enabled,
• *When* an alert chime plays,
• *Then* background media ducks transiently and the chime routes to `TYPE_BUILTIN_SPEAKER` via `setPreferredDevice()`.
• *Given* active alert debouncing in `RadarAlertManager`,
• *When* the system wall clock jumps backwards or forwards by 1 hour (NTP sync / timezone change),
• *Then* debounce cooldowns remain strictly governed by monotonic elapsed time without freezing or sticking.

**Invariants**: Guaranteed volume restoration in `finally`/`onDestroy()`; §3.4 warm static replay (zero `track.stop()`); zero main-thread blocking; zero SQLite schema modifications; 100% 9-language translation parity.

### REQ-CON-030: Garmin Varia BLE V2 High-Resolution Radar Stream & Vehicle Classification [Verified]
- **Tests**: `TST-CON-030`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BTLERadarDevice.java`, `MyBTLEDevice.java`, `DeviceType.java`, `RadarTarget.kt`, `RadarWindowAggregator.kt`, `RadarTrafficSimulator.kt`, `SensorType.java`, `BluetoothConstants.java`, `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `DeveloperMenuScreen.kt`, `strings_radar.xml`
- **Rationale**: Next-generation Garmin Varia radar units (e.g. RearVue 820) introduce a high-resolution 2D BLE telemetry stream on characteristic `6a4e3204-667b-11e3-949a-0800200c9a66` extending radar detection range up to 175 m, providing lateral offset (identifying overtaking trajectory and close passes), and classifying vehicle size (Bikes/Motorcycles vs. Cars vs. Trucks/Busses). The protocol requires LESC bonding, MTU 247 negotiation, and a serialized GATT handshake while enforcing seamless, automatic fallback to legacy V1 characteristic `6a4e3203` on unbonded devices, silent streams, or older hardware (RTL515/516/315/715) without breaking the 3.5s starvation watchdog (ATT-5095, ATT-6706, ATT-7542, Parent Epic ATT-1286, extending `REQ-CON-028` and `REQ-CON-029`):
1. *BLE V2 Handshake Choreography (`REQ-CON-030.1`)*:
• Upon peripheral connection and service discovery, `BTLERadarDevice` SHALL enqueue Battery Level (`0x2A19`) read and CCCD notification subscription *before* proceeding to V2 characteristic `6a4e3204`.
• The system SHALL NEVER subscribe to legacy V1 characteristic `6a4e3203` prior to attempting V2 negotiation, avoiding firmware V1 pinning.
• If characteristic `6a4e3204` is present and the device is bonded (`BluetoothDevice.BOND_BONDED`), `BTLERadarDevice` SHALL enqueue an MTU expansion request to 247 bytes (`RequestMtuOp`) in `mGattQueue`, enable CCCD indications/notifications on `6a4e3204`, and arm a 1500 ms V2 silence-timeout timer upon CCCD write completion.
• `MyBTLEDevice` SHALL delegate measurement characteristic filtering to an overridable `isMeasurementCharacteristic(UUID)` method, enabling `BTLERadarDevice` to route both V1 (`6a4e3203`) and V2 (`6a4e3204`) telemetry packets to `measurementCharacteristicUpdate()`.
2. *Automatic Legacy Fallback Invariant (`REQ-CON-030.2`)*:
• If characteristic `6a4e3204` is absent, or if peripheral is not bonded, or if MTU/handshake negotiation or CCCD descriptor write fails (e.g., non-`GATT_SUCCESS`), or if 0 notifications arrive within 1500 ms of CCCD activation, `BTLERadarDevice` SHALL immediately fall back to subscribing to legacy V1 characteristic `6a4e3203` (`fallbackToV1()`), maintaining 100% backward compatibility with legacy radar devices and preventing silent deadlocks.
• Upon disconnection, `BTLERadarDevice` SHALL reset V2 fallback state so reconnects re-attempt V2 negotiation cleanly.
3. *High-Resolution V2 Packet Parsing & Domain Mapping (`REQ-CON-030.3`)*:
• The system SHALL parse multi-target V2 telemetry frames formatted as `[2-byte LE header] + N * [9-byte target struct]`.
• 2-byte idle heartbeats (`len == 2`, e.g. `02 00` or `02 90`) and 5- or 6-byte device-status frames (header bit 2 `0x0004` set) SHALL reset the 3.5s watchdog, prune stale tracks, and publish `RadarConnectionState.CONNECTED` without emitting spurious targets.
• Multi-target frames (`len >= 11 && (len - 2) % 9 == 0`) SHALL unpack 24-bit little-endian range words into 11-bit signed lateral offset $x$ ($0.1\text{ m}$ precision, positive = right) and 13-bit signed longitudinal distance $y$ ($0.1\text{ m}$ precision behind rider), map `targetClass` (`LARGE = 36` $\to$ `TRUCK_OR_BUS`, `MODERATE = 23 | 26` $\to$ `CAR`, `FAINT = 13 | 16` $\to$ `UNKNOWN_OR_LIGHT`), decode longitudinal velocity ($v = -speedY$, $0.5\text{ m/s}$ resolution), and classify `ThreatLevel` and `LaneState`.
4. *Lateral Passing Clearance & Large Vehicle Aggregation (`REQ-CON-030.4`)*:
• `RadarWindowAggregator` SHALL track minimum lateral passing distance during vehicle overtaking ($y \le 3.0\text{ m}$) and latch `RADAR_PASSING_DISTANCE_m` (in meters) and `RADAR_LARGE_VEHICLE_COUNT` (cumulative count of passed large vehicles) at each 1 Hz tick.
5. *Scalar Sensor Publishing & DeviceType Registration (`REQ-CON-030.5`)*:
• `BTLERadarDevice` SHALL register and publish `SensorType.RADAR_PASSING_DISTANCE_m` and `SensorType.RADAR_LARGE_VEHICLE_COUNT` to `MySensorManager` for live cockpit telemetry and storage.
• `DeviceType.getSensorDeviceTypes()` SHALL map both `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` to `DeviceType.BIKE_RADAR`.
6. *Traffic Simulator V2 Extension (`REQ-CON-030.6`)*:
• `RadarTrafficSimulator` SHALL generate realistic V2 trajectory scenarios including vehicle size diversity, lane positioning, and close-pass overtaking events.
7. *9-Language Localization Parity (`REQ-CON-030.7`)*:
• All sensor names and short labels in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).
8. *Developer BLE V1-Only Protocol Override (`REQ-CON-030.8`)*:
• In debug builds (`BuildConfig.DEBUG == true`), a developer toggle `radar_v1_only_override` (persisted in DataStore/SharedPreferences) SHALL allow developers and QA to disable V2 radar negotiation. When enabled, `BTLERadarDevice.configureServices()` SHALL bypass V2 characteristic discovery and directly invoke `fallbackToV1()`, forcing legacy V1 protocol parsing for testing backward compatibility.
• In release builds (`BuildConfig.DEBUG == false`), the override SHALL be strictly ignored and default to false (V2 negotiation enabled).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-CON-030` (Garmin Varia BLE V2 High-Resolution Radar Stream & Vehicle Classification), targeting `MyBTLEDevice.java`, `BTLERadarDevice.java`, `DeviceType.java`, `ActivityType.java`, and `RadarTrafficSimulator.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-5095` (commit `524e937d`) establishing BLE V2 packet decoding and window aggregation for 2D radar metrics; refined in `ATT-6706`; refined in `ATT-7542` to align with authentic RearVue 820 GATT pre-handshake ordering, 1500 ms silence fallback, and 24-bit packed range `2 + 9*N` wire framing; refined in `ATT-7659` to introduce debug-only Developer Menu V1-only protocol override for backward compatibility QA.
3. *Root Reason for Existing Formulation*: The initial formulation assumed single-characteristic measurement filtering, synthetic 1-byte header `0x01`, and 8-byte target structs with embedded threat bits. Real hardware accepts V2 CCCD writes with `GATT_SUCCESS` even when silent, requiring Battery CCCD prioritization, a 1500 ms silence watchdog fallback to V1, and 24-bit packed range word unpacking.
4. *Preservation of Core Invariants*: Refining `REQ-CON-030` with `isMeasurementCharacteristic(UUID)` extraction, serialized `RequestMtuOp`, LESC bonding verification, 1500 ms fallback, and `DeviceType` registration guarantees 100% backward compatibility for legacy V1 radars (`6a4e3203`), seamless fallback on unbonded/failed handshakes, and full Cockpit UI tile availability without schema migrations.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a connected Garmin Varia BLE peripheral exposing characteristic `6a4e3204`,
• *When* GATT discovery completes,
• *Then* `BTLERadarDevice` prioritizes Battery CCCD, requests MTU 247, completes the V2 handshake, and streams 2D targets with range up to 175 m and lateral offset.
• *Given* a bonded Garmin Varia with silent V2 stream,
• *When* 1500 ms elapse after CCCD write without V2 notifications,
• *Then* `BTLERadarDevice` automatically invokes `fallbackToV1()` subscribing to `6a4e3203`.
• *Given* a legacy Garmin Varia RTL515 exposing only `6a4e3203`,
• *When* GATT discovery completes,
• *Then* `BTLERadarDevice` automatically subscribes to V1 `6a4e3203` without error or connection timeout.
• *Given* a large truck overtaking at 1.2 m lateral clearance,
• *When* the vehicle passes ($y \le 3.0\text{ m}$),
• *Then* `RadarWindowAggregator` latches `RADAR_PASSING_DISTANCE_m` to 1.2 m and increments `RADAR_LARGE_VEHICLE_COUNT`.

**Invariants**: Guaranteed legacy V1 fallback; 3.5s packet starvation watchdog preservation; non-blocking serialized GATT operations; thread safety across 4–8 Hz updates and 1 Hz latching; 100% 9-language translation parity.

### REQ-CON-031: BLE Radar Tail Light Mode Control, Smart Auto Persistence & Quick-Switch Unification [Verified]
- **Tests**: `TST-CON-031`, `TST-CON-047`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BTLERadarDevice.java`, `MyBTLEDevice.java`, `BluetoothConstants.java`, `RadarLightController.kt`, `RadarLightMode.kt`, `RadarEdgeOverlay.kt`, `RadarSettingsSection.kt`, `TuningPreferencesDataStore.kt`, `TrackerService.java`, `RadarLightQuickSwitchSheet.kt`, `EditDeviceDialog.kt`, `strings_radar.xml`, `tools/ble_radar_simulator.py`
- **Rationale**: Garmin Varia radar tail lights (e.g. RearVue 820, RTL515, RTL516) expose tail light mode control. On next-generation RearVue 820 hardware, control operates over dedicated Control Service `6a4e2f00-667b-11e3-949a-0800200c9a66` (write+indicate characteristic `6a4e2f11-667b-11e3-949a-0800200c9a66`, notify characteristic `6a4e2f14-667b-11e3-949a-0800200c9a66`), while legacy units expose characteristic `6a4e3202-667b-11e3-949a-0800200c9a66` on service `6a4e3200-667b-11e3-949a-0800200c9a66`. Cyclists require on-the-fly handlebar switching of tail light modes without reaching behind the saddle, bidirectional sync of physical device button presses, unified preference persistence remembering active operating state (Automatik vs. explicit mode) across rides, seamless transition without trapping automation, and automated workout start/finish lighting control to preserve peripheral battery and prevent riding without active lighting (ATT-5102, ATT-6707, ATT-7545, ATT-7540, ATT-7538, ATT-7991, ATT-7737, Parent Epic ATT-1286, extending `REQ-CON-028`, `REQ-CON-029`, and `REQ-CON-030`):
1. *BLE Light Control Discovery, `6a4e2f11` CCCD Handshake, StVZO Detection & Serialized Queue Routing (`REQ-CON-031.1`)*:
• Upon service discovery on `BTLERadarDevice`, the system SHALL discover dedicated Control Service `6a4e2f00` (characteristics `6a4e2f11` for `(WRITE, INDICATE)` control and `6a4e2f14` for notifications) if present; if absent, it SHALL fall back to discovering legacy characteristic `6a4e3202` on service `6a4e3200`.
• When service `6a4e2f00` is present, `BTLERadarDevice` SHALL register light control capabilities, enable local notifications and subscribe to `0x2902` CCCD on **`6a4e2f11`** (`ENABLE_INDICATION_VALUE` if `PROPERTY_INDICATE` else `ENABLE_NOTIFICATION_VALUE`) *before* dispatching any control writes, and subscribe to `6a4e2f14` CCCD notifications.
• The device SHALL discover Device Information service `0x180A` and read `0x2A24` (Model Number string); if the model string corresponds to StVZO hardware (e.g. `010-03180-10`, `RTL516`), `isStvzo` SHALL be set to `true`.
• On connect or reconnect, `BTLERadarDevice` SHALL read/subscribe `6a4e2f11` and `6a4e2f14` (or legacy `6a4e3202`) to reflect physical light state, handle `6a4e2f11` indication/response frames in `onOtherCharacteristicUpdate()`, and re-apply the active workout light mode upon mid-ride reconnect strictly after `6a4e2f11` CCCD subscription is queued.
2. *Property-Aware Tail Light Mode Command Mapping, Write Status Propagation, StVZO Pruning & Gated Mutation (`REQ-CON-031.2`)*:
• The system SHALL support `RadarLightMode` states (`OFF`, `DAY_FLASH`, `NIGHT_FLASH`, `SOLID`, `PELOTON`).
• When `6a4e2f11` is active, mode changes SHALL dispatch a 4-byte set-mode override command `06 09 01 TT` where `TT` is `0x11` (SOLID), `0x12` (PELOTON), `0x13` (DAY_FLASH), `0x14` (NIGHT_FLASH), `0x1F` (OFF). Both `BTLERadarDevice` and `MyBTLEDevice.WriteCharacteristicOp` SHALL select write type strictly from characteristic properties: `WRITE_TYPE_DEFAULT` (GATT Write Request, preserving SMP `GATT_INSUFFICIENT_AUTHENTICATION` bonding elevation) whenever `(properties & PROPERTY_WRITE) != 0`, and `WRITE_TYPE_NO_RESPONSE` ONLY when `(properties & PROPERTY_WRITE) == 0 && (properties & PROPERTY_WRITE_NO_RESPONSE) != 0` (such as legacy `6a4e3202`).
• When legacy `6a4e3202` is active, mode changes SHALL dispatch a 1-byte command `mode.getCommandByte()`.
• `MyBTLEDevice.onCharacteristicWrite()` SHALL propagate `(gatt, characteristic, status)` to `BTLERadarDevice` via a subclass hook (`onCharacteristicWriteCompleted`). On `status != BluetoothGatt.GATT_SUCCESS`, `BTLERadarDevice` SHALL log the failure status, verify/initiate bonding if `status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION`, and reconcile/roll back optimistic `mLastConfiguredLightMode` and `RadarLightController` state cleanly rather than silently masking write rejection until `6a4e2f14` snaps back to `OFF`.
• Physical button press notifications and status frames on `6a4e2f14` formatted as 4-byte frames `01 [slot] ff [type]` SHALL be decoded (`0x11` SOLID, `0x12` PELOTON, `0x13` DAY_FLASH, `0x14` NIGHT_FLASH, `0x1F` OFF, `0x01` custom/SOLID) and synchronized with `RadarLightController.updateHardwareLightMode(mode)` while preserving confirmed/optimistic user selection during verified hardware transitions.
• When connected to StVZO-certified hardware, flashing modes (`DAY_FLASH`, `NIGHT_FLASH`, `PELOTON`) SHALL be pruned automatically from user selection, exposing only legal steady modes (`SOLID`, `OFF`). `RadarLightController.setLightMode()` SHALL mutate `_currentMode` and `RadarStateRepository` strictly when `dispatched == true` (or confirmed by GATT).
3. *Cockpit Quick-Switch Bottom Sheet & Automatik Selection (`REQ-CON-031.3`)*:
• Tapping the radar status badge in `RadarEdgeOverlay` SHALL display a modal quick-switch bottom sheet (`RadarLightQuickSwitchSheet.kt`) presenting `Automatik` (`R.string.radar_light_control_mode_smart`) as the first selectable option followed by available physical light modes, with live active state indication for both the user selection (`isAutoMode`) and the live underlying mode (`currentMode`).
• Selecting `Automatik` SHALL clear manual override, activate `SmartRadarLightEngine` evaluation, and persist `isAutoMode = true`. Selecting an explicit physical mode SHALL set `isAutoMode = false`, persist the mode to `radarLightDefaultMode`, and dispatch the mode to hardware without permanently trapping the user in manual mode.
• On radar devices without light control, the sheet SHALL present only audio snooze controls. Inactive mode indicators SHALL use `MaterialTheme.colorScheme.outline`, accompanied by Light, Dark, and StVZO `@Preview` composables.
4. *Workout Lifecycle Lighting Automation & Safety Pause Preservation (`REQ-CON-031.4`)*:
• `TrackerService` and `RadarLightController` SHALL govern tail light activation via persisted user selection (`isAutoMode` and `radarLightDefaultMode`):
  - In pre-workout `READY` or active `TRACKING`, mode selections persist immediately to `TuningPreferencesDataStore`.
  - On cycling workout start/resume (`onWorkoutStarted`, `onWorkoutResumed`), the system SHALL automatically activate the persisted selection: when `isAutoMode == true`, it SHALL start `SmartRadarLightEngine` without dispatching conflicting fixed modes; when `isAutoMode == false`, it SHALL command `radarLightDefaultMode` (if not `OFF`).
• During workout **Pause**, the system SHALL strictly preserve active tail light illumination and NEVER extinguish the rear safety light.
• On explicit workout finish/stop (`onWorkoutStopped` / `handleActionStop`), the system SHALL command `RadarLightMode.OFF` synchronously *before* unbinding `BANALService`, stopping sensor listeners and disconnecting light **without overwriting** or erasing the user's persisted lighting preference for subsequent workouts.
• `RadarLightController` SHALL use synchronously cached preferences without spawning unstructured background coroutines or accessing `context.filesDir` (Coding Standards §5.5).
5. *Unified Preference Persistence, Migration, Expert Settings Retirement & EditDeviceDialog UI Consistency (`REQ-CON-031.5`)*:
• `TuningPreferencesDataStore` SHALL persist radar tail light preference as a unified selection model: `isAutoMode: Boolean` (defaulting to `true` on fresh installations) and `radarLightDefaultMode: RadarLightMode` (defaulting to `DAY_FLASH`), with reactive flows `radarLightIsAutoFlow` and `radarLightDefaultModeFlow`.
• DataStore deserialization SHALL seamlessly migrate legacy preferences: legacy `SMART_AUTOMATION` maps to `isAutoMode = true`; legacy `FIXED_WORKOUT_MODE` maps to `isAutoMode = false` with existing `radarLightDefaultMode` preserved; legacy `MANUAL` maps to `isAutoMode = false` with `radarLightDefaultMode = OFF` (or last explicit mode).
• In compliance with Design Guidelines §3.2 (single unambiguous configuration surface), `RadarSettingsSection.kt` SHALL retire the redundant 3-way `Rücklicht-Steuerung` segmented button row and conditional `Lichtmodus` dropdown, while strictly retaining the `Bremslicht-Erkennung` (`radarBrakeLightEnabled`) toggle.
• `EditDeviceDialog.kt` (`RadarLightControlSection`) SHALL provide parity with `RadarLightQuickSwitchSheet.kt`, exposing `Automatik` as the first selectable option alongside physical modes with live active state indication bound consistently to `RadarLightController.isAutoMode` and `RadarLightController.currentMode` via `collectAsStateWithLifecycle()`, dispatching selection immediately to `RadarLightController` without dirtying device draft state and reflecting active hardware state upon dialog close and reopen.
6. *Traffic Simulator & BLE Peripheral Simulator Light State Fidelity (`REQ-CON-031.6`)*:
• `RadarTrafficSimulator` SHALL emulate tail light command reception and state transitions for UI testing without physical peripheral hardware.
• `tools/ble_radar_simulator.py` (`RadarControlWriteCharacteristic` on `6a4e2f11`) SHALL faithfully expose GATT flags `['write', 'indicate']` (never `write-without-response`) and enforce `6a4e2f11` CCCD subscription before accepting control writes, preventing simulator tests from masking `WRITE_TYPE_NO_RESPONSE` or missing-CCCD defects.
7. *9-Language Localization Parity (`REQ-CON-031.7`)*:
• All light mode names, dialog titles, and settings strings in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).
8. *StVZO-Aware Debounced Hardware Mode Cycling & Reactive State Separation (`REQ-CON-031.8`)*:
• `RadarLightController` and `RadarState` SHALL expose two distinct reactive `StateFlow`s:
  1. `isAutoMode: StateFlow<Boolean>` representing whether `Automatik` is the active user selection.
  2. `currentMode: StateFlow<RadarLightMode>` representing the live physical operating mode on the tail light (reflecting `SmartRadarLightEngine` escalations and transient brake overrides).
• `RadarLightController.cycleNextMode()` SHALL step through valid modes in ascending intensity order matching `RadarLightMode.getAvailableModes()`:
  - Non-StVZO: `AUTO` -> `OFF` -> `PELOTON` -> `SOLID` -> `NIGHT_FLASH` -> `DAY_FLASH` -> `AUTO`.
  - StVZO (`isStvzo == true`): `AUTO` -> `OFF` -> `SOLID` -> `AUTO`.
• `cycleNextMode()` SHALL update UI-observable target state immediately on each invocation, while debouncing outbound hardware commands (BLE GATT / ANT+ `0x23`) over a ~400 ms window to coalesce rapid multi-tap sequences and protect the peripheral GATT queue.

**Acceptance Criteria (Given-When-Then)**:
• *Criterion 1 (Workout Start/Stop Lifecycle & Persistence)*:
  - *Given* the cyclist selects `Tag-Blinken` (or `Automatik`) via Cockpit sheet or light control,
  - *When* the cycling workout ends and a new cycling workout is started later,
  - *Then* the physical tail light turns `OFF` upon workout stop, and automatically resumes `Tag-Blinken` (or `Automatik`) when the next cycling workout starts without preference erasure.
• *Criterion 2 (Debounced Mode Cycling & StVZO Filtering)*:
  - *Given* a connected non-StVZO radar light in `Automatik` mode,
  - *When* `cycleNextMode()` is invoked 3 times within 250 ms (`AUTO` -> `OFF` -> `PELOTON` -> `SOLID`),
  - *Then* UI state updates immediately on each step, and only a single hardware command (`SOLID`) is transmitted after the ~400 ms debounce window expires (and on StVZO hardware, non-StVZO flashing modes are skipped).
• *Criterion 3 (Live Status Separation in Auto Mode)*:
  - *Given* `Automatik` is selected,
  - *When* `SmartRadarLightEngine` escalates from `OFF` to `SOLID` or `DAY_FLASH` due to approaching traffic or tunnel entry,
  - *Then* `isAutoMode` remains `true` while `currentMode` reactively emits the active physical `RadarLightMode`.
• *Criterion 4 (Expert Settings Cleanup & Quick-Switch / EditDevice Parity)*:
  - *Given* the user opens Expert Settings -> Bike Radar or opens `RadarLightQuickSwitchSheet` / `EditDeviceDialog`,
  - *When* inspecting the Tail Light section,
  - *Then* the legacy 3-way mode selector and dropdown are absent from Expert Settings, `Bremslicht-Erkennung` remains intact, and `Automatik` is directly selectable in both `RadarLightQuickSwitchSheet` and `EditDeviceDialog`.
• *Criterion 5 (`6a4e2f11` CCCD Handshake, Property-Aware Write Type & Write Status Reconciliation)*:
  - *Given* a connected RearVue 820 radar exposing `6a4e2f11` with `(PROPERTY_WRITE | PROPERTY_INDICATE)`,
  - *When* `configureServices()` runs and the user selects `NIGHT_FLASH` in `EditDeviceDialog`,
  - *Then* `6a4e2f11` CCCD (`ENABLE_INDICATION_VALUE`) is queued before any `setLightMode` write, the command transmits with `WRITE_TYPE_DEFAULT`, `MyBTLEDevice.onCharacteristicWrite()` propagates GATT status (`GATT_SUCCESS` confirms while non-zero status logs/reconciles optimistic state and handles `GATT_INSUFFICIENT_AUTHENTICATION`), `EditDeviceDialog` reflects `NIGHT_FLASH` upon close/reopen, and `tools/ble_radar_simulator.py` enforces `['write', 'indicate']` + CCCD subscription.

**Invariants**: Non-blocking serialized GATT writes (`6a4e2f11` CCCD before initial `setLightMode`); ~400 ms command debouncing (zero GATT queue flooding); legacy `6a4e3202` `WRITE_TYPE_NO_RESPONSE` preservation; StVZO regulatory compliance; safety illumination during pause stops; fail-safe degradation on radar-only hardware; zero UI layout changes in `EditDeviceDialog.kt` / `RadarSettingsSection.kt`; `ANTBikeLightDevice.kt` untouched; zero SQLite schema modifications; zero MockK crashes in unit tests; 100% 9-language translation parity.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-031.1`, `REQ-CON-031.2`, `REQ-CON-031.3`, `REQ-CON-031.5`, `REQ-CON-031.6`, and `REQ-CON-031.8`, targeting `BTLERadarDevice.java`, `MyBTLEDevice.java`, `RadarLightController.kt`, `RadarLightMode.kt`, `RadarLightQuickSwitchSheet.kt`, `EditDeviceDialog.kt`, and `tools/ble_radar_simulator.py`.
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.1 via `ATT-5102` (`c1d27049`), refined in `ATT-6707` (`fe09f672`), `ATT-7545` (`6bfac996`), `ATT-7540`, `ATT-7538` (`b645651f`), `ATT-7991` (`c422e1b6`), `ATT-7737` (`60e14c9b`), and `ATT-8456` (ascending intensity mode order).
3. *Root Reason for Existing Formulation*: `c1d27049` listed `RadarLightMode` enum values in arbitrary order (`OFF, DAY_FLASH, NIGHT_FLASH, SOLID, PELOTON`), and `c422e1b6` added `cycleNextMode()` with `DAY_FLASH -> SOLID -> PELOTON -> NIGHT_FLASH -> OFF`; unifying both to ascending intensity (`OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH`) eliminates sheet-vs-tap cognitive dissonance. Meanwhile `60e14c9b` established `6a4e2f11` CCCD + `WRITE_TYPE_DEFAULT` + `onCharacteristicWrite()` reconciliation.
4. *Preservation of Core Invariants*: `Automatik` remains first; StVZO pruning strictly removes flashing modes (`OFF`, `SOLID` only); `TuningPreferencesDataStore` persists `.name` (never `.ordinal`); legacy `6a4e3202` `WRITE_TYPE_NO_RESPONSE`, ~400 ms debounce, `commandByte` wire mappings, `ANTBikeLightDevice.kt` path, and 9-language parity remain intact.

### REQ-CON-032: ANT+ Cycling Rearview Radar Profile (Device Type 0x28) Acquisition & Target Demuxing [Verified]
- **Tests**: `TST-CON-032`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ANTBikeRadarDevice.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`, `DeviceManager.java`, `DeviceType.java`, `RadarStateRepository.kt`, `RadarState.kt`, `RadarTarget.kt`, `app/build.gradle`
- **Rationale**: Legacy cycling rearview radars (Garmin Varia RTL500, RTL510) and multiple third-party radars (Sigma RECO 80/81 LINK, Bryton Gardia R300L, Magene L508, Trek CarBack) transmit target telemetry over the standardized ANT+ Bike Radar profile (Device Type 40 / 0x28). Since `antpluginlib_3-9-0.aar` predates Device Type 40 and omits it from PCC drivers and `MultiDeviceSearch`, the system must link low-level `android_antlib_4-16-0.aar`, acquire an ANT slave channel on predefined network slot 1 (`ANT_PLUS`), decode Data Pages 0x30/0x31/0x01/0x50/0x51/0x52, and route demuxed multi-target frames into the unified `RadarStateRepository` and `RadarWindowAggregator` pipeline (ATT-5084, Parent Epic ATT-1286, extending `REQ-CON-001`, `REQ-CON-004`, and `REQ-CON-028`):
1. *SDK Dependency & Predefined ANT+ Network Channel Acquisition (`REQ-CON-032.1`)*:
• The application build configuration SHALL link `android_antlib_4-16-0.aar` alongside `antpluginlib_3-9-0.aar`. When initializing an ANT+ radar connection, `ANTBikeRadarDevice` SHALL bind `AntService` and acquire a channel via `AntChannelProvider` on Predefined Network slot 1 (`ANT_PLUS`), avoiding raw network key rejection or RF 57 frequency bounds rejection by `Android_ANTRadioService`.
2. *Slave Channel Parameters & RF Kinematics (`REQ-CON-032.2`)*:
• `ANTBikeRadarDevice` SHALL configure the acquired channel with Channel Type `ChannelType.BIDIRECTIONAL_SLAVE`, Device Type `40` (`0x28`), Transmission Type `0` (wildcard pairing or paired transmission type), RF Frequency `57` (2457 MHz), and Channel Period `4084` counts (~8.02 Hz broadcast rate).
3. *Data Page 0x30 & 0x31 Multi-Target Bit Demuxing (`REQ-CON-032.3`)*:
• Upon receiving ANT broadcast event frames, `ANTBikeRadarDevice` SHALL unpack multi-target payload pages:
  - Data Page `0x30`: Targets 1–4 (Target IDs 0–3).
  - Data Page `0x31`: Targets 5–8 (Target IDs 4–7).
  For each target index $i \in [0..3]$ within the page, 2-bit Threat Level SHALL be extracted as `(byte1 >> (i * 2)) & 0x03`, 2-bit Threat Side SHALL be extracted as `(byte2 >> (i * 2)) & 0x03`, 6-bit Range SHALL be extracted from bytes 3–5 (`((ranges >> (i * 6)) & 0x3F) * 3.125f` meters, range 0..196.875 m), and 4-bit Closing Speed SHALL be extracted from bytes 6–7 (`((speeds >> (i * 4)) & 0x0F) * 3.04f` m/s, range 0..45.6 m/s). Valid targets (Threat Level > 0 and Range > 0) SHALL be assembled into immutable `RadarTarget` entities and published via `RadarStateRepository.updateState()`.
4. *Data Page 0x01 System State & Common Pages 0x50/0x51/0x52 Telemetry (`REQ-CON-032.4`)*:
• `ANTBikeRadarDevice` SHALL decode Data Page `0x01` (Radar System Status: 0 = Normal, 1 = Limited, 2 = Error) and map errors to `RadarThreatLevel.DISCONNECTED_ERROR` when indicated. Common pages `0x50` (Manufacturer ID), `0x51` (Model & HW Revision), and `0x52` (Battery Status & Operating Time) SHALL be decoded to update device metadata and battery status without throwing exceptions on unrecognized sub-pages.
5. *Wildcard Discovery & Device Pairing Integration (`REQ-CON-032.5`)*:
• `DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)` SHALL include `DeviceType.BIKE_RADAR`. `ANTSearchForNewDevicesEngineMultiDeviceSearch` SHALL open a wildcard `AntChannel` scan (`DeviceNumber = 0`, `DeviceType = 40`, `TransType = 0`) on Predefined Network slot 1 with `LibConfig` enabled (extended device number and RSSI) when searching for `DeviceType.BIKE_RADAR` or `DeviceType.ALL`, reporting discovered radar peripherals to `DeviceManager`.
6. *Starvation Watchdog & Hardware Preemption (`REQ-CON-032.6`)*:
• `ANTBikeRadarDevice` SHALL maintain a 3.5s starvation watchdog timer. If no valid ANT broadcast frame is received within 3.5 seconds, the state SHALL transition to disconnected error (`RadarState.DISCONNECTED_ERROR`), resetting active scalar sensor telemetry to clear state. Reception of valid ANT+ radar frames SHALL call `RadarTrafficSimulator.onPhysicalHardwareConnected()` to preempt any active simulation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `android_antlib_4-16-0.aar` linked and an ANT+-capable device,
• *When* the user pairs and connects an ANT+ bike radar unit (Device Type 40),
• *Then* `ANTBikeRadarDevice` acquires an `AntChannel` on Predefined Network slot 1 with RF 57 and period 4084 counts without service exceptions.
• *Given* raw ANT+ broadcast payloads for Page 0x30 (Targets 1–4) and Page 0x31 (Targets 5–8),
• *When* decoded by `ANTBikeRadarDevice`,
• *Then* target ranges (scaled by 3.125 m), closing speeds (scaled by 3.04 m/s), threat levels, and threat sides for up to 8 targets are unpacked accurately into `RadarTarget` records and emitted to `RadarStateRepository`.
• *Given* an active ANT+ radar connection,
• *When* broadcast packets cease for $\ge 3.5\text{ s}$,
• *Then* the system transitions to disconnected state and clears active threat overlays.

**Invariants**: Predefined Network Slot 1 isolation; coexistence with legacy `antpluginlib_3-9-0.aar` PCC devices (HR, Speed, Cadence, Power); non-blocking background AIDL message handling; fail-safe 3.5s disconnect watchdog; zero SQLite schema migrations; 100% test pass rate.

### REQ-CON-033: ANT+ Bike Lights Profile (Device Type 0x23) Companion Channel, Connect Handshake & Tail Light Mode Control [Verified]
- **Tests**: `TST-CON-033`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `ANTBikeLightDevice.kt`, `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt`, `RadarLightController.kt`, `RadarLightDeviceClient.kt`, `RadarLightMode.kt`
- **Rationale**: Cycling rearview radar tail lights (Garmin Varia RTL515, RTL516, RearVue 820, Magene L508, Bryton Gardia R300L) broadcast radar telemetry over the ANT+ Bike Radar profile (`0x28`), but expose light control and battery status exclusively via the standardized ANT+ Bike Lights profile (Device Type 35 / `0x23`). Cyclists require handlebar mode switching (Day Flash, Night Flash, Solid, Peloton, Off) and automated workout start/stop light control without discovering and pairing duplicate sensors in the database. The system must establish an automated companion channel bound to the paired radar, execute the mandatory 3-step handshake (Data Page `0x21` Connect Command assigning `Light Index = 1`, Data Page `0x22` Mode Command with sequence indexing, and Data Page `0x20` Disconnect Command), and integrate seamlessly into `RadarLightController` (ATT-5103, Parent Epic ATT-1286, extending `REQ-CON-001`, `REQ-CON-031`, and `REQ-CON-032`):
1. *ANT+ Bike Lights Channel Acquisition & Companion Lifecycle (`REQ-CON-033.1`)*:
• `AntChannelHelper` SHALL define Bike Lights constants (`BIKE_LIGHT_DEVICE_TYPE = 35` / `0x23`, `BIKE_LIGHT_CHANNEL_PERIOD = 4084` counts / ~8.02 Hz).
• When an `ANTBikeRadarDevice` connects, it SHALL automatically instantiate and bind an `ANTBikeLightDevice` companion channel on Predefined Network slot 1 (`ANT_PLUS`), RF frequency `57`, Channel Type `BIDIRECTIONAL_SLAVE`, matching the radar's device number.
• Fallback to wildcard search (`ChannelId(0, 35, 0)`) SHALL be triggered strictly upon `EventCode.RX_SEARCH_TIMEOUT` or `EventCode.CHANNEL_CLOSED` (never on non-timeout events), and the active channel SHALL be closed before reconfiguring channel ID.
• During wildcard search, pairing and connection SHALL be restricted to matching the radar's `antDeviceNumber` and/or verified `manufacturerId` (Page `0x50`), preventing hijacking of third-party lights in group rides.
• The companion light channel SHALL NOT create a duplicate device entry in `DevicesDatabaseManager`. On radar teardown or disconnect, the companion channel SHALL release cleanly.
2. *Broadcast Decoding: Page 0x01 (Main Light State) & Page 0x02 (Light Capabilities) (`REQ-CON-033.2`)*:
• `ANTBikeLightDevice` SHALL decode Data Page `0x01`: Light Index (`0` = unassociated, `1..63` = assigned index), Light Type (`2` = Tail Light), current Beam/Flash mode (`0x00`=Off, `0x01`=Solid High, `0x05`=Peloton/Solid Low, `0x06`=Night Flash/Slow Flash, `0x07`=Day Flash/Fast Flash), and battery status.
• `ANTBikeLightDevice` SHALL decode Data Page `0x02`: bitmask of supported light modes.
• Discovered capabilities SHALL update `RadarLightController` and `RadarStateRepository`.
3. *Stateful 3-Step Handshake: Connect (Page 0x21), Mode Control (Page 0x22) & Disconnect (Page 0x20) (`REQ-CON-033.3`)*:
• *Step 1 (Connect Handshake, Page `0x21`)*: When Data Page `0x01` reports `Light Index == 0` (unassociated), `ANTBikeLightDevice` SHALL transmit an acknowledged Data Page `0x21` message assigning `Light Index = 1`, phone-derived Controller ID, and Light Type `2` (Tail Light). Mode setting commands SHALL NOT be transmitted until Page `0x01` confirms `Light Index == 1`.
• *Step 2 (Mode Settings Command, Page `0x22`)*: Upon receiving confirmation that `Light Index == 1`, `ANTBikeLightDevice` SHALL transmit acknowledged Data Page `0x22` containing `Light Index = 1`, incrementing sequence number, and the target mode byte mapped from `RadarLightMode` (`OFF = 0x00`, `SOLID = 0x01`, `PELOTON = 0x05`, `NIGHT_FLASH = 0x06`, `DAY_FLASH = 0x07`). Applied mode state SHALL NOT update on initial queueing; confirmation SHALL be gated on radio ACK (`EventCode.TRANSFER_TX_COMPLETED`) or Page `0x01` broadcast.
• *Step 3 (Disconnect Release, Page `0x20`)*: Upon sensor teardown or workout finalization, `ANTBikeLightDevice` SHALL transmit acknowledged Data Page `0x20` with `Light Index = 1` and Controller ID to release the peripheral back to unassociated state.
4. *Unified `RadarLightController` Abstraction (`REQ-CON-033.4`)*:
• `RadarLightController` SHALL decouple from concrete `BTLERadarDevice` via a common interface (`RadarLightDeviceClient`).
• Both BLE (`BTLERadarDevice`) and ANT+ (`ANTBikeLightDevice`) SHALL register as active light clients, ensuring identical operation for Cockpit quick-switch bottom sheets, workout auto-switch (`ATT-5102`), and traffic alerts.
5. *Fail-Safe Channel Isolation & Radar Telemetry Non-Interference (`REQ-CON-033.5`)*:
• Acknowledged transmissions, retries, or companion channel unavailability (e.g. radar-only models such as Garmin Varia RVR315) SHALL execute asynchronously and SHALL NOT block or degrade `ANTBikeRadarDevice` radar target parsing (Pages `0x30`/`0x31`) or the 3.5s starvation watchdog.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active ANT+ radar whose companion channel initially broadcasts Data Page `0x01` with `Light Index = 0`,
• *When* the user or auto-light policy requests a light mode transition (`Day Flash`, `Peloton`, `Solid`, `Off`),
• *Then* `ANTBikeLightDevice` transmits Data Page `0x21` to establish association (`Light Index = 1`), and upon confirmation transmits Data Page `0x22` with the desired mode byte.
• *Given* an ANT+ radar tail light connected during a workout,
• *When* the workout starts and finishes with auto-light control enabled,
• *Then* the light automatically turns on at workout start and turns off/disconnects (`Page 0x20`) at workout finish without duplicate sensor entries in the database.
• *Given* a radar-only unit (e.g. Garmin Varia RVR315) with no companion light channel,
• *When* light connection times out or fails,
• *Then* radar target tracking proceeds uninterrupted, setting `isLightControlSupported = false`.

**Invariants**: Predefined Network slot 1 isolation; non-blocking asynchronous ACK transmission; zero database migrations or duplicate device rows; radar target telemetry and starvation watchdog independence; seamless UI parity across BLE and ANT+.

### REQ-CON-034: Environmental & Traffic-Adaptive Smart Tail Light Mode Automation [Verified]
- **Tests**: `TST-CON-034`
- **System Requirement(s)**: `SYSREQ-TRK-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SmartRadarLightEngine.kt`, `SolarElevationCalculator.kt`, `RadarLightController.kt`, `RadarLightMode.kt`, `RadarStateRepository.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Cyclists need their radar tail light to adapt dynamically to lighting conditions (daylight, tunnels, dusk, night) and approaching traffic threats without manual distraction. At night, lights must alert drivers without blinding them (anti-glare cap preventing 100 lm flash), while tunnels demand rapid activation (< 2s) from daytime modes. Ambient sensors on smartphones in jersey pockets or with screens turned off experience total darkness and must not falsely trigger tunnel modes. The system must establish a robust smart lighting engine utilizing pocket/screen-off sensor gating, astronomical solar elevation fallback, 3-stage threat escalation, and $\ge 1.5\text{ s}$ command rate-limiting (ATT-5104, Parent Epic ATT-1286, extending `REQ-CON-031`, `REQ-CON-032`, and `REQ-CON-033`):
1. *Sensor Gating & Pocket/Screen-Off Protection (`REQ-CON-034.1`)*:
• Ambient illuminance from `Sensor.TYPE_LIGHT` SHALL be processed ONLY when `PowerManager.isInteractive` is `true` (screen ON) AND `Sensor.TYPE_PROXIMITY` indicates `FAR` (or unoccluded).
• When the screen is OFF or proximity indicates `NEAR` (e.g. phone stowed in a jersey pocket or bag), ambient light values SHALL be ignored, and environmental profile determination SHALL fall back to astronomical solar elevation.
2. *Astronomical Solar Elevation Fallback (`REQ-CON-034.2`)*:
• `SolarElevationCalculator` SHALL compute the solar elevation angle $\alpha$ based on current GPS coordinates (`LATITUDE`, `LONGITUDE`) and epoch timestamp.
• The system SHALL classify solar elevation as `DAY` when $\alpha \ge -3.0^\circ$ and `DUSK_NIGHT` when $\alpha < -3.0^\circ$.
3. *Fast Tunnel Detection & Illuminance Hysteresis (`REQ-CON-034.3`)*:
• When sensor gating permits, a sudden drop in illuminance from $> 1000\text{ lx}$ to $< 50\text{ lx}$ sustained for $\ge 1.5\text{ s}$ SHALL immediately trigger tunnel mode (`SOLID` or `NIGHT_FLASH`), activating within $2\text{ s}$.
• Recovery back to daytime profile SHALL require illuminance $> 300\text{ lx}$ sustained for a $10\text{ s}$ hysteresis window.
4. *3-Stage Threat Escalation Matrix & Strict Anti-Glare Cap (`REQ-CON-034.4`)*:
• *Night / Dusk Profile*:
  - Stage 0 (No threat / `NONE`): `PELOTON` (8 lm, non-dazzling).
  - Stage 1 (Normal approach / `APPROACHING`): `SOLID` (25 lm).
  - Stage 2 (High threat / `FAST_APPROACHING` or vehicle `IN_RIDER_LANE` with low clearance): `NIGHT_FLASH` (40 lm, strictly capped; `DAY_FLASH` 100 lm is strictly prohibited at night to prevent blinding approaching motorists).
• *Daytime Profile*:
  - Stage 0: `OFF` (or `PELOTON`).
  - Stage 1: `SOLID` or `DAY_FLASH`.
  - Stage 2: `DAY_FLASH` (100 lm).
5. *Command Rate-Limiting & Post-Pass Cooldown Hysteresis (`REQ-CON-034.5`)*:
• Dispatched mode transitions via `RadarLightController` SHALL enforce a minimum interval of $\ge 1.5\text{ s}$ between hardware writes to protect BLE and ANT+ message queues.
• Following threat de-escalation back to `NONE`, the engine SHALL enforce a $3\text{--}5\text{ s}$ cooldown hysteresis before stepping down to Stage 0.
6. *Settings Preference, Mode Integration & 9-Language Localization Parity (`REQ-CON-034.6`)*:
• Smart light automation SHALL be integrated into the unified `RadarLightControlMode.SMART_AUTOMATION` setting in `TuningPreferencesDataStore`. Legacy preference `radarSmartLightAutomation` SHALL be migrated seamlessly and retained for downgrade compatibility.
• `RadarSettingsSection.kt` and mode descriptions SHALL maintain 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Smart Tail Light Automation enabled during daytime,
• *When* ambient light drops from $3000\text{ lx}$ to $20\text{ lx}$ while screen is ON and proximity is FAR (tunnel entry),
• *Then* `SmartRadarLightEngine` switches the tail light to tunnel mode within $2\text{ s}$, and recovers to daytime mode $10\text{ s}$ after exiting into sunlight.
• *Given* the phone stowed in a dark jersey pocket ($0\text{ lx}$) during daytime ($\alpha \ge -3.0^\circ$),
• *When* proximity is NEAR or screen is OFF,
• *Then* `SmartRadarLightEngine` ignores the $0\text{ lx}$ reading and maintains the daytime lighting profile.
• *Given* the Night/Dusk profile active at Stage 0 (`PELOTON`),
• *When* a fast-approaching vehicle is detected (`FAST_APPROACHING`),
• *Then* the light escalates to `NIGHT_FLASH` (40 lm) and never to `DAY_FLASH` (100 lm), enforcing a $\ge 1.5\text{ s}$ interval between mode changes.

**Invariants**: Strict night anti-glare cap; pocket and screen-off false trigger immunity; $\ge 1.5\text{ s}$ command rate-limiting; StVZO mode pruning compliance; zero SQLite migrations.

### REQ-CON-035: Smartphone IMU/GPS Deceleration Brake-Light Trigger for BLE & ANT+ (0x23) Radar Tail Lights [Verified]
- **Tests**: `TST-CON-035`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SmartBrakeLightDetector.kt`, `RadarLightController.kt`, `RadarLightMode.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Cycling radar tail lights lacking internal brake sensors (e.g. Garmin Varia RTL515, RTL516, RTL510, Bryton Gardia R300L, Magene L508) cannot warn approaching traffic or group ride partners during emergency or sudden braking. Smartphone devices mounted on handlebars or carried during rides feature high-frequency IMU sensors (`Sensor.TYPE_LINEAR_ACCELERATION`, `Sensor.TYPE_GRAVITY`) and GPS/wheel-speed telemetry. The system provides an automated software brake-light trigger that fuses linear deceleration and attitude compensation, rejects road shocks/potholes, immediately overrides tail light rate-limiting with high-visibility illumination (`SOLID` or `DAY_FLASH`), preserves a 2.0s hold window, and automatically bypasses when radar hardware with onboard brake sensing (Garmin Varia RearVue 820) is detected (ATT-5121, Parent Epic ATT-1286, extending `REQ-CON-031`, `REQ-CON-033`, and `REQ-CON-034`):
1. *Sensor Fusion Deceleration Trigger & Sustained Threshold (`REQ-CON-035.1`)*:
• `SmartBrakeLightDetector` SHALL process 3-axis linear acceleration from `Sensor.TYPE_LINEAR_ACCELERATION` ($\vec{a}_{\text{lin}}$) attitude-compensated using `Sensor.TYPE_GRAVITY` ($\hat{g}$), projecting forward longitudinal deceleration $a_{\text{long}} = \vec{a}_{\text{lin}} \cdot \hat{f}$ along the travel axis.
• A Brake Event SHALL be triggered when forward deceleration $a \le -2.0\text{ m/s}^2$ is sustained continuously for $\ge 250\text{ ms}$ while current riding speed $v \ge 2.5\text{ m/s}$ ($9\text{ km/h}$) and speed derivative $dv/dt \le 0$.
2. *Pothole & Vertical Shock Rejection (`REQ-CON-035.2`)*:
• High-frequency vertical acceleration spikes ($|a_v| = |\vec{a}_{\text{lin}} \cdot \hat{g}| > 4.0\text{ m/s}^2$) caused by road bumps, expansion joints, or potholes without sustained longitudinal deceleration ($a_{\text{long}} > -2.0\text{ m/s}^2$) SHALL NOT trigger a Brake Event.
3. *Immediate Priority Override & 2.0s Post-Brake Hold Window (`REQ-CON-035.3`)*:
• Upon a confirmed Brake Event, `RadarLightController` SHALL immediately override `SmartRadarLightEngine` rate limits and command the active radar peripheral to brake mode (`DAY_FLASH` 100 lm in daytime, or `SOLID` 25 lm).
• The brake illumination SHALL be held continuously throughout the deceleration duration plus a $2.0\text{ s}$ post-brake hold window after deceleration ceases ($a > -2.0\text{ m/s}^2$).
• Upon expiration of the $2.0\text{ s}$ hold window, `RadarLightController` SHALL smoothly restore the tail light to its pre-brake mode (`preBrakeMode`), resuming background `SmartRadarLightEngine` evaluation.
4. *StVZO & Night Anti-Glare Mode Clamping Invariant (`REQ-CON-035.4`)*:
• In strict compliance with `REQ-CON-031.2`, `REQ-CON-034.4`, and StVZO regulations, if connected hardware is StVZO-certified (`isStvzo == true`) OR the ambient environment is dark/night/tunnel (`SmartRadarLightEngine.isTunnelActive == true` or solar profile `DUSK_NIGHT`), the brake mode command SHALL be clamped to steady `RadarLightMode.SOLID` (25 lm), strictly prohibiting `DAY_FLASH` (100 lm) or any flashing mode at night to prevent blinding trailing motorists.
5. *Hardware Brake Sensor Auto-Bypass (`REQ-CON-035.5`)*:
• When the connected radar peripheral is identified as having an onboard hardware brake sensor—specifically Garmin Varia RearVue 820 (`010-03180-00` or `010-03180-10`) identified via BLE Model Number `0x2A24` or ANT+ Page `0x50` Product ID—software deceleration triggering SHALL be automatically bypassed (defaulted to disabled) to prevent redundant actuation, with manual override permitted in Settings.
• Auto-bypass status SHALL persist across sequential workout sessions while the hardware device remains connected; stopping a workout SHALL reset transient motion state without resetting the hardware auto-bypass flag.
6. *Settings Preference & 9-Language Localization Parity (`REQ-CON-035.6`)*:
• `TuningPreferencesDataStore` SHALL persist preference `radarBrakeLightEnabled` (`Boolean`, default `true`, auto-bypassed for RearVue 820).
• `RadarSettingsSection.kt` SHALL expose a dedicated toggle and status description with 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT in `strings_radar.xml`.
7. *Active Workout Lifecycle Management & Sensor Conservation (`REQ-CON-035.7`)*:
• `SmartBrakeLightDetector` SHALL register Android `SensorEventListener` (`Sensor.TYPE_LINEAR_ACCELERATION` and `Sensor.TYPE_GRAVITY`) exclusively during active cycling tracking sessions when radar light control is connected and enabled.
• Sensor listeners SHALL be immediately unregistered, any active brake light immediately reverted to `preBrakeMode` (including restoring `OFF` when `preBrakeMode == RadarLightMode.OFF`), and any pending post-brake hold cancelled upon workout pause, stop, or service destruction to prevent battery drain and prevent tail light resuscitation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Software Brake Light enabled and tail light in `PELOTON` (8 lm) mode at $25\text{ km/h}$,
• *When* the cyclist brakes hard ($a \le -2.0\text{ m/s}^2$ for $\ge 250\text{ ms}$),
• *Then* `RadarLightController` immediately commands brake mode (`DAY_FLASH` or `SOLID`) overriding normal rate limits, holds through braking, and restores `PELOTON` exactly $2.0\text{ s}$ after braking ends.
• *Given* the cyclist rides over a pothole at constant speed ($25\text{ km/h}$) causing a vertical acceleration spike ($|a_v| > 5.0\text{ m/s}^2$),
• *When* longitudinal deceleration does not exceed $-2.0\text{ m/s}^2$ for $250\text{ ms}$,
• *Then* `SmartBrakeLightDetector` rejects the shock and does not trigger the brake light.
• *Given* an StVZO-certified radar tail light connected,
• *When* a brake event occurs,
• *Then* `RadarLightController` clamps the brake mode to `SOLID` and never triggers a flashing mode.
• *Given* a Garmin Varia RearVue 820 connected,
• *When* device identification completes,
• *Then* software brake triggering is automatically bypassed to defer to the peripheral's hardware brake sensor.
• *Given* an active cycling workout with tail light in `OFF` mode (`RadarLightMode.OFF`),
• *When* the cyclist decelerates hard triggering a brake light event and the workout pauses,
• *Then* `RadarLightController` immediately reverts the tail light to `preBrakeMode` (`OFF`), clearing active brake state and cancelling pending holds.
• *Given* a connected Garmin Varia RearVue 820 with hardware brake sensing (`isAutoBypassed == true`),
• *When* a workout is stopped and a subsequent cycling workout is started without disconnecting the peripheral,
• *Then* `SmartBrakeLightDetector.isAutoBypassed` remains `true` and smartphone IMU sensor listeners are NOT registered.

**Invariants**: StVZO flashing prohibition compliance; road shock/pothole immunity; 2.0s hold window with exact pre-brake mode restoration; zero SQLite schema modifications; 100% 9-language translation parity.

### REQ-CON-036: Resilient Mid-Workout BLE Peripheral Reconnection, Scan-Quota Throttling & Hardware Auto-Connect Architecture [Verified]
- **Tests**: `TST-CON-036`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MyBTLEDevice.java`, `DeviceManager.java`
- **Rationale**: Mid-workout BLE disconnections caused by RF body-shadowing or momentary signal loss must not permanently abandon reconnection when a single search timeout expires. Peripheral drivers must persist retry chains with exponential backoff and hardware autoConnect while preserving battery bounding for never-seen offline sensors at startup (ATT-7755, Parent Epic ATT-5910, extending REQ-CON-020 and REQ-CON-023).
- **Description**:
1. *Continuous Mid-Session Reconnection Invariant (`REQ-CON-036.1`)*:
• `MyBTLEDevice` SHALL track whether the device has successfully connected in the current session (`mHasConnectedInSession`) and/or whether workout tracking is active (`TrackingMode.TRACKING` / `PAUSED`).
• While initial startup search for never-seen offline sensors MUST continue to terminate after `mSearchTimeoutRunnable` and bounded `mMyRemoteDeviceTries` (conserving battery), a mid-session disconnect of a previously connected peripheral (`!mIsShutDown`) SHALL NEVER abandon reconnection upon search timeout expiration. Upon timeout expiration without data, it SHALL reschedule `mReconnectRunnable` with exponential backoff (1s up to 16s) until reconnected or shut down.
2. *Hardware-Offloaded `autoConnect = true` & Direct Connection Resilience (`REQ-CON-036.2`)*:
• For reconnecting an already-known / session-connected BLE peripheral, `MyBTLEDevice` SHALL maintain a persistent controller-level GATT connection request (`connectGatt(mContext, /* autoConnect = */ true, mGattCallback, BluetoothDevice.TRANSPORT_LE)`) so the Bluetooth controller automatically establishes connection upon receiving the peripheral's advertisement without hammering active scanner windows.
3. *Sliding-Window BLE Scan Quota Throttling & Safe Fallback (`REQ-CON-036.3`)*:
• `MyBTLEDevice` SHALL enforce sliding-window scan rate throttling (maximum 4 scans per 30 seconds) to comply with Android framework BLE scanner limits (preventing `BluetoothLeScanner` unregistration errors).
• When scan assistance is throttled, unavailable, or encounters `onScanFailed`, `MyBTLEDevice` SHALL fall back immediately to direct/auto `connectGatt` without stranding the state machine.
4. *Session Re-Registration & State Machine Hygiene (`REQ-CON-036.4`)*:
• Upon successful reconnection (`STATE_CONNECTED` -> `onServicesDiscovered`), `MyBTLEDevice` SHALL re-discover services, re-enable characteristic notifications (CCCD), re-register sensors with `MySensorManager`, and reset backoff delay to `INITIAL_RECONNECT_DELAY_MS`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session with a connected BLE heart rate monitor (e.g. Polar H10),
• *When* an RF body-shadowing disconnect occurs (LSTO `HCI reason = 8`) and no advertisement is received during the initial 5-second search window,
• *Then* `MyBTLEDevice` does not abandon reconnection; it reschedules reconnection with exponential backoff, maintains auto-connect, and reconnects as soon as the peripheral reappears.
• *Given* a paired sensor that is offline during app startup,
• *When* the startup discovery search queue exhausts its configured retry attempts,
• *Then* `DeviceManager` and `MyBTLEDevice` terminate searching cleanly to conserve battery.
• *Given* multiple rapid reconnect cycles within 30 seconds,
• *When* BLE scan start is requested,
• *Then* scan quota throttling limits scanner invocations to <= 4 per 30 seconds and falls back to direct `connectGatt`.

**Invariants**: Offline sensor startup battery bounding preserved; thread safety under `mGattLock` preserved; zero idle battery regression; 100% test pass rate.

### REQ-CON-037: ANT+ Bike Radar Cold-Start Channel Acquisition and UsbAntService Race Resilience [Verified]
- **Tests**: `TST-CON-037`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`
- **Rationale**: When an external USB ANT+ stick is plugged in, `AntRadioService` cold-starts `UsbAntService` asynchronously on a background executor. Initial channel acquisition attempts via `AntChannelHelper.acquireAntPlusChannel()` immediately throw `ChannelNotAvailableException` (`SERVICE_INITIALIZING` / `NO_ADAPTERS_EXIST` or zero available channels). The system must register a broadcast receiver for `AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED` with `ContextCompat.RECEIVER_EXPORTED`, execute bounded interval retry backoff (up to 5 retries spaced 1000ms apart with 10s overall timeout), and automatically acquire the channel once channels become available, ensuring clean unregistration upon teardown (ATT-7546, Parent Epic ATT-1286, extending `REQ-CON-032`):
- **Description**:
  1. *Shared Asynchronous Channel Acquisition Engine (`REQ-CON-037.1`)*:
     - `AntChannelHelper` SHALL provide an asynchronous channel acquisition facility (`acquireAntPlusChannelAsync`) returning an `AcquisitionCancellationHandle`.
     - Upon invocation, it SHALL attempt immediate channel acquisition. If channels are available (`provider.getNumChannelsAvailable() > 0`), it SHALL acquire the channel and deliver it via `onChannelAcquired(channel)`.
  2. *Cold-Start State Observation & Bounded Backoff (`REQ-CON-037.2`)*:
     - If initial acquisition fails with `ChannelNotAvailableException` indicating `SERVICE_INITIALIZING` or `NO_ADAPTERS_EXIST` (or `getNumChannelsAvailable() == 0`), the engine SHALL register a `BroadcastReceiver` for `AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED` using `ContextCompat.RECEIVER_EXPORTED`.
     - The engine SHALL schedule periodic retry attempts (1000ms interval, up to 5 retries) and enforce a 10-second overall timeout.
     - When `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` is received or a retry timer fires, the engine SHALL verify `provider.getNumChannelsAvailable() > 0` before attempting `acquireAntPlusChannel`.
     - Upon successful acquisition, the receiver and retry timers SHALL be unregistered and `onChannelAcquired(channel)` SHALL be invoked.
     - If the 10-second timeout expires or an unrecoverable exception occurs, the receiver and timers SHALL be unregistered and `onAcquisitionFailed(exception)` SHALL be invoked.
  3. *Clean Lifecycle Teardown & Unregistration (`REQ-CON-037.3`)*:
     - In `ANTSearchForNewDevicesEngineMultiDeviceSearch`, invocation of `stopAsyncSearch()` or `onServiceDisconnected()` SHALL invoke `cancel()` on any pending channel acquisition handle.
     - In `ANTBikeRadarDevice`, invocation of `shutDown()`, `onWatchdogTimeout()`, or `onServiceDisconnected()` SHALL invoke `cancel()` on any pending channel acquisition handle.
  4. *Security & Broadcast Spoofing Guard (`REQ-CON-037.4`)*:
     - The broadcast receiver SHALL treat `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` solely as an event notification and SHALL query the authentic bound `AntChannelProvider` IPC interface for channel availability, never relying on untrusted intent extras.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-032.1` and `REQ-CON-032.5` targeting `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt`, and `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-46.3 (`ATT-5084`).
3. *Root Reason for Existing Formulation*: Assumed synchronous channel availability upon `onServiceConnected()` (valid for built-in SOC ANT chips, but races with USB ANT+ stick asynchronous driver initialization).
4. *Preservation of Core Invariants*: Predefined Network Slot 1 (`ANT_PLUS`) isolation, channel parameters (4084 period, RF 57, device type 40), and 3.5s starvation watchdog remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an external USB ANT+ stick plugged in and `AntRadioService` cold-starting,
• *When* `ANTBikeRadarDevice` or `ANTSearchForNewDevicesEngineMultiDeviceSearch` attempts channel acquisition and encounters `SERVICE_INITIALIZING` or `NO_ADAPTERS_EXIST`,
• *Then* the acquisition engine registers a receiver for `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` with `ContextCompat.RECEIVER_EXPORTED` and schedules 1000ms retry backoff.
• *Given* an active waiting acquisition session,
• *When* `UsbAntService` completes initialization and broadcasts `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` or a retry fires with available channels,
• *Then* `AntChannel` is acquired on Predefined Network slot 1, the receiver is unregistered, and `onChannelAcquired(channel)` is invoked.
• *Given* channels remain unavailable for 10 seconds,
• *When* the overall timeout expires,
• *Then* the receiver is cleanly unregistered and `onAcquisitionFailed(exception)` is invoked without unhandled crashes.
• *Given* an ongoing acquisition attempt,
• *When* the user stops search (`stopAsyncSearch()`) or the device is shut down (`shutDown()`),
• *Then* `cancel()` unregisters the receiver and removes all pending handler callbacks immediately.

**Invariants**: Predefined Network Slot 1 isolation; zero regression on BLE radar or standard ANT PCC devices; memory leak prevention via deterministic unregistration; thread safety on MainLooper.

### REQ-CON-038: ANT+ Bike Radar Continuous Discovery, Channel Event Re-Opening & ExtendedData SDK Parsing [Verified]
- **Tests**: `TST-CON-038`
- **System Requirement(s)**: `SYSREQ-TRK-002`
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

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-032.2` and `REQ-CON-032.5` targeting `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt`, and `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-46.3 (`ATT-5084`).
3. *Root Reason for Existing Formulation*: Assumed wildcard slave channel remains open indefinitely and `setAdapterWideLibConfig()` always succeeds on an exclusive adapter.
4. *Preservation of Core Invariants*: Predefined Network Slot 1 (`ANT_PLUS`) isolation, channel parameters (4084 period, RF 57, device type 40), and 3.5s starvation watchdog remain 100% preserved.

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

### REQ-CON-039: ANT+ Bike Radar Shutdown Cleanup, MainLooper Thread Serialization & Channel-Close Reconnect [Verified]
- **Tests**: `TST-CON-039`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ANTBikeRadarDevice.kt`
- **Rationale**: When an ANT+ Bike Radar device is shut down, all active target maps, sensor values, and `RadarStateRepository` states must be cleanly reset to prevent ghost vehicles from lingering in downstream Cockpit overlays. Concurrently, ANT message ingestion runs on the IPC binder thread while starvation watchdog timers and shutdown routines run on the main thread; message processing must be serialized onto `Handler(Looper.getMainLooper())` to avoid race conditions. Furthermore, if tracking channel connection drops or the 25-second slave search timeout expires, the device must re-open the channel automatically to resume tracking (ATT-7576, Parent Epic ATT-1286, extending `REQ-CON-032`, `REQ-CON-037`, and `REQ-CON-038`):
- **Description**:
  1. *Clean Shutdown State & Repository Disconnect (`REQ-CON-039.1`)*:
     - In `ANTBikeRadarDevice.shutDown()`, the device SHALL mark `isShutDown = true`, invoke `handler.removeCallbacksAndMessages(null)` to cancel pending runnables and watchdogs, clear `activeTargets`, and publish `RadarState` with `RadarConnectionState.DISCONNECTED`, `ThreatLevel.NONE`, and empty targets to `RadarStateRepository`.
     - `resetSensorsToClear()` SHALL be invoked to clear all scalar sensor values.
  2. *MainLooper Thread Serialization for ANT Message Ingestion (`REQ-CON-039.2`)*:
     - In `ANTBikeRadarDevice.channelEventHandler`, incoming `BROADCAST_DATA` and `ACKNOWLEDGED_DATA` messages received on the ANT binder thread SHALL clone their payload (`payload.clone()`) and dispatch `decodeBroadcastData` onto `handler` (`Looper.getMainLooper()`).
     - State mutations (`activeTargets`, `RadarStateRepository.updateState()`, `windowAggregator`, and `MySensor.newValue()`) SHALL execute exclusively on the main thread, eliminating thread concurrency races with `onWatchdogTimeout()` and `shutDown()`.
     - In `channelEventHandler.onChannelDeath()`, `onWatchdogTimeout()` SHALL also be posted to `handler`.
  3. *Tracking Mode Channel-Close Reconnect (`REQ-CON-039.3`)*:
     - In `ANTBikeRadarDevice.channelEventHandler`, when `MessageFromAntType.CHANNEL_EVENT` reports `EventCode.RX_SEARCH_TIMEOUT` or `EventCode.CHANNEL_CLOSED`, if `!isShutDown` and `antChannel != null`, the event handler SHALL dispatch a channel re-open request (`antChannel.open()`) via `handler`.
     - This re-open SHALL occur regardless of whether `isSearching` is true or false, allowing the radar channel to automatically recover and resume tracking after a 25-second slave search timeout or transient radio drop.
  4. *Shutdown Guard on Late-Arriving Messages (`REQ-CON-039.4`)*:
     - Once `shutDown()` has been invoked (`isShutDown == true`), all callbacks posted to `handler` and any late-arriving ANT messages SHALL be dropped immediately without modifying state, touching sensors, or re-opening `antChannel`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-032.2` & `REQ-CON-038.2` targeting `ANTBikeRadarDevice.kt`.
2. *Historical Origin & Commit Trace*: Sprints 2026-46.3 (`ATT-5084`) and 2026-46.6 (`ATT-7547`).
3. *Root Reason for Existing Formulation*: Scoped channel re-open strictly to initial discovery search (`isSearching == true`) to avoid interference with tracking, overlooking that a 25s tracking loss also closes the slave channel.
4. *Preservation of Core Invariants*: Predefined Network Slot 1 isolation, 3.5s starvation watchdog, and strict sensor decoupling remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ANTBikeRadarDevice.shutDown()` is called while targets are active,
• *When* shutdown completes,
• *Then* `activeTargets` is cleared and `RadarStateRepository` transitions to `DISCONNECTED` with empty targets.
• *Given* concurrent ANT+ broadcast packets on the binder thread and watchdog timeout on main,
• *When* messages are received,
• *Then* payload bytes are cloned and decoded sequentially on `Handler(Looper.getMainLooper())` without race conditions.
• *Given* an active tracking session (`isSearching == false`),
• *When* `EVENT_RX_SEARCH_TIMEOUT` or `EVENT_CHANNEL_CLOSED` is received,
• *Then* the channel event handler dispatches `antChannel.open()` to re-arm tracking.
• *Given* `shutDown()` has executed,
• *When* late ANT events arrive,
• *Then* they are ignored without re-opening channels or emitting state changes.

**Invariants**: Predefined Network Slot 1 isolation; thread safety via single-threaded MainLooper serialization; zero memory leaks or unhandled exceptions on shutdown.

### REQ-CON-040: ANT+ Radar Track Identity Continuity, 0-Bin Target Ingestion & Rolling Window Latch Preservation [Verified]
- **Tests**: `TST-CON-040`
- **System Requirement(s)**: `SYSREQ-TRK-002`
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

### REQ-CON-041: Concurrent Radar Source Arbitration & Multi-Transport State Decoupling [Verified]
- **Tests**: `TST-CON-041`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarState.kt`, `RadarStateRepository.kt`, `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`, `RadarTrafficSimulator.kt`
- **Rationale**: Dual-protocol cycling rearview radars (e.g., Garmin Varia RTL515, RearVue 820) broadcast telemetry over Bluetooth LE and ANT+ concurrently. If a cyclist pairs both transports in the application, unarbitrated updates in `RadarStateRepository` cause high-frequency target clobbering, jitter in Cockpit overlays, and erroneous disconnect alerts when one transport experiences transient packet starvation while the other is healthy. Furthermore, drivers inspecting global state for link health misreport their own connectivity. The system must establish deterministic source priority arbitration (`BLE` > `ANT_PLUS` > `SIMULATOR`), isolate watchdog disconnect states, provide per-source state inspection, and scope driver link telemetry strictly to its own transport (ATT-7575, Parent Epic ATT-1286, extending `REQ-CON-028`, `REQ-CON-029`, and `REQ-CON-032`):
- **Description**:
  1. *RadarSource Classification & Priority Hierarchy (`REQ-CON-041.1`)*:
     - The system SHALL define `RadarSource` enum (`NONE`, `SIMULATOR`, `ANT_PLUS`, `BLE`) with priority order `BLE` (3) > `ANT_PLUS` (2) > `SIMULATOR` (1) > `NONE` (0).
     - Every `RadarState` published SHALL include `source: RadarSource`.
  2. *Active Source Arbitration & Clashing Prevention (`REQ-CON-041.2`)*:
     - In `RadarStateRepository`, updates from a lower-priority source SHALL NOT overwrite the active prioritized source's `targets` or `connectionState` while the active prioritized source remains in `CONNECTED` state and has received updates within 3,500 ms.
     - When both `BLE` and `ANT_PLUS` drivers are connected and receiving packets, `RadarStateRepository.radarState` SHALL exclusively emit telemetry from the higher-priority `BLE` stream.
  3. *Watchdog Collision & Disconnect Isolation (`REQ-CON-041.3`)*:
     - If a secondary or subordinate radar source times out (watchdog fires with `DISCONNECTED_ERROR`), `RadarStateRepository` SHALL ignore the error state while the active prioritized source remains `CONNECTED`, strictly preventing false disconnect flashes or target erasure in Cockpit overlays.
     - If the active prioritized source disconnects (`DISCONNECTED` or `DISCONNECTED_ERROR`), `RadarStateRepository` SHALL failover immediately to any connected secondary source (e.g. `ANT_PLUS`), or transition to disconnected if no other source is active.
  4. *Per-Source State Inspection & Driver isReceivingData Isolation (`REQ-CON-041.4`)*:
     - `RadarStateRepository` SHALL expose `getState(source: RadarSource): RadarState` returning the isolated state of the specified transport.
     - In `ANTBikeRadarDevice.kt`, `isReceivingData()` SHALL query `RadarStateRepository.getState(RadarSource.ANT_PLUS).connectionState == RadarConnectionState.CONNECTED`, strictly isolating ANT+ link status from concurrent BLE activity.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-028.4` (Reactive Repository) and `REQ-CON-032.3` (Target Demuxing) in `docs/requirements/req_tracking_cockpit.md`.
2. *Historical Origin & Commit Trace*: Sprints 2026-46.3 (`ATT-5083`, `ATT-5084`).
3. *Root Reason for Existing Formulation*: `RadarStateRepository` was introduced as a single `MutableStateFlow` under the initial assumption that only one radar transport would be paired or active at a time.
4. *Preservation of Core Invariants*: 4–8 Hz reactive telemetry stream; fail-safe timeout transitions; backward-compatible `RadarState` constructors; 100% test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* both BLE and ANT+ radar drivers are connected,  
• *When* both receive packets,  
• *Then* only the active prioritized source updates `RadarStateRepository.radarState`.  
• *Given* the primary link is streaming and the secondary link times out,  
• *When* the secondary watchdog fires,  
• *Then* the primary `CONNECTED` state is not overwritten with `DISCONNECTED_ERROR`.  
• *Given* the primary link disconnects while a secondary link is connected,  
• *When* the primary disconnects,  
• *Then* `RadarStateRepository` seamlessly fails over to the secondary source.  
• *Given* only BLE is connected and ANT+ is not receiving data,  
• *When* `ANTBikeRadarDevice.isReceivingData()` is invoked,  
• *Then* it returns `false`.  

**Invariants**: Predefined Network Slot 1 isolation; thread-safe state synchronization; zero memory leaks; 100% test pass rate.

### REQ-CON-042: Radar Application State Preservation During Preemption & Hardware Packet Ingestion [Verified]
- **Tests**: `TST-CON-042`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarTrafficSimulator.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarStateRepository.kt`
- **Rationale**: Cyclists setting active 1-tap audio snooze (`RadarAudioSnoozeState`) or active tail light operating modes (`RadarLightMode`) expect these user-configured application states to persist when connecting real radar hardware or when receiving 4–8 Hz physical telemetry frames. Unconditional invocation of simulator preemption and destructive `RadarStateRepository.reset()` during simulator shutdown wiped these states to defaults (`INACTIVE`, `OFF`). The system must guarantee that hardware packet processing and simulator preemption non-destructively preserve active audio snooze and tail light modes (ATT-7574, Parent Epic ATT-1286, extending `REQ-CON-029`, `REQ-CON-031`, and `REQ-TRK-064`):
- **Description**:
  1. *Non-Destructive Simulator Shutdown (`REQ-CON-042.1`)*:
     - When `RadarTrafficSimulator.stop()` or `onPhysicalHardwareConnected()` terminates an active simulation, it SHALL NOT invoke `RadarStateRepository.reset()`.
     - It SHALL update `RadarSource.SIMULATOR` connection state to `DISCONNECTED` while preserving `lightMode`, `audioSnoozeState`, and `isLightControlSupported`.
  2. *Gated Simulator Preemption & Inverted State Sampling (`REQ-CON-042.2`)*:
     - `RadarTrafficSimulator.onPhysicalHardwareConnected()` SHALL be invoked only when `RadarTrafficSimulator.isSimulating` is true.
     - In `BTLERadarDevice.java`, `currentState = RadarStateRepository.getState()` SHALL be sampled before any simulator preemption invocation.
     - In `ANTBikeRadarDevice.kt`, preemption invocation SHALL be gated with `if (RadarTrafficSimulator.isSimulating)`.
  3. *Application State Preservation Invariant (`REQ-CON-042.3`)*:
     - In `RadarStateRepository`, an active `audioSnoozeState` or `lightMode` SHALL NOT be reset to defaults by physical radar telemetry packets arriving over BLE or ANT+.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active audio snooze or non-default tail light mode in `RadarStateRepository`,  
• *When* real BLE or ANT+ radar packets arrive,  
• *Then* `audioSnoozeState` and `lightMode` remain preserved in `RadarStateRepository.getState()`.  
• *Given* `RadarTrafficSimulator` is running,  
• *When* physical radar hardware connects or emits packets,  
• *Then* simulation stops immediately and physical hardware state is active without wiping repository snooze or light state.  

**Invariants**: Predefined Network Slot 1 isolation; thread safety via repository synchronization lock; zero memory leaks; 100% test pass rate.

### REQ-CON-043: Cockpit Radar Tail Light Button, Live Auto Status & Canonical Action Row [Verified]
- **Tests**: `TST-CON-043`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, `RadarLightButton.kt`, `strings_radar.xml`
- **Rationale**: Cyclists operating in traffic require rapid handlebar control to cycle radar tail light modes directly from the Cockpit without taking a hand off the bars to reach the saddle or navigating multi-step settings menus. A dedicated Cockpit action button with live physical status, `AUTO` micro-badge, tap-to-cycle, long-press menu, and canonical integration into a responsive `[Licht, Lap, Klingel]` action row provides immediate situational lighting control while preserving Cockpit layout ergonomics (ATT-7972, Parent Epic ATT-1286, extending `REQ-CON-031` and `REQ-UI-421`):
1. *TrackingViewsDbHelper Version 21 Migration & Schema Parity (`REQ-CON-043.1`)*:
• `TrackingViewsDbHelper` SHALL increment `DB_VERSION` from 20 to 21, adding column `ShowRadarLight` (`INTEGER DEFAULT 0`, with default `1` for cycling sport views).
• Database migration `onUpgrade` from version 20 to 21 SHALL be idempotent, checking column existence via `PRAGMA table_info(tracking_views)` prior to executing `ALTER TABLE`.
• Fresh installation schema `onCreate()` and `DatabaseSchemaRegistry` SHALL include `ShowRadarLight` with 100% parity across fresh and upgraded databases.
• `TrackingViewsDatabaseManager` and `TrackingViewsRepository` SHALL map `ShowRadarLight` symmetrically across single and batch cursor extraction methods.
2. *Cockpit RadarLightButton Live State, Micro-Badge & Tap/Long-Press Demuxing (`REQ-CON-043.2`)*:
• The system SHALL provide `RadarLightButton.kt` styled as a Material 3 `ElevatedButton` (`height = 72.dp`, `RoundedCornerShape(12.dp)`, `6.dp` elevation).
• `RadarLightButton` SHALL display live semantic vector icons and container/content colors corresponding to the active physical `RadarLightMode` (`OFF`, `SOLID`, `DAY_FLASH`, `NIGHT_FLASH`, `PELOTON`).
• When `isAutoMode == true`, the button SHALL continue rendering the live physical `RadarLightMode` icon and color while displaying a compact `AUTO` micro-badge at the bottom (`PlatformTextStyle(includeFontPadding = false)`, `lineHeight = fontSize`).
• `RadarLightButton` SHALL track gestures via `MutableInteractionSource`: a short tap triggers `HapticFeedback` and `RadarLightController.cycleNextMode()`; a long press triggers `HapticFeedbackType.LongPress` and displays `RadarLightQuickSwitchSheet` without advancing the mode.
• When `screenMode == ScreenMode.CONFIGURATION` or when no controllable light is connected (`isLightControlAvailable == false`), the button SHALL be completely hidden from the Cockpit.
3. *Canonical Action Row Ordering & Responsive Width Budget (`REQ-CON-043.3`)*:
• `TrackingTabsScreen.kt` SHALL layout bottom action buttons in strict left-to-right order: `[RadarLightButton, LapButton, BikeBellButton]`.
• When all three buttons are enabled simultaneously on a 360.dp wide portrait viewport at up to 130% font scale, the layout SHALL adapt horizontal padding and enforce single-line text (`maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`) on `LapButton` (`"Runde"`) so zero clipping or horizontal wrapping occurs.
4. *SensorGridScreen Configuration Toggle & Floating Clearance (`REQ-CON-043.4`)*:
• In `SensorGridScreen.kt` (`ScreenMode.CONFIGURATION`), the bottom configuration panel SHALL expose a dedicated toggle for `ShowRadarLight` alongside Lap and Bike Bell toggles.
• `ElevationProfile` and `LiveMmpCurveChart` SHALL dynamically adjust `fabBottomClearance` when `isRadarLightButtonVisible` is true, ensuring charts never overlap the floating button row.
5. *9-Language Localization Parity (`REQ-CON-043.5`)*:
• All button content descriptions, tab configuration toggle titles, and accessibility labels SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT in `strings_radar.xml` (zero inline emojis).

**Acceptance Criteria (Given-When-Then)**:
• *Criterion 1 (Conditional Visibility & Cycling Tab Default)*:
  - *Given* a cycling tracking tab where `ShowRadarLight` defaults to `true`,
  - *When* no controllable radar light is connected vs. when a light-capable radar connects in `READY`, `TRACKING`, or `PAUSED`,
  - *Then* the button is hidden while disconnected and appears immediately once light control becomes available.
• *Criterion 2 (Live Status + Bottom AUTO Indicator)*:
  - *Given* `Automatik` mode is active and `SmartRadarLightEngine` switches the physical light from `OFF` to `DAY_FLASH` as a vehicle approaches,
  - *When* viewing `RadarLightButton`,
  - *Then* the button's main icon and color update live from `OFF` to `DAY_FLASH` while the `AUTO` text remains cleanly visible at the bottom of the button without clipping.
• *Criterion 3 (Short-Tap Cycle vs. Long-Press Selection Sheet)*:
  - *Given* `RadarLightButton` is visible on the Cockpit,
  - *When* the cyclist performs a short tap vs. a long press,
  - *Then* a short tap advances to the next mode with debounced radio dispatch, whereas a long press opens `RadarLightQuickSwitchSheet` without triggering a mode step.
• *Criterion 4 ([Licht, Lap, Klingel] Ordering & 3-Button Portrait Fit)*:
  - *Given* `ShowRadarLight`, `ShowLapButton`, and `ShowBikeBell` are all active on a 360.dp portrait screen in German at 130% font scale,
  - *When* the Cockpit renders the bottom button row,
  - *Then* the buttons appear in exact order `[Licht, Lap, Klingel]` on a single horizontal row without text wrapping, clipping, or overlapping `ElevationProfile` / `LiveMmpCurveChart`.

**Invariants**: Non-blocking GATT mode cycling; zero ElevatedCard/pointerInput contention; idempotent SQLite upgrade; 100% 9-language translation parity.

### REQ-CON-044: Instance-Isolated Connection State & Multi-Device Gating for BLE & ANT+ Radar [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-044`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `MyBTLEDevice.java`
- **Rationale**: When multiple radar peripherals are paired in `DeviceManager` across multiple bikes (e.g. `RearVue8` on bike A and `ATT-Rdr` on bike B), connecting to one radar must not falsely mark all paired radar peripherals as connected on the Start screen or in Cockpit device status headers. Each device instance must evaluate its own physical connection and packet reception status while preserving fail-safe packet-starvation watchdog detection (ATT-8109, Parent Epic ATT-1286, extending `REQ-CON-028` and `REQ-CON-041.4`):
- **Description**:
  1. *Per-Instance BLE GATT Connection Qualification (`REQ-CON-044.1`)*:
     - `BTLERadarDevice.isReceivingData()` SHALL verify that *this specific device instance* has an active GATT connection and discovered services via `super.isReceivingData()` (`mState == State.CONNECTED_WITH_SERVICE`), in conjunction with `RadarStateRepository.getState(RadarSource.BLE).getConnectionState() == RadarConnectionState.CONNECTED`.
     - When multiple `BTLERadarDevice` instances are paired, an active connection on one peripheral SHALL NOT cause disconnected or powered-off peer instances to return `true`.
  2. *Fail-Safe Watchdog Starvation Preservation (`REQ-CON-044.2`)*:
     - If the underlying GATT connection of a `BTLERadarDevice` remains open but incoming packet flow ceases (triggering the 3.5s packet-starvation watchdog and setting `RadarConnectionState.DISCONNECTED_ERROR`), `isReceivingData()` SHALL evaluate to `false` via the conjunction with `RadarStateRepository`.
  3. *Per-Instance ANT+ Radar Packet Reception Gating (`REQ-CON-044.3`)*:
     - `ANTBikeRadarDevice.isReceivingData()` SHALL verify that *this specific device instance* has received valid radar broadcast packets within the active watchdog timeout window (`WATCHDOG_TIMEOUT_MS = 3500L`), in conjunction with `RadarStateRepository.getState(RadarSource.ANT_PLUS).connectionState == RadarConnectionState.CONNECTED`.
     - When multiple ANT+ radars are configured, a live broadcast stream on one device SHALL NOT cause a silent peer ANT+ radar to report `isReceivingData() == true`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-CON-044` extending `REQ-CON-041.4` (*Isolated Transport Inspection* in `ATT-7575`). Targets `BTLERadarDevice.java` and `ANTBikeRadarDevice.kt`.
2. *Historical Origin & Commit Trace*: Commit `663c69ea` (ATT-7575) introduced `BTLERadarDevice.isReceivingData()` inspecting `RadarStateRepository.getState(RadarSource.BLE)` to prevent ANT+ streams from falsely activating BLE radar tiles.
3. *Root Reason for Existing Formulation*: The formulation in ATT-7575 focused on cross-protocol arbitration (BLE vs. ANT+), inadvertently omitting the per-instance GATT connection check from `MyBTLEDevice`.
4. *Preservation of Core Invariants*: Preserves cross-protocol isolation (`RadarSource.BLE` vs. `RadarSource.ANT_PLUS`), watchdog fail-safe timeout upon packet starvation, and single-radar operation.

**Acceptance Criteria (Given-When-Then)**:
• *Criterion 1 (Multi-Instance BLE Radar Gating)*:
  - *Given* two paired BLE radar devices (`device1` and `device2`) in `DeviceManager`,
  - *When* `device1` is connected (`CONNECTED_WITH_SERVICE`) and streaming, while `device2` is powered off (`DISCONNECTED`),
  - *Then* `device1.isReceivingData()` returns `true`, and `device2.isReceivingData()` returns `false`.
• *Criterion 2 (BLE Watchdog Packet Starvation)*:
  - *Given* a connected `BTLERadarDevice` (`CONNECTED_WITH_SERVICE`),
  - *When* radar packet delivery stops and the 3.5s watchdog timer fires (setting `RadarConnectionState.DISCONNECTED_ERROR`),
  - *Then* `device.isReceivingData()` returns `false` despite GATT connection remaining open.
• *Criterion 3 (Multi-Instance ANT+ Radar Gating)*:
  - *Given* two configured ANT+ radar devices,
  - *When* only one device receives broadcast data pages,
  - *Then* only that active instance returns `isReceivingData() == true`.

**Invariants**: Zero false-positive device status on Start/Cockpit screens; 100% fail-safe watchdog retention; 100% clean-room test pass rate.

### REQ-CON-045: Polar Live IMU Streaming Bridge Search Timeout & GATT Contention Recovery [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-045`, `TST-CON-050`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`, `SYSREQ-DEX-007`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PolarOnlineStreamingBridge.kt`, `DeviceManager.java`
- **Rationale**: During sequential sensor discovery (`DeviceManager.mSearchQueue`), `PolarOnlineStreamingBridge.startSearching()` starts scanning without a search timeout watchdog. If the Polar sensor (H10 or Verity Sense) is turned off, out of range, or held in an open GATT connection by another application (e.g., Polar Flow suppressing connectable advertisements), Polar SDK's internal scan hangs indefinitely. Without `notifyStopSearching(false)`, the entire peripheral search queue deadlocks, blocking subsequent sensors (such as Garmin Varia Radar or powermeters). Furthermore, if `polarApi != null`, `connectPersistentSession()` exits silently after `notifyStartSearching()` has already been dispatched, permanently freezing the UI on repeated searches or after disconnect / Bluetooth adapter power cycles (`ATT-8451`, extending `REQ-SET-112.2` and `REQ-CON-004`):
- **Description**:
  1. *Deterministic Search Timeout & Queue Unblocking (`REQ-CON-045.1`)*:
     - `PolarOnlineStreamingBridge.startSearching()` SHALL schedule a deterministic search timeout matching `TrainingApplication.getBtleSearchPeriodMs()` (analogous to `MyBTLEDevice`).
     - If the timeout expires before `deviceConnected` is received, the bridge SHALL abort the ongoing scan/connect attempt, invoke `notifyStopSearching(false)` to unblock `DeviceManager.mSearchQueue`, and (if `!isReceivingData()`) disconnect from the device via `polarApi.disconnectFromDevice(polarId)`.
  2. *Idempotent Search & Session State Recovery on Cached `polarApi` (`REQ-CON-045.2`)*:
     - Calling `startSearching()` when `polarApi != null` SHALL NOT silently exit without attempting connection.
     - If already connected and receiving data (`isReceivingData() == true`), `startSearching()` SHALL immediately invoke `notifyStopSearching(true)` without re-opening sessions.
     - If disconnected (`isReceivingData() == false`), `startSearching()` SHALL re-initiate `api.connectToDevice(polarId)` (or re-instantiate `polarApi` if the SDK session is invalidated/closed) and schedule the search timeout watchdog.
  3. *Search Watchdog Cancellation on Connection (`REQ-CON-045.3`)*:
     - When `deviceConnected(polarDeviceInfo)` callback is received during an active search, `PolarOnlineStreamingBridge` SHALL immediately cancel the active search timeout watchdog, invoke `notifyStopSearching(true)`, register sensors, and synchronize active streaming sessions.
  4. *Bluetooth Power Cycle & Lifecycle Teardown (`REQ-CON-045.4`)*:
     - `PolarOnlineStreamingBridge.sdkCallback` SHALL implement `blePowerStateChanged(powered: Boolean)`.
     - When `powered == false`, the bridge SHALL mark `isConnectedFlag = false`, cancel active streaming coroutines (`hrJob`, `accJob`, `gyroJob`), release the `ACC` ownership lock in `PolarOfflineGattCoordinator`, unregister sensors, and (if currently searching) cancel the search timeout watchdog and invoke `notifyStopSearching(false)`.
     - `shutDown()` SHALL cancel the search timeout watchdog, disconnect/cleanup `polarApi`, unregister receivers, and cancel the coroutine scope, while strictly preserving `activeBridges` registry contracts (`ATT-8019`), API < 33 fallback, cadence/dynamics arbitration (`REQ-SET-112.4`), and offline GATT sharing (`REQ-SET-112.3`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-CON-045` extending `REQ-SET-112.2` and `REQ-CON-004`. Targets `PolarOnlineStreamingBridge.kt` and `DeviceManager.java`.
2. *Historical Origin & Commit Trace*: Commit `d9f9a263` (`ATT-8016`, persistent single-GATT Polar live IMU streaming bridge).
3. *Root Reason for Existing Formulation*: In ATT-8016, `connectPersistentSession()` used `if (polarApi != null) return` to avoid duplicate `PolarBleApi` instantiation across workout cycles, but overlooked that repeated search attempts or post-disconnect retries require re-initiating connection. Search timeout was omitted during live streaming focus.
4. *Preservation of Core Invariants*: Preserves standard `BTLEHeartRateDevice` fallback when live IMU is disabled or `< API 33`; preserves IMU cadence priority and `BSportType.RUN` gating (`REQ-SET-112.4`); preserves offline session sharing (`REQ-SET-112.3`); preserves `activeBridges` registry (`ATT-8019`).

**Acceptance Criteria (Given-When-Then)**:
• *Criterion 1 (Deterministic Search Timeout & Unblocking)*:
  - *Given* a paired Polar H10 or Verity Sense with live IMU enabled (`polarLiveImuEnabled = 1`) that is powered off, out of range, or advertisement-starved by an existing Polar Flow GATT connection,
  - *When* `startSearching()` is invoked by `DeviceManager.searchForNextRemoteDevice()`,
  - *Then* `notifyStartSearching()` is broadcast, and after `TrainingApplication.getBtleSearchPeriodMs()` elapses without connection, the bridge disconnects and broadcasts `notifyStopSearching(false)`, allowing `DeviceManager.mSearchQueue` to advance to the next sensor.
• *Criterion 2 (Idempotent Repeated Search with Existing `polarApi`)*:
  - *Given* a `PolarOnlineStreamingBridge` whose `polarApi` instance is already initialized (e.g. following a prior search, disconnect, or Bluetooth restart) but currently disconnected,
  - *When* `startSearching()` is invoked again (e.g. manual user search retry),
  - *Then* the bridge re-initiates connection via `api.connectToDevice(polarId)`, arms the search timeout watchdog, and does not silently deadlock the search queue.
• *Criterion 3 (Connection Success Timeout Cancellation)*:
  - *Given* an active search with the search timeout watchdog running,
  - *When* the Polar sensor connects and `deviceConnected` is received,
  - *Then* the search timeout watchdog is immediately cancelled, `notifyStopSearching(true)` is broadcast, and live streaming is established without delayed disconnect timeouts.
• *Criterion 4 (Bluetooth Adapter Power Cycle)*:
  - *Given* an active search or connected streaming session on `PolarOnlineStreamingBridge`,
  - *When* the Bluetooth adapter powers off (`blePowerStateChanged(false)`),
  - *Then* active timeout jobs are cancelled, connection state is cleared, streaming jobs are halted, and if searching, `notifyStopSearching(false)` is dispatched.

**Invariants**: `TrainingApplication.getBtleSearchPeriodMs()` remains source of truth; zero regression on standard `BTLEHeartRateDevice` fallback (< API 33 or live IMU disabled); `MySensorManager.CADENCE_PRIORITY` ranking (`RUN_SPEED` > `BIKE_*` > `HRM`) preserved; `PolarOfflineGattCoordinator` session sharing preserved; `activeBridges` registry (`ATT-8019`) preserved; zero localized string changes.

### REQ-CON-046: Radar `isAutoMode` Preservation Across Telemetry Frames, Simulator & Source Arbitration [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-046`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-007`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarState.kt`, `RadarStateRepository.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarTrafficSimulator.kt`, `RadarLightController.kt`
- **Rationale**: When a cyclist selects a manual tail-light mode (`RadarLightController.setManualLightMode()` / `cycleNextMode()`), `isAutoMode = false` is published to `RadarStateRepository`. However, telemetry and lifecycle `RadarState` constructions in `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, and `RadarTrafficSimulator.kt` omit `isAutoMode`, falling back to the Kotlin default `isAutoMode = true` at 4–8 Hz and causing Cockpit UI (`TrackingTabsScreen.kt`) to revert immediately to `Automatik` (`ATT-8455`, extending `REQ-CON-031.8` and `REQ-CON-042.3`):
- **Description**:
  1. *Driver & Simulator `isAutoMode` Preservation (`REQ-CON-046.1`)*:
     - All 6 `RadarState` emission sites in `BTLERadarDevice.java` (`mWatchdogRunnable`, `startSearching`, `notifyStopSearching`, `configureServices`, `processTargets`, `disconnectFromGatt`), all 3 sites in `ANTBikeRadarDevice.kt` (`publishTargets`, `onWatchdogTimeout`, `shutDown`), and `RadarTrafficSimulator.kt` (`generateFrame`, `tick`, `start`, `stop`) SHALL propagate the current `isAutoMode` flag without resetting manual overrides to `true`.
  2. *Cross-Source Control-State Sync & Failover Continuity (`REQ-CON-046.2`)*:
     - `RadarStateRepository` SHALL preserve and synchronize `isAutoMode` (and `audioSnoozeState` per `REQ-CON-042.3`) across `sourceStates` entries during multi-source arbitration and failover (`BLE` <-> `ANT_PLUS` <-> `SIMULATOR`), while explicit `RadarLightController.setAutoMode()` transitions SHALL restore `isAutoMode = true` and manual overrides SHALL keep `SmartRadarLightEngine` automation suspended.
  3. *Repository Isolation & Reset Contract (`REQ-CON-046.3`)*:
     - `RadarStateRepository.reset()` SHALL remain cleanly decoupled from `RadarLightController`, clearing `sourceStates` and resetting `_radarState` to default `RadarState()` without leaking state across unit tests (`REQ-CON-042.3`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-CON-046` extending `REQ-CON-031.8` and `REQ-CON-042.3`; targets `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarTrafficSimulator.kt`, `RadarStateRepository.kt`, and `RadarLightController.kt`.
2. *Historical Origin & Commit Trace*: Commit `a3783bd7` (`ATT-7574`, `REQ-CON-042`) established `lightMode`/`audioSnoozeState` preservation; commit `c422e1b6` (`ATT-7991`, `REQ-CON-031.8`) appended `val isAutoMode: Boolean = true` to `RadarState.kt:123`.
3. *Root Reason for Existing Formulation*: `ATT-7991` added `isAutoMode = true` as a 9th `@JvmOverloads` parameter, leaving existing 8-arg Java calls (`BTLERadarDevice.java`) and Kotlin constructor/copy sites (`ANTBikeRadarDevice.kt`, `RadarTrafficSimulator.kt`) silently defaulting to `true`.
4. *Preservation of Core Invariants*: Preserves `REQ-CON-041` source priority (`BLE > ANT_PLUS > SIMULATOR`), `REQ-CON-042.2` pre-preemption state sampling, `REQ-CON-042.3` application state preservation, `SmartRadarLightEngine` manual override suspension, and `RadarStateRepository.reset()` isolation.

**Acceptance Criteria (Given-When-Then)**:
• *Criterion 1 (Telemetry & Lifecycle Frame Preservation)*:
  - *Given* `RadarStateRepository` has `isAutoMode == false` after manual light selection,
  - *When* telemetry or lifecycle frames are emitted by `BTLERadarDevice`, `ANTBikeRadarDevice`, or `RadarTrafficSimulator` (`generateFrame`/`tick`/`start`/`stop`),
  - *Then* `RadarStateRepository.getState().isAutoMode` remains `false`.
• *Criterion 2 (Explicit Auto Restoration & Multi-Source Failover)*:
  - *Given* `isAutoMode == false` across active `BLE`/`ANT_PLUS`/`SIMULATOR` streams,
  - *When* the active source fails over to another transport in `RadarStateRepository.sourceStates`,
  - *Then* `isAutoMode` remains `false`, and *When* `RadarLightController.setAutoMode()` is invoked, *Then* `isAutoMode` transitions cleanly to `true` and resumes `SmartRadarLightEngine`.
• *Criterion 3 (Reset Isolation)*:
  - *Given* non-default `isAutoMode == false` in `RadarStateRepository`,
  - *When* `RadarStateRepository.reset()` is called,
  - *Then* `sourceStates` clears and `RadarStateRepository.getState()` resets to default `RadarState()` (`isAutoMode == true`).

**Invariants**: `REQ-CON-041` priority (`BLE > ANT_PLUS > SIMULATOR`), `REQ-CON-042.3` `lightMode`/`audioSnoozeState` preservation, `SmartRadarLightEngine` override gating, zero SQLite schema changes, zero localization changes.

### REQ-CON-047: Compact Quick-Switch Sheet & Shared 2-Column RadarLightModeGrid, Button-Matching Icons & Ascending Mode Order [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-047`, `TST-CON-048`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarLightModeGrid.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarLightButton.kt`, `RadarLightMode.kt`, `RadarLightController.kt`, `EditDeviceDialog.kt`
- **Rationale**: PO review on Pixel 10 (`ATT-7972`) observed `RadarLightQuickSwitchSheet` overflowing vertically past the 4th audio snooze option due to 10 tall full-width `RadioButton` rows, using generic `10.dp` colored dots instead of semantic vector icons from `RadarLightButton`, and presenting a different mode sequence than `RadarLightController.cycleNextMode()` (`ATT-8456`, refining `REQ-CON-031.3` and `REQ-CON-031.8`). Subsequently, `EditDeviceDialog.kt` (`RadarLightControlSectionContent`) retained legacy `SingleChoiceSegmentedButtonRow` (StVZO) and `10.dp` `CircleShape` dot + `RadioButton` rows; `ATT-8678` consolidates both surfaces onto a single shared stateless `RadarLightModeGrid` composable with zero duplicated mode-grid UI:
- **Description**:
  1. *Compact Non-Scrolling Quick-Switch Layout (`REQ-CON-047.1`)*:
     - `RadarLightQuickSwitchSheet` / `RadarLightQuickSwitchSheetContent` SHALL arrange all light modes (including `Automatik`) and all 4 audio snooze options (`SNOOZE_15M`, `SNOOZE_30M`, `FAST_ONLY`, `REST_OF_RIDE`, plus `Resume Audio` when snoozed) in a compact layout that fits completely within standard smartphone viewports (e.g. Pixel 10) without vertical scroll clipping, while retaining all `RadarLightMode_*` and `RadarSnooze_*` testTags and Light/Dark/StVZO `private` `@Preview`s (`DeadCodeEliminationContractTest`).
  2. *Shared Stateless `RadarLightModeGrid` & Semantic Vector Icons (`REQ-CON-047.2`)*:
     - A single shared, stateless composable `RadarLightModeGrid` alongside `resolveLightVisuals` SHALL reside in `com.atrainingtracker.trainingtracker.ui.components.radar` (`RadarLightModeGrid.kt`) and be invoked identically by BOTH `RadarLightQuickSwitchSheetContent` (keeping Audio Snooze local) and `EditDeviceDialog`'s `RadarLightControlSection` / `RadarLightControlSectionContent` (`0` duplicated dot/segmented-row UI), using semantic vector icons and mode colors (`FlashlightOff`, `Groups`, `FlashlightOn`, `NightlightRound`, `WbSunny` via `TTColor.RadarLightContainer`) and Light/Dark `MaterialTheme.colorScheme` tokens.
     - `RadarLightModeGrid` SHALL render the StVZO notice banner (`testTag("RadarLightStvzoNotice")`) when `isStvzo == true`, `Automatik` tile (`testTag("RadarLightMode_AUTO")`) with active-mode subtitle gated by `showActiveModeSubtitle: Boolean` (`true` in `RadarLightQuickSwitchSheetContent`; `!isOffline` in `EditDeviceDialog`), and 2-column even/odd chunked physical mode tiles (`testTag("RadarLightMode_${mode.name}")`, `Role.RadioButton`, merged selection semantics), while preserving `RadarLightQuickSwitchSheet*` and `RadarLightButton` (`resolveLightVisuals` delegation) reflection/IF-SWE signatures and adding zero new `internal`/`public` `@Preview`s.
  3. *Unified Ascending-Intensity Mode Order (`REQ-CON-047.3`)*:
     - `RadarLightMode.getAvailableModes()`, `RadarLightController.cycleNextMode()`, `RadarLightQuickSwitchSheet` (via `RadarLightModeGrid`), and `EditDeviceDialog` (via `RadarLightModeGrid`) SHALL share the identical ascending-intensity order:
       - Non-StVZO: `Automatik` -> `OFF` (0 lm) -> `PELOTON` (~8 lm) -> `SOLID` (~25 lm) -> `NIGHT_FLASH` (~40 lm) -> `DAY_FLASH` (100 lm) (-> `Automatik`).
       - StVZO (`isStvzo == true`): `Automatik` -> `OFF` -> `SOLID` (-> `Automatik`).
  4. *Localization & Persistence Preservation (`REQ-CON-047.4`)*:
     - All UI labels SHALL reuse existing 9-locale string resources in `strings_radar.xml` (zero hardcoded literals; `Localization Parity: N/A - no UI strings` added/modified), and `TuningPreferencesDataStore` `.name` persistence + `RadarLightMode.commandByte` wire bytes + Workout Auto-Switch behavior SHALL remain unchanged.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-CON-047` (`.2`/`.3`), `REQ-CON-031.3`, and `REQ-CON-031.8`; targets `RadarLightModeGrid.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarLightButton.kt`, `RadarLightMode.kt`, `RadarLightController.kt`, and `EditDeviceDialog.kt`.
2. *Historical Origin & Commit Trace*: `ATT-5102` (`c1d27049`) introduced `RadarLightMode` enum order; `ATT-7991` (`c422e1b6`) added `Automatik` and `cycleNextMode()`; `ATT-8456` introduced the 2-column tile grid in `RadarLightQuickSwitchSheet.kt`; `ATT-8450` added offline `EditDeviceDialog` light controls using legacy rows.
3. *Root Reason for Existing Formulation*: `ATT-8456` and `ATT-8450` evolved in parallel on separate UI widgets before consolidating the shared selector grid under `ui/components/radar/`.
4. *Preservation of Core Invariants*: `Automatik` remains first; StVZO pruning excludes flashing modes; `TuningPreferencesDataStore` persists `.name` (never `.ordinal`); ~400 ms `cycleNextMode()` debounce, Workout Auto-Switch, `RadarLightQuickSwitchSheet*`/`RadarLightButton` IF-SWE/reflection signatures, and 9-locale `strings_radar.xml` parity remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Criterion 1 (Shared 2-Column Layout & Semantic Icons)*:
  - *Given* `RadarLightQuickSwitchSheetContent` or `RadarLightControlSectionContent` is rendered with `isLightControlSupported = true` and `isStvzo = false` (or `isStvzo = true`),
  - *When* inspected in Compose UI tests and Light/Dark `@Preview` PNG captures,
  - *Then* both surfaces delegate to the identical `RadarLightModeGrid` 2-column row-major layout (`[AUTO, OFF]`, `[PELOTON, SOLID]`, `[NIGHT_FLASH, DAY_FLASH]` for non-StVZO; `AUTO` full-width row 1 + `[OFF, SOLID]` row 2 + `RadarLightStvzoNotice` for StVZO), matching `resolveLightVisuals` semantic icons and Light/Dark M3 tokens with zero duplicated dot/segmented-row UI.
• *Criterion 2 (Unified Ascending Mode Order Across UI & Tap Cycling)*:
  - *Given* non-StVZO or StVZO hardware,
  - *When* querying `RadarLightMode.getAvailableModes()`, `RadarLightController.getAvailableModes()`, `EditDeviceDialog`, `RadarLightQuickSwitchSheet`, and stepping through `RadarLightController.cycleNextMode()`,
  - *Then* all 4 surfaces follow the exact ascending order `Automatik -> OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH` (StVZO: `Automatik -> OFF -> SOLID`).
• *Criterion 3 (Contract Guards & Zero New Strings)*:
  - *Given* `RadarLightModeGrid.kt`, `RadarLightQuickSwitchSheet.kt`, and `EditDeviceDialog.kt`,
  - *When* audited by contract tests (`ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`),
  - *Then* zero layering/signature violations and zero new `internal`/`public` `@Preview`s exist, and all labels resolve exclusively via existing `strings_radar.xml` resources (`Localization Parity: N/A - no UI strings`).

**Invariants**: Single shared `RadarLightModeGrid` in `ui/components/radar/`; `Automatik` always first; StVZO pruning excludes `PELOTON`/`NIGHT_FLASH`/`DAY_FLASH`; `RadarLightQuickSwitchSheet*` & `RadarLightButton` signatures preserved; Workout Auto-Switch & ~400 ms debounce unchanged; `RadarLightMode.commandByte` & `TuningPreferencesDataStore` `.name` persistence unchanged; zero SQLite changes; `Localization Parity: N/A - no UI strings`.

### REQ-TRK-064: 1-Tap Radar Audio Snooze, Peloton-Mode Audio Sync & Smart Urban Alert Suppression [Verified]
- **Tests**: `TST-TRK-064`
- **System Requirement(s)**: `SYSREQ-TRK-005`, `SYSREQ-TRK-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarSnoozeController.kt`, `RadarAlertManager.kt`, `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarState.kt`, `RadarStateRepository.kt`, `BTLERadarDevice.java`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Cyclists riding through city traffic, along busy parallel roads, or in group rides experience severe alert fatigue from continuous radar beeping. Navigating deeply buried menus mid-ride is hazardous, and following riders in peloton formations trigger constant spurious alerts. The system must provide 1-tap temporary audio snooze from the Cockpit (`RadarLightQuickSwitchSheet` via threat-state `RadarEdgeOverlay` badge or `RadarLightButton` long-press), automatic audio suppression coupled to tail light Peloton mode, and an automated smart urban convoy filter while preserving 100% active visual rail tracking, haptic feedback, and high-speed threat alerts (ATT-5119, ATT-6708, ATT-8679, Parent Epic ATT-1286, extending `REQ-TRK-062`, `REQ-TRK-063`, and `REQ-CON-031`):
1. *Lifecycle-Decoupled 1-Tap Cockpit Audio Snooze (`REQ-TRK-064.1`)*:
• The system SHALL provide 1-tap quick snooze actions accessible from `RadarLightQuickSwitchSheet` (opened via active-threat `RadarEdgeOverlay` badge or `RadarLightButton` long-press): Snooze 15 minutes, Snooze 30 minutes, High-Threat Only, and Mute Rest of Ride. Snooze state, ticker countdown, and expiration SHALL be managed by a lifecycle-safe singleton (`RadarSnoozeController`) in `com.atrainingtracker.banalservice.devices.radar` using a strictly monotonic time clock (`SystemClock.elapsedRealtime()`), ensuring snooze and resume operate consistently across `READY`, `PAUSED`, and `TRACKING` states, survive transient BLE disconnects/reconnects without reset to `INACTIVE`, remain unaffected by wall-clock NTP adjustments or timezone jumps, and reset cleanly upon explicit workout finish.
2. *Approaching Vehicle Count Retention, Muted-Bell Vector Icon & Accessibility Localization (`REQ-TRK-064.2`)*:
• While audio is snoozed and vehicles are approaching (`hasActiveThreats`), `RadarStatusBadgePill` SHALL render a dedicated muted-bell vector icon and localized countdown indicator alongside (never replacing) the active approaching vehicle count (`targets.size`); when road is clear (`isClear`), `RadarStatusBadgePill` remains suppressed per `REQ-TRK-062.3` (zero green circle). Audio snooze SHALL strictly preserve 100% active, un-dimmed visual vertical target rail tracking and threat animations. All threat badge accessibility descriptions SHALL be localized across all 9 supported locales via `radar_overlay_threats_format` and `radar_overlay_threats_fast_format`.
3. *Acoustic Chime Gating, Haptics Preservation & Manual Resume Override (`REQ-TRK-064.3`)*:
• Audio snooze, Peloton light mode sync, and Urban congestion mute SHALL gate only acoustic chime playback while strictly preserving haptic vibrations when `config.haptics` is enabled. Tapping "Resume Audio" SHALL cancel active snooze and provide an explicit manual override of active Peloton sync and Urban congestion auto-mute until the next state transition. `RadarSnoozeController` SHALL expose a static `toggleRadarAudioSnooze()` API to support remote handlebar/shifter button invocation (`ATT-1288`).
4. *Peloton Light Mode Audio Sync (`REQ-TRK-064.4`)*:
• When `radarPelotonAudioSync` is enabled in `TuningPreferencesDataStore` (default `true`), switching the radar tail light to `PELOTON` mode (`ATT-5102`) SHALL automatically suppress routine `APPROACHING` and `ALL_CLEAR` chimes, retaining only `FAST_APPROACHING` threats and haptic alerts. Reverting from `PELOTON` mode SHALL immediately restore configured audio alerts.
5. *Resilient Smart Urban Congestion Suppression (`REQ-TRK-064.5`)*:
• When `radarSmartUrbanMute` is enabled (default `false`), `RadarAlertManager` SHALL suppress repetitive `APPROACHING` and `ALL_CLEAR` chimes when $\ge 3$ vehicles approach with relative speed $< 5.0\text{ m/s}$ ($< 18\text{ km/h}$) continuously for $\ge 30\text{ s}$. The 30s congestion accumulator SHALL remain intact when $\ge 3$ total vehicles are present and at least 2 are slow, even if a single faster vehicle ($\ge 5.0\text{ m/s}$) passes. The system SHALL exit urban suppression once clear or uncongested for $\ge 20\text{ s}$. Any `FAST_APPROACHING` vehicle SHALL immediately sound the urgent threat chime.
6. *Settings Configuration & DataStore Persistence (`REQ-TRK-064.6`)*:
• `TuningPreferencesDataStore` SHALL persist `radarPelotonAudioSync` (Boolean, default `true`) and `radarSmartUrbanMute` (Boolean, default `false`), exposed in `RadarSettingsSection.kt`.
7. *9-Language Localization Parity (`REQ-TRK-064.7`)*:
• All snooze options, badge text, dialog strings, accessibility threat strings, and settings toggles in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-TRK-064` (Radar Audio Snooze, Peloton Sync & Urban Suppression), targeting `RadarAlertManager.kt`, `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`, `TrackingTabsScreen.kt`, and `BTLERadarDevice.java`.
2. *Historical Origin & Commit Trace*: `ATT-5119` (commit `24a520e5`). Refined under `ATT-6708` to decouple snooze from `TrackerService` lifecycle, decouple audio mute from haptic alerts, preserve approaching vehicle count on the overlay badge, and prevent urban timer resets from passing cars; reconciled under `ATT-8679` with `REQ-TRK-062` clear-state badge suppression.
3. *Root Reason for Existing Formulation*: Snooze logic was originally housed in `RadarAlertManager` for convenience alongside chime playback; gating checks were placed at method entry, inadvertently suppressing haptics; `disconnectFromGatt()` cleared snooze state assuming all state was session-bound; badge text replaced vehicle count because a combined layout had not yet been designed.
4. *Preservation of Core Invariants*: 100% visual target rail continuity preserved; `FAST_APPROACHING` safety chimes and haptics always sound; zero native `AudioTrack` leaks; zero SQLite migrations; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the app is in `READY` or `PAUSED` mode with radar connected and `RadarLightQuickSwitchSheet` opened (via `RadarLightButton` long-press or threat-state `RadarEdgeOverlay` badge),
• *When* the user selects "Snooze 15 min",
• *Then* `RadarSnoozeController` activates 15m snooze, the state persists when tracking starts, and `RadarStatusBadgePill` displays remaining time and a muted bell icon alongside `targets.size` whenever threats approach (while clear-state green badge remains suppressed per `REQ-TRK-062.3`).
• *Given* active audio snooze or Peloton mode mute with haptics enabled,
• *When* a vehicle approaches at normal speed,
• *Then* the audio chime is silenced but haptic vibration triggers normally.
• *Given* active Peloton or Urban auto-mute,
• *When* the user taps "Resume Audio Alerts",
• *Then* audio chimes resume immediately and remain active until the next radar state transition.
• *Given* audio snooze is active and a vehicle approaches,
• *Then* `RadarStatusBadgePill` continues to display the approaching vehicle count alongside the muted-bell indicator.
• *Given* an active 15m or 30m audio snooze,
• *When* the system wall clock jumps backwards or forwards by 1 hour (NTP sync / timezone change),
• *Then* `RadarSnoozeController` remaining snooze duration and expiration remain strictly governed by monotonic elapsed time.

**Invariants**: Visual target rail remains 100% active during audio snooze; `FAST_APPROACHING` high-speed threat safety alerts preserved; haptic vibrations preserved during audio snooze; zero SQLite schema modifications; 100% 9-language translation parity.

### REQ-TRK-065: 3D Stereo Lateral-Offset Panning & Approach-Speed Pitch Modulation for Radar Audio [Verified]
- **Tests**: `TST-TRK-065`
- **System Requirement(s)**: `SYSREQ-TRK-006`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarSpatialAudioMath.kt`, `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Standard mono radar beeps only signal vehicle proximity, requiring cyclists with headphones to divert their gaze from the roadway to assess whether an approaching vehicle is directly behind them or has pulled into the passing lane, and how rapidly it is closing. By decoding BLE V2 lateral lane offset $x$ and approach speed $v_{\text{rel}}$, the system delivers intuitive 3D spatial audio panning and speed-adaptive pitch modulation directly over stereo and bone-conduction headphones without compromising visual rail tracking or maximum phone speaker output (ATT-5120, Parent Epic ATT-1286, extending `REQ-TRK-063` and `REQ-CON-030`):
1. *Stereo Lateral-Offset Panning & Speaker Gain Collapsing (`REQ-TRK-065.1`)*:
• When stereo audio output is active and `RadarTarget.lateralOffsetMeters` ($x$) is available from the BLE V2 stream (`6a4e3204`), the system SHALL compute left and right channel gains `(leftGain, rightGain)` according to lateral offset $x$ and configured traffic orientation:
  - In-Lane ($|x| < 1.0\text{ m}$): Centered stereo `(1.0, 1.0)`.
  - Passing Lane ($|x| \ge 1.5\text{ m}$): Panned toward the overtaking ear `(1.0, 0.20)` for Right-Hand Traffic (left ear) or `(0.20, 1.0)` for Left-Hand Traffic (right ear).
  - Linear transition attenuation between $1.0\text{ m}$ and $1.5\text{ m}$.
  - When `route == FORCE_SPEAKER`, or 3D spatial audio is disabled, or `route == SYSTEM_DEFAULT` when no external headphones/Bluetooth audio devices are connected, stereo gains SHALL collapse to centered `(1.0, 1.0)` to preserve maximum phone speaker loudness.
2. *Approach-Speed Pitch & Tempo Modulation (`REQ-TRK-065.2`)*:
• When `radarSpeedPitchModulation` is enabled (default `true`), the system SHALL scale playback pitch and tempo linearly across relative approach speeds $3.0\text{ m/s} \le v_{\text{rel}} \le 30.0\text{ m/s}$ ($11\text{--}108\text{ km/h}$) to pitch range `0.95x .. 1.35x` using `AudioTrack.setPlaybackParams()` without rebuilding static PCM buffers.
3. *Lane-Change Confirmation Spatial Cue & Multi-Target Hysteresis (`REQ-TRK-065.3`)*:
• When `radarLaneChangeTickEnabled` is enabled (default `true`), transitioning a tracked vehicle from `IN_RIDER_LANE` ($|x| < 1.0\text{ m}$) to `CHANGING_LANE_SAFE` ($|x| \ge 1.5\text{ m}$) SHALL emit a subtle directional spatial tick cue in the overtaking ear. `RadarAlertManager` SHALL track per-target `LaneTrackState` across multi-car convoys with spatial boundary hysteresis and a 3,000 ms per-target cooldown, pruning departed target IDs to prevent 8 Hz jitter fluttering.
4. *DataStore Persistence & Traffic Orientation Configuration (`REQ-TRK-065.4`)*:
• `TuningPreferencesDataStore` SHALL persist `radarSpatialAudioEnabled` (Boolean, default `true`), `radarSpeedPitchModulation` (Boolean, default `true`), `radarTrafficOrientation` (String/enum `RIGHT_HAND`/`LEFT_HAND`, default `RIGHT_HAND`), and `radarLaneChangeTickEnabled` (Boolean, default `true`), exposed in `RadarSettingsSection.kt`.
5. *Interactive Audio Preview Controls & Lifecycle Management (`REQ-TRK-065.5`)*:
• `RadarSettingsSection.kt` SHALL provide interactive audio preview controls (Centered In-Lane, Passing Lane, Slow Approach, Fast Approach, Lane Change Tick) rendered as `AssistChip` action buttons per Design Guidelines §1.2. `RadarAlertManager` SHALL expose `releasePreviewSynthesizer()` invoked via `DisposableEffect` on Settings disposal to release native `AudioTrack` resources. Subordinate settings nested under spatial audio SHALL render as `SubordinateTuningToggleItem` per Design Guidelines §3.3. Deprecated `setStereoVolume()` on mono tracks SHALL be replaced with non-deprecated `setVolume()`.
6. *9-Language Localization Parity (`REQ-TRK-065.6`)*:
• All spatial audio toggles, traffic orientation labels, and audio preview buttons in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-TRK-063` (Radar Alert Engine) and `REQ-TRK-065` (Spatial Audio & Speed Pitch Modulation), extending `REQ-CON-028` and `REQ-CON-030`. Targets `RadarSpatialAudioMath.kt`, `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, and `RadarSettingsSection.kt`.
2. *Historical Origin & Commit Trace*: `ATT-5120` introduced spatial audio panning and pitch modulation in commit `28fb9956`; `ATT-5086` introduced the core alert manager in commit `85790a1b`; `ATT-6709` refines speaker gain collapse on `SYSTEM_DEFAULT`, `FAST_APPROACHING` kinematics selection, multi-car lane tracking with cooldown, and `AudioTrack` cleanup.
3. *Root Reason for Existing Formulation*: `FORCE_SPEAKER` was the only explicit phone speaker route initially anticipated, leaving `SYSTEM_DEFAULT` without headphones subject to stereo attenuation. Closest-target selection was used uniformly for simplicity without considering multi-vehicle fast-threat scenarios. Stateless `FilterChip`s and `Switch` rows were used before Design Guidelines §1.2 and §3.3 standardized subordinate and action patterns.
4. *Preservation of Core Invariants*: Full phone speaker volume preserved; zero regression on headphone stereo panning; 100% 9-language translation parity; zero native `AudioTrack` resource leaks.

**Acceptance Criteria (Given-When-Then)**:
• *Given* 3D spatial audio enabled and Right-Hand Traffic configured,
• *When* an approaching vehicle shifts laterally from $x = 0.0\text{ m}$ to $x = -2.0\text{ m}$,
• *Then* the audio chime pans from centered `(1.0, 1.0)` toward the left ear `(1.0, 0.20)`.
• *Given* speed-adaptive pitch modulation enabled,
• *When* a vehicle approaches at $25.0\text{ m/s}$ ($90\text{ km/h}$) versus $5.0\text{ m/s}$ ($18\text{ km/h}$),
• *Then* the alert chime playback pitch scales from `~1.0x` to `~1.28x`.
• *Given* `route == FORCE_SPEAKER` or `route == SYSTEM_DEFAULT` without headphones connected,
• *When* a passing vehicle triggers an alert,
• *Then* stereo gains remain centered `(1.0, 1.0)` to preserve full phone speaker volume.
• *Given* multiple approaching targets including a slow trailing car and a fast approaching threat,
• *When* escalating to `FAST_APPROACHING`,
• *Then* `RadarAlertManager` selects the kinematics of the `FAST_APPROACHING` target to drive alert pitch and panning.
• *Given* `RadarSettingsSection` leaving composition,
• *When* disposed,
• *Then* `RadarAlertManager.releasePreviewSynthesizer()` is invoked releasing native `AudioTrack`s.

**Invariants**: Warm `MODE_STATIC` replay latency preserved without AudioFlinger port teardown; zero volume reduction on phone speaker; zero SQLite schema modifications; 100% 9-language translation parity; zero native resource leaks.

### REQ-TRK-066: Repository-Only Live Recording Service Database Access Layer (Phase C) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-066`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`, `app/src/main/java/com/atrainingtracker/banalservice/BANALService.java`, `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/WorkoutRepository.kt`, `app/src/main/java/com/atrainingtracker/banalservice/repositories/SportTypesRepository.kt`, `app/src/main/java/com/atrainingtracker/banalservice/repositories/DeviceDataRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/WorkoutRrRepository.kt`
- **Rationale**: Direct instantiation of SQLite database manager singletons (`*DatabaseManager.getInstance()`) and raw `Cursor` queries within foreground services (`TrackerService`, `BANALService`) and hardware coordinators (`DeviceManager`) violate SWE.2 Clean Architecture boundaries, couple service lifecycles to low-level SQLite helpers, and impede automated unit testing. All persistence operations across Phase C live recording components SHALL route through domain repositories, and raw `Cursor` extractions for paired devices SHALL be encapsulated behind domain DTOs (`PairedRemoteDeviceDto`).
- **Description**:
  1. *TrackerService Persistence Decoupling (`REQ-TRK-066.1`)*:
     - `TrackerService.java` SHALL eliminate all direct calls to `WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, `SportTypeDatabaseManager`, `ActiveDevicesDbHelper`, `WorkoutRrDatabaseManager`, and `KnownLocationsDatabaseManager`.
     - All session initialization, crash-resume rehydration, lap persistence, 1-Hz sample recording, extrema updates, error logging, active device binding, and session finalization SHALL route through `WorkoutRepository`, `SportTypesRepository`, `DeviceDataRepository`, `KnownLocationsRepository`, and `WorkoutRrRepository`.
  2. *BANALService Sport Type Decoupling (`REQ-TRK-066.2`)*:
     - `BANALService.java` SHALL eliminate all direct calls to `SportTypeDatabaseManager.getInstance(...)`.
     - Sport type identifier resolution, UI names, default sport type lookups, and active sport type lists SHALL route through `SportTypesRepository`.
  3. *DeviceManager Decoupling & PairedRemoteDeviceDto (`REQ-TRK-066.3`)*:
     - `DeviceManager.java` SHALL eliminate all direct calls to `DevicesDatabaseManager.getInstance(...)` and raw SQLite queries on `DevicesDbHelper.DEVICES`.
     - `DeviceDataRepository` SHALL expose `getPairedRemoteDevices(protocol)` returning a list of strongly-typed `PairedRemoteDeviceDto` instances, encapsulating device ID, protocol, device type, ANT device number, and Bluetooth MAC address with zero leaky Cursors or SQLite handles.
  4. *High-Frequency Execution & Invariant Preservation (`REQ-TRK-066.4`)*:
     - High-frequency 1-Hz sample recording delegates in `WorkoutRepository` SHALL execute on `TrackerService.mDbExecutor` with zero thread contention or dropped samples.
     - Core system invariants SHALL remain strictly preserved: unconditional `FINISHED = 1` commitment in a minimal transaction (`REQ-TRK-030.1`), mid-workout PRAGMA table info schema discovery (`REQ-TRK-026.4` / `REQ-TRK-051.1`), and bit-exact crash resume (`REQ-TRK-031.3`).
  5. *Zero Backdoor Getters & Service Architecture Cleanliness (`REQ-TRK-066.5`)*:
     - Domain repositories SHALL NOT expose public or internal `get*DatabaseManager()`, `get*DbHelper()`, `SQLiteDatabase`, or raw `Cursor` getters to service callers (adhering to coding standards §2.5).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TrackerService.java`, `BANALService.java`, and `DeviceManager.java`,
• *When* auditing source code and AST references,
• *Then* zero occurrences of `*DatabaseManager.getInstance(...)` or `*DbHelper.getInstance(...)` exist across all Phase C service files.
• *Given* `DeviceManager.createPairedRemoteDevices(...)`,
• *When* querying paired ANT+ or Bluetooth LE devices,
• *Then* `DeviceDataRepository.getPairedRemoteDevices(protocol)` returns typed `PairedRemoteDeviceDto` objects with zero direct `Cursor` access.
• *Given* an active workout recording session,
• *When* 1-Hz telemetry samples are captured, laps are saved, or a crash-resume occurs,
• *Then* all summary, sample, and lap persistence operate bit-exact and functionally identical to previous database manager behavior.

**Invariants**: Unconditional `FINISHED = 1` transaction committed before metadata updates (`REQ-TRK-030.1`); 1-Hz write cadence on `mDbExecutor` preserved; zero SQLite schema modifications; zero dropped telemetry samples; 100% unit test pass rate.

### REQ-TRK-067: Firmware-Smoothed & Quantized Synthetic RR Stream Detection (HrvSignalQualityAnalyzer) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-067`
- **System Requirement(s)**: `SYSREQ-TRK-006`, `SYSREQ-TRK-012`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/devices/hrv/HrvSignalQualityAnalyzer.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/hrv/HrvSignalQualityResult.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/HRVMetricsDevice.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/morninghrv/MorningHrvCheckViewModel.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/hrv/HrvPostWorkoutRecomputer.kt`
- **Rationale**: Budget dual-band ECG chest straps (e.g. Magene H64/H303, CooSpo) apply internal moving-average smoothing to beat-to-beat intervals in firmware prior to BLE `0x2A37` transmission, while some optical/budget sensors synthesize stepped RR intervals from integer BPM ($RR = 60000 / \text{BPM}$). Because smoothed intervals lack abrupt spike outliers, the Lipponen & Tarvainen (2019) artifact detector reports 0.0% artifacts (green light) while adjacent interval differences collapse ($rMSSD$ 3–8 ms even at rest) and short-scale variance $F(4)$ is erased, artificially inflating DFA $\alpha_1$ into a smooth Brownian regime (1.40–1.85). In real human physiology, $rMSSD < 10.0\text{ ms}$ and DFA $\alpha_1 > 1.35$ are mutually exclusive (intense sympathetic exercise collapses $\alpha_1 \to 0.50$, never $> 1.35$). The system must detect smoothed and quantized synthetic streams using Turning-Point Ratio ($TPR$), zero-diff rate, and discordance gates without falsely penalizing authentic exercise or resting ECG.
- **Description**:
  1. *Stateless Signal Quality Classifier (`REQ-TRK-067.1`)*:
     - `HrvSignalQualityAnalyzer.kt` SHALL provide a pure, stateless evaluation function analyzing a window of RR intervals and returning structured `HrvSignalQualityResult` (`status: HrvSignalQualityStatus`, `tpr: Double`, `zeroDiffRate: Double`, `rmssdMs: Double`, `dfaAlpha1: Double`, `artifactRate: Double`).
     - Supported status values SHALL include `VALID`, `INSUFFICIENT_DATA`, `HIGH_ARTIFACTS`, `SMOOTHED_STREAM`, and `QUANTIZED_SYNTHETIC`.
  2. *Data Sufficiency & Artifact Pre-Gating (`REQ-TRK-067.2`)*:
     - If the beat sequence contains fewer than 30 beats ($N < 30$), the analyzer SHALL return status `INSUFFICIENT_DATA`.
     - If the artifact rate exceeds 5.0% ($> 0.05$), the analyzer SHALL return status `HIGH_ARTIFACTS`.
  3. *Smoothed Stream Detection via Discordance & Turning-Point Ratio (`REQ-TRK-067.3`)*:
     - The analyzer SHALL flag the window as `SMOOTHED_STREAM` if:
       a. Discordance: $rMSSD < 10.0\text{ ms}$ AND DFA $\alpha_1 > 1.35$ AND accompanied by either suppressed Turning-Point Ratio ($TPR < 0.28$) or extreme variance collapse ($rMSSD < 2.0\text{ ms}$); OR
       b. Turning-Point Ratio ($TPR$) of consecutive non-zero differences $\Delta RR_i = RR_i - RR_{i-1}$ satisfies $TPR < 0.20$ alongside DFA $\alpha_1 > 1.30$.
  4. *Quantized Synthetic RR Detection (`REQ-TRK-067.4`)*:
     - The analyzer SHALL flag the window as `QUANTIZED_SYNTHETIC` if more than 55% ($> 0.55$) of consecutive beat pairs satisfy $|\Delta RR_i| < 1.0\text{ ms}$ over $\ge 30$ beats.
  5. *Preservation of Authentic Physiology Invariants (`REQ-TRK-067.5`)*:
     - Authentic resting ECG ($rMSSD \approx 42\text{ ms}$, $\alpha_1 \approx 1.12$, normal RSA turning points) SHALL evaluate to `VALID`.
     - Authentic high-intensity exercise ECG ($rMSSD \approx 4.5\text{ ms}$, $\alpha_1 \approx 0.48$) SHALL evaluate to `VALID` and MUST NEVER be falsely flagged as `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
     - Authentic resting sinus rhythm under sympathetic/evening/orthostatic conditions with low vagal tone and baroreflex Mayer-wave drift ($rMSSD < 10.0\text{ ms}$, DFA $\alpha_1 > 1.35$, $TPR \ge 0.28$, e.g. Polar H10 with $rMSSD = 4.5\text{ ms}$, $\alpha_1 = 1.52$, $TPR = 0.38$) SHALL evaluate to `VALID`.
  6. *Engine Telemetry & Service Wiring (`REQ-TRK-067.6`)*:
     - `HRVMetricsDevice.kt` SHALL expose `@Volatile var signalQualityResult: HrvSignalQualityResult` updated on each 5s evaluation tick, resetting to default state upon `resetSession()` (`Rule 51`/`52`).
     - `MorningHrvCheckViewModel.kt` SHALL evaluate signal quality at completion of 60s/120s measurements and hold the status in `MorningHrvUiState`.
     - `HrvPostWorkoutRecomputer.kt` SHALL expose helper evaluation for post-workout RR streams.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an empirical Magene-style smoothed series ($rMSSD \approx 3\text{--}8\text{ ms}$, $\alpha_1 > 1.35$ with $TPR < 0.28$, or low $TPR < 0.20$ with $\alpha_1 > 1.30$),
• *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`,
• *Then* `status` equals `SMOOTHED_STREAM`.
• *Given* an authentic resting ECG series with baroreflex Mayer drift ($rMSSD = 4.5\text{ ms}$, $\alpha_1 = 1.52$, $TPR = 0.38$),
• *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`,
• *Then* `status` equals `VALID` (no false-positive smoothed sensor warning).
• *Given* a quantized constant-BPM synthetic series derived from $RR = 60000 / \text{integer\_BPM}$ with $>55\%$ $|\Delta RR| < 1.0\text{ ms}$,
• *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`,
• *Then* `status` equals `QUANTIZED_SYNTHETIC`.
• *Given* an authentic resting ECG series ($rMSSD \approx 42\text{ ms}$, $\alpha_1 \approx 1.12$),
• *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`,
• *Then* `status` equals `VALID`.
• *Given* an authentic high-intensity exercise ECG series ($rMSSD \approx 4.5\text{ ms}$, $\alpha_1 \approx 0.48$),
• *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`,
• *Then* `status` equals `VALID` (never falsely flagged as smoothed or synthetic).
• *Given* an RR stream with artifact rate $> 5.0\%$,
• *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`,
• *Then* `status` equals `HIGH_ARTIFACTS`.
• *Given* an RR stream with $< 30$ beats,
• *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`,
• *Then* `status` equals `INSUFFICIENT_DATA`.

**Requirement Archaeology & Chesterton's Fence (REQ-PRO-022)**:
- `Original Requirement ID & Target`: `REQ-TRK-067.3` (Discordance Gate, ATT-6744).
- `Historical Origin & Commit Trace`: Commit `5551c89e` (`ATT-6744`, Sprint 2026-46.3).
- `Root Reason for Existing Formulation`: Unconditionally assumed $rMSSD < 10\text{ ms}$ and $\alpha_1 > 1.35$ were physiologically discordant, based on high-intensity exercise dynamics where $\alpha_1 \to 0.50$, overlooking resting/orthostatic Mayer-wave baroreflex drift where $\alpha_1$ legitimately exceeds $1.35$ while $TPR \ge 0.28$.
- `Preservation of Core Invariants`: Firmware-smoothed series ($TPR < 0.20$ or discordant $TPR < 0.28$) remain 100% detected; authentic resting sinus rhythm is preserved without false alerts.

**Invariants**: Zero false-positive smoothing flags during authentic intense exercise; Rule 51 (state seeding parity) & Rule 52 (presence gating before RR arrival) preserved in `HRVMetricsDevice`; zero SQLite schema modifications; 100% unit test pass rate.

### REQ-TRK-068: Smoothed RR Stream Warnings & Sensor Compatibility Guidance across Presentation Surfaces [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-068`
- **System Requirement(s)**: `SYSREQ-TRK-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvInfoBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/MorningHrvCheckScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvAlpha1ScatterCard.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/cockpit/HrvZoneIndicator.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/viewmodel/TrackingViewModel.kt`, `app/src/main/res/values*/strings_hrv.xml`
- **Rationale**: When athletes pair budget ECG chest straps applying firmware moving-average smoothing (e.g. Magene H64/H303, CooSpo) or synthetic stepped RR streams, the outlier filter reports 0.0% artifacts while DFA $\alpha_1$ is falsely inflated into an easy aerobic Zone 2 (> 1.40) and $rMSSD$ collapses. Without explicit UI warnings, athletes pacing live efforts receive deceptive guidance, morning readiness baselines ($SWC$) are polluted with artificially low $rMSSD$, and athletes are unaware their sensor hardware is unsuitable for aerobic threshold estimation. The presentation layer must surface prominent cautionary warnings across all 4 touchpoints and provide clear sensor hardware recommendations with 9-language parity.
- **Description**:
  1. *HrvInfoBottomSheet Sensor Recommendation Modernization (`REQ-TRK-068.1`)*:
     - `HrvInfoBottomSheet.kt` and `strings_hrv.xml` (`hrv_info_sensor_recommendation_desc`) SHALL explicitly distinguish recommended unsmoothed ECG straps (Polar H10, Polar H9, Garmin HRM-Dual, HRM-Pro, HRM-Pro Plus) from unsuitable sensors (optical wrist sensors, Magene, CooSpo firmware-smoothed straps).
  2. *Live Cockpit Smoothed Stream Warning & Zone Suppression (`REQ-TRK-068.2`)*:
     - `HrvInfoBottomSheet.kt` SHALL render a top warning banner when the active device `signalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
     - `HrvZoneIndicator.kt` and `TrackingViewModel.kt` SHALL suppress/caution the green aerobic Zone 2 indicator when `isSmoothedStream` is true, replacing misleading green feedback with a neutral warning state.
  3. *Morning HRV Check Smoothed Warning Card & Dynamic Action Button Prominence (`REQ-TRK-068.3`)*:
     - `MorningHrvCheckScreen.kt` SHALL render a dedicated `SmoothedSignalWarningCard` when measurement completion yields `signalQualityStatus == SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`, cautioning the athlete that firmware smoothing suppresses $rMSSD$ and advising against saving into longitudinal readiness baselines.
     - When discard is recommended (`recommendDiscard == true`, due to `highArtifactWarning` or `signalQualityStatus` in `SMOOTHED_STREAM`, `QUANTIZED_SYNTHETIC`, `HIGH_ARTIFACTS`) and measurement is unsaved:
       a. The *Verwerfen* (Discard) button SHALL be visually emphasized as the primary filled `Button`.
       b. The *Speichern* (Save) button SHALL be de-emphasized as secondary `OutlinedButton`.
     - When discard is not recommended (`recommendDiscard == false`), *Verwerfen* SHALL remain `OutlinedButton` and *Speichern* SHALL remain primary filled `Button`.
     - Action buttons SHALL enforce `Modifier.heightIn(min = 52.dp)` and button text SHALL specify `maxLines = 1` with `overflow = TextOverflow.Ellipsis`, preventing multi-line wrapping and vertical slicing/clipping.
  4. *Post-Workout DFA Alpha 1 Scatter Warning Banner (`REQ-TRK-068.4`)*:
     - `HrvAlpha1ScatterCard.kt` SHALL render an inline warning banner when workout RR intervals were detected as firmware-smoothed or synthetic (`isSmoothedStream == true`), indicating that $\alpha_1 = 0.75$ ($HRVT_1$) and $\alpha_1 = 0.50$ ($HRVT_2$) threshold estimates are uncalibrated.
  5. *9-Language Localization Parity & Copy Polish (`REQ-TRK-068.5`)*:
     - All user-facing strings across warnings and sensor recommendations SHALL maintain 100% parity across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
     - `morning_hrv_subtitle` SHALL use natural resting guidance phrasing across all 9 locales (DE: *"Ruhemessung direkt nach dem Aufwachen"*; EN: *"Resting measurement right after waking"*).
     - `ReadinessResultCard` SHALL display dedicated title `morning_hrv_result_title` (DE: *"Messergebnis"*; EN: *"Measurement Result"*) rather than duplicating `morning_hrv_state_completed`.
     - `morning_hrv_btn_save` SHALL be shortened to a single-word verb (DE: *"Speichern"*; EN: *"Save"*) matching *"Verwerfen"* across all 9 locales.
  6. *Presentation Invariants & Theme Parity (`REQ-TRK-068.6`)*:
     - Warning banners SHALL use centralized `TTColor.ArtifactWarning*` / `TTColor.Warning` theme tokens (Rule 71).
     - Standard zone pacing on valid unsmoothed streams and independent $> 5\%$ artifact warning cards SHALL continue to function without regression.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `HrvInfoBottomSheet`,
• *When* the sensor recommendation section is rendered,
• *Then* it explicitly lists Polar H10/H9 and Garmin HRM-Dual/Pro as recommended, and optical sensors and Magene/CooSpo smoothed straps as unsuitable.
• *Given* `HRVMetricsDevice.signalQualityResult.status == SMOOTHED_STREAM`,
• *When* opening `HrvInfoBottomSheet` or viewing live DFA $\alpha_1$ cockpit tile,
• *Then* `HrvInfoBottomSheet` displays a top warning banner and the cockpit suppresses the green Zone 2 indicator.
• *Given* `MorningHrvCheckViewModel` completes with `signalQualityStatus == SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`,
• *When* rendering `MorningHrvCheckScreen`,
• *Then* a dedicated `SmoothedSignalWarningCard` is displayed above the readiness actions.
• *Given* `MorningHrvCheckScreen` with `recommendDiscard == true`,
• *When* rendering completion action buttons,
• *Then* *Verwerfen* is rendered as the primary filled `Button` and *Speichern* is rendered as secondary `OutlinedButton`.
• *Given* the German locale on `MorningHrvCheckScreen`,
• *When* viewing the top bar and completed result card,
• *Then* the subtitle reads *"Ruhemessung direkt nach dem Aufwachen"*, the card title reads *"Messergebnis"*, and the save button reads *"Speichern"* on a single line without vertical clipping.
• *Given* a workout session with smoothed RR intervals,
• *When* `HrvAlpha1ScatterCard` is displayed,
• *Then* an inline warning banner informs the user that threshold estimates are uncalibrated due to sensor smoothing.
• *Given* all 9 supported locales,
• *When* checking `strings_hrv.xml`,
• *Then* all new warning and recommendation strings have 100% key and format-specifier parity.

**Requirement Archaeology & Chesterton's Fence (REQ-PRO-022)**:
- `Original Requirement ID & Target`: `REQ-TRK-068.3` & `REQ-TRK-068.5` (Morning HRV warnings and strings, ATT-6745).
- `Historical Origin & Commit Trace`: Commit `fb6797a7` (`ATT-6745`, Sprint 2026-46.3).
- `Root Reason for Existing Formulation`: ATT-6745 introduced warning cards and action buttons with fixed `52.dp` height and static save prominence, without dynamic discard highlighting when bad data was detected, and used literal translations that caused two-line button text clipping.
- `Preservation of Core Invariants`: Theme colors, warning card conditions, and standard save workflows are preserved while UI ergonomics and button hierarchy are aligned with recommended user actions.

**Invariants**: Independent $> 5\%$ artifact warnings preserved; Rule 71 theme tokens enforced; Rule 72 modal sheet scrolling safety maintained; zero SQLite schema modifications; 100% unit test pass rate.

### REQ-TRK-069: ANT+ UTC Epoch Timestamping, Reconnection Sensor Registration & BLE Bundled Drift Guard [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-069`
- **System Requirement(s)**: `SYSREQ-TRK-003`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/devices/ANTHeartRateDevice.java`, `app/src/main/java/com/atrainingtracker/banalservice/devices/BTLEHeartRateDevice.java`, `app/src/main/java/com/atrainingtracker/banalservice/devices/hrv/HRVMetricsDevice.kt`
- **Rationale**: Real-time HRV metrics (`rMSSD`, `DFA a1`) and lossless `WorkoutRrIntervals` persistence require accurate UTC Unix epoch timestamps across hardware acquisition interfaces. Under `ATT-3540`/`ATT-6742`, ANT+ `estTimestamp` was passed as raw boot-relative time (`SystemClock.elapsedRealtime()`), causing immediate eviction in `HRVMetricsDevice.pruneOldBeats()` and near-1970 timestamp corruption. Additionally, `unregisterSensors()` in both ANT+ and BLE drivers failed to clear `mRrIntervalSensor`, preventing sensor re-registration upon device reconnection. Finally, BLE bundled RR packets anchored the first beat instead of the newest beat and accumulated integer millisecond rounding errors, triggering spurious `ARTIFACT_GAP` drift events after 30–40 min. Hardware drivers must convert ANT+ timestamps to UTC epoch ms, reset sensor handles on disconnect, anchor bundled BLE packets to arrival time, and accumulate timestamps with sub-millisecond precision.
- **Description**:
  1. *ANT+ estTimestamp UTC Epoch Conversion (`REQ-TRK-069.1`)*:
     - `ANTHeartRateDevice.java` SHALL convert `estTimestamp` from boot-relative monotonic time to UTC epoch milliseconds: `epochOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()`, setting `beatTimestampMs = (estTimestamp > 0) ? (epochOffset + estTimestamp) : System.currentTimeMillis()`.
     - When `estTimestamp <= 0` or upon gap recovery / `DataState.ZERO_DETECTED`, `beatTimestampMs` SHALL fall back safely to `System.currentTimeMillis()`.
     - ANT+ beats delivered to `HRVMetricsDevice` SHALL survive `pruneOldBeats(now)` evaluation over the 120s sliding window without spurious eviction.
  2. *Driver Reconnection Sensor Re-Registration (`REQ-TRK-069.2`)*:
     - `ANTHeartRateDevice.unregisterSensors()` and `BTLEHeartRateDevice.unregisterSensors()` SHALL set `mRrIntervalSensor = null`.
     - On sensor reconnection and subsequent `registerSensors()` execution, `mRrIntervalSensor` SHALL be newly instantiated and registered with `addAndRegisterSensor`, ensuring continuous RR beat streaming after reconnect.
  3. *BLE Bundled RR Backward Anchoring & Sub-ms Drift Guard (`REQ-TRK-069.3`)*:
     - In `BTLEHeartRateDevice.java`, when receiving bundled RR packets ($K > 1$), the most recent beat ($K$) SHALL be anchored to `packetArrivalMs`, and prior beats ($1 \dots K-1$) SHALL be back-calculated in reverse: `t_{k-1} = t_k - RR_k`.
     - Continuous timestamp accumulation SHALL maintain floating-point (`double`) precision to prevent integer round-off drift accumulation, guaranteeing that drift against phone arrival clock remains $< 2000\text{ ms}$ over $\ge 60\text{ min}$ of continuous streaming without packet loss.
  4. *End-to-End ANT+ & BLE Integration Integrity (`REQ-TRK-069.4`)*:
     - Integration tests wiring `ANTHeartRateDevice` into `HRVMetricsDevice` SHALL verify that valid ANT+ beat streams compute non-zero `rMSSD` and valid `DFA a1` in real-time.
     - Core invariants (`deltaC == 1` continuity guard, `ARTIFACT_GAP` on packet drop, optical sensor fallback `flag = 0x00`) SHALL remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ANTHeartRateDevice` receiving an RR event with `estTimestamp > 0`,
• *When* calculating `beatTimestampMs`,
• *Then* `beatTimestampMs` equals `(System.currentTimeMillis() - SystemClock.elapsedRealtime()) + estTimestamp` within $\pm 50\text{ ms}$ of `System.currentTimeMillis()`.
• *Given* `ANTHeartRateDevice` connected to `HRVMetricsDevice`,
• *When* continuous ANT+ beats are streamed over 120 seconds,
• *Then* `HRVMetricsDevice.pruneOldBeats()` retains all active window beats and computes valid real-time `rMSSD` and `DFA a1`.
• *Given* `ANTHeartRateDevice` or `BTLEHeartRateDevice`,
• *When* `unregisterSensors()` is called followed by `registerSensors()`,
• *Then* `mRrIntervalSensor` is not null and is active in `MyDevice`.
• *Given* `BTLEHeartRateDevice` receiving a bundled packet of 3 RR intervals at `t = 10000 ms` with intervals [800 ms, 810 ms, 820 ms],
• *When* timestamps are computed,
• *Then* Beat 3 has timestamp `10000 ms`, Beat 2 has `9180 ms`, and Beat 1 has `8370 ms`.
• *Given* `BTLEHeartRateDevice` running a continuous 60-minute session (3600 beats) without packet loss,
• *When* tracking cumulative drift against arrival time,
• *Then* $|t_{\text{cumulative}} - t_{\text{arrival}}| < 2000\text{ ms}$ and zero false `ARTIFACT_GAP` events are emitted.

**Invariants**: `deltaC == 1` ANT+ continuity check preserved; `ARTIFACT_GAP` emitted on genuine packet drop; 1-Hz HR display unaffected; optical sensor fallback intact; zero SQLite schema modifications; 100% unit test pass rate.

### REQ-TRK-070: 2-Level Sensor Hierarchy (SensorSubCategory), Unified HRV/Radar Groups & Complete ActivityType Sensor Mapping [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-070`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorCategory.java`, `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorSubCategory.java`, `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`, `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
- **Rationale**: Since `ATT-3852` introduced the 7-category accordion in `EditSensorFieldDialog.kt` (`REQ-UI-395`), telemetry metrics have grown from 43 to 63 constants (50 cockpit-selectable), creating significant UX and architectural friction: `POWER_AND_PEDALING` contains 22 flat metrics, and `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` contains 16 mixed metrics; HRV metrics are fragmented across Heart Rate (`HRV_DFA_A1`, `HRV_RMSSD`) and System Diagnostics (`RR_INTERVAL`, `HRV_ARTIFACT_RATE`); `CADENCE` is stranded under a running icon in cycling; `ELEVATION_AND_NAVIGATION` mentions navigation without selectable navigational coordinates; 10 telemetry metrics (8 power duration metrics `POWER_5S..20M`, `POWER_PEAK_5S..20M` and 2 radar v2 metrics `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) are missing from `ActivityType.getSensorTypeArray()`; `CALORIES` is commented out across 3 profiles; and athletes have no visual indicators in the picker for Bar (`isBarSupported`) or Graph (`isGraphSupported`) capabilities. The system must establish a 2-level taxonomy (`SensorCategory` and `SensorSubCategory`), wire all missing telemetry into `ActivityType`, and provide a 2-level accordion with capability badges in `EditSensorFieldDialog.kt`.
- **Description**:
  1. *Authoritative 6-Group Top-Level Category Taxonomy (`REQ-TRK-070.1`)*:
     - `com.atrainingtracker.banalservice.sensor.SensorCategory` SHALL define exactly 6 top-level categories:
       1. `POWER_AND_PEDALING` (`R.string.sensor_category_power_and_pedaling`, `R.drawable.ic_power`)
       2. `HEART_RATE_AND_ENERGY` (`R.string.sensor_category_heart_rate_and_energy`, `R.drawable.ic_heart_rate`): unifies all 5 HRV metrics (`HRV_DFA_A1`, `HRV_RMSSD`, `RR_INTERVAL`, `HRV_ARTIFACT_RATE`, `LIVE_HR_DRIFT`), heart rate (`HR`), and energy (`CALORIES`).
       3. `SPEED_PACE_AND_DISTANCE` (`R.string.sensor_category_speed_pace_and_distance`, `R.drawable.ic_speed`): merges `CADENCE` and `STRIDES`, eliminating stranded cadence.
       4. `TIME_AND_LAPS` (`R.string.sensor_category_time_and_laps`, `R.drawable.ic_time_active`)
       5. `ELEVATION` (`R.string.sensor_category_elevation`, `R.drawable.ic_altitude`): renamed from `ELEVATION_AND_NAVIGATION`.
       6. `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` (`R.string.sensor_category_system_environment_and_diagnostics`, `R.drawable.ic_battery_full`): radar safety, battery, ambient temperature, and system diagnostics.
     - Legacy enums `CADENCE_AND_RUNNING` and `ELEVATION_AND_NAVIGATION` SHALL be eliminated.
  2. *SensorSubCategory Hierarchy & SensorType Mapping (`REQ-TRK-070.2`)*:
     - `com.atrainingtracker.banalservice.sensor.SensorSubCategory` SHALL define 9 sub-categories with localized title resource IDs (`getTitleResId()`):
       1. `POWER_LOAD` (`R.string.sensor_sub_category_power_load`): `NORMALIZED_POWER`, `POWER_TO_WEIGHT`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`
       2. `POWER_ROLLING` (`R.string.sensor_sub_category_power_rolling`): `POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`
       3. `POWER_PEAK` (`R.string.sensor_sub_category_power_peak`): `POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`
       4. `PEDALING_DYNAMICS` (`R.string.sensor_sub_category_pedaling_dynamics`): `TORQUE`, `PEDAL_POWER_BALANCE`, `PEDAL_SMOOTHNESS`, `PEDAL_SMOOTHNESS_L`, `PEDAL_SMOOTHNESS_R`, `TORQUE_EFFECTIVENESS_L`, `TORQUE_EFFECTIVENESS_R`
       5. `HRV_AND_DRIFT` (`R.string.sensor_sub_category_hrv_and_drift`): `HRV_DFA_A1`, `HRV_RMSSD`, `RR_INTERVAL`, `HRV_ARTIFACT_RATE`, `LIVE_HR_DRIFT`
       6. `LAP_METRICS` (`R.string.sensor_sub_category_lap_metrics`): `TIME_LAP`, `DISTANCE_m_LAP`, `LAP_NR`
       7. `RADAR_SAFETY` (`R.string.sensor_sub_category_radar_safety`): `RADAR_THREAT_COUNT`, `RADAR_CLOSEST_RANGE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_THREAT_SEVERITY`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`
       8. `AMBIENT_TEMPERATURE` (`R.string.sensor_sub_category_ambient_temperature`): `TEMPERATURE`, `TEMPERATURE_MIN`, `TEMPERATURE_MAX`
       9. `PHONE_BATTERY_GROUP` (`R.string.sensor_sub_category_phone_battery`): `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`
     - `SensorType` SHALL expose `@Nullable public SensorSubCategory getSubCategory()`.
     - Direct core metrics (`POWER`, `W_PRIME_BAL`, `HR`, `CALORIES`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `CADENCE`, `STRIDES`, `TIME_ACTIVE`, `TIME_TOTAL`, `TIME_OF_DAY`, `ALTITUDE`, `ASCENT`, `DESCENT`, `SLOPE`, `VERTICAL_SPEED`, `BEARING`, `LINE_DISTANCE_m`, `LATITUDE`, `LONGITUDE`, `ACCURACY`, `SENSORS`, `ACCUMULATED_SENSORS`) SHALL return `null` for `getSubCategory()`.
     - All 63 `SensorType` constants SHALL map to their designated `SensorCategory` and `@Nullable SensorSubCategory`.
  3. *ActivityType Sensor Telemetry Wiring & Priority Athletic Ordering (`REQ-TRK-070.3`)*:
     - `ActivityType.getSensorTypeArray()` SHALL wire all 8 missing Power Duration metrics (`POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`, `POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`) into `BIKE_POWER`, achieving 100% parity with `CyclingPowerMetricsDevice.java`.
     - `ActivityType.getSensorTypeArray()` SHALL wire both Radar V2 metrics (`RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) into cycling profiles (`BSportType.BIKE`). Radar metrics SHALL NOT be included in running or non-cycling profiles.
     - `CALORIES` SHALL be uncommented and enabled across all sports profiles where previously disabled.
     - Metrics within `getSensorTypeArray()` SHALL be ordered by athletic priority: primary immediate telemetry (Power, HR, Speed, Cadence) precedes calculated load metrics, duration benchmarks, pedaling dynamics, and diagnostic metadata.
  4. *2-Level Nested Accordion Selector & Capability Badging in EditSensorFieldDialog (`REQ-TRK-070.4`)*:
     - `EditSensorFieldDialog.kt` (`SensorTypeSelectorSection`) SHALL present a 2-level accordion hierarchy:
       - 6 top-level `SensorCategory` accordion headers.
       - Inside each expanded category, direct core metrics (`subCategory == null`) SHALL be rendered immediately at the top for instant 1-tap selection.
       - Sub-category items SHALL be grouped into collapsible `SensorSubCategory` accordion cards.
       - Upon opening the dialog or changing selection, the active `SensorType`'s parent `SensorCategory` and child `SensorSubCategory` (if present) SHALL be automatically expanded.
       - When the athlete enters a search query, matching categories and sub-categories SHALL expand automatically.
       - Each sensor type item row SHALL display compact capability badges:
         - A "Bar" badge if `TrackingViewsDbHelper.isBarSupported(sensorType, activityType)` is true.
         - A "Graph" badge if `TrackingViewsDbHelper.isGraphSupported(sensorType)` is true.
     - Strict preservation of `AppModalBottomSheet` scroll safety (Section 5.8): all accordions and rows SHALL render within standard `Column` layouts without nested scroll containers.
  5. *9-Language Localization Parity (`REQ-TRK-070.5`)*:
     - All category and sub-category title string resources (`sensor_category_*`, `sensor_sub_category_*`) SHALL achieve 100% parity across all 9 application locales: English (`values/`), German (`values-de/`), Spanish (`values-es/`), French (`values-fr/`), Italian (`values-it/`), Japanese (`values-ja/`), Dutch (`values-nl/`), Polish (`values-pl/`), and Portuguese (`values-pt/`).
     - Capability badges SHALL provide localized accessibility descriptions and labels.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-395` (*Categorized & Searchable SensorType Selector in EditSensorFieldDialog*), targeting `SensorCategory.java`, `SensorType.java`, `ActivityType.java`, and `EditSensorFieldDialog.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3852` (commit `14292b35206`), refined in `ATT-3542` (`REQ-TRK-061`) and `ATT-5879`.
3. *Root Reason for Existing Formulation*: 7 flat categories were created for 43 metrics. As metrics grew to 63 (with 50 cockpit-selectable), `POWER_AND_PEDALING` expanded to 22 flat metrics and `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` expanded to 16 mixed metrics; HRV was split across HR and Diagnostics; Cadence sat stranded in a running-focused category; and 10 telemetry metrics were omitted from `ActivityType.getSensorTypeArray`.
4. *Preservation of Core Invariants*: All 63 `SensorType` enum identifiers and integer IDs remain unchanged. Persistence schema in `ROWS_TABLE` stores `sensorType.name()`, preserving backward compatibility. Section 5.8 bottom-sheet scroll safety (no nested scrollable containers) is strictly maintained. 9-language translation parity across all new and existing category/sub-category string keys is preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SensorCategory.values()` and `SensorSubCategory.values()`,
• *When* evaluated by unit tests,
• *Then* exactly 6 top-level categories and 9 sub-categories exist, and all 63 `SensorType` constants map to valid categories and expected sub-categories (or null for direct core metrics).
• *Given* `ActivityType.BIKE_POWER`,
• *When* `getSensorTypeArray()` is inspected,
• *Then* all 8 Power Duration metrics (`POWER_5S..20M`, `POWER_PEAK_5S..20M`) are present and match `CyclingPowerMetricsDevice.java`.
• *Given* `ActivityType` profiles for cycling and non-cycling sports,
• *When* `getSensorTypeArray()` is evaluated,
• *Then* Radar metrics (including `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT`) are present only in cycling profiles, and `CALORIES` is present across active profiles.
• *Given* `EditSensorFieldDialog.kt`,
• *When* the selector is opened with a selected sensor having a sub-category,
• *Then* its category and sub-category are auto-expanded, direct metrics appear at the category top, and capability badges ("Bar", "Graph") are visible matching `TrackingViewsDbHelper`.
• *Given* all 9 locale directories,
• *When* audited by localization parity tests,
• *Then* 100% of category and sub-category title strings exist without missing translations or malformed format tokens.

**Invariants**: All 63 `SensorType` enum names and ordinals preserved; zero SQLite schema modifications; Section 5.8 bottom sheet scroll safety preserved; 100% unit and localization test pass rate.

### REQ-TRK-071: Google Fused Location Registration Idempotency & Teardown Lifecycle [Verified]
- **Tests**: `TST-TRK-071`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `SpeedAndLocationDevice_GoogleFused.java`
- **Rationale**: When tracking with Google Fused (`SpeedAndLocationDevice_GoogleFused`), Android lifecycle and tracking startup invoke `BANALService.checkOrInitializeLocationDevices()` repeatedly across UI and service entry points (e.g. `MainActivityWithNavigation.onResume`, `ControlTrackingScreen` composition and start button, `TrainingApplication.startTracking`, and `TrackerService` connection/start). Without internal registration state tracking, `checkOrReRegisterLocationUpdates()` executed `LocationServices.FusedLocationApi.requestLocationUpdates(...)` on every invocation, creating up to 7 concurrent duplicate listeners in Google Play Services and flooding Logcat 6 times per second (`location delivery to %s blocked - too fast`). Furthermore, `shutDown()` failed to unregister listeners via `LocationServices.FusedLocationApi.removeLocationUpdates(...)`, leaking active registrations until process death. Introducing an idempotent `mUpdatesRegistered` guard and clean defensive lifecycle teardown guarantees strictly at most 1 active listener while preserving permission recovery (ATT-6683, Parent Epic ATT-166, refining `REQ-STB-004`).
- **Description**:
1. *Idempotent Registration Guard (`REQ-TRK-071.1`)*:
• `SpeedAndLocationDevice_GoogleFused` SHALL maintain an internal registration flag `mUpdatesRegistered` (initially `false`).
• In `onConnected()` and `checkOrReRegisterLocationUpdates()`, if `mUpdatesRegistered == true`, the device SHALL immediately return without invoking `LocationServices.FusedLocationApi.requestLocationUpdates(...)`.
2. *State Flag Transition on Registration (`REQ-TRK-071.2`)*:
• Upon successfully requesting location updates via `LocationServices.FusedLocationApi.requestLocationUpdates(...)`, `SpeedAndLocationDevice_GoogleFused` SHALL set `mUpdatesRegistered = true`.
3. *Defensive Lifecycle Teardown & Clean Unregistration (`REQ-TRK-071.3`)*:
• In `shutDown()`, if `mGoogleApiClient != null && mGoogleApiClient.isConnected()`, `SpeedAndLocationDevice_GoogleFused` SHALL invoke `LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)` within a defensive try-catch block, reset `mUpdatesRegistered = false`, and disconnect the client.
4. *Connection State Reset (`REQ-TRK-071.4`)*:
• Upon `onConnectionSuspended()` or `onConnectionFailed()`, `SpeedAndLocationDevice_GoogleFused` SHALL reset `mUpdatesRegistered = false`.
5. *Registration State Observability (`REQ-TRK-071.5`)*:
• `SpeedAndLocationDevice_GoogleFused` SHALL expose `public synchronized boolean isLocationUpdatesRegistered()` returning `mUpdatesRegistered` for contract verification and parity with `SpeedAndLocationDevice_GPS`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-071`, refining `REQ-STB-004` (*Resilient Location Provider Initialization & Device Hardware Fault Tolerance*). Targets `SpeedAndLocationDevice_GoogleFused.java`.
2. *Historical Origin & Commit Trace*: `ATT-3045` (commit `53ba5fa8`). Introduced `checkOrReRegisterLocationUpdates()` to re-attempt provider registration following runtime permission grants.
3. *Root Reason for Existing Formulation*: `SpeedAndLocationDevice_GPS` received the `mUpdatesRegistered` guard and unregistration teardown in `ATT-3045`, but `SpeedAndLocationDevice_GoogleFused` omitted them, causing Google Play Services to accumulate concurrent listeners whenever tracking startup triggered multiple checks.
4. *Preservation of Core Invariants*: Runtime permission recovery on fresh installs or late permission grants is fully preserved (if permission is granted after initial connection, `checkOrReRegisterLocationUpdates()` proceeds because `!mUpdatesRegistered`). GoogleApiClient architecture (`SYSREQ-TRK-004`) is strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SpeedAndLocationDevice_GoogleFused` connected with updates registered (`isLocationUpdatesRegistered() == true`),
• *When* `checkOrReRegisterLocationUpdates()` is called repeatedly,
• *Then* `requestLocationUpdates(...)` SHALL NOT be called again, maintaining exactly 1 active registration.
• *Given* `SpeedAndLocationDevice_GoogleFused` with active updates,
• *When* `shutDown()` is invoked,
• *Then* `removeLocationUpdates(...)` SHALL be invoked, `isLocationUpdatesRegistered()` SHALL become `false`, and `disconnect()` SHALL execute cleanly.
• *Given* an active registered client,
• *When* `onConnectionSuspended()` or `onConnectionFailed()` occurs,
• *Then* `isLocationUpdatesRegistered()` SHALL reset to `false`.

**Invariants**: Retains `GoogleApiClient` / `LocationServices.FusedLocationApi` architecture (`SYSREQ-TRK-004`); zero concurrent duplicate registrations; 100% test pass rate.

### REQ-TRK-072: Guided DFA a1 Step & Ramp Test Protocol Screen [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-072`
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-012`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestConfig.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestState.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/navigation/NavRoutes.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/ATrainingTrackerApp.kt`
- **Rationale**: While `ATT-3554` established confidence-gated post-workout threshold estimation (`REQ-PER-036`), determining physiological thresholds ($HRVT_1$ and $HRVT_2$) in unstandardized free-form workouts suffers from non-monotonic power/pace progressions, cadence surges, and pacing variability that reduce $R^2$ and trigger span failures. Furthermore, athletes often mistakenly believe threshold testing requires riding to maximal physical exhaustion, whereas autonomic threshold capture is complete as soon as DFA $\alpha_1$ sustainably drops below $(T_2 - 0.05f).\text{coerceAtLeast}(0.25f)$ (defaulting to $0.45$). The system SHALL provide a dedicated, guided DFA $\alpha_1$ step and continuous ramp test protocol screen with inline real-time sensor quality status, dynamic protocol guidance and pros/cons, clean single-line controls, prominent live target vs. actual pacing guidance, and automated early sub-exhaustion completion detection.
- **Description**:
  1. *Protocol Configuration, Pacing Presets & Dynamic Guidance (`REQ-TRK-072.1`)*:
     - The system SHALL provide configurable test protocols supporting both Cycling (`BIKE_POWER`, power in Watts) and Running (`RUNNING`, speed in km/h or pace in min/km).
     - **Progressive Step Protocol (`GUIDED_STEP`)**: Defaults to 4 min (240 s) steps for complete cardiodynamic and autonomic settling (with selectable 3 min / 180 s minimum option). Configurable starting target (e.g. 100 W or 45% FTP for cycling) and step increment (default +15 W or +20 W; +0.5 km/h for running).
     - **Continuous Ramp Protocol (`GUIDED_RAMP`)**: Continuous linear progression with gentle slope $s \in \{+10\text{ W/min}, +15\text{ W/min}\}$ starting at 100 W or 45% FTP, tagging Mean Response Time $\tau_{\text{MRT}} = 45\text{ s}$ for downstream lag-compensated threshold estimation.
     - **Standard Warm-up**: Configurable steady warm-up defaulting to 10 minutes prior to protocol steps.
     - **Segmented Control Layout**: All `SegmentedButton` rows (Sport, Protocol Type, Step Duration) SHALL render without checkmark icons (`icon = {}`) and enforce single-line text formatting (`maxLines = 1, overflow = TextOverflow.Ellipsis`).
     - **Dynamic Protocol Information Box**: The configuration card SHALL render a structured protocol summary card containing a title and badge, 3 numbered procedural steps (*Ablauf & Durchführung*), and protocol-specific *+ Vorteil* / *– Nachteil* items dynamically updated when toggling sport, protocol type, or step duration.
  2. *Per-Step Evaluation Window & Zero Bleed Contract (`REQ-TRK-072.2`)*:
     - For `GUIDED_STEP`, each completed step $j$ ending at $t_{\text{end},j}$ SHALL contribute exactly one $(\overline{P}_j, \overline{\text{HR}}_j, \alpha_{1,j})$ data point evaluated strictly on RR beats inside $[t_{\text{end},j} - 120\text{ s}, t_{\text{end},j}]$.
     - Step evaluation SHALL NOT use data points beyond $t_{\text{end},j} - 60\text{ s}$ for centered windows, guaranteeing zero bleed into subsequent step transitions and eliminating step settling transients.
  3. *Inline Sensor Quality & Direct Test Launch (`REQ-TRK-072.3`)*:
     - The system SHALL immediately bind to sensor telemetry and begin evaluating heart rate and beat-to-beat RR intervals upon opening `HrvRampTestScreen`.
     - Sensor readiness and diagnostic advice SHALL be embedded inline directly on `ProtocolConfigurationCard` above the primary **`Test starten`** action button, eliminating the separate intermediate pre-flight screen.
     - The inline sensor card SHALL grade sensor readiness:
       - **High Quality**: ECG chest strap connected, beat-to-beat RR streaming, artifact rate $\le 3.0\%$.
       - **Moderate Quality**: Artifact rate between $3.0\%$ and $5.0\%$, displaying a warning that results will be capped at `Confidence.MEDIUM`.
       - **Unsuitable**: Artifact rate $> 5.0\%$, firmware-smoothed RR stream detected, or no RR interval stream available (e.g., optical wrist sensor). Test start SHALL be gated (`enabled = uiState.canStartTest`) with actionable user diagnostics.
     - User-visible status copy across all 9 supported locales SHALL use natural phrasing (*"Sensor- & Signalstatus"* / *"Sensor & Signal Status"*) replacing literal aviation translations (*"Vorflug-Sensorkontrolle"*).
  4. *In-Workout Real-Time Guidance & Cockpit Overlay (`REQ-TRK-072.4`)*:
     - `HrvRampTestViewModel` SHALL collect `SensorType.SPEED_mps` and expose `currentSpeedKmh = speedMps * 3.6`.
     - The protocol screen SHALL render current phase (`WARMUP`, `STEP`, `RAMP`, `COOLDOWN`), active target intensity (Watts or Pace), step countdown timer ($MM:SS$), live DFA $\alpha_1$, live artifact rate (%), and current HR.
     - `ActivePacingGuidanceCard` SHALL display a prominent **Ziel** vs. **Aktuell (Ist)** pacing comparison block and show both live Power (`W`) and live Speed (`km/h`).
     - Action buttons on `ActivePacingGuidanceCard` (*Abwärmen beginnen* / *Test beenden*) SHALL enforce uniform single-line height (`height(44.dp)`, `maxLines = 1, overflow = TextOverflow.Ellipsis`).
  5. *Early Sub-Exhaustion Auto-Completion Detection (`REQ-TRK-072.5`)*:
     - During the test phase, when live DFA $\alpha_1$ drops sustainably below $(T_2 - 0.05f).\text{coerceAtLeast}(0.25f)$ (where $T_2$ is the athlete's anaerobic threshold from `TuningPreferencesDataStore.dfaA1ThresholdT2Flow`, defaulting to $0.50f$ yielding cutoff $0.45f$) for at least 60 consecutive seconds (confirming both $HRVT_1$ and $HRVT_2$ have been traversed), the system SHALL immediately notify the athlete via visual banner and audible tone that threshold determination is complete.
     - The system SHALL offer a 1-tap transition into cool-down, terminating test loading without requiring maximal volitional exhaustion.
  6. *Session Metadata Tagging & Decimated Grid Quality Governance (`REQ-TRK-072.6`)*:
     - Upon completion, the session SHALL be tagged with `hrvTestProtocol = GUIDED_STEP` or `GUIDED_RAMP` and associated protocol parameters (`stepDurationSec`, `rampSlopeWattsPerMin`).
     - Downstream threshold estimation in `HrvThresholdEstimator` (`REQ-PER-036`) SHALL restrict regression analysis strictly to the post-warmup monotonic test window and enforce quality gates on the decimated grid ($R^2 \ge 0.70$, artifact rate $\le 3.0\%$, $\ge 5$ steps or $N_{\text{eff}} \ge 10$) for `Confidence.HIGH`.
  7. *Consolidated Launcher Access & Ergonomic Entry Point Alignment (`REQ-TRK-072.7`)*:
     - The Guided DFA $\alpha_1$ Threshold Test launcher SHALL reside exclusively in **`ZoneSettingsScreen` -> `HRV (DFA a1)` tab** (`ZonesSettingsActivity.kt`) with leading icon `Icons.AutoMirrored.Filled.TrendingUp` and single-line label rendering (`maxLines = 1, overflow = TextOverflow.Ellipsis` for 31-char German `"Geführten Schwellentest starten"` at phone widths).
     - Inside `HrvDfaA1SettingsContent` (`ZonesSettingsActivity.kt`), when `onOpenHrvTest != null`, the CTA `OutlinedButton` (`R.string.hrv_start_guided_ramp_test`) SHALL be positioned immediately below the explanatory intro text (`R.string.hrv_zones_guidance_desc`) and above the physiological band cards (`HrvZoneBandCard`) and threshold controls (`HrvDfaA1RangeSlider`, `HrvThresholdControlRow`) with minimal vertical spacing (avoiding double `Spacer` padding on top of `Column` `Arrangement.spacedBy(8.dp)`) so the CTA appears above the fold without scrolling and `HrvDfaA1RangeSliderCard` stays visible within `680.dp` height (`ATT-8951`).
     - Redundant launcher CTA buttons in the Start Tracking screen (`TrackingTabsScreen.kt`, `hrv_test_cockpit_launcher`) and Athlete Profile HRV Readiness tab (`AthleteProfileScreen.kt`, `btn_guided_hrv_test`) SHALL be removed, preventing everyday workout clutter and separating resting morning readiness checks from exercise threshold testing.
  8. *Lifecycle Automation: Auto-Naming, Lap Splits & Automated Stop (`REQ-TRK-072.8`)*:
     - **Phase Lap Splits**: Upon `WARMUP -> TESTING` countdown reaching 0, `HrvRampTestViewModel` SHALL broadcast `TrainingApplication.REQUEST_NEW_LAP`. Upon transition into `COOLDOWN` (`transitionToCooldown()`), `HrvRampTestViewModel` SHALL broadcast `TrainingApplication.REQUEST_NEW_LAP`. Intermediate laps between steps in `GUIDED_STEP` SHALL NOT be triggered, guaranteeing exactly 3 canonical laps (Warmup, Active Test, Cooldown).
     - **Descriptive Protocol Auto-Naming & Overwrite Guard**: In `tagActiveWorkoutWithProtocol()`, the system SHALL persist `WorkoutSummaries.WORKOUT_NAME` as a localized protocol title (`hrv_workout_name_step` / `hrv_workout_name_ramp` across all 9 supported locales). In `TrackerService.finalizeLiveSession()`, the system SHALL check whether `method` is `"GUIDED_STEP"` or `"GUIDED_RAMP"`; if true, the pre-assigned protocol workout name SHALL be strictly preserved and route cluster / known-location auto-naming overwrites SHALL be suppressed.
     - **Automated Stop on Test Completion**: When cooldown expires (`COOLDOWN -> FINISHED`) or `finishTest()` is invoked while tracking is active (`TrainingApplication.isTracking() == true`), `HrvRampTestViewModel` SHALL broadcast `TrainingApplication.REQUEST_STOP_TRACKING` to stop and finalize workout recording.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-072`, extending `REQ-TRK-061` (*Live HRV Telemetry & Configurable DFA a1 Thresholds*) and companion to `REQ-PER-036` (*Confidence-Gated HRVT1/HRVT2 Estimation & Summary Card*). Targets `HrvRampTestScreen.kt`, `HrvRampTestViewModel.kt`, `HrvRampTestConfig.kt`, `TrackingTabsScreen.kt`, `AthleteProfileScreen.kt`, `ZonesSettingsActivity.kt`, and `ATrainingTrackerApp.kt`.
2. *Historical Origin & Commit Trace*: Conceived in Epic `ATT-3539` (*Heart Rate Variability*), companion to `ATT-3554` (commit `5deec7cd`), implemented in `ATT-4969` (commit `424580a2`), refined in `ATT-7317` (commit `fce6777b`), ergonomically streamlined in `ATT-7529`, launcher access consolidated in `ATT-7527` (`b7d3ce84`), and CTA repositioned above the fold in `ATT-8951`.
3. *Root Reason for Existing Formulation*: Athletic tracking previously supported only free progression or static zone alerts. Free progression frequently suffers from pacing variability and non-monotonicity, while athletes lacked guidance to terminate tests sub-maximally when DFA $\alpha_1 < 0.45$. ATT-4969 initially created a separate `PRE_FLIGHT` screen to enforce sensor checking, and sprinkled launcher buttons across tracking and profile screens to boost beta discoverability. Once the dedicated Training Zones `HRV (DFA a1)` tab was built in ATT-7317, these duplicated buttons caused UI clutter on everyday tracking launches and conflated resting readiness checks with strenuous exercise ramp testing; `ATT-7317`/`ATT-7527` placed the consolidated CTA at the bottom of `HrvDfaA1SettingsContent`, which subsequent preview/display sections pushed below the fold (`ATT-8951`).
4. *Preservation of Core Invariants*: Full host navigation reachability (`NavRoutes.HRV_TEST`) is preserved via the authoritative Training Zones entry point; resting morning HRV and everyday tracking remain completely intact; strict signal quality gating ($rMSSD$, artifact rate $\le 3.0\%$ for `HIGH`, $> 5.0\%$ blocks start), early sub-45 s completion detection, and `GUIDED_STEP`/`GUIDED_RAMP` session tagging preserved; zero regressions in tracking, readiness, or ramp test functionality.
5. *Chesterton's Fence Audit (`REQ-TRK-072.8` - ATT-7745)*:
   - *Original Requirement ID & Target*: `REQ-TRK-072.8` (Lifecycle Automation: Auto-Naming, Lap Splits & Automated Stop).
   - *Historical Origin & Commit Trace*: `ATT-4969` (commit `424580a2`), `ATT-7317` (`fce6777b`), `ATT-7745`.
   - *Root Reason for Existing Formulation*: Initial guided test implementation left workout stop and lap boundaries to manual user intervention. Free workout clustering in `TrackerService.finalizeLiveSession()` assumed any workout with location points was an outdoor route eligible for cluster renaming.
   - *Preservation of Core Invariants*: Manual stop and lap splits remain available; free workouts and outdoor rides retain full cluster and known-location auto-naming; Option A (phase lap transitions only) avoids per-step lap fragmentation; 100% 9-language localization parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `HrvRampTestScreen`,
• *When* the setup screen renders,
• *Then* sensor quality status (HR, RR artifact rate %, quality tier `HIGH`/`MODERATE`/`UNSUITABLE`) is displayed directly inline on `ProtocolConfigurationCard` above the `Test starten` button (`enabled = uiState.canStartTest`), and tapping `Test starten` directly commences the test protocol into `WARMUP` without requiring an intermediate screen.
• *Given* an active guided test on `ActivePacingGuidanceCard`,
• *When* telemetry updates arrive from connected sensors,
• *Then* `HrvRampTestViewModel` ingests both `SensorType.POWER` and `SensorType.SPEED_mps`, displaying a prominent **Ziel** vs. **Aktuell (Ist)** pacing comparison block and rendering both live Power (`W`) and live Speed (`km/h`) alongside current HR, live DFA $\alpha_1$, and artifact rate %.
• *Given* the configuration and active pacing cards in `HrvRampTestScreen.kt`,
• *When* controls are rendered,
• *Then* all `SegmentedButton` rows display without checkmark icons (`icon = {}`) with single-line labels (`maxLines = 1, overflow = TextOverflow.Ellipsis`), and active-screen action buttons share uniform `height(44.dp)`.
• *Given* an athlete configuring protocol options on `ProtocolConfigurationCard`,
• *When* toggling Sport (`Radfahren` / `Laufen`), Protocol (`Stufentest` / `Rampentest`), or Step Duration (`4 Min.` / `3 Min.`),
• *Then* the dynamic Protocol Info card updates immediately with the matching title badge, 3 numbered procedural steps (*Ablauf & Durchführung*), and protocol-specific *+ Vorteil* and *– Nachteil* bullet items.
• *Given* the 9 supported application locales,
• *When* inspecting sensor readiness and protocol guidance strings in `strings_hrv.xml`,
• *Then* aviation terminology (*"Vorflug-Sensorkontrolle"*) is replaced with natural sensor status wording with 100% parity verified by `TranslationParityTest`.
• *Given* a test in progress where DFA $\alpha_1$ drops below $0.45$ sustainably for $\ge 60\text{ s}$,
• *When* the condition is met,
• *Then* the screen notifies the athlete that both thresholds ($HRVT_1$ and $HRVT_2$) are captured and prompts immediate transition to cool-down without exhaustion.
• *Given* a completed Guided Step Test session evaluated by `HrvThresholdEstimator`,
• *When* regression is performed,
• *Then* each step is evaluated strictly over $[t_{\text{end},j} - 120\text{ s}, t_{\text{end},j}]$ with zero bleed into subsequent steps, and high confidence is emitted only if $R^2 \ge 0.70$, artifact rate $\le 3.0\%$, and $\ge 5$ valid steps are present.
• *Given* the Start Tracking screen (`TrackingTabsScreen.kt`),
• *When* `ControlTrackingScreen` renders,
• *Then* `hrv_test_cockpit_launcher` is absent from `bottomContent`, displaying only `RouteSelectionButton` and optional Morning HRV check.
• *Given* the Athlete Profile screen (`AthleteProfileScreen.kt`),
• *When* the HRV Readiness tab is displayed,
• *Then* `btn_guided_hrv_test` is absent, presenting only the resting Morning Readiness CTA and readiness trend cards.
• *Given* `ZoneSettingsScreen` in `ZonesSettingsActivity.kt`,
• *When* viewing the `HRV (DFA a1)` tab (`HrvDfaA1SettingsContent`) with `onOpenHrvTest != null`,
• *Then* the Guided Threshold Test `OutlinedButton` (`R.string.hrv_start_guided_ramp_test`) renders cleanly on a single line directly below the explanatory intro text (`R.string.hrv_zones_guidance_desc`) and above the zone cards (`HrvZoneBandCard`) and threshold controls (`HrvDfaA1RangeSlider`, `HrvThresholdControlRow`), with leading icon `Icons.AutoMirrored.Filled.TrendingUp` navigating to `NavRoutes.HRV_TEST` and `HrvDfaA1RangeSliderCard` remaining visible inside a `680.dp` viewport.
• *Given* a Guided DFA α1 Threshold Test running in `HrvTestPhase.WARMUP`,
• *When* the warmup timer reaches 0,
• *Then* `REQUEST_NEW_LAP` is broadcast and the active test phase starts in a new lap.
• *Given* a Guided DFA α1 Threshold Test in `TESTING` or `EARLY_COMPLETION`,
• *When* transitioning to `COOLDOWN` (`transitionToCooldown()`),
• *Then* `REQUEST_NEW_LAP` is broadcast and the cooldown phase starts in a new lap.
• *Given* a Guided DFA α1 Threshold Test whose cooldown timer reaches 0 or `finishTest()` is invoked while tracking is active,
• *When* `finishTest()` or timer completion runs,
• *Then* `REQUEST_STOP_TRACKING` is broadcast to stop the recording.
• *Given* a guided test workout tagged with `method == "GUIDED_STEP"` or `"GUIDED_RAMP"`,
• *When* `finalizeLiveSession()` completes in `TrackerService`,
• *Then* the localized protocol title is preserved and not overwritten by cluster or location auto-naming.

**Invariants**: Zero background service sampling regressions; zero SQLite schema modifications; Section 5.8 Composable navigation safety; 100% clean-room test pass rate across all 9 locales.

### REQ-TRK-073: Live 2D Lane-Offset & Vehicle Sizing Radar Edge Overlay [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-073`
- **System Requirement(s)**: `SYSREQ-TRK-006`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/radar/RadarEdgeOverlay.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/radar/RadarSettingsSection.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/RadarTrafficSummaryCard.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java`
- **Rationale**: Following BLE V2 high-resolution radar decoding (ATT-5095 / Garmin Varia RearVue 820), the system receives 2D spatial telemetry (lateral offset, vehicle classification, lane trajectory, passing clearance). Athletes need real-time visual distinction between vehicle sizes (cars vs trucks) and lateral lane-change status on the live Cockpit vertical edge overlay, alongside configurable close-pass (< 1.5 m) safety thresholds, aftermath map hazard markers, summary card metrics, and FIT developer fields.
- **Description**:
  1. *Dynamic 175m Rail Scaling (`REQ-TRK-073.1`)*: The vertical radar rail SHALL dynamically scale its maximum detection range to 175.0 m when connected to a V2-capable radar or when any tracked target distance exceeds 140.0 m, falling back to 140.0 m for standard V1 and ANT+ radar streams.
  2. *Vehicle Size Category Geometry (`REQ-TRK-073.2`)*: `RadarEdgeOverlay.kt` SHALL render target indicators according to `VehicleSizeCategory`:
     - `UNKNOWN_OR_LIGHT`: Compact circular dot (10.dp).
     - `CAR`: Standard circular marker (14.dp).
     - `TRUCK_OR_BUS`: Elongated pill badge (20.dp width x 12.dp height, rounded 6.dp) with high-contrast border and inner vehicle icon/glyph.
  3. *Lateral Lane Offset Mapping (`REQ-TRK-073.3`)*: The vertical rail track SHALL expand usable width (28.dp) and map `target.lateralOffsetMeters` (or `target.laneState`) to horizontal translation ($x \in [-6\text{dp}, +6\text{dp}]$), adjusted for `radarTrafficOrientation` (Right-Hand Traffic vs. Left-Hand Traffic). When vehicle has pulled into the passing lane, the target indicator SHALL visually shift away from the rider centerline.
  4. *Configurable Close-Pass Safety Threshold (`REQ-TRK-073.4`)*: `TuningPreferencesDataStore` SHALL persist `radarClosePassThresholdM` (Float, default `1.5f`), exposed in `RadarSettingsSection.kt` via `TuningSegmentedButtonRow` with selectable distance presets (`1.0 m (3.3 ft)`, `1.5 m (5.0 ft)`, `2.0 m (6.6 ft)`) that fit without ellipsizing, with `radar_close_pass_threshold_title` passed as title and `radar_close_pass_threshold_summary` passed as `helperText` (rendered as `bodySmall`).
     - `1.0 m` (3 ft, UK/US standard)
     - `1.5 m` (StVO § 5 Abs. 4 Innerorts / Urban standard)
     - `2.0 m` (StVO § 5 Abs. 4 Außerorts / Rural standard)
  5. *Post-Workout Close-Pass Hazard Map Layer (`REQ-TRK-073.5`)*: `WorkoutRepository.getWorkoutMarkers()` SHALL inspect `sample.radarPassingDistanceM` upon vehicle pass intervals ($\Delta \text{RADAR\_VEHICLE\_COUNT} > 0$). If passing distance is less than the configured close-pass threshold, a dedicated Close-Pass Hazard `LocationMarker` (hazard icon) SHALL be placed along the polyline on `TrackOnMapScreen.kt` with tooltip detailing clearance distance, vehicle size, and speed.
  6. *Aftermath Summary Card Clearance Metrics (`REQ-TRK-073.6`)*: `RadarTrafficSummaryCard.kt` SHALL display Minimum Passing Distance, Close-Pass Count (with warning badge when $> 0$), and Large Vehicle Count / Share when V2 telemetry is present.
  7. *FIT Protocol V2.0 Developer Fields (`REQ-TRK-073.7`)*: `FitFileWriter.java` SHALL define and serialize developer fields for `passing_distance` (uint16, scale 100, "m") and `large_vehicle_count` (uint16, scale 1, "vehicles") on `RecordMesg`.
  8. *9-Language Localization Parity (`REQ-TRK-073.8`)*: All close-pass settings, map marker titles, and summary card labels SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active V2 radar stream with a `TRUCK_OR_BUS` target at 160m `IN_RIDER_LANE` and a `CAR` target at 50m `PASSING_LANE`,
• *When* `RadarEdgeOverlay` renders,
• *Then* the rail scales to 175m, the 160m target is drawn as an elongated heavy-vehicle pill in the center/rider rail position, and the 50m target is drawn as a standard car circle shifted laterally into the passing lane.
• *Given* a completed ride with a vehicle pass recorded at 1.12m passing clearance and close-pass threshold set to 1.50m,
• *When* `TrackOnMapScreen.kt` and `RadarTrafficSummaryCard.kt` render,
• *Then* a close-pass hazard marker is positioned at the exact pass GPS coordinate, and the summary card displays 1 close pass (<1.5m, min 1.12m).
• *Given* a legacy V1 or ANT+ radar without 2D telemetry,
• *When* the overlay or summary renders,
• *Then* targets render as standard centered circular dots up to 140m with zero regressions or layout distortion.

**Invariants**: 100% V1 backward compatibility; zero SQLite database migrations; overlay rail touch pass-through preserved; 100% 9-language translation parity.

### REQ-TRK-074: Duplicate Location Callback Filtering, Asymmetric Deadband Separation & Stationary Pinning Entry Hysteresis in `SpeedAndLocationDevice` [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-074`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceStationaryJitterTest.kt`
- **Rationale**: During active athletic tracking (especially on devices using Google Fused Location Provider, `SpeedAndLocationDevice_GoogleFused`), sub-second duplicate/burst callbacks (e.g. 50–150 ms apart with identical coordinates and `speed == 0.0f` from resting phone IMUs) and low-speed movements (e.g. hill climbing $\le 15\text{--}21\text{ km/h}$ where 1-second displacement $\le \text{escapeRadius}$) erroneously triggered stationary anchor pinning (`mIsStationaryPinned = true`), repeatedly overwriting valid moving velocity with `0.0 km/h` and interrupting distance accumulation. The system SHALL ignore sub-interval and duplicate location callbacks, separate the stationary pinning entry threshold (`deadbandRadius`) from the exit escape threshold (`escapeRadius`), and enforce multi-tick entry hysteresis ($\ge 3$ consecutive 1 Hz updates) before transitioning from moving (`!mIsStationaryPinned`) into pinned (`mIsStationaryPinned = true`) state (ATT-6682, amending `REQ-TRK-048.1` and `REQ-TRK-056.1`):
- **Description**:
  1. *Sub-Interval & Duplicate Callback Guard (`REQ-TRK-074.1`)*:
     - In `SpeedAndLocationDevice.onNewLocation()`, when `mPrevLocation != null`:
       - If $\Delta t_{\text{ms}} = \text{location.getTime()} - \text{mPrevLocation.getTime()} < 250\text{ ms}$, OR if $(\text{rawDelta} == 0.0\text{ m} \land \Delta t_{\text{ms}} < 900\text{ ms})$:
         `SpeedAndLocationDevice` SHALL ignore the fix and return immediately without updating `mPrevLocation`, without mutating `mIsStationaryPinned`, without zeroing `mSpeed`, and without publishing spurious zero velocity to `mSpeedSensor`.
  2. *Separation of Entry and Exit Pinning Thresholds (`REQ-TRK-074.2`)*:
     - The entry candidate condition to begin stationary pinning while moving (`!mIsStationaryPinned`) SHALL be evaluated against `deadbandRadius` ($\max(3.0\text{ m}, 0.6 \times \text{accuracy})$) and NOT against `escapeRadius` ($\max(3.0\text{ m}, 1.5 \times \text{accuracy})$).
     - The exit condition to unpin while already pinned (`mIsStationaryPinned`) SHALL continue to require `distFromAnchor > deadbandRadius` (with positive Doppler speed or displacement exceeding `escapeRadius`).
  3. *Stationary Pinning Entry Hysteresis (`REQ-TRK-074.3`)*:
     - `SpeedAndLocationDevice` SHALL maintain an entry hysteresis streak counter `mStationaryHysteresisCount` (initialized to `0`).
     - When `mIsStationaryPinned == true`: `mStationaryHysteresisCount` SHALL remain `0`. When unpinning occurs, `mStationaryHysteresisCount` SHALL reset to `0`.
     - When `mIsStationaryPinned == false` and `mPrevLocation != null`:
       - If candidate stationary conditions are met:
         $(\text{isDopplerStationary} \land \text{rawDelta} \le \text{deadbandRadius}) \lor (\neg \text{hasSpeed} \land \text{rawDelta} \le \text{deadbandRadius} \land mSpeed \le \text{SPEED\_ZERO\_THRESHOLD})$:
         `mStationaryHysteresisCount` SHALL increment by `1`.
         - If `mStationaryHysteresisCount >= STATIONARY_PINNING_HYSTERESIS_TICKS` (constant $= 3$):
           The device SHALL transition to `mIsStationaryPinned = true`, set `mStationaryAnchor = location`, set `delta_distance = 0.0`, clamp `speed = 0.0`, and reset `mStationaryHysteresisCount = 0`.
         - Else (`mStationaryHysteresisCount < 3`):
           The device SHALL remain in `mIsStationaryPinned = false`, treat the tick as transient movement, compute `delta_distance = rawDelta`, calculate `calcSpeed`, and fall back to geometric speed (`REQ-TRK-056.2`), preventing single-sample zero-speed drops.
       - Else (when $\text{rawDelta} > \text{deadbandRadius}$ or $\text{rawSpeed} > \text{SPEED\_ZERO\_THRESHOLD}$):
         `mStationaryHysteresisCount` SHALL reset to `0`, and legitimate movement progression SHALL proceed.
  4. *Initial Fix Immediate Pinning Invariance (`REQ-TRK-074.4`)*:
     - When `mPrevLocation == null` (initial fix at session start):
       If $\text{isDopplerStationary}$ or $\neg \text{hasSpeed}$, `SpeedAndLocationDevice` SHALL immediately initialize `mStationaryAnchor = location` and `mIsStationaryPinned = true` with zero hysteresis delay, preserving start-line stationary jitter suppression (`REQ-TRK-048.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-074`, amending `REQ-TRK-048.1` (*Stationary GPS Anchor Pinning & Multi-Provider Deadband Gating*, `ATT-4850`) and `REQ-TRK-056.1` (*Google Fused Location Stationary Doppler Pinning & Motion Escape Bounds*, `ATT-6256`). Targets `SpeedAndLocationDevice.java` and `SpeedAndLocationDeviceStationaryJitterTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4850`, commit `19175f0a`), Sprint 2026-45.13 (`ATT-6256`, commit `fa5f2791`).
3. *Root Reason for Existing Formulation (Why was this fence built?)*: `REQ-TRK-048.1` introduced immediate zero Doppler pinning to freeze GPS spiderwebs at traffic stops. `REQ-TRK-056.1` added `rawDelta <= escapeRadius` to avoid pinning when physical phone IMUs were stationary on a desk during fast mock replays. However, using `escapeRadius` (up to 6.0 m) as the entry threshold and allowing single-sample pinning without hysteresis caused low-speed movement ($v \le 15\text{--}21\text{ km/h}$) and sub-second duplicate bursts to falsely pin the device.
4. *Preservation of Core Invariants (Why is it safe to modify now?)*: Re-routing the entry check to `deadbandRadius`, requiring $N=3$ consecutive stationary ticks before transitioning to `mIsStationaryPinned = true`, filtering sub-second duplicates ($dt < 250\text{ ms}$ or identical coordinates $dt < 900\text{ ms}$), while retaining immediate pinning on the initial startup fix (`mPrevLocation == null`), completely prevents false velocity drops during movement while preserving 100% of stationary red light jitter suppression and start-line stabilization.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SpeedAndLocationDevice` in active movement at $v = 8.0\text{ m/s}$ ($28.8\text{ km/h}$),
• *When* a duplicate location callback arrives $100\text{ ms}$ later with $\text{rawDelta} == 0.0\text{ m}$ and $\text{speed} == 0.0\text{ m/s}$,
• *Then* `SpeedAndLocationDevice` ignores the duplicate callback, leaves `isStationaryPinned == false`, and preserves the active moving speed.
• *Given* `SpeedAndLocationDevice` climbing a hill at $3.0\text{ m/s}$ ($10.8\text{ km/h}$, displacement $3.0\text{ m/s} \le \text{escapeRadius}$),
• *When* a single spurious Doppler zero fix ($\text{speed} == 0.0\text{ m/s}$) arrives,
• *Then* `SpeedAndLocationDevice` does not pin to stationary anchor, preserves `isStationaryPinned == false`, and maintains continuous speed via geometric fallback.
• *Given* `SpeedAndLocationDevice` halting at a traffic light,
• *When* $3$ consecutive $1\text{ Hz}$ updates arrive with $\text{rawDelta} \le \text{deadbandRadius}$ and $\text{isDopplerStationary} == \text{true}$,
• *Then* on the 3rd update, `SpeedAndLocationDevice` transitions to `isStationaryPinned == true`, anchors coordinates, clamps speed to $0.0\text{ m/s}$, and locks bearing.
• *Given* session start with initial fix (`mPrevLocation == null`) reporting $\text{speed} == 0.0\text{ m/s}$,
• *When* `onNewLocation` is invoked,
• *Then* `SpeedAndLocationDevice` immediately pins to stationary anchor on the 1st fix without waiting for 3 ticks.

**Invariants**: Zero background service IPC regressions; initial fix stationary pinning preserved; 100% test pass rate across `banalservice.devices.*`.

### REQ-TRK-075: Core Tracking Pipeline Integration Verification (TrackerService, Sensor Layer & SQLite Database) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-075`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Implementation**: `TrackerService.java`, `WorkoutRepository.kt`, `AutoPauseManager.kt`, `LiveWorkoutSession.java`, `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
- **Rationale**: Prior unit tests isolated tracking components using heavy mocking of databases and services, leaving the full tracking chain unverified as integrated software. In compliance with ASPICE SWE.5 (Remediation ID C-11), the system SHALL verify the integrated execution of TrackerService, sensor telemetry ingestion, active/pause/resume state transitions, and real SQLite persistence (workout_summaries, workout_samples, workout_rr_intervals, and laps) using Robolectric and authentic database engines (ATT-6185):
- **Description**:
  1. *Start Tracking and Multi-Sensor Telemetry Persistence (`REQ-TRK-075.1`)*:
     - Upon starting a workout, TrackerService SHALL create a record in `workout_summaries`, dynamically create the `workout_samples_<baseFileName>` table, ingest simulated multi-sensor telemetry (Heart Rate, Speed, Cadence, Power, GPS coordinates, Altitude), persist sample rows to SQLite, and stream beat-to-beat RR intervals to `workout_rr_intervals`.
  2. *Manual Pause Invariant (`REQ-TRK-075.2`)*:
     - When tracking is paused, TrackerService SHALL suspend sample row insertions into the dynamic samples table and freeze active time (`TIME_ACTIVE_s`), while continuing to update total elapsed time (`TIME_TOTAL_s`) in `workout_summaries`.
  3. *Manual Resume Continuity (`REQ-TRK-075.3`)*:
     - Upon resuming from pause, TrackerService SHALL resume accumulating active time (`TIME_ACTIVE_s`) and resume inserting sample rows into the dynamic samples table.
  4. *Auto-Pause State Transitions (`REQ-TRK-075.4`)*:
     - TrackerService and AutoPauseManager SHALL evaluate speed against the configured auto-pause speed threshold; dropping below threshold SHALL transition state to `AUTO_PAUSED` and trigger pause broadcast; exceeding threshold SHALL transition state to `TRACKING` and trigger resume broadcast.
  5. *Finalization and Summary Persistence (`REQ-TRK-075.5`)*:
     - Upon calling `endWorkout()`, TrackerService SHALL close out the active lap in `laps`, flush pending RR intervals synchronously to SQLite, compute terminal summary statistics (total distance, active time, total time, average speed, average/max heart rate, ascent), and commit `FINISHED = 1` in `workout_summaries`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* TrackerService initialized with simulated multi-sensor telemetry (HR, Speed, Cadence, Power, GPS),
• *When* tracking is started and samples are processed,
• *Then* `workout_summaries`, `workout_samples_<baseFileName>`, and `workout_rr_intervals` tables in SQLite contain matching populated records.
• *Given* an active tracking session,
• *When* tracking is paused,
• *Then* no new sample rows are inserted into the samples table, `TIME_ACTIVE_s` stops increasing, and `TIME_TOTAL_s` continues updating.
• *Given* a paused tracking session,
• *When* tracking is resumed,
• *Then* active time accumulation resumes and new sample rows are written to SQLite.
• *Given* tracking with auto-pause enabled,
• *When* speed drops below the auto-pause threshold,
• *Then* the service transitions to `AUTO_PAUSED`; when speed subsequently rises above the threshold, it resumes to `TRACKING`.
• *Given* an active workout with recorded telemetry and laps,
• *When* `endWorkout()` is invoked,
• *Then* the final lap is saved, `FINISHED = 1` is committed, and computed summary metrics (distance, active time, max/avg HR, ascent) are persisted to SQLite.

**Invariants**: Zero modification to production code or database schemas; complete test isolation per run; 100% authentic SQLite persistence verification.

### REQ-TRK-076: Defensive Concurrency and Re-Entrant Stop-to-Start Lifecycle in TrackerService [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-076`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackerService.java`, `TrackerServiceFinalizationTest.kt`
- **Rationale**: When an athlete stops a workout and starts a new session immediately while asynchronous finalization (`finalizationTask`) is still running on `mDbExecutor`, `TrackerService` SHALL NOT throw `RejectedExecutionException`, SHALL NOT corrupt the finishing session's state, and SHALL NOT allow the finishing session's teardown callback to prematurely terminate the new session (ATT-7746, amending `REQ-TRK-034.2` and `REQ-TRK-034.3`):
- **Description**:
  1. *Deferred Executor Teardown strictly to onDestroy (`REQ-TRK-076.1`)*:
     - `TrackerService.handleActionStop()` SHALL cancel periodic sampling via `cancelTrackerHandle()`, but SHALL NOT invoke `mDbExecutor.shutdown()`, `mScheduler.shutdownNow()`, or `mRrFlushExecutor.shutdown()`. Complete executor shutdown SHALL be deferred strictly to `onDestroy()`.
     - In `onDestroy()`, `mDbExecutor.shutdown()` and `mScheduler.shutdownNow()` SHALL be invoked across all exit paths, including when `mIsStopping == true`.
  2. *Session-Isolated Snapshot Parameters (`REQ-TRK-076.2`)*:
     - In `handleActionStop()`, `TrackerService` SHALL capture immutable local references to the active session parameters (`workoutId`, `baseFileName`, `liveSession`) before submitting `finalizationTask` to `mDbExecutor`.
     - These parameters SHALL be passed explicitly to `endWorkout(...)` and `finalizeLiveSession(...)` so that subsequent mutations to `TrackerService` instance fields by `onStartCommand(START_NORMAL)` cannot cross-contaminate or corrupt the finalizing session.
  3. *Monotonic Session Generation Gating for Teardown (`REQ-TRK-076.3`)*:
     - `TrackerService` SHALL maintain a monotonic `mSessionGeneration` counter incremented upon each `START_NORMAL` dispatch.
     - In `finalizationTask`'s `finally` block, `performStopSelf()`, `performStopForeground()`, `safeUnbindBanalService()`, and `releaseWakeLock()` SHALL execute ONLY if `mSessionGeneration == stoppingGeneration` and `mIsStopping == true`. If a new session has started, the previous session's teardown callbacks SHALL NOT terminate or unbind resources for the active session.
  4. *Service State Re-initialization & Receiver Re-registration on Restart (`REQ-TRK-076.4`)*:
     - When `onStartCommand` receives `START_NORMAL` while `mIsStopping == true`, `TrackerService` SHALL reset `mIsStopping = false`, reset `mTrackingInterrupted = false`, re-register transient broadcast receivers (`mSearchingFinishedReceiver`, `mAltitudeCorrectionReceiver`, `mLapSummaryReceiver`, `mUserSelectedSportTypeChangedReceiver`), and ensure foreground service state and wakelock are actively maintained.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-TRK-034` (Clauses 2 & 3), targeting `TrackerService.java` and `TrackerServiceFinalizationTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.9 (`ATT-4098`, commit `4795a2cf`). Introduced `ACTION_STOP` to retain Foreground Service priority during async finalization on `mDbExecutor`.
3. *Root Reason for Existing Formulation*: Assumed a single linear session lifecycle where `ACTION_STOP` finalization runs to termination before a new session starts.
4. *Preservation of Core Invariants*: Retains 1-Hz tick suppression (`cancelTrackerHandle()`), foreground LMK protection, dedicated `FINISHED = 1` minimal transaction, and `TRACKING_FINISHED_INTENT` broadcast, while establishing re-entrant stop-to-start lifecycle safety.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TrackerService` has received `ACTION_STOP` and `finalizationTask` is actively running on `mDbExecutor`,
• *When* `onStartCommand` with `START_NORMAL` is delivered to the same `TrackerService` instance before `finalizationTask` completes,
• *Then* no `RejectedExecutionException` is thrown, the new session task queues behind the finalization task in strict FIFO order, the previous session finalizes with its isolated parameters, and the previous session's completion callback does not invoke `performStopSelf()`.
• *Given* `TrackerService` receives `ACTION_STOP` and no subsequent `onStartCommand` arrives,
• *When* `finalizationTask` finishes,
• *Then* `performStopSelf()`, `performStopForeground()`, and `safeUnbindBanalService()` are invoked on the main thread, and all executors are cleanly shut down in `onDestroy()`.
• *Given* `TrackerService.onDestroy()` is invoked while `mIsStopping == true`,
• *When* `onDestroy()` executes,
• *Then* `mDbExecutor.shutdown()` and `mScheduler.shutdownNow()` are executed to prevent worker thread leaks.

**Invariants**: Zero `RejectedExecutionException`; strict single-thread FIFO execution on `mDbExecutor`; zero state cross-contamination between consecutive sessions; clean service and executor destruction on terminal exit.

### REQ-SEN-028: Pairable Virtual Sensor ATT Fused (Position und Geschwindigkeit) with Priority Routing and Input Source Guards [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SEN-028`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceType.java`, `SpeedAndLocationDevice_ATTFused.kt`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `SensorType.java`, `TrackerService.java`, `EditDeviceDialog.kt`, `strings_devices.xml`
- **Rationale**: Expose the 5-state unicycle odometry fusion engine (ATT-131) as an activatable/pairable virtual Smartphone device with MySensorManager priority routing, 1-Hz world yaw-rate persistence in WorkoutSamples.db for post-workout ERTS replay (ATT-7750), and guarded UI source configuration in EditDeviceDialog.kt (ATT-7751, extending REQ-TRK-024 and REQ-FIL-007..010):
- **Description**:
  1. *Pairable Smartphone Virtual Device & Schema Migration (`REQ-SEN-028.1`)*:
     - `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` SHALL be declared under `Protocol.SMARTPHONE`, providing `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `LINE_DISTANCE_m`, `BEARING`, and `ACCURACY`.
     - `DevicesDbHelper.DB_VERSION` SHALL be incremented to `11`.
     - An idempotent migration `migrateAddSpeedAndLocationAttFused()` SHALL insert the `SPEED_AND_LOCATION_ATT_FUSED` device row into `Devices4` table with default `PAIRED = 0` (unpaired/opt-in) on upgrade (`oldVersion <= 10`) and on fresh database creation (`addLocationDevicesToDb`).
     - Display name strings `devices_location_ATT_fused` SHALL be localized across all 9 supported languages ("ATT Fused (Position und Geschwindigkeit)" / "ATT Fused (Location & Speed)").
  2. *MySensorManager Priority Hierarchy Integration (`REQ-SEN-028.2`)*:
     - In `MySensorManager.java`, `SPEED_AND_LOCATION_ATT_FUSED` SHALL be placed at index 0 of `LOCATION_PRIORITY` (ahead of `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS`).
     - `SPEED_AND_LOCATION_ATT_FUSED` SHALL be integrated at index 0 of `SPEED_PRIORITY` when active and paired, ensuring fused speed and distance incorporating calibrated wheel revolutions take precedence over raw speed, and cleanly falling back to standard hardware/GNSS priority when unpaired.
  3. *Device Driver Lifecycle & 1-Hz Yaw-Rate Persistence (`REQ-SEN-028.3`)*:
     - `SpeedAndLocationDevice_ATTFused` SHALL extend `SpeedAndLocationDevice`, driving `UnicycleOdometryEkf`, `QuaternionGyroProjector`, and `WheelCircumferenceCalibrator`.
     - `DeviceManager` SHALL instantiate and manage `mSpeedAndLocationDevice_ATTFused` upon pairing activation and shut it down upon unpairing.
     - `SensorType.WORLD_YAW_RATE_radps` SHALL be declared in `SensorType.java` (`SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`).
     - While `SPEED_AND_LOCATION_ATT_FUSED` is active with Gyro turn support enabled, it SHALL emit 1-Hz world yaw-rate $\omega_{ENU,z}$ which `TrackerService` SHALL persist into `WorkoutSamples.db` for post-workout ERTS replay (ATT-7750).
  4. *EditDeviceDialog Educational Card & Paired-Source Guards (`REQ-SEN-028.4`)*:
     - In `EditDeviceDialog.kt`, opening configuration for `SPEED_AND_LOCATION_ATT_FUSED` SHALL display an educational "How It Works" card detailing multi-sensor fusion benefits and trade-offs.
     - The dialog SHALL provide 4 configuration toggles with pairing and hardware availability guards:
       a) *Location Source (GNSS)*: Default true; disabled with localized hint if neither GPS nor Google Fused is paired.
       b) *External Wheel/Footpod Sensors*: Default true; disabled with localized hint if no external speed sensor is paired.
       c) *3D Gyro Turn Support*: Default true; disabled with localized hint if hardware gyroscope / game rotation vector sensors are unavailable.
       d) *Auto Wheel Circumference Calibration ($C_{wheel}$)*: Default false; disabled with localized hint if no external wheel sensor is paired.
     - The UI layout SHALL satisfy Rule 84 visual inspection with zero text clipping or ellipsizing across German and English.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEN-028`, extending `REQ-TRK-024` (*Sensor Priority Hierarchy*) and `REQ-FIL-007`..`010` (*Odometry EKF*). Targets `DeviceType.java`, `SpeedAndLocationDevice_ATTFused.kt`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: `ATT-3961` / `ATT-5491` / `ATT-5492` (sensor priority hierarchy and network excision in `Devices.db` v10) and `ATT-131` (`bc4a4109`, 5-state unicycle EKF core).
3. *Root Reason for Existing Formulation*: Prior versions only supported single-source position tracking without cross-sensor coupling or modular opt-in virtual devices.
4. *Preservation of Core Invariants*: Default state is unpaired (`PAIRED = 0`). When inactive, priority routing, battery drain, and sensor behavior are 100% identical to baseline. Independent device-attributed columns in `WorkoutSamples.db` remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Smartphone sensor list on fresh install or upgrade to DB_VERSION 11,
• *When* the user views available sensors,
• *Then* `ATT Fused (Position und Geschwindigkeit)` is listed with default unpaired state (`PAIRED = 0`) across all 9 supported languages.
• *Given* `ATT Fused (Position und Geschwindigkeit)` is paired/activated,
• *When* tracking runs,
• *Then* `MySensorManager` prioritizes its fused coordinates, bearing, speed, and distance above Google Fused and GPS, and `WorkoutSamples.db` persists separate device-attributed columns and 1-Hz `WORLD_YAW_RATE_radps`.
• *Given* the user opens `EditDeviceDialog.kt` for `ATT Fused`,
• *When* hardware gyroscopes or external speed sensors are absent/unpaired,
• *Then* the corresponding toggle is greyed out with a clear localized hint and zero text clipping.
• *Given* `ATT Fused` is deactivated in the sensor list,
• *When* a workout is recorded,
• *Then* location and speed routing cleanly reverts to standard GPS/Google Fused/wheel priority with zero background gyro drain.

**Invariants**: Unpaired by default; zero battery consumption when inactive; thread safety on sensor single-thread dispatchers; 9-language localization parity; 100% clean-room test pass rate.

### REQ-FIL-016: Kinematic Terrain-Slope Altitude Fusion Engine & Pairable ATT Fused (Höhe und Steigung) Virtual Device [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-016`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, `DeviceType.java`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, `strings_devices.xml`
- **Rationale**: Provide a coupled kinematic altitude and slope estimation engine ($dh = g \cdot ds$) with observability-aware 2-State $[h_k, g_k]^T$ and 3-State $[h_k, g_k, b_{baro,k}]^T$ filtering, standstill altitude clamping, and paired-source UI configuration guards, exposed as pairable virtual sensor ATT Fused (Höhe und Steigung) (ATT-6279, extending REQ-FIL-012, REQ-SEN-025, and REQ-SEN-026):
- **Description**:
  1. *Kinematic Terrain-Slope EKF & Observability Modes (`REQ-FIL-016.1`)*:
     - The system SHALL maintain state elevation $h$ (meters) and terrain slope grade $g$ (dimensionless, $\frac{dh}{ds}$) coupled to horizontal ground displacement $\Delta s_k = v_k \cdot \Delta t_k$ via $h_k = h_{k-1} + g_{k-1} \cdot \Delta s_k$.
     - When unanchored (Barometer + Speed only), the filter SHALL operate in *2-State mode $[h_k, g_k]^T$*, updating elevation via barometric measurements $z_{baro,k} = h_k$.
     - When anchored (DEM via `ALTITUDE_FROM_POSITION` or GPS altitude paired & enabled), the filter SHALL operate in *3-State mode $[h_k, g_k, b_{baro,k}]^T$*, where $z_{baro,k} = h_k + b_{baro,k}$ and $z_{ref,k} = h_k$, making barometric thermal and synoptic weather drift continuously observable and smoothly decoupling elevation from 1m DEM discrete grid staircase artifacts.
  2. *Standstill Clamping & Double-EMA Prevention (`REQ-FIL-016.2`)*:
     - When horizontal speed $v = 0$ ($\Delta s_k = 0$), slope process noise SHALL be zero ($Q_g = 0$) and ground altitude SHALL be clamped ($dh = 0$), preventing stationary barometric weather/temperature drift from accumulating false climbing meters or oscillating slope.
     - In 3-State mode, standstill barometric drift SHALL update barometric bias $b_{baro,k}$ directly.
     - `BarometricAltitudeFilter.kt` SHALL expose `filterSlewOnly(rawAltitude, timestampMs)` (or equivalent) providing median-filtered and slew-rate clamped raw elevation, which `AltitudeAndSlopeDevice_ATTFused` SHALL ingest to prevent cascaded double-EMA phase lag.
  3. *Pairable Virtual Device & Devices.db Schema v12 Migration (`REQ-FIL-016.3`)*:
     - `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` SHALL be declared under `Protocol.SMARTPHONE`, providing `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
     - `DevicesDbHelper.DB_VERSION` SHALL be incremented to `12`. Idempotent migration `migrateAddAltitudeAndSlopeAttFused()` SHALL insert the device row with default `PAIRED = 0` (unpaired/opt-in) on upgrade (`oldVersion <= 11`) and fresh install.
     - Display name strings `devices_altitude_slope_ATT_fused` SHALL be localized across all 9 supported languages ("ATT Fused (Höhe und Steigung)" / "ATT Fused (Altitude & Slope)").
  4. *MySensorManager Priority Hierarchy Integration (`REQ-FIL-016.4`)*:
     - When paired, `ALTITUDE_AND_SLOPE_ATT_FUSED` SHALL be placed at index 0 of `ALTITUDE_PRIORITY` (ahead of `ALTITUDE_FROM_POSITION` and `ALTITUDE_FROM_PRESSURE`).
     - Dedicated priority arrays SHALL be established in `MySensorManager.java` for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT` prioritizing `ALTITUDE_AND_SLOPE_ATT_FUSED` at index 0 over `VERTICAL_SPEED_AND_SLOPE`.
     - Deactivating `ALTITUDE_AND_SLOPE_ATT_FUSED` SHALL cleanly fall back to `VerticalSpeedAndSlopeDevice` and standard altitude priority.
  5. *EditDeviceDialog Educational Card & Source Guards (`REQ-FIL-016.5`)*:
     - In `EditDeviceDialog.kt`, opening configuration for `ALTITUDE_AND_SLOPE_ATT_FUSED` SHALL display an educational "How It Works" card detailing kinematic slope coupling, thermal drift decoupling, and battery considerations.
     - The dialog SHALL provide 4 configuration toggles with pairing and hardware availability guards:
       a) *Höhe aus Luftdruck (Barometer)*: Default true; disabled with localized hint if `ALTITUDE_FROM_PRESSURE` is not paired or pressure hardware is absent.
       b) *Topografische Höhe (DEM)*: Default false (opt-in); disabled with localized hint if `ALTITUDE_FROM_POSITION` is not paired.
       c) *GPS-Höhe (Langzeit-Driftkorrektur)*: Default false; disabled with localized hint if no location provider is paired.
       d) *Geschwindigkeits-Kopplung (Stillstands-Fix & dh = g * ds)*: Default true; uses active speed/distance sensor.
     - The UI layout SHALL satisfy Rule 84 visual inspection with zero text clipping or ellipsizing across German and English.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-FIL-016`, extending `REQ-FIL-012` (*Stabilized VAM Metric*), `REQ-SEN-025` (*DEM Elevation*), and `REQ-SEN-026` (*Barometric Elevation*). Targets `KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `MySensorManager.java`, `DevicesDatabaseManager.java`, and `EditDeviceDialog.kt`.
2. *Historical Origin & Commit Trace*: `ATT-1621` / `ATT-1738` (VAM linear regression), `ATT-4859` (DEM altitude device), `ATT-6265` (causal barometric outlier filtering).
3. *Root Reason for Existing Formulation*: Prior designs treated barometric elevation, DEM elevation, and vertical speed as decoupled silos without kinematic terrain-slope progression ($dh = g \cdot ds$).
4. *Preservation of Core Invariants*: Default state is unpaired (`PAIRED = 0`). When inactive, priority routing, battery drain, and sensor behavior are 100% identical to baseline. Independent device-attributed columns in `WorkoutSamples.db` remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Smartphone sensor list on fresh install or upgrade to DB_VERSION 12,
• *When* the user views available sensors,
• *Then* `ATT Fused (Höhe und Steigung)` is listed with default unpaired state (`PAIRED = 0`) across all 9 supported languages.
• *Given* `ATT Fused (Höhe und Steigung)` is configured with Barometer + Speed only (DEM disabled),
• *When* the user moves and then stops ($v = 0$),
• *Then* the filter operates in 2-State mode $[h_k, g_k]^T$ without double-EMA lag and clamps altitude changes during standstills to prevent false climb accumulation.
• *Given* `ATT Fused (Höhe und Steigung)` is configured with Barometer + DEM + Speed,
• *When* DEM elevation updates arrive,
• *Then* the filter operates in 3-State mode $[h_k, g_k, b_{baro,k}]^T$, isolating barometric thermal/weather drift into $b_{baro}$ while maintaining smooth altitude and slope without 1m staircase artifacts.
• *Given* the user opens `EditDeviceDialog.kt` for `ATT Fused (Höhe und Steigung)`,
• *When* `ALTITUDE_FROM_PRESSURE` or `ALTITUDE_FROM_POSITION` is not paired,
• *Then* the corresponding toggle is greyed out with a clear localized hint and zero text clipping.
• *Given* `ATT Fused (Höhe und Steigung)` is deactivated in the sensor list,
• *When* a workout is recorded,
• *Then* altitude, slope, and VAM cleanly revert to `VerticalSpeedAndSlopeDevice` and standard altitude priority.

**Invariants**: Unpaired by default (`PAIRED = 0`); zero CPU/battery drain when inactive; single-thread sensor dispatcher safety; 9-language localization parity; 100% clean-room test pass rate.

### REQ-FIL-017: Post-Workout Extended Rauch-Tung-Striebel (ERTS/RTS) Zero-Phase Smoothing Engine [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-017`, `TST-FIL-025`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ExtendedRtsOdometrySmoother.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `ErtsPostWorkoutRecomputer.kt`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, `TrackerService.java`
- **Rationale**: Provide a non-causal bidirectional Extended Rauch-Tung-Striebel (ERTS/RTS) smoother for 2D track odometry and 3D elevation profiles reusing ATT-131 and ATT-6279 kinematic models, strictly preserving raw device columns in `WorkoutSamples.db` while updating canonical columns and `WorkoutSummaries.db` streams, guarded by an expert settings opt-in toggle (ATT-7750, extending `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010`, and `REQ-FIL-016`):
- **Description**:
  1. *2D Trajectory & Speed Extended RTS Pass (`REQ-FIL-017.1`)*:
     - The system SHALL execute a forward EKF replay using the 5-state unicycle odometry model $[E, N, v, \psi, b_{\omega_z}]^T$, recording prior states $x_{k|k-1}$, prior covariances $P_{k|k-1}$, state-transition Jacobians $F_{k-1}$, posterior states $x_{k|k}$, and posterior covariances $P_{k|k}$ across discrete 1-Hz sample points $k = 0 \dots N$.
     - The backward recursion SHALL execute:
       $$C_k = P_{k|k} F_k^T (P_{k+1|k})^{-1}$$
       $$x_{k|N} = x_{k|k} + C_k \left(x_{k+1|N} - x_{k+1|k}\right)$$
       $$P_{k|N} = P_{k|k} + C_k \left(P_{k+1|N} - P_{k+1|k}\right) C_k^T$$
       for $k = N-1 \dots 0$, normalizing angular heading differences $\psi_{k+1|N} - \psi_{k+1|k}$ to $[-\pi, \pi)$ via `normalizeAngle`.
     - Backward smoothing SHALL propagate post-outage GNSS position and Doppler speed fixes backward through outages (e.g. 45s curved tunnel or canopy dropout) to eliminate trajectory steps and speed dropouts without rounding genuine sharp turns or hairpins.
     - The 5x5 matrix inversion $(P_{k+1|k})^{-1}$ and recursion across 14,400 samples (4-hour workout) SHALL execute in $< 100\text{ ms}$ with zero external matrix libraries.
  2. *Kinematic Altitude & Slope Extended RTS Pass (`REQ-FIL-017.2`)*:
     - The system SHALL execute a forward-backward RTS pass over vertical elevation and terrain slope using the exact kinematic model $[h_k, g_k, b_{baro,k}]^T$ (or 2-State $[h_k, g_k]^T$ if DEM/GPS reference is absent), driven by smoothed horizontal displacements $\Delta s_{k|N} = v_{k|N} \cdot \Delta t_k$.
     - Backward recursion SHALL eliminate causal startup transients and filter phase delay, yielding zero-phase altitude $h_{k|N}$, terrain slope $g_{k|N}$, vertical speed $v_{z,k|N} = \frac{h_{k|N} - h_{k-1|N}}{\Delta t_k}$, and cumulative ascent/descent.
     - Standstill segments ($v < 0.3\text{ m/s}$) SHALL clamp slope process noise ($Q_g = 0$) and prevent false climb accumulation.
  3. *Strict Column Scope Invariant (`REQ-FIL-017.3`)*:
     - In `WorkoutSamples.db`, the post-workout smoother SHALL update *exclusively* the canonical unsuffixed columns:
       `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `BEARING`, `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
     - Every source-attributed per-device column (`<SensorType> (<SourceName>)`), explicitly including `LATITUDE (GPS)`, `SPEED_mps (GPS)`, `SPEED_mps (Google Fused)`, `SPEED_mps (att_fused)`, `ALTITUDE (Höhe aus Luftdruck)`, `ALTITUDE (Topografische Höhe (DEM))`, and `ALTITUDE (att_fused_altitude_slope)`, SHALL remain 100% byte-for-byte untouched.
  4. *Workout Summaries & Stream Synchronization (`REQ-FIL-017.4`)*:
     - Upon successful ERTS smoothing of `WorkoutSamples.db`, the system SHALL update `WorkoutSummaries.db` via `WorkoutSummariesDatabaseManager.updateMapAndStreams` with the re-encoded smoothed polyline, altitude stream, and distance stream, and update summary metrics (total distance, total ascent/descent, max/avg speed).
  5. *Expert Settings Opt-In Configuration (`REQ-FIL-017.5`)*:
     - The smoother SHALL be guarded by a preference `postWorkoutErtsSmoothingEnabled` in `TuningPreferencesDataStore.kt` (`KEY_POST_WORKOUT_ERTS_SMOOTHING`), defaulting to `false`.
     - In `SensorsGpsFilterSection.kt` and `AdvancedTuningScreen.kt`, a dedicated `TuningToggleItem` SHALL expose this setting ("Post-Workout-ERTS-Glättung" / "Post-Workout ERTS Smoothing"), localized across all 9 supported languages with zero text clipping.
     - When the preference is disabled, post-workout finalization SHALL bypass ERTS smoothing with zero behavioral change from baseline.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-FIL-017`, extending `REQ-FIL-007`..`REQ-FIL-010` (*Odometry EKF & Calibration*) and `REQ-FIL-016` (*Coupled Kinematic Elevation EKF*). Targets `ExtendedRtsOdometrySmoother.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `ErtsPostWorkoutRecomputer.kt`, `TuningPreferencesDataStore.kt`, and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: `ATT-131` (Unicycle odometry EKF), `ATT-7751` (SpeedAndLocationDevice_ATTFused), `ATT-6279` (Kinematic altitude and slope EKF), `ATT-352` (Polyline and streams decoding).
3. *Root Reason for Existing Formulation*: Live filtering is inherently causal and suffers from irreversible filter lag and GNSS re-acquisition position steps. Post-workout zero-phase bidirectional smoothing eliminates latency while maintaining mathematical consistency.
4. *Preservation of Core Invariants*: Raw sensor and device-specific columns remain untouched; opt-in default (`false`); zero runtime overhead during active recording; 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with a 45s curved tunnel GNSS outage and a 15m exit position jump,
• *When* the workout is completed with ERTS smoothing enabled,
• *Then* the backward pass eliminates the exit step discontinuity and produces a smooth, continuous trajectory without jagged steps.
• *Given* an altitude profile with initial barometric offset followed by delayed DEM anchor lock,
• *When* processed by ERTS backward smoothing,
• *Then* the DEM anchor propagates backward to eliminate initial elevation bias and standstill climb accumulation is 0.0 m.
• *Given* a `WorkoutSamples.db` table containing canonical and device columns (`LATITUDE (GPS)`, `SPEED_mps (GPS)`, etc.),
• *When* `ErtsPostWorkoutRecomputer` runs,
• *Then* all device columns remain byte-for-byte identical, canonical columns are updated with smoothed data, and `WorkoutSummaries.db` streams are refreshed.
• *Given* the preference `postWorkoutErtsSmoothingEnabled` is false (default),
• *When* a workout is finalized,
• *Then* ERTS recomputation is completely bypassed and live recorded values remain unmodified.

**Invariants**: Disabled by default (`false`); raw device-attributed columns 100% byte-for-byte preserved; single-transaction SQLite update; zero external math libraries; 9-language localization parity; 100% clean-room test pass rate.

### REQ-TRK-077: Indoor GPS Suppression, External Speed Sensor Coupling & IMU Rest-Variance Stationary Pinning [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-077`
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`, `MySensorManager.java`, `TuningConfig.kt`, `SettingsDataStoreJavaHelper.kt`
- **Rationale**: While GNSS Doppler and spatial displacement pinning (REQ-TRK-048, REQ-TRK-056, REQ-TRK-074) suppress stationary jitter during outdoor riding at open sky, indoor trainer workouts and café/rest stops suffer from severe multipath coordinate jumps (> 1.5 * accuracy) that break stationary pinning, recording tangled spiderweb polylines and accumulating spurious GPS distance. The system SHALL support indoor GPS suppression, couple stationary pinning to connected external wheel/footpod sensors reporting 0.0 m/s, and gate unpinning on low-power accelerometer rest variance (ATT-47, refining REQ-TRK-048, REQ-TRK-056, and REQ-TRK-074):
- **Description**:
  1. *Indoor Workout GPS Drift & Distance Suppression (`REQ-TRK-077.1`)*:
     - `SpeedAndLocationDevice` SHALL maintain an `mIsIndoorMode` state (default `false`, with thread-safe `setIndoorMode(boolean)` and `isIndoorMode()`).
     - When `mIsIndoorMode == true`:
       - Upon receiving the first valid location fix, `SpeedAndLocationDevice` SHALL initialize `mStationaryAnchor = location` and `mIsStationaryPinned = true`.
       - While `mIsIndoorMode == true`, `SpeedAndLocationDevice` SHALL keep `mIsStationaryPinned = true` unconditionally: unpinning checks SHALL evaluate `shouldUnpin = false` regardless of coordinate displacement or GPS speed.
       - GPS distance increments SHALL be suppressed (`delta_distance = 0.0`), preventing distance creep in `mDistanceSensor` and `mLapDistanceSensor`.
       - GPS speed SHALL be clamped to `0.0 m/s` (`mSpeed = 0.0`), ensuring paired external sensors (smart trainer, speed sensor, footpod) retain uninterrupted priority for speed and distance in `MySensorManager`.
       - Published coordinates SHALL remain pinned to `mStationaryAnchor`, eliminating indoor spiderweb polylines.
  2. *External Wheel / Footpod Zero-Speed Pinning Coupling (`REQ-TRK-077.2`)*:
     - In `SpeedAndLocationDevice.onNewLocation()`, before evaluating coordinate displacement unpinning, the device SHALL inspect connected speed sensors via `mMySensorManager`.
     - When an active external speed sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, or `BIKE_POWER`) is connected, has updated within the last $5000\text{ ms}$ (`System.currentTimeMillis() - sensor.getLastUpdateTimestamp() <= 5000L`), and reports zero velocity (`sensor.getValue() <= SPEED_ZERO_THRESHOLD` where threshold $= 0.1\text{ m/s}$):
       - Standstill is established by physical wheel/footpod measurement.
       - Unpinning SHALL be blocked (`shouldUnpin = false`), and `isCandidateStationary` SHALL evaluate as `true`.
       - The device SHALL remain pinned to `mStationaryAnchor` even if GNSS multipath displacement exceeds `escapeRadius` ($> 1.5 \times \text{accuracy}$).
       - Distance creep SHALL be suppressed (`delta_distance = 0.0`).
       - As soon as the external sensor reports physical motion ($v > 0.1\text{ m/s}$) or updates become stale ($> 5000\text{ ms}$), external standstill locking SHALL disengage immediately.
  3. *Low-Power Accelerometer Rest-Variance Gating (`REQ-TRK-077.3`)*:
     - When a hardware accelerometer (`Sensor.TYPE_ACCELEROMETER`) is available, `SpeedAndLocationDevice` SHALL monitor 3D acceleration samples $(a_x, a_y, a_z)$ at low power (`SensorManager.SENSOR_DELAY_UI`).
     - The device SHALL compute the Euclidean acceleration norm $||a|| = \sqrt{a_x^2 + a_y^2 + a_z^2}$ and maintain a rolling window of samples over $1.5\text{--}2.0\text{ s}$.
     - When the rolling variance $\sigma^2 = \frac{1}{N}\sum (||a||_i - \mu)^2$ is below the static rest floor ($\sigma^2 \le 0.05\text{ (m/s}^2)^2$):
       - The phone/bike is confirmed physically motionless (e.g. resting on table or stationary against a wall).
       - Stationary pinning SHALL be reinforced: unpinning on single-fix GNSS multipath spikes or noisy Doppler velocity SHALL be blocked (`shouldUnpin = false`).
       - As soon as physical motion variance resumes ($\sigma^2 > 0.05\text{ (m/s}^2)^2$), the rest gate SHALL release within $\le 1.0\text{ s}$, restoring standard dynamic escape bounds.
  4. *Mock & Sensorless Fallback Invariance (`REQ-TRK-077.4`)*:
     - When no external wheel/footpod sensor is connected and hardware accelerometer is absent (e.g. mock GPS replay via `tools/replay_workout.py`, headless emulators, or standard outdoor GPS-only tracking):
       - `SpeedAndLocationDevice` SHALL operate with neutral fallback, preserving 100% mathematical and behavioral equivalence with baseline `REQ-TRK-048`, `REQ-TRK-056`, and `REQ-TRK-074`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-077`, refining `REQ-TRK-048` (*Stationary GPS Jitter Suppression*, `ATT-4850`), `REQ-TRK-056` (*Google Fused Doppler Velocity Plausibility & Movement Pinning Prevention*, `ATT-6256`), and `REQ-TRK-074` (*Entry Hysteresis & Sub-Second Duplicate Filtering*, `ATT-6682`). Targets `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_GPS.java`, and `SpeedAndLocationDevice_GoogleFused.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4850`), Sprint 2026-45.13 (`ATT-6256`), and Sprint 2026-46.2 (`ATT-6682`).
3. *Root Reason for Existing Formulation*: Prior requirements assumed GPS signals alone were sufficient to detect standstill and motion. In indoor environments (trainers, gyms) and café stops under awnings, multipath reflection produces non-zero Doppler and 10–30 m coordinate jumps that defeat GPS-only deadbands.
4. *Preservation of Core Invariants*: Standalone outdoor GPS tracking, 3-tick entry hysteresis, start-line pinning (`mPrevLocation == null`), Doppler zero preservation, and teleportation spike limit $\le 70\text{ m/s}$ (`REQ-TRK-041.1`) remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SpeedAndLocationDevice` with indoor mode active (`mIsIndoorMode == true`),
• *When* indoor GNSS multipath fixes arrive with random coordinate jumps $> 1.5 \times \text{accuracy}$,
• *Then* `SpeedAndLocationDevice` remains pinned to `mStationaryAnchor`, clamps GPS speed to $0.0\text{ m/s}$, and accumulates zero distance creep (`delta_distance = 0.0`).
• *Given* an outdoor ride with a connected ANT+/BLE wheel speed sensor reporting $0.0\text{ m/s}$ at a café stop,
• *When* indoor GNSS multipath jumps beyond $\text{escapeRadius}$ ($> 1.5 \times \text{accuracy}$),
• *Then* `SpeedAndLocationDevice` remains pinned to `mStationaryAnchor` without accumulating false GPS coordinates or distance.
• *Given* the phone/bike resting completely motionless with accelerometer rolling variance $\sigma^2 \le 0.05\text{ (m/s}^2)^2$,
• *When* degraded GNSS reports noisy non-zero Doppler jumps,
• *Then* stationary pinning is maintained and releases within $\le 1.0\text{ s}$ once physical movement resumes.
• *Given* standalone GPS tracking without external sensors or accelerometer hardware (e.g. mock replay via `tools/replay_workout.py`),
• *When* location fixes arrive,
• *Then* existing GNSS/Doppler unpinning and hysteresis behavior works identically with zero regressions.

**Invariants**: Zero GPS distance creep during indoor trainer workouts; zero spiderweb polyline drift at rest stops; 100% backward compatibility for mock GPS replays and standalone GPS tracking; 100% test pass rate across `banalservice.devices.*`.

### REQ-SEN-029: Front/Rear Wheel Speed Sensor Mounting Position Tracking, Selection Dialog & Dual-Sensor Guidance [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SEN-029`, `TST-SEN-031`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentDbHelper.java`, `WheelPosition.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, `EquipmentSensorMatrixScreen.kt`, `EditDeviceDialog.kt`, `strings_equipment.xml`, `docs/guide/`
- **Rationale**: Track FRONT vs. REAR wheel sensor mounting positions in SQLite and Compose UI to enable dual-wheel odometry fusion (ATT-131 / ATT-7751), per-wheel circumference history, and dual-wheel iTPMS slow-puncture detection (ATT-7753), while providing clear contextual guidance explaining the physical benefits of 1 vs. 2 wheel speed sensors alongside smartphone GPS (ATT-7752, extending REQ-UI-256 and REQ-DAT-031):
- **Description**:
  1. *SQLite Schema Extension & Migration (`REQ-SEN-029.1`)*:
     - The system SHALL declare enum `WheelPosition` with values `FRONT`, `REAR`, `UNSPECIFIED`.
     - In `EquipmentDbHelper.java`, `DB_VERSION` SHALL be incremented to `4`.
     - On upgrade (`oldVersion < 4`), `onUpgrade` SHALL execute non-destructive migration: `ALTER TABLE Links ADD COLUMN wheel_position TEXT DEFAULT 'UNSPECIFIED'`.
     - In fresh installations, `CREATE_LINKS_TABLE` SHALL include `wheel_position text default 'UNSPECIFIED'`.
     - Existing links SHALL preserve 100% data integrity with `wheel_position = 'UNSPECIFIED'`.
     - `EquipmentDbHelper` SHALL expose `getDeviceWheelPositionsForEquipment(equipmentId)`, `getWheelPosition(equipmentId, deviceId)`, and `setDeviceLink(equipmentId, deviceId, isLinked, wheelPosition)`.
  2. *EquipmentRepository & ViewModel Synchronization with Bike Guardrail (`REQ-SEN-029.2`)*:
     - `EquipmentItem` SHALL encapsulate `linkedDeviceWheelPositions: Map<Long, WheelPosition>`.
     - `EquipmentRepository` and `EquipmentViewModel` SHALL support updating wheel mounting positions and dispatching reactive notifications via `equipmentLinksChanged`.
     - *Guardrail per bike*: When a bike already links a speed sensor with position `FRONT` and the user assigns `FRONT` to a second sensor on that bike, the system SHALL offer a clean swap or reassign the existing sensor to `REAR`/`UNSPECIFIED` so two sensors on the same bike are never accidentally both marked `FRONT` (and similarly for `REAR`).
  3. *Interactive Wheel Position Selection Dialog & Badge Rendering (`REQ-SEN-029.3`)*:
     - In `EquipmentSensorMatrixScreen.kt`, tapping a wheel speed sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`) cell SHALL display a compact Material 3 dialog prompting for mounting position: *Vorderrad (Front)*, *Hinterrad (Rear)*, or *Trennen (Unlink)* if already linked (refined by `REQ-SEN-032`).
     - Tapping a non-wheel sensor (HRM, Cadence, Power, Radar) SHALL retain standard 1-tap checkbox toggling.
     - Linked wheel speed sensor cells SHALL display a localized short badge (e.g. `V` / `H` in German, `F` / `R` in English) alongside the checkmark within `SENSOR_COLUMN_WIDTH` (88 dp) without text clipping (`Rule 84`).
  4. *In-App Guidance Card & Living User Documentation (`REQ-SEN-029.4`)*:
     - `EquipmentSensorMatrixScreen.kt` and `EditDeviceDialog.kt` (for `BIKE_SPEED` and `SPEED_AND_LOCATION_ATT_FUSED`) SHALL provide a localized info card or dialog explaining the physical advantages of 1 vs. 2 wheel sensors:
       a) *1 Wheel Sensor + GPS*: Instant speed responsiveness without GNSS filter lag, exact distance under canopy, mountain hairpins and tunnels, sharper live slope %, and tire pressure baseline tracking.
       b) *2 Wheel Sensors (Front + Rear) + GPS*: Independent front/rear tire circumference history, GPS-free ABS-style dual-wheel slow-puncture detection within 150–250 m (identifying front vs. rear pressure loss), and triple-redundant multi-sensor fusion.
     - `docs/guide/en/03-equipment-sensor-matrix.md` and `docs/guide/de/03-equipment-sensor-matrix.md` SHALL detail Section 3.3 ("Why 1 or 2 Wheel Sensors Alongside GPS?").
     - All user-facing UI strings SHALL be translated with 100% parity across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEN-029`, refining and extending `REQ-UI-256` (*Equipment Management Matrix*, `ATT-2126`) and `REQ-DAT-031` (*EquipmentDbHelper Schema*, `ATT-4011`). Targets `EquipmentDbHelper.java`, `WheelPosition.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, `EquipmentSensorMatrixScreen.kt`, `EditDeviceDialog.kt`, and `docs/guide/`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.15 (`ATT-2126`, `ATT-2307`, `REQ-UI-256` matrix) and Sprint 2026-46.6 (`ATT-7751`, `REQ-SEN-028` ATT Fused sensor).
3. *Root Reason for Existing Formulation*: Original matrix only supported binary association without tracking per-wheel mounting geometry or dual-wheel puncture differentials.
4. *Preservation of Core Invariants*: Default `UNSPECIFIED` value preserves 100% of existing equipment links; non-wheel sensors remain unaffected; backup/restore compatibility is maintained; 100% test pass rate across equipment and database suites.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing `Equipment.db` at version 3 with bike-sensor links,
• *When* the database is opened under `EquipmentDbHelper` version 4,
• *Then* `wheel_position` column is added non-destructively, existing links return `WheelPosition.UNSPECIFIED`, and new links with `FRONT`/`REAR` persist reliably.
• *Given* `EquipmentSensorMatrixScreen.kt`,
• *When* the user taps a `BIKE_SPEED` or `BIKE_SPEED_AND_CADENCE` cell for a bike,
• *Then* an interactive dialog prompts for *Vorderrad* or *Hinterrad*, and the cell renders a compact non-clipping badge (`V`/`H` or `F`/`R`) within 88 dp column width.
• *Given* a bike with an existing `FRONT` wheel sensor,
• *When* the user selects `FRONT` for a second wheel speed sensor on the same bike,
• *Then* the system detects the conflict and offers to swap positions or reassign cleanly without leaving two `FRONT` sensors on the same bike.
• *Given* the sensor matrix or `EditDeviceDialog.kt`,
• *When* the user opens the sensor info guidance,
• *Then* localized text across all 9 languages clearly explains the physical benefits of 1 vs. 2 wheel sensors alongside GPS.

**Invariants**: Zero data loss during SQLite upgrade; non-wheel sensors retain 1-tap checkbox toggling; zero text clipping across German and English UI strings (`Rule 84`); 100% clean-room test pass rate.

### REQ-SEN-030: Per-Bike Front/Rear Wheel Circumference Time-Series History & GPS-Free Dual-Wheel iTPMS Puncture Detection [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SEN-030`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DualWheelTpmsEstimator.kt`, `IndirectTpmsEstimator.kt`, `BANALServiceRepository.kt`, `EquipmentDbHelper.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, `WheelCircumferenceHistoryChart.kt`, `strings.xml`
- **Rationale**: Provide automotive ABS-style GPS-free dual-wheel slow-puncture detection within 150–250 m by tracking the revolution ratio $\rho_k = \frac{\Delta N_{rear,k}}{\Delta N_{front,k}} = \frac{C_{front}}{C_{rear}}$ with yaw-rate gating ($|\omega_{ENU,z}| < 5^\circ/\text{s}$), identifying whether the front or rear tire is deflating without requiring GPS signal, and persist post-workout circumference estimates into `Equipment.db` table `WheelCircumferenceHistory` to render longitudinal Front vs. Rear tire pressure/wear time-series charts per bike with pumped baseline reset (ATT-7753, extending REQ-TRK-050 and REQ-SEN-029):
- **Description**:
  1. *GPS-Free Dual-Wheel Ratio CUSUM Detector (`REQ-SEN-030.1`)*:
     - When a bike has active `FRONT` and `REAR` wheel speed sensors, `DualWheelTpmsEstimator` SHALL monitor $\rho_k = \frac{\Delta N_{rear,k}}{\Delta N_{front,k}}$.
     - Updates SHALL be gated on low yaw-rate ($|\omega_{ENU,z}| < 0.087\text{ rad/s} \approx 5^\circ/\text{s}$ or straight bearing rate $< 5^\circ/\text{s}$) and minimum forward speed ($v \ge 3.0\text{ m/s}$).
     - The detector SHALL lock the baseline ratio $\rho_{start}$ after $300\text{ m}$ of qualified straight riding.
     - A sliding-window CUSUM detector over 150–250 m SHALL compute deviation $\delta = \frac{\rho_k - \rho_{start}}{\rho_{start}}$.
     - When $\delta \ge +0.008$ ($+0.8\%$), the system SHALL trigger a `CRITICAL` alert: *"Druckverlust am Hinterrad!"* (*Rear Wheel Pressure Loss!*).
     - When $\delta \le -0.008$ ($-0.8\%$), the system SHALL trigger a `CRITICAL` alert: *"Druckverlust am Vorderrad!"* (*Front Wheel Pressure Loss!*).
     - Sharp turns and hairpins ($|\omega_{ENU,z}| \ge 5^\circ/\text{s}$) SHALL be discarded with zero false alarms.
  2. *Concurrent Multi-Sensor Telemetry Routing (`REQ-SEN-030.2`)*:
     - `BANALServiceRepository.kt` SHALL maintain an estimator instance per active wheel speed sensor (`Map<Long, IndirectTpmsEstimator>`).
     - Each active wheel speed sensor SHALL be evaluated against GNSS to monitor multi-day pressure loss relative to pumped baseline (`baselineCircumferenceMeters`), alerting if both tires deflated equally in storage.
  3. *Longitudinal Wheel Circumference History Persistence (`REQ-SEN-030.3`)*:
     - `EquipmentDbHelper.java` (`DB_VERSION = 5`) SHALL declare table `WheelCircumferenceHistory` (`_id`, `workout_id`, `timestamp_ms`, `equipment_id`, `device_id`, `wheel_position`, `circumference_mm`, `std_error_mm`, `gated_distance_m`, `is_baseline`).
     - Upon workout finalization, converged circumference estimates from active wheel sensors SHALL be persisted into `WheelCircumferenceHistory`.
     - `EquipmentRepository` and `EquipmentViewModel` SHALL expose `getCircumferenceHistory(equipmentId)` and `setPumpedBaseline(equipmentId, deviceId)`.
  4. *Bike Equipment UI Circumference History Chart (`REQ-SEN-030.4`)*:
     - In Bike Equipment details, `WheelCircumferenceHistoryChart` SHALL render Front and Rear circumference curves over time side-by-side with localized legends.
     - The UI SHALL offer a *"Reifen gerade aufgepumpt (Baseline setzen)"* button to reset the pumped baseline for the selected bike/wheel.
     - Layout SHALL comply with `Rule 84` visual inspection with zero text clipping across all 9 languages.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEN-030`, extending `REQ-TRK-050` (*iTPMS Single-Sensor GNSS*, `ATT-4880`) and `REQ-SEN-029` (*Wheel Mounting Positions*, `ATT-7752`). Targets `DualWheelTpmsEstimator.kt`, `IndirectTpmsEstimator.kt`, `BANALServiceRepository.kt`, `EquipmentDbHelper.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, and `WheelCircumferenceHistoryChart.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4880`, commit `8d400192`, single-wheel iTPMS) and Sprint 2026-46.6 (`ATT-7752`, commit `4edcda86`, front/rear position tracking).
3. *Root Reason for Existing Formulation*: Baseline only evaluated the first wheel sensor against GNSS due to absence of mounting position metadata in `Equipment.db`.
4. *Preservation of Core Invariants*: Single-wheel bikes maintain exact baseline behavior of `REQ-TRK-050` with zero regression; non-wheel sensors remain unaffected; 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a bike with active `FRONT` and `REAR` wheel speed sensors during a GNSS-degraded ride,
• *When* the rear tire effective circumference drops by $\ge 0.8\%$ over $250\text{ m}$,
• *Then* the GPS-free dual-wheel ratio detector triggers a critical alert explicitly naming the *rear wheel* (*Hinterrad*), while hairpins ($|\omega_{ENU,z}| \ge 5^\circ/\text{s}$) produce zero false positives.
• *Given* both tires simultaneously lost $0.8\%$ circumference over weeks in storage leaving $\frac{C_{front}}{C_{rear}}$ unchanged,
• *When* riding on open road,
• *Then* the per-sensor GNSS estimators trigger the low-pressure pump reminder for both wheels.
• *Given* completed rides with wheel speed sensors linked to a bike,
• *When* gated circumference estimation converges,
• *Then* each wheel sensor's estimated circumference is persisted into `WheelCircumferenceHistory` and rendered as a historical time-series chart per bike.

**Invariants**: Single-wheel bikes retain 100% baseline behavior; hairpins produce 0.0% false puncture alerts; SQLite migration v4 $\rightarrow$ v5 preserves existing tables; 100% test pass rate.

### REQ-FIL-018: Epoch-Bounded Barometric Calibration & Non-Barometric Column Isolation [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-018`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `LiveWorkoutSession.java`, `TrackerService.java`
- **Rationale**: Prevent post-crash (`RESUME_SERVICE_RECREATION`) barometric calibration shifts from double-shifting already-calibrated pre-crash samples/streams, corrupting non-barometric elevation columns (DEM, GPS, Google Fused, ATT Fused), or causing a ~20x average altitude drop in `LiveWorkoutSession` Case B (`ATT-7876`, extending `REQ-CON-017`, `REQ-TRK-047.2`, and `REQ-TRK-057.3`).
- **Description**:
  1. *Strict Barometric Column Scope (`REQ-FIL-018.1`)*:
     - `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection()` MUST mutate exclusively barometric pressure altitude column(s) (`DeviceType.ALTITUDE_FROM_PRESSURE` names resolved from `DevicesDatabaseManager`, current `R.string.devices_altitude_from_pressure`, known 9-locale defaults, and legacy `ATT-5073` names) and canonical `ALTITUDE`.
     - It SHALL NEVER mutate `ALTITUDE (GPS)`, `ALTITUDE (Google Fused)`, `ALTITUDE (Topografische Höhe (DEM))`, `ALTITUDE (ATT Fused ...)`, or any other non-barometric `ALTITUDE (*)` / `ALTITUDE_*` column.
     - Canonical `ALTITUDE` SHALL ONLY be shifted on rows where barometric pressure was the active canonical source (`baroCol IS NOT NULL AND ABS(ALTITUDE - baroCol) < 0.01`), evaluated atomically prior to shifting `baroCol`.
  2. *Process-Epoch Lower Bound & Upper-Bound Case A Guard (`REQ-FIL-018.2`)*:
     - `TrackerService` SHALL record `mSessionEpochStartTimeMs` on `START_NORMAL`, `RESUME_SERVICE_RECREATION`, and process-recreation resumes (`mLiveSession == null`) and propagate it through `WorkoutRepository`, `WorkoutSamplesDatabaseManager`, and `LiveWorkoutSession`.
     - When Case A executes (`t0Ms <= 0 || t1Ms <= t0Ms`), `WorkoutSamplesDatabaseManager` SHALL restrict updates to `TIME >= floor(sessionEpochStartTimeMs / 1000)` (when `sessionEpochStartTimeMs > 0`) and `TIME <= t1Ms / 1000.0` (when `t1Ms > 0`), leaving pre-crash calibrated rows and post-calibration rows untouched.
     - `LiveWorkoutSession.rehydrateStreams()` SHALL align `sampledAltitudeTimestamps` (`0L` sentinels for pre-resume entries) and preserve pre-epoch `RunningStats` and `sampledAltitudes` across Case A post-crash calibration.
     - `TrackerService.mAltitudeCorrectionReceiver` SHALL only update `LiveWorkoutSession` and `WorkoutSummariesDatabaseManager` canonical altitude summary/streams when `DeviceType.ALTITUDE_FROM_PRESSURE` is the active canonical `ALTITUDE` source.
  3. *Dimensionally Consistent Case B Average Altitude (`REQ-FIL-018.3`)*:
     - In `LiveWorkoutSession.applyRetroactiveAltitudeCorrection()` Case B (`t0Ms > 0 && t1Ms > t0Ms`), `altStats.sum` MUST remain dimensionally consistent with 1-Hz `altStats.count` by adding `meanAppliedCorrection * altStats.count` rather than overwriting `altStats.sum` with the sum over 20-second `sampledAltitudes`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-018` extending `REQ-CON-017` (*Barometric Calibration Sanity Guard*), `REQ-TRK-047.2` (*Retroactive Drift Correction*), and `REQ-TRK-057.3` (*Sourced Column Sync*).
2. *Historical Origin & Commit Trace*: `ATT-4845` (`REQ-TRK-047`) and `ATT-6265` (`e079f7a9`, `REQ-TRK-057.3`) introduced `"ALTITUDE (*)"` wildcard discovery when barometer was the sole named altitude source.
3. *Root Reason for Existing Formulation*: Multi-source DEM (`ATT-4859`) and ATT Fused (`ATT-6279`) columns were added later; unbounded Case A across process restarts and 1-Hz vs 20-s stat cadences were unmodeled.
4. *Preservation of Core Invariants*: Preserves $|\Delta h| \le 500\text{ m}$ (`REQ-CON-017`), Case B piecewise-linear interpolation over `[t0, t1]` (`REQ-TRK-047.2`), barometric column sync (`REQ-TRK-057.3`), passive `onOpen()`, and `mDbExecutor` single-thread safety.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutSamples` containing `ALTITUDE (GPS)`, `ALTITUDE (Google Fused)`, `ALTITUDE (Topografische Höhe (DEM))`, and `ALTITUDE (Höhe aus Luftdruck)`,
• *When* `ALTITUDE_CORRECTION_INTENT` fires with `deltaOffset != 0`,
• *Then* GPS, Google Fused, and DEM columns remain unchanged, and canonical `ALTITUDE` shifts only on barometric-sourced rows.
• *Given* a workout with calibrated pre-crash samples resumed via `RESUME_SERVICE_RECREATION`,
• *When* the first post-restart barometric calibration fires (`prevCalTimeMs == -1L`),
• *Then* only uncalibrated post-restart barometric samples in `[restartTime, calibrationTime]` are shifted.
• *Given* `LiveWorkoutSession` with 1-Hz `ALTITUDE` samples around `445 m` and 20-second `sampledAltitudes`,
• *When* `applyRetroactiveAltitudeCorrection(offset, t0Ms, t1Ms)` executes Case B (`t0Ms > 0`),
• *Then* `altStats.getAverage()` remains around `445 m + meanCorrection` (never divided by ~20).

**Invariants**: Non-barometric columns 100% immutable; pre-crash calibrated rows untouched on `RESUME_SERVICE_RECREATION`; 1-Hz `altStats.count` and `sum` dimensionally consistent; passive DB `onOpen()`; 100% clean-room test pass rate.

### REQ-FIL-019: Post-Workout ERTS Smoother GNSS Fix Gating, Origin Anchoring & Output Sanity Guard [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-019`, `TST-FIL-025`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`
- **Rationale**: Prevent leading SQLite `NULL` coordinates from coercing to `0.0` ("Null Island" in Gulf of Guinea), anchoring the ENU reference origin to `(0.0, 0.0)`, and triggering catastrophic velocity shocks (~477 km/s) during post-workout Extended Rauch-Tung-Striebel (ERTS) smoothing that corrupt `WorkoutSamples.db`, `WorkoutSummaries.db` (10,196 km total distance), exports, period statistics, and route clusters (`ATT-8337`, extending `REQ-FIL-017`).
- **Description**:
  1. *Preservation of Nullable GNSS Coordinates (`REQ-FIL-019.1`)*:
     - `RawOdometrySample` SHALL support nullable `latitude: Double?` and `longitude: Double?` (defaulting to `null`).
     - `ErtsPostWorkoutRecomputer` SHALL preserve SQLite `NULL` coordinates when constructing `RawOdometrySample` objects and SHALL NEVER coerce `null` coordinates to `0.0`.
     - In `WorkoutSamplesDatabaseManager`, queries retrieving coordinates for post-workout smoothing or spatial calculations SHALL guard column reads with `cursor.isNull(idx)`.
  2. *First Valid GNSS Fix Reference Origin & Gated Measurement Ingestion (`REQ-FIL-019.2`)*:
     - `ExtendedRtsOdometrySmoother.smoothSamples()` SHALL select local tangent-plane reference coordinates `refLat` and `refLon` strictly from the **first valid GNSS sample** (`latitude != null && longitude != null && !latitude.isNaN() && !longitude.isNaN() && (abs(latitude) > 1e-6 || abs(longitude) > 1e-6)`).
     - If no sample in the workout contains a valid GNSS fix, `smoothSamples()` SHALL bypass odometry smoothing and return an empty list (preserving raw live coordinates).
     - In the forward EKF replay loop, `ekf.updateGnssPosition()` SHALL ONLY be invoked for samples with a valid GNSS fix; samples lacking a valid GNSS fix SHALL execute EKF prediction and wheel-speed / yaw-rate updates only without pulling the state toward `(0.0, 0.0)`.
  3. *Pre-Fix / Missing-Fix Column Integrity & Polyline Exclusion (`REQ-FIL-019.3`)*:
     - Leading and trailing workout rows lacking a valid GNSS fix SHALL NOT be populated with backward-smoothed coordinates or Null Island coordinates in `WorkoutSamples.db` (preserving SQLite `NULL` values).
     - `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()` SHALL exclude samples lacking a valid GNSS fix (`(0.0, 0.0)` or `null`) from `mapPolyline` generation (`PolyUtil.encode`), start/end location extrema in `WorkoutSummaries.db`, and bounds calculation.
  4. *Post-Workout ERTS Output Sanity Guard (`REQ-FIL-019.4`)*:
     - Before committing smoothed odometry or altitude streams to `WorkoutSamples.db` or `WorkoutSummaries.db`, `ErtsPostWorkoutRecomputer` SHALL execute a sanity validation on the smoothed results.
     - The sanity guard MUST verify that smoothed total distance does not exceed raw distance by an impossible factor (e.g. $|\text{smoothed} - \text{raw}| > 3\times$ or total distance $> 1,000\text{ km}$), maximum smoothed speed is physically plausible ($< 100\text{ m/s}$ for cycling/running), and coordinates are finite within geographic bounds ($\text{lat} \in [-90, 90], \text{lon} \in [-180, 180]$).
     - If the sanity check fails, the transaction SHALL be aborted, original live tracking data in SQLite preserved, and an error logged.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-019` extending `REQ-FIL-017` (*Post-Workout ERTS Zero-Phase Smoothing Engine*). Targets `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, and `WorkoutSamplesDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: `ATT-7750` (`d56b001a`) introduced `ExtendedRtsOdometrySmoother` assuming continuous GNSS fix availability and anchored `refLat`/`refLon` to `samples.first()`.
3. *Root Reason for Existing Formulation*: Workout initialization with indoor cold-starts or delayed GPS lock was not covered in the original unit test fixtures, which always passed complete synthetic circular/straight tracks starting with valid coordinates.
4. *Preservation of Core Invariants*: Preserves 5-state unicycle model $[E, N, v, \psi, b_{\omega_z}]^T$, backward RTS recursion equations, zero-phase error elimination on valid GNSS segments, device column isolation (`ALLOWED_CANONICAL_COLUMNS`), and opt-in Expert Settings gating (`KEY_POST_WORKOUT_ERTS_SMOOTHING`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout where the first $N$ samples ($N \ge 1$) have `LATITUDE = NULL, LONGITUDE = NULL` prior to the first valid GNSS fix,
• *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` / `ExtendedRtsOdometrySmoother.smoothSamples()` executes,
• *Then* `refLat`/`refLon` is anchored to the first valid GNSS fix, no `updateGnssPosition(0.0, 0.0)` is invoked, leading no-fix rows remain `NULL` in `WorkoutSamples.db`, and smoothed distance matches true ground track.
• *Given* a workout with mid-ride GNSS gaps (e.g. tunnel or forest canopy dropout),
• *When* `ExtendedRtsOdometrySmoother.smoothSamples()` executes,
• *Then* gap samples execute EKF prediction and wheel/yaw updates without pulling toward `(0.0, 0.0)` and backward RTS smoothly bridges the gap.
• *Given* smoothed odometry producing non-finite coordinates, impossible speeds ($> 100\text{ m/s}$), or total distance $> 1,000\text{ km}$,
• *When* `ErtsPostWorkoutRecomputer` validates the result,
• *Then* the SQLite update is aborted, original live data is preserved, and an error is logged.

**Invariants**: Zero Null Island $(0.0, 0.0)$ coordinates persisted to `WorkoutSamples.db` or encoded into `mapPolyline`; device-attributed columns 100% untouched; raw live data preserved if smoothing diverges; 100% test pass rate.

### REQ-FIL-020: MSL Normalization, Reference Precedence, and Elevation Hysteresis for ATT Fused Altitude [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-020`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AltitudeAndSlopeDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`
- **Rationale**: Prevent WGS84 ellipsoid vs. MSL geoid datum tug-of-war (~47.5 m offset in Central Europe) between GPS and DEM reference sources from shifting fused altitude +11 m and inducing Kalman slope/VAM oscillations (-28% .. +13%), eliminate 5-Hz zero-hysteresis noise rectification inflating ascent/descent by ~4.5x, and decouple database column persistence from localized display names (`ATT-8081`, extending `REQ-FIL-016`).
- **Description**:
  1. *Reference Altitude Precedence & Geoid MSL Normalization (`REQ-FIL-020.1`)*:
     - In `AltitudeAndSlopeDevice_ATTFused`, when fresh orthometric MSL elevation from `AltitudeFromPositionDevice` (DEM) is available, it SHALL take strict precedence over GPS elevation as the absolute reference anchor for the Kalman filter.
     - When GPS elevation is utilized as reference anchor (e.g. DEM unavailable or stale), `AltitudeAndSlopeDevice_ATTFused` SHALL normalize ellipsoidal WGS84 altitude to orthometric Mean Sea Level (MSL) altitude via `LocationCompat.getMslAltitudeMeters(location)` when `LocationCompat.hasMslAltitude(location)` is true, preventing datum step-shifts.
  2. *Anchor-Based Elevation Hysteresis Deadband (`REQ-FIL-020.2`)*:
     - `publishAltitudeAndMetrics()` SHALL NOT accumulate raw single-step delta differences at 5 Hz directly into ascent/descent accumulators.
     - It SHALL implement an anchor-based elevation hysteresis deadband ($1.5\text{ m} \le \text{threshold} \le 2.0\text{ m}$, default $2.0\text{ m}$): ascent SHALL only accumulate when smoothed altitude rises $\ge \text{threshold}$ above the lowest anchor, and descent SHALL only accumulate when smoothed altitude drops $\ge \text{threshold}$ below the highest anchor, rejecting barometric micro-ripples and sensor noise.
  3. *Locale-Invariant Technical DB Identifier (`REQ-FIL-020.3`)*:
     - `AltitudeAndSlopeDevice_ATTFused.getName()` SHALL return the canonical, locale-invariant technical identifier `att_fused_altitude_slope` for SQLite column and device table persistence.
     - User-facing UI screens, dialogs, and settings SHALL continue to display the localized name via `DevicesDatabaseManager` and resource string `R.string.devices_altitude_slope_ATT_fused` across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-020` extending `REQ-FIL-016` (*Coupled Kinematic Elevation & Slope Filter Virtual Device*). Targets `AltitudeAndSlopeDevice_ATTFused.kt` and `SpeedAndLocationDevice.java`.
2. *Historical Origin & Commit Trace*: `ATT-6279` (Sprint 2026-46.6) introduced `AltitudeAndSlopeDevice_ATTFused` verifying DEM and GPS reference modes in isolation with synthetic zero-geoid coordinates.
3. *Root Reason for Existing Formulation*: Testing separate reference sources in artificial planar environments did not expose real-world $N \approx +47.5\text{ m}$ datum tug-of-war when both sensors are active, nor the rectification of 5-Hz barometric sensor noise into phantom ascent.
4. *Preservation of Core Invariants*: Standstill clamping ($v < 0.3\text{ m/s}$), 2-State / 3-State EKF observability switching, column whitelist invariants, and localized UI display in `DevicesDatabaseManager` remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `AltitudeAndSlopeDevice_ATTFused` active with both DEM (MSL, 500.0 m) and GPS (WGS84, 547.5 m, $N = 47.5\text{ m}$) enabled,
• *When* reference altitude updates are processed,
• *Then* DEM takes precedence, no alternating ±48 m innovation kicks occur, and fused elevation converges to 500.0 m MSL without slope oscillations.
• *Given* stationary or flat riding with ±0.3 m barometric pressure noise at 5 Hz,
• *When* `publishAltitudeAndMetrics()` accumulates ascent and descent,
• *Then* 0.0 m phantom ascent and 0.0 m phantom descent are accumulated over 60 seconds.
• *Given* real terrain climbing with elevation rising from 500.0 m to 525.0 m,
• *When* climbing progression exceeds the 2.0 m hysteresis threshold,
• *Then* cumulative ascent accurately records 25.0 m climbing.
• *Given* a workout recorded in German, French, or Japanese locale,
• *When* `WorkoutSamples.db` records device columns for ATT Fused altitude and slope,
• *Then* the column names use the canonical technical identifier `att_fused_altitude_slope` consistently regardless of system locale.

**Invariants**: Standstill clamping produces 0.0 m drift during pauses; localized display names in `DevicesDatabaseManager` preserved across all 9 UI languages; zero regression in 2-State / 3-State EKF switching; 100% test pass rate.

### REQ-TRK-078: Mid-Workout Crash Recovery Accumulator Preservation and Baseline Seeding [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-078`
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-013`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackerService.java`, `WorkoutRepository.kt`, `BANALService.java`, `MyAccumulatorSensor.java`, `TrackerServiceResumeTest.kt`
- **Rationale**: Prevent mid-workout process recreation (`StartType.RESUME_SERVICE_RECREATION` and `StartType.RESUME_BY_USER`) from resetting session accumulators (`TIME_TOTAL`, `TIME_ACTIVE`, `DISTANCE_m`, `CALORIES`, `LAP_NR`, `ASCENT`, `DESCENT`, `TIME_LAP`, `DISTANCE_m_LAP`), which causes data loss in summaries, exports (`.fit`, `.tcx`, `.gpx`), and fold-back elevation/telemetry artifacts in Aftermath (`ATT-7875`, refining `REQ-TRK-026` and `REQ-TRK-031`).
- **Description**:
  1. *Omission of Reset Accumulators Intent on Resume (`REQ-TRK-078.1`)*:
     - `TrackerService.mBanalConnection.onServiceConnected()` SHALL NOT broadcast `BANALService.RESET_ACCUMULATORS_INTENT` when resuming tracking (`mResumeTrackingWhenConnectedToBanalService` or `mResumeFromPausedWhenConnectedToBanalService`).
     - `BANALService.RESET_ACCUMULATORS_INTENT` SHALL strictly be reserved for fresh workout starts (`StartType.START_NORMAL` in `TrainingApplication.startTracking()`).
  2. *Extended Resumed Workout State Rehydration (`REQ-TRK-078.2`)*:
     - `ResumedWorkoutState` SHALL encapsulate `ascent: Double`, `descent: Double`, `timeLap_s: Int`, and `distanceLap_m: Double` alongside `baseFileName`, `workoutId`, `timeTotal_s`, `timeActive_s`, `calories`, `lapNr`, and `distanceTotal_m`.
     - `WorkoutRepository.getResumedWorkoutState(workoutId)` SHALL query `ASCENDING` and `DESCENDING` from `WorkoutSummaries.db`.
     - For active lap metrics: when `lapNr > 1`, `timeLap_s = timeActive_s - sum(completedLapTime)` and `distanceLap_m = distanceTotal_m - sum(completedLapDistance)` derived from `LapsDatabaseManager.getLaps(workoutId)`; when `lapNr == 1`, `timeLap_s = timeActive_s` and `distanceLap_m = distanceTotal_m`.
  3. *Full Sensor Baseline Seeding on Resume (`REQ-TRK-078.3`)*:
     - In `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)`, `TrackerService` SHALL invoke `BANALService.setInitialSensorValue` for `SensorType.ASCENT`, `SensorType.DESCENT`, `SensorType.TIME_LAP`, and `SensorType.DISTANCE_m_LAP` in addition to `TIME_TOTAL`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, and `DISTANCE_m`.
  4. *Late-Binding Sensor Initial Value Inheritance (`REQ-TRK-078.4`)*:
     - `MyAccumulatorSensor` constructor SHALL query `mMySensorManager.getInitialValue(sensorType)` and seed `mInitialValue` when an initial value has been registered in `MySensorManager`, ensuring sensors connecting after service restart inherit the pre-crash baseline rather than defaulting to zero.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-TRK-026` (`TST-TRK-018`) and complements `REQ-TRK-031` (`ATT-3987`, *Resumed LiveWorkoutSession Rehydration*). Targets `TrackerService.java`, `WorkoutRepository.kt`, `BANALService.java`, `MyAccumulatorSensor.java`, and `TrackerServiceResumeTest.kt`.
2. *Historical Origin & Commit Trace*: `845818c2cfe0` (Aug 2019) introduced `RESET_ACCUMULATORS_INTENT` unconditionally in `onServiceConnected()`. `ATT-3980` (`f4650d74e8b4`) ordered table name initialization in `recreateValuesWhenResuming()` but retained the unconditional reset broadcast.
3. *Root Reason for Existing Formulation*: In legacy code, `TrackerService` lacked distinct state tracking between fresh workout initialization and sticky process recreation; the reset intent was broadcast to ensure sensors did not carry over stale state from previously stopped workouts in the same process.
4. *Preservation of Core Invariants*: Fresh workout start resets via `TrainingApplication.startTracking()` remain 100% untouched. Backward compatibility of `ResumedWorkoutState` constructors is preserved. The `mTableInitializationFuture` barrier (`REQ-TRK-026.3`) and single-thread DB execution contracts remain strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with persisted `timeTotal_s = 643`, `timeActive_s = 643`, `distanceTotal_m = 1098.8`, `ascent = 21.0`, `descent = 1.7`, `lapNr = 1`,
• *When* `TrackerService` restarts via `StartType.RESUME_SERVICE_RECREATION` and binds to `BANALService`,
• *Then* `RESET_ACCUMULATORS_INTENT` is not broadcast, all 9 accumulators are restored with pre-crash initial values, and subsequent samples proceed monotonically without resetting to 0.
• *Given* an active workout on `lapNr = 2` with lap 1 having `time = 300 s` and `distance = 500 m`,
• *When* `TrackerService` recovers from crash,
• *Then* `TIME_LAP` is initialized to `timeActive_s - 300` and `DISTANCE_m_LAP` is initialized to `distanceTotal_m - 500`.
• *Given* a new accumulator sensor instantiated after resume has executed,
• *When* constructed with a sensor type registered in `MySensorManager`,
• *Then* its initial value reflects the restored baseline rather than zero.

**Invariants**: Monotonic non-decreasing accumulator progression across service recreation boundaries; `START_NORMAL` fresh workout start behavior and resets remain strictly unchanged; 100% clean-room test pass rate.

### REQ-TRK-079: Application-Scoped DataStore Lifecycle and Offscreen Cockpit Map Disposal [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-079`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `TuningPreferencesDataStore.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`
- **Rationale**: Prevent fatal 512 MB OutOfMemoryError heap exhaustion, native JNI map crashes, GC stalls, and background CPU usage process kills during active workouts by enforcing application-scoped singleton DataStore lifecycle, bounding HorizontalPager offscreen composition, and gating live UI map telemetry on `Lifecycle.State.STARTED` (ATT-7874, extending `REQ-SET-073` and `REQ-MAP-051`).
- **Description**:
  1. *Application-Scoped TuningPreferencesDataStore Singleton Lifecycle (`REQ-TRK-079.1`)*:
     - `TuningPreferencesDataStore` primary constructor SHALL be scoped to internal / `@VisibleForTesting`, and production callers SHALL exclusively obtain instances via `TuningPreferencesDataStore.getInstance(context)`.
     - `getInstance(context)` SHALL normalize any supplied `Context` to `context.applicationContext`, preventing retention of `Activity`, `View`, or Compose hierarchies.
     - `TuningPreferencesDataStore` internal startup maintenance (such as legacy map camera preference purging) SHALL run within `appScope` rather than spawning independent unmanaged `CoroutineScope(Dispatchers.IO)` workers.
     - All 19 existing production call sites SHALL be migrated to `TuningPreferencesDataStore.getInstance(context.applicationContext)`.
  2. *Bounded Viewport Composition & Inactive Tab Map Disposal (`REQ-TRK-079.2`)*:
     - `TrackingTabsScreen` `HorizontalPager` SHALL NOT set `beyondViewportPageCount = trackingViews.size + 1`. It SHALL set `beyondViewportPageCount = 0` (or default bounded window), ensuring offscreen Cockpit tabs are not actively composed.
     - When a Cockpit tab containing `TrackingMapContainer` is not the active visible page (`!isTabActive`), the underlying `GoogleMap` composable and view hierarchy SHALL be disposed, freeing native map renderer resources and bitmap textures.
  3. *Lifecycle-Gated Cockpit Telemetry Collection (`REQ-TRK-079.3`)*:
     - In `TrackingViewModel`, live 1-Hz map telemetry and polyline state pipelines (`observeMapState`) and Compose collectors (`collectAsStateWithLifecycle`) SHALL gate emission on `Lifecycle.State.STARTED`.
     - When the athlete turns off the screen or moves the app to the background (`STOPPED`), UI map and polyline emissions SHALL pause completely, while `TrackerService` 1-Hz GPS tracking and SQLite database recording continue without interruption.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-079`, extending `REQ-SET-073` (*Advanced Tuning Preferences*) and `REQ-MAP-051` (*Map Segment Layer Isolation*). Targets `TuningPreferencesDataStore.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`, and `ATrainingTrackerMap.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2945` (`commit f3ff8577`) introduced `beyondViewportPageCount = trackingViews.size + 1` in `TrackingTabsScreen` to eliminate tab switching latency when athletes swipe between Cockpit tabs during tracking.
3. *Root Reason for Existing Formulation*: At the time, Cockpit tabs only hosted lightweight View/Compose text metric tiles; multi-tab Google Map composition and 1-Hz `observeMapState` coroutine pipelines were not present.
4. *Preservation of Core Invariants*: Tab swiping responsiveness for sensor metric grids is preserved while heavy native `GoogleMap` composition is deferred on inactive tabs; `TrackerService` 1-Hz background GPS tracking and SQLite database recording remain 100% independent and unaffected.

**Acceptance Criteria (Given-When-Then)**:
• *Given* repeated navigation across Cockpit tabs, Aftermath screens, Elevation Profiles, and Settings,
• *When* `TuningPreferencesDataStore` is queried,
• *Then* exactly one application-scoped instance exists and zero `Activity` contexts or orphaned coroutine scopes are retained.
• *Given* an active workout with multiple Cockpit tabs including map views,
• *When* the athlete is viewing a non-map tab or the device screen is turned off (`STOPPED`),
• *Then* offscreen `GoogleMap` composables are disposed and `TrackingViewModel.observeMapState()` pauses live polyline/marker emissions.
• *Given* an active workout with screen turned off,
• *When* `TrackerService` runs in the foreground service,
• *Then* 1-Hz workout sample recording to `WorkoutSamples.db` continues uninterrupted with zero data loss.

**Invariants**: `TrackerService` 1-Hz background recording 100% unaffected; immediate persistence of tuning preferences preserved; zero leak of `Activity` references; 100% clean-room test pass rate.

### REQ-TRK-080: Mid-Workout Process Restart Cockpit Telemetry, Live Map Track & Sparkline Rehydration [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-080`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BANALServiceRepository.kt`, `LiveSparklineBuffer.kt`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`
- **Rationale**: When `aTrainingTracker` recovers from background process termination (`StartType.RESUME_SERVICE_RECREATION`) or when UI reconnects mid-workout, accumulator recovery (REQ-TRK-078) restores service scalar state, but `BANALServiceRepository` initializes empty `_currentTrack`, `_currentPathPoints`, and `sparklineBuffers`. Rehydrating telemetry asynchronously from `WorkoutSamples.db` ensures athletes immediately see their full pre-crash GPS route, elevation profile, and sparkline trends without UI stutter or data resets (ATT-7877, extending REQ-TRK-059 and REQ-TRK-042).
- **Description**:
  1. *Asynchronous Rehydration Dispatch & State Gating (`REQ-TRK-080.1`)*:
     - When `BANALServiceRepository` observes `TrackingMode.TRACKING` or `TrackingMode.PAUSED` for an active workout session with a valid `baseFileName` / `workoutId` (via `WorkoutRepository.getResumedWorkoutState()` or `TrainingApplication.getActiveWorkoutID()`) while `_currentTrack.value.isEmpty()`:
     - `BANALServiceRepository` SHALL dispatch an asynchronous rehydration task on `Dispatchers.IO` to load persisted coordinates and numeric sensor histories from `WorkoutSamples.db`.
     - Rehydration SHALL NOT execute during fresh workout starts (`StartType.START_NORMAL`), preserving clean initial states.
     - Telemetry emission to the UI layer SHALL preserve `Lifecycle.State.STARTED` gating per `REQ-TRK-079.3`.
  2. *Live Map Track & PathPoint Rehydration (`REQ-TRK-080.2`)*:
     - Persisted GPS samples (`LATITUDE`, `LONGITUDE`, `DISTANCE_m`, and `ALTITUDE`) ordered chronologically by `TIME` SHALL be retrieved from `WorkoutSamples.db` and converted into `List<LatLng>` and `List<PathPoint>`.
     - `_currentTrack` and `_currentPathPoints` SHALL be atomically populated using `SnapshotChunkedList.from()`, preserving chunked memory bounds and immutability.
     - Any live GPS location updates received concurrently while the asynchronous query is executing SHALL be merged monotonically at the tail, preventing dropped coordinates or out-of-order vertices.
  3. *LiveSparklineBuffer Horizon Historical Backfill (`REQ-TRK-080.3`)*:
     - For registered `sparklineBuffers`, `LiveSparklineBuffer` SHALL support `backfillHistoricalSamples(samples: List<Float>)` protected by thread-safe synchronization (`synchronized(lock)`).
     - Query results for the corresponding numeric sensor column (`HR`, `SPEED_mps`, `POWER_W`, `CADENCE`, `ALTITUDE`) across the past `horizonSeconds` window SHALL populate the buffer up to its configured `maxCapacity`.
     - Live samples appended via `updateSparklineBuffers()` concurrently with the asynchronous fetch SHALL be preserved monotonically without buffer overrun or corruption.
  4. *Localization Neutrality & System Invariants (`REQ-TRK-080.4`)*:
     - Rehydration operates exclusively within the data and repository layer; zero new UI string resources or layout changes are required across all 9 supported locales.
     - Query execution MUST NOT block the Android main thread. Total in-memory allocations MUST remain bounded via `SnapshotChunkedList` and `LiveSparklineBuffer` capacity limits.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-080`, extending `REQ-TRK-059` (*Live Sparklines*), `REQ-TRK-042` (*Snapshot Chunked Track*), and `REQ-TRK-078` (*Crash Recovery Accumulators*). Targets `BANALServiceRepository.kt`, `LiveSparklineBuffer.kt`, and `WorkoutSamplesDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: `ATT-6710` introduced in-memory `LiveSparklineBuffer` for 1-Hz streaming; `REQ-TRK-042` introduced `SnapshotChunkedList` for live breadcrumbs. Both assumed a continuous lifecycle from workout start to stop.
3. *Root Reason for Existing Formulation*: Buffers were created empty at component construction because tracking was originally assumed to run within an uninterrupted process lifecycle.
4. *Preservation of Core Invariants*: Streaming 1-Hz updates, `SnapshotChunkedList` memory bounding, `ATT-7874` offscreen map disposal, and `ATT-7875` accumulator preservation remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with 15 minutes of persisted 1-Hz telemetry in `WorkoutSamples.db` and configured Cockpit sparkline fields,
• *When* the app recovers from background process termination (`RESUME_SERVICE_RECREATION`) or UI reconnection,
• *Then* `_currentTrack`, `_currentPathPoints`, and registered `sparklineBuffers` backfill from `WorkoutSamples.db` on `Dispatchers.IO`, restoring the map route and 15-minute telemetry trend immediately.
• *Given* an ongoing rehydration task on `Dispatchers.IO`,
• *When* new 1-Hz GPS or sensor ticks arrive from `BANALService`,
• *Then* incoming samples are merged monotonically after the historical baseline with zero dropped ticks and zero race conditions.
• *Given* a new workout initialized via `StartType.START_NORMAL`,
• *When* `BANALServiceRepository` starts,
• *Then* rehydration is skipped and track collections remain empty.

**Invariants**: Zero main-thread blocking (`Dispatchers.IO`); strictly bounded memory footprint via `SnapshotChunkedList` and `LiveSparklineBuffer`; monotonic timestamp ordering; clean-room test pass rate.

### REQ-TRK-081: Per-Tile Cockpit Sparkline Graph Color Persistence and Minimalist Color Editor UI [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-081`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, `TelemetryGraphColorPreferences.kt`
- **Rationale**: Cockpit sparkline tiles allow configurable time horizons, but lack persistent graph colors and default to theme or zone colors. Providing per-tile color override options from the centralized 8-color telemetry palette, Theme Default, and Dynamic Zone (for HR, Power, and HRV DFA α1), paired with a compact 2-segment scope toggle ("This tile" vs "All <Metric>") and SQLite schema v22 persistence, enables customized live data differentiation while preserving global preference coherence and legacy backup backward compatibility (ATT-7760, Parent Epic ATT-5908, refining `REQ-TRK-059` and `REQ-UI-530`).
- **Description**:
  1. *Database Schema v22 Persistence & Migration (`REQ-TRK-081.1`)*:
     - SQLite `DB_VERSION` in `TrackingViewsDatabaseManager.java` SHALL increment to 22.
     - `ROWS_TABLE` SHALL add column `GraphColor text DEFAULT 'GLOBAL_DEFAULT'`.
     - `onUpgrade` for version 22 SHALL execute `ALTER TABLE LayoutRowsTable ADD COLUMN GraphColor text DEFAULT 'GLOBAL_DEFAULT'`, preserving all existing cockpit tile layouts without data loss.
     - Backup/restore (.attbackup JSON serialization and deserialization) SHALL include `graphColor`. Missing or null `graphColor` keys during import SHALL default safely to `"GLOBAL_DEFAULT"`.
  2. *Color Mode Model & Repository/State Threading (`REQ-TRK-081.2`)*:
     - The system SHALL support the following graph color tokens: `"GLOBAL_DEFAULT"`, `"THEME_DEFAULT"`, `"DYNAMIC_ZONE"`, and the 8 fixed telemetry palette color tokens (`"CRIMSON"`, `"VIOLET"`, `"EMERALD"`, `"AMBER"`, `"TEAL"`, `"MAGENTA"`, `"CYAN"`, `"CORAL"`).
     - `SensorFieldConfig` and `SensorFieldState` SHALL include `val graphColor: String = "GLOBAL_DEFAULT"`.
     - `TrackingViewsRepository` SHALL query and persist `graphColor` across insert, update, and fetch operations.
     - In `SensorFieldView.kt`, live sparkline stroke color SHALL resolve deterministically:
       • `"DYNAMIC_ZONE"`: resolves to `fieldState.zoneColor` when zone color is active (`zoneColor != Color.Transparent`), falling back to `MaterialTheme.colorScheme.primary`.
       • `"THEME_DEFAULT"`: resolves to `MaterialTheme.colorScheme.primary`.
       • `"GLOBAL_DEFAULT"`: resolves via `TelemetryGraphColorPreferences.getGraphColor(context, sensorType).resolveColor(colorScheme, isDarkTheme)`.
       • Explicit palette name: resolves via `TelemetryGraphColor.valueOf(graphColor).resolveColor(colorScheme, isDarkTheme)`.
  3. *Minimalist Color Editor UI & Scope Toggle (`REQ-TRK-081.3`)*:
     - When `isGraphSupported` and `showGraph` are true in `EditSensorFieldDialog.kt`, a minimalist color editor section SHALL be rendered directly below the time-horizon slider.
     - The editor SHALL display a compact row containing circular swatches for the 8 fixed palette colors, a chip for `Theme Default`, and a chip for `Dynamic Zone` (gated strictly to `SensorType.HR`, `SensorType.POWER` family, and `SensorType.HRV_DFA_A1`).
     - Below the swatches, a compact 2-segment scope toggle SHALL offer:
       • `This tile`: applies color selection strictly to the current tile's local `graphColor`.
       • `All <Metric>`: applies color selection globally to `TelemetryGraphColorPreferences.setGraphColor(context, sensorType, color)` and sets the current tile's `graphColor` to `"GLOBAL_DEFAULT"`.
  4. *9-Language Localization Parity (`REQ-TRK-081.4`)*:
     - All user-facing strings for the scope toggle (`cockpit_graph_color_this_tile`, `cockpit_graph_color_all_metric`), chips (`cockpit_graph_color_theme_default`, `cockpit_graph_color_dynamic_zone`), and section header (`cockpit_graph_color_title`) SHALL maintain 100% translation parity across all 9 supported locales: `values/` (EN), `values-de/` (DE), `values-es/` (ES), `values-fr/` (FR), `values-it/` (IT), `values-ja/` (JA), `values-nl/` (NL), `values-pl/` (PL), and `values-pt/` (PT), verified by `TranslationParityTest`.
  5. *Visual Compose Screenshot Verification (`REQ-TRK-081.5`)*:
     - Per Rule 8.5, visual self-inspection SHALL be conducted via a Robolectric Compose screenshot test generating `docs/engineering/walkthroughs/img/ATT-7760_color_picker.png` verifying the 4-point visual checklist (orientation & geometry, color mapping & M3 artifacts, typography & wrapping, touch ergonomics).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-081`, refining `REQ-TRK-059` (*Configurable Live Sparkline Graph Cockpit Tiles*) and integrating with `REQ-UI-530` (*Centralized Telemetry Color Palette*). Targets `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, and `TelemetryGraphColorPreferences.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.1 via `ATT-1294` added `ShowGraph` and `GraphHorizonS` to SQLite v19. `SensorFieldView.kt` lines 461-465 hardcoded sparkline colors to `zoneColor` or `MaterialTheme.colorScheme.primary`. Sprint 2026-46.7 via `ATT-7759` introduced `TelemetryGraphColorPreferences` and the 8-color palette.
3. *Root Reason for Existing Formulation*: During initial sparkline MVP (`ATT-1294`), color selection was omitted to minimize dialog complexity and database migrations.
4. *Preservation of Core Invariants*: Existing database rows and backups default to `GLOBAL_DEFAULT`, preserving current visual behavior without data loss or layout resets; non-graph tiles and unsupported sensor types remain completely unaffected; 100% 9-language translation parity preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete edits a Power tile with `showGraph` enabled and scope set to `This tile`,
• *When* they select Cyan and save,
• *Then* only that tile persists `GRAPH_COLOR = CYAN`, while other Power tiles and Workout Detail charts remain unchanged.
• *Given* an athlete edits a Heart Rate tile and switches scope to `All HR`,
• *When* they select Violet and save,
• *Then* `TelemetryGraphColorPreferences` for Heart Rate is updated to Violet, updating Workout Detail and all Heart Rate tiles set to `GLOBAL_DEFAULT`.
• *Given* `EditSensorFieldDialog` is open with `showGraph = true`,
• *When* the selected sensor is `HR`, `POWER`, or `HRV_DFA_A1`,
• *Then* the `Dynamic Zone` chip is visible; for other sensors (Speed, Cadence, Altitude), `Dynamic Zone` is hidden.
• *Given* an existing database (v21) or legacy `.attbackup` without `GRAPH_COLOR`,
• *When* upgraded to v22 or restored,
• *Then* all existing graph tiles default cleanly to `GLOBAL_DEFAULT` without crashing or resetting layouts.

**Invariants**: SQLite schema backward compatibility guaranteed; legacy backup JSON imports default cleanly; zero Compose snapshot state writes during composition; 100% 9-language localization parity; clean-room test pass rate.

### REQ-TRK-082: Decoupled Multi-Color Dynamic Zone Sparkline Rendering for HR, Power, and HRV DFA α1 [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-082`
- **System Requirement(s)**: `SYSREQ-TRK-006`, `SYSREQ-TRK-012`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorSparklineView.kt`, `SensorFieldView.kt`, `SensorFieldState.kt`, `TrackingViewModel.kt`, `EditSensorFieldDialog.kt`
- **Rationale**: In `ATT-7760` (`REQ-TRK-081.2`), `resolveSparklineLineColor` retained a legacy `showTextColor` fallback when `sensorType == null`, callers (`SensorFieldView.kt`, `EditSensorFieldDialog.kt`) still passed `showTextColor`, and `"DYNAMIC_ZONE"` painted the entire sparkline with a single instantaneous `fieldState.zoneColor`. Fully decoupling sparkline color resolution from `zoneDisplayOptions.showTextColor` and mapping athlete zone thresholds onto `Brush.verticalGradient` stops lets athletes keep crisp high-contrast numbers while inspecting historical multi-zone transitions across HR, Power, and HRV DFA α1 sparklines with zero extra `List<Float>` ring-buffer allocations (ATT-7761, ATT-8436, Parent Epic ATT-5908, refining `REQ-TRK-081` and `REQ-TRK-059`).
- **Description**:
  1. *Complete Decoupling from `showTextColor` (`REQ-TRK-082.1`)*:
     - `resolveSparklineLineColor` in `SensorSparklineView.kt` SHALL NOT read, accept, or depend on `zoneDisplayOptions.showTextColor` across any color resolution branch (including `"GLOBAL_DEFAULT"` when `sensorType == null`, `"THEME_DEFAULT"`, `"DYNAMIC_ZONE"`, and explicit palette tokens); callers in `SensorFieldView.kt` and `EditSensorFieldDialog.kt` SHALL NOT pass `showTextColor` to sparkline color resolution.
     - Numeric tile text coloring in `SensorFieldView.kt` SHALL remain governed exclusively by `fieldState.zoneDisplayOptions.showTextColor`, independent of `fieldState.graphColor`.
  2. *Multi-Color Historical `Brush.verticalGradient` Zone Curve & Terminal Dot (`REQ-TRK-082.2`)*:
     - `SensorFieldState` SHALL carry an optional sparkline zone threshold specification derived from `TrackingViewModel.zoneCutoffsMap` / HRV parameters without modifying `List<Float>` sample ring buffers or allocating per-sample colors during 1 Hz updates.
     - When `graphColor == "DYNAMIC_ZONE"` and valid zone threshold stops are available, `SensorSparklineView.kt` SHALL render both the stroke path and subtle gradient fill via `Brush.verticalGradient` using monotonically non-decreasing normalized color stops in `[0f, 1f]` mapped onto `[minVal, maxVal]` within `[verticalPaddingPx, height - verticalPaddingPx]` (`maxVal -> 0f` top, `minVal -> 1f` bottom for standard metrics), and SHALL render the terminal dot in the zone color corresponding to the latest sample (`samples.last()`).
     - `EditSensorFieldDialog.kt` SHALL provide representative/configured zone stops to its `SensorSparklineView` preview whenever `uiState.graphColor == "DYNAMIC_ZONE"` for a supported metric.
     - Zone threshold mappings MUST cover:
       • **Heart Rate (`SensorType.HR`)**: 4 cutoffs (`HR_RUN` / `HR_BIKE`) mapping ascending values across `TTColor.Zone1`..`TTColor.Zone5`.
       • **Power (`SensorType.POWER` family, excluding `POWER_TO_WEIGHT`)**: 6 cutoffs (`PWR_BIKE`) mapping ascending values across `TTColor.Zone1`..`TTColor.Zone7`. `SensorType.POWER_TO_WEIGHT` (W/kg) MUST be guarded against raw Watt cutoffs and MUST NOT receive `PWR_BIKE` gradient stops.
       • **HRV DFA α1 (`SensorType.HRV_DFA_A1`)**: inverted polarity and inverted Y-axis where higher aerobic values (`alpha1 > t1`) map to `TTColor.Zone2` (green, bottom / `stop = 1f`) and lower anaerobic values (`alpha1 < t2`) map to `TTColor.Zone4` (red, top / `stop = 0f`), with `t2 <= alpha1 <= t1` mapping to `TTColor.Zone3` (orange, middle); `SensorSparklineView.kt` SHALL invert the canvas Y-axis coordinate mapping (`currentY = verticalPaddingPx + ((value - minVal) / range) * usableHeight`) whenever `sensorType == SensorType.HRV_DFA_A1` or `thresholds.invertedPolarity == true` across both dynamic-zone and fixed-color modes, ensuring that the sparkline curve and fill rise upward into red under strain and fall downward into green during recovery (`ATT-8436`); zone coloring MUST be suppressed (falling back to solid `lineColor`) when `hrvSignalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC` (consistent with `HrvZoneIndicator`).
  3. *Dark/AMOLED Zone 7 Contrast & Graceful Edge-Case Fallbacks (`REQ-TRK-082.3`)*:
     - When `TTColor.Zone7` (`#000000`) appears on a dark or AMOLED surface (`TelemetryGraphColor.isDarkSurface(colorScheme) == true`), `SensorSparklineView.kt` MUST substitute a high-contrast dark-surface zone color (e.g., `colorScheme.onSurface`) so Zone 7 segments and terminal dots remain legible.
     - `SensorSparklineView.kt` MUST fall back gracefully to solid `lineColor` (zero divide-by-zero or invalid gradient stop errors) whenever `samples.size == 1`, `range == 0f` (`minVal == maxVal`), `isPlaceholder == true`, or zone threshold stops are null/empty.
  4. *Visual Compose Screenshot Verification (`REQ-TRK-082.4`, Rule 8.5)*:
     - A Robolectric Compose screenshot test SHALL capture `docs/engineering/walkthroughs/img/ATT-7761_dynamic_zone_sparkline.png` verifying Light and Dark/AMOLED rendering across HR (5-zone), Power (7-zone including Zone 7 dark contrast), HRV DFA α1 (inverted polarity with aerobic green at bottom and anaerobic red at top), decoupled fixed-color + `showTextColor = true`, and placeholder/flat-line fallbacks against the 4-point visual checklist.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-TRK-081` (`REQ-TRK-081.2`) and `REQ-TRK-059` in `docs/requirements/req_tracking_cockpit.md` (`5854-5908`). Targets `SensorSparklineView.kt`, `SensorFieldView.kt`, `SensorFieldState.kt`, `TrackingViewModel.kt`, and `EditSensorFieldDialog.kt`.
2. *Historical Origin & Commit Trace*: `ATT-7760` (`65d2c2d5`, Sprint 2026-46.8) introduced `SensorFieldState.graphColor` and `resolveSparklineLineColor`, retaining a `showTextColor` fallback (`SensorSparklineView.kt:332`) when `sensorType == null` and resolving `"DYNAMIC_ZONE"` to a single `fieldState.zoneColor`.
3. *Root Reason for Existing Formulation*: `ATT-7760` scoped SQLite v22 schema persistence, global/per-tile preferences, and the color picker UI, intentionally deferring multi-stop threshold-mapped gradient rendering and complete `showTextColor` decoupling to `ATT-7761`.
4. *Preservation of Core Invariants*: Fixed palette tokens, `THEME_DEFAULT`, `GLOBAL_DEFAULT`, SQLite v22 schema, `List<Float>` sample ring buffers, and `showTextColor` numeric text coloring remain intact; `"DYNAMIC_ZONE"` upgrades cleanly to threshold-aligned vertical gradients with solid fallback when thresholds or sample variance are absent.
5. *DFA Alpha 1 Y-Axis Inversion Refinement (`ATT-8436`)*: Ceremony 2 Sprint Review follow-up in Sprint `2026-11-16_02_Spät`. Inverts the spatial Y-axis and gradient stop orientation for DFA alpha 1 so that aerobic/green values sit at the bottom and anaerobic/red values sit at the top, ensuring intuitive visual strain alignment with HR and Power sparklines.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an HR tile with `showTextColor = true` and `graphColor` set to a fixed palette color (e.g., `CRIMSON` or `VIOLET`) or `GLOBAL_DEFAULT` (even when `sensorType == null`),
• *When* the athlete enters Zone 4,
• *Then* the numeric value tints to Zone 4 color while the sparkline stroke, fill, and terminal dot remain in the configured sparkline color without reading `showTextColor`.
• *Given* an HR (4 cutoffs -> Z1..5), Power (6 cutoffs -> Z1..7, excluding `POWER_TO_WEIGHT`), or HRV DFA α1 (`t1`, `t2` inverted polarity; not `SMOOTHED_STREAM`/`QUANTIZED_SYNTHETIC`) tile with `graphColor = "DYNAMIC_ZONE"` and `showTextColor = false`,
• *When* sparkline samples span multiple zones,
• *Then* the stroke and fill render historical multi-colored `Brush.verticalGradient` segments aligned with the athlete's thresholds, the terminal dot matches the latest sample's zone (with dark/AMOLED contrast fallback for `Zone7`), and the numeric value remains standard `onSurface`.
• *Given* a `DYNAMIC_ZONE` sparkline with `isPlaceholder = true`, `samples.size == 1`, `minVal == maxVal`, `POWER_TO_WEIGHT`, or `hrvSignalQualityStatus` in `{SMOOTHED_STREAM, QUANTIZED_SYNTHETIC}`,
• *When* rendered in `SensorSparklineView`,
• *Then* it renders cleanly without divide-by-zero or invalid gradient stops, falling back to placeholder dashed styling or solid `lineColor`.

**Invariants**: Zero extra per-sample color allocations in `List<Float>` ring buffers; zero SQLite v22 schema changes; monotonically non-decreasing `Brush.verticalGradient` stops in `[0f, 1f]`; zero Compose snapshot state writes during composition; clean-room test pass rate and Rule 8.5 PNG inspection.

### REQ-TRK-083: Canonical Internal Storage, Display & Export Unit Specification for SensorType [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-083`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`, `docs/architecture/sensor_units.md`, `docs/architecture.md`, `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`
- **Rationale**: Only 10 of 67 `SensorType` constants encode physical units in their enum name, while `SensorType.unitId` points to UI display resources (`R.string.units_*`) that diverge from internal SQLite/sensor-bus storage units after formatter scaling or `MyHelper.getUnitsId()` remapping. This ambiguity caused a 3600x vertical speed unit mismatch (`ATT-8577`, `ErtsPostWorkoutRecomputer` writing `m/s` into `m/h` `VERTICAL_SPEED`). Establishing a canonical 67-entry data dictionary in `docs/architecture/sensor_units.md`, structured `internalUnit` metadata and per-constant Javadoc in `SensorType.java`, and an automated bidirectional contract test eliminates storage/display/export unit ambiguity without altering runtime behavior or SQLite schemas (`ATT-8579`, Epic `ATT-431`, complementing `REQ-TRK-070` and `REQ-PRO-043`).
- **Description**:
  1. *Canonical Sensor Unit Data Dictionary (`REQ-TRK-083.1`)*:
     - `docs/architecture/sensor_units.md` (linked from `docs/architecture.md`) SHALL define a complete data-dictionary table covering all **67** `SensorType` enum constants, specifying per constant:
       • Enum `name()`, `SensorCategory`, and `SensorSubCategory` (`null` if direct core metric).
       • Internal SQLite/sensor-bus storage unit (exact SI/physical unit, e.g., `m/s`, `m/h`, `m`, `s`, `ms`, `cm`, `rad/s`, `%`, `W`, `kJ`, `bpm`, `deg`, `kcal`, `Nm`, `W/kg`, `degC`, or `dimensionless`).
       • `SensorValueType` (`INTEGER`, `DOUBLE`, `STRING`), valid physical range, and sentinel conventions (including `BATTERY_REMAINING_TIME`: `-1` charging, `<= -2` stabilizing; `PACE_spm`: `> BANALService.MAX_PACE` -> `"~~"`; `HRV_DFA_A1`, `POWER_TO_WEIGHT`, `W_PRIME_BAL`: `< 0`, `NaN`, `Infinite` -> `"--.--"` / `"--"`).
       • UI formatter (`MyFormatter` class) and Metric/Imperial display unit conversion via `MyHelper.getUnitsId()`.
       • File export units across FIT (`FitFileWriter.kt`), TCX (`TCXFileWriter.kt`), GPX (`GPXFileWriter.java`), CSV (`CSVFileWriter.java`), and Golden Cheetah (`GCFileWriter.java`).
     - Authentic existing discrepancies (`W_PRIME_BAL` emitting `kJ` while formatted with `PowerToWeightFormatter` `"W/kg"`; `MyHelper.getUnitsId()` omitting radar distance/speed types and falling back to `units_distance_basic`/`units_speed_basic`) SHALL be documented explicitly without altering runtime formatter/helper/exporter behavior.
  2. *Code-Level `internalUnit` Metadata & Per-Constant Javadoc (`REQ-TRK-083.2`)*:
     - `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java` SHALL declare a non-null `private final String internalUnit` field and `public String getInternalUnit()` accessor on every `SensorType` constant, together with per-constant Javadoc documenting internal storage unit, valid range/sentinel behavior, and UI display/export conversion.
     - Enum declaration order (`Parcelable` `ordinal()`), enum `name()` identifiers (`WorkoutSamples.db` column names), `@Deprecated getUnitId()`, and all runtime formatter, exporter, and device calculation behaviors MUST remain unchanged (`N/A - no UI strings`).
  3. *Automated Bidirectional Parity Contract (`REQ-TRK-083.3`)*:
     - `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt` SHALL execute on the JVM (`./gradlew testDebugUnitTest`) and assert:
       • 100% coverage of `SensorType.values()` (every constant has a non-blank `getInternalUnit()` and a matching per-constant Javadoc block in `SensorType.java`).
       • 100% bidirectional set parity (`codeConstants == docConstants`) and metadata parity (`SensorCategory`, `SensorValueType`, `internalUnit`) between `SensorType.values()` and `docs/architecture/sensor_units.md`, plus cross-link verification in `docs/architecture.md`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* all 67 enum constants in `SensorType.values()`,
• *When* `SensorTypeUnitsContractTest` inspects `SensorType.getInternalUnit()`, `SensorType.java` source Javadoc, `docs/architecture/sensor_units.md`, and `docs/architecture.md`,
• *Then* 100% of constants have non-blank `internalUnit`, per-constant Javadoc, and matching dictionary rows in `docs/architecture/sensor_units.md` with zero missing or orphaned entries.
• *Given* an existing `SensorType` consumer (`Parcelable` `CREATOR`, `WorkoutSamplesDatabaseManager`, `MyFormatter`, `MyHelper`, or `FitFileWriter`),
• *When* `SensorType` metadata and documentation are added,
• *Then* `ordinal()`, `name()`, `getUnitId()`, SQLite column names, and runtime formatting/export outputs remain identical.

**Invariants**: `SensorType` `ordinal()` and `name()` unchanged; `WorkoutSamples.db` schema unchanged; `@Deprecated getUnitId()` contract preserved; zero UI strings added (`N/A - no UI strings`); zero runtime formatter/exporter/device behavioral changes; `./gradlew testDebugUnitTest` passes cleanly.

### REQ-TRK-084: Valid-Data `AccumulatedSensors` Persistence Filtering, Resume Rehydration & Post-Workout Backfill Upsert [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-084`
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-006`, `SYSREQ-TRK-011`, `SYSREQ-DEX-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackerService.java`, `LiveWorkoutSession.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `HrvPostWorkoutRecomputer.kt`
- **Rationale**: `MySensorManager.registerSensor()` unconditionally inserts every registered `SensorType` into `mAccumulatedSensorTypeSet` on first registration, while `BANALService.onCreate()` unconditionally constructs `CyclingPowerMetricsDevice` (15 sensors), `HRVMetricsDevice` (3 sensors), and `VerticalSpeedAndSlopeDevice` (4 sensors) alongside `ClockDevice`, `BatteryDevice`, and location/altitude devices (40 total). Because `TrackerService.endWorkout()` persisted `getAccumulatedSensorTypeSet()` verbatim into `AccumulatedSensors`, workouts recorded without HR, Power, or Cadence hardware (`GCData = "TDS----AG--"`) falsely persisted ~40 unattached virtual/derived sensor rows. Filtering `AccumulatedSensors` persistence to sensor types that actually produced valid non-null telemetry—while preserving `MySensorManager.getAccumulatedSensorTypeSet()` (`REQ-TRK-030.2`), `BanalFinalizationSnapshot` (`REQ-TRK-035`), resume continuity, and post-workout Polar/HRV backfill—eliminates false-positive sensor rows (`ATT-8578`).
- **Description**:
  1. *Live Valid-Sample Observation & Virtual-Device Silence (`REQ-TRK-084.1`)*:
     - During 1 Hz sampling (`TrackerService.sampleAndWriteToDb()`), `LiveWorkoutSession` SHALL record a `SensorType` into its `observedSensorTypes` set strictly when `sensorData != null && sensorData.getValue() != null` is observed. Unattached virtual/derived sensors (`CyclingPowerMetricsDevice`, `HRVMetricsDevice`, `VerticalSpeedAndSlopeDevice`) or disconnected hardware sensors that never emit non-null values SHALL NOT enter `observedSensorTypes`.
     - In `TrackerService.endWorkout(@Nullable BanalFinalizationSnapshot snapshot)`, after committing `FINISHED = 1` first (`REQ-TRK-030.1`), `TrackerService` SHALL persist `observedSensorTypes` into `AccumulatedSensors` whenever `mLiveSession != null`, while preserving the `snapshot.accumulatedSensorTypes` (or `banalService.getAccumulatedSensorTypeSet()`) fallback when `mLiveSession == null` (`REQ-TRK-035.3`, `TST-TRK-027.3`).
  2. *`finalizeLiveSession()` Post-Processed Metric Upsert (`REQ-TRK-084.2`)*:
     - Because `endWorkout()` saves `AccumulatedSensors` prior to `finalizeLiveSession()` (`REQ-TRK-030.1`), `finalizeLiveSession()` SHALL idempotently upsert into `AccumulatedSensors` (via `WorkoutRepository`) any derived metrics that produce non-null persisted values during session finalization (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`, and `LINE_DISTANCE_m` when computed from valid `startPos`/`sampledLatLngs`).
  3. *Workout Resume Rehydration & Idempotent Deduplication (`REQ-TRK-084.3`)*:
     - Upon `RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`, `TrackerService.rehydrateLiveSession()` SHALL rehydrate `LiveWorkoutSession.observedSensorTypes` from existing `AccumulatedSensors` rows (`WorkoutRepository.getAccumulatedSensorTypes(workoutId)`) and persisted extrema sensors so pre-resume sensors survive process death.
     - `WorkoutSummariesDatabaseManager.saveAccumulatedSensorTypes(workoutId, sensorTypes)` (and any `addAccumulatedSensorTypes(workoutId, sensorTypes)` upsert helper) MUST deduplicate against existing `(workout_id, sensor_type)` rows for `workoutId` inside a transaction without modifying the `AccumulatedSensors` table schema (`N/A - no UI strings`).
  4. *Polar Offline Backfill & Conditional HRV Recomputation Upserts (`REQ-TRK-084.4`)*:
     - `PolarH10OfflineController` (both synchronous `stopAndBackfillWorkoutBlocking` and deferred `syncPendingWorkout`) SHALL idempotently upsert `SensorType.HR` into `AccumulatedSensors` whenever `patchMissingHeartRateSamples()` receives non-empty HR/RR samples.
     - `PolarVeritySenseOfflineController` SHALL idempotently upsert `SensorType.HR` into `AccumulatedSensors` in both `applyBackfillAndCommit()` and `applySwimBackfillAndCommit()` whenever `hrPairs` is non-empty.
     - `HrvPostWorkoutRecomputer.recomputeWorkout()` SHALL upsert `SensorType.HRV_ARTIFACT_RATE` when written, and conditionally upsert `SensorType.HRV_RMSSD` (only when `rmssdValues.isNotEmpty()`), `SensorType.HRV_DFA_A1` (only when `dfaValues.isNotEmpty()`), and `SensorType.HRV_SDNN` (only when `sessionSdnn != null`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements `REQ-TRK-030` (`REQ-TRK-030.2`, `MySensorManager.getAccumulatedSensorTypeSet()`), `REQ-TRK-035` (`REQ-TRK-035.1`/`.3`, `BanalFinalizationSnapshot`), `REQ-SET-105` (Polar H10 backfill), `REQ-SET-108` (Polar Verity Sense backfill), and `REQ-PER-034.3` (`HrvPostWorkoutRecomputer`).
2. *Historical Origin & Commit Trace*: `ATT-3986` (`0bf6fb05`) hardened `MySensorManager.getAccumulatedSensorTypeSet()` with a defensive copy; `ATT-4099` (`aa0e9097`) introduced `BanalFinalizationSnapshot`; `a7049ef9` retired legacy `CalcExtremaWorker` readers of `getAccumulatedSensorTypes()`; `ATT-7918`/`ATT-7923` added Polar H10/Verity Sense offline backfill and `HrvPostWorkoutRecomputer` post-workout recomputation.
3. *Root Reason for Existing Formulation*: `REQ-TRK-030.2` (`TST-TRK-022.3`) tests immediate registration visibility on `MySensorManager.getAccumulatedSensorTypeSet()`, and `REQ-TRK-035.3` (`TST-TRK-027.3`) tests `endWorkout(snapshot)` persistence when `mLiveSession == null`. Meanwhile, `PolarH10OfflineController`, `PolarVeritySenseOfflineController`, and `HrvPostWorkoutRecomputer` ran after `endWorkout()`'s `saveAccumulatedSensorTypes()` call without upserting backfilled sensor types.
4. *Preservation of Core Invariants*: Preserves `MySensorManager.getAccumulatedSensorTypeSet()` registration semantics (`TST-TRK-022.3`), `BanalFinalizationSnapshot.capture()` (`TST-TRK-027.1`), `mLiveSession == null` snapshot fallback (`TST-TRK-027.3`), `FINISHED = 1` first-commit isolation (`REQ-TRK-030.1`), zero schema migrations, and complete post-workout HR/HRV backfill coverage.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a live workout with GPS/Clock/Battery active and no HR, Power, or Cadence hardware connected (`CyclingPowerMetricsDevice`, `HRVMetricsDevice`, and `VerticalSpeedAndSlopeDevice` registered in `BANALService` emitting `null`),
• *When* `TrackerService.sampleAndWriteToDb()` and `endWorkout()` complete,
• *Then* `AccumulatedSensors` contains only sensors that emitted non-null telemetry and contains zero `HR`, `POWER`, `HRV_*`, `POWER_*`, `TSS`, `W_PRIME_BAL`, or `LIVE_HR_DRIFT` rows.
• *Given* a workout with valid power/HR/GPS samples whose derived metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`, `LINE_DISTANCE_m`) are computed in `finalizeLiveSession()`,
• *When* `endWorkout()` and `finalizeLiveSession()` complete,
• *Then* `FINISHED = 1` still commits first (`REQ-TRK-030.1`) and those computed non-null metric types are idempotently present in `AccumulatedSensors`.
• *Given* a resumed workout (`RESUME_BY_USER` / `RESUME_SERVICE_RECREATION`) with prior `AccumulatedSensors` rows or repeated `saveAccumulatedSensorTypes()`/`addAccumulatedSensorTypes()` calls,
• *When* `rehydrateLiveSession()` and `endWorkout()` execute,
• *Then* pre-resume sensor types are retained without duplicate `(workout_id, sensor_type)` rows.
• *Given* a Polar H10 (synchronous or deferred `syncPendingWorkout`) or Polar Verity Sense (`applyBackfillAndCommit` / `applySwimBackfillAndCommit`) offline backfill with non-empty HR/RR samples,
• *When* backfill and `HrvPostWorkoutRecomputer.recomputeWorkout()` complete,
• *Then* `SensorType.HR` is upserted into `AccumulatedSensors`, `HRV_ARTIFACT_RATE` is upserted when written, and `HRV_RMSSD`, `HRV_DFA_A1`, and `HRV_SDNN` are upserted strictly when their computed outputs are non-empty/non-null.

**Invariants**: `REQ-TRK-030.1` (`FINISHED = 1` first), `REQ-TRK-030.2` (`TST-TRK-022.3` defensive copy), `REQ-TRK-035.1`/`.3` (`TST-TRK-027.1`/`.3` snapshot contract), `GCData` 11-char mask, zero `AccumulatedSensors` schema changes, zero duplicate `(workout_id, sensor_type)` rows, and `N/A - no UI strings`.

### REQ-FIL-021: Post-Workout ERTS `VERTICAL_SPEED` `m/h` Unit Conversion, Clamping & Integer Persistence [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-021`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt`, `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`, `docs/architecture/sensor_units.md`
- **Rationale**: `SensorType.VERTICAL_SPEED` declares `SensorValueType.INTEGER` and `internalUnit = "m/h"` (`REQ-TRK-083`), and live devices (`VerticalSpeedAndSlopeDevice`, `AltitudeAndSlopeDevice_ATTFused`) emit rounded integer `m/h` clamped to `[-3000, 3000]` (`REQ-FIL-012`). Previously, `ErtsPostWorkoutRecomputer.bindColumnValue()` bound `SmoothedAltitudeSlopePoint.verticalSpeedMps` (`m/s`, clamped to `[-3.0, 3.0] m/s` in `ExtendedRtsAltitudeSlopeSmoother`) directly via `stmt.bindDouble()`, introducing a 3600x unit mismatch in `WorkoutSamples.db` and causing `CSVFileWriter.doExport()` (`cursor.getInt()`) to truncate sub-unit `m/s` values to `0` (`ATT-8577`, refining `REQ-FIL-017.3`, `REQ-FIL-012`, and `REQ-TRK-083`).
- **Description**:
  1. *`m/s` to `m/h` Conversion, Clamping & Integer SQLite Binding (`REQ-FIL-021.1`)*:
     - In `ErtsPostWorkoutRecomputer.bindColumnValue()`, when `columnName == SensorType.VERTICAL_SPEED.name` and `alt != null`, the system SHALL convert `alt.verticalSpeedMps * 3600.0`, clamp the result to `[-3000.0, 3000.0]` `m/h`, round via `Math.round(...)` to `Long`, and bind the integer value via `stmt.bindLong(bindIdx, vamMph)`.
     - When `alt == null`, `bindColumnValue()` SHALL preserve `stmt.bindNull(bindIdx)`.
     - `SmoothedAltitudeSlopePoint.verticalSpeedMps` MUST remain in `m/s` on the `ExtendedRtsAltitudeSlopeSmoother` API, and `ALLOWED_CANONICAL_COLUMNS`, device-attributed columns (`" ("`), `SLOPE`, `KEY_POST_WORKOUT_ERTS_SMOOTHING` (default `false`), and `SensorType.ordinal()`/`name()` MUST remain unchanged (`Localization Parity: N/A - no UI strings`).
  2. *Canonical Unit Documentation Synchronization (`REQ-FIL-021.2`)*:
     - `SensorType.java` (`VERTICAL_SPEED` Javadoc) and `docs/architecture/sensor_units.md` (`VERTICAL_SPEED` row and Section 2 note) SHALL reflect that `ATT-8577` resolved the legacy `ErtsPostWorkoutRecomputer` `m/s` mismatch while preserving the `"ATT-8577"` token asserted by `SensorTypeUnitsContractTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-021` refining `REQ-FIL-017.3` (*Strict Column Scope Invariant*), `REQ-FIL-012` (*Stabilized VAM Metric*), and `REQ-TRK-083` (*Canonical SensorType Unit Specification*). Targets `ErtsPostWorkoutRecomputer.kt`, `SensorType.java`, `docs/architecture/sensor_units.md`, and `ErtsPostWorkoutRecomputerTest.kt`.
2. *Historical Origin & Commit Trace*: `ed6acfee` (`ATT-7750`) added `ErtsPostWorkoutRecomputer.kt` and `ExtendedRtsAltitudeSlopeSmoother.kt`; `ed972bad` (`ATT-8337`) added null/sanity guards; `367a412f` (`ATT-8579`) formalized `VERTICAL_SPEED` `"m/h"` in `SensorType.java` and `sensor_units.md`.
3. *Root Reason for Existing Formulation*: `ExtendedRtsAltitudeSlopeSmoother` operates in SI `m/s` (`dh/dt`), and `ATT-7750` bound `alt.verticalSpeedMps` verbatim into SQLite without applying `SensorType.VERTICAL_SPEED`'s `m/h` `INTEGER` and `[-3000, 3000]` contract.
4. *Preservation of Core Invariants*: Preserves `SmoothedAltitudeSlopePoint.verticalSpeedMps` (`m/s`), strict canonical column scope (`REQ-FIL-017.3`), device column immutability, `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default, `SensorType` `ordinal()`/`name()`, and `"ATT-8577"` token in `docs/architecture/sensor_units.md`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with positive smoothed altitude climb (`alt.verticalSpeedMps = 0.25 m/s`),
• *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` updates `WorkoutSamples.db`,
• *Then* `VERTICAL_SPEED` stores integer `900` (`m/h`).
• *Given* a workout with negative smoothed vertical speed requiring fractional rounding (e.g. `alt.verticalSpeedMps` yielding non-integer `m/h` or `-0.25 m/s`),
• *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` executes,
• *Then* `VERTICAL_SPEED` stores `Math.round((alt.verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))` as an integer (`-900` for `-0.25 m/s`).
• *Given* extreme smoothed vertical speeds at the `ExtendedRtsAltitudeSlopeSmoother` bounds (`+3.0 m/s` and `-3.0 m/s`),
• *When* `ErtsPostWorkoutRecomputer.bindColumnValue()` binds `VERTICAL_SPEED`,
• *Then* values clamp strictly to `+3000` and `-3000` `m/h` respectively via `stmt.bindLong()`.
• *Given* `alt == null` in `ErtsPostWorkoutRecomputer.bindColumnValue()` alongside device-attributed columns (`"ALTITUDE (Höhe aus Luftdruck)"`, etc.),
• *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` runs,
• *Then* `VERTICAL_SPEED` binds SQLite `NULL` when `alt == null` and all device-attributed columns remain 100% byte-for-byte untouched, while `SensorTypeUnitsContractTest` passes cleanly (`N/A - no UI strings`).

**Invariants**: `SmoothedAltitudeSlopePoint.verticalSpeedMps` remains `m/s`; `ALLOWED_CANONICAL_COLUMNS` & device-column immutability (`REQ-FIL-017.3`); `[-3000, 3000]` `m/h` live parity (`REQ-FIL-012`); `SensorType` `ordinal()`/`name()` & `"ATT-8577"` contract token preserved; `N/A - no UI strings`.

### REQ-FIL-022: 3-State EKF Barometer Weather-Offset Bias Initialization & Calibration Jump Ascent Guard [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-022`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`
- **Rationale**: In 3-state mode (`is3StateMode = true`), `KinematicAltitudeSlopeEkf.updateBarometer()` previously initialized uncalibrated standard-atmosphere barometer altitude with `p00 = varBaro = 0.25` and `baroBias = 0.0`, causing Huber clamping (`K = 3.0`) and zero `p02` cross-covariance to lock in `+130 m` synoptic weather-pressure offsets against delayed DEM/GPS reference fixes (and conversely causing `Ref-before-Baro` `+130 m` offsets to pull `altitude` upward due to `p22 = 100.0` `13σ` clamping). Separating 3-state unanchored barometer startup from 2-state `p00 = varBaro` tracking and resetting the hysteresis anchor on initial reference calibration eliminates weather-pressure altitude lock-in and phantom `ASCENT`/`DESCENT` spikes (`ATT-8575`, refining `REQ-FIL-016.1`/`.2` and `REQ-FIL-020.2`).
- **Description**:
  1. *3-State Baro-before-Ref Weather-Offset Anchoring (`REQ-FIL-022.1`)*:
     - When `is3StateMode == true` and `updateBarometer()` initializes before any reference altitude anchor (`DEM` / `KnownLocation` / `GPS`), `KinematicAltitudeSlopeEkf` SHALL initialize `p00` with high unanchored altitude uncertainty (MUST NOT lock `p00 = 0.25`) and track that no reference anchor has been ingested yet.
     - Upon arrival of the first valid reference altitude `refAltM` (`updateReferenceAltitude(refAltM, varRef)`), even at standstill (`v < 0.3 m/s`), the EKF SHALL anchor `altitude = refAltM` (`p00 = varRef`), absorb the accumulated barometric offset into `baroBias` (`baroBias += oldAltitude - refAltM`), clear cross-covariances (`p01`, `p10`, `p02`, `p20`), and signal that initial calibration re-anchoring occurred.
  2. *3-State Ref-before-Baro Calibration & 2-State Covariance Guard (`REQ-FIL-022.2`)*:
     - When `updateReferenceAltitude()` initializes the EKF first (`hasReferenceAnchor = true`) and the first `updateBarometer(baroAltM)` subsequently arrives in 3-state mode, `KinematicAltitudeSlopeEkf` SHALL seed `baroBias = baroAltM - altitude` directly so large weather-pressure offsets (`>= 130 m`) do not trigger Huber-clamped pulling of `altitude` away from the valid reference.
     - In **2-state mode (`!is3StateMode`)**, `updateBarometer()` MUST preserve `p00 = varBaro` (`0.25`) and existing covariance propagation (`ekf.p00 < 10.0` after `predict(1800.0, 5.0)`). `reset()` and `setMode()` SHALL cleanly reset reference/barometer initialization tracking flags (`Localization Parity: N/A - no UI strings`).
  3. *Calibration Jump Ascent/Descent Guard (`REQ-FIL-022.3`)*:
     - In `AltitudeAndSlopeDevice_ATTFused` (`handleReferenceAltitude()` and `checkReferenceAltitudeFromSensors()`), whenever `ekf.updateReferenceAltitude(...)` performs initial reference re-anchoring, the device SHALL reset/align `mAscentDescentAnchorAltitude` and `mLastAltitude` prior to `publishAltitudeAndMetrics()` so the calibration jump adds `0.0 m` bogus `ASCENT` or `DESCENT`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-022` refining `REQ-FIL-016.1`/`.2` (*Kinematic Terrain-Slope EKF & Observability Modes*) and `REQ-FIL-020.2` (*Anchor-Based Elevation Hysteresis Deadband*). Targets `KinematicAltitudeSlopeEkf.kt` and `AltitudeAndSlopeDevice_ATTFused.kt`.
2. *Historical Origin & Commit Trace*: `75c1497e` (`ATT-6279`) introduced `KinematicAltitudeSlopeEkf` (`p00 = varBaro`, `p22 = 100.0`); `8b0eba41` (`ATT-8081`) introduced DEM precedence, MSL normalization, and `2.0 m` `mAscentDescentAnchorAltitude` hysteresis.
3. *Root Reason for Existing Formulation*: `ATT-6279` initialized `p00 = varBaro` identically in 2-state and 3-state modes and tested 3-state thermal drift (`testThreeStateMode`) with `0 m` initial baro-vs-DEM offset.
4. *Preservation of Core Invariants*: Preserves 2-state `p00 = varBaro` (`testPredict_clampsLargeDtSec`), standstill clamping (`v < 0.3 m/s -> dh = 0`), DEM-over-GPS precedence (`REQ-FIL-020.1`), `2.0 m` hysteresis (`REQ-FIL-020.2`), 3-state thermal drift absorption (`testThreeStateMode`), and `@Synchronized` thread safety.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `KinematicAltitudeSlopeEkf` in 3-state mode initialized at standstill with uncalibrated barometer `568.4 m` (`+130.4 m` weather offset),
• *When* a single DEM reference `438.0 m` arrives (`updateReferenceAltitude(438.0, DEFAULT_VAR_DEM)`) followed by 5-Hz `568.4 m` barometer updates,
• *Then* `altitude` anchors at `438.0 m` (±`0.5 m`), `baroBias` absorbs `+130.4 m` (±`0.5 m`), and subsequent barometer updates do not pull `altitude` back toward `568.4 m`.
• *Given* `KinematicAltitudeSlopeEkf` in 3-state mode initialized first by DEM `438.0 m` (`Ref-before-Baro`),
• *When* uncalibrated barometer readings at `568.4 m` (`+130.4 m` offset) arrive while moving or stationary,
• *Then* `baroBias` initializes to `+130.4 m` and `altitude` remains anchored near `438.0 m` without Huber-clamped drift.
• *Given* `KinematicAltitudeSlopeEkf` in **2-state mode (`is3StateMode = false`)**,
• *When* `updateBarometer(200.0)` and `predict(1800.0, 5.0)` execute,
• *Then* initial `p00 = varBaro` (`0.25`) is preserved and `ekf.p00 < 10.0`.
• *Given* `AltitudeAndSlopeDevice_ATTFused` receiving `568.4 m` pressure before first `438.0 m` DEM/GPS lock,
• *When* `calculateMetrics()` ingests the first DEM/GPS reference and publishes metrics,
• *Then* `mAltitudeSensor` reports `438.0 m`, `mAscentSensor` and `mDescentSensor` both remain `0.0 m` (`N/A - no UI strings`).

**Invariants**: 2-state `p00 = varBaro` preserved; standstill `dh = 0`; DEM-over-GPS precedence (`REQ-FIL-020.1`); `2.0 m` hysteresis (`REQ-FIL-020.2`); `baroBias` thermal drift isolation; `@Synchronized` thread safety; `N/A - no UI strings`.

### REQ-FIL-023: att_fused Cold GNSS Jump Gating, Standstill Slope Clearing & Low-Speed Slope Damping [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-023`, `TST-FIL-025`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`
- **Rationale**: During Pixel 10 ride `Zum Bäcker #56` (`ATT-8576`), cold GNSS convergence (`25.1 m -> 16.0 m` at `0.00 m/s` GPS speed) combined with premature `refLat/refLon` anchoring, pre-speed EKF position updates, and `location.accuracy` masking produced a `25.2 m / 1 s` jump (`46.3 km/h` spike, `-4.37 m` altitude drop, `-31%` slope), stops froze `SLOPE` at `-21%`, and `0.82–0.99 m/s` roll-away spiked `SLOPE` to `+29%` and inflated `ASCENT` to `26.00 m` (vs `6.13 m`). Hardening initial GNSS anchoring/gating, decaying/publishing `0%` slope on sustained standstill, and damping low-speed slope coupling eliminates these artifacts (`ATT-8576`, refining `REQ-FIL-007`, `REQ-SEN-028.3`, `REQ-FIL-016.1`/`.2`, and `REQ-FIL-022`):
- **Description**:
  1. *Cold GNSS Convergence Gating & Accuracy Preservation (`REQ-FIL-023.1`)*:
     - `SpeedAndLocationDevice_ATTFused.onLocationChanged()` SHALL NOT anchor `refLat`/`refLon` or forward unanchored fixes to `onNewLocation()` when GNSS `accuracy > UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS` (`20.0 m`) prior to the first valid GNSS anchor; `refLat`/`refLon` SHALL initialize on the first valid fix (`acc <= 20.0 m`) without emitting synthetic start distance.
     - `SpeedAndLocationDevice_ATTFused` SHALL ingest available speed/ZUPT updates before `ekf.updateGnssPosition()` and preserve `location.accuracy >= acc` (`max(acc, sqrt(ekf.positionVariance))`), preventing artificial `escapeRadius` shrinkage in `SpeedAndLocationDevice.onNewLocation()`.
     - `UnicycleOdometryEkf.updateGnssPosition()` SHALL apply innovation/ZUPT gating against stationary/cold convergence position jumps while preserving `REQ-FIL-009` tunnel re-acquisition during active riding.
  2. *Standstill Slope Decay & 0% Cockpit Clearing (`REQ-FIL-023.2`)*:
     - During sustained standstill (`speedMps < minSpeed`, default `0.5 m/s`, or `ekf` standstill `speed < standstillSpeedThresholdMps`), `KinematicAltitudeSlopeEkf` SHALL decay/reset `slope` toward `0.0` (preserving `dh = 0`, `Q_g = 0`, and `REQ-FIL-022` `baroBias` convergence), and `AltitudeAndSlopeDevice_ATTFused.publishAltitudeAndMetrics()` SHALL publish the zeroed/decayed `SLOPE` value (`0%`) instead of leaving pre-stop slope frozen in Cockpit and `WorkoutSamples.db` (`ATT-8473`/`ATT-8474` coherence; `Localization Parity: N/A - no UI strings`).
  3. *Low-Speed Slope Coupling Damping & Grade Bounds (`REQ-FIL-023.3`)*:
     - At low speeds (`< ~1.2 m/s`), `KinematicAltitudeSlopeEkf` SHALL damp slope innovation gain / process sensitivity over small `deltaDist` steps and enforce physical terrain grade bounds so barometric micro-noise at walking speed (`0.82–0.99 m/s`) cannot spike `SLOPE` to `+29%` / `-31%` or inflate `ASCENT` across the `2.0 m` hysteresis deadband (`REQ-FIL-020.2`), while preserving accurate slope and ascent on real climbs (`>= 1.5 m/s`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-023` refining `REQ-FIL-007`, `REQ-SEN-028.3`, `REQ-FIL-016.1`/`.2`, and `REQ-FIL-022`. Targets `SpeedAndLocationDevice_ATTFused.kt`, `UnicycleOdometryEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, and `KinematicAltitudeSlopeEkf.kt`.
2. *Historical Origin & Commit Trace*: `bc4a4109` (`ATT-131`), `75c1497e` (`ATT-6279`), `8b0eba41` (`ATT-8081`), `e9766625` (`ATT-8575`), and `196b3e67` (`ATT-6256` / `REQ-TRK-056`).
3. *Root Reason for Existing Formulation*: `ATT-131`/`ATT-7751` assumed initial fixes already satisfied `<= 20 m` accuracy and movement; `ATT-6279` mirrored `VerticalSpeedAndSlopeDevice` by skipping `mSlopeSensor` below `minSpeed` and left `ekf.slope` unbounded/undamped above `0.3 m/s`.
4. *Preservation of Core Invariants*: Preserves `SpeedAndLocationDevice.java` `REQ-TRK-056` fallback, `REQ-FIL-009` tunnel re-acquisition, `REQ-FIL-020.1`/`.2` DEM precedence & `2.0 m` hysteresis, `REQ-FIL-022` 3-state `baroBias` & 2-state `p00 = varBaro`, `@Synchronized` thread safety, and `ExtendedRts*Smoother.kt` post-workout logic (`N/A - no UI strings`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SpeedAndLocationDevice_ATTFused` receiving a cold `25.1 m` fix at `0.00 m/s` followed by a converged `16.0 m` fix `25.2 m` away at `0.00 m/s` (`t=7 s`),
• *When* `onLocationChanged()` processes both fixes,
• *Then* no `25.2 m` distance jump or `46.3 km/h` speed spike is emitted (`SPEED_mps < 2.0 m/s`, `DISTANCE_m < 5.0 m`), and `location.accuracy` is not masked below GNSS `acc`.
• *Given* `AltitudeAndSlopeDevice_ATTFused` / `KinematicAltitudeSlopeEkf` holding `-21%` slope before stopping at a traffic light (`speed = 0.0 m/s` for `>= 10 s`),
• *When* `calculateMetrics()` runs during standstill,
• *Then* `ekf.slopePercent` and `mSlopeSensor` smoothly clear to `0%` while `mAscentSensor` and `mDescentSensor` remain unchanged.
• *Given* roll-away at `0.82–0.99 m/s` on flat terrain with `±0.35 m` barometric noise,
• *When* 5-Hz `handlePressureMeasurement()` and 1-Hz `calculateMetrics()` execute for `30 s`,
• *Then* `SLOPE` stays bounded without `+29%` spikes and `ASCENT` does not inflate spuriously, while a true `+8%` climb at `5.0 m/s` still tracks accurately.

**Invariants**: `SpeedAndLocationDevice.java` `REQ-TRK-056` untouched; `REQ-FIL-009` tunnel exit preserved; `REQ-FIL-020` & `REQ-FIL-022` preserved; `@Synchronized` thread safety; `REQ-PRO-049.1` behavioral tests; `N/A - no UI strings`.

### REQ-CON-048: Offline & Unified EditDeviceDialog Radar Tail Light Configuration, Cold-Start Hydration & StVZO/Radar-Only Handling [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-048`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-007`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditDeviceDialog.kt`, `RadarLightModeGrid.kt`, `RadarLightController.kt`, `strings_radar.xml`
- **Rationale**: Following `ATT-7991` retiring tail light mode selectors from `RadarSettingsSection.kt` (`REQ-CON-031.5`), `ATT-8450` enabled offline `EditDeviceDialog` radar light configuration using legacy `SingleChoiceSegmentedButtonRow` (StVZO) and `10.dp` colored `CircleShape` dot + `RadioButton` rows. `ATT-8678` replaces that legacy selector UI in `EditDeviceDialog.kt` (`RadarLightControlSectionContent`) with the shared stateless `RadarLightModeGrid` (`ui/components/radar/`), achieving 1:1 visual/structural parity with `RadarLightQuickSwitchSheet` while preserving offline hydration, `showActiveModeSubtitle = !isOffline` accuracy, `RVR315` exclusion, direct `RadarLightController` wiring (decoupled from `EditDeviceViewModel` draft state), and Workout Auto-Switch (`ATT-8450`, `ATT-8678`, refining `REQ-CON-031.5`, `REQ-CON-031.7`, and `REQ-CON-047.2`/`.3`):
- **Description**:
  1. *Offline Visibility & Radar-Only Exclusion (`REQ-CON-048.1`)*:
     - `EditDeviceDialog.kt` (`RadarLightControlSection`) SHALL remain visible and interactive for `DeviceType.BIKE_RADAR` even when `!isConnected && !isSimulating`, unless the device is live-connected without light support (`isConnected && !isLightControlAvailable && !isSimulating`) or identifiable as a radar-only model without tail light (e.g. `RVR315` / `RVR 315` in `deviceName`).
  2. *Cold-Start DataStore Hydration, Offline Selection Persistence & Direct Controller Wiring (`REQ-CON-048.2`)*:
     - `RadarLightController` SHALL hydrate `isAutoMode` and `preferredMode` (`radarLightDefaultMode`) from `TuningPreferencesDataStore` prior to workout start / upon dialog initialization, expose the effective selected mode so offline manual selection persists and highlights the chosen mode (never falling back to `OFF` because live GATT dispatch returned `false`), and reset `_isStvzo` to `false` in `unregisterRadarDevice()`.
     - `RadarLightControlSection` MUST wire selections directly to `RadarLightController.setAutoMode()` and `RadarLightController.setManualLightMode(mode)` without mutating `EditDeviceViewModel` draft state, and MUST preserve Workout Auto-Switch lifecycle behavior (`RadarLightController`).
  3. *Shared `RadarLightModeGrid`, Offline `showActiveModeSubtitle` Gate & Unknown StVZO Handling (`REQ-CON-048.3`)*:
     - `RadarLightControlSectionContent` SHALL invoke the single shared stateless `RadarLightModeGrid` (`com.atrainingtracker.trainingtracker.ui.components.radar`) with `showActiveModeSubtitle = !isOffline`, completely eliminating duplicated `SingleChoiceSegmentedButtonRow` and `10.dp` dot/`RadioButton` rows while retaining `RadarLightMode_AUTO`, `RadarLightMode_<MODE>`, and `RadarLightStvzoNotice` semantic testTags.
     - When `!isConnected && !isSimulating`, `RadarLightControlSection` SHALL display `RadarLightOfflineHint` (`radar_light_offline_hint`) indicating selection applies on next connection, suppress `radar_light_active_mode_format` (`showActiveModeSubtitle == false` because offline `effectiveMode` is `preferredMode`, not live hardware state), and MUST NOT display `RadarLightStvzoNotice` while `isStvzo == false`, while still pruning to `Automatik`, `OFF`, `SOLID` whenever `isStvzo == true` and clamping non-StVZO defaults on connection/workout start.
  4. *Localization Parity, Preview Hygiene & Behavioral UI Tests (`REQ-CON-048.4`)*:
     - `Localization Parity: N/A - no UI strings` (`strings_radar.xml` untouched; existing 9-locale parity preserved across EN, DE, ES, FR, IT, JA, NL, PL, PT).
     - Zero new `internal`/`public` `@Preview`s SHALL be added (`DeadCodeEliminationContractTest`; pre-existing `EditDeviceDialog.kt` previews tracked under `ATT-8823`), and parity SHALL be verified via Robolectric `createComposeRule()` UI tests (row-major 2-column geometry, Light/Dark, StVZO/non-StVZO, online/offline, zero `app/src/main` source-reading calls per `REQ-PRO-049.1`) plus `Rule 8.5` PNG capture `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-CON-048`, `REQ-CON-031.5`, and `REQ-CON-031.7` (aligning with `REQ-CON-047.2`/`.3`); targets `EditDeviceDialog.kt`, `RadarLightModeGrid.kt`, and `RadarLightController.kt`.
2. *Historical Origin & Commit Trace*: `ATT-7540` introduced `RadarLightControlSection` gated on `isConnected && isLightControlAvailable`; `ATT-7991` (`c422e1b6`, `6e9b3c06`, `533d88b1`) retired Expert Settings mode controls and added `isSimulating`; `ATT-8456` unified ascending mode order (`REQ-CON-047.3`) in `RadarLightQuickSwitchSheet.kt`; `ATT-8450` enabled offline `EditDeviceDialog` mode config on legacy segmented/radio UI.
3. *Root Reason for Existing Formulation*: `ATT-8450` enabled offline configuration on the pre-existing `EditDeviceDialog` widgets while `ATT-8456` built the 2-column grid separately in `RadarLightQuickSwitchSheet.kt`.
4. *Preservation of Core Invariants*: Connected radar-only (`isConnected && !isLightControlAvailable`) and `RVR315` remain hidden; `EditDeviceViewModel` draft state remains decoupled (`saveChanges()` untouched); direct `RadarLightController.setAutoMode()` / `setManualLightMode(mode)` wiring and Workout Auto-Switch remain intact; offline `showActiveModeSubtitle = !isOffline` suppression prevents misleading live-state claims; StVZO pruning & on-connect clamping, `REQ-CON-047.3` ascending order, and 9-locale `strings_radar.xml` parity remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a paired, disconnected `RearVue8` (`DeviceType.BIKE_RADAR`, `isOffline = true`, `isStvzo = false`),
• *When* `RadarLightControlSectionContent` renders and the user selects `SOLID` or `Automatik`,
• *Then* `RadarLightModeGrid` renders the 2-column row-major tile grid (`[AUTO, OFF]`, `[PELOTON, SOLID]`, `[NIGHT_FLASH, DAY_FLASH]`) with `RadarLightOfflineHint` (zero `RadarLightStvzoNotice`, zero `radar_light_active_mode_format` subtitle when `isOffline = true`), highlights `SOLID` / `Automatik`, and invokes `onSelectMode(SOLID)` / `onSelectAuto()`.
• *Given* `RadarLightControlSectionContent` online (`isOffline = false`, `isAutoMode = true`),
• *When* rendered in Light/Dark mode (non-StVZO or StVZO),
• *Then* `showActiveModeSubtitle = true` displays `radar_light_active_mode_format` on `RadarLightMode_AUTO`, and StVZO renders `RadarLightStvzoNotice` + full-width `AUTO` row 1 + `[OFF, SOLID]` row 2 while pruning flashing modes.
• *Given* a radar-only unit (`RVR315`) or live-connected radar with `isLightControlAvailable = false` (not simulating),
• *When* `shouldShowRadarLightControlSection` evaluates,
• *Then* `RadarLightControlSection` is omitted completely, and `unregisterRadarDevice()` resets `_isStvzo` to `false`.

**Invariants**: Single shared `RadarLightModeGrid` (`0` duplicated dot/segmented-row UI); `showActiveModeSubtitle = !isOffline`; connected `!isLightControlAvailable` & `RVR315` hidden; `EditDeviceViewModel` draft state decoupled; direct `RadarLightController.setAutoMode()`/`setManualLightMode(mode)` wiring & Workout Auto-Switch preserved; StVZO pruning & on-connect clamping preserved; `REQ-CON-047.3` ascending order preserved; zero new `internal`/`public` `@Preview`s; zero SQLite schema changes; `Localization Parity: N/A - no UI strings`; zero `src/main` reads in new/modified tests (`REQ-PRO-049.1`).

### REQ-FIL-024: Barometric Broadcast Source Guard, Synchronized Workout-Start Reset & Same-Device Step Re-Seeding in VerticalSpeedAndSlopeDevice [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-024`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `VerticalSpeedAndSlopeDevice.java`, `VerticalSpeedAndSlopeDeviceTest.kt`
- **Rationale**: When `AltitudeAndSlopeDevice_ATTFused` (Rank 0) or `AltitudeFromPositionDevice` (Rank 1) outranks `AltitudeFromPressureDevice` (Rank 2) in `MySensorManager.ALTITUDE_PRIORITY`, background barometric calibration broadcasts (`ALTITUDE_CORRECTION_INTENT`) unconditionally shifted `VerticalSpeedAndSlopeDevice` filters (`REQ-FIL-012.6`), producing `+96.4 m` phantom `DESCENT` and `-203%` `SLOPE` spikes during workout startup. Additionally, `VerticalSpeedAndSlopeDevice` lacked an `onAccumulatorsReset()` override, synchronization between `calculateMetrics()` and `mAltitudeCorrectionReceiver`, and same-device altitude step re-seeding (`ATT-8707`, refining `REQ-FIL-012.5`, `REQ-FIL-012.6`, and aligning with `REQ-FIL-018.2`):
- **Description**:
  1. *Barometric Broadcast Source Guard (`REQ-FIL-024.1`)*:
     - `VerticalSpeedAndSlopeDevice.mAltitudeCorrectionReceiver` SHALL unwrap `mMySensorManager.getSensor(SensorType.ALTITUDE)` (`ProxySensor.getBestSensor()`) and apply `ALTITUDE_CORRECTION_INTENT` `delta` shifts (`mLastAltitudeSuperFiltered`, `mLastAltitude`, `mAltitudeHistory`, `cAltitudeFilter`, `cAltitudeSuperFilter`) ONLY when the active `SensorType.ALTITUDE` source device has `DeviceType.ALTITUDE_FROM_PRESSURE`. When a non-barometric source (e.g., `ALTITUDE_AND_SLOPE_ATT_FUSED` or `ALTITUDE_FROM_POSITION`) is active, `mAltitudeCorrectionReceiver` SHALL ignore the broadcast without shifting filters or baselines.
  2. *Synchronized Workout-Start Reset (`REQ-FIL-024.2`)*:
     - `VerticalSpeedAndSlopeDevice` SHALL override `onAccumulatorsReset()` (`synchronized` on `VerticalSpeedAndSlopeDevice.this`) to clear `mLastAltitudeSuperFiltered`, `mLastAltitude`, `mLastAltitudeSourceDevice`, and `mAltitudeHistoryHead`/`mAltitudeHistoryCount` (`mAltitudeHistory` zeroed) upon `RESET_ACCUMULATORS_INTENT`.
     - `VerticalSpeedAndSlopeDevice.calculateMetrics()` MUST synchronize on `VerticalSpeedAndSlopeDevice.this` consistently with `mAltitudeCorrectionReceiver` and `reseedAltitudeFilters()`.
  3. *Same-Device Altitude Step / EKF Re-Anchor Re-Seeding & Finite Guards (`REQ-FIL-024.3`)*:
     - `VerticalSpeedAndSlopeDevice` SHALL detect non-physical same-device step discontinuities ($\ge 15.0\text{ m}$) in raw `SensorType.ALTITUDE` on non-barometric sources (e.g., `AltitudeAndSlopeDevice_ATTFused` initial Baro-to-DEM/GPS anchor snap while `currentDevice == mLastAltitudeSourceDevice`) relative to `mLastAltitude` and immediately reseed via `reseedAltitudeFilters(rawAlt)` instead of integrating phantom `ASCENT`, `DESCENT`, or extreme `SLOPE`. `DeviceType.ALTITUDE_FROM_PRESSURE` SHALL be excluded from same-device step reseeding so barometric calibration shifts are applied solely via `ALTITUDE_CORRECTION_INTENT` without double-shift races.
     - Both `mAltitudeCorrectionReceiver` (`delta`) and `checkAltitudeSourceTransition()` / `calculateMetrics()` (`rawAlt` and filtered altitudes) MUST enforce `Double.isFinite(...)` so `NaN` and `±Infinity` are rejected deterministically.
  4. *Steady-State & Barometric Parity Preservation (`REQ-FIL-024.4`)*:
     - Genuine steady-state ascent/descent/slope integration, `N=15` linear regression VAM, accuracy/low-speed gating, `[-3000, 3000] m/h` clamping, and barometric calibration filter shifting when `DeviceType.ALTITUDE_FROM_PRESSURE` is the active `SensorType.ALTITUDE` source (`TST-FIL-004.5`) SHALL remain intact (`Localization Parity: N/A - no UI strings`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-024` refining `REQ-FIL-012` (`REQ-FIL-012.5`, `REQ-FIL-012.6`) and aligning with `REQ-FIL-018.2`. Targets `VerticalSpeedAndSlopeDevice.java`.
2. *Historical Origin & Commit Trace*: `31553134` (`ATT-4845`, `mAltitudeCorrectionReceiver`), `8d45376e` (`ATT-5373`, `checkAltitudeSourceTransition` + `shiftFilter`), `75c1497e` (`ATT-6279`, added `ATTFused` at `ALTITUDE_PRIORITY` Rank 0).
3. *Root Reason for Existing Formulation*: `ATT-5373` assumed `ALTITUDE_CORRECTION_INTENT` always applied to the active `ALTITUDE` filter and that large altitude jumps only occurred across `MyDevice` pointer switches before `ATTFused` existed.
4. *Preservation of Core Invariants*: Preserves `TST-FIL-004.1-.5` (including baro shift parity when `ALTITUDE_FROM_PRESSURE` is active), `REQ-FIL-012` VAM `N=15` regression and deadbands, `REQ-FIL-018` `TrackerService` handling, and zero DB/UI/submodule changes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SensorType.ALTITUDE` is backed by `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` (or `ALTITUDE_FROM_POSITION`),
• *When* `ALTITUDE_CORRECTION_INTENT` broadcasts `delta = +100.0 m`,
• *Then* `mLastAltitudeSuperFiltered`, `mAltitudeHistory`, `cAltitudeFilter`, and `cAltitudeSuperFilter` remain unshifted, and `DESCENT`/`SLOPE` remain `0.0 m` / `0%`.
• *Given* `VerticalSpeedAndSlopeDevice` holds pre-workout `mLastAltitudeSuperFiltered` and `mAltitudeHistory` state,
• *When* `onAccumulatorsReset()` runs on `RESET_ACCUMULATORS_INTENT`,
• *Then* `mLastAltitudeSuperFiltered`, `mLastAltitude`, `mLastAltitudeSourceDevice`, and `mAltitudeHistoryCount`/`Head` are cleared cleanly under `VerticalSpeedAndSlopeDevice.this` synchronization.
• *Given* `SensorType.ALTITUDE` stays on the same `ATTFused` device and snaps by a large calibration step (e.g., `-54 m` or `+100 m` on first DEM/GPS anchor),
• *When* `calculateMetrics()` executes,
• *Then* `reseedAltitudeFilters(rawAlt)` reseeds filters and baseline so `DESCENT`, `ASCENT`, and `SLOPE` record zero phantom spikes, while steady climbs/descents and active-baro `ALTITUDE_CORRECTION_INTENT` shifts work as before.

**Invariants**: `REQ-FIL-012` VAM `N=15` linear regression, accuracy/low-speed gating, `[-3000, 3000] m/h` clamp; `TST-FIL-004.5` active-baro shift parity; thread-safe synchronization on `VerticalSpeedAndSlopeDevice.this`; zero DB/UI changes; `N/A - no UI strings`.

### REQ-FIL-025: Post-Workout ERTS Running-Pace Heading Anchoring, 1-Hz Yaw-Rate Anti-Aliasing, Symmetric Sanity Bounds & Laps.db Sync [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-025`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ExtendedRtsOdometrySmoother.kt`, `UnicycleOdometryEkf.kt`, `ErtsPostWorkoutRecomputer.kt`, `LapsDatabaseManager.java`
- **Rationale**: During running workouts (`~2.0–2.2 m/s`, below `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0`), `ExtendedRtsOdometrySmoother` hardcoded `initialHeadingRad = PI / 2.0`, suppressed all heading updates, and multiplied 1-Hz instantaneous `WORLD_YAW_RATE_radps` snapshots by `dtSec ~ 1.0 s`, driving heading backwards, collapsing `x[2]` negative and clamping speed to `0.0 m/s` (-18.4% distance loss, false altitude standstill clamping). Additionally, `ErtsPostWorkoutRecomputer.isOdometrySane()` checked only upper `> 3.0x` divergence, and `updateWorkoutSummaries()` left `Laps.db` desynced (`ATT-8705`, refining `REQ-FIL-007`, `REQ-FIL-017`, `REQ-FIL-019.4`, and preserving `REQ-FIL-023.1`):
- **Description**:
  1. *Offline ERTS Heading Initialization & Running-Pace Displacement/Bearing Anchoring (`REQ-FIL-025.1`)*:
     - `ExtendedRtsOdometrySmoother.smoothSamples()` SHALL initialize `initialHeadingRad` from the first valid GNSS movement chord (or valid moving GNSS bearing) rather than hardcoding `PI / 2.0` whenever non-stationary motion is present (`PI / 2.0` fallback only if no valid GNSS chord exists).
     - During offline ERTS replay at running paces (e.g., `~2.0 m/s`, `>= 0.8 m/s` confirmed by GNSS displacement/speed), `ExtendedRtsOdometrySmoother` SHALL anchor heading using GNSS displacement-chord bearing and/or offline Doppler heading updates, while `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS` (`3.0 m/s`) MUST remain intact for live `updateDopplerHeading()` stationary/low-speed protection (`ATT-8576`, `REQ-FIL-023.1`).
  2. *1-Hz Yaw-Rate Snapshot Anti-Aliasing & Non-Negative Velocity Protection (`REQ-FIL-025.2`)*:
     - `ExtendedRtsOdometrySmoother` SHALL mitigate 1-Hz instantaneous `WORLD_YAW_RATE_radps` snapshot aliasing in offline RTS replay (attenuating/bounding high-frequency swing aliasing and increasing gyro/heading uncertainty when relying on 1-Hz snapshots) and MUST prevent non-physical negative velocity state collapse (`x[2] < 0`) and false `0.0 m/s` clamping during active motion, preserving `ExtendedRtsAltitudeSlopeSmoother` standstill gating (`STANDSTILL_SPEED_THRESHOLD_MPS = 0.3 m/s`) accuracy (`REQ-FIL-017.2`).
  3. *Symmetric ERTS Odometry Sanity Bounds & Live `att_fused` Preservation (`REQ-FIL-025.3`)*:
     - `ErtsPostWorkoutRecomputer.isOdometrySane()` SHALL enforce symmetric lower and upper distance divergence bounds (accounting for pauses so paused stops do not trigger false lower-bound rejections) against raw reference distance, rejecting collapsed or runaway smoothed trajectories and cleanly preserving live `att_fused` canonical columns and summaries (`REQ-FIL-017.3`, `REQ-FIL-019.4`).
  4. *Post-Workout `Laps.db` Synchronization (`REQ-FIL-025.4`)*:
     - Whenever `ErtsPostWorkoutRecomputer` commits updated smoothed odometry totals to `WorkoutSummaries.db`, it SHALL synchronize `Laps.db` (`LapsDatabaseManager` / `Laps.TABLE`) lap distances and average speeds for the workout using smoothed `WorkoutSamples.db` `LAP_NR` segments (with deterministic proportional fallback when per-lap sample boundaries are unavailable), keeping `Laps.db` consistent with `WorkoutSummaries.db` (`Localization Parity: N/A - no UI strings`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-025` refining `REQ-FIL-007`, `REQ-FIL-017`, and `REQ-FIL-019.4`, while preserving `REQ-FIL-023.1` (`ATT-8576`). Targets `ExtendedRtsOdometrySmoother.kt`, `UnicycleOdometryEkf.kt`, `ErtsPostWorkoutRecomputer.kt`, and `LapsDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: `bc4a4109` (`ATT-131` cycling Doppler gate `> 3.0 m/s`), `d56b001a` (`ATT-7750` offline ERTS replay), `ed972bad` (`ATT-8337` one-sided `> 3.0x` Null-Island sanity guard), `eba66320`/`f6715226` (`ATT-8576` live stationary/low-speed ZUPT protection).
3. *Root Reason for Existing Formulation*: `MIN_DOPPLER_SPEED_MPS = 3.0` prevented noisy GNSS Doppler bearings from spinning live stationary/walking headings; `isOdometrySane()` was scoped only to `> 3x` Null-Island explosions.
4. *Preservation of Core Invariants*: Preserves live `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` & `ATT-8576` stationary/ZUPT gating, `REQ-FIL-009` tunnel dead reckoning, `REQ-FIL-017.3` raw device-column immutability, `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default, and `ExtendedRtsAltitudeSlopeSmoother` standstill gating accuracy.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a running workout at `~2.0 m/s` starting South/West/East (not North) with 1-Hz `WORLD_YAW_RATE_radps` swing noise,
• *When* `ExtendedRtsOdometrySmoother.smoothSamples()` runs,
• *Then* `initialHeadingRad` initializes from the first valid GNSS movement chord, running-pace displacement/bearing updates keep heading aligned, `x[2]` never collapses negative or clamps falsely to `0.0 m/s`, cumulative distance stays accurate, and `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` remains enforced for live `updateDopplerHeading()`.
• *Given* an offline smoothed trajectory whose distance collapses below the lower divergence bound (while accounting for pauses) or exceeds the upper `3.0x` bound,
• *When* `ErtsPostWorkoutRecomputer.isOdometrySane()` validates the output,
• *Then* sanity validation returns `false`, preserving live `att_fused` canonical columns, summaries, and `Laps.db` intact.
• *Given* a multi-lap workout where `ErtsPostWorkoutRecomputer` accepts and commits smoothed totals to `WorkoutSummaries.db`,
• *When* `updateWorkoutSummaries()` finishes,
• *Then* `Laps.db` lap distances and average speeds are updated to match the smoothed `WorkoutSamples.db` `LAP_NR` segments and sum consistently with `WorkoutSummaries.DISTANCE_TOTAL_m`.

**Invariants**: Live `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` & `ATT-8576` stationary/ZUPT gating unchanged; `ExtendedRtsAltitudeSlopeSmoother` `STANDSTILL_SPEED_THRESHOLD_MPS = 0.3` preserved; `REQ-FIL-017.3` raw device-column immutability & `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` preserved; zero DB schema version bumps; `REQ-PRO-049.1` offline tests with zero `src/main` reads; `N/A - no UI strings`.

### REQ-SEN-031: EditDeviceDialog Wheel Speed Sensor Mounting Position Display, Draft Selection & Conflict Swap [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SEN-031`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceData.kt`, `RawDeviceDataProvider.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt`, `WheelPositionSelectionDialog.kt`, `EquipmentRepository.kt`, `EquipmentDbHelper.java`, `strings_equipment.xml`
- **Rationale**: `REQ-SEN-029` (`ATT-7752`) introduced `WheelPosition` (`FRONT`, `REAR`, `UNSPECIFIED`) in `Equipment.db` and `EquipmentSensorMatrixScreen.kt`, but `EditDeviceDialog.kt` (`EquipmentSection`) continued displaying plain bike names and binary checkboxes without showing or prompting for mounting position (`ATT-8719`, refining `REQ-SEN-029` and `REQ-UI-149`):
- **Description**:
  1. *Per-Bike `WheelPosition` Hydration & Summary/Sheet Display (`REQ-SEN-031.1`)*:
     - `DeviceRawData` and `DeviceUiData` SHALL expose `linkedWheelPositions: Map<String, WheelPosition> = emptyMap()` (defaulting to `emptyMap()` for source compatibility) hydrated by `RawDeviceDataProvider` from `EquipmentDbHelper`.
     - For `DeviceType.BIKE_SPEED` and `DeviceType.BIKE_SPEED_AND_CADENCE`, `EditDeviceDialog.kt` (`EquipmentSection`) SHALL display each linked bike's localized `WheelPosition` (`equipment_wheel_position_front` / `_rear` / `_unspecified`) in both the read-only summary field and the bike-linking bottom sheet rows.
  2. *Stateless `WheelPositionSelectionDialog` Reuse, Conflict Preview & Strict Draft Semantics (`REQ-SEN-031.2`)*:
     - Tapping a bike row in the `EquipmentSection` sheet for a wheel speed sensor SHALL open the reused stateless `WheelPositionSelectionDialog` to stage draft `WheelPosition` (`FRONT` or `REAR`) or unlink, surfacing a conflict indicator/swap preview when another wheel sensor on that bike already occupies `FRONT` or `REAR` (refined by `REQ-SEN-032`).
     - Staging changes in `WheelPositionSelectionDialog`, the Equipment sheet (`OK`/`Cancel`), or `EditDeviceDialog` (`SaveCancel`) MUST remain strictly in `EditDeviceViewModel` draft memory; tapping `Cancel` on either sheet or dialog MUST discard all draft link/position edits with zero `Equipment.db` mutations.
  3. *Atomic `Save` Commit & `swapConflict = true` Parity (`REQ-SEN-031.3`)*:
     - On `Save`, `DeviceDataRepository.updateDevice()` SHALL detect changes to either `linkedEquipment` or `linkedWheelPositions` (compared against `originalState`), update `_allDevices` before dispatching bulk link notifications, unlink removed bikes, commit linked wheel sensors via `EquipmentRepository.setWheelSensorLink(equipmentId, sensorId, position, swapConflict = true)`, and trigger `EquipmentRepository.notifyEquipmentLinksChanged(null)` so sibling unswapped wheel sensors refresh `wheelPositionConflicts`.
  4. *Non-Wheel Sensor Isolation & 9-Locale Parity (`REQ-SEN-031.4`)*:
     - Non-wheel sensors (`HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, `RUN_SPEED`, smartphone sensors) MUST strictly retain the existing simple checkbox selection UI with zero wheel-position prompts.
     - UI labels SHALL reuse existing 9-locale `strings_equipment.xml` resources (`equipment_wheel_position_*`), and any newly added conflict/swap hint key MUST achieve 100% parity across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-SEN-029` (*Front/Rear Wheel Speed Sensor Mounting Position Tracking*, `ATT-7752`) and `REQ-UI-149` (*Modal Bottom Sheet Dialogs*). Targets `DeviceData.kt`, `RawDeviceDataProvider.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt`, `WheelPositionSelectionDialog.kt`.
2. *Historical Origin & Commit Trace*: `4edcda86` (`ATT-7752` added `WheelPosition` in `EquipmentSensorMatrixScreen.kt` and `setEquipmentLinks()` preservation in `EquipmentDbHelper.java:839-876`).
3. *Root Reason for Existing Formulation*: `ATT-7752` deferred `EditDeviceDialog.kt` `EquipmentSection` wheel-position UI and `DeviceDataRepository.updateDevice()` wheel-position commit parity.
4. *Preservation of Core Invariants*: Preserves `AppDialogActions.SaveCancel` draft isolation (`Rule 68`), `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)` single-`FRONT`/single-`REAR` per-bike guardrail, `equipmentLinksChanged` reactive sync, and simple checkbox UI for non-wheel sensors.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `BIKE_SPEED` or `BIKE_SPEED_AND_CADENCE` sensor linked to Bike A (`FRONT`), *When* `EditDeviceDialog` opens, *Then* `EquipmentSection` summary and bike-linking sheet display Bike A with its localized `Front` mounting position.
• *Given* the bike-linking sheet for a wheel speed sensor where Bike B already has another sensor at `REAR`, *When* the user taps Bike B and selects `REAR` (seeing the conflict/swap preview) and confirms sheet `OK` + dialog `Save`, *Then* `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)` swaps Bike B's conflicting sensor and persists `REAR`; *When* the user taps `Cancel` on the sheet or dialog instead, *Then* zero DB mutations occur and draft changes are discarded.
• *Given* a non-wheel sensor (`HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, `RUN_SPEED`), *When* `EquipmentSection` sheet opens, *Then* simple checkboxes render without `WheelPositionSelectionDialog`.

**Invariants**: `SaveCancel` draft isolation (`Cancel` = zero DB writes); `EquipmentDbHelper` schema v4 unchanged; `swapConflict = true` bike guardrail; non-wheel simple checkbox UI untouched; 9-locale `strings_equipment.xml` parity; `REQ-PRO-049.1` offline tests with zero `src/main` reads.

### REQ-TRK-085: HRV/RR Ingestion Monotonicity, Physiological Gap Flagging, Pre-Filter Timestamp Sorting & DFA α1 Validity Gate [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-085`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-006`, `SYSREQ-TRK-012`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceManager.java`, `TrackerService.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `RrBeatTimestampReconstructor.kt`, `RrArtifactFilter.kt`, `HrvPostWorkoutRecomputer.kt`, `DfaCalculator.kt`, `HRVMetricsDevice.kt`
- **Rationale**: Dual simultaneous HRMs (`BTLEHeartRateDevice` + `PolarOnlineStreamingBridge`) multiplexed into one `TrackerService.mRrBeatListener`/`mRrBeatIndexCounter`, unclamped drift re-anchoring produced negative `beat_timestamp_ms` jumps (`-430 ms`), drift-only gap detection left multi-second RR intervals (`5,196–6,378 ms`) unflagged (`artifact_flag = 0`), `HrvPostWorkoutRecomputer` filtered before sorting by `beatTimestampMs`, and `DfaCalculator` permitted non-physical negative slopes (`HRV_DFA_A1 = -1.0684`). Enforcing single-primary-source RR routing, monotonic clamping, physiological RR bounds, pre-filter chronological sorting, and positive finite DFA $\alpha_1$ gating eliminates corrupted RR persistence and negative $\alpha_1$ artifacts (`ATT-8706`, refining `REQ-CON-027.3`, `REQ-TRK-060.2`/`.4`, and `REQ-PER-034.2`/`.3`).
- **Description**:
  1. *Single-Primary-Source RR Routing & Monotonic Timestamp Enforcement (`REQ-TRK-085.1`)*:
     - `DeviceManager` / `TrackerService` SHALL route live RR beats to `TrackerService.mRrBeatListener` and `HRVMetricsDevice` from at most one active primary `RrBeatSource` at a time (preventing concurrent dual-HRM interleaving into `mRrBeatIndexCounter` and live HRV windows).
     - `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, and `RrBeatTimestampReconstructor` MUST enforce monotonic non-decreasing `beatTimestampMs >= lastBeatTimestampMs` across bundle back-calculation and drift re-anchoring (`drift > 2000 ms`).
  2. *Physiological RR Gap Flagging at Ingestion & Filter (`REQ-TRK-085.2`)*:
     - `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, and `RrBeatTimestampReconstructor` at ingestion—and `RrArtifactFilter` (`filterBidirectionalBeats`, `filterLiveCausal`) defensively—SHALL flag implausible physiological RR intervals (`rrMs > 2500.0` ms or `rrMs < 250.0` ms) with `RrBeatEvent.ARTIFACT_GAP` (`5`) and positive estimated `gapBeats` even when wall-clock drift is `<= 2000 ms` or `k = 1`.
  3. *Pre-Filter Chronological Sorting in `HrvPostWorkoutRecomputer` (`REQ-TRK-085.3`)*:
     - `HrvPostWorkoutRecomputer` SHALL sort `beats` chronologically by `beatTimestampMs` (with deterministic tie-break on `rawCounter` and monotonic normalization of any residual backward timestamps) BEFORE invoking `RrArtifactFilter.filterBidirectionalBeats()`, preventing interleaved intervals from distorting 91-beat quartile thresholds (`th1`/`th2`).
  4. *DFA $\alpha_1$ Positive Finite Validity Gate (`REQ-TRK-085.4`)*:
     - `DfaCalculator.computeDfaAlpha1()`, `HrvPostWorkoutRecomputer`, and `HRVMetricsDevice` SHALL reject non-finite or non-positive DFA $\alpha_1$ values (`!alpha1.isFinite() || alpha1 <= 0.0`) cleanly as `Double.NaN` / `null` (never persisting negative `HRV_DFA_A1`), while preserving `DfaCalculatorTest.testConstantSignalDoesNotCrash` and NeuroKit2 0.2.10 golden parity (`|Δα1| <= 0.02` pre-corrected / `<= 0.03` end-to-end, `|ΔrMSSD| <= 0.5 ms`; `Localization Parity: N/A - no UI strings`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-TRK-085` refining `REQ-CON-027.3`, `REQ-TRK-060.2`/`.4`, and `REQ-PER-034.2`/`.3`. Targets `DeviceManager.java`, `TrackerService.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `RrBeatTimestampReconstructor.kt`, `RrArtifactFilter.kt`, `HrvPostWorkoutRecomputer.kt`, `DfaCalculator.kt`, and `HRVMetricsDevice.kt`.
2. *Historical Origin & Commit Trace*: `8b2196d9` (`ATT-6714`, split/merge & `overlap=True`), `50ffedc6` (`ATT-4970`, durability), `c54768d2` (`ATT-7530`), `42d1e4c9` (`ATT-8578`).
3. *Root Reason for Existing Formulation*: `REQ-CON-027.3` & `REQ-PER-034.2` detected gaps solely via wall-clock drift (`> 2000 ms`) assuming one continuous sensor stream, and `HrvPostWorkoutRecomputer` trusted `WorkoutRrDatabaseManager` `beat_index ASC` ordering before `filterBidirectionalBeats()`.
4. *Preservation of Core Invariants*: Preserves `HrvGoldenOracleTest` NeuroKit2 0.2.10 tolerances (`|Δα1| <= 0.02`/`0.03`, `|ΔrMSSD| <= 0.5 ms`), `DfaCalculatorTest.testConstantSignalDoesNotCrash` (`NaN || 0.0`), `RrArtifactFilterTest` split/merge semantics, `REQ-TRK-084.4` conditional `AccumulatedSensors` upsert, zero `WorkoutRr.db` schema migration, and `N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* two concurrent `RrBeatSource` HRMs (e.g., Polar H10 + Verity Sense) or a drift re-anchor with back-calculated bundle beats,
• *When* RR beats arrive at `DeviceManager`/`TrackerService` or `BTLEHeartRateDevice`/`PolarOnlineStreamingBridge`/`RrBeatTimestampReconstructor`,
• *Then* only the active primary `RrBeatSource` feeds `mRrBeatListener`/`mHrvMetricsDevice` and `beatTimestampMs` never decreases (`beatTimestampMs >= lastBeatTimestampMs`).
• *Given* an RR interval `rrMs > 2500.0 ms` (e.g. `5196.29 ms`) or `< 250.0 ms` arriving with `drift <= 2000 ms`,
• *When* processed by ingestion or `RrArtifactFilter`,
• *Then* the beat is flagged `ARTIFACT_GAP` (`5`) with non-zero `gapBeats` and counted toward artifact rate.
• *Given* persisted `WorkoutRr.db` beats containing interleaved/out-of-order `beat_timestamp_ms` and multi-second gaps,
• *When* `HrvPostWorkoutRecomputer` evaluates the workout,
• *Then* beats are sorted by `beatTimestampMs` before `filterBidirectionalBeats()`, `HRV_DFA_A1` never persists negative values (`<= 0.0` gated to `null`/`Double.NaN`), and NeuroKit2 golden parity remains within tolerance.

**Invariants**: NeuroKit2 0.2.10 golden parity (`|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`0.03`); `DfaCalculatorTest.testConstantSignalDoesNotCrash`; `REQ-TRK-084.4` conditional upsert; zero `WorkoutRr.db` schema bump; `@Synchronized` thread safety; `N/A - no UI strings`.

### REQ-TRK-086: Gesture-Isolated HrvDfaA1RangeSlider Dragging in ZonesSettingsActivity HorizontalPager [Verified]
- **Tests**: `TST-TRK-086`
- **System Requirement(s)**: `SYSREQ-TRK-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`
- **Rationale**: On tab 3 (`HRV (DFA α1)`, `page == 3`) of `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`), `HorizontalPager` previously omitted `userScrollEnabled`, causing rightward drags on `HrvDfaA1RangeSlider` thumbs (especially right thumb `T2`) or touches inside the slider `Card` padding to lose the touch-slop race and switch back to `PWR_BIKE` (`page == 2`), while unbuffered async `TuningPreferencesDataStore` writes risked mid-drag thumb snap-back (`ATT-8105`, refining `REQ-TRK-061.4`).
- **Description**:
  1. *HorizontalPager Gesture Isolation via `PointerEventPass.Initial` & `MutableInteractionSource` (`REQ-TRK-086.1`)*:
     - `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`) SHALL hold lifted `isSliderGestureActive` state controlling `HorizontalPager(userScrollEnabled = !isSliderGestureActive)` and pass a defaulted `onSliderGestureActiveChange: (Boolean) -> Unit = {}` callback through `HrvDfaA1SettingsContent` to `HrvDfaA1RangeSlider` and `HrvThresholdControlRow`.
     - `HrvDfaA1RangeSlider` (and `HrvThresholdControlRow` when `showSlider == true`) MUST combine `PointerEventPass.Initial` press/release detection on its slider container with M3 `MutableInteractionSource` (`startInteractionSource` / `endInteractionSource` press & drag tracking) so `isSliderGestureActive` becomes `true` immediately on initial pointer down (zero slop-race window) and resets cleanly to `false` on pointer release, cancel, or composable disposal (`DisposableEffect`).
     - The slider `Card`/container MUST consume residual horizontal-dominant pointer deltas in `PointerEventPass.Main` so unconsumed horizontal movement inside `12.dp` padding cannot trigger `HorizontalPager` tab switches, while preserving `verticalScroll` and normal horizontal tab swipes outside the slider region.
  2. *Bidirectional Thumb Tracking & Local Drag State (`REQ-TRK-086.2`)*:
     - Both rightward (`T2` / `T1` moved right on `page == 3`) AND leftward (`T1` / `T2` moved left) thumb drags on `HrvDfaA1RangeSlider` SHALL adjust thresholds smoothly without triggering tab switches or pager overscroll.
     - `HrvDfaA1RangeSlider` MUST maintain local drag state synchronized with `t1`/`t2` only while idle (`!isDragging`), preventing asynchronous `TuningPreferencesDataStore` latency or repeated clamped updates from snapping thumbs backward mid-drag while strictly enforcing domain `0.30f..1.20f` and $\alpha_{1,\text{T1}} \ge \alpha_{1,\text{T2}} + 0.05f$.
  3. *Behavioral Compose UI Gesture Verification & Visual Evidence (`REQ-TRK-086.3`)*:
     - Gesture isolation, bidirectional `T1`/`T2` dragging, non-slider tab switching, and vertical scroll preservation SHALL be verified via behavioral Robolectric Compose `createComposeRule()` (`performTouchInput`) tests with zero `app/src/main/` file reads (`REQ-PRO-049.1` / `ATT-8198`), plus Light/Dark screenshot PNG verification at `docs/engineering/walkthroughs/img/ATT-8105_hrv_slider_zones.png` (`Rule 8.5`; `Localization Parity: N/A - no UI strings added or modified`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-TRK-086` refining `REQ-TRK-061.4` (`ZonesSettingsActivity.kt`).
2. *Historical Origin & Commit Trace*: `ATT-3542` (`14292b35`) $\rightarrow$ `ATT-7317` $\rightarrow$ `ATT-7519` (`467f7663`) $\rightarrow$ `ATT-7741` (`5fa1e164`) $\rightarrow$ `ATT-8498` (`81af2506`).
3. *Root Reason for Existing Formulation*: `REQ-TRK-061.4` introduced the unified inverted 3-zone M3 `RangeSlider` inside `HorizontalPager` tab 3 without `userScrollEnabled` gesture arbitration or local drag buffering.
4. *Preservation of Core Invariants*: Inverted left-green/right-red `RangeSlider` `track` slot (`TTColor.Zone2/3/4`), `0.30f..1.20f` domain, $T_1 \ge T_2 + 0.05f$ gap, public composable names (defaulted optional callback), vertical scrolling, non-slider tab swiping, `HrvDfaA1SettingsContractTest`, and 9-locale `strings_hrv.xml` parity remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZoneSettingsScreen` on tab 3 (`HRV (DFA α1)`, `page == 3`),
• *When* the user drags `HrvDfaA1RangeSlider` thumb `T2` or `T1` rightward OR leftward (including starting inside slider `Card` padding),
• *Then* `HorizontalPager` stays on `page == 3` (`userScrollEnabled == false` during the gesture), thresholds update smoothly within `0.30f..1.20f` enforcing $T_1 \ge T_2 + 0.05f$ without snap-back, and `userScrollEnabled` restores to `true` on release.
• *Given* `ZoneSettingsScreen` on tab 3 with the slider idle,
• *When* the user swipes horizontally outside the slider `Card` OR scrolls vertically across `HrvDfaA1SettingsContent`,
• *Then* horizontal swipe switches cleanly to `page == 2` (`PWR_BIKE`) and vertical scroll advances `scrollState` without interference.

**Invariants**: Inverted 3-zone `RangeSlider` track (`TTColor.Zone2/3/4`); `0.30f..1.20f` bounds & $T_1 \ge T_2 + 0.05f$; backward-compatible public composable signatures; `verticalScroll` & non-slider tab swiping intact; `HrvDfaA1SettingsContractTest` intact; `REQ-PRO-049.1` zero `src/main` reads in new/modified tests; `Localization Parity: N/A - no UI strings`.


### REQ-CON-049: Hardware-Grounded Polar Detection & Canonical ID via BLE Service UUIDs (PMD/PFTP) and DIS (0x2A24/0x2A25) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-049`, `TST-CON-050`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`, `SYSREQ-DEX-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BluetoothConstants.java`, `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `BTSearchForNewDevicesEngine.java`, `MyBTLEDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `DeviceDataRepository.kt`, `EditDeviceDialog.kt`
- **Rationale**: `PolarOfflineGattCoordinator` and downstream callers (`PolarOnlineStreamingBridge`, `PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `DeviceDataRepository`, `EditDeviceDialog`) currently classify Polar H10 vs. Verity Sense/OH1 and extract the 8-hex Polar ID via user-editable display-name regex/substring checks (`isPolarH10Name`, `isPolarVeritySenseName`, `POLAR_ID_REGEX`). Because `BTSearchForNewDevicesEngine` marks `UUID_SERVICE_POLAR_PFTP` for H10 as well as Verity Sense, renaming an H10 causes it to fail `!isPolarH10Name` and misclassify as Verity Sense (routing to the wrong offline API, `VERITY_SENSE_ARM` IMU mode, and `RR_PRIORITY_PPG`), while renaming a Verity Sense falls back to MAC instead of the canonical 8-hex Polar ID (`ATT-8717`, refining `REQ-SET-104.3`, `REQ-SET-108.1`, `REQ-SET-112.1/.2`, and `REQ-SET-118.2`):
- **Description**:
  1. *BLE GATT Service UUID Capability Separation (`REQ-CON-049.1`)*:
     - `PolarOfflineCapabilityStore` SHALL store `hasPmdService` (`UUID_SERVICE_POLAR_PMD`, `fb005c80-02e7-f387-1cad-8acd2d8df0c8`) and `hasPftpService` (`UUID_SERVICE_POLAR_PFTP`, `0000feee-0000-1000-8000-00805f9b34fb`) independently from generic `offline_rec_<MAC>` per normalized MAC address, ensuring PFTP capability on a Polar H10 never triggers Verity Sense classification.
  2. *DIS (`0x180A`) Hardware Metadata Persistence Without `Devices.db` Migration (`REQ-CON-049.2`)*:
     - `BluetoothConstants` SHALL define `UUID_CHARACTERISTIC_SERIAL_NUMBER` (`00002a25-0000-1000-8000-00805f9b34fb`) alongside `UUID_CHARACTERISTIC_MODEL_NUMBER` (`00002a24-0000-1000-8000-00805f9b34fb`).
     - `PolarOfflineCapabilityStore` SHALL persist hardware `modelNumber` (`0x2A24`), canonical `polarDeviceId` (`0x2A25` / `PolarDeviceInfo.deviceId`), and derived `PolarDeviceType` (`H10`, `VERITY_SENSE`, `UNKNOWN`) in `SharedPreferences` + `ConcurrentHashMap` keyed by normalized MAC address, keeping `DevicesDatabaseManager.DB_VERSION` unchanged at `14`.
  3. *Three-Path DIS & Capability Hydration (`REQ-CON-049.3`)*:
     - The system SHALL capture and persist PMD/PFTP service UUIDs and DIS `0x2A24`/`0x2A25` metadata during: (a) pairing in `BTSearchForNewDevicesEngine`, (b) standard BLE HRM connect/reconnect in `MyBTLEDevice` / `BTLEHeartRateDevice`, and (c) Polar SDK sessions in `PolarOnlineStreamingBridge.sdkCallback` (`disInformationReceived`, `deviceConnected`, and `bleSdkFeatureReady` for `FEATURE_POLAR_H10_EXERCISE_RECORDING` vs. `FEATURE_POLAR_OFFLINE_RECORDING`).
  4. *Deterministic Classification, Pre-Connect Alias Seeding & Legacy Un-Reconnected Fallback (`REQ-CON-049.4`)*:
     - `PolarOfflineGattCoordinator` SHALL classify Polar models by persisted `PolarDeviceType` / DIS `0x2A24` (`"H10"` vs. `"Verity Sense"`/`"Sense"`/`"OH1"`) / SDK feature precedence first (independent of user display name), and resolve canonical `polarDeviceId` (`0x2A25` / `PolarDeviceInfo.deviceId` -> `POLAR_ID_REGEX` -> `macAddress`).
     - `PolarOnlineStreamingBridge`, `PolarH10OfflineController` (including `syncPendingWorkout`), `PolarVeritySenseOfflineController`, `DeviceDataRepository`, and `EditDeviceDialog` MUST classify and match via `PolarOfflineGattCoordinator` hardware-backed helpers and seed `registerDeviceAliases(polarDeviceId, mac)` BEFORE `deviceConnected` (`ATT-8690` multiplexing & `ATT-8706` `RR_PRIORITY_PPG` vs. `RR_PRIORITY_ECG` parity).
     - Legacy name/regex fallback SHALL execute strictly when `PolarDeviceType == UNKNOWN` (un-reconnected paired rows) and yield automatically upon first reconnect once hardware metadata is persisted.
  5. *Localization Parity & Offline Test Hygiene (`REQ-CON-049.5`)*:
     - `Localization Parity: N/A - no UI strings` (`values*/strings*.xml` untouched).
     - All verification suites MUST execute offline (`Rule 5`) with `0` `app/src/main` source-text reads (`REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-SET-104.3`, `REQ-SET-108.1`, `REQ-SET-112.1/.2`, and `REQ-SET-118.2`. Targets `BluetoothConstants.java`, `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `BTSearchForNewDevicesEngine.java`, `MyBTLEDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `DeviceDataRepository.kt`, `EditDeviceDialog.kt`.
2. *Historical Origin & Commit Trace*: `53b53e87` (`ATT-7762`), `e7201bdb` (`ATT-7923`), `f4bef44c` (`ATT-8016`), `34b78941` (`ATT-8690`).
3. *Root Reason for Existing Formulation*: `ATT-7762` matched `"Polar H10"` by name; `ATT-7923` introduced `PolarOfflineCapabilityStore` (`offline_rec_` boolean) without a SQLite schema bump and relied on `!isPolarH10Name` to exclude H10; `ATT-8690` added dynamic MAC <-> 8-hex alias multiplexing at connect time.
4. *Preservation of Core Invariants*: Preserves legacy name/regex fallback strictly for un-reconnected rows until first reconnect; preserves synchronous schema-free `PolarOfflineCapabilityStore`, `Devices.db` v14 (`DevicesDatabaseManagerMigrationV14Test`), `ATT-8690` singleton & callback multiplexing, `RR_PRIORITY_ECG` vs. `RR_PRIORITY_PPG` (`DeviceManager.java`), and `EditDevicePolarBackupTest` / `EditDevicePolarLiveImuTest`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Polar H10 renamed `"Chest Strap"` with persisted PMD+PFTP UUIDs and DIS `0x2A24="H10"` + `0x2A25="0A1B2C3D"`,
• *When* inspected across `PolarOfflineGattCoordinator`, `PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `PolarOnlineStreamingBridge`, `DeviceDataRepository`, and `EditDeviceDialog`,
• *Then* it classifies deterministically as `H10` (never Verity Sense), uses `0A1B2C3D` with pre-connect alias seeding, and retains `RR_PRIORITY_ECG`.
• *Given* a Polar Verity Sense or OH1 renamed `"Armband"` with persisted DIS `0x2A24` or `FEATURE_POLAR_OFFLINE_RECORDING`,
• *When* `PolarOnlineStreamingBridge` initializes prior to `deviceConnected`,
• *Then* `isVeritySense == true` (`RR_PRIORITY_PPG`), `registerDeviceAliases(polarDeviceId, mac)` is already registered pre-connect, and offline/IMU routing succeeds.
• *Given* a legacy paired row with `PolarDeviceType == UNKNOWN`,
• *When* evaluated prior to first reconnect,
• *Then* legacy name/regex fallback preserves existing behavior until first reconnect persists DIS/UUID metadata.

**Invariants**: `Devices.db` `DB_VERSION = 14` untouched; `PolarOfflineCapabilityStore` synchronous & schema-free; `ATT-8690` ref-counted singleton & multiplexing preserved; `RR_PRIORITY_ECG` vs. `RR_PRIORITY_PPG` parity preserved; legacy pre-reconnect fallback preserved; `N/A - no UI strings`; `REQ-PRO-049.1` offline tests with zero `src/main` reads.


### REQ-TRK-087: Remove Redundant Zonen-Balken (`showGauge`) Switch from HRV Tab in ZonesSettingsActivity [Verified]
- **Tests**: `TST-TRK-087`
- **System Requirement(s)**: `SYSREQ-TRK-006`
- **Priority**: Low
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`
- **Rationale**: After `ATT-8498` (`REQ-UI-2783`) placed `ZoneDisplayOptions` (including `R.string.zone_display_gauge` for `HRV_DFA_A1`) inside `EditSensorFieldDialog` and `SensorGridScreen` via `ZoneDisplayConfigSection` (`SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`), `HrvDfaA1SettingsContent` (tab 3 in `ZonesSettingsActivity.kt`) still rendered an extra `ZoneDisplayToggleRow(R.string.zone_display_gauge)` switch below `ZoneDisplayOptionsToggleList`, creating a duplicate global toggle inconsistent with tabs 0–2 (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`, deprecated under `REQ-TRK-046.4`) and misleading because live cockpit gauge bars are gated per tile (`config.showBar`, `ATT-8712`, refining `REQ-TRK-061.4` and `REQ-UI-2783.3`).
- **Description**:
  1. *HRV Tab Display Options Parity (`REQ-TRK-087.1`)*:
     - `HrvDfaA1SettingsContent` (`ZonesSettingsActivity.kt`) SHALL remove the `HorizontalDivider` and `ZoneDisplayToggleRow(label = stringResource(R.string.zone_display_gauge), ...)` below `ZoneDisplayOptionsToggleList(..., showGaugeToggle = false)`, rendering strictly the 4 standard display toggles (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`) in 1:1 parity with `SettingsScreenContent` (tabs 0–2) and `REQ-TRK-046.4`.
     - `HrvDfaA1SettingsContent` SHALL set `previewFieldState.showBar = true` unconditionally (matching `SettingsScreenContent`), keeping the 3-segment live `SensorFieldView` gauge preview visible while editing thresholds.
  2. *Preservation of Tile/Tab Config, DataStore & Behavioral Compose Verification (`REQ-TRK-087.2`)*:
     - `ZoneDisplayConfigSection` (`ZoneDisplayOptionsControls.kt`, used by `EditSensorFieldDialog` and `SensorGridScreen`) SHALL retain `showGaugeToggle = (selectedProfile == ZoneProfileTarget.HRV_DFA_A1)`, `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`, and `R.string.zone_display_gauge` across all 9 locales (`Localization Parity: N/A - no UI strings added, modified, or removed`).
     - Parity and preview rendering SHALL be verified via behavioral Robolectric Compose `createComposeRule()` UI tests with zero `app/src/main/` file reads (`REQ-PRO-049.1`) alongside updated `HrvDfaA1SettingsContractTest`, `EditSensorFieldZoneDisplayTest`, `TrackingTabConfigZoneDisplayTest`, and `Rule 8.5` Light/Dark PNG inspection.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-TRK-087` refining `REQ-TRK-061.4` and `REQ-UI-2783.3`, reconciling with `REQ-TRK-046.4` (`ZonesSettingsActivity.kt`).
2. *Historical Origin & Commit Trace*: `ATT-4841`/`ATT-4927` (`REQ-TRK-046.4`) deprecated the global `showGauge` toggle in `ZonesSettingsActivity`; `ATT-7742` (`5e30863e`) re-added `zone_display_gauge` to `HrvDfaA1SettingsContent`; `ATT-8498` (`d7dec447`) moved `ZoneDisplayOptions` (including `HRV_DFA_A1` `showGauge`) into `EditSensorFieldDialog` & `SensorGridScreen` while keeping `ZonesSettingsActivity` lines solely for `HrvDfaA1SettingsContractTest`.
3. *Root Reason for Existing Formulation*: Interim HRV preview toggle (`ATT-7742`) prior to `ATT-8498` placing `ZoneDisplayOptions` in `EditSensorFieldDialog` & `SensorGridScreen`.
4. *Preservation of Core Invariants*: Preserves 4-toggle `ZoneDisplayOptionsToggleList` + live `SensorFieldView` preview (`showBar = true`) across all 4 `ZonesSettingsActivity` tabs, `EditSensorFieldDialog`/`SensorGridScreen` `showGauge` toggle & `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`, `ATT-8105` slider gesture isolation, $T_1 \ge T_2 + 0.05f$, and 9-locale `strings*.xml` parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZoneSettingsScreen` on tab 3 (`HRV (DFA α1)`),
• *When* `HrvDfaA1SettingsContent` renders,
• *Then* the 4 `ZoneDisplayOptionsToggleList` toggles (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`) are displayed, `R.string.zone_display_gauge` (*Zonen-Balken*) is absent, and `SensorFieldView` preview renders with `showBar = true` regardless of `displayOptions.showGauge`.
• *Given* `EditSensorFieldDialog` or `SensorGridScreen` (`ScreenMode.CONFIGURATION`) with `HRV_DFA_A1`,
• *When* rendered,
• *Then* `R.string.zone_display_gauge` remains displayed and persists to `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`.

**Invariants**: 4-toggle `ZoneDisplayOptionsToggleList` across all 4 tabs; `previewFieldState.showBar = true`; `ZoneDisplayConfigSection` & `HRV_DFA_A1_ZONE_SHOW_GAUGE` untouched; `ATT-8105` slider gesture isolation & $T_1 \ge T_2 + 0.05f$ intact; `REQ-PRO-049.1` zero `src/main` reads in new/modified Compose UI tests; `Localization Parity: N/A - no UI strings`.

### REQ-CON-050: Live Polar-IMU Toggle Handoff, Shared-Session Recovery & Continuous HR/Raw-Telemetry Delivery [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-050`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`, `SYSREQ-DEX-007`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BANALService.java`, `DeviceDataRepository.kt`, `DeviceManager.java`, `MyBTLEDevice.java`, `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinator.kt`, `PolarRawTelemetryRecorder.kt`
- **Rationale**: Toggling `polarLiveImuEnabled` while a Polar H10 or Verity Sense is already connected leaves `DeviceManager` retaining the old HRM device because `DeviceDataRepository.updateDevice()` emits no Live-IMU change broadcast and `createNewBluetoothLEDevice()`/`createPairedRemoteDevices()` skip existing `mMyRemoteDevices` keys. Furthermore, async GATT close (`MyBTLEDevice.disconnectFromGatt()` / `PolarOnlineStreamingBridge.shutDown()`) races replacement connection startup, `PolarOnlineStreamingBridge.connectPersistentSession()` misses `SESSION_OPEN` / already-ready features on the shared `PolarBleApi` singleton, dynamic `polarId` flipping mid-session (`macAddress` -> 8-hex) breaks `startHrStreaming`/`requestStreamSettings`, and `deviceDisconnected` leaves stale `hrJob`/`accJob`/`gyroJob` running—collectively dropping `HR`/`RR_INTERVAL` and leaving `files/raw_sensor/` empty (`ATT-8714`, refining `REQ-SET-112.1/.2`, `REQ-CON-045.2`, and `REQ-CON-049.4`):
- **Description**:
  1. *Live `polarLiveImuEnabled` Toggle Broadcast & Ordered Handoff (`REQ-CON-050.1`)*:
     - `DeviceDataRepository.updateDevice()` SHALL emit a dedicated broadcast (`BANALService` action with `deviceId` and `polarLiveImuEnabled` extras) whenever `polarLiveImuEnabled` changes, both when a workout is idle and when a workout is active.
     - Upon receiving the Live-IMU toggle signal (for paired BLE HRM devices at `API >= 33`), `DeviceManager` SHALL detach `RrBeatSource`, remove and cleanly stop the active HRM instance (`BTLEHeartRateDevice` or `PolarOnlineStreamingBridge`), wait/yield until the prior GATT link or Polar SDK session release completes before activating the replacement Polar device via `createRemoteDevice()`, re-attach `RrBeatSource` / `FilterManager` / search queue, and trigger connection/search.
  2. *Shared `PolarBleApi` `SESSION_OPEN` & Already-Ready Feature Synchronization (`REQ-CON-050.2`)*:
     - When `PolarOnlineStreamingBridge.connectPersistentSession()` connects on the shared `PolarOfflineGattCoordinator` / `PolarBleApi` singleton and `connectToDevice(...)` attaches to an already-open (`SESSION_OPEN`) session where `deviceConnected` or `bleSdkFeatureReady` is not re-emitted, the bridge SHALL synchronize connected state (`isConnectedFlag`, `registerSensors()`, `activeBridges`, `PolarRawTelemetryRecorder` attachment, `notifyStopSearching(true)`) and check/start already-ready `FEATURE_HR` and `FEATURE_POLAR_ONLINE_STREAMING` streams.
  3. *Consistent `polarBleSessionId` & Disconnect Job Hygiene (`REQ-CON-050.3`)*:
     - `PolarOnlineStreamingBridge` MUST retain the exact `polarBleSessionId` used to open `connectToDevice(...)` for all in-session SDK calls (`startHrStreaming`, `requestStreamSettings`, `startAccStreaming`, `startGyroStreaming`, `stopStreaming`, `disconnectFromDevice`), preventing mid-session `macAddress`-to-8-hex `polarId` flips from breaking Polar SDK session lookup.
     - `PolarOnlineStreamingBridge.sdkCallback.deviceDisconnected()` MUST cancel stale `hrJob`, `accJob`, and `gyroJob` coroutines and reset streaming flags before automatic reconnection re-establishes them.
  4. *Continuous `HR`/`RR_INTERVAL` & `PolarRawTelemetryRecorder` Sidecar Capture (`REQ-CON-050.4`)*:
     - Across both `polarLiveImuEnabled` enable and disable transitions, `ProxySensor` / `FilterManager` / `RrBeatSource` arbitration SHALL continue delivering `HR` and `RR_INTERVAL`, and an active `PolarRawTelemetryRecorder` session (`TrackerService` `files/raw_sensor/*.polar_raw.csv.gz`) SHALL attach on bridge readiness and persist streamed frames (`rowCount > 0`).
  5. *Localization & Hardware Verification Boundary (`REQ-CON-050.5`)*:
     - `Localization Parity: N/A - no UI strings` (`values*/strings*.xml` untouched).
     - Phase 2 verification SHALL execute via offline unit/Robolectric tests (`Rule 7.6`, `REQ-PRO-049.1`); physical Pixel 10 + Polar Verity Sense / H10 field verification remains PO-executed/optional and SHALL NOT be synthesized.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-SET-112.1`, `REQ-SET-112.2`, `REQ-CON-045.2`, and `REQ-CON-049.4`. Targets `BANALService.java`, `DeviceDataRepository.kt`, `DeviceManager.java`, `MyBTLEDevice.java`, `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinator.kt`, and `PolarRawTelemetryRecorder.kt`.
2. *Historical Origin & Commit Trace*: `f4bef44c` (`ATT-8016`), `34b78941` (`ATT-8690`), `9f9e5967` (`ATT-8717`).
3. *Root Reason for Existing Formulation*: `ATT-8016` assumed `polarLiveImuEnabled` is configured before connecting; `ATT-8690` / `ATT-8717` assumed `connectToDevice` starts from `SESSION_CLOSED` and emits fresh `deviceConnected` / `bleSdkFeatureReady` events with a static `polarId`.
4. *Preservation of Core Invariants*: Preserves `< API 33` `BTLEHeartRateDevice` fallback, `ProxySensor` continuity (`REQ-TRK-055.1`), single-GATT ref-counting (`REQ-SET-118.3`), `ACC` channel mutual exclusion (`REQ-SET-112.3`), `RR_PRIORITY_ECG` / `RR_PRIORITY_PPG` arbitration (`REQ-TRK-085`), and `PolarRawTelemetryRecorder` empty-file deletion (`rowCount == 0`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a connected Polar H10 or Verity Sense running on `BTLEHeartRateDevice` (idle or during an active workout),
• *When* `DeviceDataRepository.updateDevice()` persists `polarLiveImuEnabled = true`,
• *Then* `DeviceManager` receives the Live-IMU change broadcast, completes `BTLEHeartRateDevice` GATT close before opening `PolarOnlineStreamingBridge`, preserves `HR`/`RR_INTERVAL` delivery via `ProxySensor`/`RrBeatSource`, and records frames into `PolarRawTelemetryRecorder`.
• *Given* an already-open (`SESSION_OPEN`) shared `PolarBleApi` session opened by `macAddress` with `FEATURE_HR` and `FEATURE_POLAR_ONLINE_STREAMING` ready,
• *When* `PolarOnlineStreamingBridge.connectPersistentSession()` runs and later receives `deviceConnected` (with 8-hex `deviceId`) or `deviceDisconnected`,
• *Then* the bridge immediately syncs connected state and starts HR/IMU streams using the consistent `polarBleSessionId`, and cancels `hrJob`/`accJob`/`gyroJob` cleanly on disconnect.
• *Given* an active `PolarOnlineStreamingBridge` when `polarLiveImuEnabled` is toggled to `false`,
• *When* `DeviceManager` performs the reverse handoff,
• *Then* `PolarOnlineStreamingBridge` shuts down cleanly before `BTLEHeartRateDevice` connects and resumes `HR`/`RR_INTERVAL` delivery.

**Invariants**: `< API 33` fallback preserved; `ProxySensor` & `RrBeatSource` continuity preserved; single-GATT ref-counting (`REQ-SET-118.3`) & `ACC` mutex (`REQ-SET-112.3`) preserved; `PolarRawTelemetryRecorder` `rowCount == 0` cleanup preserved; `Devices.db` v14 untouched; `N/A - no UI strings`; `Rule 7.6` offline verification boundary respected.

### REQ-CON-051: Non-Blocking BLE Search-Queue Timeout Handoff & Decoupled Mid-Session Background Reconnect [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-051`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java`, `app/src/main/java/com/atrainingtracker/banalservice/devices/MyRemoteDevice.java`, `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java`, `docs/architecture/detailed_design/my_btle_device.md`, `docs/architecture/detailed_design/banal_service.md`
- **Rationale**: When resuming a workout (`TrackingMode.TRACKING` or `PAUSED`) or searching mid-workout, `MyBTLEDevice.isMidSessionOrWorkoutActive()` returns `true` for every paired BLE sensor—including never-connected offline peripherals. Upon `mSearchTimeoutRunnable` expiration, `MyBTLEDevice` schedules `mReconnectRunnable` and `autoConnect = true` without resetting `mState` from `State.SEARCHING` or invoking `notifyStopSearching(false)`. `DeviceManager.cMyRemoteDeviceCurrentlySearchingFor` deadlocks forever on the first offline BLE sensor (`BANALService.isSearching() == true`), starving all subsequent sensors in `mSearchQueue` (`ATT-8998`, Parent Epic `ATT-5910`, refining `REQ-CON-020.2`, `REQ-CON-020.3`, `REQ-CON-036.1`, and `REQ-CON-036.2`):
- **Description**:
  1. *Immediate Sequential Search-Queue Release on Timeout (`REQ-CON-051.1`)*:
     - When `MyBTLEDevice.mSearchTimeoutRunnable` expires without receiving data (`!isReceivingData()`), `MyBTLEDevice` SHALL always transition `mState` out of `State.SEARCHING` (`State.DISCONNECTED`) and invoke `notifyStopSearching(false)` (clearing `MyRemoteDevice.mSearching = false` and broadcasting `BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT`), regardless of `TrackingMode` or `mHasConnectedInSession`.
     - `DeviceManager.mSearchingStoppedForOneReceiver` SHALL clear `cMyRemoteDeviceCurrentlySearchingFor = null` and invoke `searchForNextRemoteDevice()` so subsequent paired sensors in `mSearchQueue` (e.g., a second HRM after an unavailable BLE Radar) are searched immediately and `BANALService.isSearching()` returns `false` once the queue drains.
  2. *Decoupled Background Reconnect Without Queue Blocking or `tries <= 1` Teardown (`REQ-CON-051.2`)*:
     - Mid-session / active-workout background reconnection (`mReconnectRunnable` exponential backoff `1s -> 2s -> 4s -> 8s -> 16s` and persistent `autoConnect = true` per `REQ-CON-036`) SHALL be decoupled from `DeviceManager` search-queue ownership: background reconnect attempts MUST NOT re-enter `MyRemoteDevice.mSearching = true`, MUST NOT block `cMyRemoteDeviceCurrentlySearchingFor`, and MUST NOT stall a concurrent `startSearchForPairedDevices()` / `searchForNextRemoteDevice()` (`next.isSearching()`).
     - `MyBTLEDevice` SHALL preserve `triggerGattConnect(dev, true)` / `onScanResult()` attachment during background reconnect without keeping `mState == State.SEARCHING` on the active queue slot, and `shutDown()` MUST cancel all pending timers and close GATT cleanly.
     - In `DeviceManager.mSearchingStoppedForOneReceiver`, when `!success` and `tries == null || tries <= 1`, `DeviceManager` MUST NOT call `shutDown()` or remove from `mMyRemoteDevices` any device persisting active mid-session / active-workout background reconnect, while never-connected offline devices in `TrackingMode.READY` (`!hasConnectedInSession()`) MUST still invoke `shutDown()` and removal (`REQ-CON-036.1`).
  3. *Monolith LoC Ceiling, Design Parity & Localization Invariant (`REQ-CON-051.3`)*:
     - `DeviceManager.java` MUST remain strictly `<= 1,708` LoC (`ArchitectureConformanceContractTest`); `DOC-ARC-DD-BTLE` (`my_btle_device.md`) and `DOC-ARC-DD-BANAL` (`banal_service.md`) SHALL be synchronized (`DetailedDesignParityContractTest`); `SYSREQ-TRK-002` requires no new `SYS.2`/`SYS.3` interfaces (`REQ-PRO-131.4`); `Localization Parity: N/A - no UI strings`; unit tests SHALL execute offline with `0` new `src/main` file reads (`REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-CON-051`, refining `REQ-CON-020.2`, `REQ-CON-020.3`, `REQ-CON-036.1`, and `REQ-CON-036.2`. Targets `MyBTLEDevice.java`, `MyRemoteDevice.java`, `DeviceManager.java`, `my_btle_device.md`, and `banal_service.md`.
2. *Historical Origin & Commit Trace*: `e84383ee` (`ATT-4007`, queue & backoff), `22d8c54d` (`ATT-5489`, scan assist & configurable timeout), `3dc19303` (`ATT-7755`, `isMidSessionOrWorkoutActive()` persistent reconnect).
3. *Root Reason for Existing Formulation*: `ATT-7755` omitted `notifyStopSearching(false)` and state reset in `mSearchTimeoutRunnable` under `isMidSessionOrWorkoutActive()` assuming `cMyRemoteDeviceCurrentlySearchingFor == null` during mid-workout disconnects, unaware that workout resume and mid-workout searches traverse `mSearchQueue` while `TrackingMode` is `TRACKING`/`PAUSED` and `DeviceManager` tears down devices when `tries <= 1`.
4. *Preservation of Core Invariants*: Preserves offline startup battery bounding (`TrackingMode.READY && !mHasConnectedInSession` shuts down after bounded tries), preserves mid-workout exponential backoff (`1s..16s`) and `autoConnect = true` (`REQ-CON-036`), preserves `DeviceManager` alien-broadcast isolation (`REQ-CON-020.3`) and `mGattLock` / `mSearchQueue` thread safety, preserves `MyANTDevice` and `PolarOnlineStreamingBridge`, and enforces `DeviceManager.java <= 1,708` LoC.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TrackingMode.TRACKING` (or `PAUSED`) with `[Offline BLE Radar, Online BLE HRM]` queued in `DeviceManager`,
• *When* `MyBTLEDevice.mSearchTimeoutRunnable` expires on the offline BLE Radar,
• *Then* `MyBTLEDevice` transitions out of `State.SEARCHING` and calls `notifyStopSearching(false)`, `DeviceManager` advances immediately to search the BLE HRM, and `BANALService.isSearching()` becomes `false` once `mSearchQueue` drains.
• *Given* `DeviceManager.mSearchingStoppedForOneReceiver` receiving `success = false` with `tries <= 1` for a device persisting active-workout / mid-session background reconnect vs. an offline device in `TrackingMode.READY`,
• *When* the broadcast is processed,
• *Then* the persisting device is NOT shut down or removed from `mMyRemoteDevices` and continues background `mReconnectRunnable` (`2s -> 4s -> 8s -> 16s`) + `autoConnect = true` without setting `mSearching = true` or stalling subsequent `startSearchForPairedDevices()`, whereas the `TrackingMode.READY` offline device IS shut down and removed.

**Invariants**: Offline startup battery bounding (`REQ-CON-036.1`) preserved; mid-workout exponential backoff (`1s..16s`) & `autoConnect = true` preserved; `mGattLock` & `mSearchQueue` thread safety preserved; `DeviceManager.java <= 1,708` LoC enforced; `MyANTDevice` & `PolarOnlineStreamingBridge` untouched; `0` new `src/main` test reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

### REQ-CON-052: Extended Exponential Sensor Reconnect / Re-Search Backoff & Search Settings Configuration [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-052`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorReconnectBackoffPolicy.kt`, `SensorSearchSettings.kt`, `MyBTLEDevice.java`, `DeviceManager.java`, `PolarOnlineStreamingBridge.kt`, `SearchSettingsDialog.kt`, `SensorSearchTuningSection.kt`, `app/src/main/res/xml/prefs_search.xml`, `app/src/main/res/values*/strings_devices.xml`
- **Rationale**: During active workouts, `MyBTLEDevice` caps exponential reconnect backoff at `MAX_RECONNECT_DELAY_MS = 16000L` indefinitely, wasting radio/battery budget during longer out-of-range intervals, while `DeviceManager` (`tries <= 1`) and `PolarOnlineStreamingBridge` (Live-IMU mode) stop searching permanently after `mMyRemoteDeviceTries` (`default = 3`) is exhausted (`ATT-8965`, Parent Epic `ATT-5910`, refining `REQ-CON-020.2`, `REQ-CON-036.1`, and `REQ-CON-051.2`):
- **Description**:
  1. *Extended Exponential Backoff Progression (`REQ-CON-052.1`)*:
     - `SensorReconnectBackoffPolicy` SHALL compute reconnect/re-search delays starting from `INITIAL_RECONNECT_DELAY_MS = 1_000 ms`, doubling sequentially across `1 s -> 2 s -> 4 s -> 8 s -> 16 s -> 32 s -> 64 s -> 128 s -> <finalPeriodMs>` (default `300_000 ms` = `5 min`), capped at `<finalPeriodMs>`, and resetting to `1_000 ms` upon successful connection / service discovery.
     - `MyBTLEDevice.INITIAL_RECONNECT_DELAY_MS = 1000L` and `MyBTLEDevice.MAX_RECONNECT_DELAY_MS = 16000L` static constants MUST remain unchanged for `SYSREQ-TRK-002` contract parity (`SystemLiveTrackingRequirementsContractTest`), while runtime reconnect progression delegates to `SensorReconnectBackoffPolicy`.
  2. *Consistent Periodic Re-Search Coordination Across BLE, Polar SDK & DeviceManager (`REQ-CON-052.2`)*:
     - When periodic long-term re-search is enabled (default `true`), `MyBTLEDevice`, `PolarOnlineStreamingBridge`, and `DeviceManager` (on `tries <= 1` search exhaustion) SHALL schedule periodic re-search / reconnect retries using `SensorReconnectBackoffPolicy` without deadlocking `DeviceManager.cMyRemoteDeviceCurrentlySearchingFor` (`REQ-CON-051.1`).
     - All pending periodic re-search timers/coroutines MUST cancel cleanly upon sensor connection, `startSearchForPairedDevices()`, `removeAndStopRemoteDevice()`, and `shutDown()`.
     - When periodic long-term re-search is disabled (`false`), `DeviceManager` SHALL NOT schedule periodic long-term re-search after `tries <= 1` exhaustion (`shutDown()` + removal preserved for `READY` / exhausted `PolarOnlineStreamingBridge` per `REQ-CON-036.1`), and mid-workout `MyBTLEDevice` SHALL stay capped at `MAX_RECONNECT_DELAY_MS = 16000L` (`SYSREQ-TRK-002`).
     - `DeviceManager.java` MUST remain `<= 1,708` LoC and `TrainingApplication.java` MUST remain `<= 2,135` LoC (`ArchitectureConformanceContractTest`).
  3. *Dual Search Settings UI, Preference Defaults & 9-Language Localization Parity (`REQ-CON-052.3`)*:
     - `SensorSearchSettings` (`PreferenceManager.getDefaultSharedPreferences`) and `app/src/main/res/xml/prefs_search.xml` SHALL persist the periodic long-term re-search toggle (default `true`) and configurable discrete final steady-state period (`60 s`, `120 s`, `300 s`, `600 s`; default `300 s` = `5 min`).
     - BOTH `SearchSettingsDialog.kt` AND `SensorSearchTuningSection.kt` SHALL expose both controls identically (`SensorSearchTuningSection.kt` MUST use `TuningToggleItem` and `TuningSegmentedButtonRow` per `docs/design_guidelines.md` §1.4; zero raw `Switch(`).
     - All new user-facing UI strings SHALL be added under `app/src/main/res/values*/strings_devices.xml` with complete 9-language parity (`EN, DE, ES, FR, IT, JA, NL, PL, PT`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-CON-052`, refining `REQ-CON-020.2`, `REQ-CON-036.1`, and `REQ-CON-051.2`. Targets `MyBTLEDevice.java`, `DeviceManager.java`, `PolarOnlineStreamingBridge.kt`, `SearchSettingsDialog.kt`, `SensorSearchTuningSection.kt`.
2. *Historical Origin & Commit Trace*: `0234bfc0` (`ATT-4007`, `16000L` cap), `3dc19303` (`ATT-7755`, `isMidSessionOrWorkoutActive()`), `ATT-8998` (`REQ-CON-051`, search-queue timeout release & `1,708` LoC cap).
3. *Root Reason for Existing Formulation*: `16000L` bounded GATT retry storms mid-workout while `tries <= 1` shutdown conserved battery on offline startup sensors; `MAX_RECONNECT_DELAY_MS = 16000L` is verified via reflection by `SystemLiveTrackingRequirementsContractTest`.
4. *Preservation of Core Invariants*: Preserves `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L` static constant, preserves `REQ-CON-051` non-blocking timeout release, restores bounded pre-workout shutdown when periodic re-search is disabled, and enforces `DeviceManager.java <= 1,708` LoC + `TrainingApplication.java <= 2,135` LoC.

**Acceptance Criteria (Given-When-Then)**:
• *Given* periodic long-term re-search is enabled with final period `5 min` (`300 s`),
• *When* consecutive connection/search retries fail in `MyBTLEDevice`, `PolarOnlineStreamingBridge`, or `DeviceManager`,
• *Then* retry delays progress `1s -> 2s -> 4s -> 8s -> 16s -> 32s -> 64s -> 128s -> 300s` (capped at `300s`) and reset to `1s` on connect, while pending timers cancel on `shutDown()` / `startSearchForPairedDevices()` / `removeAndStopRemoteDevice()`.
• *Given* periodic long-term re-search is disabled (`false`),
• *When* `DeviceManager` exhausts `mMyRemoteDeviceTries` for an idle or `PolarOnlineStreamingBridge` device,
• *Then* no periodic re-search is scheduled and the device is shut down and removed cleanly, while `MyBTLEDevice` mid-workout stays capped at `16 s`.
• *Given* `SearchSettingsDialog` or `SensorSearchTuningSection` rendered via Compose,
• *When* changing the periodic re-search toggle or final period (`1m`, `2m`, `5m`, `10m`),
• *Then* shared preferences update immediately in sync across both UIs with 9-language localization parity.

**Invariants**: `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L` preserved; `DeviceManager.java <= 1,708` LoC & `TrainingApplication.java <= 2,135` LoC enforced; `REQ-CON-051` search-queue unblocking preserved; `MyANTDevice` untouched; `0` new `src/main` test reads (`REQ-PRO-049.1`); 9-locale parity in `strings_devices.xml`.

### REQ-TRK-088: Radar Approach Speed Display Units in MyHelper & Sensor Unit Dictionary Sync [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-088`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/MyHelper.java`, `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`, `docs/architecture/sensor_units.md`
- **Rationale**: During on-device review alongside `ATT-8440`, radar speed units displayed `m/s` instead of `km/h` (Metric) / `mile/h` (Imperial) when resolved via `MyHelper`. While `RadarTrafficSummaryCard.kt` formats via `SpeedFormatter.format_with_units()`, `MyHelper.getUnitsId(SensorType)` (`L152`) and `MyHelper.getShortUnitsId(SensorType)` (`L162`) omit `SensorType.RADAR_APPROACH_SPEED_mps` from the `SPEED_mps` switch arm and fall through to `sensorType.getUnitId()` = `R.string.units_speed_basic` (`"m/s"`), diverging from `SpeedFormatter` across `TrackingViewModel`, `EditSensorFieldDialog`, `SensorSourceDialog`, `GetMergedDevicesUseCase`, `PebbleService`, `WorkoutDataMapper`, and `WorkoutRepository.kt:3128` (`ATT-8676`, Epic `ATT-1286`, refining `REQ-TRK-083.1` and `REQ-CON-028`):
- **Description**:
  1. *`MyHelper` Radar Speed Unit Mapping (`REQ-TRK-088.1`)*:
     - `MyHelper.getUnitsId(SensorType)` SHALL map `SensorType.RADAR_APPROACH_SPEED_mps` alongside `SensorType.SPEED_mps` to `getSpeedUnitNameId()`, returning `R.string.units_speed_metric` (`"km/h"`) under `MyUnits.METRIC` and `R.string.units_speed_imperial` (`"mile/h"`) under `MyUnits.IMPERIAL` — never falling through to `R.string.units_speed_basic` (`"m/s"`).
     - `MyHelper.getShortUnitsId(SensorType)` SHALL map `SensorType.RADAR_APPROACH_SPEED_mps` alongside `SensorType.SPEED_mps` to `getShortSpeedUnitNameId()`, returning `R.string.units_speed_short_metric` (`"km/h"`) under `MyUnits.METRIC` and `R.string.units_speed_short_imperial` (`"mi/h"`) under `MyUnits.IMPERIAL` — never falling through to `R.string.units_speed_basic` (`"m/s"`).
  2. *Unit Dictionary & `SensorType` Javadoc Synchronization (`REQ-TRK-088.2`)*:
     - `docs/architecture/sensor_units.md` (§1 line 76 `RADAR_APPROACH_SPEED_mps` row and §2 item 3) and `SensorType.java` (`RADAR_APPROACH_SPEED_mps` Javadoc, line 440) SHALL be updated to document `MyHelper.getUnitsId` / `MyHelper.getShortUnitsId` resolving `RADAR_APPROACH_SPEED_mps` to `km/h` / `mile/h` (`mi/h` short), while preserving `"Internal unit: {@code m/s}"` Javadoc (`verify_sensor_type_javadocs()`) and the `"MyHelper.getUnitsId"` contract token (`SensorTypeUnitsContractTest`).
  3. *Storage, Export, Scope & Localization Invariants (`REQ-TRK-088.3`)*:
     - `SensorType.RADAR_APPROACH_SPEED_mps` MUST preserve `internalUnit = "m/s"`, `ordinal()`, `name()`, and `@Deprecated getUnitId() == R.string.units_speed_basic`; SQLite storage and FIT/CSV raw `m/s` exports (`FitFileWriter.kt`, `CSVFileWriter.java`) MUST remain unchanged.
     - `RADAR_CLOSEST_DISTANCE_m`, `RADAR_PASSING_DISTANCE_m`, and `W_PRIME_BAL` (`docs/architecture/sensor_units.md` §1 & §2) MUST remain untouched; `WorkoutRepository.kt` (`<= 5,117` LoC) MUST remain untouched.
     - `Localization Parity: N/A - no UI strings` (`app/src/main/res/values*/strings_radar.xml` and `strings.xml` untouched).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-TRK-088`, refining `REQ-TRK-083.1` (`req_tracking_cockpit_part2.md:2467-2474`) and `REQ-CON-028` (`req_tracking_cockpit_part2.md:15-55`). Targets `MyHelper.java`, `SensorType.java`, and `docs/architecture/sensor_units.md`.
2. *Historical Origin & Commit Trace*: `ATT-5083` (`REQ-CON-028`) introduced `RADAR_APPROACH_SPEED_mps` with `SpeedFormatter` and `R.string.units_speed_basic`; `ATT-8579` (`367a412f`, `a3508e6b`, `f55c9fee`) created `REQ-TRK-083`, `sensor_units.md`, and `SensorTypeUnitsContractTest`; `ATT-8440` (`e3c278bb`) fixed `RadarTrafficSummaryCard.kt` via `SpeedFormatter.format_with_units()`.
3. *Root Reason for Existing Formulation*: `REQ-TRK-083.1` intentionally froze existing runtime behavior during documentation (`ATT-8579`) rather than altering `MyHelper.getUnitsId()`, explicitly noting the radar fallback discrepancy.
4. *Preservation of Core Invariants*: Preserves `RADAR_APPROACH_SPEED_mps.internalUnit = "m/s"`, `@Deprecated getUnitId() = R.string.units_speed_basic`, `ordinal()`/`name()`, SQLite & FIT/CSV raw `m/s` exports, `"MyHelper.getUnitsId"` contract token, `RADAR_CLOSEST_DISTANCE_m`/`RADAR_PASSING_DISTANCE_m`/`W_PRIME_BAL` untouched, `WorkoutRepository.kt` (`<= 5,117` LoC) untouched, and `Localization Parity: N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TrainingApplication.getUnit() == MyUnits.METRIC`,
• *When* `MyHelper.getUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)` and `MyHelper.getShortUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)` are invoked (including via `WorkoutDataMapper`, `WorkoutRepository`, or `TrackingViewModel`),
• *Then* `getUnitsId` returns `R.string.units_speed_metric` (`"km/h"`) and `getShortUnitsId` returns `R.string.units_speed_short_metric` (`"km/h"`), never `R.string.units_speed_basic` (`"m/s"`).
• *Given* `TrainingApplication.getUnit() == MyUnits.IMPERIAL`,
• *When* `MyHelper.getUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)` and `MyHelper.getShortUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)` are invoked,
• *Then* `getUnitsId` returns `R.string.units_speed_imperial` (`"mile/h"`) and `getShortUnitsId` returns `R.string.units_speed_short_imperial` (`"mi/h"`), never `R.string.units_speed_basic` (`"m/s"`).
• *Given* `SensorTypeUnitsContractTest`, `SystemArchitectureDesignContractTest`, `SourceTextReadingAuditContractTest`, and `verify_requirement_governance.py`,
• *When* executed after updating `MyHelper.java`, `SensorType.java`, and `docs/architecture/sensor_units.md`,
• *Then* all contract and governance checks pass with `0` new `app/src/main/` source-text reads (`REQ-PRO-049.1`).

**Invariants**: `internalUnit = "m/s"`, `ordinal()`, `name()`, and `@Deprecated getUnitId() = R.string.units_speed_basic` unchanged; FIT/CSV raw `m/s` exports unchanged; `RADAR_CLOSEST_DISTANCE_m`/`RADAR_PASSING_DISTANCE_m`/`W_PRIME_BAL` unchanged; `WorkoutRepository.kt` untouched (`<= 5,117` LoC); `Localization Parity: N/A - no UI strings`; `0` new `src/main` test reads (`REQ-PRO-049.1`).

### REQ-FIL-026: 1-Hz Barometric Ingestion Rate-Matching & Adaptive Cyclic Jitter Variance Scaling in ATT Fused Altitude/Slope [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-026`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/BarometricAltitudeFilter.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`
- **Rationale**: Hardware `Sensor.TYPE_PRESSURE` registered with `SensorManager.SENSOR_DELAY_NORMAL` dispatches `handlePressureMeasurement()` at `~5 Hz` (`~200 ms`), shrinking `BarometricAltitudeFilter.medianWindowSize = 7` from `7 s` to `~1.4 s` and executing `ekf.updateBarometer()` `5x` per `1-Hz` `ekf.predict()` with static `DEFAULT_VAR_BARO = 0.25` (effective 1-s variance `0.05 m^2`). On jeans-pocket cycling rides (`Zum Bäcker #57`), `1.0–1.5 Hz` pedaling pressure oscillations (`±0.8..1.2 m`) and `1–2 s` compression pulses (`±8 m`) couple into `±10–12%` flat-road `SLOPE` swings and `+26.89 m` ascent inflation. Rate-matching live barometric ingestion to `1 Hz` and scaling effective `varBaro` during cyclic sign-reversing jitter eliminates pocket-induced oscillations while preserving steady climbs (`ATT-8881`, refining `REQ-FIL-016.2`, `REQ-FIL-020.2`, `REQ-FIL-023.3`, and `REQ-TRK-057.1`):
- **Description**:
  1. *1-Hz Barometric Rate-Matching, First-Sample Init & Deterministic Flushing (`REQ-FIL-026.1`)*:
     - `AltitudeAndSlopeDevice_ATTFused` SHALL aggregate `handlePressureMeasurement(pressureHpa, timestampMs)` samples into `1-Hz` (`1,000 ms` bucket/window) representative altitude observations before feeding `BarometricAltitudeFilter.filterSlewOnly()` (preserving `medianWindowSize = 7` true `~7 s` span and zero double-EMA lag per `REQ-FIL-016.2`), `ekf.updateBarometer()`, and `publishAltitudeAndMetrics()` in lockstep with `1-Hz` `calculateMetrics()` / `ekf.predict()`.
     - On the very first `handlePressureMeasurement()` call (`!ekf.isInitialized`), `AltitudeAndSlopeDevice_ATTFused` MUST initialize `ekf` and publish `mAltitudeSensor` immediately using that sample, using deterministic `timestampMs` arguments (never wall-clock time) for bucket/window boundaries.
     - `calculateMetrics(nowMs)` SHALL coordinate cleanly with any pending sub-second barometric buffer, and `shutDown()` MUST clear all sub-second aggregator state alongside `baroFilter.reset()` and `ekf.reset()`.
  2. *Adaptive Cyclic Pocket-Jitter Variance Scaling & `DEFAULT_VAR_BARO` Guard (`REQ-FIL-026.2`)*:
     - `KinematicAltitudeSlopeEkf` MUST preserve `DEFAULT_VAR_BARO = 0.25` (`(0.5 m)^2`) and 2-state initialization `p00 = varBaro` (`KinematicAltitudeSlopeEkfTest:264`).
     - During active barometric updates, `KinematicAltitudeSlopeEkf` SHALL detect short-term oscillatory/sign-reversing residual jitter and adaptively inflate effective `varBaro` so `±0.8..1.2 m` cyclic pocket-compression ripples at cycling speeds (`5–6 m/s`) keep flat-road `slopePercent` within `±2%` and prevent false `2.0 m` `ASCENT`/`DESCENT` hysteresis steps (`REQ-FIL-020.2`), without attenuating genuine monotonic climbs (`0.4–0.84 m/s`).
  3. *Sibling Isolation, Localization & Test Hygiene (`REQ-FIL-026.3`)*:
     - `AltitudeFromPressureDevice.java`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `SpeedAndLocationDevice_ATTFused.kt`, and `WorkoutRepository.kt` (`<= 5,117` LoC) MUST remain untouched (`Localization Parity: N/A - no UI strings`), and all new/modified unit tests SHALL pass offline with `0` `app/src/main` source-text reads (`REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-026` refining `REQ-FIL-016.2`, `REQ-FIL-020.2`, `REQ-FIL-023.3`, and `REQ-TRK-057.1` (linking `SYSREQ-TRK-003`, `SYSREQ-TRK-004`). Targets `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, and `KinematicAltitudeSlopeEkf.kt`.
2. *Historical Origin & Commit Trace*: `e079f7a9` (`ATT-6265`, 7-sample median + `1.2 m/s` slew), `75c1497e` / `33e60295` (`ATT-6279`, `ATTFused` + `filterSlewOnly()`), `8b0eba41` (`ATT-8081`, `2.0 m` hysteresis), `23317f58` / `e9766625` (`ATT-8575`, 3-state `baroBias` & 2-state `p00 = DEFAULT_VAR_BARO`), `eba66320` / `f6715226` (`ATT-8576`, standstill clearing & `< 1.2 m/s` slope damping).
3. *Root Reason for Existing Formulation*: `ATT-6265` calibrated `medianWindowSize = 7` assuming `1-Hz` sampling; `ATT-6279` added `filterSlewOnly()` without rate-matching `SENSOR_DELAY_NORMAL`; `ATT-8576` damped low-speed (`< 1.2 m/s`) slope coupling while leaving `5–6 m/s` cruising speeds exposed to 5-Hz pocket compression.
4. *Preservation of Core Invariants*: Preserves `filterSlewOnly()` zero double-EMA lag (`REQ-FIL-016.2`), `BarometricAltitudeFilter.medianWindowSize = 7`, `DEFAULT_VAR_BARO = 0.25` & 2-state `p00 = varBaro` (`KinematicAltitudeSlopeEkfTest:264`), immediate first-sample `ekf.isInitialized`, clean `shutDown()` reset, `2.0 m` hysteresis (`REQ-FIL-020.2`), 3-state `baroBias` (`REQ-FIL-022`), `@Synchronized` thread safety, and `AltitudeFromPressureDevice.java` / `ExtendedRtsAltitudeSlopeSmoother.kt` / `WorkoutRepository.kt` untouched.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `AltitudeAndSlopeDevice_ATTFused` receiving `5-Hz` (`200 ms` `timestampMs`) `TYPE_PRESSURE` events with `±1.2 m` cyclic pedaling oscillations and `±8 m` compression pulses over `60 s` on flat terrain (`DEM = 438.0 m`, `speed = 6.0 m/s`),
• *When* `handlePressureMeasurement()` and `1-Hz` `calculateMetrics()` process the stream (and a synthetic `2 km` `Zum Bäcker #57` `435..446 m` profile),
• *Then* barometric EKF ingestion is rate-matched to `1 Hz`, flat-road `slopePercent` stays within `±2%`, flat `ASCENT`/`DESCENT` stay `0.0 m`, and `2 km` total ascent/descent stay within `10–16 m`.
• *Given* `AltitudeAndSlopeDevice_ATTFused` receiving a single `handlePressureMeasurement(1013.25f, 1000L)` call or steady `+0.83..0.84 m/s` climbs (`tstFil020_3`, `tstFil022_3`, `tstFil023_3`),
• *When* initialized or climbing,
• *Then* the first sample initializes `ekf` and `mAltitudeSensor` immediately, `shutDown()` clears all aggregator state, `DEFAULT_VAR_BARO = 0.25` / 2-state `p00` remain intact, and real climbs accumulate full `22..27 m` ascent without lag (`Localization Parity: N/A - no UI strings`).

**Invariants**: `REQ-FIL-016.2` `filterSlewOnly()` zero double-EMA lag; `medianWindowSize = 7`; `DEFAULT_VAR_BARO = 0.25` & 2-state `p00 = varBaro`; immediate first-sample init; clean `shutDown()` reset; `REQ-FIL-020.2` `2.0 m` hysteresis; `REQ-FIL-022` `baroBias`; `AltitudeFromPressureDevice.java`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `WorkoutRepository.kt` (`<= 5,117` LoC) untouched; `@Synchronized` thread safety; `REQ-PRO-049.1` (`0` `src/main` reads); `N/A - no UI strings`.

### REQ-TRK-089: Full-Screen Radar Vehicle Markers with Non-Clipping Badges, V2 Size Icons & High-Contrast Framing [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-089`
- **System Requirement(s)**: `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarEdgeOverlay.kt`, `Color.kt`, `strings_radar.xml`
- **Rationale**: In `RadarEdgeSide.FULL_SCREEN` mode, `RadarFullScreenThreatOverlay` currently renders fixed `28×20.dp` / `32×22.dp` / `36×26.dp` text-only boxes with `9.sp` range labels and a `1.5.dp` white border, causing 3-digit distances (`128m`, `160m`) to clip against the pill edges, badges to wash out against light cockpit cards and map tiles, V2 `VehicleSizeCategory` variants to lack distinct vehicle icons, and `UNKNOWN_OR_LIGHT` targets to fall back to an untranslated `"Target"` accessibility prefix (`ATT-8733`, Parent Epic `ATT-1286`, refining `REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.6`, and complementing `REQ-TRK-073.2`):
- **Description**:
  1. *Non-Clipping Content-Sized Markers & Edge Clamping (`REQ-TRK-089.1`)*:
     - `RadarFullScreenThreatOverlay` SHALL replace fixed `28×20` / `32×22` / `36×26.dp` boxes with content-sized pill/capsule markers (`widthIn(min = ...)` / `wrapContentWidth()`) and generous padding, rendering bold tabular-numeral (`fontFeatureSettings = "tnum"`) distance text at `11–13.sp` so 2- and 3-digit distances (`15m`, `128m`, `160m`) never clip or wrap.
     - Marker positioning SHALL incorporate measured marker dimensions (or equivalent layout clamping/anchoring) so content-sized markers near horizontal (`0.15f`–`0.85f`) and vertical (`0.0f`–`1.0f`) extremes remain fully within `RadarFullScreenThreatOverlay` bounds without clipping or overflow, while preserving upward kinematics ($140\text{ m} / 175\text{ m} \to 0\text{ m}$) and optional subtle proximity perspective scaling / non-intrusive lane guides.
  2. *Distinct High-Contrast V2 Vehicle Icons, Theme Tokens & 9-Locale Accessibility (`REQ-TRK-089.2`)*:
     - Each full-screen marker SHALL render a distinct vector icon mapped to `VehicleSizeCategory`: `CAR` (passenger car icon, e.g., `Icons.Default.DirectionsCar`), `TRUCK_OR_BUS` (heavy-vehicle icon, e.g., `Icons.Default.LocalShipping` with larger marker scale), and `UNKNOWN_OR_LIGHT` (light/two-wheel/directional vehicle icon).
     - Markers SHALL apply subtle elevation (`shadowElevation` / `Surface`), dark scrim/outer contrast, and border framing legible across both Light/Dark themes and Map tiles, sourcing any new color tokens strictly from `Color.kt` (`TTColor`, zero inline `Color(0x...)` literals in composables per `ThemeColorCentralizationAuditTest`).
     - Accessibility `contentDescription` SHALL map `UNKNOWN_OR_LIGHT` to `R.string.radar_vehicle_size_light`, `CAR` to `R.string.radar_vehicle_size_car`, and `TRUCK_OR_BUS` to `R.string.radar_vehicle_size_truck`, eliminating the hardcoded `"Target"` fallback (`Localization Parity: PASS - 9/9 existing strings_radar.xml keys reused, zero new keys needed`).
  3. *Preserved Overlay & Contract Invariants (`REQ-TRK-089.3`)*:
     - Full-screen markers, lane guides, and container MUST attach zero `.clickable`, `.pointerInput`, or `.pointerInteropFilter` modifiers (`REQ-TRK-062.3` 100% touch pass-through for `HorizontalPager`, `GoogleMap`, and edge back gestures).
     - `RadarStatusBadgePill` SHALL remain absent during clear road (`ATT-8679`), `rememberInfiniteTransition` SHALL remain gated strictly on `overallThreatLevel == ThreatLevel.FAST_APPROACHING`, `LEFT`/`RIGHT` `RadarVerticalRail` dot geometry (`REQ-TRK-073.2`) MUST remain untouched, all `@Preview` functions MUST be declared `private` (`DeadCodeEliminationContractTest`), `RadarEdgeOverlayTest.kt` MUST perform `0` `app/src/main/` file reads (`SourceTextReadingAuditContractTest`), and Stage 5 verification MUST include `Rule 8.5` Light/Dark PNG capture.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-TRK-089`, refining `REQ-TRK-062` (`REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.6`) in `docs/requirements/req_tracking_cockpit_part2.md:138–198`, targeting `RadarEdgeOverlay.kt` (`RadarFullScreenThreatOverlay`), `Color.kt`, and `RadarEdgeOverlayTest.kt` while keeping `REQ-TRK-073.2` untouched.
2. *Historical Origin & Commit Trace*: `2a3baf12` (`ATT-5085`, initial edge overlay), `f2c2e15e` (`ATT-5096`, `VehicleSizeCategory` & `REQ-TRK-073`), `3206a504` (`ATT-7537`, introduced `RadarFullScreenThreatOverlay` & `RadarEdgeSide.FULL_SCREEN`), `49deebfb` (`ATT-8679`, removed clear-state green `RadarStatusBadgePill` and refined `REQ-TRK-062`).
3. *Root Reason for Existing Formulation*: `ATT-7537` reused compact rail-sized (`28–36.dp`) text-only boxes with `9.sp` font and `85%` opacity to avoid occluding underlying cockpit metrics before on-device screenshot inspection revealed 3-digit text clipping (`128m`), low white-border contrast over light cards/maps, missing V2 icons, and an untranslated `"Target"` string fallback.
4. *Preservation of Core Invariants*: Preserves 100% gesture pass-through (zero clickable/pointer modifiers on full-screen overlay/markers/guides), upward kinematics ($140\text{ m}/175\text{ m} \to 0\text{ m}$), RHT/LHT lateral mapping (`RadarOverlayMath.calculateFullScreenHorizontalFraction`), conditional `FAST_APPROACHING` pulsing animation, `ATT-8679` clear-badge suppression, `LEFT`/`RIGHT` `RadarVerticalRail` geometry (`REQ-TRK-073.2`), centralized `Color.kt` tokens, `private` `@Preview` visibility, and 9-language parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `RadarEdgeSide.FULL_SCREEN` with targets at `160m`, `128m`, and `15m` near lateral (`0.15f`/`0.85f`) or range (`0.0f`/`1.0f`) boundaries,
• *When* `RadarFullScreenThreatOverlay` composes,
• *Then* content-sized (`widthIn`/`wrapContentWidth`) markers render tabular `11–13.sp` bold distance labels without text clipping or container-bounds overflow.
• *Given* active V2 targets of categories `CAR`, `TRUCK_OR_BUS`, and `UNKNOWN_OR_LIGHT`,
• *When* `RadarFullScreenThreatOverlay` renders over Light, Dark, and Map-style backgrounds,
• *Then* distinct vehicle icons, elevated scrim/border contrast (sourced from `Color.kt`), and localized `radar_vehicle_size_car` / `_truck` / `_light` accessibility descriptions appear with zero `"Target"` fallback and zero inline `Color(0x...)` literals.
• *Given* `RadarFullScreenThreatOverlay` active with clear road vs. `APPROACHING` vs. `FAST_APPROACHING`,
• *When* inspected for gestures, animations, and rail geometry,
• *Then* full-screen markers/guides consume zero touch events, `RadarStatusBadgePill` stays absent when clear (`ATT-8679`), `rememberInfiniteTransition` composes strictly for `FAST_APPROACHING`, and `LEFT`/`RIGHT` `RadarVerticalRail` remains unchanged.

**Invariants**: Zero `.clickable`/`.pointerInput`/`.pointerInteropFilter` on full-screen markers/guides (`REQ-TRK-062.3`); `RadarStatusBadgePill` absent when clear (`ATT-8679`); `rememberInfiniteTransition` gated strictly on `FAST_APPROACHING`; `LEFT`/`RIGHT` `RadarVerticalRail` untouched (`REQ-TRK-073.2`); `Color(0x...)` strictly confined to `Color.kt`; `private` `@Preview` visibility only; `0` `src/main` reads in `RadarEdgeOverlayTest.kt`; `Localization Parity: PASS - 9/9 existing strings_radar.xml keys reused, zero new keys needed`.

### REQ-FIL-027: Post-Workout ERTS Monotonic Timing, Stationary Indoor Pinning, Jeans-Pocket Gyro/Baro Filtering, Lap Distance Sync & Stream Subsampling [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-027`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmoother.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmoother.kt`
- **Rationale**: Pixel 10 cycling telemetry (`Zum Bäcker #57`, phone in front jeans pocket) exposed 5 compounding defects across live yaw-rate publishing and post-workout ERTS recomputation: (1) SQLite `time` (`DEFAULT CURRENT_TIMESTAMP`) batch-flush `dt = 0 s / 2 s` quantization stalls EKF prediction, drops `VERTICAL_SPEED` to `0 m/h`, and bypasses `useAntiAliasedYaw`; (2) `SpeedAndLocationDevice_ATTFused` overwrites `mWorldYawRateSensor` with the last 20-ms gyro sample, logging `1.2–1.5 Hz` thigh-pedaling aliases; (3) indoor stops (`speed <= 0.05 m/s`, `acc = 50 m`) omit ZUPT and stationary cross-covariance decoupling, letting backward RTS pull post-stop departure backwards into the stop; (4) `ALLOWED_CANONICAL_COLUMNS` omits `DISTANCE_m_LAP` and `updateWorkoutSummaries()` skips `PolyUtil.simplify(..., 10.0)` and `WorkoutSummaries.ENCODING_STEP_SIZE` (`20`) subsampling; and (5) `ExtendedRtsAltitudeSlopeSmoother` lacks `2.0 m` ascent/descent hysteresis (`REQ-FIL-020.2`) while `ErtsPostWorkoutRecomputer` ingests filtered `ALTITUDE` instead of pre-filtering 9-locale raw barometer columns through `BarometricAltitudeFilter.filterSlewOnly()` (`ATT-8880`, refining `REQ-FIL-017.1`–`.4`, `REQ-FIL-019.3`, `REQ-FIL-020.2`, `REQ-FIL-025.2`/`.4`, `REQ-SEN-028.3`, and `REQ-TRK-075`).
- **Description**:
  1. *Monotonic `timestampMs` Reconstruction (`REQ-FIL-027.1`)*:
     - `ErtsPostWorkoutRecomputer.readWorkoutSamples()` SHALL reconstruct monotonic `timestampMs` from `SensorType.TIME_TOTAL` anchored to the first valid `time` epoch, falling back to `parseTimestampMs(timeStr)` with strictly monotonic `max(parsedTs, prevTs + 1000L)` when `TIME_TOTAL` is absent/null/non-increasing (preserving pause gaps and schemas without `TIME_TOTAL`), BEFORE invoking `BarometricAltitudeFilter.filterSlewOnly()` or ERTS smoothing, eliminating `dt = 0 s / 2 s` quantization, `0 m/h` `VERTICAL_SPEED` sawtooth drops, and `useAntiAliasedYaw` bypasses.
  2. *Rolling 1.0-s Time-Weighted Mean `WORLD_YAW_RATE_radps` (`REQ-FIL-027.2`)*:
     - `SpeedAndLocationDevice_ATTFused` SHALL maintain a rolling `1.0 s` time-weighted mean window ($\sum \omega_i \Delta t_i / \sum \Delta t_i$, retaining at least the latest sample) for `mWorldYawRateSensor` across `onSensorChanged()` and `ingestImuMeasurement()`, MUST preserve immediate single-sample emission on sample 1 (`SpeedAndLocationDevice_ATTFusedTest:126-139`), and MUST reset the accumulator alongside `mLastGyroTimestampNs = 0L` in `resetOriginAndEkf()` and `shutDown()`.
  3. *Stationary Indoor-Stop Pinning & Cross-Covariance Decoupling (`REQ-FIL-027.3`)*:
     - `ExtendedRtsOdometrySmoother.smoothSamples()` SHALL classify stationary epochs strictly as `speedMps != null && speedMps <= 0.05 && (wheelSpeedMps == null || wheelSpeedMps <= 0.05)`, invoke `ekf.applyZeroVelocityUpdate()`, pass `isStationary = true` to `ekf.updateGnssPosition()`, decouple stationary velocity/position transition-Jacobian and covariance coupling in `snapshots` / `smoothSnapshots()` backward RTS recursion, and clamp `smoothedStates[k][2] = 0.0` on stationary epochs.
     - `UnicycleOdometryEkf.ZUPT_SPEED_VARIANCE = 2.0`, `MIN_DOPPLER_SPEED_MPS = 3.0`, live `UnicycleOdometryEkf` contracts, and canopy Doppler-dropout tests (`wheelSpeedMps = 10.0`, `ExtendedRtsOdometrySmootherTest:140`) MUST remain intact.
  4. *`DISTANCE_m_LAP` Sync & Summary Stream Subsampling Parity (`REQ-FIL-027.4`)*:
     - `ErtsPostWorkoutRecomputer.ALLOWED_CANONICAL_COLUMNS` and `updateSamplesDatabase()` SHALL include `SensorType.DISTANCE_m_LAP.name`, resetting per-lap cumulative distance to `0.0` on `LAP_NR` transitions before adding `odom.deltaDistanceMeters` in parity with `Laps.db` (`LapsDatabaseManager.syncLapDistancesAndSpeeds()`).
     - `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()` SHALL simplify valid fixes via `PolyUtil.simplify(validLatLngs, 10.0)` (`TrackerService.finalizeLiveSession()` parity; collinear straight-line routes simplify to `2` endpoints, refining `TST-FIL-017.3` & `TST-FIL-019.3`) and subsample `distanceStream` / `altitudeStream` at `WorkoutSummaries.ENCODING_STEP_SIZE` (`20`) parity (retaining start/end points for `< 20`-sample workouts).
  5. *9-Locale Raw Barometer Pre-Filtering & `2.0 m` Elevation Hysteresis (`REQ-FIL-027.5`)*:
     - `ErtsPostWorkoutRecomputer.readWorkoutSamples()` SHALL select raw barometer altitude columns across all 9 locales matching `WorkoutSamplesDatabaseManager.resolveBarometricDeviceNames()` prefixes (plus legacy names, excluding DEM/GPS), pre-filter non-null values through `BarometricAltitudeFilter.filterSlewOnly()` using reconstructed monotonic `timestampMs`, and fall back to canonical `ALTITUDE` only when absent/null.
     - `ExtendedRtsAltitudeSlopeSmoother.smooth()` SHALL apply `ASCENT_DESCENT_HYSTERESIS_METERS = 2.0` (`REQ-FIL-020.2`) anchor-based hysteresis when accumulating `ascentM` and `descentM`.
     - Raw device-attributed columns in `WorkoutSamples.db` (`REQ-FIL-017.3`), `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` (`REQ-FIL-017.5`), and `WorkoutRepository.kt` (`<= 5,117` LoC) MUST remain untouched (`Localization Parity: N/A - no UI strings`; `0` new `src/main` reads per `REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-027` refining `REQ-FIL-017.1`–`.4`, `REQ-FIL-019.3`, `REQ-FIL-020.2`, `REQ-FIL-025.2`/`.4`, `REQ-SEN-028.3`, and `REQ-TRK-075`. Targets `ErtsPostWorkoutRecomputer.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `ExtendedRtsOdometrySmoother.kt`, `UnicycleOdometryEkf.kt`, and `ExtendedRtsAltitudeSlopeSmoother.kt`.
2. *Historical Origin & Commit Trace*: `7d2836ef` (`ATT-7751`), `ed6acfee` (`ATT-7750`), `ed972bad` (`ATT-8337`), `8b0eba41` (`ATT-8081`), `eba66320`/`f6715226` (`ATT-8576`), `d96fd52f` (`ATT-8705`).
3. *Root Reason for Existing Formulation*: `ATT-7750` assumed `time` was 1-Hz monotonic, `ALTITUDE` was usable as raw altitude, and RTS transitions were always moving; `ATT-8705` synced `Laps.db` without `DISTANCE_m_LAP` or indoor-stop ZUPT decoupling.
4. *Preservation of Core Invariants*: Preserves raw device columns byte-for-byte (`REQ-FIL-017.3`), `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` (`REQ-FIL-017.5`), `UnicycleOdometryEkf.ZUPT_SPEED_VARIANCE = 2.0` & `MIN_DOPPLER_SPEED_MPS = 3.0`, immediate sample-1 `mWorldYawRateSensor` emission, Null-Island `NULL` preservation (`REQ-FIL-019`), `Laps.db` parity (`REQ-FIL-025.4`), `WorkoutRepository.kt <= 5,117` LoC untouched, `REQ-PRO-049.1`, and `N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutSamples.db` with duplicate batch-flush `time` (`dt = 0 s / 2 s`), monotonic `TIME_TOTAL`, 9-locale barometer altitude with pocket spikes, `LAP_NR` segments, and an indoor stop (`speed <= 0.05`, `acc = 50 m`),
• *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` runs,
• *Then* monotonic `timestampMs` reconstructs `1-Hz` `dtSec` (`0` `0 m/h` `VERTICAL_SPEED` drops), raw barometer pre-filters via `filterSlewOnly()` + `2.0 m` hysteresis, stationary stop clamps to `0.0 m/s` without pre-stop collapse, `DISTANCE_m_LAP` matches per-lap totals, `mapPolyline` uses `PolyUtil.simplify(..., 10.0)` (`2` points on collinear tracks), and streams subsample at `ENCODING_STEP_SIZE` (`20`).
• *Given* `SpeedAndLocationDevice_ATTFused` receiving a single gyro sample (`0.1 rad/s`) vs. `50-Hz` cyclic pedaling oscillations (`1.2–1.5 Hz`),
• *When* `ingestImuMeasurement()` / `onSensorChanged()`, `resetOriginAndEkf()`, and `shutDown()` execute,
• *Then* sample 1 emits `0.1 rad/s` immediately, `50-Hz` cyclic oscillations average out over a rolling `1.0 s` window, and reset/shutdown clear all gyro-window state cleanly.

**Invariants**: Monotonic `timestampMs` before `filterSlewOnly()`/ERTS; immediate 1st-sample `mWorldYawRateSensor` + rolling `1.0 s` mean + clean reset; stationary ZUPT + RTS decoupling without altering `UnicycleOdometryEkf.ZUPT_SPEED_VARIANCE = 2.0` / `MIN_DOPPLER_SPEED_MPS = 3.0` / `ExtendedRtsOdometrySmootherTest:140`; `DISTANCE_m_LAP` + `PolyUtil.simplify(..., 10.0)` + `ENCODING_STEP_SIZE (20)`; 9-locale barometer `filterSlewOnly()` + `2.0 m` hysteresis; raw device columns & `WorkoutRepository.kt <= 5,117` LoC untouched; `0` new `src/main` reads; `N/A - no UI strings`.

### REQ-FIL-028: Activity & Phone Carriage Mode IMU/Barometer Classification, 1-Hz Persistence & Adaptive EKF/ERTS Filtering [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-028`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageMode.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt`, `app/src/main/java/com/atrainingtracker/banalservice/sensor/formater/PhoneCarriageModeFormatter.kt`, `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`, `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java`, `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceType.java`, `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/BarometricAltitudeFilter.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmoother.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmoother.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt`, `app/src/main/res/values*/strings_devices.xml`
- **Rationale**: `ATT-8880` (`REQ-FIL-027`) established fixed `1.0 s` gyro averaging and `1.0 m`/`2.0 m` barometric hysteresis tuned empirically to a front jeans-pocket cycling defect (`Zum Bäcker #57`), leaving three structural gaps across heterogeneous real-world phone mounting positions and multi-modal dismounts: (1) handlebars, jersey rear pockets, jeans front pockets, running waist belts, arm bands, handheld running, walking dismounts (inside buildings/bakeries), and stationary stops exhibit radically different gyro oscillation energy (`1.2–1.5 Hz` thigh sway vs. `1.4–2.0 Hz` arm swing vs. steering micro-vibrations), barometric pressure artifacts (pocket compression spikes vs. clean handlebar airflow), and step cadence; (2) no telemetry channel persists the active activity/carriage state at `1 Hz` for cockpit observability or post-workout ERTS; and (3) `ExtendedRtsOdometrySmoother` relies solely on heuristic GNSS/wheel thresholds instead of explicit 1-Hz carriage/dismount telemetry (requiring deterministic legacy kinematic fallback when omitted) (`ATT-8890`, refining `REQ-SEN-028.3`, `REQ-FIL-016.2`, `REQ-FIL-017.1`–`.2`, `REQ-FIL-023.1`, `REQ-FIL-025.1`–`.2`, `REQ-FIL-026`, `REQ-FIL-027`, `REQ-TRK-070`, `REQ-TRK-083`).
- **Description**:
  1. *`PhoneCarriageMode` Taxonomy & 50-Hz `PhoneCarriageClassifier` (`REQ-FIL-028.1`)*:
     - `PhoneCarriageMode` enum SHALL define exact persisted integer codes: `UNKNOWN(-1)`, `STATIONARY(0)`, `WALKING_TRANSITION(1)`, `BIKE_HANDLEBAR(2)`, `BIKE_JERSEY_BACK(3)`, `BIKE_FRONT_POCKET(4)`, `RUN_WAIST_HIP(5)`, `RUN_BICEP_ARM(6)`, and `RUN_HANDHELD(7)` (with safe `fromCode(Int)` defaulting to `UNKNOWN`).
     - `PhoneCarriageClassifier` (pure Kotlin, JVM-testable) SHALL fuse sliding 50-Hz gravity/rotation-vector orientation stability, periodic gyro/accelerometer oscillation spectra, barometric micro-slew turbulence, GNSS/wheel speed, optional step cadence, and base `ActivityType` context (`BIKE*` vs `RUN*`) to emit hysteresis-stabilized modes (`>= 4–8 s` dwell/vote hysteresis across mounting-position transitions; immediate/fast transition on `STATIONARY` <-> `WALKING_TRANSITION` dismount/remount transitions).
     - `SpeedAndLocationDevice_ATTFused` SHALL treat `Sensor.TYPE_STEP_DETECTOR` as strictly opportunistic/defensively gated (`sdkInt < 29 || checkSelfPermission(ACTIVITY_RECOGNITION) == GRANTED`, non-null `getDefaultSensor(TYPE_STEP_DETECTOR)`, `SecurityException` catch, and plain-JVM `Build.VERSION.SDK_INT == 0` guard without adding onboarding steps or startup permission prompts) and SHALL degrade deterministically when `TYPE_STEP_DETECTOR`, `TYPE_ROTATION_VECTOR`, `TYPE_GYROSCOPE`, or `TYPE_PRESSURE` is unavailable.
  2. *1-Hz `SensorType.ACTIVITY_AND_CARRIAGE_MODE` Persistence, CSV & Reachability (`REQ-FIL-028.2`)*:
     - `SensorType.ACTIVITY_AND_CARRIAGE_MODE` SHALL be appended strictly at the end of `SensorType.java` (ordinal `67`, preserving `0..66` `Parcelable` ordinals) with `SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`, `subCategory = null`, `SensorValueType.INTEGER`, `filteringPossible = false` (preventing `FilterManager` weighted/moving averaging from corrupting discrete enum codes), `internalUnit = "dimensionless"`, Javadoc containing `'Internal unit:'` + `dimensionless`, and `PhoneCarriageModeFormatter` (`MyFormatter<Number>`).
     - `ActivityType.getSensorTypeArray()` and `DeviceType.getDeviceTypeList()` SHALL expose `ACTIVITY_AND_CARRIAGE_MODE` (`Rule 81`); `TrackerService.sampleAndWriteToDb()` and `CSVFileWriter` SHALL persist and export the 1-Hz integer column via existing `SensorValueType.INTEGER` paths with `0` LoC added to `TrackerService.java` (`3,559`), `DeviceManager.java` (`1,708`), `WorkoutSamplesDatabaseManager.java` (`1,533`), `CSVFileWriter.java`, and `WorkoutRepository.kt` (`<= 5,117`).
  3. *Smooth Live EKF/Filter Adaptation Across All 8 Active Modes + `UNKNOWN` (`REQ-FIL-028.3`)*:
     - `SpeedAndLocationDevice_ATTFused`, `AltitudeAndSlopeDevice_ATTFused`, `BarometricAltitudeFilter`, `UnicycleOdometryEkf`, and `KinematicAltitudeSlopeEkf` SHALL smoothly blend (without EKF state jumps or resetting existing constants `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`, `DEFAULT_VAR_BARO = 0.25`, or immediate sample-1 `WORLD_YAW_RATE_radps` `0.1 rad/s` emission): (a) `Q_GYRO` process gyro noise; (b) yaw averaging window + glance-spike gating (`BIKE_HANDLEBAR` sharp steering/rear-glance rejection vs. `BIKE_FRONT_POCKET`/`RUN_BICEP_ARM`/`RUN_HANDHELD` cyclic oscillation suppression); (c) barometer median pre-filter window + slew clamp + `rBaro` variance scaling (tight for `BIKE_HANDLEBAR`/`RUN_WAIST_HIP`, dampened for `BIKE_FRONT_POCKET` compression and `RUN_HANDHELD`/`RUN_BICEP_ARM` swing shock); and (d) slow running-pace chord-heading conditioning & `STATIONARY`/`WALKING_TRANSITION` ZUPT pinning.
  4. *1-Hz ERTS Ingestion & Legacy Kinematic Fallback (`REQ-FIL-028.4`)*:
     - `ErtsPostWorkoutRecomputer` SHALL include `SensorType.ACTIVITY_AND_CARRIAGE_MODE.name` in `ALLOWED_CANONICAL_COLUMNS` (propagating canonical integer values through `updateSamplesDatabase()`), ingest persisted 1-Hz `PhoneCarriageMode` epochs directly into `ExtendedRtsOdometrySmoother` & `ExtendedRtsAltitudeSlopeSmoother` for dismount pinning (`STATIONARY`/`WALKING_TRANSITION` indoor drift suppression) and per-mode noise/`rBaro`/window scaling, and reconstruct a 1-Hz `PhoneCarriageMode` sequence via a deterministic kinematic/barometric-variance fallback when replaying legacy `WorkoutSamples.db` tables lacking the column or containing only `UNKNOWN(-1)`.
  5. *9-Locale Parity & Contract/Documentation Sync Plan (`REQ-FIL-028.5`)*:
     - `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt` in `strings_devices.xml` SHALL provide parity translations (preserving `\n` rules, zero `&#10;`) for `SensorType.ACTIVITY_AND_CARRIAGE_MODE` and all 9 `PhoneCarriageMode` labels (`UNKNOWN(-1)` + `0..7`).
     - In Stages 3–5 alongside production edits, `SensorCategoryAndSelectorContractTest` (`67 -> 68`), `SensorTypeUnitsContractTest`, `SensorFilterDefaultsTest`, `docs/architecture/sensor_units.md`, `docs/architecture.md` (`Rule 8.8`), and `docs/system/` (`SYSREQ-TRK-010`, `system_context.md`, `system_architecture.md`, `system_software_traceability.md` per `REQ-PRO-131.4`) SHALL be synchronized cleanly with `0` new `src/main` file reads in tests (`REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-028` refining `REQ-SEN-028.3`, `REQ-FIL-016.2`, `REQ-FIL-017.1`–`.2`, `REQ-FIL-023.1`, `REQ-FIL-025.1`–`.2`, `REQ-FIL-026`, `REQ-FIL-027`, `REQ-TRK-070`, `REQ-TRK-083` across `PhoneCarriageMode`/`PhoneCarriageClassifier`, `SensorType.ACTIVITY_AND_CARRIAGE_MODE`, live EKF/filters, and `ErtsPostWorkoutRecomputer`.
2. *Historical Origin & Commit Trace*: `7d2836ef` (`ATT-7751`), `ed6acfee` (`ATT-7750`), `eba66320` (`ATT-8576`), `14935efb` (`ATT-8880`), `1ef85e6a` (`ATT-8097` — health/activity permission contract), `b73c6375` (`ATT-8410` — `SensorCategory` & `SensorType` 67-count contract).
3. *Root Reason for Existing Formulation*: Fixed `1.0 s` gyro window and static baro/EKF tuning assumed one implicit carriage model; `TrackerService.hasHealthPermission()` (`ACTIVITY_RECOGNITION`) governs Android 14+ FGS health type without requiring `ACTIVITY_RECOGNITION` manifest onboarding (`PermissionOnboardingSheetContractTest`: 4 steps only).
4. *Preservation of Core Invariants*: Preserves `SensorType` ordinals `0..66`, `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` & `ZUPT_SPEED_VARIANCE = 2.0`, `KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO = 0.25`, `ExtendedRtsOdometrySmootherTest:140` canopy dropout contract, immediate sample-1 `WORLD_YAW_RATE_radps`, `4`-step permission onboarding (`PermissionOnboardingSheetContractTest`), `0` LoC growth in `TrackerService.java`/`DeviceManager.java`/`WorkoutSamplesDatabaseManager.java`/`WorkoutRepository.kt`, raw device column immutability (`REQ-FIL-017.3`), `REQ-PRO-049.1`, and `9/9` locale parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `PhoneCarriageClassifier` fed synthetic 50-Hz `Zum Bäcker` trajectories spanning `BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, `BIKE_FRONT_POCKET`, `WALKING_TRANSITION`, `STATIONARY`, `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, `RUN_HANDHELD`, and `UNKNOWN` (plus denied `ACTIVITY_RECOGNITION` / missing `TYPE_STEP_DETECTOR` / missing barometer / `SDK_INT == 0` plain JVM),
• *When* transitions occur live and when `SensorType.ACTIVITY_AND_CARRIAGE_MODE` is sampled at `1 Hz` into `WorkoutSamples.db` and CSV,
• *Then* hysteresis prevents flicker, missing sensors degrade deterministically without `SecurityException`, discrete integer codes remain unfiltered (`filteringPossible = false`), `Q_GYRO`/yaw window/glance gate/baro median/`rBaro` adapt smoothly without EKF state jumps, `SensorType` Javadoc/reachability contracts pass, and all 9 locales localize labels without `src/main` test reads.
• *Given* both `ACTIVITY_AND_CARRIAGE_MODE`-equipped and legacy `WorkoutSamples.db` recordings replayed through `ErtsPostWorkoutRecomputer.recomputeWorkout()`,
• *When* `ExtendedRtsOdometrySmoother` and `ExtendedRtsAltitudeSlopeSmoother` run with explicit vs. kinematic fallback carriage modes,
• *Then* indoor dismounts pin cleanly, pocket/arm barometric spikes attenuate, handlebar climbs retain sharp sensitivity, `ExtendedRtsOdometrySmootherTest:140` stays green, and `ACTIVITY_AND_CARRIAGE_MODE` survives ERTS recomputation accurately.

**Invariants**: `PhoneCarriageMode` `UNKNOWN(-1)` + `0..7`; opportunistic/defensively gated `TYPE_STEP_DETECTOR` (`0` onboarding steps/eager prompts); `SensorType` ordinal `67` + `filteringPossible = false` + `'Internal unit:' dimensionless` + `ActivityType`/`DeviceType` reachable; `0` LoC growth in monoliths; `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`, `DEFAULT_VAR_BARO = 0.25`, sample-1 `0.1 rad/s` yaw & `ExtendedRtsOdometrySmootherTest:140` preserved; 1-Hz ERTS ingestion + legacy kinematic fallback; `9/9` `strings_devices.xml` parity; `0` `src/main` reads (`REQ-PRO-049.1`); Stage 3–5 `SWE.2`/`SYS.2`/`SYS.3` doc sync.

### REQ-FIL-029: Carriage-Classified Smartphone IMU Cycling Crank Cadence & Running Stride/Step Cadence Estimation [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-029`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImuCadenceDsp.kt`, `PolarLiveRunningCadenceEstimator.kt`, `SmartphoneImuCadenceEstimator.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`, `BANALService.java`, `MySensorManager.java`, `MyAccumulatorSensor.java`, `DeviceType.java`
- **Rationale**: When cycling with the phone in a trouser or jersey pocket (`BIKE_FRONT_POCKET`, `BIKE_JERSEY_BACK`) or running without a footpod or Polar IMU (`RUN_WAIST_HIP`, `RUN_BICEP_ARM`, `RUN_HANDHELD`), `SensorType.CADENCE` and `SensorType.STRIDES` remain unrecorded (`GCData[5] = '-'`). Reusing the pure-Kotlin 3D vector autocorrelation DSP architecture (`PolarLiveRunningCadenceEstimator`) dispatched by `PhoneCarriageMode` (`ATT-8890`) extracts accurate crank `RPM` and running `strides/min` + `STRIDES` while preserving the `ATT-166` invariant strictly suppressing `BIKE_HANDLEBAR` road-vibration cadence (`ATT-8895`, extending `REQ-SEN-028`, `REQ-FIL-028`, `REQ-SET-112.4`, `REQ-SET-114`).
- **Description**:
  1. *Shared Pure-Kotlin IMU Cadence DSP Core (`REQ-FIL-029.1`)*:
     - Extract `UniformGridResampler` (`50 Hz` / `20 ms`), `BiquadBandPass`, `Vector3BandPass`, `BiquadSection`, `DoubleRingBuffer`, `Vector3RingBuffer`, and `findFundamentalLag()` (`5 s` sliding normalized autocorrelation with parabolic sub-sample interpolation) from `PolarLiveRunningCadenceEstimator.kt` into a shared pure-Kotlin DSP core (`ImuCadenceDsp.kt`) reused by both `PolarLiveRunningCadenceEstimator` and `SmartphoneImuCadenceEstimator`.
     - Refactoring MUST preserve `PolarLiveRunningCadenceEstimator` (`H10_CHEST` `mG`, `VERITY_SENSE_ARM` `mG` + `deg/s`) outputs bit-for-bit across `PolarLiveRunningCadenceEstimatorTest` and `PolarOnlineStreamingBridgeTest`, while `SmartphoneImuCadenceEstimator` ingests Android SI units (`m/s^2`, `rad/s`) and `Sensor.TYPE_STEP_DETECTOR` events.
  2. *Carriage-Mode Cycling & Running Cadence Dispatch (`REQ-FIL-029.2`)*:
     - `BIKE_FRONT_POCKET`: SHALL apply `0.65–2.2 Hz` (`39–132 RPM`) 3D `GYRO + ACC` thigh-flexion autocorrelation with a fast `1.5 s` coasting energy gate emitting `0.0 RPM` within `2.0 s` when thigh angular RMS drops `< 0.25 rad/s`; if quiet coasting later transitions `PhoneCarriageClassifier` across `DEFAULT_MOUNT_HYSTERESIS_MS` (`4,000 ms`) into `BIKE_HANDLEBAR`, cadence estimation SHALL suppress cleanly (`null`) without emitting phantom RPMs.
     - `BIKE_JERSEY_BACK`: SHALL apply `0.65–2.2 Hz` 3D gyro autocorrelation with `f` vs. `2f` harmonic disambiguation and a strict normalized autocorrelation prominence gate ($R(\tau_{\text{peak}})/R(0) \ge 0.45$), suppressing output on rough roads or during coasting.
     - `BIKE_HANDLEBAR` (plus `UNKNOWN` and `WALKING_TRANSITION` while `BSportType.BIKE`): MUST remain strictly disabled/suppressed (`null`, zero RPM emission, zero `CADENCE`/`STRIDES` registration or `GCData[5]` `'C'` pollution per `ATT-166`).
     - `RUN_WAIST_HIP` (and `WALKING_TRANSITION` while `BSportType.RUN`): SHALL apply `1.2–4.0 Hz` impact band-pass + `Sensor.TYPE_STEP_DETECTOR` fusion emitting canonical `strides/min` (`steps/min / 2`) and `+1` `SensorType.STRIDES` per `2` steps, degrading deterministically to `ACC + TYPE_STEP_DETECTOR` when gyroscope is absent/disabled.
     - `RUN_BICEP_ARM` & `RUN_HANDHELD`: SHALL apply `0.6–2.0 Hz` 3D `GYRO + ACC` pendulum arm-swing autocorrelation + `TYPE_STEP_DETECTOR` fusion with `PhoneCarriageClassifier.isGlanceActive()` pitch/roll baseline-jump blanking in `RUN_HANDHELD` (zero phantom spikes or strides during wrist screen glances), plus deterministic `ACC + TYPE_STEP_DETECTOR` fallback when gyro is absent.
  3. *Dual-Rate Smartphone IMU Ingestion & Dynamic `CADENCE`/`STRIDES` Lifecycle (`REQ-FIL-029.3`)*:
     - `SpeedAndLocationDevice_ATTFused` SHALL subscribe `Sensor.TYPE_ACCELEROMETER` in `registerImuSensors()` at `SensorManager.SENSOR_DELAY_GAME` (`~50 Hz`) independently of `LocationAvailable()` and `AttFusedPreferences.isEffectiveGyroActive()` so `1.2–4.0 Hz` impacts and indoor runs execute without GPS lock or hardware gyro, while guarding `SpeedAndLocationDevice.unregisterAccelerometer()` so GNSS loss (`LocationUnavailable()`) does not tear down active 50-Hz cadence ingestion.
     - `SpeedAndLocationDevice_ATTFused` SHALL dynamically register `SensorType.CADENCE` (`MySensor<Double>`) only when non-handlebar cadence is active (unregistering/suppressing immediately upon `BIKE_HANDLEBAR`, never emitting `GCData[5]` `'C'` on handlebar-only rides), SHALL register `SensorType.STRIDES` (`MyIntegerAccumulatorSensor(..., true)` seeded via `accumulate(0)`) strictly when `BSportType.RUN` is active (observing `BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT`), and SHALL reset estimator/sensors cleanly on `onAccumulatorsReset()` and `shutDown()`.
  4. *`MySensorManager` Priority Arbitration, `DeviceType` Reachability & Zero Monolith Growth (`REQ-FIL-029.4`)*:
     - `MySensorManager.CADENCE_PRIORITY` SHALL append `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` strictly at the tail behind `DeviceType.HRM` (`RUN_SPEED > BIKE_CADENCE > BIKE_SPEED_AND_CADENCE > BIKE_POWER > HRM > SPEED_AND_LOCATION_ATT_FUSED`), and `MySensorManager` SHALL define explicit `STRIDES_PRIORITY` (`RUN_SPEED > HRM > SPEED_AND_LOCATION_ATT_FUSED`) wired via `setPriorityList(SensorType.STRIDES, STRIDES_PRIORITY)` and `getPriorityArray(SensorType.STRIDES)` instead of ordinal fallback.
     - `DeviceType.getDeviceTypeList()` SHALL add `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` for `SensorType.CADENCE` and `SensorType.STRIDES`.
     - Implementation MUST add `0` LoC to protected monoliths (`TrackerService.java <= 3,556`, `DeviceManager.java <= 1,707`, `WorkoutSamplesDatabaseManager.java <= 1,533`, `WorkoutRepository.kt <= 4,973` / `<= 5,117` cap on `e62a6ba4`), introduce zero DB schema changes, and require zero new UI strings (`N/A - no UI strings`).
  5. *`SWE.2` / `SYS.2` / `SYS.3` Parity & Governance Hygiene (`REQ-FIL-029.5`)*:
     - In Stages 4–5 alongside newly created `.kt` files, `docs/architecture.md` (`Rule 8.8`) and `docs/system/` (`SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`, `system_architecture.md`, `system_software_traceability.md` per `REQ-PRO-131.4`) SHALL be synchronized cleanly with `0` new `src/main` reads in unit/contract tests (`REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-029` extending `REQ-SEN-028`, `REQ-FIL-028`, `REQ-SET-112.4`, and `REQ-SET-114` across `PolarLiveRunningCadenceEstimator.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`, `MySensorManager.java`, and `DeviceType.java`.
2. *Historical Origin & Commit Trace*: `f4bef44c` (`ATT-8016` — `CADENCE_PRIORITY` tail lock at `HRM` + `BSportType.RUN` gating), `438233eb` (`ATT-8017` — `PolarLiveRunningCadenceEstimator`), `f82a2960` (`ATT-7751` — `SpeedAndLocationDevice_ATTFused`), `fee3e805` (`ATT-8890` — `PhoneCarriageMode`/`PhoneCarriageClassifier`).
3. *Root Reason for Existing Formulation*: `ATT-166` explicitly forbade handlebar cycling cadence because `5–50 Hz` road chatter masks `1.2–1.6 Hz` pedaling sway; `ATT-8016` locked `HRM` at the end of `CADENCE_PRIORITY` so Polar IMU never preempted ANT+/BLE footpods or bike sensors.
4. *Preservation of Core Invariants*: Preserves strict `BIKE_HANDLEBAR` suppression (zero RPM, zero `GCData[5]` `'C'`), external sensor precedence (`RUN_SPEED`/`BIKE_*`/`HRM` > `SPEED_AND_LOCATION_ATT_FUSED`), Polar H10/Verity equivalence, `0` LoC monolith growth (`<= 4,973` `WorkoutRepository.kt`), and `N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `PolarLiveRunningCadenceEstimator` backed by the extracted shared DSP core (`ImuCadenceDsp`), *When* executing `PolarLiveRunningCadenceEstimatorTest` and `PolarOnlineStreamingBridgeTest`, *Then* all H10 (`100/200 Hz`) and Verity Sense (`52 Hz`) outputs remain equivalent within existing tolerances.
• *Given* a cycling workout in `BIKE_FRONT_POCKET` with `50-Hz` thigh oscillations at `1.25 Hz` (`75 RPM`) followed by `5 s` coasting (`v = 6.0 m/s`, thigh gyro RMS `< 0.15 rad/s`), *When* processed by `SmartphoneImuCadenceEstimator`, *Then* it emits `75 ± 2 RPM` while pedaling, drops to `0.0 RPM` within `2.0 s` via the `1.5 s` coasting gate, and suppresses cleanly (`null`) if quiet coasting later transitions classifier state to `BIKE_HANDLEBAR`.
• *Given* `BIKE_JERSEY_BACK` cycling gyro streams at `f_pedal` with `2f` vertical impacts vs. rough-road noise, *When* processed by `SmartphoneImuCadenceEstimator`, *Then* `f`/`2f` harmonic disambiguation reports fundamental crank `RPM` only when $R(\tau_{\text{peak}})/R(0) \ge 0.45$ and suppresses low-prominence road chatter.
• *Given* cycling or dismount streams classified as `BIKE_HANDLEBAR` (with `5–50 Hz` vibration), `UNKNOWN`, or `WALKING_TRANSITION` under `BSportType.BIKE`, *When* evaluated by `SmartphoneImuCadenceEstimator` and `SpeedAndLocationDevice_ATTFused`, *Then* cycling cadence stays strictly `null`, `SensorType.CADENCE`/`STRIDES` remain unregistered, and `GCData[5]` never receives `'C'`.
• *Given* running streams in `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, or `RUN_HANDHELD` at `170 steps/min` (`85 strides/min`) with `TYPE_STEP_DETECTOR` (including a `RUN_HANDHELD` wrist screen-glance gesture and a gyro-absent `ACC + TYPE_STEP_DETECTOR` fallback), *When* processed by `SmartphoneImuCadenceEstimator` and `SpeedAndLocationDevice_ATTFused` (even indoors pre-GPS lock), *Then* `SensorType.CADENCE` emits `85 ± 2 strides/min` and `SensorType.STRIDES` increments monotonically from `1` without glance phantom spikes.
• *Given* active ANT+/BLE `RUN_SPEED`/`BIKE_CADENCE`/`BIKE_SPEED_AND_CADENCE`/`BIKE_POWER`/`HRM` alongside `SPEED_AND_LOCATION_ATT_FUSED`, *When* `MySensorManager` resolves `CADENCE` and `STRIDES`, *Then* external sensors strictly preempt `SPEED_AND_LOCATION_ATT_FUSED` with deterministic `STRIDES_PRIORITY` (`RUN_SPEED > HRM > SPEED_AND_LOCATION_ATT_FUSED`).

**Invariants**: `BIKE_HANDLEBAR` strictly suppressed (no RPM, no `GCData[5]` `'C'`); `RUN_SPEED`/`BIKE_*`/`HRM` > `SPEED_AND_LOCATION_ATT_FUSED` in `CADENCE_PRIORITY`/`STRIDES_PRIORITY`; Polar H10/Verity equivalence preserved; `0` LoC growth in `TrackerService.java`/`DeviceManager.java`/`WorkoutSamplesDatabaseManager.java`/`WorkoutRepository.kt` (`3,556`/`1,707`/`1,533`/`4,973` on `e62a6ba4`); `N/A - no UI strings`; `0` new `src/main` reads (`REQ-PRO-049.1`).

### REQ-FIL-030: Kinematic `BSportType` Inference & Stop-Filtered Moving-Speed Sport Identity Resolution [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-030`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KinematicSportTypeDiscriminator.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `BANALService.java`, `EquipmentAndSportTypeDiscoveryManager.kt`, `LiveWorkoutSession.java`, `TrackerService.java`
- **Rationale**: When a user tracks without pre-selecting a sport (`mHaveUserSelectedSportType == false`) and without paired sport-specific ANT+/BLE sensors (`DeviceManager.getSportType() == BSportType.UNKNOWN`, `GCData = TDS----AG--`), `BANALService.getBSportType()` stays `UNKNOWN` throughout the workout and finalization guesses the sport solely from session-wide `averageSpeed = mDistanceTotal_m / mTimeActive_s`. Unpaused shopping/bakery stops dilute errand bike rides (`Metzger, Bäcker, und Knittel`: `1.35 m/s` avg vs. `p90 = 6.34 m/s`, `416 s` coasting; `Norma #9`: `2.34 m/s` vs. `p90 = 6.66 m/s`, `343 s` coasting; `Zum Bäcker #57`: `3.19 m/s` vs. `p90 = 6.65 m/s`, `46 s` coasting) into walking/running speed brackets, whereas true runs (`Kurze Runde #52`) exhibit `0 s` coasting and step-coherent footstrike periodicity (`ATT-8891`, extending `REQ-SEN-028`, `REQ-FIL-028`, `REQ-FIL-029`, `REQ-SET-030`, `REQ-SET-064`, `REQ-TRK-030`).
- **Description**:
  1. *Pure-Kotlin Kinematic `BSportType` Discriminator & Hardware Degradation (`REQ-FIL-030.1`)*:
     - `KinematicSportTypeDiscriminator` (`banalservice/odometry/`) SHALL fuse four orthogonal physical cues with zero Android UI/DB coupling or SQLite reads:
       (a) **Coasting / Freewheeling**: sustained speed $v > 3.0\text{ m/s}$ for $\ge 3.0\text{ s}$ with near-zero step rate ($f_{\text{step}} < 0.5\text{ Hz}$ or absent) and low cyclic angular/footstrike oscillation (de-biased gyro RMS $< 0.25\text{ rad/s}$ when gyro active, low `2.4–3.6 Hz` impact energy) SHALL contribute strong `BSportType.BIKE` evidence.
       (b) **Step-Length & Permission-Free Footstrike Impact Coherence**: active `Sensor.TYPE_STEP_DETECTOR` cadence $f_{\text{step}} \in [2.0, 3.6]\text{ Hz}$—or, when `TYPE_STEP_DETECTOR` is unavailable (`ACTIVITY_RECOGNITION` denied, missing hardware, or OEM screen-off throttling), permission-free `50-Hz` `Sensor.TYPE_ACCELEROMETER` `2.4–3.6 Hz` vertical/magnitude impact periodicity reusing `ImuCadenceDsp`—with physiological step length $L_{\text{step}} = v / f_{\text{step}} \in [0.60, 1.75]\text{ m}$ (ACF interpolation tolerance `<= 1.82 m`) SHALL contribute strong `BSportType.RUN` evidence (classifying active running including `5.6 m/s` fast intervals within `<= 30 s`), whereas $f_{\text{step}} \approx 0$ or apparent distance per cycle $> 3.5\text{ m}$ at $v > 5.0\text{ m/s}$ SHALL vote `BSportType.BIKE`.
       (c) **Carriage Mode Coupling**: `BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, and `BIKE_FRONT_POCKET` SHALL vote `BSportType.BIKE`; `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, and `RUN_HANDHELD` SHALL vote `BSportType.RUN`; `STATIONARY`, `WALKING_TRANSITION`, and `UNKNOWN` SHALL remain neutral.
       (d) **Stop-Filtered Moving Speed Distribution (`MovingSpeedSummary`)**: moving samples ($v > 0.8\text{ m/s}$) SHALL be accumulated separately from stationary time (`movingMeanMps`, `p50Mps`, `p90Mps`, `movingSampleCount`, `coastingSeconds`) via $O(1)$ constant-memory `MovingSpeedHistogram` with kinematic coasting/footstrike cues taking precedence over hard speed cutoffs so `5.6 m/s` interval runners classify as `RUN` and `2.0 m/s` steep uphill MTB climbs classify as `BIKE` (or avoid false `RUN` latches).
     - Evidence scoring SHALL use bounded accumulation with hysteresis permitting mid-session recovery (`RUN -> BIKE` or `BIKE -> RUN`) on sustained counter-evidence and MUST degrade cleanly without error when `TYPE_PRESSURE == null`, `TYPE_GYROSCOPE == null`, `TYPE_GAME_ROTATION_VECTOR`/`TYPE_ROTATION_VECTOR == null`, or `ACTIVITY_RECOGNITION` is denied.
  2. *Split Sport Context Hint & `SpeedAndLocationDevice_ATTFused` Integration (`REQ-FIL-030.2`)*:
     - `PhoneCarriageClassifier.setSportContext(...)` MUST consume strictly `BANALService.getAuthoritativeBSportType()` (Tier 1 user selection > Tier 2 paired external sensors only; `UNKNOWN` when sensorless/unselected) to prevent circular positive-feedback latching of kinematic guesses.
     - Downstream consumers (`SmartphoneImuCadenceEstimator` and `applyCadenceAndStrideSensors()`) MUST consume full `BANALService.getBSportType()` (Tier 1 > Tier 2 > Tier 3) so kinematically inferred `BSportType.RUN` automatically activates `SensorType.STRIDES`, preserves stride counting across `WALKING_TRANSITION` walk breaks, and suppresses false cycling RPM on transient carriage flicker.
     - On inferred `BSportType` transitions, `SpeedAndLocationDevice_ATTFused` SHALL publish the updated sport and `MovingSpeedSummary` to `BANALService` and invoke internal `handleSportTypeChanged()`, MUST NOT broadcast `SPORT_TYPE_CHANGED_BY_USER_INTENT`, and SHALL reset discriminator/BANAL inferred state on `onAccumulatorsReset()` and `shutDown()`.
  3. *`BANALService` 4-Tier Precedence, Unified `getSportTypeId` & `RESET_ACCUMULATORS_INTENT` Hygiene (`REQ-FIL-030.3`)*:
     - `BANALService.getBSportType()` (and `getActiveBSportType()`) SHALL enforce strict volatile, zero-SQLite precedence: (1) `mHaveUserSelectedSportType` (`mUserSelectedBSportType` — preserving explicit `OTHER`/`WALK` selections) > (2) `DeviceManager.getSportType()` (`!= BSportType.UNKNOWN`) > (3) high-confidence kinematic `mInferredBSportType` (`BIKE` or `RUN`) > (4) `BSportType.UNKNOWN`.
     - Both `BANALService.getSportTypeId()` and `BANALService.getSportTypeId(double avgSpd)` SHALL route through `getBSportType()` when `!mHaveUserSelectedSportType` for consistent 4-tier resolution.
     - `BANALService` SHALL register a `RESET_ACCUMULATORS_INTENT` receiver (safely unregistered in `onDestroy()`) that clears `mInferredBSportType = BSportType.UNKNOWN` and `mMovingSpeedSummary = null` (as does `onDestroy()`) while strictly preserving `mHaveUserSelectedSportType`, `mUserSelectedSportTypeId`, and `mUserSelectedBSportType` across `RESET_ACCUMULATORS_INTENT`.
  4. *Moving-Speed Sport/Equipment Discovery & Finalization Integration (`REQ-FIL-030.4`)*:
     - `EquipmentAndSportTypeDiscoveryManager` SHALL preserve existing 2/3-arg `resolveSportType()`, `resolveIdentity()`, and `getCandidateBSportTypes()` signatures and provide moving-speed-aware resolution enforcing:
       (a) **Sparse-Sample Floor**: when `movingSummary == null` or `movingSampleCount < 10` moving seconds (e.g., indoor/treadmill sessions or short walks), fall back strictly to `sessionAvgSpeed`.
       (b) **Sport-Aware Metric Selection**: when `movingSampleCount >= 10`, use `movingMeanMps` for `UNKNOWN` and `RUN` sessions so `p90` spikes never misclassify an `UNKNOWN` fast `4.3–5.6 m/s` runner into `MTB`/`BIKE`; for `BSportType.BIKE`, evaluate representative cycling cruising speed (`max(movingMeanMps, 0.5 * (p50Mps + p90Mps))` when stops dilute `movingMeanMps`) so multi-stop errand rides (`Metzger`, `Norma #9`, `Zum Bäcker #57`) resolve cleanly to `BSportType.BIKE` (`Endurance` / linked bike gear).
     - `LiveWorkoutSession` SHALL track 1-Hz moving speed ($v > 0.8\text{ m/s}$) as fallback for GPS-only sessions lacking `ATT_FUSED`. `TrackerService` (`BanalFinalizationSnapshot` retaining Rule 20 backward-compatible 4-arg/6-arg `capture` overloads, and `finalizeLiveSession()`) SHALL pass moving-speed-aware inputs to `EquipmentAndSportTypeDiscoveryManager` while strictly preserving `effectiveAvgSpeed = mDistanceTotal_m / mTimeActive_s` for `WorkoutSummaries.SPEED_AVERAGE_mps` and `SensorType.SPEED_mps` `ExtremaType.AVG` (`SCRUM-108` / `REQ-TRK-030.3`), with zero new main-thread SQLite queries.
  5. *Monolith LoC Bounds, `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` Parity & Governance Hygiene (`REQ-FIL-030.5`)*:
     - Implementation MUST uphold `TrackerService.java <= 3,556` LoC (`0` net growth; achieved `3,540` LoC), `DeviceManager.java <= 1,707` LoC (`0` edits), `SpeedAndLocationDevice_ATTFused.kt < 1,500` LoC (`1,417` LoC), `BANALService.java <= 1,035` LoC (`1,018` LoC), zero DB schema changes, `N/A - no UI strings`, `0` new `src/main` test reads (`REQ-PRO-049.1`), and Stage 4–5 synchronization of `docs/architecture.md`, `docs/architecture/detailed_design/banal_service.md`, `docs/system/sysreq_live_tracking.md`, `docs/system/system_architecture.md`, and `docs/system/system_software_traceability.md` (`Rule 8.8`, `REQ-PRO-131.4`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-030` extending `REQ-SEN-028`, `REQ-FIL-028`, `REQ-FIL-029`, `REQ-SET-030`, `REQ-SET-064`, and `REQ-TRK-030` across `BANALService.java`, `SpeedAndLocationDevice_ATTFused.kt`, `EquipmentAndSportTypeDiscoveryManager.kt`, `LiveWorkoutSession.java`, and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: `SCRUM-108` / `ATT-3986` (`REQ-TRK-030.3` authoritative `distance/time` `SPEED_mps` average), `ATT-773` (`REQ-SET-064` multi-sport cluster candidates), `fee3e805` (`ATT-8890` `PhoneCarriageClassifier`), `a3a5942a` (`ATT-8895` `ImuCadenceDsp`, `BANALService.cServiceInstance`, volatile `mUserSelectedBSportType`).
3. *Root Reason for Existing Formulation*: Before `ATT-8890`/`ATT-8895`, sensorless workouts had no 50-Hz IMU carriage or footstrike periodicity pipeline, leaving `BANALService` unable to distinguish cycling from running without external sensors and forcing `EquipmentAndSportTypeDiscoveryManager` to rely solely on session-wide `averageSpeed`.
4. *Preservation of Core Invariants*: Explicit user selection (`mHaveUserSelectedSportType`, including `OTHER`/`WALK`) and paired sport-specific hardware sensors (`DeviceManager.getSportType()`, `identity.isHighConfidence`) remain 100% sovereign; `WorkoutSummaries.SPEED_AVERAGE_mps` and `SensorType.SPEED_mps` `AVG` extrema retain `mDistanceTotal_m / mTimeActive_s`; `PhoneCarriageClassifier` consumes only authoritative Tier 1+2 hints; existing `EquipmentAndSportTypeDiscoveryManager` and `BanalFinalizationSnapshot` APIs remain backward-compatible.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a sensorless unselected workout (`GCData = TDS----AG--`, `mHaveUserSelectedSportType == false`) modeling multi-stop errand rides (`Metzger` `1.35 m/s` avg / `p90 = 6.34 m/s` / `416 s` coasting, `Norma #9`, `Zum Bäcker #57`), *When* live inference and finalization (`EquipmentAndSportTypeDiscoveryManager.resolveIdentity`) execute, *Then* `BANALService.getBSportType()` infers `BSportType.BIKE`, finalization resolves to cycling `SportType`/`Equipment` (`BIKE`, not `WALK`/`RUN`), and `SPEED_mps` `AVG` extrema retains session `mDistanceTotal_m / mTimeActive_s`.
• *Given* a sensorless unselected running workout (`Kurze Runde #52` at `1.9–2.6 m/s` and fast `5.6 m/s` intervals, `0 s` coasting, `2.6–3.2 Hz` footstrikes, $L_{\text{step}} \in [0.65, 1.75]\text{ m}$) on a device missing barometer & gyroscope with `ACTIVITY_RECOGNITION` denied, *When* 50-Hz `TYPE_ACCELEROMETER` and GNSS speed stream, *Then* `KinematicSportTypeDiscriminator` infers `BSportType.RUN` within `<= 30 s`, activates `SensorType.STRIDES` across `WALKING_TRANSITION` breaks, and finalizes as `RUN` without `p90` misclassifying into `MTB`/`BIKE`.
• *Given* an uphill MTB climb at `2.0 m/s` without `2.4–3.6 Hz` running impact periodicity, or an initial jog transitioning into `>= 3 s` bicycle coasting (`v > 3.0 m/s`), *When* evaluated by `KinematicSportTypeDiscriminator`, *Then* uphill MTB avoids false `RUN` lock-in and `RUN -> BIKE` recovers cleanly without circular `PhoneCarriageClassifier` latching.
• *Given* an explicit user sport pre-selection (including `OTHER`/`WALK`) or a paired active external sport sensor (`BIKE_POWER`, `BIKE_CADENCE`, `RUN_SPEED`), *When* `KinematicSportTypeDiscriminator` runs and `RESET_ACCUMULATORS_INTENT` fires at workout start, *Then* Tier 1/2 precedence is never overridden, user selection survives `RESET_ACCUMULATORS_INTENT`, and inferred Tier 3 state resets cleanly between sessions.
• *Given* an indoor/treadmill workout or short walk with `< 10` moving GPS seconds, *When* `EquipmentAndSportTypeDiscoveryManager` resolves identity, *Then* moving-speed override defers to `sessionAvgSpeed` fallback.

**Invariants**: Strict precedence `mHaveUserSelectedSportType (incl. OTHER/WALK) > DeviceManager.getSportType() > KinematicSportTypeDiscriminator > speed fallback`; `PhoneCarriageClassifier` consumes only `getAuthoritativeBSportType()`; `WorkoutSummaries.SPEED_AVERAGE_mps` & `SPEED_mps` `AVG` extrema retain `mDistanceTotal_m / mTimeActive_s`; zero IMU/Main-thread SQLite reads; `TrackerService.java <= 3,556` LoC (`3,540`), `DeviceManager.java <= 1,707` LoC, `SpeedAndLocationDevice_ATTFused.kt < 1,500` LoC (`1,417`); Rule 20 DTO compatibility; `N/A - no UI strings`; `0` new `src/main` test reads (`REQ-PRO-049.1`).

### REQ-FIL-031: Multisport Transition Detection, Retroactive Dismount/Mount Anchoring & Automatic Named Laps [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-031`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-006`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MultisportTransitionDetector.kt`, `MultisportTransitionBridge.kt`, `MultisportLapCoordinator.kt`, `MyAccumulatorSensor.java`, `SpeedAndLocationDevice_ATTFused.kt`, `BANALService.java`, `TrackerService.java`, `values*/strings_cockpit.xml`
- **Rationale**: During triathlon, duathlon, and cycling-running brick workouts, manual lap/sport button presses in `T1`/`T2` are error-prone and frequently forgotten. Fusing the 1-Hz `PhoneCarriageMode` stream (`REQ-FIL-028`), permission-free 50-Hz accelerometer footstrike / kinematic inference (`REQ-FIL-030`), and active non-zero external sensor telemetry allows hands-free `BIKE <-> TRANSITION <-> RUN` segmentation, retroactive boundary anchoring (`t_exit_sport1`, `t_enter_sport2`), named laps (`Bike`, `Transition`, `Run`) in `Laps.db`, and live cockpit sport switching (`km/h` vs. `min/km`, cycling `RPM` vs. running `strides/min`) while preserving same-sport stop immunity (`ATT-8917`, extending `REQ-FIL-028`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-TRK-002`, `REQ-FIL-025.4`).
- **Description**:
  1. *Pure-Kotlin Multisport Transition State Machine (`REQ-FIL-031.1`)*:
     - `MultisportTransitionDetector` (`banalservice/odometry/`) SHALL execute at 1 Hz with zero Android UI/DB coupling, fusing `PhoneCarriageMode`, `KinematicSportTypeDiscriminator` (including permission-free 50-Hz `TYPE_ACCELEROMETER` footstrike inference so `PhoneCarriageClassifier.kt:398` `cyclingPrior += 2.5` cannot mask running in `T2` while a parked bike sensor stays connected without `TYPE_STEP_DETECTOR`), and active non-zero hardware telemetry (`BIKE_POWER`, `BIKE_SPEED_AND_CADENCE`/`BIKE_SPEED`/`BIKE_CADENCE`, `RUN_SPEED`).
     - Maintain a bounded 1-Hz anchor ring buffer `(timestampMs, activeTimeS, totalTimeS, totalDistanceM)` recording `t_exit_sport1` upon leaving confirmed `sport1` (`BIKE` or `RUN`) into `WALKING_TRANSITION`/`STATIONARY` (or direct opposite sport) and `t_enter_sport2` upon first sustained opposite-sport evidence.
     - Gate the `15 s` (`15_000 ms`) continuous confirmation window strictly on active unpaused tracking (`isTracking && !isPaused && !isAutoPaused`); transitions MUST NEVER confirm while `!isTracking`, `PAUSED`, or `AUTO_PAUSED`.
     - Upon `15 s` continuous confirmation of `sport2` (`RUN` after `BIKE`, or `BIKE` after `RUN`), emit a confirmed transition event carrying `(fromSport, toSport, t_exit_sport1, t_enter_sport2, t_confirm)` with wall-clock epoch ms and `(activeTimeS, totalTimeS, totalDistanceM)` anchors.
     - Returning to `sport1` after any `WALKING_TRANSITION`/`STATIONARY` stop (e.g., `160 s` bakery stop `BIKE -> WALK/STAT -> BIKE`) or `< 15 s` opposite-sport noise MUST reset pending candidates cleanly and emit `0` transitions.
  2. *`MyAccumulatorSensor.resetTo(N)` Contract & `BANALService` Precedence (`REQ-FIL-031.2`)*:
     - `MyAccumulatorSensor.resetTo(N targetValue)` SHALL acquire `synchronized (this)`, compute `mInitialValue = sub(targetValue, mValue)`, leave `mLastAbsoluteValue` untouched (so cumulative ANT+/BLE `accumulate()` never drops a tick), snapshot `active = mActivated`, and notify `mSensorListeners` outside the lock with `targetValue` when `active` is true, with ZERO mutation of `MySensorManager.mInitialValues`.
     - `BANALService` SHALL expose dedicated `resetAccumulatorSensorsTo(SensorType, Integer|Double)` helpers and maintain volatile confirmed multisport `BSportType` (`mMultisportBSportType`), strictly subordinate to Tier 1 user lock (`mHaveUserSelectedSportType == true` MUST block auto-multisport), while overriding Tier 2 `BSportType.CONFLICT` or silent/parked bike sensors when `!mHaveUserSelectedSportType`.
     - On confirmed transition, `BANALService` SHALL update `mMultisportBSportType`, reset `mActivityTypeMax` to the new leg's `ActivityType` (ensuring `TrackingTabsScreen` switches `BIKE <-> RUN` tabs/units even when `!changeSportWhenDeviceGetsLost()`), refresh `SpeedAndLocationDevice_ATTFused.handleSportTypeChanged()` (`STRIDES` gating) without broadcasting `SPORT_TYPE_CHANGED_BY_USER_INTENT`, and broadcast package-scoped `BANALService.MULTISPORT_TRANSITION_INTENT` (never raw `LAP_SUMMARY` popups). `RESET_ACCUMULATORS_INTENT`, `onAccumulatorsReset()`, `shutDown()`, and `onDestroy()` MUST reset all detector/multisport state cleanly.
  3. *Retroactive Named Laps, Auto-Pause `T2` Policy, Manual-Lap Clamping & `WorkoutSamples.db` Sync (`REQ-FIL-031.3`)*:
     - `MultisportLapCoordinator` (`trainingtracker/tracker/`) SHALL track `currentLapStartAnchor` (updated on workout start/resume and every non-pause `BANALService.LAP_SUMMARY`), clamping `effectiveExit = max(t_exit, currentLapStart)` and `effectiveEnter = max(t_enter, effectiveExit)`.
     - **Auto-Pause inside `T2` & `REQ-TRK-002` Pre-Increment Evaluation**: `Transition` lap eligibility SHALL evaluate wall-clock `(effectiveEnterMs - effectiveExitMs) >= 10_000L`. Persisted `Laps.TIME_TOTAL_s` for Leg 1 and `Transition` SHALL use active unpaused delta `activeTimeS(end) - activeTimeS(start)`. `hasLeg1` and `hasTransition` MUST be evaluated against `REQ-TRK-002` (`lapTime > 0 || lapDistance > 0.0`) *before* advancing `LAP_NR` (`+2` if both persist, `+1` if only one persists, `+0` if neither) so 100% auto-paused stationary transitions (`0 s` active, `0.0 m`) are skipped cleanly with contiguous `LAP_NR` and zero orphaned sample tags.
     - Persist closed legs via `WorkoutRepository.saveLap(..., timeStart, duration, distance, avgSpeed, name, null)` with `Laps.TIME_START` formatted in UTC `"yyyy-MM-dd HH:mm:ss"` (`Locale.US`, `ZoneOffset.UTC`, matching `FitFileWriter.parseUtcTimestamp()`) and localized `Laps.NAME` (`lap_name_bike`, `lap_name_transition`, `lap_name_run`; English fallback `"Bike"`, `"Transition"`, `"Run"`), then advance `LAP_NR` cleanly and re-seed `TIME_LAP` / `DISTANCE_m_LAP` via `resetAccumulatorSensorsTo` to `(now - effectiveEnter)` deltas while storing active-leg `timeStart` + `name` for `TrackerService.createNewLap()` at workout stop.
     - Update `WorkoutSamples.db` `LAP_NR` asynchronously on `mDbExecutor` (chained after `mTableInitializationFuture`) with **`0` edits** to `WorkoutSamplesDatabaseManager.java` (`1,533 / 1,533` LoC): verify `existsColumnInTable` / `PRAGMA table_info` for `LAP_NR` (adding `INTEGER DEFAULT 1` if missing) before executing parameterized `UPDATE` bounded by `TIME_TOTAL` (or UTC `time`) across the persisted `Transition` and new-leg windows.
  4. *9-Locale Localization Parity (`REQ-FIL-031.4`)*:
     - Add `lap_name_bike`, `lap_name_transition`, and `lap_name_run` across all 9 `values*/strings_cockpit.xml` locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) with zero `&#10;` and exact key parity verified by `TranslationParityTest`.
  5. *Monolith LoC Ceilings & `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` Parity (`REQ-FIL-031.5`)*:
     - Implementation MUST uphold `TrackerService.java <= 3,540` LoC (`+0`), `DeviceManager.java <= 1,707` LoC (`0` edits), `WorkoutSamplesDatabaseManager.java <= 1,533` LoC (`0` edits), `WorkoutRepository.kt <= 4,931` LoC (`+0`), `SpeedAndLocationDevice_ATTFused.kt <= 1,455` LoC (`< 1,500`), `BANALService.java <= 1,075` LoC, zero DB schema changes (`Laps.db` `DB_VERSION = 3` unchanged), `0` new `src/main` test reads (`REQ-PRO-049.1`), and Stage 4–5 sync of `docs/architecture.md`, `docs/architecture/detailed_design/banal_service.md`, `docs/system/sysreq_live_tracking.md`, `docs/system/system_architecture.md`, and `docs/system/system_software_traceability.md` (`Rule 8.8`, `REQ-PRO-131.4`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-031` extending `REQ-FIL-028`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-TRK-002`, and `REQ-FIL-025.4` across `MultisportTransitionDetector.kt`, `MultisportLapCoordinator.kt`, `MyAccumulatorSensor.java`, `SpeedAndLocationDevice_ATTFused.kt`, `BANALService.java`, and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: `ATT-896` (`REQ-TRK-002` zero-duration/zero-distance phantom lap guard), `fee3e805` (`ATT-8890` `PhoneCarriageClassifier`), `a3a5942a` (`ATT-8895` `STRIDES` `BSportType.RUN` gating), `410240a8` (`ATT-8891` `KinematicSportTypeDiscriminator`, `BANALService` 4-tier sport precedence).
3. *Root Reason for Existing Formulation*: `ATT-896` rejected empty `0s/0m` splits to keep UI and FIT/TCX exports clean; `ATT-8891` enforced Tier 1/2 sovereignty; `mActivityTypeMax` guarded cockpit tabs against transient ANT+/BLE dropouts; `MyAccumulatorSensor.reset()` kept `mValue` monotonic for accumulator deltas.
4. *Preservation of Core Invariants*: Tier 1 user sport lock (`mHaveUserSelectedSportType`) stays 100% sovereign; `REQ-TRK-002` phantom-lap rejection & contiguous `1..N` `LAP_NR` are preserved; existing `MyAccumulatorSensor.reset()`/`setInitialValue()` behavior is unchanged; `PhoneCarriageClassifier` never receives circular kinematic hints; `WorkoutSummaries.SPEED_AVERAGE_mps` remains unchanged.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout cycling (`BIKE_*`) until `t = 1800 s`, walking/racking in `T2` (`WALKING_TRANSITION`/`STATIONARY`) `1800..1850 s`, and running (`RUN_*`) from `1850 s`, *When* `RUN_*` confirms after `15 s` (`t = 1865 s`), *Then* the tracker closes `Bike` at `t = 1800 s`, persists `Transition` (`1800..1850 s`, UTC `TIME_START`), anchors `Run` at `t = 1850 s` with `TIME_LAP = 15 s` & `DISTANCE_m_LAP = d(1865)-d(1850)` via `MyAccumulatorSensor.resetTo`, updates `WorkoutSamples.db` `LAP_NR`, and switches `BSportType` to `RUN` (`ActivityType.RUN_SPEED_AND_CADENCE`).
• *Given* cycling with a `160 s` bakery stop (`WALKING_TRANSITION`/`STATIONARY`) followed by `BIKE_*`, or `< 15 s` opposite-sport noise, *When* cycling resumes, *Then* `0` transitions/laps emit and `BSportType` stays `BIKE`.
• *Given* partial or 100% auto-pause inside `T2`, a direct `< 10 s` `BIKE -> RUN` switch, or a manual lap pressed between `t_exit` and `t_confirm`, *When* transition confirms, *Then* `Transition` persists active unpaused duration (skipped cleanly if `0s & 0m` or `< 10 s` wall-clock), boundaries clamp to `currentLapStartAnchor`, and `LAP_NR` remains contiguous without gaps.
• *Given* `mHaveUserSelectedSportType == true`, a parked silent `0 W` bike sensor in `T2` without `TYPE_STEP_DETECTOR` (50-Hz `ACC` footstrikes active) or `CONFLICT`, or `RESET_ACCUMULATORS_INTENT`/`onDestroy()`, *Then* Tier 1 lock blocks transitions, parked-sensor/conflict confirms `RUN` & resets `mActivityTypeMax`, and reset/destroy clears all detector/multisport state cleanly.

**Invariants**: `mHaveUserSelectedSportType == true` strictly blocks auto-multisport; transitions never confirm while `!isTracking`, `PAUSED`, or `AUTO_PAUSED`; same-sport stops emit `0` transitions; `MyAccumulatorSensor.resetTo()` preserves `mValue`/`mLastAbsoluteValue`, notifies outside lock, and never mutates `MySensorManager.mInitialValues`; `Laps.TIME_START` UTC `"yyyy-MM-dd HH:mm:ss"`; contiguous `1..N` `LAP_NR`; zero DB schema changes (`Laps.db` v3); `TrackerService <= 3,540` (`+0`), `DeviceManager <= 1,707` (`0` edits), `WorkoutSamplesDatabaseManager <= 1,533` (`0` edits), `WorkoutRepository <= 4,931` (`+0`), `SpeedAndLocationDevice_ATTFused <= 1,455`, `BANALService <= 1,075`; 9-locale parity (`lap_name_bike`/`transition`/`run`); `0` new `src/main` test reads (`REQ-PRO-049.1`).

### REQ-SEN-032: WheelPositionSelectionDialog Front/Rear-Only Selection with Legacy UNSPECIFIED Fallback [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SEN-032`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WheelPositionSelectionDialog.kt`
- **Rationale**: Following PO review of `ATT-8719`, offering `Unspecified` (`WheelPosition.UNSPECIFIED`) as an interactive selection in `WheelPositionSelectionDialog` encouraged ambiguous wheel-sensor links that disable Front/Rear odometry differentiation (`ATT-7752`) and dual-wheel iTPMS (`ATT-7753`). Restricting interactive choices strictly to `Front` and `Rear` while preserving `WheelPosition.UNSPECIFIED` in `WheelPosition`, `Equipment.db` v4, and `EditDeviceDialog` legacy summary display enforces clean sensor mounting intent without breaking existing rows (`ATT-8859`, refining `REQ-SEN-029.3` and `REQ-SEN-031.2`):
- **Description**:
  1. *Front & Rear Only Interactive Options (`REQ-SEN-032.1`)*:
     - `WheelPositionSelectionDialog` SHALL render strictly two selectable mounting-position options: `WheelPosition.FRONT` (`R.string.equipment_wheel_position_front`, tag `wheel_position_front`) and `WheelPosition.REAR` (`R.string.equipment_wheel_position_rear`, tag `wheel_position_rear`).
     - `WheelPositionSelectionDialog` MUST NOT render `WheelPosition.UNSPECIFIED` (`wheel_position_unspecified` / `R.string.equipment_wheel_position_unspecified`).
  2. *Legacy `UNSPECIFIED` State, `Unlink` & `Cancel` Semantics (`REQ-SEN-032.2`)*:
     - When `WheelPositionSelectionDialog` opens with `currentPosition == WheelPosition.UNSPECIFIED` (legacy DB link), neither `Front` nor `Rear` radio button SHALL be pre-selected (`selected == false`), `Unlink` (`wheel_position_unlink` when `isLinked == true`) and `Cancel` (`wheel_position_cancel`) SHALL remain active, and tapping `Front` or `Rear` SHALL invoke `onSelectPosition(WheelPosition.FRONT / REAR)` cleanly.
     - Conflict/swap hints (`conflictHints`) MUST continue rendering on `Front` and `Rear` options.
  3. *Preserved Enum, DB Schema, Legacy Display & Private `@Preview` Hygiene (`REQ-SEN-032.3`)*:
     - `WheelPosition.UNSPECIFIED`, `EquipmentDbHelper` v4 `DEFAULT 'UNSPECIFIED'`, `RawDeviceDataProvider` / `EditDeviceViewModel` fallback, and 9-locale `R.string.equipment_wheel_position_unspecified` (actively used by `EditDeviceDialog.kt` legacy summary/sheet display) MUST remain unchanged (`Localization Parity: N/A - no UI strings`).
     - Any Compose `@Preview` functions in `WheelPositionSelectionDialog.kt` MUST use `private fun` (`REQ-ARC-003.5`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-SEN-029` (`REQ-SEN-029.3`, `ATT-7752`) and `REQ-SEN-031` (`REQ-SEN-031.2`, `ATT-8719`) targeting `WheelPositionSelectionDialog.kt`.
2. *Historical Origin & Commit Trace*: `4edcda86` (`ATT-7752` introduced `WheelPositionSelectionDialog` with 3 options mirroring `WheelPosition` enum) and `b08c779e` (`ATT-8719` reused it in `EditDeviceDialog.kt`).
3. *Root Reason for Existing Formulation*: `UNSPECIFIED` was exposed in the UI dialog to match the 3-value database enum and `EquipmentDbHelper` v4 migration default.
4. *Preservation of Core Invariants*: `WheelPosition.UNSPECIFIED` enum value, `Equipment.db` schema v4 `DEFAULT 'UNSPECIFIED'`, `WheelPosition.fromString(...)` fallback, `R.string.equipment_wheel_position_unspecified` 9-locale display in `EditDeviceDialog.kt`, and `swapConflict = true` guardrail remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WheelPositionSelectionDialog` opened from `EditDeviceDialog` or `EquipmentSensorMatrixScreen`, *When* rendered, *Then* strictly `Front` (`wheel_position_front`) and `Rear` (`wheel_position_rear`) options render and `wheel_position_unspecified` does not exist.
• *Given* a legacy linked bike with `currentPosition = WheelPosition.UNSPECIFIED` and `isLinked = true`, *When* `WheelPositionSelectionDialog` opens, *Then* neither radio button is selected, `Unlink` and `Cancel` remain functional, and selecting `Front` or `Rear` invokes `onSelectPosition(FRONT / REAR)` cleanly.
• *Given* `EditDeviceDialog` rendering a legacy `UNSPECIFIED` bike link, *When* rendered, *Then* `EditDeviceDialog` still displays localized `equipment_wheel_position_unspecified` and `WheelPosition.values().size == 3` holds.

**Invariants**: `WheelPosition.UNSPECIFIED` enum/DB/fallback untouched; `R.string.equipment_wheel_position_unspecified` retained across all 9 locales; `EditDeviceDialog.kt <= 2,004` (`0` edits); `WheelPositionSelectionDialog.kt` `@Preview`s strictly `private fun` (`REQ-ARC-003.5`); `0` `src/main` test reads (`REQ-PRO-049.1`); `Rule 8.5` Light/Dark PNG verified.

### REQ-FIL-032: Post-Workout ERTS Beat-Immune 1-Hz Timestamping, Authoritative speedAverage_mps Parity & Stationary Indoor Coordinate Pinning [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-032`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmoother.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt`
- **Rationale**: Pixel 10 telemetry (`Zum Bäcker #58`, `workoutID = 2411`, `584 s`) exposed three residual post-workout ERTS defects: (1) asynchronous 1-Hz `ClockDevice` vs. `TrackerService` `TIME_TOTAL` `{1: 427, 2: 78, 0: 78}` beat pairs ratchet `baseEpochMs` forward by `+1 s` per `2 ... 0` pair (`+78 s` -> `662 s`); (2) `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()` and `updateExtrema()` write unweighted sample mean `speeds.average()` (`3.050 m/s`), disagreeing by `7%` with `distanceTotal_m / timeActive_s` (`3.271 m/s`) and `Laps.db` (`3.265 m/s`); and (3) forward stationary `UnicycleOdometryEkf.predict()` keeps `F[0..1, 2] * P[2][2]` (`~2.0 m^2/s`) + `Q_POS` active during `118 s` indoor stops (`acc = 48.5 m > 20 m`), causing backward RTS smoothing to drift `35.66 m` across the building while `speedMps == 0.00 m/s` (`ATT-9064`, refining `REQ-FIL-017.1`/`.3`, `REQ-FIL-025.4`, `REQ-FIL-027.1`/`.3`, `REQ-FIL-028.4`, `REQ-TRK-030.3`).
- **Description**:
  1. *Beat-Immune 1-Hz Timestamp Reconstruction (`REQ-FIL-032.1`)*:
     - `ErtsPostWorkoutRecomputer.readWorkoutSamples()` SHALL progress active 1-Hz timestamps via `oneHzTs = prevTs + 1000L` without ratcheting `baseEpochMs` on `TIME_TOTAL` `{0, 2}` clock-beat pairs in any ordering (`0 -> 2`, `2 -> 0`, or separated beats).
     - It SHALL detect and re-anchor (`baseEpochMs`, `firstTimeTotalSec`) strictly on genuine `> 5_000L ms` pauses—signaled by single-step `deltaTimeTotal > 5L` (or `timeTotalTs - oneHzTs > 5_000L`) OR wall-clock `(parsedTs - oneHzTs) > 5_000L` (including when a `> 5 s` wall pause lands on a `timeTotalSec == prevTimeTotalSec` row or leading/all-`null` `TIME_TOTAL` rows)—preserving `TST-FIL-027.1` `6 s` batch-flush stall immunity and `30 s`/`25 s` double pauses.
  2. *Authoritative `speedAverage_mps` & `ExtremaType.AVG(SPEED_mps)` Parity (`REQ-FIL-032.2`)*:
     - `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()` and `updateExtrema()` SHALL persist one unified finite non-negative average speed to both `WorkoutSummaries.SPEED_AVERAGE_mps` and `ExtremumValues(SensorType.SPEED_mps, ExtremaType.AVG)`: (a) `totalDistance / TIME_ACTIVE_s` when persisted `WorkoutSummaries.TIME_ACTIVE_s > 0` (matching `TrackerService.getAverageSpeed()` & `LapsDatabaseManager.syncLapDistancesAndSpeeds()`); (b) else matched active ratio `activeDist / activeDt` over steps with `dt in (0.0, MAX_ACTIVE_SAMPLE_GAP_SEC (5.0)]` (preventing pause-gap inflation when `TIME_ACTIVE_s` is `NULL`/`<= 0`); (c) else finite non-negative unweighted mean (`0.0` if empty/non-finite).
  3. *Stationary `[E, N]` Covariance Freeze & Backward RTS Coordinate Pinning (`REQ-FIL-032.3`)*:
     - `UnicycleOdometryEkf.predict()` SHALL add `@JvmOverloads isStationary: Boolean = false` (leaving live `SpeedAndLocationDevice_ATTFused` untouched); when `isStationary == true`, `predict()` MUST freeze `x[0..1]`, set `F[0..1, :] = [I_2, 0]`, and zero position process noise (`Q_POS = 0.0`) while keeping heading/bias propagation and positive-definite `P_{k+1|k}` invertibility intact.
     - `ExtendedRtsOdometrySmoother.smoothSamples()` SHALL pass `isStationary = isStationary` to `ekf.predict()`, and `smoothSnapshots()` SHALL pin `smoothedStates[k][0..1] = smoothedStates[k + 1][0..1]` for consecutive stationary steps (`currStationary && nextStationary`), guaranteeing `0.00 m` Haversine coordinate drift across stationary indoor stops while preserving `TST-FIL-027.3` post-outage GNSS re-acquisition and canopy Doppler-dropout (`wheelSpeedMps = 10.0`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-032` refining `REQ-FIL-017.1`/`.3`, `REQ-FIL-025.4`, `REQ-FIL-027.1`/`.3`, `REQ-FIL-028.4`, and `REQ-TRK-030.3` across `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, and `UnicycleOdometryEkf.kt`.
2. *Historical Origin & Commit Trace*: `ed6acfee` (`ATT-7750`), `d96fd52f` (`ATT-8705`), `14935efb` (`ATT-8880`), `fee3e805` (`ATT-8890`).
3. *Root Reason for Existing Formulation*: `ATT-8880` assumed `TIME_TOTAL` was strictly 1-Hz monotonic (no `0/2` beat pairs) and decoupled `[0..1, 2]` cross-covariance without freezing forward stationary `F * P * F^T` / `Q_POS` or pinning backward `[E, N]`.
4. *Preservation of Core Invariants*: Preserves raw device-column immutability (`REQ-FIL-017.3`), `KEY_POST_WORKOUT_ERTS_SMOOTHING = false`, live `UnicycleOdometryEkf` defaults (`isStationary = false`, `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`), `TST-FIL-027.1` flush-stall/double-pause behavior, `0` protected monolith edits (`ErtsPostWorkoutRecomputer`, `ExtendedRtsOdometrySmoother`, `UnicycleOdometryEkf` `< 1,500` LoC), `0` DB schema changes, `0` new `src/main` test reads (`REQ-PRO-049.1`), and `N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `584`-row 1-Hz `WorkoutSamples.db` table with `78` `TIME_TOTAL` `0/2` beat pairs (`2 -> 0` and `0 -> 2`), *When* `ErtsPostWorkoutRecomputer` reconstructs timestamps and updates summaries/extrema/laps, *Then* the span equals `583 s` (not `662 s`), and `WorkoutSummaries.speedAverage_mps`, `ExtremumValues(SPEED_mps, AVG)`, and `Laps.speedAverage_mps` agree with `totalDistance / TIME_ACTIVE_s` within `0.02 m/s` (while `NULL` `TIME_ACTIVE_s` uses matched `activeDist / activeDt` over `dt <= 5.0 s`).
• *Given* a workout with a `118 s` indoor stationary stop (`speed == 0.00 m/s`, `acc = 48.5 m`) between arrival and `+35.66 m` departure GNSS re-acquisition, *When* `ExtendedRtsOdometrySmoother.smoothSamples()` runs, *Then* both `deltaDistanceMeters` and Haversine coordinate movement across the stationary stop equal `0.00 m` while pre-/post-stop moving speeds stay `> 4.0 m/s`.

**Invariants**: Raw device columns immutable (`REQ-FIL-017.3`); live `UnicycleOdometryEkf` untouched (`isStationary = false`, `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`); `> 5 s` wall/total pauses & `6 s` batch-flush stall preserved (`TST-FIL-027.1`); `0` DB schema bumps; `0` monolith LoC edits; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

### REQ-FIL-033: Post-Workout ERTS Altitude/Slope DEM Grid-Step Decoupling, Contractive OU Standstill Decay, Barometer-Free Fallback & Speed-Coupled Slew Limit [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-033`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmoother.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/BarometricAltitudeFilter.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt`
- **Rationale**: Field ride `Zum Bäcker #58` (`workoutID = 2411`, `584 s`) showed post-workout `ExtendedRtsAltitudeSlopeSmoother` producing a `+20.5 m` initial altitude overshoot (`458.48 m` vs. `DEM = 438.00 m`, `BARO = 437.19 m`), `-91%` slope at `t = 4..31 s`, and `21.97 m` of phantom initial descent after an integer DEM grid step (`438 -> 435 m`) during slow startup (`~5 m`), `20 s` frozen-slope standstill, and backward RTS amplification; additionally, fixed `BIKE_FRONT_POCKET` `0.7 m/s` barometric slew clips genuine `-1.8..-2.0 m/s` fast alpine descents (`17–20 m/s` at `-10%`) (`ATT-9063`, refining `REQ-FIL-017.2`/`.3`, `REQ-FIL-023.2`/`.3`, `REQ-FIL-027.5`, `REQ-FIL-028.4`, `REQ-TRK-057.1`).
- **Description**:
  1. *DEM Grid-Step Slope Decoupling, Physical Bounds, Low-Speed Damping & Contractive OU Standstill Decay (`REQ-FIL-033.1`)*:
     - `ExtendedRtsAltitudeSlopeSmoother.smooth()` SHALL clamp slope `x[1]` and backward-smoothed `smoothedStates[k][1]` to `[-MAX_PHYSICAL_SLOPE, +MAX_PHYSICAL_SLOPE]` (`±0.35`, `±35%`), initialize flat-start slope variance `p[1][1] = 0.0025` (`5%` SD), cap moving `p[1][1]` at `MAX_SLOPE_VARIANCE = 0.015` (computing `p00New`/`p01New` from pre-update `p[1][1]` before capping `p11New`), and damp moving slope process noise (`qgVal`) and barometer slope gain (`k1`) via `lowSpeedSlopeGainFactor(speed)` (`0.0` below `0.3 m/s`, quadratic ramp to `1.0` at `1.2 m/s`).
     - During sustained standstill (`standstillDurationSec >= 2.0 s`), it SHALL apply exact continuous linear Ornstein-Uhlenbeck decay (`alpha = exp(-dtSec / 2.5)`, `x[1] *= alpha`, `fMat[1][1] = alpha`, `p[0][1] *= alpha`, `p[1][2] *= alpha`, `p[1][1] = alpha^2 * p[1][1] + (1 - alpha^2) * max(0.0025, p[1][1])`) with no hard zero-snap discontinuity so `priors[k]` matches `jacobians[k]` identically and backward RTS gain stays strictly contractive ($0 < C_k[1][1] \le \alpha < 1$).
     - When `hasBaroData` (`samples.any { it.rawAltitudeM.isFinite() }`), Step 3 reference updates MUST set `k1 = 0.0` (decoupling DEM grid steps from slope); all Step 2 (moving and standstill $H=[0,0,1]$) and Step 3 covariance updates MUST use exact 3×3 Joseph form $P^+ = (I - K H) P^- (I - K H)^T + K R K^T$.
  2. *Speed-Coupled Barometric Slew Limiting (`REQ-FIL-033.2`)*:
     - `BarometricAltitudeFilter.computeSpeedCoupledSlewRateMps(baseMaxSlewRateMps, speedMps)` SHALL return `max(safeBase, 0.25 * clampedSpeed)`, explicitly returning `safeBase` when `!speedMps.isFinite() || speedMps <= 0.0` (preventing Kotlin `Double.NaN.coerceIn` propagation), sanitizing non-finite/`<= 0` base rates to `DEFAULT_MAX_SLEW_RATE_MPS (1.2)`, and capping finite positive speed at `35.0 m/s`.
     - `@JvmOverloads BarometricAltitudeFilter.filterSlewOnly(rawAltitude, timestampMs, speedMps = 0.0)` SHALL use the speed-coupled slew limit without mutating `maxSlewRateMps`, and `ErtsPostWorkoutRecomputer.resolveCarriageModesAndFilterBaro()` SHALL pass finite non-negative `epochSpeedMps` (`wheelSpeedMps` else `speedMps` else `0.0`, sanitized with `.takeIf { it.isFinite() && it >= 0.0 }`).
  3. *Barometer-Free Fallback & NaN/Leading-Null Immunity (`REQ-FIL-033.3`)*:
     - When `!hasBaroData` (barometer-free DEM/GPS-only stream), Step 3 SHALL allow damped, `±0.35`-clamped slope learning (`k1 = if (isStandstill) 0.0 else (p[1][0] / sRef) * gainScale`) via Joseph covariance update.
     - `ExtendedRtsAltitudeSlopeSmoother.smooth()` SHALL initialize `initialAlt` by sequentially scanning `for (s in samples)` taking `s.referenceAltitudeM?.takeIf { it.isFinite() } ?: s.rawAltitudeM.takeIf { it.isFinite() }` (fallback `0.0`), set `hasInitialRef = (samples[0].referenceAltitudeM?.isFinite() == true)` strictly at `k = 0`, and apply the same finite guard for `n == 1`.
     - `ErtsPostWorkoutRecomputer` SHALL pass `rawAltitudeM = row.rawAltitude ?: Double.NaN` in `runAltitudeSmoothing()` and gate altitude smoothing on `rawRows.any { it.rawAltitude != null || it.referenceAltitude != null }`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-033` refining `REQ-FIL-017.2`/`.3`, `REQ-FIL-023.2`/`.3`, `REQ-FIL-027.5`, `REQ-FIL-028.4`, and `REQ-TRK-057.1` across `ExtendedRtsAltitudeSlopeSmoother.kt`, `BarometricAltitudeFilter.kt`, and `ErtsPostWorkoutRecomputer.kt`.
2. *Historical Origin & Commit Trace*: `e079f7a9` (`ATT-6265`), `75c1497e` (`ATT-6279`), `ed6acfee` (`ATT-7750`), `14935efb` (`ATT-8880`), `fee3e805` (`ATT-8890`).
3. *Root Reason for Existing Formulation*: `ATT-7750` predated `ATT-8576` live slope hardening (`REQ-FIL-023.2`/`.3`), and `ATT-8890` set fixed `0.7 m/s` `BIKE_FRONT_POCKET` slew without horizontal speed coupling.
4. *Preservation of Core Invariants*: Raw device columns immutable (`REQ-FIL-017.3`), `ASCENT_DESCENT_HYSTERESIS_METERS = 2.0`, `VERTICAL_SPEED` `[-3000, 3000] m/h`, `BarometricAltitudeFilter.maxSlewRateMps` & 2-arg `filterSlewOnly` unchanged, live `AltitudeAndSlopeDevice_ATTFused`/`KinematicAltitudeSlopeEkf` untouched, `0` DB schema bumps, `0` monolith LoC edits (`< 1,500` LoC), `0` new `src/main` test reads (`REQ-PRO-049.1`), and `Localization Parity: N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a closed-loop workout starting at `438 m` (`BARO ~ 437.5 m`, `DEM = 438 m`) with a `-3 m` or `-10 m` DEM step at `t = 4 s` during slow startup (`~5 m`), followed by `20 s` standstill and flat/hilly return, *When* `ExtendedRtsAltitudeSlopeSmoother.smooth()` runs, *Then* `smoothed[0].altitudeM` stays within `±2.0 m` of `438.0 m`, `|slopePercent| <= 35%` (`< 10%` on flat start), and `|ascentM - descentM| <= 3.0 m`.
• *Given* an `8–15%` climb and an `18 m/s` `-10%` (`-1.8 m/s`) alpine descent in `BIKE_FRONT_POCKET`, plus slow pocket spikes and non-finite speeds, *When* `BarometricAltitudeFilter.filterSlewOnly(..., speedMps)` and `ErtsPostWorkoutRecomputer` + `ExtendedRtsAltitudeSlopeSmoother.smooth()` run, *Then* `-1.8 m/s` passes unclipped while slow spikes stay clamped, `maxSlewRateMps == 0.7`, and non-finite speeds safely return `safeBase`.
• *Given* a barometer-free DEM/GPS-only workout (including leading-null rows and `n == 1` non-finite sample), *When* `ErtsPostWorkoutRecomputer` and `ExtendedRtsAltitudeSlopeSmoother.smooth()` run, *Then* elevation, slope (`±35%`), and ascent/descent remain finite and smooth without NaN/overshoot.

**Invariants**: Raw device columns immutable (`REQ-FIL-017.3`); `ASCENT_DESCENT_HYSTERESIS_METERS = 2.0`; `BarometricAltitudeFilter.maxSlewRateMps` & 2-arg `filterSlewOnly` unchanged; live `AltitudeAndSlopeDevice_ATTFused` / `KinematicAltitudeSlopeEkf` untouched; `0` DB schema bumps; `0` monolith LoC edits; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

### REQ-FIL-034: PhoneCarriageClassifier Indoor GNSS-Outage Stationary Transition, Cyclic-Immune Glance Gating, Multipath Speed Gating & Cross-Device Portability [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-034`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt`
- **Rationale**: Field ride `Zum Bäcker #58` (`workoutID = 2411`, `584 s`) exposed three live carriage classification defects: (1) during a `118 s` indoor bakery stop (`v = 0.00 m/s`), GNSS staleness (`> 6 s`) caused `evaluateRawCandidate()` to return `UNKNOWN` and freeze `committedMode = RUN_WAIST_HIP (5)` while `SpeedAndLocationDevice` zero-decay never reached `carriageClassifier`; (2) `SpeedAndLocationDevice_ATTFused.onLocationChanged()` fed raw multipath `location.speed` (`2.89 m/s @ acc = 43.9 m`) directly to `carriageClassifier` and `isMovingByGnss`; and (3) cyclic `0.8–1.8 Hz ±25°` jeans-pocket thigh flexion falsely triggered `detectGlanceShift()` (`+2.2`) alongside speed-only `runningPrior`, misclassifying front-pocket cycling acceleration (`2.5–5.4 m/s`) as `RUN_HANDHELD (7)` (`ATT-9062`, refining `REQ-FIL-028.1`/`.3`, `REQ-FIL-029`, `REQ-FIL-030`).
- **Description**:
  1. *Indoor GNSS-Outage, Stagnant-Fix & Low-Speed Quiet-IMU `STATIONARY` Transition (`REQ-FIL-034.1`)*:
     - `PhoneCarriageClassifier.evaluateRawCandidate()` SHALL classify `PhoneCarriageMode.STATIONARY (0)` (subject to dwell hysteresis) when `(hasOrientation || hasGyro)`, `quietImu` holds (`swingSdDeg < 2.5`, `rawGyroRms < 0.14`, `stepFreqHz == null`, no active accelerometer footstrikes), and `!wheelActiveMoving`, under: (a) stale GNSS (`effectiveSpeed == null`) after prior speed ingestion or non-`UNKNOWN` committed mode (preserving initial `UNKNOWN` for pure IMU-only checks with zero prior speed); (b) `effectiveSpeed <= 1.8 m/s`; or (c) stagnant cached GNSS positions (`>= 4.0 s` span, `>= 3` fixes, `< 2.0 m` spread).
     - `SpeedAndLocationDevice_ATTFused` SHALL override `triggerDecayTick()` to propagate `0.0 m/s` to `carriageClassifier.ingestGnssSpeed(0.0, null, null, nowMs)` and invoke `updateCarriageClassification(nowMs, forceClassify = true)` using injectable `currentTimeMillis()` when `mSpeed <= SPEED_ZERO_THRESHOLD`.
  2. *Cyclic-Immune `detectGlanceShift()` & Front-Pocket Cycling (`REQ-FIL-034.2`)*:
     - `PhoneCarriageClassifier.detectGlanceShift()` SHALL require `prevSpanMs >= 1_200L` (`prevN >= 4`) and MUST reject periodic thigh oscillation whenever `(recSdDeg > 8.0 && deltaDeg < 2.6 * recSdDeg)` OR `(prevSdDeg >= 7.0 && deltaDeg <= 1.25 * prevPeakDeg)`, preventing false glance bonuses during `0.8–1.8 Hz ±25°` front-pocket pedaling while preserving genuine sustained wrist-glance holds (`70° -> 18–20°` and `70° -> 35°`).
  3. *Degraded Multipath GNSS Speed & `isMovingByGnss` Gating (`REQ-FIL-034.3`)*:
     - `SpeedAndLocationDevice_ATTFused.onLocationChanged()` SHALL define `hasTrustedGpsSpeed = hasGpsSpeed && acc > 0.0 && acc <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS` and gate BOTH `speedForClassifier` and `isMovingByGnss` on `hasTrustedGpsSpeed` (passing `lat`/`lon` for stagnation tracking strictly when `acc > 0.0 && acc <= MAX_GNSS_ACCURACY_METERS`), preventing degraded multipath fixes (`acc = 43.9 m`, `2.89 m/s`) from triggering `RUN_*` or defeating `forceStationaryPinning`.
  4. *Cross-Device & Cross-Athlete Portability (`REQ-FIL-034.4`)*:
     - `PhoneCarriageClassifier` SHALL distinguish `stepDetectorUnavailable` from `stepDetectorActiveAndZero` via `setStepDetectorRegistered(Boolean)` (default `false`) and reuse `ImuCadenceDsp` (`50 Hz` time-based `UniformGridResampler`, `BiquadBandPass(50.0, 2.1, 4.2)`, `DoubleRingBuffer(250)`, `findFundamentalLagResult`) via `ingestAccelerometer(ax, ay, az, timestampMs)`, requiring BOTH `2.1–4.2 Hz` bandpass RMS `>= 1.2 m/s²` AND normalized ACF `peakCorrelation >= 0.42` over `2.3..3.8 Hz` (lags `13..22`), plus `>= 2.0 s` active accel span before `hasConfirmedNoFootstrikes = true`.
     - `effectiveRunStepFreqHz = stepFreqHz ?: accelFootstrikeFreqHz` SHALL award `runningPrior += 5.0` ahead of speed cutoffs (`>= 5.5 m/s`) so `5.6 m/s` interval runners and permission-denied/barometer-free runners classify as `RUN_*`; `confirmedNoFootstrikes = stepDetectorActiveAndZero || hasConfirmedNoFootstrikes` SHALL gate the `WALKING_TRANSITION` short-circuit with `!(confirmedNoFootstrikes && effectiveSpeed >= 1.6)` and award `cyclingPrior` (not `runningPrior += 0.6`) when `confirmedNoFootstrikes && effectiveSpeed >= 1.8` so `2.0 m/s` `60 RPM` uphill MTB climbers classify as `BIKE_*` while step-detector-less walking (`PhoneCarriageClassifierTest:295`) stays `WALKING_TRANSITION`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-034` refining `REQ-FIL-028.1`/`.3`, `REQ-FIL-029`, and `REQ-FIL-030` across `PhoneCarriageClassifier.kt` and `SpeedAndLocationDevice_ATTFused.kt`.
2. *Historical Origin & Commit Trace*: `fee3e805` (`ATT-8890`), `a3a5942a` (`ATT-8895`), `410240a8` (`ATT-8891`), `a93f3230` (`ATT-8917`).
3. *Root Reason for Existing Formulation*: `ATT-8890` returned `UNKNOWN` on stale speed to keep IMU-only unit checks neutral and predated `ATT-8895`'s `ImuCadenceDsp` bandpass/ACF primitives and step-detector registration awareness.
4. *Preservation of Core Invariants*: `PhoneCarriageMode` codes (`-1..7`) & `CarriageFilterProfile` untouched; `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` untouched; IMU-only initial `UNKNOWN` state preserved; `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC (`0` protected monolith edits); time-based windows only; `0` DB schema bumps; `0` new `src/main` test reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout entering a bakery (`1.44 m/s`, `acc = 48.5 m`) followed by `120 s` indoor standstill (GNSS outage or stagnant cached fixes) with quiet IMU and zero steps, *When* `PhoneCarriageClassifier.classify()` or `triggerDecayTick()` runs, *Then* mode transitions to and stays in `STATIONARY (0)` instead of freezing on `RUN_WAIST_HIP (5)`.
• *Given* front-pocket cycling acceleration `2.5 -> 5.4 m/s` (`sportHint == UNKNOWN`, `wheelSpeed == null`, `stepDetectorActiveAndZero`, cyclic `0.8–1.8 Hz ±25°` thigh flexion), *When* `PhoneCarriageClassifier.classify()` evaluates, *Then* `detectGlanceShift()` fires `0` false glances and output is `BIKE_FRONT_POCKET (4)` (while genuine wrist glances still trigger `isGlanceActive == true`).
• *Given* walking (`ekf.speedMps ~ 1.0 m/s`) encountering an indoor multipath fix (`acc = 43.9 m`, `speed = 2.89 m/s`), *When* `SpeedAndLocationDevice_ATTFused.onLocationChanged()` runs, *Then* `2.89 m/s` is rejected from `speedForClassifier` and `isMovingByGnss` and mode stays `WALKING_TRANSITION (1)` / `STATIONARY (0)`.
• *Given* (a) a barometer-free `ACTIVITY_RECOGNITION`-denied runner, (b) a `5.6 m/s` interval runner (`3.0 Hz` footstrikes), and (c) a `2.0 m/s` `60 RPM` uphill MTB climber (no footstrikes), *When* `PhoneCarriageClassifier` evaluates each stream, *Then* (a) and (b) classify as `RUN_*` and (c) classifies as `BIKE_*` (never `RUN_*`).

**Invariants**: `PhoneCarriageMode` codes (`-1..7`) & `CarriageFilterProfile` untouched; `MIN_DOPPLER_SPEED_MPS = 3.0` untouched; `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC (`0` protected monolith edits); time-based windows only; `0` DB schema bumps; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

### REQ-FIL-035: PhoneCarriageClassifier Front-Hip Running Belt, Easy-Pace Running & Sport-Context Guardrail Calibration [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-035`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt`
- **Rationale**: A 44-minute Pixel 10 field run (`2026-11-19_184953`, `Sport = RUN`, phone carried continuously in an elastic front-hip Salomon running belt with one slow obstacle climb at `19:12:54–19:18:04`) logged `RUN_WAIST_HIP (5)` only `0.3%` (`8 s`), `WALKING_TRANSITION (1)` `52.2%` (`1,386 s`), `RUN_HANDHELD (7)` `34.3%` (`911 s`), cycling modes `13.0%` (`BIKE_FRONT_POCKET 343 s`, `BIKE_JERSEY_BACK 3 s`), and `389` mode switches (`6.8 s` mean segment duration) due to (1) soft `runningPrior` allowing `BIKE_*` bleed without wheel/`>= 5.5 m/s` cycling evidence, (2) easy-pace runs (`1.65–2.5 m/s`) getting trapped in `walkingStepBand`/`walkingSpeedBand`, (3) rigid rear-clip penalty cliffs in `scoreRunWaistHip()`, and (4) `SCORE_MARGIN = 0.25` + `2.0 s` `RUN -> WALK` dwell (`ATT-9157`, refining `REQ-FIL-028.1`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-FIL-031`, `REQ-FIL-034`).
- **Description**:
  1. *Sport-Context Cycling Guardrail with Brick Override & Non-Sticky Eviction (`REQ-FIL-035.1`)*:
     - When `suppressCyclingForRun` holds (`sportHint == SportContextHint.RUNNING && !wheelActiveMoving && (wheelSpeed == null || wheelSpeed <= 0.5) && (effectiveSpeed == null || effectiveSpeed < 5.5)`), `PhoneCarriageClassifier.evaluateRawCandidate()` SHALL exclude `BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, and `BIKE_FRONT_POCKET` from candidate scoring and zero-IMU fallback, and map any stale `BIKE_*` `committedMode` to `committedScore = Double.NEGATIVE_INFINITY` so `SCORE_MARGIN` never retains a masked cycling mode.
     - Once moving wheel speed (`wheelActiveMoving || wheelSpeed > 0.5`) or cycling velocity (`effectiveSpeed >= 5.5 m/s`) occurs, `suppressCyclingForRun` SHALL lift automatically to preserve triathlon/brick transition detection (`REQ-FIL-031`).
  2. *Easy-Pace Running Walking-Gate Calibration & Front-Hip Running Belt Scoring (`REQ-FIL-035.2`)*:
     - `AccelFootstrikeEval` SHALL expose `impactRmsMps2: Double?` (`2.1–4.2 Hz` bandpass RMS). Scoped strictly to `sportHint == SportContextHint.RUNNING && effectiveSpeed != null && effectiveSpeed >= 1.65`, `easyRunSuppressWalk` SHALL hold when `impactRmsMps2 >= 1.8`, OR step cadence `>= 2.15 Hz`, OR (`effectiveSpeed >= 1.75` and `impactRmsMps2 == null || impactRmsMps2 >= 1.2`), bypassing BOTH `walkingStepBand` and `walkingSpeedBand` while preserving `< 1.65 m/s` / low-impact obstacle climbs and all non-running dismount flows.
     - `scoreRunWaistHip` (`swingSd < 10.0 -> +2.6`, `< 15.5 -> +1.8`, `< 22.0 -> -0.5`, else `-2.0`; `yaw <= 0.48 -> +2.2`, `<= 0.68 -> +1.4`, `<= 0.95 -> -0.4`, else `-1.5`; `baro < 0.45 -> +1.2`; `step >= 2.15 -> +0.8`; `glance == 0 -> +1.2`, `>= 1 -> -1.5`), `scoreRunBicepArm` (`swingSd >= 15.5 -> +2.6`, `>= 11.5 -> +0.8`, else `-0.8`), and `scoreRunHandheld` (`swingSd >= 15.0 -> +1.8`, `>= 11.0 -> +0.4`; `yaw >= 0.55 -> +2.6`, `>= 0.42 -> +0.8`; `baro >= 0.45 -> +1.8`; `glance >= 1 -> +2.8`, `== 0 -> -1.4`) SHALL smoothly classify front-hip running belts as `RUN_WAIST_HIP` while preserving rear-clip, bicep armband (`17°`), and handheld/wrist-glance separation.
  3. *Chatter-Resistant Margin & Asymmetric Running Dwell Hysteresis (`REQ-FIL-035.3`)*:
     - `PhoneCarriageClassifier.SCORE_MARGIN` SHALL be `0.85`.
     - Dwell hysteresis SHALL be `0 ms` for verified wrist-glance `RUN_* -> RUN_HANDHELD`, `mountHysteresisMs (4_000L)` for `RUN_* -> WALKING_TRANSITION`, `maxOf(mountHysteresisMs, 6_000L)` for non-glance intra-run `RUN_* -> RUN_*`, and fast `dismountHysteresisMs (2_000L)` for `WALKING_TRANSITION -> RUN_*`, `RUN_* -> STATIONARY`, `STATIONARY <-> *`, and `BIKE_* <-> WALKING_TRANSITION`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-035` refining `REQ-FIL-028.1`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-FIL-031`, and `REQ-FIL-034` strictly inside `PhoneCarriageClassifier.kt`.
2. *Historical Origin & Commit Trace*: `fee3e805` (`ATT-8890`), `a3a5942a` (`ATT-8895`), `410240a8` (`ATT-8891`), `a93f3230` (`ATT-8917`), `0210b827` (`ATT-9062`).
3. *Root Reason for Existing Formulation*: `ATT-8890` calibrated rigid rear-clip thresholds and a soft `+2.5` running prior before elastic front-hip belt field validation and `ATT-8917` brick override triggers.
4. *Preservation of Core Invariants*: `PhoneCarriageMode` codes (`-1..7`) & `CarriageFilterProfile` untouched; `SpeedAndLocationDevice_ATTFused.kt = 1,459 / 1,500` LoC (`0` edits); `PhoneCarriageClassifier.kt <= 1,500` LoC (`0` protected monolith edits); all `ATT-9062` (`REQ-FIL-034`) & `ATT-8890` (`REQ-FIL-028`) tests preserved; time-based windows only; `0` DB schema bumps; `0` new `src/main` test reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `sportHint == RUNNING` with `wheelSpeed <= 0.5` (or `null`) and `effectiveSpeed < 5.5 m/s`, *When* `PhoneCarriageClassifier.classify()` evaluates (including from a stale `BIKE_*` committed mode), *Then* `BIKE_*` modes are masked (`0%` output, `committedScore = -Inf`), whereas wheel speed `> 0.5 m/s` or `effectiveSpeed >= 5.5 m/s` lifts the mask for brick transitions.
• *Given* an easy-pace front-hip running belt workout (`1.8–2.5 m/s`, `2.15–2.30 Hz` or dropped steps, `impactRms >= 1.8 m/s²`, `swingSd 10–15°`, `yaw 0.38–0.65`, `baro < 0.45`, `glance == 0`), *When* `classify()` runs, *Then* `easyRunSuppressWalk` bypasses both `walkingStepBand` and `walkingSpeedBand` and output is `RUN_WAIST_HIP (5)`, while `< 1.55 m/s` / low-impact obstacle stretches still enter `WALKING_TRANSITION (1)` and rear-clip, bicep armband (`17°`), and handheld/wrist-glance profiles retain positive winning margins.
• *Given* transient 2–3 s canopy speed dips or 4–5 s non-glance IMU bursts during steady running, *When* `classify()` runs, *Then* `SCORE_MARGIN = 0.85` and asymmetric `4 s`/`6 s` dwell hold `RUN_WAIST_HIP (5)` with zero spurious flips, while wrist glance switches in `0 ms` and `WALK -> RUN` / `RUN -> STATIONARY` switch in `2 s`.

**Invariants**: `PhoneCarriageMode` codes (`-1..7`) & `CarriageFilterProfile` untouched; `SpeedAndLocationDevice_ATTFused.kt` `0` edits (`1,459 / 1,500` LoC); `PhoneCarriageClassifier.kt <= 1,500` LoC (`0` protected monolith edits); time-based windows only; `0` DB schema bumps; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

### REQ-FIL-036: ATT Fused Cold-Start Pinning Release, Unanchored Heading/Velocity Decoupling & Post-Unpin ERTS Velocity Overshoot Gating [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-036`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/AttFusedColdStartHelper.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt`, `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmoother.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt`
- **Rationale**: Pixel 10 field run `Kurze Runde var 2 #53` (`2026-11-19_184953`, `_id = 2414`, `~8:15/km`) exposed (1) a `23 s` cold-start freeze in `att_fused` (`t = 1..23 s`, `0.0 m/s` / `0.0 m`) despite `30–38 m` of `1.0–2.3 m/s` running at `9.2–18.9 m` GPS accuracy caused by unconditional `distFromAnchor > 0.6 * acc` pinning, unanchored `PI/2` North heading crushing `ekf.speedMps`, and unbounded `(acc * 0.25)^2` Doppler variance, followed by `9.34 m` (`t = 24 s`) and `12.20 m` (`t = 31 s`) jumps; (2) post-workout ERTS synthesizing an artificial `5.73 m/s` (`20.6 km/h`) catch-up spike at `t = 30–34 s` overwriting `ExtremumValues.MAX(SPEED_mps)`; and (3) a `119–177 m` canonical distance shortfall (`ATT-9158`, refining `REQ-FIL-023.1`, `REQ-FIL-017.1`/`.3`, `REQ-FIL-025`, `REQ-FIL-027.3`, `REQ-FIL-032.3`, `REQ-TRK-048.2`, and `REQ-TRK-077`).
- **Description**:
  1. *Corroborated Cold-Start Pinning Release, Unbypassable Veto Precedence & Anti-Oscillation Guard (`REQ-FIL-036.1`)*:
     - `SpeedAndLocationDevice.java` SHALL expose `protected boolean shouldReleaseColdStartStationaryPin(Location location)` (default `false` so `GPS`/`GoogleFused` remain identical), evaluated in `onNewLocation()` strictly AFTER `if (mIsIndoorMode)` and strictly BEFORE `if (shouldUnpin && (extStationary || accelAtRest)) { shouldUnpin = false; }` so indoor, wheel-zero, and IMU rest-variance vetoes remain structurally unbypassable.
     - Pure helper `AttFusedColdStartHelper` (`banalservice.odometry`, keeping `SpeedAndLocationDevice_ATTFused.kt <= 1,485 / 1,500` LoC) SHALL release cold-start pinning under coarse GNSS accuracy (`10–20 m`) ONLY when BOTH `>= 2` consecutive fresh corroborated motion fixes (`hasTrustedGpsSpeed && gpsSpeed >= 0.8 m/s`, OR `isWheelMoving`, OR active running/walking carriage/step evidence) with `!isStationaryByClassifier` AND net directed non-oscillating raw-GNSS progression (`netDist >= 3.0 m` and straightness `netDist / pathDist >= 0.55` across `>= 2` moving intervals) hold, rejecting indoor handheld oscillating multipath while releasing outdoor cold-start running within `<= 5 s` without `10+ m` unpin jumps.
  2. *Raw-Chord Cold-Start Heading Seeding, Unanchored/Post-Outage Velocity Decoupling & Bounded Doppler Variance (`REQ-FIL-036.2`)*:
     - While EKF heading is unanchored, `AttFusedColdStartHelper` SHALL seed `UnicycleOdometryEkf` heading via `updateOfflineHeading()` once directed raw-GNSS progression reaches `>= 4.0 m` (or corroborated moving GPS bearing at `gpsSpeed >= 1.0 m/s`, `acc <= 20.0 m`) even below `MIN_DOPPLER_SPEED_MPS (3.0 m/s)`.
     - `UnicycleOdometryEkf.updateGnssPosition()` SHALL support `@JvmOverloads decoupleVelocity: Boolean = false` and zero `k[2][0..1]` + decouple `P[0..1, 2]` whenever `decoupleVelocity == true` OR `confirmedPostOutage` inflates `p[0][0] = max(p[0][0], y0^2); p[1][1] = max(p[1][1], y1^2)`, preventing unanchored velocity crushing and post-outage velocity spikes while preserving `TST-FIL-023.1` & `REQ-FIL-009`.
     - For trusted GNSS fixes (`acc > 0.0 && acc <= 20.0 m`), Doppler `speedVar` in `SpeedAndLocationDevice_ATTFused` and `ExtendedRtsOdometrySmoother` SHALL use calibrated bounded variance `((acc * 0.08)^2).coerceIn(0.25, 2.25)`, preserving distrust when `acc > 20.0 m`.
  3. *Post-Workout ERTS Speed-Before-Position Ordering, `dt_gnss`-Normalized Teleport Decoupling, 1-Tick Glitch Pre-Gate & Backward Catch-Up Bound (`REQ-FIL-036.3`)*:
     - `ExtendedRtsOdometrySmoother.smoothSamples()` SHALL apply moving speed updates (`validWheelSpeed` / `validSpeed`) BEFORE `ekf.updateGnssPosition()` and pre-gate isolated 1-tick non-wheel geometric speed glitches (`> max(2.0 * localMedianSpeed, localMedianSpeed + 2.5)`) without damping genuine multi-second sprint accelerations (`2.0 -> 3.5 -> 5.0 -> 6.0 m/s`).
     - `ExtendedRtsOdometrySmoother` SHALL detect GNSS step discontinuities normalized by actual valid-GNSS elapsed time `dt_gnss` (`dt_gnss in (0.0, 5.0]` and `stepDist / dt_gnss > max(6.0, localSpeed * 2.2 + 2.5)`, avoiding false triggers on `45 s` curved tunnels), decouple `[0..1, 2]` position-velocity covariance in BOTH forward `updateGnssPosition(..., decoupleVelocity = true)` and backward `smoothSnapshots()` across discontinuity epochs, and bound backward RTS velocity `smoothedStates[k][2]` against position-driven catch-up overshoot so canonical `SPEED_mps` and `ExtremumValues.MAX(SPEED_mps)` remain free of synthetic `5.73 m/s` spikes while preserving genuine sprints and accurate `DISTANCE_m`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-036` refining `REQ-FIL-023.1`, `REQ-FIL-017.1`/`.3`, `REQ-FIL-025`, `REQ-FIL-027.3`, `REQ-FIL-032.3`, `REQ-TRK-048.2`, and `REQ-TRK-077` across `AttFusedColdStartHelper.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`, `UnicycleOdometryEkf.kt`, `ExtendedRtsOdometrySmoother.kt`, and `ErtsPostWorkoutRecomputer.kt`.
2. *Historical Origin & Commit Trace*: `eba66320` & `f6715226` (`ATT-8576`), `ed6acfee` (`ATT-7750`), `d96fd52f` (`ATT-8705`), `14935efb` (`ATT-8880`), `c2f48ca1` (`ATT-9064`), `0210b827` (`ATT-9062`).
3. *Root Reason for Existing Formulation*: `ATT-8576` preserved `location.accuracy >= acc` and gated cold stationary jumps at `0.0 m/s`, while `SpeedAndLocationDevice` relied on `distFromAnchor > 0.6 * acc` for indoor spiderweb suppression; neither addressed cold-start outdoor running with `10–20 m` accuracy and wrong initial `PI/2` heading, nor backward RTS `C_k[2, 0..1]` catch-up velocity overshoot across post-unpin teleports.
4. *Preservation of Core Invariants*: `SpeedAndLocationDevice_ATTFused.kt <= 1,485 / 1,500` LoC (`0` protected monolith edits); `SpeedAndLocationDevice.java` hook default `false` (`GPS`/`GoogleFused` unchanged); `REQ-TRK-077` indoor/wheel/rest vetoes unbypassable; `REQ-FIL-023.1` cold stationary gating & `location.accuracy >= acc` preserved; `MIN_DOPPLER_SPEED_MPS = 3.0` unchanged; `REQ-FIL-017.3` raw column immutability & `REQ-FIL-032` indoor pinning preserved; `0` DB schema bumps; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an outdoor running start (`1.0–2.3 m/s`, heading South/East/West) with cold-start GPS accuracy (`9.2–18.9 m`) and active running IMU motion (`!mIsIndoorMode`, `!extStationary`, `!accelAtRest`), *When* `SpeedAndLocationDevice_ATTFused` processes the first `30 s`, *Then* pinning releases in `<= 5 s`, heading seeds from directed raw-GNSS progression without velocity crushing, and no `>= 10 m` teleport jump occurs.
• *Given* indoor mode, stationary external wheel (`0.0 m/s`), `accelAtRest == true`, cold stationary `25.1 -> 16.0 m` jump at `0.0 m/s`, OR indoor handheld oscillating multipath (`acc = 12–18 m`, fake `1.2–1.8 m/s` bouncing without directed progress), *When* `SpeedAndLocationDevice_ATTFused` processes the fixes, *Then* stationary pinning stays engaged (`0.0 m/s`, `0.0 m` distance creep).
• *Given* a recorded track with `23 s` pinned standstill followed by `9.34 m` (`t = 24 s`) and `12.20 m` (`t = 31 s`) teleports (normal live speeds `< 3.3 m/s`, with or without a 1-tick geometric glitch), *When* `ExtendedRtsOdometrySmoother` / `ErtsPostWorkoutRecomputer` runs, *Then* canonical `SPEED_mps` and `ExtremumValues.MAX(SPEED_mps)` stay `< 3.5 m/s` (no `5.73 m/s` spike) and raw device columns stay immutable.
• *Given* a genuine multi-second sprint (`2.0 -> 3.5 -> 5.0 -> 6.0 m/s`), a `45 s` curved tunnel outage, and `acc > 20 m` Doppler dropouts, *When* `ExtendedRtsOdometrySmoother` / `UnicycleOdometryEkf` runs, *Then* the sprint peaks accurately (`>= 5.5 m/s`), tunnel continuity holds without false discontinuity triggers, and `acc > 20 m` fixes remain distrusted.

**Invariants**: `SpeedAndLocationDevice_ATTFused.kt <= 1,485 / 1,500` LoC (`0` protected monolith edits); `SpeedAndLocationDevice.java` hook default `false`; `REQ-TRK-077` indoor/wheel/rest vetoes unbypassable; `REQ-FIL-023.1` & `REQ-FIL-009` preserved; `MIN_DOPPLER_SPEED_MPS = 3.0` unchanged; `REQ-FIL-017.3` & `REQ-FIL-032` preserved; `0` DB schema bumps; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

### REQ-FIL-037: Live ATT Fused Altitude/Slope EKF DEM Grid-Step Jump Gating, Coherent DEM/GPS Variance Retuning & Joseph SPD Slope Decoupling [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-037`
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-008`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`, `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`
- **Rationale**: Pixel 10 run `Kurze Runde var 2 #53` (`2026-11-19_184953`, `_id = 2414`) exposed live `att_fused_altitude_slope` reporting `167.1 m` ascent / `166.0 m` descent (vs `~58–64 m` true gain) and `-35%..+34%` slope swings (`253` samples `|Δalt/dt| > 0.5 m/s`) because live `KinematicAltitudeSlopeEkf` used over-confident `DEFAULT_VAR_DEM = 4.0 m²` (`±2 m`), coupled DEM jumps directly into `slope` (`k1Eff`) even with active barometer, and lacked baro-vs-DEM step-discrepancy gating and Huber variance inflation (`ATT-9156`, live counterpart to offline `ATT-9063` / `REQ-FIL-033`, refining `REQ-FIL-016.1`/`.2`, `REQ-FIL-020.1`/`.2`, `REQ-FIL-022`, `REQ-FIL-023.2`/`.3`, and `REQ-FIL-026`).
- **Description**:
  1. *Coherent DEM/GPS Variance Retuning, Baro/Standstill Slope Decoupling & 3×3 Joseph SPD Update (`REQ-FIL-037.1`)*:
     - `KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM` SHALL be retuned to `25.0 m²` (`±5 m` 1σ SRTM/DEM uncertainty) and `DEFAULT_VAR_GPS` SHALL be retuned coherently to `64.0 m²` (`±8 m` 1σ vertical GPS uncertainty, strictly `> DEFAULT_VAR_DEM` preserving `REQ-FIL-020.1` DEM > GPS hierarchy and `testThreeStateGpsBeforeDem` `< 1.0 m` convergence).
     - In `KinematicAltitudeSlopeEkf.updateReferenceAltitude()`, `k1Eff` SHALL be decoupled to `0.0` whenever `hasBarometerSample || isStandstill` (preserving `k1Eff = (p10 / s) * gainScale` for barometer-free DEM/GPS-only slope tracking), and 3-state reference covariance updates SHALL use the exact symmetric positive-definite 3×3 Joseph form `P^+ = (I - KH)P(I - KH)^T + K R K^T` (`H = [1, 0, 0]`, `R = effectiveVarRef`).
  2. *Post-Anchor Baro-vs-Reference Step-Discrepancy Jump Gating, Reference Tracker Advancement, Quadratic Huber Variance Inflation & High-Precision Anchor Guard (`REQ-FIL-037.2`)*:
     - `KinematicAltitudeSlopeEkf.updateReferenceAltitude()` SHALL expose `@JvmOverloads enableStepGating: Boolean = (varRef > 1.0)` so high-precision known-location or manual calibration anchors (`varRef <= 1.0 m²`) bypass DEM step gating completely.
     - Strictly for post-anchor updates (`hasReferenceAnchor == true`, preserving `REQ-FIL-022` initial `+130.4 m` weather-offset anchoring and `resetAscentDescentAnchor()`), when `enableStepGating && hasBarometerSample`: (a) compute `stepDiscrepancy = abs((refAltM - lastRefAltM) - (lastBaroAltM - lastRefBaroAltM))` and inflate `stepVarRef` quadratically when `stepDiscrepancy > 3.0 m` so `10–21 m` 1–2 s DEM cell bounces produce negligible altitude pull and `0.0 m` false ascent across `2.0 m` hysteresis; (b) advance `lastRefAltM` and `lastRefBaroAltM` to `(refAltM, lastBaroAltM)` even when step inflation triggers so persistent offset corrections (`453 -> 438 m`) converge cleanly on ticks `2..10` while transient bounces (`440 -> 427 -> 440 m`) inflate on both edges; and (c) apply quadratic innovation-based Huber variance inflation `effectiveVarRef = stepVarRef * (normRes / huberK)^2` when `abs(y) / sqrt(p00 + stepVarRef) > huberK` (preserving `p00` on multi-tick outliers while keeping normal GPS->DEM recovery uninflated). `reset()` and `setMode()` SHALL clear all step-gating tracker state.
  3. *Live Loop Ascent/Descent Accuracy, Slope Plausibility & MSL Loop-Closure Anchoring (`REQ-FIL-037.3`)*:
     - `AltitudeAndSlopeDevice_ATTFused` SHALL feed retuned DEM (`25.0 m²`) and GPS (`64.0 m²`) reference updates into `KinematicAltitudeSlopeEkf` while preserving `REQ-FIL-020.1` DEM precedence, `REQ-FIL-020.2` `2.0 m` hysteresis, `REQ-FIL-022.3` re-anchor hysteresis reset, and `REQ-FIL-026` 1-Hz barometric bucketing, keeping live `ASCENT`/`DESCENT` within `55–75 m` (not `167 m`), live `SLOPE` free of `±30–35%` DEM bounce spikes, and loop closure anchored near true MSL altitude on synthetic Pixel 10 `Kurze Runde var 2 #53` replay.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-037` refining `REQ-FIL-016.1`/`.2`, `REQ-FIL-020.1`/`.2`, `REQ-FIL-022`, `REQ-FIL-023.2`/`.3`, and `REQ-FIL-026` across `KinematicAltitudeSlopeEkf.kt` and `AltitudeAndSlopeDevice_ATTFused.kt`.
2. *Historical Origin & Commit Trace*: `75c1497e` & `33e60295` (`ATT-6279`), `8b0eba41` (`ATT-8081`), `23317f58` & `e9766625` (`ATT-8575`), `eba66320` (`ATT-8576`), `60472a32` (`ATT-8881`), `b6a1eaea` (`ATT-9063`).
3. *Root Reason for Existing Formulation*: `ATT-6279` assumed `±2 m` smooth DEM (`DEFAULT_VAR_DEM = 4.0`) and coupled DEM innovations directly into `slope` without baro step-discrepancy gating or Joseph SPD updates; `ATT-9063` fixed offline ERTS (`ExtendedRtsAltitudeSlopeSmoother.kt`) without modifying live `KinematicAltitudeSlopeEkf`.
4. *Preservation of Core Invariants*: `DEFAULT_VAR_BARO = 0.25` & 2-state `p00 = varBaro` unchanged; `REQ-FIL-022` `+130.4 m` initial weather-offset snap & `resetAscentDescentAnchor()` preserved; `REQ-FIL-020.1`/`.2` DEM precedence & `2.0 m` hysteresis preserved; `REQ-FIL-023.2`/`.3` standstill slope clearing & low-speed damping preserved; `REQ-FIL-026` 1-Hz bucketing & adaptive jitter preserved; `0` protected monolith edits; `0` DB schema bumps; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a live 3-state barometer + DEM session on `AltitudeAndSlopeDevice_ATTFused` experiencing `13–21 m` 1–2 s DEM grid jumps (`440 -> 427 -> 440 m`) while barometer changes `< 0.5 m`, *When* `KinematicAltitudeSlopeEkf` processes the updates, *Then* step-discrepancy + Huber variance inflation and `k1 = 0.0` hold `|Δalt/dt| < 0.3 m/s`, flat `|SLOPE| < 3%`, `ASCENT`/`DESCENT = 0.0 m`, and symmetric SPD 3×3 covariance.
• *Given* coarse GPS anchor (`453 m`, `64.0 m²`) followed by persistent DEM (`438 m`, `25.0 m²`), gradual barometric drift, high-precision calibration (`varRef <= 1.0`), and barometer-free DEM-only climbing, *When* `KinematicAltitudeSlopeEkf` runs, *Then* GPS->DEM converges within `±1.0 m`, weather drift absorbs into `baroBias`, high-precision anchors apply unimpeded, and DEM-only climb estimates positive slope.
• *Given* a synthetic Pixel 10 `Kurze Runde var 2 #53` loop (`~60 m` true gain/loss with `13–21 m` DEM jumps and gradual baro drift) replayed through `AltitudeAndSlopeDevice_ATTFused`, *When* live metrics are evaluated, *Then* live `ASCENT`/`DESCENT` stay in `55–75 m` (not `167 m`), `|SLOPE|` remains plausible without `±30–35%` DEM artifacts, and loop closure stays anchored near true MSL.

**Invariants**: `DEFAULT_VAR_BARO = 0.25` & 2-state `p00 = varBaro` unchanged; `REQ-FIL-022` initial weather-offset anchoring preserved; `REQ-FIL-020` & `REQ-FIL-023` & `REQ-FIL-026` preserved; `@Synchronized` thread safety; `0` protected monolith edits; `0` DB schema bumps; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

### REQ-SEN-033: EditDeviceDialog Wheel Sensor Calibration UI Cleanup, Initial Tire-Size Preset & Embedded WheelCircumferenceHistoryChart [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SEN-033`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-010`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`, `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/WheelCircumferenceSection.kt`, `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModel.kt`, `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelCircumferenceHistoryChart.kt`, `app/src/main/res/values*/strings_devices.xml`, `app/src/main/res/values*/strings_equipment.xml`
- **Rationale**: Wheel circumference is automatically calibrated during rides via GNSS and persisted in `WheelCircumferenceHistory` (`ATT-131`, `ATT-7753`), yet `EditDeviceDialog.kt` still exposed a legacy manual known-distance calculator (`Icons.Default.Calculate` / `CorrectCalibrationDialog`), a hardcoded English baseline label, and no longitudinal circumference history chart (`ATT-8422`, refining `REQ-SEN-030`, `REQ-TRK-050`, `REQ-SEN-031`, `REQ-UI-149`, and `REQ-LOC-001`).
- **Description**:
  1. *Bike-Sensor Known-Distance Calculator Removal, Initial Tire-Size Preset & Auto-Calibration Hint (`REQ-SEN-033.1`)*:
     - For `BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, and wheel-capable `BIKE_POWER`, `EditDeviceDialog` SHALL remove the manual known-distance calculator button (`Icons.Default.Calculate`) and `CorrectCalibrationDialog` invocation while preserving `RunCalibrationFactorSelector` + `CorrectCalibrationDialog` for `RUN_SPEED` footpods.
     - The dialog SHALL preserve the tire-size preset dropdown (`700 x 28` etc.) and numeric mm input for initial sensor setup, display a localized GNSS auto-calibration hint (`devices_wheel_circumference_auto_hint`), and replace hardcoded `"Baseline: $baselineMm mm"` with localized `devices_wheel_baseline_format` across all 9 locales.
  2. *Per-Sensor Chronological History Query, Unlinked Fallback & Async Baseline Sync (`REQ-SEN-033.2`)*:
     - `EquipmentRepository` SHALL expose `getCircumferenceHistoryForDevice(deviceId)` (scoping each linked bike's records by assigned `WheelPosition` or all bike records when `UNSPECIFIED`, merged and sorted strictly by `timestampMs ASC`, returning `emptyList()` when unlinked) and `recordManualBaselineResetForDevice(deviceId, circumferenceMm, timestampMs)`.
     - `EditDeviceViewModel` SHALL perform `EquipmentRepository` SQLite reads/writes on an injectable `ioDispatcher` (`Dispatchers.IO`), expose `wheelCircumferenceHistory: StateFlow<List<WheelCircumferenceRecord>>`, and synchronize `Devices.db` (including edited draft circumference) + `EquipmentRepository` + `wheelCircumferenceHistory` on `resetTirePressureBaseline()`.
  3. *Embedded `WheelCircumferenceHistoryChart` with `UNSPECIFIED` Fallback, Sensor Empty State, Unified Baseline Reset & 9-Locale Parity (`REQ-SEN-033.3`)*:
     - `WheelCircumferenceHistoryChart` SHALL render `WheelPosition.UNSPECIFIED` records with its localized legend (`equipment_tpms_unspecified_legend`) strictly when `unspecifiedRecords.isNotEmpty()` (leaving `EditEquipmentDialog` Front/Rear charts visually unchanged) and accept an optional `emptyStateText` parameter so `EditDeviceDialog` displays sensor-specific `devices_wheel_history_empty`.
     - `EditDeviceDialog` SHALL embed `WheelCircumferenceHistoryChart` inside the wheel-circumference section with a single unified baseline reset button (removing the redundant standalone button and obsolete bike-only strings across all 9 locales: `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-SEN-033`, refining `REQ-SEN-030` (`ATT-7753`), `REQ-TRK-050` (`ATT-4880`), `REQ-SEN-031` (`ATT-8719`), and `REQ-UI-149`.
2. *Historical Origin & Commit Trace*: `e7cc0f39` (`ATT-4880`), `a5665f9a` (`ATT-7753`), `b08c779e` (`ATT-8719`), `f55c9fee` (`ATT-8206`).
3. *Root Reason for Existing Formulation*: Legacy known-distance calibration predated GNSS auto-calibration (`ATT-131`); `ATT-7753` mounted `WheelCircumferenceHistoryChart` only in `EditEquipmentDialog`.
4. *Preservation of Core Invariants*: Initial tire-size presets & manual mm entry preserved; `CorrectCalibrationDialog` preserved for `RUN_SPEED`; `EditEquipmentDialog` visual parity preserved; `EditDeviceDialog.kt` LoC strictly downward-ratcheted (`< 1,843 / 2,004`); `0` direct `*DbHelper` imports in UI/ViewModel (`REQ-PRO-050`); `0` SQLite schema bumps; 9-locale parity (`TranslationParityTest`); `0` new `src/main` reads (`REQ-PRO-049.1`); `0` new reflection-only UI tests.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a wheel-capable bike sensor in `EditDeviceDialog`, *When* rendered, *Then* zero calculator icon / `CorrectCalibrationDialog` trigger exists, tire-size preset dropdown & mm input remain interactive with localized auto-calibration hint & localized baseline label, and `RUN_SPEED` retains its calculator button.
• *Given* linked bike(s) with `FRONT`/`REAR`/`UNSPECIFIED` records (or `0` linked bikes), *When* `EquipmentRepository` & `EditDeviceViewModel` load history and invoke `resetTirePressureBaseline()`, *Then* records are scoped per linked bike/position and sorted by `timestampMs ASC` (empty list if unlinked), I/O runs on `ioDispatcher`, and baseline reset syncs both `Devices.db` and `WheelCircumferenceHistory`.
• *Given* populated (`FRONT`/`REAR`/`UNSPECIFIED`) and empty histories in Light & Dark themes, *When* verified via Compose tests and `Rule 8.5` PNG capture, *Then* exactly one baseline reset button renders, `UNSPECIFIED` renders with legend (no blank Canvas), empty state renders cleanly, and all 9 locales pass `TranslationParityTest`.

**Invariants**: `RunCalibrationFactorSelector` + `CorrectCalibrationDialog` preserved for `RUN_SPEED`; `EditEquipmentDialog` chart parity preserved; `EditDeviceDialog.kt < 1,843 / 2,004` LoC; `0` direct `*DbHelper` calls in UI/ViewModel; `0` DB schema bumps; 9-locale parity (`values` + `de/es/fr/it/ja/nl/pl/pt`); `0` new `src/main` reads; `0` new reflection-only UI tests.

### REQ-TRK-090: Actual UTC Lap Start Timestamp Persistence in `TrackerService`, `MultisportLapCoordinator` & `LapsDatabaseManager` [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-090`
- **System Requirement(s)**: `SYSREQ-TRK-003`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/MultisportLapCoordinator.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManager.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
- **Rationale**: Observed in `Laps.db` on Pixel 10 (`workoutID = 2414`), `TrackerService.saveLap()` delegates through the 5-argument `WorkoutRepository.saveLap()` / `LapsDatabaseManager.saveLap()` overload passing `timeStart = null`, causing SQLite `DATETIME DEFAULT CURRENT_TIMESTAMP` to record the lap's **end/insertion** time instead of its start time (`ATT-9159`, Parent Epic `ATT-826`, extending `REQ-TRK-002` and `REQ-FIL-031.3`).
- **Description**:
  1. *Pause-Neutral Wall-Clock Start & Pre-Mutation Anchor Capture (`REQ-TRK-090.1`)*:
     - `MultisportLapCoordinator.onLapSummaryIntent()` SHALL capture `previousLapStartMs = currentLapStartAnchor.timestampMs` **before** `if (!isPause)` overwrites `currentLapStartAnchor`, and for non-pause laps where `closingStartUtc == null` and `previousLapStartMs > 0L`, SHALL format `previousLapStartMs` as canonical UTC `"yyyy-MM-dd HH:mm:ss"` via `formatUtcTimestamp()` and pass it explicitly to `WorkoutRepository.saveLap(...)`.
     - `MultisportLapCoordinator.saveFinalLap()` SHALL likewise format `currentLapStartAnchor.timestampMs` via `formatUtcTimestamp()` whenever `activeLegStartTimeUtc == null` and `currentLapStartAnchor.timestampMs > 0L`.
     - Pause/resume transitions (`IS_PAUSE == true`, `pauseTracking()`, `resumeFromPaused()`) MUST NOT mutate `currentLapStartAnchor` or close/create laps (`REQ-TRK-002` parity). In-process resume preserves the exact in-memory `currentLapStartAnchor`; post-process-death recovery (`TrackerService.applyResumedState`, `TrackerService.java:1906`) anchors `currentLapStartAnchor` at `Math.max(0L, System.currentTimeMillis() - state.timeLap_s * 1000L)`.
  2. *`TrackerService.saveLap` & `LapsDatabaseManager.saveLap` UTC Fallback + API/LoC Compatibility (`REQ-TRK-090.2`)*:
     - `TrackerService.saveLap` MUST preserve its 4-argument and 5-argument method signatures (preserving test subclasses/callers) and keep `TrackerService.java <= 3,559` LoC (`3,538` baseline), resolving UTC `timeStart` via `MultisportLapCoordinator.formatUtcTimestamp(anchorMs)` when `anchorMs > 0L` (or `System.currentTimeMillis() - lapTime * 1000L` UTC fallback when `lapTime > 0`) and forwarding through `WorkoutRepository.saveLap(workoutId, lapNr, timeStart, lapTime, lapDistance, averageSpeed)`.
     - `LapsDatabaseManager.saveLap` MUST preserve its 5-arg, 6-arg, and 8-arg signatures; when invoked with `timeStart == null` and `lapTime > 0` by any legacy caller, it SHALL compute `System.currentTimeMillis() - (lapTime * 1000L)` formatted strictly in **UTC** `"yyyy-MM-dd HH:mm:ss"` (never local timezone), ensuring `Laps.TIME_START` never silently records SQLite `CURRENT_TIMESTAMP` completion time while preserving `REQ-TRK-002` zero-duration/zero-distance rejection (`lapTime <= 0 && lapDistance <= 0.0`) and `NaN`/infinite speed sanitization.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-TRK-090`, extending `REQ-TRK-002` (`docs/requirements/req_tracking_cockpit_part1.md:281-307`) and `REQ-FIL-031.3` (`MultisportLapCoordinator.kt`); targets `MultisportLapCoordinator.kt`, `TrackerService.java`, `LapsDatabaseManager.java`, `WorkoutRepository.kt`.
2. *Historical Origin & Commit Trace*: `4fca7376` / `e7e02b0e` (`ATT-896`, `REQ-TRK-002` phantom-lap rejection & pause/resume neutrality); `5fb97345` (`ATT-8147`, `SYSREQ-TRK-003` linkage).
3. *Root Reason for Existing Formulation*: `REQ-TRK-002` preserved the 5-arg `saveLap` call chain and relied on SQLite `DATETIME DEFAULT CURRENT_TIMESTAMP` (`LapsDatabaseManager.java:558`), which inserts UTC write time (lap end) rather than lap start time for single-sport and final laps.
4. *Preservation of Core Invariants*: Preserves `REQ-TRK-002` zero-phantom rejection (`lapTime <= 0 && lapDistance <= 0.0`), pause/resume neutrality, 4-/5-arg `TrackerService.saveLap` and 5-arg `LapsDatabaseManager.saveLap` API compatibility, `TrackerService.java <= 3,559` LoC, `WorkoutRepository.kt <= 5,117` LoC, and canonical UTC `'yyyy-MM-dd HH:mm:ss'` formatting matching `FitFileWriter.parseUtcTimestamp()`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a tracked workout starting at `T0`, splitting Lap 1 at `T1` (`LAP_SUMMARY` `isPause=false`), and ending Lap 2 at `T2`,
• *When* `MultisportLapCoordinator` / `TrackerService.saveLap()` persists each lap (under any device timezone, e.g. `Europe/Berlin`),
• *Then* `Laps.TIME_START` stores `T0` for Lap 1 and `T1` for Lap 2 in UTC `'yyyy-MM-dd HH:mm:ss'` (captured pre-mutation before `currentLapStartAnchor` updates to `T1`), and `IS_PAUSE=true` does not overwrite `currentLapStartAnchor`.
• *Given* `LapsDatabaseManager.saveLap()` invoked with `timeStart == null` and `lapTime > 0` by any legacy caller,
• *When* persisted to `Laps.db`,
• *Then* `Laps.TIME_START` stores UTC `now - lapTime * 1000L` (`'yyyy-MM-dd HH:mm:ss'`) instead of SQLite `CURRENT_TIMESTAMP` end time, while `lapTime <= 0 && lapDistance <= 0.0` still rejects phantom laps.

**Invariants**: UTC `'yyyy-MM-dd HH:mm:ss'` matching `FitFileWriter.parseUtcTimestamp()` (never local TZ); `REQ-TRK-002` phantom-lap rejection & pause/resume neutrality; 4-/5-arg `TrackerService.saveLap` & 5-arg `LapsDatabaseManager.saveLap` signatures preserved; `TrackerService.java <= 3,559` LoC; `WorkoutRepository.kt <= 5,117` LoC; `Laps.db` `DB_VERSION = 3` unchanged; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.
### REQ-FIL-038: Sensor Disconnect Filter Reset, Accumulator Preservation & Sparkline Flat-Line Termination (`ATT-8994`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-FIL-038`
- **System Requirement(s)**: Derived (SW-only) — Internal sensor deactivation, `ProxySensor` unbinding, and `FilterManager` disconnect teardown lifecycle fix (refining `REQ-TRK-055` and `REQ-FIL-013`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MySensor.java`, `MyAccumulatorSensor.java`, `ProxySensor.java`, `FilterManager.kt`, `ANTHeartRateDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`
- **Rationale**: Disconnected device-pinned sensors (`Magene HR ANT+`, `Polar H10 ANT+`) left `InstantaneousFilter`/`MovingAverageFilter` tiles frozen on their last reading and fed horizontal flat-line samples into `LiveSparklineBuffer` because `MySensor.deactivateSensor()` did not notify listeners with `null`, HRM dynamic sensors were removed without deactivation, and `FilterManager.rebindExistingFilters()` ignored `currentSensor == null || !isActivated` (`ATT-8994`).
- **Description**:
  1. *Lock-Free Deactivation, Silent Accumulator Override & `ProxySensor` Safety (`REQ-FIL-038.1`)*: `MySensor.deactivateSensor()` SHALL set `mActivated = false`, clear `mValue = null`, and notify `mSensorListeners` (`CopyOnWriteArrayList`, lock-free) with `newValue(null)`. `MyAccumulatorSensor` and `ProxySensor` SHALL override `deactivateSensor()` to set `mActivated = false` silently without notifying `null`. `ProxySensor` SHALL track `mAccumulatorBacked` (`isAccumulatorBacked()`) whenever a `MyAccumulatorSensor` binds, and `ProxySensor.setSourceSensor(null)` SHALL notify `listenersSnapshot` with `newValue(null)` strictly outside `mSourceLock` (`REQ-SEN-021.2`) when `previousSource != null && !(previousSource instanceof MyAccumulatorSensor)`.
  2. *Dynamic Sensor Deactivation Before Removal (`REQ-FIL-038.2`)*: `ANTHeartRateDevice`, `BTLEHeartRateDevice`, and `PolarOnlineStreamingBridge` SHALL invoke `sensor.deactivateSensor()` prior to `unregisterSensor()`/`removeSensor()` for all dynamic sensors (`mRrIntervalSensor`, `mCadenceSensor`, `mStridesSensor`, `mVerticalOscillationSensor`, `mGroundContactTimeSensor`) across `unregisterSensors()`, `unregisterImuCadenceSensors()`, and `shutDown()`.
  3. *`FilterManager` Disconnect Clearing, Accumulator/Cumulative Immunity & `createFilter()` Parity (`REQ-FIL-038.3`)*: `FilterManager` SHALL track accumulator-backed filters explicitly via `mAccumulatorFilters` (populated when binding to `MyAccumulatorSensor` or accumulator-backed `ProxySensor`, never querying `MySensorManager.mAccumulatedSensorTypeSet`). In `rebindExistingFilters()` and `createFilter()`, unwrap `ProxySensor`: for device-pinned filters (`deviceName != null`) where `currentSensor == null || !currentSensor.isActivated`, unbind (`rebindFilter(filter, boundSensor, null)`) and clear non-accumulator live filters (`InstantaneousFilter` via `newValue(null)`; `MovingAverageFilter` via `reset()`) while preserving `AverageFilter`, `MaxValueFilter`, and `mAccumulatorFilters`, and defer new device-pinned inactive filters via `mRequiredButNotAvailableFilters`; for `"Bester Sensor"` (`deviceName == null`), keep bound to retained `ProxySensor` (`REQ-TRK-055.1`) and clear live non-accumulator filters only when `effectiveSource == null || !effectiveSource.isActivated`.
  4. *Sparkline Flat-Line Termination & Invariants (`REQ-FIL-038.4`)*: Clearing live filters to `null` (`"--"`) stops `BANALServiceRepository.updateSparklineBuffers()` from feeding frozen values into `LiveSparklineBuffer` with `0` edits to `BANALServiceRepository.kt`, `SensorFieldView.kt`, `DeviceManager.java`, `TrackerService.java`, and `DevicesDatabaseManager.java`; `Localization Parity: N/A - no new UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**: Refines `REQ-TRK-055` (`ATT-6257`), `REQ-FIL-011` (`ATT-275`), `REQ-FIL-013` (`ATT-3222`), `REQ-SEN-021` (`ATT-4096`), `ATT-7530` (`c54768d2`); preserves lock-free callbacks, retained `ProxySensor` fallback, cumulative `AverageFilter`/`MaxValueFilter` & `MyAccumulatorSensor` session totals, `ExponentialSmoothingFilter` signature parity, and `0` protected monolith edits.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* active non-accumulator & accumulator sensors, *When* `deactivateSensor()` or `ProxySensor.setSourceSensor(null)` runs, *Then* non-accumulator listeners receive `null` lock-free while `MyAccumulatorSensor` & `ProxySensor.deactivateSensor()` stay silent.
  • *Given* `ANTHeartRateDevice`/`BTLEHeartRateDevice`/`PolarOnlineStreamingBridge` dynamic sensors, *When* disconnecting/shutting down, *Then* `deactivateSensor()` runs before `unregisterSensor()`/`removeSensor()`, clearing `RR_INTERVAL`/IMU live tiles while keeping `STRIDES`.
  • *Given* device-pinned & `"Bester Sensor"` `InstantaneousFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`, `AverageFilter`, `MaxValueFilter`, and `STRIDES` filters, *When* sensors disconnect & repeated `SENSORS_CHANGED` fire, *Then* non-accumulator live filters clear to `null` (`"--"`) and sparkline feeding stops, cumulative/accumulator totals stay intact, `"Bester Sensor"` preserves fallback & `ProxySensor` binding, and reconnect rebinds cleanly.
### REQ-SEN-034: iTPMS Persisted Baseline Wiring, Live Reset Propagation & Strict Null GNSS Accuracy/Bearing Gating [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SEN-034`
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`, `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt`
- **Rationale**: Follow-up defect from Ceremony 2 review of `ATT-4880`: `BANALServiceRepository` instantiated per-device `IndirectTpmsEstimator` instances with default `baselineCircumferenceMeters = 0.0` (never calling `DevicesDatabaseManager.getBaselineCircumference(devId)`), defaulted null GNSS accuracy/bearing to `5.0`/`0.0` (bypassing `<= 6.0 m` and `< 3.0 deg/s` gating), and gated GPS-free `DualWheelTpmsEstimator` inside `location != null` (`ATT-8421`, hardening `REQ-TRK-050`, `REQ-SEN-030.2`, and verifying `REQ-SEN-033.3`).
- **Description**:
  1. *Persisted `baselineCircumference` Loading, Live Propagation & Zero-Baseline Overwrite (`REQ-SEN-034.1`)*:
     - `BANALServiceRepository` SHALL load per-device `DevicesDatabaseManager.getBaselineCircumference(devId)` off the main thread (`Dispatchers.IO`, via an injectable baseline loader for JVM testing) into a `ConcurrentHashMap<Long, Double>` cache and apply the resolved baseline to both newly created and live cached `perDeviceItpmsEstimators[devId]` instances so `IndirectTpmsEstimator.TpmsAlertResult.LowPressure` fires when `C_start` drops `>= 0.6%` below a persisted baseline `> 0.0`.
     - Async `Dispatchers.IO` baseline loads, `DeviceDataRepository.baselineCircumferenceChanged` events, `BANALService.CALIBRATION_FACTOR_CHANGED` broadcasts (registered strictly via `registerInternalReceiver()` for JVM `SDK_INT == 0` safety), `clearBreadcrumbs()`, and `TrackingMode.READY -> TRACKING` reloads MUST push the resolved baseline onto live cached `perDeviceItpmsEstimators[devId]` instances, explicitly overwriting `baselineCircumferenceMeters = 0.0` when the persisted baseline is `<= 0.0` or non-finite.
     - `DeviceDataRepository` SHALL expose a buffered `SharedFlow<Long>` (`baselineCircumferenceChanged`, `extraBufferCapacity = 64`, `BufferOverflow.DROP_OLDEST`) emitted on both branches of `resetTirePressureBaseline()` (`circumferenceMm > 0` and `null`) and when `updateDevice()` modifies `baselineCircumference`.
  2. *Strict Null/Non-Finite GNSS Accuracy & Bearing Gating with GPS-Free Dual-Wheel Isolation (`REQ-SEN-034.2`)*:
     - `BANALServiceRepository` SHALL remove the synthetic fallback defaults `?: 5.0` (for `SensorType.ACCURACY`) and `?: 0.0` (for `_currentBearing.value`), skipping Section 1 (GNSS `IndirectTpmsEstimator`) whenever `location == null`, `acc == null || !acc.isFinite() || acc < 0.0`, or `bearing == null || !bearing.isFinite()`.
     - `BANALServiceRepository` SHALL scope `location != null` and GNSS `acc`/`bearing` guards strictly to Section 1 so Section 2 (GPS-free `DualWheelTpmsEstimator`, `REQ-SEN-030.1`) continues running under `TrackingMode.TRACKING` whenever `wheelDevices.size >= 2` even when GNSS `location`, `accuracy`, or `bearing` is `null` or non-finite.
  3. *Baseline Label 9-Locale Parity Regression & Architectural Guardrails (`REQ-SEN-034.3`)*:
     - `Localization Parity: N/A - already localized by ATT-8422 (00549967) via R.string.devices_wheel_baseline_format across all 9 active locales (values, de, es, fr, it, ja, nl, pl, pt)` with `0` remaining hardcoded English `"Baseline"` UI labels (regression guarded by `TranslationParityTest` & `EditDeviceWheelCircumferenceTest`).
     - `BANALServiceRepository.kt` (`<= 1,500` LoC) and `DeviceDataRepository.kt` (`<= 1,500` LoC) MUST respect file-size caps with `0` protected monolith edits (`DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, `DeviceManager.java`, `TrackerService.java`), `0` SQLite schema bumps, and `0` `src/main` file reads in new tests (`REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-SEN-034`, hardening runtime wiring for `REQ-TRK-050` (`ATT-4880`), `REQ-SEN-030.2` (`ATT-7753`), and verifying `REQ-SEN-033.3` (`ATT-8422`).
2. *Historical Origin & Commit Trace*: `e7cc0f39` (`ATT-4880`), `a5665f9a` (`ATT-7753`), `00549967` (`ATT-8422`).
3. *Root Reason for Existing Formulation*: `ATT-4880` unit-tested `IndirectTpmsEstimator` directly while `BANALServiceRepository` kept default `IndirectTpmsEstimator()` and `?: 5.0` / `?: 0.0` fallbacks inside its 1-Hz loop; `ATT-7753` nested Section 2 inside `location != null`.
4. *Preservation of Core Invariants*: `IndirectTpmsEstimator` math/thresholds unchanged; `DualWheelTpmsEstimator` GPS-free behavior preserved; `persistConvergedCircumferences()`, `clearBreadcrumbs()`, `Devices.db` v8 schema, and `CALIBRATION_FACTOR` / wheel accumulators unchanged; `0` monolith edits; `0` `src/main` reads in new tests.

**Acceptance Criteria (Given-When-Then)**:
• *Given* persisted `baselineCircumference = 2.130 m` in `Devices.db`, *When* `BANALServiceRepository` creates/reloads the device's `IndirectTpmsEstimator` and `C_start` locks at `<= 2.115 m` (`-0.7%`), *Then* `TpmsAlertResult.LowPressure` fires; and *When* reset via `DeviceDataRepository` or cleared to `0.0` and reloaded, *Then* live cached estimators update immediately (including overwriting with `0.0`).
• *Given* `TrackingMode.TRACKING` with null/non-finite GNSS `accuracy`, `bearing`, or `location`, *When* `BANALServiceRepository` processes a tick, *Then* Section 1 (`IndirectTpmsEstimator`) skips the sample without fabricating `5.0`/`0.0`, while Section 2 (GPS-free `DualWheelTpmsEstimator`) continues detecting punctures uninterrupted.
• *Given* `ATT-8422` 9-locale `devices_wheel_baseline_format` parity and repository wiring changes, *When* targeted unit, localization, and contract suites run, *Then* all pass with `0` monolith growth and `0` new `src/main` reads.

**Invariants**: `IndirectTpmsEstimator` thresholds unchanged; GPS-free `DualWheelTpmsEstimator` active when GNSS is absent; `BANALServiceRepository.kt <= 1,500` LoC & `DeviceDataRepository.kt <= 1,500` LoC; `0` protected monolith edits; `0` SQLite schema bumps; `Localization Parity: N/A - already localized by ATT-8422`; `0` new `src/main` reads.

### REQ-CON-053: PolarOnlineStreamingBridge Atomic Stream Start Guards, PMD ERROR_ALREADY_IN_STATE Recovery & Blocked-Stream Warning (`ATT-9161`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-053`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarPmdStreamCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamWarningEffect.kt`, `TrackingTabsScreen.kt`, `DevicesTabbedScreen.kt`, `strings_devices.xml`
- **Rationale**: Overlapping Polar SDK handshake callbacks (`bleSdkFeatureReady`, `syncAlreadyOpenSessionIfReady`, `disInformationReceived`) re-entrantly cancel and restart unguarded `HR`, `ACC`, and `GYRO` coroutines (causing 7x `00002a37` CCCD toggle storms and overlapping PMD `REQUEST_MEASUREMENT_START` commands), while PMD `ERROR_ALREADY_IN_STATE` (from re-entrancy or background `fi.polar.polarflow`) silently kills `ACC`, `GYRO`, and `PPI` for the entire session with zero recovery or user feedback (`ATT-9161`).
- **Description**:
  1. *Unified 4-Stream Atomic Re-Entrancy Guard & Job-Identity Cleanup (`REQ-CON-053.1`)*: `PolarPmdStreamCoordinator` SHALL manage `HR`, `ACC`, `GYRO`, and `PPI` slots under a single lock with pre-launch atomic `compareAndSet(false, true)`, `CoroutineStart.LAZY` job publication, and `slot.job === myJob` identity-guarded `finally` cleanup. Duplicate start triggers while active/in-flight (including suspended `resolve*Setting` / `getAvailableOnlineStreamDataTypes`) SHALL be atomic no-ops; `ACC` SHALL claim its CAS slot BEFORE `tryAcquireAccChannel(..., ACC_OWNER_ONLINE)` and roll back cleanly if offline recording holds the channel.
  2. *Bounded PMD `ERROR_ALREADY_IN_STATE` Recovery (`REQ-CON-053.2`)*: Across `ACC`, `GYRO`, and `PPI`, classify `ERROR_ALREADY_IN_STATE` via typed `BleControlPointCommandError` (direct or `cause`) and message fallback; execute up to `3` bounded recovery attempts (`api.stopStreaming(sessionId, pmdType)` + injectable backoff `500 ms -> 1,000 ms -> 2,000 ms`) while connected and not shut down, resetting attempt count on first received frame and immediately rethrowing `CancellationException`.
  3. *Diagnostics, Session-Deduplicated Warning & 9-Locale Snackbar (`REQ-CON-053.3`)*: On retry exhaustion while connected, log structured diagnostics and emit ONE session-deduplicated `PolarPmdStreamWarning` onto `PolarPmdStreamCoordinator.streamWarning` (`StateFlow` with `consumeStreamWarning()` and reset on connect/disconnect/power-off/`shutDown()`), observed via shared `PolarPmdStreamWarningEffect(snackbarHostState)` in `TrackingTabsScreen.kt` & `DevicesTabbedScreen.kt` using 9-locale `polar_live_imu_stream_blocked_warning` in `strings_devices.xml`.
  4. *Monolith Ceilings & Regression Invariants (`REQ-CON-053.4`)*: Keep `PolarOnlineStreamingBridge.kt <= 1,306 / 1,500` LoC, `TrackingTabsScreen.kt <= 1,450 / 1,500` LoC, `0` edits to protected monoliths (`DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`), `0` upward `banalservice -> trainingtracker.ui` imports, and zero regression in `ATT-9176` (`REQ-HRV-051`) & `ATT-8994` (`REQ-FIL-038.2`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-SET-112.2`/`.3`, `REQ-CON-045.4`, `REQ-CON-050.2`/`.3`, `REQ-HRV-051.2`, preserving `REQ-FIL-038.2` in `PolarOnlineStreamingBridge.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-8016` (`f4bef44c`), `ATT-8714` (`1e7a2f0b`), `ATT-8717` (`9f9e5967`), `ATT-9176` (`32ca4857`), `ATT-8994` (`001dd39f`).
  3. *Root Reason for Existing Formulation*: Prior iterations cancelled `hrJob`/`accJob`/`gyroJob` unconditionally assuming single-shot feature callbacks; `ATT-9176` added atomic CAS only for `PPI`, leaving `HR`/`ACC`/`GYRO` unguarded and all PMD streams without `ERROR_ALREADY_IN_STATE` recovery.
  4. *Preservation of Core Invariants*: Preserves single-GATT ref-counting (`REQ-SET-118`), `ACC` mutex (`REQ-SET-112.3`), `activeSdkStreamId` pinning (`REQ-CON-050.3`), `BSportType.RUN` gating, optical PPI H10 exclusion & `PolarPpiStreamProcessor`, dynamic sensor `deactivateSensor()`, and `0` protected monolith edits.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* rapid overlapping SDK feature/DIS/session-sync callbacks on Polar H10 or Verity Sense, *When* streams start, *Then* at most one subscription runs per stream (`HR`, `ACC`, `GYRO`, `PPI`), `ACC` CAS rolls back if offline-locked, and cancelled jobs never clobber newer slots or `ACC_OWNER_ONLINE`.
  • *Given* `ACC`, `GYRO`, or `PPI` fails with `ERROR_ALREADY_IN_STATE`, *When* caught, *Then* `stopStreaming` + `500/1000/2000 ms` bounded retry (max `3`) recovers stream flow on success, or logs structured diagnostics and surfaces ONE session-deduplicated 9-locale Snackbar warning on exhaustion.

### REQ-CON-054: BLE HRM Silent-Connection Watchdog, Alias-Complete Unpair Teardown & Connected-GATT Rediscovery (`ATT-9155`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-CON-054`
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `BleHrmSilentConnectionWatchdog.kt`, `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamCoordinator.kt`, `MyBTLEDevice.java`, `BTLEHeartRateDevice.java`, `BTSearchForNewDevicesEngine.java`, `DeviceDataRepository.kt`, `DeviceManager.java`
- **Rationale**: Field captures on Pixel 10 with Polar Verity Sense showed (1) BLE HRMs staying permanently connected with `--` when `0x2A37` CCCD or SDK HR collection stalls, and (2) unpairing/deleting leaving raw `BluetoothGatt` or `PolarBleApi` alias handles open so the peripheral stops advertising and stays invisible to BLE scans until power-cycled (`ATT-9155`).
- **Description**:
  1. *Alias-Complete Unpair/Delete Teardown (`REQ-CON-054.1`)*: `PolarOfflineGattCoordinator` SHALL provide `forceDisconnectDevice(context, mac, polarId)` and `disconnectUnpairedSessions(context, pairedProvider)` disconnecting all MAC/8-hex aliases and clearing `connectedDeviceIds`/`activeAccOwners`/`activeSessions` without harming sibling paired Polar sensors (`REQ-SET-118.3`). `PolarOnlineStreamingBridge.shutDown()`, `BTLEHeartRateDevice.shutDown()`, `DeviceDataRepository.updateDevice()`/`deleteDevice()` (capturing MAC pre-delete), and `DeviceManager.pairingChanged()` (even when absent from `mMyRemoteDevices`) SHALL invoke it; `MyBTLEDevice.disconnectFromGatt()` SHALL close synchronously when on `mHandler`'s looper and `onConnectionStateChange()` SHALL close late `STATE_CONNECTED` callbacks strictly when `mIsShutDown == true`.
  2. *Connected-GATT Discovery Fallback (`REQ-CON-054.2`)*: `BTSearchForNewDevicesEngine.startAsyncSearch()` SHALL release unpaired Polar SDK sessions for `HRM`, probe `BluetoothManager.getConnectedDevices(BluetoothProfile.GATT)` (permission-guarded, skipping already-paired DB sensors), and enforce target GATT service-UUID validation + immediate close of non-matching probes strictly on `mConnectedGattProbes`.
  3. *Silent-HR Watchdog & CCCD Retry (`REQ-CON-054.3`)*: Shared pure-JVM `BleHrmSilentConnectionWatchdog` (`12 s` default, injectable) SHALL drive both `BTLEHeartRateDevice` (preserving `REQ-CON-049` DIS-before-HR-CCCD order, retrying failed `0x2A37` CCCD status once, deferring while `isGattPaused()`) and `PolarOnlineStreamingBridge` to re-enable `0x2A37` / re-subscribe `HR` once on `12 s` silence, then cleanly disconnect/close & reconnect if still silent, disarming immediately on any HR packet.
  4. *Monolith & Governance Invariants (`REQ-CON-054.4`)*: Keep `DeviceManager.java <= 1,708` (`+0` net LoC = `1,707`), `0` edits to `TrackerService.java`/`DevicesDatabaseManager.java`, all edited Kotlin/Java files `<= 1,500` LoC, `0` new `src/main` reads, and `Localization Parity: N/A - no new or modified UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-CON-012`, `REQ-CON-018`, `REQ-CON-050`, `REQ-CON-053`, `REQ-SET-118.3` across `MyBTLEDevice`, `BTLEHeartRateDevice`, `BTSearchForNewDevicesEngine`, `PolarOfflineGattCoordinator`, `PolarOnlineStreamingBridge`.
  2. *Historical Origin & Commit Trace*: `ATT-1348`, `ATT-2224`, `ATT-8690`, `ATT-8714` (`1e7a2f0b`), `ATT-8717` (`9f9e5967`), `ATT-9161` (`104a028f`).
  3. *Root Reason for Existing Formulation*: Prior teardown assumed single-ID `mMyRemoteDevices` ownership, scans assumed unpaired peripherals always advertise, and HR CCCD writes were fire-and-forget.
  4. *Preservation of Core Invariants*: Preserves `REQ-CON-049` DIS-before-CCCD order (`BTLEHeartRateDeviceTest:561`), `MyBTLEDeviceLifecycleTest:323` non-shutdown null-GATT callback, `REQ-CON-050.1` ordered teardown, `REQ-SET-118.3` sibling Polar session isolation, `REQ-CON-053` 4-stream coordination, and `DeviceManager.java <= 1,708` LoC.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a connected BLE/Polar HRM (optionally alongside a sibling paired Polar sensor), *When* unpaired or deleted, *Then* both raw `BluetoothGatt` and all MAC/8-hex `PolarBleApi` aliases tear down immediately without affecting the sibling sensor.
  • *Given* an unpaired/deleted compatible BLE HRM still GATT-connected to the phone, *When* BLE search starts, *Then* unpaired Polar sessions release and `getConnectedDevices(GATT)` probes surface the sensor while skipping already-paired and non-matching peripherals.
  • *Given* a connected BLE HRM receiving `0` HR packets for `12 s` (and not GATT-paused), *When* the watchdog fires, *Then* `0x2A37` CCCD / `HR` stream re-enables once and, if still silent, cleanly disconnects/closes and reconnects.

### REQ-TRK-091: Decoupled Stop-Workout Post-Processing, Short-Service Progress Notification & Edit-Safe Refresh (`ATT-9154`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-091`
- **System Requirement(s)**: `SYSREQ-TRK-005`, `SYSREQ-DEX-007`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutPostProcessingCoordinator.kt`, `WorkoutPostProcessingService.kt`, `WorkoutPostProcessingNotificationManager.kt`, `TrackerService.java`, `WorkoutRepository.kt`, `EditWorkoutViewModel.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `AndroidManifest.xml`, `strings.xml`
- **Rationale**: Connected armed Polar H10/Verity Sense offline backfill and HRV/ERTS recomputation synchronously block `TrackerService.endWorkout()` before `TRACKING_FINISHED_INTENT`, stalling Stop -> `EditWorkoutScreen` navigation (`ATT-9154`).
- **Description**:
  1. *Non-Blocking Stop & Armed-Token Isolation (`REQ-TRK-091.1`)*: `TrackerService.endWorkout()` SHALL commit `FINISHED = 1`, persist devices/RR/sensors/metadata, run `finalizeLiveSession()`, close `PolarRawTelemetryRecorder.stopActiveSession()`, capture armed flags/start timestamps/monotonic armed tokens synchronously, enqueue `WorkoutPostProcessingCoordinator`, and broadcast `TRACKING_FINISHED_INTENT` immediately (`< 1 s`). Async backfill SHALL run sequentially (`H10(showOwnNotification=false)` -> `Verity(showOwnNotification=false)` -> `HrvPostWorkoutRecomputer` -> optional `ErtsPostWorkoutRecomputer` -> `refreshWorkout` -> `ExportManager.workoutFinished` -> deferred `exportWorkout`) with per-stage fault isolation, clearing `isArmed` strictly when the armed token still matches the stopping workout.
  2. *Atomic Export Deferral Lock & Edit-Safe Refresh (`REQ-TRK-091.2`)*: `WorkoutPostProcessingCoordinator` SHALL coordinate `activeWorkouts` and `deferredExportWorkouts` under a single lock so early `WorkoutRepository.saveWorkout()` persists metadata + updates `_allWorkouts` (incl. `rpe`/`feel`) immediately while deferring `exportManager.exportWorkout(freshWorkoutFromDb)` until post-processing completes. `EditWorkoutViewModel` SHALL observe `repository.allWorkouts` filtered to `workoutId` and merge strictly telemetry/derived fields without overwriting user-editable fields or re-running sport/equipment suggestion rewrites.
  3. *`shortService` FGS, Progress Notification `8047`/`8048` & 9-Locale Parity (`REQ-TRK-091.3`)*: Declare `WorkoutPostProcessingService` with `android:foregroundServiceType="shortService"` (`0` new manifest permissions, `0` `connectedDevice`), API 34+ `onTimeout()` clean stop, and `ForegroundServiceStartNotAllowedException` in-process fallback. `WorkoutPostProcessingNotificationManager` (`ID 8047` progress, `ID 8048` warning, `NOTIFICATION_CHANNEL__EXPORT`, API 33+ `POST_NOTIFICATIONS` guard) SHALL update localized stage progress across all 9 locales (`0` emojis), cancel `8047` on completion, and cancel `8047` + post an auto-cancel warning on dedicated ID `8048` on failure so stopping the FGS never wipes the warning.
  4. *LoC & Governance Invariants (`REQ-TRK-091.4`)*: `TrackerService.java < 3,539 / 3,559`, `WorkoutRepository.kt <= 5,117`, `PolarVeritySenseOfflineController.kt <= 1,500`, `0` edits to `DeviceManager.java`/`DevicesDatabaseManager.java`, `0` schema migrations, `0` new `src/main` reads.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-SET-105.2`/`.5`, `REQ-SET-109.1`, preserving `REQ-TRK-022`, `REQ-TRK-030`, `REQ-TRK-033`, `REQ-SET-119`, `REQ-UI-211`.
  2. *Historical Origin & Commit Trace*: `ATT-3986`/`ATT-4099` (`aa0e9097`), `ATT-3567`, `ATT-7918` (`60ec5c3b`), `ATT-7924`, `ATT-8969` (`8e44039d`).
  3. *Root Reason for Existing Formulation*: Synchronous `endWorkout()` ordering ensured Polar backfill and HRV/ERTS completed before `ExportManager` export generation.
  4. *Preservation of Core Invariants*: Preserves isolated `FINISHED = 1` commit first, zero-gap Polar fast paths & `5 s` connect timeout / disconnected fast-fail, Verity `WorkoutRr.db` isolation, export-after-backfill+ERTS ordering, no `connectedDevice`, and hidden-field preservation.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* Polar H10/Verity offline recording is armed, *When* Stop is tapped, *Then* `TRACKING_FINISHED_INTENT` fires in `< 1 s`, async stages run with unified progress notification `8047`, back-to-back armed tokens are isolated, early user edits/saves never lose data or export stale telemetry, and `shortService` + 9-locale parity hold.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8717` / `ATT-8712`) | `ATT-8717`, `ATT-8712` | Added `REQ-CON-049` [Verified] and `REQ-TRK-087` [Verified] cleanly post-split (`ATT-8206`). |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8714`) | `ATT-8714` | Added `REQ-CON-050` [Verified] post-Bounce 1/3 heal (`ATT-8714` migrated onto `part2` post-`ATT-8206` shard split). |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Refined `REQ-TRK-062` [Planned] to suppress green connected `RadarStatusBadgePill` when clear (incl. snoozed clear) with `14.dp` top spacer guard & resource cleanup, and reconciled `REQ-TRK-064` [Planned]. |
| `1.0.5` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Promoted `REQ-TRK-062` & `REQ-TRK-064` to `[Implemented]` following Stage 4 construction (`RadarEdgeOverlay.kt`, `ic_device_available.xml` & 9-locale `radar_overlay_clear`/`radar_overlay_connecting` removal, `RadarEdgeOverlayTest.kt`). |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Promoted `REQ-TRK-062` & `REQ-TRK-064` to `[Verified]` in Stage 5 following 10-suite verification (`126/126`), `:app:lintDebug` (`0` errors), and `Rule 8.5` PNG audit. |
| `1.0.7` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Refined `REQ-CON-047` (`.2`/`.3`) & `REQ-CON-048` to `[Planned]` to extract single shared stateless `RadarLightModeGrid` + `resolveLightVisuals` under `ui/components/radar/` for both `RadarLightQuickSwitchSheetContent` and `EditDeviceDialog` (`showActiveModeSubtitle = !isOffline`, zero duplicated dot/segmented-row UI, `N/A - no UI strings`). |
| `1.0.8` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Promoted `REQ-CON-047` & `REQ-CON-048` to `[Implemented]` following Stage 4 extraction of `RadarLightModeGrid.kt`, refactoring of `RadarLightQuickSwitchSheet.kt` / `RadarLightButton.kt` / `EditDeviceDialog.kt` (`1843` LoC), `109/109` targeted tests across `14` suites, and `Rule 8.5` PNG verification. |
| `1.0.9` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Promoted `REQ-CON-047` & `REQ-CON-048` to `[Verified]` in Stage 5 following governance checks, `has_verified_test_evidence` verification, and `Rule 8.5` PNG audit. |
| `1.0.10` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Added `REQ-TRK-088` [Planned] for `MyHelper.getUnitsId`/`getShortUnitsId` `RADAR_APPROACH_SPEED_mps` unit mapping and `sensor_units.md`/`SensorType.java` synchronization (`N/A - no UI strings`). |
| `1.0.11` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Promoted `REQ-TRK-088` to `[Implemented]` following Stage 4 changes in `MyHelper.java`, `SensorType.java`, `docs/architecture/sensor_units.md`, and `MyHelperRadarUnitsTest.kt`. |
| `1.0.12` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Promoted `REQ-TRK-088` to `[Verified]` in Stage 5 following clean-room 10-suite verification (`55/56` PASS across 9/10 full-PASS suites; `1` pre-existing `ATT-8823` failure in `EditDeviceDialog.kt`) and `has_verified_test_evidence("TST-TRK-088")` PASS. |
| `1.0.13` | 2026-11-18 | AI Agent (`ATT-8881`) | `ATT-8881` | Added `REQ-FIL-026` `[Verified]` for 1-Hz barometric ingestion rate-matching and adaptive cyclic pocket-jitter `varBaro` scaling in `AltitudeAndSlopeDevice_ATTFused` and `KinematicAltitudeSlopeEkf` (`67/67` PASS across `8` XMLs). |
| `1.0.14` | 2026-11-18 | AI Agent (`ATT-8733`) | `ATT-8733` | Added `REQ-TRK-089` `[Verified]` (`RadarFullScreenThreatOverlay` non-clipping content-sized markers, touch-transparent `Box` + single-pass `.layout`, `TwoWheeler`/`DirectionsCar`/`LocalShipping`, `Color.kt` contrast & map-backdrop tokens, `9/9` locales, `76/76` scoped PASS, `:app:lintDebug` `0` errors, `Rule 8.5` PNG). |
| `1.0.16` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Linked refined `REQ-*` entries in-place to net-new `SYSREQ-TRK-007..013` and `SYSREQ-DEX-007..008`. |
| `1.0.17` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Added `REQ-FIL-027` `[Planned]` for ERTS monotonic `TIME_TOTAL` timestamp reconstruction, rolling 1.0-s gyro averaging, stationary indoor ZUPT/RTS decoupling, `DISTANCE_m_LAP` & `ENCODING_STEP_SIZE` / `PolyUtil.simplify(..., 10.0)` parity, and 9-locale `filterSlewOnly()` + `2.0 m` hysteresis. |
| `1.0.18` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `REQ-FIL-027` to `[Implemented]` following Stage 4 construction (`SpeedAndLocationDevice_ATTFused.kt`, `ExtendedRtsOdometrySmoother.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `ErtsPostWorkoutRecomputer.kt`). |
| `1.0.19` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `REQ-FIL-027` to `[Verified]` in Stage 5 following Tier 1 targeted verification (`54/54` PASS across 6 JUnit XMLs) and `verify_requirement_governance.py` (`exit 0`). |
| `1.0.20` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Stage 4 Bounce 1/3 heal: restricted wheel-speed column detection to `SPEED_mps (` (excluding `RADAR`/`YAW`/`CADENCE`/`ATT`/`GPS`/`Google`), added `.isFinite()` sanitization across `ErtsPostWorkoutRecomputer`/`ExtendedRtsOdometrySmoother`/`UnicycleOdometryEkf`, re-anchored `baseEpochMs`/`firstTimeTotalSec` on leading null `TIME_TOTAL` & `wallJumpPastTimeTotal` vs reconstructed time, and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.21` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `REQ-FIL-027` to `[Verified]` in Stage 5 (Bounce 1/3) on `8a976a35` (`58/58` Author Tier 1 + `62/62` Gate 4 Bounce 1 PASS, `verify_requirement_governance.py` `exit 0`). |
| `1.0.22` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Added `REQ-FIL-028` (`[Planned]`, `REQ-FIL-028.1`–`.5`) for activity & phone carriage mode IMU/baro classification, `SensorType.ACTIVITY_AND_CARRIAGE_MODE` 1-Hz DB/CSV persistence, adaptive EKF/filter/ERTS tuning with legacy fallback, optional `TYPE_STEP_DETECTOR` gating, and 9-locale parity plan. |
| `1.0.23` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Promoted `REQ-FIL-028` to `[Implemented]` following Stage 4 implementation and targeted unit/contract test validation across all 23 suites. |
| `1.0.24` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Promoted `REQ-FIL-028` to `[Verified]` in Stage 5 following Gate 4 Pass (`ATT-8987`: initial `195/195` across 24 suites + Rev-1 `89/89` `--rerun-tasks` across 9 suites, `0` failed/`0` skipped, `verify_requirement_governance.py` `exit 0`). |
| `1.0.25` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Stage 4 Bounce 1/3 heal: prioritized confirmed `>= 3.0 s` standstill cluster in `computeRawGnssDisplacementSpeedMps`, throttled 50-Hz gyro classify/sensor emission with `dt`-exponential smoothing + zero-alloc classifier scoring, reset `estimatedGnssYawRateRadPerSec` across expired/invalid GNSS bearing gaps, and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.26` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Promoted `REQ-FIL-028` to `[Verified]` in Stage 5 (Bounce 1/3) following Gate 4 Pass (`ATT-9006`: `92/92` `--rerun-tasks` across 9 post-heal suites + unchanged `195/195` baseline across 24 suites, `0` failed/`0` skipped, `verify_requirement_governance.py` `exit 0`). |
| `1.0.27` | 2026-11-18 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Added `REQ-CON-051` `[Planned]` (`REQ-CON-051.1`–`.3`) for non-blocking BLE search-queue timeout handoff (`notifyStopSearching(false)` + state reset) and decoupled mid-session background reconnect with `DeviceManager.java <= 1,708` LoC guardrail. |
| `1.0.28` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Promoted `REQ-CON-051` to `[Implemented]` following Stage 4 implementation (`MyRemoteDevice.java`, `MyBTLEDevice.java`, `DeviceManager.java` at `1,707` LoC). |
| `1.0.29` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Promoted `REQ-CON-051` to `[Verified]` in Stage 5 following targeted unit/contract verification, static analysis baselines, and emulator deployment. |
| `1.0.30` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Stage 6 Bounce 1/3 heal: guarded `mSearchTimeoutRunnable` on `CONNECTED_TO_GATT`/`CONNECTED_WITH_SERVICE` under `mGattLock`, synchronized `banal_service.md` & `my_btle_device.md`, and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.31` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Promoted `REQ-CON-051` to `[Verified]` in Stage 5 (Bounce 1/3) following `--rerun-tasks` verification (`125/125` PASS across 19 suites), static baselines, emulator check, and governance `exit 0`. |
| `1.0.32` | 2026-11-18 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Added `REQ-FIL-029` (`[Planned]`, `REQ-FIL-029.1`–`.5`) for shared IMU cadence DSP core extraction, carriage-classified cycling/running cadence (`BIKE_FRONT_POCKET`, `BIKE_JERSEY_BACK`, `RUN_*`) with strict `BIKE_HANDLEBAR` suppression, 50-Hz `ACC` lifecycle, explicit `CADENCE_PRIORITY`/`STRIDES_PRIORITY`, and `N/A - no UI strings`. |
| `1.0.33` | 2026-11-18 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `REQ-FIL-029` to `[Implemented]` following Stage 4 implementation (`ImuCadenceDsp.kt`, `PolarLiveRunningCadenceEstimator.kt`, `SmartphoneImuCadenceEstimator.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `MySensorManager.java`, `DeviceType.java`), `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` doc sync, and targeted unit/contract validation. |
| `1.0.34` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `REQ-FIL-029` to `[Verified]` in Stage 5 following post-rebase `--rerun` verification across 18 suites (`146/146 PASS`, `0` skipped, `0` failed), `LifecycleUtils.safeUnregisterReceiver` fix in `SpeedAndLocationDevice_ATTFused.kt`, 4 Python safety linters + static baselines PASS, live `emulator-5554` debug tracking smoke check (clean crash buffer), and `docs/engineering/walkthroughs/ATT-8895_walkthrough.md`. |
| `1.0.35` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Stage 6 Bounce 1/3 heal + Gate 4 revision: implemented per-sensor-type monotonic timestamp clamping (`resolveSensorEventTimestampMs`), monotonic cross-sensor `updateCarriageClassification()` clocks (`mLastClassifyTimestampMs`, `mLastModeEmissionTimestampMs`, `mLastAdaptationTimestampMs`), preserved mid-run `STRIDES` accumulator across `unregisterStridesSensor()`/`ensureStridesSensorRegistered()` (`SpeedAndLocationDevice_ATTFused.kt`), enabled `detectImpactWaveformStep()` for gyro-absent `RUN_BICEP_ARM`/`RUN_HANDHELD` (`SmartphoneImuCadenceEstimator.kt`), updated `WorkoutRepository.kt <= 4,973` (`+0` LoC), and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.36` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `REQ-FIL-029` to `[Verified]` in Stage 5 (Bounce 1/3) following Gate 4 Pass (`ATT-9044`), Stage 5 `--rerun` verification across all 18 unit/NFR/contract/audit/localization suites, static baselines & 4 Python safety linters PASS, live `emulator-5554` cycling/running smoke verification, and `docs/engineering/walkthroughs/ATT-8895_walkthrough.md`. |
| `1.0.37` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Stage 6 Bounce 2/3 heal: separated `currentStepAcfIntervalMs` vs `currentArmAcfIntervalMs` in `SmartphoneImuCadenceEstimator.kt` (eliminating ~50% gyro-absent `RUNNING_ARM` stride undercount and `RUNNING_ARM -> RUNNING_STEP` refractory bleed), overrode `MyAccumulatorSensor.addSensorListener` to push `getValue()` instead of raw `mValue` so mid-run `STRIDES` re-registration never pushes a transient `0` to `ProxySensor`/`FilterManager` listeners, rebased cleanly onto `sprint/2026-11-18_03_Nacht` (`e62a6ba4`), and set `[Verified]` for Stage 5 Bounce 2 verification. |
| `1.0.38` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `REQ-FIL-029` to `[Verified]` in Stage 5 (Bounce 2/3) on `sprint/2026-11-18_03_Nacht` (`e62a6ba4`) following `--rerun-tasks` verification across `29` unit/NFR/contract/audit/localization suites (`201/201 PASS`, `0` fail, `0` error, `0` skipped), static analysis baselines & 4 Python safety linters PASS, `+0` monolith LoC growth, and `docs/engineering/walkthroughs/ATT-8895_walkthrough.md`. |
| `1.0.39` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Stage 6 Bounce 3/3 heal (+ Gate 4 revision): added `BANALService.cServiceInstance` (cleared in `onDestroy()`, keeping leak-free `cServiceContext = getApplicationContext()`) and volatile `mUserSelectedBSportType` cache (resolved once in `setUserSelectedSportTypeId()` before publishing `mHaveUserSelectedSportType = true` for zero per-sample SQLite lookups on 100-Hz IMU callbacks) in `BANALService.getActiveBSportType()` + `SpeedAndLocationDevice_ATTFused.resolveActiveBSportType()`; synced `mLastEstimatorTotalStrides` during non-`RUN` dwell in `applyCadenceAndStrideSensors()`; updated `banal_service.md` (`980 LOC`), listed `BANALService.java` in `Implementation`, and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.40` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `REQ-FIL-029` to `[Verified]` in Stage 5 (Bounce 3/3) rebased cleanly atop `sprint/2026-11-19_01_Früh` (`8e44039d`) reusing Gate 4 Pass (`ATT-9068`) & Stage 4 verified test evidence (`Rule 7.5`), static baselines/linters PASS, `+0` monolith LoC growth, and fresh `docs/engineering/walkthroughs/ATT-8895_walkthrough.md`. |
| `1.0.41` | 2026-11-19 | AI Agent (`ATT-8965`, `--as agent1`) | `ATT-8965` | Added & verified `REQ-CON-052` (`[Verified]`, linked to `SYSREQ-TRK-002`) following Gate 4 Pass (`ATT-9083`: `105/105` incremental `--rerun` PASS; Author Stage 4 `103/103` across 14 suites, `0` new compiler warnings) reusing Gate 4 evidence (`Rule 7.5`) and `docs/engineering/walkthroughs/ATT-8965_walkthrough.md`. |
| `1.0.42` | 2026-11-19 | AI Agent (`ATT-8965`, `--as agent1`) | `ATT-8965` | Stage 6 Bounce 1/3 heal: restored `ATT-8895` (`1.0.32`–`1.0.39`) revision rows & fixed cancellation symbol names; added `SensorReconnectBackoffPolicy.effectiveDelayMs` live clamping before scheduling; fixed `DeviceSearchPeriodicRetryCoordinator` disable-while-pending zombie cleanup + inside-lock runnable identity scheduling + synchronized `DeviceManager` lazy init (`1,707` LoC); fixed `PolarOnlineStreamingBridge` exception reschedule, timeout/disconnect echo double-advance guard, and `blePowerStateChanged` cancellation; set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.43` | 2026-11-19 | AI Agent (`ATT-8965`, `--as agent1`) | `ATT-8965` | Promoted `REQ-CON-052` to `[Verified]` in Stage 5 (Bounce 2) reusing Gate 4 Bounce 2 Pass (`ATT-9088`: `128/128` incremental `--rerun` PASS across 16 suites, `0` failed/`0` skipped, static baselines & 4 safety linters exit `0`, zero `app/` edits post-`587730da` per `Rule 7.5`) and updated `docs/engineering/walkthroughs/ATT-8965_walkthrough.md`. |
| `1.0.44` | 2026-11-19 | AI Agent (`ATT-8891`, `--as agent1`) | `ATT-8891` | Added & verified `REQ-FIL-030` (`[Verified]`, `REQ-FIL-030.1`–`.5`) for kinematic `BSportType` discrimination & stop-filtered moving-speed sport identity rebased cleanly atop `sprint/2026-11-19_01_Früh` (`6bb37af4`), reusing Gate 4 Pass (`ATT-9091`: `19` suites / `147/147` PASS, `0` `app/` diff post-Gate 4 per `Rule 7.5`), `TrackerService.java = 3,540` LoC (`-16`), and `docs/engineering/walkthroughs/ATT-8891_walkthrough.md`. |
| `1.0.45` | 2026-11-19 | AI Agent (`ATT-8951`, `--as agent1`) | `ATT-8951` | Refined `REQ-TRK-072.7` (`[Planned]`) to position the Guided Threshold Test CTA `OutlinedButton` in `HrvDfaA1SettingsContent` immediately between `R.string.hrv_zones_guidance_desc` and the zone cards / threshold controls with single-line DE label enforcement and minimal vertical spacing (`N/A - no UI strings`). |
| `1.0.46` | 2026-11-19 | AI Agent (`ATT-8951`, `--as agent1`) | `ATT-8951` | Promoted `REQ-TRK-072` to `[Implemented]` following Stage 4 relocation of `OutlinedButton` in `ZonesSettingsActivity.kt` (`contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)`), behavioral `createComposeRule()` ordering + DE-360dp single-line tests, `Rule 8.5` PNG audit, and `34/34` targeted Gradle PASS (`--rerun`). |
| `1.0.47` | 2026-11-19 | AI Agent (`ATT-8951`, `--as agent1`) | `ATT-8951` | Promoted `REQ-TRK-072` to `[Verified]` in Stage 5 reusing Gate 4 `34/34` PASS (`Rule 7.5`, zero `app/` edits post-`3da26c27`), `Rule 8.5` PNG `img/ATT-8951_hrv_cta_above_fold.png`, `Localization Parity: N/A`, and `docs/engineering/walkthroughs/ATT-8951_walkthrough.md`. |
| `1.0.51` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Added `REQ-FIL-031` (`[Planned]`, `REQ-FIL-031.1`–`.5`, linked to `SYSREQ-TRK-003`/`006`/`010`) for triathlon/duathlon/brick multisport transition detection, retroactive dismount/mount anchoring, `MyAccumulatorSensor.resetTo(N)`, auto-pause `T2` & manual-lap clamping, named laps (`Bike`/`Transition`/`Run` across 9 locales), and zero-growth monolith ceilings. |
| `1.0.49` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Promoted `REQ-FIL-031` to `[Implemented]` following Stage 4 construction (`MultisportTransitionDetector.kt`, `MultisportTransitionBridge.kt`, `MultisportLapCoordinator.kt`, `MyAccumulatorSensor.resetTo`, `BANALService.java` `1,074` LoC, `SpeedAndLocationDevice_ATTFused.kt` `1,441` LoC, `TrackerService.java` `3,537` LoC [`-3`], 9-locale `strings_cockpit.xml`, and `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` sync). |
| `1.0.50` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Promoted `REQ-FIL-031` to `[Verified]` in Stage 5 rebased cleanly atop `sprint/2026-11-19_01_Früh` (`50a57b3e`), reusing Gate 4 Pass (`ATT-9113`: `17` suites / `140/140` PASS, `0` warnings, `0` `app/` diff post-Gate 4 per `Rule 7.5`), static baselines & safety linters PASS, and `docs/engineering/walkthroughs/ATT-8917_walkthrough.md`. |
| `1.0.51` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Stage 6 Bounce 1/3 heal verified (`ATT-9128`: `11` suites / `123/123` PASS, `TrackerService.java = 3,538 <= 3,540`): scoped branch 3 cycling override to parked bike hardware (`hw.hardwareSportType == BIKE`), moved `resetForSession` past in-memory `mLiveSession` reuse guard, and moved `mLastMultisportTickMs` reset inside `synchronized(mYawRateLock)`; rebased cleanly atop `sprint/2026-11-19_02_Spät` (`73ff0cdd`). |
| `1.0.52` | 2026-11-19 | AI Agent (`ATT-8859`, `--as agent1`) | `ATT-8859` | Added `REQ-SEN-032` `[Planned]` (`REQ-SEN-032.1`–`.3`) and refined `REQ-SEN-029.3` / `REQ-SEN-031.2` dialog option text so `WheelPositionSelectionDialog` presents strictly `Front` and `Rear` while preserving `WheelPosition.UNSPECIFIED` enum/DB fallback and 9-locale legacy summary display (`N/A - no UI strings`). |
| `1.0.53` | 2026-11-19 | AI Agent (`ATT-8859`, `--as agent1`) | `ATT-8859` | Promoted `REQ-SEN-032` to `[Implemented]` following Stage 4 implementation in `WheelPositionSelectionDialog.kt`, behavioral Compose UI test `WheelPositionSelectionDialogUiTest.kt` (`4/4` PASS), `Rule 8.5` Light/Dark PNG capture (`ATT-8859_wheel_position_dialog.png`), and `35/35` targeted test PASS across 6 suites post-rebase onto `6927b2f8`. |
| `1.0.54` | 2026-11-19 | AI Agent (`ATT-8859`, `--as agent1`) | `ATT-8859` | Promoted `REQ-SEN-032` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9145`) `35/35` PASS evidence (`Rule 7.5`, zero `app/` diff since `9e265f02`), `Rule 8.5` Light/Dark PNG audit (`ATT-8859_wheel_position_dialog.png`), and `verify_requirement_governance.py` `exit 0`. |
| `1.0.55` | 2026-11-19 | AI Agent (`ATT-9064`, `--as agent1`) | `ATT-9064` | Added `REQ-FIL-032` `[Planned]` (`REQ-FIL-032.1`–`.3`, linked to `SYSREQ-TRK-003`/`004`) for beat-immune 1-Hz ERTS timestamping, authoritative `speedAverage_mps` / `ExtremaType.AVG(SPEED_mps)` parity with matched `activeDist / activeDt` fallback, and stationary `[E, N]` covariance freeze & backward RTS pinning (`N/A - no UI strings`). |
| `1.0.56` | 2026-11-19 | AI Agent (`ATT-9064`, `--as agent1`) | `ATT-9064` | Promoted `REQ-FIL-032` to `[Implemented]` following Stage 4 construction (`ErtsPostWorkoutRecomputer.kt` `1,051` LoC, `ExtendedRtsOdometrySmoother.kt` `742` LoC, `UnicycleOdometryEkf.kt` `810` LoC; `0` monolith edits; `84/84` PASS across 9 targeted/contract suites, `0` warnings, 4 safety linters PASS). |
| `1.0.57` | 2026-11-19 | AI Agent (`ATT-9064`, `--as agent1`) | `ATT-9064` | Promoted `REQ-FIL-032` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9167`) `84/84` PASS evidence (`Rule 7.5`, zero `app/` diff since `75f3bff9`), 4 safety linters PASS, `verify_requirement_governance.py` `exit 0`, and `docs/engineering/walkthroughs/ATT-9064_walkthrough.md`. |
| `1.0.58` | 2026-11-19 | AI Agent (`ATT-9063`, `--as agent1`) | `ATT-9063` | Added `REQ-FIL-033` `[Planned]` (`REQ-FIL-033.1`–`.3`, linked to `SYSREQ-TRK-003`/`004`) for DEM grid-step slope decoupling, `±35%` forward+backward clamping, low-speed slope damping, contractive OU standstill decay, 3×3 Joseph SPD updates, speed-coupled NaN-safe barometer slew, and barometer-free/leading-null fallback (`N/A - no UI strings`). |
| `1.0.59` | 2026-11-19 | AI Agent (`ATT-9063`, `--as agent1`) | `ATT-9063` | Promoted `REQ-FIL-033` to `[Implemented]` following Stage 4 construction (`ExtendedRtsAltitudeSlopeSmoother.kt` `547` LoC, `BarometricAltitudeFilter.kt` `234` LoC, `ErtsPostWorkoutRecomputer.kt` `1,054` LoC; `0` monolith edits; `109/109` PASS across 11 targeted/contract suites, `0` warnings). |
| `1.0.60` | 2026-11-19 | AI Agent (`ATT-9063`, `--as agent1`) | `ATT-9063` | Promoted `REQ-FIL-033` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9196`) `109/109` PASS evidence (`Rule 7.5`, zero `app/` diff since `0f4dd34b`), `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9063_walkthrough.md`. |
| `1.0.61` | 2026-11-20 | AI Agent (`ATT-9062`, `--as agent1`) | `ATT-9062` | Added `REQ-FIL-034` `[Planned]` (`REQ-FIL-034.1`–`.4`, linked to `SYSREQ-TRK-003`/`004`/`010`) for indoor GNSS-outage/stagnant-fix/decay-to-zero `STATIONARY` transition, cyclic-immune `detectGlanceShift()`, accuracy-gated multipath speed & `isMovingByGnss`, and cross-device/athlete `ImuCadenceDsp` footstrike portability (`N/A - no UI strings`). |
| `1.0.62` | 2026-11-20 | AI Agent (`ATT-9062`, `--as agent1`) | `ATT-9062` | Promoted `REQ-FIL-034` to `[Implemented]` following Stage 4 construction (`PhoneCarriageClassifier.kt` `1,062` LoC, `SpeedAndLocationDevice_ATTFused.kt` `1,459 / 1,500` LoC; `0` monolith edits; `135/135` PASS across 12 executed suites, `0` new warnings). |
| `1.0.63` | 2026-11-20 | AI Agent (`ATT-9062`, `--as agent1`) | `ATT-9062` | Promoted `REQ-FIL-034` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9208`) `135/135` PASS evidence (`Rule 7.5`, zero `app/` diff since `4b47831a`), clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9062_walkthrough.md`. |
| `1.0.64` | 2026-11-20 | AI Agent (`ATT-9157`, `--as agent1`) | `ATT-9157` | Added `REQ-FIL-035` `[Planned]` (`REQ-FIL-035.1`–`.3`, linked to `SYSREQ-TRK-003`/`004`/`010`) for `PhoneCarriageClassifier` sport-context cycling guardrail + non-sticky eviction, easy-pace `walkingStepBand`/`walkingSpeedBand` bypass, front-hip running belt scoring tiers, and chatter-resistant `SCORE_MARGIN = 0.85` + asymmetric dwell (`0` edits to `SpeedAndLocationDevice_ATTFused.kt`, `N/A - no UI strings`). |
| `1.0.65` | 2026-11-20 | AI Agent (`ATT-9157`, `--as agent1`) | `ATT-9157` | Promoted `REQ-FIL-035` to `[Implemented]` following Stage 4 construction (`PhoneCarriageClassifier.kt` `1,124 / 1,500` LoC, `SpeedAndLocationDevice_ATTFused.kt` `1,459` LoC [`0` edits], `0` monolith edits; `138/138` PASS across 12 suites, `0` new compiler warnings). |
| `1.0.66` | 2026-11-20 | AI Agent (`ATT-9157`, `--as agent1`) | `ATT-9157` | Promoted `REQ-FIL-035` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9224`) `138/138` PASS evidence (`Rule 7.5`, zero `app/` diff since `9b89af95`), clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9157_walkthrough.md`. |
| `1.0.67` | 2026-11-20 | AI Agent (`ATT-9158`, `--as agent1`) | `ATT-9158` | Added `REQ-FIL-036` `[Planned]` (`REQ-FIL-036.1`–`.3`, linked to `SYSREQ-TRK-003`/`004`/`010`) for corroborated cold-start pinning release with unbypassable indoor/wheel/rest vetoes & anti-oscillation guard, raw-chord heading seeding + unanchored/post-outage velocity decoupling + bounded Doppler variance, and `dt_gnss`-normalized ERTS teleport decoupling + 1-tick glitch pre-gate + backward catch-up velocity bound (`SpeedAndLocationDevice_ATTFused.kt <= 1,485 / 1,500` LoC, `N/A - no UI strings`). |
| `1.0.68` | 2026-11-20 | AI Agent (`ATT-9158`, `--as agent1`) | `ATT-9158` | Promoted `REQ-FIL-036` to `[Implemented]` following Stage 4 construction (`AttFusedColdStartHelper.kt` `256` LoC, `SpeedAndLocationDevice_ATTFused.kt` `1,481 / 1,500` LoC, `SpeedAndLocationDevice.java` `824` LoC, `UnicycleOdometryEkf.kt` `844` LoC, `ExtendedRtsOdometrySmoother.kt` `920` LoC; `0` monolith edits; `144/144` PASS across 12 suites + `:app:assembleDebug`, `0` new compiler warnings). |
| `1.0.69` | 2026-11-20 | AI Agent (`ATT-9158`, `--as agent1`) | `ATT-9158` | Promoted `REQ-FIL-036` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9246`) `144/144` PASS evidence on `97f49453` (`Rule 7.5`, zero `app/` diff since `97f49453`), `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9158_walkthrough.md`. |
| `1.0.70` | 2026-11-20 | AI Agent (`ATT-9156`, `--as agent1`) | `ATT-9156` | Added `REQ-FIL-037` `[Planned]` (`REQ-FIL-037.1`–`.3`, linked to `SYSREQ-TRK-003`/`004`/`008`) for live `KinematicAltitudeSlopeEkf` coherent `DEFAULT_VAR_DEM = 25.0 m²` / `DEFAULT_VAR_GPS = 64.0 m²`, baro/standstill slope decoupling (`k1 = 0`) + 3×3 Joseph SPD update, post-anchor baro-vs-DEM step-discrepancy jump gating with reference tracker advancement + quadratic Huber variance inflation + `varRef <= 1.0` high-precision guard (`N/A - no UI strings`). |
| `1.0.71` | 2026-11-20 | AI Agent (`ATT-9156`, `--as agent1`) | `ATT-9156` | Promoted `REQ-FIL-037` to `[Implemented]` following Stage 4 construction (`KinematicAltitudeSlopeEkf.kt` `614 / 1,500` LoC, `AltitudeAndSlopeDevice_ATTFused.kt` `514 / 1,500` LoC; `0` protected monolith edits; `72/72` PASS across 7 unit & contract suites + `:app:assembleDebug`, `0` new compiler warnings). |
| `1.0.72` | 2026-11-20 | AI Agent (`ATT-9156`, `--as agent1`) | `ATT-9156` | Promoted `REQ-FIL-037` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9260`) independent `72/72` PASS evidence on `0b792832` (`Rule 7.5`, zero `app/` diff since `0b792832`), `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9156_walkthrough.md`. |
| `1.0.73` | 2026-11-20 | AI Agent (`ATT-8422`, `--as agent1`) | `ATT-8422` | Added `REQ-SEN-033` `[Planned]` (`REQ-SEN-033.1`–`.3`, linked to `SYSREQ-TRK-001`/`010`) for `EditDeviceDialog` wheel-sensor calibration UI cleanup (removing known-distance calculator for bike sensors while preserving `RUN_SPEED`), initial tire-size preset + localized auto-calibration hint & baseline label, `EquipmentRepository` chronological per-sensor history query & async baseline sync, and embedded `WheelCircumferenceHistoryChart` with `UNSPECIFIED` series/legend fallback, sensor empty state, unified baseline button, and 9-locale parity. |
| `1.0.74` | 2026-11-20 | AI Agent (`ATT-8422`, `--as agent1`) | `ATT-8422` | Promoted `REQ-SEN-033` to `[Implemented]` following Stage 4 construction (`EditDeviceDialog.kt` shrunk `1,843 -> 1,774 / 2,004` LoC via new `WheelCircumferenceSection.kt` `168` LoC, `EditDeviceViewModel.kt` `429` LoC, `DeviceDataRepository.kt` `738` LoC, `EquipmentRepository.kt` `315` LoC, `WheelCircumferenceHistoryChart.kt` `298` LoC, 18 9-locale XML files; `0` monolith edits; `61/61` PASS across 11 suites + `:app:assembleDebug`, `0` production compiler warnings, Rule 8.5 PNG visually verified). |
| `1.0.75` | 2026-11-20 | AI Agent (`ATT-8422`, `--as agent1`) | `ATT-8422` | Promoted `REQ-SEN-033` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9268`) independent `61/61` PASS evidence on `f05ab17b` (`Rule 7.5`, zero `app/` diff since `f05ab17b`), `Rule 8.5` Light/Dark PNG certification (`docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png`), `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-8422_walkthrough.md`. |
| `1.0.76` | 2026-11-20 | AI Agent (`ATT-9159`, `--as agent1`) | `ATT-9159` | Added `REQ-TRK-090` `[Planned]` (`REQ-TRK-090.1`–`.2`, linked to `SYSREQ-TRK-003`) for pause-neutral pre-mutation UTC `TIME_START` lap start persistence in `MultisportLapCoordinator`, `TrackerService.saveLap`, and `LapsDatabaseManager.saveLap` (`now - lapTime` UTC fallback, preserving 4-/5-arg APIs and `TrackerService.java <= 3,559` LoC, `N/A - no UI strings`). |
| `1.0.77` | 2026-11-20 | AI Agent (`ATT-9159`, `--as agent1`) | `ATT-9159` | Promoted `REQ-TRK-090` to `[Implemented]` following Stage 4 construction (`MultisportLapCoordinator.kt`, `TrackerService.java` `3,539 / 3,559` LoC, `LapsDatabaseManager.java`, `tracker_service.md`). |
| `1.0.78` | 2026-11-20 | AI Agent (`ATT-9159`, `--as agent1`) | `ATT-9159` | Promoted `REQ-TRK-090` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9294`) `103/103 PASS` across 10 targeted/contract suites on `45c3fd9c` (`Rule 7.5`, zero `app/` diff since `45c3fd9c`, `0` new compiler warnings), clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9159_walkthrough.md`. |
| `1.0.79` | 2026-11-20 | Lane B Author (`ATT-8994`, `--as agent1`) | `ATT-8994` | Added `REQ-FIL-038` (`[Planned]`, `.1`–`.4`, Derived SW-only) for lock-free `MySensor.deactivateSensor()` null notification, silent `MyAccumulatorSensor`/`ProxySensor` overrides, dynamic HRM sensor deactivation, `FilterManager` disconnect clearing with accumulator/cumulative immunity & `ProxySensor` retention, and sparkline flat-line termination (`N/A - no UI strings`). |
| `1.0.80` | 2026-11-20 | Lane B Author (`ATT-8994`, `--as agent1`) | `ATT-8994` | Promoted `REQ-FIL-038` to `[Implemented]` following Stage 4 construction (`MySensor.java`, `MyAccumulatorSensor.java`, `ProxySensor.java`, `FilterManager.kt`, `ANTHeartRateDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`), `:app:assembleDebug`, `0` new Kotlin compiler warnings, and 100% PASS across all 16 unit/concurrency/contract suites. |
| `1.0.81` | 2026-11-20 | Lane B Author (`ATT-8994`, `--as agent1`) | `ATT-8994` | Promoted `REQ-FIL-038` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9293`) verified `140/140 PASS` evidence across all 16 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` code drift since `b34c62cc`), `4/4` Python safety linters PASS, clean-checkout `TST-FIL-038` verbatim traceability parity, and `docs/engineering/walkthroughs/ATT-8994_walkthrough.md`. |
| `1.0.82` | 2026-11-20 | AI Agent (`ATT-8421`, `--as agent1`) | `ATT-8421` | Added `REQ-SEN-034` `[Planned]` (`REQ-SEN-034.1`–`.3`, linked to `SYSREQ-TRK-004`/`010`) for `BANALServiceRepository` persisted `baselineCircumference` IO cache + live reset propagation (`DeviceDataRepository.baselineCircumferenceChanged` buffered `SharedFlow`, `CALIBRATION_FACTOR_CHANGED` via `registerInternalReceiver()`, `clearBreadcrumbs()` / `READY -> TRACKING` reload, explicit `0.0` overwrite), strict null/non-finite GNSS accuracy/bearing skip guard with GPS-free `DualWheelTpmsEstimator` isolation, and `ATT-8422` 9-locale Baseline label parity verification. |
| `1.0.83` | 2026-11-20 | AI Agent (`ATT-8421`, `--as agent1`) | `ATT-8421` | Promoted `REQ-SEN-034` to `[Implemented]` following Stage 4 construction (`BANALServiceRepository.kt` `1,336 / 1,500` LoC, `DeviceDataRepository.kt` `764 / 1,500` LoC, `0` protected monolith edits, `0` new Kotlin compiler warnings, `75/75` PASS across `12` suites + `:app:assembleDebug`). |
| `1.0.84` | 2026-11-20 | AI Agent (`ATT-8421`, `--as agent1`) | `ATT-8421` | Promoted `REQ-SEN-034` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9292`) independent `77/77` PASS evidence on `ba662145` (`Rule 7.5`, zero `app/` diff since `ba662145`, `BANALServiceRepository.kt = 1,354 / 1,500`, `DeviceDataRepository.kt = 769 / 1,500`, `0` protected monolith edits, `0` new Kotlin warnings, `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-8421_walkthrough.md`). |
| `1.0.85` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Added `REQ-CON-053` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-TRK-001`/`SYSREQ-TRK-002`) for unified 4-stream atomic CAS/`CoroutineStart.LAZY`/job-identity guards, bounded PMD `ERROR_ALREADY_IN_STATE` recovery across `ACC`/`GYRO`/`PPI`, deduplicated Snackbar warning (`PolarPmdStreamWarningEffect`), and 9-locale `strings_devices.xml` parity. |
| `1.0.86` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Promoted `REQ-CON-053` to `[Implemented]` following Stage 4 construction (`PolarPmdStreamCoordinator.kt` `294` LoC, `PolarOnlineStreamingBridge.kt` shrunk `1,306 -> 1,261/1,500` LoC, `PolarPmdStreamWarningEffect.kt` `56` LoC, `TrackingTabsScreen.kt` `1,419/1,500` LoC, `DevicesTabbedScreen.kt` `813/1,500` LoC, 9-locale `strings_devices.xml`), `0` protected monolith edits, `:app:assembleDebug` + `172/172` PASS across 22 suites, and `0` new Kotlin warnings. |
| `1.0.87` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Promoted `REQ-CON-053` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9309` @ `80bd02ca`) `172/172 PASS` across 22 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` drift since `80bd02ca`), `4/4` Python safety linters PASS, clean-checkout `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9161_walkthrough.md`. |
| `1.0.88` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Stage 6 Bounce 1/3 heal: executed `onFinalCleanup` and `onStreamsCancelled` atomically under `slotLock` prior to slot release, added `preAcquire` exception CAS rollback in `PolarPmdStreamCoordinator.kt`, wired `cancelAllStreams { releaseOnlineAccChannels() }` in `PolarOnlineStreamingBridge.kt`, added deterministic ownership test in `PolarPmdStreamCoordinatorTest.kt`, and set `[Implemented]` pending Gate 4 / Stage 5 re-verification. |
| `1.0.89` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Promoted `REQ-CON-053` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9313` @ `79e9e1b1`) `173/173 PASS` across 22 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` drift since `79e9e1b1`), `4/4` Python safety linters PASS, clean-checkout `--check-verified-evidence` EXIT `0`, and refreshed `ATT-9161_walkthrough.md`. |
| `1.0.90` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Added `REQ-CON-054` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-TRK-001`/`SYSREQ-TRK-002`) for alias-complete GATT/Polar unpair/delete teardown, connected-GATT discovery fallback, and shared `12 s` silent-HR watchdog & CCCD retry (`N/A - no UI strings`). |
| `1.0.91` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Promoted `REQ-CON-054` to `[Implemented]` following Stage 4 construction (`BleHrmSilentConnectionWatchdog.kt`, `PolarOfflineGattCoordinator.kt`, `PolarPmdStreamCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `MyBTLEDevice.java`, `BTLEHeartRateDevice.java`, `BTSearchForNewDevicesEngine.java`, `DeviceDataRepository.kt`, `DeviceManager.java` `1,707/1,708` `+0` LoC, `0` protected monolith edits). |
| `1.0.92` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Promoted `REQ-CON-054` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9336` @ `3b81ce09`) `180/180 PASS` across 19 suites + `:app:assembleDebug` + `0` new Kotlin warnings (`Rule 7.5`, `0` `app/` drift since `3b81ce09`), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9155_walkthrough.md`. |
| `1.0.93` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Stage 6 Bounce 1/3 heal: added monotonic watchdog `generation` + `silentHrWatchdogLock` job-identity guard (`BleHrmSilentConnectionWatchdog`, `PolarOnlineStreamingBridge`, `BTLEHeartRateDevice`), stale `gatt != mBluetoothGatt` guard in `onDescriptorWriteCompleted()`, `!posted -> closeRunnable.run()` GATT close fallback in `MyBTLEDevice.disconnectFromGatt()`, and `my_btle_device.md` precision fix; set `[Implemented]` pending Gate 4 / Stage 5 re-verification. |
| `1.0.94` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Promoted `REQ-CON-054` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9351` @ `98a377f6`) `184/184 PASS` across 19 suites + `:app:assembleDebug` + `0` new production Kotlin warnings (`Rule 7.5`, `0` `app/` drift since `98a377f6`), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9155_walkthrough.md`. |
| `1.0.90` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Added `REQ-TRK-091` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-TRK-005`/`SYSREQ-DEX-007`) for decoupled stop-workout post-processing, armed-token isolation, atomic export deferral lock, edit-safe telemetry refresh, `shortService` FGS, and 9-locale progress notification `8047`. |
| `1.0.91` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Promoted `REQ-TRK-091` to `[Implemented]` (`TrackerService.java` `3,511/3,559`, `WorkoutRepository.kt` `4,953/5,117`, `PolarVeritySenseOfflineController.kt` `1,483/1,500`, `WorkoutPostProcessing{Coordinator,Service,NotificationManager}.kt`, 9-locale `strings.xml`; `200/200` PASS across 23 suites + `:app:assembleDebug` + `0` new Kotlin warnings). |
| `1.0.92` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Promoted `REQ-TRK-091` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9341` @ `9ca92f78`) `115/115` Bounce 1 (17 suites) + `200/200` full 23-suite (`7d3b0908`) PASS + `:app:assembleDebug` + `0` new Kotlin warnings (`Rule 7.5`, `0` `app/` diff since `9ca92f78`), `4/4` Python safety linters PASS, `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9154_walkthrough.md`. |
| `1.0.93` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Stage 6 Bounce 1/3 heal: overrode both 1-arg `onTimeout(startId)` and 2-arg `onTimeout(startId, fgsType)` in `WorkoutPostProcessingService.kt`, preserved transient `syncToHealthConnect` across deferred export via `MutableMap<Long, Boolean?>` + `DeferredExportDecision`, added `hostLock -> stateLock` TOCTOU protection, propagated `initialHadWarning` on drain restart, exposed `currentProgress` for `onStartCommand()`, and reset to `[Implemented]` pending Gate 4 / Stage 5 re-verification. |
| `1.0.94` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Promoted `REQ-TRK-091` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9361` @ `7725517b`) `113/113 PASS` (16 suites, `+6` regression tests) layered on full 23+ suite baseline + `:app:assembleDebug` + `0` new Kotlin warnings (`Rule 7.5`, `0` `app/` diff since `7725517b`), `4/4` Python safety linters PASS, `--check-verified-evidence` `exit 0`, and refreshed `ATT-9154_walkthrough.md`. |
