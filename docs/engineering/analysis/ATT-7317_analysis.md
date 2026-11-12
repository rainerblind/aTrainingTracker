# Stage 1 Analysis: ATT-7317 - Promote HRV DFA a1 aerobic and anaerobic thresholds into a dedicated Training Zones tab

**Ticket**: [ATT-7317](https://rainerblind.atlassian.net/browse/ATT-7317)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability / HRV*)  
**Target Release**: `Sprint 2026-46.4`  
**Active Sprint**: `sprint/2026-46.4`  
**Branch**: `subagent-Lane-1-Agent---ATT-7317-self-7f572b08`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Currently, aerobic and anaerobic heart rate and power training zones (`HR Run`, `HR Bike`, `Power Bike`) reside in `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`), while HRV Detrended Fluctuation Analysis (DFA) $\alpha_1$ thresholds ($T_1$ aerobic, $T_2$ anaerobic) are buried inside `PhysiologyModelsSection.kt` under `AdvancedTuningScreen.kt`. Athletes seeking to inspect, understand, or personalize their physiological pacing zones encounter a fragmented user experience. Furthermore:
1. `ZoneSettingsScreen` only provides 3 tabs (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`), missing a dedicated 4th tab for HRV DFA $\alpha_1$.
2. The DFA $\alpha_1$ sliders in `PhysiologyModelsSection.kt` are redundant and duplicate physiological zone concepts outside the central Training Zones hub.
3. In `HrvRampTestViewModel.kt`, early sub-exhaustion completion relies on a hardcoded cutoff `0.45` instead of dynamically adhering to the athlete's customized anaerobic threshold $T_2$ ($T_2 - 0.05$).

Promoting HRV DFA $\alpha_1$ thresholds into a dedicated 4th tab in `ZoneSettingsScreen` unifies all physiological zones in one place, provides $0.01$-step precision, live theme-aware `SensorFieldView` preview, invariant enforcement ($T_1 \ge T_2 + 0.05$), Reset to Defaults, and dynamic early completion in Guided HRV Ramp Tests.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Architectural Gap**: `ZoneSettingsScreen` was historically built around 5-zone HR and 7-zone Power models managed by `ZoneProfileState` and `SettingsDataStore`. HRV DFA $\alpha_1$ was subsequently introduced in `ATT-3542` and `ATT-6715` as part of `TuningPreferencesDataStore` with controls situated in `PhysiologyModelsSection.kt`.
* **Independent Peer Review & Verification**:
  * Cross-checked `ZonesSettingsActivity.kt`: `profileNameResIds` currently defines 3 tabs (`R.string.tab_hr_run`, `R.string.tab_hr_bike`, `R.string.tab_pwr_bike`) and `pagerState` is bound to `profileNameResIds.size`. Top bar exposes `Icons.Default.Calculate` and `Icons.Default.History` unconditionally, which are HR/Power specific.
  * Cross-checked `TuningPreferencesDataStore.kt`: `KEY_DFA_A1_THRESHOLD_T1` and `KEY_DFA_A1_THRESHOLD_T2` already exist with `dfaA1ThresholdT1Flow` and `dfaA1ThresholdT2Flow`, defaulting to `0.75f` and `0.50f`, with clamping helper `setDfaA1Thresholds(t1, t2)` enforcing `MIN_DFA_A1_THRESHOLD_GAP` (`0.05f`).
  * Cross-checked `HrvZoneIndicator.kt`: Computes semantic colors (`TTColor.Zone2` for $> T_1$, `TTColor.Zone3` for $T_2 \le \alpha_1 \le T_1$, `TTColor.Zone4` for $< T_2$) and 5-zone gauge state.
  * Cross-checked `HrvRampTestViewModel.kt` line 406: Live early completion check evaluates `dfaA1 != null && dfaA1 < 0.45` with hardcoded `0.45` rather than reading $T_2$ from `TuningPreferencesDataStore`.
  * Cross-checked `NavRoutes.kt`: `trainingZones(initialTab = 3)` route helper already supports integer index parameter, making tab 3 immediately deep-linkable.
  * Confirmed hypothesis: Promoting $T_1$/$T_2$ to `ZoneSettingsScreen`, removing redundant sliders from `PhysiologyModelsSection.kt`, and wiring $T_2$ into `HrvRampTestViewModel` achieves complete physiological zone unification with zero data-migration risk.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add 4th tab `R.string.tab_hrv_dfa_a1` (`HRV (DFA a1)`) to `ZoneSettingsScreen` in `ZonesSettingsActivity.kt`.
  2. Implement 3 inverse-intensity physiological bands (Zone 2/Green $> T_1$, Zone 3/Orange $T_2..T_1$, Zone 4/Red $< T_2$) with `0.01` precision controls (stepper / text entry + slider) and $T_1 \ge T_2 + 0.05$ invariant enforcement.
  3. Include Reset to Defaults (`0.75 / 0.50`), Info sheet (`HrvInfoBottomSheet`), and Live Theme-Aware `SensorFieldView` Preview with interactive pills (`Aerobic`, `Transition`, `Anaerobic`).
  4. Hide HR/Power-only `ZoneCalculatorDialog` and `ZoneHistoryDialog` top-bar action icons when the HRV DFA $\alpha_1$ tab is selected.
  5. Remove redundant $T_1$ and $T_2$ sliders from `PhysiologyModelsSection.kt` in `AdvancedTuningScreen.kt` (retaining `HRV Artifact Gate` slider).
  6. Update `HrvRampTestViewModel.kt` to dynamically read $T_2$ from `TuningPreferencesDataStore` and trigger early completion at `dfaA1 < (dfaA1ThresholdT2 - 0.05).coerceAtLeast(0.25f)`.
  7. Maintain 100% 9-language localization parity across all modified/added strings.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying SQLite schema or `WorkoutRrIntervals` table format.
  2. Altering Kubios/NeuroKit2 DFA $\alpha_1$ calculation algorithms or window lengths.
  3. Redesigning HR or Power zone calculation algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-061.4` (*User-Configurable Individual Threshold Overrides*) and `REQ-TRK-072.5` (*Early Sub-Exhaustion Auto-Completion Detection*).
* **Historical Origin & Commit Trace**:
  * `REQ-TRK-061.4` was introduced in `ATT-3542` and refined in `ATT-6715` (Sprint `2026-46.1`), placing initial threshold sliders in `PhysiologyModelsSection.kt` as an interim home.
  * `REQ-TRK-072.5` was introduced in `ATT-4969` (commit `69f52f40b`), using hardcoded `0.45` as a proxy for $T_2 - 0.05$ based on population defaults ($0.50 - 0.05 = 0.45$).
* **Root Reason for Existing Formulation**:
  * In `ATT-3542`/`ATT-6715`, `ZoneSettingsScreen` was limited to `SettingsDataStore.ZoneType` (HR & Power). Thresholds were temporarily placed in Advanced Tuning.
  * In `ATT-4969`, dynamic coupling between `HrvRampTestViewModel` and `TuningPreferencesDataStore` was deferred to avoid cross-module churn during initial step test delivery.
* **Preservation of Core Invariants**:
  * Population defaults ($T_1 = 0.75$, $T_2 = 0.50$) and physiological gap invariant ($T_1 \ge T_2 + 0.05$, bounds $[0.20, 1.50]$) are strictly preserved.
  * All downstream consumers (`HrvZoneIndicator`, `TelemetryMetricGraph`, `HrvAlpha1ScatterChart`, `HrvThresholdSummaryCard`) read from `TuningPreferencesDataStore` flows and will immediately reflect the user's settings.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-061.4` (amended: threshold configuration promoted to Training Zones 4th tab), `REQ-TRK-072.5` (amended: dynamic early completion cutoff using $T_2 - 0.05$).
* **Affected Architecture Elements**:
  * `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`, tab bar, top bar icons, new `HrvDfaA1SettingsContent`).
  * `PhysiologyModelsSection.kt` (removal of redundant $T_1$/$T_2$ sliders; retain artifact gate slider).
  * `AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt` (remove redundant callbacks).
  * `HrvRampTestViewModel.kt` (inject `TuningPreferencesDataStore`, read `dfaA1ThresholdT2Flow`, evaluate dynamic cutoff).
  * Resource files: `strings.xml` / `strings_hrv.xml` (all 9 canonical languages).
* **Verification Impact & Test Surface**:
  * Unit tests for `ZoneSettingsScreen` 4th tab rendering, invariant clamping, and reset action.
  * `HrvRampTestViewModelTest` asserting dynamic early-cutoff trigger when $T_2$ is customized.
  * Contract tests for `PhysiologyModelsSection` and localization parity across 9 languages.
* **Risk Assessment & Invariants**:
  * Risk: Low. DataStore keys `KEY_DFA_A1_THRESHOLD_T1` and `KEY_DFA_A1_THRESHOLD_T2` remain identical; zero schema migrations; zero breaking changes to existing HR/Power zones.

---

## 6. Architectural Strategy & High-Level Solution
1. **`ZoneSettingsScreen` Tab 4 (`HrvDfaA1SettingsContent`)**:
   * Add `R.string.tab_hrv_dfa_a1` to `profileNameResIds` (4 tabs total).
   * In header top bar: only show `Calculate` and `History` buttons when `pagerState.currentPage < 3`. When on tab 3, optionally provide an Info (`?`) action or threshold test CTA.
   * Author `HrvDfaA1SettingsContent` composable:
     * Expose controls for $T_1$ (Aerobic, $[0.60, 1.20]$, default `0.75`) and $T_2$ (Anaerobic, $[0.30, 0.80]$, default `0.50`) with step `0.01` and continuous sliders/steppers.
     * Enforce $T_1 \ge T_2 + 0.05$ immediately on edit and persist to `TuningPreferencesDataStore`.
     * Card rendering the 3 physiological bands with semantic colors (`TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`).
     * "Reset to Defaults" button resetting to `0.75` and `0.50`.
     * Info button launching `HrvInfoBottomSheet(dfaA1ThresholdT1 = t1, dfaA1ThresholdT2 = t2)`.
     * Live Theme-Aware `SensorFieldView` Preview with interactive pills: `Aerobic` ($\alpha_1 > T_1$), `Transition` ($T_2 \le \alpha_1 \le T_1$), `Anaerobic` ($\alpha_1 < T_2$), previewing the gauge bar using `HrvZoneIndicator.calculateZoneGaugeState`.
2. **Cleanup of `PhysiologyModelsSection.kt`**:
   * Remove $T_1$ and $T_2$ sliders from `PhysiologyModelsSection.kt`, keeping `HRV Artifact Gate` ($3.0\% - 5.0\%$) and adding a helpful note/link to Training Zones.
3. **Dynamic Early Cutoff in `HrvRampTestViewModel.kt`**:
   * Collect `dfaA1ThresholdT2Flow` from `TuningPreferencesDataStore` into `private var dfaA1ThresholdT2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2`.
   * Early completion evaluation: `val cutoff = (dfaA1ThresholdT2 - 0.05f).coerceAtLeast(0.25f); if (dfaA1 != null && dfaA1 < cutoff) ...`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. $T_1 \ge T_2 + 0.05$ gap strictly enforced across all inputs and DataStore writes.
  2. Zero regression in existing HR Run, HR Bike, and Power Bike zone editing or calculation.
  3. Single source of truth via `TuningPreferencesDataStore` (`KEY_DFA_A1_THRESHOLD_T1`, `KEY_DFA_A1_THRESHOLD_T2`).
  4. 100% 9-language translation parity for all new strings.
* **Risk Rating**: **LOW** (UI layout enhancement and clean-up of existing DataStore properties, no database migration, no background concurrency risks).
