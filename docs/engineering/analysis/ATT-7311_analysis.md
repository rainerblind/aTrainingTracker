# Stage 1 Analysis: ATT-7311 - [Bug] Deduplicate overlapping climbs in Climbs.db and map layer after historical backfill

**Ticket**: [ATT-7311](https://rainerblind.atlassian.net/browse/ATT-7311)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-7311-self-b7801336`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.3 Joint Review of ATT-6692, the Product Owner observed duplicate climbs on the map and in `Climbs.db` on device:
* Multiple duplicate climb segments appear on the map when the `GeneralMapLayer.CLIMBS` layer is enabled.
* Current spatial deduplication in `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch` relies on a strict 50m tolerance between start and summit coordinates simultaneously (`startDist <= 50m && endDist <= 50m`).
* Repeated passes, historical backfills across identical road segments, or GPX route imports versus workout GPS recordings drift by more than 50m due to GPS noise and barometric detection delays, failing to match existing climb IDs.
* Expected Behavior: Identical or heavily overlapping ascents along the same road segment must be cleanly deduplicated upon insertion and consolidated in existing databases, preventing visually overlapping duplicate climb polylines on the map.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

Forensic analysis of `ClimbsDatabaseManager.kt`, `WorkoutRepository.kt`, `LiveClimbsRepository.kt`, and `MapScreenWithTrack.kt` reveals three distinct failure mechanisms:

1. **Overly Constrained 50m Endpoint Spatial Threshold**:
   - `ClimbsDatabaseManager.insertClimbsWithDeduplication` and `insertClimbsWithDeduplicationBatch` test `startDist <= thresholdMeters && endDist <= thresholdMeters` with default `thresholdMeters = 50.0f`.
   - Real-world GPS noise on phones (10–20m jitter) combined with barometric filter lag causes `ClimbDetector.detectClimbs` start and peak triggers to vary along road corridors by 60–120m across separate rides.
   - Any variation $> 50\text{ m}$ in either start or summit causes candidate climbs on the exact same hill to fail matching, inserting redundant rows.

2. **Absence of Road Corridor / Polyline Overlap Deduplication**:
   - Two ascents along the same hill may start or end at different locations (e.g. one rider stopping 100m early or starting 120m later).
   - The engine lacked corridor/path overlap evaluation. If two climbs share $\ge 70\%$ of their trajectory in the same heading direction, they are the same ascent, yet previously both persisted.

3. **Absence of Retroactive Database Consolidation & Map Display Safeguards**:
   - Prior backfills (`backfillHistoricalClimbs`) already generated duplicates in `Climbs.db` on existing installations.
   - `MapScreenWithTrack` / `MapFragmentWithTrackViewModel` renders every entry from `LiveClimbsRepository.allStoredClimbs`, causing visual polyline stacking.

* **Independent Peer Review & Verification**:
  - Audited `ClimbsDatabaseManager.kt:134-185`: Confirmed rigid 50m check only on endpoints.
  - Audited `WorkoutRepository.kt:4150`: Confirmed explicit `thresholdMeters = 50.0f` call site.
  - Audited `MapScreenWithTrack.kt:366` & `MapContentScope.kt:565`: Confirmed all items in `uiState.climbs` render polylines without display deduplication.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  - Relax spatial endpoint deduplication tolerance and introduce corridor/overlap similarity matching in `ClimbsDatabaseManager`.
  - Provide an idempotent database deduplication consolidation routine (`deduplicateExistingClimbs`) wired into `WorkoutRepository` startup self-healing.
  - Add display-level deduplication in `LiveClimbsRepository` / `MapFragmentWithTrackViewModel` to guarantee zero overlapping climb polylines on the map.
  - Provide comprehensive unit test coverage for batch deduplication, corridor overlap, and database consolidation.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to `ClimbDetector` detection formulas or FIETS index categorization (`ATT-7162`).
  - No schema alterations to `Climbs.db` v1.
  - No changes to route navigation state machine (`LiveClimbsRepository.updateTracking`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-064.1` (*Post-Workout & Import Climb Extraction*), `REQ-MAP-066.1` (*Historical Climb Backfill*), refining `REQ-MAP-027` (*ClimbPro & Spatial Deduplication*).
* **Historical Origin & Commit Trace**: Commit `c7438e78` (`ATT-2766`), `8d42cf31` (`ATT-6691`), `045e14d4` (`ATT-1281`).
* **Root Reason for Existing Formulation**: 50m was initially chosen as a conservative proximity guess without empirical data on GPS drift between multiple rides on identical climbs.
* **Preservation of Core Invariants**: 50m remains the lower baseline, while expanded endpoint tolerance ($\le 100\text{m}$) and $\ge 70\%$ corridor overlap safely handle real-world drift; route association (`routeId`) is strictly preserved; zero schema migration required.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new requirement `REQ-CLM-011` (`REQ-CLM-011.1` Spatial Endpoint & Corridor Deduplication, `REQ-CLM-011.2` Idempotent Database Consolidation Migration, `REQ-CLM-011.3` Map Layer Display Deduplication Parity), refining `REQ-MAP-064.1` and `REQ-MAP-066.1`.
* **Affected Architecture Elements**: `ClimbsDatabaseManager.kt`, `WorkoutRepository.kt`, `LiveClimbsRepository.kt`, `MapFragmentWithTrackViewModel.kt`.
* **Verification Impact & Test Surface**: `TST-CLM-011` (`TST-CLM-011.1` Endpoint & Overlap Deduplication Test, `TST-CLM-011.2` Idempotent Database Deduplication Migration Test, `TST-CLM-011.3` Map ViewModel Display Deduplication Test, `TST-CLM-011.4` Regression Suite).
* **Risk Assessment & Invariants**: Low risk. Spatial deduplication is purely additive and consolidating; route climbs retain precedence over free-riding climbs; `Climbs.db` v1 schema remains unchanged.

---

## 6. Architectural Strategy & High-Level Solution

1. **Enhanced Deduplication in `ClimbsDatabaseManager`**:
   - Evaluate endpoint proximity with expanded threshold ($\le 100\text{ m}$).
   - Evaluate corridor overlap: for climbs with aligned heading ($\Delta \text{bearing} \le 45^\circ$), calculate polyline spatial overlap; if overlap $\ge 70\%$, treat as duplicate.
   - When candidate has `routeId` and existing entry does not, update existing record with `routeId`.
2. **Idempotent Self-Healing Migration in `WorkoutRepository`**:
   - Introduce `PREF_CLIMBS_WORKOUT_DEDUPLICATION_V3 = "climbs_workout_deduplication_v3"`.
   - On startup, execute `climbsDb.deduplicateExistingClimbs()`, removing redundant duplicate rows and refreshing `LiveClimbsRepository`.
3. **Display Deduplication in `MapFragmentWithTrackViewModel` / `LiveClimbsRepository`**:
   - Filter `allStoredClimbs` for display to ensure non-overlapping polylines on the map.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Route climb associations (`routeId`) are never lost during deduplication or consolidation.
  2. `Climbs.db` schema version 1 integrity remains intact.
  3. 100% test pass rate across existing climb suites (`ClimbsDatabaseManagerTest`, `LiveClimbsRepositoryTest`, `WorkoutClimbIngestionTest`, `WorkoutHistoricalClimbBackfillTest`).
* **Risk Rating**: **LOW** — Purely geometric and database deduplication enhancement with zero external hardware or network dependencies.
