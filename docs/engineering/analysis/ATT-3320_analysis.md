# Stage 1 Analysis: ATT-3320 - Configurable auto-switch to unique route in advanced navigation settings

**Ticket**: [ATT-3320](https://rainerblind.atlassian.net/browse/ATT-3320)  
**Sub-task**: [ATT-3337](https://rainerblind.atlassian.net/browse/ATT-3337) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3320`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Statement & Motivation

When an athlete tracks a workout with multiple active routes selected simultaneously, the application continuously monitors candidate trajectories at trail forks. Under the current autonomous route binding mechanism in `ForkNavigationRepository.kt`, as soon as the athlete commits to a specific branch and the alternative candidate routes diverge by more than 50 meters, the app automatically switches active navigation to lock onto the chosen route via:
```kotlin
routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)
```

While this automatic route snapping is convenient for athletes following an intended branch hands-free, athletes who intentionally load multiple alternative loops, interconnected trail options, or exploration tracks frequently prefer to keep all candidate routes active and visible on the map without the app making the decision for them or locking turn-by-turn guidance to a single route.

Providing a dedicated toggle in **Advanced Settings -> Navigation** gives athletes full control over this behavior, preserving autonomous switching as the default while allowing athletes to disable auto-switching if desired.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap)

Forensic inspection of `ForkNavigationRepository.kt` (lines 138–151) reveals the current unconditional autonomous route binding:

```kotlin
if (chosenRoute != null) {
    // Verify alternative candidate branches have cross-track error > 50m
    val otherCandidates = candidates.filter { it.summary.id != chosenRoute.summary.id }
    val alternativesFar = otherCandidates.isEmpty() || otherCandidates.all { other ->
        val projOther = ForkRouteMatcher.projectOntoPolyline(currentPos, other.path)
        projOther.crossTrackDistanceMeters > AUTO_BIND_MIN_CROSS_TRACK_ALTERNATIVE_METERS
    }

    if (alternativesFar) {
        routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)
        _forkDecisionState.value = null
        return
    }
}
```

1. **Unconditional Binding Mechanism**:
   When `alternativesFar == true`, `ForkNavigationRepository` directly invokes `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)` without checking any user preference.
2. **Missing Configuration Hook in Preferences**:
   `TuningPreferencesDefaults`, `TuningConfig`, and `TuningPreferencesDataStore` lack an `auto_follow_unique_route_enabled` preference key.
3. **Repository Dependency Gap**:
   `ForkNavigationRepository` currently injects `RoutesRepository` and `BANALServiceRepository`, but does not inject or observe `TuningPreferencesDataStore` or `TuningConfig`.
4. **UI Gap in Navigation Tuning**:
   `NavigationSection.kt` exposes parameters for route selection radius, navigation cue transparency/dismiss duration, and map follow-me camera settings, but has no toggle for fork route auto-switching.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Preference Architecture**:
    * Add `DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = true` to `TuningPreferencesDefaults`.
    * Add `KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = booleanPreferencesKey("tuning_auto_follow_unique_route_enabled")` in `TuningPreferencesDataStore`.
    * Expose `val autoFollowUniqueRouteEnabled: Boolean` in `TuningConfig` (default `true`).
    * Add `suspend fun updateAutoFollowUniqueRouteEnabled(enabled: Boolean)` to `TuningPreferencesDataStore` and include the key in `resetToDefaults()`.
  * **UI Integration**:
    * In `NavigationSection.kt`, render a `TuningToggleItem`:
      * Title: `tuning_auto_follow_unique_route_title` ("Auto-follow unique route").
      * Summary: `tuning_auto_follow_unique_route_desc` ("Automatically switch to follow a route when it becomes the only matching route at a fork.").
      * Checked state: `autoFollowUniqueRouteEnabled`.
    * Wire state through `AdvancedTuningDialog.kt`.
  * **Repository Binding Guard**:
    * Update `ForkNavigationRepository` constructor to accept `TuningPreferencesDataStore? = null` (and expose `@VisibleForTesting var autoFollowUniqueRouteEnabled: Boolean`).
    * In `getInstance(context)`, pass `TuningPreferencesDataStore(context.applicationContext)`.
    * When `alternativesFar == true`:
      * If `autoFollowUniqueRouteEnabled == true`: invoke `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)`.
      * If `autoFollowUniqueRouteEnabled == false`: suppress calling `setActiveNavigatedRoute`, clear `_forkDecisionState.value = null`, and return.
  * **Localization Parity**:
    * Add string resources across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  * **Verification**:
    * Add unit tests in `ForkNavigationRepositoryTest.kt` verifying both enabled (auto-binds) and disabled (suppresses auto-bind, dismisses alert) behaviors.
    * Add unit tests in `TuningConfigTest.kt` and `TuningPreferencesDataStore` test suites for persistence, mutation, and factory reset.
    * Update living documentation (`docs/requirements.md` / `REQ-MAP-031` and `docs/tests.md` / `TST-MAP-033`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to fork divergence detection algorithms in `RouteDivergenceDetector.kt` or corridor matching in `ForkRouteMatcher.kt`.
  * No alterations to manual route selection via `ForkDecisionCard.kt` (`selectRouteManually(routeId)`).
  * No changes to turn-by-turn prompt engine (`TurnByTurnNavigationEngine.kt`).
  * No database schema migrations or changes to `RouteSummary` / Room entities.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*), amending Clause 4, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.16 (`ATT-1955`, commit `34c752d2` / `419e6ebe`).
* **Root Reason for Existing Formulation**: In ATT-1955, autonomous route binding was hardcoded to automatically switch navigation to the chosen route (`routesRepository.setActiveNavigatedRoute(...)`) whenever an athlete progressed >= 50m past a fork and alternative candidate routes diverged by > 50m. This was designed for hands-free navigation along an intended single course. However, athletes who intentionally load multiple candidate routes or alternative exploration tracks prefer not to have the app unilaterally lock onto a single route.
* **Preservation of Core Invariants**: Default behavior remains enabled (`autoFollowUniqueRouteEnabled = true`), preserving 100% backward compatibility for athletes who want automatic snapping. When disabled, the fork decision card is dismissed once branches diverge beyond 50m without locking `activeNavigatedRouteId`, leaving candidate routes selected and visible. Offline operation, pure geodesic calculations, and 100% clean-room test pass rate remain preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Preference Store (`TuningPreferencesDataStore.kt`)**:
   ```kotlin
   // TuningPreferencesDefaults
   const val DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = true

   // TuningConfig
   val autoFollowUniqueRouteEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED

   // TuningPreferencesDataStore
   val KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED: Preferences.Key<Boolean> =
       booleanPreferencesKey("tuning_auto_follow_unique_route_enabled")
   ```

2. **Repository Guard (`ForkNavigationRepository.kt`)**:
   ```kotlin
   if (alternativesFar) {
       if (autoFollowUniqueRouteEnabled) {
           routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)
       }
       _forkDecisionState.value = null
       return
   }
   ```

3. **Navigation Tuning UI (`NavigationSection.kt` & `AdvancedTuningDialog.kt`)**:
   Add `TuningToggleItem` within the Navigation category in `NavigationSection.kt`, providing clear localized title and description.

4. **9-Language Parity**:
   Define `tuning_auto_follow_unique_route_title` and `tuning_auto_follow_unique_route_desc` in all 9 locale resource files.

---

## 6. Verification & Test Plan

1. **Unit Tests**:
   - `ForkNavigationRepositoryTest.kt`:
     - Test `onLocationChanged_pastForkOnBranchA_whenAutoFollowEnabled_autoBindsRouteA()`: confirms `setActiveNavigatedRoute` is called when `autoFollowUniqueRouteEnabled == true`.
     - Test `onLocationChanged_pastForkOnBranchA_whenAutoFollowDisabled_suppressesAutoBind()`: confirms `setActiveNavigatedRoute` is NOT called when `autoFollowUniqueRouteEnabled == false`, and `forkDecisionState` is reset to `null`.
   - `TuningConfigTest.kt` / `TuningPreferencesDataStoreTest`:
     - Verify default value is `true`.
     - Verify updating through DataStore mutator persists.
     - Verify reset to defaults clears the key and restores `true`.
2. **Clean-Room Regression**:
   - Full suite execution: `./gradlew testDebugUnitTest`.
3. **Living Documentation & Governance**:
   - Update `docs/requirements.md` and `docs/tests.md`.
   - Run `tools/verify_requirement_governance.py`.
