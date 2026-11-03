# Sprint Review & Retrospective — Sprint 2026-45.1 (`ATT-5082`)

**Ticket**: [ATT-5082](https://rainerblind.atlassian.net/browse/ATT-5082)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Sprint**: `2026-45.1` (Sprint ID `393`)  
**Target Release**: `V4.9.41`  
**Branch**: `sprint/2026-45.1` $\rightarrow$ `develop`  
**Date**: 2026-11-03  

---

## 1. Sprint Review Summary (15 Work Tickets + 1 Retro Ticket)

| # | Key | Summary | Commit | Review Outcome | Follow-Up Tickets |
| :-: | :--- | :--- | :---: | :--- | :--- |
| 1 | `ATT-5068` | W' values shown (and probably also calculated) although no power available | `357331b9` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 2 | `ATT-5070` | Power zone not shown | `893fe749` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 3 | `ATT-5078` | Copy tracking tab to other sport types | `a210595d` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5189` |
| 4 | `ATT-5079` | Move tracking tab. | `12cd675e` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 5 | `ATT-5081` | Google Drive: automatically recover stuck credential-failed workout uploads on app startup without requiring manual reconnect | `6812c559` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 6 | `ATT-5080` | [Highest] Stopping a workout sets accumulator fields to --:--:-- instead of 0:00:00 and crashes TrackingViewModel with NPE on null stringValue | `9f827416` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5191` |
| 7 | `ATT-5077` | EditSensorFieldDialog: remove overflowing 'Zeilenaufteilung' row-split presets and keep only 'Relative Breite' (1x, 2x, 3x) | `fee83598` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5192` |
| 8 | `ATT-5076` | Periods (Years): Embed Yearly Activity Contribution Grid inside each sport's SportStatsRow box below main totals | `44180f27` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5193`, `ATT-5194`, `ATT-5195` |
| 9 | `ATT-5075` | Lieblingsstrecken map popup: match info and look of Lieblingsstrecken list items (and remove lila/purple color) | `95cb0048` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5196` |
| 10 | `ATT-5074` | Lieblingsstrecken map layer: disable by default and significantly reduce loading/rendering latency | `c020fa5c` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5196`, `ATT-5197` |
| 11 | `ATT-5073` | Specialized Altitude Devices: rename 'Luftdruck' to 'Höhe aus Luftdruck' (all 9 languages) and explain how they work (pros & cons) in device details | `59a1be36` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5198`, `ATT-5199` |
| 12 | `ATT-5072` | Altitude calibration dialog: make 'Save as Known Location' unchecked by default and only show/enable Location Name when checked | `f1157d75` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 13 | `ATT-5071` | Profile & Zones: Zone visualization preview tile (HR / Power) is vertically clipped/collapsed | `94d5f27b` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5200` |
| 14 | `ATT-4968` | Not tracked workout | `25f1e2c6` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 15 | `ATT-4956` | Update BTLE sensor icons: full-bleed badge, large uppercase labels (PWR/SPD/CAD/S&C/HR), and top-right sensor mini-icons | `be03a5bc` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5201` |

* **Tier 2 Sprint Integration Gate**: `4,221 tests, 0 failures, 0 skipped (100% pass rate)` (`docs/engineering/test_results/index.html`) + `verify_requirement_governance.py` `PASS`.

---

## 2. Retrospective Topics & Permanent Countermeasures (`Rule 68`)

### Topic 1: Stronger Emphasis on Side-Effects & Cross-Caller Impact (`ATT-5082` Comment 1)
* **Observations**:
  1. **`ATT-5080` $\rightarrow$ `ATT-5191` (`ProxySensor` Wrapper Around `MyAccumulatorSensor`)**: `FilterManager.resetAllFilters()` checked `if (sensor instanceof MyAccumulatorSensor)`, which passed in unit tests that mocked `MyIntegerAccumulatorSensor` directly, but failed at runtime for all `"Best Sensor"` (`deviceName == null`) tiles because `MySensorManager.getSensor(sensorType)` returns a `ProxySensor` wrapping the underlying `MyAccumulatorSensor`.
  2. **`ATT-5074` $\rightarrow$ `ATT-5196` & `ATT-5197` (Side-Effects of `loadAllClustersLightweight()` & Shared Layer State)**: Introducing `loadAllClustersLightweight()` populated `_allClusters` without populating `_clusterStats`, causing `ClusterOnMapSheet` to miss Best Time, Ascent, and Recency when the map was opened before visiting the *Lieblingsstrecken* list screen (`ATT-5196`). Similarly, wiring `mapClustersFlow` into `TrackingViewModel` caused historical *Lieblingsstrecken* to appear on the live tracking cockpit map (`ATT-5197`).
* **Countermeasures (`Rule 68` — `docs/coding_standards.md` Sec 5.8, `.agents/skills/stage1-analysis/SKILL.md`)**:
  1. **Mandatory Side-Effect & Cross-Caller Audit in Stage 1 & Stage 3**: Every Stage 1 Analysis and Stage 3 Implementation Plan MUST explicitly trace all consumers of modified repositories/flows and verify runtime wrapper/decorator types (`ProxySensor.getBestSensor()`) rather than relying on direct subclass mocks.
  2. **Companion StateFlow Hydration Parity**: Fast/lightweight repository loaders (`loadAllClustersLightweight()`) must hydrate all companion `StateFlow`s (`_clusterStats`) required by downstream UI consumers.

### Topic 2: Strictly One Ticket at a Time During Review (`ATT-5082` Comment 2)
* **Observation**: During Ceremony 2, when the user asked to revisit the previous ticket (`ATT-5075` / `ATT-5196`) while `ATT-5074` was on screen and then said *"Great. Let's go to the next one"*, the agent misinterpreted that as approving `ATT-5074` and jumped to `ATT-5073`.
* **Countermeasure (`Rule 68` — `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 Step 3)**:
  * When a turn during Ceremony 2 revisits a previous ticket (e.g., to create or update a follow-up ticket), *"Let's go to the next one"* means returning to the current unreviewed ticket — **never** approving the current ticket without explicit user evaluation.

### Topic 3: Component Reuse in Map Popups, Micro-Typography Clipping & ICC Color Fidelity
* **Observations**:
  1. `ATT-5075` built a standalone `ClusterOnMapSheet` `Column` instead of reusing `WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock` inside `MapDetailLayout(useStatusBarsPadding = false)` with `MinimumDragHandle()` like `SegmentOnMapScreen` and `RouteOnMapScreen` (`ATT-5196`).
  2. `ATT-5076` used `labelSmall.copy(fontSize = 9.sp)` inside an `11.dp` box without overriding `lineHeight = 9.sp`, clipping the bottom of `'F'` (`ATT-5193`).
  3. `ATT-4956` hand-drew top-right mini-icons instead of reusing `ic_power.xml` / `ic_speed.xml` / `ic_cadence.xml` and retained `#07377F` instead of the official `#0A3D91` Bluetooth color from `Bluetooth.svg` (`ATT-5201`).
* **Countermeasures (`Rule 68` — `docs/design_guidelines.md` Sec 5.15)**:
  * Mandate `MapDetailLayout(useStatusBarsPadding = false)` + list-item header reuse for all map entity popups, explicit `lineHeight = fontSize` + `includeFontPadding = false` for micro-typography (`<= 10.sp`), and canonical `ic_*` / `.svg` color & path reuse.
