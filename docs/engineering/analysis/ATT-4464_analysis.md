# Stage 1 Analysis: ATT-4464 - [Verbesserung] Significantly reduce switch size and visual weight for subordinate 'Show in Workout Summary' toggles in Physiology & Training Models

**Ticket**: [ATT-4464](https://rainerblind.atlassian.net/browse/ATT-4464)  
**Sub-task**: [ATT-4466](https://rainerblind.atlassian.net/browse/ATT-4466) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4464`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-43.3 Review on Pixel 10 (as documented in `ATT-4464` and the Sprint Retrospective `ATT-4433`), the user evaluated the newly added subordinate *"In Workout-Zusammenfassung anzeigen"* (*"Show in Workout Summary"*) toggles in `Settings -> Advanced Settings -> Physiology & Training Models` (`PhysiologyModelsSection.kt`).

Although the subordinate toggle row was indented by `16.dp` and rendered with smaller typography (`MaterialTheme.typography.bodySmall`), the toggle control itself rendered as a prominent Material 3 `Switch` of the **exact same physical size** and **visual weight** as the parent model switch directly above it:
- Both parent and child switches were scaled to `0.8f` (`Modifier.scale(0.8f)`).
- Both parent and child switches used default Material 3 color tokens (`primary` track and `onPrimary` thumb).

Because the parent `TuningToggleItem` and the child `SubordinateTuningToggleItem` shared identical switch dimensions and high-contrast primary blue styling, the subordinate switch visually competed with the primary model switch, violating the visual hierarchy principles mandated by Rule 59 and `docs/design_guidelines.md` Section 3.3.

### Aligned Goal (Sprint Planning 2026-43.4):
- Keep `SubordinateTuningToggleItem` as a `Switch` (avoiding checkboxes or alternate controls to preserve visual harmony).
- Downscale the subordinate switch significantly further than the parent `0.8f` switch, applying `Modifier.scale(0.6f)`.
- Apply compact vertical layout height (`Modifier.height(24.dp)`) so the scaled switch does not inflate the row height.
- Style the subordinate switch using Material 3 `secondary` color tokens (`checkedThumbColor = MaterialTheme.colorScheme.onSecondary`, `checkedTrackColor = MaterialTheme.colorScheme.secondary`) instead of `primary`, ensuring it is distinctly smaller, lighter, and subordinate to the parent model toggle.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Control Scaling Parity in `TuningFormControls.kt`
In `TuningFormControls.kt`, the primary `TuningToggleItem` composable was historically refactored to apply `Modifier.scale(0.8f)`:
```kotlin
// TuningFormControls.kt line 142
Switch(
    checked = isChecked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    modifier = Modifier.scale(0.8f)
)
```
During Sprint 2026-43.3 (`ATT-4432`), when `SubordinateTuningToggleItem` was created, `Modifier.scale(0.8f)` was applied to the child switch under the erroneous assumption that the parent switch was unscaled (`1.0f`):
```kotlin
// TuningFormControls.kt line 238
Switch(
    checked = isChecked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    modifier = Modifier.scale(0.8f)
)
```
Consequently:
- Parent switch scale: $0.8 \times \text{M3 default} = 41.6\text{ dp wide} \times 25.6\text{ dp high}$.
- Child switch scale: $0.8 \times \text{M3 default} = 41.6\text{ dp wide} \times 25.6\text{ dp high}$.

The parent and child switches ended up with identical bounding geometry.

### 2.2 Unconstrained Layout Bounds & Row Inflation
In Jetpack Compose Material 3, `Switch` internally applies `Modifier.minimumInteractiveComponentSize()`, which enforces a 48.dp minimum touch target. While `Modifier.scale()` applies a graphic layer transformation that visually shrinks the drawn switch, the unconstrained Compose layout pass allocates unscaled bounds unless explicit height constraints (`Modifier.height(...)`) are enforced. Without explicit layout height, the subordinate row reserves unnecessary vertical whitespace around the switch. Enforcing `Modifier.height(24.dp)` matches the layout space to the visual scale of the $0.6\text{x}$ switch ($32\text{ dp} \times 0.6 \approx 19.2\text{ dp}$).

### 2.3 Shared Primary Color Tokens
By default, Material 3 `Switch` renders checked states using `MaterialTheme.colorScheme.primary` for the track and `MaterialTheme.colorScheme.onPrimary` for the thumb. Without explicit color overrides, the child switch drew the same vibrant primary blue accent as the master feature switch, dominating visual attention despite its subordinate role.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Downscale `Switch` inside `SubordinateTuningToggleItem` (`TuningFormControls.kt`) from `0.8f` to `0.6f` (`Modifier.scale(0.6f)`).
  2. Apply compact vertical layout height (`Modifier.height(24.dp)`) to the subordinate switch to prevent row height inflation.
  3. Style `SubordinateTuningToggleItem` using Material 3 `secondary` color tokens:
     ```kotlin
     colors = SwitchDefaults.colors(
         checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
         checkedTrackColor = MaterialTheme.colorScheme.secondary
     )
     ```
  4. Preserve backward-compatible public signature and parameter defaults for `SubordinateTuningToggleItem`.
  5. Add automated unit test assertions in `TuningFormControlsTest.kt` verifying `0.6f` scale, `height(24.dp)`, and `secondary` color tokens.
  6. Maintain strict modularity bounds (< 400 lines in `TuningFormControls.kt` and `PhysiologyModelsSection.kt`).
  7. Update living documentation in `docs/requirements.md` (`REQ-UI-424.1` and Acceptance Criteria) and `docs/tests.md` (`TST-UI-388.3`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. **No Checkbox Replacement**: Aligned sprint planning explicitly retains `Switch` rather than `Checkbox` to keep design language consistent.
  2. **No Changes to Parent `TuningToggleItem`**: The master switch retains its standard `0.8f` scale, 48.dp touch target, and primary color tokens.
  3. **No Changes to DataStore Persistence or Preference Keys**: Preference reading and writing via `TuningPreferencesDataStore` and all 3 summary keys remain untouched.
  4. **No Telemetry or Live Cockpit Changes**: Background recording in `TrackerService`, sensor filtering in `EditSensorFieldViewModel`, and presentation gating in `WorkoutDetails` remain 100% identical.
  5. **No Localization Changes**: All string resources across 9 locales are finalized and unchanged.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - Refines Clause 1 and Acceptance Criteria of `REQ-UI-424` (*Show in Workout Summary Toggles for Physiology Models in Advanced Settings, Rule 59 Subordinate Toggle Hierarchy, and Coggan-Only Presentation Gating in WorkoutDetails*).
  - Target files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControlsTest.kt`, `docs/requirements.md`, `docs/tests.md`.
* **Historical Origin & Commit Trace**:
  - `ATT-4369` (commit `157684c0`, Sprint 2026-43.2): Initial introduction of summary toggles reusing full `TuningToggleItem` rows.
  - `ATT-4432` (commit `e7b34e7e`, Sprint 2026-43.3): Introduced `SubordinateTuningToggleItem` and Rule 59 with `Modifier.scale(0.8f)` and lighter typography, removing the unrequested Basic Power toggle.
  - `ATT-4433` (commit `7065376d`, Sprint 2026-43.3 Retro): Identified that because `TuningToggleItem` was already `0.8f`, the subordinate switch was identical in size on Pixel 10; updated Rule 59 in `docs/design_guidelines.md` §3.3 and created `ATT-4464`.
* **Root Reason for Existing Formulation**:
  - `Modifier.scale(0.8f)` was introduced in `ATT-4432` under the assumption that `TuningToggleItem` was unscaled (`1.0f`). In reality, `TuningToggleItem` had already scaled down to `0.8f`, resulting in zero size differentiation between parent and child switches.
* **Preservation of Core Invariants**:
  - Control cardinality and type: `SubordinateTuningToggleItem` remains a `Switch`.
  - Master model toggle isolation: All 4 models in `PhysiologyModelsSection.kt` and their 3 subordinate summary toggles retain exact functional parity.
  - Modularity budgets: File line counts remain strictly `< 400` lines.
  - 100% clean-room test suite pass rate strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Component Refinement in `TuningFormControls.kt`
Refactor `SubordinateTuningToggleItem` in `TuningFormControls.kt` to:
1. Apply `Modifier.scale(0.6f).height(24.dp)` to the `Switch`.
2. Configure `SwitchDefaults.colors` with `MaterialTheme.colorScheme.onSecondary` for checked thumb and `MaterialTheme.colorScheme.secondary` for checked track:

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

### 5.2 Verification in `TuningFormControlsTest.kt`
Add dedicated unit test assertions to `TuningFormControlsTest.kt`:
- Verify `SubordinateTuningToggleItem` specifies `Modifier.scale(0.6f)`.
- Verify `SubordinateTuningToggleItem` specifies `height(24.dp)`.
- Verify `SubordinateTuningToggleItem` specifies `SwitchDefaults.colors` with `checkedThumbColor = MaterialTheme.colorScheme.onSecondary` and `checkedTrackColor = MaterialTheme.colorScheme.secondary`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in settings persistence (`TuningPreferencesDataStore`).
  2. Zero regression in presentation gating in `WorkoutDetails.kt` or sensor selection in `EditSensorFieldViewModel.kt`.
  3. Modularity limits strictly maintained (< 400 lines in `TuningFormControls.kt` and `PhysiologyModelsSection.kt`).
  4. 100% clean-room unit test pass rate under `./gradlew testDebugUnitTest`.
  5. Parent ticket Human Decision Gate remains strictly enforced.

* **Risk Rating**: **LOW**
  - **Justification**: The modification is purely a localized visual refinement of `SubordinateTuningToggleItem` within `TuningFormControls.kt`. It does not affect database schemas, background threads, Android platform lifecycles, or calculation algorithms.
