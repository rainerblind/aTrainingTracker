# Implementation Plan: ATT-494 - Cluster Naming Dialog Localization

## 1. Goal Description
Resolve missing translations in `ClusterNamingDialog`:
1. **Define String Resources (`strings.xml`)**: Add 10 localized string keys (`cluster_naming__*`) across default `values/strings.xml` and all 8 supported translation files (`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
2. **Refactor Composables (`ImportBackupTabsScreen.kt`)**: Replace all 10 hardcoded string literals inside `ClusterNamingDialog` with `stringResource()` calls using positional format specifiers.

---

## 2. Proposed Changes

### Component 1: `app/src/main/res/values*/strings.xml`
#### [MODIFY] [strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml)
- Add 10 string keys:
  - `cluster_naming__select_existing_route`
  - `cluster_naming__title`
  - `cluster_naming__found_recurring`
  - `cluster_naming__task_count`
  - `cluster_naming__assignment_label`
  - `cluster_naming__create_new`
  - `cluster_naming__selected_route_label`
  - `cluster_naming__new_name_prompt`
  - `cluster_naming__new_route_name_label`
  - `cluster_naming__leave_unclustered`
- Translate all 10 keys across `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.

### Component 2: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- In `ClusterNamingDialog`:
  - Replace `"Select Existing Route"` with `stringResource(R.string.cluster_naming__select_existing_route)`
  - Replace `"Assign Workout to Route"` with `stringResource(R.string.cluster_naming__title)`
  - Replace `"Found a recurring route from ${state.date}."` with `stringResource(R.string.cluster_naming__found_recurring, state.date)`
  - Replace `"Task 1 of $queueCount"` with `stringResource(R.string.cluster_naming__task_count, 1, queueCount)`
  - Replace `"Route Assignment:"` with `stringResource(R.string.cluster_naming__assignment_label)`
  - Replace `"Create New..."` with `stringResource(R.string.cluster_naming__create_new)`
  - Replace `"Selected Route"` with `stringResource(R.string.cluster_naming__selected_route_label)`
  - Replace `"Or give it a new name:"` with `stringResource(R.string.cluster_naming__new_name_prompt)`
  - Replace `"New Route Name"` with `stringResource(R.string.cluster_naming__new_route_name_label)`
  - Replace `"Leave Unclustered"` with `stringResource(R.string.cluster_naming__leave_unclustered)`

---

## 3. Verification Plan

### Automated Tests
- Execute `:app:testDebugUnitTest` to verify layout and string resource compilation across all locales.

### Manual Verification Steps (`TST-UI-078`)
1. **Localization Audit**:
   - Set app locale to German (DE).
   - Trigger TCX import to launch `ClusterNamingDialog`.
   - Verify all 10 text elements are rendered in German without raw English text.
