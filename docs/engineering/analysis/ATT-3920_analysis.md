# Stage 1 Analysis: ATT-3920 - Workout Details: Individually configurable tabs

**Ticket**: [ATT-3920](https://rainerblind.atlassian.net/browse/ATT-3920)  
**Sub-task**: [ATT-3967](https://rainerblind.atlassian.net/browse/ATT-3967) (`[Analysis]`)  
**Parent Epic**: [ATT-2177](https://rainerblind.atlassian.net/browse/ATT-2177) (*Aftermath & Post-Workout Analytics*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.8`  
**Branch**: `feature/ATT-3920`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

Currently, the Workout Details screen ([`TrackOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) + [`MapDetailLayout.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)) renders all enabled post-workout sections (`DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`) on a **single monolithic page** divided into two slots (`preMapSections` above the map and `postMapSections` below the map):

1. **Severe Vertical Crowding & Competing Scroll Containers**:
   - `preMapSections` (rendered in `metadataContent` above the map) uses its own bounded `verticalScroll` (`maxMetadataHeightDp`) combined with `CollapsingAppBarNestedScrollConnection`.
   - The Google Map (`ATrainingTrackerMap`) in the center intercepts 2D touch pan/zoom gestures.
   - `postMapSections` below `SplitPaneDivider` uses a second independent `verticalScroll` container alongside interactive horizontal scrubbing/panning charts (`ElevationProfile`, `TelemetryMetricGraph`).
   - Having two vertical scroll containers sandwiching a 2D panning map on a single screen creates gesture friction, visual clutter, and squashed viewports.
2. **Missing Primary Summary Metrics (`WorkoutDetails`) in Detail View**:
   - While workout list cards ([`WorkoutSummary.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)) display [`WorkoutDetails`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt) (Duration, Distance, Speed/Pace, HR, Power, Ascent/Descent, Coggan `NP/IF/TSS`, Aerobic Efficiency `EF/Pw:Hr`, and Anaerobic `W'bal`), `TrackOnMapScreen` only shows `WorkoutHeader` and omits `WorkoutDetails`.
3. **Lack of Purpose-Driven Tabbed Organization**:
   - Athletes inspecting a workout want clean, dedicated tabs (similar to the live tracking cockpit's configurable tabs) so they can view an uncluttered **Overview**, an interactive **Map & Graphs** split-pane, **Zones**, or **Laps & Segments**, and customize their tabs (add, rename, reorder, delete, assign/reorder sections per tab, or reset to defaults).

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **`TrackOnMapScreen.kt` Monolithic Layout**:
   - [`TrackOnMapScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt#L60-L549) directly wraps a single `MapDetailLayout` call, partitioning `activeSectionsOrder` around `WorkoutSectionType.MAP` into `preMapSections` (`metadataContent`) and `postMapSections` (`analyticsContent`).
2. **Data Model & Persistence (`MyPreferenceManager.kt` & `WorkoutSectionType.kt`)**:
   - [`WorkoutSectionType.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutSectionType.kt) defines the 9 reorderable sections (`DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `EXPORT_STATUS`) used by `WorkoutSummary.kt` (where `WorkoutDetails` is permanently anchored above the loop).
   - Existing unit and contract tests (`WorkoutSectionOrderPersistenceTest`, `WorkoutSummaryDynamicOrderContractTest`, `WorkoutSectionReorderContractTest`) enforce the 9-element `WorkoutSectionType.DEFAULT_ORDER` contract for Workout Summary list cards.
   - Therefore, introducing a dedicated `WorkoutDetailSectionType` (covering all 10 detail sections: `DETAILS`, `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`) and `WorkoutDetailTabConfig` model (serialized as JSON in `MyPreferenceManager` under `WORKOUT_DETAIL_TABS_JSON`) cleanly supports full per-tab customization in Workout Details while preserving 100% backward compatibility with the Workout Summary list card preferences and existing contract tests.
3. **Three Conflict-Free Per-Tab Layout Modes**:
   - By eliminating `preMapSections` in tabbed detail mode (i.e. if `MAP` is present in a tab, it is always anchored at the top of that tab), every tab resolves deterministically into one of three conflict-free layout modes:
     - **Mode A — Card / Analytics Tab (No `MAP` in tab)**: Single `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding())` (with a sticky `GlobalTelemetryZoomToolbar` at the top if `ELEVATION` or `CHARTS` are present in the tab).
     - **Mode B — Split Map + Telemetry Tab (`MAP` + other sections)**: `MapDetailLayout` with `header = {}` (since `WorkoutHeader` and `TabRow` are persistent above the pager), `metadataContent = null` (zero upper metadata scroll conflict), `MAP` anchored at top, `SplitPaneDivider`, sticky `GlobalTelemetryZoomToolbar` (if `ELEVATION` or `CHARTS` are in the tab), and a single lower scrollable pane containing the tab's remaining sections in their configured order, with synchronized scrubbing (`selectedDistance`) and lap highlighting (`selectedLapNr`).
     - **Mode C — Full-Screen Map Tab (Only `MAP` in tab)**: `MapDetailLayout` with `showMap = true`, `showElevationProfile = false`, `showTelemetryCharts = false`, `metadataContent = null`, `analyticsContent = null`, rendering `ATrainingTrackerMap` at 100% of the tab viewport height with track layer toggles and share action.
4. **Smart Gesture Isolation**:
   - On Mode A tabs without `MAP`, `ELEVATION`, or `CHARTS`, `HorizontalPager` sets `userScrollEnabled = true` for natural horizontal swipe between tabs.
   - On tabs containing `MAP`, `ELEVATION`, or `CHARTS` (with data), `HorizontalPager` sets `userScrollEnabled = false` so horizontal touch drags exclusively pan the map or scrub/pan telemetry graphs without triggering accidental tab switches.
5. **Auto-Hiding Empty Tabs**:
   - A pure helper function `hasDataForSection(section: WorkoutDetailSectionType, ...)` evaluates whether a section has non-empty content for the current workout (e.g., `MAP` requires `showMap && hasGpsTrack`, `ELEVATION` requires `(hasGpsTrack || hasTracklessAltitude) && hasAltitudeData`, `CHARTS` requires non-empty telemetry data, `ZONES` requires `hrZoneDistribution != null || powerZoneDistribution != null`, `LAPS` requires `splitChartData != null && workoutData.laps.size > 1`, `STRAVA` requires `!workoutData.stravaActivityData.isNullOrBlank()`, `DESCRIPTION` requires non-blank description/goal/method or editable state, `EXTREMA` requires `workoutData.extremaData.dataRows.isNotEmpty()`, `EXPORT_STATUS` requires `workoutData.exportStatuses.isNotEmpty()`, `DETAILS` is always non-empty).
   - Any tab where zero assigned sections have data for the current workout is automatically hidden from the Tab Bar for that workout.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Introduce `WorkoutDetailSectionType` (10 sections including `DETAILS`) and `WorkoutDetailTabConfig` (`id`, `customTitle`, `presetType`, `sections: List<WorkoutDetailSectionType>`) with default 4 tabs:
    1. **Overview** (`Übersicht`): `[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]` (Mode A)
    2. **Map & Graphs** (`Karte & Diagramme`): `[MAP, ELEVATION, CHARTS]` (Mode B)
    3. **Zones** (`Zonen`): `[ZONES]` (Mode A)
    4. **Laps & Segments** (`Runden & Segmente`): `[LAPS, STRAVA]` (Mode A)
  * Persist tab configurations in `MyPreferenceManager` (`workoutDetailTabsFlow` / `setWorkoutDetailTabs` / `resetWorkoutDetailTabs`) with JSON serialization, self-healing deserialization, and `MAP`-pinned-first normalization.
  * Render persistent `WorkoutHeader` (with a `"Configure Tabs"` icon action) + Material 3 `ScrollableTabRow` / `TabRow` + `HorizontalPager` in `TrackOnMapScreen` supporting Mode A, Mode B, and Mode C layouts, smart `userScrollEnabled` gesture isolation, and auto-hiding empty tabs.
  * Provide a full Tab Configuration sheet/dialog (`WorkoutDetailTabsConfigSheet`) allowing users to add tabs, rename tabs, reorder tabs, delete tabs (min 1 tab), toggle & reorder sections within each tab (`MAP` always pinned at top when enabled), and reset to the 4 default tabs.
  * Expose the Tab Configuration UI from two entry points:
    1. Directly from the `"Configure Tabs"` icon action in `WorkoutHeader` on `TrackOnMapScreen`.
    2. From Advanced Settings -> Workout Masks & Cards (`WorkoutMasksAndCardsSection.kt`), keeping all `.kt` files in `ui/settings/tuning/` strictly `< 400` lines.
  * Localize all new strings across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter the Workout Summary list card (`WorkoutSummary.kt`) layout or its 9-section `WorkoutSectionType.DEFAULT_ORDER` contract.
  * Do not modify `RouteOnMapScreen`, `SegmentOnMapScreen`, `ClimbOnMapScreen`, or `PeriodMapScreen` layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-408` (building upon `REQ-UI-235`, `REQ-UI-240`, `REQ-UI-245`, `REQ-UI-248`, `REQ-UI-250`, `REQ-UI-252`, `REQ-UI-255`, `REQ-UI-404`, `REQ-UI-407`). No existing requirements in `docs/requirements.md` are destructively modified.
* **Historical Origin & Commit Trace**:
  * `REQ-UI-240` (`ATT-2030`), `REQ-UI-245` (`ATT-2112`), `REQ-UI-248` (`ATT-2137`), `REQ-UI-252` (`ATT-2170`), and `REQ-UI-255` (`ATT-2176`) established section visibility and ordering in `WorkoutSummary` and `TrackOnMapScreen`, enforced by structural contract tests (`TrackOnMapScreenDetailPreferencesContractTest`, `TrackOnMapScreenSectionSlottingContractTest`, `TrackOnMapScreenMetadataRoutingContractTest`, `TrackOnMapScreenLapRoutingContractTest`, `TracklessAftermathVisualContractTest`, `WorkoutSectionReorderContractTest`).
* **Root Reason for Existing Formulation**:
  * Before tabbed workout details existed, `TrackOnMapScreen` needed a single-screen partitioning strategy around `MAP` (`preMapSections` vs `postMapSections`) and boolean visibility gates (`WorkoutDetailPreferences`).
* **Preservation of Core Invariants**:
  * `TrackOnMapScreen.kt` retains its existing function signature and legacy single-page `MapDetailLayout` fallback branch (when `useTabbedLayout = false` or custom `metadataContent`/`analyticsContent` slots are supplied), ensuring 100% backward compatibility with all existing structural contract tests while routing standard workout detail views through the new conflict-free tabbed layout.

---

## 5. Architectural Strategy & High-Level Solution

1. **Model & Serialization (`ui/aftermath/WorkoutDetailTabConfig.kt`)**:
   - `enum class WorkoutDetailSectionType(@StringRes val titleRes: Int)`: `DETAILS`, `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`.
   - `enum class WorkoutDetailTabPreset(@StringRes val defaultTitleRes: Int)`: `OVERVIEW`, `MAP_AND_GRAPHS`, `ZONES`, `LAPS_AND_SEGMENTS`, `CUSTOM`.
   - `enum class WorkoutDetailTabLayoutMode`: `MODE_A_CARD_SCROLL`, `MODE_B_SPLIT_MAP`, `MODE_C_FULL_MAP`.
   - `data class WorkoutDetailTabConfig(val id: String, val preset: WorkoutDetailTabPreset, val customTitle: String = "", val sections: List<WorkoutDetailSectionType>)` with:
     - `normalizedSections`: ensures distinct sections and pins `WorkoutDetailSectionType.MAP` at index `0` whenever `MAP` is present in `sections`.
     - `layoutMode`: returns `MODE_C_FULL_MAP` when `normalizedSections == listOf(MAP)`, `MODE_B_SPLIT_MAP` when `MAP in normalizedSections` and `normalizedSections.size > 1`, and `MODE_A_CARD_SCROLL` when `MAP !in normalizedSections`.
     - `requiresGestureIsolation(hasMapData, hasElevationData, hasChartsData)`: returns `true` when the tab actively renders `MAP`, `ELEVATION`, or `CHARTS` so `HorizontalPager(userScrollEnabled = false)` prevents horizontal swipe conflicts.
     - JSON serialization/deserialization (`toJsonList` / `fromJsonList`) with fallback to `DEFAULT_TABS`.
2. **Tabbed Renderer (`ui/aftermath/WorkoutDetailTabbedContent.kt`)**:
   - Renders persistent `WorkoutHeader` (with `"Configure Tabs"` icon button alongside `headerActions` and `onEditWorkout`), followed by Material 3 `ScrollableTabRow` / `TabRow`, followed by `HorizontalPager` hosting each visible tab in Mode A, Mode B, or Mode C.
3. **Interactive Tab Configuration Sheet (`ui/aftermath/WorkoutDetailTabsConfigSheet.kt`)**:
   - Modal sheet / dialog to add a tab, rename/reorder/delete tabs, toggle and reorder sections per tab (keeping `MAP` pinned at top when enabled), and reset to the 4 default tabs.
   - Accessible from both `TrackOnMapScreen` and `WorkoutMasksAndCardsSection`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`WorkoutSectionOrderPersistenceTest`, `WorkoutSectionReorderContractTest`, `TrackOnMapScreen*ContractTest`, `AdvancedTuningModularityTest`).
  2. All `.kt` files under `ui/settings/tuning/` remain strictly `< 400` lines.
  3. 9-language localization parity across all supported locales.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM**
  - Introduces multi-tab pager orchestration and interactive tab configuration UI across Workout Details and Advanced Settings, mitigated by pure unit-testable state/layout/gesture-isolation models and strict preservation of existing `MapDetailLayout` and `TrackOnMapScreen` contracts.
