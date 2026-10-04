# Stage 3: Architecture & Implementation Plan - ATT-2303: Filter Workouts by Origin Source Attribute (Tracked, TCX, GPX, FIT)

**Ticket**: [ATT-2303](https://atrainingtracker.atlassian.net/browse/ATT-2303)  
**Sub-task**: [ATT-2335](https://atrainingtracker.atlassian.net/browse/ATT-2335) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-265` (*Workout List Filtering by Origin Source Attribute*)  
**Test Mapping**: `TST-UI-224` (*Workout List Filtering by Origin Source Attribute Verification*)  
**Branch**: `feature/ATT-2303`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

With the introduction of origin provenance tracking in ATT-2186 (`REQ-DAT-017`), workout sessions are tagged with their ingestion source (`WorkoutSource`: `TRACKED`, `TCX`, `GPX`, `FIT`). However, the workout list filter system (`WorkoutFilterCriteria`, `WorkoutFilterBottomSheet`, `ActiveFilterChipsRow`) lacked support for this dimension, preventing athletes from filtering the workout journal to isolate imported files or focus exclusively on device-recorded workouts.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-265` (*Workout List Filtering by Origin Source Attribute (Tracked, TCX, GPX, FIT)*)
* **Test Mapping**: `TST-UI-224` (`TST-UI-224.1` to `TST-UI-224.4`)
* **Living Documentation**: `docs/requirements.md` (`REQ-UI-265`), `docs/tests.md` (`TST-UI-224`).

---

## 3. System Invariants & Preserved Behavior

1. **DataStore Backward Compatibility**: Any existing JSON persisted in DataStore without the `"source"` key must safely deserialize to `source = null`.
2. **Reactive In-Memory Evaluation**: The ViewModel combining pipeline (`workoutRepo.allWorkouts`, `_sortOrder`, `_filterCriteria`) must evaluate `criteria.matches(it)` with zero extra database I/O.
3. **Ergonomic Section Ordering (`REQ-UI-194`)**: The Origin Source filter section must be placed with discrete workout metadata attributes (directly after Workout Attributes and before Distance/Duration Intervals), preserving Favorite Locations and Route Clusters at the bottom of the bottom sheet.
4. **Single-Choice Toggle Mechanics**: Selecting a source chip sets `localSource = option`; selecting the already active chip deselects it (`localSource = null`), matching existing attribute toggle conventions.
5. **9-Language Localization Parity**: All string resources must exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. **Mandatory Programmatic Pre-Check**: Before Stage 4 code edits, `python3 tools/jira_util.py check-gate ATT-2335` must exit with code 0 (`GATE_PASSED: ATT-2335 is Erledigt`).

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Data & Domain Layer: `WorkoutFilterCriteria.kt`
* Add `val source: WorkoutSource? = null` to data class constructor.
* In `activeFilterCount`: increment count when `source != null`.
* In `matches(workout: WorkoutData)`:
  ```kotlin
  if (source != null && workout.source != source) {
      return false
  }
  ```
* In `toJson()`: write `source?.let { json.put("source", it.name) }`.
* In `fromJson()`: read `source = if (json.has("source")) WorkoutSource.fromString(json.optString("source")) else null`.

### 4.2 Presentation Layer: `WorkoutFilterBottomSheet.kt`
* Add local state: `var localSource by remember(criteria.source) { mutableStateOf(criteria.source) }`.
* In `onClearAll`: reset `localSource = null`.
* In `onApply`: pass `source = localSource` into updated criteria.
* Add Origin Source section:
  ```kotlin
  Column {
      Text(
          text = stringResource(R.string.filter_section_origin_source),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(6.dp))
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
          val sources = listOf(
              WorkoutSource.TRACKED to R.string.workout_source_tracked,
              WorkoutSource.TCX to R.string.workout_source_tcx,
              WorkoutSource.GPX to R.string.workout_source_gpx,
              WorkoutSource.FIT to R.string.workout_source_fit
          )
          sources.forEach { (src, labelRes) ->
              FilterChip(
                  selected = (localSource == src),
                  onClick = { localSource = if (localSource == src) null else src },
                  label = { Text(stringResource(labelRes)) }
              )
          }
      }
  }
  ```

### 4.3 Presentation Layer: `ActiveFilterChipsRow.kt` & `WorkoutTabsScreen.kt`
* In `ActiveFilterChipsRow.kt`:
  - Add parameter `onRemoveSource: () -> Unit = {}`.
  - Render removable filter chip when `criteria.source != null` using localized label (`workout_source_tracked`, `workout_source_tcx`, etc.).
* In `WorkoutTabsScreen.kt`:
  - Forward `onRemoveSource = { onUpdateFilterCriteria { it.copy(source = null) } }`.

### 4.4 Localization: 9-Language Parity
* Add `filter_section_origin_source` across all 9 `strings.xml` files.

---

## 5. Atomic Implementation Steps (SWE.3)

| Step | Action | Target Files | Verification Method |
| :--- | :--- | :--- | :--- |
| **Step 1** | Extend `WorkoutFilterCriteria` with `source: WorkoutSource?`, update predicate, count, and JSON serialization. | `WorkoutFilterCriteria.kt` | `WorkoutFilterCriteriaTest.kt` |
| **Step 2** | Add `filter_section_origin_source` string to all 9 `strings.xml` locales. | `app/src/main/res/values*/strings.xml` | `TranslationParityTest.kt` |
| **Step 3** | Add Origin Source section with `FilterChip` items in `WorkoutFilterBottomSheet.kt`. | `WorkoutFilterBottomSheet.kt` | UI compilation & contract test |
| **Step 4** | Add active source chip to `ActiveFilterChipsRow.kt` and wire `onRemoveSource` in `WorkoutTabsScreen.kt`. | `ActiveFilterChipsRow.kt`, `WorkoutTabsScreen.kt` | UI compilation & tests |
| **Step 5** | Author unit tests verifying domain predicate matching, serialization, and ViewModel reactive filtering. | `WorkoutFilterCriteriaTest.kt`, `WorkoutSummariesViewModelFilterTest.kt` | `./gradlew testDebugUnitTest --tests "...WorkoutFilterCriteriaTest" --tests "...WorkoutSummariesViewModelFilterTest"` |
| **Step 6** | Clean-room full repository regression verification. | Full repository | `./gradlew testDebugUnitTest` |
