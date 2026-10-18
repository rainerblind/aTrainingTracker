# Walkthrough: Support Configurable Horizontal Section Dividers with Optional Mini-Titles Between Cockpit Rows (ATT-4055)

* **Parent Ticket**: [ATT-4055](https://rainerblind.atlassian.net/browse/ATT-4055) (*[Verbesserung] Support configurable horizontal section dividers with optional mini-titles between Cockpit rows*)
* **Subtasks**:
  - [ATT-4111](https://rainerblind.atlassian.net/browse/ATT-4111) (*[Analysis] Support configurable horizontal section dividers with optional mini-titles between Cockpit rows* - Erledigt)
  - [ATT-4112](https://rainerblind.atlassian.net/browse/ATT-4112) (*[Req & Test Spec] Support configurable horizontal section dividers with optional mini-titles between Cockpit rows* - Erledigt)
  - [ATT-4113](https://rainerblind.atlassian.net/browse/ATT-4113) (*[Impl-Plan] Support configurable horizontal section dividers with optional mini-titles between Cockpit rows* - Erledigt)
  - [ATT-4114](https://rainerblind.atlassian.net/browse/ATT-4114) (*[Implementation] Support configurable horizontal section dividers with optional mini-titles between Cockpit rows* - Erledigt)
  - [ATT-4115](https://rainerblind.atlassian.net/browse/ATT-4115) (*[Test] Support configurable horizontal section dividers with optional mini-titles between Cockpit rows* - In Review)
* **Requirement**: `REQ-UI-411` (Verified)
* **Test Specification**: `TST-UI-374` (Verified)
* **Target Version**: `V4.9.40`
* **Branch**: `feature/ATT-4055`

---

## 1. Executive Summary

When a Cockpit tab (`SensorGridScreen`) contains 5–7 rows of sensor metrics, adjacent rows visually blend into a dense "wall of numbers", making sub-second handlebar glanceability difficult during intense rides or runs. Previously, `SensorFieldStyle.kt` only provided all-or-nothing tile styling (border contrast / card elevation) across every individual field, without any semantic grouping entity between rows (e.g. separating *Live Effort* from *Power & Pacing*, *Lap*, or *Trip* metrics).

We resolved this issue by introducing configurable horizontal section dividers with optional mini-titles between Cockpit rows, featuring:
1. **Schema Migration & Row-Shift Tracking (`FlexibleGenericView.db` v14)**:
   - Bumped `DB_VERSION` to 14 and created `SectionDividersTable` with `UNIQUE(ViewId, RowNr) ON CONFLICT REPLACE`.
   - Guaranteed transactional divider shifting on row insertion in `insertSensorFieldAt`, row re-indexing in `normalizeGrid`, and cascading cleanup in `deleteTabView`.
2. **Reactive Flow & Hoisted State Integration**:
   - Exposed `getSectionDividersFlow` from `TrackingViewsRepository`, feeding `TrackingScreenState.sectionDividers` via `TrackingViewModel`.
3. **Glanceable Compose Divider & Dialog**:
   - Implemented `CockpitSectionDivider` with 1.5 dp line and uppercase bold mini-title flanked by divider rules, strictly bounded to $\le 20\text{ dp}$ height and single-line truncation (`maxLines = 1`, `TextOverflow.Ellipsis`).
   - Implemented `EditSectionDividerDialog` in `ScreenMode.CONFIGURATION` featuring localized quick-pick preset chips (*Power*, *Heart Rate*, *Lap*, *Pacing*, *Navigation*, *Total*) and custom title input.
4. **9-Language Localization Parity**:
   - Added 11 localized strings across all 9 supported application languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Changes Summary

| Area | Component | Change | Description |
| :--- | :--- | :--- | :--- |
| **Database** | `TrackingViewsDatabaseManager.java` | [MODIFY] | Incremented `DB_VERSION` to 14, created `SectionDividersTable`, added `SectionDividerData`, row shift logic in `insertSensorFieldAt`, and normalization in `normalizeGrid`. |
| **Repository & State** | `TrackingViewsRepository.kt` | [MODIFY] | Added `SectionDividerInfo` data class, `getSectionDividersFlow`, `setSectionDivider`, and `deleteSectionDivider`. |
| **ViewModel & Actions** | `TrackingViewModel.kt` | [MODIFY] | Added `sectionDividers` state flow aggregation in `loadSensorFieldStates`, and `onSetSectionDivider` / `onDeleteSectionDivider` actions. |
| **UI Rendering & Dialog** | `SensorGridScreen.kt` | [MODIFY] | Implemented `CockpitSectionDivider`, `EditSectionDividerDialog`, configuration mode inter-row actions, and tracking/preview divider rendering. |
| **UI Wiring** | `TrackingTabGridContent.kt` | [MODIFY] | Forwarded `onSetSectionDivider` and `onDeleteSectionDivider` to `GridActions`. |
| **Localization** | `res/values*/strings.xml` | [MODIFY] | Added 11 localized strings across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`. |
| **Unit Tests** | `TrackingViewsDatabaseManagerTest.kt` | [MODIFY] | Added unit tests verifying v14 migration, divider CRUD, insert row-shifting, and grid normalization. |
| **Unit Tests** | `TrackingViewsRepositoryTest.kt` | [MODIFY] | Added unit tests verifying flow emission and database delegation. |
| **Unit Tests** | `TrackingViewModelGridTest.kt` | [MODIFY] | Added unit tests verifying divider action dispatching. |
| **Unit Tests** | `SensorGridLocalizationTest.kt` | [MODIFY] | Added unit tests asserting 100% non-blank string parity for all 11 divider keys across all 9 languages. |
| **Living Requirements** | `docs/requirements.md` | [MODIFY] | Added `REQ-UI-411` with Given-When-Then criteria and updated status to `Verified`. |
| **Living Test Specs** | `docs/tests.md` | [MODIFY] | Added `TST-UI-374` verification suite specification and updated status to `Verified`. |

---

## 3. Verification & Test Results

### 3.1 Automated Tests
* **SQLite Database Migration & CRUD Tests (`TrackingViewsDatabaseManagerTest.kt`)**:
  - `sectionDividers_schemaUpgradeAndCrudOperations`: PASSED (verified table creation on v14 upgrade, set, get, delete).
  - `sectionDividers_rowInsertionShiftsDividersAndNormalizationCleansUp`: PASSED (verified row 2 insertion shifts row 2 & 4 dividers to row 3 & 5, and normalizeGrid maintains alignment).
* **Repository & ViewModel Unit Tests (`TrackingViewsRepositoryTest.kt`, `TrackingViewModelGridTest.kt`)**:
  - `getSectionDividersFlow_mapsDataCorrectly`: PASSED.
  - `setSectionDivider_delegatesToViewsDbManager`: PASSED.
  - `deleteSectionDivider_delegatesToViewsDbManager`: PASSED.
  - `testSetSectionDivider_dispatchesToRepository`: PASSED.
  - `testDeleteSectionDivider_dispatchesToRepository`: PASSED.
* **9-Language Localization Parity Tests (`SensorGridLocalizationTest.kt`)**:
  - `testSensorGridStrings_completeAcrossAllLanguages`: PASSED (144 checks passed across 9 locales).
* **Clean-Room Full Suite Regression Execution**:
  - Command: `./gradlew testDebugUnitTest`
  - Result: **3169/3169 tests passing (100% GREEN, 0 regressions)**.

### 3.2 Requirement Governance Verification
* Script: `python3 tools/verify_requirement_governance.py`
* Result: Clean execution, 100% compliance.

---

## 4. UI Consistency & Visual Verification

* **Visual Consistency Standard**: Adheres strictly to `docs/design_guidelines.md` Section 5:
  - *Color Tokens*: Uses `MaterialTheme.colorScheme.outlineVariant` for divider stroke lines and `MaterialTheme.colorScheme.primary` for mini-title text.
  - *Typography*: Uses `MaterialTheme.typography.labelSmall` in uppercase with bold weight and tracking for optimal handlebar readability.
  - *Height & Compactness*: Bounded to $\le 20\text{ dp}$ total height overhead, ensuring that sensor metric font sizes and the map viewport never shrink.
  - *Preset Chips*: Provides localized chips (`Power`, `Heart Rate`, `Lap`, `Pacing`, `Navigation`, `Total`) in `AssistChip` style with semantic labels.
  - *Style Preset Harmony*: Seamlessly complements all 4 `SensorFieldStyle` presets (`CLASSIC_SEAMLESS`, `OUTLINED_TILES`, `ELEVATED_CARDS`, `SOFT_CAPSULES`) in both Light and Dark themes.
