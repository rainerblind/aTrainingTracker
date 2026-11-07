# Stage 2: Requirement & Test Specification - ATT-5888: Uniform 'Clear all' Action in ActiveFilterChipsRow

**Ticket**: [ATT-5888](https://atrainingtracker.atlassian.net/browse/ATT-5888)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5888`  
**Requirement Mapping**: `REQ-UI-468` (*Uniform 'Clear all' Action Across Active Filter Chips Rows*)  
**Test Spec ID**: `TST-UI-468` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-468)

### 1.1 Problem Statement & Rationale
Across filterable list views, gating the "Clear all" button behind plural checks (`activeFilterCount > 1` or `>= 2`) created inconsistency with Favorite Locations (`ActiveKnownLocationsFilterChipsRow.kt`, which shows it unconditionally whenever `activeFilterCount >= 1`) and prevented athletes from resetting single default filters (such as `minHitCount = 5` on Favorite Routes) in one tap. The system SHALL display the "Clear all" (`R.string.filter_clear_all`) action button unconditionally whenever any filter criterion is active.

### 1.2 Functional & Architectural Requirements
1. **Unconditional Clear All Visibility (`REQ-UI-468.1`)**: In `ActiveFilterChipsRow.kt` (Workouts), `ActiveSegmentFilterChipsRow.kt` (Segments), `ActiveRouteFilterChipsRow.kt` (Routes), `ActiveClusterFilterChipsRow.kt` (Favorite Routes), `ActiveDeviceFilterChipsRow.kt` (Sensors/Devices), and `ActiveKnownLocationsFilterChipsRow.kt` (Favorite Locations), the "Clear all" action button SHALL be displayed unconditionally in the `LazyRow` whenever `!criteria.isEmpty` (`activeFilterCount >= 1`).
2. **Removal of Plural Guards (`REQ-UI-468.2`)**: The conditional guards `if (criteria.activeFilterCount > 1)` and `if (criteria.activeFilterCount >= 2)` MUST NOT be present in any of the 6 `Active*FilterChipsRow` composables.
3. **Row Suppression on Empty State (`REQ-UI-468.3`)**: When `criteria.isEmpty` is true (`activeFilterCount == 0`), the entire composable row SHALL return immediately without rendering any layout, spacer, or "Clear all" button.
4. **Action Styling & Alignment (`REQ-UI-468.4`)**: The button SHALL be rendered as a `TextButton(onClick = onClearAll)` with `Modifier.padding(start = 4.dp)` displaying `Text(stringResource(R.string.filter_clear_all), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)`.
5. **9-Language Localization & Accessibility (`REQ-UI-468.5`)**: `filter_clear_all` SHALL maintain 100% parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with touch target $\ge 32\text{dp}$ and zero inline emojis (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Single Active Filter)**:
  * *Given* exactly 1 active filter criterion on any filterable list screen,
  * *When* the active filter chips row renders,
  * *Then* both the individual filter chip and the "Alle zurücksetzen" / "Clear all" button are visible.
* **Criterion 2 (Multiple Active Filters)**:
  * *Given* $\ge 2$ active filter criteria on any filterable list screen,
  * *When* the active filter chips row renders,
  * *Then* all active chips and the "Alle zurücksetzen" / "Clear all" button are visible.
* **Criterion 3 (Empty State Suppression)**:
  * *Given* zero active filters (`criteria.isEmpty == true`),
  * *When* the active filter chips row evaluates,
  * *Then* the function returns immediately, rendering zero elements.
* **Criterion 4 (Reset Action)**:
  * *Given* active filters on any list screen,
  * *When* the athlete taps "Alle zurücksetzen" / "Clear all",
  * *Then* `onClearAll` is invoked, clearing all active filters.

### 1.4 System Invariants
* Filter criteria data classes, ViewModel state flow, and `matches()` filtering algorithms MUST NOT be altered.
* Individual chip dismissal callbacks (`onRemove*`) remain fully operational.
* 100% 9-language translation parity with zero format specifier mismatches.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-468` refining `REQ-UI-132`, `REQ-UI-351`, `REQ-UI-431`, `REQ-UI-466`, and `REQ-UI-467`.
* **Historical Origin & Commit Trace**: `ATT-1402` (commit `91acde33`), `ATT-3166` (commit `a9d4791c`), `ATT-5822` (commit `9fa4429e`), `ATT-5823` (commit `3b1f3b33`).
* **Root Reason for Existing Formulation**: A legacy plural heuristic (`activeFilterCount > 1`) assumed "Clear all" was redundant for a single filter dimension. In practice, default filters (such as `minHitCount = 5` on Favorite Routes) left athletes without a prominent reset button.
* **Preservation of Core Invariants**: `criteria.isEmpty` remains the sole visibility gate for the row; individual chip removal and full reset callbacks remain intact; zero empty-state leakage; 100% 9-language translation parity.

---

## 2. Test Specification (TST-UI-468)

### Test Case 1: Structural Contract Test (`TST-UI-468.1`)
* **Scope**: Contract Test (`ActiveFilterChipsRowClearAllContractTest.kt`).
* **Assertions**:
  1. None of the 6 `Active*FilterChipsRow` files contain `activeFilterCount > 1` or `activeFilterCount >= 2`.
  2. All 6 files contain references to `R.string.filter_clear_all`.
  3. All 6 files contain `if (criteria.isEmpty) return`.

### Test Case 2: 9-Language Localization Audit (`TST-UI-468.2`)
* **Scope**: `TranslationParityTest.kt`.
* **Assertions**: `R.string.filter_clear_all` exists and matches across all 9 locales.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-468.3`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.segments.segmentlist.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass rate across touched UI packages and build audits.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-468.1]` | Contract | `ActiveFilterChipsRowClearAllContractTest.kt`: No plural guards, uniform clear_all in all 6 files | `REQ-UI-468.1`, `REQ-UI-468.2` | Specified |
| `[TST-UI-468.2]` | Localization | `TranslationParityTest.kt`: Parity for `filter_clear_all` across 9 locales | `REQ-UI-468.5`, `REQ-LOC-001` | Specified |
| `[TST-UI-468.3]` | Regression | Tier 1 targeted suite (`workoutlist.*`, `segmentlist.*`, `routes.*`, `clusters.*`, `build.*`) | `REQ-PRO-001` | Specified |
