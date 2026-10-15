# Stage 3: Implementation Plan - ATT-3189: Navigate to zone settings upon pairing new heart rate monitor or power meter when unconfigured

**Ticket**: [ATT-3189](https://rainerblind.atlassian.net/browse/ATT-3189)  
**Sub-task**: [ATT-3617](https://rainerblind.atlassian.net/browse/ATT-3617) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*)  
**Test Mapping**: `TST-UI-337` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification*)  
**Branch**: `feature/ATT-3189`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

When an athlete pairs a heart rate monitor or cycling power meter for the first time, their personal physiological training zones (5-zone HR scheme and 7-zone Coggan power model) typically remain at generic system defaults. Without configured zones, real-time cockpit tile color accents (`REQ-UI-370`) and aftermath distribution analytics (`REQ-UI-372`) fail to provide meaningful personalized feedback.

This implementation plan specifies the construction of the contextual zone navigation feature under ASPICE SWE.2:
1. `SettingsDataStore.kt` extension tracking explicit configuration status (`is_hr_zones_configured`, `is_pwr_zones_configured`) with intelligent backward-compatible migration heuristics.
2. `DeviceListViewModel.kt` pairing transition interception and sensor discrimination (`DeviceType.HRM`, `DeviceType.BIKE_POWER`), emitting one-time deep-link navigation events via Kotlin Coroutines `SharedFlow`.
3. Parameterized navigation routing in `NavRoutes.kt` and `ATrainingTrackerApp.kt` supporting `training_zones?initialTab={initialTab}&promptReason={promptReason}` with clean back-stack return to `NavRoutes.SENSORS`.
4. Material 3 contextual onboarding banner (`ZoneOnboardingBanner.kt`) in `ZoneSettingsScreen` explaining the redirection and providing an explicit dismissal action that confirms zone configuration.
5. Full 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*)
  * `REQ-UI-374.1`: Pairing Detection & Sensor Discrimination (`DeviceType.HRM`, `DeviceType.BIKE_POWER` vs others)
  * `REQ-UI-374.2`: Zone Configuration Status Guard & Migration Heuristics (`SettingsDataStore`)
  * `REQ-UI-374.3`: One-Time Deep-Link Navigation & Parameterized Route (`NavRoutes.trainingZones`)
  * `REQ-UI-374.4`: Contextual Guidance Banner UI (`ZoneOnboardingBanner`)
  * `REQ-UI-374.5`: 9-Language Localization Parity
* **Test Mapping**: `TST-UI-337` (*Contextual Zone Settings Navigation Verification*)
  * `[TST-UI-337.1]`: Configuration Guard & Migration Heuristics Unit Tests (`SettingsDataStoreConfiguredTest.kt`)
  * `[TST-UI-337.2]`: Pairing Detection & Sensor Discrimination Unit Tests (`DeviceListViewModelPairingTest.kt`)
  * `[TST-UI-337.3]`: Navigation Route & Deep-Link Parameter Formatting Contract (`NavRoutesZoneTest.kt`)
  * `[TST-UI-337.4]`: Contextual Guidance Banner Component & Dismissal Contract (`ZoneOnboardingBannerContractTest.kt`)
  * `[TST-UI-337.5]`: 9-Language Localization & Format Token Parity Audit (`ZoneOnboardingLocalizationTest.kt`)
  * `[TST-UI-337.6]`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Unconfigured-Only Navigation Invariant**: Navigation MUST ONLY occur when the athlete's zones for that specific metric are unconfigured. Pairing with configured zones or re-pairing existing sensors MUST complete silently without navigating away.
2. **Physiological-Only Discrimination Invariant**: Only `DeviceType.HRM` and `DeviceType.BIKE_POWER` trigger zone onboarding. Non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, etc.) MUST NEVER trigger zone navigation.
3. **Smooth Back-Stack Navigation**: Tapping the top app bar back icon or Android system back gesture from `ZoneSettingsScreen` MUST return cleanly to `NavRoutes.SENSORS` with zero state corruption, loop trapping, or crashes.
4. **Single-Activity Architecture**: Deep-linking is orchestrated within `ATrainingTrackerApp.kt` via standard Jetpack Compose `NavController`. No secondary Activity tasks are spawned.
5. **Historical Workout Immutability**: All completed workouts and aggregates in SQLite (`WorkoutSummaries.db`, `Extrema.db`) remain strictly immutable.
6. **Clean-Room Test Pass Rate**: 100% test pass rate across the full application unit test suite (0 failures, 0 regressions).

---

## 4. Proposed Architectural Changes (SWE.2 Component Design)

### Component 1: SettingsDataStore Configuration Keys & Migration (`SettingsDataStore.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.settings`
* **Layer**: Persistence / Preferences
* **New Preference Keys**:
  ```kotlin
  val KEY_IS_HR_ZONES_CONFIGURED = booleanPreferencesKey("is_hr_zones_configured")
  val KEY_IS_PWR_ZONES_CONFIGURED = booleanPreferencesKey("is_pwr_zones_configured")
  ```
* **API Contracts**:
  ```kotlin
  val isHrZonesConfigured: Flow<Boolean>
  val isPwrZonesConfigured: Flow<Boolean>

  suspend fun setHrZonesConfigured(configured: Boolean)
  suspend fun setPwrZonesConfigured(configured: Boolean)
  ```
* **Migration Heuristic Fallback**:
  - `isHrZonesConfigured`: observes `KEY_IS_HR_ZONES_CONFIGURED`. If `null`, checks `hr_max != null || hr_run_zones_customized == true || hr_bike_zones_customized == true`. If any are true, returns `true`, else `false`.
  - `isPwrZonesConfigured`: observes `KEY_IS_PWR_ZONES_CONFIGURED`. If `null`, checks `cycling_ftp != null || pwr_bike_zones_customized == true`. If any are true, returns `true`, else `false`.
* **Automatic Status Updates**:
  - `saveCalculatedPowerZones()` sets `is_pwr_zones_configured = true`.
  - `saveCalculatedHeartRateZones()` sets `is_hr_zones_configured = true`.
  - `saveHrZoneMax()` sets `is_hr_zones_configured = true`.
  - `savePowerZoneMax()` sets `is_pwr_zones_configured = true`.

### Component 2: Pairing Interception & Event Channel (`DeviceListViewModel.kt`)
* **Package**: `com.atrainingtracker.banalservice.ui.devices.devicelist`
* **Layer**: Presentation / ViewModel
* **Event Model**:
  ```kotlin
  data class ZoneOnboardingEvent(
      val initialTab: Int,
      val promptReason: String
  )
  ```
* **API Contracts**:
  ```kotlin
  val zoneNavigationEvent: SharedFlow<ZoneOnboardingEvent>
  ```
* **Behavior in `onPairedChanged(deviceId, isPaired)`**:
  - When `isPaired == true` (transitioning from unpaired to paired):
    - Check `currentState.deviceType`:
      - If `DeviceType.HRM`: read `settingsDataStore.isHrZonesConfigured.first()`. If `false`: emit `ZoneOnboardingEvent(initialTab = 0, promptReason = "hr_paired")`.
      - If `DeviceType.BIKE_POWER`: read `settingsDataStore.isPwrZonesConfigured.first()`. If `false`: emit `ZoneOnboardingEvent(initialTab = 2, promptReason = "pwr_paired")`.
      - Otherwise: complete silently.

### Component 3: Navigation Parameterization (`NavRoutes.kt` & `ATrainingTrackerApp.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.navigation`
* **Layer**: Presentation / Navigation
* **Route Definitions in `NavRoutes.kt`**:
  ```kotlin
  const val TRAINING_ZONES = "training_zones"
  const val TRAINING_ZONES_PATTERN = "training_zones?initialTab={initialTab}&promptReason={promptReason}"
  const val ARG_INITIAL_TAB = "initialTab"
  const val ARG_PROMPT_REASON = "promptReason"

  fun trainingZones(initialTab: Int = 0, promptReason: String? = null): String {
      return if (initialTab != 0 || promptReason != null) {
          val reasonParam = promptReason?.let { "&promptReason=$it" } ?: ""
          "training_zones?initialTab=$initialTab$reasonParam"
      } else {
          TRAINING_ZONES
      }
  }
  ```
* **Drawer ID Resolution**:
  - `toDrawerItemId`: extracts route name prefix `route?.substringBefore("?")` ensuring `"training_zones?..."` resolves to `R.id.drawer_training_zones`.
* **Graph Wiring in `ATrainingTrackerApp.kt`**:
  - In `composable(NavRoutes.SENSORS)`: passes callback `onNavigateToZones = { tab, reason -> navController.navigate(NavRoutes.trainingZones(tab, reason)) }` to `DevicesTabbedScreen`.
  - In `composable(NavRoutes.TRAINING_ZONES_PATTERN, arguments = ...)`:
    - Extracts `initialTab` (default 0) and `promptReason` (nullable string).
    - Invokes `ZoneSettingsScreen(initialTab = initialTab, promptReason = promptReason, onFinish = { navController.popBackStack() })`.

### Component 4: Contextual Guidance Banner Component (`ZoneOnboardingBanner.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.settings`
* **Layer**: Presentation / Jetpack Compose
* **API Contracts**:
  ```kotlin
  @Composable
  fun ZoneOnboardingBanner(
      promptReason: String,
      onDismiss: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
* **UI Consistency (Rule 23)**:
  - **Reference screen / component**: Material 3 Banner / `AdvancedTuningScreen.kt` informational cards.
  - **Reused components**: `Card`, `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)`, `Icon(Icons.Default.Info)`, `TextButton`.
  - **Theme tokens**: `MaterialTheme.colorScheme.primaryContainer`, `MaterialTheme.colorScheme.onPrimaryContainer`, `RoundedCornerShape(12.dp)`, `12.dp` padding.
  - **Behavior**:
    - For `"hr_paired"`: renders `stringResource(R.string.zone_onboarding_hr_banner)`.
    - For `"pwr_paired"`: renders `stringResource(R.string.zone_onboarding_pwr_banner)`.
    - Action button: renders `stringResource(R.string.zone_onboarding_dismiss)`. Tapping dismiss invokes `onDismiss()` which sets the configuration flag in `SettingsDataStore`.

### Component 5: Localization Parity (9 Locales)
* Define 3 new string resources across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
  - `zone_onboarding_hr_banner`
  - `zone_onboarding_pwr_banner`
  - `zone_onboarding_dismiss`

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: DataStore Configuration Keys & Migration Heuristics
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStore.kt`:
  * Add `KEY_IS_HR_ZONES_CONFIGURED` and `KEY_IS_PWR_ZONES_CONFIGURED`.
  * Add `isHrZonesConfigured` and `isPwrZonesConfigured` flows with migration fallbacks.
  * Add `setHrZonesConfigured` and `setPwrZonesConfigured`.
  * Update `saveCalculatedPowerZones`, `saveCalculatedHeartRateZones`, `saveHrZoneMax`, and `savePowerZoneMax` to set flags to `true`.
* Create unit test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/SettingsDataStoreConfiguredTest.kt` (`[TST-UI-337.1]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.SettingsDataStoreConfiguredTest"`

### Step 2: Parameterized Navigation Routing (`NavRoutes.kt`)
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`:
  * Add `TRAINING_ZONES_PATTERN`, `ARG_INITIAL_TAB`, `ARG_PROMPT_REASON`, and `fun trainingZones(...)`.
  * Update `toDrawerItemId` to handle query parameters.
* Create unit test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/NavRoutesZoneTest.kt` (`[TST-UI-337.3]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.NavRoutesZoneTest"`

### Step 3: DeviceListViewModel Pairing Interception & Event Channel
* Update `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModel.kt`:
  * Add `ZoneOnboardingEvent` and `zoneNavigationEvent: SharedFlow<ZoneOnboardingEvent>`.
  * In `onPairedChanged(deviceId, isPaired)`, inspect `DeviceType` and check `SettingsDataStore` configuration status when transitioning to `isPaired == true`.
* Create unit test: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModelPairingTest.kt` (`[TST-UI-337.2]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModelPairingTest"`

### Step 4: 9-Language Localization Resources
* Add 3 onboarding strings to `strings.xml` across all 9 application locales.
* Create localization audit test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneOnboardingLocalizationTest.kt` (`[TST-UI-337.5]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.ZoneOnboardingLocalizationTest"`

### Step 5: Guidance Banner UI & Screen Integration
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/ZoneOnboardingBanner.kt`.
* Update `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`:
  * Accept `onNavigateToZones` and collect `listViewModel.zoneNavigationEvent`.
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`:
  * Wire `onNavigateToZones` on `NavRoutes.SENSORS` and configure `NavRoutes.TRAINING_ZONES_PATTERN` arguments.
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`:
  * Accept `promptReason` in `ZoneSettingsScreen`.
  * Mount `ZoneOnboardingBanner` and handle dismissal.
* Create UI contract test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneOnboardingBannerContractTest.kt` (`[TST-UI-337.4]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.ZoneOnboardingBannerContractTest"`

### Step 6: Clean-Room Full Suite Regression Execution
* Run `./gradlew testDebugUnitTest` verifying 100% pass rate (`[TST-UI-337.6]`).

---

## 6. Verification & Rollback Plan

* **Verification**:
  * Step-by-step JVM unit test verification at each atomic milestone.
  * Clean-room full test execution (`./gradlew testDebugUnitTest`) verifying 0 regressions.
* **Rollback Plan**:
  * All work isolated on branch `feature/ATT-3189`. In case of defects, the feature branch can be safely reset or discarded without impacting `sprint/2026-42.4`.
