# Stage 1 Analysis: ATT-2275 - Create Comprehensive Online User Guide on GitHub Pages

**Ticket**: [ATT-2275](https://rainerblind.atlassian.net/browse/ATT-2275)  
**Sub-task**: [ATT-5325](https://rainerblind.atlassian.net/browse/ATT-5325) (`[Analysis]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-2275`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
aTrainingTracker has matured over 15+ years into an advanced, privacy-first athletic computer and telemetry tracking suite with standout capabilities (dual BLE/ANT+ arbitration, barometric Known Locations calibration, automatic route clustering / *Lieblingsstrecken*, Strava Live Segments with interactive elevation profiles, Coggan/Skiba physiology analytics $\text{NP}$/$\text{IF}$/$\text{TSS}$/$W'_{\text{bal}}$, and Periods 2.0). However, there is no centralized, illustrated online manual for athletes.

## 2. Root Cause & Independent Hypothesis Verification
- **Gap Analysis**: The existing `docs/` repository hosts internal ASPICE protocol artifacts, engineering plans, and architecture notes, but lacks a public, mobile-responsive user guide for athletes in German and English.
- **Independent Peer Review & Verification**:
  - `docs/_config.yml` must explicitly configure Jekyll `exclude:` entries to prevent internal developer protocol files (`ci-secrets.md`, `project_protocol.md`, `engineering/`, `requirements/`, `tests/`) from polluting public site navigation.
  - A structured chapter hierarchy (`docs/guide/de/` and `docs/guide/en/`) with 9 parallel chapters and stable HTML anchors (`<a id="...">`) is required to support in-app deep linking.

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Author 9 comprehensive, screenshot-illustrated chapters in both German and English (18 markdown files + 2 index files).
  2. Configure Jekyll GitHub Pages landing page (`docs/index.md`) and exclusion hygiene (`docs/_config.yml`).
  3. Curate high-resolution screenshots in `docs/guide/img/` illustrating actual Jetpack Compose UI workflows.
  4. Implement `OnlineUserGuideContractTest.kt` verifying chapter completeness, exclusions, assets, and anchor parity.
- **Out-of-Scope Non-Goals**:
  1. Video editing / YouTube promo production (reserved for `ATT-2274`).
  2. Modifying core Android tracking or database runtime services.

## 4. Requirement Archaeology & Chesterton's Fence Audit
- Net-new requirement only (`REQ-PRO-033`). No existing requirements modified.

## 5. Architectural Strategy & High-Level Solution
- Host static Jekyll Markdown within `docs/` utilizing GitHub Pages.
- Organize content into modular chapters (01 Quick Start through 09 Export & Cloud Sync) with 1:1 bilingual section anchors.
- Automate contract verification via JUnit 4 test in `build` package.

## 6. System Invariants & Risk Assessment
- **Invariants**: 100% data sovereignty preserved; zero Android APK runtime regression; developer ASPICE governance files protected.
- **Risk Rating**: **LOW** (Static documentation and build-time verification only).
