# Sprint Review & Retrospective: Sprint 2026-41.8

* **Ticket**: [ATT-3175](https://atrainingtracker.atlassian.net/browse/ATT-3175) (*Review & Retro - Sprint 2026-41.8*)
* **Sprint**: `2026-41.8`
* **Branch**: `sprint/2026-41.8` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 preview / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-11

---

## 1. Executive Summary & Review Outcomes

Sprint **2026-41.8** focused on cockpit UX polish, navigation guidance visual stability, climb and segment integration resilience, and robust hardware sensor lifecycle management.

Key milestones delivered:
1. **Sensor Grid In-Ride Customization ([ATT-3101](https://atrainingtracker.atlassian.net/browse/ATT-3101))**:
   - Restored direct long-press editing of cockpit sensor metrics during live workouts (`REQ-UI-336`), while preserving single-tap no-op safety.
2. **Unified Viewport Anchor Authority & Map Stability ([ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102))**:
   - Resolved the disorienting 1 Hz camera jitter oscillation during Follow-Me tracking by decoupling native Google Maps content padding and introducing analytical forward lookahead target projection (`REQ-MAP-046`).
3. **Climb & Segment Visual Ergonomics & Crash Defense**:
   - Normalized Climb popup elevation profile X-axis to local climb distance ([ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105)), eliminating the squished sliver bug.
   - Defended against fatal `NumberFormatException` crashes on segment completion ([ATT-3108](https://atrainingtracker.atlassian.net/browse/ATT-3108)).
   - Conducted visual design refinements for the Live Climb cockpit sheet ([ATT-3103](https://atrainingtracker.atlassian.net/browse/ATT-3103)) with multi-state `@Preview` composables.
   - Eliminated lingering grey container surface tints on in-ride fork decision cards ([ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104)).
4. **Sensor Discovery & Automatic Sport Type Selection**:
   - Promoted newly discovered sensors to the top of scan lists with notification alerts ([ATT-3099](https://atrainingtracker.atlassian.net/browse/ATT-3099)).
   - Automatically updated selected sport type when picking cycling or running routes ([ATT-3092](https://atrainingtracker.atlassian.net/browse/ATT-3092)).
   - Suppressed in-ride fork alerts when candidate routes share identical headings ([ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)).
   - Increased selected route polyline prominence at standard zoom levels ([ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)).

In accordance with **Rule 16 (Install Before Review)**, the sprint build was compiled from `sprint/2026-41.8` and installed onto the physical Google Pixel 10 prior to evaluation. All sprint tickets were inspected strictly one-by-one in rank order (**Rules 9 & 14**).

---

### 1.1 Review Evaluation Decisions (All 17 Tickets)

| Ticket | Summary | Review Result | Target Version | Status & Follow-up Actions |
| :--- | :--- | :---: | :---: | :--- |
| **[ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)** | Increase selected route polyline thickness and remove dashed overlay | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| **[ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)** | Suppress in-ride fork decision alerts when all routes share same direction | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| **[ATT-2079](https://atrainingtracker.atlassian.net/browse/ATT-2079)** | Inform Athlete on Process Kill Reasons via ApplicationExitInfo | **Deferred / In Review** | — | Retained in `Final Review (Human)` per user decision. |
| **[ATT-2192](https://atrainingtracker.atlassian.net/browse/ATT-2192)** | Modernize post-workout low sensor battery warning | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)`. Follow-up bugs **[ATT-3167](https://atrainingtracker.atlassian.net/browse/ATT-3167)** and **[ATT-3166](https://atrainingtracker.atlassian.net/browse/ATT-3166)** logged. |
| **[ATT-2346](https://atrainingtracker.atlassian.net/browse/ATT-2346)** | Scan Google Drive for historical workout files during bulk import | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)`. Follow-up **[ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168)** logged for format subdirectories. |
| **[ATT-2667](https://atrainingtracker.atlassian.net/browse/ATT-2667)** | Simplify raw GPS track with Douglas-Peucker for workout summary previews | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)` per user decision. |
| **[ATT-3056](https://atrainingtracker.atlassian.net/browse/ATT-3056)** | First workout does not immediately appear in periods view upon completion | **Deferred / In Review** | — | Retained in `Final Review (Human)` per user decision. |
| **[ATT-3092](https://atrainingtracker.atlassian.net/browse/ATT-3092)** | Automatically set selected sport type when selecting route (Bike / Run) | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| **[ATT-3099](https://atrainingtracker.atlassian.net/browse/ATT-3099)** | Highlight and prioritize newly discovered sensors at top of scan list | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Follow-up bug **[ATT-3169](https://atrainingtracker.atlassian.net/browse/ATT-3169)** logged for orphaned sensor deletion. |
| **[ATT-3100](https://atrainingtracker.atlassian.net/browse/ATT-3100)** | Anchor navigation hints over top of map view when map is present | **Rejected / Revert Requested** | `V4.9.39` | User decided to restore previous top-of-tab anchoring. Follow-up revert ticket **[ATT-3170](https://atrainingtracker.atlassian.net/browse/ATT-3170)** logged. |
| **[ATT-3101](https://atrainingtracker.atlassian.net/browse/ATT-3101)** | Long clicking sensor field on tracking screen does not open config dialog | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Formalized `REQ-UI-336` and `TST-UI-296`. |
| **[ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102)** | Map camera continuously alternates between center and bottom lookahead | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Follow-up bug **[ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171)** logged for lookahead slider physical calibration. |
| **[ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104)** | Remove remaining grey container surfaces from in-ride fork decision card | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| **[ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105)** | Normalize Climb popup elevation profile X-axis to climb distance | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Follow-up bug **[ATT-3172](https://atrainingtracker.atlassian.net/browse/ATT-3172)** logged for preview polyline prominence. |
| **[ATT-3107](https://atrainingtracker.atlassian.net/browse/ATT-3107)** | Display elevation profile with adequate vertical height when map disabled | **Evaluated with Follow-up** | `V4.9.39` | Follow-up bug **[ATT-3173](https://atrainingtracker.atlassian.net/browse/ATT-3173)** logged to restore canonical adaptive height and prevent 50% screen stretching. |
| **[ATT-3108](https://atrainingtracker.atlassian.net/browse/ATT-3108)** | LiveSegmentSheet crashes with NumberFormatException on segment finish | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| **[ATT-3103](https://atrainingtracker.atlassian.net/browse/ATT-3103)** | Iterative visual design optimization for Live Climb cockpit sheet | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3174](https://atrainingtracker.atlassian.net/browse/ATT-3174)** logged for linear interpolation along sparse climb points. |

---

### 1.2 Backlog Follow-Up Tickets Created During Joint Review

In strict compliance with **"No code changes during Sprint Review & Lean Defect Recording"**, human feedback and observations were captured immediately into dedicated follow-up tickets ranked at the top of the backlog:

1. **[ATT-3174](https://atrainingtracker.atlassian.net/browse/ATT-3174)**: `[Verbesserung] Linearly interpolate sparse path points on Climb elevation profile and live progress metrics` (Rank 1).
2. **[ATT-3173](https://atrainingtracker.atlassian.net/browse/ATT-3173)**: `[Bug] Sole elevation profile on tracking screen without map stretches to half screen instead of using canonical adaptive height` (Rank 2).
3. **[ATT-3172](https://atrainingtracker.atlassian.net/browse/ATT-3172)**: `[Bug] Route preview on RoutesScreen and RouteDetailSheet hardcodes isActiveNavigation=true, ignoring active/inactive selection state` (Rank 3).
4. **[ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171)**: `[Bug] Modifying Vorausschau-Kameraabstand setting has barely noticeable effect on map follow-me camera offset` (Rank 4).
5. **[ATT-3170](https://atrainingtracker.atlassian.net/browse/ATT-3170)**: `[Verbesserung] Revert ATT-3100: Restore top-of-tab anchoring for navigation hints in tracking view` (Rank 5).
6. **[ATT-3169](https://atrainingtracker.atlassian.net/browse/ATT-3169)**: `[Bug] Deleting active connected sensor leaves orphaned ghost 'New Sensor' in Control Tracking leading to dimmed blank dialog` (Rank 6).
7. **[ATT-3167](https://atrainingtracker.atlassian.net/browse/ATT-3167)**: `[Bug] Post-workout low battery snackbar is occluded by Android system navigation bar` (Rank 7).
8. **[ATT-3166](https://atrainingtracker.atlassian.net/browse/ATT-3166)**: `[Verbesserung] Filter sensors and devices by protocol, category, and battery level with notification deep-link` (Rank 8).
9. **[ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168)**: `[Verbesserung] Organize Google Drive workouts into format-specific directories (FIT, TCX, GPX) for export and import` (Rank 9).

---

## 2. Topic 1 Deep Dive: Chesterton's Fence for Empty Callback Lambdas & Intent-Explicit Signatures (ATT-3101)

### 2.1 The Issue
During refactoring in Sprint 2026-41.2 (`e5033b5c`, ATT-2620), `SensorGridScreen.kt` was restructured to separate `ScreenMode.CONFIGURATION` from `ScreenMode.TRACKING`. In the tracking branch, `onEdit = {}` was passed as an empty lambda under the mistaken assumption that tracking mode should not allow any editing. However, inside `SensorFieldView.kt`, the long-press gesture delegated to `onEdit()` when in `ScreenMode.TRACKING`. Passing `{}` silently deleted the in-ride long-click editing feature without breaking existing tests.

### 2.2 Forensic Analysis
1. **Ambiguous Callback Semantics**: `SensorFieldView` had a single callback `onEdit: () -> Unit`. Inside the composable, `onEdit()` was invoked on single-click in configuration mode, but on long-click in tracking mode. This dual-use obscured the caller's understanding of what `onEdit` represented.
2. **Missing Chesterton's Fence on Empty Lambdas**: Passing `{}` to a callback parameter is functionally a deletion of code/behavior, but in diffs it looks like "safe boilerplate".
3. **Missing Gesture Contract Tests**: Unit tests verified ViewModels and SQLite repositories, but lacked gesture invocation assertions.

### 2.3 Permanent Countermeasure (Rule 34)
* When refactoring containers or screens, developers must audit all call sites within a child component before passing `{}` or suppressing an existing callback parameter.
* Event callbacks must use intent-explicit names (`onConfigureField` vs. `onPickTileToMove`) rather than generic ambiguous names (`onEdit`).
* Components handling multi-gesture inputs (`combinedClickable`, gestures) must be protected by automated gesture contract tests.

---

## 3. Topic 2 Deep Dive: Sparse Path Geometries & Continuous Vector Projection (ATT-3103 & ATT-3174)

### 3.1 The Issue
On straight road stretches or imported GPX tracks, route vertices can be 50–200 m apart. During climb tracking, `LiveClimbsRepository` snapped progress strictly to the nearest discrete vertex (`path[closestIdx]`). Consequently, the rider indicator pin on the elevation profile, remaining distance, elevation gain, and current gradient stayed frozen and then jumped abruptly from vertex to vertex.

### 3.2 Mathematical Formulation
Instead of nearest-neighbor point quantization, progress must be determined by projecting the athlete's current location $L$ onto the adjacent polyline segment $[P_i, P_{i+1}]$:
$$\vec{v} = P_{i+1} - P_i$$
$$s = \frac{(L - P_i) \cdot \vec{v}}{\|\vec{v}\|^2} \in [0.0, 1.0]$$
The interpolated metrics are continuous:
$$\text{dist} = P_i.\text{dist} + s \times (P_{i+1}.\text{dist} - P_i.\text{dist})$$
$$\text{alt} = P_i.\text{alt} + s \times (P_{i+1}.\text{alt} - P_i.\text{alt})$$

### 3.3 Permanent Countermeasure (Design Guidelines §2.4)
Progress tracking along geometric paths (climbs, segments, routes) MUST linearly project the athlete's current location onto the nearest line segment $[P_i, P_{i+1}]$ to continuously compute distance, altitude, and visual marker positions. Codified permanently in `docs/design_guidelines.md` Section 2.4 (*Continuous Linear Projection for Sparse Geometry Progress Tracking*).

---

## 4. Topic 3 Deep Dive: Canonical Chart Sizing vs. Unbounded Container Flexibility (ATT-3107 & ATT-3173)

### 4.1 The Issue
When the map was disabled on tracking tabs, `ATT-3107` assigned `Modifier.weight(1f)` and `fillMaxHeight()` to the elevation profile, causing it to consume ~50% of the entire screen height, ballooning into an oversized box.

### 4.2 Forensic Analysis
Across the rest of the application (e.g., Segments and Route Detail sheets in `LiveSegmentSheet.kt` and `MapDetailLayout.kt`), the elevation profile is governed by a canonical adaptive height algorithm:
$$\text{height} = 70\text{dp} + \left(\frac{\text{range}}{1000\text{m}}\right)^{0.6} \times (200\text{dp} - 70\text{dp})$$
It scales between $70\text{dp}$ (flat) and $200\text{dp}$ (mountainous) based strictly on $\Delta H = H_{\max} - H_{\min}$, wrapping its content naturally without flexible stretching.

### 4.3 Permanent Countermeasure (Design Guidelines §2.3)
* Visual analytics and elevation profiles must not be given unbounded flexible weights (e.g. `weight(1f)`) that stretch them out of proportion.
* Sizing must adhere to canonical domain algorithms (`calculateElevationProfileHeight(range)`).
* Flexible space must be absorbed by scrollable content (sensor grid with `weight(1f, fill = false)`), while charts retain their fixed adaptive height.
* Codified permanently in `docs/design_guidelines.md` Section 2.3 (*Elevation Profile Sizing & Adaptive Height Invariant*).

---

## 5. Topic 4 Deep Dive: Clean Teardown on Persistence Deletion (ATT-3099 & ATT-3169)

### 5.1 The Issue
When a connected sensor was deleted from *Meine Sensoren* (`DeviceListFragment`), it was deleted from SQLite (`Devices.db`), but `BANALService` continued running and maintaining the active connection in memory. `ControlTrackingViewModel` looked up the active ID in `dbDevices`, found `null`, and fell back to `RemoteDeviceUIData(DeviceType.DUMMY, "New Sensor")`. Clicking this ghost icon opened an empty `DialogFragment` with `FLAG_DIM_BEHIND`, darkening the screen without any view content or dismissal controls.

### 5.2 Permanent Countermeasure (Governance Rule 35)
* Deleting an entity from persistence must immediately notify and tear down the corresponding hardware/background service connection in `BANALService`.
* ViewModels must defensively discard orphaned IDs rather than constructing invalid fallback models (`DeviceType.DUMMY`) that open broken dialogs.
* Codified permanently in `.agents/rules/aspice_governance.md` Rule 35 (*Clean Hardware Teardown and Orphan Defense on Persistence Deletion*).

---

## 6. Retrospective Process Hardening & Countermeasures Summary

### 6.1 ASPICE Governance Rules (`.agents/rules/aspice_governance.md`)
1. **Rule 34: Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests**:
   Never pass `{}` to suppress an existing callback without auditing child call sites. Use intent-explicit callback names. Protect multi-gesture components with gesture contract tests.
2. **Rule 35: Clean Hardware Teardown and Orphan Defense on Persistence Deletion**:
   Deleting persisted entities must trigger explicit disconnection and registration teardown in background services. UI layers must filter out orphaned entities.

### 6.2 UI & Visual Analytics Standards (`docs/design_guidelines.md`)
1. **Section 2.1: Coordinate Space Normalization for Sub-Entity Inspection Charts**:
   Normalize sub-entity distance domains to $[0, \text{length}]$ rather than inheriting parent route global coordinates.
2. **Section 2.3: Elevation Profile Sizing & Adaptive Height Invariant**:
   Chart heights are governed by canonical adaptive domain formulas (70dp–200dp based on $\Delta H$). Flexible container space is absorbed by scrollable content (`weight(1f, fill = false)`).
3. **Section 2.4: Continuous Linear Projection for Sparse Geometry Progress Tracking**:
   Live metric and HUD progress along polyline paths must project coordinates onto adjacent segments to calculate continuous linear interpolation for distance, altitude, and visual marker positions.
4. **Section 3.1: Explanatory Scale Direction in Settings Sliders**:
   Settings sliders must explain physical scale direction in subtitle/hint text.
5. **Section 6.1: Unified Viewport Anchor Authority for Map Tracking**:
   Follow-Me camera positioning and lookahead offsets share a single spatial authority to prevent competing layout recentering loops and camera jitter.

