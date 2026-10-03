# Stage 3: Implementation Plan - ATT-2014: Configurable Minimum Pace Ceiling in Expert Settings (Default 3:00 min/km)

**Ticket**: [ATT-2014](https://rainerblind.atlassian.net/browse/ATT-2014)  
**Sub-task**: [ATT-2104](https://rainerblind.atlassian.net/browse/ATT-2104) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-243` (*Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km*)  
**Test Mapping**: `TST-UI-202` (*Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km Verification*)  
**Branch**: `feature/ATT-2014`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In the Aftermath workout inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapScreen.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), and [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt)), running workouts (`BSportType.RUN`) render a continuous running pace curve ("Tempo").

Currently, [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt) clamps pace data points to a hardcoded lower bound of `1.5` min/km (1:30 min/km = 40 km/h) to prevent division-by-zero during stationary/slow periods:
```kotlin
(secPerUnit / 60.0).coerceIn(1.5, 20.0)
```
and:
```kotlin
val paceMin = (min * 0.95).coerceAtLeast(1.5)
val paceMax = (max * 1.05).coerceAtMost(20.0)
paceMin to paceMax.coerceAtLeast(paceMin + 1.0)
```

When transient GPS velocity spikes occur (common upon GPS acquisition, resume after pause, or multipath reflection under bridges/dense tree canopy), an instantaneous speed spike reaches or exceeds 1.5 min/km. This causes the top Y-axis label to scale to **1:30** (1:30 min/km).
1. **Athletic Invariance**: 1:30 min/km is physiologically impossible for human distance runners (world record marathon pace is ~2:50 min/km; world record 100m sprint velocity sustained over a full km would be ~1:35 min/km). Rendering "1:30" as the graph ceiling damages athletic realism.
2. **Graph Dynamic Range Compression**: With an upper bound of 1:30 against a lower bound of 20:00, the vertical dynamic span is 18.5 min/km. The actual running effort curve (typically 4:30 – 6:30 min/km) is compressed into an unreadable, flat band in the middle of the graph.

Providing a user-configurable minimum pace ceiling in Expert Settings (Section 4: *Aftermath & Analyse*) defaulting to **3:00 min/km** (and ~4:50 min/mi in Imperial) restores athletic realism, protects chart readability against GPS noise spikes, and allows athletes to tailor the dynamic range of their running pace charts.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-243` (*Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km*)
* **Test Mapping**: `TST-UI-202` (*Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km Verification*)
  * `TST-UI-202.1`: Preferences Model & DataStore Tests (`TuningPreferencesDataStorePaceCeilingTest.kt`)
  * `TST-UI-202.2`: Expert Settings UI & Architecture Contract Tests (`AdvancedTuningPaceCeilingContractTest.kt`)
  * `TST-UI-202.3`: Pace Telemetry Graph Clamping & Y-Axis Bounds Tests (`TelemetryMetricGraphPaceCeilingTest.kt`)
  * `TST-UI-202.4`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-202.5`: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Inverted Pace Graph Axis**: Faster pace MUST remain plotted higher and slower pace lower on the Y-axis.
2. **Athletic Formatting**: Pace values MUST continue to be formatted via athletic `mm:ss` representation (`formatPaceMinutes`).
3. **Speed Invariance**: Non-running workouts (cycling speed, hiking) MUST NOT be clamped or bounded by the running pace ceiling.
4. **Zero Unintended Regressions**: All 1,380+ existing test cases must continue to pass cleanly with 100% pass rate.
5. **Standalone Caller Compatibility**: `TelemetryMetricGraph` and `TelemetryMetricUtils.extractMetricValue` must provide default parameter `paceCeilingMinKm = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM` so external callers are unaffected.
6. **Programmatic Gate Check**: Stage 4 construction will not proceed until `python3 tools/jira_util.py check-gate ATT-2104` validates Gate 3 approval.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2014` remains strictly reserved for the human user in `Final Review (Human)`. AI agents must never transition parent tickets to `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningPreferencesDataStore.kt`
* In `TuningPreferencesDefaults`:
  ```kotlin
  const val DEFAULT_PACE_CEILING_MIN_KM = 3.0f
  const val MIN_PACE_CEILING_MIN_KM = 2.0f
  const val MAX_PACE_CEILING_MIN_KM = 6.0f
  ```
* In `TuningConfig`:
  ```kotlin
  val paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
  ```
* In `TuningPreferencesDataStore`:
  * Add key `KEY_PACE_CEILING_MIN_KM = floatPreferencesKey("tuning_pace_ceiling_min_km")` to companion object and `ALL_KEYS`.
  * In `tuningConfigFlow`: read `prefs[KEY_PACE_CEILING_MIN_KM] ?: TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM` clamped to `[MIN_PACE_CEILING_MIN_KM, MAX_PACE_CEILING_MIN_KM]`.
  * In `saveTuningConfig`: clamp `config.paceCeilingMinKm` and persist to `prefs[KEY_PACE_CEILING_MIN_KM]`.
  * In `resetToDefaults`: automatically cleared via `ALL_KEYS` iteration.

### Component 2: `AdvancedTuningDialog.kt`
* In `AdvancedTuningDialog`:
  * Initialize local state:
    ```kotlin
    var paceCeilingMinKm by remember(persistedTuningConfig) {
        mutableFloatStateOf(persistedTuningConfig?.paceCeilingMinKm ?: TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM)
    }
    ```
  * In save block: pass `paceCeilingMinKm` into `TuningConfig(...)`.
  * In reset button click listener: reset `paceCeilingMinKm = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM`.
* In `AftermathAnalysisSection`:
  * Accept parameters:
    ```kotlin
    paceCeilingMinKm: Float,
    onPaceCeilingChange: (Float) -> Unit,
    isMetric: Boolean = remember { TrainingApplication.getUnit() == MyUnits.METRIC }
    ```
  * Render `TuningSliderItem`:
    ```kotlin
    TuningSliderItem(
        title = stringResource(R.string.tuning_pace_ceiling_title),
        valueText = TuningPaceCeilingFormatter.formatPaceCeiling(paceCeilingMinKm, isMetric),
        helperText = stringResource(R.string.tuning_pace_ceiling_desc),
        defaultText = stringResource(
            R.string.tuning_default_format,
            TuningPaceCeilingFormatter.formatPaceCeiling(TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM, isMetric)
        ),
        value = paceCeilingMinKm,
        onValueChange = onPaceCeilingChange,
        valueRange = TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM..TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM,
        steps = 15 // 16 discrete increments of 0.25 min (15 sec): 2:00, 2:15, ..., 6:00
    )
    ```

### Component 3: `TelemetryMetricGraph.kt`
* In `TelemetryMetricUtils.extractMetricValue`:
  ```kotlin
  fun extractMetricValue(
      point: PathPoint,
      metricType: TelemetryMetricType,
      unit: MyUnits,
      paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
  ): Double? {
      ...
      TelemetryMetricType.PACE -> {
          point.speedMps?.takeIf { it >= 0.55 }?.let { mps ->
              val secPerKm = 1000.0 / mps
              val secPerUnit = if (unit == MyUnits.METRIC) {
                  secPerKm
              } else {
                  secPerKm * (BANALService.METER_PER_MILE / 1000.0)
              }
              val effectiveCeiling = if (unit == MyUnits.METRIC) {
                  paceCeilingMinKm.toDouble()
              } else {
                  paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)
              }
              (secPerUnit / 60.0).coerceIn(effectiveCeiling, 20.0)
          }
      }
      ...
  }
  ```
* In `TelemetryMetricGraph`:
  * Add parameter: `paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM`.
  * Pass `paceCeilingMinKm` into `TelemetryMetricUtils.extractMetricValue`.
  * In `remember(validValues, metricType, paceCeilingMinKm)`:
    ```kotlin
    TelemetryMetricType.PACE -> {
        val effectiveCeiling = if (unit == MyUnits.METRIC) {
            paceCeilingMinKm.toDouble()
        } else {
            paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)
        }
        val paceMin = (min * 0.95).coerceAtLeast(effectiveCeiling)
        val paceMax = (max * 1.05).coerceAtMost(20.0)
        paceMin to paceMax.coerceAtLeast(paceMin + 1.0)
    }
    ```

### Component 4: `MapDetailLayout.kt`
* Forward `paceCeilingMinKm = tuningConfig.paceCeilingMinKm` when invoking `TelemetryMetricGraph` for pace/speed.

### Component 5: 9-Language Localization
* Add strings `tuning_pace_ceiling_title` and `tuning_pace_ceiling_desc` to all 9 locales:
  * `values/strings.xml` (EN):
    * `tuning_pace_ceiling_title`: "Minimum Pace Ceiling"
    * `tuning_pace_ceiling_desc`: "Filters GPS noise spikes in the pace chart. Speeds faster than this ceiling are clamped."
  * `values-de/strings.xml` (DE):
    * `tuning_pace_ceiling_title`: "Schnellste Tempo-Grenze"
    * `tuning_pace_ceiling_desc`: "Filtert GPS-Ausreißer im Tempo-Diagramm. Werte schneller als diese Grenze werden abgeschnitten."
  * `values-es/strings.xml` (ES):
    * `tuning_pace_ceiling_title`: "Límite de ritmo más rápido"
    * `tuning_pace_ceiling_desc`: "Filtra anomalías del GPS en el gráfico de ritmo. Los valores más rápidos que este límite se recortan."
  * `values-fr/strings.xml` (FR):
    * `tuning_pace_ceiling_title`: "Allure maximale du graphique"
    * `tuning_pace_ceiling_desc`: "Filtre les anomalies GPS sur le graphique d'allure. Les allures plus rapides que ce seuil sont écrêtées."
  * `values-it/strings.xml` (IT):
    * `tuning_pace_ceiling_title`: "Limite massimo di passo"
    * `tuning_pace_ceiling_desc`: "Filtra i picchi GPS nel grafico del passo. Le andature più veloci di questo limite vengono tagliate."
  * `values-ja/strings.xml` (JA):
    * `tuning_pace_ceiling_title`: "ペース上限（最速値）"
    * `tuning_pace_ceiling_desc`: "ペースグラフのGPS異常値を抑制します。この上限を超える速度はクランプされます。"
  * `values-nl/strings.xml` (NL):
    * `tuning_pace_ceiling_title`: "Maximale tempogrens"
    * `tuning_pace_ceiling_desc`: "Filtert GPS-uitschieters in de tempografiek. Waarden sneller dan deze grens worden afgekapt."
  * `values-pl/strings.xml` (PL):
    * `tuning_pace_ceiling_title`: "Górny limit tempa"
    * `tuning_pace_ceiling_desc`: "Filtruje anomalie GPS na wykresie tempa. Wartości szybsze niż ten limit są obcinane."
  * `values-pt/strings.xml` (PT):
    * `tuning_pace_ceiling_title`: "Limite de ritmo mais rápido"
    * `tuning_pace_ceiling_desc`: "Filtra anomalias de GPS no gráfico de ritmo. Valores mais rápidos que este limite são limitados."

---

## 5. Implementation Steps (Atomic & Sequenced)

### Step 1: DataStore Model & Persistence
* **Files**: [TuningPreferencesDataStore.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt)
* Add `DEFAULT_PACE_CEILING_MIN_KM`, `MIN_PACE_CEILING_MIN_KM`, `MAX_PACE_CEILING_MIN_KM` to `TuningPreferencesDefaults`.
* Add `paceCeilingMinKm` to `TuningConfig`.
* Add `KEY_PACE_CEILING_MIN_KM`, read flow mapping, save function, and reset handling in `TuningPreferencesDataStore`.

### Step 2: Formatter & Settings UI
* **Files**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningPaceCeilingFormatter.kt`
  * [AdvancedTuningDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt)
* Implement `TuningPaceCeilingFormatter.formatPaceCeiling(paceMinKm: Float, isMetric: Boolean): String`.
* Integrate `TuningSliderItem` into `AftermathAnalysisSection` in `AdvancedTuningDialog.kt`.
* Wire state, persistence, and reset in `AdvancedTuningDialog.kt`.

### Step 3: Telemetry Graph Clamping & Y-Axis Scaling
* **Files**: [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt)
* Update `extractMetricValue` to accept `paceCeilingMinKm` and clamp pace data points to `effectiveCeiling`.
* Update `TelemetryMetricGraph` to accept `paceCeilingMinKm`, forward to extractor, and bound `paceMin` by `effectiveCeiling`.

### Step 4: MapDetailLayout Integration
* **Files**: [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)
* Forward `paceCeilingMinKm = tuningConfig.paceCeilingMinKm` to `TelemetryMetricGraph`.

### Step 5: 9-Language Localization
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Add `tuning_pace_ceiling_title` and `tuning_pace_ceiling_desc`.

### Step 6: Unit & Contract Tests Construction
* **Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStorePaceCeilingTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningPaceCeilingContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphPaceCeilingTest.kt`
* Execute targeted test suite via Gradle.

---

## 6. Verification & Test Suite

1. **Targeted Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*"
   ```
2. **Localization Parity Test**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
   ```
3. **Clean-Room Full Suite Regression**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
