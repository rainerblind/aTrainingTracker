# Stage 1 Analysis: ATT-6210 - Breakdown of Workout Starts by Sport Type on KnownLocationCard

**Ticket**: [ATT-6210](https://atrainingtracker.atlassian.net/browse/ATT-6210)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6210`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Context**: `KnownLocationCard` (`KnownLocationsScreen.kt`) renders a static runner icon (`Icons.AutoMirrored.Filled.DirectionsRun`) before the total starts count (e.g. `🏃 12 Starts`), regardless of which sports actually started at that location.
* **Defect**: For locations predominantly used for cycling (e.g. *IAV*, *Zu Hause*) or multi-sport activities, a static runner icon is misleading and hides the athletic breakdown of the location.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `ATT-5887` introduced the unboxed starts metric with a hardcoded `DirectionsRun` icon and single integer count (`startsCount: Int`).
  * In-memory aggregation grouped starts by location ID without partitioning by sport type.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `KnownLocationsViewModel.kt:512-544`: `groupStartsByLocation` already evaluates distance between each `workout.startLatLng` and `location.latLng`.
  * Inspected `WorkoutData.kt:72`: Each `WorkoutData` object holds authentic `val bSportType: BSportType`.
  * Inspected `BSportType.java:25-30`: Maps to multi-color vector drawables `R.drawable.bsport_bike`, `R.drawable.bsport_run`, `R.drawable.bsport_other`.
  * Inspected `design_guidelines.md` §1.2: Enforces canonical order (`Bike`, `Run`, `Other` on far right). Un-tinting with `Color.Unspecified` preserves authentic multi-color vector graphics.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Define `SportStartsCount(bike: Int, run: Int, other: Int)` and compute breakdown in `KnownLocationsViewModel`.
  * Expose `sportStartsByLocationId: Map<Long, SportStartsCount>` in `KnownLocationsUiState`.
  * In `KnownLocationCard`, display sports with `count > 0` in canonical order `[Bike, Run, Other]` using `R.drawable.bsport_*` with `tint = Color.Unspecified`.
  * Preserve fallback for `0 Starts` when no workouts match.
  * Preserve clickable drill-down (`onClick = onShowWorkouts`) and `location_starts_badge_${item.id}` test tag.
  * Update contract tests in `KnownLocationCardLayoutTest.kt` and `KnownLocationsScreenTest.kt`.
* **Out-of-Scope**: Database schema modifications, changing workout recording, or altering cluster cards.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-UI-469.2` (*Unboxed Starts Metric on KnownLocationCard*).
* **Historical Origin & Commit Trace**: `e14d805f` (`ATT-5887`) unboxed the starts badge with `DirectionsRun`.
* **Root Reason for Existing Formulation**: Static runner icon was an interim placeholder before sport breakdown was requested.
* **Preservation of Core Invariants**: Unboxed bottom-anchored alignment, test tag `location_starts_badge_${item.id}`, and `onShowWorkouts` navigation action strictly preserved.
* **Side-Effect Audit (Rule 68)**: Zero SQLite schema changes; `KnownLocationsDatabaseManager` untouched; `startsByLocationId` preserved for sorting and filtering; zero regression on map previews or routes cross-links.

---

## 5. Architectural Strategy & High-Level Solution
* **ViewModel Aggregation**: In `groupStartsByLocation`, partition matched workouts by `bSportType` into `SportStartsCount(bike, run, other)`, storing both total starts and sport breakdown.
* **Composable Presentation**: In `KnownLocationCard`, if `sportStarts.total > 0`, render each sport with `count > 0` as a compact row item `[bsport_logo] count` in canonical order `[Bike, Run, Other]`. If `sportStarts.total == 0`, show `startsCount` / `0 Starts`.
* **Visual Parity**: Multi-color drawables rendered un-tinted via `tint = Color.Unspecified`, matching app sport selector headers.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Active Sprint `2026-45.13`; 100% test pass in `KnownLocationCardLayoutTest` and `KnownLocationsScreenTest`.
* **Risk Rating**: **LOW** — In-memory aggregation and leaf Composable UI refinement with zero database or service side-effects.
