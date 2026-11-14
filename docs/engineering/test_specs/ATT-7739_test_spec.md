# Stage 2: Requirement & Test Specification - ATT-7739: Morning HRV Instructions Desc Clean Phrasing

**Ticket**: [ATT-7739](https://rainerblind.atlassian.net/browse/ATT-7739)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-PER-032` / `REQ-PER-032.6`  
**Test Spec ID**: `TST-PER-040` / `TST-PER-040.6`  
**Branch**: `feat/ATT-7739` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-PER-032.6 Refinement)

### 1.1 Problem Statement & Rationale
Clinical protocol phrasing in `morning_hrv_instructions_desc` ("nach dem Entleeren der Blase" / "after bladder voiding") is unnecessarily crude and indelicate for consumer athletic software. Athletes require clear, rest-focused guidance upon waking without clinical bladder emptying references across all 9 supported locales.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional / Localization | **Verification Method**: Unit Test & Review

### 1.3 Functional & Architectural Requirements
1. In `strings_hrv.xml` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), `morning_hrv_instructions_desc` SHALL instruct the athlete to measure at rest upon waking, remaining still and breathing normally without speaking, maintaining posture consistency for reliable SWC baseline comparisons.
2. The instruction string SHALL NOT contain bladder emptying or voiding terminology across any locale.
3. In `TranslationParityTest.kt`, a dedicated contract test assertion SHALL verify that `morning_hrv_instructions_desc` exists in all 9 locales, is localized, contains expected rest phrasing, and contains zero forbidden bladder voiding terms.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Exclusion of Bladder Voiding Terms)**:
  * *Given* `morning_hrv_instructions_desc` parsed across all 9 locales,
  * *When* evaluated by `TranslationParityTest`,
  * *Then* none of the strings contain `"Blase"`, `"bladder"`, `"vejiga"`, `"vessie"`, `"vescica"`, `"排尿"`, `"blaas"`, `"pęcherz"`, or `"bexiga"`.
* **Criterion 2 (Inclusion of Rest Guidance)**:
  * *Given* `morning_hrv_instructions_desc` parsed across all 9 locales,
  * *When* evaluated by `TranslationParityTest`,
  * *Then* each string contains the appropriate localized rest phrasing (`"Ruhe"`, `"at rest"`, `"en reposo"`, `"au repos"`, `"a riposo"`, `"安静"`, `"in rust"`, `"w spoczynku"`, `"em repouso"`).
* **Criterion 3 (100% 9-Language Localization & AAPT2 Parity)**:
  * *Given* all 9 `strings_hrv.xml` resource files,
  * *When* tested with `TranslationParityTest` and `processDebugResources`,
  * *Then* 100% key parity and specifier parity are maintained with zero AAPT2 compilation warnings.

### 1.5 System Invariants
- Zero Kotlin code logic or Compose layout modifications; zero database changes.

---

## 2. Test Specification (TST-PER-040.6)

### Test Case 1: `testMorningHrvInstructionsDesc_noBladderPhrasingAcrossAllLocales` (`TST-PER-040.6a`)
* **Scope**: Unit Test (`TranslationParityTest.kt`)
* **Assertions**:
  - Assert `morning_hrv_instructions_desc` is present in base (`values`) and all 8 non-default locales.
  - Assert no locale contains forbidden bladder terms: `listOf("blase", "bladder", "vejiga", "vessie", "vescica", "排尿", "blaas", "pęcherz", "bexiga")`.
  - Assert each locale contains corresponding positive rest term.

### Test Case 2: 9-Language Parity Suite (`TST-PER-040.6b`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

### Test Case 3: Fast AAPT2 Resource Compilation (`TST-PER-040.6c`)
* **Command**: `./gradlew processDebugResources`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-040.6a]` | Unit | `TranslationParityTest.testMorningHrvInstructionsDesc...` | `REQ-PER-032.6` | Verified |
| `[TST-PER-040.6b]` | Tier 1 Parity | `TranslationParityTest` full suite | `REQ-PER-032.6` | Verified |
| `[TST-PER-040.6c]` | Fast AAPT2 | `processDebugResources` | `REQ-PER-032.6` | Verified |
