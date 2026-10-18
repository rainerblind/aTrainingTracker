# Stage 1 Analysis: ATT-4055 - Support configurable horizontal section dividers with optional mini-titles between Cockpit rows

**Ticket**: [ATT-4055](https://rainerblind.atlassian.net/browse/ATT-4055)  
**Sub-task**: [ATT-4111](https://rainerblind.atlassian.net/browse/ATT-4111) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-4055`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

When a Cockpit tracking tab (`SensorGridScreen.kt`) contains 5–7 rows of sensor metrics, adjacent rows visually blend into a dense "wall of numbers", impairing sub-second handlebar glanceability during high-intensity cycling or running.

Currently, `SensorFieldStyle.kt` only provides global, all-or-nothing styling:
- `CLASSIC_SEAMLESS` (`Variant0_Baseline`) defaults to `borderContrast = 0.0f` (no visible separation between adjacent rows).
- `OUTLINED_TILES`, `ELEVATED_CARDS`, and `SOFT_CAPSULES` frame every individual cell uniformly without visual hierarchy or grouping.

There is currently no mechanism for users to group related telemetry rows into semantic sections (e.g. *Live Effort*, *Power & Pacing*, *Current Lap*, *Trip / Navigation*).

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Database Schema Limitation**:
   Cockpit layout sensor fields are persisted in `FlexibleGenericView.db` within `LayoutRowsTable`, indexed by `ViewId`, `RowNr`, and `ColNr`. There is currently no entity or table representing inter-row boundaries, dividers, or section titles.
   Current schema version is `DB_VERSION = 13` (upgraded for Elevation Profile Scope & Lookahead in ATT-3317).
2. **UI Layout Pipeline**:
   - In `ScreenMode.TRACKING`, rows are grouped by `rowNr` via `packVisibleRowsForTuning(state.fields, tuningConfig, screenMode)` and rendered in a vertical `Column`. No divider composables are inserted between rows.
   - In `ScreenMode.CONFIGURATION`, rows are rendered with `RowAdder` buttons between rows, but no divider editing or toggling action exists.
3. **Row Re-indexing & Normalization Invariants**:
   When rows are inserted, deleted, or swapped in `TrackingViewsDatabaseManager.java`, row numbers shift:
   - `insertSensorFieldAt(tabViewId, rowNr, -1, ...)` executes `UPDATE LayoutRowsTable SET RowNr = RowNr + 1 WHERE RowNr >= rowNr`.
   - `deleteSensorField` invokes `normalizeGrid(tabViewId)`, which compacts row indices to contiguous `1..N`.
   Any inter-row divider definition must participate in these shift and normalization operations to stay aligned with its target row.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Database Persistence (`FlexibleGenericView.db`)**: Add `SectionDividersTable` (`_id`, `ViewId`, `RowNr`, `Title`) with `DB_VERSION = 14`.
  * **Manager & DAO Operations**: Implement `getSectionDividers`, `setSectionDivider`, `deleteSectionDivider`, and integrate divider shifts into `insertSensorFieldAt`, `normalizeGrid`, and `deleteTabView`.
  * **State Exposure**: Expose `SectionDividerInfo` in `TrackingViewsRepository`, `TrackingScreenState`, and `TrackingViewModel`.
  * **Tracking Mode Rendering (`ScreenMode.TRACKING`)**: Render horizontal section dividers above designated rows:
    - *Unlabeled mode*: 1–2 dp stroke line with minimal vertical padding (~4 dp), consuming negligible vertical height.
    - *Labeled mode*: Compact inline section header (e.g. `─── POWER & PACING ───`) with ~14–16 dp height.
  * **Configuration Mode Controls (`ScreenMode.CONFIGURATION`)**: Provide WYSIWYG inter-row divider action/indicator next to `RowAdder`, with a dialog to set/edit/clear titles (with localized quick-pick chips: *Power*, *Heart Rate*, *Lap*, *Pacing*, *Navigation*, *Total*) or remove dividers.
  * **9-Language Localization Parity**: Full parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, and `values-pt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Vertical column dividers (columns are already visually distinct through cell aspect ratios and borders).
  * Arbitrary per-cell custom background coloring (handled by `SensorFieldStyle` and zone colors).
  * Overwriting existing user tabs or altering default factory tabs during database migration.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

Net-new requirement only (`REQ-UI-411` / `TST-UI-374`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution

1. **Database Schema (`FlexibleGenericView.db`)**:
   Bump `DB_VERSION` from `13` to `14` in `TrackingViewsDatabaseManager.TrackingViewsDbHelper`.
   ```sql
   CREATE TABLE SectionDividersTable (
       _id INTEGER PRIMARY KEY AUTOINCREMENT,
       ViewId INTEGER NOT NULL,
       RowNr INTEGER NOT NULL,
       Title TEXT,
       UNIQUE(ViewId, RowNr) ON CONFLICT REPLACE
   );
   ```
2. **Database Migration (`onUpgrade`)**:
   In `TrackingViewsDbHelper.onUpgrade`:
   ```java
   if (oldVersion < 14) {
       db.execSQL("CREATE TABLE IF NOT EXISTS SectionDividersTable ("
               + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
               + "ViewId INTEGER NOT NULL, "
               + "RowNr INTEGER NOT NULL, "
               + "Title TEXT, "
               + "UNIQUE(ViewId, RowNr) ON CONFLICT REPLACE);");
   }
   ```
3. **DAO Methods in `TrackingViewsDatabaseManager`**:
   - `List<SectionDividerData> getSectionDividers(long viewId)`
   - `void setSectionDivider(long viewId, int rowNr, @Nullable String title)`
   - `void deleteSectionDivider(long viewId, int rowNr)`
   - Row shifting in `insertSensorFieldAt`: shift `RowNr = RowNr + 1` for `ViewId = ? AND RowNr >= ?`.
   - Row re-indexing in `normalizeGrid`: map existing dividers to new contiguous row indices.
4. **Repository Flow & ViewModel**:
   - `TrackingViewsRepository.getSectionDividersFlow(tabViewId: Long): Flow<List<SectionDividerInfo>>`.
   - `TrackingScreenState.sectionDividers: List<SectionDividerInfo> = emptyList()`.
   - `GridActions`: add callbacks `onSetSectionDivider(rowNr: Int, title: String?)` and `onDeleteSectionDivider(rowNr: Int)`.
5. **UI Rendering**:
   - Composable `CockpitSectionDivider(title: String?)`: renders clean divider line or line with inline uppercase mini-title.
   - Composable `EditSectionDividerDialog`: modal dialog with text input, preset chips, save, and remove actions.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing 3,158 clean-room unit tests.
  2. Handlebar glanceability preserved: minimal vertical height overhead (< 20 dp) ensures sensor font sizes and map viewport remain uncompromised.
  3. Database consistency: complete transactional integrity during row insertions and deletions.
  4. Parent ticket Human Decision Gate remains strictly enforced (`Analysis` -> `Test Spec` -> `Impl-Plan` -> `Implementation` -> `Final Review (Human)`).
* **Risk Rating**: **LOW**
  - Schema change is additive (new table `SectionDividersTable`), preventing data loss or corruption of existing views.
  - UI components are non-intrusive and cleanly isolated in `SensorGridScreen.kt`.
