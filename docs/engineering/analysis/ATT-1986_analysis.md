# Stage 1 Analysis: ATT-1986 - [Feature] [Aftermath/Settings] Independent X-Axis Domain Settings for Elevation Profile (Distance) vs. Telemetry Graphs (Time)

**Ticket**: [ATT-1986](https://rainerblind.atlassian.net/browse/ATT-1986)  
**Sub-task**: [ATT-2000](https://rainerblind.atlassian.net/browse/ATT-2000) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Branch**: `feature/ATT-1986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In post-workout visual analytics, athletes interpret topographic terrain differently from physiological and exertion telemetry:
1. **Topography (Elevation Profile)** is inherently geographic: athletes think of climbs, descents, and mountain passes in terms of **Distance** (e.g. "the 4 km climb starting at km 12"). Viewing elevation purely over time distorts the terrain slope depending on how fast or slow the athlete was traveling (e.g. a steep climb where speed drops to 8 km/h appears stretched out horizontally, while a fast 60 km/h descent appears artificially compressed).
2. **Physiological & Exertion Telemetry (Heart Rate, Speed/Pace, Power)** is inherently temporal: athletes evaluate training load, pacing, fatigue, cardiac drift, and zone intervals in terms of **Time** (e.g. "a 10-minute threshold interval", "holding 280 W for 20 minutes", "heart rate drift over 90 minutes").

Currently, a single global setting (`profileXAxisDomain`) in `TuningPreferencesDataStore.kt` controls both chart types simultaneously. Athletes need independent configurability so that:
- Elevation Profile defaults to **Distance** (`ProfileXAxisDomain.DISTANCE`),
- Telemetry Graphs (Heart Rate, Speed/Pace, Power) default to **Time** (`ProfileXAxisDomain.TIME`),
- Full freedom is provided in Expert Settings to configure each chart domain independently according to personal preference.

---

## 2. Root Cause Analysis & Architectural Gap Analysis

### 2.1 Preference DataStore & Domain Model (`TuningPreferencesDataStore.kt`)
- `TuningConfig` currently has a single property:
  ```kotlin
  val profileXAxisDomain: ProfileXAxisDomain = TuningPreferencesDefaults.PROFILE_X_AXIS_DOMAIN
  ```
- `TuningPreferencesDataStore` uses a single key:
  ```kotlin
  val KEY_PROFILE_X_AXIS_DOMAIN = stringPreferencesKey("tuning_profile_x_axis_domain")
  ```
- **Architectural Gap**: DataStore needs two separate persisted keys:
  - `KEY_ELEVATION_X_AXIS_DOMAIN` (default: `DISTANCE`)
  - `KEY_TELEMETRY_X_AXIS_DOMAIN` (default: `TIME`)
  - Seamless migration: if legacy `KEY_PROFILE_X_AXIS_DOMAIN` was customized, it can seed `elevationXAxisDomain`, while `telemetryXAxisDomain` defaults to `TIME` unless explicitly configured.

### 2.2 Expert Settings UI (`AdvancedTuningDialog.kt` & `AdvancedTuningAccordion.kt`)
- Under the Aftermath subsection of `AdvancedTuningDialog.kt`, `AftermathAnalysisSection` renders only a single `FilterChip` pair under `R.string.tuning_profile_x_axis_title`.
- **Architectural Gap**: `AftermathAnalysisSection` needs two dedicated selector rows:
  1. **Elevation Profile (Höhenprofil)**: `[Distanz (Standard) | Zeit]`
  2. **Telemetry Graphs (Telemetrie - HF, Tempo, Leistung)**: `[Zeit (Standard) | Distanz]`
- `TuningSubtitleFormatter.formatAftermathSubtitle` must be updated to summarize both domains (e.g. "Höhenprofil: Distanz, Telemetrie: Zeit").
- 9-language localization parity must be provided for new strings (`tuning_telemetry_x_axis_title`, `tuning_telemetry_x_axis_desc`).

### 2.3 Inspection Layout & Scrubbing Synchrony (`MapDetailLayout.kt`)
- Currently, `MapDetailLayout.kt` feeds the same `tuningConfig.profileXAxisDomain` to `ElevationProfile` and all three `TelemetryMetricGraph` instances.
- `selectedDistance` is stored in meters and shared across all charts and the map.
- When scrubbing, each chart maps touch position to the nearest `PathPoint` and emits `activePoint.distance`.
- In `TelemetryMetricGraph.kt` (line 791), a range check bug `if (currentDistance != null && currentDistance in 0.0..totalSpan)` fails when `currentDistance` (meters) exceeds `totalSpan` (seconds) in numeric magnitude. This range check must be validated against `cursorDistSpan` (`nearestPt.timeSec.toDouble()`), ensuring cursor rendering operates reliably in the time domain.
- For zoom and pan navigation (`GlobalTelemetryZoomToolbar`), `ElevationProfile` and `TelemetryMetricGraph` maintain synchronized alignment across their respective domains.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Decouple DataStore persistence into `elevationXAxisDomain` (default: `DISTANCE`) and `telemetryXAxisDomain` (default: `TIME`), preserving backward compatibility with legacy `profileXAxisDomain`.
  * Update `AdvancedTuningDialog.kt` and `AdvancedTuningAccordion.kt` to present two independent selector rows in the Aftermath section with full 9-language localization parity.
  * Update `MapDetailLayout.kt` to supply `elevationXAxisDomain` to `ElevationProfile` and `telemetryXAxisDomain` to `TelemetryMetricGraph` instances.
  * Ensure seamless cross-domain scrubbing and cursor synchronization using `PathPoint(distance, timeSec)` mapping.
  * Fix the `currentDistance` range check in `TelemetryMetricGraph.kt` to evaluate `cursorDistSpan in 0.0..totalSpan`.
  * Add unit tests verifying DataStore defaults, independent mutation, legacy migration, UI rendering, and localization parity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification of SQLite sample database schemas or query operations.
  * No modification of Google Maps route rendering or GPS tracking pipelines.
  * No changes to cockpit active tracking typography or battery saver settings.
  * No alteration of `LapSplitVisualizer` or lap statistics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-201` (*Aftermath: Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain Architecture*) and `REQ-UI-206` (*Aftermath: Continuous Telemetry Metric Graphs with Section Headings & Synchronized Multi-Chart Scrubbing Architecture*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**:
  - `ATT-1391` (Sprint 2026-40.5, Commit `e7a6a273`): Introduced `ProfileXAxisDomain` (`DISTANCE` vs `TIME`) for `ElevationProfile`.
  - `ATT-1740` (Sprint 2026-40.6, Commit `061a4b35`): Introduced continuous metric curves in `TelemetryMetricGraph` reusing the global `profileXAxisDomain`.
  - `ATT-1821` (Sprint 2026-40.7, Commit `0fdd3e4e`): Structured settings into collapsible accordion subsections.
* **Root Reason for Existing Formulation**: When continuous telemetry curves were initially introduced in `ATT-1740`, reusing the existing `profileXAxisDomain` was chosen for simplicity to avoid multiple configuration toggles before user feedback was gathered. However, athletic review confirmed that topography is fundamentally evaluated over distance, whereas sensor metrics (HR, power, pace) are evaluated over elapsed time. Coupling them to a single toggle forced an unnatural compromise.
* **Preservation of Core Invariants**:
  - Distance-based topographic plotting and time-based adaptive tick formatting (`ElevationProfileZoomMath`) remain 100% preserved.
  - Synchronized multi-metric scrubbing cursor parity across all stacked charts and the map marker remains strictly preserved.
  - 100% 9-language localization parity across all supported application locales.
  - Zero database schema mutations.

---

## 5. Proposed Solution Architecture

### 5.1 Preferences DataStore (`TuningPreferencesDataStore.kt`)
```kotlin
object TuningPreferencesDefaults {
    val ELEVATION_X_AXIS_DOMAIN = ProfileXAxisDomain.DISTANCE
    val TELEMETRY_X_AXIS_DOMAIN = ProfileXAxisDomain.TIME
    val PROFILE_X_AXIS_DOMAIN = ELEVATION_X_AXIS_DOMAIN
}

data class TuningConfig(
    val elevationXAxisDomain: ProfileXAxisDomain = TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN,
    val telemetryXAxisDomain: ProfileXAxisDomain = TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN,
    @Deprecated("Use elevationXAxisDomain or telemetryXAxisDomain")
    val profileXAxisDomain: ProfileXAxisDomain = elevationXAxisDomain,
    ...
)
```

### 5.2 Settings UI (`AdvancedTuningDialog.kt` & `AdvancedTuningAccordion.kt`)
In `AftermathAnalysisSection`:
- Row 1: **Elevation Profile X-Axis** with FilterChips `[Distance (Default) | Time]`.
- Row 2: **Telemetry Graphs X-Axis** with FilterChips `[Time (Default) | Distance]`.
- Subtitle: Formatted via `formatAftermathSubtitle(elevationDomain, telemetryDomain, context)`.

### 5.3 Cross-Domain Scrubbing & Chart Alignment (`MapDetailLayout.kt` & `TelemetryMetricGraph.kt`)
- `ElevationProfile`: `xAxisDomain = tuningConfig.elevationXAxisDomain`.
- `TelemetryMetricGraph`: `xAxisDomain = tuningConfig.telemetryXAxisDomain`.
- Cursor positioning: When scrubbing, touch coordinates resolve to the nearest `PathPoint` and publish `distance`. When rendering the cursor, each chart resolves its horizontal position according to its own active domain (`distance` or `timeSec`), maintaining seamless bidirectional lockstep across all charts and the map.

---

## 6. Verification & Gate Governance Strategy

1. **Unit Testing (`ProfileXAxisDomainTest.kt`, `AftermathTuningLocalizationTest.kt`)**:
   - Verify defaults: Elevation = `DISTANCE`, Telemetry = `TIME`.
   - Verify independent configuration persistence and mutation.
   - Verify defensive fallback on unknown or missing DataStore keys.
   - Verify localization parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for new string keys.
2. **Clean-Room Full Suite Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate across all test suites.
3. **ASPICE Gate 1 Audit**:
   - Audit subtask `ATT-2000` via `tools/review_agent.py audit ATT-2000`.
