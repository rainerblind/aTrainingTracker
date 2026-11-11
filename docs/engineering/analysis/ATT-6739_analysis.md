# Stage 1 Analysis: ATT-6739 - Replace hallucinated radar/audio classes and enforce file existence in sysreq_live_tracking.md

**Ticket**: [ATT-6739](https://rainerblind.atlassian.net/browse/ATT-6739)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6739`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.1 review of ATT-6160, an automated source-grounding audit caught several non-existent/stale class and test references in `docs/system/sysreq_live_tracking.md`:
1. Hallucinated radar/audio classes in `SYSREQ-TRK-006`: `RadarThreatView` / `RadarThreatView.kt`, `SoundEngine` / `SoundEngine.java`, and `BikeBellAudioSynthesizer.java` (actual components are `RadarEdgeOverlay.kt`, `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `NavigationAudioAlertManager.kt`, and `BikeBellAudioSynthesizer.kt`).
2. Stale test and map helper in `SYSREQ-TRK-001` & `SYSREQ-TRK-004`: `BANALServiceTest` (real: `BANALServiceLifecycleTest.kt`, `BANALServiceRepositoryTest.kt`) and `MapViewportHelper.kt` (real: `MapDetailViewportMath.kt`).
3. Location client and FGS contradiction: `FusedLocationProviderClient` (`SpeedAndLocationDevice_GoogleFused` uses `LocationServices.FusedLocationApi` / `GoogleApiClient` + `LocationListener`) and `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` in line 78 (real: `location|health` only).
4. Contract test gap: `SystemLiveTrackingRequirementsContractTest.kt` does not dynamically assert `File.exists()` on cited backticked `.kt`/`.java` files.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Live Grounding Verification**:
  * *Radar & Audio*: Verified that `RadarThreatView` and `SoundEngine` do not exist anywhere in `app/`. Real Compose UI overlay is `RadarEdgeOverlay.kt`; real audio alert management is handled by `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `NavigationAudioAlertManager.kt`, and `BikeBellAudioSynthesizer.kt` (Kotlin, not Java).
  * *Tests & Map Helper*: Verified that `BANALServiceTest` does not exist; authentic tests are `BANALServiceLifecycleTest.kt` and `BANALServiceRepositoryTest.kt`. `MapViewportHelper.kt` does not exist; authentic viewport math helper is `MapDetailViewportMath.kt`.
  * *Location & FGS*: Verified that `SpeedAndLocationDevice_GoogleFused.java` uses `com.google.android.gms.location.LocationServices.FusedLocationApi` with `GoogleApiClient` and `LocationListener`. Verified `TrackerService.java` and `AndroidManifest.xml` declare only `FOREGROUND_SERVICE_TYPE_LOCATION` and `FOREGROUND_SERVICE_TYPE_HEALTH`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Replace all hallucinated, stale, or contradictory class, test, and constant references in `docs/system/sysreq_live_tracking.md`.
  * Extend `SystemLiveTrackingRequirementsContractTest.kt` to dynamically extract all backticked `.kt`/`.java` filenames from `sysreq_live_tracking.md` and assert `File.exists()` on disk without reading production source text.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero modifications to Android production runtime bytecode in `app/src/main/java`.
  * No modifications to Bluetooth constants or timing thresholds.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-054` / `TST-PRO-054` (*System Requirements for Live Tracking with Sensors*).
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-02 (2026-11-08) and ticket ATT-6160 (commit `1c055509`).
* **Root Reason for Existing Formulation**: Early draft used hypothetical naming conventions and pre-Kotlin file extensions during initial SYS.2 documentation.
* **Preservation of Core Invariants**: All 6 requirements `SYSREQ-TRK-001..006` remain 100% preserved and allocated to `SYS-EL-01..05`; exact behavioral boundaries and timing constants are maintained.

---

## 5. Architectural Strategy & High-Level Solution

1. Update `docs/system/sysreq_live_tracking.md` to reference authentic classes, helpers, and tests.
2. Extend `SystemLiveTrackingRequirementsContractTest.kt` with a dynamic file existence test verifying every cited `.kt`/`.java` file exists on disk.
3. Synchronize `REQ-PRO-054` and `TST-PRO-054` in living docs and verify master index tables.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**: Zero runtime bytecode changes; 100% unit and contract test pass rate; zero source reading audit violations.
* **Risk Rating**: **LOW** (1 SP, specification alignment and contract test hardening).
