# Stage 2: Requirement & Test Specification - ATT-2274: Produce Google Play Store Promo Video & Feature Highlights

**Ticket**: [ATT-2274](https://rainerblind.atlassian.net/browse/ATT-2274)  
**Sub-task**: `[Req & Test Spec]` ([ATT-5366](https://rainerblind.atlassian.net/browse/ATT-5366))  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.5`  
**Requirement Mapping**: `REQ-STP-006`  
**Test Spec ID**: `TST-STP-006`  
**Branch**: `feature/ATT-2274`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-STP-006)

### 1.1 Scope & Functional Clauses
1. **5-Scene Storyboard (`REQ-STP-006.1`)**: Maintain timestamped storyboard in `docs/store_presence/promo_video_storyboard.md` (0:00–1:00) covering Hook & Cockpit, Sensors/Gear, Navigation/Climbs, Clustering/Physiology, and Privacy/Export.
2. **Handlebar Mount Centering & Cockpit Geometry (`REQ-STP-006.2`)**: The procedural background in `tools/generate_promo_video.py` SHALL center the carbon stem, clamp, and dual out-front mount arms directly behind and beneath the smartphone at $X = 680$ with realistic carbon styling and calibrated B-roll crop coordinates.
3. **Verified Screenshot Mapping & Unique Frames (`REQ-STP-006.3`)**: Map all 5 scenes strictly to genuine verified screenshots from `ATT-4853`. All 5 rendered scene frames (`scene_01_cockpit.png`..`scene_05_privacy_export.png`) MUST have distinct SHA-256 hashes (zero duplicate files) and be visually verified via `read_file` (`Rule 71`).
4. **1080p MP4 Video & Contract Verification (`REQ-STP-006.4`)**: Output 60s 1080p ($1920 \times 1080$) video to `docs/store_presence/video/att_promo_showcase.mp4` verified by JUnit 4 contract test `PromoVideoAssetsContractTest.kt`. YouTube/Play Console publishing is documented as a Human Prerequisite (`Rule 22`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-STP-006` (targeting `tools/generate_promo_video.py`, `docs/store_presence/video/`, `PromoVideoAssetsContractTest.kt`).
* **Historical Origin & Commit Trace**: Sprint 2026-45.4 (`ATT-2274`, commits `f0be0abd`, `cf412745`).
* **Root Reason for Existing Formulation**: Sprint 2026-45.4 placed the procedural stem at $X = 960$ while the phone was at $X = 680$ (leaving the phone floating), and ingested unverified screenshots leading to an `n.i.O.` rating in Sprint Review.
* **Preservation of Core Invariants**: Centering the cockpit at $X = 680$, binding scenes to verified ATT-4853 screenshots, and enforcing SHA-256 frame uniqueness and visual inspection restores authentic athletic presentation without touching runtime app code.

### 1.2 Acceptance Criteria (Given-When-Then)
* **AC-1 (Centered Cockpit Mount)**: *Given* `tools/generate_promo_video.py`, *When* rendered, *Then* the out-front carbon mount is symmetrically centered at $X = 680$ directly supporting the phone.
* **AC-2 (Frame Uniqueness & Authenticity)**: *Given* the 5 generated scene frames, *When* hashed and visually inspected via `read_file`, *Then* all 5 have unique SHA-256 hashes and display genuine target screens.
* **AC-3 (CI Contract Verification)**: *Given* `PromoVideoAssetsContractTest.kt`, *When* run via Gradle, *Then* storyboard, script, MP4, and frame assertions pass at 100%.

---

## 2. Test Specification (TST-STP-006)
* **TST-STP-006.1 (Storyboard Structure Audit)**: Verifies `promo_video_storyboard.md` covers 5 scenes across 0:00–1:00.
* **TST-STP-006.2 (Generator Script & Centered Cockpit Test)**: Verifies `generate_promo_video.py` centers mount at $X = 680$ and runs with exit code 0.
* **TST-STP-006.3 (Video Asset & Frame Uniqueness Contract)**: Verifies `att_promo_showcase.mp4` exists ($>100\text{ KB}$), 5 scene frames are $1920 \times 1080$, unique SHA-256, and visually verified.
* **TST-STP-006.4 (Clean-Room Build Contract)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PromoVideoAssetsContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STP-006.1` | Build Contract | `testPromoVideoStoryboard_existsAndValidatesScenes` | `REQ-STP-006` | Specified |
| `TST-STP-006.2` | Build Contract | `testGeneratePromoVideoScript_existsAndSupportsEncoders` | `REQ-STP-006` | Specified |
| `TST-STP-006.3` | Build Contract | `testPromoVideoAssets_existAndMeetResolutionStandards` | `REQ-STP-006` | Specified |
| `TST-STP-006.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
