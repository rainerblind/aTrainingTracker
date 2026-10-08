# Stage 5 Walkthrough - ATT-2748: Remove Climb Pins from Route Map and Display UC Climbs on Map and Elevation Profile

**Ticket**: [ATT-2748](https://atrainingtracker.atlassian.net/browse/ATT-2748)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*[Epic] Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Requirement Mapping**: `REQ-UI-307` (*Route Map Climb Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting & Elevation Profile Baseline Parity*, amending `REQ-UI-298` and `REQ-UI-299`)  
**Test Spec ID**: `TST-UI-267` (*Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification*)  
**Branch**: `improvement/ATT-2748`  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Objective

Under `ATT-2748`, visual clutter on route maps and discrepancies between route climb breakdowns and elevation profile visualizations were eliminated:
1. **Climb Pin Elimination on Route Map (`RouteOnMapScreen.kt`)**: Circular ascent start pin markers (`ic_ascent`) were removed from the route map layer. Since climbs are rendered directly as colored polyline spans along the route curve (`MapContentScope.climbs()`), redundant pins caused marker crowding. The map now renders strictly the Start (`control_start`) and End (`control_stop`) navigation markers.
2. **Full-Spectrum UC Climb Polyline Highlighting (`MapContentScope.kt`)**: Uncategorized (UC) climbs are no longer skipped during route map polyline generation. All 6 recognized UCI climb classifications (`HC`, `CAT_1`, `CAT_2`, `CAT_3`, `CAT_4`, and `UNCATEGORIZED`) are highlighted as colored polyline spans along the route curve at `zIndex = 25f`.
3. **Elevation Profile Baseline Parity (`ElevationProfile.kt`)**: Distance-domain elevation profile baseline indicator bars along the X-axis now include `ClimbCategory.UNCATEGORIZED` rendered in neutral grey (`#757575`) at `strokeWidth = 4.dp`, establishing complete parity with the route climb breakdown list.
4. **Contrast Harmony**: Builds seamlessly upon the Royal Blue route baseline introduced in **ATT-2761** (`#1565C0`), providing high contrast for Kat 4 green (`#2E7D32`) and UC neutral grey (`#757575`).

---

## 2. Test Execution & Evidence

### 2.1 Route Map Marker Contract Audit (`TST-UI-267.1`)
Verified in `RouteOnMapScreenClimbContractTest.kt`:
* Empty route paths produce zero markers.
* Non-empty route paths with multiple detected climbs produce strictly 2 markers: Start (`control_start`) and End (`control_stop`).
* Zero `ic_ascent` climb markers are created or supplied to `markers()` on `RouteOnMapScreen`.

### 2.2 Full-Spectrum Climb Polyline Highlighting Contract Tests (`TST-UI-267.2`)
Verified in `ClimbPolylineContractTest.kt`:
* Verified all 6 climb categories generate highlight data polylines at `zIndex = 25f` and `width = 10f`.
* `ClimbCategory.UNCATEGORIZED` resolves cleanly to `TTColor.ClimbUncategorized` (`Color(0xFF757575)`).
* Verified fallback endpoint generation when `pathPoints` is empty.

### 2.3 Elevation Profile Baseline UC Span Parity (`TST-UI-267.3`)
Verified in `ElevationProfileClimbSpanContractTest.kt`:
* In distance domain (`!isTimeDomain && climbs.isNotEmpty()`), climbs list containing UC climbs is not filtered.
* Baseline indicator bar is rendered for UC climbs with `cap = StrokeCap.Round`, `strokeWidth = 4.dp`, and color `Color(0xFF757575)`.

### 2.4 Living Documentation Synchronization (`TST-UI-267.4`)
* `docs/requirements.md` updated with `REQ-UI-307` set to `Verified`.
* `docs/tests.md` updated with `TST-UI-267` set to `Verified`.
* Requirement governance audited and verified via `tools/verify_requirement_governance.py`.

### 2.5 Clean-Room Regression Verification (`TST-UI-267.5`)
Full clean-room unit test suite executed:
```
BUILD SUCCESSFUL in 2m 57s
32 actionable tasks: 12 executed, 20 up-to-date
Total tests: 2172, Failures: 0, Errors: 0, Skipped: 0
```
100% pass rate achieved across all 2,172 tests with zero regressions.

---

## 3. UI Consistency & Design Guidelines Alignment (Rule 23)

* **Contrast & Color Palette**: In alignment with `docs/design_guidelines.md` Section 5.4, UC climb spans use `TTColor.ClimbUncategorized` (`#757575`), ensuring WCAG AA contrast against dark canvases and high chromatic distinction against `TTColor.RouteSelected` Royal Blue (`#1565C0`).
* **Visual Density**: Removing circular pins reduces visual occlusion on zoom levels $\le 14$, allowing continuous route curves and critical turns to remain clearly visible.
* **Component Parity**: Elevation profile X-axis baseline indicators and route map polylines now display an identical set of climbs matching the route climb breakdown sheet.

---

## 4. Requirement Traceability Matrix

| Requirement | Test Specification | Status | Deliverable / Test Artifact |
| :--- | :--- | :--- | :--- |
| `REQ-UI-307.1` (Route Map Pin Elimination) | `TST-UI-267.1` | **Verified** | `RouteOnMapScreen.kt`, `RouteOnMapScreenClimbContractTest.kt` |
| `REQ-UI-307.2` (Full-Spectrum UC Map Polyline) | `TST-UI-267.2` | **Verified** | `MapContentScope.kt`, `ClimbPolylineContractTest.kt` |
| `REQ-UI-307.3` (Elevation Profile UC Baseline) | `TST-UI-267.3` | **Verified** | `ElevationProfile.kt`, `ElevationProfileClimbSpanContractTest.kt` |
| `REQ-UI-307.4` (Chesterton's Fence Audit & Living Docs) | `TST-UI-267.4` | **Verified** | `docs/requirements.md`, `docs/tests.md` |
| `REQ-UI-307.5` (Clean-Room Suite Integrity) | `TST-UI-267.5` | **Verified** | 2,172 clean-room unit tests passed |

---

## 5. Conclusion

Stage 5 Verification for `ATT-2748` is complete with 100% test pass rate and zero regressions. All acceptance criteria are met, and the branch is ready for continuous sprint integration into `sprint/2026-41.4`.
