# Stage 2: Requirement & Test Specification - ATT-7310: Enable Anchor-Based Heart Rate Training Zone Updates from HRVT1/HRVT2 Consensus

**Ticket**: [ATT-7310](https://rainerblind.atlassian.net/browse/ATT-7310)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PER-037.2`, `REQ-PER-037.7` (*HRV Threshold Training Zone Comparison & Anchor-Based Power/HR Zone Updates*)  
**Test Spec ID**: `TST-PER-045` (`TST-PER-045.8`..`TST-PER-045.11`)  
**Branch**: `subagent-Lane-1-Agent---ATT-7310-self-d5962554` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PER-037.7)

### 1.1 Problem Statement & Rationale
Athletes currently receive informational heart rate comparison deltas against configured $HR_{\max}$ boundaries in `HrvThresholdSummaryCard`, but have no automated or guided way to apply verified aerobic ($HRVT_1$) and anaerobic ($HRVT_2$ / LTHR) physiological anchors to their Heart Rate training zones.

### 1.2 Functional & Architectural Requirements
1. **Threshold-Anchored HR Zone Calculation (`ZoneCalculationMath`)**:
   - Zone 2 aerobic threshold ceiling SHALL anchor strictly at $HRVT_1$ (`z2Max = hrvt1`).
   - Zone 4 anaerobic threshold / LTHR ceiling SHALL anchor strictly at $HRVT_2$ (`z4Max = hrvt2`).
   - Zone 3 ceiling SHALL be the physiological midpoint: $\text{round}((hrvt1 + hrvt2) / 2.0)$.
   - Zone 1 ceiling SHALL be $\text{round}(hrRest + 0.70 \times (hrvt1 - hrRest))$ when $hrRest < hrvt1$, otherwise $\text{round}(0.85 \times hrvt1)$.
   - Monotonic rationalization SHALL guarantee $Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$.
2. **Heart Rate Consensus Gating (`HrvZoneComparisonMath.evaluateHrConsensusGate`)**:
   - The gate SHALL evaluate to `UNLOCKED` if EITHER:
     - Condition A (Guided Test): The session is a `Confidence.HIGH` Guided Step or Ramp Test (`ATT-4969`) with valid $(HRVT_1, HRVT_2)$ and $HRVT_2 \ge HRVT_1 + 5\text{ bpm}$.
     - Condition B (Multi-Session Consensus): At least $M \ge 3$ recent sessions within 30 days have `Confidence.HIGH` or `Confidence.MEDIUM` HR threshold estimates where $\max(HRVT_1) - \min(HRVT_1) \le 6\text{ bpm}$ and $\max(HRVT_2) - \min(HRVT_2) \le 6\text{ bpm}$. Suggested anchors SHALL be the statistical medians of the respective threshold series.
   - Workouts with spread $> 6\text{ bpm}$, $< 3$ qualifying sessions, or low confidence SHALL evaluate to `LOCKED`.
3. **Summary Card Action & Modal Confirmation Flow (`HrvThresholdSummaryCard`)**:
   - `HrvThresholdSummaryCard` SHALL render an "Update HR Zones" button. When locked, it is disabled with an explanatory diagnostic lock reason.
   - When unlocked, tapping launches a modal confirmation dialog detailing old vs. suggested $Z_2$ and $Z_4$ boundaries, protocol provenance, and noting that existing manual custom bounds will be replaced while preserving the ability to manually adjust zones in Zone Settings.
4. **DataStore Persistence & ViewModel State (`SettingsDataStore`, `TrackOnMapAftermathViewModel`)**:
   - Upon confirmation, `TrackOnMapAftermathViewModel.updateHrZones` SHALL save the calculated zones to `SettingsDataStore.saveCalculatedHeartRateZones(zoneType, hrMax, hrRest, method = "HRVT_ANCHORS", cutoffs)` and update `_uiState.value.hrThresholds`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (HR Consensus Gating & Median Aggregation)**:
  * *Given* 3 workouts within 30 days with $HRVT_1 \in \{144, 145, 148\}\text{ bpm}$ and $HRVT_2 \in \{170, 172, 174\}\text{ bpm}$ ($\text{spread} \le 6\text{ bpm}$),
  * *When* `evaluateHrConsensusGate` is evaluated,
  * *Then* it returns `UNLOCKED` with `suggestedHrvt1 = 145` and `suggestedHrvt2 = 172`.
* **Criterion 2 (Confirmation Dialog & Persistence)**:
  * *Given* an unlocked HR consensus state on `HrvThresholdSummaryCard`,
  * *When* the athlete taps "Update HR Zones" and confirms the modal dialog,
  * *Then* the calculated zones are saved to `SettingsDataStore` with method `HRVT_ANCHORS` and the UI updates the displayed boundaries.

### 1.4 System Invariants
1. No silent or automatic overwrites of heart rate zones without explicit modal athlete confirmation.
2. Single-session noisy workouts without consensus remain locked.
3. Strict monotonicity ($Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$).
4. Full 9-language localization parity in `strings_hrv.xml`.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-037.2` (Informational HR Comparison & Non-Overwrite Invariant) and net-new `REQ-PER-037.7` in `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3555` (commit `a216f4a3`).
* **Root Reason for Existing Formulation**: Protected athletes against erroneous silent overwriting of HR zones or $HR_{\max}$ from single uncalibrated workouts or noisy submaximal data.
* **Preservation of Core Invariants**: Silent background overwrites remain strictly prohibited; updates require explicit athlete confirmation via modal dialog and are gated behind high-confidence guided tests or 30-day multi-session consensus ($M \ge 3$, spread $\le 6\text{ bpm}$); full support for subsequent manual customization in Zone Settings is preserved; integer monotonicity ($Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$) is mathematically guaranteed.

---

## 2. Test Specification (TST-PER-045)

### Test Case 1: `testCalculateThresholdAnchoredHeartRateZones` (`TST-PER-045.8`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculationMathTest.kt`
* **Preconditions**: Valid $HRVT_1 = 145\text{ bpm}$, $HRVT_2 = 172\text{ bpm}$, $HR_{\max} = 190\text{ bpm}$, $HR_{\text{rest}} = 55\text{ bpm}$.
* **Action**: Invoke `ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(145, 172, 190, 55)`.
* **Expected Result**: `z2Max == 145`, `z4Max == 172`, `z3Max == 159` (midpoint), $z1Max < z2Max$, monotonic progression $z1 < z2 < z3 < z4 < 190$.

### Test Case 2: `testEvaluateHrConsensusGate_multiSessionAndGuided` (`TST-PER-045.9`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvZoneComparisonMathTest.kt`
* **Preconditions**: Sessions within 30 days agreeing within $\le 6\text{ bpm}$, versus spread $> 6\text{ bpm}$.
* **Action**: Invoke `HrvZoneComparisonMath.evaluateHrConsensusGate(...)`.
* **Expected Result**: `UNLOCKED` with medians for agreeing sessions; `LOCKED` with reason for excessive spread or $< 3$ sessions.

### Test Case 3: `testHrvThresholdSummaryCard_hrZoneUpdateContract` (`TST-PER-045.10`)
* **Scope**: Contract / UI Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvThresholdSummaryCardTest.kt`
* **Preconditions**: Composable contract inspects `onUpdateHrZones` parameter and tags `hrv_btn_update_hr_zones`, `hrv_hr_zone_confirm_dialog`.
* **Expected Result**: Tags and methods exist and match contract.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-PER-045.11`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-045.8` | Unit | `ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones` | `REQ-PER-037.7` | Specified |
| `TST-PER-045.9` | Unit | `HrvZoneComparisonMath.evaluateHrConsensusGate` | `REQ-PER-037.7` | Specified |
| `TST-PER-045.10` | UI/Contract | `HrvThresholdSummaryCard`, `TrackOnMapAftermathViewModel.updateHrZones` | `REQ-PER-037.7` | Specified |
| `TST-PER-045.11` | Localization | `TranslationParityTest` | `REQ-PER-037.6`, `REQ-PER-037.7` | Specified |
