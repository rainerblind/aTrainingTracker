# Stage 3: Implementation Plan - ATT-6680: SupporterScreen (PioneerStatusBanner): Add transition sentence inviting voluntary financial support below the Pioneer unlock notice

**Ticket**: [ATT-6680](https://rainerblind.atlassian.net/browse/ATT-6680)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-516` (`REQ-UI-516.1`..`REQ-UI-516.4`)  
**Test Mapping**: `TST-UI-516` (`TST-UI-516.1`..`TST-UI-516.4`)  
**Branch**: `feature/ATT-6680`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
In `ATT-6290`, `pioneer_thank_you_body` on `PioneerStatusBanner` (`SupporterScreen.kt`) was updated to confirm permanent feature unlock for Pioneer users. Because the Supporter subscription cards appear directly below this banner, a natural transition sentence is needed explaining that voluntary financial support for ongoing development remains welcome. This plan outlines updating string resources across all 9 locales and aligning contract tests in `SupporterCopyContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-516` (*Pioneer Status Banner Voluntary Support Transition Copy Parity*)
* **Test Mapping**: `TST-UI-516` (*Presence, Hygiene, and Exact German/English Copy Assertions*)

---

## 3. System Invariants & Preserved Behavior
1. **Unconditional Feature Unlock**: Pioneer status permanently unlocks unlimited sensors and all future features without requiring payments.
2. **Hygiene Standards**: Zero format specifiers (`%s`, `%d`), zero inline decorative emojis (`ATT-5400`), zero "ANT+" references, and zero marketing buzzwords across all 9 locales.
3. **UI Consistency**: Existing `SupporterScreen.kt` and `SupporterComponents.kt` Compose layouts, card styling, and Material 3 theme tokens remain unchanged.

---

## 4. Proposed Architectural Changes
* **Localization Resources (`app/src/main/res/values*/strings.xml`)**:
  - Append the appreciative voluntary support transition sentence to `pioneer_thank_you_body` across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Contract Test Suite (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterCopyContractTest.kt`)**:
  - Update `testPioneerThankYouBodyEliminatesAntPlusAndMatchesExactCopy` to verify the expanded German and English strings.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: 9-Language Localization Update (`values*/strings.xml`)**
   - Update `pioneer_thank_you_body` using reference copy from `ATT-6680_test_spec.md` (§3) across all 9 locales.
2. **Step 2: Update Contract Test Assertions (`SupporterCopyContractTest.kt`)**
   - Align exact copy assertions for `de` and base (`values/`) locales.
3. **Step 3: Verification Execution (`SupporterCopyContractTest.kt` & `TranslationParityTest.kt`)**
   - Run targeted unit tests under `/tmp/att_build_merge.lock` via `python3 tools/parallel_sprint_lock.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.SupporterCopyContractTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
* **Rollback**: Discard uncommitted changes on `feature/ATT-6680` via `git restore . && git clean -fd`.
