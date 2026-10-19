# Stage 3: Implementation Plan - ATT-4055: Support configurable horizontal section dividers with optional mini-titles between Cockpit rows

**Ticket**: [ATT-4055](https://rainerblind.atlassian.net/browse/ATT-4055)  
**Sub-task**: [ATT-4113](https://rainerblind.atlassian.net/browse/ATT-4113) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*)  
**Test Mapping**: `TST-UI-374` (*Cockpit Sensor Grid Configurable Section Dividers, Automated Row Shift & 9-Language Localization Verification Suite*)  
**Branch**: `feature/ATT-4055`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

When a Cockpit tracking tab (`SensorGridScreen.kt`) contains 5–7 rows of sensor metrics, adjacent rows visually blend into a dense "wall of numbers", making sub-second handlebar glanceability harder during high-intensity rides or runs.
Currently, `SensorFieldStyle.kt` only provides global cell framing without any mechanism to group related rows into semantic clusters (e.g., *Live Effort*, *Power & Pacing*, *Current Lap*, *Trip Overview*).

This plan introduces horizontal section dividers with optional mini-titles between Cockpit rows, backed by SQLite persistence (`DB_VERSION = 14`), automated row shift handling, glanceable single-line rendering ($\le 20\text{ dp}$), WYSIWYG configuration controls, and 9-language localization parity.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*)
* **Test Mapping**: `TST-UI-374` (`TST-UI-374.1` to `TST-UI-374.6`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All 3,158 existing clean-room unit tests continue to pass cleanly.
2. **Minimal Vertical Overhead Invariant**: Dividers must consume minimal vertical height ($\le 20\text{ dp}$), enforced via single-line uppercase rendering with `maxLines = 1` and `TextOverflow.Ellipsis`, guaranteeing that map viewport and sensor font sizes remain uncompromised.
3. **Database Schema & Shift Invariants**:
   - `FlexibleGenericView.db` `DB_VERSION` bumped to `14` (verified as the next sequential version).
   - Adding/removing rows shifts divider `RowNr` symmetrically without orphan records or `REPLACE` collisions.
   - Grid normalization re-indexes dividers to contiguous rows.
4. **Thread Safety & Dispatcher Affinity**: Database operations execute on background dispatchers (`Dispatchers.IO`), and StateFlow emissions synchronize to the Compose UI.
5. **9-Language Localization Parity**: All new UI strings exist across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, and `values-pt`.

---

## 4. Proposed Architectural Changes

### Component 1: Database Persistence Layer (`TrackingViewsDatabaseManager.java`)
- Increment `TrackingViewsDbHelper.DB_VERSION` from `13` to `14`.
- Define table `SectionDividersTable` with columns:
  - `_id` INTEGER PRIMARY KEY AUTOINCREMENT
  - `ViewId` INTEGER NOT NULL
  - `RowNr` INTEGER NOT NULL
  - `Title` TEXT
  - `UNIQUE(ViewId, RowNr) ON CONFLICT REPLACE`
- Implement `onUpgrade` logic for `oldVersion < 14` with idempotent table creation.
- Add `SectionDividerData` record/DTO.
- Implement CRUD operations:
  - `List<SectionDividerData> getSectionDividers(long viewId)`
  - `void setSectionDivider(long viewId, int rowNr, @Nullable String title)`
  - `void deleteSectionDivider(long viewId, int rowNr)`
  - `void deleteSectionDividersForView(long viewId)`
- Update row shifting logic:
  - In `insertSensorFieldAt(tabViewId, rowNr, colNr, ...)` with `colNr == -1`: shift `RowNr = RowNr + 1` for `ViewId = ? AND RowNr >= ?`.
  - In `deleteSensorField(fieldId)` / row removal: shift `RowNr = RowNr - 1` for `ViewId = ? AND RowNr > deletedRowNr`.
  - In `normalizeGrid(db, tabViewId)`: map divider `RowNr` to new normalized row indices, deleting orphaned dividers.
  - In `deleteTabView(long viewId)`: clean up all section dividers for `viewId`.

### Component 2: Repository & ViewModel Layer (`TrackingViewsRepository.kt`, `TrackingViewModel.kt`)
- Define DTO `data class SectionDividerInfo(val id: Long = 0, val viewId: Long, val rowNr: Int, val title: String? = null)`.
- In `TrackingViewsRepository`:
  - `fun getSectionDividersFlow(tabViewId: Long): Flow<List<SectionDividerInfo>>` triggered by `configUpdateTrigger`.
  - Mutators: `suspend fun setSectionDivider(...)` and `suspend fun deleteSectionDivider(...)` that invoke `TrackingViewsDatabaseManager` and increment `configUpdateTrigger`.
- In `TrackingScreenState`:
  - Add property `val sectionDividers: List<SectionDividerInfo> = emptyList()`.
- In `TrackingViewModel`:
  - Collect `repository.getSectionDividersFlow(tabViewId)` and update `_uiState.update { it.copy(sectionDividers = dividers) }`.
- In `GridActions`:
  - Add callbacks:
    - `fun onSetSectionDivider(rowNr: Int, title: String?) {}`
    - `fun onDeleteSectionDivider(rowNr: Int) {}`

### Component 3: UI Layer (`SensorGridScreen.kt`)
- Composable `CockpitSectionDivider(divider: SectionDividerInfo)`:
  - If `divider.title.isNullOrBlank()`: clean horizontal rule (`HorizontalDivider`, 1.5 dp, `outlineVariant`, vertical padding 4 dp).
  - If `!divider.title.isNullOrBlank()`: inline mini-title `Row` with flanking `HorizontalDivider`s and `Text` (`labelSmall`, bold, uppercase, letter spacing 1.2 sp, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`). Total height $\le 20\text{ dp}$.
- Tracking Mode (`ScreenMode.TRACKING` / `ScreenMode.PREVIEW`):
  - In `Column(packedRows)`, before rendering `fieldsInThisRow`, check if `state.sectionDividers` contains an entry for `fieldsInThisRow.first().rowNr`. If present, render `CockpitSectionDivider`.
- Configuration Mode (`ScreenMode.CONFIGURATION`):
  - In the inter-row area (adjacent to `RowAdder`), show the current divider state:
    - If divider exists: compact chip with title/icon and edit/delete affordance.
    - If divider absent: subtle "+ Divider" action button.
  - Composable `EditSectionDividerDialog`:
    - Title: "Section Divider" / "Abschnitts-Trennlinie".
    - Preset chips: *Power*, *Heart Rate*, *Lap*, *Pacing*, *Navigation*, *Total*.
    - Text field for custom mini-title (or leave empty for line-only).
    - Actions: Save, Clear Title (Line Only), Delete Divider, Cancel.

### UI Consistency (Rule 23 — mandatory for UI changes)
* **Reference screen / component**: `SensorGridScreen.kt` (inter-row `RowAdder` and `SpatialCockpitToggleCard`), `FilterChip` in elevation profile lookahead settings, and `AppModalBottomSheet` / `AlertDialog` styling.
* **Reused components**: `FilterChip`, `HorizontalDivider`, `TextButton`, `OutlinedTextField`, `Surface`, `MaterialTheme.typography.labelSmall`, `MaterialTheme.typography.titleMedium`.
* **Theme tokens**: shapes `RoundedCornerShape(8.dp)` / `RoundedCornerShape(12.dp)`, `MaterialTheme.colorScheme.outlineVariant`, `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.surfaceVariant`.
* **New one-off styles & justification**: None; fully conforms to M3 design tokens.

### Component 4: 9-Language Localization Parity (`values*/strings.xml`)
- Add the following strings across all 9 supported locales:
  - `cockpit_section_divider_add`
  - `cockpit_section_divider_edit`
  - `cockpit_section_divider_dialog_title`
  - `cockpit_section_divider_title_label`
  - `cockpit_section_divider_chip_power`
  - `cockpit_section_divider_chip_heart_rate`
  - `cockpit_section_divider_chip_lap`
  - `cockpit_section_divider_chip_pacing`
  - `cockpit_section_divider_chip_navigation`
  - `cockpit_section_divider_chip_total`
  - `cockpit_section_divider_delete`

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Resources
* Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* Changes: Add section divider strings and quick-pick chips with literal linebreaks and 100% parity.

### Step 2: Database Schema v14, Shifting & CRUD in `TrackingViewsDatabaseManager.java`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
* Changes:
  - Bump `DB_VERSION = 14`.
  - Add `SectionDividersTable` schema to `onCreate` and `onUpgrade`.
  - Add `SectionDividerData` record.
  - Implement CRUD: `getSectionDividers`, `setSectionDivider`, `deleteSectionDivider`, `deleteSectionDividersForView`.
  - Add row shift logic in `insertSensorFieldAt`, `deleteSensorField`, and `normalizeGrid`.

### Step 3: Repository Flow & ViewModel Integration
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt` (`GridActions`)
* Changes:
  - Add `SectionDividerInfo`.
  - Expose `getSectionDividersFlow` in `TrackingViewsRepository`.
  - Wire repository flow into `TrackingViewModel` and `TrackingScreenState.sectionDividers`.
  - Add divider action handlers in `GridActions`.

### Step 4: SensorGridScreen UI Rendering & Configuration Dialog
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Changes:
  - Add `CockpitSectionDivider` composable (with single-line ellipsis truncation).
  - Render dividers in `ScreenMode.TRACKING` / `ScreenMode.PREVIEW`.
  - Add inter-row divider action/indicator in `ScreenMode.CONFIGURATION`.
  - Implement `EditSectionDividerDialog` with preset chips and free-text input.

### Step 5: Unit Tests & Verification
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorGridLocalizationTest.kt`
* Targeted Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.SensorGridLocalizationTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests executed after each construction step, followed by the complete clean-room test suite (`./gradlew testDebugUnitTest`) verifying 100% pass rate.
* **Rollback Plan**: All changes isolated on branch `feature/ATT-4055`. If critical regressions occur, the branch can be cleanly reset to `sprint/2026-42.10` without impact on release integration.
