# Implementation Plan: ATT-7742 Localized HRV Zones & Gauge Bar

## Metadata
- **Ticket**: ATT-7742
- **Target Release**: V5.0.0
- **Stage**: Stage 3 (Implementation Plan)
- **Status**: [Proposed]

## 1. SWE.2 Architecture & Component Mapping
- **UI / Presentation Layer**:
  - `HrvZoneIndicator.kt`: Modernize default `HRV_ZONE_LABELS` to `listOf("Aerobic", "Transition", "Anaerobic")`. Support parameter `zoneLabels: List<String>? = HRV_ZONE_LABELS` in `calculateZoneGaugeState`.
  - `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent`): Expose `zone_display_gauge` (`showGauge`) toggle row. Bind `previewFieldState.showBar = displayOptions.showGauge`. Pass localized zone strings (`hrv_preview_pill_*`) into `calculateZoneGaugeState`.
- **Telemetry / ViewModel Layer**:
  - `TrackingViewModel.kt`: Pass localized zone strings (`hrv_preview_pill_*`) into `HrvZoneIndicator.calculateZoneGaugeState` for live tracking cockpit gauge bar rendering.
- **Data & Configuration Layer**:
  - `TrackingViewsDatabaseManager.java`: In `isBarSupported()` and `isBarSupportedSensorType()`, add `SensorType.HRV_DFA_A1` support for all sports.
- **Localization Resources**:
  - `res/values*/strings_hrv.xml` (9 locales): Strip `(Zone 2)`, `(Zone 3)`, `(Zone 4)` and localized equivalents from `hrv_zone_aerobic_base_label`, `hrv_zone_threshold_transition_label`, `hrv_zone_anaerobic_label`.

## 2. Atomic Construction Steps
1. **Localization Cleanup (Step 1)**: In all 9 `strings_hrv.xml`, remove parenthetical zone numbers `(Zone X)` from the 3 zone labels.
2. **Database Views Manager (Step 2)**: Add `SensorType.HRV_DFA_A1` to `isBarSupported` (return `true` for all sports) and `isBarSupportedSensorType` in `TrackingViewsDatabaseManager.java`.
3. **Gauge Indicator Modernization (Step 3)**: In `HrvZoneIndicator.kt`, update `HRV_ZONE_LABELS` to `listOf("Aerobic", "Transition", "Anaerobic")` and add optional `zoneLabels: List<String>?` to `calculateZoneGaugeState`.
4. **Settings & Preview Wiring (Step 4)**: In `ZonesSettingsActivity.kt`, add `ZoneDisplayToggleRow` for `R.string.zone_display_gauge` in `HrvDfaA1SettingsContent`, persist to `appSettingsDataStore.saveHrvZoneDisplayOptions`, wire `previewFieldState.showBar = displayOptions.showGauge`, and supply localized pill strings to preview gauge state.
5. **Live Tracking ViewModel Wiring (Step 5)**: In `TrackingViewModel.kt`, pass localized zone label strings into `calculateZoneGaugeState`.
6. **Unit & Contract Testing (Step 6)**: Update `TrackingHrvZoneIndicatorTest.kt`, `HrvDfaA1SettingsContractTest.kt`, and add checks in `TrackingViewsDatabaseManagerTest.java` / `TranslationParityTest.kt`.

## 3. Invariants & UI Consistency
- Invariant 1: HR 5-zone and Power 7-zone gauge bars remain 100% unaffected (`zoneLabels == null` fallback preserves `"Z${activeZoneIndex}"`).
- Invariant 2: `ZoneDisplayOptions.showGauge` defaults to `true`.
- Invariant 3: Single-line and no-scroll layout invariants preserved across all locales (Rule 71 & 72).
- UI Consistency: Use standard `ZoneDisplayToggleRow` with `stringResource(R.string.zone_display_gauge)` matching HR and Power settings cards.

## 4. Verification Commands
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.zones.*" --tests "com.atrainingtracker.trainingtracker.views.*" --tests "*TranslationParityTest*" --max-workers=2`
