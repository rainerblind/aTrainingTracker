# Stage 5 Walkthrough: ATT-1296 - Real-time W' Balance (W-Prime Battery) expenditure and recovery tracking

**Ticket**: [ATT-1296](https://atrainingtracker.atlassian.net/browse/ATT-1296)  
**Sub-task**: [ATT-4270](https://atrainingtracker.atlassian.net/browse/ATT-4270) (`[Test]`)  
**Parent Epic**: [ATT-1153](https://atrainingtracker.atlassian.net/browse/ATT-1153) (*Sensor Architecture & Concurrency Stability*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-416` (*Real-time W' Balance Expenditure, Reconstitution, Battery Gauge Bar, and Warning Alert*)  
**Test Spec ID**: `TST-UI-379`  
**Branch**: `feature/ATT-1296`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Verification Outcome

ATT-1296 delivers real-time anaerobic work capacity tracking via an intuitive **W' Balance Battery Gauge Bar** embedded directly into the live cockpit tiles (`SensorType.W_PRIME_BAL`), providing instant biofeedback during suprathreshold surges and recovery intervals.

### 1.1 Architecture & Implementation Highlights (SWE.2 & SWE.3)
1. **Low-Level Device Driver Decoupling (`REQ-UI-416.1`)**:
   * Decoupled `CyclingPowerMetricsDevice.java` from UI `SettingsDataStore.kt` by introducing constant `DEFAULT_FALLBACK_CP_WATTS = 200`.
   * Hardened multi-tier Critical Power fallback: Configured CP -> Configured FTP -> Fallback CP (`200W`).
   * Hardened `resetSession()` to restore full $W'$ capacity whenever FTP or fallback CP is present.
   * Continuous 1-Hz depletion $(P - CP) \cdot 1\text{s}$ above Critical Power and exponential reconstitution at $P = 0\text{ W}$ during pauses. Zero memory allocation on hot path.
2. **Immutable Math & State Model (`REQ-UI-416.2`)**:
   * `WPrimeBatteryState.kt` and `WPrimeBatteryMath.kt`: Pure, immutable calculation functions with bounds clamping $[0..100]$ and $[0.0f..1.0f]$.
   * 4-tier warning level categorization: `NORMAL` (>30%), `LOW` (20–30%), `CRITICAL` (<20%), and `DEPLETED` (≤0%).
   * 3000ms reactive staleness debounce.
3. **Jetpack Compose Battery Gauge Bar (`REQ-UI-416.3`)**:
   * `WPrimeBatteryGaugeBar.kt`: Fixed 14.dp vertical slot invariant, 3.dp end-cap boundary padding preventing clipping, 2.dp terminal battery cap, and smooth `animateFloatAsState` fill interpolation (300ms `FastOutSlowInEasing`).
   * High-contrast outline (`MaterialTheme.colorScheme.outlineVariant`), dimmed track opacity (`0.35f`) during staleness, and progressive disclosure collapsing when space is constrained.
4. **Cockpit Tile Integration & Warning Alerts (`REQ-UI-416.4`)**:
   * `SensorFieldState.kt` and `SensorFieldView.kt`: Dynamic card border elevation to 1.5.dp Warning Red (`MaterialTheme.colorScheme.error`) during critical discharge (<20%), with Compose `remember` caching.
   * `TrackingViewModel.kt`: Structured Coroutine concurrency managing 3000ms stale debounce job without blocking locks.
5. **Full 9-Language Localization Parity (`REQ-UI-416.5`)**:
   * Added `w_prime_battery_pct` (`%1$d%%`) and `w_prime_warning_low_battery` across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Requirement & Test Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-UI-416.1` | `TST-UI-379.2` | Unit / Driver | `CyclingPowerMetricsDeviceTest.testResolveEffectiveCpWatts_multiTierFallback` & `testResetSessionRestoresCapacityWithFtpFallback` | **PASSED** | `Verified` |
| `REQ-UI-416.2` | `TST-UI-379.1` | Unit / Math | `WPrimeBatteryMathTest` (10 unit tests: clamping, normalization, warning thresholds, staleness, denominator stability) | **PASSED** | `Verified` |
| `REQ-UI-416.3` | `TST-UI-379.3` | UI / Layout | `WPrimeBatteryGaugeBarLayoutTest` (fixed 14.dp height, 3.dp anti-clipping padding, font scaling stability, progressive disclosure) | **PASSED** | `Verified` |
| `REQ-UI-416.4` | `TST-UI-379.3` | UI / Cockpit | `SensorFieldWPrimeBatteryTest` & `SensorFieldStyleContractTest` (warning border, stale dimming, rapid debounce cancellation) | **PASSED** | `Verified` |
| `REQ-UI-416.5` | `TST-UI-379.4` | Localization | `WPrimeBatteryLocalizationTest` (100% translation parity across 9 locales) | **PASSED** | `Verified` |
| `REQ-UI-416` | `TST-UI-379.5` | Full Regression | Clean-room test suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Test Execution Verification

### 3.1 Targeted Test Suite
* `CyclingPowerMetricsDeviceTest`: 100% pass (Multi-tier CP fallback, FTP fallback, full resetSession restoration).
* `WPrimeBatteryMathTest`: 100% pass (Zero capacity division-by-zero protection, boundary clamping [0..100], 20% critical warning threshold, 3000ms staleness timeout).
* `WPrimeBatteryGaugeBarLayoutTest`: 100% pass (14.dp container height invariant, 3.dp end padding, font scaling robustness with `TextOverflow.Clip`).
* `SensorFieldWPrimeBatteryTest`: 100% pass (SensorFieldState attachment, warning border flag, rapid debounce job cancellation).
* `SensorFieldStyleContractTest`: 100% pass (Structural outlineVariant contract preserved).
* `WPrimeBatteryLocalizationTest`: 100% pass (Exact string keys and `%1$d%%` format specifiers across all 9 locales).

### 3.2 Clean-Room Full Regression Suite
* `./gradlew testDebugUnitTest` executed with 100% pass rate across the full application test suite.

---

## 4. Modified & Created Files

* [CyclingPowerMetricsDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java):
  * Added `DEFAULT_FALLBACK_CP_WATTS = 200`.
  * Decoupled from `SettingsDataStore.kt`.
  * Hardened `resolveEffectiveCpWatts()` and `resetSession()`.
* [WPrimeBatteryState.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/WPrimeBatteryState.kt): Immutable battery state and warning levels.
* [WPrimeBatteryMath.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/WPrimeBatteryMath.kt): Pure math functions and Skiba model KDocs.
* [WPrimeBatteryGaugeBar.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/WPrimeBatteryGaugeBar.kt): Fixed 14.dp battery gauge composable.
* [SensorFieldState.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt): Added `wPrimeBatteryState`.
* [SensorFieldView.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt): Cockpit battery bar rendering and dynamic Warning Red border.
* [TrackingViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt): Coroutine-based 3000ms staleness debounce.
* Localization files across 9 languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* Automated test suites:
  * `CyclingPowerMetricsDeviceTest.kt`
  * `WPrimeBatteryMathTest.kt`
  * `WPrimeBatteryLocalizationTest.kt`
  * `WPrimeBatteryGaugeBarLayoutTest.kt`
  * `SensorFieldWPrimeBatteryTest.kt`
* Living documentation:
  * [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) (`REQ-UI-416` -> `Verified`)
  * [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) (`TST-UI-379` -> `Verified`)
