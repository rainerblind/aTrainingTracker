# Living Requirements: UI Components & Design System (Part 5 of 6)

**Document ID**: `DOC-REQ-UI-P5`  
**Version**: 1.0.38  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part5`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Requirements**: 56

---

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
     - The radar icon SHALL render at `1.0f` alpha when `RADAR_THREAT_COUNT` is present in `activeSensors` (indicating active BLE/ANT+ radar hardware connected) and at `INACTIVE_SENSOR_ALPHA = 0.38f` when inactive.
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

### REQ-UI-488: Strava Settings Dialog Close Button Elimination & Sync Card Action Icons [Verified]
- **Tests**: `TST-UI-488`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaSettingsDialog.kt`, `AppModalBottomSheet.kt`
- **Rationale**: In `StravaSettingsDialog.kt`, two visual and interaction defects were identified: (1) redundant top-right `✕` close button in the header alongside sticky bottom `Abbrechen` and `Speichern` action buttons (addressed via `AppBottomSheetContent`'s `showCloseButton = (actions == null)` default introduced in `REQ-UI-473`); and (2) manual sync cards (*Equipment*, *Routes*, *Segments*) were rendered as static text blocks in a bare `Column` without trailing action indicators or progress spinners, making interactive triggers look like read-only status displays.
- **Description**:
1. *Close Button Elimination (`REQ-UI-488.1`)*:
   • In `StravaSettingsDialog.kt`, because `actions` is provided (`AppDialogActions.SaveCancel`), `AppBottomSheetContent` SHALL automatically suppress the top-right `✕` close button, leaving the bottom action row as the sole dismissal/confirmation mechanism.
2. *Interactive Manual Sync Card Feedback (`REQ-UI-488.2`)*:
   • The three manual sync `OutlinedCard`s (*Ausrüstung aktualisieren*, *Routen aktualisieren*, *Segmente aktualisieren*) SHALL format their content using a horizontal `Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))`.
   • The left child SHALL be a `Column(modifier = Modifier.weight(1f))` holding the title (`MaterialTheme.typography.titleMedium`) and the timestamp / status subtitle (`MaterialTheme.typography.bodySmall`, `onSurfaceVariant`).
   • The right child SHALL be a trailing feedback container rendering `CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)` when actively syncing (`isEquipmentSyncing`, `isRoutesSyncing`, or `isSegmentsSyncing` is `true`), or `Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)` when idle.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Builds on `REQ-UI-473` (*Modal Bottom Sheet Close Button Elimination*); establishes `REQ-UI-488` (*Strava Settings Dialog Close Button Elimination & Sync Card Action Icons*).
2. *Historical Origin & Commit Trace*: Commit `8058c49e` (ATT-1083 / `REQ-UI-149`) and commit `d92469ec` (ATT-5873).
3. *Root Reason for Existing Formulation*: Manual sync cards initially rendered simple vertical text columns; reactive sync StateFlows only swapped subtitle text without visual icon affordances.
4. *Preservation of Core Invariants*: Manual sync click callbacks, concurrency guards (`if (!isSyncing)`), background sync workers (`StravaEquipmentSyncWorker`, `StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker`), and selective upload preferences remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `StravaSettingsDialog` open with bottom `Abbrechen` and `Speichern` buttons,
• *When* inspecting the header row,
• *Then* zero `✕` close buttons are rendered.
• *Given* an idle Strava sync card (*Equipment*, *Routes*, or *Segments*),
• *When* rendered in `StravaSettingsDialog`,
• *Then* it displays `Icons.Default.Refresh` as a trailing action icon on the right.
• *Given* an active synchronization operation in progress,
• *When* rendered in `StravaSettingsDialog`,
• *Then* the corresponding card displays a 20.dp `CircularProgressIndicator` instead of the refresh icon.

**Invariants**: Strava OAuth 2.0 authentication, worker scheduling, repository sync algorithms, and 100% test pass rate MUST NOT be broken.

### REQ-UI-489: Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting [Verified]
- **Tests**: `TST-UI-489`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `RouteSummaryHeader.kt`, `RouteOnMapScreen.kt`, `RouteSegmentsBreakdownSection.kt`, `ClimbDetector.kt`
- **Rationale**: In the Route Details screen (`RouteOnMapScreen.kt`), UI and localization audits identified four presentation defects: (1) excessive legacy top padding (`44.dp`) in `ElevationProfile.kt` creating an unneeded gap and causing summit climb badges to clip under the toolbar boundary; (2) duplicate upward arrow icon (`ic_ascent`) for both elevation gain and climb count in `RouteSummaryHeader.kt`; (3) redundant breakdown section headers (`Anstiege (N)` / `Segmente (M)`) rendered directly beneath active tab filter chips; and (4) hardcoded `Locale.US` decimal points (`0.7 km @ 3.7%`) in climb titles generated by `ClimbDetector.kt` conflicting with German comma conventions (`3,7%`).
- **Description**:
1. *Elevation Profile Top Clearance & Height Calibration (`REQ-UI-489.1`)*:
   • In `ElevationProfile.kt`, `topPadding` SHALL be declared as `if (showZoomControls) 24.dp else 16.dp`, and `totalCanvasHeight` SHALL be declared as `fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 8.dp else cachedData.adaptiveHeight`. This eliminates the legacy 20dp white space gap reserved for decoupled zoom controls while maintaining at least 24dp top clearance for summit climb category badges (`UC`, `Cat 4`, etc.) at 1.0x font scale without vertical boundary clipping during split-pane scrolling.
2. *Route Summary Header Climb Icon Disambiguation (`REQ-UI-489.2`)*:
   • In `RouteSummaryHeader.kt`, the metric item for climb count (`climbs.size`) SHALL bind `iconRes = R.drawable.ic_altitude` (mountain silhouette) instead of `R.drawable.ic_ascent` (upward arrow), cleanly distinguishing total climb count from cumulative elevation gain.
3. *Route Details Section Heading Deduplication (`REQ-UI-489.3`)*:
   • `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection` SHALL declare an optional parameter `showHeader: Boolean = true`.
   • In both breakdown composables, the section heading `Text` component SHALL be wrapped in `if (showHeader)`.
   • In `RouteOnMapScreen.kt`, when rendering the breakdown sections under the dual-tab filter chip row (`climbs.isNotEmpty() && matchedSegments.isNotEmpty()`), `showHeader` SHALL be passed as `!(climbs.isNotEmpty() && matchedSegments.isNotEmpty())`, suppressing the redundant duplicate section heading beneath the active filter chip.
4. *Locale-Aware Climb Title Grade Formatting (`REQ-UI-489.4`)*:
   • In `ClimbDetector.kt` (`buildClimb`), the generated default climb name SHALL format average grade using `String.format(Locale.getDefault(), "%.1f%%", avgGrade)` instead of hardcoded `Locale.US`.
   • In `RouteClimbsBreakdownSection` in `RouteOnMapScreen.kt`, default generated climb titles SHALL dynamically format the average gradient matching the active runtime locale and user preferences.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-225` (*Decoupled Global Telemetry Zoom Toolbar & Chart Spacing*), `REQ-UI-274` (*Dynamic Elevation Profile Top Clearance & Container Height Stability*), `REQ-UI-302` (*Route Details Segment & Climb Tabs*), `REQ-UI-308` (*Route POI Overlay & Waypoint Filtering*), and `REQ-UI-387` (*Localized Climb Titles & Metric Formatter Parity*); establishes `REQ-UI-489` (*Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting*).
2. *Historical Origin & Commit Trace*: Commit `2b3cb559` (ATT-2386 zoom decoupling), commit `6eaeb507` (ATT-3453 climb badge clearance), commit `0937a091` (ATT-3575 climb title localization), and commit `b24a3e9c` (ATT-2583 / ATT-2763 route tabs).
3. *Root Reason for Existing Formulation*: Legacy 44dp topPadding accommodated internal on-canvas zoom buttons prior to external toolbar extraction; duplicate `ic_ascent` was reused for climb count because no dedicated terrain icon was assigned; unconditional breakdown headers were authored prior to the introduction of dual-tab filter chips; hardcoded `Locale.US` in `ClimbDetector` was left during initial detector prototyping.
4. *Preservation of Core Invariants*: Climb detection algorithms (`REQ-MAP-027`), segment matching, map polyline overlays, scrubbing overlay synchronization, 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Elevation Profile rendered in detail view (`showZoomControls = true`),
• *When* inspecting `topPadding` and `totalCanvasHeight`,
• *Then* `topPadding` is `24.dp` and `totalCanvasHeight` adds `8.dp` over `cachedData.adaptiveHeight`, eliminating the 20dp empty white gap while keeping summit climb badges completely visible without clipping.
• *Given* the Route Details summary header with climbs present,
• *When* rendered on screen,
• *Then* total elevation gain displays `R.drawable.ic_ascent` (upward arrow) and climb count displays `R.drawable.ic_altitude` (mountain silhouette).
• *Given* a route with both climbs and matched segments (`climbs.isNotEmpty() && matchedSegments.isNotEmpty()`),
• *When* the user views the route details screen,
• *Then* the breakdown section displays filter chips `[Anstiege (N)] [Segmente (M)]` and suppresses the duplicate text heading immediately below the chips.
• *Given* the application running in German locale (`de`),
• *When* an automated climb is detected or displayed,
• *Then* its grade in the title row is formatted with a localized comma (e.g., `3,7%`) rather than an English dot (`3.7%`).

**Invariants**: Climb categorization thresholds (`CAT_4`..`HC`), route point coordinates, split-pane dragging, scrubbing overlay synchronization, 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT, and 100% test pass rate MUST NOT be broken.

### REQ-UI-490: Workout Cluster Trend Chart & Dashboard Polish: Label Collision Resolution, Average Background Badge, Time-Range Filtering & Thousands Grouping [Verified]
- **Tests**: `TST-UI-490`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterHeatmapScreen.kt`, `DistanceFormatter.java`, `AltitudeFormatter.java`
- **Rationale**: In `WorkoutClusterDetailDashboard` (`WorkoutClusterHeatmapScreen.kt` & `WorkoutClusterTrendChartCard.kt`), UI audits of high-volume clusters (e.g. `Bike2Work`) revealed five readability and layout defects: (1) average label `Ø ...` drawn without a background pill directly over dense trend lines and data markers; (2) bottom Y-axis boundary label colliding vertically with X-axis start date at `x = leftPadding`; (3) redundant card title `📈 Leistungsverlauf` repeating the selected tab button; (4) lack of time-range filtering for multi-year clusters; and (5) lack of localized thousands separators on cumulative statistics totals (`136730 m`, `4226 km`).
- **Description**:
1. *Canvas Text Separation & Left Gutter (`REQ-UI-490.1`)*:
   • In `WorkoutClusterTrendChartCard.kt`, Y-axis boundary labels (`yAxisTopLabel` and `yAxisBottomLabel`) SHALL be positioned in a dedicated left gutter (`leftPadding = 48.dp.toPx()`) rather than overlapping the start date.
   • `xAxisStartLabel` and `xAxisEndLabel` SHALL be drawn exclusively along the bottom margin below the plot canvas.
2. *Average Reference Line Badge (`REQ-UI-490.2`)*:
   • The average label `Ø ...` SHALL be rendered with a rounded background container badge (`surfaceColor` with subtle fill) drawn on top of the trend lines and points, guaranteeing text readability regardless of underlying data marker density.
3. *Header Deduplication & Time-Range Filter Chips (`REQ-UI-490.3`)*:
   • The redundant text header `📈 Leistungsverlauf` inside `WorkoutClusterTrendChartCard` SHALL be replaced with a compact time-range filter chip group (`FilterChip` for `1J` / `3J` / `Alle`, localized across all 9 locales without emojis) docked to the left of the `[Dauer | Tempo]` selector row.
   • When a time range is selected, the chart points SHALL filter relative to the latest workout timestamp (`latestTimeS - spanS`), automatically falling back to all points if fewer than 2 workouts match the selected window.
4. *Cumulative Volume Localized Thousands Grouping (`REQ-UI-490.4`)*:
   • Large integer meter/kilometer totals formatted via `DistanceFormatter.java` and `AltitudeFormatter.java` SHALL use locale-aware grouping formats (`%,.0f`) so cumulative totals render with localized grouping separators (e.g. `136.730 m`, `4.226 km` in German).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters*), `REQ-UI-225`, and Rule 36 formatters (`DistanceFormatter`, `AltitudeFormatter`).
2. *Historical Origin & Commit Trace*: Commit `feat(ATT-4067)` (Sprint 2026-42.10) and `feat(ATT-4287)`.
3. *Root Reason for Existing Formulation*: Initial trend card placed both `yAxisBottomLabel` and `xAxisStartLabel` at `x = leftPadding`, without anticipating collision or time-range filtering on clusters with hundreds of workouts; formatters used `%.0f` without grouping specifier `,`.
4. *Preservation of Core Invariants*: "Faster-is-higher" coordinate inversion, PR markers, nearest-point touch selection, $N \ge 2$ threshold, and 9-language localization parity without decorative emojis (`ATT-5400`) MUST remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutClusterTrendChartCard` rendering a cluster with multiple workouts,
• *When* the average label `Ø ...` is drawn on canvas,
• *Then* it renders on top of a rounded surface background badge, ensuring complete text legibility over dense trend lines and data markers.
• *Given* `WorkoutClusterTrendChartCard` with start date and bottom Y-axis label,
• *When* rendered on canvas,
• *Then* the bottom Y-axis label is rendered in the left gutter and the start date along the bottom margin without visual collision.
• *Given* the toolbar header row of `WorkoutClusterTrendChartCard`,
• *When* viewed in `WorkoutClusterDetailDashboard`,
• *Then* the redundant `📈 Leistungsverlauf` heading is replaced by time-range filter chips (`1J | 3J | Alle`) docked left of the `[Dauer | Tempo]` toggle.
• *Given* the cumulative volume card in the Statistics tab,
• *When* displaying totals $\ge 1.000$,
• *Then* cumulative distance and elevation are formatted with locale-aware thousands grouping (e.g., `4.226 km` and `136.730 m` in German).

**Invariants**: 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT; cluster similarity calculations; database schema backward compatibility; 100% test pass rate.

### REQ-UI-491: MapDetailLayout Status Bar Inset Clearance for Collapsed Header Floating Controls [Verified]
- **Tests**: `TST-UI-491`, `TST-UI-500`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `MapDetailLayoutCollapsingHeaderContractTest.kt`
- **Rationale**: In full-screen detail views utilizing `MapDetailLayout` with `useStatusBarsPadding == true` (Favorite Routes, Route Details, Segment Details, Climbs), scrolling down collapses the top header offscreen, causing `currentTopPaddingPx` to reach `0`. While `ATrainingTrackerMap` bleeds edge-to-edge behind the status bar, top-aligned floating action buttons (the Share button and `overlay()` composables) translate into the system status bar, overlapping system status bar indicators (clock, 5G, battery).
- **Description**:
1. *Dynamic Floating Controls Top Inset (`REQ-UI-491.1`)*:
   • In `MapDetailLayout.kt`, when `useStatusBarsPadding == true`, dynamic status bar clearance `floatingControlsTopOffsetDp = with(density) { (statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp() }` SHALL be computed, where `statusBarHeightPx = WindowInsets.statusBars.getTop(density)`.
   • When `useStatusBarsPadding == false` (e.g. bottom sheets), `statusBarHeightPx` MUST evaluate to `0`, ensuring `floatingControlsTopOffsetDp == 0.dp`.
2. *Floating Controls Container Clearance (`REQ-UI-491.2`)*:
   • In `mapBox`, the slotted `overlay()` content and the floating Share button SHALL be wrapped in a container applying `Modifier.fillMaxSize().padding(top = floatingControlsTopOffsetDp)`.
   • When the header collapses (`currentTopPaddingPx == 0`), floating action controls SHALL remain offset below the status bar (`statusBarHeight + defaultPadding`), while `ATrainingTrackerMap` continues to render edge-to-edge behind the translucent status bar.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-250` (*Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll in MapDetailLayout*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-2150` (commit `e2b80b2d`) for collapsing header behavior in `MapDetailLayout`.
3. *Root Reason for Existing Formulation*: `currentTopPaddingPx` was allowed to drop to `0` to give the map full-screen bleed, but overlooked status bar collision for top-aligned floating action buttons inside `mapBox`.
4. *Preservation of Core Invariants*: Full-screen map bleed under the transparent status bar is 100% preserved; uncollapsed `16.dp` button spacing below header is preserved; bottom sheet layouts (`useStatusBarsPadding == false`) remain unaltered (`floatingControlsTopOffsetDp == 0.dp`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `MapDetailLayout` with `useStatusBarsPadding == true` and an expanded header,
• *When* the header is fully expanded (`connection.appBarOffset == 0`),
• *Then* `floatingControlsTopOffsetDp` evaluates to `0.dp`, preserving the default `16.dp` spacing below the header bottom edge.
• *Given* `MapDetailLayout` with `useStatusBarsPadding == true`,
• *When* the user scrolls and the header collapses completely (`connection.appBarOffset == -headerHeightPx`),
• *Then* `floatingControlsTopOffsetDp` evaluates to `statusBarHeightDp`, preventing the Share button and overlay action buttons from colliding with Android system status bar icons.
• *Given* `MapDetailLayout` configured as a bottom sheet (`useStatusBarsPadding == false`),
• *When* scrolling or expanding,
• *Then* `floatingControlsTopOffsetDp` remains strictly `0.dp`.

**Invariants**: Edge-to-edge map bleed under status bar is preserved; no changes to public API of `MapDetailLayout`; bottom sheet mode behavior is preserved; 100% test pass rate.

### REQ-UI-492: Equipment & Sensors UI: Strava Gear Deduplication, Date/Frame-Type Localization, Placeholder Harmonization & Sensor Detail Status Dot Alignment [Verified]
- **Tests**: `TST-UI-492`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentTabsScreen.kt`, `BikeTypeSelector.kt`, `EditEquipmentDialog.kt`, `EditDeviceDialog.kt`, `DeviceData.kt`, `values*/strings.xml`
- **Rationale**: Live audits of `Räder & Schuhe` and `Sensoren` identified four user-facing defects: (1) redundant repetition of identical Strava gear names in subtitle rows, (2) unlocalized raw ISO usage dates (`YYYY-MM-DD`) and hardcoded English bike frame types ("Road", "Time Trial"), (3) German compounding typo `Rad Typ` and asymmetric dropdown placeholders ("Keine" vs. "Keine verknüpften Sensoren"), and (4) sensor detail dialog showing a green status LED while displaying "100% (nicht verbunden)", uncapitalized internal smartphone manufacturers ("samsung", "google"), and an unstyled bare `TextButton` for equipment linking.
- **Description**:
1. *Strava Gear Name Deduplication (`REQ-UI-492.1`)*:
   • In `EquipmentTabsScreen.kt`, when an equipment item's `stravaName` is non-blank and identical (case-insensitive) to its primary `name`, the card SHALL render an inline `StravaOriginalLogo` badge in the title row and SHALL suppress the duplicate subtitle line.
   • When `stravaName` differs from `name`, the distinct Strava gear name SHALL continue to render in the subtitle row with `StravaOriginalLogo`.
2. *Usage Date & Frame-Type Localization (`REQ-UI-492.2`)*:
   • In `UsageItem`, `firstUsed` and `lastUsed` date strings SHALL be formatted via `DateTimeUtils.formatIsoDate(date)` adhering to the active locale (`FormatStyle.MEDIUM`).
   • Bike frame types (1: MTB, 2: Cross, 3: Road, 4: Time Trial) SHALL be resolved via localized string resources (`equipment_bike_type_mtb`, `cross`, `road`, `tt`) across all 9 locales (e.g. "Rennrad", "Zeitfahrrad" in German) in both `EquipmentTabsScreen.kt` and `BikeTypeSelector.kt`.
3. *Spelling & Dropdown Placeholder Harmonization (`REQ-UI-492.3`)*:
   • In `values-de/strings.xml`, `equipment_bike_type` SHALL be corrected to `Radtyp` and `equipment_select_bike_type` to `Radtyp auswählen`.
   • In `EditEquipmentDialog.kt`, the empty placeholder for linked sensors SHALL be harmonized with linked sport types to display compact "Keine" / "None" (`R.string.equipment_no_sports_linked`).
4. *Sensor Detail Dialog Status Dot, Capitalization & Dropdown Styling (`REQ-UI-492.4`)*:
   • In `EditDeviceDialog.kt`, the technical status LED dot color SHALL be strictly synchronized with `DeviceStatusRow` connection state (`liveData?.isConnected ?: data.isConnected`), displaying `TTColor.ConnectionStatusGreen` when connected and neutral gray (`Color.LightGray`) when disconnected.
   • In `DeviceData.kt:raw2UiDeviceData`, raw Android manufacturer strings (e.g. `Build.BRAND`) SHALL be capitalized (e.g. "Samsung", "Google") via titlecase formatting.
   • In `EditDeviceDialog.kt:EquipmentSection`, the linked equipment selector SHALL be styled as an `OutlinedTextField` with dropdown affordance (`trailingIcon`) matching standard dialog inputs.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-492`, refining `REQ-UI-061` (*Universal List Card Contracts*), `REQ-UI-149` (*Modal Bottom Sheet Dialogs*), `REQ-UI-256` (*Equipment Management UI*), and `REQ-UI-283` (*Sensor Management*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-2126` (`commit fc10ad81`) and `ATT-2382` during equipment card and sensor management implementation.
3. *Root Reason for Existing Formulation*: Strava subtitle was unconditionally rendered to confirm external provenance, overlooking 1:1 name matches; frame types were hardcoded during rapid prototyping; and status LED was bound independently from `DeviceStatusRow`.
4. *Preservation of Core Invariants*: Equipment-to-sensor relationships (`REQ-UI-256`), database schemas (`Equipment.db`, `Devices.db`), swipe-to-delete card gestures (`REQ-UI-061`), 9-locale parity (`REQ-LOC-001`), and 100% test pass rate MUST NOT be compromised.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an equipment item where `stravaName` matches `name` (e.g. "Brooks Green Silence"),
• *When* rendered in `EquipmentTabsScreen`,
• *Then* an inline Strava logo badge is displayed next to the title and no duplicate subtitle line is shown.
• *Given* an equipment item with `firstUsed` or `lastUsed` ISO dates,
• *When* rendered in German locale,
• *Then* dates are formatted as `DD.MM.YYYY` (e.g. `10.01.2019`) rather than raw ISO `YYYY-MM-DD`.
• *Given* a bike with frame type 3 (Road) or 4 (Time Trial) in German locale,
• *When* viewed in `EquipmentTabsScreen` or `BikeTypeSelector`,
• *Then* the frame type displays as "Rennrad" or "Zeitfahrrad" and the selector label reads "Radtyp".
• *Given* a sensor that is disconnected in `EditDeviceDialog`,
• *When* the dialog is displayed,
• *Then* the status indicator dot is gray (`Color.LightGray`), the status text reads `(nicht verbunden)`, and smartphone manufacturers like "samsung" or "google" are capitalized to "Samsung" or "Google".

**Invariants**: Equipment and sensor database schemas remain unchanged; Bluetooth/ANT+ background connectivity pipelines remain unaffected; 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT; 100% test pass rate.

### REQ-UI-493: ClusterItem & Filter (Lieblingsstrecken): Hit Count Display Healing, Bidirectional Location Context, Landscape Thumbnail & Filter Sheet Reset Action [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-493`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterRepository.kt`, `FilterBottomSheetScaffold.kt`, `strings.xml` (all 9 locales)
- **Rationale**: In `Lieblingsstrecken` cluster management (`WorkoutClusterComponents.kt`, `WorkoutClusterRepository.kt`, and `FilterBottomSheetScaffold.kt`), UI audits revealed four presentation and telemetry defects: (1) cluster cards display `0 Aufzeichnungen` when `RouteClusters.hitCount` is desynchronized from `WorkoutSummaries`; (2) cluster cards only display start locations and fail to resolve destination coordinates (`endLat`, `endLng`); (3) `110.dp × 75.dp` map thumbnails with `newLatLngBounds(..., 40)` leave unused horizontal whitespace beside `~117.dp`-tall metadata on `411.dp` phones while `Google` watermark and Start/End/Apex markers obscure the polyline; and (4) the filter bottom sheet reset button label ("Alle zurücksetzen") wraps onto two lines in the 50% split button row.
- **Description**:
1. *Hit Count Display Healing & Repository Reconciliation (`REQ-UI-493.1`)*:
   • In `WorkoutClusterComponents.kt` (`WorkoutClusterMetadataBlock`), the displayed recordings count SHALL be calculated from `maxOf(cluster.hitCount, stats?.workoutCount ?: 0)` (falling back to `cluster.hitCount`), guaranteeing that cluster cards display the accurate number of recorded workouts even if the cached `hitCount` in SQLite is zero.
   • In `WorkoutClusterRepository.kt` (`loadAllClustersLightweight`), when clusters are loaded and `_clusterStats` is hydrated from `WorkoutSummaries`, any cluster with `hitCount == 0` that has associated workouts in summary stats SHALL have its `hitCount` healed and updated.
2. *Bidirectional Route Endpoint Location Context (`REQ-UI-493.2`)*:
   • `WorkoutClustersViewModel` SHALL expose a general location lookup `getLocationName(lat: Double, lng: Double): String?` resolving recognized locations via `KnownLocationsDatabaseManager`.
   • In `WorkoutClusterComponents.kt` (`WorkoutClusterMetadataBlock`), both start coordinates (`startLat`, `startLng`) and destination coordinates (`endLat`, `endLng`) SHALL be resolved.
   • If start and destination resolve to the same known location, the UI SHALL display `Start & Ziel: [Location]` (`R.string.workout_start_and_destination`).
   • If start and destination resolve to different known locations, the UI SHALL display `[Start] → [Ziel]`.
   • If only one endpoint resolves to a known location, the UI SHALL display `Start: [Start]` (`R.string.cluster_start_location`) or `Ziel: [Ziel]` (`R.string.workout_destination_location`).
3. *Landscape Map Thumbnail, Camera Framing & Equipment Text Constraining (`REQ-UI-493.3`)*:
   • In `WorkoutClusterComponents.kt`, both `ClusterItem` and `UnclusteredWorkoutItem` SHALL render the right-hand route preview map `Surface` at `175.dp × 120.dp` (`Modifier.size(width = 175.dp, height = 120.dp)` with `12.dp` rounded corners on standard `>= 387.dp` screens), paired with a `12.dp` horizontal `Spacer` separating `WorkoutClusterMetadataBlock` / workout metadata from the map thumbnail, while preserving `GoogleMapOptions().liteMode(true)`, `DarkMapStyle.resolveMapProperties`, and `DarkMapAntiFlashOverlay`.
   • On narrow screens (e.g., `360.dp` viewport / `< 387.dp` screen width), the thumbnail width MUST clamp responsively so the left metadata column retains at least `160.dp` width; this width calculation MUST NOT place `BoxWithConstraints` (`SubcomposeLayout`) inside `Row(Modifier.height(IntrinsicSize.Min))` (derive width from `LocalConfiguration.current.screenWidthDp` or measure outside the `IntrinsicSize.Min` `Row`).
   • `CameraUpdateFactory.newLatLngBounds(bounds, ...)` SHALL use a named `16–20 px` padding constant (e.g., `THUMBNAIL_BOUNDS_PADDING_PX` in `16..20`, replacing literal `40` and removing the corresponding `MagicNumber` entry from `app/detekt-baseline.xml`), wrapped in a `try/catch` guard against unlaid-out / zero-size map layout exceptions (`IllegalStateException`), and `rememberCameraPositionState` SHALL optionally seed its initial `CameraPosition` to `bounds.center` when `bounds` is non-null to suppress world-zoom `(0, 0)` flash prior to `onMapLoaded`.
   • In `WorkoutClusterMetadataBlock` and `UnclusteredWorkoutItem`, the linked equipment string SHALL enforce `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
   • **Localization Parity**: `N/A - no UI strings` (zero `values*/strings*.xml` edits for the `ATT-8748` thumbnail enlargement; `REQ-UI-493.4` 9-locale `filter_reset` parity remains intact).
4. *Single-Line Filter Sheet Reset Button Action (`REQ-UI-493.4`)*:
   • In `FilterBottomSheetScaffold.kt`, the reset action button SHALL use the concise localized resource `R.string.filter_reset` ("Zurücksetzen" / "Reset") with `maxLines = 1` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT), guaranteeing a uniform single-line height matching the adjacent "OK" button in 50% split button rows.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-493.3` (`ATT-5846`, commit `c3c957e2`) alongside `REQ-SET-067` (*Auto-Naming & Hit Counting*), `REQ-UI-186` (*Recognized Start Location Badges*), `REQ-UI-244` (*Cluster Map Thumbnails*), and `REQ-UI-149` (*Filter Bottom Sheets*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-714`, `ATT-1402`, `ATT-2276`, and refined in `c3c957e2` (`ATT-5846`, which replaced `100.dp × 100.dp` thumbnails with `110.dp × 75.dp`).
3. *Root Reason for Existing Formulation*: `110.dp × 75.dp` (`ATT-5846`) preserved the prior `~100.dp` horizontal footprint, leaving `~65.dp` unused horizontal whitespace beside `~117.dp`-tall metadata on `411.dp` phones while `newLatLngBounds(..., 40)` compressed route polylines under the Google watermark and Start/End/Apex markers.
4. *Preservation of Core Invariants*: `GoogleMapOptions().liteMode(true)`, `DarkMapAntiFlashOverlay`, `DarkMapStyle.resolveMapProperties`, TopStart Merge/Delete `DropdownMenu`, `IntrinsicSize.Min` bottom alignment (zero `BoxWithConstraints` inside `IntrinsicSize.Min`), `maxLines = 1` equipment ellipsis, `filter_clear_all` / `filter_reset` 9-locale parity, zero-touch auto-naming counter integrity, and `KnownLocationsScreen.kt` (*Lieblingsorte*, `ATT-8749`) isolation MUST NOT be compromised.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a cluster with `hitCount = 0` but having matching workouts in `WorkoutSummaries`,
• *When* displayed in `ClusterItem` or `WorkoutClusterDetailDashboard`,
• *Then* the recordings badge displays the non-zero count matching `stats.workoutCount`.
• *Given* a cluster where start and end coordinates match the same known location (e.g. "Home"),
• *When* rendered in `WorkoutClusterMetadataBlock`,
• *Then* the location badge reads "Start & Ziel: Home".
• *Given* a cluster where start coordinates resolve to "Home" and destination coordinates resolve to "Work",
• *When* rendered in `WorkoutClusterMetadataBlock`,
• *Then* the location badge reads "Home → Work".
• *Given* `ClusterItem` or `UnclusteredWorkoutItem` rendered on a standard `411.dp` phone viewport,
• *When* the card layout composes,
• *Then* the map preview `Surface` measures `175.dp` width by `120.dp` height with a `12.dp` horizontal spacer, multi-gear equipment strings truncate with an ellipsis on a single line, and `newLatLngBounds` frames the route using the named `16–20 px` padding constant with bounds-center camera seeding and `try/catch` safety.
• *Given* `ClusterItem` or `UnclusteredWorkoutItem` rendered on a narrow `360.dp` phone viewport,
• *When* the card layout composes,
• *Then* the thumbnail width clamps so the left metadata column retains at least `160.dp` width without throwing any `IntrinsicSize.Min` / `SubcomposeLayout` exception.
• *Given* `FilterBottomSheetScaffold` displayed in German locale,
• *When* inspecting the bottom action bar,
• *Then* the reset button displays "Zurücksetzen" on a single line with uniform height matching the "OK" button.

**Invariants**: MapView `liteMode(true)` and dark map anti-flash styling preserved; TopStart Merge/Delete `DropdownMenu` preserved; `IntrinsicSize.Min` bottom alignment preserved without `BoxWithConstraints` inside `Row`; auto-naming counter integrity preserved; 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT without emojis (`Localization Parity: N/A - no UI strings` for `ATT-8748`); 100% test pass rate.

### REQ-UI-494: ElevationProfile Y-Axis Grid Clearance & Zero-Altitude Profile Suppression in RouteItem [Verified]
- **Tests**: `TST-UI-494`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `RouteItem.kt`
- **Rationale**: In `ElevationProfile` and `RouteItem`, UI audits of the Routes screen (`screen_04_routen.png`) identified two presentation defects: (1) intermediate Y-axis elevation grid labels (e.g. `400`) overlap the bottom minimum altitude label (`383 m`) due to omitting the `+ 10f` baseline offset in vertical clearance calculations and using a fixed hardcoded `-110f` X-offset; and (2) flat GPX routes lacking altitude data (e.g. `bike2work`, `↑ 0 m`) render a meaningless synthetic `-10 m` to `10 m` flat green elevation profile in `RouteItem`.
- **Description**:
1. *Y-Axis Intermediate Label Baseline Clearance Guard (`REQ-UI-494.1`)*:
   • In `ElevationProfile.kt`, the vertical clearance evaluation for intermediate Y-axis labels SHALL incorporate the drawn label baseline offset (`labelBaselineY = y + 10f`).
   • An intermediate altitude label SHALL only be rendered if its baseline maintains a clearance of at least `textPaint.textSize * 1.35f` from the bottom canvas boundary (`height`), from the top `maxAltLabel` boundary (`highlightPaint.textSize`), and from the previously rendered intermediate tick (`Math.abs(y - lastY)`).
2. *Right-Aligned Intermediate Elevation Labels (`REQ-UI-494.2`)*:
   • Intermediate Y-axis elevation labels SHALL NOT use a fixed `-110f` X-offset.
   • Intermediate labels SHALL be right-aligned dynamically to `-textPaint.measureText(altText) - 10f`, matching the visual right alignment of `minAltLabel` and `maxAltLabel`.
3. *Zero-Altitude Elevation Profile Suppression in RouteItem (`REQ-UI-494.3`)*:
   • In `RouteItem.kt`, `ElevationProfile` SHALL only be rendered when authentic altitude data is available (`hasElevationData = pathPoints.any { it.altitude != null && !it.altitude.isNaN() && Math.abs(it.altitude) > 1e-3 } || summary.elevationGain > 0.0`).
   • For flat GPX routes or tracks with zero elevation gain and null/zero altitude points, the `ElevationProfile` composable SHALL be suppressed, preventing display of the synthetic minimum vertical span (`-10 m` to `10 m`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-494`, refining `REQ-UI-197` (*Elevation Profile Layout Architecture*) and `REQ-UI-225` (*Route Item Layout*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-508` (bounds calculation) and `ATT-2463` (`RouteItem` layout).
3. *Root Reason for Existing Formulation*: The clearance check in `ElevationProfile` checked `height - y` before the `+ 10f` baseline shift was introduced for vertical text centration on grid lines; `RouteItem` assumed all imported GPX routes would contain elevation data.
4. *Preservation of Core Invariants*: `ATT-5853` top padding (`24.dp` / `16.dp`) and climb badge clearance invariants, chart zoom/pan math, full-screen route detail elevation analysis (`MapDetailLayout`), and authentic route elevation visualization MUST NOT be compromised.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation profile with minimum altitude `383 m` and an intermediate tick at `400 m` whose drawn baseline would fall within `textPaint.textSize * 1.35f` of `height`,
• *When* the profile canvas is drawn,
• *Then* the colliding intermediate label `400` is suppressed, leaving `383 m` legible without overlap.
• *Given* intermediate altitude labels rendered on the Y-axis,
• *When* drawn on the canvas,
• *Then* each label is right-aligned to `-textPaint.measureText(altText) - 10f`.
• *Given* a route item with zero elevation gain (`elevationGain == 0.0`) and path points with null or zero altitude,
• *When* the route card is rendered in the routes list,
• *Then* the elevation profile is hidden completely rather than showing a `-10 m` to `10 m` synthetic bar.

**Invariants**: `ATT-5853` top padding and climb badge clearance invariants preserved; full-screen elevation detail inspection in `MapDetailLayout` preserved; authentic elevation profiles render without regression; 100% test pass rate.

---

### REQ-UI-2795: Workout Details Tab Section Order Compliance for ELEVATION and CHARTS (`ATT-9508`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2795`
- **System Requirement(s)**: Derived (SW-only — Compose tab-ordering bugfix refining `REQ-UI-408.3` and `REQ-UI-408.5`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDetailSectionOrderUiTest.kt`
- **Localization Parity**: `N/A - 0 new or modified UI strings`
- **Rationale**: Observed during PO on-device review (Pixel 10, build `5.0.0-dev-c9b5619a`) while testing `ATT-9066`: moving `WorkoutDetailSectionType.CHARTS` (*Telemetrie-Diagramme*) or `WorkoutDetailSectionType.ELEVATION` (*Höhenprofil*) in the Workout Details tab-outline editor had no effect on rendered order because `WorkoutDetailTabbedContent.kt` filtered `ELEVATION` and `CHARTS` out of `nonMapNonGraphSections` while `MapDetailLayout.kt` always rendered them at fixed top slots above `analyticsContent`.
- **Description**:
  1. *Scoped Lower-Pane Slot in `MapDetailLayout` (`REQ-UI-2795.1`)*:
     - `MapDetailLayout` (`MapDetailLayout.kt`) SHALL expose `interface MapDetailLowerContentScope` with `ElevationProfileSection(modifier: Modifier = Modifier)` and `TelemetryChartsSection(modifier: Modifier = Modifier, applyLandscapeToolbarTopPadding: Boolean = false)`, and an optional defaulted parameter `customLowerContent: (@Composable MapDetailLowerContentScope.() -> Unit)? = null`.
     - When `customLowerContent == null`, `MapDetailLayout` SHALL preserve 100% identical layout, snapshot capture, and bottom-inset behavior for all legacy callers (`RouteOnMapScreen`, `SegmentOnMapScreen`, `ClimbOnMapScreen`, `LiveSegmentSheet`, `WorkoutClusterHeatmapScreen`, `MapScreenWithTrack`, legacy `TrackOnMapScreen`).
     - When `customLowerContent != null`, `MapDetailLayout` SHALL render the caller's custom-ordered sections inside the single shared scrollable lower pane wrapped with `navigationBarsPadding()` (`Rule 41`) and recorded by `elevationLayer` (`REQ-REV-015`).
  2. *Configured Section Ordering Across `MODE_A_CARD_SCROLL` and `MODE_B_SPLIT_MAP` (`REQ-UI-2795.2`)*:
     - `WorkoutDetailTabbedContent` (`WorkoutDetailTabbedContent.kt`) SHALL render all active non-`MAP` sections — including `WorkoutDetailSectionType.ELEVATION` (when `hasAltitudeData`) and `WorkoutDetailSectionType.CHARTS` — in the exact relative order defined by `tab.normalizedSections` in both `MODE_A_CARD_SCROLL` and `MODE_B_SPLIT_MAP`, while keeping `MAP` strictly pinned in the top split pane in `MODE_B_SPLIT_MAP` (`REQ-UI-408.3`) and preserving single-section `[MAP, ELEVATION]` / `[ELEVATION]` intrinsic layout.
     - In Landscape mode with `GlobalTelemetryZoomToolbar` floating at `Alignment.TopEnd`, `TelemetryChartsSection` SHALL apply top toolbar clearance (`applyLandscapeToolbarTopPadding = (index == 0)`) only when `CHARTS` is the first section in the lower pane.
  3. *Preservation of Synchronized Scrubbing, Overlays, Visibility Toggles & Gesture Isolation (`REQ-UI-2795.3`)*:
     - Interleaved `ELEVATION` and `CHARTS` sections SHALL share a single `selectedDistance`, `profileZoomScale`, `viewportStartFraction`, `isPanMode`, `activeAltitudeOverlays`, `hiddenCharts` / `onToggleChartVisibility`, and `onInteractiveCanvasTouch` (`PointerEventPass.Initial` reset on `lowerColumn` with child `chartInteractiveTouchDetector` plot override), preserving `HorizontalPager` gesture isolation (`REQ-UI-408.4` / `ATT-5826`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a Workout Details tab in `MODE_A_CARD_SCROLL` configured with `[DETAILS, ELEVATION, ZONES, CHARTS]` or `[CHARTS, ELEVATION]`, *When* the tab renders, *Then* each section's vertical `boundsInRoot.top` matches the exact configured relative order (`detailsTop < elevationTop < zonesTop < chartsTop` and `chartsTop < elevationTop`).
  • *Given* a Workout Details tab in `MODE_B_SPLIT_MAP` configured with `[DETAILS, CHARTS, MAP, ZONES, ELEVATION]`, *When* the tab renders, *Then* `MAP` remains pinned in the top split pane (`detailsTop > 250.dp`) and the lower scrollable pane renders in exact configured order (`detailsTop < chartsTop < zonesTop < elevationTop`).
  • *Given* custom-ordered `[DETAILS, ELEVATION, CHARTS]` in a multi-tab `HorizontalPager`, *When* the user touches `DETAILS`, scrubs inside `ELEVATION`, and toggles a chart via `charts_visibility_button`, *Then* `DETAILS` keeps `HorizontalPager` swipe unlocked, scrubbing inside `ELEVATION` locks `HorizontalPager` and displays `scrubbing_telemetry_badge`, releasing restores pager swipe, and toggling `SPEED_PACE` updates `hiddenCharts` cleanly.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-UI-408.3` / `REQ-UI-408.5` (`ATT-3920`) and `REQ-UI-408.4` (`ATT-5826`), targeting `MapDetailLayout.kt` and `WorkoutDetailTabbedContent.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-3920` (`ea5fa705`) reused `MapDetailLayout`'s fixed elevation/charts + `analyticsContent` slots when introducing multi-tab Workout Details; `ATT-5826` added `chartInteractiveTouchDetector` and `lowerColumn` `PointerEventPass.Initial` arbitration.
  3. *Root Reason for Existing Formulation*: `MapDetailLayout` predated configurable section order (`RouteOnMapScreen`/`SegmentOnMapScreen`), so `WorkoutDetailTabbedContent` delegated graph rendering to fixed boolean flags (`showElevationProfile`, `showTelemetryCharts`) and passed only remaining cards via `analyticsContent`.
  4. *Preservation of Core Invariants*: Backward-compatible `MapDetailLayout` (`customLowerContent = null`), `MAP` split-pane pinning at index `0`, synchronized scrubbing & zoom across Map/`ElevationProfile`/`TelemetryChartStack`, `Rule 64` plot-width alignment, `Rule 41` `navigationBarsPadding()`, `REQ-REV-015` offscreen snapshot capture, `<= 1,500` LoC per file, and `0` string edits.

---

### REQ-UI-2796: Synchronized Activity & Carriage Mode Bar Below Map Coupled with Scrubbing & Zoom (`ATT-9172`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2796`
- **System Requirement(s)**: Derived (SW-only — Compose visualization of existing `ACTIVITY_AND_CARRIAGE_MODE` telemetry from `REQ-PER-045` / `REQ-FIL-028`, extending `REQ-UI-408` and `REQ-UI-2795`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ActivityCarriageSyncBar.kt`, `MapDetailLayout.kt`, `ActivityCarriageTimelineCard.kt`, `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `values*/strings_workout_details.xml`
- **Localization Parity**: `9/9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) via values*/strings_workout_details.xml`
- **Rationale**: Athletes reviewing workouts in `Karte & Diagramme` need a spatially synchronized, color-coded Activity & Carriage Mode bar directly below the map and coupled 1:1 with the scrubbing cursor, map marker, zoom/pan viewport, and `DISTANCE`/`TIME` X-axis domains while keeping `Höhenprofil` (`ELEVATION`) independently configurable (`ATT-9172`).
- **Description**:
  1. *Synchronized Segmented Bar & Exact `Rule 64` Plot Alignment (`REQ-UI-2796.1`)*:
     - `ActivityCarriageSyncBar` (`ActivityCarriageSyncBar.kt`) SHALL render a compact, color-coded horizontal segmented bar using canonical `TTColor.getCarriageModeColor(mode)` and `carriageModeIcon(mode)` shared with `ActivityCarriageTimelineCard.kt`, matching `ElevationProfile` and `TelemetryMetricGraph` horizontal plot geometry pixel-for-pixel (`startPaddingPx = 50.dp`, portrait `endPaddingPx = 52.dp` when aligned with histograms, landscape `0.72f`/`0.28f` split).
     - `ActivityCarriageBarMath.projectSegmentsToDomain(...)` SHALL project segments accurately across both `ProfileXAxisDomain.DISTANCE` (preserving zero-distance stationary stops as crisp markers) and `ProfileXAxisDomain.TIME` in lockstep with `MapDetailLayout`.
  2. *1:1 Bidirectional Scrubbing, Active-Mode Readout & Zoom/Pan Coupling (`REQ-UI-2796.2`)*:
     - Dragging or tapping along `ActivityCarriageSyncBar` SHALL update `MapDetailLayout`'s shared `selectedDistance`, moving the map marker and vertical scrub cursors across `ElevationProfile` and `TelemetryChartStack` simultaneously; scrubbing any chart below SHALL update the vertical indicator on `ActivityCarriageSyncBar` in lockstep.
     - While scrubbing, `ActivityCarriageSyncBar` SHALL display a compact active-mode readout badge (`testTag("activity_carriage_scrub_readout")`) showing the scrubbed `PhoneCarriageMode` color dot, icon, and localized `labelResId`.
     - Zooming/panning via `GlobalTelemetryZoomToolbar` or bar/chart gestures SHALL rescale and shift `ActivityCarriageSyncBar` 1:1 (`profileZoomScale`, `viewportStartFraction`, `isPanMode`) with `ChartGestureDisambiguator` vertical/horizontal disambiguation and `HorizontalPager` gesture isolation + pointer-up unlock.
  3. *Configurable Tab Section, `v4|` Migration, `ELEVATION` Coexistence & Graceful Omission (`REQ-UI-2796.3`)*:
     - `WorkoutDetailSectionType` SHALL add `ACTIVITY_CARRIAGE_BAR` (`15`th detail-only section; `toWorkoutSectionTypeOrNull() -> null`, leaving `WorkoutSectionType` at `14` entries for `WorkoutSummary.kt`), placed by default in `tab_map_graphs` as `[MAP, ACTIVITY_CARRIAGE_BAR, ELEVATION, CHARTS]` so users can display it alongside `Höhenprofil`, instead of `Höhenprofil`, or at any custom position.
     - `WorkoutDetailTabConfig` SHALL bump `SCHEMA_PREFIX` to `"v4|"`, auto-migrating legacy pre-`v4|` (`v1|`/`v2|`/`v3|`) configs lacking `ACTIVITY_CARRIAGE_BAR` by inserting it directly after `MAP` in `tab_map_graphs` while preserving explicit user removals on `v4|` (`REQ-UI-2787.1`).
     - When `activityCarriageTimeline == null || segments.isEmpty()`, `ACTIVITY_CARRIAGE_BAR` SHALL be omitted cleanly with zero placeholder or layout gap. `workout_detail_section_activity_carriage_bar` SHALL have 9-locale parity across all `values*/strings_workout_details.xml`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a workout with `ACTIVITY_AND_CARRIAGE_MODE` data opened in `Karte & Diagramme`, *When* rendered, *Then* `ActivityCarriageSyncBar` appears directly below the map, strictly above `ElevationProfile` and `TelemetryChartStack`, with identical horizontal plot edges (`Rule 64`).
  • *Given* the user scrubs/taps the Activity bar or any chart below, *When* the scrub position crosses mode transitions, *Then* `selectedDistance`, `ScrubbingTelemetryBadge`, and `activity_carriage_scrub_readout` update in lockstep across `DISTANCE` and `TIME` domains and zoom/pan viewports.
  • *Given* a legacy workout without `ACTIVITY_AND_CARRIAGE_MODE` data OR a tab configured with `[MAP, ACTIVITY_CARRIAGE_BAR, CHARTS]` (no `ELEVATION`), *When* rendered, *Then* the bar omits cleanly when telemetry is absent and renders directly below the map instead of `ELEVATION` when `ELEVATION` is disabled.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PER-045` (`ATT-8916`), `REQ-UI-408` (`ATT-3920`), `REQ-UI-2787.1` (`ATT-8079`), and `REQ-UI-2795` (`ATT-9508`), targeting `ActivityCarriageSyncBar.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, and `TrackOnMapScreen.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-8916` added `ActivityCarriageTimelineCard` in `DETAILS` without altering `v3|`/`14`-entry enums; `ATT-9508` introduced `MapDetailLowerContentScope` enabling arbitrary ordered lower-pane sections in `MapDetailLayout`.
  3. *Root Reason for Existing Formulation*: `REQ-PER-045.4` deferred synchronized map/chart bar integration until upstream classifier fixes (`ATT-9157`, `ATT-9062`) and ordered lower-pane slotting (`ATT-9508`) landed.
  4. *Preservation of Core Invariants*: Preserves `WorkoutSectionType` (`14` entries) and `WorkoutSummary.kt`, preserves `ActivityCarriageTimelineCard` in `DETAILS`, preserves `ELEVATION` independently configurable, preserves `v4|` persistence of user removals (`REQ-UI-2787.1`), `MapDetailLayout` backward compatibility, `<= 1,500` LoC ceilings, and 9-locale `TranslationParityTest`.

---

### REQ-UI-2797: TelemetryMetricGraph Dotted Grey Entire-Workout Average Reference Line & Top-Priority `Ø` Y-Axis Label (`ATT-9170`) [Verified]
- **Tests**: `TST-UI-2797`
- **System Requirement(s)**: Derived (SW-only — Compose Canvas visualization inside `TelemetryMetricGraph`, extending `REQ-UI-206`, `REQ-UI-219`, `REQ-UI-230`, `REQ-HRV-046`, and `REQ-HRV-047`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TelemetryMetricUtils.kt`, `TelemetryChartCanvasHelpers.kt`, `TelemetryMetricGraph.kt`
- **Localization Parity**: `N/A - 0 new or modified UI strings (uses language-neutral Ø mathematical symbol U+00D8 with locale-aware numeric formatting)`
- **Rationale**: Athletes reviewing workout telemetry curves in both Workout Summary (`WorkoutSummary.kt`) and Workout Details (`TelemetryChartStack.kt` / `MapDetailLayout.kt`) need a constant dotted grey horizontal reference line and `Ø`-prefixed Y-axis label at the entire workout's overall average so every segment can be judged against the session average at a glance (`ATT-9170`).
- **Description**:
  1. *Metric-Accurate Entire-Workout Average & Constant Zoom Anchor (`REQ-UI-2797.1`)*:
     - `TelemetryMetricUtils.computeWorkoutAverage(points, metricType, unit)` SHALL compute the entire workout's overall average for every `TelemetryMetricGraph` instance in Workout Summary and Workout Details (covering `SPEED`, `PACE`, `SLOPE`, `HEART_RATE`, `POWER`, `CADENCE`, `W_PRIME_BAL`, `HRV_DFA_A1`, `HRV_RMSSD`, and `ALTITUDE`).
     - For `PACE`, the average SHALL be derived from overall mean positive finite speed (`unitMeters / mean(speedMps) / 60.0` in active `METRIC`/`IMPERIAL` unit) rather than arithmetic mean of per-point pace values, preventing slow walking/stop segments from skewing the reference line.
     - For `HRV_DFA_A1` and `HRV_RMSSD`, high-artifact samples (`hrvArtifactRate > 5.0`) SHALL be excluded when clean samples exist (with fallback to all valid finite samples otherwise).
     - The average anchor SHALL remain constant across Workout Details zoom/pan interactions and SHALL be cleanly hidden via `resolveVisibleAverageValue` whenever no valid finite average exists or when it falls outside `[dataMin, dataMax]`. `ElevationProfile` SHALL remain completely untouched.
  2. *`Ø` Y-Axis Label Gutter Placement, Auto-Fit & Strict Collision Priority (`REQ-UI-2797.2`)*:
     - `TelemetryMetricUtils.formatAverageYAxisLabel` SHALL prefix the metric's standard Y-axis formatter with `Ø` (`U+00D8`, e.g., `Ø155`, `Ø8:15`, `Ø0,83`, `Ø235`, `Ø1%`, `Ø0%` with `-0%` suppression) honoring `currentLocale`.
     - `TelemetryMetricGraph` SHALL right-align the `Ø` label inside the `50.dp` left gutter (`startPaddingPx - 4.dp`) using `computeFittedGutterTextSize` and `computeRightAlignedGutterX` so neither compact nor wide labels clip into the plot area across any screen density.
     - `resolveTelemetryYAxisLabels` SHALL resolve vertical gutter labels in strict priority order (`Y_AXIS_PRIORITY_AVERAGE > Y_AXIS_PRIORITY_THRESHOLD > Y_AXIS_PRIORITY_EXTREMA`) using tight numeric glyph extents so the `Ø` label ALWAYS takes visual priority over colliding min/max extrema, `DFA α1` threshold labels, or future `ATT-9164` zone boundaries while preserving well-separated non-colliding labels.
  3. *Distinct Dotted Grey Horizontal Reference Line & Narrow-Canvas Guard (`REQ-UI-2797.3`)*:
     - `drawAverageReferenceLine` SHALL render a horizontal dotted grey line across `[startPaddingPx, startPaddingPx + rawChartWidthPx]` at `y = valueToY(visibleAverageValue)` using `colorScheme.onSurfaceVariant.copy(alpha = 0.60f)`, `strokeWidth = 1.5.dp`, `StrokeCap.Round`, and `PathEffect.dashPathEffect(floatArrayOf(0.5.dp, 5.dp), 0f)`, remaining clearly distinct from existing `6.dp / 6.dp` dashed guidelines in Light, Dark, and AMOLED themes, and cleanly no-oping when `plotWidth <= 0f`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* any `TelemetryMetricGraph` (Speed, Pace, Slope, Heart Rate, Power, Cadence, W'bal, DFA α1, rMSSD) in Workout Summary or Workout Details with valid samples, *When* rendered, *Then* a horizontal dotted grey line and right-aligned `Ø` Y-axis label appear at the entire workout's average value, visually distinct from dashed threshold/`0%` lines.
  • *Given* the user zooms or pans a Workout Details chart OR a `PACE` run contains slow walking stops, *When* rendered, *Then* the average remains anchored to the full workout (hidden cleanly if off-scale) and `PACE` uses speed-derived harmonic averaging rather than arithmetic per-point pace mean.
  • *Given* the `Ø` label sits near min/max, a `DFA α1` threshold, or a zone boundary, *When* Y-axis labels are placed in the `50.dp` gutter, *Then* `Ø` always remains legible without overlapping any other label or clipping into the plot area in Light and Dark/AMOLED themes, while `ElevationProfile` remains unchanged.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-UI-206` (`ATT-1740`), `REQ-UI-219` (`ATT-1818`), `REQ-UI-230` (`ATT-1839`), `REQ-HRV-046` (`ATT-8953`), and `REQ-HRV-047` (`ATT-9171`), targeting `TelemetryMetricUtils.kt`, `TelemetryChartCanvasHelpers.kt`, and `TelemetryMetricGraph.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-1740` created `TelemetryMetricGraph` with top/bottom extrema labels; `ATT-1839` added `6.dp/6.dp` dashed threshold lines; `ATT-8710`/`ATT-8953` added inverted `DFA α1` threshold labels; `ATT-9170` adds the session-average dotted grey reference line and priority `Ø` gutter label.
  3. *Root Reason for Existing Formulation*: Earlier Y-axis rendering drew extrema and `DFA α1` thresholds unconditionally without collision detection or session-average reference context.
  4. *Preservation of Core Invariants*: Preserves `ElevationProfile.kt` untouched, preserves `Rule 64` `50.dp` left & `52.dp`/`0.72f` right plot alignment, preserves `6.dp/6.dp` dashed threshold and `0%` slope lines, preserves inverted `PACE`/`HRV_DFA_A1` Y-axes, preserves `TelemetryMetricGraph.kt <= 1,965` LoC, and introduces `0` new XML strings or compiler warnings.

---

### REQ-UI-2798: Zone-Colored Heart Rate & Power Curves, Zone-Orthogonal Multi-Source Overlays & Zone-Boundary / Nice-Tick Y-Axis Labels (`ATT-9164`) [Verified]
- **Tests**: `TST-UI-2798`
- **System Requirement(s)**: Derived (SW-only — Compose Canvas visualization inside `TelemetryMetricGraph`, extending `REQ-UI-206`, `REQ-UI-230`, `REQ-UI-530`, `REQ-HRV-044`, and `REQ-UI-2797`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `Color.kt`, `TelemetryZoneMath.kt`, `TelemetryOverlayComponents.kt`, `TelemetryMetricGraph.kt`
- **Localization Parity**: `N/A - 0 new or modified UI strings`
- **Rationale**: Athletes inspecting Heart Rate and Power curves in Aftermath need the primary curve to transition cleanly across training zone colors (matching `DFA α1`) and display exact non-overlapping zone boundary values along the Y-axis so zone transitions and physiological thresholds can be read at a glance, while preserving multi-source comparison distinguishability and falling back cleanly when zones are unconfigured (`ATT-9164`).
- **Description**:
  1. *High-Contrast Zone-Colored Curves & Scrub/Point Dots (`REQ-UI-2798.1`)*:
     - `TTColor.zoneStrokeColor(zoneIndex, isDarkTheme)` (`Color.kt`) SHALL provide theme-adaptive WCAG 2.1 AA (`>= 3.0:1` non-text contrast) stroke colors for all 7 zones (`Z1`..`Z7`) across Light, Dark, and AMOLED surfaces (specifically darkening bright `Z1` chartreuse & `Z3` orange on Light surfaces and lifting `Z5` violet, `Z6` brown, and `Z7` silver-white `#E2E8F0` on Dark/AMOLED surfaces).
     - When valid Heart Rate (`isValidHeartRateThresholds`) or Power (`isValidPowerThresholds`) zones are configured, `TelemetryZoneMath.buildZoneBandsVerticalGradientStops` and `buildMetricZoneGradientStops` SHALL compute monotonically non-decreasing hard-stepped vertical gradient `zoneStrokeStops` and subtle `zoneFillStops` (`topAlpha = 0.22f`, `bottomAlpha = 0.04f`) so `TelemetryMetricGraph` transitions stroke and fill cleanly right at every zone boundary.
     - Single-point segments and scrubbing indicator dots/halos SHALL resolve to the sample's zone stroke color via `TelemetryZoneMath.resolveMetricZoneStrokeColor`.
  2. *Multi-Source Overlay Color Distinction (`REQ-UI-2798.2`)*:
     - When zone coloring is active on the primary curve (`useZoneColoredCurve == true`), `overlayStrokeColor(index, isDarkSurface, avoidZoneColors = true)` (`TelemetryOverlayComponents.kt`) SHALL render secondary multi-source overlay curves, legend swatches, and scrub dots using a zone-orthogonal theme-adapted contrast palette (`CYAN`, `THEME_DEFAULT`, `MAGENTA`, `TEAL`) so multiple comparison sensors (e.g., Polar H10 + Polar Verity Sense) stay clearly distinguishable across all zones.
  3. *Zone-Boundary Y-Axis Labels, Collision Priority & Unzoned Nice-Tick Fallback (`REQ-UI-2798.3`)*:
     - For configured `HEART_RATE` and `POWER` graphs, visible zone boundaries strictly within `(dataMin, dataMax)` (`thresholdDashes`) SHALL be emitted at `Y_AXIS_PRIORITY_THRESHOLD` through `resolveTelemetryYAxisLabels` (`ATT-9170`), ensuring `Ø` average (`Y_AXIS_PRIORITY_AVERAGE`) always takes top priority, zone boundaries suppress colliding raw edge `min`/`max` labels (`Y_AXIS_PRIORITY_EXTREMA`), and zero labels overlap.
     - When HR or Power zones are not configured (`effectiveHrThresholds == null` or `effectivePowerThresholds == null`), `TelemetryMetricGraph` SHALL fall back cleanly to the metric's configured `accentColor` (`TelemetryGraphColorPreferences`) and render non-overlapping dynamic round-number intermediate ticks computed by `TelemetryZoneMath.calculateNiceIntermediateTicks` (multiples of `10`/`20`/`25`/`50`/`100` bpm or `25`/`50`/`100`/`200`/`250`/`500` W scaled to visible span and chart height).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a workout with Heart Rate or Power telemetry and configured zones, *When* rendered in `TelemetryMetricGraph`, *Then* the primary curve, fill, single-point dots, and scrub indicator transition cleanly across WCAG-compliant zone colors in Light and Dark/AMOLED themes, and visible zone boundaries render as non-overlapping Y-axis labels coordinated with `ATT-9170`'s `Ø` average label.
  • *Given* multi-source HR or Power comparison overlays are enabled alongside a zone-colored primary curve, *When* rendered, *Then* the primary curve remains zone-colored while secondary overlay curves, legend dots, and scrub dots render in distinct zone-orthogonal contrasting colors.
  • *Given* a workout where HR or Power zones are unconfigured/invalid, *When* rendered, *Then* the curve falls back to `accentColor` and the Y-axis displays non-overlapping nice round intermediate ticks alongside `Ø`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-UI-206` (`ATT-1740`), `REQ-UI-230` (`ATT-1839`), `REQ-UI-530` (`ATT-7759`), `REQ-HRV-044` (`ATT-8710`), and `REQ-UI-2797` (`ATT-9170`), targeting `Color.kt`, `TelemetryZoneMath.kt`, `TelemetryOverlayComponents.kt`, and `TelemetryMetricGraph.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-1839` added background zone bands and `6.dp/6.dp` threshold dashes; `ATT-8710`/`ATT-8953` added 3-zone vertical gradients for `DFA α1`; `ATT-9170` introduced priority-based `resolveTelemetryYAxisLabels`; `ATT-9164` extends zone-colored curves and zone-boundary/nice-tick Y-axis labels to `HEART_RATE` and `POWER`.
  3. *Root Reason for Existing Formulation*: `HEART_RATE` and `POWER` previously rendered flat solid curves with only raw extrema Y-axis labels.
  4. *Preservation of Core Invariants*: Preserves `DFA α1` inverted 3-zone rendering (`REQ-HRV-044`/`REQ-HRV-046`), preserves `ATT-9170` `Ø` top priority and dotted grey reference line (`REQ-UI-2797`), preserves `TelemetryGraphColorPreferences` on all non-zoned metrics and unzoned HR/Power fallback (`REQ-UI-530`), preserves `ThemeColorCentralizationAuditTest` (all tokens inside `Color.kt`), preserves `ElevationProfile.kt` untouched, and keeps `TelemetryMetricGraph.kt <= 1,965` LoC (`1,910` LoC).
### REQ-UI-2799: Pure Compose Day Navigation in `PeriodsScreen`, Locale/Rotation/Cancel-Safe `EditDeviceDialog` & Single Window-Insets in BottomSheets (`ATT-8125`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2799`
- **System Requirement(s)**: Derived (SW-only — Compose navigation, dialog state integrity, and bottom-sheet WindowInsets bugfix under Epic `ATT-8119`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodsScreen.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `DevicesTabbedScreen.kt`, `ATrainingTrackerApp.kt`, `FilterBottomSheetScaffold.kt`
- **Localization Parity**: `N/A - 0 new or modified UI strings`
- **Rationale**: Fixes three concrete UI/UX defects surfaced by incomplete Fragment-to-Compose transitions and uncoordinated dialog/inset state: (1) `PeriodsScreen.handleDayClick` added a legacy `WorkoutSummariesListFragment` directly onto `android.R.id.content` over the Compose `NavHost`; (2) `EditDeviceDialog` lost unsaved edits on screen rotation, discarded German comma decimal keystrokes in `RunCalibrationFactorContent` / `CorrectCalibrationDialog`, and persisted Sensor-Fusion, TPMS baseline reset, and Radar light edits immediately ignoring `Cancel`; and (3) `ATrainingTrackerApp` doubled `navigationBars`/`ime` insets around `AppBottomSheetContent` settings sheets while `FilterBottomSheetScaffold` omitted `imePadding()` (`ATT-8125`).
- **Description**:
  1. *Pure Compose Day-Click Routing in `PeriodsScreen` (`REQ-UI-2799.1`)*:
     - `PeriodsScreen` (`PeriodsScreen.kt`) SHALL route day-cell clicks via `buildDayPeriodSummary(...)` (`PeriodType.DAY`, exact day epoch bounds `[dayStartS, dayEndS]`, sport stats) through the `onStartWorkoutList` Compose navigation callback (`ATrainingTrackerApp` -> `MainActivityWithNavigation.startWorkoutSummaryListFromPeriod` -> `NavRoutes.workouts(pushed = true)`), enabling clean backstack pop to `Periods` without `FragmentActivity`, `WorkoutSummariesListFragment`, or `FragmentTransaction` on `android.R.id.content`.
  2. *Rotation-Safe Drafts, Locale-Agnostic Decimal Input & Cancel Zero-Write Persistence in `EditDeviceDialog` (`REQ-UI-2799.2`)*:
     - `DevicesTabbedScreen` SHALL preserve `editingDeviceId` via `rememberSaveable`, and `EditDeviceViewModel.loadInitialDeviceData(deviceId)` SHALL return early without overwriting `_deviceSnapshot` or staged drafts when `_editingId.value == deviceId && _deviceSnapshot.value != null && hasUnsavedChanges`, while still fetching fresh DB snapshots when clean or reopened (`TST-UI-216.3`) and resetting all drafts on `discardDraft()` (`Cancel` / dismiss).
     - `RunCalibrationFactorContent` and `CorrectCalibrationDialog` SHALL parse both `.` and `,` decimal separators (`parseLocaleDecimal`) backed by a `rememberSaveable` local text buffer in `RunCalibrationFactorContent` so `de_DE` comma edits work seamlessly and survive rotation.
     - ATT Odometry Fusion (`AttFusedDraftConfig`), ATT Altitude & Slope Fusion (`AttFusedAltitudeDraftConfig`), TPMS `resetTirePressureBaseline()`, and `EditDeviceDialog` Radar tail light selections (`RadarLightDraftSelection`) SHALL be staged strictly in `EditDeviceViewModel` memory and persisted to SQLite/`SharedPreferences`/`RadarLightController` ONLY upon `saveChanges()`, with zero persistence writes upon `Cancel` (`discardDraft()`).
  3. *Single Non-Doubled Bottom-Sheet Insets & Filter IME Padding (`REQ-UI-2799.3`)*:
     - `ATrainingTrackerApp` SHALL host its Settings `ModalBottomSheet` without an outer `.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))` modifier so child `AppBottomSheetContent` -> `AppBottomSheetBody` applies `.navigationBarsPadding().imePadding()` strictly once.
     - `FilterBottomSheetScaffold` (`FilterBottomSheetBody`) SHALL apply `.navigationBarsPadding().imePadding()` matching `AppBottomSheetBody` so text fields inside filter bottom sheets shift cleanly above the software keyboard.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `PeriodsScreen` displaying a week with daily activities inside a Compose `NavHost`, *When* the user taps an active day cell and presses Back, *Then* navigation routes strictly through Compose `NavHost` with day-scoped `WorkoutFilterCriteria` (zero `android.R.id.content` `FragmentTransaction`) and Back returns cleanly to `PeriodsScreen`.
  • *Given* `EditDeviceDialog` under `de_DE` locale, *When* the user edits name, comma/dot calibration factor, Fused/Altitude toggles, TPMS baseline reset, or Radar mode, rotates the device, and taps `Cancel`, *Then* unsaved values survive rotation, comma and dot decimals are accepted, and zero writes are persisted to SQLite, `SharedPreferences`, or `RadarLightController` / DataStore (while tapping `Save` commits all staged changes).
  • *Given* `ATrainingTrackerApp` Settings bottom sheets and `FilterBottomSheetScaffold` with open IME, *When* rendered, *Then* bottom `navigationBars`/`ime` insets are applied strictly once (1x) and filter text fields lift cleanly above the soft keyboard.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-CON-031.5` (`ATT-7540`, `EditDeviceDialog` radar light staging) and `REQ-SEN-033.2` (`ATT-8422`, TPMS baseline reset staging), while preserving `REQ-UI-216` (`TST-UI-216.3`), `REQ-UI-149`, and `REQ-UI-189` across `PeriodsScreen.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `DevicesTabbedScreen.kt`, `ATrainingTrackerApp.kt`, and `FilterBottomSheetScaffold.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-7540` wired `RadarLightControlSection` directly to `RadarLightController`; `ATT-8422` persisted `resetTirePressureBaseline` eagerly; `ATT-7751`/`ATT-6279` persisted Fused toggles immediately; `ATT-5195` added `handleDayClick` with a legacy `FragmentTransaction`.
  3. *Root Reason for Existing Formulation*: Earlier incremental additions bypassed `EditDeviceViewModel`'s draft-save lifecycle and Compose `NavHost` routing before holistic complexity cleanup (`ATT-8119`).
  4. *Preservation of Core Invariants*: Preserves `TST-UI-216.3` fresh reload on clean/reopened dialogs, `RadarLightQuickSwitchSheet` immediate live cockpit dispatch, `PeriodsFragment.kt` legacy test (`TST-PER-009`), `EditDeviceDialog.kt <= 2,004` LoC (`1,831` LoC), `EditDeviceViewModel(Application)` reflection constructor (`AndroidViewModelReflectionContractTest`), `BottomSheetDesign` tokens (`BottomSheetVisualContractTest`), and `0` string resource modifications.

---

### REQ-UI-2800: Live Sensor & Cockpit Hotpath De-Coupling — Background Repository Scopes, `O(N)` RR Median Precomputation, Single-Push `ProxySensor`/`FilterManager` & Stable 1-Hz Recomposition Boundaries (`ATT-8126`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2800`
- **System Requirement(s)**: Derived (SW-only — internal concurrency, HRV DSP algorithmic efficiency, sensor-filter keying, and Compose recomposition boundary optimization under Epic `ATT-8119`)
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `BANALServiceRepository.kt`, `FilteredSensorData.kt`, `TrackingViewModel.kt`, `SensorFieldState.kt`, `WorkoutRepository.kt`, `EquipmentRepository.kt`, `SportTypesRepository.kt`, `RrArtifactFilter.kt`, `ProxySensor.java`, `FilterData.kt`, `FilterManager.kt`, `MyFilter.kt`, `DeviceManager.java`, `TrackingViewsDatabaseManager.java`, `EditSensorFieldViewModel.kt`, `TrackingTabGridContent.kt`, `TrackingTabsScreen.kt`
- **Localization Parity**: `N/A - 0 new or modified UI strings`
- **Rationale**: Eliminates avoidable Main-thread polling, per-beat redundant sorts/allocations under `@Synchronized`, double initial filter pushes, name-fragile filter keys, and 1-Hz root cockpit recompositions during multi-hour live recordings (`ATT-8126`).
- **Description**:
  1. *Background Repository Scopes, Structural `FilteredSensorData` Conflation, Sparkline/W'bal Liveness & Concurrency (`REQ-UI-2800.1`)*:
     - `BANALServiceRepository.repositoryScope` and `WorkoutRepository.coroutineContext` SHALL execute on `Dispatchers.Default` (with `@VisibleForTesting` dispatcher injection on `BANALServiceRepository`), while `EquipmentRepository.coroutineContext` and `SportTypesRepository.coroutineContext` SHALL execute on `Dispatchers.IO`.
     - `FilteredSensorData` SHALL override structural `equals`/`hashCode` over `(sensorType, value [Objects.equals for NaN parity], stringValue, deviceName, deviceId, filterType, mFilterConstant.toBits())` (excluding `SensorData.mTimestamp`) so `_allFilteredSensorData` `StateFlow` conflates unchanged 1-Hz snapshots, while decoupled `_sparklineTick` and `_wPrimeTelemetryHeartbeat` flows keep steady-value `showGraph` sparklines progressing and `W_PRIME_BAL` freshness timers alive (zero wakeups on views without those elements).
     - `BANALServiceRepository` SHALL guard `processItpmsTelemetryTick`, atomic `loadAndApplyItpmsBaseline` generation check + apply, `clearBreadcrumbs` estimator resets, and `persistConvergedCircumferences` snapshotting under `itpmsLock`; guard `applyRetroactiveAltitudeCorrection` under `trackLock`; and guard `bind`/`unbind`/`forceUnbind`/`onServiceConnected`/`onServiceDisconnected` (plus `bindService` false/throw reset) under `connectionLock`.
  2. *Single-Pass `O(N)` Local Median Precomputation & Thread-Safe Scratch-Buffer Sorting in `RrArtifactFilter` (`REQ-UI-2800.2`)*:
     - `RrArtifactFilter` SHALL precompute `localMedians: DoubleArray` and `dRrs: DoubleArray` once in `O(N)` using **method-local** `DoubleArray` scratch buffers (`2 * MEDIAN_HALF_WINDOW + 1` and `QD_WINDOW_SIZE + 1 == 92` capacity covering `filterLiveCausal`'s `[i - 86, i + 6)` window), pass `localMedians` into `processBeats(...)`, and produce bit-exact `FilterResult` outputs with full thread-safety.
  3. *Single-Push `ProxySensor`/`FilterManager` & End-to-End Stable `deviceId` Keying (`REQ-UI-2800.3`)*:
     - `ProxySensor.addSensorListener` SHALL register on `mSensorListeners` and forward to `mSourceSensor` once outside `mSourceLock` (`REQ-SEN-021.2`) with post-register `mSourceSensor != source` concurrent-swap re-check, without pushing a spurious leading `null` when bound.
     - `FilterManager.rebindFilter` SHALL rely on `newSensor.addSensorListener(myFilter)` for single initial delivery without manually calling `myFilter.newValue(currentValue)` a second time.
     - `FilterData` (`@JvmOverloads deviceId: Long = -1L`), `DeviceManager.getMyDeviceById(long)` (`<= 1,708` LoC), `FilterManager.resolveSensor` (returning `byId.getSensor(sensorType)` directly without name fallback when `byId != null`), `MyFilter` (`mDeviceId` + live `mDeviceName` sync on bind/rebind), `FilteredSensorData` (`deviceId`), `TrackingViewModel`, and `BANALServiceRepository` sparklines SHALL key and match pinned sensors by `deviceId > 0L` (with `deviceName`/global `null` fallback) so device renames keep cockpit tiles and sparklines live end-to-end.
  4. *Stable Cockpit 1-Hz Recomposition Boundaries (`REQ-UI-2800.4`)*:
     - `TrackingTabGridContent` SHALL wrap `gridActions` in `remember(viewModel)`.
     - `TrackingTabsScreen` SHALL collect `allFilteredSensorData` strictly inside `LaunchedEffect(brightnessMode, activityType, tuningConfig, trackingTabsViewModel, batterySaverController)` gated on `brightnessMode == DisplayBrightnessMode.AUTO` (skipped in `SYSTEM`/`CUSTOM`) and collect `currentLocation` inside `LaunchedEffect(controlViewModel, routeSelectorViewModel)`, while preserving `beyondViewportPageCount = 0` (`REQ-TRK-079.2`) and `SensorGridScreen` root GPS decoupling (`REQ-UI-2793`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `BANALServiceRepository`, *When* unchanged `FilteredSensorData` snapshots are polled or `bind`+`unbind` occurs before `onServiceConnected`, *Then* polling runs on `Dispatchers.Default`, unchanged snapshots conflate with zero duplicate emissions, and no `ServiceConnection` leaks.
  • *Given* a 300-beat RR window in `RrArtifactFilter.filterLiveCausal` / `filterBidirectionalBeats`, *When* evaluated, *Then* outputs match reference classification identically with single-pass `O(N)` median precomputation and zero per-beat nested median recomputation.
  • *Given* `ProxySensor` wrapping an active sensor, `FilterManager.rebindFilter`, and a renamed device with `deviceId > 0L`, *When* bound/rebound, *Then* listeners receive the initial value strictly once and `FilterManager` keeps resolving the renamed device by `deviceId`.
  • *Given* `TrackingTabGridContent` & `TrackingTabsScreen` receiving 1-Hz sensor and GPS updates, *When* `brightnessMode != AUTO` (or `AUTO`), *Then* `gridActions` stays referentially identical (`===`), root `TrackingTabsScreen` does not recompose on 1-Hz sensor/GPS ticks, and `beyondViewportPageCount == 0` holds.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-UI-2800` reinforcing `REQ-TRK-055`, `REQ-TRK-059.6`, `REQ-TRK-060.2`, `REQ-TRK-079.2`, `REQ-FIL-038`, `REQ-SEN-021.2`, `REQ-SEN-034`, and `REQ-UI-2793`.
  2. *Historical Origin & Commit Trace*: `ATT-6037` already decoupled `SensorGridScreen` root `currentLocationFlow`; `REQ-TRK-079.2` already bound `beyondViewportPageCount = 0`; `ATT-9160` already hoisted `allMRrs` in `filterLiveCausal`.
  3. *Root Reason for Existing Formulation*: Repository scopes defaulted to `Dispatchers.Main` historically before `StateFlow` adoption; `FilterData` predated stable `deviceId` plumbing; `TrackingTabsScreen` collected `allFilteredSensorData` at root before `brightnessMode` gating.
  4. *Preservation of Core Invariants*: Preserves `REQ-SEN-021.2` lock-order discipline in `ProxySensor`, `REQ-FIL-038.3` disconnect clearing, `REQ-TRK-059.6` 1-Hz sparkline feeding, `REQ-TRK-079.2` `beyondViewportPageCount = 0`, `REQ-HRV-048` exact artifact flags, `DeviceManager.java <= 1,708`, `WorkoutRepository.kt <= 5,117`, and `0` string edits.

---

## Change History

| Version | Date | Author | Ticket | Summary |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.29` | 2026-11-22 | Interactive-PO (`ATT-9504`) | `ATT-9504` | Rebalanced UI requirements across `part2`–`part6` so all shards stay comfortably below 4,000 lines. |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8712`) | `ATT-8712` | Aligned `REQ-UI-2783.3` with `REQ-TRK-046.4` and `REQ-TRK-087` cleanly post-split (`ATT-8206`). |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Added `REQ-UI-2785` (`[Planned]`) for enlarged `175x120 dp` Lieblingsorte preview map, `120.0 dp` zoom calibration, and `360dp` overflow protection. |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Promoted `REQ-UI-2785` to `[Verified]` after Stage 4/5 verification (`89/89` tests, `Rule 8.5` PNG audit). |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8748`) | `ATT-8748` | Refined `REQ-UI-493` to `[Verified]` (`175.dp x 120.dp` thumbnail, `12.dp` spacing, `resolveClusterThumbnailWidthDp`, `THUMBNAIL_BOUNDS_PADDING_PX = 18`, `151/151` PASS, `Rule 8.5` PNG). |
| `1.0.7` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Added `REQ-UI-2786` (`[Planned]`) unifying Workout Detail header action icons, global-vs-unit grouping, tooltips, universal `⇄` visibility, and non-GPS fallback. |
| `1.0.8` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Promoted `REQ-UI-2786` to `[Implemented]` after Stage 4 implementation (`90/90` targeted tests PASS, `Rule 8.5` PNG inspected). |
| `1.0.9` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Promoted `REQ-UI-2786` to `[Verified]` after Stage 5 verification (`90/90` XML tests PASS, governance `exit 0`, `Rule 8.5` PNG). |
| `1.0.10` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Added `REQ-UI-2787` (`[Planned]`) for `v3|` tab persistence, Workout Details summary-toggle decoupling, dynamic header action reservation, and tab-driven settings/subtitle. |
| `1.0.11` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Promoted `REQ-UI-2787` to `[Implemented]` in Stage 4 (`WorkoutDetailTabConfig`, `WorkoutDetailTabbedContent`, `WorkoutHeader`, `WorkoutMasksAndCardsSection`, `AdvancedTuning*`, 9-locale `strings_workout_details.xml`). |
| `1.0.12` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Promoted `REQ-UI-2787` to `[Verified]` in Stage 5 (`94/94` Tier 1 JUnit XMLs PASS, `Rule 8.5` PNG inspected). |
| `1.0.13` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Added `REQ-UI-2788` (`[Planned]`, `.1`–`.3`) for adequate Value+Graph sparkline Canvas height (`46/52/60/68.dp`), monotonic Graph-only heights (`48/56/68/84.dp`), compact `12.sp` X-axis leading (`includeFontPadding = false`), flexible `heightIn(min).weight(1f)` in `SensorFieldView`, and `52.dp` preview parity (`N/A - no new or modified UI strings`). |
| `1.0.14` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Promoted `REQ-UI-2788` to `[Implemented]` (`SensorSparklineView.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `SensorFieldViewSparklineLayoutComposeTest.kt`, Light/Dark `Rule 8.5` PNG `ATT-8995_sparkline_value_graph_layout.png`; `0` `strings*.xml` or monolith edits). |
| `1.0.15` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Promoted `REQ-UI-2788` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9382` @ `a5cbbd1b`) `57/57 PASS` across 11 suites + `:app:assembleDebug` + `0` new Kotlin/deprecation warnings (`Rule 7.5`, `0` `app/` diff since `a5cbbd1b`), `Rule 8.5` Light/Dark PNG certified, `4/4` safety linters PASS, `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-8995_walkthrough.md`. |
| `1.0.16` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Added `REQ-UI-2790` `[Planned]` for type-safe sealed UI state models (`PendingRestoreSource`, `CloudInfoDialog`, `PendingImportSource`, `MapPeekSelection` + `Saver`, dead `pendingSingleLegacyUri` removal, `<= 1,500` LoC ratchet). |
| `1.0.17` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Promoted `REQ-UI-2790` to `[Implemented]` (`ImportBackupTabsScreen.kt` `1,400` LoC, `MapScreenWithTrack.kt` `886` LoC, `ArchitectureConformanceContractTest.kt` baseline ratcheted to 15, `ImportBackupTabsSealedStateComposeTest.kt`, `MapPeekSelectionSaverAndUiComposeTest.kt`). |
| `1.0.18` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Promoted `REQ-UI-2790` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9485` @ `cd809943`: `886/886 PASS` across 172 suites, `0` new warnings, `--check-verified-evidence` `exit 0`). |
| `1.0.19` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Added `REQ-UI-2791` (`[Planned]`, `.1`–`.6`, refining `REQ-UI-401.3`/`REQ-UI-495`) for PMC box-to-curve swatches/legend & neutral Row 2 borders, 2-line unclipped readiness badges, `zeroY`-clamped CTL area fill, pure rounded Y-axis ticks with explicit `0`, `currentLocale` `ACWR` formatting, and modular `PerformanceManagementChartCard.kt` extraction (`0` new strings). |
| `1.0.20` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Promoted `REQ-UI-2791` to `[Implemented]` (`PerformanceManagementChartCard.kt` `613` LoC, `AthleteProfileScreen.kt` `1,596/1,975` LoC, `AthleteMetricsPlotMath.kt` `365` LoC; `55/55 PASS` across 7 Tier-1 suites + `3/3` Python safety linters, `0` new warnings, `Rule 8.5` Light/Dark PNG inspected). |
| `1.0.21` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Promoted `REQ-UI-2791` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9506` @ `04271fff`: `55/55` Stage 4 + `35/35` Gate 4 PASS, `0` `app/` drift, `0` new warnings, `3/3` safety linters PASS, `Rule 8.5` Light/Dark PNG certified, `--check-verified-evidence` `exit 0`). |
| `1.0.22` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Re-promoted `REQ-UI-2791` to `[Verified]` in Stage 5 Bounce 1 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 Bounce 1 `ATT-9512` @ `4fe0f4af`: narrow-range `yMin == ticks.first()` / `yMax == ticks.last()` sync, `currentLocale` X-axis `formatShortDate`, TalkBack decorative swatch cleanup, `36/36 PASS`, `0` `app/` drift, `Rule 8.5` PNG re-certified, `--check-verified-evidence` `exit 0`). |
| `1.0.23` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Added `REQ-UI-2792` (`[Planned]`, `.1`–`.2`, refining `REQ-UI-401.1`/`.3`/`.4` & `REQ-UI-2791`) for neutral ACWR building-base hint (`—` + `athlete_profile_pmc_acwr_building_base` when `currentAcwr == null` with non-empty `dailyPoints`/`pmcPlotPoints`, `badgeText = null` when both empty) and 9-locale trademark-free parity (`EN, DE, ES, FR, IT, JA, NL, PL, PT`). |
| `1.0.24` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `REQ-UI-2792` to `[Implemented]` (`PerformanceManagementChartCard.kt` neutral building-base badge + 9-locale `values*/strings.xml` parity). |
| `1.0.25` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `REQ-UI-2792` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9534` @ `51ce6830`: `67/67 PASS` incl. `TranslationParityTest` `11/11`, `0` `app/` drift, `Rule 8.5` Light/Dark PNG certified, `--check-verified-evidence` `exit 0`). |
| `1.0.26` | 2026-11-21 | Lane 3 Author (`ATT-9558`, `--as agent1`) | `ATT-9558` | Refined `REQ-UI-2791.1`: replaced thick top-only `3.dp` bar on Row 1 `PmcStatBadge` cards with clean uniform `1.5.dp` all-around colored borders matching curve colors while preserving Row 2 neutral `1.dp` `outlineVariant` borders and 3-curve `FlowRow` legend. |
| `1.0.27` | 2026-11-21 | Lane 1 Author (`ATT-6037`, `--as agent1`) | `ATT-6037` | Added and verified `REQ-UI-2793` (`[Verified]`, `.1`–`.3`) for `ElevationProfile` Canvas Paint/Path/climb-summit hoisting & coalesced color runs, `MapScreenWithTrack` direct `StateFlow` forwarding + `MapContentScopeImpl` `Rule 66` plain `mutableListOf` + `contentRevision` fix, `TrackingViewModel`/`SensorGridScreen` true `mapState` decoupling, and shared base `AppHttpClient` (emulator + Light/Dark PNGs visually verified via `read_file`, automated gate & evidence bundle in `docs/engineering/test_evidence/tickets/ATT-6037/`). |
| `1.0.28` | 2026-11-22 | Lane 2 Author (`ATT-9553`, `--as agent1`) | `ATT-9553` | Added and verified `REQ-UI-2794` (`[Verified]`, `.1`–`.3`) promoting `MapPeekSelection.Climb` to `isSheetBacked = true` in `MapScreenWithTrack` via `BottomSheetScaffold` + `BottomSheetDesign.PeekHeightClimb` (`192.dp`) + `ClimbOnMapScreen` dynamic `measuredHeaderHeight` + `SheetValue.Hidden` selection cleanup, superseding `REQ-UI-2790.2` modal Climb carve-out while preserving `ClimbDetailSheet` for `RouteOnMapScreen`. |
| `1.0.29` | 2026-11-22 | Lane 2 Author (`ATT-9508`, `--as agent1`) | `ATT-9508` | Added and verified `REQ-UI-2795` (`[Verified]`, `.1`–`.3`) introducing `MapDetailLowerContentScope` / `customLowerContent` in `MapDetailLayout.kt` and custom-ordered `ELEVATION` & `CHARTS` rendering across `MODE_A_CARD_SCROLL` and `MODE_B_SPLIT_MAP` in `WorkoutDetailTabbedContent.kt` while fixing `PointerEventPass.Initial` pointer-up unlock and preserving `MAP` split-pane pinning, synchronized scrubbing, chart visibility toggles, and `HorizontalPager` gesture isolation (`82/82` targeted tests PASS, Light/Dark PNG & live `emulator-5554` verified). |
| `1.0.30` | 2026-11-22 | Lane 2 Author (`ATT-9172`, `--as agent1`) | `ATT-9172` | Added and verified `REQ-UI-2796` (`[Verified]`, `.1`–`.3`) introducing synchronized `ActivityCarriageSyncBar` (`ActivityCarriageBarMath`, `Rule 64` `50.dp` alignment, bidirectional scrubbing + active-mode readout chip, 1:1 zoom/pan & `DISTANCE`/`TIME` support, configurable `WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR` + `v4|` migration, graceful omission when absent, and 9-locale `strings_workout_details.xml` parity). |
| `1.0.31` | 2026-11-22 | Lane 2 Author (`ATT-9170`, `--as agent1`) | `ATT-9170` | Added and verified `REQ-UI-2797` (`[Verified]`, `.1`–`.3`) adding dotted grey entire-workout average reference lines (`drawAverageReferenceLine`) and top-priority auto-fitted `Ø` Y-axis gutter labels (`computeWorkoutAverage`, `formatAverageYAxisLabel`, `resolveTelemetryYAxisLabels`) across all `TelemetryMetricGraph` charts in Workout Summary & Workout Details while keeping `ElevationProfile` untouched. |
| `1.0.32` | 2026-11-22 | Lane 2 Author (`ATT-9164`, `--as agent1`) | `ATT-9164` | Added and verified `REQ-UI-2798` (`[Verified]`, `.1`–`.3`) rendering `HEART_RATE` and `POWER` graphs in WCAG 2.1 AA theme-adaptive zone colors (`TTColor.zoneStrokeColor`, `buildZoneBandsVerticalGradientStops`), zone-orthogonal multi-source overlay contrast (`overlayStrokeColor`), non-overlapping zone boundary Y-axis labels integrated with `ATT-9170` `Ø` priority, and accent-color + nice round-tick fallback (`calculateNiceIntermediateTicks`) when unzoned. |
| `1.0.31` | 2026-11-22 | Lane 3 Author (`ATT-8125`, `--as agent1`) | `ATT-8125` | Added and verified `REQ-UI-2799` (`[Verified]`, `.1`–`.3`) replacing legacy `PeriodsScreen` `android.R.id.content` `FragmentTransaction` with pure Compose `onStartWorkoutList` day routing, hardening `EditDeviceDialog`/`EditDeviceViewModel` with rotation-safe dirty draft retention, `de_DE` comma/dot calibration input buffer, and Save-only / Cancel-zero-write persistence for Fused/TPMS/Radar options, removing duplicate outer `ATrainingTrackerApp` bottom-sheet insets, and adding `imePadding()` to `FilterBottomSheetScaffold` (`98/98` targeted tests PASS, Light/Dark & live `emulator-5554` PNGs verified). |
| `1.0.34` | 2026-11-22 | Lane 3 Author (`ATT-8126`, `--as agent1`) | `ATT-8126` | Added `REQ-UI-2800` (`[Planned]`, `.1`–`.4`) for background repository scopes (`BANALServiceRepository`, `WorkoutRepository`, `EquipmentRepository`, `SportTypesRepository`), structural `FilteredSensorData` `StateFlow` conflation & unbind-before-connect leak closure, `O(N)` `RrArtifactFilter` local-median/scratch-buffer precomputation, single-push `ProxySensor`/`FilterManager` + `deviceId` filter keying, and cockpit 1-Hz recomposition bounds (`TrackingTabGridContent`, `TrackingTabsScreen`). |
| `1.0.35` | 2026-11-22 | Lane 3 Author (`ATT-8126`, `--as agent1`) | `ATT-8126` | Gate 3 (`ATT-9635`) revision on `REQ-UI-2800`: added decoupled `_sparklineTick`/`_wPrimeTelemetryHeartbeat` liveness + NaN-safe `FilteredSensorData` equality, method-local `QD_WINDOW_SIZE + 1` (92) scratch buffers & `DoubleArray` `computeDifferences`, end-to-end `deviceId` rename survival (`TrackingViewModel`/sparklines/`MyFilter`), full `itpmsLock`/`trackLock`/`connectionLock` coverage, `ProxySensor` post-register re-check, and `DisplayBrightnessMode.SYSTEM`/`CUSTOM`/`AUTO` code-grounding. |
| `1.0.36` | 2026-11-22 | Lane 3 Author (`ATT-8126`, `--as agent1`) | `ATT-8126` | Promoted `REQ-UI-2800` to `[Verified]` after Stage 4–5 implementation & verification (`128/128` PASS across 20 targeted suites, `0` new compiler warnings, `DeviceManager.java` `1,679 <= 1,708`, live `emulator-5554` smoke & cockpit grid PNGs verified `0` crashes, `docs/engineering/test_evidence/tickets/ATT-8126/`). |
