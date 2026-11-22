# ADR-007: Unified BLE & ANT+ Hardware Sensor Abstraction (`MyDevice` / `MyRemoteDevice`)

- **Document ID**: `DOC-ARC-ADR007`
- **Version**: 1.0.0
- **Status**: Approved
- **Approver**: Rainer Blind (PO)
- **Deciders**: Architecture Board, Product Owner (Pending Sign-Off)
- **Date**: 2026-11-17
- **Target Release**: `5.1.0` (Active Sprint: `2026-11-17_01_Früh`)
- **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
- **Ticket**: [ATT-8161](https://issue-tracker/ATT-8161)
- **Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1`, `REQ-PRO-116.2`), `REQ-CON-001`, `REQ-CON-023`, `REQ-SET-108`
- **Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1`, `TST-PRO-116.2`), `MyBTLEDeviceLifecycleTest`, `MyBTLEDeviceQueueTest`, `HardwareHalIngestionIntegrationTest`

---

## 1. Context & Problem Statement

`aTrainingTracker` ingests real-time athletic telemetry from heterogeneous protocols: Bluetooth Low Energy GATT profiles (HR `0x180D`, Cycling Power `0x1818`, CSC `0x1816`, RSC `0x1814`, Garmin Varia Radar), Polar BLE SDK PMD high-rate streams (`PolarOnlineStreamingBridge`), ANT+ wireless peripherals via the pinned `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`, `AntPluginPcc`), and internal smartphone sensors (GPS/Fused Location, Barometer). Upstream `TrackerService`, `FilterManager`, and Compose cockpit tiles require a single protocol-agnostic `SensorType` bus.

## 2. Decision Drivers & Evaluated Alternatives

Key selection criteria: (1) protocol-agnostic sensor arbitration and `< 3.5 s` failover (`ProxySensor`, `REQ-NFR-008`), (2) serialized BLE GATT queue integrity avoiding Android `BluetoothGatt` command drops (`MyBTLEDevice.mGattQueue`), (3) strict preservation of pinned `ANT-Android-SDKs` binary/IPC semantics, and (4) extensibility for Polar PMD live IMU streaming without rewriting `TrackerService`.

### Alternative 1: Protocol-Specific Pipelines in `TrackerService` and Cockpit UI
- **Description**: Let `TrackerService` and UI ViewModels subscribe separately to `BluetoothGattCallback`, `AntPluginPcc`, and `LocationListener` adapters per protocol.
- **Evaluation**: **Rejected**. Duplicating failover, smoothing, accumulator reset (`RESET_ACCUMULATORS_INTENT`), and calibration logic across BLE, ANT+, and smartphone providers would fracture `TrackerService` and break cross-protocol sensor substitution (e.g., BLE HR failing over to ANT+ HR).

### Alternative 2: Direct Kotlin Coroutine Rewrite of All Peripheral Drivers
- **Description**: Replace `MyANTDevice` and `MyBTLEDevice` subclasses simultaneously with a new coroutine-based BLE/ANT+ HAL.
- **Evaluation**: **Rejected**. Explicitly rejected by PO decision (`ATT-8115`, `ADR-001` Tiers 3 & 5) because `MyBTLEDevice` (`mGattQueue`, `MAX_SCANS_IN_WINDOW`, Random Static `getRemoteLeDevice` handling) and `MyANTDevice` encode device-specific RF quirk mitigations validated over years of field sessions.

### Alternative 3: Unified `MyDevice` -> `MyRemoteDevice` Hierarchy + `DeviceManager` + `ProxySensor` Abstraction (Selected)
- **Description**: Model all sensor providers under `MyDevice` (registering `MySensor` instances per `SensorType` into `MySensorManager`), specializing remote RF peripherals via `MyRemoteDevice` into `MyANTDevice` (wrapping `ANT-Android-SDKs` `AntPluginPcc`) and `MyBTLEDevice` (serializing `BluetoothGatt` operations in `mGattQueue` with `mGattLock`, `docs/architecture/detailed_design/my_btle_device.md`), plus `PolarOnlineStreamingBridge` extending `MyDevice` for Polar PMD live IMU/RR streaming. `DeviceManager` orchestrates sequential/active discovery and `ProxySensor` arbitrates competing sources uniformly by `SensorType`.
- **Evaluation**: **Selected**. Isolates RF protocol quirks inside leaf drivers while keeping `TrackerService`, `FilterManager`, SQLite persistence, and Compose UI 100% protocol-agnostic.

## 3. Decision Outcome

All hardware peripherals MUST register metrics into `MySensorManager` through `MyDevice` / `MyRemoteDevice` (`MyANTDevice`, `MyBTLEDevice`, `PolarOnlineStreamingBridge`) so `ProxySensor` and `FilterManager` remain the sole unified telemetry bus (`IF-SWE-03`, `IF-SWE-04`).

## 4. Architectural Consequences & Trade-offs

### Positive Consequences
- **Seamless Multi-Protocol Failover**: `ProxySensor` switches transparently between BLE, ANT+, and smartphone sources on packet timeout (`3.5 s`).
- **RF Driver Stability**: `MyBTLEDevice` (`mGattQueue`) and pinned `ANT-Android-SDKs` (`0e8cea71`) remain isolated from UI/business churn.

### Negative / Mitigated Consequences
- **Legacy Java Inheritance Tree**: `MyDevice` / `MyRemoteDevice` remain in Java per `ADR-001`; mitigated by unit/characterisation coverage (`MyBTLEDeviceLifecycleTest`, `MyBTLEDeviceQueueTest`, `HardwareHalIngestionIntegrationTest`) and `detailed_design/my_btle_device.md`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8161`, `ATT-8164`) | `ATT-8161`, `ATT-8164` | Retrospective ADR baseline with standardized `DOC-ARC-ADR007` control header. |
