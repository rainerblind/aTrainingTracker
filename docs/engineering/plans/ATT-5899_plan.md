# Stage 3: Implementation Plan - ATT-5899: Point-to-Point Corridor Discrimination & Elliptical Apex

**Ticket**: [ATT-5899](https://atrainingtracker.atlassian.net/browse/ATT-5899) | **Target Release**: `V5.0.0` | **Sprint**: `2026-45.13` | **Branch**: `feature/ATT-5899`  
**Requirement Mapping**: `REQ-SET-097` | **Test Mapping**: `TST-SET-097` | **Author**: AI Agent 1 (Implementer, `subagent_pro`)  

---

## 1. Problem Description & Architectural Context
On point-to-point (A $\rightarrow$ B) routes, defining Apex (`maxDispLat/Lng`) as the coordinate maximizing distance from `start` causes the apex to collapse onto `end` ($apex \approx end$). This renders the 20% apex similarity term ($s_3$) a duplicate of the end term ($s_2$), blinding `WorkoutClusterEngine` from discriminating parallel route corridors (e.g., eastern forest vs. central city between Sindelfingen and Schönaich) sharing identical endpoints, distance, and elevation.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-097` (*Point-to-Point Corridor Discrimination & Non-Degenerate Elliptical Apex*)
  * `REQ-SET-097.1`: Non-degenerate elliptical apex calculation ($S(P) = d(\text{start}, P) + d(P, \text{end})$) for $d(\text{start}, \text{end}) \ge 150\text{ m}$ with 50% midpoint fallback for straight paths.
  * `REQ-SET-097.2`: Live session finalization synchronization in `TrackerService.java`.
  * `REQ-SET-097.3`: Polyline corridor deviation tie-breaking in `scoreClusters` / `suggestCluster` via `WorkoutClusterCorridorDetector`.
  * `REQ-SET-097.4`: 100% loop-route ($d(\text{start}, \text{end}) < 150\text{ m}$) backward compatibility.
* **Test Mapping**: `TST-SET-097` (`TST-SET-097.1` to `TST-SET-097.4`).

---

## 3. System Invariants & Preserved Behavior
1. **Loop-Route Backward Compatibility**: When $d(\text{start}, \text{end}) < 150\text{ m}$, elliptical formulation $d(\text{start}, P) + d(P, \text{end}) \equiv 2 \cdot d(\text{start}, P)$ preserves legacy apex and similarity scores identically.
2. **Deterministic Arithmetic Means**: Cluster apex running averages across member workouts remain intact under `clusterLock`.
3. **No Unintended Cluster Splitting**: Standard similarity scoring threshold (`1.2`) and macro parameters in `ClusterTuningScreen` remain unmodified.
4. **Non-Blocking Scoring**: Polyline station sampling and corridor distance evaluations run in $< 1\text{ ms}$ on `Dispatchers.Default`.

---

## 4. Proposed Architectural Changes
* **Component 1: Non-Degenerate Apex Calculation (`WorkoutClusterEngine.kt`)**: Add `@JvmOverloads fun findApexFromPoints(start: LatLng, points: List<LatLng>, end: LatLng? = null): LatLng`. When `end != null` and $d(\text{start}, \text{end}) \ge 150\text{ m}$, evaluate $S(P) = d(\text{start}, P) + d(P, \text{end})$. If $\max S(P) \ge d(\text{start}, \text{end}) + 20\text{ m}$, return the maximizing coordinate; else return the coordinate nearest the 50% cumulative distance midpoint.
* **Component 2: Session Finalization Sync (`TrackerService.java`)**: In `finalizeLiveSession()`, pass `endPos` to `WorkoutClusterEngine.findApexFromPoints(startPos, points, endPos)` so newly recorded point-to-point sessions persist non-degenerate apexes in `WorkoutSummaries`.
* **Component 3: Corridor-Aware Scoring (`WorkoutClusterEngine.kt`)**: In `scoreClusters` / `calculateSimilarity`, add optional `workoutPolyline: List<LatLng>? = null` (or `mapPolyline: String? = null`). When candidate clusters have $s_1 \le 0.5$ and $s_2 \le 0.5$ and `previewPaths` are present, compute 5-station corridor deviation via `WorkoutClusterCorridorDetector.samplePolylineStations` to rank the true corridor variant first.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Elliptical Apex Calculation**: Implement non-degenerate `findApexFromPoints(start, points, end)` in `WorkoutClusterEngine.kt`.
* **Step 2: Apex Unit Tests**: Author `WorkoutClusterPointToPointApexTest.kt` verifying point-to-point arc, straight line, and loop course compatibility (`TST-SET-097.1`).
* **Step 3: TrackerService Integration**: Update `TrackerService.java:2758-2771` to pass `endPos` to `findApexFromPoints` and persist non-degenerate apexes (`TST-SET-097.3`).
* **Step 4: Corridor Scoring Discrimination**: Implement corridor deviation tie-breaking in `WorkoutClusterEngine.scoreClusters` / `calculateSimilarity` using `WorkoutClusterCorridorDetector`.
* **Step 5: Corridor Discrimination Unit Tests**: Author `WorkoutClusterCorridorScoringTest.kt` asserting that parallel Sindelfingen $\rightarrow$ Schönaich variants (forest vs. city) are accurately discriminated (`TST-SET-097.2`).
* **Step 6: Regression Verification**: Run Tier 1 targeted test suite (`TST-SET-097.4`) and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`WorkoutClusterPointToPointApexTest`, `WorkoutClusterCorridorScoringTest`, `WorkoutClusterApexTest`), living doc governance check, and AAPT2 build verification.
* **Rollback**: Clean git restore on `feature/ATT-5899` without database migrations.
