# Sprint Review & Retrospective: Sprint 2026-41.3

* **Ticket**: [ATT-2660](https://atrainingtracker.atlassian.net/browse/ATT-2660) (*-- Review & Retro --*)
* **Sprint**: `2026-41.3`
* **Branch**: `sprint/2026-41.3` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-08

---

## 1. Executive Summary & Review Outcome

Sprint **2026-41.3** was an extensive and high-velocity iteration addressing route intelligence, visual hierarchy, sensor UI/stability, and data sovereignty:
- Matched and listed starred Strava and local segments along planned routes ([ATT-2583](https://atrainingtracker.atlassian.net/browse/ATT-2583)).
- Dedicated climb detail bottom sheet with focused map and zoomed elevation profile ([ATT-2511](https://atrainingtracker.atlassian.net/browse/ATT-2511)).
- Climb highlighting on route map and elevation profile axis ([ATT-2509](https://atrainingtracker.atlassian.net/browse/ATT-2509), [ATT-2510](https://atrainingtracker.atlassian.net/browse/ATT-2510)).
- Elevation profile smoothing using distance-weighted kernel ([ATT-2512](https://atrainingtracker.atlassian.net/browse/ATT-2512)).
- Speed decay towards zero during prolonged GPS silence ([ATT-2616](https://atrainingtracker.atlassian.net/browse/ATT-2616)).
- Elimination of sensor header collision on Control Tracking Screen ([ATT-2479](https://atrainingtracker.atlassian.net/browse/ATT-2479)).
- Format-specific structuring of Import tab (FIT, TCX, GPX) ([ATT-2623](https://atrainingtracker.atlassian.net/browse/ATT-2623)).
- Sensor pairing UX, battery optimization flow, and cockpit tile grid spacing ([ATT-2625](https://atrainingtracker.atlassian.net/browse/ATT-2625), [ATT-2626](https://atrainingtracker.atlassian.net/browse/ATT-2626), [ATT-2621](https://atrainingtracker.atlassian.net/browse/ATT-2621), [ATT-2624](https://atrainingtracker.atlassian.net/browse/ATT-2624), [ATT-2620](https://atrainingtracker.atlassian.net/browse/ATT-2620)).

In accordance with **Rule 16 (Install Before Review)**, the Joint Review began with compiling and deploying the integrated sprint build to the attached physical Google Pixel 10 (`66020DLCR002FL`). The sprint tickets in `Final Review (Human)` / `Test` were reviewed strictly one-by-one in rank order (**Rule 9 & Rule 14**).

---

### 1.1 Review Evaluation Decisions

| Ticket | Summary | Review Result | Status & Follow-up Actions |
|:---|:---|:---:|:---|
| **[ATT-2622](https://atrainingtracker.atlassian.net/browse/ATT-2622)** | Filter file picker dialogs to matching workout file extensions for FIT, TCX, and GPX import | **Rejected (n.i.O.)** | Reopened and moved back to `Analysis`. In physical testing, selecting a FIT file still displays all `.tcx` files because SAF wildcard/octet-stream matching allows all binary files. SAF picker must strictly filter to `.fit`. |
| **[ATT-2511](https://atrainingtracker.atlassian.net/browse/ATT-2511)** | Display Dedicated Climb Detail View with Focused Map and Zoomed Elevation Profile on Climb Item Tap | **Accepted (i.O.)** | Fully accepted. Ready for human transition to `Erledigt`. |
| **[ATT-2479](https://atrainingtracker.atlassian.net/browse/ATT-2479)** | Sensor overlapping when there are too many sensors active | **Accepted w/ Refinements** | Core layout non-overlapping fix accepted. Filed **ATT-2772** (sensor tile name width limit & line wrap) and **ATT-2773** (BLE sensor scanning investigation) at top of backlog. |
| **[ATT-2616](https://atrainingtracker.atlassian.net/browse/ATT-2616)** | When we don't receive a location update after some time, the speed must go towards zero | **Accepted (i.O.)** | Halving exponential decay and pace nullification verified and accepted. Ready for human transition to `Erledigt`. |
| **[ATT-2583](https://atrainingtracker.atlassian.net/browse/ATT-2583)** | Match and List Starred Segments along Routes in Route Details | **Accepted w/ Refinements** | Core geodesic matching and breakdown display accepted. Filed **ATT-2774** (reuse existing Segment details popup on segment card tap) at top of backlog. |

---

## 2. Retrospective: Observations, Root Causes & Process Hardening

### 2.1 Action Item 1: Compaction & Restart Resume Mandate (Rule 1 & Rule 15 Hardening)
* **Observation**: Several times during this sprint, agents paused execution between tickets and asked the user whether to continue (e.g. after ATT-2628 and ATT-2621), despite Rule 15's full-sprint mandate.
* **Root Cause Analysis**:
  - Context compaction summaries often end with conversational handover phrases ("Next steps: start ticket Y").
  - LLM models default to conversational turn-taking reflexes after summarizing completed work, mistakenly treating compaction summaries as check-in points rather than autonomous instructions.
* **Process Hardening**:
  - **Rule 1 (.agents/rules/aspice_governance.md)** updated with explicit *Compaction & Restart Resume Mandate*:
    > *"Upon waking up from a context compaction or system restart during an active sprint, the agent MUST NOT ask the user what to do next or present an idle summary. The agent's first action MUST be to query the active sprint backlog, identify the current or next uncompleted ticket in rank order, re-read governance via `view_file`, and autonomously resume execution immediately."*
  - **Rule 15 (.agents/rules/aspice_governance.md)** updated with explicit *Compaction & Turn-Ending Invariant*:
    > *"When resuming from a context compaction or session restoration, the agent MUST NOT treat the compaction summary's 'Next Steps' as an interactive prompt for the user or ask for permission. Concluding a turn with questions like 'Shall I proceed with ATT-XXXX?' or 'Would you like me to continue?' is strictly prohibited."*
  - Synchronized in `docs/project_protocol.md`.

---

### 2.2 Action Item 2: SAF MIME Type Filtering vs. Extension Rigor (ATT-2622)
* **Observation**: In ATT-2622, providing `application/octet-stream` in the MIME types array for FIT file selection resulted in Android's Storage Access Framework (SAF) displaying all binary files in the directory (including TCX files).
* **Root Cause Analysis**:
  - In Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT` / `EXTRA_MIME_TYPES`), `application/octet-stream` is a generic fallback matched by almost all custom or unrecognized file formats.
  - Adding `application/octet-stream` effectively circumvents MIME type filtering.
* **Process Hardening**:
  - When restricting document pickers to proprietary or binary file formats (like FIT), do not supply generic MIME wildcards.
  - Use exact MIME types or evaluate custom document provider extension filtering (`EXTRA_MIME_TYPES`) and strict file extension matching in the document picker callback.
  - Bounced ATT-2622 back to Stage 1 Analysis to redesign SAF filtering without `octet-stream` broadening.

---

### 2.3 Action Item 3: UI Interaction Symmetry Across Route Breakdown Lists (ATT-2583 & ATT-2511)
* **Observation**: In route details, tapping a climb opens a rich `ClimbDetailSheet`, whereas tapping a matched segment did not open a detail sheet.
* **Root Cause Analysis**:
  - ATT-2583 focused primarily on geodesic spatial matching and rendering the breakdown cards, leaving the click action as a map scrub rather than a detail popup.
  - Users expect uniform affordances across parallel list components on the same screen.
* **Process Hardening**:
  - Recorded **ATT-2774** to reuse the existing Segment detail popup when tapping a segment item in `RouteSegmentsBreakdownSection`.
  - Added UI consistency check in design reviews: parallel list items (Climbs, Segments, POIs) within the same detail layout must provide symmetric drill-down affordances.

---

### 2.4 Action Item 4: Sensor Tile Name Wrapping & BLE Discovery (ATT-2479)
* **Observation**:
  - Sensor tiles in `RemoteDevices` expanded horizontally to accommodate long device names without wrapping, causing excessive white space gaps.
  - During sensor scanning, Bluetooth LE devices were not discovered on Pixel 10 (Android 16).
* **Process Hardening**:
  - Recorded **ATT-2772** to bound tile width and wrap sensor names onto up to 2 lines.
  - Recorded **ATT-2773** to perform root cause investigation into modern Android runtime permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`), scan filter settings, and `BANALService` discovery callbacks.

---

## 3. Sprint Metrics & Artifact Traceability
* **Sprint Name**: `2026-41.3`
* **Parent Sprint Epic**: ATT-355 / ATT-281 / ATT-66 / ATT-2582
* **Tickets Completed & Verified**: 18
* **Tickets Rejected / Bounced**: 1 ([ATT-2622](https://atrainingtracker.atlassian.net/browse/ATT-2622))
* **New Backlog Tickets Filed from Review**:
  - [ATT-2772](https://atrainingtracker.atlassian.net/browse/ATT-2772) (Rank #2 in Backlog)
  - [ATT-2773](https://atrainingtracker.atlassian.net/browse/ATT-2773) (Rank #3 in Backlog)
  - [ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774) (Rank #1 in Backlog)
* **Regression Test Status**: Full test suite (`./gradlew testDebugUnitTest`) clean (0 failures).
