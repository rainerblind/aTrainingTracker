# Walkthrough — ATT-6047: Encapsulate `WorkoutCluster` Centroid/Altitude/Bounds Math & Fix Move/Delete Extrema

- **Jira Ticket**: `ATT-6047` (`3 SP`, Medium/P2, Epic `ATT-6033`)
- **Target Release**: `V5.0.0`
- **Living Specs**: `REQ-REV-017` (`REQ-REV-017.1`..`REQ-REV-017.3`) <-> `TST-REV-018` (`TST-REV-018.1`..`TST-REV-018.3`) — `[Verified]`
- **Code Commit (Gate 4 Certified)**: `e9af7578` (`ATT-9407`) on `feature/ATT-6047` (`Rule 7.5` doc-only Stage 5: `0` changes to `app/` post-`e9af7578`)
- **Localization**: `Localization Parity: N/A - no new or modified UI strings`

## 1. Root Cause & Implemented Changes
1. **`WorkoutCluster` Domain Encapsulation (`WorkoutClusterDatabaseManager.kt`, `776 -> 857` LoC, `+81` LoC — `REQ-REV-017.1`)**:
   - Added pure `WorkoutCluster.withAddedWorkout(...)` and `WorkoutCluster.withRemovedWorkout(...)` backed by private `addWeightedCoord(...)` and `removeWeightedCoord(...)`.
   - Preserves caller `hitCount` semantics (`withAddedWorkout` leaves `hitCount` unchanged for SQLite reconciliation; `withRemovedWorkout` supports `decrementHitCount: Boolean = false` and guards `hitCount <= 1`), handles nullable `minAltLat/Lng` & `maxAltLat/Lng` coordinates, and expands bounds via `minOf`/`maxOf`.
2. **Engine Delegation, Move/Delete Extrema & Deduplication (`WorkoutClusterEngine.kt`, `1,634 -> 1,563` LoC, `-71` LoC — `REQ-REV-017.2`, `REQ-REV-017.3`)**:
   - Delegated all 5 arithmetic callers (`learnFromWorkout`, `onWorkoutFinished`, `migrateHistory`, `moveWorkoutToCluster`, `onWorkoutDeleted`) to `withAddedWorkout`/`withRemovedWorkout`.
   - Updated `moveWorkoutToCluster` to fetch `SensorType.ALTITUDE` `MIN`/`MAX` + `getWorkoutBounds(workoutId)` using `WorkoutSummaries.BOUND_*` keys (fixing the latent `"BoundMinLat"` vs `"boundMinLat"` key mismatch in `moveWorkoutToCluster`, `migrateHistory`, and `createNewClusterFromWorkout`), apply `withRemovedWorkout` (source) & `withAddedWorkout` (target) before `assignClusterToWorkout`, and run `recalculateClusterAnchors(context, refreshedOld)` afterward to shrink source bounds and refresh sport distribution (`refreshedNew` sport frequency refreshed too).
   - Updated `onWorkoutDeleted` to reverse nullable in-memory `w.minAltitudeLatLng`/`w.maxAltitudeLatLng` via `withRemovedWorkout(..., decrementHitCount = true)` before `recalculateClusterAnchors` when `isAnchor`.
   - Consolidated both `createNewClusterFromWorkout` overloads into `createClusterFromMetadata(...)` and deduplicated hit-count decrement via `reconcileRemovedClusterHitCount(...)`.
   - Conformance maintained: `1,563 <= 1,635` monolith ceiling in `ArchitectureConformanceContractTest`, `clusterLock` preserved, `0` `.ui.*` imports (`REQ-PRO-130.2`).

## 2. Verified Test & Governance Evidence (`Rule 7.5` Reuse @ `e9af7578`)
*Scope: Non-UI database/engine ticket (APK deployment skipped per `stage5-verification` Section 2). Stage 4 & Gate 4 (`ATT-9407` @ `e9af7578`) ran Tier 1 targeted unit/contract suites with `--rerun` and `4/4` Python safety linters; `app/` is bit-for-bit identical since `e9af7578`.*

| Verification Gate / Suite | Result | Key `TST-REV-018` Coverage |
| :--- | :---: | :--- |
| Gate 4 (`ATT-9407` @ `e9af7578`) | `106/106` PASS, `4/4` linters PASS | Targeted cluster & architecture contract suites + secrets/cursor/lifecycle/enum safety linters |
| Stage 4 Targeted Batch (24 suites) | `131/131` PASS (`0` fail, `0` skip) | `WorkoutClusterApexTest` (`9/9`: `testWithAddedWorkout_*`, `testWithRemovedWorkout_*`, `testOnWorkoutDeleted_reversesAltitudeExtremaFromInMemorySnapshot`), `EditWorkoutClusterTest` (`11/11`: `testMoveWorkoutToCluster_updatesAltitudeExtremaAndRecalculatesBounds`), `ArchitectureConformanceContractTest` (`11/11`), `WorkoutClusterDeleteTest` (`2/2`), 20 `WorkoutCluster*` suites (`98/98`) |
| `WorkoutClusterPreviewPersistenceTest` (isolated) | `1/1` PASS | Clean isolated pass (batch run hit pre-existing `Dispatchers.IO`/`MutableStateFlow` conflation race on `coldStatuses`) |
| Requirement Governance (`d60bf458` base) | EXIT `0` | `--sync-indexes` + `--base-ref d60bf458 --check-verified-evidence --test-results-dir /tmp/empty_check_6047` |
