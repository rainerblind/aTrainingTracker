# Stage 2: Requirement & Test Specification - ATT-2472: Select one of the favorite locations as home-base for return navigation

**Ticket**: [ATT-2472](https://atrainingtracker.atlassian.net/browse/ATT-2472)  
**Sub-task**: [ATT-2587](https://atrainingtracker.atlassian.net/browse/ATT-2587) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MAP-034` (Refines & Amends `REQ-MAP-029` Clause 1, interfaces with `REQ-UI-165`)  
**Test Spec ID**: `TST-MAP-036`  
**Branch**: `feature/ATT-2472`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-MAP-034)

### 1.1 Problem Statement & Rationale
In ATT-1953, "Take Me Home" return navigation was introduced (`REQ-MAP-029`). The system deduced the home destination via keyword substring matching (`"haus"`, `"home"`, `"zuhause"`). This produced frequent false positives (e.g. "Rathausplatz" or "Gasthaus" matching "haus" and overriding true home locations). Athletes need explicit control in favorite locations management to designate one location as their home-base, with clear visual indicator badges, while retaining a robust fallback to highest start frequency when no explicit home is configured.

### 1.2 Functional & Architectural Requirements
1. **Database Schema Upgrade v6 (`KnownLocationsDatabaseManager.java`, `KnownLocationsDbHelper`)**:
   - `KnownLocationsDbHelper.DB_VERSION` SHALL be upgraded to `6`.
   - Table `StartLocation2Altitude` SHALL include column `is_home integer default 0`.
   - Migration for `oldVersion < 6` SHALL execute: `ALTER TABLE StartLocation2Altitude ADD COLUMN is_home integer default 0;`.
   - `KnownLocationsDatabaseManager` SHALL provide `setHomeLocation(long id)` which atomically clears `is_home = 0` on all rows and sets `is_home = 1` for the specified `id` within an exclusive SQLite transaction block. If `id <= 0`, all locations are cleared.
   - `KnownLocationsDatabaseManager` SHALL provide `clearHomeLocation()`.
   - `KnownLocationsDatabaseManager` SHALL provide `getHomeLocation(): MyLocation?` querying `is_home = 1`.
   - `MyLocation` and `cursorToMyLocation` SHALL parse and retain `isHome: Boolean`.
2. **Repository & Domain Integration (`KnownLocationsRepository.kt`, `KnownLocationItem`)**:
   - `KnownLocationItem` SHALL expose `val isHome: Boolean = false`.
   - `KnownLocationsRepository` SHALL provide `setHomeLocation(id: Long)` and `clearHomeLocation()`, dispatching to `KnownLocationsDB-Thread` and updating `locationsFlow`.
3. **ViewModel & Presentation Layer Integration (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`, `EditKnownLocationDialog.kt`)**:
   - `KnownLocationsViewModel` SHALL expose `setHomeLocation(id: Long)` and `clearHomeLocation()`.
   - In `KnownLocationCard`, when `item.isHome == true`, the UI SHALL render a Home icon (`Icons.Default.Home`) next to the title and a dedicated Home Base Badge (`known_locations_home_badge`) in the badges row.
   - The long-press context menu of `KnownLocationCard` SHALL provide an item to set/clear home-base ("Als Heim-Basis festlegen" / "Heim-Basis aufheben").
   - `EditKnownLocationDialog` SHALL provide a toggle switch to set/remove the location as Home Base.
4. **Home Location Resolution Engine (`HomeLocationResolver.kt`)**:
   - `resolveHomeLocation(knownLocationsManager: KnownLocationsDatabaseManager): HomeDestination?` SHALL evaluate destinations in strict priority:
     - Priority 1: The location with `isHome == true`.
     - Priority 2 (Graceful fallback): The location with the highest `hitCount` (where `hitCount > 0`).
     - Priority 3: The first available location if any exists.
     - Substring keywords ("haus", "home", "zuhause") matching indiscriminately SHALL be eliminated, preventing "Rathaus" or "Gasthaus" false positives.
5. **100% 9-Language Localization Parity**:
   - String resources `known_locations_home_base`, `known_locations_home_badge`, `known_locations_set_home`, `known_locations_remove_home` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends Clause 1 of `REQ-MAP-029` (*"Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1953`, Sprint `2026-40.16`, commit `0994f794`.
* **Root Reason for Existing Formulation**: In ATT-1953, the home resolver was created to support instant "Take Me Home" functionality without requiring a configuration UI upfront. Heuristic substring matching on "haus"/"home" was used as a rapid expedient.
* **Preservation of Core Invariants**:
  - Full backward compatibility for existing `StartLocation2Altitude.db` files via standard `onUpgrade` (v5 -> v6).
  - Graceful fallback: when no location has been explicitly designated by the user, the resolver seamlessly falls back to highest `hitCount`, preserving functionality for users who do not configure an explicit home.
  - Return navigation HUD, reverse route travel, and 100% test pass rate remain completely intact.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* multiple saved locations in `KnownLocationsDatabaseManager`,
* *When* the user designates location A as Home Base,
* *Then* location A has `is_home = 1` and all other locations have `is_home = 0`.
* *Given* a designated home location A and a location B named "Rathausplatz" with higher hit count,
* *When* `HomeLocationResolver.resolveHomeLocation` is invoked,
* *Then* location A SHALL be returned as the home destination.
* *Given* no location has been explicitly designated as Home Base,
* *When* `HomeLocationResolver.resolveHomeLocation` is invoked,
* *Then* it SHALL return the location with the highest `hitCount` (where `hitCount > 0`), without matching "Rathaus" or "Gasthaus" erroneously.
* *Given* the Known Locations list,
* *When* displaying the designated home location,
* *Then* a Home icon beside the name and a dedicated Home Base Badge SHALL be visible.
* *Given* `EditKnownLocationDialog` for a location,
* *When* opened,
* *Then* a toggle switch to designate or remove Home Base status SHALL be present and functional.

---

## 2. Test Specification (TST-MAP-036)

### Test Case 1: Database Migration & Transactional Exclusivity (`TST-MAP-036.1`)
* **Scope**: Automated Unit & SQLite Migration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerHomeTest.kt`
* **Preconditions**: In-memory SQLite database initialized with version 5 schema.
* **Actions**:
  1. Trigger upgrade to v6; assert `is_home` column exists.
  2. Insert 3 locations.
  3. Call `setHomeLocation(loc1.id)`; assert loc1 is_home == 1, loc2 is_home == 0, loc3 is_home == 0.
  4. Call `setHomeLocation(loc2.id)`; assert loc1 is_home == 0, loc2 is_home == 1, loc3 is_home == 0.
  5. Call `clearHomeLocation()`; assert all locations have is_home == 0.
  6. Assert `getHomeLocation()` returns loc2 after step 4, and null after step 5.
* **Expected Result**: All assertions pass; single-home exclusivity strictly enforced.

### Test Case 2: Home Location Resolution & False Positive Prevention (`TST-MAP-036.2`)
* **Scope**: Domain Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolverTest.kt`
* **Preconditions**: Mocked `KnownLocationsDatabaseManager`.
* **Actions**:
  1. Test with designated home location: returns designated home even if another location has higher hitCount or contains "Gasthaus".
  2. Test with no designated home and locations named "Rathaus" (hitCount 2) and "Start Spot" (hitCount 10): returns "Start Spot" (highest hitCount) rather than "Rathaus".
  3. Test with no designated home and all hitCount == 0: falls back to first available location.
  4. Test with empty database: returns null safely.
* **Expected Result**: 100% pass rate; false positives completely eliminated.

### Test Case 3: Repository Reactive StateFlow (`TST-MAP-036.3`)
* **Scope**: Repository Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepositoryTest.kt`
* **Actions**:
  1. Call `setHomeLocation(id)`.
  2. Assert `locationsFlow` emits updated list with target item `isHome == true` and others `false`.
* **Expected Result**: Reactive state immediately updates.

### Test Case 4: UI & Dialog Contract Tests (`TST-MAP-036.4`)
* **Scope**: Component & Structural Contract Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationHomeContractTest.kt`
* **Actions**:
  1. Verify `KnownLocationsScreen.kt` renders Home icon and Home badge when `item.isHome == true`.
  2. Verify `KnownLocationCard` context menu contains set/clear home actions.
  3. Verify `EditKnownLocationDialog.kt` contains Home Base toggle switch.
* **Expected Result**: All contract assertions pass.

### Test Case 5: 9-Language Localization Parity Audit (`TST-MAP-036.5`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence across all 9 locales:
  - EN, DE, ES, FR, IT, JA, NL, PL, PT
  - Keys: `known_locations_home_base`, `known_locations_home_badge`, `known_locations_set_home`, `known_locations_remove_home`.
* **Expected Result**: 100% parity across all 9 locales, zero missing entries.

### Test Case 6: Full Clean-Room Regression Suite (`TST-MAP-036.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Zero regressions across full unit test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-036.1` | SQLite / Unit | `KnownLocationsDatabaseManager` | `REQ-MAP-034.1` | Specified |
| `TST-MAP-036.2` | Domain / Unit | `HomeLocationResolver` | `REQ-MAP-034.4` | Specified |
| `TST-MAP-036.3` | Repository | `KnownLocationsRepository` | `REQ-MAP-034.2` | Specified |
| `TST-MAP-036.4` | Compose Contract | `KnownLocationsScreen`, `EditKnownLocationDialog` | `REQ-MAP-034.3` | Specified |
| `TST-MAP-036.5` | Localization | `strings.xml` (all 9 locales) | `REQ-MAP-034.5`, `REQ-UI-106` | Specified |
| `TST-MAP-036.6` | Regression | Full Test Suite | `REQ-PRO-001` | Specified |
