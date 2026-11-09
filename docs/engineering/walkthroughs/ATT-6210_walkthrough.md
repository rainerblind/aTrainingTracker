# Stage 5: Walkthrough & Verification - ATT-6210: Sport Type Breakdown on KnownLocationCard

**Ticket**: [ATT-6210](https://atrainingtracker.atlassian.net/browse/ATT-6210)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6210`  
**Requirement Mapping**: `REQ-UI-470` (`REQ-UI-470.6`) | **Test Spec ID**: `TST-UI-470` (`TST-UI-470.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Scope
`KnownLocationCard` (`KnownLocationsScreen.kt`) previously displayed a static runner icon (`Icons.AutoMirrored.Filled.DirectionsRun`) before the starts count, regardless of the sport performed. In this ticket, `KnownLocationsViewModel` was enhanced with `SportStartsCount(bike, run, other)` to partition starts by `BSportType` within geofence boundaries. `KnownLocationCard` now renders active sports (`count > 0`) using authentic multi-color vector drawables (`Color.Unspecified`) in canonical order `[Bike, Run, Other]`, with graceful fallback to `0 Starts` when no workouts match.

---

## 2. On-Device Visual Inspection (Rule 71 Certified)
* Verified on physical **Pixel 10** (`66020DLCR002FL`, Android 17):
  * Navigated to *Karten -> Lieblingsorte* (`KnownLocationsScreen`).
  * Inspected cards:
    * **Zu Hause**: Renders Bike (364), Run (159), Other (10) in canonical order `[Bike, Run, Other]`.
    * **IAV**: Renders Bike (207) and Other (2). Runner is omitted due to zero starts.
  * Multi-color vector assets (`bsport_bike`, `bsport_run`, `bsport_other`) render cleanly with `tint = Color.Unspecified`.
  * Preserved unboxed bottom-anchored alignment, test tag `location_starts_badge_${item.id}`, and `onShowWorkouts` navigation action.

![Known Location Sport Starts Breakdown](file:///home/rainer/.gemini/antigravity/worktrees/b86ffd1a-5a6a-4e52-9976-b53aca20c1c8/subagent-Epic-ATT-1396-Pro-Agent-self-5415be16/docs/engineering/walkthroughs/img/ATT-6210_known_location_sport_starts.png)

---

## 3. Automated Test Execution & Scope
* **Tier 1 Targeted Test Scope**:
  * Command: `./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * **Result**: **100% PASS** executed under `parallel_sprint_lock.py` (57s compile/execution).
* **Living Documentation Governance**:
  * Command: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-45.13`
  * **Result**: **100% PASS** (all 9 domain shards synchronized, `REQ-UI-470.6` and `TST-UI-470.7` updated from `[Planned]` to `[Verified]`).

---

## 4. Acceptance Criteria Verification Table

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1 (Multi-Sport Breakdown)**: Dynamic breakdown in canonical order `[Bike, Run, Other]` | Device Inspection + Unit Test | **MET** | `KnownLocationsViewModelTest.kt:252`, `ATT-6210_known_location_sport_starts.png` (*Zu Hause*: Bike 364, Run 159, Other 10) |
| **AC-2 (Un-tinted Vector Graphics)**: Multi-color vector graphics preserved with `tint = Color.Unspecified` | Contract Test + Device Inspection | **MET** | `KnownLocationCardLayoutTest.kt:145`, `SportStartsItem` Compose tree |
| **AC-3 (Zero Starts Fallback)**: Graceful fallback to `0 Starts` when `sportStarts.total == 0` | Unit & Contract Test | **MET** | `KnownLocationsScreen.kt:949-963`, `KnownLocationCardLayoutTest.kt:110` |
| **AC-4 (Layout & Tag Invariant)**: Bottom-anchored row and test tag `location_starts_badge_${item.id}` preserved | Contract Test | **MET** | `KnownLocationCardLayoutTest.kt:133` |
| **AC-5 (Governance & Parity)**: Master tables synced, living docs verified | Governance Tool | **MET** | `verify_requirement_governance.py` PASS |
