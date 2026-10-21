# Stage 2: Requirement & Test Specification - ATT-4513: Use a lighter checked track color for subordinate 'Show in Workout Summary' switches in Light Mode

**Ticket**: [ATT-4513](https://rainerblind.atlassian.net/browse/ATT-4513) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4513`  
**Requirement Mapping**: `REQ-UI-424`, `REQ-UI-424.1` | **Test Spec ID**: `TST-UI-388` (`TST-UI-388.3`)  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-UI-424, REQ-UI-424.1)

### 1.1 Problem Statement & Rationale
In `ATT-4464`, `SubordinateTuningToggleItem` in `TuningFormControls.kt` downscaled subordinate switches to `0.6f` with `checkedTrackColor = MaterialTheme.colorScheme.secondary`. In Light Mode, `secondary` resolves to `LightSecondary = #0000CD` (relative luminance ~0.07), creating an aggressively dark navy checked track that visually overpowers the master toggle (`primary = #1464F4`, luminance ~0.16) and violates Rule 59 / `docs/design_guidelines.md` §3.3.

### 1.2 Functional & Architectural Requirements
1. In `TuningFormControls.kt`, `SubordinateTuningToggleItem` SHALL style the inner `Switch` using:
   ```kotlin
   colors = SwitchDefaults.colors(
       checkedThumbColor = MaterialTheme.colorScheme.primary,
       checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
   )
   ```
2. Unchecked and disabled switch states SHALL continue to delegate to default Material 3 color mapping modulated by `enabled` and `TTAlpha.Disabled`.
3. In Light Mode, the subordinate track SHALL render as a soft pastel blue (`#95B9FA` on white) with solid primary blue thumb, distinctly lighter than the parent toggle. In Dark Mode, the track SHALL render as a muted cyan-blue (`#55647F` on dark surface) with bright sky-blue thumb (`#A6C8FF`), preserving legibility.
4. Call sites in `PhysiologyModelsSection.kt` (lines 91, 118, 135) and function signature SHALL remain strictly untouched.
5. Invariants: `TuningFormControls.kt` file length MUST remain strictly `< 400` lines (Rule 60 / Rule 29).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-UI-424.1` in `TuningFormControls.kt`, `TuningFormControlsTest.kt`, and `PhysiologyModelsVisibilityContractTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-4369` (`157684c0`, summary toggles added), `ATT-4432` (`e7b34e7e`, `SubordinateTuningToggleItem` created), and `ATT-4464` (`0deaf91a`, downscaled to 0.6f with `secondary` colors).
* **Root Reason for Existing Formulation**: `ATT-4464` selected `secondary` to visually soften subordinate toggles without anticipating that `LightSecondary` was `#0000CD`, inadvertently making the Light Mode track darker than primary.
* **Preservation of Core Invariants**: 0.6f scale, 24.dp height, function signature, modularity (< 400 lines), and 100% test pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* `Settings -> Advanced Settings -> Physiology & Training Models` in Light Mode with a master toggle ON,
* *When* `SubordinateTuningToggleItem` renders for *"Show in Workout Summary"*,
* *Then* the checked track renders with `MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)` and thumb renders with `MaterialTheme.colorScheme.primary`, visibly lighter than the master switch.
* *Given* the same screen in Dark Mode,
* *When* inspected,
* *Then* the subordinate checked track remains clearly legible against dark surfaces.

---

## 2. Test Specification (TST-UI-388)

### Test Case 1: `SubordinateTuningToggleItem` Primary Alpha Color Tokens (`TST-UI-388.3a`)
* **Scope**: Unit / Contract Test | **Target**: `TuningFormControlsTest.kt`
* **Verification**: Verify `SubordinateTuningToggleItem` declares `SwitchDefaults.colors` with `checkedThumbColor = MaterialTheme.colorScheme.primary` and `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)`.

### Test Case 2: `PhysiologyModelsVisibilityContractTest` Color Contract (`TST-UI-388.3b`)
* **Scope**: Contract Test | **Target**: `PhysiologyModelsVisibilityContractTest.kt`
* **Verification**: Verify line 324 assertion checks `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)`.

### Test Case 3: Modularity & Clean-Room Regression (`TST-UI-388.3c`, `TST-UI-388.6`)
* **Scope**: Modularity & Regression Test | **Target**: `TuningFormControls.kt` & Gradle suite
* **Verification**: Verify `TuningFormControls.kt` line count is `< 400` lines; `./gradlew testDebugUnitTest` passes 100%.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-388.3a` | Unit | `TuningFormControlsTest.testSubordinateTuningToggleItem_...` | `REQ-UI-424.1` | Specified |
| `TST-UI-388.3b` | Contract | `PhysiologyModelsVisibilityContractTest.testSubordinateTuningToggle...` | `REQ-UI-424.1` | Specified |
| `TST-UI-388.3c` | Modularity | `TuningFormControls.kt` line count budget (< 400 lines) | `REQ-UI-424.5` | Specified |
| `TST-UI-388.6` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
