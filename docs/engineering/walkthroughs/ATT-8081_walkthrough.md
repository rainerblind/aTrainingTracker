# Stage 5: Walkthrough & Verification — ATT-8081: MSL Normalization & Ascent Hysteresis in ATT Fused Altitude

**Ticket**: [ATT-8081](https://rainerblind.atlassian.net/browse/ATT-8081)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-47.2` | **Branch**: `feature/ATT-8081`  
**Requirement Mapping**: `REQ-FIL-020` (`REQ-FIL-020.1`..`REQ-FIL-020.3`)  
**Test Mapping**: `TST-FIL-020` (`TST-FIL-020.1`..`TST-FIL-020.5`)  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash) | **Date**: 2026-11-16  

---

## 1. Executive Summary & Verification Overview
Outdoor tracking with `AltitudeAndSlopeDevice_ATTFused` (`ATT-6279`) exhibited a +11 m vertical offset and ~4.5x inflated climbing (94.6 m / 95.9 m on a 20–26 m real climb) due to datum mismatch between WGS84 GPS ($h_{ellip}$) and MSL DEM ($H_{ortho}$, $N \approx +47.5\text{ m}$ in Central Europe) and 5-Hz zero-hysteresis noise rectification.
* **Fix Implemented**:
  1. `SpeedAndLocationDevice.java`: Prefer orthometric MSL altitude via `LocationCompat.hasMslAltitude(location)` / `LocationCompat.getMslAltitudeMeters(location)`.
  2. `AltitudeAndSlopeDevice_ATTFused.kt`: DEM reference precedence over unnormalized WGS84 GPS altitude with a 15-second freshness threshold (`DEM_STALENESS_THRESHOLD_MS`), completely eliminating the geoid datum tug-of-war.
  3. `AltitudeAndSlopeDevice_ATTFused.kt`: 2.0 m anchor-based elevation hysteresis deadband (`ASCENT_DESCENT_HYSTERESIS_METERS`), resetting on `shutDown()` and eliminating 5-Hz barometric micro-ripple noise rectification.
  4. `AltitudeAndSlopeDevice_ATTFused.kt`: Locale-invariant technical DB identifier (`TECHNICAL_DEVICE_NAME = "att_fused_altitude_slope"`), while `DevicesDatabaseManager` retains localized display names across all 9 supported languages and `WorkoutSamplesDatabaseManager` retains legacy column compatibility.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-020.1` (*DEM Precedence & MSL*) | `TST-FIL-020.1` | Unit Test | **PASSED** | `[Verified]` |
| `REQ-FIL-020.2` (*2.0m Hysteresis*) | `TST-FIL-020.2`, `TST-FIL-020.3` | Unit Test | **PASSED** | `[Verified]` |
| `REQ-FIL-020.3` (*Technical DB Name*) | `TST-FIL-020.4` | Unit Test | **PASSED** | `[Verified]` |
| `REQ-FIL-016` / Invariants | `TST-FIL-020.5` | Targeted Regression & Governance | **PASSED** | `[Verified]` |

---

## 3. Automated Test Evidence

### Targeted Unit Test Scope (`./gradlew testDebugUnitTest --tests "*AltitudeAndSlopeDevice_ATTFused*"`)
* **`AltitudeAndSlopeDevice_ATTFusedTest.kt`** (14/14 tests PASSED):
  * `testDemReferencePrecedence_overridesGpsAltitude_tstFil020_1` (`// TST-FIL-020.1`): PASSED.
  * `testBarometricMicroRippleHysteresisRejection_tstFil020_2` (`// TST-FIL-020.2`): PASSED.
  * `testTerrainClimbAccumulation_withHysteresis_tstFil020_3` (`// TST-FIL-020.3`): PASSED.
  * `testTechnicalDeviceName_localeInvariant_tstFil020_4` (`// TST-FIL-020.4`): PASSED.
  * All 10 existing simulation, concurrency, and EKF state tests: PASSED.

---

## 4. Verification & Clean-Room Governance Audit
* `python3 tools/verify_requirement_governance.py`: **PASS** (100% living doc parity, 0 ungrounded requirements, 0 code orphans).
* `python3 tools/verify_requirement_governance.py --check-code-orphans`: **PASS** (0 code-to-shard orphans).
* `python3 tools/verify_requirement_governance.py --check-req-description`: **PASS** (100% description coverage).
* `python3 tools/verify_requirement_governance.py --verify-sys-trace --check-reverse-sys-trace`: **PASS** (100% bidirectional SYSREQ-TRK-003 / SYSREQ-TRK-004 traceability).
* `ANT-Android-SDKs` submodule: clean & untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
