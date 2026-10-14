# Stage 3: Implementation Plan - ATT-3365: Standardize cluster tuning form controls using TuningFormControls

**Ticket**: [ATT-3365](https://rainerblind.atlassian.net/browse/ATT-3365)  
**Sub-task**: [ATT-3564](https://rainerblind.atlassian.net/browse/ATT-3564) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-SET-077` (*Standardized Route Clustering Form Controls via TuningFormControls (Rule 36)*)  
**Test Spec Mapping**: `TST-SET-077`  
**Branch**: `feature/ATT-3365`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Architectural Motivation & Single Source of Truth (Rule 36)

In `TuningFormControls.kt`, the application established canonical, accessible form controls for numeric sliders and boolean toggles across settings screens:
1. `TuningSliderItem`: Standardized title, formatted active value, helper text, default label, and slider bar.
2. `TuningToggleItem`: Standardized title, description, and Switch with consistent alignment, spacing, and scaling (`scale(0.8f)`).

However, in `ClusterTuningContent.kt` (which hosts the canonical workout route clustering parameters for `ClusterTuningScreen.kt`, `ClusterTuningSection.kt` in Advanced Settings, and `ImportBackupTabsScreen.kt`):
- Hand-crafted `Row` + `Switch` is used for `showDetails`.
- Hand-crafted `Row` + `Column` + `Switch` blocks are used for `useSportTypeForClustering` and `useAltitudePosForClustering`.
- A bespoke `TuningSlider` composable is implemented from scratch with custom layout rather than utilizing `TuningSliderItem`.

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**.

### 1.2 Target Architectural Structure

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 TuningFormControls.kt                  │
                  │  - TuningSliderItem(title, valueText, ...,             │
                  │       enabled = true, onValueChangeFinished = null)    │
                  │  - TuningToggleItem(title, isChecked, ...,             │
                  │       enabled = true) [minHeight = 48.dp, scale(0.8f)] │
                  └───────────┬────────────────────────────────┬───────────┘
                              │                                │
            used by           │                                │ used by 15+ sections:
                              ▼                                ▼
┌──────────────────────────────────────────────┐ ┌──────────────────────────────────────────────┐
│          ClusterTuningContent.kt             │ │ NavigationSection, SensorsGpsFilterSection,  │
│  - showDetails: TuningToggleItem             │ │ AmoledBatterySaverSection,                   │
│  - useSportType: TuningToggleItem            │ │ CockpitTypographySection,                    │
│  - useAltitudePos: TuningToggleItem          │ │ SensorSearchTuningSection,                   │
│  - 4x Tolerance Sliders: TuningSliderItem    │ │ AftermathAnalysisSection                     │
│  - legacy TuningSlider: @Deprecated          │ └──────────────────────────────────────────────┘
└─────────────────────┬────────────────────────┘
                      │ shared across 3 consumers:
                      ├───────────────────────────────┬───────────────────────────────┐
                      ▼                               ▼                               ▼
      ┌───────────────────────────────┐ ┌───────────────────────────────┐ ┌───────────────────────────────┐
      │     ClusterTuningScreen       │ │    ClusterTuningSection       │ │    ImportBackupTabsScreen     │
      │   (Standalone Tuning View)    │ │   (Advanced Settings Tab)     │ │     (Migration / Tuning)      │
      └───────────────────────────────┘ └───────────────────────────────┘ └───────────────────────────────┘
```

---

## 2. UI Consistency (Rule 23)

* **Closest Existing Reference Screen**:
  - `SensorsGpsFilterSection.kt`, `CockpitTypographySection.kt`, and `NavigationSection.kt` in `ui/settings/tuning/categories/`.
* **Reused Components (`ui/components/`)**:
  - `TuningSliderItem` from `ui/settings/tuning/TuningFormControls.kt`.
  - `TuningToggleItem` from `ui/settings/tuning/TuningFormControls.kt`.
  - `TTAlpha.Disabled` from `ui/theme/TTAlpha.kt`.
* **Theme Tokens**:
  - **Colors**: `MaterialTheme.colorScheme.onSurface` (labels), `MaterialTheme.colorScheme.onSurfaceVariant` (descriptions), `MaterialTheme.colorScheme.primary` (active values), `outline` (defaults).
  - **Disabled States**: `if (enabled) 1f else TTAlpha.Disabled` applied to label colors. Material 3 `SliderDefaults.colors()` and `SwitchDefaults.colors()` handle track/thumb disabled palettes automatically.
  - **Typography**: `MaterialTheme.typography.bodyMedium` (titles), `MaterialTheme.typography.bodySmall` (summaries/helpers), `MaterialTheme.typography.labelSmall` (defaults).
  - **Spacing**: Canonical `2.dp` inner spacing, `spacedBy(12.dp)` or `spacedBy(24.dp)` section rhythm.
  - **Touch Accessibility**: `Modifier.defaultMinSize(minHeight = 48.dp)` applied to `TuningToggleItem` to guarantee WCAG 2.1 48dp minimum touch target height regardless of switch track scaling (`scale(0.8f)`).
* **Justification for New One-Off Styles**:
  - Zero new one-off styles. Complete consolidation into canonical Material 3 and `TuningFormControls` patterns.

---

## 3. Invariants & Safety Measures

1. **Signature Safety & Binary Backward Compatibility (SWE.2)**:
   - Trailing parameter rule: `enabled: Boolean = true` and `onValueChangeFinished: (() -> Unit)? = null` MUST be strictly appended as trailing parameters with default values at the end of parameter signatures in `TuningFormControls.kt`.
   - All 15+ existing callers in `NavigationSection`, `SensorsGpsFilterSection`, `CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorSearchTuningSection`, and `AftermathAnalysisSection` compile without modification and require zero signature changes.
2. **Framework-Level Gesture Detachment & Callback Gating**:
   - Forwarding `enabled = false` to Material 3 `Slider` and `Switch` detaches pointer input gesture handling at the framework level (`Modifier.pointerInput` inactive), guaranteeing that neither `onValueChange` nor `onValueChangeFinished` can fire upon touch events while disabled.
3. **State Gating Truth Table Invariant**:
   - The altitude position tolerance slider is gated via `enabled = enabled && useAltitudePosForClustering`:
     - `(enabled = false, useAltitudePos = false) -> false`
     - `(enabled = false, useAltitudePos = true)  -> false`
     - `(enabled = true,  useAltitudePos = false) -> false`
     - `(enabled = true,  useAltitudePos = true)  -> true`
4. **Tolerance Range & Domain Invariants**:
   - Tolerance bounds (`10..500f`, `0.01..0.2f`), master sensitivity calculation, recalculation domain locking (`isRecalculatingLock`), and persistence on drag completion (`onValueChangeFinished`) remain completely unchanged.
5. **Deprecation Integrity**:
   - Legacy `TuningSlider` carries `@Deprecated` with fully qualified `ReplaceWith(..., imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"])`.
6. **Zero Test Regressions**:
   - 100% full-suite unit test pass rate across `ClusterTuningContractTest`, `TuningFormControlsTest`, `AdvancedTuningVisualContractTest`, and all existing unit tests.

---

## 4. Bite-Sized Atomic Implementation Steps

### Step 1: Parameterize `TuningFormControls.kt` with Defensive Trailing Defaults
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`
- **Actions**:
  1. Add optional trailing parameters to `TuningSliderItem`:
     `enabled: Boolean = true`, `onValueChangeFinished: (() -> Unit)? = null`.
  2. Forward `enabled` and `onValueChangeFinished` to the internal `Slider`.
  3. Apply `if (enabled) 1f else TTAlpha.Disabled` to title, value text, helper, and default text colors.
  4. Add optional trailing parameter to `TuningToggleItem`: `enabled: Boolean = true`.
  5. Add `Modifier.defaultMinSize(minHeight = 48.dp)` to `TuningToggleItem` row for WCAG 2.1 touch accessibility.
  6. Forward `enabled` to `Switch`, applying `if (enabled) 1f else TTAlpha.Disabled` to title and summary text colors.
- **Verification Command**:
  `./gradlew compileDebugKotlin`

### Step 2: Author Unit & Contract Tests in `TuningFormControlsTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControlsTest.kt`
- **Actions**:
  1. Test trailing parameter signatures and binary backward compatibility for callers omitting them.
  2. Test `Modifier.defaultMinSize(minHeight = 48.dp)` accessibility enforcement.
  3. Test disabled alpha application (`TTAlpha.Disabled`).
  4. Test callback gating / disabled mode behavior.
- **Verification Command**:
  `./gradlew testDebugUnitTest --tests "*TuningFormControlsTest*"`

### Step 3: Standardize `ClusterTuningContent.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt`
- **Actions**:
  1. Import `TuningSliderItem` and `TuningToggleItem` from `com.atrainingtracker.trainingtracker.ui.settings.tuning`.
  2. Replace `showDetails` `Row` + `Switch` with `TuningToggleItem`.
  3. Replace `useSportTypeForClustering` and `useAltitudePosForClustering` blocks with `TuningToggleItem`.
  4. Replace 4x tolerance sliders (`endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`) with `TuningSliderItem`.
  5. Gate altitude position slider with `enabled = enabled && useAltitudePosForClustering`.
  6. Annotate legacy `TuningSlider` with `@Deprecated` and fully qualified `ReplaceWith(..., imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"])`.
- **Verification Command**:
  `./gradlew compileDebugKotlin`

### Step 4: Expand `ClusterTuningContractTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/ClusterTuningContractTest.kt`
- **Actions**:
  1. Add test asserting `ClusterTuningContent.kt` delegates to `TuningToggleItem` and `TuningSliderItem`.
  2. Add test asserting `ClusterTuningSection.kt` propagates `enabled = !isRecalculating`.
  3. Add truth-table unit test verifying `enabled = enabled && useAltitudePosForClustering` across all 4 boolean combinations.
  4. Add test asserting legacy `TuningSlider` carries `@Deprecated` with fully qualified `ReplaceWith`.
- **Verification Command**:
  `./gradlew testDebugUnitTest --tests "*ClusterTuningContractTest*"`

### Step 5: Multi-Consumer & Visual Regression Verification
- **Target Files**: All consumers (`ClusterTuningScreen.kt`, `ClusterTuningSection.kt`, `ImportBackupTabsScreen.kt`, `AdvancedTuningVisualContractTest.kt`)
- **Actions**:
  1. Run `./gradlew testDebugUnitTest --tests "*AdvancedTuning*" --tests "*ClusterTuning*"`
  2. Execute full clean-room unit test suite: `./gradlew testDebugUnitTest` asserting 100% pass rate.
