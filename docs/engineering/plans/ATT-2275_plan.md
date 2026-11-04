# Stage 3: Implementation Plan - ATT-2275: Create Comprehensive Online User Guide on GitHub Pages

**Ticket**: [ATT-2275](https://rainerblind.atlassian.net/browse/ATT-2275)  
**Sub-task**: [ATT-5328](https://rainerblind.atlassian.net/browse/ATT-5328) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-PRO-033`  
**Test Mapping**: `TST-PRO-033`  
**Branch**: `feature/ATT-2275`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Description & Background
Athletes using aTrainingTracker need a comprehensive, searchable user guide covering features from quick start to Skiba $W'_{\text{bal}}$ analytics and local FIT export, published via GitHub Pages.

## 2. Traceability & Requirements Mapping
- **Requirement**: `REQ-PRO-033` (*Comprehensive Online User Guide on GitHub Pages*)
- **Test Mapping**: `TST-PRO-033` (*Online User Guide Content, Exclusion & Asset Contract*)

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Regressions**: No changes to Android APK production classes or manifest permissions.
2. **Developer Hygiene**: Internal developer artifacts in `docs/` remain in git but are hidden from GitHub Pages.
3. **Subtask & Gate Lifecycle**: Strict ASPICE progression with independent gate audit.

## 4. Proposed Architectural Changes
- **Jekyll Site Config**: Update `docs/_config.yml` with exclusions for `ci-secrets.md`, `project_protocol.md`, `engineering/`, `requirements/`, and `tests/`.
- **Landing Page & Chapters**: Author `docs/index.md` and 18 chapters under `docs/guide/de/` and `docs/guide/en/`.
- **Asset Directory**: Populate `docs/guide/img/` with curated UI screenshots.
- **Contract Verification**: Implement `OnlineUserGuideContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/`.

### UI Consistency (Rule 23)
- No Android app UI changes. Static documentation and automated JUnit 4 build verification test only.

## 5. Step-by-Step Implementation Sequence
- **Step 1**: Curate high-resolution screenshots into `docs/guide/img/`.
- **Step 2**: Configure `docs/_config.yml` and author landing page `docs/index.md`.
- **Step 3**: Author 9 German chapters (`docs/guide/de/`) and 9 English chapters (`docs/guide/en/`) with matching anchors.
- **Step 4**: Implement `OnlineUserGuideContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/`.
- **Step 5**: Update living requirements in `req_process_stability.md`, `tst_process_stability.md`, `requirements.md`, and `tests.md`.
- **Step 6**: Run targeted test suite: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` and `python3 tools/verify_requirement_governance.py`.

## 6. Verification & Rollback Plan
- **Verification**: Run `OnlineUserGuideContractTest` verifying all 18 chapters, exclusions, images, and anchors.
- **Rollback**: Clean git branch revert without affecting `sprint/2026-45.4`.
