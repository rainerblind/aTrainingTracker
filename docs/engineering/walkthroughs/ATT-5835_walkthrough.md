# Stage 5: Walkthrough & Verification - ATT-5835: WorkoutDetailTabbedContent & MapDetailLayout: Remove full-header edit trigger in details view and hide bottom-sheet drag handle in full-screen tabs

**Ticket**: [ATT-5835](https://rainerblind.atlassian.net/browse/ATT-5835)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-498` (`REQ-UI-498.1`–`REQ-UI-498.4`)  
**Test Mapping**: `TST-UI-498` (`TST-UI-498.1`–`TST-UI-498.4`)  
**Branch**: `feature/ATT-5835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
Resolved header click trigger and full-screen map bottom sheet chrome defects in workout detail view (`audit_05_under_edit.png`):
1. **Header Click Decoupling**: In `WorkoutDetailTabbedContent.kt`, set `onClicked = null` when instantiating `WorkoutHeader`. The full header card no longer responds to clicks, eliminating accidental triggers of the Edit Workout dialog when inspecting details or switching tabs. Intentional editing remains accessible via `WorkoutOverflowMenu` (`"Bearbeiten"`).
2. **Map Chrome Decoupling**: In `MapDetailLayout.kt`, decoupled bottom-sheet styling from `useStatusBarsPadding` by introducing explicit parameter `isBottomSheet: Boolean = !useStatusBarsPadding`. In full-screen tabs (`WorkoutDetailTabbedContent.kt` and `TrackOnMapScreen.kt`), `isBottomSheet = false` suppresses `MinimumDragHandle()` and sets `shape = RectangleShape`.
3. **Modal Sheet Chrome Preservation**: Modal bottom sheet callers (`MapScreenWithTrack.kt`, `LiveSegmentSheet.kt`) retain the bottom sheet drag handle and rounded top corners (`isBottomSheet` defaults to `!useStatusBarsPadding = true`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-498.1` | `TST-UI-498.1` | Contract Test (`WorkoutDetailHeaderClickContractTest.testWorkoutDetailHeader_hasNoClickModifier`) | **PASSED** | `Verified` |
| `REQ-UI-498.2` | `TST-UI-498.2` | Contract Test (`MapDetailLayoutSheetChromeContractTest.testMapDetailLayout_fullScreenTabSuppressesDragHandle`) | **PASSED** | `Verified` |
| `REQ-UI-498.3` | `TST-UI-498.3` | Contract Test (`MapDetailLayoutSheetChromeContractTest.testMapDetailLayout_fullScreenTabUsesRectangleShape`) | **PASSED** | `Verified` |
| `REQ-UI-498.4` | `TST-UI-498.4` | Contract Test (`MapDetailLayoutSheetChromeContractTest.testMapDetailLayout_modalSheetPreservesDragHandleAndRoundedShape`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-498.5` | Tier 1 Targeted Regression (`ui.aftermath.*`, `ui.map.*`, `ui.theme.*`, `build.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2 && python3 tools/verify_requirement_governance.py`
* **Output**:
  ```text
  BUILD SUCCESSFUL in 1m 19s
  32 actionable tasks: 1 executed, 31 up-to-date
  PASS: Living documentation master tables match all 9 domain shards with 100% parity.
  Net-new requirement(s) detected: REQ-UI-498. Bypassing archaeology check cleanly.
  ```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **On-Device Status**: No physical USB test device attached (`adb devices` reported no active targets). Live on-device inspection across `WorkoutDetailTabbedContent` is deferred to Ceremony 2 joint review.
* **Layout Invariant Audit**: Verified header card click detachment and sheet chrome suppression via automated unit and contract tests.

---

## 5. Invariant & Governance Verification
1. **Floating Controls Layout Invariant**: `floatingControlsTopOffsetPx` strictly retains `useStatusBarsPadding` calculation, unaffected by `isBottomSheet`.
2. **Three-Dot Overflow Action Invariant**: Intentional editing remains accessible via overflow menu (`"Bearbeiten"`, `"GPX exportieren"`, `"Löschen"`).
3. **Living Documentation Parity**: `REQ-UI-498` and `TST-UI-498` marked `[Verified]` in `req_ui_design_system.md` and `tst_ui_design_system.md`; 100% parity verified in master indices (`docs/requirements.md`, `docs/tests.md`).
4. **Governance Verification**: `tools/verify_requirement_governance.py` confirmed 0 errors.
