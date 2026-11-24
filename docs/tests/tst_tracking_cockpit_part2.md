# Living Test Specification: Tracking & Live Sensor Cockpit (Part 2 of 2)

**Document ID**: `DOC-TST-TRK-P2`  
**Version**: 1.0.39  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `tracking_cockpit_part2`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.  
**Total Tests**: 104

---

### TST-CON-029: **Cycling Radar Traffic Simulator, Pipeline Integration & Parity Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-029` (`REQ-CON-029.1`..`REQ-CON-029.5`)
- **Component**: `RadarTrafficSimulator.kt`, `SimulatedRadarDevice.kt`, `BANALService.java`, `BTLERadarDevice.java`, `TrackerService.java`, `RadarStateRepository.kt`, `RadarWindowAggregator.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Jira**: `ATT-5087`, `ATT-6704`
- **Test Description**:
  1. *Scenario Generation & 8 Hz Stream Unit Tests (`TST-CON-029.1`, `RadarTrafficSimulatorTest.kt`)*:
  • Verify all 5 scenario presets emit synthetic target vectors at 8 Hz with correct kinematics (range, approach speed, threat level).
  • Verify Single Overtaking Car passing increments `RADAR_VEHICLE_COUNT` via hysteresis.
  • Verify Multi-Vehicle Convoy generates 3 simultaneous distinct target IDs.
  • Verify High-Speed Threat closes at 25 m/s with `FAST_APPROACHING` threat level.
  • Verify Mid-Ride Disconnect transitions to `DISCONNECTED_ERROR` and recovers after 5 seconds.
  2. *Scalar MySensor Publishing & Bridge Unit Tests (`TST-CON-029.2`, `SimulatedRadarDeviceTest.kt`, `RadarTrafficSimulatorTest.kt`)*:
  • Verify `SimulatedRadarDevice` registers 6 scalar `MySensor` channels (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) on `MySensorManager`.
  • Verify 1 Hz window aggregator snapshot triggers `newValue()` publishing to each registered sensor.
  • Verify `stop()` unregisters sensors cleanly without leaking in `MySensorManager`.
  3. *Hardware Precedence, Preemption & Workout Teardown Unit Tests (`TST-CON-029.3`, `BTLERadarDeviceTest.kt`, `TrackerServiceTest.kt`)*:
  • Verify simulation halts immediately and yields when physical radar streams data (`onPhysicalHardwareConnected()`).
  • Verify `TrackerService.handleActionStop()` and `onDestroy()` trigger simulator `stop()`, guaranteeing zero lingering simulation or phantom DB writes post-workout.
  4. *Reactive StateFlow & Monotonic Clock Tests (`TST-CON-029.4`, `RadarTrafficSimulatorTest.kt`)*:
  • Verify `isSimulatingFlow: StateFlow<Boolean>` emits `true` when simulation starts and `false` when stopped or preempted.
  • Verify elapsed simulation timing relies strictly on `SystemClock.elapsedRealtime()` or monotonic time source, immune to wall-clock shifts.
  • Verify coroutines run under `SupervisorJob` and cancel cleanly without unhandled exceptions.
  5. *UI ExposedDropdownMenuBox & 9-Language Localization Tests (`TST-CON-029.5`, `TranslationParityTest.kt`)*:
  • Verify `RadarSettingsSection.kt` renders scenario selection using `ExposedDropdownMenuBox` with 5 scenarios, obeying Design Guidelines §1.3 and Rule 38.
  • Verify status text formatting `radar_simulator_status_format` and all scenario names maintain 100% key and format parity across partitioned `strings_radar.xml` in all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  6. *Clean-Room Targeted Test Suite Execution (`TST-CON-029.6`)*:
  • Execute targeted package tests (`com.atrainingtracker.banalservice.devices.radar.*`, `com.atrainingtracker.trainingtracker.audio.radar.*`, `com.atrainingtracker.trainingtracker.ui.settings.radar.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.


### TST-TRK-062: **Live Rearview Radar Vertical Edge Overlay & Gesture Pass-Through Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-062` (`REQ-TRK-062.1`..`REQ-TRK-062.6`)
- **Component**: `RadarEdgeOverlay.kt`, `TrackingTabsScreen.kt`, `BTLERadarDevice.java`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `AdvancedTuningAccordion.kt`, `strings_radar.xml`
- **Jira**: `ATT-5085`, `ATT-6703`, `ATT-7537`, `ATT-7744`, `ATT-8679`
- **Test Description**:
  1. *Radar Edge Rail & Full-Screen Kinematics Unit Tests (`TST-TRK-062.1`, `RadarEdgeOverlayTest.kt`)*:
  • Verify target range normalization ($140\text{ m} \to 1.0\text{ (bottom)}$, $70\text{ m} \to 0.5\text{ (mid)}$, $0\text{ m} \to 0.0\text{ (top/cyclist)}$) and multi-target coordinate resolution, asserting vehicles approach upward from bottom to top in both edge rail and `FULL_SCREEN` mode.
  • Verify lateral lane offset and traffic orientation (RHT vs LHT) mapping across full tracking screen width in `FULL_SCREEN` mode.
  • Verify boundary clamping: $> 140\text{ m} \to 1.0$, negative distance $\to 0.0$, degenerate max range $\le 0\text{ m} \to 0.0$.
  2. *Semantic Threat Color & Conditional Pulsing Alert Tests (`TST-TRK-062.2`, `RadarEdgeOverlayTest.kt`)*:
  • Verify color mapping (Green = `NONE`, Amber = `APPROACHING`, Red = `FAST_APPROACHING`).
  • Verify pulsing red alert animation is evaluated strictly when `overallThreatLevel == ThreatLevel.FAST_APPROACHING`, yielding constant 1.0f alpha during clear and standard approach.
  3. *Fail-Safe Disconnect Warning, Error State & Active-Threat Badge Action Tests (`TST-TRK-062.3`, `RadarEdgeOverlayTest.kt`)*:
  • Verify `DISCONNECTED_ERROR` and `DISCONNECTED` render warning indicator (`RadarStatusBadge` with `ic_device_not_available`) and strictly prohibit green clear state across all overlay modes (`LEFT`, `RIGHT`, `FULL_SCREEN`).
  • Verify tapping `RadarStatusBadge` in `DISCONNECTED_ERROR` invokes `onReconnectClick` (`REQUEST_START_SEARCH_FOR_PAIRED_DEVICES`), and tapping `RadarStatusBadge` during active threats (`APPROACHING` / `FAST_APPROACHING`, with or without audio snooze) invokes `onBadgeClick` (`RadarLightQuickSwitchSheet`).
  4. *Clear-State Badge Suppression, Rail Alignment & Touch Pass-Through Compose UI Tests (`TST-TRK-062.4`, `RadarEdgeOverlayTest.kt`)*:
  • Using Robolectric Compose `createComposeRule()` with ZERO `app/src/main/` file reads (`REQ-PRO-049.1`), verify `CONNECTED` + clear road (`targets.isEmpty()`, `ThreatLevel.NONE`, both unsnoozed and `audioSnoozeState.isSnoozed == true`) renders ZERO `RadarStatusBadge` (`assertDoesNotExist()`) across `RadarEdgeSide.LEFT`, `RadarEdgeSide.RIGHT`, and `RadarEdgeSide.FULL_SCREEN` (covering Start tab `page == 0` and live tracking tabs `page > 0`).
  • Verify in `LEFT`/`RIGHT` clear-road mode (`SLIM_RAIL`) that guarding the top `8.dp` + `6.dp` `Spacer`s on badge visibility aligns `RadarVerticalRail` at `y = 0` without a `14.dp` downward shift.
  • Verify `FULL_SCREEN` clear mode renders neither background track rail nor `RadarStatusBadge` (zero touch-intercepting nodes), and `AUTO_HIDE_3S` fades out the vertical rail after 3s with zero clear-state badge rendered and zero ghost click traps.
  5. *TuningPreferencesDataStore Edge Side, Clear Mode & Accordion Subtitle Tests (`TST-TRK-062.5`, `TuningPreferencesDataStoreTest.kt`, `RadarAccordionSectionTest.kt`, `RadarSettingsSectionTest.kt`)*:
  • Verify default values (`RIGHT`, `SLIM_RAIL`) and persistence of custom 3-way `RadarEdgeSide` (`LEFT`, `FULL_SCREEN`, `RIGHT`) with backward-compatible decode.
  • Verify settings UI displays 3-way segmented button (`Links | Vollbild | Rechts`) and conditionally hides `RadarClearMode` selector when `FULL_SCREEN` is active.
  • Verify `AdvancedTuningAccordion.formatCyclingRadarSubtitle` formats `RadarEdgeSide.FULL_SCREEN` correctly.
  6. *9-Language Localization Parity & Unused Resource Cleanup Test (`TST-TRK-062.6`, `TranslationParityTest.kt`)*:
  • Verify 100% key and format parity across `strings_radar.xml` in all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for all active overlay strings (`radar_overlay_*`, including `radar_overlay_mode_fullscreen`, `radar_overlay_threats*`, `radar_overlay_disconnected`) after cleanly removing orphaned `radar_overlay_clear` and `radar_overlay_connecting` across all 9 locales and orphaned `ic_device_available.xml` (`:app:lintDebug` `UnusedResources` compliance).
  7. *Clean-Room Targeted Test Suite Execution (`TST-TRK-062.7`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlayTest`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*`) asserting 100% pass rate.
  8. *Cockpit Overlay Search Suppression & Reconnect Error Retention Tests (`TST-TRK-062.8`, `TrackingTabsScreenTest.kt`, `BTLERadarDeviceTest.kt`)*:
  • Verify `TrackingTabsScreen` suppresses `RadarEdgeOverlay` when `radarState.connectionState == RadarConnectionState.CONNECTING` or initial `DISCONNECTED`, and displays it strictly when `CONNECTED`, `DISCONNECTED_ERROR`, or `isSimulating`.
  • Verify `BTLERadarDevice.startSearching()` preserves `DISCONNECTED_ERROR` when already in error state during reconnect attempts.
  9. *Visual Self-Inspection `@Preview` Screenshot (`TST-TRK-062.9`, `Rule 8.5`)*:
  • Capture Light/Dark `@Preview` screenshot PNG at `docs/engineering/walkthroughs/img/ATT-8679_radar_edge_overlay_preview.png` covering `ClearLight` (zero green badge), `ApproachingDark`, `FastThreatAmoled`, and `Disconnected` states, and inspect via `read_file` by both `agent1` and `agent2`.

### TST-TRK-063: **Radar Audio Chime and Haptic Alert Engine Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-063` (`REQ-TRK-063.1`..`REQ-TRK-063.7`)
- **Component**: `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `TrackerService.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Jira**: `ATT-5086`, `ATT-6709`, `ATT-6705`
- **Test Description**:
  1. *Threat Transition & Debounce Unit Tests (`TST-TRK-063.1`, `RadarAlertManagerTest.kt`)*:
  • Verify audio chime and vibration triggers on `NONE -> APPROACHING`, escalation to `FAST_APPROACHING`, all-clear `-> NONE`, and disconnect `-> DISCONNECTED_ERROR`. Verify debouncing prevents repeated chimes on continuous 8 Hz range telemetry.
  • Verify `FAST_APPROACHING` escalation occurring within `< 800 ms` of initial approach is NOT debounced, immediately triggers the fast-threat chime and vibration, and debounced non-escalating frames do not mutate `lastThreatLevel`.
  • Verify `FAST_APPROACHING` escalation selects the target with highest approach speed to drive alert pitch and spatial panning when multiple targets exist.
  • Verify monotonic time source resilience against backward and forward wall-clock jumps for alert debouncing and cooldowns (`ATT-7580`).
  2. *PCM Audio Synthesis & MODE_STATIC Replay Tests (`TST-TRK-063.2`, `RadarAudioSynthesizerTest.kt`)*:
  • Verify multi-tone PCM wave generation (non-empty byte arrays, 16-bit signed PCM within [-1.0, 1.0]), `USAGE_MEDIA` AudioAttributes, and warm `MODE_STATIC` replay (`pause()`, `playbackHeadPosition = 0`, `play()`).
  3. *Independent Volume Boost & Safe Restoration Tests (`TST-TRK-063.3`, `RadarAlertManagerTest.kt`)*:
  • Verify saving everyday stream volume, boosting hardware volume during chime playback with `flags = 0`, and guaranteed auto-restoration in `finally`, on `stop()`, and service `onDestroy()`.
  • Verify speaker boost activates when audio routes to `SYSTEM_DEFAULT` and no external wired/Bluetooth headphones are connected (`isHeadphonesOrBluetoothConnected() == false`).
  • Verify `stop()` removes pending `restoreRunnable` callbacks on the handler.
  4. *Transient Audio Ducking Tests (`TST-TRK-063.4`, `RadarAlertManagerTest.kt`)*:
  • Verify `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` request before playback and immediate abandonment on chime completion.
  5. *Audio Device Routing Tests (`TST-TRK-063.5`, `RadarAudioSynthesizerTest.kt`)*:
  • Verify `AudioTrack.setPreferredDevice()` sets `TYPE_BUILTIN_SPEAKER` when forced, and `null` for system default / connected headphones.
  6. *TuningPreferencesDataStore Alert Preferences & Reactive Caching Tests (`TST-TRK-063.6`, `TuningPreferencesDataStoreTest.kt`, `RadarAlertManagerTest.kt`)*:
  • Verify defaults and DataStore persistence for alert mode, volume, speaker boost, audio route, and haptic vibration.
  • Verify `RadarAlertManager` collects `tuningConfigFlow` reactively and caches configuration without per-frame DataStore queries on 8 Hz telemetry.
  7. *9-Language Localization Parity & Compact Segmented Controls (`TST-TRK-063.7`, `TranslationParityTest.kt`)*:
  • Verify 100% key and format parity across `strings_radar.xml` in all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify compact phrasing and single-line rendering enforcement on `SegmentedButton` labels across all 9 locales, asserting non-ellipsized fit on standard screen widths.
  8. *Clean-Room Targeted Test Suite Execution (`TST-TRK-063.8`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.audio.radar.*`, `com.atrainingtracker.trainingtracker.settings.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-CON-030: **Garmin Varia BLE V2 High-Resolution Radar Stream Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-030` (`REQ-CON-030.1`..`REQ-CON-030.8`)
- **Component**: `BTLERadarDevice.java`, `MyBTLEDevice.java`, `DeviceType.java`, `ActivityType.java`, `RadarTarget.kt`, `RadarWindowAggregator.kt`, `RadarTrafficSimulator.kt`, `SensorType.java`, `BluetoothConstants.java`, `strings_radar.xml`
- **Jira**: `ATT-5095`, `ATT-6706`, `ATT-7542`, `ATT-7659`
- **Test Description**:
  1. *V2 Handshake Choreography, MTU Negotiation & Notification Routing Tests (`TST-CON-030.1`, `BTLERadarDeviceTest.kt`, `MyBTLEDeviceTest.kt`)*:
  • Verify Battery 0x2a19 read and CCCD notification subscription execute prior to V2 characteristic 6a4e3204 setup without touching V1 prematurely.
  • Verify request of MTU 247 enqueued in `mGattQueue`, bonding verification, serialized GATT handshake execution on peripheral discovery of characteristic `6a4e3204`, and end-to-end routing of V2 payloads through `isMeasurementCharacteristic`.
  2. *Automatic Legacy Fallback Invariant Tests (`TST-CON-030.2`, `BTLERadarDeviceTest.kt`)*:
  • Verify immediate fallback to V1 characteristic `6a4e3203` when `6a4e3204` is absent, when device is unbonded, when CCCD descriptor write fails with non-GATT_SUCCESS status, or when the 1500 ms V2 silence timer expires without notifications.
  • Verify fallback state resets cleanly upon device disconnect.
  3. *V2 Packet Parsing & Domain Mapping Tests (`TST-CON-030.3`, `BTLERadarDeviceTest.kt`)*:
  • Verify decoding of `2 + 9*N` V2 multi-target packets, checking 24-bit packed range word unpacking (11-bit lateral offset, 13-bit longitudinal range up to 175 m), negative `speedY` closing speed derivation, target classification (`LARGE`, `MODERATE`, `FAINT`), and 2-byte idle heartbeats (`02 00` / `02 90`) and 5/6-byte device-status frames (`0x0004`).
  4. *Lateral Passing Clearance & Large Vehicle Aggregation Tests (`TST-CON-030.4`, `RadarWindowAggregatorTest.kt`)*:
  • Verify latching of minimum lateral passing clearance when $y \le 3.0\text{ m}$ and cumulative tracking of large vehicle counts.
  5. *SensorType Enum, DeviceType Mapping & Unit Formatter Tests (`TST-CON-030.5`, `SensorTypeTest.kt`, `DeviceTypeTest.kt`)*:
  • Verify `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` sensor definitions, `DeviceType.getSensorDeviceTypes()` mappings to `BIKE_RADAR`, and formatting parity.
  6. *Traffic Simulator V2 Trajectory Tests (`TST-CON-030.6`, `RadarTrafficSimulatorTest.kt`)*:
  • Verify generation of simulated V2 frames with lateral offsets and vehicle classifications.
  7. *9-Language Localization Parity Test (`TST-CON-030.7`, `TranslationParityTest.kt`)*:
  • Verify 100% key and format parity across `strings_radar.xml` in all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  8. *Clean-Room Targeted Test Suite Execution (`TST-CON-030.8`)*:
  • Execute targeted package tests (`com.atrainingtracker.banalservice.devices.bluetooth_le.*`, `com.atrainingtracker.banalservice.devices.radar.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.
  9. *Developer BLE V1-Only Protocol Override Tests (`TST-CON-030.9`, `BTLERadarDeviceTest.kt`, `DeveloperMenuScreenTest.kt`)*:
  • Verify that when `isRadarV1OnlyOverrideEnabled` is true in `BuildConfig.DEBUG`, `BTLERadarDevice.configureServices()` directly invokes `fallbackToV1()` bypassing V2 GATT discovery/setup.
  • Verify that when `BuildConfig.DEBUG` is false or override is disabled, normal V2 discovery and negotiation proceed.

### TST-CON-031: **BLE Radar Tail Light Mode Control Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-031` (`REQ-CON-031.1`..`REQ-CON-031.8`)
- **Component**: `BTLERadarDevice.java`, `MyBTLEDevice.java`, `RadarLightController.kt`, `RadarLightMode.kt`, `RadarEdgeOverlay.kt`, `RadarSettingsSection.kt`, `TuningPreferencesDataStore.kt`, `TrackerService.java`, `RadarLightQuickSwitchSheet.kt`, `EditDeviceDialog.kt`, `strings_radar.xml`, `tools/ble_radar_simulator.py`, `MyBTLEDeviceQueueTest.kt`, `tools/test_ble_radar_simulator.py`
- **Jira**: `ATT-5102`, `ATT-6707`, `ATT-7545`, `ATT-7540`, `ATT-7538`, `ATT-7991`, `ATT-7737`
- **Test Description**:
  1. *Light Characteristic Discovery, `6a4e2f11` CCCD Handshake, Model Number Query & Queue Routing Tests (`TST-CON-031.1`, `BTLERadarDeviceTest.kt`)*:
  • Verify discovery of RearVue 820 Control Service `6a4e2f00` (write+indicate `6a4e2f11`, notify `6a4e2f14`), enabling `setCharacteristicNotification(controlWriteChar, true)` and queuing `0x2902` CCCD (`ENABLE_INDICATION_VALUE` / `ENABLE_NOTIFICATION_VALUE`) on **`6a4e2f11`** strictly *before* any initial `setLightMode()` control write, alongside CCCD subscription on `6a4e2f14`.
  • Verify `onOtherCharacteristicUpdate()` decodes/handles indication responses on `6a4e2f11`, transparent legacy fallback to `6a4e3202` on service `6a4e3200`, read of Device Information `0x2A24` (Model Number string), StVZO detection (`isStvzo = true` for `010-03180-10`, `RTL516`), active light mode re-dispatch on mid-ride reconnect after CCCD queueing, and serialized write queueing.
  2. *Property-Aware Write Type, Write Error Propagation, Slot Decoding, StVZO Pruning & Gated Mutation Tests (`TST-CON-031.2`, `BTLERadarDeviceTest.kt`, `MyBTLEDeviceQueueTest.kt`, `RadarLightControllerTest.kt`)*:
  • Verify dispatch of 4-byte mode commands `06 09 01 TT` (`0x11` SOLID, `0x12` PELOTON, `0x13` DAY_FLASH, `0x14` NIGHT_FLASH, `0x1F` OFF) on `6a4e2f11` using `WRITE_TYPE_DEFAULT` whenever `PROPERTY_WRITE` is supported (`(WRITE, INDICATE)`), while `MyBTLEDevice.WriteCharacteristicOp` and `BTLERadarDevice` select `WRITE_TYPE_NO_RESPONSE` strictly when `PROPERTY_WRITE == 0 && (properties & PROPERTY_WRITE_NO_RESPONSE) != 0` (preserving legacy `6a4e3202`).
  • Verify `MyBTLEDevice.onCharacteristicWrite()` propagates `(gatt, characteristic, status)` to `BTLERadarDevice.onCharacteristicWriteCompleted()`: non-`GATT_SUCCESS` status logs error diagnostics, handles `GATT_INSUFFICIENT_AUTHENTICATION` bonding check/request, and reconciles/rolls back optimistic `mLastConfiguredLightMode` / `RadarLightController` state cleanly rather than silently masking failure until `6a4e2f14` snaps back to `OFF`.
  • Verify legacy 1-byte command dispatch on `6a4e3202`, decoding of 4-byte slot notifications `01 [slot] ff [type]` on `6a4e2f14` updating `RadarLightController`, flashing mode pruning under StVZO, and verification that `_currentMode` and `RadarStateRepository` mutate strictly when `dispatched == true`.
  3. *Cockpit Quick-Switch Sheet Automatik & Physical Mode Selection Tests (`TST-CON-031.3`, `RadarLightQuickSwitchSheetTest.kt`)*:
  • Verify modal sheet rendering with `Automatik` as the first selectable option followed by available physical modes, live active state indication for both `isAutoMode` and `currentMode`, proper callback invocation clearing manual override on `Automatik` selection and persisting `radarLightDefaultMode` on explicit physical mode selection, non-interactive badge behavior on devices lacking light characteristic, styling using `MaterialTheme.colorScheme.outline`, and `@Preview` coverage.
  4. *Workout Lifecycle Auto-Switch Automation & Pause Safety Tests (`TST-CON-031.4`, `RadarLightControllerTest.kt`, `TrackerServiceTest.kt`)*:
  • Verify automatic activation of persisted user lighting selection (`AUTO` starts `SmartRadarLightEngine`; explicit mode dispatches directly) on tracker start/resume (`START_NORMAL`, `RESUME_BY_USER`, `RESUME_AUTOMATICALLY`), preservation of tail light illumination across workout pause, and synchronous dispatch of `OFF` on explicit stop (`handleActionStop`) without erasing or overwriting persisted user preferences for future workouts.
  5. *Unified Persistence Migration, Expert Settings Retirement & EditDeviceDialog UI Consistency Tests (`TST-CON-031.5`, `TuningPreferencesDataStoreRadarLightControlModeTest.kt`, `RadarSettingsSectionTest.kt`, `RadarLightControllerTest.kt`, `EditDeviceDialogTest.kt`)*:
  • Verify DataStore migration from legacy `RadarLightControlMode` to unified `isAutoMode: Boolean` + `radarLightDefaultMode: RadarLightMode` with fallback defaults (`true` and `DAY_FLASH`).
  • Verify retirement of redundant 3-way mode segmented button and default mode dropdown in `RadarSettingsSection.kt`, and preservation of `radarBrakeLightEnabled` toggle.
  • Verify `EditDeviceDialog` live tail light controls: conditional visibility gating (`BIKE_RADAR`, connected, `isLightControlAvailable`; offline `BIKE_RADAR` visibility extended by `TST-CON-048`), inclusion of `Automatik` as first option alongside physical modes, immediate dispatch via `RadarLightController`, draft state decoupling, StVZO option restriction, and UI binding consistency with `RadarLightController.currentMode` / `isAutoMode` on dialog close and reopen.
  6. *Traffic Simulator & BLE Peripheral Simulator Fidelity Tests (`TST-CON-031.6`, `RadarTrafficSimulatorTest.kt`, `tools/test_ble_radar_simulator.py`)*:
  • Verify light command handling and simulated state reflections in `RadarTrafficSimulator`.
  • Verify `tools/ble_radar_simulator.py` (`RadarControlWriteCharacteristic` on `6a4e2f11`) exposes `['write', 'indicate']` properties (never `write-without-response`) and rejects writes when `6a4e2f11` CCCD indications are not subscribed.
  7. *9-Language Localization Parity Test (`TST-CON-031.7`, `TranslationParityTest.kt`)*:
  • Verify 100% key and format parity for light mode, Automatik, and status string resources across all 9 supported locales.
  8. *Clean-Room Targeted Test Suite Execution (`TST-CON-031.8`)*:
  • Execute targeted package tests asserting 100% pass rate.
  9. *Debounced Mode Stepping & Reactive State Separation Tests (`TST-CON-031.9`, `RadarLightControllerTest.kt`)*:
  • Verify `cycleNextMode()` sequence: `AUTO` -> `OFF` -> `PELOTON` -> `SOLID` -> `NIGHT_FLASH` -> `DAY_FLASH` -> `AUTO` on non-StVZO hardware, and `AUTO` -> `OFF` -> `SOLID` -> `AUTO` on StVZO hardware.
  • Verify immediate UI-observable target mode updates on rapid successive taps, with ~400 ms debounce window coalescing outbound commands into a single physical dispatch.
  • Verify distinct reactive emission of `isAutoMode` and `currentMode` `StateFlow`s during auto-escalation and manual override.

### TST-CON-032: **ANT+ Cycling Rearview Radar Profile (Device Type 0x28) Driver & Target Demuxing Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-032` (`REQ-CON-032.1`..`REQ-CON-032.6`)
- **Component**: `ANTBikeRadarDevice.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`, `DeviceManager.java`, `DeviceType.java`, `RadarStateRepository.kt`, `RadarState.kt`, `RadarTarget.kt`, `app/build.gradle`
- **Jira**: `ATT-5084`, `ATT-7108`
- **Test Description**:
  1. *ANT+ Channel Acquisition, Predefined Network Slot 1 & Configuration Tests (`TST-CON-032.1`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify `AntService` binding and channel acquisition on Predefined Network slot 1 (`ANT_PLUS`).
  • Verify channel configuration parameters: Device Type 40 (`0x28`), Channel Type `BIDIRECTIONAL_SLAVE`, RF Frequency 57 (2457 MHz), Channel Period 4084 counts.
  2. *Data Page 0x30 & 0x31 Multi-Target Bit Demuxing Tests (`TST-CON-032.2`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify bit unpacking for Page 0x30 (Targets 1–4) and Page 0x31 (Targets 5–8):
    - 2-bit Threat Level (`0 = None`, `1 = Approaching`, `2 = Fast Approaching`).
    - 2-bit Threat Side (`0 = Behind`, `1 = Right`, `2 = Left`).
    - 6-bit Range scaled by $3.125\text{ m/bit}$ ($0..196.875\text{ m}$).
    - 4-bit Closing Speed scaled by $3.04\text{ m/s/bit}$ ($0..45.6\text{ m/s}$).
  • Verify target assembly into `RadarTarget` and emission to `RadarStateRepository`.
  3. *Data Page 0x01 System Status & Common Pages 0x50/0x51/0x52 Tests (`TST-CON-032.3`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify decoding of Data Page `0x01` (Radar System State: Normal, Limited, Error) and mapping to radar state.
  • Verify common pages decoding: Page `0x50` (Manufacturer ID), Page `0x51` (Model Number), Page `0x52` (Battery Status & Voltage).
  4. *Wildcard Discovery & Device Number Extraction Tests (`TST-CON-032.4`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`)*:
  • Verify wildcard `AntChannel` initialization (`DeviceNumber = 0`, `DeviceType = 40`) and extended data parsing (device number extraction and RSSI) when searching for `BIKE_RADAR`.
  • Verify `DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)` includes `BIKE_RADAR`.
  5. *Starvation Watchdog & Hardware Preemption Tests (`TST-CON-032.5`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify 3.5s timeout triggers transition to `DISCONNECTED_ERROR` and resets scalar sensor latches.
  • Verify physical frame arrival invokes `RadarTrafficSimulator.onPhysicalHardwareConnected()`.
  6. *Targeted Radar Test Suite Execution (`TST-CON-032.6`)*:
  • Run all radar unit and integration tests under `com.atrainingtracker.banalservice.devices.antplus.*` and `com.atrainingtracker.banalservice.devices.radar.*` asserting 100% pass rate.

### TST-CON-033: **ANT+ Bike Lights Profile (Device Type 0x23) Companion Channel, Handshake & Mode Control Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-033` (`REQ-CON-033.1`..`REQ-CON-033.5`)
- **Component**: `ANTBikeLightDevice.kt`, `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt`, `RadarLightController.kt`, `RadarLightDeviceClient.kt`, `RadarLightMode.kt`
- **Jira**: `ATT-5103`, `ATT-7131`
- **Test Description**:
  1. *Channel Acquisition & Companion Binding Tests (`TST-CON-033.1`, `ANTBikeLightDeviceTest.kt`)*:
  • Verify `AntChannelHelper` constants: `BIKE_LIGHT_DEVICE_TYPE = 35`, `BIKE_LIGHT_RF_FREQUENCY = 57`, `BIKE_LIGHT_CHANNEL_PERIOD = 4084`.
  • Verify companion channel acquisition on Predefined Network slot 1 (`ANT_PLUS`), channel configuration as `BIDIRECTIONAL_SLAVE`, and clean teardown without creating duplicate database rows.
  2. *Data Page 0x01 & 0x02 Broadcast Decoding Tests (`TST-CON-033.2`, `ANTBikeLightDeviceTest.kt`)*:
  • Verify unpacking of Page `0x01` (Main Light State): Light Index, Light Type (Tail Light = 2), current mode, and battery status.
  • Verify unpacking of Page `0x02` (Capabilities): supported modes bitmask and propagation to `RadarStateRepository`.
  3. *Stateful 3-Step Handshake Tests (`TST-CON-033.3`, `ANTBikeLightDeviceTest.kt`)*:
  • Verify Page `0x21` (Connect Command) transmission when `Light Index == 0`, assigning Light Index 1 and phone controller ID.
  • Verify Page `0x22` (Mode Command) transmission with sequence incrementing only after `Light Index == 1` is confirmed.
  • Verify Page `0x20` (Disconnect Command) transmission upon release.
  4. *Unified Controller Abstraction Tests (`TST-CON-033.4`, `RadarLightControllerTest.kt`)*:
  • Verify `RadarLightController` accepts `RadarLightDeviceClient` implementations (both BLE and ANT+).
  • Verify mode transitions, workout auto-switch, and state updates dispatch to `ANTBikeLightDevice` identically to BLE.
  5. *Fail-Safe Isolation & Radar Telemetry Non-Interference Tests (`TST-CON-033.5`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that companion light channel drop or timeout leaves ANT+ radar target tracking (Pages `0x30`/`0x31`) completely unaffected.
  6. *Channel Event Filtering & Wildcard Safe Fallback Tests (`TST-CON-033.6`, `ANTBikeLightDeviceTest.kt`)*:
  • Verify non-timeout channel events (e.g. `EventCode.TX`, `TRANSFER_RX_FAILED`, `TRANSFER_TX_COMPLETED`) do not trigger wildcard fallback.
  • Verify that on `EventCode.RX_SEARCH_TIMEOUT` or `EventCode.CHANNEL_CLOSED`, the active channel is closed before `setChannelId(ChannelId(0, 35, 0))` and reopened.
  7. *Group Ride Disambiguation & Verified Mode Byte Mapping Tests (`TST-CON-033.7`, `ANTBikeLightDeviceTest.kt`)*:
  • Verify that during wildcard search, broadcast packets with non-matching `deviceNumber` or non-matching `manufacturerId` (Page `0x50`) are rejected and do not trigger `sendConnectCommand()`.
  • Verify verified mode byte mapping: `OFF=0x00`, `SOLID=0x01`, `PELOTON=0x05`, `NIGHT_FLASH=0x06`, `DAY_FLASH=0x07`.
  • Verify that `sendLightSettingsCommand()` does not synchronously mark mode applied; applied mode is updated upon `EventCode.TRANSFER_TX_COMPLETED` or Page `0x01` decode.

### TST-CON-034: **Environmental & Traffic-Adaptive Smart Tail Light Automation Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-034` (`REQ-CON-034.1`..`REQ-CON-034.6`)
- **Component**: `SmartRadarLightEngine.kt`, `SolarElevationCalculator.kt`, `RadarLightController.kt`, `RadarLightMode.kt`, `RadarStateRepository.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Jira**: `ATT-5104`, `ATT-7145`
- **Test Description**:
  1. *Pocket Occlusion & Screen-Off Gating Tests (`TST-CON-034.1`, `SmartRadarLightEngineTest.kt`)*:
  • Verify that $0\text{ lx}$ illuminance is ignored when screen is OFF (`isInteractive == false`) or proximity is `NEAR`.
  • Verify that solar elevation determines environmental profile when ambient sensor is gated.
  2. *Solar Elevation Calculation Tests (`TST-CON-034.2`, `SolarElevationCalculatorTest.kt`)*:
  • Verify solar elevation angle calculation across noon, sunset, civil twilight ($-3^\circ$), and midnight.
  • Verify classification into `DAY` vs. `DUSK_NIGHT`.
  3. *Tunnel Entry & Exit Hysteresis Tests (`TST-CON-034.3`, `SmartRadarLightEngineTest.kt`)*:
  • Verify sudden illuminance drop triggers tunnel mode within $2\text{ s}$.
  • Verify $10\text{ s}$ recovery hysteresis before returning to daytime mode upon illuminance recovery.
  4. *3-Stage Threat Escalation & Anti-Glare Cap Tests (`TST-CON-034.4`, `SmartRadarLightEngineTest.kt`)*:
  • Verify night escalation: `PELOTON` (Stage 0) $\rightarrow$ `SOLID` (Stage 1) $\rightarrow$ `NIGHT_FLASH` (Stage 2).
  • Assert `DAY_FLASH` is strictly never emitted during night/dusk profile.
  5. *Command Rate-Limiting & Post-Pass Cooldown Tests (`TST-CON-034.5`, `SmartRadarLightEngineTest.kt`)*:
  • Verify mode command dispatch is rate-limited to at most one call per $\ge 1.5\text{ s}$.
  • Verify $3\text{--}5\text{ s}$ post-threat cooldown before returning to Stage 0.
  6. *Settings & 9-Language Localization Parity Tests (`TST-CON-034.6`, `TranslationParityTest.kt`)*:
  • Verify `radarSmartLightAutomation` DataStore persistence and UI toggle rendering.
  • Verify 100% key parity and matching specifiers across all 9 locales in `strings_radar.xml`.

### TST-CON-035: **Smartphone IMU/GPS Deceleration Brake-Light Trigger Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-035` (`REQ-CON-035.1`..`REQ-CON-035.7`)
- **Component**: `SmartBrakeLightDetector.kt`, `RadarLightController.kt`, `RadarLightMode.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Jira**: `ATT-5121`
- **Test Description**:
  1. *Deceleration Detection & Duration Gating Tests (`TST-CON-035.1`, `SmartBrakeLightDetectorTest.kt`)*:
  • Verify that sustained deceleration $a \le -2.0\text{ m/s}^2$ for $\ge 250\text{ ms}$ with speed $v \ge 2.5\text{ m/s}$ and speed derivative $dv/dt \le 0$ triggers a Brake Event.
  • Verify that positive acceleration ($dv/dt > 0$) suppresses false brake activations.
  • Verify that transient deceleration lasting $< 250\text{ ms}$ does not trigger a Brake Event.
  • Verify that deceleration below minimum speed ($v < 2.5\text{ m/s}$) does not trigger a Brake Event.
  2. *Vertical Shock & Pothole Rejection Tests (`TST-CON-035.2`, `SmartBrakeLightDetectorTest.kt`)*:
  • Simulate high vertical shock acceleration ($|a_v| > 6.0\text{ m/s}^2$) at constant velocity ($v = 7.0\text{ m/s}$, $dv/dt = 0$) and assert zero false brake activations.
  3. *Controller Immediate Override & 2.0s Hold Tests (`TST-CON-035.3`, `RadarLightControllerTest.kt`)*:
  • Verify that confirmed Brake Event immediately overrides `SmartRadarLightEngine` rate limits and dispatches brake mode.
  • Verify that brake mode persists throughout deceleration plus $2.0\text{ s}$ post-brake hold window.
  • Verify that previous active mode (`preBrakeMode`, e.g. `PELOTON` or `OFF`) is restored upon hold expiration.
  4. *StVZO & Night Anti-Glare Mode Clamping Tests (`TST-CON-035.4`, `RadarLightControllerTest.kt`)*:
  • Verify that when `isStvzo == true`, brake mode is strictly clamped to `RadarLightMode.SOLID` and never emits `DAY_FLASH`.
  • Verify that in dark/night/tunnel environments or when pre-brake mode is night/peloton, brake mode is strictly clamped to `RadarLightMode.SOLID` (25 lm), never emitting `DAY_FLASH` (100 lm).
  5. *RearVue 820 Hardware Auto-Bypass Tests (`TST-CON-035.5`, `RadarLightControllerTest.kt`)*:
  • Verify that connecting a RearVue 820 device (`010-03180-00`/`010-03180-10`) automatically sets brake light auto-bypass to active.
  • Verify that hardware auto-bypass remains active across workout stop and sequential workout start while the device remains connected, preventing IMU sensor listener registration (`ATT-7514`).
  6. *Settings & 9-Language Localization Parity Tests (`TST-CON-035.6`, `TranslationParityTest.kt`)*:
  • Verify `radarBrakeLightEnabled` DataStore persistence, default value, and UI toggle in `RadarSettingsSection.kt`.
  • Verify 100% key parity and format specifiers across all 9 locales in `strings_radar.xml`.
  7. *Lifecycle Management & Sensor Conservation Tests (`TST-CON-035.7`, `SmartBrakeLightDetectorTest.kt`, `RadarLightControllerTest.kt`)*:
  • Verify `SensorEventListener` registration with `SensorManager` on workout start/resume and clean unregistration on pause/stop.
  • Verify that pausing a workout while braking reverts the light mode to `preBrakeMode` (including `OFF`), and that stopping a workout cancels pending brake holds and prevents post-workout light resuscitation (`ATT-7514`).

### TST-CON-036: **Resilient Mid-Workout BLE Peripheral Reconnection, Scan-Quota Throttling & Hardware Auto-Connect Verification Suite** [Verified]
- **Requirement(s)**: `REQ-CON-036` (`REQ-CON-036.1`..`REQ-CON-036.4`)
- **Component**: `MyBTLEDevice.java`, `DeviceManager.java`
- **Jira**: `ATT-7755`
- **Test Description**:
  1. *Mid-Workout Disconnect & Consecutive Timeout Recovery (`TST-CON-036.1`, `BLEReconnectRobustnessTest.kt`)*:
  • Verify that when a session-connected peripheral experiences an unexpected GATT disconnection (`STATE_DISCONNECTED`), `mReconnectRunnable` is scheduled with exponential backoff.
  • Verify that upon `mSearchTimeoutRunnable` expiring with zero scan results, the device does NOT abandon search, but reschedules `mReconnectRunnable` with doubled backoff delay (capped at 16s) and maintains auto-connect.
  • Verify that when peripheral advertisement or connection resume occurs, services are rediscovered and sensors re-registered cleanly.
  2. *Startup Offline Sensor Search Bounding Preserved (`TST-CON-036.2`, `BLEReconnectRobustnessTest.kt`)*:
  • Verify that for a never-connected device during initial app search, `mSearchTimeoutRunnable` expiring invokes `notifyStopSearching(false)` without rescheduling infinite reconnect timers, preserving `DeviceManager`'s `mMyRemoteDeviceTries` limit and battery conservation.
  3. *Sliding-Window Scan Quota Throttling & Fallback (`TST-CON-036.3`, `BLEReconnectRobustnessTest.kt`)*:
  • Verify that scan rate throttling restricts BLE scanner starts to <= 4 per 30-second window.
  • Verify that when scan quota is exhausted or `onScanFailed` occurs, connection falls back immediately to direct `connectGatt` without stranding the device in `SEARCHING`.
  4. *Tier 1 Targeted Package Regression (`TST-CON-036.4`)*:
  • Verify 100% test pass rate across `com.atrainingtracker.banalservice.devices.bluetooth_le.*` and `com.atrainingtracker.trainingtracker.build.*`.

### TST-CON-037: **ANT+ Bike Radar Cold-Start Channel Acquisition and Race Resilience Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-037` (`REQ-CON-037.1`..`REQ-CON-037.4`)
- **Component**: `AntChannelHelperTest.kt`, `ANTBikeRadarDeviceTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`
- **Jira**: `ATT-7546`
- **Test Description**:
  1. *Immediate Synchronous Channel Acquisition Test (`TST-CON-037.1`, `AntChannelHelperTest.kt`)*:
  • Verify that when channels are already available, `acquireAntPlusChannelAsync` acquires the channel immediately on Predefined Network slot 1 without registering a broadcast receiver.
  2. *Cold-Start Broadcast-Triggered Channel Acquisition Test (`TST-CON-037.2`, `AntChannelHelperTest.kt`)*:
  • Verify that when initial acquisition throws `SERVICE_INITIALIZING` or `NO_ADAPTERS_EXIST` (or `numChannelsAvailable == 0`), a receiver for `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` is registered with `ContextCompat.RECEIVER_EXPORTED`.
  • Verify that upon receiving `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` with `NUM_CHANNELS_AVAILABLE > 0`, the channel is successfully acquired and delivered to `onChannelAcquired(channel)`.
  3. *Bounded Retry Backoff Fallback Test (`TST-CON-037.3`, `AntChannelHelperTest.kt`)*:
  • Verify that if the broadcast is delayed, scheduled retry timer triggers successful acquisition once channels become available.
  4. *Acquisition Timeout & User-Visible Failure Test (`TST-CON-037.4`, `AntChannelHelperTest.kt`)*:
  • Verify that after 10 seconds of persistent channel unavailability, `onAcquisitionFailed` is invoked and the receiver is unregistered.
  5. *Teardown & Unregistration Contract Tests (`TST-CON-037.5`, `AntChannelHelperTest.kt`, `ANTBikeRadarDeviceTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`)*:
  • Verify calling `cancel()` unregisters the broadcast receiver and removes all pending handler callbacks immediately.
  • Verify `ANTSearchForNewDevicesEngineMultiDeviceSearch.stopAsyncSearch()` cancels pending acquisition.
  • Verify `ANTBikeRadarDevice.shutDown()` and `onServiceDisconnected()` cancels pending acquisition.

### TST-CON-038: **ANT+ Bike Radar Continuous Discovery, Channel Event Re-Opening & ExtendedData Parsing Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-038` (`REQ-CON-038.1`..`REQ-CON-038.5`)
- **Component**: `AntChannelHelperTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`, `ANTBikeRadarDeviceTest.kt`
- **Jira**: `ATT-7547`
- **Test Description**:
  1. *Discovery Channel Event Re-Open on 25s Timeout Test (`TST-CON-038.1`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`)*:
  • Verify that delivering `CHANNEL_EVENT` with `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED` while radar search is active triggers `channel.open()`.
  • Verify that when radar search is stopped (`mIsRadarSearchActive == false`), `CHANNEL_EVENT` does NOT re-open the channel.
  2. *Tracking Channel Search Re-Open on Timeout Test (`TST-CON-038.2`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that delivering `CHANNEL_EVENT` with `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED` while `ANTBikeRadarDevice.isSearching == true` triggers `channel.open()`.
  • Verify that when `isSearching == false` (device already receiving data), timeout does NOT re-open the channel.
  3. *Background Scanning Assignment & Fallback Test (`TST-CON-038.3`, `AntChannelHelperTest.kt`)*:
  • Verify `AntChannelHelper.assignScanChannel` assigns `ExtendedAssignment(BACKGROUND_SCANNING)` when adapter capabilities report support.
  • Verify fallback to standard `BIDIRECTIONAL_SLAVE` assignment when background scanning is unsupported or assignment fails.
  4. *Safe LibConfig Exception Tolerance Test (`TST-CON-038.4`, `AntChannelHelperTest.kt`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that when `setAdapterWideLibConfig()` throws `AntCommandFailedException`, `AntChannelHelper.safeSetAdapterWideLibConfig` catches the exception without crashing and logs a warning.
  5. *DataMessage ExtendedData & RSSI Extraction Test (`TST-CON-038.5`, `AntChannelHelperTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`)*:
  • Verify that both `AcknowledgedDataMessage` and `BroadcastDataMessage` extract device number and real RSSI values when extended data is present.
  • Verify that missing RSSI defaults to `-1` safely.

### TST-CON-039: **ANT+ Bike Radar Shutdown Cleanup, Thread Serialization & Channel-Close Reconnect Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-039` (`REQ-CON-039.1`..`REQ-CON-039.4`)
- **Component**: `ANTBikeRadarDevice.kt`, `ANTBikeRadarDeviceTest.kt`
- **Jira**: `ATT-7576`
- **Test Description**:
  1. *Shutdown State Cleanup & Repository Disconnect Test (`TST-CON-039.1`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that calling `shutDown()` on `ANTBikeRadarDevice` with active targets clears `activeTargets` and transitions `RadarStateRepository` to `RadarConnectionState.DISCONNECTED` with empty targets.
  2. *Main Thread Ingestion & Concurrency Serialization Test (`TST-CON-039.2`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that incoming `BROADCAST_DATA` and `ACKNOWLEDGED_DATA` messages clone the payload and dispatch `decodeBroadcastData` onto `handler` (`Looper.getMainLooper()`).
  • Verify that `onChannelDeath()` posts `onWatchdogTimeout()` to `handler`.
  3. *Tracking Mode Channel-Close Reconnect Test (`TST-CON-039.3`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that delivering `CHANNEL_EVENT` with `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED` triggers `antChannel.open()` even when `isSearching == false` (connected/tracking).
  4. *Post-Shutdown Message Ingestion Suppression Test (`TST-CON-039.4`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that late-arriving messages or channel events received after `shutDown()` do not re-open the channel or mutate `RadarStateRepository`.

### TST-CON-040: **ANT+ Bike Radar Track Identity Continuity, 0-Bin Ingestion & Rolling Window Latch Preservation Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-040` (`REQ-CON-040.1`..`REQ-CON-040.4`)
- **Component**: `ANTBikeRadarDevice.kt`, `RadarWindowAggregator.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDeviceTest.kt`, `RadarWindowAggregatorTest.kt`, `BTLERadarDeviceTest.kt`
- **Jira**: `ATT-7578`
- **Test Description**:
  1. *ANT+ Track Stability Across Slot Shifts Test (`TST-CON-040.1`, `ANTBikeRadarDeviceTest.kt`)*:
  • Given two approaching vehicles (Car A at 5m slot 0, Car B at 15m slot 1) decoded by `ANTBikeRadarDevice`, when Car A passes and drops out and Car B moves to slot 0 at 12m in the subsequent frame, verify that Car B preserves its stable `trackId` and `RadarWindowAggregator` increments `cumulativeVehicleCount` by exactly 1.
  2. *0-Bin Target Ingestion and Lateral Latch Test (`TST-CON-040.2`, `ANTBikeRadarDeviceTest.kt`)*:
  • Given an incoming ANT+ Page 0x30 packet with `threatLevelBits > 0` and `rawRange = 0`, verify that `ANTBikeRadarDevice` decodes a valid `RadarTarget` with `rangeMeters = 0.0f` and latches lateral passing clearance $\le 3.0\text{ m}$.
  3. *Rolling Window Latch Preservation Test (`TST-CON-040.3`, `RadarWindowAggregatorTest.kt`)*:
  • Given a target that exits tracking with passing clearance, verify that multiple consecutive calls to `sampleAndResetWindow()` at 8 Hz rate within 1,000 ms retain `passingDistanceMeters` non-null, and that `passingDistanceMeters` resets to `null` only after 1,000 ms expires.
  4. *1 Hz Driver Gating Test (`TST-CON-040.4`, `ANTBikeRadarDeviceTest.kt`, `BTLERadarDeviceTest.kt`)*:
  • Given rapid 8 Hz packet updates delivered to `ANTBikeRadarDevice` and `BTLERadarDevice`, verify that `sampleAndResetWindow()` and scalar sensor `.newValue()` updates occur on a 1-second cadence rather than per-frame.

### TST-CON-041: **Concurrent Radar Source Arbitration & Multi-Transport Decoupling Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-041` (`REQ-CON-041.1`..`REQ-CON-041.4`)
- **Component**: `RadarState.kt`, `RadarStateRepository.kt`, `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`, `RadarTrafficSimulator.kt`, `RadarStateRepositoryTest.kt`, `ANTBikeRadarDeviceTest.kt`, `BTLERadarDeviceTest.kt`
- **Jira**: `ATT-7575`
- **Test Description**:
  1. *Prioritized Source Selection Test (`TST-CON-041.1`, `RadarStateRepositoryTest.kt`)*:
  • Given `ANT_PLUS` publishes `CONNECTED` with targets and `BLE` subsequently publishes `CONNECTED` with targets, verify that `BLE` preempts `ANT_PLUS` and `RadarStateRepository.radarState` emits `BLE` targets exclusively.
  • Given both sources streaming, verify secondary `ANT_PLUS` updates do not interleave or overwrite `radarState`.
  2. *Secondary Watchdog Immunity Test (`TST-CON-041.2`, `RadarStateRepositoryTest.kt`)*:
  • While `BLE` is streaming (`CONNECTED`), deliver a `DISCONNECTED_ERROR` update from `ANT_PLUS`.
  • Verify `RadarStateRepository.radarState` remains in `CONNECTED` with `BLE` targets and does not transition to `DISCONNECTED_ERROR`.
  3. *Primary Disconnect Failover Test (`TST-CON-041.3`, `RadarStateRepositoryTest.kt`)*:
  • While `BLE` is primary and `ANT_PLUS` is connected, deliver `DISCONNECTED_ERROR` or `DISCONNECTED` from `BLE`.
  • Verify `RadarStateRepository` fails over immediately to `ANT_PLUS`, and `radarState` transitions to `ANT_PLUS` targets.
  4. *Driver isReceivingData Scoping Test (`TST-CON-041.4`, `ANTBikeRadarDeviceTest.kt`)*:
  • When only `BLE` is connected and `ANT_PLUS` has not received data, verify `ANTBikeRadarDevice.isReceivingData()` returns `false`.
  • When `ANT_PLUS` receives data, verify `ANTBikeRadarDevice.isReceivingData()` returns `true`.

### TST-CON-042: **Radar Application State Preservation During Preemption & Hardware Packet Ingestion Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-042` (`REQ-CON-042.1`..`REQ-CON-042.3`)
- **Component**: `RadarTrafficSimulator.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarStateRepository.kt`, `RadarTrafficSimulatorTest.kt`, `BTLERadarDeviceTest.kt`, `ANTBikeRadarDeviceTest.kt`
- **Jira**: `ATT-7574`
- **Test Description**:
  1. *Physical Telemetry Packet Application State Preservation (`TST-CON-042.1`, `BTLERadarDeviceTest.kt`, `ANTBikeRadarDeviceTest.kt`)*:
  • Given `RadarStateRepository` with an active non-default `lightMode` (e.g. `DAY_FLASH`) and active `audioSnoozeState` (e.g. `SNOOZE_15M`),
  • When real BLE packets (`processTargets()`) or ANT+ packets (`parseTargetsPage()` / `publishTargets()`) arrive,
  • Verify that `RadarStateRepository.getState()` continues to report the active `lightMode` and `audioSnoozeState` without being wiped to `OFF` or `INACTIVE`.
  2. *Simulator Preemption State Preservation (`TST-CON-042.2`, `RadarTrafficSimulatorTest.kt`)*:
  • Given `RadarTrafficSimulator` actively running a simulation scenario (`isSimulating == true`),
  • When `onPhysicalHardwareConnected()` is invoked,
  • Verify that `RadarTrafficSimulator.isSimulating` transitions to `false` and the simulation job terminates immediately,
  • And verify that `RadarStateRepository.reset()` is NOT invoked, preserving any pre-existing application `lightMode` or `audioSnoozeState`.
  3. *Gated Simulator Preemption Call-Site Verification (`TST-CON-042.3`, `BTLERadarDeviceTest.kt`, `ANTBikeRadarDeviceTest.kt`)*:
  • Verify that when `RadarTrafficSimulator.isSimulating` is `false`, `onPhysicalHardwareConnected()` is bypassed upon incoming target frames.

### TST-CON-043: **Cockpit Radar Tail Light Button & Action Row Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-043` (`REQ-CON-043.1`..`REQ-CON-043.5`)
- **Component**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, `RadarLightButton.kt`, `strings_radar.xml`
- **Jira**: `ATT-7972`
- **Test Description**:
  1. *TrackingViewsDbHelper Version 21 Migration & Contract Parity (`TST-CON-043.1`, `TrackingViewsDbHelperMigrationTest.kt`)*:
  • Verify incremental upgrade from v20 to v21 adds `ShowRadarLight` column with idempotent execution.
  • Verify `onCreate()` fresh schema matches upgraded schema 100%.
  • Verify `DatabaseSchemaRegistry` reflects `DB_VERSION = 21`.
  • Verify cursor extraction maps `ShowRadarLight` symmetrically across single and batch loaders.
  2. *RadarLightButton Interaction, Live State & Auto Badge Tests (`TST-CON-043.2`, `RadarLightButtonTest.kt`)*:
  • Verify short tap invokes `RadarLightController.cycleNextMode()`.
  • Verify long press triggers `RadarLightQuickSwitchSheet` without cycling mode.
  • Verify button renders `AUTO` micro-badge when `isAutoMode` is true and reflects real-time physical mode transitions.
  • Verify button is hidden when `isLightControlAvailable` is false or tab setting is disabled.
  3. *Action Row Layout, Canonical Ordering & Responsive Budget Tests (`TST-CON-043.3`, `TrackingTabsScreenTest.kt`)*:
  • Verify canonical button ordering `[RadarLightButton, LapButton, BikeBellButton]`.
  • Verify single-row fit on 360.dp portrait viewport at 130% font scaling without text clipping.
  4. *SensorGridScreen Configuration Toggle & fabBottomClearance Tests (`TST-CON-043.4`, `SensorGridScreenTest.kt`)*:
  • Verify `ShowRadarLight` toggle in configuration mode updates view settings.
  • Verify `fabBottomClearance` expands when radar light button is visible.
  5. *9-Language Localization Parity Test (`TST-CON-043.5`, `TranslationParityTest.kt`)*:
  • Verify 100% parity of all radar light button strings across all 9 supported locales in `strings_radar.xml`.
  6. *Clean-Room Targeted Test Suite Execution (`TST-CON-043.6`)*:
  • Verify targeted regression tests pass at 100%.

### TST-CON-044: **Instance-Isolated Radar Connection State & Multi-Device Gating Test Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-044` (`REQ-CON-044.1`..`REQ-CON-044.3`)
- **Component**: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `BTLERadarDeviceTest.kt`, `ANTBikeRadarDeviceTest.kt`
- **Jira**: `ATT-8109`
- **Test Type**: Unit & Regression
- **Verification Method**: Unit
- **Procedure**:
  1. *Multi-Instance BLE Radar Isolation Test (`TST-CON-044.1`, `BTLERadarDeviceTest.kt`)*:
     • Instantiate two `BTLERadarDevice` instances (`device1` and `device2`).
     • Connect `device1` (`State.CONNECTED_WITH_SERVICE`) while leaving `device2` disconnected (`State.DISCONNECTED`).
     • Update `RadarStateRepository` to `CONNECTED` under `RadarSource.BLE`.
     • Assert `device1.isReceivingData` is `true` and `device2.isReceivingData` is `false`.
  2. *Fail-Safe Watchdog Starvation Retention Test (`TST-CON-044.2`, `BTLERadarDeviceTest.kt`)*:
     • With `device1` in `State.CONNECTED_WITH_SERVICE`, trigger watchdog timeout (`watchdogRunnable.run()`).
     • Assert `device1.isReceivingData` transitions to `false`.
  3. *Multi-Instance ANT+ Radar Isolation Test (`TST-CON-044.3`, `ANTBikeRadarDeviceTest.kt`)*:
     • Instantiate two `ANTBikeRadarDevice` instances with different ANT device numbers.
     • Feed broadcast pages to instance 1 only; assert instance 1 returns `true` and instance 2 returns `false`.
  4. *Clean-Room Targeted Test Suite Execution (`TST-CON-044.4`)*:
     • Verify all radar unit test suites pass with 100% success rate.

### TST-CON-045: **Polar Live IMU Streaming Bridge Search Timeout & GATT Contention Recovery Test Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-045` (`REQ-CON-045.1`..`REQ-CON-045.4`)
- **Component**: `PolarOnlineStreamingBridge.kt`, `PolarOnlineStreamingBridgeTest.kt`
- **Jira**: `ATT-8451`
- **Test Type**: Unit & Regression
- **Verification Method**: Unit
- **Procedure**:
  1. *Search Timeout & Queue Unblocking Test (`TST-CON-045.1`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • Instantiate `PolarOnlineStreamingBridge` with a mock `PolarBleApi` where `connectToDevice` does not emit `deviceConnected`.
     • Register a broadcast receiver for `SEARCHING_STOPPED_FOR_ONE_INTENT`.
     • Invoke `startSearching()`.
     • Advance test dispatcher/scheduler time by `TrainingApplication.getBtleSearchPeriodMs()`.
     • Assert `notifyStopSearching(false)` broadcast is received, `isSearching == false`, and `api.disconnectFromDevice(polarId)` is invoked.
  2. *Repeated Search with Existing `polarApi` Test (`TST-CON-045.2`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • With an initialized disconnected `polarApi` instance, invoke `startSearching()`.
     • Assert `api.connectToDevice` is called, search timeout is armed, and the search does not hang.
     • When already connected (`isReceivingData() == true`), invoke `startSearching()`; assert `notifyStopSearching(true)` is immediately returned without re-connecting.
  3. *Connection Success Timeout Cancellation Test (`TST-CON-045.3`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • Invoke `startSearching()`.
     • Trigger `sdkCallback.deviceConnected(polarDeviceInfo)`.
     • Assert `notifyStopSearching(true)` is broadcast and the search timeout watchdog is cancelled.
     • Advance test time beyond timeout period; assert no disconnect or `notifyStopSearching(false)` is dispatched.
  4. *Bluetooth Adapter Power Cycle & Teardown Test (`TST-CON-045.4`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • During an active search or connected stream, trigger `sdkCallback.blePowerStateChanged(false)`.
     • Assert `isConnectedFlag == false`, active streaming jobs are cancelled, search timeout is cancelled, and `notifyStopSearching(false)` is broadcast.
     • Call `shutDown()`; assert active timeout jobs, receivers, and coroutine scopes are cancelled cleanly.

### TST-CON-046: **Radar `isAutoMode` Preservation Across Telemetry Frames, Simulator & Source Arbitration Test Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-046` (`REQ-CON-046.1`..`REQ-CON-046.3`)
- **Component**: `RadarState.kt`, `RadarStateRepository.kt`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarTrafficSimulator.kt`, `RadarLightController.kt`
- **Jira**: `ATT-8455`
- **Test Type**: Unit & Integration
- **Verification Method**: Unit
- **Procedure**:
  1. *BTLERadarDevice 6-Site `isAutoMode` Preservation & Restore (`TST-CON-046.1`, `BTLERadarDeviceTest.kt`)*:
     • Given `RadarStateRepository` / `RadarLightController` with `isAutoMode == false`,
     • When exercising all 6 `BTLERadarDevice` emission paths (`startSearching`, `notifyStopSearching`, `configureServices`, `processTargets`, `mWatchdogRunnable`, `disconnectFromGatt`),
     • Assert `RadarStateRepository.getState().isAutoMode` remains `false`; then invoke `RadarLightController.setAutoMode()` and assert `isAutoMode == true`.
  2. *RadarTrafficSimulator Lifecycle & Tick Preservation (`TST-CON-046.2`, `RadarTrafficSimulatorTest.kt`)*:
     • Given `isAutoMode == false` in `RadarStateRepository`,
     • When running `generateFrame`, `tick`, `start` coroutine loop frames, and `stop`,
     • Assert `RadarStateRepository.getState().isAutoMode` remains `false` throughout and `setAutoMode()` restores `true`.
  3. *ANTBikeRadarDevice 3-Site `isAutoMode` Preservation (`TST-CON-046.3`, `ANTBikeRadarDeviceTest.kt`)*:
     • Given `isAutoMode == false` in `RadarStateRepository`,
     • When invoking `publishTargets`, `onWatchdogTimeout`, and `shutDown`,
     • Assert `RadarStateRepository.getState().isAutoMode` remains `false` and `setAutoMode()` restores `true`.
  4. *Repository `sourceStates` Failover & `reset()` Isolation (`TST-CON-046.4`, `RadarStateRepositoryTest.kt`)*:
     • Given concurrent `BLE` and `ANT_PLUS` sources with manual override `isAutoMode == false`,
     • When `BLE` disconnects and `RadarStateRepository` fails over to `ANT_PLUS` (or `SIMULATOR`),
     • Assert `isAutoMode` stays `false` (and `audioSnoozeState` stays intact per `REQ-CON-042.3`); when `RadarStateRepository.reset()` is invoked, assert clean reset to default `RadarState()` (`isAutoMode == true`).
  5. *Light Controller Manual Override vs Adaptive Automation (`TST-CON-046.5`, `RadarLightControllerTest.kt`)*:
     • Given `setManualLightMode()` or `cycleNextMode()` followed by incoming telemetry updates,
     • Assert `RadarLightController.isAutoMode.value == false`, `RadarStateRepository.getState().isAutoMode == false`, and `SmartRadarLightEngine` automation remains suspended until `setAutoMode()` restores `true`.

### TST-CON-047: **Compact Quick-Switch Sheet & Shared RadarLightModeGrid Parity, Button-Matching Icons & Ascending Mode Order Test Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-047` (`REQ-CON-047.1`..`REQ-CON-047.4`), `REQ-CON-031` (`REQ-CON-031.3`, `REQ-CON-031.8`)
- **Component**: `RadarLightModeGrid.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarLightButton.kt`, `RadarLightMode.kt`, `RadarLightController.kt`, `EditDeviceDialog.kt`, `RadarLightQuickSwitchSheetTest.kt`, `EditDeviceDialogRadarLightTest.kt`, `RadarLightButtonContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `DeadCodeEliminationContractTest.kt`
- **Jira**: `ATT-8456`, `ATT-8678`
- **Test Type**: Unit & UI (Compose/Robolectric + Emulator Visual)
- **Verification Method**: Unit
- **Procedure**:
  1. *Shared `RadarLightModeGrid` & Row-Major 2-Column Compose UI Parity (`TST-CON-047.1`, `RadarLightQuickSwitchSheetTest.kt`, `EditDeviceDialogRadarLightTest.kt`)*:
     • Given `RadarLightQuickSwitchSheetContent` and `RadarLightControlSectionContent` rendered in Robolectric `createComposeRule()` across Light/Dark themes, StVZO/non-StVZO, and online/offline states (with `0` new `src/main` reads per `REQ-PRO-049.1`),
     • Assert both surfaces render the identical 2-column row-major `RadarLightModeGrid` (`[AUTO, OFF]`, `[PELOTON, SOLID]`, `[NIGHT_FLASH, DAY_FLASH]` when non-StVZO; `RadarLightStvzoNotice` + full-width `AUTO` row 1 + `[OFF, SOLID]` row 2 when StVZO), `showActiveModeSubtitle = true` vs `!isOffline` active-mode subtitle gating, `RadarLightMode_AUTO`/`RadarLightMode_<MODE>` tags, semantic `resolveLightVisuals` vector icons, and zero vertical clipping in `RadarLightQuickSwitchSheetContent`.
  2. *Unified Ascending Mode Order & Architectural Contract Guards (`TST-CON-047.2`, `RadarLightControllerTest.kt`, `RadarLightQuickSwitchSheetTest.kt`, `RadarLightButtonContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `DeadCodeEliminationContractTest.kt`)*:
     • Assert `RadarLightMode.getAvailableModes(false)` returns `[OFF, PELOTON, SOLID, NIGHT_FLASH, DAY_FLASH]`, `getAvailableModes(true)` returns `[OFF, SOLID]`, `RadarLightController.cycleNextMode()` steps identically, `RadarLightQuickSwitchSheet*` and `RadarLightButton` (`resolveLightVisuals`) reflection/IF-SWE signatures remain intact, `ArchitectureConformanceContractTest` and `InterfaceSignatureDriftContractTest` pass, and `DeadCodeEliminationContractTest` reports `0` new violations (`RadarLightModeGrid.kt` uses `private` previews; `4/5 PASS` with `1` pre-existing unrelated `ATT-8823` failure in `EditDeviceDialog.kt`).
  3. *Localization Preservation (`TST-CON-047.3`)*:
     • Confirm `Localization Parity: N/A - no UI strings` (`strings_radar.xml` unchanged; all UI labels reuse existing 9-locale string resources with zero hardcoded literals).
  4. *`Rule 8.5` Light & Dark Theme Visual Inspection (`TST-CON-047.4`)*:
     • Capture Light/Dark `@Preview` comparison PNG `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png` covering `RadarLightQuickSwitchSheetContent` & `RadarLightControlSectionContent` (StVZO/non-StVZO, online/offline) and verify via `read_file` (zero clipping, crisp `resolveLightVisuals` icon contrast, balanced compact spacing).

### TST-TRK-064: **1-Tap Radar Audio Snooze, Peloton Sync & Urban Suppression Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-064` (`REQ-TRK-064.1`..`REQ-TRK-064.7`)
- **Component**: `RadarSnoozeController.kt`, `RadarAlertManager.kt`, `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarState.kt`, `RadarStateRepository.kt`, `BTLERadarDevice.java`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Jira**: `ATT-5119`, `ATT-6708`, `ATT-8679`
- **Test Description**:
  1. *Lifecycle-Decoupled Audio Snooze & Resilient Reconnect Tests (`TST-TRK-064.1`, `RadarSnoozeControllerTest.kt`, `RadarAlertManagerTest.kt`)*:
  • Verify audio snooze triggers (15m, 30m, Fast Only, Rest of Ride), countdown decrementing, automatic restoration upon timer expiry, and manual resume across READY, PAUSED, and TRACKING modes.
  • Verify snooze state is preserved across transient BLE drops (`disconnectFromGatt`) and reset cleanly upon explicit workout finish.
  • Verify monotonic time source resilience for snooze duration and expiration against wall-clock jumps (`ATT-7580`).
  2. *Muted Badge Countdown Indicator, Muted-Bell Icon & Approaching Vehicle Count Tests (`TST-TRK-064.2`, `RadarEdgeOverlayTest.kt`)*:
  • Verify top `RadarStatusBadgePill` displays muted-bell vector icon and localized countdown indicator alongside (never replacing) approaching vehicle count (`targets.size`) during active threats, while clear-state (`snoozeState.isSnoozed && isClear`) renders zero green badge per `TST-TRK-062.4`.
  • Verify all threat badge accessibility descriptions format cleanly using localized string resources.
  3. *Acoustic Chime Gating, Haptics Preservation & Manual Resume Override Tests (`TST-TRK-064.3`, `RadarAlertManagerTest.kt`)*:
  • Verify `vibrate()` fires on threat transitions even when audio snooze, Peloton sync, or Urban mute is active.
  • Verify tapping "Resume Audio Alerts" overrides active Peloton sync and Urban mute until the next state transition.
  • Verify `RadarSnoozeController.toggleRadarAudioSnooze()` alternates between snooze and active audio alerts.
  4. *Peloton Mode Light-Audio Sync Tests (`TST-TRK-064.4`, `RadarAlertManagerTest.kt`)*:
  • Verify routine `APPROACHING` chimes are suppressed when tail light enters `PELOTON` mode and restored when exiting, while `FAST_APPROACHING` threats continue to sound and haptics remain active.
  5. *Resilient Smart Urban Congestion Filter Tests (`TST-TRK-064.5`, `RadarAlertManagerTest.kt`)*:
  • Verify convoy suppression when $\ge 3$ vehicles $< 5\text{ m/s}$ persist for $\ge 30\text{ s}$, exit after $\ge 20\text{ s}$ clear, and immediate high-speed threat alert triggering.
  • Verify 30s congestion accumulator remains intact when $\ge 3$ vehicles are present and one vehicle passes at $\ge 5.0\text{ m/s}$.
  6. *Settings Persistence & DataStore Parity Tests (`TST-TRK-064.6`, `TuningPreferencesDataStoreTest.kt`)*:
  • Verify preference persistence and defaults for `radarPelotonAudioSync` and `radarSmartUrbanMute`.
  7. *9-Language Localization Parity Test (`TST-TRK-064.7`, `TranslationParityTest.kt`)*:
  • Verify 100% key and format parity across `strings_radar.xml` in all 9 supported locales.
  8. *Clean-Room Targeted Test Suite Execution (`TST-TRK-064.8`)*:
  • Execute targeted package tests asserting 100% pass rate.

### TST-TRK-065: **3D Stereo Panning & Approach-Speed Pitch Modulation Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-065` (`REQ-TRK-065.1`..`REQ-TRK-065.6`)
- **Component**: `RadarSpatialAudioMath.kt`, `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Jira**: `ATT-5120`, `ATT-6709`
- **Test Description**:
  1. *Stereo Lateral Gain Computation Tests (`TST-TRK-065.1`, `RadarSpatialAudioMathTest.kt`)*:
  • Verify stereo gain calculation `(leftGain, rightGain)` for in-lane ($|x| < 1.0\text{ m}$), safe passing ($|x| \ge 1.5\text{ m}$), intermediate linear interpolation, and traffic orientation (RHT vs. LHT).
  • Verify stereo gains collapse to centered `(1.0, 1.0)` on `SYSTEM_DEFAULT` when `hasHeadphones` is false.
  2. *Speed-to-Pitch Linear Mapping Tests (`TST-TRK-065.2`, `RadarSpatialAudioMathTest.kt`)*:
  • Verify linear mapping from approach velocity ($3.0\text{--}30.0\text{ m/s}$) to pitch factors ($0.95x\text{--}1.35x$) and boundary clamping.
  3. *Lane-Change Confirmation Spatial Cue Tests (`TST-TRK-065.3`, `RadarAlertManagerTest.kt`)*:
  • Verify spatial tick chime emission when tracked target transitions from in-lane to safe passing lane and suppression when disabled.
  • Verify multi-car convoy per-target `LaneTrackState` tracking with spatial hysteresis and 3,000 ms per-target cooldown, asserting departed targets are pruned.
  4. *Speaker Fallback & Lifecycle Invariant Tests (`TST-TRK-065.4`, `RadarAlertManagerTest.kt`, `RadarAudioSynthesizerTest.kt`)*:
  • Verify stereo gains collapse to centered `(1.0, 1.0)` when audio route is `FORCE_SPEAKER` or `SYSTEM_DEFAULT` without headphones.
  • Verify `releasePreviewSynthesizer()` closes and unsets active `AudioTrack`s without leakage.
  5. *Preferences Persistence & DataStore Parity Tests (`TST-TRK-065.5`, `TuningConfigTest.kt`)*:
  • Verify DataStore keys, default values, and serialization for `radarSpatialAudioEnabled`, `radarSpeedPitchModulation`, `radarTrafficOrientation`, and `radarLaneChangeTickEnabled`.
  6. *9-Language Localization Parity Test (`TST-TRK-065.6`, `TranslationParityTest.kt`)*:
  • Verify 100% key and format parity across `strings_radar.xml` in all 9 supported locales.
  7. *Clean-Room Targeted Test Suite Execution (`TST-TRK-065.7`)*:
  • Execute targeted package tests asserting 100% pass rate.

### TST-TRK-066: **Repository-Only Live Recording Service Database Access Layer Test Suite (Phase C)** [Verified]
- **Requirement(s)**: `REQ-TRK-066` (`REQ-TRK-066.1`..`REQ-TRK-066.5`)
- **Component**: `TrackerService.java`, `BANALService.java`, `DeviceManager.java`, `WorkoutRepository.kt`, `SportTypesRepository.kt`, `DeviceDataRepository.kt`, `PairedRemoteDeviceDto.kt`
- **Jira**: `ATT-6427`
- **Test Description**:
  1. *AST DatabaseManager Prohibition Contract Test (`TST-TRK-066.1`, `RepositoryServiceCleanlinessContractTest.kt`)*:
  • Verify via AST and reflection inspection that zero direct `*DatabaseManager.getInstance()` or `*DbHelper.getInstance()` calls exist in `TrackerService.java`, `BANALService.java`, and `DeviceManager.java`.
  2. *PairedRemoteDeviceDto & DeviceDataRepository Remote Query Unit Test (`TST-TRK-066.2`, `DeviceDataRepositoryTest.kt`)*:
  • Verify `DeviceDataRepository.getPairedRemoteDevices(protocol)` extracts paired ANT+ and Bluetooth LE devices correctly into `PairedRemoteDeviceDto` with zero raw Cursors leaked to callers.
  3. *TrackerService Repository Delegation & Lifecycle Unit Tests (`TST-TRK-066.3`, `TrackerServiceResumeTest.kt`, `TrackerServiceFinalizationTest.kt`)*:
  • Verify workout start, 1-Hz sample recording, lap persistence, crash-resume, and unconditional `FINISHED = 1` finalization execute identically through repository delegates.
  4. *BANALService Sport Type Delegation Unit Test (`TST-TRK-066.4`, `BANALServiceLifecycleTest.kt`)*:
  • Verify sport type lookup, default sport type ID, and sport type lists in `BANALService` delegate to `SportTypesRepository`.
  5. *Clean-Room Targeted Test Suite Execution (`TST-TRK-066.5`)*:
  • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.repositories.*"` asserting 100% pass rate.

### TST-TRK-067: **Firmware-Smoothed & Quantized Synthetic RR Stream Detection Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-067` (`REQ-TRK-067.1`..`REQ-TRK-067.6`)
- **Component**: `HrvSignalQualityAnalyzer.kt`, `HrvSignalQualityResult.kt`, `HRVMetricsDevice.kt`, `MorningHrvCheckViewModel.kt`, `HrvPostWorkoutRecomputer.kt`
- **Jira**: `ATT-6744`
- **Test Description**:
  1. *Smoothed RR Stream Detection Tests (`TST-TRK-067.1`, `HrvSignalQualityAnalyzerTest.kt`)*:
  • Verify classification of empirical Magene-style moving-average smoothed series ($rMSSD \approx 3.3\text{--}7.3\text{ ms}$, $\alpha_1 \approx 1.41\text{--}1.81$) as `SMOOTHED_STREAM` via both low-rMSSD/high-a1 discordance gate (accompanied by $TPR < 0.28$) and low Turning-Point Ratio ($TPR < 0.20$ with $\alpha_1 > 1.30$).
  2. *Quantized Constant-BPM Stepped Series Tests (`TST-TRK-067.2`, `HrvSignalQualityAnalyzerTest.kt`)*:
  • Verify classification of stepped constant-BPM series ($RR = 60000 / \text{integer\_BPM}$) where $>55\%$ of consecutive beat differences satisfy $|\Delta RR| < 1.0\text{ ms}$ over $\ge 30$ beats as `QUANTIZED_SYNTHETIC`.
  3. *Authentic Rest & High-Intensity Exercise Invariant Tests (`TST-TRK-067.3`, `HrvSignalQualityAnalyzerTest.kt`)*:
  • Verify authentic resting ECG ($rMSSD \approx 42\text{ ms}$, $\alpha_1 \approx 1.12$, normal RSA turning points) evaluates to `VALID`.
  • Verify authentic high-intensity exercise ECG ($rMSSD \approx 4.5\text{ ms}$, $\alpha_1 \approx 0.48$) evaluates to `VALID` and is never falsely flagged as `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
  • Verify authentic resting sinus rhythm with Mayer-wave baroreflex drift ($rMSSD = 4.5\text{ ms}$, $SDNN = 8.7\text{ ms}$, $\alpha_1 = 1.52$, $TPR = 0.38$, e.g. Polar H10 evening/sympathetic state) evaluates to `VALID` and is not falsely flagged as smoothed.
  4. *Data Sufficiency & Artifact Pre-Gating Tests (`TST-TRK-067.4`, `HrvSignalQualityAnalyzerTest.kt`)*:
  • Verify $<30$ beats evaluates to `INSUFFICIENT_DATA`.
  • Verify $>5.0\%$ artifact rate evaluates to `HIGH_ARTIFACTS`.
  5. *HRVMetricsDevice 5s Tick & Lifecycle Invariant Tests (`TST-TRK-067.5`, `HRVMetricsDeviceTest.kt`)*:
  • Verify `HRVMetricsDevice` updates `signalQualityResult` on every 5s evaluation tick, reflects `SMOOTHED_STREAM` for smoothed input, and clears to initial default on `resetSession()` adhering to Rules 51 & 52.
  6. *MorningHrvCheckViewModel & Post-Workout Recomputer Wiring Tests (`TST-TRK-067.6`, `MorningHrvCheckViewModelTest.kt`, `HrvPostWorkoutRecomputerTest.kt`)*:
  • Verify `MorningHrvCheckViewModel` populates signal quality status into `MorningHrvUiState` upon measurement completion.
  • Verify `HrvPostWorkoutRecomputer` exposes helper evaluation for full workout RR streams.
  7. *Clean-Room Targeted Test Suite Execution (`TST-TRK-067.7`)*:
  • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.*" --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.ui.morninghrv.*"` asserting 100% pass rate.

### TST-TRK-068: **Smoothed RR Stream Warnings & Sensor Compatibility Guidance Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-068` (`REQ-TRK-068.1`..`REQ-TRK-068.6`)
- **Component**: `HrvInfoBottomSheet.kt`, `MorningHrvCheckScreen.kt`, `HrvAlpha1ScatterCard.kt`, `HrvZoneIndicator.kt`, `TrackingViewModel.kt`, `strings_hrv.xml`
- **Jira**: `ATT-6745`
- **Test Description**:
  1. *HrvInfoBottomSheet Sensor Recommendation & Top Warning Banner Tests (`TST-TRK-068.1`, `HrvInfoBottomSheetTest.kt`)*:
  • Verify updated sensor recommendation text displays Polar H10/H9, Garmin HRM-Dual/Pro as recommended and optical/Magene/CooSpo as unsuitable.
  • Verify top warning banner renders conditionally when `signalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`, and is hidden when `VALID`.
  2. *Cockpit DFA a1 Zone 2 Suppression Tests (`TST-TRK-068.2`, `HrvZoneIndicatorTest.kt`, `TrackingViewModelHrvTest.kt`)*:
  • Verify `HrvZoneIndicator.calculateZoneColor` and `TrackingViewModel` suppress green Zone 2 color when `isSmoothedStream` is true.
  • Verify valid unsmoothed streams maintain normal `TTColor.Zone2` rendering.
  3. *MorningHrvCheckScreen Smoothed Signal Warning Card & Dynamic Action Button Tests (`TST-TRK-068.3`, `MorningHrvCheckScreenTest.kt`)*:
  • Verify `SmoothedSignalWarningCard` renders when `uiState.signalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
  • Verify independent co-existence with `highArtifactWarning`.
  • Verify action button dynamic prominence: when `recommendDiscard == true`, *Verwerfen* renders as primary filled `Button` and *Speichern* renders as secondary `OutlinedButton`; when `false`, *Speichern* is filled `Button`.
  • Verify action button constraints apply `Modifier.heightIn(min = 52.dp)` with single-line truncation protection.
  4. *HrvAlpha1ScatterCard Warning Banner Tests (`TST-TRK-068.4`, `HrvAlpha1ScatterCardTest.kt`)*:
  • Verify inline warning banner renders when `isSmoothedStream == true` and is hidden when false.
  5. *9-Language Translation Parity & Copy Polish Tests (`TST-TRK-068.5`, `TranslationParityTest.kt`)*:
  • Verify 100% key and format parity across `strings_hrv.xml` in all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) for updated `morning_hrv_subtitle`, `morning_hrv_btn_save`, and new `morning_hrv_result_title`.
  6. *Clean-Room Targeted Test Suite Execution (`TST-TRK-068.6`)*:
  • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.cockpit.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"` asserting 100% pass rate.

### TST-TRK-069: **ANT+ UTC Epoch Timestamping, Reconnection Sensor Registration & BLE Bundled Drift Guard Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-069` (`REQ-TRK-069.1`..`REQ-TRK-069.4`)
- **Component**: `ANTHeartRateDevice.java`, `BTLEHeartRateDevice.java`, `HRVMetricsDevice.kt`
- **Jira**: `ATT-6713`, `ATT-6742`
- **Test Description**:
  1. *ANT+ estTimestamp UTC Epoch Conversion Unit Tests (`TST-TRK-069.1`, `ANTHeartRateDeviceTest.kt`)*:
  • Verify `estTimestamp > 0` is converted via `System.currentTimeMillis() - SystemClock.elapsedRealtime() + estTimestamp`, yielding valid UTC epoch timestamp matching current wall-clock.
  • Verify fallback to `System.currentTimeMillis()` when `estTimestamp <= 0` or after connection reset.
  2. *Driver Reconnection Sensor Re-Registration Unit Tests (`TST-TRK-069.2`, `ANTHeartRateDeviceTest.kt`, `BTLEHeartRateDeviceTest.kt`)*:
  • Verify `unregisterSensors()` resets `mRrIntervalSensor` to `null` in both `ANTHeartRateDevice` and `BTLEHeartRateDevice`.
  • Verify subsequent call to `registerSensors()` successfully re-instantiates and registers `mRrIntervalSensor` via `addAndRegisterSensor`.
  3. *BLE Bundled RR Backward Anchoring & Sub-ms Precision Unit Tests (`TST-TRK-069.3`, `BTLEHeartRateDeviceTest.kt`)*:
  • Verify bundled packets with $K > 1$ beats anchor the newest beat to `packetArrivalMs` and back-date prior beats without shifting into the future.
  • Verify 60-minute simulated session (3600 beats) with double precision accumulation maintains cumulative clock drift $< 2000\text{ ms}$ with zero false `ARTIFACT_GAP` events.
  4. *End-to-End ANT+ & HRVMetricsDevice Integration Regression Tests (`TST-TRK-069.4`, `ANTHeartRateDeviceHrvMetricsTest.kt`)*:
  • Wire `ANTHeartRateDevice` to `HRVMetricsDevice` and stream synthetic ANT+ packets.
  • Verify beats are not evicted by `HRVMetricsDevice.pruneOldBeats()` and valid real-time `rMSSD` and `DFA a1` are produced.
  5. *Clean-Room Targeted Test Suite Execution (`TST-TRK-069.5`)*:
  • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ANTHeartRateDeviceTest" --tests "com.atrainingtracker.banalservice.devices.BTLEHeartRateDeviceTest" --tests "com.atrainingtracker.banalservice.devices.hrv.*"` asserting 100% pass rate.

### TST-TRK-070: **2-Level Sensor Hierarchy (SensorSubCategory), Unified HRV/Radar Groups & Complete ActivityType Sensor Mapping Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-070` (`REQ-TRK-070.1`..`REQ-TRK-070.5`)
- **Component**: `SensorCategory.java`, `SensorSubCategory.java`, `SensorType.java`, `ActivityType.java`, `EditSensorFieldDialog.kt`
- **Jira**: `ATT-6726`
- **Test Description**:
  1. *2-Level Sensor Taxonomy & SensorType Category/SubCategory Parity Test (`TST-TRK-070.1`, `SensorCategorySubCategoryTest.kt` / `SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify `SensorCategory` defines exactly 6 enum constants (`POWER_AND_PEDALING`, `HEART_RATE_AND_ENERGY`, `SPEED_PACE_AND_DISTANCE`, `TIME_AND_LAPS`, `ELEVATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`).
  • Verify `SensorSubCategory` defines exactly 9 enum constants (`POWER_LOAD`, `POWER_ROLLING`, `POWER_PEAK`, `PEDALING_DYNAMICS`, `HRV_AND_DRIFT`, `LAP_METRICS`, `RADAR_SAFETY`, `AMBIENT_TEMPERATURE`, `PHONE_BATTERY_GROUP`).
  • Verify all 63 `SensorType` constants map to non-null categories and expected `@Nullable SensorSubCategory` (null for direct core metrics; assigned sub-category for grouped secondary metrics).
  • Verify all 5 HRV metrics (`HRV_DFA_A1`, `HRV_RMSSD`, `RR_INTERVAL`, `HRV_ARTIFACT_RATE`, `LIVE_HR_DRIFT`) reside in `HEART_RATE_AND_ENERGY` with sub-category `HRV_AND_DRIFT`.
  • Verify `CADENCE` and `STRIDES` reside in `SPEED_PACE_AND_DISTANCE`.
  2. *ActivityType Sensor Telemetry Mapping & Athletic Ordering Contract Test (`TST-TRK-070.2`, `ActivityTypeSensorMappingTest.kt` / `ActivityTypeTest.kt`)*:
  • Verify `ActivityType.BIKE_POWER.getSensorTypeArray()` contains all 8 Power Duration metrics (`POWER_5S..20M`, `POWER_PEAK_5S..20M`) with 100% parity against `CyclingPowerMetricsDevice.java`.
  • Verify `ActivityType.getSensorTypeArray()` includes `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` for `BSportType.BIKE` and strictly excludes radar metrics from running and non-cycling profiles.
  • Verify `CALORIES` is included across active sports profiles.
  • Verify primary athletic ordering puts instant telemetry (Power, HR, Speed, Cadence) before load metrics, benchmarks, and balance.
  3. *EditSensorFieldDialog 2-Level Grouping, Auto-Expansion & Capability Badges Test (`TST-TRK-070.3`, `EditSensorFieldDialogTest.kt` / `SensorTypeSearchFilterTest.kt`)*:
  • Verify direct metrics are grouped at category top and sub-categories are partitioned into nested expandable cards.
  • Verify selected sensor type auto-expands its category and sub-category.
  • Verify `TrackingViewsDbHelper.isBarSupported(sensorType, activityType)` and `isGraphSupported(sensorType)` correctly drive "Bar" and "Graph" capability badge rendering.
  • Verify Section 5.8 bottom-sheet scroll safety (zero nested scroll containers).
  4. *9-Language Localization & Format Specifier Parity Test (`TST-TRK-070.4`, `SensorHierarchyLocalizationTest.kt`)*:
  • Verify 100% presence and parity of all category and sub-category title strings across all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  • Verify zero format specifier mismatches and zero illegal collapsed linebreaks.
  5. *Clean-Room Targeted Regression Suite Execution (`TST-TRK-070.5`)*:
  • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.banalservice.ActivityTypeTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*"` asserting 100% pass rate.

### TST-TRK-071: **Google Fused Location Registration Idempotency, Deduplication & Teardown Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-071` (`REQ-TRK-071.1`..`REQ-TRK-071.5`)
- **Component**: `SpeedAndLocationDevice_GoogleFused.java`
- **Jira**: `ATT-6683`
- **Test Description**:
  1. *Idempotent Registration Guard & Duplicate Prevention Test (`TST-TRK-071.1`, `SpeedAndLocationDeviceGoogleFusedTest.kt`)*:
  • Given `SpeedAndLocationDevice_GoogleFused` connected with updates registered (`isLocationUpdatesRegistered() == true`),
  • When `checkOrReRegisterLocationUpdates()` is called repeatedly (simulating 7 tracking lifecycle calls),
  • Then verify `FusedLocationApi.requestLocationUpdates(...)` was called exactly once, preventing duplicate concurrent listener registrations.
  2. *Initial Connection Registration & Observability Test (`TST-TRK-071.2`, `SpeedAndLocationDeviceGoogleFusedTest.kt`)*:
  • Verify `isLocationUpdatesRegistered()` returns `false` before connection.
  • Verify `onConnected(Bundle)` triggers `requestLocationUpdates(...)` and sets `isLocationUpdatesRegistered()` to `true`.
  3. *Clean Lifecycle Teardown & Unregistration Test (`TST-TRK-071.3`, `SpeedAndLocationDeviceGoogleFusedTest.kt`)*:
  • Verify `shutDown()` on an active registered device invokes `LocationServices.FusedLocationApi.removeLocationUpdates(...)`.
  • Verify `isLocationUpdatesRegistered()` is reset to `false` and `mGoogleApiClient.disconnect()` is called.
  4. *Connection State Resets & Exception Shielding Test (`TST-TRK-071.4`, `SpeedAndLocationDeviceGoogleFusedTest.kt`)*:
  • Verify `onConnectionSuspended()` and `onConnectionFailed()` reset `isLocationUpdatesRegistered()` to `false`.
  • Verify exceptions thrown during `removeLocationUpdates(...)` in `shutDown()` are caught and logged cleanly without throwing uncaught exceptions.
  5. *Targeted Devices & Architecture Contract Suite Execution (`TST-TRK-071.5`)*:
  • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"` asserting 100% pass rate.

### TST-TRK-072: **Guided DFA a1 Step & Ramp Test Protocol Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-072` (`REQ-TRK-072.1`..`REQ-TRK-072.8`)
- **Component**: `HrvRampTestViewModel.kt`, `HrvRampTestScreen.kt`, `HrvRampTestConfig.kt`, `HrvThresholdEstimator.kt`, `TrackerService.java`, `HrvDfaA1RangeSliderGestureTest.kt`, `strings_hrv.xml`
- **Jira**: `ATT-4969`, `ATT-7529`, `ATT-7745`, `ATT-8951`
- **Test Description**:
  1. *Protocol Configuration & Direct Start from CONFIG Phase (`TST-TRK-072.1`, `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt`)*:
     • Verify sensor telemetry is bound in `init` and continuously evaluates readiness while in `CONFIG` phase.
     • Verify `canStartTest` is `true` when sensor quality is `HIGH` or `MODERATE`, enabling the `Test starten` button.
     • Verify tapping `Test starten` transitions directly into `WARMUP` phase without requiring an intermediate pre-flight screen.
  2. *Inline Sensor- & Signalstatus UI & Quality Diagnostics (`TST-TRK-072.2`, `HrvRampTestScreenTest.kt`)*:
     • Verify inline sensor status card renders directly on `ProtocolConfigurationCard` above `Test starten`.
     • Verify that when sensors are disconnected or artifact rate $> 5.0\%$ (`UNSUITABLE`), `Test starten` is disabled and clear diagnostic advice is shown.
  3. *Single-Line SegmentedButton & Active Action Button Layout Integrity (`TST-TRK-072.3`, `HrvRampTestScreenTest.kt`)*:
     • Verify all `SegmentedButton` rows (Sport, Protocol Type, Step Duration) render without checkmark icons (`icon = {}`) and enforce single-line text formatting (`maxLines = 1, overflow = TextOverflow.Ellipsis`).
     • Verify action buttons on `ActivePacingGuidanceCard` (*Abwärmen beginnen* / *Test beenden*) render with uniform `height(44.dp)`.
  4. *Dynamic Protocol Explanation & Pros/Cons Box (`TST-TRK-072.4`, `HrvRampTestScreenTest.kt`)*:
     • Verify dynamic update of summary badge, 3 numbered procedural steps (*Ablauf & Durchführung*), and protocol-specific *+ Vorteil* / *– Nachteil* bullet points when switching Sport (Cycling/Running), Protocol (Step/Ramp), and Step Duration (4 min / 3 min).
  5. *Dual Power (W) and Speed (km/h) Live Telemetry & Target Comparison (`TST-TRK-072.5`, `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt`)*:
     • Verify `HrvRampTestViewModel` collects `SensorType.SPEED_mps` and exposes `currentSpeedKmh`.
     • Verify `ActivePacingGuidanceCard` renders prominent **Ziel** vs. **Aktuell (Ist)** pacing comparison block.
     • Verify simultaneous display of live Power (`W`) and live Speed (`km/h`) alongside current HR, live DFA $\alpha_1$, and artifact rate %.
  6. *Early Sub-Exhaustion Auto-Completion Detection (`TST-TRK-072.6`, `HrvRampTestViewModelTest.kt`)*:
     • Stream simulated DFA $\alpha_1$ values descending below dynamic cutoff $(T_2 - 0.05f).\text{coerceAtLeast}(0.25f)$ (cutoff $0.45$ for default $T_2 = 0.50$, or cutoff $0.55$ for custom $T_2 = 0.60$).
     • Verify that sustained $\alpha_1 < \text{cutoff}$ for $\ge 60\text{ s}$ transitions state to `EARLY_COMPLETION` with user alert and prompts transition to `COOLDOWN`.
     • Verify that transient dips below cutoff lasting $< 60\text{ s}$ do not trigger early completion.
  7. *9-Language Localization & Format Parity Test (`TST-TRK-072.7`, `TranslationParityTest.kt`)*:
     • Verify 100% presence and argument-specifier parity for all new protocol guidance strings and natural sensor status copy across all 9 localized `strings_hrv.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  8. *Clean-Room Targeted Test Suite Execution (`TST-TRK-072.8`)*:
     • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"` asserting 100% pass rate.
  9. *Consolidated Launcher Access & Ergonomic Entry Point Verification (`TST-TRK-072.9`, `HrvRampTestScreenTest.kt`, `HrvDfaA1RangeSliderGestureTest.kt`)*:
     • Verify `hrv_test_cockpit_launcher` is absent from `TrackingTabsScreen.kt` `bottomContent`.
     • Verify `btn_guided_hrv_test` is absent from `AthleteProfileScreen.kt` HRV tab.
     • Verify `ZonesSettingsActivity.kt` `HrvDfaA1SettingsContent` renders `Icons.AutoMirrored.Filled.TrendingUp` for `hrv_start_guided_ramp_test`.
     • Verify via Robolectric `createComposeRule()` (passing non-null `onOpenHrvTest = {}` with `0` `src/main` reads per `REQ-PRO-049.1`) that `HrvDfaA1SettingsContent` positions the `R.string.hrv_start_guided_ramp_test` `OutlinedButton` strictly between `R.string.hrv_zones_guidance_desc` and the first zone card / threshold control (`guidanceDesc.bottom <= ctaButton.top && ctaButton.bottom <= firstZoneOrThresholdControl.top`), renders single-line in verbose German (`"Geführten Schwellentest starten"` at `360.dp`–`412.dp`), keeps `HrvDfaA1RangeSliderCard` visible inside `680.dp` (`testZoneSettingsScreen_tab3PreservesVerticalScrollAndOutsideSwipe`), invokes `onOpenHrvTest` on click, and passes `Rule 8.5` Light/Dark PNG inspection (`ATT-8951`).
     • Verify host route `NavRoutes.HRV_TEST` remains mounted in `ATrainingTrackerApp.kt` and reachable via Training Zones.
  10. *Lifecycle Automation: Lap Splits, Protocol Auto-Naming & Stop Broadcast Tests (`TST-TRK-072.10`, `HrvRampTestViewModelTest.kt`, `TrackerServiceAutoNamingTest.kt`)*:
     • Verify `REQUEST_NEW_LAP` is broadcast on `WARMUP -> TESTING` transition.
     • Verify `REQUEST_NEW_LAP` is broadcast on `transitionToCooldown()`.
     • Verify `REQUEST_STOP_TRACKING` is broadcast on `COOLDOWN -> FINISHED` and on `finishTest()`.
     • Verify `tagActiveWorkoutWithProtocol()` sets `WORKOUT_NAME` to localized step/ramp test title across all 9 locales in `strings_hrv.xml`.
     • Verify `TrackerService.finalizeLiveSession()` preserves `WORKOUT_NAME` when `method` is `"GUIDED_STEP"` or `"GUIDED_RAMP"` and does not overwrite it with cluster or location names.

### TST-TRK-073: **Live 2D Lane-Offset & Vehicle Sizing Radar Edge Overlay Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-073` (`REQ-TRK-073.1`..`REQ-TRK-073.8`)
- **Component**: `RadarEdgeOverlay.kt`, `RadarEdgeOverlayTest.kt`, `RadarSettingsSection.kt`, `WorkoutRepository.kt`, `TrackOnMapScreen.kt`, `FitFileWriter.java`
- **Jira**: `ATT-5096`
- **Test Description**:
  1. *Dynamic 175m Rail Scaling & Sizing Kinematics Tests (`TST-TRK-073.1`, `RadarEdgeOverlayTest.kt`)*:
     • Verify `RadarOverlayMath.resolveRailMaxRange()` returns 175.0m when targets exceed 140.0m or V2 telemetry is present, and 140.0m for legacy V1.
     • Verify `RadarOverlayMath.normalizeRadarRange()` correctly normalizes 175m range inputs to [0.0, 1.0].
     • Verify target indicator geometry and test tag resolution across `UNKNOWN_OR_LIGHT` (compact dot), `CAR` (standard circle), and `TRUCK_OR_BUS` (elongated pill).
  2. *Lateral Lane Offset Translation & Traffic Orientation Tests (`TST-TRK-073.2`, `RadarEdgeOverlayTest.kt`)*:
     • Verify `RadarOverlayMath.calculateHorizontalOffset()` translates signed lateral offset meters to horizontal pixel offset within [-6.dp, +6.dp].
     • Verify inversion of lateral offset direction under `RadarTrafficOrientation.LEFT_HAND` vs. `RIGHT_HAND`.
  3. *TuningPreferencesDataStore Close-Pass Threshold Tests (`TST-TRK-073.3`, `TuningPreferencesDataStoreTest.kt`)*:
     • Verify default threshold value is 1.5f meters.
     • Verify persistence and flow updates for presets 1.0f, 1.5f, and 2.0f meters.
  4. *Aftermath Close-Pass Hazard Marker Generation Tests (`TST-TRK-073.4`, `WorkoutRepositoryTest.kt`)*:
     • Stream sample intervals with vehicle passes and verify `WorkoutRepository.getWorkoutMarkers()` emits a close-pass hazard `LocationMarker` with custom hazard icon when `radarPassingDistanceM < threshold`.
     • Verify marker title includes passing clearance distance, vehicle type, and speed.
     • Verify vehicle passes with clearance $\ge \text{threshold}$ emit standard pass markers.
  5. *FIT Protocol V2.0 Developer Fields Serialization Tests (`TST-TRK-073.5`, `FitFileWriterRadarTest.kt`)*:
     • Export synthetic workout with V2 passing distance and large vehicle count; decode with Garmin FIT SDK `FitDecoder`.
     • Assert developer field 4 (`passing_distance`) and developer field 5 (`large_vehicle_count`) are defined and written to `RecordMesg`.
  6. *9-Language Localization & Format Parity Audit (`TST-TRK-073.6`, `TranslationParityTest.kt`)*:
     • Verify 100% presence and argument-specifier parity for all new close-pass settings, map marker titles, and summary labels across all 9 localized `strings_radar.xml` files.
  7. *Clean-Room Targeted Test Suite Execution (`TST-TRK-073.7`)*:
     • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"` asserting 100% pass rate.

### TST-TRK-074: **Duplicate Location Filtering, Asymmetric Deadband Separation & Stationary Entry Hysteresis Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-074` (`REQ-TRK-074.1`..`REQ-TRK-074.4`)
- **Component**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDeviceStationaryJitterTest.kt`
- **Jira**: `ATT-6682`
- **Test Description**:
  1. *Sub-Interval & Duplicate Callback Rejection Test (`TST-TRK-074.1`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
     • Fix 1: Movement at $8.0\text{ m/s}$.
     • Fix 2: 100 ms later, duplicate coordinate fix with $\text{rawDelta} == 0.0\text{ m}$ and $\text{speed} == 0.0\text{ m/s}$.
     • Verify callback is dropped: `isStationaryPinned` remains `false`, speed sensor value remains $8.0\text{ m/s}$, and distance is uncorrupted.
  2. *Single-Sample Zero Doppler Suppression on Low-Speed Climb (`TST-TRK-074.2`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
     • Fix 1: Athlete climbing at $3.0\text{ m/s}$ ($10.8\text{ km/h}$).
     • Fix 2: 1s later, displacement is $3.0\text{ m}$ ($\le \text{escapeRadius} = 3.75\text{ m}$), but provider reports $\text{speed} == 0.0\text{ m/s}$.
     • Verify `isStationaryPinned` remains `false`, hysteresis count reaches 1, and speed does not drop to 0.0.
  3. *3-Tick Consecutive Stationary Entry Hysteresis (`TST-TRK-074.3`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
     • Fix 1: Moving fix at $8.0\text{ m/s}$.
     • Fix 2: Halt at stop line (1st stationary tick $\le \text{deadbandRadius}$, speed 0.0 m/s): remains unpinned (`count = 1`).
     • Fix 3: 2nd stationary tick ($\le \text{deadbandRadius}$, speed 0.0 m/s): remains unpinned (`count = 2`).
     • Fix 4: 3rd stationary tick ($\le \text{deadbandRadius}$, speed 0.0 m/s): transitions to `isStationaryPinned = true`, anchors coordinates, speed clamps to $0.0\text{ m/s}$, bearing locks.
  4. *Streak Reset on Movement Resumption Before 3 Ticks (`TST-TRK-074.4`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
     • 2 stationary ticks followed by movement resumption ($\text{rawDelta} > \text{deadbandRadius}$, $\text{speed} = 5.0\text{ m/s}$).
     • Verify `mStationaryHysteresisCount` resets to `0` and device never pins.
  5. *Initial Fix Immediate Pinning Invariance (`TST-TRK-074.5`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
     • Verify initial fix (`mPrevLocation == null`) with $\text{speed} == 0.0\text{ m/s}$ pins immediately to anchor on tick 1 (`REQ-TRK-074.4`).
  6. *Targeted Package Regression Execution (`TST-TRK-074.6`)*:
     • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*"` asserting 100% pass rate.


### TST-TRK-075: **Core Tracking Pipeline Integration Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-075` (`REQ-TRK-075.1`..`REQ-TRK-075.5`), `REQ-TRK-001`
- **Component**: `TrackerService.java`, `WorkoutRepository.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
- **Jira**: `ATT-6185`
- **Test Description**:
  1. *Start Tracking and Multi-Sensor Telemetry Ingestion Test (`TST-TRK-075.1`, `TrackerServiceIntegrationTest.kt`)*:
     • Start tracking session; emit simulated samples for HR (145 bpm), Speed (8.33 m/s), Cadence (85 rpm), Power (220 W), Distance (100 m), and beat-to-beat RR intervals.
     • Assert `workout_summaries` row exists with valid `workout_id`.
     • Assert `workout_samples_<baseFileName>` table exists and contains inserted row with matching sensor column values.
     • Assert `workout_rr_intervals` contains the flushed RR beat entries.
  2. *Pause Tracking Ingestion Freeze Test (`TST-TRK-075.2`, `TrackerServiceIntegrationTest.kt`)*:
     • Transition to pause (`TrainingApplication.pauseTracking(false)`).
     • Trigger sample tick with updated total time and simulated sensor data.
     • Query database: assert sample row count does not increase, active time remains unchanged, and total elapsed time is updated in `workout_summaries`.
  3. *Resume Tracking Sample Continuation Test (`TST-TRK-075.3`, `TrackerServiceIntegrationTest.kt`)*:
     • Transition to resume (`mAutoPauseManager.onManualResume(...)` and `TrainingApplication.setTracking()`).
     • Trigger sample tick with incremented active time.
     • Query database: assert sample row count increases and `TIME_ACTIVE_s` updates in SQLite.
  4. *Auto-Pause Trigger and Motion Recovery Test (`TST-TRK-075.4`, `TrackerServiceIntegrationTest.kt`)*:
     • Configure AutoPause with threshold $1.0\text{ m/s}$.
     • Feed speed $0.2\text{ m/s}$ ($< 1.0\text{ m/s}$) for consecutive debounce ticks; assert transition to `AUTO_PAUSED` and pause broadcast.
     • Feed speed $5.0\text{ m/s}$ ($> 1.0\text{ m/s}$); assert transition back to `TRACKING` and resume broadcast.
  5. *Stop and Summary Finalization Verification Test (`TST-TRK-075.5`, `TrackerServiceIntegrationTest.kt`)*:
     • Populate telemetry: distance, HR max/avg, active time, ascent.
     • Invoke `endWorkout()`.
     • Assert `workout_summaries` has `FINISHED = 1`, final lap record in `laps`, and all computed summary fields match expectations.
  6. *Targeted Package Integration Suite Execution (`TST-TRK-075.6`)*:
     • `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.integration.*"` asserting 100% pass rate.


### TST-TRK-076: **Defensive Concurrency and Re-Entrant Stop-to-Start Lifecycle Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-076` (`REQ-TRK-076.1`..`REQ-TRK-076.4`)
- **Component**: `TrackerService.java`, `TrackerServiceFinalizationTest.kt`
- **Jira**: `ATT-7746`
- **Test Description**:
  1. *Immediate START_NORMAL During In-Flight Finalization Test (`TST-TRK-076.1`, `TrackerServiceFinalizationTest.kt`)*:
     • Simulate active workout finalization running on `mDbExecutor` after `handleActionStop()`.
     • Dispatch `onStartCommand(START_NORMAL)` before `finalizationTask` completes.
     • Assert zero `RejectedExecutionException` thrown and task enqueued on `mDbExecutor` in FIFO order.
  2. *Session Parameter Isolation Verification Test (`TST-TRK-076.2`, `TrackerServiceFinalizationTest.kt`)*:
     • Update `mWorkoutID`, `mBaseFileName`, and `mLiveSession` in `onStartCommand(START_NORMAL)` while previous session is finalizing.
     • Assert that the finishing session's `endWorkout` and `finalizeLiveSession` execute using the captured snapshot parameters without corruption.
  3. *Suppression of Stale StopSelf and Teardown Test (`TST-TRK-076.3`, `TrackerServiceFinalizationTest.kt`)*:
     • Complete `finalizationTask` on `mDbExecutor` after `onStartCommand(START_NORMAL)` has incremented `mSessionGeneration` and reset `mIsStopping = false`.
     • Assert that `performStopSelf()`, `performStopForeground()`, and `safeUnbindBanalService()` are NOT executed by the completed previous session.
  4. *Clean Normal Teardown on Terminal Exit Test (`TST-TRK-076.4`, `TrackerServiceFinalizationTest.kt`)*:
     • Execute `handleActionStop()` without subsequent restart.
     • Assert that upon `finalizationTask` completion, teardown is invoked, and `onDestroy()` shuts down `mDbExecutor`, `mScheduler`, and `mRrFlushExecutor`.
  5. *Targeted Package Regression Execution (`TST-TRK-076.5`)*:
     • `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*"` asserting 100% pass rate.


### TST-SEN-028: **Pairable Virtual Sensor ATT Fused (Position und Geschwindigkeit) Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SEN-028` (`REQ-SEN-028.1`..`REQ-SEN-028.4`)
- **Component**: `DeviceType.java`, `SpeedAndLocationDevice_ATTFused.kt`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `SensorType.java`, `TrackerService.java`, `EditDeviceDialog.kt`
- **Jira**: `ATT-7751`
- **Test Description**:
  1. *DeviceType Registration & Schema Version 11 Migration Test (`TST-SEN-028.1`, `DevicesDatabaseManagerMigrationTest.kt`)*:
     • Verify `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` exists and maps correctly to `SensorType` collection.
     • Create in-memory `Devices.db` at version 10. Upgrade to version 11; verify `SPEED_AND_LOCATION_ATT_FUSED` row inserted with `PAIRED = 0`.
     • Verify `DatabaseSchemaRegistry` reflects schema version 11 without discrepancy.
  2. *MySensorManager Priority Hierarchy & Dynamic Fallback Test (`TST-SEN-028.2`, `MySensorManagerPriorityTest.kt`)*:
     • When `SPEED_AND_LOCATION_ATT_FUSED` is paired and receiving, assert it is selected as `bestSensor` for `LATITUDE`, `LONGITUDE`, `BEARING`, `SPEED_mps`, `DISTANCE_m`.
     • When `SPEED_AND_LOCATION_ATT_FUSED` is unpaired, assert priority cleanly reverts to hardware wheel sensors for speed and Google Fused/GPS for location.
  3. *SpeedAndLocationDevice_ATTFused Driver Lifecycle & IMU Sampling Test (`TST-SEN-028.3`, `SpeedAndLocationDevice_ATTFusedTest.kt`)*:
     • Verify driver registers listeners for `Sensor.TYPE_GYROSCOPE` and `Sensor.TYPE_GAME_ROTATION_VECTOR` when enabled.
     • Feed simulated GPS fixes and wheel revolutions; assert EKF predicts and updates, emitting fused location, speed, distance, and 1-Hz `WORLD_YAW_RATE_radps`.
     • Verify clean teardown in `shutDown()` unregistering all IMU listeners with zero leaks.
  4. *Side-by-Side WorkoutSamples Persistence Test (`TST-SEN-028.4`, `WorkoutSamplesPersistenceTest.kt`)*:
     • Run simulated tracking session with both Google Fused and ATT Fused enabled.
     • Assert `WorkoutSamples.db` records both canonical/fused columns and dedicated `LATITUDE_att_fused`, `LONGITUDE_att_fused`, `SPEED_mps_att_fused`, and `WORLD_YAW_RATE_radps` columns.
  5. *EditDeviceDialog Educational Card & Source Guards Test (`TST-SEN-028.5`, `EditDeviceDialogGuardsTest.kt`)*:
     • Verify educational "How It Works" card is rendered for `SPEED_AND_LOCATION_ATT_FUSED`.
     • Simulate missing external wheel sensor; assert wheel speed and auto-calibration toggles are disabled with localized hint.
     • Simulate missing gyroscope hardware; assert gyro toggle is disabled with localized hardware hint.
     • Verify 9-language localization parity (`values*/strings_devices.xml` and `strings.xml`) with zero missing keys or broken specifiers.
  6. *Targeted Unit & Regression Suite Execution (`TST-SEN-028.6`)*:
     • `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.odometry.*"` asserting 100% pass rate.


### TST-FIL-016: **Kinematic Terrain-Slope Altitude Fusion Engine Verification Suite** [Verified]
- **Requirement(s)**: `REQ-FIL-016` (`REQ-FIL-016.1`..`REQ-FIL-016.5`)
- **Component**: `KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, `DeviceType.java`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `EditDeviceDialog.kt`
- **Jira**: `ATT-6279`
- **Test Description**:
  1. *Kinematic Altitude & Slope EKF 2-State & 3-State Simulation Test (`TST-FIL-016.1`, `KinematicAltitudeSlopeEkfTest.kt`)*:
     • In 2-State mode ($[h_k, g_k]^T$), feed constant $+8\%$ slope at $5\text{ m/s}$; assert $h_k$ advances kinematically via $dh = g \cdot ds$ and estimated grade tracks $+8\%$.
     • Simulate standstill ($v=0, \Delta s = 0$) with drifting barometric pressure; assert $Q_g = 0$ and altitude is clamped with zero false climb accumulation.
     • In 3-State mode ($[h_k, g_k, b_{baro,k}]^T$), simulate pocket heating bias drift ($+0.2\text{ m/min}$) anchored by 1m-stepped DEM measurements; assert $b_{baro}$ absorbs the drift and estimated elevation remains smooth without staircase jumps.
  2. *DeviceType Registration & Schema Version 12 Migration Test (`TST-FIL-016.2`, `DevicesDatabaseManagerMigrationV12Test.kt`)*:
     • Verify `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` exists and maps correctly to `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
     • Create in-memory `Devices.db` at version 11; upgrade to 12 and assert row inserted with `PAIRED = 0`. Verify `DatabaseSchemaRegistry` parity.
  3. *MySensorManager Priority Hierarchy & Fallback Test (`TST-FIL-016.3`, `AltitudeSlopePriorityTest.kt`)*:
     • When `ALTITUDE_AND_SLOPE_ATT_FUSED` is paired and receiving, assert it is selected as `bestSensor` for `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
     • When unpaired, assert priority cleanly reverts to `VerticalSpeedAndSlopeDevice` and standard altitude hierarchy.
  4. *AltitudeAndSlopeDevice_ATTFused Driver Lifecycle Test (`TST-FIL-016.4`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Verify driver registers listeners for barometric, DEM, and speed sensors.
     • Feed simulated pressure, DEM, and speed samples; verify EKF updates and emission of fused altitude, slope %, vertical speed m/h, ascent, and descent.
     • Verify clean teardown in `shutDown()` with zero leaked receivers.
  5. *EditDeviceDialog Educational Card & Source Guards Test (`TST-FIL-016.5`, `EditDeviceDialogAltitudeGuardsTest.kt`)*:
     • Verify educational "How It Works" card is rendered for `ALTITUDE_AND_SLOPE_ATT_FUSED`.
     • Simulate missing barometric sensor; assert barometer toggle is disabled with localized hint.
     • Simulate unpaired DEM; assert DEM toggle is disabled with localized hint.
     • Verify 9-language localization parity across all 9 supported locales with zero missing strings.
  6. *Targeted Unit & Regression Suite Execution (`TST-FIL-016.6`)*:
     • `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.odometry.*"` asserting 100% pass rate.

### TST-FIL-017: Extended Rauch-Tung-Striebel (ERTS/RTS) Zero-Phase Smoothing Verification Suite [Verified]
- **Requirement(s)**: `REQ-FIL-017` (`REQ-FIL-017.1`..`REQ-FIL-017.5`)
- **Component**: `ExtendedRtsOdometrySmoother.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `ErtsPostWorkoutRecomputer.kt`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, `TrackerService.java`
- **Jira**: `ATT-7750`
- **Test Description**:
  1. *Unicycle Odometry Extended RTS 5-State Simulation Test (`TST-FIL-017.1`, `ExtendedRtsOdometrySmootherTest.kt`)*:
     • Curved Tunnel Outage Replay: Simulate 45s $90^\circ$ curved tunnel at $10\text{ m/s}$ with IMU yaw-rate and wheel speed, followed by a 15m GNSS position jump at exit. Verify the backward RTS pass eliminates the step at the exit and smoothly bends the trajectory through the tunnel without jagged discontinuities.
     • Doppler Dropout Replay: Simulate 10s canopy Doppler speed dropout ($v_{GNSS} = 0$). Verify smoothed speed $v_{k|N}$ remains smooth and continuous at $\sim 10\text{ m/s}$.
     • Performance Benchmark: Replay 14,400 1-Hz synthetic samples (4-hour ride); verify the complete forward-backward pass finishes in $< 100\text{ ms}$.
  2. *Kinematic Altitude & Slope RTS Simulation Test (`TST-FIL-017.2`, `ExtendedRtsAltitudeSlopeSmootherTest.kt`)*:
     • Delayed Anchor Convergence: Simulate hill climb (+8% grade) where barometric altitude drifts by $+20\text{ m}$ in first 60s before DEM lock stabilizes. Verify RTS backward pass propagates DEM anchor backward, eliminating initial elevation bias.
     • Standstill Clamping: Simulate 5-minute coffee stop ($v=0$) with barometric pressure drop ($1\text{ hPa} \approx 8\text{ m}$). Verify smoothed elevation remains flat and false climb accumulation is 0.0 m.
  3. *Strict Column Scope Invariant & Database Recomputation Test (`TST-FIL-017.3`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Column Invariant Verification: Populate SQLite `WorkoutSamples.db` table with canonical columns and 6 device columns (`LATITUDE (GPS)`, `SPEED_mps (GPS)`, `SPEED_mps (Google Fused)`, `SPEED_mps (att_fused)`, `ALTITUDE (Höhe aus Luftdruck)`, `ALTITUDE (Topografische Höhe (DEM))`). Run `ErtsPostWorkoutRecomputer.recomputeWorkout(...)`.
     • Assert all device-attributed columns are byte-for-byte identical before and after recomputation.
     • Assert canonical `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `ALTITUDE`, `SLOPE`, `ASCENT`, `DESCENT` are updated with smoothed values.
     • Assert `WorkoutSummaries.db` polyline (simplified via `PolyUtil.simplify(validLatLngs, 10.0)` yielding `2` endpoints on a 30-sample collinear straight-line track per `REQ-FIL-027.4`), streams (subsampled at `WorkoutSummaries.ENCODING_STEP_SIZE = 20` parity with `TrackerService.finalizeLiveSession()`), distance, and extrema are refreshed.
  4. *TrackerService Lifecycle & Toggle Gating Test (`TST-FIL-017.4`, `TrackerServiceErtsFinalizationTest.kt`)*:
     • Finalize workout with toggle disabled: verify `ErtsPostWorkoutRecomputer` is never invoked.
     • Finalize workout with toggle enabled: verify `ErtsPostWorkoutRecomputer` runs asynchronously and dispatches `refreshWorkout`.
  5. *Expert Settings UI & 9-Language Localization Contract Test (`TST-FIL-017.5`, `AdvancedTuningErtsContractTest.kt`)*:
     • Verify `TuningPreferencesDataStore` default value is `false` for `KEY_POST_WORKOUT_ERTS_SMOOTHING`.
     • Verify toggle item rendered in `SensorsGpsFilterSection` and `AdvancedTuningScreen`.
     • Verify 9-language localization parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
  6. *Targeted Unit & Regression Suite Execution (`TST-FIL-017.6`)*:
     • Full regression check across odometry, database, and settings packages.

  6. *Targeted Unit & Regression Suite Execution (`TST-FIL-017.6`)*:
     • `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.*" --tests "com.atrainingtracker.trainingtracker.database.*"` asserting 100% pass rate.


### TST-TRK-077: Indoor GPS Suppression, External Speed Sensor Coupling & IMU Rest-Variance Stationary Pinning Verification [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-077` (`REQ-TRK-077.1`..`REQ-TRK-077.4`)
- **Component**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDeviceStationaryJitterTest.kt`, `SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`
- **Test Type**: Unit & Contract
- **Verification Method**: Unit
- **Procedure**:
  1. *Indoor Mode GPS Suppression & Distance Freezing Test (`TST-TRK-077.1`, `SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`)*:
     • Enable indoor mode (`device.setIndoorMode(true)`).
     • Deliver initial location fix; verify `isStationaryPinned == true` and anchor is set.
     • Deliver series of erratic multipath location fixes ($30\text{ m}$ jump, $v = 5\text{ m/s}$, accuracy $= 10\text{ m}$); assert `isStationaryPinned == true`, `getDistanceSensor().getValue() == 0.0`, and published coordinates remain clamped to anchor.
  2. *External Wheel/Footpod Standstill Coupling Test (`TST-TRK-077.2`, `SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`)*:
     • Configure mock `MySensorManager` with active external speed sensor (`BIKE_SPEED`) reporting $0.0\text{ m/s}$ with timestamp $t_{\text{now}}$.
     • Deliver location fixes with coordinate jumps exceeding `escapeRadius` ($> 1.5 \times \text{accuracy}$); assert `shouldUnpin` is blocked, `isStationaryPinned == true`, and distance does not advance.
     • Update external sensor to $5.0\text{ m/s}$; assert unpinning engages immediately upon motion.
     • Stale sensor test: advance time by $6000\text{ ms}$ without external updates; assert stale sensor does not block normal GPS movement unpinning.
  3. *Accelerometer Rest-Variance Gating & Rapid Motion Release Test (`TST-TRK-077.3`, `SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`)*:
     • Feed accelerometer samples with static noise floor ($\sigma^2 \le 0.05\text{ (m/s}^2)^2$); assert `isAccelerometerAtRest() == true`.
     • Deliver noisy Doppler location fixes; assert stationary pinning holds.
     • Feed dynamic movement accelerometer samples ($\sigma^2 \ge 0.5\text{ (m/s}^2)^2$); assert `isAccelerometerAtRest() == false` and device unpins within $\le 1.0\text{ s}$.
  4. *Mock Replay & Baseline Compatibility Test (`TST-TRK-077.4`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
     • Execute existing `SpeedAndLocationDeviceStationaryJitterTest` suite with external sensors and accelerometer absent; assert 100% pass rate with zero regression in 3-tick hysteresis, deadband gating, and start-line pinning.
  5. *Targeted Unit & Regression Suite Execution (`TST-TRK-077.5`)*:
     • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*"` asserting 100% pass rate.


### TST-SEN-029: Front/Rear Wheel Speed Sensor Mounting Position Tracking, Selection Dialog & Dual-Sensor Guidance Verification Suite [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-SEN-029` (`REQ-SEN-029.1`..`REQ-SEN-029.4`)
- **Component**: `EquipmentDbHelper.java`, `WheelPosition.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, `EquipmentSensorMatrixScreen.kt`, `EditDeviceDialog.kt`, `strings_equipment.xml`, `docs/guide/`
- **Test Type**: Unit & Contract
- **Verification Method**: Unit
- **Procedure**:
  1. *SQLite v3 $\rightarrow$ v4 Schema Migration & Position Persistence Test (`TST-SEN-029.1`, `EquipmentDbHelperMigrationTest.kt`)*:
     • Create in-memory/temp SQLite database with v3 schema (without `wheel_position`).
     • Insert legacy equipment and link entries.
     • Execute `EquipmentDbHelper.onUpgrade(db, 3, 4)`.
     • Verify column `wheel_position` exists and existing links evaluate as `WheelPosition.UNSPECIFIED`.
     • Insert new link with `WheelPosition.FRONT` and `WheelPosition.REAR`; verify persistence and retrieval.
  2. *EquipmentRepository & Bike Duplicate Guardrail Swap Test (`TST-SEN-029.2`, `EquipmentRepositoryWheelPositionTest.kt`)*:
     • Link sensor 1 to bike A as `FRONT`.
     • Link sensor 2 to bike A as `FRONT` with swap enabled; assert sensor 2 is `FRONT` and sensor 1 is updated to `REAR` or `UNSPECIFIED`.
     • Assert `EquipmentRepository.equipmentLinksChanged` emits affected device IDs.
  3. *Compose Matrix Wheel Position Dialog & Badge Rendering Test (`TST-SEN-029.3`, `EquipmentSensorMatrixWheelPositionTest.kt`)*:
     • Render `EquipmentSensorMatrixScreen` with bike and wheel speed sensors (`BIKE_SPEED`).
     • Tap cell; assert mounting position selection dialog is displayed.
     • Select *Vorderrad*; verify cell renders short badge (`V`/`F`) alongside checkmark without text clipping within `SENSOR_COLUMN_WIDTH` (88 dp, `Rule 84`).
     • Tap non-wheel sensor (cadence pod); verify standard 1-tap checkbox toggles without dialog.
  4. *9-Language Localization & Format Parity Audit (`TST-SEN-029.4`, `TranslationParityTest.kt`)*:
     • Assert all newly introduced string resources (`equipment_wheel_position_*`, `equipment_guidance_*`) exist across all 9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
     • Verify matching format specifiers and zero missing entries.
  5. *In-App Guidance & User Guide Markdown Audit (`TST-SEN-029.5`)*:
     • Verify presence of contextual explanation card in `EquipmentSensorMatrixScreen` and `EditDeviceDialog`.
     • Verify Section 3.3 in `docs/guide/en/03-equipment-sensor-matrix.md` and `docs/guide/de/03-equipment-sensor-matrix.md`.
  6. *Targeted Unit & Regression Suite Execution (`TST-SEN-029.6`)*:
     • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.Equipment*" --tests "com.atrainingtracker.trainingtracker.ui.equipment.*"` asserting 100% pass rate.


### TST-SEN-030: Front/Rear Wheel Circumference Time-Series History & Dual-Wheel iTPMS Verification Suite [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-SEN-030` (`REQ-SEN-030.1`..`REQ-SEN-030.4`)
- **Component**: `DualWheelTpmsEstimator.kt`, `IndirectTpmsEstimator.kt`, `BANALServiceRepository.kt`, `EquipmentDbHelper.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, `WheelCircumferenceHistoryChart.kt`, `strings.xml`
- **Test Type**: Unit & Contract
- **Verification Method**: Unit
- **Procedure**:
  1. *Dual-Wheel Ratio Slow-Puncture Detection Test (`TST-SEN-030.1`, `DualWheelTpmsEstimatorTest.kt`)*:
     • Feed baseline straight-line revolutions for front and rear wheels ($C_{front} = 2.100\text{ m}$, $C_{rear} = 2.100\text{ m}$); assert $\rho_{start} = 1.0$ is locked after $300\text{ m}$.
     • Simulate rear tire slow puncture (deflate rear circumference by $1.0\%$ over $250\text{ m}$); assert `DualWheelTpmsAlert.RearPuncture` triggered within $\le 250\text{ m}$.
     • Simulate front tire slow puncture (deflate front circumference by $1.0\%$ over $250\text{ m}$); assert `DualWheelTpmsAlert.FrontPuncture` triggered within $\le 250\text{ m}$.
     • Hairpin cornering rejection: feed high yaw-rate ($|\omega_{ENU,z}| = 15^\circ/\text{s}$); assert turn samples are discarded and zero false alerts are triggered.
  2. *Concurrent Multi-Sensor Telemetry Routing Test (`TST-SEN-030.2`, `BANALServiceRepositoryMultiWheelTpmsTest.kt`)*:
     • Connect two active wheel speed sensors (`FRONT` and `REAR`).
     • Verify both sensors instantiate independent `IndirectTpmsEstimator` instances and concurrently feed `DualWheelTpmsEstimator`.
  3. *Wheel Circumference History SQLite Schema & Persistence Test (`TST-SEN-030.3`, `WheelCircumferenceHistoryDbTest.kt`)*:
     • Execute `EquipmentDbHelper.onUpgrade(db, 4, 5)`; assert table `WheelCircumferenceHistory` created.
     • Insert post-workout records for `FRONT` and `REAR`; assert query by `equipmentId` returns ordered historical series.
  4. *Bike Equipment UI Circumference Chart & Baseline Reset Test (`TST-SEN-030.4`, `WheelCircumferenceHistoryChartTest.kt`)*:
     • Render `WheelCircumferenceHistoryChart` with historical records for Front and Rear.
     • Verify dual curves render without text clipping (`Rule 84`).
     • Click "Reifen aufgepumpt" baseline reset; assert repository updates baseline.
  5. *9-Language Localization & Format Parity Audit (`TST-SEN-030.5`, `TranslationParityTest.kt`)*:
     • Assert all newly introduced string resources (`itpms_front_wheel_*`, `itpms_rear_wheel_*`, `itpms_circumference_history_*`) exist across all 9 locales.
  6. *Targeted Unit & Regression Suite Execution (`TST-SEN-030.6`)*:
     • `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.tpms.*" --tests "com.atrainingtracker.trainingtracker.database.Equipment*"` asserting 100% pass rate.


### TST-FIL-018: Epoch-Bounded Barometric Calibration & Case B Average Consistency Suite [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-018` (`REQ-FIL-018.1`..`REQ-FIL-018.3`)
- **Component**: `WorkoutSamplesDatabaseManagerTest.kt`, `LiveWorkoutSessionAltitudeDriftTest.kt`, `TrackerServiceCrashRecoveryCharacterisationTest.kt`
- **Test Type**: Unit & Contract
- **Verification Method**: Unit
- **Procedure**:
  1. *Non-Barometric Column Isolation & Canonical Source Match Test (`TST-FIL-018.1`, `WorkoutSamplesDatabaseManagerTest.kt`)*:
     • Create `WorkoutSamples` table containing `ALTITUDE`, `"ALTITUDE (Höhe aus Luftdruck)"`, `"ALTITUDE (Topografische Höhe (DEM))"`, `"ALTITUDE (GPS)"`, `"ALTITUDE (Google Fused)"`, and `"ALTITUDE (ATT Fused (Höhe und Steigung))"`.
     • Populate rows where canonical `ALTITUDE` equals DEM vs rows where canonical `ALTITUDE` equals barometric pressure, invoke `applyAltitudeDriftCorrection(...)`, and assert DEM/GPS/Google Fused/ATT Fused columns are unchanged while canonical `ALTITUDE` shifts exclusively on barometric-sourced rows.
  2. *Post-Crash Process-Epoch Lower & Upper Bound Test (`TST-FIL-018.2`, `WorkoutSamplesDatabaseManagerTest.kt` & `LiveWorkoutSessionAltitudeDriftTest.kt`)*:
     • Insert pre-crash calibrated rows (`TIME < sessionEpochStartTimeMs`), uncalibrated post-restart rows in `[sessionEpochStartTimeMs, t1Ms]`, and post-calibration rows (`TIME > t1Ms`).
     • Invoke Case A (`t0Ms = -1L`) with `sessionEpochStartTimeMs`; assert ONLY rows in `[sessionEpochStartTimeMs, t1Ms]` shift while pre-crash and post-calibration rows remain untouched.
     • Verify `LiveWorkoutSession.rehydrateStreams()` + `setSessionEpochStartTimeMs()` protects pre-crash `sampledAltitudes` and pre-epoch `RunningStats` during Case A.
  3. *1-Hz vs 20-s Case B Average Altitude Dimensional Consistency Test (`TST-FIL-018.3`, `LiveWorkoutSessionAltitudeDriftTest.kt`)*:
     • Feed 100 1-Hz `ALTITUDE` samples around `445.0 m` and 5 corresponding 20-second `sampledAltitudes` across `[500 ms..4500 ms]`.
     • Execute `applyRetroactiveAltitudeCorrection(offset = +40.0, t0Ms = 1500L, t1Ms = 3500L)`.
     • Assert `altStats.count == 100` and `altStats.getAverage()` equals `445.0 + meanCorrection` (within `0.5 m`, never ~`22 m`).
  4. *9-Locale Pressure Device Name Parity Verification (`TST-FIL-018.4`)*:
     • Verify zero new strings added and existing `R.string.devices_altitude_from_pressure` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) plus legacy `ATT-5073` names are recognized as barometric columns.
  5. *Targeted Tier-1 Unit & Contract Verification (`TST-FIL-018.5`)*:
     • Run targeted Gradle suite (`WorkoutSamplesDatabaseManager*`, `LiveWorkoutSession*`, `AltitudeFromPressure*`, `TrackerServiceCrashRecoveryCharacterisationTest`, `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`) + `:app:lintDebug` + scoped `:app:detekt`.

### TST-FIL-019: Post-Workout ERTS Fix Gating, Origin Anchoring & Sanity Guard Verification Suite [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-019` (`REQ-FIL-019.1`..`REQ-FIL-019.4`)
- **Component**: `ExtendedRtsOdometrySmootherTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`, `WorkoutSamplesDatabaseManagerTest.kt`
- **Test Type**: Unit & Regression
- **Verification Method**: Unit
- **Procedure**:
  1. *Nullable Coordinate Support & First Valid Fix Origin Test (`TST-FIL-019.1`, `ExtendedRtsOdometrySmootherTest.kt`)*:
     • Construct sample sequence with leading `null` / `(0.0, 0.0)` coordinates followed by valid GNSS coordinates at Schönaich (`48.6567°, 9.0545°`).
     • Invoke `ExtendedRtsOdometrySmoother.smoothSamples()`. Assert `refLat`/`refLon` anchors to the first valid fix, no velocity shock or spiral oscillation occurs, and smoothed coordinates remain localized near Schönaich.
     • Pass sequence with 100% `null` GNSS coordinates; verify smoother returns safely without exception.
  2. *Mid-Workout GNSS Gap Predict-Only Ingestion Test (`TST-FIL-019.2`, `ExtendedRtsOdometrySmootherTest.kt`)*:
     • Construct 60-second trajectory with a 15-second GNSS gap (`latitude = null, longitude = null`) with continuous wheel speed (10 m/s).
     • Assert forward EKF executes prediction + wheel speed update during the gap without `updateGnssPosition(0.0, 0.0)` and backward RTS produces a continuous, smooth trajectory across the gap.
  3. *Leading/Trailing NULL Preservation & Map Polyline Exclusion Test (`TST-FIL-019.3`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Recompute workout with 5 leading `NULL` coordinates followed by 25 collinear valid GNSS fixes in `WorkoutSamples.db`.
     • Assert leading rows retain `LATITUDE = NULL` and `LONGITUDE = NULL` in `WorkoutSamples.db` (never `0.0`), `mapPolyline` contains zero `(0.0, 0.0)` points and simplifies via `PolyUtil.simplify(validLatLngs, 10.0)` (`<= 3` simplified points instead of unsimplified `25` points, per `REQ-FIL-027.4`), streams follow `WorkoutSummaries.ENCODING_STEP_SIZE` (`20`) parity, and `WorkoutSummaries.db` `START` extrema reflects the first genuine GNSS fix.
  4. *Output Sanity Guard Anomaly Abort Test (`TST-FIL-019.4`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Simulate corrupted smoother output (e.g. distance $> 1,000\text{ km}$ or speed $> 100\text{ m/s}$).
     • Assert `recomputeWorkout()` rejects the commit, original live `WorkoutSamples.db` and `WorkoutSummaries.db` values remain completely intact, and an error is logged.
  5. *Targeted Tier-1 Unit & Contract Verification (`TST-FIL-019.5`)*:
     • Run targeted Gradle suite (`ExtendedRtsOdometrySmootherTest`, `ErtsPostWorkoutRecomputerTest`, `WorkoutSamplesDatabaseManager*`, `SystemLiveTrackingRequirementsContractTest`) + living documentation governance verification (`python3 tools/verify_requirement_governance.py`).

### TST-FIL-020: MSL Normalization, Reference Precedence, and Elevation Hysteresis Verification Suite [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-020` (`REQ-FIL-020.1`..`REQ-FIL-020.3`)
- **Component**: `AltitudeAndSlopeDevice_ATTFusedTest.kt`, `AltitudeAndSlopeDeviceHysteresisTest.kt`
- **Test Type**: Unit & Regression
- **Verification Method**: Unit
- **Procedure**:
  1. *DEM Reference Precedence & WGS84 MSL Normalization Test (`TST-FIL-020.1`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Configure `AltitudeAndSlopeDevice_ATTFused` with simultaneous DEM (500.0 m) and GPS (547.5 m) feeds.
     • Assert DEM takes precedence and reference altitude fed to EKF remains 500.0 m without opposing kicks.
     • Feed GPS with `LocationCompat.hasMslAltitude == true`; verify MSL altitude is extracted and applied when DEM is inactive.
  2. *5-Hz Barometric Micro-Ripple Hysteresis Rejection Test (`TST-FIL-020.2`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Feed 300 samples (60s at 5 Hz) of flat elevation (500.0 m) with Gaussian/sinusoidal noise $\le \pm 0.4\text{ m}$.
     • Assert cumulative ascent and descent both remain exactly 0.0 m.
  3. *True Terrain Climbing Ascent Accumulation Test (`TST-FIL-020.3`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Feed continuous 25 m climb from 500.0 m to 525.0 m with superposed ±0.2 m noise.
     • Assert cumulative ascent records exactly 25.0 m (within 0.5 m tolerance).
  4. *Locale-Invariant Column Identifier & UI Name Compatibility Test (`TST-FIL-020.4`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Verify `device.getName()` returns `att_fused_altitude_slope` independently of default locale.
     • Verify `DevicesDatabaseManager` continues to resolve localized names across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Targeted Tier-1 Unit & Contract Verification (`TST-FIL-020.5`)*:
     • Run targeted Gradle suite (`AltitudeAndSlopeDevice_ATTFusedTest`, `AltitudeSlopeTest`, `SystemLiveTrackingRequirementsContractTest`) + living documentation governance verification (`python3 tools/verify_requirement_governance.py`).

### TST-TRK-078: **Mid-Workout Crash Recovery Accumulator Preservation and Continuity Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-078` (`REQ-TRK-078.1`..`REQ-TRK-078.4`)
- **Component**: `TrackerServiceResumeTest.kt`, `TrackerServiceCrashRecoveryCharacterisationTest.kt`, `MyAccumulatorSensorTest.kt`
- **Test Type**: Unit & Regression
- **Verification Method**: Unit
- **Procedure**:
  1. *Omission of Reset Intent on Resume Test (`TST-TRK-078.1`, `TrackerServiceResumeTest.kt`)*:
     • Simulate `onStartCommand()` with `StartType.RESUME_SERVICE_RECREATION` and `mBanalService == null`, followed by `onServiceConnected()`. Verify `sentBroadcasts` does NOT contain `BANALService.RESET_ACCUMULATORS_INTENT`.
  2. *Resumed Workout State Elevation & Active Lap Rehydration Test (`TST-TRK-078.2`, `TrackerServiceResumeTest.kt`)*:
     • Verify `getResumedWorkoutState()` extracts `ASCENDING`, `DESCENDING`, and computes active lap duration and distance when multiple laps exist or single lap exists.
  3. *Full Sensor Baseline Seeding Verification Test (`TST-TRK-078.3`, `TrackerServiceResumeTest.kt`)*:
     • Verify `applyResumedState()` calls `BANALService.setInitialSensorValue` for `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP` with exact state values.
  4. *Late-Binding Sensor Initial Value Inheritance Test (`TST-TRK-078.4`, `MyAccumulatorSensorTest.kt`)*:
     • Verify newly instantiated `MyAccumulatorSensor` initializes `mInitialValue` from `MySensorManager.getInitialValue(sensorType)`.
  5. *Monotonic Continuity Across Recovery Boundary Characterisation Test (`TST-TRK-078.5`, `TrackerServiceCrashRecoveryCharacterisationTest.kt`)*:
     • Verify end-to-end simulation where pre-crash workout state produces subsequent sample values monotonically non-decreasing (`v >= v_pre`) for time, distance, and elevation.
  6. *Targeted Tier-1 Unit & Contract Verification (`TST-TRK-078.6`)*:
     • Run targeted Gradle suite (`TrackerServiceResumeTest`, `TrackerServiceCrashRecoveryCharacterisationTest`, `TrackerServiceLapTest`, `MyAccumulatorSensorTest`) asserting 100% pass rate.

### TST-TRK-079: **DataStore Singleton Lifecycle, Offscreen Map Disposal & Lifecycle Gating Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-079` (`REQ-TRK-079.1`..`REQ-TRK-079.3`)
- **Component**: `TuningPreferencesDataStoreLifecycleTest.kt`, `TrackingViewModelMapStateTest.kt`, `TrackingTabsScreenLayoutTest.kt`
- **Test Type**: Unit & Lifecycle
- **Verification Method**: Unit
- **Procedure**:
  1. *DataStore Singleton & Application Context Normalization Test (`TST-TRK-079.1`, `TuningPreferencesDataStoreLifecycleTest.kt`)*:
     • Verify calling `TuningPreferencesDataStore.getInstance(activityContext)` returns identical singleton reference across multiple invocations and stores `applicationContext`.
     • Verify all 19 production call sites route through `getInstance(context)`.
  2. *Bounded Viewport Page Count & Inactive Tab Map Guard Test (`TST-TRK-079.2`, `TrackingTabsScreenLayoutTest.kt`)*:
     • Verify `HorizontalPager.beyondViewportPageCount` is bounded (`0` or `1`) rather than `trackingViews.size + 1`.
     • Verify `TrackingMapContainer` renders a lightweight placeholder or skips `GoogleMap` composition when `isTabActive == false`.
  3. *Lifecycle-Gated Map State Emission Test (`TST-TRK-079.3`, `TrackingViewModelMapStateTest.kt`)*:
     • Simulate `Lifecycle.State.STOPPED` / background transition; verify live `TrackingMapState` emissions pause and resume when returning to `Lifecycle.State.STARTED`.
  4. *TrackerService Background Persistence Neutrality Test (`TST-TRK-079.4`, `TrackerServiceResumeTest.kt` / `TrackerServiceIntegrationTest.kt`)*:
     • Verify `TrackerService` 1-Hz database sample insertion continues independently when UI is in `STOPPED` state.
  5. *Tier 1 Targeted Unit & Contract Verification (`TST-TRK-079.5`)*:
     • Run targeted Gradle suite (`TuningPreferences*`, `TrackingViewModel*`, `TrackingTabs*`) asserting 100% pass rate.



### TST-TRK-080: **Mid-Workout Telemetry, Live Map Track & Sparkline Rehydration Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-7877`
- **Requirement(s)**: `REQ-TRK-080` (`REQ-TRK-080.1`..`REQ-TRK-080.4`)
- **Component**: `BANALServiceRepositoryTest.kt`, `LiveSparklineBufferTest.kt`, `TrackerServiceResumeTest.kt`
- **Test Type**: Unit, Concurrency & Regression
- **Verification Method**: Unit
- **Procedure**:
  1. *Asynchronous Rehydration Gating & StartType Neutrality Test (`TST-TRK-080.1`, `BANALServiceRepositoryTest.kt`)*:
     • Verify observing `TrackingMode.TRACKING` / `PAUSED` with active workout ID triggers async rehydration on `Dispatchers.IO`.
     • Verify observing `TrackingMode.STOPPED` or fresh workout start (`START_NORMAL`) does NOT invoke database rehydration.
  2. *Live Map Track & PathPoint Rehydration Parity Test (`TST-TRK-080.2`, `BANALServiceRepositoryTest.kt`)*:
     • Mock `WorkoutSamplesDatabaseManager` returning historical GPS rows (`LATITUDE`, `LONGITUDE`, `DISTANCE_m`, `ALTITUDE`).
     • Verify `_currentTrack` and `_currentPathPoints` are populated via `SnapshotChunkedList.from()` matching persisted coordinates and elevations exactly.
  3. *LiveSparklineBuffer Backfill & Capacity Clamping Test (`TST-TRK-080.3`, `LiveSparklineBufferTest.kt`)*:
     • Instantiate `LiveSparklineBuffer(maxCapacity = 300)`. Call `backfillHistoricalSamples` with 400 floats.
     • Assert buffer contains only the most recent 300 samples, preserving chronological order and `currentSpanSeconds == 300`.
  4. *Concurrent Live Tick Merge & Race Condition Guard Test (`TST-TRK-080.4`, `LiveSparklineBufferTest.kt` & `BANALServiceRepositoryTest.kt`)*:
     • Simulate live `addSample()` / location update occurring simultaneously with historical backfill across concurrent threads.
     • Assert thread safety (`synchronized(lock)`), monotonic timestamp ordering, and absence of data loss or buffer corruption.
  5. *Tier 1 Targeted Unit & Concurrency Verification Suite (`TST-TRK-080.5`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryTest" --tests "com.atrainingtracker.trainingtracker.ui.sparkline.LiveSparklineBufferTest"`.



### TST-TRK-081: **Per-Tile Cockpit Sparkline Graph Color Persistence and Minimalist Color Editor UI Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-7760`
- **Requirement(s)**: `REQ-TRK-081` (`REQ-TRK-081.1`..`REQ-TRK-081.5`)
- **Component**: `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsDbMigrationV22Test.kt`, `TrackingViewsRepositoryTest.kt`, `EditSensorFieldViewModelTest.kt`, `SensorFieldViewTest.kt`, `TranslationParityTest.kt`
- **Test Type**: Unit, Database Migration, ViewModel, Localization & Screenshot
- **Verification Method**: Unit
- **Procedure**:
  1. *Database Schema v22 Migration & Backup Compatibility Test (`TST-TRK-081.1`, `TrackingViewsDbMigrationV22Test.kt`)*:
     • Create a v21 database with existing layouts containing `ShowGraph` and `GraphHorizonS`. Run SQLite upgrade to v22.
     • Verify `GraphColor` column exists with default `'GLOBAL_DEFAULT'`. Verify existing rows retain layout data and receive default.
     • Verify `.attbackup` export includes `graphColor` and import with missing `graphColor` key safely defaults to `"GLOBAL_DEFAULT"`.
  2. *Repository & State Flow Threading Test (`TST-TRK-081.2`, `TrackingViewsRepositoryTest.kt`)*:
     • Insert, update, and query `SensorFieldConfig` with various `graphColor` values (`"GLOBAL_DEFAULT"`, `"THEME_DEFAULT"`, `"DYNAMIC_ZONE"`, `"CYAN"`).
     • Verify `SensorFieldState.graphColor` reflects persisted value.
  3. *ViewModel State & Scope Toggle Logic Test (`TST-TRK-081.3`, `EditSensorFieldViewModelTest.kt`)*:
     • Test color selection with scope `This tile`: asserts only field config `graphColor` is updated.
     • Test color selection with scope `All <Metric>`: asserts `TelemetryGraphColorPreferences` is updated for that sensor type, and field config `graphColor` is set to `"GLOBAL_DEFAULT"`.
     • Test dynamic zone visibility gating: asserts `isDynamicZoneSupported` is true for `HR`, `POWER`, `HRV_DFA_A1`, and false for `SPEED_mps`, `CADENCE`, `ALTITUDE`.
  4. *Sparkline Color Resolution Test (`TST-TRK-081.4`, `SensorFieldViewTest.kt`)*:
     • Verify sparkline line color resolution across `"GLOBAL_DEFAULT"`, `"THEME_DEFAULT"`, `"DYNAMIC_ZONE"`, and explicit palette tokens in Light and Dark themes.
  5. *9-Language Localization & Translation Parity Test (`TST-TRK-081.5`, `TranslationParityTest.kt`)*:
     • Verify 100% translation parity, matching format specifiers, and zero missing keys across all 9 locales for all new string tokens.
  6. *Visual Compose Screenshot Verification Test (`TST-TRK-081.6`, `EditSensorFieldDialogScreenshotTest.kt`)*:
     • Render `EditSensorFieldDialog` in Light and Dark themes and capture `docs/engineering/walkthroughs/img/ATT-7760_color_picker.png` verifying Rule 8.5 4-point visual checklist.


### TST-TRK-082: **Decoupled Multi-Color Dynamic Zone Sparkline Rendering Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-7761`, `ATT-8436`
- **Requirement(s)**: `REQ-TRK-082` (`REQ-TRK-082.1`..`REQ-TRK-082.4`)
- **Component**: `SensorSparklineViewTest.kt`, `TrackingViewModelDynamicZoneSparklineTest.kt`, `SensorFieldViewDynamicZoneScreenshotTest.kt`
- **Test Type**: Unit, ViewModel & Compose Screenshot
- **Verification Method**: Unit
- **Procedure**:
  1. *Decoupled Sparkline Color Resolution Test (`TST-TRK-082.1`, `SensorSparklineViewTest.kt`)*:
     • Verify `resolveSparklineLineColor` returns `colorScheme.primary` (not `zoneColor`) when `graphColor = "GLOBAL_DEFAULT"` and `sensorType = null` regardless of text zone coloring (`showTextColor` removed from sparkline color resolution).
     • Verify fixed palette tokens, `"THEME_DEFAULT"`, `"GLOBAL_DEFAULT"`, and `"DYNAMIC_ZONE"` resolve deterministically independent of `zoneDisplayOptions.showTextColor`.
  2. *Threshold Gradient Stop Mapping, Terminal Dot & Fallback Test (`TST-TRK-082.2`, `SensorSparklineViewTest.kt`)*:
     • **HR (4 cutoffs -> Z1..Z5)** and **Power (6 cutoffs -> Z1..Z7)**: verify normalized stops in `[0f, 1f]` are monotonically non-decreasing, aligned to `minVal..maxVal` within canvas vertical padding, and terminal dot color matches `samples.last()` zone.
     • **HRV DFA α1 (inverted polarity & inverted Y-axis, ATT-8436)**: verify `alpha1 < t2` maps to `TTColor.Zone4` (red, top / `stop = 0f`), `t2 <= alpha1 <= t1` maps to `TTColor.Zone3` (orange, middle), and `alpha1 > t1` maps to `TTColor.Zone2` (green, bottom / `stop = 1f`). Verify spatial Y-coordinate inversion renders lower values at `verticalPaddingPx` (top) and higher values at `height - verticalPaddingPx` (bottom) across dynamic and fixed color modes.
     • **Dark/AMOLED Zone 7 Contrast**: verify `TTColor.Zone7` (`#000000`) substitutes a high-contrast dark-surface color when `isDarkSurface(colorScheme) == true`.
     • **Edge-Case Fallbacks**: verify graceful fallback to solid `lineColor` (null gradient stops / zero crash) for `samples.size == 1`, `range == 0f` (`minVal == maxVal`), `isPlaceholder == true`, and null/empty thresholds.
  3. *TrackingViewModel Zone Cutoff Projection & Guard Test (`TST-TRK-082.3`, `TrackingViewModelDynamicZoneSparklineTest.kt`)*:
     • Verify `TrackingViewModel` projects `HR_RUN`/`HR_BIKE` (4 cutoffs), `PWR_BIKE` (6 cutoffs), and `HRV_DFA_A1` (`t1`, `t2`) sparkline zone specs onto `SensorFieldState` only when `showGraph` and `graphColor == "DYNAMIC_ZONE"` (or resolved global dynamic mode).
     • Verify `SensorType.POWER_TO_WEIGHT` is strictly guarded against receiving raw Watt (`PWR_BIKE`) cutoffs.
     • Verify `HRV_DFA_A1` zone gradient stops are suppressed when `hrvSignalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
  4. *Visual Compose Screenshot Verification Test (`TST-TRK-082.4`, `SensorFieldViewDynamicZoneScreenshotTest.kt`)*:
     • Render `SensorFieldView` / `SensorSparklineView` tiles in Light and Dark/AMOLED themes covering HR (5-zone), Power (7-zone incl. Zone 7 dark contrast), HRV DFA α1 (inverted polarity with aerobic green at bottom and anaerobic red at top), decoupled fixed-color + `showTextColor = true`, and placeholder/flat-line fallbacks; capture `docs/engineering/walkthroughs/img/ATT-7761_dynamic_zone_sparkline.png` and certify against Rule 8.5 4-point visual checklist.


### TST-TRK-083: **Canonical SensorType Storage, Display & Export Unit Parity Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8579`
- **Requirement(s)**: `REQ-TRK-083` (`REQ-TRK-083.1`..`REQ-TRK-083.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`
- **Test Type**: Unit / Contract
- **Verification Method**: Unit
- **Procedure**:
  1. *Code-Level `internalUnit` & Javadoc Coverage Test (`TST-TRK-083.1`, `SensorTypeUnitsContractTest.kt`)*:
     • Iterate over all `SensorType.values()` (67 constants) and assert `sensorType.getInternalUnit()` is non-null, non-blank, and free of placeholder markers.
     • Parse `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java` and assert every enum constant declaration is preceded by a per-constant Javadoc block (`/** ... */`) documenting its internal unit.
  2. *Bidirectional `docs/architecture/sensor_units.md` & Cross-Link Parity Test (`TST-TRK-083.2`, `SensorTypeUnitsContractTest.kt`)*:
     • Parse `docs/architecture/sensor_units.md` data-dictionary table and assert exact bidirectional set equality (`docConstants == SensorType.values().map { it.name }.toSet()`), plus per-row agreement on `SensorCategory`, `SensorValueType`, and `internalUnit`.
     • Assert `docs/architecture.md` contains an active reference link to `architecture/sensor_units.md` and that `sensor_units.md` documents sentinel values (`BATTERY_REMAINING_TIME` `-1` / `<= -2`, `PACE_spm` `MAX_PACE`, `HRV_DFA_A1` / `POWER_TO_WEIGHT` / `W_PRIME_BAL`) and export conversions (`FitFileWriter.kt`, `TCXFileWriter.kt`, `GPXFileWriter.java`, `CSVFileWriter.java`, `GCFileWriter.java`).
  3. *Invariants & Regression Suite (`TST-TRK-083.3`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeFormatterTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeMmpTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` and verify 100% pass with zero runtime/UI regressions (Localization parity: `N/A - no UI strings`).


### TST-TRK-084: **Valid-Data `AccumulatedSensors` Persistence Filtering, Resume Rehydration & Post-Workout Backfill Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8578`
- **Requirement(s)**: `REQ-TRK-084` (`REQ-TRK-084.1`..`REQ-TRK-084.4`)
- **Component**: `TrackerServiceFinalizationTest.kt`, `TrackerServiceIntegrationTest.kt`, `TrackerServiceResumeTest.kt`, `WorkoutSummariesAccumulatedSensorsTest.kt`, `PolarH10OfflineControllerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`, `HrvPostWorkoutRecomputerTest.kt`
- **Test Type**: Unit & Integration
- **Verification Method**: Unit
- **Procedure**:
  1. *Live Non-Null Observation & Virtual-Device Silence Test (`TST-TRK-084.1`, `TrackerServiceFinalizationTest.kt`)*:
     • Run `sampleAndWriteToDb()` with `BANALServiceComm.getAllSensorData()` returning valid `TIME_TOTAL`, `SPEED_mps`, `LATITUDE`, `LONGITUDE` plus `null`-valued `HR`, `POWER`, `HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`, `POWER_5S`, `TSS`, `W_PRIME_BAL`, `LIVE_HR_DRIFT`, and `BANALServiceComm.getAccumulatedSensorTypeSet()` returning all 40 registered types.
     • Invoke `endWorkout(snapshot)` and assert `AccumulatedSensors` persists strictly the observed non-null types (`TIME_TOTAL`, `SPEED_mps`, `LATITUDE`, `LONGITUDE`) and zero unattached virtual HRV/Power/Dynamics sensors.
     • Verify `mLiveSession == null` fallback still persists `snapshot.accumulatedSensorTypes` (`TST-TRK-027.3`) and `MySensorManager.getAccumulatedSensorTypeSet()` registration behavior (`TST-TRK-022.3`) remains unchanged.
  2. *`finalizeLiveSession()` Post-Processed Metric Upsert Test (`TST-TRK-084.2`, `TrackerServiceFinalizationTest.kt`)*:
     • Execute `endWorkout()` + `finalizeLiveSession()` for a session with recorded power/HR/GPS samples producing `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`, and `LINE_DISTANCE_m`.
     • Assert `FINISHED = 1` commits before auxiliary saves (`REQ-TRK-030.1`) and `finalizeLiveSession()` upserts the non-null computed metrics into `AccumulatedSensors`.
  3. *Resume Rehydration & Idempotent DB Deduplication Test (`TST-TRK-084.3`, `TrackerServiceResumeTest.kt` & `WorkoutSummariesAccumulatedSensorsTest.kt`)*:
     • Persist initial `AccumulatedSensors` rows (`HR`, `SPEED_mps`) for a workout, run `rehydrateLiveSession()` on resume, record new `POWER` samples, and finalize; assert `AccumulatedSensors` contains `{HR, SPEED_mps, POWER}` with zero duplicate rows.
     • Invoke `saveAccumulatedSensorTypes()` / `addAccumulatedSensorTypes()` repeatedly with overlapping `SensorType` sets and assert `SELECT COUNT(*)` in `AccumulatedSensors` matches the deduplicated set size (`0` duplicates).
  4. *Polar H10 / Verity Sense Backfill & Conditional HRV Recomputation Upsert Test (`TST-TRK-084.4`, `PolarH10OfflineControllerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`, `HrvPostWorkoutRecomputerTest.kt`)*:
     • Exercise `PolarH10OfflineController` (synchronous stop backfill and deferred `syncPendingWorkout()`) and `PolarVeritySenseOfflineController` (`applyBackfillAndCommit()` and `applySwimBackfillAndCommit()`) with non-empty HR/RR samples; assert `SensorType.HR` is upserted into `AccumulatedSensors`.
     • Exercise `HrvPostWorkoutRecomputer.recomputeWorkout()` across full-HRV (`rmssdValues`, `dfaValues`, `sessionSdnn` all populated) and sparse/artifact-gated (`dfaValues` empty or `sessionSdnn == null`) cases; assert `HRV_ARTIFACT_RATE` is upserted when written and `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_SDNN` are upserted strictly when non-empty/non-null.
  5. *Tier 1 Targeted Regression Suite & Localization Parity (`TST-TRK-084.5`)*:
     • Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*" --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass (Localization parity: `N/A - no UI strings`).


### TST-FIL-021: **Post-Workout ERTS `VERTICAL_SPEED` `m/h` Conversion, Clamping & Integer Persistence Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8577`
- **Requirement(s)**: `REQ-FIL-021` (`REQ-FIL-021.1`..`REQ-FIL-021.2`)
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`
- **Test Type**: Unit / Contract
- **Verification Method**: Unit
- **Procedure**:
  1. *Positive Climb & Negative Descent `m/h` Scaling & Rounding Test (`TST-FIL-021.1`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Populate `WorkoutSamples.db` with climbing (`+0.2 m/s` / `+0.25 m/s` grade) and descending (`-0.25 m/s` / fractional `m/s`) altitude profiles alongside device-attributed columns.
     • Run `ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)`.
     • Assert canonical `VERTICAL_SPEED` rows store integer `m/h` values matching `Math.round((alt.verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))` (e.g., positive climb `~720..900 m/h` and negative descent `~-900 m/h`, never raw `m/s` fractional values), while device-attributed columns remain byte-for-byte identical.
  2. *Boundary Clamping (`[-3000, 3000] m/h`) & Null-Safety Test (`TST-FIL-021.2`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Exercise `ErtsPostWorkoutRecomputer` / `bindColumnValue()` with extreme smoothed vertical speeds (`+3.0 m/s` and `-3.0 m/s` from `ExtendedRtsAltitudeSlopeSmoother` bounds, i.e., `±10800 m/h`) and verify persisted SQLite `VERTICAL_SPEED` values are clamped to `+3000` and `-3000` `m/h` respectively.
     • Verify `alt == null` binds SQLite `NULL` into `VERTICAL_SPEED`.
  3. *Unit Contract Doc Parity & Regression Execution (`TST-FIL-021.3`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest" --tests "com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmootherTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest"` asserting 100% pass rate (`Localization parity: N/A - no UI strings`).


### TST-FIL-022: **3-State EKF Barometer Weather-Offset Anchoring & Calibration Jump Ascent Guard Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8575`
- **Requirement(s)**: `REQ-FIL-022` (`REQ-FIL-022.1`..`REQ-FIL-022.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *3-State Baro-before-Ref `+130 m` Standstill Anchoring Test (`TST-FIL-022.1`, `KinematicAltitudeSlopeEkfTest.kt`)*:
     • Configure `KinematicAltitudeSlopeEkf(is3StateMode = true)`. Feed 10 standstill barometer updates at `568.4 m`, then invoke a single `updateReferenceAltitude(438.0, DEFAULT_VAR_DEM)` followed by 20 more `568.4 m` barometer updates (both standstill and moving).
     • Assert `ekf.altitude` anchors within `438.0 ± 0.5 m`, `ekf.baroBias` converges within `130.4 ± 0.5 m`, and `p00` does not remain locked at `0.25` prior to reference arrival.
  2. *3-State Ref-before-Baro Offset & 2-State Covariance Guard Test (`TST-FIL-022.2`, `KinematicAltitudeSlopeEkfTest.kt`)*:
     • Initialize 3-state EKF with `updateReferenceAltitude(438.0, DEFAULT_VAR_DEM)`, then feed 20 moving/standstill barometer updates starting at `568.4 m` (`+130.4 m` offset). Assert `ekf.baroBias` absorbs `130.4 ± 0.5 m` and `ekf.altitude` stays within `438.0 ± 0.5 m`.
     • Verify 2-state mode (`is3StateMode = false`) continues initializing `p00 = varBaro` (`0.25`) with `ekf.p00 < 10.0` after `predict(1800.0, 5.0)`, and `reset()` / `setMode()` clears anchor state cleanly.
  3. *Device Calibration Jump Ascent/Descent Guard Test (`TST-FIL-022.3`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • With DEM (or GPS) active on `AltitudeAndSlopeDevice_ATTFused`, feed uncalibrated pressure (`~568.4 m`) before the first `438.0 m` DEM fix arrives during `calculateMetrics()`.
     • Assert `mAltitudeSensor` updates to `438.0 ± 0.5 m` and both `mAscentSensor` and `mDescentSensor` remain `0.0 m` (zero `+130.4 m` phantom descent/ascent), followed by true `25 m` climb accumulating `~25 m` ascent.
  4. *Tier 1 Targeted Regression Execution (`TST-FIL-022.4`)*:
     • Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkfTest" --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" --tests "com.atrainingtracker.trainingtracker.build.MeasuredNfrBenchmarkTest"` asserting 100% pass rate (`Localization parity: N/A - no UI strings`).

---

### TST-FIL-023: **att_fused Cold GNSS Jump Gating, Standstill Slope Clearing & Low-Speed Slope Damping Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8576`
- **Requirement(s)**: `REQ-FIL-023` (`REQ-FIL-023.1`..`REQ-FIL-023.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Cold GNSS Fix Tightening & Accuracy Preservation Test (`TST-FIL-023.1`, `SpeedAndLocationDevice_ATTFusedTest.kt` & `UnicycleOdometryEkfTest.kt`)*:
     • Feed `SpeedAndLocationDevice_ATTFused.onLocationChanged()` an initial degraded fix (`accuracy = 25.1 m`, `speed = 0.0f`) followed by a converged fix (`accuracy = 16.0 m`, `25.2 m` offset, `speed = 0.0f`).
     • Assert no `25.2 m` jump or `46.3 km/h` speed spike (`mSpeedSensor < 2.0 m/s`, `mDistanceSensor < 5.0 m`), `location.accuracy >= 16.0f`, and `UnicycleOdometryEkf.updateGnssPosition()` gates stationary/cold innovation jumps while still re-acquiring valid GNSS fixes during moving tunnel exit (`REQ-FIL-009`).
  2. *Standstill Slope Clearing Test (`TST-FIL-023.2`, `KinematicAltitudeSlopeEkfTest.kt` & `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Establish a non-zero pre-stop slope (e.g. `-21%` / `-10%` during motion), then transition to `speed = 0.0 m/s` for sustained standstill ticks (`>= 10 s`).
     • Assert `ekf.slopePercent` decays cleanly to `0%`, `mSlopeSensor` publishes `0` (not frozen at `-21`), `mAscentSensor`/`mDescentSensor` remain unchanged, and 3-state `baroBias` remains preserved (`REQ-FIL-022`).
  3. *Low-Speed Slope Damping & Real Climb Preservation Test (`TST-FIL-023.3`, `KinematicAltitudeSlopeEkfTest.kt` & `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Simulate `0.85 m/s` roll-away over flat DEM (`442.0 m`) with `±0.35 m` 5-Hz baro noise for `30 s`; assert `SLOPE` stays bounded without `+29%` spikes and `ASCENT` stays near `0.0 m`. Then simulate a real `+8%` climb at `5.0 m/s` and verify accurate slope and ascent accumulation.
  4. *Tier 1 Targeted Regression Execution (`TST-FIL-023.4`)*:
     • Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkfTest" --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkfTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceStationaryJitterTest" --tests "com.atrainingtracker.banalservice.odometry.OdometrySimulationTest"` asserting 100% pass rate with zero `src/main` source-reading calls (`REQ-PRO-049.1`; `Localization parity: N/A - no UI strings`).

### TST-CON-048: **Offline & Unified EditDeviceDialog Radar Tail Light Grid, Hydration, Wiring & StVZO Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8450`, `ATT-8678`
- **Requirement(s)**: `REQ-CON-048` (`REQ-CON-048.1`..`REQ-CON-048.4`), `REQ-CON-047` (`REQ-CON-047.2`, `REQ-CON-047.3`)
- **Component**: `EditDeviceDialog.kt`, `RadarLightModeGrid.kt`, `RadarLightController.kt`, `strings_radar.xml`, `RadarLightControllerTest.kt`, `EditDeviceDialogRadarLightTest.kt`, `RadarLightQuickSwitchSheetTest.kt`, `EditDeviceViewModelTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `DeadCodeEliminationContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Behavioral `RadarLightModeGrid` Parity, Visibility, Row-Major Bounds & Direct Controller Wiring (`TST-CON-048.1`, `EditDeviceDialogRadarLightTest.kt`, `EditDeviceViewModelTest.kt`)*:
     • Render `RadarLightControlSectionContent` via Robolectric `createComposeRule()` (`0` new `src/main` reads per `REQ-PRO-049.1`) across online/offline (`showActiveModeSubtitle = !isOffline`), StVZO/non-StVZO (`RadarLightStvzoNotice`), and Light/Dark themes.
     • Assert row-major 2-column grid bounds (`[AUTO, OFF]`, `[PELOTON, SOLID]`, `[NIGHT_FLASH, DAY_FLASH]` non-StVZO; `AUTO` full-width row 1 + `[OFF, SOLID]` row 2 StVZO) replacing legacy vertical monotonicity, `RadarLightOfflineHint` visible only when `isOffline`, `radar_light_active_mode_format` hidden when `isOffline && isAutoMode` and shown when `!isOffline && isAutoMode`, `RVR315` & connected `!isLightControlAvailable` hidden, and direct `RadarLightController.setAutoMode()` / `setManualLightMode(mode)` wiring without mutating `EditDeviceViewModel` draft state.
  2. *Offline Mode Persistence, Cold-Start Hydration, StVZO Reset & Workout Auto-Switch (`TST-CON-048.2`, `RadarLightControllerTest.kt`)*:
     • Verify offline `setManualLightMode(SOLID)` / `setAutoMode()` updates effective UI selection (`preferredMode` / `isAutoMode`) and persists to `TuningPreferencesDataStore` without highlighting `OFF`, cold-start DataStore hydration restores persisted state, `unregisterRadarDevice()` resets `_isStvzo` to `false`, and Workout Auto-Switch lifecycle transitions remain intact.
  3. *Contract Guards, Light/Dark `@Preview` Hygiene & `Rule 8.5` PNG Inspection (`TST-CON-048.3`, `EditDeviceDialogRadarLightTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `DeadCodeEliminationContractTest.kt`)*:
     • Verify `ArchitectureConformanceContractTest` and `InterfaceSignatureDriftContractTest` pass, and `DeadCodeEliminationContractTest` reports `0` new violations (`RadarLightModeGrid.kt` uses `private` previews; `4/5 PASS` with `1` pre-existing unrelated `ATT-8823` failure in `EditDeviceDialog.kt`) alongside Light/Dark/StVZO/online/offline Robolectric tests and **`Rule 8.5` Light/Dark `@Preview` PNG `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png`** inspection via `read_file`.
  4. *Localization Parity & Targeted Regression (`TST-CON-048.4`)*:
     • Confirm `Localization Parity: N/A - no UI strings` (`strings_radar.xml` untouched; existing 9-locale parity preserved), and execute targeted Gradle unit and Robolectric Compose UI tests asserting 100% pass rate.

### TST-FIL-024: **VerticalSpeedAndSlopeDevice Baro Source Guard, Accumulator Reset & Same-Device Step Re-Seeding Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8707`
- **Requirement(s)**: `REQ-FIL-024` (`REQ-FIL-024.1`..`REQ-FIL-024.4`), `REQ-FIL-012`
- **Component**: `app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java`, `app/src/test/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDeviceTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Non-Baro Canonical `ALTITUDE_CORRECTION_INTENT` Guard & Active-Baro Parity Test (`TST-FIL-024.1`, `VerticalSpeedAndSlopeDeviceTest.kt`)*:
     • Configure `SensorType.ALTITUDE` backed by `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` (or `ALTITUDE_FROM_POSITION`) via `ProxySensor.bestSensor` and invoke `mAltitudeCorrectionReceiver.onReceive(...)` with `delta = +100.0 m`. Assert `mLastAltitudeSuperFiltered` remains unchanged and `FilterManager.shiftFilter` is NOT invoked (`verify(exactly = 0)`).
     • Configure `SensorType.ALTITUDE` backed by `DeviceType.ALTITUDE_FROM_PRESSURE` and invoke `mAltitudeCorrectionReceiver.onReceive(...)` with `delta = +25.0 m`. Assert `mLastAltitudeSuperFiltered` shifts by `+25.0 m` and `shiftFilter` is invoked for both `21.0s` and `300.0s` filters (`TST-FIL-004.5` parity); verify active-baro calibration `>= 15.0 m` followed by `calculateMetrics()` before `ALTITUDE_CORRECTION_INTENT` delivery neither triggers premature `reseedFilter` nor double-shifts filters (`TST-FIL-024.1c`).
  2. *Synchronized `onAccumulatorsReset()` State Clearing Test (`TST-FIL-024.2`, `VerticalSpeedAndSlopeDeviceTest.kt`)*:
     • Populate `VerticalSpeedAndSlopeDevice` with pre-start altitude samples (`mLastAltitudeSuperFiltered`, `mAltitudeHistory`, `mLastAltitudeSourceDevice`), then invoke `onAccumulatorsReset()`.
     • Assert `mLastAltitudeSuperFiltered == null`, `mLastAltitude == null`, `mLastAltitudeSourceDevice == null`, and `mAltitudeHistoryCount == 0` / `mAltitudeHistoryHead == 0`, so subsequent startup ticks emit `0.0 m` `DESCENT` / `ASCENT`.
  3. *Same-Device Step / EKF Re-Anchor Re-Seeding & Steady-State Preservation Test (`TST-FIL-024.3`, `VerticalSpeedAndSlopeDeviceTest.kt`)*:
     • Keep `SensorType.ALTITUDE` on the same `ALTITUDE_AND_SLOPE_ATT_FUSED` device; stream initial uncalibrated `538.0 m` samples, then inject an abrupt same-device snap to `438.0 m` (`-100.0 m` DEM/GPS re-anchor) during `calculateMetrics()`. Assert `reseedAltitudeFilters(438.0)` triggers, `DESCENT`/`ASCENT` remain `0.0 m`, and `SLOPE` records zero `-203%` spike.
     • Follow with a genuine steady climb and steady descent (`0.5 m/s` / `-0.5 m/s` at `5.0 m/s` horizontal speed) and verify `ASCENT`, `DESCENT`, `VERTICAL_SPEED`, and `SLOPE` integrate accurately; verify non-finite `NaN` / `±Infinity` `delta` and `rawAlt` inputs are rejected cleanly (`TST-FIL-024.3c`).
  4. *Targeted Tier-1 Unit Regression (`TST-FIL-024.4`)*:
     • Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest" --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest"` asserting 100% pass rate with zero `src/main` reads (`REQ-PRO-049.1`; `Localization parity: N/A - no UI strings`).

### TST-FIL-025: **Post-Workout ERTS Running-Pace Heading, Yaw-Rate Anti-Aliasing, Sanity Bounds & Laps.db Sync Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8705`
- **Requirement(s)**: `REQ-FIL-025` (`REQ-FIL-025.1`..`REQ-FIL-025.4`), `REQ-FIL-007`, `REQ-FIL-017`, `REQ-FIL-019.4`, `REQ-FIL-023.1`
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmootherTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Offline Heading Initialization & Running-Pace Displacement/Bearing Anchoring Test (`TST-FIL-025.1`, `ExtendedRtsOdometrySmootherTest.kt` & `UnicycleOdometryEkfTest.kt`)*:
     • Given a simulated running track moving South/West (`~2.0 m/s`, not North `PI / 2.0`), execute `ExtendedRtsOdometrySmoother.smoothSamples()`. Assert initial heading aligns with the first valid GNSS movement chord, running-pace GNSS displacement/bearing updates keep heading aligned within tolerance, and cumulative smoothed distance matches ground truth without 18% loss.
     • Verify `UnicycleOdometryEkf.updateDopplerHeading()` at `<= 3.0 m/s` (`MIN_DOPPLER_SPEED_MPS = 3.0`) remains strictly gated for live callers (`ATT-8576` / `REQ-FIL-023.1`).
  2. *1-Hz Yaw-Rate Anti-Aliasing, Non-Negative Speed & Altitude Standstill Coupling Test (`TST-FIL-025.2`, `ExtendedRtsOdometrySmootherTest.kt`)*:
     • Inject oscillatory 1-Hz `WORLD_YAW_RATE_radps` arm-swing snapshots during straight/curved `~2.2 m/s` running. Assert heading does not invert backwards, smoothed `speedMps` never collapses negative or clamps falsely to `0.0 m/s`, and passing smoothed speeds into `ExtendedRtsAltitudeSlopeSmoother.smooth()` preserves genuine standstill clamping (`speed < 0.3 m/s`) without false ascent/descent inflation.
  3. *Symmetric ERTS Sanity Bounds & Live `att_fused` Preservation Test (`TST-FIL-025.3`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Verify `ErtsPostWorkoutRecomputer.isOdometrySane()` rejects collapsed smoothed distance (below lower divergence bound) as well as `> 3.0x` runaway distance, while accepting legitimate paused workouts; when rejected, assert `WorkoutSamples.db` live `att_fused` canonical columns, `WorkoutSummaries.db`, and `Laps.db` remain unmodified.
  4. *`Laps.db` Post-Workout Synchronization Test (`TST-FIL-025.4`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Execute `ErtsPostWorkoutRecomputer.recomputeWorkoutBlocking()` on a multi-lap Robolectric workout DB (`WorkoutSamples.db` with `LAP_NR` segments, `WorkoutSummaries.db`, `Laps.db`). Assert `Laps.TABLE` `DISTANCE_TOTAL_m` and `SPEED_AVERAGE_mps` match smoothed per-lap totals and sum cleanly to `WorkoutSummaries.DISTANCE_TOTAL_m`.
  5. *Targeted Tier-1 Offline Unit Regression (`TST-FIL-025.5`)*:
     • Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.*" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest"` (strictly offline JUnit/Robolectric/MockK with zero `src/main` reads per `REQ-PRO-049.1`; `Localization parity: N/A - no UI strings`).


### TST-TRK-085: **HRV/RR Ingestion Monotonicity, Physiological Gap Flagging, Pre-Filter Sorting & DFA α1 Gate Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8706`
- **Requirement(s)**: `REQ-TRK-085` (`REQ-TRK-085.1`..`REQ-TRK-085.4`), `REQ-CON-027`, `REQ-TRK-060`, `REQ-PER-034`
- **Component**: `BTLEHeartRateDeviceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `RrBeatTimestampReconstructionTest.kt`, `TrackerServiceRrFlushTest.kt`, `DeviceManagerRrSourceTest.kt`, `RrArtifactFilterTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `DfaCalculatorTest.kt`, `HRVMetricsDeviceTest.kt`, `HrvGoldenOracleTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Single-Primary-Source RR Routing & Monotonic Clamping Test (`TST-TRK-085.1`)*:
     • Register two concurrent `RrBeatSource` HRMs (e.g., `BTLEHeartRateDevice` + `PolarOnlineStreamingBridge`) in `DeviceManager` / `TrackerService` and assert only one active primary source feeds `TrackerService.mRrBeatListener` and `HRVMetricsDevice` without timestamp/index interleaving.
     • Trigger drift re-anchoring (`drift > 2000 ms`) with multi-beat bundles in `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, and `RrBeatTimestampReconstructor`; assert `beatTimestampMs >= lastBeatTimestampMs` strictly holds across all emitted beats.
  2. *Physiological RR Gap Flagging Test (`TST-TRK-085.2`)*:
     • Inject multi-second (`5196.29 ms`, `6377.93 ms`, `> 2500.0 ms`) and sub-minimum (`< 250.0 ms`) RR intervals arriving with `drift <= 2000 ms` (and `k = 1`) into `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, `RrBeatTimestampReconstructor`, and `RrArtifactFilter`.
     • Assert `artifactFlag == RrBeatEvent.ARTIFACT_GAP` (`5`) and `gapBeats > 0`, while normal physiological intervals (`250..2500 ms`) remain `ARTIFACT_NORMAL`.
  3. *Pre-Filter Chronological Sorting Test (`TST-TRK-085.3`, `HrvPostWorkoutRecomputerTest.kt`)*:
     • Seed `WorkoutRr.db` with out-of-order / interleaved `beat_timestamp_ms` rows and unflagged multi-second RR gaps matching the Pixel 10 `ATT-8706` failure profile.
     • Execute `HrvPostWorkoutRecomputer.recomputeWorkout()`; assert beats are sorted by `beatTimestampMs` before `RrArtifactFilter.filterBidirectionalBeats()` runs, multi-second gaps are excluded/flagged, and persisted `HRV_DFA_A1` contains zero negative values.
  4. *DFA $\alpha_1$ Validity Gate & NeuroKit2 Golden Parity Test (`TST-TRK-085.4`, `DfaCalculatorTest.kt`, `HRVMetricsDeviceTest.kt`, `HrvGoldenOracleTest.kt`)*:
     • Feed synthetic inputs producing negative OLS log-log slope into `DfaCalculator.computeDfaAlpha1()`, `HrvPostWorkoutRecomputer`, and `HRVMetricsDevice`; assert `Double.NaN` / `null` (never negative values), `DfaCalculatorTest.testConstantSignalDoesNotCrash` passes, and `HrvGoldenOracleTest` maintains `|Δα1| <= 0.02` (`<= 0.03` end-to-end) and `|ΔrMSSD| <= 0.5 ms` offline (`REQ-PRO-049.1` zero `src/main` reads; `Localization parity: N/A - no UI strings`).

### TST-SEN-031: **EditDeviceDialog Wheel Mounting Position Display, Draft State & Conflict Swap Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8719`
- **Requirement(s)**: `REQ-SEN-031` (`REQ-SEN-031.1`..`REQ-SEN-031.4`), `REQ-SEN-029`, `REQ-UI-149`
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceEquipmentWheelPositionTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModelTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepositoryEquipmentSyncTest.kt`, `TranslationParityTest.kt`
- **Test Type**: Unit & UI
- **Verification Method**: Unit
- **Procedure**:
  1. *ViewModel Draft Staging & `Cancel`-vs-`Save` Isolation Test (`TST-SEN-031.1`, `EditDeviceViewModelTest.kt` & `DeviceDataRepositoryEquipmentSyncTest.kt`)*:
     • Update `linkedEquipment` and `linkedWheelPositions` in `EditDeviceViewModel`; verify `_deviceSnapshot` holds draft positions with zero calls to `DeviceDataRepository.updateDevice()` or `EquipmentRepository.setWheelSensorLink()` until `saveChanges()`.
     • Verify `DeviceDataRepository.updateDevice()` commits position-only changes (unchanged bike list, changed `WheelPosition`) via `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)`, unlinks removed bikes cleanly, and refreshes `wheelPositionConflicts` across sibling unswapped wheel speed sensors in `allDevices`.
  2. *Compose `EquipmentSection` Display, Reused `WheelPositionSelectionDialog` & Conflict Preview Test (`TST-SEN-031.2`, `EditDeviceEquipmentWheelPositionTest.kt`)*:
     • Strictly offline Robolectric `createComposeRule()` (zero `src/main` reads per `REQ-PRO-049.1`): render `EditDeviceDialog` / `EquipmentSection` for `BIKE_SPEED` and `BIKE_SPEED_AND_CADENCE`.
     • Assert localized `Front` / `Rear` / `Unspecified` labels appear in the summary field and bike-linking sheet; tap a bike row, assert `WheelPositionSelectionDialog` opens with conflict/swap preview when another sensor already occupies the target position, select `REAR`, confirm sheet `OK` / `Cancel` draft behavior.
  3. *Non-Wheel Sensor Isolation Test (`TST-SEN-031.3`, `EditDeviceEquipmentWheelPositionTest.kt`)*:
     • Render `EquipmentSection` sheet for `HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, and `RUN_SPEED`; verify standard simple checkbox toggling without opening `WheelPositionSelectionDialog` or rendering wheel-position labels.
  4. *9-Locale Parity, Light/Dark `@Preview` PNG Verification & Targeted Regression (`TST-SEN-031.4`, `EditDeviceEquipmentWheelPositionTest.kt` & `TranslationParityTest.kt`)*:
     • Render Light & Dark `@Preview`s of `EditDeviceDialog` `EquipmentSection` / bike sheet for wheel speed sensors and visually inspect captured PNGs (`Rule 8.5`).
     • Verify 100% 9-locale parity for `strings_equipment.xml` (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) and execute targeted offline Gradle unit + Compose UI tests asserting 100% pass rate.


### TST-TRK-086: **HrvDfaA1RangeSlider HorizontalPager Gesture Isolation & Local Drag State Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8105`
- **Requirement(s)**: `REQ-TRK-086` (`REQ-TRK-086.1`..`REQ-TRK-086.3`), `REQ-TRK-061.4`
- **Component**: `HrvDfaA1RangeSliderGestureTest.kt`, `HrvDfaA1SettingsContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Bidirectional Slider Drag Isolation & Local Drag Buffering (`TST-TRK-086.1`, `HrvDfaA1RangeSliderGestureTest.kt`)*:
     • Render `ZoneSettingsScreen` (or `HorizontalPager` tab host matching `ZoneSettingsScreen` with `HrvDfaA1SettingsContent` on `page == 3`) via Robolectric Compose `createComposeRule()` with zero `app/src/main/` file reads (`REQ-PRO-049.1` / `ATT-8198`).
     • Execute `performTouchInput` rightward (`T2` right drag and `T1` right drag) AND leftward (`T1` left drag and `T2` left drag) drags across `HrvDfaA1RangeSlider` thumbs and starting inside the slider `Card` padding.
     • Assert `pagerState.currentPage` remains `3` throughout and after drags (no switch back to `PWR_BIKE` page 2), `T1`/`T2` update smoothly within `0.30f..1.20f` enforcing $T_1 \ge T_2 + 0.05f$ even under delayed DataStore updates (no snap-back), and `userScrollEnabled` re-enables on pointer release.
  2. *Non-Slider Tab Swipe, Vertical Scroll & Optional Row Slider Isolation (`TST-TRK-086.2`, `HrvDfaA1RangeSliderGestureTest.kt`)*:
     • On `page == 3` with the slider idle, perform horizontal `swipeRight` outside the slider `Card` (e.g., over the header/zone card) and assert `HorizontalPager` transitions smoothly to `page == 2`.
     • Perform vertical `swipeUp` / `swipeDown` across `HrvDfaA1SettingsContent` and assert `scrollState.value` scrolls cleanly.
     • Exercise `HrvThresholdControlRow` with `showSlider = true` and assert `onSliderGestureActiveChange(true)` / `(false)` triggers on slider press/release.
  3. *Contract Regression & Rule 8.5 Visual Inspection (`TST-TRK-086.3`, `HrvDfaA1SettingsContractTest.kt`, `Rule 8.5`)*:
     • Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1RangeSliderGestureTest" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest"` confirming 100% pass (`Localization parity: N/A - no UI strings added or modified`).
     • Capture Light/Dark screenshot PNG at `docs/engineering/walkthroughs/img/ATT-8105_hrv_slider_zones.png` and inspect via `read_file` against the 4-point `Rule 8.5` checklist.
### TST-CON-049: **Hardware-Grounded Polar Detection (PMD/PFTP UUIDs & DIS 0x2A24/0x2A25), Non-Polar Hard Gate & Empty-Name Resilience Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8717`, `ATT-8949`
- **Requirement(s)**: `REQ-CON-049` (`REQ-CON-049.1`..`REQ-CON-049.5`), `REQ-SET-104.3`, `REQ-SET-108.1`, `REQ-SET-112.1`, `REQ-SET-112.2`, `REQ-SET-118.2`
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarOfflineCapabilityStoreTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarOfflineGattCoordinatorCoexistenceTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarOnlineStreamingBridgeTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarH10OfflineControllerTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarVeritySenseOfflineControllerTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEHeartRateDeviceTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDevicePolarBackupTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDevicePolarLiveImuTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Capability Store, Negative Non-Polar GATT, Polar-Gated `"Sense"` & Empty-Name Test (`TST-CON-049.1`, `PolarOfflineCapabilityStoreTest.kt` & `PolarOfflineGattCoordinatorCoexistenceTest.kt`)*:
     • Verify `PolarOfflineCapabilityStore` persists `hasPmdService`, `hasPftpService`, `modelNumber` (`0x2A24`), `polarDeviceId` (`0x2A25`), `PolarDeviceType`, and hardware-overridable legacy seed/suppression flags per normalized MAC; confirm `isConfirmedNonPolarGatt` short-circuits `isPolarH10Device`/`isPolarVeritySenseDevice`/`isPolarOfflineCapableDevice` to `false` even when renamed `"Polar H10"` or `"Polar Verity Sense"`.
     • Assert bare `"Sense"` classifies as `VERITY_SENSE` strictly when Polar PMD/PFTP is confirmed (keeping unconfirmed `"Sense"`/`"CardioSense"` rejected), non-Polar `manufacturer` (`"Garmin"`/`"Wahoo"`) blocks legacy fallback, and `isPolarVeritySenseDevice` stays `true` with `name = ""` when `VERITY_SENSE` or legacy `offline_rec_` is set.
  2. *Pairing & Standard HRM Reconnect Positive/Negative GATT + DIS Hydration Test (`TST-CON-049.2`, `BTSearchForNewDevicesEngineTest.kt` & `BTLEHeartRateDeviceTest.kt`)*:
     • Mock `BluetoothGatt` for Polar HRMs (`0x180D` + `PMD`/`PFTP` + DIS `0x2A24`/`0x2A25`) and non-Polar HRMs (`0x180D` without `PMD`/`PFTP`); assert Polar HRMs persist UUIDs + DIS metadata while non-Polar HRMs persist `(false, false)` so `isConfirmedNonPolarGatt == true` and renaming to `"Polar H10"` returns `false`.
  3. *SDK Bridge DIS Hydration, `matchesBridgeIdentifier`, Pre-Connect Aliases & `RR_PRIORITY_PPG` Test (`TST-CON-049.3`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • Trigger `sdkCallback.disInformationReceived` (`UUID`/`String` & `DisInfo`, incl. `activeSdkStreamId` matching and `"Sense"` model) and `bleSdkFeatureReady`; assert store hydration, canonical `0x2A25` `polarId`, pre-connect `registerDeviceAliases(polarDeviceId, mac)`, and `isVeritySense == true` (`RR_PRIORITY_PPG`).
  4. *Controllers, Pre-Rename Legacy Backfill, Protocol Gate & Behavioral `EditDeviceDialog` Compose UI Test (`TST-CON-049.4`, Offline Mocked `Rule 5`, `REQ-PRO-049.1`, `REQ-PRO-132`)*:
     • Verify renamed H10/Verity routing across offline controllers and `DeviceDataRepository` (pre-rename H10 legacy backfill preserving options after rename + non-Polar rename suppression + `Protocol.BLUETOOTH_LE` gating excluding ANT+ HRMs).
     • Render `EditDeviceDialog` / `PolarOfflineDeviceSection` via Robolectric `createComposeRule()` confirming: (a) non-Polar HRM renamed `"Polar H10"` renders 0 Polar cards, (b) already-paired existing H10 renamed `"Brustgurt"` retains H10 cards, (c) Verity Sense / H10 cleared to `""` retains Polar cards (`N/A - no UI strings`; `0` `src/main` reads).

### TST-CON-050: **Live Polar-IMU Toggle Handoff, Shared-Session Recovery & Continuous HR/Raw-Telemetry Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8714`
- **Requirement(s)**: `REQ-CON-050` (`REQ-CON-050.1`..`REQ-CON-050.5`), `REQ-SET-112.1`, `REQ-SET-112.2`, `REQ-CON-045.2`, `REQ-CON-049.4`
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDevicePolarLiveImuTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/DeviceManagerTeardownTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarOnlineStreamingBridgeTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarRawTelemetryRecorderTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Live `polarLiveImuEnabled` Broadcast & Ordered GATT Handoff Test (`TST-CON-050.1`, `EditDevicePolarLiveImuTest.kt`, `DeviceManagerTeardownTest.kt`)*:
     • Update `polarLiveImuEnabled` (`false -> true` and `true -> false`) via `DeviceDataRepository.updateDevice()` during both idle and active workout states; assert the Live-IMU change broadcast fires with `deviceId` and new boolean flag (while non-IMU edits do not fire it).
     • Verify `DeviceManager` detaches old `RrBeatSource`, completes `MyBTLEDevice` GATT close (or `PolarOnlineStreamingBridge.shutDown()`) strictly before activating the replacement Polar device, preserves `ProxySensor` / `RrBeatSource` continuity, and preserves `< API 33` `BTLEHeartRateDevice` fallback.
  2. *Shared `PolarBleApi` `SESSION_OPEN` & Already-Ready Feature Recovery Test (`TST-CON-050.2`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • Mock `PolarBleApi` where `connectToDevice()` attaches to an already-open session (`isFeatureReady` returning `true` for `FEATURE_HR` and `FEATURE_POLAR_ONLINE_STREAMING` without re-emitting `deviceConnected`/`bleSdkFeatureReady`); assert `isConnectedFlag`, `registerSensors()`, `activeBridges`, `PolarRawTelemetryRecorder` attachment, HR streaming, and ACC/GYRO streaming activate cleanly.
  3. *Consistent `polarBleSessionId` & Disconnect Job Hygiene Test (`TST-CON-050.3`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • Connect by `macAddress` (empty `POLAR_DEVICE_ID` initially), then emit `deviceConnected` carrying an 8-hex `deviceId`; assert `startHrStreaming`, `requestStreamSettings`, `startAccStreaming`, and `startGyroStreaming` continue using the exact `polarBleSessionId` opened with `connectToDevice` (never breaking on mid-session MAC-to-8-hex flip).
     • Emit `deviceDisconnected` and assert `hrJob`, `accJob`, and `gyroJob` are cancelled and streaming flags reset before reconnect.
  4. *Continuous HR/RR Delivery, `PolarRawTelemetryRecorder` Capture & Hardware Boundary (`TST-CON-050.4`, `PolarRawTelemetryRecorderTest.kt`, `Rule 7.6`)*:
     • Verify active `PolarRawTelemetryRecorder` attaches on bridge readiness during a workout and persists non-zero `.polar_raw.csv.gz` frames (`rowCount > 0`) to `files/raw_sensor/` across Live-IMU enable handoff (`N/A - no UI strings`, `0` `src/main` reads per `REQ-PRO-049.1`).
     • Physical Pixel 10 + Polar Verity Sense / H10 live BLE verification is explicitly deferred to optional PO-executed hardware testing per `Rule 7.6` (never synthesized in Phase 2).

### TST-CON-051: **Non-Blocking BLE Search-Queue Timeout Handoff & Decoupled Mid-Session Background Reconnect Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8998`
- **Requirement(s)**: `REQ-CON-051` (`REQ-CON-051.1`..`REQ-CON-051.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/devices/DeviceManagerSearchQueueTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BLEReconnectRobustnessTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDeviceAddressAndSearchTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDeviceLifecycleTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Search-Queue Timeout Unblock During `TRACKING` / `PAUSED` (`TST-CON-051.1`, `BLEReconnectRobustnessTest`, `DeviceManagerSearchQueueTest`)*:
     • Configure `TrackingMode.TRACKING` and `TrackingMode.PAUSED` with `[Offline BLE Radar, Online BLE HRM]` queued in `DeviceManager`; fire `MyBTLEDevice.mSearchTimeoutRunnable` on the offline Radar.
     • Assert `MyBTLEDevice` clears `State.SEARCHING` / `isSearching() == false`, broadcasts `SEARCHING_STOPPED_FOR_ONE_INTENT` (`success = false`), `DeviceManager` clears `cMyRemoteDeviceCurrentlySearchingFor` and immediately invokes `startSearching()` on the second HRM, and `BANALService.isSearching()` becomes `false` once `mSearchQueue` drains.
  2. *Decoupled Background Reconnect & `tries <= 1` Survival vs. Startup Battery Guard (`TST-CON-051.2`, `DeviceManagerSearchQueueTest`, `BLEReconnectRobustnessTest`)*:
     • Deliver `SEARCHING_STOPPED_FOR_ONE_INTENT` (`success = false`) with `tries <= 1` for a device persisting active-workout / mid-session background reconnect; assert `DeviceManager` does NOT invoke `shutDown()` or remove the device from `mMyRemoteDevices` and keeps `mReconnectRunnable` (`2s -> 4s -> 8s -> 16s`) + `autoConnect = true` intact, while in `TrackingMode.READY` (`!hasConnectedInSession()`) `tries <= 1` STILL calls `shutDown()` and removes the offline sensor (`REQ-CON-036.1`).
     • Verify background `mReconnectRunnable` executes `triggerGattConnect(dev, true)` / `onScanResult()` attachment without setting `MyRemoteDevice.mSearching = true` or causing a subsequent `startSearchForPairedDevices()` / `searchForNextRemoteDevice()` to stall on `next.isSearching()`.
  3. *BLE Lifecycle, LoC Ceiling & Detailed-Design Parity (`TST-CON-051.3`, `MyBTLEDeviceAddressAndSearchTest`, `MyBTLEDeviceLifecycleTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`)*:
     • Execute `MyBTLEDeviceAddressAndSearchTest`, `MyBTLEDeviceLifecycleTest`, `ArchitectureConformanceContractTest` (verifying `DeviceManager.java <= 1,708` LoC), and `DetailedDesignParityContractTest` (`my_btle_device.md`, `banal_service.md`), asserting 100% pass with `0` new `src/main` test reads (`REQ-PRO-049.1`) and `Localization Parity: N/A - no UI strings`.

### TST-CON-052: **Extended Sensor Reconnect / Re-Search Backoff & Dual Search Settings Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8965`
- **Requirement(s)**: `REQ-CON-052` (`REQ-CON-052.1`..`REQ-CON-052.3`)
- **Component**: `SensorReconnectBackoffPolicyTest.kt`, `SensorSearchSettingsTest.kt`, `MyBTLEDeviceLifecycleTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `DeviceManagerSearchQueueTest.kt`, `SearchSettingsDialogTest.kt`, `SensorSearchTuningSectionTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `TranslationParityTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Extended Backoff Schedule, Clamping & Toggle Policy (`TST-CON-052.1`, `SensorReconnectBackoffPolicyTest`, `SensorSearchSettingsTest`)*:
     • Step through `SensorReconnectBackoffPolicy` with default `5 min` (`300 s`) final period; assert sequence `1_000 -> 2_000 -> 4_000 -> 8_000 -> 16_000 -> 32_000 -> 64_000 -> 128_000 -> 300_000 -> 300_000 ms`, verify smaller final periods (`60 s`, `120 s`) clamp cleanly without overshooting, verify `10 min` (`600 s`) reaches `600_000 ms`, verify disabled mode caps at `16_000 ms`, and verify reset restores `1_000 ms`.
     • Verify `SensorSearchSettings` defaults (`periodicResearchEnabled = true`, `finalPeriodSec = 300`) and SharedPreferences round-trip read/write.
  2. *BLE, Polar SDK & DeviceManager Periodic Re-Search + Contract Invariants (`TST-CON-052.2`, `MyBTLEDeviceLifecycleTest`, `PolarOnlineStreamingBridgeTest`, `DeviceManagerSearchQueueTest`, `SystemLiveTrackingRequirementsContractTest`, `ArchitectureConformanceContractTest`)*:
     • Assert `MyBTLEDevice` mid-session reconnect progresses beyond `16 s` up to configured final period when enabled, caps at `16 s` when disabled, cancels on `shutDown()`, and keeps `MAX_RECONNECT_DELAY_MS == 16000L`.
     • Assert `DeviceManager` + `PolarOnlineStreamingBridge` re-enqueue search-exhausted sensors with extended backoff when enabled, preserve `REQ-CON-051` non-blocking search-queue release, shut down & remove cleanly when disabled, cancel pending retries on `startSearchForPairedDevices()` / `removeAndStopRemoteDevice()` / `shutDown()`, and keep `DeviceManager.java <= 1,708` LoC & `TrainingApplication.java <= 2,135` LoC.
  3. *Behavioral Compose Dual Search Settings UI & 9-Locale Parity (`TST-CON-052.3`, `SearchSettingsDialogTest`, `SensorSearchTuningSectionTest`, `TranslationParityTest`, `REQ-PRO-132`, `0` `src/main` reads)*:
     • Render `SearchSettingsDialog` and `SensorSearchTuningSection` via `createComposeRule()`; verify periodic re-search toggle (`default = true`) and final period selector (`1m`, `2m`, `5m`, `10m`; `default = 5m`) persist changes to SharedPreferences, verify disabled toggle dims/disables final period selector, and assert `TranslationParityTest` passes across all 9 `res/values*/strings_devices.xml` locales.


### TST-TRK-087: **ZonesSettingsActivity HRV Tab Gauge Toggle Removal & Behavioral Compose Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8712`
- **Requirement(s)**: `REQ-TRK-087` (`REQ-TRK-087.1`, `REQ-TRK-087.2`), `REQ-TRK-061.4`, `REQ-TRK-046.4`, `REQ-UI-2783.3`
- **Component**: `HrvDfaA1RangeSliderGestureTest.kt`, `HrvDfaA1SettingsContractTest.kt`, `EditSensorFieldZoneDisplayTest.kt`, `TrackingTabConfigZoneDisplayTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Behavioral Compose UI Verification (`TST-TRK-087.1`, `createComposeRule()`, zero `src/main` reads per `REQ-PRO-049.1`)*:
     • Render `HrvDfaA1SettingsContent` (and/or `ZoneSettingsScreen` on `page == 3`) via Robolectric Compose `createComposeRule()` with `displayOptions = ZoneDisplayOptions(showGauge = false)`.
     • Assert `R.string.zone_display_title` and the 4 `ZoneDisplayOptionsToggleList` labels (`zone_display_background`, `zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`) are displayed, `R.string.zone_display_gauge` (*Zonen-Balken*) does NOT exist (`assertDoesNotExist()`), and the live `SensorFieldView` preview renders the active 3-segment gauge bar (`showBar = true`) even when `displayOptions.showGauge == false`.
     • Capture Light/Dark PNG screenshot via `captureToImage()` and inspect via `read_file` per `Rule 8.5`.
  2. *Contract & Tile/Tab Config Regression (`TST-TRK-087.2`, `HrvDfaA1SettingsContractTest.kt`, `EditSensorFieldZoneDisplayTest.kt`, `TrackingTabConfigZoneDisplayTest.kt`)*:
     • Update `HrvDfaA1SettingsContractTest.testHrvSettings_gaugeToggleAndPreviewBarContracts` to assert absence of `R.string.zone_display_gauge` / `displayOptions.showGauge` and `showBar = true` in `HrvDfaA1SettingsContent` (preserving `app/source-reading-baseline.txt` debt boundary).
     • Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1RangeSliderGestureTest" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldZoneDisplayTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingTabConfigZoneDisplayTest"` asserting 100% pass (`Localization Parity: N/A - no UI strings added, modified, or removed; R.string.zone_display_gauge remains active in ZoneDisplayOptionsControls.kt:230 across all 9 locales`).

### TST-TRK-088: **Radar Approach Speed Display Unit Mapping (`MyHelper.getUnitsId`/`getShortUnitsId`) & Contract Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8676`
- **Requirement(s)**: `REQ-TRK-088` (`REQ-TRK-088.1`..`REQ-TRK-088.3`)
- **Component**: `MyHelperRadarUnitsTest.kt` (new), `SensorTypeUnitsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `RadarTrafficSummaryCardTest.kt`, caller unit tests (`WorkoutDataMapper`, `WorkoutRepository`, `TrackingViewModel`)
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *`MyHelper` Metric & Imperial Unit ID/String Resolution (`TST-TRK-088.1`, `MyHelperRadarUnitsTest.kt`)*:
     • Under `MyUnits.METRIC` and `MyUnits.IMPERIAL`, assert `MyHelper.getUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)` returns `R.string.units_speed_metric` (`"km/h"`) / `R.string.units_speed_imperial` (`"mile/h"`) matching `SPEED_mps` and never `R.string.units_speed_basic` (`"m/s"`).
     • Under `MyUnits.METRIC` and `MyUnits.IMPERIAL`, assert `MyHelper.getShortUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)` returns `R.string.units_speed_short_metric` (`"km/h"`) / `R.string.units_speed_short_imperial` (`"mi/h"`) matching `SPEED_mps` and never `R.string.units_speed_basic` (`"m/s"`).
     • Verify preserved invariants: `SensorType.RADAR_APPROACH_SPEED_mps.internalUnit == "m/s"`, `getUnitId() == R.string.units_speed_basic`, and `RADAR_CLOSEST_DISTANCE_m` / `RADAR_PASSING_DISTANCE_m` still resolve to `R.string.units_distance_basic`.
  2. *Caller Unit Integration (`TST-TRK-088.2`, `WorkoutDataMapper` / `WorkoutRepository` / `TrackingViewModel` & `RadarTrafficSummaryCardTest`)*:
     • Verify caller unit resolution across `WorkoutDataMapper` (`getUnitsId`), `WorkoutRepository` (`getShortUnitsId` extrema marker path), `TrackingViewModel` (`getShortUnitsId` cockpit tile path), and `RadarTrafficSummaryCardTest` (`SpeedFormatter`) emits Metric `"km/h"` and Imperial `"mile/h"` (`"mi/h"` short), never `"m/s"`.
  3. *Contract, Source-Read Hygiene & Governance Verification (`TST-TRK-088.3`)*:
     • Execute `SensorTypeUnitsContractTest`, `SystemArchitectureDesignContractTest`, and `SourceTextReadingAuditContractTest` asserting `0` new `app/src/main/` reads (`REQ-PRO-049.1`), `"MyHelper.getUnitsId"` token preserved in `docs/architecture/sensor_units.md`, and `python3 tools/verify_requirement_governance.py --repo-root .` exits `0` (`verify_sensor_type_javadocs()` passing; `Localization Parity: N/A - no UI strings`).

### TST-FIL-026: **5-Hz Jeans-Pocket Barometric Rate-Matching, Adaptive Jitter Variance & Real Climb Preservation Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8881`
- **Requirement(s)**: `REQ-FIL-026` (`REQ-FIL-026.1`..`REQ-FIL-026.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/BarometricAltitudeFilterTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *5-Hz Jeans-Pocket Pedaling Oscillation & Synthetic `Zum Bäcker #57` Replay Test (`TST-FIL-026.1`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Feed `5-Hz` (`200 ms` synthetic `timestampMs`) pressure readings with cyclic `±1.2 m` (`1.0–1.5 Hz`) pedaling compression oscillations and `±8 m` pocket pulses over `60 s` on flat `DEM = 438.0 m` (`speed = 6.0 m/s`), synchronized with `1-Hz` `calculateMetrics(nowMs)`.
     • Assert barometric EKF ingestion is rate-matched to `1 Hz` (`~7 s` effective `BarometricAltitudeFilter` median span), flat-road `mSlopeSensor` / `ekf.slopePercent` stays within `±2%` (zero `±10–12%` swings), and `ASCENT` / `DESCENT` remain `0.0 m`.
     • Replay a full `2 km` synthetic `Zum Bäcker #57` elevation profile (`435..446 m` envelope with `±1.2 m` pocket ripples and `±8 m` compression pulses) and assert total cumulative ascent and descent stay within `10–16 m` without `+26.89 m` inflation.
  2. *Adaptive Cyclic Jitter Variance Scaling & 2-State `p00` Preservation Test (`TST-FIL-026.2`, `KinematicAltitudeSlopeEkfTest.kt`)*:
     • Verify 2-state `p00 == DEFAULT_VAR_BARO` (`0.25`) remains intact (`testThreeStateRefBeforeBaro_andTwoStateGuard_preservesP00AndClearsFlags`).
     • Inject cyclic sign-reversing `±1.2 m` barometric residual oscillations at `5–6 m/s` and assert effective `varBaro` scales upward adaptively to bound `|slopePercent| <= 2%`, while steady monotonic climbs (`+0.83..0.84 m/s`) avoid over-damping and converge accurately.
  3. *First-Sample Immediate Init, `shutDown()` Aggregator Reset & Real Climb Preservation Test (`TST-FIL-026.3`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`, `BarometricAltitudeFilterTest.kt`)*:
     • Verify a single `handlePressureMeasurement(1013.25f, 1000L)` call immediately initializes `ekf.isInitialized == true` and populates `mAltitudeSensor`, `shutDown()` clears all sub-second aggregator state cleanly across restarts, and steady-climb / calibration / standstill / filter tests (`tstFil020_3`, `tstFil022_3`, `tstFil023_2`, `tstFil023_3`, `BarometricAltitudeFilterTest` with `medianWindowSize = 7` and `filterSlewOnly()`) continue passing.
  4. *Tier-1 Targeted Regression & Source-Text Audit (`TST-FIL-026.4`, `SourceTextReadingAuditContractTest.kt`)*:
     • Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.devices.BarometricAltitudeFilterTest" --tests "com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkfTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"` asserting 100% pass rate with `0` new `src/main` source-text reads (`REQ-PRO-049.1`; `Localization parity: N/A - no UI strings`).

### TST-TRK-089: **Full-Screen Radar Vehicle Markers, V2 Icons & Contrast Framing Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8733`
- **Requirement(s)**: `REQ-TRK-089` (`REQ-TRK-089.1`..`REQ-TRK-089.3`)
- **Component**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/radar/RadarEdgeOverlay.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/radar/RadarEdgeOverlayTest.kt`, `ThemeColorCentralizationAuditTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `TranslationParityTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Non-Clipping Content-Sized Markers & Edge Clamping (`TST-TRK-089.1`, `RadarEdgeOverlayTest.kt`, `createComposeRule()`)*:
     • Render `RadarEdgeOverlay` in `RadarEdgeSide.FULL_SCREEN` with 2- and 3-digit targets (`15m`, `128m`, `160m`) placed at central and extreme lateral/vertical positions (`0.15f` / `0.85f` horizontal, `0.0f` / `1.0f` range).
     • Assert each distance label (`15m`, `128m`, `160m`) is displayed unclipped (`getUnclippedBoundsInRoot()`), marker bounds remain strictly inside the `RadarEdgeOverlay` container bounds, and `TRUCK_OR_BUS` marker dimensions exceed `CAR` / `UNKNOWN_OR_LIGHT`.
  2. *Distinct V2 Icons, 9-Locale Accessibility & Invariant Preservation (`TST-TRK-089.2`, `RadarEdgeOverlayTest.kt`, `TranslationParityTest.kt`)*:
     • Render mixed V2 targets (`CAR`, `TRUCK_OR_BUS`, `UNKNOWN_OR_LIGHT`) in `FULL_SCREEN` mode; assert distinct localized accessibility descriptions using existing `radar_vehicle_size_car`, `radar_vehicle_size_truck`, and `radar_vehicle_size_light` (zero `"Target"` fallback) and `0` missing keys across all 9 `values*/strings_radar.xml` locales (`Localization Parity: PASS - 9/9 existing strings_radar.xml keys reused, zero new keys needed`).
     • Assert full-screen markers/guides expose zero clickable/pointer actions (`assertHasNoClickAction()`), `RadarStatusBadgePill` remains absent when road is clear (`ATT-8679`), `FAST_APPROACHING` pulse activates only for `ThreatLevel.FAST_APPROACHING`, and `LEFT`/`RIGHT` `RadarVerticalRail` bounds/sizes remain unchanged (`REQ-TRK-073.2`).
  3. *Contract Hygiene & Rule 8.5 Visual Inspection (`TST-TRK-089.3`, `ThemeColorCentralizationAuditTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, `Rule 8.5`)*:
     • Execute `RadarEdgeOverlayTest`, `ThemeColorCentralizationAuditTest` (zero inline `Color(0x...)` in composables), `DeadCodeEliminationContractTest` (all `@Preview` functions `private`), `SourceTextReadingAuditContractTest` (`0` `src/main` file reads in `RadarEdgeOverlayTest.kt`), `TranslationParityTest`, and `:app:lintDebug`.
     • Capture Light/Dark/Map-style `@Preview` PNG screenshot at `docs/engineering/walkthroughs/img/ATT-8733_radar_fullscreen_markers_preview.png` in Stage 5 and inspect via `read_file` against `Rule 8.5`.

### TST-FIL-027: **Post-Workout ERTS Monotonic Timing, Stationary Pinning, Pocket Gyro/Baro Filtering, Lap Sync & Stream Subsampling Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8880`
- **Requirement(s)**: `REQ-FIL-027` (`REQ-FIL-027.1`..`REQ-FIL-027.5`)
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmootherTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmootherTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Monotonic `TIME_TOTAL` Timestamp Reconstruction & Batch Quantization Elimination (`TST-FIL-027.1`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Construct `WorkoutSamples.db` rows with `dt = 0 s / 2 s` SQLite `time` batch-flush duplicates paired with monotonic `TIME_TOTAL` (plus fallback schema lacking `TIME_TOTAL` and explicit pause gaps).
     • Assert reconstructed `timestampMs` drives `1-Hz` `dtSec` cleanly before `BarometricAltitudeFilter.filterSlewOnly()` and ERTS passes, eliminating `dt = 0` distance freezes, `0 m/h` `VERTICAL_SPEED` sawtooth drops, and `useAntiAliasedYaw` bypasses while preserving pause gaps.
  2. *Rolling 1.0-s Time-Weighted `WORLD_YAW_RATE_radps` & Clean Reset (`TST-FIL-027.2`, `SpeedAndLocationDevice_ATTFusedTest.kt`)*:
     • Verify single-sample `ingestImuMeasurement()` continues emitting `0.1 rad/s` immediately (`SpeedAndLocationDevice_ATTFusedTest:126-139`).
     • Feed `50-Hz` cyclic thigh-pedaling gyro oscillations (`1.2–1.5 Hz`, `±0.5..4.81 rad/s`) alongside a steady turn and assert `mWorldYawRateSensor` publishes the rolling `1.0 s` time-weighted mean (canceling cyclic pedaling aliases while preserving steady turn rate) and `resetOriginAndEkf()` / `shutDown()` clear all window state and `mLastGyroTimestampNs`.
  3. *Indoor-Stop ZUPT Pinning, Backward RTS Decoupling & Canopy Dropout Preservation (`TST-FIL-027.3`, `ExtendedRtsOdometrySmootherTest.kt`, `UnicycleOdometryEkfTest.kt`)*:
     • Replay a riding-to-indoor-stop-to-departure trajectory (`140 s` stationary block with `speedMps = 0.0`, `acc = 50.0 m`, post-stop departure) and assert `smoothedStates[k][2] == 0.0` (`+0.0 m` phantom indoor drift) during the stop without collapsing pre-stop moving speeds to `0.0 m/s`.
     • Verify `ExtendedRtsOdometrySmootherTest:140` (`wheelSpeedMps = 10.0`, `gnssSpeed = 0.0`) and `UnicycleOdometryEkf` contracts (`ZUPT_SPEED_VARIANCE = 2.0`, `MIN_DOPPLER_SPEED_MPS = 3.0`) remain intact.
  4. *`DISTANCE_m_LAP` Sync, Collinear `PolyUtil.simplify(..., 10.0)` & `ENCODING_STEP_SIZE` Parity (`TST-FIL-027.4`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Execute `recomputeWorkout()` on multi-lap and `< 20`-sample / `>= 60`-sample tracks; assert `DISTANCE_m_LAP` resets on `LAP_NR` transitions and matches `Laps.db` lap totals, `mapPolyline` simplifies via `PolyUtil.simplify(validLatLngs, 10.0)` (`2` endpoints on collinear straight-line tracks in `TST-FIL-017.3` & `TST-FIL-019.3`), and `distanceStream` / `altitudeStream` match `WorkoutSummaries.ENCODING_STEP_SIZE` (`20`) parity.
  5. *9-Locale Raw Barometer `filterSlewOnly()`, `2.0 m` Hysteresis & Governance Hygiene (`TST-FIL-027.5`, `ExtendedRtsAltitudeSlopeSmootherTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`, `SourceTextReadingAuditContractTest.kt`)*:
     • Test non-German/9-locale barometer column names (e.g., `"ALTITUDE (Altitude from Pressure)"`, `"ALTITUDE (Höhe aus Luftdruck)"`, `"ALTITUDE (Altitudine da pressione)"`) with pocket compression spikes (`-8.05 m / +6.87 m`) and verify `BarometricAltitudeFilter.filterSlewOnly()` pre-filtering + `ExtendedRtsAltitudeSlopeSmoother` `2.0 m` hysteresis (`ASCENT_DESCENT_HYSTERESIS_METERS = 2.0`) eliminates phantom climb while keeping raw device columns untouched, `WorkoutRepository.kt <= 5,117` LoC untouched, `0` new `src/main` reads (`REQ-PRO-049.1`), and `N/A - no UI strings`.

### TST-FIL-028: **Activity & Phone Carriage Mode Classification, 1-Hz Persistence & Adaptive EKF/ERTS Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8890`
- **Requirement(s)**: `REQ-FIL-028` (`REQ-FIL-028.1`..`REQ-FIL-028.5`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifierTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/sensor/formater/PhoneCarriageModeFormatterTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmootherTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmootherTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`, `SensorCategoryAndSelectorContractTest.kt`, `SensorTypeUnitsContractTest.kt`, `SensorFilterDefaultsTest.kt`, `TranslationParityTest.kt`, `MergedManifestFgsAuditTest.kt`, `PermissionOnboardingSheetContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *All-9-Mode `PhoneCarriageClassifier` Hysteresis, `Zum Bäcker` Dismount & Missing-Sensor Graceful Fallback (`TST-FIL-028.1`, `PhoneCarriageClassifierTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`)*:
     • Feed synthetic 50-Hz IMU/rotation-vector/barometer/step/speed/cadence profiles covering all 9 `PhoneCarriageMode` states (`UNKNOWN(-1)`, `STATIONARY(0)`, `WALKING_TRANSITION(1)`, `BIKE_HANDLEBAR(2)`, `BIKE_JERSEY_BACK(3)`, `BIKE_FRONT_POCKET(4)`, `RUN_WAIST_HIP(5)`, `RUN_BICEP_ARM(6)`, `RUN_HANDHELD(7)`) plus a `Zum Bäcker` `Ride -> Walk -> Stationary -> Walk -> Ride` transition timeline; assert mode classification accuracy, dwell hysteresis stability against transient bumps, and fast dismount/remount responsiveness.
     • Simulate `ACTIVITY_RECOGNITION` denied, `Sensor.TYPE_STEP_DETECTOR == null`, `TYPE_ROTATION_VECTOR == null`, `TYPE_PRESSURE == null`, and plain-JVM `SDK_INT == 0`; assert zero `SecurityException`/NPE crashes and deterministic fallback mode classification.
  2. *1-Hz `SensorType.ACTIVITY_AND_CARRIAGE_MODE` Persistence, CSV Export, Formatter & Reachability Contracts (`TST-FIL-028.2`, `SensorCategoryAndSelectorContractTest.kt`, `SensorTypeUnitsContractTest.kt`, `SensorFilterDefaultsTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Verify `SensorType.ACTIVITY_AND_CARRIAGE_MODE` sits at ordinal `67` (`0..66` unchanged), carries `SensorValueType.INTEGER`, `filteringPossible = false`, `internalUnit = "dimensionless"`, is reachable from `ActivityType.getSensorTypeArray()` / `DeviceType.getDeviceTypeList()` (`Rule 81`), and formats discrete codes via `PhoneCarriageModeFormatter` without breaking `SensorFilterDefaultsTest` or `SensorCategoryAndSelectorContractTest` (`68` total).
     • Verify `WorkoutSamples.db` schema auto-creation (`WorkoutSamplesDatabaseManager`) and `CSVFileWriter` 1-Hz integer column persistence + formatting without increasing `TrackerService.java`, `DeviceManager.java`, `WorkoutSamplesDatabaseManager.java`, or `WorkoutRepository.kt` line counts.
  3. *Smooth Live EKF/Filter Adaptation Across Modes Without State Discontinuities (`TST-FIL-028.3`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`, `UnicycleOdometryEkfTest.kt`, `KinematicAltitudeSlopeEkfTest.kt`)*:
     • Switch between `BIKE_HANDLEBAR`, `BIKE_FRONT_POCKET`, `RUN_BICEP_ARM`, `RUN_HANDHELD`, `WALKING_TRANSITION`, and `STATIONARY` mid-session; assert smooth transitions of `Q_GYRO`, yaw windowing + cyclist rear-glance rejection, barometer median window + slew, and `rBaro` without EKF position/heading/altitude discontinuities, preserving immediate sample-1 `0.1 rad/s` `WORLD_YAW_RATE_radps` emission, `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`, and `DEFAULT_VAR_BARO = 0.25`.
  4. *1-Hz ERTS Carriage Ingestion & Legacy Kinematic Fallback Replay (`TST-FIL-028.4`, `ErtsPostWorkoutRecomputerTest.kt`, `ExtendedRtsOdometrySmootherTest.kt`, `ExtendedRtsAltitudeSlopeSmootherTest.kt`)*:
     • Run `ErtsPostWorkoutRecomputer.recomputeWorkout()` on: (a) `WorkoutSamples.db` containing persisted `ACTIVITY_AND_CARRIAGE_MODE` (`BIKE_FRONT_POCKET -> WALKING_TRANSITION -> STATIONARY`), asserting indoor-stop pinning, pocket-spike attenuation, handlebar climb fidelity, and preserved `ACTIVITY_AND_CARRIAGE_MODE` values post-ERTS; and (b) legacy `WorkoutSamples.db` lacking `ACTIVITY_AND_CARRIAGE_MODE`, asserting kinematic fallback sequence reconstruction while keeping `ExtendedRtsOdometrySmootherTest:140` canopy Doppler-dropout green.
  5. *9-Locale Localization Parity, Platform Permission Audits & Architecture/System Sync (`TST-FIL-028.5`, `TranslationParityTest.kt`, `MergedManifestFgsAuditTest.kt`, `PermissionOnboardingSheetContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SourceTextReadingAuditContractTest.kt`)*:
     • Verify all 9 `values*/strings_devices.xml` locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) pass `TranslationParityTest` (zero `&#10;`, exact placeholder/newline parity), manifest/onboarding contracts preserve 4 steps (`PermissionOnboardingSheetContractTest`), `docs/architecture.md`/`sensor_units.md`/`docs/system/` (`SYS.2`/`SYS.3`) pass contract suites after Stage 3–5 implementation, and `SourceTextReadingAuditContractTest` reports `0` `src/main` file reads in new/modified tests.

### TST-FIL-029: **Carriage-Classified Smartphone IMU Cycling & Running Cadence Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8895`
- **Requirement(s)**: `REQ-FIL-029` (`REQ-FIL-029.1`..`REQ-FIL-029.5`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/SmartphoneImuCadenceEstimatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarLiveRunningCadenceEstimatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarOnlineStreamingBridgeTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/BANALServiceLifecycleTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerCadenceArbitrationTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/DeviceTypeSensorMappingTest.kt`, `ArchitectureConformanceContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Shared DSP Equivalence (`TST-FIL-029.1`, `PolarLiveRunningCadenceEstimatorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`)*:
     • Execute all existing Polar H10 (`100/200 Hz`) and Verity Sense (`52 Hz`) cadence and bridge tests after extracting `ImuCadenceDsp.kt`, asserting bit-for-bit behavioral parity (`±1.0–1.5 spm`, `<= 3 s` standstill zeroing, monotonic strides).
  2. *Cycling Pocket, Jersey Back, Coasting & Handlebar Suppression (`TST-FIL-029.2`, `SmartphoneImuCadenceEstimatorTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`)*:
     • Replay `BIKE_FRONT_POCKET` (`50-Hz` `1.25 Hz` / `75 RPM` thigh oscillations -> `5 s` coasting at `v = 6.0 m/s`, thigh gyro RMS `< 0.15 rad/s`), asserting `75 ± 2 RPM` active output, drop to `0.0 RPM` within `2.0 s` (`1.5 s` coasting gate), and clean `null` suppression if quiet coasting transitions classifier state across `4.0 s` hysteresis to `BIKE_HANDLEBAR`.
     • Replay `BIKE_JERSEY_BACK` `f` vs. `2f` harmonic and rough-road noise profiles, verifying fundamental `RPM` accuracy when $R(\tau_{\text{peak}})/R(0) \ge 0.45$ and suppression below `0.45`.
     • Replay `BIKE_HANDLEBAR` (`5–50 Hz` road chatter), `UNKNOWN`, and `WALKING_TRANSITION` under `BSportType.BIKE`, asserting `null` output, zero `SensorType.CADENCE`/`STRIDES` registration, and zero `GCData[5]` `'C'` emission.
  3. *Running Waist/Arm/Handheld, Screen-Glance Blanking & Dual-Rate Indoor Fallback (`TST-FIL-029.3`, `SmartphoneImuCadenceEstimatorTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`)*:
     • Replay `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, and `RUN_HANDHELD` at `170 steps/min` (`85 strides/min`) with `TYPE_STEP_DETECTOR`, mid-run `RUN_HANDHELD` `isGlanceActive()` wrist-glance orientation shifts, and gyro-absent `ACC + TYPE_STEP_DETECTOR` fallback (including indoor pre-GPS lock); assert `85 ± 2 strides/min` (`SensorType.CADENCE`), monotonic `SensorType.STRIDES` from `1` without glance spikes, `SENSOR_DELAY_GAME` (`50 Hz`) `TYPE_ACCELEROMETER` registration, and `LocationUnavailable()`/`onAccumulatorsReset()`/`shutDown()` safety.
  4. *`MySensorManager` Priority Arbitration & `DeviceType` Mapping (`TST-FIL-029.4`, `MySensorManagerCadenceArbitrationTest.kt`, `MySensorManagerPriorityTest.kt`, `DeviceTypeSensorMappingTest.kt`)*:
     • Update `MySensorManagerCadenceArbitrationTest.kt:168-171` to verify `CADENCE_PRIORITY` ends at `SPEED_AND_LOCATION_ATT_FUSED` strictly behind `HRM` (`RUN_SPEED > BIKE_CADENCE > BIKE_SPEED_AND_CADENCE > BIKE_POWER > HRM > SPEED_AND_LOCATION_ATT_FUSED`), verify explicit `STRIDES_PRIORITY` (`RUN_SPEED > HRM > SPEED_AND_LOCATION_ATT_FUSED`) arbitration across connect/disconnect cycles, and assert `DeviceType.getDeviceTypeList(CADENCE|STRIDES)` contains `SPEED_AND_LOCATION_ATT_FUSED`.
  5. *Monolith Ceilings, `SWE.2`/`SYS.2`/`SYS.3` Parity & Governance Hygiene (`TST-FIL-029.5`, `ArchitectureConformanceContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SourceTextReadingAuditContractTest.kt`)*:
     • Verify `TrackerService.java <= 3,556`, `DeviceManager.java <= 1,707`, `WorkoutSamplesDatabaseManager.java <= 1,533`, `WorkoutRepository.kt <= 4,973` (`+0` from `ATT-8895`, `<= 5,117` cap on `e62a6ba4`), zero new `src/main` test reads (`REQ-PRO-049.1`), `N/A - no UI strings`, Stage 4–5 `docs/architecture.md`/`docs/system/` sync, and `verify_requirement_governance.py` `exit 0`.

### TST-FIL-030: **Kinematic `BSportType` Inference & Stop-Filtered Moving-Speed Sport Identity Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8891`
- **Requirement(s)**: `REQ-FIL-030` (`REQ-FIL-030.1`..`REQ-FIL-030.5`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicSportTypeDiscriminatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/BANALServiceSportInferenceTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/EquipmentAndSportTypeDiscoveryManagerMovingSpeedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/BANALServiceLifecycleTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Kinematic Discrimination, Coasting, Hardware Degradation & Edge-Case Athletes (`TST-FIL-030.1`, `KinematicSportTypeDiscriminatorTest.kt`)*:
     • Replay multi-stop errand cycling profiles (`Metzger` `1.35 m/s` session avg / `p50 = 3.88 m/s` / `p90 = 6.34 m/s` / coasting segments; `Norma #9`; `Zum Bäcker #57`), asserting `BSportType.BIKE` inference, `coastingSeconds > 0`, and accurate stop-filtered `MovingSpeedSummary` (`movingMeanMps`, `p50Mps`, `p90Mps`, `movingSampleCount`) unaffected by `15–25 min` stationary gaps.
     • Replay `Kurze Runde #52` (`1.9–2.6 m/s`, `0 s` coasting, `2.6–3.0 Hz` steps) AND fast `5.6 m/s` interval running (`3.2 Hz` impacts, $L_{\text{step}} = 1.75\text{ m}$) under both full-sensor and degraded budget-phone configurations (`TYPE_PRESSURE == null`, `TYPE_GYROSCOPE == null`, `TYPE_ROTATION_VECTOR == null`, `ACTIVITY_RECOGNITION` denied -> permission-free 50-Hz `TYPE_ACCELEROMETER` via `ImuCadenceDsp`), asserting `BSportType.RUN` within `<= 30 s` and zero exceptions.
     • Replay slow `2.0 m/s` steep uphill MTB cycling (no `2.4–3.6 Hz` impact periodicity, cycling carriage/crank rhythm) and a `RUN -> BIKE` mid-session transition (brief initial jog followed by `>= 3 s` coasting at `v > 3.0 m/s` and cycling carriage), asserting uphill MTB avoids false `RUN` latching and `RUN -> BIKE` transitions cleanly via bounded hysteresis.
  2. *Split Sport Context Hint & `SpeedAndLocationDevice_ATTFused` Integration (`TST-FIL-030.2`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`)*:
     • Verify `PhoneCarriageClassifier.setSportContext(...)` receives strictly `BANALService.getAuthoritativeBSportType()` (`SportContextHint.UNKNOWN` during sensorless unselected sessions even after kinematic inference settles to `RUN`/`BIKE`), preventing circular feedback.
     • Verify downstream `SmartphoneImuCadenceEstimator` and `applyCadenceAndStrideSensors()` observe full `BANALService.getBSportType()` so kinematically inferred `BSportType.RUN` dynamically registers `SensorType.STRIDES`, retains stride counting across `WALKING_TRANSITION` walk breaks, suppresses false cycling RPM on transient carriage flicker, never broadcasts `SPORT_TYPE_CHANGED_BY_USER_INTENT`, and resets cleanly on `onAccumulatorsReset()` / `shutDown()`.
  3. *`BANALService` 4-Tier Precedence, Unified `getSportTypeId` & `RESET_ACCUMULATORS_INTENT` Lifecycle (`TST-FIL-030.3`, `BANALServiceSportInferenceTest.kt`, `BANALServiceLifecycleTest.kt`)*:
     • Verify strict precedence: Tier 1 `mHaveUserSelectedSportType` (including explicit `OTHER`/`WALK`) > Tier 2 `DeviceManager.getSportType()` (`BIKE_POWER`/`BIKE_CADENCE`/`RUN_SPEED`) > Tier 3 `mInferredBSportType` > Tier 4 `UNKNOWN`, with zero SQLite queries on `getBSportType()`/`getActiveBSportType()`/`setInferredSportTelemetry()`.
     • Verify unified `getSportTypeId()` and `getSportTypeId(avgSpd)` both route through `getBSportType()`, and verify `RESET_ACCUMULATORS_INTENT` clears `mInferredBSportType` and `mMovingSpeedSummary` while strictly preserving `mHaveUserSelectedSportType`, `mUserSelectedSportTypeId`, and `mUserSelectedBSportType` (`onDestroy()` clears all).
  4. *Moving-Speed Discovery, Sparse-Sample Floor & Zero-Bloat Finalization (`TST-FIL-030.4`, `EquipmentAndSportTypeDiscoveryManagerMovingSpeedTest.kt`, `TrackerServiceFinalizationTest.kt`)*:
     • Verify `EquipmentAndSportTypeDiscoveryManager` preserves existing 2/3-arg signatures, falls back strictly to `sessionAvgSpeed` when `movingSampleCount < 10` (indoor/treadmill/short walk), uses `movingMeanMps` for `UNKNOWN`/`RUN` (preventing `p90` misclassification of fast `4.3–5.6 m/s` runners into `MTB`/`BIKE`), and uses cycling cruising speed under `BSportType.BIKE` so `Metzger`/`Norma #9`/`Zum Bäcker #57` resolve to `BIKE` (`Endurance` / linked bike gear) instead of `WALK`/`RUN`.
     • Verify `TrackerService` (`BanalFinalizationSnapshot` backward-compatible 4-arg/6-arg overloads + `finalizeLiveSession()`) and `LiveWorkoutSession` 1-Hz moving-speed fallback pass moving-speed-aware identity while preserving `WorkoutSummaries.SPEED_AVERAGE_mps` and `SensorType.SPEED_mps` `ExtremaType.AVG` as `mDistanceTotal_m / mTimeActive_s` (`SCRUM-108` / `REQ-TRK-030.3`) with zero new main-thread SQLite queries.
  5. *Monolith Ceilings, `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` Parity & Governance Hygiene (`TST-FIL-030.5`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SourceTextReadingAuditContractTest.kt`)*:
     • Verify `TrackerService.java <= 3,556` (`3,540`), `DeviceManager.java <= 1,707`, `SpeedAndLocationDevice_ATTFused.kt < 1,500` (`1,417`), `BANALService.java <= 1,035` (`1,018`), `N/A - no UI strings`, zero new `src/main` test reads (`REQ-PRO-049.1`), Stage 4–5 `docs/architecture.md`/`banal_service.md`/`docs/system/` parity, and `verify_requirement_governance.py` `exit 0`.

### TST-FIL-031: **Multisport Transition Detection, Retroactive Anchoring & Automatic Named Laps Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8917`
- **Requirement(s)**: `REQ-FIL-031` (`REQ-FIL-031.1`..`REQ-FIL-031.5`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/MultisportTransitionDetectorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/sensor/MyAccumulatorSensorResetToTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/BANALServiceMultisportTransitionTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/MultisportLapCoordinatorTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`, `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Multisport State Machine, Pause Gating, Bakery Stop Immunity & Parked-Sensor/50-Hz `ACC` Fusion (`TST-FIL-031.1`, `MultisportTransitionDetectorTest.kt`)*:
     • Replay `BIKE -> T2 (1800..1850 s) -> RUN (1850..1865 s)` brick and `RUN -> T1 -> BIKE -> T2 -> RUN` duathlon timelines, asserting exact confirmation at `t = 1865 s` with retroactive anchors `t_exit = 1800 s`, `t_enter = 1850 s`, and `(activeTimeS, totalTimeS, totalDistanceM)` snapshots.
     • Replay `< 10 s` direct sport switch, `160 s` same-sport bakery stop (`BIKE -> WALK/STAT -> BIKE`), `< 15 s` opposite-sport blip, `!isTracking` / `PAUSED` / `AUTO_PAUSED` gating (confirming zero triggers during pause), parked silent `0 W` bike sensor in `T2` with `TYPE_STEP_DETECTOR` absent (fusing 50-Hz `ACC` footstrikes from `KinematicSportTypeDiscriminator`), and clean `reset()`.
  2. *`MyAccumulatorSensor.resetTo(N)` & `BANALService` Precedence / Lifecycle (`TST-FIL-031.2`, `MyAccumulatorSensorResetToTest.kt`, `BANALServiceMultisportTransitionTest.kt`)*:
     • Verify `resetTo(targetValue)` sets `mInitialValue = target - mValue`, emits `targetValue` to listeners outside `synchronized (this)`, preserves `mLastAbsoluteValue` across subsequent `accumulate()` ticks, and never mutates `MySensorManager.mInitialValues`.
     • Verify Tier 1 `mHaveUserSelectedSportType == true` blocks auto-multisport, confirmed transition overrides Tier 2 `CONFLICT` / silent parked bike sensor, resets `mActivityTypeMax` even when `!changeSportWhenDeviceGetsLost()`, emits `MULTISPORT_TRANSITION_INTENT` without firing `SPORT_TYPE_CHANGED_BY_USER_INTENT` or `LAP_SUMMARY`, and clears cleanly on `RESET_ACCUMULATORS_INTENT` and `onDestroy()`.
  3. *`MultisportLapCoordinator` Retroactive Named Laps, Auto-Pause `T2`, Manual-Lap Clamping & `WorkoutSamples.db` Sync (`TST-FIL-031.3`, `MultisportLapCoordinatorTest.kt`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`, `TrackerServiceFinalizationTest.kt`)*:
     • Verify `Laps.db` persists `Bike` (`0..1800 s`), `Transition` (`1800..1850 s`), and final-stop `Run` (`1850..end`) with UTC `"yyyy-MM-dd HH:mm:ss"` `TIME_START` parseable by `FitFileWriter` and localized names; verify `TIME_LAP` / `DISTANCE_m_LAP` re-seed via `resetTo` to `(now - effectiveEnter)` deltas.
     • Verify partial auto-pause inside `T2` persists active unpaused `Transition` duration, 100% auto-paused stationary `T2` (`0 s`, `0.0 m`) skips `Transition` under `REQ-TRK-002` with contiguous `LAP_NR` (`+1`, no `1 -> 3` gap), manual-lap interleaving clamps boundaries to `currentLapStartAnchor`, and async `WorkoutSamples.db` `LAP_NR` range update checks `existsColumnInTable` / `PRAGMA table_info` before updating time-bounded rows with `0` edits to `WorkoutSamplesDatabaseManager.java`.
  4. *9-Locale Localization Parity (`TST-FIL-031.4`, `TranslationParityTest.kt`)*:
     • Verify `lap_name_bike`, `lap_name_transition`, and `lap_name_run` exist across all 9 `values*/strings_cockpit.xml` locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) with zero `&#10;` and exact key parity.
  5. *Monolith LoC Ceilings & `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` Parity (`TST-FIL-031.5`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SourceTextReadingAuditContractTest.kt`)*:
     • Verify `TrackerService.java <= 3,540` (`3,537`, `-3`), `DeviceManager.java <= 1,707` (`0` edits), `WorkoutSamplesDatabaseManager.java <= 1,533` (`0` edits), `WorkoutRepository.kt <= 4,931` (`0` edits), `SpeedAndLocationDevice_ATTFused.kt <= 1,455` (`1,441`, `< 1,500`), `BANALService.java <= 1,075` (`1,074`), zero DB schema changes (`Laps.db` v3), `0` new `src/main` test reads (`REQ-PRO-049.1`), Stage 4–5 `docs/architecture.md`/`banal_service.md`/`tracker_service.md`/`docs/system/` parity, and `verify_requirement_governance.py` `exit 0`.

### TST-SEN-032: **WheelPositionSelectionDialog Front/Rear-Only Selection & Legacy Fallback Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-8859`
- **Requirement(s)**: `REQ-SEN-032` (`REQ-SEN-032.1`..`REQ-SEN-032.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelPositionSelectionDialogUiTest.kt` (new), `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelPositionDialogsTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceEquipmentWheelPositionTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit & UI
- **Verification Method**: Unit
- **Procedure**:
  1. *Behavioral Compose Front/Rear-Only Dialog & Legacy `UNSPECIFIED` State Test (`TST-SEN-032.1`, `WheelPositionSelectionDialogUiTest.kt`, `createComposeRule()`, `0` `src/main` reads)*:
     • Render `WheelPositionSelectionDialog` via Robolectric Compose `createComposeRule()`: assert strictly 2 options render (`wheel_position_front`, `wheel_position_rear`) and `wheel_position_unspecified` / `R.string.equipment_wheel_position_unspecified` do NOT exist (`assertDoesNotExist()`).
     • Verify tapping `wheel_position_front` / `wheel_position_rear` invokes `onSelectPosition(WheelPosition.FRONT / REAR)` and `onDismiss()`.
     • Render with `currentPosition = WheelPosition.UNSPECIFIED` and `isLinked = true`: assert neither `Front` nor `Rear` radio is selected (`assertIsNotSelected()`), `wheel_position_unlink` and `wheel_position_cancel` remain functional, tapping `Front`/`Rear` upgrades cleanly, and `conflictHints` still render on `Front`/`Rear`.
  2. *Legacy Enum/DB/Caller Compatibility, Private `@Preview` & `Rule 8.5` PNG (`TST-SEN-032.2`, `WheelPositionDialogsTest`, `EditDeviceEquipmentWheelPositionTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`)*:
     • Capture Light & Dark PNG screenshot at `docs/engineering/walkthroughs/img/ATT-8859_wheel_position_dialog.png` and verify visually via `read_file` (`Rule 8.5`).
     • Verify `WheelPosition.values().size == 3`, `EditDeviceEquipmentWheelPositionTest` legacy `UNSPECIFIED` summary/sheet display (`R.string.equipment_wheel_position_unspecified` preserved across all 9 locales; `Localization Parity: N/A - no UI strings`), `DeadCodeEliminationContractTest` (`private fun` `@Preview`s), and `SourceTextReadingAuditContractTest` (`0` new `src/main` reads) pass `100%`.

### TST-FIL-032: **Post-Workout ERTS Beat-Immune Timestamping, speedAverage_mps Parity & Stationary Indoor Pinning Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-9064`
- **Requirement(s)**: `REQ-FIL-032` (`REQ-FIL-032.1`..`REQ-FIL-032.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmootherTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceErtsFinalizationTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManagerTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Beat-Immune 1-Hz Timestamping & Pause/Stall Parity (`TST-FIL-032.1`, `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Replay `584`-row `78`-pair `0/2` (`2 -> 0` and `0 -> 2`) `TIME_TOTAL` beat sequence asserting reconstructed span equals `583_000L ms` (`0 s` stretch vs. `662 s`), plus `> 5 s` wall pause landing on `timeTotalSec == prevTimeTotalSec`, `deltaTimeTotal > 5L` pause, leading/all-`null` `TIME_TOTAL` pauses, and regression of `testLeadingNullTimeTotal_batchFlushStall6s_andDoubleWallClockPause_tstFil027_1`.
  2. *Authoritative `speedAverage_mps` / `Extrema(AVG)` / `Laps.db` Parity & Matched Fallback (`TST-FIL-032.2`, `ErtsPostWorkoutRecomputerTest.kt`, `LapsDatabaseManagerTest.kt`)*:
     • Verify `WorkoutSummaries.SPEED_AVERAGE_mps`, `ExtremumValues(SensorType.SPEED_mps, ExtremaType.AVG)`, and `Laps.speedAverage_mps` agree with `totalDistance / TIME_ACTIVE_s` within `0.02 m/s` on a `584 s` ride with an indoor stop.
     • Verify `TIME_ACTIVE_s = NULL` / `<= 0` with a `30 s` pause uses matched `activeDist / activeDt` over `dt in (0.0, 5.0]` without pause-gap inflation, and verify zero/empty/non-finite safety guards.
  3. *Stationary Covariance Freeze, `0.00 m` Indoor Haversine Pinning & Contracts (`TST-FIL-032.3`, `UnicycleOdometryEkfTest.kt`, `ExtendedRtsOdometrySmootherTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`, `TrackerServiceErtsFinalizationTest.kt`, `ArchitectureConformanceContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SourceTextReadingAuditContractTest.kt`)*:
     • Verify `UnicycleOdometryEkf.predict(isStationary = true)` freezes `[E, N]`, zeroes `F[0..1, 2..4]` and `Q_POS` (zero `P_pos` growth over `118 s`, SPD invertible `P_{k+1|k}`), leaving default `isStationary = false` untouched.
     • Verify `ExtendedRtsOdometrySmoother.smoothSamples()` and `smoothSnapshots()` yield `0.00 m` Haversine drift and `0.00 m` `deltaDistanceMeters` across a `118 s` (`acc = 48.5 m`) indoor stop with `+35.66 m` departure jump while preserving `TST-FIL-027.3` & canopy Doppler-dropout (`wheelSpeedMps = 10.0`).
     • Verify `ArchitectureConformanceContractTest` (`0` protected monolith edits; all 3 touched files `< 1,500` LoC), `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest` (`0` new `src/main` reads), and `Localization Parity: N/A - no UI strings`.

### TST-FIL-033: **Post-Workout ERTS Altitude/Slope DEM Grid-Step Immunity, Contractive OU Standstill Decay, Speed-Coupled Slew & Barometer-Free Fallback Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-9063`
- **Requirement(s)**: `REQ-FIL-033` (`REQ-FIL-033.1`..`REQ-FIL-033.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmootherTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/BarometricAltitudeFilterTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmootherTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceErtsFinalizationTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *DEM Grid-Step Immunity, Contractive OU Standstill Decay & Backward RTS Bounds (`TST-FIL-033.1`, `ExtendedRtsAltitudeSlopeSmootherTest`: `testZumBaeckerStartupDemGridStepAndStandstill_noSlopeBlowupOrInitialOvershoot_tstFil033_1`, `testSteepMountainTransientDemStepAndStandstill_staysBoundedWithoutOvershoot_tstFil033_1`, `testHelperGuards_lowSpeedGainClampAndJosephSpd_tstFil033_1`)*:
     • Replay `Zum Bäcker #58` closed-loop startup (`438 m`, `BARO ~ 437.5 m`) with `-3 m` (`438 -> 435 m`) and steep-mountain transient `-10 m` (`438 -> 428 -> 438 m`) integer DEM steps at `t = 4 s` during slow movement (`~5 m`), followed by `20 s` standstill and normal closed-loop riding at `437–438 m`.
     • Assert `|smoothed[0].altitudeM - 438.0| <= 2.0`, `|slopePercent| <= 35.0` everywhere (`< 10.0%` on flat start), no backward RTS blowup across the `20 s` stop, exact 3×3 Joseph SPD symmetry/positive determinant, and `|ascentM - descentM| <= 3.0 m`, while preserving delayed-anchor convergence (`TST-FIL-017.2`), `2.0 m` hysteresis (`TST-FIL-027.5`), and carriage baro variance scaling (`TST-FIL-028.4`).
  2. *Speed-Coupled Barometric Slew, Alpine Descent/Climb & NaN-Safe Speed Sanitization (`TST-FIL-033.2`, `BarometricAltitudeFilterTest`: `testComputeSpeedCoupledSlewRateMps_scalesWithSpeedAndGuardsNonFinite_tstFil033_2`, `testSpeedCoupledFilterSlewOnly_passesAlpineDescentUnclippedWhileRejectingSlowPocketSpikes_tstFil033_2`; `ErtsPostWorkoutRecomputerTest`: `testAlpineClimbAndFastDescentInFrontPocket_speedCoupledSlewUnclippedAndNaNWheelSpeedSafe_tstFil033_2`)*:
     • Verify `BarometricAltitudeFilter.computeSpeedCoupledSlewRateMps()` across finite (`0.0`, `2.0`, `18.0`, `50.0` capped at `35.0`), negative (`-5.0`), and non-finite (`Double.NaN`, `Double.POSITIVE_INFINITY`) speeds and non-finite base rates.
     • Verify `18 m/s` `-10%` (`-1.8 m/s`) alpine descent and `10%` climb in `BIKE_FRONT_POCKET` pass unclipped via `filterSlewOnly(..., speedMps)` and `ErtsPostWorkoutRecomputer`, slow-speed `±8 m` pocket spikes stay clamped, `baroFilter.maxSlewRateMps == 0.7`, and NaN wheel speed safely falls back to finite `speedMps`/`0.0`.
  3. *Barometer-Free Fallback, Sequential Finite-Scan, `n == 1` Guard & Contracts (`TST-FIL-033.3`, `ExtendedRtsAltitudeSlopeSmootherTest`: `testBarometerFreeDemOnlyLeadingNullAndSingleSample_finiteAndPhysicallyBounded_tstFil033_3`; `ErtsPostWorkoutRecomputerTest`: `testBarometerFreeDemOnlyAndLeadingNullAltitudeWorkout_smoothAndFinite_tstFil033_3`; `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`)*:
     • Verify barometer-free DEM/GPS-only stream (`rawAltitudeM = Double.NaN`) learns physically bounded climb/descent slope (`|slopePercent| <= 35%`, zero NaN) without overshoot.
     • Verify leading-null `rawAltitude`/`referenceAltitude` rows (never `0.0 m` injection) and single-sample `n == 1` non-finite input emit finite outputs, and verify `ArchitectureConformanceContractTest` (`0` protected monolith edits, `547`/`234`/`1,054 < 1,500` LoC), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`, and `Localization Parity: N/A - no UI strings`.

### TST-FIL-034: **PhoneCarriageClassifier Indoor GNSS-Outage Stationary Transition, Cyclic-Immune Glance Gating, Multipath Speed Gating & Cross-Device Portability Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-9062`
- **Requirement(s)**: `REQ-FIL-034` (`REQ-FIL-034.1`..`REQ-FIL-034.4`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifierTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicSportTypeDiscriminatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/MultisportTransitionDetectorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/SmartphoneImuCadenceEstimatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Indoor GNSS-Outage, Stagnant-Fix & Decay-to-Zero `STATIONARY` Transition (`TST-FIL-034.1`, `PhoneCarriageClassifierTest`: `testIndoorGnssOutageAndStagnantCachedFixes_transitionToStationaryInsteadOfRunWaistHipFreeze_tstFil034_1`; `SpeedAndLocationDevice_ATTFusedTest`: `testTriggerDecayTick_propagatesZeroSpeedToCarriageClassifierWithInjectableClock_tstFil034_1`)*:
     • Replay bakery walk-in (`1.44 m/s`, `acc = 48.5 m`) followed by `120 s` GNSS outage with quiet IMU (`swingSd < 2.5°`, `rawGyroRms < 0.14 rad/s`, `0` steps) asserting clean transition to and persistence in `STATIONARY (0)` (never freezing on `RUN_WAIST_HIP (5)`).
     • Replay stagnant cached indoor fixes (`>= 4 s`, `< 2.0 m` spread, cached `2.0 m/s`, `acc = 5 m`) + quiet IMU asserting `STATIONARY (0)`, verify pure IMU-only startup without prior speed stays `UNKNOWN`, and verify `SpeedAndLocationDevice_ATTFused.triggerDecayTickForTesting()` with injectable `currentTimeMillis()` propagates `0.0 m/s` and commits `STATIONARY (0)`.
  2. *Cyclic-Immune `detectGlanceShift()` & Front-Pocket Cycling (`TST-FIL-034.2`, `PhoneCarriageClassifierTest`: `testCyclicFrontPocketPedaling_zeroFalseGlancesAndClassifiesBikeFrontPocket_tstFil034_2`; `SpeedAndLocationDevice_ATTFusedTest`: `testHandheldGlanceGatingAndRunningChordHeading_tstFil028_3`)*:
     • Replay front-pocket cycling acceleration (`2.5 -> 5.4 m/s`, `sportHint == UNKNOWN`, `wheelSpeed == null`, `stepDetectorRegistered = true` with zero steps, cyclic `0.85/1.2/1.5/1.75 Hz ±25°` thigh flexion, `yaw >= 0.45 rad/s`, `baro >= 0.40 m/s`) asserting `0` false glances (including startup `prevSpanMs < 1_200L`) and `BIKE_FRONT_POCKET (4)` output.
     • Verify genuine sustained wrist glances (`70° ± 22° -> 18–20°` and gentle `70° -> 35°`) still trigger `isGlanceActive == true` and `RUN_HANDHELD`.
  3. *Degraded Multipath Speed & `isMovingByGnss` Accuracy Gating (`TST-FIL-034.3`, `SpeedAndLocationDevice_ATTFusedTest`: `testDegradedMultipathIndoorSpeed_gatedFromClassifierAndStationaryPinning_tstFil034_3`)*:
     • Replay walking transition (`ekf.speedMps ~ 1.0 m/s`) encountering indoor multipath fixes (`acc = 43.9 m`, `location.speed = 2.89 m/s`); assert degraded speed is rejected from both `speedForClassifier` and `isMovingByGnss`, `forceStationaryPinning` stays intact, and mode remains `WALKING_TRANSITION (1)` / `STATIONARY (0)`.
  4. *Cross-Device/Athlete Portability & Contracts (`TST-FIL-034.4`, `PhoneCarriageClassifierTest`: `testCrossDeviceAndAthletePortability_deniedPermissionBaroFreeFastRunnerAndUphillMtb_tstFil034_4`; `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`)*:
     • Verify (a) barometer-free `ACTIVITY_RECOGNITION`-denied runner (`stepDetectorUnavailable`, `2.8 Hz` accel footstrikes, `RMS >= 1.2 m/s²`), (b) `5.6 m/s` (`3:00 min/km`) interval runner (`3.0 Hz` footstrikes, with and without step detector), and (c) `2.0 m/s` uphill `60 RPM` MTB climber (smooth `1.0 Hz` pedaling + low-energy `3.0 Hz` harmonic `< 1.2 m/s²`, zero footstrikes) classify as `RUN_*`, `RUN_*`, and `BIKE_*` respectively while `PhoneCarriageClassifierTest:295` stays `WALKING_TRANSITION`.
     • Verify `ArchitectureConformanceContractTest` (`SpeedAndLocationDevice_ATTFused.kt = 1,459 / 1,500` LoC, `0` protected monolith edits), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`, and `Localization Parity: N/A - no UI strings`.

### TST-FIL-035: **PhoneCarriageClassifier Front-Hip Running Belt, Easy-Pace Running & Sport-Context Guardrail Verification Suite** [Verified]
- **Requirement(s)**: `REQ-FIL-035` (`REQ-FIL-035.1`..`REQ-FIL-035.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifierTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/SmartphoneImuCadenceEstimatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicSportTypeDiscriminatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/MultisportTransitionDetectorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Sport-Context Cycling Guardrail, Non-Sticky Eviction & Brick Override (`TST-FIL-035.1`, `PhoneCarriageClassifierTest`: `testRunningSportContextGuardrail_masksCyclingEvictsStaleBikeAndAllowsBrickOverride_tstFil035_1`)*:
     • Verify `sportHint == RUNNING` with `wheelSpeed == null` (or `<= 0.5 m/s`), `effectiveSpeed < 5.5 m/s`, and cycling-like front-pocket IMU (plus zero-IMU fallback) emits `0%` `BIKE_*` modes and evicts any stale committed `BIKE_*` mode (`committedScore = Double.NEGATIVE_INFINITY`).
     • Verify triathlon/brick override: once wheel speed `> 0.5 m/s` or `effectiveSpeed >= 5.5 m/s` arrives without footstrikes, `suppressCyclingForRun` lifts and `BIKE_*` classifies cleanly.
  2. *Easy-Pace Running Walking-Gate Bypass, Front-Hip Belt Tiers & 9-Profile Carry-Mode Separation (`TST-FIL-035.2`, `PhoneCarriageClassifierTest`: `testFrontHipRunningBeltAndEasyPace_bypassesWalkingBandsPreservesObstacleWalkAndCarrySeparation_tstFil035_2`; `SpeedAndLocationDevice_ATTFusedTest`: `testHandheldGlanceGatingAndRunningChordHeading_tstFil028_3`)*:
     • Replay easy-pace front-hip running belt (`1.7–2.3 m/s`, `2.20 Hz` in `walkingStepBand`, dropped step detector with `impactRms >= 1.8 m/s²`, and permission-denied moderate-impact accel cadence `>= 2.15 Hz`) asserting both `walkingStepBand` and `walkingSpeedBand` are bypassed and output is `RUN_WAIST_HIP (5)` across mild/lively/bouncy/low-yaw belt profiles.
     • Verify slow obstacle climbs (`1.2 m/s`, `1.5 Hz`, `impactRms < 1.2 m/s²`, `>= 4.0 s`) and low-impact brisk walks (`1.85 m/s`, `impactRms < 1.2 m/s²`) still enter `WALKING_TRANSITION (1)`, and verify separation of rear clip, bicep armband (`17.0°`), handheld with glance, handheld without glance, and `SpeedAndLocationDevice_ATTFusedTest:testHandheldGlanceGatingAndRunningChordHeading_tstFil028_3`.
  3. *Chatter-Resistant Margin, Asymmetric Dwell & Regression/Contract Suite (`TST-FIL-035.3`, `PhoneCarriageClassifierTest`: `testChatterResistantMarginAndAsymmetricDwell_suppressesTransientDipsWhilePreservingFastGlanceAndWalkToRun_tstFil035_3`; `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`, Contracts)*:
     • Verify steady front-hip belt running with transient 2.5 s canopy speed dips and 4.5 s non-glance IMU bursts holds `RUN_WAIST_HIP (5)` (`0` spurious switches via `SCORE_MARGIN = 0.85`, `4.0 s` `RUN -> WALK` dwell, `6.0 s` intra-run non-glance dwell), while verified wrist glance switches in `0 ms` and `WALK -> RUN` / `RUN -> STATIONARY` switch in `2.0 s` (preserving `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` Phase 2 `5.0 s` walk→run).
     • Verify `ATT-9062` (`TST-FIL-034`) & `ATT-8890` (`TST-FIL-028`) regression, `ArchitectureConformanceContractTest` (`SpeedAndLocationDevice_ATTFused.kt = 1,459 / 1,500`, `PhoneCarriageClassifier.kt = 1,124 / 1,500`, `0` protected monolith edits), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`, and `Localization Parity: N/A - no UI strings`.

### TST-FIL-036: **ATT Fused Cold-Start Pinning Release, Unanchored Heading/Velocity Decoupling & Post-Unpin ERTS Velocity Overshoot Verification Suite** [Verified]
- **Requirement(s)**: `REQ-FIL-036` (`REQ-FIL-036.1`..`REQ-FIL-036.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/AttFusedColdStartHelperTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceStationaryJitterTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceDisplacementTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmootherTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Procedure**:
  1. *Cold-Start Pin Release, Veto Precedence & Indoor Anti-Oscillation Guard (`TST-FIL-036.1`, `AttFusedColdStartHelperTest`: `testDirectedOutdoorMotion_releasesColdStartPinAndSeedsSouthHeading_tstFil036_1`, `testIndoorHandheldOscillatingMultipath_rejectedByAntiOscillationGuard_tstFil036_1`; `SpeedAndLocationDevice_ATTFusedTest`: `testColdStartSouthEasyRun_releasesPinPromptlyWithout23sFreezeOrTeleport_tstFil036_1_2`, `testIndoorHandheldOscillatingMultipath_remainsPinnedWithZeroDistanceCreep_tstFil036_1`; `SpeedAndLocationDeviceIndoorAndRestCouplingTest`: `testColdStartReleaseHook_cannotBypassIndoorExternalWheelOrAccelRestVetoes_tstFil036_1`)*:
     • Replay Pixel 10 `t = 0..25 s` outdoor South easy run (`1.8 m/s`, cold GPS `acc = 18.9 -> 9.2 m`, active running IMU) verifying pinning releases within `<= 5 s`, no `>= 10 m` unpin teleport jump, and continuous speed/distance progression (`> 32 m` recovered over `45 m`).
     • Verify structurally unbypassable vetoes (`mIsIndoorMode == true`, stationary external wheel `0.0 m/s`, `accelAtRest == true`, cold stationary `25.1 -> 16.0 m` jump at `0.0 m/s`) AND indoor handheld oscillating multipath (`acc = 15–16 m`, fake `1.5 m/s` bouncing without directed progress, straightness `< 0.55`) all remain pinned (`0.0 m/s`, `0.0 m` distance).
  2. *Raw-Chord Heading Seed, Unanchored/Post-Outage Velocity Decoupling & Bounded Doppler Variance (`TST-FIL-036.2`, `AttFusedColdStartHelperTest`: `testCalibratedDopplerSpeedVariance_boundsTrustedFixesAndPreservesUntrustedDistrust_tstFil036_2`; `UnicycleOdometryEkfTest`: `testSeedInitialHeadingAndVelocityDecoupling_snaps180DegAndBlocksOutageSpeedKick_tstFil036_2`; `SpeedAndLocationDevice_ATTFusedTest`: `testColdStartSouthEasyRun_releasesPinPromptlyWithout23sFreezeOrTeleport_tstFil036_1_2`)*:
     • Verify South start (`acc = 18.9 -> 9.2 m`, `speed = 1.8 m/s < 3.0 m/s`) snaps 180° heading via one-shot `seedInitialHeading()` from directed raw-GNSS chord (`>= 4.0 m`, `>= 3` samples) and decouples `K[2][0..1]` while unanchored so `ekf.speedMps` is never crushed toward `0`.
     • Verify `UnicycleOdometryEkf.updateGnssPosition()` `confirmedPostOutage` snap re-converges `[E, N]` without injecting a velocity spike into `x[2]`, and bounded Doppler `speedVar` (`0.25..2.25` for `acc <= 20 m`) improves cold-start speed tracking while preserving `acc > 20 m` distrust (`TST-FIL-023.1` & `TST-FIL-017` preserved).
  3. *ERTS `dt_gnss`-Normalized Teleport Decoupling, 1-Tick Glitch Pre-Gate, Backward Catch-Up Bound, Genuine Sprint & Contracts (`TST-FIL-036.3`, `ExtendedRtsOdometrySmootherTest`: `testPostUnpinTeleportAnd1TickGlitch_suppresses573MpsCatchUpOvershoot_tstFil036_3`, `testGenuineLaunchAndMultiSecondSprint_notClippedByTeleportOrGlitchGates_tstFil036_3`; `ErtsPostWorkoutRecomputerTest`: `testPostUnpinTeleportRun_boundsCanonicalSpeedAndMaxExtremaAndPreservesRawColumns_tstFil036_3`; Contracts)*:
     • Replay `23 s` pinned standstill + `9.34 m` (`t = 24 s`, with isolated 1-tick `9.34 m/s` geometric glitch) and `12.20 m` (`t = 31 s`) teleports through `ExtendedRtsOdometrySmoother` & `ErtsPostWorkoutRecomputer`: assert canonical `SPEED_mps` and `ExtremumValues.MAX(SPEED_mps)` remain `< 3.5 m/s` (no `5.73 m/s` spike) and raw device columns remain byte-identical.
     • Verify genuine launch (`0, 0, 2.0, 2.2`) & multi-second sprint (`2.0 -> 3.5 -> 5.0 -> 6.0 m/s`) preserve peak speed `>= 5.5 m/s`, `45 s` curved tunnel outage (`dt_gnss = 45 s`) does not false-trigger discontinuity gating, and `ArchitectureConformanceContractTest` (`SpeedAndLocationDevice_ATTFused.kt = 1,481 / 1,500` LoC, `0` protected monolith edits), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`, and `Localization Parity: N/A - no UI strings` all pass (`144/144 PASS` verified on `97f49453`, Gate 4 `ATT-9246`).

### TST-FIL-037: **Live ATT Fused Altitude/Slope EKF DEM Grid-Step Jump Gating, Coherent Variance Retuning & Joseph SPD Verification Suite** [Verified]
- **Requirement(s)**: `REQ-FIL-037` (`REQ-FIL-037.1`..`REQ-FIL-037.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmootherTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Verification Result**: PASS (`72/72` PASS across 7 unit & contract suites + `:app:assembleDebug`, `0` failures, `0` errors, `0` skipped, `0` new Kotlin compiler warnings; independently verified at Gate 4 `ATT-9260` on SHA `0b792832`, zero `app/` diff post-Gate 4 per `Rule 7.5`; `4/4` Python safety linters PASS)
- **Verification Evidence**:
  - `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`: `KinematicAltitudeSlopeEkfTest.testCoherentVarianceRetuningAndBaroSlopeDecouplingWithJosephSpd_tstFil037_1` (`TST-FIL-037.1`), `KinematicAltitudeSlopeEkfTest.testStepDiscrepancyGatingTrackerAdvancementHuberAndHighPrecisionGuard_tstFil037_2` (`TST-FIL-037.2`), `KinematicAltitudeSlopeEkfTest.testThreeStateGpsBeforeDem_recoversFromCoarseGpsAnchorWithoutBiasFreeze` (`TST-FIL-037.2` non-regression)
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`: `AltitudeAndSlopeDevice_ATTFusedTest.testSyntheticPixel10KurzeRunde53LoopReplay_gatesDemGridJumpsAndPreservesMslClosure_tstFil037_3` (`TST-FIL-037.3`)
  - Contracts & regression suites: `ExtendedRtsAltitudeSlopeSmootherTest` (`8/8`), `ArchitectureConformanceContractTest` (`11/11`), `DeadCodeEliminationContractTest` (`5/5`), `SourceTextReadingAuditContractTest` (`6/6`), `SystemLiveTrackingRequirementsContractTest` (`4/4`)
- **Procedure**:
  1. *Coherent DEM/GPS Variance Retuning, Baro/Standstill Slope Decoupling & 3×3 Joseph SPD (`TST-FIL-037.1`, `KinematicAltitudeSlopeEkfTest`: `testCoherentVarianceRetuningAndBaroSlopeDecouplingWithJosephSpd_tstFil037_1`)*:
     • Verify `DEFAULT_VAR_DEM == 25.0`, `DEFAULT_VAR_GPS == 64.0` (`DEFAULT_VAR_GPS > DEFAULT_VAR_DEM`), and `DEFAULT_VAR_BARO == 0.25`, plus invalid `refAltM`/`varRef` input guards.
     • Feed `-13 m` and `+21 m` DEM grid jumps while barometer is active & flat, asserting `ekf.slope` stays decoupled (`|slopePercent| < 1%`), `|Δaltitude| < 0.5 m`, and 3×3 covariance stays symmetric positive-definite (`p00, p11, p22 > 0`, `pij == pji`, positive principal minors & determinant); also verify barometer-free DEM-only climbing still updates positive slope (`> 3%`).
  2. *Baro-vs-DEM Step-Discrepancy Gating, Reference Tracker Advancement, Huber Variance Inflation & High-Precision Anchor Guard (`TST-FIL-037.2`, `KinematicAltitudeSlopeEkfTest`: `testStepDiscrepancyGatingTrackerAdvancementHuberAndHighPrecisionGuard_tstFil037_2`, `testThreeStateGpsBeforeDem_recoversFromCoarseGpsAnchorWithoutBiasFreeze`)*:
     • Verify transient `440 -> 427 -> 440 -> 461 -> 461 -> 440 m` DEM bounces with flat barometer (`|Δbaro| < 0.5 m`) trigger step-discrepancy + Huber variance inflation (`|Δalt| < 0.5 m`, zero `p00` collapse), and `Ref-before-Baro` + standstill baro drift seed/track `Δbaro` accurately on the first post-anchor DEM jump.
     • Verify persistent coarse GPS (`453 m`) -> DEM (`438 m`) recovery converges within `±1.0 m` (`testThreeStateGpsBeforeDem`), gradual weather drift absorbs into `baroBias`, and high-precision anchors (`varRef = 0.5 <= 1.0`, `enableStepGating = false`) apply unimpeded (`> 6.0 m` single-tick pull).
  3. *Synthetic Pixel 10 `Kurze Runde var 2 #53` Loop Replay & Contracts (`TST-FIL-037.3`, `AltitudeAndSlopeDevice_ATTFusedTest`: `testSyntheticPixel10KurzeRunde53LoopReplay_gatesDemGridJumpsAndPreservesMslClosure_tstFil037_3`, Contracts)*:
     • Replay synthetic `~60 m` climb/descent loop (`720 s` at `2.0 m/s`) with repeated `13–21 m` DEM grid jumps and gradual `+3.0 m` barometric weather drift through `AltitudeAndSlopeDevice_ATTFused`: assert live `ASCENT`/`DESCENT` stay in `55–75 m` (not `167 m`), live `|SLOPE| <= 20%` (no `-35%/+34%` artifacts), `|Δalt/dt| < 1.5 m/s`, and closing `ALTITUDE` stays within `±5.0 m` of `440.0 m` MSL.
     • Verify `ArchitectureConformanceContractTest` (`0` protected monolith edits), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`, and `Localization Parity: N/A - no UI strings`.

### TST-SEN-033: **EditDeviceDialog Wheel Sensor Calibration UI Cleanup & WheelCircumferenceHistoryChart Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SEN-033` (`REQ-SEN-033.1`..`REQ-SEN-033.3`)
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceWheelCircumferenceTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/WheelCircumferenceHistoryDbTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelCircumferenceHistoryChartTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceEquipmentWheelPositionTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialogWheelGuidanceTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/UiReflectionOnlyTestAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Verification Result**: PASS (`63/63` PASS across 11 UI/DB/localization/contract suites + `:app:compileDebugKotlin` GREEN, `0` failures, `0` errors, `0` skipped, `0` Kotlin compiler warnings across all `ATT-8422` production & test files; independently verified at Gate 4 Bounce 1 `ATT-9275` on SHA `c4bdc03c`, zero `app/` diff post-Gate 4 per `Rule 7.5`; `Rule 8.5` Light/Dark PNG visually certified; `4/4` Python safety linters PASS)
- **Verification Evidence**:
  - `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceWheelCircumferenceTest.kt`: `EditDeviceWheelCircumferenceTest.testWheelCircumferenceSection_removesBikeCalculatorPreservesPresetsAndAutoHint_tstSen033_1`, `EditDeviceWheelCircumferenceTest.testRunCalibrationFactorContent_preservesKnownDistanceCalculator_tstSen033_1`, `EditDeviceWheelCircumferenceTest.testBaselineCircumferenceRounding_avoidsIeee754TruncationAt2010mm_tstSen033_1` (`TST-SEN-033.1`); `EditDeviceWheelCircumferenceTest.testEquipmentRepository_getCircumferenceHistoryForDeviceAndManualReset_tstSen033_2`, `EditDeviceWheelCircumferenceTest.testEditDeviceViewModel_asyncHistoryLoadStaleClearAndDraftBaselineSync_tstSen033_2` (`TST-SEN-033.2`); `EditDeviceWheelCircumferenceTest.testWheelCircumferenceHistoryChart_unspecifiedLegendAndSensorEmptyState_tstSen033_3`, `EditDeviceWheelCircumferenceTest.testLightDarkWheelCircumferenceSection_renderAndCapturePng_rule85_tstSen033_3`, `EditDeviceWheelCircumferenceTest.testNarrow280dpThreeLegendFlowRow_wrapsWithoutClippingOrZeroWidth_tstSen033_3` (`TST-SEN-033.3`)
  - Regression, localization & contracts: `EditDeviceEquipmentWheelPositionTest` (`5/5`), `EditDeviceDialogWheelGuidanceTest` (`2/2`), `WheelCircumferenceHistoryChartTest` (`3/3`), `WheelCircumferenceHistoryDbTest` (`4/4`), `TranslationParityTest` (`11/11` across all 9 locales), `ArchitectureConformanceContractTest` (`11/11`), `DeadCodeEliminationContractTest` (`5/5`), `UiReflectionOnlyTestAuditContractTest` (`4/4`), `SourceTextReadingAuditContractTest` (`6/6`), `SystemLiveTrackingRequirementsContractTest` (`4/4`)
  - Visual PNG Artifact (`Rule 8.5`): `docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png`
- **Procedure**:
  1. *Bike-Sensor Calibration UI Cleanup & `RUN_SPEED` Preservation (`TST-SEN-033.1`, `EditDeviceWheelCircumferenceTest`: `testWheelCircumferenceSection_removesBikeCalculatorPreservesPresetsAndAutoHint_tstSen033_1`, `testRunCalibrationFactorContent_preservesKnownDistanceCalculator_tstSen033_1`)*:
     • Strictly offline Robolectric `createComposeRule()` behavioral test (zero `src/main` reads, zero reflection-only UI tests): render `WheelCircumferenceSection` asserting zero calculator icon, interactive tire-size preset selection (`700 x 32 (2155 mm)`), localized GNSS auto-calibration hint (`devices_wheel_circumference_auto_hint`), localized baseline label (`devices_wheel_baseline_format`), and exactly ONE baseline reset button (`wheel_circumference_reset_baseline_button`).
     • Render `RunCalibrationFactorContent` and assert calculator button remains present and opens `CorrectCalibrationDialog`.
  2. *Per-Sensor Chronological History Query, Unlinked Fallback & Async Baseline Sync (`TST-SEN-033.2`, `EditDeviceWheelCircumferenceTest`: `testEquipmentRepository_getCircumferenceHistoryForDeviceAndManualReset_tstSen033_2`, `testEditDeviceViewModel_asyncHistoryLoadStaleClearAndDraftBaselineSync_tstSen033_2`; `WheelCircumferenceHistoryDbTest`)*:
     • Against Robolectric SQLite `EquipmentDbHelper` + `EquipmentRepository`: assert `getCircumferenceHistoryForDevice(deviceId)` filters per linked bike's assigned `WheelPosition`, merges multi-bike history sorted strictly by `timestampMs ASC`, includes all records when link position is `UNSPECIFIED`, and returns `emptyList()` for unlinked sensors.
     • Assert `recordManualBaselineResetForDevice()` persists `SOURCE_MANUAL_RESET` per linked `(bike, position)` and `EditDeviceViewModel` runs SQLite I/O on injected `ioDispatcher`, synchronizes edited draft circumference to `Devices.db` + `EquipmentRepository`, refreshes `wheelCircumferenceHistory`, and clears stale history on non-wheel devices.
  3. *`WheelCircumferenceHistoryChart` `UNSPECIFIED`/Empty State, Rule 8.5 PNG Audit, 9-Locale Parity & Contracts (`TST-SEN-033.3`, `EditDeviceWheelCircumferenceTest`: `testWheelCircumferenceHistoryChart_unspecifiedLegendAndSensorEmptyState_tstSen033_3`, `testLightDarkWheelCircumferenceSection_renderAndCapturePng_rule85_tstSen033_3`; `TranslationParityTest`, Contracts)*:
     • Verify `WheelCircumferenceHistoryChart` renders `UNSPECIFIED` series + `equipment_tpms_unspecified_legend` ONLY when `unspecifiedRecords.isNotEmpty()`, renders sensor-specific `devices_wheel_history_empty` on empty state, and preserves `EditEquipmentDialog` Front/Rear parity.
     • Capture and visually inspect Light & Dark Mode PNG (`docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png`, `Rule 8.5`) and verify `TranslationParityTest` (all 9 locales: `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`), `ArchitectureConformanceContractTest` (`EditDeviceDialog.kt = 1,774 / 2,004` LoC), `DeadCodeEliminationContractTest`, `UiReflectionOnlyTestAuditContractTest`, `SourceTextReadingAuditContractTest`, and `SystemLiveTrackingRequirementsContractTest`.

### TST-TRK-090: **Actual UTC Lap Start Timestamp Persistence & Anchor Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-9159`
- **Requirement(s)**: `REQ-TRK-090` (`REQ-TRK-090.1`..`REQ-TRK-090.2`)
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/MultisportLapCoordinatorTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManagerTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceLapTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceStateMachineCharacterisationTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/integration/FileExportersIntegrationTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
- **Test Type**: Unit
- **Verification Method**: Unit
- **Verification Result**: PASS (`103/103` PASS across 10 unit/integration/contract suites on `45c3fd9c`, `0` failures, `0` errors, `0` skipped, `0` new compiler warnings; verified at Gate 4 `ATT-9294` with zero `app/` diff post-`45c3fd9c` per `Rule 7.5`; `Localization Parity: N/A - no UI strings`)
- **Verification Evidence**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/MultisportLapCoordinatorTest.kt`: `MultisportLapCoordinatorTest.singleSportMultiLap_persistsPreMutationUtcTimeStart_withPauseNeutrality_underNonUtcTimezone_tstTrk090_1`, `MultisportLapCoordinatorTest.emptyManualSplit_doesNotLeakPendingStartTimestamp_tstTrk090_1` (`7/7` PASS, `TST-TRK-090.1`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManagerTest.kt`: `LapsDatabaseManagerTest.testSaveLap_nullTimeStartWithPositiveLapTime_fallsBackToUtcNowMinusLapTime_tstTrk090_2` (`11/11` PASS, `TST-TRK-090.2`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceLapTest.kt`: `TrackerServiceLapTest.testTrackerServiceSaveLap_forwardsUtcStartTimestampFromCoordinator_tstTrk090_2` (`7/7` PASS, `TST-TRK-090.2`)
  - Regression & contracts: `TrackerServiceFinalizationTest` (`29/29`), `TrackerServiceResumeTest` (`18/18`), `TrackerServiceStateMachineCharacterisationTest` (`8/8`), `FileExportersIntegrationTest` (`3/3`), `ArchitectureConformanceContractTest` (`11/11`, `TrackerService.java = 3,539 / 3,559` LoC), `DetailedDesignParityContractTest` (`3/3`), `SourceTextReadingAuditContractTest` (`6/6`, `0` `src/main` reads)
- **Procedure**:
  1. *Pre-Mutation Anchor Capture, Multi-Lap UTC `TIME_START` & Pause Neutrality (`TST-TRK-090.1`, `MultisportLapCoordinatorTest.kt`)*:
     • Initialize `MultisportLapCoordinator.resetForSession(T0, ...)` under non-UTC timezone (`Europe/Berlin`); dispatch `LAP_SUMMARY` (`isPause=true`) verifying `currentLapStartAnchor` stays `T0` with zero lap write; dispatch `LAP_SUMMARY` (`isPause=false`) at `T1` followed by `saveFinalLap()` at `T2`.
     • Assert Lap 1 `timeStart == formatUtcTimestamp(T0)` and Lap 2 `timeStart == formatUtcTimestamp(T1)` in canonical UTC `yyyy-MM-dd HH:mm:ss` (proving pre-mutation capture before `currentLapStartAnchor` updates to `T1` and matching `FitFileWriter.parseUtcTimestamp()`), and verify in-process resume preserves `currentLapStartAnchor` while post-process-death `applyResumedState` anchors at `max(0L, now - timeLap_s * 1000L)`.
  2. *`TrackerService.saveLap`, `LapsDatabaseManager` UTC `now - lapTime` Fallback & Contract Hygiene (`TST-TRK-090.2`, `LapsDatabaseManagerTest.kt`, `TrackerServiceLapTest.kt`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceStateMachineCharacterisationTest.kt`, `FileExportersIntegrationTest.kt`, Contracts)*:
     • Invoke `LapsDatabaseManager.saveLap(workoutId, lapNr, null, lapTime = 300, ...)` under non-UTC timezone (`Europe/Berlin`): assert persisted `ContentValues` `TIME_START` equals UTC `yyyy-MM-dd HH:mm:ss` for `now - 300_000 ms` (never `CURRENT_TIMESTAMP` end time); verify explicit `timeStart` is preserved verbatim and `lapTime <= 0 && lapDistance <= 0.0` still rejects phantom laps.
     • Verify `TrackerServiceLapTest`, `TrackerServiceFinalizationTest`, `TrackerServiceStateMachineCharacterisationTest` (4-/5-arg `saveLap` overrides & broadcasts), `FileExportersIntegrationTest` (FIT `LapMesg.startTime` UTC round-trip), `ArchitectureConformanceContractTest` (`TrackerService.java <= 3,559` LoC, `WorkoutRepository.kt <= 5,117` LoC), and `SourceTextReadingAuditContractTest` (`0` new `src/main` reads; `Localization Parity: N/A - no UI strings`).
### TST-FIL-038: **Sensor Disconnect Filter Reset, Accumulator Preservation & Sparkline Flat-Line Termination Suite** (`ATT-8994`) [Verified]
- **Requirement(s)**: `REQ-FIL-038` (`REQ-FIL-038.1`..`REQ-FIL-038.4`)
- **Component**: `MySensor.java`, `MyAccumulatorSensor.java`, `ProxySensor.java`, `FilterManager.kt`, `ANTHeartRateDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `MySensorDeactivationAndProxyTest.kt`, `MySensorManagerProxyTest.kt`, `MyAccumulatorSensorResetToTest.kt`, `SensorAndDeviceConcurrencyStressTest.kt`, `SpeedAndLocationDeviceDecayTest.kt`, `ANTHeartRateDeviceTest.kt`, `BTLEHeartRateDeviceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `FilterManagerRebindTest.kt`, `FilterNullSafetyTest.kt`, `FilterResetAndPreStartTest.kt`, `FilterManagerAccumulatorResetTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
- **Jira**: `ATT-8994`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Deactivation, Silent Accumulator/Proxy Overrides & `ProxySensor` Unbind (`TST-FIL-038.1`, `MySensorDeactivationAndProxyTest#testMySensorDeactivate_clearsValueAndNotifiesListenersNullLockFree_TST_FIL_038_1`, `MySensorDeactivationAndProxyTest#testMyAccumulatorSensorDeactivate_staysSilentAndPreservesTotals_TST_FIL_038_1`, `MySensorDeactivationAndProxyTest#testProxySensorDeactivateAndSetSourceNull_notifiesOnlyNonAccumulatorsAndTracksAccumulatorBacked_TST_FIL_038_1`, `MySensorManagerProxyTest`, `MyAccumulatorSensorResetToTest`, `SensorAndDeviceConcurrencyStressTest`, `SpeedAndLocationDeviceDecayTest`)*: Verify `MySensor.deactivateSensor()` clears `mValue` and notifies listeners `null` lock-free; verify `MyAccumulatorSensor.deactivateSensor()` and `ProxySensor.deactivateSensor()` stay silent; verify `ProxySensor.setSourceSensor(null)` notifies `null` outside `mSourceLock` strictly for non-accumulator previous sources and tracks `isAccumulatorBacked()` for accumulators (`STRIDES`).
  2. *Dynamic HRM Sensor Deactivation Before Removal (`TST-FIL-038.2`, `ANTHeartRateDeviceTest#testRrIntervalSensor_deactivatesAndNotifiesNullOnUnregisterAndShutDown_TST_FIL_038_2`, `BTLEHeartRateDeviceTest#testRrIntervalSensor_deactivatesAndNotifiesNullOnUnregisterAndShutDown_TST_FIL_038_2`, `PolarOnlineStreamingBridgeTest#testDynamicRrAndImuSensors_deactivateOnDisconnectWhileStridesPreservesTotal_TST_FIL_038_2`)*: Verify dynamic `mRrIntervalSensor` and Polar IMU cadence/VO/GCT sensors call `deactivateSensor()` and emit `null` to listeners prior to `unregisterSensor()`/`removeSensor()` on `unregisterSensors()` & `shutDown()`, while `mStridesSensor` retains accumulated strides.
  3. *`FilterManager` Disconnect Clearing, Accumulator/Cumulative Immunity & `createFilter()` Parity (`TST-FIL-038.3`, `FilterManagerRebindTest#testDevicePinnedDisconnect_clearsInstantaneousAndMovingAverage_preservesCumulativeAndStrides_andRebinds_TST_FIL_038_3`, `FilterManagerRebindTest#testBestSensorProxyDisconnect_preservesFallback_clearsOnLastDisconnect_andRetainsProxyBinding_TST_FIL_038_3`, `FilterManagerRebindTest#testCreateFilter_defersInactiveDevicePinnedSensor_andNeverDefersRetainedProxy_TST_FIL_038_3`, `FilterNullSafetyTest`, `FilterResetAndPreStartTest`, `FilterManagerAccumulatorResetTest`)*: Verify device-pinned disconnect (`null` & `!isActivated`) unbinds and resets `InstantaneousFilter`/`TimedMovingAverageFilter`/`NumberedMovingAverageFilter` to `null` (`"--"`) while preserving `AverageFilter`, `MaxValueFilter`, and dynamic `STRIDES` accumulator across repeated `SENSORS_CHANGED` broadcasts; verify `"Bester Sensor"` `ProxySensor` retains binding across fallback vs last-sensor disconnect & reconnect (both `InstantaneousFilter` and `MovingAverageFilter`); verify `createFilter()` defers inactive device-pinned sensors while never deferring retained `ProxySensor`.
  4. *Sparkline Flat-Line Termination & Contracts (`TST-FIL-038.4`, `FilterManagerRebindTest#testDisconnectStopsFlatLineSparklineFeedingAcrossRepeatedTicks_TST_FIL_038_4`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`)*: Verify `BANALServiceRepository.updateSparklineBuffers(filterManager.getAllFilteredSensorData())` appends `0` samples after disconnect across repeated 1-Hz ticks; verify `0` edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `BANALServiceRepository.kt`, `SensorFieldView.kt`, `PolarOnlineStreamingBridge.kt` (`1,306/1,500`), and hyphenated `TST-FIL-038` tags (`REQ-PRO-099.3`).
- **Verification Evidence**: Gate 4 (`ATT-9293`) independently verified SHA `b34c62cc` (`Rule 7.5`, `0` `app/` code drift) — `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, and `:app:assembleDebug` PASS with `0` new Kotlin compiler warnings + `140/140 PASS` (`0` failed, `0` errors, `0` skipped) across all 16 suites (`10/10` verbatim `TST-FIL-038` methods verified); `4/4` Python safety linters PASS; clean-checkout `verify_requirement_governance.py` EXIT `0`.
### TST-SEN-034: iTPMS Persisted Baseline Wiring, Live Reset Propagation & Null GNSS Gating Verification [Verified]
- **Requirement(s)**: `REQ-SEN-034`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryItpmsWiringTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryMultiWheelTpmsTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/tpms/IndirectTpmsEstimatorTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceWheelCircumferenceTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
- **Jira**: `ATT-8421`
- **Procedure**:
  1. *Persisted Baseline Loading, Live Reset Propagation & Zero-Baseline Overwrite (`TST-SEN-034.1`, `BANALServiceRepositoryItpmsWiringTest`: `testPersistedBaselineLoaded_triggersLowPressureAlertAndDeduplicatesColdStartIo_tstSen034_1`, `testLiveResetSignalsZeroOverwriteAndErrorPreservation_tstSen034_1`, `testDeviceDataRepositoryResetBranches_emitBaselineChangedToBanalServiceRepository_tstSen034_1`, `testOutOfOrderAsyncIoLoad_staleCompletionIsDiscardedAndDoesNotOverwriteNewerBaseline_tstSen034_1`, `testDeviceDataRepositoryUpdateDevice_emitsBaselineChangedAndUpdatesLiveEstimator_tstSen034_1`)*:
     • Behavioral JVM unit tests (zero `src/main` reads) injecting a fake baseline loader (`DevicesDatabaseManager.getBaselineCircumference` delegate) into `BANALServiceRepository`: verify cold-start IO load deduplication (`pendingBaselineLoads`), loading persisted `2.130 m` baseline, and locking `C_start <= 2.115 m` (`-0.7%`) emits `TpmsAlertResult.LowPressure` (`WARNING` alert).
     • Verify async `withContext(itpmsIoDispatcher)` hydration applies results back on `repositoryScope` (Main thread) guarded by per-device monotonic generation tokens (`baselineLoadGenerations`) so stale out-of-order IO completions are discarded, resolving `<= 0.0` or `NaN` explicitly overwrites any prior non-zero baseline to `0.0`, transient loader `Throwable` preserves existing baseline, and `DeviceDataRepository.resetTirePressureBaseline(id, mm/null)`, `updateDevice()`, `notifyBaselineCircumferenceChanged()`, `CALIBRATION_FACTOR_CHANGED` (specific `DEVICE_ID` + `-1L` fallback), and `clearBreadcrumbs()` update live cached estimators immediately (safe at `SDK_INT == 0`).
  2. *Null/Non-Finite GNSS Skipping & GPS-Free Dual-Wheel Isolation (`TST-SEN-034.2`, `BANALServiceRepositoryItpmsWiringTest`: `testNullOrNonFiniteGnssAccuracyOrBearing_skipsIndirectTpmsWithoutFabricatingDefaults_tstSen034_2`, `testNullLocationAccuracyBearing_dualWheelTpmsStillDetectsRearPunctureGpsFree_tstSen034_2`)*:
     • Invoke `BANALServiceRepository.processItpmsTelemetryTick()` with `accuracy = null`/`NaN`/`-1.0`/`Infinity` or `bearing = null`/`NaN`/`-Infinity` and assert Section 1 (`IndirectTpmsEstimator`) skips samples with zero instantiation/accumulation (never fabricating `5.0`/`0.0`).
     • Invoke consecutive ticks for active `FRONT` + `REAR` wheel sensors with `location = null`, `accuracy = null`, `bearing = null` and assert Section 1 is skipped while Section 2 (GPS-free `DualWheelTpmsEstimator`) locks `startRatio` and detects a `>= 0.8%` rear puncture (`CRITICAL` alert).
  3. *9-Locale Baseline Parity & Architecture/Governance Contracts (`TST-SEN-034.3`, `BANALServiceRepositoryItpmsWiringTest`: `testBaselineCircumferenceChangedFlow_isBufferedAndDeliversWithoutDropping_tstSen034_3`; `EditDeviceWheelCircumferenceTest`, `TranslationParityTest`, `BANALServiceRepositoryMultiWheelTpmsTest`, `IndirectTpmsEstimatorTest`, `DualWheelTpmsEstimatorTest`, Contracts)*:
     • Verify buffered `baselineCircumferenceChanged` burst delivery, `TranslationParityTest` (`11/11` across all 9 locales), `EditDeviceWheelCircumferenceTest` (`8/8`), `ArchitectureConformanceContractTest` (`11/11`, `BANALServiceRepository.kt = 1,354 / 1,500`, `DeviceDataRepository.kt = 769 / 1,500`, `0` monolith growth), `DeadCodeEliminationContractTest` (`6/6`), `SourceTextReadingAuditContractTest` (`6/6`), and `SystemLiveTrackingRequirementsContractTest` (`4/4`).

### TST-CON-053: **PolarOnlineStreamingBridge Atomic Stream Guards, PMD ERROR_ALREADY_IN_STATE Recovery & Warning Suite** (`ATT-9161`) [Verified]
- **Requirement(s)**: `REQ-CON-053` (`REQ-CON-053.1`..`REQ-CON-053.4`)
- **Component**: `PolarPmdStreamCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamWarningEffect.kt`, `TrackingTabsScreen.kt`, `DevicesTabbedScreen.kt`, `PolarPmdStreamCoordinatorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `PolarPmdStreamWarningEffectTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-9161`
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Atomic 4-Stream Re-Entrancy, ACC Mutex Rollback & Job-Identity Cleanup (`TST-CON-053.1`, `PolarPmdStreamCoordinatorTest#testLaunchStream_enforcesAtomicSingleFlightAndPreAcquireRollback_TST_CON_053_1`, `PolarPmdStreamCoordinatorTest#testCancelledJobFinally_neverClobbersNewerSlotOrCleanup_TST_CON_053_1`, `PolarPmdStreamCoordinatorTest#testAtomicSlotCleanupAndPreAcquireException_preservesAccChannelOwnership_TST_CON_053_1`, `PolarOnlineStreamingBridgeTest#testReentrantHandshakeBurst_preventsHrToggleStormAndDuplicatePmdStarts_TST_CON_053_1`)*: Verify single subscription per stream (`HR`/`ACC`/`GYRO`/`PPI`) under rapid callback bursts with suspended settings probes, `ACC_OWNER_OFFLINE` & `preAcquire` exception CAS rollback, atomic `slotLock` cleanup before slot reopen, and `job === myJob` non-clobbering.
  2. *Bounded `ERROR_ALREADY_IN_STATE` Recovery (`TST-CON-053.2`, `PolarPmdStreamCoordinatorTest#testCollectPmdRecovery_handlesTypedCauseMessageAndResetsCounterOnSample_TST_CON_053_2`, `PolarOnlineStreamingBridgeTest#testPmdAlreadyInStateRecovery_recoversAccGyroAndPpiAfterStopStreamingAndBackoff_TST_CON_053_2`)*: Verify typed/cause/message detection, `stopStreaming` + `500/1000/2000 ms` backoff recovery on `ACC`/`GYRO`/`PPI`, first-sample retry reset, and immediate non-matching exit.
  3. *Deduplicated Warning, Snackbar UI & 9-Locale Parity (`TST-CON-053.3`, `PolarPmdStreamCoordinatorTest#testExhaustionDeduplicatesWarningAndRespectsMultiDeviceOwnership_TST_CON_053_3`, `PolarOnlineStreamingBridgeTest#testPmdAlreadyInStateExhaustion_emitsSingleDeduplicatedWarningAndClearsOnDisconnect_TST_CON_053_3`, `PolarPmdStreamWarningEffectTest#testPolarPmdStreamWarningEffect_displaysLocalizedSnackbarAndDismissesOnDisconnect_TST_CON_053_3`, `TranslationParityTest`)*: Verify single session-deduplicated warning on 3-retry exhaustion, multi-device ownership, disconnect dismissal, and 9-locale `polar_live_imu_stream_blocked_warning` parity.
  4. *Regression & Contract Guards (`TST-CON-053.4`)*: Verify `ATT-9176` PPI & `ATT-8994` `deactivateSensor()` tests, `ArchitectureConformanceContractTest` (`PolarOnlineStreamingBridge.kt` `1,259/1,500`, `0` protected monolith edits), `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `UiReflectionOnlyTestAuditContractTest`, and `SourceTextReadingAuditContractTest`.
- **Verification Evidence**: Gate 4 Bounce 1 (`ATT-9313` @ `79e9e1b1`, `Rule 7.5`, `0` `app/` drift) — `173/173 PASS` (`0` fail, `0` err, `0` skip) across 22 suites + `:app:assembleDebug` PASS + `0` new Kotlin warnings (`9/9` verbatim `TST-CON-053` methods verified); `4/4` Python safety linters PASS; clean-checkout `verify_requirement_governance.py --check-verified-evidence` EXIT `0`.

### TST-CON-054: **BLE HRM Silent-Connection Watchdog, Alias-Complete Unpair Teardown & Connected-GATT Discovery Suite** (`ATT-9155`) [Verified]
- **Requirement(s)**: `REQ-CON-054` (`REQ-CON-054.1`..`REQ-CON-054.4`)
- **Component**: `BleHrmSilentConnectionWatchdog.kt`, `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamCoordinator.kt`, `MyBTLEDevice.java`, `BTLEHeartRateDevice.java`, `BTSearchForNewDevicesEngine.java`, `DeviceDataRepository.kt`, `DeviceManager.java`, `BleHrmSilentConnectionWatchdogTest.kt`, `BTLEHeartRateDeviceTest.kt`, `MyBTLEDeviceLifecycleTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `BTSearchForNewDevicesEngineTest.kt`, `DeviceManagerTeardownTest.kt`
- **Jira**: `ATT-9155`
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Alias-Complete Unpair/Delete Teardown (`TST-CON-054.1`, `PolarOfflineGattCoordinatorCoexistenceTest#testForceDisconnectDeviceAndDisconnectUnpairedSessions_clearsTargetAliasesWithoutHarmingSibling_TST_CON_054_1`, `DeviceManagerTeardownTest#testPairingChangedUnpair_whenAbsentFromRemoteDevices_forceDisconnectsPolarSession_TST_CON_054_1`, `MyBTLEDeviceLifecycleTest#testOnConnectionStateChange_afterShutDown_immediatelyClosesLateGattCallback_TST_CON_054_1`, `MyBTLEDeviceLifecycleTest#testDisconnectFromGatt_whenOnHandlerLooper_closesSynchronouslyWithoutPosting_TST_CON_054_1`, `MyBTLEDeviceLifecycleTest#testDisconnectFromGatt_whenPostReturnsFalse_stillDisconnectsClosesAndRunsCallback_TST_CON_054_1`)*: Verify MAC + 8-hex alias teardown without harming sibling paired Polar sensors (`clearRegistrations` `true` vs `false`), `pairingChanged` cleanup when absent from `mMyRemoteDevices`, same-looper sync close (`onGattClosed == null`), `!posted` sync close fallback, and strict `mIsShutDown == true` late-callback close.
  2. *Connected-GATT Discovery Fallback (`TST-CON-054.2`, `BTSearchForNewDevicesEngineTest#testStartAsyncSearch_probesConnectedGattHrmSkipsPairedAndRejectsNonMatching_TST_CON_054_2`, `BTSearchForNewDevicesEngineTest#testStartAsyncSearch_missingBluetoothConnectPermission_skipsConnectedProbeGracefully_TST_CON_054_2`)*: Verify unpaired Polar session release on `HRM` search, `getConnectedDevices(GATT)` HRM discovery, paired-DB skip, non-matching probe rejection/close, and permission fallback.
  3. *Silent-HR Watchdog & CCCD Retry (`TST-CON-054.3`, `BleHrmSilentConnectionWatchdogTest#testWatchdogEscalation_reenablesCccdThenDisconnectsAndStops_TST_CON_054_3`, `BleHrmSilentConnectionWatchdogTest#testOnHrPacketReceived_disarmsInitialAndPostResubscribeStates_TST_CON_054_3`, `BleHrmSilentConnectionWatchdogTest#testStaleGeneration_isRejectedAfterResetDisarmOrRearm_TST_CON_054_3`, `BTLEHeartRateDeviceTest#testSilentHrWatchdog_cccdRetryPauseDeferralDisconnectAndPolarShutDown_TST_CON_054_3`, `BTLEHeartRateDeviceTest#testOnDescriptorWriteCompleted_ignoresStaleGattWithoutBurningRetryFlag_TST_CON_054_3`, `PolarOnlineStreamingBridgeTest#testSilentHrWatchdog_resubscribesThenDisconnectsPreservingRegistrationsAndShutDownDisconnectsAllAliases_TST_CON_054_3`, `PolarOnlineStreamingBridgeTest#testStaleWatchdogGenerationTick_afterDisconnectReconnectOrHrPacket_doesNotCancelHrSlotOrLeakTimer_TST_CON_054_3`)*: Verify `12 s` escalation (`REENABLE_CCCD -> DISCONNECT_AND_RECONNECT`), monotonic generation & `silentHrWatchdogLock` stale-tick rejection, HR packet disarm, stale-GATT descriptor ignore & failed CCCD retry, `isGattPaused()` deferral, `clearRegistrations = false` reconnect preservation, and `shutDown()` Polar alias teardown.
  4. *Monolith Ceilings & Contract Guards (`TST-CON-054.4`)*: Verify `ArchitectureConformanceContractTest` (`DeviceManager.java` `1,707/1,708`, `PolarOnlineStreamingBridge.kt` `1,382/1,500`, `0` edits to `TrackerService.java`/`DevicesDatabaseManager.java`), `DetailedDesignParityContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, and `SourceTextReadingAuditContractTest` (`0` new `src/main` reads; `Localization Parity: N/A`).
- **Verification Evidence**: Gate 4 Bounce 1 (`ATT-9351` @ `98a377f6`, `Rule 7.5`, `0` `app/` drift) — `184/184 PASS` (`0` fail, `0` err, `0` skip) across 19 suites + `:app:assembleDebug` PASS + `0` new production Kotlin warnings (`14/14` verbatim `TST-CON-054` methods verified); `4/4` Python safety linters PASS; `--check-verified-evidence` EXIT `0`.

### TST-TRK-091: **Decoupled Stop-Workout Post-Processing, Short-Service Notification & Edit-Safe Refresh Suite** (`ATT-9154`) [Verified]
- **Requirement(s)**: `REQ-TRK-091` (`REQ-TRK-091.1`..`REQ-TRK-091.4`)
- **Component**: `WorkoutPostProcessingCoordinator.kt`, `WorkoutPostProcessingService.kt`, `WorkoutPostProcessingNotificationManager.kt`, `TrackerService.java`, `WorkoutRepository.kt`, `EditWorkoutViewModel.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `WorkoutPostProcessingCoordinatorTest.kt`, `WorkoutPostProcessingNotificationManagerTest.kt`, `WorkoutPostProcessingServiceTest.kt`, `TrackerServiceFinalizationTest.kt`, `EditWorkoutViewModelTest.kt`, `WorkoutRepositoryDeferredExportTest.kt`, `MergedManifestFgsAuditTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-9154` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Non-Blocking Stop & Armed-Token Isolation (`TST-TRK-091.1`)*: Verify `TrackerService.endWorkout()` fires `TRACKING_FINISHED_INTENT` without blocking on Polar/HRV/ERTS; verify sequential stage execution (`H10(showOwnNotification=false)` -> `Verity(showOwnNotification=false)` -> `HRV` -> `ERTS` -> `refreshWorkout` -> `workoutFinished` -> deferred `exportWorkout`), fault isolation, single-worker back-to-back serialization, and back-to-back armed-token preservation.
  2. *Atomic Export Deferral & Edit-Safe Refresh (`TST-TRK-091.2`)*: Verify single-lock `deferExportIfPostProcessing`/`completePostProcessing` defers early `saveWorkout()` exports until post-processing completes with fresh SQLite metadata+telemetry (and exports immediately post-completion), `rpe`/`feel` in-memory parity, and telemetry-only `EditWorkoutViewModel` merge preserving user edits and explicit `createNewCluster()` `clusterId` reloads without suggestion rewrites.
  3. *`shortService` FGS, Progress Notification `8047`/`8048` & 9-Locale Parity (`TST-TRK-091.3`)*: Verify `WorkoutPostProcessingNotificationManager` stage progress (`8047`), API 33+ permission gate, completion cancel (`8047`) & dedicated auto-cancel failure warning (`8048`); verify `shortService` API 34+ promotion, `onTimeout()`, `ForegroundServiceStartNotAllowedException` fallback, `MergedManifestFgsAuditTest` (`0` `connectedDevice`), and 9-locale parity (`0` emojis).
  4. *LoC & Governance Contracts (`TST-TRK-091.4`)*: Verify `TrackerService.java < 3,539 / 3,559`, `WorkoutRepository.kt <= 5,117`, `PolarVeritySenseOfflineController.kt <= 1,500`, `0` edits to `DeviceManager.java`/`DevicesDatabaseManager.java`, `0` new `src/main` reads, and `SYSREQ-TRK-005`/`SYSREQ-DEX-007` contracts.
- **Verified Evidence (`7725517b`, `ATT-9361` PASS, `25/25`)**: `WorkoutPostProcessingCoordinatorTest#testEnqueue_executesStrictStageOrderWithoutBlockingCaller_tstTrk091_1`, `WorkoutPostProcessingCoordinatorTest#testEarlySaveDuringPostProcessing_defersExportAndExportsFreshRowAfterWorkoutFinished_tstTrk091_2`, `WorkoutPostProcessingCoordinatorTest#testFaultIsolation_stageExceptionContinuesPipelineClearsActiveStateAndPostsWarning_tstTrk091_1`, `WorkoutPostProcessingCoordinatorTest#testBackToBackEnqueue_serializesSingleWorkerAndNeverClobbersNewWork_tstTrk091_1`, `WorkoutPostProcessingCoordinatorTest#testDeferredExport_preservesExplicitSyncToHealthConnectTrueAndFalseOverrides_tstTrk091_2`, `WorkoutPostProcessingCoordinatorTest#testEnqueueDuringIdleFinalization_hostLockPreventsFgsOrNotificationClobber_tstTrk091_1`, `WorkoutPostProcessingCoordinatorTest#testShouldRestartDrain_preservesBatchHadWarningFromPrecedingJob_tstTrk091_1`, `WorkoutPostProcessingNotificationManagerTest#testShowStageProgress_api33PermissionGranted_postsProgressNotification_tstTrk091_3`, `WorkoutPostProcessingNotificationManagerTest#testShowStageProgress_api33PermissionDenied_suppressesNotifyAndWarning_tstTrk091_3`, `WorkoutPostProcessingNotificationManagerTest#testShowWarningAndCancelNotification_usesDedicatedWarningId8048AndCancels8047_tstTrk091_3`, `WorkoutPostProcessingServiceTest#testDetermineForegroundServiceType_api34ReturnsShortService_api33ReturnsZero_tstTrk091_3`, `WorkoutPostProcessingServiceTest#testOnTimeout_stopsForegroundCleanlyWithoutCancellingActiveCoordinatorWork_tstTrk091_3`, `WorkoutPostProcessingServiceTest#testOnTimeoutSingleArg_api34ShortServiceStopsForegroundWithoutCancellingCoordinatorWork_tstTrk091_3`, `WorkoutPostProcessingServiceTest#testOnStartCommand_rendersActiveCurrentProgressInsteadOfResettingToZeroOfOne_tstTrk091_3`, `WorkoutPostProcessingServiceTest#testStartForPostProcessing_fgsStartDisallowed_fallsBackCleanlyWithoutThrowing_tstTrk091_3`, `TrackerServiceFinalizationTest#testEndWorkout_decouplesPostProcessingAndBroadcastsTrackingFinishedImmediately_tstTrk091_1`, `PolarH10OfflineControllerTest#testStopAndBackfillWorkout_staleArmedTokenPreservesNewerWorkoutArmedStateAndSuppressesNotification_tstTrk091_1`, `PolarVeritySenseOfflineControllerTest#testStopAndBackfillWorkout_staleArmedTokenPreservesNewerWorkoutArmedStateAndSuppressesNotification_tstTrk091_1`, `MergedManifestFgsAuditTest#testManifest_postProcessingService_declaresShortServiceAndExportedFalse_tstTrk091_3`, `WorkoutRepositoryDeferredExportTest#testSaveWorkout_whenPostProcessingActive_persistsMetadataAndRpeFeelWhileDeferringExport_tstTrk091_2`, `WorkoutRepositoryDeferredExportTest#testSaveWorkout_whenPostProcessingInactive_triggersExportImmediately_tstTrk091_2`, `WorkoutRepositoryDeferredExportTest#testLoadFreshWorkoutForExport_readsDirectSqliteCursorAndClosesCleanly_tstTrk091_2`, `WorkoutRepositoryDeferredExportTest#testSaveWorkout_deferredExportPreservesSyncToHealthConnectTrueAndFalseOverrides_tstTrk091_2`, `EditWorkoutViewModelTest#testReactiveTelemetryRefresh_preservesAllUserEditsAndSkipsSuggestionRewrites_tstTrk091_2`, `EditWorkoutViewModelTest#testCreateNewClusterInitialReload_adoptsClusterIdWhileAllWorkoutsRefreshPreservesIt_tstTrk091_2`.

### TST-FIL-039: **PhoneCarriageClassifier Front-Hip Belt Discrimination, Viewing-Inclination Glance & Active-Footstrike Cycling Mask Suite** (`ATT-9347`) [Verified]
- **Requirement(s)**: `REQ-FIL-039`
- **Component**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifierTest.kt`, `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFusedTest.kt`
- **Jira**: `ATT-9347` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit | **Localization Parity**: `N/A - no UI strings`
- **Test Description**:
  1. *Active-Footstrike Cycling Mask & Step-Batching Impact Guard (`TST-FIL-039.1`, `PhoneCarriageClassifierTest`)*: Verify `0%` (`0 s`) `BIKE_FRONT_POCKET`/`BIKE_*` under `Sport = RUN` (`< 5.5 m/s`, no wheel), under `sportHint == UNKNOWN` (post-crash fallback) and `>= 5.5 m/s` GNSS spikes with active `>= 2.25 Hz` footstrikes (step detector or accel), and during `> 2.2 s` step-batching gaps with `impactRms >= 1.8 m/s²`, while preserving uphill MTB (`impactRms < 1.2`) and footstrike-free brick overrides (`wheel > 0.5` or `speed >= 5.5`).
  2. *Viewing-Inclination Glance Guard (`TST-FIL-039.2`, `PhoneCarriageClassifierTest`)*: Verify `0` false glances on vertical-plane belt slips/rolls (`18°` and `35°` lateral roll at `recIncl > 60°`), hill torso-lean (`85° -> 68°`), and post-glance stow (`20° -> 75°`), while preserving genuine wrist glances (`70–72° -> 18–35°`, `55° -> 30°`) and cyclic thigh-flexion rejection.
  3. *Front-Hip Belt 45-Min Replay, 9-Mode Separation, `10 s` Dwell & Contracts (`TST-FIL-039.3`, `PhoneCarriageClassifierTest`, `SpeedAndLocationDevice_ATTFusedTest`, Contracts)*: Verify synthetic Salomon front-hip belt run (`155–170 spm`, `swingSd 10–17.5°`, `yaw 0.45–1.05`, `baro 0.25–0.58`, vertical slips + obstacle walk + wrist glance) achieves `>= 85%` `RUN_WAIST_HIP` with low transitions (`<< 111`), `0` switches on `8 s` non-glance bounce burst (`10 s` dwell), intact `0 ms` wrist glance, `4 s` obstacle walk, and full 9-mode separation (bicep armband `17°`, unglanced handheld `18.4°`/`0.74`/`0.83`, post-glance `SpeedAndLocationDevice_ATTFusedTest:865` `yaw = 0`, `BIKE_*`, `STATIONARY`), plus architecture/governance contracts (`0` edits to `SpeedAndLocationDevice_ATTFused.kt`/monoliths, `0` `src/main` test reads).

---

### TST-TRK-092: **Non-Blocking TrackerService.onDestroy() During Active Finalization & Idempotent Post-endWorkout Cleanup Regression Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-034`
- **Component**: `TrackerService.java`, `TrackerServiceFinalizationTest.kt`
- **Jira**: `ATT-6178`
- **Test Description**:
  1. *Non-Blocking External `onDestroy()` During In-Flight `ACTION_STOP` Finalization (`TST-TRK-092.1`, `TrackerServiceFinalizationTest.kt#testOnDestroy_duringActiveActionStopFinalization_returnsNonBlockingWithoutPrematureUnbind_tstTrk092_1`)*:
  • Configure `TestableTrackerService` with a queued Main-Thread `Runnable` dispatcher (simulating Android's Main Looper where posted cleanup cannot run while the caller thread is blocked) and hold `endWorkout(...)` on a `CountDownLatch`.
  • Invoke `handleActionStop()` (`mIsStopping == true`, `mFinalizationFuture` incomplete), then invoke `onDestroy()` on the caller thread.
  • Verify `onDestroy()` returns in `< 100 ms` (zero 2.0-s deadlock), `mScheduler`/`mRrFlushExecutor`/`mDbExecutor` are shut down (`REQ-TRK-076.1`), and `safeUnbindBanalService()`, `performStopForeground()`, and `releaseWakeLock()` have NOT yet been called while `endWorkout(...)` is still active (`REQ-TRK-033.2`, `REQ-TRK-034.3`).
  • Also verify the race where `endWorkout(...)` finishes and posts its cleanup Runnable to the Main-Thread queue *before* `onDestroy()` runs: `onDestroy()` still returns `< 100 ms` without deadlocking on `future.get(...)`.
  2. *Deferred Idempotent Fault-Isolated Cleanup After Prior `onDestroy()` (`TST-TRK-092.2`, `TrackerServiceFinalizationTest.kt#testPostEndWorkoutCleanupAfterOnDestroy_runsIdempotentlyWithoutExceptionOnDestroyedService_tstTrk092_2`)*:
  • Release `endWorkout(...)` after `onDestroy()` has returned, then drain the queued Main-Thread cleanup Runnable while `performStopForeground()` / `performStopSelf()` throw `IllegalStateException` and `unbindService()` throws `IllegalArgumentException` (simulating an already-destroyed `Service`).
  • Verify strict `endWorkout` $\rightarrow$ `unbindService` $\rightarrow$ `performStopForeground` $\rightarrow$ `performStopSelf` order, `mBanalService == null`, `wakeLock.release()` invoked once (idempotent on repeated cleanup), `finalizationFuture.isDone() == true`, and zero uncaught exceptions.
  3. *`endWorkout` Failure Fallback, Completed-Future Safety & Contract Parity (`TST-TRK-092.3`, `TrackerServiceFinalizationTest.kt#testOnDestroy_duringActionStopWithEndWorkoutFailureOrCompletedFuture_completesCleanupSafely_tstTrk092_3`)*:
  • Verify (a) an exception thrown inside `endWorkout(...)` after external `onDestroy()` still runs fault-isolated cleanup and completes `finalizationFuture`, and (b) `onDestroy()` when `mIsStopping == true` and `mFinalizationFuture.isDone() == true` safely releases any remaining `wakeLock`/`BANAL` binding without blocking.
  • Verify `TrackerService.java <= 3,559` LoC (`ArchitectureConformanceContractTest`) and SWE.3 living design parity (`DetailedDesignParityContractTest`).

### TST-TRK-093: **Polar H10 Recording Anchor, Trailing RR Trimming, Post-Backfill Physiology Recomputation & Legacy GATT Release Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-093`
- **Component**: `PolarH10OfflineController.kt`, `PostWorkoutPhysiologyRecomputer.kt`, `WorkoutPostProcessingCoordinator.kt`, `TrackerService.java`, `MyBTLEDevice.java`, `MyAccumulatorSensor.java`
- **Jira**: `ATT-8124`
- **Test Description**:
  1. *Exact `startRecording` Time Anchor & Lifecycle (`TST-TRK-093.1`, `PolarH10OfflineControllerTest.kt#testExecuteArming_capturesPostStartRecordingAnchorAndResetsLifecycle_TST_TRK_093_1`)*:
  • Verify `recordingStartTimeMs` is captured strictly after `api.startRecording(...)` completes (offset `+8 s` from `sessionStartTimeMs`), cleared in `clearArmedStateIfTokenMatches()` / `resetForTesting()` / unarmed `setSessionStartTimeMs()`, and used as default anchor in `stopAndBackfillWorkout()`, `stopAndBackfillWorkoutBlocking()`, and `recordPendingSync()`.
  2. *3-Case Trailing RR Trimming, Clock-Inversion & Non-Positive RR Guards (`TST-TRK-093.2`, `PolarH10OfflineControllerTest.kt#testBuildAnchoredSamples_handlesThreeEndTimeCasesAndInvalidRr_TST_TRK_093_2`, `#testStopAndBackfillWorkout_trimsTrailingBeats_skipsEmptyDbWrite_andRespectsRunPostBackfillRecompute_TST_TRK_093_2`, `#testSyncPendingWorkout_trimsByExpectedEndMsAndRunsPhysiologyBeforeHrv_TST_TRK_093_2`)*:
  • Verify `buildAnchoredSamples`, `stopAndBackfillWorkout(..., endTimeMs)`, and `syncPendingWorkout()` (`expectedEndMs`) handle `endTimeMs <= 0L` (no trim), `0 < endTimeMs < anchorMs` (clock inversion logs warning & keeps beats), `endTimeMs >= anchorMs` (trims `beatTs > endTimeMs`), ignore `rr <= 0`, and skip DB patching when trimmed intervals are empty.
  3. *Single-Pass Post-Backfill Physiology Recomputation & Consistent `h10Armed` Snapshot (`TST-TRK-093.3`, `PostWorkoutPhysiologyRecomputerTest.kt`, `WorkoutPostProcessingCoordinatorTest.kt#testPostBackfillPhysiologyRecompute_runsStrictlyOnceAfterBackfillAndBeforeHrvReloadExport_TST_TRK_093_3`, `TrackerServiceFinalizationTest.kt#testEndWorkout_consistentSingleH10ArmedSnapshot_defersHrPhysiologyAndPassesAnchorAndEndTime_TST_TRK_093_3`)*:
  • Verify `PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill()` filters nullable/non-finite/non-positive `hrSamples` (persisting HR `MIN`/`MAX`/`AVG` with valid count), resolves `BSportType` via `getWorkoutSummaryUpdateData()`, and persists `EFFICIENCY_FACTOR`, `AEROBIC_DECOUPLING`, and `CardioPowerDynamicsRecord`.
  • Verify `TrackerService.endWorkout()` captures `h10Armed` once for both `finalizeLiveSession()` deferral and `PostProcessingRequest`, and `WorkoutPostProcessingCoordinator` runs H10 backfill (`runPostBackfillRecompute = false`) $\rightarrow$ `runPhysiologyRecompute` (1x) $\rightarrow$ `runHrvRecompute` (1x) before `reloadWorkout` and export.
  4. *Sync Legacy GATT Release, Sensor Re-Registration, `TST-CON-054.3` & Part 1/2 Lock (`TST-TRK-093.4`, `MyBTLEDeviceLifecycleTest.kt#testPauseGattOperations_synchronouslyReleasesGattUnregistersSensorsAndReconfiguresOnReconnect_TST_TRK_093_4`, `BTLEHeartRateDeviceTest.kt`, `MyAccumulatorSensorTest.kt`, `TrackerServiceResumeTest.kt`)*:
  • Verify full `pauseGattOperations() -> sync disconnect/close outside mGattLock -> unregisterSensors() -> paused onConnectionStateChange close outside lock -> resumeGattOperations() (preserving mIsBackgroundReconnecting) -> onServicesDiscovered -> readNextCharacteristic -> configureServices -> CONNECTED_WITH_SERVICE` cycle and reconcile `BTLEHeartRateDeviceTest.kt` (`TST-CON-054.3`).
  • Verify null-device fresh `MyIntegerAccumulatorSensor(null, SensorType.LAP_NR, false, 1)` (`1 -> 2 -> 1`), `TrackerServiceResumeTest` `RESUME_SERVICE_RECREATION` zero-reset, `TrackerService.java <= 3,559` LoC, `MyBTLEDevice.java <= 1,500` LoC, and `DetailedDesignParityContractTest`.

---

### TST-CON-055: **Radar Quick-Switch Modal Callback Forwarding & StVZO-Safe Deferred BLE GATT Registration Verification (`ATT-8123`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-055` (`REQ-CON-055.1`–`REQ-CON-055.4`)
- **Component**: `RadarLightQuickSwitchSheet.kt`, `BTLERadarDevice.java`, `RadarLightQuickSwitchSheetTest.kt`, `BTLERadarDeviceTest.kt`, `RadarLightModeGridTest.kt`
- **Jira**: `ATT-8123` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Implemented Unit Tests**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/radar/RadarLightQuickSwitchSheetTest.kt`: `testModalWrapper_clickingAllSnoozePresets_triggersOnSnoozeSelectedBeforeDismiss_TST_CON_055_1`, `testModalWrapper_activeSnooze_clickingResume_triggersOnResumeAudioBeforeDismiss_TST_CON_055_1` (`11/11` PASS)
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERadarDeviceTest.kt`: `testConfigureServices_legacyStvzoModel_defersRegistrationAndClampsActiveDayFlashToSolid_TST_CON_055_2`, `testConfigureServices_rearVueStvzoModel_defersRegistrationSetsHardwareBrakeAndClampsToSolid_TST_CON_055_2`, `testConfigureServices_nonStvzoModel_registersWithStvzoFalseAndPreservesDayFlash_TST_CON_055_2`, `testConfigureServices_modelNumberReadFailureNull_fallsBackImmediatelyOnce_TST_CON_055_3`, `testConfigureServices_timeoutFallbackAndLateStvzoSelfHeal_plusIdempotentLateTimeout_TST_CON_055_3`, `testDisconnectAndShutdown_clearsStvzoStateAndDropsLateModelReadOrTimeout_TST_CON_055_4`, `testCompleteLightControlRegistration_concurrentDisconnectDuringClampedSetLightMode_doesNotResurrectSolid_TST_CON_055_4` (`64/64` PASS)
  - `RadarLightModeGridTest` (`3/3`), `RadarLightControllerTest` (`39/39`), `UiReflectionOnlyTestAuditContractTest` (`4/4`), `ArchitectureConformanceContractTest` (`11/11`), `JavaCoreMigrationStrategyContractTest` (`5/5`), `SystemLiveTrackingRequirementsContractTest` (`4/4`)
- **Test Description**:
  1. *Modal Wrapper Audio-Snooze & Resume Ordering (`TST-CON-055.1`, `RadarLightQuickSwitchSheetTest.kt`)*:
     • Render `RadarLightQuickSwitchSheet(...)` directly via Robolectric Compose; click `SNOOZE_15M`, `SNOOZE_30M`, `FAST_ONLY`, `REST_OF_RIDE`, and `RadarSnooze_Resume`; assert exact callback fires strictly before `onDismiss()` and compile-time required action parameters prevent omitted callbacks.
  2. *Deferred StVZO GATT Registration & Initial Mode Clamping (`TST-CON-055.2`, `BTLERadarDeviceTest.kt`)*:
     • With active `DAY_FLASH` and `UUID_CHARACTERISTIC_MODEL_NUMBER` exposed: assert `configureServices` neither registers with `RadarLightController` nor enqueues a light write before model-number completion; deliver `"Garmin Varia RTL516"` (legacy) and `"010-03180-10"` (RearVue 820) and assert single registration with `isStvzo == true` (plus `hasHardwareBrakeSensor == true` on RearVue) and clamped `SOLID` command (never `DAY_FLASH`).
  3. *Absent-DIS, Read-Error & Timeout Fallbacks (`TST-CON-055.3`, `BTLERadarDeviceTest.kt`)*:
     • Verify absent DIS/Model-Number registers immediately preserving FIFO CCCD-before-write order; verify `null`/empty read and `MODEL_NUMBER_READ_TIMEOUT_MS` (`3000L`) timeout each trigger exactly one idempotent `(false, false)` fallback registration + active mode dispatch, and late StVZO read self-heals `DAY_FLASH` to `SOLID`.
  4. *Disconnect/Shutdown Race Isolation & Architecture Contracts (`TST-CON-055.4`, `BTLERadarDeviceTest.kt`, `RadarLightModeGridTest.kt`, `RadarLightControllerTest.kt`, `ArchitectureConformanceContractTest.kt`, `JavaCoreMigrationStrategyContractTest.kt`)*:
     • Verify `disconnectFromGatt()` / `shutDown()` clears `mIsStvzo`/`mModelNumber`, drops late model-number updates or fallback timers without re-registering, and prevents clamped post-disconnect `setLightMode()` races from resurrecting `SOLID` in `RadarLightController`/`RadarStateRepository`; verify `BTLERadarDevice.java <= 1,500` LoC.
- **Verification Evidence**: Verified (`ATT-8123` Bounce 1, Gate 3 `ATT-9606` PASSED; `141/141` targeted unit/Compose/contract tests PASS across 8 suites via `./gradlew :app:testDebugUnitTest --rerun`, `0` fail, `0` err, `0` skip, `0` new compiler warnings; `BTLERadarDevice.java` `1,467/1,500` LoC; Light/Dark Compose preview + live `emulator-5554` boot smoke `CRASH_LOGCAT_CLEAN` visually inspected per `Rule 8.5`; `auto_gate.py` SHA-256 evidence bundle at `docs/engineering/test_evidence/tickets/ATT-8123/manifest.json`).

### TST-TRK-094: **TrackerService Shutdown Dedup, Type-Safe Lap Extraction, PostWorkoutFinalizer, TrainingApplication Teardown & Parameterized Strava Sync Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-094`
- **Component**: `TrackerService.java`, `PostWorkoutFinalizer.kt`, `LapTelemetryExtractor.kt`, `BANALService.java`, `TrainingApplication.java`, `StravaSyncScheduler.kt`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`
- **Jira**: `ATT-6034`
- **Test Description**:
  1. *Type-Safe Lap Extraction & Shutdown Dedup (`TST-TRK-094.1`, `LapTelemetryExtractorTest.kt`, `TrackerServiceFinalizationTest.kt`)*:
  • Verify `LapTelemetryExtractor.extract(...)` accepts all `Number` subtypes (`Integer`, `Long`, `Float`, `Double`), guards non-numeric/`NaN`/`Infinity`/`null` without `ClassCastException`, preserves `"??:??:??"` / `"0"` fallback strings, and computes `0.0` speed when `lapTimeSeconds <= 0`; verify `handleActionStop()`/`onDestroy()` preserve all `ATT-6178`/`REQ-TRK-033`/`034`/`076` invariants.
  2. *`PostWorkoutFinalizer` Characterization & Delegation Parity (`TST-TRK-094.2`, `PostWorkoutFinalizerTest.kt`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResumeTest.kt`)*:
  • Verify all 7 fault-isolated finalization stages (map/streams, extrema/apex, aerobic efficiency/decoupling/power-HR dynamics gated on `deferHrPhysiologyToPostProcessing` + immediate NP/IF/TSS/Work/Wkg, `guessCommuteAndTrainer` XOR truth table, identity/clustering/auto-naming/milestones, converged wheel circumferences, diagnostic JSON) and `TrackerService` 0-/1-/4-arg delegation parity.
  3. *`TrainingApplication` Teardown Helper Ordering (`TST-TRK-094.3`, `TrainingApplicationTrackingTest.kt`)*:
  • Verify `stopTracking()` clears pause locks strictly before `ACTION_STOP`/`stopService` dispatch and resets `cTrackingMode = READY` / `sActiveWorkoutId = -1` / `clearBreadcrumbs` strictly after dispatch, `resetTrackingStateOnAbort()` works statically with null/non-null `cAppContext`, `onTrackingAborted()` cancels notification and broadcasts `RESET_ACCUMULATORS_INTENT`, and `trackingStopped()` leaves pause/mode flags untouched.
  4. *Parameterized Strava Sync Prefs, Shared Worker Scheduler & Monolith Ratchets (`TST-TRK-094.4`, `StravaSyncSchedulerTest.kt`, `Strava*SyncWorkerTest.kt`, `TrainingApplicationTrackingTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
  • Verify all 18 Strava `Equipment`/`Routes`/`Segments` preference methods preserve keys, distinct `"never"` string resources, and null-pref safety; verify `StravaSyncScheduler` preserves `WORK_NAME`, `TAG`, WorkManager availability guard, cancellation, and backoff constraints across all 3 workers; verify tightened `TrackerService.java <= 3,220` & `TrainingApplication.java <= 2,050` LoC baselines and `DetailedDesignParityContractTest`.

---

### TST-TRK-095: **Race-Free `TrackerService` & `WorkoutPostProcessingService` FGS Handshake and Multi-OS/9-Locale Sweep Verification Suite (`ATT-9498`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-095`
- **Component**: `TrackerService.java`, `TrainingApplication.java`, `WorkoutPostProcessingService.kt`, `WorkoutPostProcessingCoordinator.kt`, `MultiLocaleUiSweepAndroidTest.kt`, `docs/architecture/detailed_design/tracker_service.md`
- **Jira**: `ATT-9498`
- **Test Description**:
  1. *`TrackerService` Pending-FGS Handshake, Early Promotion & FIFO `mWorkoutID` Resolution (`TST-TRK-095.1`, `TrainingApplicationTrackingTest.kt`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`)*:
  • Verified via `TrainingApplicationTrackingTest#testStopTracking_whenFgsStartPendingAndServiceNotYetRunning_suppressesStopService_TST_TRK_095_1` and `#testStartTracking_afterDeferredPreStartStop_clearsPendingStopForNewStart_TST_TRK_095_1`: `stopTracking()` suppresses `stopService()` while `TrackerService.isRunning() == false` and FGS start is pending, preserving idle fallback `stopService()` and clearing pending stop on re-start.
  • Verified via `TrackerServiceFinalizationTest#testOnStartCommand_deferredPreStartStop_promotesForegroundThenStopsWithoutCreatingWorkout_TST_TRK_095_1`, `#testOnStartCommand_promotesBeforeDbSubmit_andRapidActionStopResolvesFifoWorkoutId_TST_TRK_095_1`, `#testActionStopPendingFgsGuard_andStaleTeardownSuppressedWhenNewFgsStartPending_TST_TRK_095_1`, and `TrackerServiceResilienceTest` (`7/7`): `onStartCommand()` promotes unconditionally before `mDbExecutor.submit()`, executes promote$\rightarrow$demote$\rightarrow$`stopSelf()` with `0` DB init on deferred pre-start stop, preserves `intent == null && !hasBackgroundLocationPermission()` abort, satisfies pending FGS in `handleActionStop()`, blocks stale teardown when a new FGS start is pending, and resolves FIFO-created `mWorkoutID` in `enqueueAsyncFinalization()` to commit `FINISHED = 1`.
  2. *`WorkoutPostProcessingService` Generation-Guarded FGS Lifecycle (`TST-TRK-095.2`, `WorkoutPostProcessingServiceTest.kt`, `WorkoutPostProcessingCoordinatorTest.kt`)*:
  • Verified via `WorkoutPostProcessingServiceTest#testFastDrainBeforeOnStartCommand_suppressesStopServiceThenPromotesAndStopsWithStartId_TST_TRK_095_2`, `#testReEnqueueBeforeOnStartCommand_clearsPendingStopAndAllowsPostPromotionStop_TST_TRK_095_2`, `#testActionStopPendingFgsAndPromoteFailureCleanup_TST_TRK_095_2`, and `WorkoutPostProcessingCoordinatorTest` (`8/8`): fast-drain `stopIfRunning()` suppresses `appContext.stopService(...)` while `pendingFgsGeneration > 0L`, `onStartCommand()` promotes before `demoteForeground()` + `performStopSelf(startId)`, re-enqueue clears pending stop, `ACTION_STOP` with pending FGS promotes first, promote-failure resets `pendingFgsGeneration`, and `onTimeout` coordinator isolation remains intact.
  3. *Multi-OS Android 12/15/16 & 9-Locale Sweep Verification (`TST-TRK-095.3`, `MultiLocaleUiSweepAndroidTest.kt`, `tools/run_multi_os_emulator_sweep.py`)*:
  • Verified via `:app:assembleDebug :app:assembleDebugAndroidTest` (`BUILD SUCCESSFUL`), `tools/test_run_multi_os_emulator_sweep.py#test_att_9498_uiautomator_dump_retries_transient_error_and_api31_merged_bounds`, and live `docs/engineering/emulator_sweeps/2026-11-22_sweep.md` achieving `12/12` `PASS` across `Pixel_5_Android_12` (`en`), `Pixel_8_Android_15` (`de` + all 9 locales `en..pt`), and `Pixel_7_Android_36` (`fr`, `/data/local/tmp/att_sweep_dump.xml`) with `0` `ForegroundServiceDidNotStartInTimeException` crashes, `0` logcat/bounds defects, and `12/12` `Rule 8.5` PNG audits.
  4. *Monolith Ceilings & SWE.3 Parity (`TST-TRK-095.4`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`)*:
  • Verified via `ArchitectureConformanceContractTest` (`11/11`) and `DetailedDesignParityContractTest` (`3/3`): `TrackerService.java = 3,207 <= 3,220` LoC, `TrainingApplication.java = 2,040 <= 2,050` LoC, and `docs/architecture/detailed_design/tracker_service.md` (`v1.0.11`) code parity (`REQ-PRO-114`).

---

### TST-FIL-040: **Pocket-Compression Barometer Dispersion Anti-Aliasing, 3-State Startup Calibration Grace & ERTS Low-Speed Braking Slope Verification (`ATT-9602`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-040` (`REQ-FIL-040.1`–`REQ-FIL-040.4`)
- **Component**: `BarometricBucketDispersion.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `KinematicAltitudeSlopeEkf.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`
- **Jira**: `ATT-9602` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Verified Test Methods**: `BarometricBucketDispersionTest#calmHandlebarAndSteepAlpineDescent_keepZeroDispersionAndEmitExactBucketMedian`, `BarometricBucketDispersionTest#pocketCompression092HzWave_inflatesDispersionAndSuppressesBeatAliasing`, `BarometricBucketDispersionTest#dispersionVarianceDecaysSmoothlyWhenPocketWaveStops`, `BarometricBucketDispersionTest#guardsNonFiniteBackwardTimestampPauseGapSmallBucketsAndDelayedFlush`, `KinematicAltitudeSlopeEkfTest#testCorruptedBaroDispersion_doesNotVetoFlatDemAndRecoversCleanly_TST_FIL_040_2`, `AltitudeAndSlopeDevice_ATTFusedTest#testPocketCompressionAliasing_flatAndFullLoop_eliminatesPhantomHill_TST_FIL_040_1`, `AltitudeAndSlopeDevice_ATTFusedTest#testCalmHandlebarClimbAndFastAlpineDescent_zeroDispersionAndPromptTracking_TST_FIL_040_1`, `AltitudeAndSlopeDevice_ATTFusedTest#testStartupReferenceGrace_eliminatesCliffAndPreservesOfflineFallback_TST_FIL_040_3`, `ExtendedRtsAltitudeSlopeSmootherTest#testLowSpeedBrakingProtection_eliminatesFlatBrakingPlungeAndPreservesDownhill_TST_FIL_040_4`
- **Test Description**:
  1. *Pocket-Compression Wave Anti-Aliasing & Handlebar Responsiveness (`TST-FIL-040.1` — `BarometricBucketDispersionTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Feed synthetic `~13 Hz` asymmetric `0.92 Hz` pocket-compression waves (`70–88 m` peak-to-peak, matching `2026-11-22_081402`) over `40 s` flat `436–438 m` road (`DEM = 436..438 m`, `speed = 5.5 m/s`, `+34 m` QNH offset) and across a full-workout loop with real climbs/descents; assert `dispersionVarM2` inflates immediately, live fused `ALTITUDE` stays within `<= 2.0 m` of true elevation (no `+6.7 m` / `+10%` phantom hill), and full-workout live `ASCENT`/`DESCENT` stay within `<= 20%` of ERTS/true gain.
     • Feed calm/handlebar `8–12%` climb and fast `-12%` alpine descent (`18 m/s`, intra-bucket `span <= 2.2 m`); assert `dispersionVarM2 == 0.0`, `varBaro` stays uninflated (`profile.rBaroM2`), and live altitude & slope % track the true grade promptly with zero added lag.
  2. *Corrupted-Barometer DEM Step-Gate Bypass & Healthy-Baro Preservation (`TST-FIL-040.2` — `KinematicAltitudeSlopeEkfTest.kt`)*:
     • Verify that when `updateBarometer(..., varBaro = 1000.0)` injects a `+8 m` biased barometer sample (`lastBaroVarM2 > 16.0`), subsequent flat `DEM` (`updateReferenceAltitude`) is NOT vetoed by `computeStepGatedRefVariance()`, while healthy `varBaro <= 16.0` preserves all `REQ-FIL-037.2` DEM grid-jump gating and `DEFAULT_VAR_BARO = 0.25` / 2-state `p00` invariants.
  3. *3-State Startup Calibration Grace, Offline Fallback & 2-State Immediate Publish (`TST-FIL-040.3` — `AltitudeAndSlopeDevice_ATTFusedTest.kt`)*:
     • Verify 3-State workout with `+35 m` QNH offset (`470.9 m` baro vs `435.0 m` DEM arriving at `t = 2 s`) sets `ekf.isInitialized == true` at `t = 0 s`, emits NO uncalibrated `470.9 m` cliff on `t = 0..1 s`, emits `435.0 m` cleanly at `t = 2 s` with `0.0 m` phantom ascent/descent; verify offline/no-DEM workout falls back to baro after `>= 5 s` grace expiry (and late DEM lock at `t = 10 s` still adds `0.0 m` bogus descent); verify 2-State mode and `reanchorOnResume()` publish immediately at `t = 0 s`.
  4. *ERTS Low-Speed Braking Slope Stability & Contract Checks (`TST-FIL-040.4` — `ExtendedRtsAltitudeSlopeSmootherTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Replay `15 s` flat-road braking segment (`5.5 -> 0.6 m/s`, `DEM = 438 m`, `BARO` dropping `-2.0 m` on pocket decompression); assert ERTS smoothed `SLOPE` stays within `±4%` (no `-11%` plunge) and smoothed altitude stays anchored near `438 m`; verify real `-8%` downhill braking preserves negative grade smoothly without overshoot; verify `lowSpeedSlopeGainFactor()` (`TST-FIL-033.1`) and `ArchitectureConformanceContractTest` (`<= 1,500` LoC) pass.

### TST-FIL-041: **Immediate Stationary Rejected-GNSS Zero-Speed Publication & Post-Pause Velocity Spike Bounding Verification (`ATT-9601`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-041` (`REQ-FIL-041.1`–`REQ-FIL-041.3`)
- **Component**: `SpeedAndLocationDevice.java`, `UnicycleOdometryEkf.kt`, `SpeedAndLocationDevice_ATTFused.kt`
- **Jira**: `ATT-9601` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Verified Test Methods**: `SpeedAndLocationDevice_ATTFusedTest#testStationaryRejectedIndoorGnss_publishesZeroSpeedCancelsWatchdogAndFreezesDistance_TST_FIL_041_1`, `UnicycleOdometryEkfTest#testPostPauseAndPostStandstillDecoupling_blocksSnapSpikeAndRecouplesOnFix4_TST_FIL_041_2`, `SpeedAndLocationDevice_ATTFusedTest#testPostPause159sStopAnd19mOutdoorSnap_boundsPublishedSpeedNearDoppler_TST_FIL_041_2`, `SpeedAndLocationDevice_ATTFusedTest#testGenuineFastStartFromStandstill_tracks9MpsDopplerAndWheelWithoutClamping_TST_FIL_041_3`
- **Test Description**:
  1. *Immediate Indoor Zero-Speed Publication (`TST-FIL-041.1` — `SpeedAndLocationDevice_ATTFusedTest.kt`)*:
     • Move at `1.54 m/s`, then transition to an indoor stop (`acc = 45.9 m > MAX_GNSS_ACCURACY_METERS`, `PhoneCarriageMode.STATIONARY`); assert `mSpeedSensor` immediately drops to `0.0 m/s` and `mPaceSensor` to `null` on the exact rejected-fix tick (zero `10–14 s` watchdog lag) while pinned coordinates and cumulative distance remain unchanged (`+0.0 m`). Verify pre-anchor coarse stationary fixes also publish `0.0 m/s` cleanly.
  2. *Post-Pause `159 s` Stop & `+19.1 m` Outdoor Snap Velocity Bound (`TST-FIL-041.2` — `UnicycleOdometryEkfTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`)*:
     • Simulate a `159 s` indoor stop followed by outdoor re-acquisition (`row 228` `acc = 12.75 m`, `row 229` `+19.1 m` coordinate snap in `1 s`, `acc = 11.73 m`, Doppler `3.51 m/s`); assert `ekf.speedMps` and published `mSpeedSensor` remain within `<= 1.5 m/s` of `3.51 m/s` (never spiking to `10.85 m/s`), and verify the 3-fix decoupling window clears back to coupled EKF updates cleanly on fix 4.
  3. *Genuine Fast Start Preservation, `REQ-TRK-056.2` Regression & LoC Contract (`TST-FIL-041.3` — `SpeedAndLocationDevice_ATTFusedTest.kt`, `SpeedAndLocationDeviceStationaryJitterTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Verify legitimate acceleration from standstill to `9.0 m/s` (outdoor GNSS `acc <= 10 m` Doppler `9.0 m/s` and active wheel sensor) tracks `9.0 m/s` promptly without clamping or lag; verify `SpeedAndLocationDevice_GoogleFused` preserves `REQ-TRK-056.2` damped-Doppler fallback; verify `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC.

### TST-FIL-042: **Unified Monotonic `elapsedRealtime` Clock Domain, OEM HAL Normalization & Out-of-Order Hysteresis Guard Verification (`ATT-9600`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-042` (`REQ-FIL-042.1`–`REQ-FIL-042.3`)
- **Component**: `AttFusedTimestampNormalizer.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `PhoneCarriageClassifier.kt`, `UnicycleOdometryEkf.kt`
- **Jira**: `ATT-9600` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Verified Test Methods**: `AttFusedTimestampNormalizerTest#testStandardClockBoottime_unifiesGnssAndImuIgnoringLaggingLocationTime_TST_FIL_042_1`, `AttFusedTimestampNormalizerTest#testDivergentOemHals_uptimeMillisDeepSleepAndEpochNsNtpJumpReanchorCleanly_TST_FIL_042_2`, `AttFusedTimestampNormalizerTest#testPlainJvmUnmockedAndRobolectricFrozenClockCoherenceAndReset_TST_FIL_042_2`, `SpeedAndLocationDevice_ATTFusedTest#testPixel10Minus218sGnssOffsetReplay_enforcesFullDwellZeroModeChatterAndPreservesUtcSidecar_TST_FIL_042_1`, `SpeedAndLocationDevice_ATTFusedTest#testDivergentOemSensorTimestamps_uptimeAndEpochNsAlignWithGnssWithoutBreakingDt_TST_FIL_042_2`, `PhoneCarriageClassifierTest#testOutOfOrderLaggedClassifyAndGnss_preservesFullDismountDwellWithoutBypass_TST_FIL_042_3`, `UnicycleOdometryEkfTest#testInterleavedLaggedGnssTimestamp_keepsCurrentTimestampMonotonicAndResetClearsIt_TST_FIL_042_3`
- **Test Description**:
  1. *Pixel 10 `-2.18 s` GNSS Offset Replay & External UTC Preservation (`TST-FIL-042.1` — `SpeedAndLocationDevice_ATTFusedTest.kt`, `AttFusedTimestampNormalizerTest.kt`)*:
     • Reproduce the `t = 4..20 s` window from `2026-11-22_081402.phone_raw.csv.gz` where GPS `Location.getTime()` lags `System.currentTimeMillis()` by `-2.18 s` while `Location.getElapsedRealtimeNanos()` matches `SystemClock.elapsedRealtimeNanos()` within `< 200 ms`; assert `PhoneCarriageClassifier` enforces full `dismountHysteresisMs` (`2.0 s`) / `mountHysteresisMs` (`4.0 s`) dwell with zero sub-`2 s` `4 <-> 1` mode-chatter flips and zero `-2.18 s` EKF/classifier sawtooth; assert `toWallClockEpochMs()` emits monotonic UTC epoch ms immune to NTP jumps and sidecar `epochMs` remains UTC wall-clock.
  2. *Cross-Device OEM `uptimeMillis`/Epoch-ns Sensor Normalization, Wheel Alignment & JVM/Robolectric Compatibility (`TST-FIL-042.2` — `AttFusedTimestampNormalizerTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`)*:
     • Verify `SensorEvent.timestamp` on `uptimeMillis` (`-120 s` offset + `+30 s` deep-sleep jump) and Unix epoch ns (`~1.79e18` + NTP jump) normalize cleanly onto `elapsedRealtime` preserving `20 ms` `dt`; verify wall-clock `MySensorManager` wheel callbacks align with GNSS/IMU; verify plain-JVM unmocked `SystemClock` and Robolectric frozen `SystemClock` suites pass cleanly.
  3. *Out-of-Order Classifier/EKF Monotonic Guards & LoC Contract (`TST-FIL-042.3` — `PhoneCarriageClassifierTest.kt`, `UnicycleOdometryEkfTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Verify out-of-order `PhoneCarriageClassifier.classify(nowMs < lastEventTimestampMs)` clamps `effectiveNowMs` to `lastEventTimestampMs` so `candidateSinceMs` never seeds in the past and full `2.0 s` dwell holds; verify `UnicycleOdometryEkf` keeps `currentTimestampMs` monotonically non-decreasing; verify `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC.

---

### TST-CON-056: **Radar Subsystem & State-Cache Consolidation, 3-StateFlow Split & AB-BA Deadlock-Free Concurrency Verification (`ATT-8132`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-056` (`REQ-CON-056.1`–`REQ-CON-056.4`)
- **Component**: `TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`, `TrainingApplication.java`, `TrackerService.java`, `RadarState.kt`, `RadarAudioSnoozeState.kt`, `RadarStateRepository.kt`, `RadarSnoozeController.kt`, `RadarLightController.kt`, `SmartBrakeLightDetector.kt`, `SmartRadarLightEngine.kt`, `RadarAlertManager.kt`, `RadarEdgeOverlay.kt`
- **Jira**: `ATT-8132` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Verified Test Methods**: `TuningPreferencesDataStoreRadarLightControlModeTest#createPreferencesMigration_migratesLegacyRadarLightKeysAndCleansClusterPrefs_tstCon056_1`, `RadarBrakeLightConcurrencyStressTest#deadCachesAndInvalidationHooks_areRemovedFromRadarLightControllerAndTrackerService_tstCon056_2`, `RadarBrakeLightConcurrencyStressTest#preferenceChanges_propagateImmediatelyWithoutStaleCache_tstCon056_2`, `RadarSnoozeControllerTest#oneShotExpiryTimer_hasZeroIntermediateEmissions_reschedulesOn15mTo30m_andExpiresCleanly_tstCon056_3`, `RadarBrakeLightConcurrencyStressTest#timedSnooze_producesZeroWakeupsInSmartRadarLightEngineAndRadarAlertManager_tstCon056_3`, `RadarBrakeLightConcurrencyStressTest#concurrentThreatAndSnoozeMutations_neverOverwriteTargetsOrFailoverControlState_tstCon056_3`, `RadarBrakeLightConcurrencyStressTest#parallelRadarLightControllerAndSmartBrakeLightDetector_haveZeroAbBaDeadlocks_tstCon056_4`
- **Test Description**:
  1. *Canonical 2-Key Radar-Light Migration & Single Source of Truth (`TST-CON-056.1` — `TuningPreferencesDataStoreRadarLightControlModeTest.kt`, `MapCameraTuningPreferencesTest.kt`, `TuningPreferencesDataStoreRoundTripTest.kt`, `TuningConfigTest.kt`, `AltitudeAwareClusterMatchingTest.kt`, `WorkoutClusterEngineScoreThresholdTest.kt`)*:
     • Run one-time `DataMigration<Preferences>` across all legacy radar-light key permutations, `LEGACY_MAP_CAMERA_KEYS`, and legacy SharedPreferences cluster tolerances; assert exact preservation of prior effective modes, removal of obsolete keys, idempotent `shouldMigrate == false` on rerun, unified fresh-install `SMART_AUTOMATION` consistency, and full `TuningConfig` derived-property agreement after `updateRadarAutoLightControl`/`updateRadarLightControlMode`.
  2. *Zero Stale Caches & Immediate Settings Propagation (`TST-CON-056.2` — `RadarLightControllerTest.kt`, `TrackerServiceStateMachineCharacterisationTest.kt`, `TrackerServiceResilienceTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Verify `RadarLightController` has zero `cached*` fields / `updateCachedPreferences` overloads, reflects DataStore changes immediately, and persists on one long-lived scope; verify `TrackerService.getAutoPauseConfig()` immediately reflects `updateAutoPause{Bike,Run,Other}Enabled` and `resetToDefaults` without `invalidateAutoPauseCache()`; assert `TrackerService.java <= 3,220` LoC and `TrainingApplication.java <= 2,050` LoC.
  3. *3-StateFlow Split, Zero 1-Hz Snooze Wakeups & Atomic Concurrency (`TST-CON-056.3` — `RadarStateRepositoryTest.kt`, `RadarSnoozeControllerTest.kt`, `SmartRadarLightEngineTest.kt`, `RadarAlertManagerTest.kt`, `RadarEdgeOverlayTest.kt`, `BTLERadarDeviceTest.kt`, `ANTBikeRadarDeviceTest.kt`, `RadarTrafficSimulatorTest.kt`, `RadarSettingsSectionTest.kt`)*:
     • Activate timed 15m/30m snooze with `TestScope` and advance intermediate seconds -> assert `0` intermediate `snoozeState` emissions, `0` wakeups or cooldown extensions in `SmartRadarLightEngine` and `RadarAlertManager`, and exact 1-shot expiry at `snoozeUntilElapsedRealtimeMs`; verify concurrent 8-Hz threat + snooze/light updates never overwrite targets and preserve failover (`BLE -> ANT_PLUS -> SIMULATOR`); verify `RadarEdgeOverlay` derives badge minutes from `snoozeUntilElapsedRealtimeMs`.
  4. *AB-BA Deadlock-Free Multi-Thread Stress Test (`TST-CON-056.4` — `RadarBrakeLightConcurrencyStressTest.kt`, `SmartBrakeLightDetectorTest.kt`, `RadarLightControllerTest.kt`)*:
     • Run parallel `ExecutorService` threads (`>= 4` threads, `>= 5,000` iterations) concurrently exercising `RadarLightController` (`registerRadarDevice`, `unregisterRadarDevice`, `setManualLightMode`, `setAutoMode`, `checkAndRestorePreBrakeMode`) and `SmartBrakeLightDetector` (`onLinearAccelerationChanged`, `stopListening`, `isAutoBypassed`, `isEnabled`) while checking `ThreadMXBean.findDeadlockedThreads()`; assert bounded completion with `0` deadlocks and `0` uncaught exceptions.

---

### TST-CON-057: **Capability-Gated Polar Live-IMU HRM Source Selection & `BIKE_POWER` Slope/VAM Removal Verification (`ATT-8991`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-057` (`REQ-CON-057.1`–`REQ-CON-057.4`)
- **Component**: `HrmSensorCapabilityHelper.kt`, `DeviceType.java`, `DevicesDatabaseManager.java`, `SensorSourceDialog.kt`
- **Jira**: `ATT-8991` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Verified Test Methods**: `DeviceTypeSensorMappingTest#testPolarHrmCadenceStridesAndBikePowerRemovedFromSlopeVamAscentDescent_TST_CON_057_1`, `HrmSensorCapabilityHelperTest#testBlePolarH10_supportsCadenceStridesVoGctAndHrHrv_TST_CON_057_2`, `HrmSensorCapabilityHelperTest#testBlePolarVeritySense_supportsCadenceAndStridesOnlyNotVoOrGct_TST_CON_057_2`, `HrmSensorCapabilityHelperTest#testAntPlusAndNonPolarOrSpoofedHrms_excludedFromLiveImuButKeptForHrHrv_TST_CON_057_2`, `HrmSensorCapabilityHelperTest#testNullSensorTypeAndThrowingContext_resilientFallback_TST_CON_057_2`, `DevicesDatabaseManagerTest#testGetDeviceIdAndNameLists_capabilityGatesPolarHrmAndExcludesBikePowerFromSlope_TST_CON_057_3`, `SensorSourceDialogPriorityTest#testNotConnectedDevices_capabilityGatesPolarLiveImuAndNormalizesUnknownManufacturer_TST_CON_057_3`, `SensorSourceDialogContractTest#testComposeRender_cadenceAndVerticalOscillationCapabilityFilterHrms_TST_CON_057_3`
- **Test Description**:
  1. *`DeviceType.getDeviceTypeList()` Mapping Verification (`TST-CON-057.1` — `DeviceTypeSensorMappingTest.kt`)*:
     • Assert `DeviceType.getDeviceTypeList(CADENCE)` and `DeviceType.getDeviceTypeList(STRIDES)` contain `DeviceType.HRM` alongside existing cadence/stride sources while `CALORIES` stays isolated.
     • Assert `DeviceType.getDeviceTypeList(SLOPE/VERTICAL_SPEED/ASCENT/DESCENT)` exclude `DeviceType.BIKE_POWER` while preserving `DeviceType.VERTICAL_SPEED_AND_SLOPE` and `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED`.
  2. *`HrmSensorCapabilityHelper` Capability Matrix (`TST-CON-057.2` — `HrmSensorCapabilityHelperTest.kt`)*:
     • Verify BLE Polar H10 (renamed hardware DIS/SDK `H10` & legacy name) returns `true` for `CADENCE`, `STRIDES`, `VERTICAL_OSCILLATION`, `GROUND_CONTACT_TIME`, `HR`, `RR_INTERVAL`, and `HRV_*`.
     • Verify BLE Polar Verity Sense/OH1 (renamed hardware `VERITY_SENSE` / `offline_rec_` & legacy name) returns `true` for `CADENCE`/`STRIDES`/`HR`/`HRV_*` and `false` for `VERTICAL_OSCILLATION`/`GROUND_CONTACT_TIME`.
     • Verify ANT+ Polar H10, ordinary BLE/ANT+ HRMs, Polar H9, confirmed non-Polar GATT spoofs, and rename-suppressed HRMs return `false` for all 4 Live-IMU metrics while returning `true` for `HR`, `RR_INTERVAL`, and `HRV_*`.
  3. *`DevicesDatabaseManager` & `SensorSourceDialog` Integration & Behavioral Compose Verification (`TST-CON-057.3` — `DevicesDatabaseManagerTest.kt`, `SensorSourceDialogPriorityTest.kt`, `SensorSourceDialogContractTest.kt`)*:
     • Exercise `DevicesDatabaseManager.getDeviceIdAndNameLists()` across `CADENCE`, `STRIDES`, `VERTICAL_OSCILLATION`, `GROUND_CONTACT_TIME`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`, `HR`, and `HRV_RMSSD` with mixed DB rows (incl. partial-column cursor safety & `BIKE_POWER` flag filtering preservation).
     • Verify `SensorSourceDialog.sortNotConnectedDevices()` and behavioral Robolectric `createComposeRule()` UI rendering filter non-IMU HRMs out of `CADENCE`/`STRIDES` and filter Verity Sense/ordinary HRMs out of `VERTICAL_OSCILLATION`/`GROUND_CONTACT_TIME` while preserving `REQ-UI-2804.3` HR modality sorting.
  4. *Monolith, Priority & Governance Contract Verification (`TST-CON-057.4` — `MySensorManagerPriorityTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`)*:
     • Assert `DevicesDatabaseManager.java <= 1,671` LoC (`0` upward `.ui.*` imports), `SensorSourceDialog.kt <= 1,500` LoC, `MySensorManager` priority ranks unchanged, zero `src/main` reads, and `Localization Parity: N/A - 0 string changes`.

---

### TST-CON-058: **`ANTBikeSpeedDevice` & `ANTBikeSpeedAndCadenceDevice` Null Speed Guard & Standstill Pace Verification (`ATT-9605`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-058` (`REQ-CON-058.1`, `REQ-CON-058.2`)
- **Component**: `ANTBikeSpeedDevice.java`, `ANTBikeSpeedAndCadenceDevice.java`
- **Jira**: `ATT-9605` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Verified Test Methods**: `ANTBikeSpeedDeviceTest#testANTBikeSpeed_nullCalculatedSpeed_initialRemainsNullWithoutNpe_tstCon058_1`, `ANTBikeSpeedDeviceTest#testANTBikeSpeed_nullCalculatedSpeed_afterMotionPreservesValuesWithoutNpe_tstCon058_1`, `ANTBikeSpeedDeviceTest#testANTBikeSpeed_initialZeroSpeed_setsZeroSpeedAndNullPace_tstCon058_1`, `ANTBikeSpeedDeviceTest#testANTBikeSpeed_positiveSpeed_publishesMetersPerSecondAndFinitePace_tstCon058_1`, `ANTBikeSpeedDeviceTest#testANTBikeSpeed_zeroSpeedAfterMotion_decaysPaceToNullAtThreshold_tstCon058_1`, `ANTBikeSpeedDeviceTest#testANTBikeSpeed_negativeSpeed_emitsNullPace_tstCon058_1`, `ANTBikeSpeedAndCadenceDeviceTest#testANTBikeSpeedAndCadence_nullCalculatedSpeed_initialRemainsNullWithoutNpe_tstCon058_2`, `ANTBikeSpeedAndCadenceDeviceTest#testANTBikeSpeedAndCadence_nullCalculatedSpeed_afterMotionPreservesValuesWithoutNpe_tstCon058_2`, `ANTBikeSpeedAndCadenceDeviceTest#testANTBikeSpeedAndCadence_initialZeroSpeed_setsZeroSpeedAndNullPace_tstCon058_2`, `ANTBikeSpeedAndCadenceDeviceTest#testANTBikeSpeedAndCadence_positiveSpeed_publishesMetersPerSecondAndFinitePace_tstCon058_2`, `ANTBikeSpeedAndCadenceDeviceTest#testANTBikeSpeedAndCadence_zeroSpeedAfterMotion_decaysPaceToNullAtThreshold_tstCon058_2`, `ANTBikeSpeedAndCadenceDeviceTest#testANTBikeSpeedAndCadence_negativeSpeed_emitsNullPace_tstCon058_2`
- **Test Description**:
  1. *`ANTBikeSpeedDevice.onNewCalculatedSpeed` Null Guard, Units & Standstill Decay (`TST-CON-058.1` — `ANTBikeSpeedDeviceTest.kt`)*:
     • Capture `CalculatedSpeedReceiver` via `mockPcc.subscribeCalculatedSpeedEvent(...)`; invoke `onNewCalculatedSpeed(..., null)` both initially and after a valid reading; verify zero NPE (including `myLog` path) and undisturbed `mSpeedSensor` / `mPaceSensor` values.
     • Invoke `onNewCalculatedSpeed(..., BigDecimal.ZERO)` initially; verify `mSpeedSensor.value == 0.0` and `mPaceSensor.value == null` (never `Double.POSITIVE_INFINITY`).
     • Invoke `onNewCalculatedSpeed(..., BigDecimal("10.0"))` (`m/s`); verify `mSpeedSensor.value == 10.0` and `mPaceSensor.value == 0.1` (`1.0 / 10.0` finite `s/m`, no `/ 3.6` scaling).
     • Deliver `8` (`SPEED_THRESHOLD`) consecutive `BigDecimal.ZERO` standstill ticks after motion; verify ticks `1..7` hold `0.1`, tick `8` decays `mPaceSensor.value` cleanly to `null`, and negative speed feeds `null` pace.
  2. *`ANTBikeSpeedAndCadenceDevice.onNewCalculatedSpeed` Null Guard, Units & Standstill Decay (`TST-CON-058.2` — `ANTBikeSpeedAndCadenceDeviceTest.kt`)*:
     • Capture `CalculatedSpeedReceiver` via `mockSpeedPcc.subscribeCalculatedSpeedEvent(...)` alongside existing `TST-CON-025.1`/`.2` release-handle tests; verify identical `null` (initial & post-motion), initial `0.0` (`pace == null`), positive `10.0 m/s -> 0.1 s/m`, `8`-tick `SPEED_THRESHOLD` standstill decay to `null`, and negative speed (`pace == null`) assertions.
  3. *Architecture Contract & Regression Parity (`TST-CON-058.3` — `ArchitectureConformanceContractTest.kt`)*:
     • Run incremental `--rerun` targeted unit & contract tests (`ANTBikeSpeedDeviceTest`, `ANTBikeSpeedAndCadenceDeviceTest`, `ArchitectureConformanceContractTest`); confirm `ANTBikeSpeedDevice.java <= 1,500` LoC, `ANTBikeSpeedAndCadenceDevice.java <= 1,500` LoC, and `0` string edits (`Localization Parity: N/A - 0 string changes`).

---

### TST-FIL-043: **`PhoneCarriageClassifier` Fast `STATIONARY` Exit, Continuous Non-Stationary Dwell & Indoor Multipath Immunity Verification (`ATT-9469`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-043` (`REQ-FIL-043.1`, `REQ-FIL-043.2`, `REQ-FIL-043.3`)
- **Component**: `PhoneCarriageClassifier.kt`
- **Jira**: `ATT-9469` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Test Description**:
  1. *Bank-Stop Departure Continuous Dwell & `<= 1 s` Fast Exit (`TST-FIL-043.1` — `PhoneCarriageClassifierTest#testBankStopDeparture_continuousNonStationaryDwellAndFastExitWithinOneSecond_TST_FIL_043_1`)*:
     • Reproduce `2026-11-21_081228` Bank-stop departure (`rows 401..409`: `STATIONARY` -> accelerating `2.42 -> 3.88 -> 4.60 m/s` and `3.5 -> 5.5 -> 6.1 m/s` @ `acc = 15.8 -> 13.7 -> 12.2 m`, `> 10 m` displacement, `rawCandidate` shifting `WALKING_TRANSITION -> BIKE_JERSEY_BACK -> BIKE_FRONT_POCKET`); assert `committedMode` exits `STATIONARY` within `<= 1 s` of crossing `3.2 m/s` (`0` multi-second `STATIONARY` tail at `> 5 m/s`), commits active `BIKE_*`, and keeps post-exit dwell state synchronized (`candidateMode`/`candidateSinceMs` cleared); assert a brief `1 s` standstill fidget returning to `STATIONARY` clears `nonStationarySinceMs` without false exit.
  2. *Cross-Device Portability: Barometer-Free / Wheel-Free, Wheel-Active, Rigid Handlebar & Slow Walk (`TST-FIL-043.2` — `PhoneCarriageClassifierTest#testCrossDeviceStationaryFastExit_baroFreeWheelFreeWheelActiveAndRigidHandlebar_TST_FIL_043_2`)*:
     • Verify `<= 1 s` exit on barometer-free, wheel-free, `stepDetectorRegistered=false` cycling and running starts (`>= 3.2 m/s`, including omitted `accuracyMeters = null` and empty `positionSamples` with `!quietImu`), on `wheelActiveMoving` cycling launch, and on rigid handlebar mount (`quietImu == true`, no wheel/baro) blocked when `positionSamples` is empty/stale (`< 2` samples or `> STALENESS_MS`) and fast-exiting once `>= 10 m` fresh trusted displacement + `>= 3.2 m/s` holds; verify slow non-stationary walk (`1.2 m/s`) with shifting candidates exits cleanly at `2.0 s` (`dismountHysteresisMs`) without subclass-flip timer resets.
  3. *Indoor Multipath GNSS-Spike Immunity, Invalid Accuracy, `UNKNOWN`-Gap Reset & Monotonic Guard (`TST-FIL-043.3` — `PhoneCarriageClassifierTest#testStationaryIndoorMultipathSpikeAndLaggedTimestamp_preservesStationaryImmunity_TST_FIL_043_3`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`)*:
     • Verify `1–2 s` coarse (`acc = 25..45 m > 20 m`) and invalid-accuracy (`Double.NaN`, `Double.POSITIVE_INFINITY`, `0.0`, `-1.0`) GNSS spikes (`v = 5–7 m/s`, with or without coordinate jumps) do NOT enter `positionSamples` or poison `isPositionStagnant(nowMs)` and produce `0` exits from `STATIONARY` (contrasted with omitted `null` compatibility); verify stagnant-position optimistic-accuracy spikes (`acc = 5 m`, `v = 6 m/s`) under `quietImu == true` stay `STATIONARY`; verify an `UNKNOWN` dropout gap resets `nonStationarySinceMs` so motion resuming afterward starts a fresh dwell; verify lagged timestamps preserve monotonic dwell, `reset()` clears `nonStationarySinceMs`/`lastGnssAccuracyMeters`, `PhoneCarriageClassifier.kt <= 1,500` LoC, and `100%` pass across adjacent `ATTFused`/multisport/replay/contract suites (`Localization Parity: N/A - 0 string changes`).

---

### TST-FIL-044: **False `RUN_HANDHELD` Suppression & Multisport `RUN` Kinematic/Footstrike Guardrails Verification (`ATT-9467`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-044` (`REQ-FIL-044.1`, `REQ-FIL-044.2`, `REQ-FIL-044.3`)
- **Component**: `PhoneCarriageClassifier.kt`, `MultisportTransitionDetector.kt`, `MultisportTransitionBridge.kt`, `SpeedAndLocationDevice_ATTFused.kt`
- **Jira**: `ATT-9467` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Verified Test Methods**: `PhoneCarriageClassifierTest#testCyclingStartPhoneHandlingAndZeroStrideErrandRoll_neverEmitRunHandheld_TST_FIL_044_1`, `PhoneCarriageClassifierTest#testTriathlonT2SlowJoggerWithHandheldGlance_crossDeviceHardwareAndAccelFootstrikes_TST_FIL_044_2`, `MultisportTransitionDetectorTest#testRunEstablishmentAndBikeToRunGuardrails_blockLowSpeedAndZeroStrideRollsWhilePreservingSlowT2_TST_FIL_044_3`
- **Test Description**:
  1. *Cycling Start Phone Handling & Zero-Stride Errand Roll Replay (`TST-FIL-044.1` — `PhoneCarriageClassifierTest#testCyclingStartPhoneHandlingAndZeroStrideErrandRoll_neverEmitRunHandheld_TST_FIL_044_1`)*:
     • Reproduce `2026-11-21_081228` `rows 1..25` (phone-handling + `glanceCount >= 1` + brief `acc > 15 m` GNSS spike `5.71..7.46..5.89 m/s` + `0.32..0.75 m/s` bike-pushing with `0` steps & non-pendulum handling) and `rows 310..340` (`Metzger -> Bank` `47.8 m` roll at `2.0..4.0 m/s` for `19 s` with phone glance & `0–1` steps, across both `stepDetectorRegistered = true` and `false` + 50-Hz non-footstrike accel, with and without barometer); assert `0` ticks emit `RUN_HANDHELD` (or any `RUN_*`), low-speed pushing resolves to `WALKING_TRANSITION`, rolling resolves to `BIKE_*`, `< 2 s` cold-start glance without accel warmup never overrides `BIKE_FRONT_POCKET`, and `hasActiveRunFootstrike()` returns `false` on fresh classifier, post-`reset()`, and explicit `Long.MIN_VALUE` without signed underflow.
  2. *Cross-Device Genuine Triathlon `T2` Slow Jogger & Handheld Glance Preservation (`TST-FIL-044.2` — `PhoneCarriageClassifierTest#testTriathlonT2SlowJoggerWithHandheldGlance_crossDeviceHardwareAndAccelFootstrikes_TST_FIL_044_2`)*:
     • Verify genuine `T2` dismount (`BIKE_FRONT_POCKET -> WALKING_TRANSITION -> RUN`) for a slow jogger (`2.0 m/s`, `2.55 Hz` / `153 spm`, incl. wrist glance to `RUN_HANDHELD`) across 4 device profiles (hardware `TYPE_STEP_DETECTOR` vs permission-free 50-Hz accel footstrikes, with and without barometer, under `UNKNOWN` and `CYCLING` hints); assert `hasActiveRunFootstrike() == true`, `RUN_HANDHELD` / `RUN_*` commits cleanly, and existing `Sport = RUN` & trail-batching tests remain green.
  3. *`MultisportTransitionDetector` Bounded Trailing Sliding-Window `RUN` Guardrails (`TST-FIL-044.3` — `MultisportTransitionDetectorTest#testRunEstablishmentAndBikeToRunGuardrails_blockLowSpeedAndZeroStrideRollsWhilePreservingSlowT2_TST_FIL_044_3`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`)*:
     • Verify directly and coupled with `PhoneCarriageClassifier` that: (a) `rows 1..25` (`0.46 m/s`, `10.9 m`, `0` footstrikes) & direct low-speed `RUN_HANDHELD` injection never lock `establishedSport = RUN` and emit `0` `Laufen -> Rad` splits; (b) `rows 310..340` (`47.8 m` roll at `2.0..4.0 m/s`, `19 s`, `0` footstrikes, `kinematicSport = BIKE`, `hasActiveRunFootstrike = false` or `null`) emits `0` `BIKE -> RUN` transitions; (c) Phase 1 & Phase 2 corroboration-dilution immunity (`60 s` uncorroborated zero-stride roll followed by brief `5 s` noise burst) emits `0` `RUN` establishment or `BIKE -> RUN` transitions; (d) Phase 2 post-low-speed recovery (`15 s` slow `0.5 m/s` ticks followed by `15 s` real `2.0 m/s` `T2` jog) confirms cleanly at `+15 s` of real jogging with `enterAnchor` anchored to the qualifying jog start and `exitAnchor` preserved intact (plus Phase 1 slow-start recovery & mid-confirm pause); (e) `NaN`/negative distance deltas and zero-duration (`windowActiveSec == 0.0`) ticks are guarded safely, `reset()` clears sliding windows, `@JvmStatic @JvmOverloads` 5/6-arg bridge compatibility holds, all touched files stay `<= 1,500` LoC, and `100%` of adjacent `ATTFused`/multisport/replay/contract suites pass (`Localization Parity: N/A - 0 string changes`).

---

### TST-FIL-045: Post-Workout ERTS Pause-Gap Zero-Distance & Segment Decoupling Suite (`ATT-9242`) [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-FIL-045` (`REQ-FIL-045.1`, `REQ-FIL-045.2`, `REQ-FIL-045.3`)
- **Component**: `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`
- **Jira**: `ATT-9242` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Verified Test Methods**: `ErtsPostWorkoutRecomputerTest#testZumBaecker59AndMultiPauseRecomputation_zeroPauseDistanceAndLapsAndTimingParity_TST_FIL_045_1`, `ErtsPostWorkoutRecomputerTest#testLeadingNullTimeTotal_batchFlushStall6s_andDoubleWallClockPause_tstFil027_1`, `ExtendedRtsOdometrySmootherTest#testPauseGapDecouplingAndOutAndBackHeading_zeroPauseDistanceAndNoCrossPauseBleed_TST_FIL_045_2`, `ExtendedRtsAltitudeSlopeSmootherTest#testPauseGapSegmentation_zeroPhantomJumpAndTrailingSingleSampleAndBiasCarryover_TST_FIL_045_3`
- **Test Description**:
  1. *`Zum Bäcker #59` & Multi-Pause End-to-End SQLite / `Laps.db` / Timing Parity (`TST-FIL-045.1` — `ErtsPostWorkoutRecomputerTest.kt`)*:
     • Replay a `Zum Bäcker #59` profile (`TIME_ACTIVE_s = 391`, `TIME_TOTAL_s = 570`) with `4 s`, `17 s` (`0.88 m/s` resume), `134 s` (`TIME_TOTAL: 241 -> 375`, `TIME_ACTIVE: 218 -> 219`, `3.84 m/s` resume), and `> 10 min` (`610 s`) pauses across 2 laps (`LAP_NR = 1, 2`) plus untouched device-attributed columns; assert `0.0 m` `DISTANCE_m` delta and `0 m/h` `VERTICAL_SPEED` on every pause-resume row, total `DISTANCE_TOTAL_m` within `±3%` of live active distance (`~1,948 m`), `SPEED_AVERAGE_mps` and `Extrema(SPEED_mps, AVG)` reflecting true active average (`~5.0 m/s`, incl. null-`TIME_ACTIVE_s` fallback parity), `Laps.db` / `DISTANCE_m_LAP` exact parity, byte-identical device columns, `reconstructTimingWithPauses()` wall-clock timestamp advancement across repeated `4 s` auto-pauses, `prevPair*` reset across a `TIME_TOTAL` pause landing on a `TIME_ACTIVE == null` row (zero false pause on next row), immunity to `{0, 2}` beats / `6 s` flush stalls / constant `TIME_ACTIVE` with nulls, per-segment `detectAndCompensateBaroResetSteps()` isolation (zero cross-pause shift leakage or null-baro masking), and aligned `TST-FIL-027.1` assertions.
  2. *Out-and-Back Heading, Segment-Independent Odometry ERTS & Direct `smoothSnapshots()` Defense (`TST-FIL-045.2` — `ExtendedRtsOdometrySmootherTest.kt`)*:
     • Verify an out-and-back ride (`950 m` East, `134 s` pause, turnaround heading West with explicit `initialHeadingRad = 0.0`) resolves Segment 2 initial heading West (`~±PI`, not `0 rad` East), plus `4 s` (`isPauseGap`), `17 s`, `134 s`, and `610 s` pauses, 1-sample & no-GNSS post-pause segments, direct `smoothSnapshots()` pause-gap call, and `Long.MIN_VALUE` / `Long.MAX_VALUE` / backwards-timestamp overflow guards; assert `deltaDistanceMeters == 0.0` at every pause boundary, zero cross-pause RTS/EKF bleed (exact parity with independently smoothed segments), `result.size == samples.size`, and all outputs finite.
  3. *Segment-Independent Altitude/Slope ERTS, 1-Sample Tail & Bias Carryover (`TST-FIL-045.3` — `ExtendedRtsAltitudeSlopeSmootherTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`)*:
     • Verify `4 s` (`isPauseGap`), `17 s`, `134 s` (at `3.84 m/s`), and `610 s` manual pause (with `+12 m` baro weather drift during pause), trailing 1-sample post-pause segment (preserving cumulative `ascentM`/`descentM`, baro-only bias subtraction, and all-`NaN` `fallbackInitialAlt` — never resetting to `0.0`), and DEM-only middle segment forwarding `inheritedBaroBias` to a baro-only final segment; assert `verticalSpeedMps == 0.0` on pause-resume rows, zero phantom `g * v * dt_pause` elevation jump (`|alt - trueAlt| <= 2.0 m`), zero phantom pause ascent/descent, monotonic cumulative `ascentM`/`descentM` across active segments, bounded `|slopePercent| <= 35%`, `<= 1,500` LoC caps, and `100%` pass across adjacent ERTS/Laps/contract suites (`Localization Parity: N/A - 0 string changes`).

---

### TST-SEN-035: `EditEquipmentDialog` Wheel Position Prompt, Display & Persistence Suite (`ATT-9320`) [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-SEN-035` (`REQ-SEN-035.1`, `REQ-SEN-035.2`, `REQ-SEN-035.3`)
- **Component**: `EditEquipmentDialog.kt`, `EquipmentTabsScreen.kt`, `EquipmentViewModel.kt`, `EquipmentRepository.kt`, `EquipmentDbHelper.java`
- **Jira**: `ATT-9320` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Verified Test Methods**: `EditEquipmentDialogWheelPositionTest#testDraftWheelPositionHelpers_conflictHintsSwapAndThirdSensorDemotion_TST_SEN_035_1`, `EditEquipmentDialogWheelPositionTest#testAddBike_tappingWheelSpeedSensorPromptsDialogAndPersistsFrontRearOnSave_TST_SEN_035_1`, `EditEquipmentDialogWheelPositionTest#testEditBike_conflictSwapUnlinkAndPreservesUntouchedPositions_TST_SEN_035_1`, `EditEquipmentDialogWheelPositionTest#testNonWheelSensorsAndShoes_toggleDirectlyWithoutWheelPositionDialog_TST_SEN_035_2`, `EquipmentRepositoryWheelPositionTest#testAddAndUpdateEquipmentWithWheelPositions_persistsSwapsAndPreservesUntouched_TST_SEN_035_3`, `EquipmentViewModelMatrixTest#testAddAndUpdateEquipment_dispatchesWheelPositionOverloadsWhenPopulated_TST_SEN_035_3`
- **Test Description**:
  1. *Wheel Position Prompt, Conflict Swap & Unlink (`TST-SEN-035.1` — `EditEquipmentDialogWheelPositionTest.kt`)*:
     • Verify in Compose UI (`createComposeRule()`) and helper unit tests that tapping a `BIKE_SPEED` or `BIKE_SPEED_AND_CADENCE` sensor on a bike (`frameType > 0`) opens `WheelPositionSelectionDialog`, displays conflict swap hints when another selected sensor occupies `FRONT`/`REAR`, swaps positions cleanly on conflict selection (2-sensor swap, free-opposite move, 3rd-sensor fallback to `UNSPECIFIED`), unlinks on `Unlink Sensor`, and preserves untouched positions on Save.
  2. *Visible Position Summary/Badges & Non-Wheel Sensor Passthrough (`TST-SEN-035.2` — `EditEquipmentDialogWheelPositionTest.kt`, `EditEquipmentDialogContractTest.kt`, `WheelPositionSelectionDialogUiTest.kt`)*:
     • Verify collapsed summary field and expanded sensor rows show localized `Front Wheel`/`Rear Wheel` labels and `F`/`R` badges for linked wheel speed sensors; verify non-wheel sensors (HR, Power, Cadence-only, Radar) and shoe items (`frameType == 0`) toggle directly without opening `WheelPositionSelectionDialog`; verify Light & Dark live emulator PNGs (`Rule 8.5`).
  3. *Atomic SQLite & ViewModel Persistence (`TST-SEN-035.3` — `EquipmentRepositoryWheelPositionTest.kt`, `EquipmentViewModelMatrixTest.kt`, `EquipmentViewModelRetiredTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Verify `addEquipment(..., wheelPositions)` and `updateEquipment(..., wheelPositions)` persist `FRONT`/`REAR`/swapped positions atomically in SQLite, preserve existing positions for untouched linked sensors when omitted or using legacy 5-arg overload, and pass all adjacent equipment/contract suites (`Localization Parity: N/A - 0 string changes`).
### TST-TRK-096: **Retroactive Multisport `LAP_NR` Backfill Concurrency, Monotonic Floor & Finalization Reconciliation Test Suite (`ATT-9468`)** [Verified]
- **Requirement(s)**: `REQ-TRK-096` (`REQ-TRK-096.1`–`REQ-TRK-096.3`)
- **Component**: `MultisportLapCoordinator.kt`, `TrackerService.java`, `docs/architecture/detailed_design/tracker_service.md`
- **Jira**: `ATT-9468`
- **Verified Test Methods**: `MultisportLapCoordinatorTest#row425RaceInterleaving_inFlightStaleAndTornLapNrEnforcedMonotonically_tstTrk096_1`, `MultisportLapCoordinatorTest#multiTransitionWithManualLapDirectSwitchAndSessionIsolation_reconcilesMonotonically_tstTrk096_2`, `MultisportLapCoordinatorTest#concurrentSamplingAndMultisportTransitions_zeroNonMonotonicAnomaliesAndZeroDeadlocks_tstTrk096_3`, `TrackerServiceMultisportMonotonicLapTest#sampleAndWriteToDbAndEndWorkout_enforcesMonotonicLapNrAndReconcilesOnFinalization_tstTrk096_3`
- **Test Description**:
  1. *Deterministic `row 425` Interleaving, Torn Intermediate `LAP_NR`, Decoupled Summary Floor & Dual-Column `TIME_TOTAL IS NULL` Fallback (`TST-TRK-096.1` — `MultisportLapCoordinatorTest.kt`)*:
     • Deterministically reproduce Pixel 10 `workoutID = 2423`, `row 425`: insert `rows 1337..1352` (`LAP_NR = 4`), snapshot in-flight `t = 1353` (`LAP_NR = 4`, `summaryValues.LAPS = 4`), torn mid-bump `t = 1353` (`LAP_NR = 5`), delayed `[exitS, enterS)` transition sample (`t = 1320`, `LAP_NR = 4`, `summaryValues.LAPS = 4`), delayed `< exitS` pre-transition sample (`t = 1299`, `LAP_NR = 4`, `summaryValues.LAPS = 4`), and a `TIME_TOTAL IS NULL` row with valid UTC `time` in a table having both `TIME_TOTAL` and `time` columns; run `onMultisportTransitionIntent()` + `backfillWorkoutSamplesLapNumbers()` BEFORE flushing snapshotted rows through `enforceMonotonicLapNr()` + `INSERT`. Assert `row(1299) == 4` with `summaryValues.LAPS == 6`, `row(1320) == 5` with `summaryValues.LAPS == 6`, `rows(1337..1353) == 6` with `summaryValues.LAPS == 6`, dual-column `TIME_TOTAL IS NULL` + UTC `time` row backfilled & enforced to `6`, pre-existing torn `LAP_NR = 5` upgraded by backfill while manual `LAP_NR = 7` is never downgraded, and `row[i].LAP_NR <= row[i+1].LAP_NR` across the entire table.
  2. *Multi-Transition Consistency, Direct `< 10 s` Switch, Mixed-Window Merge & Rapid Stop/Start Isolation (`TST-TRK-096.2` — `MultisportLapCoordinatorTest.kt`)*:
     • Execute consecutive transitions (`Run (1) -> T1 (2) -> Bike (3) -> manual lap (4) -> T2 (5) -> Run (6)` plus a direct `< 10 s` `hasTransition = false` switch) with delayed inserts across every segment; verify exact `[exitS, enterS) -> transitionLapNr`, `>= enterS -> newLegLapNr`, manual lap `4` preservation, `reconcileWorkoutSamplesOnFinalization()` short-circuiting on `null`/blank `baseFileName`, merging `null`-scoped + keyed windows deduplicated in order, repairing the stopped workout's `baseFileName` table idempotently even after `resetForSession()` starts Session 2 (`0` cross-session bleed), and performing `0` DB operations on single-sport workouts.
  3. *Multi-Threaded Concurrency Stress, Robolectric `TrackerService` Wiring & `SWE.3`/LoC Contract Parity (`TST-TRK-096.3` — `MultisportLapCoordinatorTest.kt`, `TrackerServiceMultisportMonotonicLapTest.kt`, `DetailedDesignParityContractTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Execute a multi-threaded sampler vs. Main-thread multisport transition stress test with bounded timeout asserting `0` non-monotonic `LAP_NR` rows and `0` deadlocks; verify via **Robolectric** `TrackerServiceMultisportMonotonicLapTest` (real `ContentValues`) that `TrackerService.sampleAndWriteToDb()` invokes `enforceMonotonicLapNr()` on `mDbExecutor` (off main thread) and `endWorkout()` invokes `reconcileWorkoutSamplesOnFinalization()` prior to post-processing; assert `DetailedDesignParityContractTest`, `ArchitectureConformanceContractTest` (`TrackerService.java <= 3,220`, `MultisportLapCoordinator.kt <= 1,500`, `0` edits to `WorkoutSamplesDatabaseManager.java`), and `InterfaceSignatureDriftContractTest` pass `100%` (`Localization Parity: N/A - 0 string changes`).

### TST-TRK-097: **Tracking Cockpit Default Tab 9-Language Re-Localization & Custom Title Preservation Test Suite (`ATT-9511`)** [Verified]
- **Requirement(s)**: `REQ-TRK-097` (`REQ-TRK-097.1`–`REQ-TRK-097.3`)
- **Component**: `TrackingTabTitleResolver.kt`, `TrackingViewsRepository.kt`, `TrackingTabsScreen.kt`, `TrackingTabPreviewHeader.kt`, `TrackingTabConfigHeader.kt`
- **Jira**: `ATT-9511`
- **Verified Test Methods**: `TrackingTabTitleResolverTest#all9LocaleDefaultTabStrings_mapDeterministicallyToExpectedKey_tstTrk097_1`, `TrackingTabTitleResolverTest#crossLocaleMatrix_all9SourceLocalesRelocalizeToAll9TargetLocales_tstTrk097_1`, `TrackingTabTitleResolverTest#userRenamedAndNumericTitles_neverMatchDefaultKeysAndStayPreserved_tstTrk097_1`, `TrackingTabLocaleSwitchRobolectricTest#repository_reLocalizesFreshlyCreatedAndLegacyDefaultTabsWhilePreservingCustomNames_tstTrk097_2`, `TrackingTabLocaleSwitchRobolectricTest#composeUi_relocalizesRetainedGermanTabViewInfoAcrossLocaleSwitchesAndPreservesCustom_tstTrk097_3`
- **Test Description**:
  1. *9-Language XML Resource Parity & Cross-Locale Resolution Matrix (`TST-TRK-097.1`, `TrackingTabTitleResolverTest.kt`)*:
     • Verify via `LocalizationTestCache` that 100% of translations for `text_default`, `tab_power`, `tab_map`, and `tab_laps` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) resolve deterministically to their exact `DefaultTrackingTabKey` without ambiguity or collisions.
     • Verify cross-locale re-localization from every source locale A (`de`, `en`, `ja`, etc.) to every target locale B (`es`, `ja`, `pt`, `fr`, `it`, `nl`, `pl`, `en`, `de`).
  2. *Legacy & Newly Created SQLite Row Resolution & Custom Title Preservation (`TST-TRK-097.2`, `TrackingTabTitleResolverTest.kt`, `TrackingViewsRepositoryTest.kt`)*:
     • Seed real SQLite `TrackingViewsDatabaseManager` rows under locale A (both freshly initialized `onCreate()` tabs and legacy persisted German/French/Japanese rows) plus user-renamed custom rows (`"Intervalle"`, `"Race Cockpit"`, `"4"`), switch active Robolectric locale to locale B, and assert `TrackingViewsRepository.getTrackingViewsFlow()` and `getTrackingViewInfoFlow()` emit localized default titles in locale B while preserving custom/numeric titles 100% verbatim.
  3. *Behavioral Compose UI Locale-Switch & Emulator Verification (`TST-TRK-097.3`, `TrackingTabTitleResolverTest.kt`, `ProcessStabilityComposeUiTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     • Render `TrackingTabPreviewHeader`, `TrackingTabConfigHeader`, and tab bar labels via Robolectric Compose `createComposeRule()` under switched `LocalConfiguration` locales (`de -> es/ja/pt`) asserting default tabs re-localize immediately (even when `TrackingViewInfo.name` carries legacy `"Standard"`/`"Karte"`/`"Runden"`) while custom titles stay intact; verify live `emulator-5554` locale switch (`Rule 8.5` before/after PNGs + clean `logcat`) and `TrackingTabsScreen.kt <= 1,500` LoC (`Localization Parity: N/A - 0 string changes`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-TST-TRK` work-product control header and Change History. |
| `1.0.1` | 2026-11-17 | AI Agent (`ATT-8450`) | `ATT-8450` | Added `TST-CON-048` [Planned] and cross-referenced `TST-CON-031.5` for offline `EditDeviceDialog` radar light config. |
| `1.0.2` | 2026-11-17 | AI Agent (`ATT-8450`) | `ATT-8450` | Promoted `TST-CON-048` to `[Implemented]` following Stage 4 unit & Compose UI test construction. |
| `1.0.3` | 2026-11-17 | AI Agent (`ATT-8450`) | `ATT-8450` | Promoted `TST-CON-048` to `[Verified]` in Stage 5 following post-rebase verification. |
| `1.0.4` | 2026-11-17 | AI Agent (`ATT-8707`) | `ATT-8707` | Added `TST-FIL-024` [Proposed] for `VerticalSpeedAndSlopeDevice` baro broadcast guard, `onAccumulatorsReset()`, and same-device step re-seeding. |
| `1.0.5` | 2026-11-17 | AI Agent (`ATT-8707`) | `ATT-8707` | Promoted `TST-FIL-024` to `[Implemented]` following Stage 4 unit test implementation. |
| `1.0.6` | 2026-11-17 | AI Agent (`ATT-8707`) | `ATT-8707` | Promoted `TST-FIL-024` to `[Verified]` in Stage 5 following targeted unit regression verification. |
| `1.0.7` | 2026-11-17 | AI Agent (`ATT-8707`) | `ATT-8707` | Stage 6 Bounce 1/3 heal: added active-baro no-double-shift regression (`TST-FIL-024.1c`) and `Double.isFinite` rejection test (`TST-FIL-024.3c`). |
| `1.0.8` | 2026-11-18 | AI Agent (`ATT-8705`) | `ATT-8705` | Added `TST-FIL-025` [Proposed] for post-workout ERTS running-pace heading anchoring, yaw-rate anti-aliasing, symmetric sanity bounds, and `Laps.db` synchronization. |
| `1.0.9` | 2026-11-18 | AI Agent (`ATT-8705`) | `ATT-8705` | Promoted `TST-FIL-025` to `[Implemented]` following Stage 4 unit & Robolectric test construction. |
| `1.0.10` | 2026-11-18 | AI Agent (`ATT-8705`) | `ATT-8705` | Promoted `TST-FIL-025` to `[Verified]` in Stage 5 following Tier-1 targeted unit/Robolectric regression verification. |
| `1.0.11` | 2026-11-18 | AI Agent (`ATT-8706`) | `ATT-8706` | Added and verified `TST-TRK-085` [Verified] for RR source isolation, monotonic clamping, physiological gap flagging, pre-filter sort, and DFA α1 validity gate (502/502 tests across 88 suites). |
| `1.0.17` | 2026-11-18 | AI Agent (`ATT-8706`) | `ATT-8706` | Promoted `TST-TRK-085` to `[Verified]` in Stage 5 following Bounce 3/3 heal verification (`449/449` tests across `83` suites). |
| `1.0.18` | 2026-11-18 | AI Agent (`ATT-8105`) | `ATT-8105` | Added and verified `TST-TRK-086` [Verified] (`HrvDfaA1RangeSliderGestureTest.kt` Robolectric `createComposeRule()` gesture isolation `5/5`, `Rule 8.5` Light/Dark PNG audit, and Tier-1 regression `31/31` across 7 suites). |
| `1.0.19` | 2026-11-18 | AI Agent (`ATT-8717`) | `ATT-8717` | Added and verified `TST-CON-049` [Verified] across 34 unit/Robolectric suites (166/166 PASS) including CardioSense DIS rejection, pre-CCCD DIS read order, 8-hex ID preservation, and IMU stream rejection. |
| `1.0.20` | 2026-11-18 | AI Agent (`ATT-8717` / `ATT-8712`) | `ATT-8717`, `ATT-8712` | Added `TST-CON-049` [Verified] and `TST-TRK-087` [Verified] cleanly post-`ATT-8206` rebase. |
| `1.0.25` | 2026-11-18 | AI Agent (`ATT-8714`) | `ATT-8714` | Added and verified `TST-CON-050` [Verified] post-Bounce 1/3 heal (`064edfd3`, `23` suites, `154/154` PASS incl. `SourceTextReadingAuditContractTest` 4/4 & `DetailedDesignParityContractTest` 3/3; `Rule 7.6` offline boundary). |
| `1.0.26` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Updated `TST-TRK-062` [Planned] (`createComposeRule()` clear-badge suppression + `14.dp` rail spacer guard with zero `src/main` reads per `REQ-PRO-049.1`, `TranslationParityTest` for 9-locale `radar_overlay_clear` cleanup, and `Rule 8.5` PNG preview) and reconciled `TST-TRK-064` [Planned]. |
| `1.0.27` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Promoted `TST-TRK-062` & `TST-TRK-064` to `[Implemented]` following `RadarEdgeOverlayTest` (`30/30`), 10-suite verification (`126/126`), `:app:lintDebug` (`0` errors), and `Rule 8.5` PNG capture. |
| `1.0.28` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Promoted `TST-TRK-062` & `TST-TRK-064` to `[Verified]` in Stage 5 following governance checks, `has_verified_test_evidence` verification, and `Rule 8.5` PNG audit. |
| `1.0.29` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Refined `TST-CON-047` & `TST-CON-048` to `[Planned]` mandating behavioral Robolectric Compose UI parity tests across `RadarLightQuickSwitchSheetTest` & `EditDeviceDialogRadarLightTest` (row-major 2-col grid, Light/Dark, StVZO/non-StVZO, online/offline `showActiveModeSubtitle`, `0` new `src/main` reads), contract guards (`ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest` noting `ATT-8823`), and `Rule 8.5` PNG `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png`. |
| `1.0.30` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Promoted `TST-CON-047` & `TST-CON-048` to `[Implemented]` following `RadarLightModeGridTest` (`3/3`), `EditDeviceDialogRadarLightTest` (`5/5`), `RadarLightQuickSwitchSheetTest` (`9/9`), and `14`-suite Stage 4 run (`109/109` PASS) + `Rule 8.5` PNG inspection. |
| `1.0.31` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Promoted `TST-CON-047` & `TST-CON-048` to `[Verified]` in Stage 5 following governance checks (`--sync-indexes`), `has_verified_test_evidence` verification, and `Rule 8.5` PNG audit. |
| `1.0.32` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Stage 4 Bounce 1/3 heal: clarified `DeadCodeEliminationContractTest` (`0` new violations; `4/5 PASS` with `1` pre-existing unrelated `ATT-8823` failure in `EditDeviceDialog.kt:1435..1501`) and set `TST-CON-047` & `TST-CON-048` -> `[Implemented]` pending Stage 5 re-verification. |
| `1.0.33` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Promoted `TST-CON-047` & `TST-CON-048` to `[Verified]` in Stage 5 (Bounce 1/3) backed by `14` JUnit XMLs (`109/109 PASS`) on `66f707f9`, governance checks (`exit 0`), and `Rule 8.5` PNG audit. |
| `1.0.34` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Added `TST-TRK-088` [Planned] for `MyHelper` `RADAR_APPROACH_SPEED_mps` unit mapping, caller integration, `SensorTypeUnitsContractTest`, `SystemArchitectureDesignContractTest`, and `SourceTextReadingAuditContractTest` (`0` new `src/main` reads). |
| `1.0.35` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Promoted `TST-TRK-088` to `[Implemented]` following Stage 4 construction of `MyHelperRadarUnitsTest.kt` and consolidated unit/contract test run. |
| `1.0.36` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Promoted `TST-TRK-088` to `[Verified]` in Stage 5 following 10-suite `--rerun-tasks` verification (`55/56` PASS across 9/10 full-PASS suites; `1` pre-existing `ATT-8823` failure in `EditDeviceDialog.kt`) and `has_verified_test_evidence("TST-TRK-088")` PASS. |
| `1.0.37` | 2026-11-18 | AI Agent (`ATT-8881`) | `ATT-8881` | Added `TST-FIL-026` `[Verified]` following clean-room `:app:compileDebugKotlin` (0 new warnings) and 8-suite `:app:testDebugUnitTest` (`67/67` PASS across 8 JUnit XMLs). |
| `1.0.38` | 2026-11-18 | AI Agent (`ATT-8733`) | `ATT-8733` | Added `TST-TRK-089` `[Verified]` (`RadarEdgeOverlayTest` `37/37` PASS w/ direct marker tap/swipe pass-through, Frame-1 `.layout` clamping & `FAST_APPROACHING`-only pulse; `:app:lintDebug` `0` errors; `Rule 8.5` Light/Dark/Map PNG). |
| `1.0.39` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Added `TST-FIL-027` `[Planned]` and refined `TST-FIL-017.3` / `TST-FIL-019.3` for `DISTANCE_m_LAP`, `PolyUtil.simplify(..., 10.0)` (`2` collinear endpoints), and `WorkoutSummaries.ENCODING_STEP_SIZE` (`20`) parity. |
| `1.0.41` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `TST-FIL-027` to `[Implemented]` following Stage 4 test implementation (`ErtsPostWorkoutRecomputerTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `ExtendedRtsOdometrySmootherTest.kt`, `ExtendedRtsAltitudeSlopeSmootherTest.kt`). |
| `1.0.42` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `TST-FIL-027` to `[Verified]` in Stage 5 following Tier 1 targeted verification (`54/54` PASS across 6 JUnit XMLs) and `verify_requirement_governance.py` (`exit 0`). |
| `1.0.43` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Stage 4 Bounce 1/3 heal: added tests for `RADAR`/`YAW` non-matching + `NaN` sanitization (`ErtsPostWorkoutRecomputerTest`, `ExtendedRtsOdometrySmootherTest`, `UnicycleOdometryEkfTest`) and leading null `TIME_TOTAL` + `>= 6s` batch-flush stall + repeated wall-clock pauses (`ErtsPostWorkoutRecomputerTest`), setting `[Implemented]` pending Stage 5 re-verification. |
| `1.0.44` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `TST-FIL-027` to `[Verified]` in Stage 5 (Bounce 1/3) on `8a976a35` (`58/58` Author Tier 1 + `62/62` Gate 4 Bounce 1 PASS incl. `SourceTextReadingAuditContractTest`, `verify_requirement_governance.py` `exit 0`). |
| `1.0.45` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Added `TST-FIL-028` (`[Planned]`, `TST-FIL-028.1`–`.5`) covering all 9 `PhoneCarriageMode` states, `Zum Bäcker` timeline, missing-sensor/denied-permission fallback, `SensorType.ACTIVITY_AND_CARRIAGE_MODE` DB/CSV/contract guards, smooth live EKF adaptation, 1-Hz ERTS ingestion + legacy fallback, and 9-locale/`SYS.2`/`SYS.3`/`SWE.2` parity. |
| `1.0.46` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Promoted `TST-FIL-028` to `[Implemented]` following Stage 4 implementation and 23-suite unit/contract validation. |
| `1.0.47` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Promoted `TST-FIL-028` to `[Verified]` in Stage 5 following Gate 4 Pass (`195/195` across 24 suites + Rev-1 `89/89` `--rerun-tasks` across 9 suites, `0` failures/`0` skipped) and `verify_requirement_governance.py` (`exit 0`). |
| `1.0.48` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Stage 4 Bounce 1/3 heal: added regression tests for no-Doppler `Moving -> Stationary` ghost-tail elimination, 50-Hz classify/emission throttle & `dt`-invariant smoothing, and expired GNSS bearing-gap `estimatedGnssYawRateRadPerSec` reset; set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.49` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Promoted `TST-FIL-028` to `[Verified]` in Stage 5 (Bounce 1/3) following Gate 4 Pass (`92/92` `--rerun-tasks` across 9 post-heal suites + unchanged `195/195` baseline across 24 suites, `0` failed/`0` skipped, `verify_requirement_governance.py` `exit 0`). |
| `1.0.50` | 2026-11-18 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Added `TST-CON-051` `[Planned]` (`TST-CON-051.1`–`.3`) covering search-queue timeout release in `TRACKING`/`PAUSED`, decoupled background reconnect without `tries <= 1` teardown or `mSearching` re-entry, and `DeviceManager.java <= 1,708` LoC + detailed-design parity. |
| `1.0.51` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Promoted `TST-CON-051` to `[Implemented]` following Stage 4 unit test construction across `DeviceManagerSearchQueueTest`, `BLEReconnectRobustnessTest`, `MyBTLEDeviceAddressAndSearchTest`, and `MyBTLEDeviceLifecycleTest`. |
| `1.0.52` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Promoted `TST-CON-051` to `[Verified]` in Stage 5 following targeted unit/contract verification, static analysis baselines, and emulator deployment. |
| `1.0.53` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Stage 6 Bounce 1/3 heal: added `BLEReconnectRobustnessTest.testSearchTimeout_whenAlreadyConnectedToGatt_abortsWithoutReconnectOrStopSearching` and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.54` | 2026-11-19 | AI Agent (`ATT-8998`, `--as agent1`) | `ATT-8998` | Promoted `TST-CON-051` to `[Verified]` in Stage 5 (Bounce 1/3) following `--rerun-tasks` verification (`125/125` PASS across 19 suites), static baselines, emulator check, and governance `exit 0`. |
| `1.0.55` | 2026-11-18 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Added `TST-FIL-029` (`[Planned]`, `TST-FIL-029.1`–`.5`) covering shared DSP equivalence, `BIKE_FRONT_POCKET` coasting/`BIKE_HANDLEBAR` suppression/`BIKE_JERSEY_BACK` prominence, `RUN_*` cadence + glance blanking + gyro-absent fallback, `CADENCE_PRIORITY`/`STRIDES_PRIORITY` arbitration, monolith caps, and `SYS.2`/`SYS.3`/`SWE.2` sync. |
| `1.0.56` | 2026-11-18 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `TST-FIL-029` to `[Implemented]` following Stage 4 test construction (`SmartphoneImuCadenceEstimatorTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `MySensorManagerCadenceArbitrationTest.kt`, `MySensorManagerPriorityTest.kt`, `DeviceTypeSensorMappingTest.kt`) and targeted unit/contract test execution. |
| `1.0.57` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `TST-FIL-029` to `[Verified]` in Stage 5 following post-rebase `--rerun` verification across 18 suites (`146/146 PASS`: 8 unit suites, 8 contract/audit suites, `MeasuredNfrBenchmarkTest`, `TranslationParityTest`), `DeadCodeEliminationContractTest` baseline confirmation (`0` `ATT-8895` findings; `4/5` with pre-existing `EditDeviceDialog.kt` failure), and live `emulator-5554` smoke run. |
| `1.0.58` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Stage 6 Bounce 1/3 heal + Gate 4 revision: added CHRE delayed batch & interleaved FIFO per-sensor timestamp test, non-rewinding `updateCarriageClassification()` clock & `dt`-invariant adaptation test, mid-run `STRIDES` accumulator preservation test (`SpeedAndLocationDevice_ATTFusedTest`), gyro/step-absent `RUN_BICEP_ARM`/`RUN_HANDHELD` stride fallback test (`SmartphoneImuCadenceEstimatorTest`), aligned `WorkoutRepository.kt <= 4,973` (`+0` LoC), and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.59` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `TST-FIL-029` to `[Verified]` in Stage 5 (Bounce 1/3) following Gate 4 Pass (`ATT-9044`), clean Stage 5 `--rerun` verification across all 18 targeted/contract/audit/NFR/localization suites, `DeadCodeEliminationContractTest` baseline confirmation, static baselines & linters PASS, and live `emulator-5554` smoke run. |
| `1.0.60` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Stage 6 Bounce 2/3 heal: added 20s gyro-absent `RUN_BICEP_ARM` strong `armAcc` full stride count + `RUN_BICEP_ARM -> RUN_WAIST_HIP` transition regression test (`SmartphoneImuCadenceEstimatorTest`) and live `ProxySensor`/late `FilterManager` listener zero-dip mid-run `STRIDES` retention test (`SpeedAndLocationDevice_ATTFusedTest`), rebased cleanly onto `sprint/2026-11-18_03_Nacht` (`e62a6ba4`), and set `[Verified]` for Stage 5 Bounce 2 verification. |
| `1.0.61` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `TST-FIL-029` to `[Verified]` in Stage 5 (Bounce 2/3) on `sprint/2026-11-18_03_Nacht` (`e62a6ba4`) following `--rerun-tasks` verification across `29` unit/NFR/contract/audit/localization suites (`201/201 PASS`, `0` fail, `0` error, `0` skipped), static analysis baselines & 4 Python safety linters PASS, and `docs/engineering/walkthroughs/ATT-8895_walkthrough.md`. |
| `1.0.62` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Stage 6 Bounce 3/3 heal: added end-to-end Robolectric smartphone-only `BANALService` sport-context integration test (`SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`, zero test hints) proving `WALKING_TRANSITION` + running `STRIDES` registration, `BIKE` broadcast teardown, and `onDestroy()` cleanup (`BANALServiceLifecycleTest`), plus `RUN -> BIKE (transient dwell strides) -> RUN` baseline sync regression test (`SpeedAndLocationDevice_ATTFusedTest`), and set `[Implemented]` pending Stage 5 re-verification. |
| `1.0.63` | 2026-11-19 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Promoted `TST-FIL-029` to `[Verified]` in Stage 5 (Bounce 3/3) rebased cleanly atop `sprint/2026-11-19_01_Früh` (`8e44039d`) reusing Stage 4 Bounce 3 (`32` suites / `215/215` + `5` incremental suites / `42/42`) and Gate 4 Pass (`ATT-9068`: `23` suites / `169/169` + `5` suites / `42/42`, `0` new warnings) evidence including zero-SQLite hot-path cache verification (`Rule 7.5`). |
| `1.0.64` | 2026-11-19 | AI Agent (`ATT-8965`, `--as agent1`) | `ATT-8965` | Added & verified `TST-CON-052` (`[Verified]`, `TST-CON-052.1`–`.3`) following Gate 4 Pass (`ATT-9083`: `105/105` incremental `--rerun` PASS; Author Stage 4 `103/103` across 14 suites, `0` new compiler warnings) reusing Gate 4 evidence (`Rule 7.5`, zero `app/` edits post-Gate 4) and `docs/engineering/walkthroughs/ATT-8965_walkthrough.md`. |
| `1.0.65` | 2026-11-19 | AI Agent (`ATT-8965`, `--as agent1`) | `ATT-8965` | Stage 6 Bounce 1/3 heal: added regression unit tests for `effectiveDelayMs` live clamping (`SensorReconnectBackoffPolicyTest`, `BLEReconnectRobustnessTest`), disable-while-pending zombie cleanup & runnable-identity cancellation race (`DeviceSearchPeriodicRetryCoordinatorTest`, `DeviceManagerSearchQueueTest`), and Polar echo double-advance / `blePowerStateChanged` / exception reschedule (`PolarOnlineStreamingBridgeTest`); set `[Implemented]` pending verification. |
| `1.0.66` | 2026-11-19 | AI Agent (`ATT-8965`, `--as agent1`) | `ATT-8965` | Promoted `TST-CON-052` to `[Verified]` in Stage 5 (Bounce 2) reusing Gate 4 Bounce 2 Pass (`ATT-9088`: `128/128` incremental `--rerun` PASS across 16 suites, zero `app/` changes post-`587730da` per `Rule 7.5`) and `docs/engineering/walkthroughs/ATT-8965_walkthrough.md`. |
| `1.0.67` | 2026-11-19 | AI Agent (`ATT-8891`, `--as agent1`) | `ATT-8891` | Added & verified `TST-FIL-030` (`[Verified]`, `TST-FIL-030.1`–`.5`) rebased cleanly atop `sprint/2026-11-19_01_Früh` (`6bb37af4`) reusing Gate 4 Pass (`ATT-9091`: `19` suites / `147/147` PASS, `0` failed, `0` skipped, zero `app/` diff post-`ee81c8ae` per `Rule 7.5`), static baselines (`lint = 216`, `detekt = 9509`) & safety linters PASS, and `docs/engineering/walkthroughs/ATT-8891_walkthrough.md`. |
| `1.0.68` | 2026-11-19 | Lane A Flash (`ATT-8329`, `--as agent1`) | `ATT-8329` | Updated `Component` & `Test Description` for `TST-FIL-002` (5-mode `calcExtremaValue` Robolectric matrix), `TST-FIL-003` (dedicated `WorkoutSummariesAltitudeShiftTest.kt` + session/samples tags), and `TST-CON-007` (`BatteryDeviceTest.kt`), remaining `[Implemented]` pending Stage 4/5 execution. |
| `1.0.69` | 2026-11-19 | Lane A Flash (`ATT-8329`, `--as agent1`) | `ATT-8329` | Promoted `TST-FIL-002`, `TST-FIL-003`, and `TST-CON-007` to `[Verified]` in Stage 5 reusing Gate 4 verified evidence (`Rule 7.5`: `33/33` PASS across 5 suites, `0` `app/` code changes since Gate 4, `ATT-8329_walkthrough.md`). |
| `1.0.70` | 2026-11-19 | AI Agent (`ATT-8951`, `--as agent1`) | `ATT-8951` | Refined & verified `TST-TRK-072.9` (`[Verified]`) atop `50a57b3e` following Gate 4 `34/34` PASS (`HrvDfaA1RangeSliderGestureTest` `10/10`, `HrvDfaA1SettingsContractTest` `12/12`, `HrvRampTestScreenTest` `6/6`, `SourceTextReadingAuditContractTest` `6/6`), `Rule 8.5` PNG `img/ATT-8951_hrv_cta_above_fold.png`, and `--check-verified-evidence` `exit 0`. |
| `1.0.71` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Added & verified `TST-FIL-031` (`[Verified]`, `TST-FIL-031.1`–`.5`) rebased cleanly atop `sprint/2026-11-19_01_Früh` (`50a57b3e`) reusing Gate 4 Pass (`ATT-9113`: `17` suites / `140/140` PASS, `0` warnings, `0` `app/` diff post-`7e594f8a` per `Rule 7.5`), static baselines (`lint = 216`, `detekt = 9509`) & safety linters PASS, and `docs/engineering/walkthroughs/ATT-8917_walkthrough.md`. |
| `1.0.72` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Stage 6 Bounce 1/3 heal verified (`ATT-9128`: `11` suites / `123/123` PASS, `0` `app/` diff post-`fe67803b` per `Rule 7.5`): added phone-only duathlon `T1` kinematic `RUN` carryover test (`MultisportTransitionDetectorTest`, `10/10`) and in-memory pause/resume multisport leg preservation test (`TrackerServiceResumeTest`, `18/18`), rebased cleanly atop `sprint/2026-11-19_02_Spät` (`73ff0cdd`). |
| `1.0.73` | 2026-11-19 | AI Agent (`ATT-8859`, `--as agent1`) | `ATT-8859` | Added `TST-SEN-032` `[Planned]` (`TST-SEN-032.1`, `TST-SEN-032.2`) for `WheelPositionSelectionDialog` Front/Rear-only behavioral Compose UI testing (`WheelPositionSelectionDialogUiTest`), legacy `UNSPECIFIED` fallback preservation, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, and `Rule 8.5` Light/Dark PNG (`Localization Parity: N/A - no UI strings`). |
| `1.0.74` | 2026-11-19 | AI Agent (`ATT-8859`, `--as agent1`) | `ATT-8859` | Promoted `TST-SEN-032` to `[Implemented]` following Stage 4 test execution (`35/35` PASS across 6 suites post-rebase onto `6927b2f8`: `WheelPositionSelectionDialogUiTest 4/4`, `WheelPositionDialogsTest 4/4`, `EditDeviceEquipmentWheelPositionTest 5/5`, `ArchitectureConformanceContractTest 11/11`, `DeadCodeEliminationContractTest 5/5`, `SourceTextReadingAuditContractTest 6/6`) and `Rule 8.5` Light/Dark PNG inspection. |
| `1.0.75` | 2026-11-19 | AI Agent (`ATT-8859`, `--as agent1`) | `ATT-8859` | Promoted `TST-SEN-032` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9145`) `35/35` PASS evidence (`Rule 7.5`, zero `app/` diff since `9e265f02`), `Rule 8.5` Light/Dark PNG (`ATT-8859_wheel_position_dialog.png`), and `verify_requirement_governance.py` `exit 0`. |
| `1.0.76` | 2026-11-19 | AI Agent (`ATT-9064`, `--as agent1`) | `ATT-9064` | Added `TST-FIL-032` `[Planned]` (`TST-FIL-032.1`–`.3`) covering `584`-row `78`-pair `0/2` `TIME_TOTAL` beat non-stretching & pause/stall parity, `speedAverage_mps` / `Extrema(AVG)` / `Laps.db` parity + matched `activeDist / activeDt` fallback, and `UnicycleOdometryEkf.predict(isStationary = true)` + `0.00 m` indoor Haversine pinning (`N/A - no UI strings`). |
| `1.0.77` | 2026-11-19 | AI Agent (`ATT-9064`, `--as agent1`) | `ATT-9064` | Promoted `TST-FIL-032` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`84/84` PASS across 9 suites: `ErtsPostWorkoutRecomputerTest 17/17`, `ExtendedRtsOdometrySmootherTest 12/12`, `UnicycleOdometryEkfTest 17/17`, `TrackerServiceErtsFinalizationTest 2/2`, `LapsDatabaseManagerTest 10/10`, `ArchitectureConformanceContractTest 11/11`, `SystemLiveTrackingRequirementsContractTest 4/4`, `DeadCodeEliminationContractTest 5/5`, `SourceTextReadingAuditContractTest 6/6`). |
| `1.0.78` | 2026-11-19 | AI Agent (`ATT-9064`, `--as agent1`) | `ATT-9064` | Promoted `TST-FIL-032` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9167`) `84/84` PASS evidence (`Rule 7.5`, zero `app/` diff since `75f3bff9`), 4 safety linters PASS, `verify_requirement_governance.py` `exit 0`, and `docs/engineering/walkthroughs/ATT-9064_walkthrough.md`. |
| `1.0.79` | 2026-11-19 | AI Coordinator (`ATT-9121`) | `ATT-9121` | Cleaned Criterion 4 symmetric `Requirement(s)` scope on `TST-SEN-032` (`REQ-SEN-032` only) and `TST-FIL-032` (`REQ-FIL-032` only) for 100% full-governance parity at Sprint 463 Tier-2 closeout. |
| `1.0.80` | 2026-11-19 | AI Agent (`ATT-9063`, `--as agent1`) | `ATT-9063` | Added `TST-FIL-033` `[Planned]` (`TST-FIL-033.1`–`.3`, strictly symmetric `REQ-FIL-033`) covering `-3 m`/`-10 m` DEM step startup + `20 s` OU standstill closed loop, `18 m/s` `-10%` alpine descent & NaN-safe speed-coupled slew, and barometer-free/leading-null/`n == 1` fallback (`N/A - no UI strings`). |
| `1.0.81` | 2026-11-19 | AI Agent (`ATT-9063`, `--as agent1`) | `ATT-9063` | Promoted `TST-FIL-033` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`109/109` PASS across 11 targeted/contract suites, `0` fail, `0` err, `0` skip, `0` new warnings). |
| `1.0.82` | 2026-11-19 | AI Agent (`ATT-9063`, `--as agent1`) | `ATT-9063` | Promoted `TST-FIL-033` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9196`) `109/109` PASS evidence (`Rule 7.5`, zero `app/` diff since `0f4dd34b`), `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9063_walkthrough.md`. |
| `1.0.83` | 2026-11-20 | AI Agent (`ATT-9062`, `--as agent1`) | `ATT-9062` | Added `TST-FIL-034` `[Planned]` (`TST-FIL-034.1`–`.4`, strictly symmetric `REQ-FIL-034`) covering indoor GNSS-outage/stagnant-fix/decay-to-zero `STATIONARY`, cyclic-immune `detectGlanceShift()` & front-pocket cycling, accuracy-gated multipath speed & `isMovingByGnss`, and cross-device/athlete portability (`N/A - no UI strings`). |
| `1.0.84` | 2026-11-20 | AI Agent (`ATT-9062`, `--as agent1`) | `ATT-9062` | Promoted `TST-FIL-034` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`135/135` PASS across 12 suites, `0` fail, `0` err, `0` skip, `0` new warnings). |
| `1.0.85` | 2026-11-20 | AI Agent (`ATT-9062`, `--as agent1`) | `ATT-9062` | Promoted `TST-FIL-034` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9208`) `135/135` PASS evidence (`Rule 7.5`, zero `app/` diff since `4b47831a`), clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9062_walkthrough.md`. |
| `1.0.86` | 2026-11-20 | AI Agent (`ATT-9157`, `--as agent1`) | `ATT-9157` | Added `TST-FIL-035` `[Planned]` (`TST-FIL-035.1`–`.3`, strictly symmetric `REQ-FIL-035`) covering sport-context cycling guardrail + non-sticky eviction + brick override, easy-pace `walkingStepBand`/`walkingSpeedBand` bypass + front-hip belt 9-profile separation, and `SCORE_MARGIN = 0.85` + asymmetric dwell (`N/A - no UI strings`). |
| `1.0.87` | 2026-11-20 | AI Agent (`ATT-9157`, `--as agent1`) | `ATT-9157` | Promoted `TST-FIL-035` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`138/138` PASS across 12 suites, `0` fail, `0` err, `0` skip, `0` new warnings; `4` static `TST-FIL-035` tags indexed). |
| `1.0.88` | 2026-11-20 | AI Agent (`ATT-9157`, `--as agent1`) | `ATT-9157` | Promoted `TST-FIL-035` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9224`) `138/138` PASS evidence (`Rule 7.5`, zero `app/` diff since `9b89af95`), clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9157_walkthrough.md`. |
| `1.0.89` | 2026-11-20 | AI Agent (`ATT-9158`, `--as agent1`) | `ATT-9158` | Added `TST-FIL-036` `[Planned]` (`TST-FIL-036.1`–`.3`, strictly symmetric `REQ-FIL-036`) covering cold-start outdoor release + unbypassable vetoes + indoor oscillating multipath rejection, raw-chord heading seeding + unanchored/post-outage velocity decoupling + bounded Doppler variance, and ERTS `dt_gnss`-normalized teleport decoupling + 1-tick glitch pre-gate + genuine sprint & curved tunnel non-regression (`N/A - no UI strings`). |
| `1.0.90` | 2026-11-20 | AI Agent (`ATT-9158`, `--as agent1`) | `ATT-9158` | Promoted `TST-FIL-036` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`144/144` PASS across 12 suites + `:app:assembleDebug`, `0` fail, `0` err, `0` skip, `0` new compiler warnings; explicit hyphenated `REQ-FIL-036.x / TST-FIL-036.x` tags indexed across 6 test suites). |
| `1.0.91` | 2026-11-20 | AI Agent (`ATT-9158`, `--as agent1`) | `ATT-9158` | Promoted `TST-FIL-036` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9246`) `144/144` PASS evidence on `97f49453` (`Rule 7.5`, zero `app/` diff since `97f49453`), `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9158_walkthrough.md`. |
| `1.0.92` | 2026-11-20 | AI Agent (`ATT-9156`, `--as agent1`) | `ATT-9156` | Added `TST-FIL-037` `[Planned]` (`TST-FIL-037.1`–`.3`, strictly symmetric `REQ-FIL-037`) covering coherent `DEFAULT_VAR_DEM = 25.0`/`DEFAULT_VAR_GPS = 64.0`, baro/standstill slope decoupling + 3×3 Joseph SPD, baro-vs-DEM step-discrepancy jump gating with reference tracker advancement + quadratic Huber variance inflation + `varRef <= 1.0` guard, and synthetic Pixel 10 `Kurze Runde var 2 #53` loop replay (`N/A - no UI strings`). |
| `1.0.93` | 2026-11-20 | AI Agent (`ATT-9156`, `--as agent1`) | `ATT-9156` | Promoted `TST-FIL-037` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`72/72` PASS across 7 unit & contract suites + `:app:assembleDebug`, `0` fail, `0` err, `0` skip, `0` new compiler warnings; explicit hyphenated `REQ-FIL-037.x / TST-FIL-037.x` tags indexed in `KinematicAltitudeSlopeEkfTest` and `AltitudeAndSlopeDevice_ATTFusedTest`). |
| `1.0.94` | 2026-11-20 | AI Agent (`ATT-9156`, `--as agent1`) | `ATT-9156` | Promoted `TST-FIL-037` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9260`) independent `72/72` PASS evidence on `0b792832` (`Rule 7.5`, zero `app/` diff since `0b792832`), `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9156_walkthrough.md`. |
| `1.0.95` | 2026-11-20 | AI Agent (`ATT-8422`, `--as agent1`) | `ATT-8422` | Added `TST-SEN-033` `[Planned]` (`TST-SEN-033.1`–`.3`, strictly symmetric `REQ-SEN-033`) covering `EditDeviceDialog` bike calculator removal & `RUN_SPEED` preservation, tire-size preset + auto-calibration hint + localized baseline, `EquipmentRepository` chronological per-sensor history & async `EditDeviceViewModel` baseline sync, `WheelCircumferenceHistoryChart` `UNSPECIFIED` series/legend & sensor empty state, Rule 8.5 Light/Dark PNG capture, and 9-locale `TranslationParityTest` + governance contracts. |
| `1.0.96` | 2026-11-20 | AI Agent (`ATT-8422`, `--as agent1`) | `ATT-8422` | Promoted `TST-SEN-033` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`61/61` PASS across 11 UI/DB/localization/contract suites + `:app:assembleDebug`, `0` fail, `0` err, `0` skip, `0` production compiler warnings; Rule 8.5 Light/Dark PNG visually verified; explicit hyphenated `REQ-SEN-033.x / TST-SEN-033.x` tags indexed in `EditDeviceWheelCircumferenceTest.kt`). |
| `1.0.97` | 2026-11-20 | AI Agent (`ATT-8422`, `--as agent1`) | `ATT-8422` | Promoted `TST-SEN-033` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9268`) independent `61/61` PASS evidence on `f05ab17b` (`Rule 7.5`, zero `app/` diff since `f05ab17b`), `Rule 8.5` Light/Dark PNG certification, `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-8422_walkthrough.md`. |
| `1.0.98` | 2026-11-20 | AI Agent (`ATT-8422`, `--as agent1`) | `ATT-8422` | Stage 5 Bounce 1 update: refreshed `TST-SEN-033` `[Verified]` evidence to Gate 4 Bounce 1 (`ATT-9275`) independent `63/63` PASS on `c4bdc03c` (`0` Kotlin compiler warnings across all `ATT-8422` files, zero `app/` diff since `c4bdc03c` per `Rule 7.5`), adding verbatim Bounce 1 regression tests `testBaselineCircumferenceRounding_avoidsIeee754TruncationAt2010mm_tstSen033_1` (`roundToInt()`) and `testNarrow280dpThreeLegendFlowRow_wrapsWithoutClippingOrZeroWidth_tstSen033_3` (`FlowRow` legend wrap). |
| `1.0.99` | 2026-11-20 | AI Agent (`ATT-9159`, `--as agent1`) | `ATT-9159` | Added `TST-TRK-090` `[Planned]` (`TST-TRK-090.1`–`.2`, strictly symmetric `REQ-TRK-090`) covering pre-mutation UTC `TIME_START` capture, pause neutrality, in-process vs post-process-death resume scoping, `LapsDatabaseManager` UTC `now - lapTime` fallback, FIT export round-trip, and `TrackerService.java <= 3,559` LoC / source-read contracts (`N/A - no UI strings`). |
| `1.0.100` | 2026-11-20 | AI Agent (`ATT-9159`, `--as agent1`) | `ATT-9159` | Promoted `TST-TRK-090` to `[Implemented]` following Stage 4 unit test construction (`MultisportLapCoordinatorTest.kt`, `LapsDatabaseManagerTest.kt`, `TrackerServiceLapTest.kt`) and incremental Gradle verification. |
| `1.0.101` | 2026-11-20 | AI Agent (`ATT-9159`, `--as agent1`) | `ATT-9159` | Promoted `TST-TRK-090` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9294`) `103/103 PASS` across 10 suites on `45c3fd9c` (`Rule 7.5`, zero `app/` diff post-`45c3fd9c`, `0` new compiler warnings), clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9159_walkthrough.md`. |
| `1.0.102` | 2026-11-20 | Lane B Author (`ATT-8994`, `--as agent1`) | `ATT-8994` | Added `TST-FIL-038` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-FIL-038`) covering lock-free `MySensor.deactivateSensor()` & silent `MyAccumulatorSensor`/`ProxySensor` overrides, dynamic HRM deactivation, `FilterManager` disconnect clearing with accumulator/cumulative immunity & `ProxySensor` retention + `createFilter()` parity, and sparkline flat-line termination (`N/A - no UI strings`). |
| `1.0.103` | 2026-11-20 | Lane B Author (`ATT-8994`, `--as agent1`) | `ATT-8994` | Promoted `TST-FIL-038` to `[Implemented]` following Stage 4 implementation and 100% PASS across all 16 unit, concurrency, sparkline & contract suites (`MySensorDeactivationAndProxyTest`, `MySensorManagerProxyTest`, `MyAccumulatorSensorResetToTest`, `SensorAndDeviceConcurrencyStressTest`, `SpeedAndLocationDeviceDecayTest`, `FilterManagerRebindTest`, `FilterNullSafetyTest`, `FilterResetAndPreStartTest`, `FilterManagerAccumulatorResetTest`, `ANTHeartRateDeviceTest`, `BTLEHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`) with `0` new Kotlin compiler warnings. |
| `1.0.104` | 2026-11-20 | Lane B Author (`ATT-8994`, `--as agent1`) | `ATT-8994` | Promoted `TST-FIL-038` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9293`) verified `140/140 PASS` evidence across all 16 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` code drift since `b34c62cc`), exact verbatim `TestClass#testMethod` citations (`10/10`), `4/4` Python safety linters PASS, and `docs/engineering/walkthroughs/ATT-8994_walkthrough.md`. |
| `1.0.105` | 2026-11-20 | AI Agent (`ATT-8421`, `--as agent1`) | `ATT-8421` | Added `TST-SEN-034` `[Planned]` (`TST-SEN-034.1`–`.3`, strictly symmetric `REQ-SEN-034`) covering `BANALServiceRepository` persisted `baselineCircumference` IO cache + live reset propagation (`SharedFlow`/broadcast/`clearBreadcrumbs` + explicit `0.0` overwrite) + `LowPressure` alert, null/non-finite GNSS accuracy/bearing skipping with GPS-free `DualWheelTpmsEstimator` isolation, and `ATT-8422` 9-locale parity & architecture/governance contracts. |
| `1.0.106` | 2026-11-20 | AI Agent (`ATT-8421`, `--as agent1`) | `ATT-8421` | Promoted `TST-SEN-034` to `[Implemented]` following Stage 4 incremental `--rerun` verification (`75/75` PASS across `12` suites + `:app:assembleDebug`, `0` fail, `0` err, `0` skip, `0` new Kotlin compiler warnings; explicit hyphenated `REQ-SEN-034.x / TST-SEN-034.x` tags indexed in `BANALServiceRepositoryItpmsWiringTest.kt`). |
| `1.0.107` | 2026-11-20 | AI Agent (`ATT-8421`, `--as agent1`) | `ATT-8421` | Gate 4 Revision 1 (`ATT-9292`): added per-device monotonic `baselineLoadGenerations` token + `repositoryScope`/`withContext(itpmsIoDispatcher)` thread confinement in `BANALServiceRepository.kt` (`1,354 / 1,500` LoC), added out-of-order `CompletableDeferred` discard test and `DeviceDataRepository.updateDevice()` baseline change test (`77/77` PASS across `12` suites + `:app:assembleDebug`, `0` new Kotlin compiler warnings). |
| `1.0.108` | 2026-11-20 | AI Agent (`ATT-8421`, `--as agent1`) | `ATT-8421` | Promoted `TST-SEN-034` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9292`) independent `77/77` PASS evidence on `ba662145` (`Rule 7.5`, zero `app/` diff since `ba662145`, verbatim `BANALServiceRepositoryItpmsWiringTest` `8/8` incl. `testOutOfOrderAsyncIoLoad_staleCompletionIsDiscardedAndDoesNotOverwriteNewerBaseline_tstSen034_1` & `testDeviceDataRepositoryUpdateDevice_emitsBaselineChangedAndUpdatesLiveEstimator_tstSen034_1`, `4/4` Python safety linters PASS, clean-checkout `verify_requirement_governance.py --check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-8421_walkthrough.md`). |
| `1.0.109` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Added `TST-CON-053` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-CON-053`) covering 4-stream atomic CAS/`CoroutineStart.LAZY`/job-identity guards, bounded PMD `ERROR_ALREADY_IN_STATE` recovery (`ACC`/`GYRO`/`PPI`), session-deduplicated Snackbar warning + 9-locale parity, and regression/contract guards. |
| `1.0.110` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Promoted `TST-CON-053` to `[Implemented]` following Stage 4 test construction (`PolarPmdStreamCoordinatorTest` `4/4`, `PolarOnlineStreamingBridgeTest` `35/35`, `PolarPmdStreamWarningEffectTest` `1/1`, `TranslationParityTest` `11/11`, 6 contract suites + 12 polar suites = `172/172 PASS`, `:app:assembleDebug` PASS, `0` new Kotlin compiler warnings). |
| `1.0.111` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Promoted `TST-CON-053` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9309` @ `80bd02ca`) `172/172 PASS` across 22 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` drift since `80bd02ca`), `8/8` verbatim `TestClass#testMethod` citations verified, `4/4` Python safety linters PASS, clean-checkout `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9161_walkthrough.md`. |
| `1.0.112` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Stage 6 Bounce 1/3 heal: added `PolarPmdStreamCoordinatorTest#testAtomicSlotCleanupAndPreAcquireException_preservesAccChannelOwnership_TST_CON_053_1` (`9/9` verbatim `TST-CON-053` methods) and set `[Implemented]` pending Gate 4 / Stage 5 re-verification. |
| `1.0.113` | 2026-11-20 | Lane B2 Author (`ATT-9161`, `--as agent1`) | `ATT-9161` | Promoted `TST-CON-053` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9313` @ `79e9e1b1`) `173/173 PASS` across 22 suites + `:app:assembleDebug` + `0` new warnings (`Rule 7.5`, `0` `app/` drift since `79e9e1b1`), `9/9` verbatim `TestClass#testMethod` citations verified, `4/4` Python safety linters PASS, clean-checkout `--check-verified-evidence` EXIT `0`, and refreshed `ATT-9161_walkthrough.md`. |
| `1.0.114` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Added `TST-CON-054` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-CON-054`) covering alias-complete GATT/Polar unpair teardown, connected-GATT discovery fallback, `12 s` silent-HR watchdog & CCCD retry, and monolith/governance contracts (`N/A - no UI strings`). |
| `1.0.115` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Promoted `TST-CON-054` to `[Implemented]` following Stage 4 test construction across `BleHrmSilentConnectionWatchdogTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest`, `MyBTLEDeviceLifecycleTest`, `BTLEHeartRateDeviceTest`, `BTSearchForNewDevicesEngineTest`, `DeviceManagerTeardownTest`, and contract suites. |
| `1.0.116` | 2026-11-20 | Lane B Author (`ATT-9155`, `--as agent1`) | `ATT-9155` | Stage 6 Bounce 1/3 heal & Stage 5 Bounce 1 `[Verified]`: added 4 regression tests (`14/14` verbatim `TST-CON-054` methods covering stale watchdog generation, `silentHrWatchdogLock`, stale GATT `onDescriptorWriteCompleted`, and `!posted` sync close fallback), promoted `[Verified]` reusing Gate 4 Bounce 1 (`ATT-9351` @ `98a377f6`, `Rule 7.5`, `0` `app/` drift) `184/184 PASS` across 19 suites + `:app:assembleDebug` + `0` new production Kotlin warnings, `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `ATT-9155_walkthrough.md`. |
| `1.0.114` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Added `TST-TRK-091` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-TRK-091`) covering non-blocking stop, armed-token isolation, atomic export deferral lock, edit-safe refresh, `shortService` FGS, 9-locale notification `8047`, and LoC/governance contracts. |
| `1.0.115` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Promoted `TST-TRK-091` to `[Implemented]` (`200/200` PASS across 23 unit/contract suites + `:app:assembleDebug` + `0` new Kotlin warnings + `4/4` Python safety linters PASS). |
| `1.0.116` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Promoted `TST-TRK-091` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9341` @ `9ca92f78`) `115/115` Bounce 1 (17 suites) + `200/200` full 23-suite (`7d3b0908`) PASS (`Rule 7.5`, `0` `app/` diff since `9ca92f78`, `19/19` verbatim `TestClass#testMethod` citations incl. Bounce 1 concurrency & `clusterId` regressions, `0` new Kotlin warnings, `4/4` safety linters PASS, `--check-verified-evidence` `exit 0`). |
| `1.0.117` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Stage 5 Bounce 1 (`ATT-9361` @ `7725517b`): promoted `TST-TRK-091` to `[Verified]` with `25/25` verbatim `TestClass#testMethod` citations (`113/113 PASS` across 16 suites incl. `+6` Stage 6 Bounce 1 regression tests for 1-arg `onTimeout`, `syncToHealthConnect` preservation, `hostLock -> stateLock` TOCTOU, `initialHadWarning`, and active `currentProgress`; `Rule 7.5` `0` `app/` diff since `7725517b`, `0` new Kotlin warnings, `4/4` safety linters PASS, `--check-verified-evidence` `exit 0`). |
| `1.0.119` | 2026-11-21 | Lane B Author (`ATT-9347`, `--as agent1`) | `ATT-9347` | Added `TST-FIL-039` `[Planned]` (`TST-FIL-039.1`–`.3`, symmetric `REQ-FIL-039`) covering active-footstrike cycling mask + `impactRms >= 1.8` batching guard, viewing-inclination `detectGlanceShift()` filter, Salomon front-hip belt replay (`>= 85%` `RUN_WAIST_HIP`), `10 s` non-glance dwell, and 9-mode + contract parity (`N/A - no UI strings`). |
| `1.0.120` | 2026-11-21 | Lane B Author (`ATT-9347`, `--as agent1`) | `ATT-9347` | Promoted `TST-FIL-039` to `[Implemented]` (`130/130` PASS across 11 suites incl. `PhoneCarriageClassifierTest 15/15` [`testActiveFootstrikeCyclingMaskAndHighImpactBatchingGuard_eliminatesBikeFrontPocketBleed_tstFil039_1`, `testViewingInclinationGlanceGuard_rejectsVerticalBeltRollsAndPreservesWristGlances_tstFil039_2`, `testSalomonFrontHipBeltReplayNarrowBicepCornerAndTenSecondDwell_achievesHighWaistShare_tstFil039_3`] & `SpeedAndLocationDevice_ATTFusedTest 34/34`, `0` new warnings, `3/3` safety linters PASS). |
| `1.0.121` | 2026-11-21 | Lane B Author (`ATT-9347`, `--as agent1`) | `ATT-9347` | Promoted `TST-FIL-039` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9449`) independent `130/130` PASS evidence @ `2680a799` (`Rule 7.5`, `0` `app/` diff since `2680a799`), `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-9347_walkthrough.md`. |
| `1.0.122` | 2026-11-22 | Lane 3 Author (`ATT-8949`, `--as agent1`) | `ATT-8949` | Extended `TST-CON-049` (`[Planned]`, `.1`–`.4`) for negative non-Polar GATT `(false, false)` + `isConfirmedNonPolarGatt` hard gate, Polar-gated `"Sense"` DIS classification, pre-rename legacy H10 backfill & non-Polar rename suppression, `Protocol.BLUETOOTH_LE` gate, empty-name resilience, and behavioral Robolectric Compose `createComposeRule()` `EditDeviceDialog` UI tests (`N/A - no UI strings`). |
| `1.0.123` | 2026-11-22 | Lane 3 Author (`ATT-8949`, `--as agent1`) | `ATT-8949` | Promoted `TST-CON-049` to `[Implemented]` following Stage 4 unit & behavioral Robolectric Compose UI test construction (`PolarOfflineCapabilityStoreTest`, `BTLEHeartRateDeviceTest`, `BTSearchForNewDevicesEngineTest`, `PolarOnlineStreamingBridgeTest`, `EditDevicePolarBackupTest`, `EditDevicePolarLiveImuTest` + `Rule 8.5` PNG capture). |
| `1.0.124` | 2026-11-22 | Lane 3 Author (`ATT-8949`, `--as agent1`) | `ATT-8949` | Promoted `TST-CON-049` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9590`) `176/176` PASS + Stage 4 `283/283` PASS (`Rule 7.5`, `0` `app/` drift), locked `emulator-5554` smoke (`0` crashes), `Rule 8.5` Light/Dark PNG audit, `ATT-9544` evidence bundle, and `docs/engineering/walkthroughs/ATT-8949_walkthrough.md`. |
| `1.0.125` | 2026-11-22 | Lane 3 Author (`ATT-8949`, `--as agent1`) | `ATT-8949` | Stage 6 Bounce 1/5 heal: added `testLegacyOfflineRecOnlyVeritySenseRenamedToPolarH10_staysVerityNeverBleedsH10_TST_CON_049_4`, `testLoadAllDevices_throwingBackfillDoesNotAbortDeviceListLoad_TST_CON_049_3`, process-restart `KEY_TYPE_PREFIX` persistence assertion, and negative-case Rule 8.5 PNG panel; reset `[Implemented]` pending Gate 4 Bounce 1. |
| `1.0.126` | 2026-11-22 | Lane 3 Author (`ATT-8949`, `--as agent1`) | `ATT-8949` | Promoted `TST-CON-049` to `[Verified]` in Stage 5 Bounce 1 following Gate 4 Bounce 1 (`ATT-9593`), `DeviceManagementUiTest` `2/2 PASS` (`d7d42580`), full `auto_gate.py --write-bundle` PASS (`566/566` tests across `68` JUnit XMLs + `2` clean screenshots = `71` SHA-256 hashed files), clean `emulator-5554` launch smoke (`0` crashes), and refreshed `docs/engineering/walkthroughs/ATT-8949_walkthrough.md`. |
| `1.0.0` | 2026-11-22 | Lane 1 Author (`ATT-6178`, `--as agent1`) | `ATT-6178` | Created Part 2 shard (`tst_tracking_cockpit_part2.md`, keeping `tst_tracking_cockpit.md` at `3,995/4,000` lines) and added `TST-TRK-092` `[Planned]` (`TST-TRK-092.1`–`.3`) for non-blocking `TrackerService.onDestroy()` and idempotent post-`endWorkout` cleanup (`N/A - no UI strings`). |
| `1.0.1` | 2026-11-22 | Lane 1 Author (`ATT-6178`, `--as agent1`) | `ATT-6178` | Promoted `TST-TRK-092` to `[Verified]` (`TrackerServiceFinalizationTest` `33/33` PASS incl. `TST-TRK-092.1`–`.3` after pre-fix `2,001 ms` RED proof, `163/163` PASS across 24 unit/contract suites, clean `emulator-5554` smoke, `docs/engineering/test_evidence/tickets/ATT-6178/`). |
| `1.0.2` | 2026-11-22 | Lane 3 Author (`ATT-8123`, `--as agent1`) | `ATT-8123` | Added & verified `TST-CON-055` (`ATT-8123` Bounce 1 concurrency hardening: `141/141` PASS across 8 suites incl. `RadarLightQuickSwitchSheetTest` `11/11`, `BTLERadarDeviceTest` `64/64`, `RadarLightControllerTest` `39/39`, `ArchitectureConformanceContractTest` `11/11`; Compose Light/Dark preview + live `emulator-5554` smoke `0` crashes). |
| `1.0.3` | 2026-11-22 | Lane 1 Author (`ATT-8124`, `--as agent1`) | `ATT-8124` | Added `TST-TRK-093` (`TST-TRK-093.1`–`.4`) & Gate 3 (`ATT-9608`) revision covering Polar H10 `startRecording` anchor, trailing RR trimming, single-pass post-backfill physiology/HRV recomputation, `MyBTLEDevice.pauseGattOperations()` GATT release, `TST-CON-054.3` reconciliation, and Part 1/2 regression lock. |
| `1.0.4` | 2026-11-22 | Lane 1 Author (`ATT-8124`, `--as agent1`) | `ATT-8124` | Promoted `TST-TRK-093` to `[Verified]` (`549/549` dependent tests PASS, `0` new compiler warnings, clean `emulator-5554` smoke, evidence bundle `docs/engineering/test_evidence/tickets/ATT-8124/`). |
| `1.0.5` | 2026-11-22 | Lane 1 Author (`ATT-8124`, `--as agent1`) | `ATT-8124` | Stage 6 Bounce 1/5 sync with `sprint/2026-11-22_01_Früh` (`1c3fbfd8`, preserving `TST-TRK-092`, `TST-CON-055`, `TST-TRK-093`) and added `MyBTLEDeviceLifecycleTest` regression tests for all 3 `mIsGattPaused` concurrency races. |
| `1.0.6` | 2026-11-22 | Lane 1 Author (`ATT-6034`, `--as agent1`) | `ATT-6034` | Added `TST-TRK-094` `[Planned]` (`TST-TRK-094.1`–`.4`, strictly symmetric `REQ-TRK-094`, `N/A - no UI strings`) covering `LapTelemetryExtractor`, `TrackerService` shutdown dedup, `PostWorkoutFinalizer`, `TrainingApplication` teardown helpers, and parameterized Strava sync prefs & scheduling. |
| `1.0.7` | 2026-11-22 | Lane 1 Author (`ATT-6034`, `--as agent1`) | `ATT-6034` | Promoted `TST-TRK-094` to `[Verified]` (`224/224` PASS across 34 unit/contract suites incl. Stage 6 Bounce 1 lazy `activeSessionDeviceIdsProvider` fault-isolation test, `0` fail, `0` err, `0` skip, clean `emulator-5554` smoke, SHA-256 bundle `docs/engineering/test_evidence/tickets/ATT-6034/manifest.json`). |
| `1.0.9` | 2026-11-22 | Lane 1 Author (`ATT-9498`, `--as agent1`) | `ATT-9498` | Added `TST-TRK-095` `[Planned]` (`TST-TRK-095.1`–`.4`, strictly symmetric `REQ-TRK-095`, `N/A - no UI strings`) covering `TrackerService`/`TrainingApplication` & `WorkoutPostProcessingService` FGS handshakes, FIFO `mWorkoutID` resolution, 3-OS/9-locale sweep verification, and LoC/SWE.3 parity. |
| `1.0.10` | 2026-11-22 | Lane 1 Author (`ATT-9498`, `--as agent1`) | `ATT-9498` | Promoted `TST-TRK-095` (`TST-TRK-095.1`–`.4`) to `[Verified]` with verbatim test citations (`24` JVM suites / `174/174` PASS, `47/47` Python tests PASS, `12/12` live Multi-OS & 9-language emulator sweep `PASS` in `2026-11-22_sweep.md`, `12/12` `Rule 8.5` PNGs audited, SHA-256 bundle `docs/engineering/test_evidence/tickets/ATT-9498/manifest.json`). |
| `1.0.11` | 2026-11-23 | Lane 2 Author (`ATT-9602`, `--as agent1`) | `ATT-9602` | Added `[Planned]` `TST-FIL-040` (`TST-FIL-040.1`–`TST-FIL-040.4`, strictly symmetric `REQ-FIL-040`) covering `70–88 m` pocket-compression barometer anti-aliasing, calm/handlebar `8–12%` responsiveness, corrupted-barometer DEM step-gate bypass, 3-state `5 s` startup calibration grace + offline fallback, and ERTS low-speed braking slope stability. |
| `1.0.13` | 2026-11-23 | Lane 2 Author (`ATT-9602`, `--as agent1`) | `ATT-9602` | Promoted `TST-FIL-040` (`.1`–`.4`) to `[Verified]` and aligned shard header `Version` (`1.0.13`) with verbatim method citations (`90/90` PASS across 8 targeted suites: `BarometricBucketDispersionTest` `4/4`, `KinematicAltitudeSlopeEkfTest` `17/17`, `AltitudeAndSlopeDevice_ATTFusedTest` `26/26`, `ExtendedRtsAltitudeSlopeSmootherTest` `10/10`, `BarometricAltitudeFilterTest` `7/7`, `OfflineTelemetryReplayHarnessTest` `11/11`, `OfflineReplayCliTest` `4/4`, `ArchitectureConformanceContractTest` `11/11`). |
| `1.0.14` | 2026-11-23 | Lane 2 Author (`ATT-9601`, `--as agent1`) | `ATT-9601` | Added `[Planned]` `TST-FIL-041` (`TST-FIL-041.1`–`TST-FIL-041.3`, strictly symmetric `REQ-FIL-041`) covering immediate indoor `acc = 45.9 m` zero-speed publication, `159 s` post-pause `+19.1 m` snap velocity bound (`3.51 m/s` vs `10.85 m/s`), genuine `0 -> 9.0 m/s` fast start, `REQ-TRK-056.2` `GoogleFused` preservation, and `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC. |
| `1.0.15` | 2026-11-23 | Lane 2 Author (`ATT-9601`, `--as agent1`) | `ATT-9601` | Promoted `TST-FIL-041` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`146/146` PASS across 11 targeted suites: `SpeedAndLocationDevice_ATTFusedTest` `40/40`, `UnicycleOdometryEkfTest` `20/20`, `SpeedAndLocationDeviceStationaryJitterTest` `15/15`, `SpeedAndLocationDeviceDecayTest` `14/14`, `SpeedAndLocationDeviceIndoorAndRestCouplingTest` `11/11`, `ExtendedRtsOdometrySmootherTest` `14/14`, `AttFusedColdStartHelperTest` `3/3`, `OdometrySimulationTest` `3/3`, `OfflineTelemetryReplayHarnessTest` `11/11`, `OfflineReplayCliTest` `4/4`, `ArchitectureConformanceContractTest` `11/11`). |
| `1.0.16` | 2026-11-23 | Lane 2 Author (`ATT-9600`, `--as agent1`) | `ATT-9600` | Added `[Planned]` `TST-FIL-042` (`TST-FIL-042.1`–`TST-FIL-042.3`, strictly symmetric `REQ-FIL-042`) covering Pixel 10 `-2.18 s` GNSS offset replay (`0` sub-`2 s` `4 <-> 1` flips), OEM `uptimeMillis`/epoch-ns `> 5 s` re-anchoring, wall-clock wheel alignment, UTC `MultisportTelemetryAnchor`/sidecar preservation, out-of-order `PhoneCarriageClassifier`/`UnicycleOdometryEkf` monotonic guards, and `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC. |
| `1.0.17` | 2026-11-23 | Lane 2 Author (`ATT-9600`, `--as agent1`) | `ATT-9600` | Promoted `TST-FIL-042` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`160/160` PASS across 13 targeted suites: `AttFusedTimestampNormalizerTest` `3/3`, `SpeedAndLocationDevice_ATTFusedTest` `42/42`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` `3/3`, `PhoneCarriageClassifierTest` `16/16`, `UnicycleOdometryEkfTest` `21/21`, `AttFusedColdStartHelperTest` `3/3`, `KinematicSportTypeDiscriminatorTest` `6/6`, `SpeedAndLocationDeviceStationaryJitterTest` `15/15`, `SpeedAndLocationDeviceDecayTest` `14/14`, `SpeedAndLocationDeviceIndoorAndRestCouplingTest` `11/11`, `OfflineTelemetryReplayHarnessTest` `11/11`, `OfflineReplayCliTest` `4/4`, `ArchitectureConformanceContractTest` `11/11`). |
| `1.0.18` | 2026-11-23 | Lane 1 Author (`ATT-8132`, `--as agent1`) | `ATT-8132` | Added `[Planned]` `TST-CON-056` (`.1`–`.4`, strictly symmetric `REQ-CON-056`) covering canonical 2-key radar-light migration & SSoT, zero stale caches in `RadarLightController` & `TrackerService`, 3-`StateFlow` `RadarStateRepository` split without 1-Hz snooze wakeups, and multi-threaded `ThreadMXBean` AB-BA deadlock stress verification (`N/A - no UI strings`). |
| `1.0.19` | 2026-11-23 | Lane 1 Author (`ATT-8132`, `--as agent1`) | `ATT-8132` | Promoted `TST-CON-056` (`.1`–`.4`) to `[Verified]` with verbatim method citations (`569/569` in-scope tests PASS across 18 suites incl. `RadarBrakeLightConcurrencyStressTest 5/5`, `TuningPreferencesDataStoreRadarLightControlModeTest 10/10`, `RadarLightControllerTest`, `RadarSnoozeControllerTest`, `RadarStateRepositoryTest`, `RadarTrafficSimulatorTest`, `ANTBikeRadarDeviceTest`, `BTLERadarDeviceTest`, `ArchitectureConformanceContractTest`, live `emulator-5554` smoke `0` crashes, `Rule 8.5` PNG verified). |
| `1.0.20` | 2026-11-23 | Lane 2 Author (`ATT-8991`, `--as agent1`) | `ATT-8991` | Added `[Planned]` `TST-CON-057` (`.1`–`.4`, strictly symmetric `REQ-CON-057`) covering `DeviceType.getDeviceTypeList()` HRM/BIKE_POWER updates, `HrmSensorCapabilityHelper` matrix (BLE H10 vs Verity Sense vs ANT+ H10 vs non-Polar/spoofed HRMs vs HR/HRV), `DevicesDatabaseManager.getDeviceIdAndNameLists()` & `SensorSourceDialog` Compose verification, and monolith/governance contracts (`N/A - 0 string changes`). |
| `1.0.21` | 2026-11-23 | Lane 2 Author (`ATT-8991`, `--as agent1`) | `ATT-8991` | Promoted `TST-CON-057` (`.1`–`.4`) to `[Verified]` with 8 verbatim `TestClass#testMethod` citations (`127/127` PASS across 15 suites: `HrmSensorCapabilityHelperTest 4/4`, `DeviceTypeSensorMappingTest 3/3`, `DevicesDatabaseManagerTest 17/17`, `SensorTypeHrvTest 5/5`, `MySensorManagerPriorityTest 8/8`, `AltitudeSlopePriorityTest 3/3`, `PrimaryHrSensorArbitratorTest 9/9`, `SensorSourceDialogPriorityTest 7/7`, `SensorSourceDialogContractTest 13/13`, `EditSensorFieldViewModelTest 28/28`, 5 contract suites `30/30`; `4/4` safety linters PASS; live `emulator-5554` `CADENCE` smoke `0` crashes + `Rule 8.5` PNG verified). |
| `1.0.22` | 2026-11-23 | Lane 2 Author (`ATT-9605`, `--as agent1`) | `ATT-9605` | Added `[Planned]` `TST-CON-058` (`.1`–`.3`, strictly symmetric `REQ-CON-058`) covering `ANTBikeSpeedDevice` & `ANTBikeSpeedAndCadenceDevice` `onNewCalculatedSpeed` null early-return (initial & post-motion), direct `m/s` scaling, standstill `null` pace + `8`-tick `SPEED_THRESHOLD` decay, negative speed guard, and `ArchitectureConformanceContractTest` parity (`N/A - 0 string changes`). |
| `1.0.23` | 2026-11-23 | Lane 2 Author (`ATT-9605`, `--as agent1`) | `ATT-9605` | Promoted `TST-CON-058` (`.1`–`.3`) to `[Verified]` with `12` verbatim `TestClass#testMethod` citations (`36/36` PASS across 4 targeted suites: `ANTBikeSpeedDeviceTest 6/6`, `ANTBikeSpeedAndCadenceDeviceTest 9/9`, `ANTBikePowerDeviceTest 10/10`, `ArchitectureConformanceContractTest 11/11`; `3/3` safety linters PASS; live `emulator-5554` smoke `0` crashes + `Rule 8.5` PNG verified; SHA-256 evidence bundle `docs/engineering/test_evidence/tickets/ATT-9605/`). |
| `1.0.24` | 2026-11-24 | Lane 2 Author (`ATT-9469`, `--as agent1`) | `ATT-9469` | Added `[Planned]` `TST-FIL-043` (`.1`–`.3`, strictly symmetric `REQ-FIL-043`) covering Bank-stop departure continuous non-stationary dwell & `<= 1 s` fast exit, barometer-free/wheel-free/wheel-active/rigid-handlebar portability, indoor `acc > 20 m` multipath & stagnant-position spike immunity, lagged-timestamp monotonic guard, and `PhoneCarriageClassifier.kt <= 1,500` LoC (`N/A - 0 string changes`). |
| `1.0.25` | 2026-11-24 | Lane 2 Author (`ATT-9469`, `--as agent1`) | `ATT-9469` | Gate 3 (`ATT-9674`) revision on `[Planned]` `TST-FIL-043`: expanded verification to cover invalid `accuracyMeters` (`Double.NaN`, `±Infinity`, `0.0`, `-1.0`) vs omitted `null` compatibility, empty/stale `positionSamples` behavior, non-poisoning of `isPositionStagnant(nowMs)` by coarse/invalid coordinate jumps, `UNKNOWN`-gap `nonStationarySinceMs` reset, and post-exit `candidateMode`/`candidateSinceMs` sync. |
| `1.0.26` | 2026-11-24 | Lane 2 Author (`ATT-9469`, `--as agent1`) | `ATT-9469` | Promoted `TST-FIL-043` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`149/149` PASS across `12` targeted suites: `PhoneCarriageClassifierTest` `19/19`, `SpeedAndLocationDevice_ATTFusedTest` `42/42`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` `3/3`, `SpeedAndLocationDeviceStationaryJitterTest` `15/15`, `SpeedAndLocationDeviceDecayTest` `14/14`, `SpeedAndLocationDeviceIndoorAndRestCouplingTest` `11/11`, `MultisportTransitionDetectorTest` `10/10`, `BANALServiceMultisportTransitionTest` `4/4`, `KinematicSportTypeDiscriminatorTest` `6/6`, `OfflineTelemetryReplayHarnessTest` `11/11`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`; `3/3` safety linters PASS; live `emulator-5554` smoke `0` crashes + `Rule 8.5` PNG verified; SHA-256 evidence bundle `docs/engineering/test_evidence/tickets/ATT-9469/`). |
| `1.0.27` | 2026-11-24 | Lane 1 Author (`ATT-9467`, `--as agent1`) | `ATT-9467` | Added `[Planned]` `TST-FIL-044` (`.1`–`.3`, strictly symmetric `REQ-FIL-044`) covering `2026-11-21_081228` `rows 1..25` start phone-handling/bike-pushing & `rows 310..340` `Metzger -> Bank` zero-stride roll replay, cross-device `T2` slow `2.0 m/s` jogger + handheld glance preservation, `MultisportTransitionDetector` `RUN` establishment & `BIKE -> RUN` kinematic/corroboration guardrails, and `<= 1,500` LoC / contract parity (`N/A - 0 string changes`). |
| `1.0.28` | 2026-11-24 | Lane 1 Author (`ATT-9467`, `--as agent1`) | `ATT-9467` | Gate 3 (`ATT-9678`) revision on `[Planned]` `TST-FIL-044`: added fresh/`reset()`/`Long.MIN_VALUE` underflow assertions for `hasActiveRunFootstrike()`, Phase 1/2 `60 s + 5 s` corroboration-dilution immunity tests, Phase 2 `15 s` slow-start -> `15 s` `2.0 m/s` `T2` recovery test verifying qualifying-head `enterAnchor` & intact `exitAnchor`, zero-duration `windowActiveSec == 0.0` guard, and `@JvmStatic @JvmOverloads` 5/6-arg bridge verification. |
| `1.0.29` | 2026-11-24 | Lane 1 Author (`ATT-9467`, `--as agent1`) | `ATT-9467` | Promoted `TST-FIL-044` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`119/119` PASS across `10` targeted suites: `PhoneCarriageClassifierTest` `21/21`, `MultisportTransitionDetectorTest` `11/11`, `SpeedAndLocationDevice_ATTFusedTest` `42/42`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` `3/3`, `BANALServiceMultisportTransitionTest` `4/4`, `MultisportLapCoordinatorTest` `7/7`, `KinematicSportTypeDiscriminatorTest` `6/6`, `OfflineTelemetryReplayHarnessTest` `11/11`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`; SHA-256 evidence bundle `docs/engineering/test_evidence/tickets/ATT-9467/`). |
| `1.0.30` | 2026-11-24 | Lane 1 Author (`ATT-9242`, `--as agent1`) | `ATT-9242` | Added `[Planned]` `TST-FIL-045` (`.1`–`.3`, strictly symmetric `REQ-FIL-045`) covering `Zum Bäcker #59` `134 s` auto-pause & `4 s`/`17 s`/`>10 min` multi-pause SQLite/`Laps.db` parity, `reconstructTimingWithPauses()` beat/stall/null immunity, segment-independent odometry & altitude/slope ERTS decoupling, `Long` overflow guards, and aligned `TST-FIL-027.1` assertions (`N/A - 0 string changes`). |
| `1.0.31` | 2026-11-24 | Lane 1 Author (`ATT-9242`, `--as agent1`) | `ATT-9242` | Gate 3 (`ATT-9679`) revision on `[Planned]` `TST-FIL-045`: added out-and-back 180° heading-reversal test (Segment 2 West with Segment 0 explicit East), repeated `4 s` auto-pause timestamp sync & `TIME_TOTAL` pause on `TIME_ACTIVE == null` row tests, trailing 1-sample altitude segment (`ascentM`/`descentM`/bias/`fallbackInitialAlt`) & DEM-only bias handoff tests, per-segment `detectAndCompensateBaroResetSteps()` isolation, and `updateExtrema()` `pauseGapMask` parity. |
| `1.0.32` | 2026-11-24 | Lane 1 Author (`ATT-9242`, `--as agent1`) | `ATT-9242` | Promoted `TST-FIL-045` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`106/106` PASS across `11` targeted suites: `ErtsPostWorkoutRecomputerTest` `22/22`, `ExtendedRtsOdometrySmootherTest` `15/15`, `ExtendedRtsAltitudeSlopeSmootherTest` `11/11`, `UnicycleOdometryEkfTest` `21/21`, `BarometricAltitudeFilterTest` `7/7`, `TrackerServiceErtsFinalizationTest` `2/2`, `LapsDatabaseManagerTest` `11/11`, `WorkoutDataMapperLapsTest` `1/1`, `AdvancedTuningErtsContractTest` `2/2`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`; SHA-256 evidence bundle `docs/engineering/test_evidence/tickets/ATT-9242/`). |
| `1.0.33` | 2026-11-24 | Lane 1 Author (`ATT-9320`, `--as agent1`) | `ATT-9320` | Added `[Planned]` `TST-SEN-035` (`.1`–`.3`, strictly symmetric `REQ-SEN-035`) covering `EditEquipmentDialog` `WheelPositionSelectionDialog` prompting, conflict swap & unlink, visible position summary/row badges, non-wheel sensor passthrough, atomic `addEquipment`/`updateEquipment` SQLite & ViewModel persistence, and live Light/Dark `Rule 8.5` emulator verification (`N/A - 0 string changes`). |
| `1.0.34` | 2026-11-24 | Lane 1 Author (`ATT-9320`, `--as agent1`) | `ATT-9320` | Promoted `TST-SEN-035` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`64/64` PASS across `18` targeted suites: `EditEquipmentDialogWheelPositionTest` `4/4`, `EquipmentRepositoryWheelPositionTest` `2/2`, `EquipmentViewModelMatrixTest` `4/4`, `EditEquipmentDialogContractTest` `1/1`, `EquipmentViewModelRetiredTest` `4/4`, `EquipmentTabsScreenContractTest` `1/1`, `WheelPositionSelectionDialogUiTest` `4/4`, `WheelPositionDialogsTest` `4/4`, `EquipmentSensorMatrixScreenTest` `7/7`, `EquipmentSensorMatrixContractTest` `5/5`, `EquipmentSensorMatrixWheelPositionTest` `2/2`, `EditDeviceEquipmentWheelPositionTest` `5/5`, `EditDeviceViewModelTest` `1/1`, `EquipmentDbHelperMigrationTest` `3/3`, `WheelCircumferenceHistoryDbTest` `2/2`, `WheelCircumferenceHistoryChartTest` `1/1`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`; live `emulator-5554` SQLite + `4` Light/Dark `Rule 8.5` PNGs verified; SHA-256 evidence bundle `docs/engineering/test_evidence/tickets/ATT-9320/`). |
| `1.0.33` | 2026-11-24 | Lane 2 Author (`ATT-9468`, `--as agent1`) | `ATT-9468` | Added `[Planned]` `TST-TRK-096` (`.1`–`.3`, strictly symmetric `REQ-TRK-096`) covering deterministic `row 425` interleaving, torn intermediate `LAP_NR = 5`, `summaryValues.LAPS`, delayed `[exitS, enterS)` & UTC fallback samples, multi-transition + manual-lap + rapid Stop/Start session isolation + `reconcileWorkoutSamplesOnFinalization()`, multi-threaded concurrency stress, `TrackerService` wiring, and `SWE.3`/LoC contract parity (`N/A - 0 string changes`). |
| `1.0.34` | 2026-11-24 | Lane 2 Author (`ATT-9468`, `--as agent1`) | `ATT-9468` | Gate 3 (`ATT-9680`) revision on `[Planned]` `TST-TRK-096`: added decoupled `summaryValues.LAPS == 6` assertions for `t = 1299` (`row 4`) & `t = 1320` (`row 5`), dual-column `TIME_TOTAL IS NULL` + UTC `time` test case, `null`/blank short-circuit & mixed `null`+keyed window merge assertions, and retargeted `TrackerService` wiring test from plain-JVM `TrackerServiceLapTest` to Robolectric `TrackerServiceMultisportMonotonicLapTest`. |
| `1.0.35` | 2026-11-24 | Lane 2 Author (`ATT-9468`, `--as agent1`) | `ATT-9468` | Promoted `TST-TRK-096` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`108/108` PASS across `11` targeted suites: `MultisportLapCoordinatorTest` `10/10`, `TrackerServiceMultisportMonotonicLapTest` `1/1`, `TrackerServiceLapTest` `7/7`, `TrackerServiceResumeTest` `18/18`, `TrackerServiceFinalizationTest` `37/37`, `TrackerServiceIntegrationTest` `6/6`, `TrackerServiceStateMachineCharacterisationTest` `8/8`, `BANALServiceMultisportTransitionTest` `4/4`, `DetailedDesignParityContractTest` `3/3`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`; SHA-256 evidence bundle `docs/engineering/test_evidence/tickets/ATT-9468/`). |
| `1.0.36` | 2026-11-24 | Lane 2 Author (`ATT-9511`, `--as agent1`) | `ATT-9511` | Added `[Planned]` `TST-TRK-097` (`.1`–`.3`, strictly symmetric `REQ-TRK-097`) covering 9-language XML parity & cross-locale resolution matrix, newly created & legacy SQLite `TrackingViewsRepository` resolution, custom title preservation (`"Intervalle"`, `"Race Cockpit"`, `"4"`), behavioral Compose UI locale-switch rendering, and live `emulator-5554` `Rule 8.5` verification (`N/A - 0 string changes`). |
| `1.0.37` | 2026-11-24 | Lane 2 Author (`ATT-9511`, `--as agent1`) | `ATT-9511` | Promoted `TST-TRK-097` (`.1`–`.3`) to `[Verified]` with verbatim method citations (`125/125` PASS across `19` targeted suites: `TrackingTabTitleResolverTest` `4/4`, `TrackingTabLocaleSwitchRobolectricTest` `2/2`, `TrackingViewsRepositoryTest` `15/15`, `BikePowerCockpitDefaultsAndGatingTest` `6/6`, `13` `trackingtabs.*` suites `74/74`, `ProcessStabilityComposeUiTest` `10/10`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`; live `emulator-5554` `de -> es -> ja` locale-switch verification + `3` `Rule 8.5` PNGs audited; `0` crashes). |
