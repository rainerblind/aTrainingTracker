# Stage 2: Requirement & Test Specification - ATT-1986: [Feature] [Aftermath/Settings] Independent X-Axis Domain Settings for Elevation Profile (Distance) vs. Telemetry Graphs (Time)

**Ticket**: [ATT-1986](https://rainerblind.atlassian.net/browse/ATT-1986)  
**Sub-task**: [ATT-2001](https://rainerblind.atlassian.net/browse/ATT-2001) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-233`  
**Test Mapping**: `TST-UI-192`  
**Branch**: `feature/ATT-1986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification

### 1.1 Requirement Statement (`REQ-UI-233`)

The system SHALL provide independent horizontal X-axis domain configuration for the elevation profile and continuous telemetry metric graphs in Advanced Settings (`TuningPreferencesDataStore.kt` & `AdvancedTuningDialog.kt`), defaulting Elevation to Distance and Telemetry to Time, while preserving synchronized cross-domain scrubbing and zoom lockstep across `MapDetailLayout.kt` (ATT-1986):

1. **DataStore Preferences Decoupling (`TuningPreferencesDataStore.kt`)**:
   - The system SHALL decouple `profileXAxisDomain` into two distinct persisted preferences:
     - `val elevationXAxisDomain: ProfileXAxisDomain` with factory default `ProfileXAxisDomain.DISTANCE`. Persisted to key `tuning_elevation_x_axis_domain`.
     - `val telemetryXAxisDomain: ProfileXAxisDomain` with factory default `ProfileXAxisDomain.TIME`. Persisted to key `tuning_telemetry_x_axis_domain`.
   - *Backward Compatibility & Migration*: If `tuning_elevation_x_axis_domain` is uninitialized, the system SHALL check legacy `tuning_profile_x_axis_domain`, falling back to `DISTANCE`. If `tuning_telemetry_x_axis_domain` is uninitialized, it SHALL default to `TIME`. `TuningConfig` SHALL provide a deprecated `profileXAxisDomain` accessor returning `elevationXAxisDomain` to preserve binary and source compatibility.
   - Factory reset (`resetToDefaults()`) SHALL restore `elevationXAxisDomain = DISTANCE` and `telemetryXAxisDomain = TIME`.

2. **Expert Settings UI Selector Controls (`AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)**:
   - In `AftermathAnalysisSection`, the dialog SHALL render two independent selector rows:
     - **Elevation Profile X-Axis**: Title `R.string.tuning_profile_x_axis_title`, description `R.string.tuning_profile_x_axis_desc`, and FilterChips `[Distance (Default) | Time]`.
     - **Telemetry Graphs X-Axis**: Title `R.string.tuning_telemetry_x_axis_title`, description `R.string.tuning_telemetry_x_axis_desc`, and FilterChips `[Time (Default) | Distance]`.
   - The Aftermath accordion subtitle (`TuningSubtitleFormatter.formatAftermathSubtitle`) SHALL summarize both configured domains (e.g. "Höhenprofil: Distanz, Telemetrie: Zeit").
   - Display strings SHALL be localized across all 9 application locales.

3. **Independent Domain Distribution in MapDetailLayout (`MapDetailLayout.kt`)**:
   - `ElevationProfile` SHALL be supplied with `xAxisDomain = tuningConfig.elevationXAxisDomain`.
   - Stacked `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, Power) SHALL be supplied with `xAxisDomain = tuningConfig.telemetryXAxisDomain`.

4. **Synchronized Cross-Domain Scrubbing & Cursor Rendering (`MapDetailLayout.kt`, `TelemetryMetricGraph.kt`, `ElevationProfile.kt`)**:
   - `selectedDistance` across all charts and the map marker SHALL be maintained in route meters.
   - When scrubbing either chart, touch coordinates SHALL resolve to the nearest `PathPoint` and dispatch its route distance.
   - When rendering the scrubbing cursor in `TelemetryMetricGraph.kt`, the component SHALL convert `currentDistance` to temporal coordinates when in Time domain and evaluate `cursorDistSpan in 0.0..totalSpan` and `cursorDistSpan in startDist..(startDist + visibleSpan)`.

5. **Preservation of Core Invariants**:
   - Directional gesture disambiguation (`REQ-UI-226`) and vertical scroll freedom remain 100% intact.
   - Plot area padding parity (`start = 50.dp, end = 25.dp`) remains strictly aligned.
   - 9-language localization parity across all supported application locales.
   - Zero database schema mutations.

---

### 1.2 Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-233`, extending and refining `REQ-UI-201` (*Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain*) and `REQ-UI-206` (*Continuous Telemetry Metric Graphs*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**: Sprint 2026-40.5 (`ATT-1391`, Commit `e7a6a273`) and Sprint 2026-40.6 (`ATT-1740`, Commit `061a4b35`).
* **Root Reason for Existing Formulation**: When telemetry curves were created in ATT-1740, they reused `profileXAxisDomain` from ATT-1391 for simplicity. However, athletic mental models dictate that terrain is inherently spatial (Distance), while metabolic and exertion metrics are inherently temporal (Time). Coupling them into a single setting forced an unwanted compromise.
* **Preservation of Core Invariants**: Full zoom/pan mathematics, cross-chart scrubbing synchronization, padding parity (50dp/25dp), 9-language localization parity, and zero SQLite schema mutations are 100% strictly preserved.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Factory Defaults & Reset)**:
  * *Given* a fresh application install or executing "Auf Werkseinstellungen zurücksetzen",
  * *When* inspecting `TuningConfig`,
  * *Then* `elevationXAxisDomain` SHALL equal `ProfileXAxisDomain.DISTANCE` and `telemetryXAxisDomain` SHALL equal `ProfileXAxisDomain.TIME`.

* **Criterion 2 (Independent Configuration in Expert Settings)**:
  * *Given* an athlete in `AdvancedTuningDialog` (Aftermath section),
  * *When* selecting `Time` for Elevation and `Distance` for Telemetry (or any other combination),
  * *Then* both preferences SHALL persist independently to DataStore, and the subtitle SHALL reflect both values without interference.

* **Criterion 3 (Synchronized Cross-Domain Scrubbing)**:
  * *Given* `ElevationProfile` rendered in Distance domain and `TelemetryMetricGraph` rendered in Time domain,
  * *When* touching or scrubbing either chart,
  * *Then* both charts and the map marker SHALL synchronously position at the matching spatial/temporal track sample without cursor disappearance or clipping.

* **Criterion 4 (Legacy Preferences Migration)**:
  * *Given* an installation with legacy `KEY_PROFILE_X_AXIS_DOMAIN = "TIME"`,
  * *When* `tuningConfigFlow` emits,
  * *Then* `elevationXAxisDomain` SHALL resolve to `ProfileXAxisDomain.TIME`, and `telemetryXAxisDomain` SHALL default to `ProfileXAxisDomain.TIME`.

---

## 2. Test Specification (`TST-UI-192`)

### 2.1 Test Case 1: DataStore Preferences Decoupling & Fallback (`ProfileXAxisDomainTest.kt`)
* **Objective**: Validate factory defaults, independent persistence, legacy migration, and factory reset.
* **Assertions**:
  - `TuningConfig().elevationXAxisDomain == ProfileXAxisDomain.DISTANCE`
  - `TuningConfig().telemetryXAxisDomain == ProfileXAxisDomain.TIME`
  - Modifying `elevationXAxisDomain` to `TIME` while keeping `telemetryXAxisDomain` at `TIME` persists and emits both independently.
  - Modifying `telemetryXAxisDomain` to `DISTANCE` while keeping `elevationXAxisDomain` at `DISTANCE` persists and emits both independently.
  - Legacy DataStore key `tuning_profile_x_axis_domain` cleanly migrates into `elevationXAxisDomain`.
  - Factory reset restores both default constants.

### 2.2 Test Case 2: Expert Settings UI & Accordion Subtitle (`AdvancedTuningVisualContractTest.kt`)
* **Objective**: Validate UI contract for two FilterChip rows in `AftermathAnalysisSection` and subtitle formatting.
* **Assertions**:
  - `AftermathAnalysisSection` renders two selector rows with titles and FilterChips for Distance and Time.
  - `TuningSubtitleFormatter.formatAftermathSubtitle` formats both domains (e.g. contains both domain labels).

### 2.3 Test Case 3: 9-Language Localization Parity (`AftermathTuningLocalizationTest.kt`)
* **Objective**: Audit resource parity across all 9 supported locales:
  - `tuning_telemetry_x_axis_title`
  - `tuning_telemetry_x_axis_desc`
* **Assertions**: Keys exist and are non-blank across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### 2.4 Test Case 4: Cross-Domain Scrubbing Range Check Validation (`TelemetryMetricGraphRangeTest.kt`)
* **Objective**: Verify `TelemetryMetricGraph` validates cursor range against temporal coordinates (`cursorDistSpan in 0.0..totalSpan`), eliminating the magnitude mismatch bug when distance (meters) exceeds duration (seconds).

### 2.5 Test Case 5: Clean-Room Full Suite Regression
* **Objective**: Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Requirement | Test Spec | Verification File | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-233.1` | `TST-UI-192.1` | `ProfileXAxisDomainTest.kt` | Unit Test | Proposed |
| `REQ-UI-233.2` | `TST-UI-192.2` | `AdvancedTuningVisualContractTest.kt` | Contract Test | Proposed |
| `REQ-UI-233.2` | `TST-UI-192.3` | `AftermathTuningLocalizationTest.kt` | XML Parity Test | Proposed |
| `REQ-UI-233.3` | `TST-UI-192.4` | `TelemetryMetricGraphRangeTest.kt` | Unit Test | Proposed |
| `REQ-PRO-022` | `TST-PRO-015` | `ATT-1986_test_spec.md` | Python Script (`verify_requirement_governance.py`) | Proposed |
| `REQ-PRO-001` | `TST-UI-192.5` | `./gradlew testDebugUnitTest` | Full Suite Regression | Proposed |
