# Living Requirements: UI Components & Design System (Part 4 of 5)

**Document ID**: `DOC-REQ-UI-P4`  
**Version**: 1.0.1  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part4`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Requirements**: 78

---

### REQ-UI-356: Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance & Semantic Severity Color Tiering [Verified]
- **Tests**: `TST-UI-316`, `TST-STB-019`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, `WorkoutNavigationEvents.kt`, `Color.kt`
- **Rationale**: The system SHALL evaluate paired and active sensors at workout start and during active live tracking, dispatching non-blocking Material 3 alert snackbars with system navigation bar inset compliance, floating control clearance, and dedicated semantic severity color tiering (ATT-3313, ATT-3451, refining and extending `REQ-UI-350` and `REQ-CON-007`):
1. *Workout Start Evaluation & Severity Tiering*:
• Upon tracking launch (`REQUEST_START_TRACKING`), the system SHALL asynchronously evaluate all paired remote devices in SQLite storage (`DevicesDbHelper.PAIRED > 0`).
• If one or more paired remote sensors have `LAST_BATTERY_PERCENTAGE >= 0` and `<= 20`, the system SHALL emit a `LowBatteryAlert` event via `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.
• The alert SHALL assign `BatteryAlertSeverity.CRITICAL` if any alerted sensor has battery $\le 10\%$, and `BatteryAlertSeverity.WARNING` if all alerted sensors have battery in $[11, 20]$.
2. *In-Ride Real-Time Battery Drop & Escalation Evaluation*:
• During active tracking (`TrackingMode.TRACKING` or `TrackingMode.PAUSED`), whenever an active session sensor reports a battery percentage in $[0, 20]$ via `DeviceDataRepository.deviceBatteryUpdates`, the system SHALL evaluate the sensor's alert state.
• If the sensor has not yet alerted in the session, a `LowBatteryAlert` event SHALL be emitted with the appropriate severity (`WARNING` for $11\dots 20\%$, `CRITICAL` for $\le 10\%$).
• If the sensor previously triggered a `WARNING` alert, and subsequently drops to $\le 10\%$, the system SHALL emit an escalated `CRITICAL` alert.
• Repeated updates within the same severity tier SHALL be suppressed by session deduplication.
• Session deduplication sets SHALL be cleared whenever tracking is stopped or a new workout is created.
3. *Tracking & Aftermath Screen Presentation & Preemption (`TrackingTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`)*:
• Both screens SHALL host a `SnackbarHost` collecting `WorkoutNavigationEvents.lowBatteryAlert` and displaying `BatterySnackbarVisuals`.
• *Instantaneous Preemption*: When a `CRITICAL` alert arrives while a snackbar is active, the active snackbar SHALL be immediately dismissed via `snackbarHostState.currentSnackbarData?.dismiss()` so the critical alert renders without delay.
• *Warning Tier Styling*: Amber container (`LightWarningContainer` / `DarkWarningContainer`), deep brown/pale amber text (`LightOnWarningContainer` / `DarkOnWarningContainer`), and high-contrast amber action button (`LightWarningAction` / `DarkWarningAction`) with WCAG contrast $\ge 7:1$.
• *Critical Tier Styling*: Red/error container (`MaterialTheme.colorScheme.errorContainer`), high-contrast text (`onErrorContainer`), and action button (`error`).
• *Non-Battery Fallback*: General non-battery snackbars SHALL retain standard Material 3 colors (`inverseSurface` / `inverseOnSurface` / `primary`).
• Action `[Zu Sensoren]` (`R.string.sensor_battery_action_view_sensors`) SHALL navigate to `R.id.drawer_my_sensors` via `MainActivityWithNavigation.navigateToDrawerItem()` and clear the alert via `consumeLowBatteryAlert()`.
4. *Navigation Bar Inset & Floating Control Ergonomics*:
• The `SnackbarHost` in `TrackingTabsScreen.kt` SHALL configure `Modifier.navigationBarsPadding()` ensuring zero visual occlusion by the Android system navigation bar or gesture handle.
• When the `LapButton` is visible and active on the current tab, the `SnackbarHost` SHALL apply additional vertical clearance (`bottom = 64.dp`), ensuring the snackbar floats cleanly above the lap button without overlapping. When the lap button is suppressed, standard padding (`bottom = 8.dp`) SHALL be applied.
5. *Normal Battery Neutrality*:
• When all paired and active sensors have battery $> 20\%$ (or negative unmeasured values), zero snackbars SHALL be triggered.
6. *100% 9-Language Localization Parity*:
• All snackbar messages and action labels SHALL maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Amends and extends `REQ-UI-350` (*Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls*, `ATT-3167`) and `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting*, `ATT-2192`), targeting `TrackingTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, and `WorkoutNavigationEvents.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (`ATT-2192`), Sprint 2026-41.9 (`ATT-3167`), and Sprint 2026-42.1 (`ATT-3313`).
3. *Root Reason for Existing Formulation*: Low battery alerting was previously restricted to workout completion and then expanded to tracking HUD in ATT-3313 using primary blue as a safe placeholder. ATT-3451 introduces semantic color tiering (amber warning, red critical) and preemption.
4. *Preservation of Core Invariants*: The 20% critical threshold, non-blocking M3 snackbar ergonomics, system navigation bar padding (`navigationBarsPadding()`), action navigation to `drawer_my_sensors`, sticky event consumption, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a paired sensor with recorded battery percentage 15% in the database,
• *When* the athlete starts a workout,
• *Then* a snackbar is displayed on `TrackingTabsScreen` with amber container and high-contrast text stating `⚠️ [Sensor Name]: Akku schwach (15%)` with action `[Zu Sensoren]` in high-contrast amber.
• *Given* an active workout tracking session where an active sensor drops to 8%,
• *When* the alert is triggered,
• *Then* the snackbar renders in red `errorContainer` and `onErrorContainer`.
• *Given* an active sensor that alerted at 18% (warning),
• *When* the sensor subsequently drops to 9% (critical),
• *Then* an escalated critical alert is triggered.
• *Given* a warning snackbar currently resting on screen when a critical alert arrives,
• *When* processed,
• *Then* the active snackbar is immediately dismissed and replaced by the critical alert.
• *Given* a low battery snackbar displayed on `TrackingTabsScreen`,
• *When* the athlete taps `[Zu Sensoren]`,
• *Then* the app navigates to "Meine Sensoren" and clears the active alert.

**Invariants**: Non-blocking M3 snackbars, 20% threshold, navigation bar insets, session deduplication, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-357: Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization [Verified]
- **Tests**: `TST-UI-317`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningScreen.kt`, `AdvancedTuningAccordion.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`
- **Rationale**: The system SHALL migrate Advanced Settings from a nested `ModalBottomSheet` into a dedicated full-screen Compose destination `AdvancedTuningScreen.kt` (`NavRoutes.ADVANCED_SETTINGS`), eliminating accidental drag-dismissals during list scrolling, and SHALL restructure the category hierarchy into an intuitive 3-phase athletic lifecycle sequence (ATT-3344, amending `REQ-SET-073`, `REQ-SET-074`, and `REQ-UI-222`):
1. *Dedicated Full-Screen Screen Architecture (`AdvancedTuningScreen.kt`)*:
• The system SHALL host Advanced Settings within a Material 3 `Scaffold` in `AdvancedTuningScreen.kt`.
• The screen SHALL include a `TopAppBar` rendering the localized title `R.string.advanced_tuning_title`, a navigation back arrow (`Icons.AutoMirrored.Filled.ArrowBack`) calling `onNavigateBack()`, and an action icon button to restore factory defaults (`Icons.Default.RestartAlt`, `R.string.reset_to_defaults`).
• The screen SHALL provide a sticky bottom bar or action control hosting Save (`R.string.save`) and Cancel actions (`AppDialogActions.SaveCancel`).
• Tapping Back or invoking system back gesture SHALL safely pop back to the previous screen without saving uncommitted edits; tapping Save SHALL atomically persist all settings before navigating back.
2. *Elimination of Scroll Dismissals (Lösung A)*:
• Advanced Settings SHALL NOT be hosted inside `ModalBottomSheet` or `BottomSheetScaffold`.
• The content container SHALL utilize full-height vertical scrolling (`Modifier.fillMaxSize().verticalScroll(rememberScrollState())`) decoupled from modal dismiss gestures, guaranteeing zero accidental closures upon upward scrolling or momentum flings.
3. *Navigation & Route Integration (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)*:
• `NavRoutes` SHALL define `ADVANCED_SETTINGS = "advanced_settings"`.
• `NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning)` SHALL return `NavRoutes.ADVANCED_SETTINGS`.
• `NavRoutes.toDrawerItemId(NavRoutes.ADVANCED_SETTINGS)` SHALL return `R.id.drawer_advanced_tuning`.
• `NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning)` SHALL return `null`.
• `ATrainingTrackerApp.kt` SHALL register `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
• `DisplaySettingsDialog.kt`'s tuning link (`onNavigateToTuning`) SHALL navigate directly to `NavRoutes.ADVANCED_SETTINGS`.
4. *Lifecycle-Driven Category Reorganization (`TuningSection`)*:
• The category enum `TuningSection` and the accordion layout SHALL enforce the canonical 7-section sequence:
1. `NAVIGATION`: Navigation & Kartenführung (`R.string.tuning_cat_navigation`, promoted to Section 1).
2. `COCKPIT_TYPOGRAPHY`: Cockpit-Typografie & Sensorfelder (`R.string.tuning_cat_cockpit_typography`).
3. `BATTERY_SAVER`: AMOLED & Akkuschoner (`R.string.tuning_cat_battery_saver`).
4. `SENSORS_GPS`: Sensoren & GPS-Filterung (`R.string.tuning_cat_sensors_gps`).
5. `SENSOR_SEARCH`: Sensorsuche & Verbindungen (`R.string.tuning_cat_sensor_search`).
6. `WORKOUT_MASKS_CARDS`: Workout-Masken & Detailkarten (`R.string.tuning_cat_workout_masks_cards`).
7. `AFTERMATH_ANALYSIS`: Aftermath & Analyse (`R.string.tuning_cat_aftermath`).
5. *Preservation of Invariants & Modularity*:
• All 25+ parameter DataStore keys, default values, and clamping bounds MUST remain strictly identical.
• Modularity constraint: Every file in `ui.settings.tuning` MUST remain strictly < 400 lines of code (`AdvancedTuningModularityTest`).
• All accordion cards retain `RoundedCornerShape(12.dp)`, `surfaceVariant`, `outlineVariant`, animated chevron rotation, live active-value summary subtitles, and initially collapsed state (`emptySet()`).
• 100% 9-language translation parity maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends and modernizes `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*, `ATT-1304`), `REQ-SET-074` (*Expert Settings Drawer Positioning*, `ATT-1646`), and `REQ-UI-222` (*Accordion Architecture for Advanced Settings*, `ATT-1821`, `ATT-1957`).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-1304`), Sprint 2026-40.8 (`ATT-1821`), Sprint 2026-40.9 (`ATT-1957`), and Sprint 2026-40.10 (`ATT-2033`).
3. *Root Reason for Existing Formulation*: Advanced Tuning was embedded in `ModalBottomSheet` when settings modals were being unified. As tuning options grew across 7 subsections with nested scroll, scrolling to the top triggered parent sheet swipe-to-dismiss, accidentally dumping uncommitted user edits. Furthermore, Navigation options were relegated to the bottom, separating them from Cockpit options.
4. *Preservation of Core Invariants*: Parameter schemas, DataStore persistence (`TuningConfig`), factory reset, category composables, advisory warning card, and 400-line modularity boundaries remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opening Advanced Settings from the drawer or Display Settings dialog,
• *When* the screen renders,
• *Then* it displays as a dedicated full-screen screen with `TopAppBar` and back arrow, decoupled from `ModalBottomSheet`.
• *Given* the athlete browsing settings,
• *When* expanding accordions and rapidly scrolling up/down across the full list,
• *Then* the screen remains steadily open and never dismisses due to scroll overscroll gestures.
• *Given* the category list,
• *When* inspecting the first section,
• *Then* Navigation & Kartenführung appears as Section 1, followed by Cockpit, AMOLED Battery Saver, Sensors, Sensor Search, Workout Masks, and Aftermath Analysis.
• *Given* modified tuning parameters,
• *When* the athlete taps Back without saving,
• *Then* uncommitted changes are discarded and the screen pops backstack.
• *Given* modified tuning parameters,
• *When* the athlete taps Save,
• *Then* all changes are atomically committed to DataStore and SharedPreferences and the screen pops backstack.

**Invariants**: Parameter ranges, DataStore keys, warning card, factory reset, zero tonal elevation, 400-line file limit, and 9-language localization parity MUST NOT be broken.

### REQ-UI-358: Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment [Verified]
- **Tests**: `TST-UI-319`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL ensure that when a tracking tab displays both a sensor grid and an embedded map view (`showMap == true`), the map expands flexibly to consume 100% of all vertical space remaining between the sensor grid and the elevation profile (or bottom cockpit boundary), eliminating dead white space, while bounding multi-row sensor grids to at most 50% of available viewport height with internal vertical scrolling (ATT-3343, amending `REQ-UI-346` and `REQ-UI-339`, complementing `REQ-UI-355`):
1. *Available Viewport Measurement (`SensorGridScreen.kt`)*:
• The main tracking cockpit container in `SensorGridScreen.kt` SHALL be hosted within `BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()))`.
• The available height `val availableHeight = maxHeight` SHALL constrain child elements proportionally.
2. *Asymmetric Weight Allocation for Sensor Grid & Map*:
• When `state.mapState.showMap == true`:
- The sensor grid `Column` SHALL NOT declare `Modifier.weight(...)`.
- The sensor grid `Column` SHALL declare `Modifier.fillMaxWidth().heightIn(max = availableHeight * 0.5f).verticalScroll(rememberScrollState())`.
- For 1–2 rows, the sensor grid measures at its exact intrinsic content height; for 4–6 rows, it is capped at 50% of `availableHeight` and scrolls internally.
- The map `Box` SHALL declare `Modifier.fillMaxWidth().weight(1f, fill = true)`.
- Because the map is the sole weighted child in the Column, it expands flexibly to occupy 100% of all vertical space remaining after measuring the sensor grid and elevation profile.
• When `state.mapState.showMap == false`:
- The sensor grid `Column` SHALL declare `Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f, fill = false)`, preserving the exact canonical behavior of `REQ-UI-346`.
3. *Elevation Profile Anchoring & Adaptive Height Preservation*:
• The elevation profile `Surface` SHALL remain non-weighted with `Modifier.fillMaxWidth().padding(top = 8.dp)`.
• The elevation profile SHALL continue to calculate its height adaptively between $70\text{dp}$ and $200\text{dp}$ via `calculateElevationProfileHeight(range)` per `REQ-UI-346`.
• When the elevation profile is toggled off or contains fewer than 2 points, the map expands seamlessly down to the bottom screen boundary.
4. *Preservation of System Invariants*:
• Zero regression to `TrackingMapContainer` recomposition barrier isolation (`REQ-UI-326`).
• Ambient navigation overlays, live climb sheets, and configuration mode controls remain fully functional.
• 100% clean-room unit test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107), and complements `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Scoping During Tracking*, Sprint 2026-41.10 / Sprint 2026-42.1, ATT-3199 & ATT-3317). Targets `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`) introduced `Modifier.weight(1f, fill = false)` on the sensor grid. Sprint 2026-41.9 (`ATT-3173`, commit `ad0c2020`) removed weight from the elevation profile.
3. *Root Reason for Existing Formulation*: In `ATT-3107`, `weight(1f, fill = false)` was placed on the sensor grid so it wraps 1–2 rows and scrolls for many rows. However, when `showMap == true`, the map was also given `weight(1f)`. In Compose Column, weighted children divide available space proportionally. With `fill = false`, the sensor grid only consumes intrinsic height, but Compose does not donate unused quota to the other weighted child (the map). The map remains capped at 50%, leaving the remaining space blank as dead white space.
4. *Preservation of Core Invariants*: Sensor grid scrolling, canonical adaptive height ($70\text{dp} \dots 200\text{dp}$), non-weighted wrapping elevation profile Surface, map recomposition barrier isolation, ambient overlays, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a tracking view configured with 1 row of sensor fields, a map, and an elevation profile,
• *When* viewing the tracking screen,
• *Then* the map expands to fill all remaining vertical space between the sensor row and the elevation profile, with zero dead white space below the elevation profile.
• *Given* a tracking view configured with 5 rows of sensor fields and a map,
• *When* viewing the screen,
• *Then* the sensor grid is capped at 50% of the available height and scrolls smoothly without pushing the map or elevation profile off screen.
• *Given* a tracking view with the map toggled off (`showMap == false`) and elevation profile enabled,
• *When* viewing the tracking screen,
• *Then* the sensor grid wraps content without stretching and the elevation profile renders at its canonical adaptive height directly below it.
• *Given* an active tracking view with map and elevation profile,
• *When* the athlete toggles the elevation profile off in configuration mode,
• *Then* the map dynamically expands to fill the entire vertical space below the sensor grid down to the screen edge.

**Invariants**: Sensor grid scrollability, canonical adaptive height, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-359: Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand [Verified]
- **Tests**: `TST-UI-320`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL ensure that when a Strava Live Segment or Route Climb is active during workout tracking (`shouldShowBottomSheet == true`), the athlete can reliably re-expand the bottom sheet (`BottomSheetScaffold`) to full height (`SheetValue.Expanded`) via an upward swipe gesture along the bottom edge or a single tap on the peek header, after the sheet has been collapsed or dismissed (ATT-3341, amending `REQ-UI-343` and `REQ-UI-327`):
1. *Bottom-Edge Swipe-Up Gesture Interceptor (`SensorGridScreen.kt`)*:
• When `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`, the layout SHALL host a gesture detection area aligned at `Alignment.BottomCenter` covering the bottom edge and peek region (`BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`).
• The gesture detector SHALL monitor vertical drag gestures using `pointerInput(Unit)` and `detectVerticalDragGestures`.
• When an upward drag is registered with accumulated vertical drag delta $\Delta y < -20\text{dp}$ (or negative upward velocity), the system SHALL execute `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
2. *Peek Area Tap-to-Expand Interaction*:
• When `scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded`, tapping anywhere on the visible sheet header or gesture target SHALL invoke `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
3. *Touch Neutrality When Inactive*:
• When `shouldShowBottomSheet == false` (or when `screenMode != ScreenMode.TRACKING` or `isTabActive == false`), zero bottom gesture strips or swipe interceptors SHALL be composed or active, and background views (map panning, zoom controls, sensor grid vertical scrolling, configuration mode) SHALL receive 100% of touch events without interference or latency.
4. *Preservation of System Invariants*:
• The initial unconditional animation to `SheetValue.Expanded` on new entity activation (`REQ-UI-343`) SHALL remain fully operative.
• The sheet container shape, elevation, and contour styling per `BottomSheetDesign` SHALL remain intact.
• 100% clean-room test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*, Sprint 2026-41.9 / Sprint 2026-42.1, ATT-3176 & ATT-3319) and `REQ-UI-327` (*Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization*, Sprint 2026-41.7, ATT-2945). Targets `SensorGridScreen.kt` and `SensorGridBottomSheetGestureTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2945` established the bottom sheet scaffold with active tab gating. `ATT-3319` updated the initial value and reactive launched effect to default to `SheetValue.Expanded` upon entity activation.
3. *Root Reason for Existing Formulation*: In `ATT-3319`, automatic pop-up was implemented so athletes wouldn't have to manually pull the sheet open on every climb. However, when an athlete manually dismissed or collapsed the sheet to inspect underlying sensor grid tiles (power, cadence, lap timer), `activeEntityKey` remained constant, leaving no reactive or gestural mechanism to re-expand the sheet for the rest of the climb/segment.
4. *Preservation of Core Invariants*: Initial expansion on new entity, hidden state when inactive, clean peek height, and zero interference when inactive are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session with an active Live Segment or Climb whose bottom sheet was swiped down,
• *When* the athlete swipes upward from the bottom of the screen,
• *Then* the bottom sheet smoothly re-expands to `SheetValue.Expanded`, displaying the full elevation profile and pacing HUD.
• *Given* the sheet is resting in `SheetValue.PartiallyExpanded`,
• *When* the athlete taps the visible header / peek area,
• *Then* the sheet smoothly animates to `SheetValue.Expanded`.
• *Given* no live segment or climb is active (`shouldShowBottomSheet == false`),
• *When* the athlete interacts with the lower portion of the screen,
• *Then* sensor grid scrolling and map gestures operate without interference.

**Invariants**: Initial automatic expansion, active-tab gating, inactive touch neutrality, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-360: Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery [Verified]
- **Tests**: `TST-UI-323`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ic_turn_slight_right.xml`, `ic_turn_slight_left.xml`, `TurnDirection.kt`, `TurnPromptBanner.kt`
- **Rationale**: The system SHALL replace the distorted vector path drawables for slight right (`ic_turn_slight_right.xml`) and slight left (`ic_turn_slight_left.xml`) with canonical, bottom-anchored Material Design vector geometries, and SHALL provide an interactive Jetpack Compose `@Preview` gallery displaying all 8 `TurnDirection` symbols across Light and Dark themes (ATT-3455, complementing `REQ-MAP-028` and `REQ-UI-324`):
1. *Canonical Vector Path Replacement (`ic_turn_slight_right.xml`, `ic_turn_slight_left.xml`)*:
• In `ic_turn_slight_right.xml`, the vector path SHALL rise vertically from baseline $y=20$ with standard 2dp stroke width ($x \in [9, 11]$), transition through a smooth 45° curve to the upper right via control point $(9.59, 11.01)$, and conclude with a symmetric 45° arrowhead with 2dp wing thickness: `M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z`
• In `ic_turn_slight_left.xml`, the vector path SHALL provide an exact horizontal mirror across the vertical centerline ($x' = 24 - x$): `M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z`
• Both assets SHALL preserve standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`) and primary tint fill color token `#FF1976D2`.
2. *Interactive Jetpack Compose `@Preview` Gallery (`TurnPromptBanner.kt`)*:
• The system SHALL provide `TurnCueIconsGallery` and multi-theme `@Preview` composables (`TurnCueIconsGalleryLightPreview`, `TurnCueIconsGalleryDarkPreview`) in `TurnPromptBanner.kt`.
• The gallery SHALL display all 8 `TurnDirection` enum entries (`LEFT`, `RIGHT`, `SLIGHT_LEFT`, `SLIGHT_RIGHT`, `SHARP_LEFT`, `SHARP_RIGHT`, `STRAIGHT`, `U_TURN`) in a structured grid/row layout.
• Each item SHALL display the vector icon using `painterResource(id = direction.iconResId)` and the localized direction title via `stringResource(id = direction.displayNameResId)` with appropriate Material 3 typography and colors.
• Dual `@Preview` annotations SHALL support Light Theme and Dark Theme (`uiMode = Configuration.UI_MODE_NIGHT_YES`) with `showBackground = true`.
3. *Visual & Structural Contract Testing*:
• Unit test `TurnDirectionIconContractTest.kt` SHALL programmatically parse each vector XML resource referenced by `TurnDirection.values()`.
• Test SHALL verify XML root element `<vector>`, valid 24x24 viewport, non-empty `android:pathData`, and bottom-stem grounding ($y=19..20$).
• Test SHALL verify mathematical horizontal symmetry between `ic_turn_slight_right` and `ic_turn_slight_left`.
4. *Preservation of System Invariants*:
• Real-time turn-by-turn guidance math (`TurnByTurnNavigationEngine.kt`), angle threshold classification in `TurnCueDetector.kt`, and audio alerting (`NavigationAudioAlertManager.kt`) MUST remain completely unaltered.
• 100% full clean-room regression pass rate (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-360`). No existing requirement logic modified. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450) and `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Top-Level Spatial Overlay Integration & Design Guideline Harmonization*, ATT-2941).
2. *Historical Origin & Commit Trace*: Baseline turn cue drawables were originally added during initial turn prompt development in `ATT-1450` (Sprint 2026-40.16). While cardinal turns (`LEFT`, `RIGHT`, `STRAIGHT`) used clean paths, `SLIGHT_RIGHT` and `SLIGHT_LEFT` accidentally used preliminary SVG trace data.
3. *Root Reason for Existing Formulation*: The original asset was imported from an unvetted vector snippet that lacked bottom stem grounding.
4. *Preservation of Core Invariants*: Viewport 24x24, color `#FF1976D2`, and integration with `TurnDirection` enum and `TurnPromptBanner.kt` remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an upcoming navigation cue with `TurnDirection.SLIGHT_RIGHT`,
• *When* rendering `ic_turn_slight_right.xml` in the navigation banner,
• *Then* the icon displays a clean vertical base rising from the bottom baseline $y=20$ and smoothly veering 45° to the upper-right with a symmetric arrowhead.
• *Given* an upcoming navigation cue with `TurnDirection.SLIGHT_LEFT`,
• *When* rendering `ic_turn_slight_left.xml`,
• *Then* the icon displays the identical, cleanly mirrored 45° veer to the upper-left.
• *Given* a developer viewing Compose previews in Android Studio,
• *When* rendering `TurnCueIconsGalleryPreview`,
• *Then* all 8 turn icons are displayed side by side in a structured grid with their titles in both Light and Dark themes for immediate visual verification.
• *Given* all 8 `TurnDirection` drawables rendered in the preview gallery,
• *When* inspecting the icon set,
• *Then* stem width (2dp), baseline position ($y=19..20$), and arrowhead proportions are visually harmonious across all directions.

**Invariants**: Pure vector XML assets, no changes to route navigation math, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-361: Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification [Verified]
- **Tests**: `TST-UI-324`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
3. *Rotational Progression & Preview Gallery Compatibility*:
• The diagonal $135^\circ$ / $-135^\circ$ arrowheads SHALL complete the consistent rotational progression of the turn icon family in `TurnCueIconsGallery` (`Straight 0°` $\uparrow$ $\to$ `Slight Right 45°` $\nearrow$ $\to$ `Right 90°` $\rightarrow$ $\to$ `Sharp Right 135°` $\searrow$ $\to$ `U-Turn 180°` $\downarrow$ $\to$ `Sharp Left -135°` $\swarrow$ $\to$ `Left -90°` $\leftarrow$ $\to$ `Slight Left -45°` $\nwarrow$).
• Sharp turn cues MUST exhibit distinct visual separation from U-turn cues (`ic_turn_u_turn.xml`), preventing false turn-around interpretations by athletes.
4. *Preservation of System Invariants*:
• Real-time turn-by-turn guidance math (`TurnByTurnNavigationEngine.kt`), angle threshold classification in `TurnCueDetector.kt`, and audio alerting (`NavigationAudioAlertManager.kt`) MUST remain completely unaltered.
• 100% full clean-room regression pass rate (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*, ATT-3493) and `TST-UI-324`. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450), `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Overlay*, ATT-2941), and `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Harmonization*, ATT-3455).
2. *Historical Origin & Commit Trace*: Originally introduced in commit `39610f62` (ATT-3493, Sprint 2026-42.3).
3. *Root Reason for Existing Formulation*: The author in ATT-3493 sought to ground sharp turns to baseline $y=20$ and add downward arrowheads, but accidentally formulated a $180^\circ$ semicircle arch spanning across to $x=18$ paired with a vertical downward shaft and a downward-pointing arrow tip, unintentionally mirroring the exact visual silhouette of `ic_turn_u_turn.xml`.
4. *Preservation of Core Invariants*: Baseline grounding at $y=20$ with uniform 2dp stroke width, standard 24x24dp viewport, `#FF1976D2` tint fill token, contract safety comments, and zero modifications to navigation route heuristics or angle thresholds are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an upcoming navigation cue with `TurnDirection.SHARP_RIGHT`,
• *When* rendering `ic_turn_sharp_right.xml` in the navigation banner,
• *Then* the icon displays a clean vertical base rising from bottom baseline $y=20$, looping through an acute ~135° bend, and exiting with a diagonal shaft and arrowhead pointing South-East ($135^\circ$, $\searrow$), with zero resemblance to a $180^\circ$ U-turn.
• *Given* an upcoming navigation cue with `TurnDirection.SHARP_LEFT`,
• *When* rendering `ic_turn_sharp_left.xml`,
• *Then* the icon displays the identical, cleanly mirrored acute bend and diagonal arrowhead pointing South-West ($-135^\circ$, $\swarrow$).
• *Given* all 8 turn icons displayed in `TurnCueIconsGallery`,
• *When* visually inspecting the rotational sequence from straight to U-turn,
• *Then* each step progresses in consistent, unambiguous angular increments with harmonious 2dp stem widths, baseline grounding, and canonical arrowheads.
• *Given* the vector definitions of sharp right and sharp left,
• *When* parsed by `TurnDirectionIconContractTest.kt`,
• *Then* all 22 path milestone coordinates reflect across $x=12$ with zero divergence ($x_R + x_L = 24.0 \pm 0.01$, $
- **Implementation**: y_R - y_L
- **Rationale**: The system SHALL refine the vector path drawables for sharp right (`ic_turn_sharp_right.xml`) and sharp left (`ic_turn_sharp_left.xml`) with canonical, bottom-anchored Material Design vector geometries featuring an acute ~135° deflection bend and diagonal downward-pointing arrowheads ($\searrow$ and $\swarrow$), completely eliminating visual resemblance to the 180° circular dome loop of U-turns, and SHALL enforce visual and mathematical reflection symmetry in contract testing (ATT-3493, ATT-3573, complementing `REQ-MAP-028`, `REQ-UI-324`, and `REQ-UI-360`):
1. *Canonical Vector Path Replacement (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`)*:
• In `ic_turn_sharp_right.xml`, the vector path SHALL rise vertically from baseline $y=20$ with standard 2dp stroke width ($x \in [6, 8]$) to $y=8.5$, bend sharply around an acute ~135° deflection arc ($cx=10.5, cy=8.5$, outer radius $R=4.5$, inner radius $r=2.5$), extend diagonally along $(+1, +1)$, and terminate with a diagonal downward-pointing $135^\circ$ arrowhead pointing South-East ($\searrow$): `M6,20h2V8.5c0,-1.38 1.12,-2.5 2.5,-2.5c0.66,0 1.3,0.26 1.77,0.73l2.5,2.5l-1.41,1.41l4.24,0l0,-4.24l-1.41,1.41l-2.5,-2.5c-0.84,-0.84 -1.99,-1.32 -3.18,-1.32c-2.49,0 -4.5,2.01 -4.5,4.5V20z`
• In `ic_turn_sharp_left.xml`, the vector path SHALL provide an exact horizontal reflection across the vertical centerline ($x' = 24 - x, y' = y$): `M18,20h-2V8.5c0,-1.38 -1.12,-2.5 -2.5,-2.5c-0.66,0 -1.3,0.26 -1.77,0.73l-2.5,2.5l1.41,1.41l-4.24,0l0,-4.24l1.41,1.41l2.5,-2.5c0.84,-0.84 1.99,-1.32 3.18,-1.32c2.49,0 4.5,2.01 4.5,4.5V20z`
• Both assets SHALL preserve standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`), primary tint fill color token `#FF1976D2`, and contract safety header comment `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
2. *Mathematical Reflection Symmetry & Baseline Grounding Contract Tests*:
• Unit test `TurnDirectionIconContractTest.kt` SHALL programmatically parse each vector XML resource for sharp right and sharp left.
• Test SHALL verify XML root element `<vector>`, valid 24x24 viewport, non-empty `android:pathData`, and bottom-stem grounding at $y=20$ (`M6,20h2` and `V20z` for right; `M18,20h-2` and `V20z` for left).
• Test SHALL verify mathematical horizontal reflection symmetry between `ic_turn_sharp_right` and `ic_turn_sharp_left` across midline $x=12$ ($x_R + x_L = 24.0 \pm 0.01$, $

### REQ-UI-362: Removal of Google Drive Format Scan Option from Workout Import Cards [Verified]
- **Tests**: `TST-UI-325`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL remove the Google Drive scan option across all workout format import cards (FIT, TCX, GPX) in `ImportBackupTabsScreen.kt`, eliminating confusing least-privilege OAuth scope restrictions while preserving native Storage Access Framework (SAF) Google Drive file picking and Google Drive full database backup/restore (ATT-3489, amending `REQ-UI-293`):
1. *Composable Format Import Card Decluttering (`ImportBackupTabsScreen.kt`)*:
• In `FormatImportCard`, the system SHALL remove parameters `googleDriveButtonText`, `isGoogleDriveConnected`, and `onGoogleDriveClick`.
• `FormatImportCard` SHALL render exactly two action buttons per card: Button 1 (Local File Picker via SAF) and Button 2 (Dropbox Scan). The third `OutlinedButton` for Google Drive scan SHALL be excised.
• In `ImportTabContent`, the system SHALL remove parameters `isGoogleDriveConnected` and `onGoogleDriveScanClick`, configuring FIT, TCX, and GPX cards strictly with local file and Dropbox handlers.
2. *Screen State & Dialog Simplification (`ImportBackupTabsScreen.kt`)*:
• `ImportBackupTabsScreen` SHALL remove unused state variables `showTuningDialogForGoogleDrive`, `showGoogleDriveDisconnectedDialog`, and `pendingGoogleDriveFormat`.
• The obsolete `showGoogleDriveDisconnectedDialog` alert dialog and `showTuningDialogForGoogleDrive` bottom sheet invocation SHALL be cleanly removed.
3. *String Resource Hygiene (`strings.xml`)*:
• Unused string resources `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` SHALL be removed across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero lint or parity regressions.
4. *Preservation of Core Invariants*:
• Google Drive database backup/restore (`.attbackup`) in `BackupTabContent` and automated background export capabilities SHALL remain 100% operational under `drive.file` scope.
• Local multi-document file picking via Android SAF (`ActivityResultContracts.OpenMultipleDocuments()`), supporting Google Drive cloud documents, remains fully preserved.
• Dropbox format scanning across all three format cards (`fit`, `tcx`, `gpx`) remains fully preserved.
• Pre-import cluster parameter tuning (`PreImportTuningBottomSheet`) remains mandatory before local or Dropbox batch imports commence.
• Clean-room unit regression test suite pass rate SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad (FIT, TCX, GPX)*, Sprint 2026-41.1, ATT-2623; amended ATT-3315).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2623` (commit `d5a9d217`), Sprint 2026-41.1.
3. *Root Reason for Existing Formulation*: Symmetrically presented Local, Dropbox, and Google Drive for each format. However, least-privilege `drive.file` OAuth scope prevents REST discovery of externally copied files, causing misleading empty scan results. Because SAF natively provides Google Drive file picking without sensitive scopes, the dedicated REST scan button was redundant and misleading.
4. *Preservation of Core Invariants*: Local SAF picking (which imports Google Drive files), Dropbox scanning, pre-import cluster tuning, and Google Drive database backup/restore remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating to the Import tab in `ImportBackupTabsScreen`,
• *When* inspecting the FIT, TCX, and GPX format import cards,
• *Then* each card renders exclusively the Local File Picker button ("Dateien importieren") and the Dropbox Scan button, with zero Google Drive scan buttons present.
• *Given* an athlete importing workouts from Google Drive,
• *When* tapping "Dateien importieren" on any format card,
• *Then* Android SAF document picker opens, allowing selection of `.fit`, `.tcx`, or `.gpx` files stored in Google Drive.
• *Given* an athlete with connected Google Drive,
• *When* accessing the Backup tab or automated export settings,
• *Then* Google Drive backup creation, restore, and automated export remain fully functional.
• *Given* all 9 supported application locales,
• *When* running translation and contract tests,
• *Then* zero orphaned string warnings occur and all contract tests pass.

**Invariants**: Android SAF local file picking, Dropbox format scanning, pre-import cluster parameter tuning, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-363: Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework [Verified]
- **Tests**: `TST-UI-326`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `SafDirectoryScanner.kt`, `BackupRestoreViewModel.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide recursive directory workout file discovery and bulk import across all format import cards (FIT, TCX, GPX) using Android Storage Access Framework (SAF) `OpenDocumentTree()`, allowing athletes to bulk-import nested workout folder hierarchies with automated duplicate detection and zero sensitive OAuth scope requirements (ATT-3491, complementing `REQ-UI-293` and `REQ-UI-362`):
1. *Composable Format Import Card & Launcher Integration (`ImportBackupTabsScreen.kt`)*:
• In `FormatImportCard`, the system SHALL introduce `folderButtonText: String` and `onFolderClick: () -> Unit` action parameters.
• `FormatImportCard` SHALL render three action buttons per card: Button 1 (Local File Picker via `OpenMultipleDocuments()`), Button 2 (Directory Folder Scan via `OpenDocumentTree()`), and Button 3 (Dropbox Cloud Scan).
• In `ImportTabContent`, the system SHALL accept `onFolderImportClick: (format: String) -> Unit` and wire FIT, TCX, and GPX cards to trigger directory picking for their respective target format (`fit`, `tcx`, `gpx`).
• In `ImportBackupTabsScreen`, the system SHALL register an `OpenDocumentTree()` activity result launcher (`pickDirectoryLauncher`) that captures the selected directory tree URI and target format (`pendingDirectoryUri`, `pendingDirectoryFormat`).
• Upon tree URI selection, `ImportBackupTabsScreen` SHALL display the pre-import cluster parameter tuning bottom sheet (`PreImportTuningBottomSheet`), confirming cluster tolerances before recursive traversal commences.
2. *Recursive Traversal Engine (`SafDirectoryScanner.kt`)*:
• The system SHALL provide `SafDirectoryScanner` in package `com.atrainingtracker.trainingtracker.migration`.
• `SafDirectoryScanner.scanDirectory(context: Context, treeUri: Uri, targetFormat: String, maxDepth: Int = 6, cancelSignal: (() -> Boolean)? = null): List<Uri>` SHALL recursively traverse `DocumentFile.fromTreeUri(context, treeUri)` subdirectories.
• Traversal SHALL enforce max depth bound (depth $\le$ 6), cycle detection via visited URI set tracking, case-insensitive extension filtering (`.$targetFormat`), and non-blocking coroutine cancellation checks.
3. *Batch Ingestion Pipeline & Post-Import Reconciliation (`BackupRestoreViewModel.kt`)*:
• `BackupRestoreViewModel` SHALL provide `fun importWorkoutDirectory(context: Context, treeUri: Uri, format: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): Job`.
• Progress feedback SHALL report folder scanning state (`R.string.import_directory_scanning`), then transition to sequential file ingestion progress (`R.string.import_workout_files_progress` / `R.string.import_fit_progress`).
• If 0 matching files are discovered, an informative notification (`R.string.import_directory_no_files_found`) SHALL be displayed.
• Discovered files SHALL be ingested sequentially via `LegacyImportEngine`, skipping existing duplicates (`isWorkoutExisting`), and triggering post-import database reconciliation (`WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, `WorkoutClusterRepository.refreshClusters()`).
4. *9-Language Localization Parity (`strings.xml`)*:
• String keys `import_fit_folder_button`, `import_tcx_folder_button`, `import_gpx_folder_button`, `import_directory_scanning`, and `import_directory_no_files_found` SHALL maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-363`). Complements `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad (FIT, TCX, GPX)*) and `REQ-UI-362` (*Removal of Google Drive Format Scan Option from Workout Import Cards*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2623` (commit `d5a9d217`), Sprint 2026-41.1; refined under `ATT-3489` (commit `3961ed8f`), Sprint 2026-42.3.
3. *Root Reason for Existing Formulation*: Following the removal of the direct Google Drive REST API scan (constrained by Google's sensitive scope verification policy), athletes required a non-sensitive, platform-native mechanism to scan and bulk-import folders containing historical workout files across cloud providers (Google Drive, Dropbox, Nextcloud) and local directory trees without OAuth scope friction. Android's native `OpenDocumentTree()` fulfills this need with least-privilege security.
4. *Preservation of Core Invariants*: Local multi-document file picking via `OpenMultipleDocuments()`, Dropbox format scanning, pre-import cluster parameter tuning, and post-import database reconciliation remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with a folder hierarchy containing nested subdirectories with `.fit`, `.tcx`, or `.gpx` files,
• *When* selecting the root folder via the SAF directory picker and confirming cluster tuning,
• *Then* the app recursively traverses all subfolders up to max depth 6 and identifies all matching workout files.
• *Given* an athlete selecting a folder backed by Google Drive, Dropbox, or local storage in the SAF directory picker,
• *When* the import commences,
• *Then* the app streams and ingests files through the SAF content resolver without prompting for Google Drive OAuth scopes.
• *Given* a directory containing 10 workouts, 4 of which already exist in SQLite,
• *When* directory import executes,
• *Then* 6 new workouts are imported, 4 duplicates are skipped, and the summary displays "6 importiert, 4 Duplikate übersprungen".
• *Given* an empty folder or a folder with no matching extensions,
• *When* traversal concludes,
• *Then* an informative notification displays that no compatible files were found.
• *Given* all 9 supported application locales,
• *When* verifying string resources,
• *Then* all folder import labels, progress text, and empty messages exist and match format specifiers.

**Invariants**: Android SAF least privilege, depth limit bound 6, cycle detection, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-364: Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards (Rule 36) [Verified]
- **Tests**: `TST-UI-327`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ForkDecisionCard.kt`, `ReturnNavigationState.kt`, `RouteSelectionButton.kt`, `RouteSelectorSheet.kt`, `TurnPromptBanner.kt`, `LapSplitVisualizer.kt`, `LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, `RouteOnMapScreen.kt`, `AltitudeFormatter.java`, `TrainingApplication.java`
- **Rationale**: The system SHALL replace all hardcoded metric template strings ("km", "m") across route selection, active navigation prompts, return navigation HUD, live climb sheets, and lap visualizers with centralized, unit-aware formatters (`DistanceFormatter` and `AltitudeFormatter`), enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing strict Imperial unit parity (ATT-3361, complementing `REQ-UI-015`, `REQ-UI-016`, `REQ-UI-330`, `REQ-MAP-027`, `REQ-MAP-028`, and `REQ-MAP-029`):
1. *Fork Decision Navigation Card (`ForkDecisionCard.kt`)*:
• In `ForkDecisionCard.kt`, branch distance and elevation metrics SHALL be formatted using `LocalMetricFormatter.current.distance.format_with_units(branch.totalDistanceMeters)` and `"+${LocalMetricFormatter.current.altitude.format_with_units(branch.totalElevationMeters)}"`.
• Hardcoded `String.format(Locale.US, "%.1f km", distKm)` and `"+${branch.totalElevationMeters.roundToInt()} m"` templates SHALL be excised.
2. *Return Navigation & Arrival State (`ReturnNavigationState.kt`)*:
• In `ReturnNavigationState.kt`, `formattedRemainingDistance` SHALL return `DistanceFormatter().format_with_units(remainingDistanceMeters)`.
• In `ReturnNavigationState.kt`, `formattedRemainingClimb` SHALL return `"+${AltitudeFormatter().format_with_units(remainingElevationGainMeters)}"`.
• Hardcoded `String.format(Locale.US, "%.1f km", km)` and `"+$climb m"` templates SHALL be excised.
3. *Route Selection Bar & Button (`RouteSelectionButton.kt` & `RouteSelectorSheet.kt`)*:
• In `RouteSelectionButton.kt`, active route metrics subtitle SHALL format distance via `distanceFormatter.format_with_units(activeRoute.summary.distance)` and elevation via `"+${altitudeFormatter.format_with_units(activeRoute.summary.elevationGain)}"`.
• In `RouteSelectorSheet.kt`, `formatDistanceAndElevation` SHALL format distance and elevation via `DistanceFormatter().format_with_units(distanceMeters)` and `"+${AltitudeFormatter().format_with_units(elevationMeters)}"`.
• Hardcoded `String.format(Locale.getDefault(), "%.1f km", distKm)` and `"+%.0f m"` templates SHALL be excised.
4. *Turn-by-Turn Navigation Prompt Banner (`TurnPromptBanner.kt`)*:
• In `TurnPromptBanner.kt`, remaining distance before turn cue execution SHALL be formatted via `distanceFormatter.format_with_units(distanceRemaining)`.
• Hardcoded `"${distanceRemaining} m"` template SHALL be excised.
5. *Lap Split Visualizer (`LapSplitVisualizer.kt`)*:
• In `LapSplitVisualizer.kt`, `formatSplitDistance` SHALL return `DistanceFormatter().format_with_units(distanceM)`.
• Hardcoded `String.format(Locale.US, "%.2f km", ...)` and `String.format(Locale.US, "%.0f m", ...)` templates SHALL be excised.
6. *Live Climb Sheet & Map Screens (`LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, `RouteOnMapScreen.kt`)*:
• In `LiveClimbSheet.kt`, remaining elevation gain, finish states, and climb route counter SHALL be formatted via `altitudeFormatter.format_with_units(...)`.
• In `ClimbOnMapScreen.kt` and `RouteOnMapScreen.kt`, hardcoded fallback strings `"${climb.elevationGainMeters.roundToInt()} m"` and `"${(startDistMeters / 1000.0).roundToInt()} km"` SHALL be excised.
7. *Formatter Robustness & Null Safety (`AltitudeFormatter.java`, `DistanceFormatter.java`, `TrainingApplication.java`)*:
• `AltitudeFormatter.format_with_units` and `TrainingApplication.getUnit()` SHALL safeguard against null `ApplicationContext` and null `SharedPreferences` in pure unit test environments without throwing `NullPointerException`.
8. *Preservation of System Invariants*:
• Pure Compose UI state handling, route navigation geometries, audio alerts, and 100% clean-room test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-364`). Complements `REQ-UI-015` (*Unified Metric component system*), `REQ-UI-016` (*Centralized metric formatting context*), `REQ-UI-330` (*Turn-by-turn Navigation Prompts Overlay / Fork Decision Card*), `REQ-MAP-027` (*ClimbPro Live Climb Bottom Sheet*), `REQ-MAP-028` (*Turn-by-turn navigation banner*), and `REQ-MAP-029` (*Return Navigation and Dynamic Arrival Metrics*).
2. *Historical Origin & Commit Trace*: Early Compose migration (Sprints 2026-40.16 to 2026-41.8) introduced Compose cards where distance and elevation strings were hand-rolled with raw `"km"` and `"m"` templates.
3. *Root Reason for Existing Formulation*: Quick prototyping in metric locales where developers verified metric formatting, inadvertently bypassing `DistanceFormatter` and `AltitudeFormatter` and breaking Imperial unit support.
4. *Preservation of Core Invariants*: Metric and Imperial unit conversion formulas in `DistanceFormatter` and `AltitudeFormatter`, visual styling of cards, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with Imperial units selected in settings,
• *When* viewing `TurnPromptBanner`, `ForkDecisionCard`, `RouteSelectionButton`, or `ReturnNavigationHud`,
• *Then* all distance values are formatted with `ft` or `mile`, and elevation values with `ft`, with zero occurrences of raw `km` or `m`.
• *Given* an athlete with Metric units selected in settings,
• *When* viewing these navigation cards,
• *Then* distance and elevation values are formatted with canonical `m` and `km` with standard threshold precision.
• *Given* `LiveClimbSheet` during an active or finished climb,
• *When* viewing remaining distance and elevation gain,
• *Then* metrics route through `DistanceFormatter` and `AltitudeFormatter` without raw inline unit templates.
• *Given* pure JVM unit tests invoking `DistanceFormatter` and `AltitudeFormatter` without an Android application context,
• *When* formatting distances and altitudes,
• *Then* the formatters return valid formatted strings without throwing `NullPointerException`.

**Invariants**: Pure Compose architecture, Single Source of Truth (Rule 36), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-365: Centralized Speed Unit Conversion, Formatter Harmonization and Progressive Disclosure in Sport Type Editor (Rule 36) [Verified]
- **Tests**: `TST-UI-328`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MyHelper.java`, `TelemetryMetricUtils.kt`, `EditSportTypeDialog.kt`, `SpeedFormatter.java`
- **Rationale**: The system SHALL centralize all speed unit conversions and eliminate manual inline conversion formulas across telemetry graph utilities, sport type dialogs, and speed formatters by routing all conversions strictly through `MyHelper`, and SHALL simplify the sport type configuration dialog by bundling speed thresholds (with auto-detection guidance) and export mappings under an initially collapsed Advanced Options accordion, enforcing Rule 36 (Single Source of Truth) and Design Guidelines Section 3 (*Settings & Accordion Structure*) (ATT-3363, ATT-5821, complementing `REQ-UI-015`, `REQ-UI-016`, and `REQ-UI-364`):
1. *Authoritative Mathematical Conversions in `MyHelper.java`*:
• `MyHelper` SHALL provide symmetrical overloaded methods for bidirectional conversion:
- `mps2userUnit(double speed)` and `mps2userUnit(double speed, @NonNull MyUnits myUnits)`
- `UserUnit2mps(double speed)` and `UserUnit2mps(double speed, @NonNull MyUnits myUnits)`
• The conversion factors SHALL reside strictly within `MyHelper`: METRIC uses factor $3.6$, IMPERIAL uses factor $2.23693629$.
2. *Telemetry Graph & Scrubber Metric Harmonization (`TelemetryMetricUtils.kt`)*:
• In `TelemetryMetricUtils.kt`, `extractMetricValue` for `TelemetryMetricType.SPEED` SHALL delegate conversion to `MyHelper.mps2userUnit(mps, unit)`.
• In `TelemetryMetricUtils.kt`, `formatValue` for `TelemetryMetricType.SPEED` SHALL convert user-domain values to m/s via `MyHelper.UserUnit2mps(value, unit)` prior to passing to `speedFormatter`.
• Inline magic constants `3.6` and `2.236936` in `TelemetryMetricUtils.kt` SHALL be completely excised.
3. *Canonical Numeric Formatting & Locale Resilience in Sport Type Editor (`EditSportTypeDialog.kt`)*:
• In `EditSportTypeDialog.kt`, speed formatting SHALL use standard localized numeric formatting via `String.format(Locale.getDefault(), "%.2f", MyHelper.mps2userUnit(speed))` preserving native decimal formatting without ad-hoc string character replacement (`.replace(",", ".")`).
• In `EditSportTypeDialog.kt`, user input parsing for minimum and maximum speed thresholds SHALL accept both dot and comma decimal delimiters (`it.replace(',', '.').toDoubleOrNull()`), preventing user entry rejection in European/comma-decimal locales.
4. *Formatter DRY Consolidation (`SpeedFormatter.java`)*:
• In `SpeedFormatter.java`, internal unit conversion logic SHALL delegate directly to `MyHelper.mps2userUnit(speed_mps.doubleValue())` instead of duplicating switch-case conversion logic.
5. *Progressive Disclosure & Advanced Options Encapsulation in Sport Type Editor (`EditSportTypeDialog.kt`, `REQ-UI-365.4`)*:
• In `EditSportTypeDialog.kt`, the primary dialog surface SHALL present only everyday configuration fields: Sport Name, Base Sport Type (`bSportType`), Linked Equipment (`MultiSelectEquipmentSpinner`), and Strava Sport Mapping (`stravaName`).
• Technical minimum and maximum speed thresholds (`minSpeed`, `maxSpeed`) SHALL be accompanied by an explanatory hint informing the athlete that these thresholds govern automatic sport type detection during recording (`sport_type_speed_detection_hint`).
• Min/Max speed fields and niche export mappings (TCX Export Name `tcxName`, GoldenCheetah Export Name `gcName`) SHALL be encapsulated inside an expandable accordion section titled `sport_type_advanced_options` ("Erweiterte Optionen") at the bottom of the dialog.
• The accordion section SHALL default to initially collapsed (`isAdvancedExpanded = false` via `rememberSaveable`) to keep the dialog uncluttered and focused on primary settings.
• All new titles, descriptions, and detection hints SHALL maintain 100% localization parity across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-365`). Complements `REQ-UI-015` (*Unified Metric component system*), `REQ-UI-016` (*Centralized metric formatting context*), and `REQ-UI-364` (*Centralized metric distance and altitude formatting enforcement*). Targets `MyHelper.java`, `TelemetryMetricUtils.kt`, `EditSportTypeDialog.kt`, and `SpeedFormatter.java`.
2. *Historical Origin & Commit Trace*: `TelemetryMetricUtils.kt` was authored during Sprint 2026-40.16 (`ATT-2031`) for interactive elevation and telemetry profile scrubbing. `EditSportTypeDialog.kt` was written during Sprint 2026-41.4 (`ATT-2751`), where speed fields and export mappings were added linearly. Refined in Sprint 2026-42.3 (`ATT-3363`) and Sprint 2026-45.12 (`ATT-5821`).
3. *Root Reason for Existing Formulation*: Expedient local development led to magic constants and flat dialog forms without progressive disclosure.
4. *Preservation of Core Invariants*: Exact conversion precision, Imperial and Metric parity, decimal input robustness, and database update persistence strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* speed telemetry points displayed on a workout map scrub graph,
• *When* converting speed values between m/s and user display units in Metric or Imperial modes,
• *Then* conversions execute via `MyHelper.mps2userUnit` and `MyHelper.UserUnit2mps` with zero magic constants in `TelemetryMetricUtils.kt`.
• *Given* the sport type editor dialog (`EditSportTypeDialog`) opened in any language locale,
• *When* viewing the primary dialog surface,
• *Then* only Sport Name, Base Sport Type, Linked Equipment, and Strava Sport Name are visible, while speed fields and TCX/GoldenCheetah mappings are collapsed inside "Erweiterte Optionen".
• *Given* the Advanced Options accordion in `EditSportTypeDialog`,
• *When* expanded,
• *Then* an explanatory text explains that speed bounds are used for automatic sport type detection (`sport_type_speed_detection_hint`), followed by the Min/Max speed row and TCX/GoldenCheetah export dropdowns.
• *Given* user input into minimum and maximum speed thresholds,
• *When* typing with comma or dot decimal separators,
• *Then* speed values parse without error and persist correctly on save.

**Invariants**: Mathematical precision, Single Source of Truth (Rule 36), locale robustness, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-366: Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36) [Verified]
- **Tests**: `TST-UI-329`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveSegmentSheet.kt`, `LiveClimbSheet.kt`, `SensorGridScreen.kt`
- **Rationale**: The system SHALL harmonize live tracking bottom sheet scaffolding and mounting across Strava Live Segments and Live Climbs (ClimbPro), eliminating duplicate container wrappers in `SensorGridScreen.kt`, normalizing file naming to `LiveSegmentSheet.kt`, and establishing a unified sheet container architecture, enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing visual and behavioral consistency (ATT-3364, complementing `REQ-MAP-027`, `REQ-UI-196`, `REQ-UI-270`, `REQ-UI-331`, and `REQ-UI-340`):
1. *Filename Normalization & Import Parity (`LiveSegmentSheet.kt`)*:
• The system SHALL rename `LIveSegmentSheet.kt` to `LiveSegmentSheet.kt` with standard PascalCase.
• All imports, reflection tests, and documentation SHALL resolve to `LiveSegmentSheet` and `LiveSegmentSheetKt`.
2. *Unified Sheet Container in Cockpit (`SensorGridScreen.kt`)*:
• The system SHALL extract a reusable container `LiveTrackingSheetContainer` encapsulating `.fillMaxWidth().wrapContentHeight().sheetContour().background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)` and partially-expanded click-to-expand handling.
• In `SensorGridScreen.kt`, duplicate `Box` blocks for `activeSegment` and `activeLiveClimb` SHALL be consolidated into a single unified sheet slot hosting `LiveTrackingSheetContainer`.
3. *Visual & Structural Parity*:
• Both `LiveSegmentSheet` and `LiveClimbSheet` SHALL render with identical container surface colors (`MaterialTheme.colorScheme.surface`), shapes (`BottomSheetDesign.SheetShape`), contour borders, and canonical 0.5dp horizontal dividers (`color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`).
• Segment visual precedence over climbs (`showLiveSegments && activeSegment != null` over `activeLiveClimb`) SHALL strictly be preserved per `REQ-MAP-027` Section 4.
4. *Preservation of System Invariants*:
• Clamped wrap-content height (`REQ-UI-270`), dynamic peek height (`BottomSheetDesign.PeekHeightLiveSegment`), live rider progress pins on the elevation profile (`REQ-UI-331`), type-safe finish evaluation (`REQ-UI-340`), and 100% full-suite unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-366`). Harmonizes and consolidates scaffolding of `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*), `REQ-UI-196` (*Live Segment Bottom Sheet Design & Surface Background*), `REQ-UI-270` (*Clamped Bottom Sheet Height to Elevation Profile Content Height*), and `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*).
2. *Historical Origin & Commit Trace*: `LIveSegmentSheet.kt` was authored in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`), refined in Sprint 2026-40.16 (`ATT-2231`, `ATT-2967`), and Sprint 2026-41.8 (`ATT-3108`). `LiveClimbSheet.kt` was introduced in Sprint 2026-40.14 (`ATT-1281`, commit `ceaf4070`).
3. *Root Reason for Existing Formulation*: `LiveClimbSheet` was developed independently of `LiveSegmentSheet` to quickly implement ClimbPro without coupling to `MapDetailLayout`. Mounting logic in `SensorGridScreen.kt` was copy-pasted to support the new entity.
4. *Preservation of Core Invariants*: Visual precedence of segments over climbs, elevation profile pin synchronization, peek heights, contour clipping, and 100% test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the repository file tree in `ui/segments`,
• *When* inspecting the live segment sheet file,
• *Then* the file is named `LiveSegmentSheet.kt` with standard PascalCase.
• *Given* `SensorGridScreen.kt` rendering bottom sheet content,
• *When* evaluating the active sheet slot for segments or climbs,
• *Then* both entities mount via a single shared `LiveTrackingSheetContainer` composable, eliminating duplicate `Box` modifier chains and expansion lambdas.
• *Given* active segment and active climb bottom sheets in the cockpit,
• *When* comparing sheet appearance,
• *Then* both sheets utilize identical container backgrounds, shapes, contour borders, and 0.5dp dividers.
• *Given* all layout and contract tests in `LiveSegmentSheetContractTest.kt`, `LiveSegmentSheetLayoutTest.kt`, and `LiveClimbSheetLayoutTest.kt`,
• *When* executed on pure JVM,
• *Then* 100% of tests pass, verifying both the renamed file and the unified container.

**Invariants**: Segment precedence over climbs, elevation profile pin alignment, wrap-content height, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-UI-367: First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration [Verified]
- **Tests**: `TST-UI-330`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• `TrackingTabsScreen` SHALL gate invoking `trackingTabsViewModel.markCoachmarkRenderedInSession()` on `showStopTrackingCoachmark && pagerState.settledPage > 0 && !pagerState.isScrollInProgress`, ensuring `hasRenderedCoachmarkInSession` is never armed during scroll animations away from `Page 0`.
• `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(...)` SHALL accept `(trackingMode, currentPage, settledPage, isScrollInProgress, hasSeenHint, hasRenderedCoachmarkInSession, isDismissPending)` and return `true` iff `hasSeenHint == false && !isDismissPending && hasRenderedCoachmarkInSession && (trackingMode == TrackingMode.TRACKING
- **Rationale**: The system SHALL display a non-intrusive, high-contrast Material 3 onboarding coachmark banner on the cockpit tracking screen during the athlete's global first active workout session (`TrackingMode.TRACKING` or `TrackingMode.PAUSED`) to guide discovering the `Control` tab (`Tab 0`) via horizontal swipe or tab bar tap, and SHALL permanently dismiss the hint upon acknowledgment or navigation to `Tab 0` after the banner has been rendered on a settled cockpit page while resetting it on factory defaults restoration (ATT-3387, hardened in ATT-4456, complementing `REQ-UI-027`, `REQ-UI-216`, `REQ-UI-325`, and `REQ-SET-073`):
1. *Decoupled DataStore Persistence & Factory Reset (`TuningPreferencesDataStore.kt`)*:
• `TuningPreferencesDefaults` SHALL define `const val DEFAULT_HAS_SEEN_STOP_TRACKING_HINT = false`.
• `TuningPreferencesDataStore` SHALL define `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` included in `ALL_KEYS`, `val hasSeenStopTrackingHintFlow: Flow<Boolean>`, and `suspend fun setHasSeenStopTrackingHint(seen: Boolean)`.
• Invoking `tuningDataStore.resetToDefaults()` in Advanced Settings SHALL automatically remove `KEY_HAS_SEEN_STOP_TRACKING_HINT` and restore the hint to `false`.
2. *ViewModel Session State & Configuration-Change Survival (`TrackingTabsViewModel.kt`)*:
• `TrackingTabsViewModel` SHALL maintain `val hasRenderedCoachmarkInSession: StateFlow<Boolean>` (`markCoachmarkRenderedInSession()`, `resetCoachmarkSessionState()`), surviving configuration changes and resetting to `false` when `trackingMode` transitions to `TrackingMode.READY`.
3. *Settled Gating & Race-Free Auto-Acknowledgement (`TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`)*:
• `TrackingTabsScreen` SHALL collect `hasSeenStopTrackingHintFlow` (`initial = true` to prevent flash prior to DataStore load) and `hasRenderedCoachmarkInSession`.
• `TrackingTabsScreen` SHALL evaluate `shouldShowStopTrackingCoachmark(trackingMode, currentPage, screenMode, hasSeenHint, isDismissPending)`, returning `true` iff `hasSeenHint == false && !isDismissPending && (trackingMode == TrackingMode.TRACKING

### REQ-UI-368: Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation [Verified]
- **Tests**: `TST-UI-331`, `TST-UI-392`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• Straight-line displacement `LINE_DISTANCE_m` SHALL be recalculated row-by-row from `newStartLatLng` via Haversine distance (or anchored to `0.0` for GPS-less indoor activities).
• Verify post-op checksum: `remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount && remainingRows >= 2`.
5. *Deterministic Lap Metadata Mapping (`LapsDatabaseManager`)*:
• Laps outside the retained window are pruned. Each retained lap $k \in \{1 \dots K\}$ strictly preserves the exact user-authored `NAME` and `DESCRIPTION` from its original parent lap without truncation or heuristic merging.
• Boundary laps $1$ and $K$ have their start time, total time, total distance, and average speed recalculated strictly from their retained samples.
• `LAP_NR` is re-indexed sequentially `1..K` in both `Laps.TABLE` and `WorkoutSamples` in lockstep.
6. *Sanitized Non-Linear Extrema & Sparse Telemetry Handling*:
• Telemetry aggregation (`CropWorkoutMath`) SHALL filter invalid or non-finite values (`Double.isFinite(v)`).
• Non-linear sensor extrema (`HR`, `POWER`, `CADENCE`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`) in `ExtremumValues` SHALL be strictly re-derived (`MIN`, `MAX`, `AVG`, `START`, `END`) from retained samples.
• If a sensor stream contains zero valid readings in the cropped window, its extrema fields SHALL be set to `0.0`, signaling `hasSensorData = false` to UI consumers.
7. *Synchronous Export Invalidation, Cloud Sync & Failure Recovery*:
• Active export tasks for `fileBaseName` SHALL be cancelled and cached `.tcx`, `.fit`, `.gpx`, `.csv`, `.json` files deleted synchronously pre-commit.
• Cloud sync status in `SyncStatusDatabaseManager` / `ExportStatusDatabaseManager` SHALL be marked `PENDING_UPDATE` / `WAITING`.
• Fresh export jobs SHALL be enqueued post-commit via `WorkManager.enqueueUniqueWork("export_" + fileBaseName, ExistingWorkPolicy.REPLACE, exportRequest)`.
• If `WorkManager` enqueue fails, `ExportStatus` SHALL be set to `ERROR`, and a "Force Re-Export" button SHALL be displayed in the UI.
• `WorkoutRepository` SHALL invalidate its in-memory cache and re-fetch `WorkoutData` directly from SQLite post-commit under `cropMutex`.
8. *9-Language Localization Parity*:
• All UI strings, button labels, and dialog messages SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-368`). Targets `CropWorkoutScreen.kt`, `CropWorkoutMath.kt`, `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `ExportManager.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3274`, Sprint `2026-42.4`, Epic `ATT-3267`.
3. *Root Reason for Existing Formulation*: Athletes occasionally record unwanted leading GPS clusters or trailing car rides; currently no in-app trimming tool exists, causing corrupted summary metrics and exports.
4. *Preservation of Core Invariants*: Rollback atomicity, irreversible destructive confirmation dialog, zero-anchoring with `EPSILON = 1e-6`, lap metadata preservation, synchronous export invalidation, and 9-language translation parity MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a finished workout with $\ge 2$ samples opened in `CropWorkoutScreen`,
• *When* the athlete adjusts the start or end handles on the `RangeSlider`,
• *Then* the map polyline highlights the retained range while dimming trimmed segments, the elevation profile updates, and live preview values for trimmed offsets, new distance, and new duration update continuously.
• *Given* a selection where duration $< 1.0\text{ s}$ or fewer than 2 points,
• *When* rendered in `CropWorkoutScreen`,
• *Then* the "Crop" action button is visually disabled and cannot be tapped.
• *Given* a valid crop range with "Crop" tapped,
• *When* the confirmation dialog appears,
• *Then* it warns that sample deletion is permanent and irreversible.
• *Given* confirmation granted,
• *When* the crop transaction executes,
• *Then* leading/trailing samples are pruned, cumulative metrics are zero-anchored at `startRowId` with `EPSILON = 1e-6` clamping, `LINE_DISTANCE_m` is recalculated row-by-row, laps are re-indexed with user notes preserved, summary extrema are strictly re-derived, and cached export files are replaced.
• *Given* an ongoing crop transaction,
• *When* the user presses the system back button,
• *Then* navigation is blocked by `BackHandler(enabled = isCroppingInProgress)` until the transaction completes.

**Invariants**: SQLite rollback journal safety, post-op checksum assert, `isCroppingInProgress` UI lock, universal `EPSILON = 1e-6` clamping, and 100% clean-room test pass rate MUST NOT be broken.
- **Implementation**: v'
- **Rationale**: The system SHALL provide an interactive workout cropping subview (`CropWorkoutScreen.kt`) with synchronized map polyline and elevation profile range scrubbing, allowing athletes to permanently trim unwanted leading and trailing samples from a recorded workout, and SHALL atomically prune SQLite samples, re-index laps while preserving user-authored notes, re-derive all summary aggregates and sensor extrema with universal floating-point clamping, and synchronously invalidate and replace export artifacts (ATT-3274, complementing `REQ-UI-015`, `REQ-UI-016`, `REQ-TRK-001`, and `REQ-TRK-002`):
1. *Interactive Dual-Range Scrubber & Synchronized Previews (`CropWorkoutScreen.kt`)*:
• Accessible via an action button ("Track zuschneiden" / "Crop workout") in `EditWorkoutScreen.kt` (`EditWorkoutDialog`) for completed activities with $\ge 2$ samples.
• Dual-handle `RangeSlider` with minimum handle separation (`endIndex - startIndex >= minIndexStep`, corresponding to $\ge 1\text{ s}$).
• Track Map preview (`PathPreviewMap.kt`): The retained segment (`[startIndex..endIndex]`) SHALL render as a solid primary polyline; trimmed leading and trailing segments SHALL render as dimmed, dashed polylines (`Dash(15f), Gap(15f)`).
• Elevation Profile preview (`ElevationProfile.kt`): Renders with adaptive canvas height (`calculateElevationProfileHeight(range)`), highlighting the retained interval.
• Live Metric Previews: Dynamic live preview of trimmed start offset, trimmed end offset, new total distance (`DistanceFormatter`), and new total duration (`DateUtils.formatElapsedTime`).
• RTL & Font Scaling: All layout arrangements use RTL-safe start/end primitives and support up to `2.0x` font scaling.
2. *Hard Boundary Pre-Condition Guard & Destructive Confirmation (`Coding Standards Sec 1.4`)*:
• The "Crop" action button SHALL be disabled unless `(endIndex - startIndex >= minIndexStep) && (croppedDurationSec >= 1.0) && (endTimestampMs - startTimestampMs >= 1000L) && !isCroppingInProgress`.
• Tapping "Crop" SHALL display an explicit `AlertDialog` warning that samples outside the selected window will be permanently deleted, the operation is irreversible, and reminding athletes they can use *Save as Route* beforehand to preserve the untrimmed path.
3. *Pre-Flight Storage Verification & Ephemeral Recovery Backup*:
• `WorkoutRepository` SHALL verify internal storage availability via `StatFs` ($\ge 10\text{ MB}$).
• `WorkoutRepository` SHALL write an ephemeral versioned JSON snapshot to `context.cacheDir/workout_crop_backup_<workoutId>_<timestamp>.json` inside a `try ... finally` block, purging it upon successful transaction completion.
4. *Native SQLite Rollback Transactions & Universal Cumulative Clamping*:
• Prune samples outside `[startRowId, endRowId]` in `WorkoutSamplesDatabaseManager`.
• Cumulative monotonic columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) SHALL be shifted by subtracting baseline values at `startRowId`. The new start row (`_id == startRowId`) is zero-anchored (`0.0` / `0`), and for subsequent rows, shifted values with $

### REQ-UI-369: [REVERTED - Architectural Fence] Prohibition of Static BottomSheetScaffold Bottom Inset Padding on Cockpit ElevationProfile Surface [Verified]
- **Tests**: `TST-UI-332`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`
- **Rationale**: The system SHALL NOT apply `bottom = paddingValues.calculateBottomPadding()` from Material 3 `BottomSheetScaffold` to the `ElevationProfile` `Surface` in `SensorGridScreen.kt` (ATT-3311 Post-Review Revert of `f3edad51`, preserving `REQ-UI-346`, `REQ-UI-355`, and `REQ-UI-358`):
1. *Root Cause of Post-Review Revert (Documented Deviation)*:
• In `SensorGridScreen.kt`, `sheetPeekHeight` evaluates to `BottomSheetDesign.PeekHeightLiveSegment + navBarHeight` ($\approx 174\text{dp}$) whenever `shouldShowBottomSheet == true`, and `rememberStandardBottomSheetState` is configured with `skipHiddenState = false`.
• Material 3 `BottomSheetScaffold` passes `PaddingValues(bottom = sheetPeekHeight)` statically to its body content **even when the user swipes the Live Segment / Live Climb sheet down into `SheetValue.Hidden`**. Applying `bottom = paddingValues.calculateBottomPadding()` to the `ElevationProfile` `Surface` therefore creates a permanent $\approx 174\text{dp}$ blank whitespace below the `ElevationProfile` whenever a segment/climb sheet is hidden.
• Furthermore, the original screenshot symptom in `ATT-3311` (`screencap_elevation_missing.png` from Sprint `2026-41.10`, recorded at `0:00:00 s` / `0,00 km` on a navigated route) had already been resolved by `ATT-3199` (`REQ-UI-355`, route elevation profile fallback when recorded points $< 2$) and `ATT-3343` (`REQ-UI-358`, asymmetric map `weight(1f, fill = true)`).
2. *Enforced Layout Invariant*:
• The `ElevationProfile` `Surface` in `SensorGridScreen.kt` SHALL declare `.fillMaxWidth().padding(top = 8.dp)` with zero static bottom scaffold inset padding.

### REQ-UI-370: Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model [Verified]
- **Tests**: `TST-UI-333`, `TST-UI-391`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SettingsDataStore.kt`, `ZonesSettingsActivity.kt`, `TelemetryZoneMath.kt`, `ZoneDistributionCalculator.kt`, `TrackingViewModel.kt`, `Color.kt`
- **Rationale**: The system SHALL decouple the training zone architecture by retaining the canonical 5-zone model for Heart Rate (`HR_RUN`, `HR_BIKE`) while expanding Cycling Power (`PWR_BIKE`) to Dr. Andy Coggan's 7-zone framework across settings, real-time HUD cockpit tiles, and post-workout aftermath analytics (ATT-3184, amending `REQ-UI-203` and complementing `REQ-UI-202` and `REQ-UI-231`):
1. *Color Token System & Theme Parity (`Color.kt`, `color.xml`)*:
• `TTColor.Zone6 = Color(0xFF4E342E)` (Dark Chestnut Brown) and `TTColor.Zone7 = Color(0xFF000000)` (Black).
• In `res/values/color.xml`: `<color name="zone_6">#4E342E</color>`, `<color name="zone_7">#000000</color>`.
• In dark / AMOLED theme, Zone 7 retains Black fill with high-contrast border outline.
2. *DataStore Storage & Dynamic Proportional Migration (`SettingsDataStore.kt`)*:
• `ZoneType` exposes `zoneCount` (5 for HR, 7 for Power) and `boundaryCount` (4 for HR, 6 for Power).
• `Zone` enum defines `ZONE_1(1)`, `ZONE_2(2)`, `ZONE_3(3)`, `ZONE_4(4)`, `ZONE_5(5)`, `ZONE_6(6)`.
• Add preference keys `pwr_bike_zone5_max` and `pwr_bike_zone6_max`.
• For existing user profiles with missing Z5/Z6 keys, the system SHALL calculate proportional defaults based on existing `PWR_BIKE_ZONE4_MAX`: $\text{Estimated FTP} = \max(50, \text{round}(\text{z4Max} / 1.05))$, $\text{Default z5Max} = \max(\text{z4Max} + 10, \text{round}(\text{Estimated FTP} \times 1.20))$, $\text{Default z6Max} = \max(\text{z5Max} + 10, \text{round}(\text{Estimated FTP} \times 1.50))$. Default fallback if z4Max missing: Z1=132, Z2=180, Z3=216, Z4=252, Z5=288, Z6=360 (updated to calculator-aligned baseline in ATT-4521 / REQ-UI-426).
• Migration check and preference initialization SHALL execute inside a single atomic `dataStore.edit` transaction.
• Strict monotonic ascending validation: $0 < z1 < z2 < z3 < z4 < z5 < z6$.
3. *Synchronous Java Helper (`SettingsDataStoreJavaHelper.kt`)*:
• `getZoneMax(context, zoneType, zoneIndex)` SHALL support `zoneIndex == 5` and `zoneIndex == 6`.
4. *Domain Models & Telemetry Mathematics (`ZoneDistributionModels.kt`, `TelemetryZoneMath.kt`, `BatterySaverModel.kt`)*:
• `PowerZoneThresholds(z1Max, z2Max, z3Max, z4Max, z5Max, z6Max)`.
• `TelemetryZoneMath.calculatePowerZoneBands()` SHALL compute 7 bands with colors `Zone1..Zone7` and labels `Z1..Z7`.
• `TelemetryZoneMath.determinePowerZone()` SHALL map instantaneous power to 1-indexed zones `1..7`.
• `TelemetryZoneMath.loadPowerThresholds()` SHALL load all 6 boundaries and validate $0 < z1 < z2 < z3 < z4 < z5 < z6$.
• `BatterySaverModel.calculateZoneIndex()` SHALL map power values to zones `1..7`.
5. *Real-Time Tracking Cockpit HUD (`TrackingViewModel.kt`)*:
• `zoneColors` list expanded to 7 colors (`Zone1..Zone7`).
• `calculateZoneColor()` maps power values across all 7 zones for background, left/right bar, and text color modes.
6. *Settings UI (`ZonesSettingsActivity.kt`)*:
• Dynamically renders 5 boundary cards for HR and 7 boundary cards for Power.
• Linked min/max inputs preserve $z_n \ge z_{n-1} + 1$.
• Live preview renders 7 selector chips (`Z1..Z7`) for Power and 5 chips (`Z1..Z5`) for HR.
7. *Post-Workout Aftermath Analytics & Histograms (`ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`)*:
• `calculatePowerDistribution()` computes duration and percentages across 7 zones (`Zone1..Zone7`).
• `calculatePowerHistogram()` maps frequency bins across 7 zones with matching colors.
• `ZoneDistributionColumnChart.kt` dynamically adapts to 7 equal-width columns.
• Computed dynamically on-the-fly from raw samples in `WorkoutSamples` (zero SQLite schema mutations required).
8. *9-Language Localization Parity*:
• Define `zone_6_label` and `zone_7_label` across all 9 application locales with programmatic `Z$zoneIndex` fallback.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*, Sprint 2026-40.5, ATT-1390). Targets `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `ZonesSettingsActivity.kt`, `ZoneDistributionModels.kt`, `TelemetryZoneMath.kt`, `TrackingViewModel.kt`, `ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`, `Color.kt`, and `res/values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced cycling power 5-zone distribution in Aftermath, designed to match `REQ-UI-202` (Heart Rate 5-Zone Distribution).
3. *Root Reason for Existing Formulation*: Power was originally configured with 5 zones solely to match Heart Rate (`REQ-UI-202`) and minimize architectural divergence across domain models and settings UI.
4. *Preservation of Core Invariants*: Heart Rate (`HR_RUN`, `HR_BIKE`) 5-zone scheme remains 100% intact; dynamic proportional migration from stored Z4 Max prevents arbitrary defaults; zero schema mutations in `WorkoutSummaries` / `WorkoutSamples`; 100% raw export format compatibility; 9-language localization parity strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opens `ZonesSettingsActivity`,
• *When* selecting Heart Rate (Run or Bike),
• *Then* exactly 5 zone cards and 5 preview chips (`Z1..Z5`) are rendered.
• *When* selecting Cycling Power,
• *Then* exactly 7 zone cards and 7 preview chips (`Z1..Z7`) are rendered with linked min/max editing.
• *Given* an existing user database where `PWR_BIKE_ZONE4_MAX = 210` (corresponding to a 200W FTP athlete) and Z5/Z6 are unconfigured,
• *When* `SettingsDataStore` loads power thresholds,
• *Then* `PWR_BIKE_ZONE5_MAX` resolves to $240\text{W}$ ($120\%$ FTP) and `PWR_BIKE_ZONE6_MAX` resolves to $300\text{W}$ ($150\%$ FTP) within an atomic transaction.
• *Given* an active workout with a power meter connected,
• *When* the athlete produces power in Zone 6 ($Z5_{\max} < P \le Z6_{\max}$),
• *Then* the cockpit power tile resolves `TTColor.Zone6` (Dark Chestnut Brown).
• *When* the athlete produces power in Zone 7 ($P > Z6_{\max}$),
• *Then* the cockpit power tile resolves `TTColor.Zone7` (Black).
• *Given* a completed workout containing power meter telemetry,
• *When* viewing the workout summary in Aftermath,
• *Then* the Power Zone Distribution card renders 7 vertical columns with accurate time-in-zone durations and percentages.
• *Given* all existing unit tests in `HeartRateZoneLocalizationTest`, `PowerZoneDistributionCalculatorTest`, `TelemetryZoneMathTest`, and `SensorFieldZoneDisplayTest`,
• *When* running `./gradlew testDebugUnitTest`,
• *Then* 100% of tests pass without regressions.

**Invariants**: Heart Rate 5-zone scheme, SQLite single-thread confinement, atomic DataStore transactions, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-371: Elevation Profile 7-Zone Gradient Shading and Harmonized Legend [Verified]
- **Tests**: `TST-UI-334`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`
- **Rationale**: The system SHALL harmonize elevation profile slope gradient shading and the interactive `GradeLegend` in `ElevationProfile.kt` with the application's unified 7-zone design system (`TTColor.Zone1..Zone7`) established in `REQ-UI-370` (ATT-3391, complementing `REQ-UI-352` and `REQ-UI-370`):
1. *7-Zone Slope Gradient Scale & Exact Half-Open Intervals (`REQ-UI-371.1`)*:
• Terrain incline SHALL be categorized into 7 discrete slope gradient intervals following strict half-open interval logic $[x_{n-1}, x_n)$ mapped deterministically to `TTColor`:
- Zone 1 (`TTColor.Zone1` Chartreuse `#7FFF00`): $\text{Grade} \in [-\infty, 2.0\%)$ (Flat, rolling terrain, descents, and non-finite values).
- Zone 2 (`TTColor.Zone2` Green `#00E676`): $2.0\% \le \text{Grade} < 5.0\%$ (Gentle incline).
- Zone 3 (`TTColor.Zone3` Orange `#FFA500`): $5.0\% \le \text{Grade} < 10.0\%$ (Moderate climb).
- Zone 4 (`TTColor.Zone4` Red `#FF0000`): $10.0\% \le \text{Grade} < 15.0\%$ (Steep climb).
- Zone 5 (`TTColor.Zone5` Dark Violet `#8A2BE2`): $15.0\% \le \text{Grade} < 20.0\%$ (Very steep climb).
- Zone 6 (`TTColor.Zone6` Dark Chestnut Brown `#4E342E`): $20.0\% \le \text{Grade} < 25.0\%$ (Extreme ramp; $20.0\%$ inclusive, $25.0\%$ exclusive).
- Zone 7 (`TTColor.Zone7` Black `#000000`): $\text{Grade} \ge 25.0\%$ (Monster grade / Wall; $25.0\%$ inclusive).
2. *Deterministic Top-Level Utility Function & Non-Finite Safeguards (`REQ-UI-371.2`)*:
• `ElevationProfile.kt` SHALL expose a public top-level utility function `getElevationGradeColor(gradePercent: Double): Color` executing monotonic half-open interval evaluation $[x_{n-1}, x_n)$ for segment color resolution when `climbGradientShading == false` and no custom `segmentColorProvider` is supplied.
• If `gradePercent.isNaN()` or `!gradePercent.isFinite()`, the function SHALL safely return `TTColor.Zone1`, strictly preventing UI crashes or undefined rendering on GPS telemetry drops.
3. *Explicit Descent & Negative Slope Handling (`REQ-UI-371.3`)*:
• All downhill slopes and descents ($\text{Grade} < 0.0\%$) and flat terrain ($0.0\% \le \text{Grade} < 2.0\%$) SHALL resolve to `TTColor.Zone1`. Negative gradients represent gravitational assistance with zero upward climbing resistance and are intentionally assigned the baseline recovery zone color.
4. *GradeLegend Harmonization (`REQ-UI-371.4`)*:
• The interactive `GradeLegend` popup in `ElevationProfile.kt` SHALL display exactly 7 legend items in sequence: `< 2%` (`TTColor.Zone1`), `2 - 5%` (`TTColor.Zone2`), `5 - 10%` (`TTColor.Zone3`), `10 - 15%` (`TTColor.Zone4`), `15 - 20%` (`TTColor.Zone5`), `20 - 25%` (`TTColor.Zone6`), `> 25%` (`TTColor.Zone7`). Legend width constraint SHALL be configured to `widthIn(max = 300.dp)` to prevent text truncation on wrapped multi-row layouts.
5. *Accessibility & Contrast Compliance (`REQ-UI-371.5`)*:
• Swatch labels in `GradeLegendItem` SHALL be rendered adjacent to swatches using `MaterialTheme.colorScheme.onSurface` on `MaterialTheme.colorScheme.surface`, providing a contrast ratio $> 10:1$ in both light and dark themes (complying with WCAG 2.1 AA $\ge 4.5:1$).
• For `TTColor.Zone6` (`#4E342E`) and `TTColor.Zone7` (`#000000`), `GradeLegendItem` SHALL render a border outline `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` around the swatch to maintain visible boundaries against dark surfaces.
6. *Historical Data Strategy & System Invariants (`REQ-UI-371.6`)*:
• Elevation profiles are rendered on-the-fly from raw GPS sample coordinates in `WorkoutSamples`. Re-rendering historical workouts with the unified 7-zone scale is an intentional visual enhancement providing immediate clarity without database migrations.
• `getClimbGradeColor` in `LiveClimbSheet.kt` (`REQ-UI-352`) and its specific 6-band cycling climb classification ($<3\%, 3-6\%, 6-9\%, 9-12\%, 12-20\%, \ge 20\%$) SHALL remain unaltered, preserving full backward compatibility with `ElevationProfileClimbShadingTest.kt`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-352` (*Real-Time Live Climb Sheet & Slope Shading*, Sprint 2026-42.1, ATT-3343) and complements `REQ-UI-370` (*7-Zone Coggan Cycling Power Model & Palette*, Sprint 2026-42.4, ATT-3184). Targets `ElevationProfile.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.1 (`ATT-3343`, commit `fca58372`) introduced climb slope shading with 6 bands, while default elevation profile slope shading in `ElevationProfile.kt` was restricted to 6 bands due to legacy 5-zone palette limitations.
3. *Root Reason for Existing Formulation*: Prior to ATT-3184, only 5 training zone colors and generic `Color.Black` existed in the design system, forcing all slopes $\ge 20\%$ to be lumped into Black without distinction for extreme ramps.
4. *Preservation of Core Invariants*: Live climb gradient shading (`REQ-UI-352`), zero bitmap caching, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation segment with grade $22.4\%$,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone6` (Dark Chestnut Brown `#4E342E`) is resolved.
• *Given* an elevation segment with grade $26.8\%$,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone7` (Black `#000000`) is resolved.
• *Given* a descent segment with grade $-8.5\%$,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone1` (Chartreuse `#7FFF00`) is resolved.
• *Given* exact boundary values `2.0%`, `5.0%`, `10.0%`, `15.0%`, `20.0%`, and `25.0%`,
• *When* evaluating `getElevationGradeColor`,
• *Then* each boundary maps deterministically to the higher interval ($[x_{n-1}, x_n)$).
• *Given* input values `Double.NaN` or `Double.POSITIVE_INFINITY`,
• *When* evaluating `getElevationGradeColor`,
• *Then* `TTColor.Zone1` is safely returned.
• *Given* the `GradeLegend` popup opened on a dark theme,
• *When* rendered in `ElevationProfile`,
• *Then* all 7 items are displayed with swatches and non-truncated labels, and Zone 6 / Zone 7 swatches have outline borders.

**Invariants**: Live climb shading (`REQ-UI-352`), zero disk/DB cache regressions, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-372: Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching in Workout Summary Cards [Verified]
- **Tests**: `TST-UI-335`, `TST-UI-382`, `TST-UI-383`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionStackedBar.kt`, `ZoneDistributionModels.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `MyPreferenceManager.kt`
- **Rationale**: The system SHALL provide a continuous horizontal stacked distribution bar as an alternative display mode alongside the vertical column chart and fine-grained telemetry histogram in workout aftermath zone cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`) (ATT-3390, amending `REQ-UI-202` and `REQ-UI-203`, and complementing `REQ-UI-231`, `REQ-UI-251`, and `REQ-UI-370`):
1. *Continuous Horizontal Stacked Distribution Bar (`REQ-UI-372.1`)*:
• `ZoneDistributionStackedBar.kt` SHALL expose a composable function `ZoneDistributionStackedBar(distribution: ZoneDistributionData, modifier: Modifier = Modifier, barHeight: Dp = 16.dp, showLegend: Boolean = true)` rendering active workout duration normalized to 100%.
• Active zone entries ($d_i > 0$ and $p_i > 0.0$) SHALL be laid out horizontally in contiguous order (Zone 1 to Zone 5 for Heart Rate; Zone 1 to Zone 7 for Power).
• Each active segment's width SHALL be weighted in Compose `Row` using `Modifier.weight(entry.percentage)` directly, guaranteeing that segments sum to exactly 100% of the bar's inner width without layout overflow or rounding clipping.
• Outer bar container SHALL be clipped with `RoundedCornerShape(6.dp)`, enclosed with a uniform contour border `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`, and filled with background `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`.
• Active segments SHALL be separated by uniform vertical dividers (`1.dp` width, `color = MaterialTheme.colorScheme.surface`).
• If total active time is 0 or all zone entries have 0 duration, the component SHALL render an empty neutral container track without throwing layout or weighting exceptions.
• Zero-duration zones ($d_i = 0$) SHALL be gracefully omitted from the horizontal bar.
2. *Contrast, Border & Theme Uniformity (`REQ-UI-372.2`)*:
• To eliminate visual jitter and ensure consistent aesthetic rhythm, a uniform border outline `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` SHALL be applied around the entire outer stacked bar container and around all circular legend badge dots (`7.dp`).
• This guarantees compliant contrast ($\ge 3:1$ Non-Text Contrast per WCAG 2.1 AA 1.4.11) across all theme environments: dark swatches (`TTColor.Zone6`, `TTColor.Zone7`) remain distinctly bounded against dark surfaces, and light swatches (`TTColor.Zone1`) remain distinctly bounded against light surfaces.
3. *Compact Integrated Legend & Responsive Wrapping (`REQ-UI-372.3`)*:
• Below the horizontal bar, a responsive legend SHALL render all configured zones of the metric model (Z1..Z5 for Heart Rate, Z1..Z7 for Cycling Power) to provide full metabolic context.
• Legend items SHALL be arranged in a responsive wrapping layout (`FlowRow` or adaptive wrapping row with `horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)`), ensuring that items wrap gracefully across multiple lines on compact or narrow mobile displays without horizontal clipping or text truncation.
• Each legend item SHALL render: circular color badge (`7.dp`) with matching zone fill and uniform outline border, zone badge text (`Z1`..`Z5` or `Z1`..`Z7`) in `labelSmall` bold, localized zone name, formatted duration (`ZoneDistributionChartMath.formatZoneDuration(entry.durationSec)`), and percentage readout (`String.format(Locale.getDefault(), "%.0f%%", entry.percentage)`).
• Inactive zones ($d_i = 0$) SHALL be visually dimmed (`alpha = 0.45f`, displaying `0:00 (0%)`) to preserve complete metabolic context without implying active bar representation.
4. *Expanded Display Modes & Enum Parity (`REQ-UI-372.4`)*:
• `enum class ZoneCardDisplayMode` in `ZoneDistributionModels.kt` SHALL define: `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and backward-compatible legacy alias `FIVE_ZONES` for `COLUMNS` to satisfy existing contract tests.
5. *Card Mode Switching & Segmented Buttons (`REQ-UI-372.5`)*:
• In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`:
- When `distribution.histogram != null`: The segmented button row SHALL render 3 buttons: `STACKED_BAR` (R.string.zone_mode_stacked_bar), `COLUMNS` (R.string.zone_mode_columns), and `HISTOGRAM` (R.string.zone_mode_histogram).
- When `distribution.histogram == null`: The segmented button row SHALL render 2 buttons: `STACKED_BAR` and `COLUMNS`, enabling athletes to switch between the stacked bar and vertical columns even on activities without raw sample histograms.
- When `effectiveDisplayMode` is `STACKED_BAR`, the card body renders `ZoneDistributionStackedBar`.
- When `effectiveDisplayMode` is `COLUMNS` or `FIVE_ZONES`, the card body renders `ZoneDistributionColumnChart`.
- When `effectiveDisplayMode` is `HISTOGRAM`, the card body renders `TelemetryHistogramChart`.
6. *Preference Persistence (`REQ-UI-372.6`)*:
• `WorkoutCardSectionPreferences` in `MyPreferenceManager.kt` SHALL include `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS`.
• Athlete's selected mode SHALL be persisted across application restarts via DataStore key `workout_card_zone_display_mode`.
7. *9-Language Localization Parity (`REQ-UI-372.7`)*:
• Add `zone_mode_stacked_bar` and `zone_mode_columns` across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
8. *Default Zone Card Display Mode Selector in Advanced Settings & Workout Summary Wiring (`ATT-3676`)*:
• The system SHALL expose a dedicated `SingleChoiceSegmentedButtonRow` selector for `ZoneCardDisplayMode` in `WorkoutMasksAndCardsSection.kt` under Advanced Settings, displaying options `STACKED_BAR` (`R.string.zone_mode_stacked_bar`), `COLUMNS` (`R.string.zone_mode_columns`), and `HISTOGRAM` (`R.string.zone_mode_histogram`).
• The selector SHALL be displayed conditionally when either `workoutCardPrefs.showZoneAnalysis == true` or `workoutDetailPrefs.showZoneAnalysis == true`.
• The selector SHALL display localized title `settings_zone_card_display_mode_title` across all 9 application locales.
• State Synchronization & Precedence: Tier 1 (Interactive Manual Override) takes precedence for an individual card during an active session; Tier 2 (Reactive Global Default) reflects `preferences.zoneCardDisplayMode` when no manual override exists; Tier 3 (Domain Fallback Guard) gracefully resolves to `COLUMNS` if `HISTOGRAM` is set on activities where `distribution.histogram == null`.
• Modularity limit of $\le 400$ lines in `WorkoutMasksAndCardsSection.kt` (REQ-UI-262), `FIVE_ZONES` legacy compatibility alias, and 100% clean-room test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution*, Sprint 2026-40.5, ATT-1390) and `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution*, Sprint 2026-40.5, ATT-1390). Complements `REQ-UI-231`, `REQ-UI-251`, `REQ-UI-262`, and `REQ-UI-370`. Targets `ZoneDistributionStackedBar.kt`, `ZoneDistributionModels.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `MyPreferenceManager.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced vertical columns; Sprint 2026-40.8 (`ATT-1845`, commit `7b14d9a2`) introduced `ZoneCardDisplayMode` with `FIVE_ZONES` and `HISTOGRAM`; Sprint 2026-41.2 (`ATT-2147`, commit `c8192a0e`) introduced `TelemetryMetricGraphMarginalBarsContractTest.kt` verifying `ZoneCardDisplayMode.FIVE_ZONES`; Sprint 2026-42.4 (`ATT-3184`) expanded Power to 7 zones; Sprint 2026-42.4 (`ATT-3390`, commit `38706240`) added stacked bar and DataStore preference key; Sprint 2026-42.5 (`ATT-3676`) exposes default selector in Advanced Settings and wires to workout summaries.
3. *Root Reason for Existing Formulation*: In ATT-3390, primary focus was implementing the `ZoneDistributionStackedBar` composable, the 3-way segmented button switching on individual cards, and defining the enum and DataStore persistence key. Exposing the global default selector in Advanced Settings and passing it down as the card default was deferred to keep ATT-3390 focused on graphical component rendering.
4. *Preservation of Core Invariants*: Interactive per-card session toggling preserved; backward-compatible `FIVE_ZONES` enum alias preserved; telemetry metric graph marginal strip rendering preserved; REQ-UI-262 <= 400 lines limit preserved; and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in Advanced Settings navigating to Workout-Masken & Detailkarten,
• *When* Zone Analysis is enabled in either List or Details,
• *Then* a dedicated 3-way segmented button selector titled Zone Card Display Mode is displayed with options Stacked Bar, Columns, and Histogram, and selecting any option updates `workoutCardPrefs.zoneCardDisplayMode` and persists to DataStore on Save.
• *Given* `workoutCardPrefs.zoneCardDisplayMode` configured as `STACKED_BAR`,
• *When* viewing the workout journal list (`WorkoutSummary.kt`),
• *Then* all `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` instances initialize displaying the horizontal stacked bar (`ZoneDistributionStackedBar`), and when the global preference in DataStore is updated to `COLUMNS`, all cards without manual overrides reactively re-render as column charts.
• *Given* a workout summary card currently rendering in `STACKED_BAR` by global default,
• *When* the athlete taps the Columns button on that specific card,
• *Then* that card switches to `COLUMNS`, preserving its manual selection during the active session.
• *Given* global preference configured as `HISTOGRAM`,
• *When* viewing an activity without fine-grained telemetry histogram data (`distribution.histogram == null`),
• *Then* `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` gracefully resolve to `COLUMNS`, and the 2-button segmented row highlights Columns as selected.
• *Given* `WorkoutMasksAndCardsSection.kt` after adding the segmented button row selector,
• *When* `AdvancedTuningModularityTest.testAllTuningSourceFilesUnder400Lines` executes,
• *Then* the file length is strictly <= 400 lines.

**Invariants**: Backward-compatible `FIVE_ZONES` enum value, telemetry graph marginal strip preservation, REQ-UI-262 <= 400 lines limit, zero SQLite migrations, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-373: Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines [Verified]
- **Tests**: `TST-UI-336`, `TST-UI-391`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneCalculationMath.kt`, `ZoneValidator.kt`, `ZoneNumberParser.kt`, `ZoneCalculatorDialog.kt`, `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`
- **Rationale**: The system SHALL provide automated, deterministic training zone boundary calculation directly within the zones settings workflow (`ZonesSettingsActivity.kt`) from physiological benchmarks (Functional Threshold Power for cycling power, and Maximum Heart Rate / Resting Heart Rate for heart rate), while strictly preserving manual customization, guaranteeing single-transaction ACID atomicity in DataStore, enforcing two-layer boundary inversion protection with strict integer increments ($Z_n \ge Z_{n-1} + 1$), guaranteeing historical analytics immutability, and maintaining full 9-language localization parity (ATT-3188, amending `REQ-UI-370` and complementing `REQ-PRO-022`):
1. *Cycling Power Coggan 7-Zone Calculation & Zone 7 Ceiling (`REQ-UI-373.1`)*:
• The system SHALL compute cycling power zone upper boundaries from Functional Threshold Power (FTP, in Watts) using the canonical Coggan percentages: Z1 (55%), Z2 (75%), Z3 (90%), Z4 (105%), Z5 (120%), Z6 (150%).
• The system SHALL apply deterministic half-up rounding (`Math.round(percent * ftp).toInt()`) with boundary rationalization guaranteeing strictly monotonic ascending boundaries ($Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$).
• Zone 7 upper bound SHALL be capped at physical maximum $2500\text{ Watts}$ (finite storage invariant; zero `Infinity` values stored in DataStore). Zone 7 lower bound is $Z_{6\text{max}} + 1\text{ W}$.
• Allowed FTP input range SHALL be constrained to $[50, 600]\text{ Watts}$. For maximum FTP = $600\text{ W}$, Zone 6 cutoff evaluates to $900\text{ W}$, leaving a valid Zone 7 interval $[901\text{ W}, 2500\text{ W}]$. If any input sets $Z_{6\text{max}} \ge 2500\text{ W}$, `ZoneCalculationMath` and `ZoneValidator` MUST throw `IllegalArgumentException`.
2. *Heart Rate Zone Calculation (% of HRmax and Karvonen HRR) & Explicit Error Messages (`REQ-UI-373.2`)*:
• Model A (% of HRmax): Z1 (59%), Z2 (69%), Z3 (79%), Z4 (89%), Z5 ($HR_{\max}$).
• Model B (Karvonen HRR): Target HR = $\text{round}(HR_{\text{rest}} + (HR_{\max} - HR_{\text{rest}}) \times \text{intensity}\%)$. Z1 (59% HRR), Z2 (69% HRR), Z3 (79% HRR), Z4 (89% HRR), Z5 ($HR_{\max}$).
• Allowed baseline ranges: $HR_{\text{rest}} \in [30, 120]\text{ bpm}$, $HR_{\max} \in [80, 240]\text{ bpm}$.
• Primary Karvonen Invariant Check: Enforces $HR_{\max} > HR_{\text{rest}}$ and $HR_{\max} - HR_{\text{rest}} \ge 20\text{ bpm}$. If $HR_{\max} \le HR_{\text{rest}}$, the UI displays `zone_calc_error_hr_max_greater_rest` (*"HFmax muss größer als HFruhe sein"*) and disables calculation. If $HR_{\max} - HR_{\text{rest}} < 20\text{ bpm}$, the UI displays `zone_calc_error_karvonen_delta` (*"HFmax muss mindestens 20 bpm über HFruhe liegen"*) and disables calculation. `ZoneCalculationMath` throws `IllegalArgumentException` on violation.
3. *Two-Layer Monotonicity Validation & Strict Step Increment (`REQ-UI-373.3` & `REQ-UI-373.4`)*:
• Layer 1 (UI Reactive Barrier): Evaluated synchronously on every keystroke (`onValueChange`). Verifies $Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$ and $Z_{n\text{max}} \le Z_{n+1\text{max}} - 1$. Minimum integer delta $\Delta = 1$ is mandatory (adjacent zones cannot share identical cutoffs). If violated, sets `isError = true`, displays error message (\"Wert muss zwischen %d und %d liegen\"), and disables Save/Apply buttons.
• Layer 2 (Domain Blocking Guard): `ZoneValidator.validatePowerZoneStack()` and `validateHeartRateZoneStack()` throw `IllegalArgumentException` on any inversion ($Z_i \ge Z_{i+1}$) or out-of-bounds cutoff.
4. *Single-Transaction ACID Atomicity & UI Mutation Lock (`REQ-UI-373.5`, `REQ-UI-373.6` & `REQ-UI-373.10`)*:
• Baseline mutation, customization flag (`pwr_bike_zones_customized`, `hr_zones_customized`), and all cutoffs persist atomically in a single `dataStore.edit { }` block via file-level atomic swap.
• UI maintains `isSaving: Boolean` state; while writing to DataStore, action buttons are disabled with loading indicators, preventing concurrent or re-entrant mutations.
• UI observes state via UDF `collectAsState()` directly from DataStore, eliminating disconnected local state.
5. *Formal Data Integrity Policy & Historical Analytics Immutability (`REQ-UI-373.7`)*:
• Prospective-Only Baseline Mutation: Past completed workouts in SQLite (`WorkoutSummaries.db`, `Extrema.db`) are immutable and NEVER retroactively recalculated.
• Intra-Session Historical Fidelity: Mid-workout baseline changes apply strictly to subsequent telemetry ticks ($t \ge t_{\text{change}}$).
6. *Customization Lifecycle & Destructive Overwrite Warning (`REQ-UI-373.8`)*:
• Profiles transition to `CUSTOMIZED` upon any manual edit of an individual cutoff field.
• Recalculating over customized zones triggers a modal confirmation dialog warning that manual zones will be overwritten. Cancel preserves custom zones with 0 changes.
7. *Locale-Aware Number Parsing & 9-Language Localization Token Parity (`REQ-UI-373.9`)*:
• `ZoneNumberParser.parseIntOrNull()` handles `,` vs `.` and non-breaking space grouping.
• All UI strings maintain 100% translation parity and format specifier token parity (exact count, order, and argument types of `%d`, `%s`, `%1$d`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-373`. Amends `REQ-UI-370` (*Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model*, Sprint 2026-42.4, ATT-3184) and complements `REQ-PRO-022`. Targets `ZoneCalculationMath.kt`, `ZoneValidator.kt`, `ZoneNumberParser.kt`, `ZoneCalculatorDialog.kt`, `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced manual zone configuration; Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) expanded power to 7 zones. Active ticket `ATT-3188`.
3. *Root Reason for Existing Formulation*: Zones were previously entered manually because athlete baselines (FTP, HRmax) were not stored in DataStore, treating cutoffs as raw arbitrary thresholds.
4. *Preservation of Core Invariants*: Manual cutoff customization preserved, proportional migration fallback preserved, SQLite historical workout immutability preserved, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete entering FTP = 250W in the calculator dialog,
• *When* tapping "Berechnen",
• *Then* calculated preview displays: Z1 <= 138W, Z2 <= 188W, Z3 <= 225W, Z4 <= 263W, Z5 <= 300W, Z6 <= 375W, Z7 is [376W, 2500W] capped at 2500W.
• *Given* HRmax = 190 and HRrest = 50,
• *When* calculating Karvonen zones,
• *Then* cutoffs resolve to: Z1 <= 133, Z2 <= 147, Z3 <= 161, Z4 <= 175, Z5 <= 190 bpm.
• *Given* HRmax - HRrest < 20 bpm,
• *When* inputting in the dialog,
• *Then* calculation is disabled and the explicit localized error message `zone_calc_error_karvonen_delta` is displayed.
• *Given* HRmax <= HRrest,
• *When* inputting in the dialog,
• *Then* calculation is disabled and the explicit localized error message `zone_calc_error_hr_max_greater_rest` is displayed.
• *Given* manual editing of adjacent zone cutoffs with zero delta ($Z_3 = 200, Z_4 = 200$),
• *When* validated,
• *Then* validation fails because $Z_4$ must be >= $Z_3 + 1$.
• *Given* calculated zones applied from the dialog,
• *When* persisted to DataStore,
• *Then* baseline, timestamp, customization flag, and all cutoffs save atomically in a single transaction while disabling action buttons with a loading state.
• *Given* customized zones and user tapping recalculate,
• *When* modal confirmation appears and user taps cancel,
• *Then* existing custom zones remain completely untouched.

**Invariants**: Historical workout immutability in SQLite, finite Zone 7 bound (2500W ceiling), strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$), canonical SI storage, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-374: Contextual Zone Settings Navigation Upon Physiological Sensor Pairing [Verified]
- **Tests**: `TST-UI-337`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Rationale**: The system SHALL automatically direct the athlete to the relevant training zone settings tab immediately after pairing a new physiological sensor (heart rate monitor or power meter) if physiological zones for that metric have not yet been configured, while maintaining silent pairing completion for configured athletes and non-physiological sensors (ATT-3189, ATT-4452, complementing `REQ-UI-159`, `REQ-UI-370`, and `REQ-UI-373`):
1. *Pairing Detection & Sensor Discrimination (`REQ-UI-374.1`)*:
• The system SHALL monitor pairing transitions when a sensor's state changes from `isPaired == false` to `isPaired == true` in `DeviceListViewModel`.
• The system SHALL discriminate device capability based on `DeviceType`:
- Heart Rate Monitor: `DeviceType.HRM` (BLE Heart Rate or ANT+ Heart Rate).
- Cycling Power Meter: `DeviceType.BIKE_POWER` (BLE Cycling Power or ANT+ Bike Power).
• For all other sensor types (`BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, etc.), the system SHALL complete pairing silently on the sensor screen without navigating away.
• Unpairing a sensor (`isPaired` transitioning to `false`) SHALL NEVER trigger navigation.
2. *Zone Configuration Status Guard in SettingsDataStore (`REQ-UI-374.2`)*:
• The system SHALL maintain two persistent boolean preferences in `SettingsDataStore`:
- `KEY_IS_HR_ZONES_CONFIGURED = booleanPreferencesKey("is_hr_zones_configured")`
- `KEY_IS_PWR_ZONES_CONFIGURED = booleanPreferencesKey("is_pwr_zones_configured")`
• On a fresh installation, both flags SHALL default to `false`.
• Migration Heuristic / Fallback: If `KEY_IS_HR_ZONES_CONFIGURED` is unset, the system evaluates `hr_max != null

### REQ-UI-376: Real-Time Visual Training Zone Gauge Cockpit Integration [Verified]
- **Tests**: `TST-UI-339`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneGaugeBar.kt`, `ZoneGaugeMath.kt`, `SensorFieldView.kt`, `ZoneDisplayOptions.kt`, `ZonesSettingsActivity.kt`
- **Rationale**: The system SHALL provide an integrated real-time visual training zone gauge bar for Heart Rate and Cycling Power sensor tiles in the live tracking cockpit, dynamically adapting to the athlete's configured zone model (5-zone Heart Rate or 7-zone Coggan Cycling Power), calculating high-precision intra-zone needle positions, providing ambient peripheral comprehension, and maintaining strict AMOLED contrast and performance budgets (ATT-1293, extending `REQ-UI-172` and complementing `REQ-UI-370`, `REQ-UI-372`, `REQ-UI-373`, and `REQ-UI-374`):
1. *User Configuration & DataStore Persistence (`REQ-UI-376.1`)*:
• `ZoneDisplayOptions.kt` SHALL define property `val showGauge: Boolean = false`.
• `SettingsDataStore.kt` SHALL serialize and persist `showGauge` across application restarts for all three zone profiles (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) using preference keys `hr_run_show_zone_gauge`, `hr_bike_show_zone_gauge`, and `pwr_bike_show_zone_gauge`.
• `ZonesSettingsActivity.kt` SHALL include a toggle row \"Zonen-Balken / Zone gauge bar\" (`R.string.zone_display_gauge`) in the \"Zonendarstellung / Zone Visualization\" card, updating the live preview and persisting to DataStore.
2. *Decoupled Segment Geometry & Equal Visual Width (`REQ-UI-376.2`)*:
• The visual gauge track SHALL be partitioned into $K$ contiguous, equal-width horizontal segments matching the active metric's physiological model:
- For Heart Rate (`HR_RUN`, `HR_BIKE`): Exactly $K = 5$ segments using `TTColor.Zone1..Zone5`.
- For Cycling Power (`PWR_BIKE`): Exactly $K = 7$ segments using `TTColor.Zone1..Zone7`.
• Equal visual width per zone ($\frac{1}{K}$ width each) guarantees that narrow high-intensity target zones (e.g. Z4 Sweetspot, Z5 VO2max) remain clear, readable, and trackable without shrinking to unreadable slivers.
3. *Intra-Zone Needle Interpolation & Clamping Math (`ZoneGaugeMath.kt`) (`REQ-UI-376.3`)*:
• For interior closed zones $i \in \{1 \dots K-1\}$ with bounds $[L_i, U_i]$:
$$\text{fraction}_i = \frac{v - L_i}{U_i - L_i} \in [0.0, 1.0]$$
$$x_{\text{needle}} = \frac{(i - 1) + \text{fraction}_i}{K} \in [0.0, 1.0]$$
• Baseline lower bounds: $L_1 = 0.0\text{ W}$ for Power; $L_1 = 30.0\text{ bpm}$ for Heart Rate.
• For readings below baseline ($v \le L_1$): Needle clamps cleanly to $x_{\text{needle}} = 0.0$ (left edge of Zone 1).
• Open-Ended Top Zone Dynamic Ceiling:
- For HR Zone 5: Lower bound $L_5 = Z_{4\text{max}}$. Nominal ceiling $U_5 = \max(H\!R_{\max}, Z_{4\text{max}} + 25\text{ bpm})$.
- For Power Zone 7: Lower bound $L_7 = Z_{6\text{max}}$. Nominal ceiling $U_7 = \min(2500\text{ W}, \max(Z_{6\text{max}} + 300\text{ W}, \text{FTP} \times 2.0))$, conforming to the $2500\text{ W}$ physical ceiling of `REQ-UI-373.1`.
- For top zone ($v \ge L_{\text{top}}$):
$$\text{fraction}_{\text{top}} = \min\left(1.0, \frac{v - L_{\text{top}}}{U_{\text{top}} - L_{\text{top}}}\right)$$
$$x_{\text{needle}} = \frac{(K - 1) + \text{fraction}_{\text{top}}}{K}$$
- Peak sprints ($v \ge U_{\text{top}}$) clamp cleanly to $x_{\text{needle}} = 1.0$ without overflow.
4. *Telemetry Stale / Disconnected / Null State Handling (`REQ-UI-376.4`)*:
• When telemetry is absent, disconnected, unconfigured, or stale (`v == null`, `v.isNaN()`, `!v.isFinite()`, or stale packet age $> 5\text{ s}$):
- The needle SHALL be completely hidden (needle alpha 0.0).
- The segmented zone track SHALL remain rendered with dimmed opacity (alpha 0.35), preserving full visual context of the zone boundaries.
5. *Cockpit UI & AMOLED Contrast Compliance (`REQ-UI-376.5`)*:
• `ZoneGaugeBar.kt` SHALL render a compact horizontal track (`height = 6.dp`, `shape = RoundedCornerShape(3.dp)`).
• Segments SHALL be separated by thin vertical dividers (`1.dp`).
• The outer track and segment dividers SHALL be stroked with `MaterialTheme.colorScheme.outlineVariant` (`#2B3342`), ensuring non-text contrast $\ge 3:1$ against True-Black AMOLED backgrounds (`#000000`) for dark zones (`TTColor.Zone6` and `TTColor.Zone7`).
• The indicator needle SHALL be rendered with a high-contrast inverted marker (Pure White `#FFFFFF` with a 1dp dark shadow/border) with minimum height of `10.dp` centered vertically over the track, achieving $> 12:1$ contrast against any zone segment color.
• When configured, a compact active zone badge (`Z1`..`Z7`) rendered in the active zone color SHALL appear adjacent to the gauge.
6. *60 FPS Performance & Recomposition Decoupling (`REQ-UI-376.6`)*:
• The gauge needle position SHALL be calculated via pure $O(1)$ math.
• Needle rendering in Compose SHALL execute via canvas drawing or drawWithContent without triggering full tile recompositions on high-frequency power packets (2Hz–4Hz).
7. *9-Language Localization Parity (`REQ-UI-376.7`)*:
• Add string token `zone_display_gauge` across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-376`. Extends `REQ-UI-172` (*Configurable Zone Visual Presentation*), complements `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*), `REQ-UI-372` (*Horizontal Stacked Bar*), `REQ-UI-373` (*Automated Zone Boundaries*), and `REQ-UI-374` (*Contextual Zone Settings Navigation*). Targets `ZoneDisplayOptions.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `ZoneGaugeBar.kt`, `ZonesSettingsActivity.kt`, and `SettingsDataStore.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced `ZoneDisplayOptions` (background tint, edge strips, text color). Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) added 7 power zones. Active ticket `ATT-1293`.
3. *Root Reason for Existing Formulation*: Background tint and edge strips indicate categorical zone membership but lack continuous intra-zone target location feedback during hard efforts.
4. *Preservation of Core Invariants*: Full backward compatibility for `ZoneDisplayOptions`, True-Black AMOLED theme contrast, 16ms/60fps frame budget, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete configuring zone display options in Zone Settings,
• *When* enabling "Zonen-Balken / Zone gauge bar",
• *Then* `showGauge` persists to DataStore as true and displays live in the preview tile.
• *Given* active heart rate tracking in the cockpit with `showGauge == true`,
• *When* HR is at the midpoint of Zone 3,
• *Then* `ZoneGaugeBar` displays 5 equal segments (Z1..Z5) and the needle is positioned at $x = 0.50$ (middle of segment 3).
• *Given* active cycling power tracking in the cockpit with `showGauge == true`,
• *When* power is in Zone 6 (Anaerobic Capacity),
• *Then* `ZoneGaugeBar` displays 7 equal segments (Z1..Z7) using `TTColor.Zone1..Zone7` and the needle indicates position within segment 6.
• *Given* dark/AMOLED theme,
• *When* observing `ZoneGaugeBar` in Zone 6 or Zone 7,
• *Then* the track and dividers are outlined with `outlineVariant` and the needle is white with dark outline, maintaining WCAG contrast $> 3:1$.
• *Given* disconnected or null sensor data,
• *When* observing `ZoneGaugeBar`,
• *Then* the needle is hidden and the track is rendered with dimmed opacity (0.35 alpha).

**Invariants**: Existing `ZoneDisplayOptions` backward compatibility, AMOLED True-Black compatibility, 60fps frame budget, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-377: Pedal Power Balance Real-Time Visual Split Indicator Cockpit Integration [Verified]
- **Tests**: `TST-CON-022`, `TST-UI-340`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: \Delta
- **Rationale**: The system SHALL provide an integrated real-time visual pedal power balance split-bar widget for dual-sided power meters in the live tracking cockpit, featuring dual split formatting, center-anchored bidirectional deviation gauging, physiological asymmetry tier color-coding, and strict True-Black AMOLED compliance (ATT-1295, extending `REQ-UI-171` and `REQ-UI-103`, and complementing `REQ-UI-376`):
1. *Mathematical Representation & Telemetry Ingestion (`REQ-UI-377.1`)*:
• `PedalPowerBalanceMath.kt` SHALL consume raw telemetry representing Right pedal percentage $P_R \in [0, 100]\%$.
• The system SHALL calculate Left pedal percentage $P_L = 100 - P_R$.
• For valid readings, the deviation magnitude is $

### REQ-UI-378: Compact Action Buttons and Header Height Containment in ZonesSettingsActivity [Verified]
- **Tests**: `TST-UI-341`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`
- **Rationale**: The system SHALL ensure header compactness and prevent vertical layout blowout in `ZonesSettingsActivity.kt` by replacing text buttons with compact symbol icon actions and bounding title container width, adhering to `docs/design_guidelines.md` Section 5.6 (ATT-3675, amending `REQ-UI-370` and `REQ-UI-373`):
1. *Compact Symbol Icon Actions (`REQ-UI-378.1`)*:
• In `ZonesSettingsActivity.kt`, the two horizontal `TextButton` components ("Zonen berechnen" and "Auf Standardwerte zurücksetzen") SHALL be replaced with compact Material 3 `IconButton` components: `Icons.Default.Calculate` (contentDescription `R.string.zone_calc_button`) and `Icons.Default.RestartAlt` (contentDescription `R.string.reset_to_defaults`).
• Both icons SHALL use `tint = MaterialTheme.colorScheme.onPrimaryContainer`.
2. *Bounded Title Container & Ellipsis Guard (`REQ-UI-378.2`)*:
• The left-side title container holding `title_edit_zones` and `SuggestionChip` SHALL apply `Modifier.weight(1f, fill = false)`.
• The title `Text` component SHALL declare `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
3. *Header Height Containment Invariant (`REQ-UI-378.3`)*:
• The total measured height of the header row in `ZonesSettingsActivity.kt` SHALL remain strictly $\le 64\text{ dp}$ across all supported mobile viewports ($360\text{ dp} \dots 600\text{ dp}$) and all 9 application locales.
4. *Accessibility Parity (`REQ-UI-378.4`)*:
• Both action buttons SHALL maintain a minimum $48\text{ dp} \times 48\text{ dp}$ touch target with localized `contentDescription` matching `R.string.zone_calc_button` and `R.string.reset_to_defaults`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends UI presentation of `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*) and `REQ-UI-373` (*Automatic Training Zone Boundary Calculation*). Targets `ZonesSettingsActivity.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3184` commit `0da639ba`, `ATT-3188` commit `c800ca58`).
3. *Root Reason for Existing Formulation*: Actions were initially introduced with textual `TextButton` controls, which caused horizontal layout starvation and 30-line vertical wrapping under localized German strings.
4. *Preservation of Core Invariants*: Full functional calculation and reset-to-defaults capabilities preserved, accessibility descriptions preserved, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZonesSettingsActivity` rendered on Google Pixel 10 under German locale,
• *When* the header row renders,
• *Then* header height is $\le 64\text{ dp}$, tab row and boundary cards remain visible above the fold, and zero 1-character vertical text wrapping occurs.
• *Given* tapping the Calculate icon button,
• *When* clicked,
• *Then* `ZoneCalculatorDialog` opens.
• *Given* tapping the Reset icon button,
• *When* clicked,
• *Then* `dataStore.resetToDefaults` is executed.

**Invariants**: Header height $\le 64\text{ dp}$, $48\text{ dp}$ touch targets, WCAG accessibility parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-379: State-Aware Live Segment Lifecycle Metrics, Past-Finish Rollout & PR Delta Feedback [Verified]
- **Tests**: `TST-UI-342`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveSegmentSheet.kt`, `SegmentLiveDetails.kt`, `LiveSegmentsRepository.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL decouple post-finish rollout tracking from lateral course offset, provide real-time Personal Record (PR) time delta feedback upon completion, and conditionally render metrics based on segment lifecycle state in `LiveSegmentSheet.kt` and `SegmentLiveDetails.kt` (ATT-3578, complementing `REQ-MAP-027`, `REQ-UI-196`, `REQ-UI-340`, and `REQ-UI-366`):
1. *Domain Model Decoupling (`LiveSegmentData`)*:
• `LiveSegmentData` SHALL declare dedicated fields for post-finish tracking and PR delta: `val timeOnSegment_raw: Long = 0L`, `val distancePastFinish: String = "--"`, `val distancePastFinish_raw: Double = 0.0`, `val prDeltaSeconds: Long? = null`, `val prDeltaFormatted: String? = null`.
• In `LiveSegmentStatus.FINISHED`, `LiveSegmentsRepository` SHALL store `distanceToEnd` exclusively in `distancePastFinish` and `distancePastFinish_raw`.
• `LiveSegmentsRepository` SHALL NOT overload `segmentOffset` or `segmentOffset_raw` with `distanceToEnd`.
2. *Personal Record (PR) Delta Calculation (`LiveSegmentsRepository.kt`)*:
• When a segment finishes with `staticData.summary.prTime_raw > 0`:
- `prDeltaSeconds` SHALL equal `timeOnSegment - prTime_raw`.
- `prDeltaFormatted` SHALL format with explicit sign (`"-M:SS"` if `prDeltaSeconds <= 0`, `"+M:SS"` if `prDeltaSeconds > 0`).
• If `staticData.summary.prTime_raw <= 0`, `prDeltaSeconds` and `prDeltaFormatted` SHALL remain `null`.
3. *State-Aware Metric Visibility (`SegmentLiveDetails.kt`)*:
• **When `LiveSegmentStatus.APPROACHING`**:
- Render Line 1: `R.string.segment_status_start_in` with `distanceToStart` (`titleLarge`, bold).
- Render Line 2: `R.string.segment_status_remaining` with `remainingDistance` (`bodyLarge`, bold).
- The lateral offset row (`R.string.segment_status_offset`) SHALL be completely hidden.
• **When `LiveSegmentStatus.ON_SEGMENT` or `ON_SEGMENT_CLOSE_TO_FINISH`**:
- Render Line 1: `distanceOnSegment`.
- Render Line 2: `R.string.segment_status_remaining` with `remainingDistance`.
- Render Line 3: `R.string.segment_status_offset` with `segmentOffset` and cross-track warning colors (`onSurfaceVariant` / `TTColor.Zone3` / `TTColor.Zone4`).
• **When `LiveSegmentStatus.FINISHED`**:
- Render Line 1: `distanceOnSegment` (`titleLarge`, bold).
- Render Line 2: `stringResource(R.string.segment_status_past_finish, liveSegmentData.distancePastFinish)` in neutral `MaterialTheme.colorScheme.onSurfaceVariant` (`bodyMedium`).
- Neither `remainingDistance` ("Ziel in: 0 m") nor `segmentOffset` ("Daneben: ...") SHALL be rendered.
- In the right column, below `timeOnSegment`: when `prDeltaFormatted != null`, render `stringResource(R.string.segment_status_pr_delta, liveSegmentData.prDeltaFormatted)` colored in `TTColor.Zone2` (green) when `(prDeltaSeconds ?: 1L) <= 0L` and `MaterialTheme.colorScheme.onSurfaceVariant` when `> 0L`.
4. *9-Language Localization Parity (Rule 29)*:
• `segment_status_past_finish` and `segment_status_pr_delta` SHALL exist with non-empty translations and matching format specifiers (`%1$s`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.
• `segment_status_offset` SHALL be corrected in `values-ja` to "コースずれ: %1$s" and clarified in `es`, `fr`, `nl`, `pl`, `pt`.
5. *Preservation of System Invariants*:
• Virtual gate finish line crossing math, polyline orthogonal projection, bottom sheet layout height, and 100% full clean-room unit test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-379`). Complements `REQ-MAP-027` (*ClimbPro Cockpit Sheet*), `REQ-UI-196` (*Live Segment Bottom Sheet Design*), `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*), and `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture*).
2. *Historical Origin & Commit Trace*: `LiveSegmentData` and `SegmentLiveDetails` were introduced in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`, commits `50123512`, `7d488f51`) and refined in Sprint 2026-41.8 (`ATT-3108`, commit `91b12a9a`).
3. *Root Reason for Existing Formulation*: When segment tracking was first implemented, `segmentOffset` was the only secondary distance field on `LiveSegmentData`. To display how far the rider had traveled after crossing the finish line before auto-dismissal, `distanceToEnd` was shoved into `segmentOffset`. At the time, `SegmentLiveDetails` simply displayed all three lines statically without state-based visibility branches.
4. *Preservation of Core Invariants*: Active cross-track deviation warnings while on segment, along-track orthogonal projection, finish line virtual gate detection, and 100% full clean-room test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a live segment transitioning to `LiveSegmentStatus.FINISHED` with `distanceToEnd = 210.0`, `timeOnSegment = 219`, and `prTime_raw = 229`,
• *When* `SegmentLiveDetails` renders,
• *Then* it displays `distanceOnSegment`, "Nach Ziel: 210 m" / "Past finish: 210 m" in neutral `onSurfaceVariant`, and "-0:10 vs. PR" in green (`TTColor.Zone2`), with zero occurrences of "Ziel in: 0 m" or red "Daneben: 210 m".
• *Given* a live segment in `LiveSegmentStatus.APPROACHING` state,
• *When* `SegmentLiveDetails` renders,
• *Then* it displays `distanceToStart` and `remainingDistance`, and completely omits the lateral offset row ("Daneben: --").
• *Given* a live segment in `LiveSegmentStatus.ON_SEGMENT` with `distanceToSegment = 40.0`,
• *When* `SegmentLiveDetails` renders,
• *Then* it displays `distanceOnSegment`, `remainingDistance`, and `segmentOffset` ("Daneben: 40 m") styled with `TTColor.Zone4`.
• *Given* all 9 supported locale `strings.xml` resource files,
• *When* resolving `segment_status_past_finish`, `segment_status_pr_delta`, and `segment_status_offset`,
• *Then* all keys exist, are non-empty, and format strings compile cleanly.

**Invariants**: Pure UI presentation, zero changes to GPS projection math, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-380: Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture [Verified]
- **Tests**: `TST-UI-343`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable*`, Compose icon call sites, `DrawableDensityFallbackAuditTest.kt`, `ComposeSafePainterContractTest.kt`
- **Rationale**: The system SHALL eliminate `Resources$NotFoundException` crashes on density-split APKs and custom DPI devices by populating complete high-density raster drawables and systematically migrating dynamic Jetpack Compose icon call sites to defensive `safePainterResource` resolution (ATT-3529, amending `REQ-UI-312` and complementing `REQ-UI-375`):
1. *Pillar 1: Complete High-Density Asset Coverage (`REQ-UI-380.1`)*:
• The system SHALL provide high-density raster assets in `drawable-xxhdpi/` (3x) and `drawable-xxxhdpi/` (4x) for all core protocol, branding, and cloud drawables:
• `ant_logo.png`: `drawable-xxhdpi/` (144x144 px) and `drawable-xxxhdpi/` (192x192 px).
• `logo_protocol_bluetooth.png`: `drawable-xxxhdpi/` (192x192 px).
• `dropbox_logo_blue.png`: `drawable-xxxhdpi/` (192x192 px).
• `logo.png`: `drawable-xxhdpi/` (144x126 px) and `drawable-xxxhdpi/` (192x168 px).
• All populated PNGs SHALL maintain exact aspect ratios, transparent alpha channels, and crisp visual geometry matching existing raster assets.
2. *Pillar 2: Defensive Compose Architecture Migration (`REQ-UI-380.2`)*:
• The system SHALL migrate all dynamic icon rendering call sites from raw `painterResource(id = ...)` to `safePainterResource(id = ..., fallback = ...)` across all 11 target components:
• `PairingButtons.kt`: Pairing protocol items (`ant_logo`, `logo_protocol_bluetooth`).
• `PairingProtocolBottomSheet.kt`: BLE and ANT+ selection items.
• `DeviceTypeSelectionDialog.kt`: Device type list items.
• `DeviceItem.kt`: Discovered and paired device list items.
• `SensorSourceDialog.kt`: Sensor type header and device selection list items.
• `SensorStatus.kt`: Cockpit sensor availability indicators.
• `SearchArea.kt`: Sensor scanning indicator.
• `DropboxConnectionHeader.kt`: Cloud connection banner logo.
• `DropboxSettingsDialog.kt`: Settings dialog title icon.
• `ActivityTypeSelectionDialog.kt`: Sport activity selection dialog icons.
• `ControlTrackingButton.kt`: Primary tracking control action button.
3. *Fallback Vector Provisioning (`REQ-UI-380.3`)*:
• Each migrated call site SHALL supply a context-appropriate Material Design `ImageVector` fallback (e.g. `Icons.Default.Sensors`, `Icons.Default.Bluetooth`, `Icons.Default.Cloud`, `Icons.Default.Sports`) to guarantee graceful visual degradation if an asset is unresolvable.
4. *Automated CI Enforcement (`REQ-UI-380.4`)*:
• `DrawableDensityFallbackAuditTest.kt` SHALL enforce that core protocol and branding drawables exist across `drawable-xhdpi/`, `drawable-xxhdpi/`, and `drawable-xxxhdpi/`.
• `ComposeSafePainterContractTest.kt` SHALL enforce via AST/source code scanning that target Compose components never invoke raw `painterResource`.
5. *Binary & Layout Invariants (`REQ-UI-380.5`)*:
• Resource symbol names (`R.drawable.*`) SHALL remain identical.
• Sizing modifiers (`Modifier.size(...)`), padding, and clipping parameters SHALL remain identical.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*). Targets `app/src/main/res/drawable*`, Compose icon call sites, and `DrawableDensityFallbackAuditTest.kt`. Formally tracked as `REQ-UI-380`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2858` (Sprint `2026-41.5`) introduced `safePainterResource` and baseline fallbacks in root `res/drawable/`. Production release V4.9.38.5 (build 269) experienced a fatal crash on device in `PairingButtons.kt` (`R.drawable.ant_logo`) due to density gaps in Google Play split APKs and unguarded `painterResource` usage.
3. *Root Reason for Existing Formulation*: While `safePainterResource` was created, it was only applied to a subset of UI screens. Furthermore, Google Play App Bundle (`.aab`) density slicing omitted lower-density rasters from high-density split APKs.
4. *Preservation of Core Invariants*: Zero UI thread crashes, full density-split immunity, high visual fidelity on QHD+/4K screens, binary resource symbol stability, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Android project resource directories `drawable-xxhdpi/` and `drawable-xxxhdpi/`,
• *When* auditing `ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, and `logo.png`,
• *Then* all assets are present with valid dimensions and non-zero byte size.
• *Given* the 11 target Compose UI components,
• *When* evaluating dynamic icon rendering,
• *Then* each call site resolves via `safePainterResource` with a non-null semantic fallback `ImageVector`.
• *Given* `DrawableDensityFallbackAuditTest` and `ComposeSafePainterContractTest`,
• *When* executed via `./gradlew testDebugUnitTest`,
• *Then* all tests pass with 100% compliance.

**Invariants**: Zero UI thread crashes, binary resource ID stability, visual fidelity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-381: Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture [Verified]
- **Tests**: `TST-UI-344`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Color.kt`, `Theme.kt`, `LiveClimbSheet.kt`, `ClimbDetailSheet.kt`, and 21 other Compose files
- **Rationale**: The system SHALL centralize all Jetpack Compose color tokens, semantic domain colors, and theme palettes in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt` and expose them via the `TTColor` design system object or Compose theme color schemes (ATT-3574):
1. *Centralized Color Token Definitions (`REQ-UI-381.1`)*:
• The system SHALL define AMOLED container tokens in `Color.kt`: `AmoledPrimaryContainer` (`#000000`), `AmoledOnPrimaryContainer` (`#FFFFFF`), `AmoledSurfaceBright` (`#1A1A1A`), `AmoledSurfaceContainerHigh` (`#121212`), `AmoledSurfaceContainerHighest` (`#000000`).
• The system SHALL define climb category colors in `TTColor`: `ClimbCategoryHc` (`#B71C1C`), `ClimbCategory1` (`#D32F2F`), `ClimbCategory2` (`#EF6C00`), `ClimbCategory3` (`#FBC02D`), `ClimbCategory4` (`#388E3C`), and `ClimbCategoryUncategorized` (`#757575`).
• The system SHALL define climb gradient band colors in `TTColor`: `ClimbGradeFlat` (`#4CAF50`), `ClimbGradeMild` (`#8BC34A`), `ClimbGradeMedium` (`#FFEB3B`), `ClimbGradeSteep` (`#FF9800`), `ClimbGradeSevere` (`#F44336`), and `ClimbGradeExtreme` (`#9C27B0`).
• The system SHALL define a single authoritative helper `TTColor.getClimbGradeColor(gradePercent: Double): Color` mapping slope percentages to the 6 discrete climb gradient bands.
• The system SHALL define dark map canvas and track tokens in `TTColor`: `DarkMapCanvas` (`#121212`) and `DarkMapTrackCyan` (`#00E5FF`).
• The system SHALL define geofence and location badge tokens in `TTColor`: `GeofenceFill` (`#332196F3`), `GeofenceStroke` (`#882196F3`), `GeofencePin` (`#2196F3`), `LocationBadgeHomeContainer` (`#E8F5E9`), `LocationBadgeHomeContent` (`#2E7D32`), `LocationBadgeWorkContainer` (`#FFF8E1`), `LocationBadgeWorkContent` (`#F57F17`), `LocationBadgeOtherContainer` (`#FFE0B2`), `LocationBadgeOtherContent` (`#E65100`).
• The system SHALL define cloud and connectivity tokens in `TTColor`: `DropboxBlue` (`#0061FE`) and `ConnectionConnected` (`#2E7D32`).
• The system SHALL define cockpit gauge and sensor border tokens in `TTColor`: `PauseAmber` (`#F59E0B`), `SensorActiveBorder` (`#2196F3`), `SensorBorderDark` (`#383838`), `SensorBorderLight` (`#D6D6D6`), `BalanceGaugeTrack` (`#1B1B1F`), `ZoneGaugeTrack` (`#1B1B1F`), and `WarningBorderLight` (`#E0E0E0`).
2. *Purge of Inline Color Literals Across Consuming UI Components (`REQ-UI-381.2`)*:
• All 67 inline instantiations of `Color(0x...)` across all 23 production files outside `Color.kt` SHALL be migrated to reference their centralized `Color.kt` or `TTColor` tokens.
• Both `LiveClimbSheet.kt` and `ClimbDetailSheet.kt` SHALL delegate elevation gradient resolution to `TTColor.getClimbGradeColor`.
3. *Zero Hex Literals Outside Color.kt Architecture Enforcement (`REQ-UI-381.3`)*:
• A static source code scanning contract test (`ThemeColorCentralizationAuditTest.kt`) SHALL enforce that exactly 0 occurrences of `Color(0x` exist outside `Color.kt` in `app/src/main/java/`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-381` (*Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture*). Amends architectural design system conventions under Rule 36. Targets `ui/theme/Color.kt`, `TTColor`, and all 23 consuming Jetpack Compose UI files.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.2 (`ATT-1281`: Persistent Climbs Database & Live ClimbPro Cockpit Sheet), Sprint 2026-41.5 (`ATT-1399`: Live Cockpit Location & Barometer Calibration Feedback), and Sprint 2026-41.6 (`ATT-2946`: Speed-Dependent Map Zoom Calibration).
3. *Root Reason for Existing Formulation*: The "fence" (hardcoded inline `Color(0x...)` literals) was erected for high-velocity exploratory prototyping during early feature spikes without the ceremony of updating `Color.kt`. However, as the design system matures toward V4.9.40, this technical debt directly violates the Single Source of Truth & DRY principle (Rule 36), preventing centralized AMOLED/contrast theming.
4. *Preservation of Core Invariants*: All 67 color values are preserved with 100% ARGB numerical identity ($A, R, G, B$ values identical to the original hex literals), preserving pixel-perfect visual fidelity, dark mode contrast, zero UI thread crashes, and 100% full clean-room unit test suite pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* all production Kotlin source files in `app/src/main/java/`,
• *When* executing `ThemeColorCentralizationAuditTest.verifyZeroHexColorsOutsideColorKt`,
• *Then* exactly 0 occurrences of `Color(0x` exist outside `Color.kt`.
• *Given* `LiveClimbSheet.kt` and `ClimbDetailSheet.kt`,
• *When* rendering elevation profiles,
• *Then* both delegate to `TTColor.getClimbGradeColor(gradePercent)`.
• *Given* exact boundary and interior grade percentages across `[-5.0, 0.0, 2.99, 3.0, 5.99, 6.0, 8.99, 9.0, 11.99, 12.0, 14.99, 15.0, 25.0]`,
• *When* evaluating `TTColor.getClimbGradeColor(gradePercent)`,
• *Then* the returned colors match strictly `ClimbGradeFlat`, `ClimbGradeMild`, `ClimbGradeMedium`, `ClimbGradeSteep`, `ClimbGradeSevere`, and `ClimbGradeExtreme`.

**Invariants**: Exact ARGB color parity across all UI screens; zero runtime crashes; 100% full clean-room unit test suite pass rate MUST NOT be broken.

### REQ-UI-382: Comprehensive Canonical Formatting, Unit Conversion, and Dead Control Elimination Architecture [Verified]
- **Tests**: `TST-UI-345`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ClimbDetector.kt`, `LapSplitCalculator.kt`, `AdvancedTuningAccordion.kt`, `NavigationSection.kt`, `SensorsGpsFilterSection.kt`, `ClusterTuningContent.kt`
- **Rationale**: The system SHALL eliminate all hardcoded distance/speed unit conversions, raw metric formatting strings, and obsolete deprecated tuning controls across all remaining production components in strict adherence to Rule 36 (Single Source of Truth & DRY) and Rule 29 (Full 9-Language Localization Parity) (ATT-3575):
1. *Climb Detector Localization & Canonical Distance Formatting (`REQ-UI-382.1`)*:
• `ClimbDetector.kt` SHALL format climb distance via `DistanceFormatter().format_with_units(dist)` instead of manual metric division `dist / 1000.0`.
• `ClimbDetector.kt` SHALL resolve the localized climb title via `TrainingApplication.getAppContext()?.getString(R.string.climb_title) ?: "Climb"` instead of hardcoding German `"Anstieg"`.
• The climb name template SHALL format as `"$climbTitle $formattedDistance @ ${String.format(Locale.getDefault(), "%.1f%%", avgGrade)}"`.
2. *Lap Split Calculator Formatter Delegation (`REQ-UI-382.2`)*:
• In `LapSplitCalculator.kt`, `defaultPaceFormatter(paceSecPerMeter: Double)` SHALL delegate directly to `PaceFormatter().format_with_units(paceSecPerMeter)`, eliminating raw `"%d:%02d /km"`.
• In `LapSplitCalculator.kt`, `defaultSpeedFormatter(speedMps: Double)` SHALL delegate directly to `SpeedFormatter().format_with_units(speedMps)`, eliminating raw magic multiplier `3.6` and `"%.1f km/h"`.
3. *Tuning Accordion and Navigation Section Distance Formatting (`REQ-UI-382.3`)*:
• In `AdvancedTuningAccordion.kt`, `formatNavigationSubtitle` SHALL format route selection radius using `DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())`, eliminating hardcoded `"%.1f km"`.
• In `NavigationSection.kt`, route selection radius slider `valueText` and default text SHALL format using `DistanceFormatter().format_with_units((routeSelectionRadiusKm * 1000f).toDouble())` and `DistanceFormatter().format_with_units((TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM * 1000f).toDouble())`.
4. *Sensors GPS Filter Section Canonical Speed Formatting (`REQ-UI-382.4`)*:
• In `SensorsGpsFilterSection.kt`, the slope minimum speed slider `valueText` SHALL format secondary speed via `SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())` within `String.format(Locale.getDefault(), "%.1f m/s (%s)", slopeMinSpeed, SpeedFormatter().format_with_units(slopeMinSpeed.toDouble()))`, eliminating inline `slopeMinSpeed * 3.6f` and `"km/h"`.
5. *Cluster Tuning Dead Slider Elimination (`REQ-UI-382.5`)*:
• In `ClusterTuningContent.kt`, the dead `@Deprecated fun TuningSlider(...)` composable and all related imports SHALL be completely excised from the codebase.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements and closes technical debt from `REQ-UI-364` (ATT-3361), `REQ-UI-365` (ATT-3363), and `REQ-SET-077` (ATT-3365). Formally tracked as `REQ-UI-382`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.3 standardized primary UI call sites but omitted helper and tuning modules.
3. *Root Reason for Existing Formulation*: Initial feature spikes used ad-hoc string formatting; subsequent standardization passes did not scan peripheral files.
4. *Preservation of Core Invariants*: Numerical calculations (climb scores, pace values, tuning sliders) remain identical. 9-language localization parity is fully maintained (`R.string.climb_title` is already verified across all 9 languages). 100% full clean-room unit test suite pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ClimbDetector.kt`, `LapSplitCalculator.kt`, `AdvancedTuningAccordion.kt`, `NavigationSection.kt`, and `SensorsGpsFilterSection.kt`,
• *When* running `MetricFormatterContractTest` and `SpeedConversionContractTest`,
• *Then* all contract assertions pass with zero hardcoded `"%.1f km"`, `"%.1f km/h"`, `"Anstieg"`, or `* 3.6` formulas.
• *Given* `ClusterTuningContent.kt`,
• *When* running `ClusterTuningContractTest`,
• *Then* `fun TuningSlider` is completely absent.

**Invariants**: Exact numerical parity across calculations; zero runtime crashes; 100% full clean-room unit test suite pass rate MUST NOT be broken.

### REQ-UI-383: Unified Internal Tracking Sheet Layout & Shared LiveTrackingEntitySheet Architecture [Verified]
- **Tests**: `TST-UI-346`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LiveTrackingEntitySheet.kt`, `LiveSegmentSheet.kt`, `LiveClimbSheet.kt`, `LiveTrackingSheetContainerTest.kt`, `LiveSegmentSheetContractTest.kt`, `LiveClimbSheetLayoutTest.kt`
- **Rationale**: The system SHALL eliminate duplicated internal layout structures, divergent outer/inner paddings, and redundant divider declarations across live tracking sheets by introducing a shared internal layout composable `LiveTrackingEntitySheet` in `com.atrainingtracker.trainingtracker.ui.components.core` and refactoring both `LiveSegmentSheet.kt` and `LiveClimbSheet.kt` to strictly delegate to it, enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing visual and structural parity (ATT-3576, completing Tier 2 of `REQ-UI-366`):
1. *Shared Internal Layout Composable (`LiveTrackingEntitySheet.kt`)*:
• The system SHALL define `LiveTrackingEntitySheet` in package `com.atrainingtracker.trainingtracker.ui.components.core` accepting `headerContent: @Composable (Modifier) -> Unit`, `telemetryHudContent: @Composable (Modifier) -> Unit`, `elevationContent: @Composable () -> Unit`, and optional `modifier: Modifier = Modifier`.
• `LiveTrackingEntitySheet` SHALL enforce the canonical vertical layout structure: outer `Column(modifier = modifier.fillMaxWidth().wrapContentHeight().background(MaterialTheme.colorScheme.surface))`, `headerContent(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))`, canonical `HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`, `telemetryHudContent(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))`, and `elevationContent()`.
2. *Refactor LiveSegmentSheet (`LiveSegmentSheet.kt`)*:
• In `LiveSegmentSheet.kt`, `MapDetailLayout`'s `header` slot SHALL invoke `LiveTrackingEntitySheet`, passing `SegmentHeader` to `headerContent` and `SegmentLiveDetails` to `telemetryHudContent`, completely eliminating direct `HorizontalDivider` instantiation.
3. *Refactor LiveClimbSheet (`LiveClimbSheet.kt`)*:
• In `LiveClimbSheet.kt`, the system SHALL decompose the monolithic layout into `ClimbHeader` and `ClimbLiveDetails`.
• `LiveClimbSheet` SHALL delegate its entire root layout to `LiveTrackingEntitySheet`, passing `ClimbHeader` to `headerContent`, `ClimbLiveDetails` to `telemetryHudContent`, and `ElevationProfile` to `elevationContent`, completely eliminating direct `HorizontalDivider` instantiation and divergent outer padding.
4. *Preservation of System Invariants*:
• Clamped wrap-content height (`REQ-UI-270`), dynamic peek heights (`BottomSheetDesign.PeekHeightLiveSegment`), elevation profile gradient shading and rider pin tracking (`REQ-UI-352`), and 100% full clean-room unit test suite pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36)*). Formally tracked as `REQ-UI-383`. Targets `LiveTrackingEntitySheet.kt`, `LiveSegmentSheet.kt`, `LiveClimbSheet.kt`, `LiveTrackingSheetContainerTest.kt`, `LiveSegmentSheetContractTest.kt`, and `LiveClimbSheetLayoutTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3364` (Sprint 2026-42.3, commit `31c4eb4f`) implemented Tier 1 (`LiveTrackingSheetContainer` in `SensorGridScreen.kt`), but left `LiveClimbSheet.kt` untouched (0 lines changed).
3. *Root Reason for Existing Formulation*: The outer container consolidation in `SensorGridScreen.kt` took priority in ATT-3364 to prevent overlapping sheets, leaving internal sheet refactoring incomplete.
4. *Preservation of Core Invariants*: Exact divider styling (0.5dp thickness, `outlineVariant` with alpha 0.5f), uniform 16dp horizontal divider margin, HUD padding (horizontal = 16dp, vertical = 4dp), terrain icon, category badges, status badges, and route counters in `LiveClimbSheet`, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `LiveSegmentSheet.kt` and `LiveClimbSheet.kt`,
• *When* inspecting their source code in `LiveSegmentSheetContractTest` and `LiveClimbSheetLayoutTest`,
• *Then* both files invoke `LiveTrackingEntitySheet(` and neither file contains a direct `HorizontalDivider(` call.
• *Given* an active live segment sheet and an active live climb sheet,
• *When* rendered in the cockpit,
• *Then* header margins (`start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp`), 0.5dp divider margins (`horizontal = 16.dp`), and HUD margins (`horizontal = 16.dp, vertical = 4.dp`) are governed by the single shared `LiveTrackingEntitySheet` composable.
• *Given* the complete unit test suite,
• *When* executing `./gradlew testDebugUnitTest`,
• *Then* 100% of tests pass without errors or regressions.

**Invariants**: Wrap-content height, elevation profile pin alignment, divider styling, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-UI-384: Compact Zone-Tinted Bordered Selector Pills in Zone Settings Preview [Verified]
- **Tests**: `TST-UI-347`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`, `ZonesSettingsPreviewPillsContractTest.kt`
- **Rationale**: The system SHALL prevent horizontal text starvation and vertical line wrapping (`"Z\n1"`..`"Z\n7"`) in the live preview zone selector row of `ZonesSettingsActivity.kt` by replacing Material 3 `FilterChip` + `leadingIcon` controls with compact zone-tinted bordered pills (`ZonePreviewPill`), adhering to Rule 38 (`docs/design_guidelines.md` Section 5.6, ATT-3776, complementing `REQ-UI-172` and `REQ-UI-370`):
1. *Elimination of FilterChip & LeadingIcon Overhead (`REQ-UI-384.1`)*:
• The preview zone selector row in `ZonesSettingsActivity.kt` SHALL NOT use Material 3 `FilterChip` or a separate `leadingIcon` circle indicator.
• Instead, it SHALL render a single horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp))` containing `zoneCount` (`5` for Heart Rate, `7` for Cycling Power) equal-weight (`Modifier.weight(1f)`) `ZonePreviewPill` composables.
2. *Zone-Tinted Border, Subtle Fill Styling & WCAG AA Contrast (`REQ-UI-384.2`)*:
• Each `ZonePreviewPill` SHALL use `RoundedCornerShape(8.dp)`.
• Background container color SHALL be tinted with the zone's canonical color (`zone1Color`..`zone7Color`): `zoneColor.copy(alpha = if (selected) 0.28f else 0.12f)`, harmonizing with `ZoneRow`'s `0.12f` card background alpha.
• Border stroke SHALL use the zone's canonical color: `BorderStroke(width = if (selected) 2.dp else 1.dp, color = if (selected) zoneColor else zoneColor.copy(alpha = 0.6f))`.
• Label text color (`MaterialTheme.colorScheme.onSurface`) over the subtle zone-tinted background (`0.12f` / `0.28f` alpha blended over `surface`) SHALL maintain a WCAG AA contrast ratio $\ge 4.5:1$ in both Light and Dark/AMOLED themes.
3. *Single-Line Centered Label & Font Scale Guarantee (`REQ-UI-384.3`)*:
• Each pill SHALL render centered text `"Z1"`..`"Z7"` with `maxLines = 1`, `softWrap = false`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, and compact horizontal padding (`<= 4.dp`), guaranteeing zero vertical line wrapping across all 5-zone and 7-zone tabs even under Large/Largest system font scales (up to 130%).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-384`, complementing `REQ-UI-172` (`ATT-1390`) and `REQ-UI-370` (`ATT-3184`). Targets `ZonesSettingsActivity.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced 5 `FilterChip`s for Material 3 compliance; Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) expanded `PWR_BIKE` to 7 `FilterChip`s.
3. *Root Reason for Existing Formulation*: Material 3 `FilterChip` with a `10.dp` `leadingIcon` circle was initially chosen to show zone color alongside selection state, but its ~32dp internal horizontal padding plus 10dp icon overhead proved incompatible with the density of 7-zone labels on standard 360dp–412dp screen widths.
4. *Preservation of Core Invariants*: Encoding zone color into the pill border and background tint preserves both color identity, WCAG AA contrast ($\ge 4.5:1$), and selection state while freeing ~32dp of horizontal padding per pill.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZonesSettingsActivity` on either a 5-zone HR tab or 7-zone Cycling Power tab (including 130% system font scale),
• *When* the live preview zone selector row renders,
• *Then* all `Z1`..`Z5` or `Z1`..`Z7` pills render on a single line (`maxLines = 1`, `softWrap = false`) with zone-colored borders, subtle zone-tinted fills ($\ge 4.5:1$ text contrast), and clicking any pill updates `previewZone`.

**Invariants**: Single-line rendering across 5 and 7 zones up to 130% font scale (Rule 38); WCAG AA contrast $\ge 4.5:1$; interactive `previewZone` selection preserved; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-385: Streamlined Single-Action Header in ZonesSettingsActivity [Verified]
- **Tests**: `TST-UI-348`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`, `ZonesSettingsHeaderContractTest.kt`
- **Rationale**: The system SHALL streamline the top header bar of `ZonesSettingsActivity.kt` by removing the status `SuggestionChip`, the Reset-to-Defaults `IconButton`, the "Last updated / Calibration recommended" row, and the "Athlete Weight" row, leaving strictly the screen title, the Calculate `IconButton`, and the `PrimaryScrollableTabRow` (ATT-3777, superseding the secondary header elements of `REQ-UI-373`, `REQ-UI-378`, and `REQ-TRK-019.4`):
1. *Single-Action Top Bar (`REQ-UI-385.1`)*:
• The top header bar of `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`) SHALL contain strictly the screen title (`R.string.title_edit_zones`, with `maxLines = 1` and `overflow = TextOverflow.Ellipsis` inside a `Modifier.weight(1f, fill = false)` container) on the left and the single Calculate `IconButton` (`Icons.Default.Calculate`, `contentDescription = stringResource(R.string.zone_calc_button)`) on the right.
2. *Removal of Redundant Header Elements (`REQ-UI-385.2`)*:
• The header of `ZonesSettingsActivity.kt` SHALL NOT render:
- The status `SuggestionChip` (`R.string.zone_calc_status_customized` / `R.string.zone_calc_status_calculated`).
- The Reset-to-Defaults `IconButton` (`Icons.Default.RestartAlt` / `R.string.reset_to_defaults`).
- The "Last updated / Calibration recommended" row (`R.string.zone_calc_last_updated` / `R.string.zone_calc_calibration_recommended`).
- The "Athlete Weight" row (`R.string.athlete_weight_title` / `R.string.athlete_weight_edit`) and its `showAthleteWeightDialog` block.
3. *Preservation of Tab Bar & Calculator Dialog (`REQ-UI-385.3`)*:
• Directly beneath the title/action row, the header `Surface` SHALL render `PrimaryScrollableTabRow` (`tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`).
• Tapping the `Icons.Default.Calculate` `IconButton` SHALL continue to open `ZoneCalculatorDialog` with full `ZoneCalculatorStateHolder` state.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-385`, superseding secondary header elements of `REQ-UI-373` (`ATT-3188`), `REQ-UI-378` (`ATT-3675`), and `REQ-TRK-019.4` (`ATT-1289`). Targets `ZonesSettingsActivity.kt` and `ZonesSettingsHeaderContractTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3188` commit `c800ca58`, `ATT-1289` commit `5eee662f`) and Sprint 2026-42.5 (`ATT-3675` commit `0cf5f417`).
3. *Root Reason for Existing Formulation*: Status chip, reset button, last-updated timestamp, and athlete weight row were initially placed in the `ZonesSettingsActivity` header before the dedicated Athlete Profile screen (`ATT-3704`) was designed and before on-device review revealed excessive header clutter.
4. *Preservation of Core Invariants*: Retaining `title_edit_zones`, `Icons.Default.Calculate`, and `PrimaryScrollableTabRow` preserves full zone calculation and tab switching while keeping `SettingsDataStore` methods (`resetToDefaults`, `ftpUpdatedAtFlow`, `athleteWeightKgFlow`) intact for unit tests and `ATT-3704`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZonesSettingsActivity` opened on any tab (`HR_RUN`, `HR_BIKE`, or `PWR_BIKE`),
• *When* the top header `Surface` renders,
• *Then* only the title (`title_edit_zones`), the `Icons.Default.Calculate` button, and the `PrimaryScrollableTabRow` are rendered in the header, and zero status chips, reset buttons, last-updated rows, or athlete weight rows appear in the header.
• *Given* the `Icons.Default.Calculate` button in the header,
• *When* tapped by the user,
• *Then* `ZoneCalculatorDialog` opens for the active `ZoneType`.

**Invariants**: Header row height $\le 64\text{ dp}$; single-line ellipsis title; $48\text{ dp}$ Calculate touch target; `SettingsDataStore` API stability; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-386: Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Architecture [Verified]
- **Tests**: `TST-UI-349`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL prevent vertical word and character wrapping (`"Histo-\ngramm"`) across all 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` controls in `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` by enforcing compact single-line `SegmentedButton` layout and concise 9-locale chart labels (`<= 11` characters) in strict compliance with Rule 38 (`docs/design_guidelines.md` Section 5.6, ATT-3771, amending `REQ-UI-372`):
1. *Checkmark Suppression & Compact Horizontal Padding (`REQ-UI-386.1`)*:
• Every `SegmentedButton` in the 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` across `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` SHALL specify `icon = {}` and `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`.
• `SingleChoiceSegmentedButtonRow` in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` SHALL include `Modifier.fillMaxWidth().height(28.dp)`.
2. *Single-Line Typography & Dual Selection Cue (`REQ-UI-386.2`)*:
• Every `SegmentedButton` label `Text` in the 3-way `ZoneCardDisplayMode` selector SHALL specify `style = MaterialTheme.typography.labelSmall`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis`.
3. *Concise 9-Locale Chart Mode Labels (`REQ-UI-386.3`)*:
• Across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` SHALL use concise statistical chart nouns of at most `11` characters (`values`: `"Stacked Bar"`, `values-de`: `"Balken"`, `values-es`: `"Barra"`, `values-fr`: `"Barre"`, `values-it`: `"Barra"`, `values-ja`: `"スタックバー"`, `values-nl`: `"Balk"`, `values-pl`: `"Pasek"`, `values-pt`: `"Barra"`), ensuring zero line wrapping and zero truncation even at 150% system font scale on 360dp viewports.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-386`, amending `REQ-UI-372` (`ATT-3390` & `ATT-3676`) under Rule 38. Targets `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3390` commit `63ccd164`) and Sprint 2026-42.5 (`ATT-3676` commit `b989e1ce`).
3. *Root Reason for Existing Formulation*: `WorkoutMasksAndCardsSection.kt` copied the 2-way `LapDisplayMode` `SingleChoiceSegmentedButtonRow` without `icon = {}` or `labelSmall`, and 6 locales used verbose 13–17 char translations for `zone_mode_stacked_bar`.
4. *Preservation of Core Invariants*: 3-way `ZoneCardDisplayMode` switching (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`), WCAG 2.1 AA contrast (`>= 4.5:1` text, `>= 3.0:1` boundary), dual non-color selection cues (`FontWeight.Bold` + `secondaryContainer`), and `< 400` line limit on `WorkoutMasksAndCardsSection.kt` are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt`,
• *When* rendering the 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` in any of the 9 supported locales (up to 150% system font scale on a 360dp viewport),
• *Then* each `SegmentedButton` renders with `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `style = MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis` on a single horizontal line without wrapping.
• *Given* all 9 `strings.xml` files,
• *When* auditing `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram`,
• *Then* all 27 strings are non-empty and have length `<= 11` characters.

**Invariants**: Zero line wrapping or mid-word breaks across all 9 locales up to 150% font scale (Rule 38); WCAG 2.1 AA contrast; `< 400` lines in `WorkoutMasksAndCardsSection.kt`; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-387: Compact Legend-Free Stacked Bar Display Mode in Zone Distribution Cards [Verified]
- **Tests**: `TST-UI-350`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionStackedBar.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `ZoneDistributionStackedBarTest.kt`
- **Rationale**: The system SHALL omit the detailed per-zone time and percentage breakdown list (`FlowRow` of `StackedBarLegendItem`) underneath the horizontal stacked bar when `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` is rendered in `ZoneCardDisplayMode.STACKED_BAR` mode (ATT-3772, amending `REQ-UI-372`):
1. *Default Legend Suppression (`REQ-UI-387.1`)*:
• `ZoneDistributionStackedBar` in `ZoneDistributionStackedBar.kt` SHALL default its `showLegend: Boolean` parameter to `false`.
• When `showLegend == false`, `ZoneDistributionStackedBar` SHALL render strictly the continuous horizontal stacked bar (`Box` with proportional `weight(entry.percentage)` segments, `1.dp` dividers, and `0.5.dp` outline border) without rendering the `FlowRow` per-zone breakdown list underneath.
2. *Zone Distribution Card Stacked Bar Invocation (`REQ-UI-387.2`)*:
• In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, the `ZoneCardDisplayMode.STACKED_BAR` branch SHALL invoke `ZoneDistributionStackedBar(distribution = distribution, modifier = Modifier.fillMaxWidth(), showLegend = false)`.
3. *Preservation of Detailed Breakdown in Columns Mode (`REQ-UI-387.3`)*:
• Detailed per-zone durations (`m:ss` / `h:mm:ss`), percentages (`%`), and zone badges (`Z1..Z7`) SHALL continue to be displayed in `ZoneCardDisplayMode.COLUMNS` (`ZoneDistributionColumnChart`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-387`, amending `REQ-UI-372` (`ATT-3390` & `ATT-3676`). Targets `ZoneDistributionStackedBar.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, and `ZoneDistributionStackedBarTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3390` commit `63ccd164`) and Sprint 2026-42.5 (`ATT-3676` commit `b989e1ce`).
3. *Root Reason for Existing Formulation*: `ATT-3390` initially defaulted `showLegend = true` on `ZoneDistributionStackedBar` before `STACKED_BAR` mode was designated as the compact overview mode in `ATT-3772` / `ATT-3773`.
4. *Preservation of Core Invariants*: Defaulting `showLegend = false` and passing `showLegend = false` in `STACKED_BAR` mode makes the stacked bar representation compact for workout summaries while preserving detailed per-zone duration/percentage inspection in `COLUMNS` mode.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` with `effectiveDisplayMode == ZoneCardDisplayMode.STACKED_BAR`,
• *When* `ZoneDistributionStackedBar` is rendered,
• *Then* `showLegend` is `false` and the detailed per-zone time breakdown list (`FlowRow` of `StackedBarLegendItem`) is omitted underneath the horizontal stacked bar.
• *Given* `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` with `effectiveDisplayMode == ZoneCardDisplayMode.COLUMNS`,
• *When* `ZoneDistributionColumnChart` is rendered,
• *Then* per-zone durations (`m:ss`), percentages (`%`), and `Z1..Z7` labels remain displayed.

**Invariants**: Continuous horizontal stacked bar proportional weighting (`weight(entry.percentage)`), zero-duration filtering, `16.dp` bar height, `RoundedCornerShape(6.dp)`, `0.5.dp` WCAG 2.1 AA outline border, and `COLUMNS`/`HISTOGRAM` display modes MUST NOT be broken; 100% clean-room test pass rate.

### REQ-UI-388: Decoupled Default Zone Card Display Modes for Workout Summary and Workout Details & Switcher-Free Summary Cards [Verified]
- **Tests**: `TST-UI-351`, `TST-UI-382`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- When `workoutCardPrefs.showZoneAnalysis == true`: a selector labeled with `R.string.settings_zone_card_display_mode_title` and `R.string.tuning_matrix_col_list` bound to `workoutCardPrefs.zoneCardDisplayMode` (`workoutCardPrefs.copy(zoneCardDisplayMode = mode)`).
- When `workoutDetailPrefs.showZoneAnalysis == true`: a selector labeled with `R.string.settings_zone_card_display_mode_title` and `R.string.tuning_matrix_col_details` bound to `workoutDetailPrefs.zoneCardDisplayMode` (`workoutDetailPrefs.copy(zoneCardDisplayMode = mode)`).
• `WorkoutMasksAndCardsSection.kt` SHALL strictly remain under 400 lines (`REQ-UI-262`).
3. *Dense Switcher-Free Workout Summary Cards vs. Interactive Workout Details Cards (`REQ-UI-388.3`)*:
• `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL accept parameter `showModeSelector: Boolean = true` and gate the bottom `SingleChoiceSegmentedButtonRow` on `if (showModeSelector && distribution.entries.isNotEmpty())`.
• In `WorkoutSummary.kt`, `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL be invoked with `showModeSelector = false` and `displayMode` bound to `preferences.zoneCardDisplayMode`, omitting the mode switcher row.
• In `TrackOnMapScreen.kt`, `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` SHALL resolve against `activeDetailPrefs.zoneCardDisplayMode` with `showModeSelector = true`, preserving interactive in-card mode switching and automatic Tier 3 fallback (`resolveWithHistogramFallback(distribution.histogram != null)` -> `COLUMNS`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-388`, amending `REQ-UI-372` (`ATT-3390` & `ATT-3676`). Targets `MyPreferenceManager.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `WorkoutMasksAndCardsSection.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3390` commit `38706240`) and Sprint 2026-42.5 (`ATT-3676` commit `b989e1ce`).
3. *Root Reason for Existing Formulation*: `ATT-3676` wired a single global `WorkoutCardSectionPreferences.zoneCardDisplayMode` across both Workout Summary and Workout Details before the Sprint 2026-42.5 Joint Review established that athletes want a compact, switcher-free `STACKED_BAR` in the Workout Summary list and a detailed `HISTOGRAM` (with switcher) in Workout Details.
4. *Preservation of Core Invariants*: Tier 3 histogram fallback (`HISTOGRAM` -> `COLUMNS` when `distribution.histogram == null`), single-line `SegmentedButton` typography (`REQ-UI-386` / Rule 38), legend-free `STACKED_BAR` (`REQ-UI-387`), and `< 400` line modularity cap (`REQ-UI-262`) are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* default `WorkoutCardSectionPreferences()` and `WorkoutDetailPreferences()`,
• *When* inspected without user overrides,
• *Then* `WorkoutCardSectionPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.STACKED_BAR` and `WorkoutDetailPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.HISTOGRAM`.
• *Given* `WorkoutSummary.kt` rendering `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`,
• *When* Zone Analysis is enabled,
• *Then* both cards are invoked with `showModeSelector = false`, omitting the bottom `SingleChoiceSegmentedButtonRow`.
• *Given* `TrackOnMapScreen.kt` rendering `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`,
• *When* Zone Analysis is enabled,
• *Then* `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` resolve from `activeDetailPrefs.zoneCardDisplayMode` and `showModeSelector` remains `true`.
• *Given* `WorkoutMasksAndCardsSection.kt` in Advanced Settings,
• *When* Zone Analysis is enabled for Summary and/or Details,
• *Then* separate 3-way `SingleChoiceSegmentedButtonRow` selectors update `workoutCardPrefs.copy(zoneCardDisplayMode = mode)` and `workoutDetailPrefs.copy(zoneCardDisplayMode = mode)` and `WorkoutMasksAndCardsSection.kt` has `< 400` lines.

**Invariants**: Tier 3 histogram fallback (`HISTOGRAM` -> `COLUMNS`), `< 400` lines in `WorkoutMasksAndCardsSection.kt`, single-line non-wrapping segmented buttons (Rule 38), and 100% clean-room test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL configure and persist the default `ZoneCardDisplayMode` independently for the Workout Summary (List / Compact Summary) and Workout Details (`TrackOnMapScreen`) views, and SHALL omit the in-card mode switcher button row in Workout Summary cards to keep the summary compact and dense (ATT-3773, amending `REQ-UI-372` and complementing `REQ-UI-386` and `REQ-UI-387`):
1. *Independent Preference Defaults & DataStore Persistence (`REQ-UI-388.1`)*:
• `WorkoutCardSectionPreferences` in `MyPreferenceManager.kt` SHALL default `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.STACKED_BAR`, and `workoutCardPreferencesFlow` SHALL fall back to `ZoneCardDisplayMode.STACKED_BAR` when `WORKOUT_CARD_ZONE_DISPLAY_MODE` is unset.
• `WorkoutDetailPreferences` in `MyPreferenceManager.kt` SHALL include `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM`, persisted via DataStore key `WORKOUT_DETAIL_ZONE_DISPLAY_MODE = stringPreferencesKey("workout_detail_zone_display_mode")` in `workoutDetailPreferencesFlow` and `setWorkoutDetailPreferences`.
2. *Independent Selectors in Advanced Settings (`REQ-UI-388.2`)*:
• In `WorkoutMasksAndCardsSection.kt`, when `workoutCardPrefs.showZoneAnalysis

### REQ-UI-389: Unified Filled-Triangle Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Across All 8 Turn Cue Vector Icons [Verified]
- **Tests**: `TST-UI-352`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable/ic_turn_*.xml`, `TurnDirectionIconContractTest.kt`
- **Rationale**: The system SHALL unify all 8 `TurnDirection` vector drawables (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`) so that every icon uses a single closed `<path>` grounded at baseline $y = 20.0\text{ dp}$ with uniform $2.0\text{ dp}$ stem/shaft thickness and an identical filled right-isosceles triangle arrowhead ($W = 8.0\text{ dp}$ base width, $D = 4.0\text{ dp}$ tip depth, $o = 3.0\text{ dp}$ wing overhang on each side of the $2.0\text{ dp}$ shaft), and SHALL center `ic_turn_left.xml`'s arrowhead strictly on the horizontal shaft axis $y = 10.0\text{ dp}$ in exact reflection symmetry with `ic_turn_right.xml` (ATT-3774, amending `REQ-UI-360` and `REQ-UI-361`):
1. *Unified Filled Right-Isosceles Triangle Arrowhead (`REQ-UI-389.1`)*:
• All 8 `ic_turn_*.xml` icons SHALL terminate with a filled right-isosceles triangle arrowhead having wing overhang $o = 3.0\text{ dp}$ perpendicular to the $2.0\text{ dp}$ shaft on each side, total base width $W = 8.0\text{ dp}$ (half-base $R = 4.0\text{ dp}$), tip depth $D = 4.0\text{ dp}$, and outer wing edge length $L = 4.0\sqrt{2} \approx 5.66\text{ dp}$:
- Cardinal icons (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`): overhang `±3dp` (`v3`/`v-3` or `h3`/`h-3`) and tip legs `(±4dp, ±4dp)`.
- Diagonal $45^\circ$/$135^\circ$ icons (`ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`): perpendicular overhang `(±2.12dp, ±2.12dp)` ($3.0/\sqrt{2} = 2.1213$) and axis-aligned outer wing legs `±5.66dp` ($4.0\sqrt{2} = 5.6568$).
2. *Turn Left & Turn Right Vertical Centering & Reflection Symmetry (`REQ-UI-389.2`)*:
• `ic_turn_right.xml` (`M7,20h2v-8c0,-0.55 0.45,-1 1,-1h5v3l4,-4l-4,-4v3H10c-1.66,0 -3,1.34 -3,3V20z`) and `ic_turn_left.xml` (`M17,20h-2v-8c0,-0.55 -0.45,-1 -1,-1H9v3l-4,-4l4,-4v3h5c1.66,0 3,1.34 3,3V20z`) SHALL center their horizontal shaft on $y \in [9, 11]$ (axis $y = 10.0$), place their arrowhead wings at $y = 6.0$ and $y = 14.0$ ($3.0\text{ dp}$ overhang above and below the shaft), place their arrowhead tips at $(19.0, 10.0)$ and $(5.0, 10.0)$ respectively, and satisfy exact horizontal reflection symmetry ($x_L + x_R = 24.0, y_L = y_R$) across all milestones.
3. *Uniform Baseline Grounding & Single-Path Construction (`REQ-UI-389.3`)*:
• All 8 `ic_turn_*.xml` icons SHALL ground their vertical base stem at $y = 20.0\text{ dp}$ with $2.0\text{ dp}$ stem width and consist of a single closed `<path>` with `android:fillColor="#FF1976D2"` in a `24dp x 24dp` (`viewportWidth="24"`, `viewportHeight="24"`) viewport.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-389`, amending the arrowhead specifications of `REQ-UI-360` (`ATT-3455`) and `REQ-UI-361` (`ATT-3493` / `ATT-3573`). Targets all 8 `app/src/main/res/drawable/ic_turn_*.xml` files and `TurnDirectionIconContractTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (`ATT-1450`), Sprint 2026-42.2 (`ATT-3455`), and Sprint 2026-42.5 (`ATT-3573`).
3. *Root Reason for Existing Formulation*: Earlier tickets refined `slight` and `sharp` turn icons in isolation, leaving `slight_*` and `straight` with stroked chevrons, `sharp_*` with a $6\text{ dp}$ triangle, `left`/`right` with a $10\text{ dp}$ triangle (and `left`'s tip off-center at $y=9$), and `u_turn` with a two-subpath $8\text{ dp} \times 5\text{ dp}$ triangle.
4. *Preservation of Core Invariants*: `24x24` viewport, `#FF1976D2` fill token, `2dp` stem width, $y=20$ baseline grounding, $45^\circ$/$90^\circ$/$135^\circ$/$180^\circ$ angular progression, and reflection symmetry across $x=12$ are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* all 8 `ic_turn_*.xml` vector drawables,
• *When* parsed and evaluated by `TurnDirectionIconContractTest.kt`,
• *Then* every icon has a single closed `<path>` grounded at $y = 20.0$ with $2.0\text{ dp}$ stem width and a filled right-isosceles triangle arrowhead of base width $W = 8.0\text{ dp} \pm 0.05$, tip depth $D = 4.0\text{ dp} \pm 0.05$, and wing overhang $o = 3.0\text{ dp} \pm 0.05$.
• *Given* `ic_turn_left.xml` and `ic_turn_right.xml`,
• *When* evaluated by `TurnDirectionIconContractTest.kt`,
• *Then* both arrowheads have upper wing $y = 6.0$, lower wing $y = 14.0$, and tip $y = 10.0$ (strictly centered on shaft $y \in [9, 11]$), and every milestone vertex satisfies $x_L + x_R = 24.0 \pm 0.01$ and $y_L = y_R \pm 0.01$.

**Invariants**: `24x24` viewport, `#FF1976D2` fill color, $2.0\text{ dp}$ stem width, $y = 20.0$ baseline grounding, reflection symmetry across all 3 pairs (`LEFT`/`RIGHT`, `SLIGHT_LEFT`/`SLIGHT_RIGHT`, `SHARP_LEFT`/`SHARP_RIGHT`), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-390: Proportional Zone Segment Weights, 10% Boundary Clipping, Under-Segment Centered Active Badge & Primary-Blue Needle in Live ZoneGaugeBar [Verified]
- **Tests**: `TST-UI-353`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`
- **Rationale**: The system SHALL scale the live cockpit `ZoneGaugeBar` linearly across $[X_{\min}, X_{\max}]$ by sizing interior zone segments proportionally to their configured span $w_i = C_i - C_{i-1}$ and clipping the outer open-ended zones ($Z_1$ and $Z_K$) to a $10\%$ boundary span ($w_{\text{cap}} = \frac{10.0}{95.0} S_{\text{inner}}$), SHALL center the active zone badge (`"Z1"`..`"Z7"`) directly beneath the active zone segment using high-contrast theme text (`MaterialTheme.colorScheme.onSurface`), and SHALL render the needle indicator in theme Primary Blue (`MaterialTheme.colorScheme.primary`) with a contrasting halo outline (ATT-3708, amending `REQ-UI-376`):
1. *Unified Linear Domain $[X_{\min}, X_{\max}]$ & Proportional Segment Weights (`REQ-UI-390.1`)*:
• Given $K$ zones ($K = 7$ for Cycling Power, $K = 5$ for Heart Rate) and $K-1$ upper cutoffs $[C_1, \dots, C_{K-1}]$:
- Interior closed zones $i \in \{2 \dots K-1\}$ SHALL have span $w_i = \max(1.0, (C_i - C_{i-1}).\text{toDouble}())$, with total interior span $S_{\text{inner}} = \sum_{i=2}^{K-1} w_i$.
- Outer open-ended zones $Z_1$ and $Z_K$ SHALL be clipped to $w_1 = w_K = w_{\text{cap}} = \frac{10.0}{95.0} \times S_{\text{inner}}$ (so that for standard Coggan Power cutoffs $[55, 75, 90, 105, 120, 150]\%$ of FTP where $S_{\text{inner}} = 95\%\text{ FTP}$, $w_1 = w_7 = 10\%\text{ FTP}$ and total linear span $S_{\text{total}} = 115\%\text{ FTP}$).
- The linear domain bounds SHALL be $X_{\min} = C_1 - w_{\text{cap}}$ and $X_{\max} = C_{K-1} + w_{\text{cap}}$, with normalized segment weights $\text{weight}_i = (w_i / S_{\text{total}}).\text{toFloat}()$ exposed via `ZoneGaugeMath.calculateSegmentWeights(cutoffs, isPower)` and stored in `ZoneGaugeState.segmentWeights`.
- `ZoneGaugeMath.calculateNeedleFraction` SHALL compute strictly linear needle position across $[X_{\min}, X_{\max}]$: $\text{needleFraction} = ((v - X_{\min}) / (X_{\max} - X_{\min})).\text{coerceIn}(0.0, 1.0).\text{toFloat}()$, aligning with exact cumulative segment weights at every cutoff $C_i$ and clamping to $0.0\text{f}$ for $v \le X_{\min}$ and $1.0\text{f}$ for $v \ge X_{\max}$.
2. *Full-Width Weighted Track & Centered Under-Segment Active Zone Label (`REQ-UI-390.2`)*:
• `ZoneGaugeBar.kt` SHALL use an outer vertical `Column(modifier = modifier.fillMaxWidth())` so the horizontal segmented track always spans the full container width.
• Each zone segment `Box` in the track `Row` SHALL apply `Modifier.weight(state.segmentWeights.getOrElse(index) { 1f })`.
• When `showBadge && state.activeZoneIndex != null`, `ZoneGaugeBar` SHALL render a matching bottom `Row(modifier = Modifier.fillMaxWidth())` with $K$ slots using the identical `Modifier.weight(state.segmentWeights.getOrElse(index) { 1f })`, centering `Text(text = "Z${state.activeZoneIndex}", color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, modifier = Modifier.testTag("zone_gauge_badge"))` directly beneath the active zone segment.
3. *High-Contrast Primary Blue Needle Indicator (`REQ-UI-390.3`)*:
• The needle indicator in `ZoneGaugeBar.kt` SHALL use a `4.dp` wide rounded rectangle filled with `MaterialTheme.colorScheme.primary` (instead of `Color.White`) surrounded by a contrasting halo outline (`TTColor.ZoneGaugeTrack` / `MaterialTheme.colorScheme.surface`), ensuring 100% hue separation against all 7 zone colors and immediate legibility on both Light and Dark/AMOLED tile backgrounds.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-390`, amending `REQ-UI-376.2`, `REQ-UI-376.3`, and `REQ-UI-376.5` (`ATT-1293`). Targets `ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`, `ZoneGaugeMathTest.kt`, and `SensorFieldZoneGaugeTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint `2026-42.5` (`ATT-1293`, commits `5416c8b2`, `20e7e00d`, `9873a200`).
3. *Root Reason for Existing Formulation*: `REQ-UI-376` originally used equal $1/K$ segment widths and piecewise-linear interpolation to avoid open-ended Z1/Z7 compression, placed the `"Z3"` badge to the right of the bar in `activeColor`, and used a `Color.White` needle fill. On-device review in Sprint `2026-42.5` revealed non-uniform needle velocity across zones, unreadable Z7 Black / Z4 Yellow badge colors, and needle camouflage on white Light Mode tiles.
4. *Preservation of Core Invariants*: DataStore `showGauge` persistence (`REQ-UI-376.1`), stale/null dimmed state (`0.35f` alpha and hidden needle, `REQ-UI-376.4`), AMOLED `outlineVariant` track border (`REQ-UI-376.5`), $O(1)$ math performance (`REQ-UI-376.6`), and 9-locale parity (`REQ-UI-376.7`) remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* standard Coggan 7-zone power cutoffs (`[55%, 75%, 90%, 105%, 120%, 150%]` of FTP),
• *When* `ZoneGaugeMath.calculateSegmentWeights` and `ZoneGaugeState` are computed,
• *Then* the 7 segments have relative weights proportional to `[10, 20, 15, 15, 15, 30, 10]` ($S_{\text{total}} = 115\%\text{ FTP}$).
• *Given* any valid zone cutoffs $[C_1, \dots, C_{K-1}]$ and linear bounds $[X_{\min}, X_{\max}]$,
• *When* `calculateNeedleFraction` is evaluated,
• *Then* the needle fraction scales strictly linearly with `value`, matches the exact cumulative segment weight at every cutoff $C_i$, and clamps to `0.0f` for $\text{value} \le X_{\min}$ and `1.0f` for $\text{value} \ge X_{\max}$.
• *Given* `showBadge = true` and a valid `activeZoneIndex`,
• *When* `ZoneGaugeBar` is rendered,
• *Then* the `"Z${activeZoneIndex}"` label is centered directly beneath the active zone segment using `MaterialTheme.colorScheme.onSurface` and the needle is rendered in `MaterialTheme.colorScheme.primary` with a contrasting outline.

**Invariants**: DataStore `showGauge` persistence, stale dimmed state (`0.35f` alpha), AMOLED `outlineVariant` track border, $O(1)$ math, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-391: Dedicated Athlete Profile Screen, Timestamped Body Metrics SQLite History, Derived W/kg Time-Series Plot & Unidirectional SettingsDataStore Projection [Verified]
- **Tests**: `TST-UI-354`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AthleteMetricsDatabaseManager.kt`, `AthleteMetricsRepository.kt`, `AthleteMetricsPlotMath.kt`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `NavRoutes.kt`, `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt`
- **Rationale**: The system SHALL provide a dedicated Athlete Profile / Body Metrics screen (`NavRoutes.ATHLETE_PROFILE`) accessible from the Navigation Drawer under `R.string.drawer__my_stuff`, backed by a SQLite time-series table (`athlete_metrics_history`) and unidirectional `AthleteMetricsRepository -> SettingsDataStore` synchronization (ATT-3704, complementing `REQ-UI-159`, `REQ-TRK-019`, `REQ-UI-373`, and `REQ-UI-385`):
1. *Navigation Drawer & Route Wiring (`REQ-UI-391.1`)*:
• The system SHALL define `R.id.drawer_athlete_profile`, vector icon `R.drawable.ic_athlete_profile` (constructed from curved geometric primitives in `res/drawable/` per Rule 37), and route `NavRoutes.ATHLETE_PROFILE = "athlete_profile"`.
• `AppNavigationDrawer.kt` (`createDrawerGroups`) SHALL include `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` in `R.string.drawer__my_stuff`, and `ATrainingTrackerApp.kt` SHALL host `AthleteProfileScreen` at `composable(NavRoutes.ATHLETE_PROFILE)`.
2. *SQLite Persistence, Bounds Validation & Symmetric DTO Mappers (`REQ-UI-391.2` — Rule 20)*:
• `AthleteMetricsDatabaseManager.kt` SHALL define `AthleteMetricType` (`WEIGHT_KG(30.0, 300.0)`, `HR_REST_BPM(30.0, 120.0)`, `FTP_WATTS(50.0, 600.0)`), `AthleteMetricsDbHelper` (`AthleteMetrics.db`, table `athlete_metrics_history` with `CHECK(value > 0)`), and `AthleteMetricEntry` with symmetric `fromCursor(Cursor)` and `fromCursorBatch(Cursor)` mappers.
3. *Unidirectional Single Source of Truth, Delete Fallback State Machine & Transactional Seeding (`REQ-UI-391.3`)*:
• `AthleteMetricsRepository` SHALL act as the single source of truth, projecting the chronologically latest entry per metric (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`) into `SettingsDataStore` on every add, edit, or delete (reverting to the next most recent remaining entry when the latest is deleted, or resetting to default/null when the last entry is deleted, and never overwriting newer baselines when a backdated entry is added).
• `ensureSeededFromDataStore()` SHALL seed existing positive `athleteWeightKgFlow`, `hrRestFlow`, and `ftpFlow` (with `ftpUpdatedAtFlow ?: nowMs`) once inside an atomic SQLite transaction.
• Saving FTP or Resting HR in `ZonesSettingsActivity` or saving Weight in `EditSensorFieldDialog` SHALL record a timestamped entry via `AthleteMetricsRepository`.
4. *Derived W/kg Precision & Time-Series Plot Math (`REQ-UI-391.4`)*:
• `AthleteMetricsPlotMath` SHALL filter series by `MetricsTimeRange` (`1M`, `3M`, `6M`, `1Y`, `ALL`), convert weight via `SettingsDataStore.LBS_PER_KG` (`%.1f`) when imperial, and compute derived `W/kg` (`F(t) / W(t)`) in `Double` precision formatted to `2` decimal places (`%.2f W/kg`).
5. *Dedicated AthleteProfileScreen UI, Locale Date Formatting & 9-Language Parity (`REQ-UI-391.5` — Rule 23, Rule 38)*:
• `AthleteProfileScreen` SHALL render a `primaryContainer` header with `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` and `+` `IconButton`, a 2x2 summary grid of `RoundedCornerShape(12.dp)` cards (Weight, Resting HR, FTP, `W/kg`) with locale-formatted dates (`DateFormat.getDateInstance`), an interactive `Canvas` time-series chart with single-line non-wrapping selector pills (`maxLines = 1, softWrap = false`), and a chronological history list with `DatePickerDialog` edit and `DeleteConfirmationDialog` delete.
• All new string resources SHALL be localized across all 9 locales with positional format specifiers.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-391`, complementing `REQ-UI-159` (`ATT-1083`), `REQ-TRK-019` (`ATT-1289`), `REQ-UI-373` (`ATT-3188`), and `REQ-UI-385` (`ATT-3777`).
2. *Historical Origin & Commit Trace*: Sprint `2026-42.4` (`ATT-1289` commit `5eee662f`, `ATT-3188` commit `c800ca58`) and Sprint `2026-42.6` (`ATT-3777` commit `5e0e50c3`).
3. *Root Reason for Existing Formulation*: `SettingsDataStore` originally stored only single scalar baselines for live tracking and zone calculation.
4. *Preservation of Core Invariants*: Unidirectional projection from `AthleteMetricsRepository` to `SettingsDataStore` preserves all live tracking scalar flows (`athleteWeightKgFlow`, `hrRestFlow`, `ftpFlow`), symmetric DTO mapping (Rule 20), non-wrapping pills (Rule 38), and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the user selects *Athlete Profile* in the Navigation Drawer and adds/edits/deletes a Weight, Resting HR, or FTP entry,
• *When* the mutation completes,
• *Then* the entry is persisted in SQLite, rendered in the time-series plot and history log, and the chronologically latest remaining entry for that metric is projected into `SettingsDataStore`.
• *Given* timestamped history entries exist for FTP and Weight,
• *When* `AthleteMetricsPlotMath` computes the `POWER_TO_WEIGHT` series,
• *Then* `W/kg` is computed as `F(t) / W(t)` at each timestamp and formatted to `2` decimal places (`%.2f`).

**Invariants**: Unidirectional data flow (`AthleteMetricsRepository -> SettingsDataStore`); symmetric `fromCursor`/`fromCursorBatch` mappers (Rule 20); single-line non-wrapping pills (Rule 38); 9-locale parity; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-392: High-Contrast Warm Earthy Brown Zone 6 Color Token & Zone 7 Visual Separation [Verified]
- **Tests**: `TST-UI-355`, `TST-UI-383`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Color.kt`, `color.xml`, `SensorFieldZoneDisplayTest.kt`, `SensorFieldZoneGaugeTest.kt`
- **Rationale**: The system SHALL define `TTColor.Zone6` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`) and `<color name="zone_6">` (`app/src/main/res/values/color.xml`) as `#8B5A2B` (`Color(0xFF8B5A2B)`, Warm Earthy Brown) so that Zone 6 is immediately distinguishable from Zone 7 (`#000000` Pure Black) with a WCAG 2.1 AA non-text contrast ratio $\ge 3.0:1$ ($3.44:1$) across all zone distribution charts, live tracking zone gauges, elevation profile gradient shading, and zone configuration screens (ATT-3696, amending the Zone 6 hex value in `REQ-UI-370` and `REQ-UI-371`):
1. *Warm Earthy Brown Token & 1:1 XML/Compose Parity (`REQ-UI-392.1`)*:
• `TTColor.Zone6` in `Color.kt` SHALL equal `Color(0xFF8B5A2B)`.
• `<color name="zone_6">` in `res/values/color.xml` SHALL equal `#8B5A2B`.
2. *WCAG 2.1 AA Non-Text Contrast Against Zone 7 Black (`REQ-UI-392.2`)*:
• The relative luminance of `TTColor.Zone6` ($L \approx 0.122$) SHALL maintain a contrast ratio $(L_6 + 0.05) / (L_7 + 0.05) \ge 3.0:1$ ($3.44:1$) against `TTColor.Zone7` (`#000000`, $L = 0.0$).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-392`, amending the `#4E342E` Zone 6 hex value in `REQ-UI-370` (`ATT-3184`) and `REQ-UI-371` (`ATT-3391`). Targets `Color.kt`, `color.xml`, `SensorFieldZoneDisplayTest.kt`, and `SensorFieldZoneGaugeTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint `2026-42.4` (`ATT-3184` commit `0da639ba`, `ATT-3391` commit `ca77b115`).
3. *Root Reason for Existing Formulation*: Material Brown 800 (`#4E342E`) was initially selected without evaluating its low relative luminance ($0.036$, $1.72:1$ contrast against `#000000`) on OLED displays.
4. *Preservation of Core Invariants*: Updating `TTColor.Zone6` and `R.color.zone_6` to `#8B5A2B` preserves 1:1 XML/Compose parity and all 7-zone calculations while raising Zone 6 vs. Zone 7 contrast to $3.44:1$ ($\ge 3.0:1$).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TTColor.Zone6` in `Color.kt` and `<color name="zone_6">` in `color.xml`,
• *When* evaluated by unit and contract tests,
• *Then* both equal `#8B5A2B` (`Color(0xFF8B5A2B)`) and achieve $\ge 3.0:1$ luminance contrast against `TTColor.Zone7` (`#000000`).

**Invariants**: 1:1 parity between `Color.kt` and `color.xml`; $\ge 3.0:1$ contrast against `Zone7`; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-393: Removal of Dead Sensor Pairing Code from Control Tracking Cockpit & Activity Navigation [Verified]
- **Tests**: `TST-UI-356`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt`, `ComposeSafePainterContractTest.kt`
- **Rationale**: The system SHALL remove all dead, unreachable ANT+ and Bluetooth LE sensor pairing UI components, parameters, ViewModel state/callbacks, and activity navigation helpers from the Control Tracking cockpit flow (`PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`), while preserving active sensor discovery (`ResearchButton` / `onSearchClicked`), connected device management (`RemoteDevices` / `ToEditDevice`), and sensor pairing in **My Sensors** (`DevicesTabbedContainerFragment` / `DeviceTypeSelectionDialog`) (ATT-3692, completing `REQ-UI-301`):
1. *Deletion of Dead `PairingButtons.kt` (`REQ-UI-393.1`)*:
• `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt` SHALL be deleted from the repository, and removed from `ComposeSafePainterContractTest.kt`.
2. *Clean `ControlTrackingScreen` Signature & Body (`REQ-UI-393.2`)*:
• `ControlTrackingScreen` in `ControlTrackingScreen.kt` (and all of its `@Preview` composables) SHALL NOT declare or pass `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, or `onCancelDeviceTypeSelection`, and SHALL NOT import or invoke `DeviceTypeSelectionDialog`.
3. *Clean `ControlTrackingViewModel` & `ControlNavigation` (`REQ-UI-393.3`)*:
• `ControlNavigation` in `ControlTrackingViewModel.kt` SHALL contain strictly `ToEditDevice` and `ShowRecoveryDialog` (omitting `ToPairing`).
• `ControlTrackingViewModel` SHALL NOT declare `_selectingProtocol`, `selectingProtocol`, `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, or `onCancelDeviceTypeSelection()`.
4. *Clean `TrackingTabsScreen` & `MainActivityWithNavigation` (`REQ-UI-393.4`)*:
• `TrackingTabsScreen.kt` SHALL NOT collect `selectingProtocol`, handle `ControlNavigation.ToPairing`, or pass pairing parameters to `ControlTrackingScreen`.
• `MainActivityWithNavigation.kt` SHALL NOT declare `fun startPairing(protocol: Protocol, deviceType: DeviceType?)`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-393`, completing the clean-up of `REQ-UI-301` (`ATT-2791`). Targets `PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt`, and `ComposeSafePainterContractTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint `2026-41.4` (`ATT-2791`).
3. *Root Reason for Existing Formulation*: `ATT-2791` removed `PairingButtons(...)` from `ControlTrackingScreen`'s layout column without pruning the leftover parameter and ViewModel/Activity plumbing.
4. *Preservation of Core Invariants*: Active sensor discovery (`hasPairedRemoteDevices`, `remoteDevices`, `onSearchClicked`, `ToEditDevice`, `ShowRecoveryDialog`) and My Sensors pairing (`DevicesTabbedContainerFragment`, `DeviceTypeSelectionDialog`) remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`,
• *When* evaluated by `ControlTrackingDeadPairingRemovalTest` and `ComposeSafePainterContractTest`,
• *Then* `PairingButtons.kt` does not exist and zero dead pairing symbols (`ToPairing`, `selectingProtocol`, `onPairingClicked`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`, `isAntSupported`, `isBluetoothSupported`, `isAntProperlyInstalled`, `startPairing`) remain in those files.

**Invariants**: Active Control Tracking sensor search/edit and My Sensors pairing MUST remain intact; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-394: ElevationProfile Empty State Rendering & Pre-Start Space Reservation on Tracking Tabs [Verified]
- **Tests**: `TST-UI-357`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `SensorGridScreen.kt`, `values*/strings.xml`, `ElevationProfileEmptyStateContractTest.kt`
- **Rationale**: The system SHALL render a themed empty-state placeholder for `ElevationProfile` on tracking tabs (`SensorGridScreen`) whenever `state.showElevationProfile == true` and fewer than 2 path points are available (`resolvedProfile.pathPoints.size < 2`), reserving the canonical `calculateElevationProfileHeight(0.0)` (`70.dp`) height (`docs/design_guidelines.md` Section 2.3) so that athletes receive immediate visual feedback when enabling the Elevation Profile toggle and experience zero layout jump when tracking begins (ATT-3779, complementing `REQ-UI-346` and `REQ-UI-355`):
1. *Additive `showEmptyState` Parameter & Canonical Height (`REQ-UI-394.1`)*:
• Both `ElevationProfile` overloads in `ElevationProfile.kt` SHALL accept `showEmptyState: Boolean = false`.
• When `pathPoints.size < 2`: if `!showEmptyState`, `ElevationProfile` SHALL return without rendering; if `showEmptyState`, `ElevationProfile` SHALL render `ElevationProfileEmptyState` with height strictly determined by `val targetHeight = fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp` when `fillParentHeight == false`), subtle horizontal reference grid lines on a `Canvas`, `Icon(imageVector = Icons.Default.Terrain, contentDescription = null)`, and `Text(text = stringResource(R.string.elevation_profile_empty_state), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
2. *Tracking Tab Unconditional ElevationProfile Wiring (`REQ-UI-394.2`)*:
• In `SensorGridScreen.kt`, the elevation profile section SHALL be gated strictly on `if (state.showElevationProfile)` (without `&& resolvedProfile.pathPoints.size >= 2`) and SHALL invoke `ElevationProfile` with `showEmptyState = true`.
3. *9-Language Localization Parity & Multi-State `@Preview`s (`REQ-UI-394.3`)*:
• `R.string.elevation_profile_empty_state` SHALL be localized across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), and `ElevationProfile.kt` SHALL provide Light and Dark `@Preview`s (`ElevationProfileEmptyStateLightPreview`, `ElevationProfileEmptyStateDarkPreview`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-394`, complementing `REQ-UI-346` (`ATT-3173`) and `REQ-UI-355` (`ATT-3199`). Targets `ElevationProfile.kt`, `SensorGridScreen.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint `2026-41.8` / `2026-41.9` (`ATT-3173`, `ATT-3199`).
3. *Root Reason for Existing Formulation*: `ElevationProfile` originally returned early on `< 2` points because it was designed for post-workout tracks, so `SensorGridScreen` suppressed the `Surface` when `pathPoints.size < 2`.
4. *Preservation of Core Invariants*: Defaulting `showEmptyState = false` preserves post-workout omission for indoor/non-GPS workouts, while `showEmptyState = true` in `SensorGridScreen` obeys Section 2.3 (`calculateElevationProfileHeight(0.0) == 70.dp`) and eliminates layout shifts on tracking start.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SensorGridScreen` with `state.showElevationProfile == true` and `resolvedProfile.pathPoints.size < 2`,
• *When* rendered,
• *Then* `ElevationProfile` is called with `showEmptyState = true` and renders `ElevationProfileEmptyState` at `calculateElevationProfileHeight(0.0)` (`70.dp`) height with `Icons.Default.Terrain` and `R.string.elevation_profile_empty_state`.
• *Given* all 9 `values*/strings.xml` files,
• *When* audited by `ElevationProfileEmptyStateContractTest`,
• *Then* `elevation_profile_empty_state` is present and non-empty in every locale.

**Invariants**: Canonical adaptive height `calculateElevationProfileHeight(0.0) == 70.dp` (Section 2.3); default `showEmptyState = false` backward compatibility; 9-locale parity; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-375: Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge [Verified]
- **Tests**: `TST-UI-338`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable/*.xml`, `SportTypeSelector.kt`, `VectorAssetResolutionContractTest.kt`
- **Rationale**: The system SHALL migrate the 16 sport type and ANT+/BLE sensor raster drawables (`bsport_bike`, `bsport_run`, `bsport_other`, `bike_cad`, `bike_pwr`, `bike_spd`, `bike_speed_and_cadence`, `hr`, `run_spd`, `temp`, `bt_bike_cad`, `bt_bike_pwr`, `bt_bike_spd`, `bt_bike_speed_and_cadence`, `bt_hr`, `bt_run`) to density-independent XML `VectorDrawable` assets in `app/src/main/res/drawable/` constructed directly from the authoritative mathematical Xfig 3.2 (`.fig`), SVG (`.svg`), and GIMP (`.xcf`) source files in `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/` and analytical geometric primitives, and purge the 106 superseded raster PNG files (including `_gray.png` and `btle_hr.png`) across all density buckets (ATT-2859, Rule 37):
1. *Direct Mathematical Translation from `.fig` / `.svg` / `.xcf` Masters (`REQ-UI-375.1` — Rule 37)*:
• `bsport_bike.xml`, `bsport_run.xml`, and `bsport_other.xml` SHALL translate the exact Xfig 3.2 circles (`1`), circular arcs (`5`), stroked polylines (`2`), and X-splines (`3`) from `logo.fig`, `Bluetooth/bike.fig`, and `Bluetooth/runner.fig` in back-to-front Z-depth order (`depth 70` down to `depth 50`) preserving their exact multi-color palettes.
• `bt_bike_cad.xml`, `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, and `bt_run.xml` SHALL translate the `1400x1400` `BT_*.xcf` master compositions combining the `#07377F` rounded-square badge with the mathematical white Xfig geometry from `bike.fig`, `runner.fig`, and `BT_HR_just_the_heart.fig` and exact vector font outlines (`android:fillType="evenOdd"`) for `cadence`, `power`, `speed`, and `s & c`.
2. *Analytical Geometric Reconstruction of ANT+ Sensor Badges (`REQ-UI-375.2` — Rule 37)*:
• `bike_cad.xml`, `bike_pwr.xml`, `bike_spd.xml`, `bike_speed_and_cadence.xml`, `hr.xml`, `run_spd.xml`, and `temp.xml` SHALL use the canonical `#000000` rounded-square badge (`96x96`, corner radius $r=10$) with `#FFFFFF` analytical geometric emblems and vector font outlines (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) with `android:fillType="evenOdd"`.
3. *Elimination of Redundant Raster & `_gray` Assets (`REQ-UI-375.3`)*:
• All 106 superseded raster PNG files across `drawable*` SHALL be deleted, and `SportTypeSelector.kt` SHALL preserve `tint = Color.Unspecified`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-375`, complementing `REQ-UI-312` (`ATT-2858`) and `REQ-UI-380` (`ATT-3529`).
2. *Historical Origin & Commit Trace*: Sprint `2026-41.5` (`ATT-2858`) and Sprint `2026-42.5` (`ATT-3529`).
3. *Root Reason for Existing Formulation*: Raster PNGs were previously duplicated into root `res/drawable/` as a stopgap before the original `.fig`/`.svg`/`.xcf` vector masters in `/home/rainer/Dropbox/workspace/` were translated.
4. *Preservation of Core Invariants*: Root XML `VectorDrawable`s satisfy `DrawableDensityFallbackAuditTest` with zero density-split risk and 1:1 visual fidelity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the 16 migrated icon resource names in `app/src/main/res/drawable/`,
• *When* evaluated by `VectorAssetResolutionContractTest`,
• *Then* each exists as a valid XML `<vector>` with curved geometric primitives (`A`/`C`) and proper `strokeWidth` or `fillType="evenOdd"` attributes, and zero `.png` files remain for those 16 icons, `bsport_*_gray`, or `btle_hr`.

**Invariants**: 1:1 visual fidelity with original `.fig`/`.xcf` masters and reference PNGs (Rule 37); `DrawableDensityFallbackAuditTest` 100% pass; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-395: Categorized & Searchable SensorType Selector in EditSensorFieldDialog [Verified]
- **Tests**: `TST-UI-358`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorCategory.java`, `SensorType.java`, `EditSensorFieldDialog.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL organize all 43 `SensorType` metrics into 7 semantic `SensorCategory` domain groups and replace the flat 43-item `SensorType` dropdown in `EditSensorFieldDialog.kt` with a categorized accordion and instant search selector (`SensorTypeSelectorSection`), adhering to `docs/design_guidelines.md` Section 3 and Section 5.8 (ATT-3852):
1. *Authoritative `SensorCategory` Domain Partition (`REQ-UI-395.1`)*:
• `com.atrainingtracker.banalservice.sensor.SensorCategory` SHALL define 7 enum constants with localized title resource IDs (`getTitleResId()`): `TIME_AND_LAPS` (`R.string.sensor_category_time_and_laps`, 5 metrics: `TIME_ACTIVE`, `TIME_TOTAL`, `TIME_LAP`, `TIME_OF_DAY`, `LAP_NR`), `SPEED_PACE_AND_DISTANCE` (`R.string.sensor_category_speed_pace_and_distance`, 4 metrics: `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`), `CADENCE_AND_RUNNING` (`R.string.sensor_category_cadence_and_running`, 2 metrics: `CADENCE`, `STRIDES`), `HEART_RATE_AND_ENERGY` (`R.string.sensor_category_heart_rate_and_energy`, 2 metrics: `HR`, `CALORIES`), `POWER_AND_PEDALING` (`R.string.sensor_category_power_and_pedaling`, 13 metrics: `POWER`, `NORMALIZED_POWER`, `POWER_TO_WEIGHT`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `TORQUE`, `PEDAL_POWER_BALANCE`, `PEDAL_SMOOTHNESS`, `PEDAL_SMOOTHNESS_L`, `PEDAL_SMOOTHNESS_R`, `TORQUE_EFFECTIVENESS_L`, `TORQUE_EFFECTIVENESS_R`), `ELEVATION_AND_NAVIGATION` (`R.string.sensor_category_elevation_and_navigation`, 9 metrics: `ALTITUDE`, `ASCENT`, `DESCENT`, `SLOPE`, `VERTICAL_SPEED`, `BEARING`, `LINE_DISTANCE_m`, `LATITUDE`, `LONGITUDE`), and `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` (`R.string.sensor_category_system_environment_and_diagnostics`, 8 metrics: `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`, `SENSORS`, `ACCUMULATED_SENSORS`, `ACCURACY`, `TEMPERATURE`, `TEMPERATURE_MIN`, `TEMPERATURE_MAX`).
• Every `SensorType` constant (`43` total) SHALL expose `public SensorCategory getCategory()`.
2. *Categorized Accordion & Search Selector in `EditSensorFieldDialog.kt` (`REQ-UI-395.2`)*:
• `EditSensorFieldDialog.kt` SHALL replace the flat `SensorType` `DropdownSelector` with `SensorTypeSelectorSection`, featuring a summary header card (showing selected category, full name, short name, and unit) that toggles an inline selector panel with a search `OutlinedTextField` (`R.string.sensor_type_search_hint`), 7 collapsible `SensorCategory` accordions (with only the active `SensorType`'s category initially expanded when search is blank), and instant case-insensitive search filtering (`SensorTypeSearchFilter.filterByQuery`) across localized full name, short name, unit, and enum name.
• Each `SensorType` option row SHALL display the localized full name, localized short name subtitle, and unit badge (`MySensorManager.getUnitIds(type)`).
3. *Bottom Sheet Scroll Safety & 9-Language Localization Parity (`REQ-UI-395.3` — Rule 23, Section 5.8)*:
• `SensorTypeSelectorSection` SHALL render as a standard `Column` without nested `verticalScroll` or `LazyColumn` inside `AppModalBottomSheet(scrollable = true)`.
• All 9 new string keys (`sensor_category_*`, `sensor_type_search_hint`, `sensor_type_no_matches`) SHALL be localized across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-395`, complementing `REQ-UI-373` (`ATT-3188`), `REQ-UI-376` (`ATT-1293`), and `REQ-TRK-019` (`ATT-1289`). Targets `SensorCategory.java`, `SensorType.java`, `EditSensorFieldDialog.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: `EditSensorFieldDialog.kt` previously used a generic `DropdownSelector` over `SensorType.entries.sortedBy { stringResource(it.fullNameId) }`.
3. *Root Reason for Existing Formulation*: `DropdownSelector` was convenient for small enums (4–6 options), whereas `SensorType` grew to 43 telemetry metrics.
4. *Preservation of Core Invariants*: All 43 `SensorType` enum identifiers, downstream `EditSensorFieldDialog` controls (Zone Display Mode, Zone Gauge, Athlete Weight for `POWER_TO_WEIGHT`, View Type, Accumulation Period, Source, Text Size), and `AppModalBottomSheet` scroll safety (Section 5.8) remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SensorType.values()` and `SensorCategory.values()`,
• *When* evaluated by unit tests,
• *Then* all 43 `SensorType` constants map to their exact `SensorCategory` (`5 + 4 + 2 + 2 + 13 + 9 + 8 = 43`).
• *Given* `EditSensorFieldDialog.kt`,
• *When* selecting a `SensorType`,
• *Then* the athlete can browse the 7 collapsible `SensorCategory` accordions or filter by search query across full name, short name, and unit without nested scroll containers.

**Invariants**: Zero nested `verticalScroll` or `LazyColumn` inside `AppModalBottomSheet` (Section 5.8); 9-locale parity; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-396: System Navigation Bar Bottom Inset Clearance in AthleteProfileScreen [Verified]
- **Tests**: `TST-UI-359`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AthleteProfileScreen.kt`, `AthleteProfileContractAndLocalizationTest.kt`
- **Rationale**: The system SHALL apply the Android system navigation bar bottom inset (`WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`) to the scrollable `LazyColumn` in `AthleteProfileScreen.kt` (`AthleteProfileContent`) in compliance with Rule 41 (`docs/design_guidelines.md` Section 5.6) so that the bottommost measurement history row or empty-history card clears the system navigation bar when scrolled to the bottom (ATT-3850, complementing `REQ-UI-391.5`):
1. *Bottom Navigation Bar Inset in `LazyColumn` `contentPadding` (`REQ-UI-396.1` — Rule 41)*:
• `AthleteProfileContent` in `AthleteProfileScreen.kt` SHALL compute `val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`.
• The main `LazyColumn` in `AthleteProfileContent` SHALL configure `contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + navBarBottomPadding)` (preserving `16.dp` horizontal and top padding and adding `navBarBottomPadding` to the `16.dp` bottom margin).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-396`, complementing `REQ-UI-391.5` (`ATT-3704`). Targets `AthleteProfileScreen.kt` and `AthleteProfileContractAndLocalizationTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint `2026-42.6` (`ATT-3704`, commit `b660ef43`).
3. *Root Reason for Existing Formulation*: `AthleteProfileContent` initially used `contentPadding = PaddingValues(16.dp)` on the `LazyColumn` without adding the edge-to-edge bottom navigation bar inset.
4. *Preservation of Core Invariants*: Preserves `.statusBarsPadding()` on the top header row, `16.dp` horizontal/top padding and `16.dp` item spacing (`docs/design_guidelines.md` Section 5.2), and all Athlete Profile summary, chart, filter, and CRUD functionality.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `AthleteProfileScreen.kt` (`AthleteProfileContent`) hosted inside edge-to-edge `MainActivityWithNavigation`,
• *When* the `LazyColumn` is scrolled to the bottom,
• *Then* its `contentPadding` includes `bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` so the bottommost item clears the system navigation bar.

**Invariants**: Rule 41 system navigation bar inset clearance; `16.dp` spacing scale (Rule 23); 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-397: Restructured 5-Group Navigation Drawer, Consolidated Equipment & Profile/Zones Destinations, and Collapsible Secondary Sections [Verified]
- **Tests**: `TST-UI-360`, `TST-UI-393`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `AthleteProfileScreen.kt`, `ZonesSettingsActivity.kt`, `ids.xml`, `values*/strings.xml`
- **Rationale**: The system SHALL declutter and restructure the main Navigation Drawer (`AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `AthleteProfileScreen.kt`, `ZonesSettingsActivity.kt`) into 5 groups with 12 permanently visible everyday items and 2 collapsible secondary configuration sections (ATT-3851, amending `REQ-UI-159` and extending `REQ-UI-391`):
1. *Consolidated "My Stuff" Group in Athlete -> Gear Order (`REQ-UI-397.1`)*:
• In `createDrawerGroups(startTrackingTitleRes: Int)`, `R.string.drawer__my_stuff` SHALL contain strictly 4 items in Athlete -> Gear order: `R.id.drawer_athlete_profile` (`R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile` — **Profile & Zones**), `R.id.drawer_my_sensors` (`R.drawable.ic_my_paired_devices`, `R.string.devices_myRemoteDevices` — **My Sensors**), `R.id.drawer_equipment` (`R.drawable.ic_equipment_combined`, `R.string.equipment_management_title` — **Equipment**), and `R.id.drawer_sport_types` (`R.drawable.ic_sports_combined`, `R.string.sport_types` — **Sport Types**).
• `R.id.drawer_equipment` SHALL map bidirectionally to `NavRoutes.EQUIPMENT = "equipment"` and open `EquipmentTabsScreen(initialTab = 0)` in `ATrainingTrackerApp.kt`, while preserving `NavRoutes.BIKES` and `NavRoutes.SHOES` for backward compatibility.
• `isDrawerItemSelected(itemId, selectedItemId)` SHALL highlight `R.id.drawer_athlete_profile` when `selectedItemId` is `R.id.drawer_athlete_profile` or `R.id.drawer_training_zones`, and `R.id.drawer_equipment` when `selectedItemId` is `R.id.drawer_equipment`, `R.id.drawer_bikes`, or `R.id.drawer_shoes`.
2. *Unified Tabbed "Profile & Zones" Screen (`REQ-UI-397.2`)*:
• `AthleteProfileScreen` (`AthleteProfileScreen.kt`) SHALL render a unified top header (`R.string.athlete_profile_title`) and a `PrimaryScrollableTabRow` + `HorizontalPager` hosting 4 tabs: **Profile** (`R.string.athlete_profile_tab_profile`, rendering `AthleteProfileContent` with `+` header action) and the 3 **Training Zones** tabs (`R.string.tab_hr_run`, `R.string.tab_hr_bike`, `R.string.tab_pwr_bike`, rendering `ZoneProfileTabContent` from `ZonesSettingsActivity.kt` with `Icons.Default.Calculate` header action).
3. *Merged "Advanced Tuning" in "Settings" Group (`REQ-UI-397.3`)*:
• `R.id.drawer_advanced_tuning` SHALL be moved into `R.string.drawer__settings` (6 items total: `Units`, `Display`, `Configure Displays`, `Import & Backup`, `Advanced Tuning`, `Privacy Policy`) and the standalone `R.string.drawer__expert_settings` group SHALL be removed.
4. *Collapsible Accordion Sections for "Online Communities" & "Settings" (`REQ-UI-397.4`)*:
• `DrawerGroup` SHALL declare `val collapsible: Boolean = false`. The top 3 groups (`drawer__training`, `drawer__maps`, `drawer__my_stuff`) SHALL have `collapsible = false`, while `prefsOnlineCommunities` and `drawer__settings` SHALL have `collapsible = true` (collapsed by default via `isDrawerGroupInitiallyExpanded`, auto-expanding when `selectedItemId` belongs to the group, with clickable header and trailing expand/collapse chevron icon).
5. *9-Language Localization Parity (`REQ-UI-397.5` — Rule 29)*:
• `drawer_athlete_profile`, `athlete_profile_title`, and `athlete_profile_tab_profile` SHALL be localized across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-397`, amending drawer item structure in `REQ-UI-159` (`ATT-1083`) and extending `REQ-UI-391` (`ATT-3704`).
2. *Historical Origin & Commit Trace*: `ATT-526`, `ATT-1083`, `ATT-2936`, and `ATT-3704` (`b660ef43`).
3. *Root Reason for Existing Formulation*: Granular drawer items were originally ported 1:1 from legacy XML menus before `EquipmentTabsScreen`, `ZoneSettingsScreen`, and `AthleteProfileScreen` were modernized into tabbed Compose destinations.
4. *Preservation of Core Invariants*: Compact `40.dp` item height (`ATT-243`), `40.dp` left-edge gesture interceptor (`REQ-UI-161`), LIFO `BackHandler` (`REQ-UI-162`), sensor-pairing zone onboarding deep links (`REQ-UI-374.3`), and bottom navigation bar inset clearance (`REQ-UI-396.1`) remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `createDrawerGroups(R.string.tab_start)`,
• *When* evaluated by unit tests,
• *Then* it returns 5 groups (`Training` = 3, `Maps` = 5, `My Stuff` = 4 in Athlete->Gear order, collapsible `Online Communities` = 4, collapsible `Settings` = 6 including `drawer_advanced_tuning`), with 12 default visible items and auto-expansion when a collapsible group contains `selectedItemId`.
• *Given* `AthleteProfileScreen` opened via **Profile & Zones**,
• *When* rendered,
• *Then* it hosts 4 tabs (`Profile`, `HR Run`, `HR Bike`, `Power Bike`) with context-sensitive header actions (`+` vs. `Calculate`).

**Invariants**: `REQ-UI-159`, `REQ-UI-374.3`, `REQ-UI-391`, `REQ-UI-396` (Rule 41), 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-398: Space-Aware Per-Segment Percentage Labels Below ZoneDistributionStackedBar [Verified]
- **Tests**: `TST-UI-361`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionStackedBar.kt`, `ZoneDistributionStackedBarTest.kt`
- **Rationale**: The system SHALL render the formatted time percentage (e.g. `24%`) centered directly beneath each active zone segment in `ZoneDistributionStackedBar.kt` if and only if that segment's horizontal width is sufficient to fit the single-line percentage label without horizontal clipping or overlapping (ATT-3853, complementing `REQ-UI-372` and `REQ-UI-387`):
1. *Pure Percentage Formatting & Width-Threshold Calculation (`REQ-UI-398.1`)*:
• `ZoneDistributionStackedBarMath` SHALL provide `formatSegmentPercentage(percentage: Float, locale: Locale = Locale.getDefault()): String` returning `String.format(locale, "%.0f%%", percentage)`.
• `ZoneDistributionStackedBarMath` SHALL provide `minRequiredSegmentWidthDp(labelText: String, charWidthDp: Float = 6.5f, horizontalPaddingDp: Float = 4f): Float` returning `labelText.length * charWidthDp + horizontalPaddingDp` and `canFitPercentageLabel(segmentWidthDp: Float, labelText: String, charWidthDp: Float = 6.5f, horizontalPaddingDp: Float = 4f): Boolean` returning `labelText.isNotEmpty() && segmentWidthDp >= minRequiredSegmentWidthDp(labelText, charWidthDp, horizontalPaddingDp)`.
2. *Aligned Weighted Segment Percentage Row (`REQ-UI-398.2`)*:
• In `ZoneDistributionStackedBar` (`ZoneDistributionStackedBar.kt`), when `activeEntries.isNotEmpty()`, the system SHALL render a horizontal `Row(modifier = Modifier.fillMaxWidth())` directly beneath the `barHeight` stacked bar `Box` whose child cells iterate over `activeEntries` with the exact same `Modifier.weight(entry.percentage)` and `1.dp` inter-segment `Spacer`s as the colored bar segments above.
• Inside each weighted cell (`BoxWithConstraints(modifier = Modifier.weight(entry.percentage), contentAlignment = Alignment.Center)`), the system SHALL format `val label = ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)` and render a single-line `Text` (`style = MaterialTheme.typography.labelSmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`, `maxLines = 1`, `softWrap = false`) centered beneath the segment ONLY when `ZoneDistributionStackedBarMath.canFitPercentageLabel(maxWidth.value, label)` is `true` and `!didOverflowWidth` (`onTextLayout`).
• Narrow segments whose allocated width is below the required threshold (or whose rendered text overflows horizontally) SHALL cleanly omit their percentage label.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-398`, complementing `REQ-UI-372` (`ATT-3390`) and `REQ-UI-387` (`ATT-3772`). Targets `ZoneDistributionStackedBar.kt` and `ZoneDistributionStackedBarTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-3390` (commit `38706240`, Sprint 2026-42.4) and `ATT-3772` (commit `cb8056d5`, Sprint 2026-42.6).
3. *Root Reason for Existing Formulation*: `REQ-UI-387` hid the multi-line `FlowRow` legend by default (`showLegend: Boolean = false`) to keep `STACKED_BAR` mode compact in Workout Summary cards, which left the colored bar segments without any numerical percentage display.
4. *Preservation of Core Invariants*: `showLegend: Boolean = false` default parameter and conditional `FlowRow` legend rendering (`REQ-UI-387`), proportional `Modifier.weight(entry.percentage)` and `1.dp` dividers (`REQ-UI-372`), single-line non-wrapping typography (`maxLines = 1, softWrap = false`, Rule 38), and 9-locale parity (`ZoneDistributionStackedBarLocalizationTest.kt`) remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active zone segment with `percentage = 24f` (`"24%"`, 3 chars -> min width `23.5dp`),
• *When* evaluated at `segmentWidthDp = 40f` vs. `segmentWidthDp = 15f`,
• *Then* `ZoneDistributionStackedBarMath.canFitPercentageLabel` returns `true` for `40f` (rendering `"24%"` centered beneath the segment) and `false` for `15f` (omitting the label).
• *Given* `ZoneDistributionStackedBar` with multiple active zones,
• *When* rendered,
• *Then* the percentage label row aligns 1:1 with the bar segments above via matching `Modifier.weight(entry.percentage)` and `1.dp` inter-segment spacers.

**Invariants**: `REQ-UI-372`, `REQ-UI-387`, Rule 38 (`maxLines = 1, softWrap = false`), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-399: Critical Power (CP), W' (Anaerobic Work Capacity), and W'bal (Anaerobic Battery) Across Athlete Profile, Live Cockpit, and Aftermath [Verified]
- **Tests**: `TST-UI-362`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PowerPhysiologyCalculator.kt`, `AthleteMetricsDatabaseManager.kt`, `SettingsDataStore.kt`, `AthleteMetricsRepository.kt`, `AthleteMetricsPlotMath.kt`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `SensorType.java`, `CyclingPowerMetricsDevice.java`, `TrackerService.java`, `LegacyImportEngine.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `TelemetryMetricGraph.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL support Critical Power ($CP$, in W), $W'$ (Anaerobic Work Capacity, in kJ), and Skiba's 1-Hz $W'_{\text{bal}}$ (Anaerobic Battery, in kJ and $\%$) across the Athlete Profile, Live Cockpit (`CyclingPowerMetricsDevice`), Aftermath Workout Summaries/Details/Telemetry Graph, and Legacy File Imports (`LegacyImportEngine`) (ATT-3862, extending `REQ-UI-391` and `REQ-TRK-021`):
1. *Athlete Profile Baselines, Unidirectional Sync, Fallback & 2-Point Estimator (`REQ-UI-399.1`)*:
• `AthleteMetricType` in `AthleteMetricsDatabaseManager.kt` SHALL define `CP_WATTS("CP_WATTS", 50.0, 1000.0)` and `W_PRIME_KJ("W_PRIME_KJ", 5.0, 60.0)`.
• `SettingsDataStore` and `SettingsDataStoreJavaHelper` SHALL persist `KEY_CYCLING_CP_WATTS` (`Int?`) and `KEY_CYCLING_W_PRIME_KJ` (`Float?`) and provide canonical fallback resolvers `getEffectiveCpWatts(context)` (returning `cpWatts` when $> 0$, else falling back to `getFtp(context)`) and `getEffectiveWPrimeKj(context)` (returning `wPrimeKj` when $> 0\text{f}$, else defaulting to `20.0f` kJ).
• `AthleteMetricsRepository.syncLatestToDataStoreLocked()` SHALL synchronize the latest `CP_WATTS` and `W_PRIME_KJ` entries unidirectionally to `SettingsDataStore`.
• `AthleteMetricsPlotMath` and `PowerPhysiologyCalculator` SHALL provide a 2-point work-time solver `estimateCpAndWPrime(p1Watts, t1Sec, p2Watts, t2Sec)` solving $W_i = P_i \cdot t_i = CP \cdot t_i + W'$, exposed in `AthleteProfileScreen` (`PlotMetricType.CP`, `PlotMetricType.W_PRIME`, summary cards, and a 2-point calculator helper inside `MetricEntryDialog`).
2. *Skiba 1-Hz $W'_{\text{bal}}$ Physiology Engine & Live Cockpit Sensor (`REQ-UI-399.2`)*:
• `PowerPhysiologyCalculator` (`com.atrainingtracker.trainingtracker.physiology`) SHALL implement Skiba's differential $W'_{\text{bal}}$ algorithm (Rule 36 — DRY): for $P(t) > CP$, $W'_{\text{bal}}(t) = \max(0, W'_{\text{bal}}(t-\Delta t) - (P(t)-CP)\Delta t)$; for $P(t) \le CP$, $\tau_{W'}(t) = 546 \cdot e^{-0.01(CP - P(t))} + 316$ and $W'_{\text{bal}}(t) = W' - (W' - W'_{\text{bal}}(t-\Delta t))e^{-\Delta t / \tau_{W'}(t)}$; with $W'_{\text{bal},\%}(t) = (W'_{\text{bal}}(t)/W') \times 100$.
• `SensorType.java` SHALL define `W_PRIME_BAL(SensorCategory.POWER_AND_PEDALING, R.string.w_prime_bal, R.string.w_prime_bal_short, R.string.units_work_kj, SensorValueType.DOUBLE, new PowerToWeightFormatter(), false)` (total sensors = `44`, `POWER_AND_PEDALING` = `14`).
• `CyclingPowerMetricsDevice.java` SHALL maintain live $W'_{\text{bal}}$ in Joules, initializing to $W'$ on `resetSession()`, stepping at 1 Hz (including exponential reconstitution at $P = 0\text{ W}$ when paused), and emitting `mWPrimeBalSensor` in kJ.
3. *Aftermath Summaries, Interactive Telemetry Chart & Import Recalculation (`REQ-UI-399.3`)*:
• `TrackerService.finalizeLiveSession()` and `LegacyImportEngine.recalculateStats()` SHALL persist `SensorType.W_PRIME_BAL` (`MIN`, `AVG`, `END`) extrema.
• `WorkoutData` and `WorkoutDataMapper` SHALL expose `minWPrimeBalKj: Double?`, `minWPrimeBalPct: Double?`, and `isEmptyTankWarning: Boolean` ($\min W'_{\text{bal},\%} < 10.0\%$), displayed in `WorkoutSummary.kt` and `TrackOnMapScreen.kt` when available.
• `PathPoint` (`MapModels.kt`), `WorkoutRepository.loadTrackPoints()`, and `TelemetryMetricGraph.kt` (`TelemetryMetricType.W_PRIME_BAL`) SHALL support interactive $W'_{\text{bal}}$ (`kJ`) curve inspection, gated on valid recorded power telemetry (`TelemetryMetricUtils.hasWPrimeBalData(path)` requiring `hasPowerData(path)`) to suppress phantom flat-line curves on power-less activities.
4. *9-Language Localization Parity (`REQ-UI-399.4`)*:
• All new string resources (`w_prime_bal`, `w_prime_bal_short`, `athlete_profile_metric_cp`, `athlete_profile_metric_w_prime`, `athlete_profile_cp_estimator_*`, `workout_w_prime_bal_min_format`, `workout_w_prime_empty_tank`) SHALL exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-399`, extending `REQ-UI-391` (`ATT-3704`) and `REQ-TRK-021` (`ATT-1291`).
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-1291`) and Sprint 2026-42.6 (`ATT-3704`).
3. *Root Reason for Existing Formulation*: Earlier sprints implemented FTP-based Coggan metrics (`NP`, `IF`, `TSS`) and basic Athlete Profile history (`WEIGHT_KG`, `HR_REST_BPM`, `FTP_WATTS`) before introducing anaerobic capacity ($CP$, $W'$, $W'_{\text{bal}}$).
4. *Preservation of Core Invariants*: Existing `AthleteMetricType` entries, `CyclingPowerMetricsDevice` `NP`/`IF`/`TSS` warm-up and pause semantics (`REQ-TRK-023`), and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* two maximal efforts ($P_1 = 420\text{ W}$ for $t_1 = 180\text{ s}$ and $P_2 = 320\text{ W}$ for $t_2 = 720\text{ s}$),
• *When* `PowerPhysiologyCalculator.estimateCpAndWPrime(420.0, 180, 320.0, 720)` is evaluated,
• *Then* it returns $CP = 286.67\text{ W}$ (`287 W`) and $W' = 24.0\text{ kJ}$.
• *Given* $CP = 300\text{ W}$ and $W' = 20.0\text{ kJ}$,
• *When* an athlete rides at $400\text{ W}$ for $100\text{ s}$ and then recovers at $200\text{ W}$ for $100\text{ s}$,
• *Then* $W'_{\text{bal}}$ depletes linearly to $10.0\text{ kJ}$ ($50\%$) and reconstitutes exponentially with $\tau_{W'} = 546 \cdot e^{-1.0} + 316 \approx 516.86\text{ s}$ to $\approx 11.76\text{ kJ}$, and `minWPrimeBalKj` is $10.0\text{ kJ}$.

**Invariants**: Rule 36 (DRY single source of truth in `PowerPhysiologyCalculator`), `SensorCategoryAndSelectorContractTest` lockstep (`44` total sensors), 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-400: Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) Calculation, Persistence, and Aftermath Display [Verified]
- **Tests**: `TST-UI-363`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PowerPhysiologyCalculator.kt`, `ExtremaType.java`, `TrackerService.java`, `LegacyImportEngine.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`, `WorkoutDetails.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL calculate, persist, and display Efficiency Factor ($\text{EF}$) and Aerobic Decoupling ($\text{Pw:Hr }\%$ for cycling/power, $\text{Pa:Hr }\%$ for running/speed) across `PowerPhysiologyCalculator`, live session finalization (`TrackerService`), TCX/GPX/FIT file imports (`LegacyImportEngine`), and Aftermath Workout Summaries and Workout Details (`WorkoutData`, `WorkoutDataMapper`, `WorkoutDetailsData`, `WorkoutDetails`) (ATT-3861, extending `REQ-UI-399` and `REQ-STB-013`):
1. *Canonical Physiology Domain Calculation (`REQ-UI-400.1` — Rule 36 & Rule 40)*:
• `PowerPhysiologyCalculator` SHALL implement pure, stateless functions `calculateNormalizedPower(powerSamplesWatts)` (30s rolling 4th-power mean with growing $1..29\text{ s}$ warm-up window), `calculateEfficiencyFactorPower(npWatts, avgHrBpm)` ($\text{NP} / \text{Avg\_HR}$ in $\text{W/bpm}$), `calculateEfficiencyFactorSpeed(avgSpeedMps, avgHrBpm)` ($(\text{avgSpeedMps} \times 60.0) / \text{Avg\_HR}$ in $\text{m/min/bpm}$), `calculateAerobicDecouplingFromEf(efFirstHalf, efSecondHalf)` ($(\text{EF}_1 - \text{EF}_2) / \text{EF}_1 \times 100.0$), and `computeAerobicEfficiencyAndDecoupling(powerSamplesWatts, speedSamplesMps, hrSamplesBpm, bSportType)` splitting active paired samples into equal halves $H_1$ and $H_2$ and flagging $\text{Decoupling }\% \ge 5.0\%$ as `isHighDrift = true`.
2. *Database Persistence in Live Finalization & File Imports (`REQ-UI-400.2`)*:
• `ExtremaType.java` SHALL define `EFFICIENCY_FACTOR(R.string.efficiency_factor_short)` and `AEROBIC_DECOUPLING(R.string.aerobic_decoupling_short)`.
• `LegacyImportEngine.recalculateStats()` and `TrackerService.finalizeLiveSession()` SHALL compute `AerobicEfficiencyResult` from the workout's recorded sample streams and persist `ExtremaType.EFFICIENCY_FACTOR` and `ExtremaType.AEROBIC_DECOUPLING` on `SensorType.POWER` (`Pw:Hr`) or `SensorType.SPEED_mps` (`Pa:Hr`).
3. *Symmetric DTO Mapping & Aftermath UI Presentation (`REQ-UI-400.3` — Rule 20 & Rule 23)*:
• `WorkoutData` and `WorkoutDetailsData` SHALL expose `efficiencyFactor: Double?`, `aerobicDecouplingPct: Double?`, `isPowerBasedEfficiency: Boolean`, and `isAerobicDecouplingHighDrift: Boolean`, populated symmetrically in `WorkoutDataMapper.fromCursor` and `WorkoutDataMapper.fromSnapshot` via `resolveAerobicEfficiencySummary(...)` and rendered in `WorkoutDetails.kt` with a semantic Coupled ($< 5.0\%$) vs. Drift ($\ge 5.0\%$) badge.
4. *9-Language Localization Parity (`REQ-UI-400.4`)*:
• All new string resources (`efficiency_factor_short`, `aerobic_decoupling_short`, `workout_ef_pwhr_format`, `workout_ef_pahr_format`, `workout_ef_only_pw_format`, `workout_ef_only_pa_format`, `workout_decoupling_coupled`, `workout_decoupling_drift`) SHALL exist across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-400`, extending `REQ-UI-399` (`ATT-3862`) and `REQ-STB-013` (`ATT-2309`).
2. *Historical Origin & Commit Trace*: Sprint 2026-42.7 (`ATT-3862` commit `61ccb801`).
3. *Root Reason for Existing Formulation*: `WorkoutSummaries` previously persisted only univariate sensor extrema (`MIN`, `AVG`, `MAX`) without cross-sensor aerobic efficiency (`NP / Avg_HR`, `Speed / Avg_HR`) or first-half vs. second-half cardiac drift (`Pw:Hr %`, `Pa:Hr %`).
4. *Preservation of Core Invariants*: Storing `EFFICIENCY_FACTOR` and `AEROBIC_DECOUPLING` via `ExtremaType` requires zero SQLite schema changes (`DB_VERSION = 24`), preserves `SensorType` count (`44`), and maintains Rule 20 DTO mapping symmetry.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a cycling workout with $H_1$ at $240\text{ W} / 150\text{ bpm}$ ($\text{EF}_1 = 1.60$) and $H_2$ at $240\text{ W} / 160\text{ bpm}$ ($\text{EF}_2 = 1.50$),
• *When* `computeAerobicEfficiencyAndDecoupling` is evaluated,
• *Then* $\text{Decoupling }\% = 6.25\%$, `isPowerBased = true`, and `isHighDrift = true` ($\ge 5.0\%$).
• *Given* persisted `EFFICIENCY_FACTOR` and `AEROBIC_DECOUPLING` extrema records,
• *When* mapped via `fromCursor` (single & batch) and `fromSnapshot`,
• *Then* all three paths produce identical `WorkoutData` and `WorkoutDetailsData` fields.

**Invariants**: Rule 20 cursor/snapshot symmetry, Rule 36 DRY calculator in `PowerPhysiologyCalculator`, 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-401: Performance Management Chart (PMC) for Fitness (CTL), Fatigue (ATL), Form / Race-Readiness (TSB), Workload Ratio (ACWR), and Weekly Ramp Rate [Verified]
- **Tests**: `TST-UI-364`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PowerPhysiologyCalculator.kt`, `WorkoutSummariesDatabaseManager.java`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL compute and display a longitudinal Performance Management Chart (PMC) modeling Chronic Training Load ($\text{CTL}$), Acute Training Load ($\text{ATL}$), Training Stress Balance ($\text{TSB}$), Acute:Chronic Workload Ratio ($\text{ACWR}$), and 7-Day Fitness Ramp Rate ($\Delta \text{CTL} / 7\text{d}$) with multi-sport Heart-Rate fallback ($\text{hrTSS}$ / Banister $\text{TRIMP}$) across `PowerPhysiologyCalculator`, `WorkoutSummariesDatabaseManager`, `AthleteProfileViewModel`, and `AthleteProfileScreen` (ATT-3859, extending `REQ-UI-391`, `REQ-TRK-021`, and `REQ-MIG-039`):
1. *Canonical Impulse-Response PMC & Multi-Sport $\text{hrTSS}$ Domain Engine (`REQ-UI-401.1` — Rule 36)*:
• `PowerPhysiologyCalculator.kt` SHALL define `TsbReadinessZone` (`FRESH_RACE_READY` for $\text{TSB} \ge +5.0$, `NEUTRAL` for $-10.0 \le \text{TSB} < +5.0$, `OPTIMAL_OVERLOAD` for $-30.0 \le \text{TSB} < -10.0$, `HIGH_FATIGUE_RISK` for $\text{TSB} < -30.0$), `AcwrZone` (`UNDER_TRAINING` for $\text{ACWR} < 0.80$, `SWEET_SPOT` for $0.80 \le \text{ACWR} \le 1.30$, `HIGH_LOAD_CAUTION` for $1.30 < \text{ACWR} \le 1.50$, `DANGER_ZONE` for $\text{ACWR} > 1.50$), `WorkoutTrainingLoadSample`, `PmcDailyPoint`, and `PmcSummaryResult`.
• `calculateHrTss(activeTimeSeconds, avgHrBpm, hrRestBpm, hrMaxBpm)` SHALL estimate training stress for non-power workouts with valid HR using Banister's $\text{TRIMP}$ normalized to 1 hour at Lactate Threshold ($HR_{r,\text{thr}} = 0.85$): $HR_r = \text{clamp}\left(\frac{HR_{\text{avg}} - HR_{\text{rest}}}{HR_{\text{max}} - HR_{\text{rest}}}, 0.0, 1.0\right)$ and $\text{hrTSS} = 100.0 \cdot \frac{(T_{\text{active}} / 60.0) \cdot HR_r \cdot e^{1.92 \cdot HR_r}}{60.0 \cdot 0.85 \cdot e^{1.92 \cdot 0.85}}$.
• `resolveWorkoutTrainingLoad(...)` SHALL resolve workout training stress via 3 tiers: (1) stored `powerTss`, (2) power-derived `TSS` from `NP` (or `avgPower`) + `activeTimeSeconds` + `ftpWatts`, or (3) `hrTSS` from `avgHrBpm` + `activeTimeSeconds` + `hrRestBpm` / `hrMaxBpm`.
• `computePerformanceManagementChart(...)` SHALL aggregate daily training stress $TSS_t$ across calendar days ($t = \lfloor \text{timestampMs} / 86{,}400{,}000 \rfloor$, with $TSS_t = 0.0$ on rest days) through `nowMs`, evaluating $\text{CTL}_t = \text{CTL}_{t-1} + \frac{TSS_t - \text{CTL}_{t-1}}{42.0}$, $\text{ATL}_t = \text{ATL}_{t-1} + \frac{TSS_t - \text{ATL}_{t-1}}{7.0}$, $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$, $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$ (when $\text{CTL}_t > 0$), and $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$ (flagging `isAggressiveRampUp = rampRate7d > 8.0`).
2. *Batch Workout Load Retrieval (`REQ-UI-401.2`)*:
• `WorkoutSummariesDatabaseManager.java` SHALL provide `getWorkoutTrainingLoadRecords()` returning a list of `WorkoutTrainingLoadSample` records for all finished workouts (`FINISHED = 1`).
3. *Athlete Profile PMC Summary Cards & Interactive Multi-Curve Chart (`REQ-UI-401.3` — Rule 23 & Rule 38)*:
• `AthleteProfileUiState` and `AthleteProfileViewModel` SHALL expose `pmcSummary: PmcSummaryResult` and `pmcPlotPoints: List<PmcDailyPoint>` filtered by `selectedTimeRange`.
• `AthleteProfileScreen.kt` SHALL render `PerformanceManagementChartCard` displaying current status summary badges for **Fitness (CTL)**, **Fatigue (ATL)**, **Form (TSB)** (with `TsbReadinessZone` badge), **Workload Ratio (ACWR)** (with `AcwrZone` badge), and **7d Ramp Rate** (`%1$+.1f CTL/wk` with `isAggressiveRampUp` warning badge), together with a multi-line `PerformanceManagementChartCanvas` plotting `CTL`, `ATL`, and `TSB`.
4. *9-Language Localization Parity (`REQ-UI-401.4`)*:
• All 17 `athlete_profile_pmc_*` string resources SHALL exist and be non-blank across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-401`, extending `REQ-UI-391` (`ATT-3704`), `REQ-TRK-021` (`ATT-1291`), and `REQ-MIG-039` (`ATT-3860`).
2. *Historical Origin & Commit Trace*: Sprint 2026-42.6 (`ATT-3704`, commit `b660ef43`) and Sprint 2026-42.7 (`ATT-3860`, commit `e5d9225e`).
3. *Root Reason for Existing Formulation*: `AthleteProfileScreen.kt` initially plotted only discrete athlete baseline measurements (`WEIGHT`, `HR_REST`, `FTP`, `CP`, `W_PRIME`, `POWER_TO_WEIGHT`) stored in `AthleteMetrics.db`, without querying longitudinal workout training stress from `WorkoutSummaries.db`.
4. *Preservation of Core Invariants*: Existing 4-tab `Profile & Zones` layout, `WindowInsets.navigationBars` bottom padding (Rule 41), and single-metric history plot in `AthleteProfileScreen.kt` remain 100% intact with zero database schema migrations.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a daily training load sequence (e.g., $100\text{ TSS/day}$ for 42 days followed by rest days),
• *When* `PowerPhysiologyCalculator.computePerformanceManagementChart` is evaluated,
• *Then* $\text{CTL}_t$, $\text{ATL}_t$, $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$, $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$, and $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$ match the exact EWMA equations and classify `TsbReadinessZone`, `AcwrZone`, and `isAggressiveRampUp` accurately.
• *Given* a workout without power data ($3600\text{ s}$ at threshold heart rate $HR_r = 0.85$),
• *When* `PowerPhysiologyCalculator.calculateHrTss` is evaluated,
• *Then* $\text{hrTSS} = 100.0$, matching 1 hour at FTP.

**Invariants**: Rule 36 DRY domain calculator, Rule 41 bottom inset clearance, 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-402: Symmetric DTO Mapping & Aftermath Display of Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) in Workout Summaries and Workout Details [Verified]
- **Tests**: `TST-UI-365`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Rationale**: The system SHALL map and display session-level power-based training metrics — Normalized Power ($\text{NP}$, in $\text{W}$), Intensity Factor ($\text{IF} = \text{NP} / \text{FTP}$), Training Stress Score ($\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$), Mechanical Work ($\text{Work}$, in $\text{kJ}$), Power-to-Weight ($\text{W/kg}$), and Variability Index ($\text{VI} = \text{NP} / \text{Avg\_Power}$) — across `WorkoutData`, `WorkoutDetailsData`, `WorkoutDataMapper`, and `WorkoutDetails` (shared by `WorkoutSummary` and `TrackOnMapScreen` / `MapDetailLayout`) (ATT-3858, extending `REQ-MIG-039`, `REQ-UI-399`, `REQ-UI-400`, and `REQ-STB-013`):
1. *DTO Properties & Symmetric Resolution (`REQ-UI-402.1` — Rule 20)*:
• `WorkoutData` and `WorkoutDetailsData` SHALL expose `normalizedPowerWatts: Double? = null`, `intensityFactor: Double? = null`, `tss: Double? = null`, `workKj: Double? = null`, `avgPowerToWeight: Double? = null`, `maxPowerToWeight: Double? = null`, and `variabilityIndex: Double? = null`.
• `WorkoutDataMapper` SHALL define `internal data class PowerTrainingSummary(...)` and `internal fun resolvePowerTrainingSummary(...)`, invoked symmetrically in both `fromCursor(cursor)` and `fromSnapshot(snapshot, batch)`.
• `resolvePowerTrainingSummary(...)` SHALL prioritize `ExtremaType.END` (falling back to `ExtremaType.AVG` / `ExtremaType.MAX`) for cumulative session metrics (`SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, `SensorType.TSS`, `SensorType.WORK_KJ`), read `ExtremaType.AVG` and `ExtremaType.MAX` for `SensorType.POWER_TO_WEIGHT` and `SensorType.POWER`, compute `variabilityIndex = PowerPhysiologyCalculator.calculateVariabilityIndex(np, avgPower)`, and provide an on-the-fly fallback from `avgPowerWatts`, `maxPowerWatts`, `activeTimeSec`, `effectiveFtpWatts`, and `effectiveWeightKg` for historical power workouts.
2. *Shared Aftermath UI Presentation (`REQ-UI-402.2` — Rule 23)*:
• `WorkoutDetails.kt` SHALL render a dedicated Power Training Metrics summary section whenever power training metrics are present (`data.normalizedPowerWatts != null

### REQ-UI-403: Progressive Disclosure of Physiology & Training Models in Advanced Settings, Profile & Zones, Live Cockpit Sensor Selector, and Aftermath Views [Verified]
- **Tests**: `TST-UI-366`, `TST-UI-388`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TuningPreferencesDataStore.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `PhysiologyModelsSection.kt`, `WorkoutDetails.kt`, `WorkoutSummary.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `AthleteProfileScreen.kt`, `AthleteProfileViewModel.kt`, `EditSensorFieldViewModel.kt`, `EditSensorFieldDialog.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL provide a dedicated **Physiology & Training Models** (*Physiologie & Trainingsmodelle*) accordion category (`TuningSection.PHYSIOLOGY_MODELS`, `PhysiologyModelsSection.kt`) in **Advanced Settings** (`AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`) backed by `TuningPreferencesDataStore` (`TuningConfig`) to toggle the UI visibility of four sports-science schools while preserving unconditional background calculation and SQLite persistence (ATT-3929, extending `REQ-UI-262`, `REQ-UI-399`, `REQ-UI-400`, `REQ-UI-401`, and `REQ-UI-402`):
1. *DataStore Persistence & Default Configuration (`REQ-UI-403.1`)*:
• `TuningPreferencesDataStore` and `TuningConfig` SHALL define and persist 4 boolean toggles:
- `enableCogganPowerMetrics: Boolean = true` (Default: `true` / ON — `NP`, `IF`, `TSS`, `VI`).
- `enablePmcModel: Boolean = false` (Default: `false` / OFF — `PMC`: `CTL`, `ATL`, `TSB`, `ACWR`, `Ramp Rate`).
- `enableAerobicEfficiencyModel: Boolean = false` (Default: `false` / OFF — `EF`, `Pw:Hr %`, `Pa:Hr %`).
- `enableCriticalPowerModel: Boolean = false` (Default: `false` / OFF — `CP`, `W'`, `W'bal`).
• `resetToDefaults()` SHALL restore these 4 defaults (`true`, `false`, `false`, `false`).
2. *Advanced Settings Accordion Category & `< 400` Line Modularity (`REQ-UI-403.2` — `REQ-UI-262`)*:
• `TuningSection` (`AdvancedTuningAccordion.kt`) SHALL include `PHYSIOLOGY_MODELS`, with `buildSectionSummary` rendering a localized summary of active models.
• `categories/PhysiologyModelsSection.kt` SHALL render 4 Material 3 `Switch` toggle rows with descriptive titles and subtitles.
• Every `.kt` file in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` SHALL strictly remain `< 400` lines (`AdvancedTuningModularityTest`).
3. *Progressive UI Gating Across Consumer Surfaces (`REQ-UI-403.3`)*:
• **Basic Physics Always Visible in Cockpit & Profile**: Instantaneous/Avg/Max `Power (W)`, `Mechanical Work (kJ)`, and `Power-to-Weight (W/kg)` SHALL remain available in live cockpit tiles (`SensorType`) and profile statistics whenever power data is present regardless of model toggles; summary rows in `WorkoutDetails` are governed by `REQ-UI-424`.
• **Coggan Power Metrics (`enableCogganPowerMetrics`)**: Gates `NP`, `IF`, `TSS`, and `VI` in `WorkoutDetails.kt` and filters `SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, and `SensorType.TSS` in `EditSensorFieldViewModel.kt` / `EditSensorFieldDialog.kt` when `false`.
• **PMC (`enablePmcModel`)**: Gates `PerformanceManagementChartCard` in `AthleteProfileScreen.kt` when `false`.
• **Aerobic Efficiency & Decoupling (`enableAerobicEfficiencyModel`)**: Gates `EF` and `Pw:Hr %` / `Pa:Hr %` in `WorkoutDetails.kt` when `false`.
• **Critical Power & Anaerobic Battery (`enableCriticalPowerModel`)**: Gates `CP` and `W'` summary cards (restoring the 2x2 grid when `false`), `PlotMetricType.CP` / `PlotMetricType.W_PRIME` plot pills, and `AthleteMetricType.CP_WATTS` / `AthleteMetricType.W_PRIME_KJ` history filter pills in `AthleteProfileScreen.kt`; gates `Min W'bal` and `Empty Tank` warning in `WorkoutDetails.kt`; gates `TelemetryMetricType.W_PRIME_BAL` telemetry charts in `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `WorkoutSummary.kt`; and filters `SensorType.W_PRIME_BAL` in `EditSensorFieldViewModel.kt` / `EditSensorFieldDialog.kt` when `false`.
4. *Unconditional Background Calculation & 9-Language Localization Parity (`REQ-UI-403.4`)*:
• Background recording (`CyclingPowerMetricsDevice`, `TrackerService`) and file import calculation (`LegacyImportEngine`) SHALL remain unconditionally active so historical workouts retain complete telemetry.
• All new string resources (`tuning_cat_physiology_*`, `tuning_physiology_*`) SHALL exist across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-403`, extending `REQ-UI-262` (`ATT-3325`), `REQ-UI-399` (`ATT-3862`), `REQ-UI-400` (`ATT-3861`), `REQ-UI-401` (`ATT-3859`), and `REQ-UI-402` (`ATT-3858`).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.10 (`ATT-3325`) and Sprint 2026-42.7 (`ATT-3858`..`ATT-3862`). Refined in Sprint 2026-43.3 (`ATT-4432`) to reaffirm the 4 canonical models and clarify basic physics scope.
3. *Root Reason for Existing Formulation*: Physiology models were initially surfaced unconditionally upon implementation in Sprint 2026-42.7 prior to introducing progressive disclosure toggles.
4. *Preservation of Core Invariants*: Unconditional background persistence, basic physics (`Power`, `Work kJ`, `W/kg`) visibility in cockpit tiles and profile, `< 400` line modularity cap in `ui/settings/tuning/`, 9-locale parity, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* default `TuningConfig()`,
• *When* inspected without user overrides,
• *Then* `enableCogganPowerMetrics == true`, `enablePmcModel == false`, `enableAerobicEfficiencyModel == false`, and `enableCriticalPowerModel == false`.
• *Given* `enableCogganPowerMetrics == false`,
• *When* live cockpit tiles or athlete profile are rendered with power data,
• *Then* `Work (kJ)` and `W/kg` remain available while `NP`, `IF`, `TSS`, and `VI` are hidden.
• *Given* `enableCriticalPowerModel == false` and `enablePmcModel == false`,
• *When* `AthleteProfileScreen` renders the Profile tab,
• *Then* the summary grid displays the 2x2 basic metrics (`Weight`, `Resting HR`, `FTP`, `W/kg`) without `CP`/`W'` cards, omits `CP`/`W'` plot and history filter chips, and hides `PerformanceManagementChartCard`.

**Invariants**: Unconditional background persistence, `< 400` line modularity limit in `ui/settings/tuning/`, 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-404: AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails (`docs/design_guidelines.md` Section 5.10) [Verified]
- **Tests**: `TST-UI-367`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
3. *Aerobic Efficiency & Decoupling Row (`AerobicEfficiencyRow`, `REQ-UI-404.3`)*:
• Gated on `enableAerobicEfficiencyModel && data.efficiencyFactor != null`.
• Left header: `R.string.workout_domain_efficiency` + `R.drawable.ic_cardio_load` (`24.dp`).
• Right row: 2 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns:
- **EF**: `label = stringResource(R.string.workout_ef_short)`, `value = stringResource(if (data.isPowerBasedEfficiency) R.string.workout_ef_value_pw_format else R.string.workout_ef_value_pa_format, data.efficiencyFactor)`.
- **Pw:Hr / Pa:Hr** (when `data.aerobicDecouplingPct != null`): `label = stringResource(if (data.isPowerBasedEfficiency) R.string.workout_pwhr_short else R.string.workout_pahr_short)`, `value = stringResource(R.string.workout_decoupling_pct_format, data.aerobicDecouplingPct)`, `secondaryValue = stringResource(if (data.isAerobicDecouplingHighDrift) R.string.workout_decoupling_drift else R.string.workout_decoupling_coupled)`.
4. *Critical Power / Anaerobic Battery Row (`AnaerobicBatteryRow`, `REQ-UI-404.4`)*:
• Gated on `enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
• Left header: `R.string.w_prime_bal_short` + `R.drawable.ic_battery_full` (`24.dp`).
• Right row: 2 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns:
- **Min W'bal**: `label = stringResource(R.string.workout_w_prime_min_kj_label)`, `value = stringResource(R.string.workout_w_prime_min_kj_value_format, data.minWPrimeBalKj)`.
- **Min W'bal %**: `label = stringResource(R.string.workout_w_prime_min_pct_label)`, `value = stringResource(R.string.workout_w_prime_min_pct_value_format, data.minWPrimeBalPct)`, `secondaryValue = if (data.isEmptyTankWarning) stringResource(R.string.workout_w_prime_empty_tank) else null`.
5. *Prohibition of `FlowRow` Pills & 9-Language Localization Parity (`REQ-UI-404.5`)*:
• `WorkoutDetails.kt` SHALL NOT use `FlowRow` or `surfaceContainerHighest` pill badges.
• All new domain/metric label and value format string resources SHALL be localized across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-404`, amending the UI layout of `REQ-UI-399.3` (`ATT-3862`), `REQ-UI-400.3` (`ATT-3861`), and `REQ-UI-402.2` (`ATT-3858`), and complementing `REQ-UI-403` (`ATT-3929`).
2. *Historical Origin & Commit Trace*: Sprint 2026-42.7 (`ATT-3862`, `ATT-3861`, `ATT-3858`) and Sprint 2026-42.8 (`ATT-3929`, commit `ad82d34b`).
3. *Root Reason for Existing Formulation*: Physiology summary rows in `WorkoutDetails.kt` originally used inline text and `FlowRow` pill chips prior to `docs/design_guidelines.md` Section 5.10 (Rule 42).
4. *Preservation of Core Invariants*: Preserves all `ATT-3929` school toggles, unconditional `Work kJ` and `W/kg` visibility, 9-locale parity, and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutDetails.kt` inspected by source contract tests,
• *When* evaluated against `docs/design_guidelines.md` Section 5.10,
• *Then* `WorkoutDetails.kt` contains zero `FlowRow` or `surfaceContainerHighest` calls and renders `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` using `WorkoutDomainRow` (`24.dp` domain icon + `20.dp` `Spacer`) and equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.

**Invariants**: Section 5.10 / Rule 42 compliance, `ATT-3929` toggle gating, 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL render all power and physiology summary sections in `WorkoutDetails.kt` (`PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow`) using the canonical `AltitudeRow` + `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` grouped row layout pattern (`docs/design_guidelines.md` Section 5.10 / Rule 42) aligned with the `ATT-3929` training science school toggles, and SHALL remove all `FlowRow` `Surface` pill badges and raw inline `Text("EF: ...")` / `Text("Min W'bal: ...")` rows (ATT-3930, amending the UI layout of `REQ-UI-399.3`, `REQ-UI-400.3`, and `REQ-UI-402.2`, and complementing `REQ-UI-403`):
1. *Canonical Domain Row Structure (`REQ-UI-404.1` — Section 5.10)*:
• Each grouped row (`WorkoutDomainRow` / `PowerTrainingMetricsSection` / `AerobicEfficiencyRow` / `AnaerobicBatteryRow`) SHALL render an outer `Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)` containing:
- **Left Domain Header Column**: `Column` with localized domain label `Text(style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`, `Spacer(modifier = Modifier.height(4.dp))`, and a `24.dp` domain `Icon(tint = MaterialTheme.colorScheme.onSurfaceVariant)`, followed by `Spacer(modifier = Modifier.width(20.dp))`.
- **Right Metrics Column / Grid**: `Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp))` containing horizontal `Row(modifier = Modifier.fillMaxWidth())` rows of 2–3 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.
2. *Coggan & Power Load Row (`PowerTrainingMetricsSection`, `REQ-UI-404.2`)*:
• Left header: `R.string.power` + `R.drawable.ic_power` (`24.dp`).
• Right column:
- When `enableCogganPowerMetrics == true` and any of `normalizedPowerWatts`, `intensityFactor`, or `tss` is present: a horizontal `Row` of equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns for **NP** (`label = stringResource(R.string.normalized_power_short)`, `value = stringResource(R.string.workout_power_value_watts_format, np)`, `secondaryValue = data.variabilityIndex?.let { stringResource(R.string.workout_power_metrics_vi_format, it) }`), **IF** (`label = stringResource(R.string.intensity_factor_short)`, `value = stringResource(R.string.workout_power_value_if_format, ifVal)`), and **TSS** (`label = stringResource(R.string.tss_short)`, `value = stringResource(R.string.workout_power_value_tss_format, tssVal)`).
- When `workKj != null

### REQ-UI-405: Profile & Zones Placement in Training Drawer Section and Collapsible My Stuff Section [Verified]
- **Tests**: `TST-UI-368`, `TST-UI-385`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-405`, amending `REQ-UI-391.1` (`ATT-3704`), `REQ-UI-397.1`, and `REQ-UI-397.4` (`ATT-3851`). Targets `AppNavigationDrawer.kt`, `NavigationDrawerStateTest.kt`, and `AthleteProfileContractAndLocalizationTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.6 (`ATT-3704`, commit `b660ef43`) and Sprint 2026-42.7 (`ATT-3851`, `REQ-UI-397`).
3. *Root Reason for Existing Formulation (Why was this fence built?)*: `Profile & Zones` initially resided in `My Stuff` (`collapsible = false`) when it only managed static body weight and FTP entries, prior to `ATT-3859` adding the longitudinal Performance Management Chart (`PMC`: `CTL`, `ATL`, `TSB`) and `ATT-3851` unifying it with Training Zones.
4. *Preservation of Core Invariants (Why is it safe to modify now?)*: Moving `R.id.drawer_athlete_profile` into `R.string.drawer__training` aligns daily training & analytics destinations while leaving `R.string.drawer__my_stuff` with pure hardware/gear setup items (`My Sensors`, `Equipment`, `Sport Types`) that can safely default to collapsed (`collapsible = true`). Total groups (`5`), total unique destinations (`22`), route mappings (`NavRoutes.ATHLETE_PROFILE`, `NavRoutes.EQUIPMENT`), deep-link selection matching (`isDrawerItemSelected`), and 9-locale parity remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `createDrawerGroups(R.string.tab_start)`,
• *When* queried by `titleRes`,
• *Then* the `R.string.drawer__training` group has `collapsible = false` and 4 destination items ending with `R.id.drawer_athlete_profile`, the `R.string.drawer__my_stuff` group has `collapsible = true` and 3 destination items (`[drawer_my_sensors, drawer_equipment, drawer_sport_types]`), and `groups.filter { !it.collapsible }.sumOf { it.items.size } == 9` (excluding the 5 section header rows).
• *Given* `myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }`,
• *When* `isDrawerGroupInitiallyExpanded(myStuffGroup, selectedItemId)` is evaluated,
• *Then* it returns `false` for `R.id.drawer_start_tracking` and `true` exhaustively for every `item.id` in `myStuffGroup.items` plus `R.id.drawer_bikes` and `R.id.drawer_shoes`.

**Invariants**: `5` groups, `22` unique items, `isDrawerItemSelected` deep-link aliases, and 100% clean-room test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL place **Profile & Zones** (`R.id.drawer_athlete_profile`) at the bottom of the **Training** drawer section (`R.string.drawer__training`) and configure the **My Stuff** drawer section (`R.string.drawer__my_stuff`) as collapsible by default (`collapsible = true`) in `AppNavigationDrawer.kt` (`createDrawerGroups`) (ATT-3928, amending `REQ-UI-391.1`, `REQ-UI-397.1`, and `REQ-UI-397.4`):
1. *Training Drawer Section (`REQ-UI-405.1`)*:
• The `DrawerGroup` with `titleRes == R.string.drawer__training` in `createDrawerGroups(startTrackingTitleRes: Int)` SHALL have `collapsible = false` and contain strictly 4 destination items (`DrawerItemConfig`) in order: `R.id.drawer_start_tracking` (`R.drawable.control_start`, `startTrackingTitleRes`), `R.id.drawer_workouts` (`R.drawable.workout_list`, `R.string.tab_workouts`), `R.id.drawer_periods` (`R.drawable.ic_calendar_month`, `R.string.workout_periods__periods`), and `R.id.drawer_athlete_profile` (`R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile`).
2. *Collapsible My Stuff Gear Section (`REQ-UI-405.2`)*:
• The `DrawerGroup` with `titleRes == R.string.drawer__my_stuff` in `createDrawerGroups(startTrackingTitleRes: Int)` SHALL have `collapsible = true` and contain strictly 3 hardware/gear destination items (`DrawerItemConfig`) in order: `R.id.drawer_my_sensors` (`R.drawable.ic_my_paired_devices`, `R.string.devices_myRemoteDevices`), `R.id.drawer_equipment` (`R.drawable.ic_equipment_combined`, `R.string.equipment_management_title`), and `R.id.drawer_sport_types` (`R.drawable.ic_sports_combined`, `R.string.sport_types`).
3. *Default Visible Destination Item Density & Dynamic Auto-Expansion (`REQ-UI-405.3`)*:
• When the 3 collapsible secondary sections (`R.string.drawer__my_stuff` = 3 items, `R.string.prefsOnlineCommunities` = 4 items, `R.string.drawer__settings` = 6 items; `13` hidden destination items total) are collapsed, the 2 non-collapsible primary drawer sections (`R.string.drawer__training` = 4 destination items, `R.string.drawer__maps` = 5 destination items) SHALL yield strictly `9` visible `DrawerItemConfig` destination rows (`groups.filter { !it.collapsible }.sumOf { it.items.size } == 9`, separate from the `5` always-visible category header rows; `9 + 13 = 22` total defined `DrawerItemConfig` items across all `5` groups).
• `isDrawerGroupInitiallyExpanded(group, selectedItemId)` SHALL evaluate `!group.collapsible

### REQ-UI-406: Transparent Inner Dial Fill (`#00000000`) on Stopwatch Vector Icon (`bsport_other.xml`) Across Active and Desaturated States [Verified]
- **Tests**: `TST-UI-369`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable/bsport_other.xml`, `VectorAssetResolutionContractTest.kt`
- **Rationale**: The `bsport_other.xml` sport icon (`app/src/main/res/drawable/bsport_other.xml`) SHALL define a transparent interior fill (`android:fillColor="#00000000"`) on its main stopwatch dial circle `<path>` (`M 31.63,110.42 A 63.87,63.87 ...`) while preserving its gold circular ring stroke (`android:strokeColor="#FFC700"`, `android:strokeWidth="8.81"`), three gold stem paths (`#FFC700`), and three multi-color button caps (`#000090`, `#FF0000`, `#009000`), matching the legacy `bsport_other` and `bsport_other_gray` rendering in both active and unselected/desaturated (`SportTypeSelector.kt`) states (ATT-3926, amending `REQ-UI-375.1`):
1. *Transparent Dial Interior (`REQ-UI-406.1`)*:
• Every `<path>` element in `app/src/main/res/drawable/bsport_other.xml`, including the primary stopwatch circle `<path>` (`M 31.63,110.42 A 63.87,63.87 0 1,0 159.37,110.42 A 63.87,63.87 0 1,0 31.63,110.42 Z`), SHALL declare `android:fillColor="#00000000"`.
• `bsport_other.xml` SHALL NOT contain `#F0F0F0` or any opaque fill color on the dial face.
2. *Preservation of Multi-Color Stroke & Rule 37 Geometry (`REQ-UI-406.2`)*:
• `bsport_other.xml` SHALL preserve its `48dp x 48dp` dimensions, `192x192` viewport, analytical `A` arc primitives, `#FFC700` gold ring/stems, `#000090` left button cap, `#FF0000` center button cap, and `#009000` right button cap.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-406`, amending `REQ-UI-375.1` (`ATT-2859`). Targets `app/src/main/res/drawable/bsport_other.xml` and `VectorAssetResolutionContractTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.7 (`ATT-2859`, commit `15c74685`).
3. *Root Reason for Existing Formulation (Why was this fence built?)*: `Bilder/logo.fig` defined `#F0F0F0` as the background fill of the stopwatch circle for the composite app launcher icon (`logo.png`), whereas the standalone `bsport_other.png` and `bsport_other_gray.png` icons had a transparent interior (`#00000000`).
4. *Preservation of Core Invariants (Why is it safe to modify now?)*: Setting `android:fillColor="#00000000"` on the stopwatch circle path restores 1:1 visual parity with legacy `bsport_other` and `bsport_other_gray` icons in both active and desaturated (`SportTypeSelector.kt`) states while preserving all Rule 37 mathematical arc geometry and multi-color stroke attributes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `app/src/main/res/drawable/bsport_other.xml`,
• *When* parsed and inspected by `VectorAssetResolutionContractTest`,
• *Then* all `<path>` elements declare `android:fillColor="#00000000"`, `#F0F0F0` is absent, and `#FFC700`, `#000090`, `#FF0000`, and `#009000` strokes remain intact.

**Invariants**: Rule 37 analytical geometry, multi-color stroke fidelity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-407: Smart Auto-Collapse and Inline Expansion of Export Status in Workout Summary and Details [Verified]
- **Tests**: `TST-UI-370`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Rationale**: The system SHALL minimize vertical space consumed by `ExportStatus` (`ExportStatusGroup.kt`, `ExportStatusGroupData.kt`) in Workout Summary cards (`WorkoutSummary.kt`) and Workout Details (`TrackOnMapScreen.kt`) by defaulting to a smart collapsed state (`isExpanded = false`) that summarizes fully succeeded export groups on a single clickable header row while automatically surfacing any waiting, ongoing, or failed export groups inline, and expanding all groups via `AnimatedVisibility` when tapped (ATT-3938, complementing `REQ-UI-149` and `REQ-UI-285`):
1. *Classification & Partitioning in `ExportStatusGroupData` (`REQ-UI-407.1`)*:
• `ExportStatusGroupData` (`ExportStatusGroupData.kt`) SHALL expose computed classification properties `val isFullySucceeded: Boolean` (`hasContent && !succeededLine.isNullOrBlank() && waitingLine.isNullOrBlank() && runningLine.isNullOrBlank() && failedLine.isNullOrBlank()`) and `val needsAttentionOrInProgress: Boolean` (`hasContent && (!waitingLine.isNullOrBlank()

### REQ-UI-408: Individually Configurable Multi-Tab Workout Details Layout, Conflict-Free Per-Tab Modes, Gesture Isolation, Auto-Hiding Empty Tabs, and Dual Configuration Entry Points [Verified]
- **Tests**: `TST-UI-371`, `TST-UI-398`, `TST-UI-456`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabsConfigSheet.kt`, `MyPreferenceManager.kt`, `TrackOnMapScreen.kt`, `WorkoutMasksAndCardsSection.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL organize the Workout Details view (`TrackOnMapScreen.kt`) into individually configurable tabs (`WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabsConfigSheet.kt`) backed by `MyPreferenceManager` while preserving `WorkoutSectionType` (`9` values) for the Workout Summary list card (`WorkoutSummary.kt`) and retaining full backward compatibility with legacy single-page `MapDetailLayout` callers (ATT-3920, ATT-5483, ATT-5655, complementing `REQ-UI-240`, `REQ-UI-262`, `REQ-UI-285`, `REQ-UI-337`, `REQ-UI-338`, `REQ-UI-355`, `REQ-UI-359`, `REQ-UI-403`, `REQ-UI-404`, and `REQ-UI-407`):
1. *Domain Model & Default 4-Tab Archetype (`REQ-UI-408.1`)*:
• `WorkoutDetailSectionType` (`WorkoutDetailTabConfig.kt`) SHALL enumerate all `10` assignable Workout Details sections: `DETAILS`, `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `STRAVA`, and `EXPORT_STATUS` (plus `PHOTOS` integrated per ATT-5474 / ATT-5655; `WorkoutSectionType` with `9` values in `WorkoutSectionOrder.kt` SHALL remain unmodified for `WorkoutSummary.kt`).
• `WorkoutDetailTabConfig.DEFAULT_TABS` SHALL define 4 default tabs: (1) **Overview** (`OVERVIEW`, `[DETAILS, PHOTOS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`), (2) **Map & Graphs** (`MAP_AND_GRAPHS`, `[MAP, ELEVATION, CHARTS]`), (3) **Zones** (`ZONES`, `[ZONES]`), and (4) **Laps & Segments** (`LAPS_AND_SEGMENTS`, `[LAPS, STRAVA]`).
• In `WorkoutDetailTabbedContent.kt`, rendering `WorkoutPhotoStrip` SHALL be governed exclusively by `WorkoutDetailSectionType.PHOTOS` (line 785); the legacy fallback under `DETAILS` (rendering photos when absent from `sections`) is completely excised, establishing tab assignment as the single authoritative configuration surface (Design Guidelines Sec 3.2).
2. *Persistence & Defensive Serialization (`REQ-UI-408.2`)*:
• `WorkoutDetailTabConfig` SHALL provide `serializeTabs` and `deserializeTabs` that escape delimiter characters, deduplicate sections, pin `MAP` at index `0` whenever `MAP` is present (`normalizedSections`), and fall back to `DEFAULT_TABS` on null/blank/empty input.
• Serialization format SHALL use prefix `v2|`. In `deserializeTabs`, legacy `v1|` inputs lacking `PHOTOS` across all tabs SHALL be automatically migrated by injecting `PHOTOS` into `tab_overview` (immediately following `DETAILS`), guaranteeing existing user configurations seamlessly surface photo management without data loss.
• `MyPreferenceManager.kt` SHALL expose `workoutDetailTabsOrderFlow`, `getWorkoutDetailTabs()`, `setWorkoutDetailTabs(tabs)`, and `resetWorkoutDetailTabs()` under `"pref_key_workout_detail_tabs_config"`.
3. *Three Conflict-Free Per-Tab Layout Modes & Map Pinning Invariant (`REQ-UI-408.3`)*:
• `WorkoutDetailTabConfig.layoutMode` SHALL resolve each tab into `MODE_A_CARD_SCROLL` (`MAP` absent -> single `verticalScroll` + `navigationBarsPadding()` column with optional sticky `GlobalTelemetryZoomToolbar`), `MODE_B_SPLIT_MAP` (`MAP` + other sections -> `MapDetailLayout` with `MAP` pinned at index `0`, `SplitPaneDivider`, sticky zoom toolbar, and a single lower scrollable pane with synchronized scrubbing `selectedDistance` and `selectedLapNr`), or `MODE_C_FULL_MAP` (`MAP` only -> full-height `MapDetailLayout` without divider or lower scroll pane).
• In `MODE_A_CARD_SCROLL` when rendering `LAPS` (`WorkoutDetailSectionType.LAPS`), clicking any lap row SHALL invoke `onLapClick`, opening `LapEditBottomSheet` as a modal overlay alongside the tabbed content (`Rule 47`), displaying the interactive lap map segment preview, persisting custom lap name & description edits via `WorkoutRepository.updateLapDetails`, and exposing lap split/move boundary actions while synchronizing `selectedLapNr` without unmounting or resetting the active tab.
4. *Smart Gesture Isolation & Auto-Hiding Empty Tabs (`REQ-UI-408.4`)*:
• `requiresHorizontalGestureIsolation(...)` SHALL return `true` when the active tab contains active `MAP`, `ELEVATION`, or `CHARTS` data, and `false` on card-only tabs, bound to `HorizontalPager(userScrollEnabled = !activeTabRequiresGestureIsolation)`.
• `filterVisibleWorkoutDetailTabs(...)` SHALL hide any tab where zero assigned sections have available data for the current workout (retaining at least 1 fallback tab).
5. *Interactive Tab Configuration Sheet & Dual Entry Points (`REQ-UI-408.5`)*:
• `WorkoutDetailTabsConfigSheet.kt` SHALL support adding, renaming, reordering, and deleting tabs (minimum 1 tab), toggling and reordering the 10 sections per tab (`MAP` pinned at index `0`), and resetting to `DEFAULT_TABS`.
• Entry Point 1: Compact `IconButton` in `WorkoutHeader` (`TrackOnMapScreen.kt` / `WorkoutDetailTabbedContent.kt`).
• Entry Point 2: Action row/button in `WorkoutMasksAndCardsSection.kt` in Advanced Settings (keeping every `.kt` file in `ui/settings/tuning/` strictly `< 400` lines).
6. *9-Language Localization Parity (`REQ-UI-408.6`)*:
• All new `workout_detail_tab_*`, `workout_detail_section_*`, and `workout_detail_tabs_config_*` string resources SHALL exist across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-408` (amended by `ATT-5483` and `ATT-5655`), complementing `REQ-UI-240`, `REQ-UI-262`, `REQ-UI-285`, `REQ-UI-337`, `REQ-UI-338`, `REQ-UI-355`, `REQ-UI-359`, `REQ-UI-403`, `REQ-UI-404`, and `REQ-UI-407`. Targets `WorkoutDetailTabConfig.kt` and `WorkoutDetailTabbedContent.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2310` (`REQ-UI-285`), `ATT-3267` (`REQ-UI-337`), `ATT-3266` (`REQ-UI-338`), `ATT-3395` (`REQ-UI-359`), `ea5fa705` (`ATT-3920`, `ATT-5483`), `8aed20fc` (`ATT-5474`), and `ATT-5655`.
3. *Root Reason for Existing Formulation*: When `PHOTOS` was introduced in ATT-5474, a fallback was placed under `DETAILS` in `WorkoutDetailTabbedContent.kt:686` to prevent photos from disappearing on legacy tabs that did not contain `PHOTOS`. However, this forced photos on-screen even when explicitly unassigned in tab configuration, violating Design Guidelines Section 3.2.
4. *Preservation of Core Invariants*: Single authoritative configuration surface (Design Guidelines Sec 3.2); map pinned at index 0; seamless `v1|` -> `v2|` wire migration preserving user tab customizations; minimum 1 tab fallback; Preserves `WorkoutSectionType` (`9` values) for `WorkoutSummary.kt`, preserves all 7 existing `TrackOnMapScreen*ContractTest` suites via legacy single-page fallback when `useTabbedLayout = false` or custom `metadataContent`/`analyticsContent` lambdas are supplied, keeps all `ui/settings/tuning/*.kt` files `< 400` lines, and preserves Rule 47 modal overlay alongside host tabs.

**Acceptance Criteria (Given-When-Then)**:
• *Given* default configuration,
• *When* `WorkoutDetailTabConfig.DEFAULT_TABS` is serialized and deserialized,
• *Then* 4 tabs (`Overview`, `Map & Graphs`, `Zones`, `Laps & Segments`) round-trip identically and resolve to `MODE_A_CARD_SCROLL`, `MODE_B_SPLIT_MAP`, `MODE_A_CARD_SCROLL`, and `MODE_A_CARD_SCROLL`.
• *Given* the 'Laps & Segments' tab in `MODE_A_CARD_SCROLL`,
• *When* the athlete taps an individual lap row,
• *Then* `LapEditBottomSheet` opens as an overlay alongside the tabbed content (Rule 47) with segment map preview and edit controls.
• *Given* a tab with `[DETAILS, MAP, CHARTS]`,
• *When* `normalizedSections` is evaluated,
• *Then* `MAP` is pinned at index `0` (`[MAP, DETAILS, CHARTS]`) and `layoutMode` is `MODE_B_SPLIT_MAP`.
• *Given* an indoor workout without GPS map or Strava segments,
• *When* `filterVisibleWorkoutDetailTabs` is evaluated,
• *Then* any tab with zero available sections is automatically hidden.

**Invariants**: `WorkoutSectionType` (`9` values), `TrackOnMapScreen*ContractTest` compatibility, `< 400` line modularity in `ui/settings/tuning/`, 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-409: Default `BIKE_POWER` Cockpit Tabs (`Standard`, `Power`, `Map`, `Laps`), `W_PRIME_BAL` Sensor Catalog Inclusion, and Live Cockpit Gating by Physiology Model Toggles [Verified]
- **Tests**: `TST-UI-372`, `TST-UI-384`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ActivityType.java`, `TrackingViewsDatabaseManager.java`, `SensorFieldState.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL include `SensorType.W_PRIME_BAL` in `ActivityType.BIKE_POWER`, seed 4 purpose-built default cockpit tabs (`Standard`, `Power`, `Map`, `Laps`) for `ActivityType.BIKE_POWER` on first-run database creation (`TrackingViewsDbHelper.onCreate`), and dynamically gate live cockpit rendering of `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS` (`enableCogganPowerMetrics`) and `W_PRIME_BAL` (`enableCriticalPowerModel`) with memoized state synchronization, null-safe initial-boot fallback, and all-gated empty-state protection while keeping `ScreenMode.CONFIGURATION` unfiltered (ATT-3921, extending `REQ-UI-200`, `REQ-UI-216`, `REQ-UI-399`, and `REQ-UI-403`):
1. *`ActivityType.BIKE_POWER` Sensor Catalog (`REQ-UI-409.1`)*:
• `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, Context)` SHALL include `SensorType.W_PRIME_BAL` immediately after `SensorType.TSS`, alongside `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, `TSS`, `PEDAL_POWER_BALANCE`, and `TORQUE`.
2. *4-Tab Default `BIKE_POWER` Cockpit Architecture & First-Run Seeding (`REQ-UI-409.2`)*:
• In `TrackingViewsDatabaseManager.TrackingViewsDbHelper.onCreate(SQLiteDatabase db)` (`DB_VERSION = 13` unchanged so existing user-customized `TrackingViews.db` rows are never overwritten or duplicated on upgrade), `ActivityType.BIKE_POWER` SHALL seed 4 default tabs in order:
- **Tab 1 — Standard (`R.string.text_default`, `layoutNr = 1`, `showLapButton = false, showMap = false, showLiveSegments = true, showLiveClimbs = true, showNavigationHints = true`)**: Row 1: `TIME_ACTIVE` (`SMALL`, col 1), `TIME_OF_DAY` (`SMALL`, col 2); Row 2: `SPEED_mps` (`NORMAL`, col 1), `DISTANCE_m` (`NORMAL`, col 2); Row 3: `HR` (`LARGE`, col 1), `CADENCE` (`LARGE`, col 2); Row 4: `POWER` (`HUGE`, col 1); Row 5: `POWER_TO_WEIGHT` (`NORMAL`, col 1), `NORMALIZED_POWER` (`NORMAL`, col 2); Row 6: `ALTITUDE` (`NORMAL`, col 1), `ASCENT` (`NORMAL`, col 2).
- **Tab 2 — Dedicated Power Tab (`R.string.tab_power`, `layoutNr = 2`, `showLapButton = false, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true`)** via `getDefaultPowerRowDataList()`: Row 1: `TIME_ACTIVE` (`SMALL`, col 1), `HR` (`SMALL`, col 2), `CADENCE` (`SMALL`, col 3); Row 2: `POWER` (`HUGE`, col 1); Row 3: `POWER_TO_WEIGHT` (`LARGE`, col 1), `NORMALIZED_POWER` (`LARGE`, col 2); Row 4: `INTENSITY_FACTOR` (`NORMAL`, col 1), `TSS` (`NORMAL`, col 2), `WORK_KJ` (`NORMAL`, col 3); Row 5: `W_PRIME_BAL` (`LARGE`, col 1); Row 6: `PEDAL_POWER_BALANCE` (`SMALL`, col 1) spanning full width without `TORQUE`.
- **Tab 3 — Map (`R.string.tab_map`, `layoutNr = 3` for `BIKE_POWER`, `2` for others; `showLapButton = false, showMap = true, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true`)** via `getDefaultMapRowDataList(ActivityType.BIKE_POWER)`: Row 1: `TIME_ACTIVE` (`NORMAL`, col 1), `DISTANCE_m` (`NORMAL`, col 2); Row 2: `SPEED_mps` (`NORMAL`, col 1), `HR` (`NORMAL`, col 2); Row 3: `POWER` (`NORMAL`, col 1), `POWER_TO_WEIGHT` (`NORMAL`, col 2).
- **Tab 4 — Laps (`R.string.tab_laps`, `layoutNr = 4` for `BIKE_POWER`, `3` for others; `showLapButton = true, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = false`)** via `getDefaultLapRowDataList(ActivityType.BIKE_POWER)`: Row 1: `TIME_LAP` (`LARGE`, col 1), `LAP_NR` (`SMALL`, col 2); Row 2: `SPEED_mps` (`NORMAL`, col 1), `DISTANCE_m_LAP` (`NORMAL`, col 2); Row 3: `HR` (`NORMAL`, col 1), `CADENCE` (`NORMAL`, col 2); Row 4: `POWER` (`LARGE`, col 1), `POWER_TO_WEIGHT` (`NORMAL`, col 2); Row 5 (if `havePressureSensor`): `ALTITUDE` (`NORMAL`, col 1), `ASCENT` (`NORMAL`, col 2).
3. *Live-Cockpit Gating, Empty-State Protection & Memoized State Synchronization (`REQ-UI-409.3`)*:
• `SensorFieldState` (`SensorFieldState.kt`) SHALL expose `val sensorType: SensorType? = null` (populated in `TrackingViewModel.kt`) and pure, null-safe functions `isSensorTypeVisibleForTuning(sensorType: SensorType?, tuningConfig: TuningConfig? = null): Boolean` (resolving `tuningConfig ?: TuningConfig()` during initial boot before DataStore emits) and `filterVisibleFieldsForTuning(fields, tuningConfig, screenMode)` returning all fields unfiltered in `ScreenMode.CONFIGURATION` and filtering out disabled physiology sensors (`NP`, `IF`, `TSS` when `!enableCogganPowerMetrics`; `W_PRIME_BAL` when `!enableCriticalPowerModel`) in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW` without mutating `rowNr`/`colNr` or triggering any database write.
• If all `SensorFieldState` elements in an individual row are gated off, the parent container SHALL collapse that row height to `0` (omitting that `rowNr` from `visibleFields.groupBy { it.rowNr }`) to prevent inter-row gaps; if **all** rows in the tab are gated off (`visibleFields.isEmpty()`), `SensorGridContent` SHALL maintain a minimum interactive container height (`defaultMinSize(minHeight = 48.dp)`) so the screen never collapses to a zero-pixel area or throws layout exceptions.
• `SensorGridScreen.kt` SHALL memoize `remember(state.fields, tuningConfig, state.screenMode) { filterVisibleFieldsForTuning(...) }` and ensure smooth layout transitions for row collapsing.
4. *9-Language Localization Parity (`REQ-UI-409.4`)*:
• `<string name="tab_power">...</string>` SHALL exist and be non-blank across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-409`, extending `REQ-UI-200`, `REQ-UI-216`, `REQ-UI-399`, and `REQ-UI-403`, refined by `ATT-4447` to remove `TORQUE` from Row 6 of default Power tab so `PEDAL_POWER_BALANCE` spans full row width. Targets `TrackingViewsDatabaseManager.java` and `BikePowerCockpitDefaultsAndGatingTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-1291` (`REQ-TRK-021`), `ATT-3106` (`REQ-UI-216`), `ATT-3586` / `ATT-3862` (`REQ-UI-399`), `ATT-3929` (`REQ-UI-403`), and `ATT-3921` (commit `a6289096`).
3. *Root Reason for Existing Formulation*: `PEDAL_POWER_BALANCE` and `TORQUE` were initially placed side-by-side as secondary pedaling metrics to fit 6 rows, but on physical devices `PedalPowerBalanceBar` caused stepped card height mismatch and cramped horizontal resolution.
4. *Preservation of Core Invariants*: `SensorType.TORQUE` remains in `ActivityType.BIKE_POWER` sensor catalog, zero database migrations on existing user layouts, and 100% test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a clean database creation (`TrackingViewsDbHelper.onCreate`),
• *When* `ActivityType.BIKE_POWER` tabs are seeded,
• *Then* 4 tabs (`Standard`, `Power`, `Map`, `Laps`) are created with the specified sensor rows, `ViewSize`s, and tab toggles, with Row 6 of the Power tab containing solely `PEDAL_POWER_BALANCE` spanning full width without `TORQUE`.
• *Given* the `Power` tab in `ScreenMode.TRACKING` with default `TuningConfig` (`enableCogganPowerMetrics = true, enableCriticalPowerModel = false`),
• *When* `filterVisibleFieldsForTuning` is evaluated,
• *Then* `W_PRIME_BAL` (Row 5) is hidden (collapsing Row 5 height to `0` in `fieldsByRow`) while `NP`, `IF`, `TSS`, `WORK_KJ`, `POWER`, and `POWER_TO_WEIGHT` remain visible.
• *Given* a custom tab where all fields are gated off or `tuningConfig == null`,
• *When* `filterVisibleFieldsForTuning` is evaluated,
• *Then* it safely handles the all-gated state (`emptyList()`) and null boot state (`TuningConfig()`) without throwing exceptions.

**Invariants**: `DB_VERSION = 13`, zero-height empty-row collapse, non-zero container height on all-gated tabs, unfiltered `ScreenMode.CONFIGURATION`, 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-410: Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & Context Unwrapping (`TrackingTabsScreen.kt`) [Verified]
- **Tests**: `TST-UI-373`, `TST-UI-375`, `TST-UI-399`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsScreen.kt`, `TrackingTabsNavigationTimeoutTest.kt`
- **Rationale**: `TrackingTabsScreen.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`) SHALL guarantee that page-count timeouts during `TabNavigationEvent.NavigateTo` never cancel the `navigationEvent` collector coroutine, that `DialogFragment.show(...)` calls are lifecycle- and `isStateSaved`-safe, and that `LocalContext.current` is unwrapped safely without unchecked casts (ATT-4000, extending `REQ-UI-163`):
1. *Non-Cancelling Page-Count Timeout via `withTimeoutOrNull` & `first` (`REQ-UI-410.1`)*:
• When handling `TabNavigationEvent.NavigateTo(index)` (via `navigateToPagerTarget`), `TrackingTabsScreen` SHALL calculate `target = index + (if (screenMode == ScreenMode.TRACKING) 1 else 0)` and, when `currentPageCount() <= target`, await readiness using `kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { pageCountFlow.first { count -> count > target } }` (`timeoutMs = 2000L`) instead of `withTimeout` + `CancellationException("PageCountReady")`.
• When `withTimeoutOrNull` returns `null` (timeout), `navigateToPagerTarget` SHALL log a warning (`Log.w`) and return `false` cleanly without throwing `TimeoutCancellationException` and without mutating `lastKnownPage` to the unreachable `target`, keeping the `navigationEvent` collector coroutine alive for all subsequent events.
• When `target in 0 until currentPageCount()`, `navigateToPagerTarget` SHALL update `lastKnownPage = target` and execute `animateScrollToPage(target)` with fallback to `scrollToPage(target)` (while propagating outer scope cancellation via `currentCoroutineContext().ensureActive()`).
2. *Lifecycle-Aware Event Collection & `isStateSaved` Dialog Guard (`REQ-UI-410.2`)*:
• Both `trackingTabsViewModel.navigationEvent` and `controlViewModel.navigationEvent` collectors in `TrackingTabsScreen.kt` SHALL collect inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`.
• All `DialogFragment.show(...)` calls (`EditDeviceDialog`, `StartOrResumeDialog`) SHALL be guarded by `activity.canShowDialogFragments()` (`this != null && !isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved`).
3. *Safe `Context.findActivity()` Unwrapping (`REQ-UI-410.3`)*:
• `TrackingTabsScreen.kt` SHALL replace `LocalContext.current as AppCompatActivity` with `internal tailrec fun Context.findActivity(): AppCompatActivity?` that unwraps `ContextWrapper` chains and safely returns `null` for non-Activity contexts.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-410`, extending `REQ-UI-163` (`ATT-1340`). Targets `TrackingTabsScreen.kt` and `TrackingTabsNavigationTimeoutTest.kt`.
2. *Historical Origin & Commit Trace*: Commit `3a9417e1b59fea6dd3e6556bad29a16f27a4011a` (`ATT-1340`).
3. *Root Reason for Existing Formulation*: `ATT-1340` used `withTimeout(2000)` and threw `CancellationException("PageCountReady")` to exit `snapshotFlow.collect`, overlooking that `TimeoutCancellationException` extends `CancellationException` and was rethrown on timeout.
4. *Preservation of Core Invariants*: Preserves all `REQ-UI-163` rising-edge tracking start navigation, `rememberUpdatedState(pagerState)`, `animateScrollToPage` with `scrollToPage` fallback, and outer scope cancellation semantics.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `TabNavigationEvent.NavigateTo` event whose target page times out,
• *When* a subsequent valid `TabNavigationEvent.NavigateTo` event arrives,
• *Then* the `navigationEvent` collector coroutine remains active and navigates the pager to the requested page.
• *Given* an `AppCompatActivity` that is finishing, destroyed, or has `supportFragmentManager.isStateSaved == true`,
• *When* `canShowDialogFragments()` is evaluated,
• *Then* it returns `false` and skips `DialogFragment.show(...)` without throwing `IllegalStateException`.
• *Given* a `Context` wrapped in `ContextWrapper` layers around an `AppCompatActivity` (or a non-Activity `Context`),
• *When* `context.findActivity()` is called,
• *Then* it returns the underlying `AppCompatActivity` (or `null`) without throwing `ClassCastException`.

**Invariants**: `REQ-UI-163` rising-edge cockpit navigation, outer coroutine cancellation propagation (`ensureActive()`), 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-411: Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid [Verified]
- **Tests**: `TST-UI-374`, `TST-UI-386`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL support configurable horizontal section dividers with optional mini-titles between sensor rows in Cockpit tracking tabs (`TrackingViewsDbHelper`, `TrackingViewsDatabaseManager`, `TrackingViewsRepository`, `SensorGridScreen.kt`), persisting them in `FlexibleGenericView.db` with automated row shift tracking, prominent divider stroke contrast, horizontally centered mini-titles, unambiguous split iconography, minimal vertical height overhead, and 9-language localization parity (ATT-4055, refined by ATT-4286):
1. *Database Schema & Migration (`REQ-UI-411.1`)*:
• In `TrackingViewsDatabaseManager.TrackingViewsDbHelper`, `DB_VERSION` SHALL be incremented to `14` (verified as the next sequential schema version for `FlexibleGenericView.db`).
• A dedicated table `SectionDividersTable` SHALL be created with columns: `_id INTEGER PRIMARY KEY AUTOINCREMENT`, `ViewId INTEGER NOT NULL`, `RowNr INTEGER NOT NULL`, `Title TEXT`, and constraint `UNIQUE(ViewId, RowNr) ON CONFLICT REPLACE`.
• In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`, when `oldVersion < 14`, table `SectionDividersTable` SHALL be created idempotently via `CREATE TABLE IF NOT EXISTS SectionDividersTable (...)`.
2. *Data Access & Row Shift Invariants (`REQ-UI-411.2`)*:
• `TrackingViewsDatabaseManager` SHALL provide methods `getSectionDividers(long viewId)`, `setSectionDivider(long viewId, int rowNr, @Nullable String title)`, `deleteSectionDivider(long viewId, int rowNr)`, and `deleteSectionDividersForView(long viewId)`.
• When a new row is inserted at `rowNr` in `insertSensorFieldAt(long tabViewId, int rowNr, int colNr, ...)` with `colNr == -1`, any existing divider at `RowNr >= rowNr` for `tabViewId` SHALL shift up by 1 (`UPDATE SectionDividersTable SET RowNr = RowNr + 1 WHERE ViewId = ? AND RowNr >= ?`).
• When a row is removed or deleted, any divider at `RowNr > deletedRowNr` SHALL shift down by 1 (`UPDATE SectionDividersTable SET RowNr = RowNr - 1 WHERE ViewId = ? AND RowNr > ?`), while any divider attached to `deletedRowNr` SHALL be re-indexed during `normalizeGrid(tabViewId)` to the surviving row or removed if no rows remain.
• When `normalizeGrid(long tabViewId)` re-indexes distinct rows to contiguous `1..N`, existing section dividers for `tabViewId` SHALL be mapped to the matching new row indices, and any orphaned dividers SHALL be deleted.
• When a tab is deleted in `deleteTabView(long viewId)`, all dividers for `viewId` SHALL be deleted.
3. *Repository Flow & Screen State Synchronization (`REQ-UI-411.3`)*:
• `TrackingViewsRepository` SHALL expose `fun getSectionDividersFlow(tabViewId: Long): Flow<List<SectionDividerInfo>>` triggered by `configUpdateTrigger`.
• `TrackingScreenState` SHALL include `val sectionDividers: List<SectionDividerInfo> = emptyList()`.
• `TrackingViewModel` SHALL collect dividers from `TrackingViewsRepository` and update `TrackingScreenState.sectionDividers`.
4. *Glanceable Cockpit Rendering, Prominence & Centered Mini-Titles (`REQ-UI-411.4`)*:
• In `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`, `SensorGridScreen.kt` SHALL render a section divider above any visible row that has a matching entry in `state.sectionDividers` (`divider.rowNr == fieldsInThisRow.first().rowNr`).
• When `divider.title` is null or blank, the divider SHALL render with a prominent high-contrast stroke (`HorizontalDivider`, `thickness = 2.5.dp`, `color = MaterialTheme.colorScheme.outline`) across the grid with minimal vertical padding (`padding(vertical = 4.dp)`), ensuring clear demarcation against bordered sensor cells with total height $\le 10\text{ dp}$.
• When `divider.title` is present, the divider mini-title SHALL be horizontally centered across the full row width, flanked symmetrically by left and right `HorizontalDivider` segments with equal `Modifier.weight(1f)` and `thickness = 2.dp` (`MaterialTheme.colorScheme.outline`), styled with `MaterialTheme.typography.labelSmall`, uppercase, bold, letter-spacing, and primary color, with total height overhead constrained to $\le 20\text{ dp}$.
• Mini-titles SHALL specify `maxLines = 1` and `overflow = TextOverflow.Ellipsis`, guaranteeing that long text strings gracefully truncate on narrow screen displays and never cause multi-line wrapping or breach the $\le 20\text{ dp}$ vertical height constraint.
5. *Configuration Mode WYSIWYG Controls & Unambiguous Split Iconography (`REQ-UI-411.5`)*:
• In `ScreenMode.CONFIGURATION`, an inter-row divider action/indicator SHALL be displayed next to `RowAdder` between rows.
• When `existingDivider == null`, the divider insertion action button SHALL render icon `Icons.Default.HorizontalSplit` with tint `MaterialTheme.colorScheme.outline` and contentDescription `stringResource(R.string.cockpit_section_divider_add)`, strictly prohibiting the use of `Icons.Default.HorizontalRule` (`—`) to eliminate any false visual affordance of a minus/row-delete button (`+ / -`) adjacent to `RowAdder` (`Icons.Default.Add`).
• When `existingDivider != null`, the action button SHALL render icon `Icons.Default.Title` with tint `MaterialTheme.colorScheme.primary` and contentDescription `stringResource(R.string.cockpit_section_divider_edit)`.
• Clicking the action SHALL launch `EditSectionDividerDialog` allowing the user to select from localized quick-pick preset chips (*Power*, *Heart Rate*, *Lap*, *Pacing*, *Navigation*, *Total*), enter custom free text, clear the title to make it an unlabeled divider line, or delete the divider entirely.
6. *9-Language Localization Parity (`REQ-UI-411.6`)*:
• All UI strings for section divider controls, dialog actions, and preset chips SHALL exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-411.4` (Glanceable Cockpit Rendering & Truncation Safety) and `REQ-UI-411.5` (Configuration Mode WYSIWYG Controls) within `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*), refined by `ATT-4286`. Targets `SensorGridScreen.kt`, `SensorGridScreenLayoutTest.kt`, and `SensorGridLocalizationTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4055` (commit `6199b2a45fcfb81f13a31906b3e2b725ea6c4618`, Sprint `2026-42.10`), refined in `ATT-4286` (Sprint `2026-43.1`).
3. *Root Reason for Existing Formulation*: `Icons.Default.HorizontalRule` was selected literally to represent a horizontal divider rule, but placed directly next to `RowAdder`'s `+` icon it misleadingly functioned as a minus/delete button (`-`). A faint `1.5.dp` stroke with `outlineVariant` visually blended with `1.dp` cell borders, and asymmetric weights (`0.12f`/`0.88f`) were an exploratory left-accent layout that appeared lopsided on device.
4. *Preservation of Core Invariants*: Replacing `HorizontalRule` with `HorizontalSplit` removes the deceptive minus affordance while preserving click handlers, accessibility descriptions, and active title indicators. Increasing stroke thickness to `2.5.dp` (unlabeled) and `2.dp` (labeled) ensures glanceability while strictly preserving the $\le 20\text{ dp}$ height ceiling. Centering titles via symmetric `weight(1f)` flanking lines provides balanced layout across all screen sizes and orientations without mutating database schema (v14) or row-shift logic.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Cockpit in `ScreenMode.CONFIGURATION`,
• *When* viewing the inter-row controls between sensor rows,
• *Then* the add-divider action displays `Icons.Default.HorizontalSplit` (and not `Icons.Default.HorizontalRule`), preventing false minus/delete affordances next to `RowAdder` (`+`).
• *Given* an unlabeled section divider in `ScreenMode.TRACKING` or `ScreenMode.PREVIEW`,
• *When* rendered above a row,
• *Then* it renders with a prominent stroke (`thickness = 2.5.dp`, `MaterialTheme.colorScheme.outline`) distinguishable from adjacent cell borders with total height $\le 10\text{ dp}$.
• *Given* a titled section divider in `ScreenMode.TRACKING` or `ScreenMode.PREVIEW`,
• *When* rendered above a row,
• *Then* the title is horizontally centered with symmetric flanking divider lines (`weight(1f)`, `thickness = 2.dp`, `MaterialTheme.colorScheme.outline`), truncating with ellipsis (`maxLines = 1`) if long.
• *Given* the Cockpit in `ScreenMode.CONFIGURATION`,
• *When* the user taps the section divider action between two rows and selects a preset chip (e.g. "Power") or enters text,
• *Then* the section divider is persisted in `SectionDividersTable` and dynamically shown on the screen.
• *Given* rows are inserted or deleted above an existing divider,
• *When* the layout updates and normalizes,
• *Then* the divider shifts cleanly with its row boundary without orphan records.

**Invariants**: Additive schema upgrade (`DB_VERSION = 14`), transactional row-shift integrity, minimal height footprint ($\le 20\text{ dp}$), 9-locale translation parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-412: Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety [Verified]
- **Tests**: `TST-UI-375`, `TST-UI-399`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsViewModel.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `TrackingTabsNavigationTimeoutTest.kt`
- **Rationale**: `TrackingTabsViewModel` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`), `ControlTrackingViewModel` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`), and `TrackingTabsScreen.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`) SHALL buffer one-off navigation events via Kotlin coroutine `Channel`s across the Android `STOPPED` lifecycle state, consume each event exactly once upon resuming `STARTED` collection without duplicate replay, and cleanly respect user touch swipe interruptions during pager animations without triggering fallback instant snaps (ATT-4097, extending `REQ-UI-163` and `REQ-UI-410`):
1. *Channel-Buffered One-Off Navigation Events in ViewModels (`REQ-UI-412.1`)*:
• In `TrackingTabsViewModel`, `_navigationEvent` SHALL be declared as `Channel<TabNavigationEvent>(Channel.BUFFERED)` and exposed as `val navigationEvent = _navigationEvent.receiveAsFlow()`.
• In `ControlTrackingViewModel`, `_navigationEvent` SHALL be declared as `Channel<ControlNavigation>(Channel.BUFFERED)` and exposed as `val navigationEvent = _navigationEvent.receiveAsFlow()`.
• Navigation event emissions (`onEditDevice`, rising-edge `TRACKING` start in `TrackingTabsViewModel`; `onDeviceClicked`, `onStartTracking` recovery dialog in `ControlTrackingViewModel`) SHALL send events via the channel buffer, guaranteeing that events emitted while subscribers are suspended (Activity in `Lifecycle.State.STOPPED`) remain buffered in memory without being dropped.
2. *Exactly-Once Lifecycle Delivery & Re-Subscription Safety (`REQ-UI-412.2`)*:
• In `TrackingTabsScreen.kt`, `trackingTabsViewModel.navigationEvent` and `controlViewModel.navigationEvent` collectors running inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` SHALL consume buffered events as soon as the Activity enters `STARTED`.
• Because channels do not broadcast or retain replay caches once consumed, re-subscribing (e.g. Activity recreation or screen navigation) SHALL NOT re-deliver previously handled events.
3. *User Gesture Cancellation Resilience in `navigateToPagerTarget` (`REQ-UI-412.3`)*:
• In `navigateToPagerTarget` (`TrackingTabsScreen.kt`), `animateScrollToPage(target)` SHALL catch `kotlinx.coroutines.CancellationException` specifically.
• When a scroll animation is cancelled by a user manual drag/swipe gesture or subsequent pager interaction (`CancellationException`), `navigateToPagerTarget` SHALL log the cancellation and SHALL NOT execute the fallback `scrollToPage(target)`, preserving the user's manual swipe position without hard snapping.
• Non-cancellation exceptions (`Exception`) encountered during animation SHALL continue to invoke the fallback `scrollToPage(target)` while propagating coroutine cancellation via `currentCoroutineContext().ensureActive()`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-412`, extending `REQ-UI-163` (`ATT-1340`) and `REQ-UI-410` (`ATT-4000`). Targets `TrackingTabsViewModel.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `TrackingTabsNavigationTimeoutTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint `2026-42.9` Code Review of `ATT-4000` (`84f8d3e8`) Findings 1 & 2 identified that `MutableSharedFlow(replay = 0)` drops events during `STOPPED` and `catch (e: Exception)` in `navigateToPagerTarget` overrides user swipe gestures on `CancellationException`.
3. *Root Reason for Existing Formulation*: `MutableSharedFlow` was originally used as standard Kotlin Flow UI event mechanism; `catch (e: Exception)` caught `CancellationException` inadvertently because `CancellationException` inherits from `Exception`.
4. *Preservation of Core Invariants*: Rising-edge tracking navigation, 2000 ms page-count timeout safety, `canShowDialogFragments()` dialog guards, and `tailrec Context.findActivity()` unwrapping remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Cockpit screen is in the background (`STOPPED` lifecycle state) when a tracking start event occurs or a device edit is requested,
• *When* the app returns to the foreground (`STARTED` state),
• *Then* the buffered navigation event is immediately received and processed by the UI.
• *Given* an event has been consumed and the screen undergoes configuration change or re-subscription,
• *When* the collector re-subscribes in `STARTED`,
• *Then* zero duplicate navigation events are emitted.
• *Given* `animateScrollToPage` is in progress towards `target`,
• *When* the user initiates a manual swipe gesture causing `CancellationException`,
• *Then* the fallback `scrollToPage(target)` is skipped, allowing the user's swipe to govern pager positioning.
• *Given* `animateScrollToPage` fails with an unexpected runtime exception,
• *When* caught,
• *Then* `scrollToPage(target)` executes as fallback to guarantee pager target arrival.

**Invariants**: No duplicate event replays; non-blocking coroutine dispatch; 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-413: Cockpit Sensor Field Insertion Transaction Atomicity, Multi-Tab Isolation, and Grid Normalization [Verified]
- **Tests**: `TST-UI-376`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRealSqliteMigrationTest.kt`, `TrackingViewsDatabaseManagerTest.kt`
- **Rationale**: `TrackingViewsDatabaseManager.insertSensorFieldAt` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`) SHALL guarantee multi-tab isolation, atomic coordinate updates, parameter-bound query execution, section divider synchronization, out-of-bounds row handling, and post-insertion grid normalization across all configured cockpit tabs (ATT-4010, extending `REQ-UI-200` and `REQ-UI-411.2`):
1. *Input & Boundary Validation (`REQ-UI-413.1`)*:
• `insertSensorFieldAt` SHALL validate that `tabViewId > 0` and `rowNr >= 1`. If either parameter is invalid, the method SHALL log an error/warning and return immediately without executing any database mutations.
• For out-of-bounds row insertion where `rowNr > currentMaxRow + 1`, the insertion SHALL be accepted, placing the new field at `rowNr`, and subsequently compacted to contiguous coordinate `currentMaxRow + 1` by post-insertion grid normalization (`REQ-UI-413.5`), guaranteeing that gaps in row coordinates are never permanently persisted.
2. *Single-Transaction Atomicity (`REQ-UI-413.2`)*:
• All database updates and insertions in `insertSensorFieldAt` SHALL execute within a single atomic SQLite transaction (`db.beginTransaction() ... try { ... db.setTransactionSuccessful(); } finally { db.endTransaction(); }`).
• If any exception, constraint violation, or crash occurs during coordinate shifting, divider adjustment, or row insertion, the transaction SHALL be rolled back completely, leaving `ROWS_TABLE` and `SECTION_DIVIDERS_TABLE` in their exact pre-call state with zero orphaned shifts or corrupted coordinates.
3. *Multi-Tab Isolation & Parameterized DML (`REQ-UI-413.3`)*:
• All `UPDATE` statements shifting row numbers or column numbers SHALL strictly filter by `WHERE VIEW_ID = ?` (using parameterized `Object[]` bind arguments) matching `tabViewId`.
• Shifting rows or columns in Tab A SHALL NEVER affect, renumber, or mutate any row or divider record in Tab B, Tab C, or any other configured cockpit tab.
4. *Deterministic Sequence & Divider Synchronization (`REQ-UI-413.4`)*:
• When inserting a new row (`colNr == -1`), `ROWS_TABLE` rows $\ge \text{rowNr}$ SHALL be shifted down by 1 first, followed by shifting `SECTION_DIVIDERS_TABLE` records $\ge \text{rowNr}$ down by 1 (`UPDATE SectionDividersTable SET RowNr = RowNr + 1 WHERE ViewId = ? AND RowNr >= ?`).
• If a section divider's row coordinate is shifted, it SHALL retain its relative position between rows. Subsequent grid normalization SHALL ensure that any divider residing at `RowNr <= 1` or orphaned by layout changes is safely purged, while valid dividers remain bound to their row boundaries ($\ge 2$).
• The new sensor field SHALL be inserted at `(rowNr, 1)`.
• When inserting into an existing row (`colNr \ge 1`), columns $\ge \text{colNr}$ at `rowNr` SHALL be shifted right by 1, and the new field inserted at `(rowNr, colNr)`.
5. *Post-Insertion Grid Normalization (`REQ-UI-413.5`)*:
• Before committing the transaction, `insertSensorFieldAt` SHALL invoke the internal transaction-participating overload `normalizeGrid(SQLiteDatabase db, long tabViewId)`, ensuring that all sensor fields in `tabViewId` occupy contiguous $1..N$ coordinates, healing any gapped states (e.g. rows 1, 3, 5 -> 1, 2, 3), and remapping all section dividers to valid contiguous row indices ($\ge 2$) without creating nested transaction deadlocks.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-413`, extending `REQ-UI-200` (*Sensor Grid Configuration & Drag-and-Drop / Pick & Place*) and `REQ-UI-411.2` (*Section Divider Row Shift Invariants*). Targets `TrackingViewsDatabaseManager.java`, `TrackingViewsRealSqliteMigrationTest.kt`, and `TrackingViewsDatabaseManagerTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4010`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `insertSensorFieldAt` was authored in March 2026 (commit `5a8d102e`) when the application supported only a single global layout view. Commit `6199b2a4` (`ATT-4055`) introduced horizontal section dividers and added `WHERE VIEW_ID = tabViewId` string filters to mitigate cross-tab leakage, but left the method non-transactional and un-normalized.
3. *Root Reason for Existing Formulation*: The original logic operated directly on SQLite without transaction wrapping because initial single-tab layouts were simple and rarely edited concurrently. String interpolation was used for simplicity before security/safety linting was applied across the DAL. Grid normalization was omitted because callers were expected to supply contiguous indices.
4. *Preservation of Core Invariants*: Multi-tab isolation across independent cockpits, contiguous $1..N$ coordinates with zero gaps, section divider alignment between row boundaries ($\ge 2$), passive `onOpen()` behavior, and 100% clean-room test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* two distinct cockpit tabs with sensor fields and section dividers,
• *When* `insertSensorFieldAt` executes for Tab 1,
• *Then* Tab 1 updates cleanly and all records in Tab 2 remain 100% untouched.
• *Given* an insertion error or constraint failure mid-transaction,
• *When* the exception is caught,
• *Then* the transaction rolls back completely and zero rows or dividers remain shifted.
• *Given* a gapped sensor grid (e.g. rows 1, 3, 5) or out-of-bounds insertion (`rowNr = 999`),
• *When* `insertSensorFieldAt` and `normalizeGrid(db, tabViewId)` complete,
• *Then* row and column coordinates are contiguous $1..N$ with zero gaps.
• *Given* invalid bounds (`tabViewId <= 0` or `rowNr < 1`),
• *When* `insertSensorFieldAt` is called,
• *Then* the method returns immediately without executing any database writes.

**Invariants**: Multi-tab isolation, contiguous coordinates, divider alignment, passive onOpen, and 100% clean-room test pass rate.

### REQ-UI-414: Workout Cards & Details Settings Streamlining: Redundant Details Column Removal, Authoritative Tab Section Activation, Collapsible Summary Reorder List, and Scoped Zone Mode Selectors [Verified]
- **Tests**: `TST-UI-377`, `TST-UI-456`, `TST-UI-457`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabConfig.kt`, `strings.xml`
- **Rationale**: `WorkoutMasksAndCardsSection.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsSection.kt`) and `WorkoutDetailSectionAvailability.isSectionActive` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabConfig.kt`) SHALL eliminate the redundant "Details" checkbox column and header from the aftermath settings matrix, make detail tab configuration in `WorkoutDetailTabConfig` strictly authoritative over section activation by evaluating solely data presence and tab section inclusion rather than legacy boolean preference flags, partition the settings screen into visually distinct "Trainingsliste" and "Trainings-Details" domain sections, collapse the reorderable summary cards list by default with active count toggle indicator, scope the zone display mode selectors into their respective domain blocks, integrate full configuration for Workout Photos (visibility, Add-button tile, and thumbnail preview size), and enforce strictly < 400 lines modularity (ATT-4043, ATT-5474, ATT-5655, refining `REQ-UI-240`, `REQ-UI-264`, `REQ-UI-271`, `REQ-UI-408`, and `REQ-UI-448`):
1. *Elimination of Redundant Details Column & Authoritative Tab Assignment (`REQ-UI-414.1`)*:
• `WorkoutMasksAndCardsSection.kt` SHALL remove the "Details" checkbox column and `tuning_matrix_col_details` header from the 10-row reorderable section table.
• `WorkoutDetailSectionAvailability.isSectionActive(section, detailPreferences)` SHALL evaluate section activation strictly based on non-empty data presence (`hasDescription`, `hasExtrema`, `hasMap`, `hasElevation`, `hasCharts`, `hasZones`, `hasLaps`, `hasStrava`, `hasExportStatus`, `hasPhotos`) and tab section inclusion in `WorkoutDetailTabConfig`, and SHALL NOT gate section activation on legacy boolean flags in `WorkoutDetailPreferences` (`showDescription`, `showExtrema`, `showMap`, etc.), ensuring tab assignment in `WorkoutDetailTabConfig` is the sole authoritative configuration source for the Workout Details screen.
2. *Domain Partitioning: Trainingsliste (Summary) vs. Trainings-Details (`REQ-UI-414.2`)*:
• `WorkoutMasksAndCardsSection.kt` SHALL partition its layout into two distinct visual domain blocks: (a) Trainingsliste (Summary-Karten) dedicated to workout summary cards in the workout list, and (b) Trainings-Details (Detail-Ansicht) dedicated to the full-screen Workout Details screen and its tabs.
3. *Collapsible Summary Cards Reorder List (`REQ-UI-414.3`)*:
• The 10-row reorderable summary card section list in the Trainingsliste block SHALL be collapsible by default.
• A toggle header/button SHALL present the title and active card count (`R.string.workout_card_reorder_toggle`, showing `%1$d active` / `%1$d aktiv`) alongside an expandable chevron icon (`KeyboardArrowDown` when collapsed, `KeyboardArrowUp` when expanded).
• Tapping the toggle SHALL expand or collapse the 10-row table without disrupting the athlete's current configuration.
• When expanded, the table header SHALL render a 32dp spacer, Element title (`R.string.tuning_matrix_feature`, weight 1f), and a single 50dp centered header (`R.string.tuning_matrix_col_list`), with each row containing 32dp Up/Down reorder icon buttons, the localized section title, and a centered 50dp Checkbox controlling `workoutCardPrefs` visibility.
4. *Domain-Scoped Zone Card Display Mode & Lap Display Mode Placement (`REQ-UI-414.4`)*:
• The *Zonenkarten-Anzeigemodus* (`ZoneDisplayModeSelector`) SHALL be split and placed within its respective domain block: the Summary Zone Display Mode selector SHALL reside within the Trainingsliste block (`workoutCardPrefs.zoneCardDisplayMode`), and the Details Zone Display Mode selector SHALL reside within the Trainings-Details block (`workoutDetailPrefs.zoneCardDisplayMode`), directly alongside the "Detail-Tabs konfigurieren" action button.
• The *Runden-Anzeigemodus* (`LapDisplayMode`: Table vs Visualizer) SHALL reside within the Trainingsliste block when `workoutCardPrefs.showLaps` is enabled.
5. *Modularity & File Length Limit (`REQ-UI-414.5`)*:
• `WorkoutMasksAndCardsSection.kt` SHALL remain strictly under 400 lines in compliance with `REQ-PRO-011`.
6. *Workout Photos Configuration, Responsive Sizing & Dynamic Summary Slotting (`REQ-UI-414.6` / `ATT-5474`, `ATT-5655`)*:
• In `WorkoutMasksAndCardsSection.kt`, the system SHALL integrate `WorkoutSectionType.PHOTOS` into the reorderable summary section table and `WorkoutDetailSectionType.PHOTOS` into tab assignments.
• The system SHALL provide DataStore preferences in `WorkoutCardSectionPreferences` and `WorkoutDetailPreferences` for `showPhotos` (default `true`), `showPhotoAddButton` (default `true`), and `photoThumbnailSize` (`PhotoThumbnailSize`: `SMALL`, `MEDIUM`, `LARGE`, default `MEDIUM`).
• In `WorkoutPhotoStrip.kt`, photo thumbnail cards and the add-photo tile SHALL calculate responsive viewport-relative dimensions using `BoxWithConstraints(availableWidth = maxWidth - 32.dp)`:
  - `LARGE`: 1 full-width photo per row (`width = availableWidth`, `height = (availableWidth * 0.65f).coerceIn(180.dp, 260.dp)`, horizontally scrollable when $> 1$ photo exists).
  - `MEDIUM`: 2 photos side-by-side per row (`width = (availableWidth - 8.dp) / 2`, square aspect ratio, horizontally scrollable when $> 2$ photos exist).
  - `SMALL`: 3–4 photos side-by-side per row (`width = ((availableWidth - 24.dp) / 3.5f).coerceIn(60.dp, 100.dp)`, square aspect ratio).
• When `showPhotos` is enabled in `WorkoutMasksAndCardsSection.kt`, subordinate controls SHALL allow toggling `showPhotoAddButton` and selecting `photoThumbnailSize` via a 3-segment `SingleChoiceSegmentedButtonRow`.
• In `WorkoutSummary.kt`, `WorkoutPhotoStrip` SHALL be dynamically ordered within `workoutSectionsOrder`, respecting `showPhotos`, `showPhotoAddButton`, and `thumbnailSize`.
• `WorkoutMasksAndCardsSection.kt` SHALL remain strictly under 400 lines via modular `WorkoutPhotoPreferencesComponents.kt`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-414` (amended by `ATT-5655`), refining `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections*), `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout*), `REQ-UI-271` (*Simplified Preposition-Free Column Headers*), `REQ-UI-408` (*Individually Configurable Workout Details Tabs*), and `REQ-UI-448` (*Workout Photos Strip*). Targets `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabConfig.kt`, `WorkoutPhotoPreferencesComponents.kt`, `WorkoutPhotoStrip.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Commit `02f3d414` (Sprint 2026-40.12, initial 2-column matrix), commit `6817b037` (Sprint 2026-40.14, singular headers), commit `c8939a3f` (Sprint 2026-42.8, ATT-3920 configurable detail tabs), commit `5c9d9653` (Sprint 2026-45.6, ATT-43 workout photos), commit `8aed20fc` (Sprint 2026-45.8, ATT-5474 photo settings), and Sprint 2026-45.9 (`ATT-5655`).
3. *Root Reason for Existing Formulation*: When the 2-column matrix was introduced, Workout Details had no tabs. In ATT-5474, fixed thumbnail DP constants (56dp/76dp/104dp) were used, causing multiple small tiles even on *Groß*.
4. *Preservation of Core Invariants*: Summary card section reordering (`DEFAULT_ORDER`), DataStore persistence (`WorkoutCardSectionPreferences`), and active card visibility in `WorkoutSummary.kt` must remain 100% intact; tab validation rules in `WorkoutDetailTabConfig` must remain intact; minimum 48dp touch targets on checkboxes and buttons; strictly < 400 lines file length limit preserved via modularity; safe downsampled bitmap decoding; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete assigning the `MAP` section to a tab in `WorkoutDetailTabsConfigSheet`,
• *When* opening that tab on a workout with a GPS track,
• *Then* the Map view SHALL render regardless of legacy `WorkoutDetailPreferences.showMap` boolean state.
• *Given* an athlete opening *Advanced Settings -> Trainingsliste & Detail-Ansicht*,
• *When* the screen composes,
• *Then* the 10-row summary cards list is collapsed by default showing the active count, the "Details" column is absent from the table, and two distinct sections for Summary Cards and Detail-Ansicht are displayed.
• *When* the collapsible toggle is tapped,
• *Then* the 10 reorderable rows expand with Up/Down buttons and single-column checkboxes for `WorkoutCardSectionPreferences` (including `PHOTOS`).
• *Given* the Summary and Details configuration blocks,
• *When* adjusting *Zonenkarten-Anzeigemodus*,
• *Then* the Summary selector updates `workoutCardPrefs.zoneCardDisplayMode` and the Details selector updates `workoutDetailPrefs.zoneCardDisplayMode`.
• *Given* an athlete configuring Workout Photos in `WorkoutMasksAndCardsSection.kt`,
• *When* toggling `showPhotos`, `showPhotoAddButton`, or changing `photoThumbnailSize` (Small/Medium/Large),
• *Then* preferences are persisted immediately in DataStore and dynamically reflected in `WorkoutSummary` and `WorkoutDetailTabbedContent`.

**Invariants**: Summary card reorder persistence, active card visibility in WorkoutSummary, tab validation rules in WorkoutDetailTabConfig, 48dp touch targets, and 100% clean-room test pass rate.

### REQ-UI-415: Single-Line Power Row and Visual Domain Row Separation with Subtle Horizontal Dividers in WorkoutDetails [Verified]
- **Tests**: `TST-UI-378`, `TST-UI-388`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- Preceding `PowerTrainingMetricsSection` when power slots are present (`powerSlots.isNotEmpty()`).
- Preceding `AerobicEfficiencyRow` when `enableAerobicEfficiencyModel && showAerobicEfficiencyInWorkoutSummary && data.efficiencyFactor != null`.
- Preceding `AnaerobicBatteryRow` when `enableCriticalPowerModel && showCriticalPowerInWorkoutSummary && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
• Dividers SHALL only be rendered when the corresponding section is actually visible, ensuring zero leading, zero trailing, and zero consecutive duplicate dividers.
• The outer `Column` vertical spacing SHALL be adjusted to `Arrangement.spacedBy(12.dp)` to provide balanced 12dp margins above and below each divider.
3. *Preservation of Design System Standards & Line Count Modularity (`REQ-UI-415.3`)*:
• `WorkoutDetails.kt` SHALL adhere to `docs/design_guidelines.md` Section 5.10 (`WorkoutDomainRow` with `24.dp` domain icon, `20.dp` spacer, `Modifier.weight(1f)` items).
• `WorkoutDetails.kt` SHALL maintain zero `FlowRow` and zero `surfaceContainerHighest` pill badges.
• `WorkoutDetails.kt` SHALL remain strictly under 500 lines.
4. *9-Language Localization Parity (`REQ-UI-415.4`)*:
• All existing power metric strings (`normalized_power_short`, `intensity_factor_short`, `tss_short`, `workout_power_value_watts_format`, `workout_power_value_if_format`, `workout_power_value_tss_format`, `workout_power_metrics_vi_format`) remain fully defined across all 9 application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-415`, refining `REQ-UI-404` (*AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails*). Targets `WorkoutDetails.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`).
2. *Historical Origin & Commit Trace*: Sprint 2026-42.8 (`ATT-3930`, commit `c8939a3f`) and refined in Sprint 2026-43.3 (`ATT-4432`).
3. *Root Reason for Existing Formulation*: When migrating from legacy pill badges to the Section 5.10 `WorkoutDomainRow` layout in `ATT-3930`, the developer retained both Coggan metrics (`NP`, `IF`, `TSS`) and physics metrics (`Work`, `W/kg`) by rendering two consecutive `Row` composables in `PowerTrainingMetricsSection` so that no metric was lost. However, on-device review on Pixel 10 revealed that two stacked rows disrupted the horizontal cadence of the card and crowded the layout. In `ATT-4432`, user feedback established that basic physics metrics are not a physiology model and must not be shown in summary rows at all.
4. *Preservation of Core Invariants*: The canonical `WorkoutDomainRow` structure (`24.dp` domain icon, `20.dp` spacer, right-hand column with `Modifier.weight(1f)`) remains 100% intact; basic physics metrics (`Work kJ`, `W/kg`) remain fully available in live cockpit tiles; zero `FlowRow` and zero `surfaceContainerHighest` pill badges; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with `normalizedPowerWatts = 215.0`, `intensityFactor = 0.78`, `tss = 91.0`, `workKj = 1080.0`, `avgPowerToWeight = 2.87`, and `enableCogganPowerMetrics = true`,
• *When* `PowerTrainingMetricsSection` is rendered,
• *Then* exactly ONE horizontal row is rendered displaying `NP` (with `VI`), `IF`, and `TSS` in 3 equal-weight columns, and `Work` / `W/kg` are omitted to preserve single-line cadence.
• *Given* a workout with `normalizedPowerWatts = 215.0`, `workKj = 1080.0`, `avgPowerToWeight = 2.87`, null `intensityFactor`, null `tss`, and `enableCogganPowerMetrics = true`,
• *When* `PowerTrainingMetricsSection` is rendered,
• *Then* exactly ONE horizontal row is rendered displaying strictly `NP` (with `VI`), and `Work (kJ)` / `W/kg` are NOT rendered.
• *Given* a workout with null `normalizedPowerWatts` (or `enableCogganPowerMetrics = false` or `showCogganPowerInWorkoutSummary = false`), `workKj = 1080.0`, and `avgPowerToWeight = 2.87`,
• *When* `PowerTrainingMetricsSection` is rendered,
• *Then* `PowerTrainingMetricsSection` and its preceding divider are completely omitted.
• *Given* `WorkoutDetails` rendered for a workout with active time/distance, altitude, power, efficiency, and W'bal,
• *When* inspected,
• *Then* a `HorizontalDivider` is rendered before each subsequent visible section (altitude, power, efficiency, battery), separating all domain rows with balanced 12dp spacing.
• *Given* an indoor workout without altitude data,
• *When* inspected,
• *Then* no divider is rendered for altitude, and the first divider appears directly before the first subsequent visible domain row.

**Invariants**: Section 5.10 / Rule 42 compliance, `ATT-3929` toggle gating, 9-locale parity, and 100% clean-room test pass rate MUST NOT be broken.
- **Rationale**: `WorkoutDetails.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`) SHALL consolidate the *Leistung* (Power) domain row (`PowerTrainingMetricsSection`) into at most ONE single horizontal row displaying 2 or 3 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns exclusively for Coggan Power Metrics, eliminating stacked 2-row layouts and physics metric fallback, and SHALL render a subtle `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` immediately preceding each subsequent visible domain row (`AltitudeRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, `AnaerobicBatteryRow`) with balanced 12dp spacing (ATT-4042, ATT-4432, refining `REQ-UI-404`):
1. *Single-Line Power & Coggan Load Row (`PowerTrainingMetricsSection`, `REQ-UI-415.1`)*:
• `WorkoutDetails.kt` (`PowerTrainingMetricsSection`) SHALL render at most ONE single horizontal `Row(modifier = Modifier.fillMaxWidth())` inside `WorkoutDomainRow` displaying 2 or 3 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns exclusively for Coggan Power Metrics.
• When `enableCogganPowerMetrics == true`, `showCogganPowerInWorkoutSummary == true`, and `data.normalizedPowerWatts != null`:
- If both FTP-dependent metrics `data.intensityFactor != null` and `data.tss != null` are present: render `[NP (with secondary VI), IF, TSS]`.
- If `data.intensityFactor == null` or `data.tss == null` (e.g. athlete has no configured FTP): render `NP` (with secondary `VI` if present) followed by available metrics (`IF` if present, `TSS` if present). Basic mechanical work (`kJ`) and `W/kg` SHALL NOT be appended.
• When `enableCogganPowerMetrics == false`, `showCogganPowerInWorkoutSummary == false`, or `data.normalizedPowerWatts == null`:
- `PowerTrainingMetricsSection` and its preceding divider SHALL return without rendering (zero fallback to `Work (kJ)` or `W/kg`).
2. *Visual Separation Between Domain Rows in `WorkoutDetails` (`REQ-UI-415.2`)*:
• `WorkoutDetails` SHALL render a subtle `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` immediately preceding any visible domain row after the initial Time/Distance row:
- Preceding `AltitudeRow` when `data.ascentMeters > 0

### REQ-UI-416: Real-time W' Balance (W-Prime Battery) Expenditure, Reconstitution, Battery Gauge Bar, and Warning Alert [Verified]
- **Tests**: `TST-UI-379`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WPrimeBatteryState.kt`, `WPrimeBatteryMath.kt`, `WPrimeBatteryGaugeBar.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`, `CyclingPowerMetricsDevice.java`, `SettingsDataStore.kt`
- **Rationale**: The system SHALL maintain continuous 1-Hz tracking of remaining $W'$ ($W'_{\text{bal}}$ in kJ) in `CyclingPowerMetricsDevice.java` using Dr. Phil Skiba's differential expenditure and exponential reconstitution model, render a high-visibility, True-Black AMOLED-compliant battery gauge bar with smooth animated fill interpolation inside `SensorFieldView.kt` for `SensorType.W_PRIME_BAL`, display percentage remaining ($0\text{–}100\%$), trigger an immediate color-coded warning alert when $W' < 20\%$, enforce fixed-height layout stability across active and stale states, and preserve progressive disclosure gating (`REQ-UI-409.3`) and 9-language localization parity (ATT-1296, extending `REQ-UI-399` and `REQ-UI-409`):
1. *Skiba W' Balance Differential Algorithm & Device Hardening (`REQ-UI-416.1`)*:
• In `CyclingPowerMetricsDevice.java`, `calculateMetrics()` SHALL compute $W'_{\text{bal}}$ at 1 Hz based on instantaneous power vs Critical Power ($CP$ in W) and user anaerobic work capacity ($W'$ in kJ), delegating to `PowerPhysiologyCalculator.stepWPrimeBalanceJoules`.
• `resolveEffectiveCpWatts()` SHALL resolve Critical Power via `mCpProvider.getCpWatts(mContext)`, falling back to `mFtpProvider.getFtp(mContext)`, and falling back to `SettingsDataStore.DEFAULT_CYCLING_FTP` (`200` W) if neither is positive, with multi-tier null-safety.
• `resetSession()` SHALL invoke `resolveEffectiveCpWatts()` to restore full $W'$ capacity on session reset even when $CP$ is unconfigured and only $FTP$ is present.
• During recording suspension (paused / auto-paused), $W'_{\text{bal}}$ SHALL continue exponential reconstitution at $P = 0\text{ W}$.
• *Mid-Session Parameter Change Integrity*: If $CP$ or $W'$ capacity is modified mid-session, the tracking engine SHALL re-evaluate the normalized remaining fraction against the new capacity $W'_{\text{new}}$, clamping remaining balance to $[0.0, W'_{\text{new}}]$ without causing negative values, mathematical underflows, or discontinuous state corruption.
2. *W' Battery State Model & Mathematical Normalization (`REQ-UI-416.2`)*:
• `WPrimeBatteryState` SHALL be an immutable data class capturing `val remainingKj: Double?`, `val totalKj: Double`, `val percentage: Int`, `val batteryFraction: Float?`, `val warningLevel: WPrimeWarningLevel`, `val batteryColor: Color`, `val isWarning: Boolean`, `val isStale: Boolean`, and `val timestampMs: Long`.
• `WPrimeBatteryMath.calculateBatteryState(...)` SHALL normalize remaining energy against an immutable snapshot of total $W'$ capacity, clamping `percentage` to $[0..100]$ and `batteryFraction` to $[0.0f..1.0f]$.
• `warningLevel` SHALL evaluate to `NORMAL` ($> 30\%$, `TTColor.WPrimeBalGreen`), `LOW` ($20\% \le \text{pct} \le 30\%$, `TTColor.Zone3`), `CRITICAL` ($0\% < \text{pct} < 20\%$, `TTColor.Zone5`), and `DEPLETED` ($\le 0\%$, `TTColor.Zone5`).
• `isWarning` SHALL evaluate to `true` when `percentage < 20 && !isStale`.
• `isStale` SHALL evaluate to `true` when telemetry data is absent, null, non-finite, or older than `STALE_TIMEOUT_MS = 3000L`. In `TrackingViewModel`, staleness evaluation SHALL be executed via periodic flow/ticker monitoring comparing elapsed time against `timestampMs`, immediately emitting an updated state with `isStale = true` if the delta exceeds $3000\text{ ms}$ without waiting for subsequent telemetry packets.
3. *Cockpit Battery Gauge Composable & Layout Invariants (`REQ-UI-416.3`)*:
• `WPrimeBatteryGaugeBar.kt` SHALL render a horizontal battery gauge track with high-contrast outline (`outlineVariant.copy(alpha = 0.6f)`), rounded capsule corners, terminal end-cap nipple, proportional fill animated via `animateFloatAsState(targetValue = fraction, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))`, and compact percentage badge when `viewSize >= ViewSize.NORMAL`.
• **Animation Continuity**: `WPrimeBatteryGaugeBar` SHALL support continuous animation updates: if a new `fraction` target is received while an animation is in progress, `animateFloatAsState` SHALL smoothly interpolate from the current animated value to the new target without visual snapping or animation reset.
• **Boundary Anti-Clipping Padding**: The fill bar SHALL be constrained with `endPadding = 3.dp` from the terminal nipple so at 100% capacity the fill never overlaps or clips the terminal cap, and at 0% renders cleanly as an empty outlined capsule track.
• **Fixed-Height Container Invariant & Font Scaling Robustness**: The composable container SHALL be constrained to a fixed vertical slot (`Modifier.height(14.dp)`). The percentage badge text SHALL be constrained with `maxLines = 1`, `overflow = TextOverflow.Clip`, and compact typography (`style = MaterialTheme.typography.labelSmall`), ensuring the 14dp height slot is strictly enforced without layout expansion regardless of system accessibility font scaling or active/stale state.
• **Stale State UI Contract**: When telemetry is stale, the gauge SHALL remain rendered with dimmed track opacity (`alpha = 0.35f`) and empty fill, rather than collapsing or hiding.
4. *Live Cockpit Integration & Color-Coded Warning Alert (`REQ-UI-416.4`)*:
• In `SensorFieldState.kt`, `val wPrimeBatteryState: WPrimeBatteryState? = null` SHALL be exposed with a default parameter, preserving in-memory backward compatibility.
• In `SensorFieldView.kt`, `WPrimeBatteryGaugeBar` SHALL be rendered directly beneath the primary value and unit for `SensorType.W_PRIME_BAL` fields when `wPrimeBatteryState != null`.
• When `isWarning == true` ($W' < 20\%$), the tile's outline border SHALL switch to high-visibility Warning Red (`MaterialTheme.colorScheme.error` / `TTColor.Zone5`), providing immediate biofeedback without touch interaction.
• In `TrackingViewModel.kt`, `wPrimeBatteryState` SHALL be computed in `applySensorData` using an immutable snapshot of `totalWPrimeKj` per 1-Hz frame, stamped with the frame timestamp.
• Progressive disclosure gating (`REQ-UI-409.3`) SHALL be strictly preserved: when `enableCriticalPowerModel == false`, the entire `W_PRIME_BAL` tile is excluded from `visibleFields` and row height collapses to 0.
5. *9-Language Localization Parity (`REQ-UI-416.5`)*:
• All new UI strings (`w_prime_battery_pct` with format `%1$d%%`, `w_prime_warning_low_battery` e.g. `W\' Battery Low (<20%)`) SHALL exist across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-416`, extending `REQ-UI-399` (ATT-3862) and `REQ-UI-409` (ATT-3921). Targets `WPrimeBatteryState.kt`, `WPrimeBatteryMath.kt`, `WPrimeBatteryGaugeBar.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`, `CyclingPowerMetricsDevice.java`, `SettingsDataStore.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: `ATT-3862` (`61ccb801dc18`), `ATT-3921` (`a6289096638b`), `ATT-3106` (`REQ-UI-376`), and `ATT-3107` (`REQ-UI-377`).
3. *Root Reason for Existing Formulation*: While ATT-3862 introduced Skiba mathematical modeling and scalar Aftermath telemetry, the live cockpit tile only rendered scalar kJ numbers without an intuitive visual battery gauge, without percentage capacity, and without a low-battery color-coded warning alert.
4. *Preservation of Core Invariants*: Preservation of Skiba math formulas, 44-sensor catalog partition, `DB_VERSION = 13`, progressive disclosure gating (`REQ-UI-409.3`), True-Black AMOLED compliance, and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active ride with power telemetry above Critical Power ($P > CP$),
• *When* 1-Hz metrics are computed in `CyclingPowerMetricsDevice`,
• *Then* $W'_{\text{bal}}$ depletes according to $(P - CP) \cdot 1\text{s}$, and the battery gauge fill fraction decreases accordingly.
• *Given* an active ride where power drops below Critical Power or during a pause,
• *When* 1-Hz metrics are computed,
• *Then* $W'_{\text{bal}}$ reconstitutes exponentially toward full capacity $W'$.
• *Given* remaining $W'$ dropping below $20\%$,
• *When* rendered in `SensorFieldView`,
• *Then* the battery gauge bar turns Red (`TTColor.Zone5`), and the card's border switches to Warning Red.
• *Given* telemetry sensor disconnection or missing power samples,
• *When* `WPrimeBatteryGaugeBar` is rendered,
• *Then* it maintains its fixed 14.dp height slot with dimmed track opacity (0.35 alpha) and empty fill, with zero layout jumping, transitioning to stale within $\le 3000\text{ ms}$.
• *Given* mid-session baseline parameter change of $CP$ or $W'$,
• *When* recalculating W' battery state,
• *Then* remaining balance is safely clamped to $[0.0, W'_{\text{new}}]$ and normalized without discontinuous exceptions.
• *Given* `enableCriticalPowerModel == false`,
• *When* `SensorGridScreen` evaluates visible fields,
• *Then* the `W_PRIME_BAL` tile is completely hidden and row height collapses to 0.

**Invariants**: Skiba differential math formulas, 44-sensor catalog partition, `DB_VERSION = 13`, progressive disclosure gating (`REQ-UI-409.3`), True-Black AMOLED compliance, and 100% clean-room test pass rate.

### REQ-UI-417: Distance Interval Filtering (Min/Max Range and Preset Intervals) in Routes and Favorite Routes [Verified]
- **Tests**: `TST-UI-380`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• In `RouteFilterCriteria.matches(route: RouteWithPath)`: The system SHALL evaluate `if (minDistanceMeters != null && route.summary.distance < minDistanceMeters) return false` and `if (maxDistanceMeters != null && route.summary.distance > maxDistanceMeters) return false`.
• In `ClusterFilterCriteria.matches(cluster: WorkoutCluster, linkedEquipment: Set<String>)`: The system SHALL evaluate `if (minDistanceMeters != null && cluster.refDistance < minDistanceMeters) return false` and `if (maxDistanceMeters != null && cluster.refDistance > maxDistanceMeters) return false`.
• Persistence & Backward Compatibility: `toJson()` in both models SHALL serialize `maxDistanceMeters` as a JSON number when non-null. `fromJson()` SHALL deserialize `maxDistanceMeters` safely if present in JSON. Legacy JSON records lacking `maxDistanceMeters` SHALL resolve cleanly to `null` without throwing exceptions or corrupting existing filter state.
2. *Modal Filter Bottom Sheets (`RouteFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`)*:
• In `RouteFilterBottomSheet.kt`, distance and elevation gain SHALL be separated into distinct sections.
• **Distance Section** (`R.string.filter_section_distance`): SHALL provide preset chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`) and two-way synchronized custom `OutlinedTextField` inputs for `Min (km)` (`R.string.filter_min_label`) and `Max (km)` (`R.string.filter_max_label`) with `KeyboardType.Decimal`. Selecting a preset chip SHALL update numeric bounds and text representations; manual text editing SHALL update numeric bounds (multiplying entered km by 1000.0) and deselect mismatched chips.
• **Elevation Gain Section** (`R.string.sort_elevation_gain`): In `RouteFilterBottomSheet.kt`, elevation gain thresholds (`> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`) SHALL be retained under their own dedicated section title.
• In `ClusterFilterBottomSheet.kt`, Section 3 SHALL replace single-sided distance chips with the identical **Distance Section** (`R.string.filter_section_distance`) offering preset interval chips and synchronized `Min (km)` and `Max (km)` decimal text fields matching `RouteFilterBottomSheet` and `WorkoutFilterBottomSheet`.
3. *Active Filter Chip Strips (`ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, Host Screens)*:
• When `minDistanceMeters != null
- **Rationale**: The system SHALL support bounded distance interval filtering ($[D_{\text{min}}, D_{\text{max}}]$ in meters) across Routes (`RouteFilterCriteria`, `RouteFilterBottomSheet`) and Favorite Routes / Workout Clusters (`ClusterFilterCriteria`, `ClusterFilterBottomSheet`), harmonizing filter mechanics with Workout filtering (`REQ-UI-157`):
1. *Immutable Criteria Domain Models (`RouteFilterCriteria.kt`, `ClusterFilterCriteria.kt`)*:
• `RouteFilterCriteria` and `ClusterFilterCriteria` SHALL declare property `val maxDistanceMeters: Double? = null` with default `null`.
• `activeFilterCount` in both models SHALL treat distance as a single active filter dimension if and only if `minDistanceMeters != null

### REQ-UI-418: Single Percent Symbol (%) Parity in Non-Formatted Efficiency, Decoupling, and W'bal String Resources & Repository-Wide Percent Integrity Enforcement [Verified]
- **Tests**: `TST-UI-381`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/values*/strings.xml`, `WPrimeBatteryLocalizationTest.kt`, `LocalizationPercentIntegrityTest.kt`
- **Rationale**: The system SHALL replace the erroneous escaped double percent symbol (`%%`) with a standard single percent symbol (`%`) across all 5 non-formatted string resources (`workout_decoupling_coupled`, `workout_decoupling_drift`, `workout_w_prime_min_pct_label`, `workout_w_prime_empty_tank`, `w_prime_warning_low_battery`) across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`), ensuring accurate display on `WorkoutDetails` summary cards and W' battery telemetry alerts, AND SHALL enforce repository-wide percent symbol integrity across all string resource files via automated contract testing (ATT-4292, refining `REQ-UI-404.3`, `REQ-UI-404.4`, and `REQ-UI-416.5`):
1. *Aerobic Efficiency Decoupling Status Strings (`REQ-UI-418.1`)*:
• Across all 9 application locales, `workout_decoupling_coupled` SHALL specify `<5%` (e.g. EN: `Coupled (<5%)`, DE: `Stabil (<5%)`) with a single `%` character.
• Across all 9 application locales, `workout_decoupling_drift` SHALL specify `≥5%` (e.g. EN: `High Drift (≥5%)`, DE: `Hohe Drift (≥5%)`) with a single `%` character.
2. *W'bal Metric Label & Alert Strings (`REQ-UI-418.2`)*:
• Across all 9 application locales, `workout_w_prime_min_pct_label` SHALL specify a single `%` character (EN: `Min %`, DE/FR/IT/NL/PL: `Min. %`, ES/PT: `Mín. %`, JA: `最小 %`).
• Across all 9 application locales, `workout_w_prime_empty_tank` SHALL specify `<10%` (e.g. EN: `Empty Tank (<10%)`, DE: `Tank leer (<10%)`) with a single `%` character.
• Across all 9 application locales, `w_prime_warning_low_battery` SHALL specify `<20%` (e.g. EN: `W\' Battery Low (<20%)`, DE: `W\'-Akku schwach (<20%)`) with a single `%` character.
3. *Preservation of Formatted String Escaping (`REQ-UI-418.3`)*:
• String resources containing active format placeholders (including `%1$d%%`, `%1$.1f%%`, `%2$.1f%%`, `%1$.0f%%`) SHALL retain `%%` for `String.format` evaluation.
4. *Repository-Wide Percent Integrity Contract Test (`REQ-UI-418.4`)*:
• Automated test `LocalizationPercentIntegrityTest.kt` SHALL inspect all `<string>` resources across all 9 locales, verifying that: (a) all 5 fixed keys contain `%` and do NOT contain `%%`; (b) any string resource across the entire repository that contains `%%` also contains at least one standard format placeholder token (`%s`, `%d`, `%[0-9]+\$`, etc.).
• `WPrimeBatteryLocalizationTest.kt` SHALL assert that `w_prime_warning_low_battery` does NOT contain `%%`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-404.3`, `REQ-UI-404.4` (`ATT-3930`) and `REQ-UI-416.5` (`ATT-1296`). Targets `app/src/main/res/values*/strings.xml`, `WPrimeBatteryLocalizationTest.kt`, and creates `LocalizationPercentIntegrityTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint `2026-42.8` (`ATT-3930` commit `1bd468bf`, `ATT-1296` commit `f519df49`).
3. *Root Reason for Existing Formulation*: String keys were drafted with `%%` under the erroneous assumption that Android XML string resources universally require `%%` escaping for percent signs, or copied from neighboring format strings (`workout_decoupling_pct_format` = `%1$.1f%%`, `w_prime_battery_pct` = `%1$d%%`). In non-formatted strings invoked via `stringResource(id)`, `String.format` is never executed, leaving the literal double percent visible in the UI.
4. *Preservation of Core Invariants*: Correcting `%%` to `%` in non-formatted strings preserves the exact translations, layout hierarchy, and formatting across all 9 locales while restoring clean visual presentation (`<5%`, `≥5%`, `Min %`, `<10%`, `<20%`). All formatted strings (`%1$.1f%%`, `%1$d%%`) legitimately requiring `%%` remain untouched.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutDetails.kt` rendering aerobic decoupling status in any of the 9 locales,
• *When* the status subtitle is resolved via `stringResource(R.string.workout_decoupling_coupled)` or `stringResource(R.string.workout_decoupling_drift)`,
• *Then* the displayed text contains a single percent symbol (`<5%` or `≥5%`) and does NOT contain `%%`.
• *Given* `WorkoutDetails.kt` rendering W'bal minimum percentage in any of the 9 locales,
• *When* the column label or secondary empty tank alert is resolved via `stringResource(R.string.workout_w_prime_min_pct_label)` or `stringResource(R.string.workout_w_prime_empty_tank)`,
• *Then* the label renders `Min %` / `Min. %` / `Mín. %` / `最小 %` and the secondary alert renders `<10%` without double percent symbols.
• *Given* the live tracking cockpit or low battery alert evaluating `w_prime_warning_low_battery`,
• *When* the warning string is resolved across any of the 9 locales,
• *Then* it contains `<20%` with a single `%` character and does NOT contain `%%`.
• *Given* all string resource files across all 9 locales,
• *When* audited by `LocalizationPercentIntegrityTest.kt`,
• *Then* zero non-formatted strings contain `%%`.

**Invariants**: 9-language localization parity; format strings retaining `%%` for `String.format`; zero regression in existing test suite; parent ticket Human Decision Gate strictly enforced.

### REQ-UI-419: Power Histogram Rare Sprint Spike Folding, Power Zone Boundary Snapping, and Consistent Symmetrical Header Range Toggle Navigation [Verified]
- **Tests**: `TST-UI-382`, `TST-UI-383`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryHistogramCalculator.kt`, `TelemetryHistogramChart.kt`, `ZoneDistributionModels.kt`, `PowerZoneDistributionCard.kt`
- **Rationale**: The system SHALL fold rare high-wattage power spikes in Cycling Power frequency histograms into an aggregated rightmost overflow bar by default, provide interactive zoom navigation into a dedicated Sprint Tail view with independent local Y-axis scaling, provide a symmetric 2-segment header range toggle with consistent active segment highlighting and navigation affordances restoring the Main View, and snap the tail cutoff to an enclosing upper Power Zone boundary, while preserving linear binning for non-skewed rides and Heart Rate histograms (ATT-4293, ATT-4370, complementing `REQ-UI-372`, `REQ-UI-388`, and `REQ-UI-420`):
1. *Power Zone Boundary Snapping Tail Cutoff ($P_{99}$) in `TelemetryHistogramCalculator.kt` (`REQ-UI-419.1`)*:
• In `TelemetryHistogramCalculator.calculatePowerHistogram`, the system SHALL compute the cumulative active duration across frequency bins to locate the 99th percentile duration threshold ($P_{99}$): $P_{99} = \text{bin.rangeMax where } \sum_{i=0}^{k} d_i \ge 0.985 \times T_{\text{total}}$.
• The cutoff candidate SHALL be floored at the athlete's Zone 4 upper boundary (`thresholds.z4Max`) to guarantee that the full aerobic and threshold spectrum (Zones 1–4) remains permanently in the Main View: $\text{candidate} = \max(P_{99}, \text{thresholds.z4Max})$.
• The system SHALL snap the candidate cutoff upward to the enclosing upper Power Zone boundary (`thresholds.z4Max`, `thresholds.z5Max`, or `thresholds.z6Max`):
- If $\text{candidate} \le \text{thresholds.z4Max}$: snap to `thresholds.z4Max`.
- If $\text{thresholds.z4Max} < \text{candidate} \le \text{thresholds.z5Max}$: snap to `thresholds.z5Max`.
- If $\text{thresholds.z5Max} < \text{candidate} \le \text{thresholds.z6Max}$: snap to `thresholds.z6Max`.
- If $\text{candidate} > \text{thresholds.z6Max}$ (above Zone 6, into Zone 7) or if zone thresholds are unconfigured: fall back to rounding up to the nearest 50 W boundary: $\lceil \text{candidate} / 50 \rceil \times 50$.
• The snapped cutoff candidate SHALL be aligned to the histogram bin width: $\text{tailCutoff} = \lceil \text{rawCutoff} / \text{binWidth} \rceil \times \text{binWidth}$.
• Tail folding SHALL be gated and enabled if and only if: (a) $\text{dataMax} - \text{tailCutoff} \ge 10 \times \text{binWidth}$ ($\ge 100\text{ W}$ in the tail); (b) $\text{dataMax} \ge \text{tailCutoff} \times 1.25$; and (c) active duration above `tailCutoff` is strictly $> 0$.
• When gating conditions are not met or when calculating Heart Rate histograms (`calculateHeartRateHistogram`), the system SHALL return standard linear dataset (`tailCutoff = null`, `sprintTail = null`).
2. *Domain Model Extensions in `ZoneDistributionModels.kt` (`REQ-UI-419.2`)*:
• `TelemetryHistogramBin` SHALL declare `val isOverflow: Boolean = false` and `val isReturn: Boolean = false` with backward-compatible defaults.
• `TelemetryHistogramData` SHALL declare `val tailCutoff: Int? = null` and `val sprintTail: TelemetryHistogramData? = null` with backward-compatible defaults.
• When tail folding is active, the Main View dataset SHALL contain regular bins from `dataMin` to `tailCutoff` plus a single rightmost overflow bin (`isOverflow = true`, `rangeMin = tailCutoff`, `rangeMax = dataMax`, `durationSec = \sum_{P_i > \text{tailCutoff}} d_i`, `percentage = (\text{duration} / T_{\text{total}}) \times 100\%`, `zoneIndex = 5` or highest zone, `color = TTColor.Zone5`).
• The `sprintTail` dataset SHALL contain a single leftmost return bin (`isReturn = true`, `binIndex = 0`, `rangeMin = dataMin`, `rangeMax = tailCutoff`, `durationSec = \sum_{P_i \le \text{tailCutoff}} d_i`, `percentage = (\text{duration} / T_{\text{total}}) \times 100\%`, `color = MaterialTheme.colorScheme.outlineVariant`) plus all individual 10 W sprint bins from `tailCutoff` to `dataMax`.
3. *Symmetrical Two-Segment Header Range Toggle Navigation in `TelemetryHistogramChart.kt` (`REQ-UI-419.3`)*:
• `TelemetryHistogramChart` SHALL maintain reactive view state `var isSprintTailView by remember(histogram) { mutableStateOf(false) }`.
• The resting header readout SHALL render a symmetrical 2-segment range toggle alongside the resolution indicator (`Δ ${histogram.binWidth} $unit`), where the currently active range segment is consistently highlighted and the inactive range segment serves as the clickable view switch action:
- In Main View (`!isSprintTailView`), Segment 1 (Main Range `${histogram.dataMin}–${histogram.tailCutoff} $unit`) SHALL be highlighted with `primaryContainer` (e.g. `primaryContainer.copy(alpha = 0.7f)`), bold text in `onPrimaryContainer`, and non-clickable; Segment 2 (Sprint Tail Range) SHALL be rendered as an unhighlighted clickable chip with `surfaceVariant` (e.g. `surfaceVariant.copy(alpha = 0.6f)`), text in `onSurfaceVariant`, and trailing forward arrow icon (`Icons.AutoMirrored.Filled.ArrowForward`), displaying localized string `power_histogram_overflow_label` (`> ${histogram.tailCutoff} $unit`). Tapping Segment 2 or the rightmost overflow bar on the canvas SHALL set `isSprintTailView = true` and clear `selectedBinIndex`.
- In Sprint Tail View (`isSprintTailView`), Segment 1 (Main Range) SHALL be rendered as an unhighlighted clickable chip with `surfaceVariant` (e.g. `surfaceVariant.copy(alpha = 0.6f)`), text in `onSurfaceVariant`, and leading back arrow icon (`Icons.AutoMirrored.Filled.ArrowBack`), displaying localized string `power_histogram_return_label` (`${histogram.dataMin}–${histogram.tailCutoff} $unit`). Tapping Segment 1 or the leftmost return bar on the canvas SHALL set `isSprintTailView = false` and clear `selectedBinIndex`; Segment 2 (Sprint Tail Range `${histogram.tailCutoff}–${histogram.dataMax} $unit`) SHALL be highlighted with `primaryContainer` (e.g. `primaryContainer.copy(alpha = 0.7f)`), bold text in `onPrimaryContainer`, and non-clickable.
4. *Independent Y-Axis Scaling in Sprint Tail View (`REQ-UI-419.4`)*:
• In Sprint Tail View, vertical Y-axis scaling `maxDuration` SHALL be computed strictly across sprint bins (`filter { !it.isReturn }`), excluding the bulk duration of the return bar, elevating 3–15 second sprint efforts to full canvas height ($96\text{ dp}$).
• In Main View, `maxDuration` is computed across all visible bins (`bins.maxOfOrNull { it.durationSec }`), with the overflow bar rendered with a minimum visible height ($\ge 6\text{ dp}$).
5. *Bin Inspection & Drag Scrubbing Parity (`REQ-UI-419.5`)*:
• Tapping or dragging across regular bins in either view SHALL update `selectedBinIndex` and display that bin's exact wattage range, duration, percentage, and zone badge (`Z1`–`Z7`) in the header readout.
• Touching the overflow bar in Main View or return bar in Sprint Tail View immediately triggers the respective view transition without sticking in selected mode.
6. *9-Language Localization & Format Parity (`REQ-UI-419.6`)*:
• All new string resources (`power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, `power_histogram_return_desc`) SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with indexed positional specifiers (`%1$d`, `%2$s`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements `REQ-UI-372.5` (`TelemetryHistogramChart` rendered when `effectiveDisplayMode == HISTOGRAM`), introduced in Sprint 2026-40.8 (`ATT-1845`), refined in Sprint 2026-42.4 (`ATT-3390`), decoupled in Sprint 2026-42.5 (`REQ-UI-388`, `ATT-3773`), expanded in Sprint 2026-43.1 (`ATT-4293`, commit `5694bca5` and `ATT-4294`, commit `5c5367c3`), and refined in Sprint 2026-43.2 (`ATT-4370`). Targets `TelemetryHistogramCalculator.kt`, `TelemetryHistogramChart.kt`, `ZoneDistributionModels.kt`, and `PowerZoneDistributionCard.kt`.
2. *Historical Origin & Commit Trace*: Commit `7b14d9a2` (`ATT-1845`) introduced fine-grained frequency histograms with linear binning; commit `38706240` (`ATT-3390`) introduced 3-way mode switching on individual zone cards; commits `b989e1ce` (`ATT-3676`) and `e927c3da` (`ATT-3773`) established `HISTOGRAM` as default in Workout Details; commit `5694bca5` (`ATT-4293`) introduced tail folding with 50 W multiple rounding and asymmetric header chip buttons; commit `5c5367c3` (`ATT-4294`) added 7 Coggan power zones.
3. *Root Reason for Existing Formulation*: 50 W rounding was originally introduced as a simple numerical rounder before full 7-zone modeling, which caused awkward splits through Zone 5 and Zone 6 just before their upper physiological boundaries. The clickable chip in Main View was styled with `primaryContainer` for visual prominence, which unintentionally conveyed active selection in a 2-view comparison, while Sprint Tail View used unhighlighted `surfaceVariant` with plain text.
4. *Preservation of Core Invariants*: Snapping to upper power zone boundaries reinforces `REQ-UI-419` by ensuring the Main View contains complete, unsevered training zones (Z1–Z4, Z1–Z5, or Z1–Z6); rides without extended tails (`dataMax < tailCutoff * 1.25` or tail $< 100\text{ W}$) retain standard linear mode (`tailCutoff = null`); Heart Rate histograms remain 100% linear; total duration and percentage sums remain mathematically conserved ($100.0\% \pm 0.1\%$); existing resting readout string contracts remain satisfied; 3-way mode switching is preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a cycling workout with a long high-power sprint tail where $P_{99}$ candidate falls in Zone 5 (e.g. between `z4Max` and `z5Max`),
• *When* `calculatePowerHistogram` determines the tail cutoff,
• *Then* `tailCutoff` snaps to `thresholds.z5Max` (aligned to `binWidth`), keeping the entirety of Zone 5 within the Main View.
• *Given* a cycling workout where $P_{99}$ candidate falls in Zone 6 (e.g. between `z5Max` and `z6Max`),
• *When* `calculatePowerHistogram` determines the tail cutoff,
• *Then* `tailCutoff` snaps to `thresholds.z6Max` (aligned to `binWidth`), keeping the entirety of Zone 6 within the Main View.
• *Given* a cycling workout where $P_{99}$ candidate exceeds `thresholds.z6Max`,
• *When* `calculatePowerHistogram` determines the tail cutoff,
• *Then* `tailCutoff` falls back to rounding up to the nearest 50 W boundary.
• *Given* the Power Histogram in Main View,
• *When* rendered on screen,
• *Then* Segment 1 (`0–X W`) is highlighted with `primaryContainer` (bold, non-clickable) and Segment 2 (`> X W →`) is rendered as an unhighlighted clickable chip with `surfaceVariant` and forward arrow.
• *Given* the Power Histogram in Sprint Tail View,
• *When* rendered on screen,
• *Then* Segment 1 (`← 0–X W`) is rendered as an unhighlighted clickable chip with `surfaceVariant` and back arrow, and Segment 2 (`X–Y W`) is highlighted with `primaryContainer` (bold, non-clickable).
• *Given* a steady workout without an extended sprint tail (e.g. indoor trainer ride where `dataMax` is within 25% of $P_{99}$),
• *When* `calculatePowerHistogram` runs,
• *Then* `tailCutoff` is `null`, `sprintTail` is `null`, and the chart renders in standard linear mode.
• *Given* Heart Rate telemetry,
• *When* `calculateHeartRateHistogram` runs,
• *Then* tail folding is never applied and the histogram remains strictly linear.

**Invariants**: Pure mathematical calculation; backward compatibility of data classes with default arguments; mathematical conservation of duration and percentages; Heart Rate histogram invariance; 100% clean-room test pass rate.

### REQ-UI-420: Support all 7 cycling power zones (Z1–Z7) in Power Histogram, TelemetryMetricGraph threshold lines, and Dark Mode chart rendering [Verified]
- **Tests**: `TST-UI-383`, `TST-UI-382`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• In `ZoneDistributionColumnChart.kt` and `ZoneDistributionStackedBar.kt`, active bars, stacked segments, and legend swatches with `TTColor.Zone7` or `TTColor.Zone6` SHALL receive a subtle outline border (0.5.dp, `MaterialTheme.colorScheme.outlineVariant`) on dark surfaces.
4. *ZoneDistributionModels Documentation & Parity (`REQ-UI-420.4`)*:
• KDoc comments in `ZoneDistributionModels.kt` (lines 59, 78, 123) SHALL be updated to document 1..7 zones for cycling power and 1..5 zones for heart rate and pace.
5. *Preservation of System Invariants (`REQ-UI-420.5`)*:
• Heart rate histograms and threshold lines remain strictly 5 zones.
• Mathematical conservation of active duration and percentages (summing to $100.0\% \pm 0.1\%$) is strictly preserved.
• Backward compatibility of `TelemetryHistogramBin` and `TelemetryHistogramData` constructors is preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-420`, complementing `REQ-UI-419` (`ATT-4293`), `REQ-UI-372` (`ATT-1845`), and `REQ-UI-392` (`ATT-3696`). Targets `TelemetryHistogramCalculator.kt`, `TelemetryMetricGraph.kt`, `TelemetryHistogramChart.kt`, `ZoneDistributionColumnChart.kt`, `ZoneDistributionStackedBar.kt`, and `ZoneDistributionModels.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (`ATT-1845`, commit `7b14d9a2`: fine-grained histograms with 5-color array), Sprint 2026-42.5 (`ATT-3696`, commit `0da639ba`: Zone 6 high-contrast brown), Sprint 2026-43.1 (`ATT-4293`, commit `5694bca5`: sprint tail folding inheriting 5-zone clamp).
3. *Root Reason for Existing Formulation*: `TelemetryHistogramCalculator.kt` was originally adapted from 5-zone Heart Rate logic with a placeholder 5-color palette and `coerceIn(0, 4)` clamp. In `TelemetryMetricGraph.kt`, the 4-threshold heart rate helper was copy-pasted to power before `calculatePowerThresholdDashes` was hooked up. Pure black (`#000000`) for Zone 7 was introduced without dark mode container borders, causing it to blend invisibly into dark card backgrounds.
4. *Preservation of Core Invariants*: Heart rate histograms strictly retain 5 zones; power zone bands already support 7 zones; duration and percentage conservation is maintained; 100% clean-room test pass rate preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* cycling power telemetry with samples in Zone 6 (e.g. 380 W where `z5Max = 350 W`, `z6Max = 450 W`),
• *When* `calculatePowerHistogram` evaluates the samples,
• *Then* the corresponding histogram bin has `zoneIndex = 6` and `color = TTColor.Zone6` (Brown `#8B5A2B`).
• *Given* cycling power telemetry with samples in Zone 7 (e.g. 600 W where `z6Max = 450 W`),
• *When* `calculatePowerHistogram` evaluates the samples,
• *Then* the corresponding histogram bin has `zoneIndex = 7` and `color = TTColor.Zone7` (Black `#000000`).
• *Given* an active workout with tail folding where the sprint tail reaches Zone 7,
• *When* `calculatePowerHistogram` builds the overflow bin,
• *Then* `overflowBin.zoneIndex` is 7 and `overflowBin.color` is `TTColor.Zone7`.
• *Given* `TelemetryMetricGraph` rendering power telemetry with `PowerZoneThresholds`,
• *When* `thresholdDashes` is remembered,
• *Then* it invokes `calculatePowerThresholdDashes` and produces dashed lines for all 6 thresholds (`z1Max`..`z6Max`) within the data range.
• *Given* the app rendered in Dark / AMOLED theme,
• *When* a Zone 7 bin is drawn in `TelemetryHistogramChart`, `ZoneDistributionColumnChart`, or `ZoneDistributionStackedBar`,
• *Then* the bar renders with an `outlineVariant` border ensuring clear visual distinction from the dark background.
• *Given* a selected Zone 7 bin in `TelemetryHistogramChart` in Dark theme,
• *When* the header readout renders,
• *Then* the text is displayed in `MaterialTheme.colorScheme.onSurface` rather than pure black.
• *Given* heart rate telemetry,
• *When* `calculateHeartRateHistogram` runs,
• *Then* all bins have `zoneIndex` in 1..5 and `color` in `Zone1`..`Zone5`, and `TelemetryMetricGraph` draws exactly 4 dashed lines.

**Invariants**: Heart rate 5-zone invariance, duration/percentage conservation, stateless calculation, WCAG contrast compliance, and 100% clean-room test pass rate.
- **Rationale**: The system SHALL support all 7 cycling power zones (Zone 1 Active Recovery through Zone 7 Neuromuscular Sprint) across Power Histogram calculations, TelemetryMetricGraph threshold dashed lines, and dark mode chart rendering, ensuring full Coggan 7-zone training model parity, visual contrast against dark card surfaces, and non-regression on heart rate histograms (ATT-4294, complementing `REQ-UI-419`, `REQ-UI-372`, and `REQ-UI-392`):
1. *7-Zone Power Histogram Binning in `TelemetryHistogramCalculator.kt` (`REQ-UI-420.1`)*:
• The system SHALL expand the static zone color palette `ZONE_COLORS` to include all 7 standard zone colors: `TTColor.Zone1` (Gray), `TTColor.Zone2` (Blue), `TTColor.Zone3` (Green), `TTColor.Zone4` (Yellow), `TTColor.Zone5` (Purple), `TTColor.Zone6` (Warm Earthy Brown `#8B5A2B`), and `TTColor.Zone7` (Pure Black `#000000`).
• In `calculateHistogram`, the zone index clamp SHALL be updated from `coerceIn(0, 4)` to `coerceIn(0, ZONE_COLORS.lastIndex)` (`0..6`), mapping bins with midpoint $> z5Max$ to Zone 6 (`zoneIndex = 6`, `color = TTColor.Zone6`) and midpoint $> z6Max$ to Zone 7 (`zoneIndex = 7`, `color = TTColor.Zone7`).
• In `calculatePowerHistogram`, the rightmost folded `overflowBin` (`REQ-UI-419`) SHALL dynamically reflect the highest zone present in `tailBins` (`tailBins.maxByOrNull { it.zoneIndex }?.zoneIndex ?: 7`, `color = peakTailBin?.color ?: TTColor.Zone7`) rather than hardcoding `zoneIndex = 5` and `TTColor.Zone5`.
• `calculateHeartRateHistogram` SHALL preserve strict 5-zone mapping (`getZoneForValue` returning `0..4`, `zoneIndex` 1..5, `TTColor.Zone1..Zone5`).
2. *6-Threshold Power Dashed Lines in `TelemetryMetricGraph.kt` (`REQ-UI-420.2`)*:
• In `TelemetryMetricGraph.kt`, the `thresholdDashes` remember block for `TelemetryMetricType.POWER` SHALL call `TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)`, rendering horizontal dashed boundary lines for all 6 power thresholds (`z1Max`, `z2Max`, `z3Max`, `z4Max`, `z5Max`, `z6Max`) that fall within `dataMin..dataMax`.
• `TelemetryMetricType.HEART_RATE` SHALL retain `TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)` with exactly 4 dashed lines.
3. *Dark Mode Contrast and Accessibility for `TTColor.Zone7` (`REQ-UI-420.3`)*:
• In `TelemetryHistogramChart.kt`, when `isSystemInDarkTheme()` is active (or in dark mode) and a bin has color `TTColor.Zone7` (Pure Black) or `TTColor.Zone6` (Brown), the canvas drawing routine SHALL draw an outline stroke (1.dp, `color = MaterialTheme.colorScheme.outlineVariant`) around the bar so pure black bars remain clearly discernible against dark card surfaces.
• In `TelemetryHistogramChart.kt`, the selected bin header readout label text SHALL NOT render pure black text on dark surfaces: if `isDark && (selectedBin.color == TTColor.Zone7

### REQ-UI-421: Electronic bicycle bell in Cockpit with button, zero-look double-tap gesture, and progressive loudness escalation [Verified]
- **Tests**: `TST-UI-384`, `TST-UI-390`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDbHelper.java`, `DatabaseSchemaRegistry.kt`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, `BikeBellButton.kt`, `BikeBellAudioSynthesizer.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide an integrated electronic bicycle bell in the Cockpit experience with button and gesture triggers, realistic acoustic brass synthesis, progressive volume escalation, per-tab persistence, audible media stream playback, static AudioTrack replay reliability, and availability across `READY`, `TRACKING`, and `PAUSED` modes (ATT-4281, amended by ATT-4371 and ATT-4431, extending `REQ-UI-344`, `REQ-UI-409`, `REQ-UI-295`, and enforcing Governance Rules 53 and 58):
1. *Per-Tab Cockpit Configuration & Cycling Default (`REQ-UI-421.1`)*:
• The database schema for `TrackingViewsDbHelper` (`FlexibleGenericView.db`) SHALL be upgraded to `DB_VERSION = 15`, adding column `SHOW_BIKE_BELL INTEGER NOT NULL DEFAULT 0` via an atomic, idempotent SQLite transaction in `onUpgrade` with defensive existence checking (`PRAGMA table_info(ViewsTable)`).
• The upgrade migration SHALL automatically set `SHOW_BIKE_BELL = 1` for existing cycling tabs (`ActivityType.BIKE_SPEED_AND_CADENCE`, `ActivityType.BIKE_POWER`) while leaving running and other tabs at `0`.
• `DatabaseSchemaRegistry.kt` SHALL be updated to map both `"FlexibleGenericView.db" to 15` and `"TrackingViews.db" to 15`.
• `TrackingViewInfo` DTO and `TrackingViewsRepository.kt` SHALL map `showBikeBell` symmetrically across single-row and batch queries (Rule 20) and provide `suspend fun updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean)` to persist configuration state.
• In `SensorGridScreen.kt` configuration mode (`ScreenMode.CONFIGURATION`), the system SHALL display a `SpatialCockpitToggleCard` for `config_tracking__showBikeBell` with title, discovery subtitle (`config_tracking__showBikeBell_subtitle`), and `Icons.Default.Notifications` icon.
2. *Floating Cockpit Bell Button (`REQ-UI-421.2`)*:
• In `TrackingTabsScreen.kt`, when `showBikeBell == true` on the active tab, the system SHALL render a floating `BikeBellButton` at the bottom overlay bar, styled as a Material 3 `ElevatedButton` (72.dp height) with distinct warm orange/amber container background (`TTColor.BikeBellContainer`) and high-contrast content/icon tint (`TTColor.BikeBellContent`), visually distinguishing it from `LapButton`'s primary blue (ATT-5283).
• Auxiliary Cockpit utility control `BikeBellButton` SHALL remain enabled across `READY`, `TRACKING`, and `PAUSED` modes (`isAvailable = trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)`) per Rule 53, and SHALL be disabled when `trackingMode == TrackingMode.IDLE`.
• Both `LapButton` and `BikeBellButton` SHALL be strictly suppressed when `screenMode == ScreenMode.CONFIGURATION` (preserving `REQ-UI-295`).
• When both `showLapButton` and `showBikeBell` are true, the system SHALL arrange `LapButton` and `BikeBellButton` side-by-side in a horizontal `Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically)`.
• When only one button is active, it SHALL render centered.
• `SnackbarHost` bottom padding SHALL clear the visible button bar (64.dp when either button is active, 8.dp when hidden).
• Single tap on `BikeBellButton` SHALL trigger the double-strike tone (*"Ding-Ding!"*), haptic feedback, and a one-shot bell shake rotation animation ($0^\circ \to -15^\circ \to +15^\circ \to -10^\circ \to +10^\circ \to 0^\circ$) directly within `ElevatedButton(onClick)` without nesting or attaching conflicting `Modifier.pointerInput(detectTapGestures)` per Rules 53 and 58.
• Press-and-hold / long-press on `BikeBellButton` SHALL be monitored contention-free using `ElevatedButton(interactionSource = interactionSource)` and `LaunchedEffect` collecting `PressInteraction.Press`, `Release`, and `Cancel` (Rules 53 and 58).
• When a press duration reaches `LocalViewConfiguration.current.longPressTimeoutMillis` (~400 ms), `BikeBellButton` SHALL invoke `onContinuousRingStart()`, dispatch long-press haptic feedback, and loop a continuous rhythmic bell shake animation for the entire duration of the hold.
• Upon finger release (`PressInteraction.Release`), gesture cancellation (`PressInteraction.Cancel`), mode transition away from available modes, or composable unmount (`DisposableEffect`), `BikeBellButton` SHALL invoke `onContinuousRingStop()`, cancel running coroutines, and cleanly reset rotation to $0^\circ$.
• When a press has been handled as a continuous roll (`wasLongPressHandled = true`), the subsequent release event in `ElevatedButton(onClick)` SHALL suppress invocation of `onSingleRing()`, preventing spurious single strikes on finger lift.
• Progressive volume escalation (70% polite initial volume, 100% urgent volume upon rapid repeat rings $\le 5\text{ seconds}$) SHALL be managed statefully in `TrackingTabsViewModel.triggerBellRing()`.
3. *Zero-Look Double-Tap Gesture on Sensor Grid (`REQ-UI-421.3`)*:
• In `SensorGridScreen.kt`, double-tapping anywhere on the sensor grid when `screenMode != ScreenMode.CONFIGURATION && state.showBikeBell` SHALL immediately trigger the *"Ding-Ding!"* bell strike and haptic feedback.
• The gesture recognizer SHALL evaluate during `PointerEventPass.Main` without consuming initial pointer down events on single tap, strictly preserving screen wakefulness in `BatterySaverController` (`PointerEventPass.Initial`), vertical sensor grid scrolling, and horizontal tab swiping in `HorizontalPager`.
4. *Acoustic Brass Synthesis, Concurrency Safety & Audio Focus (`REQ-UI-421.4`)*:
• Acoustic emission SHALL be generated via pure Kotlin in-memory 16-bit PCM mono digital synthesis at $48\text{ kHz}$ (`SAMPLE_RATE = 48000`) matching native Android AudioFlinger mixer rates in `BikeBellAudioSynthesizer.kt` using `AudioTrack` configured with `AudioTrack.MODE_STATIC` for zero-latency instant playback without disk I/O (ATT-5649).
• Waveform synthesis SHALL generate an authentic, warm brass bicycle bell timbre combining 5 resonant modes: sub-octave hum tone $f_0 \approx 1020\text{ Hz}$ ($\tau_0 \approx 0.55\text{ s}$, amplitude $\approx 0.32$), prime brass dome fundamental $f_1 \approx 2040\text{ Hz}$ ($\tau_1 \approx 0.38\text{ s}$, amplitude $\approx 0.46$), tierce mode $f_2 \approx 3120\text{ Hz}$ ($\tau_2 \approx 0.08\text{ s}$, amplitude $\approx 0.14$), nominal chime $f_3 \approx 4280\text{ Hz}$ ($\tau_3 \approx 0.03\text{ s}$, amplitude $\approx 0.06$), and clapper impact shimmer $f_4 \approx 6200\text{ Hz}$ ($\tau_4 \approx 0.015\text{ s}$, amplitude $\approx 0.02$) (ATT-5282).
• Strike sample calculation SHALL apply a smooth clapper attack envelope $(1.0 - \exp(-dt / 0.0015))$ ($\sim 1.5\text{ ms}$) eliminating instantaneous step-derivative snapping artifacts on phone micro-speakers.
• Roll synthesis (`synthesizeRollPcm`) SHALL apply linear headroom scaling ($0.85\times$) across rapid strike sums, preventing hard clipping distortion and odd-harmonic saturation.
• Double strike tone (*"Ding-Ding!"*) SHALL incorporate a $40\text{ ms}$ SmartPA amplifier wakeup lead-in (`LEAD_IN_SEC = 0.040`) before strike 1 at $t=0.040\text{ s}$ and strike 2 at $t=0.150\text{ s}$ with detuned frequencies ($+2.5\%$, $f_0' = 1045\text{ Hz}$, $f_1' = 2091\text{ Hz}$) over $0.95\text{ s}$ duration (`DOUBLE_STRIKE_DURATION_SEC = 0.95`) with a $60\text{ ms}$ raised cosine fade-out (`FADE_OUT_SEC = 0.060`) tapering smoothly to 0 without residual truncation or DC pops (ATT-5649).
• Audio attributes SHALL specify `AudioAttributes.USAGE_ALARM` and `AudioAttributes.CONTENT_TYPE_SONIFICATION` (with `android.permission.MODIFY_AUDIO_SETTINGS` declared in `AndroidManifest.xml`), guaranteeing that bell audio plays loudly and clearly even when device ringer is set to Silent or Vibrate mode while coexisting with turn-by-turn navigation spoken prompts and music playback.
• In `BikeBellAudioSynthesizer.kt`, output routing SHALL conditionally set `track.setPreferredDevice(builtInSpeaker)` only when an external audio output device (`TYPE_BLUETOOTH_A2DP`, `TYPE_BLUETOOTH_SCO`, `TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER`, `TYPE_BLE_BROADCAST`, `TYPE_WIRED_HEADSET`, `TYPE_WIRED_HEADPHONES`, `TYPE_USB_HEADSET`, `TYPE_USB_DEVICE`, `TYPE_HEARING_AID`) is connected. When no external devices are connected, `track.setPreferredDevice(null)` SHALL be preserved, allowing Android to route `USAGE_ALARM` naturally without port thrashing between Port 3 (`SPEAKER`) and Port 4 (`SPEAKER_SAFE`) (ATT-5649).
• Before playing `ring()` or `startRoll()`, `BikeBellAudioSynthesizer` SHALL capture `savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)` (if unboosted), boost `AudioManager.STREAM_ALARM` to `getStreamMaxVolume(AudioManager.STREAM_ALARM)` with `flags = 0` only when `currentVolume < maxVolume` to avoid hardware gain mute events, and schedule auto-restore (+5500 ms after `ring()` matching the 5 s escalation window, +150 ms after `stopRoll()`) (ATT-5649).
• In `BikeBellAudioSynthesizer.kt`, `ring()`, `startRoll()`, and `stopRoll()` SHALL invoke `track.pause()` (never `track.stop()`) prior to `track.playbackHeadPosition = 0` on static `AudioTrack`, preserving warm AudioFlinger mixer sessions and eliminating mixer renegotiation latency (ATT-5649).
• In `BikeBellAudioSynthesizer.kt`, `startRoll()` SHALL invoke `track.setLoopPoints(0, rollPcm.size / 2, -1)` so that continuous striker roll audio loops seamlessly and indefinitely while held, and `stopRoll()` SHALL cleanly pause playback and reset playback head position to 0.
• `BikeBellAudioSynthesizer` SHALL implement `AutoCloseable`, synchronize all native `AudioTrack` operations (`synchronized(lock)`), and be scoped to `TrackingTabsViewModel.onCleared()` for clean resource teardown without leaks or native crashes.
5. *Progressive Loudness Escalation (`REQ-UI-421.5`)*:
• In `TrackingTabsViewModel.triggerBellRing()`, volume SHALL escalate across 3 stages within a 5,000 ms repeat window:
  - 1st Tap (Polite Distance Ring, -9.1 dB): volume = `0.35f`
  - 2nd Tap within $\le 5\text{ s}$ (Firm Warning Ring, -3.7 dB): volume = `0.65f`
  - 3rd+ Tap within $\le 5\text{ s}$ or Long-Press Roll (Maximum Hardware Output, 0 dB): volume = `1.00f`
• The escalation stage and timestamp (`lastRingTimestampMs`) SHALL be retained in `TrackingTabsViewModel` across configuration changes and tab switches, resetting back to Stage 1 (`0.35f`) after $> 5\text{ seconds}$ of silence.
6. *100% 9-Language Localization Parity (`REQ-UI-421.6`)*:
• String resources `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` SHALL be defined across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero missing keys and literal `\n` linebreaks.
7. *Preservation of System Invariants (`REQ-UI-421.7`)*:
• Zero regression in Cockpit telemetry streaming, map rendering, live climbs, live segments, or elevation profiles.
• 100% clean-room test suite pass rate.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-421`, amended by `ATT-4371`, `ATT-4431`, `ATT-5131`, `ATT-5283`, `ATT-5282`, and `ATT-5649`, extending `REQ-UI-344` (default tracking views matrix), `REQ-UI-409` (4-tab cycling architecture), `REQ-UI-295` (action button configuration suppression), and enforcing Governance Rules 53 and 58 (`docs/design_guidelines.md` §5.12). Targets `TrackingViewsDbHelper.java`, `DatabaseSchemaRegistry.kt`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, `BikeBellButton.kt`, `BikeBellAudioSynthesizer.kt`, `Color.kt`, `AndroidManifest.xml`, and `strings.xml` (all 9 locales).
2. *Historical Origin & Commit Trace*: Sprint 2026-43.1 (`ATT-4281` commit `40af18141387`) introduced bike bell with `detectTapGestures` nested on `ElevatedButton`. Sprint 2026-43.2 (`ATT-4371` commit `bac64ff9`) removed `detectTapGestures` to eliminate single-tap click contention and introduced `USAGE_MEDIA`. Sprint 2026-43.2 Joint Review filed `ATT-4431` to restore press-and-hold continuous ring. Sprint 2026-45.2 (`ATT-5131`) amends `REQ-UI-421.4` and `REQ-UI-421.5` to force built-in speaker routing (`TYPE_BUILTIN_SPEAKER`), switch to `USAGE_ALARM` (`STREAM_ALARM`) with temporary 100% hardware volume boost and auto-restore, and replace binary volume with 3-stage logarithmic escalation (`0.35f -> 0.65f -> 1.00f`). Sprint 2026-45.3 (`ATT-5283`) amends `REQ-UI-421.2` to replace blue palette styling with distinct orange/amber tokens (`TTColor.BikeBellContainer`, `TTColor.BikeBellContent`) resolving visual confusion with `LapButton`. Sprint 2026-45.3 (`ATT-5282`) amends `REQ-UI-421.4` to replace harsh 4-mode synthesis with a warm 5-mode natural brass bell acoustic model, smooth attack window, and roll headroom scaling. Sprint 2026-45.9 (`ATT-5649`) amends `REQ-UI-421.4` to resolve clipped/muted bell chimes across Android devices via warm `pause()` replay, conditional speaker routing, 5.5s alarm restore with max-volume guard, native 48kHz sampling, 40ms SmartPA lead-in, and 0.95s duration with 60ms cosine fade-out.
3. *Root Reason for Existing Formulation*: `USAGE_MEDIA` was adopted in `ATT-4371` to bypass Silent/Vibrate mode ringer suppression, but `USAGE_MEDIA` defaults to routing audio into connected headphones instead of the phone speaker and is capped by user media volume. Binary toggle `0.70f -> 1.00f` produced only 3.1 dB change, which was imperceptible outdoors. `BikeBellButton` originally inherited `LapButton`'s primary blue theme tokens. 4-mode synthesis ($2.4\text{--}7.6\text{ kHz}$) lacked lower body resonance and sustained harsh overtones under 100% alarm volume boost. `track.stop()` was used in `ATT-4371` to avoid `ERROR_INVALID_OPERATION` on older Android versions, but modern AudioFlinger destroys the active port on `stop()`, causing 200–385 ms cold-start latency. Unconditional `setPreferredDevice` caused port-thrashing between `SPEAKER` and `SPEAKER_SAFE` on devices without headphones. `RING_RESTORE_DELAY_MS = 650L` truncated chimes and triggered hardware gain mute events during the 5 s escalation window.
4. *Preservation of Core Invariants*: `USAGE_ALARM` preserves Silent/Vibrate mode bypass while isolating `STREAM_ALARM` from `STREAM_MUSIC`, ensuring headphone music volume is untouched when boosting speaker volume; conditional `setPreferredDevice(TYPE_BUILTIN_SPEAKER)` guarantees external acoustic projection when headphones are connected while preventing port thrashing when absent; `flags = 0` prevents UI popups; `track.pause()` + `playbackHeadPosition = 0` reliably rewinds static tracks while maintaining active AudioFlinger mixer sessions; Rules 53 and 58 compliance (zero pointer contention on `ElevatedButton`, `isAvailable` multi-mode gating); theme token centralization in `Color.kt` (zero hex literals outside `Color.kt`); zero-latency `MODE_STATIC` in-memory replay; PCM buffer size invariants (91,200 bytes for 0.95s double strike at 48kHz, 240,000 bytes for 2.5s roll at 48kHz); 100% clean-room test suite pass rate strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a cycling tab (`ActivityType.BIKE_SPEED_AND_CADENCE` or `ActivityType.BIKE_POWER`),
• *When* the database is created or upgraded to v15,
• *Then* `SHOW_BIKE_BELL` is set to `1` by default.
• *Given* a running or generic tab,
• *When* the database is created or upgraded to v15,
• *Then* `SHOW_BIKE_BELL` is set to `0` by default.
• *Given* `SensorGridScreen` in `ScreenMode.CONFIGURATION`,
• *When* configuring tab features,
• *Then* a `SpatialCockpitToggleCard` for `Show Bike Bell` is displayed with title and subtitle.
• *Given* a Cockpit tab with `showBikeBell == true` in `TrackingMode.READY`, `TrackingMode.TRACKING`, or `TrackingMode.PAUSED`,
• *When* viewing the bottom overlay bar,
• *Then* `BikeBellButton` is displayed as enabled (side-by-side with `LapButton` if lap is enabled and tracking is active, or centered).
• *Given* a Cockpit screen in `ScreenMode.CONFIGURATION`,
• *When* observing the bottom overlay,
• *Then* both `LapButton` and `BikeBellButton` are hidden.
• *Given* a device in Silent or Vibrate mode,
• *When* `BikeBellAudioSynthesizer.ring()` or `startRoll()` is executed,
• *Then* audio playback routes to `USAGE_MEDIA` and sounds audibly at the user's media volume level.
• *Given* an `AudioTrack.MODE_STATIC` instance playing bell audio,
• *When* `ring()` is called repeatedly,
• *Then* `track.stop()` is invoked before resetting `track.playbackHeadPosition = 0`, resetting static pointers cleanly without `ERROR_INVALID_OPERATION`.
• *Given* a single tap on `BikeBellButton` in `READY`, `TRACKING`, or `PAUSED` mode after $> 5\text{ seconds}$ of silence,
• *When* triggered directly through `ElevatedButton(onClick)`,
• *Then* the *"Ding-Ding!"* double strike plays at polite volume (70%) with haptic click and shake animation without gesture contention.
• *Given* a second tap within 5 seconds of the first ring,
• *When* triggered,
• *Then* the *"Ding-Ding!"* bell escalates to 100% maximum volume.
• *Given* double-tap on `SensorGridScreen` when `screenMode != ScreenMode.CONFIGURATION && state.showBikeBell`,
• *When* triggered,
• *Then* the *"Ding-Ding!"* bell strike plays immediately across `TRACKING` and `PREVIEW` modes.
• *Given* horizontal swipe gestures across `SensorGridScreen`,
• *When* swiping between tabs,
• *Then* `HorizontalPager` smoothly transitions tabs without gesture blocking.
• *Given* an athlete pressing and holding `BikeBellButton` in `READY`, `TRACKING`, or `PAUSED` mode,
• *When* the press duration reaches $\ge 400\text{ ms}$,
• *Then* `onContinuousRingStart()` is triggered, continuous shake animation loops, and `BikeBellAudioSynthesizer.startRoll()` loops the *"Brriiiing!"* roll indefinitely.
• *Given* an active continuous roll on `BikeBellButton`,
• *When* the athlete releases their finger or drags off the button,
• *Then* `onContinuousRingStop()` is triggered, audio roll stops, rotation resets to $0^\circ$, and `onSingleRing()` is suppressed.
• *Given* `BikeBellButton.kt`,
• *When* inspecting modifiers on `ElevatedButton`,
• *Then* zero instances of `pointerInput` or `detectTapGestures` exist, conforming strictly to Rules 53 and 58.

**Invariants**: Zero regression in Cockpit telemetry/views, transactional SQLite migration, zero-latency PCM audio without GC allocations, thread-safe synthesizer lifecycle teardown, non-exclusive audio focus, and 100% clean-room test pass rate.

### REQ-UI-422: Relocate 'Sport Types' drawer item to Settings and rename 'Equipment' item to 'Bikes & Shoes' [Verified]
- **Tests**: `TST-UI-385`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppNavigationDrawer.kt`, `NavigationDrawerStateTest.kt`, `EquipmentTabsScreen.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL relocate the 'Sport Types' (`R.id.drawer_sport_types`) navigation destination from the 'My Stuff' / Equipment drawer group (`R.string.drawer__my_stuff`) into the 'Settings' drawer group (`R.string.drawer__settings`) positioned immediately after 'Tracking Layouts' (`R.id.drawer_tracking_layouts`), and SHALL rename `R.string.equipment_management_title` from "Equipment" to "Bikes & Shoes" ("Räder & Schuhe") across all 9 canonical application locales, eliminating category label duplication and aligning athletic taxonomy configuration within Settings (ATT-4289, amending `REQ-UI-405.2` and `REQ-UI-405.3`):
1. *Hardware-Only Equipment Drawer Hub (`REQ-UI-422.1`)*:
• In `AppNavigationDrawer.kt`, `createDrawerGroups(startTrackingTitleRes: Int)` SHALL configure `R.string.drawer__my_stuff` (`collapsible = true`) to contain strictly two destination items in exact order: `R.id.drawer_my_sensors` (`R.drawable.ic_my_paired_devices`, `R.string.devices_myRemoteDevices`) and `R.id.drawer_equipment` (`R.drawable.ic_equipment_combined`, `R.string.equipment_management_title`).
• `R.id.drawer_sport_types` SHALL be excised completely from `R.string.drawer__my_stuff`.
2. *Sport Types Placement in Settings Hub (`REQ-UI-422.2`)*:
• In `AppNavigationDrawer.kt`, `createDrawerGroups(startTrackingTitleRes: Int)` SHALL position `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` inside the `R.string.drawer__settings` group (`collapsible = true`), placed immediately after `R.id.drawer_tracking_layouts`.
• The resulting `R.string.drawer__settings` group SHALL contain strictly seven destination items in logical sequence: `drawer_units`, `drawer_display_settings`, `drawer_tracking_layouts`, `drawer_sport_types`, `drawer_backup_restore`, `drawer_privacy_policy`, and `drawer_advanced_tuning`.
3. *Rename Equipment Title to "Bikes & Shoes" Across All 9 Locales (`REQ-UI-422.3`)*:
• The string resource `R.string.equipment_management_title` SHALL be updated across all 9 canonical application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) to denote "Bikes & Shoes" ("Räder & Schuhe") using XML entity escaping (`&amp;`) where applicable (`values`, `values-de`, `values-nl`).
• The screen title in `EquipmentTabsScreen.kt` (`stringResource(R.string.equipment_management_title)`) SHALL reflect this updated string, ensuring 1:1 parity between the navigation drawer destination row and the screen header.
4. *Drawer State Machine & Auto-Expansion Invariant (`REQ-UI-422.4`)*:
• Evaluating `isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_sport_types)` SHALL return `true`.
• Evaluating `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_sport_types)` SHALL return `false`.
• Selecting any item in `myStuffGroup` (`drawer_my_sensors`, `drawer_equipment`) or its deep-link aliases (`drawer_bikes`, `drawer_shoes`) SHALL continue to return `true` for `isDrawerGroupInitiallyExpanded(myStuffGroup, ...)`.
5. *Preservation of System Invariants (`REQ-UI-422.5`)*:
• Total drawer category hubs SHALL remain strictly 5.
• Total unique navigation destination IDs across all 5 groups SHALL remain strictly 22 ($4 + 5 + 2 + 4 + 7 = 22$).
• Default visible destinations density across non-collapsible groups (`Training` + `Maps`) SHALL remain strictly 9.
• Navigation route identifiers in `NavRoutes.kt` and alias matching in `isDrawerItemSelected` SHALL remain 100% backward compatible.
• Clean-room unit test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-422`, amending `REQ-UI-405.2` and `REQ-UI-405.3` (`ATT-3928`), which previously amended `REQ-UI-397.1` (`ATT-3851`). Targets `AppNavigationDrawer.kt`, `NavigationDrawerStateTest.kt`, `EquipmentTabsScreen.kt`, and `strings.xml` (all 9 locales).
2. *Historical Origin & Commit Trace*: `ATT-3851` (commit `04b6b158`, Sprint 2026-42.7) established 5 drawer category hubs with `drawer_sport_types` and `drawer_equipment` in `My Stuff`; `ATT-3928` (commit `4623bb6f`, Sprint 2026-42.8) moved `drawer_athlete_profile` to `Training` and configured `My Stuff` as collapsible.
3. *Root Reason for Existing Formulation*: `drawer_sport_types` was historically left in `My Stuff` before athletic taxonomy configuration was distinguished from physical gear (sensors, bikes, shoes). `equipment_management_title` was titled "Equipment" when Bikes and Shoes were first consolidated, producing duplicate "Ausrüstung" header and child rows.
4. *Preservation of Core Invariants*: 5 category hubs, 22 total destinations, 9 default visible rows, `isDrawerItemSelected` deep-link aliases, 9-language localization parity, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the application navigation drawer rendered with `createDrawerGroups(R.string.tab_start)`,
• *When* inspecting `drawer__my_stuff`,
• *Then* it contains strictly 2 items (`drawer_my_sensors`, `drawer_equipment`) and `drawer_sport_types` is absent.
• *Given* the application navigation drawer rendered with `createDrawerGroups(R.string.tab_start)`,
• *When* inspecting `drawer__settings`,
• *Then* it contains strictly 7 items with `drawer_sport_types` positioned at index 3, directly between `drawer_tracking_layouts` and `drawer_backup_restore`.
• *Given* `isDrawerGroupInitiallyExpanded(group, R.id.drawer_sport_types)`,
• *When* evaluated against `settingsGroup`,
• *Then* it returns `true`; and when evaluated against `myStuffGroup`, it returns `false`.
• *Given* `R.string.equipment_management_title` across all 9 locales,
• *When* rendered in the drawer or `EquipmentTabsScreen`,
• *Then* it resolves to "Bikes & Shoes" (or localized equivalent, e.g. "Räder & Schuhe" in German) without duplicate "Ausrüstung" labels.

**Invariants**: 5 category hubs, 22 unique destinations, 9 default visible rows, `isDrawerItemSelected` deep links, 9-locale parity, and 100% clean-room test pass rate.

### REQ-UI-423: Modal Overlay Composition Retention for Route Tabbed Screen During Route Editing [Verified]
- **Tests**: `TST-UI-387`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`
- **Rationale**: The system SHALL retain `RouteTabbedScreen` continuously mounted in the Jetpack Compose composition hierarchy while `EditRouteScreen` (an `AppModalBottomSheet`) is displayed, composing `EditRouteScreen` as an overlay within the non-details branch rather than in a mutually exclusive conditional block, preserving `PrimaryScrollableTabRow` scroll offset, list scroll states, and collapsing header offsets without tab bar jump animations upon sheet dismissal (ATT-4285, enforcing Governance Rule 47 and Design Guidelines Section 5.8):
1. *Continuous Composition Retention of `RouteTabbedScreen` (`REQ-UI-423.1`)*:
• In `RoutesScreen.kt`, when `selectedRouteIdForDetails == null`, `RouteTabbedScreen` SHALL remain unconditionally mounted in the composition tree regardless of whether `selectedRouteIdForEdit` is `null` or populated with an active route ID.
2. *Modal Overlay Composition for `EditRouteScreen` (`REQ-UI-423.2`)*:
• In `RoutesScreen.kt`, `EditRouteScreen` SHALL be rendered conditionally alongside `RouteTabbedScreen` inside the non-details branch whenever `selectedRouteIdForEdit != null` and a matching route is found in `routes`, presenting the route edit bottom sheet as a true modal overlay above the dimmed routes list.
3. *Tab Bar Scroll Offset & Header State Preservation (`REQ-UI-423.3`)*:
• Dismissing `EditRouteScreen` (via save, cancel, back navigation, or scrim click) SHALL clear `selectedRouteIdForEdit = null` without triggering unmounting, remounting, or scroll reset of `RouteTabbedScreen`. Specifically, the internal horizontal `ScrollState` of `PrimaryScrollableTabRow` SHALL remain intact at its current scroll position, preventing jump animations from tab 0 to the active tab (e.g. tab 3 "Other" / "Sonstige"), and `CollapsingAppBarNestedScrollConnection` SHALL preserve its accumulated `appBarOffset`.
4. *Preservation of System & Navigation Invariants (`REQ-UI-423.4`)*:
• Full-screen route detail inspection (`RouteOnMapScreen` when `selectedRouteIdForDetails != null`) SHALL remain completely unaffected.
• Route metadata updates via `viewModel.updateRoute(it)` and cancellation logic in `EditRouteScreen` SHALL remain 100% functional.
• Back button handling (`BackHandler`) SHALL dismiss `EditRouteScreen` by setting `selectedRouteIdForEdit = null` when active.
• Clean-room unit test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-423`, refining `REQ-UI-191` (*Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance*) and `REQ-UI-159` (*Single-Activity Architecture & Jetpack Compose Navigation Modernization*), and enforcing Governance Rule 47 / Design Guidelines Section 5.8 (*Modal Overlay Composition Retention*). Targets `RoutesScreen.kt` and `RoutesScreenContractTest.kt`.
2. *Historical Origin & Commit Trace*: `ef96c234` (Sprint 2026-40.7, ATT-1083) migrated navigation to Compose with an initial `if / else if / else` branching model; `fea001b7` (Sprint 2026-41.2, ATT-1541 / `REQ-UI-191`) added dedicated edit button setting `selectedRouteIdForEdit = id`. Sprint Review 2026-42.10 (ATT-4049) discovered tab bar jump when editing from tab index 3.
3. *Root Reason for Existing Formulation*: When `RoutesScreen.kt` was first created, screen-level destination states were naively modeled as mutually exclusive `if / else if / else` branches without distinguishing full-screen destinations (`RouteOnMapScreen`) from modal bottom sheet overlays (`EditRouteScreen` wrapping `AppModalBottomSheet`), unmounting `RouteTabbedScreen` while editing.
4. *Preservation of Core Invariants*: Full-screen map inspection, route update persistence, back navigation, tab state stability, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete is on any non-zero tab (e.g. Tab 3 "Other" / "Sonstige") in `RouteTabbedScreen` with a scrolled list and partially collapsed header,
• *When* the athlete taps the edit action on a route card setting `selectedRouteIdForEdit`,
• *Then* `RouteTabbedScreen` remains continuously mounted in the composition tree, and `EditRouteScreen` is displayed as an overlay on top of the dimmed route list.
• *Given* `EditRouteScreen` is active as a modal overlay,
• *When* the athlete saves changes (`onSave`), cancels (`onCancel`), taps the scrim, or presses the back button (`BackHandler`),
• *Then* `EditRouteScreen` is dismissed (`selectedRouteIdForEdit = null`), `RouteTabbedScreen` remains mounted without remounting, and `PrimaryScrollableTabRow` retains its exact horizontal scroll offset without jumping or animating from Tab 0.
• *Given* `selectedRouteIdForDetails != null`,
• *When* a route is selected for map inspection,
• *Then* `RouteOnMapScreen` continues to display as a full-screen view.

**Invariants**: Zero state unmounting for `RouteTabbedScreen`, navigation parity for `RouteOnMapScreen`, route update parity, 9-language localization parity, and 100% clean-room test suite pass rate.

### REQ-UI-424: Show in Workout Summary Toggles for Physiology Models in Advanced Settings, Rule 59 Subordinate Toggle Hierarchy, and Coggan-Only Presentation Gating in WorkoutDetails [Verified]
- **Tests**: `TST-UI-388`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PhysiologyModelsSection.kt`, `TuningPreferencesDataStore.kt`, `WorkoutDetails.kt`, `PowerMetricSlot.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `TuningFormControls.kt`
- **Rationale**: The system SHALL provide subordinate "Show in Workout Summary" (*In Workout-Zusammenfassung anzeigen*) toggle switches in `Settings -> Advanced Settings -> Physiology & Training Models` (`PhysiologyModelsSection.kt`) using `SubordinateTuningToggleItem` with lighter visual hierarchy, downscaled `Switch` (`Modifier.scale(0.6f)`), compact layout height (`Modifier.height(24.dp)`), and lighter subordinate checked track color (`checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)` with `checkedThumbColor = MaterialTheme.colorScheme.primary`) per Rule 59 / `docs/design_guidelines.md` Section 3.3 for the three physiology models that contribute summary rows to `WorkoutDetails.kt` (Coggan Power Metrics, Aerobic Efficiency & Decoupling, Critical Power & Anaerobic Battery), persisting preferences via `TuningPreferencesDataStore`, SHALL NOT display an unrequested "Basic Power Metrics (Work, W/kg)" model toggle in settings, and SHALL gate the Power domain row in `WorkoutDetails.kt` to exclusively render Coggan Power Metrics (`NP` with `VI`, `IF`, `TSS`) when Coggan power and summary are enabled, completely omitting basic mechanical work (`kJ`) and `W/kg` from `WorkoutDetails` without altering live cockpit telemetry tiles, sensor recording, or background extrema persistence (ATT-4369, ATT-4432, ATT-4464, ATT-4513, refining `REQ-UI-403` and `REQ-UI-415`):
1. *Subordinate Summary Toggles in Physiology & Training Models (`PhysiologyModelsSection.kt`, `TuningFormControls.kt`, `REQ-UI-424.1`)*:
• In `Settings -> Advanced Settings -> Physiology & Training Models` (`PhysiologyModelsSection.kt`), exactly 4 training science models SHALL be presented:
- **Coggan Power Metrics**: `enableCogganPowerMetrics` (master, default `true`), with subordinate `showCogganPowerInWorkoutSummary` (default `true`).
- **Performance Management (PMC)**: `enablePmcModel` (master, default `false`), with no summary toggle (contributes no summary rows).
- **Aerobic Efficiency & Decoupling**: `enableAerobicEfficiencyModel` (master, default `false`), with subordinate `showAerobicEfficiencyInWorkoutSummary` (default `true`).
- **Critical Power & Anaerobic Battery**: `enableCriticalPowerModel` (master, default `false`), with subordinate `showCriticalPowerInWorkoutSummary` (default `true`).
• The unrequested "Basic Power Metrics (Work, W/kg)" model toggle and its subordinate switch SHALL be completely removed.
• Subordinate toggles SHALL be rendered using `SubordinateTuningToggleItem` (`TuningFormControls.kt`) conforming to Rule 59: `MaterialTheme.typography.bodySmall` / `labelMedium` font in `MaterialTheme.colorScheme.onSurfaceVariant`, compact vertical padding (`padding(start = 16.dp, top = 2.dp, bottom = 2.dp)`), single concise line without a heavy subtitle, downscaled `Switch` (`Modifier.scale(0.6f)`) with compact vertical layout height (`Modifier.height(24.dp)`), and styled with `SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))`, displayed conditionally when the parent model toggle is `true`.
2. *Jetpack DataStore Persistence & Configuration Flow (`TuningPreferencesDataStore.kt`, `REQ-UI-424.2`)*:
• `TuningPreferencesDataStore` and `TuningConfig` SHALL persist and emit the 3 summary boolean preferences:
- `KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY` / `showCogganPowerInWorkoutSummary` (default `true`)
- `KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY` / `showAerobicEfficiencyInWorkoutSummary` (default `true`)
- `KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY` / `showCriticalPowerInWorkoutSummary` (default `true`)
• `KEY_ENABLE_BASIC_POWER_METRICS` and `KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY` SHALL be removed from `TuningPreferencesDataStore`, `TuningConfig`, `TuningPreferencesDefaults`, `ALL_KEYS`, `tuningConfigFlow`, `saveTuningConfig`, and `resetToDefaults()`.
3. *Power Metric Slot Resolution & Coggan-Only Presentation (`PowerMetricSlot.kt`, `REQ-UI-424.3`)*:
• `PowerMetricSlot` enum SHALL declare exclusively Coggan metrics: `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`. Enum entries `WORK_KJ` and `POWER_TO_WEIGHT` SHALL be removed.
• `resolvePowerMetricSlots` SHALL evaluate: `showCoggan = enableCogganPowerMetrics && showCogganPowerInWorkoutSummary && data.normalizedPowerWatts != null`.
• When `showCoggan == true`: return `NORMALIZED_POWER`, plus `INTENSITY_FACTOR` (if non-null), plus `TSS` (if non-null).
• When `showCoggan == false`: return `emptyList()`.
• Basic mechanical work (`kJ`) and `W/kg` SHALL NOT be returned as fallback slots and SHALL NOT be displayed in the Workout Summary.
4. *Granular Presentation Gating & Visual Cadence in `WorkoutDetails` (`WorkoutDetails.kt`, `REQ-UI-424.4`)*:
• `WorkoutDetails.kt` SHALL consume the summary preferences without accepting basic power parameters:
- Preceding `HorizontalDivider` and `PowerTrainingMetricsSection` SHALL be rendered if and only if `powerSlots.isNotEmpty()`.
- Preceding `HorizontalDivider` and `AerobicEfficiencyRow` SHALL be rendered if and only if `enableAerobicEfficiencyModel && showAerobicEfficiencyInWorkoutSummary && data.efficiencyFactor != null`.
- Preceding `HorizontalDivider` and `AnaerobicBatteryRow` SHALL be rendered if and only if `enableCriticalPowerModel && showCriticalPowerInWorkoutSummary && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
• When an athlete disables summary display for all power and physiology models, `WorkoutDetails` SHALL render only the top Time & Distance row (and Altitude if elevation is present), eliminating unnecessary card expansion while preserving 12dp divider cadence and zero duplicate/trailing dividers (`REQ-UI-415.2`).
• Callers `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt` SHALL pass the corresponding preferences from `TuningConfig`.
5. *Preservation of System Invariants & Modularity Budgets (`REQ-UI-424.5`)*:
• Live cockpit tile gating (`SensorGridScreen.kt`, `EditSensorFieldViewModel.kt`, `filterVisibleFieldsForTuning`) and chart telemetry metrics SHALL continue to depend strictly on master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), completely unaffected by the summary toggles.
• Baseline physics metrics (`SensorType.WORK_KJ`, `SensorType.POWER_TO_WEIGHT`) in live cockpit tiles, `WorkoutExtrema`, `WorkoutDescription`, and `AthleteProfileScreen` remain fully available and untouched.
• Background recording in `TrackerService`, `CyclingPowerMetricsDevice`, and SQLite extrema/streams SHALL remain 100% active and uninhibited.
• All `.kt` files in `ui/settings/tuning/` (`AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `PhysiologyModelsSection.kt`, `TuningFormControls.kt`) SHALL strictly remain `< 400` lines.
• `WorkoutDetails.kt` SHALL strictly remain `< 500` lines.
6. *9-Language Localization Parity (`REQ-UI-424.6`)*:
• Dead string keys `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc` SHALL be cleanly removed across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with 100% parity.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-424`, `REQ-UI-403` (*Progressive Disclosure of Physiology & Training Models in Advanced Settings*), and `REQ-UI-415` (*Single-Line Power Row in WorkoutDetails*). Targets `PhysiologyModelsSection.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `WorkoutDetails.kt`, `PowerMetricSlot.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `TuningFormControls.kt`, and `res/values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.7 (`ATT-3929`, commit `a888c381`) introduced `PhysiologyModelsSection.kt` with 4 master toggles; Sprint 2026-42.8 (`ATT-4042`, commit `5f178dc9`) established single-line power row priority with basic physics fallback in `WorkoutDetails.kt`; Sprint 2026-43.2 (`ATT-4369`, commit `157684c0`) added summary toggles but erroneously introduced "Basic Power Metrics (Work, W/kg)" as a 5th model and reused heavy toggle items; Sprint 2026-43.2 Joint Review (`ATT-4432`, commit `e7b34e7e`) mandated removing the basic power model/fallback and applying Rule 59 visual hierarchy; Sprint 2026-43.3 Review on Pixel 10 (`ATT-4433` / `ATT-4464`, commit `0deaf91a`) downscaled to `Modifier.scale(0.6f)`, compact vertical height `24.dp`, and `secondary` color tokens; Sprint 2026-43.5 (`ATT-4513`) identified that in Light Mode, `secondary` resolves to `LightSecondary = #0000CD` (relative luminance ~0.07 vs `LightPrimary = #1464F4` luminance ~0.16), making the subordinate switch track appear darker than the parent switch, and replaced it with `primary.copy(alpha = 0.45f)` track and `primary` thumb.
3. *Root Reason for Existing Formulation*: In `ATT-4464`, `MaterialTheme.colorScheme.secondary` was selected under the assumption that secondary tokens are universally lighter/softer across themes, overlooking that `LightSecondary` is `#0000CD` (a dark navy blue) in Light Mode, which creates an overly dark and visually dominant track under primary blue toggles.
4. *Preservation of Core Invariants*: Control cardinality and type: `SubordinateTuningToggleItem` remains a `Switch`; master model toggle isolation: all 4 models and 3 summary toggles retain exact functional parity; modularity budgets: file line counts remain strictly `< 400` lines in tuning and `< 500` lines in `WorkoutDetails`; 100% clean-room test suite pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `Settings -> Advanced Settings -> Physiology & Training Models` is opened,
• *When* inspecting the models list,
• *Then* exactly 4 models are displayed (Coggan Power Metrics, Performance Management, Aerobic Efficiency & Decoupling, Critical Power & Anaerobic Battery), `Basic Power Metrics` is completely absent, and each model with a summary toggle renders a compact `SubordinateTuningToggleItem` with smaller text, a downscaled switch (`Modifier.scale(0.6f)`), compact height (`24.dp`), and lighter subordinate checked track color (`checkedThumbColor = primary, checkedTrackColor = primary.copy(alpha = 0.45f)`) when its parent master toggle is ON.
• *Given* an athlete with power data but unconfigured FTP (null `intensityFactor` and `tss`) and `enableCogganPowerMetrics = true` & `showCogganPowerInWorkoutSummary = true`,
• *When* `WorkoutDetails` is rendered,
• *Then* only `NP` (with `VI`) is rendered in the Power row, and `Work (kJ)` / `W/kg` are NOT rendered.
• *Given* `enableCogganPowerMetrics = false` or `showCogganPowerInWorkoutSummary = false` (or `normalizedPowerWatts = null`),
• *When* `WorkoutDetails` is rendered,
• *Then* the Power row and its preceding divider are completely omitted, with zero basic physics fallback.
• *Given* live cockpit editing or tracking screen,
• *When* selecting sensor fields,
• *Then* `WORK_KJ` and `POWER_TO_WEIGHT` remain fully available regardless of summary toggle states.

**Invariants**: Background sensor recording, cockpit tile selection, SQLite storage, Section 5.10 layout, modularity limits (< 400 lines in tuning, < 500 lines in WorkoutDetails), 9-locale parity, and 100% clean-room test pass rate.

### REQ-UI-425: Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) across Routes, Favorite Routes / Workout Clusters, and Workouts [Verified]
- **Tests**: `TST-UI-389`, `TST-UI-508`, `TST-UI-509`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• In `RouteFilterCriteria.matches(route: RouteWithPath)`: evaluate `if (minElevationGainMeters != null && route.summary.elevationGain < minElevationGainMeters) return false` and `if (maxElevationGainMeters != null && route.summary.elevationGain > maxElevationGainMeters) return false`.
• In `ClusterFilterCriteria.matches(cluster: WorkoutCluster, linkedEquipment: Set<String>, elevationGainMeters: Double?)`: if `minElevationGainMeters != null
- **Rationale**: The system SHALL support bounded elevation gain interval filtering (`minElevationGainMeters` / `maxElevationGainMeters`) with canonical preset interval chips (`< 100 m`, `100 - 250 m`, `250 - 500 m`, `500 - 1000 m`, `> 1000 m`) and two-way synchronized custom `Min (m)` / `Max (m)` integer input fields across Routes, Favorite Routes / Workout Clusters, and Workouts, harmonizing filter architecture and active filter chip strips across all three collection screens (ATT-4367, refining `REQ-UI-133`, `REQ-UI-135`, `REQ-UI-132`, and amending `REQ-UI-417`):
1. *Domain Model Extensions (`RouteFilterCriteria.kt`, `ClusterFilterCriteria.kt`, `WorkoutFilterCriteria.kt`)*:
• `RouteFilterCriteria` SHALL declare `val maxElevationGainMeters: Double? = null`.
• `ClusterFilterCriteria` SHALL declare `val minElevationGainMeters: Double? = null` and `val maxElevationGainMeters: Double? = null`.
• `WorkoutFilterCriteria` SHALL declare `val minElevationGainMeters: Double? = null` and `val maxElevationGainMeters: Double? = null`.
• In all three domain models, `activeFilterCount` SHALL treat elevation gain as a single active filter dimension if and only if `minElevationGainMeters != null

### REQ-UI-426: Realistic Default Cycling FTP (270 W) and Calculator-Aligned Training Zone Baselines [Verified]
- **Tests**: `TST-UI-391`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SettingsDataStore.kt`, `ZoneCalculatorDialog.kt`, `TrackingViewModel.kt`
- **Rationale**: The system SHALL align default Functional Threshold Power (FTP) to 270 W (~3.38 W/kg for 80 kg) and synchronize default training zone boundaries across `SettingsDataStore.kt`, `SettingsSnapshot.DEFAULT`, `ZoneCalculatorDialog.kt`, and `TrackingViewModel.kt` using canonical Coggan 7-zone and Karvonen heart rate calculation algorithms (ATT-4521, ATT-4543, Implemented in: ATT-4543, Status: Verified, amending `REQ-UI-370` and `REQ-UI-373`):
1. *Cycling Power Baseline & Coggan 7-Zone Default Cutoffs (`REQ-UI-426.1`)*:
• In `SettingsDataStore.kt`, `DEFAULT_CYCLING_FTP` SHALL be set to `270` (Watts).
• Default `PWR_BIKE` cutoffs in `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, and `resetToDefaults()` SHALL match canonical Coggan percentages of 270 W rounded half-up: Z1=149 W (55%), Z2=203 W (75%), Z3=243 W (90%), Z4=284 W (105%), Z5=324 W (120%), Z6=405 W (150%).
• In `ZoneCalculatorDialog.kt`, default input for FTP SHALL be `"270"` (`DEFAULT_CYCLING_FTP`).
2. *Heart Rate Baselines & Karvonen Default Cutoffs (`REQ-UI-426.2`)*:
• In `SettingsDataStore.kt`, default baselines `DEFAULT_HR_MAX = 185` (bpm) and `DEFAULT_HR_REST = 55` (bpm) SHALL be defined and used across DataStore fallbacks.
• Default `HR_RUN` cutoffs SHALL match Karvonen baselines (HRmax=185, HRrest=55, HRR=130): Z1=132 bpm (59%), Z2=145 bpm (69%), Z3=158 bpm (79%), Z4=171 bpm (89%).
• Default `HR_BIKE` cutoffs SHALL match cycling Karvonen baselines (~5 bpm lower): Z1=127 bpm, Z2=140 bpm, Z3=153 bpm, Z4=166 bpm.
• In `ZoneCalculatorDialog.kt`, default inputs for HRmax and HRrest SHALL be `"185"` and `"55"`.
3. *Runtime Fallback Alignment in Tracking HUD (`REQ-UI-426.3`)*:
• `TrackingViewModel.kt`s `zoneCutoffsMap` fallback cutoffs SHALL match canonical defaults (PWR_BIKE: 149, 203, 243, 284, 324, 405; HR_RUN: 132, 145, 158, 171; HR_BIKE: 127, 140, 153, 166).
4. *Preservation of System Invariants (`REQ-UI-426.4`)*:
• Existing customized user profiles (`isCustomized == true`) SHALL remain completely untouched.
• Historical workout analytics and recorded samples in SQLite SHALL remain immutable.
• Strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and Zone 7 finite ceiling ($2500\text{ W}$) SHALL remain guaranteed.
• Zero modifications to mathematical calculation formulas in `ZoneCalculationMath.kt`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-426`, amending `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*) and `REQ-UI-373` (*Automatic Training Zone Boundary Calculation from FTP and HR Baselines*). Targets `SettingsDataStore.kt`, `ZoneCalculatorDialog.kt`, `TrackingViewModel.kt`, `ZoneCalculatorDialogContractTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`, `BatterySaverModelPureTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3184` (`25828a66`) introduced 7-zone power with placeholder cutoffs (150..450 W). `ATT-3188` (`889fcebf`) added `ZoneCalculatorDialog` with local defaults (200 W, 190/50 bpm). `ATT-1289` (`559376e2`) added `DEFAULT_CYCLING_FTP = 200`. Ticket `ATT-4521` (`7051a307ddf7`) established 240 W recreational baseline; ticket `ATT-4543` elevates baseline to 270 W for competitive cyclists.
3. *Root Reason for Existing Formulation*: Placeholder thresholds were chosen before calculator integration; when the calculator dialog was built, defaults were hardcoded locally without refactoring existing DataStore defaults. In ATT-4521 240 W was chosen, which was calibrated up to 270 W in ATT-4543.
4. *Preservation of Core Invariants*: Customized user profiles retain stored values; strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling remain guaranteed; formulas in `ZoneCalculationMath` remain pure and unchanged.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an uncustomized athlete profile on a fresh install or after reset to defaults,
• *When* `SettingsDataStore` loads default zones,
• *Then* `pwrBikeZoneMax` resolves to [149, 203, 243, 284, 324, 405], `hrRunZoneMax` resolves to [132, 145, 158, 171], and `hrBikeZoneMax` resolves to [127, 140, 153, 166].
• *Given* `ZoneCalculatorDialog` opened without prior stored benchmarks,
• *When* inspected,
• *Then* FTP input defaults to "270", HRmax input defaults to "185", and HRrest input defaults to "55".
• *Given* an athlete with customized zones (`isCustomized == true`),
• *When* app starts or settings are loaded,
• *Then* custom cutoffs are strictly preserved without being overwritten by new defaults.

**Invariants**: Custom profiles preserved, SQLite immutability, integer monotonicity, and 100% clean-room test pass rate.

### REQ-UI-427: Aftermath Loader Concurrency, Process-Death Detail Restoration, Elevation Gesture State Integrity and Lifecycle Safety [Verified]
- **Tests**: `TST-UI-392`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackOnMapAftermathViewModel.kt`, `WorkoutClustersViewModel.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesTabbedScreen.kt`, `ElevationProfile.kt`, `CropWorkoutViewModel.kt`, `WorkoutRepository.kt`, `WorkoutSummariesViewModel.kt`
- **Rationale**: The system SHALL ensure race-free aftermath telemetry loading, process-death detail restoration, live gesture state synchronization, and coroutine lifecycle safety across aftermath screens and view models (ATT-4018, ATT-5478, Status: Verified, reinforcing `REQ-MAP-032`, `REQ-DAT-011`, and `REQ-UI-368`):
1. *Loader Coroutine Cancellation (`TrackOnMapAftermathViewModel.kt`, `WorkoutClustersViewModel.kt`, `PeriodsViewModel.kt`, `REQ-UI-427.1`)*:
• In `TrackOnMapAftermathViewModel`, `loadAftermathData(workoutData)` SHALL track its background coroutine in a `loadJob: Job?` handle and cancel any active previous job (`loadJob?.cancel()`) prior to launching a new fetch, preventing older asynchronous loads from overwriting newer selections with out-of-order UI state.
• In `WorkoutClustersViewModel` and `PeriodsViewModel`, `selectWorkoutForPeek(id)` SHALL track its fetch coroutine in a `peekJob: Job?` handle and cancel any active previous job (`peekJob?.cancel()`) prior to querying track points or markers, guaranteeing peak sheet state reflects strictly the latest user selection.
2. *Process-Death Detail Restoration & In-Place WorkoutData State Protection (`WorkoutSummariesTabbedScreen.kt`, `TrackOnMapAftermathViewModel.kt`, `REQ-UI-427.2`)*:
• In `TrackOnMapAftermathViewModel`, `AftermathMapUIState` SHALL explicitly track `loadedWorkoutId: Long?`.
• In `WorkoutSummariesTabbedScreen.kt`, `LaunchedEffect(selectedWorkoutIdForDetails)` SHALL observe `selectedWorkoutIdForDetails` (restored via `rememberSaveable` across configuration changes or process recreation) and invoke `trackOnMapViewModel.loadAftermathData(workoutData)` whenever `selectedWorkoutForDetails != null` and `uiState.loadedWorkoutId != selectedWorkoutIdForDetails`, preventing blank aftermath details upon process restore while preventing redundant reloads when in-place `WorkoutData` updates (lap splits, boundary moves, photo edits, or background `ExportWorker` broadcasts) are emitted for the same workout.
• In `TrackOnMapAftermathViewModel.loadAftermathData(workoutData)`, when `loadedWorkoutId == workoutData.id`, the ViewModel SHALL perform an in-place update (updating metadata/laps/export statuses) WITHOUT executing the Phase 1 state wipe (`tracks = emptyList()`, `telemetryPath = emptyList()`), preventing `hasGpsTrack`/`hasMap` from dropping to `false`, eliminating `visibleTabs` collapse, and preventing `HorizontalPager` tab selection clamping or jumping (`Rule 73`). When `loadedWorkoutId != workoutData.id`, Phase 1 state wipe is executed to cleanly prevent ghost data.
3. *Elevation Profile Gesture State Integrity (`ElevationProfile.kt`, `REQ-UI-427.3`)*:
• In `ElevationProfile.kt`, `pathPoints` captured within `pointerInput(totalSpan, isTimeDomain, isPanMode)` SHALL be referenced via `rememberUpdatedState(pathPoints)` (e.g. `val currentPathPoints by rememberUpdatedState(pathPoints)`), ensuring long-running gesture loops immediately access high-resolution telemetry points (including HR, cadence, and power) once loaded, rather than reading stale initial thinned fast-track points.
4. *Destructive Operation Lifecycle Protection & Double-Tap Guards (`CropWorkoutViewModel.kt`, `WorkoutRepository.kt`, `WorkoutSummariesViewModel.kt`, `REQ-UI-427.4`)*:
• In `CropWorkoutViewModel`, `_uiState.update { it.copy(isCropping = true) }` SHALL be invoked synchronously prior to dispatching `viewModelScope.launch`, eliminating the race window for double-tap duplicate crop executions.
• In `WorkoutRepository.cropWorkout`, coroutine `CancellationException` SHALL NOT be caught and converted to `Result.failure`, but SHALL be rethrown to preserve structured concurrency cancellation semantics.
• In `WorkoutSummariesViewModel.executeDeleteOldWorkouts` and `WorkoutRepository.deleteOldWorkouts`, post-deletion analytical cache resynchronization (`resyncAllPeriods`, `refreshClusters`, `enrichAllClusterMetadata`) SHALL be executed under `NonCancellable` (or repository-level application scope), ensuring that navigating away from `WorkoutSummariesTabbedScreen` mid-deletion does not abort downstream cache reconciliation or corrupt period/cluster state.
5. *ViewModel Scope Lifecycle Management (`WorkoutSummariesTabbedScreen.kt`, `REQ-UI-427.5`)*:
• In `WorkoutSummariesTabbedScreen`, `EditWorkoutViewModel` instances SHALL NOT accumulate indefinitely in the parent `ViewModelStore`. The ViewModel SHALL either use a bounded/cleared lifecycle owner or a scoped key that is cleared upon dialog dismissal (`selectedWorkoutIdForEdit = null`).
6. *Preservation of System Invariants (`REQ-UI-427.6`)*:
• Zero regressions in existing aftermath map and elevation profile visualizations.
• Zero schema alterations to SQLite databases.
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-427` (amended by `ATT-5478`) under Epic `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*), reinforcing `REQ-MAP-032` (*Scrubber Synchronization*), `REQ-DAT-011` (*Period Cache Resynchronization*), `REQ-UI-368` (*Workout Cropping*), and `REQ-UI-408.4` (*Auto-Hiding Empty Tabs*). Targets `TrackOnMapAftermathViewModel.kt`, `WorkoutClustersViewModel.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesTabbedScreen.kt`, `ElevationProfile.kt`, `CropWorkoutViewModel.kt`, `WorkoutRepository.kt`, and `WorkoutSummariesViewModel.kt`.
2. *Historical Origin & Commit Trace*: `REQ-MAP-032` introduced in Sprint 2026-41.1 (`ATT-2340`, `1faa8656`); `REQ-DAT-011` introduced in Sprint 2026-40.1 (`ATT-987`); `REQ-UI-368` introduced in Sprint 2026-42.4 (`ATT-3274`); `ATT-4018` (`145ee346`, process-death restoration check); `ATT-5478`.
3. *Root Reason for Existing Formulation*: `ATT-4018` introduced `alreadyLoaded` to restore detail state after process recreation. It assumed `tracks` would retain `workoutId`, unaware that Phase 4 used `type.ordinal.toLong()`, and did not anticipate repeated `WorkoutData` emissions from background export workers.
4. *Preservation of Core Invariants*: Process-death detail restoration (`REQ-UI-427.2`) is preserved; ghost data prevention when switching between different workouts is strictly preserved via Phase 1 wipe; in-place metadata/lap/export updates for the same workout preserve active map tracks and `HorizontalPager` tab selection without tab jumping (`Rule 73`); 100% test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* rapid consecutive calls to `TrackOnMapAftermathViewModel.loadAftermathData` for workout A then workout B,
• *When* workout A's background IO job finishes after workout B's fast-track,
• *Then* workout A's job has been cancelled and does NOT overwrite workout B's state in `_uiState`.
• *Given* `WorkoutSummariesTabbedScreen` with a selected workout detail sheet open,
• *When* the activity undergoes process destruction and recreation with `selectedWorkoutIdForDetails` restored,
• *Then* `LaunchedEffect` detects the restored selection and automatically invokes `loadAftermathData`, restoring the aftermath map tracks and profile.
• *Given* Workout Details open on the "Runden & Segmente" tab (or any non-map tab) for workout ID $W$,
• *When* `WorkoutData` emits an update for the same workout $W$ (e.g. from lap split/move or background export broadcast),
• *Then* `TrackOnMapAftermathViewModel.loadAftermathData` skips Phase 1 state wipe, `tracks` and `telemetryPath` remain populated, `visibleTabs` does not shrink, and the active tab selection on `HorizontalPager` is strictly preserved without jumping or flickering (Rule 73).
• *Given* an active drag/scrub gesture on `ElevationProfile`,
• *When* high-resolution telemetry points load asynchronously while the finger is down,
• *Then* subsequent scrub coordinates read the updated telemetry points via `rememberUpdatedState` rather than initial fast-track points.
• *Given* a user rapidly double-tapping the Crop button in `CropWorkoutScreen`,
• *When* the first tap executes `executeCrop`,
• *Then* `isCropping` is synchronously set to `true`, and the second tap is immediately dropped without launching a second job.
• *Given* a bulk deletion in progress via `WorkoutSummariesViewModel.executeDeleteOldWorkouts`,
• *When* the ViewModel's `viewModelScope` is cancelled before downstream resync finishes,
• *Then* cache resynchronization (`resyncAllPeriods`, `refreshClusters`) runs under `NonCancellable` to complete without leaving caches in an inconsistent state.

**Invariants**: Zero SQLite schema migrations; thread-safe background loading; structured concurrency cancellation safety; 100% clean-room test pass rate.

### REQ-UI-428: Atomic Single-Transaction DataStore Zone Persistence, SavedStateHandle Draft Preservation, Deduplicated Profile Loading, and Zone UI Decoupling [Verified]
- **Tests**: `TST-UI-393`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`, `AthleteProfileScreen.kt`, `AndroidManifest.xml`
- **Rationale**: The system SHALL serialize and persist athletic training zone profiles (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) within a single atomic DataStore transaction (`dataStore.edit {}` / `saveZoneProfile`), preserve uncommitted and temporarily non-monotonic user drafts across configuration changes and navigation tab switches via `SavedStateHandle`, deduplicate zone loading logic into a unified repository/helper function, decouple `ZoneProfileTabContent` from Activity dependencies without breaking architectural contract tests, eliminate the dead unstarted `ZonesSettingsActivity` entry from `AndroidManifest.xml`, and safeguard window inset styling against unsafe Activity casts (ATT-4021, amending `REQ-UI-172` and `REQ-UI-397.2`):
1. *Atomic Single-Transaction Zone Profile Persistence (`REQ-UI-428.1`)*:
• In `SettingsDataStore.kt` (or zone persistence layer), the system SHALL provide an atomic `suspend fun saveZoneProfile(profile: ZoneProfileState)` that writes all zone thresholds (`ZONE_1`..`ZONE_4` or `ZONE_1`..`ZONE_6`) and `ZoneDisplayOptions` inside a single `dataStore.edit {}` transaction.
• Rapid keystrokes and threshold updates SHALL be debounced or serialized via a stateful ViewModel dispatcher, preventing concurrent uncoordinated coroutines from interleaving or persisting stale threshold states.
2. *SavedStateHandle Draft Preservation Across Configuration Changes (`REQ-UI-428.2`)*:
• In `ZoneSettingsViewModel` (or equivalent retained state holder), active in-flight edits (including drafts that are temporarily non-monotonic during active typing) SHALL be preserved across screen rotation, configuration changes, and navigation tab switches via `SavedStateHandle`.
• Recomposition or screen rotation SHALL NOT clear uncommitted user input or reload stale values from DataStore over active drafts.
• Threshold values SHALL only be committed to DataStore once the draft profile satisfies `isProfileMonotonic`.
3. *Deduplicated Zone Profile Loading & Migration (`REQ-UI-428.3`)*:
• Duplicate profile loading blocks across `reloadProfiles`, `LaunchedEffect(Unit)`, and `loadProfile` SHALL be consolidated into a single reusable loader method (`loadZoneProfile(type: ZoneType): ZoneProfileState` or `loadAllZoneProfiles(): List<ZoneProfileState>`).
• Power zone migration (`checkAndMigratePowerZones()`) SHALL be executed idempotently prior to reading zone flows.
4. *Zone UI Decoupling, Manifest Cleanup & Safe Window Insets (`REQ-UI-428.4`)*:
• `ZoneProfileTabContent` SHALL be hosted cleanly without requiring an Activity context or an inverted dependency from `AthleteProfileScreen.kt` to an unreachable activity.
• The dead, unexported, unstarted `<activity android:name=".trainingtracker.activities.ZonesSettingsActivity">` declaration SHALL be removed from `AndroidManifest.xml`.
• Architectural contract tests (`ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`, `AthleteProfileContractAndLocalizationTest`) asserting file presence, composable markers, and signatures SHALL remain 100% satisfied.
• The unsafe cast `(context as android.app.Activity).window` in `SideEffect` SHALL be replaced with safe context unwrapping or `LocalView` insets controller access, eliminating `ClassCastException` risks in previews or non-Activity contexts.
5. *Preservation of System Invariants (`REQ-UI-428.5`)*:
• Backward compatibility: DataStore preference keys (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) and default threshold calculations MUST remain unaltered.
• Monotonicity invariant: Non-monotonic drafts MUST NEVER be written to DataStore.
• Zero regressions in `ZoneCalculatorDialog`, live theme-aware preview, or cockpit zone rendering.
• 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-172` (*Configurable Zone Visualization Options and Theme-Aware Live Preview*, Sprint 2026-40.5, ATT-1265) and `REQ-UI-397.2` (*Unified Tabbed "Profile & Zones" Screen*, Sprint 2026-42.7, ATT-3851). Targets `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`, `ZoneSettingsViewModel.kt`, `AthleteProfileScreen.kt`, `AndroidManifest.xml`, and architectural contract tests.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1265` / `ATT-1390`, commit `e3966ebf`) introduced `ZoneSettingsScreen`; Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) added 7 power zones; Sprint 2026-42.7 (`ATT-3851`, commit `04b6b158`) colocated `ZoneProfileTabContent` in `ZonesSettingsActivity.kt` to share tabs with `AthleteProfileScreen`.
3. *Root Reason for Existing Formulation*: When `ZoneProfileTabContent` was colocated during drawer consolidation, inline callbacks launching unthrottled IO coroutines were used to avoid introducing an additional ViewModel file late in the sprint. DataStore was edited key-by-key across 5-7 separate operations, and drafts were held in ephemeral Compose `remember` state.
4. *Preservation of Core Invariants*: Monotonicity enforcement, DataStore schema and keys, live theme-aware preview tiles, `ZoneCalculatorDialog`, and all architectural contract tests (`ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`, `AthleteProfileContractAndLocalizationTest`) remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete rapidly typing new zone threshold values in `AthleteProfileScreen` or `ZoneSettingsScreen`,
• *When* editing is in progress,
• *Then* edits are debounced and serialized, persisting via a single atomic `dataStore.edit {}` transaction upon becoming monotonic without racing coroutines or dropped values.
• *Given* an athlete entering a temporarily non-monotonic draft (e.g. typing "18" before "185"),
• *When* the screen is rotated (configuration change) or tab switched before saving,
• *Then* the draft is preserved in `SavedStateHandle` / ViewModel and restored seamlessly without being wiped.
• *Given* zone profile loading across initial load or tab switch,
• *When* profiles are queried from DataStore,
• *Then* loading logic executes via a single consolidated repository/loader method without 3x copy-pasted blocks.
• *Given* `AndroidManifest.xml`,
• *When* inspected,
• *Then* the dead `<activity android:name=".ZonesSettingsActivity">` declaration is removed, while `ZonesSettingsActivity.kt` and contract test assertions remain fully satisfied.
• *Given* Compose preview or non-Activity context hosting zone content,
• *When* the screen renders,
• *Then* window insets styling executes safely without throwing `ClassCastException`.

**Invariants**: Monotonicity enforcement, 100% DataStore schema compatibility, architectural contract test pass rate, and clean-room full test suite pass rate.

### REQ-UI-429: Global User Interface Localization Parity, Unit Adaptability, and Accessibility Semantics [Verified]
- **Tests**: `TST-UI-394`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `CropWorkoutScreen.kt`, `WorkoutFilterBottomSheet.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: The system SHALL extract hardcoded user-visible text literals, German labels, English `contentDescription` attributes, and marker labels to Android string resources with 100% translation parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT), SHALL adapt unit formatting and filter criteria dynamically based on athlete unit preference (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`), and SHALL establish accessible TalkBack semantics across interactive Compose controls and canvas charts (ATT-4026, Parent Epic ATT-355, status: Verified, extending `REQ-LOC-001`, `REQ-UI-122`, and `REQ-UI-157`):
1. *Resource Extraction & 9-Locale Parity (`REQ-UI-429.1`)*:
• User-visible literals in `ImportBackupTabsScreen.kt`, `MainActivityWithNavigation.kt`, `ZoneCalculatorDialog.kt`, `LapEditBottomSheet.kt`, `PeriodSummaryCard.kt`, and map marker overlays SHALL be extracted to string resources in `res/values/strings.xml` and fully translated into all 8 localized variants (`values-{de,es,fr,it,ja,nl,pl,pt}/strings.xml`).
• All string format specifiers SHALL adhere to positional arguments (`%1$s`, `%1$d`) per `REQ-UI-122`.
2. *Compose Screen Unit Adaptability (`REQ-UI-429.2`)*:
• In `CropWorkoutScreen.kt`, distance and speed labels/values SHALL format according to user unit configuration (`MyUnits.IMPERIAL` vs `METRIC`) using standard formatters (`DistanceFormatter`, `SpeedFormatter`), eliminating hardcoded German strings and metric units.
• In `WorkoutFilterBottomSheet.kt`, distance and elevation filter chips, input fields, and parse calculations SHALL adapt dynamically to imperial units (miles and feet) when `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.
3. *Accessibility & TalkBack Semantics (`REQ-UI-429.3`)*:
• Interactive `IconButton` elements lacking TalkBack descriptions (`contentDescription = null`) in `ClusterTuningScreen.kt`, `ManualClusterScreen.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `LapEditBottomSheet.kt`, and `ImportBackupTabsScreen.kt` SHALL provide localized action descriptions.
• Hardcoded English `contentDescription` strings across Compose components SHALL be extracted to localized string resources.
• Canvas charts (e.g. `LapSplitVisualizer.kt`, `ElevationProfile.kt`) SHALL expose accessible TalkBack content descriptions.
4. *Preservation of System Invariants (`REQ-UI-429.4`)*:
• 100% translation parity verified by `TranslationParityTest`.
• Stored metrics in SQLite remain invariant (SI units: meters, seconds, mps).
• Zero regressions across existing unit test suites.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-429` under Epic `ATT-355` (*Good and consistent UI*), extending `REQ-LOC-001` (*Translation Parity*), `REQ-UI-122` (*Positional Placeholder Parity*), and `REQ-UI-157` (*Workout Filter Criteria*). Targets UI presentation layers in Compose screens and Activities.
2. *Historical Origin & Commit Trace*: Discovered during whole-app architecture review (branch `feature/ATT-3921` @ `0234bfc0`). Review flagged UI literal debt accumulated during high-speed Compose migrations.
3. *Root Reason for Existing Formulation*: String literals and metric unit assumptions were introduced as temporary placeholders during rapid UI iteration.
4. *Preservation of Core Invariants*: Database schema, underlying SI units in persistence, and existing calculation formulas remain strictly intact; unit transformations occur exclusively at the presentation boundary.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete configured with French locale (`Locale.FRENCH`) and Imperial units (`MyUnits.IMPERIAL`),
• *When* navigating to `CropWorkoutScreen` or `WorkoutFilterBottomSheet`,
• *Then* all labels display in French and metrics/filter thresholds reflect miles and feet.
• *Given* an athlete using TalkBack screen reader,
• *When* navigating interactive icon buttons or charts,
• *Then* accessibility content descriptions are announced in the active locale without English fallbacks or unannounced controls.
• *Given* the 9 localized `strings.xml` resource files,
• *When* `TranslationParityTest` executes,
• *Then* all new keys exist across all 9 locales with 100% parity and matching argument specifiers.

**Invariants**: 100% translation parity, SI unit persistence in SQLite, 100% clean-room test pass rate.

### REQ-UI-430: Multi-Provider Cloud Settings UI Harmonization, Shared Composable Layout & Symmetric Granular Sync Configuration [Verified]
- **Tests**: `TST-UI-395`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DropboxSettingsDialog.kt`, `GoogleDriveSettingsDialog.kt`, `CloudConnectionHeader.kt`, `CloudProviderSettingsContent.kt`, `DropboxConnectionHeader.kt`, `GoogleDriveConnectionHeader.kt`, `TrainingApplication.java`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL harmonize cloud synchronization and automated backup settings interfaces across cloud storage providers (`DropboxSettingsDialog.kt` and `GoogleDriveSettingsDialog.kt`), extract shared parameterized layout and connection header composables in `ui.settings.cloud` (`CloudConnectionHeader.kt` and `CloudProviderSettingsContent.kt`), establish UX and functional symmetry across disconnected states (suppressing switches behind informative provider-specific connect prompts) and connected states (displaying account email, granular switches for workout export and database backup, backup interval dropdown when database backup is enabled, Wi-Fi only restriction, and last sync and last backup timestamps), commit staged preferences and reschedule WorkManager periodic backups on save, and maintain 100% translation parity across all 9 supported application locales (ATT-4666, Parent Epic ATT-355, status: Verified, refining `REQ-UI-152` and `REQ-DAT-020.6`, and aligning with `REQ-DAT-032.7`, Rule 36, and Rule 62):
1. *Shared Parameterized Layout & Header Extraction (`REQ-UI-430.1`)*:
• In `com.atrainingtracker.trainingtracker.ui.settings.cloud`, the system SHALL extract `CloudConnectionHeader.kt` and `CloudProviderSettingsContent.kt` per DRY (Rule 36) and Multi-Provider Symmetry (Rule 62).
• `CloudConnectionHeader` SHALL render the connection status text (`colorScheme.error` when disconnected, `TTColor.ConnectionConnected` when connected), optional account email, brand-colored Connect button with brand icon, and `errorContainer` Disconnect button.
• `DropboxConnectionHeader.kt` and `GoogleDriveConnectionHeader.kt` SHALL delegate to `CloudConnectionHeader`, preserving their public signatures, parameter contracts, and `ComposeSafePainterContractTest.kt` invariants.
• `CloudProviderSettingsContent` SHALL compose `CloudConnectionHeader`, loading indicator, error banner, disconnected connect prompt, and connected synchronization switches and status footers.
2. *Disconnected State UX Symmetry (`REQ-UI-430.2`)*:
• When disconnected (`isConnected == false`), both `DropboxSettingsDialog` and `GoogleDriveSettingsDialog` SHALL display the connection header followed by an informative localized connect prompt (`@string/dropbox_connect_prompt` and `@string/google_drive_connect_prompt`), suppressing all synchronization switches and dropdown selectors until authenticated.
3. *Connected Synchronization Granularity Symmetry (`REQ-UI-430.3`)*:
• When connected (`isConnected == true`), both dialogs SHALL render identical configuration controls:
- Header with connected status, account email (`TrainingApplication.getDropboxAccountEmail()` / `TrainingApplication.getGoogleDriveAccountEmail()`), and Disconnect button.
- Upload Workouts Toggle: Dedicated switch (`TrainingApplication.uploadWorkoutsToDropbox()` / `TrainingApplication.uploadWorkoutsToGoogleDrive()`) with localized summary.
- Upload Backups Toggle: Dedicated switch (`TrainingApplication.uploadBackupToDropbox()` / `TrainingApplication.uploadBackupToGoogleDrive()`) with localized summary.
- Backup Interval Selector: When Upload Backups is enabled, a `DropdownSelector` for `backup_interval_days` (options: Daily, Every 3 days, Weekly, Monthly from `@array/backup_interval_entries`, defaulting to `"1"`) SHALL be rendered, enabling athletes to configure periodic backup frequency for either cloud provider.
- Wi-Fi Only Toggle: Dedicated switch (`TrainingApplication.uploadToDropboxOnlyOnWifi()` / `TrainingApplication.uploadToGoogleDriveOnlyOnWifi()`) with localized summary.
- Status Footers: Both dialogs SHALL render Last Sync / Upload timestamp and Last Database Backup timestamp at the bottom of the dialog, rendering localized fallbacks when not yet synchronized or never backed up.
4. *Transactional Save & WorkManager Rescheduling (`REQ-UI-430.4`)*:
• Tapping "Abbrechen" (`@string/Cancel`) or dismissing the sheet SHALL discard in-flight edits without mutating preferences.
• Tapping "Speichern" (`@string/save`) SHALL atomically commit provider-specific preferences and `backup_interval_days` to `SharedPreferences` / `TrainingApplication`, reschedule periodic backups via `BackupWorker.schedule(context)`, and dismiss the sheet.
5. *9-Language Localization Parity (`REQ-UI-430.5`)*:
• All new user-facing strings (`dropbox_connect_prompt`, `upload_workouts_to_dropbox_summary`, `upload_backup_to_dropbox_summary`, `last_backup_never`) SHALL be defined and localized across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 100% parity verified by `TranslationParityTest`.
6. *Preservation of System Invariants (`REQ-UI-430.6`)*:
• OAuth PKCE scheme (`db-iknmdmr31sf64r0`), Google Sign-In `drive.file` scope, credential storage security in `auth_credentials_prefs.xml`, `safePainterResource` contracts in `ComposeSafePainterContractTest.kt`, single-authority credential capture, and 100% clean-room test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-430` under Epic `ATT-355` (*Good and consistent UI*), refining and unifying `REQ-UI-152` (`DropboxSettingsDialog.kt`) and `REQ-DAT-020.6` (`GoogleDriveSettingsDialog.kt`). Aligns with `REQ-DAT-032.7`, Rule 36, and Rule 62.
2. *Historical Origin & Commit Trace*: `REQ-UI-152` in Sprint `2026-40.4` (`commit d93fa11b`), refined in `ATT-4588` (`commit ce9a4913`). `REQ-DAT-020` in Sprint `2026-41.1` (`commit 624b5d2e`). Backend accessors added in `ATT-4665` (`commit dfdc602c`).
3. *Root Reason for Existing Formulation*: Independent implementation cycles created divergent UI surfaces before backend capabilities were unified.
4. *Preservation of Core Invariants*: OAuth PKCE redirect (`db-iknmdmr31sf64r0`), Google Sign-In `drive.file` scope, credential isolation in `auth_credentials_prefs.xml`, `BackupWorker` scheduling semantics, 9-language translation parity, and test contracts (`ComposeSafePainterContractTest`, `DropboxSettingsDialogTest`) remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opens `DropboxSettingsDialog` while disconnected from Dropbox,
• *When* the dialog renders,
• *Then* `CloudConnectionHeader` shows disconnected status and "Connect to Dropbox" button, and `@string/dropbox_connect_prompt` is shown while switches are hidden.
• *Given* an athlete opens `GoogleDriveSettingsDialog` or `DropboxSettingsDialog` while connected,
• *When* the dialog renders,
• *Then* both dialogs render symmetric switches for "Upload Workouts", "Upload Backups", "Wi-Fi Only", and timestamps for Last Sync and Last Backup.
• *Given* "Upload Backups" is enabled in either `DropboxSettingsDialog` or `GoogleDriveSettingsDialog`,
• *When* inspecting the dialog,
• *Then* the "Backup Interval" `DropdownSelector` is displayed with entries from `backup_interval_entries`.
• *Given* modified switches and backup interval in either dialog,
• *When* "Speichern" is tapped,
• *Then* preferences are committed to `TrainingApplication` / `SharedPreferences`, `BackupWorker.schedule(context)` is invoked, and the dialog dismisses.
• *Given* the 9 supported locales,
• *When* `TranslationParityTest` runs,
• *Then* all new keys exist across all 9 locales with 100% parity.

**Invariants**: Zero breakage of OAuth PKCE redirect or Google Sign-In, `ComposeSafePainterContractTest.kt`, `DropboxSettingsDialogTest.kt`, and 100% clean-room test pass rate.

### REQ-UI-431: Landscape Compact Aligned Marginal Zone Distribution Bars Right of Telemetry Graph [Verified]
- **Tests**: `TST-UI-396`, `TST-UI-398`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryMetricGraph.kt`
- **Rationale**: In landscape orientation, the system SHALL decouple the marginal zone distribution from the compressed 25dp inline strip into an aligned side-by-side marginal zone distribution pane docked immediately to the right of the continuous telemetry graph. This utilizes available horizontal viewport width (~640–900dp), establishes pixel-perfect vertical Y-axis alignment between horizontal zone bars and telemetry curve thresholds, displays legible zone labels, percentage shares, and formatted time durations, seamlessly synchronizes display mode transitions (`FIVE_ZONES` vs. `HISTOGRAM`), and preserves 100% byte-for-byte portrait layout fidelity (ATT-2145, Parent Epic ATT-2144, status: Verified, refining and extending `REQ-UI-251` and `REQ-UI-206`):
1. *Landscape Proportional Dual-Pane Layout Composition (`REQ-UI-431.1`)*:
• In landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), `TelemetryMetricGraph` SHALL arrange the continuous telemetry curve canvas and the marginal zone distribution pane side-by-side in a horizontal `Row`.
• The left telemetry chart canvas SHALL receive proportional weight ~72% (`Modifier.weight(0.72f)`).
• The right marginal zone distribution pane SHALL receive proportional weight ~28% (`Modifier.weight(0.28f)`).
2. *Exact Vertical Y-Axis Coordinate Alignment (`REQ-UI-431.2`)*:
• In landscape mode, the marginal zone distribution pane SHALL align all horizontal zone bars (in 5-zone modes) or frequency histogram bins (in histogram mode) 1:1 on the vertical axis with the corresponding metric threshold boundaries and plot area of the adjacent telemetry graph.
• The top and bottom paddings of the marginal zone container SHALL strictly match the graph plot bounds, ensuring zone boundaries match the graph's horizontal dashed threshold guidelines.
3. *Granular Metrics Text Legibility (`REQ-UI-431.3`)*:
• In landscape mode, each horizontal zone bar SHALL render a filled weighted bar with overlaid text displaying Zone Label (e.g., `Z5`), Percentage share (e.g., `45%`), and Duration (e.g., `19:40 min` formatted via `TimeFormatter`).
• Text typography, color contrast, and clipping prevention SHALL ensure legibility across dynamic themes and font scales.
4. *Synchronous Display Mode Switching (`REQ-UI-431.4`)*:
• The marginal zone distribution pane SHALL observe the hoisted `zoneDisplayMode: ZoneCardDisplayMode` state synchronously with the aftermath zone distribution cards.
• In `FIVE_ZONES`, `COLUMNS`, or `STACKED_BAR` modes, the pane SHALL render 5 horizontal zone bars mapped to the respective metric zone thresholds.
• In `HISTOGRAM` mode, the pane SHALL render frequency distribution bars mapped to the metric's histogram bins.
5. *Preservation of Portrait Invariants (`REQ-UI-431.5`)*:
• In portrait orientation (`Configuration.ORIENTATION_PORTRAIT`), `TelemetryMetricGraph` SHALL maintain the 25dp inline canvas strip per `REQ-UI-251` (`ATT-2147`) with 100% byte-for-byte visual and functional fidelity.
• The start padding (`startPadding = 50.dp`) SHALL remain identical across both orientations, guaranteeing synchronized vertical scrubbing alignment across multi-chart stacks.
6. *Preservation of System Invariants & Gestures (`REQ-UI-431.6`)*:
• Touch gestures (scrub drag, tap point selection, pinch-to-zoom/pan), callback invocations (`onDistanceSelected`, `onPointSelected`, `onZoomChanged`), and zero callback suppression (Rule 34) MUST remain completely unimpaired.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-431` under Epic `ATT-2144` (*Landscape Mode*), refining and extending `REQ-UI-251` (*Marginal Zone Bars & Histogram in Portrait*) and `REQ-UI-206` (*Continuous Telemetry Graph Composable*).
2. *Historical Origin & Commit Trace*: Commit `c8192a0e` (`ATT-2147`, `REQ-UI-251`, `TST-UI-210`, inline marginal zone strip within `endPaddingPx = 25.dp`).
3. *Root Reason for Existing Formulation*: Portrait viewports (~360dp width) lack horizontal space for text labels and time metrics without severe graph compression. Landscape orientation provides abundant horizontal width (~640–900dp) allowing a dedicated 28% pane while maintaining exact vertical Y-axis alignment.
4. *Preservation of Core Invariants*: 100% portrait layout fidelity (25dp inline strip), multi-chart scrubber vertical alignment (`startPadding = 50.dp`), touch gesture disambiguation, and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete inspects workout telemetry graphs in landscape orientation,
• *When* `TelemetryMetricGraph` renders with a valid `zoneDistribution`,
• *Then* the chart canvas and marginal zone distribution pane are displayed side-by-side in a 72/28 proportional `Row` layout.
• *Given* the landscape marginal zone distribution pane,
• *When* rendered alongside the telemetry curve,
• *Then* the top and bottom boundaries of each zone bar align 1:1 on the vertical Y-axis with the graph's zone threshold Y-coordinates.
• *Given* the landscape marginal zone bars,
• *When* rendered with valid telemetry zone distribution data,
• *Then* each bar renders an overlaid text summary showing Zone Label, Percentage, and formatted Duration.
• *Given* an athlete toggles between `FIVE_ZONES` and `HISTOGRAM` display modes,
• *When* the state updates,
• *Then* the landscape marginal pane synchronously transitions between 5 horizontal zone bars and histogram frequency bars.
• *Given* the device is in portrait orientation,
• *When* `TelemetryMetricGraph` renders,
• *Then* the existing 25dp inline canvas strip is rendered without regression and multi-chart scrubbing alignment is preserved.

**Invariants**: Zero regression to portrait mode, zero regression to touch gestures or scrub synchronization, `startPadding = 50.dp` preserved, and 100% clean-room test pass rate.

### REQ-UI-432: In-App Feature Discovery, Tips Guide & Navigation Drawer Integration [Verified]
- **Tests**: `TST-UI-397`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TipsGuideScreen.kt`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: The system SHALL provide an opt-in, non-intrusive in-app feature discovery surface ("Tips & Tricks" / `TipsGuideScreen`) accessible via the navigation drawer (`R.id.drawer_tips_guide`) under the collapsible Settings group. This guides athletes to discover high-value background capabilities (favorite route clustering, sensor redundancy failover, customizable cockpit layouts, Strava live segments, automated gear tracking, physiological analytics, and offline data sovereignty) with direct 1-tap deep links into relevant app screens, zero blocking walkthrough overlays, zero asset/bitmap bloat, and full 9-language localization parity (ATT-2276, Parent Epic ATT-2273, status: Verified, amending and superseding `REQ-UI-422.3` and `REQ-UI-159`):
1. *Navigation Drawer Item & Group Placement (`REQ-UI-432.1`)*:
• The navigation drawer SHALL include a dedicated destination item `R.id.drawer_tips_guide` with label `R.string.drawer_tips_guide` ("Tips & Tricks" / "Tipps & Tricks") and vector icon `R.drawable.ic_tips_and_updates`.
• `drawer_tips_guide` SHALL be positioned inside the collapsible `drawer__settings` group immediately preceding `drawer_privacy_policy` and `drawer_advanced_tuning`.
• Total destinations in the navigation drawer SHALL expand from 22 to 23 across the 5 category hubs, while strictly preserving the 9 default visible rows across non-collapsible groups (`drawer__training` [4] + `drawer__maps` [5] = 9) and maintaining `drawer_advanced_tuning` as the terminal entry.
2. *Compose Navigation Route Mapping (`REQ-UI-432.2`)*:
• `NavRoutes` SHALL define `NavRoutes.TIPS_GUIDE = "tips_guide"`.
• `NavRoutes.fromDrawerItemId(R.id.drawer_tips_guide)` SHALL resolve to `NavRoutes.TIPS_GUIDE`.
• `NavRoutes.toDrawerItemId(NavRoutes.TIPS_GUIDE)` SHALL resolve to `R.id.drawer_tips_guide`.
• Selecting `drawer_tips_guide` in `MainActivityWithNavigation` or `AppNavigationDrawer` SHALL close the drawer and navigate to `NavRoutes.TIPS_GUIDE` without destroying backstack state.
3. *TipsGuideScreen Layout & Accordion Architecture (`REQ-UI-432.3`)*:
• `TipsGuideScreen` SHALL be hosted in Compose with a standard `Scaffold`, `TopAppBar` (title `R.string.tips_guide_title`), back navigation button (`onNavigateBack`), and scrollable `LazyColumn`.
• The header block SHALL display an introductory title (`R.string.tips_guide_header_title`) and descriptive subtitle (`R.string.tips_guide_header_subtitle`).
• Tips SHALL be rendered as expandable Material 3 cards (`ElevatedCard`, `RoundedCornerShape(12.dp)`), containing an icon/badge, title, subtitle, expandable descriptive body, and an action button for deep linking.
4. *Eight Curated Feature Discovery Tips (`REQ-UI-432.4`)*:
• Tip 1 (Favorite Routes): Title `tip_routes_clustering_title`, subtitle `tip_routes_clustering_subtitle`, description `tip_routes_clustering_desc`, action button `tip_routes_clustering_action` ("View Favorite Routes") navigating to `NavRoutes.LOCATIONS`.
• Tip 2 (Sensor Failover): Title `tip_sensors_failover_title`, subtitle `tip_sensors_failover_subtitle`, description `tip_sensors_failover_desc`, action button `tip_sensors_failover_action` ("Manage Sensors") navigating to `NavRoutes.SENSORS`.
• Tip 3 (Cockpit Customization): Title `tip_cockpit_customization_title`, subtitle `tip_cockpit_customization_subtitle`, description `tip_cockpit_customization_desc`, icon `R.drawable.ic_table_edit`, action button `tip_cockpit_customization_action` ("Configure Displays") navigating to `NavRoutes.TRACKING_LAYOUTS` (triggering `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)` to open `ActivityTypeSelectionDialog`).
• Tip 4 (Strava Live Segments): Title `tip_live_segments_title`, subtitle `tip_live_segments_subtitle`, description `tip_live_segments_desc`, action button `tip_live_segments_action` ("Explore Segments") navigating to `NavRoutes.SEGMENTS`.
• Tip 5 (Gear & Equipment): Title `tip_gear_tracking_title`, subtitle `tip_gear_tracking_subtitle`, description `tip_gear_tracking_desc`, action button `tip_gear_tracking_action` ("Manage Equipment") navigating to `NavRoutes.EQUIPMENT`.
• Tip 6 (Physiology & Zones): Title `tip_training_zones_title`, subtitle `tip_training_zones_subtitle`, description `tip_training_zones_desc`, action button `tip_training_zones_action` ("Configure Zones") navigating to `NavRoutes.trainingZones()`.
• Tip 7 (Data Sovereignty & Offline Backup): Title `tip_data_backup_title`, subtitle `tip_data_backup_subtitle`, description `tip_data_backup_desc`, action button `tip_data_backup_action` ("Backup & Restore") navigating to `NavRoutes.BACKUP_RESTORE`.
• Tip 8 (Custom Sport Types): Title `tip_sport_types_title`, subtitle `tip_sport_types_subtitle`, description `tip_sport_types_desc`, icon `R.drawable.ic_sports_combined`, action button `tip_sport_types_action` ("Manage Sport Types") navigating to `NavRoutes.SPORT_TYPES`.
5. *Non-Intrusive & Zero-Asset-Bloat Invariant (`REQ-UI-432.5`)*:
• The Tips Guide SHALL be 100% opt-in: no automatic full-screen dialogs, blocking tutorial overlays, or modal walkthrough popups on app launch.
• Visual graphics SHALL rely entirely on existing Android vector drawables and Material 3 theme-aware composable styling; no raster bitmaps (PNG/JPG) or animated GIF/Lottie files SHALL be packaged.
6. *9-Language Localization Parity (`REQ-UI-432.6`)*:
• All 36 string resources (`drawer_tips_guide`, `tips_guide_title`, `tips_guide_header_title`, `tips_guide_header_subtitle`, and the 8 tip titles, subtitles, descriptions, and action button labels) SHALL maintain 100% translation coverage across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-432.4` (*Curated Feature Discovery Tips*) and `REQ-UI-432.6` (*9-Language Localization Parity*), refining `REQ-UI-432`.
2. *Historical Origin & Commit Trace*: Sprint 2026-43.9 via commit `99f66b16e` (`ATT-2276`, "Implement In-App Feature Discovery and Tips Guide").
3. *Root Reason for Existing Formulation*: Cockpit customization was initially wired to `NavRoutes.SPORT_TYPES` with `ic_display_settings` because cockpit layouts are partitioned by sport type, leaving custom sport types without its own tip card and incorrectly routing cockpit configuration to the sport types manager instead of the cockpit layouts dialog (`R.id.drawer_tracking_layouts`).
4. *Preservation of Core Invariants*: Opt-in non-intrusive discovery (`REQ-UI-432.5`), zero raster asset bloat, non-collapsible drawer density of 9 visible items preserved, and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the navigation drawer and expanding the Settings group,
• *When* inspecting the destination list,
• *Then* `R.id.drawer_tips_guide` is visible with label `R.string.drawer_tips_guide` and icon `R.drawable.ic_tips_and_updates`, positioned before `drawer_privacy_policy`.
• *Given* an athlete tapping the "Tips & Tricks" drawer item,
• *When* the navigation event occurs,
• *Then* the drawer closes and `TipsGuideScreen` is displayed at `NavRoutes.TIPS_GUIDE`.
• *Given* an athlete viewing `TipsGuideScreen`,
• *When* scrolling through the tip list,
• *Then* 8 curated feature cards are displayed with titles, subtitles, expandable descriptions, and action buttons.
• *Given* an athlete tapping the action button on the Cockpit Customization tip card,
• *When* clicked,
• *Then* the app triggers `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)`, opening the `ActivityTypeSelectionDialog`.
• *Given* an athlete tapping the action button on the Sport Types tip card,
• *When* clicked,
• *Then* the app navigates directly to `NavRoutes.SPORT_TYPES`.
• *Given* the 9 supported application locales,
• *When* strings are audited via `TranslationParityTest`,
• *Then* 100% parity is verified with zero missing entries and zero format specifier mismatches across all 36 tip guide string resources.

**Invariants**: Zero auto-prompting popups, zero background service disruption, non-collapsible drawer density remains 9, and 100% clean-room test pass rate.

### REQ-UI-433: Vertical Space Reclamation in WorkoutDetailTabbedContent: Scroll-Away Collapsing Header, Single-Line Landscape Header, Inline Zoom Controls, and 72% X-Axis Plot Alignment [Verified]
- **Tests**: `TST-UI-398`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetailTabbedContent.kt`, `WorkoutHeader.kt`, `MapDetailLayout.kt`, `TelemetryMetricGraph.kt`, `ElevationProfile.kt`
- **Rationale**: In `WorkoutDetailTabbedContent` (especially in landscape orientation with ~411dp vertical height), pinned top chrome currently consumes ~55% of the entire viewport height. The system SHALL reclaim vertical screen real estate for charts, maps, and analytics cards by providing scroll-away collapsing headers, a single-line compact landscape header, inline/floating zoom controls, and pixel-perfect 72% horizontal plot alignment across all stacked charts (ATT-4774, Parent Epic ATT-2144, status: Planned, refining `REQ-UI-408`, `REQ-UI-431`, and `REQ-UI-225`):
1. *Scroll-Away Collapsing Top Header (`REQ-UI-433.1`)*:
• In `WorkoutDetailTabbedContent`, scrolling down inside any tab (Mode A cards or Mode B split lower section) SHALL smoothly collapse the top chrome off the top of the viewport via `CollapsingAppBarNestedScrollConnection` (collapsing `WorkoutHeader` in portrait, and both `WorkoutHeader` and `PrimaryScrollableTabRow` in landscape mode), expanding charts and content to nearly 100% of vertical height.
• Scrolling upward SHALL immediately reveal the collapsed header chrome (quick return).
2. *Single-Line Compact Landscape WorkoutHeader (`REQ-UI-433.2`)*:
• In landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), `WorkoutHeader` SHALL collapse its 4 stacked vertical rows into a single compact horizontal row (`heightIn(max = 44.dp)`): `[Sport Icon] Title [Cluster Pill] • SportType • Date & Time [Actions]`.
• In portrait orientation, `WorkoutHeader` SHALL preserve its multi-row vertical layout with 100% byte-for-byte fidelity.
3. *Zero-Extra-Row Inline / Floating Zoom Controls (`REQ-UI-433.3`)*:
• In `MapDetailLayout`, zoom and pan controls (`-`, `+`, `👆`) SHALL NOT reserve a dedicated full-width 36dp vertical row.
• Controls SHALL be rendered as a compact floating overlay anchored to the top-right of the lower inspection viewport (or inlined within the first chart header row), reclaiming 36dp of vertical height for data visualization.
4. *Stacked Multi-Chart 72% Plot Width & Scrubber Alignment in Landscape (`REQ-UI-433.4`)*:
• In landscape orientation, all stacked charts (`ElevationProfile`, `Speed/Pace`, `Heart Rate`, `Power`, `W'bal`) SHALL share the identical horizontal plot width (~72% of viewport width from `startPadding = 50.dp` to plot right edge), ensuring X-axis ticks and vertical scrubbing cursors align 1:1 across all sister charts.
• On the 72% curve pane of `TelemetryMetricGraph` when `AlignedMarginalZoneDistribution` is docked on the right, redundant double right padding (`endPaddingPx = 25.dp`) SHALL be eliminated (`endPaddingPx = 0.dp`).
5. *Preservation of Portrait & Touch Invariants (`REQ-UI-433.5`)*:
• Portrait layout, gesture isolation on chart/map canvases, scrubber synchronization, and human decision gate MUST remain completely intact without regressions.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-408` (*Multi-Tab Workout Details Container*), `REQ-UI-431` (*Landscape Marginal Zone Distribution*), and `REQ-UI-225` (*Global Telemetry Zoom Toolbar*).
2. *Historical Origin & Commit Trace*: Commit `ea5fa705` (`ATT-3920`), commit `dcb8f15f` (`ATT-2145`), and commit `1357ce0b` (`ATT-1876` / `ATT-3453`).
3. *Root Reason for Existing Formulation*: Pinned chrome guaranteed visibility on portrait screens (~800dp height); `ATT-2145` docked marginal zone bars on HR/Power but left sibling charts at 100% width and zoom controls in a dedicated row, causing 55% vertical occlusion on landscape viewports (~411dp).
4. *Preservation of Core Invariants*: 100% portrait fidelity, multi-chart scrubbing synchronization (`startPadding = 50.dp`), gesture isolation, and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete inspecting workout details in `WorkoutDetailTabbedContent`,
• *When* scrolling down inside a tab's scrollable content,
• *Then* the top header chrome smoothly collapses off-screen, maximizing the vertical viewing area for charts and cards.
• *Given* the device is in landscape orientation,
• *When* `WorkoutHeader` renders,
• *Then* it displays sport icon, title, cluster pill, sport type, date/time, and action buttons in a single compact horizontal row.
• *Given* `MapDetailLayout` displaying telemetry and elevation charts,
• *When* rendered,
• *Then* zoom controls consume zero dedicated row height, anchored as a compact floating overlay or inlined control.
• *Given* stacked elevation and telemetry charts in landscape mode,
• *When* rendered,
• *Then* all charts align their right plot bounds to the 72% boundary and X-axis ticks / scrubbing cursor align 1:1 vertically across all charts.

**Invariants**: Zero regression in portrait aftermath inspection, multi-chart scrub synchronization preserved, and human decision gate enforced.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
