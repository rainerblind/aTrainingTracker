# Sprint Review & Retrospective: Sprint 2026-40.7 (Release V4.9.38)

* **Ticket**: [ATT-1820](https://atrainingtracker.atlassian.net/browse/ATT-1820) (*Review & Retro*)
* **Sprint**: `2026-40.7`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.7` -> `develop`

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.7**, 12 core feature, improvement, and bug tickets were implemented, verified through the unified 5-stage ASPICE process, validated with clean-room regression suites (`./gradlew testDebugUnitTest`), and cleanly integrated into the sprint integration branch `sprint/2026-40.7` using Continuous Integration Strategy A. In Ceremony 2 (Joint Review), all 12 tickets were reviewed on-device on Google Pixel 10 hardware and individually approved by the human user.

### 1.1 Summary of Shipped Tickets
| Ticket | Type | Summary | Key Impact & Solution | Traceability | Review Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-1742](https://atrainingtracker.atlassian.net/browse/ATT-1742)** | Feature | Aftermath/Splits: High-Aesthetic Redesign of Lap & Interval Split Visualizer | Redesigned post-workout split visualizer into an aesthetic, card-based comparative telemetry view. | `REQ-UI-204`, `TST-UI-158` | Approved | **Erledigt** |
| **[ATT-1751](https://atrainingtracker.atlassian.net/browse/ATT-1751)** | Feature | Cockpit/Typography: Configurable Cockpit Font Family & Boldness in Advanced Settings | Added configurable font family and weight with live preview for tracking HUD numbers. | `REQ-UI-214`, `TST-UI-168` | Approved | **Erledigt** |
| **[ATT-1811](https://atrainingtracker.atlassian.net/browse/ATT-1811)** | Bug | Aftermath/Zones: Heart rate and power zone distribution calculation computes total time as 1 second | Fixed zone aggregation query and calculation returning 1 second instead of actual workout duration. | `REQ-UI-202`, `TST-UI-156` | Approved | **Erledigt** |
| **[ATT-1812](https://atrainingtracker.atlassian.net/browse/ATT-1812)** | Bug | Aftermath/Map: Restore map preview visibility on detailed workout inspection screen | Fixed compose layout constraint hiding the static/dynamic track map in workout inspection. | `REQ-UI-206`, `TST-UI-160` | Approved | **Erledigt** |
| **[ATT-1813](https://atrainingtracker.atlassian.net/browse/ATT-1813)** | Verbesserung | Aftermath/Graphs: Reorder telemetry graphs: place Speed/Pace above Heart Rate graph | Reordered telemetry graph stack so athlete primary speed/pace graph appears directly below elevation. | `REQ-UI-206`, `TST-UI-160` | Approved | **Erledigt** |
| **[ATT-1814](https://atrainingtracker.atlassian.net/browse/ATT-1814)** | Verbesserung | Aftermath/Graphs: Synchronize horizontal zoom globally across all telemetry graphs | Unified horizontal viewport state across all continuous metric graphs for seamless multi-metric inspection. | `REQ-UI-215`, `TST-UI-169` | Approved | **Erledigt** |
| **[ATT-1815](https://atrainingtracker.atlassian.net/browse/ATT-1815)** | Verbesserung | Settings/Aftermath: Relocate Edit Workout fields and Workout List card section toggles to Advanced Settings | Consolidated disparate UI card settings into dedicated sections under Advanced Settings. | `REQ-UI-216`, `TST-UI-170` | Approved | **Erledigt** |
| **[ATT-1816](https://atrainingtracker.atlassian.net/browse/ATT-1816)** | Verbesserung | Lieblingsorte: Add map preview thumbnail on right of KnownLocationCard and standardize heading typography | Added mini map thumbnail to known location cards with 200m circular geofence overlay. | `REQ-UI-217`, `TST-UI-171` | Approved | **Erledigt** |
| **[ATT-1817](https://atrainingtracker.atlassian.net/browse/ATT-1817)** | Verbesserung | UI/Theme: Standardize popup and bottom sheet surface background across LiveSegment, Routes, Segments, and Settings | Standardized bottom sheet containers to use `MaterialTheme.colorScheme.surface` with 0.dp tonal elevation. | `REQ-UI-218`, `TST-UI-172` | Approved | **Erledigt** |
| **[ATT-1818](https://atrainingtracker.atlassian.net/browse/ATT-1818)** | Bug | Aftermath/Graphs: Fix pace decoding, clamp implausible scrubbing values, and format Y-axis pace labels as mm:ss | Corrected inverted speed-to-pace formula ($s/m = 1/v$), clamped speeds $< 0.55\text{ m/s}$ to `"-- min/km"`, and formatted Y-axis in `mm:ss`. | `REQ-UI-219`, `TST-UI-173` | Approved | **Erledigt** |
| **[ATT-1819](https://atrainingtracker.atlassian.net/browse/ATT-1819)** | Bug | Aftermath/Graphs: Fix unreadable x-Axis milestone collisions and text overlapping on HR and Pace graphs | Enforced adaptive 2km steps, 12dp label clearance, and standardized milestone formatting with boundary labels. | `REQ-UI-220`, `TST-UI-174` | Approved | **Erledigt** |
| **[ATT-1810](https://atrainingtracker.atlassian.net/browse/ATT-1810)** | Bug | Sensors/Altimeter: Super implausible altitude | Fixed barometric baseline correction feedback oscillation, gated calibration to geofence transitions, clamped climb rates, and auto-sanitized historical 7300m profiles. | `REQ-CON-017`, `TST-CON-008` | Approved | **Erledigt** |

---

## 2. Retrospective: Analysis of Observations & Countermeasures

### 2.1 Observation 1: Jira Access Script Friction During Sprint Planning
* **Observation**:
  User reported in ATT-1820: *"Something was wrong with the skript to access jira. We had a lot of errors during the sprint planning."*
* **Root Cause**:
  During early Sprint Planning, invocations to `jira_util.py` failed when role arguments, flags, or command syntax deviated from strict CLI signatures (e.g. attempting unsupported subcommands or misconfigured environment variables in `.env.jira`).
* **Countermeasure**:
  - Validated all CLI signatures in `tools/jira_util.py`.
  - Hardened error handling and usage documentation in `jira-workflow` skill and `tools/jira_util.py`.

### 2.2 Observation 2: Barometric Calibration Feedback Loop & Idempotency Safeguards (ATT-1810)
* **Observation**:
  Workout *"Kurz zum Bäcker #14"* generated a 7300 m elevation profile due to staircase plateaus.
* **Root Cause**:
  `setAltitudeCorrection` evaluated `currentAltitude` from `mAltitudeSensor.getValue()` which was *already* corrected, causing oscillation between $+57\text{ m}$ and $0.0\text{ m}$ on alternate sensor cycles. Every cycle shifted historical samples in the SQLite table, accumulating runaway heights.
* **Countermeasure**:
  - Correction is now calculated strictly relative to raw barometric pressure: $\text{newCorrection} = \text{correctAltitude} - \text{mLastRawAltitude}$.
  - Correction broadcast is gated on $|\text{deltaOffset}| \ge 0.1\text{ m}$.
  - Altimeter calibration dispatch in `TrackingTabsViewModel.kt` is gated on geofence transitions via `lastCalibratedLocationId`.
  - Added physical sanity limits ($\le 30\text{ m/s}$ climb rate, retroactive shifts $\le 500\text{ m}$) and background SQLite auto-sanitization for historical workouts.

### 2.3 Observation 3: Android Framework Mocking Pattern Standardization (ContentValues & Log)
* **Observation**:
  Unit tests interacting with Android framework classes (`android.util.Log`, `android.content.ContentValues`) threw `RuntimeException: Method ... not mocked` in JVM test suites.
* **Root Cause**:
  Android unit tests run on standard JVM without framework implementations unless Robolectric is used or classes are mocked.
* **Countermeasure**:
  Standardized MockK patterns across all unit test suites:
  - For `Log`: `mockkStatic(Log::class)` with stubs for `d`, `i`, `w`, `e`.
  - For `ContentValues`: `mockkConstructor(ContentValues::class)` with `import io.mockk.*` and `constructedWith<ContentValues>()` answer stubs.

### 2.4 Observation 4: Strategy A Continuous Integration & Zero Merge Conflicts
* **Observation**:
  12 tickets touched shared UI containers, maps, filters, telemetry graphs, and databases.
* **Result**:
  **Zero merge conflicts** occurred during the entire sprint. Strategy A (merging verified feature branches into `sprint/2026-40.7` immediately upon Stage 5 verification with `--no-ff`) guaranteed that each subsequent ticket was built directly upon the latest verified sprint state.

---

## 3. Sprint Closure Sign-Off

* **Total Shipped Scope**: 12 tickets verified and approved.
* **Release Version**: `V4.9.38`.
* **Clean-Room Test Pass Rate**: 100% (0 failures, 0 regressions across all modules).
