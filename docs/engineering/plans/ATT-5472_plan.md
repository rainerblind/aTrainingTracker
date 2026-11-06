# Stage 3: Implementation Plan - ATT-5472: PhotoTrackOverlay Supporter Symbol, Portrait Proportions & Stacked Share Buttons

**Ticket**: [ATT-5472](https://rainerblind.atlassian.net/browse/ATT-5472)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5472`  
**Requirement Mapping**: `REQ-UI-447` (`REQ-UI-447.2`, `REQ-UI-447.3`, `REQ-UI-447.4`, `REQ-UI-447.5`), `REQ-UI-445.5`  
**Test Mapping**: `TST-UI-447` (`TST-UI-447.1`–`.6`), `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Sprint 2026-45.6 review of ATT-30 (`att_30_portrait_photo_result.png`) revealed 5 defects on portrait photos: (1) footer renders wide text pill badge (`[◆ SPONSOR]`) instead of a supporter symbol; (2) footer text collides with/gets truncated by the badge on narrow widths; (3) workout metrics & title typography are small and hover with a large empty gap above the footer; (4) GPS track silhouette spans 40% photo height, dominating central scenery; (5) `WorkoutShareBottomSheet` action buttons wrap awkwardly side-by-side ("Foto / auswählen").

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-447.2` (Portrait track bounds $\le 22\%$), `REQ-UI-447.3` (Enlarged typography & bottom-anchored metrics), `REQ-UI-447.4` (Standalone emblem & auto-scaling text), `REQ-UI-447.5` (Rule 73 stacked buttons), `REQ-UI-445.5` (Watermark signature).
* **Test Mapping**: `TST-UI-447.1` (Track projection & bounds), `TST-UI-447.2` (Typography & WCAG AAA contrast), `TST-UI-447.3` (Footer emblem & auto-scaling), `TST-UI-447.4` (Stacked buttons), `TST-UI-447.5` (9-locale parity), `TST-UI-447.6` (Regression).

---

## 3. System Invariants & Preserved Behavior
1. **Aspect Ratio & Geometry**: Cosine-latitude projection ($\Delta x = \Delta\lambda \cdot \cos(\phi_{\text{mean}})$, $\Delta y = -\Delta\phi$) remains 100% distortion-free.
2. **WCAG AAA Contrast**: All white metrics typography over dark translucent gradient scrim maintains $\ge 7.0:1$ contrast.
3. **Classic Share Backward Compatibility**: `combineWorkoutAndShare` and non-workout callers remain completely intact.
4. **9-Language Localization**: Zero missing strings or formatting defects across all 9 supported locales.

---

## 4. Proposed Architectural Changes
* **Branding Footer (`ShareUtils.kt`)**: Query `ProFeatureManager.effectiveTier`, render `LauncherIconVariant.fromTier` launcher icon on left, draw standalone `variant.emblemRes` (`ic_supporter_emblem_*`) on right when `isPro`, and dynamically scale `textPaint.textSize` if text width exceeds available horizontal space.
* **Photo Overlay Engine (`PhotoTrackOverlayComposer.kt`)**: Constrain portrait (`height > width`) `trackRect` to upper-right quadrant ($\le 22\%$ height). Enlarge title (50f), date (26f), and stat values (38f). Anchor metrics block compactly directly above `footerTop` (`footerTop - 20f * scaleRef`) and smoothly fade scrim above title.
* **Share Sheet (`WorkoutShareBottomSheet.kt`)**: Move action buttons from `actions` into `content` as vertically stacked full-width buttons (`Button` "Teilen" above `OutlinedButton` "Foto auswählen") per Rule 73.
* **UI Consistency**: Conforms to `docs/design_guidelines.md` Rule 73 (Stacked Full-Width Action Buttons), Section 5.2 spacing (8dp/16dp), Section 5.3 shapes (`RoundedCornerShape(12.dp)`), and Section 5.5 iconography.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Branding Footer Supporter Emblem & Text Scaling (`ShareUtils.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/helpers/ShareUtils.kt`
* Draw `variant.emblemRes` on right without text pill; load tier-matched launcher icon; auto-scale text to fit available width.

### Step 2: Portrait Track Bounding Box & Typography Sizing (`PhotoTrackOverlayComposer.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/helpers/PhotoTrackOverlayComposer.kt`
* Constrain `trackRect` on portrait photos; enlarge typography; anchor metrics block directly above `footerTop`.

### Step 3: Rule 73 Stacked Full-Width Buttons (`WorkoutShareBottomSheet.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheet.kt`
* Place full-width stacked `Button` and `OutlinedButton` at bottom of preview sheet content with 8dp spacing.

### Step 4: Unit & Contract Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/helpers/PhotoTrackOverlayComposerTest.kt`, `.../ui/map/WorkoutShareBottomSheetContractTest.kt`, `.../helpers/ShareUtilsTest.kt`
* Add tests verifying portrait bounding box, bottom-anchored positioning, emblem rendering, and stacked button layout.

### Step 5: Test Execution & Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.helpers.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit & contract tests during Stage 4; on-device ADB screenshot inspection and clean-room regression in Stage 5.
* **Rollback**: Clean branch isolation on `feature/ATT-5472` enables complete zero-risk revert without affecting `sprint/2026-45.8`.
