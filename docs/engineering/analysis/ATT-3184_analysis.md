# Stage 1 Analysis: ATT-3184 - [Verbesserung] Migrate cycling power to 7-zone Coggan model while retaining 5-zone scheme for heart rate

**Ticket**: [ATT-3184](https://atrainingtracker.atlassian.net/browse/ATT-3184)  
**Sub-task**: [ATT-3595](https://atrainingtracker.atlassian.net/browse/ATT-3595) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `v1.0.0-rc1`  
**Active Sprint**: `2026-42.4`  
**Branch**: `feature/ATT-3184`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

Currently, `aTrainingTracker` enforces an identical 5-zone model across all metabolic and physiological metrics:
- Heart Rate for Running (`HR_RUN`)
- Heart Rate for Cycling (`HR_BIKE`)
- Cycling Power (`PWR_BIKE`)

### The Physiological & Practical Problem
While a 5-zone model is the established gold standard for Heart Rate (governed by cardiac autonomic response, Karvonen formulas, and cardiac output limits), cycling power follows Dr. Andy Coggan's internationally recognized **7-Zone Model** (Hunter & Coggan, *Training and Racing with a Power Meter*; TrainingPeaks; Wahoo SYSTM; Garmin; Zwift).

Because mechanical power is instantaneous and does not suffer from cardiac lag, cyclists experience a vast dynamic range above threshold:
1. **VO2 Max (Zone 5, 106–120% FTP)**: Aerobic capacity efforts sustained for 3 to 8 minutes.
2. **Anaerobic Capacity (Zone 6, 121–150% FTP)**: Glycolytic efforts sustained for 30 seconds to 2 minutes (steep punchy climbs, bridging attacks).
3. **Neuromuscular Power (Zone 7, > 150% FTP)**: Phosphagen / ATP-CP all-out sprints sustained for under 15–20 seconds.

Under the current 5-zone system, all suprathreshold efforts (> 105% FTP) are collapsed into a generic "Zone 5 (Anaerobic)", obliterating the athlete's ability to monitor, analyze, or review VO2 Max versus sprint intensity in real-time or post-workout.

---

## 2. Gap Analysis & Existing Codebase State

A detailed forensic survey across the codebase revealed the specific touchpoints where 5-zone assumptions are hardcoded:

### 2.1 Settings Storage (`SettingsDataStore.kt` & `SettingsDataStoreJavaHelper.kt`)
- `SettingsDataStore.Zone` enum is strictly restricted to `ZONE_1`, `ZONE_2`, `ZONE_3`, `ZONE_4` (representing upper bounds for a 5-zone model).
- Preference keys for `PWR_BIKE` only extend to `PWR_BIKE_ZONE4_MAX`.
- `getDefaultValue()` only returns 4 boundaries for `PWR_BIKE` (150W, 200W, 250W, 300W).
- `SettingsDataStoreJavaHelper.getZoneMax()` only maps indices `1..4`.

### 2.2 Settings UI (`ZonesSettingsActivity.kt`)
- `ZoneProfileState` hardcodes `(z1, z2, z3, z4)`.
- `ZoneSettingsContent` statically composes 5 `ZoneRow` components for all profiles.
- `previewZone` selector chips only render `Z1..Z5`.
- Colors in `ZoneRow` only use `R.color.zone_1` through `R.color.zone_5`.

### 2.3 Domain Models & Telemetry Mathematics (`ZoneDistributionModels.kt` & `TelemetryZoneMath.kt`)
- `PowerZoneThresholds` defines only `(z1Max, z2Max, z3Max, z4Max)`.
- `TelemetryZoneMath.calculatePowerZoneBands()` maps `Triple`s for 5 zones and uses `ZONE_COLORS` (5 elements).
- `TelemetryZoneMath.determinePowerZone()` only branches to 5 zones.
- `TelemetryZoneMath.loadPowerThresholds()` only reads 4 boundaries.
- `BatterySaverModel.kt.calculateZoneIndex()` only checks 4 boundaries.

### 2.4 Tracking Cockpit Tiles & Theme (`TrackingViewModel.kt` & `Color.kt`)
- `TrackingViewModel.zoneColors` has 5 entries.
- `TrackingViewModel.calculateZoneColor()` resolves up to `zoneColors[4]`.
- `Color.kt` / `color.xml` define `Zone1` through `Zone5`. `Zone6` and `Zone7` tokens are missing.

### 2.5 Aftermath Analytics & Histograms (`ZoneDistributionCalculator.kt` & `TelemetryHistogramCalculator.kt`)
- `ZoneDistributionCalculator.calculatePowerDistribution()` initializes `LongArray(5)`.
- `TelemetryHistogramCalculator.calculatePowerHistogram()` maps to 5 zone indices and coerces into `0..4`.
- Note: `ZoneDistributionColumnChart.kt` is already fully dynamic: it iterates over `distribution.entries`, so once 7 entries are supplied, it will automatically render 7 columns with equal weight.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

### 3.1 Trace & History
1. **Original Requirement ID & Target**: Amends `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*, Sprint 2026-40.5, ATT-1390). Targets `SettingsDataStore.kt`, `ZonesSettingsActivity.kt`, `TelemetryZoneMath.kt`, `ZoneDistributionModels.kt`, `ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`, `Color.kt`, and `res/values*/strings.xml`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`): Initial introduction of cycling power 5-zone distribution in Aftermath, designed to match `REQ-UI-202` (Heart Rate 5-Zone Distribution).
   - Sprint 2026-40.8 (`ATT-1839`, `ATT-1845`): Added telemetry metric zone bands and fine-grained histograms with 5 zones.
   - Sprint 2026-40.11 (`ATT-2031`): Standardized threshold loading in `TelemetryZoneMath`.
3. **Root Reason for Existing Formulation (The "Fence")**:
   When power analytics were initially introduced in `ATT-1390`, the system reused the exact same 5-zone architecture from Heart Rate (`REQ-UI-202`) to expedite delivery and minimize architectural divergence across `ZoneDistributionModels.kt` and `ZonesSettingsActivity.kt`.
4. **Preservation of Core Invariants**:
   - **Heart Rate Scheme Unbroken**: `HR_RUN` and `HR_BIKE` MUST retain their canonical 5-zone scheme with 4 upper boundaries (`z1Max` through `z4Max`), 5 distribution columns, and `TTColor.Zone1` through `TTColor.Zone5`.
   - **Non-Destructive Dynamic Migration**: Existing installations with configured power thresholds (1..4) must not crash or lose their settings; `PWR_BIKE_ZONE5_MAX` and `PWR_BIKE_ZONE6_MAX` must default dynamically and proportionally from the athlete's existing `ZONE4_MAX` (see Section 4).
   - **Single-Thread SQLite Confinement & Async IO**: Telemetry loading in `WorkoutRepository` remains confined to `Dispatchers.IO`.
   - **9-Language Localization Parity**: All newly introduced labels for Zone 6 and Zone 7 must maintain 100% parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 4. Deep-Dive Scrutiny & Invariant Verification (Gate 1 Auditor Refinement)

### 4.1 Proportional Dynamic Migration Logic for Existing Thresholds
Hardcoding static defaults (e.g. 350W / 450W) for missing `ZONE5_MAX` / `ZONE6_MAX` is unacceptable for athletes with lower baseline FTPs (e.g. 150W or 200W). Instead, the system implements dynamic proportional derivation based on the athlete's stored `PWR_BIKE_ZONE4_MAX`:
- In Coggan's framework, Zone 4 represents Lactate Threshold ($91\% \dots 105\%$ FTP), where `ZONE4_MAX` represents the upper boundary of threshold ($105\%$ FTP).
- If `PWR_BIKE_ZONE5_MAX` or `PWR_BIKE_ZONE6_MAX` is missing in preferences, but `PWR_BIKE_ZONE4_MAX` exists:
  $$\text{Estimated FTP} = \max\left(50, \text{round}\left(\frac{\text{z4Max}}{1.05}\right)\right)$$
  $$\text{Default z5Max (VO2 Max, 120\% FTP)} = \max\left(\text{z4Max} + 10, \text{round}(\text{Estimated FTP} \times 1.20)\right)$$
  $$\text{Default z6Max (Anaerobic Capacity, 150\% FTP)} = \max\left(\text{z5Max} + 10, \text{round}(\text{Estimated FTP} \times 1.50)\right)$$
- If `PWR_BIKE_ZONE4_MAX` is also absent (fresh application install), canonical 300W FTP defaults are used:
  $\text{Z1}=150\text{W}, \text{Z2}=200\text{W}, \text{Z3}=250\text{W}, \text{Z4}=300\text{W}, \text{Z5}=350\text{W}, \text{Z6}=450\text{W}$.
- **Atomic Transaction Guarantee**: The migration check and preference derivation are executed inside a single atomic `dataStore.edit { prefs -> ... }` block, preventing partial state writes or corruption if interrupted.

### 4.2 Database Schema & Historical Workout Record Architecture
An audit of `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, and `WorkoutRepository.kt` verifies:
- `WorkoutSummaries` does **not** store pre-aggregated zone distributions or fixed-size 5-element arrays in SQLite.
- `WorkoutRepository.getPowerZoneDistribution(workoutId)` computes zone distributions **on-the-fly** from raw point-by-point power samples (`SensorType.POWER.name` and sample timestamps in `WorkoutSamplesDatabaseManager`).
- When viewing historical workouts (from 2024, 2025, or earlier), the system queries the raw historical watt samples and aggregates them dynamically against the athlete's configured thresholds.
- Therefore, **no database table migration, column additions, or `zone_model_version` schema mutations** are required in SQLite. The database schema remains 100% clean and stable.
- **Dynamic Chart Adaptation**: `ZoneDistributionColumnChart.kt` dynamically inspects `distribution.entries.size` and renders each column with `Modifier.weight(1f)`. Whether supplied with 5 entries (Heart Rate) or 7 entries (Power), it adapts cleanly with zero hardcoded index assumptions and zero `ArrayIndexOutOfBoundsException` risk.

### 4.3 Export Format & External Data Compatibility Audit
An exhaustive audit of `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/` (`BaseExporter`, `ExportWorker`, `ExportManager`, TCX, GPX, FIT, CSV, and JSON pipelines) confirmed:
- Export engines serialize raw sample telemetry (`watts`, `bpm`, timestamps, speed, lat/lng) and session summary statistics (`avgPower`, `maxPower`, `workKj`).
- Exporters do **not** write internal application zone indices or zone counts.
- External analytics platforms (Strava, TrainingPeaks, intervals.icu) recalculate zones using their own athlete profiles from raw wattage.
- Therefore, migrating to 7 power zones introduces zero schema regressions or breaking changes to exported workout files.

### 4.4 Strict Monotonic Safety Guardrails at Storage & UI Layers
To prevent UI glitches, division-by-zero, or inverted telemetry classifications:
- `SettingsDataStore`: Methods saving power zone boundaries enforce strict monotonic ascending order:
  $$0 < z1 < z2 < z3 < z4 < z5 < z6$$
  If invalid thresholds are passed, the save operation rejects the update or clamps values to guarantee $z_n \ge z_{n-1} + 1$.
- `ZonesSettingsActivity`: Linked boundary input validation automatically updates adjacent boundaries when an athlete increments or decrements a value, maintaining $z_n \ge z_{n-1} + 1$ interactively.
- **Worst-Case Boundary Test Verification**: Test specifications will explicitly mandate unit tests covering edge-case athletic profiles (e.g. ultra-low FTP of 50W–80W, ultra-high FTP of 500W–600W, and zero/negative power samples) to assert strictly positive, non-overlapping intervals.

### 4.5 Java Interoperability & Call Site Audit
An audit of all occurrences of `SettingsDataStoreJavaHelper.getZoneMax()` revealed:
- Zero callers in legacy Java modules.
- All 4 call sites are in modern Kotlin components (`WorkoutRepository.kt`, `TrackingViewModel.kt`, `TelemetryZoneMath.kt`, `BatterySaverModel.kt`).
- `SettingsDataStoreJavaHelper.getZoneMax()` will be cleanly extended to handle `zoneIndex == 5` and `zoneIndex == 6` without risk of `ArrayIndexOutOfBoundsException`.

### 4.6 Localization Fallback Strategy
- All 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) will define `zone_6_label` and `zone_7_label`.
- If an unknown locale or resource resolution occurs, `ZoneDistributionCalculator` and `ZoneColumnItem` gracefully fall back to programmatic canonical string `Z$zoneIndex` ("Z6", "Z7"), guaranteeing zero crashes.

---

## 5. Scope Bounding & Invariants

### In-Scope:
1. **Color Tokens**: Add `Zone6` (`#4E342E`, Dark Chestnut Brown) and `Zone7` (`#000000`, Black) to `TTColor` in `Color.kt` and `res/values/color.xml`.
2. **DataStore Expansion & Dynamic Migration**:
   - Add `PWR_BIKE_ZONE5_MAX` and `PWR_BIKE_ZONE6_MAX` preference keys and flows to `SettingsDataStore.kt`.
   - Extend `Zone` enum with `ZONE_5` and `ZONE_6`.
   - Implement proportional dynamic defaults deriving missing Z5/Z6 thresholds from stored `ZONE4_MAX` within an atomic `dataStore.edit` transaction.
3. **Synchronous Java Helper**: Extend `SettingsDataStoreJavaHelper.getZoneMax()` to support indices `5` and `6`.
4. **Threshold Models**: Update `PowerZoneThresholds` to encapsulate 6 boundaries (`z1Max..z6Max`).
5. **Math & Histogram Engines**: Update `TelemetryZoneMath`, `ZoneDistributionCalculator`, and `TelemetryHistogramCalculator` to compute 7-zone power bands, distributions, and histograms.
6. **Cockpit HUD Tiles**: Update `TrackingViewModel` and `BatterySaverModel` to resolve power zones `1..7` with matching colors.
7. **Settings UI**: Update `ZonesSettingsActivity` to dynamically display 5 boundary cards for HR and 7 boundary cards for Power, with 7 preview chips and linked boundary validation.
8. **Localization**: Add `zone_6_label` and `zone_7_label` across all 9 language resources (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### Out-of-Scope (Deferred to Downstream Tickets):
- Automatic calculation of zone boundaries from FTP (`ATT-3188`).
- Direct navigation to zone settings upon pairing new power meter (`ATT-3189`).
- Horizontal stacked distribution bar display mode (`ATT-3390`).
- Slope shading palette extension (`ATT-3391`).

---

## 6. Architectural Solution & Component Breakdown

```
┌────────────────────────────────────────────────────────┐
│ SettingsDataStore & JavaHelper                         │
│ • HR_RUN / HR_BIKE: 4 boundaries (Zones 1..5)          │
│ • PWR_BIKE: 6 boundaries (Zones 1..7: Z1..Z6 max)       │
│ • Proportional Dynamic Migration from existing Z4 Max  │
│ • Atomic dataStore.edit transaction safety             │
│ • Strict Monotonic Validation: z1 < z2 < ... < z6      │
└──────────┬───────────────────────────────┬─────────────┘
           │                               │
           ▼                               ▼
┌─────────────────────────┐     ┌────────────────────────┐
│ Settings UI             │     │ Real-Time Cockpit HUD  │
│ (ZonesSettingsActivity) │     │ (TrackingViewModel)    │
│ • Dynamic 5 vs 7 cards  │     │ • Resolves Z1..Z7      │
│ • Linked min/max inputs │     │ • TTColor.Zone1..Zone7 │
│ • 5 vs 7 preview chips  │     └────────────────────────┘
└─────────────────────────┘                │
           │                               │
           ▼                               ▼
┌────────────────────────────────────────────────────────┐
│ Post-Workout Aftermath Analytics                       │
│ • PowerZoneThresholds(z1..z6)                          │
│ • ZoneDistributionCalculator: 7 columns                │
│ • TelemetryHistogramCalculator: 7 shaded zone bands    │
│ • ZoneDistributionColumnChart: Auto-scales 7 columns   │
│ • Dynamic computation from raw samples (Zero DB change)│
│ • Raw Exporter Formats: Unchanged & 100% compatible    │
└────────────────────────────────────────────────────────┘
```

---

## 7. Next Steps (Stage 2 Transition)

1. Update sub-task `ATT-3595` Description with this revised Stage 1 Analysis.
2. Transition `ATT-3595` to `In Überprüfung`.
3. Re-run Gate 1 review audit via `review_agent.py`.
4. Upon approval, transition `ATT-3595` to `Erledigt` via `freigabe` and proceed to Stage 2 (`[Req & Test Spec]`).
