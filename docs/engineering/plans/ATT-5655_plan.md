# Stage 3: Implementation Plan - ATT-5655: Workout Photos Thumbnail Sizing & Detail Tab Synchronization

**Ticket**: [ATT-5655](https://rainerblind.atlassian.net/browse/ATT-5655)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5655`  
**Requirement Mapping**: `REQ-UI-408` (`REQ-UI-408.1`, `REQ-UI-408.2`), `REQ-UI-414` (`REQ-UI-414.6`)  
**Test Mapping**: `TST-UI-371` (`TST-UI-371.1`, `TST-UI-371.2`), `TST-UI-377` (`TST-UI-377.1`, `TST-UI-377.2`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `WorkoutPhotoStrip.kt`, static dimensions (`56/76/104.dp`) display small square tiles even on `LARGE`, rather than full-width or half-width cards. In `WorkoutDetailTabbedContent.kt:686`, a legacy fallback forces photos to render under `DETAILS` whenever `PHOTOS` is absent from the tab, bypassing user customization. Meanwhile, `DEFAULT_TABS[0]` (`tab_overview`) in `WorkoutDetailTabConfig.kt` omitted `PHOTOS`. ATT-5655 addresses responsive card sizing and establishes tab configuration as the single authoritative surface.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-408.1` (Default Tab Sections), `REQ-UI-408.2` (Wire Schema Migration), `REQ-UI-414.6` (Responsive Sizing)
* **Tests**: `TST-UI-371.1` (Default Tab Overview), `TST-UI-371.2` (v1-to-v2 Migration), `TST-UI-377.1` (Responsive Sizing), `TST-UI-377.2` (Fallback Removal)

---

## 3. System Invariants & Preserved Behavior
1. **Single Authoritative Surface**: Tab config strictly dictates section visibility in Workout Details (Design Guidelines Sec 3.2).
2. **Tab Validation Invariants**: Map pinned at index 0; minimum 1-tab fallback preserved.
3. **Upgrade Path Safety**: Existing `v1|` tab configurations in DataStore auto-migrate `PHOTOS` into `tab_overview` without photo loss.
4. **Memory Safety**: Downsampled thumbnail decoding (`maxDim = 240`) preserved.

---

## 4. Proposed Architectural Changes
* **Component 1 (`WorkoutPhotoStrip.kt`)**: Wrap `LazyRow` in `BoxWithConstraints(availableWidth = (maxWidth - 32.dp).coerceAtLeast(100.dp))`:
  - `LARGE`: `cardWidth = availableWidth`, `cardHeight = (availableWidth * 0.65f).coerceIn(180.dp, 260.dp)` (1 photo/row, scrollable if $>1$).
  - `MEDIUM`: `cardWidth = (availableWidth - 8.dp) / 2`, `cardHeight = cardWidth` (2 photos/row, scrollable if $>2$).
  - `SMALL`: `cardWidth = ((availableWidth - 24.dp) / 3.5f).coerceIn(60.dp, 90.dp)`, `cardHeight = cardWidth` (3–4 photos/row).
  - Size both `PhotoThumbnailCard` and Add Photo tile with these responsive dimensions.
* **Component 2 (`WorkoutDetailTabbedContent.kt`)**: Delete legacy fallback lines 686–698; line 785 becomes the sole rendering site.
* **Component 3 (`WorkoutDetailTabConfig.kt`)**: Add `PHOTOS` to `DEFAULT_TABS[0].sections` at index 1 (`[DETAILS, PHOTOS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`). Bump wire schema prefix to `SCHEMA_PREFIX = "v2|"`. In `deserializeTabs`, if input is `v1|` and lacks `PHOTOS`, inject `PHOTOS` after `DETAILS` in `tab_overview`. Preserve user-edited `v2|` configs.
* **UI Consistency (Rule 23)**: Reuses `WorkoutDetailTabbedContent.kt` and `WorkoutPhotoStrip.kt`; tokens: `surfaceVariant`, `onSurfaceVariant`, `RoundedCornerShape(8.dp)`; no new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Tab Config & Wire Migration (`REQ-UI-408.1`, `REQ-UI-408.2`)**: Add `PHOTOS` to `DEFAULT_TABS[0]`, bump to `v2|`, implement `v1|` self-healing in `WorkoutDetailTabConfig.kt`.
2. **Remove Conflicting Fallback (`REQ-UI-408.1`, `TST-UI-377.2`)**: Remove lines 686–698 under `DETAILS` in `WorkoutDetailTabbedContent.kt`.
3. **Responsive Thumbnail Widths (`REQ-UI-414.6`, `TST-UI-377.1`)**: Implement `BoxWithConstraints` responsive item sizing in `WorkoutPhotoStrip.kt`.
4. **Update Unit & Contract Tests**: Update `WorkoutDetailTabsTest.kt`, `WorkoutDetailTabbedContentTest.kt`, and `PhotoThumbnailSizeTest.kt`.
5. **Targeted Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit & contract tests, governance verification exit code 0.
* **Rollback**: Clean `git restore` on `feature/ATT-5655`.
