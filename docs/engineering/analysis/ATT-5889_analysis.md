# Stage 1: Problem Domain & Root Cause Analysis - ATT-5889

**Ticket**: [ATT-5889](https://rainerblind.atlassian.net/browse/ATT-5889)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396): Lieblingsorte (Known Locations, Geofences & Auto-Naming) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5889`  
**Requirement Mapping**: `REQ-SET-095` (*Known Locations Merge & Geofence Reconciliation*), refining `REQ-DAT-016`, `REQ-UI-165`, `REQ-MAP-034`  
**Test Mapping**: `TST-SET-062` (*Known Locations Merge Suite*)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
When GPS fix accuracy varies across sessions, duplicate `KnownLocation` (*Lieblingsorte*) entries are recorded for the same physical location (e.g. driveway vs. doorway). Currently, `KnownLocationsScreen` only offers deletion and editing. Deleting duplicate location $S$ drops its geofence coverage, detaching historical workouts and route clusters. Athletes need an intuitive, directional **Merge** capability ($S \rightarrow T$) that calculates a combined hybrid geofence, preserves `Heim-Basis` / locked altitude pins, reconciles workout start counts authoritatively without double-counting, and updates route clusters reactively.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (4-Augen-Prinzip)
* **Forensic RCA**:
  - `KnownLocationsDatabaseManager.java`: Only provides single-row CRUD (`updateLocation`, `deleteId`); lacks transactional `mergeLocations(...)` supporting atomic coordinate, radius, altitude, and home-base updates.
  - `KnownLocationsRepository.kt`: Lacks atomic merge dispatch and post-merge cross-database reconciliation.
  - `KnownLocationsViewModel.kt`: Does not expose candidate proximity filtering ($d \le 2\,000\text{ m}$) or merge coordination.
  - `KnownLocationsScreen.kt`: Context menu lacks merge action; lacks candidate picker and confirmation dialogs with radius preview.
* **Independent Hypothesis Verification & Code Audit**:
  - **Geometry Calculation**: Pure geodesic interpolation (`SphericalUtil.interpolate` / haversine math) guarantees proper minimum bounding circle (MBC) center and radius ($r_{\text{new}} = \lceil (d + r_T + r_S)/2 \rceil$) without JVM mock dependencies.
  - **Pinned Target Safeguard**: For `target.isHome || target.isLocked`, center remains fixed ($C_{\text{new}} = C_T$) and radius expands to $r_{\text{new}} = \max(r_T, \lceil d + r_S \rceil)$, preserving athlete reference points.
  - **Hit Count Double-Count Prevention**: Re-running `reconcileHitCountsWithWorkoutSummaries` on $T$ re-evaluates all historical workout start points within $(C_{\text{new}}, r_{\text{new}})$, preventing arithmetic addition duplicates ($N_T + N_S$).

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  1. Add *"Zusammenführen…"* action to `KnownLocationCard` context menu.
  2. Implement proximity candidate selection dialog ($d \le 2\,000\text{ m}$, sorted ascending by raw numeric distance per `ATT-3324`).
  3. Implement merge confirmation dialog with radius preview ($r_{\text{new}}$) and coordinate pinning notice (`ATT-3355`).
  4. Implement `KnownLocationsDatabaseManager.mergeLocations(...)` executing atomic SQLite updates in `StartLocation2Altitude.db`.
  5. Authoritative hit count reconciliation via `reconcileHitCountsWithWorkoutSummaries` and reactive repository refresh.
  6. 100% 9-language localization parity across all merge dialog strings without emojis (`ATT-5400`).
* **Out-of-Scope Non-Goals**:
  - Automated background location deduplication or clustering.
  - Redesigning `KnownLocationCard` metrics or FAB (reserved for `ATT-5887` and `ATT-5884`).
* **Complexity Rating**: **3 SP (Complex)**. Cross-layer feature spanning SQLite database transaction, geodetic geometry, ViewModel state, and Compose dialogs. Single ticket capacity.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-095` (net-new), refining `REQ-DAT-016` (*Authoritative Workout Start Reconciliation*), `REQ-UI-165` (*Known Locations Management UI*), and `REQ-MAP-034` (*Home Base Location*).
* **Historical Origin & Commit Trace**: `ATT-1447` (start location creation), `ATT-1734` (authoritative hit count reconciliation), `ATT-5666` (cluster merge architecture), `ATT-5764` (home base single-exclusivity).
* **Root Reason for Existing Formulation**: Prior tickets focused on creation, single-home exclusivity, and individual deletion. Merging completes lifecycle parity with `Lieblingsstrecken` (`WorkoutClusters`).
* **Preservation of Core Invariants**: Pinned target invariant preserves `isHome` and user-calibrated altitudes (`isLocked`); single-home exclusivity is maintained; all SQLite writes remain confined to `KnownLocationsDB-Thread`.

---

## 5. Architectural Strategy & High-Level Solution
* **Geometry Engine**: `KnownLocationsGeometryMath.calculateMergedGeofence(source, target)` computes $(C_{\text{new}}, r_{\text{new}})$ based on pinned status and distance $d$.
* **Data Layer**: `KnownLocationsDatabaseManager.mergeLocations(...)` updates $T$ and deletes $S$ inside `beginTransaction() / endTransaction()`, followed by `reconcileHitCountsWithWorkoutSummaries(...)`.
* **Repository / ViewModel**: `KnownLocationsRepository.mergeLocations(...)` dispatches on `dbDispatcher` and reloads state; `KnownLocationsViewModel` exposes candidate list and merge action.
* **UI Layer**: `KnownLocationCard` dropdown $\rightarrow$ `KnownLocationMergeSelectionDialog` $\rightarrow$ `KnownLocationMergeConfirmDialog` $\rightarrow$ `viewModel.mergeLocations(source, target)`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Single-home exclusivity (`isHome`); locked altitude immutability; authoritative start count derived from actual workouts; strict raw numeric sorting (`ATT-3324`); explicit user confirmation (`ATT-3355`).
* **Risk Rating**: **MEDIUM** (Atomic SQLite transaction and geodetic math, shielded by confirmation dialog and single-thread SQLite serialization).
