# Stage 2: Requirement & Test Specification (`ATT-3920`)

## 1. Ticket Reference &Traceability Overview
* **Jira Ticket**: `ATT-3920` — *[Feature] Workout Details: Individually configurable tabs*
* **Stage 2 Sub-Task**: `ATT-3968` — `[Req & Test Spec] [Feature] Workout Details: Individually configurable tabs`
* **Requirement ID**: `REQ-UI-408` (Net-new requirement in `docs/requirements.md`)
* **Test ID**: `TST-UI-371` (Net-new test specification in `docs/tests.md`)
* **Related Requirements**: `REQ-UI-240`, `REQ-UI-262`, `REQ-UI-285`, `REQ-UI-337`, `REQ-UI-338`, `REQ-UI-346`, `REQ-UI-349`, `REQ-UI-350`, `REQ-UI-355`, `REQ-UI-359`, `REQ-UI-403`, `REQ-UI-404`, `REQ-UI-407`

---

## 2. Formal Requirement Specification (`REQ-UI-408`)

### `REQ-UI-408`: Individually Configurable Multi-Tab Workout Details Layout, Conflict-Free Per-Tab Modes, Gesture Isolation, Auto-Hiding Empty Tabs, and Dual Configuration Entry Points

The system SHALL organize the Workout Details view (`TrackOnMapScreen.kt`) into individually configurable tabs (`WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabsConfigSheet.kt`) backed by `MyPreferenceManager` while preserving `WorkoutSectionType` (`9` values) for the Workout Summary list card (`WorkoutSummary.kt`) and retaining full backward compatibility with legacy single-page `MapDetailLayout` callers (`ATT-3920`, complementing `REQ-UI-240`, `REQ-UI-262`, `REQ-UI-285`, `REQ-UI-337`, `REQ-UI-338`, `REQ-UI-355`, `REQ-UI-359`, `REQ-UI-403`, `REQ-UI-404`, and `REQ-UI-407`):

1. **Domain Model & Default 4-Tab Archetype (`REQ-UI-408.1`)**:
   * `WorkoutDetailSectionType` (`WorkoutDetailTabConfig.kt`) SHALL enumerate all `10` assignable Workout Details sections:
     - `DETAILS` (*Workout Metrics / Details*, `R.string.workout_detail_section_details`)
     - `DESCRIPTION` (*Description / Goal / Equipment*, `R.string.workout_section_description`)
     - `EXTREMA` (*Min / Avg / Max Table*, `R.string.workout_section_extrema`)
     - `MAP` (*Interactive Route Map*, `R.string.workout_section_map`)
     - `ELEVATION` (*Elevation Profile*, `R.string.workout_section_elevation`)
     - `CHARTS` (*Telemetry Graphs*, `R.string.workout_section_charts`)
     - `ZONES` (*HR & Power Zone Distributions*, `R.string.workout_section_zones`)
     - `LAPS` (*Laps Table*, `R.string.workout_section_laps`)
     - `STRAVA` (*Strava Segments*, `R.string.workout_section_strava`)
     - `EXPORT_STATUS` (*Export Status*, `R.string.workout_section_export_status`)
   * `WorkoutSectionType` (`9` values in `WorkoutSectionOrder.kt`) SHALL remain unmodified for `WorkoutSummary.kt` where `WorkoutDetails` is permanently anchored at the top of the summary card (`WorkoutSectionOrderPersistenceTest`, `WorkoutSummaryDynamicOrderContractTest`, `WorkoutSectionReorderContractTest`).
   * `WorkoutDetailTabPreset` SHALL define `OVERVIEW`, `MAP_AND_GRAPHS`, `ZONES`, `LAPS_AND_SEGMENTS`, and `CUSTOM`.
   * `WorkoutDetailTabConfig.DEFAULT_TABS` SHALL define 4 default tabs:
     1. **Overview** (`OVERVIEW`, `R.string.workout_detail_tab_overview`): `[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]` -> `MODE_A_CARD_SCROLL`
     2. **Map & Graphs** (`MAP_AND_GRAPHS`, `R.string.workout_detail_tab_map_graphs`): `[MAP, ELEVATION, CHARTS]` -> `MODE_B_SPLIT_MAP`
     3. **Zones** (`ZONES`, `R.string.workout_detail_tab_zones`): `[ZONES]` -> `MODE_A_CARD_SCROLL`
     4. **Laps & Segments** (`LAPS_AND_SEGMENTS`, `R.string.workout_detail_tab_laps_segments`): `[LAPS, STRAVA]` -> `MODE_A_CARD_SCROLL`

2. **Persistence & Defensive Serialization (`REQ-UI-408.2`)**:
   * `WorkoutDetailTabConfig` SHALL provide deterministic string serialization (`serializeTabs(tabs: List<WorkoutDetailTabConfig>): String` and `deserializeTabs(raw: String?): List<WorkoutDetailTabConfig>`) that safely escapes delimiter characters (`|`, `;`, `,`), ignores unknown/deprecated tokens, deduplicates sections within each tab, pins `MAP` at index `0` whenever `MAP` is present in a tab (`normalizedSections`), and falls back to `DEFAULT_TABS` when `raw` is `null`, blank, or results in an empty tab list.
   * `MyPreferenceManager.kt` SHALL expose `workoutDetailTabsOrderFlow: StateFlow<List<WorkoutDetailTabConfig>>`, `getWorkoutDetailTabs(): List<WorkoutDetailTabConfig>`, `setWorkoutDetailTabs(tabs: List<WorkoutDetailTabConfig>)`, and `resetWorkoutDetailTabs()` persisted under SharedPreferences key `"pref_key_workout_detail_tabs_config"`.

3. **Three Conflict-Free Per-Tab Layout Modes & Map Pinning Invariant (`REQ-UI-408.3`)**:
   * `WorkoutDetailTabConfig.layoutMode` SHALL deterministically resolve each tab's `normalizedSections` into one of three layout modes (`WorkoutDetailTabLayoutMode`):
     - **`MODE_A_CARD_SCROLL` (`MAP` not in tab)**: Renders a single unified `Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding())` (Rule 41). If `ELEVATION` or `CHARTS` are present in the tab, a sticky `GlobalTelemetryZoomToolbar` is rendered above the scrollable column (`ChartGestureDisambiguator` handles horizontal chart panning vs. vertical column scrolling). No `SplitPaneDivider` or dual scroll regions exist.
     - **`MODE_B_SPLIT_MAP` (`MAP` + at least one other section in tab)**: `MAP` is strictly pinned at index `0` (`normalizedSections`), guaranteeing `preMapSections` is always empty (`emptyList()`). Renders `MapDetailLayout` with the interactive map anchored at the top, `SplitPaneDivider`, sticky `GlobalTelemetryZoomToolbar` (when `ELEVATION` or `CHARTS` are present), and a single lower scrollable pane rendering all remaining sections in their configured order, with synchronized distance scrubbing (`selectedDistance`) and lap highlighting (`selectedLapNr`).
     - **`MODE_C_FULL_MAP` (`MAP` is the only active section in tab)**: Renders `MapDetailLayout` with `showMap = true`, `showElevationProfile = false`, `showTelemetryCharts = false`, `metadataContent = null`, and `analyticsContent = null`, giving the map 100% of the tab height without any `SplitPaneDivider`.

4. **Smart Gesture Isolation & Auto-Hiding Empty Tabs (`REQ-UI-408.4`)**:
   * `WorkoutDetailTabConfig.requiresHorizontalGestureIsolation(hasMapData, hasElevationData, hasChartsData, detailPreferences)` SHALL return `true` when the tab contains an active `MAP` (with GPS track data and `detailPreferences.showMap`), `ELEVATION` (with altitude profile data and `detailPreferences.showElevation`), or `CHARTS` (with telemetry series data and `detailPreferences.showCharts`), and `false` otherwise.
   * `WorkoutDetailTabbedContent` SHALL bind `HorizontalPager(state = pagerState, userScrollEnabled = !activeTabRequiresGestureIsolation)` so athletes can pan maps and scrub charts without accidental tab switches on map/chart tabs, while enjoying natural horizontal swipe navigation across card-only (`MODE_A_CARD_SCROLL`) tabs; tapping any `PrimaryScrollableTabRow` / `TabRow` header switches tabs unconditionally.
   * `filterVisibleWorkoutDetailTabs(tabs, availability, detailPreferences)` SHALL hide any tab where zero assigned sections have available data for the current workout (falling back to keeping the first tab if all tabs would otherwise be empty).

5. **Interactive Tab Configuration Sheet & Dual Entry Points (`REQ-UI-408.5`)**:
   * `WorkoutDetailTabsConfigSheet.kt` (hosted inside `AppModalBottomSheet`) SHALL support:
     - **Add Tab** (`+ Add Tab`), **Rename Tab**, **Reorder Tabs** (Up/Down), and **Delete Tab** (enforcing at least 1 remaining tab).
     - **Toggle & Reorder Sections per Tab** across all 10 `WorkoutDetailSectionType` items, pinning `MAP` at the top (`index 0`) whenever `MAP` is enabled in a tab.
     - **Reset to Default Tabs** restoring `WorkoutDetailTabConfig.DEFAULT_TABS`.
   * **Entry Point 1 (Workout Details Header)**: `WorkoutHeader` in `TrackOnMapScreen.kt` / `WorkoutDetailTabbedContent.kt` SHALL display a compact `IconButton` (`Icons.Default.Tune` / `Icons.Default.Tab`, Rule 23 Section 5.6) that opens `WorkoutDetailTabsConfigSheet`.
   * **Entry Point 2 (Advanced Settings -> Workout Cards & Details)**: `WorkoutMasksAndCardsSection.kt` SHALL provide a dedicated action row/button to open `WorkoutDetailTabsConfigSheet` while keeping every `.kt` file in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` strictly `< 400` lines (`AdvancedTuningModularityTest`).

6. **9-Language Localization Parity (`REQ-UI-408.6`)**:
   * All new `workout_detail_tab_*`, `workout_detail_section_*`, and `workout_detail_tabs_config_*` string resources SHALL exist and be non-blank with matching positional format specifiers across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-408`, complementing `REQ-UI-240`, `REQ-UI-262`, `REQ-UI-285`, `REQ-UI-337`, `REQ-UI-338`, `REQ-UI-355`, `REQ-UI-359`, `REQ-UI-403`, `REQ-UI-404`, and `REQ-UI-407`.
2. **Historical Origin & Commit Trace**:
   * `ATT-2310` (`REQ-UI-285`) introduced `WorkoutDetailPreferences` and `WorkoutSectionType` (`9` values) shared between `WorkoutSummary.kt` and `TrackOnMapScreen.kt`.
   * `ATT-3267` (`REQ-UI-337`) & `ATT-3266` (`REQ-UI-338`) added `preMapSections` (`32%` max height cap) and `postMapSections` around `MapDetailLayout`.
   * `ATT-3395` (`REQ-UI-359`) unified post-map sections into `analyticsContent`.
3. **Root Reason for Existing Formulation**: `TrackOnMapScreen` originally tried to render all 10 workout sections simultaneously on a single vertical page with a split map and two separate scroll areas.
4. **Preservation of Core Invariants**:
   * `WorkoutSectionType` (`9` values) and `WorkoutSummary.kt` section ordering remain 100% untouched.
   * All 7 existing `TrackOnMapScreen*ContractTest` suites remain 100% green by preserving legacy `TrackOnMapScreen` parameters and single-page `MapDetailLayout` fallback when `useTabbedLayout = false` or custom `metadataContent`/`analyticsContent` lambdas are supplied.
   * Every file in `ui/settings/tuning/` stays strictly `< 400` lines (`AdvancedTuningModularityTest`).

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Default 4-Tab Archetype & Serialization Round-Trip)**:
  * *Given* a fresh install or `resetWorkoutDetailTabs()`,
  * *When* `WorkoutDetailTabConfig.DEFAULT_TABS` is inspected or serialized/deserialized via `serializeTabs` / `deserializeTabs`,
  * *Then* 4 tabs are returned (`OVERVIEW`: `[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`, `MAP_AND_GRAPHS`: `[MAP, ELEVATION, CHARTS]`, `ZONES`: `[ZONES]`, `LAPS_AND_SEGMENTS`: `[LAPS, STRAVA]`) with exact round-trip fidelity.
* **AC-2 (Map Pinning & Conflict-Free Layout Mode Resolution)**:
  * *Given* a tab configured with `[DETAILS, MAP, CHARTS]`,
  * *When* `tab.normalizedSections` and `tab.layoutMode` are evaluated,
  * *Then* `MAP` is automatically pinned at index `0` (`[MAP, DETAILS, CHARTS]`) and `layoutMode` resolves to `MODE_B_SPLIT_MAP` (`MODE_A_CARD_SCROLL` when `MAP` is absent; `MODE_C_FULL_MAP` when `MAP` is the sole active section).
* **AC-3 (Smart Horizontal Gesture Isolation)**:
  * *Given* the default 4 tabs on an outdoor GPS workout,
  * *When* `requiresHorizontalGestureIsolation` is evaluated per tab,
  * *Then* it returns `false` on Tab 1 (`Overview`), Tab 3 (`Zones`), and Tab 4 (`Laps & Segments`) (allowing horizontal pager swipe) and `true` on Tab 2 (`Map & Graphs`) (protecting map pan and chart scrubbing from accidental tab switches).
* **AC-4 (Auto-Hiding Empty Tabs)**:
  * *Given* an indoor stationary workout (`hasMapData = false`, `hasElevationData = false`, `hasStravaSegments = false`, `hasMeaningfulLaps = false`),
  * *When* `filterVisibleWorkoutDetailTabs` is evaluated against tabs where a tab has only `[MAP]` or `[LAPS, STRAVA]`,
  * *Then* tabs with zero available sections are automatically hidden while tabs with available data remain visible.
* **AC-5 (Tab CRUD & Section Reordering Helpers)**:
  * *Given* a list of `WorkoutDetailTabConfig` tabs in `WorkoutDetailTabsConfigSheet`,
  * *When* the user adds a custom tab, renames a tab, reorders tabs, toggles/reorders sections within a tab, or deletes a tab,
  * *Then* pure state mutation helpers update the tab list deterministically, prevent deleting the last remaining tab (`tabs.size == 1`), and pin `MAP` at index `0` whenever enabled.

---

## 3. Test Specification (`TST-UI-371`)

### Test Suite: `WorkoutDetailTabsTest.kt` (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabsTest.kt`)

| Test Case ID | Test Method Name | Requirement | Verification Scope |
| :--- | :--- | :--- | :--- |
| `TST-UI-371.1` | `defaultTabs_matchFourCanonicalArchetypesAndPreserveWorkoutSectionTypeCount` | `REQ-UI-408.1` | Verifies `WorkoutDetailSectionType.entries.size == 10`, `WorkoutSectionType.entries.size == 9`, and `WorkoutDetailTabConfig.DEFAULT_TABS` has 4 tabs (`OVERVIEW`, `MAP_AND_GRAPHS`, `ZONES`, `LAPS_AND_SEGMENTS`) with exact section lists. |
| `TST-UI-371.2` | `serializeAndDeserializeTabs_roundTripsDefaultAndCustomTabsDefensively` | `REQ-UI-408.2` | Verifies `serializeTabs` / `deserializeTabs` round-trips default tabs, custom tab names (including special characters), deduplicates sections, ignores unknown section tokens, and falls back to `DEFAULT_TABS` on null/blank/corrupt input. |
| `TST-UI-371.3` | `layoutModeAndNormalizedSections_pinMapToTopAndResolveModeAModeBModeC` | `REQ-UI-408.3` | Verifies `normalizedSections` pins `MAP` to index `0` whenever present, and `resolveLayoutMode` returns `MODE_A_CARD_SCROLL` (no `MAP`), `MODE_B_SPLIT_MAP` (`MAP` + other sections), and `MODE_C_FULL_MAP` (`MAP` only). |
| `TST-UI-371.4` | `requiresHorizontalGestureIsolation_isolatesMapElevationAndChartsTabsOnly` | `REQ-UI-408.4` | Verifies `requiresHorizontalGestureIsolation` returns `true` when active `MAP`, `ELEVATION`, or `CHARTS` is present in the tab, and `false` for card-only tabs (`DETAILS`, `DESCRIPTION`, `EXTREMA`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`) or when map/chart data is absent/disabled. |
| `TST-UI-371.5` | `filterVisibleWorkoutDetailTabs_hidesTabsWithZeroAvailableSections` | `REQ-UI-408.4` | Verifies `filterVisibleWorkoutDetailTabs` omits tabs whose assigned sections all lack data for the current workout (e.g. `MAP`-only tab on indoor workout, `LAPS + STRAVA` tab when no laps/segments exist) and retains at least 1 fallback tab if all tabs would be empty. |
| `TST-UI-371.6` | `tabMutationHelpers_addRenameReorderDeleteAndToggleSectionsDeterministically` | `REQ-UI-408.5` | Verifies pure helpers `addCustomTab`, `renameTab`, `moveTab`, `deleteTab` (blocking deletion when `size == 1`), `toggleSectionInTab`, and `moveSectionInTab` (keeping `MAP` pinned at index `0`). |
| `TST-UI-371.7` | `uiAndSettingsIntegration_wiresTabbedContentConfigSheetAndPreservesTuningLineLimits` | `REQ-UI-408.5` | Verifies `TrackOnMapScreen.kt` invokes `WorkoutDetailTabbedContent` and `WorkoutDetailTabsConfigSheet`, `MyPreferenceManager.kt` exposes `workoutDetailTabsOrderFlow`, `WorkoutMasksAndCardsSection.kt` exposes the tab configuration action, and all `.kt` files under `ui/settings/tuning/` are strictly `< 400` lines. |
| `TST-UI-371.8` | `allNineLocales_defineWorkoutDetailTabAndSectionStringsWithMatchingSpecifiers` | `REQ-UI-408.6` | Parses all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and asserts every new `workout_detail_tab_*`, `workout_detail_section_*`, and `workout_detail_tabs_config_*` resource exists, is non-blank, and has matching positional format specifiers. |

---

## 4. 9-Language Localization Matrix (`REQ-UI-408.6`)

All 9 locale directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) SHALL include the following keys:
* `workout_detail_tab_overview`: Default Tab 1 title (*Overview* / *Übersicht* / *Resumen* / *Aperçu* / *Panoramica* / *概要* / *Overzicht* / *Przegląd* / *Visão geral*)
* `workout_detail_tab_map_graphs`: Default Tab 2 title (*Map & Graphs* / *Karte & Diagramme* / *Mapa y gráficos* / *Carte et graphiques* / *Mappa e grafici* / *地図とグラフ* / *Kaart & grafieken* / *Mapa i wykresy* / *Mapa e gráficos*)
* `workout_detail_tab_zones`: Default Tab 3 title (*Zones* / *Zonen* / *Zonas* / *Zones* / *Zone* / *ゾーン* / *Zones* / *Strefy* / *Zonas*)
* `workout_detail_tab_laps_segments`: Default Tab 4 title (*Laps & Segments* / *Runden & Segmente* / *Vueltas y segmentos* / *Tours et segments* / *Giri e segmenti* / *ラップ＆セグメント* / *Rondes & segmenten* / *Okrążenia i segmenty* / *Voltas e segmentos*)
* `workout_detail_tab_custom_default`: Fallback custom tab title (*Custom Tab* / *Eigener Tab* / ...)
* `workout_detail_section_details`: Section label for `WorkoutDetails` (*Workout Metrics & Summary* / *Trainingsmetriken & Übersicht* / ...)
* `workout_detail_tabs_config_title`: Bottom sheet & settings row title (*Configure Detail Tabs* / *Detail-Tabs konfigurieren* / ...)
* `workout_detail_tabs_config_subtitle`: Subtitle explaining per-tab customization (*Customize tabs and assign sections for the Workout Details screen* / *Tabs und Sektionen der Trainingsdetails individuell anpassen* / ...)
* `workout_detail_tabs_add_tab`: Action button (*Add Tab* / *Tab hinzufügen* / ...)
* `workout_detail_tabs_rename_tab`: Rename label (*Tab Name* / *Tab-Name* / ...)
* `workout_detail_tabs_delete_tab`: Delete action (*Delete Tab* / *Tab löschen* / ...)
* `workout_detail_tabs_reset_defaults`: Reset action (*Reset to Default Tabs* / *Standard-Tabs wiederherstellen* / ...)
* `workout_detail_tabs_sections_header`: Header for section checklist/order (*Assigned Sections* / *Zugewiesene Sektionen* / ...)
* `workout_detail_tabs_map_pinned_hint`: Hint explaining map pinning (*Map is always anchored at the top when enabled in a tab* / *Die Karte wird im Tab immer oben fixiert* / ...)

---

## 5. Bidirectional Traceability Matrix

| Requirement ID | Requirement Clause | Test Case ID | Target Production & Test Files |
| :--- | :--- | :--- | :--- |
| `REQ-UI-408.1` | 10 `WorkoutDetailSectionType` values & 4 Default Tabs (`Overview`, `Map & Graphs`, `Zones`, `Laps & Segments`), preserving 9-value `WorkoutSectionType` | `TST-UI-371.1` | `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabsTest.kt` |
| `REQ-UI-408.2` | Deterministic serialization/deserialization & `MyPreferenceManager` persistence | `TST-UI-371.2`, `TST-UI-371.7` | `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutDetailTabsTest.kt` |
| `REQ-UI-408.3` | 3 conflict-free per-tab layout modes (`MODE_A_CARD_SCROLL`, `MODE_B_SPLIT_MAP`, `MODE_C_FULL_MAP`) & `MAP` pinned at index `0` | `TST-UI-371.3`, `TST-UI-371.7` | `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabsTest.kt` |
| `REQ-UI-408.4` | Smart horizontal gesture isolation (`userScrollEnabled`) & auto-hiding empty tabs (`filterVisibleWorkoutDetailTabs`) | `TST-UI-371.4`, `TST-UI-371.5` | `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabsTest.kt` |
| `REQ-UI-408.5` | `WorkoutDetailTabsConfigSheet` CRUD/reorder & dual entry points (`WorkoutHeader` + `WorkoutMasksAndCardsSection.kt` `< 400` lines) | `TST-UI-371.6`, `TST-UI-371.7` | `WorkoutDetailTabsConfigSheet.kt`, `TrackOnMapScreen.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabsTest.kt` |
| `REQ-UI-408.6` | 9-language localization parity across `values*/strings.xml` | `TST-UI-371.8` | `values*/strings.xml`, `WorkoutDetailTabsTest.kt` |
