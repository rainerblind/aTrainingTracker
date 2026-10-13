# Stage 5 Walkthrough: ATT-2931 - Add layers control menu to toggle routes, segments, markers, and track on general map

**Ticket**: [ATT-2931](https://atrainingtracker.atlassian.net/browse/ATT-2931)  
**Sub-task**: [ATT-2965](https://atrainingtracker.atlassian.net/browse/ATT-2965) (`[Test]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-2931`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Outcome

During Sprint 2026-41.5 testing on Google Pixel 10, the general map (`MapScreenWithTrack`) unconditionally rendered all spatial entities (active routes, Strava segments, favorite known locations, and live recorded tracks with start markers). As users accumulated training data, routes, segments, and favorite locations crowded the central viewport, obscuring critical navigation cues and confusing athletes.

Ticket `ATT-2931` established formal requirement `REQ-UI-322` (amending `REQ-UI-180` and `REQ-UI-308`), implemented an ergonomic floating Layers control button and Material 3 dropdown menu with custom color-coded legend swatches, connected layer state to `SharedPreferences` persistence in `MapFragmentWithTrackViewModel`, conditioned `ATrainingTrackerMap` layer composition, and achieved 100% localization parity across all 9 supported languages.

### Key Enhancements
1. **Explicit General Map Layer Domain Model (`GeneralMapLayer.kt`)**:
   * Declared `enum class GeneralMapLayer` with exactly four types: `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`.
2. **Reactive State Flow & Preference Persistence (`MapFragmentWithTrackViewModel.kt`)**:
   * Exposes `val enabledLayers: StateFlow<Set<GeneralMapLayer>>`.
   * Restores enabled layers from `SharedPreferences` (`PREF_GENERAL_MAP_ENABLED_LAYERS`).
   * Defaults to all layers enabled (`GeneralMapLayer.entries.toSet()`) on fresh installations.
   * `toggleLayer(layer)` updates in-memory `StateFlow` and commits serialized string set asynchronously to `SharedPreferences`.
   * Constructor injection `prefs: SharedPreferences? = null` allows pure unit testing without Android system dependencies.
3. **Ergonomic Floating Layers Control & Dropdown Menu (`MapScreenWithTrack.kt`)**:
   * Floating `Surface` circular button (`44.dp`, `CircleShape`, `tonalElevation = 2.dp`, `shadowElevation = 6.dp`) positioned at `Alignment.TopEnd` with `statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
   * `Icon(Icons.Default.Layers)` dynamically tinted with `MaterialTheme.colorScheme.primary` when any layer is disabled, or `MaterialTheme.colorScheme.onSurface` when all layers are enabled.
   * Material 3 `DropdownMenu` rendering four `DropdownMenuItem` entries with a `Checkbox`, colored legend swatch (`12.dp`, `RoundedCornerShape(2.dp)`), and localized title:
     * `ROUTES`: `TTColor.RouteSelected`
     * `SEGMENTS`: `TTColor.StravaOrange`
     * `KNOWN_LOCATIONS`: `MaterialTheme.colorScheme.tertiary`
     * `TRACK`: `TrackType.BEST.color`
4. **Conditional Map Canvas Layer Composition**:
   * `ATrainingTrackerMap` layer composition in `MapScreenWithTrack.kt` dynamically gates elements:
     * `GeneralMapLayer.KNOWN_LOCATIONS` gates `knownLocations(displayLocations, ...)`
     * `GeneralMapLayer.SEGMENTS` gates `segments(uiState.segments, ...)`
     * `GeneralMapLayer.ROUTES` gates `routes(uiState.routes, ...)`
     * `GeneralMapLayer.TRACK` gates `markers(uiState.markers)` and `liveTrack(uiState.currentTrack)`
5. **100% 9-Language Localization Parity**:
   * Externalized `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, and `map_layer_track` across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.
6. **Preservation of System Invariants**:
   * Bottom sheet detail inspection for clicked routes, segments, and favorite locations remains intact.
   * Live GPS recording and background telemetry processing are completely untouched.
   * Start pin logic and 100% test pass rate across the full test suite strictly preserved.

---

## 2. Requirement & Test Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-UI-322` | `TST-UI-282.1` | Contract | `GeneralMapLayersContractTest.testGeneralMapLayer_enumEntries` & `testDefaultEnabledLayers_containsAllLayersWhenPrefsNull` | **PASSED** | `Verified` |
| `REQ-UI-322` | `TST-UI-282.2` | Contract | `GeneralMapLayersContractTest.testToggleLayer_persistsAndUpdatesFlow`, `testLoadEnabledLayers_fromExistingPreferences`, `testLoadEnabledLayers_ignoresUnknownEntries` | **PASSED** | `Verified` |
| `REQ-UI-322` | `TST-UI-282.3` | Contract | `GeneralMapLayersContractTest.testMapScreenWithTrack_structuralVerification` | **PASSED** | `Verified` |
| `REQ-UI-322` | `TST-UI-282.4` | Localization | `GeneralMapLayersLocalizationTest.testGeneralMapLayersStringsParityAcrossAll9Locales` (All 9 Locales) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-282.5` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Test Execution Results

### Targeted Unit Tests
```text
GeneralMapLayersContractTest > testLoadEnabledLayers_fromExistingPreferences PASSED
GeneralMapLayersContractTest > testToggleLayer_persistsAndUpdatesFlow PASSED
GeneralMapLayersContractTest > testGeneralMapLayer_enumEntries PASSED
GeneralMapLayersContractTest > testDefaultEnabledLayers_containsAllLayersWhenPrefsNull PASSED
GeneralMapLayersContractTest > testMapScreenWithTrack_structuralVerification PASSED
GeneralMapLayersContractTest > testLoadEnabledLayers_ignoresUnknownEntries PASSED
GeneralMapLayersLocalizationTest > testGeneralMapLayersStringsParityAcrossAll9Locales PASSED
MapFragmentWithTrackViewModelTest > testUiState_combinesKnownLocationsFlow PASSED
MapFragmentWithTrackViewModelTest > testUpdateKnownLocation_delegatesToRepository PASSED

BUILD SUCCESSFUL in 1m 52s
32 actionable tasks: 6 executed, 26 up-to-date
Tests executed: 9, Failures: 0, Errors: 0, Skipped: 0
```

### Clean-Room Full Regression Test Suite
```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 2m 29s
32 actionable tasks: 1 executed, 31 up-to-date
Total test suites: 454
Total unit tests executed: 2,235, Failures: 0, Errors: 0, Skipped: 0
```

---

## 4. Modified Files

* [GeneralMapLayer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayer.kt): Created `GeneralMapLayer` enum declaring `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, and `TRACK`.
* [MapFragmentWithTrackViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt): Added `enabledLayers` StateFlow, `toggleLayer(layer)` with SharedPreferences persistence, and `prefs` constructor parameter.
* [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt): Added floating circular layers action button, Material 3 `DropdownMenu` with checkboxes and swatches, and conditioned canvas layers on `enabledLayers`.
* [strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml): Added English string resources (`map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track`).
* [strings.xml (de)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-de/strings.xml): Added German translations.
* [strings.xml (es)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-es/strings.xml): Added Spanish translations.
* [strings.xml (fr)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-fr/strings.xml): Added French translations.
* [strings.xml (it)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-it/strings.xml): Added Italian translations.
* [strings.xml (ja)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-ja/strings.xml): Added Japanese translations.
* [strings.xml (nl)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-nl/strings.xml): Added Dutch translations.
* [strings.xml (pl)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-pl/strings.xml): Added Polish translations.
* [strings.xml (pt)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-pt/strings.xml): Added Portuguese translations.
* [GeneralMapLayersContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersContractTest.kt): Unit & contract test suite for layer enum, ViewModel state, toggling, SharedPreferences persistence, and UI layout structure.
* [GeneralMapLayersLocalizationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersLocalizationTest.kt): 9-language localization audit test suite.
* [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): Updated `REQ-UI-322` to `Verified`.
* [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): Updated `TST-UI-282` to `Verified`.
* [docs/engineering/analysis/ATT-2931_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-2931_analysis.md): Stage 1 analysis deliverable.
* [docs/engineering/test_specs/ATT-2931_test_spec.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/test_specs/ATT-2931_test_spec.md): Stage 2 test spec deliverable.
* [docs/engineering/plans/ATT-2931_plan.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/plans/ATT-2931_plan.md): Stage 3 implementation plan deliverable.
* [docs/engineering/walkthroughs/ATT-2931_walkthrough.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/walkthroughs/ATT-2931_walkthrough.md): Stage 5 walkthrough deliverable.
