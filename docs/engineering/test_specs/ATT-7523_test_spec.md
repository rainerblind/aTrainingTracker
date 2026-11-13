# Stage 2: Requirement & Test Specification - ATT-7523: ClimbDeduplicator Spatial Indexing & UI Lag Fix

**Ticket**: [ATT-7523](https://rainerblind.atlassian.net/browse/ATT-7523) | **Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7523`  
**Requirement Mapping**: `REQ-CLM-011` (`REQ-CLM-011.1`, `REQ-CLM-011.3`) | **Test Spec ID**: `TST-CLM-011` (`TST-CLM-011.1`..`TST-CLM-011.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-CLM-011 Refinement)

### 1.1 Problem Statement & Rationale
Unindexed $O(N^2)$ climb comparison and exhaustive cross-track polyline segment traversal in `ClimbDeduplicator`, combined with redundant re-deduplication in `MapFragmentWithTrackViewModel`, cause multi-second UI freezes when opening Segmente, Routen, and Anstiege on device.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional & Performance Non-Functional
- **Verification Method**: Unit & Performance Benchmark

### 1.3 Functional & Architectural Requirements
1. **Bounding Box Pre-Filtering (`REQ-CLM-011.1`)**: `Climb` SHALL provide a cached `GeoBoundingBox` covering start, summit, and path points. `ClimbDeduplicator` SHALL reject climb pairs whose bounding boxes (with $\ge 100\text{m}$ margin) do not overlap in $O(1)$ time without calculating spherical distance or polyline segments.
2. **Segment Pruning & Decimation (`REQ-CLM-011.1`)**: In `isDuplicateOrOverlapping`, polyline corridor analysis SHALL prune segments outside candidate bounding boxes to eliminate $O(N \cdot M)$ trigonometric operations.
3. **Single-Pass Display Caching (`REQ-CLM-011.3`)**: `LiveClimbsRepository` SHALL publish pre-deduplicated climbs in `allStoredClimbs`. `MapFragmentWithTrackViewModel.mapClimbsFlow` SHALL emit `allStoredClimbs` directly without executing secondary `deduplicateForDisplay` passes.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (O(1) Bounding Box Discard)**:
  * *Given* two geographically separated climbs (e.g. bounding boxes $> 100\text{m}$ apart),
  * *When* `isDuplicateOrOverlapping` or `deduplicateForDisplay` evaluates the pair,
  * *Then* it returns `false` immediately without evaluating headings or polyline cross-track distances.
* **Criterion 2 (Dense Performance Benchmark)**:
  * *Given* a dense synthetic list of 300 climbs with 500-point polylines,
  * *When* `ClimbDeduplicator.deduplicateForDisplay` executes,
  * *Then* deduplication completes in $\le 100\text{ ms}$ on JVM without thread starvation.

### 1.5 System Invariants
* Endpoint proximity ($\le 100\text{m}$), heading ($\le 45^\circ$), elevation ($\le 50\%$), and corridor overlap ($\ge 70\%$) thresholds are strictly preserved.
* `Climbs.db` SQLite schema v1 remains unchanged; zero UI thread blocking; 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-CLM-011.1` and `REQ-CLM-011.3` in `docs/requirements/req_maps_navigation.md`.
* **Historical Origin & Commit Trace**: Commit `fbcfcdeb` (`ATT-7311`, Sprint 2026-46.4).
* **Root Reason for Existing Formulation**: ATT-7311 introduced corridor deduplication to consolidate split climb segments, but lacked spatial bounding box pre-filtering and segment decimation.
* **Preservation of Core Invariants**: 100m endpoint proximity, $45^\circ$ heading alignment, 50% elevation gain compatibility, and $\ge 70\%$ corridor overlap tolerances are strictly preserved.

---

## 2. Test Specification (TST-CLM-011)

### Test Cases
* **`TST-CLM-011.1`**: Spatial endpoint and corridor overlap deduplication in `ClimbsDatabaseManagerTest` and `ClimbDeduplicatorTest`.
* **`TST-CLM-011.2`**: Idempotent database consolidation migration in `WorkoutHistoricalClimbBackfillTest` and `ClimbsDatabaseManagerTest`.
* **`TST-CLM-011.3`**: Map ViewModel display deduplication parity in `MapFragmentWithTrackViewModelTest` (single-tier emission).
* **`TST-CLM-011.4`**: Targeted regression verification suite across climbs, aftermath, map, and database packages.
* **`TST-CLM-011.5`**: Performance benchmark & bounding box pre-filtering contract test in `ClimbDeduplicatorTest` ($\le 100\text{ms}$ for 300 climbs).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CLM-011.1` | Unit | `ClimbDeduplicator.isDuplicateOrOverlapping` | `REQ-CLM-011.1` | Specified |
| `TST-CLM-011.2` | DB / Migration | `ClimbsDatabaseManager.deduplicateExistingClimbs` | `REQ-CLM-011.2` | Specified |
| `TST-CLM-011.3` | ViewModel | `MapFragmentWithTrackViewModel.mapClimbsFlow` | `REQ-CLM-011.3` | Specified |
| `TST-CLM-011.4` | Regression | Targeted Climb & Map test suite | `REQ-CLM-011` | Specified |
| `TST-CLM-011.5` | Benchmark | `ClimbDeduplicator.deduplicateForDisplay` | `REQ-CLM-011.1`, `REQ-CLM-011.3` | Specified |
