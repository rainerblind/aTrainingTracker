# Stage 1 Analysis: ATT-5472 - PhotoTrackOverlayComposer & Share Footer: Supporter Symbol Only, Portrait Proportions & Stacked Sheet Buttons

**Ticket**: [ATT-5472](https://rainerblind.atlassian.net/browse/ATT-5472)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5472`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During Sprint 2026-45.6 review of ATT-30 on portrait photos (`att_30_portrait_photo_result.png`), five visual defects were identified: (1) footer renders a wide text badge (`[◆ SPONSOR]`) instead of a clean supporter symbol; (2) footer watermark text collides with/gets truncated by the badge on narrow widths; (3) workout metrics & title typography are small and float in the middle with a large empty gap above the footer; (4) GPS track silhouette covers 40% of portrait height, dominating the subject area; (5) `WorkoutShareBottomSheet` action buttons wrap awkwardly side-by-side ("Foto / auswählen").

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Footer Badge & Collision**: `ShareUtils.kt` (`drawFooter`) hardcodes `textSize = 38f` and draws a wide rounded text pill (`~260px`), colliding with `"Aufgezeichnet mit aTrainingTracker"` (34 chars) on portrait aspect ratios.
* **Vertical Gap & Tiny Typography**: `PhotoTrackOverlayComposer.kt` anchors title/metrics to `scrimTop = height * 0.65f` instead of anchoring above `footerTop`, leaving a dead space of >400px on portrait photos. Title (38f) and metric values (28f) lack visual prominence over the footer.
* **Track Bounding Box**: `trackRect` spans `height * 0.08f .. 0.48f` (40% of photo height), encroaching on central portrait photo subjects.
* **Sheet Button Wrapping**: `WorkoutShareBottomSheet.kt` splits actions into a 50/50 horizontal `RowScope` (`Modifier.weight(1f)`), violating `docs/design_guidelines.md` Rule 73 for multi-word labels.
* **Independent Peer Review & Verification**:
  * Audited `ShareUtils.kt:240-288`: Replacing text pills with `ic_supporter_emblem_*` (matching `SensorStatus.kt` / ATT-5496) frees ~180px of horizontal space. Dynamically scaling footer text prevents overlap across all 9 locales.
  * Audited `PhotoTrackOverlayComposer.kt:311-395`: Anchoring metrics block directly above `footerTop` (`footerTop - 24f * scaleRef`) eliminates the dead zone; enlarging title to ~48f and values to ~38f restores athletic prominence.
  * Audited `LauncherIconVariant.kt`: `LauncherIconVariant.fromTier(effectiveTier)` provides the tier-matched launcher icon.
  * Audited `WorkoutShareBottomSheet.kt:146-196`: Adopting full-width stacked buttons in `content` conforms strictly to Rule 73.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Replace footer supporter text pill with vector emblem (`ic_supporter_emblem_*`) in `ShareUtils.kt` (classic & photo share).
  * Auto-scale footer text to fit available width between tier-matched launcher icon and right supporter emblem.
  * Enlarge title, date, and metrics typography and anchor block compactly directly above the branding footer in `PhotoTrackOverlayComposer.kt`.
  * Constrain GPS track bounding box on portrait photos (`height > width`) to upper-right corner ($<22\%$ photo height).
  * Convert `WorkoutShareBottomSheet.kt` action buttons to stacked full-width layout (Rule 73).
* **Out-of-Scope Non-Goals**:
  * Modifying GPX/polyline decoding or equirectangular projection math (`calculateTrackProjection`).
  * Changing classic snapshot stitching logic in `combineWorkoutAndShare`.
* **Complexity Rating**: **2 SP (Standard)** (Pure Compose UI and Canvas styling improvements).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-447` (`REQ-UI-447.2` track bounds, `REQ-UI-447.3` metrics typography/layout, `REQ-UI-447.4` branding footer, `REQ-UI-447.5` stacked sheet buttons) and `REQ-UI-445.5` (Share Watermark). Targets `PhotoTrackOverlayComposer.kt`, `ShareUtils.kt`, `WorkoutShareBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-30` (`584284ff`, Photo Track Overlay), `ATT-1406`/`ATT-5397` (Supporter Watermarks), `ATT-5496` (`e5f5c754`, Vector Emblems & `LauncherIconVariant`), and `ATT-5399`/`ATT-5468` (Rule 73 Stacked Buttons).
* **Root Reason for Existing Formulation**: `REQ-UI-445.5` used text pills before vector emblems existed; `REQ-UI-447` was tuned for 16:9 landscape photos where 35% scrim and side-by-side buttons had adequate space.
* **Preservation of Core Invariants**: Equirectangular projection aspect ratio, WCAG AAA text contrast, backward compatibility for `combineWorkoutAndShare`, and 9-language localization parity are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Branding Footer (`ShareUtils.kt`)**: Query `effectiveTier`, render `LauncherIconVariant.fromTier(effectiveTier)` icon on left, draw `ic_supporter_emblem_*` on right, and scale text to fit horizontally without collision.
* **Photo Overlay Engine (`PhotoTrackOverlayComposer.kt`)**: Constrain portrait `trackRect` to $\le 22\%$ height. Anchor enlarged title & metrics block right above `footerTop`. Fade gradient scrim cleanly above title.
* **Share Sheet (`WorkoutShareBottomSheet.kt`)**: Stack primary ("Teilen") and secondary ("Foto auswählen") CTAs as full-width buttons in sheet content per Rule 73.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Equirectangular track geometry, WCAG AAA text contrast ($\ge 7.0:1$), zero regression on classic share, 9-language localization parity.
* **Risk Rating**: **LOW** (Presentation and Canvas styling improvements; zero database, sensor, or threading touchpoints).
