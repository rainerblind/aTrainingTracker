# Stage 2: Requirement & Test Specification - ATT-5655: Workout Photos Thumbnail Sizing & Detail Tab Synchronization

**Ticket**: [ATT-5655](https://rainerblind.atlassian.net/browse/ATT-5655)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5655`  
**Requirement Mapping**: `REQ-UI-408` (`REQ-UI-408.1`, `REQ-UI-408.2`), `REQ-UI-414` (`REQ-UI-414.6`) | **Test Spec ID**: `TST-UI-371`, `TST-UI-377`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-408`, `REQ-UI-414`)

### 1.1 Problem Statement & Rationale
1. `PhotoThumbnailSize` fixed dimensions (`SMALL` 56dp, `MEDIUM` 76dp, `LARGE` 104dp) display multiple small tiles even for `LARGE` instead of full/half-width cards.
2. In `WorkoutDetailTabbedContent.kt:686`, a legacy fallback unconditionally rendered `WorkoutPhotoStrip` under `DETAILS` whenever `PHOTOS` was missing from `sections`, bypassing athlete tab customization. Furthermore, `DEFAULT_TABS[0]` (`tab_overview`) omitted `PHOTOS`.

### 1.2 Functional Specification
1. **Responsive Viewport Sizing (`REQ-UI-414.6`)**: In `WorkoutPhotoStrip.kt`, wrap content in `BoxWithConstraints(availableWidth = maxWidth - 32.dp)`:
   - `LARGE`: 1 full-width photo per row (`width = availableWidth`, `height = (availableWidth * 0.65f).coerceIn(180.dp, 260.dp)`, horizontally scrollable if $> 1$).
   - `MEDIUM`: 2 photos side-by-side per row (`width = (availableWidth - 8.dp) / 2`, square aspect ratio, horizontally scrollable if $> 2$).
   - `SMALL`: 3–4 photos side-by-side per row (`width = ((availableWidth - 24.dp) / 3.5f).coerceIn(60.dp, 100.dp)`, square aspect ratio).
2. **Detail Tab Synchronization (`REQ-UI-408.1`, `REQ-UI-408.2`)**:
   - `WorkoutDetailTabConfig.DEFAULT_TABS[0]` (`tab_overview`) default sections: `[DETAILS, PHOTOS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`.
   - Remove legacy fallback at `WorkoutDetailTabbedContent.kt:686-698`; `WorkoutDetailSectionType.PHOTOS` at line 785 is the sole rendering site.
   - Wire schema bump to `v2|`: `deserializeTabs` automatically injects `PHOTOS` after `DETAILS` in `tab_overview` when migrating legacy `v1|` strings that lack `PHOTOS` across all tabs. Explicit section assignments in `v2|` are strictly honored.

### 1.3 Acceptance Criteria & Invariants
- **AC-1 (Responsive Sizing)**: *Given* `WorkoutPhotoStrip`, *When* rendering with `LARGE`, *Then* photo cards span full available width; *When* `MEDIUM`, cards span half available width; *When* `SMALL`, cards span ~80dp.
- **AC-2 (Authoritative Tab Visibility)**: *Given* a workout detail tab configuration, *When* `PHOTOS` is absent from `sections`, *Then* `WorkoutPhotoStrip` is not rendered (legacy line 686 excised).
- **AC-3 (Default Tab Parity)**: *Given* a clean install or tab reset, *When* querying `DEFAULT_TABS[0]`, *Then* `PHOTOS` is present at index 1 following `DETAILS`.
- **AC-4 (Wire Migration Parity)**: *Given* a legacy `v1|` serialized string without `PHOTOS`, *When* `deserializeTabs` is invoked, *Then* `PHOTOS` is injected into `tab_overview`.
- **Invariants**: Single authoritative configuration surface (Design Guidelines Sec 3.2); Map pinned at index 0; downsampled bitmap decoding safety; 100% 9-language translation parity.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Amends `REQ-UI-408` and `REQ-UI-414`, targeting `WorkoutPhotoStrip.kt`, `WorkoutDetailTabConfig.kt`, and `WorkoutDetailTabbedContent.kt`.
- *Historical Origin & Commit Trace*: `ATT-3920` (`c8939a3f`), `ATT-43` (`5c9d9653`), `ATT-5474` (`8aed20fc`), `ATT-5655`.
- *Root Reason for Existing Formulation*: Line 686 was a temporary bridge for legacy tabs predating `PHOTOS`. Fixed dp sizes were initial placeholders.
- *Preservation of Core Invariants*: Single configuration surface preserved; tab validation rules intact; memory safety intact.

---

## 2. Test Specification (`TST-UI-371`, `TST-UI-377`)

- **TST-UI-371.1 (Default Tabs Overview with Photos)**: Verify `DEFAULT_TABS[0].sections` contains `PHOTOS` at index 1.
- **TST-UI-371.2 (Wire v1 to v2 Migration Contract)**: Verify deserializing a legacy `v1|` string without `PHOTOS` injects `PHOTOS` into `tab_overview`, while `v2|` respects omission.
- **TST-UI-377.1 (Responsive Viewport Calculation Contract)**: Verify responsive item width/height calculation across `LARGE`, `MEDIUM`, and `SMALL` sizes.
- **TST-UI-377.2 (Legacy Fallback Removal Verification)**: Verify `WorkoutDetailTabbedContent` does not render `WorkoutPhotoStrip` under `DETAILS` when `PHOTOS` is omitted from `sections`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-371.1` | Unit | `WorkoutDetailTabsTest.kt` | `REQ-UI-408.1` | Specified |
| `TST-UI-371.2` | Unit | `WorkoutDetailTabsTest.kt` | `REQ-UI-408.2` | Specified |
| `TST-UI-377.1` | Unit / UI | `WorkoutPhotoStripTest.kt` | `REQ-UI-414.6` | Specified |
| `TST-UI-377.2` | Unit / Contract | `WorkoutDetailTabbedContentTest.kt` | `REQ-UI-408.1` | Specified |
