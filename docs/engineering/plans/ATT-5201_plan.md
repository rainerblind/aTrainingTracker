# Stage 3: Implementation Plan - ATT-5201: BTLE sensor icons (bt_*.xml): restore official Bluetooth color (#0A3D91), center cyclist, and move canonical mini-icons to top-left

**Ticket**: [ATT-5201](https://rainerblind.atlassian.net/browse/ATT-5201)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-442` (`REQ-UI-442.1`–`REQ-UI-442.4`)  
**Test Mapping**: `TST-UI-442` (`TST-UI-442.1`–`TST-UI-442.4`)  
**Branch**: `feature/ATT-5201`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Description & Background

Refine the 6 Bluetooth LE sensor vector drawables (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, `bt_run.xml`):
1. Restore official Bluetooth brand color `#0A3D91` across all 6 drawables (replacing ICC-distorted `#07377F`).
2. Center the cyclist motif horizontally ($\Delta x \approx +18.18$ shift to $x \in [28.5, 115.5]$) on the 4 bike drawables.
3. Replace hand-drawn mini-icons with scaled canonical domain paths from `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml` in the top-left quadrant ($x \approx 12..52, y \approx 10..50$).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-442` (*Full-Bleed Badge Geometry, Large Uppercase Labels, Centered Cyclist & Top-Left Canonical Mini-Icons for Bluetooth LE Sensor Drawables*)
  * `REQ-UI-442.1`: Official `#0A3D91` badge background with full-bleed `0..144` ($r=22.0$) geometry.
  * `REQ-UI-442.2`: Bold uppercase bottom typography (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) with `evenOdd` at $y \ge 113.25$.
  * `REQ-UI-442.3`: Centered cyclist motif ($x \in [28.5, 115.5]$) and top-left canonical mini-icons ($x \approx 12..52, y \approx 10..50$).
  * `REQ-UI-442.4`: Rule 37 mathematical vector fidelity ($\le 45$ `L` cmds/path, analytical curves, $0$ breaking changes to `R.drawable.bt_*`).
* **Test Mapping**: `TST-UI-442` (`TST-UI-442.1`..`TST-UI-442.4`) in `VectorAssetResolutionContractTest.kt`.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: `UIHelper.getIconId()` and Compose screen bindings continue resolving non-zero IDs.
2. **Rule 4 Code Lock**: Zero production edits before Gate 3 passes `python3 tools/jira_util.py check-gate`.
3. **Typography Baseline Invariance**: Uppercase bottom labels at $y \ge 113.25$ and `fillType="evenOdd"` remain strictly unchanged.
4. **Subtask & Human Gate Invariants**: Subtasks close via `freigabe`; parent ticket completes at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Vector Drawables (`app/src/main/res/drawable/bt_*.xml`)
* Replace `#07377F` with `#0A3D91` in all 6 `bt_*.xml` files.
* Center cyclist motif on 4 bike drawables: wrap cyclist paths in `<group android:translateX="18.18">` (or bake coordinates $+18.18$) so bounds center at $x = 72.0$.
* Embed canonical mini-icons in top-left:
  * `bt_bike_pwr.xml`: Embed `ic_power.xml` path scaled into $x \approx 14..42, y \approx 12..44$.
  * `bt_bike_spd.xml`: Embed `ic_speed.xml` path scaled into $x \approx 14..44, y \approx 12..42$.
  * `bt_bike_cad.xml`: Embed `ic_cadence.xml` path scaled into $x \approx 14..44, y \approx 12..42$.
  * `bt_bike_speed_and_cadence.xml`: Embed paired canonical paths (`ic_speed` + `ic_cadence`) in top-left quadrant ($x \approx 12..50, y \approx 10..46$).

### Component 2: Contract Tests (`VectorAssetResolutionContractTest.kt`)
* Update background color assertion from `#07377F` to `#0A3D91` across all 6 BLE badges.
* Update mini-icon and centering contract tests to verify top-left placement and horizontal centering.

### UI Consistency (Rule 23)
* **Reference screen**: `ControlTrackingScreen.kt` (paired BTLE device chips).
* **Reused components**: Canonical vector paths from `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml` (Rule 36).
* **Theme tokens**: Official Bluetooth protocol badge color `#0A3D91`, glyph color `#FFFFFF`.
* **New one-off styles & justification**: None (strictly restoring official brand color and standardizing icon reuse).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Bluetooth LE Vector Drawables
* Files: `app/src/main/res/drawable/bt_*.xml` (all 6 files)
* Changes: Replace `#07377F` with `#0A3D91`. On the 4 bike icons, center the cyclist motif ($\Delta x = +18.18$) and embed canonical mini-icons in top-left.

### Step 2: Update Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* Changes: Assert `#0A3D91` badge color, top-left mini-icon bounds, centered cyclist bounds, and Rule 37 compliance.

### Step 3: Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest" --max-workers=2`

---

## 6. Verification & Rollback Plan

* **Verification**: Run `VectorAssetResolutionContractTest`, verify requirement governance (`tools/verify_requirement_governance.py`), and inspect Compose preview gallery in Stage 5.
* **Rollback**: Atomic git merge allows clean branch revert if needed.
