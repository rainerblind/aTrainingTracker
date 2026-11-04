# Stage 3: Implementation Plan - ATT-2371: Add prominent Google Play Store badge with optimized placement in README

**Ticket**: [ATT-2371](https://rainerblind.atlassian.net/browse/ATT-2371)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-2371`  
**Requirement Mapping**: `REQ-STP-005` | **Test Mapping**: `TST-STP-005`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Root `README.md` is the primary entry point for GitHub visitors, athletes, and open-source contributors. Currently, it lacks a prominent visual download badge, featuring only an unstyled markdown text link. Furthermore, the modern app logo (`images/logo_512.png`), newly authored bilingual Online User Guide (`ATT-2275`), and V5.0 feature highlights are missing from the top hero section. This plan coordinates adding a self-hosted vector Google Play badge, modernizing `README.md` hero presentation, and introducing an automated JUnit 4 contract test.

---

## 2. Traceability & System Invariants
* **Requirements**: `REQ-STP-005` (README Play Store Badge, Hero Header & Guide Integration).
* **Test Mapping**: `TST-STP-005` (`ReadmePlayStoreBadgeContractTest.kt`).
* **Core Invariants**:
  1. *Canonical Package Identifier*: Store link MUST target canonical ID `com.atrainingtracker` (`https://play.google.com/store/apps/details?id=com.atrainingtracker`).
  2. *Self-Hosted Asset Resilience*: Badge and logo assets MUST be hosted locally under `images/` (immune to GitHub Camo proxy breakage/rate-limiting).
  3. *Link & Asset Integrity*: All image tags and documentation links (`docs/index.md`, `docs/guide/de/`, `docs/guide/en/`) in `README.md` MUST resolve to valid existing files.
  4. *Zero Production Blast Radius*: Zero changes to `app/src/main/` runtime code, database schemas, or Android lifecycles.

---

## 3. Proposed Architectural Changes

### Component 1: Official Vector Google Play Store Badge Asset
* **File**: `images/google-play-badge.svg`
* Self-hosted, brand-compliant SVG vector badge conforming to Google Play guidelines (black rounded container, color Play icon, "GET IT ON / Google Play" typography).

### Component 2: Root `README.md` Hero Header Modernization
* **File**: `README.md`
* Hero presentation embedding `images/logo_512.png` (height 120px), title, tagline, and clickable `images/google-play-badge.svg` linking to canonical Play Store URL.
* Quick-access bilingual navigation bar linking to `docs/index.md`, `docs/guide/de/index.md`, and `docs/guide/en/index.md`.
* Modernized V5.0 feature highlights (.FIT recording, Strava/Dropbox/Drive sync, Skiba $W'_{\text{bal}}$, Coggan analytics, Lieblingsstrecken route clustering).

### Component 3: Automated Contract Test (`ReadmePlayStoreBadgeContractTest.kt`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReadmePlayStoreBadgeContractTest.kt`
* Verifies `README.md` embeds valid local badge linking to canonical Play Store listing.
* Verifies `images/google-play-badge.svg` and `images/logo_512.png` exist with non-zero size.
* Verifies relative User Guide links resolve to existing markdown files on disk.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create Official Google Play Store Vector Badge Asset
* Create `images/google-play-badge.svg` containing valid vector SVG geometry and styling.

### Step 2: Implement JUnit 4 README Contract Test
* Author `ReadmePlayStoreBadgeContractTest.kt` in `com.atrainingtracker.trainingtracker.build` asserting badge existence, URL validity, logo presence, and User Guide link resolution.

### Step 3: Modernize Root `README.md` Hero Header & Feature Highlights
* Update `README.md` top section with logo, clickable badge, User Guide links, and V5.0 feature highlights.

### Step 4: Run Targeted Contract Test Suite & Governance Verification
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReadmePlayStoreBadgeContractTest"`.
* Run `python3 tools/verify_requirement_governance.py` (`exit code 0`).
