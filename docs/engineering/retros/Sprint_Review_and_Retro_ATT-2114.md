# Sprint Review & Retrospective: Sprint 2026-40.13 (Release V4.9.38)

* **Ticket**: [ATT-2114](https://rainerblind.atlassian.net/browse/ATT-2114) (*Review & Retro*)
* **Sprint**: `2026-40.13`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.13` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 Preview, physical device `66020DLCR002FL`)
* **Date**: 2026-10-03

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.13**, 9 substantive engineering tickets were completed and verified through the agile ASPICE lifecycle. All 9 tickets completed Stage 1–5 workflows, passed independent Stage Gate audits (Agent 2), passed unit and clean-room full regression suites (`./gradlew testDebugUnitTest`), and were integrated continuously into `sprint/2026-40.13` via Strategy A.

In Ceremony 2 (Joint Review), the integrated build was deployed to the connected Google Pixel 10 physical hardware via `./gradlew installDebug` and evaluated with the human user in strict backlog rank order:

1. **[ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)** (*Prevent Duplicate Workouts from TCX File Imports*):
   - Resolved start timestamp jitter between TCX activity header and initial GPS trackpoint by introducing a resilient fuzzy deduplication heuristic ($\pm 120\text{s}$) with trackpoint count cross-validation.
   - Verified on physical Dropbox imports with zero duplicate insertions. **Approved (i.O.)** and transitioned to **Erledigt**.

2. **[ATT-2113](https://rainerblind.atlassian.net/browse/ATT-2113)** (*Align Scrubbing Telemetry Badge with Zoom Controls and Offset to the Right*):
   - Restructured `MapDetailLayout.kt` viewport into a unified `Box` container, anchoring `ScrubbingTelemetryBadge` to the top horizontal baseline of the zoom controls with an 8dp right offset to eliminate overlap.
   - Verified on-device across all scrub positions. **Approved (i.O.)** and transitioned to **Erledigt**.

3. **[ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)** (*Ensure Description and Extrema Cards Render in Detailed Workout View*):
   - Slotted `WorkoutDescription` and `WorkoutExtrema` into `metadataContent` in `TrackOnMapScreen.kt` above the map, matching the canonical section hierarchy of `WorkoutSummary.kt`.
   - Verified on-device with custom notes and multiple extrema rows. **Approved (i.O.)** and transitioned to **Erledigt**.

4. **[ATT-2129](https://rainerblind.atlassian.net/browse/ATT-2129)** (*Telemetry Graphs and Zone Cards in Workout Summary Do Not Open Workout Details on Click*):
   - Wired touch interaction and ripple semantics to `TelemetryMetricGraph` and zone distribution cards within `WorkoutSummary.kt`, seamlessly opening the detailed workout inspection screen.
   - Verified on-device with fluid navigation. **Approved (i.O.)** and transitioned to **Erledigt**.

5. **[ATT-2137](https://rainerblind.atlassian.net/browse/ATT-2137)** (*Strava Activity Data Not Displayed in Workout Details When Enabled in Advanced Settings*):
   - Routed `StravaActivitySection` into the upper metadata slot in `TrackOnMapScreen.kt`, honoring `activeDetailPrefs.showStrava`.
   - Verified on-device with synced Strava activities. **Approved (i.O.)** and transitioned to **Erledigt**.

6. **[ATT-2138](https://rainerblind.atlassian.net/browse/ATT-2138)** (*Suppress Zoom Controls When Neither Elevation Profile Nor Telemetry Graphs Are Displayed*):
   - Evaluated `hasZoomToolbar = showZoomControls && (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()`, recovering vertical screen estate when no zoomable charts are rendered.
   - Verified on-device with elevation and telemetry disabled. **Approved (i.O.)** and transitioned to **Erledigt**.

7. **[ATT-2147](https://rainerblind.atlassian.net/browse/ATT-2147)** (*Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode*):
   - Implemented ultra-slim marginal distribution strip (~28dp) adjacent to Heart Rate and Power telemetry curves, vertically aligned with zone thresholds, synchronously toggled between 5-Zones and Histogram modes via zone cards.
   - Verified on-device for density, legibility, and zero visual clutter. **Approved (i.O.)** and transitioned to **Erledigt**.

8. **[ATT-2150](https://rainerblind.atlassian.net/browse/ATT-2150)** (*Prevent Map Squashing by Large Upper Metadata in Workout Details*):
   - Implemented `CollapsingAppBarNestedScrollConnection` in `MapDetailLayout.kt`, allowing upper metadata (header, notes, extrema, Strava) to collapse upward smoothly on scroll gestures, while guaranteeing `MIN_MAP_HEIGHT` (120dp) in base state.
   - Verified on-device with lengthy notes; map expands to full screen height on scroll. **Approved (i.O.)** and transitioned to **Erledigt**.

9. **[ATT-2170](https://rainerblind.atlassian.net/browse/ATT-2170)** (*Place Lap Splits Between Extrema and Strava/Map in Detailed Workout View to Match Summary Order*):
   - Relocated `LapSplitVisualizerCard` from `analyticsContent` into `metadataContent` between `WorkoutExtrema` and `StravaActivitySection`, establishing 1:1 structural order parity between `WorkoutSummary.kt` and `TrackOnMapScreen.kt`.
   - Verified on-device; lap tapping continues to slice and highlight GPS segments on the map. **Approved (i.O.)** and transitioned to **Erledigt**.

---

### 1.1 Summary of Evaluated Tickets

| Ticket | Type | Summary | Traceability | Review Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)** | Bug | Prevent Duplicate Workouts from TCX File Imports | `REQ-DAT-020`, `TST-DAT-020` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2113](https://rainerblind.atlassian.net/browse/ATT-2113)** | Improvement | Align Scrubbing Telemetry Badge with Zoom Controls and Offset to Right | `REQ-UI-246`, `TST-UI-206` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)** | Bug | Ensure Description and Extrema Cards Render in Detailed Workout View | `REQ-UI-245`, `TST-UI-204` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2129](https://rainerblind.atlassian.net/browse/ATT-2129)** | Bug | Telemetry Graphs and Zone Cards in Workout Summary Open Details on Click | `REQ-UI-247`, `TST-UI-207` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2137](https://rainerblind.atlassian.net/browse/ATT-2137)** | Bug | Strava Activity Data Displayed in Workout Details When Enabled | `REQ-UI-248`, `TST-UI-205` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2138](https://rainerblind.atlassian.net/browse/ATT-2138)** | Bug | Suppress Zoom Controls When Neither Elevation Nor Telemetry Graphs Displayed | `REQ-UI-249`, `TST-UI-208` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2147](https://rainerblind.atlassian.net/browse/ATT-2147)** | Improvement | Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph | `REQ-UI-251`, `TST-UI-210` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2150](https://rainerblind.atlassian.net/browse/ATT-2150)** | Improvement | Prevent Map Squashing by Large Upper Metadata via Collapsing NestedScroll | `REQ-UI-250`, `TST-UI-209` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2170](https://rainerblind.atlassian.net/browse/ATT-2170)** | Improvement | Place Lap Splits Between Extrema and Strava/Map to Match Summary Order | `REQ-UI-252`, `TST-UI-211` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2114](https://rainerblind.atlassian.net/browse/ATT-2114)** | Feature | Sprint 2026-40.13: Review & Retro | `REQ-PRO-001` | Closed | **Erledigt** |

---

## 2. Retrospective: Observations, Root Causes & Countermeasures

### 2.1 Process Learning: Clean Ticket Summaries (Prohibition of Category & Epic Prefixes)
* **Observation**:
  During Sprint 2026-40.13, tickets were generated with prefixes like `[Bug] [Aftermath/Details] ...` or `[Verbesserung] [Aftermath/Layout] ...`. The human reviewer noted in Jira ticket `ATT-2114`:
  > *"The Ticket name should not contain the ticket category since this is redundant. The same holds for the epic. The Epic should also be not part of the ticket name."*
* **Root Cause**:
  AI prompt templates historically borrowed bracket tags (`[Bug]`, `[Feature]`, `[Improvement]`, `[EpicName]`) from command-line issue generation habits. However, in Jira, issue type and Epic link are first-class, structured attributes rendered natively by the UI. Prepending them into the summary text causes duplicate labels and clutters backlogs, agile boards, and git commit logs.
* **Countermeasure & Action Item (Rule 18)**:
  - Added **Rule 18** to `.agents/rules/aspice_governance.md`: Ticket summaries MUST NOT contain bracketed category prefixes (`[Bug]`, `[Feature]`, `[Verbesserung]`, `[Improvement]`) or Epic names (`[Aftermath]`, etc.).
  - Updated `tools/jira_util.py` (`create_issue`) to automatically strip leading category and epic bracket tags, enforcing clean titles programmatically.
  - Updated `brainstormer` and `sprint-planner` skills to generate clean, direct, descriptive summaries.

### 2.2 Process Learning: Canonical Section Hierarchy Parity
* **Observation**:
  Iterative feature additions to `WorkoutSummary.kt` and `TrackOnMapScreen.kt` over several sprints led to divergence in section ordering. While `WorkoutSummary.kt` evolved a natural visual narrative (Header -> Description -> Details -> Extrema -> Laps -> Strava -> Map -> Graphs -> Zones), `TrackOnMapScreen.kt` had description, extrema, laps, and Strava slotted at differing positions around the map.
* **Root Cause**:
  Early aftermath screens lacked the upper collapsible metadata slot, forcing all secondary content into `lowerColumn` below the map.
* **Countermeasure**:
  Sprint 2026-40.13 fully resolved this technical debt:
  1. `ATT-2112` moved Description & Extrema above the map.
  2. `ATT-2137` moved Strava above the map.
  3. `ATT-2150` made the upper metadata section smoothly collapsible via `CollapsingAppBarNestedScrollConnection`, eliminating map squashing.
  4. `ATT-2170` moved `LapSplitVisualizerCard` between Extrema and Strava.
  Both screens now share an identical, intuitive 1:1 section hierarchy.

### 2.3 Process Learning: Lean Defect Recording in Review Workflow
* **Observation**:
  When lap placement was evaluated in Ceremony 2, the team adhered strictly to the **Lean Defect Recording** rule (Rule 17): rather than hacking code during the review session, the feedback was captured cleanly in `ATT-2170`, taken through the full ASPICE pipeline, verified clean-room, deployed to Pixel 10, and accepted with zero regressions.

---

## 3. Sprint Closure Sign-Off & Verification Metrics

* **Continuous Integration**: `sprint/2026-40.13` contains all 9 verified tickets merged cleanly via `--no-ff`.
* **Automated Regression Suite**: 32 actionable Gradle test tasks passed (100% pass rate in clean-room build).
* **Governance Compliance**: All living docs (`docs/requirements.md`, `docs/tests.md`) maintained in verified state.
* **Hardware Validation**: Tested and approved on Google Pixel 10 physical hardware.
