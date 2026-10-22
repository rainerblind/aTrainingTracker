# Stage 1 Analysis: Real-time W' Balance (W-Prime Battery) Expenditure and Recovery Tracking

**Ticket**: [ATT-1296](https://rainerblind.atlassian.net/browse/ATT-1296)  
**Sub-task**: [ATT-4266](https://rainerblind.atlassian.net/browse/ATT-4266) (`[Analysis]`)  
**Parent Epic**: [ATT-160](https://rainerblind.atlassian.net/browse/ATT-160) (*Cockpit & Live Tracking Engine*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Domain

Dr. Phil Skiba's $W'$ Balance ($W'_{\text{bal}}$) model represents an athlete's anaerobic work capacity—a finite energy reservoir (measured in kilojoules, typically 15.0–30.0 kJ) available above Critical Power ($CP$ or $FTP$). 
- When an athlete rides above $CP$ ($P(t) > CP$), $W'$ depletes at a rate proportional to the supra-threshold power surplus: $(P(t) - CP) \cdot \Delta t$.
- When riding below $CP$ ($P(t) \le CP$) or coasting/pausing ($P(t) = 0\text{ W}$), $W'$ reconstitutes exponentially with recovery time constant $\tau_{W'}(t) = 546 \cdot e^{-0.01 \cdot (CP - P(t))} + 316$.

Displaying remaining $W'$ during live cycling tracking as a dynamic "anaerobic battery gauge" gamifies pacing, provides immediate biofeedback during high-intensity attacks and climbs, and prevents catastrophic exhaustion ("blowing up") before summits or sprints.

### Identified Architectural Gaps

While sprint tickets ATT-3862 (`REQ-UI-399`) and ATT-3921 (`REQ-UI-409`) introduced physiological baselines for $CP$ and $W'$ in the Athlete Profile and added `SensorType.W_PRIME_BAL` to `CyclingPowerMetricsDevice` and default `BIKE_POWER` cockpit templates, critical live tracking and cockpit display gaps remain:

1. **Absence of Visual Battery Gauge in Cockpit Tiles (`SensorFieldView.kt`)**:
   - `SensorType.W_PRIME_BAL` currently renders as a plain scalar number formatted in kJ (e.g. `"18.5 kJ"` via `PowerToWeightFormatter`).
   - Cockpit tiles lack a visual battery bar component analogous to `ZoneGaugeBar` (`REQ-UI-376`) or `PedalPowerBalanceBar` (`REQ-UI-377`).
   - Athletes cannot gauge their remaining anaerobic energy capacity at a glance without mentally comparing the current kJ value against their configured total $W'$ capacity.

2. **Missing Percentage Battery Gauge Readout (100% Full down to 0% Depleted)**:
   - The live tile does not display remaining $W'$ as a percentage ($0\text{–}100\%$).
   - The UI does not expose percentage capacity alongside or within the gauge bar.

3. **Missing Color-Coded Warning Alert (< 20%)**:
   - Ticket ATT-1296 specifically mandates a color-coded warning alert when $W'$ drops below $20\%$.
   - Currently, no warning state, alert color transition (Normal Green $\to$ Low Amber $\to$ Critical Red), or tile accent exists when anaerobic reserves reach critical depletion levels.

4. **Inconsistent $CP$ Fallback in `CyclingPowerMetricsDevice.resetSession()`**:
   - In `CyclingPowerMetricsDevice.java:255`, `resetSession()` checks `mCpProvider.getCpWatts(mContext)` directly instead of `resolveEffectiveCpWatts()`. If an athlete has configured $FTP$ but not an explicit $CP$, `resetSession()` leaves `mCurrentWPrimeBalJoules = null` on session reset, breaking initial state until a live power sample triggers `calculateMetrics()`.

---

## 2. Historical Context & Chesterton's Fence

An archaeological review of requirements and commits tracing $W'$ Balance implementation reveals:

1. **`ATT-3862` / `REQ-UI-399` (`61ccb801dc18`)**:
   - Introduced `PowerPhysiologyCalculator.kt` with pure implementations of `calculateSkibaTauSeconds` and `stepWPrimeBalanceJoules`.
   - Added `SensorType.W_PRIME_BAL` to `SensorCategory.POWER_AND_PEDALING` and integrated 1-Hz computation into `CyclingPowerMetricsDevice.java`.
   - Added post-workout Aftermath summary and telemetry graph scrubbing (`TelemetryMetricType.W_PRIME_BAL`).
   - *Chesterton's Fence Invariant*: The underlying Skiba differential math and Aftermath telemetry models are robust and fully verified; our solution must build directly on `PowerPhysiologyCalculator` without changing mathematical formulas or breaking existing unit tests.

2. **`ATT-3921` / `REQ-UI-409` (`a6289096638b`)**:
   - Seeded `W_PRIME_BAL` into the dedicated Power tab for `ActivityType.BIKE_POWER`.
   - Added progressive disclosure gating: `W_PRIME_BAL` is rendered only when `enableCriticalPowerModel` is true in `TuningConfig`.
   - *Chesterton's Fence Invariant*: Progressive disclosure gating via `filterVisibleFieldsForTuning` and empty-row collapse must remain strictly preserved.

3. **`ATT-3106` (`REQ-UI-376`) & `ATT-3107` (`REQ-UI-377`)**:
   - Established the pattern for specialized inline visual indicators in `SensorFieldView.kt`:
     - `ZoneGaugeBar`: horizontal multi-segment track with needle indicator.
     - `PedalPowerBalanceBar`: center-anchored horizontal split track with dominant side and deviation indicator.
   - Both components adhere strictly to True-Black AMOLED guidelines (Rule 23), use pure Compose drawing, and integrate seamlessly within `SensorFieldState`.

---

## 3. Root Cause & Architectural Gap Analysis

| Component | Current State | Target State | Resolution Strategy |
| :--- | :--- | :--- | :--- |
| **`WPrimeBatteryState`** | Does not exist | Immutable data model capturing real-time battery state | Create `WPrimeBatteryState.kt` with `remainingKj`, `totalKj`, `percentage`, `batteryFraction`, `warningLevel`, `batteryColor`, `isWarning`, and `isStale`. |
| **`WPrimeBatteryMath`** | Does not exist | Pure, decoupled mathematical conversion engine | Create `WPrimeBatteryMath.kt` with 100% unit-tested calculation of battery percentage, warning levels, color mapping, and formatting. |
| **`WPrimeBatteryGaugeBar`** | Does not exist | High-visibility True-Black AMOLED battery gauge composable | Create `WPrimeBatteryGaugeBar.kt` rendering horizontal battery track, proportional colored fill, optional percentage badge, and critical warning glow. |
| **`SensorFieldState`** | Only has `zoneGaugeState` and `pedalBalanceState` | Includes `wPrimeBatteryState: WPrimeBatteryState? = null` | Add `wPrimeBatteryState` field to `SensorFieldState.kt`. |
| **`SensorFieldView`** | No battery bar; no warning highlight for $W'$ | Renders `WPrimeBatteryGaugeBar` beneath value; highlights warning when $< 20\%$ | Update `SensorFieldView.kt` to render battery bar and apply warning border/accent when `isWarning` is true. |
| **`TrackingViewModel`** | Ignores battery state for `W_PRIME_BAL` | Populates `wPrimeBatteryState` in `baseFields` and `applySensorData` | In `TrackingViewModel.kt`, compute `wPrimeBatteryState` using effective $W'$ capacity and live kJ value. |
| **`CyclingPowerMetricsDevice`** | `resetSession()` skips $FTP$ fallback | Uses `resolveEffectiveCpWatts()` consistently | Fix `resetSession()` in `CyclingPowerMetricsDevice.java` to call `resolveEffectiveCpWatts()`. |

---

## 4. Target Design & Component Decomposition

### A. Mathematical Engine & State Model (`WPrimeBatteryMath.kt`, `WPrimeBatteryState.kt`)
1. **`WPrimeWarningLevel`**:
   - `NORMAL`: Remaining $W' > 30\%$ (Healthy battery).
   - `LOW`: $20\% \le W' \le 30\%$ (Caution tier).
   - `CRITICAL`: $0\% < W' < 20\%$ (Severe depletion / impending exhaustion).
   - `DEPLETED`: $W' \le 0\%$ (Tank completely empty).
2. **`WPrimeBatteryState`**:
   - `remainingKj: Double?`: Remaining energy in kJ (null when stale or uninitialized).
   - `totalKj: Double`: Configured total capacity in kJ ($> 0$).
   - `percentage: Int`: Clamped integer percentage $[0..100]$.
   - `batteryFraction: Float?`: Normalized float $[0.0f..1.0f]$ (null when stale).
   - `warningLevel: WPrimeWarningLevel`: Classification enum (`NORMAL`, `LOW`, `CRITICAL`, `DEPLETED`).
   - `batteryColor: Color`: Semantic color:
     - Normal: Emerald / Cyan Green (`TTColor.WPrimeBalGreen` `#10B981`).
     - Low: Amber (`TTColor.Zone3` `#F59E0B`).
     - Critical / Depleted: Red (`TTColor.Zone5` / `MaterialTheme.colorScheme.error` `#EF4444`).
   - `isWarning: Boolean`: `percentage < 20 && !isStale`.
   - `isStale: Boolean`: Derived from data availability and staleness timeout (`lastReceivedTimestampMs == null || (now - lastReceivedTimestampMs > STALE_TIMEOUT_MS)` where `STALE_TIMEOUT_MS = 3000L`).
   - `timestampMs: Long`: Timestamp of the telemetry frame, ensuring temporal alignment and state atomicity with the parent `SensorFieldState`.
   - *In-Memory Backward Compatibility*: Added as `val wPrimeBatteryState: WPrimeBatteryState? = null` in `SensorFieldState`, fully preserving binary, reflective, and preview compatibility without touching database schemas.
3. **`WPrimeBatteryMath`**:
   - Pure function: `calculateBatteryState(wPrimeBalKj: Double?, totalWPrimeKj: Double?, lastReceivedTimestampMs: Long? = null, currentTimestampMs: Long = System.currentTimeMillis()): WPrimeBatteryState`
   - Handles edge cases: `totalWPrimeKj <= 0`, `NaN`, `Infinite`, unconfigured states, and bounds $[0.0, 1.0]$.

### B. UI Battery Gauge Bar (`WPrimeBatteryGaugeBar.kt`)
A purpose-built Jetpack Compose composable rendered inside cockpit sensor tiles:
1. **Track & Battery Terminal Geometry (Anti-Clipping & Layout Invariant)**:
   - Fixed-height container constrained via `Modifier.height(14.dp)` ensuring that the card height remains strictly constant whether the percentage badge is shown, hidden, or in a stale state.
   - Rounded capsule track (height 6.dp to 8.dp) with high-contrast outline (`outlineVariant.copy(alpha = 0.6f)`).
   - Terminal nipple / end cap icon on the right side of the gauge track to visually reinforce the battery metaphor.
   - **Boundary Anti-Clipping Padding**: The active fill bar is contained within an inner layout area with `endPadding = 3.dp` offset from the terminal nipple. This guarantees that at $100\%$ (full) capacity the fill never overlaps or clips the terminal cap, and at $0\%$ (empty) capacity renders cleanly as an empty outlined capsule.
2. **Proportional Fill & Smooth Animated Transitions**:
   - The fill fraction is animated via Compose `animateFloatAsState(targetValue = state.batteryFraction ?: 0.0f, animationSpec = tween(durationMillis = 300))` to ensure that telemetry step changes or mid-session $W'$ capacity modifications smoothly interpolate without jarring visual snaps or flickering.
   - Transitions smoothly between Green (> 30%), Amber (20–30%), and Red (< 20%).
3. **Percentage Badge**:
   - Displays compact percentage text (e.g. `"85%"` or `"15%"`) adjacent to or centered with the gauge when `viewSize >= ViewSize.NORMAL`.
4. **Stale Data Handling (UI Contract Invariant)**:
   - Track opacity dims to 0.35 alpha when telemetry is disconnected or stale, hiding the fill while preserving the 14.dp height slot.

### C. Live Cockpit Integration, Concurrency Guard & Layout Stability (`SensorFieldView.kt`, `TrackingViewModel.kt`)
1. **Mid-Session Baseline Snapshotting & Idempotent State Propagation**:
   - `WPrimeBatteryState` is an immutable value object (`data class`).
   - In `TrackingViewModel.kt`, all telemetry ingestion, sensor data merging, and field mapping execute within the ViewModel's structured coroutine scope (`viewModelScope.launch`) on `Dispatchers.Main.immediate` / default dispatcher through `combine(...)`.
   - **Configuration Snapshotting & Temporal Alignment**: At the beginning of each 1-Hz evaluation tick, the current capacity baseline is captured as an immutable local value:
     `val totalWPrimeKjSnapshot = effectiveWPrimeKjFlow.value`
     This snapshot is passed directly into `WPrimeBatteryMath.calculateBatteryState(wPrimeBalKj, totalWPrimeKjSnapshot, frameTimestamp)`. The resulting `WPrimeBatteryState` is stamped with the frame timestamp, ensuring that the evaluation is completely idempotent, referentially transparent, and temporally synchronized with `sensorData.stringValue`.
   - The UI observes `_uiState: StateFlow<TrackingScreenState>` atomically. Producing immutable `SensorFieldState` instances with `fieldToUpdate.copy(wPrimeBatteryState = ...)` guarantees atomic, non-tearing updates between high-frequency (1-Hz) telemetry emissions and Compose recomposition.

2. **Progressive Disclosure Integration (`REQ-UI-409.3`)**:
   - `WPrimeBatteryGaugeBar` is strictly bounded by the progressive disclosure architecture established in `REQ-UI-409.3`.
   - When `enableCriticalPowerModel == false`, the entire `SensorType.W_PRIME_BAL` tile—including its embedded `WPrimeBatteryGaugeBar`—is filtered out by `filterVisibleFieldsForTuning(fields, tuningConfig, screenMode)`.
   - The parent container collapses the row height to `0` (omitting that `rowNr` from `visibleFields.groupBy { it.rowNr }`), ensuring that no residual gauge bar, empty space, or orphaned UI artifacts remain on screen when the Critical Power physiology model is disabled.

3. **Stale State UI Contract & Constant Height Invariant (Zero-Jumping)**:
   - `SensorFieldView` enforces strict layout height stability to eliminate screen jitter and layout thrashing during rides:
     - When `enableCriticalPowerModel` is enabled, the tile allocates a fixed vertical layout slot constrained to `Modifier.height(14.dp)`.
     - **Stale State UI Invariant**: When sensor telemetry becomes stale (e.g. power sensor disconnects, drops out, or workout is paused before the first power sample), `WPrimeBatteryGaugeBar` MUST NOT collapse to `null` or hide. Instead, it MUST remain rendered in a stable "stale" state with dimmed track opacity (`alpha = 0.35f`) and empty fill. This guarantees a constant card height, eliminating visual jumping or jitter during rides.

4. **`resolveEffectiveCpWatts()` Hardening, Named Constant & Null-Pointer Defense**:
   - In `SettingsDataStore.kt`, declare canonical constant:
     `const val DEFAULT_CYCLING_FTP = 200`
   - In `CyclingPowerMetricsDevice.java`, `resolveEffectiveCpWatts()` is audited and hardened with comprehensive multi-tier null checks and the named constant:
     ```java
     private Integer resolveEffectiveCpWatts() {
         if (mContext == null) return SettingsDataStore.DEFAULT_CYCLING_FTP;
         Integer cp = (mCpProvider != null) ? mCpProvider.getCpWatts(mContext) : null;
         if (cp != null && cp > 0) return cp;
         Integer ftp = (mFtpProvider != null) ? mFtpProvider.getFtp(mContext) : null;
         if (ftp != null && ftp > 0) return ftp;
         return SettingsDataStore.DEFAULT_CYCLING_FTP;
     }
     ```
   - In `resetSession()`, replacing the direct `mCpProvider.getCpWatts(mContext)` call with `resolveEffectiveCpWatts()` guarantees consistent fallback to $FTP$ even when $CP$ is unconfigured or when providers are lazily wired.

---

## 5. Scope Bounding & Invariants

### In Scope
1. Creation of pure, decoupled `WPrimeBatteryState.kt` and `WPrimeBatteryMath.kt`.
2. Creation of high-visibility, True-Black AMOLED-compliant `WPrimeBatteryGaugeBar.kt` with fixed-height layout stability, boundary clipping prevention, and stale dimming.
3. Thread-safe integration into `SensorFieldState.kt`, `SensorFieldView.kt`, and `TrackingViewModel.kt` with atomic state updates.
4. Hardened `resolveEffectiveCpWatts()` with named constant `SettingsDataStore.DEFAULT_CYCLING_FTP`, multi-tier null-safety, and $FTP$ fallback in `CyclingPowerMetricsDevice.java`.
5. Comprehensive unit test suites (`WPrimeBatteryMathTest.kt`, `WPrimeBatteryGaugeBarTest.kt`, `CyclingPowerMetricsDeviceTest.kt`), including mid-session baseline mutation resilience and stale state transition verification.
6. 9-language localization parity for any new strings.

### Out of Scope
1. Database schema migrations or changes to `TrackingViews.db` (`DB_VERSION = 13` invariant).
2. Creating a separate `SensorType` enum constant (the canonical 44 constants are strictly preserved).
3. Modifying Aftermath / Post-workout summary graphs (already implemented in ATT-3862).

---

## 6. Verification Strategy

1. **Mathematical Unit Tests (`WPrimeBatteryMathTest.kt`)**:
   - Verify calculation of `percentage` and `batteryFraction` across varying $W'$ values.
   - Verify warning level transitions at exactly $30\%$ (Normal $\to$ Low) and $20\%$ (Low $\to$ Critical).
   - Verify boundary clamping ($W' > total \to 100\%$, $W' < 0 \to 0\%$).
   - Verify resilience against null, NaN, infinite, and zero-capacity inputs.
   - **Stale State Transition Test**: Verify that passing null or expired `wPrimeBalKj` yields `isStale == true`, `batteryFraction == null`, and safe zero-percentage representation without throwing exceptions.
   - **Mid-Session Baseline Mutation Test**: Verify that updating total $W'$ mid-session (e.g. from 20.0 kJ to 25.0 kJ or 15.0 kJ while current remaining is 10.0 kJ) gracefully re-scales percentage (e.g. from 50% to 40% or 67%) without negative values, divide-by-zero, jumps, or exceptions.
2. **Device State Tests (`CyclingPowerMetricsDeviceTest.kt`)**:
   - Verify `resetSession()` restores full $W'$ capacity when only $FTP$ is configured (using fallback).
   - Verify continuous 1-Hz depletion and exponential reconstitution.
   - Verify null-safe handling when providers return null or when context is detached.
3. **Cockpit UI & Contract Tests**:
   - Verify `SensorCategoryAndSelectorContractTest` continues to pass with 44 sensors.
   - Verify `SensorFieldView` layout stability: fixed 14.dp height in stale vs active telemetry, zero height when gated off.
   - Verify boundary anti-clipping padding at 0% and 100% capacity.
   - Verify warning color activation when $W' < 20\%$.
4. **Clean-Room Regression**:
   - Full suite execution via `./gradlew testDebugUnitTest` asserting 100% pass rate.
