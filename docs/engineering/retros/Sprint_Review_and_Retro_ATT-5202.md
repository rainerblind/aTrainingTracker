# Sprint Review & Retrospective — Sprint 2026-45.2 (`ATT-5202`)

**Ticket**: [ATT-5202](https://rainerblind.atlassian.net/browse/ATT-5202)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Sprint**: `2026-45.2` (Sprint ID `394`)  
**Target Release**: `V4.9.41`  
**Branch**: `sprint/2026-45.2` $\rightarrow$ `develop`  
**Date**: 2026-11-03  

---

## 1. Sprint Velocity & Capacity Summary (`REQ-PRO-029`)

| Metric | Planned at Sprint Start | Mid-Sprint Additions (`Rule 65`) | Total Completed & Accepted (`i.O.`) | Completion Rate |
| :--- | :---: | :---: | :---: | :---: |
| **Engineering Tickets** | 14 | +1 (`ATT-5203`) | **15** | **100%** |
| **Review & Retro Ticket** | 1 (`ATT-5202`) | 0 | **1** | **100%** |
| **Total Issues** | 15 | +1 | **16** | **100%** |
| **Story Points (`customfield_10016`)** | **23 SP** (22 SP work + 1 SP retro) | **+2 SP** (`ATT-5203`) | **25 SP** | **100%** |

* **Tier 2 Sprint Integration Gate**: `4,257 tests, 0 failures, 0 ignored (100% pass rate)` (`docs/engineering/test_results/index.html`), `python3 -m unittest tools/test_jira_story_points.py` (`10/10 PASS`), and `python3 tools/verify_requirement_governance.py` (`0 errors` across 817 requirements and 816 test specs).

---

## 2. Sprint Review Summary (15 Engineering Tickets + 1 Retro Ticket)

| # | Key | SP | Summary | Commit | Review Outcome | Follow-Up Tickets |
| :-: | :--- | :-: | :--- | :---: | :--- | :--- |
| 1 | `ATT-5201` | 1 | BTLE sensor icons (`bt_*.xml`): restore official Bluetooth color (`#0A3D91`), center cyclist, and move canonical mini-icons to top-left | `43de6fd0` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 2 | `ATT-5200` | 1 | Profile & Zones (`Zonendarstellung`): rename `zone_display_background` to "Eingefärbter Hintergrund" / "Tinted background" across all 9 locales | `69e11411` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 3 | `ATT-5199` | 2 | `ElevationCache.db` & `AltitudeFromPositionDevice`: prevent inaccurate Segment/Route altitudes from overwriting or suppressing authoritative Open-Meteo `DEM_CACHE` lookups | `fa3c161b` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 4 | `ATT-5198` | 1 | Topografische Höhe (DEM) device details: explicitly mention active internet connection requirement for uncached areas in Pros & Cons (all 9 languages) | `3381f2a7` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 5 | `ATT-5197` | 2 | Tracking cockpit map (`SensorGridScreen` & `TrackingViewModel`): never show or compute Lieblingsstrecken (clusters) | `66c81245` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 6 | `ATT-5196` | 3 | Lieblingsstrecken map popup: add drag handle & expandable detail view, reuse `WorkoutClusterIdentityRow`/`MetadataBlock`, and hydrate `clusterStats` in `loadAllClustersLightweight()` | `ec65167f` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5279` |
| 7 | `ATT-5194` | 1 | `YearlyContributionGrid`: auto-scroll by default so the current month (or end of year) is shown on the very right | `f15889a3` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 8 | `ATT-5195` | 2 | Periods (Months & Weeks): embed daily activity intensity / mini-bar strip inside each sport's `SportStatsRow` (Option A) | `ca6a892f` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5280` |
| 9 | `ATT-5193` | 1 | `YearlyContributionGrid`: fix vertical clipping of day-of-week labels (e.g. `'F'`) and add `'S'` label for Sunday | `73eec633` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 10 | `ATT-5192` | 1 | `EditSensorFieldDialog`: only show `'Relative Breite'` (`1x`, `2x`, `3x`) when the row contains more than one sensor field | `20a6d928` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 11 | `ATT-5191` | 2 | `[Highest]` `FilterManager.resetAllFilters()`: unwrap `ProxySensor` so accumulator fields (`TIME_ACTIVE`, `TIME_LAP`, `DISTANCE`) re-seed to `0:00:00` instead of `--:--:--` | `1b0313c1` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 12 | `ATT-5190` | 1 | Power `TelemetryMetricGraph` marginal histogram: render unfolded 10 W bins instead of giant black Sprint-Tail `overflowBin` | `9d44d0f5` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5281` |
| 13 | `ATT-5189` | 1 | `CopyTrackingTabDialog`: do not preselect current activity type (all unselected by default) | `ad1cbebf` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 14 | `ATT-5131` | 3 | Bike Bell: force built-in speaker routing (`TYPE_BUILTIN_SPEAKER`), temporarily boost hardware volume to 100%, and implement 3-stage dB escalation | `0a1bd4da` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5282`, `ATT-5283` |
| 15 | `ATT-5203` | 2 | AAA Workflow & `jira_util.py`: Introduce 1/2/3/5+ Story Point Estimation (`customfield_10016`), Complexity Split Guardrail & Sprint Capacity Tracking | `a6db04ac` | **Accepted (`i.O.`)** (`V4.9.41`) | — |

---

## 3. Retrospective Topics & Permanent Countermeasures (`Rule 69`)

### Topic 1: Map Entity Bottom-Sheet Expansion Parity (`MapDetailLayout` Header vs. Lower Map Section — `ATT-5196` $\rightarrow$ `ATT-5279`)
* **Observation**: In `ATT-5196`, `ClusterOnMapSheet` wrapped its content in `MapDetailLayout(useStatusBarsPadding = false)` with `WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock` inside `header`, but also rendered `WorkoutClusterDetailDashboard` inside `metadataContent` while passing `showMap = false`. When expanded, the sheet showed duplicate statistics and lacked the detailed sub-entity map that `RouteOnMapScreen` and `SegmentOnMapScreen` display in the lower section.
* **Countermeasure (`Rule 69` — `docs/design_guidelines.md` Sec 5.15)**:
  * In `MapDetailLayout`-hosted map entity sheets (`SegmentOnMapScreen`, `RouteOnMapScreen`, `ClusterOnMapSheet`), the `header` slot contains the peeked summary (`IdentityRow` + `MetadataBlock`), `showMap = true` renders the detailed sub-entity map in the lower expanded area, and lower content slots must **never** duplicate metadata already shown in the `header`.

### Topic 2: Bar Chart Corner Rounding & Histogram Background Track Continuity (`ATT-5195` $\rightarrow$ `ATT-5280`, `ATT-5190` $\rightarrow$ `ATT-5281`)
* **Observations**:
  1. **`ATT-5195` $\rightarrow$ `ATT-5280` (Inverted Corner Rounding on `DailyActivityStrip` Mini-Bars)**: In `DailyActivityStrip.kt`, the outer container `Box(contentAlignment = Alignment.BottomCenter)` applied `.clip(RoundedCornerShape(2.dp / 3.dp))` to all 4 corners while the inner value bar used `RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)`. This clipped the bottom corners of the bar to `2.dp / 3.dp` while leaving the top corners nearly sharp (`1.dp`).
  2. **`ATT-5190` $\rightarrow$ `ATT-5281` (White Horizontal Stripes in `TelemetryMetricGraph` Marginal Histogram)**: Gating the `alpha = 0.15f` background container track behind `if (bin.durationSec > 0L)` caused empty intermediate 10 W bins between non-contiguous high-wattage sprint spikes (`450 W`, `490 W`, `570 W`) to skip drawing their background rect, leaving white horizontal stripes across the Zone 7 background band.
* **Countermeasures (`Rule 69` — `docs/design_guidelines.md` Sec 2.7, `.agents/skills/stage1-analysis/SKILL.md`)**:
  1. **Bottom-Anchored Vertical Bar Rounding**: Vertical bar charts anchored to a bottom baseline (`DailyActivityStrip`, histogram columns) MUST have **rounded top corners (`topStart = 2.dp..3.dp, topEnd = 2.dp..3.dp`)** and **sharp bottom corners (`bottomStart = 0.dp, bottomEnd = 0.dp`)**; outer container `Box`es must never clip bottom corners with a larger radius than the inner bar's top corners.
  2. **Unconditional Background Track Continuity in Histograms**: Marginal/zone histograms MUST render the `alpha = 0.15f` background container track unconditionally for every bin across the plotted range so empty intermediate bins never leave white stripes in the zone band; only the solid foreground bar is gated on `durationSec > 0L`.
