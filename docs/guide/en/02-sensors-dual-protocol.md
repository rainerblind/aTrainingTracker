# Chapter 2: Sensors, Devices & Dual-Protocol Hardware (BLE & ANT+)

<a id="sensor-pairing-bluetooth-ant"></a>
## 2.1 Bluetooth LE & ANT+ Dual-Protocol Pairing

aTrainingTracker features a carrier-grade multi-protocol sensor engine supporting simultaneous Bluetooth Low Energy (BLE) and native ANT+ device connectivity.

- **Supported Sensor Profiles**:
  - Heart Rate Monitors (BLE HRS & ANT+ HRM)
  - Cycling Power Meters (BLE CPS & ANT+ PWR, including Left/Right balance)
  - Speed & Cadence Sensors (BLE CSC & ANT+ BSC)
  - Running Dynamics & Footpods (BLE RSC & ANT+ Stride)
- **Scanning & Discovery**:
  Open the *Devices / Sensors* screen. The unified scanner actively searches both radio interfaces concurrently. One tap pairs the device and binds it to the corresponding measurement channel.

![Device & Sensor Pairing](../img/02_sensor_pairing.png)

---

<a id="barometric-altitude-calibration"></a>
## 2.2 Smartphone Sensors & Barometric Altitude Calibration

Raw GPS altitude typically suffers from vertical dilution of precision ($\pm 15\text{--}30\text{ m}$). If your smartphone includes an onboard barometer, aTrainingTracker unleashes sub-meter altitude accuracy:

- **Known Locations**: Configure geographical reference points (such as your home or trailheads) with surveyed elevations. Starting a workout near a Known Location instantly anchors the barometer to true sea-level altitude without baseline drift.
- **Continuous Calibration**: In the background, slow meteorological pressure changes are filtered and reconciled against heavily damped GPS altitude.

![Barometric Altitude Calibration](../img/02_altitude_calibration.png)

---

<a id="priority-and-failover"></a>
## 2.3 Sensor Priority, Failover & Battery Telemetry

When multiple telemetry sources provide the same metric (e.g. dual BLE + ANT+ broadcasting or wheel speed vs. GPS):

1. **Hardware Precedence**: Precise wheel circumference revolutions always override GPS speed, guaranteeing exact pacing through tunnels, canyons, and dense tree canopies.
2. **Seamless Failover Arbitration**: If a Bluetooth link drops temporarily, the engine immediately arbitrates to ANT+ or GPS without creating recording gaps.
3. **Live Battery Monitoring**: Real-time battery percentages or discrete health tiers (Good / Ok / Low / Critical) keep you informed before coin cells expire mid-ride.

![Dual Protocol Status & Battery Monitoring](../img/02_dual_protocol.png)

---

[Previous: Quick Start](01-quick-start.md) | [Next: Equipment & Sensor Matrix](03-equipment-sensor-matrix.md)
