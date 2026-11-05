# Stage 3: Implementation Plan - ATT-5422: SensorStatus Top-Bar 16 dp Spacing

**Ticket**: [ATT-5422](https://rainerblind.atlassian.net/browse/ATT-5422)  
**Sub-task**: [ATT-5459](https://rainerblind.atlassian.net/browse/ATT-5459) (`[Impl-Plan]`)  
**Parent Epic**: Standalone Ticket (None)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5422`  
**Requirement Mapping**: `REQ-UI-451` (*SensorStatus Top Bar 16 dp Spacing and Centering*)  
**Test Mapping**: `TST-UI-451` (*SensorStatus Top Bar Layout and Centering Tests*)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Description & Background
In `SensorStatus.kt` (embedded in `TrackingTabsScreen.kt`), drawer hamburger icon (`☰`) and supporter emblem (`💎`) sit ~4 dp from the display boundary. This plan implements Option A: establishing a 16 dp screen edge margin via `Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))` inside `Surface(Modifier.padding(4.dp))` and enlarging `IconButton` and fallback `Spacer` sizes to `32.dp` (housing 24 dp icons) so the central `Box(Modifier.weight(1f))` holding the 8 sensor icons remains mathematically centered.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-451` (`REQ-UI-451.1` edge margin, `REQ-UI-451.2` 32 dp touch targets & centering, `REQ-UI-451.3` landscape invariant).
* **Test Mapping**: `TST-UI-451` (`TST-UI-451.LAYOUT`, `TST-UI-451.NONREG`, `TST-UI-451.SUITE`).

---

## 3. System Invariants & Preserved Behavior
1. **Mathematical Sensor Centering**: Both outer slots (button/spacer) occupy identical 32 dp widths, centering the 8 sensor icons.
2. **Landscape & Dialog Invariance**: Compact landscape (`REQ-UI-434.1`, max 28 dp) and sensor source dialog logic are unchanged.
3. **Code Lock (Rule 4)**: Production code in `app/src/main/` locked until Gate 3 approval.

---

## 4. Proposed Architectural Changes
* **`SensorStatus.kt` (Portrait Layout)**:
  * Apply `Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)` to outer portrait `Row`.
  * Enlarge hamburger `IconButton` and fallback `Spacer` to `Modifier.size(32.dp)` (`Icon` remains `24.dp`).
  * Enlarge supporter `IconButton` and fallback `Spacer` to `Modifier.size(32.dp)` (`Icon` remains `24.dp`).
* **Contract Tests**: Author `SensorStatusTopBarSpacingTest.kt` validating outer row padding, 32 dp touch targets, and spacers.
* **UI Consistency (Rule 23)**: Aligns with Material 3 `TopAppBar` 16 dp standard; reuses `MaterialTheme.colorScheme.onSurface`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Code Lock Verification**: Confirm `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns exit code 0.
2. **Refactor Portrait Layout**: In `SensorStatus.kt`, update `Row` padding to `(horizontal = 8.dp, vertical = 4.dp)` and `IconButton` / `Spacer` sizes to `32.dp`.
3. **Author Layout Contract Test**: Author `SensorStatusTopBarSpacingTest.kt` in `ui.tracking.controltracking`.
4. **Targeted Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Layout contract tests confirm measurements; on-device screenshot verified via `read_file` (Rule 71).
* **Rollback**: Atomic git commit revert on `feature/ATT-5422`.
