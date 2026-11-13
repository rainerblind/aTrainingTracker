# Stage 1 Analysis: ATT-7518 - Fix Snowman Dot and Pinched Crescent Arcs in bt_bike_radar.xml

**Ticket**: [ATT-7518](https://rainerblind.atlassian.net/browse/ATT-7518)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7518` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
On the *Alle Sensoren* screen (`ATT-Rdr` BLE sensor card using `res/drawable/bt_bike_radar.xml`), the top-left radar mini-icon (`mini_icon_radar`) displays a distorted double-circle ("snowman" / figure-8) center dot and pinched, razor-thin crescent arcs instead of uniform wave bands. Furthermore, the symbol points straight upward instead of rearward (left-facing), conflicting with the rearview traffic radar detection domain.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Broken Circle Chord Math)**: In `bt_bike_radar.xml:152`, `M 480,720 A 80,80 0 1,1 560,720 A 80,80 0 1,1 480,720 Z` spans chord length $80 = r$ instead of diameter $160 = 2r$. With `largeArcFlag = 1`, SVG draws two separate $300^\circ$ major arcs centered at $y = 650.7$ and $y = 789.3$, creating two vertically stacked circles ("snowman").
* **RCA 2 (Inverted Inner Arc Offsets)**: Inner arc endpoints subtracted $56$ from $y$ (moving away from center) instead of adding $+56$ toward center, pinching filled wave bands into thin crescents.
* **RCA 3 (Upward vs. Rearward Orientation)**: Mini-icon emitted upward ($0^\circ$), whereas rearview radar detects rearward traffic (left-facing / $-90^\circ$ relative to right-facing cyclist).
* **Independent Peer Review & Verification**:
  * Verified `bt_bike_radar.xml#L150-153`: Confirmed chord length 80 and inverted inner Y offsets.
  * Verified `bike_radar.xml#L31-35`: ANT+ variant uses stroked arcs at $(36.0, 26.0)$ and is free from the snowman defect.
  * Verified `VectorAssetResolutionContractTest.kt#L425-443`: Lacks analytical geometry verification for circular dot and arc concentricity.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Author clean single-dot and concentric radar wave geometry in `bt_bike_radar.xml` `mini_icon_radar`.
  2. Reorient radar wave emission rearward (left-facing, pointing backward behind cyclist).
  3. Ensure visual weight matches sibling mini-icons (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`).
  4. Update `VectorAssetResolutionContractTest.kt` with geometric circularity and concentricity checks.
* **Out-of-Scope Non-Goals**:
  * Modifying the cyclist silhouette or `RDR` typography in `bt_bike_radar.xml`.
  * Modifying `bike_radar.xml` (already verified clean).
  * Modifying `ic_radar.xml` (governed by ATT-7544).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-514.2` in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: Commit `45693026` (`ATT-6699`, Sprint 2026-46.3).
* **Root Reason for Existing Formulation**: Established 144x144 full-bleed BLE badge layout with top-left `mini_icon_radar`, but path math contained chord length and offset bugs.
* **Preservation of Core Invariants**: Full-bleed `#0A3D91` badge ($r=22.0$), centered cyclist (`translateX=18.18`), top-left quadrant clearance ($maxX \le 45.0$, $\ge 7.0$ units from cyclist), and Rule 37 constraints are strictly preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-514.2`, `REQ-UI-514.4`.
* **Affected Architecture Elements**: `res/drawable/bt_bike_radar.xml`, `VectorAssetResolutionContractTest.kt`.
* **Verification Impact & Test Surface**: `VectorAssetResolutionContractTest.kt`, `ThemeColorCentralizationAuditTest.kt`, `processDebugResources`.
* **Risk Assessment & Invariants**: LOW risk. Asset path correction and test assertion tightening; zero API, database, or runtime logic impact.

---

## 6. Architectural Strategy & High-Level Solution
1. **Asset Geometry**: Replace `mini_icon_radar` path in `bt_bike_radar.xml` with mathematically exact circle dot ($2r = d$) and rearward-oriented concentric wave bands.
2. **Contract Testing**: Update `VectorAssetResolutionContractTest.kt` to inspect dot diameter/chord ratio and arc concentricity.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero silhouette collision; top-left quadrant bounding box compliance; Rule 37 vector limits.
* **Risk Rating**: **LOW** (Targeted VectorDrawable XML geometry correction).
