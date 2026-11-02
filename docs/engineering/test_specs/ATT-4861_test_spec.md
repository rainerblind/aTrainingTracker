# Stage 2: Requirement & Test Specification - ATT-4861: User-Configurable Horizontal Width Ratios in Cockpit Sensor Fields

**Ticket**: [ATT-4861](https://rainerblind.atlassian.net/browse/ATT-4861)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-UI-437` (`REQ-UI-437.1`..`REQ-UI-437.5`)  
**Test Mapping**: `TST-UI-437` (`TST-UI-437.1`..`TST-UI-437.5`)  
**Branch**: `feature/ATT-4861`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
In `SensorGridScreen.kt`, multi-column sensor fields currently hardcode `Modifier.weight(1f)`, forcing side-by-side fields in a row into equal horizontal widths (`1/2 : 1/2` or `1/3 : 1/3 : 1/3`). This specification establishes user-configurable horizontal width ratios (e.g. `3/4 : 1/4`, `2/3 : 1/3`, `1/2 : 1/2`, `1/3 : 2/3`, `1/4 : 3/4`), backward-compatible SQLite migration v16 $\rightarrow$ v17 with default `1.0` weight backfill, dual UX controls (individual relative weight + 2-field row split presets), and proportional Jetpack Compose layout rendering.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-UI-437.1` | `TST-UI-437.1` | `TrackingViewsRealSqliteMigrationTest` | SQLite v16 $\rightarrow$ v17 migration, `ColWeight real DEFAULT 1.0`, backfill existing rows, `DatabaseSchemaRegistry` parity. |
| `REQ-UI-437.2` | `TST-UI-437.2` | `TrackingViewsRepositoryTest` | `SensorFieldConfig` DTO and cursor mapping symmetry across all pathways (Rule 20). |
| `REQ-UI-437.3` | `TST-UI-437.3` | `EditSensorFieldViewModelTest` | Individual relative weight selection & 2-field row split presets (`3/4:1/4`, `2/3:1/3`, `1/2:1/2`, `1/3:2/3`, `1/4:3/4`). |
| `REQ-UI-437.4` | `TST-UI-437.4` | `SensorGridScreenLayoutTest` | Jetpack Compose layout rendering with `Modifier.weight(effectiveWeight)`, non-zero guard, and landscape packing parity. |
| `REQ-UI-437.5` | `TST-UI-437.5` | `SensorGridLocalizationTest` | 9-language localization parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-UI-437.1: SQLite Migration v16 $\rightarrow$ v17 & Default Weight Backfill
* **Given** an existing `FlexibleGenericView.db` / `TrackingViews.db` at schema version 16,
* **When** opened by `TrackingViewsDatabaseManager` v17,
* **Then** the database upgrades to v17, adds column `ColWeight`, backfills `ColWeight = 1.0` for all existing rows, and passes `DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 17)`.

### TST-UI-437.2: DTO & Cursor Mapping Symmetry
* **Given** a persisted sensor field row with `ColWeight = 3.0`,
* **When** queried through `TrackingViewsRepository.fetchSensorFieldConfigs()`,
* **Then** `SensorFieldConfig.colWeight` maps to `3.0f` symmetrically across all cursor mapping pathways.

### TST-UI-437.3: Edit Dialog Relative Weight & Row Split Presets
* **Given** an athlete editing a sensor field sharing a row with one other field,
* **When** selecting preset `3/4 : 1/4`,
* **Then** the current field weight updates to `3.0f` and the sibling field weight updates to `1.0f`, persisting across database re-read.

### TST-UI-437.4: Compose Row Layout Proportional Width Rendering
* **Given** two sensor fields in the same row with weights `3.0f` (Power Zone Bar) and `1.0f` (Power numeric value),
* **When** rendered in `SensorGridContent` or configuration mode,
* **Then** the left field consumes $75\%$ ($3/4$) and the right field consumes $25\%$ ($1/4$) of row width, with non-zero safety clamp.

### TST-UI-437.5: 9-Language Localization Audit
* **Given** localized string resources for width ratio titles and presets,
* **When** validated by `TranslationParityTest`,
* **Then** all 9 supported locales have complete parity with zero missing keys.

---

## 4. Invariants & Non-Functional Verification
1. **Backward Compatibility**: Default `ColWeight = 1.0` preserves existing 1:1 and 1:1:1 layout geometry.
2. **Compose Safety Invariant**: Weight $\le 0f$ is clamped to `1.0f` to prevent Compose layout crashes.
3. **Single Source of Truth**: Database schema version registered synchronously in `DatabaseSchemaRegistry.kt`.
4. **Clean-Room Test Pass Rate**: 100% pass across targeted unit tests and full Tier 1 regression suite.
