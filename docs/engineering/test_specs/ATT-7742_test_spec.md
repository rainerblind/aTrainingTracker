# Test Specification: ATT-7742 Localized HRV Zones & Gauge Bar

## Metadata
- **Ticket**: ATT-7742
- **Target Release**: V5.0.0
- **Stage**: Stage 2 (Requirement & Test Specification)
- **Status**: [Proposed]

## 1. Requirements Matrix
| Requirement ID | Summary | Scope |
| :--- | :--- | :--- |
| `REQ-TRK-061.3` | HRV Gauge Bar Active Segment Label: Replace hardcoded `"Z2"`, `"Z3"`, `"Z4"` with localized Aerobic, Transition, Anaerobic strings (`hrv_preview_pill_*`) with fallback to `"Aerobic"`, `"Transition"`, `"Anaerobic"`. | Cockpit / Gauge UI |
| `REQ-TRK-061.4` | HRV Zone Settings & Cockpit Gauge Support: Remove parenthetical zone numbers `(Zone 2)`, `(Zone 3)`, `(Zone 4)` across all 9 languages; add `zone_display_gauge` toggle to HRV settings card; enable `SensorType.HRV_DFA_A1` in `TrackingViewsDatabaseManager.isBarSupported()`. | Settings / Views DB |

## 2. Test Matrix
| Test Case ID | Traceability | Description / Acceptance Criteria | Level |
| :--- | :--- | :--- | :--- |
| `TST-TRK-061.3` | `REQ-TRK-061.3` | Given DFA a1 values in Base, Transition, or Anaerobic zones, when gauge state is computed, then labels reflect localized strings (`hrv_preview_pill_*`) or non-empty localized zone names without `"Z2"`, `"Z3"`, `"Z4"`. | Unit (JVM) |
| `TST-TRK-061.4` | `REQ-TRK-061.4` | 1. `hrv_zone_*_label` strings across all 9 `strings_hrv.xml` contain no `(Zone X)` or `(Zon X)` substrings.<br>2. `HrvDfaA1SettingsContent` exposes `zone_display_gauge` switch updating `showGauge`.<br>3. `TrackingViewsDatabaseManager.isBarSupported(HRV_DFA_A1, sport)` returns `true` for all sports. | Unit / Contract |

## 3. Localization Parity Audit (9 Languages)
- Required string resources verified across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-nl`, `values-pl`, `values-pt`, `values-ru`:
  - `hrv_preview_pill_aerobic` ("Aerob" / "Aerobic" / ...)
  - `hrv_preview_pill_transition` ("Übergang" / "Transition" / ...)
  - `hrv_preview_pill_anaerobic` ("Anaerob" / "Anaerobic" / ...)
  - `hrv_zone_aerobic_base_label`: clean label without `(Zone 2)`
  - `hrv_zone_threshold_transition_label`: clean label without `(Zone 3)`
  - `hrv_zone_anaerobic_label`: clean label without `(Zone 4)`

## 4. Chesterton's Fence Audit
1. **Historical Intent**: Zones 2/3/4 mapping was copied from general 5-zone HR/Power models during initial DFA a1 implementation (ATT-7520).
2. **Underlying Problem**: Users were confused by 3 HRV physiological domains labelled as "Z2/Z3/Z4" when HR had 5 zones.
3. **Modern Context**: DFA a1 has distinct physiological thresholds (T1=0.75, T2=0.50) representing Aerobic, Transition, Anaerobic states.
4. **Verification**: Invariants in `ZoneGaugeState` remain intact; only label representation and configuration visibility are modernized.

## 5. Invariants & Backward Compatibility
- Default parameters in `HrvZoneIndicator.calculateZoneGaugeState` maintain non-null defaults (`listOf("Aerobic", "Transition", "Anaerobic")`).
- `ZoneDisplayOptions.showGauge` defaults to `true`, maintaining existing cockpit rendering behavior.
