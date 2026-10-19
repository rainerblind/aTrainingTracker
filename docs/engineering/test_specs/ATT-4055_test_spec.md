# Stage 2: Requirement & Test Specification - ATT-4055: Support configurable horizontal section dividers with optional mini-titles between Cockpit rows

**Ticket**: [ATT-4055](https://rainerblind.atlassian.net/browse/ATT-4055)  
**Sub-task**: [ATT-4112](https://rainerblind.atlassian.net/browse/ATT-4112) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*)  
**Test Spec ID**: `TST-UI-374`  
**Branch**: `feature/ATT-4055`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (REQ-UI-411)

### 1.1 Problem Statement & Rationale
When a Cockpit tracking tab (`SensorGridScreen.kt`) contains 5–7 rows of sensor metrics, adjacent rows visually blend into a dense "wall of numbers", making sub-second handlebar glanceability harder during rides or runs.
Currently, `SensorFieldStyle.kt` only provides global, all-or-nothing styling (`CLASSIC_SEAMLESS`, `OUTLINED_TILES`, `ELEVATED_CARDS`, `SOFT_CAPSULES`) framing every cell uniformly without any visual semantic grouping. Providing configurable horizontal section dividers between rows with optional mini-titles allows athletes to cluster related metrics (e.g. *Live Effort*, *Power & Pacing*, *Current Lap*, *Trip / Navigation*) while conserving vertical screen space for sensor typography and the map canvas.

### 1.2 Functional & Architectural Requirements
The system SHALL support configurable horizontal section dividers with optional mini-titles between sensor rows in Cockpit tracking tabs (`TrackingViewsDbHelper`, `TrackingViewsDatabaseManager`, `TrackingViewsRepository`, `SensorGridScreen.kt`), persisting them in `FlexibleGenericView.db` with automated row shift tracking, minimal vertical height overhead, and 9-language localization parity:

1. **Database Schema & Migration (`REQ-UI-411.1`)**:
   - In `TrackingViewsDatabaseManager.TrackingViewsDbHelper`, `DB_VERSION` SHALL be incremented to `14` (verified as the next sequential schema version for `FlexibleGenericView.db` on `develop`).
   - A dedicated table `SectionDividersTable` SHALL be created with columns: `_id INTEGER PRIMARY KEY AUTOINCREMENT`, `ViewId INTEGER NOT NULL`, `RowNr INTEGER NOT NULL`, `Title TEXT`, and constraint `UNIQUE(ViewId, RowNr) ON CONFLICT REPLACE`.
   - In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`, when `oldVersion < 14`, the table `SectionDividersTable` SHALL be created idempotently via `CREATE TABLE IF NOT EXISTS SectionDividersTable (...)`.
2. **Data Access & Row Shift Invariants (`REQ-UI-411.2`)**:
   - `TrackingViewsDatabaseManager` SHALL provide methods `getSectionDividers(long viewId)`, `setSectionDivider(long viewId, int rowNr, @Nullable String title)`, `deleteSectionDivider(long viewId, int rowNr)`, and `deleteSectionDividersForView(long viewId)`.
   - **Row Insertion Shift**: When a new row is inserted at `rowNr` in `insertSensorFieldAt(long tabViewId, int rowNr, int colNr, ...)` with `colNr == -1`, any existing divider at `RowNr >= rowNr` for `tabViewId` SHALL shift up by 1 (`UPDATE SectionDividersTable SET RowNr = RowNr + 1 WHERE ViewId = ? AND RowNr >= ?`).
   - **Row Deletion Shift**: When a row is removed or deleted, any divider at `RowNr > deletedRowNr` SHALL shift down by 1 (`UPDATE SectionDividersTable SET RowNr = RowNr - 1 WHERE ViewId = ? AND RowNr > ?`), while any divider attached to `deletedRowNr` SHALL be re-indexed during `normalizeGrid(tabViewId)` to the surviving row or removed if no rows remain.
   - **Grid Normalization**: When `normalizeGrid(long tabViewId)` re-indexes distinct rows to contiguous `1..N`, existing section dividers for `tabViewId` SHALL be mapped to the matching new row indices, and any orphaned dividers SHALL be deleted.
   - When a tab is deleted in `deleteTabView(long viewId)`, all dividers for `viewId` SHALL be deleted.
3. **Repository Flow & Screen State Synchronization (`REQ-UI-411.3`)**:
   - `TrackingViewsRepository` SHALL expose `fun getSectionDividersFlow(tabViewId: Long): Flow<List<SectionDividerInfo>>` triggered by `configUpdateTrigger`.
   - `TrackingScreenState` SHALL include `val sectionDividers: List<SectionDividerInfo> = emptyList()`.
   - `TrackingViewModel` SHALL collect dividers from `TrackingViewsRepository` and update `TrackingScreenState.sectionDividers`.
4. **Glanceable Cockpit Rendering & Truncation Safety (`REQ-UI-411.4`)**:
   - In `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`, `SensorGridScreen.kt` SHALL render a section divider above any visible row that has a matching entry in `state.sectionDividers` (`divider.rowNr == fieldsInThisRow.first().rowNr`).
   - When `divider.title` is null or blank, the divider SHALL render as a crisp horizontal rule line (`HorizontalDivider`, 1–2 dp stroke, `MaterialTheme.colorScheme.outlineVariant`) across the grid with minimal vertical padding (~4 dp).
   - When `divider.title` is present, the divider SHALL render as a compact inline header (e.g. `─── POWER & PACING ───`) styled with `MaterialTheme.typography.labelSmall`, uppercase, bold, letter-spacing, and subtle flanking divider rules, with total height overhead constrained to $\le 20\text{ dp}$.
   - **Title Truncation Invariant**: Mini-titles SHALL specify `maxLines = 1` and `overflow = TextOverflow.Ellipsis`, guaranteeing that long text strings gracefully truncate on narrow screen displays and never cause multi-line wrapping or breach the $\le 20\text{ dp}$ vertical height constraint.
5. **Configuration Mode WYSIWYG Controls (`REQ-UI-411.5`)**:
   - In `ScreenMode.CONFIGURATION`, an inter-row divider action/indicator SHALL be displayed next to `RowAdder` between rows.
   - If a divider exists at that row boundary, a styled chip/indicator SHALL display the title (or "Divider") with an edit/delete affordance.
   - Clicking the action SHALL launch `EditSectionDividerDialog` allowing the user to select from localized quick-pick preset chips (*Power*, *Heart Rate*, *Lap*, *Pacing*, *Navigation*, *Total*), enter custom free text, clear the title to make it an unlabeled divider line, or delete the divider entirely.
6. **9-Language Localization Parity (`REQ-UI-411.6`)**:
   - All new UI strings for section divider controls, dialog actions, and preset chips SHALL exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*). Targets `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`, and `values*/strings.xml`.
* **Historical Origin & Commit Trace**: Ticket `ATT-4055`, Sprint `2026-42.10`.
* **Root Reason for Existing Formulation**: Cockpit layout historically lacked inter-row grouping entities, causing 5–7 rows of sensor metrics to visually blend into a dense "wall of numbers".
* **Preservation of Core Invariants**: Full backward compatibility for `FlexibleGenericView.db` (`DB_VERSION = 14`), zero layout corruption during row insertions/deletions, minimal vertical overhead ($\le 20\text{ dp}$) with single-line ellipsis truncation to protect map and sensor typography, and 100% clean-room test pass rate strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Configuration & Persistence)**:
  * *Given* the Cockpit in `ScreenMode.CONFIGURATION`,
  * *When* the user taps the section divider action between two rows and selects a preset chip (e.g. "Power") or enters text,
  * *Then* the section divider is persisted in `SectionDividersTable` and dynamically shown on the screen.
* **Criterion 2 (Unlabeled Divider in Tracking Mode)**:
  * *Given* a section divider configured without text,
  * *When* viewed in `ScreenMode.TRACKING`,
  * *Then* a horizontal divider line is rendered above that row across all `SensorFieldStyle` variants with minimal vertical height overhead.
* **Criterion 3 (Labeled Mini-Title in Tracking Mode)**:
  * *Given* a section divider configured with a title (e.g. "RUNDE / LAP"),
  * *When* viewed in `ScreenMode.TRACKING`,
  * *Then* the title is rendered inline with uppercase typography and flanking divider rules.
* **Criterion 4 (Title Truncation Guard)**:
  * *Given* a long section divider title on a narrow screen,
  * *When* rendered in `ScreenMode.TRACKING`,
  * *Then* the title truncates cleanly with ellipsis (`maxLines = 1`) without multi-line wrapping or exceeding $20\text{ dp}$ height.
* **Criterion 5 (Row Insertion, Deletion & Normalization Shift)**:
  * *Given* rows are inserted or deleted above an existing divider,
  * *When* the layout updates and normalizes,
  * *Then* the divider shifts cleanly with its row boundary without orphan records.

### 1.5 System Invariants
1. **Schema Migration Backward Compatibility**: `DB_VERSION = 14` must migrate seamlessly from `13` without dropping or corrupting existing user tabs.
2. **Minimal Vertical Height Overhead**: Dividers must not exceed $20\text{ dp}$ in vertical height (enforced via `maxLines = 1` and `TextOverflow.Ellipsis`) so that sensor font sizes and map viewport remain uncompromised.
3. **Round-Trip Tuning Invariant**: Dividers must not interfere with `filterVisibleFieldsForTuning` / `packVisibleRowsForTuning`.
4. **9-Language Localization Parity**: Zero missing strings or XML entity syntax violations.

---

## 2. Test Specification (TST-UI-374)

### Test Case 1: SQLite Schema Creation & Upgrade Migration Test (`TST-UI-374.1`)
* **Scope**: Database Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Preconditions**: Test database instance in memory.
* **Action**:
  1. Verify `TrackingViewsDbHelper.DB_VERSION == 14`.
  2. Verify `onCreate` creates `SectionDividersTable`.
  3. Verify `onUpgrade` from version 13 creates `SectionDividersTable` idempotently.
* **Expected Result**: Table columns `_id`, `ViewId`, `RowNr`, `Title` are verified and unique constraint is active.

### Test Case 2: Section Divider CRUD, Row Shifting on Insert & Grid Normalization Test (`TST-UI-374.2`)
* **Scope**: Database Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Preconditions**: A view with 4 rows.
* **Action**:
  1. Insert dividers at `rowNr = 2` ("Power") and `rowNr = 4` (null title).
  2. Query `getSectionDividers(viewId)` and verify presence.
  3. Insert a new row at `rowNr = 2` (`colNr = -1`) via `insertSensorFieldAt`.
  4. Verify dividers shift up to `rowNr = 3` and `rowNr = 5`.
  5. Delete a sensor field/row and verify dividers at `rowNr > deletedRowNr` shift down by 1.
  6. Trigger `normalizeGrid(viewId)`; verify dividers remain aligned with normalized rows and orphaned dividers are cleared.
  7. Delete the view via `deleteTabView(viewId)`; verify all dividers are removed.
* **Expected Result**: 100% data integrity and row alignment throughout row insertion, deletion, and normalization lifecycle.

### Test Case 3: TrackingViewsRepository & TrackingViewModel Flow Emission Test (`TST-UI-374.3`)
* **Scope**: Repository / ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepositoryTest.kt`
* **Preconditions**: Mock/Robolectric database context.
* **Action**:
  1. Collect `getSectionDividersFlow(viewId)`.
  2. Insert and update section dividers.
  3. Trigger `configUpdateTrigger`.
* **Expected Result**: Flow emits updated `List<SectionDividerInfo>` matching database state.

### Test Case 4: SensorGridScreen Visual Divider Rendering & Truncation Test (`TST-UI-374.4`)
* **Scope**: UI Layout Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `TrackingScreenState` with populated `fields` and `sectionDividers`.
* **Action**:
  1. In `ScreenMode.TRACKING`, verify unlabeled divider renders line.
  2. In `ScreenMode.TRACKING`, verify labeled divider renders uppercase title with `maxLines = 1` and `TextOverflow.Ellipsis`.
* **Expected Result**: Dividers render above target rows without layout exceptions or multi-line overflow.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-UI-374.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorGridLocalizationTest.kt`
* **Preconditions**: String resources across 9 directories.
* **Action**:
  Audit the following string keys across EN, DE, ES, FR, IT, JA, NL, PL, PT:
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
* **Expected Result**: 100% parity across all 9 locales, zero missing entries, zero unescaped linebreaks.

### Test Case 6: Clean-Room Full Suite Regression (`TST-UI-374.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-374.1` | Unit (DB) | `TrackingViewsDbHelper.onCreate / onUpgrade` | `REQ-UI-411.1` | Specified |
| `TST-UI-374.2` | Unit (DB) | `TrackingViewsDatabaseManager.getSectionDividers / setSectionDivider / shift` | `REQ-UI-411.2` | Specified |
| `TST-UI-374.3` | Unit (Repo/VM) | `TrackingViewsRepository.getSectionDividersFlow` | `REQ-UI-411.3` | Specified |
| `TST-UI-374.4` | UI Layout | `SensorGridScreen / CockpitSectionDivider` | `REQ-UI-411.4`, `REQ-UI-411.5` | Specified |
| `TST-UI-374.5` | Localization | `SensorGridLocalizationTest` | `REQ-UI-411.6`, `REQ-UI-106` | Specified |
| `TST-UI-374.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
