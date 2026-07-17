# Implementation Plan - ATT-250: Resolve String Resource Warnings

Resolve build warnings and ensure string resource integrity by removing translated keys that are missing from the default locale and are unused in the project.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-113` (String Resource Integrity)
- **Test ID**: `TST-STR-013` (String Resource Integrity Check)

## 2. Impact Analysis
- **Core Component**: `strings.xml` in all locales.
- **Side Effects**: None. The strings identified are unused in the codebase (Java, Kotlin, XML layouts, menus, and preferences).
- **Risk**: Low. Removing unused resources improves project health.

## 3. Proposed Changes

### 3.1 Resource Removal
The following unused string keys will be removed from all `strings.xml` files where they exist:
- `Edit_Location`
- `Edit_Location_Message`
- `EndLocation`
- `NameScheme`
- `StartLocation`
- `addCounter`
- `addVia`
- `calc_workout_name`
- `configureWorkoutNameSchemes`
- `create_new_my_location`
- `delete_location`
- `editWorkoutNameCounters`
- `edit_counter`
- `edit_counter_format`
- `edit_location`
- `loop_around_format`
- `really_delete_location`
- `via_format`
- `workout_name_scheme`
- `workout_name_schemes`
- `device_type_short_sensor_manager`

## 4. Verification Plan
- **Static Analysis**: Re-run the `comm` audit to verify that no translated keys remain missing from the default `strings.xml`.
- **Build Verification**: Ensure the project builds without "is translated here but not found in default locale" warnings for these specific keys.
