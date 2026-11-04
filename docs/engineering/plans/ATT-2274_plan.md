# Stage 3: Implementation Plan - ATT-2274: Produce Google Play Store Promo Video and Feature Highlights

**Ticket**: [ATT-2274](https://rainerblind.atlassian.net/browse/ATT-2274)  
**Sub-task**: [ATT-5367](https://rainerblind.atlassian.net/browse/ATT-5367) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-STP-006`  
**Test Mapping**: `TST-STP-006`  
**Branch**: `feature/ATT-2274`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Description & Background
Prospective athletes visiting Google Play need a dynamic video showcase demonstrating aTrainingTracker's responsive Compose telemetry, BLE/ANT+ arbitration, and route clustering on a bicycle handlebar. Per user screening guidance, we provide an automated hybrid pipeline compositing high-res app screenshots into outdoor handlebar plates with modular photo/screenshot drop-in support.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-STP-006` (*Play Store Promo Video Storyboard, Composite Generator & Showcase Pipeline*)
* **Test Mapping**: `TST-STP-006` (`PromoVideoAssetsContractTest.kt`, covering `TST-STP-006.1`..`3`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Regressions**: No modifications to Android production APK classes or manifest permissions.
2. **Store Parity Invariant**: Existing localized store descriptions (`REQ-STP-001`..`005`) remain strictly untouched.
3. **Deterministic Tooling**: `tools/generate_promo_video.py` runs with standard Python 3 + Pillow + GStreamer without paid external services.
4. **Directory Invariant (Rule 67)**: Video assets reside under `docs/store_presence/video/`, keeping `docs/engineering/` canonical.

---

## 4. Proposed Architectural Changes
* **Storyboard (`docs/store_presence/promo_video_storyboard.md`)**: 5 scenes spanning 60 seconds (0:00–0:10 Cockpit Hook, 0:10–0:25 Sensors & Gear, 0:25–0:40 Navigation & Climbs, 0:40–0:50 Clustering & Physiology, 0:50–1:00 Privacy & Supporter CTA).
* **Generator Pipeline (`tools/generate_promo_video.py`)**: Procedural handlebar background plate generator, smartphone bezel compositing, touch ripple markers, callout banners, and dual video encoder backends (GStreamer `x264enc`/`openh264enc` and ffmpeg).
* **Contract Test**: `PromoVideoAssetsContractTest.kt` verifying storyboard, generator script, 1080p MP4, and keyframe dimensions.
* **UI Consistency (Rule 23)**: No app UI changes (offline media tooling and store documentation).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (Storyboard)**: Author `docs/store_presence/promo_video_storyboard.md` detailing timestamps, A-Roll/B-Roll camera angles, touch gestures, visual-first English callouts, and custom handlebar photo swap instructions.
* **Step 2 (Generator Script)**: Create `tools/generate_promo_video.py` supporting `--bg-photo`, `--screens-dir`, `--output-video`, and `--output-frames`, generating composite frames and 1080p MP4 showcase video.
* **Step 3 (Asset Generation)**: Execute generator script to produce `docs/store_presence/video/frames/scene_01..05.png`, `docs/engineering/walkthroughs/img/att2274_*.png`, and `docs/store_presence/video/att_promo_showcase.mp4`.
* **Step 4 (Contract Test)**: Author and run `PromoVideoAssetsContractTest.kt` in `app/src/test/java/.../build/`.
* **Step 5 (Unit Verification)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PromoVideoAssetsContractTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: `PromoVideoAssetsContractTest` verifies all assets, durations, and dimensions.
* **Rollback**: Clean git branch revert without impacting `sprint/2026-45.4`.
