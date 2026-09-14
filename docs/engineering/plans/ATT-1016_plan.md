# Stage 3 Implementation Plan: Display Settings Parity & Dynamic Application (ATT-1016)

* **Ticket**: [ATT-1016](https://rainerblind.atlassian.net/browse/ATT-1016) (*Check display settings*)
* **Sub-task**: [ATT-1020](https://rainerblind.atlassian.net/browse/ATT-1020) (*[Impl-Plan] Check display settings*)
* **Branch**: `bugfix/ATT-1016`
* **Requirement Traced**: `REQ-SET-052`
* **Test Specification**: `TST-NAV-004`

---

## 1. Proposed Architectural Changes

### 1.1 `TrainingApplication.java`
* **Visibility & Encapsulation**:
  * Make `DEFAULT_DISPLAY_OPTIONS` public (or expose via public getter `getDefaultDisplayOptions()`) as an unmodifiable Set containing `"forcePortrait"`, `"keepScreenOn"`, and `"noUnlocking"`.
  * Add convenience accessors:
    * `public static Set<String> getDisplayOptions()`: returns `cSharedPreferences.getStringSet(SP_DISPLAY_OPTIONS, DEFAULT_DISPLAY_OPTIONS)`.
    * `public static void setDisplayOptions(Set<String> options)`: persists defensive copy `new HashSet<>(options)` to `cSharedPreferences`.

### 1.2 `DisplaySettingsDialog.kt`
* **State Hoisting**:
  * Hoist `currentOptions` to the root `DisplaySettingsDialog` composable.
  * Initialize `currentOptions` using `TrainingApplication.getDisplayOptions()`.
  * Convert `DisplayOptionToggle` into a stateless composable:
    * Parameters: `label: String`, `isChecked: Boolean`, `onCheckedChange: (Boolean) -> Unit`.
  * In `onCheckedChange`:
    * Atomically construct `newSet = currentOptions.toMutableSet()`.
    * Add/remove `prefValue`.
    * Persist `newSet` via `TrainingApplication.setDisplayOptions(newSet)`.
    * Update hoisted `currentOptions = newSet`.
    * Invoke optional `onSettingsChanged?.invoke()`.

### 1.3 `DisplaySettingsDialogFragment.kt`
* **Lifecycle & Callback Hooks**:
  * Pass `onSettingsChanged = { (activity as? MainActivityWithNavigation)?.applyDisplaySettings() }` to `DisplaySettingsDialog`.
  * Override `onDismiss(dialog: DialogInterface)` to invoke `(activity as? MainActivityWithNavigation)?.applyDisplaySettings()`, ensuring dynamic update on dialog dismissal via back gesture, tap outside, or Done button.

### 1.4 `MainActivityWithNavigation.kt`
* **Centralized Display Application (`applyDisplaySettings`)**:
  * Encapsulate display behavior in `fun applyDisplaySettings()`:
    ```kotlin
    fun applyDisplaySettings() {
        window.decorView.keepScreenOn = TrainingApplication.keepScreenOn()

        if (TrainingApplication.NoUnlocking()) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        if (TrainingApplication.forcePortrait()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    ```
  * Call `applyDisplaySettings()` in `onResume()`.

### 1.5 Unit Tests (`DisplaySettingsTest.kt`)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsTest.kt`:
  * Verify `DEFAULT_DISPLAY_OPTIONS` contains `"forcePortrait"`, `"keepScreenOn"`, `"noUnlocking"`.
  * Verify `TrainingApplication.getDisplayOptions()` returns default set when SharedPreferences is empty.
  * Verify `forcePortrait()`, `keepScreenOn()`, and `NoUnlocking()` evaluate to `true` by default.
  * Verify mutating options via `setDisplayOptions` updates all three getters reactively.
  * Verify atomic option removal preserves sibling options.

---

## 2. Invariants & Guardrails ("What MUST NOT Change")

1. **Preference Key & Values**: `pref_display_options` key and option strings (`"forcePortrait"`, `"keepScreenOn"`, `"noUnlocking"`) must remain identical.
2. **Backward Compatibility**: Existing customized sets stored in user SharedPreferences must continue to be respected.
3. **Activity Contracts**: Navigation drawer routing, fragment state handling, and BANALService lifecycle in `MainActivityWithNavigation` must remain completely untouched.

---

## 3. Impact Analysis & Mitigation

* **Android System (Orientation / Window Flags)**:
  * When `forcePortrait` is disabled, releasing to `SCREEN_ORIENTATION_UNSPECIFIED` allows normal sensor-based rotation without recreating or destroying the Activity state unnecessarily.
  * Explicitly calling `clearFlags(FLAG_SHOW_WHEN_LOCKED)` ensures proper security behavior when "keep screen unlocked" is turned off.
* **Concurrency / SharedPreferences Reference Leaks**:
  * `SharedPreferencesImpl.putStringSet()` requires defensive copies (`HashSet<>(options)`) so reference equality checks do not skip disk writes.
  * Hoisting state in Compose guarantees atomic set transitions across consecutive switch toggles.

---

## 4. Verification Plan (TST-NAV-004)

1. **Automated Unit Tests**:
   * Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsTest`.
   * Run full clean regression: `./gradlew testDebugUnitTest`.
2. **Manual / Functional Verification**:
   * Verify all switches render in ON state upon initial inspection.
   * Verify toggling switches persists accurately and updates activity flags dynamically.
