# Stage 2: Requirement & Test Specification - ATT-2629: Remove redundant Heim-Basis badge and default to location with most starts when no home-base set

**Ticket**: [ATT-2629](https://atrainingtracker.atlassian.net/browse/ATT-2629)  
**Sub-task**: [ATT-2671](https://atrainingtracker.atlassian.net/browse/ATT-2671) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-MAP-036` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment*)  
**Test Spec ID**: `TST-MAP-038`  
**Branch**: `feature/ATT-2629`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-MAP-036)

### 1.1 Problem Statement & Rationale
Athletes managing saved favorite locations (Lieblingsorte / Known Locations) and using return navigation ("Take Me Home") require clean visual presentation and seamless alignment between the user interface and the underlying navigation routing engine:
1. In `KnownLocationCard.kt`, when a location is designated as the home-base, a Home icon (`Icons.Default.Home`) is rendered directly adjacent to the location title, while simultaneously a separate Material 3 `Surface` badge chip labeled "Heim-Basis" (`known_locations_home_badge`) is rendered in the metadata `FlowRow` below the title. This duplicate signaling adds visual noise, consumes vertical card padding, and clutters the metadata container alongside the start frequency and route count chips.
2. When no location is explicitly designated in SQLite (`is_home = 0` for all rows in `StartLocation2Altitude.db`), the UI currently does not render any Home indicator on any location. However, the return navigation engine (`HomeLocationResolver.resolveHomeLocation()`) automatically treats the location with the highest number of workout starts (`hitCount > 0`) as the effective home-base for "Take Me Home" route guidance. This produces a state mismatch: the navigation system has an active home destination, but the athlete cannot see which location is acting as their home-base in the UI.
3. When the athlete explicitly designates a location as their home-base via the context menu or edit dialog, this explicit preference must strictly supersede the start-count heuristic.

### 1.2 Functional & Architectural Requirements
The system SHALL excise the redundant "Heim-Basis" badge chip from `KnownLocationCard.kt`, unify home location resolution across domain repositories and navigation engines, and automatically display the Home icon on the effective home-base when no explicit designation exists (ATT-2629):

1. *Unified Home Location Resolution Engine (`HomeLocationResolver.kt`)*:
   - `HomeLocationResolver` SHALL expose `fun resolveHomeLocationId(locations: List<MyLocation>): Long?`.
   - It SHALL evaluate candidate locations according to a strict 3-tier priority hierarchy:
     - **Tier 1 (Explicit User Designation)**: If any location has `isHome == true`, return its `id`.
     - **Tier 2 (Workout Start Frequency Fallback)**: If no explicit designation exists, return the `id` of the location with the maximum `hitCount` among locations where `hitCount > 0`. If multiple locations share the identical maximum `hitCount > 0`, the first one encountered SHALL be returned.
     - **Tier 3 (First Location Fallback)**: If no location has `hitCount > 0`, return the `id` of the first location in the list.
     - If the list is empty, return `null`.
   - `HomeLocationResolver.resolveHomeLocation(knownLocationsManager: KnownLocationsDatabaseManager): HomeDestination?` SHALL delegate its candidate selection to `resolveHomeLocationId(rawLocations)`.

2. *Repository & Domain Model State Alignment (`KnownLocationsRepository.kt`, `KnownLocationItem`)*:
   - In `KnownLocationsRepository.loadLocations()`, the system SHALL query `rawLocations = databaseManager.allLocations`.
   - It SHALL evaluate `effectiveHomeId = HomeLocationResolver.resolveHomeLocationId(rawLocations)`.
   - When mapping `rawLocations` to `KnownLocationItem`, `KnownLocationItem.isHome` SHALL evaluate to `(loc.id == effectiveHomeId)`.
   - This ensures `locationsFlow` emits items where exactly one location has `isHome == true` whenever saved locations exist, perfectly matching the return navigation destination.

3. *Redundant Badge Excision in Presentation Layer (`KnownLocationsScreen.kt`)*:
   - In `KnownLocationsScreen.kt` (`KnownLocationCard`), the redundant `Surface` badge chip displaying `R.string.known_locations_home_badge` with test tag `location_home_badge_${item.id}` inside the metadata `FlowRow` SHALL be completely excised.
   - The primary title row Home icon (`Icons.Default.Home`) with test tag `location_home_icon_${item.id}`, tinted `MaterialTheme.colorScheme.primary`, and accessibility description `@string/known_locations_home_base` SHALL remain the single, elegant, and prominent indicator of home-base identity.

4. *Explicit User Designation & Transactional Exclusivity Preservation*:
   - Setting a home location via `KnownLocationsViewModel.setHomeLocation(id)` or `EditKnownLocationDialog` SHALL atomically mark that location as `is_home = 1` in SQLite while clearing all other rows to `is_home = 0`.
   - Clearing the home location via `clearHomeLocation()` SHALL reset all rows in SQLite to `is_home = 0`. Upon the subsequent repository refresh, the location with the highest start count (`hitCount > 0`, or first) SHALL automatically resume acting as the effective home-base, updating the UI reactively.

5. *100% 9-Language Localization & String Resource Integrity*:
   - String resource `known_locations_home_base` SHALL be preserved for the Home icon's accessibility description across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - String resource `known_locations_home_badge` MAY be retained to prevent breaking legacy references or translations, but MUST NOT be rendered in `KnownLocationCard`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines Clause 3 of `REQ-MAP-034` (*Designated Home-Base Selection for Return Navigation & Substring Disambiguation*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2472`, Sprint `2026-41.1`, commit `0994f794`.
* **Root Reason for Existing Formulation**: In ATT-2472, both the title icon and a dedicated metadata badge chip were introduced to ensure maximum visual discoverability when home-base designation was first launched. At the time, `KnownLocationsRepository` mapped `isHome = loc.isHome` directly from the raw database query, omitting the fallback logic in `HomeLocationResolver` and leaving the UI without an active indicator when no explicit home was designated.
* **Preservation of Core Invariants**:
  - Single-home transactional exclusivity in SQLite (`StartLocation2Altitude.db`).
  - Explicit user designation strictly overriding start-count heuristics.
  - Context menu and edit dialog actions (`setHomeLocation`, `clearHomeLocation`) remain 100% operational.
  - Return navigation routing integrity: `ReturnNavigationRepository` and `HomeLocationResolver` continue to guide to the exact same coordinates.
  - Thread safety: SQLite interactions remain confined to `KnownLocationsDB-Thread`.
  - 100% full-suite clean-room unit test pass rate MUST NOT be broken.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Redundant Badge Excision)**:
  * *Given* a location item with `isHome == true`,
  * *When* `KnownLocationCard` renders,
  * *Then* the Home icon beside the title SHALL be displayed, and NO "Heim-Basis" badge chip SHALL be rendered in the metadata `FlowRow`.

* **Criterion 2 (Explicit Home Designation Precedence)**:
  * *Given* saved locations where Location A has `isHome = true` (hitCount = 2) and Location B has `isHome = false` (hitCount = 50),
  * *When* `KnownLocationsRepository.loadLocations()` or `HomeLocationResolver.resolveHomeLocationId()` evaluates,
  * *Then* Location A SHALL be resolved as the home location (`effectiveHomeId == Location A.id`).

* **Criterion 3 (Default to Location with Most Starts)**:
  * *Given* saved locations where all locations have `isHome = false`, Location A has `hitCount = 10`, and Location B has `hitCount = 25`,
  * *When* `KnownLocationsRepository.loadLocations()` executes,
  * *Then* Location B SHALL have `item.isHome == true` and Location A SHALL have `item.isHome == false`.

* **Criterion 4 (Zero-Start Fallback to First Location)**:
  * *Given* saved locations where all locations have `isHome = false` and all `hitCount == 0`,
  * *When* `KnownLocationsRepository.loadLocations()` executes,
  * *Then* the first location in the list SHALL have `item.isHome == true`.

* **Criterion 5 (Empty Location Set Safety)**:
  * *Given* an empty list of saved locations,
  * *When* `HomeLocationResolver.resolveHomeLocationId(emptyList())` is called,
  * *Then* it SHALL safely return `null` without throwing an exception.

* **Criterion 6 (Reversible Explicit Designation)**:
  * *Given* Location B resolved as default home due to highest start count,
  * *When* the athlete explicitly designates Location A as home,
  * *Then* Location A becomes `isHome == true`.
  * *When* the athlete subsequently clears explicit home designation,
  * *Then* Location B automatically resumes `isHome == true`.

### 1.5 System Invariants
- Database schema `StartLocation2Altitude` and column `is_home integer default 0` (DB version 6) MUST NOT be altered.
- Geodesic return navigation mathematics, elevation-aware ETA calculation, and fork decision algorithms MUST NOT be altered.
- Single-home exclusivity: at most one location can be designated in SQLite, and exactly one location is marked as `isHome` in the UI when locations exist.
- 100% clean-room test pass rate across the full test suite.

---

## 2. Test Specification (TST-MAP-038)

### Test Case 1: `HomeLocationResolverTest_resolveHomeLocationId_hierarchy` (`TST-MAP-038.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolverTest.kt`
* **Preconditions**: In-memory `MyLocation` objects with varying `isHome` and `hitCount` values.
* **Action**:
  1. Test explicit designation: Location 1 (`isHome = true`, `hitCount = 0`), Location 2 (`isHome = false`, `hitCount = 100`). Verify returns `1L`.
  2. Test highest starts fallback: Location 1 (`isHome = false`, `hitCount = 5`), Location 2 (`isHome = false`, `hitCount = 42`). Verify returns `2L`.
  3. Test zero-start fallback: Location 1 (`isHome = false`, `hitCount = 0`), Location 2 (`isHome = false`, `hitCount = 0`). Verify returns `1L` (first).
  4. Test empty list: `emptyList<MyLocation>()`. Verify returns `null`.
* **Expected Result**: All 4 scenarios evaluate to the exact expected ID or null.

### Test Case 2: `KnownLocationsRepositoryTest_effectiveHomeResolution` (`TST-MAP-038.2`)
* **Scope**: Repository Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepositoryTest.kt`
* **Preconditions**: Mocked `KnownLocationsDatabaseManager`.
* **Action**:
  1. Setup `allLocations` with Location A (`id = 10`, `isHome = false`, `hitCount = 2`) and Location B (`id = 20`, `isHome = false`, `hitCount = 15`).
  2. Call `loadLocations()` and observe emitted items from `locationsFlow`.
  3. Verify item 20 has `isHome == true` and item 10 has `isHome == false`.
  4. Setup `allLocations` where Location A is explicitly set to `isHome = true`.
  5. Verify item 10 has `isHome == true` and item 20 has `isHome == false`.
* **Expected Result**: `KnownLocationItem.isHome` matches the effective home resolver priority.

### Test Case 3: `KnownLocationsScreenTest_redundantBadgeExcised_titleIconPreserved` (`TST-MAP-038.3`)
* **Scope**: Compose UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
* **Preconditions**: Composable `KnownLocationCard` rendered with a `KnownLocationItem` having `isHome = true`.
* **Action**:
  1. Inspect composable node tree.
  2. Assert `location_home_icon_${item.id}` is present and visible.
  3. Assert `location_home_badge_${item.id}` is NOT present in the node tree.
* **Expected Result**: Title icon is rendered, redundant badge chip is completely absent.

### Test Case 4: 9-Language Localization & Format Parity Audit (`TST-MAP-038.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/translations/TranslationParityTest.kt`
* **Action**: Execute `TranslationParityTest` asserting 100% presence and parity of `known_locations_home_base`, `known_locations_set_home`, and `known_locations_remove_home` across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Expected Result**: 100% parity, zero missing keys, zero AAPT2 formatting flaws.

### Test Case 5: Full Clean-Room Regression Suite (`TST-MAP-038.5`)
* **Scope**: Clean-room regression test
* **Action**: Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% pass rate across all test classes with zero regressions.

---

## 3. Traceability Matrix

| Requirement | Description | Test Case | Target Artifacts | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-036.1` | Unified Home Resolution Hierarchy | `TST-MAP-038.1` | `HomeLocationResolver.kt` | `Specified` |
| `REQ-MAP-036.2` | Repository Reactive State Alignment | `TST-MAP-038.2` | `KnownLocationsRepository.kt` | `Specified` |
| `REQ-MAP-036.3` | Redundant Badge Excision | `TST-MAP-038.3` | `KnownLocationsScreen.kt` | `Specified` |
| `REQ-MAP-036.4` | Explicit User Designation Override | `TST-MAP-038.1`, `TST-MAP-038.2` | `KnownLocationsRepository.kt`, `HomeLocationResolver.kt` | `Specified` |
| `REQ-MAP-036.5` | 9-Language Localization Parity | `TST-MAP-038.4` | `strings.xml` (all 9 locales) | `Specified` |
| `REQ-PRO-001` | Full Clean-Room Regression Suite | `TST-MAP-038.5` | Entire test suite | `Specified` |
