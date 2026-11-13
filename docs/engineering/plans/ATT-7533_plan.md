# Stage 3: Implementation Plan - ATT-7533: Polar H10 False-Positive Smoothed Warning Fix & UI Ergonomics

**Ticket**: [ATT-7533](https://rainerblind.atlassian.net/browse/ATT-7533)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-TRK-067.3`, `REQ-TRK-067.5`, `REQ-TRK-068.3`, `REQ-TRK-068.5`  
**Test Mapping**: `TST-TRK-067.1`, `TST-TRK-067.3`, `TST-TRK-068.3`, `TST-TRK-068.5`  
**Branch**: `feature/ATT-7533` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Unconstrained discordance checking in `HrvSignalQualityAnalyzer` falsely flagged authentic Polar H10 ECG streams during evening/orthostatic low vagal states ($rMSSD = 4.5\text{ ms}$, $\alpha_1 = 1.52$) as `SMOOTHED_STREAM` despite normal turning-point ratios ($TPR = 0.38 \ge 0.28$). In `MorningHrvCheckScreen`, the German subtitle was literally translated, *"Messung abgeschlossen"* was duplicated in the result card header, *Verwerfen* was unhighlighted when discard was recommended, and fixed `52.dp` height sliced *"Messung speichern"* vertically.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-067.3 / REQ-TRK-067.5**: Gated discordance via `SMOOTHED_DISCORDANT_TPR_THRESHOLD = 0.28` and $rMSSD < 2.0\text{ ms}$; preservation of authentic sinus rhythm $\rightarrow$ `TST-TRK-067.1`, `TST-TRK-067.3`.
* **REQ-TRK-068.3**: Dynamic action button prominence inversion (*Verwerfen* primary filled `Button`, *Speichern* secondary `OutlinedButton` on `recommendDiscard`) & `heightIn(min = 52.dp)` anti-clipping $\rightarrow$ `TST-TRK-068.3`.
* **REQ-TRK-068.5**: 9-language localization parity (`morning_hrv_subtitle`, `morning_hrv_btn_save`, `morning_hrv_result_title`) $\rightarrow$ `TST-TRK-068.5`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Smoothing Detection Degradation**: Firmware-smoothed sensors (Magene H64/H303 with $TPR < 0.20$ or $TPR < 0.28$) remain 100% detected.
2. **Zero Schema Alteration**: SQLite tables, `HrvReadinessEntry`, and baseline SWC math remain strictly invariant.
3. **9-Language Parity**: 100% string key and token parity across all 9 canonical locales.
4. **Theme & Typography Parity**: Centralized `TTColor` tokens, M3 `heightIn(min = 52.dp)`, and single-line ellipsis.

---

## 4. Proposed Architectural Changes

### Component 1: Algorithm Layer (`HrvSignalQualityAnalyzer.kt`)
* Introduce `const val SMOOTHED_DISCORDANT_TPR_THRESHOLD = 0.28` and `const val SMOOTHED_DISCORDANT_MIN_RMSSD_MS = 2.0`.
* Refine `isDiscordant`: `computedRmssd < SMOOTHED_RMSSD_THRESHOLD_MS && computedDfa > SMOOTHED_DFA_A1_THRESHOLD && ((tpr != null && tpr < SMOOTHED_DISCORDANT_TPR_THRESHOLD) || computedRmssd < SMOOTHED_DISCORDANT_MIN_RMSSD_MS)`.

### Component 2: Presentation Layer (`MorningHrvCheckScreen.kt`)
* In `ActionButtonsSection`: evaluate `val recommendDiscard = uiState.highArtifactWarning || uiState.signalQualityStatus in setOf(SMOOTHED_STREAM, QUANTIZED_SYNTHETIC, HIGH_ARTIFACTS)`.
* When `recommendDiscard && !uiState.isSaved`: render *Verwerfen* as filled `Button` and *Speichern* as `OutlinedButton`.
* Enforce `Modifier.heightIn(min = 52.dp)` and `maxLines = 1, overflow = TextOverflow.Ellipsis` on all action buttons.
* In `ReadinessResultCard`: replace `R.string.morning_hrv_state_completed` with `R.string.morning_hrv_result_title`.

### Component 3: Localization (`strings_hrv.xml` across all 9 locales)
* `morning_hrv_subtitle`: natural resting guidance (DE: *"Ruhemessung direkt nach dem Aufwachen"*, EN: *"Resting measurement right after waking"*).
* `morning_hrv_btn_save`: concise single-word verb (DE: *"Speichern"*, EN: *"Save"*, FR: *"Enregistrer"*, etc.).
* `morning_hrv_result_title`: dedicated result header (DE: *"Messergebnis"*, EN: *"Measurement Result"*, etc.).

### UI Consistency (Rule 23 / `docs/design_guidelines.md` §1 & §5)
* **Reference Component**: `MorningHrvCheckScreen.kt` (`ActionButtonsSection`, `ReadinessResultCard`).
* **Reused Components**: Material 3 `Button`, `OutlinedButton`, `Card`, `MaterialTheme.typography.titleMedium`.
* **Theme Tokens**: `RoundedCornerShape(12.dp)`, `TTColor.ReadinessOptimal`, `MaterialTheme.colorScheme.surface`.
* **Side-by-Side Action Buttons**: 1-word labels fitting single-line on 360–412dp viewports, filled `Button` for recommended action.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Algorithm Refinement (`HrvSignalQualityAnalyzer.kt`)
* Add `SMOOTHED_DISCORDANT_TPR_THRESHOLD = 0.28` and `SMOOTHED_DISCORDANT_MIN_RMSSD_MS = 2.0`.
* Refine `isDiscordant` condition with null-safe reason formatting.

### Step 2: 9-Locale String Parity (`strings_hrv.xml`)
* Update `morning_hrv_subtitle` and `morning_hrv_btn_save`; add `morning_hrv_result_title` across all 9 `strings_hrv.xml` files.

### Step 3: UI Ergonomics & Button Inversion (`MorningHrvCheckScreen.kt`)
* Wire `morning_hrv_result_title` into `ReadinessResultCard`; implement dynamic prominence & `heightIn(min = 52.dp)` in `ActionButtonsSection`.

### Step 4: Verification Unit Tests (`HrvSignalQualityAnalyzerTest.kt`, `MorningHrvCheckViewModelTest.kt`)
* Add `testAuthenticPolarH10RestingStreamPreservedAsValid` ($rMSSD = 4.5, DFA\ \alpha_1 = 1.52, TPR = 0.38 \to VALID$) in `HrvSignalQualityAnalyzerTest.kt`.
* Add discard recommendation prominence assertions in `MorningHrvCheckViewModelTest.kt`.
* Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityAnalyzerTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckViewModelTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Zero failures across DSP, UI ViewModel, and translation parity unit test suites.
* **Rollback**: Clean revert via `git reset --hard feature/ATT-7533` before branch merge.
