# Stage 3: Architecture & Implementation Plan (`ATT-3920`)

## 1. Ticket Reference & Traceability
* **Jira Ticket**: `ATT-3920` — *[Feature] Workout Details: Individually configurable tabs*
* **Stage 3 Sub-Task**: `ATT-3969` — `[Impl-Plan] Workout Details: Individually configurable tabs`
* **Requirement ID**: `REQ-UI-408`
* **Test ID**: `TST-UI-371`

---

## 2. SWE.2 Architectural Decomposition

### 2.1 Component Responsibilities & Layer Boundaries
1. **Domain & Configuration Model (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabConfig.kt`)**:
   * `WorkoutDetailSectionType`: Enum of the `10` assignable Workout Detail sections (`DETAILS`, `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`), each carrying its `labelRes: Int`.
   * *Note*: `WorkoutSectionType` (`9` values in `WorkoutSectionOrder.kt`) remains untouched for `WorkoutSummary.kt` list cards (`WorkoutSectionOrderPersistenceTest`, `WorkoutSummaryDynamicOrderContractTest`, `WorkoutSectionReorderContractTest`).
   * `WorkoutDetailTabPreset`: Enum (`OVERVIEW`, `MAP_AND_GRAPHS`, `ZONES`, `LAPS_AND_SEGMENTS`, `CUSTOM`) with `defaultTitleRes: Int`.
   * `WorkoutDetailTabLayoutMode`: Enum (`MODE_A_CARD_SCROLL`, `MODE_B_SPLIT_MAP`, `MODE_C_FULL_MAP`).
   * `WorkoutDetailSectionAvailability`: Data class encapsulating per-workout section data presence (`hasDetails`, `hasDescription`, `hasExtrema`, `hasMap`, `hasElevation`, `hasCharts`, `hasZones`, `hasLaps`, `hasStrava`, `hasExportStatus`).
   * `WorkoutDetailTabConfig`: Immutable data class (`id: String`, `preset: WorkoutDetailTabPreset`, `customTitle: String = ""`, `sections: List<WorkoutDetailSectionType>`) with:
     - `normalizedSections`: Deduplicates `sections` and pins `WorkoutDetailSectionType.MAP` at index `0` whenever `MAP` is present.
     - `layoutMode`: Resolves `normalizedSections` into `MODE_A_CARD_SCROLL` (`MAP` absent), `MODE_B_SPLIT_MAP` (`MAP` + at least one other section), or `MODE_C_FULL_MAP` (`MAP` only).
     - Pure state mutation helpers (`addCustomTab`, `renameTab`, `moveTab`, `deleteTab`, `toggleSectionInTab`, `moveSectionInTab`, `filterVisibleWorkoutDetailTabs`, `serializeTabs`, `deserializeTabs`).
     - `DEFAULT_TABS`:
       1. `OVERVIEW` (`[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`)
       2. `MAP_AND_GRAPHS` (`[MAP, ELEVATION, CHARTS]`)
       3. `ZONES` (`[ZONES]`)
       4. `LAPS_AND_SEGMENTS` (`[LAPS, STRAVA]`)

2. **Explicit Bidirectional Enum Mapping (`WorkoutDetailSectionType` <-> `WorkoutSectionType`)**:
   * To guarantee zero data loss and full architectural clarity between the 9-section Workout Summary list card (`WorkoutSummary.kt`) and the 10-section Tabbed Workout Detail view (`TrackOnMapScreen.kt`), `WorkoutDetailTabConfig.kt` defines explicit conversion functions:
     - `fun WorkoutDetailSectionType.toWorkoutSectionTypeOrNull(): WorkoutSectionType?`: Maps the 9 shared sections (`DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `EXPORT_STATUS`) 1:1 to `WorkoutSectionType`, and returns `null` for `WorkoutDetailSectionType.DETAILS` (since `WorkoutDetails` is permanently anchored above the dynamic loop in `WorkoutSummary.kt`).
     - `fun WorkoutSectionType.toWorkoutDetailSectionType(): WorkoutDetailSectionType`: Maps all 9 `WorkoutSectionType` values 1:1 to their corresponding `WorkoutDetailSectionType`.
     - `fun deriveSummarySectionOrder(tabs: List<WorkoutDetailTabConfig>, fallbackOrder: List<WorkoutSectionType> = DEFAULT_WORKOUT_SECTIONS_ORDER): List<WorkoutSectionType>`: Flattens all `tabs` via `toWorkoutSectionTypeOrNull()`, deduplicates while preserving order, and appends any remaining `WorkoutSectionType` entries from `fallbackOrder` so all 9 summary sections are always accounted for without data loss.
   * `WorkoutSummary.kt` continues reading `workoutSectionsOrderFlow` (`PREF_KEY_WORKOUT_SECTIONS_ORDER`), while `TrackOnMapScreen.kt` reads `workoutDetailTabsOrderFlow` (`PREF_KEY_WORKOUT_DETAIL_TABS_CONFIG`), ensuring editing detail tabs never corrupts the summary card order.

3. **Hit-Test & Region-Aware Gesture Arbitration (`WorkoutDetailTabConfig.kt` & `WorkoutDetailTabbedContent.kt`)**:
   * Rather than blindly locking out horizontal tab swipes across the entire screen whenever a tab contains a map, gesture arbitration distinguishes between **Map/Chart canvas touch** (`InteractiveCanvasRegion`) and **Lower-Pane Card / Edge touch**:
     - `WorkoutDetailTabConfig.requiresHorizontalGestureIsolation(availability, detailPreferences, isTouchingInteractiveCanvas: Boolean = true, touchXDp: Float? = null, screenWidthDp: Float? = null, edgeSwipeGutterDp: Float = 24f): Boolean`:
       1. If the tab contains no active `MAP`, `ELEVATION`, or `CHARTS` for the current workout (`MODE_A_CARD_SCROLL` with cards only), returns `false` (`HorizontalPager` swipe is always enabled).
       2. If `touchXDp != null && screenWidthDp != null` and the touch originates within the left or right screen-edge gutter (`touchXDp <= edgeSwipeGutterDp || touchXDp >= screenWidthDp - edgeSwipeGutterDp`), returns `false` so edge swipes always allow switching tabs.
       3. On `MODE_B_SPLIT_MAP` tabs, `WorkoutDetailTabbedContent` tracks `var isTouchingMapOrChartCanvas by remember { mutableStateOf(false) }` via `PointerEventPass.Initial` on the Map container and Chart/Elevation canvases vs. lower-pane non-chart cards (`DETAILS`, `DESCRIPTION`, `EXTREMA`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`):
          - Touching/dragging inside the **Map viewport** or **Elevation/Telemetry chart canvases** sets `isTouchingInteractiveCanvas = true` -> `userScrollEnabled = false` on `HorizontalPager`, so 2D map panning and horizontal chart scrubbing never trigger accidental tab switches.
          - Touching/swiping horizontally on **non-chart cards in the lower scrollable pane** (`DETAILS`, `EXTREMA`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`) sets `isTouchingInteractiveCanvas = false` -> `userScrollEnabled = true` on `HorizontalPager`, allowing natural horizontal swipe-to-switch-tab in the lower content pane!
       4. On `MODE_C_FULL_MAP` tabs (where 100% of the viewport is the map), `isTouchingInteractiveCanvas` defaults to `true` inside the central map area (with `24.dp` edge-gutter and `PrimaryScrollableTabRow` header clicks available to switch tabs).

4. **Persistence Robustness, Versioned Schema & Atomic Recovery (`MyPreferenceManager.kt` & `WorkoutDetailTabConfig.kt`)**:
   * **Versioned Wire Format**: `serializeTabs(tabs)` prefixes the serialized payload with a schema version header (`"v1|<tab1>;<tab2>;..."`), URL-encoding custom tab titles so arbitrary Unicode characters, emojis, or delimiters (`|`, `;`, `,`, `:`) never corrupt token splitting.
   * **Crash/Corruption Recovery**: `deserializeTabs(raw: String?)` is wrapped in `runCatching { ... }.getOrElse { DEFAULT_TABS }`. It accepts both `"v1|..."` and unprefixed legacy strings, strips unknown/corrupted section names, deduplicates sections per tab, pins `MAP` to index `0`, drops tabs that parse with zero valid sections, and falls back to `DEFAULT_TABS` if the resulting tab list is empty, null, or truncated by a partial write.
   * **Thread-Safe StateFlow Synchronization**: `MyPreferenceManager.kt` persists `PREF_KEY_WORKOUT_DETAIL_TABS_CONFIG = "pref_key_workout_detail_tabs_config"` atomically and updates `_workoutDetailTabsOrderFlow: MutableStateFlow<List<WorkoutDetailTabConfig>>` both immediately in `setWorkoutDetailTabs` / `resetWorkoutDetailTabs` and via `SharedPreferences.OnSharedPreferenceChangeListener`.

5. **Cross-Package Dependency Graph (`ui/settings/tuning` -> `ui/aftermath`)**:
   * The project is a single Gradle module (`:app`) with a strictly acyclic package dependency direction: `com.atrainingtracker.trainingtracker.ui.settings.tuning` -> `com.atrainingtracker.trainingtracker.ui.aftermath`.
   * Specifically, `WorkoutMasksAndCardsSection.kt` (in `ui/settings/tuning/categories/`) **already** imports `WorkoutSectionType` and `DEFAULT_WORKOUT_SECTIONS_ORDER` from `ui/aftermath`, while `ui/aftermath` has **zero** imports from `ui/settings/tuning`.
   * `WorkoutMasksAndCardsSection.kt` invokes `WorkoutDetailTabsConfigSheet` (or delegates via `onOpenDetailTabsConfig: () -> Unit`), preserving a 100% acyclic dependency graph and keeping every `.kt` file in `ui/settings/tuning/` strictly `< 400` lines (`AdvancedTuningModularityTest`).

6. **Multi-Tab Workout Details Renderer (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`)**:
   * Renders the sticky top `WorkoutHeader` (including the compact `"Configure Tabs"` `IconButton` alongside existing `"Edit"` and `"Recalculate"` actions, Rule 23 Section 5.6).
   * Filters visible tabs via `filterVisibleWorkoutDetailTabs(tabs, availability, detailPreferences)`.
   * Renders a Material 3 `PrimaryScrollableTabRow` / `ScrollableTabRow` (when `visibleTabs.size > 1`) and a `HorizontalPager(state = pagerState, userScrollEnabled = !activeTabRequiresGestureIsolation)`.
   * Inside each page:
     - **`MODE_A_CARD_SCROLL`**: Optional sticky `GlobalTelemetryZoomToolbar` (if `ELEVATION` or `CHARTS` are in the tab) + a single unified `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding())` rendering each assigned section in `tab.normalizedSections` order.
     - **`MODE_B_SPLIT_MAP`**: `MapDetailLayout` with `MAP` anchored at top (`metadataContent = null`), `SplitPaneDivider`, sticky zoom toolbar (if `ELEVATION` or `CHARTS` are present), and a single lower scrollable pane rendering all non-map sections in `tab.normalizedSections` order, with synchronized distance scrubbing (`selectedDistance`) and lap highlighting (`selectedLapNr`), plus hit-test tracking between Map/Chart canvases and non-chart card rows.
     - **`MODE_C_FULL_MAP`**: Full-height `MapDetailLayout` (`showMap = true`, `showElevationProfile = false`, `showTelemetryCharts = false`, `metadataContent = null`, `analyticsContent = null`).

7. **Interactive Tab Configuration Sheet (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabsConfigSheet.kt`)**:
   * Hosted inside `AppModalBottomSheet(onDismissRequest = onDismiss, scrollable = true)` (Rule 23 Section 5.8: no nested `verticalScroll` inside `AppModalBottomSheet`).
   * Displays:
     - Tab selector pills + Up/Down tab reorder buttons + Delete Tab button (disabled when `tabs.size <= 1`) + `+ Add Tab` button.
     - Tab name `OutlinedTextField` for renaming the selected tab.
     - Assigned & Available `WorkoutDetailSectionType` checklist (`10` sections) with Checkbox toggles and Up/Down reorder buttons for active sections (`MAP` pinned at index `0` with explanatory hint when enabled).
     - `Reset to Default Tabs` button (`OutlinedButton`).

---

## 3. UI Consistency (Rule 23 & `docs/design_guidelines.md`)
* **Closest Existing References**:
  - Tabbed navigation: `RouteTabbedScreen.kt`, `EquipmentTabsScreen.kt`, `AthleteProfileScreen.kt` (standard Material 3 `TabRow` / `ScrollableTabRow` + `HorizontalPager`).
  - Bottom sheet editor: `WorkoutSectionOrderSheet` in `WorkoutMasksAndCardsSection.kt` (`AppModalBottomSheet` with `RoundedCornerShape(12.dp)` section rows, `IconButton` Up/Down controls, and `OutlinedButton` reset action).
  - Top header action: `WorkoutHeader.kt` compact `IconButton` (`Icons.Default.Tune`, `20.dp` icon inside `36.dp` button, Rule 23 Section 5.6).
* **Theme Tokens**: Uses `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `RoundedCornerShape(12.dp)` / `RoundedCornerShape(8.dp)`, and `4.dp / 8.dp / 12.dp / 16.dp` spacing scale. Zero hardcoded hex colors.
* **Bottom Sheet Scroll Rule (Section 5.8)**: `WorkoutDetailTabsConfigSheet` uses `AppModalBottomSheet(..., scrollable = true)` with a standard non-scrolling `Column` inside to avoid nested `verticalScroll` crashes.

---

## 4. Atomic Implementation Steps

1. **Step 1 — Localization Strings (All 9 Locales)**:
   * Add `workout_detail_tab_*`, `workout_detail_section_details`, and `workout_detail_tabs_*` string resources across all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
2. **Step 2 — Domain Model, Bidirectional Enum Mapping, Hit-Test Gesture Arbitration & Versioned Serialization (`WorkoutDetailTabConfig.kt` + `MyPreferenceManager.kt`)**:
   * Create `WorkoutDetailTabConfig.kt` with `WorkoutDetailSectionType`, `toWorkoutSectionTypeOrNull()`, `toWorkoutDetailSectionType()`, `deriveSummarySectionOrder()`, `WorkoutDetailTabPreset`, `WorkoutDetailTabLayoutMode`, `WorkoutDetailSectionAvailability`, `WorkoutDetailTabConfig`, `DEFAULT_TABS`, versioned `v1|...` `serializeTabs`/`deserializeTabs` with corrupt/partial-write fallback, hit-test & edge-gutter `requiresHorizontalGestureIsolation(...)`, and pure CRUD/reordering/filtering helpers.
   * Add `workoutDetailTabsOrderFlow`, `getWorkoutDetailTabs()`, `setWorkoutDetailTabs()`, and `resetWorkoutDetailTabs()` to `MyPreferenceManager.kt`.
3. **Step 3 — Tab Configuration Bottom Sheet (`WorkoutDetailTabsConfigSheet.kt`)**:
   * Implement `WorkoutDetailTabsConfigSheet` using `AppModalBottomSheet` for tab CRUD, renaming, reordering, section toggling/reordering, and resetting to defaults, plus `@Preview` composables.
4. **Step 4 — Multi-Tab Workout Details Content (`WorkoutDetailTabbedContent.kt` & `TrackOnMapScreen.kt` & `WorkoutHeader.kt` & `WorkoutMasksAndCardsSection.kt`)**:
   * Create `WorkoutDetailTabbedContent.kt` implementing Mode A (`MODE_A_CARD_SCROLL`), Mode B (`MODE_B_SPLIT_MAP`), and Mode C (`MODE_C_FULL_MAP`), region-aware hit-test gesture isolation (`isTouchingInteractiveCanvas` + edge gutter), and empty-tab auto-hiding.
   * Wire `WorkoutHeader.kt`, `TrackOnMapScreen.kt`, and `WorkoutMasksAndCardsSection.kt` (verifying `< 400` lines across `ui/settings/tuning/*.kt`).
5. **Step 5 — Unit, Contract, Persistence Corruption & 9-Locale Verification Suite (`WorkoutDetailTabsTest.kt`)**:
   * Author `WorkoutDetailTabsTest.kt` covering `TST-UI-371.1` through `TST-UI-371.8` (including bidirectional enum mapping, partial-write/corrupt payload recovery, and hit-test/edge-gutter gesture arbitration) and run targeted tests.

---

## 5. Non-Negotiable Invariants & Verification Commands
* `WorkoutSectionType.entries.size == 9` MUST remain unchanged for `WorkoutSummary.kt`, with lossless bidirectional mapping to `WorkoutDetailSectionType` (`10` values).
* All 7 existing `TrackOnMapScreen*ContractTest` suites MUST pass without regression.
* Every `.kt` file in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` MUST have `< 400` lines, and `ui/aftermath` MUST NOT import `ui/settings/tuning`.
* Targeted Stage 4 Test Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionOrderPersistenceTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionReorderContractTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen*" --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapScreen*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"
  ```
