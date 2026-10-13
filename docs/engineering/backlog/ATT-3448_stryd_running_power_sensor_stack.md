# [Feature] [Sensors] Support Stryd and Bluetooth Running Power Service in sensor stack

**Ticket**: [ATT-3448](https://rainerblind.atlassian.net/browse/ATT-3448)  
**Parent Epic**: [ATT-2561](https://rainerblind.atlassian.net/browse/ATT-2561) (*Sensors: BLE / ANT+ Connectivity, Calibration & Telemetry*)  
**Status**: Backlog  

---

## Problem Statement & Value Proposition
Runners utilizing power-based training with running power sensors (such as Stryd footpods supporting the Bluetooth SIG Running Power standard) currently cannot connect their devices for running activities in aTrainingTracker:
1. Sensor Filtering: In `DevicesDatabaseManager`, power sensors are hardcoded exclusively to cycling (`BSportType.BIKE` / `BIKE_POWER`). When setting up running equipment or selecting sensors for a running activity (`BSportType.RUN`), power meters are completely filtered out.
2. Architecture & Service Constraint: Running power must strictly follow the official Bluetooth SIG **Running Power Service** (UUID `0x1834`, with Running Power Measurement characteristic `0x2B84`). Workarounds using the Cycling Power Service (`0x1818`) are strictly prohibited to ensure domain model cleanliness and standard compliance.
3. Multi-Service BLE Support: Running footpods (like Stryd) broadcast live power via the standard Running Power Service (`0x1834`) while simultaneously providing pace, distance, and cadence via the Running Speed and Cadence Service (`0x1814`, characteristic `0x2A53`). Currently, `BluetoothConstants` and `MyBTLEDevice` bind device types 1:1 to a single GATT service, preventing unified ingestion of power, cadence, and pace from a single physical peripheral MAC address.
4. Telemetry Ingestion: The running sensor pipeline must ingest instantaneous power in Watts, compute running cadence (SPM), and provide configurable precedence between footpod pace/distance and phone GPS pace/distance.

## User Story
* **As a**: runner training with a running power meter (e.g. Stryd)
* **I want**: to discover, pair, and connect my sensor strictly via the standard Bluetooth SIG Running Power Service (`0x1834`)
* **So that**: my instantaneous running power (Watts), cadence, and stride metrics are captured cleanly and recorded alongside heart rate and GPS data.

## Scope & Technical Architecture
1. **Sensor Classification & Domain Model**:
   * Introduce `DeviceType.RUN_POWER` in `DeviceType.java` mapped to `BSportType.RUN`.
   * Update `DevicesDatabaseManager.isRunSensor()`, `isBikeSensor()`, and `getSensorsForSportType(BSportType.RUN)` to recognize running power devices.
2. **Bluetooth LE Running Power Service Subscription (`BTLERunPowerDevice`)**:
   * Implement dedicated `BTLERunPowerDevice` supporting the official Bluetooth SIG Running Power Service (UUID `00001834-0000-1000-8000-00805f9b34fb`).
   * Subscribe to Running Power Measurement characteristic (`00002b84-0000-1000-8000-00805f9b34fb`).
   * *Strict Invariant*: Do NOT subscribe to or ingest from Cycling Power Service (`0x1818`).
   * Optionally subscribe to the secondary Running Speed & Cadence Service (`0x1814`) on the same peripheral if exposed for stride/speed metrics.
   * Parse instantaneous power (UINT16 Watts) and running cadence.
3. **Telemetry Invariants & Precedence**:
   * Register `SensorType.POWER` and `SensorType.CADENCE` under the active running session.
   * Provide user-configurable telemetry preference: Footpod Pace & Distance priority vs. GPS Pace & Distance priority (with GPS track coordinate logging always preserved for mapping).

## Acceptance Criteria (Given-When-Then)
* **Criterion 1: Strict Running Power Service Gating**:
  * *Given* a sensor scanning for running power devices
  * *When* evaluating available GATT services on a discovered peripheral
  * *Then* the app connects strictly via the Bluetooth SIG Running Power Service (`0x1834`) and rejects Cycling Power Service (`0x1818`) channels for running profiles.
* **Criterion 2: Running Power Discovery & Pairing**:
  * *Given* a runner searching for sensors under Running sport settings
  * *When* scanning for nearby Bluetooth LE devices with an active Stryd footpod emitting the Running Power Service
  * *Then* the device is detected and displayed as a compatible running power sensor.
* **Criterion 3: Live Power and Cadence Ingestion**:
  * *Given* a paired running power sensor connected during an active running workout
  * *When* the runner begins moving and generating power
  * *Then* the BANAL sensor pipeline ingests and emits live `SensorType.POWER` (Watts) and `SensorType.CADENCE` (SPM) at 1 Hz without dropped packets.
* **Criterion 4: Pace & Distance Precedence**:
  * *Given* an active running session with both phone GPS and running footpod connected
  * *When* footpod pace precedence is enabled
  * *Then* cockpit pace and workout distance are derived from footpod telemetry, while geographic coordinates and route polyline continue to be recorded from GPS.
* **Criterion 5: Disconnection & Reconnection Resilience**:
  * *Given* an active running workout with a connected running power sensor
  * *When* the BLE connection drops temporarily due to radio interference or range
  * *Then* the sensor manager handles reconnection automatically without freezing the workout or crashing the background tracking service.

## Parent Epic & Sprint Placement
* **Target Epic**: `ATT-2561` (Sensors: BLE / ANT+ Connectivity, Calibration & Telemetry)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
