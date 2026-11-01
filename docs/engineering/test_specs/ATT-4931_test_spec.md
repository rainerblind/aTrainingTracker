# Stage 2: Requirement & Test Specification - ATT-4931: Decouple Workout Clusters Heatmap from Periods Styling

**Ticket**: [ATT-4931](https://rainerblind.atlassian.net/browse/ATT-4931)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-058` (`REQ-MAP-058.1`..`REQ-MAP-058.5`, refining `REQ-MAP-056` and `REQ-MAP-057`)  
**Test Mapping**: `TST-MAP-060` (`TST-MAP-060.1`..`TST-MAP-060.5`)  
**Branch**: `feature/ATT-4931`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
Sprint Review 2026-44.1 of `ATT-4833` revealed that shared heatmap modifications unintentionally restyled the Workout Clusters heatmap (`WorkoutClusterHeatmapScreen.kt`): base alpha stops were boosted to 55%/80%/100%, default opacity raised to 1.0, and logarithmic corridor normalization (`ATT-4929`) was implicitly forced onto all `MapContentScope.heatmap` callers. `ATT-4931` restores the pre-`ATT-4833` visual appearance for Workout Clusters while cleanly decoupling it from Periods:
1. **Pre-`ATT-4833` Palette Preset**: `ThermalColorLut.CLASSIC_BLUE` with alpha stops 20% / 30% / 70% (51, 77, 180) and global opacity 0.8.
2. **Classic Step Intensity Strategy**: Restore classic pass-intensity calculation in `PolylineHeatmapTileProvider` (<=5: 0.25f, <=20: 0.15f, <=60: 0.08f, <=150: 0.05f, >150: 0.035f).
3. **Opt-In Corridor Normalization**: `MapContentScope.heatmap` introduces `normalizeCorridors: Boolean = false`, preventing unwanted logarithmic compression on cluster tracks.
4. **Explicit Call Site Binding**: `WorkoutClusterHeatmapScreen.kt` explicitly passes `ThermalColorLut.CLASSIC_BLUE` and `normalizeCorridors = false`.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-058.1` | `TST-MAP-060.1` | `ThermalColorLutTest` | `CLASSIC_BLUE` has opacity 0.8, base alphas 51/77/180, and interpolates Cyan -> Blue -> Indigo. |
| `REQ-MAP-058.2` | `TST-MAP-060.2` | `PolylineHeatmapTileProviderTest` | Classic intensity strategy evaluates pass count thresholds (0.25f, 0.15f, 0.08f, 0.05f, 0.035f). |
| `REQ-MAP-058.3` | `TST-MAP-060.3` | `MapContentScopeTest` | `normalizeCorridors = false` disables corridor estimation and leaves linear step accumulation active. |
| `REQ-MAP-058.4` | `TST-MAP-060.4` | `WorkoutClusterHeatmapTest` | `WorkoutClusterHeatmapScreen` configures `CLASSIC_BLUE` and classic intensity, isolating it from Periods. |
| `REQ-MAP-058.5` | `TST-MAP-060.5` | Targeted Test Suite | Full targeted regression (`ui.map.*`, `ui.clusters.*`, `ui.aftermath.periodlist.*`) 100% PASS. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-MAP-060.1: Classic Blue LUT Alpha & Opacity Verification
* **Given** `ThermalColorLut.CLASSIC_BLUE`,
* **When** resolving colors across indices 0, 1, 128, and 255,
* **Then** index 0 is transparent (`0x00000000`), opacity is 0.8, and alpha evaluates to $\approx 41$ (low), $\approx 62$ (mid), and $\approx 144$ (high), matching pre-`ATT-4833` appearance.

### TST-MAP-060.2: Classic Step Intensity Strategy
* **Given** a cluster with 4, 15, 45, 100, and 200 workouts,
* **When** evaluating classic step intensity,
* **Then** step intensity returns exactly `0.25f`, `0.15f`, `0.08f`, `0.05f`, and `0.035f` respectively.

### TST-MAP-060.3: Corridor Normalization Opt-In Guard
* **Given** a `MapContentScope.heatmap` call with `normalizeCorridors = false`,
* **When** the provider is instantiated,
* **Then** `maxCorridorPassCount` remains `null`, preventing logarithmic compression.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-056.2` (*256-Entry Sequential Blue Color LUT*) and `REQ-MAP-057.4` (*Modular Decoupling & Parameterization*).
* **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) established the classic 4..29 pass saturation and 20%/30%/70% alpha stops. Sprint Review 2026-44.1 of `ATT-4833` and `ATT-4929` revealed inadvertent coupling with Workout Clusters.
* **Root Reason for Existing Formulation**: Shared components were modified in-place for Periods without establishing distinct presets or configuration parameters.
* **Preservation of Core Invariants**: Periods heatmap styling (`HOT`, zoom-adaptive stroke, logarithmic normalization) is 100% preserved. Thread safety on `Dispatchers.Default` and zero UI-thread blocking are strictly preserved.

---

## 4. Invariants & Localization
* **No UI String Changes**: Zero localized string modifications; `TranslationParityTest` remains 100% PASS.
* **Target Release Parity**: `V4.9.41` verified as active/unreleased in Jira.
