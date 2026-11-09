# Stage 2: Requirement & Test Specification - ATT-6210: Sport Type Breakdown on KnownLocationCard

**Ticket**: [ATT-6210](https://atrainingtracker.atlassian.net/browse/ATT-6210)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6210`  
**Requirement Mapping**: `REQ-UI-470` (`REQ-UI-470.5` / `REQ-UI-470.6`, refining `REQ-UI-469.2`)  
**Test Spec ID**: `TST-UI-470` (`TST-UI-470.7`) | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-470 Sport Breakdown Refinement)

### 1.1 Problem Statement & Rationale
`KnownLocationCard` currently renders a static runner icon (`Icons.AutoMirrored.Filled.DirectionsRun`) before the starts count, regardless of which sport was actually performed. For locations predominantly used for cycling (e.g. *IAV*, *Zu Hause*) or mixed sports, this is misleading and conceals the athletic profile of the location.

### 1.2 Functional & Architectural Requirements
1. **Sport Starts Aggregation (`REQ-UI-470.6`)**:
   - In `KnownLocationsViewModel`, workouts matching each location SHALL be partitioned by `BSportType` into `SportStartsCount(bike: Int, run: Int, other: Int)`.
   - `KnownLocationsUiState` SHALL expose `sportStartsByLocationId: Map<Long, SportStartsCount>`.
2. **Canonical Sport Breakdown Layout (`REQ-UI-470.6`)**:
   - In `KnownLocationCard`, if `sportStarts.total > 0`, each sport with `count > 0` SHALL be rendered in canonical order: Bike (`R.drawable.bsport_bike`), Run (`R.drawable.bsport_run`), Other (`R.drawable.bsport_other` on far right).
   - Each sport item SHALL display its authentic multi-color vector drawable (`tint = Color.Unspecified`, 16.dp) with bold primary text (`MaterialTheme.typography.bodyMedium`, `FontWeight.Bold`, `MaterialTheme.colorScheme.primary`).
3. **Fallback & Invariants**:
   - If `sportStarts.total == 0`, render `0 Starts` with default styling.
   - Maintain `onShowWorkouts` click action and test tag `location_starts_badge_${item.id}`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Multi-Sport Breakdown)**:
  * *Given* a location with 5 Bike starts and 3 Run starts,
  * *When* `KnownLocationCard` is rendered,
  * *Then* it displays `[bsport_bike] 5` followed by `[bsport_run] 3` in canonical order `[Bike, Run, Other]`.
* **Criterion 2 (Un-tinted Vector Graphics)**:
  * *Given* sport logo drawables in the starts badge,
  * *When* rendered in Compose,
  * *Then* `tint = Color.Unspecified` is used to preserve multi-color vector master geometry.
* **Criterion 3 (Zero Starts Fallback)**:
  * *Given* a location with 0 starts,
  * *When* `KnownLocationCard` is rendered,
  * *Then* it displays `0 Starts` without crashing or rendering empty rows.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-469.2` (*Unboxed Starts Metric*) and `REQ-UI-470.5` / `REQ-UI-470.6`.
2. *Historical Origin & Commit Trace*: `e14d805f` (`ATT-5887`), `ce73d561` (`ATT-5884`).
3. *Root Reason for Existing Formulation*: Static runner icon was an interim placeholder when unboxing cards before sport aggregation was introduced.
4. *Preservation of Core Invariants*: Bottom-anchored baseline alignment (`IntrinsicSize.Min`), test tag `location_starts_badge_${item.id}`, `onShowWorkouts` drill-down action, and canonical order (`design_guidelines.md` §1.2: `[Bike, Run, Other]`) are strictly preserved.

---

## 2. Test Specification (TST-UI-470.7)

### Test Case 1: Sport Starts Aggregation Unit Tests (`TST-UI-470.7a`)
* **Scope**: `KnownLocationsViewModelTest.kt`.
* **Assertions**: `groupStartsByLocation` correctly partitions Bike, Run, and Other counts per location ID.

### Test Case 2: Card Layout & Canonical Sport Order Contract Tests (`TST-UI-470.7b`)
* **Scope**: `KnownLocationCardLayoutTest.kt`, `KnownLocationsScreenTest.kt`.
* **Assertions**: `KnownLocationCard` renders sport drawables in order `[Bike, Run, Other]` with `Color.Unspecified`, and test tag `location_starts_badge_${item.id}` remains present.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-470.7c`)
* **Scope**: Targeted unit tests (`ui.knownlocations.*`, `ui.theme.*`, `build.*`).
* **Assertions**: 100% pass across touched packages and architectural rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-470.7a]` | Unit | `KnownLocationsViewModelTest.kt`: Sport breakdown aggregation | `REQ-UI-470.6` | Specified |
| `[TST-UI-470.7b]` | Contract | `KnownLocationCardLayoutTest.kt`: Canonical sport ordering `[Bike, Run, Other]` | `REQ-UI-470.6` | Specified |
| `[TST-UI-470.7c]` | Regression | Tier 1 targeted suite (`ui.knownlocations.*`, `build.*`) | `REQ-PRO-001` | Specified |
