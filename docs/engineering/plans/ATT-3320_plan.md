# Stage 3: Implementation Plan - ATT-3320: Configurable auto-switch to unique route in advanced navigation settings

**Ticket**: [ATT-3320](https://rainerblind.atlassian.net/browse/ATT-3320)  
**Sub-task**: [ATT-3339](https://rainerblind.atlassian.net/browse/ATT-3339) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MAP-031` (amending Clause 4), `REQ-SET-073`  
**Test Mapping**: `TST-MAP-033` (amending Clause 3)  
**Branch**: `feature/ATT-3320`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Description & Background

When athletes track a workout with multiple active routes selected simultaneously, the application continuously evaluates candidate trajectories at trail forks. In `ForkNavigationRepository.kt`, when the athlete commits to a specific branch and the alternative candidate routes diverge by more than 50 meters, the app automatically switches active navigation to lock onto the chosen route (`routesRepository.setActiveNavigatedRoute(...)`).

While this automatic route snapping is convenient for hands-free navigation along an intended single course, athletes who intentionally load multiple alternative loops or exploration tracks prefer to keep all candidate paths visible and active on the map without having the system force a single active route.

This plan details the construction steps to:
1. Provide a configurable toggle `autoFollowUniqueRouteEnabled` in Advanced Settings -> Navigation (default: `true`).
2. Persist the preference in `TuningPreferencesDataStore`.
3. Guard the autonomous binding block in `ForkNavigationRepository.kt` so that route locking is suppressed when the toggle is disabled, while dismissing the fork decision alert cleanly.
4. Maintain 100% localization parity across all 9 supported application locales.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*), amending Clause 4, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Test Mapping**: `TST-MAP-033` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts Verification*), amending Clause 3.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*), amending Clause 4, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.16 (`ATT-1955`, commit `34c752d2` / `419e6ebe`).
* **Root Reason for Existing Formulation**: In ATT-1955, autonomous route binding was hardcoded to automatically switch navigation to the chosen route (`routesRepository.setActiveNavigatedRoute(...)`) whenever an athlete progressed >= 50m past a fork and alternative candidate routes diverged by > 50m. This was designed for hands-free navigation along an intended single course. However, athletes who intentionally load multiple candidate routes or alternative exploration tracks prefer not to have the app unilaterally lock onto a single route.
* **Preservation of Core Invariants**: Default behavior remains enabled (`autoFollowUniqueRouteEnabled = true`), preserving 100% backward compatibility for athletes who want automatic snapping. When disabled, the fork decision card is dismissed once branches diverge beyond 50m without locking `activeNavigatedRouteId`, leaving candidate routes selected and visible. Offline operation, pure geodesic calculations, and 100% clean-room test pass rate remain preserved.

---

## 3. System Invariants & Preserved Behavior

1. **Default Hands-Free Auto-Binding**: On clean installs and default settings (`autoFollowUniqueRouteEnabled == true`), the app continues to auto-bind the chosen route identically to existing behavior.
2. **Manual Selection Operability**: Athletes can always manually select any branch on `ForkDecisionCard` (`selectRouteManually`), regardless of the toggle setting.
3. **Pure Geodesic Isolation**: All fork detection and polyline projection math remains pure Kotlin without Android framework dependencies.
4. **Subtask Lifecycle**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Clean-Room Test Pass Rate**: 100% test pass rate across all unit tests in `./gradlew testDebugUnitTest`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: DataStore & Preferences (`TuningPreferencesDataStore.kt`)
* In `TuningPreferencesDefaults`:
  ```kotlin
  const val DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = true
  ```
* In `TuningConfig`:
  ```kotlin
  val autoFollowUniqueRouteEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED
  ```
* In `TuningPreferencesDataStore.Companion`:
  ```kotlin
  val KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED: Preferences.Key<Boolean> =
      booleanPreferencesKey("tuning_auto_follow_unique_route_enabled")
  ```
  Add to `ALL_KEYS` list so `resetToDefaults()` automatically resets it.
* In `tuningConfigFlow`: map `prefs[KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED] ?: TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED`.
* In `saveTuningConfig(config)`: write `prefs[KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED] = config.autoFollowUniqueRouteEnabled`.
* Expose `suspend fun updateAutoFollowUniqueRouteEnabled(enabled: Boolean)`.

### Component 2: Fork Navigation Repository Guard (`ForkNavigationRepository.kt`)
* Add `tuningPreferencesDataStore: TuningPreferencesDataStore? = null` to internal constructor.
* In `init` block:
  ```kotlin
  if (tuningPreferencesDataStore != null) {
      scope.launch {
          tuningPreferencesDataStore.tuningConfigFlow.collect { config ->
              autoFollowUniqueRouteEnabled = config.autoFollowUniqueRouteEnabled
          }
      }
  }
  ```
* Expose:
  ```kotlin
  var autoFollowUniqueRouteEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED
  ```
* In `getInstance(context: Context)`:
  Pass `tuningPreferencesDataStore = TuningPreferencesDataStore(context.applicationContext)`.
* In `onLocationChanged`:
  ```kotlin
  if (alternativesFar) {
      if (autoFollowUniqueRouteEnabled) {
          routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)
      }
      _forkDecisionState.value = null
      return
  }
  ```

### Component 3: Navigation Section & Dialog (`NavigationSection.kt`, `AdvancedTuningDialog.kt`)
* In `NavigationSection.kt`:
  Add parameters:
  ```kotlin
  autoFollowUniqueRouteEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED,
  onAutoFollowUniqueRouteEnabledChange: (Boolean) -> Unit = {}
  ```
  Render `TuningToggleItem`:
  ```kotlin
  TuningToggleItem(
      title = stringResource(R.string.tuning_auto_follow_unique_route_title),
      isChecked = autoFollowUniqueRouteEnabled,
      onCheckedChange = onAutoFollowUniqueRouteEnabledChange,
      summary = stringResource(R.string.tuning_auto_follow_unique_route_desc)
  )
  ```
* In `AdvancedTuningDialog.kt`:
  * Add state: `var autoFollowUniqueRouteEnabled by remember { mutableStateOf(TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED) }`.
  * Update `applyConfig`: `autoFollowUniqueRouteEnabled = config.autoFollowUniqueRouteEnabled`.
  * Update `saveTuningConfig`: pass `autoFollowUniqueRouteEnabled` into `TuningConfig`.
  * Wire callbacks to `NavigationSection`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: Existing `NavigationSection.kt` and `SensorsGpsFilterSection.kt`.
* **Reused components**: `TuningToggleItem` from `com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControls.kt`.
* **Theme tokens**: Standard typography (`MaterialTheme.typography.bodyMedium` for title, `MaterialTheme.typography.bodySmall` for summary) and colors (`MaterialTheme.colorScheme.onSurface`, `MaterialTheme.colorScheme.onSurfaceVariant`).
* **New one-off styles & justification**: None. Reuses the standard `TuningToggleItem` without custom layout or modifiers.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Localized String Resources (9 Locales)
* **Files**: `app/src/main/res/values*/strings.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Strings**:
  * `tuning_auto_follow_unique_route_title`
  * `tuning_auto_follow_unique_route_desc`

### Step 2: DataStore & Preference Plumbing
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Changes**:
  * Add `DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED` to `TuningPreferencesDefaults`.
  * Add `autoFollowUniqueRouteEnabled` to `TuningConfig`.
  * Add `KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED` and add to `ALL_KEYS`.
  * Update mapping in `tuningConfigFlow` and saving in `saveTuningConfig`.
  * Add `updateAutoFollowUniqueRouteEnabled`.

### Step 3: Guard Autonomous Binding in ForkNavigationRepository
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepository.kt`
* **Changes**:
  * Inject `tuningPreferencesDataStore` in constructor.
  * Collect `tuningConfigFlow` to maintain `autoFollowUniqueRouteEnabled`.
  * Guard `routesRepository.setActiveNavigatedRoute` with `if (autoFollowUniqueRouteEnabled)`.
  * Pass `TuningPreferencesDataStore(context.applicationContext)` in `getInstance(context)`.

### Step 4: UI Integration in Navigation Tuning
* **Files**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**:
  * Add `autoFollowUniqueRouteEnabled` toggle to `NavigationSection`.
  * Bind state in `AdvancedTuningDialog`.

### Step 5: Unit Tests & Verification
* **Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepositoryTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningConfigTest.kt`
* **Targeted Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.ForkNavigationRepositoryTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest"
  ```
