# Stage 2: Requirement & Test Specification - ATT-4464: [Verbesserung] Significantly reduce switch size and visual weight for subordinate 'Show in Workout Summary' toggles in Physiology & Training Models

**Ticket**: [ATT-4464](https://rainerblind.atlassian.net/browse/ATT-4464)  
**Sub-task**: `[Req & Test Spec]` (To be created via `submit-stage`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-424`, `REQ-UI-424.1`  
**Test Spec ID**: `TST-UI-388` (`TST-UI-388.3`, `TST-UI-42402`)  
**Branch**: `feature/ATT-4464`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-424, REQ-UI-424.1)

### 1.1 Problem Statement & Rationale
During the Sprint 2026-43.3 Review on Pixel 10 (ticket `ATT-4464` and retrospective `ATT-4433`), the user evaluated the subordinate *"In Workout-Zusammenfassung anzeigen"* (*"Show in Workout Summary"*) toggles in `Settings -> Advanced Settings -> Physiology & Training Models` (`PhysiologyModelsSection.kt`).

Although `SubordinateTuningToggleItem` indented the row by `16.dp` and applied smaller typography (`MaterialTheme.typography.bodySmall`), the toggle control itself rendered as a prominent Material 3 `Switch` of the **exact same physical size** and **visual weight** as the parent model switch directly above it:
- Both parent `TuningToggleItem` and child `SubordinateTuningToggleItem` applied `Modifier.scale(0.8f)`.
- Both parent and child switches drew default Material 3 color tokens (`primary` track and `onPrimary` thumb).
- Because `Switch` allocates unscaled touch target layout bounds unless constrained, the subordinate row reserved unneeded whitespace.

This caused the child toggle to visually compete with the primary model header, violating Rule 59 and `docs/design_guidelines.md` Section 3.3. Under the aligned Sprint Planning 2026-43.4 agreement:
- `SubordinateTuningToggleItem` SHALL remain a `Switch` (avoiding checkboxes or alternate controls to preserve visual harmony).
- The subordinate switch SHALL downscale significantly further than the parent `0.8f` switch, applying `Modifier.scale(0.6f)`.
- The subordinate switch SHALL enforce a compact vertical layout height (`Modifier.height(24.dp)`) so the scaled graphic does not inflate the row height.
- The subordinate switch SHALL be styled using Material 3 `secondary` color tokens (`checkedThumbColor = MaterialTheme.colorScheme.onSecondary`, `checkedTrackColor = MaterialTheme.colorScheme.secondary`) instead of `primary`, ensuring it is distinctly smaller, lighter, and subordinate to the parent model toggle.

### 1.2 Functional & Architectural Requirements

#### 1.2.1 Subordinate Switch Geometry & Scale (`TuningFormControls.kt`, `REQ-UI-424.1`)
1. In `TuningFormControls.kt`, `SubordinateTuningToggleItem` SHALL apply `Modifier.scale(0.6f)` and `Modifier.height(24.dp)` to its inner `Switch`.
2. The outer row SHALL retain `fillMaxWidth()` with start padding of `16.dp` and vertical padding `top = 2.dp, bottom = 2.dp`.
3. The typography SHALL remain `MaterialTheme.typography.bodySmall` in `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)`.

#### 1.2.2 Subordinate Switch Color Tokens (`TuningFormControls.kt`, `REQ-UI-424.1`)
1. In `TuningFormControls.kt`, `SubordinateTuningToggleItem` SHALL style the `Switch` using:
   ```kotlin
   colors = SwitchDefaults.colors(
       checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
       checkedTrackColor = MaterialTheme.colorScheme.secondary
   )
   ```
2. Unchecked states and disabled states SHALL cleanly delegate to default Material 3 color mapping modulated by `enabled` and `contentAlpha`.

#### 1.2.3 Backward Compatibility & API Stability (`TuningFormControls.kt`, `REQ-UI-424.1`)
1. The public function signature and default parameter values of `SubordinateTuningToggleItem` SHALL remain strictly backward compatible:
   ```kotlin
   @Composable
   fun SubordinateTuningToggleItem(
       title: String,
       isChecked: Boolean,
       onCheckedChange: (Boolean) -> Unit,
       modifier: Modifier = Modifier,
       enabled: Boolean = true
   )
   ```
2. Call sites in `PhysiologyModelsSection.kt` (lines 91, 118, 135) SHALL require zero parameter modifications.

#### 1.2.4 Modularity & Invariant Budgets (`REQ-UI-424.5`)
1. File line count of `TuningFormControls.kt` SHALL strictly remain `< 400` lines.
2. File line count of `PhysiologyModelsSection.kt` SHALL strictly remain `< 400` lines.
3. Telemetry recording in `TrackerService`, sensor filtering in `EditSensorFieldViewModel`, and presentation gating in `WorkoutDetails` SHALL remain 100% unaffected.

#### 1.2.5 9-Language Localization Parity (`REQ-UI-424.6`)
1. No user-visible strings are added, removed, or modified. Existing string `tuning_physiology_show_in_workout_summary` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) remains 100% intact.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Refines Clause 1 and Acceptance Criteria of `REQ-UI-424` (*Show in Workout Summary Toggles for Physiology Models in Advanced Settings, Rule 59 Subordinate Toggle Hierarchy, and Coggan-Only Presentation Gating in WorkoutDetails*).
  - Target files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControlsTest.kt`, `docs/requirements.md`, `docs/tests.md`.
* **Historical Origin & Commit Trace**:
  - `ATT-4369` (commit `157684c0`, Sprint 2026-43.2): Initial introduction of summary toggles reusing full `TuningToggleItem` rows.
  - `ATT-4432` (commit `e7b34e7e`, Sprint 2026-43.3): Introduced `SubordinateTuningToggleItem` and Rule 59 with `Modifier.scale(0.8f)` and lighter typography, removing the unrequested Basic Power toggle.
  - `ATT-4433` (commit `7065376d`, Sprint 2026-43.3 Retro) & `ATT-4464` (Sprint 2026-43.4): Identified that because `TuningToggleItem` was already `0.8f`, the subordinate switch was visually identical in size and weight on Pixel 10; updated Rule 59 in `docs/design_guidelines.md` §3.3 and sprint planning.
* **Root Reason for Existing Formulation**:
  - `Modifier.scale(0.8f)` was applied in `ATT-4432` under the assumption that `TuningToggleItem` was unscaled (`1.0f`). Because `TuningToggleItem` had already scaled down to `0.8f`, parent and child switches ended up with identical bounding geometry and identical primary blue color tokens, competing for visual attention.
* **Preservation of Core Invariants**:
  - Control cardinality and type: `SubordinateTuningToggleItem` remains a `Switch`.
  - Master model toggle isolation: All 4 models in `PhysiologyModelsSection.kt` and their 3 subordinate summary toggles retain exact functional parity.
  - Modularity budgets: File line counts remain strictly `< 400` lines in tuning and `< 500` lines in `WorkoutDetails`.
  - 100% clean-room test suite pass rate strictly preserved.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Downscaled 0.6f Switch with Compact Height)**:
  * *Given* `Settings -> Advanced Settings -> Physiology & Training Models` is opened with a master model enabled,
  * *When* `SubordinateTuningToggleItem` is rendered for *"Show in Workout Summary"*,
  * *Then* the switch applies `Modifier.scale(0.6f)` and `Modifier.height(24.dp)`, ensuring it is visibly smaller than the parent `0.8f` switch and does not inflate the row height.

* **Criterion 2 (Secondary Color Styling Hierarchy)**:
  * *Given* an active subordinate summary switch in checked state,
  * *When* rendered on screen,
  * *Then* the checked track color is `MaterialTheme.colorScheme.secondary` and the checked thumb color is `MaterialTheme.colorScheme.onSecondary`, visually distinguishing it from the primary blue accent of the master switch.

* **Criterion 3 (Disabled State & Interaction Alpha)**:
  * *Given* `SubordinateTuningToggleItem` with `enabled = false`,
  * *When* rendered,
  * *Then* label text alpha is multiplied by `TTAlpha.Disabled` (0.38f), the switch forwards `enabled = false`, and gestures are disabled.

* **Criterion 4 (Contract Invariance & Persistence Isolation)**:
  * *Given* changing the state of `SubordinateTuningToggleItem`,
  * *When* toggled,
  * *Then* preference keys in `TuningPreferencesDataStore` update reactively, live cockpit sensor visibility is unaffected, and all 4 models in `PhysiologyModelsSection.kt` operate normally.

---

### 1.5 System Invariants
1. **Control Type Harmony**: `SubordinateTuningToggleItem` uses a Material 3 `Switch` (no checkboxes).
2. **Master Switch Independence**: Parent `TuningToggleItem` maintains its `0.8f` scale, 48.dp touch target, and primary color tokens.
3. **Strict Modularity Limits**: Line counts in `TuningFormControls.kt` and `PhysiologyModelsSection.kt` remain `< 400` lines.
4. **9-Language Localization Parity**: 100% parity preserved across all 9 canonical application locales with zero missing or dead strings.
5. **Parent Ticket Governance**: Human decision gate on parent ticket `ATT-4464` remains strictly intact.

---

## 2. Test Specification (TST-UI-388, TST-UI-42402)

### Test Case 1: `SubordinateTuningToggleItem` Visual Scale, Compact Height & Color Tokens (`TST-UI-388.3a`, `TST-UI-42402`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControlsTest.kt`
* **Preconditions**: Source file `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`.
* **Actions & Expected Results**:
  1. Verify `SubordinateTuningToggleItem` composable applies `Modifier.scale(0.6f)`.
  2. Verify `SubordinateTuningToggleItem` composable applies `height(24.dp)` to the `Switch`.
  3. Verify `SubordinateTuningToggleItem` configures `SwitchDefaults.colors` with `checkedThumbColor = MaterialTheme.colorScheme.onSecondary` and `checkedTrackColor = MaterialTheme.colorScheme.secondary`.
  4. Verify `SubordinateTuningToggleItem` uses `MaterialTheme.typography.bodySmall` and `MaterialTheme.colorScheme.onSurfaceVariant`.

### Test Case 2: `SubordinateTuningToggleItem` API Compatibility & Disabled Alpha (`TST-UI-388.3b`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControlsTest.kt`
* **Preconditions**: `TuningFormControls.kt` source and runtime contract.
* **Actions & Expected Results**:
  1. Verify `SubordinateTuningToggleItem` parameter signature has `enabled: Boolean = true` and `modifier: Modifier = Modifier`.
  2. Verify `contentAlpha` calculation uses `TTAlpha.Disabled`.
  3. Verify `Switch` forwards `enabled = enabled`.

### Test Case 3: `PhysiologyModelsSection` Hierarchy & Modularity Contract (`TST-UI-388.3c`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Preconditions**: `PhysiologyModelsSection.kt` and `TuningFormControls.kt`.
* **Actions & Expected Results**:
  1. Verify `PhysiologyModelsSection.kt` renders subordinate toggles via `SubordinateTuningToggleItem` for Coggan, Aerobic Efficiency, and Critical Power models.
  2. Verify line count of `TuningFormControls.kt` is strictly `< 400` lines.
  3. Verify line count of `PhysiologyModelsSection.kt` is strictly `< 400` lines.

### Test Case 4: 9-Language Localization Audit (`TST-UI-388.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/main/res/values*/strings.xml`
* **Actions & Expected Results**:
  1. Verify `tuning_physiology_show_in_workout_summary` exists in `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
  2. Verify zero untranslated or missing entries across all 9 locales.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-388.6`)
* **Scope**: Project-wide Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Actions & Expected Results**:
  1. Assert 100% unit and contract tests pass with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-388.3a` / `TST-UI-42402` | Unit / Contract | `SubordinateTuningToggleItem` scale, height, colors | `REQ-UI-424.1` | Specified |
| `TST-UI-388.3b` | Unit | `SubordinateTuningToggleItem` API & alpha | `REQ-UI-424.1` | Specified |
| `TST-UI-388.3c` | Contract | `PhysiologyModelsSection` Rule 59 & modularity | `REQ-UI-424.1`, `REQ-UI-424.5` | Specified |
| `TST-UI-388.5` | Localization | 9-Language Strings Parity | `REQ-UI-424.6` | Specified |
| `TST-UI-388.6` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
