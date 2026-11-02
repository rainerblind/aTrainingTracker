# Sprint Review & Retrospective: Sprint 2026-40.10 (Release V4.9.38)

* **Ticket**: [ATT-1989](https://rainerblind.atlassian.net/browse/ATT-1989) (*Review & Retro*)
* **Sprint**: `2026-40.10`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.10` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 15 / 16 Preview, physical device `66020DLCR002FL`)

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.10**, 3 tickets were developed and processed through the ASPICE engineering lifecycle. All 3 tickets completed Stage 1–5 workflows, passed unit and clean-room regression test suites (`./gradlew testDebugUnitTest`), and were integrated continuously into `sprint/2026-40.10` via Strategy A.

In Ceremony 2 (Joint Review), all tickets were deployed to the attached Google Pixel 10 physical hardware via `adb install -r` and evaluated one-by-one in strict backlog rank order:

* **[ATT-1986](https://rainerblind.atlassian.net/browse/ATT-1986)** was **Approved (i.O.)** and transitioned to **Erledigt** by the human user.
* **[ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)** was evaluated on physical hardware: panning on Telemetry Graphs is now fluid and responsive, but panning via the Elevation Profile was non-responsive. Moved back to **Zu erledigen** for revision in Sprint 2026-40.11.
* **[ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)** was evaluated on physical hardware: technical functionality is sound, but the `FilterChip` UI was rejected for this binary choice in favor of a `SingleChoiceSegmentedButtonRow` (consistent with the 5 Zones vs Histogram switcher). Moved back to **Zu erledigen** for revision in Sprint 2026-40.11.
* **[ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)**: During hardware testing, an edge case was observed where indoor/GPS-less workouts populate the 5-zone HR card but fail to display a continuous HR telemetry graph. A dedicated backlog ticket was filed with an on-device screenshot attached.

### 1.1 Summary of Evaluated Tickets

| Ticket | Type | Summary | Key Impact & Solution | Traceability | Review Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)** | Verbesserung | [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs | Decoupled pointerInput keys and accumulated drag delta. Panning on Telemetry Graphs is fluid, but Elevation Profile panning requires further remediation. | `REQ-UI-232`, `TST-UI-190` | `n.i.O.` (Elevation Profile pan fix needed) | **Zu erledigen** |
| **[ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)** | Verbesserung | [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings | Streamlined `LapDisplayMode` to binary `TABLE_ONLY` / `VISUALIZER_ONLY`. Technical wiring verified; UI requires `SegmentedButton` styling. | `REQ-UI-229`, `TST-UI-191` | `n.i.O.` (UI design refinement to SegmentedButton) | **Zu erledigen** |
| **[ATT-1986](https://rainerblind.atlassian.net/browse/ATT-1986)** | Feature | [Aftermath/Settings] Independent X-Axis Domain Settings for Elevation Profile (Distance) vs. Telemetry Graphs (Time) | Decoupled `elevationXAxisDomain` (DISTANCE) and `telemetryXAxisDomain` (TIME), added settings rows with 9-locale parity, and resolved cross-domain cursor scaling. | `REQ-UI-233`, `TST-UI-192` | **Approved (i.O.)** | **Erledigt** |
| **[ATT-1989](https://rainerblind.atlassian.net/browse/ATT-1989)** | Task | Review & Retro for Sprint 2026-40.10 | Governance facilitation, retrospective synthesis, and sprint closure. | `REQ-PRO-001` | Closed | **Erledigt** |

---

## 2. Retrospective: Observations, Root Causes & Countermeasures

### 2.1 Observation 1: Elevation Profile vs. Telemetry Graphs Gesture Asymmetry (ATT-1987)
* **Observation**:
  On physical Pixel 10 hardware, panning across Telemetry Graphs (Speed, Heart Rate, Power) followed finger drag seamlessly across the full travel distance. However, swiping horizontally on the Elevation Profile failed to move the chart viewport.
* **Root Cause Analysis**:
  In `ElevationProfile.kt`, `currentUpdateZoomState` was passed as `rememberUpdatedState(::updateZoom)`. In addition, when `ATT-1986` decoupled the domain into `elevationXAxisDomain` (Distance in meters) and `telemetryXAxisDomain` (Time in seconds), `MapDetailLayout.kt` retained a single shared `profileStartDist` scalar state. When interacting with time-based telemetry graphs, `profileStartDist` receives temporal magnitudes (seconds) which corrupt distance-based clamping in `ElevationProfileZoomMath.applyPan`, effectively locking the elevation profile viewport at `maxStart`.
* **Countermeasure & Action Item**:
  Ticket `ATT-1987` was moved back to `Zu erledigen`. In Sprint 2026-40.11, the gesture handling in `ElevationProfile.kt` and domain start offset state in `MapDetailLayout.kt` will be cleanly separated and calibrated.

### 2.2 Observation 2: Design Token & Control Selection for Binary Preferences (ATT-1988)
* **Observation**:
  While eliminating the redundant `'Both'` option was technically successful, presenting a binary choice (**Tabelle** vs. **Visualizer**) using two separate `FilterChip` components lacked visual polish and felt out of place compared to established application design patterns.
* **Root Cause**:
  `FilterChip` rows are intended for multi-select tag filtering, whereas mutually exclusive binary modes are best represented by Material 3 `SingleChoiceSegmentedButtonRow` / `SegmentedButton` controls, as already implemented in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`.
* **Countermeasure & Action Item**:
  Ticket `ATT-1988` was moved back to `Zu erledigen`. In Sprint 2026-40.11, the selector in `AdvancedTuningDialog.kt` will be replaced with a centered `SingleChoiceSegmentedButtonRow` matching the 5 Zones vs. Histogram design.

### 2.3 Observation 3: Missing Telemetry Curve on GPS-Less / Indoor Activities (ATT-2006)
* **Observation**:
  Inspecting workout `2014-10-11_134259` revealed that while the 5-zone Heart Rate card and frequency histogram were fully populated (39:38 duration), no continuous Heart Rate telemetry graph was displayed above it.
* **Root Cause**:
  In `MapDetailLayout.kt`, all telemetry metric graphs are nested inside `activeScrubPath?.let { path ->`. When an activity is recorded indoors or lacks GPS track points, `activeScrubPath` is null, suppressing the entire graph section even though rich sensor telemetry exists in the database.
* **Countermeasure**:
  Captured an on-device screenshot and created backlog ticket **[ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)** (`[Aftermath/Telemetry] Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track`) with the screenshot attached.

---

## 3. Backlog Scope for Sprint 2026-40.11

| Key | Summary | Priority | Origin |
| :--- | :--- | :--- | :--- |
| **[ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)** | [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs | Rank 1 (Carried Over) | ATT-1987 physical review |
| **[ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)** | [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings (SegmentedButton) | Rank 2 (Carried Over) | ATT-1988 physical review |
| **[ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)** | [Aftermath/Telemetry] Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track | Backlog | ATT-2006 physical review |
| **[ATT-1645](https://rainerblind.atlassian.net/browse/ATT-1645)** | [UI/Sheets] Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets | Backlog | Interactive Tuning Session |

---

## 4. Sprint Closure Sign-Off & Verification Metrics

* **Continuous Integration**: `sprint/2026-40.10` merged cleanly into `develop` with `--no-ff`.
* **Automated Regression Suite**: 32 actionable Gradle test tasks passed (100% pass rate).
* **Governance Compliance**: All living docs (`docs/requirements.md`, `docs/tests.md`) maintained in verified state.
