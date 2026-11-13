# Stage 2: Requirement & Test Specification - ATT-7518: Fix bt_bike_radar mini_icon_radar Geometry

**Ticket**: [ATT-7518](https://rainerblind.atlassian.net/browse/ATT-7518) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7518`  
**Requirement Mapping**: `REQ-UI-514` (`REQ-UI-514.2`, `REQ-UI-514.4`) | **Test Spec ID**: `TST-UI-514` (`TST-UI-514.5`)  
**Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (`REQ-UI-514`)

### 1.1 Attributes & Problem Statement
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- In `app/src/main/res/drawable/bt_bike_radar.xml`, the top-left radar mini-icon (`mini_icon_radar`) exhibited a double-circle ("snowman") dot artifact due to a malformed SVG arc command with chord length equal to radius ($80 = r$ instead of $160 = 2r$), generating two vertically stacked major arcs. Additionally, inner arc offsets pinched wave bands into thin crescents, and orientation faced upward instead of rearward (left-facing) toward oncoming traffic.

### 1.2 Functional & Architectural Requirements
1. `app/src/main/res/drawable/bt_bike_radar.xml` SHALL define `mini_icon_radar` with a single circular source dot ($\Delta x = 2r$ diameter chord or analytical circle geometry, precluding double-circle / snowman rendering).
2. The radar wave bands in `mini_icon_radar` SHALL radiate rearward (left-facing toward oncoming traffic), maintaining uniform radial band thickness and zero pinched crescents.
3. `mini_icon_radar` SHALL maintain clean separation ($\ge 7.0$ px) from the cyclist silhouette and stay strictly within top-left quadrant bounds ($x \approx 12..44, y \approx 17..36$).
4. All path elements in `bt_bike_radar.xml` SHALL conform to Rule 37 ($\le 45$ `L` commands, analytical curves `A`, `C`, or `Q`).

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* `bt_bike_radar.xml`, *When* `mini_icon_radar` path data is parsed, *Then* the source dot has a single geometric center ($2r$ chord) without double-circle snowman artifacts.
- *Given* `bt_bike_radar.xml`, *When* radar wave arcs are evaluated, *Then* waves radiate rearward (left-facing) with uniform concentric thickness and zero pinched crescents.
- *Given* `bt_bike_radar.xml`, *When* evaluated by `VectorAssetResolutionContractTest`, *Then* cyclist group contains 18 standard paths, `mini_icon_radar` maintains $\ge 7.0$ px separation from cyclist silhouette, and Rule 37 constraints pass 100%.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Amending `REQ-UI-514.2` and `REQ-UI-514.4` (*Bluetooth LE Radar Badge Geometry & Typography*), targeting `app/src/main/res/drawable/bt_bike_radar.xml` and `VectorAssetResolutionContractTest.kt`.
- *Historical Origin & Commit Trace*: Sprint 2026-45.1 (`ATT-4956`, commit `be03a5bc14`), Sprint 2026-45.5 (`ATT-5201`, commit `43de6fd0`), Sprint 2026-46.5 (`ATT-6699`, `ATT-7318`), and Sprint 2026-46.5 (`ATT-7518`).
- *Root Reason for Existing Formulation*: When `mini_icon_radar` was added to `bt_bike_radar.xml`, manual SVG arc coordinate transposition introduced a chord length of 80 with radius 80 and inverted inner Y offsets, creating the double-circle dot artifact and pinched crescents.
- *Preservation of Core Invariants*: Full-bleed badge geometry `0..144` ($r=22.0$), official `#0A3D91` token, centered cyclist silhouette (`translateX=18.18`, 18 unmodified paths), bold `RDR` label with `evenOdd`, and Rule 37 complexity limits are strictly preserved.

---

## 2. Test Specification (`TST-UI-514`)

### Test Cases
- `[TST-UI-514.5]` (Unit - `VectorAssetResolutionContractTest.kt`): Verify `bt_bike_radar.xml` source dot single-circle geometry (no snowman), uniform concentric rearward wave arcs, $\ge 7.0$ px cyclist clearance, and Rule 37 compliance.
- `[TST-UI-514.6]` (Regression - Vector Asset DOM & Resolution): Execute `VectorAssetResolutionContractTest` verifying all vector assets and protocol icon mappings pass 100%.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-514.5]` | Unit | `bt_bike_radar.xml` (`mini_icon_radar` geometry & clearance) | `REQ-UI-514.2`, `REQ-UI-514.4` | Specified |
| `[TST-UI-514.6]` | Regression | `VectorAssetResolutionContractTest.kt` (Full vector suite) | `REQ-PRO-001` | Specified |
