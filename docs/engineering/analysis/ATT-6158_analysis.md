# Stage 1 Analysis: ATT-6158 - Define system context and external system elements

**Ticket**: [ATT-6158](https://rainerblind.atlassian.net/browse/ATT-6158)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6158`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 pre-assessment finding S-01 (SYS.2 BP1, SYS.3) identified that aTrainingTracker currently lacks a defined system level and system context boundary. The application interacts extensively with external entities (BLE and ANT+ peripherals, GNSS hardware, Android OS platform services, Google Play Services, Google Maps, Strava, Dropbox, Google Drive, Health Connect, and PebbleKit), but these external elements, interfaces, protocols, and operational assumptions are undocumented. Establishing a formal system context (`docs/system/system_context.md`) delineates the system boundary and grounds all external elements in live source code.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing System Level Documentation (SYS.2/SYS.3 Gap)**:
   - Historical documentation focused strictly on software-level components (`docs/architecture.md`), skipping the system context architecture required by ASPICE CL2.
   - External dependencies and protocol bindings were implicitly defined across build scripts and source files rather than in an authoritative system boundary specification.
2. **Independent Peer Review & Code-Grounding Verification (ATT-5431)**:
   - Audited `AndroidManifest.xml`: Verified explicit system boundaries including GNSS (`android.hardware.location.gps`), ANT+ IPC services (`com.dsi.ant.plugins.antplus`, `com.dsi.ant.service.socket`), Health Connect (`com.google.android.apps.healthdata`), and Google Play Services (`com.google.android.gms`).
   - Audited `BluetoothConstants.java`: Verified active BLE GATT profiles (HRM `0x180D`, Cycling Speed/Cadence `0x1816`, Power `0x1818`, Running `0x1814`, Battery `0x180F`, Garmin Varia Radar `6a4e3200...`).
   - Audited `app/build.gradle` & Cloud Uploaders: Confirmed active cloud providers (Strava OAuth2 API, Dropbox Core SDK 8.0.2, Google Drive REST API v3, Health Connect 1.1.0-alpha11, PebbleKit 4.0.1).
   - Audited Decommissioned Services: Verified Runkeeper and TrainingPeaks APIs are permanently decommissioned and ignored in secrets build configuration (`ignoreList.add('^RUNKEEPER.*')`), retaining database columns strictly for legacy schema stability.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Author authoritative system context document at `docs/system/system_context.md` containing a Mermaid context diagram, comprehensive external elements inventory, interface protocols, and operational assumptions (supported Android OS versions: minSdk 26, targetSdk 37; offline-first telemetry caching).
  - Define living requirement `REQ-PRO-053` and test spec `TST-PRO-053` in `req_process_stability.md` and `tst_process_stability.md`.
  - Author automated contract test `SystemContextContractTest.kt` under `app/src/test/.../build/` enforcing structural integrity and complete code-grounding of `docs/system/system_context.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Introducing new cloud or sensor hardware integrations.
  - Modifying Android production runtime code in `app/src/main/` or database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-053` (*System Context and External Interfaces Specification*), establishing ASPICE SYS.2/SYS.3 baseline under Parent Epic ATT-6142.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-01 (2026-11-08) and ticket ATT-6158.
* **Root Reason for Existing Formulation**: Project grew as an Android application without a formal systems engineering process, leaving external interface contracts implicit in code.
* **Preservation of Core Invariants**: Zero runtime code changes; offline-first storage and existing network upload logic remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
* `docs/system/system_context.md`: Establish comprehensive system specification including:
  - System boundary diagram (Mermaid) separating athlete, mobile application, sensors, OS platform, and remote clouds.
  - External elements catalog classified by domain (Sensors & Peripherals, Platform Frameworks, Location & Maps, Cloud & Storage, Health Aggregators).
  - Explicit demarcation between active external elements and decommissioned legacy platforms (Runkeeper, TrainingPeaks).
* `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemContextContractTest.kt`: Enforce that `docs/system/system_context.md` exists, defines the context diagram, and covers all required external elements with valid source code grounding.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero production runtime code changes; living documentation 100% parity; 100% test pass rate.
* **Risk Rating**: **LOW** (2 SP, documentation and contract testing baseline without runtime code impacts).
