# Stage 2: Requirement & Test Specification - ATT-3365: Standardize cluster tuning form controls using TuningFormControls

**Ticket**: [ATT-3365](https://rainerblind.atlassian.net/browse/ATT-3365)  
**Sub-task**: [ATT-3561](https://rainerblind.atlassian.net/browse/ATT-3561) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-SET-077` (*Standardized Route Clustering Form Controls via TuningFormControls (Rule 36)*), complementing `REQ-SET-075` and `REQ-UI-262`  
**Test Spec ID**: `TST-SET-077`  
**Branch**: `feature/ATT-3365`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-SET-077)

### 1.1 Problem Statement & Rationale
In `TuningFormControls.kt`, the application established standardized, accessible form controls for numeric sliders and boolean toggles across settings screens:
1. `TuningSliderItem`: Standardized title, formatted active value, helper text, default label, and slider bar.
2. `TuningToggleItem`: Standardized title, description, and Switch with consistent alignment, spacing, and scaling (`scale(0.8f)`).

However, in `ClusterTuningContent.kt` (which hosts the canonical workout route clustering parameters for `ClusterTuningScreen.kt`, `ClusterTuningSection.kt` in Advanced Settings, and `ImportBackupTabsScreen.kt`):
- Hand-crafted `Row` + `Switch` is used for `showDetails`.
- Hand-crafted `Row` + `Column` + `Switch` blocks are used for `useSportTypeForClustering` and `useAltitudePosForClustering`.
- A bespoke `TuningSlider` composable is implemented from scratch with custom `Column`, `Text`, and `Slider` layouts rather than utilizing `TuningSliderItem`.

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**.

### 1.2 Functional & Architectural Scope

1. **Parameterize `TuningFormControls.kt` with Defensive Trailing Defaults**:
   - `TuningSliderItem` SHALL accept optional trailing parameters `enabled: Boolean = true` and `onValueChangeFinished: (() -> Unit)? = null` strictly appended to the end of the existing parameter signature to ensure 100% source and binary backward compatibility for all existing call sites.
   - Forwarding & Framework Gesture Detachment: `TuningSliderItem` forwards `enabled` and `onValueChangeFinished` directly to the Material 3 `Slider`. When `enabled = false`, the Material 3 `Slider` physically detaches its gesture detector at the framework level, rendering `SliderDefaults.colors()` disabled palette and guaranteeing that neither `onValueChange` nor `onValueChangeFinished` can fire even on stray touch events.
   - `TuningToggleItem` SHALL accept optional trailing parameter `enabled: Boolean = true` appended to its signature, forwarding it to the internal `Switch`.
   - Text & Label Alpha: Standard disabled alpha (`if (enabled) 1f else TTAlpha.Disabled`) SHALL be applied to title, value text, and toggle labels when `enabled = false`.
   - Accessibility Invariant: `TuningToggleItem` SHALL enforce a minimum touch target height of at least 48.dp (`Modifier.defaultMinSize(minHeight = 48.dp)` on the row/container) ensuring WCAG 2.1 touch target compliance regardless of `scale(0.8f)` on the switch track.
   - All existing 15+ call sites across `NavigationSection`, `SensorsGpsFilterSection`, `AmoledBatterySaverSection`, `CockpitTypographySection`, `SensorSearchTuningSection`, and `AftermathAnalysisSection` SHALL retain 100% binary compatibility without signature regressions or source modifications.

2. **Standardize `ClusterTuningContent.kt`**:
   - The system SHALL replace hand-crafted switch blocks for `showDetails`, `useSportTypeForClustering`, and `useAltitudePosForClustering` with `TuningToggleItem`.
   - The system SHALL replace bespoke tolerance slider blocks (`endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`) with direct invocations of `TuningSliderItem`.
   - The legacy `TuningSlider` composable in `ClusterTuningContent.kt` SHALL be marked with `@Deprecated` containing a fully-qualified `ReplaceWith(expression = "TuningSliderItem(title = title, valueText = valueText, helperText = \"\", defaultText = \"\", value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled, onValueChangeFinished = onValueChangeFinished)", imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"])`.

3. **Multi-Consumer & State Propagation Support**:
   - All 3 consumers of `ClusterTuningContent` (`ClusterTuningScreen.kt`, `ClusterTuningSection.kt`, and `ImportBackupTabsScreen.kt`) SHALL inherit identical visual rhythm and switch scaling.
   - The `enabled` state (e.g. `!isRecalculating`) SHALL cleanly propagate down to all toggles and sliders. The altitude tolerance slider SHALL be gated to `enabled = enabled && useAltitudePosForClustering`.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-SET-077`). Complements and standardizes implementation of `REQ-SET-075` (*Centralized Route Clustering Tuning Controls via ClusterTuningContent*) and `REQ-UI-262` (*Standardized Advanced Tuning Form Controls & Parameter Presentation*).
2. **Historical Origin & Commit Trace**: `REQ-UI-262` was introduced in Sprint 2026-40.12 (`ATT-262`). `REQ-SET-075` was introduced in Sprint 2026-41.9 (`ATT-3452`).
3. **Root Reason for Existing Formulation**: When clustering parameters were centralized into `ClusterTuningContent.kt` in ATT-3452, the developer migrated the existing custom `Row`/`Switch` and custom `TuningSlider` verbatim to avoid modifying `TuningFormControls.kt`.
4. **Preservation of Core Invariants**: Tolerance ranges (`10..500f`, `0.01..0.2f`), master sensitivity calculation, recalculation domain locking (`isRecalculatingLock`), persistence on drag completion (`onValueChangeFinished`), and 100% test pass rate strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Standardized Toggles, Accessibility & Disabled Visuals)**:
  * *Given* `ClusterTuningContent` rendering clustering parameters,
  * *When* displaying `showDetails`, `useSportTypeForClustering`, and `useAltitudePosForClustering`,
  * *Then* they render using `TuningToggleItem` with `scale(0.8f)`, minimum 48dp touch height, applying `TTAlpha.Disabled` to labels and disabled palette to `Switch` when `enabled = false`.
* **Criterion 2 (Standardized Sliders & Framework Gesture Detachment)**:
  * *Given* `ClusterTuningContent` rendering tolerance parameters,
  * *When* adjusting endpoint, apex, distance, and altitude position tolerances,
  * *Then* they render using `TuningSliderItem` with title, active formatted value, and slider bar, applying `TTAlpha.Disabled` to header text and detaching gesture handling so neither `onValueChange` nor `onValueChangeFinished` fire when `enabled = false`.
* **Criterion 3 (State & Gating Propagation with Truth Table)**:
  * *Given* an active cluster recalculation in progress (`isRecalculating == true`),
  * *When* viewing `ClusterTuningSection`,
  * *Then* all toggles and sliders receive `enabled = false` and cannot be interacted with.
  * *Given* `ClusterTuningContent`,
  * *When* evaluating altitude position tolerance slider gating (`enabled = enabled && useAltitudePosForClustering`),
  * *Then* the slider is enabled strictly when both `enabled == true` AND `useAltitudePosForClustering == true` (evaluating (F,F)->F, (F,T)->F, (T,F)->F, (T,T)->T).
* **Criterion 4 (Contract, Signature Safety & Test Integrity)**:
  * *Given* `ClusterTuningContractTest` and `AdvancedTuningVisualContractTest`,
  * *When* executed on pure JVM,
  * *Then* 100% of tests pass, verifying architectural conformity, binary backward compatibility for callers omitting trailing parameters, and zero regressions on existing tuning sections.

---

## 4. Test Specification (TST-SET-077)

### 4.1 Unit & Contract Test Cases

1. **`TuningFormControlsTest.kt`**:
   - Signature Safety & Trailing Parameter Verification: Verify `TuningSliderItem` accepts optional trailing `enabled` and `onValueChangeFinished` without breaking existing 8-parameter callers. Verify `TuningToggleItem` accepts trailing `enabled`.
   - Accessibility Touch Target Verification: Verify `TuningToggleItem` row enforces at least 48.dp minimum height.
   - Negative Callback Gating / Gesture Detachment: Verify that when `enabled = false`, `Slider` and `Switch` are in disabled state, preventing `onValueChange` and `onValueChangeFinished` from firing or mutating state.
   - Disabled Alpha Verification: Verify `TTAlpha.Disabled` is applied to title, value text, and toggle labels when `enabled = false`.

2. **`ClusterTuningContractTest.kt`**:
   - Verify `ClusterTuningContent.kt` delegates to `TuningToggleItem` and `TuningSliderItem`.
   - Verify `ClusterTuningSection.kt` propagates `enabled = !isRecalculating`.
   - Truth-Table Gating Test: Formally test the boolean truth-table for altitude tolerance slider:
     - `enabled = false, useAltitude = false` -> slider `enabled = false`
     - `enabled = false, useAltitude = true`  -> slider `enabled = false`
     - `enabled = true,  useAltitude = false` -> slider `enabled = false`
     - `enabled = true,  useAltitude = true`  -> slider `enabled = true`
   - Verify deprecated `TuningSlider` composable carries `@Deprecated` with fully qualified `ReplaceWith(..., imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"])`.

3. **`AdvancedTuningVisualContractTest.kt`**:
   - Verify all 15+ existing consumers of `TuningFormControls` (`NavigationSection`, `SensorsGpsFilterSection`, `CockpitTypographySection`, `AmoledBatterySaverSection`, etc.) compile without modification and maintain 100% test pass rate.

4. **Multi-Consumer Visual Layout Audit**:
   - Verify `ClusterTuningScreen.kt`, `ClusterTuningSection.kt`, and `ImportBackupTabsScreen.kt` compile and render cleanly with unified form controls.

5. **Clean-Room Regression Suite**:
   - Run `./gradlew testDebugUnitTest` and assert 100% passing tests with zero regressions.
