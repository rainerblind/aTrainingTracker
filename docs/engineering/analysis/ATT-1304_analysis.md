# Stage 1 Analysis: Dedicated Advanced Tuning Preferences Screen with Parameter Validation and Reset-to-Defaults

**Ticket**: `ATT-1304` / Subtask: `ATT-1630`  
**Date**: 2026-09-29  
**Status**: Completed  
**Author**: AI Agent 1 (Implementer)  

---

## 1. Problem Statement & Background
Several critical tracking heuristics, sensor filtering constants, and power-saving algorithms in `aTrainingTracker` currently rely on hardcoded constant literals scattered throughout the codebase. While these values were chosen as reasonable defaults during initial development, real-world deployment across fragmented Android hardware manifests wide variations in GPS receiver noise, barometer drift, ambient display brightness, and user physical profiles.

Without a dedicated UI to inspect and adjust these mathematical heuristics:
1. Power users and testers cannot adapt tracking behavior to noisy sensors without recompiling the application.
2. Field-testing optimal battery-saving thresholds on varying AMOLED panels is slow and cumbersome.
3. Users have no centralized transparency into the heuristic parameters governing display dimming, GPS accuracy rejection, and gradient smoothing.

`ATT-1304` mandates creating a dedicated, isolated "Advanced Tuning Preferences" section featuring:
- Advisory warning notice alerting users to the expert nature of the parameters.
- Comprehensive inventory of safe, qualified tuning parameters.
- Strict value validation and clamping (`coerceIn`) to eliminate illegal states, division by zero, or unreadable screens.
- Prominent single-tap "Reset to Factory Defaults" action.
- Reactive DataStore persistence ensuring active tracking services and UI controllers adopt changed parameters without app restart.

---

## 2. Forensic Audit: Hardcoded Parameters Inventory & Qualification

A thorough code audit across active tracking and sensor packages identified the following candidates:

### A. AMOLED Battery Saver (ATT-1268)
Located in:
- `BatterySaverModel.kt` (`DimmingLevel.FULL_DIM = 0.25f`, `MEDIUM_DIM = 0.50f`, `WAKEUP_DURATION_MS = 15_000L`, `DOWNWARD_HYSTERESIS_MS = 3_000L`)
- `BatterySaverStateMachine.kt` (Flat terrain `< 2.0%`, Steep climb `> 5.0%`)
- `TrackingTabsScreen.kt` (Slope validity speed cutoff `speed > 0.5 m/s`)

| Parameter Name | Current Hardcoded Value | Qualified for Exposure? | Proposed Safe Range (Clamp) | Rationale |
|---|---|---|---|---|
| Full Dimming Factor | `0.25f` (25%) | **Yes** | `0.05f .. 0.50f` (5% - 50%) | Allows maximum power savings on AMOLED displays while maintaining readability. Clamped above 5% safety floor. |
| Medium Dimming Factor | `0.50f` (50%) | **Yes** | `0.20f .. 0.90f` (20% - 90%) | Balanced brightness for tempo efforts. Clamped to remain $\ge$ full dimming factor. |
| Flat / Downhill Slope Threshold | `2.0%` | **Yes** | `0.0% .. 5.0%` | Grade threshold below which full dimming is allowed. |
| Steep Climb Slope Threshold | `5.0%` | **Yes** | `2.0% .. 15.0%` | Grade threshold above which full screen illumination is forced. Clamped to remain $\ge$ flat slope threshold. |
| Gesture/Touch Wake-up Duration | `15` seconds | **Yes** | `5 .. 60` seconds | Controls duration of full brightness upon proximity sensor hover or touch gesture. |
| Downward Dimming Damping Delay | `3` seconds | **Yes** | `1 .. 15` seconds | Hysteresis damping to prevent flickering when transitioning from bright to dimmed states. |

### B. GPS & Location Filtering
Located in:
- `SpeedAndLocationDevice.java` (`ACCURACY_THRESHOLD = 200.0` meters)
- `SpeedAndLocationDevice.java` (`SAMPLING_TIME = 1000` ms)

| Parameter Name | Current Hardcoded Value | Qualified for Exposure? | Proposed Safe Range (Clamp) | Rationale |
|---|---|---|---|---|
| GPS Accuracy Threshold | `200.0` meters | **Yes** | `10.0 .. 500.0` meters | Drops GPS fixes with horizontal accuracy worse than threshold. Outdoor cyclists may tighten to 30m; trail runners under dense canopy may widen to 300m. |
| GPS Sampling Interval | `1000` ms | **No (Reject)** | N/A | Android LocationManager / Fused Location provider battery optimizations and internal accumulator expectations rely on 1Hz sampling; changing this breaks distance accumulator rate assumptions. |

### C. Altitude & Vertical Speed / Gradient Filtering
Located in:
- `VerticalSpeedAndSlopeDevice.java` (`cAltitudeFilter = 21` s, `cAltitudeSuperFilter = 300` s, `MIN_SPEED = 0.5` m/s)

| Parameter Name | Current Hardcoded Value | Qualified for Exposure? | Proposed Safe Range (Clamp) | Rationale |
|---|---|---|---|---|
| Altitude Filter Window | `21` seconds | **Yes** | `5 .. 60` seconds | Moving average window for instantaneous barometric altitude / vertical speed. Shorter = more responsive, longer = smoother. |
| Slope Minimum Speed | `0.5` m/s (~1.8 km/h) | **Yes** | `0.2 .. 2.0` m/s | Rejects gradient calculations when speed is near zero to avoid division by zero and noisy vertical jumps while stationary. |
| Altitude Super-Filter Window | `300` seconds | **No (Deferred)** | N/A | Long-term ascent/descent accumulator baseline; altering without extensive field validation could distort elevation gain calculations. |

---

## 3. Architecture & Design Specification

### 3.1 Persistence & Data Model
Create `TuningPreferencesDataStore.kt` utilizing the application's unified DataStore (`user_settings`):
- Exposes typed Kotlin `Flow`s for reactive observation.
- Implements strict validation and clamping in setters (`coerceIn`).
- Encapsulates battle-tested factory defaults in a companion data model `TuningPreferencesDefaults`.
- Implements `resetToDefaults()` to clear or restore all keys to standard values in a single atomic edit transaction.
- Bridges to Java via `SettingsDataStoreJavaHelper.kt` / `TuningPreferencesJavaHelper.kt` for non-coroutine callers (`SpeedAndLocationDevice`, `VerticalSpeedAndSlopeDevice`).

### 3.2 UI & Navigation Integration
- Add `SettingsBottomSheetType.ADVANCED_TUNING` to `NavRoutes.kt`.
- Integrate `R.id.drawer_advanced_tuning` into `AppNavigationDrawer.kt` within `drawer__settings` category.
- Also provide an entry point link from `DisplaySettingsDialog.kt` ("Erweiterte Abstimmung / Advanced Tuning").
- Create `AdvancedTuningDialog.kt` (using standard `AppBottomSheetContent` and Material 3 design system):
  - **Advisory Warning Notice Card**: Prominent banner cautioning that modifying these parameters directly impacts tracking accuracy and battery life.
  - **Categorized Sections**:
    1. *AMOLED Battery Saver*: Full dimming brightness, medium dimming brightness, flat slope cutoff, steep slope cutoff, wake-up duration, dimming delay.
    2. *GPS & Location Filtering*: Accuracy threshold.
    3. *Gradient & Barometer Dynamics*: Altitude smoothing window, slope minimum speed cutoff.
  - **Parameter Controls**: Slider + Number display with contextual helper description and default value tag (`Standard: X`).
  - **Bottom Action Bar**: Prominent "Auf Werkseinstellungen zurücksetzen" (Reset to Defaults) button and "Fertig / Schließen" button.

### 3.3 Dynamic Reconfigurability
- `BatterySaverController` / `BatterySaverStateMachine`:
  - Updated to accept a dynamic `BatterySaverTuningConfig` or observe `TuningPreferencesDataStore`.
  - State machine dynamically updates thresholds and dimming factor targets without restarting or losing current workout state.
- `SpeedAndLocationDevice`:
  - Reads `getGpsAccuracyThreshold()` dynamically on each location update.
- `VerticalSpeedAndSlopeDevice` & `TrackingTabsScreen`:
  - Uses configured `slopeMinSpeedMps` dynamically for gradient calculations.

---

## 4. Invariants & Safety Invariants
1. **Brightness Clamping Invariant**: Full dimming factor $\ge 0.05$ (safety floor to prevent screen blackout) and $\le$ Medium dimming factor $\le 1.0$.
2. **Slope Consistency Invariant**: Flat slope threshold $\le$ Steep slope threshold.
3. **Speed Cutoff Non-Zero Invariant**: Minimum speed for slope $\ge 0.2\text{ m/s}$ (prevents division by zero).
4. **Accuracy Threshold Safety**: GPS accuracy threshold $\ge 10\text{ m}$ (prevents dropping all GPS fixes under real-world conditions).
5. **Atomic Factory Reset**: Single tap on "Reset to Defaults" restores all parameters atomically to known good factory defaults.
6. **Localization Parity**: 100% string coverage across all 9 supported languages (`de`, `en`, `fr`, `es`, `it`, `ja`, `nl`, `pl`, `pt`).

---

## 5. Test & Verification Strategy
- **Unit Tests**:
  - `TuningPreferencesDataStoreTest`: Verify initial defaults, clamping logic (`coerceIn`), persistence, reactive flow emissions, and reset-to-defaults functionality.
  - `BatterySaverDynamicTuningTest`: Verify that `BatterySaverStateMachine` and `BatterySaverController` react correctly when tuning parameters are adjusted in real time.
- **Contract Tests**:
  - Ensure drawer navigation maps `R.id.drawer_advanced_tuning` to `SettingsBottomSheetType.ADVANCED_TUNING`.
- **Regression Verification**:
  - Full `./gradlew testDebugUnitTest` suite execution.
