# Stage 3 Implementation Plan - ATT-7520: 3-Zone HRV Gauge Bar Architecture

## 1. Architecture & Component Decomposition (SWE.2)
- **Target Components**:
  - `ui/tracking/ZoneGaugeState.kt`:
    - Extend `ZoneGaugeState` data class with optional `val zoneLabels: List<String>? = null` (default `null`).
  - `ui/tracking/ZoneGaugeBar.kt`:
    - In under-segment active badge, resolve text via `state.zoneLabels?.getOrNull(index) ?: "Z${state.activeZoneIndex}"`.
  - `ui/tracking/HrvZoneIndicator.kt`:
    - Declare `HRV_SEGMENT_COLORS = listOf(TTColor.Zone2, TTColor.Zone3, TTColor.Zone4)` and `HRV_ZONE_LABELS = listOf("Z2", "Z3", "Z4")`.
    - Update `calculateZoneGaugeState(alpha1, t1, t2)`:
      - Compute proportional weights: $S_1 = 1.20 - T_1$, $S_2 = T_1 - T_2$, $S_3 = T_2 - 0.20$; $w_i = S_i / S_{total}$.
      - Interpolate strictly monotonic continuous `needleFraction` across the 3 segments (Green left, Orange middle, Red right), clamped in $[0.05f, 0.95f]$.
      - Assign `activeZoneIndex` = 1 ($> T_1$, Z2), 2 ($T_2..T_1$, Z3), 3 ($< T_2$, Z4).
      - Return `ZoneGaugeState` with 3 segments, proportional weights, and `HRV_ZONE_LABELS`.
  - `TrackingHrvZoneIndicatorTest.kt`:
    - Update test suite to verify 3 segments, `activeZoneIndex` 1..3, `zoneLabels`, and dynamic weights.
  - `SensorFieldZoneGaugeTest.kt`:
    - Add assertions for `zoneLabels` in `ZoneGaugeBar` and verify backward compatibility for HR (5-zone) and Power (7-zone).
- **Scope Boundaries (ATT-1250)**: Zero modifications to HR (5-zone) or Power (7-zone) gauge math in `ZoneGaugeMath.kt`. DataStore keys and defaults remain 100% invariant.

## 2. Atomic Implementation Steps
1. **Step 1 - ZoneGaugeState & ZoneGaugeBar Custom Labels Support (`REQ-TRK-061.3a`, `REQ-UI-376`)**:
   - Add `zoneLabels: List<String>? = null` to `ZoneGaugeState`.
   - Update `ZoneGaugeBar.kt` under-segment badge text resolution with fallback to `"Z${state.activeZoneIndex}"`.
2. **Step 2 - HrvZoneIndicator 3-Segment Gauge Calculation (`REQ-TRK-061.3b`–`REQ-TRK-061.3f`)**:
   - In `HrvZoneIndicator.kt`, define `HRV_SEGMENT_COLORS` and `HRV_ZONE_LABELS`.
   - Implement dynamic proportional `segmentWeights`, continuous linear `needleFraction`, and active zone mapping.
3. **Step 3 - Unit Test Suite Updates & Coverage (`TST-TRK-061.3a`–`TST-TRK-061.3d`)**:
   - Update `TrackingHrvZoneIndicatorTest.kt` assertions for 3-zone structure, weights, and labels.
   - Add `SensorFieldZoneGaugeTest.kt` tests for custom `zoneLabels` rendering in `ZoneGaugeBar`.
4. **Step 4 - Verification & Governance Suite (`TST-TRK-061.3e`)**:
   - Run targeted unit tests: `TrackingHrvZoneIndicatorTest`, `SensorFieldZoneGaugeTest`, `ZoneGaugeMathTest`, `ThemeColorCentralizationAuditTest`.
   - Run `python3 tools/verify_requirement_governance.py`.

## 3. Invariant Protection & System Properties
- **Backward Compatibility**: HR (5-zone) and Power (7-zone) gauges unaffected (`zoneLabels == null` fallback).
- **Theme & Contrast**: Strict compliance with Rule 71 theme tokens and AMOLED contrast.
- **Invariants**: $T_1 \ge T_2 + 0.05f$ threshold boundaries respected.

## 4. Verification & Testing Strategy
- Automated tests: `TrackingHrvZoneIndicatorTest`, `SensorFieldZoneGaugeTest`, `ZoneGaugeMathTest`.
- Governance: `python3 tools/verify_requirement_governance.py`.

## 5. UI Consistency & Living Documentation Updates
- Eliminates 5-zone HR visual confusion in `SensorFieldView` for HRV metrics, aligning with the 3-zone physiological model.
- Update `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md` to `[Verified]` in Stage 5.
