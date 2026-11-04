# Stage 3: Implementation Plan - ATT-2274: Produce Google Play Store Promo Video & Feature Highlights

**Ticket**: [ATT-2274](https://rainerblind.atlassian.net/browse/ATT-2274)  
**Sub-task**: [ATT-5367](https://rainerblind.atlassian.net/browse/ATT-5367) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-2274`  
**Requirement Mapping**: `REQ-STP-006` | **Test Mapping**: `TST-STP-006`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Sprint 2026-45.4 Review issued an `n.i.O.` rating on ATT-2274 due to two critical visual flaws:
1. **Cascaded Mislabeled Screenshots (Rule 71)**: `generate_promo_video.py` ingested unverified screenshots from `docs/store_presence/screenshots/`, displaying the workout edit dialog in Scenes 1 and 4, and workout details in Scenes 2 and 3.
2. **Handlebar Mount Disconnect & Off-Center Alignment**: The procedural background placed the stem at $X = 960$ while placing the phone at $X = 680$, leaving the phone floating in mid-air over the left handlebar drop.
3. **B-Roll Zoom Crop Misalignment**: Callout cards cropped irrelevant areas of the mislabeled screens.
This plan coordinates Stage 4 implementation (`HIGH-RISK` per Rule 71, executed by `subagent_pro`).

---

## 2. Traceability & System Invariants
* **Requirements**: `REQ-STP-006` (`REQ-STP-006.1`..`4` — Storyboard, Centered Mount, Screenshot Mapping, 1080p MP4).
* **Test Mapping**: `TST-STP-006` (`TST-STP-006.1`..`4` — Storyboard audit, script test, asset/uniqueness contract, unit test).
* **Core Invariants**:
  1. 1080p Full HD resolution ($1920 \times 1080$) and 60s total duration.
  2. All 5 scene frames have unique SHA-256 hashes and display verified authentic Compose screens.
  3. Out-front carbon computer mount is symmetrically centered at $X = 680$ directly under and behind the smartphone.
  4. Zero modifications to production Android APK code, database schemas, or runtime permissions.

---

## 3. Atomic Step-by-Step Implementation Sequence (Stage 4 Construction — HIGH-RISK)

### Step 1: Redesign Procedural Cockpit & Calibrate B-Roll Crops (`REQ-STP-006.2`)
* **File**: `tools/generate_promo_video.py`
* In `generate_procedural_handlebar_background(path)`:
  - Redesign the carbon stem, clamp, 4 titanium faceplate bolts, and dual-arm out-front mount centered symmetrically at the smartphone center $X = 680$.
  - Add realistic 3K carbon weave texture, metallic highlights, and natural road depth-of-field bokeh.
* In `render_scene()`:
  - Map Scene 1 to `01_cockpit_tracking.png` with B-roll crop on Speed, Distance, and HR zone bar.
  - Map Scene 2 to `02_sensor_management.png` with B-roll crop on BLE/ANT+ device matrix and battery levels.
  - Map Scene 3 to `03_live_map.png` with B-roll crop on live vector map canvas, navigation heading, and bike bell.
  - Map Scene 4 to `04_lieblingsstrecken.png` on phone with B-roll crop on `05_physiology_zones.png` training zones.
  - Map Scene 5 to `06_periods_analytics.png` on phone with B-roll crop on `docs/guide/img/09_fit_export.png` SAF .FIT export.

### Step 2: Regenerate Scene Frames & Showcase MP4 (`REQ-STP-006.3`, `Rule 71`)
* Execute `python3 tools/generate_promo_video.py`.
* Output: `docs/store_presence/video/frames/scene_01..05.png`, `docs/engineering/walkthroughs/img/att2274_*.png`, and `docs/store_presence/video/att_promo_showcase.mp4`.
* **Mandatory `read_file` visual audit**: Inspect all 5 generated PNGs to confirm centered mount alignment and genuine screen depictions.

### Step 3: Extend Automated Contract Test (`REQ-STP-006.4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/PromoVideoAssetsContractTest.kt`
* Add SHA-256 uniqueness assertion across all 5 scene frames (zero duplicates).
* Assert `generate_promo_video.py` enforces centered mount geometry at the smartphone center $X = 680$.

### Step 4: Verification & Governance
* Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PromoVideoAssetsContractTest"`.
* Execute `python3 tools/verify_requirement_governance.py` (`exit code 0`).
* Document YouTube upload and Play Console video URL linking as Human Prerequisite (`Rule 22`).

---

## 4. System Invariants & Rollback Plan
* **Risk Classification**: **HIGH-RISK for Stage 4 (Rule 71)** — executed by `subagent_pro`.
* **Rollback**: Branch isolation (`feature/ATT-2274`) ensures instant clean rollback with zero repository side effects.
