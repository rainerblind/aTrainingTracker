# Sprint Review & Retrospective: Sprint 2026-41.4

* **Ticket**: [ATT-2764](https://atrainingtracker.atlassian.net/browse/ATT-2764) (*-- Review & Retro --*)
* **Sprint**: `2026-41.4`
* **Branch**: `sprint/2026-41.4` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 17 / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-09

---

## 1. Executive Summary & Review Outcome

Sprint **2026-41.4** was an intensive, high-throughput engineering sprint delivering 16 functional work items covering field stability hotfixes, major developer velocity acceleration, route visualization intelligence, sensor UI polish, and telemetry integrity:

1. **Critical Field Hotfix (`V4.9.38.4`)**:
   - Resolved critical Android 14 (API 34) crash `NoSuchFieldError: Api34Impl.getActionScrollInDirection` when opening `ControlTrackingScreen` ([ATT-2767](https://atrainingtracker.atlassian.net/browse/ATT-2767)).
2. **10x Test Velocity Acceleration**:
   - Parallelized unit test execution (`maxParallelForks`, worker memory tuning, Gradle caching) reducing full clean-room suite execution from ~32 minutes down to ~2.5–3.5 minutes ([ATT-2770](https://atrainingtracker.atlassian.net/browse/ATT-2770)).
3. **Route Intelligence & Segment Visualizations**:
   - Segment detail bottom sheet reuse on route segment tap ([ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)).
   - Reverse route discovery displaying saved routes containing a segment ([ATT-2585](https://atrainingtracker.atlassian.net/browse/ATT-2585)).
   - Map layers menu for climbs, segments, and waypoints with permanent base route line anchor ([ATT-2763](https://atrainingtracker.atlassian.net/browse/ATT-2763)).
   - Route polyline palette transition from green to royal blue for high contrast against terrain and climb categories ([ATT-2761](https://atrainingtracker.atlassian.net/browse/ATT-2761)).
   - Removal of climb pin clutter and unified UC climb representation across map and profile ([ATT-2748](https://atrainingtracker.atlassian.net/browse/ATT-2748)).
   - Active sport discipline filtering and sport iconography in quick route selector ([ATT-2668](https://atrainingtracker.atlassian.net/browse/ATT-2668)).
4. **Sensor & Cockpit Enhancements**:
   - Remote device tile name truncation prevention and multi-line wrapping ([ATT-2772](https://atrainingtracker.atlassian.net/browse/ATT-2772)).
   - Untinted official ANT+ badge rendering and BLE hint removal from ANT+ service status sheet ([ATT-2749](https://atrainingtracker.atlassian.net/browse/ATT-2749)).
   - Tracking tab config toggle layout restructuring to eliminate German text truncation ([ATT-2744](https://atrainingtracker.atlassian.net/browse/ATT-2744)).
   - Removal of in-ride auto-detected route prompt banner from telemetry sensor grid ([ATT-2771](https://atrainingtracker.atlassian.net/browse/ATT-2771)).
   - Direct `TRANSPORT_LE` connection, aggressive scan parameters, and Android 14+ connected device foreground service declarations ([ATT-2773](https://atrainingtracker.atlassian.net/browse/ATT-2773)).
5. **Data Processing & Architecture**:
   - Douglas-Peucker GPS track simplification (10m tolerance) and scalar stream downsampling for workout summary previews ([ATT-2667](https://atrainingtracker.atlassian.net/browse/ATT-2667)).
   - User-configurable elevation profile smoothing sigma in expert settings ([ATT-2746](https://atrainingtracker.atlassian.net/browse/ATT-2746)).

In accordance with **Rule 16 (Install Before Review)**, the sprint build was compiled from `sprint/2026-41.4` and installed onto the physical Google Pixel 10 prior to evaluation. All sprint tickets were inspected strictly one-by-one in rank order (**Rules 9 & 14**).

---

### 1.1 Review Evaluation Decisions

| Ticket | Summary | Review Result | Target Version | Status & Follow-up Actions |
|:---|:---|:---:|:---:|:---|
| **[ATT-2767](https://atrainingtracker.atlassian.net/browse/ATT-2767)** | Fix NoSuchFieldError crash on Android 14 in Api34Impl | **Approved & Accepted** | `V4.9.38.4` | Field hotfix verified and accepted for immediate hotfix release. |
| **[ATT-2770](https://atrainingtracker.atlassian.net/browse/ATT-2770)** | Accelerate test suite execution via parallel test forks & caching | **Approved & Accepted** | `V4.9.39` | ~10x speedup verified (full suite ~2.5–3.5m). |
| **[ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)** | Open Segment details popup when clicking segment in route breakdown | **Approved w/ Refinement** | `V4.9.39` | Accepted. Follow-up **ATT-2860** filed to polish climb/segment bottom sheet styling. |
| **[ATT-2772](https://atrainingtracker.atlassian.net/browse/ATT-2772)** | Limit sensor name width & allow line wrapping in device tiles | **Approved & Accepted** | `V4.9.39` | Sensor tiles wrap long names cleanly without layout blowout. |
| **[ATT-2585](https://atrainingtracker.atlassian.net/browse/ATT-2585)** | Show Saved Routes Containing Segment in Segment Details | **Approved w/ Refinements** | `V4.9.39` | Accepted. Follow-ups filed: **ATT-2861** (show route popup instead of full navigation) and **ATT-2862** (investigate missed detection for Schönaich Welle 1 on Nur ein Test). |
| **[ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)** | Filter file picker strictly by file extension for FIT, TCX, GPX | **Rejected (n.i.O.)** | — | SAF still shows all files and selecting a file produces no reaction. Moved back to `Zu erledigen` for rework. |
| **[ATT-2744](https://atrainingtracker.atlassian.net/browse/ATT-2744)** | Improve tracking tab configuration toggles layout | **Approved & Accepted** | `V4.9.39` | German label truncation eliminated. |
| **[ATT-2746](https://atrainingtracker.atlassian.net/browse/ATT-2746)** | Elevation smoothing sigma configurable, default 21m | **Approved & Accepted** | `V4.9.39` | Verified in Expert Settings and elevation profile. |
| **[ATT-2761](https://atrainingtracker.atlassian.net/browse/ATT-2761)** | Transition route color palette to Royal Blue | **Approved & Accepted** | `V4.9.39` | High contrast against green terrain and climb category spans. |
| **[ATT-2748](https://atrainingtracker.atlassian.net/browse/ATT-2748)** | Remove climb pins & display UC climbs on map/profile | **Approved & Accepted** | `V4.9.39` | Clutter removed; UC climbs correctly rendered. |
| **[ATT-2763](https://atrainingtracker.atlassian.net/browse/ATT-2763)** | Toggleable climbs/segments/waypoints with layers menu | **Partly Approved / Accepted** | `V4.9.39` | Accepted. Follow-ups filed: **ATT-2863** (fix segment unselection toggle) and **ATT-2864** (solid climb above route, dashed segment above climb). |
| **[ATT-2749](https://atrainingtracker.atlassian.net/browse/ATT-2749)** | Untinted ANT+ logo & remove BLE hint from ANT+ status sheet | **Approved & Accepted** | `V4.9.39` | Untinted authentic branding verified; BLE note cleanly removed. |
| **[ATT-2668](https://atrainingtracker.atlassian.net/browse/ATT-2668)** | Filter route selector by sport & display sport icon | **Partly Approved / Accepted** | `V4.9.39` | Accepted. Follow-up **ATT-2865** filed to preserve authentic sport icon colors without monochrome theme tint. |
| **[ATT-2667](https://atrainingtracker.atlassian.net/browse/ATT-2667)** | Simplify raw GPS track with Douglas-Peucker for previews | **Deferred / In Review** | `V4.9.39` | On-device physical testing deferred; ticket remains in Review for V4.9.39. |
| **[ATT-2771](https://atrainingtracker.atlassian.net/browse/ATT-2771)** | Remove in-ride route auto-detect banner from sensor grid | **Approved & Accepted** | `V4.9.39` | Telemetry grid stability verified. |
| **[ATT-2773](https://atrainingtracker.atlassian.net/browse/ATT-2773)** | Fix BLE sensor discovery on Android 14+ | **Approved & Accepted** | `V4.9.39` | LE transport and manifest modernization accepted. Follow-up **ATT-2866** filed for unbonded discovery / parallel app package investigation. |

---

## 2. Retrospective: Observations, Root Causes & Process Hardening

### 2.1 Action Item 1: Release Version Separation (Hotfix vs Minor Feature Release)
* **Observation**:
  - Initially during Joint Review, all tickets were proposed with FixVersion `V4.9.38.4`.
  - The human user clarified that `V4.9.38.4` is strictly an immediate field crash hotfix for ATT-2767, while all other feature and non-crash improvement items must be targeted to `V4.9.39`.
* **Root Cause**:
  - Absence of an explicit version-tagging guideline distinguishing emergency point hotfixes (`patch.x`) from regular sprint milestone deliveries (`minor`).
* **Process Hardening**:
  - Updated **Rule 19 (.agents/rules/aspice_governance.md)**:
    > *"When a sprint includes an emergency production crash hotfix (e.g. `V4.9.38.4`), that patch version is reserved exclusively for the hotfix ticket(s). All non-hotfix features, refactorings, and improvements developed in the sprint must be tagged with the upcoming minor release (e.g. `V4.9.39`). Never batch non-crash features into an emergency crash hotfix version."*

---

### 2.2 Action Item 2: Storage Access Framework (SAF) vs Custom File Pickers (ATT-2740)
* **Observation**:
  - `ATT-2740` was rejected on the physical Pixel 10 (Android 17 / SDK 37) because `ACTION_OPEN_DOCUMENT` still displayed non-target files, and clicking a valid `.tcx` or `.fit` file produced no reaction in the app.
* **Root Cause**:
  - Android SAF relies on system document providers which handle custom binary MIME types inconsistently (especially for non-standard formats like FIT or TCX).
  - Broadening MIME filters or relying on system picker intent extras without fallback intent handling creates silent failures.
* **Process Hardening**:
  - `ATT-2740` bounced back to `Zu erledigen`.
  - For future file import work, implement strict URI extension validation and provide an in-app file browser or explicit content-type intent fallbacks rather than relying solely on SAF MIME filters.

---

### 2.3 Action Item 3: Map Overlay Z-Order and Stroke Styling Invariants (ATT-2763)
* **Observation**:
  - The user noted that climbs should appear as solid continuous lines above the route line, and segments should appear as dashed lines above the climb overlay.
* **Root Cause**:
  - Overlay Z-order and stroke pattern (solid vs dashed) were not formally specified as SWE.2 architecture invariants in Stage 2/3.
* **Process Hardening**:
  - Filed **ATT-2864** to enforce the formal overlay rendering hierarchy:
    1. Base Route Line: Solid (`TTColor.RouteSelected`), bottom layer.
    2. Climb Spans: Solid colored polyline, middle layer (above route line).
    3. Segment Spans: Dashed polyline, top layer (above climb spans).
  - Stage 2 requirements for multi-layer map rendering must always explicitly specify Z-ordering and stroke dash patterns.

---

### 2.4 Action Item 4: Authentic Multi-Color Icon Assets vs Theme Tinting (ATT-2668 & ATT-2749)
* **Observation**:
  - In ATT-2668, sport type icons were tinted with `MaterialTheme.colorScheme.onSurfaceVariant`, losing their distinct sport identity colors.
  - In ATT-2749, the ANT+ logo was similarly being tinted blue by default until `Color.Unspecified` was applied.
* **Root Cause**:
  - Default `Icon()` composable applies `LocalContentColor.current` tint unless explicitly overridden with `tint = Color.Unspecified`.
* **Process Hardening**:
  - Filed **ATT-2865** for sport type icons.
  - UI guideline rule: Domain icons with inherent branding or established categorical color palettes (sport types, partner badges, official protocol logos) must specify `tint = Color.Unspecified`.

---

### 2.5 Action Item 5: Unbonded BLE Peripheral Discovery & App Package Coexistence (ATT-2773)
* **Observation**:
  - While direct `TRANSPORT_LE` and Android 14+ foreground service types were verified, unbonded BLE sensors were still not discovered during live scanning on Pixel 10, whereas devices already bonded in the parallel production app installation could be discovered in the debug build.
* **Root Cause**:
  - Android's Bluetooth adapter shares bonded device state globally across packages, but unbonded advertisement discovery requires active scan filters matching service UUIDs or promiscuous unbonded scan permissions.
* **Process Hardening**:
  - Filed **ATT-2866** to perform targeted investigation into GATT bonding state, broadcast service UUID filters, and parallel package coexistence.

---

## 3. Sprint Metrics & Artifact Traceability

* **Sprint Name**: `2026-41.4`
* **Parent Epics**: ATT-66, ATT-355, ATT-356, ATT-2455, ATT-2564, ATT-2582
* **Tickets Evaluated in Review**: 16
* **Tickets Accepted**: 14 (1 for `V4.9.38.4`, 13 for `V4.9.39`)
* **Tickets Deferred in Review**: 1 ([ATT-2667](https://atrainingtracker.atlassian.net/browse/ATT-2667) for `V4.9.39`)
* **Tickets Rejected / Rework**: 1 ([ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740))
* **New Backlog Tickets Filed from Review (Ranked at Top of Backlog)**:
  1. [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860): Visual and UX styling polish for Climb and Segment detail bottom sheets
  2. [ATT-2861](https://atrainingtracker.atlassian.net/browse/ATT-2861): Display route details popup instead of full route navigation when tapping route card in segment view
  3. [ATT-2862](https://atrainingtracker.atlassian.net/browse/ATT-2862): Investigate missed segment detection for Schönaich Welle 1 on Route Nur ein Test
  4. [ATT-2863](https://atrainingtracker.atlassian.net/browse/ATT-2863): Fix segment unselection toggle in route map layers menu to hide segment polylines
  5. [ATT-2864](https://atrainingtracker.atlassian.net/browse/ATT-2864): Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs
  6. [ATT-2865](https://atrainingtracker.atlassian.net/browse/ATT-2865): Preserve authentic sport type icon colors in route selector sheet
  7. [ATT-2866](https://atrainingtracker.atlassian.net/browse/ATT-2866): Investigate BLE device discovery issues for unbonded sensors and parallel debug release installations
* **Clean-Room Full Regression Suite (`./gradlew testDebugUnitTest`)**:
  - Passing 100% across all 2,176 tests in ~2.5–3.5 minutes.
