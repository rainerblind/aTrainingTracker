# Stage 1 Analysis: ATT-5656 - WorkoutShareBottomSheet: 3-Tier Photo Cascade Preselection

**Ticket**: [ATT-5656](https://rainerblind.atlassian.net/browse/ATT-5656)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.9`  
**Branch**: `feature/ATT-5656`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In `WorkoutShareBottomSheet.kt`, choosing "Foto mit Track & Daten" (`ShareMode.PHOTO_OVERLAY`) or clicking "Foto auswählen" in the preview currently launches Android's generic system photo picker (`PickVisualMedia`) immediately. While `attachedPhotos: List<Uri>` is already passed into `WorkoutShareBottomSheet` from all callers (`MapDetailLayout`, `WorkoutDetailTabbedContent`, `WorkoutSummary`), it is dead code. Athletes lack a stepped, context-aware preselection prioritizing photos directly attached to or captured during the activity.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: `WorkoutShareBottomSheet.kt` hardcodes `photoPickerLauncher.launch(...)` at lines 212 and 255. It lacks internal UI states to present workout-attached photos or time-window filtered photos prior to invoking the system picker.
* **Independent Peer Review & Verification**:
  * Confirmed: `attachedPhotos: List<Uri> = emptyList()` is declared on `WorkoutShareBottomSheet` (line 93) but never read or referenced in the composition body.
  * Confirmed: `MapDetailLayout.kt` (lines 936–947) already loads `attachedPhotos` asynchronously via `WorkoutRepository.getWorkoutPhotos(workoutData.id).map { it.uri }` and passes it to the sheet.
  * Confirmed: Time-window filtering infrastructure (`WorkoutPhotoManager.queryWorkoutTimePhotos(context, startMs, endMs)`) is fully operational (`ATT-5475`).
  * Invariant check: `WorkoutShareBottomSheetContractTest.kt` enforces Rule 73 stacked buttons in `PREVIEW` mode and strictly prohibits the `actions =` slot on `AppModalBottomSheet`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Implement 3-tier cascade inside `WorkoutShareBottomSheet.kt`:
    - **Tier 1 (`ATTACHED`)**: If `attachedPhotos.isNotEmpty()`, display a grid of attached photos, with buttons below to expand to Tier 2 (*"Alle während der Einheit aufgenommenen Fotos anzeigen"*) or Tier 3 (*"Alle Fotos anzeigen"*).
    - **Tier 2 (`TIME_WINDOW`)**: If `attachedPhotos.isEmpty()` (or user switches from Tier 1), query and display photos taken within `[startTimeMs - 5m .. endTimeMs + 5m]` with timestamps, with an option below to switch to Tier 3 (*"Alle Fotos anzeigen"*).
    - **Tier 3 (`ALL_PHOTOS`)**: If both tiers are empty, or user clicks "Alle Fotos anzeigen", launch `PickVisualMedia`.
  * Support switching between tiers and re-opening the cascade from the composed preview ("Foto auswählen").
  * Ensure 100% 9-language translation parity for new button/header strings.
* **Out-of-Scope Non-Goals**:
  * No modification to `PhotoTrackOverlayComposer.kt` drawing logic or branding footer.
  * No changes to `WorkoutTimePhotoPickerSheet.kt` (used by `WorkoutPhotoStrip` for attaching photos).
  * No database schema migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-447` (specifically `REQ-UI-447.5` and introduces `REQ-UI-447.6`, 3-Tier Photo Selection Cascade).
* **Historical Origin & Commit Trace**: `ATT-30` (`584284ff`, initial photo overlay), `ATT-43` (`5c9d9653`, attached photos & strip), `ATT-5472` (Rule 73 stacked buttons in preview), `ATT-5475` (`c82c8248`, time-window query).
* **Root Reason for Existing Formulation**: `PickVisualMedia` was initially wired directly as a temporary placeholder in ATT-30 before photo attachment (`ATT-43`) and MediaStore time querying (`ATT-5475`) were built.
* **Preservation of Core Invariants**: Option A (Map & Analytics snapshot) remains untouched; `initialPhotoUri != null` preview fast-path preserved; Rule 73 stacked buttons in preview preserved; safe downsampled bitmap decoding (`maxDim`) prevents OOM.

---

## 5. Architectural Strategy & High-Level Solution
* Introduce internal state management in `WorkoutShareBottomSheet.kt`:
  - `ShareSheetState`: `MODE_SELECTION` (Option A/B), `PHOTO_SELECTION` (Tier 1/2), `PREVIEW` (Overlay).
  - `PhotoTier`: `ATTACHED` (Tier 1), `TIME_WINDOW` (Tier 2).
* In `PHOTO_SELECTION`:
  - Render photo grid in a bounded-height container (`height(120.dp / 240.dp)`) using downsampled thumbnails.
  - Tapping any photo triggers sampled decoding and transitions to `PREVIEW`.
  - Stacked action buttons (Rule 73) allow seamless switching between Tier 1, Tier 2, and Tier 3 (`PickVisualMedia`).
* Tapping "Foto auswählen" from `PREVIEW` transitions back to `PHOTO_SELECTION` (Tier 1 if `attachedPhotos.isNotEmpty()`, else Tier 2/3).
* Add required localized strings to all 9 `strings.xml` files.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions to classic snapshot sharing (Option A).
  2. Bounded height for LazyGrids to avoid infinite-measurement crash in `AppModalBottomSheet`.
  3. Strict compliance with `WorkoutShareBottomSheetContractTest.kt` (no `actions =` slot, Rule 73 button order).
* **Risk Rating**: **LOW** — Purely UI state and presentation enhancement reusing existing `WorkoutPhotoManager` query methods and `PhotoTrackOverlayComposer`.
