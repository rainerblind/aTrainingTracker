# Stage 2: Requirement & Test Specification - ATT-3320: Configurable auto-switch to unique route in advanced navigation settings

**Ticket**: [ATT-3320](https://rainerblind.atlassian.net/browse/ATT-3320)  
**Sub-task**: [ATT-3338](https://rainerblind.atlassian.net/browse/ATT-3338) (`[Test-Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MAP-031` (amending Clause 4), `REQ-SET-073`  
**Test Mapping**: `TST-MAP-033` (amending Clause 3)  
**Branch**: `feature/ATT-3320`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Detailed Requirements & Architectural Constraints

### 1.1 Formal Specification (REQ-MAP-031 Amendment & REQ-SET-073 Complement)

The system SHALL provide a configurable user setting in **Advanced Settings -> Navigation** governing whether the application automatically binds to follow a unique route when diverging at a trail fork during multi-route tracking (ATT-3320, amending `REQ-MAP-031` clause 4 and complementing `REQ-SET-073`):

1. **Preference Definition & DataStore Storage (`TuningPreferencesDataStore.kt`)**:
   * The system SHALL define `TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = true`.
   * The DataStore repository SHALL define `KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = booleanPreferencesKey("tuning_auto_follow_unique_route_enabled")` in `TuningPreferencesDataStore`.
   * `TuningConfig` SHALL declare property `val autoFollowUniqueRouteEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED`.
   * `TuningPreferencesDataStore` SHALL expose mutator `suspend fun updateAutoFollowUniqueRouteEnabled(enabled: Boolean)`.
   * Invoking `resetToDefaults()` SHALL remove `KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED` and restore the default state `true`.

2. **Advanced Settings UI Integration (`NavigationSection.kt` & `AdvancedTuningDialog.kt`)**:
   * Within the Navigation accordion section, `NavigationSection.kt` SHALL render a Material 3 `TuningToggleItem`:
     * Title: `tuning_auto_follow_unique_route_title` ("Auto-follow unique route").
     * Summary: `tuning_auto_follow_unique_route_desc` ("Automatically switch to follow a route when it becomes the only matching route at a fork").
     * Checked state: bound to `autoFollowUniqueRouteEnabled`.
   * Toggling SHALL mutate local dialog state, and tapping "Speichern" (Save) SHALL persist the updated preference into `TuningPreferencesDataStore`.
   * Tapping "Auf Werkseinstellungen zurücksetzen" (Reset) SHALL revert the toggle to `true`.

3. **Autonomous Route Binding Guard (`ForkNavigationRepository.kt`)**:
   * `ForkNavigationRepository` SHALL observe or query `autoFollowUniqueRouteEnabled`.
   * In `onLocationChanged()`, when the athlete progresses past the divergence coordinate and reaches a unique branch where alternative candidate branches diverge by $> 50\text{ m}$ (`alternativesFar == true`):
     * If `autoFollowUniqueRouteEnabled == true`: the system SHALL invoke `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)`, dismiss the fork alert state (`_forkDecisionState.value = null`), and initiate active turn prompts and climb guidance (preserving default behavior).
     * If `autoFollowUniqueRouteEnabled == false`: the system SHALL suppress the call to `setActiveNavigatedRoute`, dismiss the fork alert state (`_forkDecisionState.value = null`), and return, leaving all candidate routes active and rendered on the map.

4. **9-Language Localization Parity**:
   * `tuning_auto_follow_unique_route_title` and `tuning_auto_follow_unique_route_desc` SHALL be defined across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

5. **Preservation of System Invariants**:
   * Pure geodesic calculations without android framework dependencies for unit testability MUST be preserved.
   * Manual route selection via `ForkDecisionCard` (`selectRouteManually`) MUST remain operative regardless of the toggle setting.
   * Single-route tracking and free-ride tracking invariants MUST remain intact.
   * 100% clean-room test pass rate across all suites.

---

### 1.2 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Default Auto-Binding Enabled)**:
  * *Given* default settings or a new installation (`autoFollowUniqueRouteEnabled == true`),
  * *When* the athlete navigates with multiple active routes and diverges $\ge 50\text{ m}$ down one branch while others diverge $> 50\text{ m}$,
  * *Then* the app automatically switches to follow that unique route (`routesRepository.setActiveNavigatedRoute` is invoked) and dismisses the fork decision prompt.

* **Criterion 2 (Auto-Binding Suppressed when Disabled)**:
  * *Given* the athlete turns OFF "Auto-follow unique route" in Advanced Settings -> Navigation,
  * *When* the athlete navigates with multiple active routes and diverges down one branch while others diverge $> 50\text{ m}$,
  * *Then* the app does NOT invoke `routesRepository.setActiveNavigatedRoute`, clears the fork decision prompt, and keeps all candidate routes active on the map.

* **Criterion 3 (Manual Route Selection Preserved)**:
  * *Given* "Auto-follow unique route" is OFF and a fork prompt is active,
  * *When* the athlete taps one of the branch options on the `ForkDecisionCard`,
  * *Then* the selected route is immediately bound to `routesRepository.setActiveNavigatedRoute(routeId)` and the prompt is dismissed.

* **Criterion 4 (Preference Persistence & Factory Reset)**:
  * *Given* the athlete disables the toggle in Advanced Settings and saves,
  * *When* the app restarts,
  * *Then* `autoFollowUniqueRouteEnabled` remains `false`,
  * *When* the athlete triggers factory reset ("Auf Werkseinstellungen zurücksetzen"),
  * *Then* `autoFollowUniqueRouteEnabled` reverts to `true`.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*), amending Clause 4, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.16 (`ATT-1955`, commit `34c752d2` / `419e6ebe`).
* **Root Reason for Existing Formulation**: In ATT-1955, autonomous route binding was hardcoded to automatically switch navigation to the chosen route (`routesRepository.setActiveNavigatedRoute(...)`) whenever an athlete progressed >= 50m past a fork and alternative candidate routes diverged by > 50m. This was designed for hands-free navigation along an intended single course. However, athletes who intentionally load multiple candidate routes or alternative exploration tracks prefer not to have the app unilaterally lock onto a single route.
* **Preservation of Core Invariants**: Default behavior remains enabled (`autoFollowUniqueRouteEnabled = true`), preserving 100% backward compatibility for athletes who want automatic snapping. When disabled, the fork decision card is dismissed once branches diverge beyond 50m without locking `activeNavigatedRouteId`, leaving candidate routes selected and visible. Offline operation, pure geodesic calculations, and 100% clean-room test pass rate remain preserved.

---

## 2. Concrete Test Specifications & Verification Procedures

### 2.1 Repository Unit Tests (`ForkNavigationRepositoryTest.kt`)

* **TST-MAP-033.3a: Auto-binding when enabled (default)**:
  * Set up `ForkNavigationRepository` with `autoFollowUniqueRouteEnabled = true`.
  * Simulate approach to fork (e.g. `posApproach` at 800m).
  * Simulate progression down branch 1 past divergence (`posBranch1` at 1500m).
  * Verify `mockRoutesRepo.setActiveNavigatedRoute(1L)` was called.
  * Verify `forkDecisionState.value` is `null`.

* **TST-MAP-033.3b: Suppression of auto-binding when disabled**:
  * Set up `ForkNavigationRepository` with `autoFollowUniqueRouteEnabled = false`.
  * Simulate approach to fork.
  * Simulate progression down branch 1 past divergence.
  * Verify `mockRoutesRepo.setActiveNavigatedRoute(any())` was **NEVER** called (`verify(exactly = 0)`).
  * Verify `forkDecisionState.value` is `null`.

* **TST-MAP-033.3c: Manual selection when auto-follow disabled**:
  * Set up `ForkNavigationRepository` with `autoFollowUniqueRouteEnabled = false`.
  * Simulate approach to fork.
  * Invoke `repository.selectRouteManually(2L)`.
  * Verify `mockRoutesRepo.setActiveNavigatedRoute(2L)` is called immediately and state is cleared.

### 2.2 Preference & DataStore Tests (`TuningConfigTest.kt` & `TuningPreferencesDataStoreTest.kt`)

* **TST-MAP-033.3d: Default value and serialization**:
  * Verify `TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED == true`.
  * Verify `TuningConfig().autoFollowUniqueRouteEnabled == true`.
* **TST-MAP-033.3e: DataStore mutation and factory reset**:
  * Update preference to `false`, observe flow emitted `false`.
  * Execute `resetToDefaults()`, verify preference resets to `true`.

### 2.3 Localization Audit (`TranslationParityTest.kt`)

* **TST-MAP-033.5: 9-Locale string parity**:
  * Verify `tuning_auto_follow_unique_route_title` and `tuning_auto_follow_unique_route_desc` exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 2.4 Clean-Room Full Suite Regression Execution

* Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across all unit test suites.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification | Target File | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-031.4` (Auto-Binding Enabled) | `TST-MAP-033.3a` | `ForkNavigationRepositoryTest.kt` | Unit Test (MockK) |
| `REQ-MAP-031.4` (Auto-Binding Disabled) | `TST-MAP-033.3b` | `ForkNavigationRepositoryTest.kt` | Unit Test (MockK) |
| `REQ-MAP-031.3` (Manual Route Selection) | `TST-MAP-033.3c` | `ForkNavigationRepositoryTest.kt` | Unit Test (MockK) |
| `REQ-SET-073.1` (DataStore & Defaults) | `TST-MAP-033.3d`, `TST-MAP-033.3e` | `TuningConfigTest.kt` | Unit Test |
| `REQ-MAP-031.5` (Localization Parity) | `TST-MAP-033.5` | `res/values-*/strings.xml` | String Parity Audit |
