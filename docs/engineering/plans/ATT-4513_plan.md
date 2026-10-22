# Stage 3: Implementation Plan - ATT-4513: Use a lighter checked track color for subordinate 'Show in Workout Summary' switches in Light Mode

**Ticket**: [ATT-4513](https://rainerblind.atlassian.net/browse/ATT-4513) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4513`  
**Requirement Mapping**: `REQ-UI-424.1` | **Test Mapping**: `TST-UI-388.3a`, `TST-UI-388.3b`, `TST-UI-388.3c`, `TST-UI-388.6`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background
In `ATT-4464`, `SubordinateTuningToggleItem` (`TuningFormControls.kt`) used `checkedTrackColor = MaterialTheme.colorScheme.secondary`. In Light Mode, `secondary` is `LightSecondary = Color(0xFF0000CD)` (dark navy blue, luminance ~0.07), which is darker and visually heavier than `LightPrimary = Color(0xFF1464F4)` (luminance ~0.16). This violates Rule 59 / `docs/design_guidelines.md` §3.3.

---

## 2. Proposed Architectural Changes (SWE.2)

| Layer / File | Responsibility & Modification |
| :--- | :--- |
| **Compose UI**<br>`TuningFormControls.kt` | Update `SubordinateTuningToggleItem` `SwitchDefaults.colors`: set `checkedThumbColor = MaterialTheme.colorScheme.primary` and `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)`. |
| **Unit Test**<br>`TuningFormControlsTest.kt` | Update assertions to check `checkedThumbColor = MaterialTheme.colorScheme.primary` and `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)`. |
| **Contract Test**<br>`PhysiologyModelsVisibilityContractTest.kt` | Update line 324 assertion to verify `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)`. |
| **Contract Test**<br>`PhysiologyTuningSectionContractTest.kt` | Verify contract tokens and execute relative luminance math check confirming blended track over white surface is significantly lighter than `LightPrimary` and `LightSecondary`. |

### UI Consistency (Rule 23)
* **Reference**: `SubordinateTuningToggleItem` under `PhysiologyModelsSection.kt`.
* **Tokens**: Reuses `MaterialTheme.colorScheme.primary` with `0.45f` alpha for track, solid `primary` for thumb, `scale(0.6f)`, `height(24.dp)`, `bodySmall` with `onSurfaceVariant`. Zero one-off styles.

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Update Switch Colors in `TuningFormControls.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`
* Change: In `SubordinateTuningToggleItem`, replace `SwitchDefaults.colors` parameters:
  `checkedThumbColor = MaterialTheme.colorScheme.primary`,
  `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)`.
* Keep line count `< 400` lines (currently 280 lines).

### Step 2: Update Existing Contract & Unit Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControlsTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* Change: Update expectations from `secondary` to `primary` / `primary.copy(alpha = 0.45f)`.

### Step 3: Implement `PhysiologyTuningSectionContractTest.kt` (`TST-UI-388.3`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyTuningSectionContractTest.kt`
* Change: Add contract test verifying `SubordinateTuningToggleItem` color declarations, plus mathematical relative luminance test asserting `LightPrimary.copy(alpha = 0.45f)` on `LightSurface` has luminance $> 0.40$, strictly lighter than `LightPrimary` (~0.16) and `LightSecondary` (~0.05).

### Step 4: Targeted Test Execution
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`

---

## 4. System Invariants & Rollback Plan
* **Invariants**: Modularity budgets (`< 400` lines in tuning files); unchanged `SubordinateTuningToggleItem` signature; unchanged `DataStore` keys and defaults; 100% test pass rate.
* **Rollback Plan**: All changes isolated on `feature/ATT-4513`; git checkout/revert if needed.
