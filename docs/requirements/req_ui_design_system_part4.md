# Living Requirements: UI Components & Design System (Part 4 of 5)

**Document ID**: `DOC-REQ-UI-P4`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part4`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Requirements**: 91

---

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

### REQ-UI-434: Landscape Cockpit Vertical Space Optimization: Sensor Icons in Center Status Bar and Auto-Hiding Floating Tab Row [Verified]
- **Tests**: `TST-UI-399`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsScreen.kt`, `SensorStatus.kt`
- **Rationale**: In `TrackingTabsScreen` in landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`) during `ScreenMode.TRACKING`, three stacked horizontal bars (`statusBarsPadding()`, `SensorStatus`, `PrimaryScrollableTabRow`) consume ~130–145dp of vertical space, occluding ~35% of the screen. In landscape mode, the center of the Android status bar between the left clock/notifications and right status icons is completely vacant. The system SHALL reclaim vertical space for live tracking by centering sensor availability icons inside the status bar strip and presenting the tab row as an auto-hiding floating overlay (ATT-4773, Parent Epic ATT-2144, status: Planned, refining `REQ-UI-163`, `REQ-UI-410`, and `REQ-UI-412`):
1. *Sensor Availability Icons in Center Status Bar (`REQ-UI-434.1`)*:
• In landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`) during `ScreenMode.TRACKING`, `SensorStatus` availability icons SHALL be rendered centered in the top status bar strip, omitting separate `statusBarsPadding()` vertical stacking.
• The drawer button (☰) SHALL be positioned without colliding with the top-left system clock (e.g., at the leading edge of the floating tab row or just left of the centered sensor icons).
2. *Auto-Hiding Floating Tab Row Overlay (`REQ-UI-434.2`)*:
• In landscape orientation during `ScreenMode.TRACKING`, `PrimaryScrollableTabRow` SHALL NOT occupy permanent vertical height in the layout hierarchy.
• `PrimaryScrollableTabRow` SHALL be rendered as a floating top overlay (`zIndex(1f)`) over `HorizontalPager`, smoothly animated (fade/slide) into view whenever the user scrolls/swipes tabs (`pagerState.isScrollInProgress`), switches tabs, taps the top area, or is on Page 0 (`ControlTab`), and SHALL automatically fade/slide out after a 2.5–3.0 second timeout once scrolling ceases.
3. *Screen Mode & Portrait Invariant Preservation (`REQ-UI-434.3`)*:
• In portrait orientation (`Configuration.ORIENTATION_PORTRAIT`), `TrackingTabsScreen` SHALL retain 100% byte-for-byte fidelity with persistent `statusBarsPadding()`, persistent `SensorStatus`, and persistent `PrimaryScrollableTabRow`.
• In `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`, top headers SHALL remain persistently visible across all orientations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-163` (*Tracking Tabs Screen Navigation*), `REQ-UI-410` (*Tracking Tabs Sensor Status Bar*), and `REQ-UI-412` (*Tracking Screen Mode Switching*).
2. *Historical Origin & Commit Trace*: Commit `e253c312` (`ATT-1340`), commit `de1c2965` (`ATT-4000`), and commit `d63529d8` (`ATT-4097`).
3. *Root Reason for Existing Formulation*: Static `Column` with `statusBarsPadding()` and persistent `PrimaryScrollableTabRow` guaranteed discoverability and prevented status bar clipping on tall portrait screens (~800dp). In landscape (~411dp), permanent ~130–145dp chrome wastes ~35% of the viewport.
4. *Preservation of Core Invariants*: 100% portrait layout fidelity, tab switching/swipe responsiveness, bottom button clearance (`LapButton`, `BikeBellButton`), and 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tracking a workout in landscape orientation,
• *When* `TrackingTabsScreen` is in `ScreenMode.TRACKING`,
• *Then* sensor availability icons are rendered centered in the top status bar strip, and `PrimaryScrollableTabRow` floats as an auto-hiding overlay without permanently pushing down `HorizontalPager`.
• *Given* the floating tab row is hidden in landscape mode,
• *When* the athlete swipes between tabs or switches tabs,
• *Then* the floating tab row smoothly animates into view and remains visible for 2.5–3.0 seconds after scrolling stops before fading out.
• *Given* the device is in portrait orientation or in `ScreenMode.CONFIGURATION`/`PREVIEW`,
• *When* rendered,
• *Then* status bars padding and persistent headers remain fully visible with zero regression.

**Invariants**: Zero regression in portrait mode, gesture slop and touch event routing preserved, and human decision gate enforced.

### REQ-UI-435: Tracking Tabs Mode Transition Initial Page Resolution [Verified]
- **Tests**: `TST-UI-435`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsPageResolver.kt`, `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: When navigating between `ScreenMode.TRACKING`, `ScreenMode.CONFIGURATION`, and `ScreenMode.PREVIEW`, `TrackingTabsScreen` SHALL resolve the initial pager page index correctly, preventing off-by-one tab jumps caused by the presence of the Control Tab at index 0 in `ScreenMode.TRACKING` (ATT-4838, Parent Epic ATT-355, extending `REQ-UI-163` and `REQ-UI-412`):
1. *Drawer Configuration Entry (`REQ-UI-435.1`)*:
• When transitioning into `ScreenMode.CONFIGURATION` from `ScreenMode.TRACKING` or via `MainActivityWithNavigation.onActivityTypeSelected(activityType)`, the initial pager page index SHALL be resolved to `0` (the first configured sensor tab), and `lastKnownPage` SHALL be reset to `0`.
2. *Editor-Preview Round-Trip (`REQ-UI-435.2`)*:
• Switching between `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW` SHALL preserve the active sensor tab index ($p \leftrightarrow p$), coerced within `0 until tabCount`.
3. *Preview Exit to Tracking (`REQ-UI-435.3`)*:
• Exiting from `ScreenMode.PREVIEW` (or `ScreenMode.CONFIGURATION`) back to `ScreenMode.TRACKING` SHALL map sensor tab index $p$ to tracking page index $p + 1$ (accounting for the Control Tab at page 0), or `0` if `tabCount <= 0`.
4. *Sport Type Switching within Configuration (`REQ-UI-435.4`)*:
• When `activityType` changes while in `ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW`, the pager state SHALL be recreated via `key(activityType, trackingViews.size, screenMode)` and initial page SHALL reset to `0`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-435`, extending `REQ-UI-163` (Tracking Tabs Screen Navigation) and `REQ-UI-412` (Tracking Screen Mode Switching).
2. *Historical Origin & Commit Trace*: `ATT-1340` (commit `e253c312`), `ATT-1456` (commit `c457fa91`), `ATT-4000` (commit `de1c2965`).
3. *Root Reason for Existing Formulation*: `lastKnownPage` was originally designed to prevent pager recreation from snapping to page 0 when sensor tabs were added, deleted, or reordered. It treated the pager index as a raw scalar across all screen modes, overlooking that `ScreenMode.TRACKING` has an extra Control Tab at page 0 whereas `CONFIGURATION` and `PREVIEW` do not.
4. *Preservation of Core Invariants*: Pager state persistence during tab additions/deletions, timeout-safe event routing, and gesture slop are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on Sensor Tab 0 in `ScreenMode.TRACKING` (`lastKnownPage = 1`),
• *When* the athlete navigates to *Settings -> Configure Displays* and selects an `ActivityType`,
• *Then* `TrackingTabsScreen` enters `ScreenMode.CONFIGURATION` with initial page `0` (the first configured tab).
• *Given* an athlete editing Sensor Tab 1 (`page = 1`) in `ScreenMode.CONFIGURATION`,
• *When* the athlete toggles to `ScreenMode.PREVIEW` (or back to `CONFIGURATION`),
• *Then* the pager remains on Sensor Tab 1 (`page = 1`).
• *Given* an athlete previewing Sensor Tab 1 (`page = 1`) in `ScreenMode.PREVIEW`,
• *When* the athlete exits preview back to `ScreenMode.TRACKING`,
• *Then* the tracking cockpit opens on Sensor Tab 1 (`page = 2`, accounting for the Control Tab).

**Invariants**: Pager stability during tab mutations, zero regressions in existing navigation and timeout suites, and 100% clean-room test pass rate.

### REQ-UI-436: Active Training Zone Segment Height Emphasis & Bar-Only Sizing [Verified]
- **Tests**: `TST-UI-436`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneGaugeBar.kt`, `SensorFieldView.kt`, `PedalPowerBalanceBar.kt`, `WPrimeBatteryGaugeBar.kt`
- **Rationale**: In glance-based peripheral vision during athletic workouts, the active training zone segment in `ZoneGaugeBar` SHALL be visually prominent by elevating its vertical height relative to inactive segments, and when a sensor field is configured in bar-only mode (`showValue = false, showBar = true`), bars SHALL dynamically scale their height according to tile `ViewSize` to cleanly fill available vertical card space (ATT-4860, Parent Epic ATT-355, extending `REQ-UI-376`, `REQ-UI-390`, and `REQ-TRK-046`). Furthermore, outer container heights SHALL remain strictly constant across live vs. stale/null telemetry states to prevent cockpit tile and grid height jumps (ATT-4928):
1. *Active Zone Height & Geometry Emphasis (`REQ-UI-436.1`)*:
• In `ZoneGaugeBar.kt`, the outer container height (`totalBarHeight`) SHALL unconditionally reserve the elevated track height plus needle overhang (`calculateReservedActiveTrackHeight(trackHeight) + 4.dp`, where `calculateReservedActiveTrackHeight(trackHeight) = trackHeight * 1.6f`), strictly invariant of telemetry staleness or active zone state (ATT-4928).
• The horizontal base track (`height = trackHeight`) SHALL be centered vertically within the reserved container.
• When a valid zone is active (`state.activeZoneIndex != null` and in `1..segmentColors.size`) and telemetry is live (`!isStale`), the active zone segment (`index == state.activeZoneIndex - 1`) SHALL be rendered vertically elevated with height equal to $1.6\times$ `trackHeight` (`activeTrackHeight = calculateReservedActiveTrackHeight(trackHeight)`), centered vertically within the track container, with rounded corner radius (`CornerRadius(activeTrackHeight / 4)`).
2. *Inactive & Stale Segment Behavior (`REQ-UI-436.2`)*:
• Inactive zone segments SHALL be rendered with standard un-elevated `trackHeight`.
• When telemetry is stale or null (`state.needleFraction == null` or `state.activeZoneIndex == null`), all segments SHALL be rendered at uniform base `trackHeight` with `0.35f` dimmed alpha without changing outer container height `totalBarHeight`.
3. *Needle Span Synchronization (`REQ-UI-436.3`)*:
• The Primary Blue needle overlay and its contrasting halo outline SHALL span the full reserved container height (`calculateReservedActiveTrackHeight(trackHeight) + 4.dp`), ensuring it cleanly covers and extends 2dp above and below the elevated active segment.
4. *Dynamic Height Scaling in Bar-Only Mode (`REQ-UI-436.4`)*:
• In `SensorFieldView.kt`, when a sensor field is in bar-only mode (`!fieldState.showValue && fieldState.showBar`), horizontal telemetry bars SHALL scale their heights with `fieldState.viewSize`:
  - `ZoneGaugeBar`: `SMALL = 10.dp`, `NORMAL = 14.dp`, `LARGE = 18.dp`, `EXTRA_LARGE = 22.dp` (vs standard `4.dp`, `6.dp`, `8.dp`, `10.dp`).
  - `PedalPowerBalanceBar`: `SMALL = 12.dp`, `NORMAL = 16.dp`, `LARGE = 20.dp`, `EXTRA_LARGE = 24.dp` (vs standard `6.dp`, `8.dp`, `10.dp`, `12.dp`).
  - `WPrimeBatteryGaugeBar`: `SMALL = 14.dp`, `NORMAL = 20.dp`, `LARGE = 26.dp`, `EXTRA_LARGE = 32.dp` (vs standard `16.dp`).
5. *Component Height Invariance & Leftover Hygiene (`REQ-UI-436.5`)*:
• `PedalPowerBalanceBar` and `WPrimeBatteryGaugeBar` SHALL maintain strictly constant vertical heights across live vs stale/null states.
• Obsolete overloads (`calculateActiveTrackHeight(baseTrackHeight: Dp, isActive: Boolean)`) in `ZoneGaugeBar.kt` and commented-out height overrides (`// .height(W_PRIME_BATTERY_GAUGE_HEIGHT)`) in `WPrimeBatteryGaugeBar.kt` SHALL be removed (ATT-4928).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-436`, extending `REQ-UI-376` (Zone Gauge Bar), `REQ-UI-390` (Proportional Zone Track Scaling), and `REQ-TRK-046` (Per-Field Element Display Configuration). Refined in `ATT-4928` for container height invariance across live and stale states.
2. *Historical Origin & Commit Trace*: `ATT-1293` (commit `3e78df12`), `ATT-3708` (commit `69df2531`), `ATT-4841` (commit `0ea00b1d`), `ATT-4860` (commit `9d5fd7ff`).
3. *Root Reason for Existing Formulation*: `ZoneGaugeBar` originally clipped all segments inside a single uniform outer `Box(height = trackHeight)`. Introducing active zone emphasis required per-segment height differentiation, but `ATT-4860` conditionally shrank `totalBarHeight` to `trackHeight + 4.dp` when stale, causing sensor card height jumps.
4. *Preservation of Core Invariants*: Proportional segment weights (`state.segmentWeights`), needle interpolation math (`ZoneGaugeMath`), 0.35 stale alpha, visual 1.6x active elevation when live, and bottom `"Z1"`..`"Z7"` badges remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ZoneGaugeBar` rendering an active workout in Zone 3 (`activeZoneIndex = 3`),
• *When* the track segments are drawn,
• *Then* segment index 2 renders with height $1.6\times$ `trackHeight` and rounded corners, while segments 0, 1, 3, and 4 render at `trackHeight`, within reserved container height $1.6\times \text{trackHeight} + 4\text{dp}$.
• *Given* `ZoneGaugeBar` transitioning from live telemetry to stale or null telemetry (`needleFraction == null`),
• *When* the component recomposes,
• *Then* the measured outer container height remains 100% invariant ($1.6\times \text{trackHeight} + 4\text{dp}$) and all segments render with uniform `trackHeight` and `0.35f` opacity.
• *Given* a sensor field with `showValue = false` and `showBar = true` in `ViewSize.NORMAL`,
• *When* `SensorFieldView` renders the card,
• *Then* the gauge track height is scaled to 14.dp instead of 6.dp.

**Invariants**: Zero layout jump when switching between live and stale telemetry, zero regression in stale/null handling, True-Black AMOLED compliance, and 100% clean-room test pass rate.

### REQ-UI-437: User-Configurable Horizontal Width Ratios & Split Presets in Cockpit Sensor Grid [Verified]
- **Tests**: `TST-UI-437`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `TrackingViewsRepository.kt`, `SensorFieldState.kt`, `SensorGridScreen.kt`, `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`
- **Rationale**: In multi-column cockpit rows, side-by-side sensor fields have historically been forced to equal width (`1/2 : 1/2` or `1/3 : 1/3 : 1/3`) via hardcoded `Modifier.weight(1f)`. Asymmetric layouts—such as a wide `3/4` Power Zone Bar paired with a compact `1/4` numeric value readout—require user-configurable horizontal width ratios, backward-compatible persistence, and proportional Compose rendering (ATT-4861, Parent Epic ATT-355, extending `REQ-UI-200` and `REQ-TRK-046`):
1. *SQLite Schema v17 & DTO Weight Persistence (`REQ-UI-437.1`)*:
• In `TrackingViewsDatabaseManager.java`, `LayoutRowsTable` SHALL include a `ColWeight real DEFAULT 1.0` column, bumping `DB_VERSION` to 17.
• Database upgrade from v16 to v17 SHALL add `ColWeight` and backfill `ColWeight = 1.0` for all existing records.
• `DatabaseSchemaRegistry.kt` SHALL register version 17 for `TrackingViews.db` and `FlexibleGenericView.db`.
• `SensorFieldConfig` and `SensorFieldState` SHALL include `colWeight: Float = 1.0f`, mapped symmetrically across all cursor mapping pathways.
2. *Proportional Weight Cockpit Rendering (`REQ-UI-437.2`)*:
• In `SensorGridScreen.kt`, multi-column rows in `SensorGridContent`, landscape row packing, and configuration mode SHALL apply `Modifier.weight(effectiveWeight)`.
• Runtime layout code SHALL guard `effectiveWeight = if (fieldState.colWeight > 0f) fieldState.colWeight else 1.0f`, preventing `IllegalArgumentException` from zero or negative weights.
• Single-field rows SHALL span full row width.
3. *User Configuration UI in Edit Dialog & Cardinality Gating (`REQ-UI-437.3`)*:
• In `EditSensorFieldDialog.kt`, the Relative Width section (`R.string.sensor_field_relative_weight`) providing selectable relative weight options (`1x`, `2x`, `3x`) SHALL be rendered conditionally strictly when `uiState.isMultiFieldRow == true`. For single-field rows (`isMultiFieldRow == false`), the Relative Width section SHALL be omitted to eliminate misleading configuration controls (ATT-5192).
• In `EditSensorFieldViewModel.kt`, `isMultiFieldRow` SHALL be determined during `loadInitialState()` and `setupDefaultState()` via pure helper `calculateIsMultiFieldRow(existingFieldsInTargetRow, isNewField)`.
• The legacy overflowing 5-chip *Zeilenaufteilung* row-split presets section (`3/4 : 1/4`, `2/3 : 1/3`, `1/2 : 1/2`, `1/3 : 2/3`, `1/4 : 3/4`), `isTwoFieldRow` dual-weight model, and sibling weight synchronization SHALL remain removed from `EditSensorFieldDialog.kt` and `EditSensorFieldViewModel.kt` (ATT-5077).
• The dialog SHALL persist the selected `colWeight` per field via `updateSensorFieldConfig()`.
4. *9-Language Localization Parity (`REQ-UI-437.4`)*:
• String resources for relative width section titles SHALL maintain complete parity across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), and unused `sensor_field_row_split_presets` string keys SHALL be removed from all 9 locales (ATT-5077).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Modifies `REQ-UI-437.3` under parent `REQ-UI-437`. Targets `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, and `EditSensorFieldViewModelTest.kt` (ATT-5077, ATT-5192).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-44.2 via commit `244300a2` (`ATT-4861`), simplified in `ATT-5077` (Sprint 2026-45.1), and cardinality-gated in `ATT-5192` (Sprint 2026-45.2).
3. *Root Reason for Existing Formulation*: Relative width chips (1x, 2x, 3x) were originally rendered unconditionally. However, for single-field rows, relative weight has zero effect on layout and causes athlete confusion.
4. *Preservation of Core Invariants*: Multi-field rows retain full access to `1x`, `2x`, `3x` relative weights; `colWeight` persistence and proportional layout math in `SensorGridScreen.kt` remain 100% unaffected.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete editing a sensor field that is the only tile in its row,
• *When* `EditSensorFieldDialog` is displayed,
• *Then* the Relative Width section (`1x`, `2x`, `3x`) SHALL NOT be rendered.
• *Given* an athlete editing a sensor field sharing a row with one or more sibling fields,
• *When* `EditSensorFieldDialog` is displayed,
• *Then* the Relative Width section (`1x`, `2x`, `3x`) SHALL be rendered.
• *Given* any sensor field in `EditSensorFieldDialog` in a multi-field row,
• *When* the user selects a relative weight (`1x`, `2x`, or `3x`),
• *Then* `colWeight` is updated (`1.0f`, `2.0f`, or `3.0f`) and persisted, rendering the field proportionally in multi-column rows without horizontal clipping or overflowing preset chips.
• *Given* an existing database upgraded from schema v16 to v17,
• *When* rows are loaded,
• *Then* all existing layout fields are migrated with `colWeight = 1.0f`.
• *Given* a sensor field with an invalid or zero weight ($\le 0f$),
• *When* rendered in Compose,
• *Then* the layout engine safely clamps to 1.0f without crashing.
• *Then* `ColWeight` defaults to 1.0f and existing equal-width layouts remain unchanged.
• *Given* a sensor field with an invalid or zero weight ($\le 0f$),
• *When* rendered in Compose,
• *Then* the layout engine safely clamps to 1.0f without crashing.

**Invariants**: Backward compatibility for existing layouts, zero Compose runtime crash risk ($weight > 0f$), 9-language localization parity, and 100% clean-room test pass rate.

### REQ-UI-438: Vertical Card Height and Border Stretching in Cockpit Sensor Grid [Verified]
- **Tests**: `TST-UI-438`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `SensorFieldView.kt`
- **Rationale**: In multi-column cockpit rows, fields of different sizes (e.g. `SMALL` and `LARGE`) or differing layouts (numeric readout next to a zone gauge bar) share a row wrapped in `Row(modifier = Modifier.height(IntrinsicSize.Min))`. Currently, intermediate `Box` wrappers and `SensorFieldView` `Card` omit `.fillMaxHeight()`, and the inner card `Row` hardcodes `.height(IntrinsicSize.Min)`. This leaves shorter cards floating at their intrinsic minimum height with disjointed borders and misaligned indicator strips. Stretching the card height, extending borders, and vertically centering content ensures a unified, professional head-unit cockpit UI (ATT-4878, Parent Epic ATT-355, extending `REQ-UI-200` and `REQ-UI-437`):
1. *Row Height Propagation & Container Expansion (`REQ-UI-438.1`)*:
• In `SensorGridScreen.kt`, within multi-column sensor rows in `SensorGridContent`, landscape packing, and configuration mode, the per-cell `Box` SHALL apply `Modifier.weight(effectiveWeight).fillMaxHeight()`.
• `SensorFieldView` SHALL receive and apply `modifier = Modifier.fillMaxHeight()` to fill the cell container height.
2. *Full-Height Card and Border Rendering (`REQ-UI-438.2`)*:
• In `SensorFieldView.kt`, `Card` SHALL apply `modifier.fillMaxWidth().fillMaxHeight()`.
• The inner card layout `Row` SHALL apply `Modifier.fillMaxWidth().fillMaxHeight()` instead of `.height(IntrinsicSize.Min)`.
• Card borders and left/right zone indicator strips (`Spacer`) SHALL span 100% of the stretched height flush with the tallest card in the row.
3. *Vertical Content Centering with Anchored Header & Actions (`REQ-UI-438.3`)*:
• In `SensorFieldView.kt`, the content `Column` SHALL apply `.fillMaxHeight()`, anchoring the field label and filter description `Row` to the top.
• The sensor numeric readout, unit label, and any graphical bars/gauges (Zone Gauge, Pedal Balance, W' Anaerobic Battery) SHALL be vertically centered within the remaining card height.
• Configuration mode action icon buttons (`IconButton`) SHALL remain docked at the bottom of the card.
4. *Visual Parity & Clean-Room Invariants (`REQ-UI-438.4`)*:
• Cockpit rows with identical `ViewSize` cards and single-field rows SHALL preserve existing layout geometry with zero visual regression.
• Localization and accessibility labels SHALL maintain full parity across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-438`, extending `REQ-UI-200` (Sensor Grid Normalization & Layout) and `REQ-UI-437` (Horizontal Width Ratios). Targets `SensorGridScreen.kt` and `SensorFieldView.kt`.
2. *Historical Origin & Commit Trace*: Commit `a6920388` introduced initial Jetpack Compose sensor grid layout with unconstrained child card heights.
3. *Root Reason for Existing Formulation*: Basic cell layout was designed for homogeneous numeric fields before heterogeneous sizes and graphical gauge bars were placed side by side.
4. *Preservation of Core Invariants*: `Row(modifier = Modifier.height(IntrinsicSize.Min))` strictly preserves finite row height governed by the tallest child; `.fillMaxHeight()` only propagates this measured height to shorter siblings without expanding the parent row.

**Acceptance Criteria (Given-When-Then)**:
• *Given* two sensor fields of different intrinsic heights placed in the same cockpit row,
• *When* rendered in tracking, preview, or configuration modes,
• *Then* both cards stretch to equal height matching the tallest field in the row, with flush continuous borders and indicator strips.
• *Given* a stretched sensor card with extra vertical space,
• *When* laid out,
• *Then* the header label is anchored at the top, configuration buttons are docked at the bottom, and the telemetry value/gauge is centered vertically.
• *Given* a row of uniform-size cards or a single-column row,
• *When* rendered,
• *Then* the visual presentation is identical to pre-modification behavior.

**Invariants**: Zero infinite height expansion, strict preservation of `IntrinsicSize.Min` on parent `Row`, vertical telemetry centering, and 100% clean-room test pass rate.

### REQ-UI-439: Copy Tracking Tab to Target Activity Types [Verified]
- **Tests**: `TST-UI-439`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabConfigHeader.kt`, `CopyTrackingTabDialog.kt`, `TrackingTabsViewModel.kt`, `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`
- **Rationale**: Athletes configuring custom tracking cockpits often wish to reuse complex tab layouts across different sport types (`ActivityType`), or duplicate an existing tab within the same sport type. Providing a multi-select bottom sheet to copy an existing tab to one or more target sport types with deep-copy and append semantics eliminates tedious manual reconfiguration (ATT-5078, ATT-5189, extending `REQ-UI-103` and `REQ-UI-175`):
1. *Copy Tab Header Action (`REQ-UI-439.1`)*:
• In `TrackingTabConfigHeader.kt`, Row 2 SHALL provide a Copy Tab icon button (`Icons.Default.ContentCopy`) alongside `Add Before`, `Delete`, and `Add After`, invoking `onCopyTab(tabViewId)`.
2. *Target ActivityType Selection Dialog (`REQ-UI-439.2`)*:
• The system SHALL provide a modal bottom sheet (`CopyTrackingTabDialog.kt`) listing all `ActivityType` profiles with their authentic sport logos (`type.logoId`), localized titles, and multi-select checkboxes.
• Upon opening the dialog, all sport profiles SHALL start unselected by default (`selectedTypes` initialized to empty set), ensuring no profile (including the current source activity type) is preselected (ATT-5189).
• The Copy action button SHALL remain disabled while no sport profile is selected (`selectedTypes.isEmpty()`).
• Tapping confirm with at least one selected target SHALL trigger the copy operation for all selected profiles; tapping cancel or dismissing SHALL abort without changes.
3. *Transactional Deep-Copy & Append Semantics (`REQ-UI-439.3`)*:
• In `TrackingViewsDatabaseManager.java` and `TrackingViewsRepository.kt`, copying a tab SHALL execute in an atomic SQLite transaction on a background thread (`Dispatchers.IO`), cloning the source tab's row in `VIEWS_TABLE` (with new sequential `LAYOUT_NR = max(LAYOUT_NR) + 1` per target sport type), all its sensor field rows in `ROWS_TABLE`, and all its section dividers in `SECTION_DIVIDERS_TABLE`.
• When the currently active `ActivityType` is among the targets, the system SHALL update the active tab list and auto-navigate to the newly appended tab.
4. *9-Language Localization & Accessibility Parity (`REQ-UI-439.4`)*:
• All new user-facing strings (dialog title, copy action button, content descriptions) SHALL maintain 100% parity across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-439.2` (*Target ActivityType Selection Dialog*), Parent Epic `ATT-355`. Targets `CopyTrackingTabDialog.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.1 commit `82bf905d` (ATT-5078) added the initial multi-select bottom sheet implementation with default `setOf(sourceActivityType)`.
3. *Root Reason for Existing Formulation*: Preselection was introduced as a placeholder default during initial bottom sheet authoring without considering cross-sport copying ergonomics, leading to accidental duplicate tabs in the active sport profile.
4. *Preservation of Core Invariants*: Multi-selection of any `ActivityType` (including the source profile) remains fully supported; deep copy and append semantics (`REQ-UI-439.3`) and 9-language localization (`REQ-UI-439.4`) remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in tracking tab configuration mode (`ScreenMode.CONFIGURATION`),
• *When* viewing Row 2 of `TrackingTabConfigHeader`,
• *Then* a Copy Tab icon button (`Icons.Default.ContentCopy`) is rendered with localized content description `cd_copy_tab`.
• *Given* the Copy Tab button is tapped,
• *When* the modal bottom sheet opens,
• *Then* all active `ActivityType` profiles are displayed with their authentic logos, localized titles, and multi-selection checkboxes, all unselected by default (`selectedTypes.isEmpty()`), and the Copy action button is disabled.
• *Given* the athlete selects one or more target profiles,
• *When* at least one checkbox is checked,
• *Then* the Copy action button becomes enabled.
• *Given* one or more target `ActivityType` profiles selected (including the source profile),
• *When* the athlete taps the Copy action,
• *Then* the source tab, all sensor field cells, and section dividers are deeply cloned and appended at the end of each selected profile's tab list.
• *Given* the currently active `ActivityType` profile is among the copy targets,
• *When* the copy completes,
• *Then* the tab bar refreshes and the pager automatically navigates to the newly appended tab.

**Invariants**: Atomic SQLite transaction, zero blocking of the main thread (`Dispatchers.IO`), 9-language localization parity, and 100% clean-room test pass rate.

### REQ-UI-440: Move Tracking Tab Left/Right [Verified]
- **Tests**: `TST-UI-440`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabConfigHeader.kt`, `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, `TrackingViewsRepository.kt`, `TrackingViewsDatabaseManager.java`
- **Rationale**: Athletes configuring custom tracking cockpits need the ability to reorganize/reorder tabs within an active sport profile (`ActivityType`) (e.g., moving the 2nd tab to the very end or shifting a map/telemetry tab forward/backward) without recreating or cloning tabs. Adding Move Left and Move Right controls in the configuration header, updating SQLite `LAYOUT_NR` atomically within the sport profile, and auto-navigating the `HorizontalPager` to follow the moved tab provides seamless WYSIWYG reordering (ATT-5079, extending `REQ-UI-103`, `REQ-UI-175`, and `REQ-UI-439`):
1. *Move Left and Move Right Header Actions (`REQ-UI-440.1`)*:
• In `TrackingTabConfigHeader.kt`, Row 2 SHALL provide a Move Left icon button (`Icons.AutoMirrored.Filled.ArrowBack`) and a Move Right icon button (`Icons.AutoMirrored.Filled.ArrowForward`) alongside `Add Before`, `Copy`, `Delete`, and `Add After`.
• The Move Left button SHALL be disabled when the tab is at the leftmost position (`tabIndex == 0`).
• The Move Right button SHALL be disabled when the tab is at the rightmost position (`tabIndex >= tabCount - 1`).
2. *Transactional SQLite Tab Reordering & Profile Isolation (`REQ-UI-440.2`)*:
• In `TrackingViewsDatabaseManager.java` and `TrackingViewsRepository.kt`, moving a tab left or right SHALL execute atomically inside a SQLite transaction on a background thread (`Dispatchers.IO`).
• Tab reordering SHALL be strictly scoped to the tab's `ActivityType`, swapping `LAYOUT_NR` with the adjacent neighbor and re-normalizing `LAYOUT_NR` (1..N) without affecting other sport profiles.
3. *Reactive Pager Focus & Repeatable Reordering (`REQ-UI-440.3`)*:
• Upon successful move in `TrackingTabsViewModel.kt`, the system SHALL emit a `TabNavigationEvent.NavigateTo(newIndex)` so the `HorizontalPager` automatically follows the moved tab, allowing the user to tap repeatedly to move a tab across multiple positions.
4. *9-Language Localization & Accessibility Parity (`REQ-UI-440.4`)*:
• All new user-facing action content descriptions (`cd_move_tab_left`, `cd_move_tab_right`) SHALL maintain 100% parity across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-440`, extending `REQ-UI-103` (Customizable Cockpits), `REQ-UI-175` (Tracking Tabs Configuration Transition), and `REQ-UI-439` (Copy Tracking Tab). Targets `TrackingTabConfigHeader.kt`, `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, `TrackingViewsRepository.kt`, and `TrackingViewsDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-1281`), Sprint 2026-40.6 (`ATT-1450`), and `ATT-5078` established customizable tracking tab headers and spatial WYSIWYG configuration with `LAYOUT_NR` order.
3. *Root Reason for Existing Formulation*: Tab order was originally fixed by creation sequence (`addEmptyTabView` before or after). Dynamic reordering controls were omitted in earlier iterations.
4. *Preservation of Core Invariants*: Existing tab database schema (`VIEWS_TABLE`, `ROWS_TABLE`, `SECTION_DIVIDERS_TABLE`), sensor field configurations, telemetry recording, and other `ActivityType` profiles MUST remain 100% unaltered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in tracking tab configuration mode (`ScreenMode.CONFIGURATION`),
• *When* viewing Row 2 of `TrackingTabConfigHeader`,
• *Then* Move Left (`ArrowBack`) and Move Right (`ArrowForward`) buttons are visible with localized content descriptions `cd_move_tab_left` and `cd_move_tab_right`.
• *Given* the active tab is at index 0 (first tab),
• *When* rendered,
• *Then* Move Left is disabled, and Move Right is enabled (if `tabCount > 1`).
• *Given* the active tab is at index `tabCount - 1` (last tab),
• *When* rendered,
• *Then* Move Right is disabled, and Move Left is enabled (if `tabCount > 1`).
• *Given* an active tab at intermediate index `0 < index < tabCount - 1`,
• *When* the athlete taps Move Left or Move Right,
• *Then* `LAYOUT_NR` is swapped atomically in SQLite within the active `ActivityType`, `trackingViews` updates, and `HorizontalPager` smoothly follows the tab to its `newIndex`.

**Invariants**: Atomic SQLite transaction, strict `ActivityType` isolation, 9-language localization parity, and 100% clean-room test pass rate.

### REQ-UI-441: Intrinsic Minimum Height Bounding for Standalone Sensor Field View Containers [Verified]
- **Tests**: `TST-UI-441`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt`
- **Rationale**: On the *Profil & Zonen* screen (`AthleteProfileScreen.kt`), `SensorFieldView` is embedded inside `SettingsScreenContent`'s vertically scrollable `Column(Modifier.verticalScroll(...))` across the *Lauf HF*, *Rad HF*, and *Rad Leistung* tabs as a live theme-aware preview tile. `SensorFieldView`'s internal layout contract (`REQ-UI-438`) applies `Card(Modifier.fillMaxWidth().fillMaxHeight())`, inner `Row(Modifier.fillMaxWidth().fillMaxHeight())`, and inner telemetry `Column(Modifier.fillMaxWidth().weight(1f))`. Under an unbounded scrollable container (`Constraints.Infinity`), `fillMaxHeight()` collapses to minimum height (0), causing the numeric telemetry value, unit label, zone gauge bar, and vertical indicator strips to be clipped or zero-height. Bounding the preview container via `Modifier.height(IntrinsicSize.Min)` guarantees full-height rendering of the card, centered telemetry readout, units, zone gauge bar, and vertical indicator strips without vertical collapse (ATT-5071, Parent Epic ATT-355, extending `REQ-UI-438`, Rule 67, and Design Guideline 5.14):
1. *Preview Container Height Bounding (`REQ-UI-441.1`)*:
• In `ZonesSettingsActivity.kt`, within `SettingsScreenContent`, the enclosing container (`Box` / `Surface`) wrapping `SensorFieldView` SHALL apply `Modifier.height(IntrinsicSize.Min)`.
2. *Full-Height Card and Telemetry Content Visibility (`REQ-UI-441.2`)*:
• When rendered inside the bounded preview container, `SensorFieldView` SHALL render at its minimum intrinsic height (~96 dp), fully displaying:
  a. Field header label (`"Heart Rate"` or `"Power"`) anchored at top;
  b. Centered telemetry numeric value and unit label (`"bpm"` / `"W"`);
  c. Centered or active zone gauge bar (`ZoneGaugeBar`) when enabled;
  d. Continuous vertical left/right indicator strip (`Spacer`) spanning 100% of card height flush with the borders.
3. *Scrollable Host Container Stability & Parity (`REQ-UI-441.3`)*:
• The enclosing `Column(Modifier.verticalScroll(scrollState))` in `ZonesSettingsActivity.kt` and `AthleteProfileScreen.kt` tabs (`Lauf HF`, `Rad HF`, `Rad Leistung`) SHALL remain smoothly scrollable with zero layout measurement exceptions or infinite constraint loops.
4. *Cockpit Grid Invariant Preservation (`REQ-UI-441.4`)*:
• The internal implementation of `SensorFieldView.kt` and multi-column equal-height stretching in `SensorGridScreen.kt` (`REQ-UI-438`) SHALL remain untouched and 100% preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-438` (*Vertical Card Height and Border Stretching in Cockpit Sensor Grid*) and targets `ZonesSettingsActivity.kt`.
2. *Historical Origin & Commit Trace*: `ATT-4878` (commit `12682cfaf66a224127dbd5eb014ed1970f0f38e2`) introduced `Modifier.fillMaxHeight()` and inner `.weight(1f)` in `SensorFieldView` to match the tallest sibling card in cockpit rows.
3. *Root Reason for Existing Formulation*: Equalizing row height in `SensorGridScreen` required `SensorFieldView` to fill maximum container height. However, the preview container in `ZonesSettingsActivity.kt` was not updated with `Modifier.height(IntrinsicSize.Min)`.
4. *Preservation of Core Invariants*: `SensorFieldView`'s internal layout contract for `REQ-UI-438` and `SensorGridScreen` equal-height behavior remain strictly untouched; only the caller container in `ZonesSettingsActivity.kt` is bounded.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing *Zonendarstellung -> Vorschau* on *Lauf HF*, *Rad HF*, or *Rad Leistung* in `AthleteProfileScreen`,
• *When* the preview tile is rendered inside the scrollable settings column,
• *Then* the preview sensor card displays the complete header label, numeric value, unit label, zone gauge bar, and vertical indicator strips without vertical clipping or collapse.
• *Given* a standalone `SensorFieldView` embedded inside an unconstrained scrollable column,
• *When* wrapped in a container with `Modifier.height(IntrinsicSize.Min)`,
• *Then* Compose measures the card's intrinsic minimum height and renders all internal elements with non-zero vertical bounds.

**Invariants**: Zero infinite height expansion, strict preservation of `IntrinsicSize.Min` on parent containers, zero regression on `SensorGridScreen.kt`, and 100% clean-room test pass rate.

### REQ-UI-442: Full-Bleed Badge Geometry, Large Uppercase Labels, Centered Cyclist & Top-Left Canonical Mini-Icons for Bluetooth LE Sensor Drawables [Verified]
- **Tests**: `TST-UI-442`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, `bt_run.xml`, `VectorAssetResolutionContractTest.kt`
- **Rationale**: When Bluetooth LE (`bt_*.xml`) sensor icons are rendered at compact UI dimensions (20dp–24dp, e.g. status bar, device pairing, sensor matrix rows), full-bleed badge geometry and bold uppercase labels establish visual parity with ANT+ icons (ATT-4956). ATT-5201 refines three visual and architectural aspects: (1) restores official Bluetooth brand color `#0A3D91` (`rgb(10, 61, 145)`) from master `Bluetooth.svg` and `logo_protocol_bluetooth.png` (eliminating ICC profile distorted `#07377F`); (2) centers the cyclist silhouette horizontally ($x \in [28.5, 115.5]$) across all 4 bike icons; (3) positions canonical domain mini-icons in the open top-left quadrant ($x \approx 12..52, y \approx 10..50$) above the rear wheel and behind the rider's sloping back, reusing scaled vector paths from `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml` (Rule 36):
1. *Full-Bleed Rounded Badge Geometry & Protocol Color Token (`REQ-UI-442.1`)*:
• In all 6 Bluetooth LE vector drawables (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, `bt_run.xml`), the background badge path SHALL be full-bleed spanning `0..144` with corner radius $r = 22.0$ (`M 22,0 H 122 A 22,22 0 0 1 144,22 V 122 A 22,22 0 0 1 122,144 H 22 A 22,22 0 0 1 0,122 V 22 A 22,22 0 0 1 22,0 Z`), replacing the historical 10-unit inset margin (`10..134`).
• The badge fill color SHALL strictly maintain the official Bluetooth badge color `#0A3D91` (`rgb(10, 61, 145)`), replacing the historical ICC-distorted `#07377F`.
2. *Large Bold Uppercase Bottom Typography (`REQ-UI-442.2`)*:
• All 6 BTLE vector drawables SHALL render bold uppercase abbreviation labels centered at the bottom of the badge, occupying approximately $16.7\%$ viewport height ($y \approx 113..136$ in `144x144` coordinate space):
  a. `bt_bike_pwr.xml`: `PWR`;
  b. `bt_bike_spd.xml`: `SPD`;
  c. `bt_bike_cad.xml`: `CAD`;
  d. `bt_bike_speed_and_cadence.xml`: `S&C`;
  e. `bt_hr.xml`: `HR`;
  f. `bt_run.xml`: `SPD`.
• Glyphs with internal counters (`P`, `R`, `D`, `A`, `&`) SHALL use `android:fillType="evenOdd"` for proper punch-out transparency.
3. *Centered Cyclist Motif & Top-Left Canonical Sensor Mini-Icons (`REQ-UI-442.3`)*:
• In bike icons (`bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`), the cyclist silhouette motif SHALL be centered horizontally across the badge ($x \in [28.5, 115.5]$ with balanced $28.5$ unit horizontal margins).
• Sensor mini-icons SHALL be positioned in the top-left quadrant ($x \approx 12..52, y \approx 10..50$) above the rear wheel and behind the cyclist's back, reusing and scaling canonical vector path geometry from `ic_power.xml`, `ic_speed.xml`, and `ic_cadence.xml` (Rule 36):
  a. `bt_bike_pwr.xml`: Canonical lightning bolt from `ic_power.xml`;
  b. `bt_bike_spd.xml`: Canonical speedometer dial / gauge arc from `ic_speed.xml`;
  c. `bt_bike_cad.xml`: Canonical crank arm with rotation loop from `ic_cadence.xml`;
  d. `bt_bike_speed_and_cadence.xml`: Combined dual-sensor motif reusing canonical speedometer arc (`ic_speed.xml`) and rotation loop (`ic_cadence.xml`).
• In `bt_hr.xml` and `bt_run.xml`, the main graphic motif (heart, runner) remains horizontally centered and scaled cleanly above the bottom `HR` and `SPD` text labels without overlap.
4. *Rule 37 Vector Geometry & Fidelity Governance (`REQ-UI-442.4`)*:
• All modified vector paths MUST adhere to Rule 37 constraints: pure analytical geometry (`M`, `L`, `H`, `V`, `C`, `A`, `Z`), $\le 45$ `L` commands per `<path>`, zero polygon auto-tracing approximations, and 100% clean parsing.
• Resource resolution in `UIHelper.java` and Compose screens SHALL remain 100% backward compatible without breaking changes to drawable resource identifiers.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-442` (*Full-Bleed Badge Geometry, Large Uppercase Labels & Top-Right Mini-Icons for Bluetooth LE Sensor Drawables*, `ATT-4956`). Targets `app/src/main/res/drawable/bt_*.xml`, `VectorAssetResolutionContractTest.kt`, and `docs/requirements/req_ui_design_system.md`.
2. *Historical Origin & Commit Trace*: `ATT-4956` (commit `be03a5bc14`, Sprint 2026-45.1) introduced full-bleed badges, uppercase labels, and top-right mini-icons with `#07377F`.
3. *Root Reason for Existing Formulation*: `#07377F` was sampled from ICC-profile distorted legacy PNGs rather than the master `Bluetooth.svg` (`#0A3D91`). Hand-drawn mini-icons were placed in the top-right without recognizing that the rider's forward lean creates ideal negative space in the top-left, and without reusing canonical `ic_power`, `ic_speed`, and `ic_cadence` vector assets.
4. *Preservation of Core Invariants*: Full-bleed badge geometry `0..144` ($r=22.0$), bold uppercase bottom typography with `evenOdd`, Rule 37 primitive limits ($\le 45$ `L` commands, analytical curves), and non-zero `R.drawable.bt_*` resource IDs are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* any of the 6 BTLE sensor vector drawables,
• *When* inspected or rendered in compact UI (20dp–24dp),
• *Then* the background badge spans the full viewport bounds `0..144` with corner radius $r=22.0$ and background color `#0A3D91`.
• *Given* an athlete viewing BTLE sensor icons in device pairing, status bar, or matrix screens,
• *When* reading the icon,
• *Then* the bold uppercase abbreviation (`PWR`, `SPD`, `CAD`, `S&C`, `HR`, `SPD`) is clearly legible at compact dimensions.
• *Given* the 4 BTLE bike sensor icons,
• *When* comparing `bt_bike_pwr`, `bt_bike_spd`, `bt_bike_cad`, and `bt_bike_speed_and_cadence`,
• *Then* the cyclist is horizontally centered ($x \in [28.5, 115.5]$) and each displays its unique canonical sensor mini-icon in the top-left quadrant ($x \approx 12..52, y \approx 10..50$) derived from `ic_power`, `ic_speed`, and `ic_cadence`.
• *Given* `VectorAssetResolutionContractTest`,
• *When* executing the test suite,
• *Then* all vector drawables pass Rule 37 geometric primitive checks, full-bleed badge assertions with `#0A3D91`, centered cyclist assertions, and uppercase label validation.

**Invariants**: Rule 37 geometric primitives, official `#0A3D91` Bluetooth badge token, `R.drawable.bt_*` resource ID mapping, and 100% clean-room test pass rate.

### REQ-UI-443: Power TelemetryMetricGraph Marginal Histogram Unfolded Linear Bins Projection Parity in Portrait and Landscape Modes [Verified]
- **Tests**: `TST-UI-443`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionModels.kt`, `TelemetryMetricGraph.kt`
- **Rationale**: In workout details and telemetry graphs (`TelemetryMetricGraph.kt`), when fine-grained frequency histogram mode (`ZoneCardDisplayMode.HISTOGRAM`) is active for Cycling Power, the marginal histogram docked to the right of the continuous power time-series curve SHALL project individual 10 W frequency bins across the continuous 1:1 vertical Y-axis (`[dataMin, dataMax]`) using unfolded linear bins (`TelemetryHistogramData.linearBins`) instead of stretching folded Sprint-Tail overflow bars (`overflowBin`) across the high-wattage range (ATT-5190, amending `REQ-UI-251` and `REQ-UI-431`, and complementing `REQ-UI-419` and `REQ-UI-420`):
1. *Unfolded Linear Bins Domain Helper (`REQ-UI-443.1`)*:
• In `ZoneDistributionModels.kt`, `TelemetryHistogramData` SHALL expose an accessor `val linearBins: List<TelemetryHistogramBin>` that resolves the full continuous sequence of frequency bins across `[dataMin, dataMax]`:
  - When `sprintTail == null`, returns `bins`.
  - When `sprintTail != null`, returns `bins.filterNot { it.isOverflow } + sprintTail.bins.filterNot { it.isReturn }`.
2. *Marginal Histogram Rendering in Portrait Mode (`REQ-UI-443.2`)*:
• In portrait orientation (`TelemetryMetricGraph.kt:482-510`), when `zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM`, the 25dp inline marginal strip SHALL iterate over `histogram.linearBins` instead of `histogram.bins`.
• `maxBinPct` SHALL be computed from `histogram.linearBins.maxOfOrNull { it.percentage }`.
• For each bin in `linearBins`, the background container track (`alpha = 0.15f`) SHALL be drawn unconditionally across the full plotted range `[dataMin, dataMax]`, while the solid foreground bar (`barWidthPx > 0f`) is gated on `bin.durationSec > 0L`, eliminating disruptive white horizontal stripes across high-wattage intervals (ATT-5281, Rule 69).
• Drawing routines SHALL filter out-of-domain bins (`bin.rangeMax <= dataMin || bin.rangeMin >= dataMax`) and clamp each bin's range to `[dataMin, dataMax]` before Y-mapping (`valueToY(...)`). Bins with clamped height $\le 0$ SHALL NOT be rendered with `.coerceAtLeast(1f)`, preventing stacking of $N$ translucent rectangles into a solid black bar at the top or bottom edges (ATT-5311, Rule 70).
3. *Aligned Marginal Zone Distribution in Landscape Mode (`REQ-UI-443.3`)*:
• In landscape orientation (`TelemetryMetricGraph.kt:938-976`, `AlignedMarginalZoneDistribution`), the marginal pane SHALL iterate over `histogram.linearBins` and compute `maxBinPct` across `linearBins`.
• For each bin in `linearBins`, the background container track (`alpha = 0.15f`) SHALL be drawn unconditionally across `[dataMin, dataMax]`, while the solid foreground bar (`barWidthPx > 0f`) and text overlays (`${bin.percentage.toInt()}%`) remain gated on `bin.durationSec > 0L` (ATT-5281, Rule 69).
• `AlignedMarginalZoneDistribution` SHALL filter out-of-domain bins (`bin.rangeMax <= dataMin || bin.rangeMin >= dataMax`) and clamp each bin's range to `[dataMin, dataMax]` before Y-mapping (`valueToY(...)`). Bins with clamped height $\le 0$ SHALL NOT be rendered with `.coerceAtLeast(1f)`, eliminating boundary stacking at the top or bottom edge (ATT-5311, Rule 70).
• Text overlays (`${bin.percentage.toInt()}%`) SHALL continue to be rendered for bins with `rectHeight >= 12.dp.toPx() && bin.durationSec > 0L`.
4. *Preservation of Standalone Histogram Sprint-Tail Folding & Invariants (`REQ-UI-443.4`)*:
• The standalone Power Histogram card on the *Zones* tab (`TelemetryHistogramChart.kt`) and `TelemetryHistogramCalculator.calculatePowerHistogram` SHALL remain 100% invariant, retaining the interactive Sprint-Tail overflow bar, P99 boundary snapping, and drill-down navigation per `REQ-UI-419`.
• Multi-chart plot alignment, horizontal 50dp start padding, 72%/28% landscape split, and touch gestures SHALL remain 100% preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-443.2` (Portrait) & `REQ-UI-443.3` (Landscape), amending `REQ-UI-251` (*Marginal Strip in Portrait*) and `REQ-UI-431` (*Marginal Pane in Landscape*); complements `REQ-UI-419` (*Sprint Tail Folding*) and `REQ-UI-420` (*Coggan 7-Zone Parity*). Targets `ZoneDistributionModels.kt` and `TelemetryMetricGraph.kt`.
2. *Historical Origin & Commit Trace*: Commit `c8192a0e` (`ATT-2147`, `REQ-UI-251`), commit `752d580f` (`ATT-4293`, `REQ-UI-419`), commit `dcb8f15f` (`ATT-2145`, `REQ-UI-431`), commit `9d44d0f5` (Sprint 2026-45.2, `ATT-5190`), and commit `9ce1ef68` (Sprint 2026-45.3, `ATT-5281`). Amending ticket `ATT-5311` (Sprint 2026-45.4).
3. *Root Reason for Existing Formulation*: Sprint-tail folding in `TelemetryHistogramCalculator.kt` was designed for standalone equal-column horizontal X-axis charts (`TelemetryHistogramChart.kt`) to avoid compressing the main training zone columns. Its side effect on marginal histograms docked to continuous 1:1 vertical Y-axes in `TelemetryMetricGraph.kt` was not anticipated, causing the single `overflowBin` spanning `[tailCutoff, dataMax]` to stretch across the entire high-power range as a giant black block. In `ATT-5190`, `if (bin.durationSec > 0L)` was added to suppress background tracks on empty bins to avoid visual clutter, which produced disruptive horizontal white stripes across non-contiguous Zone 7 power bursts. In `ATT-5281`, unconditional tracks were restored across `linearBins`. However, `linearBins` spans up to max sprint wattage (e.g. 800+ W), exceeding the graph's plotted Y-axis range `[dataMin, dataMax]`. For out-of-domain bins above `dataMax`, coordinates clamped to `topPaddingPx` and `.coerceAtLeast(1f)` forced 1px rects at `y = 0`, stacking $N$ translucent layers into a solid black horizontal bar along the top edge. In `ATT-5311`, domain boundary filtering and clamping `[rangeMin, rangeMax]` to `[dataMin, dataMax]` eliminates boundary stacking while preserving unconditional track continuity within `[dataMin, dataMax]`.
4. *Preservation of Core Invariants*: Standalone `TelemetryHistogramChart.kt` folding, interactive header navigation, Heart Rate linear histograms, multi-chart scrubbing alignment (`startPadding = 50.dp`), landscape 72%/28% split (`REQ-UI-431`), and clean-room test suite pass rate are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a cycling workout with power spikes triggering sprint-tail folding (`histogram.sprintTail != null`) and `ZoneCardDisplayMode.HISTOGRAM`,
• *When* viewing the Power time-series graph in `TelemetryMetricGraph` (both portrait and landscape),
• *Then* the marginal histogram renders individual 10 W bins at their true Y-axis watt positions up to `dataMax` without a giant solid black `overflowBin`.
• *Given* high-wattage power bins with `durationSec == 0L`,
• *When* the marginal histogram renders,
• *Then* the background container track (`alpha = 0.15f`) is drawn unconditionally for every bin in `linearBins` within `[dataMin, dataMax]`, while solid foreground frequency bars and text overlays remain suppressed.
• *Given* power bins extending above `dataMax` or below `dataMin`,
• *When* the marginal histogram renders in portrait or landscape mode,
• *Then* out-of-range bins are filtered/clamped so no black bar or stacked translucent rects appear at `y = 0` / top padding boundary.
• *Given* the same workout on the *Zones* tab (`TelemetryHistogramChart.kt`),
• *When* inspecting the standalone Power histogram,
• *Then* the interactive Sprint-Tail overflow bar and drill-down navigation continue to operate without regression.

**Invariants**: Standalone sprint-tail folding preserved, multi-chart horizontal alignment preserved, 100% clean-room test suite pass rate.

---

### REQ-UI-444: Android Adaptive Icon Support, 66dp Safe-Zone Padding & Brand Preservation [Verified]
- **Tests**: `TST-UI-444`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/drawable/ic_launcher_background.xml`, `res/drawable/ic_launcher_monochrome.xml`, `AndroidManifest.xml`
- **Rationale**: Modern Android launchers (API 26+) mask app icons into round, squircle, or teardrop shapes, clipping unpadded legacy static icons (`@drawable/logo`) and enclosing them in artificial white plates. REQ-UI-444 introduces a fully conformant Android Adaptive Icon architecture while preserving 100% of the authentic Cervélo P3C and classic stopwatch branding from `Bilder/logo.fig`:
1. *Adaptive Icon Layer Architecture (`REQ-UI-444.1`)*:
• The application SHALL provide standard adaptive icon configurations under `res/mipmap-anydpi-v26/` (`ic_launcher.xml` and `ic_launcher_round.xml`).
• The adaptive icon SHALL decouple visual representation into three distinct layers: `<background android:drawable="@drawable/ic_launcher_background" />`, `<foreground android:drawable="@drawable/ic_launcher_foreground" />`, and `<monochrome android:drawable="@drawable/ic_launcher_monochrome" />`.
• The background layer SHALL render a clean, neutral surface (`#FFFFFF`).
2. *Brand Geometry & Safe-Zone Fill (`REQ-UI-444.2`)*:
• The foreground vector drawable (`ic_launcher_foreground.xml`) SHALL be reconstructed directly from `Bilder/logo.fig` master geometry (vector depths 70 down to 50), preserving 100% of the authentic brand elements and colors:
  a. Cervélo P3C Time-Trial Bike: Red aero frame (`#FF0000`), black carbon aero wheels (`#000000`), and black saddle (`#000000`);
  b. Classic Multi-Sport Stopwatch: Golden bezel & stems (`#FFC700`), light dial (`#F0F0F0`), and classic 3 pushers (Left Navy Blue `#000090`, Center Red `#FF0000`, Right Green `#009000`) with overlapping button caps eliminating white gaps;
  c. Athletes: Royal blue cyclist and runner (`#0000FF`), reconstructing the cyclist back/hip via smooth cubic Bézier curves (`C`/`c`) with round caps covering the frame junction without angular kinks or protruding black saddle artifacts.
• All visual paths and elements within `ic_launcher_foreground.xml` SHALL be scaled and centered strictly within the balanced circular safe zone ($cx=54, cy=54, r \le 34.5\text{dp}$) on the standard 108dp canvas, guaranteeing zero clipping across Circle, Squircle, Rounded Square, and Teardrop launcher masks while filling the mask generously.
3. *Android 13+ Themed Icons Support (`REQ-UI-444.3`)*:
• The application SHALL provide `res/drawable/ic_launcher_monochrome.xml` defining a clean single-color vector silhouette (`#000000`) of the complete logo artwork.
• The monochrome silhouette SHALL dynamically adapt to the active Material You system palette on Android 13+ (API 33+) when Themed Icons are enabled.
4. *Manifest Integration, Master Asset Preservation & Caller Parity (`REQ-UI-444.4`)*:
• `AndroidManifest.xml` SHALL declare `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.
• Existing full-bleed bitmap assets `app/src/main/res/drawable/logo_512.png` and `images/logo_512.png` SHALL remain intact and match `drawable-xxxhdpi/logo_512.png` (`sha256: f84c625104d15bab...`), preserving unpadded in-app rendering (`AppNavigationDrawer.kt`).
• High-resolution 512x512 Play Store master icon asset with safe-zone padding SHALL be maintained separately (`images/play_store_icon_512.png`).
• Existing legacy bitmap `res/drawable/logo.png` SHALL remain untouched to preserve backward compatibility for existing in-app callers (notifications, protocol emblems).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android 8.0+ (API 26+) launcher with arbitrary mask shapes (circle, squircle, teardrop),
• *When* the user views the launcher home screen,
• *Then* the app icon renders without outer container padding plates and all athlete and bicycle elements fit completely inside the safe zone ($r \le 34.5\text{dp}$) without clipping.
• *Given* an Android 13+ (API 33+) launcher with Themed Icons enabled,
• *When* the user switches system wallpaper or dynamic color theme,
• *Then* the launcher icon adapts dynamically to the active palette using `ic_launcher_monochrome.xml`.
• *Given* in-app callers displaying the application branding in `AppNavigationDrawer`,
• *When* resolving `R.drawable.logo_512`,
• *Then* full-bleed unpadded asset renders without artificial margin plates.
• *Given* existing in-app callers referencing `R.drawable.logo`,
• *When* displaying notifications or protocol emblems,
• *Then* legacy drawable resolution functions with zero regressions.

**Invariants**: 100% Cervélo P3C and stopwatch color fidelity preserved, 100% containment within $r \le 34.5\text{dp}$ circular safe zone on 108dp canvas, zero regression in in-app notification drawables or drawer headers.

---

### REQ-UI-445: Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem, Navigation Drawer & Share Watermark [Verified]
- **Tests**: `TST-UI-445`, `TST-LOC-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LauncherIconManager.kt`, `SensorStatus.kt`, `AppNavigationDrawer.kt`, `SupporterComponents.kt`, `ShareUtils.kt`, `AndroidManifest.xml`, `res/mipmap-anydpi-v26/`, `res/drawable/`
- **Rationale**: To provide visible recognition for athletes supporting development via Supporter subscriptions (Bronze, Silver, Gold, Diamond) or recognized as Pioneer Early Adopters (ATT-51), the system SHALL introduce customizable homescreen launcher icons (Option A / Variant C), a live Cockpit status emblem, Navigation Drawer branding, full-color emblems in the Supporter screen, and a workout share watermark, while strictly preserving 100% of authentic Cervélo P3C and classic stopwatch colors (ATT-1482, ATT-5496):
1. *Variant C Adaptive Launcher Icons with Safe-Zone Compliance (`REQ-UI-445.1` / `ATT-5496`)*:
• The application SHALL provide 6 adaptive launcher icon variants (`STANDARD`, `PIONEER`, `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`) under `res/mipmap-anydpi-v26/` and `res/drawable/`.
• Every supporter launcher icon SHALL implement Variant C: built directly upon the corrected `ATT-1482` base vector (smooth cubic Béziers `C`/`c` for royal blue `#0000FF` cyclist back/hip at depth 50, black saddle `#000000` at depth 53 with zero protrusion, flush stopwatch pushers on thick `#FFC700` stems, safe zone $33.0 \le r \le 34.5\text{dp}$ on 108dp canvas) with a centered compact colored text stadium block flanked freely on the left and right by full-color tier emblems on the crisp `#FFFFFF` background:
  a. Standard: Authentic Cervélo P3C & classic stopwatch adaptive icon without badge overlays.
  b. Pioneer Edition: Emerald `PIONEER` text block flanked by emerald-and-gold Pioneer emblems on `#FFFFFF`, emerald border ring.
  c. Bronze Supporter: Bronze `SUPPORTER` text block flanked by Variant B5 Participant Rosettes on `#FFFFFF`, bronze border ring.
  d. Silver Pro Supporter: Silver `PRO SUPPORTER` text block flanked by Variant S4-A Single-Rim 3D Silver Coins on `#FFFFFF`, silver border ring.
  e. Gold Pro+ Supporter: Gold `PRO+ SUPPORTER` text block flanked by Variant G6 Gold Bar + Coin on `#FFFFFF`, gold border ring.
  f. Diamond Sponsor: Diamond-blue `SPONSOR` text block flanked by Faceted Brilliant-Cut Diamonds on `#FFFFFF`, cyan-blue border ring.
• All 6 launcher icon foreground drawables SHALL preserve 100% authentic Cervélo P3C bike (red frame `#FF0000`, carbon wheels/saddle `#000000`), classic stopwatch (golden bezel `#FFC700`, dial `#F0F0F0`, pushers `#000090`, `#FF0000`, `#009000`), and royal blue athletes (`#0000FF`).
• All vector paths across all 6 icons SHALL fit strictly inside the safe zone ($33.0 \le r \le 34.5\text{dp}$) on the 108dp canvas, guaranteeing zero clipping across Circle, Squircle, Rounded Square, and Teardrop launcher masks.
2. *Activity-Alias Manifest Architecture & Deferred Automatic Tier Synchronization (`REQ-UI-445.2`)*:
• `AndroidManifest.xml` SHALL declare 6 `<activity-alias>` entries targeting `MainActivityWithNavigation`, with the Standard alias enabled by default.
• The system SHALL provide `LauncherIconManager` observing `ProcessLifecycleOwner` lifecycle.
• `LauncherIconManager` SHALL automatically synchronize the homescreen launcher icon with `ProFeatureManager.effectiveTier` via `LauncherIconVariant.fromTier(effectiveTier)` (`FREE -> STANDARD`, `PIONEER -> PIONEER`, `BRONZE -> BRONZE`, `SILVER -> SILVER`, `GOLD -> GOLD`, `DIAMOND -> DIAMOND`) (ATT-5397). Manual icon selection and `SettingsDataStore.selectedLauncherIcon` are retired.
• The system SHALL apply `PackageManager.setComponentEnabledSetting` with `PackageManager.DONT_KILL_APP` deferred until the application transitions to the background (`onStop`) AND no active workout recording is running (`!TrainingApplication.isTracking()`). The app process or active workout SHALL NEVER be killed or restarted.
3. *Live Cockpit Status Emblem & Supporter Screen Emblems (`REQ-UI-445.3` / `ATT-5372`, `ATT-5397`, `ATT-5496`)*:
• In `SensorStatus.kt` (top sensor status bar of the Tracking Cockpit), the system SHALL render a pure 24dp vector emblem (B5 Participant Rosette, S4-A Silver Coins, G6 Gold Bar + Coin, Faceted Brilliant Diamond, or Pioneer Star — without text) at the right end of the row, symmetrically balancing the 24dp menu button on the left while keeping the 8 sensor icons centered.
• Tapping the emblem in `SensorStatus.kt` SHALL NOT open an icon picker dialog; it renders as a non-modal supporter status emblem (or navigates directly to `SupporterScreen`) (ATT-5397).
• `SupporterPlanCard` in `SupporterComponents.kt` SHALL display its corresponding full-color tier emblem (`44.dp`, `Color.Unspecified`) on the left, and `ActiveTierBanner` and `PioneerStatusBanner` SHALL display their full-color tier emblem (`36.dp`, `Color.Unspecified`) on the left (ATT-5496).
• `SupporterQuickIconPickerDialog.kt` and `supporter_quick_picker_*` string resources SHALL be retired and removed from the codebase.
4. *Navigation Drawer Header Recognition & 9-Locale Tier Naming (`REQ-UI-445.4` / `ATT-5496`)*:
• In `AppNavigationDrawer.DrawerHeader`, the system SHALL preserve the original clean single-line header layout (`46.dp` icon on the left + `"a Training Tracker"` title on the right over `menu_header_background`, without any text pill below the title) and render the active custom Cervélo P3C supporter icon matching `effectiveTier` directly (`foregroundRes` scaled by `1.81f` to compensate for the 108dp adaptive-icon safe-zone margin so its visual size matches `R.drawable.logo_512` at `46.dp` 1:1) without manual icon picking (ATT-5397).
• All 9 locales SHALL maintain consistent verbal progression: Bronze `SUPPORTER`, Silver `PRO SUPPORTER` (unhyphenated), Gold `PRO+ SUPPORTER` (with `+`), and Diamond `SPONSOR` across `supporter_tier_*`, `drawer_tier_*`, `launcher_icon_*`, and `garage_detector_dialog_message_gold` (ATT-5496).
5. *Social Share Workout Watermark (`REQ-UI-445.5`)*:
• In `ShareUtils.kt` (`combineWorkoutAndShare` and `combinePeriodAndShare`), the footer watermark signature bar SHALL render the mini Cervélo P3C icon, "Tracked with aTrainingTracker", and the user's active supporter tier pill (`★ GOLD PRO+`, `◆ SPONSOR`, `★ PIONEER`, etc.).
6. *Tier Unlock Hierarchy & Debug Override (`REQ-UI-445.6`)*:
• Higher supporter tiers SHALL automatically unlock all lower-tier launcher icons (and Pioneer early adopters additionally unlock Pioneer Edition).
• In `AdvancedSettings` when `BuildConfig.DEBUG == true`, the system SHALL expose a developer tier override selector (`AUTO`, `FREE`, `PIONEER`, `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`) allowing instant 1-tap testing of all 6 tiers and badges.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with an active Supporter tier or Pioneer status,
• *When* inspecting the Tracking Cockpit, Navigation Drawer, or shared workout image,
• *Then* the corresponding 24dp Cockpit emblem, Drawer header icon, and share watermark render accurately matching `effectiveTier`.
• *Given* an athlete whose `effectiveTier` updates (or on background `onStop`),
• *When* the application transitions to the background while not tracking (`!TrainingApplication.isTracking()`),
• *Then* `LauncherIconManager` automatically activates the corresponding `<activity-alias>` via `DONT_KILL_APP` without interrupting active tracking or killing the foreground process.
• *Given* any of the 6 launcher icon drawables,
• *When* rendered inside circular or squircle launcher masks,
• *Then* zero artwork paths clip outside the safe zone ($33.0 \le r \le 34.5\text{dp}$) and 100% of Cervélo P3C and stopwatch colors are preserved.
• *Given* an athlete tapping the Cockpit status emblem or navigation drawer header,
• *When* the interaction occurs,
• *Then* no icon picker dialog is opened.

**Invariants**: 100% Cervélo P3C and stopwatch color fidelity preserved, 100% safe-zone containment ($33.0 \le r \le 34.5\text{dp}$), zero interruption/restart during workout recording or foreground sessions, zero crash-prone mipmap Compose references, 100% free core workout tracking features.

### REQ-UI-446: Universal Stacked Workout Details Marginal Histograms & Widened Strip Layout Parity [Verified]
- **Tests**: `TST-UI-446`, `TST-LOC-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryMetricGraph.kt`, `ElevationProfile.kt`, `MapDetailLayout.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`
- **Rationale**: In workout details and map inspection views (`MapDetailLayout`, `WorkoutSummary`, `TrackOnMapScreen`), right-side marginal histograms were historically restricted to physiological metrics with zone models (Heart Rate and Power), leaving elevation profile, speed/pace, and W' balance with an unused right margin (empty 25dp strip in portrait; blank spacer in landscape). To create a unified visual analytics layout across the entire stacked telemetry family, utilize the Rule 64 right-edge alignment space on every chart, and provide athletes an immediate distribution of altitude bands, cruising speed/pace, and anaerobic W' balance depletion, the system SHALL render marginal histograms across all stacked graphs in graph/series color, widen the portrait margin to 52dp, and strictly maintain 1:1 horizontal plot-width alignment (`Rule 64`), unconditional background tracks (`Rule 69`), and domain-boundary clamping (`Rule 70`). Furthermore, the marginal altitude histogram next to `ElevationProfile` SHALL be restricted exclusively to Workout Details, suppressing histograms and right margin offsets in non-workout inspection views and list cards (ATT-5370, ATT-5486, ATT-5504, amending `REQ-UI-251`, `REQ-UI-431`, and `REQ-UI-433.4`):
1. *Universal Marginal Histogram Across All Stacked Workout Details Graphs (`REQ-UI-446.1`)*:
• The system SHALL render a right-side Y-axis-aligned marginal histogram on *all* stacked graphs in Workout Details and Map inspection views: `ElevationProfile`, `SPEED/PACE`, `HEART_RATE`, `POWER`, and `W_PRIME_BAL` (and any future stacked telemetry metrics).
• In `MapDetailLayout.kt`, `ElevationProfile` SHALL support an optional parameter `showElevationHistogram: Boolean = workoutData != null`. The system SHALL pass `alignWithTelemetryCharts = showElevationHistogram` to `ElevationProfile`, ensuring the marginal altitude histogram and widened 52dp margin render exclusively in Workout Details (`TrackOnMapScreen` and `WorkoutDetailTabbedContent`, where `workoutData != null`) and are strictly hidden in non-workout callers (`LiveSegmentSheet`, `SegmentOnMapScreen`, `ClimbOnMapScreen`, `RouteOnMapScreen`, `MapScreenWithTrack`, and `WorkoutClusterHeatmapScreen`, where `workoutData == null`).
• In `WorkoutSummary.kt`, the compact list card `ElevationProfile` SHALL set `alignWithTelemetryCharts = false`, strictly suppressing marginal altitude histograms in workout list feed cards.
• Non-stacked callers (`SensorGridScreen`, `LiveClimbSheet`, `ClimbDetailSheet`, `RouteItem`, `SegmentItem`) SHALL continue to default to `alignWithTelemetryCharts = false`, preserving clean boundary paddings without histograms.
2. *Series-Color Rendering for Non-Zoned Graphs (`REQ-UI-446.2`)*:
• For telemetry and elevation graphs without physiological training zones (`ElevationProfile`, `SPEED/PACE`, `W_PRIME_BAL`), the system SHALL compute uniform linear frequency bins (20–24 bins) across the plotted domain `[dataMin, dataMax]`.
• Non-zoned marginal histograms SHALL render in the graph's primary series color (Speed/Pace: `MaterialTheme.colorScheme.primary`; W'bal: `TTColor.WPrimeBalGreen`; Elevation: fixed `TTColor.Zone1` green base color across both portrait and landscape orientations (`AlignedMarginalZoneDistribution`), ensuring the marginal altitude frequency strip is visually decoupled from instantaneous start gradient and remains consistent regardless of initial slope or GPS elevation spikes).
• For each bin in the non-zoned histogram, the background container track (`alpha = 0.15f`) SHALL be drawn unconditionally across the plotted domain `[dataMin, dataMax]` (Rule 69), while the solid foreground bar (`barWidthPx > 0f`) is gated on `bin.durationSec > 0L` (or sample count > 0).
• Drawing routines SHALL filter out-of-domain bins and clamp bin boundaries to `[dataMin, dataMax]` before Y-mapping (`valueToY(...)`). Bins with clamped height $\le 0$ SHALL NOT be rendered with `.coerceAtLeast(1f)`, preventing out-of-domain rectangle stacking at `y = 0` (Rule 70).
• Physiological 5/7-zone coloring and thresholds SHALL be preserved for `HEART_RATE` and `POWER`.
3. *Widened Marginal Histogram Strip & 1:1 Plot Alignment (`REQ-UI-446.3`)*:
• In portrait orientation (`Configuration.ORIENTATION_PORTRAIT`), the right-side margin (`endPaddingPx`) SHALL be widened from `25.dp` to `52.dp` across all stacked charts (`TelemetryMetricGraph` and `ElevationProfile` with `alignWithTelemetryCharts = true`), yielding a bar strip width of ~44dp (`(endPaddingPx - 8.dp).coerceAtLeast(10.dp)`), providing clear legibility for frequency variations.
• In landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), all stacked charts SHALL adhere to the 72%/28% dual-pane split (`Modifier.weight(0.72f)` for the chart canvas and `Modifier.weight(0.28f)` for the marginal histogram pane), eliminating blank fallback spacers for non-zoned metrics and ensuring X-axis ticks and vertical scrubbing cursors align 1:1 horizontally across all sister charts (Rule 64).
• Both portrait and landscape plot areas SHALL maintain `startPadding = 50.dp` across all stacked charts.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-251` (Portrait Marginal Strip), `REQ-UI-431` (Landscape Marginal Pane), `REQ-UI-433.4` (Multi-Chart Alignment), and `REQ-UI-446.1`/`REQ-UI-446.2` (Universal Stacked Workout Details Marginal Histograms & Series-Color Rendering). Targets `TelemetryMetricGraph.kt`, `ElevationProfile.kt`, `MapDetailLayout.kt`, `WorkoutSummary.kt`, and `TrackOnMapScreen.kt`.
2. *Historical Origin & Commit Trace*: Commit `c8192a0e` (`ATT-2147`, `REQ-UI-251`), commit `dcb8f15f` (`ATT-2145`, `REQ-UI-431`), commit `56b230ec` (`ATT-4774`, `REQ-UI-433.4`), commits `9ce1ef68` / `cbb8317a` (`ATT-5281` / `ATT-5311`, Rules 69 & 70), commit `75485932` (`ATT-5370`, universal marginal histograms), commit `7ecad186` (`ATT-5486`, fixed Zone 1 green base color), and `ATT-5504` (restrict marginal altitude histogram exclusively to workout details).
3. *Root Reason for Existing Formulation*: Commit `75485932` (`ATT-5370`) introduced universal marginal histograms across stacked workout charts and hardcoded `alignWithTelemetryCharts = true` in `MapDetailLayout.kt:389` and `WorkoutSummary.kt:342`. Because `MapDetailLayout` is shared with non-workout inspection views (Live Segments, Climbs, Routes, Live Map, Clusters), this caused the altitude histogram and 52dp margin to leak into screens lacking telemetry charts. Parameterizing `showElevationHistogram: Boolean = workoutData != null` in `MapDetailLayout` and setting `alignWithTelemetryCharts = false` in `WorkoutSummary` confines the histogram strictly to detailed workout views.
4. *Preservation of Core Invariants*: 1:1 horizontal plot width (`startPadding = 50.dp`, `endPadding = 52.dp` in portrait; 72%/28% in landscape per Rule 64) and vertical scrubbing cursor alignment across all stacked charts in Workout Details; unconditional background tracks (Rule 69); domain-boundary clamping (Rule 70); clean full-width profiles for non-workout callers and list cards (`alignWithTelemetryCharts = false`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete inspecting non-workout map screens (`LiveSegmentSheet`, `SegmentOnMapScreen`, `ClimbOnMapScreen`, `RouteOnMapScreen`, `MapScreenWithTrack`, `WorkoutClusterHeatmapScreen`) or workout list cards (`WorkoutSummary`),
• *When* `ElevationProfile` renders,
• *Then* `alignWithTelemetryCharts` is `false`, rendering clean full-width terrain profiles without marginal altitude histograms or 52dp right-margin offsets.
• *Given* an athlete inspecting Workout Details (`TrackOnMapScreen`, `WorkoutDetailTabbedContent`),
• *When* `ElevationProfile` renders with `workoutData != null`,
• *Then* `alignWithTelemetryCharts` is `true`, rendering the marginal altitude histogram with 1:1 plot alignment.
• *Given* an athlete inspecting stacked workout details graphs in portrait orientation,
• *When* `ElevationProfile`, `Speed/Pace`, `Heart Rate`, `Power`, or `W'bal` render,
• *Then* all charts render a right-side marginal histogram of width ~44dp within a widened 52dp margin, with non-zoned charts rendered in their respective series color and HR/Power in physiological zone colors.
• *Given* an athlete inspecting stacked workout details graphs in landscape orientation,
• *When* rendered side-by-side,
• *Then* all stacked charts share identical 72%/28% dual-pane horizontal bounds, rendering the marginal histogram pane on the right without blank spacers, aligning X-axis bounds and scrubbing cursors 1:1.
• *Given* non-zoned marginal histograms (`ElevationProfile`, `Speed/Pace`, `W'bal`),
• *When* bins are drawn across `[dataMin, dataMax]`,
• *Then* background container tracks (`alpha = 0.15f`) are drawn unconditionally for all in-domain bins, foreground bars are gated on positive frequency, and out-of-domain bins are clamped and filtered so zero rectangles stack at `y = 0`.
• *Given* non-stacked callers of `ElevationProfile` (`SensorGridScreen`, `LiveClimbSheet`, `ClimbDetailSheet`, `RouteItem`, `SegmentItem`),
• *When* rendered,
• *Then* they default to `alignWithTelemetryCharts = false`, rendering clean profiles without marginal histograms.

**Invariants**: 1:1 horizontal plot width and scrubbing alignment (Rule 64), unconditional background tracks (Rule 69), domain-boundary clamping (Rule 70), 5/7-zone coloring preserved for HR/Power, 100% clean-room test suite pass rate.

### REQ-UI-447: Workout Share Mode Selection Bottom Sheet & Photo Track Overlay Snapshot Composer [Verified]
- **Tests**: `TST-UI-447`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `ShareUtils.kt`, `PhotoTrackOverlayComposer.kt`, `WorkoutShareBottomSheet.kt`, `LauncherIconVariant.kt`, `WorkoutHeader.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`
- **Rationale**: Currently, tapping the Share button in `MapDetailLayout` immediately executes a vertical composite map/graph snapshot (`combineWorkoutAndShare`). Athletes frequently prefer sharing activities using a personal scenery, selfie, or gear photo overlaid with their GPS route silhouette and key stats (Strava/Garmin style). REQ-UI-447 introduces a share mode selection sheet offering the classic analysis snapshot or a photo with track & stats overlay (ATT-30, ATT-5479, ATT-5472, ATT-5656, ATT-5809):
1. *Share Mode Selection Bottom Sheet & Persistent Header Trigger (`REQ-UI-447.1`)*:
• In `WorkoutHeader.kt`, the system SHALL expose an `onShareWorkout: (() -> Unit)? = null` callback and render a uniform `IconButton` (`size = 32.dp`, `Icons.Default.Share`, `tint = MaterialTheme.colorScheme.primary`, `contentDescription = stringResource(R.string.share)`) adjacent to `onEditWorkout` across both portrait and landscape orientations.
• In `WorkoutHeader.kt` portrait mode, the top action button spacer SHALL dynamically expand to ~104dp when both edit and share actions are present (`hasEdit && hasShare`), preventing title text collision and truncation.
• In `WorkoutSummary.kt` (workout list card) and `WorkoutDetailTabbedContent.kt` (persistent aftermath header), `WorkoutHeader` SHALL wire `onShareWorkout` to open `WorkoutShareBottomSheet` pre-populated with `attachedPhotos`.
• In `WorkoutSummary.kt`, selecting Option A (*Karten- & Analyse-Snapshot*) SHALL navigate to map details (`onMapClick()`). In `WorkoutDetailTabbedContent.kt`, selecting Option A SHALL supply the `WorkoutHeader` composable into `MapDetailLayout` `header` parameter (capturing it in `headerLayer` snapshot output) and initiate snapshot generation.
• In `MapDetailLayout.kt`, the floating share button on the map canvas SHALL be suppressed when `workoutData != null` in multi-tab details (where `WorkoutHeader` provides the permanent share trigger), while remaining active for standalone route/segment maps (`workoutData == null`).
• Tapping the floating Share button on a completed workout (or tapping the header share button) SHALL display `WorkoutShareBottomSheet` (hosted in `AppModalBottomSheet`) presenting two choices:
  - *Option A — Karten- & Analyse-Snapshot*: Executes the vertical composite snapshot (`combineWorkoutAndShare`).
  - *Option B — Foto mit Track-Overlay*: Launches the 3-tier photo cascade.
• For non-workout callers (`RouteOnMapScreen`, `SegmentOnMapScreen`, or when `workoutData == null`), tapping Share SHALL continue executing Option A directly without presenting the mode sheet.
2. *2D GPS Track Silhouette Projection & Portrait Constraint (`REQ-UI-447.2`)*:
• In `PhotoTrackOverlayComposer.kt`, the workout's GPS polyline (`workoutData.mapPolyline`) SHALL be decoded via `PolyUtil.decode` and projected onto the photo.
• Projection SHALL apply equirectangular cosine-latitude scaling ($\Delta x = \Delta\lambda \cdot \cos(\phi_{\text{mean}})$, $\Delta y = -\Delta\phi$) to preserve true geometric aspect ratio across all latitudes without distortion. Trackpoints with invalid or Null-Island coordinates (`!isValidGpsCoordinate(lat, lng)`) SHALL be filtered out prior to bounding box and silhouette calculation to prevent track silhouette distortion.
• On portrait photos (`height > width`), the track bounding box SHALL be constrained to the upper-right quadrant ($\le 22\%$ photo height), ensuring the silhouette remains compact and unobtrusive.
• The track silhouette SHALL be drawn with a subtle drop shadow, anti-aliased stroke, and distinctive start/finish marker dots.
3. *Bottom Gradient Scrim, Key Metrics Typography & Compact Anchoring (`REQ-UI-447.3`)*:
• A vertical translucent gradient scrim (transparent $\rightarrow$ black alpha 0.85) SHALL be drawn across the bottom 25–30% of the photo canvas.
• Workout title, date, distance, duration, ascent, and average speed/power SHALL be rendered on the scrim in high-contrast white typography, guaranteeing WCAG AAA contrast ratio ($\ge 7.0:1$) over arbitrary bright or dark backgrounds.
• In `PhotoTrackOverlayComposer.kt`, workout title (scaled to ~48f), date (~26f), and key metrics (~36f) SHALL have increased prominence and anchor compactly directly above `footerTop` (`footerTop - 24f * scaleRef`), eliminating portrait vertical dead space.
4. *Official Branding & Supporter Emblem Watermark (`REQ-UI-447.4`)*:
• The bottom footer watermark bar in `ShareUtils.kt` SHALL render the tier-matched launcher icon (`LauncherIconVariant.fromTier`), "Tracked with aTrainingTracker" (`R.string.share_watermark_tracked_with`), and only the standalone supporter vector emblem (`ic_supporter_emblem_*`) when `effectiveTier.isPro`, omitting wide text pill badges (`REQ-UI-445.5`).
• The footer text SHALL auto-scale dynamically if required to fit cleanly between the launcher icon and right emblem without text collision or truncation.
5. *Interactive Preview, Stacked Sharing CTAs & 9-Language Parity (`REQ-UI-447.5`)*:
• After photo selection, `WorkoutShareBottomSheet` SHALL display an in-sheet preview of the composed photo with full-width stacked action buttons per Rule 73 (primary "Teilen" `Button` above secondary "Foto auswählen" `OutlinedButton` with 8dp spacing).
• All user-visible strings SHALL maintain 100% localization parity across all 9 supported application locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
6. *3-Tier Photo Selection Cascade (`REQ-UI-447.6`)*:
• In `WorkoutShareBottomSheet.kt`, selecting Option B ("Foto mit Track & Daten") or tapping "Foto auswählen" in the composed preview SHALL present a 3-tier photo selection cascade:
  - *Tier 1 — Attached Photos*: When `attachedPhotos.isNotEmpty()`, the sheet SHALL display a thumbnail grid of attached photos with subtitle `share_photos_attached_subtitle`. Stacked full-width buttons below SHALL provide options to expand selection: secondary CTA `share_photos_show_time_window` (switching to Tier 2) and tertiary CTA `workout_photos_show_all` (switching to Tier 3). Tapping any thumbnail SHALL select the photo and advance to the composed preview.
  - *Tier 2 — Workout Time-Window Photos*: When `attachedPhotos.isEmpty()`, or when the athlete taps `share_photos_show_time_window`, the system SHALL query photos captured during the workout time window (`[startTime - 5m .. endTime + 5m]`) via `WorkoutPhotoManager.queryWorkoutTimePhotos`. If matching photos exist, the sheet SHALL display a thumbnail grid with timestamps (`HH:mm`) and subtitle `workout_photos_time_picker_subtitle`. Stacked full-width buttons below SHALL provide `workout_photos_show_all` (Tier 3), and if `attachedPhotos.isNotEmpty()`, `share_photos_show_attached` (returning to Tier 1). Tapping any thumbnail SHALL select the photo and advance to the composed preview.
  - *Tier 3 — All Photos (System Picker)*: When both Tier 1 and Tier 2 contain zero photos, or when the athlete taps `workout_photos_show_all`, the system SHALL launch Android's system photo picker (`ActivityResultContracts.PickVisualMedia(ImageOnly)`).
• In the composed preview, tapping "Foto auswählen" (`share_choose_photo`) SHALL re-enter the 3-tier cascade at Tier 1 if `attachedPhotos.isNotEmpty()`, else Tier 2 if time-window photos exist, else Tier 3.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-447` (amended by `ATT-5479`, `ATT-5472`, `ATT-5656`, and `ATT-5809`), extending `REQ-UI-177` (*Theme-Aware Post-Workout & Period Share Snapshot Canvas and Footer*), `REQ-UI-205` (*Workout Snapshot Layout*), `REQ-UI-255` (*Header & Primary Metrics Top Identity Anchor*), `REQ-UI-408` (*Multi-Tab Workout Details*), and `REQ-UI-445.5` (*Social Share Workout Watermark*). Targets `WorkoutHeader.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, `ShareUtils.kt`, `PhotoTrackOverlayComposer.kt`, `WorkoutShareBottomSheet.kt`, and `LauncherIconVariant.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (`ATT-1472`, `REQ-UI-177`), Sprint 2026-40.7 (`ATT-1393`, `REQ-UI-205`), Sprint 2026-42.8 (`ATT-3920`, `REQ-UI-408`), `ATT-5397` (`REQ-UI-445.5`), `ATT-30` (`584284ff`), `ATT-5496` (`e5f5c754`), `ATT-5472`, Sprint 2026-45.6 (`ATT-5475`, `c82c8248`), Sprint 2026-45.9 (`ATT-5656`), and Sprint 2026-45.12 (`ATT-5809`).
3. *Root Reason for Existing Formulation*: Prior to ATT-30, snapshot sharing only supported stitching layout bitmaps. Prior to ATT-5809, sharing was only initiated from the floating button inside the map container (`MapDetailLayout`) or via attached photos in `WorkoutPhotoPreviewBottomSheet`. Multi-tab details (`ATT-3920`) defaulted to Tab 1 without a map, hiding the floating button, and passed `header = {}` to `MapDetailLayout`, omitting the header from Option A snapshots.
4. *Preservation of Core Invariants*: Existing `combineWorkoutAndShare` and `combineAndShare` functions and non-workout callers (`RouteOnMapScreen`, `SegmentOnMapScreen`) remain 100% operational under Option A with map-based floating share button; `WorkoutHeader` export menu (FIT/TCX/GPX/CSV/GC), Strava upload, and edit buttons remain intact; safe bitmap decoding (`inSampleSize`) avoids OOM; touch targets >= 32dp; WCAG AAA text contrast; Rule 73 stacked buttons; and 9-language localization parity are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a completed workout in `WorkoutSummary` (workout list) or `WorkoutDetailTabbedContent` (workout details),
• *When* the athlete taps the share button in `WorkoutHeader`,
• *Then* `WorkoutShareBottomSheet` appears displaying Option A (Classic Analysis Snapshot) and Option B (Photo with Track-Overlay).
• *Given* an athlete in `WorkoutSummary` selecting Option A,
• *When* tapped,
• *Then* the system navigates to map details to generate the full composite snapshot.
• *Given* an athlete in `WorkoutDetailTabbedContent` selecting Option A from any tab,
• *When* triggered,
• *Then* `combineWorkoutAndShare` captures Header (from `headerLayer`), Map, Elevation, Analytics, and Footer and dispatches via `FileProvider`.
• *Given* an athlete viewing `MapDetailLayout` in multi-tab workout details (`workoutData != null`),
• *When* the screen renders,
• *Then* the floating share button on the map canvas is hidden, while remaining visible for non-workout maps (`workoutData == null`).
• *Given* the athlete selects Option B in `WorkoutShareBottomSheet`,
• *When* photos are attached to the workout (`attachedPhotos.isNotEmpty()`),
• *Then* Tier 1 (Attached Photos) is displayed with options to switch to Tier 2 (Time-Window) or Tier 3 (All Photos).
• *Given* the athlete selects Option B with zero attached photos, or chooses to view time-window photos,
• *When* photos were taken during the workout time window,
• *Then* Tier 2 (Time-Window Photos) is displayed with an option to switch to Tier 3 (All Photos).
• *Given* zero attached and zero time-window photos, or the athlete chooses "Show All Photos",
• *When* triggered,
• *Then* the system photo picker (`PickVisualMedia`) is launched.
• *Given* a photo selected from any tier,
• *When* the photo is decoded and composited,
• *Then* `PhotoTrackOverlayComposer` presents the composed preview, and clicking "Foto auswählen" returns to the 3-tier cascade.

**Invariants**: Backward compatibility for all existing `combineWorkoutAndShare` callers, safe bitmap downsampling preventing OOM, WCAG AAA text contrast, 9-language localization parity, and 100% clean-room test pass rate.

### REQ-UI-448: Workout Photos Strip, Full-Screen Preview Bottom Sheet with ATT-30 Overlay Share, Map Pin Markers, and Null-Island Coordinate Rejection [Verified]
- **Tests**: `TST-UI-448`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetails.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `ATrainingTrackerMap.kt`, `WorkoutPhotoViewerSheet.kt`, `WorkoutPhotoPreviewBottomSheet.kt`, `WorkoutShareBottomSheet.kt`, `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `WorkoutPhotoManager.kt`, `WorkoutRepository.kt`, `MapBehaviors.kt`
- **Rationale**: Athletes capture photos during workouts and wish to browse them alongside workout telemetry, inspect them on the workout GPS map, and share them via social media. REQ-UI-448 defines the UI presentation and interaction contracts for workout photos, EXIF coordinate validation, and camera bounds protection (ATT-43, ATT-5479, ATT-5473, ATT-5811, extending `REQ-UI-447` and `REQ-UI-408`):
1. *Workout Photos Horizontal Strip & Neutral Summary Styling (`REQ-UI-448.1`)*:
• In `WorkoutDetails.kt` (and `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`), when attached photos exist or `showAddButton == true`, the system SHALL render a dedicated horizontal photo strip section.
• In `WorkoutPhotoStrip.kt`, the header camera icon (`Icons.Default.PhotoCamera`) and label tint SHALL use neutral `MaterialTheme.colorScheme.onSurfaceVariant` instead of electric blue (`colorScheme.primary`), establishing visual harmony with neighboring summary telemetry rows.
• In `WorkoutSummary.kt`, a standard `HorizontalDivider` (`colorScheme.outlineVariant`, 0.5.dp thickness, 16.dp horizontal padding) SHALL precede the `WorkoutPhotoStrip` section.
• In `WorkoutPhotoStrip.kt`, the "Add Photos" action SHALL be rendered in the section header row (as a compact `IconButton` with `Icons.Default.AddPhotoAlternate` and neutral `onSurfaceVariant` tinting, `contentDescription = stringResource(R.string.workout_photos_add)`) when `showAddButton == true`; the `LazyRow` SHALL be rendered only when `photos.isNotEmpty()` and SHALL display photo thumbnails starting from index 0 without an oversized empty tile.
• If `photos.isEmpty()` and `showAddButton == true`, `WorkoutPhotoStrip` SHALL render only the compact single-line header row.
2. *Full-Screen Photo Viewer Bottom Sheet (`REQ-UI-448.2`)*:
• Tapping a photo thumbnail SHALL open `WorkoutPhotoViewerSheet` / `WorkoutPhotoPreviewBottomSheet` (hosted in `AppModalBottomSheet` per ATT-5399), displaying the full image, capture timestamp, caption (if present), an action to delete the photo (with confirmation dialog), and a prominent CTA to share the photo via `WorkoutShareBottomSheet` (ATT-30).
• Per Rule 73, action buttons inside `WorkoutPhotoPreviewBottomSheet.kt` SHALL be arranged as vertically stacked full-width buttons (`fillMaxWidth()`) with 8dp vertical spacing (primary "Mit Track-Overlay teilen" `Button` stacked above secondary "Foto löschen" `OutlinedButton`), eliminating horizontal text squishing in localized languages.
3. *Map Photo Pin Markers & Strict Coordinate Validation (`REQ-UI-448.3`)*:
• In `TrackOnMapScreen.kt` and `ATrainingTrackerMap.kt`, photos with valid geographic GPS coordinates SHALL be rendered as distinct photo pin markers (`LocationMarker`) along the workout route.
• Coordinate validity SHALL be evaluated via `GeoUtils.isValidGpsCoordinate(latitude, longitude)`, requiring finite latitude in `[-90.0, 90.0]`, finite longitude in `[-180.0, 180.0]`, and strictly excluding Null Island (`abs(lat) < 1e-5 && abs(lng) < 1e-5`).
• In `WorkoutRepository.getWorkoutMarkers`, photos missing coordinates or holding invalid/Null-Island coordinates (`!isValidGpsCoordinate`) SHALL be excluded from the marker list and never rendered on the map.
• In `MapBoundsController` (`MapBehaviors.kt`), invalid coordinates (`!isValidGpsCoordinate`) SHALL be excluded from `LatLngBounds.Builder`, ensuring map camera bounds never expand to continental zoom levels.
• Tapping a valid photo marker on the map SHALL trigger `WorkoutPhotoViewerSheet` for that photo.
4. *EXIF Null-Island Rejection & Database Self-Healing (`REQ-UI-448.4`)*:
• In `WorkoutPhotoManager.extractExifMetadata`, when `ExifInterface.getLatLong()` returns true for zeroed EXIF rational tags (`0.0, 0.0`), the system SHALL treat latitude and longitude as `null` (`!isValidGpsCoordinate`), enabling fallback timestamp-based trackpoint interpolation (`interpolateCoordinatesAtTimestamp`) against `WorkoutSamples.db` to execute.
• When loading photos for a workout in `WorkoutPhotoManager` / `WorkoutRepository`, existing database records in `workout_photos` holding `(0.0, 0.0)` coordinates SHALL be self-healed: the system SHALL re-interpolate valid coordinates from `WorkoutSamples.db` using `taken_at_ms` (or reset to `null` if interpolation fails) and persist the repaired coordinates back to SQLite via `WorkoutSummariesDatabaseManager.updatePhotoCoordinates`.
5. *Memory Safety & 9-Language Localization Parity (`REQ-UI-448.5`)*:
• Thumbnail and preview decoding SHALL use downsampled bitmap decoders (`inSampleSize`) via `PhotoTrackOverlayComposer.decodeSampledBitmap` to prevent out-of-memory exceptions.
• All user-visible labels, headers, dialogs, and accessibility descriptions SHALL maintain 100% parity across all 9 supported locales without decorative inline Unicode emojis.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-448` (amended by `ATT-5479`, `ATT-5473`, and `ATT-5811`), extending `REQ-UI-447` (*Workout Share Mode Selection & Photo Track Overlay*), `REQ-UI-408` (*Multi-Tab Workout Details*), and `REQ-DAT-038` (*Workout Photo Storage & EXIF Interpolation*). Targets `WorkoutDetails.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `ATrainingTrackerMap.kt`, `WorkoutPhotoViewerSheet.kt`, `WorkoutPhotoPreviewBottomSheet.kt`, `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `WorkoutPhotoManager.kt`, `WorkoutRepository.kt`, and `MapBehaviors.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.6 (`ATT-30`, `REQ-UI-447`; `ATT-43`, commit `5c9d9653`), Sprint 2026-44.1 (`ATT-3920`, `REQ-UI-408`), Sprint 2026-45.8 (`ATT-5474`, commit `8aed20fc`; `ATT-5473`), Sprint 2026-45.12 (`ATT-5811`).
3. *Root Reason for Existing Formulation*: Prior to ATT-43, `WorkoutDetails` only rendered tabular telemetry metrics. ATT-43 and ATT-5474 introduced the "Add Photos" action as an `OutlinedCard` square tile inside the `LazyRow` carousel. Following responsive thumbnail scaling in ATT-5655 (`MEDIUM` ~175dp, `LARGE` ~360dp), this tile became an oversized empty box that wasted screen height and displaced photos.
4. *Preservation of Core Invariants*: Relocating the "Add Photos" trigger into the compact header row preserves full photo-adding functionality (`REQ-UI-455`), neutral color styling (`onSurfaceVariant`), accessibility descriptions, and downsampling/preview behavior while eliminating layout bloat and unblocking photo visibility.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout with attached photos in `WorkoutDetails`,
• *When* the details screen renders,
• *Then* a horizontal photo strip appears displaying thumbnails and an add-photos button.
• *Given* an attached photo with zeroed EXIF GPS tags (`0.0, 0.0`),
• *When* processed by `WorkoutPhotoManager`,
• *Then* Null Island coordinates are rejected as null, triggering fallback trackpoint interpolation based on `taken_at_ms`.
• *Given* an existing photo record in SQLite `workout_photos` with `(0.0, 0.0)` coordinates,
• *When* loaded in `WorkoutPhotoManager` / `WorkoutRepository`,
• *Then* the record is self-healed in-place with interpolated coordinates or set to null.
• *Given* an athlete viewing a workout map,
• *When* `WorkoutRepository.getWorkoutMarkers` and `MapBoundsController` compute markers and bounds,
• *Then* invalid coordinates (`!isValidGpsCoordinate`) are excluded from marker generation and camera bounding box calculations, preserving tight map framing on the workout track.
• *Given* the athlete taps a photo in the strip or a photo pin on the map,
• *When* clicked,
• *Then* `WorkoutPhotoViewerSheet` opens displaying the full preview, caption, delete action, and "Share with Track Overlay" CTA.
• *Given* the athlete taps "Share with Track Overlay" in the viewer sheet,
• *When* triggered,
• *Then* `WorkoutShareBottomSheet` (ATT-30) opens pre-seeded with the selected photo.

**Invariants**: Bottom popup standard via `AppModalBottomSheet`, safe downsampled bitmap decoding, 9-language localization parity without inline emojis, 100% clean-room test pass rate.

### REQ-UI-449: Interactive Map Lap Creation & Splitting in Workout Aftermath [Verified]
- **Tests**: `TST-UI-449`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `CreateLapOnMapSheet.kt`, `LapEditorMath.kt`, `PathPreviewMap.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, `WorkoutLaps.kt`
- **Rationale**: When an athlete forgets to trigger a lap split during an activity, the system SHALL allow retroactively creating and splitting a lap at any selected sample point along the recorded GPS polyline in Aftermath (`TrackOnMapScreen` / `WorkoutDetails`), calculating accurate split metrics and updating both telemetry sample records and lap summaries in an atomic SQLite transaction without altering total session metrics (ATT-907, ATT-5480):
- **Description**:
  1. *Entry Points & Scrub Pre-Seeding (`REQ-UI-449.1`)*: In `WorkoutLaps.kt` / `LapSplitVisualizerCard`, `MapDetailLayout.kt` (scrubbing overlay action bar), and `CreateLapOnMapSheet.kt`, the system SHALL provide an interactive "Create / Split Lap on Map" (*Runde auf Karte teilen*) action using dedicated scissors vector drawable `R.drawable.ic_lap_split_scissors`. When triggered from an active map or chart scrubbing position, the selected split position SHALL be pre-seeded to the current scrub point index.
  2. *Interactive Split Bottom Sheet & Canonical Blue Polyline (`REQ-UI-449.2`, `CreateLapOnMapSheet.kt`)*: The editor SHALL be hosted in `AppModalBottomSheet` (per `ATT-5399`), rendering:
     - The complete workout GPS track instantiated via `MapTrack` with `type = TrackType.BEST` (rendering canonical blue `TTColor.TrackBest` polyline) with existing lap boundary pins.
     - A movable "New Lap Split Pin" draggable along the route or selectable via a precision sample/distance slider, rendered in `PathPreviewMap` via parameterized `apexIconRes = R.drawable.ic_lap_split_scissors` (dedicated scissors icon instead of the Max Line Distance ruler icon `R.drawable.ic_distance`).
     - Live metric preview cards for both resulting split laps (Lap $k$ before split and Lap $k+1$ after split: active duration, distance, and average speed/pace calculated via `LapEditorMath`).
     - Split Boundary Validation: The split index $s$ SHALL be strictly constrained between the start and end of the target lap ($start_k < s \le end_k$) so neither resulting split lap has duration $\le 0$ or distance $\le 0.0$ (enforcing zero phantom laps per `REQ-TRK-002`).
  3. *Atomic Multi-Database Transaction (`REQ-UI-449.3`, `WorkoutRepository.splitLapAtSample`)*:
     - In `WorkoutSamples.db`, `WorkoutSamplesDatabaseManager.splitLapAtSampleRow` SHALL execute an atomic update incrementing `LAP_NR = LAP_NR + 1` for all samples with `_id >= splitRowId` in the workout table.
     - In `Laps.db`, updated lap summaries for Lap $k$ and Lap $k+1$ (plus subsequent renumbered laps) SHALL be recalculated and persisted via `LapsDatabaseManager.replaceCroppedLaps` in a single SQLite transaction.
     - Export caches SHALL be invalidated and reactive `WorkoutData` emitted so `WorkoutLaps`, `LapSplitVisualizerCard`, and map markers refresh immediately without reloading the screen.
  4. *Synergistic Foundation for ATT-908 & ATT-5480 (`REQ-UI-449.4`)*: Domain calculations (`LapEditorMath.calculateSplitLapsPreview`), `PathPreviewMap` parameterization (`apexIconRes`), and sheet components SHALL be architected synergistically with boundary moving and map visualization standards.
  5. *9-Language Localization Parity (`REQ-UI-449.5`)*: All user-facing strings SHALL maintain 100% parity across all 9 supported locales without inline decorative Unicode emojis.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-449` (amended by `ATT-5480`), extending `REQ-TRK-002` (Lap lifecycle & phantom lap elimination), `REQ-UI-368` (Workout cropping & sample pruning), and `REQ-UI-204` (Lap split visualizer). Targets `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `CreateLapOnMapSheet.kt`, `PathPreviewMap.kt`, and lap action buttons.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.6 (`ATT-907`, commit `f1663727`, map lap creation), Sprint 2026-44.1 (`ATT-896`, zero phantom laps), Sprint 2026-42.3 (`ATT-3274`, transactional sample pruning), and Sprint 2026-45.7 (`ATT-5480`).
3. *Root Reason for Existing Formulation*: Laps were historically recorded exclusively during live tracking to avoid post-hoc data desynchronization between samples and lap summaries. In ATT-907, `CreateLapOnMapSheet` initially reused `TrackType.GPS` (green) and `PathPreviewMap`'s hardcoded `ic_distance` (ruler) apex marker from route analysis. Amending introduces canonical blue `TrackType.BEST` and dedicated scissors iconography (`ic_lap_split_scissors`).
4. *Preservation of Core Invariants*: Resulting laps MUST NOT be phantom (duration $> 0$ or distance $> 0.0$); sequential $1 \dots K$ lap numbering strictly maintained; total workout summary metrics, polyline, and extrema remain immutable; `PathPreviewMap` backward compatibility preserved via default `apexIconRes = R.drawable.ic_distance`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout with recorded samples in `TrackOnMapScreen` or `WorkoutDetails`,
• *When* the athlete triggers "Create Lap on Map" (or scrubs to a point and taps the split lap action),
• *Then* `CreateLapOnMapSheet` opens with the canonical blue track map (`TrackType.BEST`), existing lap markers, the movable split pin displaying the dedicated scissors icon (`ic_lap_split_scissors`), and live preview metrics for Lap $k$ and Lap $k+1$.
• *When* the split pin or slider is adjusted,
• *Then* the preview metrics for Lap $k$ and Lap $k+1$ update reactively in real time.
• *When* the athlete confirms the split,
• *Then* `WorkoutRepository.splitLapAtSample` executes atomically, `LAP_NR` is incremented for subsequent samples in `WorkoutSamples.db`, `Laps.db` is updated via `replaceCroppedLaps`, and the UI reflects the new lap split immediately.
• *Given* an attempt to place a split pin at an invalid boundary ($s \le start_k$ or $s > end_k$),
• *Then* confirmation is disabled and the boundary is clamped.
• *Given* lap creation and split entry buttons across `WorkoutLaps`, `LapSplitVisualizer`, and `MapDetailLayout`,
• *When* rendered,
• *Then* each action button displays the dedicated scissors vector drawable `R.drawable.ic_lap_split_scissors`.

**Invariants**: Zero phantom laps (`REQ-TRK-002`), sequential $1 \dots K$ contiguous lap sequence, immutable workout total duration/distance, 9-locale parity without inline emojis, 100% clean-room test pass rate.

### REQ-UI-450: Interactive Map Lap Boundary Moving in Workout Aftermath [Verified]
- **Tests**: `TST-UI-450`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MoveLapBoundaryOnMapSheet.kt`, `LapSplitAndMoveCalculator.kt`, `PathPreviewMap.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `LapEditBottomSheet.kt`, `WorkoutLaps.kt`, `TrackOnMapScreen.kt`
- **Rationale**: When an athlete triggers a lap split too early or too late, or when auto-lap boundaries split mid-segment, the system SHALL allow retroactively shifting the boundary between any two adjacent recorded laps ($k$ and $k+1$) along the GPS track in Aftermath, updating sample records in an atomic range transaction and recalculating both lap summaries without altering total session metrics or affecting any other laps (ATT-908, ATT-5483, ATT-5480):
- **Description**:
  1. *Entry Points & Boundary Pre-Seeding (`REQ-UI-450.1`)*: In `LapEditBottomSheet.kt` (for lap $k < K$) invoked from either `WorkoutSummary.kt` or Workout Details (`TrackOnMapScreen.kt` / `WorkoutDetailTabbedContent.kt`) and `WorkoutLaps.kt` / `LapSplitVisualizerCard` (boundary edit action between adjacent laps), the system SHALL provide an interactive "Move Boundary on Map" (*Rundengrenze auf Karte verschieben*) action using dedicated shift-boundary vector drawable `R.drawable.ic_lap_move_boundary`. When triggered from `LapEditBottomSheet` in Workout Details, the edit sheet is dismissed and `MoveLapBoundaryOnMapSheet` opens pre-seeded at the current boundary index between Lap $k$ and Lap $k+1$ as a modal overlay on `TrackOnMapScreen`.
  2. *Interactive Boundary Move Bottom Sheet & Canonical Blue Polyline (`REQ-UI-450.2`, `MoveLapBoundaryOnMapSheet.kt`)*: The editor SHALL be hosted in `AppModalBottomSheet` (per `ATT-5399`), rendering:
     - The workout GPS track with the active two-lap sub-segment highlighted, instantiated via `MapTrack` with `type = TrackType.BEST` (rendering canonical blue `TTColor.TrackBest` polyline) and start/stop pins.
     - A movable boundary pin draggable or adjustable via a precision slider, rendered in `PathPreviewMap` via parameterized `apexIconRes = R.drawable.ic_lap_move_boundary` (dedicated shift boundary icon instead of the Max Line Distance ruler icon `R.drawable.ic_distance`).
     - Live metric preview cards for both affected adjacent laps (Lap $k$ and Lap $k+1$: duration, distance, and average speed/pace calculated via `LapSplitAndMoveCalculator`).
     - Boundary Clamping & Zero-Phantom Invariant: The new boundary index $s_{\text{new}}$ SHALL be strictly clamped to the range $[startIndex(k) + 1, endIndex(k+1) - 1]$. Neither Lap $k$ nor Lap $k+1$ can collapse into a zero-duration or zero-distance phantom lap (`REQ-TRK-002`).
  3. *Atomic Multi-Database Transaction (`REQ-UI-450.3`, `WorkoutRepository.moveLapBoundary`)*:
     - In `WorkoutSamples.db`, `WorkoutSamplesDatabaseManager.updateLapNumberForSampleRange` SHALL execute an atomic update assigning the shifted sample range $[s_{\text{min}}, s_{\text{max}}]$ to the appropriate target `LAP_NR` ($k$ if shifting boundary forward/right, $k+1$ if shifting boundary backward/left).
     - In `Laps.db`, updated lap summaries for Lap $k$ and Lap $k+1$ SHALL be recalculated from samples and persisted via `LapsDatabaseManager.replaceCroppedLaps` in a single SQLite transaction, leaving all other laps ($1 \dots k-1$ and $k+2 \dots K$) untouched.
     - Export caches SHALL be invalidated and reactive `WorkoutData` emitted to refresh UI immediately.
  4. *9-Language Localization Parity (`REQ-UI-450.4`)*: All user-facing strings SHALL maintain 100% parity across all 9 supported locales without inline decorative Unicode emojis.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-450` (amended by `ATT-5483` and `ATT-5480`), extending `REQ-UI-449` (ATT-907), `REQ-TRK-002` (Lap lifecycle & phantom lap elimination), and `REQ-UI-142` (Edit lap details). Targets `WorkoutRepository.kt`, `LapSplitAndMoveCalculator.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `LapEditBottomSheet.kt`, `MoveLapBoundaryOnMapSheet.kt`, `PathPreviewMap.kt`, and lap action buttons.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.6 (`ATT-907`, map lap creation; `ATT-908`, boundary moving), Sprint 2026-44.1 (`ATT-896`, zero phantom laps), `ATT-5483`, and Sprint 2026-45.7 (`ATT-5480`).
3. *Root Reason for Existing Formulation*: Lap boundaries were historically immutable after tracking to prevent sample/summary desynchronization. In ATT-908, `MoveLapBoundaryOnMapSheet` initially reused `TrackType.GPS` (green) and `PathPreviewMap`'s hardcoded `ic_distance` (ruler) apex marker. Amending introduces canonical blue `TrackType.BEST` and dedicated shift-boundary iconography (`ic_lap_move_boundary`).
4. *Preservation of Core Invariants*: No phantom laps: both Lap $k$ and Lap $k+1$ maintain duration $> 0$ and distance $> 0.0$; contiguous $1 \dots K$ lap numbering strictly preserved; total workout duration, distance, polyline, and extrema remain immutable; Rule 47 modal overlay preserved; `PathPreviewMap` backward compatibility preserved via default `apexIconRes = R.drawable.ic_distance`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an aftermath workout with $\ge 2$ laps,
• *When* the athlete taps "Move Boundary on Map" for Lap $k$,
• *Then* `MoveLapBoundaryOnMapSheet` opens showing the adjacent route segment in canonical blue (`TrackType.BEST`), the movable boundary pin displaying the dedicated shift boundary icon (`ic_lap_move_boundary`), and live preview cards for Lap $k$ and Lap $k+1$.
• *When* the athlete adjusts the boundary slider or pin,
• *Then* the preview metrics for Lap $k$ and Lap $k+1$ update reactively in real time.
• *Given* an attempt to drag the boundary pin to $s \le startIndex(k)$ or $s \ge endIndex(k+1)$,
• *Then* the pin is strictly clamped within $[startIndex(k) + 1, endIndex(k+1) - 1]$ and confirmation is preserved for valid interior points.
• *When* the athlete confirms the boundary move,
• *Then* `WorkoutRepository.moveLapBoundary` executes atomically, updating sample rows in `WorkoutSamples.db` and replacing both lap summaries in `Laps.db`, and the UI immediately displays the updated lap boundaries.
• *Given* lap boundary move entry buttons across `WorkoutLaps`, `LapEditBottomSheet`, `LapSplitVisualizer`, and `MoveLapBoundaryOnMapSheet`,
• *When* rendered,
• *Then* each action button displays the dedicated shift boundary vector drawable `R.drawable.ic_lap_move_boundary`.

**Invariants**: Zero phantom laps (`REQ-TRK-002`), strictly interior boundary clamping $[startIndex(k) + 1, endIndex(k+1) - 1]$, contiguous $1 \dots K$ lap sequence, immutable workout total duration/distance, 9-locale parity without inline emojis, 100% clean-room test pass rate.

### REQ-UI-451: Tracking Cockpit SensorStatus Top Bar 16 dp Screen Edge Offset, 32 dp Touch Targets and 9-Icon Radar Integration [Verified]
- **Tests**: `TST-UI-451`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorStatus.kt`, `SensorSourceDialog.kt`, `SensorType.java`, `res/drawable/ic_radar.xml`, `TrackingTabsScreen.kt`
- **Rationale**: To eliminate display edge clinging (~4 dp) of the hamburger menu button and supporter emblem in the top tracking cockpit bar, align edge margins with Material 3 standard 16 dp offsets (Option A), improve touch ergonomics from 24 dp to 32 dp, strictly preserve mathematical centering of telemetry sensor indicators, and seamlessly integrate the 9th sensor indicator (`SensorType.RADAR_THREAT_COUNT` / `DeviceType.BIKE_RADAR`) within the 272 dp portrait budget without clipping on 360 dp viewports (ATT-5422, ATT-7544, extending `REQ-UI-445.3`):
- **Description**:
  1. *Screen Edge Offset & Padding Geometry (`REQ-UI-451.1`)*:
     - In portrait mode (`!isCompactLandscape`), `SensorStatus.kt` outer `Row` SHALL apply `padding(horizontal = 8.dp, vertical = 4.dp)`.
     - In combination with the enclosing `Surface` padding (`Modifier.padding(4.dp)`) in `TrackingTabsScreen.kt` and the internal 4 dp offset of the 24 dp icon within the 32 dp button (`(32 - 24) / 2 = 4.dp`), the visual edge margin from the display boundary to the outer icon contours SHALL equal exactly 16 dp (`4.dp + 8.dp + 4.dp = 16.dp`), achieving visual harmony with the Material 3 `TopAppBar` and cockpit sensor card headers (`aktiv`, `Geschw.`, `HF`, `Höhe`).
  2. *Symmetrical 32 dp Touch Targets & Fallback Spacers (`REQ-UI-451.2`)*:
     - The left drawer hamburger menu `IconButton` (`onMenuClick`) and right supporter status emblem `IconButton` (`onSupporterClick`) SHALL have touch target bounds of `Modifier.size(32.dp)` housing their standard 24 dp vector drawables (`Icons.Default.Menu` and `supporterEmblemRes`).
     - Symmetrical inactive/fallback spacers (`onMenuClick == null` and `supporterEmblemRes == null`) in both branches SHALL be sized identically to `Modifier.size(32.dp)`.
     - The central container `Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center)` housing the telemetry sensor icons SHALL remain strictly and mathematically centered in the viewport across all supporter tiers (`FREE`, `PIONEER`, `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`).
  3. *Landscape Mode & Invariant Preservation (`REQ-UI-451.3`)*:
     - Compact landscape mode (`isCompactLandscape = true`) governed by `REQ-UI-434.1` SHALL remain untouched (max 28 dp height, trailing horizontal layout), rendering all 9 sensor icons centered alongside the menu button.
     - Sensor source dialog trigger, sensor alpha states (`INACTIVE_SENSOR_ALPHA = 0.38f`), and telemetry click behaviors SHALL remain 100% operational with zero regressions.
  4. *Nine-Icon Header Budget & Radar Vector Icon (`REQ-UI-451.4`)*:
     - `sensorDefinitions` in `SensorStatus.kt` SHALL include `SensorType.RADAR_THREAT_COUNT` as the 9th sensor icon (following `POWER`) in both portrait and compact landscape modes.
     - In portrait mode (`!isCompactLandscape`), horizontal padding per sensor icon in the central container SHALL be adjusted to `4.dp` (`size(22.dp)` + `padding(horizontal = 4.dp)` = `30.dp` per icon slot), ensuring all 9 icons fit cleanly within the 272 dp central container (`9 * 30.dp = 270.dp <= 272.dp`) without clipping on compact 360 dp viewports.
     - The radar icon SHALL render at `1.0f` alpha when `RADAR_THREAT_COUNT` is present in `activeSensors` (indicating active BLE/ANT+ radar hardware connected or `RadarTrafficSimulator` active) and at `INACTIVE_SENSOR_ALPHA = 0.38f` when inactive.
     - `SensorType.getIconResId()` SHALL return dedicated 24dp vector drawable `R.drawable.ic_radar` for `SensorType.RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, and `RADAR_VEHICLE_COUNT`.
     - Dedicated vector asset `res/drawable/ic_radar.xml` SHALL be authored as a clean, standalone 24dp vector drawable featuring concentric radar waves and origin point, rotated 90 degrees counter-clockwise (to the left) so waves radiate horizontally to the left representing rearview vehicular radar detection horizon (`ATT-7736`).
  5. *Bike Radar Sensor Source Dialog & Scalar Metric Omission (`REQ-UI-451.5`)*:
     - When `SensorSourceDialog` is opened for `SensorType.RADAR_THREAT_COUNT`:
       - The dialog title SHALL display the localized "Bike Radar" string (`R.string.device_type_bike_radar`, capitalized) alongside `R.drawable.ic_radar`.
       - The dialog SHALL enumerate active source, active backups, and paired disconnected devices of `DeviceType.BIKE_RADAR` (or display `R.string.source_none_configured` when zero radar devices are paired).
       - Scalar metric `valueWithUnit` text display SHALL be omitted on device identity rows for `RADAR_THREAT_COUNT`.
       - Clicking any device row SHALL invoke `onDeviceClick` opening `EditDeviceDialog`.
  6. *Localization Parity (`REQ-UI-451.6`)*:
     - All radar sensor status and source dialog text, dialog titles, and accessibility content descriptions SHALL maintain 100% localization parity across all 9 supported languages (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Extending `REQ-UI-445.3` (*Live Cockpit Status Emblem & Retirement of Quick Icon Picker Dialog*), targets `SensorStatus.kt`, `SensorSourceDialog.kt`, `SensorType.java`, `TrackingTabsScreen.kt`, `res/drawable/ic_radar.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.4 (`ATT-5397`, commit `8baf42c8`), Sprint 2026-45.4 (`ATT-5422`, commit `fb6797a7`), Sprint 2026-46.5 (`ATT-7544`), and Sprint 2026-46.6 (`ATT-7736`).
3. *Root Reason for Existing Formulation*: `REQ-UI-451` originally budgeted 272 dp for 8 legacy sensors (8 * 34 dp = 272 dp). Adding the 9th sensor for Cycling Rearview Radar (`DeviceType.BIKE_RADAR`) without padding adjustment would require 306 dp (> 272 dp) and cause clipping on 360 dp screens. ATT-7544 authored the standalone 24dp vector asset with concentric waves, but defaulted to upward-pointing waves. Sprint 2026-46.5 Review noted that cycling radar icons canonically radiate horizontally to the left (rearview radar perspective).
4. *Preservation of Core Invariants*: The 8 legacy sensors maintain 100% functional and visual fidelity; 32 dp symmetrical action buttons and 16 dp total display edge offsets are strictly preserved; central alignment is maintained; compact landscape mode remains preserved; 24dp asset bounds, 960x960 viewport, and vector tinting are strictly preserved under 90° counter-clockwise rotation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the tracking cockpit in portrait mode on a 360 dp display,
• *When* inspecting the top `SensorStatus` bar,
• *Then* all 9 sensor icons (including the radar icon `R.drawable.ic_radar`) are visible without clipping, with the left menu button and right supporter emblem starting at exactly 16 dp from the outer display boundary.
• *Given* Cycling Rearview Radar hardware connected or simulator active,
• *When* the radar icon renders,
• *Then* it displays at `1.0f` alpha when active and `0.38f` alpha when inactive.
• *Given* an athlete tapping the radar icon in `SensorStatus`,
• *When* `SensorSourceDialog` opens,
• *Then* the title displays the localized "Bike Radar" with `R.drawable.ic_radar`, enumerates `BIKE_RADAR` devices without scalar value text, and clicking a device opens `EditDeviceDialog`.
• *Given* the Bike Radar status icon `R.drawable.ic_radar` rendered in `SensorStatus` or `SensorSourceDialog`,
• *When* visually inspected,
• *Then* the origin point is located at the center-right and the concentric radar wave arcs radiate horizontally to the left (90 degrees counter-clockwise from upward).

**Invariants**: Option A 16 dp visual edge offset, exact mathematical centering of 9 sensor icons across all tiers, 270 dp <= 272 dp width on 360 dp displays, untouched compact landscape layout (`REQ-UI-434.1`), and 100% 9-language localization parity.

### REQ-UI-452: Dedicated Status Bar Vector Icon, Notification Builder Migration & Monolithic Density Packaging [Verified]
- **Tests**: `TST-UI-452`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable/ic_stat_notify_logo.xml`, `TrainingApplication.java`, `TrackerService.java`, `WorkoutDeletionNotificationManager.kt`, `ExportNotificationManager.kt`, `SensorBatteryNotificationManager.kt`, `app/build.gradle`, `DrawableDensityFallbackAuditTest.kt`
- **Rationale**: To eliminate fatal `android.app.RemoteServiceException` / `BadForegroundServiceNotificationException` crashes reported via Firebase Crashlytics on split-APK devices (where `SystemUI` fails to resolve multi-density raster drawables like `R.drawable.logo` across Binder IPC) and enforce Android notification status bar guidelines requiring monochrome vector silhouettes:
- **Description**:
  1. *Dedicated Monochrome Vector Notification Icon (`REQ-UI-452.1`)*:
     - The application SHALL provide a single density-independent VectorDrawable at `app/src/main/res/drawable/ic_stat_notify_logo.xml` (viewport width 24dp, viewport height 24dp).
     - The vector asset SHALL render the canonical athlete/stopwatch silhouette in monochrome white (`#FFFFFF`) with transparent negative space, without any density-specific (`drawable-*dpi`) or `nodpi` raster siblings.
  2. *Universal Notification Builder Migration (`REQ-UI-452.2`)*:
     - All status bar notification builders across the application SHALL invoke `.setSmallIcon(R.drawable.ic_stat_notify_logo)` instead of `R.drawable.logo`.
     - Specifically, all 6 call sites across the 5 notification managers SHALL be migrated:
       1. `TrainingApplication.java:1511` (Foreground Tracking notification)
       2. `TrackerService.java:1118` (Tracking Interrupted alert notification)
       3. `WorkoutDeletionNotificationManager.kt:62` (Workout deletion progress notification)
       4. `WorkoutDeletionNotificationManager.kt:84` (Workout cache resync notification)
       5. `ExportNotificationManager.kt:111` (Workout export notification)
       6. `SensorBatteryNotificationManager.kt:102` (Sensor battery alert notification)
  3. *Monolithic Density Packaging in App Bundle (`REQ-UI-452.3`)*:
     - In `app/build.gradle`, under `android { ... }`, the application SHALL configure `bundle { density { enableSplit = false } }`.
     - This ensures all raster and vector drawable resources remain embedded directly in `base.apk`, eliminating Split-APK resource resolution desynchronization across all Android devices.
  4. *Redundant `drawable-nodpi/` Duplicate Cleanup & Contract Test Alignment (`REQ-UI-452.4`)*:
     - Duplicate raster assets in `app/src/main/res/drawable-nodpi/` that already exist across canonical density folders (`drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`) and root fallback `drawable/` SHALL be removed.
     - `DrawableDensityFallbackAuditTest.kt` SHALL be updated to verify core assets across canonical high-density buckets (`drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`) without requiring redundant `drawable-nodpi/` duplicates.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-452`, extending `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Architecture*) and `REQ-UI-444.4` (*Master Asset Preservation & Notification Callers*). Targets notification managers, `app/build.gradle`, and `res/drawable/`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.1 (`ATT-3529`, commit `4adb2fcb`) introduced high-density coverage and `drawable-nodpi/` duplicates; Sprint 2026-45.4 (`ATT-1482`, commit `df8bc617`) preserved `R.drawable.logo` for notification backwards compatibility.
3. *Root Reason for Existing Formulation*: `ATT-3529` populated `drawable-nodpi/` to prevent density fallback crashes, but did not prevent AGP density splitting in App Bundles, nor did it provide a vector drawable for `SystemUI` Binder IPC.
4. *Preservation of Core Invariants*: Legacy `R.drawable.logo` is preserved for non-notification usages (`Protocol.java`); all 5 notification managers continue to post notifications on their designated notification channels; zero change to notification priorities or pending intents.

**Acceptance Criteria (Given-When-Then)**:
• *Given* any notification posted by `TrainingApplication`, `TrackerService`, `WorkoutDeletionNotificationManager`, `ExportNotificationManager`, or `SensorBatteryNotificationManager`,
• *When* `NotificationCompat.Builder` builds and posts the notification to `NotificationManagerCompat`,
• *Then* the small icon is `R.drawable.ic_stat_notify_logo` which resolves safely from `base.apk` without throwing `RemoteServiceException`.
• *Given* the Android App Bundle packaging configuration in `app/build.gradle`,
• *When* building release or debug artifacts,
• *Then* `bundle.density.enableSplit` is `false`, keeping all drawables inside `base.apk`.
• *Given* `DrawableDensityFallbackAuditTest.kt`,
• *When* unit tests execute,
• *Then* the test asserts high-density coverage across `drawable-xhdpi`, `drawable-xxhdpi`, and `drawable-xxxhdpi` and passes cleanly.

**Invariants**: Zero `RemoteServiceException` / `BadForegroundServiceNotificationException` crashes, standard monochrome status bar icon style, preservation of `R.drawable.logo` for non-notification callers, and 100% test pass rate.

### REQ-UI-453: Aftermath Workout Details: Grade / Slope Distribution Card & 1% Telemetry Histogram [Verified]
- **Tests**: `TST-UI-453`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/GradeZoneDistributionCard.kt`, `ZoneDistributionCalculator.kt`, `ZoneDistributionColumnChart.kt`, `ZoneDistributionStackedBar.kt`, `ZoneDistributionModels.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabConfig.kt`, `WorkoutSectionType.kt`, `MyPreferenceManager.kt`, `WorkoutRepository.kt`, `AftermathMapUIState.kt`
- **Rationale**: To provide athletes with a dedicated slope distribution visualization in Workout Details and Workout Summary, featuring independent visibility settings, accurate physical terrain nomenclature ("Steigungsverteilung" / "Slope Distribution" instead of physiological "Zonen"), and unambiguous mathematical threshold labels (<2%, ≥2%, ≥5%, ≥10%, ≥15%, ≥20%, ≥25%):
- **Description**:
  1. *Seven Canonical Slope Categories & Unambiguous Threshold Labels (`REQ-UI-453.1`)*:
     - The calculation engine and UI card SHALL classify grade into 7 canonical categories consistent with `ElevationProfile.kt` (`getElevationGradeColor` / `GradeLegend`) using `TTColor.Zone1..Zone7`:
       - Category 1 (`TTColor.Zone1`, Green): `< 2.0 %` (Flat / Downhill)
       - Category 2 (`TTColor.Zone2`, Yellow): `2.0 – 5.0 %`
       - Category 3 (`TTColor.Zone3`, Orange): `5.0 – 10.0 %`
       - Category 4 (`TTColor.Zone4`, Red): `10.0 – 15.0 %`
       - Category 5 (`TTColor.Zone5`, Magenta/Violet): `15.0 – 20.0 %`
       - Category 6 (`TTColor.Zone6`, Black): `20.0 – 25.0 %`
       - Category 7 (`TTColor.Zone7`, White with dark border): `> 25.0 %`
     - In both Column Chart (`ZoneDistributionColumnChart`) and Stacked Bar (`ZoneDistributionStackedBar`), category labels for slope distribution SHALL render mathematical threshold notation `<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%` instead of `Z1`..`Z7`.
     - Physiological zone cards (Heart Rate, Cycling Power) SHALL strictly retain `Z1`..`Z5` and `Z1`..`Z7` labels.
  2. *Loess-Smoothed Grade Calculation Engine (`REQ-UI-453.2`)*:
     - `ZoneDistributionCalculator.calculateGradeDistribution(...)` SHALL smooth path elevation samples using `ElevationSmoothingMath.smoothAltitudes` ($\sigma = 21.0\text{m}$) before grade calculation to prevent GPS quantization noise and phantom `> 25%` grade spikes.
     - Segments with valid duration $\Delta t \le 5\text{s}$ and positive distance $\Delta d > 0$ SHALL be categorized into 7 categories and into 1% fine-grained histogram bins across the full workout grade range (`[minGrade, maxGrade]`).
  3. *`GradeZoneDistributionCard` UI Component & 3-Way Mode Switcher (`REQ-UI-453.3`)*:
     - The UI card SHALL render in `ui/aftermath/zones/GradeZoneDistributionCard.kt` with icon `R.drawable.ic_grade`, localized title `aftermath_grade_zones_title` (*Steigungsverteilung* / *Slope Distribution* across all 9 locales, omitting the word "Zonen" / "Zones"), total active moving/climbing time, and 3-way toggle (`SingleChoiceSegmentedButtonRow`: Balken | Säulen | Histogramm) matching `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.
     - In Histogram mode, `TelemetryHistogramChart` SHALL display 1% bins with unit `"%"` and interactive touch scrubbing.
  4. *Workout Details & Summary Integration as Independent Reorderable Sections (`REQ-UI-453.4`)*:
     - `WorkoutSectionType.GRADE_DISTRIBUTION` (*Steigungsverteilung*) SHALL be an independent, reorderable section in `WorkoutSectionType` and the Summary Cards section list (`WorkoutMasksAndCardsSection.kt`), rendered in `WorkoutSummary.kt` according to `workoutSectionsOrder` and guarded by `WorkoutCardSectionPreferences.showGradeDistribution` (default: `false`).
     - `WorkoutDetailSectionType.GRADE_DISTRIBUTION` (*Steigungsverteilung*) SHALL be an independent, reorderable section in `WorkoutDetailSectionType`, configurable per tab in `WorkoutDetailTabsConfigSheet.kt` (enabled by default in `tab_zones` directly after `ZONES`), and rendered in `WorkoutDetailTabbedContent.kt` at its configured position.
     - In Expert Settings (`WorkoutMasksAndCardsSection.kt`), subordinate toggle items (`SubordinateTuningToggleItem`) for slope distribution under `ZoneDisplayModeSelector` SHALL be removed, consolidating visibility and ordering entirely within the reorderable summary matrix and Detail Tabs configuration sheet.
     - In `WorkoutDetailTabConfig.deserializeTabs`, stored tab configs lacking `GRADE_DISTRIBUTION` across all tabs SHALL automatically migrate `GRADE_DISTRIBUTION` into `tab_zones` directly following `ZONES`.
     - In `TrackOnMapScreen.kt`, `hasZonesData` check SHALL include `|| (gradeZoneDistribution != null && activeDetailPrefs.showGradeDistribution)` so altitude workouts without HR/power sensors only surface the Zonen tab when grade distribution is enabled.
     - Telemetry computation SHALL run 100% asynchronously on `Dispatchers.IO` via `WorkoutRepository` / `TrackOnMapAftermathViewModel`.
  5. *9-Language Localization Parity (`REQ-UI-453.5`)*:
     - All user-visible strings (`aftermath_grade_zones_title` and `settings_workout_card_grade_distribution`) SHALL be localized across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% parity verified by `TranslationParityTest`. Decorative inline Unicode emojis are prohibited.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-453` (Grade / Slope Distribution Card & 1% Telemetry Histogram). Target: `ui/aftermath/zones/`, `WorkoutMasksAndCardsSection.kt`, `MyPreferenceManager.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabConfig.kt`, `WorkoutSectionType.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.7 (`ATT-5487`, commit `36e00b64ef28827de43f2f04399de442ca9a6898`), Sprint 2026-45.8 (`ATT-5563`, commit `7febe360e2`), Sprint 2026-45.9 (`ATT-5654`).
3. *Root Reason for Existing Formulation*: ATT-5563 placed Steigungsverteilung as a subordinate switch under Zone Card Display Mode as an interim visibility control; ATT-5654 promotes it to a first-class sortable section entry in both Summary Cards and Detail Tabs while removing the competing subordinate switches.
4. *Preservation of Core Invariants*: Retains canonical 7-zone color palette (`TTColor.Zone1..Zone7`), preserves DataStore backward compatibility (`showGradeDistribution`), preserves single authoritative configuration surface (Design Guidelines Sec 3.2), keeps `WorkoutMasksAndCardsSection.kt` < 400 lines constraint, and ensures safe auto-migration in `WorkoutDetailTabConfig.deserializeTabs`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a recorded workout with valid GPS distance and elevation path points,
• *When* Workout Details opens the Zonen tab and `WorkoutDetailSectionType.GRADE_DISTRIBUTION` is configured in that tab,
• *Then* `GradeZoneDistributionCard` is rendered showing title "Steigungsverteilung" (DE) / "Slope Distribution" (EN) and threshold badges `<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%`.
• *Given* a workout summary list card,
• *When* `WorkoutSectionType.GRADE_DISTRIBUTION` is enabled (`preferences.showGradeDistribution == true`) and positioned in `workoutSectionsOrder`,
• *Then* `GradeZoneDistributionCard` is rendered at its configured order slot; when disabled, it is omitted.
• *Given* a stored detail tabs configuration lacking `GRADE_DISTRIBUTION`,
• *When* `WorkoutDetailTabConfig.deserializeTabs` is called,
• *Then* `GRADE_DISTRIBUTION` is automatically inserted into `tab_zones` directly following `ZONES`.
• *Given* the Expert Settings screen (`WorkoutMasksAndCardsSection.kt`),
• *When* viewing Zone Analysis sub-settings,
• *Then* zero subordinate toggle switches for slope distribution are rendered under `ZoneDisplayModeSelector`.
• *Given* a workout without heart rate or power sensors but with altitude data,
• *When* `TrackOnMapScreen` evaluates `hasZonesData`,
• *Then* `hasZonesData` evaluates to `true` if `gradeZoneDistribution != null && activeDetailPrefs.showGradeDistribution`.
• *Given* the 3-way display mode toggle on `GradeZoneDistributionCard`,
• *When* the user switches between Stacked Bar, Columns, and Histogram,
• *Then* the card seamlessly transitions between visualization modes with interactive scrubbing supported on the 1% grade histogram.
• *Given* the 9 supported application locales,
• *When* `TranslationParityTest` executes,
• *Then* `aftermath_grade_zones_title` and `settings_workout_card_grade_distribution` are present and non-empty across all 9 resource files.

**Invariants**: Zero frame drops on aftermath screen entry (computation on `Dispatchers.IO`), zero SQLite schema modifications, `WorkoutMasksAndCardsSection.kt` < 400 lines (`TST-UI-377.5`), identical color and boundary definitions as `ElevationProfile.kt`, and 100% test pass rate across targeted test suites.

### REQ-UI-454: Interactive Aftermath Lap Deletion & Adjacent Lap Merging [Verified]
- **Tests**: `TST-UI-454`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `LapSplitAndMoveCalculator.kt`, `WorkoutRepository.kt`, `LapEditBottomSheet.kt`, `DeleteLapConfirmationDialog.kt`
- **Rationale**: When an athlete records unwanted or accidental lap splits, or needs to consolidate adjacent interval segments after tracking in Aftermath, the system SHALL allow deleting a lap boundary and merging adjacent laps whenever $N \ge 2$ laps exist, recomputing combined lap metrics and atomically updating sample records and lap summaries without altering total session metrics.
- **Description**:
  1. *UI Entry Point & Boundary Direction Selection (`REQ-UI-454.1`)*:
     - In `LapEditBottomSheet.kt` and Aftermath Laps (`WorkoutLaps`), when `totalLaps >= 2`, the system SHALL provide a "Delete Lap / Merge Lap" (*Runde löschen / zusammenführen*) action.
     - When clicked, an interactive confirmation dialog (`DeleteLapConfirmationDialog.kt`) SHALL open.
     - **Boundary Direction Selection (Option A)**:
       - For the first lap ($k = 1$), the system SHALL merge Lap 1 with Lap 2 (removing boundary 1).
       - For the last lap ($k = N$), the system SHALL merge Lap $N-1$ with Lap $N$ (removing boundary $N-1$).
       - For interior laps ($1 < k < N$), the system SHALL provide a Material 3 `SingleChoiceSegmentedButtonRow` (per `docs/design_guidelines.md` Section 1.1) allowing the athlete to toggle between:
         - Merging with previous lap: **Lap $k-1$ + Lap $k$** (removing boundary $k-1$)
         - Merging with next lap: **Lap $k$ + Lap $k+1$** (removing boundary $k$)
  2. *Interactive Combined Lap Metric Preview (`REQ-UI-454.2`)*:
     - The confirmation dialog SHALL display live combined metrics preview for the resulting merged lap: active duration, total distance, and average speed/pace calculated via `LapSplitAndMoveCalculator.calculateMergePreview`.
     - Non-empty custom lap names and descriptions from the merged laps SHALL be cleanly preserved and concatenated.
  3. *Atomic Multi-Database Transaction (`REQ-UI-454.3`, `WorkoutRepository.deleteLap`)*:
     - In `WorkoutSamples.db`, `WorkoutSamplesDatabaseManager.decrementLapNumbersAbove` SHALL execute an atomic update `UPDATE <table> SET LAP_NR = LAP_NR - 1 WHERE LAP_NR > ?` with `boundaryLapNr`, reassigning samples so that subsequent lap numbers decrement by 1 and remain strictly contiguous $1 \dots N-1$.
     - In `Laps.db`, updated lap summaries SHALL be recalculated from samples/merged lap data and persisted atomically via `LapsDatabaseManager.replaceCroppedLaps` in a single SQLite transaction.
     - Export caches SHALL be invalidated and reactive `WorkoutData` emitted so `WorkoutLaps`, `LapEditBottomSheet`, and map markers refresh immediately without screen reloads.
  4. *Invariants & Guards (`REQ-UI-454.4`)*:
     - Zero phantom laps (`REQ-TRK-002`): Resulting merged lap duration $> 0$ and distance $\ge 0.0$.
     - Minimum 1 lap: Deletion action SHALL be strictly disabled when total laps $N < 2$.
     - Session totals immutability: Workout total duration, distance, polyline, and extrema remain 100% immutable.
  5. *9-Language Localization Parity (`REQ-UI-454.5`)*:
     - All user-facing strings (`delete_lap_action`, `delete_lap_dialog_title`, `delete_lap_confirm`, `delete_lap_merge_with_previous`, `delete_lap_merge_with_next`, `delete_lap_preview_title`, `delete_lap_warning_description`, `delete_lap_direction_label`) SHALL maintain 100% parity across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) with zero decorative inline Unicode emojis.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-454`, extending `REQ-UI-449` (ATT-907, Map Lap Creation), `REQ-UI-450` (ATT-908, Lap Boundary Moving), `REQ-TRK-002` (Lap lifecycle & zero phantom laps), and `REQ-UI-142` (Lap details editing). Targets `WorkoutRepository.kt`, `LapSplitAndMoveCalculator.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, and `LapEditBottomSheet.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.6 (`ATT-907`, `ATT-908`), Sprint 2026-44.1 (`ATT-896`, zero phantom laps), and Sprint 2026-42.3 (`ATT-3274`, transactional sample pruning).
3. *Root Reason for Existing Formulation*: Lap boundaries were historically immutable post-workout to prevent database desynchronization. ATT-907 added splitting and ATT-908 boundary shifting; deleting/merging closes the lifecycle gap by allowing boundary removal.
4. *Preservation of Core Invariants*: No phantom laps: merged lap duration $> 0$ and distance $\ge 0.0$; contiguous $1 \dots N-1$ lap numbering strictly preserved; total workout duration, distance, polyline, and extrema remain immutable; Rule 47 modal overlay preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with $N \ge 2$ laps,
• *When* an athlete opens `LapEditBottomSheet` for Lap $k$,
• *Then* the "Delete / Merge Lap" action button is visible and enabled.
• *Given* an interior lap $1 < k < N$,
• *When* the user triggers "Delete / Merge Lap",
• *Then* the confirmation dialog displays a `SingleChoiceSegmentedButtonRow` offering toggling between merging with Lap $k-1$ and merging with Lap $k+1$, along with a live preview of the combined lap duration, distance, and pace/speed.
• *Given* the first lap ($k = 1$) or the last lap ($k = N$),
• *When* the user triggers "Delete / Merge Lap",
• *Then* the confirmation dialog preselects and fixes the valid adjacent boundary ($1+2$ for $k=1$, $N-1+N$ for $k=N$) without showing ambiguous toggles.
• *Given* the user confirms deletion of boundary $B$,
• *When* `WorkoutRepository.deleteLap` executes,
• *Then* samples with `LAP_NR > B` are decremented by 1 in `WorkoutSamples.db`, updated lap summaries $1 \dots N-1$ are written to `Laps.db` via `replaceCroppedLaps`, reactive `WorkoutData` emits the updated lap list, and overall workout totals remain unchanged.
• *Given* the 9 supported application locales,
• *When* `TranslationParityTest` executes,
• *Then* all `delete_lap_*` string resources exist across all 9 locale directories with matching format specifiers and zero decorative emojis.

**Invariants**: Zero phantom laps (`REQ-TRK-002`), contiguous $1 \dots N-1$ lap numbering, atomic SQLite rollback, session totals invariance, and 100% test pass rate across targeted test suites.

### REQ-UI-455: Workout-Time Pre-Filtered Photo Picker with Show All Photos Fallback Button [Verified]
- **Tests**: `TST-UI-455`, `TST-LOC-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutPhotoStrip.kt`, `WorkoutTimePhotoPickerSheet.kt`, `WorkoutPhotoManager.kt`, `AndroidManifest.xml`
- **Rationale**: When attaching photos to a completed workout, athletes previously had to browse their entire photo library via Android's generic system photo picker. REQ-UI-455 introduces an in-app bottom sheet pre-filtering photos taken chronologically during the workout time window, while preserving the system picker via a prominent full-width fallback button (Sprint 2026-45.6 user observation, ATT-5475, extending `REQ-UI-448`):
1. *Workout Time Window Pre-Filtered Selection Sheet (`REQ-UI-455.1`)*:
• In `WorkoutPhotoStrip.kt`, tapping "Add Photos" SHALL check for storage/media read permissions (`READ_MEDIA_IMAGES` on API 33+, `READ_MEDIA_VISUAL_USER_SELECTED` on API 34+, or `READ_EXTERNAL_STORAGE` on API <= 32).
• When media read permission is granted, the app SHALL query `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` via `WorkoutPhotoManager.queryWorkoutTimePhotos(context, startMs, endMs, toleranceMs)` for photos taken within `[workoutStartMs - 300_000L .. workoutEndMs + 300_000L]` (matching the canonical 5-minute tolerance from `WorkoutPhotoManager.kt:248`).
• If matching photos exist, the system SHALL display `WorkoutTimePhotoPickerSheet` (hosted in `AppModalBottomSheet(scrollable = false)` per `docs/design_guidelines.md` Section 5.8), presenting a 3-column `LazyVerticalGrid` of downsampled thumbnail cards (`PhotoTrackOverlayComposer.decodeSampledBitmap`) with capture timestamps (`HH:mm`), selection checkboxes, and multi-select support for 1-tap attachment via `WorkoutPhotoManager.addPhoto`.
2. *Stacked Full-Width Action Buttons & Rule 73 Compliance (`REQ-UI-455.2`)*:
• In `WorkoutTimePhotoPickerSheet`, action controls SHALL follow `Rule 73` stacked full-width button layout at the bottom of the sheet content:
  - Primary CTA: `Button(Modifier.fillMaxWidth())` "Hinzufügen (<count>)" / "Add (<count>)", enabled when selected count $> 0$.
  - Secondary CTA: Prominent `OutlinedButton(Modifier.fillMaxWidth())` *"Alle Bilder anzeigen"* ("Show all photos") launching the generic system photo picker (`ActivityResultContracts.PickMultipleVisualMedia`).
• In `actions` slot of `AppModalBottomSheet`: A single full-width secondary text button "Abbrechen" / "Cancel" (`TextButton(Modifier.fillMaxWidth())`).
3. *Automatic Fallback on Permission Denial or Zero Matches (`REQ-UI-455.3`)*:
• If media read permissions are not granted or permanently denied, or if `queryWorkoutTimePhotos` returns 0 matching photos for the time window, the system SHALL seamlessly and automatically fall back to launching `ActivityResultContracts.PickMultipleVisualMedia` without dead-ends.
4. *Runtime Permission Declarations in Manifest (`REQ-UI-455.4`)*:
• `AndroidManifest.xml` SHALL declare `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`, and `READ_EXTERNAL_STORAGE` (`android:maxSdkVersion="32"`).
5. *9-Language Localization Parity (`REQ-UI-455.5`)*:
• All user-visible strings (`workout_photos_time_picker_title`, `workout_photos_show_all`, `workout_photos_add_count`, `workout_photos_no_time_photos`) SHALL maintain 100% parity across all 9 supported application locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) without decorative inline Unicode emojis.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-455`, extending `REQ-UI-448` (*Workout Photos Strip, Full-Screen Preview Bottom Sheet with ATT-30 Overlay Share, Map Pin Markers, and Null-Island Coordinate Rejection*). Targets `WorkoutPhotoStrip.kt`, `WorkoutTimePhotoPickerSheet.kt`, `WorkoutPhotoManager.kt`, and `AndroidManifest.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.6 (`ATT-43`, commit `5c9d9653568e`), amended in `ATT-5479`.
3. *Root Reason for Existing Formulation*: In ATT-43, "Add Photo" in `WorkoutPhotoStrip.kt` was wired directly to Android's generic system picker (`PickMultipleVisualMedia`), which displays the athlete's entire media library without filtering chronologically against the workout time window.
4. *Preservation of Core Invariants*: Generic system photo picker remains unconditionally accessible at all times via prominent button and automatic fallback; existing photo persistence and SQLite schema v27 remain invariant; safe downsampled bitmap decoding prevents OOM.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a completed workout with granted media permissions,
• *When* tapping "Add Photos" and photos were captured during the workout time window ($\pm 5\text{ min}$),
• *Then* `WorkoutTimePhotoPickerSheet` opens displaying matching photos in a 3-column grid with timestamps and multi-select checkboxes.
• *Given* the athlete selects $K \ge 1$ photos in `WorkoutTimePhotoPickerSheet`,
• *When* clicking "Hinzufügen ($K$)",
• *Then* all $K$ selected photos are persisted via `WorkoutPhotoManager.addPhoto` and the sheet closes.
• *Given* the athlete in `WorkoutTimePhotoPickerSheet`,
• *When* clicking *"Alle Bilder anzeigen"*,
• *Then* the sheet closes and Android's system `PickMultipleVisualMedia` picker is launched.
• *Given* media permission is denied or 0 photos match the workout time window,
• *When* the athlete taps "Add Photos",
• *Then* the app automatically falls back to launching Android's system `PickMultipleVisualMedia` picker without error or dead end.
• *Given* all 9 supported application locales,
• *When* `TranslationParityTest` executes,
• *Then* all `workout_photos_*` strings exist across all 9 locale directories with zero missing entries and zero decorative inline emojis.

**Invariants**: Bottom popup standard via `AppModalBottomSheet(scrollable = false)`, `Rule 73` stacked action buttons, downsampled bitmap decoding, 9-language localization parity, and 100% clean-room test pass rate.

### REQ-UI-456: Drag & Drop Section Reordering via Full-Height Drag-Handle in Summary Cards and Detail Tabs [Verified]
- **Tests**: `TST-UI-456`, `TST-LOC-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabsConfigSheet.kt`, `WorkoutDetailTabsConfigComponents.kt`, `WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutSectionReorderContractTest.kt`, `WorkoutDetailTabsTest.kt`
- **Rationale**: In *Experteneinstellungen* (*Advanced Settings*), athletes configure section display order in two distinct surfaces: Summary-Karten (`WorkoutMasksAndCardsSection.kt`) and Detail-Tabs (`WorkoutDetailTabsConfigSheet.kt`). Currently, reordering relies on stacked 22dp `IconButton`s (`KeyboardArrowUp` / `KeyboardArrowDown`) within a 32dp column, which violate Material 3 touch ergonomics ($\ge 44\text{--}48\text{dp}$) and require tedious discrete tapping. REQ-UI-456 replaces these arrow buttons with a prominent, full-row-height drag-and-drop handle (≡) ensuring fluid vertical drag reordering and strict gesture isolation (Sprint 2026-45.10, ATT-5761, refining `REQ-UI-414.3` and `REQ-UI-408.5`):
1. *Full-Height Drag Handle Touch Target (`REQ-UI-456.1`)*:
• In `WorkoutMasksAndCardsSection.kt` and `WorkoutDetailTabsConfigSheet.kt`, section reordering SHALL be triggered via a dedicated drag handle icon (`Icons.Default.DragHandle` / ≡) spanning $\ge 44\text{dp}$ touch target width and full row height, replacing legacy stacked 22dp Up/Down arrow buttons.
2. *Strict Gesture Isolation & Host Scroll Fluidity (`REQ-UI-456.2`)*:
• Vertical drag gesture detection SHALL be strictly isolated to the leading drag handle touch area (`detectVerticalDragGestures`).
• Vertical gestures across the remainder of the row (label, checkbox, spacing) SHALL NOT be intercepted, ensuring natural vertical scrolling of the parent scrollable container (`AdvancedTuningDialog`, `AppModalBottomSheet`) remains completely smooth and unhindered.
3. *Real-Time Position Swapping & Elevation Feedback (`REQ-UI-456.3`)*:
• During active drag, the dragged item SHALL be visually elevated (`surfaceContainerHighest` container tint, subtle shadow, and `zIndex(1f)`).
• When vertical drag translation crosses the adjacent row midpoint threshold, the system SHALL immediately swap positions with the adjacent row in real time and emit updated list ordering for instant DataStore persistence.
4. *Pinned Map Anchor Invariant in Detail Tabs (`REQ-UI-456.4`)*:
• In `WorkoutDetailTabsConfigSheet.kt`, `WorkoutDetailSectionType.MAP` SHALL remain permanently pinned at index 0 when active (`normalizedSections`).
• The MAP row SHALL NOT expose an active drag handle (rendering an immutable pin indicator `Icons.Default.PushPin` or non-draggable handle), and movable rows SHALL NOT be draggable above index 0.
5. *Modularity & File Length Limit (`REQ-UI-456.5`)*:
• Reusable drag-and-drop row logic and components SHALL be placed in `WorkoutDetailTabsConfigComponents.kt`, ensuring `WorkoutMasksAndCardsSection.kt` remains strictly $< 400$ lines in compliance with `REQ-UI-414.5` and `REQ-PRO-011`.
6. *9-Language Accessibility Parity (`REQ-UI-456.6`)*:
• Accessibility content descriptions (`action_drag_to_reorder`, `workout_detail_tabs_map_pinned_desc`) SHALL exist across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) without decorative inline Unicode emojis.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-414.3` (*Collapsible Summary Cards Reorder List*) and `REQ-UI-408.5` (*Workout Detail Tabs Section Reordering*), targeting `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabsConfigSheet.kt`, and introducing `WorkoutDetailTabsConfigComponents.kt`.
2. *Historical Origin & Commit Trace*: `ATT-4043` (`a8e5e356`, Sprint 2026-42.8: 32dp column with 22dp arrows), `ATT-3920` (`ea5fa705`, Sprint 2026-42.8: Up/Down arrows in tab sheet), `ATT-5654` (`c738f195`, Sprint 2026-45.9: `GRADE_DISTRIBUTION` added to both lists).
3. *Root Reason for Existing Formulation*: Up/Down buttons were the simplest click-based control avoiding gesture interception in vertical scroll containers before isolated touch-target drag handles were designed.
4. *Preservation of Core Invariants*: Host scroll integrity preserved; `MAP` pinned at index 0 preserved; DataStore persistence preserved; file length limit `< 400` lines preserved via modular `WorkoutDetailTabsConfigComponents.kt`; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in *Experteneinstellungen* viewing the expanded summary cards list or detail tabs configuration,
• *When* inspecting the leading edge of any reorderable row,
• *Then* a prominent drag-handle icon (≡) with a full $\ge 44\text{dp}$ touch target is displayed instead of stacked 22dp arrows.
• *Given* the athlete touches and drags the drag handle vertically past an adjacent row boundary,
• *Then* the dragged row elevates visually, swaps position in real time with the adjacent row, and emits the new section order.
• *Given* the athlete swipes vertically on any part of the row outside the drag handle,
• *Then* the parent scroll container scrolls normally without initiating a row reorder drag.
• *Given* `WorkoutDetailSectionType.MAP` enabled in a detail tab,
• *Then* the MAP row remains permanently anchored at index 0 and cannot be dragged or displaced.

**Invariants**: Zero gesture contention with host vertical scroll; `MAP` pinned at index 0; instant DataStore serialization; `< 400` lines file limit.

### REQ-UI-457: Dynamic Photo Thumbnail Downsampling Resolution & Dimension-Keyed Reloading [Verified]
- **Tests**: `TST-UI-457`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutPhotoStrip.kt`, `PhotoThumbnailSizeTest.kt`
- **Rationale**: In `WorkoutPhotoStrip.kt` (`PhotoThumbnailCard`), photos were previously downsampled statically via `PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = 240)`. When displayed across `LARGE` (full card width, ~360–400dp / ~1080px physical) or `MEDIUM` (half width, ~180dp / ~540px physical) responsive tiles, upscaling a 240px bitmap produces severe magnification blur. REQ-UI-457 couples decode resolution dynamically to physical item dimensions and keys reloading on dimension changes while bounding resolution to prevent OOM (Sprint 2026-45.10, ATT-5760, refining `REQ-UI-414.6` and `REQ-UI-448`):
1. *Dynamic Downsampling Resolution Target (`REQ-UI-457.1`)*:
• In `WorkoutPhotoStrip.kt`, `calculatePhotoDecodeMaxDim(dimensions: PhotoItemDimensions, density: Density): Int` SHALL calculate the decode resolution based on the item's maximum physical pixel dimension: `(maxOf(dimensions.width, dimensions.height).value * density.density).roundToInt()`.
• The target resolution SHALL be clamped to `[256 .. 1200]` pixels, guaranteeing crisp display on high-density displays for `LARGE` and `MEDIUM` cards while bounding memory footprint.
2. *Dimension-Keyed State & Coroutine Reloading (`REQ-UI-457.2`)*:
• In `PhotoThumbnailCard`, `remember` and `LaunchedEffect` SHALL be keyed on `(photo.filePath, targetMaxDim)` (or `dimensions`), ensuring that switching thumbnail sizes in settings triggers a re-decode at the appropriate resolution without stale blur.
3. *Asynchronous Background IO Decoding (`REQ-UI-457.3`)*:
• Downsampled bitmap decoding SHALL execute asynchronously on `Dispatchers.IO` via `PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = targetMaxDim)`, keeping the Compose UI thread unblocked.
4. *OOM Protection & Layout Stability Invariants (`REQ-UI-457.4`)*:
• Clamping to $\le 1200\text{px}$ guarantees thumbnail memory footprint remains $< 4\text{MB}$ per decoded bitmap. Responsive layout calculations (`calculatePhotoItemDimensions`) and aspect ratios remain invariant.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-414.6` (*Workout Photos Configuration & Responsive Sizing*), targeting `WorkoutPhotoStrip.kt` and `PhotoThumbnailSizeTest.kt`.
2. *Historical Origin & Commit Trace*: `5c9d9653` (`ATT-43`, initial photo strip), `8aed20fc` (`ATT-5474`, photo size presets), `9baf915c` (`ATT-5655`, responsive full/half row layout).
3. *Root Reason for Existing Formulation*: When thumbnail sizes were fixed at 56dp/76dp/104dp in `ATT-5474`, `maxDim = 240` was adequate. When `ATT-5655` expanded `LARGE` to full row width, the downsampling constant was overlooked.
4. *Preservation of Core Invariants*: OOM protection preserved via 1200px upper clamp; asynchronous decoding on `Dispatchers.IO` preserved; layout dimensions and aspect ratios from `calculatePhotoItemDimensions` remain invariant.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout summary or detail card with `PhotoThumbnailSize.LARGE` on a 2.75x–3.0x density display,
• *When* `PhotoThumbnailCard` loads the photo bitmap,
• *Then* `calculatePhotoDecodeMaxDim` computes a target resolution of ~990–1080px (clamped $\le 1200\text{px}$) and displays a sharp, unblurred thumbnail.
• *Given* the athlete changes photo thumbnail size in settings from `MEDIUM` to `LARGE`,
• *When* returning to the photo strip,
• *Then* `LaunchedEffect` detects the dimension change and re-decodes the photo at the higher resolution.
• *Given* a high-density tablet or large viewport where physical pixels exceed 1200px,
• *When* `calculatePhotoDecodeMaxDim` evaluates,
• *Then* the target dimension is capped at 1200px to guarantee OOM safety.

**Invariants**: Clamped resolution $[256 \dots 1200\text{px}]$; zero OOM risk; asynchronous decoding on `Dispatchers.IO`; zero regression on `calculatePhotoItemDimensions`.

### REQ-UI-458: Uniform Action-Oriented Phrasing for Format Import Cards (Strava Bulk Export) [Verified]
- **Tests**: `TST-UI-458`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml` (all 9 locales), `ImportFormatLocalizationTest.kt`, `ImportFormatCardsContractTest.kt`
- **Rationale**: On the Import tab of *Import & Backup* (`ImportBackupTabsScreen.kt`), format import cards display actionable titles with explicit action verbs (`import_fit_title`, `import_tcx_title`, `import_gpx_title`). In contrast, `import_strava_archive_title` previously omitted the action verb (*„Strava-Bulk-Export (ZIP)“* / *„Strava Bulk Export (ZIP)“*), confusing athletes into believing the card was an export action. REQ-UI-458 standardizes `import_strava_archive_title` across all 9 supported locales to use active verb phrasing (Sprint 2026-45.10, ATT-5679, refining `REQ-UI-293` and `REQ-MIG-048`):
1. *9-Language Action Verb Parity (`REQ-UI-458.1`)*:
• `values/`: *„Import Strava Bulk Export (ZIP)“*
• `values-de/`: *„Strava-Bulk-Export (ZIP) importieren“*
• `values-es/`: *„Importar exportación masiva de Strava (ZIP)“*
• `values-fr/`: *„Importer une exportation en bloc Strava (ZIP)“*
• `values-it/`: *„Importa esportazione in blocco Strava (ZIP)“*
• `values-ja/`: *„Strava一括エクスポート (ZIP) をインポート“*
• `values-nl/`: *„Strava bulk-export (ZIP) importeren“*
• `values-pl/`: *„Importuj zbiorczy eksport Strava (ZIP)“*
• `values-pt/`: *„Importar exportação em massa do Strava (ZIP)“*
2. *Resource Key & Layout Stability (`REQ-UI-458.2`)*: Resource identifier `import_strava_archive_title` remains stable; `StravaArchiveImportCard` composable binding in `ImportBackupTabsScreen.kt` remains invariant.
3. *Prohibition of Emojis (`REQ-UI-458.3`)*: String resources SHALL NOT contain decorative inline emojis (`ATT-5400`).
4. *Grammatical Symmetry Across Format Cards (`REQ-UI-458.4`)*: Strava card title phrasing SHALL match the grammatical structure of adjacent FIT, TCX, and GPX format import cards (`REQ-UI-293`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad*) and `REQ-MIG-048` (*Strava Bulk Export Ingestion*), targeting `import_strava_archive_title` across all 9 `strings.xml` locales.
2. *Historical Origin & Commit Trace*: `ATT-2623` (`0f40ce7d`, Sprint 2026-41.1: import format cards) and `ATT-4957` (`d814238e`, Sprint 2026-45.6: Strava bulk archive card introduction).
3. *Root Reason for Existing Formulation*: In `ATT-4957`, the card was named directly after the archive payload format without considering the verbal action phrasing used across the other cards on the screen.
4. *Preservation of Core Invariants*: String key stability preserved; ingestion logic unchanged; zero emoji regression; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the Import tab in German,
• *When* reading the Strava card title,
• *Then* it displays *„Strava-Bulk-Export (ZIP) importieren“*.
• *Given* an athlete viewing the Import tab in English,
• *When* reading the Strava card title,
• *Then* it displays *„Import Strava Bulk Export (ZIP)“*.
• *Given* any of the 9 supported locales,
• *When* `ImportFormatLocalizationTest` executes,
• *Then* `import_strava_archive_title` is non-empty, contains the localized action verb, and contains zero emojis.

**Invariants**: String resource key stability (`import_strava_archive_title`); zero logic changes to `StravaBulkExportImporter.kt` or `ImportBackupTabsScreen.kt`; zero regressions on adjacent format cards.

### REQ-UI-459: Trademark-Free Physiology & Localization Terminology Parity Across All 9 Locales [Verified]
- **Tests**: `TST-UI-459`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml` (all 9 locales), `WorkoutDetails.kt`, `AthleteProfileScreen.kt`, `AdvancedTuningAccordion.kt`, `TipsGuideScreen.kt`, `TranslationParityTest.kt`
- **Rationale**: Peaksware, LLC (parent company of TrainingPeaks) holds registered trademarks on cycling/endurance terms: Normalized Power® (NP®), Intensity Factor® (IF®), Training Stress Score® (TSS®), and Performance Management Chart® (PMC®), along with CTL, ATL, and TSB. The system SHALL replace all proprietary Peaksware trademarks in UI-visible string resources across all 9 supported locales with trademark-free, descriptive terminology while preserving 100% calculation invariance and database schema compatibility (Sprint 2026-45.10, ATT-5678, refining `REQ-UI-401`, `REQ-UI-402`, and `REQ-UI-403`):
1. *Power & Training Load Terminology Parity (`REQ-UI-459.1`)*:
• Across all 9 locales, the system SHALL display **Weighted Power** (**WP**) / DE: **Gewichtete Leistung** for `normalized_power`, **Relative Intensity** (**RI**) / DE: **Relative Intensität** for `intensity_factor`, and **Training Load** (**Load**) / DE: **Trainingsbelastung** for `tss`.
• Badges `normalized_power_short` (`WP`), `intensity_factor_short` (`RI`), and `tss_short` (`Load`) SHALL reside solely in base `values/strings.xml` with `translatable="false"`.
2. *Aftermath Workout Detail Formats (`REQ-UI-459.2`)*:
• In `WorkoutDetails.kt` and summary cards, `workout_power_metrics_*_format` SHALL display `WP: %1$.0f W`, `RI: %1$.2f`, and `Load: %1$.0f` across all 9 locales.
3. *Training Load & Form (Profile & PMC) (`REQ-UI-459.3`)*:
• In `AthleteProfileScreen.kt`, the system SHALL display **Training Load & Form** (DE: **Belastungssteuerung**) for `athlete_profile_pmc_title`, **Fitness (42d)** for `athlete_profile_pmc_ctl_label`, **Fatigue (7d)** (DE: **Ermüdung (7d)**) for `athlete_profile_pmc_atl_label`, **Form (Balance)** for `athlete_profile_pmc_tsb_label`, and `%1$+.1f/wk` (DE: `%1$+.1f/Wo.`) for `athlete_profile_pmc_ramp_format`.
4. *Advanced Settings (Tuning) & Tips Parity (`REQ-UI-459.4`)*:
• In `PhysiologyModelsSection.kt` and `TipsGuideScreen.kt`, titles and descriptions SHALL use trademark-free terms (`tuning_physiology_coggan_*`, `tuning_physiology_pmc_*`, `tip_training_zones_*`).
• `tuning_physiology_pmc_short` SHALL be **Load / Form** (DE: **Belastung / Form**).
5. *System Invariants & Schema Stability (`REQ-UI-459.5`)*:
• Mathematical calculation functions in `PowerPhysiologyCalculator.kt` and `CyclingPowerMetricsDevice.java` SHALL remain strictly unchanged.
• Internal SQLite column names, DTO fields (`normalizedPowerWatts`, `intensityFactor`, `tss`), and sensor enums (`SensorType.NORMALIZED_POWER`, `SensorType.TSS`) SHALL remain invariant.
• All format specifiers (`%1$+.1f`, `%1$.0f`, `%1$.2f`) SHALL remain indexed, and zero decorative inline emojis SHALL be included (`ATT-5400`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines UI string presentation of `REQ-UI-401.3` / `REQ-UI-401.4` (*Performance Management Chart*), `REQ-UI-402.2` (*Power-Based Training Metrics Display*), and `REQ-UI-403.1` / `REQ-UI-403.4` (*Physiology Models Section*), targeting 20 string keys across all 9 `strings.xml` locales.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.7 (`ATT-3858` / `ATT-3859`, commits `a6289096` and `dc62bb3c`), Sprint 2026-44.1 (`ATT-3929`), and Sprint 2026-44.2 (`ATT-4432`).
3. *Root Reason for Existing Formulation*: Previous implementations adopted Dr. Andrew Coggan's literature terminology without separating proprietary registered trademarks from open mathematical equations.
4. *Preservation of Core Invariants*: EWMA impulse-response math ($\tau=42$, $\tau=7$), SQLite schema, backup/restore compatibility, and 9-language translation parity remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing workout details or sensor cockpit in German,
• *When* inspecting power metrics,
• *Then* labels display *„Gewichtete Leistung“* (`WP`), *„Relative Intensität“* (`RI`), and *„Trainingsbelastung“* (`Load`).
• *Given* an athlete viewing the Athlete Profile in English,
• *When* inspecting the training load section,
• *Then* header displays *„Training Load & Form“* and cards display *„Fitness (42d)“*, *„Fatigue (7d)“*, and *„Form (Balance)“*.
• *Given* any of the 9 supported locales,
• *When* `TranslationParityTest` executes,
• *Then* all 20 updated keys exist with matching positional specifiers and zero inline emojis.

**Invariants**: Zero calculation changes; zero database migrations; zero DTO field renaming; 100% 9-locale parity.

### REQ-UI-460: Brand Compliance & Feature Title Neutralization Across All 9 Locales [Verified]
- **Tests**: `TST-UI-460`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml` (all 9 locales), `AppNavigationDrawer.kt`, `SegmentsTabsScreen.kt`, `TipsGuideScreen.kt`, `ImportBackupTabsScreen.kt`
- **Rationale**: To comply strictly with the official Strava API Brand Guidelines (*"Never use Strava in the title of your app or feature"*) and avoid Google Play trademark rejections from listing third-party hardware trademarks (*"Garmin, Wahoo, Hammerhead"*), the system SHALL neutralize feature titles and descriptions across all 9 supported application locales while keeping factual integration references in body texts and preserving existing navigation routes (Sprint 2026-45.10, ATT-5677, refining `REQ-UI-010`, `REQ-UI-293`, and `REQ-UI-432`):
1. *Live Segments Tip Title Neutralization (`REQ-UI-460.1`)*:
• `tip_live_segments_title` SHALL omit the "Strava" brand prefix across all 9 locales (e.g. EN: *"Live Segments &amp; Scrubbing"*, DE: *"Live-Segmente &amp; Scrubbing"*).
• `tip_live_segments_desc` SHALL retain its factual description of Strava segment synchronization.
2. *Navigation Drawer & Top Bar Title Neutralization (`REQ-UI-460.2`)*:
• In `AppNavigationDrawer.kt` and `SegmentsTabsScreen.kt`, `segments` SHALL render neutral category titles without the "Strava" prefix across all 9 locales (e.g. EN: *"Segments"*, DE: *"Segmente"*, ES: *"Segmentos"*, JA: *"セグメント"*).
3. *FIT Hardware Description Neutralization (`REQ-UI-460.3`)*:
• In `ImportBackupTabsScreen.kt`, `import_fit_description` SHALL replace specific brand names with generic hardware categories across all 9 locales (e.g. EN: *"Import workouts from bike computers, sports watches, or smartwatches (.fit)."*, DE: *"Aktivitäten von Fahrradcomputern, Sportuhren oder Smartwatches importieren (.fit)."*).
4. *9-Language Parity & Emoji Prohibition (`REQ-UI-460.4`)*:
• All 3 string resources SHALL exist and be non-blank across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis (`ATT-5400`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines user-facing titles and descriptions in `REQ-UI-010` (*Navigation Drawer Architecture*), `REQ-UI-293` (*FIT Import Card Presentation*), and `REQ-UI-432` (*Tips Guide Screen*), targeting `tip_live_segments_title`, `segments`, and `import_fit_description` across all 9 `strings.xml` locales.
2. *Historical Origin & Commit Trace*: `ATT-2623` (`c89a01f2`, Sprint 2026-41.1: FIT import cards) and `ATT-2276` (`f38b029a`, Sprint 2026-40.16: Tips guide screen).
3. *Root Reason for Existing Formulation*: Colloquial brand names and third-party feature titles were used for immediate familiarity before formal API trademark compliance reviews.
4. *Preservation of Core Invariants*: `NavRoutes.SEGMENTS`, drawer navigation contracts, Strava API synchronization flows, and 9-language translation parity remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the Tips & Guide screen,
• *When* reading the live segments card header,
• *Then* it displays *"Live Segments & Scrubbing"* (or localized) without *"Strava"*.
• *Given* an athlete opening the navigation drawer or segments screen,
• *When* viewing the segments item,
• *Then* the title displays *"Segments"* (DE: *"Segmente"*) without *"Strava"*.
• *Given* an athlete viewing the Import tab,
• *When* inspecting the FIT card subtitle,
• *Then* it references bike computers, sports watches, or smartwatches without naming Garmin, Wahoo, or Hammerhead.
• *Given* any of the 9 supported locales,
• *When* `TranslationParityTest` executes,
• *Then* all 3 keys are non-empty with zero format errors and zero emojis.

**Invariants**: String key stability; zero route or navigation ID alterations; zero Strava OAuth/sync logic changes.

### REQ-UI-461: Trademark-Free Live Climb Tuning Title Parity Across All 9 Locales [Verified]
- **Tests**: `TST-UI-461`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml` (all 9 locales), `LiveClimbSheet.kt`, `TrackingViewsDatabaseManager.java`, `SensorGridLandscapeLayoutTest.kt`
- **Rationale**: To eliminate trademark compliance risks associated with Garmin Ltd.'s registered trademark "ClimbPro" and avoid potential automated Google Play Store rejections under *Unauthorized Use of Third-Party Trademarks*, the system SHALL remove the bracketed suffix `(ClimbPro)` from `tuning_show_live_climbs_title` across all 9 supported application locales, harmonizing the title with existing cockpit toggle phrasing (`config_tracking__show_live_climbs`) while sanitizing codebase comments in production and test files (Sprint 2026-45.10, ATT-5675, refining `REQ-MAP-027` Clause 5, `REQ-UI-383`):
1. *Tuning String Resource Neutralization (`REQ-UI-461.1`)*:
• Across all 9 locales in `app/src/main/res/values*/strings.xml`, `tuning_show_live_climbs_title` SHALL omit the `(ClimbPro)` suffix:
  - `values-de`: `Live-Anstiege`
  - `values`: `Live Climbs`
  - `values-es`: `Subidas en vivo`
  - `values-fr`: `Montées en direct`
  - `values-it`: `Salite in tempo reale`
  - `values-ja`: `ライブクライム`
  - `values-nl`: `Live beklimmingen`
  - `values-pl`: `Podjazdy na żywo`
  - `values-pt`: `Subidas ao vivo`
• `tuning_show_live_climbs_summary` SHALL retain its explanatory description of real-time climb cockpit behavior during routes and free riding.
2. *Code & Comment Trademark Hygiene (`REQ-UI-461.2`)*:
• KDoc header in `LiveClimbSheet.kt` (line 60) SHALL describe the component neutrally as real-time climb cockpit bottom sheet without `(ClimbPro)`.
• Javadoc parameter comment in `TrackingViewsDatabaseManager.java` (line 1517) SHALL refer neutrally to `Live Climb bottom sheets`.
• Test KDoc in `SensorGridLandscapeLayoutTest.kt` (line 187) SHALL refer neutrally to `Live Climb alerts`.
3. *9-Language Parity & Emoji Prohibition (`REQ-UI-461.3`)*:
• `tuning_show_live_climbs_title` SHALL maintain 100% parity across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis (`ATT-5400`) and matching casing/semantics.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines user-facing tuning title in `REQ-MAP-027` (Clause 5: Tuning & Localization Parity) and `REQ-UI-383`, targeting `tuning_show_live_climbs_title` across all 9 `strings.xml` locales, `LiveClimbSheet.kt`, and `TrackingViewsDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: `ATT-1281` (`ef9a1281` / `1e88ef25`, Sprint 2026-40.14: Persistent Climbs Database & Live Climb Bottom Sheet).
3. *Root Reason for Existing Formulation*: `(ClimbPro)` was parenthesized to provide instant feature recognition for cyclists familiar with Garmin cycling computers before trademark risk audits.
4. *Preservation of Core Invariants*: DataStore preference key `KEY_SHOW_LIVE_CLIMBS`, SQLite column `SHOW_LIVE_CLIMBS`, climb detection engine (`ClimbDetector`), and live climb state machine (`LiveClimbsRepository`) remain 100% bit- and byte-identical.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete accessing Advanced Tuning in any of the 9 supported locales,
• *When* viewing the Live Climbs toggle header,
• *Then* the title displays the localized name (`Live Climbs`, `Live-Anstiege`, etc.) without `(ClimbPro)`.
• *Given* all 9 `values*/strings.xml` files,
• *When* `TranslationParityTest` executes,
• *Then* `tuning_show_live_climbs_title` is non-empty across all locales with zero format errors and zero emojis.
• *Given* source code files `LiveClimbSheet.kt`, `TrackingViewsDatabaseManager.java`, and `SensorGridLandscapeLayoutTest.kt`,
• *When* inspected for trademark hygiene,
• *Then* zero occurrences of `ClimbPro` remain in comments or headers.

**Invariants**: Zero changes to `KEY_SHOW_LIVE_CLIMBS` or database schema; zero changes to climb detection or telemetry calculation logic.

### REQ-UI-462: Cluster Naming & Selection Dialog: Bi-directional Escape from Existing Route to Create New Favorite Route [Verified]
- **Tests**: `TST-UI-462`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt` (`ClusterNamingDialog`), `WorkoutClusterComponents.kt` (`WorkoutClusterSelectionDialog`)
- **Rationale**: In `ClusterNamingDialog`, when the athlete selects an existing favorite route via `WorkoutClusterSelectionDialog`, the UI must provide immediate, discoverable escape routes back to creating a new favorite route (`selectedCluster = null`). Furthermore, the read-only route assignment field must reliably respond to taps anywhere across its bounds to open the selection bottom sheet (Rule 77 / Design Guidelines §5.19). The system SHALL provide:
1. *Escape via Selection Dialog (`REQ-UI-462.1`)*: `WorkoutClusterSelectionDialog` SHALL expose an optional callback `onCreateNew: (() -> Unit)? = null`. When non-null, the dialog SHALL render a highlighted selectable card / row with `Icons.Default.Add` and label `cluster_naming__create_new` above the candidate routes list. Tapping this card SHALL invoke `onCreateNew()`, dismiss the bottom sheet, and revert `selectedCluster = null`.
2. *Direct Clear Action on Selected Route Field (`REQ-UI-462.2`)*: When `selectedCluster != null`, the `OutlinedTextField` for the selected route in `ClusterNamingDialog` SHALL display a clear action button (`IconButton` with `Icons.Default.Clear` and content description `R.string.clear`) in its `trailingIcon`, enabling the athlete to revert `selectedCluster` to `null` with a single tap without opening the candidate picker dialog.
3. *State & Form Restoration Invariants (`REQ-UI-462.3`)*: Upon resetting `selectedCluster` to `null` via either mechanism, the new favorite route naming section (`cluster_naming__new_name_prompt` and `name` `OutlinedTextField`) SHALL immediately reappear, preserving any previously entered or pre-filled route name text in `name`. Other callers of `WorkoutClusterSelectionDialog` (`WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`) where `onCreateNew` is omitted (null) SHALL remain strictly unchanged without showing the creation card.
4. *Read-Only Route Picker Field Tap Handling via InteractionSource (`REQ-UI-462.4`, ATT-5818)*: The read-only route assignment `OutlinedTextField` (`cluster_naming__selected_route_label`) SHALL bind a `MutableInteractionSource` and observe `PressInteraction.Release` inside a `LaunchedEffect` to trigger `showSelectionDialog = true` on any tap across the field surface. The composable MUST NOT rely on outer `Modifier.clickable` (which is consumed by Compose's internal `BasicTextField` pointer handler on `enabled = true` fields) and MUST preserve `readOnly = true` state.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ClusterNamingDialog` with an existing favorite route selected (`selectedCluster != null`),
• *When* the athlete taps the clear button (`X`) in the route field's `trailingIcon`,
• *Then* `selectedCluster` becomes `null`, the route field reverts to displaying `cluster_naming__create_new`, and the new route name input field reappears preserving previously entered text.
• *Given* `ClusterNamingDialog` with `WorkoutClusterSelectionDialog` opened,
• *When* the athlete taps the `+ Neu erstellen...` (`cluster_naming__create_new`) card above candidate suggestions,
• *Then* `onCreateNew` is invoked, the selection dialog dismisses, and `selectedCluster` is reset to `null`.
• *Given* callers `WorkoutClustersScreen` or `WorkoutClusterHeatmapScreen` invoking `WorkoutClusterSelectionDialog` without `onCreateNew`,
• *When* the bottom sheet renders,
• *Then* the creation card is omitted and only candidate routes are displayed.
• *Given* `ClusterNamingDialog` in `ImportBackupTabsScreen.kt`,
• *When* the athlete taps anywhere on the text body of the read-only route assignment `OutlinedTextField`,
• *Then* `WorkoutClusterSelectionDialog` opens without requiring the athlete to hit the trailing `my_locations` icon.

**Invariants**: Backward compatibility for `WorkoutClusterSelectionDialog` callers; 9-language localization parity (`TranslationParityTest`); no regression in `ModalBottomSheetDialogsIntegrityTest`; `readOnly = true` preserved on route picker text field.

### REQ-UI-463: Navigation Drawer & Expert Settings Thematic Grouping [Verified]
- **Tests**: `TST-UI-463`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppNavigationDrawer.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`, `strings.xml` (all 9 locales)
- **Rationale**: To prevent interface clutter as features expand, the system SHALL restructure the navigation drawer and Expert Settings into coherent thematic groups while strictly preserving all existing routes, actions, and DataStore parameters (Sprint 2026-45.10, ATT-5670, refining `REQ-UI-010` and `REQ-UI-222`):
1. *Navigation Drawer Group Restructuring (`REQ-UI-463.1`)*:
• In `AppNavigationDrawer.kt`, `drawer_backup_restore` SHALL be moved into `prefsOnlineCommunities` directly below `drawer_export` (yielding 5 items in Online Communities).
• `drawer__settings` SHALL be streamlined to 6 items in canonical sequence: `drawer_tracking_layouts`, `drawer_sport_types`, `drawer_auto_pause`, `drawer_display_settings`, `drawer_units`, `drawer_advanced_tuning`.
• A new collapsible bottom group `drawer__info_support` SHALL be introduced containing: `drawer_supporter`, `drawer_tips_guide`, and `drawer_privacy_policy`.
2. *Expert Settings Thematic Grouping & Subheaders (`REQ-UI-463.2`)*:
• The 9 accordions in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` SHALL be organized into 3 logical blocks separated by visual subheaders:
  - Block 1 (`tuning_group_cockpit_tracking`): Typography & Icons (`COCKPIT_TYPOGRAPHY`), Navigation & Tracking (`NAVIGATION`), Battery & Screen (`BATTERY_SAVER`).
  - Block 2 (`tuning_group_sensors_gps`): Sensor Search (`SENSOR_SEARCH`), GPS & Filter (`SENSORS_GPS`).
  - Block 3 (`tuning_group_analysis_models`): Workout Masks & Cards (`WORKOUT_MASKS_CARDS`), Aftermath & Analysis (`AFTERMATH_ANALYSIS`), Physiology & Performance (`PHYSIOLOGY_MODELS`), Route Clustering (`ROUTE_CLUSTERING`).
3. *Modularity & Subheader Composable Invariants (`REQ-UI-463.3`)*:
• `TuningGroupSubheader` composable SHALL be implemented in `AdvancedTuningAccordion.kt`.
• All files in `ui/settings/tuning/` (`AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`) SHALL maintain strictly `< 400` lines per `AdvancedTuningModularityTest`.
4. *9-Language Parity & Zero Emojis (`REQ-UI-463.4`)*:
• 4 new strings (`drawer__info_support`, `tuning_group_cockpit_tracking`, `tuning_group_sensors_gps`, `tuning_group_analysis_models`) SHALL be defined across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with XML entity escaping (`&amp;`) and zero inline emojis (`ATT-5400`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-010` (*Navigation Drawer Architecture*, ATT-3851) and `REQ-UI-222` (*Advanced Tuning Accordion Architecture*, ATT-1821 / ATT-1957).
2. *Historical Origin & Commit Trace*: Introduced in `d7e82cb4` (drawer collapsible groups) and `e2303aa7` (accordion modularity).
3. *Root Reason for Existing Formulation*: Settings were sequentially added to `drawer__settings` to avoid proliferating top-level categories. Accordions in Expert Settings were sequentially stacked as features grew.
4. *Preservation of Core Invariants*: All navigation route IDs (`R.id.*`) and destination routes remain identical; DataStore schema and keys remain untouched; modularity rule (`< 400` lines) is strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the navigation drawer opened,
• *When* inspecting the drawer groups,
• *Then* `prefsOnlineCommunities` contains `drawer_backup_restore` directly below `drawer_export`, `drawer__settings` contains 6 configuration items ending with `drawer_advanced_tuning`, and `drawer__info_support` forms the bottom collapsible group with supporter, tips guide, and privacy policy.
• *Given* the Expert Settings screen or dialog rendered,
• *When* viewing the accordions list,
• *Then* the 9 accordions are partitioned into 3 blocks preceded by thematic subheaders `tuning_group_cockpit_tracking`, `tuning_group_sensors_gps`, and `tuning_group_analysis_models`.
• *Given* `AdvancedTuningModularityTest`,
• *When* auditing file line counts,
• *Then* `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningAccordion.kt` are all `< 400` lines.
• *Given* `TranslationParityTest`,
• *When* validating all 9 locales,
• *Then* all 4 new keys are non-empty with zero format errors and zero emojis.

**Invariants**: Navigation route resolution (`NavRoutes`), DataStore tuning preference keys, and modularity limits MUST NOT be broken.

### REQ-UI-464: WorkoutDetailTabbedContent Horizontal Tab Swipe & Gesture Arbitration [Verified]
- **Tests**: `TST-UI-464`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabConfig.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabsTest.kt`
- **Rationale**: To prevent athletes from being trapped on the "Map & Graphs" tab (`tab_map_graphs`) and map/chart areas across all tabs, the system SHALL arbitrate horizontal touch gestures so that horizontal tab swiping is only isolated when touching actively inside the 2D map viewport or within the active scrubbing plot area of a chart, and SHALL permit unhindered tab switching from edge gutters, dividers, zoom toolbars, chart headers, and margin strips (Sprint 2026-45.10, ATT-5826, refining `REQ-UI-408.4`):
1. *Initial Down Edge-Swipe Gutter Lock (`REQ-UI-464.1`)*:
• In `WorkoutDetailTabConfig.kt` and `WorkoutDetailTabbedContent.kt`, the edge-swipe gutter SHALL be configured to `32.dp` (`edgeSwipeGutterDp = 32f`).
• The touch X-coordinate evaluation for edge swipe SHALL be latched exclusively from the initial `ACTION_DOWN` (`awaitFirstDown`) event. Once an edge swipe starts within `[0..32.dp]` or `[width - 32.dp..width]`, `userScrollEnabled` on `HorizontalPager` SHALL remain locked to `true` across all subsequent `ACTION_MOVE` events until touch release, regardless of whether the finger travels into the center of the screen.
2. *Granular Interactive Canvas Bounding (`REQ-UI-464.2`)*:
• In `WorkoutDetailTabbedContent.kt` and `MapDetailLayout.kt`, blanket touch capture wrapping the entire `MapDetailLayout` with `isTouchingInteractiveCanvas = true` SHALL be excised.
• `isTouchingInteractiveCanvas = true` SHALL be asserted strictly when touch events originate inside the interactive map viewport (`mapBox`) or inside the active plot area of a chart (`50.dp` from the left edge to `52.dp` from the right edge in portrait, or `0.dp` from the right edge in landscape with docked marginal distributions).
3. *Non-Interactive Strip Pass-Through (`REQ-UI-464.3`)*:
• Touching the `SplitPaneDivider`, the `GlobalTelemetryZoomToolbar` control row, diagram title text headers (e.g., *Höhenprofil*, *Geschwindigkeit*, *Herzfrequenz*, *Leistung*, *W'bal*), inter-chart spacing, left Y-axis labels/margin (`0..50.dp`), or right histogram margin strip (`52.dp`) SHALL set `isTouchingInteractiveCanvas = false`.
• When `isTouchingInteractiveCanvas` is `false`, `requiresHorizontalGestureIsolation` SHALL evaluate to `false`, allowing horizontal swiping to navigate seamlessly between tabs.
4. *Zero 1-Frame Recomposition Lag (`REQ-UI-464.4`)*:
• Gesture arbitration state SHALL synchronize upon the initial `ACTION_DOWN` pass so `HorizontalPager` receives and processes the down event without dropped gestures or 1-frame latency.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-408.4` (*Gesture Isolation in WorkoutDetailTabConfig.kt and WorkoutDetailTabbedContent.kt*, ATT-3920).
2. *Historical Origin & Commit Trace*: Introduced in commit `ea5fa705` (ATT-3920) to isolate 2D map panning and horizontal telemetry scrubbing from triggering unintended tab switches.
3. *Root Reason for Existing Formulation*: Blanket `isTouchingInteractiveCanvas = true` was placed around `MapDetailLayout` as a coarse heuristic because non-map tabs hosted scrollable cards, assuming `analyticsContent` would clear the flag. On `tab_map_graphs`, `analyticsContent == null`, which inadvertently covered 100% of the screen. Additionally, `lastTouchXDp` was updated during `MOVE`, causing edge swipes to cancel as soon as the finger moved past 24 dp.
4. *Preservation of Core Invariants*: Chart scrubbing within plot bounds, chart zoom/pan mode, 2D map navigation, and vertical scrolling via `ChartGestureDisambiguator` remain 100% unimpeded.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete is on the "Map & Graphs" tab (`tab_map_graphs`),
• *When* performing a horizontal swipe starting within the left or right 32 dp edge gutter,
• *Then* the pager switches to the adjacent tab smoothly without gesture abortion as the finger moves across the screen.
• *Given* the athlete touching the `SplitPaneDivider`, `GlobalTelemetryZoomToolbar`, a chart header, or chart Y-axis/histogram margins,
• *When* swiping horizontally,
• *Then* `isTouchingInteractiveCanvas` evaluates to `false` and the tab switches.
• *Given* the athlete touching inside the 2D map canvas or within the active plot area of a telemetry chart away from the edge gutters,
• *When* swiping horizontally,
• *Then* gesture isolation is enforced, preserving 2D map panning and chart scrubbing.

**Invariants**: Map 2D panning, chart scrubbing within plot bounds, vertical scrolling, and tab persistence schemas MUST NOT be degraded.

### REQ-UI-465: KnownLocationCard UI Polish, 48dp Gap Elimination & Compact Map Thumbnail [Verified]
- **Tests**: `TST-UI-465`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsScreen.kt`, `KnownLocationCardLayoutTest.kt`
- **Rationale**: To eliminate visual clutter, vertical bloating, and misleading hierarchy in the "Lieblingsorte" list, `KnownLocationCard` SHALL eliminate the 48dp minimum interactive component padding gap on wrapped drill-down badges, replace the prominent primary blue ascent climbing arrow with a semantic mountain altitude icon in secondary styling, and resize the preview thumbnail map from a 100x100dp square to a compact 116x80dp landscape aspect ratio (Sprint 2026-45.11, ATT-5825, refining `REQ-UI-195`, `REQ-UI-217`, `REQ-UI-244`):
1. *48dp Minimum Touch Target Gap Elimination (`REQ-UI-465.1`)*:
• In `KnownLocationCard` within `KnownLocationsScreen.kt`, the compact drill-down badge `Surface` components ("Starts" and "Routes") inside the `FlowRow` SHALL be scoped under `CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified)` to eliminate the unintended 48dp touch target padding that causes massive vertical whitespace/gaps when badges wrap next to the map thumbnail.
• The "Starts" badge SHALL receive subtle visual emphasis (e.g. `FontWeight.SemiBold`, `secondaryContainer` tint) to clearly distinguish it from secondary metadata chips.
2. *Semantic Altitude Icon & Secondary Hierarchy (`REQ-UI-465.2`)*:
• The reference elevation display SHALL replace the misleading ascent climbing arrow (`R.drawable.ic_ascent` / `↑`) with the standard terrain/mountain icon (`Icons.Default.Terrain` or semantic altitude vector).
• The altitude icon and text SHALL use secondary styling (`MaterialTheme.colorScheme.onSurfaceVariant`, `FontWeight.Normal` or `Medium`) instead of prominent primary blue, maintaining clean visual hierarchy.
3. *Compact Landscape Map Thumbnail (`REQ-UI-465.3`)*:
• In `KnownLocationThumbnailMap`, the preview thumbnail dimensions SHALL be updated from `100.dp × 100.dp` square to a compact 4:3 landscape ratio of `116.dp` width × `80.dp` height.
• The compact landscape thumbnail SHALL preserve horizontal geographic context around the location pin and Google watermark while reducing card vertical footprint.
4. *Contract & Layout Test Parity (`REQ-UI-465.4`)*:
• `KnownLocationCardLayoutTest.kt` SHALL be updated to assert the `116.dp` × `80.dp` dimensions and the replacement of `ic_ascent` with `Terrain`, ensuring regressions in size or icon semantics are caught at compile/test time.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-195` (Badges row in KnownLocationCard), `REQ-UI-217` (Thumbnail preview in KnownLocationCard), and `REQ-UI-244` (100dp harmonization with cluster cards).
2. *Historical Origin & Commit Trace*: `ATT-1643` (`REQ-UI-195`), `ATT-1813` (`REQ-UI-217`), `ATT-2051` (`REQ-UI-244`, commit `e29c614b`).
3. *Root Reason for Existing Formulation*: `REQ-UI-244` harmonized the 100dp square map with route cluster cards; `REQ-UI-195` decoupled badges from altitude but did not account for Material 3's implicit `LocalMinimumInteractiveComponentSize` (48dp), causing massive gaps when badges wrapped next to the 100dp map; `ic_ascent` was used as a quick visual placeholder for altitude.
4. *Preservation of Core Invariants*: Single-tap edit, long-press context menu (`REQ-UI-061`), starts and routes drill-down actions, Google Maps Lite Mode preview, dark theme styling, `LocalInspectionMode` preview fallback, and `KnownLocationZoomMath.calculateThumbnailZoom` invariant are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete is viewing the "Lieblingsorte" (Known Locations) screen,
• *When* inspecting the "Starts" and "Routes" drill-down badges on any `KnownLocationCard`,
• *Then* the badges render without the phantom 48dp minimum touch target gap, fitting compactly with neat vertical spacing when wrapped, and the "Starts" badge is visually distinct.
• *Given* any `KnownLocationCard` with configured reference altitude,
• *When* the altitude line is rendered,
• *Then* it displays `Icons.Default.Terrain` (mountain icon) in `onSurfaceVariant` rather than `ic_ascent` in `primary` blue.
• *Given* the map thumbnail in `KnownLocationCard`,
• *When* rendered on screen or in Compose previews,
• *Then* its dimensions are `116.dp` width by `80.dp` height.

**Invariants**: Single-tap edit, long-press menu, badge drill-down click listeners, Google Maps Lite Mode, test tags (`location_starts_badge_*`, `location_map_preview_*`), and zoom calculation MUST NOT be degraded.

### REQ-UI-466: KnownLocationsScreen Multi-Dimensional Filter UI & Default Threshold [Verified]
- **Tests**: `TST-UI-466`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`, `KnownLocationsFilterBottomSheet.kt`, `ActiveKnownLocationsFilterChipsRow.kt`, `KnownLocationsViewModelTest.kt`
- **Rationale**: To prevent visual clutter from incidental GPS start coordinates (1–4 starts) while keeping all locations accessible on demand, the system SHALL filter known locations on screen entry to $\ge 5$ starts by default (`minStarts = 5`) and provide Material 3 filter controls (header action button with badge, active filter chip strip with 1-tap removal, and modal bottom sheet with keyword search and starts presets) rendering filtered locations in the main list (Sprint 2026-45.11, ATT-5823, extending `REQ-UI-165`, `REQ-UI-180`, `REQ-UI-186`, `REQ-DAT-011`):
1. *Default Start Count Filter Contract (`REQ-UI-466.1`)*:
• On initialization of `KnownLocationsViewModel` / entry to `KnownLocationsScreen`, the filter criteria SHALL always default to `minStarts = 5` (`KnownLocationsFilterCriteria(query = "", minStarts = 5)`).
• The starts threshold predicate SHALL evaluate against the authoritative effective start count (`startsByLocationId[item.id] ?: item.hitCount`) to maintain 1:1 consistency with the card badges.
2. *Header Filter Action Button with Active Badge (`REQ-UI-466.2`)*:
• In the top header of `KnownLocationsScreen`, a `FilterActionButton` SHALL be positioned adjacent to the sort button.
• When any filter dimension is active (`isFilterActive == true`), the button SHALL display a `BadgedBox` badge indicating `activeFilterCount`.
3. *Active Filter Chips Row (`REQ-UI-466.3`)*:
• Directly beneath the header, when filters are active, an `ActiveKnownLocationsFilterChipsRow` SHALL display removable chips (`RemovableFilterChip`) for active dimensions (e.g., `"≥ 5 Starts"`, keyword query) and a "Clear all" button.
• Tapping a chip's remove icon (`×`) SHALL immediately clear that criterion without opening the bottom sheet. Clearing starts threshold resets `minStarts = null`, showing all locations (including 1–4 starts).
4. *Modal Filter Bottom Sheet (`REQ-UI-466.4`)*:
• Tapping the filter action button SHALL open `KnownLocationsFilterBottomSheet` (built on `FilterBottomSheetScaffold`).
• The sheet SHALL include text search for location name/coordinates and filter chip presets for minimum starts: $\ge 3$, $\ge 5$, $\ge 10$, $\ge 25$, plus "Reset all" and "OK" actions.
5. *Filtered List Content & Fallback (`REQ-UI-466.5`)*:
• `KnownLocationsListContent` SHALL render `uiState.filteredLocations` rather than unfiltered `uiState.locations`.
• When `filteredLocations` is empty while filters are active, an informative empty-state message (`filter_no_matching_locations`) with a reset button SHALL be displayed.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Extending `REQ-UI-165` (Locations Management), `REQ-UI-180` (Streamlined management & search), `REQ-DAT-011` (Dynamic starts count).
2. *Historical Origin & Commit Trace*: `ATT-1382` (`REQ-UI-165`), `ATT-1871`/`ATT-2051` (`REQ-UI-180`), `ATT-2134` (`REQ-DAT-011`).
3. *Root Reason for Existing Formulation*: Originally designed as an administrative table displaying all detected locations without thresholding. Clutter from 1–4 starts was unaddressed.
4. *Preservation of Core Invariants*: Stored SQLite locations are never permanently removed or mutated; clearing filter reveals all records; authoritative starts formula (`startsByLocationId[item.id] ?: item.hitCount`) and all item click/edit/context-menu handlers are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens the "Lieblingsorte" screen,
• *When* the list renders initially,
• *Then* only locations with $\ge 5$ effective starts are displayed, and an active filter chip `"≥ 5 Starts"` is visible.
• *Given* active filters on "Lieblingsorte",
• *When* the athlete taps the `×` on the `"≥ 5 Starts"` chip or chooses "Reset all",
• *Then* `minStarts` is cleared to `null` and all locations (including 1–4 starts) are displayed.
• *Given* the athlete tapping the filter button in the header,
• *When* the bottom sheet opens,
• *Then* it displays `FilterBottomSheetScaffold` with search query input and presets for $\ge 3$, $\ge 5$, $\ge 10$, $\ge 25$ starts.

**Invariants**: SQLite database schema, location records, click handlers, delete dialog, and unit conversion invariants MUST NOT be degraded.

### REQ-UI-467: Favorite Routes & Filter Sheets Hit Count Default and Starts-Filtered Location Selection [Verified]
- **Tests**: `TST-UI-467`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClustersViewModel.kt`, `ClusterFilterCriteria.kt`, `ClusterFilterBottomSheet.kt`, `WorkoutFilterBottomSheet.kt`, `ActiveClusterFilterChipsRow.kt`, `WorkoutClustersViewModelTest.kt`, `ClusterFilterBottomSheetTest.kt`, `WorkoutFilterBottomSheetTest.kt`
- **Rationale**: Routes recorded only 1–4 times do not constitute genuine "Lieblingsstrecken" (Favorite Routes), and locations with 1–4 starts flood the "Startet bei (Lieblingsorte)" selection in filter dialogs. The system SHALL default Favorite Routes filtering to $\ge 5$ recordings on screen entry and filter start locations to $\ge 5$ starts with a compact toggle chip (Sprint 2026-45.11, ATT-5822, extending `REQ-UI-187`, `REQ-UI-417`, `REQ-UI-466`):
1. *Favorite Routes Default Minimum Hit Count (`REQ-UI-467.1`)*:
• On initialization of `WorkoutClustersViewModel` / entry to `WorkoutClustersScreen`, the active cluster filter criteria SHALL default to `minHitCount = 5` (`ClusterFilterCriteria(minHitCount = 5)`) as a transparent session default.
• In `ActiveClusterFilterChipsRow`, the active filter chip (*"≥ 5 Aufzeichnungen"*) SHALL immediately display, allowing the athlete to 1-tap remove (`×`) or clear all filters (`minHitCount = null`) to show routes with $< 5$ recordings.
2. *Start Location Selection Filtering in Bottom Sheets (`REQ-UI-467.2`)*:
• In `ClusterFilterBottomSheet` and `WorkoutFilterBottomSheet`, the "Startet bei (Lieblingsorte)" chip group SHALL filter displayed `knownLocations` by default to items with `loc.hitCount >= 5` or any currently active selection (`loc == selectedLocation`).
3. *Show All / Frequent Locations Toggle (`REQ-UI-467.3`)*:
• When known locations with $< 5$ starts exist, a toggle chip (`FilterChip`) SHALL be presented:
  - When collapsed: displaying `"Alle anzeigen"` (`R.string.filter_locations_show_all`)
  - When expanded: displaying `"Nur ≥ 5 Starts"` (`R.string.filter_locations_show_frequent`)
• Tapping the chip toggles between displaying all locations and only locations with $\ge 5$ starts (plus active selection).
4. *9-Language Localization Parity & Zero Emojis (`REQ-UI-467.4`)*:
• All newly introduced string resources (`filter_locations_show_all`, `filter_locations_show_frequent`) SHALL maintain 100% parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero inline emojis (`ATT-5400`).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Extending `REQ-UI-187` (*Filter Bottom Sheets*), `REQ-UI-417` (*Filter Criteria Distance Range & Hit Count*), and `REQ-UI-466` (*Known Locations Starts Threshold*).
2. *Historical Origin & Commit Trace*: `ATT-1402` (commit `91acde33`), `ATT-5823` (commit `3b1f3b33`).
3. *Root Reason for Existing Formulation*: Filter sheets previously rendered all locations unconditionally because datasets were initially small. As athletes recorded hundreds of workouts, incidental single-start vacation coordinates flooded the filter sheets. Similarly, Workout Clusters displayed all clusters including 1-time trips.
4. *Preservation of Core Invariants*: Full dataset remains accessible via 1-tap toggle or chip removal; active selection is never omitted; clearing cluster filter reveals all clusters; 100% 9-language parity with zero emojis (`ATT-5400`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens the Favorite Routes (*Lieblingsstrecken*) screen,
• *When* the screen loads initially,
• *Then* `WorkoutClustersViewModel` initializes with `minHitCount = 5`, only routes with $\ge 5$ recordings are shown, and `"≥ 5 Aufzeichnungen"` is visible in the active chips row.
• *Given* active `minHitCount = 5` on Favorite Routes,
• *When* the athlete taps `×` on the chip or clears all filters,
• *Then* `minHitCount` is set to `null` and all routes (including 1–4 recordings) are displayed.
• *Given* the athlete opens `ClusterFilterBottomSheet` or `WorkoutFilterBottomSheet` with known locations having $< 5$ starts,
• *When* viewing the "Startet bei" section,
• *Then* only locations with $\ge 5$ starts (and any currently selected location) are shown, along with an `"Alle anzeigen"` toggle chip.
• *Given* the `"Alle anzeigen"` toggle chip,
• *When* tapped,
• *Then* all known locations are displayed and the toggle changes to `"Nur ≥ 5 Starts"`.

**Invariants**: Saved DataStore preference criteria are not corrupted; toggling locations does not alter or deselect the active filter coordinate; zero regressions in `ClusterFilterCriteria` or `matches()` logic; zero hardcoded strings; 9-language translation parity.

### REQ-UI-468: Uniform 'Clear all' Action Across Active Filter Chips Rows [Verified]
- **Tests**: `TST-UI-468`, `TST-UI-509`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ActiveFilterChipsRow.kt`, `ActiveSegmentFilterChipsRow.kt`, `ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveDeviceFilterChipsRow.kt`, `ActiveKnownLocationsFilterChipsRow.kt`
- **Rationale**: Across filterable list views, gating the "Clear all" button behind a plural check (`activeFilterCount > 1` or `>= 2`) created inconsistency with Favorite Locations (`ActiveKnownLocationsFilterChipsRow.kt`) and prevented athletes from resetting single default filters (such as `minHitCount = 5` on Favorite Routes) in one tap. The system SHALL display the "Clear all" (`R.string.filter_clear_all`) action button unconditionally whenever any filter criterion is active (Sprint 2026-45.12, ATT-5888, refining `REQ-UI-132`, `REQ-UI-351`, `REQ-UI-431`, `REQ-UI-466`, `REQ-UI-467`):
1. *Unconditional Clear All Visibility when Filter Active (`REQ-UI-468.1`)*:
• In `ActiveFilterChipsRow.kt` (Workouts), `ActiveSegmentFilterChipsRow.kt` (Segments), `ActiveRouteFilterChipsRow.kt` (Routes), `ActiveClusterFilterChipsRow.kt` (Favorite Routes), `ActiveDeviceFilterChipsRow.kt` (Sensors/Devices), and `ActiveKnownLocationsFilterChipsRow.kt` (Favorite Locations), the "Clear all" action button SHALL be displayed unconditionally in the `LazyRow` whenever `!criteria.isEmpty` (i.e. `activeFilterCount >= 1`).
• The legacy conditional guards `if (criteria.activeFilterCount > 1)` and `if (criteria.activeFilterCount >= 2)` MUST NOT be present.
2. *Row Suppression on Empty State (`REQ-UI-468.2`)*:
• When `criteria.isEmpty` is true (`activeFilterCount == 0`), the entire composable row SHALL return immediately without rendering any layout, spacer, or "Clear all" button.
3. *Action Styling & Padding Invariants (`REQ-UI-468.3`)*:
• The button SHALL be rendered as a `TextButton(onClick = onClearAll)` with `Modifier.padding(start = 4.dp)` containing `Text(stringResource(R.string.filter_clear_all), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)`.
4. *9-Language Localization & Accessibility (`REQ-UI-468.4`)*:
• The `filter_clear_all` string resource SHALL maintain 100% parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with touch target $\ge 32\text{dp}$ and zero inline emojis (`ATT-5400`).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new `REQ-UI-468` refining `REQ-UI-132` (*Workout Filter Chips*), `REQ-UI-351` (*Segment Filter Chips*), `REQ-UI-431` (*Route Filter Chips*), `REQ-UI-466` (*Known Location Filter Chips*), and `REQ-UI-467` (*Favorite Routes Filter Chips*).
2. *Historical Origin & Commit Trace*: `ATT-1402` (commit `91acde33`), `ATT-3166` (commit `a9d4791c`), `ATT-5822` (commit `9fa4429e`), `ATT-5823` (commit `3b1f3b33`).
3. *Root Reason for Existing Formulation*: A legacy plural heuristic (`activeFilterCount > 1`) assumed a "Clear all" button was semantically redundant when only a single filter dimension was active, expecting athletes to use the individual chip's remove button. In practice, default filters (such as `minHitCount = 5` on Favorite Routes) left athletes without a prominent reset button.
4. *Preservation of Core Invariants*: `criteria.isEmpty` remains the sole visibility gate for the row; individual chip removal callbacks (`onRemove*`) and full reset (`onClearAll`) remain intact; zero empty-state leakage; 100% 9-language translation parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* exactly 1 active filter criterion on any filterable list (Workouts, Segments, Routes, Favorite Routes, Sensors, Favorite Locations),
• *When* the active filter chips row renders,
• *Then* both the individual filter chip and the "Alle zurücksetzen" / "Clear all" button are visible in the `LazyRow`.
• *Given* multiple ($\ge 2$) active filter criteria,
• *When* the active filter chips row renders,
• *Then* all active chips and the "Alle zurücksetzen" / "Clear all" button are visible.
• *Given* zero active filters (`criteria.isEmpty`),
• *When* the active filter chips row renders,
• *Then* the entire row is suppressed and zero content is emitted.
• *Given* the athlete tapping the "Alle zurücksetzen" / "Clear all" button,
• *When* clicked,
• *Then* `onClearAll` is invoked, resetting all filter criteria.

**Invariants**: Filter criteria data structures, ViewModel filtering logic, and `matches()` predicates MUST NOT be altered; zero regression in individual chip deletion; 100% 9-language localization parity.

### REQ-UI-469: Harmonized KnownLocationCard Layout & Unboxed Typography [Verified]
- **Tests**: `TST-UI-469`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsScreen.kt`, `KnownLocationCardLayoutTest.kt`
- **Rationale**: To eliminate visual discordance with the rest of the application (specifically `WorkoutClustersScreen` / `ClusterItem`), `KnownLocationCard` SHALL harmonize its card layout and typography hierarchy by removing boxed/bordered `Surface` pill containers for starts and routes, anchoring the primary starts count to the bottom baseline in bold primary typography with an activity icon, rendering linked routes as an unboxed primary cross-link, and aligning vertical dimensions with the map thumbnail via `IntrinsicSize.Min` (Sprint 2026-45.12, ATT-5887, refining `REQ-UI-195` and `REQ-UI-465.1`):
1. *Harmonized Bottom-Anchored Column Architecture (`REQ-UI-469.1`)*:
• In `KnownLocationCard` within `KnownLocationsScreen.kt`, the content row below the location title SHALL use `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)`.
• The left metadata column SHALL occupy `Modifier.weight(1f).fillMaxHeight()` and integrate a vertical spring `Spacer(Modifier.weight(1f))` that pins the primary starts count to the bottom baseline, visually aligning with the bottom edge of the map preview thumbnail.
2. *Unboxed Primary Starts Metric (`REQ-UI-469.2`)*:
• The starts metric SHALL eliminate boxed `Surface` pill containers, background tints, border strokes, and trailing chevron icons.
• It SHALL render as an unboxed clickable text row (`onShowWorkouts`, test tag `location_starts_badge_${item.id}`) anchored at the bottom baseline displaying an activity vector (`Icons.AutoMirrored.Filled.DirectionsRun`, 16.dp) and the localized plural count (`R.plurals.known_locations_starts`) in bold primary blue typography (`MaterialTheme.typography.bodyMedium`, `FontWeight.Bold`, `MaterialTheme.colorScheme.primary`).
3. *Unboxed Routes Cross-Link (`REQ-UI-469.3`)*:
• When `linkedClusters.isNotEmpty()`, the routes count SHALL eliminate boxed `Surface` pill containers, border strokes, and trailing chevron icons.
• It SHALL render as an unboxed cross-link row (`onShowRoutes`, test tag `location_routes_badge_${item.id}`) positioned directly beneath the altitude metric, displaying `ic_favorite_route` (14.dp) and the localized plural count (`R.plurals.known_locations_routes`) in primary blue link styling (`MaterialTheme.typography.bodySmall`, `FontWeight.Medium`, `MaterialTheme.colorScheme.primary`).
4. *Header, Map & Context Menu Invariants (`REQ-UI-469.4`)*:
• The top location title row with Home base indicator (`Icons.Default.Home`), the altitude metric row in `onSurfaceVariant` with `Icons.Default.Terrain`, the compact landscape map preview thumbnail (`116.dp × 80.dp`), and the long-press context menu (Home base toggle, Merge action, Delete confirmation) SHALL remain 100% strictly intact and functional.
5. *Contract & Layout Test Parity (`REQ-UI-469.5`)*:
• `KnownLocationCardLayoutTest.kt` SHALL be synchronized to assert `IntrinsicSize.Min` vertical alignment, the unboxed primary/secondary typography hierarchy, and the elimination of bordered `Surface` pills in the metadata column.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-469` refining `REQ-UI-195` (*Badges row in KnownLocationCard*) and `REQ-UI-465.1` (*48dp Minimum Touch Target Gap Elimination on pill badges*).
2. *Historical Origin & Commit Trace*: `ATT-1643` (`REQ-UI-195`), `ATT-5825` (`REQ-UI-465.1`, commit `4c095473`).
3. *Root Reason for Existing Formulation*: In ATT-1816 and ATT-5825, starts and routes were enclosed in explicit `Surface` pill shapes (`RoundedCornerShape(8.dp)`) with background tints and borders to signal clickability. In user review (Sprint 2026-45.11 Review of ATT-5825), the user identified that boxed pills visually clash with the rest of the app, particularly the clean unboxed layout of `WorkoutClustersScreen` (`ClusterItem`), where links and primary hit counts use clean typography with icons without enclosing boxes.
4. *Preservation of Core Invariants*: Independent tap targets for `onShowWorkouts` and `onShowRoutes` preserved; test tags (`location_starts_badge_${item.id}`, `location_routes_badge_${item.id}`) preserved; universal long-press context menu (`REQ-UI-061`, `onMerge`) intact; 100% 9-language localization parity preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete viewing `KnownLocationCard` on `KnownLocationsScreen`,
• *When* the starts metric is rendered,
• *Then* it displays as an unboxed row anchored at the bottom baseline with `DirectionsRun` and bold primary blue typography (`bodyMedium`, `FontWeight.Bold`), invoking `onShowWorkouts` on tap.
• *Given* a known location with associated route clusters (`linkedClusters.isNotEmpty()`),
• *When* the card is rendered,
• *Then* the routes count renders as an unboxed cross-link below the altitude metric in `primary` blue (`bodySmall`, `FontWeight.Medium`) with `ic_favorite_route`, invoking `onShowRoutes` on tap.
• *Given* `KnownLocationCard` metadata column,
• *When* inspected for container styling,
• *Then* zero bordered or tinted `Surface` pill containers wrap the starts or routes text.
• *Given* the bottom row of `KnownLocationCard`,
• *When* measured by Compose layout,
• *Then* it enforces `height(IntrinsicSize.Min)` with `verticalAlignment = Alignment.Bottom`, aligning the bottom of the metadata column with the bottom of the map preview thumbnail.

**Invariants**: Zero regressions in 1-tap navigation to workouts or routes; test tags `location_starts_badge_*` and `location_routes_badge_*` preserved; long-press menu options (Home, Merge, Delete) intact; map thumbnail dimensions (116x80dp) preserved; 100% 9-language localization parity.

### REQ-UI-470: Known Locations Map Orientation, FAB & Description Parity [Verified]
- **Tests**: `TST-UI-470`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationZoomMath.kt`, `KnownLocationsScreen.kt`, `EditKnownLocationDialog.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsRepository.kt`, `MapUtils.kt`
- **Rationale**: To provide superior regional orientation, zero map pin occlusion, one-tap manual creation, immediate starts count hydration, and clear home base utility explanation on "Lieblingsorte" (Sprint 2026-45.12, ATT-5884, refining `REQ-UI-165`, `REQ-UI-224`, and `REQ-SET-095`; refined in Sprint 2026-45.13, ATT-6211 and ATT-6210):
1. *Broadened Map Thumbnail Zoom Range (`REQ-UI-470.1`)*:
• In `KnownLocationZoomMath.kt`, the thumbnail zoom calculation constants SHALL be adjusted to `MIN_ZOOM = 9.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, and `MAX_ZOOM = 11.7f` (reducing zoom by approximately 0.8 levels) to preserve surrounding road and town labels for regional context while ensuring geofences remain fully framed.
2. *Canonical Material Heart Glyph Marker & Opaque Halo (`REQ-UI-470.2`)*:
• In `createCenteredHeartGlyphMarker` within `MapUtils.kt` and `KnownLocationThumbnailMap` within `KnownLocationsScreen.kt`, the heart glyph geometry SHALL be rendered using the canonical Material Design Favorite path specification (`M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z` on a 24x24 coordinate grid, scaled and centered within the bitmap canvas), replacing the ad-hoc flattened Bézier curves.
• To eliminate blue-on-blue stroke bleed-through where larger geofence radii coincide with the marker lobes, the white halo outline SHALL be rendered solid and fully opaque (`Color.White`, alpha = 1.0f, stroke width $\ge 2.5\text{dp}$, `Paint.Cap.ROUND`, `Paint.Join.ROUND`) underneath the filled heart, cleanly isolating the marker from `TTColor.GeofenceStroke` and map canvas terrain.
• The concentric anchor `Offset(0.5f, 0.5f)` and compact 16dp size SHALL be preserved.
3. *Floating Action Button for Manual Location Creation (`REQ-UI-470.3`)*:
• `KnownLocationsScreen` SHALL provide a `FloatingActionButton` (`Icons.Default.Add`, test tag `known_locations_add_fab`) anchored at the bottom-end, aligning with Routes, Workout Clusters, and Equipment screens.
• Tapping the FAB SHALL open `EditKnownLocationDialog` with a new location template (`id = 0L`), allowing athletes to manually create and name a favorite location.
4. *Proactive Starts Count Hydration (`REQ-UI-470.4`)*:
• In `KnownLocationsViewModel.init`, the system SHALL proactively invoke `repository.reconcileHitCounts()` and `workoutRepo?.loadAllWorkouts()`, ensuring the starts count is populated from historical workouts and SQLite hit counts immediately upon screen entry rather than displaying stale "0 Starts".
5. *Informative Home Base Subtitle & 9-Language Parity (`REQ-UI-470.5`)*:
• In `EditKnownLocationDialog`, the "Heim-Basis" switch row subtitle SHALL replace the tautological string `known_locations_set_home` ("Als Heim-Basis festlegen") with the descriptive explanation `known_locations_home_base_desc` ("Automatische Benennung & barometrische Kalibrierung" / "Automatic workout naming & barometric altitude calibration"), localized across all 9 supported locales with 100% translation parity.
6. *Workout Starts Breakdown by Sport Type (`REQ-UI-470.6`)*:
• In `KnownLocationsViewModel`, the system SHALL compute `sportStartsByLocationId: Map<Long, SportStartsCount>` by partitioning workouts starting at each location into `SportStartsCount(bike: Int, run: Int, other: Int)`.
• In `KnownLocationCard` (`KnownLocationsScreen.kt`), the starts metric row (`onShowWorkouts`, test tag `location_starts_badge_${item.id}`) SHALL replace the static runner icon (`Icons.AutoMirrored.Filled.DirectionsRun`) with a dynamic breakdown of sports having `count > 0`, ordered canonically per design guidelines: Bike (`R.drawable.bsport_bike`), Run (`R.drawable.bsport_run`), and Other (`R.drawable.bsport_other` on far right). Each sport item SHALL display its authentic multi-color vector drawable (`tint = Color.Unspecified`, 16.dp) adjacent to its formatted count in bold primary typography (`MaterialTheme.typography.bodyMedium`, `FontWeight.Bold`, `MaterialTheme.colorScheme.primary`).
• When `total == 0` (no historical workouts matched), it SHALL display `0 Starts` (`R.plurals.known_locations_starts`) with default styling as a fallback.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-470` refining `REQ-UI-165` (*Known Locations Management Screen*), `REQ-UI-224` (*Calibrated Zoom Math for KnownLocationCard*), and `REQ-SET-095` (*Home Base Location Tagging*); refined by `ATT-6211` (`REQ-UI-470.2`) and `ATT-6210` (`REQ-UI-470.6`, refining `REQ-UI-469.2`).
2. *Historical Origin & Commit Trace*: `44d51eac` (ATT-1816), `d7b00bf8` (ATT-1878), `c6f65b71` (ATT-5889), `ce73d561` (ATT-5884), `e14d805f` (ATT-5887).
3. *Root Reason for Existing Formulation*: Conservative zoom (`MAX_ZOOM = 12.5f`) and standing pin (`36dp`) were carried over from full-screen maps before the 116x80dp thumbnail was introduced; static runner icon was an interim placeholder when unboxing cards before sport aggregation was introduced; subtitle reused the dropdown action text.
4. *Preservation of Core Invariants*: Harmonized unboxed card layout (ATT-5887), context menu (`Home`, `Merge`, `Delete`), geofence radius adjustment, altitude editing with automatic write-protection (`isLocked = 1`), concentric anchor `Offset(0.5f, 0.5f)`, drill-down navigation `onShowWorkouts`, and 100% 9-language translation parity are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `KnownLocationThumbnailMap` on `KnownLocationsScreen`,
• *When* rendered for a location with 200m radius,
• *Then* the camera zoom level is clamped to `11.7f` (showing surrounding road/regional context), and the marker is a centered heart glyph (`anchor = Offset(0.5f, 0.5f)`) with canonical Material `Favorite` geometry and an opaque white halo (`alpha = 1.0f`) preventing geofence stroke bleed-through.
• *Given* `KnownLocationsScreen`,
• *When* the screen is displayed,
• *Then* a `+` FAB (`known_locations_add_fab`) is visible at the bottom-end, and tapping it opens `EditKnownLocationDialog` to create a new location (`id = 0L`).
• *Given* historical workouts exist at a known location,
• *When* `KnownLocationsScreen` is opened,
• *Then* `reconcileHitCounts()` and `loadAllWorkouts()` run proactively, displaying accurate start counts instead of stale "0 Starts".
• *Given* `EditKnownLocationDialog`,
• *When* viewing the "Heim-Basis" toggle row,
• *Then* the subtitle displays `known_locations_home_base_desc` explaining automatic naming and barometric calibration instead of repeating "Als Heim-Basis festlegen".
• *Given* `KnownLocationCard` with historical workouts,
• *When* the card is displayed,
• *Then* workout starts are broken down by sport in canonical order `[Bike, Run, Other]` with multi-color vector drawables (`tint = Color.Unspecified`), or displays `0 Starts` when no workouts match.

### REQ-UI-471: Cockpit Edit-Mode Typography, Localization Parity & FAB Layout Polish [Verified]
- **Tests**: `TST-UI-471`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorFieldView.kt`, `SensorGridScreen.kt`, `EditSensorFieldDialog.kt`, `TrackingTabsScreen.kt`, `TrackingTabPreviewHeader.kt`, `TrackingTabConfigHeader.kt`, `strings.xml`, `strings_filters.xml`
- **Rationale**: During live UI walkthroughs of the tracking tabs editor, preview modes, and field configuration dialog, 6 distinct layout, localization, and FAB overlap issues were identified:
1. *Edit-Mode Telemetry & Filter Typography (`REQ-UI-471.1`)*:
• In `SensorFieldView.kt`, telemetry value text SHALL enforce `maxLines = 1` and apply responsive font size scaling or truncation prevention in edit mode to guarantee that 8-character strings (`18:29:15`) remain on a single line.
• Top-right filter badge SHALL enforce `maxLines = 1` with localized unit `s` instead of `sec` (e.g. `2 s Ø`), preventing 3-line vertical stacks.
2. *German Localization & Action Parity (`REQ-UI-471.2`)*:
• Active duration field label SHALL be capitalized as `Aktiv` (`R.string.time_active_short`).
• German lap telemetry labels SHALL be localized: `Rundenzeit` (`time_lap`), `Runde` (`time_lap_short`), `Rundennummer` (`lap_number`), `Runde Nr.` (`lap_number_short`), `Rundendistanz` (`distance_lap`), `Dist. Runde` (`distance_lap_short`).
• Tracking editor bottom toggle rows SHALL display `Live-Segmente` (`config_tracking__showLiveSegments`) and `Runden-Button` (`config_tracking__showLapButton`).
• Editor header checkmark button SHALL display localized `Fertig` (`R.string.Done`).
• Field edit actions SHALL use localized content descriptions `cd_move_field` ("Move field" / "Feld verschieben") and `cd_delete_field` ("Delete field" / "Feld löschen") across all 9 supported locales.
3. *Field Configuration Dialog Polish (`REQ-UI-471.3`)*:
• In `EditSensorFieldDialog.kt`, the dialog title SHALL use sentence-case `Anzeige anpassen` (`R.string.edit_field`).
• Category breadcrumb inside the top metric selector card SHALL support `maxLines = 2` to prevent truncation.
• The `Relative Breite` header SHALL be start-aligned (`Modifier.fillMaxWidth()`) with the `1x`, `2x`, `3x` chips below it.
• The smoothing selector button SHALL render a leading `Icons.Default.Tune` icon, trailing dropdown chevron, and localized text (`2 s gleitender Durchschnitt`).
4. *Bicycle Bell FAB & Elevation Profile Clearance (`REQ-UI-471.4`)*:
• On `Karte` and cockpit tabs, when `showBikeBell` or `showLapButton` is enabled and docked at the bottom in portrait mode, `ElevationProfile` in `SensorGridScreen.kt` SHALL allocate adequate bottom clearance (e.g. `68.dp` bottom padding) so floating action buttons do not obscure the elevation profile placeholder banner or canvas text.
5. *Sport Context in Tab Headers (`REQ-UI-471.5`)*:
• `TrackingTabPreviewHeader` and `TrackingTabConfigHeader` SHALL accept and display the active sport discipline name (e.g. `Radeln`) as a context label above the tab title, ensuring continuous athletic context during configuration.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-471` refining `REQ-TRK-046` (*Cockpit Tile Typography*), `REQ-UI-395` (*Sensor Picker Dialog*), `REQ-UI-421` (*Bike Bell Button*), `REQ-UI-435` (*Tracking Tabs Configuration*), and `REQ-UI-437` (*Relative Width Chips*).
2. *Historical Origin & Commit Trace*: `4dc064a684` (ATT-52, bell button alignment), `ATT-3852` (REQ-UI-395 sensor picker), `ATT-5192` (REQ-UI-437 relative width chips), `ATT-4371` / `ATT-4447` (REQ-UI-421 Bike Bell Button).
3. *Root Reason for Existing Formulation*: Rapid iterative additions left English strings in localized XMLs, unconstrained line wrapping in reduced-width edit cards, and lacked bottom clearance between floating action buttons and docked elevation profile.
4. *Preservation of Core Invariants*: 100% test pass rate; zero layout oscillation; 9-language translation parity maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a 2-column sensor grid in edit mode with `Uhrzeit` (`18:29:15`),
• *When* rendered,
• *Then* the time value remains strictly on a single line (`maxLines = 1`) without vertical wrapping.
• *Given* a sensor field with moving average smoothing (e.g. speed),
• *When* rendered in edit mode,
• *Then* the filter badge displays `2 s Ø` on a single line without wrapping into 3 lines.
• *Given* the German locale,
• *When* viewing active duration and lap fields,
• *Then* labels display `Aktiv`, `Runde`, `Runde Nr.`, and `Dist. Runde`, and editor toggles display `Live-Segmente` and `Runden-Button`.
• *Given* the `EditSensorFieldDialog`,
• *When* opened,
• *Then* the title displays `Anzeige anpassen`, `Relative Breite` is start-aligned, category breadcrumb does not truncate, and the smoothing button includes leading tune icon and trailing chevron.
• *Given* the `Karte` tab in portrait mode with `showBikeBell = true` and `showElevationProfile = true`,
• *When* rendered,
• *Then* the bicycle bell FAB does not obscure the elevation profile empty state placeholder banner.
• *Given* `TrackingTabPreviewHeader` and `TrackingTabConfigHeader`,
• *When* displayed,
• *Then* the active sport type name (e.g. `Radeln`) is rendered as a context label.

**Invariants**: Zero regressions in landscape dual-pane tracking; 9-language localization parity strictly maintained; 100% clean-room test pass rate.

### REQ-UI-472: Display & Expert Settings Terminology, Subtitle Localization & Warning Deduplication [Verified]
- **Tests**: `TST-UI-472`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningAccordion.kt`, `TuningFormControls.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `res/values*/strings.xml`
- **Rationale**: User walkthroughs and automated visual audits of Display and Expert Settings uncovered five localization, terminology, and layout defects: (1) internal developer jargon `'Control Tracking'` exposed in user-facing theme descriptions; (2) untranslated developer keyword `Aftermath` in German category headers; (3) redundant header duplication between TopAppBar (`Experten-Einstellungen`) and advisory card (`Experteneinstellungen`); (4) uninflected adjective `Aktiv` causing broken German declension in sensor search summary; and (5) hardcoded English summary fragments and inappropriate kilometer conversion (`0,200km`, `0,400km`) in accordion subtitles.
- **Description**:
1. *Display Settings Tab Terminology (`REQ-UI-472.1`)*:
   • In `cockpit_theme_description`, the system SHALL refer to the localized tab title (`'Start'` in German/English/Dutch/Polish, `'Inicio'` in Spanish, `'Démarrer'` in French, `'Avvia'` in Italian, `'スタート'` in Japanese, `'Iniciar'` in Portuguese) and SHALL NOT leak the internal identifier `'Control Tracking'`.
2. *German Category Terminology Parity (`REQ-UI-472.2`)*:
   • In German `res/values-de/strings.xml`, `tuning_cat_aftermath` SHALL be localized as `Nachbereitung & Profil-Analytik`, eliminating developer jargon.
3. *Advisory Banner Heading Deduplication (`REQ-UI-472.3`)*:
   • `advanced_tuning_warning_title` SHALL be deduplicated from repeating the screen title, displaying a concise non-redundant advisory label across all 9 locales: German `Hinweis`, English `Notice`, Spanish `Aviso`, French `Remarque`, Italian `Avviso`, Japanese `注意`, Dutch `Let op`, Polish `Uwaga`, Portuguese `Aviso`.
4. *Sensor Search Declension Grammar (`REQ-UI-472.4`)*:
   • In German `res/values-de/strings.xml`, `tuning_sensor_search_active` SHALL be inflected as `Aktive`, yielding grammatically correct `Aktive Autosuche`.
5. *Accordion Subtitle Localization & Metric Meter Formatting (`REQ-UI-472.5`)*:
   • `formatBatterySaverSubtitle()` SHALL load localized slope labels (`Flach`/`Steil`) when `Context` is available.
   • `formatSensorsGpsSubtitle()` SHALL load localized labels (`Höhe`/`Geschw.`) when `Context` is available.
   • `formatClusterTuningSubtitle()` SHALL format metric tolerances $\le 1000\text{ m}$ as integer meters (`"${tolerance.roundToInt()} m"` instead of `0,xxxkm`) and bind localized labels (`Ende`/`Apex`/`Dist.`/`Höhe` in German).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-472` refining `REQ-UI-122` (*Display Settings*) and `REQ-UI-222` (*Tuning Accordion*).
2. *Historical Origin & Commit Trace*: `ATT-2746` (REQ-UI-122 Display settings), `ATT-2940` (REQ-UI-222 Tuning accordion), and `ATT-5819` (REQ-UI-469 TuningWarningNotice styling).
3. *Root Reason for Existing Formulation*: Early rapid prototyping reused internal code identifiers and hardcoded English formatters; additions prioritized function over localization parity.
4. *Preservation of Core Invariants*: 100% 9-language translation parity maintained; zero regressions in Settings persistence and reset mechanisms.

**Acceptance Criteria (Given-When-Then)**:
• *Given* German locale active,
• *When* viewing Display settings `cockpit_theme_description`,
• *Then* the text references `'Start'` and does not contain `'Control Tracking'`.
• *Given* German locale active,
• *When* viewing Expert Settings categories,
• *Then* the aftermath section header displays `Nachbereitung & Profil-Analytik`.
• *Given* the Expert Settings screen,
• *When* viewing the top advisory warning card,
• *Then* the title displays `Hinweis` (DE) / `Notice` (EN) without duplicating the TopAppBar title `Experten-Einstellungen`.
• *Given* German locale active,
• *When* viewing sensor search accordion summary,
• *Then* the text displays `Aktive Autosuche`.
• *Given* AMOLED battery saver and sensors accordion summaries with German locale,
• *When* rendered with context,
• *Then* the summaries display `Flach`/`Steil` and `Höhe`/`Geschw.`.
• *Given* workout cluster tuning summary with metric tolerances $\le 1000\text{ m}$,
• *When* rendered,
• *Then* tolerances are formatted as integer meters (e.g. `200 m`, `400 m`) rather than fractional kilometers (`0,200km`, `0,400km`).

**Invariants**: 100% 9-language localization parity across all resource files; zero regressions in preferences persistence or tuning computations; 100% clean-room test pass rate.

### REQ-UI-473: Modal Bottom Sheet Close Button Elimination for Bottom Action Bar Dialogs & Activity Type Selection Polish [Verified]
- **Tests**: `TST-UI-473`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppModalBottomSheet.kt`, `ActivityTypeSelectionDialog.kt`, `AppModalBottomSheetTest.kt`
- **Rationale**: In `AppModalBottomSheet.kt`, `showCloseButton: Boolean = true` was statically defaulted regardless of whether a bottom action bar (`actions != null`, e.g. `AppDialogActions.SaveCancel`) was present. This created redundant dismissal controls (both a top-right `✕` and a bottom `Abbrechen` / Cancel button) across settings and sync sheets (`Dropbox`, `Google Drive`, `Export`, `Display`, `Units`, `Strava`, `EditEquipmentDialog`). Furthermore, `ActivityTypeSelectionDialog.kt` unnecessarily rendered both a top-right `✕` and a bottom `AppDialogActions.CancelOnly` row for single-tap list selections.
- **Description**:
1. *Dynamic Default for Modal Bottom Sheet Close Button (`REQ-UI-473.1`)*:
   • In `AppModalBottomSheet.kt`, `AppModalBottomSheet`, `AppBottomSheetContent`, and private `AppBottomSheetBody` SHALL position `actions: (@Composable RowScope.() -> Unit)? = null` before `showCloseButton` in their parameter lists and SHALL dynamically default `showCloseButton: Boolean = (actions == null)`.
   • When a bottom action bar is supplied (`actions != null`), the top-right `✕` close button SHALL NOT render by default.
   • When no bottom action bar is supplied (`actions == null`), the top-right `✕` close button SHALL render by default.
   • Callers SHALL retain the ability to explicitly override `showCloseButton` (`true` or `false`) via named arguments.
2. *Activity Type Selection Dialog Simplification (`REQ-UI-473.2`)*:
   • In `ActivityTypeSelectionDialog.kt`, the redundant bottom action bar (`actions = { AppDialogActions.CancelOnly(onCancel = onDismiss) }`) SHALL be removed.
   • The dialog SHALL present solely the header `✕` close button (`actions == null`) alongside list item selection taps (`onTypeSelected(type)` + `onDismiss()`).
3. *Strava Settings Dialog Synergy (`REQ-UI-473.3`)*:
   • In `StravaSettingsDialog.kt`, the presence of bottom action buttons (`actions = { ... }`) SHALL automatically eliminate the duplicate top-right `✕` button via `AppModalBottomSheet`'s dynamic default, without requiring bespoke call-site parameter overrides.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-473`, refining `REQ-UI-148` (*Universal Modal Bottom Sheet*) and `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs & Selection Flows*).
2. *Historical Origin & Commit Trace*: Commit `e8c7a75c` (ATT-1034, 2026-09-15) and commit `fceafd16` (ATT-1072, 2026-09-16).
3. *Root Reason for Existing Formulation*: Early modal bottom sheets had no bottom action bar, requiring an explicit top-right close icon. When bottom action rows were added, `showCloseButton = true` remained an unconditioned static default.
4. *Preservation of Core Invariants*: Sheets with `actions == null` (e.g. `PairingProtocolBottomSheet`, `AntServicesStatusSheet`, `ClusterInfoDialog`) continue showing top-right `✕` by default; explicit parameter overrides (`showCloseButton = true/false`) remain fully supported; named argument caller source compatibility is 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `AppModalBottomSheet` or `AppBottomSheetContent` rendered with `actions != null`,
• *When* `showCloseButton` is not explicitly specified,
• *Then* `showCloseButton` defaults to `false` and no top-right `✕` button is rendered.
• *Given* `AppModalBottomSheet` or `AppBottomSheetContent` rendered with `actions == null`,
• *When* `showCloseButton` is not explicitly specified,
• *Then* `showCloseButton` defaults to `true` and the top-right `✕` button is rendered.
• *Given* `AppModalBottomSheet` or `AppBottomSheetContent`,
• *When* `showCloseButton` is explicitly passed as `true` or `false`,
• *Then* the explicit parameter value SHALL be respected regardless of `actions`.
• *Given* `ActivityTypeSelectionDialog`,
• *When* rendered,
• *Then* it displays the header `✕` close button and no bottom action bar.

**Invariants**: Named argument caller source compatibility across all existing sheets; edge-to-edge window insets (`navigationBarsPadding()`, `imePadding()`) and drag handles preserved; 100% clean-room test pass rate.

### REQ-UI-474: Import & Backup UI Polish, Strava Archive Tuning & Heading Deduplication [Verified]
- **Tests**: `TST-UI-474`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `BackupTabContent.kt`, `RestoreTabContent.kt`, `res/values*/strings.xml`, `RestoreCardContractTest.kt`
- **Rationale**: On `ImportBackupTabsScreen.kt` and its child composables (`BackupTabContent.kt`, `RestoreTabContent.kt`), workflow and UI consistency gaps existed: (1) Strava archive picker bypassed `PreImportTuningBottomSheet`, preventing tuning review and leaking re-upload options; (2) format import and restore buttons lacked authentic brand icons; (3) incremental import in `RestoreTabContent` inappropriately used an upward backup icon (`Icons.Default.Backup`); and (4) duplicate headings were displayed immediately above identical action buttons.
- **Description**:
1. *Strava Archive Pre-Import Tuning Integration (`REQ-UI-474.1`)*:
   • In `ImportBackupTabsScreen.kt`, selecting a valid Strava export archive (`.zip`) SHALL store the URI in `pendingStravaArchiveUri` and display `PreImportTuningBottomSheet`.
   • `PreImportTuningBottomSheet` SHALL support `showStravaUploadOption: Boolean = true`. For Strava archive imports (`pendingStravaArchiveUri != null`), `showStravaUploadOption` SHALL be `false` to prohibit re-uploading Strava data back to Strava.
2. *Authentic Cloud Brand Iconography & Merge Icon (`REQ-UI-474.2`)*:
   • In `FormatImportCard`, Dropbox scan buttons SHALL render leading `R.drawable.dropbox_logo_blue` with `tint = Color.Unspecified`.
   • In `RestoreTabContent.kt`, Dropbox restore SHALL render `R.drawable.dropbox_logo_blue` and Google Drive restore SHALL render `R.drawable.ic_google_drive`, both with `tint = Color.Unspecified`.
   • In `RestoreTabContent.kt`, the incremental import action button SHALL display `Icons.Default.CallMerge` (replacing `Icons.Default.Backup`).
3. *Heading Deduplication & Action Button Phrasing Parity (`REQ-UI-474.3`)*:
   • In `BackupTabContent.kt`, the primary backup button SHALL use `R.string.backup_now` ("Jetzt sichern" / "Backup Now"), deduplicating it from the card title `R.string.create_backup`.
   • In `BackupTabContent.kt`, the automated backup card SHALL integrate the `Switch` directly into the card header row alongside `R.string.automated_backups`, eliminating the duplicate inner row title.
   • In `RestoreTabContent.kt`, the incremental import button SHALL use `R.string.merge_from_backup_file` across all 9 supported locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`), deduplicating it from the card title `R.string.import_workouts`.
4. *Contract Test Synchronization (`REQ-UI-474.4`)*:
   • `RestoreCardContractTest.kt` SHALL verify authentic brand drawables (`dropbox_logo_blue`, `ic_google_drive`) and `CallMerge` icon contracts.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-474`, refining `REQ-MIG-036` (*Automated Backups & Restore*) and `REQ-UI-458` (*Format Import Cards*).
2. *Historical Origin & Commit Trace*: Commit `8020d902` (ATT-4664, 2026-10-22) and commit `523194a2` (ATT-5679, 2026-11-05).
3. *Root Reason for Existing Formulation*: Early iterations utilized generic Material icons and duplicated card titles as quick button labels before brand icons and UI deduplication patterns were established.
4. *Preservation of Core Invariants*: 100% 9-language translation parity without emojis; archive safety validation before tuning launch; destructive restore confirmation dialogs strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete selects a Strava `.zip` export file,
• *When* the archive is validated,
• *Then* `PreImportTuningBottomSheet` is displayed with cluster tolerance sliders and without the Strava upload toggle (`showStravaUploadOption = false`).
• *Given* `RestoreTabContent`,
• *When* rendered,
• *Then* Dropbox restore displays `dropbox_logo_blue`, Google Drive restore displays `ic_google_drive`, and incremental merge displays `Icons.Default.CallMerge` with label `merge_from_backup_file`.
• *Given* `BackupTabContent`,
• *When* rendered,
• *Then* the backup button displays `backup_now` and the automated backup switch is positioned in the card header without duplicate row text.

**Invariants**: Archive validation strictly enforced; 100% 9-language parity without emojis; WorkManager background backup scheduling preserved; 100% clean-room test pass rate.

### REQ-UI-475: Sport Type Card Presentation, Localized Mappings & Locale-Aware Usage Dates [Verified]
- **Tests**: `TST-UI-475`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SportTypesTabsScreen.kt`, `DateTimeUtils.kt`, `res/values*/strings.xml`, `arrays.xml`
- **Rationale**: On `SportTypesTabsScreen.kt`, visual formatting, mapping, and localization gaps existed: (1) sport cards with empty GoldenCheetah mappings unconditionally formatted `TCX: %1$s, %2$s` with `mapping_format_gc`, producing dangling trailing text (`"💾 TCX: Other, GC:"`); (2) default locked sport types displayed raw English database names (`"Other"`) rather than localized string resources (`"Andere"` in German), and export identifiers displayed unlocalized technical keys; and (3) first and last activity usage dates displayed raw ISO-8601 strings (`"YYYY-MM-DD"`) instead of locale-formatted dates.
- **Description**:
1. *Conditional GoldenCheetah Mapping & File Export Format (`REQ-UI-475.1`)*:
   • On sport type cards, when `item.gcName.isNullOrBlank()`, the UI SHALL display only the localized TCX mapping (`stringResource(R.string.mapping_format_tcx, localizedTcx)`), completely omitting `, GC:` and trailing empty colons.
   • When `item.gcName` is present and non-blank, the UI SHALL format both mappings via `sport_type_file_mapping_format`.
2. *Default Sport Localization & Export Identifier Mapping (`REQ-UI-475.2`)*:
   • For system default / locked sport types (`!item.isEditable`), the sport card header SHALL resolve the display name via `item.bSportType.stringResId` (e.g. `R.string.sport_type_other` for `BSportType.UNKNOWN`, `R.string.sport_type_running` for `BSportType.RUNNING`, `R.string.sport_type_biking` for `BSportType.CYCLING`) rather than displaying raw database `item.name`.
   • Strava export keys SHALL be mapped to localized UI names via `Strava_Sport_Types_Strava_Names` and `Strava_Sport_Types_UI_Names` array pairs.
   • TCX and GC export keys SHALL be resolved to localized UI strings via array lookups (`R.array.TCX_Sport_Types` and `R.array.GC_Sport_Types`) with fallback to raw values if unmapped.
3. *Locale-Aware ISO-8601 Date Formatting (`REQ-UI-475.3`)*:
   • `DateTimeUtils.kt` in `com.atrainingtracker.trainingtracker.ui.common` SHALL provide pure helper `formatIsoDate(dateString: String?, locale: Locale = Locale.getDefault()): String`.
   • Valid ISO-8601 dates (e.g. `"2018-08-12"`) SHALL be parsed via `LocalDate.parse()` and formatted with `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)` (e.g. `"12.08.2018"` in `de_DE`, `"Aug 12, 2018"` in `en_US`).
   • Null, blank, or malformed strings SHALL fail-safely return an empty string or the raw fallback string without throwing runtime exceptions.
   • In `SportTypesTabsScreen.kt`, first and last activity dates SHALL be rendered using `DateTimeUtils.formatIsoDate(it)`.
4. *Contract & Unit Test Regression (`REQ-UI-475.4`)*:
   • `DateTimeUtilsTest.kt` SHALL verify date parsing across locales (`de_DE`, `en_US`, `ja_JP`) and error tolerance.
   • `SportTypesTabsScreenContractTest.kt` SHALL verify empty GC suppression, default sport localization, and formatted usage date rendering.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-475`, refining `REQ-UI-061` (*Sport Types Management Screen*).
2. *Historical Origin & Commit Trace*: Commit `2ef1bbdf` (2026-02-23) simplified file mapping labels; commit `0e17d2a5` migrated sport types list to Jetpack Compose.
3. *Root Reason for Existing Formulation*: Early Compose migration replicated legacy `SimpleCursorAdapter` text-concatenation where TCX and GC were assumed to always have non-empty pairs, and raw database strings were displayed directly.
4. *Preservation of Core Invariants*: Technical export keys sent to Strava, TCX, and FIT exporters remain strictly unchanged in SQLite and export payloads; custom user-defined sport types with `isEditable == true` retain user-defined names; 100% 9-language translation parity maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a sport type with empty `gcName` (e.g. default "Other"),
• *When* rendered in `SportTypesTabsScreen`,
• *Then* the file format row displays `"💾 TCX: Andere"` (or locale equivalent) without trailing `, GC:`.
• *Given* a locked default sport type (`!item.isEditable`),
• *When* rendered in `SportTypesTabsScreen`,
• *Then* the header title displays the localized name (`"Andere"` in German) instead of raw `"Other"`, and export identifiers display localized UI names.
• *Given* sport usage dates `"2018-08-12"` and `"2024-05-01"`,
• *When* rendered in `SportTypesTabsScreen` in locale `de_DE`,
• *Then* `UsageItem` displays `"12.08.2018"` and `"01.05.2024"`.

**Invariants**: Technical export keys in SQLite and exporter payloads are completely unaffected; custom user-defined sport types retain their exact user-specified names; `formatIsoDate` is fail-safe; 100% clean-room test pass rate.

### REQ-UI-487: Navigation Drawer Units Disambiguation & Distinct Power Cockpit Iconography [Verified]
- **Tests**: `TST-UI-487`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `res/values-de/strings.xml`, `res/values-ja/strings.xml`, `res/drawable/bsport_bike_power.xml`, `ActivityType.java`, `AppNavigationDrawer.kt`, `UnitsSettingsDialog.kt`, `ActivityTypeSelectionDialog.kt`, `CopyTrackingTabDialog.kt`
- **Rationale**: In the German navigation drawer, both `tab_workouts` (*Training -> Einheiten*) and `prefsUnitsTitle` (*Einstellungen -> Einheiten*) resolved to `"Einheiten"`, causing homonym ambiguity. In the units settings dialog (`UnitsSettingsDialog.kt`), German and Japanese retained untranslated English unit `"mile"` in `units_type_imperial` (`"Imperial (mile)"`). Furthermore, in activity type selection dialogs (`ActivityTypeSelectionDialog.kt`, `CopyTrackingTabDialog.kt`), `ActivityType.BIKE_SPEED_AND_CADENCE` and `ActivityType.BIKE_POWER` both referenced `R.drawable.bsport_bike`, rendering them visually indistinguishable.
- **Description**:
1. *Drawer & Dialog Label Disambiguation (`REQ-UI-487.1`)*:
   • In `res/values-de/strings.xml`, `prefsUnitsTitle` SHALL resolve to `"Maßeinheiten"`, eliminating navigation drawer label collision with `tab_workouts` (`"Einheiten"`).
   • In `AppNavigationDrawer.kt` (under Settings -> `drawer_units`) and `UnitsSettingsDialog.kt` header, the displayed title SHALL bind `R.string.prefsUnitsTitle` (`"Maßeinheiten"` in DE, `"Units"` in EN).
2. *Units Dialog Imperial Localization Parity (`REQ-UI-487.2`)*:
   • In `res/values-de/strings.xml`, `units_type_imperial` SHALL resolve to `"Imperial (Meilen)"`.
   • In `res/values-ja/strings.xml`, `units_type_imperial` SHALL resolve to `"ヤードポンド法 (マイル)"`.
   • In `UnitsSettingsDialog.kt`, selecting between `MyUnits.METRIC` and `MyUnits.IMPERIAL` SHALL render fully localized labels across all 9 supported locales without untranslated English unit fragments.
3. *Distinct Bike Power Iconography (`REQ-UI-487.3`)*:
   • Vector asset `res/drawable/bsport_bike_power.xml` SHALL be authored matching the `192x192` viewport / `48dp` geometry of `bsport_bike.xml`, incorporating a distinct power lightning bolt badge in the top-left quadrant using canonical `ic_power` vector geometry.
   • `ActivityType.BIKE_POWER` enum in `com.atrainingtracker.banalservice.ActivityType` SHALL bind `logoId = R.drawable.bsport_bike_power`.
   • `ActivityTypeSelectionDialog.kt` and `CopyTrackingTabDialog.kt` SHALL render `bsport_bike_power` for `ActivityType.BIKE_POWER` and `bsport_bike` for `ActivityType.BIKE_SPEED_AND_CADENCE`, ensuring visual distinction.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-122` (*9-Language Localization & Translation Parity*) and `REQ-UI-149` (*Activity Type Selection Dialog*); establishes `REQ-UI-487` (*Navigation Drawer Units Disambiguation & Distinct Power Cockpit Iconography*).
2. *Historical Origin & Commit Trace*: Commit `2ef1bbdf` (navigation drawer migration) and commit `0e17d2a5` (Compose settings sheets).
3. *Root Reason for Existing Formulation*: Literal 1:1 translation of "Units" -> "Einheiten" created homonym collision with workout sessions; `BIKE_POWER` reused `bsport_bike` because no dedicated multi-color sport vector existed at creation time.
4. *Preservation of Core Invariants*: SQLite table contracts (`TrackingViewsDatabaseManager`), preference keys (`SP_UNITS`), enum names (`ActivityType.BIKE_POWER.name()`), and drawer routes (`NavRoutes.fromDrawerItemId(R.id.drawer_units)`) remain 100% byte-for-byte identical.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Navigation Drawer open in German locale (`de`),
• *When* viewing drawer items under "Einstellungen",
• *Then* the units item displays `"Maßeinheiten"` instead of `"Einheiten"`.
• *Given* `UnitsSettingsDialog` open in German locale (`de`),
• *When* inspecting unit selection options,
• *Then* the dialog title is `"Maßeinheiten"`, metric is `"Metrisch (m)"`, and imperial is `"Imperial (Meilen)"`.
• *Given* `ActivityTypeSelectionDialog` or `CopyTrackingTabDialog` open,
• *When* inspecting the sport icon for `BIKE_POWER` ("Rad Leistung"),
• *Then* it renders `R.drawable.bsport_bike_power` with a prominent power lightning bolt badge, visually distinct from `BIKE_SPEED_AND_CADENCE` ("Radeln").

**Invariants**: SQLite database schema (`TrackingViewsDbHelper`), preference keys (`SP_UNITS`), enum names (`ActivityType.BIKE_POWER.name()`), and 100% test pass rate MUST NOT be broken.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
