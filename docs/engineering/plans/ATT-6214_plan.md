# Stage 3: Implementation Plan - ATT-6214: MapDetailLayout Unified Top-End Actions

**Ticket**: [ATT-6214](https://rainerblind.atlassian.net/browse/ATT-6214)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6214`  
**Requirement Mapping**: `REQ-UI-500` (`REQ-UI-500.1`–`REQ-UI-500.5`), `REQ-UI-308`, `REQ-UI-491`  
**Test Mapping**: `TST-UI-500` (`TST-UI-500.1`–`TST-UI-500.4`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
In ATT-5809, the workout share FAB was moved to `WorkoutHeader`, suppressing the floating Share button in `MapDetailLayout` for workouts (`showFloatingShareButton = workoutData == null`). However, callers `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt` retained a legacy hardcoded `padding(top = 76.dp, end = 16.dp)` on their Layers buttons. On workout maps, this leaves an empty 60dp gap above the Layers button. Unifying top-end actions inside `MapDetailLayout` via `topEndActions: @Composable (ColumnScope.() -> Unit)? = null` allows automatic stacking: docking at `16.dp` when Share is hidden and `76.dp` when Share is visible, eliminating caller hardcoding.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-500` (*MapDetailLayout Top-End Actions Stacking*), amending `REQ-UI-308` (Clause 2) and complementing `REQ-UI-491`
  * `REQ-UI-500.1`: `MapDetailLayout` SHALL accept `topEndActions: @Composable (ColumnScope.() -> Unit)? = null` and host Share FAB and `topEndActions` in a `Column(Alignment.TopEnd)` with `padding(16.dp)` and `spacedBy(16.dp)`.
  * `REQ-UI-500.2`: `WorkoutDetailTabbedContent` SHALL accept `mapTopEndActions` and forward it to `MapDetailLayout`.
  * `REQ-UI-500.3`: `TrackOnMapScreen` SHALL supply technical tracks Layers button via `mapTopEndActions`/`topEndActions` without hardcoded `76.dp`.
  * `REQ-UI-500.4`: `RouteOnMapScreen` SHALL supply route Layers button via `topEndActions` without hardcoded `76.dp`.
  * `REQ-UI-500.5`: Preserves `controlsTopOffsetDp` status bar clearance (REQ-UI-491), 44dp touch targets, `CircleShape`, and overlay theme styling.
* **Test Mapping**: `TST-UI-500` (`TST-UI-500.1`–`TST-UI-500.4`)
  * `TST-UI-500.1`: `MapDetailLayoutTest.kt` verifies `topEndActions` parameter and Column stacking contract.
  * `TST-UI-500.2`: `WorkoutDetailTabbedContentTest.kt` verifies `mapTopEndActions` delegation contract.
  * `TST-UI-500.3`: Contract test verifies zero occurrences of `padding(top = 76.dp)` in `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt`.
  * `TST-UI-500.4`: Tier 1 targeted unit test suite pass rate 100%.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-308` (Clause 2) and complements `REQ-UI-491`, establishing `REQ-UI-500`.
* **Historical Origin & Commit Trace**: Ticket `ATT-2763` (`REQ-UI-308`), `ATT-5809` (share move), and `ATT-5851` (`REQ-UI-491`, `controlsTopOffsetDp`).
* **Root Reason for Existing Formulation**: `top = 76.dp` was hardcoded in caller screens as manual spacing (`16.dp top + 44.dp share + 16.dp space`) before `showFloatingShareButton` became dynamic.
* **Preservation of Core Invariants**: REQ-UI-491 status bar inset clearance (`controlsTopOffsetDp`), 44dp touch targets, `CircleShape`, `TTAlpha.Overlay` styling, layer menus, and 100% test pass rate remain preserved.

---

## 3. Proposed Architectural Changes & UI Consistency
* **`MapDetailLayout.kt`**: Expose `topEndActions: @Composable (ColumnScope.() -> Unit)? = null`. Replace standalone Share `Surface` with `Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) { if (showFloatingShareButton) ShareSurface(); topEndActions?.invoke(this) }`.
* **`WorkoutDetailTabbedContent.kt`**: Expose `mapTopEndActions` and delegate to `MapDetailLayout.topEndActions` across both tabbed modes.
* **`TrackOnMapScreen.kt` & `RouteOnMapScreen.kt`**: Pass Layers button via `topEndActions`/`mapTopEndActions` without hardcoded `76.dp` top padding.
* **UI Consistency**: Reuses `44.dp` circular overlay buttons, `TTAlpha.Overlay`, `16.dp` design grid spacing, and existing `Icons.Default.Layers`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Verify Gate 3 Pass & Code Lock Release**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.
2. **Step 2: Update MapDetailLayout.kt**: Add `topEndActions` parameter and unified TopEnd `Column` with `spacedBy(16.dp)`.
3. **Step 3: Update WorkoutDetailTabbedContent.kt**: Add `mapTopEndActions` and forward to `MapDetailLayout`.
4. **Step 4: Update TrackOnMapScreen.kt & RouteOnMapScreen.kt**: Move Layers button to `topEndActions`, remove `padding(top = 76.dp)`.
5. **Step 5: Add Contract Tests**: Add `TST-UI-500.1`–`TST-UI-500.3` contract assertions in `MapDetailLayoutTest.kt` and `WorkoutDetailTabbedContentTest.kt`.
6. **Step 6: Execute Tier 1 Targeted Unit Tests**: Run targeted test suite (`ui.map.*`, `ui.aftermath.*`, `ui.routes.*`, `ThemeColorCentralizationAuditTest`, `build.*`).
7. **Step 7: Verify Requirement Governance**: Run `python3 tools/verify_requirement_governance.py`.

---

## 5. System Invariants & Rollback Plan
* **Core Invariants**: REQ-UI-491 status bar clearance, 44dp circular touch target invariant, dropdown menu toggle behavior, 100% test pass rate.
* **Rollback Plan**: Working tree clean except for docs; revert cleanly via `git checkout .` if any step fails.
