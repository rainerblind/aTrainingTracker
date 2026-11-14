# Stage 3: Implementation Plan - ATT-7736: Rotate ic_radar.xml icon 90 degrees counter-clockwise (left)

**Ticket**: [ATT-7736](https://rainerblind.atlassian.net/browse/ATT-7736)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-UI-451` / `REQ-UI-451.4`  
**Test Mapping**: `TST-UI-451` / `TST-UI-451.4`  
**Branch**: `feat/ATT-7736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
Sprint 2026-46.5 human review of ATT-7544 confirmed that while `res/drawable/ic_radar.xml` integrates cleanly into the Cockpit header and `SensorSourceDialog`, its wave arcs radiate upwards. For cycling rearview radar, the canonical icon orientation requires waves radiating horizontally to the left (90° counter-clockwise rotation from upward). This plan details the construction steps to reorient `ic_radar.xml` and update its contract tests in Stage 4.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-451` / `REQ-UI-451.4` (Nine-Icon Header Budget & Radar Vector Icon)
* **Test Mapping**: `TST-UI-451` / `TST-UI-451.4` (`SensorStatusTopBarSpacingTest.kt`)

---

## 3. System Invariants & Preserved Behavior
1. **Dimension & Viewport Invariance**: 24dp x 24dp bounds and 960x960 viewport contract preserved.
2. **SensorType Resolution**: `SensorType.getIconResId()` continues mapping all 6 radar types to `R.drawable.ic_radar`.
3. **Cockpit Layout Invariance**: Zero modifications to `SensorStatus.kt` header row, padding, or touch targets; zero changes to `SensorSourceDialog.kt`.
4. **Code Lock**: Strict enforcement of `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>` prior to production code edits.

---

## 4. Proposed Architectural Changes

### Component 1: `app/src/main/res/drawable/ic_radar.xml`
* Wrap the `<path>` inside `<group android:pivotX="480" android:pivotY="480" android:rotation="270">`.
* Rotates the origin circle from `(480, 720)` to `(720, 480)` (center-right) and wave arcs from upward `y = 333` to leftward `x = 333`.

### Component 2: `SensorStatusTopBarSpacingTest.kt`
* Extend `testIcRadarDrawableFile_existsAndIsValidVector` to assert `android:rotation="270"` (or `-90`) within `<group>`.

### UI Consistency (Rule 23)
* **Reference component**: `SensorStatus.kt` 24dp sensor icons.
* **Reused components**: Standard VectorDrawable `<group>` rotation attribute.
* **Theme tokens**: Standard tinting via `android:tint="#000000"` and Compose `LocalContentColor`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `app/src/main/res/drawable/ic_radar.xml`
* Wrap existing `<path>` with `<group android:pivotX="480" android:pivotY="480" android:rotation="270">`.

### Step 2: Update `SensorStatusTopBarSpacingTest.kt`
* Add assertion: `assertTrue("ic_radar.xml must define 90 degree counter-clockwise rotation", content.contains("android:rotation=\"270\"") || content.contains("android:rotation=\"-90\""))`.

### Step 3: Targeted Unit Tests
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatusTopBarSpacingTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test execution, visual inspection via `read_file`, and living doc governance check.
* **Rollback**: Branch isolation on `feat/ATT-7736` allows clean reset without affecting `sprint/2026-46.6`.
