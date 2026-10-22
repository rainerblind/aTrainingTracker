# Stage 1 Analysis: ATT-3365 - Standardize cluster tuning form controls using TuningFormControls

**Ticket**: [ATT-3365](https://rainerblind.atlassian.net/browse/ATT-3365)  
**Sub-task**: [ATT-3560](https://rainerblind.atlassian.net/browse/ATT-3560) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Branch**: `feature/ATT-3365`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Statement & Motivation

In `TuningFormControls.kt`, the application established standardized, accessible form controls for numeric sliders and boolean toggles across settings screens:
1. `TuningSliderItem`: Standardized title, formatted active value, helper text, default label, and slider bar.
2. `TuningToggleItem`: Standardized title, description, and Switch with consistent alignment, spacing, and scaling (`scale(0.8f)`).

However, in `ClusterTuningContent.kt` (which hosts the canonical workout route clustering parameters for `ClusterTuningScreen.kt`, `ClusterTuningSection.kt` in Advanced Settings, and `ImportBackupTabsScreen.kt`):
- Hand-crafted `Row` + `Switch` is used for `showDetails`.
- Hand-crafted `Row` + `Column` + `Switch` blocks are used for `useSportTypeForClustering` and `useAltitudePosForClustering`.
- A bespoke `TuningSlider` composable (lines 236–274) is implemented from scratch with custom `Column`, `Text`, and `Slider` layouts rather than utilizing `TuningSliderItem`.

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**:
1. It duplicates toggle and slider layout logic across the application.
2. Visual presentation (typography hierarchy, text colors, spacing, switch scaling) diverges between Advanced Settings and Cluster Tuning.
3. Fixes and enhancements to form controls (such as accessibility labeling, scaling, or theme token updates) must be repeated across multiple files.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Commit & History Archaeology**:
   - `ClusterTuningScreen.kt` was originally implemented with raw Compose primitives (`Row`, `Column`, `Slider`, `Switch`).
   - In Sprint 2026-40.12 (`ATT-262`), `TuningFormControls.kt` was introduced to provide standardized form elements for the new Advanced Tuning preference screens (`NavigationSection`, `SensorsGpsFilterSection`, `AmoledBatterySaverSection`, etc.).
   - In Sprint 2026-41.9 (`ATT-3452`), clustering parameters were extracted into `ClusterTuningContent.kt` to share tuning controls between `ClusterTuningScreen` and `ClusterTuningSection` in Advanced Settings. However, the author migrated the existing custom `Row`/`Switch` and bespoke `TuningSlider` code verbatim rather than adopting `TuningFormControls.kt`.

2. **Structural Comparison**:

| Dimension | Standard `TuningFormControls.kt` | `ClusterTuningContent.kt` (Existing) |
| :--- | :--- | :--- |
| **Toggle Switches** | `TuningToggleItem(title, isChecked, onCheckedChange, summary)` with `scale(0.8f)` | Manual `Row` + `Switch(modifier = Modifier)` (unscaled) |
| **Slider Header** | `TuningSliderItem`: Title (`bodyMedium` SemiBold) + Value (`bodyMedium` Bold primary) | `TuningSlider`: Title (`titleSmall`) + Value on separate second line |
| **State Parameters** | Currently lacks `enabled` and `onValueChangeFinished` | Requires `enabled` and `onValueChangeFinished` |

3. **Key Finding**:
   - `TuningSliderItem` and `TuningToggleItem` in `TuningFormControls.kt` currently lack optional `enabled: Boolean = true` and `onValueChangeFinished: (() -> Unit)? = null` parameters.
   - Adding these parameters with default values preserves 100% backward compatibility with all existing consumers (`NavigationSection`, `CockpitTypographySection`, `SensorsGpsFilterSection`, `AmoledBatterySaverSection`, `SensorSearchTuningSection`, `AftermathAnalysisSection`), while enabling `ClusterTuningContent` to fully adopt `TuningFormControls`.

---

## 3. Multi-Consumer Call-Site & Visual Layout Audit

`ClusterTuningContent` is utilized across three distinct entry points in `aTrainingTracker`:

1. **`ClusterTuningScreen.kt`** (Standalone full-screen activity/route):
   - Invokes `ClusterTuningContent` inside a vertically scrollable column with 16dp horizontal padding.
   - Recalculation state (`isRecalculating`) displays a progress indicator overlay at screen level.
2. **`ClusterTuningSection.kt`** (Advanced Settings accordion category):
   - Invokes `ClusterTuningContent` passing `enabled = !isRecalculating` and `isDialog = false`.
   - Embeds a red "Recalculate Clusters" button below the content.
3. **`ImportBackupTabsScreen.kt`** (Pre-import clustering tuning sheet):
   - Invokes `ClusterTuningContent` passing `onValueChangeFinished = { viewModel.saveClusteringTolerances() }` and `isDialog = false`.

### Visual Hierarchy & Spacing Normalization
- `TuningFormControls.kt` encapsulates the internal layout of controls:
  - `TuningToggleItem`: 12dp end-padding between description and switch, `scale(0.8f)` switch footprint, `2.dp` vertical padding.
  - `TuningSliderItem`: Space-between header row with title and primary bold value, optional subtitle, and standard slider with 2dp top padding.
- `ClusterTuningContent.kt` retains its outer category spacing:
  - `verticalSpacing`: `12.dp` if `isDialog`, `24.dp` otherwise.
- Transitioning to `TuningFormControls` eliminates divergence, ensuring clustering controls possess the exact visual rhythm, typography, and switch scale as all other Advanced Settings sections.

---

## 4. State Propagation & Persistence Architecture

### 4.1 State & Enablement Propagation Plan
- In `TuningFormControls.kt`:
  - `TuningToggleItem`: add `enabled: Boolean = true`. Pass `enabled` into `Switch(..., enabled = enabled)`. Apply alpha reduction (`if (enabled) 1f else TTAlpha.Disabled`) to text labels.
  - `TuningSliderItem`: add `enabled: Boolean = true` and `onValueChangeFinished: (() -> Unit)? = null`. Pass `enabled` into `Slider(..., enabled = enabled, onValueChangeFinished = onValueChangeFinished)`. Apply alpha reduction (`if (enabled) 1f else TTAlpha.Disabled`) to text labels.
  - Default arguments ensure all 15+ existing callers across other settings categories remain completely unimpacted.
- In `ClusterTuningContent.kt`:
  - Receive `enabled: Boolean = true` from parent.
  - Forward `enabled = enabled` to `showDetails` toggle, `useSportTypeForClustering` toggle, `endpointTolerance` slider, `apexTolerance` slider, and `distanceTolerance` slider.
  - For `altitudePositionTolerance`: gate as `enabled = enabled && useAltitudePosForClustering` (preventing altitude tolerance adjustment when altitude clustering is disabled).

### 4.2 Persistence & Performance Contract
- **Continuous Live Feedback**: `onValueChange = (Float) -> Unit` mutates in-memory Compose state in the ViewModel during active dragging, providing responsive UI value text updates.
- **Batch Persistence**: `onValueChangeFinished: (() -> Unit)?` fires strictly upon finger release / drag termination, dispatching `viewModel.saveTuningParameters()` (or `saveClusteringTolerances()`). This prevents thrashing SQLite / DataStore on every drag tick while guaranteeing immediate persistence upon completion.

---

## 5. Architectural Strategy: Deprecation & Clean Migration (No Permanent Adapters)

Auditor Agent 2 identified that maintaining `TuningSlider` as a permanent adapter creates a zombie API surface. We establish a clean, direct refactoring strategy:

1. **Direct Call-Site Adoption**:
   - `ClusterTuningContent.kt` will invoke `TuningSliderItem` directly for all 4 tolerance sliders (`endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`).
2. **Deprecation with `ReplaceWith`**:
   - The standalone `TuningSlider` composable in `ClusterTuningContent.kt` will be marked with:
     ```kotlin
     @Deprecated(
         message = "Use TuningSliderItem directly from ui.settings.tuning.TuningFormControls (REQ-UI-262, Rule 36)",
         replaceWith = ReplaceWith(
             "TuningSliderItem(title = label, valueText = ..., helperText = \"\", defaultText = \"\", value = value, onValueChange = onValueChange, valueRange = valueRange, enabled = enabled, onValueChangeFinished = onValueChangeFinished)",
             "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"
         ),
         level = DeprecationLevel.WARNING
     )
     ```
   - This prevents breaking external references while clearly signaling migration to `TuningSliderItem`.
3. **Contract Test Alignment**:
   - `ClusterTuningContractTest` will be updated to assert that `ClusterTuningContent.kt` uses `TuningSliderItem` and `TuningToggleItem` from `TuningFormControls`, asserting architectural conformity rather than obsolete implementation relics.

---

## 6. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. *Enhance `TuningFormControls.kt`*:
     - Add `enabled: Boolean = true` and `onValueChangeFinished: (() -> Unit)? = null` to `TuningSliderItem`.
     - Add `enabled: Boolean = true` to `TuningToggleItem`.
  2. *Refactor `ClusterTuningContent.kt`*:
     - Replace custom `Row` + `Switch` blocks for `showDetails`, `useSportTypeForClustering`, and `useAltitudePosForClustering` with `TuningToggleItem`.
     - Replace tolerance slider blocks with direct invocations of `TuningSliderItem`.
     - Deprecate bespoke `TuningSlider` with `ReplaceWith`.
  3. *Verification & Tests*:
     - Verify `ClusterTuningContractTest` and `AdvancedTuningVisualContractTest`.
     - Verify layout across `ClusterTuningScreen`, `ClusterTuningSection`, and `ImportBackupTabsScreen`.
     - Run targeted unit tests and full clean-room regression test suite.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying clustering algorithms or math in `WorkoutClusterRepository.kt`.
  - Changing clustering tolerance ranges or defaults.
  - Redesigning master sensitivity calculation or recalculation dialogs.

---

## 7. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement IDs & Targets**:
  - `REQ-SET-075` (*Centralized Route Clustering Tuning Controls via ClusterTuningContent*).
  - `REQ-UI-262` (*Standardized Advanced Tuning Form Controls & Parameter Presentation*).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-262`: Sprint 2026-40.12 (`ATT-262`).
  - `REQ-SET-075`: Sprint 2026-41.9 (`ATT-3452`).
* **Root Reason for Existing Formulation**:
  - `ClusterTuningContent` retained bespoke controls during the ATT-3452 refactoring to avoid modifying `TuningFormControls.kt`.
* **Preservation of Core Invariants**:
  1. *Parameter Integrity*: Sliders and toggles must continue to bind to `endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`, `useSportTypeForClustering`, and `useAltitudePosForClustering`.
  2. *Enabled State Gating*: When `isRecalculating` is true, all tuning controls must remain disabled.
  3. *Save Trigger*: Adjusting sliders or toggles must trigger persistence via `onValueChangeFinished` and `viewModel.saveTuningParameters()`.
  4. *Clean-Room Pass Rate*: 100% test pass rate across all contract and unit tests.

---

## 8. Risk Assessment & Verification Plan

* **Risk 1: Regressing existing `TuningFormControls` consumers**:
  - Mitigation: Default arguments (`enabled = true`, `onValueChangeFinished = null`) ensure 0 impact on existing callers.
* **Risk 2: Visual clipping or overflow in `ImportBackupTabsScreen`**:
  - Mitigation: `TuningToggleItem` and `TuningSliderItem` have identical or smaller vertical footprints than the hand-crafted rows, preventing clipping.
* **Verification**:
  - Run targeted tests: `./gradlew testDebugUnitTest --tests "*ClusterTuning*" --tests "*AdvancedTuning*"`.
  - Run full suite: `./gradlew testDebugUnitTest`.
