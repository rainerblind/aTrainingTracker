# Stage 3: Implementation Plan - ATT-6290: SupporterScreen PioneerStatusBanner Neutral Copy

**Ticket**: [ATT-6290](https://rainerblind.atlassian.net/browse/ATT-6290)  
**Sub-task**: [ATT-6329](https://rainerblind.atlassian.net/browse/ATT-6329) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1`  
**Requirement Mapping**: `REQ-UI-502`  
**Test Mapping**: `TST-UI-502`  
**Branch**: `feature/ATT-6290`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-09  

---

## 1. Problem Description & Background

In `SupporterComponents.kt` (`PioneerStatusBanner`), `R.string.pioneer_thank_you_body` specifically references "the early ANT+ days" across all 9 supported locales. This alienates early adopters who tracked workouts prior to V5.0.0 using Bluetooth LE, GPS, or internal phone sensors. The copy will be updated to express neutral, appreciative gratitude for tracking workouts with an earlier version of `aTrainingTracker`, completely removing "ANT+" across all 9 locales without decorative emojis or format arguments.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-502` (*SupporterScreen: Neutral Pioneer Early Adopter Recognition Copy*)
* **Test Mapping**: `TST-UI-502` (*SupporterScreen PioneerStatusBanner Neutral Copy Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Pioneer Invariants**: Lifetime Pro privileges (`isProActive() == true`), unlimited device pairing, dynamic `pioneerSinceYear` formatting, and 36dp vector emblem remain 100% preserved.
2. **Localization Invariants**: 100% translation parity across all 9 locales without format specifiers (`%s`, `%d`) and zero decorative emojis (`ATT-5400`).
3. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

* **Resource Layer**: Update `pioneer_thank_you_body` across all 9 `res/values*/strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Test Layer**: Extend `SupporterCopyContractTest.kt` with a contract test verifying zero "ANT+" occurrences, zero emojis, zero format arguments, and exact DE/EN match.
* **UI Consistency (Rule 23)**:
  * **Reference screen / component**: `PioneerStatusBanner` in `SupporterComponents.kt` (lines 69–109).
  * **Reused components**: Standard `Card` with `primaryContainer` background, `RoundedCornerShape(12.dp)`, `36.dp` emblem, and `onPrimaryContainer` typography.
  * **Theme tokens**: Preserves existing Material 3 color roles and typography without new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Localized Strings in All 9 Locales (`REQ-UI-502.1`, `REQ-UI-502.3`)
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales).
* **Changes**: Update `pioneer_thank_you_body` with the neutral appreciation text specified in `REQ-UI-502.1` / `docs/engineering/test_specs/ATT-6290_test_spec.md`.

### Step 2: Contract Test Verification Suite (`TST-UI-502.1`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterCopyContractTest.kt`.
* **Changes**: Add `testPioneerThankYouBodyEliminatesAntPlusAndMatchesExactCopy()` asserting 9-locale presence, zero "ANT+", zero emojis (`ATT-5400`), zero format specifiers, and exact DE/EN copy matching.

### Step 3: Targeted Unit & Governance Test Execution (`TST-UI-502.2`, `TST-UI-502.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Command**: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit test regression and translation parity suite in Stage 4; Tier 1 clean-room verification in Stage 5.
* **Rollback**: All modifications are isolated to `feature/ATT-6290` branch; clean git revert possible without impacting develop.
