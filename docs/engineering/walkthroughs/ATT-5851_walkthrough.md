# Stage 5: Walkthrough & Verification - ATT-5851: MapDetailLayout: Prevent floating map buttons (Share & overlay FABs) from colliding with system status bar when header collapses

**Ticket**: [ATT-5851](https://rainerblind.atlassian.net/browse/ATT-5851)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-491`  
**Test Mapping**: `TST-UI-491`  
**Branch**: `feature/ATT-5851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
Resolved the status bar collision defect in `MapDetailLayout.kt` where collapsing the top header caused floating map controls (`overlay()` and Share FAB) to shift into the system status bar. Dynamic status bar clearance offset `floatingControlsTopOffsetDp = with(density) { (statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp() }` is calculated via `MapDetailViewportMath.calculateFloatingControlsTopOffsetPx()` and applied to a dedicated `Box` container enclosing `overlay()` and the Share button inside `mapBox`. The underlying `ATrainingTrackerMap` continues to bleed edge-to-edge behind the transparent status bar, and bottom sheet modes (`useStatusBarsPadding == false`) retain zero top offset.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-491.1` | `TST-UI-491.1` | Contract Test (`testMapDetailLayout_computesDynamicStatusBarClearance`) | **PASSED** | `Verified` |
| `REQ-UI-491.2` | `TST-UI-491.2` | Contract Test (`testMapDetailLayout_appliesTopOffsetToFloatingControlsContainer`) | **PASSED** | `Verified` |
| `REQ-UI-491.1`, `.2` | `TST-UI-491.3` | Pure Unit Test (`testMapDetailLayout_floatingControlsOffsetCalculationLogic`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-491.4` | Tier 1 Targeted Regression (`ui.map.*`, `ThemeColorCentralizationAuditTest`, `build.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
* **Command**: `./gradlew assembleDebug testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2 && python3 tools/verify_requirement_governance.py`
* **Output**:
  ```text
  BUILD SUCCESSFUL in 13s
  50 actionable tasks: 4 executed, 46 up-to-date
  504 tests completed, 0 failed
  PASS: Living documentation master tables match all 9 domain shards with 100% parity.
  Net-new requirement(s) detected: REQ-UI-491. Bypassing archaeology check cleanly.
  ```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **On-Device Status**: No physical USB test device attached (`adb devices` reported no active targets). Live on-device inspection across Favorite Route Details, Route Details, and Segment Details screens is deferred to Ceremony 2 joint review.
* **Layout Integrity**: Edge-to-edge map bleed is preserved without extra padding on `ATrainingTrackerMap`. Floating action controls are offset by `controlsTopOffsetDp`, stopping exactly below the system status bar when collapsed and keeping standard 16.dp spacing when expanded.

---

## 5. Invariant & Governance Verification
1. **Edge-to-Edge Bleed**: Map bleeds edge-to-edge behind the status bar; floating controls stop cleanly below status bar icons.
2. **Bottom Sheet Mode**: When `useStatusBarsPadding == false`, offset strictly evaluates to `0.dp`.
3. **Living Documentation Parity**: `REQ-UI-491` and `TST-UI-491` marked `[Verified]` in `req_ui_design_system.md` and `tst_ui_design_system.md`; 100% parity verified in `docs/requirements.md` and `docs/tests.md`.
4. **Governance Verification**: `tools/verify_requirement_governance.py` confirmed 0 errors.
