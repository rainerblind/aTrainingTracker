# Stage 3: Implementation Plan - ATT-3675: Replace overflowing text buttons in ZonesSettingsActivity header with compact icon actions to prevent vertical screen blowout

**Ticket**: [ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)  
**Sub-task**: [ATT-3682](https://rainerblind.atlassian.net/browse/ATT-3682) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-UI-378` (*Compact Action Buttons and Header Height Containment in ZonesSettingsActivity*)  
**Test Mapping**: `TST-UI-341` (*Compact Action Buttons & Header Height Containment Verification Suite*)  
**Branch**: `feature/ATT-3675`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

In `ZonesSettingsActivity.kt`, the top header row currently places two textual `TextButton` controls ("Zonen berechnen" and "Auf Standardwerte zurücksetzen") alongside the screen title ("Trainings Zonen") and status chip ("Berechnet"). Under German locale on mobile device viewports, the second `TextButton` overflows horizontally and wraps vertically one character per line across ~30 lines, expanding the header height to ~800dp (~95% screen height) and pushing all content off-screen.

The objective of this ticket is to refactor the header row into a compact, robust layout using Material 3 `IconButton` symbol actions (`Icons.Default.Calculate` and `Icons.Default.RestartAlt`) and bounding the title width with `maxLines = 1` and `TextOverflow.Ellipsis`, guaranteeing that header height never exceeds 64dp across any screen width or language locale.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-378` (*Compact Action Buttons and Header Height Containment in ZonesSettingsActivity*)
  * `REQ-UI-378.1`: Replace `TextButton`s with `IconButton` using `Icons.Default.Calculate` and `Icons.Default.RestartAlt`.
  * `REQ-UI-378.2`: Bounded title container and `maxLines = 1, overflow = TextOverflow.Ellipsis`.
  * `REQ-UI-378.3`: Header height containment strictly $\le 64\text{ dp}$.
  * `REQ-UI-378.4`: Accessibility parity with localized `contentDescription`.
* **Test Mapping**: `TST-UI-341` (*Compact Action Buttons & Header Height Containment Verification Suite*)
  * `TST-UI-341.1`: Header Source Contract & Architectural Test (`ZonesSettingsHeaderContractTest.kt`).
  * `TST-UI-341.2`: 9-Language ContentDescription Localization Parity (`ZoneCalculatorLocalizationTest.kt`).
  * `TST-UI-341.3`: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Functional Regression**: Clicking the Calculate icon button opens `ZoneCalculatorDialog`; clicking the Reset icon button dispatches `dataStore.resetToDefaults(currentType)` and reloads profiles.
2. **Accessibility Parity**: `IconButton`s include localized `contentDescription` matching `R.string.zone_calc_button` and `R.string.reset_to_defaults` for TalkBack screen readers.
3. **Touch Target Sizing**: `IconButton` retains default $48\text{ dp} \times 48\text{ dp}$ touch targets per Material 3 guidelines.
4. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket ATT-3675 remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ZonesSettingsActivity.kt` (Header Row Refactoring)
* Refactor the horizontal header `Row` in `ZoneSettingsScreen` (lines 228–282):
  * Left title group: Apply `Modifier.weight(1f, fill = false)` to prevent title from expanding beyond available space. Add `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to the title `Text`.
  * Right action group: Replace `TextButton` controls with `IconButton` controls containing:
    * `Icon(imageVector = Icons.Default.Calculate, contentDescription = stringResource(R.string.zone_calc_button), tint = MaterialTheme.colorScheme.onPrimaryContainer)`
    * `Icon(imageVector = Icons.Default.RestartAlt, contentDescription = stringResource(R.string.reset_to_defaults), tint = MaterialTheme.colorScheme.onPrimaryContainer)`
  * Spacing: Use `horizontalArrangement = Arrangement.spacedBy(4.dp)` between icon actions.

### Component 2: `ZonesSettingsHeaderContractTest.kt` (Architectural AST Test)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZonesSettingsHeaderContractTest.kt` to statically inspect `ZonesSettingsActivity.kt` and assert:
  * Zero `TextButton` calls exist within the header block.
  * `Icons.Default.Calculate` and `Icons.Default.RestartAlt` are explicitly used.
  * Title `Text` has `maxLines = 1` and `TextOverflow.Ellipsis`.

### UI Consistency (Rule 23 — mandatory for UI tickets)
* **Reference screen / component**: Top bar headers across `SegmentsActivity.kt`, `RouteTabbedScreen.kt`, and `ZonesSettingsActivity.kt`.
* **Reused components**: Material 3 `IconButton`, `Icon`, `Icons.Default.Calculate`, `Icons.Default.RestartAlt`.
* **Theme tokens**:
  * Surface background: `MaterialTheme.colorScheme.primaryContainer`.
  * Icon / Text color: `MaterialTheme.colorScheme.onPrimaryContainer`.
  * Spacing: `horizontal = 16.dp, vertical = 4.dp`, action spacing `4.dp` per Section 5.2.
* **New one-off styles & justification**: None. 100% standard Material 3 tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor Header Row in `ZonesSettingsActivity.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
* Changes:
  * Import `androidx.compose.material.icons.filled.Calculate` and `androidx.compose.material.icons.filled.RestartAlt`.
  * Replace the two `TextButton`s in lines 259–281 with compact `IconButton`s.
  * Add `Modifier.weight(1f, fill = false)` and `maxLines = 1, overflow = TextOverflow.Ellipsis` to the title row.

### Step 2: Create Architectural Contract Test `ZonesSettingsHeaderContractTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZonesSettingsHeaderContractTest.kt`
* Changes:
  * Add contract assertions verifying header structure, icon vectors, and ellipsis overflow.

### Step 3: Run Targeted Unit & Localization Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*"
  ```
* Expected Outcome: 100% pass rate across all zone math, dialog, localization, and contract tests.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted test execution: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*"` (~10–15s).
  2. Full clean-room regression suite in Stage 5: `./gradlew testDebugUnitTest` (~2–3m).
  3. Visual verification on physical Google Pixel 10 during Ceremony 2.
* **Rollback**: Branch `feature/ATT-3675` is fully isolated; changes can be reverted with `git checkout sprint/2026-42.5` without touching other features.
