# Sprint Review & Retrospective — Sprint 2026-44.2 (`ATT-4936`)

**Ticket**: [ATT-4936](https://rainerblind.atlassian.net/browse/ATT-4936)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Sprint**: `2026-44.2` (Sprint ID `392`)  
**Target Release**: `V4.9.41`  
**Branch**: `sprint/2026-44.2` $\rightarrow$ `develop`  
**Date**: 2026-11-02  

---

## 1. Sprint Review Summary (23 Tickets)

| # | Key | Summary | Commit | Review Outcome | Follow-Up Tickets |
| :-: | :--- | :--- | :---: | :--- | :--- |
| 1 | `ATT-4844` | Persist timestamped zone boundary snapshots per ZoneType and resolve historical workouts against the zones active at workout time | `ecd33130` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5071` |
| 2 | `ATT-4845` | Manual altitude calibration from header Altitude icon with permanent Known Location persistence and retroactive linear drift correction | `31553134` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5072` |
| 3 | `ATT-4850` | Eliminate stationary GPS spider-map jitter, phantom speed overrides, and creeping distance when stopped or moving slowly | `fd935941` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 4 | `ATT-4859` | Implement pairable AltitudeFromPositionDevice backed by a dedicated background DEM and Route elevation cache database | `a11e13d7` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5073` |
| 5 | `ATT-4847` | Render Lieblingsstrecken (Workout Clusters) as a toggleable layer on the General Map screen and Cockpit tracking map | `4d3f24f2` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5074`, `ATT-5075` |
| 6 | `ATT-4849` | GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years) | `285d2470` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5076` |
| 7 | `ATT-4861` | Allow user-configurable horizontal width ratios (e.g. 3/4 : 1/4, 2/3 : 1/3, 1/2 : 1/2) when Cockpit sensor fields are side by side | `244300a2` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5077` |
| 8 | `ATT-4878` | Stretch Cockpit sensor field card height and border to match the tallest field in the same row | `12682cfa` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 9 | `ATT-4880` | Indirect Tire Pressure Monitoring (iTPMS): detect slow punctures and low tire pressure from GPS vs. Wheel Speed effective circumference drift | `e7cc0f39` | **Deferred Field Test** (kept in `Final Review (Human)`) | — |
| 10 | `ATT-4926` | When app is started/resumed, we show the values (time, path, ...) from the previous workout | `080d68c3` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5080` |
| 11 | `ATT-4935` | Periods heatmap: filter heatmap by sport | `dc912ae1` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 12 | `ATT-4934` | Periods map regions: show place name instead of 'Region n' | `2eac806c` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 13 | `ATT-4932` | Heatmap: count each workout once per pixel (fix bead/blob artifacts from per-segment accumulation) | `399c65cc` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 14 | `ATT-4933` | Periods heatmap look: zoom-dependent stroke width and contrasting palette vs. base map | `b20acf1d` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 15 | `ATT-4930` | Periods maps: re-introduce vertex budget after lowering simplification tolerance to 5-8 m | `2c766ca7` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 16 | `ATT-4931` | Workout Clusters heatmap unintentionally restyled by ATT-4833 shared heatmap changes | `0f9db8be` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 17 | `ATT-4929` | Periods heatmap: normalize intensity to the actual maximum corridor frequency per period | `bad11436` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 18 | `ATT-4928` | Zone gauge bar: sensor tile jumps in height when telemetry switches between live and stale | `378afea6` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 19 | `ATT-4927` | Sensor field Value/Bar: sport-dependent bar support, no empty bar-only tiles, respect previous global zone gauge setting on upgrade | `f18667c1` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 20 | `ATT-4925` | Start in Auto-Paused: workout stays auto-paused forever when no speed source is available | `534a4ab3` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 21 | `ATT-4924` | Auto-Pause settings screen: global 'Start in Auto-Pause' placement, meaningful main switch label, fixed header | `3b2ef89e` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 22 | `ATT-4923` | Live tracking: make track append, live-track decimation and sensor-field combine O(1) per tick (ATT-4863 follow-up) | `a975d6b3` | **Accepted (`i.O.`)** (`V4.9.41`) | — |
| 23 | `ATT-4922` | Google Drive queued WLAN workout uploads fail with null credential and never retry | `9a59e825` | **Accepted (`i.O.`)** (`V4.9.41`) | `ATT-5081` |

* **Tier 2 Sprint Integration Gate**: `4,174 tests, 0 failures, 0 skipped (100% pass rate)` (`docs/engineering/test_results/index.html`) + `verify_requirement_governance.py` `PASS`.

---

## 2. Retrospective Topics & Permanent Countermeasures (`Rule 67`)

### Topic 1: Canonical `docs/engineering/` Artifact Directory Structure & `test_results` Link
* **Observation**: `docs/engineering/` contained stray/legacy folders (`summaries/`, `impl_plans/`, `test_reports/`, `reviews/`, `bug_reports/`, `implementation/`, `backlog/`, a `test_spec` symlink, and 43 top-level `Sprint_Review_and_Retro_*.md` files), making the artifact layout unclear. Furthermore, the HTML test report (`app/build/reports/tests/testDebugUnitTest/index.html`) was not linked from `docs/engineering/` and had been overwritten by a single `--tests` run after the full suite finished.
* **Countermeasure (`Rule 67`)**:
  1. Consolidated all stage deliverables into the **5 canonical `docs/engineering/` subdirectories**: `analysis/`, `test_specs/`, `plans/`, `walkthroughs/`, and `retros/` (`Sprint_Review_and_Retro_<KEY>.md`).
  2. Added the directory symlink `docs/engineering/test_results -> ../../app/build/reports/tests/testDebugUnitTest` and mandated that the Tier 2 Sprint Integration Gate always finishes with an unfiltered `./gradlew testDebugUnitTest --max-workers=2` so `docs/engineering/test_results/index.html` lists all 4,174+ unit tests.
  3. Added `verify_engineering_directory_structure()` to `tools/verify_requirement_governance.py` to block any non-canonical directory or file in `docs/engineering/`.

### Topic 2: `tools/jira_util.py ensure-retro` Substring False Positive on `"retroactive"`
* **Observation**: `ensure_retro_ticket()` checked `if "retro" in summary.lower():`, matching `"retroactive"` in `ATT-4845` and falsely concluding a Retro ticket already existed.
* **Countermeasure (`Rule 67`)**: Replaced the substring check in `tools/jira_util.py` with word-boundary regex `re.search(r'\bretro(?:spective)?\b', summary, re.IGNORECASE)` and updated the default deliverable path to `docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`.

### Topic 3: `tools/verify_requirement_governance.py` Placeholder `REQ-XXX` Exclusion
* **Observation**: Git diff hunk headers in `docs/requirements.md` matched the preamble placeholder `REQ-XXX`, triggering false-positive Chesterton's Fence errors on net-new requirements.
* **Countermeasure (`Rule 67`)**: Updated `parse_diff_requirements()` in `tools/verify_requirement_governance.py` to ignore `REQ-XXX` across hunk headers, context lines, and diff lines.

### Topic 4: Shared Component Scroll-Container Audit, Broadcast Listener Ordering & Startup Self-Healing
* **Observations**:
  1. `ATT-4878` added `.fillMaxHeight()` + `Column(Modifier.weight(1f))` to `SensorFieldView.kt`, which worked inside `SensorGridScreen`'s `IntrinsicSize.Min` rows but collapsed inside `AthleteProfileScreen`'s `verticalScroll` preview (`ATT-5071`).
  2. `ATT-4926` added `InstantaneousFilter.reset() { mValue = null; }`, which ran *after* `ClockDevice`'s `RESET_ACCUMULATORS_INTENT` receiver (`0:00:00`) and overwrote `TIME_ACTIVE` with `null` (`--:--:--`), and exposed Java `null` from `DefaultStringFormatter.format(null)` to Kotlin non-null `sensorData.stringValue` (`ATT-5080`).
  3. `ATT-4922` wired `ExportManager.recoverStuckGoogleDriveExports()` into settings and active uploads, but not into app startup (`MainActivityWithNavigation`) (`ATT-5081`).
* **Countermeasures (`Rule 67` — `docs/coding_standards.md` Sec 5.7, `docs/design_guidelines.md` Sec 5.14, `stage4-implementation/SKILL.md`)**:
  1. Audit all call sites of shared composables using `.fillMaxHeight()` / vertical `.weight(1f)` across both bounded (`IntrinsicSize.Min`) and scrollable (`verticalScroll`) containers.
  2. Test multi-listener lifecycle broadcasts (`RESET_ACCUMULATORS_INTENT`, `STOP_TRACKING`) end-to-end across all registered receivers (`ClockDevice` + `FilterManager`), and enforce non-null fallbacks or `@Nullable` annotations on Java string formatters.
  3. Always wire automatic background startup self-healing (`MainActivityWithNavigation`) when recovering records stuck in terminal DB states (`FINISHED_FAILED`).
