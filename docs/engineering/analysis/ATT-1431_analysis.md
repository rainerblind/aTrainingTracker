# Engineering Analysis: ATT-1431

**Ticket**: [ATT-1431](https://atrainingtracker.atlassian.net/browse/ATT-1431): `[Verbesserung] Two more textsizes (even larger)`  
**Sprint**: `2026-39.3`  
**FixVersion**: `V4.9.38`  
**Status**: Stage 1 Analysis  
**Author**: Agent 1 (Pair Programming Assistant)  
**Date**: 2026-09-28  

---

## 1. Problem Statement & Motivation

During athletic activities with significant vibration or high glance velocity—such as road cycling, downhill mountain biking, and interval training—athletes mount their Android device onto bicycle handlebars, cockpit stems, or armbands. In these scenarios, minimal cockpit layouts dedicated to 1 or 2 high-priority metrics (e.g. Heart Rate, Pace, Power, or Cadence) require ultra-large, high-contrast numeric readouts that can be registered in milliseconds without squinting or leaning in.

Currently, the cockpit rendering system in [`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt) supports 7 discrete sizes up to `XHUGE` (100.sp). On modern high-resolution displays (such as the Google Pixel 10), cockpit tabs with only 1 or 2 sensor fields still have substantial unused vertical canvas space. Adding two even larger sizes—`XXHUGE` (140.sp) and `XXXHUGE` (180.sp)—enables giant single-field and double-field readouts tailored for glanceable readability under intense outdoor training conditions.

---

## 2. Scope Boundaries & User Motivation Grounding

- **In Scope**:
  1. Extend `enum class ViewSize` in [`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt) with `XXHUGE` and `XXXHUGE`.
  2. Implement proportional typography scaling in [`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt):
     - Primary metric value (`getSensorValueTextStyle`):
       - `XXHUGE`: `typography.displayLarge.copy(fontSize = 140.sp, fontWeight = FontWeight.SemiBold)`
       - `XXXHUGE`: `typography.displayLarge.copy(fontSize = 180.sp, fontWeight = FontWeight.SemiBold)`
     - Unit annotation (`getSensorUnitTextStyle`):
       - `XXHUGE`: `typography.headlineLarge.copy(fontSize = 56.sp)`
       - `XXXHUGE`: `typography.headlineLarge.copy(fontSize = 64.sp)`
     - Field Label (`labelStyle`):
       - `XXHUGE`: `MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp)`
       - `XXXHUGE`: `MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp)`
     - Filter Description (`filterStyle`):
       - `XXHUGE`: `MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)`
       - `XXXHUGE`: `MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)`
  3. String localization across all 9 supported locales:
     - `view_size_xxhuge` and `view_size_xxxhuge` in `values/strings.xml` and `values-{de,es,fr,it,ja,nl,pl,pt}/strings.xml`.
  4. Localized display name resolution in `ViewSize.getDisplayName(context: Context)`.
  5. Automatic UI exposure in [`EditSensorFieldViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt) and [`EditSensorFieldDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt).
  6. Unit testing in [`SensorFieldTypographyTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldTypographyTest.kt) and [`TranslationParityTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt).

- **Out of Scope**:
  - Altering existing typography metrics for sizes `XSMALL` through `XHUGE`.
  - Database schema alterations (SQLite table `TrackingViewsDatabaseManager.ROWS_TABLE` stores `ViewSize.name()` as `TEXT`).
  - Rewriting the cockpit grid layout algorithm in `SensorGridScreen.kt` or `TrackingTabsScreen.kt`.

---

## 3. Call Site Audit

All references to `ViewSize` and text size styling across the codebase were audited:

1. **Enum Definition**:
   - [`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt#L65-L67): `enum class ViewSize`.
2. **Display Name Helper**:
   - [`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt#L72-L83): `ViewSize.getDisplayName(context)`. Must include exhaustive branches for `XXHUGE` and `XXXHUGE`.
3. **Typography Resolution**:
   - [`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt#L88-L114): `getSensorValueTextStyle` and `getSensorUnitTextStyle`. Must map `XXHUGE` (140.sp / 56.sp) and `XXXHUGE` (180.sp / 64.sp) with `FontWeight.SemiBold` on value style.
   - [`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt#L132-L150): `labelStyle` (36.sp / 40.sp) and `filterStyle` (18.sp / 22.sp).
4. **Database & Serialization**:
   - [`TrackingViewsDatabaseManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java#L366): Column `VIEW_SIZE` is declared `TEXT`. Stores `ViewSize.name()`. No schema bump required.
   - [`TrackingViewsRepository.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt#L243-L245): Deserialization uses `ViewSize.valueOf(sizeString)` with `try-catch` fallback to `ViewSize.NORMAL`. Forward- and backward-compatible.
5. **Configuration UI**:
   - [`EditSensorFieldViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt#L93): `availableViewSizes: List<ViewSize> = ViewSize.values().toList()`. Automatically includes new enum entries.
   - [`EditSensorFieldDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt#L105): Maps `availableViewSizes` via `getDisplayName(context)` into the dropdown selector.
6. **Unit Tests**:
   - [`SensorFieldTypographyTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldTypographyTest.kt): Iterates over `ViewSize.values()`. Tests will immediately validate that all 9 sizes enforce `FontWeight.SemiBold`. Explicit assertions must be added for `XXHUGE` and `XXXHUGE`.
   - [`TranslationParityTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt): Validates parity across 9 languages.

---

## 4. Requirement Mapping

- **New Functional Requirement**: **`REQ-UI-181`** (*Ultra-Large Cockpit Typography Extensions (XXHuge and XXXHuge)*)
  - Extends: `REQ-UI-171` (*High-Contrast Cockpit Typography and Subtle Tile Grid*)
  - Mapped Test Case: **`TST-UI-133`** (*Ultra-Large ViewSize Typography & Configuration Verification*)

---

## 5. System Invariants Verification

1. **Visual Hierarchy & Weight Invariant**:
   - Every `ViewSize` value style SHALL strictly enforce `FontWeight.SemiBold` (`REQ-UI-171`), preserving sunlight readability.
   - Unit styles SHALL NOT be bold (`assertNotEquals(FontWeight.Bold, style.fontWeight)`), maintaining visual subordination.
2. **Backward & Forward Compatibility**:
   - Existing records with `XSMALL`, `SMALL`, `NORMAL`, `LARGE`, `XLARGE`, `HUGE`, `XHUGE` remain completely unchanged in appearance and behavior.
   - Corrupted or unrecognized size strings fall back safely to `ViewSize.NORMAL` via `TrackingViewsRepository.kt`.
3. **Database Schema Stability**:
   - SQLite schema version for `TrackingViewsDatabaseManager` remains intact.
4. **9-Language Localization Parity**:
   - 100% parity across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.

---

## 6. Risk Rating & Technical Justification

- **Risk Level**: **LOW**
- **Justification**:
  - Additive enum expansion and typography mapping.
  - Zero database migrations or breaking schema modifications.
  - All existing typography scales for sizes 1–7 remain immutable.
  - Isolated strictly to tracking view presentation and configuration dialogs.

---

## 7. Recommendation

**RECOMMEND PASS** for Stage 1 Analysis. Ready for Gate 1 independent review audit.
