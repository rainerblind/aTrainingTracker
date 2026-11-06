# Stage 1 Analysis: ATT-5474 - [Feature] Workout Photos in Advanced Settings (WorkoutMasksAndCardsSection): Configure Show Photos, Show Add-Button & Thumbnail Size

**Ticket**: [ATT-5474](https://rainerblind.atlassian.net/browse/ATT-5474)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.8`  
**Branch**: `feature/ATT-5474`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
* **Missing Settings Entry**: In *Experten-Einstellungen -> Trainingsmasken & Karten* (`WorkoutMasksAndCardsSection.kt`), athletes can customize visibility, ordering, and sub-modes for all workout summary cards and detail tabs, but the Workout Photos feature (ATT-43) is entirely unconfigurable.
* **Hardcoded UI Placement**: `WorkoutPhotoStrip` is unconditionally rendered outside the dynamic section loop in `WorkoutSummary.kt` (lines 254–269) and `WorkoutDetailTabbedContent.kt` (lines 686–696), preventing athletes from hiding photo strips, disabling the prominent "+" add tile, or customizing thumbnail preview size.
* **User Feedback (Sprint 2026-45.6 Review)**: Athletes request 3 granular photo preferences: (1) Toggle Photos visibility (`showPhotos`), (2) Toggle "Add Photo" tile (`showPhotoAddButton`), and (3) Thumbnail preview size (`photoThumbnailSize`: Small, Medium, Large).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: ATT-43 introduced photos as an MVP feature directly anchored above the section loop without updating `WorkoutSectionType`, `WorkoutCardSectionPreferences`, `WorkoutDetailPreferences`, or DataStore preference schemas.
* **Independent Peer Review & Verification**:
  * *Strict File Size Limit (`TST-UI-377.5` / `REQ-PRO-011`)*: Verified `WorkoutMasksAndCardsSection.kt` is currently 394 lines. Inlining photo controls will break the strictly < 400 lines threshold. Modularizing thumbnail size and photo sub-settings into `WorkoutPhotoPreferencesComponents.kt` keeps both files safely < 350 lines.
  * *Dynamic Section Reordering*: Verified `WorkoutSectionType.kt`. Adding `PHOTOS` enables athletes to reorder photos in summary cards via `DEFAULT_ORDER` and self-healing serialization `fromSerializedString()`.
  * *Bidirectional Detail Tab Mapping*: Verified `WorkoutDetailTabsTest.kt` line 100. Adding `PHOTOS` to `WorkoutSectionType` requires mapping to `WorkoutDetailSectionType.PHOTOS` and `WorkoutDetailSectionAvailability.hasPhotos`.
  * *Strip Parameterization*: Verified `WorkoutPhotoStrip.kt`. Fixed `76.dp` dimensions and static "+" tile must accept `showAddButton: Boolean` and `thumbnailSize: PhotoThumbnailSize`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `PHOTOS` to `WorkoutSectionType` and `WorkoutDetailSectionType` with bidirectional mapping and self-healing serialization.
  2. Add `showPhotos` (default `true`), `showPhotoAddButton` (default `true`), and `photoThumbnailSize` (default `MEDIUM`) to `WorkoutCardSectionPreferences`, `WorkoutDetailPreferences`, and DataStore flows in `MyPreferenceManager.kt`.
  3. Integrate `PHOTOS` row into the collapsible summary cards table in `WorkoutMasksAndCardsSection.kt`, displaying subordinate "Add Button" toggle and `PhotoThumbnailSize` selector when `showPhotos == true`.
  4. Move `WorkoutPhotoStrip` into the dynamic section order loop in `WorkoutSummary.kt` and parameterize `WorkoutPhotoStrip.kt`.
  5. Ensure 9-language translation parity in `values*/strings.xml`.
* **Out-of-Scope Non-Goals**:
  * No modification to SQLite photo schemas (`workout_photos` table) or URI decoding logic.
  * No modification to full-screen photo viewer (`WorkoutPhotoPreviewActivity`) or overlay sharing.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-414` (Workout Cards & Details Settings Streamlining) & `REQ-UI-448` (Workout Photos Integration). Targets: `WorkoutMasksAndCardsSection.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `strings.xml`.
* **Historical Origin & Commit Trace**: `a8e5e356` (ATT-4043, `REQ-UI-414` Streamline WorkoutMasksAndCardsSection), `5c9d9653` (ATT-43 Workout Photos), `c82c8248` (ATT-5475 Workout Photo Time Picker).
* **Root Reason for Existing Formulation**: ATT-43 focused on media ingestion and photo storage, hardcoding photo strips before configuration requirements were defined. `WorkoutMasksAndCardsSection.kt` had a 9-item fixed table.
* **Preservation of Core Invariants**: Modularity constraint (< 400 lines) preserved via `WorkoutPhotoPreferencesComponents.kt`; default fallback self-healing preserves backward compatibility; existing photo persistence and viewer contracts remain untouched; 100% 9-language translation parity.

---

## 5. Architectural Strategy & High-Level Solution
* **Preferences & Model**: Introduce `enum class PhotoThumbnailSize(val dpSize: Int, val labelRes: Int)` (`SMALL` 56dp, `MEDIUM` 76dp, `LARGE` 104dp). Expose preferences in `WorkoutCardSectionPreferences` and `WorkoutDetailPreferences`.
* **Modular UI Components**: Create `WorkoutPhotoPreferencesComponents.kt` in `categories/` containing `PhotoThumbnailSizeSelector` (3-segment row) and subordinate toggle row to keep `WorkoutMasksAndCardsSection.kt` strictly < 400 lines.
* **Summary Card Dynamic Slotting**: Move `WorkoutPhotoStrip` in `WorkoutSummary.kt` into the `workoutSectionsOrder.forEach` loop under `WorkoutSectionType.PHOTOS`, gated on `preferences.showPhotos && (photos.isNotEmpty() || preferences.showPhotoAddButton)`.
* **Strip Configuration**: Extend `WorkoutPhotoStrip` with `showAddButton: Boolean` and `thumbnailSize: PhotoThumbnailSize`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `WorkoutMasksAndCardsSection.kt` strictly < 400 lines (`REQ-PRO-011` / `TST-UI-377.5`).
  2. Backward compatibility & self-healing fallback in `WorkoutSectionType.fromSerializedString` and `WorkoutDetailTabConfig.deserializeTabs`.
  3. Zero database schema migrations or SQLite alterations.
  4. 100% 9-language translation parity across all resource files (`TranslationParityTest`).
* **Risk Rating**: **LOW** (Standard UI configuration and layout slotting without background service, sensor telemetry, or database schema risks).
