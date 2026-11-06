# Stage 3: Implementation Plan - ATT-5474: Workout Photos in Advanced Settings (WorkoutMasksAndCardsSection)

**Ticket**: [ATT-5474](https://rainerblind.atlassian.net/browse/ATT-5474)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5474`  
**Requirement Mapping**: `REQ-UI-414` (*Workout Cards & Details Settings Streamlining*) | **Test Mapping**: `TST-UI-377`, `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
* In *Experten-Einstellungen -> Trainingsmasken & Karten* (`WorkoutMasksAndCardsSection.kt`), athletes customize section order, visibility, and display modes, but the Workout Photos feature (ATT-43) lacks configuration controls.
* `WorkoutPhotoStrip` is currently hardcoded outside the dynamic section loop in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`.
* Solution integrates `PHOTOS` into `WorkoutSectionType`, exposes 3 settings (`showPhotos`, `showPhotoAddButton`, `photoThumbnailSize`), modularizes UI into `WorkoutPhotoPreferencesComponents.kt` preserving the strict < 400 lines threshold (`TST-UI-377.5`), and moves photo strip into the dynamic section order loop.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-414.6` (Workout Photos Configuration & Dynamic Summary Slotting).
* **Test Mapping**: `TST-UI-377.1` (Tab availability), `TST-UI-377.2`–`.5` (Matrix layout, reorder, modularity < 400 lines), `TST-UI-377.7` (Photos settings contract), `TST-UI-377.8` / `TST-LOC-001` (9-language parity).

---

## 3. System Invariants & Preserved Behavior
1. **Modularity Constraint (`TST-UI-377.5` / `REQ-PRO-011`)**: `WorkoutMasksAndCardsSection.kt` must remain strictly < 400 lines (current: 394 lines) by offloading photo sub-settings to `WorkoutPhotoPreferencesComponents.kt`.
2. **Self-Healing Fallback**: `WorkoutSectionType.fromSerializedString()` and `WorkoutDetailTabConfig.deserializeTabs()` safely append missing `PHOTOS` enums without data loss or crashes.
3. **Photo Ingestion & Viewer Invariance**: SQLite `workout_photos` table, image decoding, EXIF time filtering (`ATT-5475`), and full-screen preview activity remain 100% untouched.

---

## 4. Proposed Architectural Changes
* **Model & Preferences**: Define `PhotoThumbnailSize` enum (`SMALL` 56dp, `MEDIUM` 76dp, `LARGE` 104dp). Add `showPhotos`, `showPhotoAddButton`, `photoThumbnailSize` to `WorkoutCardSectionPreferences` and `WorkoutDetailPreferences` with DataStore flows in `MyPreferenceManager.kt`.
* **Section Enums & Tab Parity**: Add `PHOTOS` to `WorkoutSectionType` (`DEFAULT_ORDER`) and `WorkoutDetailSectionType` with bidirectional conversion and availability checking.
* **Modular UI Components**: Create `WorkoutPhotoPreferencesComponents.kt` containing `PhotoThumbnailSizeSelector` and `PhotoCardSubSettings` with Material 3 `SingleChoiceSegmentedButtonRow` and `SubordinateTuningToggleItem`.
* **Dynamic Rendering**: Move `WorkoutPhotoStrip` into `workoutSectionsOrder.forEach` in `WorkoutSummary.kt` and support `WorkoutDetailSectionType.PHOTOS` in `WorkoutDetailTabbedContent.kt`. Parameterize `WorkoutPhotoStrip.kt` with `showAddButton` and `thumbnailSize`.
* **UI Consistency**: Reuses `SubordinateTuningToggleItem`, `SingleChoiceSegmentedButtonRow`, `SegmentedButtonDefaults.itemShape`, `MaterialTheme.colorScheme.primary`, and 48dp minimum touch targets. Zero one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Model, Enums & Preference Management
* Files: `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutPhotoPreferencesComponents.kt`
* Add `PhotoThumbnailSize` enum, add `PHOTOS` to `WorkoutSectionType` and `WorkoutDetailSectionType`, add DataStore keys and preference fields.

### Step 2: Modular Photo Settings UI in Settings Category
* Files: `WorkoutPhotoPreferencesComponents.kt`, `WorkoutMasksAndCardsSection.kt`
* Implement `PhotoThumbnailSizeSelector` and `PhotoCardSubSettings` in companion file; hook `PHOTOS` row and sub-settings in `WorkoutMasksAndCardsSection.kt` (ensuring line count stays < 380 lines).

### Step 3: Parameterize Photo Strip & Dynamic Section Slotting
* Files: `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`
* Add `showAddButton` and `thumbnailSize` to `WorkoutPhotoStrip`; move strip into dynamic loop in `WorkoutSummary.kt` under `WorkoutSectionType.PHOTOS`.

### Step 4: 9-Language Localization
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Add all 6 new photo settings string resources with 100% translation parity.

### Step 5: Test Updates & Verification
* Files: `WorkoutSectionReorderContractTest.kt`, `WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutDetailTabsTest.kt`
* Update expected section counts/orders to 10 items; assert `PhotoThumbnailSize`, modularity (< 400 lines), and 9-language parity.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests, verify line count constraint, verify requirement governance, followed by clean-room suite in Stage 5.
* **Rollback**: Clean git branch isolation on `feature/ATT-5474` allows complete rollback if needed.
