# Stage 3: Architecture & Implementation Plan - ATT-3184: [Verbesserung] Migrate cycling power to 7-zone Coggan model while retaining 5-zone scheme for heart rate

**Ticket**: [ATT-3184](https://atrainingtracker.atlassian.net/browse/ATT-3184)  
**Sub-task**: [ATT-3597](https://atrainingtracker.atlassian.net/browse/ATT-3597) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111)  
**Target Release**: `v1.0.0-rc1`  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-370`  
**Test Spec Mapping**: `TST-UI-333`  
**Branch**: `feature/ATT-3184`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Architectural Overview & SWE.2 Design

### 1.1 Decoupled Zone Architecture
The application currently binds Heart Rate and Cycling Power to a shared 5-zone paradigm. To align with modern sports science and exercise physiology, this plan decouples them:
- **Heart Rate (`HR_RUN`, `HR_BIKE`)**: Remains strictly on the 5-zone model with 4 upper boundaries (`z1Max..z4Max`) and colors `Zone1..Zone5`.
- **Cycling Power (`PWR_BIKE`)**: Expands to Dr. Andy Coggan's 7-zone model with 6 upper boundaries (`z1Max..z6Max`) and colors `Zone1..Zone7`.

### 1.2 Color Token Extensions
- Add `TTColor.Zone6 = Color(0xFF4E342E)` (Dark Chestnut Brown) and `TTColor.Zone7 = Color(0xFF000000)` (Pure Black) in `Color.kt`.
- Add XML resources in `res/values/color.xml`: `<color name="zone_6">#4E342E</color>`, `<color name="zone_7">#000000</color>`.

### 1.3 DataStore Persistence, Boundary Semantics & Resilience
- **DataStore Storage Model**:
  - In alignment with existing `HR_RUN_ZONE1_MAX..HR_RUN_ZONE4_MAX` and `PWR_BIKE_ZONE1_MAX..PWR_BIKE_ZONE4_MAX`, explicit DataStore keys (`PWR_BIKE_ZONE5_MAX`, `PWR_BIKE_ZONE6_MAX`) are retained to preserve architectural symmetry and zero overhead without breaking existing DataStore schema paradigms.
  - Extend `Zone` enum in `SettingsDataStore.kt` with `ZONE_5(5)` and `ZONE_6(6)`.
- **Dynamic Proportional Migration & Failure Containment**:
  - When `pwr_bike_zone5_max` or `pwr_bike_zone6_max` is missing:
    - If `z4Max` is present and valid ($> 0$):
      - $\text{Estimated FTP} = \max(50, \text{round}(\text{z4Max} / 1.05))$
      - Default $z5Max = \max(z4Max + 10, \text{round}(\text{Estimated FTP} \times 1.20))$
      - Default $z6Max = \max(z5Max + 10, \text{round}(\text{Estimated FTP} \times 1.50))$
    - If `z4Max` is absent or malformed (fresh install or corrupted prefs): canonical defaults: $Z1=150, Z2=200, Z3=250, Z4=300, Z5=350, Z6=450$.
  - Atomic migration: Write both migrated values into DataStore inside a single atomic `edit` block. If reading occurs before migration is persisted, `getZoneMaxFlow` returns the calculated fallback dynamically, ensuring zero uninitialized state or race condition windows.
  - Reset to Defaults: Add `resetToDefaults(zoneType)` function in `SettingsDataStore` providing clean recovery to canonical defaults.
- **Strict Monotonic Ascending Validation**:
  - `validatePowerZones(z1, z2, z3, z4, z5, z6)` enforces $0 < z1 < z2 < z3 < z4 < z5 < z6$.
- **Zone 7 Upper Boundary Semantics**:
  - Zone 7 represents neuromuscular sprint efforts and is open-ended ($P > z6Max$).
  - In telemetry band calculation (`TelemetryZoneMath`), Zone 7 is clamped to `dataMax` (the maximum observed power in the session) or `maxOf(dataMax, z6Max + 100.0)`. Never uses `Double.MAX_VALUE` directly to prevent rendering / coordinate overflow in Compose Canvas plots.
- **Legacy Interface Stability (`SettingsDataStoreJavaHelper`)**:
  - `getZoneMax(context, zoneType, zoneIndex)` gracefully handles any query:
    - For `HR_RUN` and `HR_BIKE`: `zoneIndex in 1..4` returns threshold; `zoneIndex >= 5` returns 0 without throwing `IndexOutOfBoundsException` or crashing.
    - For `PWR_BIKE`: `zoneIndex in 1..6` returns thresholds; `zoneIndex >= 7` or `< 1` returns 0 safely.

### 1.4 Domain Models & Calculations
- Update `PowerZoneThresholds(z1Max: Int, z2Max: Int, z3Max: Int, z4Max: Int, z5Max: Int, z6Max: Int)` in `ZoneDistributionModels.kt`.
- Update `TelemetryZoneMath`:
  - `calculatePowerZoneBands()`: Generates 7 bands with colors `Zone1..Zone7` and labels `Z1..Z7`.
  - `determinePowerZone()`: Returns 1..7.
  - `loadPowerThresholds()`: Loads 6 thresholds from DataStore via `SettingsDataStoreJavaHelper`.
- Update `BatterySaverModel.calculateZoneIndex`:
  - When `zoneType == ZoneType.PWR_BIKE`, load all 6 boundaries and return zone 1..7.
- Update `TrackingViewModel`:
  - Expand `zoneColors` list to include `R.color.zone_6` and `R.color.zone_7`.
  - In `calculateZoneColor()`, support 7 zones for `PWR_BIKE`.
- Update `ZoneDistributionCalculator`:
  - `calculatePowerDistribution()`: Accumulates duration across 7 zones, creating 7 `ZoneTimeEntry` items with appropriate labels and colors.
- Update `TelemetryHistogramCalculator`:
  - `calculatePowerHistogram()`: Maps power values across 7 zones.
- Update `ZonesSettingsActivity.kt`:
  - Expand `ZoneProfileState` to support optional/nullable `z5` and `z6` (or `z5: Int = 0`, `z6: Int = 0`).
  - Render 5 zone rows for `HR_RUN` / `HR_BIKE` and 7 zone rows for `PWR_BIKE`.
  - In `SettingsScreenContent`, if `zoneType == ZoneType.PWR_BIKE`, show Zone 5, Zone 6, and Zone 7 (open-ended).
  - Add real-time UI validation highlighting invalid inputs and disabling saving if non-monotonic.
  - Add a "Reset to Defaults" option / icon button in the top app bar or below the zone list to allow instant recovery to standard defaults.
  - Live preview chips: Render 5 chips for HR and 7 chips for Power.

### 1.5 9-Language Localization Parity & Fallbacks
- Add `zone_6_label` and `zone_7_label` across all 9 `strings.xml` files:
  - `values/`: "Zone 6 (Anaerobic Capacity)", "Zone 7 (Neuromuscular Power)"
  - `values-de/`: "Zone 6 (Anaerobe Kapazität)", "Zone 7 (Neuromuskuläre Leistung)"
  - `values-es/`: "Zona 6 (Capacidad anaeróbica)", "Zona 7 (Potencia neuromuscular)"
  - `values-fr/`: "Zone 6 (Capacité anaérobie)", "Zone 7 (Puissance neuromusculaire)"
  - `values-it/`: "Zona 6 (Capacità anaerobica)", "Zona 7 (Potenza neuromuscolare)"
  - `values-ja/`: "ゾーン 6 (無酸素運動容量)", "ゾーン 7 (神経筋パワー)"
  - `values-nl/`: "Zone 6 (Anaerobe capaciteit)", "Zone 7 (Neuromusculair vermogen)"
  - `values-pl/`: "Strefa 6 (Wydolność beztlenowa)", "Strefa 7 (Moc nerwowo-mięśniowa)"
  - `values-pt/`: "Zona 6 (Capacidade anaeróbica)", "Zona 7 (Potência neuromuscular)"
- Resilient string formatting fallback: If localized resources are not found or fail to resolve, `ZoneDistributionCalculator` and `TelemetryZoneMath` fallback safely to programmatic `"Zone $zoneIndex"` and `"Z$zoneIndex"`.

---

## 2. Invariants & Safety Measures

1. **Chesterton's Fence Preservation**:
   - HR zones remain strictly 5-zone across all touchpoints.
   - Database schema (`WorkoutSummaries`, `WorkoutSamples`) is unchanged. Zone calculations remain purely dynamic.
   - Raw sensor telemetry export (TCX/FIT/GPX) unaffected.
2. **Atomic DataStore Transitions**:
   - All migrations and updates execute in `context.dataStore.edit`.
3. **Monotonic Ordering Invariant**:
   - $0 < z1 < z2 < z3 < z4 < z5 < z6$ enforced both in UI validation and in repository/math loaders.

---

## 3. Step-by-Step Implementation Plan

### Step 1: Color Tokens and Localization
- **Files**: `Color.kt`, `res/values/color.xml`, 9 `strings.xml` files.
- **Action**: Add `Zone6` and `Zone7` colors; add `zone_6_label` and `zone_7_label` in all 9 languages.

### Step 2: DataStore and Java Helper Extensions
- **Files**: `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`.
- **Action**: Add `ZONE_5`, `ZONE_6` to `Zone` enum. Add keys `PWR_BIKE_ZONE5_MAX` and `PWR_BIKE_ZONE6_MAX`. Implement dynamic proportional migration and monotonic checks. Update `SettingsDataStoreJavaHelper`.

### Step 3: Domain Models & Telemetry Mathematics
- **Files**: `ZoneDistributionModels.kt`, `TelemetryZoneMath.kt`, `BatterySaverModel.kt`.
- **Action**: Update `PowerZoneThresholds` to 6 upper bounds. Extend `calculatePowerZoneBands`, `determinePowerZone`, and `loadPowerThresholds`. Extend `calculateZoneIndex` for power.

### Step 4: Aftermath Analytics & Histograms
- **Files**: `ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`.
- **Action**: Adapt `calculatePowerDistribution` and `calculatePowerHistogram` to 7 zones.

### Step 5: Real-Time HUD Cockpit
- **Files**: `TrackingViewModel.kt`.
- **Action**: Add Zone 6 and 7 to `zoneColors` list. Update `calculateZoneColor()` for `PWR_BIKE`.

### Step 6: Settings UI
- **Files**: `ZonesSettingsActivity.kt`.
- **Action**: Support 7 zones for `PWR_BIKE` and 5 zones for HR. Implement real-time monotonic validation and 7 preview chips for power.

### Step 7: Unit & Localization Tests
- **Files**:
  - `SettingsDataStorePowerZonesTest.kt` (new)
  - `PowerZoneDistributionCalculatorTest.kt` (update to 7 zones)
  - `TelemetryZoneMathTest.kt` (update for 7 zones)
  - `SensorFieldZoneDisplayTest.kt` (update for 7 zones)
  - `PowerZoneLocalizationTest.kt` (verify all 9 locales)
- **Action**: Run targeted test suite and clean-room full test suite.
