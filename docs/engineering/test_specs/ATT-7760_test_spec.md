# Stage 2: Requirement & Test Specification - ATT-7760: Per-Tile Cockpit Graph Color Persistence and Minimalist Color Editor UI

**Ticket**: [ATT-7760](https://rainerblind.atlassian.net/browse/ATT-7760)  
**Sub-task**: [[Req & Test Spec]](https://rainerblind.atlassian.net/browse/ATT-7760)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-7760`  
**Requirement Mapping**: `REQ-TRK-081` | **Test Spec ID**: `TST-TRK-081`  
**Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-TRK-081)

### 1.1 Problem Statement & Rationale
Cockpit sparklines currently lack persistent graph colors and default to theme or zone colors. Athletes cannot distinguish adjacent tiles of the same metric (e.g. 3s Power vs 30s Power) or customize telemetry graph colors per tile. Providing per-tile color override options from the centralized 8-color palette, Theme Default, and Dynamic Zone (for HR, Power, HRV DFA α1), paired with a compact 2-segment scope toggle ("This tile" vs "All <Metric>") and SQLite schema v22 persistence, enables customized live data differentiation while preserving global preference coherence and legacy backup compatibility (ATT-7760, refining `REQ-TRK-059` and `REQ-UI-530`).

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Database Schema v22 & Backup Compatibility (`REQ-TRK-081.1`)**: SQLite `DB_VERSION` in `TrackingViewsDatabaseManager.java` SHALL increment to 22, adding `GraphColor text DEFAULT 'GLOBAL_DEFAULT'` to `ROWS_TABLE`. Missing or null `graphColor` keys in `.attbackup` JSON SHALL default safely to `"GLOBAL_DEFAULT"`.
2. **Color Mode Model & State Threading (`REQ-TRK-081.2`)**: The system SHALL support `"GLOBAL_DEFAULT"`, `"THEME_DEFAULT"`, `"DYNAMIC_ZONE"`, and the 8 fixed palette color tokens (`CRIMSON`, `VIOLET`, `EMERALD`, `AMBER`, `TEAL`, `MAGENTA`, `CYAN`, `CORAL`). `SensorFieldConfig` and `SensorFieldState` SHALL include `graphColor`. `SensorFieldView.kt` SHALL resolve line colors deterministically.
3. **Minimalist Color Editor UI & Scope Toggle (`REQ-TRK-081.3`)**: When `isGraphSupported` and `showGraph` are true in `EditSensorFieldDialog.kt`, a compact row of circular swatches for the 8 palette colors, a `Theme Default` chip, and a `Dynamic Zone` chip (gated to `HR`, `POWER`, `HRV_DFA_A1`) SHALL render below the time-horizon slider with a 2-segment scope toggle (`This tile` | `All <Metric>`).
4. **9-Language Localization Parity (`REQ-TRK-081.4`)**: All new UI string resources SHALL maintain 100% translation parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`), verified by `TranslationParityTest`.
5. **Visual Compose Screenshot Verification (`REQ-TRK-081.5`)**: Per Rule 8.5, visual self-inspection SHALL capture `docs/engineering/walkthroughs/img/ATT-7760_color_picker.png` verifying the 4-point visual checklist.

### 1.4 Acceptance Criteria & Invariants
* *Given* an athlete edits a Power tile with `showGraph` enabled and scope `This tile`, *When* they select Cyan and save, *Then* only that tile persists `GRAPH_COLOR = CYAN`.
* *Given* an athlete edits a Heart Rate tile with scope `All HR`, *When* they select Violet and save, *Then* `TelemetryGraphColorPreferences` updates globally and the tile aligns to `"GLOBAL_DEFAULT"`.
* *Given* `EditSensorFieldDialog` is open, *When* sensor is `HR`, `POWER`, or `HRV_DFA_A1`, *Then* `Dynamic Zone` chip is visible; for other sensors, it is hidden.
* *Invariants*: SQLite backward compatibility guaranteed; legacy backups restore cleanly without layout reset; zero Compose snapshot state writes; 100% 9-language translation parity.

---

## 2. Test Specification (TST-TRK-081)

### Test Case 1: Database Schema v22 Migration & Backup Compatibility (`TST-TRK-081.1`)
* **Scope**: Unit Test (`TrackingViewsDbMigrationV22Test.kt`).
* **Assertions**: Upgrading v21 DB to v22 preserves layouts and adds `GraphColor text DEFAULT 'GLOBAL_DEFAULT'`; `.attbackup` import missing `graphColor` defaults cleanly.

### Test Case 2: Repository & State Flow Threading (`TST-TRK-081.2`)
* **Scope**: Unit Test (`TrackingViewsRepositoryTest.kt`).
* **Assertions**: `SensorFieldConfig` and `SensorFieldState` persist and propagate `graphColor` across insert, update, and query operations.

### Test Case 3: ViewModel Scope Toggle & Dynamic Zone Gating (`TST-TRK-081.3`)
* **Scope**: Unit Test (`EditSensorFieldViewModelTest.kt`).
* **Assertions**: Local scope persists per-tile `graphColor`; global scope updates `TelemetryGraphColorPreferences` and resets tile to `GLOBAL_DEFAULT`; dynamic zone chip visible strictly for HR, Power, HRV DFA α1.

### Test Case 4: Sparkline Color Resolution (`TST-TRK-081.4`)
* **Scope**: Unit Test (`SensorFieldViewTest.kt`).
* **Assertions**: Line stroke color resolves correctly across `GLOBAL_DEFAULT`, `THEME_DEFAULT`, `DYNAMIC_ZONE`, and palette colors under Light and Dark themes.

### Test Case 5: 9-Language Localization Parity (`TST-TRK-081.5`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`).
* **Assertions**: 100% translation parity and format specifier symmetry across all 9 locales.

### Test Case 6: Visual Compose Screenshot Verification (`TST-TRK-081.6`)
* **Scope**: UI Screenshot Test (`EditSensorFieldDialogScreenshotTest.kt`, Rule 8.5).
* **Assertions**: Compose screenshot saved to `docs/engineering/walkthroughs/img/ATT-7760_color_picker.png` certified against 4-point visual checklist.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-081.1]` | Unit | DB v22 migration & `.attbackup` JSON compatibility | `REQ-TRK-081.1` | Planned |
| `[TST-TRK-081.2]` | Unit | Repository CRUD & `SensorFieldState` threading | `REQ-TRK-081.2` | Planned |
| `[TST-TRK-081.3]` | Unit | ViewModel scope toggle & dynamic zone option gating | `REQ-TRK-081.3` | Planned |
| `[TST-TRK-081.4]` | Unit | Sparkline line color resolution across themes | `REQ-TRK-081.2` | Planned |
| `[TST-TRK-081.5]` | Localization | 9-locale parity via `TranslationParityTest` | `REQ-TRK-081.4` | Planned |
| `[TST-TRK-081.6]` | Screenshot | Compose screenshot & 4-point visual checklist (Rule 8.5) | `REQ-TRK-081.5` | Planned |
