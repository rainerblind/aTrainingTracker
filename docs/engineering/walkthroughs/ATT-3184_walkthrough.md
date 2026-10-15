# Stage 5: Walkthrough & Verification - ATT-3184: Migrate cycling power to 7-zone Coggan model while retaining 5-zone scheme for heart rate

**Ticket**: [ATT-3184](https://rainerblind.atlassian.net/browse/ATT-3184)  
**Sub-task**: [ATT-3599](https://rainerblind.atlassian.net/browse/ATT-3599) (`[Verification] Clean-Room Regression, Living Docs & Walkthrough`)  
**Parent Epic**: [ATT-1191](https://rainerblind.atlassian.net/browse/ATT-1191) (*Modern User Experience*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-370` (*7-Zone Coggan Cycling Power Model & Independent 5-Zone HR Scheme*)  
**Test Mapping**: `TST-UI-333` (*7-Zone Coggan Power & 5-Zone Heart Rate Verification*)  
**Branch**: `feature/ATT-3184`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Verification Overview

Prior to ATT-3184, `aTrainingTracker` enforced a uniform 5-zone model across all biometric and telemetry modalities (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`). For cycling power, modern exercise physiology and power-meter standards strictly adhere to Hunter Allen & Dr. Andrew Coggan's canonical 7-zone model:
- **Z1**: Active Recovery (< 55% FTP)
- **Z2**: Endurance (56–75% FTP)
- **Z3**: Tempo (76–90% FTP)
- **Z4**: Lactate Threshold (91–105% FTP)
- **Z5**: VO2 Max (106–120% FTP)
- **Z6**: Anaerobic Capacity (121–150% FTP)
- **Z7**: Neuromuscular Power (> 150% FTP)

Attempting to compress cycling power into 5 zones conflated VO2max intervals with high-intensity anaerobic work and sprint intervals, degrading aftermath telemetry distribution analytics and live coaching feedback.

ATT-3184 successfully completes this migration under strict ASPICE SWE.2 / SWE.3 architecture principles:
1. **Model Decoupling**: `SettingsDataStore.ZoneType` cleanly differentiates `zoneCount` (5 for HR, 7 for Power) and `boundaryCount` (4 for HR, 6 for Power). Heart rate running and cycling remain untouched on 5 zones.
2. **Dynamic Proportional Migration & Failure Containment**: Existing user settings migrate proportionally based on the athlete's stored Zone 4 upper boundary ($FTP \approx z4Max / 1.05$), with strict post-migration monotonic sanity checking ($0 < z1 < z2 < z3 < z4 < z5 < z6$) falling back to canonical defaults (`150, 200, 250, 300, 350, 450`) upon corruption.
3. **Data Safety & Null-Tolerance in Java/C++ Interop**: `SettingsDataStoreJavaHelper.getZoneMax` maps Zone 5 and 6 through dedicated helper methods without array indexing, completely preventing `ArrayIndexOutOfBoundsException` or Java crashes.
4. **Color & Localization Parity**: Extended standard palette with `Zone6` (`#4E342E` Deep Espresso / Anaerobic) and `Zone7` (`#000000` Pitch Black / Neuromuscular). Complete 9-language localization parity (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) for `zone_6_label` and `zone_7_label`.
5. **UI & Analytics Adaptation**: `ZonesSettingsActivity` dynamically renders 5 cards for HR and 7 cards for Power, with monotonic validation preventing invalid saves and a "Reset to Defaults" option. Post-ride distribution calculations, histogram binning, telemetry scrubbing graphs, and real-time tracking sensor tiles properly handle 7 power zones.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-370.1` (7-Zone Power Model & 5-Zone HR) | `TST-UI-333.1` | `SettingsDataStorePowerZonesTest.kt` | **PASSED** | `Verified` |
| `REQ-UI-370.2` (Dynamic Proportional Migration) | `TST-UI-333.2` | `SettingsDataStorePowerZonesTest.kt` | **PASSED** | `Verified` |
| `REQ-UI-370.3` (Palette Extension Z6 & Z7) | `TST-UI-333.3` | `SensorFieldZoneDisplayTest.kt`, `ColorTest` | **PASSED** | `Verified` |
| `REQ-UI-370.4` (9-Language Parity) | `TST-UI-333.4` | `PowerZoneLocalizationTest.kt` | **PASSED** (100% 9 locales) | `Verified` |
| `REQ-UI-370.5` (Settings Screen 7-Card Flow) | `TST-UI-333.5` | Code review & unit test inspection | **PASSED** | `Verified` |
| `REQ-UI-370.6` (Aftermath Distribution & Histogram) | `TST-UI-333.6` | `PowerZoneDistributionCalculatorTest.kt`, `TelemetryHistogramCalculatorTest.kt` | **PASSED** | `Verified` |
| `REQ-UI-370.7` (Telemetry Graph Scrubbing & Bands) | `TST-UI-333.7` | `TelemetryMetricGraphZoneTest.kt`, `TelemetryZoneMathTest.kt` | **PASSED** | `Verified` |
| `REQ-UI-370.8` (Tracking Live View & Battery Saver) | `TST-UI-333.8` | `SensorFieldZoneDisplayTest.kt`, `BatterySaverModelTest` | **PASSED** | `Verified` |
| `REQ-UI-370.9` (Monotonic Invariant & Reset) | `TST-UI-333.9` | `SettingsDataStorePowerZonesTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-333.10` | `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Integration Tests (37/37 Passed)
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.SettingsDataStorePowerZonesTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCalculatorTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneLocalizationTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldZoneDisplayTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest"
```
**Output**:
```text
BUILD SUCCESSFUL in 13s
32 actionable tasks: 4 executed, 28 up-to-date
```
- `SettingsDataStorePowerZonesTest`: 6/6 passed (default values, proportional migration from stored z4Max, low-bound FTP monotonicity, corrupt state fallback containment, strict monotonic validation, reset to defaults).
- `TelemetryZoneMathTest`: 6/6 passed (7 power bands, clamping, sub-threshold omit Z7, zone determination 1..7).
- `PowerZoneDistributionCalculatorTest`: 6/6 passed (7 power zones with accurate percentage and time formatting).
- `PowerZoneLocalizationTest`: 1/1 passed (all 9 languages contain valid translations for `zone_6_label` and `zone_7_label`).
- `SensorFieldZoneDisplayTest`: 12/12 passed (Zone 6 and Zone 7 colors and tags for power, maintaining 5-zone HR scheme).
- `TelemetryMetricGraphZoneTest`: 6/6 passed (scrubbing tags Z1..Z7, 7 power bands on telemetry graph).

### Clean-Room Regression Full Test Suite (100% Passed)
```bash
./gradlew testDebugUnitTest
```
**Output**:
```text
BUILD SUCCESSFUL in 2m 55s
32 actionable tasks: 12 executed, 20 up-to-date
```
Zero test regressions across the entire codebase.

---

## 4. Architectural Verification & Invariants

1. **Non-Regressive Heart Rate Handling**:
   - `ZoneType.HR_RUN.zoneCount == 5`, `boundaryCount == 4`
   - `ZoneType.HR_BIKE.zoneCount == 5`, `boundaryCount == 4`
   - Unaltered 5-zone evaluation and rendering across all HR telemetry graphs, tracking views, and aftermath summaries.
2. **Power 7-Zone Contract**:
   - `ZoneType.PWR_BIKE.zoneCount == 7`, `boundaryCount == 6`
   - Default boundaries: `150, 200, 250, 300, 350, 450`
   - Monotonic constraint: `0 < z1 < z2 < z3 < z4 < z5 < z6` enforced both at UI input time and during DataStore migration.
3. **Telemetry Clamping**:
   - For sub-threshold rides where max recorded power is below `z6Max`, Zone 7 band is omitted from the graph to prevent deceptive zero-power dead bands.
