# Stage 1 Analysis: ATT-5473 - [Verbesserung] WorkoutPhotoStrip & Preview UI: Match Neutral Summary Styling (No Primary Blue), Fix Clipped Tile Text & Stack Preview Buttons

**Ticket**: [ATT-5473](https://rainerblind.atlassian.net/browse/ATT-5473)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.8`  
**Branch**: `feature/ATT-5473`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
* **Electric Blue Styling Clashes**: In `WorkoutSummary.kt`, telemetry sections (*Zeit aktiv*, *Distanz*, *Höhenmeter*, *Geschw.*, *Höhe*) use neutral `onSurfaceVariant` icons and divider lines, whereas `WorkoutPhotoStrip.kt` uses vibrant `colorScheme.primary` for the camera header icon and "Add Photos" tile, clashing with summary hierarchy.
* **Clipped Tile Label**: The "Add Photos" tile (`76.dp` square) renders `workout_photos_add` (*"Fotos hinzufügen"*) with `maxLines = 1`, which truncates to *"Fotos "* in German (and clips in other locales).
* **Cramped Side-by-Side Preview Actions**: `WorkoutPhotoPreviewBottomSheet.kt` places *"Foto löschen"* and *"Mit Track-Overlay teilen"* horizontally in a `RowScope`, violating Rule 73 / `docs/design_guidelines.md` for long localized CTAs.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: The initial ATT-43 implementation hardcoded `MaterialTheme.colorScheme.primary` across photo components and attempted single-line text within fixed-size square cards.
* **Independent Peer Review & Verification**:
  * *Screenshot Inspection (`att_43_current_view.png`)*: Verified attached user screenshot. The blue camera icon and blue "Add Photo" tile clash prominently against the grey telemetry rows; text inside the tile is visibly cut off as *"Fotos "*; no `HorizontalDivider` separates the photo section from neighboring rows.
  * *Code Audit (`WorkoutPhotoStrip.kt`)*: Lines 207, 251, 258 explicitly bind `MaterialTheme.colorScheme.primary` instead of `onSurfaceVariant`.
  * *Code Audit (`WorkoutPhotoPreviewBottomSheet.kt`)*: Lines 98–132 pack two multi-word action buttons inside `AppModalBottomSheet.actions` row, causing horizontal squishing on German text.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Replace `MaterialTheme.colorScheme.primary` in `WorkoutPhotoStrip.kt` with `onSurfaceVariant` for camera header icon, text, and add tile.
  2. Insert standard `HorizontalDivider` above `WorkoutPhotoStrip` in `WorkoutSummary.kt` matching neighboring sections.
  3. Render clean centered `AddPhotoAlternate` icon without text inside the add tile in `WorkoutPhotoStrip.kt` to eliminate text clipping.
  4. Stack full-width CTAs (*Share with Track-Overlay* and *Delete Photo*) vertically in `WorkoutPhotoPreviewBottomSheet.kt` per Rule 73.
* **Out-of-Scope Non-Goals**:
  * No modification to photo thumbnail sizes or settings toggles (established in ATT-5474).
  * No changes to SQLite schema, image downsampling, or time picker logic (`ATT-5475`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-448.1` (Photo Strip Layout) & `REQ-UI-448.2` (Preview Bottom Sheet). Targets: `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `WorkoutPhotoPreviewBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `5c9d9653` (`ATT-43`), `c82c8248` (`ATT-5475`), and `8aed20fc` (`ATT-5474`).
* **Root Reason for Existing Formulation**: ATT-43 introduced photos with Material 3 primary tinting and side-by-side action buttons as an initial layout draft.
* **Preservation of Core Invariants**: Photo click and add workflows, photo deletion confirmation dialog, and track overlay handoff remain 100% intact; 100% 9-language translation parity.

---

## 5. Architectural Strategy & High-Level Solution
* **Color & Header Harmonization**: Update `WorkoutPhotoStrip.kt` header icon and label to `MaterialTheme.colorScheme.onSurfaceVariant`. Add `HorizontalDivider` with 12dp horizontal padding above `WorkoutPhotoStrip` in `WorkoutSummary.kt`.
* **Centered Add Icon**: In `WorkoutPhotoStrip.kt`, remove the clipped single-line text composable and center `Icons.Default.AddPhotoAlternate` inside `OutlinedCard` with neutral styling and accessible `contentDescription`.
* **Rule 73 Stacked CTAs**: In `WorkoutPhotoPreviewBottomSheet.kt`, move actions into sheet content as stacked full-width buttons (`fillMaxWidth`) spaced by 8dp, placing primary Share CTA above secondary Outlined Delete CTA.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Seamless thumb touch targets ($\ge 48\text{dp}$) on all buttons and tiles.
  2. Zero regressions in photo addition, preview launch, or deletion flows.
  3. 100% clean-room test pass rate across `aftermath` and `design_system` suites.
* **Risk Rating**: **LOW** (Targeted Compose visual styling, color token updates, and layout refactoring without logic or data model changes).
