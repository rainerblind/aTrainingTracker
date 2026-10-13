# Sprint Review & Retrospective: Sprint 2026-40.16

* **Ticket**: [ATT-2389](https://rainerblind.atlassian.net/browse/ATT-2389) (*Review & Retro*)
* **Sprint**: `2026-40.16`
* **Branch**: `sprint/2026-40.16` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16)
* **Date**: 2026-10-05

---

## 1. Executive Summary & Review Outcome

Sprint **2026-40.16** focused on route navigation (quick route selector, waypoints, Take Me Home, route grouping), permission / process-kill UX, cockpit tile styling and sensor/equipment data integrity.
The Joint Review was conducted one ticket at a time in backlog rank order on the integrated sprint build. Several tickets were functionally correct but **did not match the look and feel of the rest of the app**. This was the main topic of the retrospective.

### 1.1 Evaluated Tickets

| Ticket | Summary | Review Result | Status |
| :--- | :--- | :--- | :--- |
| **[ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)** | Modernize Permission Flow (Just-in-Time Prompts) | **Accepted (i.O.)** | `Erledigt` |
| **[ATT-2079](https://rainerblind.atlassian.net/browse/ATT-2079)** | Inform Athlete on Process Kill Reasons (ApplicationExitInfo) | **Postponed**: battery-kill escalation hard to reproduce on device | `Final Review` (carry-over) |
| **[ATT-2058](https://rainerblind.atlassian.net/browse/ATT-2058)** | Tile Rounded Corners & Spacing Variants | **Accepted (i.O.)**: variants too similar; follow-ups ATT-2456, ATT-2457 | `Erledigt` |
| **[ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)** | Google Drive Integration | **Rejected (n.i.O.)**: Google Sign-In status 10 (DEVELOPER_ERROR) on device | `Analysis` |
| **[ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)** | Quick Route Selector with GPS Proximity Sorting | **Accepted (i.O.)**: look & feel follow-ups ATT-2458, ATT-2459, ATT-2460 | `Erledigt` |
| **[ATT-58](https://rainerblind.atlassian.net/browse/ATT-58)** | Waypoints, POIs & TCX Course Points | **Accepted (i.O.)**: marker visuals follow-up ATT-2461 | `Erledigt` |
| **[ATT-1953](https://rainerblind.atlassian.net/browse/ATT-1953)** | "Take Me Home" Return Navigation & ETA HUD | **Accepted (i.O.)**: design alignment follow-up ATT-2462 | `Erledigt` |
| **[ATT-1954](https://rainerblind.atlassian.net/browse/ATT-1954)** | Corridor-Based Route Grouping & Thumbnail Zoom | **Closed, feature not wanted**: removal ticket ATT-2463 | `Erledigt` |
| **[ATT-2309](https://rainerblind.atlassian.net/browse/ATT-2309)** | CursorWindow IllegalStateException on Large Databases | **Accepted (i.O.)** | `Erledigt` |
| **[ATT-2382](https://rainerblind.atlassian.net/browse/ATT-2382)** | Sport-Specific Equipment Sensor Matrix | **Accepted (i.O.)** | `Erledigt` |
| **[ATT-1841](https://rainerblind.atlassian.net/browse/ATT-1841)** | High-Contrast Active Route Rendering | **Not concluded** | `Final Review` (carry-over) |
| **[ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450)** | Turn-by-Turn Navigation Cues | **Not concluded** | `Final Review` (carry-over) |
| **[ATT-1955](https://rainerblind.atlassian.net/browse/ATT-1955)** | In-Ride Fork-in-the-Road Route Selection | **Not concluded** | `Final Review` (carry-over) |
| **[ATT-2247](https://rainerblind.atlassian.net/browse/ATT-2247)** | Distance in Auto-Generated Workout Names | **Not concluded** | `Final Review` (carry-over) |
| **[ATT-2192](https://rainerblind.atlassian.net/browse/ATT-2192)** | Low Sensor Battery Notification & Snackbar | **Not concluded** | `Final Review` (carry-over) |

Tickets still in `Analysis` that weren't started (ATT-2383, 2385, 2386, 2387, 2388, 2360, 2357, 2337, 2338, 2340, 2341, 2346) carry over to the next sprint.

### 1.2 Follow-Up Backlog Tickets Created During Review

1. **[ATT-2456](https://rainerblind.atlassian.net/browse/ATT-2456)**: More distinct cockpit tile variants and selectable tile border colour (white/black)
2. **[ATT-2457](https://rainerblind.atlassian.net/browse/ATT-2457)**: Show tile borders in settings live preview and rename Cockpit & Typografie section
3. **[ATT-2458](https://rainerblind.atlassian.net/browse/ATT-2458)**: Show route selection button only on Control Tracking screen with app-consistent branding
4. **[ATT-2459](https://rainerblind.atlassian.net/browse/ATT-2459)**: Align route selector tabs with standard tabbed layout and clarify Heimweg placement
5. **[ATT-2460](https://rainerblind.atlassian.net/browse/ATT-2460)**: Limit route selector to routes within configurable radius (default 1 km), sorted by last ridden
6. **[ATT-2461](https://rainerblind.atlassian.net/browse/ATT-2461)**: Improve waypoint marker visuals using a standard POI icon package
7. **[ATT-2462](https://rainerblind.atlassian.net/browse/ATT-2462)**: Align Heimweg (Take Me Home) UI look and feel with the app design system
8. **[ATT-2463](https://rainerblind.atlassian.net/browse/ATT-2463)**: Remove corridor-based route grouping and differentiating thumbnail zoom (ATT-1954)

---

## 2. Retrospective: Observations, Root Causes & Process Hardening

### 2.1 Human Prerequisites Not Detected During Planning (ATT-1306)
* **Observation**: Google Sign-In failed on device with status 10 (DEVELOPER_ERROR). No OAuth client / SHA-1 fingerprint was registered in the Google Cloud Console.
* **Root Cause**: Gate 5 passed on mocked unit tests only. Nobody noticed during Sprint-Start Screening that the ticket depended on actions only the human can perform.
* **Process Hardening (Rule 22)**: Added **Rule 22** to `.agents/rules/aspice_governance.md` and a *Human Prerequisite Detection* step to Ceremony 1 in `.agents/skills/sprint-planner/SKILL.md`. Required human actions are now recorded in a `Human Prerequisites` section of the ticket before it moves to `Analysis`.

### 2.2 UI Design Consistency Gap (ATT-1835, ATT-1953, ATT-2058, ATT-58)
* **Observation**: Several features worked but did not look or feel like the rest of the app: custom tab visuals, branded floating entry points, ad-hoc marker shapes, and tile variants that were barely distinguishable.
* **Root Cause**: Neither the Stage 3 plan nor the Stage 5 walkthrough required a comparison with existing screens. `docs/design_guidelines.md` pointed to a non-existent `TTDimens` object and had no concrete baseline for spacing, shapes or color.
* **Process Hardening (Rule 23)**:
  - Added **Rule 23** ("Look Like the Rest of the App") to `.agents/rules/aspice_governance.md`.
  - Added Section 5 *Visual Consistency Baseline* to `docs/design_guidelines.md` (reuse before create, spacing scale, shapes, color, typography/icons, placement). Corrected the design-token reference.
  - Stage 3: new mandatory `UI Consistency` section in `plan_template.md` and `stage3-impl-plan/SKILL.md`.
  - Stage 5: mandatory side-by-side on-device screenshots (new UI vs. reference screen, Light/Dark/AMOLED) in `walkthrough_template.md` and `stage5-verification/SKILL.md`.

### 2.3 Feature Delivered That the PO Did Not Want (ATT-1954)
* **Observation**: Corridor-based route grouping was "absolutely not what I thought". The feature will be removed (ATT-2463).
* **Root Cause**: The ticket contained a detailed AI-authored specification that never matched the PO's idea. Sprint-Start Screening accepted the polished description without checking that it matched the PO's intent.
* **Countermeasure**: During Ceremony 1, the screening confirms the user's *intent* in their own words, not only that the description is complete. This is especially important for AI-authored specs.

### 2.4 Hard-to-Reproduce On-Device Verification (ATT-2079)
* **Observation**: Simulating a battery-saver kill (removing the whitelist entry + `run-as kill -9`) was confusing because the ATT-2075 prompt re-added the exemption between kills.
* **Countermeasure**: A debug/test hook to trigger the process-kill escalation should be considered when ATT-2079 is revisited.

### 2.5 Tooling & Workflow Notes
* The Jira workflow only allows `Final Review -> Zu erledigen -> Analysis` (two steps). The sprint-planner skill's single `move <KEY> analysis` instruction on rejection doesn't match this.
* `tools/jira_util.py` network calls are blocked by the default sandbox policy and need a sandbox bypass.

---

## 3. Sprint Closure

* **Governance Compliance**: Rules 1–23 in force; Rules 22 and 23 added in this retro.
* **Carry-Over**: ATT-2079, ATT-1841, ATT-1450, ATT-1955, ATT-2247 and ATT-2192 stay in `Final Review (Human)`. Their code is already integrated and is merged to `develop` with the sprint branch.
* **Sprint Branch Integration**: `sprint/2026-40.16` merged into `develop` (`--no-ff`) and deleted.
