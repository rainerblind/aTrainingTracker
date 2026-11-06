# Stage 1 Analysis: ATT-5475 - Workout Photo Picker: Pre-Filter Photos Taken During Workout Time Window with 'Show All Photos' Button

**Ticket**: [ATT-5475](https://rainerblind.atlassian.net/browse/ATT-5475)  
**Sub-task**: [ATT-5555](https://rainerblind.atlassian.net/browse/ATT-5555) (`[Analysis]`)  
**Parent Epic**: None (*Standalone Feature*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Branch**: `feature/ATT-5475`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In `ATT-43`, tapping "Add Photo" on a workout launches Android's generic system photo picker (`PickMultipleVisualMedia`), displaying the athlete's entire media library. Athletes must manually search through thousands of photos to find pictures taken during a ride or run. The athlete requested that tapping "Add Photo" should pre-filter and display only photos taken chronologically during the workout's time window, with a prominent fallback button to browse all photos if desired.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current Implementation Gap**: `WorkoutPhotoStrip.kt` (lines 83–89, 137–140) binds the "Add Photo" tile directly to `ActivityResultContracts.PickMultipleVisualMedia()`, bypassing temporal filtering.
* **Independent Peer Review & Verification**:
  * *MediaStore Temporal Filtering*: Verified `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` supports querying `DATE_TAKEN` (epoch ms) and `DATE_ADDED` (epoch sec). The target interval is `[workoutStartMs - toleranceMs .. workoutEndMs + toleranceMs]`. In `WorkoutPhotoManager.kt:248`, a 5-minute (`300_000L` ms) tolerance is already canonical for trackpoint interpolation.
  * *Permission Constraints*: Verified `AndroidManifest.xml` currently lacks media permissions. Querying `MediaStore` requires `READ_MEDIA_IMAGES` (API 33+) / `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+) or `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`). If permission is denied, the app falls back seamlessly to `PickMultipleVisualMedia` (which requires zero permissions).
  * *UI Layout & Rule 73*: Verified `docs/design_guidelines.md` Sec 5.8: `AppModalBottomSheet` hosting a `LazyVerticalGrid` must specify `scrollable = false` to prevent infinite-height Compose measurement crashes. Sec 5.3 (`Rule 73`): The German button *"Alle Bilder anzeigen"* (20 chars) requires stacked full-width action buttons.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Guardrail
* **In-Scope Goals**:
  1. Declare `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`, and `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`) in `AndroidManifest.xml`.
  2. Implement `WorkoutPhotoManager.queryWorkoutTimePhotos(context, startMs, endMs, toleranceMs = 300_000L)` querying `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`.
  3. Create `WorkoutTimePhotoPickerSheet` using `AppModalBottomSheet(scrollable = false)` hosting a multi-select `LazyVerticalGrid` with thumbnail previews and timestamps.
  4. Implement `Rule 73` stacked action buttons: Primary CTA "Hinzufügen (<count>)", Secondary CTA *"Alle Bilder anzeigen"* (`PickMultipleVisualMedia`), and automatic fallback on denied permission or 0 matching photos.
  5. Symmetrical 9-language localization parity across all supported locales.
* **Out-of-Scope Non-Goals**: In-app camera capture; image cropping/filters; modifying SQLite schema v27; photo summary settings toggles (reserved for `ATT-5474`).
* **Complexity Audit**: 3 SP (Standard multi-component feature spanning permissions, ContentResolver queries, and Compose bottom sheet grid integration).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new sub-requirement `REQ-UI-448.6` (Workout-Time Pre-Filtered Photo Picker & All Photos Fallback), amending `REQ-UI-448` (*Workout Photos Strip, Viewer, and Map Pins*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-43` (commit `5c9d9653568e`), amended by `ATT-5479` (commit `4f7b87364b4a`).
* **Root Reason for Existing Formulation**: ATT-43 focused on SQLite persistence, EXIF parsing, and initial photo strip rendering using Android's system picker, deferring in-app time-filtered queries.
* **Preservation of Core Invariants**: System `PickMultipleVisualMedia` remains unconditionally available as secondary CTA and fallback; existing attached photos and database schema v27 remain invariant; memory-safe downsampled decoding prevents OOM.

---

## 5. Architectural Strategy & High-Level Solution
* **Query Layer**: Add `WorkoutPhotoManager.queryWorkoutTimePhotos` querying `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with selection `(DATE_TAKEN BETWEEN ? AND ?) OR ((DATE_TAKEN IS NULL OR DATE_TAKEN <= 0) AND DATE_ADDED BETWEEN ? AND ?)`.
* **Runtime Permission & Fallback Flow**: When clicking "Add Photo", check/request media permission. If granted and photos exist, open `WorkoutTimePhotoPickerSheet`. If denied or 0 photos found, trigger `PickMultipleVisualMedia` directly.
* **Composable Sheet**: `WorkoutTimePhotoPickerSheet` (`AppModalBottomSheet(scrollable = false)`) hosting a 3-column `LazyVerticalGrid` with selection checkboxes and `Rule 73` stacked buttons.
* **Architecture Docs (`ATT-5431`)**: Update `docs/architecture.md` Section 3.2 to document `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` runtime permissions.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. System photo picker remains accessible at all times; athletes are never blocked if permission is denied.
  2. Memory safety: Downsampled thumbnail loading via `PhotoTrackOverlayComposer.decodeSampledBitmap` avoids OOM.
  3. No modal unmounting or pager state disruption (`Rule 47` / `Rule 73`).
  4. Symmetrical 9-language localization parity without inline decorative emojis.
* **Risk Rating**: **MEDIUM** (Involves Android runtime permissions, ContentResolver MediaStore queries, and Compose grid bottom sheet layout; clean fallback eliminates blocker risk).
