# Stage 1 Analysis: ATT-5655 - Workout Photos Thumbnail Sizing & Expert Settings Tab Synchronization

**Ticket**: [ATT-5655](https://rainerblind.atlassian.net/browse/ATT-5655)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.9`  
**Branch**: `feature/ATT-5655`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During review of ATT-5474, two photo integration issues were identified:
1. `PhotoThumbnailSize` uses small fixed dimensions (`SMALL` 56dp, `MEDIUM` 76dp, `LARGE` 104dp), so even on *Groß* multiple small tiles sit side-by-side instead of displaying a full-width photo.
2. In `WorkoutDetailTabbedContent.kt` (line 686), a legacy fallback (`if (!sections.contains(PHOTOS) && detailPreferences.showPhotos)`) unconditionally renders `WorkoutPhotoStrip` under `DETAILS` even when `PHOTOS` is disabled in the tab config! Meanwhile, `WorkoutDetailTabConfig.DEFAULT_TABS` lacks `PHOTOS` in `tab_overview`, causing photos to show under "Unassigned Sections" while being forced on-screen.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Fixed vs. Responsive Widths)**: `WorkoutPhotoStrip.kt` applies `Modifier.size(thumbnailSize.dpSize.dp)` unconditionally. It ignores viewport constraints instead of calculating responsive item widths from container bounds.
* **RCA 2 (Legacy Fallback Overriding Tab Config)**: `WorkoutDetailTabbedContent.kt:686` renders `WorkoutPhotoStrip` under `DETAILS` whenever `PHOTOS` is absent from `sections`. This prevents athletes from hiding or reordering photos via `WorkoutDetailTabsConfigSheet`.
* **RCA 3 (Missing Default Section)**: `WorkoutDetailTabConfig.DEFAULT_TABS` (`tab_overview`) omits `PHOTOS` from its default section list (`[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`).
* **Independent Peer Review & Verification**:
  * Confirmed: `WorkoutDetailTabbedContent.kt:785` already handles `WorkoutDetailSectionType.PHOTOS` dynamically; line 686 is conflicting legacy fallback code violating Design Guidelines Section 3.2.
  * Confirmed: `WorkoutDetailTabsTest.kt:58` explicitly asserts `DEFAULT_TABS[0]` without `PHOTOS`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * `WorkoutPhotoStrip.kt`: Responsive widths via `BoxWithConstraints`:
    - `LARGE`: 1 photo per row width (`availableWidth`, height ~220dp, horizontally scrollable if $>1$).
    - `MEDIUM`: 2 photos side-by-side (`(availableWidth - 8.dp) / 2`, square height, scrollable if $>2$).
    - `SMALL`: 3–4 photos side-by-side (`(availableWidth - 24.dp) / 3.5f` ~80dp, square height).
  * `WorkoutDetailTabbedContent.kt`: Remove legacy fallback at line 686; render photos exclusively via `WorkoutDetailSectionType.PHOTOS` (line 785).
  * `WorkoutDetailTabConfig.kt`: Add `PHOTOS` to `DEFAULT_TABS` in `tab_overview` (`[DETAILS, PHOTOS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`).
  * Wire Migration (`v2|`): In `deserializeTabs`, auto-migrate legacy `v1|` configs missing `PHOTOS` by injecting `PHOTOS` into `tab_overview` (after `DETAILS`), while honoring explicit removal in `v2|`.
* **Out-of-Scope Non-Goals**:
  * No modification to `WorkoutSummary.kt` list card layout.
  * No SQLite schema migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-414.6` (*Workout Photos Configuration*) and `REQ-UI-408.1` (*Default Tab Sections*).
* **Historical Origin & Commit Trace**: `ATT-3920` (`c8939a3f`, configurable tabs), `ATT-43` (`5c9d9653`, photo strip), `ATT-5474` (`8aed20fc`, photo preferences & fixed dp sizes).
* **Root Reason for Existing Formulation**: The line 686 fallback was added as a temporary bridge for legacy tabs that predated `PHOTOS`, but it bypassed tab configuration and prevented user toggling.
* **Preservation of Core Invariants**: Map pinned at index 0; single authoritative configuration surface (Design Guidelines Sec 3.2); horizontal scroll in `LazyRow`; minimum 1 tab invariant.

---

## 5. Architectural Strategy & High-Level Solution
* In `WorkoutPhotoStrip.kt`: Wrap content in `BoxWithConstraints`. Compute `availableWidth = maxWidth - 32.dp`. Dynamically size cards for `LARGE` (full width), `MEDIUM` (half width), `SMALL` (~80dp).
* In `WorkoutDetailTabbedContent.kt`: Delete lines 686–698. Let line 785 be the sole rendering site.
* In `WorkoutDetailTabConfig.kt`: Add `PHOTOS` to `DEFAULT_TABS[0]`. Bump wire schema to `v2|`; migrate legacy `v1|` by appending `PHOTOS` after `DETAILS` in `tab_overview`.
* Update `WorkoutDetailTabsTest.kt`, `PhotoThumbnailSizeTest.kt`, and `WorkoutDetailTabbedContentTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Single authoritative configuration surface: tab config strictly controls section visibility.
  2. Bounded dimensions and memory safety: downsampled thumbnail decoding preserved.
  3. Seamless migration: existing users on `v1|` do not lose photos.
* **Risk Rating**: **LOW** — Self-contained Compose measurement and tab serialization logic with zero DB changes.
