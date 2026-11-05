# Stage 2: Requirement & Test Specification - ATT-5418: Online User Guide Layout & Accuracy

**Ticket**: [[ATT-5418]](https://rainerblind.atlassian.net/browse/ATT-5418)  
**Sub-task**: [[ATT-5463]](https://rainerblind.atlassian.net/browse/ATT-5463) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-2273]](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5418`  
**Requirement Mapping**: `REQ-PRO-033` (*Comprehensive Online User Guide on GitHub Pages*)  
**Test Spec ID**: `TST-PRO-033` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-05  

---

## 1. Requirement Specification (`REQ-PRO-033.5`)

### 1.1 Problem Statement & Rationale
The online manual generated in `ATT-2275` suffered from full-width 9:20 screenshots tearing layout flow, 12 invalid/misplaced image assets, formal German "Sie" tone, and hallucinated features.

### 1.2 Functional & Architectural Requirements
- **REQ-1 (2-Column Layout)**: All chapters SHALL render via `<table>` with `<td width="65%" valign="top" markdown="1">` (text) and `<td width="35%" valign="top" align="center">` holding clickable screenshot thumbnails constrained to `width="240"` (`<a href="../img/..."><img src="../img/..." width="240" .../></a><br/><sub><em>...</em></sub>`).
- **REQ-2 (Du-Anrede)**: The German manual (`docs/guide/de/`) SHALL consistently use personal "Du"-Anrede.
- **REQ-3 (Visual Authenticity)**: All 12 defective screenshot assets SHALL be replaced with authentic production screens (0% developer boards, 0% private icons, 0% status bar overlaps, 0% scrolled-off grids).
- **REQ-4 (Factual Precision)**: Non-existent features (e.g. `ACTIVITY_RECOGNITION`, `1x/2x/3x` tile widths, TTS cues, OSM offline maps, bike tare weight, TrainingPeaks) SHALL be replaced with genuine app features.
- **REQ-5 (Contract Test)**: `OnlineUserGuideContractTest.kt` SHALL support both Markdown and HTML `<img>` tag asset resolution.

### 1.3 Acceptance Criteria (Given-When-Then)
- **AC-1 (2-Column Layout & Tone)**: *Given* chapters 1–9 in DE and EN, *When* rendered on GitHub Pages/Markdown viewer, *Then* side-by-side table layout with `width="240"` thumbnails is applied and DE uses "Du".
- **AC-2 (100% Asset Authenticity)**: *Given* `docs/guide/img/`, *When* inspected, *Then* all images match described features without developer boards or private data.
- **AC-3 (Factual Parity)**: *Given* guide chapters, *When* comparing text to app codebase, *Then* 100% of described features exist.
- **AC-4 (Contract Test Suite)**: *Given* `OnlineUserGuideContractTest`, *When* executed via Gradle, *Then* all checks pass.

### 1.4 System Invariants & Requirement Archaeology
- **Invariants**: 18-file chapter parity, 1:1 anchor parity (`<a id="...">`), zero runtime APK code changes.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-033` under Epic `ATT-2273` (*User Documentation & Video Guides*). Targets `docs/guide/`, `docs/index.md`, and `app/src/test/java/com/atrainingtracker/trainingtracker/build/OnlineUserGuideContractTest.kt`.
2. *Historical Origin & Commit Trace*: Created in `ATT-2275` (commit `1fbb551625`), amended in `ATT-5418` (Sprint 2026-45.6).
3. *Root Reason for Existing Formulation*: Initial generation created full-width portrait screenshots (~1,400 px height), mismatched image assets, formal "Sie" tone, and hallucinated feature descriptions needing systematic structural and factual rectification.
4. *Preservation of Core Invariants*: Bilingual chapter parity (18 files), anchor parity (`<a id="...">`) for in-app deep-linking, Jekyll exclusion hygiene, and zero runtime app regression remain 100% preserved.

---

## 2. Test Specification (`TST-PRO-033`)

### Test Case 1: `OnlineUserGuideContractTest` Image Asset Resolution (`TST-PRO-033.3` / `TST-PRO-033.5`)
- **Scope**: Unit Test | **Target**: `OnlineUserGuideContractTest.kt`
- **Procedure**: Execute `testImageReferences_resolveToExistingAssets` with HTML `<img>` and Markdown `![]()` support.
- **Expected Result**: $\ge 20$ verified references, 100% resolve to existing non-empty assets under `docs/guide/img/`.

### Test Case 2: Bilingual Anchor Parity (`TST-PRO-033.4`)
- **Scope**: Unit Test | **Target**: `OnlineUserGuideContractTest.kt`
- **Procedure**: Execute `testAnchorParity_betweenGermanAndEnglishChapters`.
- **Expected Result**: 100% identical `<a id="...">` anchors across corresponding DE and EN chapters.

### Test Case 3: Jekyll Exclusion & Chapter Parity (`TST-PRO-033.1`, `TST-PRO-033.2`)
- **Scope**: Unit Test | **Target**: `OnlineUserGuideContractTest.kt`
- **Procedure**: Execute `testJekyllConfig_excludesInternalDeveloperDocs` and `testChapterFiles_existForBothLocales`.
- **Expected Result**: 18 chapter files present with non-empty content; developer directories excluded in `_config.yml`.

---

## 3. Traceability Matrix

| Test ID | Scope | Target / Method Under Test | Requirement ID | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-033.1` | Unit | `testJekyllConfig_excludesInternalDeveloperDocs` | `REQ-PRO-033.2` | Planned |
| `TST-PRO-033.2` | Unit | `testChapterFiles_existForBothLocales` | `REQ-PRO-033.1` | Planned |
| `TST-PRO-033.3` | Unit | `testImageReferences_resolveToExistingAssets` | `REQ-PRO-033.1`, `REQ-PRO-033.5` | Planned |
| `TST-PRO-033.4` | Unit | `testAnchorParity_betweenGermanAndEnglishChapters` | `REQ-PRO-033.3` | Planned |
| `TST-PRO-033.5` | Unit | `testImageReferences_resolveToExistingAssets` (HTML tags) | `REQ-PRO-033.5` | Planned |
