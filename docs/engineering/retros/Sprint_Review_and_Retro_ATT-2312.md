# Sprint Review & Retrospective: Sprint 2026-40.15 (Release V4.9.39)

* **Ticket**: [ATT-2312](https://rainerblind.atlassian.net/browse/ATT-2312) (*Review & Retro*)
* **Sprint**: `2026-40.15`
* **Target Release Version**: `V4.9.39`
* **Branch**: `sprint/2026-40.15` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16, physical device `66020DLCR002FL`)
* **Date**: 2026-10-04

---

## 1. Executive Summary & Shipped Scope

Sprint **2026-40.15** brought together a high-volume sprint closure consolidating 18 reviewed tickets across telemetry, routes, climbs, exports, and sensor management.
All integrated changes were built on `sprint/2026-40.15` and deployed to the attached Google Pixel 10 hardware via `./gradlew installDebug`. In Ceremony 2 (Joint Review), tickets were evaluated one-by-one in strict backlog rank order (`ORDER BY rank ASC`).

### 1.1 Summary of Evaluated Tickets (18 Tickets)

| Ticket | Type | Summary | Traceability | Review Result | Status | Lösungsversion |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-2224](https://rainerblind.atlassian.net/browse/ATT-2224)** | Improvement | Robust BLE Sensor Discovery & Queue Deadlock Prevention | `REQ-SEN-010`, `TST-SEN-010` | **Approved (i.O.)**: BLE Power Simulator verified on device without GATT deadlock. | `Erledigt` | `V4.9.39` |
| **[ATT-2307](https://rainerblind.atlassian.net/browse/ATT-2307)** | Bug | Stale Sensor Settings Equipment Mapping Sync | `REQ-SEN-012`, `TST-SEN-012` | **Approved (i.O.)**: Reactive sync with EquipmentDbHelper verified. | `Erledigt` | `V4.9.39` |
| **[ATT-2306](https://rainerblind.atlassian.net/browse/ATT-2306)** | Improvement | Sensor Matrix UI Spacing & Column Alignment | `REQ-UI-272`, `TST-UI-231` | **Approved (i.O.)**: Clean visual alignment. Follow-up ATT-2382 filed. | `Erledigt` | `V4.9.39` |
| **[ATT-2305](https://rainerblind.atlassian.net/browse/ATT-2305)** | Improvement | Workout Cards & Details Reordering & Spacing | `REQ-UI-271`, `TST-UI-230` | **Approved (i.O.)**: Dynamic reordering verified. Follow-ups ATT-2383 & ATT-2384 filed. | `Erledigt` | `V4.9.39` |
| **[ATT-2303](https://rainerblind.atlassian.net/browse/ATT-2303)** | Improvement | Filter Workouts by Origin Source Attribute | `REQ-DAT-029`, `TST-DAT-029` | **Approved (i.O.)**: Filtering by TRACKED/TCX/GPX/FIT functional. | `Erledigt` | `V4.9.39` |
| **[ATT-2304](https://rainerblind.atlassian.net/browse/ATT-2304)** | Bug | Restore Immediate Collapsing Header Expansion on Scroll Down | `REQ-UI-273`, `TST-UI-232` | **Approved (i.O.)**: QuickReturn fluid. Follow-up bug ATT-2385 filed. | `Erledigt` | `V4.9.39` |
| **[ATT-2311](https://rainerblind.atlassian.net/browse/ATT-2311)** | Improvement | Elevation Profile Below Map & Interactive Zoom | `REQ-MAP-031`, `TST-MAP-032` | **Approved (i.O.)**: Zoom and lower profile functional. Follow-up ATT-2386 filed. | `Erledigt` | `V4.9.39` |
| **[ATT-2310](https://rainerblind.atlassian.net/browse/ATT-2310)** | Improvement | FIT Format in Export Status & Workout Header Menu | `REQ-EXP-018`, `TST-EXP-015` | **Approved (i.O.)**: FIT export tracked in status report and 3-dots menu. | `Erledigt` | `V4.9.39` |
| **[ATT-2194](https://rainerblind.atlassian.net/browse/ATT-2194)** | Bug | Sensor Connection Status in SensorSourceDialog | `REQ-UI-268`, `TST-UI-227` | **Approved (i.O.)**: Active sources correctly display `(verfügbar)`. | `Erledigt` | `V4.9.39` |
| **[ATT-2195](https://rainerblind.atlassian.net/browse/ATT-2195)** | Bug | Cluster Info Fingerprint Bullet Point Line Breaks | `REQ-UI-269`, `TST-UI-228` | **Approved (i.O.)**: 4 distinct bullet lines restored across all 9 languages. | `Erledigt` | `V4.9.39` |
| **[ATT-2231](https://rainerblind.atlassian.net/browse/ATT-2231)** | Improvement | Limit LiveSegment Bottom Sheet Expansion | `REQ-UI-270`, `TST-UI-229` | **Approved (i.O.)**: Sheet clamped to profile height, cockpit telemetry visible. | `Erledigt` | `V4.9.39` |
| **[ATT-2186](https://rainerblind.atlassian.net/browse/ATT-2186)** | Improvement | Workout Origin Source Attribute in DB & Header | `REQ-DAT-017`, `TST-DAT-012` | **Approved (i.O.)**: DB schema v24 & tagging verified. Follow-up bug ATT-2387 filed. | `Erledigt` | `V4.9.39` |
| **[ATT-1828](https://rainerblind.atlassian.net/browse/ATT-1828)** | Feature | FIT Workout Importer with Duplicate Detection | `REQ-DAT-024`, `TST-DAT-024` | **Approved (i.O.)**: FIT import verified on device. | `Erledigt` | `V4.9.39` |
| **[ATT-1827](https://rainerblind.atlassian.net/browse/ATT-1827)** | Feature | Full-Telemetry FIT Workout Exporter via Garmin FIT SDK | `REQ-DAT-025`, `TST-DAT-025` | **Approved (i.O.)**: Exported and successfully re-imported test workout. | `Erledigt` | `V4.9.39` |
| **[ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281)** | Feature | Persistent Climbs Database & Live ClimbPro Cockpit Sheet | `REQ-MAP-027`, `TST-MAP-029` | **Approved (i.O.)**: Live climb HUD appeared during ride. Follow-up ATT-2388 filed. | `Erledigt` | `V4.9.39` |
| **[ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)** | Improvement | Modernize Permission Flow | `REQ-PRI-003`, `TST-PRI-002` | **Revision needed (n.i.O.)**: Navigates only to generic App Info rather than direct system prompts. | `Analysis` | None |
| **[ATT-1841](https://rainerblind.atlassian.net/browse/ATT-1841)** | Improvement | High-Contrast Route Navigation & Directional Chevrons | `REQ-MAP-023`, `TST-MAP-025` | **Postponed**: Retained in Final Review for in-depth outdoor test. | `Final Review` | `V4.9.39` |
| **[ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450)** | Feature | Turn-by-Turn Navigation Cues & Battery Saver Wake-Up | `REQ-MAP-028`, `TST-MAP-030` | **Postponed**: Retained in Final Review for in-depth outdoor test. | `Final Review` | `V4.9.39` |

---

### 1.2 Follow-Up Backlog Tickets Created During Review

In strict compliance with Rule 10 (*"No code changes during Sprint Review"*) and Rule 17 (*"Lean Defect Recording"*), all user observations and refinement requests were captured immediately into structured Jira backlog tickets and ranked at the top of the backlog:

1. **[ATT-2382](https://rainerblind.atlassian.net/browse/ATT-2382)**: Split Equipment Sensor Matrix into Sport-Specific Tables for Bikes and Shoes and Restrict Incompatible Sensor Linkage
2. **[ATT-2383](https://rainerblind.atlassian.net/browse/ATT-2383)**: Simplify Column Headers to List and Details in Workout Cards and Details Settings
3. **[ATT-2384](https://rainerblind.atlassian.net/browse/ATT-2384)**: Define and Harmonize Default Section Visibility and Order for Workout Cards and Details
4. **[ATT-2385](https://rainerblind.atlassian.net/browse/ATT-2385)** (Bug): Prevent Overlapping Distance Tick Labels on Elevation Profile X-Axis in Segment and Route Cards
5. **[ATT-2386](https://rainerblind.atlassian.net/browse/ATT-2386)**: Anchor Elevation Profile to Bottom Navigation Bar and Dynamically Expand Upper Map in Route and Segment Details
6. **[ATT-2387](https://rainerblind.atlassian.net/browse/ATT-2387)** (Bug): Map Origin Source Attribute in WorkoutDataMapper Batch fromCursor to Display Source Badges
7. **[ATT-2388](https://rainerblind.atlassian.net/browse/ATT-2388)**: Increase Visibility and Visual Prominence of Climbs on Routes

---

## 2. Retrospective: Observations, Root Causes & Process Hardening

### 2.1 Branch Lifecycle & Clean Sprint Closure ("Prune Merged Sprint Branches")
* **Observation**:
  During Sprint 2026-40.15, `review_agent.py` evaluated diffs against the previous sprint branch (`sprint/2026-40.14`) because it had not been deleted locally after merging into `develop`. Because git listed branches alphabetically, the old branch was selected first, resulting in massive diff truncation (>100k chars) and false-positive audit warnings.
* **Root Cause**:
  Sprint closure in `sprint-planner/SKILL.md` lacked an explicit post-condition check confirming that local sprint branches are deleted after merging into `develop`.
* **Process Hardening**:
  - Updated Ceremony 2 Step 5 in `.agents/skills/sprint-planner/SKILL.md` to mandate `git branch -d sprint/<SPRINT_NAME>` followed by an explicit verification check: `git branch --list 'sprint/*'`.
  - Updated `review_agent.py` to sort sprint branches descending (`reverse=True`) as defensive tooling hygiene.

### 2.2 Database & DTO Mapping Symmetry (ATT-2186 / ATT-2387)
* **Observation**:
  On-device inspection showed that imported TCX workouts had `source = 'TCX'` in SQLite, but the UI omitted the header badge.
* **Root Cause**:
  `WorkoutDataMapper.kt` contains two overloads: `fromCursor(cursor)` (single) and `fromCursor(cursor, batch)` (batch-loaded). While `fromCursor(cursor)` correctly mapped `source`, `fromCursor(cursor, batch)` omitted the `source` parameter. Because `WorkoutData.source` defaults to `WorkoutSource.TRACKED`, all batch-loaded workouts silently defaulted to `TRACKED`.
* **Process Hardening (Codified as Rule 20)**:
  - Added **Rule 20** to `.agents/rules/aspice_governance.md`: All mapping pathways (single, batch, and cache updates) must map database/DTO fields symmetrically. Relying on default constructor arguments without explicit mapping across all cursor overloads is prohibited and must be guarded by architectural contract tests.

### 2.3 Direct System Settings Intents vs. Generic App Info (ATT-2075)
* **Observation**:
  The reworked permission setup flow directed the user to the generic Android Application Info screen (`ACTION_APPLICATION_DETAILS_SETTINGS`), forcing the user to hunt manually through nested submenus for permissions and battery optimization.
* **Root Cause**:
  Over-generalizing the fallback intent degraded the UX from direct, actionable prompts to generic settings navigation.
* **Process Hardening (Codified as Rule 21)**:
  - Added **Rule 21** to `.agents/rules/aspice_governance.md`: User prompts for system permissions, battery optimization, or hardware settings must target the most specific direct platform intent. Generic App Details Settings is only permissible as a last-resort fallback when direct intents are unavailable or permissions permanently blocked.

### 2.4 AAPT2 Resource Compilation & Localization Linebreaks (ATT-2195)
* **Observation**:
  In the cluster explanation dialog, bullet point linebreaks (`&#10;`) in `strings.xml` were collapsed into single spaces by AAPT2 during build.
* **Root Cause**:
  Unquoted XML entities are normalized to whitespace during Android asset packaging.
* **Process Hardening**:
  - Updated `.agents/skills/stage2-req-test-spec/SKILL.md` to mandate that localized strings requiring linebreaks must use literal `\n` escapes across all 9 supported application languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 2.5 QuickReturn Scroll Mechanics (ATT-2304 / ATT-2178)
* **Observation**:
  In ATT-2178, scroll handling was moved from `onPreScroll` to `onPostScroll` to prioritize inner list scrolling, which broke the immediate expansion of collapsing headers when scrolling down on long lists.
* **Resolution & Value**:
  Fixed by implementing `quickReturn = true` on the nested scroll connection and adding dedicated contract tests to lock in the behavior.

---

## 3. Sprint Closure Sign-Off & Verification Metrics

* **Clean-Room Test Suite**: `./gradlew testDebugUnitTest` passed with 100% pass rate.
* **Target Hardware Verification**: Verified on Google Pixel 10 (Android 16).
* **Governance Compliance**: Rules 1–21 validated and enforced.
* **Sprint Branch Integration**: Branch `sprint/2026-40.15` is fully up-to-date and ready for non-fast-forward merge into `develop`.
