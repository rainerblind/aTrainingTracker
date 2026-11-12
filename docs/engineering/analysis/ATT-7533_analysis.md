# Stage 1 Analysis: ATT-7533 - Fix Morning HRV False-Positive Smoothed Warning on Polar H10 & UI Ergonomics

**Ticket**: [ATT-7533](https://rainerblind.atlassian.net/browse/ATT-7533)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7533` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
During physical testing with a Polar H10 ECG chest strap in the evening (20:09, seated upright, 83 bpm), Morning HRV Check flagged genuine data as `SMOOTHED_STREAM` ("Geglätteter RR-Datenstrom erkannt"). Three UI defects were also observed: unnatural German subtitle (*"Aufwachprotokoll für autonome Erholung"*), duplicate *"Messung abgeschlossen"* header, unhighlighted *Verwerfen* button when discard is recommended, and vertical text clipping on the *Messung speichern* button due to fixed `52.dp` height.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (False-Positive Smoothed Gate)**: In `HrvSignalQualityAnalyzer.kt:125-126`, `isDiscordant` unconditionally flagged `rMSSD < 10.0 ms && DFA a1 > 1.35`. Authentic human sinus rhythm during sympathetic/evening/orthostatic states naturally has low vagal tone ($rMSSD = 4.5\text{ ms}$) while baroreflex Mayer-wave oscillations ($SDNN = 8.7\text{ ms}$) produce steep log-log scaling ($DFA\ \alpha_1 > 1.35$, e.g. 1.52). Unlike firmware moving averages ($TPR < 0.28$), authentic ECG retains normal turning-point sign reversals ($TPR \ge 0.28$, observed 0.378 on Polar H10).
* **RCA 2 (UI Subtitle, Duplication & Clipping)**: `morning_hrv_subtitle` was literally translated; `ReadinessResultCard` reused `morning_hrv_state_completed` (duplicating the status badge); action buttons statically styled *Speichern* as primary; and fixed `height(52.dp)` caused line wrapping and vertical clipping of *"speichern"*.
* **Independent Peer Review & Verification**:
  * Verified `HrvSignalQualityAnalyzer.kt#L125-L126`: `isDiscordant` checked only `computedRmssd` and `computedDfa` without `tpr` guard.
  * Verified `MorningHrvCheckScreen.kt#L148`, `#L654`, `#L827-870`: confirmed duplicate string reference, static button hierarchy, and fixed `52.dp` height.
  * Verified that requiring `(tpr != null && tpr < 0.28) || computedRmssd < 2.0` in the discordance check resolves the false positive while preserving Magene detection ($TPR < 0.20$).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Refine `isDiscordant` with `SMOOTHED_DISCORDANT_TPR_THRESHOLD = 0.28` and extreme variance guard (`computedRmssd < 2.0`).
  2. Add unit test in `HrvSignalQualityAnalyzerTest.kt` verifying Polar H10 evening stream ($rMSSD = 4.5, DFA\ \alpha_1 = 1.52, TPR = 0.38$) returns `VALID`.
  3. Shorten `morning_hrv_btn_save` to single-word verb and update `morning_hrv_subtitle` across all 9 locales.
  4. Add dedicated `morning_hrv_result_title` (*"Messergebnis"* / *"Measurement Result"*) across all 9 locales and bind to `ReadinessResultCard`.
  5. Dynamically highlight *Verwerfen* (primary filled `Button`) when `recommendDiscard == true` in `ActionButtonsSection`.
  6. Use `.heightIn(min = 52.dp)` and `maxLines = 1, overflow = TextOverflow.Ellipsis` on action buttons.
* **Out-of-Scope Non-Goals**:
  * Modifying `DfaCalculator` or `RrArtifactFilter` DSP core logic.
  * Modifying SQLite schema or `HrvReadinessEntry` data structures.
  * Altering readiness SWC baseline scoring algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-TRK-067.3` & `REQ-TRK-068.3` in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Commit `5551c89e` (`ATT-6744`, Sprint 2026-46.3) and commit `fb6797a7` (`ATT-6745`, Sprint 2026-46.3).
* **Root Reason for Existing Formulation**: ATT-6744 assumed $rMSSD < 10\text{ ms}$ and $\alpha_1 > 1.35$ were mutually exclusive based on intense exercise regimes ($\alpha_1 \to 0.50$), overlooking evening/orthostatic baroreflex Mayer-wave drift.
* **Preservation of Core Invariants**: Retains full detection of moving-average smoothed sensors ($TPR < 0.20$ or $TPR < 0.28$ discordant) while eliminating false positives on true ECG sensors ($TPR \ge 0.28$).

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-067.3`, `REQ-TRK-067.5`, `REQ-TRK-068.3`, `REQ-TRK-068.5`.
* **Affected Architecture Elements**: `HrvSignalQualityAnalyzer.kt`, `MorningHrvCheckScreen.kt`, `strings_hrv.xml` (9 locales).
* **Verification Impact & Test Surface**: `HrvSignalQualityAnalyzerTest.kt`, `MorningHrvCheckViewModelTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low risk. DSP threshold changes are well-bounded by unit tests; 9-locale parity strictly maintained.

---

## 6. Architectural Strategy & High-Level Solution
1. **Algorithm (`HrvSignalQualityAnalyzer.kt`)**: Add `const val SMOOTHED_DISCORDANT_TPR_THRESHOLD = 0.28`. Refine `isDiscordant`: `computedRmssd < 10.0 && computedDfa > 1.35 && ((tpr != null && tpr < SMOOTHED_DISCORDANT_TPR_THRESHOLD) || computedRmssd < 2.0)`.
2. **Localization (`strings_hrv.xml` $\times 9$)**: Update `morning_hrv_subtitle`, shorten `morning_hrv_btn_save`, add `morning_hrv_result_title`.
3. **UI (`MorningHrvCheckScreen.kt`)**: Invert button prominence when `recommendDiscard == true`; apply `heightIn(min = 52.dp)` and text ellipsis.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: 100% detection of Magene/CooSpo smoothed streams; zero false positives on authentic ECG; 9-locale translation parity; zero SQLite schema alterations.
* **Risk Rating**: **LOW** (Targeted DSP refinement with explicit guardrail and Compose UI ergonomics polish).
