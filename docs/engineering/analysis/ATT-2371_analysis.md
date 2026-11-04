# Stage 1 Analysis: ATT-2371 - Add prominent Google Play Store badge with optimized placement in README

**Ticket**: [ATT-2371](https://rainerblind.atlassian.net/browse/ATT-2371)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-2371`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
* Root `README.md` is the primary landing page on GitHub for athletes, contributors, and open-source users.
* Currently, `README.md` features only an unstyled markdown text link (`**[Get it on Google Play](https://play.google.com/store/apps/details?id=com.atrainingtracker)**`) without official badge artwork or modern branding.
* The newly designed 512x512 app logo (`images/logo_512.png`), comprehensive bilingual Online User Guide (`ATT-2275`), and V5.0 feature innovations (.FIT export, Skiba $W'_{\text{bal}}$, Coggan power metrics, Lieblingsstrecken, Google Drive sync) are not integrated into the top hero section.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State & Gap Analysis**:
  * `README.md` (lines 1–11) opens directly into body text followed by legacy ANT+ sensor icons, lacking a visual hero section.
  * No visual Google Play badge exists in the repository.
  * No links exist connecting `README.md` to `docs/index.md`, `docs/guide/en/index.md`, or `docs/guide/de/index.md`.
* **Independent Peer Review & Verification (ATT-3534, Rules 69 & 70)**:
  * *Package Identifier Audit*: The ticket hypothesis cites `https://play.google.com/store/apps/details?id=com.atrainingtracker.trainingtracker`. Codebase audit of `app/build.gradle` (`namespace = "com.atrainingtracker"`), `AndroidManifest.xml` (`package="com.atrainingtracker"`), `.github/workflows/android-release.yml` (`package_name: com.atrainingtracker`), and live Google Play indexing confirms the canonical published application ID is `com.atrainingtracker`. Linking to `com.atrainingtracker.trainingtracker` would yield a 404 broken link. Canonical target: `https://play.google.com/store/apps/details?id=com.atrainingtracker`.
  * *Asset Hosting & Camo Proxy Resilience*: External CDN badges frequently break or rate-limit under GitHub's Camo image proxy. Adding an official, self-hosted vector badge (`images/google-play-badge.svg`) ensures 100% availability, crisp rendering across all screen DPIs, and zero network dependencies.
  * *Side-Effect Audit (Rule 68)*: Updating `README.md` and adding local SVG/test assets touches zero production runtime code or Android framework lifecycle paths.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Add official, brand-compliant vector badge asset `images/google-play-badge.svg`.
  * Modernize top header of `README.md` with centered hero presentation: app logo (`images/logo_512.png`), title, subtitle, clickable Google Play badge, quick links to bilingual User Guides (`docs/index.md`), and V5.0 feature highlights.
  * Implement automated JUnit 4 contract test `ReadmePlayStoreBadgeContractTest.kt` in `com.atrainingtracker.trainingtracker.build`.
* **Out-of-Scope Non-Goals**:
  * No alterations to application code in `app/src/main/`.
  * No changes to GitHub Pages Jekyll configuration (`docs/_config.yml`).
* **Complexity Rating**: **1 SP** (Trivial/Standard documentation, visual asset integration, and contract test).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Requirement Baseline**: Net-new requirement `REQ-STP-005` (*Repository README Google Play Store Badge & User Guide Navigation Integration*), extending `REQ-PRO-033` (*Online User Guide*) and `REQ-STP-001`–`004` (*Store Presence*). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
* **Asset Integration**: Place official Google Play badge vector at `images/google-play-badge.svg`.
* **Hero Section Layout (`README.md`)**:
  * Clean hero header embedding `images/logo_512.png` (height 120px) and official clickable badge linking to `https://play.google.com/store/apps/details?id=com.atrainingtracker`.
  * Direct action links to Online User Guide: `docs/index.md`, `docs/guide/en/index.md`, and `docs/guide/de/index.md`.
  * Refresh feature bullet list with V5.0 highlights (.FIT, Strava/Dropbox/Google Drive, Skiba $W'_{\text{bal}}$, Coggan analytics, Lieblingsstrecken).
* **Automated Contract Test**:
  * `ReadmePlayStoreBadgeContractTest.kt` verifying Play Store link URL, badge SVG file existence and valid XML/SVG structure, app logo embedding, and User Guide markdown link validity.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. *Link Integrity*: Play Store badge link MUST point to canonical `id=com.atrainingtracker`.
  2. *Self-Contained Assets*: Badge and logo referenced in `README.md` MUST resolve to local repository paths under `images/`.
  3. *Zero Camo Breakage*: No external CDN image URLs for core branding elements.
  4. *Zero Production Blast Radius*: Zero changes to `app/src/main/` runtime code.
* **Risk Rating**: **LOW** (Documentation and static asset enhancement validated by automated CI contract test).
