# Stage 1 Analysis: ATT-6160 - Specify system requirements for live tracking with sensors

**Ticket**: [ATT-6160](https://rainerblind.atlassian.net/browse/ATT-6160)  
**Sub-task**: [ATT-6630](https://rainerblind.atlassian.net/browse/ATT-6630) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6160`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 pre-assessment finding S-02 (SYS.2 BP1-BP3) identified that aTrainingTracker lacks system-level requirements (`SYSREQ-*`) specifying the end-to-end behavior of the mobile device with external wireless sensors and GNSS. While software-level requirements exist across domain shards (`docs/requirements/`), end-to-end system capabilities (multi-sensor acquisition, exponential backoff reconnection, 1-Hz deterministic sampling, GNSS positioning, foreground execution, power governance, and real-time sensory alerts) are not formally specified independent of internal software design. Authoring `docs/system/sysreq_live_tracking.md` fulfills ASPICE SYS.2 BP1–BP3.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing System-Level Specification Baseline (SYS.2 BP1-BP3 Gap)**:
   - System behavior was historically captured only in software implementation classes (`TrackerService.java`, `BANALService.java`, `MyBTLEDevice.java`) without black-box system requirements defining functional, timing, and environmental bounds.
2. **Independent Peer Review & Code-Grounding Verification (ATT-5431)**:
   - Audited `TrackerService.java`: Confirmed 1-Hz deterministic sampling loop via `ScheduledExecutorService` (`scheduleAtFixedRate`), single-threaded SQLite persistence via `mDbExecutor`, partial WakeLock acquisition (`PowerManager.PARTIAL_WAKE_LOCK`), and dynamic FGS type derivation (`determineForegroundServiceType()` combining `LOCATION` and conditionally `HEALTH` on Android 14+).
   - Audited `BANALService.java` & `MyBTLEDevice.java`: Confirmed active BLE GATT profiles (HRM `0x180D`, Power `0x1818`, CSC `0x1816`, RSC `0x1814`, Radar `6a4e3200...`, Battery `0x180F`), exponential backoff reconnection (`INITIAL_RECONNECT_DELAY_MS = 1000L`, `MAX_RECONNECT_DELAY_MS = 16000L`, doubling up to 16 s), 10 s search period, and 5-minute battery polling.
   - Audited `BTLERadarDevice.java`: Confirmed 3.5 s packet starvation watchdog (`WATCHDOG_TIMEOUT_MS = 3500L`) triggering fail-safe `DISCONNECTED_ERROR`.
   - Audited `SpeedAndLocationDevice_GPS.java` & `SpeedAndLocationDevice_GoogleFused.java`: Confirmed dual GNSS acquisition pipelines (hardware GPS provider and Google Play Fused Location client).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Author authoritative system requirements document `docs/system/sysreq_live_tracking.md` defining formal `SYSREQ-TRK-*` requirements covering:
    (1) Multi-sensor acquisition (BLE GATT & ANT+ profiles),
    (2) Automatic reconnection and backoff resilience,
    (3) Deterministic 1-Hz sampling and time-series recording,
    (4) GNSS location acquisition and accuracy,
    (5) Foreground service execution, partial WakeLock, and power governance,
    (6) Real-time sensory alerts and cockpit feedback.
  - Structure each `SYSREQ` with: ID, Title, Requirement Statement, Rationale, Priority, Verification Method, and Traced Software Components.
  - Define living requirement `REQ-PRO-054` in `docs/requirements/req_process_stability.md` and test spec `TST-PRO-054` in `docs/tests/tst_process_stability.md`.
  - Author automated contract test `SystemLiveTrackingRequirementsContractTest.kt` under `app/src/test/.../build/` enforcing structural integrity and complete code grounding.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying live tracking runtime code or Bluetooth/ANT+ protocol implementations in `app/src/main/`.
  - Altering database schema or SQLite table layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-054` (*System Requirements for Live Tracking with Sensors*), establishing ASPICE SYS.2 BP1–BP3 baseline under Parent Epic ATT-6142.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-02 (2026-11-08) and ticket ATT-6160.
* **Root Reason for Existing Formulation**: Project grew as an Android application without a dedicated systems engineering discipline, leaving system-level capabilities undocumented.
* **Preservation of Core Invariants**: Zero runtime code changes; 1-Hz sampling time-base, SQLite persistence, and battery governance remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
* `docs/system/sysreq_live_tracking.md`: Specify complete system requirements set:
  - `SYSREQ-TRK-001`: Multi-Sensor Acquisition & Profile Interoperability (BLE GATT + ANT+).
  - `SYSREQ-TRK-002`: Automatic Reconnection & Exponential Backoff Resilience.
  - `SYSREQ-TRK-003`: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording.
  - `SYSREQ-TRK-004`: Multi-Source GNSS Acquisition & Position Telemetry.
  - `SYSREQ-TRK-005`: Background Foreground Service Execution & Power Management.
  - `SYSREQ-TRK-006`: Real-Time Sensory Alerts & Cockpit Feedback.
* `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`: JUnit contract test asserting `docs/system/sysreq_live_tracking.md` exists, contains all 6 `SYSREQ-TRK-*` requirements with required attributes, and asserts 100% code grounding against production constants.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero modifications to production code in `app/src/main/`; 100% unit test pass rate; living documentation index parity; Parent ticket Human Decision Gate preserved.
* **Risk Rating**: **LOW** (3 SP, documentation and contract testing baseline without runtime code impacts).
