# Stage 1 Analysis: ATT-7760 - Per-tile cockpit graph color persistence and minimalist color editor UI

**Ticket**: [ATT-7760](https://rainerblind.atlassian.net/browse/ATT-7760)  
**Sub-task**: [[Analysis]](https://rainerblind.atlassian.net/browse/ATT-7760)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-7760` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Cockpit sparkline tiles (`showGraph == true`) store visibility and time horizon in `TrackingViewsDbHelper`, but lack color persistence. Sparkline line color in `SensorFieldView.kt` is hardcoded to `zoneColor` (if text color enabled) or `MaterialTheme.colorScheme.primary`. Athletes cannot distinguish adjacent tiles of the same metric (e.g. 3s Power vs 30s Power) or customize telemetry graph colors per tile, and cannot synchronize a tile's color preference to the global metric palette.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Origin**: In Sprint `2026-44.1` (`ATT-1294`, `REQ-TRK-059`), DB v19 added `ShowGraph` and `GraphHorizonS` columns, with line colors hardcoded to zone or primary theme color.
* **Architecture & Storage Deficit**:
  * `ROWS_TABLE` in `TrackingViewsDatabaseManager.java` lacks a `GraphColor` column, causing all tiles to share hardcoded color resolution.
  * `SensorFieldConfig`, `SensorFieldState`, and `EditSensorFieldViewModel` lack graph color state.
  * `EditSensorFieldDialog.kt` has no UI controls to select colors or toggle between local tile override and global metric scope.
* **Independent Peer Review & Verification**:
  * Audited `TrackingViewsDatabaseManager.java`: confirmed `DB_VERSION = 21` (bump to 22), `SHOW_GRAPH` and `GRAPH_HORIZON_S` exist in `ROWS_TABLE`.
  * Audited `SensorFieldView.kt` lines 461-465: sparkline color strictly defaults to `zoneColor` or `MaterialTheme.colorScheme.primary`.
  * Audited `TelemetryGraphColorPreferences.kt`: global preference store (`getGraphColor`, `setGraphColor`) and 8-color palette (`TelemetryGraphColor`) are available.
  * Audited `.attbackup` export/import: JSON serializes fields; missing keys must safely default to `GLOBAL_DEFAULT`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Bump `DB_VERSION` to 22; add `GraphColor text DEFAULT 'GLOBAL_DEFAULT'` to `ROWS_TABLE` with v22 migration and `.attbackup` default handling.
  2. Thread `graphColor` through `TrackingViewsRepository.kt`, `SensorFieldConfig`, `SensorFieldState`, and `SensorFieldView.kt`.
  3. In `EditSensorFieldDialog.kt`, render minimalist color editor below time horizon slider: circular swatches for 8-color palette, `Theme Default`, and `Dynamic Zone` (gated to HR, Power, HRV DFA α1).
  4. Provide a 2-segment scope toggle (`This tile` | `All <Metric>`). Global scope updates `TelemetryGraphColorPreferences` and resets tile to `GLOBAL_DEFAULT`.
  5. Add localization parity for scope and color strings across all 9 languages (`TranslationParityTest`).
  6. Generate Robolectric Compose screenshot in `docs/engineering/walkthroughs/img/ATT-7760_color_picker.png` per Rule 8.5.
* **Out-of-Scope Non-Goals**: Arbitrary RGB/hex color pickers (fixed 8-color palette only); altering workout detail chart renderers; modifying database tables other than `RowsTable`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-059` in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Added in `ATT-1294` (commit `69e94326`) establishing sparkline rendering.
* **Root Reason for Existing Formulation**: Kept minimal to avoid dialog clutter during initial sparkline MVP.
* **Preservation of Core Invariants**: Existing tiles and backups default seamlessly to `GLOBAL_DEFAULT`, preserving current visual behavior while granting optional granular override.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Amend `REQ-TRK-059` and define `REQ-TRK-059.3` (per-tile color & editor UI) and `REQ-TRK-059.4` (DB v22 migration & backup compatibility) in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, `TelemetryGraphColorPreferences.kt`, `res/values*/strings.xml`.
* **Verification Impact & Test Surface**: Amend `TST-TRK-059` in `docs/tests/tst_tracking_cockpit.md`; add unit tests in `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsRepositoryTest.kt`, and `EditSensorFieldViewModelTest.kt`; add screenshot test in `EditSensorFieldDialogScreenshotTest.kt`.
* **Risk Assessment & Invariants**: Medium risk (3 SP). Database migration must ensure SQLite backward compatibility without layout loss. UI changes isolated to `EditSensorFieldDialog`. Zero memory leak / OOM impact.

---

## 6. Architectural Strategy & High-Level Solution
* **Database & Repository Layer**:
  * Add `GRAPH_COLOR = "GraphColor"` column (`DEFAULT 'GLOBAL_DEFAULT'`) to `ROWS_TABLE` in `TrackingViewsDatabaseManager.java` (`DB_VERSION = 22`).
  * In `onUpgrade()`, add `ALTER TABLE RowsTable ADD COLUMN GraphColor text DEFAULT 'GLOBAL_DEFAULT';`.
  * Support `graphColor` in `SensorFieldConfig`, `SensorFieldState`, and JSON serialization.
* **Sparkline Color Resolution (`SensorFieldView.kt`)**:
  * `GLOBAL_DEFAULT` -> resolves via `TelemetryGraphColorPreferences.getGraphColor()`.
  * `THEME_DEFAULT` -> `MaterialTheme.colorScheme.primary`.
  * `DYNAMIC_ZONE` -> `fieldState.zoneColor` (if zone active).
  * Explicit palette value -> `TelemetryGraphColor.valueOf(graphColor).resolveColor(...)`.
* **Minimalist UI (`EditSensorFieldDialog.kt`)**:
  * Circular swatches row + Theme Default & Dynamic Zone (HR/Power/HRV) chips.
  * Segmented button: `This tile` vs `All <Metric>`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: DB migration idempotent; legacy backups restore cleanly without crashing; zero UI regression on non-graph tiles; 9-language translation parity.
* **Risk Rating**: **MEDIUM** (3 SP - DB schema migration v22 + Jetpack Compose UI extension).
