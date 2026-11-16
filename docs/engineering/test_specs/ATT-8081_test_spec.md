# Stage 2: Requirement & Test Specification - ATT-8081: MSL Normalization & Ascent Hysteresis in ATT Fused Altitude

**Ticket**: [ATT-8081](https://rainerblind.atlassian.net/browse/ATT-8081)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-47.2` | **Branch**: `feature/ATT-8081`  
**Requirement Mapping**: `REQ-FIL-020` (*MSL Normalization, Reference Precedence, and Elevation Hysteresis for ATT Fused Altitude*)  
**Test Spec ID**: `TST-FIL-020`  
**Author**: AI Agent 2 (Implementer, Lane 1 Pro) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-FIL-020)

### 1.1 Problem Statement & Rationale
When both DEM (`AltitudeFromPositionDevice`, orthometric MSL) and GPS (`SpeedAndLocationDevice`, ellipsoidal WGS84) are enabled in `AltitudeAndSlopeDevice_ATTFused`, their ~47.5 m datum difference creates opposing Kalman innovations that shift fused elevation +11 m and trigger slope/VAM oscillations (-28% .. +13%). Additionally, zero-hysteresis accumulation on 5-Hz barometric events rectifies micro-ripples into ~4.5x inflated climbing, and localized device names pollute SQLite columns (`ATT-8081`, extending `REQ-FIL-016`).

### 1.2 Attributes
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Implementation**: `AltitudeAndSlopeDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`

### 1.3 Functional & Architectural Requirements
1. **Reference Precedence & MSL Normalization (`REQ-FIL-020.1`)**: In `AltitudeAndSlopeDevice_ATTFused`, when fresh DEM elevation is available, it SHALL take strict precedence over GPS elevation as reference anchor. When GPS elevation is used, it SHALL be normalized to MSL via `LocationCompat.getMslAltitudeMeters(location)` when available.
2. **Anchor-Based Elevation Hysteresis Deadband (`REQ-FIL-020.2`)**: `publishAltitudeAndMetrics()` SHALL implement an anchor-based hysteresis deadband ($2.0\text{ m}$ threshold): ascent/descent SHALL only accumulate when smoothed altitude exceeds $\ge 2.0\text{ m}$ beyond the respective lowest/highest anchor, rejecting 5-Hz barometric micro-ripples.
3. **Locale-Invariant Technical DB Identifier (`REQ-FIL-020.3`)**: `AltitudeAndSlopeDevice_ATTFused.getName()` SHALL return `att_fused_altitude_slope` for SQLite column and device persistence, while UI displays localized names via `DevicesDatabaseManager`.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* active DEM (500.0 m MSL) and GPS (547.5 m WGS84) feeds,
  * *When* reference updates execute in `AltitudeAndSlopeDevice_ATTFused`,
  * *Then* DEM takes precedence and fused altitude converges to 500.0 m without opposing kicks or slope oscillations.
* *Given* stationary or flat riding with ±0.3 m barometric noise at 5 Hz,
  * *When* ascent/descent is accumulated over 60 seconds,
  * *Then* exactly 0.0 m phantom ascent and 0.0 m phantom descent accumulate.
* *Given* real climbing progression rising by 25.0 m,
  * *When* climbing exceeds the 2.0 m hysteresis threshold,
  * *Then* cumulative ascent records 25.0 m climbing.
* *Given* workout recording across any of the 9 UI languages,
  * *When* `WorkoutSamples.db` records device columns,
  * *Then* the column names use `att_fused_altitude_slope` consistently.

### 1.5 System Invariants
Standstill clamping produces 0.0 m drift during pauses; localized display names in `DevicesDatabaseManager` preserved across all 9 UI languages; zero regression in 2-State / 3-State EKF switching; 100% test pass rate.

---

## 2. Test Specification (TST-FIL-020)

### Test Case 1: DEM Reference Precedence & MSL Normalization (`TST-FIL-020.1`)
* **Scope**: Unit Test (`AltitudeAndSlopeDevice_ATTFusedTest.kt`).
* **Assertions**: DEM takes precedence over GPS; no opposing innovation kicks; `LocationCompat` MSL altitude applied when DEM inactive.

### Test Case 2: 5-Hz Barometric Micro-Ripple Hysteresis Rejection (`TST-FIL-020.2`)
* **Scope**: Unit Test (`AltitudeAndSlopeDevice_ATTFusedTest.kt`).
* **Assertions**: 300 samples (60s at 5 Hz) of flat elevation with ±0.4 m noise produces 0.0 m ascent and 0.0 m descent.

### Test Case 3: True Terrain Climbing Ascent Accumulation (`TST-FIL-020.3`)
* **Scope**: Unit Test (`AltitudeAndSlopeDevice_ATTFusedTest.kt`).
* **Assertions**: Continuous 25 m climb with superposed ±0.2 m noise records 25.0 m cumulative ascent (within 0.5 m).

### Test Case 4: Locale-Invariant Column Identifier & UI Compatibility (`TST-FIL-020.4`)
* **Scope**: Unit Test (`AltitudeAndSlopeDevice_ATTFusedTest.kt`).
* **Assertions**: `device.getName()` returns `att_fused_altitude_slope`; `DevicesDatabaseManager` resolves localized name in all 9 locales.

### Test Case 5: Tier-1 Targeted Regression & Governance Verification (`TST-FIL-020.5`)
* **Scope**: Regression & Contract Test.
* **Command**: `./gradlew testDebugUnitTest --tests "*AltitudeAndSlopeDevice*" --tests "*SystemLiveTrackingRequirementsContractTest*"` + `python3 tools/verify_requirement_governance.py`.
* **Assertions**: 100% test pass rate; zero requirement governance violations.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-020.1` | DEM precedence & MSL geoid normalization | `TST-FIL-020.1` | Unit Test |
| `REQ-FIL-020.2` | 2.0 m anchor-based elevation hysteresis | `TST-FIL-020.2`, `TST-FIL-020.3` | Unit Test |
| `REQ-FIL-020.3` | Technical DB identifier & UI compatibility | `TST-FIL-020.4` | Unit Test |
| `REQ-FIL-016` / Invariants | Standstill clamping & 2-State / 3-State EKF | `TST-FIL-020.5` | Targeted Regression |
