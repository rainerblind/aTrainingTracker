# Stage 1 Analysis: ATT-6741 - Reconcile 13 Orphan SYSREQ IDs, Fix 10 Hallucinated Classes & Sequence Actor in System Architecture

**Ticket**: [ATT-6741](https://rainerblind.atlassian.net/browse/ATT-6741)  
**Sub-task**: [ATT-6784](https://rainerblind.atlassian.net/browse/ATT-6784) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6741` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 review of ATT-6164 (commit `7563a9c2`), an automated bidirectional traceability audit revealed critical defects in `docs/system/system_architecture.md` (ASPICE SYS.3):
1. **13 Orphan SYSREQ IDs**: Section 5 allocates 25 requirements across 5 domains, including 13 non-existent IDs (`SYSREQ-ACQ-001..005`, `SYSREQ-PER-001..005`, `SYSREQ-ENV-001..003`) not defined anywhere in SYS.2 (`sysreq_live_tracking.md`, `sysreq_data_exchange.md`, `docs/system_requirements.md`), breaking SYS.3.BP6 bidirectional consistency.
2. **10 Non-Existent Classes, Constants & XML Entities**: Architecture cites hallucinated symbols (`HeartRateData`, `PowerData`, `BatteryLevelData`, `AntPlusManager`, `RadarThreatView`, `SoundEngine`, `FusedLocationProviderClient`, `LocationCallback`, `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, `backup_rules.xml`, and process `com.atrainingtracker.trainingtracker`).
3. **Inaccurate Sequence Actor in Diagram 4.2**: Lines 156 & 163–167 depict `WorkoutDetailScreen` finalizing `WorkoutSummaries.db` and invoking `ExportManager`. In production, workout stop is triggered from `ControlTrackingScreen` / notification action and finalized asynchronously by `TrackerService` on `mDbExecutor`.
4. **Contract Test Blind Spot**: `SystemArchitectureDesignContractTest.kt` only verified `declaredSysreqs <= allocatedSysreqs`, allowing orphan IDs to pass undetected.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: In ATT-6164, the author drafted Section 5 by fragmenting live tracking into sub-domains (ACQ, PER, ENV) without reconciling against the 12 authoritative SYS.2 system requirements (`SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`). Contract tests were written to assert the 25 items, masking the discrepancy.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *SYSREQ Inventory*: Audit of `docs/system_requirements.md`, `sysreq_live_tracking.md`, and `sysreq_data_exchange.md` confirms only 12 SYSREQ IDs exist (6 TRK, 6 DEX). The 13 ACQ/PER/ENV IDs are ungrounded sub-specifications already subsumed under `SYSREQ-TRK-001..006`.
  * *Code Grounding Audit*:
    - Sensor Data: No `HeartRateData`/`PowerData`/`BatteryLevelData` classes exist; telemetry is modeled via `MySensor<Number>` / `SensorData<T>` and `SensorBatteryNotificationManager.kt`.
    - ANT+: No `AntPlusManager` exists; `BANALService.java` directly manages `MyANTDevice` subclasses communicating via ANT+ Plugin IPC (`com.dsi.ant.plugins.antplus`).
    - Radar UI & Audio: No `RadarThreatView` or `SoundEngine` exist; live radar overlay is `RadarEdgeOverlay.kt`, and audio alerts are managed by `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, and `NavigationAudioAlertManager.kt`.
    - Location: `SpeedAndLocationDevice_GoogleFused.java` uses `GoogleApiClient` + `LocationListener` (`LocationRequest`), NOT `FusedLocationProviderClient` / `LocationCallback`.
    - FGS & Backup: `TrackerService.java` strictly uses `FOREGROUND_SERVICE_TYPE_LOCATION` (+ conditional `HEALTH`), never `CONNECTED_DEVICE` (verified by `TrackerServiceFGSTypeTest`). Manifest declares `@xml/full_backup_content` (`full_backup_content.xml`), not `backup_rules.xml`.
    - Process ID: `app/build.gradle` specifies `namespace = "com.atrainingtracker"` (process is `com.atrainingtracker` or `com.atrainingtracker.debug`), not `com.atrainingtracker.trainingtracker`.
  * *Sequence Actor Audit*: `ControlTrackingViewModel.onStopTracking()` emits `REQUEST_STOP_TRACKING`; `TrackerService.handleActionStop()` executes `finalizationTask` on `mDbExecutor`, invoking `endWorkout()` to persist summary data and trigger `ExportManager`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Reconcile Section 5 of `docs/system/system_architecture.md` into 2 domains (`SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`), eliminating all 13 orphan IDs.
  * Correct all 10 non-existent symbols and process ID across `system_architecture.md`, `sysreq_live_tracking.md`, `sysreq_data_exchange.md`, and `docs/system_requirements.md`.
  * Update Diagram 4.2 to depict `ControlTrackingScreen` / notification stop delegating to `TrackerService` (`mDbExecutor`).
  * Enhance `SystemArchitectureDesignContractTest.kt` to enforce exact bidirectional set equality (`declaredSysreqs == allocatedSysreqs`) and dynamic reflection checks (`Class.forName` / `File.exists`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Upgrading `SpeedAndLocationDevice_GoogleFused` to modern Google Play Services location APIs.
  * Modifying production runtime logic or SQLite schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-056` (`REQ-PRO-056.1`, `REQ-PRO-056.2`), verified by `TST-PRO-056` (`TST-PRO-056.1`–`TST-PRO-056.4`).
* **Historical Origin & Commit Trace**: Commit `7563a9c2` (`ATT-6164`), closing ASPICE pre-assessment finding S-05.
* **Root Reason for Existing Formulation**: Rapid authoring of system architecture without strict reverse-set validation in contract tests led to hallucinated class names and duplicate SYSREQ IDs.
* **Preservation of Core Invariants**: 5 System Elements (`SYS-EL-01..05`) and 6 System Interfaces (`SYS-IF-01..06`) remain 100% intact; 100% of authentic SYS.2 requirements remain allocated.

---

## 5. Architectural Strategy & High-Level Solution
* **Section 5 Restructuring**: Group allocation matrix into Domain 1 (Tracking Loop `SYSREQ-TRK-001..006`) and Domain 2 (Data Exchange `SYSREQ-DEX-001..006`). Update document prose to reflect exact 12 system requirements.
* **Diagram 4.2 Sequence Fix**: Model athlete tapping Stop in `ControlTrackingScreen` -> `TrackerService.handleActionStop()` -> `mDbExecutor` executing `endWorkout()` (persisting summary, invoking `ExportManager`) -> cloud sync.
* **Contract Test Hardening**: Replace unilateral subset check with `assertEquals(declaredSysreqs, allocatedSysreqs)` and assert existence of authentic classes/files.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% pass rate on `testDebugUnitTest` and all ASPICE contract tests.
  2. Exact bidirectional equality `declaredSysreqs == allocatedSysreqs` across SYS.2 and SYS.3.
  3. SYS.3 decomposition into `SYS-EL-01..05` and `SYS-IF-01..06` preserved without modification.
* **Risk Rating**: **LOW** (Bounded 2 SP documentation and contract test alignment; zero runtime code changes).
