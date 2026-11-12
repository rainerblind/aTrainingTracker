# Stage 2: Requirement & Test Specification - ATT-7533: Fix Morning HRV false-positive smoothed sensor warning on Polar H10, highlight Discard button on warnings, and fix subtitle/button clipping

**Ticket**: [ATT-7533](https://rainerblind.atlassian.net/browse/ATT-7533)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-TRK-067`, `REQ-TRK-068` | **Test Spec ID**: `TST-TRK-067`, `TST-TRK-068`  
**Branch**: `feature/ATT-7533` | **Author**: AI Agent 2 (Flash) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-TRK-067, REQ-TRK-068)

### 1.1 Problem Statement & Rationale
Polar H10 authentic ECG data during evening/orthostatic low vagal states ($rMSSD = 4.5\text{ ms}$, $SDNN = 8.7\text{ ms}$) with baroreflex Mayer-wave drift ($DFA\ \alpha_1 = 1.52$) was falsely flagged as `SMOOTHED_STREAM` due to unconstrained discordance checking ($rMSSD < 10\text{ ms}$ and $\alpha_1 > 1.35$) despite normal turning-point sign reversals ($TPR = 0.38 \ge 0.28$). Furthermore, `MorningHrvCheckScreen` displayed an awkward German subtitle, duplicated *"Messung abgeschlossen"*, failed to visually highlight *Verwerfen* when discard is recommended, and clipped *"Messung speichern"* vertically due to fixed `52.dp` height.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. **Turning-Point Guard in Discordance Gate (`REQ-TRK-067.3`)**: `HrvSignalQualityAnalyzer.kt` SHALL define `SMOOTHED_DISCORDANT_TPR_THRESHOLD = 0.28`. `isDiscordant` SHALL flag `SMOOTHED_STREAM` only when $rMSSD < 10.0\text{ ms}$ AND DFA $\alpha_1 > 1.35$ AND accompanied by either suppressed turning points ($TPR < 0.28$) or extreme variance collapse ($rMSSD < 2.0\text{ ms}$).
2. **Preservation of Authentic Sinus Rhythm (`REQ-TRK-067.5`)**: Authentic resting/orthostatic ECG streams with baroreflex drift ($rMSSD < 10\text{ ms}$, $\alpha_1 > 1.35$, $TPR \ge 0.28$, e.g. Polar H10 with $rMSSD = 4.5\text{ ms}$, $\alpha_1 = 1.52$, $TPR = 0.38$) SHALL evaluate to `VALID`.
3. **Dynamic Action Button Prominence (`REQ-TRK-068.3`)**: In `MorningHrvCheckScreen.kt`, when discard is recommended (`recommendDiscard == true`, e.g. `highArtifactWarning` or status `SMOOTHED_STREAM`, `QUANTIZED_SYNTHETIC`, `HIGH_ARTIFACTS`) and measurement is unsaved: *Verwerfen* SHALL render as primary filled `Button` and *Speichern* as secondary `OutlinedButton`. When `recommendDiscard == false`, standard styling applies.
4. **Button Sizing & Anti-Clipping (`REQ-TRK-068.3`)**: Action buttons SHALL enforce `Modifier.heightIn(min = 52.dp)` and button text SHALL specify `maxLines = 1, overflow = TextOverflow.Ellipsis`.
5. **9-Language Localization Parity (`REQ-TRK-068.5`)**: `morning_hrv_subtitle` SHALL use natural resting guidance across 9 locales (DE: *"Ruhemessung direkt nach dem Aufwachen"*); `morning_hrv_btn_save` SHALL be a single-word verb (DE: *"Speichern"*); `morning_hrv_result_title` (DE: *"Messergebnis"*) SHALL be added to `ReadinessResultCard`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Polar H10 Valid Stream)**: *Given* an authentic stream ($rMSSD = 4.5$, $\alpha_1 = 1.52$, $TPR = 0.38$), *When* evaluated by `HrvSignalQualityAnalyzer.analyze(...)`, *Then* `status` is `VALID`.
* **AC-2 (Smoothed Stream Maintained)**: *Given* a Magene-style stream ($rMSSD = 5.5$, $\alpha_1 = 1.45$, $TPR < 0.28$), *When* evaluated, *Then* `status` is `SMOOTHED_STREAM`.
* **AC-3 (Action Button Prominence Inversion)**: *Given* `MorningHrvCheckScreen` with `recommendDiscard == true`, *When* rendered, *Then* *Verwerfen* is primary filled `Button` and *Speichern* is `OutlinedButton`.
* **AC-4 (Natural Copy & Anti-Clipping)**: *Given* German locale on `MorningHrvCheckScreen`, *When* viewing top bar and completed card, *Then* subtitle is *"Ruhemessung direkt nach dem Aufwachen"*, result card title is *"Messergebnis"*, and *Speichern* fits single-line without vertical clipping.

### 1.5 System Invariants
* Core Invariants: 100% detection of Magene/CooSpo smoothed streams; zero DB schema modifications; 100% 9-language string parity.

### 1.6. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-067.3`, `REQ-TRK-068.3`, `REQ-TRK-068.5` (Discordance gate and morning HRV presentation).
* **Historical Origin & Commit Trace**: Commits `5551c89e` (`ATT-6744`) and `fb6797a7` (`ATT-6745`).
* **Root Reason for Existing Formulation**: ATT-6744/ATT-6745 assumed $rMSSD < 10\text{ ms}$ and $\alpha_1 > 1.35$ were mutually exclusive based on high-intensity exercise regimes ($\alpha_1 \to 0.50$), overlooking evening/orthostatic baroreflex Mayer-wave drift where $\alpha_1$ legitimately exceeds $1.35$ while $TPR \ge 0.28$. Fixed `52.dp` height and literal German translations caused vertical button text clipping.
* **Preservation of Core Invariants**: Firmware-smoothed series ($TPR < 0.20$ or discordant $TPR < 0.28$) remain 100% detected; authentic resting sinus rhythm is preserved without false alerts.

---

## 2. Test Specification (TST-TRK-067, TST-TRK-068)

* **TST-TRK-067.1 / TST-TRK-067.3 (Unit)**: `testAuthenticPolarH10RestingStreamPreservedAsValid` in `HrvSignalQualityAnalyzerTest.kt`. Verify series with $rMSSD = 4.5\text{ ms}$, $\alpha_1 = 1.52$, $TPR = 0.38$ returns `VALID`. Verify empirical smoothed series with $TPR < 0.28$ returns `SMOOTHED_STREAM`.
* **TST-TRK-068.3 (UI / Unit)**: `MorningHrvCheckViewModelTest.kt` / `MorningHrvCheckScreenTest.kt`. Verify `recommendDiscard` flag triggers when artifact warning or smoothed/synthetic signal status is set, asserting primary button slot renders Discard and secondary renders Save.
* **TST-TRK-068.5 (Localization)**: `TranslationParityTest.kt`. Run test suite asserting 100% key and token parity across all 9 locales for `morning_hrv_subtitle`, `morning_hrv_btn_save`, and `morning_hrv_result_title`.
* **TST-TRK-067.7 / TST-TRK-068.6 (Tier 1 Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"` asserting 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-067.3` | Unit | `HrvSignalQualityAnalyzer.analyze` | `REQ-TRK-067.3`, `REQ-TRK-067.5` | Specified |
| `TST-TRK-067.1` | Unit | `HrvSignalQualityAnalyzer.analyze` | `REQ-TRK-067.3` | Specified |
| `TST-TRK-068.3` | UI / Unit | `ActionButtonsSection` & `MorningHrvUiState` | `REQ-TRK-068.3` | Specified |
| `TST-TRK-068.5` | Localization | `TranslationParityTest` | `REQ-TRK-068.5` | Specified |
| `TST-TRK-067.7` | Regression | HRV device & UI unit tests | `REQ-PRO-001` | Specified |
