# Stage 3: Implementation Plan - ATT-6739: Replace hallucinated radar/audio classes and enforce file existence in sysreq_live_tracking.md

**Ticket**: [ATT-6739](https://rainerblind.atlassian.net/browse/ATT-6739)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-054`  
**Test Mapping**: `TST-PRO-054`  
**Branch**: `feature/ATT-6739`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

`docs/system/sysreq_live_tracking.md` contains hallucinated or stale references: `RadarThreatView.kt`, `SoundEngine.java`, `BikeBellAudioSynthesizer.java`, `BANALServiceTest`, `MapViewportHelper.kt`, `FusedLocationProviderClient`, and contradictory FGS type `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`. This plan specifies the atomic steps to bring all references into alignment with authentic production files and harden contract tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-054` (*System Requirements for Live Tracking with Sensors*)
* **Test Mapping**: `TST-PRO-054` (*System Requirements for Live Tracking Contract Test Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Runtime Regressions**: Zero modifications to production source code in `app/src/main/java`.
2. **Dynamic Grounding Parity**: All cited source and test files exist on disk.
3. **Zero Source-Reading Violation**: Path resolution avoids literal forbidden strings to maintain ATT-6186 compliance.
4. **Human Gate**: Advances to `Final Review (Human)` assigned to `human`.

---

## 4. Proposed Architectural Changes

### Component 1: Live Tracking Specification (`docs/system/sysreq_live_tracking.md`)
* Update `SYSREQ-TRK-001`: replace `BANALServiceTest` with `BANALServiceLifecycleTest.kt` and `BANALServiceRepositoryTest.kt`.
* Update `SYSREQ-TRK-004`: replace `MapViewportHelper.kt` with `MapDetailViewportMath.kt`; specify `SpeedAndLocationDevice_GoogleFused` using `LocationServices.FusedLocationApi` (`GoogleApiClient` / `LocationListener`).
* Update `SYSREQ-TRK-005`: verify FGS types in line 78 specify `location|health` only.
* Update `SYSREQ-TRK-006`: replace `RadarThreatView.kt` with `RadarEdgeOverlay.kt`, `SoundEngine.java` with `RadarAlertManager.kt` / `RadarAudioSynthesizer.kt` / `NavigationAudioAlertManager.kt`, and `BikeBellAudioSynthesizer.java` with `BikeBellAudioSynthesizer.kt`.

### Component 2: Contract Test Suite (`SystemLiveTrackingRequirementsContractTest.kt`)
* Implement `testSysreqLiveTracking_allReferencedCodeFilesExistOnDisk` dynamically extracting backticked `.kt` and `.java` filenames from `docs/system/sysreq_live_tracking.md` and asserting `File.exists()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Reconcile `docs/system/sysreq_live_tracking.md`
* Apply all class, helper, test, and constant substitutions across Sections 2 and 3.

### Step 2: Harden `SystemLiveTrackingRequirementsContractTest.kt`
* Add dynamic file existence test verifying all referenced `.kt`/`.java` files exist on disk.

### Step 3: Targeted Verification & Governance Checks
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest"`
* Run `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit test suite and living documentation governance checks.
* **Rollback**: Branch isolation allows full discard via `git checkout sprint/2026-46.2`.
