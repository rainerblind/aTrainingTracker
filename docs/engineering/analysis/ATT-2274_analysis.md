# Stage 1 Analysis: ATT-2274 - Produce Google Play Store Promo Video & Feature Highlights

**Ticket**: [ATT-2274](https://rainerblind.atlassian.net/browse/ATT-2274)  
**Sub-task**: [ATT-5365](https://rainerblind.atlassian.net/browse/ATT-5365) (`[Analysis]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-2274`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
In Sprint 2026-45.4 Review, ATT-2274 was rated `n.i.O.` due to two critical visual flaws in the promo video pipeline:
1. **Cascaded Mislabeled Screenshots (Rule 71)**: Because `tools/generate_promo_video.py` ingested unverified screenshots from `docs/store_presence/screenshots/`, Scene 1 ("COLOR-CODED ZONE TELEMETRY") and Scene 4 displayed the "Trainingseinheit überarbeiten" edit dialog, Scene 2 ("MULTI-PROTOCOL SENSOR LINKING") and Scene 3 displayed workout details instead of sensor pairing or live maps.
2. **Handlebar Mount Disconnect & Off-Center Alignment**: The procedural background drew a cartoon stem centered at $X = 960$ while placing the smartphone at $X = 680$, leaving the phone floating in mid-air over the left drop bar. The aero cockpit and out-front mount must be centered directly under and behind the smartphone.
3. **B-Roll Zoom Crop Misalignment**: B-roll callouts framed non-existent or irrelevant portions of the mislabeled screens.

---

## 2. Forensic Root Cause Analysis & Independent Hypothesis Verification
* **Visual Frame Audit (`read_file` — Rule 71)**:
  * `scene_01_cockpit.png`: Mislabeled edit dialog on phone; B-roll callout crops edit fields instead of live telemetry tiles $\rightarrow$ update to `01_cockpit_tracking.png` with crop on speed, distance, and HR zone bar.
  * `scene_02_sensors.png`: Mislabeled workout details $\rightarrow$ update to `02_sensor_management.png` with crop on BLE/ANT+ device rows.
  * `scene_03_navigation.png`: Duplicate workout details $\rightarrow$ update to `03_live_map.png` with crop on live map canvas, navigation heading, and bike bell.
  * `scene_04_clustering.png`: Mislabeled edit dialog $\rightarrow$ update to `04_lieblingsstrecken.png` with crop on cluster performance curve and heatmap.
  * `scene_05_privacy_export.png`: Periods overview $\rightarrow$ update to `05_physiology_zones.png` / `docs/guide/img/09_fit_export.png` with crop on .FIT/cloud export and data sovereignty.
* **Cockpit Geometry & Alignment Forensics**:
  * Smartphone center is at $X = 680, Y = 610$. Stem and out-front dual-arm mount must align symmetrically at $X = 680$ with carbon weave shading, faceplate bolts, 31.8mm clamp, and aero wing profile.
  * Background road vanishing point and depth-of-field bokeh must anchor the athletic perspective.
* **Toolchain Verification**: Python 3.14 + Pillow + GStreamer (`x264enc`/`openh264enc`) verified working locally.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**: Refactor `tools/generate_promo_video.py` to: (a) center the aero carbon cockpit and out-front mount under the smartphone at $X = 680$; (b) map scenes 1–5 to verified authentic screenshots; (c) recalculate B-roll crop boxes; (d) regenerate all keyframes and `att_promo_showcase.mp4`; (e) update contract assertions in `PromoVideoAssetsContractTest.kt`.
* **Out-of-Scope**: Runtime Android APK code; manual YouTube upload / Play Console URL linking (Rule 22 human prerequisite).
* **Complexity**: 2 SP. Single-script generator and media asset pipeline; well within AAA $\le 3\text{ SP}$ limit (`REQ-PRO-029`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Mapping**: `REQ-STP-006` (*Promo Video Storyboard, Composite Generator & Showcase Asset Pipeline*) and test `TST-STP-006`.
* **Chesterton's Fence & Side-Effect Audit (Rule 68)**:
  * Preserves dual-backend encoding (GStreamer / Pillow keyframe fallback).
  * Enhances visual realism without adding binary dependencies or breaking automated CI headless runs.

---

## 5. Architectural & Implementation Strategy
1. **Handlebar Cockpit Redesign**: Symmetrically center carbon stem, 4 faceplate hex bolts, out-front computer mount arms, and 3K carbon weave at $X = 680$.
2. **Scene Source Mapping & B-Roll Crops**: Point each scene to authentic `docs/store_presence/screenshots/` and `docs/guide/img/` assets with pixel-exact crop boxes.
3. **Asset Generation & Visual Audit (Rule 71)**: Execute `python3 tools/generate_promo_video.py` and inspect all 5 generated PNGs via `read_file`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 1080p Full HD resolution ($1920 \times 1080$); video duration 60s; 100% genuine Compose screenshots; zero runtime app code edits.
* **Risk Rating**: **HIGH-RISK for Stage 4 (Store Presence & Video Generation — Rule 71)**. Visual marketing asset requires `subagent_pro` construction and `read_file` verification.
