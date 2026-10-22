# Stage 5 Walkthrough - ATT-3189: Navigate to zone settings upon pairing new heart rate monitor or power meter when unconfigured

**Ticket**: [ATT-3189](https://rainerblind.atlassian.net/browse/ATT-3189)  
**Stage 5 Subtask**: [ATT-3619](https://rainerblind.atlassian.net/browse/ATT-3619) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-374` (`Verified`)  
**Test Mapping**: `TST-UI-337` (`Verified`)  
**Branch**: `feature/ATT-3189`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Root Cause

When an athlete pairs a heart rate monitor or cycling power meter for the first time, their personal physiological training zones (5-zone HR scheme and 7-zone Coggan power model) typically remain at generic system defaults. Without configured zones, real-time cockpit tile color accents (`REQ-UI-370`) and aftermath distribution analytics (`REQ-UI-372`) fail to provide meaningful personalized feedback.

Prior to ATT-3189:
1. Pairing completed entirely on the sensor screen without guiding the user to zone configuration.
2. The user had to discover and navigate to Zone Settings through the drawer menu manually.
3. System lacked explicit configuration flags (`is_hr_zones_configured`, `is_pwr_zones_configured`) to distinguish between users with deliberate baseline setups and unconfigured users.

ATT-3189 resolves these issues with an end-to-end ASPICE SWE.3/SWE.4 implementation:
- **Configuration Guard & Migration Heuristics (`SettingsDataStore.kt`)**: Tracks explicit configuration status via `KEY_IS_HR_ZONES_CONFIGURED` and `KEY_IS_PWR_ZONES_CONFIGURED`. Uses backward-compatible migration heuristics for existing users (`hr_max != null || hr_run_zones_customized || hr_bike_zones_customized` and `cycling_ftp != null || pwr_bike_zones_customized`). Automatic configuration marking on calculation or manual cutoff saves.
- **Pairing Interception & Sensor Discrimination (`DeviceListViewModel.kt`)**: Intercepts `isPaired: false -> true` transitions. Emits one-time `ZoneOnboardingEvent` for unconfigured `DeviceType.HRM` (tab 0, `hr_paired`) and `DeviceType.BIKE_POWER` (tab 2, `pwr_paired`). All non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, etc.) and unpairing transitions complete silently.
- **Parameterized Deep-Link Navigation (`NavRoutes.kt` & `ATrainingTrackerApp.kt`)**: Implements `training_zones?initialTab={initialTab}&promptReason={promptReason}` with single-activity Jetpack Compose back stack returning cleanly to `NavRoutes.SENSORS`.
- **Material 3 Contextual Guidance Banner (`ZoneOnboardingBanner.kt` & `ZonesSettingsActivity.kt`)**: Renders contextual guidance explaining the redirection with explicit dismissal marking the profile as configured.
- **9-Language Localization Parity**: Full string resources and format token parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*) -> **Status: Verified**
  * `REQ-UI-374.1`: Pairing Detection & Sensor Discrimination (`DeviceType.HRM`, `DeviceType.BIKE_POWER` vs others) -> **Verified**
  * `REQ-UI-374.2`: Zone Configuration Status Guard & Migration Heuristics (`SettingsDataStore`) -> **Verified**
  * `REQ-UI-374.3`: One-Time Deep-Link Navigation & Parameterized Route (`NavRoutes.trainingZones`) -> **Verified**
  * `REQ-UI-374.4`: Contextual Guidance Banner UI (`ZoneOnboardingBanner`) -> **Verified**
  * `REQ-UI-374.5`: 9-Language Localization Parity -> **Verified**
* **Test Mapping**: `TST-UI-337` (*Contextual Zone Settings Navigation Verification*) -> **Status: Verified**
  * `[TST-UI-337.1]`: Configuration Guard & Migration Heuristics Unit Tests (`SettingsDataStoreConfiguredTest.kt`) -> **Verified**
  * `[TST-UI-337.2]`: Pairing Detection & Sensor Discrimination Unit Tests (`DeviceListViewModelPairingTest.kt`) -> **Verified**
  * `[TST-UI-337.3]`: Navigation Route & Deep-Link Parameter Formatting Contract (`NavRoutesZoneTest.kt`) -> **Verified**
  * `[TST-UI-337.4]`: Contextual Guidance Banner Component & Dismissal Contract (`ZoneOnboardingBannerContractTest.kt`) -> **Verified**
  * `[TST-UI-337.5]`: 9-Language Localization & Format Token Parity Audit (`ZoneOnboardingLocalizationTest.kt`) -> **Verified**
  * `[TST-UI-337.6]`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`) -> **Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Unconfigured-Only Navigation Invariant**: Navigation strictly occurs when the athlete's zones for that specific metric are unconfigured. Pairing with configured zones or re-pairing existing sensors completes silently on the sensor screen.
2. **Physiological-Only Discrimination Invariant**: Only `DeviceType.HRM` and `DeviceType.BIKE_POWER` trigger zone onboarding. Non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, etc.) NEVER trigger zone navigation.
3. **Smooth Back-Stack Navigation**: Tapping back returns cleanly to `NavRoutes.SENSORS` with zero state corruption, loop trapping, or crashes.
4. **Single-Activity Architecture**: Deep-linking is orchestrated within `ATrainingTrackerApp.kt` via standard Jetpack Compose `NavController`. No secondary Activity tasks are spawned.
5. **Historical Workout Immutability**: All completed workouts and aggregates in SQLite (`WorkoutSummaries.db`, `Extrema.db`) remain strictly immutable.
6. **Clean-Room Test Pass Rate**: 100% test pass rate across the full application unit test suite (0 regressions across 2,811+ tests).

---

## 4. Test Verification Results

### Summary of Targeted Unit Tests

| Test Class | Spec ID | Tests | Pass Rate | Key Validations |
| :--- | :--- | :--- | :--- | :--- |
| `SettingsDataStoreConfiguredTest` | `[TST-UI-337.1]` | 7 | **100% PASS** | Fresh install defaults (`false`), migration heuristics (HR max/customized, FTP/customized), explicit mutators, auto-save status updates. |
| `DeviceListViewModelPairingTest` | `[TST-UI-337.2]` | 6 | **100% PASS** | Unconfigured HRM -> tab 0 `hr_paired`, configured HRM -> silent, unconfigured BIKE_POWER -> tab 2 `pwr_paired`, configured BIKE_POWER -> silent, non-physiological sensors -> silent, unpairing -> silent. |
| `NavRoutesZoneTest` | `[TST-UI-337.3]` | 4 | **100% PASS** | URL query parameter formatting (`training_zones?initialTab=X&promptReason=Y`), default route generation, drawer item ID mapping. |
| `ZoneOnboardingBannerContractTest` | `[TST-UI-337.4]` | 4 | **100% PASS** | Banner composable contract, test tags, `DevicesTabbedScreen` event collection, `ATrainingTrackerApp` graph wiring, `ZonesSettingsActivity` dismissal handling. |
| `ZoneOnboardingLocalizationTest` | `[TST-UI-337.5]` | 2 | **100% PASS** | 100% translation presence and format token parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). |

### Clean-Room Regression Verification (`[TST-UI-337.6]`)
```bash
./gradlew testDebugUnitTest
```
- **Result**: BUILD SUCCESSFUL (2,811+ tests executed, 0 failures, 100% pass rate).
- **Duration**: ~2m 51s.

---

## 5. Visual Consistency & UI Layout Check (Rule 23)

- **Component**: `ZoneOnboardingBanner.kt`
- **Surface**: `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`)
- **Container Color**: `MaterialTheme.colorScheme.primaryContainer`
- **Text & Icon Color**: `MaterialTheme.colorScheme.onPrimaryContainer`
- **Shape & Padding**: `RoundedCornerShape(12.dp)`, `12.dp` internal padding, `16.dp` horizontal screen margins.
- **Action Button**: `TextButton` with `R.string.zone_onboarding_dismiss` ("Verstanden" / "Dismiss") aligned to end.

---

## 6. Living Documentation & Governance Check

- `docs/requirements.md`: `REQ-UI-374` -> `Verified`
- `docs/tests.md`: `TST-UI-337` -> `Verified`
- `python3 tools/verify_requirement_governance.py`: PASSED (0 violations).
