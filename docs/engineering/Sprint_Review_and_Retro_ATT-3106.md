# Sprint Review & Retrospective: Sprint 2026-41.7

* **Ticket**: [ATT-3106](https://atrainingtracker.atlassian.net/browse/ATT-3106) (*Review & Retro - Sprint 2026-41.7*)
* **Sprint**: `2026-41.7`
* **Branch**: `sprint/2026-41.7` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 preview / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-10

---

## 1. Executive Summary & Review Outcomes

Sprint **2026-41.7** achieved significant milestones across developer testing tools, navigation HUD ergonomics, climb/segment popup architecture, map recomposition optimization, and core sensor initialization:

1. **TCX Replay Tool Execution Qualification ([ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969))**:
   - Qualified desktop workout replay simulation tool with PyQt6 GUI, isolated GLib D-Bus main loop, asynchronous ADB location injection queue, and Rule 30 host pre-flight diagnostics.
2. **Unified Bottom Sheet Architecture ([ATT-3053](https://atrainingtracker.atlassian.net/browse/ATT-3053))**:
   - Extracted `EntityDetailSheetScaffold` to eliminate duplicated modal bottom sheet scaffolding and harmonized Climb inspection screens with Segment and Route detail layouts.
3. **Core Sensor & Fresh Install Resilience ([ATT-3045](https://atrainingtracker.atlassian.net/browse/ATT-3045))**:
   - Defended against fresh install GPS dropouts by ensuring `BANALService.checkOrInitializeLocationDevices()` triggers dynamically upon service binding and permission grants.
4. **Navigation Cockpit Spatial Overlays & Isolation**:
   - Gated and decoupled Return Navigation HUD ([ATT-2938](https://atrainingtracker.atlassian.net/browse/ATT-2938)) and Turn-by-Turn prompt banners ([ATT-2941](https://atrainingtracker.atlassian.net/browse/ATT-2941)) into non-displacing top-center spatial overlays.
   - Gated candidate route matching to active selected routes ([ATT-2942](https://atrainingtracker.atlassian.net/browse/ATT-2942)).
   - Isolated map rendering tree from high-frequency telemetry recomposition churn ([ATT-2944](https://atrainingtracker.atlassian.net/browse/ATT-2944)).
   - Suppressed climb cockpit bottom sheets on inactive tabs ([ATT-2945](https://atrainingtracker.atlassian.net/browse/ATT-2945)).
   - Modernized in-ride fork decision card with direction grouping ([ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2962)).
   - Displayed live athlete position marker on Live Segment elevation profiles ([ATT-2967](https://atrainingtracker.atlassian.net/browse/ATT-2967)).
   - Modernized workout recovery dialog with Material 3 Jetpack Compose ([ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948)).
   - Clarified settings slider directions across all 9 locales and inverted lookahead padding ([ATT-3054](https://atrainingtracker.atlassian.net/browse/ATT-3054)).

In accordance with **Rule 16 (Install Before Review)**, the sprint build was compiled from `sprint/2026-41.7` and installed onto the physical Google Pixel 10 prior to evaluation. All sprint tickets were inspected strictly one-by-one in rank order (**Rules 9 & 14**).

---

### 1.1 Review Evaluation Decisions

| Ticket | Summary | Review Result | Target Version | Status & Follow-up Actions |
| :--- | :--- | :---: | :---: | :--- |
| **[ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)** | Synchronized TCX replay tool with PyQt6 GUI, BLE & mock GPS | **Approved & Accepted** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3099](https://atrainingtracker.atlassian.net/browse/ATT-3099)** created for prominent new sensor placement during scan. |
| **[ATT-2938](https://atrainingtracker.atlassian.net/browse/ATT-2938)** | Prevent unsolicited ReturnNavigationHud activation | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2941](https://atrainingtracker.atlassian.net/browse/ATT-2941)** | Align turn-by-turn navigation hints UI with updated design guidelines | **Approved & Accepted** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3100](https://atrainingtracker.atlassian.net/browse/ATT-3100)** created to anchor hints over top of map view when map is present. |
| **[ATT-2942](https://atrainingtracker.atlassian.net/browse/ATT-2942)** | Restrict in-ride fork candidate matching to active selected routes | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2944](https://atrainingtracker.atlassian.net/browse/ATT-2944)** | Isolate tracking map composable from sensor grid telemetry recomposition | **Approved & Accepted** | `V4.9.39` | Verified and approved. Bug **[ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102)** created for map camera center/bottom jitter oscillation. |
| **[ATT-2945](https://atrainingtracker.atlassian.net/browse/ATT-2945)** | Suppress climb cockpit bottom sheet on non-climb tracking tabs | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2947](https://atrainingtracker.atlassian.net/browse/ATT-2947)** | Align Live Climb cockpit sheet visual design with Live Segment popup | **Approved & Accepted** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3103](https://atrainingtracker.atlassian.net/browse/ATT-3103)** created for interactive UI refinement. |
| **[ATT-2948](https://atrainingtracker.atlassian.net/browse/ATT-2948)** | Modernize StartOrResume workout recovery dialog with M3 Compose | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2962)** | Modernize in-ride fork decision card with direction grouping | **Approved & Accepted** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104)** created to clean remaining grey container fills. |
| **[ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)** | Suppress in-ride fork decision alerts when all routes share same direction | **Deferred / In Review** | `V4.9.39` | Remains in `Final Review (Human)` for outdoor ride testing (Rule 28). |
| **[ATT-2967](https://atrainingtracker.atlassian.net/browse/ATT-2967)** | Athlete position marker on Live Segment elevation profile graph | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-3046](https://atrainingtracker.atlassian.net/browse/ATT-3046)** | Display elevation profile in workout details for trackless workouts | **Deferred / In Review** | — | Remains in `Final Review (Human)` for user verification of altitude dynamics. |
| **[ATT-3053](https://atrainingtracker.atlassian.net/browse/ATT-3053)** | Harmonize Climb popup visual design and architecture with Segment/Route | **Approved & Accepted** | `V4.9.39` | Verified and approved. Bug **[ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105)** created for climb elevation chart X-axis scaling. |
| **[ATT-3054](https://atrainingtracker.atlassian.net/browse/ATT-3054)** | Invert map camera lookahead padding and clarify zoom level direction in settings | **Approved & Accepted** | `V4.9.39` | Verified and approved. Interaction with camera animation captured in **ATT-3102**. |
| **[ATT-3045](https://atrainingtracker.atlassian.net/browse/ATT-3045)** | App does not receive location updates immediately after permission grant | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2079](https://atrainingtracker.atlassian.net/browse/ATT-2079)** | Inform Athlete on Process Kill Reasons via ApplicationExitInfo | **Deferred / In Review** | — | Remains in `Final Review (Human)` for subsequent review. |
| **[ATT-2192](https://atrainingtracker.atlassian.net/browse/ATT-2192)** | Modernize post-workout low sensor battery warning | **Deferred / In Review** | `V4.9.39` | Remains in `Final Review (Human)` for subsequent review. |
| **[ATT-2346](https://atrainingtracker.atlassian.net/browse/ATT-2346)** | Scan Google Drive for historical workout files during bulk import | **Deferred / In Review** | `V4.9.39` | Remains in `Final Review (Human)` for subsequent review. |
| **[ATT-2667](https://atrainingtracker.atlassian.net/browse/ATT-2667)** | Simplify raw GPS track with Douglas-Peucker for workout summary previews | **Deferred / In Review** | `V4.9.39` | Remains in `Final Review (Human)` for subsequent review. |
| **[ATT-3056](https://atrainingtracker.atlassian.net/browse/ATT-3056)** | First workout does not immediately appear in periods view upon completion | **Deferred / In Review** | — | Remains in `Final Review (Human)` for subsequent review. |
| **[ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)** | Increase selected route polyline thickness and remove dashed overlay | **In Analysis** | — | Reopened in `Analysis` to delineate single selected vs. active routes. |

---

## 2. Topic 1 Deep Dive: Coordinate Space Normalization for Entity Detail Charts (ATT-3053 & ATT-3105)

### 2.1 The Issue
During on-device testing of the unified Climb detail popup on Pixel 10, the elevation profile chart displayed a climb starting at 27.38 km along a 28 km route. The X-axis was plotted from 0 to 28.10 km, compressing the entire 722 m climb into a tiny sliver at the far right edge and leaving 97% of the graph completely blank.

### 2.2 Forensic Analysis
`ClimbOnMapScreen.kt` passed the climb's raw `PathPoint` list directly to `ElevationProfile`. Because these path points were sliced from the parent route, each point retained its absolute `distance` from the beginning of the route ($27380\text{ m} \dots 28102\text{ m}$). `ElevationProfile` computes its horizontal domain as $[\min(\text{distance}), \max(\text{distance})]$ with a zero-origin axis.

### 2.3 Permanent Countermeasure (Rule 32)
Whenever sub-entity segments, climbs, or intervals are displayed in an inspection chart, their distance domain must be normalized to local offset:
$$\text{localDistance}_i = \text{globalDistance}_i - \text{startDistance}$$
This ensures the sub-entity chart always spans from $0.0\text{ m}$ to the entity length, rendering an informative, full-width elevation profile.

---

## 3. Topic 2 Deep Dive: Map Camera Viewport ContentPadding vs. Animation Loop (ATT-3054 & ATT-3102)

### 3.1 The Issue
During Follow-Me tracking on the `Karte` tab, a screen recording on Pixel 10 revealed that the camera continuously oscillated back and forth every 1–2 seconds between centering the athlete position marker and placing it at the lower third (30% lookahead offset).

### 3.2 Forensic Analysis
In `ATrainingTrackerMap.kt`, lookahead was implemented via `GoogleMap(contentPadding = PaddingValues(top = topPadding))`. Top content padding informs Google Maps that the top of the viewport is obstructed, shifting the map's internal camera center downward. However, `followMeController` concurrently invoked:
```kotlin
cameraPositionState.animate(
    CameraUpdateFactory.newCameraPosition(
        CameraPosition.builder().target(currentLocation)...
    ), 400
)
```
The interaction between continuous recompositions, content padding recalculations in `BoxWithConstraints`, and the animation controller produced conflicting camera target evaluations.

### 3.3 Permanent Countermeasure (Rule 33)
Camera positioning and lookahead padding must have a unified authority. Target coordinates or camera bounds must cleanly account for viewport offsets without creating fighting loops between Google Maps internal content padding and external camera animations.

---

## 4. Retrospective Process Hardening & Countermeasures

1. **Rule 32: Coordinate Space Normalization for Sub-Entity Inspection Charts**:
   Sub-entity inspection screens (climbs, segments, laps) MUST normalize distance coordinates to $[0, \text{length}]$ before feeding charting components.
2. **Rule 33: Unified Viewport Anchor Authority for Map Tracking**:
   Lookahead offsets and camera positioning logic must share a single spatial authority to prevent camera oscillation and jitter.
