# Stage 2: Requirement & Test Specification - ATT-5889: Known Locations Merge with Hybrid Geofence Geometry & Cross-Database Reconciliation

**Ticket**: [ATT-5889](https://rainerblind.atlassian.net/browse/ATT-5889)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-SET-095` (*Known Locations Merge with Hybrid Geofence Geometry & Cross-Database Reconciliation*)  
**Test Spec ID**: `TST-SET-095`  
**Branch**: `feature/ATT-5889`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-SET-095)

### 1.1 Problem Statement & Rationale
When GPS fix accuracy varies across sessions, duplicate `KnownLocation` (*Lieblingsorte*) records are created for the same location. Deleting duplicate $S$ drops its geofence coverage, detaching workouts and route clusters. The system SHALL support merging $S \rightarrow T$ from the location card context menu with proximity filtering ($d \le 2\,000\text{ m}$), hybrid geofence radius preview, pinned-target preservation (`isHome`/locked altitude), atomic SQLite consolidation, authoritative start count reconciliation without double-counting, and reactive UI refresh.

### 1.2 Functional & Architectural Requirements
1. *Card Context Menu Action (`REQ-SET-095.1`)*: `KnownLocationCard` context menu SHALL offer *"Mit anderem Ort zusammenführen…"* (`R.string.known_locations_menu_merge`, `Icons.Default.CallMerge`) to initiate merge of source $S$.
2. *Proximity Candidate Selection (`REQ-SET-095.2`)*: Target picker SHALL display candidate locations ($T \ne S$) within geodetic distance $d \le 2\,000\text{ m}$, sorted ascending by raw numeric distance primitive (`ATT-3324`), displaying distance label (`R.string.known_locations_merge_distance_away`) or empty state (`R.string.known_locations_merge_no_candidates`).
3. *Explicit Merge Confirmation & Radius Preview (`REQ-SET-095.3`)*: Selecting $T$ SHALL present an `AlertDialog` (`ATT-3355`) previewing resulting radius $r_{\text{new}}$ and displaying pinned notice (`R.string.known_locations_merge_pinned_notice`) if $T$ has `isHome == true` or `isLocked == true`.
4. *Hybrid Geofence Geometry (`REQ-SET-095.4`)*:
   - *Pinned Target*: When $T.\text{isHome} == \text{true}$ or $T.\text{isLocked} == \text{true}$, center remains fixed ($C_{\text{new}} = C_T$) and radius expands to $r_{\text{new}} = \max(r_T, \lceil d + r_S \rceil)$.
   - *Minimum Bounding Circle*: When $T$ is unlocked and non-home, if one circle encloses the other, adopt it; else shift center along geodesic toward $S$ by $\frac{d + r_S - r_T}{2}$ and set $r_{\text{new}} = \lceil \frac{d + r_T + r_S}{2} \rceil$.
5. *Transactional Storage & Reconciliation (`REQ-SET-095.5`)*:
   - Atomically update $T$ with $(C_{\text{new}}, r_{\text{new}}, \text{altitude}, \text{isLocked}, \text{source}, \text{isHome} = T.\text{isHome} \lor S.\text{isHome})$ and delete $S$ in `StartLocation2Altitude.db`.
   - Recompute $T.\text{hitCount}$ via `reconcileHitCountsWithWorkoutSummaries` across all workout starts within $(C_{\text{new}}, r_{\text{new}})$ without double-counting; reload `KnownLocationsRepository`.
6. *9-Language Localization & Parity (`REQ-SET-095.6`)*: 100% parity across all 9 locales with zero inline emojis (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (MBC Merge)**: *Given* unlocked, non-home $S$ and $T$ ($r_S = 200\text{ m}, r_T = 200\text{ m}, d = 200\text{ m}$), *when* merged, *then* center moves to midpoint, $r_{\text{new}} = 300\text{ m}$, and $S$ is deleted.
* **AC 2 (Pinned Target)**: *Given* $T$ with `isHome == true` ($r_T = 200\text{ m}$) and $S$ ($r_S = 200\text{ m}, d = 150\text{ m}$), *when* merged, *then* $C_{\text{new}} = C_T$, $r_{\text{new}} = 350\text{ m}$, and `isHome == true` is preserved.
* **AC 3 (Reconciliation)**: *Given* workouts in $S$ and $T$, *when* merged, *then* $T.\text{hitCount}$ reconciles authoritatively and route clusters reflect $T$.
* **AC 4 (Dialog Flow)**: *Given* source $S$, *when* merge is triggered, *then* candidates within $2\,000\text{ m}$ are listed (sorted by raw distance) and confirmation previews $r_{\text{new}}$.

### 1.4 System Invariants & Requirement Archaeology
* **Invariants**: Single-home exclusivity, locked altitude immutability, authoritative start count derivation, raw numeric sorting (`ATT-3324`), explicit confirmation (`ATT-3355`), `KnownLocationsDB-Thread` confinement.
* **Archaeology**: Net-new requirement refining `REQ-DAT-016`, `REQ-UI-165`, `REQ-MAP-034`.

---

## 2. Test Specification (TST-SET-095)
* **`[TST-SET-095.1]` Geometry Unit Tests**: Midpoint, radius expansion, containment, and pinned target immutability in `KnownLocationsGeometryMathTest.kt`.
* **`[TST-SET-095.2]` Database Transaction & Reconciliation Tests**: Atomic update of $T$, deletion of $S$, and authoritative start count reconciliation in `KnownLocationsDatabaseManagerTest.kt`.
* **`[TST-SET-095.3]` ViewModel Candidate Filtering & Sorting Tests**: Proximity filtering ($d \le 2\,000\text{ m}$), raw distance sorting (`ATT-3324`), and empty state handling in `KnownLocationsViewModelTest.kt`.
* **`[TST-SET-095.4]` Merge UI Flow Contract Tests**: Context menu trigger, candidate picker, confirmation preview, and state reset in `KnownLocationsMergeDialogContractTest.kt`.
* **`[TST-SET-095.5]` Localization Parity Audit**: All 8 merge string keys verified across all 9 locales with zero inline emojis in `TranslationParityTest.kt`.
* **`[TST-SET-095.6]` Targeted Regression Suite**: 100% pass rate across `knownlocations.*`, `database.*`, `build.*`, `localization.*`.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-095.1]` | Unit | `KnownLocationsGeometryMath.calculateMergedGeofence` | `REQ-SET-095.4` | Specified |
| `[TST-SET-095.2]` | Database | `KnownLocationsDatabaseManager.mergeLocations` | `REQ-SET-095.5` | Specified |
| `[TST-SET-095.3]` | ViewModel | `KnownLocationsViewModel.getCandidateLocationsForMerge` | `REQ-SET-095.2` | Specified |
| `[TST-SET-095.4]` | UI Contract | `KnownLocationsScreen` merge dialog flow | `REQ-SET-095.1`, `REQ-SET-095.3` | Specified |
| `[TST-SET-095.5]` | Localization | `TranslationParityTest` | `REQ-SET-095.6` | Verified |
| `[TST-SET-095.6]` | Regression | Targeted package test suite | `REQ-PRO-001` | Specified |
