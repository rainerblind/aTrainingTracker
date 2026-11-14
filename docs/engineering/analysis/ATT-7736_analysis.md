# Stage 1 Analysis: ATT-7736 - [Verbesserung] Rotate ic_radar.xml icon 90 degrees counter-clockwise (left)

**Ticket**: [ATT-7736](https://rainerblind.atlassian.net/browse/ATT-7736)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.5 Review of ATT-7544, human review noted that while the new Bike Radar status icon (`res/drawable/ic_radar.xml`) functions properly across the Cockpit header and `SensorSourceDialog`, its radar waves radiate upwards. For cycling radar (rearview vehicle detection), the canonical visual perspective is horizontal waves radiating to the left (counter-clockwise 90° rotation from upward). ATT-7736 requests rotating `ic_radar.xml` 90 degrees counter-clockwise (to the left).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Inspected `app/src/main/res/drawable/ic_radar.xml`: Currently defines a 24dp vector with viewport 960x960. The origin circle is at `(480, 720)` (bottom center) and concentric arcs radiate upward toward `y = 333`.
  * Rotating 90° counter-clockwise around the viewport center `(480, 480)` maps the origin dot to `(720, 480)` and wave arcs radiating horizontally to the left (`x = 333`), matching radar horizon expectations.
  * In Android `VectorDrawable`, a `<group>` wrapper with `android:pivotX="480"`, `android:pivotY="480"`, and `android:rotation="270"` (or direct coordinate rotation) cleanly and losslessly achieves this reorientation across all API 21+ targets without path distortion.
  * Callers audited: `SensorType.java` (`getIconResId()` returning `R.drawable.ic_radar`), `SensorStatus.kt` (Cockpit top bar), `SensorSourceDialog.kt` (dialog title), and `SensorStatusTopBarSpacingTest.kt`. All callers consume `R.drawable.ic_radar` directly without hardcoded rotation assumptions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Rotate `res/drawable/ic_radar.xml` 90 degrees counter-clockwise (left) so waves radiate horizontally to the left.
  * Maintain exact 24dp x 24dp size, 960x960 viewport, and tinting compatibility.
  * Update `SensorStatusTopBarSpacingTest.kt` to verify vector structure and rotation configuration.
  * Update living requirements (`REQ-UI-451.4`) and test specifications (`TST-UI-451.4`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No layout or padding adjustments to `SensorStatus.kt` or `SensorSourceDialog.kt`.
  * No changes to `bt_bike_radar.xml` (dedicated composite BLE card icon).
  * No new strings or localization modifications.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-451.4` (Nine-Icon Header Budget & Radar Vector Icon)
* **Historical Origin & Commit Trace**: Commit `a5a9a228d450` (ATT-7544, Sprint 2026-46.5)
* **Root Reason for Existing Formulation**: Introduced `ic_radar.xml` as a clean 24dp vector asset replacing legacy pinched crescents, but defaulted to upward-radiating waves.
* **Preservation of Core Invariants**: Reorienting waves to the left preserves the 24dp asset footprint, vector scalability, and `SensorType` icon resolution while honoring user feedback from Sprint Review.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-451.4` (updated for leftward wave orientation).
* **Affected Architecture Elements**: `res/drawable/ic_radar.xml`, `SensorStatusTopBarSpacingTest.kt`.
* **Verification Impact & Test Surface**: Unit tests (`SensorStatusTopBarSpacingTest`), asset XML lint, visual inspection via `read_file`.
* **Risk Assessment & Invariants**: Low risk. Asset reorientation does not affect layout dimensions or runtime logic.

---

## 6. Architectural Strategy & High-Level Solution
* Wrap the `<path>` inside `<group android:pivotX="480" android:pivotY="480" android:rotation="270">` in `app/src/main/res/drawable/ic_radar.xml`.
* Validate that `SensorStatusTopBarSpacingTest` asserts existence, 24dp bounds, and rotation attribute.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 24dp x 24dp size and 960x960 viewport contract preserved.
  2. Zero breakage across `SensorStatus.kt` top bar and `SensorSourceDialog.kt`.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (isolated vector asset update, 1 SP).
