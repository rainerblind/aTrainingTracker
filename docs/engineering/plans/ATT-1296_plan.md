# Stage 3: Implementation Plan - ATT-1296: Real-time W' Balance (W-Prime Battery) Expenditure and Recovery Tracking

**Ticket**: [ATT-1296](https://rainerblind.atlassian.net/browse/ATT-1296)  
**Sub-task**: [ATT-4268](https://rainerblind.atlassian.net/browse/ATT-4268) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-160](https://rainerblind.atlassian.net/browse/ATT-160) (*Cockpit & Live Tracking Engine*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-UI-416` (*Real-time W' Balance (W-Prime Battery) Expenditure, Reconstitution, Battery Gauge Bar, and Warning Alert*)  
**Test Mapping**: `TST-UI-379` (*W' Balance Battery Gauge, Mathematical Normalization, Layout Invariants, and 9-Language Parity Verification Suite*)  
**Branch**: `feature/ATT-1296`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In cycling and athletic performance physiology, Dr. Phil Skiba's W' Balance model tracks the dynamic depletion and reconstitution of anaerobic work capacity (energy available above Critical Power / FTP, measured in kilojoules).
While sprint ticket ATT-3862 implemented Dr. Skiba's differential expenditure and exponential reconstitution formulas in `PowerPhysiologyCalculator.kt` and ATT-3921 exposed post-workout W' metrics in Aftermath summaries, live Cockpit tracking displays remaining W' balance only as a raw scalar number in kJ for `SensorType.W_PRIME_BAL`.

Athletes during high-intensity intervals or climbs cannot easily gauge their remaining capacity at a glance without mental arithmetic against their baseline capacity. Crucially:
1. There is no visual battery gauge bar representing remaining capacity ($0\text{–}100\%$).
2. There is no automated color-coded alert when the anaerobic battery drops below the critical 20% fatigue threshold.
3. In `CyclingPowerMetricsDevice.java`, `resetSession()` directly queried `mCpProvider.getCpWatts(mContext)` rather than `resolveEffectiveCpWatts()`, causing W' balance to remain uninitialized upon session reset when CP was unconfigured even though FTP was present.

This implementation plan defines the complete engineering architecture to introduce a True-Black AMOLED battery gauge bar with smooth animation, percentage display, low-battery warning alert, layout stability invariants, device hardening, and 9-language localization parity.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-416` (*Real-time W' Balance Expenditure, Reconstitution, Battery Gauge Bar, and Warning Alert*)
  * `REQ-UI-416.1`: Skiba W' Balance Differential Algorithm, Device Hardening (`resolveEffectiveCpWatts()`, `resetSession()` FTP fallback, pause reconstitution), and mid-session parameter mutation clamping to $[0.0, W'_{\text{new}}]$.
  * `REQ-UI-416.2`: W' Battery State Model (`WPrimeBatteryState`), mathematical normalization ($[0..100]$, $[0.0f..1.0f]$), warning thresholds (`NORMAL`, `LOW`, `CRITICAL`, `DEPLETED`), and periodic staleness evaluation ($\le 3000\text{ ms}$).
  * `REQ-UI-416.3`: Cockpit Battery Gauge Bar composable (`WPrimeBatteryGaugeBar.kt`), outline capsule track, terminal nipple, continuous animated fill interpolation (`tween(300, easing = FastOutSlowInEasing)`), 3.dp end-cap boundary anti-clipping padding, fixed 14.dp vertical slot height invariant with font scaling text clipping (`TextOverflow.Clip`), and dimmed stale state track opacity (`0.35f`).
  * `REQ-UI-416.4`: Live Cockpit Integration in `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`, tile Warning Red border activation when $W' < 20\%$, and progressive disclosure gating (`REQ-UI-409.3`) row height collapse.
  * `REQ-UI-416.5`: 9-Language Localization Parity for `w_prime_battery_pct` (`%1$d%%`) and `w_prime_warning_low_battery`.
* **Test Mapping**: `TST-UI-379` (`TST-UI-379.1` to `TST-UI-379.5`)
  * `TST-UI-379.1`: Mathematical Normalization, Clamping & Thresholds Unit Tests (`WPrimeBatteryMathTest.kt`).
  * `TST-UI-379.2`: Device Hardening, Fallback & Reset Unit Tests (`CyclingPowerMetricsDeviceTest.kt`).
  * `TST-UI-379.3`: Battery Gauge Layout, Fixed Height, Easing Animation, Font Scaling & Stability Contract Tests (`WPrimeBatteryGaugeBarLayoutTest.kt`).
  * `TST-UI-379.4`: 9-Language Localization & Format Audit (`WPrimeBatteryLocalizationTest.kt`).
  * `TST-UI-379.5`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Chesterton's Fence - Math Preservation**: The differential math formulas and exponential reconstitution calculations in `PowerPhysiologyCalculator.stepWPrimeBalanceJoules` and `calculateSkibaTauSeconds` MUST NOT be modified.
2. **Fixed Height Slot Invariant**: The composable container for `WPrimeBatteryGaugeBar` MUST occupy a strictly fixed 14.dp height slot (`Modifier.height(14.dp)`). Font scaling (1.0x, 1.5x, 2.0x) must not cause layout expansion (`maxLines = 1`, `overflow = TextOverflow.Clip`).
3. **Boundary Anti-Clipping Invariant**: The fill bar must have `endPadding = 3.dp` from the terminal nipple so at 100% capacity the fill never overlaps the cap, and at 0% renders cleanly as an empty outlined capsule track.
4. **Stale State UI Contract**: When telemetry data ceases or exceeds 3000 ms, the gauge remains rendered with dimmed track opacity (`alpha = 0.35f`) and empty fill rather than collapsing or hiding, preserving zero layout jumping.
5. **Continuous Animation Updates**: If a new fraction arrives mid-animation, `animateFloatAsState` interpolates smoothly to the new target without visual snapping or duration resets.
6. **Sensor Catalog & Transient State Invariant**: Zero changes to the 44-sensor catalog in `SensorType.java` and zero changes to `TrackingViews.db` (`DB_VERSION = 13`). `WPrimeBatteryState` is strictly transient in-memory UI state attached to `SensorFieldState`, and is NEVER persisted into SQLite or DataStore.
7. **Denominator Mutation Stability**: When `totalWPrimeKj` (the denominator) mutates mid-session due to user settings adjustment, remaining balance is clamped to $[0.0, W'_{\text{new}}]$ and `batteryFraction` updates proportionally ($W'_{\text{bal}} / W'_{\text{new}}$) rather than snapping or resetting to 0 or 1.
8. **Progressive Disclosure Invariant (`REQ-UI-409.3`)**: When `enableCriticalPowerModel == false`, `SensorType.W_PRIME_BAL` is filtered out of `visibleFields` by `filterVisibleFieldsForTuning`, collapsing row height to 0.
9. **True-Black AMOLED Compliance (Rule 23)**: Backgrounds remain `#000000` pure black; gauge elements use high-contrast outline (`outlineVariant.copy(alpha = 0.6f)`) and thematic colors (`TTColor.WPrimeBalGreen`, `TTColor.Zone3`, `TTColor.Zone5`).
10. **Horizontal Layout Stability & Text Badge Sizing**: The percentage text badge container must enforce a fixed minimum width (`Modifier.widthIn(min = 28.dp)`) and maintain consistent layout presence across active and stale states (rendering `--%` or preserving footprint when stale) so that digit count changes ('9%' to '10%') or stale state transitions never cause horizontal jitter or resizing of the gauge bar.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Pure Mathematical & Presentation Domain Model (`WPrimeBatteryMath.kt`, `WPrimeBatteryState.kt`)
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/models/WPrimeBatteryState.kt`:
* **Models**:
  ```kotlin
  enum class WPrimeWarningLevel {
      NORMAL,    // > 30%
      LOW,       // 20% .. 30%
      CRITICAL,  // > 0% .. < 20%
      DEPLETED   // <= 0%
  }

  data class WPrimeBatteryState(
      val remainingKj: Double?,
      val totalKj: Double,
      val percentage: Int,
      val batteryFraction: Float?,
      val warningLevel: WPrimeWarningLevel,
      val batteryColor: Color,
      val isWarning: Boolean,
      val isStale: Boolean,
      val timestampMs: Long
  )
  ```
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/models/WPrimeBatteryMath.kt`:
* **Pure Math Helper**:
  ```kotlin
  object WPrimeBatteryMath {
      const val STALE_TIMEOUT_MS = 3000L

      fun calculateBatteryState(
          remainingKj: Double?,
          totalKj: Double,
          timestampMs: Long,
          nowMs: Long = System.currentTimeMillis()
      ): WPrimeBatteryState {
          val isStale = remainingKj == null || !remainingKj.isFinite() || (nowMs - timestampMs > STALE_TIMEOUT_MS)
          if (isStale || remainingKj == null || totalKj <= 0.0) {
              return WPrimeBatteryState(
                  remainingKj = remainingKj,
                  totalKj = totalKj.coerceAtLeast(1.0),
                  percentage = 0,
                  batteryFraction = null,
                  warningLevel = WPrimeWarningLevel.DEPLETED,
                  batteryColor = TTColor.Zone5,
                  isWarning = false,
                  isStale = true,
                  timestampMs = timestampMs
              )
          }

          val clampedRemaining = remainingKj.coerceIn(0.0, totalKj)
          val fraction = (clampedRemaining / totalKj).toFloat().coerceIn(0.0f, 1.0f)
          val percentage = (fraction * 100f).roundToInt().coerceIn(0, 100)

          val warningLevel = when {
              percentage > 30 -> WPrimeWarningLevel.NORMAL
              percentage >= 20 -> WPrimeWarningLevel.LOW
              percentage > 0 -> WPrimeWarningLevel.CRITICAL
              else -> WPrimeWarningLevel.DEPLETED
          }

          val color = when (warningLevel) {
              WPrimeWarningLevel.NORMAL -> TTColor.WPrimeBalGreen
              WPrimeWarningLevel.LOW -> TTColor.Zone3
              WPrimeWarningLevel.CRITICAL, WPrimeWarningLevel.DEPLETED -> TTColor.Zone5
          }

          val isWarning = percentage < 20

          return WPrimeBatteryState(
              remainingKj = clampedRemaining,
              totalKj = totalKj,
              percentage = percentage,
              batteryFraction = fraction,
              warningLevel = warningLevel,
              batteryColor = color,
              isWarning = isWarning,
              isStale = false,
              timestampMs = timestampMs
          )
      }
  }
  ```

### Component 2: Device Hardening & Fallback (`CyclingPowerMetricsDevice.java`, `SettingsDataStore.kt`)
* In `SettingsDataStore.kt`:
  - Verify `DEFAULT_CYCLING_FTP = 200` constant is accessible or expose helper.
* In `CyclingPowerMetricsDevice.java`:
  - Update `resetSession()`:
    Replace:
    ```java
    if (mCpProvider.getCpWatts(mContext) > 0) {
        mCurrentWPrimeBalJoules = mTotalWPrimeJoules;
    }
    ```
    With:
    ```java
    if (resolveEffectiveCpWatts() > 0) {
        mCurrentWPrimeBalJoules = mTotalWPrimeJoules;
    }
    ```
  - In `resolveEffectiveCpWatts()`:
    Ensure robust multi-tier fallback: `mCpProvider.getCpWatts(mContext)` -> `mFtpProvider.getFtp(mContext)` -> `SettingsDataStore.DEFAULT_CYCLING_FTP` (`200` W).
    Annotate with `@VisibleForTesting` and package-private visibility to allow direct unit testing of multi-tier fallback without needing to mock private providers or service contexts.

### Component 3: Battery Gauge Bar Composable (`WPrimeBatteryGaugeBar.kt`)
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/WPrimeBatteryGaugeBar.kt`:
* Renders the capsule battery body with terminal nipple.
* Animated fill bar:
  ```kotlin
  val animatedFraction by animateFloatAsState(
      targetValue = if (state.isStale) 0f else (state.batteryFraction ?: 0f),
      animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
      label = "wPrimeBatteryFill"
  )
  ```
* Outer fixed height: `Modifier.height(14.dp).fillMaxWidth()`.
* End padding: `endPadding = 3.dp` inside the capsule to prevent clipping the terminal nipple.
* Stale styling: track outline dims to `alpha = 0.35f` with 0 fill.
* Percentage text badge: when `viewSize >= ViewSize.NORMAL`, rendered with fixed minimum width `Modifier.widthIn(min = 28.dp)`, `maxLines = 1`, `overflow = TextOverflow.Clip`, and `style = MaterialTheme.typography.labelSmall`.
  - When `state.isStale`, the text badge maintains its identical `widthIn(min = 28.dp)` footprint (rendering `--%` or preserving empty footprint), ensuring the gauge bar never jitters or changes width when transitioning into or out of stale state.

### Component 4: Cockpit Integration (`SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`)
* In `SensorFieldState.kt`:
  - Add `val wPrimeBatteryState: WPrimeBatteryState? = null` with default parameter.
* In `SensorFieldView.kt`:
  - When `sensorType == SensorType.W_PRIME_BAL && state.wPrimeBatteryState != null`:
    Render `WPrimeBatteryGaugeBar(state = state.wPrimeBatteryState, viewSize = viewSize)`.
  - When `state.wPrimeBatteryState?.isWarning == true`:
    Elevate card border stroke to `1.5.dp` Warning Red (`MaterialTheme.colorScheme.error` / `TTColor.Zone5`).
* In `TrackingViewModel.kt`:
  - **Concurrency & Atomic Derivation Strategy**: The ViewModel combines incoming 1-Hz `FilteredSensorData` with the latest `SettingsDataStore.criticalPowerModel` (or physiological settings) StateFlow. Derivation of `wPrimeBatteryState` is executed atomically per frame on the snapshot pair of `(remainingKj, totalWPrimeKj)`. This ensures that denominator updates and numerator updates never race or create torn, invalid states.
  - **Disconnect & Profile Teardown**: If the power meter disconnects or the user switches to a profile without power metrics, `wPrimeBatteryState` is cleanly nullified/reset, preventing stale gauge artifacts.
  - **Staleness Synchronization via Reactive Delay Debounce**: Rather than running an uncoordinated separate 1-second ticker that causes race conditions and duplicate emissions, the telemetry stream uses a reactive debounce (`transformLatest` or combined liveness): each incoming 1-Hz sample emits fresh state with `isStale = false`. If no new sample is received for 3000 ms, the flow cleanly emits `isStale = true` without polling or racing with incoming packets.

### Component 5: 9-Language Localization Parity
Add to all 9 `strings.xml` files:
* `w_prime_battery_pct`: `%1$d%%`
* `w_prime_warning_low_battery`: Localized alert string (e.g. EN: `W' Battery Low (<20%)`, DE: `W'-Akku schwach (<20%)`, etc.).

---

## 5. UI Consistency (Rule 23 — mandatory for UI changes)

* **Reference screen / component**: Cockpit sensor tiles in `SensorFieldView.kt`, battery indicator icons in `DeviceItem.kt`, and `TTColor` physiological zone color palette.
* **Reused components**: `MaterialTheme.colorScheme.outlineVariant`, `MaterialTheme.colorScheme.error`, `TTColor.WPrimeBalGreen`, `TTColor.Zone3`, `TTColor.Zone5`, `MaterialTheme.typography.labelSmall`.
* **Theme tokens**: Strict True-Black AMOLED compliance (`#000000`), fixed container height `14.dp`, capsule border radius `3.dp`, terminal nipple width `2.dp` / height `6.dp`.
* **Justification for new elements**: The W' anaerobic battery gauge is a specialized physiological visualization that requires an exact capsule track representation; it is encapsulated cleanly in `WPrimeBatteryGaugeBar.kt`.

---

## 6. Verification & Test Plan

1. **`WPrimeBatteryMathTest.kt`**:
   - `testBatteryNormalizationAcrossLevels`: Verify Normal ($>30\%$), Low ($20\text{–}30\%$), Critical ($<20\%$), and Depleted ($\le 0\%$).
   - `testClampingBounds`: Verify $>100\%$ clamps to 1.0f / 100%, and $<0\%$ clamps to 0.0f / 0%.
   - `testStaleTransition`: Verify delta $>3000\text{ ms}$ emits `isStale = true` and `batteryFraction = null`.
   - `testMidSessionParameterChangeClamping`: Verify remaining balance is safely clamped to $[0.0, W'_{\text{new}}]$ without exceptions.
   - `testDenominatorChangeStability`: Verify that when `totalKj` mutates mid-session ($20 \to 25 \to 15$ kJ with 10 kJ remaining), `batteryFraction` updates proportionally ($0.50 \to 0.40 \to 0.67$) rather than resetting to 0 or 1.
2. **`CyclingPowerMetricsDeviceTest.kt`**:
   - `testResetSessionRestoresCapacityWithFtpFallback`: Verify `resetSession()` restores $W'$ to full capacity when CP is unconfigured but FTP is set.
   - `testResolveEffectiveCpWattsMultiTierFallback`: Verify fallback order CP -> FTP -> `DEFAULT_CYCLING_FTP` (200 W) via `@VisibleForTesting` method.
   - `testPauseExponentialReconstitution`: Verify $W'$ reconstitutes during paused states at $P = 0\text{ W}$.
3. **`WPrimeBatteryGaugeBarLayoutTest.kt`**:
   - `testFixedHeightSlotInvariant`: Verify 14.dp height across active, stale, and depleted states.
   - `testBoundaryAntiClippingPadding`: Verify 3.dp end-cap boundary padding.
   - `testLayoutStabilityUnderFontScaling`: Verify container height remains strictly 14.dp at 1.0x, 1.5x, and 2.0x font scaling with `TextOverflow.Clip`.
   - `testStaleTransitionLayoutStability`: Verify that transitioning from `isStale = false` to `isStale = true` maintains identical gauge track width and text badge footprint without horizontal layout shifts.
   - `testProgressiveDisclosureCollapse`: Verify row height collapses to 0 when `enableCriticalPowerModel == false`.
4. **`WPrimeBatteryLocalizationTest.kt`**:
   - Verify `w_prime_battery_pct` and `w_prime_warning_low_battery` exist across all 9 application locales with format specifier parity.
5. **Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 7. Step-by-Step Implementation Sequence

1. **Step 1**: Create `WPrimeBatteryState.kt` and `WPrimeBatteryMath.kt` with pure mathematical normalization and clamping.
2. **Step 2**: Author `WPrimeBatteryMathTest.kt` (including `testDenominatorChangeStability`) and verify pure unit tests pass.
3. **Step 3**: Harden `CyclingPowerMetricsDevice.java` (`resolveEffectiveCpWatts()`, `resetSession()`) and update `CyclingPowerMetricsDeviceTest.kt`.
4. **Step 4**: Add `w_prime_battery_pct` and `w_prime_warning_low_battery` across all 9 `strings.xml` and author `WPrimeBatteryLocalizationTest.kt`.
5. **Step 5**: Create `WPrimeBatteryGaugeBar.kt` with fixed 14.dp slot, 3.dp end padding, easing animation, font-scaled text clipping, and stale state contract.
6. **Step 6**: Author `WPrimeBatteryGaugeBarLayoutTest.kt` verifying layout stability and progressive disclosure gating.
7. **Step 7**: Integrate `WPrimeBatteryState` into `SensorFieldState.kt`, `SensorFieldView.kt`, and `TrackingViewModel.kt` (including atomic snapshot derivation, disconnection cleanup, periodic staleness monitoring, and Warning Red tile border).
8. **Step 8**: Run full clean-room unit test suite (`./gradlew testDebugUnitTest`).
