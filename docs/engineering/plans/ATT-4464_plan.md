# Stage 3: Implementation Plan - ATT-4464: [Verbesserung] Significantly reduce switch size and visual weight for subordinate 'Show in Workout Summary' toggles in Physiology & Training Models

**Ticket**: [ATT-4464](https://rainerblind.atlassian.net/browse/ATT-4464)  
**Sub-task**: [ATT-4468](https://rainerblind.atlassian.net/browse/ATT-4468) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-424`, `REQ-UI-424.1`  
**Test Mapping**: `TST-UI-388` (`TST-UI-388.3`, `TST-UI-42402`), `TST-UI-388.6`  
**Branch**: `feature/ATT-4464`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During the Sprint 2026-43.3 Review on Pixel 10 (documented in `ATT-4464` and the Sprint Retrospective `ATT-4433`), the user evaluated the subordinate *"In Workout-Zusammenfassung anzeigen"* (*"Show in Workout Summary"*) toggles in `Settings -> Advanced Settings -> Physiology & Training Models` (`PhysiologyModelsSection.kt`).

Although `SubordinateTuningToggleItem` indented the row by `16.dp` and applied smaller typography (`MaterialTheme.typography.bodySmall`), the toggle control itself rendered as a prominent Material 3 `Switch` of the **exact same physical size** and **visual weight** as the parent model switch directly above it:
- Both parent `TuningToggleItem` and child `SubordinateTuningToggleItem` applied `Modifier.scale(0.8f)`.
- Both parent and child switches drew default Material 3 color tokens (`primary` track and `onPrimary` thumb).
- Because `Switch` allocates unscaled touch target layout bounds unless constrained, the subordinate row reserved unneeded whitespace.

This caused the child toggle to visually compete with the primary model header, violating Rule 59 and `docs/design_guidelines.md` Section 3.3. Under the aligned Sprint Planning 2026-43.4 agreement:
- `SubordinateTuningToggleItem` SHALL remain a `Switch` (avoiding checkboxes or alternate controls to preserve visual harmony).
- The subordinate switch SHALL downscale significantly further than the parent `0.8f` switch, applying `Modifier.scale(0.6f)`.
- The subordinate switch SHALL enforce a compact vertical layout height (`Modifier.height(24.dp)`) so the scaled graphic does not inflate the row height.
- The subordinate switch SHALL be styled using Material 3 `secondary` color tokens (`checkedThumbColor = MaterialTheme.colorScheme.onSecondary`, `checkedTrackColor = MaterialTheme.colorScheme.secondary`) instead of `primary`, ensuring it is distinctly smaller, lighter, and subordinate to the parent model toggle.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-424` (*Show in Workout Summary Toggles for Physiology Models in Advanced Settings, Rule 59 Subordinate Toggle Hierarchy, and Coggan-Only Presentation Gating in WorkoutDetails*)
  * `REQ-UI-424.1`: Subordinate Summary Toggles in Physiology & Training Models (`TuningFormControls.kt`, `PhysiologyModelsSection.kt`) — downscale `Switch` to `Modifier.scale(0.6f)`, enforce compact `Modifier.height(24.dp)`, and apply `SwitchDefaults.colors(checkedThumbColor = onSecondary, checkedTrackColor = secondary)` while preserving backward compatibility.
  * `REQ-UI-424.5`: Preservation of Invariants & Modularity Budgets — strict `< 400` lines budget for `TuningFormControls.kt` and `PhysiologyModelsSection.kt`; zero alteration of telemetry recording or live cockpit sensor picker.
* **Test Mapping**: `TST-UI-388` (`TST-UI-388.3`, `TST-UI-42402`), `TST-UI-388.6`
  * `[TST-UI-388.3a]` / `[TST-UI-42402]`: Unit/contract assertions in `TuningFormControlsTest.kt` verifying `scale(0.6f)`, `height(24.dp)`, and `secondary` color tokens.
  * `[TST-UI-388.3b]`: Unit assertions verifying API compatibility and `TTAlpha.Disabled` application in `TuningFormControlsTest.kt`.
  * `[TST-UI-388.3c]`: Contract assertions in `PhysiologyModelsVisibilityContractTest.kt` and `AdvancedTuningModularityTest.kt` verifying Rule 59 usage and strict `< 400` line modularity limits.
  * `[TST-UI-388.6]`: Full clean-room test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Control Type Harmony**: `SubordinateTuningToggleItem` remains a `Switch` (no checkboxes or ad-hoc custom icons).
2. **Master Switch Independence**: Parent `TuningToggleItem` maintains its `0.8f` scale, 48.dp touch target, and primary color tokens.
3. **API & Call Site Compatibility**: Function signature of `SubordinateTuningToggleItem` (`title: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true`) remains 100% backward-compatible, requiring zero signature changes at callers in `PhysiologyModelsSection.kt`.
4. **DataStore & Telemetry Invariance**: Gating in `WorkoutDetails.kt`, persistence in `TuningPreferencesDataStore.kt`, background recording in `TrackerService`, and cockpit tile filtering in `EditSensorFieldViewModel.kt` remain 100% untouched.
5. **Strict Modularity Budgets**: File line counts in `TuningFormControls.kt` and `PhysiologyModelsSection.kt` remain strictly `< 400` lines.
6. **Parent Human Gate Invariance**: Moving parent ticket `ATT-4464` to `Erledigt` is reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+-------------------------------------------------------------------------------------------------+
|                                       UI Layer (Compose)                                        |
|                                                                                                 |
|  +-------------------------------------+             +---------------------------------------+  |
|  |       PhysiologyModelsSection       |             |          TuningFormControls           |  |
|  | - 4 Models (Coggan, PMC, EF, CP)    | ----------> | - TuningToggleItem                    |  |
|  | - 3 Subordinate Summary Toggles     |  (reused)   |   * Primary switch (scale 0.8f)       |  |
|  |   (Coggan, EF, CP)                  |             |   * Primary color tokens              |  |
|  | - Call sites unchanged              |             | - SubordinateTuningToggleItem         |  |
|  +-------------------------------------+             |   * Subordinate switch (scale 0.6f)   |  |
|                                                      |   * Compact height (height 24.dp)     |  |
|                                                      |   * Secondary color tokens            |  |
|                                                      |     (track: secondary,                |  |
|                                                      |      thumb: onSecondary)              |  |
|                                                      +---------------------------------------+  |
+-------------------------------------------------------------------------------------------------+
```

### 4.1 UI Consistency (Rule 23 — mandatory for UI changes)
* **Reference screen / component**: `TuningFormControls.kt` and `PhysiologyModelsSection.kt` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/`.
* **Reused components**: Material 3 `Switch`, `SwitchDefaults.colors()`, `Row`, `Text`.
* **Theme tokens**:
  - Typography: `MaterialTheme.typography.bodySmall` for subordinate label (contrasting with `bodyMedium` on `TuningToggleItem`).
  - Label Color: `MaterialTheme.colorScheme.onSurfaceVariant` (contrasting with `onSurface` on `TuningToggleItem`).
  - Active Switch Colors: `checkedThumbColor = MaterialTheme.colorScheme.onSecondary`, `checkedTrackColor = MaterialTheme.colorScheme.secondary`.
  - Spacing & Bounds: `Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)` on Row; `Modifier.scale(0.6f).height(24.dp)` on Switch.
* **New one-off styles & justification**: None. Follows Rule 59 in `docs/design_guidelines.md` Section 3.3.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Code Check (Code Lock Verification — Rule 4)
* Verify Gate 3 has passed before touching any production source code:
  ```bash
  python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>
  ```
* Assert return code `0` (`GATE_PASSED`).

### Step 2: Component Refinement in `TuningFormControls.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`
* **Changes**:
  - Update `SubordinateTuningToggleItem`:
    ```kotlin
    @Composable
    fun SubordinateTuningToggleItem(
        title: String,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        val contentAlpha = if (enabled) 1f else TTAlpha.Disabled
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier
                    .scale(0.6f)
                    .height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
    ```

### Step 3: Compose Multi-State `@Preview` Verification
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/PhysiologyModelsSection.kt`
* **Verification**:
  - Verify existing `@Preview` composables (`PhysiologyModelsSectionDefaultPreview` and `PhysiologyModelsSectionAllEnabledPreview`) compile and render both Light and Dark themes.

### Step 4: Unit & Contract Test Implementation in `TuningFormControlsTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControlsTest.kt`
* **Changes**:
  - Add test `testSubordinateTuningToggleItem_appliesDownscale0_6fCompactHeightAndSecondaryColors`:
    - Assert `SubordinateTuningToggleItem` contains `Modifier.scale(0.6f)`.
    - Assert `SubordinateTuningToggleItem` contains `height(24.dp)`.
    - Assert `SubordinateTuningToggleItem` configures `SwitchDefaults.colors` with `checkedThumbColor = MaterialTheme.colorScheme.onSecondary` and `checkedTrackColor = MaterialTheme.colorScheme.secondary`.
    - Assert `SubordinateTuningToggleItem` uses `MaterialTheme.typography.bodySmall` and `MaterialTheme.colorScheme.onSurfaceVariant`.
  - Add test `testSubordinateTuningToggleItem_declaresTrailingDefaultEnabledAndForwardsState`:
    - Assert `enabled: Boolean = true` parameter is present with trailing default.
    - Assert `contentAlpha` uses `TTAlpha.Disabled`.
    - Assert `Switch` forwards `enabled = enabled`.

### Step 5: Test Execution & Modularity Validation
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
  ```
* **Expected Output**:
  - All tests in `TuningFormControlsTest` pass.
  - All tests in `PhysiologyModelsVisibilityContractTest` pass.
  - All tests in `AdvancedTuningModularityTest` pass (verifying `< 400` lines).

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests during Stage 4 construction:
    `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`
  - Full clean-room regression test suite during Stage 5 verification:
    `./gradlew testDebugUnitTest`
* **Rollback Plan**:
  - Git branch isolation (`feature/ATT-4464`) ensures any issue can be cleanly reverted prior to squashing onto `sprint/2026-43.4`.
