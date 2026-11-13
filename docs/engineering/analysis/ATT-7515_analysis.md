# Stage 1 Analysis: ATT-7515 - Discrete Borg CR10 Slider, Canonical Verbal Descriptors & Refined Feel Vector Cards

**Ticket**: [ATT-7515](https://rainerblind.atlassian.net/browse/ATT-7515)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7515` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
In `WorkoutRpeFeelSection.kt`, the initial prototype from ATT-6879 presents perceived exertion (RPE 1–10) as fixed-width 36dp pill buttons spanning 414 dp inside a horizontal scroll, clipping button 9 in half and hiding button 10 offscreen on standard portrait phones (360–412dp). Verbal anchors use colloquial phrasing ("Moderat", "Anstrengend") rather than canonical Borg CR10 scientific definitions ("Mäßig", "Schwer", "Extrem schwer", "Nahezu maximal"). Furthermore, subjective Feel (1–5) relies on raw unicode emojis without Material 3 vector iconography or refined descriptor wording.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (RPE Row Clipping & Non-Standard Wording)**: `WorkoutRpeFeelSection.kt:234-269` hardcodes `.size(36.dp)` and `Arrangement.spacedBy(6.dp)` on a `Row` with `.horizontalScroll(...)`. `strings_rpe_feel.xml` uses colloquial translations lacking sports-scientific Borg CR10 alignment.
* **RCA 2 (Unicode Emojis for Feel)**: `WorkoutRpeFeelSection.kt:79-86` returns emoji glyphs (`"😫"`, `"😕"`, `"😐"`, `"🙂"`, `"🤩"`), lacking visual harmony with Material 3 vector design and Android system typography.
* **Independent Peer Review & Verification**:
  * Verified `WorkoutRpeFeelSection.kt#L234-269`: Confirmed fixed 36dp buttons, horizontal scroll, and clipping.
  * Verified `WorkoutRpeFeelSection.kt#L79-86`: Confirmed raw emoji strings.
  * Verified `strings_rpe_feel.xml`: Confirmed colloquial German/English phrasing.
  * Verified Scope Bounding: Changes strictly confined to `WorkoutRpeFeelSection.kt`, `strings_rpe_feel.xml` (9 locales), and `WorkoutRpeFeelTest.kt`. Zero changes to `EditWorkoutScreen.kt` classification tiles or database schemas.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Upgrade RPE editor in `WorkoutRpeFeelSection.kt` with an interactive discrete Material 3 `Slider` (steps 1–10, `steps = 8`) and edge-to-edge non-clipping step buttons (`weight(1f)`), paired with a clear button when set.
  2. Align Borg CR10 verbal descriptors across all 9 locales to canonical scientific definitions (DE: *1: Sehr leicht, 2: Leicht, 3: Mäßig, 4: Etwas schwer, 5: Schwer, 6: Schwer bis sehr schwer, 7: Sehr schwer, 8: Extrem schwer, 9: Nahezu maximal, 10: Maximal*).
  3. Upgrade Feel (1–5) editor with Material 3 tonal cards and vector sentiment icons (`Icons.Filled.Sentiment*`) with aligned descriptors (DE: *1: Miserabel / Sehr schwach, 2: Schwerfällig / Mäßig, 3: Normal / Okay, 4: Gut / Stark, 5: Großartig / Im Flow*).
  4. Maintain 100% 9-language localization parity in `strings_rpe_feel.xml` without emojis.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to `EditWorkoutScreen.kt` classification tiles (`ATT-7517` already merged).
  * No changes to `WorkoutData` DTO schema or SQLite database tables (`DB_VERSION = 30` preserved).
  * No modifications to Garmin FIT codec (`FitFileWriter` / `FitWorkoutParser`) or Foster sRPE calculation.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-WKO-037` (*EditWorkoutScreen RPE & Feel UI Placement*) and `REQ-WKO-038` (*RPE & Feel Presentation & Descriptors*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-6879` (Sprint 2026-46.3, commit `2c999318`).
* **Root Reason for Existing Formulation**: Rapid initial prototype with static button widths and unicode emojis before ergonomics and scientific Borg wording were refined during PO review.
* **Preservation of Core Invariants**: `rpe` (1..10) and `feel` (1..5) nullability and value domains, Foster sRPE calculation (`PowerPhysiologyCalculator.calculateSessionRpe`), and Garmin FIT export/import fields 192/193 are 100% preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-WKO-037`, `REQ-WKO-038`.
* **Affected Architecture Elements**: `WorkoutRpeFeelSection.kt`, `res/values*/strings_rpe_feel.xml` (9 locales).
* **Verification Impact & Test Surface**: `WorkoutRpeFeelTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Isolated UI presentation and localization refinement in self-contained composable.

---

## 6. Architectural Strategy & High-Level Solution
1. **Interactive Discrete Slider & Buttons**: Material 3 `Slider` (1f..10f, `steps = 8`, thumb/track tinted via `TTColor.rpeColor`) accompanied by equal-width buttons (`weight(1f)`) and Clear action (`IconButton(Icons.Default.Clear)`).
2. **Refined Feel Presentation**: 5 equal-width M3 tonal cards (`weight(1f)`) using `Icons.Filled.Sentiment*` vectors and clearable on re-tap.
3. **Canonical Borg CR10 & Feel Localization**: Synchronize all 9 `strings_rpe_feel.xml` files.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Nullable `1..10` and `1..5` integer range preserved; sRPE calculation unchanged; 100% 9-language parity.
* **Risk Rating**: **LOW** (Targeted Compose UI ergonomics polish and localized string harmonization).
