# Stage 2: Requirement & Test Specification - ATT-1296: Real-time W' Balance (W-Prime Battery) Expenditure and Recovery Tracking

**Ticket**: [ATT-1296](https://rainerblind.atlassian.net/browse/ATT-1296)  
**Sub-task**: [ATT-4267](https://rainerblind.atlassian.net/browse/ATT-4267) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-160](https://rainerblind.atlassian.net/browse/ATT-160) (*Cockpit & Live Tracking Engine*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-416` (*Real-time W' Balance (W-Prime Battery) Expenditure, Reconstitution, Battery Gauge Bar, and Warning Alert*)  
**Test Mapping**: `TST-UI-379` (*W' Balance Battery Gauge, Mathematical Normalization, Layout Invariants, and 9-Language Parity Verification Suite*)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (`REQ-UI-416`)

| Requirement ID | Title | Scope & Architectural Description |
| :--- | :--- | :--- |
| **REQ-UI-416** | **Real-time W' Balance (W-Prime Battery) Expenditure, Reconstitution, Battery Gauge Bar, and Warning Alert.** | The system SHALL maintain continuous 1-Hz tracking of remaining $W'$ ($W'_{\text{bal}}$ in kJ) in `CyclingPowerMetricsDevice.java` using Dr. Phil Skiba's differential expenditure and exponential reconstitution model, render a high-visibility, True-Black AMOLED-compliant battery gauge bar with smooth animated fill interpolation inside `SensorFieldView.kt` for `SensorType.W_PRIME_BAL`, display percentage remaining ($0\text{–}100\%$), trigger an immediate color-coded warning alert when $W' < 20\%$, enforce fixed-height layout stability across active and stale states, and preserve progressive disclosure gating (`REQ-UI-409.3`) and 9-language localization parity (ATT-1296, extending `REQ-UI-399` and `REQ-UI-409`):<br><br>1. *Skiba W' Balance Differential Algorithm & Device Hardening (`REQ-UI-416.1`)*:<br>• In `CyclingPowerMetricsDevice.java`, `calculateMetrics()` SHALL compute $W'_{\text{bal}}$ at 1 Hz based on instantaneous power vs Critical Power ($CP$ in W) and user anaerobic work capacity ($W'$ in kJ), delegating to `PowerPhysiologyCalculator.stepWPrimeBalanceJoules`.<br>• `resolveEffectiveCpWatts()` SHALL resolve Critical Power via `mCpProvider.getCpWatts(mContext)`, falling back to `mFtpProvider.getFtp(mContext)`, and falling back to `SettingsDataStore.DEFAULT_CYCLING_FTP` (`200` W) if neither is positive, with multi-tier null-safety.<br>• `resetSession()` SHALL invoke `resolveEffectiveCpWatts()` to restore full $W'$ capacity on session reset even when $CP$ is unconfigured and only $FTP$ is present.<br>• During recording suspension (paused / auto-paused), $W'_{\text{bal}}$ SHALL continue exponential reconstitution at $P = 0\text{ W}$.<br>• *Mid-Session Parameter Change Integrity*: If $CP$ or $W'$ capacity is modified mid-session, the tracking engine SHALL re-evaluate the normalized remaining fraction against the new capacity $W'_{\text{new}}$, clamping remaining balance to $[0.0, W'_{\text{new}}]$ without causing negative values, mathematical underflows, or discontinuous state corruption.<br><br>2. *W' Battery State Model & Mathematical Normalization (`REQ-UI-416.2`)*:<br>• `WPrimeBatteryState` SHALL be an immutable data class capturing `val remainingKj: Double?`, `val totalKj: Double`, `val percentage: Int`, `val batteryFraction: Float?`, `val warningLevel: WPrimeWarningLevel`, `val batteryColor: Color`, `val isWarning: Boolean`, `val isStale: Boolean`, and `val timestampMs: Long`.<br>• `WPrimeBatteryMath.calculateBatteryState(...)` SHALL normalize remaining energy against an immutable snapshot of total $W'$ capacity, clamping `percentage` to $[0..100]$ and `batteryFraction` to $[0.0f..1.0f]$.<br>• `warningLevel` SHALL evaluate to `NORMAL` ($> 30\%$, `TTColor.WPrimeBalGreen`), `LOW` ($20\% \le \text{pct} \le 30\%$, `TTColor.Zone3`), `CRITICAL` ($0\% < \text{pct} < 20\%$, `TTColor.Zone5`), and `DEPLETED` ($\le 0\%$, `TTColor.Zone5`).<br>• `isWarning` SHALL evaluate to `true` when `percentage < 20 && !isStale`.<br>• `isStale` SHALL evaluate to `true` when telemetry data is absent, null, non-finite, or older than `STALE_TIMEOUT_MS = 3000L`. In `TrackingViewModel`, staleness evaluation SHALL be executed via periodic flow/ticker monitoring comparing elapsed time against `timestampMs`, immediately emitting an updated state with `isStale = true` if the delta exceeds $3000\text{ ms}$ without waiting for subsequent telemetry packets.<br><br>3. *Cockpit Battery Gauge Composable & Layout Invariants (`REQ-UI-416.3`)*:<br>• `WPrimeBatteryGaugeBar.kt` SHALL render a horizontal battery gauge track with high-contrast outline (`outlineVariant.copy(alpha = 0.6f)`), rounded capsule corners, terminal end-cap nipple, proportional fill animated via `animateFloatAsState(targetValue = fraction, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))`, and compact percentage badge when `viewSize >= ViewSize.NORMAL`.<br>• **Animation Continuity**: `WPrimeBatteryGaugeBar` SHALL support continuous animation updates: if a new `fraction` target is received while an animation is in progress, `animateFloatAsState` SHALL smoothly interpolate from the current animated value to the new target without visual snapping or animation reset.<br>• **Boundary Anti-Clipping Padding**: The fill bar SHALL be constrained with `endPadding = 3.dp` from the terminal nipple so at 100% capacity the fill never overlaps or clips the terminal cap, and at 0% renders cleanly as an empty outlined capsule track.<br>• **Fixed-Height Container Invariant & Font Scaling Robustness**: The composable container SHALL be constrained to a fixed vertical slot (`Modifier.height(14.dp)`). The percentage badge text SHALL be constrained with `maxLines = 1`, `overflow = TextOverflow.Clip`, and compact typography (`style = MaterialTheme.typography.labelSmall`), ensuring the 14dp height slot is strictly enforced without layout expansion regardless of system accessibility font scaling or active/stale state.<br>• **Stale State UI Contract**: When telemetry is stale, the gauge SHALL remain rendered with dimmed track opacity (`alpha = 0.35f`) and empty fill, rather than collapsing or hiding.<br><br>4. *Live Cockpit Integration & Color-Coded Warning Alert (`REQ-UI-416.4`)*:<br>• In `SensorFieldState.kt`, `val wPrimeBatteryState: WPrimeBatteryState? = null` SHALL be exposed with a default parameter, preserving in-memory backward compatibility.<br>• In `SensorFieldView.kt`, `WPrimeBatteryGaugeBar` SHALL be rendered directly beneath the primary value and unit for `SensorType.W_PRIME_BAL` fields when `wPrimeBatteryState != null`.<br>• When `isWarning == true` ($W' < 20\%$), the tile's outline border SHALL switch to high-visibility Warning Red (`MaterialTheme.colorScheme.error` / `TTColor.Zone5`), providing immediate biofeedback without touch interaction.<br>• In `TrackingViewModel.kt`, `wPrimeBatteryState` SHALL be computed in `applySensorData` using an immutable snapshot of `totalWPrimeKj` per 1-Hz frame, stamped with the frame timestamp.<br>• Progressive disclosure gating (`REQ-UI-409.3`) SHALL be strictly preserved: when `enableCriticalPowerModel == false`, the entire `W_PRIME_BAL` tile is excluded from `visibleFields` and row height collapses to 0.<br><br>5. *9-Language Localization Parity (`REQ-UI-416.5`)*:<br>• All new UI strings (`w_prime_battery_pct` with format `%1$d%%`, `w_prime_warning_low_battery` e.g. `W\' Battery Low (<20%)`) SHALL exist across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`). |

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-416`, extending `REQ-UI-399` (ATT-3862) and `REQ-UI-409` (ATT-3921). Targets `WPrimeBatteryState.kt`, `WPrimeBatteryMath.kt`, `WPrimeBatteryGaugeBar.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`, `CyclingPowerMetricsDevice.java`, `SettingsDataStore.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: `ATT-3862` (`61ccb801dc18`), `ATT-3921` (`a6289096638b`), `ATT-3106` (`REQ-UI-376`), and `ATT-3107` (`REQ-UI-377`).
3. *Root Reason for Existing Formulation*: While ATT-3862 introduced Skiba mathematical modeling and scalar Aftermath telemetry, the live cockpit tile only rendered scalar kJ numbers without an intuitive visual battery gauge, without percentage capacity, and without a low-battery color-coded warning alert.
4. *Preservation of Core Invariants*:
   - `PowerPhysiologyCalculator.stepWPrimeBalanceJoules` mathematical implementation is 100% preserved.
   - `SensorType` 44-constant catalog and `TrackingViews.db` (`DB_VERSION = 13`) remain untouched.
   - True-Black AMOLED compliance (Rule 23) is preserved.
   - Progressive disclosure gating and empty-row collapse (`REQ-UI-409.3`) remain strictly preserved.

### Acceptance Criteria (Given-When-Then)
- *Given* an active ride with power telemetry above Critical Power ($P > CP$),
  *When* 1-Hz metrics are computed in `CyclingPowerMetricsDevice`,
  *Then* $W'_{\text{bal}}$ depletes according to $(P - CP) \cdot 1\text{s}$, and the battery gauge fill fraction decreases accordingly.
- *Given* an active ride where power drops below Critical Power or during a pause,
  *When* 1-Hz metrics are computed,
  *Then* $W'_{\text{bal}}$ reconstitutes exponentially toward full capacity $W'$.
- *Given* remaining $W'$ dropping below $20\%$,
  *When* rendered in `SensorFieldView`,
  *Then* the battery gauge bar turns Red (`TTColor.Zone5`), and the card's border switches to Warning Red.
- *Given* telemetry sensor disconnection or missing power samples,
  *When* `WPrimeBatteryGaugeBar` is rendered,
  *Then* it maintains its fixed 14.dp height slot with dimmed track opacity (0.35 alpha) and empty fill, with zero layout jumping, transitioning to stale within $\le 3000\text{ ms}$.
- *Given* mid-session baseline parameter change of $CP$ or $W'$,
  *When* recalculating W' battery state,
  *Then* remaining balance is safely clamped to $[0.0, W'_{\text{new}}]$ and normalized without discontinuous exceptions.
- *Given* `enableCriticalPowerModel == false`,
  *When* `SensorGridScreen` evaluates visible fields,
  *Then* the `W_PRIME_BAL` tile is completely hidden and row height collapses to 0.

---

## 2. Test Specification (`TST-UI-379`)

| Test Spec ID | Target Class / File | Verification Method | Pass Criteria |
| :--- | :--- | :--- | :--- |
| **TST-UI-379.1** | `WPrimeBatteryMathTest.kt` | Unit tests for mathematical normalization, warning thresholds, bounds clamping, stale transitions, and mid-session baseline mutation. | • $18.0\text{ kJ} / 20.0\text{ kJ} \to 90\%$, fraction $0.90$, `NORMAL`, `TTColor.WPrimeBalGreen`.<br>• $5.0\text{ kJ} / 20.0\text{ kJ} \to 25\%$, fraction $0.25$, `LOW`, `TTColor.Zone3`.<br>• $3.0\text{ kJ} / 20.0\text{ kJ} \to 15\%$, fraction $0.15$, `CRITICAL`, `TTColor.Zone5`, `isWarning = true`.<br>• $\le 0\text{ kJ} \to 0\%$, fraction $0.0$, `DEPLETED`, `isWarning = true`.<br>• Clamping: $> total \to 1.0f$, $< 0 \to 0.0f$.<br>• Stale transition: null/expired timestamp (>3000ms) $\to$ `isStale = true`, `batteryFraction = null`.<br>• Mid-session mutation: $10\text{ kJ}$ with capacity mutating $20 \to 25 \to 15\text{ kJ}$ safely scales fraction $0.50 \to 0.40 \to 0.67$ without exceptions, clamping remaining to $[0.0, W'_{\text{new}}]$. |
| **TST-UI-379.2** | `CyclingPowerMetricsDeviceTest.kt` | Unit tests for FTP fallback on `resetSession()`, pause exponential reconstitution, and null-safe `resolveEffectiveCpWatts()`. | • `resetSession()` with unconfigured $CP$ and configured $FTP$ restores $W'$ to full capacity.<br>• 1-Hz depletion at $P > CP$ and exponential reconstitution at $P = 0$ during pauses.<br>• Null context and null providers fall back safely to `SettingsDataStore.DEFAULT_CYCLING_FTP` (`200` W). |
| **TST-UI-379.3** | `WPrimeBatteryGaugeBarLayoutTest.kt` | Robolectric / Compose contract tests for layout stability, fixed height, boundary anti-clipping padding, easing animation, font scaling robustness, and progressive disclosure gating. | • Fixed 14.dp height slot across active, stale, and empty states.<br>• 3.dp end-cap padding prevents clipping at 100% and 0% capacity.<br>• Layout stability under system font scaling (1.0x, 1.5x, 2.0x): 14.dp container height strictly preserved with `TextOverflow.Clip`.<br>• Continuous animation updates with `tween(durationMillis = 300, easing = FastOutSlowInEasing)`.<br>• Warning Red border activated when `isWarning == true`.<br>• Completely hidden when `enableCriticalPowerModel == false`. |
| **TST-UI-379.4** | `WPrimeBatteryLocalizationTest.kt` | Architectural audit for 9-language localization parity and XML formatting across all application locales. | • 100% parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt` for `w_prime_battery_pct` (`%1$d%%`) and `w_prime_warning_low_battery` (`W\' Battery Low (<20%)`). |
| **TST-UI-379.5** | Clean-Room Full Suite | `./gradlew testDebugUnitTest` execution across entire codebase. | • 100% pass rate with zero regressions. |

---

## 3. Traceability Matrix

| Requirement Clause | Specification Component | Test Spec Verification |
| :--- | :--- | :--- |
| `REQ-UI-416.1` (Skiba Math, Device Hardening & Mid-Session Mutation) | `CyclingPowerMetricsDevice.java`, `SettingsDataStore.kt` | `TST-UI-379.2`, `TST-UI-379.1` |
| `REQ-UI-416.2` (State Model, Normalization & Staleness Check) | `WPrimeBatteryState.kt`, `WPrimeBatteryMath.kt` | `TST-UI-379.1` |
| `REQ-UI-416.3` (Battery Gauge Composable & Layout Invariants) | `WPrimeBatteryGaugeBar.kt` | `TST-UI-379.3` |
| `REQ-UI-416.4` (Cockpit Integration & Warning Alert) | `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt` | `TST-UI-379.3` |
| `REQ-UI-416.5` (9-Language Parity) | `values*/strings.xml` | `TST-UI-379.4` |
| System Health | Entire Application | `TST-UI-379.5` |
