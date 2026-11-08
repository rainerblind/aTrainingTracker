# Stage 2: Requirement & Test Specification - ATT-5851: MapDetailLayout: Prevent floating map buttons (Share & overlay FABs) from colliding with system status bar when header collapses

**Ticket**: [ATT-5851](https://rainerblind.atlassian.net/browse/ATT-5851)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-491` (*MapDetailLayout Status Bar Inset Clearance for Collapsed Header Floating Controls*)  
**Test Spec ID**: `TST-UI-491`  
**Branch**: `feature/ATT-5851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-491)

### 1.1 Problem Statement & Rationale
In full-screen detail views utilizing `MapDetailLayout.kt` with `useStatusBarsPadding == true` (Favorite Routes, Route Details, Segment Details, Climbs), collapsing the header offscreen via downward scroll reduces `currentTopPaddingPx` to `0`. While `ATrainingTrackerMap` bleeds edge-to-edge behind the translucent status bar, floating controls inside `mapBox` (Share FAB and `overlay()` action buttons) shift up against the screen top boundary, overlapping system status bar icons (5G, clock, battery).

### 1.2 Functional & Architectural Requirements
1. **Dynamic Floating Controls Top Inset (`REQ-UI-491.1`)**:
   - In `MapDetailLayout.kt`, when `useStatusBarsPadding == true`, status bar clearance `floatingControlsTopOffsetDp = with(density) { (statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp() }` SHALL be computed, where `statusBarHeightPx = WindowInsets.statusBars.getTop(density)`.
   - When `useStatusBarsPadding == false` (bottom sheets), `statusBarHeightPx` MUST evaluate to `0`, ensuring `floatingControlsTopOffsetDp == 0.dp`.
2. **Floating Controls Container Clearance (`REQ-UI-491.2`)**:
   - In `mapBox`, `overlay()` and the floating Share button SHALL be wrapped in a container applying `Modifier.fillMaxSize().padding(top = floatingControlsTopOffsetDp)`.
   - `ATrainingTrackerMap` SHALL remain unpadded, preserving edge-to-edge bleed behind the status bar.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Collapsed Header Clearance)**:
  * *Given* `MapDetailLayout` with `useStatusBarsPadding == true`,
  * *When* the header collapses offscreen (`connection.appBarOffset == -headerHeightPx`),
  * *Then* floating map action controls remain offset by `statusBarHeightDp`, preventing overlap with status bar icons.
* **Criterion 2 (Expanded Header Invariant)**:
  * *Given* `MapDetailLayout` with `useStatusBarsPadding == true`,
  * *When* the header is fully expanded (`connection.appBarOffset == 0`),
  * *Then* `floatingControlsTopOffsetDp` is `0.dp`, maintaining standard `16.dp` spacing below the header.
* **Criterion 3 (Bottom Sheet Invariant)**:
  * *Given* `MapDetailLayout` with `useStatusBarsPadding == false`,
  * *When* expanding, dragging, or scrolling,
  * *Then* `floatingControlsTopOffsetDp` strictly evaluates to `0.dp`.

### 1.4 System Invariants
- Edge-to-edge map bleed under status bar is preserved without clipping.
- Zero public API modifications to `MapDetailLayout` or caller screens.
- Bottom sheet mode (`useStatusBarsPadding == false`) layout math is unaffected.

---

## 2. Test Specification (TST-UI-491)

### Test Case 1: Dynamic Clearance Math & Container Structure (`TST-UI-491.1`, `TST-UI-491.2`)
* **Scope**: Contract Test (`MapDetailLayoutCollapsingHeaderContractTest.kt`)
* **Verification**: Verify `statusBarHeightPx` calculation, `floatingControlsTopOffsetDp` dynamic offset, and container padding enclosing `overlay()` and Share button.

### Test Case 2: Pure Offset Math Unit Verification (`TST-UI-491.3`)
* **Scope**: Unit Test (`MapDetailLayoutCollapsingHeaderContractTest.kt`)
* **Verification**: Verify offset calculation at expanded header (`0.dp`), fully collapsed header (`statusBarHeightDp`), and sheet mode (`0.dp`).

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-491.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate across UI map package and build architecture tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-491.1` | Contract | `MapDetailLayoutCollapsingHeaderContractTest.kt` | `REQ-UI-491.1` | Specified |
| `TST-UI-491.2` | Contract | `MapDetailLayoutCollapsingHeaderContractTest.kt` | `REQ-UI-491.2` | Specified |
| `TST-UI-491.3` | Unit | `MapDetailLayoutCollapsingHeaderContractTest.kt` | `REQ-UI-491.1`, `REQ-UI-491.2` | Specified |
| `TST-UI-491.4` | Regression | `testDebugUnitTest` (`ui.map.*`) | `REQ-PRO-001` | Specified |
