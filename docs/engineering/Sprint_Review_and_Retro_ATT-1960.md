# Sprint Review & Retrospective: Sprint 2026-40.9 (Release V4.9.38)

* **Ticket**: [ATT-1960](https://atrainingtracker.atlassian.net/browse/ATT-1960) (*Review & Retro*)
* **Sprint**: `2026-40.9`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.9` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 15 / 16 Preview, physical device `66020DLCR002FL`)

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.9**, 5 feature, improvement, and bug tickets were processed through the rigorous 5-stage ASPICE engineering lifecycle. All 5 tickets were verified against formal requirements, passed automated module tests in Stage 4, passed the clean-room regression test suite (`./gradlew testDebugUnitTest`) in Stage 5, and were integrated into the sprint integration branch `sprint/2026-40.9` using Continuous Integration Strategy A.

In Ceremony 2 (Joint Review), all 5 tickets were evaluated strictly one-by-one in backlog rank order on physical Google Pixel 10 hardware with the human user:
* **4 tickets** were individually approved (i.O.) and transitioned to **Erledigt** by the human user ([ATT-1956](https://atrainingtracker.atlassian.net/browse/ATT-1956), [ATT-1957](https://atrainingtracker.atlassian.net/browse/ATT-1957), [ATT-1958](https://atrainingtracker.atlassian.net/browse/ATT-1958), [ATT-1959](https://atrainingtracker.atlassian.net/browse/ATT-1959)).
* **1 ticket** ([ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)) was evaluated on physical hardware: while noticeably improved, framing is still not as perfect as expected and was moved back to **Zu erledigen** for a dedicated live interactive tuning session.
* **"No Code Changes During Review" Mandate**: The invariant prohibiting code modifications during the review ceremony was strictly upheld. All user refinements and observations were immediately captured as dedicated, ranked backlog tickets for the next sprint ([ATT-1987](https://atrainingtracker.atlassian.net/browse/ATT-1987), [ATT-1988](https://atrainingtracker.atlassian.net/browse/ATT-1988)).

### 1.1 Summary of Evaluated Tickets

| Ticket | Type | Summary | Key Impact & Solution | Traceability | Review Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)** | Verbesserung | [UI/Sheets] Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets | Recalibrated bottom sheet peek baselines and dynamic navigation bar insets in `BottomSheetDesign.kt`; improved framing, but requires a live interactive tuning session on device. | `REQ-UI-221`, `TST-UI-175` | Live Iteration Needed | **Zu erledigen** |
| **[ATT-1956](https://atrainingtracker.atlassian.net/browse/ATT-1956)** | Verbesserung | [Aftermath/Graphs] Support Pan/Moving Gesture Across Speed, Heart Rate, and Power Graphs in Zoom Toolbar Pan Mode | Wired horizontal pan gestures in `TelemetryMetricGraph.kt` and lockstep viewport updates across `ElevationProfile` and stacked graphs in `MapDetailLayout.kt`. | `REQ-UI-232`, `TST-UI-186` | Approved (i.O.) | **Erledigt** |
| **[ATT-1957](https://atrainingtracker.atlassian.net/browse/ATT-1957)** | Verbesserung | [Settings/UI] Advanced Settings Accordion Subsections Should Be Initially Collapsed | Initialized `expandedSections` with `emptySet()` in `AdvancedTuningDialog.kt`, providing a compact, scannable overview of all 5 categories without initial scrolling. | `REQ-UI-222`, `TST-UI-187` | Approved (i.O.) | **Erledigt** |
| **[ATT-1958](https://atrainingtracker.atlassian.net/browse/ATT-1958)** | Bug | [Settings/Aftermath] Fix Lap Display Mode Selection Persistence in Expert Settings and Change Default to Visualizer Only | Set default lap display mode to `VISUALIZER_ONLY` in `MyPreferenceManager.kt` and eliminated state desynchronization upon reopening `AdvancedTuningDialog.kt` via non-null flow gating. | `REQ-UI-229`, `TST-UI-188` | Approved (i.O.) | **Erledigt** |
| **[ATT-1959](https://atrainingtracker.atlassian.net/browse/ATT-1959)** | Verbesserung | [Aftermath/Zones] Refine Zone Card Header Layout and Streamline Telemetry Histogram Readout | Decoupled mode toggle from header row in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` to eliminate duration wrapping; removed untranslated 'active bins' label in `TelemetryHistogramChart.kt`. | `REQ-UI-231`, `TST-UI-189` | Approved (i.O.) | **Erledigt** |

---

## 2. Retrospective: Analysis of Observations & Countermeasures

### 2.1 Observation 1: Strict Ceremony 2 Pre-Condition - Mandatory Device Build & Deployment ("Install Before Review")
* **Observation**:
  In accordance with Rule 16 established in Sprint 2026-40.8, the Sprint Review began with running `./gradlew installDebug` to deploy the unified `sprint/2026-40.9` binary onto physical hardware (`Pixel 10 - 17`, `66020DLCR002FL`).
* **Root Cause & Value**:
  Having the latest APK running on real hardware before discussing tickets enabled rapid, authentic validation of touch interactions, gesture responsiveness, and text rendering across real physical display dimensions.

### 2.2 Observation 2: Tooling & Transition Matching Hazard in `jira_util.py`
* **Observation**:
  During in-sprint transitions, executing `jira_util.py move <KEY> test` matched the negative transition `"Testing n.i.O."` (which transitions a ticket backwards to `Analysis`) before matching the target status `"Test"` (via `"Start Testing"`).
* **Root Cause**:
  `transition_issue` checked available transitions with a loose substring check combining `target_name` and `trans_name` simultaneously, allowing `"test" in "Testing n.i.O."` to match before examining all available target statuses.
* **Countermeasure & Permanent Hardening**:
  - Refactored `transition_issue` in `tools/jira_util.py` into a strict 4-pass evaluation:
    1. Pass 1: Exact match on target status name (`t["to"]["name"]`).
    2. Pass 2: Exact match on transition name (`t["name"]`).
    3. Pass 3: Substring match on target status name.
    4. Pass 4: Substring match on transition name, with an explicit guard excluding rejection transitions containing `"n.i.o."` unless `"n.i.o."` was explicitly requested.

### 2.3 Observation 3: Visual Baseline Calibration on Physical Form Factors (ATT-1645)
* **Observation**:
  While the recalibrated baseline tokens (`192.dp` for segments, `152.dp` for routes with descriptions) and dynamic navigation bar inset factoring prevented severe clipping, on-device inspection revealed that visual framing is still not as aesthetically pleasing or tactile as expected.
* **Root Cause**:
  Sheet peek framing depends on subjective visual harmony, thumb reachability, and exact header line heights that differ slightly between emulator layout inspectors and handheld devices.
* **Countermeasure**:
  - Ticket `ATT-1645` was moved back to `Zu erledigen` by the user.
  - A dedicated live interactive tuning session (using Compose `@Preview` or live hot reload cycles with real-time feedback) will be conducted to dial in exact pixel baselines.

### 2.4 Observation 4: Pan Gesture Sensitivity and Travel Distance (ATT-1956 / ATT-1987)
* **Observation**:
  During physical review of ATT-1956, the user noted that while horizontal panning in Pan Mode works properly, the graph moves much less than expected per touch/swipe gesture (pan travel distance feels restricted or sluggish).
* **Root Cause**:
  `ElevationProfileZoomMath.applyPan` calculates distance delta via:
  $$\text{distDelta} = \frac{\text{panDeltaX}}{\text{canvasWidth}} \times \text{visibleDist}$$
  When zoomed in moderately, $\text{visibleDist}$ is a fraction of the total route, meaning a standard finger swipe translates to a relatively small distance offset. The user intuitively expects a 1:1 screen finger drag or a touch velocity scaling factor.
* **Countermeasure**:
  - Strictly adhering to "No Code Changes During Review", created follow-up backlog ticket **[ATT-1987](https://atrainingtracker.atlassian.net/browse/ATT-1987)**: `[Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs`.
  - Ranked `ATT-1987` at the very top of the backlog.

### 2.5 Observation 5: User Simplification - Deprecation of Redundant 'Both' Lap Display Mode (ATT-1958 / ATT-1988)
* **Observation**:
  During review of the lap display mode preference, the user confirmed that persistence and the `VISUALIZER_ONLY` default work as intended, but observed that the `'Both'` option is redundant and clutters the UI.
* **Root Cause**:
  `BOTH` was originally added as a conservative fallback to preserve visual legacy behavior alongside the new split visualizer. In practice, athletes prefer either a modern graphical visualizer or a classic numeric table, but never both stacked simultaneously.
* **Countermeasure**:
  - Created follow-up backlog ticket **[ATT-1988](https://atrainingtracker.atlassian.net/browse/ATT-1988)**: `[Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings`.
  - Ranked `ATT-1988` at the top of the backlog directly behind `ATT-1987`.

### 2.6 Observation 6: Unattended Full-Sprint Autonomous Execution (Rule 14) & Strategy A
* **Observation**:
  Sprint 2026-40.9 was executed with 100% full autonomous progression across all 5 tickets without stopping for permission between tickets.
* **Result**:
  All 5 feature branches were branched from `sprint/2026-40.9`, verified with clean-room test runs, and merged cleanly with `--no-ff`. Zero merge conflicts occurred across shared files (`AdvancedTuningDialog.kt`, `docs/requirements.md`, `docs/tests.md`).

---

## 3. Summary of Backlog Follow-Up Tickets for Upcoming Sprints

| Key | Summary | Originating Ticket | Priority / Rank |
| :--- | :--- | :--- | :--- |
| **[ATT-1987](https://atrainingtracker.atlassian.net/browse/ATT-1987)** | [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs | ATT-1956 | Top of Backlog (Rank 1) |
| **[ATT-1988](https://atrainingtracker.atlassian.net/browse/ATT-1988)** | [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings | ATT-1958 | Top of Backlog (Rank 2) |
| **[ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)** | [UI/Sheets] Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets | ATT-1645 | Backlog (`Zu erledigen`, Live Tuning Session) |

---

## 4. Sprint Closure Sign-Off & Verification Metrics

* **Sprint Status**: Successfully Completed.
* **Total Tickets in Sprint**: 5 implementation tickets + 1 Review & Retro ticket.
* **Approved Tickets (Shipped to Release)**: 4 ([ATT-1956](https://atrainingtracker.atlassian.net/browse/ATT-1956), [ATT-1957](https://atrainingtracker.atlassian.net/browse/ATT-1957), [ATT-1958](https://atrainingtracker.atlassian.net/browse/ATT-1958), [ATT-1959](https://atrainingtracker.atlassian.net/browse/ATT-1959))
* **Bounced Back Tickets (Live Tuning in Backlog)**: 1 ([ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645))
* **Target Release Version**: `V4.9.38`
* **Clean-Room Test Pass Rate**: 100% (0 failures across all unit test suites).
