# Test Specification - ATT-1268: [Tracking] AMOLED Battery Saver Mode with display dimming and event-based wakeup

**Parent Ticket**: [ATT-1268](https://rainerblind.atlassian.net/browse/ATT-1268)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1452](https://rainerblind.atlassian.net/browse/ATT-1452) (`[Test-Spec]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-174`  
**Test ID**: `TST-UI-126`  

---

## 1. Traceability & Scope Alignment

| Item | Reference |
|---|---|
| **Parent Feature Ticket** | [ATT-1268](https://rainerblind.atlassian.net/browse/ATT-1268) |
| **Parent Epic** | [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*) |
| **Requirement Specification** | `REQ-UI-174` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) |
| **Test Catalog** | `TST-UI-126` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) |
| **Analysis Deliverable** | [docs/engineering/analysis/ATT-1268_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-1268_analysis.md) |
| **Target Components** | `DisplaySettingsDialog.kt`, `BatterySaverController.kt`, `BatterySaverStateMachine.kt`, `TrackingTabsScreen.kt`, `TrainingApplication.java`, `strings_display.xml` |

---

## 2. Harmonized Requirement Specification (`REQ-UI-174`)

### REQ-UI-174: AMOLED Battery Saver Mode with Multi-Factor Display Dimming and Event-Based Wakeup
The system SHALL provide an intelligent AMOLED Battery Saver Mode that dynamically dims display brightness during endurance tracking based on topography and athletic intensity zones, while waking the display to full illumination on critical events (ATT-1268):

1. **Dedicated "Einsparungen" Display Settings Category**:
   - In `DisplaySettingsDialog.kt`, the system SHALL group energy-saving options into a distinct section titled "Einsparungen" (`@string/settings_category_savings`), demarcated by a horizontal divider positioned above the Cockpit Dark Mode selector.
   - The section SHALL provide a dedicated toggle: *Akkusparer / Display-Dimming* (`@string/prefs_battery_saver_title`, `@string/prefs_battery_saver_summary`), operating independently of the cockpit theme.
   - Tapping "Speichern" SHALL persist the setting in `TrainingApplication` / SharedPreferences (`KEY_BATTERY_SAVER = "battery_saver"`, default `false`).
2. **Multi-Factor Adaptive Brightness State Machine**:
   - During active tracking with battery saver enabled, the system SHALL evaluate the derived slope sensor (`SensorType.SLOPE` in %), active Heart Rate zone ($Z_{HR}$), and active Cycling Power zone ($Z_{PWR}$):
     - *Full Dimming* (Default $0.15f$ / 15% brightness): Triggered when $\text{Slope} < 2.0\%$ **AND** $Z_{HR} \le \text{Zone 2}$ **AND** $Z_{PWR} \le \text{Zone 2}$ (for cycling). Maximizes OLED battery conservation during flat cruising, descent, or recovery.
     - *Medium Dimming* (Default $0.50f$ / 50% brightness): Triggered when $2.0\% \le \text{Slope} \le 5.0\%$ **OR** $Z_{HR} == \text{Zone 3}$ **OR** $Z_{PWR} == \text{Zone 3}$. Provides comfortable visibility during tempo efforts while saving 30–50% display power.
     - *Full Illumination* (1.0f / 100% brightness): Triggered when $\text{Slope} > 5.0\%$ **OR** $Z_{HR} \ge \text{Zone 4}$ **OR** $Z_{PWR} \ge \text{Zone 4}$. Ensures maximum peripheral visibility during hard climbs, threshold work, and intervals.
3. **Graceful Sensor Fallback & Damping Hysteresis**:
   - If Power is absent (or sport is running/hiking), the system SHALL evaluate Slope and $Z_{HR}$.
   - If Heart Rate is absent, the system SHALL evaluate Slope and $Z_{PWR}$.
   - If neither Heart Rate nor Power is available, the system SHALL fall back to pure Slope thresholds ($<2\%$, $2-5\%$, $>5\%$).
   - The system SHALL apply 3-second moving average smoothing to cycling power telemetry and a $3-5\text{s}$ damping hysteresis before executing downward brightness transitions to prevent rapid visual oscillations.
4. **Event-Based Wakeup Triggers**:
   - The system SHALL immediately restore 100% full brightness (`WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE`) for $15\text{ seconds}$ upon:
     - User touch interaction or tab swipe on `TrackingTabsScreen`.
     - Contactless hand wave detected via hardware Proximity Sensor (`Sensor.TYPE_PROXIMITY`).
     - Live Strava Segment status transition to *Approaching*, *Start*, or *Finish* (`LiveSegmentsRepository`).
     - Manual lap split trigger (`[+] Runde`).
     - Auto-pause trigger or acceleration restart from standstill.
   - Overlapping wakeup events SHALL reset the 15-second countdown timer.
5. **Window-Scoped Brightness Safety Invariant**:
   - Screen brightness overrides SHALL be applied strictly to the active `Activity` window attributes (`window.attributes.screenBrightness`), cleanly releasing upon backgrounding or screen exit.
   - Minimum brightness SHALL enforce a safety floor of $\ge 0.05f$ to prevent the screen from turning completely off.
   - Detailed fine-tuning of dimming percentages and slope thresholds SHALL be decoupled into the Advanced Tuning Preferences screen (ATT-1304).
6. **100% Localization Parity Across 9 Locales**:
   - All newly introduced string resources SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with strict positional format specifiers.

**Acceptance Criteria (Given-When-Then)**:
- *Given* an athlete opening Display Settings,
- *Then* an "Einsparungen" section SHALL appear separated by a divider above Cockpit Dark Mode, containing the Akkusparer switch.
- *Given* active tracking with Battery Saver enabled, slope $< 2\%$, and HR in Zone 1,
- *When* no interaction or workout event occurs,
- *Then* window brightness SHALL dim down to 15%.
- *Given* the display is in dimmed state,
- *When* the athlete waves their hand over the proximity sensor or touches the screen,
- *Then* brightness SHALL immediately return to 100% for 15 seconds.
- *Given* the athlete approaches a steep climb with slope $> 5\%$,
- *Then* brightness SHALL automatically elevate to 100% without requiring user interaction.
- *Given* an athlete running without a power meter,
- *Then* the state machine SHALL evaluate Slope and Heart Rate zones seamlessly without crash or delay.

**Invariants**: Screen brightness safety floor $\ge 0.05f$; non-cockpit window isolation; existing display options (`forcePortrait`, `keepScreenOn`, `noUnlocking`) preserved; 9-language translation parity.

---

## 3. Test Specification & Verification Catalog (`TST-UI-126`)

### TST-UI-126: AMOLED Battery Saver Multi-Factor Dimming & Wakeup Verification

#### Test Suite 1: Pure State Machine Unit Verification (`BatterySaverStateMachineTest.kt`)
1. **Full Dimming Matrix**:
   - Slope $-1.5\%$, HR Zone 1, Power Zone 1 $\rightarrow$ `FULL_DIM` ($0.15f$).
   - Slope $0.0\%$, HR Zone 2, Power Zone 2 $\rightarrow$ `FULL_DIM` ($0.15f$).
   - Slope $1.9\%$, HR Zone 2, Power Zone 1 $\rightarrow$ `FULL_DIM` ($0.15f$).
2. **Medium Dimming Matrix**:
   - Slope $2.0\%$, HR Zone 1, Power Zone 1 $\rightarrow$ `MEDIUM_DIM` ($0.50f$) (due to slope).
   - Slope $0.0\%$, HR Zone 3, Power Zone 1 $\rightarrow$ `MEDIUM_DIM` ($0.50f$) (due to HR).
   - Slope $0.0\%$, HR Zone 1, Power Zone 3 $\rightarrow$ `MEDIUM_DIM` ($0.50f$) (due to Power).
   - Slope $4.9\%$, HR Zone 3, Power Zone 3 $\rightarrow$ `MEDIUM_DIM` ($0.50f$).
3. **No Dimming Matrix (Full Illumination)**:
   - Slope $5.1\%$, HR Zone 1, Power Zone 1 $\rightarrow$ `NO_DIM` ($1.0f$) (steep grade).
   - Slope $0.0\%$, HR Zone 4, Power Zone 1 $\rightarrow$ `NO_DIM` ($1.0f$) (threshold HR).
   - Slope $0.0\%$, HR Zone 1, Power Zone 5 $\rightarrow$ `NO_DIM` ($1.0f$) (anaerobic Power).
   - Slope $10.0\%$, HR Zone 5, Power Zone 6 $\rightarrow$ `NO_DIM` ($1.0f$).

#### Test Suite 2: Sensor Fallback & Edge Case Matrix (`BatterySaverStateMachineTest.kt`)
1. **Running Activity / Missing Power Meter**:
   - Sport type `RUNNING`, Power is absent $\rightarrow$ state machine ignores Power and computes:
     - Slope $<2\%$, HR Zone 1 $\rightarrow$ `FULL_DIM`.
     - Slope $3\%$, HR Zone 1 $\rightarrow$ `MEDIUM_DIM`.
     - Slope $0\%$, HR Zone 4 $\rightarrow$ `NO_DIM`.
2. **Missing Heart Rate Monitor**:
   - HR is absent, Power is present $\rightarrow$ state machine ignores HR:
     - Slope $<2\%$, Power Zone 2 $\rightarrow$ `FULL_DIM`.
     - Slope $<2\%$, Power Zone 3 $\rightarrow$ `MEDIUM_DIM`.
     - Slope $<2\%$, Power Zone 4 $\rightarrow$ `NO_DIM`.
3. **Pure GPS / Standalone Device (Zero Peripheral Sensors)**:
   - Both HR and Power are absent $\rightarrow$ pure slope evaluation:
     - Slope $1.0\% \rightarrow$ `FULL_DIM`.
     - Slope $3.0\% \rightarrow$ `MEDIUM_DIM`.
     - Slope $6.0\% \rightarrow$ `NO_DIM`.

#### Test Suite 3: Wakeup Controller & Timer Lifecycle (`BatterySaverControllerTest.kt`)
1. **Wakeup Interrupt Execution**:
   - Initial state: `FULL_DIM`.
   - Trigger Touch event $\rightarrow$ brightness immediately snaps to $1.0f$ (`BRIGHTNESS_OVERRIDE_NONE`).
   - Trigger Proximity NEAR event $\rightarrow$ brightness snaps to $1.0f$.
   - Trigger Live Segment status `APPROACHING` $\rightarrow$ brightness snaps to $1.0f$.
   - Trigger Lap event $\rightarrow$ brightness snaps to $1.0f$.
   - Trigger Auto-pause / resume $\rightarrow$ brightness snaps to $1.0f$.
2. **Timer Expiration & Overlapping Reset**:
   - Wakeup event at $t = 0\text{ s}$, duration $15\text{ s}$. At $t = 10\text{ s}$, a touch event occurs.
   - Assert timer resets to $15\text{ s}$ from $t = 10\text{ s}$ (total illumination until $t = 25\text{ s}$).
   - At $t = 26\text{ s}$ without further events, verify smooth transition back to the calculated state machine brightness.

#### Test Suite 4: Settings Dialog & Persistence (`DisplaySettingsDialogTest.kt`)
1. **Category Structure**:
   - Verify `DisplaySettingsDialog` renders the divider above the savings section.
   - Verify "Einsparungen" header and subtitle appear.
   - Verify Akkusparer switch is rendered.
2. **Persistence Integrity**:
   - Verify toggling Akkusparer commits to `TrainingApplication.setBatterySaverEnabled(true)`.
   - Verify uncommitted dismiss preserves existing state.

#### Test Suite 5: Localization & Regression Execution
1. **9-Language String Parity**:
   - Verify keys `settings_category_savings`, `settings_category_savings_desc`, `prefs_battery_saver_title`, `prefs_battery_saver_summary` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
2. **Full-Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` and confirm 100% pass with 0 failures and 0 regressions.
