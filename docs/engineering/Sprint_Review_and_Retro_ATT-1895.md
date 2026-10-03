# Sprint Review & Retrospective: Sprint 2026-40.8 (Release V4.9.38)

* **Ticket**: [ATT-1895](https://atrainingtracker.atlassian.net/browse/ATT-1895) (*Review & Retro*)
* **Sprint**: `2026-40.8`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.8` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 15 / 16 Preview, physical device `66020DLCR002FL`)

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.8**, 11 core feature, improvement, and bug tickets were processed through the rigorous 5-stage ASPICE engineering lifecycle. All 11 tickets were verified against formal requirements, passed automated module tests in Stage 4, passed the clean-room regression test suite (`./gradlew testDebugUnitTest`) in Stage 5, and were merged into the sprint integration branch `sprint/2026-40.8` using Continuous Integration Strategy A.

In Ceremony 2 (Joint Review), all 11 tickets were evaluated strictly one-by-one in backlog rank order on physical Google Pixel 10 hardware with the human user:
* **10 tickets** were individually approved (i.O.) and transitioned to **Erledigt** by the human user.
* **1 ticket** ([ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)) was evaluated on physical hardware and bounced back to **Zu erledigen** due to navigation bar occlusion of bottom rows in bottom sheets.
* **"No Code Changes During Review" Mandate**: The invariant prohibiting code modifications during the review ceremony was strictly upheld. All user refinements and bug reports were immediately captured as dedicated, ranked backlog tickets for Sprint 2026-40.9 ([ATT-1956](https://atrainingtracker.atlassian.net/browse/ATT-1956), [ATT-1957](https://atrainingtracker.atlassian.net/browse/ATT-1957), [ATT-1958](https://atrainingtracker.atlassian.net/browse/ATT-1958), [ATT-1959](https://atrainingtracker.atlassian.net/browse/ATT-1959)).

### 1.1 Summary of Evaluated Tickets

| Ticket | Type | Summary | Key Impact & Solution | Traceability | Review Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)** | Verbesserung | [UI/Sheets] Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets | Calibrated bottom sheet peek baselines; physical Pixel 10 check revealed system navigation bar insets obscure the bottom text row; bounced back for inset-aware calibration. | `REQ-UI-221`, `TST-UI-175` | Revision Needed | **Zu erledigen** |
| **[ATT-1821](https://atrainingtracker.atlassian.net/browse/ATT-1821)** | Verbesserung | [Settings/UI] Structure Advanced Settings into Navigable / Collapsible Subsections | Consolidated 12 disparate settings into modular, animated accordion subsections with 0.dp tonal elevation and accessible touch targets. | `REQ-UI-222`, `TST-UI-176` | Approved (i.O.) | **Erledigt** |
| **[ATT-1890](https://atrainingtracker.atlassian.net/browse/ATT-1890)** | Feature | [Aftermath/Map] Interactive Draggable Splitter to Resize Map and Telemetry Viewports in MapDetailLayout | Added interactive split-pane divider with tactile pill handle, snap bounds (15%–85%), and responsive viewport resizing. | `REQ-UI-223`, `TST-UI-177` | Approved (i.O.) | **Erledigt** |
| **[ATT-1878](https://atrainingtracker.atlassian.net/browse/ATT-1878)** | Verbesserung | [Lieblingsorte] Calibrate Zoom Level on KnownLocationCard Map Thumbnail to Show Neighborhood Context | Calibrated dynamic zoom math ($z=15$ default, $z=14$ for $>200\text{m}$) and 200m circular geofence ring for clear neighborhood orientation. | `REQ-UI-224`, `TST-UI-178` | Approved (i.O.) | **Erledigt** |
| **[ATT-1876](https://atrainingtracker.atlassian.net/browse/ATT-1876)** | Verbesserung | [Aftermath/Graphs] Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs | Implemented sticky global zoom toolbar between map and telemetry stack with synchronized zoom-in, zoom-out, pan toggle, and reset. | `REQ-UI-225`, `TST-UI-179` | Approved (i.O.) | **Erledigt** |
| **[ATT-1872](https://atrainingtracker.atlassian.net/browse/ATT-1872)** | Bug | [Aftermath/Map] Resolve Gesture Conflict to Enable Smooth Vertical Scrolling of Graphs in MapDetailLayout | Resolved gesture competition using directional velocity disambiguation ($\theta > 30^\circ$ vertical angle unlocks container scroll). | `REQ-UI-226`, `TST-UI-180` | Approved (i.O.) | **Erledigt** |
| **[ATT-1871](https://atrainingtracker.atlassian.net/browse/ATT-1871)** | Verbesserung | [Cockpit/Typography] Expand Curated Font Selection with High-Performance Sports and HUD Fonts | Expanded curated cockpit fonts with 8 athletic, monospace, and HUD typography styles (Orbitron, Rajdhani, Chakra Petch, etc.). | `REQ-UI-227`, `TST-UI-181` | Approved (i.O.) | **Erledigt** |
| **[ATT-1869](https://atrainingtracker.atlassian.net/browse/ATT-1869)** | Verbesserung | [Aftermath/Splits] Lap Split Visualizer Refinements: 3-Lap Truncation, Custom Names, Pace Units, and Rabbit Icon | Refined split visualizer cards: 3-lap compact overview with expand toggle, custom workout names, athlete-friendly pace units, and leader rabbit badge. | `REQ-UI-228`, `TST-UI-182` | Approved (i.O.) | **Erledigt** |
| **[ATT-1870](https://atrainingtracker.atlassian.net/browse/ATT-1870)** | Feature | [Settings/Aftermath] Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings | Added SharedPreferences-backed display mode toggle (`TABLE_ONLY`, `VISUALIZER_ONLY`, `BOTH`) allowing athletes to customize aftermath lap display. | `REQ-UI-229`, `TST-UI-183` | Approved (i.O.) | **Erledigt** |
| **[ATT-1839](https://atrainingtracker.atlassian.net/browse/ATT-1839)** | Feature | [Aftermath/Graphs] Background Training Zone Bands & Right-Hand Zone Axis for Heart Rate and Power Graphs | Integrated semi-transparent zone background bands ($Z_1$–$Z_5$) and right-hand zone axis ($Z_1$–$Z_5$) on continuous telemetry metric graphs. | `REQ-UI-230`, `TST-UI-184` | Approved (i.O.) | **Erledigt** |
| **[ATT-1845](https://atrainingtracker.atlassian.net/browse/ATT-1845)** | Feature | [Aftermath/Zones] Fine-Grained Telemetry Frequency Histogram (BPM & Wattage Bins) with Zone Color Shading | Implemented fine-grained frequency histogram (bpm & wattage bins) with zone shading and seamless toggle between 5-zone view and histogram. | `REQ-UI-231`, `TST-UI-185` | Approved (i.O.) | **Erledigt** |

---

## 2. Retrospective: Analysis of Observations & Countermeasures

### 2.1 Observation 1: Mandatory On-Device Installation Before Sprint Review ("Install Before Review")
* **Observation**:
  Rainer Blind noted in ATT-1895: *"Sprint Review session must start with installing the latest sprint version on the phone."*
* **Root Cause**:
  In earlier review sessions, walkthrough discussions occasionally began while the physical device was still running a previous build or before the latest integrated sprint build was pushed. The reviewer cannot properly evaluate on-device behavior without the newest binary installed.
* **Countermeasure & Process Hardening**:
  - Added Step 1 to Ceremony 2 in `.agents/skills/sprint-planner/SKILL.md`: Before commencing ticket inspection, `./gradlew installDebug` MUST be executed to compile and deploy the sprint integration branch to the connected physical device.
  - Added Rule 16 to `.agents/rules/aspice_governance.md` and updated `docs/project_protocol.md` mandating that review sessions start with on-device installation.

### 2.2 Observation 2: System Navigation Bar Insets in Bottom Sheet Peek Baselines (ATT-1645)
* **Observation**:
  During on-device testing on the Google Pixel 10, the initial peek height for `SegmentsBottomSheet` cut off the altitude gain row, and `RouteDetailBottomSheet` obscured the route description behind the Android 15 gesture navigation bar.
* **Root Cause**:
  `BottomSheetDesign.kt` established static dp baseline tokens (e.g. 196.dp for Segments, 172.dp for Routes). While these fit comfortably on devices with traditional 3-button navigation, modern gesture navigation bars and edge-to-edge layouts consume bottom padding that was not dynamically factored into the sheet container's peek height calculation.
* **Countermeasure**:
  - The ticket `ATT-1645` was moved back to `Zu erledigen` by the user.
  - For the upcoming sprint, peek height formulas will either dynamically query `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` or increase baseline tokens by $+48\text{dp}$ to guarantee full clearance of the primary summary row above the navigation pill.

### 2.3 Observation 3: Zoom Toolbar Pan Mode Decoupled from Secondary Telemetry Graphs (ATT-1876 / ATT-1956)
* **Observation**:
  In ATT-1876, the sticky global zoom toolbar introduced a "Pan Mode" (Hand Icon). While pan gestures worked smoothly on the `ElevationProfile` graph, touching and dragging on the `Speed/Pace`, `Heart Rate`, or `Power` graphs did not pan the global horizontal viewport.
* **Root Cause**:
  `TelemetryMetricGraph.kt` only intercepted touch gestures for vertical scrubbing (scrubber line and tooltip). The pan mode drag gesture was wired exclusively to `ElevationProfileLayout.kt`.
* **Countermeasure**:
  - Strictly adhering to "No Code Changes During Review", follow-up ticket **[ATT-1956](https://atrainingtracker.atlassian.net/browse/ATT-1956)** was created and ranked at the top of the backlog.
  - In Sprint 2026-40.9, `TelemetryMetricGraph.kt` will consume horizontal drag deltas in pan mode and update the shared `TelemetryViewportState`.

### 2.4 Observation 4: Accordion Subsection Initial Collapse State in Settings (ATT-1821 / ATT-1871 / ATT-1957)
* **Observation**:
  When opening Advanced Settings, the "Cockpit-Typografie" accordion section was expanded by default. The user expected all accordion subsections to be initially collapsed so the user has an immediate, compact overview of all categories.
* **Root Cause**:
  In `AdvancedTuningAccordion.kt`, the initial expansion state was initialized to `true` for Cockpit Typography to highlight recent feature additions.
* **Countermeasure**:
  - Created follow-up backlog ticket **[ATT-1957](https://atrainingtracker.atlassian.net/browse/ATT-1957)**.
  - In Sprint 2026-40.9, all accordion sections will default to `isExpanded = false`.

### 2.5 Observation 5: Configurable Lap Mode Selection Persistence & Default Mode (ATT-1870 / ATT-1958)
* **Observation**:
  1. Default setting: User noted that the default lap display mode should be `VISUALIZER_ONLY` rather than `BOTH`.
  2. Dialog persistence: When changing the setting in Expert Settings and saving, the workout list reflected the change; however, reopening the Expert Settings dialog still showed the old chip selected.
* **Root Cause**:
  `LapDisplayMode` defaulted to `BOTH` in preference fallbacks. In the Compose settings dialog, the mutable state holding the selected radio chip was not re-initialized from SharedPreferences when the dialog recomposed or reopened.
* **Countermeasure**:
  - Created follow-up backlog ticket **[ATT-1958](https://atrainingtracker.atlassian.net/browse/ATT-1958)**.
  - In Sprint 2026-40.9, set default to `VISUALIZER_ONLY` and bind dialog state dynamically to the active preference.

### 2.6 Observation 6: Card Header Duration Squeeze & Histogram Metric Streamlining (ATT-1845 / ATT-1959)
* **Observation**:
  1. Header wrapping: On the Pixel 10 screen, placing the segmented button (`5 Zones` vs `Histogram`) inside the card header row squeezed the total duration text (`1:20:27`), causing it to wrap vertically into a single-character column.
  2. Readout clutter: The subtitle row contained a middle label (`43 active bins`) between the range (`84 - 182 bpm`) and bin width ($\Delta 2\text{ bpm}$) that added visual noise without high athlete utility.
* **Root Cause**:
  The card header used a single `Row` where `Text(totalDuration)` had `weight(1f)` without a minimum width constraint. The segmented button consumed majority horizontal width.
* **Countermeasure**:
  - Created follow-up backlog ticket **[ATT-1959](https://atrainingtracker.atlassian.net/browse/ATT-1959)**.
  - In Sprint 2026-40.9, refine header layout (compact icon toggle or stacked title) to protect duration text, and remove the middle `"43 active bins"` label.

### 2.7 Observation 7: Strategy A Continuous Sprint Integration: Zero Merge Conflicts Across 11 Complex Tickets
* **Observation**:
  11 tickets concurrently modified complex shared layouts (`MapDetailLayout`, `TelemetryMetricGraph`, `ElevationProfileLayout`, `AdvancedTuningAccordion`, `strings.xml`, `docs/requirements.md`, `docs/tests.md`).
* **Result**:
  **Zero merge conflicts** occurred during the entire sprint. Strategy A (merging each verified feature branch directly into `sprint/2026-40.8` upon passing Stage 5 clean-room regression) ensured that subsequent tickets always branched from the latest verified state.

### 2.8 Observation 8: Inviolable Enforcement of "No Code Changes During Review"
* **Observation**:
  During Ceremony 2, multiple UX refinements and minor bug observations arose across 4 tickets.
* **Result**:
  In accordance with ASPICE governance, **zero code modifications** were made to `sprint/2026-40.8` during the review ceremony. Every observation was transformed into a structured, ranked backlog ticket for Sprint 2026-40.9, keeping the release candidate stable, traceable, and ready for clean integration.

---

## 3. Summary of Backlog Follow-Up Tickets for Sprint 2026-40.9

| Key | Summary | Originating Ticket | Priority / Rank |
| :--- | :--- | :--- | :--- |
| **[ATT-1956](https://atrainingtracker.atlassian.net/browse/ATT-1956)** | [Aftermath/Graphs] Pan mode gesture should also pan viewport when touching Speed, HR, and Power graphs | ATT-1876 | Top of Backlog |
| **[ATT-1957](https://atrainingtracker.atlassian.net/browse/ATT-1957)** | [Settings/UI] Accordion subsections in Advanced Settings should open initially collapsed | ATT-1871 / ATT-1821 | Top of Backlog |
| **[ATT-1958](https://atrainingtracker.atlassian.net/browse/ATT-1958)** | [Settings/Aftermath] Lap display mode default to VISUALIZER_ONLY and fix persistence upon reopening dialog | ATT-1870 | Top of Backlog |
| **[ATT-1959](https://atrainingtracker.atlassian.net/browse/ATT-1959)** | [Aftermath/Zones] Refine Telemetry Histogram card header layout to prevent duration wrapping & remove 43 active bins label | ATT-1845 | Top of Backlog |

---

## 4. Sprint Closure Sign-Off & Verification Metrics

* **Sprint Status**: Successfully Completed.
* **Total Tickets in Sprint**: 11
* **Approved Tickets (Shipped to Release)**: 10
* **Bounced Back Tickets (Recalibration in Backlog)**: 1 ([ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645))
* **Target Release Version**: `V4.9.38`
* **Clean-Room Test Pass Rate**: 100% (0 failures across all unit test suites).
