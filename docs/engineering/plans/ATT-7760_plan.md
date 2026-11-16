# Stage 3: Implementation Plan - ATT-7760: Per-Tile Cockpit Graph Color Persistence & Color Editor UI

**Ticket**: [ATT-7760](https://rainerblind.atlassian.net/browse/ATT-7760) | **Sub-task**: [[Impl-Plan]](https://rainerblind.atlassian.net/browse/ATT-7760)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-7760`  
**Requirement Mapping**: `REQ-TRK-081` (`REQ-TRK-081.1`..`REQ-TRK-081.5`)  
**Test Mapping**: `TST-TRK-081` (`TST-TRK-081.1`..`TST-TRK-081.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
Cockpit sparklines currently lack per-tile color customization and persistence. `SensorFieldView.kt` hardcodes sparkline color to zone color or `primary`, preventing athletes from differentiating adjacent tiles (e.g. 3s Power vs 30s Power) or applying custom palettes. This plan introduces SQLite schema v22 adding `GraphColor` to `ROWS_TABLE`, threads color state through repository and view layers, adds a minimalist color picker with a 2-segment scope toggle (`This tile` | `All <Metric>`) in `EditSensorFieldDialog.kt`, supports `Theme Default` and gated `Dynamic Zone`, preserves `.attbackup` JSON compatibility, and maintains 9-language parity.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-TRK-081.1` (DB v22 & Backup), `REQ-TRK-081.2` (Color Mode Model & State Flow), `REQ-TRK-081.3` (Color Editor UI & Scope Toggle), `REQ-TRK-081.4` (9-Language Parity), `REQ-TRK-081.5` (Rule 8.5 Visual Inspection).
* **Test Mapping**: `TST-TRK-081.1`..`TST-TRK-081.6` in `docs/tests/tst_tracking_cockpit.md`.

---

## 3. System Invariants & Preserved Behavior
1. **Database Backward Compatibility**: Missing `GraphColor` in legacy DB or `.attbackup` defaults safely to `"GLOBAL_DEFAULT"`.
2. **Preference Isolation & OOM Safety (`ATT-7874`)**: Telemetry color store updates utilize `context.applicationContext` without leaking CoroutineScopes or ViewModels.
3. **Dynamic Zone Gating**: `Dynamic Zone` chip is offered exclusively for `HR`, `POWER`, and `HRV_DFA_A1`.
4. **UI Guidelines Compliance (`docs/design_guidelines.md` §1)**: Binary scope toggle strictly uses `SingleChoiceSegmentedButtonRow` with `SegmentedButton(..., icon = {})` (no `FilterChip`).
5. **Human Decision Gate**: Rule 3 Human Gate remains strictly intact.

---

## 4. Proposed Architectural Changes
* **Component 1 (SQLite & Backup)**: Bump `DB_VERSION = 22` in `TrackingViewsDatabaseManager.java`, add `GRAPH_COLOR` (`DEFAULT 'GLOBAL_DEFAULT'`), implement `onUpgrade` v21->v22, and handle missing keys in JSON backup import.
* **Component 2 (Repository & View Layer)**: Expose `graphColor: String = "GLOBAL_DEFAULT"` on `SensorFieldConfig` and `SensorFieldState`. In `SensorFieldView.kt`, resolve line color via `GLOBAL_DEFAULT`, `THEME_DEFAULT`, `DYNAMIC_ZONE`, or palette color.
* **Component 3 (ViewModel & Preferences)**: Update `EditSensorFieldViewModel.kt` to track selected color and scope (`THIS_TILE` vs `ALL_METRIC`). On save: `THIS_TILE` persists to tile's `graphColor`; `ALL_METRIC` updates `TelemetryGraphColorPreferences.setGraphColor()` and resets tile to `"GLOBAL_DEFAULT"`.
* **Component 4 (Compose UI Dialog)**: In `EditSensorFieldDialog.kt`, render 8 circular swatches + `Theme Default` chip + `Dynamic Zone` chip (gated) + M3 `SingleChoiceSegmentedButtonRow`. Update preview `SensorSparklineView` live.
* **Component 5 (Localization)**: Add UI string resources with 100% parity across all 9 supported locales in `res/values*/strings.xml`.
* **UI Consistency**: Strictly adheres to `docs/design_guidelines.md` §1 (SegmentedButton with `icon = {}`, single-line German labels, 48dp touch targets).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Medium Risk, 3 SP -> Flash executes Stage 4)**.
* **Step 1: DB Schema v22 & Backup**: Update `TrackingViewsDatabaseManager.java` (`DB_VERSION = 22`, `GRAPH_COLOR`) and backup import/export round-trip.
* **Step 2: Repository & State Flow**: Update `TrackingViewsRepository.kt`, `SensorFieldConfig`, `SensorFieldState`, and `SensorFieldView.kt` color resolution.
* **Step 3: ViewModel Scope & Logic**: Update `EditSensorFieldViewModel.kt` with graph color state, scope selection, and `TelemetryGraphColorPreferences` integration.
* **Step 4: Color Editor UI**: Implement color swatch row, chip options, and M3 `SingleChoiceSegmentedButtonRow` in `EditSensorFieldDialog.kt`. Wire live preview.
* **Step 5: 9-Language Localization**: Add localized strings in `res/values*/strings.xml` verified via `TranslationParityTest`.
* **Step 6: Unit Test Suite**: Author unit tests with explicit `// TST-TRK-081` tags in `TrackingViewsDbMigrationV22Test.kt`, `TrackingViewsRepositoryTest.kt`, `EditSensorFieldViewModelTest.kt`, and `SensorFieldViewTest.kt`.
* **Step 7: Rule 8.5 UI Verification**: Generate `docs/engineering/walkthroughs/img/ATT-7760_color_picker.png` and verify against 4-point visual checklist.
* **Tier 1 Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDbMigrationV22Test" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" && python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Execute Tier 1 test command (100% pass rate) + Rule 8.5 visual inspection via `read_file`.
* **Rollback**: Branch isolation (`feature/ATT-7760`) allows clean rollback with zero impact on `sprint/2026-46.8`.
