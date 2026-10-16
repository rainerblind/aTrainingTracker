# Stage 2: Requirement & Test Specification - ATT-3850: Apply bottom system navigation bar inset padding to AthleteProfileScreen scrollable content

**Ticket**: [ATT-3850](https://rainerblind.atlassian.net/browse/ATT-3850)  
**Sub-task**: [ATT-3875](https://rainerblind.atlassian.net/browse/ATT-3875) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2633](https://rainerblind.atlassian.net/browse/ATT-2633) (*UI Modernization & Visual Polish*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-396` (*System Navigation Bar Bottom Inset Clearance in AthleteProfileScreen*)  
**Test Spec ID**: `TST-UI-359`  
**Branch**: `feature/ATT-3850`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (`REQ-UI-396`)

### 1.1 Problem Statement & Rationale
When `AthleteProfileScreen` (`AthleteProfileContent`) is hosted inside edge-to-edge `MainActivityWithNavigation`, its `LazyColumn` previously used a fixed `contentPadding = PaddingValues(16.dp)`, causing the bottommost measurement history row (`HistoryEntryRow`) or empty-history card to be partially occluded behind the Android system navigation bar when scrolled all the way down. `REQ-UI-396` enforces Rule 41 (`docs/design_guidelines.md` Section 5.6) by adding `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` to the `LazyColumn`'s bottom `contentPadding`.

### 1.2 Functional & Architectural Requirements
1. **Bottom Navigation Bar Inset in `LazyColumn` `contentPadding` (`REQ-UI-396.1` — Rule 41)**:
   * `AthleteProfileContent` in `AthleteProfileScreen.kt` SHALL compute:
     ```kotlin
     val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
     ```
   * The main `LazyColumn` in `AthleteProfileContent` SHALL configure:
     ```kotlin
     contentPadding = PaddingValues(
         start = 16.dp,
         top = 16.dp,
         end = 16.dp,
         bottom = 16.dp + navBarBottomPadding
     )
     ```
   * Content SHALL continue to scroll smoothly edge-to-edge behind the translucent navigation bar while resting `16.dp` above the top edge of the system navigation bar at maximum scroll extent.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Bottom Inset Clearance)**:
  * *Given* `AthleteProfileScreen.kt` (`AthleteProfileContent`) hosted inside edge-to-edge `MainActivityWithNavigation`,
  * *When* the `LazyColumn` is scrolled to the bottom,
  * *Then* its `contentPadding` includes `bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` so the bottommost item clears the system navigation bar with `16.dp` spacing.

### 1.4 System Invariants
* Top header `.statusBarsPadding()` and `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` remain intact.
* `16.dp` horizontal, top, and inter-item spacing (`docs/design_guidelines.md` Section 5.2) remain intact.
* 100% pass rate across all existing unit and localization tests.

---

## 2. Test Specification (`TST-UI-359`)

### Test Case 1: `AthleteProfileScreen applies WindowInsets.navigationBars to LazyColumn bottom contentPadding (Rule 41)` (`TST-UI-359.1`)
* **Scope**: Architectural Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
* **Preconditions**: `AthleteProfileScreen.kt` source file on disk.
* **Action**: Inspect `AthleteProfileScreen.kt` imports and `AthleteProfileContent`'s `LazyColumn` block.
* **Expected Result**:
  * `AthleteProfileScreen.kt` imports `WindowInsets`, `asPaddingValues`, and `navigationBars`.
  * `AthleteProfileContent` computes `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` and passes `bottom = 16.dp + navBarBottomPadding` into `LazyColumn`'s `PaddingValues`.
  * Top header row retains `.statusBarsPadding()`.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-UI-359.2`)
* **Scope**: Localization Parity Test (`AthleteProfileContractAndLocalizationTest.kt`)
* **Goal**: Verify all 22 Athlete Profile string keys and `%1$s`/`%2$s`/`%3$s` positional specifiers across all 9 locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`).
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-359.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-359.1` | Contract | `AthleteProfileContent` (`LazyColumn` `contentPadding`) | `REQ-UI-396.1` | Specified |
| `TST-UI-359.2` | Localization | `AthleteProfileContractAndLocalizationTest` | `REQ-UI-396`, `REQ-UI-106` | Specified |
| `TST-UI-359.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
