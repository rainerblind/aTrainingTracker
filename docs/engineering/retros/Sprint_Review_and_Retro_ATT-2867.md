# Sprint Review & Retrospective: Sprint 2026-41.5

* **Ticket**: [ATT-2867](https://atrainingtracker.atlassian.net/browse/ATT-2867) (*-- Review & Retro --*)
* **Sprint**: `2026-41.5`
* **Branch**: `sprint/2026-41.5` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 17 / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-09

---

## 1. Executive Summary & Review Outcome

Sprint **2026-41.5** was an intensive, high-throughput delivery cycle addressing critical production crash immunity, file picker storage access framework stability, cockpit UI ergonomics, navigation drawer decluttering, route/segment matching intelligence, multi-tier map polyline layering, BLE sensor discovery forensics, and in-ride fork route detection optimization:

1. **Crash Immunity & Universal Fallback Assets (`ATT-2858` / `ATT-2856`)**:
   - Resolved fatal `Resources$NotFoundException` production crash (Firebase Crashlytics Issue `1a5e51ef2d4d97e17883c7b67c486655`) by populating 49 missing baseline fallback drawables in `res/drawable/`.
   - Implemented `SafePainterResource.kt` providing defensive try/catch loading with graceful vector fallback degradation across high-risk Compose call sites.
   - Backported and verified cleanly on both `hotfix/V4.9.38.4__267` and `sprint/2026-41.5`.
2. **File Picker Selectability (`ATT-2740`)**:
   - Restored `application/octet-stream` alongside specific MIME types in `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES` so that Android's Storage Access Framework (`DocumentsUI`) allows file selection without greying out or ignoring workout files.
   - Enforced strict defense-in-depth file extension validation via `ImportFileValidator`.
3. **Sensor UI & Symmetrical Cockpit Centering (`ATT-2791`)**:
   - Centered remote sensor device tiles on `ControlTrackingScreen` using an invisible symmetrical balancing anchor (`clearAndSetSemantics { }`), eliminating visual right-shift caused by `ResearchButton`.
4. **Navigation Drawer Decluttering (`ATT-2780`)**:
   - Removed misleading "Suche" / "Search Settings" item from the main navigation drawer.
   - Relocated low-level BLE/ANT+ radio search parameters into a new modular accordion section in `AdvancedTuningDialog.kt` with 9-language translation parity.
5. **Route & Segment Visualizations & Matching**:
   - `ATT-2860`: Visual polish for climb and segment bottom sheets. *Rejected (n.i.O.)*; user requested segment popup to look identical to the one that pops up in the map (reusing the same code), and route popup to follow this layout. Bounced back to `Analysis`.
   - `ATT-2861`: Modal route details sheet (`RouteDetailSheet`) on segment route tap instead of full-screen navigation. *Approved & Accepted*; layout to be harmonized with `ATT-2860` rework.
   - `ATT-2862`: Orthogonal polyline projection and candidate interval matching in `RouteSegmentMatcher.kt`, fixing missed detection for segment *"Schönaich Welle 1 --> ganz"* on route *"Nur ein Test"*. *Approved & Accepted*.
   - `ATT-2863`: Gated `backgroundPaths` and de-duplicated matched segments in `RouteOnMapScreen.kt`, ensuring unselecting "Segments" in the layers menu hides all segment lines. *Approved & Accepted*.
   - `ATT-2864`: Solid climb polyline above route line and dashed segment above climbs. *Rejected (n.i.O.)*; user reported it does not work yet and guided that routes are drawn in two layers (one solid, one dashed). Bounced back to `Zu erledigen`.
   - `ATT-2865`: Preserved authentic multi-color vector asset colors for sport icons in `RouteSelectorSheet.kt` (`RouteCard`) via `tint = Color.Unspecified`. *Approved & Accepted*.
6. **BLE Discovery Forensic Investigation (`ATT-2866`)**:
   - Executed deep forensic research on why unbonded sensors fail to appear in debug builds while bonded sensors succeed. Zero production code modified (strictly adhering to research mandate). Formulated 4 actionable follow-up tickets. *Approved & Accepted*.
7. **In-Ride Fork Route Detection Optimization (`ATT-2873` / `ATT-2874`)**:
   - `ATT-2873`: $O(1)$ spatial bounding-box rejection, sport-type pre-filtering, and quiescent GPS evaluation throttling in `ForkRouteMatcher.kt` and `ForkNavigationRepository.kt`. *Stays in Review* for on-bike field testing.
   - `ATT-2874`: Floated `ForkDecisionCard` as top-level spatial `Box` overlay at `Alignment.TopCenter` and gated strictly behind `state.showNavigationHints`, eliminating telemetry displacement. *Stays in Review* for on-bike field testing.

In accordance with **Rule 16 (Install Before Review)**, the sprint build was compiled from `sprint/2026-41.5` and installed onto the physical Google Pixel 10 prior to evaluation. All sprint tickets were inspected strictly one-by-one in rank order (**Rules 9 & 14**).

---

### 1.1 Review Evaluation Decisions

| Ticket | Summary | Review Result | Target Version | Status & Follow-up Actions |
| :--- | :--- | :---: | :---: | :--- |
| **[ATT-2858](https://atrainingtracker.atlassian.net/browse/ATT-2858)** | Eliminate ResourcesNotFoundException crashes by populating fallback drawables and defensive Compose loading | **Approved & Accepted** | `V4.9.38.4` | Closed as `Erledigt`. Backported to `hotfix/V4.9.38.4__267`. |
| **[ATT-2856](https://atrainingtracker.atlassian.net/browse/ATT-2856)** | Production crash in SportTypeSelectorKt.SportItem | **Approved & Accepted** | `V4.9.38.4` | Resolved and verified via ATT-2858 blanket fix. |
| **[ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)** | Filter file picker strictly by file extension for FIT, TCX, and GPX import | **Approved & Accepted** | `V4.9.39` | Option A (Selectability First + defensive extension validation) accepted. |
| **[ATT-2791](https://atrainingtracker.atlassian.net/browse/ATT-2791)** | Center sensor device tiles on ControlTrackingScreen with balanced header layout | **Approved & Accepted** | `V4.9.39` | Symmetrical balancing anchor verified. |
| **[ATT-2780](https://atrainingtracker.atlassian.net/browse/ATT-2780)** | Relocate sensor search settings from navigation drawer to advanced tuning | **Approved & Accepted** | `V4.9.39` | Drawer decluttered; new accordion section functional in Advanced Tuning. |
| **[ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)** | Visual and UX styling polish for Climb and Segment detail bottom sheets | **Rejected (n.i.O.)** | — | Bounced to `Analysis`. User requirement: Segment popup must look identical to the map popup (code reuse), and route popup must follow this layout. |
| **[ATT-2861](https://atrainingtracker.atlassian.net/browse/ATT-2861)** | Display route details popup instead of full route navigation when tapping route card in segment view | **Approved & Accepted** | `V4.9.39` | Modal overlay behavior accepted. Layout will be unified with map popup during ATT-2860 rework. |
| **[ATT-2862](https://atrainingtracker.atlassian.net/browse/ATT-2862)** | Investigate missed segment detection for Schönaich Welle 1 on Route Nur ein Test | **Approved & Accepted** | `V4.9.39` | Orthogonal projection & interval matching verified; segment detection restored. |
| **[ATT-2863](https://atrainingtracker.atlassian.net/browse/ATT-2863)** | Fix segment unselection toggle in route map layers menu to hide segment polylines | **Approved & Accepted** | `V4.9.39` | Background paths filtering and de-duplication verified. |
| **[ATT-2864](https://atrainingtracker.atlassian.net/browse/ATT-2864)** | Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs | **Rejected (n.i.O.)** | — | Bounced to `Zu erledigen`. User reported it does not work yet; root cause linked to multi-layer drawing (solid in one layer, dashed in another). |
| **[ATT-2865](https://atrainingtracker.atlassian.net/browse/ATT-2865)** | Preserve authentic sport type icon colors in route selector sheet | **Approved & Accepted** | `V4.9.39` | `tint = Color.Unspecified` verified; authentic vector asset colors preserved. |
| **[ATT-2866](https://atrainingtracker.atlassian.net/browse/ATT-2866)** | Investigate BLE device discovery issues for unbonded sensors and parallel debug release installations | **Approved & Accepted** | `V4.9.39` | Forensic analysis deliverable approved; zero production code modified. 4 follow-up tickets defined. |
| **[ATT-2873](https://atrainingtracker.atlassian.net/browse/ATT-2873)** | Optimize in-ride fork route detection performance via spatial bounding-box rejection and GPS throttling | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for on-bike field testing. |
| **[ATT-2874](https://atrainingtracker.atlassian.net/browse/ATT-2874)** | Float fork decision prompt as top-level overlay and gate by tab navigation hints toggle | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for on-bike field testing. |

---

## 2. Retrospective: Observations, Root Causes & Process Hardening

### 2.1 Action Item 1: Dual Branch Integration for Production Hotfix FixVersions
* **Observation**:
  - `ATT-2858` and `ATT-2856` were initially merged into `sprint/2026-41.5` but were not backported to `hotfix/V4.9.38.4__267`, even though their Jira `FixVersion` was explicitly set to `V4.9.38.4`.
  - The human user had to inquire during the review whether the hotfix branch contained the fix.
* **Root Cause**:
  - The sprint workflow defaults to Strategy A (merging exclusively into `sprint/<sprint_id>`). When tickets carry a hotfix `FixVersion` corresponding to an open hotfix release branch, there was no automated prompt or invariant mandating immediate backporting to the hotfix branch.
* **Process Hardening**:
  - **New Rule / Invariant**: When an in-sprint ticket has a `FixVersion` targeting an active hotfix branch (`hotfix/*`), the agent must cherry-pick the verified code and assets onto the hotfix branch immediately following Stage 5 verification on the sprint branch, and verify compilation via `./gradlew assembleDebug` on the hotfix branch.

---

### 2.2 Action Item 2: Unified Entity Map Popups & Code Reuse (ATT-2860 & ATT-2861)
* **Observation**:
  - In Sprint 2026-41.4 and 41.5, separate bottom sheets were authored for segments and climbs (`SegmentDetailSheet.kt`, `ClimbDetailSheet.kt`, `RouteDetailSheet.kt`).
  - During review of `ATT-2860`, the user was dissatisfied with the discrepancy: *"The segment popup must look identical to the one that pops up in the map. I think, we should use the same code here. The route popup should follow this layout."*
* **Root Cause**:
  - Different development phases introduced bottom sheets independently rather than enforcing a single canonical composable for entity inspection across map taps and list item taps.
* **Process Hardening**:
  - `ATT-2860` bounced back to `Analysis`.
  - The rework must extract and share a single unified map popup composable used identically when tapping a segment/route on the map canvas and when tapping it in lists or breakdowns.

---

### 2.3 Action Item 3: Forensic Trace of Multi-Pass Polyline Rendering Pipelines (ATT-2864)
* **Observation**:
  - `ATT-2864` attempted to render climbs as solid polylines and segments as dashed polylines above climbs. On physical device review, the user observed that this did not work, noting: *"Note that the root cause might be that we draw the routes in two layers. Within one layer it is solid in the other it is dashed. Please check in more detail."*
* **Root Cause**:
  - The Stage 1 analysis adjusted z-indexes and patterns in `MapModels.kt` / `MapLayers.kt` without discovering that Google Maps polylines in the app are rendered across multiple layered passes (`XRayPolyline`, base route, background paths), causing stroke and pattern collisions.
* **Process Hardening**:
  - `ATT-2864` bounced back to `Zu erledigen`.
  - In Stage 1 Analysis for map canvas rendering, agents must document all active polyline drawing passes and their interaction before designing SWE.2 architecture.

---

### 2.4 Action Item 4: Deferred Review State for On-Bike / Field-Tested Features (ATT-2873 & ATT-2874)
* **Observation**:
  - `ATT-2873` and `ATT-2874` address dynamic in-ride fork-in-the-road route alert performance and non-displacing overlay presentation.
  - The human user decided that these tickets must stay in `Final Review (Human)` rather than being accepted immediately at the desk, allowing outdoor field testing on real rides.
* **Process Hardening**:
  - Formally recognize the **"Deferred for Field Review"** pattern: when a ticket modifies in-ride GPS dynamics or sensor protocols that require outdoor rides to judge, keeping the ticket in `Final Review (Human)` across sprint close allows real-world validation without delaying the sprint retro and merge to `develop`.
