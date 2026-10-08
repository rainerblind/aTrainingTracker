# Stage 3: Implementation Plan - ATT-2629: Remove redundant Heim-Basis badge and default to location with most starts when no home-base set

**Ticket**: [ATT-2629](https://atrainingtracker.atlassian.net/browse/ATT-2629)  
**Sub-task**: [ATT-2672](https://atrainingtracker.atlassian.net/browse/ATT-2672) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-MAP-036` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment*)  
**Test Mapping**: `TST-MAP-038`  
**Branch**: `feature/ATT-2629`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

During Sprint 2026-41.1 review on a physical Pixel 10 device, two ergonomic issues were identified in the Lieblingsorte (Known Locations) screen:
1. In `KnownLocationCard.kt`, designating a location as the home-base renders both a Home icon next to the title line and a separate "Heim-Basis" badge chip in the metadata `FlowRow`. This duplicates the signal and clutters the metadata row alongside start count and route count chips.
2. In SQLite (`StartLocation2Altitude.db`), when no location is explicitly designated (`is_home = 0` for all records), the UI renders no home indicator at all. However, return navigation (`HomeLocationResolver.resolveHomeLocation()`) automatically treats the location with the most starts (`hitCount > 0`) as the effective home-base. This creates a state disconnect between the UI and the routing engine.

Expected behavior:
* Excise the redundant "Heim-Basis" text badge chip from `KnownLocationCard.kt`, retaining the prominent Home icon on the title line.
* When no explicit home location is set in SQLite, default to the location with the most workout starts (`hitCount > 0`, or fallback to the first location if all are 0) as the effective home-base, displaying the Home icon on it in the UI.
* Explicit user designation in SQLite strictly overrides the start-count default.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-036` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment*)
  - Clause 1: Unified Home Location Resolution Engine in `HomeLocationResolver.kt`.
  - Clause 2: Repository state mapping in `KnownLocationsRepository.kt`.
  - Clause 3: Redundant badge excision in `KnownLocationsScreen.kt`.
  - Clause 4: Explicit user designation override and transactional exclusivity preservation.
  - Clause 5: 100% 9-language localization and string resource integrity.
* **Test Mapping**: `TST-MAP-038`
  - `TST-MAP-038.1`: `HomeLocationResolverTest` verifying 3-tier hierarchy (explicit, highest start count, first fallback, empty list null safety).
  - `TST-MAP-038.2`: `KnownLocationsRepositoryTest` verifying `loadLocations()` assigns `isHome` to the resolved effective home ID.
  - `TST-MAP-038.3`: `KnownLocationHomeContractTest` / `KnownLocationsScreenTest` verifying badge excision and title icon retention.
  - `TST-MAP-038.4`: `TranslationParityTest` 9-language localization audit.
  - `TST-MAP-038.5`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Schema Changes**: SQLite schema version 6 and column `is_home integer default 0` in `StartLocation2Altitude.db` MUST NOT be modified.
2. **Explicit User Designation Precedence**: An explicit home set by the user (`is_home = 1` in SQLite) takes 100% precedence over start count.
3. **Transactional Exclusivity**: Setting an explicit home continues to atomically clear all other rows via `setHomeLocation(id)`. Clearing an explicit home via `clearHomeLocation()` resets all rows to 0, which reactively reverts the UI to the highest-start default.
4. **Dispatcher Confinement**: SQLite reads and writes remain strictly confined to `KnownLocationsDB-Thread` (`dbDispatcher`).
5. **Return Navigation Consistency**: "Take Me Home" navigation and `KnownLocationsRepository` resolve the exact same destination coordinate.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via transition `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2629` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `HomeLocationResolver.kt` (Domain & Navigation Engine)
* Extract core ID resolution into `@JvmStatic fun resolveHomeLocationId(locations: List<MyLocation>): Long?`:
  1. Priority 1 (Explicit): First location where `isHome == true`.
  2. Priority 2 (Start count fallback): Location with maximum `hitCount` among locations where `hitCount > 0`.
  3. Priority 3 (First fallback): First location in list if non-empty.
  4. Returns `null` if empty.
* Refactor `resolveHomeLocation(knownLocationsManager)` to delegate candidate selection directly to `resolveHomeLocationId(allLocations)`.

### Component 2: `KnownLocationsRepository.kt` (Repository & State Flow)
* In `loadLocations()`:
  - Query `rawLocations = databaseManager.allLocations`.
  - Evaluate `effectiveHomeId = HomeLocationResolver.resolveHomeLocationId(rawLocations)`.
  - In `KnownLocationItem` mapping, assign `isHome = (loc.id == effectiveHomeId)`.
* Emits reactive items to `locationsFlow` where exactly the effective home has `isHome == true`.

### Component 3: `KnownLocationsScreen.kt` (Presentation Layer)
* In `KnownLocationCard`, excise the `Surface` badge chip at lines 551–579 inside the metadata `FlowRow`:
  - Remove `if (item.isHome) Surface(...)` with test tag `location_home_badge_${item.id}` and text `@string/known_locations_home_badge`.
* Retain the clean, prominent title icon at lines 506–516 (`Icons.Default.Home`, test tag `location_home_icon_${item.id}`, tinted `MaterialTheme.colorScheme.primary`).

### UI Consistency (Rule 23)
* **Reference screen / component**: `KnownLocationsScreen.kt` (`KnownLocationCard`)
* **Reused components**: `Icon(Icons.Default.Home)`
* **Theme tokens**: `MaterialTheme.colorScheme.primary`
* **New one-off styles & justification**: None. Excising the redundant chip reduces visual noise, cleans up the metadata row, and makes the existing title icon the canonical home indicator.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `HomeLocationResolver.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolver.kt`
* Add `@JvmStatic fun resolveHomeLocationId(locations: List<MyLocation>): Long?`.
* Refactor `resolveHomeLocation()` to delegate to `resolveHomeLocationId`.

### Step 2: Update `KnownLocationsRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepository.kt`
* In `loadLocations()`, compute `effectiveHomeId = HomeLocationResolver.resolveHomeLocationId(rawLocations)` and map `isHome = (loc.id == effectiveHomeId)`.

### Step 3: Excise Redundant Badge in `KnownLocationsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* Remove the `if (item.isHome) Surface(...)` block inside `KnownLocationCard`'s `FlowRow`.

### Step 4: Update Unit & Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolverTest.kt`: Add unit tests for `resolveHomeLocationId` covering all 4 priority cases.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepositoryTest.kt`: Add test `testLoadLocations_resolvesEffectiveHomeId_fromResolverHierarchy`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationHomeContractTest.kt`: Update contract assertions to verify `location_home_icon_${item.id}` is present and `location_home_badge_${item.id}` is NOT present in `KnownLocationsScreen.kt`.

### Step 5: Execute Targeted Tests
* Run command:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.routes.HomeLocationResolverTest" \
    --tests "com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepositoryTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationHomeContractTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreenTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted unit and contract tests pass with 100% success rate.
  2. Full clean-room test suite `./gradlew testDebugUnitTest` runs with 0 regressions.
  3. Gate 4 and Gate 5 independent audits confirm compliance.
* **Rollback Plan**:
  - The feature is fully isolated on branch `feature/ATT-2629`.
  - If required, `git revert` or branch deletion returns the codebase cleanly to `sprint/2026-41.3`.
