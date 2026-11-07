# Stage 3: Implementation Plan - ATT-5889: Known Locations Merge with Hybrid Geofence Geometry

**Ticket**: [ATT-5889](https://rainerblind.atlassian.net/browse/ATT-5889)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5889`  
**Requirement Mapping**: `REQ-SET-095` (`REQ-SET-095.1`–`REQ-SET-095.6`)  
**Test Mapping**: `TST-SET-095` (`TST-SET-095.1`–`TST-SET-095.6`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
When GPS fix accuracy varies across sessions, duplicate `KnownLocation` (*Lieblingsorte*) records are created for the same location. Deleting duplicate $S$ drops its geofence coverage, detaching workouts and route clusters. Athletes need an intuitive merge workflow from `KnownLocationCard`'s context menu: choosing from candidates within $2\,000\text{ m}$ (sorted by raw distance), reviewing the combined radius in a confirmation dialog, atomically consolidating attributes (`isHome`, `isLocked`, `altitude`, `source`), updating target geofence geometry, deleting $S$, and authoritatively reconciling workout hit counts without double-counting.

---

## 2. Invariants & Preserved Behavior
1. **Pinned-Target Invariant (`REQ-SET-095.4`, AC 2)**: When `target.isHome == true` or `target.isLocked == true`, target coordinates $C_T$ remain strictly pinned ($C_{\text{new}} = C_T$) and radius expands to $r_{\text{new}} = \max(r_T, \lceil d + r_S \rceil)$.
2. **Minimum Bounding Circle Geometry (`REQ-SET-095.4`, AC 1)**: When target is unlocked and non-home, if one circle encloses the other, adopt the enclosing circle; otherwise set $r_{\text{new}} = \lceil (d + r_T + r_S)/2 \rceil$ and interpolate center along the geodesic toward source by $(d + r_S - r_T)/2$.
3. **Database Transaction & Single-Home Exclusivity (`REQ-SET-095.5`)**: Consolidation of target and deletion of source are enclosed in an atomic SQLite transaction in `StartLocation2Altitude.db`. Single-home invariant is preserved: `newIsHome = target.isHome || source.isHome`.
4. **Authoritative Hit Count Derivation (`REQ-SET-095.5`, AC 3)**: Target `hitCount` is derived directly from `WorkoutSummaries.db` via `reconcileHitCountsWithWorkoutSummaries`, never by naive arithmetic sum ($N_T + N_S$).
5. **Numeric Primitive Ranking & Confirmation Guard (`ATT-3324`, `ATT-3355`)**: Candidate sorting strictly evaluates raw numeric primitives (`Double` meters), not formatted strings. Merge commits only after explicit confirmation in `AlertDialog`.
6. **Code Lock (Rule 4)**: Zero production source code edits until Gate 3 audit passes.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Patterns**: `DeleteConfirmationDialog` and `WorkoutClustersScreen` merge workflow (`ATT-5666`).
* **Candidate Selection**: `KnownLocationMergeSelectionDialog` implemented via `AppModalBottomSheet` with empty state when no locations within $2\,000\text{ m}$.
* **Confirmation Dialog**: M3 `AlertDialog` displaying source $\rightarrow$ target names, new radius preview formatted via `DistanceFormatter`, and pinned-target notice if applicable.
* **Iconography**: Material `Icons.Default.CallMerge` for the context menu item.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Geofence Geometry Engine (`REQ-SET-095.4`, `TST-SET-095.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsGeometryMath.kt`
* **Changes**: Implement `calculateMergedGeofence(source: KnownLocationItem, target: KnownLocationItem): MergedGeofence` handling Pinned-Target Mode, containment, and MBC geodesic interpolation with JVM unit test safety.

### Step 2: Atomic SQLite Merge & Hit Count Reconciliation (`REQ-SET-095.5`, `TST-SET-095.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java`
* **Changes**: Add `mergeLocations(...)` executing target update and source deletion within `beginTransaction() / endTransaction()`, followed by `reconcileHitCountsWithWorkoutSummaries(...)`.

### Step 3: Repository & ViewModel Orchestration (`REQ-SET-095.2`, `REQ-SET-095.5`, `TST-SET-095.3`)
* **Files**:
  - `KnownLocationsRepository.kt`: Add `mergeLocations(source, target)` on `dbDispatcher` coordinating geometry calculation, DB transaction, and state reload.
  - `KnownLocationsViewModel.kt`: Expose `getCandidateLocationsForMerge(source)` filtering $d \le 2\,000\text{ m}$ (raw distance sorting) and `mergeLocations(source, target)`.

### Step 4: UI Context Menu & Candidate Picker / Confirmation Dialogs (`REQ-SET-095.1`, `REQ-SET-095.2`, `REQ-SET-095.3`, `TST-SET-095.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* **Changes**: Add merge action to `KnownLocationCard` context menu, integrate `KnownLocationMergeSelectionDialog` and confirmation `AlertDialog` with radius preview and pinned notice.

### Step 5: Unit & Contract Tests (`TST-SET-095.1`–`TST-SET-095.6`)
* **Files**:
  - `KnownLocationsGeometryMathTest.kt`: Verify MBC (AC 1) and Pinned Target (AC 2) geometry.
  - `KnownLocationsDatabaseManagerTest.kt`: Verify atomic merge transaction and authoritative hit count reconciliation (AC 3).
  - `KnownLocationsViewModelTest.kt`: Verify candidate proximity filtering ($2\,000\text{ m}$) and raw distance sorting (AC 4).
  - `KnownLocationsMergeDialogContractTest.kt`: Verify UI dialog flow and state clearing.

---

## 5. Verification Commands (`TST-SET-095.6`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.KnownLocations*" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`
* `python3 tools/verify_requirement_governance.py`
