# ADR-003: Sensor Ingestion Bus Architecture (`BANALService` & `BANALServiceRepository`)

- **Document ID**: `DOC-ARC-ADR003`
- **Version**: 1.0.0
- **Status**: Approved
- **Approver**: Rainer Blind (PO)
- **Deciders**: Architecture Board, Product Owner (Pending Sign-Off)
- **Date**: 2026-11-17
- **Target Release**: `5.1.0` (Active Sprint: `2026-11-17_01_Früh`)
- **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
- **Ticket**: [ATT-8161](https://issue-tracker/ATT-8161)
- **Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1`, `REQ-PRO-116.2`), `REQ-PRO-044`, `REQ-NFR-007`, `REQ-NFR-008`
- **Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1`, `TST-PRO-116.2`), `BANALServiceArbitrationCharacterisationTest`, `BANALServiceLifecycleTest`

---

## 1. Context & Problem Statement

Hardware sensors (ANT+, Bluetooth LE, GPS, Google Fused Location, Barometer) deliver asynchronous telemetry packets at heterogeneous rates (`1–200 Hz`). Both `TrackerService` (1-Hz SQLite recording) and Jetpack Compose ViewModels (`TrackingViewModel`, `DeviceListViewModel`) must consume priority-arbitrated, smoothed sensor data (`FilteredSensorData`, `SensorData`) without tight coupling to hardware drivers or UI lifecycles.

## 2. Decision Drivers & Evaluated Alternatives

Key selection criteria: (1) `< 25 ms` sensor dispatch latency (`REQ-NFR-007`), (2) `<= 3.5 s` failover between competing sources (`REQ-NFR-008`), (3) zero Android `BroadcastReceiver` / Context leaks across Compose UI recompositions, and (4) clean boundary separation (`IF-SWE-01`, `IF-SWE-02`, `IF-SWE-03`).

### Alternative 1: Global Static Singleton EventBus / Unbounded Callback Lists
- **Description**: Expose hardware streams via a static singleton pub-sub event bus directly subscribed by Fragments, Composables, and `TrackerService`.
- **Evaluation**: **Rejected**. Static callback registries leak destroyed UI contexts across configuration changes, lack Android Service lifecycle guarantees during background tracking, and scatter sensor priority/filtering rules across consumers.

### Alternative 2: Separate Out-of-Process Service with AIDL IPC
- **Description**: Isolate `BANALService` into a separate Android `:sensor` OS process communicating with `TrackerService` and UI via `Parcelable` AIDL transactions.
- **Evaluation**: **Rejected**. Marshaling high-frequency `FilteredSensorData` snapshots and `SensorType` maps across process boundaries every second introduces Binder serialization overhead, memory copies, and split-process lifecycle race conditions.

### Alternative 3: In-Process `BANALService` Local Binder (`BANALServiceComm`) + Kotlin `BANALServiceRepository` `StateFlow` Bridge (Selected)
- **Description**: Run `BANALService` as an in-process foreground service (`location|health`) managing `MySensorManager`, `ProxySensor`, `DeviceManager`, and `FilterManager`. Expose `BANALServiceComm` (Local Binder) for low-latency direct polling by `TrackerService` (`getAllFilteredSensorData()`, `getBestSensorData(SensorType)`), broadcast discrete lifecycle/lap intents (`RESET_ACCUMULATORS_INTENT`, `SEARCHING_STARTED_FOR_ALL_INTENT`), and bridge live telemetry to Jetpack Compose ViewModels via `BANALServiceRepository` (`allFilteredSensorData: StateFlow<List<FilteredSensorData<*>>>`).
- **Evaluation**: **Selected**. Delivers zero-copy in-process Binder performance for `TrackerService` while keeping Compose UI strictly decoupled from Android `Service` bindings (`IF-SWE-01` / `IF-SWE-02`).

## 3. Decision Outcome

`BANALService` remains the centralized in-process sensor hub (`docs/architecture/detailed_design/banal_service.md`), serving `TrackerService` through `BANALServiceComm` (`IF-SWE-03`) and serving Compose ViewModels exclusively through `BANALServiceRepository` (`IF-SWE-02`).

## 4. Architectural Consequences & Trade-offs

### Positive Consequences
- **Zero-Copy Telemetry Sharing**: `TrackerService` polls `BANALServiceComm` directly in-process without AIDL marshaling overhead.
- **UI Lifecycle Safety**: Compose screens observe conflated `StateFlow` streams in `BANALServiceRepository` with zero direct `ServiceConnection` management.
- **Centralized Arbitration**: `ProxySensor` and `MySensorManager` enforce deterministic source failover across ANT+, BLE, and internal sensors.

### Negative / Mitigated Consequences
- **Static Context Holders in Legacy Java Core**: `BANALService` retains static manager fields (`cServiceContext`, `cSensorManager`, `cDeviceManager`, `cFilterManager`); mitigated by explicit null guards (`if (mBanalComm != null)`) and `BANALServiceArbitrationCharacterisationTest`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8161`, `ATT-8164`) | `ATT-8161`, `ATT-8164` | Retrospective ADR baseline with standardized `DOC-ARC-ADR003` control header. |
