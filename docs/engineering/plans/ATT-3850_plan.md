# Stage 3: Implementation Plan - ATT-3850: Apply bottom system navigation bar inset padding to AthleteProfileScreen scrollable content

**Ticket**: [ATT-3850](https://rainerblind.atlassian.net/browse/ATT-3850)  
**Sub-task**: [ATT-3876](https://rainerblind.atlassian.net/browse/ATT-3876) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2633](https://rainerblind.atlassian.net/browse/ATT-2633) (*UI Modernization & Visual Polish*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-396`  
**Test Mapping**: `TST-UI-359`  
**Branch**: `feature/ATT-3850`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

In `AthleteProfileScreen.kt` (`AthleteProfileContent`), the main scrollable `LazyColumn` currently applies `contentPadding = PaddingValues(16.dp)`. Because `MainActivityWithNavigation` uses edge-to-edge rendering (`enableEdgeToEdge()`), the bottom `16.dp` padding sits underneath the system navigation bar (`48.dp` 3-button or `24.dp` gesture bar), partially occluding the last measurement history row or empty-history card when scrolled to the bottom. In compliance with **Rule 41** (`docs/design_guidelines.md` Section 5.6), this plan adds `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` to the `LazyColumn` bottom `contentPadding`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-396` (*System Navigation Bar Bottom Inset Clearance in AthleteProfileScreen*)
* **Test Mapping**: `TST-UI-359` (*AthleteProfileScreen System Navigation Bar Bottom Inset Clearance Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing Athlete Profile unit, repository, and localization tests continue to pass cleanly.
2. **Thread Safety & Dispatcher Affinity**: Pure UI composable padding change; zero database or repository modifications.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AthleteProfileScreen.kt` (`com.atrainingtracker.trainingtracker.ui.athleteprofile`)
* Import `androidx.compose.foundation.layout.WindowInsets`, `androidx.compose.foundation.layout.asPaddingValues`, and `androidx.compose.foundation.layout.navigationBars`.
* In `AthleteProfileContent`, compute:
  ```kotlin
  val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  ```
* Configure `LazyColumn`:
  ```kotlin
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
          start = 16.dp,
          top = 16.dp,
          end = 16.dp,
          bottom = 16.dp + navBarBottomPadding
      ),
      verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
  ```

### Component 2: `AthleteProfileContractAndLocalizationTest.kt` (`com.atrainingtracker.trainingtracker.ui.athleteprofile`)
* Add contract test `AthleteProfileScreen applies WindowInsets.navigationBars to LazyColumn bottom contentPadding (Rule 41)` verifying that `AthleteProfileScreen.kt` imports `WindowInsets`, `asPaddingValues`, and `navigationBars`, computes `navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`, and passes `bottom = 16.dp + navBarBottomPadding` into `LazyColumn`'s `PaddingValues`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `KnownLocationsScreen.kt` (`WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`) and `AthleteProfileScreen.kt` (`docs/design_guidelines.md` Section 5.6 Rule 41).
* **Reused components**: Existing `AthleteProfileContent`, `MetricSummaryCard`, `CompactSelectorPill`, `HistoryEntryRow`, `DeleteConfirmationDialog`.
* **Theme tokens**: Spacing `16.dp` + `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`, shapes `RoundedCornerShape(12.dp)` / `8.dp`, colors `MaterialTheme.colorScheme.*`.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Verification (Rule 4)
* Command: `python3 tools/jira_util.py check-gate ATT-3876`
* Confirm `GATE_PASSED: ATT-3876 is Erledigt`.

### Step 2: Update `AthleteProfileScreen.kt` (`REQ-UI-396.1`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt`
* Changes: Import `WindowInsets`, `asPaddingValues`, and `navigationBars`, and add `navBarBottomPadding` to the `LazyColumn` `contentPadding` `bottom` parameter.

### Step 3: Update `AthleteProfileContractAndLocalizationTest.kt` (`TST-UI-359.1`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
* Changes: Add `AthleteProfileScreen applies WindowInsets.navigationBars to LazyColumn bottom contentPadding (Rule 41)` test method.

### Step 4: Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests in `com.atrainingtracker.trainingtracker.ui.athleteprofile.*` during Stage 4, followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) and on-device Pixel 10 screenshot comparison in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3850` allows full revert without affecting `sprint/2026-42.7` or `develop`.
