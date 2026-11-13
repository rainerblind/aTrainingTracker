# Stage 5: Walkthrough & Verification - ATT-7520: 3-Zone HRV Gauge Bar Architecture

**Ticket**: [ATT-7520](https://rainerblind.atlassian.net/browse/ATT-7520)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-TRK-061.3` (`REQ-TRK-061.3a`–`REQ-TRK-061.3f`)  
**Test Mapping**: `TST-TRK-061.3` (`TST-TRK-061.3a`–`TST-TRK-061.3e`)  
**Branch**: `feature/ATT-7520` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview
ATT-7520 eliminates the 5-zone HR gauge bar rendering bug in the HRV tab preview tile and live tracking cockpit (`SensorFieldView`):
1. **3-Zone HRV Segment Architecture**: `HrvZoneIndicator.calculateZoneGaugeState()` returns a 3-segment `ZoneGaugeState` using `HRV_SEGMENT_COLORS`: `TTColor.Zone2` (Green, left), `TTColor.Zone3` (Orange, middle), and `TTColor.Zone4` (Red, right), eliminating dummy unused Z1 and Z5 segments.
2. **Proportional Dynamic Segment Weights**: Segment weights dynamically mirror physiological spans: $S_1 = 1.20 - T_1$, $S_2 = T_1 - T_2$, $S_3 = T_2 - 0.20$ normalized to sum to $1.0f$ ($0.45, 0.25, 0.30$ under defaults $0.75 / 0.50$).
3. **Monotonic Continuous Needle Interpolation**: Continuous linear `needleFraction` across $[0.05f, 0.95f]$ smoothly tracks $\alpha_1 \in [0.20, 1.20]$ from left to right as exercise intensity increases.
4. **Decoupled Under-Segment Zone Badges**: Extended `ZoneGaugeState` with optional `zoneLabels: List<String>? = null`. For HRV, `HRV_ZONE_LABELS = listOf("Z2", "Z3", "Z4")` renders `"Z2"`, `"Z3"`, and `"Z4"` directly beneath the active segment.
5. **100% Backward Compatibility**: HR (5-zone) and Power (7-zone) gauges default `zoneLabels` to `null` and continue rendering `"Z1"`..`"Z5"` and `"Z1"`..`"Z7"` without regressions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-061.3a` | `[TST-TRK-061.3d]` | Unit Test (`SensorFieldZoneGaugeTest.kt`) | **PASSED** | `Verified` |
| `REQ-TRK-061.3b` | `[TST-TRK-061.3a]` | Unit Test (`TrackingHrvZoneIndicatorTest.kt`) | **PASSED** | `Verified` |
| `REQ-TRK-061.3c` | `[TST-TRK-061.3a]` | Unit Test (`TrackingHrvZoneIndicatorTest.kt`) | **PASSED** | `Verified` |
| `REQ-TRK-061.3d` | `[TST-TRK-061.3b]` | Unit Test (`TrackingHrvZoneIndicatorTest.kt`) | **PASSED** | `Verified` |
| `REQ-TRK-061.3e` | `[TST-TRK-061.3c]` | Unit Test (`TrackingHrvZoneIndicatorTest.kt`) | **PASSED** | `Verified` |
| `REQ-TRK-061.3f` | `[TST-TRK-061.3c]` | Unit Test (`TrackingHrvZoneIndicatorTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-TRK-061.3e]` | Tier 1 Targeted Regression Scope | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
* `TrackingHrvZoneIndicatorTest`: 100% pass (3-zone colors, labels, proportional weights, and strictly monotonic needle interpolation).
* `SensorFieldZoneGaugeTest`: 100% pass (custom `zoneLabels` preservation, HR/Power backward compatibility, `SensorFieldState` integration).
* `ZoneGaugeMathTest` & `ThemeColorCentralizationAuditTest`: 100% pass.
* `:app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL** in 6s.
* `:app:lintDebug`: 0 errors / 0 warnings in touched files.
* `verify_requirement_governance.py`: **PASSED** (0 violations, 100% living doc parity).

---

## 4. Visual Verification
- Audited against defect screenshot `hrv_zones_bottom.png`: The horizontal preview gauge bar now displays exactly 3 segments matching the 3-zone HRV model (Zone 2 Green, Zone 3 Orange, Zone 4 Red) instead of the 5 HR segments.
- Active under-segment badge displays `"Z2"` (Aerobic Base), `"Z3"` (Threshold Transition), or `"Z4"` (Anaerobic / High Intensity) with elevated track segment.

---

## 5. Invariant & Governance Verification
1. **Backward Compatibility**: HR (5-zone) and Power (7-zone) gauge math and rendering remain 100% intact.
2. **Theme & Contrast**: Strict compliance with Rule 71 theme tokens and AMOLED contrast.
3. **Threshold Invariant**: Proportional spans gracefully adapt to athlete-configured $T_1$ and $T_2$ ($T_1 \ge T_2 + 0.05f$).
4. **Living Documentation**: Synchronized in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md` (`Verified`).
