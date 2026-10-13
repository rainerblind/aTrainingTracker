# [Feature] [Cockpit] Add running power cockpit view and telemetry analytics

**Ticket**: [ATT-3449](https://rainerblind.atlassian.net/browse/ATT-3449)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Status**: Backlog  

---

## Problem Statement & Value Proposition
With the addition of running power sensor ingestion (Stryd / BLE Running Power), the athlete needs real-time visibility in the tracking cockpit and comprehensive post-workout analysis:
1. Missing Cockpit Layout: ActivityType currently only has `RUN_SPEED_AND_CADENCE` for running, which lacks dedicated tiles for instantaneous Power (Watts), Watts per Kilogram (W/kg), and Running Power Zones. The athlete cannot monitor their pacing effort against running power targets during intervals or races.
2. Distinct Running Critical Power / rFTP: Runners pace efforts using Running Functional Threshold Power (rFTP / Critical Power in W or W/kg), which is physiologically distinct from cycling FTP. The athlete requires a distinct rFTP setting in athlete profile settings to define running power zones (Z1 Recovery to Z5+ Neuromuscular).
3. Telemetry Analytics & Graphs: Post-workout visual analytics (`Aftermath`) lack running power telemetry graphs (Power vs. Distance / Elevation) and summary metrics (Average Power, Max Power, Normalized Power / Running Work in kJ) for running workouts.

## User Story
* **As a**: runner tracking a power-based workout
* **I want**: a dedicated running power cockpit dashboard and post-workout telemetry analytics
* **So that**: I can pace my run precisely in real time according to my running power zones and analyze my power curve and effort in the workout summary.

## Scope & Technical Architecture
1. **Activity & Cockpit Configuration**:
   * Add `ActivityType.RUN_POWER` (or dynamically enable power sensor tiles in `ActivityType.RUN_SPEED_AND_CADENCE`) in `ActivityType.java` and `TrackingViewsDatabaseManager.java`.
   * Configure default grid layout: Instantaneous Power (HUGE / LARGE), Pace (LARGE), Heart Rate (NORMAL), Cadence (NORMAL), Distance (NORMAL), Elapsed Time (NORMAL).
   * Display derived metrics: Watts per Kilogram (W/kg) based on athlete weight profile.
2. **Athlete Profile & Running Power Zones (rFTP)**:
   * Add `rFTP` (Running Functional Threshold Power) configuration to user profile settings (distinct from cycling FTP).
   * Render real-time color-coded running power zone indicator (Z1–Z5) in the cockpit power tile.
3. **Aftermath Visual Analytics & Summaries**:
   * Extend `WorkoutSummariesDatabaseManager` to persist `powerMean`, `powerMax`, and `work_kJ` for running sport activities.
   * Enable Power telemetry metric curve in post-workout map and chart views (`TelemetryMetricGraph`) for running sessions.
4. **Localization Parity**:
   * Provide 9-language translations for new activity labels, sensor tiles, and settings strings (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

## Acceptance Criteria (Given-When-Then)
* **Criterion 1: Running Power Cockpit Rendering**:
  * *Given* an athlete starting a running workout with `RUN_POWER` activity type selected
  * *When* the cockpit screen is displayed
  * *Then* instantaneous power is rendered prominently with unit `W` alongside pace, heart rate, and cadence.
* **Criterion 2: Running Power Zones (rFTP)**:
  * *Given* an athlete with configured rFTP of 300 W
  * *When* running at 270 W (90% rFTP, Zone 4 - Threshold)
  * *Then* the power tile reflects the corresponding threshold zone color and label.
* **Criterion 3: Post-Workout Power Telemetry Graph**:
  * *Given* a completed running workout recorded with a running power meter
  * *When* inspecting the workout in Aftermath / Workout Details
  * *Then* the telemetry graph displays the power curve synchronized with elevation and pace over distance/time.
* **Criterion 4: Summary Metrics Persistence**:
  * *Given* a completed running power workout
  * *When* viewing the workout summary header and export data (TCX/FIT)
  * *Then* average power, maximum power, and total mechanical work (kJ) are accurately computed and displayed.

## Parent Epic & Sprint Placement
* **Target Epic**: `ATT-355` (Good and consistent UI)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
