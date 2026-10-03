# Analysis - ATT-1268: [Feature] [Tracking] AMOLED Battery Saver Mode with display dimming and event-based wakeup

**Parent Ticket**: [ATT-1268](https://rainerblind.atlassian.net/browse/ATT-1268)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1451](https://rainerblind.atlassian.net/browse/ATT-1451) (`[Analysis]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-174`  
**Test ID**: `TST-UI-126`  

---

## 1. Executive Summary & Problem Formulation

The live workout tracking cockpit of `aTrainingTracker` is designed to run on bike handlebars and in armbands during multi-hour endurance sessions. While AMOLED Pure Black (`#000000`, [REQ-UI-169](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L319), [REQ-UI-170](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L320)) significantly reduces OLED display power consumption, the backlight/OLED emission of bright metric numerals and map tiles at 100% screen brightness still constitutes the single largest consumer of battery life on Android smartphones, often depleting batteries during 4+ hour rides.

Currently, `keepScreenOn` maintains constant full display brightness indefinitely (`window.decorView.keepScreenOn = true`), regardless of whether the athlete is descending a pass at 60 km/h (where 100% focus must remain on the road) or cruising steadily on flat terrain in Zone 1.

**ATT-1268** introduces an intelligent, multi-factor **AMOLED Battery Saver Mode**:
1. **Dedicated Setting in Display Preferences**: Clearly separated under a newly established **"Einsparungen"** (Energy Savings) category in `DisplaySettingsDialog.kt`, marked by a divider positioned above the Cockpit Dark Mode selector.
2. **Multi-Factor Adaptive Brightness (Topography + Physiology)**:
   - Evaluates the existing derived slope sensor (`SensorType.SLOPE` in %) alongside athletic intensity zones (Heart Rate and Cycling Power Zones).
   - **Volle Dimmung (e.g. 15% minimum brightness)**: Steigung $< 2\,\%$ **UND** HF $\le$ Zone 2 **UND** Watt $\le$ Zone 2 (beim Radeln) $\rightarrow$ Cruising, Regeneration, Rollen lassen.
   - **Mittlere Dimmung (e.g. 50% brightness)**: Steigung $2 - 5\,\%$ **ODER** HF in Zone 3 **ODER** Watt in Zone 3 $\rightarrow$ Aktiver GA2-Arbeitsbereich mit 30–50% Energieersparnis.
   - **Keine Dimmung (100% volle Helligkeit)**: Steigung $> 5\,\%$ **ODER** HF $\ge$ Zone 4 **ODER** Watt $\ge$ Zone 4 $\rightarrow$ Schwellenbereich, Klettern, Intervalle.
3. **Event-Based Wakeup (Instant 100% illumination for 10–15s)**:
   - Any screen touch or tab swipe.
   - Contactless hand wave detected via hardware **Proximity Sensor** (`Sensor.TYPE_PROXIMITY`).
   - Live Strava Segments (*Anpirschen*, *Start*, *Ziel*).
   - Lap button interaction (`[+] Runde`).
   - Auto-pause at standstill and resumption/acceleration from stop.
   - Prepared for upcoming navigation turn cues ([ATT-1450](https://atrainingtracker.atlassian.net/browse/ATT-1450)).
4. **Decoupled Expert Tuning**:
   - The primary display dialog uses battle-tested defaults (15% full dim, 50% medium dim, <2% / 2-5% / >5% slope thresholds).
   - Deep parameter customization is cleanly delegated to the dedicated Advanced Tuning Preferences screen ([ATT-1304](https://atrainingtracker.atlassian.net/browse/ATT-1304)).

---

## 2. Requirement & Baseline Traceability

| Requirement ID | Standard / Artifact | Alignment Description |
|---|---|---|
| **`REQ-UI-174`** | `docs/requirements.md` | Net-new requirement governing AMOLED battery saver display dimming, multi-factor adaptive logic (slope + HR + power), and event wakeups. |
| **`TST-UI-126`** | `docs/tests.md` | Verification test suite for multi-factor dimming state computation, sensor fallbacks, proximity wakeup, and settings persistence. |
| **`REQ-UI-168`** | `docs/requirements.md` | Workout cockpit independent theme selector (`SYSTEM` vs `ALWAYS_DARK`). |
| **`REQ-UI-169`** | `docs/requirements.md` | AMOLED pure black (`#000000`) cockpit background. |
| **`REQ-UI-170`** | `docs/requirements.md` | Comprehensive cockpit dark theme across top bar, tab row, and system bars. |
| **`REQ-UI-172`** | `docs/requirements.md` | Heart Rate and Power training zone models and color accents. |
| **`REQ-TRK-001`** | `docs/requirements.md` | Auto-pause and motion tracking lifecycle integrity. |
| **`REQ-LIV-001`** | `docs/requirements.md` | Strava live segment proximity and approach state machine. |

---

## 3. Detailed Architectural & Technical Analysis

### 3.1. Display Settings Dialog Structure (`DisplaySettingsDialog.kt`)
The display settings bottom sheet is structured to highlight energy efficiency:
* **Top Block (Display Behavior)**:
  - `forcePortrait`
  - `keepScreenOn`
  - `noUnlocking`
* **Divider**: `HorizontalDivider()` positioned directly above the savings section.
* **Bottom Block ("Einsparungen" / Energy Savings)**:
  - Header: `Text(stringResource(R.string.settings_category_savings))` ("Einsparungen").
  - Subtitle: Explanatory summary text.
  - Setting 1: **Cockpit Dark Mode** (`SingleChoiceSegmentedButtonRow`: System vs. Always Dark).
  - Setting 2: **Akkusparer / Display-Dimming** (`DisplayOptionToggle` / Switch).

### 3.2. Multi-Factor Brightness State Machine
Let:
- $S \in \mathbb{R}$ be the current slope in percent (`SensorType.SLOPE`),
- $Z_{HR} \in \{1, 2, 3, 4, 5\}$ be the active heart rate zone (or $\emptyset$ if no HR sensor connected),
- $Z_{PWR} \in \{1, 2, 3, 4, 5, 6, 7\}$ be the active power zone (or $\emptyset$ if no power meter connected or sport is not cycling).

The target brightness state $B \in \{\text{FULL\_DIM}, \text{MEDIUM\_DIM}, \text{NO\_DIM}\}$ is resolved deterministically:

$$\begin{aligned}
\text{Condition}_{\text{NoDim}} &= (S > 5.0) \lor (Z_{HR} \ge 4) \lor (Z_{PWR} \ge 4) \\
\text{Condition}_{\text{MedDim}} &= (2.0 \le S \le 5.0) \lor (Z_{HR} = 3) \lor (Z_{PWR} = 3) \\
B &= \begin{cases} 
\text{NO\_DIM} (1.0f), & \text{if } \text{Condition}_{\text{NoDim}} \\
\text{MEDIUM\_DIM} (0.50f), & \text{else if } \text{Condition}_{\text{MedDim}} \\
\text{FULL\_DIM} (0.15f), & \text{otherwise}
\end{cases}
\end{aligned}$$

### 3.3. Sensor Fallbacks & Hysteresis Damping
1. **Graceful Sensor Degradation**:
   - If HR is absent: evaluate $S$ and $Z_{PWR}$.
   - If Power is absent (or running/hiking): evaluate $S$ and $Z_{HR}$.
   - If neither HR nor Power is available: evaluate pure slope $S$ ($<2\%$ full dim, $2-5\%$ medium dim, $>5\%$ no dim).
2. **Power Fluctuations & Smoothing**:
   - Cycling power fluctuates rapidly from second to second. The state machine evaluates 3-second moving average power (or filtered telemetry) to compute $Z_{PWR}$.
   - A temporal damping hysteresis ($3 - 5\text{ s}$) prevents rapid brightness oscillations when crossing threshold boundaries.

### 3.4. Hardware Wakeup & Proximity Sensor Integration
* **Proximity Sensor**: Using Android's `SensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)`:
  - When enabled, registering a low-frequency listener while the cockpit is active.
  - Detecting when distance drops below sensor maximum range (hand wave within ~5 cm).
  - Triggers a 15-second timer forcing $B = \text{NO\_DIM} (1.0f)$, reverting back to the adaptive state machine upon expiry.
* **Touch & Tab Events**: Compose `Modifier.pointerInput` or root touch dispatch resets the wakeup timer.
* **Workout Events**:
  - Live segment state transitions emitted by `LiveSegmentsRepository`.
  - Lap splitting emitted by `TrackingTabsViewModel.lapEvent`.
  - Auto-pause transition (`TrackingMode.PAUSED` $\leftrightarrow$ `TrackingMode.TRACKING`).

### 3.5. Window Brightness Override
In Android, per-window brightness override avoids modifying global system settings:
```kotlin
fun updateWindowBrightness(activity: Activity, brightness: Float) {
    val window = activity.window ?: return
    val lp = window.attributes
    // brightness: 0.15f for full dim, 0.50f for medium, BRIGHTNESS_OVERRIDE_NONE (-1.0f) for full/auto
    lp.screenBrightness = if (brightness >= 1.0f) {
        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    } else {
        brightness.coerceIn(0.05f, 1.0f)
    }
    window.attributes = lp
}
```

---

## 4. Call-Site & Dependency Audit

1. **`DisplaySettingsDialog.kt`**:
   - Add "Einsparungen" category header and subtitle.
   - Reposition divider cleanly above Cockpit Dark Mode.
   - Add Battery Saver toggle.
2. **`TrainingApplication.java`**:
   - Add `BATTERY_SAVER` display option key.
   - Provide getter/setter `isBatterySaverEnabled()` / `setBatterySaverEnabled(boolean)`.
3. **`TrackingTabsScreen.kt` & `MainActivityWithNavigation.kt`**:
   - Host `BatterySaverController` during active tracking.
   - Bind lifecycle (register proximity sensor in `ON_RESUME`, unregister in `ON_PAUSE`).
4. **`ElevationProfile.kt`**:
   - Verify alignment with slope thresholds ($<2\%$, $2-5\%$, $>5\%$).

---

## 5. Security, Invariant & Regression Analysis

* **No System Bar Leaks**: Window brightness overrides apply strictly to the active Activity window and automatically release when the app is backgrounded or closed.
* **Safety Invariant**: Under no circumstances will the screen turn completely off (`screenBrightness = 0.0f` is avoided, using minimum `0.10f - 0.15f`). The athlete can always see that the device is running.
* **Localization Parity**: All new strings (Category name, toggle title, summary) will be localized across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
