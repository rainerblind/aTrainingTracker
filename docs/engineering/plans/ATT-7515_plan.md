# Stage 3: Implementation Plan - ATT-7515: Discrete Borg CR10 Slider, Canonical Wording & Refined Feel Vector Cards

**Ticket**: [ATT-7515](https://rainerblind.atlassian.net/browse/ATT-7515)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7515`  
**Requirement Mapping**: `REQ-WKO-037.1`–`REQ-WKO-037.2`, `REQ-WKO-038.1`–`REQ-WKO-038.2`  
**Test Mapping**: `TST-WKO-037.1`–`TST-WKO-037.2`, `TST-WKO-038.1`–`TST-WKO-038.2`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
In `WorkoutRpeFeelSection.kt`, the initial RPE/Feel prototype suffered from horizontal row clipping on 360–412dp devices (414 dp width slicing button 9 and hiding button 10), non-standard colloquial verbal anchors, and emoji glyphs for Feel. This ticket replaces/supplements RPE with an interactive discrete Material 3 `Slider` (steps 1–10) and equal-width buttons (`weight(1f)`), upgrades Feel with 5 equal-width tonal cards rendering vector sentiment icons, and harmonizes canonical scientific Borg CR10 and Feel descriptors across all 9 locales.

---

## 2. Traceability & Requirements Mapping
* **REQ-WKO-037.1**: Discrete Borg CR10 Slider (`1f..10f`, `steps = 8`) + edge-to-edge buttons (`weight(1f)`) + clear selection action $\rightarrow$ `TST-WKO-037.1`.
* **REQ-WKO-037.2**: Refined Feel (1–5) M3 tonal cards with `Icons.Filled.Sentiment*` vectors, re-tap toggleable $\rightarrow$ `TST-WKO-037.2`.
* **REQ-WKO-038.1**: Canonical sports-scientific Borg CR10 descriptors in `strings_rpe_feel.xml` across all 9 locales $\rightarrow$ `TST-WKO-038.1`.
* **REQ-WKO-038.2**: Aligned Feel descriptors across all 9 locales without emojis $\rightarrow$ `TST-WKO-038.2`.

---

## 3. System Invariants & Preserved Behavior
1. **Model & DB Schema Invariance**: `WorkoutData.rpe` (`Int?`, `1..10`), `WorkoutData.feel` (`Int?`, `1..5`), and `WorkoutSummaries` DB columns remain 100% unchanged.
2. **Physiology & FIT Parity**: Foster Session-RPE (`PowerPhysiologyCalculator.calculateSessionRpe`) and Garmin FIT codec (fields 192/193) unchanged.
3. **Display Badge Continuity**: `WorkoutRpeFeelSection` badge row in `WorkoutSummary` and `WorkoutDetailTabbedContent` preserved.
4. **9-Language Parity**: 100% string key and descriptor parity across all 9 canonical locales with zero emojis.

---

## 4. Proposed Architectural Changes

### Component 1: Presentation Layer (`WorkoutRpeFeelSection.kt`)
* Introduce `getFeelIcon(feel: Int): ImageVector` returning `Icons.Filled.Sentiment*` for 1..5 (`VeryDissatisfied`, `Dissatisfied`, `Neutral`, `Satisfied`, `VerySatisfied`). Retain `getFeelEmoji` for backward compatibility.
* In `RpeFeelEditorControls`:
  - RPE Section: Header with active descriptor readout and clear action (`IconButton(Icons.Default.Clear)`). Interactive discrete Material 3 `Slider` (`1f..10f`, `steps = 8`, tinted via `TTColor.rpeColor`). Row of 10 equal-width buttons (`Modifier.weight(1f)`, `Arrangement.spacedBy(4.dp)`, no horizontal scroll) for instant 1-tap rating.
  - Feel Section: Row of 5 equal-width tonal cards (`Modifier.weight(1f)`, `RoundedCornerShape(8.dp)`, min height `42.dp`), each displaying its sentiment vector icon and clearable via `onFeelChange(if (isSelected) null else f)`.

### Component 2: Localization (`res/values*/strings_rpe_feel.xml` across all 9 locales)
* Update `rpe_anchor_1..10` to canonical Borg CR10 scientific descriptors across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* Update `feel_anchor_1..5` descriptors across all 9 locales without emojis.

### UI Consistency (`docs/design_guidelines.md` §1 & §5)
* **Reference Component**: `WorkoutRpeFeelSection.kt`, `TuningFormControls.kt` (Slider styling).
* **Reused Tokens**: `RoundedCornerShape(8.dp)`, `TTColor.rpeColor(score)`, M3 theme colors (`primaryContainer`, `surfaceVariant`, `outlineVariant`). Full-surface touch targets $\ge 42\text{ dp}$.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Locale String Parity (`res/values*/strings_rpe_feel.xml`)
* Update canonical Borg CR10 and Feel descriptors across all 9 locales (`TST-WKO-038.1`, `TST-WKO-038.2`).

### Step 2: Presentation Construction (`WorkoutRpeFeelSection.kt`)
* Add `getFeelIcon`, implement discrete `Slider` + non-clipping 1..10 button row + clear action for RPE, implement 5 M3 sentiment vector cards for Feel (`TST-WKO-037.1`, `TST-WKO-037.2`).

### Step 3: Test Suite Updates & Execution (`WorkoutRpeFeelTest.kt`, `TranslationParityTest.kt`)
* Update unit tests verifying slider range, non-clipping weights, sentiment vector mapping, and 9-locale string parity.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRpeFeelTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Zero failures in `WorkoutRpeFeelTest`, `TranslationParityTest`, and `verify_requirement_governance.py`.
* **Rollback**: Clean revert via `git reset --hard feature/ATT-7515` before branch merge.
