# Stage 5: Walkthrough & Verification - ATT-2303: Filter Workouts by Origin Source Attribute (Tracked, TCX, GPX, FIT)

**Ticket**: [ATT-2303](https://rainerblind.atlassian.net/browse/ATT-2303)  
**Sub-task**: [ATT-2339](https://rainerblind.atlassian.net/browse/ATT-2339) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-265`  
**Test Mapping**: `TST-UI-224`  
**Branch**: `feature/ATT-2303`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the full verification and release qualification for [ATT-2303](https://rainerblind.atlassian.net/browse/ATT-2303). The workout journal now provides complete, multi-dimensional filtering by origin source provenance (live recorded tracking vs. imported TCX, GPX, or FIT activities):
1. **Extended Domain Model**: Added `val source: WorkoutSource? = null` to `WorkoutFilterCriteria.kt`, updated `activeFilterCount` (+1 when non-null), and extended predicate evaluation in `matches(workout)` to enforce `workout.source == source`.
2. **Robust Persistence & Serialization**: Implemented JSON serialization and deserialization in `toJson()` / `fromJson()` with safe exception handling and fallback to `null` on unknown values.
3. **Dedicated Bottom Sheet Section**: Added an "Origin Source" section (`filter_section_origin_source`) to `WorkoutFilterBottomSheet.kt`, positioned after Workout Attributes and before Distance Intervals per `REQ-UI-194`. Renders 4 single-select `FilterChip` items (Tracked, TCX, GPX, FIT) with one-tap selection and toggle dismissal.
4. **Active Filter Strip Integration**: Updated `ActiveFilterChipsRow.kt` and `WorkoutTabsScreen.kt` to display a removable chip when a source filter is active, labeled with the localized source name and allowing one-tap dismissal without opening the modal sheet.
5. **Reactive View Pipeline**: `WorkoutSummariesViewModel.workouts` combines `workoutRepo.allWorkouts` and dynamically filters matching workouts in-memory with zero database re-queries.
6. **100% 9-Language Localization**: Added `filter_section_origin_source` across all 9 supported Android language resource trees (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
7. **Clean-Room Regression**: Full clean-room test execution passed with 100% success across the test suite (zero failures, zero skipped in 7m 21s).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-265` | `TST-UI-224.1` | Unit Tests (`WorkoutFilterCriteriaTest`): source predicate matching, active filter count, JSON roundtrip & fallback | **PASSED** | `Verified` |
| `REQ-UI-265` | `TST-UI-224.2` | ViewModel Tests (`WorkoutSummariesViewModelFilterTest`): reactive source filtering, conjunction with sport, filter clearing | **PASSED** | `Verified` |
| `REQ-UI-265` | `TST-UI-224.3` | Localization Audit: `filter_section_origin_source` and source labels across all 9 language directories | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-224.4` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100% pass rate) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 7m 21s
32 actionable tasks: 12 executed, 20 up-to-date
All unit tests completed with 0 failures, 0 skipped
```

### Targeted Domain & ViewModel Tests
```text
WorkoutFilterCriteriaTest:
- testFilterByOriginSource_MatchesCorrectSource: PASSED (TRACKED, TCX, GPX, FIT matching)
- testFilterByOriginSource_ActiveFilterCount: PASSED (increment count by 1)
- testFilterByOriginSource_JsonSerializationDeserialization: PASSED (roundtrip + unknown value null fallback)

WorkoutSummariesViewModelFilterTest:
- testWorkoutsFlow_FilteredByOriginSource: PASSED (reactive GPX/FIT filtering and reset)
- testWorkoutsFlow_FilteredByOriginSourceAndSportConjunction: PASSED (conjunction of source and sport)
```

---

## 4. Hardware / Physical Verification (Pixel 10)

1. **APK Installation**:
   - Deployed debug APK via `./gradlew installDebug` onto physical test device (`Pixel 10 - 17`, ID `66020DLCR002FL`).
2. **Visual Inspection**:
   - Navigated to the Workouts journal and opened the Filter Bottom Sheet.
   - Verified that the "Herkunftsquelle" (Origin Source) section displays below workout attributes with chips for "Aufgezeichnet", "TCX", "GPX", and "FIT".
   - Verified that selecting a source chip filters the list immediately.
   - Verified that the active filter strip above the list renders a removable chip for the selected source.
   - Verified that tapping the 'X' on the active chip or using "Alle Filter zurücksetzen" clears the source filter immediately.

---

## 5. Invariant & Governance Verification

1. **Zero Database / DAO Regressions**: `WorkoutSource` database mapping remains stable; filtering operates cleanly over domain models in memory.
2. **Backward Compatibility**: JSON deserialization gracefully handles omitted or unrecognized source values without crashes or default corruption.
3. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-265`) and `docs/tests.md` (`TST-UI-224`) updated to `Verified`.
4. **Subtask Completion**: Stage 5 subtask `ATT-2339` transitioned to `Erledigt` via `freigabe`.
5. **Parent Ticket Final Review**: Parent ticket `ATT-2303` moved to `Final Review (Human)` and assigned to `human` per Rule 1 and Rule 14.
6. **Continuous Sprint Branch Integration (Strategy A)**: Merged `feature/ATT-2303` into `sprint/2026-40.15` via `--no-ff` and pruned the local feature branch.
