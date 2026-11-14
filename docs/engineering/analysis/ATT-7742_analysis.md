# Stage 1: Problem Domain & Root Cause Analysis - ATT-7742

**Ticket**: [ATT-7742](https://rainerblind.atlassian.net/browse/ATT-7742)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feat/ATT-7742`  
**Role**: `subagent_pro` (`--as agent1` Analysis)

---

## 1. Problem Domain & Root Cause Analysis
Following the Sprint 2026-46.5 reviews of `ATT-7520`, `ATT-7519`, and `ATT-7524`, two physiological and presentation discrepancies were identified in HRV (DFA α1) zone visualization:
1. **Misleading "Zone 2/3/4" Nomenclature**:
   - `HrvZoneIndicator.HRV_ZONE_LABELS` uses hardcoded strings `listOf("Z2", "Z3", "Z4")`.
   - `res/values*/strings_hrv.xml` card titles append `(Zone 2)`, `(Zone 3)`, and `(Zone 4)` (e.g., `Aerobic Base (Zone 2)`).
   - *Physiological Reality*: DFA α1 represents autonomic fractal scaling across 3 physiological domains separated by HRVT1 and HRVT2: Aerobic, Threshold Transition, and Anaerobic. Conflating them with traditional 5-zone HR or 7-zone Coggan Power zone numbers ("Z2/Z3/Z4") causes cognitive confusion for athletes who expect HRV to reflect autonomic states rather than heart rate zones.
2. **Missing Gauge Bar ('Zonen-Balken' / showGauge) in Settings & Cockpit**:
   - In `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent`), only 4 display toggles exist (Background, Left Bar, Right Bar, Text Color); `showGauge` (`zone_display_gauge` / "Zonen-Balken") was omitted from the `Zonendarstellung` card, and `previewFieldState` hardcodes `showBar = true`.
   - In `TrackingViewsDatabaseManager.java`, `isBarSupported` and `isBarSupportedSensorType` omit `SensorType.HRV_DFA_A1`. Consequently, cockpit sensor field configuration dialogs reject `showBar = true` for DFA α1, preventing the 3-segment gauge bar from rendering during live tracking despite `TrackingViewModel` having gauge calculation support.

---

## 2. Chesterton's Fence Forensics
- **`HRV_ZONE_LABELS` ("Z2", "Z3", "Z4")**: Added in `ATT-7520` as an initial quick badge label to distinguish the 3 segments from HR 5-zone badges. Replacing them with localized domain labels (`hrv_preview_pill_*`: Aerobic / Transition / Anaerobic) respects the underlying physiology without breaking `ZoneGaugeBar` layout.
- **Card Titles `(Zone 2/3/4)`**: Added in `ATT-7317` when introducing the 4th tab to map thresholds to traditional zones. Removing the parenthetical zone numbers leaves clean, professional domain titles across all 9 locales.
- **`isBarSupported` Exclusions**: Configured in `ATT-4927` when bars were strictly restricted to HR, Power, Balance, and W'. Expanding `isBarSupported` to include `SensorType.HRV_DFA_A1` safely enables the horizontal bar without side effects on non-bar sensors.

---

## 3. Scope & Side-Effect Audit
- **In Scope**:
  1. Update `HrvZoneIndicator.HRV_ZONE_LABELS` to default to `listOf("Aerobic", "Transition", "Anaerobic")`, allow optional `zoneLabels: List<String>? = null` in `calculateZoneGaugeState()`, and supply localized domain labels (`hrv_preview_pill_*`) from `ZonesSettingsActivity` and `TrackingViewModel`.
  2. Strip `(Zone 2/3/4)` and localized equivalents from `hrv_zone_aerobic_base_label`, `hrv_zone_threshold_transition_label`, and `hrv_zone_anaerobic_label` across all 9 locales.
  3. Expose `zone_display_gauge` ("Zonen-Balken") toggle in `HrvDfaA1SettingsContent`, persist to `appSettingsDataStore.saveHrvZoneDisplayOptions()`, and bind `previewFieldState.showBar = displayOptions.showGauge`.
  4. Add `SensorType.HRV_DFA_A1` to `TrackingViewsDatabaseManager.isBarSupported()` and `isBarSupportedSensorType()`.
- **Out of Scope**: Heart rate 5-zone and Power 7-zone gauge math, which remain 100% unchanged.

---

## 4. Change-Request Impact Analysis (REQ-PRO-064)
- **Affected Requirements**: Amends `REQ-TRK-061.3` (3-domain gauge badge labels), `REQ-TRK-061.4` (clean zone titles), and `REQ-UI-376.1` / `REQ-TRK-046` (DFA α1 bar exposure).
- **Affected Architecture Elements**: `HrvZoneIndicator.kt` (SWE.2 UI tracking), `ZonesSettingsActivity.kt` (SWE.2 Settings UI), `TrackingViewsDatabaseManager.java` (SWE.2 database views helper), `TrackingViewModel.kt` (SWE.2 tracking state orchestration), `res/values*/strings_hrv.xml` (9-language localization).
- **Verification Impact & Test Surface**: Unit tests in `TrackingHrvZoneIndicatorTest`, contract tests in `HrvDfaA1SettingsContractTest`, database bar tests in `TrackingViewsDatabaseManagerTest`, and 9-language parity in `TranslationParityTest`.
- **Side-Effect Audit & Regression Risks**: Zero interference with 5-zone HR or 7-zone Power models; DataStore key `HRV_DFA_A1_ZONE_SHOW_GAUGE` already existed; fallback to English default labels ensures robustness if context is absent.

---

## 5. Verification Plan
- `TrackingHrvZoneIndicatorTest`: Verify default domain labels and custom/localized label propagation.
- `TrackingViewsDatabaseManagerTest` & `EditSensorFieldViewModelTest`: Verify `isBarSupported` returns true for `SensorType.HRV_DFA_A1` across sports.
- `HrvDfaA1SettingsContractTest`: Verify `zone_display_gauge` presence, DataStore persistence wiring, and clean card titles without `(Zone 2/3/4)`.
- `TranslationParityTest`: 100% parity across all 9 supported locales.
