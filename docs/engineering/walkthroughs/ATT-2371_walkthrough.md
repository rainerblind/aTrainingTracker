# Stage 5: Walkthrough & Verification - ATT-2371: Add prominent Google Play Store badge with optimized placement in README

**Ticket**: [ATT-2371](https://rainerblind.atlassian.net/browse/ATT-2371)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-STP-005`  
**Test Mapping**: `TST-STP-005`  
**Branch**: `feature/ATT-2371`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-04  

---

## 1. Executive Summary & Verification Overview
Modernized root `README.md` into an athlete- and developer-focused hero landing page. Integrated the official brand-compliant vector badge `images/google-play-badge.svg` linking directly to the canonical Google Play Store listing (`https://play.google.com/store/apps/details?id=com.atrainingtracker`, correcting the erroneous `.trainingtracker` suffix). Embedded the high-resolution app logo (`images/logo_512.png`), direct navigation links to the bilingual Online User Guide (`docs/index.md`, `docs/guide/de/index.md`, `docs/guide/en/index.md`), and updated V5.0 feature highlights (.FIT recording, cloud backup triad, Skiba $W'_{\text{bal}}$, Coggan power metrics, and *Lieblingsstrecken*). Automated CI contract test `ReadmePlayStoreBadgeContractTest.kt` guarantees asset existence, URL canonicality, and link resolution.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STP-005` | `TST-STP-005` | JUnit 4 `ReadmePlayStoreBadgeContractTest` & Link Audit | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit & Contract Test Suite
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 1s (ReadmePlayStoreBadgeContractTest + Build suite, 100% pass)
```
*Executed test scope: Tier 1 contract suite (`com.atrainingtracker.trainingtracker.build.*`), covering `ReadmePlayStoreBadgeContractTest`, `PlayStoreListingContractTest`, `OnlineUserGuideContractTest`, `ManifestAndBuildConfigContractTest`, `ReleaseCiAndLintConfigContractTest`, `DeadCodeEliminationContractTest`, and `AndroidViewModelReflectionContractTest`.*

### Requirement Governance
```text
python3 tools/verify_requirement_governance.py
Net-new requirement(s) detected: REQ-STP-005. Bypassing archaeology check cleanly.
```

---

## 4. Hardware & Physical Verification
* Documentation, repository asset, and build contract test enhancement with zero changes to `app/src/main/` runtime code. Physical APK deployment and on-device UI screenshot capture skipped per Rule 60 / Rule 62.
* Local asset files (`images/google-play-badge.svg`, `images/logo_512.png`) and User Guide documents (`docs/index.md`, `docs/guide/de/index.md`, `docs/guide/en/index.md`) verified to exist and resolve with 100% integrity.

---

## 5. Invariant & Governance Verification
1. **Canonical Package Target**: Store link points strictly to `id=com.atrainingtracker` without the 404-inducing `.trainingtracker` suffix.
2. **Asset Resilience**: Local vector badge asset (`images/google-play-badge.svg`) ensures crisp DPI rendering and avoids GitHub Camo proxy rate-limiting or CDN outages.
3. **Link & Navigation Integrity**: All documentation targets resolve locally on disk.
4. **Living Documentation Synchronized**: `REQ-STP-005` and `TST-STP-005` marked `Verified` in `docs/requirements/` and `docs/tests/`.
5. **Zero Runtime Blast Radius**: Zero lines altered in production application code.
6. **Mandatory Lösungsversion Governance**: Active release version `V5.0.0` maintained across all Stage 1–5 deliverables.
