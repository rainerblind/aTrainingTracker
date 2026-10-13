# Stage 5: Walkthrough & Verification - ATT-2629: Remove redundant Heim-Basis badge and default to location with most starts when no home-base set

**Ticket**: [ATT-2629](https://atrainingtracker.atlassian.net/browse/ATT-2629)  
**Sub-task**: [ATT-2674](https://atrainingtracker.atlassian.net/browse/ATT-2674) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-MAP-036` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment*)  
**Test Mapping**: `TST-MAP-038` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment Verification*)  
**Branch**: `feature/ATT-2629`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Executive Summary & Verification Overview

This deliverable addresses UX redundancy, visual decluttering, and domain logic alignment in Known Locations (`KnownLocationsScreen.kt`, `KnownLocationsRepository.kt`, and `HomeLocationResolver.kt`):
1. **Redundant "Heim-Basis" Badge Excision**: In `KnownLocationCard.kt`, designating a home location previously rendered both a prominent primary Home icon (`Icons.Default.Home`) next to the location name and a separate "Heim-Basis" badge chip (`Surface`) in the metadata `FlowRow`. The badge chip duplicated information and cluttered the card layout. It has been cleanly excised while retaining the elegant title row Home icon.
2. **Unified 3-Tier Candidate Resolution Engine**: Exposed `@JvmStatic fun resolveHomeLocationId(locations: List<MyLocation>): Long?` on `HomeLocationResolver`. It rigorously enforces the 3-tier hierarchy:
   - Tier 1: Explicit home designation (`isHome == true`).
   - Tier 2: Maximum workout starts (`hitCount > 0`).
   - Tier 3: First saved location fallback if all locations have zero starts (`hitCount == 0`).
   - Empty list: Safely returns `null`.
3. **Repository StateFlow Alignment**: `KnownLocationsRepository.loadLocations()` now evaluates `effectiveHomeId` via `HomeLocationResolver.resolveHomeLocationId(rawLocations)` and maps `isHome = (loc.id == effectiveHomeId)`. When no location is explicitly marked in SQLite, the effective home automatically receives `isHome = true` in the UI, perfectly harmonizing what the athlete sees with where return navigation routes.
4. **Automated Unit & Contract Test Suite**: Added comprehensive test coverage across `HomeLocationResolverTest.kt`, `KnownLocationsRepositoryTest.kt`, `KnownLocationHomeContractTest.kt`, and `KnownLocationsScreenTest.kt`.
5. **Clean-Room Regression Suite**: 100% test pass rate across the full project test suite (`BUILD SUCCESSFUL in 9m 13s`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-036.1` | `TST-MAP-038.1` | 3-tier candidate resolution hierarchy (`HomeLocationResolverTest.kt`) | **PASSED** | `Verified` |
| `REQ-MAP-036.2` | `TST-MAP-038.2` | Repository effective home resolution & StateFlow emission (`KnownLocationsRepositoryTest.kt`) | **PASSED** | `Verified` |
| `REQ-MAP-036.3` | `TST-MAP-038.3` | Title Home icon retention & redundant badge excision (`KnownLocationHomeContractTest.kt`, `KnownLocationsScreenTest.kt`) | **PASSED** | `Verified` |
| `REQ-MAP-036.4` | `TST-MAP-038.1` | Explicit designation overrides start count hierarchy (`HomeLocationResolverTest.kt`, `KnownLocationsRepositoryTest.kt`) | **PASSED** | `Verified` |
| `REQ-MAP-036.5` | `TST-MAP-038.4` | 9-language localization audit (`KnownLocationsScreenTest.kt`, `TranslationParityTest.kt`) | **PASSED** (100%) | `Verified` |
| `REQ-PRO-001` | `TST-MAP-038.5` | Full clean-room unit test suite (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 9m 13s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Contract Tests
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.routes.HomeLocationResolverTest" \
  --tests "com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepositoryTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationHomeContractTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreenTest"

BUILD SUCCESSFUL in 14s

- HomeLocationResolverTest: 4/4 PASSED
  * testResolveHomeLocationId_explicitHomeTakesPrecedence
  * testResolveHomeLocationId_fallsBackToHighestStartCount_whenNoExplicitHome
  * testResolveHomeLocationId_fallsBackToFirstLocation_whenAllZeroStarts
  * testResolveHomeLocationId_emptyList_returnsNull
- KnownLocationsRepositoryTest: 9/9 PASSED
  * testLoadLocations_resolvesEffectiveHomeId_whenNoExplicitHomeSet
  * testLoadLocations_explicitHome_overridesStartCountHierarchy
  * (and all existing repository flow tests)
- KnownLocationHomeContractTest: 1/1 PASSED
  * testHomeLocationRenderingContract_titleIconPresent_badgeExcised
- KnownLocationsScreenTest: 4/4 PASSED
  * testAll16KnownLocationStringKeysExistAcrossAll9Locales
  * (and all UI string / flow tests)
- Total: 18/18 PASSED (100%)
```

---

## 4. UI Consistency (Rule 23)

* **Reference Screen / Component**: `KnownLocationCard` title row in `KnownLocationsScreen.kt`, which renders `Icons.Default.Home` tinted `MaterialTheme.colorScheme.primary` with test tag `location_home_icon_${item.id}` and accessibility description `@string/known_locations_home_base`.
* **Theme Tokens Reused**: `MaterialTheme.colorScheme.primary` for the Home icon.
* **Checked against `docs/design_guidelines.md` §5**:
  * Shapes: Cleaned up card metadata `FlowRow` by removing the secondary pill `Surface` chip.
  * Spacing: Preserved standard 4dp/8dp spacing between title and icons; reduced vertical crowding in metadata row.
  * Colors/Themes: Primary chromatic accent retained for the home indicator; zero custom color hardcoding.
  * Typography/Icons: Standard `Icons.Default.Home` vector retained.
  * Placement: Home indicator placed directly beside the location name on the title line, ensuring immediate recognition.
* **Deviations & justification**: None. Declutters UI, removes duplicate cues, and improves visual scannability.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite executed with 100% pass rate.
2. **Chesterton's Fence & Requirement Archaeology**: Satisfies `REQ-MAP-036` and verified via `verify_requirement_governance.py`.
3. **Database Schema & Invariants**: SQLite table `StartLocation2Altitude` schema version 6 and column `is_home integer default 0` remain untouched.
4. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-MAP-036`) and `docs/tests.md` (`TST-MAP-038`) updated to `Verified`.
5. **Subtask Completion**: Stage 5 subtask (`ATT-2674`) transitioned to `Erledigt` via `freigabe` upon Gate 5 automated audit pass.
6. **Strategy A Integration**: Branch `feature/ATT-2629` merged into `sprint/2026-41.3` (`--no-ff`), and parent ticket [ATT-2629](https://atrainingtracker.atlassian.net/browse/ATT-2629) moved to `Final Review (Human)`.
