# Stage 1 Analysis: ATT-3189 - Navigate to zone settings upon pairing new heart rate monitor or power meter when unconfigured

**Ticket**: [ATT-3189](https://rainerblind.atlassian.net/browse/ATT-3189)  
**Sub-task**: [ATT-3615](https://rainerblind.atlassian.net/browse/ATT-3615) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-3189`  
**Requirement Mapping**: `REQ-UI-374`  
**Test Mapping**: `TST-UI-337`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

When an athlete pairs a new heart rate monitor (BLE / ANT+) or cycling power meter for the first time, their personal physiological training zones (HR 5-zone model or Coggan 7-zone power model) typically remain at generic system defaults. Without configured zones:
1. **Live Cockpit Inaccuracy**: Real-time telemetry tiles in the cockpit (which dynamically highlight zone borders and text colors based on configured thresholds via `REQ-UI-370`) fail to provide personalized biofeedback.
2. **Post-Workout Analytics Degradation**: Aftermath summary cards (such as the 3-way distribution bars from `REQ-UI-372`) cannot accurately evaluate whether an athlete stayed in targeted aerobic, tempo, or threshold training zones.
3. **Discoverability Friction**: Novice athletes are often unaware that physiological zones exist or where to locate the configuration screen within the settings drawer.

To eliminate this onboarding friction without annoying experienced athletes, the application must detect when a newly paired sensor is physiological (`DeviceType.HRM` or `DeviceType.BIKE_POWER`), check whether zones for that specific metric are unconfigured, and if so, seamlessly navigate the user to the corresponding tab in Zone Settings (`NavRoutes.TRAINING_ZONES`) with a contextual onboarding guidance banner.

Experienced athletes whose zones are already customized or configured must experience zero disruption: pairing completes silently on the sensor management screen.

---

## 2. Root Cause & Architectural Gap Analysis (Forensic Investigation)

### 2.1 Existing Pairing Flow
In `aTrainingTracker`, sensor discovery and pairing is managed via:
1. `DeviceListViewModel.kt` (`onPairedChanged(deviceId, isPaired)`):
   ```kotlin
   fun onPairedChanged(deviceId: Long, isPaired: Boolean) {
       viewModelScope.launch {
           val currentState = devicesDBRepository.getDeviceSnapshotById(deviceId) ?: return@launch
           val newState = currentState.copy(isPaired = !currentState.isPaired)
           devicesDBRepository.updateDevice(newState)
       }
   }
   ```
2. `DeviceDataRepository.kt`:
   - Updates `DevicesDatabaseManager` SQLite database table (`DEVICES.PAIRED = 1`).
   - Dispatches `BANALService.PAIRING_CHANGED` broadcast to trigger hardware connection.
3. `DevicesTabbedScreen.kt`:
   - Hosted inside `ATrainingTrackerApp.kt` on `NavRoutes.SENSORS`.
   - Listens to device discovery events and renders sensor tabs (`Verfügbar`, `Gekoppelt`, `Alle`).

### 2.2 Architectural Gaps
1. **No Explicit Zone Configuration Flags in DataStore**:
   - `SettingsDataStore.kt` tracks manual customization flags (`pwr_bike_zones_customized`, `hr_run_zones_customized`, `hr_bike_zones_customized`) and calculation baselines (`cycling_ftp`, `hr_max`), but does not have explicit `is_hr_zones_configured` or `is_pwr_zones_configured` flags that distinguish fresh, untouched installs from athlete-confirmed profiles.
2. **Missing Pairing Interception & Discrimination**:
   - `DeviceListViewModel` flips `isPaired` without evaluating whether the device is physiological (`DeviceType.HRM` vs `DeviceType.BIKE_POWER` vs non-physiological like `BIKE_SPEED`, `RUN_SPEED`, `ENVIRONMENT`).
   - `DeviceListViewModel` lacks a one-time event channel (`SharedFlow` or `Channel`) to dispatch navigation directives to the UI layer.
3. **Static Navigation Route Without Parameterized Tab Selection**:
   - In `NavRoutes.kt`, `TRAINING_ZONES = "training_zones"` is a static string with no query parameters.
   - In `ATrainingTrackerApp.kt`:
     ```kotlin
     composable(NavRoutes.TRAINING_ZONES) {
         ZoneSettingsScreen()
     }
     ```
     `ZoneSettingsScreen` is invoked with default `initialTab = 0` and no ability to pass an initial tab index or prompt reason from deep-links.
4. **Lack of Contextual Guidance Banner in Zone Settings**:
   - `ZoneSettingsScreen` in `ZonesSettingsActivity.kt` has no awareness of why it was opened (e.g. general drawer navigation vs onboarding redirection from pairing). It lacks a dismissable onboarding banner explaining that a new sensor was paired.
5. **Localization Resources**:
   - Lacks onboarding prompt strings across all 9 application locales.

---

## 3. Call-Site & Component Audit

| Component / File | Current Role | Changes Required for ATT-3189 |
| :--- | :--- | :--- |
| `SettingsDataStore.kt` | Persistence of preferences & zone thresholds | 1. Add `KEY_IS_HR_ZONES_CONFIGURED = booleanPreferencesKey("is_hr_zones_configured")`.<br>2. Add `KEY_IS_PWR_ZONES_CONFIGURED = booleanPreferencesKey("is_pwr_zones_configured")`.<br>3. Expose `isHrZonesConfigured(): Flow<Boolean>` and `isPwrZonesConfigured(): Flow<Boolean>`.<br>4. Implement backward-compatible migration: if key is null/unset, return `true` if baselines or custom flags are set (`hr_max != null` / `cycling_ftp != null`), otherwise `false`.<br>5. Set to `true` when auto-calculating, manually saving, or dismissing onboarding. |
| `DeviceListViewModel.kt` | Manages device list UI state and handles pair toggling | 1. Inject `SettingsDataStore` (or create via application context).<br>2. In `onPairedChanged(deviceId, isPaired)`, detect transition from `false` to `true`.<br>3. If `device.deviceType == DeviceType.HRM` and `!isHrZonesConfigured`, emit `ZoneOnboardingEvent(initialTab = 0, promptReason = PromptReason.HR_PAIRED)`.<br>4. If `device.deviceType == DeviceType.BIKE_POWER` and `!isPwrZonesConfigured`, emit `ZoneOnboardingEvent(initialTab = 2, promptReason = PromptReason.PWR_PAIRED)`.<br>5. Expose `zoneNavigationEvent: SharedFlow<ZoneOnboardingEvent>`. |
| `DevicesTabbedScreen.kt` | Compose screen hosting device management | 1. Accept callback `onNavigateToZones: (initialTab: Int, promptReason: String?) -> Unit`.<br>2. Collect `listViewModel.zoneNavigationEvent` and trigger `onNavigateToZones`. |
| `NavRoutes.kt` | Central navigation routes | 1. Add `const val TRAINING_ZONES_PATTERN = "training_zones?initialTab={initialTab}&promptReason={promptReason}"`.<br>2. Add `const val ARG_INITIAL_TAB = "initialTab"`.<br>3. Add `const val ARG_PROMPT_REASON = "promptReason"`.<br>4. Add builder `fun trainingZones(initialTab: Int = 0, promptReason: String? = null): String`. |
| `ATrainingTrackerApp.kt` | Top-level Compose navigation graph | 1. Pass `onNavigateToZones` to `DevicesTabbedScreen` navigating to `NavRoutes.trainingZones(...)`.<br>2. Update `composable(NavRoutes.TRAINING_ZONES_PATTERN, arguments = ...)` to parse `initialTab` and `promptReason` and pass to `ZoneSettingsScreen`. |
| `ZonesSettingsActivity.kt` | Standalone activity & `ZoneSettingsScreen` composable | 1. Support `initialTab` and `promptReason` from Intent extras and Compose parameters.<br>2. Render non-intrusive `ZoneOnboardingBanner` when `promptReason` is present.<br>3. When user dismisses banner or saves cutoffs, set corresponding configured flag to `true`. |
| `res/values*/strings.xml` | Localization (9 locales) | Add `zone_onboarding_hr_banner`, `zone_onboarding_pwr_banner`, `zone_onboarding_dismiss` across EN, DE, ES, FR, IT, JA, NL, PL, PT. |

---

## 4. State Machine & Event Flow

```
[Athlete in DevicesTabbedScreen]
               │
               ▼
[Taps Pair on Unpaired Sensor (isPaired: false -> true)]
               │
               ▼
   [Is Sensor Physiological?]
       ├── NO (Speed, Cadence, GPS, Temp) ──────► [Silent Pairing Complete, Stay on Screen]
       │
       └── YES (DeviceType.HRM or BIKE_POWER)
               │
               ▼
   [Are Zones Already Configured for this Metric?]
       ├── YES (is_hr_zones_configured == true or is_pwr_zones_configured == true) ──► [Silent Pairing Complete, Stay on Screen]
       │
       └── NO (is_hr_zones_configured == false or is_pwr_zones_configured == false)
               │
               ▼
   [Emit ZoneOnboardingNavigationEvent]
               │
               ▼
   [NavRoutes.TRAINING_ZONES?initialTab=X&promptReason=Y]
               │
               ▼
   [ZoneSettingsScreen Renders with Contextual Banner]
               │
   ┌───────────┴──────────────────────────────┐
   ▼                                          ▼
[Athlete Calculates / Saves Zones]     [Athlete Dismisses / Navigates Back]
   │                                          │
   ▼                                          ▼
[Sets is_zones_configured = true]      [Sets is_zones_configured = true]
   │                                          │
   └───────────────────┬──────────────────────┘
                       ▼
         [Returns to NavRoutes.SENSORS]
```

---

## 5. Chesterton's Fence Archaeology (`REQ-PRO-022`) & Invariant Preservation

### 5.1 Requirement Archaeology
1. **Target Requirement**: Net-new requirement `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*).
2. **Historical Origin**:
   - `Sprint 2026-40.5` (`ATT-1390`, commit `e3966ebf`): Introduced Zone Settings activity.
   - `Sprint 2026-42.4` (`ATT-3184`, commit `0da639ba`): Migrated cycling power to 7-zone Coggan model.
   - `Sprint 2026-42.4` (`ATT-3188`, commit `c800ca58`): Added automated zone calculation engine and baselines.
   - Active ticket: `ATT-3189`.
3. **Root Reason for Existing Formulation**:
   - Sensor pairing historically only managed Bluetooth/ANT+ connectivity records without integration into training analytics or athlete profile onboarding.
4. **Preserved Invariants**:
   - **Unconfigured-Only Navigation Invariant**: Navigation MUST NOT occur if the relevant zone configuration flag is already `true`.
   - **Physiological-Only Sensor Invariant**: Non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, etc.) MUST NEVER trigger zone navigation.
   - **Single-Activity Compose Navigation Invariant**: Deep-linking is handled within `ATrainingTrackerApp.kt` via standard NavController routing; no rogue Activity tasks or broken back stacks.
   - **Back Navigation Hygiene**: Tapping back from Zone Settings returns cleanly to `NavRoutes.SENSORS` with zero state corruption.
   - **100% Test Pass Rate**: Full unit test suite must pass with 0 failures and 0 regressions.

---

## 6. Scope Delineation (User Scope Grounding - `ATT-1250`)

### In-Scope
- Detecting pairing transitions of `DeviceType.HRM` and `DeviceType.BIKE_POWER`.
- DataStore preference keys `is_hr_zones_configured` and `is_pwr_zones_configured` with migration heuristics.
- NavController parameterized route `NavRoutes.trainingZones(initialTab, promptReason)`.
- Contextual guidance banner on `ZoneSettingsScreen`.
- 9-language translation parity for banner copy.
- Unit tests covering pairing detection, configuration flag logic, and navigation event firing.

### Out-of-Scope
- Modifying Bluetooth LE GATT discovery or ANT+ connection protocols.
- Auto-pairing sensors without athlete confirmation.
- Modifying zone calculation algorithms (handled in `ATT-3188`).
- Modifying workout recording telemetry services.

---

## 7. Risk Rating & Technical Justification

* **Risk Rating**: **LOW**
* **Justification**:
  - The feature is purely reactive to explicit user pairing taps.
  - Guarded by boolean flags in DataStore, preventing repeated navigations.
  - Does not modify SQLite schemas, low-level BLE/ANT+ drivers, or background tracking services.
  - Completely decoupled via Kotlin Coroutines `SharedFlow` events.

---

## 8. Recommendation

* **Recommendation**: **RECOMMEND PASS**
* Proceed to Stage 2 (Requirement & Test Specification) to formalize `REQ-UI-374` and `TST-UI-337`.
