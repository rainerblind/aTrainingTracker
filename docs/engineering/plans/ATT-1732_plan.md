# Stage 3 Implementation Plan: ATT-1732

## 1. Ticket & Metadata
- **Parent Ticket**: [ATT-1732](https://atrainingtracker.atlassian.net/browse/ATT-1732) - `[Filter] Rename 'Lieblingsorte' section heading in Filter dialogs to 'Start at'`
- **Subtask**: [ATT-1792](https://atrainingtracker.atlassian.net/browse/ATT-1792) - `Stage 3: Implementation Plan`
- **Target Version**: `V4.9.38`
- **Target Branch**: `feature/ATT-1732`
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Architectural Overview (SWE.2)

```
                            strings.xml (9 locales)
                        [filter_section_start_at]
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   WorkoutFilterBottomSheet                  ClusterFilterBottomSheet
  (Section 8: Location Filter)              (Section 4/5: Location Filter)
```

The change updates the section heading string reference for starting location filters across both bottom sheets without altering underlying filtering logic, spatial math, or other UI components.

---

## 3. Atomic Implementation Steps

### Step 1: 9-Language Resource Definitions
- **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
- **Content**:
  Add `<string name="filter_section_start_at">...</string>` with localized translations:
  - `values`: `Start at`
  - `values-de`: `Startet bei`
  - `values-es`: `Comienza en`
  - `values-fr`: `Départ à`
  - `values-it`: `Partenza da`
  - `values-ja`: `開始地点`
  - `values-nl`: `Start bij`
  - `values-pl`: `Start w`
  - `values-pt`: `Início em`

### Step 2: Update WorkoutFilterBottomSheet UI
- **Target File**: [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt)
- **Modification**:
  - In Section 8 (`// 8. Favorite Locations (Lieblingsorte, REQ-UI-187, REQ-UI-194)`), replace:
    ```kotlin
    text = stringResource(R.string.known_locations_title)
    ```
    with:
    ```kotlin
    text = stringResource(R.string.filter_section_start_at)
    ```

### Step 3: Update ClusterFilterBottomSheet UI
- **Target File**: [ClusterFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt)
- **Modification**:
  - In Section 4/5 (`// 5. Favorite Locations Selection (Lieblingsorte, REQ-UI-187, REQ-UI-194)`), replace:
    ```kotlin
    text = stringResource(R.string.known_locations_title)
    ```
    with:
    ```kotlin
    text = stringResource(R.string.filter_section_start_at)
    ```

### Step 4: Unit Testing & Localization Verification
- **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterSectionHeadingLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterSectionHeadingLocalizationTest.kt`
- **Test Implementation**:
  - `FilterSectionHeadingLayoutTest`: Asserts source code contracts:
    - `WorkoutFilterBottomSheet.kt` references `R.string.filter_section_start_at`.
    - `ClusterFilterBottomSheet.kt` references `R.string.filter_section_start_at`.
    - `KnownLocationsScreen.kt` preserves `R.string.known_locations_title`.
  - `FilterSectionHeadingLocalizationTest`: Parses XML files across all 9 language directories and validates exact values.
- **Targeted Test Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.common.filters.*"
  ```

---

## 4. Invariant Protection & Scope Bounding

1. **KnownLocationsScreen Preserved**: Top app bar title remains `R.string.known_locations_title` (*"Lieblingsorte"* / *"Favorite Locations"*).
2. **ATT-1731 Scope Respected**: Section 9 (*Lieblingsstrecken*) in `WorkoutFilterBottomSheet.kt` remains completely intact and will be removed under ticket ATT-1731.
3. **No Logic Regressions**: Spatial geofencing (`WorkoutClusterEngine.distanceBetween`), location chip toggle (`localStartLocationLat`, `localStartLocationLng`), active filter chips row, and dismiss callbacks remain 100% operational.
4. **9-Language Parity**: All 9 language resource files updated with 0 missing entries.

---

## 5. Gate 3 Readiness
- Plan is atomic, sequential, testable, and bounded.
- Ready for Gate 3 audit.
