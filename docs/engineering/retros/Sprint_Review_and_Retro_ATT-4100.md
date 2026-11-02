# Sprint Review & Retrospective — Sprint 2026-42.10 (`ATT-4100`)

**Date**: 2026-10-19  
**Sprint**: `2026-42.10` (`sprint/2026-42.10`, Jira Sprint ID `378`)  
**Retrospective Ticket**: [ATT-4100](https://rainerblind.atlassian.net/browse/ATT-4100)  
**Target Release**: `V4.9.40`

---

## 1. Sprint Review Summary

### 1.1 Reviewed & Accepted Tickets (`FixVersion: V4.9.40` — 36 / 36 Approved)

#### A. User-Facing UI/UX & Feature Tickets (On-Device Verified on Google Pixel 10)
1. **ATT-4043**: *Clean up WorkoutMasksAndCardsSection: remove redundant Details column, collapse Summary list by default, and split Zone Display Mode into Summary vs Details* — **Accepted** (`V4.9.40`)
2. **ATT-4042**: *Single-line Power row and clearer visual separation between domain rows in WorkoutDetails* — **Accepted** (`V4.9.40`)
3. **ATT-1296**: *Live & Post-Workout Anaerobic Capacity (W' Balance / Skiba Match Battery) Tracking* — **Accepted** (`V4.9.40`, *Follow-ups: `ATT-4282`, `ATT-4283`*)
4. **ATT-4049**: *Multi-file GPX route import (`OpenMultipleDocuments` batch import)* — **Accepted** (`V4.9.40`, *Follow-ups: `ATT-4284`, `ATT-4285`*)
5. **ATT-4055**: *Add horizontal section dividers with optional title to TrackingView (Cockpit)* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4286`*)
6. **ATT-4067**: *Historical Workout Cluster Progression & Trend Graph (`ClusterTrendChart`)* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4287`*)
7. **ATT-4051**: *Empty-state '+ Pair Sensor' action and automatic sensor pairing upon discovery* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4288`*)

#### B. Exporter, Concurrency, Security, Database & Service Lifecycle Tickets
8. **ATT-2169**: *[Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity* — **Accepted** (`V4.9.40`)
9. **ATT-3770**: *[Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)* — **Accepted** (`V4.9.40`)
10. **ATT-4098**: *[Medium] Protect async workout finalization with foreground service lifecycle or expedited WorkManager job* — **Accepted** (`V4.9.40`)
11. **ATT-4099**: *[High] Keep BANALService bound or snapshot BANAL state until async endWorkout() finalization completes in TrackerService* — **Accepted** (`V4.9.40`)
12. **ATT-4097**: *[Medium] Buffer one-off navigation events via Channel in TrackingTabsViewModel and ControlTrackingViewModel across STOPPED lifecycle* — **Accepted** (`V4.9.40`)
13. **ATT-4096**: *[Medium] Eliminate potential MySensorManager and ProxySensor lock-order inversion by dispatching initial newValue callbacks outside locks* — **Accepted** (`V4.9.40`)
14. **ATT-4095**: *[Medium] Reuse in-memory LiveWorkoutSession on RESUME_BY_USER and publish session only after rehydration* — **Accepted** (`V4.9.40`)
15. **ATT-3982**: *[Highest] Backup import vulnerable to zip-slip path traversal* — **Accepted** (`V4.9.40`)
16. **ATT-3983**: *[Highest] Strava OAuth tokens and GPS polylines written to logcat in release builds* — **Accepted** (`V4.9.40`)
17. **ATT-3989**: *[High] Full restore deletes current data before validating the backup* — **Accepted** (`V4.9.40`)
18. **ATT-3990**: *[High] Automatic altitude sanitizer irreversibly rewrites legitimate high-altitude data* — **Accepted** (`V4.9.40`)
19. **ATT-3991**: *[High] Samples table names derived from file names are unescaped and collide case-insensitively* — **Accepted** (`V4.9.40`)
20. **ATT-3992**: *[High] Backup import creates orphan rows and is not atomic across databases* — **Accepted** (`V4.9.40`)
21. **ATT-3993**: *[High] SportType database upgrade drops user-defined sport types* — **Accepted** (`V4.9.40`)
22. **ATT-3994**: *[High] FIT export timestamps are shifted by the device UTC offset* — **Accepted** (`V4.9.40`)
23. **ATT-3995**: *[High] Export jobs retry forever, get stuck and cannot be cancelled* — **Accepted** (`V4.9.40`)
24. **ATT-3996**: *[High] Strava token refresh wipes all local Strava data on any invalid response* — **Accepted** (`V4.9.40`)
25. **ATT-4004**: *[High] Remove unused client secrets from APK and restrict generated BuildConfig fields* — **Accepted** (`V4.9.40`)
26. **ATT-4005**: *[High] Add real SQLite migration and persistence tests with Robolectric* — **Accepted** (`V4.9.40`)
27. **ATT-4007**: *[Medium] Improve BLE and ANT+ reconnect and notification handling robustness* — **Accepted** (`V4.9.40`)
28. **ATT-4009**: *[Medium] Legacy database upgrade path crashes and heavy backfills run inside onUpgrade* — **Accepted** (`V4.9.40`)
29. **ATT-4010**: *[Medium] insertSensorFieldAt shifts sensor fields in all cockpit tabs* — **Accepted** (`V4.9.40`)
30. **ATT-4011**: *[Medium] Backup snapshots are inconsistent under concurrent writes and leak DB helpers* — **Accepted** (`V4.9.40`)
31. **ATT-4012**: *[Medium] Strava upload misclassifies retryable errors and may attach to wrong activity* — **Accepted** (`V4.9.40`)
32. **ATT-4013**: *[Medium] GPX/TCX exports can be malformed and Downloads copies use wrong MIME type* — **Accepted** (`V4.9.40`)
33. **ATT-4014**: *[Medium] Google Drive client: unescaped queries, stale folder cache and partial downloads* — **Accepted** (`V4.9.40`)
34. **ATT-4022**: *[Medium] Add state parameter to Strava OAuth flow and scope token exchange* — **Accepted** (`V4.9.40`)
35. **ATT-4023**: *[Medium] Exclude OAuth credentials from Auto Backup and .attbackup archives* — **Accepted** (`V4.9.40`)
36. **ATT-4094**: *[Medium] Distinguish speed sensor standstill from in-motion signal loss (tunnel/dropout) in AutoPauseManager based on last valid speed* — **Accepted** (`V4.9.40`)

---

### 1.2 Follow-Up Tickets Created at Top of Backlog
- **ATT-4282**: *[High] Fix runtime filter registration for 'Best Sensor' fields in EditSensorFieldViewModel and NaN division in TimedMovingAverageFilter* (with `w_prime_cockpit.png`)
- **ATT-4283**: *[Medium] Seed initial W'bal capacity on CyclingPowerMetricsDevice startup and align W/kg & W'bal behavior when stationary or paused* (with `w_prime_cockpit.png`)
- **ATT-4284**: *Prompt for sport type selection (apply to all or individually) during batch GPX route import*
- **ATT-4285**: *[Medium] Keep RouteTabbedScreen in composition while EditRouteScreen bottom sheet is open to prevent tab bar scroll jump*
- **ATT-4286**: *Refine Cockpit section dividers: replace minus-like icon, increase divider prominence, and center section title* (with `cockpit_divider_review.png`)
- **ATT-4287**: *Add X/Y axis labels and outlier-resilient Y-scaling to Workout Cluster progression chart* (with `cluster_trend_review.png`)
- **ATT-4288**: *Remove duplicate '+' prefix from onboarding_pair_sensor_action and devices_action_search_sensors strings* (with `sensor_onboarding_double_plus.png`)

---

## 2. Key Retrospective Learnings & Root Cause Analysis

### Learning 1: Subagent Delegation & Asymmetric `pro` / `flash` Model Pairing ("Zipper Principle" / Reißverschluss-Prinzip, Rule 46)
- **Observation**: Executing 36 sprint tickets across 5 ASPICE stages inside a single monolithic agent context window leads to context bloat and potential self-preference bias when the same model tier both authors and audits deliverables.
- **Root Cause**: Stage skills and `aspice_governance.md` previously did not mandate subagent isolation per stage or asymmetric model pairing (`pro` vs. `flash`) between `agent1` (Doing) and `agent2` (Gate Review).
- **Countermeasure (`.agents/rules/aspice_governance.md` Rule 46, `.agents/skills/stage1-analysis`..`stage5-verification/SKILL.md`, `tools/review_agent.py`, `docs/project_protocol.md`)**:
  - Codified the **Asymmetric `pro` / `flash` Zipper Principle (Reißverschluss-Prinzip)** across Stages 1–5 so that Doing (`agent1`) and Reviewing (`agent2`) in every single stage use **different model tiers**, assigning `pro` to deep reasoning/scrutiny tasks and `flash` to structured drafting and checklist verification:
    - **Stage 1 (`[Analysis]` $\rightarrow$ Gate 1)**: Doing (`agent1`) = **`pro`**, Gate Review (`agent2`) = **`flash`**.
    - **Stage 2 (`[Req & Test Spec]` $\rightarrow$ Gate 2)**: Doing (`agent1`) = **`flash`**, Gate Review (`agent2`) = **`pro`**.
    - **Stage 3 (`[Impl-Plan]` $\rightarrow$ Gate 3)**: Doing (`agent1`) = **`pro`**, Gate Review (`agent2`) = **`flash`**.
    - **Stage 4 (`[Implementation]` $\rightarrow$ Gate 4)**: Doing (`agent1`) = **`flash`** *(Standard)* / **`pro`** *(High-Risk)*, Gate Review (`agent2`) = **`pro`** *(Standard)* / **`flash`** *(High-Risk)*.
    - **Stage 5 (`[Test]` $\rightarrow$ Gate 5)**: Doing (`agent1`) = **`flash`**, Gate Review (`agent2`) = **`pro`**.
  - Updated `tools/review_agent.py` with stage-aware default model tier selection (`flash` for Gates 1 & 3; `pro` for Gates 2, 4 & 5) when no explicit `--model` override is passed.

### Learning 2: Modal Overlay Composition Retention & Chart Axis / Outlier Resilience (`ATT-4285`, `ATT-4287` — Rules 47 & 48)
- **Observation**:
  1. In `RouteListActivity` (`ATT-4049` / `ATT-4285`), closing the `EditRouteScreen` modal bottom sheet caused the `PrimaryScrollableTabRow` in `RouteTabbedScreen` to visibly jump from the leftmost tab (`All`) to the rightmost tab (`Other`).
  2. In `ClusterTrendChart` (`ATT-4067` / `ATT-4287`), the progression graph lacked X-axis and Y-axis markers, and a single anomalous workout session (`115:52 /km`) compressed all normal sessions (`5:14/km – 8:28/km`) into a flat horizontal line at the bottom of the canvas.
- **Root Cause**:
  1. `RouteListActivity` rendered `EditRouteScreen` inside a mutually exclusive `if (editingRoute != null) ... else RouteTabbedScreen(...)` branch, completely removing `RouteTabbedScreen` from the Compose tree whenever the bottom sheet opened and re-initializing `PrimaryScrollableTabRow`'s internal `ScrollState(0)` on dismissal.
  2. `ClusterTrendChart` scaled `minY..maxY` linearly across raw extrema without outlier clamping and drew gridlines without axis tick labels.
- **Countermeasure (`docs/design_guidelines.md` Sec 2.6 & Sec 5.8, `.agents/rules/aspice_governance.md` Rules 47 & 48)**:
  - **Rule 47 (Modal Overlay Composition Retention)**: Modal bottom sheets (`AppModalBottomSheet`, `ModalBottomSheet`) and dialogs MUST be rendered as overlays *alongside* the underlying host screen in composition—never inside a mutually exclusive `if / else` branch that unmounts the underlying screen and destroys scroll/tab state.
  - **Rule 48 (Mandatory Axis Scale Labels & Outlier-Resilient Y-Scaling on Telemetry/Progression Charts)**: Every canvas chart displaying historical trends or telemetry MUST render explicit X-axis and Y-axis scale markers/labels and apply outlier-resilient Y-axis domain clamping (e.g., IQR / percentile bounds) so isolated data anomalies do not flatten the primary trend line.

### Learning 3: Cockpit Section Divider Prominence, Centered Titles & Unambiguous Action Icons/Labels (`ATT-4286`, `ATT-4288` — Rule 49)
- **Observation**:
  1. In `SensorGridScreen` (`ATT-4055` / `ATT-4286`), the toolbar button to insert a divider used `Icons.Default.HorizontalRule` (`—`) next to `+`, which looked like a minus/delete button (`-`), while the rendered divider was a faint `1.dp` line with a left-aligned label.
  2. In `EquipmentSensorMatrixScreen` (`ATT-4051` / `ATT-4288`), the onboarding button displayed `+ + Pair Sensor` because `R.string.onboarding_pair_sensor_action` contained a literal `"+ "` prefix while the `OutlinedButton` also rendered a leading `Icon(Icons.Default.Add)`.
- **Root Cause**:
  1. Selecting `HorizontalRule` for an "insert" action without considering its visual identity as a minus sign next to `+`, and using a left-aligned label instead of symmetric `Modifier.weight(1f)` lines around a centered header.
  2. Including ASCII symbol prefixes (`"+ "`) inside localized XML string resources that are rendered inside buttons with leading icons.
- **Countermeasure (`docs/design_guidelines.md` Sec 5.5 & Sec 5.11, `.agents/rules/aspice_governance.md` Rule 49)**:
  - **Rule 49 (Unambiguous Action Icons, No ASCII Icon Prefixes in Strings, and Centered Section Dividers)**:
    - Never use `Icons.Default.HorizontalRule` (`—`) as an "Add/Insert" toolbar action next to `+`.
    - Never prefix localized `<string>` resources with literal `"+ "` when the UI component renders a leading `Icons.Default.Add` icon.
    - Cockpit and form section dividers with titles must center the title (`Row` with `HorizontalDivider(Modifier.weight(1f))` on both sides, or a prominent full-width section banner) with clear visual contrast (`thickness >= 2.dp`).

### Learning 4: Backend Filter Key Normalization, Post-Trim Empty Guard in Rolling Filters, and Constructor State Seeding (`ATT-4282`, `ATT-4283` — Rules 50 & 51)
- **Observation**:
  1. When adding a new filtered metric tile (e.g., `W' Balance` with 5 min moving average) for `"Best Sensor"` in the Cockpit (`ATT-1296` / `ATT-4282`), `EditSensorFieldViewModel` passed the localized UI label `"Best Sensor"` as `FilterData.deviceName` instead of `null`, causing `FilterManager` to register the filter under `"Best Sensor"` while `TrackingViewModel` looked it up under `null`. Furthermore, `TimedMovingAverageFilter.getFilteredValue()` checked `mValues.isEmpty()` *before* `trimValues()`, returning `0.0 / 0 = Double.NaN` (`"--"`) whenever all samples in the window aged out.
  2. `CyclingPowerMetricsDevice` (`ATT-4283`) only seeded `currentWPrimeBalance = wPrimeCapacity` inside `resetSession()` (called by unit tests) rather than in its constructor, leaving `W' Balance` at `0 J` on startup before power samples arrived.
- **Root Cause**:
  1. Leaking a display-layer sentinel string (`context.getString(R.string.bestSensor)`) into a backend service identifier (`FilterData.deviceName`), and mutating a collection via `trimValues()` after the emptiness guard prior to dividing by `mValues.size()`.
  2. Asymmetry between constructor initialization and `resetSession()` initialization in stateful sensor/metric devices.
- **Countermeasure (`docs/coding_standards.md` Sec 1.7 & Sec 1.8, `.agents/rules/aspice_governance.md` Rules 50 & 51)**:
  - **Rule 50 (Sentinel ID Normalization & Post-Trim Empty Guard in Rolling Filters)**: ViewModels MUST normalize sentinel database IDs (`selectedDeviceId <= 0`) to `null` before constructing backend keys (`FilterData.deviceName`), never passing localized UI placeholder strings (`"Best Sensor"`) to backend services. Rolling-window filters that prune expired samples (`trimValues()`) MUST check `isEmpty()` *after* pruning before dividing by sample count.
  - **Rule 51 (Constructor State Seeding Parity with `resetSession()`)**: Stateful metric and virtual sensor devices (`CyclingPowerMetricsDevice`) MUST initialize their baseline state in the constructor (or invoke `resetSession()` from the constructor) so production startup state matches the state tested in unit tests.
