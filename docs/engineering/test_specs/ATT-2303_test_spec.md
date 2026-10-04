# Stage 2: Requirement & Test Specification - ATT-2303: Filter Workouts by Origin Source Attribute (Tracked, TCX, GPX, FIT)

**Ticket**: [ATT-2303](https://rainerblind.atlassian.net/browse/ATT-2303)  
**Sub-task**: [ATT-2334](https://rainerblind.atlassian.net/browse/ATT-2334) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-265` (*Workout List Filtering by Origin Source Attribute*)  
**Test Spec ID**: `TST-UI-224`  
**Branch**: `feature/ATT-2303`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-UI-265)

### 1.1 Problem Statement & Rationale
With the introduction of origin provenance tracking in ATT-2186 (`REQ-DAT-017`), workout sessions are tagged with their ingestion source (`WorkoutSource`: `TRACKED`, `TCX`, `GPX`, `FIT`). However, the workout list filter system (`WorkoutFilterCriteria`, `WorkoutFilterBottomSheet`, `ActiveFilterChipsRow`) lacked support for this dimension, preventing athletes from filtering the workout journal to isolate imported files or focus exclusively on device-recorded workouts.

### 1.2 Functional & Architectural Requirements
The system SHALL provide origin source filtering for workout sessions across the domain model, modal filter bottom sheet, active filter strip, and reactive ViewModel pipeline (ATT-2303):

1. **Origin Source Filter Domain Model (`WorkoutFilterCriteria.kt`)**:
   - `WorkoutFilterCriteria` SHALL define nullable property: `val source: WorkoutSource? = null`.
   - *Predicate Evaluation (`matches`)*: When `source != null`, the system SHALL evaluate `workout.source`. If `workout.source != source`, `matches` SHALL return `false`.
   - `activeFilterCount` SHALL increment by 1 when `source != null`.
   - `toJson()` and `fromJson()` SHALL serialize and deserialize `source` via its enum name (`WorkoutSource.valueOf` / `fromString`), falling back safely to `null` if absent.
2. **Origin Source Filter Section (`WorkoutFilterBottomSheet.kt`)**:
   - `WorkoutFilterBottomSheet` SHALL render an "Origin Source" section (`filter_section_origin_source`) positioned directly after Workout Attributes (section 5) and before Distance Intervals (section 6), respecting the prioritized layout order of `REQ-UI-194`.
   - The section SHALL render 4 single-selectable `FilterChip` items corresponding to `WorkoutSource.TRACKED` (`workout_source_tracked`), `WorkoutSource.TCX` (`workout_source_tcx`), `WorkoutSource.GPX` (`workout_source_gpx`), and `WorkoutSource.FIT` (`workout_source_fit`).
   - Tapping an unselected chip SHALL set the source filter to that option. Tapping the already selected chip SHALL deselect it (`source = null`).
   - `onApply` SHALL propagate the selected `source` in the updated `WorkoutFilterCriteria`.
   - `onClearAll` SHALL reset `source = null`.
3. **Active Filter Chip Presentation (`ActiveFilterChipsRow.kt`)**:
   - When `criteria.source != null`, `ActiveFilterChipsRow` SHALL render an active removable filter chip labeled with the localized name of the active origin source (using `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, or `workout_source_fit`).
   - Tapping the remove ('X') icon SHALL invoke `onRemoveSource: () -> Unit`, clearing the source filter dimension while preserving all other active criteria.
4. **Screen & ViewModel Reactive Wiring (`WorkoutTabsScreen.kt`, `WorkoutSummariesViewModel.kt`)**:
   - `WorkoutTabsScreen` SHALL supply `onRemoveSource = { onUpdateFilterCriteria { it.copy(source = null) } }` to `ActiveFilterChipsRow`.
   - `WorkoutSummariesViewModel.workouts` reactive combining pipeline SHALL evaluate `criteria.matches(it)` over all workouts, updating the emitted list instantly without database re-queries.
5. **100% 9-Language Localization Parity**:
   - String resource `filter_section_origin_source` SHALL be defined across all 9 supported application locales (EN: "Origin Source", DE: "Datenquelle", ES: "Origen de datos", FR: "Source de données", IT: "Origine dati", JA: "データソース", NL: "Gegevensbron", PL: "Źródło danych", PT: "Origem dos dados").
   - Existing string resources `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, and `workout_source_fit` SHALL be verified across all 9 supported locales.
6. **Preservation of Functional Invariants**:
   - Sport tabs (`BSportType`), search queries, date/time ranges, equipment selections, distance/duration intervals, favorite locations, route clusters, sort orders, and DataStore serialization backward compatibility MUST NOT be altered or regressed.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Filter Options Availability)**:
  * *Given* an athlete opening `WorkoutFilterBottomSheet` in the Workouts journal,
  * *When* viewing filter sections,
  * *Then* an "Origin Source" section SHALL be visible with chips for Tracked, TCX, GPX, and FIT.
* **Criterion 2 (Reactive List Filtering)**:
  * *Given* a workout list containing both live tracked sessions and imported FIT/TCX/GPX workouts,
  * *When* the athlete selects the "FIT" source chip and applies filters,
  * *Then* only workouts with `source == WorkoutSource.FIT` SHALL be displayed in the workout list.
* **Criterion 3 (Active Chip Feedback & Single-Tap Removal)**:
  * *Given* an active source filter (e.g. "FIT"),
  * *When* inspecting `ActiveFilterChipsRow`,
  * *Then* an active removable chip "FIT" SHALL be displayed, and tapping its remove icon SHALL clear the source filter while preserving other active filters.
* **Criterion 4 (Reset & Independence)**:
  * *Given* an active source filter along with other filters (e.g. Sport = Bike, Year = 2024),
  * *When* tapping "Alle zurücksetzen" (Clear All) in the filter sheet or the strip,
  * *Then* all filters including `source` SHALL be reset to null, restoring the full unfiltered list.

---

## 2. Test Specification (TST-UI-224)

### 2.1 Verification Approach
Testing will follow a multi-tier automated test strategy covering pure domain predicate matching, JSON serialization round-trips, ViewModel reactive StateFlow emission, and 9-language translation audit:

1. **`TST-UI-224.1`: Domain Model & Predicate Tests (`WorkoutFilterCriteriaTest.kt`)**:
   - Test predicate matching against each `WorkoutSource` (`TRACKED`, `TCX`, `GPX`, `FIT`).
   - Test non-matching workouts rejection.
   - Test null source filter matching all workouts.
   - Test `activeFilterCount` derivation when `source != null`.
   - Test JSON serialization and deserialization round-trip for each source.
   - Test backward compatibility: JSON without `"source"` key deserializes to `source = null`.
2. **`TST-UI-224.2`: ViewModel Reactive Filtering Tests (`WorkoutSummariesViewModelFilterTest.kt`)**:
   - Seed `WorkoutRepository` with a list containing mixed sources (Tracked, TCX, GPX, FIT).
   - Verify `setFilterCriteria(WorkoutFilterCriteria(source = WorkoutSource.TCX))` restricts `workouts` flow strictly to TCX items.
   - Verify `setFilterCriteria(WorkoutFilterCriteria(source = WorkoutSource.TRACKED))` restricts `workouts` flow strictly to TRACKED items.
   - Verify multi-criteria conjunction (e.g. `source = WorkoutSource.FIT` AND `year = 2024`).
   - Verify `clearFilterCriteria()` immediately restores all workouts.
3. **`TST-UI-224.3`: 9-Language Localization Audit (`TranslationParityTest.kt`)**:
   - Verify `filter_section_origin_source` exists in all 9 localized `strings.xml` files with non-blank values.
   - Verify `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, `workout_source_fit` exist in all 9 locales.
4. **`TST-UI-224.4`: Clean-Room Full Suite Regression Execution**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Class / Implementation | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `REQ-UI-265.1` (Domain Model & Predicate) | `TST-UI-224.1` | `WorkoutFilterCriteriaTest.kt` | Predicate matching, active count, and JSON round-trip pass 100%. |
| `REQ-UI-265.2` (Filter Bottom Sheet UI) | `TST-UI-224.2` | `WorkoutSummariesViewModelFilterTest.kt` | Setting criteria filters workout list reactively. |
| `REQ-UI-265.3` (Active Filter Chip Strip) | `TST-UI-224.2` | `WorkoutSummariesViewModelFilterTest.kt` | Removing source filter clears source and restores list. |
| `REQ-UI-265.4` (ViewModel Combining Pipeline) | `TST-UI-224.2` | `WorkoutSummariesViewModelFilterTest.kt` | In-memory `combine` emits filtered workouts instantly. |
| `REQ-UI-265.5` (9-Language Parity) | `TST-UI-224.3` | `TranslationParityTest.kt` | All 9 locales verified with zero missing translations. |
| `REQ-PRO-001` (Clean-Room Regression) | `TST-UI-224.4` | `./gradlew testDebugUnitTest` | 100% full-suite regression pass rate. |
