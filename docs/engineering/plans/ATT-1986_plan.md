# Stage 3: Implementation Plan - ATT-1986: [Feature] [Aftermath/Settings] Independent X-Axis Domain Settings for Elevation Profile (Distance) vs. Telemetry Graphs (Time)

**Ticket**: [ATT-1986](https://rainerblind.atlassian.net/browse/ATT-1986)  
**Sub-task**: [ATT-2002](https://rainerblind.atlassian.net/browse/ATT-2002) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-233`  
**Test Mapping**: `TST-UI-192`  
**Branch**: `feature/ATT-1986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In post-workout visual analytics, athletes interpret topographic terrain differently from physiological and exertion telemetry:
1. **Topography (Elevation Profile)** is inherently geographic: athletes think of climbs, descents, and mountain passes in terms of **Distance** (e.g. "the 4 km climb starting at km 12"). Viewing elevation purely over time distorts the terrain slope depending on how fast or slow the athlete was traveling.
2. **Physiological & Exertion Telemetry (Heart Rate, Speed/Pace, Power)** is inherently temporal: athletes evaluate training load, pacing, fatigue, cardiac drift, and zone intervals in terms of **Time** (e.g. "a 10-minute threshold interval", "holding 280 W for 20 minutes", "heart rate drift over 90 minutes").

Currently, a single global setting (`profileXAxisDomain`) in `TuningPreferencesDataStore.kt` controls both chart types simultaneously. Athletes need independent configurability so that Elevation defaults to **Distance** (`ProfileXAxisDomain.DISTANCE`) while Telemetry Graphs (Heart Rate, Speed/Pace, Power) default to **Time** (`ProfileXAxisDomain.TIME`), with full freedom to customize each in Expert Settings.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-233` (*Aftermath/Settings: Independent X-Axis Domain Settings for Elevation Profile (Distance) vs. Telemetry Graphs (Time)*)
  - Decouple `profileXAxisDomain` into `elevationXAxisDomain` (default: `DISTANCE`) and `telemetryXAxisDomain` (default: `TIME`) in `TuningPreferencesDataStore.kt`.
  - Add independent UI controls in `AdvancedTuningDialog.kt` and format both in `AdvancedTuningAccordion.kt`.
  - Provide 9-language localization parity across all supported application locales.
  - Distribute domains independently in `MapDetailLayout.kt` and resolve cross-domain cursor rendering in `TelemetryMetricGraph.kt`.
* **Test Mapping**: `TST-UI-192` (*Independent X-Axis Domain Settings & Cross-Domain Scrubbing Verification*)
  - Unit tests in `ProfileXAxisDomainTest.kt`, `AdvancedTuningVisualContractTest.kt`, and `AftermathTuningLocalizationTest.kt`.
  - Full clean-room test suite regression.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Backward Compatibility & Defensive Migration**: If `tuning_elevation_x_axis_domain` is uninitialized, the system checks legacy `tuning_profile_x_axis_domain` before falling back to `DISTANCE`. `telemetryXAxisDomain` defaults safely to `TIME`. `TuningConfig.profileXAxisDomain` remains accessible as a deprecated delegate property.
3. **Cross-Domain Scrubbing Invariant**: `selectedDistance` remains in route meters across all charts and the map. Scrubbing any chart accurately highlights the corresponding sample point on all other charts.
4. **Padding & Alignment Invariant**: Horizontal padding parity (`startPaddingPx = 50.dp, endPaddingPx = 25.dp`) remains strictly preserved across `ElevationProfile` and `TelemetryMetricGraph`.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.
7. **Programmatic Pre-Check Gate**: Gate 3 verification via `tools/jira_util.py check-gate ATT-2002` must pass before any production code edits.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Localization Resources (`app/src/main/res/values*/strings.xml`)
* **Additions**:
  - `tuning_telemetry_x_axis_title`:
    - EN: "Telemetry Graphs X-Axis"
    - DE: "Telemetriegraphen X-Achse"
    - ES: "Eje X de gráficos de telemetría"
    - FR: "Axe X des graphiques de télémétrie"
    - IT: "Asse X dei grafici di telemetria"
    - JA: "テレメトリグラフX軸"
    - NL: "Telemetriegrafieken X-as"
    - PL: "Oś X wykresów telemetrycznych"
    - PT: "Eixo X dos gráficos de telemetria"
  - `tuning_telemetry_x_axis_desc`:
    - EN: "Select horizontal axis domain for telemetry graphs (Heart Rate, Speed/Pace, Power)."
    - DE: "Horizontale Achsendomäne für Telemetriegraphen (Herzfrequenz, Tempo/Geschwindigkeit, Leistung) wählen."
    - ES: "Seleccione el dominio del eje horizontal para los gráficos de telemetría (frecuencia cardíaca, ritmo/velocidad, potencia)."
    - FR: "Sélectionnez le domaine de l'axe horizontal pour les graphiques de télémétrie (fréquence cardiaque, allure/vitesse, puissance)."
    - IT: "Seleziona il dominio dell'asse orizzontale per i grafici di telemetria (frequenza cardiaca, passo/velocità, potenza)."
    - JA: "テレメトリグラフ（心拍数、ペース/速度、パワー）の水平軸ドメインを選択します。"
    - NL: "Selecteer het horizontale asdomein voor telemetriegrafieken (hartslag, tempo/snelheid, vermogen)."
    - PL: "Wybierz domenę osi poziomej dla wykresów telemetrycznych (tętno, tempo/prędkość, moc)."
    - PT: "Selecione o domínio do eixo horizontal para os gráficos de telemetria (frequência cardíaca, ritmo/velocidade, potência)."

### Component 2: DataStore & Domain Model (`TuningPreferencesDataStore.kt`)
* **Changes**:
  - In `TuningPreferencesDefaults`:
    ```kotlin
    val ELEVATION_X_AXIS_DOMAIN = ProfileXAxisDomain.DISTANCE
    val TELEMETRY_X_AXIS_DOMAIN = ProfileXAxisDomain.TIME
    val PROFILE_X_AXIS_DOMAIN = ELEVATION_X_AXIS_DOMAIN
    ```
  - In `TuningConfig`:
    ```kotlin
    val elevationXAxisDomain: ProfileXAxisDomain = TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN,
    val telemetryXAxisDomain: ProfileXAxisDomain = TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN,
    @Deprecated("Use elevationXAxisDomain or telemetryXAxisDomain")
    val profileXAxisDomain: ProfileXAxisDomain = elevationXAxisDomain,
    ```
  - In `TuningPreferencesDataStore`:
    ```kotlin
    val KEY_ELEVATION_X_AXIS_DOMAIN: Preferences.Key<String> = stringPreferencesKey("tuning_elevation_x_axis_domain")
    val KEY_TELEMETRY_X_AXIS_DOMAIN: Preferences.Key<String> = stringPreferencesKey("tuning_telemetry_x_axis_domain")
    ```
  - In `tuningConfigFlow`:
    - Read `rawElevation = prefs[KEY_ELEVATION_X_AXIS_DOMAIN] ?: prefs[KEY_PROFILE_X_AXIS_DOMAIN]`
    - Read `rawTelemetry = prefs[KEY_TELEMETRY_X_AXIS_DOMAIN]`
  - In `saveTuningConfig`:
    - Write `prefs[KEY_ELEVATION_X_AXIS_DOMAIN] = config.elevationXAxisDomain.name`
    - Write `prefs[KEY_TELEMETRY_X_AXIS_DOMAIN] = config.telemetryXAxisDomain.name`
    - Write `prefs[KEY_PROFILE_X_AXIS_DOMAIN] = config.elevationXAxisDomain.name`

### Component 3: Subtitle Formatter (`AdvancedTuningAccordion.kt`)
* **Changes**:
  - Update `formatAftermathSubtitle`:
    ```kotlin
    fun formatAftermathSubtitle(
        elevationDomain: ProfileXAxisDomain,
        telemetryDomain: ProfileXAxisDomain,
        context: Context
    ): String {
        val elevStr = if (elevationDomain == ProfileXAxisDomain.DISTANCE) {
            context.getString(R.string.tuning_profile_x_axis_distance)
        } else {
            context.getString(R.string.tuning_profile_x_axis_time)
        }
        val telemStr = if (telemetryDomain == ProfileXAxisDomain.DISTANCE) {
            context.getString(R.string.tuning_profile_x_axis_distance)
        } else {
            context.getString(R.string.tuning_profile_x_axis_time)
        }
        val elevPrefix = context.getString(R.string.tuning_profile_x_axis_title)
        val telemPrefix = context.getString(R.string.tuning_telemetry_x_axis_title)
        return "$elevPrefix: $elevStr | $telemPrefix: $telemStr"
    }

    fun formatAftermathSubtitle(
        domain: ProfileXAxisDomain,
        context: Context
    ): String = formatAftermathSubtitle(domain, domain, context)
    ```

### Component 4: Expert Settings Dialog (`AdvancedTuningDialog.kt`)
* **Changes**:
  - Track `elevationXAxisDomain` and `telemetryXAxisDomain` state variables.
  - In `AftermathAnalysisSection`:
    - Render Elevation Profile X-Axis selector with FilterChips `[Distance (Default) | Time]`.
    - Render Telemetry Graphs X-Axis selector with FilterChips `[Time (Default) | Distance]`.
  - Update factory reset handler to restore `ELEVATION_X_AXIS_DOMAIN` and `TELEMETRY_X_AXIS_DOMAIN`.

### Component 5: Cursor Range Check Fix (`TelemetryMetricGraph.kt`)
* **Changes**:
  - Fix line 791: validate `cursorDistSpan in 0.0..totalSpan` and `cursorDistSpan in startDist..(startDist + visibleSpan)` instead of `currentDistance in 0.0..totalSpan`.

### Component 6: Chart Layout Integration (`MapDetailLayout.kt`)
* **Changes**:
  - Feed `elevationXAxisDomain = tuningConfig.elevationXAxisDomain` into `ElevationProfile`.
  - Feed `telemetryXAxisDomain = tuningConfig.telemetryXAxisDomain` into all stacked `TelemetryMetricGraph` instances.
  - Calculate `telemetryStartDist` and `telemetryTotalSpan` appropriately for multi-chart lockstep.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 status of `ATT-2002` via `python3 tools/jira_util.py check-gate ATT-2002`.

### Step 2: 9-Language Localization Additions
* Add `tuning_telemetry_x_axis_title` and `tuning_telemetry_x_axis_desc` across `values/strings.xml` and all 8 `values-*/strings.xml`.

### Step 3: DataStore & Domain Model Decoupling
* Update `TuningPreferencesDefaults`, `TuningConfig`, and `TuningPreferencesDataStore` in `TuningPreferencesDataStore.kt`.

### Step 4: Settings UI & Accordion Subtitle
* Update `AdvancedTuningAccordion.kt` (`formatAftermathSubtitle`).
* Update `AdvancedTuningDialog.kt` (`AftermathAnalysisSection`).

### Step 5: TelemetryMetricGraph & MapDetailLayout Synchronization
* Update `TelemetryMetricGraph.kt` cursor range check.
* Update `MapDetailLayout.kt` to supply independent domains to `ElevationProfile` and `TelemetryMetricGraph`.

### Step 6: Unit Test Modernization & Verification
* Update `ProfileXAxisDomainTest.kt`.
* Update `AftermathTuningLocalizationTest.kt`.
* Execute targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomainTest" --tests "com.atrainingtracker.trainingtracker.settings.AftermathTuningLocalizationTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests during construction, followed by clean-room full test suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-1986` allows complete rollback via `git reset --hard origin/sprint/2026-40.10` without impacting integration branches.
