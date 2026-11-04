# Stage 2: Requirement & Test Specification - ATT-2371: Add prominent Google Play Store badge with optimized placement in README

**Ticket**: [ATT-2371](https://rainerblind.atlassian.net/browse/ATT-2371)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-STP-005`  
**Test Spec ID**: `TST-STP-005`  
**Branch**: `feature/ATT-2371`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-STP-005)

### 1.1 Scope & Functional Clauses
1. **Hero Section Branding (`README.md`)**: The repository root `README.md` SHALL render a centered hero section embedding the official application logo (`images/logo_512.png`, height 120px), application title, subtitle, and an official clickable Google Play Store badge.
2. **Self-Hosted Vector Badge Asset (`images/google-play-badge.svg`)**: The badge SHALL be hosted locally within the repository at `images/google-play-badge.svg` and SHALL link directly to the canonical store listing `https://play.google.com/store/apps/details?id=com.atrainingtracker` (avoiding external CDN failure modes and the erroneous `.trainingtracker` package suffix).
3. **User Guide Navigation Links**: The hero section SHALL provide direct navigation links to the Online User Guide (`docs/index.md`) including both English (`docs/guide/en/index.md`) and German (`docs/guide/de/index.md`) editions.
4. **V5.0 Feature Highlights**: `README.md` SHALL feature key V5.0 innovations including native .FIT export/import, Strava/Dropbox/Google Drive cloud sync, Skiba $W'_{\text{bal}}$ and Coggan power analytics, and Lieblingsstrecken navigation.
5. **Asset & Link Integrity**: All local assets and documentation links referenced in `README.md` MUST resolve to valid existing files on disk.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Novel requirement `REQ-STP-005`. Targets `README.md`, `images/google-play-badge.svg`, `ReadmePlayStoreBadgeContractTest.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-45.4 (`ATT-2371`).
* **Root Reason for Existing Formulation**: `README.md` previously contained only a plain text link to Google Play without official vector branding, logo hero presentation, or links to the newly authored Online User Guide (`ATT-2275`).
* **Preservation of Core Invariants**: 100% ad-free, local data sovereignty, zero runtime binary changes in `app/src/main/`.

### 1.2 Acceptance Criteria (Given-When-Then)
* **AC-1 (Clickable Official Badge)**: *Given* `README.md`, *When* inspected, *Then* the hero section embeds `images/google-play-badge.svg` linking to `https://play.google.com/store/apps/details?id=com.atrainingtracker`.
* **AC-2 (Self-Hosted Vector Asset)**: *Given* `images/google-play-badge.svg`, *When* parsed, *Then* it exists on disk, contains valid `<svg` root, and renders official Google Play branding without remote CDN dependencies.
* **AC-3 (User Guide Integration)**: *Given* `README.md`, *When* links are verified, *Then* links to `docs/index.md`, `docs/guide/en/index.md`, and `docs/guide/de/index.md` exist and resolve to valid markdown files.
* **AC-4 (CI Contract Test)**: *Given* `ReadmePlayStoreBadgeContractTest.kt`, *When* executed via `./gradlew testDebugUnitTest`, *Then* all badge, URL, logo, and guide link assertions pass with 100%.

---

## 2. Test Specification (TST-STP-005)
* **TST-STP-005 (README Play Store Badge Asset, Link Integrity & Navigation Contract Test)**: Automated JUnit 4 test located in `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReadmePlayStoreBadgeContractTest.kt` verifying:
  1. `README.md` contains image reference to `images/google-play-badge.svg` linking to `https://play.google.com/store/apps/details?id=com.atrainingtracker`.
  2. `images/google-play-badge.svg` exists on disk and has valid XML/SVG structure.
  3. `images/logo_512.png` is referenced and exists on disk.
  4. User Guide relative links (`docs/index.md`, `docs/guide/en/index.md`, `docs/guide/de/index.md`) exist and resolve to valid files.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STP-005` | CI Contract Test | `ReadmePlayStoreBadgeContractTest.kt`, `README.md`, `images/google-play-badge.svg` | `REQ-STP-005` | Planned |
