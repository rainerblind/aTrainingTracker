# Stage 2: Requirement & Test Specification - ATT-6214: MapDetailLayout Unified Top-End Actions

**Ticket**: [ATT-6214](https://rainerblind.atlassian.net/browse/ATT-6214)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-UI-500` (*MapDetailLayout Top-End Actions Stacking*), `REQ-UI-308`, `REQ-UI-491`  
**Test Spec ID**: `TST-UI-500`  
**Branch**: `feature/ATT-6214`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-500)

### 1.1 Problem Statement & Rationale
In ATT-5809, the workout share action was moved to `WorkoutHeader`, suppressing the floating Share FAB in `MapDetailLayout`. However, `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt` retained a legacy hardcoded `padding(top = 76.dp, end = 16.dp)` for the Layers button. On workouts, this leaves an awkward 60dp gap above the Layers button. Unifying top-end actions inside a single `Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))` enables automatic stacking: docking at `16.dp` when Share is hidden and `76.dp` when Share is visible, eliminating caller hardcoding.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-500.1** (*MapDetailLayout Unified Top-End Actions Slot*): `MapDetailLayout` SHALL accept `topEndActions: @Composable (ColumnScope.() -> Unit)? = null` and render the Share button and `topEndActions` inside a single `Column` aligned at `Alignment.TopEnd` with `padding(16.dp)` and `verticalArrangement = Arrangement.spacedBy(16.dp)`.
* **REQ-UI-500.2** (*WorkoutDetailTabbedContent Forwarding*): `WorkoutDetailTabbedContent` SHALL accept `mapTopEndActions: @Composable (ColumnScope.() -> Unit)? = null` and forward it to `MapDetailLayout.topEndActions`.
* **REQ-UI-500.3** (*TrackOnMapScreen Adaptive Docking*): In `TrackOnMapScreen.kt`, the technical tracks Layers button SHALL be passed via `mapTopEndActions` without hardcoded `padding(top = 76.dp)`. When Share is absent, it docks at `top = 16.dp`.
* **REQ-UI-500.4** (*RouteOnMapScreen Stacking*): In `RouteOnMapScreen.kt`, the route overlay Layers button SHALL be passed via `topEndActions` without hardcoded `padding(top = 76.dp)`. When Share is present, it stacks at `top = 76.dp`.
* **REQ-UI-500.5** (*Status Bar Clearance & Touch Target Invariant*): Both Share and `topEndActions` SHALL be bounded by `controlsTopOffsetDp` (REQ-UI-491). 44dp touch targets, `CircleShape`, and dropdown menu interactions MUST remain preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Workout Map Layers Docking)**:
  - *Given* `TrackOnMapScreen` where `showFloatingShareButton == false`,
  - *When* the map canvas renders,
  - *Then* the technical tracks Layers button docks at `top = 16.dp` without an empty gap.
* **Criterion 2 (Route Map Layers Stacking)**:
  - *Given* `RouteOnMapScreen` where `showFloatingShareButton == true`,
  - *When* the map canvas renders,
  - *Then* Share renders at `top = 16.dp` and Layers stacks below it at `top = 76.dp`.
* **Criterion 3 (Status Bar Clearance Parity)**:
  - *Given* `MapDetailLayout` in full-screen mode with system status bars,
  - *When* rendered,
  - *Then* all top-end actions are offset by `controlsTopOffsetDp` per REQ-UI-491.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-308` (Clause 2, *Route Map Layers Menu UI & Ergonomic Parity*) and complements `REQ-UI-491` (*Status Bar Clearance for Floating Map Controls*). Targets `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, and `RouteOnMapScreen.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-2763` (`REQ-UI-308`), `ATT-5809` (suppressed floating Share FAB for workouts), and `ATT-5851` (`REQ-UI-491`, `controlsTopOffsetDp`).
* **Root Reason for Existing Formulation**: `top = 76.dp` was originally hardcoded in individual caller screens as a static offset (`16.dp top + 44.dp share + 16.dp space = 76.dp`) before `showFloatingShareButton` became dynamic in ATT-5809.
* **Preservation of Core Invariants**: Status bar inset clearance (`controlsTopOffsetDp`), 44dp button sizes, layer dropdown toggle logic, permanent route polyline anchoring, and 100% test pass rate remain strictly preserved.

---

## 2. Test Specification (TST-UI-500)

### Test Case 1: MapDetailLayout Top-End Actions Stacking Contract (`TST-UI-500.1`)
* **Scope**: Contract / Unit Test (`MapDetailLayoutTest.kt`)
* **Assertion**: Verify `MapDetailLayout` accepts `topEndActions` and renders inside `Column(Alignment.TopEnd)` with `spacedBy(16.dp)`.

### Test Case 2: WorkoutDetailTabbedContent mapTopEndActions Delegation Contract (`TST-UI-500.2`)
* **Scope**: Contract / Unit Test (`WorkoutDetailTabbedContentTest.kt`)
* **Assertion**: Verify `WorkoutDetailTabbedContent` forwards `mapTopEndActions` to `MapDetailLayout`.

### Test Case 3: Zero Hardcoded 76dp Offset Audit (`TST-UI-500.3`)
* **Scope**: Source Code Audit Test
* **Assertion**: Verify `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt` contain 0 occurrences of `padding(top = 76.dp)`.

### Test Case 4: Tier 1 Targeted Unit Test Regression Suite (`TST-UI-500.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-500.1` | Contract | `MapDetailLayoutTest.kt` | `REQ-UI-500.1` | Specified |
| `TST-UI-500.2` | Contract | `WorkoutDetailTabbedContentTest.kt` | `REQ-UI-500.2` | Specified |
| `TST-UI-500.3` | Audit | `TrackOnMapScreen.kt`, `RouteOnMapScreen.kt` | `REQ-UI-500.3`, `REQ-UI-500.4` | Specified |
| `TST-UI-500.4` | Regression | `ui.map.*`, `ui.aftermath.*`, `ui.routes.*`, `build.*` | `REQ-PRO-001`, `REQ-UI-500.5` | Specified |
