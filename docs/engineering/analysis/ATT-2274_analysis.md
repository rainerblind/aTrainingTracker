# Stage 1 Analysis: ATT-2274 - Produce Google Play Store Promo Video and Feature Highlights

**Ticket**: [ATT-2274](https://rainerblind.atlassian.net/browse/ATT-2274)  
**Sub-task**: [ATT-5365](https://rainerblind.atlassian.net/browse/ATT-5365) (`[Analysis]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-2274`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
The Google Play Store presence for `aTrainingTracker` currently relies entirely on static screenshots and text descriptions. Prospective athletes cannot observe dynamic capabilities (Compose gauges, live map tracking, Strava segment elevation scrubbing, route clustering). During Sprint 2026-45.4 screening, the user requested: *"Please prepare everything. Maybe, we could also take a photo somewhere else, replay some workout, take a screenshot and put it into the photo?"* We need an authentic hybrid production pipeline combining handlebar/outdoor context with razor-sharp telemetry cutaways.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State**: `docs/store_presence/` contains localized descriptions (`de.md`..`pt.md`) and static screenshots (`screenshots/`). No promo video storyboard, video assets, or automated compositing pipeline exists.
* **Independent Peer Review & Verification**:
  * **Toolchain Compatibility**: Host environment has Python 3.14, Pillow 12.1.1, and GStreamer 1.26 (`x264enc`, `openh264enc`, `mp4mux`), while `ffmpeg` is not in PATH. `tools/generate_promo_video.py` must support dual video backends (`ffmpeg` with automatic `gst-launch-1.0` fallback, plus Pillow keyframe export) for robust local execution.
  * **Asset Availability**: Verified 24+ high-resolution AMOLED and Light mode UI screens in `docs/store_presence/screenshots/` and `docs/guide/img/` covering Cockpit, Sensors, Routes, Clusters, and Analytics.
  * **Rule 67 Governance**: Output video (`docs/store_presence/video/`) and storyboard (`docs/store_presence/`) reside outside `docs/engineering/`. Walkthrough evidence images (`att2274_*.png`) conform to `docs/engineering/walkthroughs/img/`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Author 5-scene promo video storyboard (`docs/store_presence/promo_video_storyboard.md`, 0:00–1:00).
  * Build Python/Pillow/GStreamer composite video generator (`tools/generate_promo_video.py`) producing composite keyframes and 1080p MP4 (`docs/store_presence/video/att_promo_showcase.mp4`).
  * Include realistic handlebar/outdoor cockpit composite background template with modular photo/screenshot drop-in support.
  * Author JUnit 4 contract test `PromoVideoAssetsContractTest.kt` in `app/src/test/.../build/`.
  * Document Rule 22 Human Prerequisites (custom handlebar photo swap, YouTube upload, Play Console URL).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to Android APK runtime code or database schemas.
  * Physical external YouTube video upload and Play Console store URL linking (Rule 22 manual step).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-STP-006` (*Promo Video Storyboard, Composite Generator & Showcase Asset Pipeline*) and test `TST-STP-006` in `req_project_governance.md` / `tst_project_governance.md`.
* **Historical Origin & Commit Trace**: Sprint 2026-45.4 (`ATT-2274`, Parent Epic `ATT-2273`). Extends `REQ-STP-004` (Store Screenshots) and `REQ-STP-005` (Store Navigation).
* **Root Reason for Existing Formulation**: Store presence historically relied exclusively on static screenshots and localized textual descriptions.
* **Preservation of Core Invariants**: Existing requirements `REQ-STP-001`..`005` remain unmodified; 9-language character length bounds strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Storyboard**: 5 scenes (0:00–0:10 Hook & Cockpit, 0:10–0:25 Sensors & Gear Matrix, 0:25–0:40 Navigation & Segments, 0:40–0:50 Clustering & Physiology, 0:50–1:00 Privacy & Supporter).
* **Generator Pipeline (`tools/generate_promo_video.py`)**:
  * Procedurally renders or loads high-resolution outdoor bicycle handlebar background.
  * Composites Pixel 10 smartphone bezel, workout screenshots, touch ripples, and visual-first English callout overlays.
  * Renders 1080p MP4 via GStreamer/ffmpeg and exports individual scene keyframe images.
* **Contract Verification**: `PromoVideoAssetsContractTest.kt` verifies storyboard structure, generator script exit code 0, video file presence, and keyframe dimensions.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero modification to production Android application codebase or Gradle dependencies.
  2. Generator runs deterministically using standard library + Pillow + GStreamer without extra pip packages.
  3. `docs/engineering/` canonical directory structure strictly preserved (Rule 67).
* **Risk Rating**: **LOW** — Offline media generation pipeline, documentation, and build contract test.
