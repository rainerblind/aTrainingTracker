# Stage 1 Analysis: ATT-7523 - ClimbDeduplicator O(N^2) Spatial Indexing & UI Lag Elimination

**Ticket**: [ATT-7523](https://rainerblind.atlassian.net/browse/ATT-7523)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7523` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
During physical Pixel 10 joint review of ATT-7311 (`REQ-CLM-011`), opening Segmente, Routen, and Anstiege (climbs) suffered multi-second UI freezes. On first open and subsequent navigation, unindexed quadratic climb comparison blocked worker threads and caused severe frame drops.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  1. *Unindexed Quadratic Spatial Comparison ($O(N^2)$)*: `ClimbDeduplicator.deduplicateForDisplay` and `isDuplicateOrOverlapping` compare all pairs without geographic bounding box pre-filtering. Distant climbs (e.g. Munich vs. Hamburg) that happen to share heading $\le 45^\circ$ and similar elevation enter full corridor inspection.
  2. *Exhaustive Cross-Track Segment Search*: In `isDuplicateOrOverlapping`, 16 sample points evaluate `distanceToSegmentMeters` across all raw polyline segments of the longer climb without segment decimation or spatial pruning ($\approx 360{,}000{,}000$ trigonometric calls for $N=300, M=500$).
  3. *Redundant UI-Layer Re-Deduplication*: `LiveClimbsRepository.refreshClimbs` deduplicates stored climbs into `_allStoredClimbs`. `MapFragmentWithTrackViewModel.mapClimbsFlow` re-executes `ClimbDeduplicator.deduplicateForDisplay` on every map layer evaluation.
* **Independent Peer Review & Verification**:
  * Verified in `ClimbDeduplicator.kt#L100-L163`: No bounding box check precedes heading/corridor calculation.
  * Verified in `MapFragmentWithTrackViewModel.kt#L139`: Redundant `deduplicateForDisplay` runs on `storedClimbs` which is already deduplicated in `LiveClimbsRepository.kt#L128`.
  * Bounding box rejection with $\ge 100\text{m}$ margin safely discards $>99\%$ of non-overlapping candidate pairs in $O(1)$ without risking false negatives.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add cached `GeoBoundingBox` on `Climb` and $O(1)$ bounding box pre-filter in `ClimbDeduplicator`.
  2. Implement polyline segment decimation / corridor candidate bounding checks in `isDuplicateOrOverlapping`.
  3. Remove redundant `deduplicateForDisplay` call in `MapFragmentWithTrackViewModel.mapClimbsFlow`.
  4. Add performance contract unit test (`TST-CLM-011.5`) asserting sub-100ms deduplication for 300 climbs.
* **Out-of-Scope Non-Goals**:
  * Modifying SQLite `Climbs.db` table schema or R-tree SQLite extensions.
  * Altering FIETS/Garmin scoring or climb detection threshold heuristics in `ClimbDetector.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refinement of `REQ-CLM-011.1` and `REQ-CLM-011.3` in `docs/requirements/req_maps_navigation.md`.
* **Historical Origin & Commit Trace**: Commit `fbcfcdeb` (`ATT-7311`, Sprint 2026-46.4).
* **Root Reason for Existing Formulation**: ATT-7311 introduced corridor deduplication to consolidate split climb segments, but lacked spatial indexing and segment bounding.
* **Preservation of Core Invariants**: 100m endpoint proximity, $45^\circ$ heading alignment, 50% elevation gain compatibility, and $\ge 70\%$ corridor overlap tolerances are strictly preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CLM-011.1` (spatial bounding box pre-filtering & decimation), `REQ-CLM-011.3` (single repository-level deduplication).
* **Affected Architecture Elements**: `Climb.kt` (`GeoBoundingBox`), `ClimbDeduplicator.kt` (bounding box overlap & segment pruning), `MapFragmentWithTrackViewModel.kt` (`mapClimbsFlow`), `ClimbsDatabaseManager.kt`.
* **Verification Impact & Test Surface**: `ClimbDeduplicatorTest.kt`, `MapFragmentWithTrackViewModelTest.kt`, `ClimbsDatabaseManagerTest.kt`, `LiveClimbsRepositoryTest.kt`.
* **Risk Assessment & Invariants**: Low-risk algorithmic optimization. Spatial margin ($\ge 100\text{m}$) prevents false-negative rejections. Thread safety and repository immutability preserved.

---

## 6. Architectural Strategy & High-Level Solution
1. **Model Layer (`Climb.kt`)**: Add cached bounding box (`minLat`, `maxLat`, `minLng`, `maxLng`) computed from start/end and pathPoints.
2. **Algorithm Layer (`ClimbDeduplicator.kt`)**: Check bounding box overlap with $100\text{m}$ padding before distance/heading checks; prune distant segments during corridor analysis.
3. **ViewModel Layer (`MapFragmentWithTrackViewModel.kt`)**: Directly stream `allStoredClimbs` without secondary deduplication.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in duplicate detection accuracy; no UI thread blocking; identical results on metric/imperial.
* **Risk Rating**: **MEDIUM** (core spatial algorithm impacting map rendering and DB insertion; mitigated by comprehensive unit test coverage).
