# Stage 2: Requirement & Test Specification - ATT-3189: Navigate to zone settings upon pairing new heart rate monitor or power meter when unconfigured

**Ticket**: [ATT-3189](https://rainerblind.atlassian.net/browse/ATT-3189)  
**Sub-task**: [ATT-3616](https://rainerblind.atlassian.net/browse/ATT-3616) (`[Test-Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*)  
**Test Spec ID**: `TST-UI-337` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification*)  
**Branch**: `feature/ATT-3189`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-UI-374)

### 1.1 Problem Statement & Rationale
When athletes pair a heart rate monitor (BLE/ANT+) or cycling power meter for the first time, physiological training zones (5-zone HR scheme and 7-zone Coggan power model) often remain at generic system defaults. Without personalized boundaries:
1. Real-time telemetry cockpit tiles (which render zone color intensity contours and font treatments via `REQ-UI-370`) provide meaningless or misleading feedback.
2. Post-workout summary cards (such as the 3-way distribution bars from `REQ-UI-372`) fail to accurately categorize aerobic, tempo, or threshold training effort.
3. Discoverability of the Zone Settings screen is low for new athletes navigating the drawer menu.

`REQ-UI-374` resolves this by automatically directing the athlete to the relevant Zone Settings tab immediately after successfully pairing a physiological sensor, provided the zones are not yet configured. Configured athletes and non-physiological sensors experience zero interruption.

---

### 1.2 Functional & Architectural Requirements

#### 1.2.1 Pairing Detection & Sensor Discrimination (`REQ-UI-374.1`)
1. The system SHALL monitor pairing transitions when a sensor's state changes from `isPaired == false` to `isPaired == true` (e.g. within `DeviceListViewModel.onPairedChanged`).
2. The system SHALL discriminate device capability based on `DeviceType`:
   - **Heart Rate Monitor**: `DeviceType.HRM` (BLE Heart Rate or ANT+ Heart Rate).
   - **Cycling Power Meter**: `DeviceType.BIKE_POWER` (BLE Cycling Power or ANT+ Bike Power).
3. For all other sensor types (`BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, `SPEED_AND_LOCATION_NETWORK`, etc.), the system SHALL complete pairing silently on the sensor screen without navigating away.
4. Unpairing a sensor (`isPaired` transitioning to `false`) SHALL NEVER trigger navigation.

#### 1.2.2 Zone Configuration Status Guard in SettingsDataStore (`REQ-UI-374.2`)
1. The system SHALL maintain two persistent boolean preference keys in `SettingsDataStore`:
   - `KEY_IS_HR_ZONES_CONFIGURED = booleanPreferencesKey("is_hr_zones_configured")`
   - `KEY_IS_PWR_ZONES_CONFIGURED = booleanPreferencesKey("is_pwr_zones_configured")`
2. **Default State**:
   - On a fresh installation, both flags SHALL default to `false`.
3. **Migration Heuristic / Backward-Compatibility Fallback**:
   - If `KEY_IS_HR_ZONES_CONFIGURED` is unset (null) in DataStore, the system SHALL evaluate:
     `hr_max != null || hr_run_zones_customized == true || hr_bike_zones_customized == true`.
     If any of these conditions are true, the profile is treated as configured (`true`); otherwise `false`.
   - If `KEY_IS_PWR_ZONES_CONFIGURED` is unset (null) in DataStore, the system SHALL evaluate:
     `cycling_ftp != null || pwr_bike_zones_customized == true`.
     If any of these conditions are true, the profile is treated as configured (`true`); otherwise `false`.
4. **Transition to Configured (`true`)**:
   - `is_hr_zones_configured` SHALL transition to `true` whenever:
     - Athlete executes automated HR zone calculation (`saveCalculatedHeartRateZones`).
     - Athlete manually edits and saves any HR zone cutoff (`saveHrZoneMax(ZoneType.HR_RUN, ...)` or `saveHrZoneMax(ZoneType.HR_BIKE, ...)`).
     - Athlete acknowledges/dismisses the HR onboarding banner or saves/exits HR zone settings.
   - `is_pwr_zones_configured` SHALL transition to `true` whenever:
     - Athlete executes automated power zone calculation (`saveCalculatedPowerZones`).
     - Athlete manually edits and saves any power zone cutoff (`savePowerZoneMax`).
     - Athlete acknowledges/dismisses the power onboarding banner or saves/exits power zone settings.
5. If the corresponding flag evaluates to `true`, sensor pairing SHALL complete silently without navigating away.

#### 1.2.3 One-Time Deep-Link Navigation & Parameterized Route (`REQ-UI-374.3`)
1. When pairing succeeds for a physiological sensor and its corresponding configuration flag evaluates to `false`:
   - For `DeviceType.HRM`: the system SHALL emit a one-time navigation event targeting `NavRoutes.TRAINING_ZONES` with `initialTab = 0` (`HR_RUN`) and `promptReason = "hr_paired"`.
   - For `DeviceType.BIKE_POWER`: the system SHALL emit a one-time navigation event targeting `NavRoutes.TRAINING_ZONES` with `initialTab = 2` (`PWR_BIKE`) and `promptReason = "pwr_paired"`.
2. `NavRoutes` SHALL expose a parameterized pattern and route builder:
   ```kotlin
   const val TRAINING_ZONES = "training_zones"
   const val TRAINING_ZONES_PATTERN = "training_zones?initialTab={initialTab}&promptReason={promptReason}"
   const val ARG_INITIAL_TAB = "initialTab"
   const val ARG_PROMPT_REASON = "promptReason"

   fun trainingZones(initialTab: Int = 0, promptReason: String? = null): String
   ```
3. **Back Stack Hygiene**: Navigating back from Zone Settings via Android system back gesture or top app bar navigation icon MUST smoothly return to the sensor screen (`NavRoutes.SENSORS`) without stack corruption, loop trapping, or crashes.

#### 1.2.4 Contextual Guidance Banner UI (`REQ-UI-374.4`)
1. When `ZoneSettingsScreen` is displayed with a non-null `promptReason`:
   - The UI SHALL render a dismissable Material 3 guidance banner (`ZoneOnboardingBanner`) positioned at the top of the content area.
   - **For `hr_paired`**: Display localized message `zone_onboarding_hr_banner` (*"New heart rate sensor paired. Set your heart rate zones for personalized training feedback."*).
   - **For `pwr_paired`**: Display localized message `zone_onboarding_pwr_banner` (*"New power meter paired. Set your power zones or calculate them from your FTP."*).
   - **Action Button**: Display localized action `zone_onboarding_dismiss` (*"Dismiss"* / *"Verstanden"*).
2. Tapping the action button SHALL dismiss the banner and mark the respective zone profile as configured in `SettingsDataStore`.
3. If `promptReason` is null, the banner SHALL NOT be rendered.

#### 1.2.5 9-Language Localization Parity (`REQ-UI-374.5`)
1. All onboarding guidance strings SHALL be defined across all 9 supported application locales:
   - `values/strings.xml` (EN)
   - `values-de/strings.xml` (DE)
   - `values-es/strings.xml` (ES)
   - `values-fr/strings.xml` (FR)
   - `values-it/strings.xml` (IT)
   - `values-ja/strings.xml` (JA)
   - `values-nl/strings.xml` (NL)
   - `values-pl/strings.xml` (PL)
   - `values-pt/strings.xml` (PT)
2. Exact format token parity (zero missing keys, exact `%s` / `%d` matching) SHALL be enforced by automated unit tests.

---

### 1.3 Given-When-Then Acceptance Criteria

* **Scenario 1: First-time pairing of Heart Rate Monitor with unconfigured zones**
  * *Given* an unconfigured heart rate zone profile (`isHrZonesConfigured == false`),
  * *When* the athlete taps "Pair" on a discovered BLE or ANT+ heart rate monitor,
  * *Then* pairing completes in the repository, and the app automatically navigates to `NavRoutes.TRAINING_ZONES` with `initialTab = 0` displaying the heart rate onboarding banner.

* **Scenario 2: First-time pairing of Cycling Power Meter with unconfigured zones**
  * *Given* an unconfigured power zone profile (`isPwrZonesConfigured == false`),
  * *When* the athlete taps "Pair" on a discovered BLE or ANT+ cycling power meter,
  * *Then* pairing completes in the repository, and the app automatically navigates to `NavRoutes.TRAINING_ZONES` with `initialTab = 2` displaying the power meter onboarding banner.

* **Scenario 3: Pairing with already configured zones**
  * *Given* an athlete whose HR or power zones are already configured (`isHrZonesConfigured == true` or `isPwrZonesConfigured == true`),
  * *When* the athlete pairs another sensor or re-pairs an existing sensor,
  * *Then* pairing completes on the sensor screen without navigating away.

* **Scenario 4: Pairing non-physiological sensors**
  * *Given* an athlete pairing a speed sensor, cadence sensor, or environment sensor (`DeviceType.BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`),
  * *When* the sensor is paired,
  * *Then* pairing completes on the sensor screen without navigating away regardless of zone configuration flags.

* **Scenario 5: Back navigation from onboarded screen**
  * *Given* the athlete has been navigated to Zone Settings via the onboarding prompt,
  * *When* tapping the top app bar back icon or Android system back gesture,
  * *Then* the screen returns cleanly to `NavRoutes.SENSORS`.

* **Scenario 6: Dismissing onboarding banner**
  * *Given* the onboarding banner is displayed in Zone Settings,
  * *When* the athlete taps "Verstanden" / "Dismiss",
  * *Then* the banner is dismissed and `is_zones_configured` is persisted as `true`.

---

### 1.4 System Invariants & Preserved Behavior

1. **Unconfigured-Only Invariant**: Navigation MUST occur if and only if the respective metric is unconfigured. Configured users MUST NEVER experience unexpected navigation interrupts.
2. **Physiological-Only Invariant**: Only `DeviceType.HRM` and `DeviceType.BIKE_POWER` can trigger zone onboarding. Non-physiological sensors MUST NEVER trigger zone navigation.
3. **Single-Activity Architecture**: Navigation occurs within `ATrainingTrackerApp.kt` via Jetpack Compose `NavController`. No secondary Activity tasks are created.
4. **Historical Workout Immutability**: All completed workouts in SQLite (`WorkoutSummaries.db`) remain strictly immutable.
5. **Clean-Room Test Pass Rate**: 100% test pass rate across all application modules (0 failures, 0 regressions).

---

## 2. Test Specification (TST-UI-337)

### 2.1 Test Suite Structure

| Test Class | Spec ID | Scope | Verification Target |
| :--- | :--- | :--- | :--- |
| `SettingsDataStoreConfiguredTest.kt` | `[TST-UI-337.1]` | Unit / Repository | Verifies `is_hr_zones_configured` and `is_pwr_zones_configured` preference defaults, migration heuristics from existing baselines, and mutator transitions. |
| `DeviceListViewModelPairingTest.kt` | `[TST-UI-337.2]` | Unit / ViewModel | Verifies `onPairedChanged` pairing interception, sensor discrimination (`HRM`, `BIKE_POWER` vs others), configuration guard checks, and navigation event emission. |
| `NavRoutesZoneTest.kt` | `[TST-UI-337.3]` | Unit / Navigation | Verifies `NavRoutes.trainingZones(initialTab, promptReason)` pattern formatting, default parameter handling, and route parsing. |
| `ZoneOnboardingBannerContractTest.kt` | `[TST-UI-337.4]` | Unit / UI Contract | Verifies `ZoneOnboardingBanner` content resolution, prompt reason mapping, and dismissal callback triggering. |
| `ZoneOnboardingLocalizationTest.kt` | `[TST-UI-337.5]` | Unit / Localization | Verifies existence, non-emptiness, and format token parity of onboarding strings across all 9 supported locales. |
| Clean-Room Regression | `[TST-UI-337.6]` | Integration | Execution of `./gradlew testDebugUnitTest` asserting 100% pass rate. |

---

### 2.2 Detailed Test Cases

#### [TST-UI-337.1]: Configuration Guard & Migration Heuristics (`SettingsDataStoreConfiguredTest.kt`)
* **Test 1.1: Fresh Install Defaults**:
  * Set up fresh DataStore with zero keys.
  * Assert `isHrZonesConfigured().first() == false`.
  * Assert `isPwrZonesConfigured().first() == false`.
* **Test 1.2: HR Migration Fallback (hr_max)**:
  * In DataStore without `is_hr_zones_configured`, set `hr_max = 190`.
  * Assert `isHrZonesConfigured().first() == true`.
* **Test 1.3: HR Migration Fallback (customized flags)**:
  * In DataStore without `is_hr_zones_configured`, set `hr_run_zones_customized = true`.
  * Assert `isHrZonesConfigured().first() == true`.
* **Test 1.4: Power Migration Fallback (cycling_ftp)**:
  * In DataStore without `is_pwr_zones_configured`, set `cycling_ftp = 250`.
  * Assert `isPwrZonesConfigured().first() == true`.
* **Test 1.5: Power Migration Fallback (pwr_bike_zones_customized)**:
  * In DataStore without `is_pwr_zones_configured`, set `pwr_bike_zones_customized = true`.
  * Assert `isPwrZonesConfigured().first() == true`.
* **Test 1.6: Explicit Mutators**:
  * Call `setHrZonesConfigured(true)`. Assert `isHrZonesConfigured().first() == true`.
  * Call `setPwrZonesConfigured(true)`. Assert `isPwrZonesConfigured().first() == true`.
* **Test 1.7: Auto-calculation sets configured**:
  * Call `saveCalculatedPowerZones(250, cutoffs)`. Assert `isPwrZonesConfigured().first() == true`.
  * Call `saveCalculatedHeartRateZones(...)`. Assert `isHrZonesConfigured().first() == true`.

#### [TST-UI-337.2]: Pairing Detection & Sensor Discrimination (`DeviceListViewModelPairingTest.kt`)
* **Test 2.1: HRM pairing with unconfigured zones emits navigation event**:
  * Mock `isHrZonesConfigured == false`.
  * Call `onPairedChanged(hrmDeviceId, true)` transitioning `isPaired: false -> true`.
  * Assert event emitted with `initialTab == 0` and `promptReason == "hr_paired"`.
* **Test 2.2: Power meter pairing with unconfigured zones emits navigation event**:
  * Mock `isPwrZonesConfigured == false`.
  * Call `onPairedChanged(powerDeviceId, true)` transitioning `isPaired: false -> true`.
  * Assert event emitted with `initialTab == 2` and `promptReason == "pwr_paired"`.
* **Test 2.3: HRM pairing with configured zones emits NO event**:
  * Mock `isHrZonesConfigured == true`.
  * Call `onPairedChanged(hrmDeviceId, true)`.
  * Assert zero navigation events emitted.
* **Test 2.4: Power meter pairing with configured zones emits NO event**:
  * Mock `isPwrZonesConfigured == true`.
  * Call `onPairedChanged(powerDeviceId, true)`.
  * Assert zero navigation events emitted.
* **Test 2.5: Non-physiological sensor pairing emits NO event**:
  * Call `onPairedChanged(speedDeviceId, true)` for `DeviceType.BIKE_SPEED`.
  * Assert zero navigation events emitted.
  * Call `onPairedChanged(cadenceDeviceId, true)` for `DeviceType.BIKE_CADENCE`.
  * Assert zero navigation events emitted.
* **Test 2.6: Unpairing sensor emits NO event**:
  * Call `onPairedChanged(hrmDeviceId, false)` transitioning `isPaired: true -> false`.
  * Assert zero navigation events emitted.

#### [TST-UI-337.3]: Navigation Route Parameter Formatting (`NavRoutesZoneTest.kt`)
* **Test 3.1: Parameterized route builder**:
  * Assert `NavRoutes.trainingZones(initialTab = 0, promptReason = "hr_paired") == "training_zones?initialTab=0&promptReason=hr_paired"`.
  * Assert `NavRoutes.trainingZones(initialTab = 2, promptReason = "pwr_paired") == "training_zones?initialTab=2&promptReason=pwr_paired"`.
  * Assert `NavRoutes.trainingZones() == "training_zones"`.
* **Test 3.2: Drawer item ID resolution**:
  * Assert `NavRoutes.toDrawerItemId("training_zones?initialTab=2&promptReason=pwr_paired") == R.id.drawer_training_zones`.

#### [TST-UI-337.4]: Contextual Guidance Banner Contract (`ZoneOnboardingBannerContractTest.kt`)
* **Test 4.1: HR banner copy**:
  * Pass `promptReason = "hr_paired"`. Assert string resource mapped to `R.string.zone_onboarding_hr_banner`.
* **Test 4.2: Power banner copy**:
  * Pass `promptReason = "pwr_paired"`. Assert string resource mapped to `R.string.zone_onboarding_pwr_banner`.
* **Test 4.3: Dismiss callback**:
  * Trigger dismiss action on banner. Assert dismiss callback executed.

#### [TST-UI-337.5]: 9-Language Localization Audit (`ZoneOnboardingLocalizationTest.kt`)
* **Test 5.1: Key existence across 9 locales**:
  * Check `zone_onboarding_hr_banner`, `zone_onboarding_pwr_banner`, `zone_onboarding_dismiss` across:
    - `values/` (EN), `values-de/` (DE), `values-es/` (ES), `values-fr/` (FR), `values-it/` (IT), `values-ja/` (JA), `values-nl/` (NL), `values-pl/` (PL), `values-pt/` (PT).
  * Assert all keys exist and are non-empty.
* **Test 5.2: Format Token Parity**:
  * Assert identical format specifier counts, types, and positions across all translations.

#### [TST-UI-337.6]: Clean-Room Regression Execution
* Run `./gradlew testDebugUnitTest`.
* Assert 100% test pass rate with 0 regressions.

---

## 3. Bidirectional Traceability Matrix

| Requirement Clause | Specification ID | Test Class | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-374.1` (Pairing Detection & Sensor Discrimination) | `[TST-UI-337.2]` | `DeviceListViewModelPairingTest` | Interception of `isPaired: false -> true`, classification of `HRM` / `BIKE_POWER`, silent completion for other types. |
| `REQ-UI-374.2` (Configuration Guard & Migration) | `[TST-UI-337.1]` | `SettingsDataStoreConfiguredTest` | `is_hr_zones_configured` / `is_pwr_zones_configured` persistence, migration heuristics, auto-calc transitions. |
| `REQ-UI-374.3` (Deep-Link Navigation & Route) | `[TST-UI-337.3]` | `NavRoutesZoneTest` | Parameterized route building, query parsing, drawer ID mapping. |
| `REQ-UI-374.4` (Contextual Guidance Banner) | `[TST-UI-337.4]` | `ZoneOnboardingBannerContractTest` | Banner rendering, prompt reason resolution, dismiss callback. |
| `REQ-UI-374.5` (9-Language Localization Parity) | `[TST-UI-337.5]` | `ZoneOnboardingLocalizationTest` | 100% key presence and format token parity across 9 locales. |
| System Non-Regression | `[TST-UI-337.6]` | Full Suite | `./gradlew testDebugUnitTest` with 100% pass rate. |
