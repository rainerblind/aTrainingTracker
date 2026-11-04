# Stage 2: Requirement & Test Specification - ATT-2275: Create Comprehensive Online User Guide on GitHub Pages

**Ticket**: [ATT-2275](https://rainerblind.atlassian.net/browse/ATT-2275)  
**Sub-task**: [ATT-5327](https://rainerblind.atlassian.net/browse/ATT-5327) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-PRO-033` (*Comprehensive Online User Guide on GitHub Pages*)  
**Test Spec ID**: `TST-PRO-033` (*Online User Guide Content, Exclusion & Asset Contract*)  
**Branch**: `feature/ATT-2275`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-PRO-033)

### 1.1 Problem Statement & Rationale
Athletes need a clear, structured, and illustrated online manual for `aTrainingTracker` covering setup, dual-protocol sensor pairing, equipment assignment, live cockpit customization, maps/segments, route clustering, physiology metrics, and data export.

### 1.2 Functional & Architectural Requirements
1. The documentation SHALL provide 9 chapters in German (`docs/guide/de/`) and English (`docs/guide/en/`) with real UI screenshots.
2. `docs/_config.yml` SHALL exclude internal developer files (`ci-secrets.md`, `project_protocol.md`, `coding_standards.md`, `design_guidelines.md`, `engineering/`, `requirements/`, `tests/`).
3. `docs/index.md` SHALL serve as the bilingual mobile-responsive landing page.
4. Each chapter SHALL maintain matching `<a id="...">` anchors across German and English.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* the `docs/` repository tree, *When* Jekyll parses configuration, *Then* internal developer documentation is excluded from public navigation.
- *Given* an athlete visiting the guide in DE or EN, *When* reading chapters 1 to 9, *Then* all capabilities are explained with matching anchors and screenshots.
- *Given* `OnlineUserGuideContractTest.kt`, *When* executed via Gradle, *Then* all 18 chapters, exclusions, images, and anchor parity pass with 100% success.

### 1.4 System Invariants
- Zero modification to Android runtime tracking or database logic.
- Complete privacy preservation and data sovereignty.

---

## 2. Test Specification (TST-PRO-033)

### Test Case 1: `testJekyllConfig_excludesInternalDeveloperDocs` (`TST-PRO-033.1`)
- **Scope**: Build Contract Test
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/OnlineUserGuideContractTest.kt`
- **Expected Result**: `docs/_config.yml` contains exclusions for all internal developer paths.

### Test Case 2: `testChapterFiles_existForBothLocales` & `testLandingPage` (`TST-PRO-033.2`)
- **Scope**: Content Verification Test
- **Expected Result**: All 18 chapter files and index landing pages exist and contain valid non-empty markdown.

### Test Case 3: `testImageReferences_resolveToExistingAssets` (`TST-PRO-033.3`)
- **Scope**: Asset Resolution Test
- **Expected Result**: All markdown image links resolve to valid, non-empty PNG files under `docs/guide/img/`.

### Test Case 4: `testAnchorParity_betweenGermanAndEnglishChapters` (`TST-PRO-033.4`)
- **Scope**: Anchor Parity Test
- **Expected Result**: All `<a id="...">` anchor identifiers match 1:1 between DE and EN chapters.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-033.1]` | Build | `testJekyllConfig_excludesInternalDeveloperDocs` | `REQ-PRO-033` | Verified |
| `[TST-PRO-033.2]` | Build | `testChapterFiles_existForBothLocales` | `REQ-PRO-033` | Verified |
| `[TST-PRO-033.3]` | Build | `testImageReferences_resolveToExistingAssets` | `REQ-PRO-033` | Verified |
| `[TST-PRO-033.4]` | Build | `testAnchorParity_betweenGermanAndEnglishChapters` | `REQ-PRO-033` | Verified |
| `[TST-PRO-001]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Verified |
