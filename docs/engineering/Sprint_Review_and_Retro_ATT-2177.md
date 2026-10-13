# Sprint Review & Retrospective: Sprint 2026-40.14 (Release V4.9.39)

* **Ticket**: [ATT-2177](https://rainerblind.atlassian.net/browse/ATT-2177) (*Review & Retro*)
* **Sprint**: `2026-40.14`
* **Target Release Version**: `V4.9.39`
* **Branch**: `sprint/2026-40.14` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 Preview, physical device `66020DLCR002FL`)
* **Date**: 2026-10-04

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.14**, an unprecedented volume of 21 substantive engineering tickets was constructed, verified across the agile ASPICE lifecycle (Stages 1–5), audited through automated stage gates, and integrated continuously into `sprint/2026-40.14` via Strategy A.

In Ceremony 2 (Joint Review), the complete integrated build was deployed to the connected Google Pixel 10 physical hardware via `./gradlew installDebug` and evaluated with the human user in strict backlog rank order (`ORDER BY rank ASC`), strictly one ticket at a time.

### 1.1 Summary of Evaluated Tickets (21 Tickets)

| Ticket | Type | Summary | Traceability | Review Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-2186](https://rainerblind.atlassian.net/browse/ATT-2186)** | Improvement | Add Origin Source Attribute to Workouts | `REQ-DAT-021`, `TST-DAT-021` | **Revision needed**: Missing UI header badge for foreign/imported workouts. | `Zu erledigen` |
| **[ATT-2178](https://rainerblind.atlassian.net/browse/ATT-2178)** | Improvement | Ensure Smooth Scrolling in Workout Details | `REQ-UI-253`, `TST-UI-212` | **Approved (i.O.)**: Fling and scroll fluid. Follow-up bug ATT-2304 filed. | `Erledigt` |
| **[ATT-2176](https://rainerblind.atlassian.net/browse/ATT-2176)** | Improvement | Section Reordering & Modular Architecture | `REQ-UI-254`, `TST-UI-213` | **Approved (i.O.)**: Dynamic ordering functional. Follow-up ATT-2305 filed. | `Erledigt` |
| **[ATT-2126](https://rainerblind.atlassian.net/browse/ATT-2126)** | Improvement | Equipment-to-Sensor Mapping Matrix | `REQ-DAT-022`, `TST-DAT-022` | **Approved (i.O.)**: Matrix mapping verified. Follow-ups ATT-2306 & ATT-2307 filed. | `Erledigt` |
| **[ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)** | Improvement | Modernize Permission Flow | `REQ-SYS-008`, `TST-SYS-008` | **Revision needed**: Missing background location ("Immer zulassen") & battery exemption. | `Zu erledigen` |
| **[ATT-2079](https://rainerblind.atlassian.net/browse/ATT-2079)** | Improvement | Inform Athlete on Process Kill Reasons via ApplicationExitInfo | `REQ-SYS-009`, `TST-SYS-009` | **Revision needed**: Dynamic title cause, tone adjustment, counter progression. | `Analysis` |
| **[ATT-2059](https://rainerblind.atlassian.net/browse/ATT-2059)** | Improvement | Reduce Card Spacing and Compact Inactive Items on Equipment Screen | `REQ-UI-255`, `TST-UI-214` | **Approved (i.O.)**: Clean compact card layout. | `Erledigt` |
| **[ATT-2058](https://rainerblind.atlassian.net/browse/ATT-2058)** | Task | Visual Prototyping: Rounded Corners & Tile Spacing Variants | `REQ-UI-256`, `TST-UI-215` | **Revision needed**: Runtime selectable setting in Expert Settings requested. | `Zu erledigen` |
| **[ATT-2057](https://rainerblind.atlassian.net/browse/ATT-2057)** | Bug | Hide Research Button When No Real Paired Remote Devices Exist | `REQ-UI-257`, `TST-UI-216` | **Approved (i.O.)**: Research button cleanly hidden when appropriate. | `Erledigt` |
| **[ATT-2031](https://rainerblind.atlassian.net/browse/ATT-2031)** | Improvement | Centralize Zone Threshold Resolution & Optimize Scrubbing to O(log N) | `REQ-DAT-023`, `TST-DAT-023` | **Approved (i.O.)**: Scrubbing instantaneous. Follow-up bug ATT-2309 filed. | `Erledigt` |
| **[ATT-2032](https://rainerblind.atlassian.net/browse/ATT-2032)** | Improvement | Unify X-Axis Domain & Time-vs-Distance Calculation Across Telemetry | `REQ-UI-258`, `TST-UI-217` | **Approved (i.O.)**: X-axis calculations fully unified. | `Erledigt` |
| **[ATT-2033](https://rainerblind.atlassian.net/browse/ATT-2033)** | Improvement | Modularize AdvancedTuningDialog into Focused Category Composables | `REQ-UI-259`, `TST-UI-218` | **Approved (i.O.)**: Dialog modularized and responsive. | `Erledigt` |
| **[ATT-1828](https://rainerblind.atlassian.net/browse/ATT-1828)** | Feature | FIT Workout Importer with Duplicate Detection and Sport Mapping | `REQ-DAT-024`, `TST-DAT-024` | **Postponed**: Retained in Final Review for in-depth file testing. | `Final Review` |
| **[ATT-1827](https://rainerblind.atlassian.net/browse/ATT-1827)** | Feature | Full-Telemetry FIT Workout Exporter via Garmin FIT SDK | `REQ-DAT-025`, `TST-DAT-025` | **Postponed**: Dropbox export verified. Follow-up ATT-2310 filed. | `Final Review` |
| **[ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)** | Feature | Google Drive Integration for Automated Workout Export & Backup | `REQ-CLD-001`, `TST-CLD-001` | **Revision needed**: Dummy token accepted; real OAuth required. | `Zu erledigen` |
| **[ATT-1502](https://rainerblind.atlassian.net/browse/ATT-1502)** | Improvement | Automatic DEM Elevation Enrichment for Imported GPX Routes | `REQ-DAT-026`, `TST-DAT-026` | **Approved (i.O.)**: DEM enrichment verified. Follow-up ATT-2311 filed. | `Erledigt` |
| **[ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)** | Feature | Quick Route Selector from Cockpit with GPS Proximity Sorting | `REQ-MAP-024`, `TST-MAP-026` | **Postponed**: Retained in Final Review for physical outdoor testing. | `Final Review` |
| **[ATT-1841](https://rainerblind.atlassian.net/browse/ATT-1841)** | Improvement | Prominent High-Contrast Rendering for Actively Navigated Routes | `REQ-MAP-023`, `TST-MAP-025` | **Postponed**: Retained in Final Review for outdoor contrast testing. | `Final Review` |
| **[ATT-58](https://rainerblind.atlassian.net/browse/ATT-58)** | Feature | Support Waypoints, POIs and TCX Course Points | `REQ-MAP-026`, `TST-MAP-028` | **Postponed**: Retained in Final Review for outdoor navigation testing. | `Final Review` |
| **[ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281)** | Feature | Persistent Climbs Database & Live ClimbPro Cockpit Sheet | `REQ-MAP-027`, `TST-MAP-029` | **Postponed**: Retained in Final Review for live ascent riding. | `Final Review` |
| **[ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450)** | Feature | Turn-by-Turn Navigation Cues | `REQ-MAP-028`, `TST-MAP-030` | **Postponed**: Retained in Final Review for live navigation riding. | `Final Review` |
| **[ATT-2177](https://rainerblind.atlassian.net/browse/ATT-2177)** | Feature | Review & Retro: Sprint 2026-40.14 | `REQ-PRO-001` | Closed via Retrospective | `Erledigt` |

---

### 1.2 Follow-Up Backlog Tickets Created During Review

In strict accordance with Rule 10 (*"No code changes during Sprint Review"*) and Rule 17 (*"Lean Defect Recording"*), all user observations and refinement requests were captured immediately into dedicated Jira tickets and ranked at the top of the backlog:

1. **[ATT-2303](https://rainerblind.atlassian.net/browse/ATT-2303)**: Filter Workouts by Origin Source Attribute
2. **[ATT-2304](https://rainerblind.atlassian.net/browse/ATT-2304)**: Restore Immediate Collapsing Header Expansion When Scrolling Down
3. **[ATT-2305](https://rainerblind.atlassian.net/browse/ATT-2305)**: Optimize Layout, Text Wrapping and Reordering Controls
4. **[ATT-2306](https://rainerblind.atlassian.net/browse/ATT-2306)**: Optimize Visual Design, Column Alignment, and Spacing in Equipment Matrix
5. **[ATT-2307](https://rainerblind.atlassian.net/browse/ATT-2307)**: Stale Sensor Settings Equipment Mapping Due to Missing Reactive Synchronization
6. **[ATT-2309](https://rainerblind.atlassian.net/browse/ATT-2309)**: CursorWindow IllegalStateException in `WorkoutRepository.loadAllWorkouts` on Large Databases (>1600 Items)
7. **[ATT-2310](https://rainerblind.atlassian.net/browse/ATT-2310)**: Include FIT Format in Detailed Export Report, Export Status Tracking and Workout Header Export Menu
8. **[ATT-2311](https://rainerblind.atlassian.net/browse/ATT-2311)**: Position Elevation Profile Below Map and Enable Interactive Zooming in Routes and Segments

---

## 2. Retrospective: Observations, Root Causes & Countermeasures

### 2.1 Process Learning: Timing of Fix Version Assignment ("Add Version When Ticket is Finished, Not When Started")
* **Observation**:
  In Jira ticket `ATT-2177`, the user provided clear operational feedback:
  > *"Add version when ticket is finished, not when started."*
* **Root Cause Analysis**:
  AI agents and automated tooling previously set or suggested `fixVersions` (e.g. `V4.9.39`) at ticket creation or during early engineering stages (Stage 1 / Stage 2). 
  This practice introduces serious governance and release-tracking anomalies:
  1. *Premature Commitment*: If a ticket is rejected during review (e.g. ATT-2186, ATT-2075, ATT-1306) or postponed for in-depth outdoor testing across sprints (e.g. ATT-1828, ATT-1835, ATT-1281), having a fixed release version already attached distorts release notes and audit reports.
  2. *Stale Metadata*: Tickets bounced back to `Zu erledigen` or `Analysis` retain a release version belonging to a sprint that is about to close.
* **Countermeasure & Action Item (Codified as Rule 19)**:
  - Added **Rule 19** to `.agents/rules/aspice_governance.md`: Parent tickets MUST ONLY receive a `Lösungsversion` (Fix Version/s) when they are **FINISHED** (accepted during review / transitioned to `Erledigt` or ready for release), NEVER when started, created, or in progress.
  - Updated `.agents/skills/jira-workflow/SKILL.md` to mandate that `set-fixversion` is executed exclusively at completion time.
  - Updated `create-issue` guidelines to ensure new tickets are born without a premature `fixVersions` tag.

### 2.2 Process Learning: Lean Defect Recording & Preserving Review Momentum
* **Observation**:
  Sprint 2026-40.14 had 21 tickets—the largest review load to date. During the walkthrough, several non-trivial findings surfaced (such as a database `CursorWindow` crash with 1600+ workouts during scrubbing review, and layout issues in route elevation profiles).
* **Root Cause & Value Delivered**:
  Attempting in-session ad-hoc debugging would have completely stalled the ceremony. Adhering rigorously to Rule 10 and Rule 17 allowed the team to capture 8 high-fidelity follow-up tickets (ATT-2303 to ATT-2311) with exact user context, screenshots, and logs, while maintaining high review velocity and keeping the sprint branch 100% stable.

### 2.3 Process Learning: Dynamic Error Context & Tone Governance (ATT-2079)
* **Observation**:
  The crash diagnosis dialog for Android `ApplicationExitInfo` was rejected because the dialog title was static, the tone was perceived as sarcastic rather than helpful, and the escalation counter advanced too quickly to iteration 3.
* **Countermeasure**:
  User-facing system dialogs must always present dynamic, actionable root causes directly in the header, maintain an objective, respectful engineering tone, and strictly base escalation counters on real repeated occurrence thresholds.

### 2.4 Process Learning: Genuine Platform Authentication vs. Fallback Mock Tokens (ATT-1306)
* **Observation**:
  The Google Drive configuration dialog accepted arbitrary text for email and an empty auth token, falling back to a dummy string and marking the cloud service as connected.
* **Countermeasure**:
  Cloud service integrations must never simulate successful authentication with mock tokens in production dialogs. If authentication is not completed via genuine Google Sign-In / Credential Manager OAuth, the configuration must fail visibly and guide the user through the standard authorization flow.

---

## 3. Sprint Closure Sign-Off & Verification Metrics

* **Continuous Integration**: `sprint/2026-40.14` contains all verified and accepted tickets merged cleanly via `--no-ff`.
* **Automated Regression Suite**: 100% test pass rate across all modules in clean-room build (`./gradlew testDebugUnitTest`).
* **Governance Compliance**: All living docs (`docs/requirements.md`, `docs/tests.md`) maintained in verified state.
* **Hardware Validation**: Tested and audited on Google Pixel 10 physical hardware (`66020DLCR002FL`).
