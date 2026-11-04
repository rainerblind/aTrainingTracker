# Stage 2: Requirement & Test Specification - ATT-2274: Produce Google Play Store Promo Video and Feature Highlights

**Ticket**: [ATT-2274](https://rainerblind.atlassian.net/browse/ATT-2274)  
**Sub-task**: [ATT-5366](https://rainerblind.atlassian.net/browse/ATT-5366) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-STP-006` (*Google Play Store Promo Video Storyboard, Composite Generator & Showcase Pipeline*)  
**Test Spec ID**: `TST-STP-006` (*Play Store Promo Video Assets, Storyboard & Generator Contract Test*)  
**Branch**: `feature/ATT-2274`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-STP-006)

### 1.1 Problem Statement & Rationale
Prospective athletes visiting the Google Play Store need dynamic visualization of live cockpit telemetry, dual-protocol sensor pairing, interactive elevation profiles, Strava live segments, and Coggan/Skiba physiology analytics in an authentic cycling environment.

### 1.2 Functional & Architectural Requirements
1. **5-Scene Storyboard (`REQ-STP-006.1`)**: Deliver timestamped storyboard (`docs/store_presence/promo_video_storyboard.md`, 0:00–1:00) covering Cockpit Hook, Sensors/Gear, Navigation/Climbs, Clustering/Physiology, and Privacy/Supporter CTA.
2. **Automated Composite Video Generator (`REQ-STP-006.2`)**: Implement `tools/generate_promo_video.py` using Python/Pillow with dual video backend support (ffmpeg with fallback to `gst-launch-1.0` / keyframe export).
3. **Handlebar Backdrop & Telemetry Framing (`REQ-STP-006.3`)**: Procedurally compose or load outdoor bike handlebar background, Pixel 10 bezel, UI screenshots, touch ripples, and English text callouts.
4. **1080p MP4 & Keyframe Output (`REQ-STP-006.4`)**: Output $1920 \times 1080$ video to `docs/store_presence/video/att_promo_showcase.mp4` and high-res scene keyframes.
5. **Contract Test (`REQ-STP-006.5`)**: Validate storyboard structure, generator exit code 0, video file presence, and keyframe dimensions via `PromoVideoAssetsContractTest.kt`.
6. **Rule 22 Human Prerequisites (`REQ-STP-006.6`)**: Document manual YouTube upload and Google Play Store Console URL registration.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* `docs/store_presence/promo_video_storyboard.md`, *When* inspected, *Then* 5 timestamped scenes spanning 0:00 to 1:00 are defined.
- *Given* `tools/generate_promo_video.py`, *When* executed on host, *Then* it produces `docs/store_presence/video/att_promo_showcase.mp4` and keyframes with exit code 0.
- *Given* `PromoVideoAssetsContractTest.kt`, *When* executed via `./gradlew testDebugUnitTest`, *Then* all tests pass 100%.

### 1.4 System Invariants
- Zero modification to production Android application code or dependencies.
- Generator operates offline using Python standard library, Pillow, and available encoder without external paid APIs.

---

## 2. Test Specification (TST-STP-006)

### Test Case 1: `testPromoVideoStoryboard_existsAndValidatesScenes` (`TST-STP-006.1`)
- **Scope**: Storyboard Structure Test (`PromoVideoAssetsContractTest.kt`)
- **Expected Result**: File exists, contains 5 scene headings, covers 0:00 to 1:00, and specifies screen and callout assets.

### Test Case 2: `testGeneratePromoVideoScript_existsAndSupportsEncoders` (`TST-STP-006.2`)
- **Scope**: Pipeline Tooling Test (`PromoVideoAssetsContractTest.kt`)
- **Expected Result**: `tools/generate_promo_video.py` exists, is executable, and contains ffmpeg and GStreamer pipeline declarations.

### Test Case 3: `testPromoVideoAssets_existAndMeetResolutionStandards` (`TST-STP-006.3`)
- **Scope**: Media Output Contract (`PromoVideoAssetsContractTest.kt`)
- **Expected Result**: `docs/store_presence/video/att_promo_showcase.mp4` exists ($>100\text{ KB}$) and keyframe images are $1920 \times 1080$.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-STP-006.1]` | Build Contract | `testPromoVideoStoryboard_existsAndValidatesScenes` | `REQ-STP-006` | Planned |
| `[TST-STP-006.2]` | Build Contract | `testGeneratePromoVideoScript_existsAndSupportsEncoders` | `REQ-STP-006` | Planned |
| `[TST-STP-006.3]` | Build Contract | `testPromoVideoAssets_existAndMeetResolutionStandards` | `REQ-STP-006` | Planned |
| `[TST-STP-001]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Verified |
