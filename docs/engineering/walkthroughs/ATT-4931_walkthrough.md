# Stage 5: Walkthrough & Verification - ATT-4931: Decouple Workout Clusters Heatmap from Periods Styling

**Ticket**: [ATT-4931](https://rainerblind.atlassian.net/browse/ATT-4931)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-058` (`REQ-MAP-058.1`..`REQ-MAP-058.5`)  
**Test Mapping**: `TST-MAP-060` (`TST-MAP-060.1`..`TST-MAP-060.5`)  
**Branch**: `feature/ATT-4931`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview
Sprint Review 2026-44.1 of `ATT-4833` revealed that shared heatmap changes unintentionally restyled the Workout Clusters heatmap (`WorkoutClusterHeatmapScreen.kt`): base alphas were raised to 55%/80%/100%, opacity to 1.0, and logarithmic corridor normalization (`ATT-4929`) was implicitly forced onto all `MapContentScope.heatmap` callers. Per Human Clarification in Ceremony 1, `ATT-4931` restores the pre-`ATT-4833` visual appearance for Workout Clusters while cleanly decoupling Periods heatmap styling:
1. **Classic Sequential Blue Preset**: `ThermalColorLut.CLASSIC_BLUE` defines base alpha stops at 20% (51), 30% (77), and 70% (180) with global layer opacity 0.8, restoring pre-`ATT-4833` subtle track blending.
2. **Classic Pass-Intensity Strategy**: `PolylineHeatmapTileProvider.calculateClassicStepIntensity` restores the 4..29 pass saturation curve (0.25f, 0.15f, 0.08f, 0.05f, 0.035f).
3. **Corridor Normalization Opt-In**: `MapContentScope.heatmap` introduces `normalizeCorridors: Boolean = false`, preventing unwanted logarithmic compression on cluster tracks.
4. **Explicit Call Site Binding**: `WorkoutClusterHeatmapScreen.kt` explicitly passes `ThermalColorLut.CLASSIC_BLUE` and `normalizeCorridors = false`, while `InteractivePeriodMap.kt` explicitly retains `ThermalColorLut.HOT` and `normalizeCorridors = true`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Component | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-058.1` | `TST-MAP-060.1` | `ThermalColorLutTest.testClassicBlueLutAlphaStopsAndOpacity_TST_MAP_060_1` | **PASSED** | `Verified` |
| `REQ-MAP-058.2` | `TST-MAP-060.2` | `PolylineHeatmapTileProviderTest.testClassicStepIntensityThresholds_TST_MAP_060_2` | **PASSED** | `Verified` |
| `REQ-MAP-058.3` | `TST-MAP-060.3` | `PolylineHeatmapTileProviderTest.testClusterProviderWithClassicIntensityAndClassicBlue_TST_MAP_060_3` | **PASSED** | `Verified` |
| `REQ-MAP-058.4` | `TST-MAP-060.4` | Call site audit & decoupling contract tests in `ui.map.*` | **PASSED** | `Verified` |
| `REQ-MAP-058.5` | `TST-MAP-060.5` | Tier 1 Targeted Test Suite (`ui.map.*`, `periodlist.*`, `ui.theme.*`, `build.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 7 & Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.trainingtracker.ui.map.*` (17 tests)
  - `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*` (90 tests)
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest` (2 tests)
  - `com.atrainingtracker.trainingtracker.build.*` (13 tests)
* **Execution**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: **BUILD SUCCESSFUL in 2m 2s** (100% clean-room test pass rate, 0 failures).
* **Build Verification**: `./gradlew assembleDebug` -> **BUILD SUCCESSFUL in 56s** (debug APK generated cleanly).
* **ADB Device Verification**: `adb devices` checked; zero devices attached. Visual styling verified via unit test matrix and bytecode assertions.
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` -> **PASSED** (code 0, net-new requirement REQ-MAP-058).

---

## 4. Invariant & Governance Verification
1. **Thread Safety & Off-Thread Rasterization**: All heatmap generation executes strictly on `Dispatchers.Default` with zero main-thread blocking.
2. **Periods Heatmap Integrity**: Periods 2.0 heatmap styling (`ThermalColorLut.HOT`, zoom-adaptive stroke, logarithmic normalization) is 100% preserved.
3. **Decoupled Architecture**: Workout Clusters and Periods use distinct presets and parameterizations without competing defaults.
4. **Living Docs Updated**: `REQ-MAP-058` and `TST-MAP-060` marked as `Verified` in domain shards and master indexes.
5. **No Premature FixVersion**: Target Release `V4.9.41` maintained; `FixVersion` reserved for human acceptance in Ceremony 2.
