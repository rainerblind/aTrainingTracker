# Stage 2: Requirement & Test Specification - ATT-3555: Compare Estimated HRVT1/HRVT2 Against Configured Training Zones with Multi-Session Consensus and Anchor-Based Updates (ZoneCalculationMath)

**Ticket**: [ATT-3555](https://rainerblind.atlassian.net/browse/ATT-3555)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-037` (*HRV Threshold Training Zone Comparison, Multi-Session Consensus Gating & Anchor-Based Power Zone Updates*)  
**Test Spec ID**: `TST-PER-045` (*HRV Threshold Training Zone Comparison, Consensus Gating & Power Zone Update Suite*)  
**Branch**: `feature/ATT-3555` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-037)

### 1.1 Problem Statement & Rationale
Athletes can estimate physiological $HRVT_1$ and $HRVT_2$ thresholds (`ATT-3554`, `ATT-4969`), but Workout Details lacks contextual comparison against configured training zones (`ZoneCalculationMath.kt`). Uncontrolled overwriting of FTP from single noisy workouts risks corrupting training zones, while modifying HR zones from submaximal thresholds is scientifically invalid because $HR_{\max}$ cannot be observed without exhaustive testing.

### 1.2 Functional & Architectural Requirements
1. **Power Zone Delta & Coggan Anchor Comparison (`REQ-PER-037.1`)**: Compare steady-state $HRVT_1$ power ($P_{\text{HRVT1,ss}}$) against Coggan Zone 2 ceiling (`z2Max = round(0.75 * ftp)`), computing $\Delta P_{\text{T1}} = P_{\text{HRVT1,ss}} - z2Max$. Compare steady-state $HRVT_2$ power ($P_{\text{HRVT2,ss}}$) against configured Functional Threshold Power (`100% FTP`), computing $\Delta P_{\text{T2}} = P_{\text{HRVT2,ss}} - FTP$. Omit deltas when thresholds are unestimated.
2. **Informational Heart Rate Comparison & Non-Overwrite Invariant (`REQ-PER-037.2`)**: Compare $\text{HR}_{\text{HRVT1}}$ against Heart Rate `z2Max` ($\Delta \text{HR}_{\text{T1}}$) and express as $\% HR_{\max}$. Compare $\text{HR}_{\text{HRVT2}}$ against Heart Rate `z4Max` ($\Delta \text{HR}_{\text{T2}}$) and express as $\% HR_{\max}$. Display explanatory tooltip. Strictly prohibit automatic overwrites of HR zones or $HR_{\max}$.
3. **Multi-Session Consensus & Guided Test Gating (`REQ-PER-037.3`)**: Gate power zone (`FTP`) updates via `HrvZoneComparisonMath.evaluateConsensusGate`. Unlock only if:
   - Condition A: Session is a `Confidence.HIGH` Guided Step or Ramp Test (`ATT-4969`), OR
   - Condition B: $M \ge 3$ recent sessions (within 30 days) with `HIGH` or `MEDIUM` confidence agree within $\le 10\text{ W}$ ($\max(P) - \min(P) \le 10\text{ W}$), using the median FTP.
4. **Summary Card Zone Comparison Section (`REQ-PER-037.4`)**: In `HrvThresholdSummaryCard`, display power and HR delta chips, consensus status badge (`LOCKED` or `UNLOCKED`), and an "Update Power Zones" action button (disabled with reason when locked).
5. **User-Confirmed Power Zone Update Flow (`REQ-PER-037.5`)**: On button tap when unlocked, show confirmation dialog displaying target FTP, protocol provenance, ramp slope & MRT adjustment, and MLSS cautionary note. On confirmation, update `SettingsDataStore.setFtp(newFtp)` and recalculate `calculateCogganPowerZones(newFtp)`.
6. **Mader-Heck Forwarding & Localization Parity (`REQ-PER-037.6`)**: Provide action link forwarding $(P_{\text{HRVT1}}, P_{\text{HRVT2}})$ into `MaderCalibrationDialog` (`ATT-4799`). Maintain 100% localization parity across all 9 supported locales in `strings_hrv.xml`.

### 1.3 System Invariants
- Automatic zone updates apply strictly to Power (`FTP`); HR zones remain 100% informational.
- Consensus gating prohibits single unverified free rides from updating zones.
- 100% 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Test Specification (TST-PER-045)

### Test Case 1: Power Zone Delta & Ceiling Calculation (`TST-PER-045.1`)
* **Scope**: Unit Test (`HrvZoneComparisonMathTest.kt`)
* **Preconditions**: Configured $\text{FTP} = 280\text{ W}$ (`z2Max = 210 W`); estimated $P_{\text{HRVT1}} = 215\text{ W}$, $P_{\text{HRVT2}} = 285\text{ W}$.
* **Action**: Execute `HrvZoneComparisonMath.comparePowerZones`.
* **Expected Result**: $\Delta P_{\text{T1}} = +5\text{ W}$, $\Delta P_{\text{T2}} = +5\text{ W}$. Returns `null` deltas for `INSUFFICIENT_SPAN`.

### Test Case 2: Informational Heart Rate Comparison (`TST-PER-045.2`)
* **Scope**: Unit Test (`HrvZoneComparisonMathTest.kt`)
* **Preconditions**: Configured $HR_{\max} = 190\text{ bpm}$, `z2Max = 142 bpm`, `z4Max = 170 bpm`; estimated $\text{HR}_{\text{HRVT1}} = 146\text{ bpm}$, $\text{HR}_{\text{HRVT2}} = 172\text{ bpm}$.
* **Action**: Execute `HrvZoneComparisonMath.compareHeartRateZones`.
* **Expected Result**: $\Delta \text{HR}_{\text{T1}} = +4\text{ bpm}$ ($77\% HR_{\max}$), $\Delta \text{HR}_{\text{T2}} = +2\text{ bpm}$ ($91\% HR_{\max}$). No mutation to $HR_{\max}$.

### Test Case 3: Consensus Gating Logic (`TST-PER-045.3`)
* **Scope**: Unit Test (`HrvZoneComparisonMathTest.kt`)
* **Preconditions**: Various session configurations (`GUIDED_STEP` HIGH, single free ride, 3 agreeing free rides $\pm 10\text{ W}$, 3 disparate free rides $> 10\text{ W}$).
* **Action**: Evaluate `evaluateConsensusGate`.
* **Expected Result**: `UNLOCKED` for Guided Test HIGH and 3 agreeing sessions; `LOCKED` for all other configurations.

### Test Case 4: Median FTP Aggregation (`TST-PER-045.4`)
* **Scope**: Unit Test (`HrvZoneComparisonMathTest.kt`)
* **Preconditions**: 3 recent sessions with $HRVT_2$ estimates $\{278\text{ W}, 280\text{ W}, 284\text{ W}\}$.
* **Action**: Compute suggested consensus FTP.
* **Expected Result**: Emits median value $280\text{ W}$.

### Test Case 5: Summary Card UI & Delta Chips (`TST-PER-045.5`)
* **Scope**: Compose UI Test (`HrvThresholdSummaryCardTest.kt`)
* **Preconditions**: Render card with power/HR deltas and locked/unlocked consensus states.
* **Action**: Assert Compose semantic nodes.
* **Expected Result**: Delta chips rendered; update button enabled when unlocked and disabled with explanation when locked.

### Test Case 6: Confirmation Dialog & Power Zone Save (`TST-PER-045.6`)
* **Scope**: Compose UI Test (`HrvThresholdSummaryCardTest.kt`)
* **Preconditions**: Unlocked consensus; tap "Update Power Zones".
* **Action**: Confirm dialog.
* **Expected Result**: Confirmation dialog shows target FTP, protocol, and MLSS warning; updates `SettingsDataStore.setFtp`.

### Test Case 7: 9-Language Localization Parity (`TST-PER-045.7`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Preconditions**: 24 string keys in `strings_hrv.xml`.
* **Action**: Run `TranslationParityTest`.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-045.1` | Unit | `HrvZoneComparisonMath.kt` | `REQ-PER-037.1` | Specified |
| `TST-PER-045.2` | Unit | `HrvZoneComparisonMath.kt` | `REQ-PER-037.2` | Specified |
| `TST-PER-045.3` | Unit | `HrvZoneComparisonMath.kt` | `REQ-PER-037.3` | Specified |
| `TST-PER-045.4` | Unit | `HrvZoneComparisonMath.kt` | `REQ-PER-037.3` | Specified |
| `TST-PER-045.5` | Compose | `HrvThresholdSummaryCard.kt` | `REQ-PER-037.4` | Specified |
| `TST-PER-045.6` | Compose | `HrvThresholdSummaryCard.kt` | `REQ-PER-037.5` | Specified |
| `TST-PER-045.7` | Locale | `strings_hrv.xml` | `REQ-PER-037.6` | Specified |
