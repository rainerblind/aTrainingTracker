# Stage 5 Walkthrough: ATT-5474 — Workout Photos in Advanced Settings (WorkoutMasksAndCardsSection)

**Ticket**: [ATT-5474](https://rainerblind.atlassian.net/browse/ATT-5474)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5474`  
**Requirement Mapping**: `REQ-UI-414` | **Test Mapping**: `TST-UI-377`, `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary
Integrated Workout Photos configuration into *Experten-Einstellungen -> Trainingsmasken & Karten* (`WorkoutMasksAndCardsSection.kt`) and dynamically slotted `WorkoutPhotoStrip` into the workout summary and workout details section loops in compliance with `REQ-UI-414.6`, `TST-UI-377.7`, and ASPICE SWE.1/SWE.2. Defined `PhotoThumbnailSize` enum (`SMALL` 56dp, `MEDIUM` 76dp default, `LARGE` 104dp) and added DataStore preferences (`showPhotos`, `showPhotoAddButton`, `photoThumbnailSize`) to `WorkoutCardSectionPreferences` and `WorkoutDetailPreferences` in `MyPreferenceManager.kt`. Integrated `PHOTOS` into `WorkoutSectionType` (`DEFAULT_ORDER`) and `WorkoutDetailSectionType` with bidirectional enum mappings and data availability checks in `WorkoutDetailTabConfig.kt`. Extracted subordinate photo controls (`PhotoThumbnailSizeSelector`, `PhotoCardSubSettings`, `SummaryPhotoSubSettings`, `DetailPhotoSubSettings`) to companion file `WorkoutPhotoPreferencesComponents.kt`, preserving the strict `< 400` lines threshold in `WorkoutMasksAndCardsSection.kt` (391 lines). Parameterized `WorkoutPhotoStrip.kt` with `showAddButton` and `thumbnailSize` and moved it into the dynamic order loops in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`. Added 100% 9-language localization parity across all resource files.

---

## 2. Key Architectural Changes
| File / Component | Summary of Changes |
| :--- | :--- |
| `PhotoThumbnailSize.kt` | Created enum defining thumbnail presets: `SMALL` (56dp), `MEDIUM` (76dp, default), and `LARGE` (104dp) with string resource bindings (`REQ-UI-414.6`). |
| `MyPreferenceManager.kt` | Added DataStore keys, preference fields (`showPhotos`, `showPhotoAddButton`, `photoThumbnailSize`), fallback parser `parsePhotoThumbnailSize`, and reactive flows for summary cards and details (`REQ-UI-414.6`). |
| `WorkoutSectionType.kt` | Added `PHOTOS` enum entry and inserted into `DEFAULT_ORDER` at index 2 (preceding `MAP`) (`REQ-UI-414.6`). |
| `WorkoutDetailTabConfig.kt` | Added `PHOTOS` to `WorkoutDetailSectionType`, added bidirectional mappings (`toWorkoutDetailSectionType()`, `toWorkoutSectionTypeOrNull()`), and added `hasPhotos` check to `WorkoutDetailSectionAvailability` (`REQ-UI-414.6`). |
| `WorkoutPhotoPreferencesComponents.kt` | Created modular companion composables (`PhotoThumbnailSizeSelector`, `PhotoCardSubSettings`, `SummaryPhotoSubSettings`, `DetailPhotoSubSettings`) using Material 3 `SingleChoiceSegmentedButtonRow` and `SubordinateTuningToggleItem` (`REQ-UI-414.6`, `REQ-PRO-011`). |
| `WorkoutMasksAndCardsSection.kt` | Added `PHOTOS` feature row to summary reorder table, active card counter, and hooked summary/detail subordinate photo settings. Preserved file length constraint at 391 lines (strictly `< 400` lines, `TST-UI-377.5`). |
| `WorkoutPhotoStrip.kt` | Parameterized with `showAddButton` and `thumbnailSize`; skips rendering when `photos.isEmpty() && !showAddButton` (`REQ-UI-414.6`). |
| `WorkoutSummary.kt` | Slotted `WorkoutPhotoStrip` dynamically into `workoutSectionsOrder.forEach` under `WorkoutSectionType.PHOTOS` (`REQ-UI-414.6`). |
| `WorkoutDetailTabbedContent.kt` & `TrackOnMapScreen.kt` | Slotted `WorkoutPhotoStrip` under `WorkoutDetailSectionType.PHOTOS` and handled enum exhaustiveness (`REQ-UI-414.6`). |
| 9 `res/values*/strings.xml` | Added 6 new keys (`settings_workout_card_photos`, `settings_workout_photos_show_add_button`, `settings_workout_photos_thumbnail_size`, `settings_workout_photos_size_small`, `settings_workout_photos_size_medium`, `settings_workout_photos_size_large`) with 100% translation parity (`TST-LOC-001`). |

---

## 3. Verification & Governance Evidence
* **Tier 1 Targeted Unit Tests**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.photos.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"` -> `BUILD SUCCESSFUL` (100% pass rate).
* **Resource Compilation Verification**:
  `./gradlew processDebugResources` -> `BUILD SUCCESSFUL` (0 AAPT2 errors across all 9 locales).
* **Requirement Governance Audit**:
  `python3 tools/verify_requirement_governance.py` -> `PASS` (100% living documentation parity, Chesterton's fence audit verified).
* **File Length Constraint**:
  `WorkoutMasksAndCardsSection.kt` is **391 lines** (strictly `< 400` lines threshold per `REQ-PRO-011` / `TST-UI-377.5`).

---

## 4. Requirement & Test Traceability
| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-414.1` | `TST-UI-377.1` | `Verified` | `WorkoutDetailTabsTest` (authoritative tab availability) |
| `REQ-UI-414.2` | `TST-UI-377.2` | `Verified` | `WorkoutMasksAndCardsLayoutTest` (domain partitioning) |
| `REQ-UI-414.3` | `TST-UI-377.3` | `Verified` | `WorkoutMasksAndCardsLayoutTest` (collapsible reorder list) |
| `REQ-UI-414.4` | `TST-UI-377.4` | `Verified` | `WorkoutMasksAndCardsLayoutTest` (scoped zone selectors) |
| `REQ-UI-414.5` | `TST-UI-377.5` | `Verified` | `WorkoutMasksAndCardsLayoutTest` (line count 391 < 400 lines) |
| `REQ-UI-414.6` | `TST-UI-377.7` | `Verified` | `PhotoThumbnailSizeTest`, `WorkoutMasksAndCardsLayoutTest`, and dynamic slotting contract tests |
| `REQ-UI-414` | `TST-UI-377` | `Verified` | Full Tier 1 targeted suite & living documentation governance |
| `REQ-LOC-001` | `TST-LOC-001` | `Verified` | `TranslationParityTest` (100% parity across all 9 locales) |
