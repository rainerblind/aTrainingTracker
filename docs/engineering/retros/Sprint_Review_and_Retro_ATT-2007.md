# Sprint Review & Retrospective: Sprint 2026-40.11 (Release V4.9.38)

* **Ticket**: [ATT-2007](https://rainerblind.atlassian.net/browse/ATT-2007) (*Review & Retro*)
* **Sprint**: `2026-40.11`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.11` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 Preview, physical device `66020DLCR002FL`)
* **Date**: 2026-10-02

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.11**, 6 feature, improvement, and bugfix tickets were developed and processed through the agile ASPICE engineering lifecycle. All 6 tickets completed Stage 1–5 workflows, passed unit and clean-room regression test suites (`./gradlew testDebugUnitTest`), and were integrated continuously into `sprint/2026-40.11` via Strategy A.

In Ceremony 2 (Joint Review), the complete integrated build was deployed to the attached Google Pixel 10 physical hardware via `./gradlew installDebug` and evaluated one-by-one in strict backlog rank order:

* **[ATT-1645](https://rainerblind.atlassian.net/browse/ATT-1645)** was evaluated on physical hardware: the bottom sheet peek behavior was judged as "still not ideal". Moved back to **Analysis** for revision in Sprint 2026-40.12.
* **[ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)** was evaluated on physical hardware: for workout `2014-12-03_145500`, the Google Map collapsed cleanly and the 5-zone HR card rendered properly, but the continuous HR line graph canvas was missing above the card. Moved back to **Zu erledigen** for forensic investigation and resolution.
* **[ATT-2005](https://rainerblind.atlassian.net/browse/ATT-2005)** was evaluated on physical hardware: marking workouts as "Race" (Wettkampf) across DB schema v23, domain models, Material 3 header badge, edit UI, list filters, and Strava sync was verified on-device. **Approved (i.O.)** and transitioned to **Erledigt** by the human user.
* **[ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)** was evaluated on physical hardware: panning on the Elevation Profile and all stacked Telemetry Graphs is now fully synchronized and responsive via normalized viewport fractions (`MapDetailViewportMath.kt`). **Approved (i.O.)** and transitioned to **Erledigt** by the human user.
* **[ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)** was evaluated on physical hardware: the lap display mode selector in `AdvancedTuningDialog.kt` was updated from `FilterChip` to Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton` (`TABLE_ONLY` vs. `VISUALIZER_ONLY`). **Approved (i.O.)** and transitioned to **Erledigt** by the human user.
* **[ATT-2008](https://rainerblind.atlassian.net/browse/ATT-2008)** was evaluated on physical hardware: bottom sheet popups for segments, routes, and live segments now apply navigation bar insets and allow full upward travel, keeping the entire elevation profile completely visible and interactive above the 3-button navigation bar. **Approved (i.O.)** and transitioned to **Erledigt** by the human user.

### 1.1 Summary of Evaluated Tickets

| Ticket | Type | Summary | Key Impact & Solution | Traceability | Review Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-1645](https://rainerblind.atlassian.net/browse/ATT-1645)** | Verbesserung | [UI/Sheets] Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets | Dynamic header height measurement in `MapDetailLayout` and centralized tokens in `BottomSheetDesign`. Needs further UX calibration. | `REQ-UI-221`, `TST-UI-175` | `n.i.O.` (Further calibration needed) | **Analysis** |
| **[ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)** | Verbesserung | [Aftermath/Telemetry] Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track | Decoupled telemetry ingestion and collapsed map. Continuous HR line graph was missing on physical test device for workout `2014-12-03_145500`. | `REQ-UI-235`, `TST-UI-194` | `n.i.O.` (HR curve missing on device) | **Zu erledigen** |
| **[ATT-2005](https://rainerblind.atlassian.net/browse/ATT-2005)** | Feature | [WorkoutSummaries] Support Marking Workouts as "Race" (Wettkampf) Across DB, Header Badge, Edit Dialog, and Filters | SQLite DB schema v23, `WorkoutData.race`, Material 3 header trophy badge, edit toggle, list filter, Strava sync, 9 locales. | `REQ-UI-236`, `TST-UI-195` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)** | Verbesserung | [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs | Normalized dimensionless viewport fractions (`MapDetailViewportMath.kt`), gesture lifecycle stability in `ElevationProfile.kt`. | `REQ-UI-232`, `REQ-UI-234`, `TST-UI-190` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)** | Verbesserung | [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings (SegmentedButton) | Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton` replacing `FilterChip` per `REQ-UI-234`. | `REQ-UI-229`, `REQ-UI-234`, `TST-UI-191` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2008](https://rainerblind.atlassian.net/browse/ATT-2008)** | Bug | [UI/Sheets] Segment and Route Popups within Map Cannot Be Moved Upward Enough | Navigation bar window insets on `ElevationProfile` surface in `MapDetailLayout.kt` and upward travel clearance above 3-button bar. | `REQ-UI-237`, `TST-UI-196` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-2007](https://rainerblind.atlassian.net/browse/ATT-2007)** | Feature | Review & Retro for Sprint 2026-40.11 | Governance facilitation, retrospective synthesis, and sprint closure. | `REQ-PRO-001` | Closed | **Erledigt** |

---

## 2. Retrospective: Observations, Root Causes & Countermeasures

### 2.1 Observation 1: Bottom Sheet Peek Height Calibration (ATT-1645)
* **Observation**:
  On physical Pixel 10 hardware, the sheet peek behavior for segment and route popups was evaluated as "still not ideal".
* **Root Cause Analysis**:
  Although `onHeaderHeightMeasured` captured the rendered header height dynamically, the combination of sheet snap animation thresholds, drag handle heights, and differing card contents (e.g. 2-line vs 3-line segment stats) causes the peek height to feel slightly misaligned or awkward when resting in peek mode.
* **Countermeasure & Action Item**:
  Ticket `ATT-1645` was moved back to **Analysis**. In Sprint 2026-40.12, we will gather specific user requirements on target peek behavior (e.g. exact resting height, whether the drag handle should be visible, or whether the sheet should open directly into expanded state).

### 2.2 Observation 2: Missing Continuous HR Line Curve on Device (ATT-2006)
* **Observation**:
  Opening trackless workout `2014-12-03_145500` on the Pixel 10 demonstrated that the Google Map was collapsed cleanly and the 5-zone HR distribution card rendered with 26:14 duration. However, the continuous HR line curve itself did not appear above the card (only the zoom toolbar was visible).
* **Root Cause Analysis**:
  While unit tests verified that `WorkoutRepository.getWorkoutTelemetryPoints` synthesizes `PathPoint` objects from samples, in `TrackOnMapScreen.kt` and `MapDetailLayout.kt`, telemetry graphs are conditionally rendered based on:
  ```kotlin
  if (TelemetryMetricUtils.hasHeartRateData(path)) {
      TelemetryMetricGraph(...)
  }
  ```
  If `path` (or `activeScrubPath`) had empty points for this specific workout, or if sample extraction in `WorkoutRepository` filtered them out (e.g. if HR was stored in legacy columns or if `workoutId` lookup differed), `hasHeartRateData(path)` evaluated to false.
* **Countermeasure & Action Item**:
  Ticket `ATT-2006` was moved back to **Zu erledigen**. In Sprint 2026-40.12, forensic inspection of the exact database samples for workout `2014-12-03_145500` will be performed to ensure telemetry points are extracted and rendered seamlessly.

### 2.3 Observation 3: High-Yield Architectural Wins (ATT-1987, ATT-1988, ATT-2005, ATT-2008)
* **Positive Findings**:
  - The normalized viewport math introduced in `MapDetailViewportMath.kt` permanently resolved cross-domain pan sensitivity and synchronization bugs across all telemetry graphs.
  - The adoption of `SingleChoiceSegmentedButtonRow` for binary mode choices in `AdvancedTuningDialog.kt` aligned the settings UI with the centralized design system (`docs/design_guidelines.md` §1.1).
  - Race workout classification (`WorkoutData.race`, DB schema v23, Material 3 trophy badge, filter bottom sheet, and Strava uploader) shipped flawlessly on the first iteration with 100% 9-language localization parity.
  - Upward travel and navigation bar insets in `MapDetailLayout.kt` cleared the elevation profile above the Android 3-button navigation bar cleanly.

---

## 3. Backlog Scope for Sprint 2026-40.12

| Key | Summary | Priority | Origin |
| :--- | :--- | :--- | :--- |
| **[ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)** | [Aftermath/Telemetry] Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track | Rank 1 (Carried Over) | ATT-2006 physical review |
| **[ATT-1645](https://rainerblind.atlassian.net/browse/ATT-1645)** | [UI/Sheets] Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets | Rank 2 (Carried Over) | ATT-1645 physical review |

---

## 4. Sprint Closure Sign-Off & Verification Metrics

* **Continuous Integration**: `sprint/2026-40.11` merged cleanly into `develop` with `--no-ff`.
* **Automated Regression Suite**: 32 actionable Gradle test tasks passed (100% pass rate in clean-room build).
* **Governance Compliance**: All living docs (`docs/requirements.md`, `docs/tests.md`) maintained in verified state.
