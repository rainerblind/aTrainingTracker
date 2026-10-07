# Sprint Review & Retrospective: Sprint 2026-41.1

* **Ticket**: [ATT-2470](https://atrainingtracker.atlassian.net/browse/ATT-2470) (*Review & Retro*)
* **Sprint**: `2026-41.1`
* **Branch**: `sprint/2026-41.1` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-07

---

## 1. Executive Summary & Review Outcome

Sprint **2026-41.1** delivered major improvements across cloud integrations (Google Drive & Dropbox), WYSIWYG tracking configuration, permission flows, elevation profiles & map telemetry, cockpit tile styling, route waypoint icons, and sport-specific equipment sensor matrices.

In accordance with **Rule 14 (Autonomous Full-Sprint Execution Mandate)**, the AI agent pipeline operated continuously and autonomously across all committed sprint tickets, successfully bringing 100% of the planned scope through Stage 5 verification onto the unified sprint integration branch `sprint/2026-41.1`.

The **Joint Review (Ceremony 2)** was conducted on a physical Google Pixel 10 with the integrated sprint build deployed at session start (**Rule 16: Install Before Review**). All tickets were reviewed strictly one-by-one in backlog rank order (**Rule 8 & Rule 13**).

### 1.1 Evaluated Sprint Tickets

| Ticket | Summary | Review Result | Status |
| :--- | :--- | :--- | :--- |
| **[ATT-1306](https://atrainingtracker.atlassian.net/browse/ATT-1306)** | Google Drive integration for automated workout export and backup synchronization | **Accepted (i.O.)**: OAuth flow & sync working; follow-up defect filed for folder resolution | `Erledigt` |
| **[ATT-2247](https://atrainingtracker.atlassian.net/browse/ATT-2247)** | Append Distance to Auto-Generated Workout Names for Start-Location Sessions | **Accepted (i.O.)**: Distance correctly formatted and appended | `Erledigt` |
| **[ATT-2383](https://atrainingtracker.atlassian.net/browse/ATT-2383)** | Simplify Column Headers to List and Details in Workout Cards and Details Settings | **Accepted (i.O.)**: Clean two-column layout | `Erledigt` |
| **[ATT-2385](https://atrainingtracker.atlassian.net/browse/ATT-2385)** | Prevent Overlapping Distance Tick Labels on Elevation Profile X-Axis in Segment and Route Cards | **Accepted (i.O.)**: Dynamic step calculation prevents label collisions | `Erledigt` |
| **[ATT-2386](https://atrainingtracker.atlassian.net/browse/ATT-2386)** | Anchor Elevation Profile to Bottom Navigation Bar and Dynamically Expand Upper Map in Route and Segment Details | **Accepted (i.O.)**: Elevation profile anchored cleanly; map expanded | `Erledigt` |
| **[ATT-2387](https://atrainingtracker.atlassian.net/browse/ATT-2387)** | Map Origin Source Attribute in WorkoutDataMapper Batch fromCursor to Display Source Badges | **Accepted (i.O.)**: Origin source mapped symmetrically across batch cursors | `Erledigt` |
| **[ATT-2388](https://atrainingtracker.atlassian.net/browse/ATT-2388)** | Increase Visibility and Visual Prominence of Climbs on Routes | **Accepted (i.O.)**: Enhanced climb styling & category badges | `Erledigt` |
| **[ATT-2360](https://atrainingtracker.atlassian.net/browse/ATT-2360)** | Unified WYSIWYG tracking tab configuration with spatial overlays and per-tab popup toggles | **Accepted (i.O.)**: Follow-up ATT-2620 filed for lower section access | `Erledigt` |
| **[ATT-2357](https://atrainingtracker.atlassian.net/browse/ATT-2357)** | Require Precise Location permission and dynamically recheck permission state to instantiate GPS device | **Accepted (i.O.)**: Follow-up ATT-2621 filed for battery optimization prompt | `Erledigt` |
| **[ATT-2337](https://atrainingtracker.atlassian.net/browse/ATT-2337)** | Navigate to imported workout in workouts list upon successful import | **Accepted (i.O.)**: Follow-up ATT-2622 filed for file extension filtering | `Erledigt` |
| **[ATT-2338](https://atrainingtracker.atlassian.net/browse/ATT-2338)** | FIT workout import lacks elevation data due to missing enhanced altitude parsing | **Accepted (i.O.)**: Enhanced altitude parsed correctly from FIT records | `Erledigt` |
| **[ATT-2340](https://atrainingtracker.atlassian.net/browse/ATT-2340)** | Scrubber marker on map not visible when scrubbing speed or telemetry charts | **Accepted (i.O.)**: Crosshair scrubber synchronized on map | `Erledigt` |
| **[ATT-2346](https://atrainingtracker.atlassian.net/browse/ATT-2346)** | Scan Google Drive for historical workout files during bulk import | **Postponed**: Requires live cloud account setup with bulk files on device | `Final Review` (carry-over) |
| **[ATT-2341](https://atrainingtracker.atlassian.net/browse/ATT-2341)** | Scan Dropbox for FIT workout files during bulk recovery | **Accepted (i.O.)**: Follow-up ATT-2623 filed for format-specific block grouping | `Erledigt` |
| **[ATT-2456](https://atrainingtracker.atlassian.net/browse/ATT-2456)** | More distinct cockpit tile variants and selectable tile border colour (white/black) | **Accepted (i.O.)**: Follow-up ATT-2624 filed for grid spacing harmonization | `Erledigt` |
| **[ATT-2457](https://atrainingtracker.atlassian.net/browse/ATT-2457)** | Show tile borders in settings live preview and rename Cockpit & Typografie section | **Accepted (i.O.)**: Live preview reflects borders accurately | `Erledigt` |
| **[ATT-2189](https://atrainingtracker.atlassian.net/browse/ATT-2189)** | Relocate pairing buttons to sensor settings and optimize control tracking screen layout | **Accepted (i.O.)**: Follow-ups ATT-2625 and ATT-2626 filed | `Erledigt` |
| **[ATT-2458](https://atrainingtracker.atlassian.net/browse/ATT-2458)** | Show route selection button only on Control Tracking screen with app-consistent branding | **Accepted (i.O.)**: Route selection button properly contextualized | `Erledigt` |
| **[ATT-2459](https://atrainingtracker.atlassian.net/browse/ATT-2459)** | Remove tabs and Heimweg card from route selector in favor of clean proximity recency list | **Approved in discussion**: Left in review; follow-up ATT-2627 filed | `Final Review` |
| **[ATT-2460](https://atrainingtracker.atlassian.net/browse/ATT-2460)** | Limit route selector to routes within configurable radius (expert setting, default 1 km) sorted by last ridden | **Accepted (i.O.)**: Radius filtering and recency sort verified | `Erledigt` |
| **[ATT-2461](https://atrainingtracker.atlassian.net/browse/ATT-2461)** | Improve waypoint marker visuals using Maki POI icon package | **Accepted (i.O.)**: Follow-up ATT-2628 filed for marker badge size | `Erledigt` |
| **[ATT-2472](https://atrainingtracker.atlassian.net/browse/ATT-2472)** | Select one of the favorite locations as home-base for return navigation | **Accepted (i.O.)**: Follow-up ATT-2629 filed for fallback logic and badge | `Erledigt` |
| **[ATT-2462](https://atrainingtracker.atlassian.net/browse/ATT-2462)** | Align Heimweg (Take Me Home) UI look and feel with the app design system | **Postponed**: Deferred for outdoor/on-bike verification | `Final Review` (carry-over) |
| **[ATT-2463](https://atrainingtracker.atlassian.net/browse/ATT-2463)** | Remove corridor-based route grouping and differentiating thumbnail zoom (ATT-1954) | **Accepted (i.O.)**: Corridor grouping cleanly excised | `Erledigt` |
| **[ATT-2464](https://atrainingtracker.atlassian.net/browse/ATT-2464)** | Order equipment sensor matrix columns with sport-specific sensors first, then shared sensors | **Accepted (i.O.)**: Column ordering matches natural athlete workflow | `Erledigt` |
| **[ATT-2465](https://atrainingtracker.atlassian.net/browse/ATT-2465)** | Move bike and shoe sensor matrices into the Sensors view as two additional tabs | **Accepted (i.O.)**: Follow-up ATT-2630 filed for icon tint harmonization | `Erledigt` |
| **[ATT-2384](https://atrainingtracker.atlassian.net/browse/ATT-2384)** | Define and Harmonize Default Section Visibility and Order for Workout Cards and Details | **Accepted (i.O.)**: Section visibility and order harmonized | `Erledigt` |

*(Carry-overs from earlier sprints ATT-1841, ATT-1450, ATT-1955, ATT-2192, and ATT-2079, along with simulator tickets ATT-2467, ATT-2468, ATT-2469, remain available in their respective statuses).*

---

### 1.2 Follow-Up Backlog Tickets Created During Review (Rule 17: Lean Defect Recording)

In strict compliance with **Rule 10 (Zero Code Changes During Review)** and **Rule 17 (Lean Defect Recording)**, no code was altered during Ceremony 2. All 12 observations were captured immediately as structured Jira tickets with full user context and evidence, and ranked at the top of the backlog:

1. **[ATT-2620](https://atrainingtracker.atlassian.net/browse/ATT-2620)**: *Tracking tab configuration mode lower section not accessible or visible* (Parent: ATT-355; screenshot `ATT-2360_review_screenshot.png` attached).
2. **[ATT-2621](https://atrainingtracker.atlassian.net/browse/ATT-2621)**: *Background energy / battery optimization permission not prompted when revoked* (Parent: ATT-166).
3. **[ATT-2622](https://atrainingtracker.atlassian.net/browse/ATT-2622)**: *Filter file picker dialogs to matching workout file extensions for FIT, TCX, and GPX import* (Parent: ATT-281).
4. **[ATT-2623](https://atrainingtracker.atlassian.net/browse/ATT-2623)**: *Structure Import tab into format-specific blocks (FIT, TCX, GPX) each offering Local, Dropbox, and Google Drive options* (Parent: ATT-281).
5. **[ATT-2624](https://atrainingtracker.atlassian.net/browse/ATT-2624)**: *Harmonize cockpit tile grid spacing between preview and tracking screen and refine thick border ergonomics* (Parent: ATT-355; screenshot `ATT-2456_review_screenshot.png` attached).
6. **[ATT-2625](https://atrainingtracker.atlassian.net/browse/ATT-2625)**: *Remove 'Alle' button from device type selection dialog in sensor pairing flow* (Parent: ATT-355).
7. **[ATT-2626](https://atrainingtracker.atlassian.net/browse/ATT-2626)**: *Keep all device types visible in Sensors view during pairing instead of filtering to selected device type* (Parent: ATT-355).
8. **[ATT-2627](https://atrainingtracker.atlassian.net/browse/ATT-2627)**: *Context-aware route selection: proximity routes before start, active route detection during tracking, and Take Me Home integration* (Parent: ATT-2564).
9. **[ATT-2628](https://atrainingtracker.atlassian.net/browse/ATT-2628)**: *Reduce route waypoint marker badge size for improved map glanceability* (Parent: ATT-66).
10. **[ATT-2629](https://atrainingtracker.atlassian.net/browse/ATT-2629)**: *Remove redundant Heim-Basis badge and default to location with most starts when no home-base set* (Parent: ATT-2564).
11. **[ATT-2630](https://atrainingtracker.atlassian.net/browse/ATT-2630)**: *Unify bike and shoe icon tinting in equipment sensor matrix* (Parent: ATT-355).
12. **[ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)**: *Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy* (Parent: ATT-162; screenshot `ATT-1306_drive_error_screenshot.png` attached).

---

## 2. Retrospective: Observations, Root Causes & Process Hardening

### 2.1 Elevating "Obey the Rules" to Rule #1 & Full-Sprint Execution Adherence
* **Observation**: In Sprint 2026-40.16, agents stopped prematurely midway through the sprint. During this sprint's retro, the Product Owner (Rainer Blind) reiterated:
  > *"Agents did not finish the entire sprint. => Rule # 1: Obey the rules. Before starting a new ticket, the rules and protocols must be reread. I want this to become the rule number one. :)"*
* **Process Hardening (Rule 1)**: Elevated **Rule 1: Obey the Rules (Mandatory Rules, Protocols & Skills Refresh at Every Ticket Start)** to the supreme position in `.agents/rules/aspice_governance.md` and `docs/project_protocol.md`.
  * Whenever starting any new ticket or stage transition, agents MUST explicitly reread `.agents/rules/aspice_governance.md`, `docs/project_protocol.md`, and the active stage skill using `view_file`.
  * This refresh permanently prevents context loss across compactions and guarantees that agents execute the entire sprint backlog autonomously to 100% completion without halting (Rule 15).
* **Sprint 2026-41.1 Evaluation**: In this sprint, the autonomous pipeline strictly honored the mandate: all 20 committed tickets were autonomously driven to Stage 5 verification and integrated into `sprint/2026-41.1` without stopping the console.

### 2.2 Unnecessary Diagnostic Build Invocations (ATT-1306 Comment 1)
* **Observation**: During Stage 1 Analysis for ATT-1306, the agent ran `./gradlew signingReport` to extract the keystore SHA-1 fingerprint, which took unnecessary time and build cache overhead. The exact SHA-1 fingerprint had already been documented in the previous Sprint Retrospective comment and ticket history.
* **Root Cause**: Stage 1 agents reflexively invoke Gradle diagnostic tasks rather than querying existing project knowledge, git history, or Jira comments.
* **Process Hardening (Rule 24)**: Added **Rule 24: Avoid Redundant Diagnostic Builds ("Check Prior Knowledge & Ticket History First")** to `.agents/rules/aspice_governance.md` and updated `stage1-analysis/SKILL.md`. Before running expensive Gradle diagnostics, agents must inspect ticket history and documented project configuration.

### 2.3 Lean Defect Recording with On-Device Evidence (Rules 11, 17, and New Rule 25)
* **Observation**: During Ceremony 2 on Pixel 10, the user observed several UI inconsistencies and edge cases (e.g. Google Drive folder error, tracking configuration scroll bounds, tile grid spacing).
* **Success**: The agent strictly preserved the code freeze (Rule 11) and captured defects immediately into Jira.
* **Enhancement (Rule 25)**: Taking direct on-device screenshots via `adb exec-out screencap -p` and uploading them as Jira attachments (as demonstrated in ATT-2620, ATT-2624, and ATT-2631) provides invaluable visual ground truth for subsequent Stage 1 analysis. Added **Rule 25: Lean Defect Recording Protocol with Immediate Evidence Capture** to formalize this practice.

---

## 3. Governance Updates

1. **Rule 1 Promoted in `.agents/rules/aspice_governance.md`**: "Obey the Rules: Mandatory Rules, Protocols & Skills Refresh at Every Ticket Start" is now Rule #1. Subsequent rules 2–15 renumbered cleanly.
2. **Rule 24 Added to `.agents/rules/aspice_governance.md`**: Avoid Redundant Diagnostic Builds.
3. **Rule 25 Added to `.agents/rules/aspice_governance.md`**: Lean Defect Recording Protocol with Immediate Evidence Capture.
4. **`docs/project_protocol.md` Synchronized**: Updated Section 2 with Rule 1 ("Obey the Rules"), Rule 24, and Rule 25.
5. **`stage1-analysis/SKILL.md` Synchronized**: Mandatory preliminary check of ticket history and git logs before executing Gradle diagnostic tasks.
6. **`docs/design_guidelines.md` Extended**: Added Section 5.7 *In-Ride Navigation Cues & HUD Overlays* specifying top-level spatial placement, semi-transparency for telemetry glanceability, green domain border accents, and transient 3–5 second auto-dismissal.

---

## 4. Sprint Closure Procedure

1. Retrospective deliverable and governance updates committed to `sprint/2026-41.1`.
2. Transition `ATT-2470` to `Erledigt` with human confirmation.
3. Merge `sprint/2026-41.1` into `develop` (`--no-ff`), push `develop`, and delete local sprint branch `sprint/2026-41.1`.
