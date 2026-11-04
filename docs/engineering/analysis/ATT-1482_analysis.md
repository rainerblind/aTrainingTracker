# Stage 1 Analysis: ATT-1482 - App Icon Modernization: Android Adaptive Icon Support & Safe Zone Parity

**Ticket**: [ATT-1482](https://rainerblind.atlassian.net/browse/ATT-1482)  
**Sub-task**: [ATT-5345](https://rainerblind.atlassian.net/browse/ATT-5345) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.5`  
**Branch**: `feature/ATT-1482`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Sprint 2026-45.4 Review rejected commit `b404e0d0` (`n.i.O.`) due to 5 critical visual and architectural regressions:
1. *Broken Cyclist Back / Hip & Saddle*: Straight polygon segments (`M...L...`) with butt caps caused angular kinks; cyclist blue hip was misattributed as a black saddle, leaving an awkward black artifact behind the back.
2. *Detached Pushers & Thin Stems*: Visible gaps between colored pushers (Navy, Red, Green) and yellow necks; stems rendered too thin.
3. *Excessive Shrinking Inside Adaptive Mask*: Shrinking to $r=30.8\text{dp}$ left excessive white border inside launcher masks.
4. *Overwritten Full-Bleed `logo_512.png`*: `drawable/logo_512.png` and `images/logo_512.png` were overwritten with a padded version, corrupting in-app callers (`AppNavigationDrawer.kt`).
5. *Model Tier Requirement (Rule 71)*: Stage 4 requires Pro model execution for mathematical vector curve reconstruction.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause Analysis**:
  * *Cyclist Spline*: `Bilder/logo.fig:65` (`3 3 0 4 1 1 50 ...`) specifies a closed interpolated X-spline in royal blue (`#0000FF`, pen/fill=1) covering the hip/frame junction. `b404e0d0` converted it into a black polygon (`#000000`), mislabeling it "Bicycle Saddle".
  * *Pusher Stems*: `logo.fig` lines 33/35/39 overlap stem lines 58/60/62 by 45 xfig units. `ic_launcher_foreground.xml` introduced coordinate offsets and disproportionate stroke widths (3.79 vs 6.32), causing gaps.
  * *Scale Factor*: Overly conservative safe-zone shrinking ($r=30.8\text{dp}$ vs 33–35dp) degraded home screen presence.
  * *Asset Overwrite*: Replacing `drawable/logo_512.png` broke `AppNavigationDrawer.kt:351` (`activeVariant == STANDARD`).
* **Independent Peer Review & Verification**:
  * Verified master render `/home/rainer/Dropbox/workspace/TrainingTracker4/Bilder/logo.png` (`993x872` RGBA) and `drawable-xxxhdpi/logo_512.png` (`sha256: f84c6251...` intact across all density folders except `drawable/`).
  * Verified true vector depth hierarchy in `logo.fig`: Dial (70), Pushers/Stems (55), Frame/Wheels (55/56), Cyclist Back/Hip (58/50), Runner (50).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Reconstruct `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` with smooth cubic Béziers (`C`/`c`), flush pushers, and optimal safe-zone fill ($r=33\text{dp}..35\text{dp}$).
  * Restore full-bleed `app/src/main/res/drawable/logo_512.png` and `images/logo_512.png` from `drawable-xxxhdpi/logo_512.png`.
  * Update `AppIconAdaptiveContractTest.kt` to support Bézier commands (`C`/`S`/`Q`) and validate 33–35dp bounds.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT modify existing notification asset `res/drawable/logo.png`.
  * Zero deviation from authentic Cervélo P3C frame (`#FF0000`) and stopwatch colors (`#FFC700`, `#000090`, `#009000`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Status**: Net-new requirement only (`REQ-UI-444` / `TST-UI-444`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
1. **Mathematical Curve Reconstruction**: Translate `logo.fig` X-spline points into smooth cubic Bézier curves with round caps (`strokeLineCap="round"`), restoring the seamless royal blue cyclist torso/hip over the frame.
2. **Pusher Geometry Realignment**: Calibrate yellow stem stroke widths and eliminate gaps by overlapping button cap paths.
3. **Safe-Zone Rebalancing**: Center on `(54, 54)` with optimal scaling ($r \approx 33.5\text{dp}..34.5\text{dp}$) for bold launcher fill.
4. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**: Restoring full-bleed `drawable/logo_512.png` fixes `AppNavigationDrawer.kt` while preserving `@mipmap/ic_launcher` for system launchers.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% brand fidelity: authentic Cervélo P3C and stopwatch colors; zero clipping under Circle/Squircle masks.
  2. In-app UI callers (`AppNavigationDrawer`) display full-bleed `logo_512.png` without artificial padding.
* **Risk Rating**: **HIGH**. Classified as HIGH-RISK under Core Rule 7 / Rule 71 (`docs/design_guidelines.md` Sec 5.16); Stage 4 (`[Implementation]`) MUST be executed by `subagent_pro` (Pro model), with `subagent_flash` auditing Gate 4.
