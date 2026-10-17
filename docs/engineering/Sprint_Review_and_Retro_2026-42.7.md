# Sprint Review & Retrospective — Sprint 2026-42.7

**Date**: 2026-10-17  
**Sprint**: `2026-42.7` (`sprint/2026-42.7`, Jira Sprint ID `374`)  
**Target Release**: `V4.9.40`

---

## 1. Sprint Review Summary

### 1.1 Accepted Tickets (`FixVersion: V4.9.40`)
1. **ATT-2859**: *Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets* — **Accepted** *(Follow-up: `ATT-3926`)*
2. **ATT-3852**: *Replace flat 40-item SensorType dropdown in EditSensorFieldDialog with categorized and searchable sensor value selector* — **Accepted**
3. **ATT-3775**: *Subscribe reactively to currentSpeed and currentBearing in TrackingViewModel.observeMapState so Follow-Me zoom and bearing update continuously* — **Accepted** *(Follow-up: `ATT-3927`)*
4. **ATT-3850**: *Apply bottom system navigation bar inset padding to AthleteProfileScreen scrollable content* — **Accepted**
5. **ATT-3851**: *Restructure and declutter Navigation Drawer items and sections* — **Accepted** *(Follow-up: `ATT-3928`)*
6. **ATT-3853**: *Show time percentage below each zone segment in ZoneDistributionStackedBar when sufficient horizontal space is available* — **Accepted**
7. **ATT-3862**: *Support Critical Power (CP), W' (Anaerobic Work Capacity), and W'bal (Anaerobic Battery) in Athlete Profile, Live Cockpit, and Aftermath* — **Accepted** *(Follow-up: `ATT-3929`)*
8. **ATT-3861**: *Calculate and display Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) in Workout Summaries, Details, and Imports* — **Accepted**
9. **ATT-3860**: *Calculate and persist power-based training metrics (NP, IF, TSS, Work kJ, W/kg) when importing TCX, GPX, or FIT workout files* — **Accepted**
10. **ATT-3859**: *Add Performance Management Chart (PMC) for Fitness (CTL), Fatigue (ATL), and Form / Race-Readiness (TSB)* — **Accepted**
11. **ATT-3858**: *Display power-based training metrics (NP, IF, TSS, Work kJ, W/kg, VI) in Workout Summaries and Workout Details* — **Accepted** *(Style guide updated with Section 5.10 + Follow-up: `ATT-3930`)*

### 1.2 Follow-Up Tickets Created at Top of Backlog
- **ATT-3930**: *Redesign WorkoutDetails power and physiology rows to follow the AltitudeRow + MetricItem pattern (Section 5.10) aligned with ATT-3929 schools*
- **ATT-3929**: *Add 'Physiology & Training Models' section in Advanced Settings to toggle training science schools (Coggan NP/IF/TSS, PMC, Aerobic Decoupling EF/Pw:Hr, Critical Power CP/W'/W'bal)*
- **ATT-3928**: *Move 'Profile & Zones' to Training drawer section and make 'My Stuff' collapsible by default*
- **ATT-3927**: *Fix TCX replay speed inflation and timeline progression for variable-interval GPS tracks in tools/replay_workout.py*
- **ATT-3926**: *Make inner dial of bsport_other.xml stopwatch icon transparent to match legacy grey/active rendering*

---

## 2. Key Retrospective Learnings & Root Cause Analysis

### Learning 1: Follow-Up Tickets Must Always Be Placed at the Top of the Backlog
- **Observation**: Follow-up tickets created during Sprint Review (or during ticket discussions) must always be placed at the very top of the backlog so they are immediately visible for the next sprint screening.
- **Root Cause**:
  1. Jira's `POST /rest/api/2/issue` endpoint places newly created issues at the **bottom** of the backlog by default.
  2. `tools/jira_util.py create-issue` did not automatically rank new tickets at the top of the backlog, relying instead on manual ad-hoc Agile API calls after creation.
  3. While `.agents/rules/aspice_governance.md` Rule 8 mentioned ranking review tickets at the top of the backlog, Rule 5 and Rule 9 did not state it explicitly.
- **Countermeasure (`.agents/rules/aspice_governance.md` Rules 5, 8, 9 & `tools/jira_util.py`)**:
  - Updated `tools/jira_util.py` with `rank_at_top_of_backlog(issue_key)` (and CLI command `rank-top <KEY>`), invoked **automatically by default inside `create_issue(...)`** so every created follow-up ticket is programmatically ranked before the #1 item on the Jira Agile board backlog.
  - Explicitly codified the top-of-backlog requirement across **Rule 5**, **Rule 8**, and **Rule 9** in `.agents/rules/aspice_governance.md`.

### Learning 2: Canonical `AltitudeRow` + `MetricItem` Pattern & Progressive Disclosure for Physiological Schools (`ATT-3858`, `ATT-3861`, `ATT-3862` -> `ATT-3929`, `ATT-3930`)
- **Observation**:
  1. Simultaneous display of four physiological training "schools" (Coggan `NP/IF/TSS/VI`, `PMC`, Aerobic Decoupling `EF/Pw:Hr`, and Critical Power `CP/W'/W'bal`) overwhelms athletes who only want standard metrics.
  2. In `WorkoutDetails.kt`, the new power and physiology sections were rendered as a 3-column `FlowRow` (`PowerMetricsSection`) and standalone `Card` blocks (`AerobicEfficiencySection`, `CriticalPowerSection`) instead of matching the existing `AltitudeRow` (`24.dp` leading icon + 2–3 equal-weight `MetricItem` columns + `0.5.dp` divider).
- **Root Cause**:
  - `docs/design_guidelines.md` lacked an explicit section codifying `AltitudeRow` + `MetricItem` as the mandatory row template for multi-metric rows in `WorkoutDetails.kt`.
- **Countermeasure (`docs/design_guidelines.md` Section 5.10 / Rule 42, `ATT-3929`, `ATT-3930`)**:
  - Immediately added **Section 5.10 (`WorkoutDetails & Summary Card Grouped Metric Rows — AltitudeRow Pattern`)** to `docs/design_guidelines.md` and mapped it as **Rule 42** in `.agents/rules/aspice_governance.md`.
  - Created **`ATT-3929`** (4 independent toggles in Advanced Settings for the physiological schools, with Coggan `ON` by default and `PMC`, `Aerobic Decoupling`, and `Critical Power` opt-in `OFF` by default while continuing background DB calculation) and **`ATT-3930`** (redesigning each school's `WorkoutDetails` presentation as a single `AltitudeRow`-style row with its 2–3 primary metrics).
