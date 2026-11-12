# aTrainingTracker: UI & Interaction Design Guidelines

This document serves as the living source of truth for UI/UX patterns, component selection heuristics, and touch interaction principles across **aTrainingTracker**. All new Jetpack Compose implementations, refactorings, and settings screens should align with these guidelines to ensure visual harmony, intuitive interaction, and brand consistency.

---

## 1. Mode Switcher & Component Selection Heuristics

Selecting the right Material 3 control depends strictly on the **cardinality** and **mutual exclusivity** of the choices.

### 1.1 Binary Mutually Exclusive Mode Selections (Exactly 2 Options)
* **Standard Control**: Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton`.
* **When to Use**:
  * Choosing between exactly two mutually exclusive display, calculation, or visualization modes.
  * *Examples*:
    * **5 Zonen** vs. **Histogramm** (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`)
    * **Tabelle** vs. **Visualizer** (`WorkoutCardPreferences`, `AdvancedTuningDialog`)
    * **Pace** vs. **Speed** toggles
* **Styling & Layout Rules**:
  * Center the segmented row or fill equal horizontal width.
  * Use compact height (e.g. `28.dp` in cards, `36.dp` in settings dialogs).
  * Use `SegmentedButtonDefaults.itemShape(index, count)` for rounded boundary pills.
* **Anti-Pattern (Avoid)**:
  * ❌ **Do NOT use `FilterChip`** for binary exclusive choices. Two adjacent chips look unanchored, visually cluttered, and semantically imply independent filter tags rather than a unified switch.
  * ❌ **Do NOT use Radio Buttons** unless part of a long vertical questionnaire list.

```kotlin
// Recommended Pattern for Binary Mode Selection:
SingleChoiceSegmentedButtonRow(
    modifier = Modifier.fillMaxWidth()
) {
    SegmentedButton(
        selected = currentMode == Mode.OPTION_A,
        onClick = { onModeSelected(Mode.OPTION_A) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
    ) {
        Text(text = stringResource(R.string.option_a))
    }
    SegmentedButton(
        selected = currentMode == Mode.OPTION_B,
        onClick = { onModeSelected(Mode.OPTION_B) },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
    ) {
        Text(text = stringResource(R.string.option_b))
    }
}
```

### 1.2 Categorical Filters & Multi-Option Tag Sets ($\ge 3$ Options)
* **Standard Control**: `FilterChip` / `SuggestionChip`.
* **When to Use**:
  * Filtering lists by categories (e.g. Sport Types: All, Run, Bike, Hike).
  * Selecting multiple non-exclusive tags.
  * Selecting among 3 to 4 distinct discrete states where each option has descriptive context.
* **Canonical Sport Type Chip Ordering (`Other` Always on the Far Right — Rule 57, ATT-4372 / ATT-4430)**:
  * Across all sport type selector rows (`FilterChip` rows, segmented buttons), specific primary sports (`Run`, `Bike` / `Bike`, `Run`) **MUST** appear first, and the catch-all `Other` (`BSportType.UNKNOWN` / `"Andere"`) **MUST** always be positioned on the **far right** (`[Bike, Run, Other]` or `[Run, Bike, Other]`), even when `Other` is preselected as the default value. Never move `Other` to the leftmost position just because it is the default selection.

### 1.3 Large Option Sets ($> 4$ Options)
* **Standard Control**: Exposed Dropdown Menu (`ExposedDropdownMenuBox`) or Modal Selection Dialog.
* **When to Use**:
  * Options list exceeds horizontal screen space.
  * *Examples*: Sensor device selection, audio cue sound profile selection, map tile source.

### 1.4 Discrete Scalar Ranges & Durations ($\ge 5$ Numeric Values) (ATT-3325 / ATT-3450)
* **Standard Control**: Discrete Slider (`TuningSliderItem` / Material 3 `Slider`) or connected `SingleChoiceSegmentedButtonRow`.
* **When to Use**:
  * Configuring durations, distances, or numeric settings offering $\ge 5$ discrete scalar options (e.g. `[1s, 2s, 3s, ... 10s]` or `[30m, 40m, ... 300m]`).
* **Anti-Pattern (Avoid)**:
  * ❌ **Do NOT use multi-chip rows (`FilterChip` in `FlowRow`)** for large scalar sets. On mobile viewports, the trailing chip(s) frequently wrap onto an isolated second row, creating unbalanced whitespace and poor visual rhythm. Prefer an interactive slider with snap steps and a live value indicator.

---

## 2. Chart & Visual Analytics Guidelines

Post-workout analysis combines geographic terrain (Elevation Profile) with continuous physiological telemetry (Speed/Pace, Heart Rate, Cycling Power).

### 2.1 Coordinate Space & Cross-Domain Integrity
* **Domain Decoupling**:
  * Geographic terrain naturally plots against **Distance** (meters/km/miles).
  * Physiological exertion naturally plots against **Time** (seconds/duration).
* **Viewport Offset Separation Invariant**:
  * When charts in a composite layout (e.g. `MapDetailLayout`) use different X-axis domains, the parent container **MUST NOT store a single shared raw metric scalar** for viewport offset / start position.
  * Storing seconds in a variable read as meters corrupts zoom and pan clamping boundaries.
  * **Solution**: Maintain independent domain offsets (`elevationStartDist` vs. `telemetryStartTimeSec`) or normalize continuous viewport pans into a **dimensionless fraction ($0.0 \dots 1.0$)** that is mapped to native units at the chart boundary.
* **Sub-Entity Coordinate Space Normalization (Local vs. Global Domain)**:
  * Whenever sub-entity segments, climbs, or intervals are displayed in an inspection chart (e.g. `ClimbDetailSheet`, `SegmentDetailSheet`), their distance domain coordinates MUST be normalized to the entity's local length ($[0, \text{length}]$) rather than inheriting the parent route's global coordinate space ($[0, \text{totalRouteDistance}]$).
  * Passing global route coordinates to sub-entity elevation profile charts squishes the entity into a tiny sliver at the far edge and leaves the chart mostly blank (ATT-3053 / ATT-3105).
* **Stacked Multi-Chart Horizontal Plot Width Alignment Invariant (`Rule 64` — ATT-2145 / ATT-4774)**:
  * Whenever multiple charts are stacked vertically in a shared inspection view (`MapDetailLayout`, `WorkoutSummary`) with a synchronized X-axis or scrubbing cursor (`ElevationProfile`, `SPEED/PACE`, `HEART_RATE`, `POWER`, `W_PRIME_BAL`), **all** stacked charts **MUST** share the exact same horizontal plot bounds (left `startPadding = 50.dp` AND right plot edge) in both Portrait and Landscape orientations.
  * If a subset of charts (`HEART_RATE`, `POWER`) uses a `0.72f` / `0.28f` horizontal split in Landscape mode to dock `AlignedMarginalZoneDistribution` on the right, sibling charts (`ElevationProfile`, `SPEED/PACE`, `W_PRIME_BAL`) must align their right plot edge to the identical `0.72f` horizontal boundary so X-axis ticks and the vertical scrubbing cursor line up 1:1 across all stacked charts.

### 2.2 Touch, Pan & Gesture Interactions
1. **Lifecycle Key Stability**:
   * Never pass continuously mutable drag parameters (`startDist`, `zoomScale`) as keys into `Modifier.pointerInput`. Key mutations cancel running coroutines, truncating swipes after 1 frame.
   * Capture mutable state via `rememberUpdatedState`.
2. **Gesture Disambiguation**:
   * Charts placed inside vertically scrollable containers (e.g. detailed workout summary) must use slope disambiguation (`ChartGestureDisambiguator`).
   * Dominant vertical motion ($\Delta y > \Delta x$ past touch slop) must be ignored by the chart to allow natural vertical parent scrolling without pointer locking.
   * Dominant horizontal motion ($\Delta x \ge \Delta y$ past touch slop) must consume pointers and accumulate pan delta smoothly.
3. **Family Interaction Parity**:
   * When implementing gesture or zoom improvements across charts, verify tactile feel, travel distance, and responsiveness across **every sister chart** (Elevation Profile, Speed/Pace, Heart Rate, Power).

### 2.3 Elevation Profile Sizing & Adaptive Height Invariant
* **Vertical Range Scaling**:
  * The elevation profile does NOT expand to arbitrary flexible screen heights (e.g. taking half the screen with `weight(1f)` or `fillMaxHeight()`).
  * Its height is strictly determined by the canonical `calculateElevationProfileHeight(range)` algorithm, scaling adaptively based on the track's total elevation difference ($\Delta H = H_{\max} - H_{\min}$):
    * **Minimum height**: `70.dp` (for flat or low-relief terrain, $\Delta H \approx 0\text{ m}$)
    * **Maximum height**: `200.dp` (at $\Delta H \ge 1000\text{ m}$)
    * **Curved progression**: $\text{height} = 70 + (\text{range} / 1000)^{0.6} \times (200 - 70)$
* **No Hardcoded Fixed Heights ("No Exceptions")**:
  * The height of the elevation profile MUST always depend on the height/elevation gain of the track, route, or climb.
  * Hardcoding arbitrary compact fixed heights (e.g. `68.dp`) in bottom sheets, cards, or dialogs is strictly prohibited (ATT-3264 / ATT-3316 / Retro 2026-41.10).
* **Cockpit Space Allocation**:
  * On tracking screens where the map is disabled and only the elevation profile is shown, the elevation profile retains its fixed adaptive height based on the track's elevation difference.
  * The sensor grid column absorbs the flexible remaining vertical space (via `weight(1f, fill = false)`), wrapping naturally if there are few tiles and scrolling smoothly if there are many tiles, without stretching the elevation profile out of proportion (ATT-3107 / ATT-3173).

### 2.4 Elevation Profile Marker Badge Boundary Clearance (ATT-3343 / ATT-3453)
* **Boundary Clipping Prevention**:
  * Overlay badges and markers rendered on elevation profile canvases (e.g. climb category pills `UC`, `CAT 4`, `CAT 3`, `CAT 2`, `CAT 1`, `HC`, start/finish markers) must have guaranteed top and horizontal boundary clearance.
  * Canvas drawing routines MUST allocate adequate top padding (at least 16–20 dp) above the peak terrain line so category badges drawn above hill summits are never clipped or truncated at the top canvas boundary.
  * Badges positioned near the right or left viewport edges must clamp horizontal bounds so badge pills remain fully visible within the canvas width.

### 2.5 Continuous Linear Projection for Sparse Geometry Progress Tracking
* **Linear Projection Invariant**:
  * Progress tracking along geometric paths (climbs, segments, routes) MUST NOT rely exclusively on nearest-neighbor discrete vertex quantization (`path[closestIdx]`).
  * On straight road sections or imported GPX tracks where points may be 50–200 m apart, nearest-neighbor snapping causes telemetry metrics (remaining distance, elevation gain, current gradient) and visual rider pins to freeze and jump discontinuously.
  * Progress tracking algorithms MUST project the athlete's current coordinate vector onto the nearest line segment $[P_i, P_{i+1}]$ to determine the fractional progress $s \in [0.0, 1.0]$:
    $$\vec{v} = P_{i+1} - P_i$$
    $$s = \frac{(L - P_i) \cdot \vec{v}}{\|\vec{v}\|^2} \in [0.0, 1.0]$$
    $$\text{dist} = P_i.\text{dist} + s \times (P_{i+1}.\text{dist} - P_i.\text{dist})$$
    $$\text{alt} = P_i.\text{alt} + s \times (P_{i+1}.\text{alt} - P_i.\text{alt})$$
  * Telemetry and visual marker positions along polyline profiles must be interpolated continuously using this projected distance and altitude (ATT-3103 / ATT-3174).

### 2.6 Mandatory Axis Scale Labels & Outlier-Resilient Y-Scaling on Telemetry & Progression Charts (Rule 48 — ATT-4287)
* **Explicit X-Axis & Y-Axis Scale Markers**:
  * Every chart rendered on a `Canvas` (including historical workout cluster progression charts such as `ClusterTrendChart` and post-workout telemetry charts) **MUST** render explicit, human-readable scale markers/labels on both the X-axis (e.g. session dates `"dd.MM."` / `"MMM yy"`, distance, or duration) and the Y-axis (e.g. formatted pace `"5:30 /km"`, speed `"28.5 km/h"`, heart rate `"155 bpm"`, or power `"220 W"` aligned with the horizontal reference gridlines).
  * Drawing unlabeled gridlines without axis tick values leaves the athlete unable to read absolute performance levels or time intervals from the graph.
* **Outlier-Resilient Y-Axis Domain Clamping**:
  * Historical progression charts **MUST NOT** scale their Y-axis linearly across raw un-clamped `min..max` extrema when a single anomalous session (e.g. an unpaused stop yielding `115:52 /km` when normal sessions are `5:14/km – 8:28/km`) is present.
  * Use outlier-resilient bounds (such as interquartile range $\text{IQR} = Q_3 - Q_1$ clamping to $[Q_1 - 1.5 \times \text{IQR}, Q_3 + 1.5 \times \text{IQR}]$ or 5th–95th percentile clamping when $N \ge 4$, pinning extreme outliers to the top/bottom canvas edge with an indicator) so the primary trend line utilizes the full vertical canvas height.

### 2.7 Bottom-Anchored Vertical Bar Rounding & Unconditional Histogram Track Continuity (Rule 69 — ATT-5195, ATT-5190 / ATT-5202)
* **Rounded Top Corners & Sharp Bottom Corners on Bottom-Anchored Bars (`ATT-5195` $\rightarrow$ `ATT-5280`)**:
  * Vertical bar charts anchored to a bottom baseline (`DailyActivityStrip` in `MonthDayBar` / `WeekDayColumn`, histogram columns) **MUST** have **rounded top corners (`topStart = 2.dp..3.dp, topEnd = 2.dp..3.dp`)** and **sharp bottom corners (`bottomStart = 0.dp, bottomEnd = 0.dp`)**.
  * Never apply `.clip(RoundedCornerShape(2.dp / 3.dp))` to all 4 corners of an outer container `Box(contentAlignment = Alignment.BottomCenter)` while giving the inner bar a smaller `1.dp` top radius, which makes the bottom of the bars look rounded and the top look sharp.
* **Unconditional Background Track Continuity & Domain-Boundary Clamping in Histograms (`ATT-5190` $\rightarrow$ `ATT-5281` $\rightarrow$ `ATT-5311`, Rules 69 & 70)**:
  * Marginal and zone histograms (`TelemetryMetricGraph`, `AlignedMarginalZoneDistribution` in `ZoneDistributionComponents.kt`) **MUST** draw the `alpha = 0.15f` background container track unconditionally for every `linearBin` *within* the plotted `[dataMin, dataMax]` domain, gating only the solid foreground bar on `if (bin.durationSec > 0L && barWidth > 0f)`.
  * Gating the background track on `durationSec > 0L` skips empty intermediate bins between non-contiguous high-wattage spikes, leaving horizontal white stripes across the Zone 7 background band (`ATT-5281`).
  * **Domain-Boundary Clamping Guard (`Rule 70` — `ATT-5281` $\rightarrow$ `ATT-5311`)**: Always filter out-of-domain bins (`bin.rangeMax > dataMin && bin.rangeMin < dataMax`) and clamp `[rangeMin, rangeMax]` to `[dataMin, dataMax]` before mapping via `valueToY(...)`. Never apply `.coerceAtLeast(1f)` to zero-height clamped bins outside `[dataMin, dataMax]`, which causes dozens of `alpha = 0.15f` rectangles to stack at `y = 0f` into a solid black bar along the top edge.

---

## 3. Settings & Accordion Structure

* **Scannability First**:
  * Complex settings screens (e.g. `AdvancedTuningDialog`) must organize options into logical accordion sections.
  * Sections should default to **initially collapsed** (`expandedSections = emptySet()`) so the athlete can scan all available categories immediately without scrolling.
* **Dynamic Header Subtitles**:
  * Collapsed accordion headers should render a concise summary subtitle reflecting the active choices within that category, eliminating the need to expand sections just to inspect current state.
* **Instant Persistence & Defensive Defaults**:
  * Settings mutations must persist immediately to `DataStore`.
  * Deserialization must be defensive: deprecated or removed enum options (e.g. legacy `"BOTH"`) must cleanly resolve to modern defaults without errors or UI disruption.

### 3.1 Explanatory Scale Direction in Settings Sliders
* **Scale Direction Transparency**:
  * Whenever exposing numerical sliders or continuous values in user settings (such as map zoom levels, camera tilt angles, sensitivity curves, or lookahead paddings), the preference subtitle or hint text MUST explicitly explain the physical direction of the scale (e.g., *"Höherer Wert = Näher herangezoomt"* / *"Niedrigerer Wert = Weiträumigere Übersicht"*) (ATT-2946 / ATT-3054 / ATT-3171).

### 3.2 Single Unambiguous Configuration Surface (Retiring Redundant Legacy Controls)
* **Retire Overlapping Matrix Columns When Adding Dedicated Config Sheets (ATT-3920 / ATT-4043)**:
  * When introducing a dedicated configuration sheet or editor for a view (such as `WorkoutDetailTabsConfigSheet` for `DetailedWorkoutView` tab ordering and visibility), any legacy settings controls that previously toggled the same view (such as the `Details` column in the 2-column `WorkoutMasksAndCardsSection` matrix) **MUST** be retired and cleaned up in the same change.
  * Never keep two competing UI surfaces in the same settings screen that toggle the same underlying preferences; doing so confuses the athlete about which control is authoritative.

### 3.3 Subordinate Settings Toggles: Lighter Visual Weight & Compact Switches (Rule 59 — ATT-4369 / ATT-4432 / ATT-4464 / ATT-4513)
* **Clear Parent-Child Hierarchy**:
  * Subordinate child toggles nested under a primary feature/model toggle (such as `"Show in Workout Summary"` / `"In Workout-Zusammenfassung anzeigen"` under a Physiology Model toggle) **MUST NOT** reuse the full-sized primary `TuningToggleItem` row or the same `Switch` scale (`0.8f`) as `TuningToggleItem`, which visually competes with the parent header.
  * Because `TuningToggleItem` already scales its primary `Switch` to `0.8f`, subordinate toggles (`SubordinateTuningToggleItem`) **MUST** render with a significantly smaller/lighter control (e.g., `Modifier.scale(0.6f)` with lighter checked track color `MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)` and thumb `primary` [ATT-4513] to avoid excessively dark navy tracks in Light Mode), smaller/lighter text (`MaterialTheme.typography.bodySmall` or `labelMedium` with `MaterialTheme.colorScheme.onSurfaceVariant`), and compact vertical padding.
* **No Unrequested Top-Level Model Toggles**:
  * Do not invent extra top-level settings toggles for baseline metrics (e.g. `"Basic Power Metrics (Work, W/kg)"`) that were not requested in the ticket specification.

### 3.4 Multi-Provider Cloud / Sync UI & Capability Symmetry (Rule 62 — ATT-4588 / ATT-4664 / ATT-4665 / ATT-4666)
* **Shared Parameterized Layout & Symmetric Capabilities**:
  * Parallel cloud/sync provider dialogs, cards, and action buttons (such as **Dropbox** vs. **Google Drive** in `CloudSyncSettingsDialogs.kt` and `RestoreTabContent.kt`) **MUST** share a unified parameterized composable layout, identical button label phrasing/iconography, and symmetric configuration granularity (e.g., independent toggles for Workouts vs. Backups, Wi-Fi-only restriction, Backup Interval selector, and Last Backup/Sync timestamp) unless a provider API physically lacks that capability.
  * Never evolve sister provider dialogs independently with divergent wording or mismatched feature sets.

---

## 4. Spacing, Typography & Localization Parity

* **Design Tokens**:
  * Always use the existing theme tokens rather than hardcoded raw colors: `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `TTColor` (domain colors: zones, routes, achievements, branding), `TTAlpha` (opacity levels) and `LayoutConstants` (header heights). There is currently no `TTDimens` object — use the spacing scale in Section 5.2.
* **Jetpack Compose Multi-State `@Preview` Tooling**:
  * Every reusable composable and screen container **MUST** provide comprehensive `@Preview` composables.
  * Previews must cover multiple states: Light and Dark/AMOLED theme, empty state, populated content state, error state, and loading/shimmer state.
  * Wrap previews in `TrainingTrackerTheme` to ensure accurate font scaling, shape, and palette rendering in Android Studio preview panes.
* **9-Language Localization Parity**:
  * Any user-visible string (titles, subtitles, button labels, descriptions, error dialogs, tooltips) must be localized across all 9 supported application locales (`values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, `values-it/strings.xml`, `values-ja/strings.xml`, `values-nl/strings.xml`, `values-pl/strings.xml`, `values-pt/strings.xml`).
  * Hardcoding untranslated string literals in Kotlin code or Compose layouts is strictly forbidden.
* **Prohibition of Decorative Inline Unicode Emojis in Localized Strings (ATT-5400)**:
  * Localized string resources for dialogs, bottom sheets, cards, and banners **MUST NOT** contain decorative inline Unicode emojis (e.g. 🚴‍♂️, 💎, ✨, 😉).
  * UI layouts must rely on dedicated Material vector icons or custom drawables (e.g. in the dialog's `icon` slot) to convey iconography, avoiding visual clutter, text wrapping anomalies, and duplication of visual motifs.
* **Positional Format Specifiers Invariant**:
  * When formatted strings contain multiple dynamic arguments, they **MUST** use indexed positional specifiers (e.g. `%1$s`, `%2$d`, `%1$f`) rather than unindexed specifiers (e.g. `%s`, `%d`).
  * Unindexed specifiers fail Android Lint and crash or misorder text in languages whose grammatical syntax reorders subject and object (such as Japanese, German, or Polish).
* **Font Scaling & Layout Truncation**:
  * UI layouts must gracefully handle user font scale settings up to 130% without clipping labels or overlapping adjacent icons. Use `TextOverflow.Ellipsis`, soft wrapping, or flexible container heights.

---

## 5. Visual Consistency Baseline ("Look Like the Rest of the App")

> Origin: Retro Sprint 2026-40.16 — ATT-1835 (Quick Route Selector), ATT-1953 (Take Me Home HUD) and ATT-2058 (tile variants) were functionally correct but did not look/feel like the rest of the app (follow-ups ATT-2456 … ATT-2462). The values below are the de-facto standard measured across the existing codebase. Binding for every ticket that adds or changes UI (Governance Rule 23).

### 5.1 Reuse Existing UI Components & Visual Patterns (Visual Baseline)
* Before designing a new screen, card, sheet, HUD element or button, find the **closest existing equivalent** in the app and copy its structure, shapes, spacing and colors.
* Prefer shared components from `ui/components/` (e.g. `MetricItem`, `MetricBadge`, `DropdownSelector`, `EmptyStatePlaceholder`, `DeleteConfirmationDialog`, `BottomSheetUtils`, `MappableListItem`) and `ui/common/`.
* Tabbed screens follow the existing tabbed layout pattern: `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` title row + standard Material 3 tab row. Do not invent alternative tab/segment visuals for top-level navigation.
  * **Long, scrollable list / browse screens** (Workouts, Segments, Routes, Equipment, Periods — `RouteTabbedScreen`, `EquipmentTabsScreen`): collapsing quick-return header (`CollapsingAppBarNestedScrollConnection`) to gain vertical space.
  * **Settings / form screens** (e.g. Auto-Pause, Sport Types, short configuration pages): **fixed header** — title row, header actions (reset, …) and the tab row stay visible at all times; do **not** use `CollapsingAppBarNestedScrollConnection` (Rule 66 — ATT-4836 / ATT-4924: the collapsing header made the sport tabs and the reset action disappear on a short settings form).
* New one-off styles (custom shapes, colors, fonts, icon sets, branding) are only allowed with an explicit justification in the Stage 3 plan (Section "UI Consistency").

### 5.2 Spacing Scale
* Use only: `4.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp` (exceptions require justification).
* Screen / card horizontal content padding: `16.dp` (dominant), compact cards `12.dp`.
* Vertical spacing between list rows / small elements: `4.dp`–`8.dp`.

### 5.3 Shapes & Dialog/Sheet Presentation
* Cards, sheets, panels, HUD overlays: `RoundedCornerShape(12.dp)` (dominant) or `MaterialTheme.shapes.medium`.
* Small elements (badges, inner tiles, chips): `8.dp`; tiny markers / bars: `4.dp`.
* Dialogs: Material 3 defaults (`MaterialTheme.shapes.extraLarge`).
* **Bottom Sheet vs. AlertDialog Standard (ATT-5399)**:
  * Interactive selection popups, editors, upgrade nudges, and celebratory prompts **MUST** be implemented as bottom popups using `AppModalBottomSheet` rather than centered `AlertDialog`s, ensuring optimal one-handed thumb reachability and edge-to-edge system insets.
  * Centered `AlertDialog` is strictly reserved for concise 1–2 sentence destructive confirmations (`DeleteConfirmationDialog`, `WorkoutDeleteDialog`) or transient tracking HUDs (`LapSummaryDialog`).
* **Stacked Full-Width Action Button Pattern for `AppModalBottomSheet` (`Rule 73` — `ATT-5399` $\rightarrow$ `ATT-5468`, `ATT-5470`)**:
  * `AppModalBottomSheet.actions` is a single horizontal `RowScope`. Whenever a bottom sheet has **$\ge 3$ action buttons** OR **any button label exceeds ~15 characters** across the 9 supported locales (especially German, e.g. `"Tauschen & Koppeln"`, `"Upgrade auf Gold Pro"`, or `"Unbegrenzte Garage freischalten"`), primary and secondary CTAs **MUST** be placed as **full-width vertically stacked buttons** (`Button` / `OutlinedButton` with `Modifier.fillMaxWidth()`) at the bottom of the sheet's `content` column (modeled on `FirstJubileeCelebrationDialog.kt`, `SwapOrUpgradeDialog.kt`, and `GarageUpgradeNudgeDialog.kt`), leaving only a single full-width dismiss/secondary `TextButton(Modifier.fillMaxWidth())` in `actions` (or at most 2 short 1-word buttons like `"Abbrechen"` / `"Speichern"`). Never pack 3 buttons or multi-word German CTAs horizontally inside `actions`.

### 5.4 Color
* Surfaces, text and accents come from `MaterialTheme.colorScheme` so that Light, Dark and **AMOLED** themes all render correctly. Verify new UI in all three.
* Domain semantics come from `TTColor` (zones, routes, start/end points, branding). Add a new `TTColor` entry instead of an inline `Color(0x…)`.
* **Route & Navigation Domain Tints**: Route geometry and trajectories on the map use Royal Blue tones (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`). Consequently, interactive UI elements, cards, and buttons specifically associated with route selection or navigation (e.g. the Route Selection button on the Control Tracking screen in ATT-2458) should incorporate a subtle Royal Blue touch (e.g. subtle Royal Blue border accent, tinted icon, or gentle container tint) to establish an intuitive semantic connection with the map's Royal Blue route visualization, while keeping the effect minimal and harmonious with the theme.
* **Semantic Alert & Warning Containers (ATT-3313 / ATT-3451)**:
  * In-app alert snackbars, badges, and warning banners **MUST NOT** use default primary blue for warnings or alerts. Blue conveys active navigation or informative telemetry, not hazard states.
  * Use dedicated semantic warning containers:
    * **Low / Warning Level** (e.g. low battery $\le 20\%$): Amber/yellow warning container with high-contrast text.
    * **Critical / Danger Level** (e.g. critical battery $\le 10\%$, sensor lost): `MaterialTheme.colorScheme.errorContainer` / error red with `onErrorContainer` text.
* Never hardcode `Color.White` / `Color.Black` for text or backgrounds in normal app UI (cockpit tiles follow `CockpitThemeMode`).

### 5.5 Typography & Icons
* Text styles from `MaterialTheme.typography`; emphasis via `FontWeight.Bold` / `SemiBold` (no custom font families).
* Icons from Material Icons (`Icons.Default.*`, `Icons.AutoMirrored.*` for directional icons). Map POIs/markers should use a consistent standard icon set (see ATT-2461) rather than ad-hoc drawn shapes.
* **Unambiguous Action Iconography & No ASCII Symbol Prefixes in Strings (Rule 49 — ATT-4286, ATT-4288)**:
  * **Never Use Minus-Like Icons (`HorizontalRule`) for Insert/Add Actions**: Never use `Icons.Default.HorizontalRule` (`—`) in a toolbar next to `+` (`Icons.Default.Add`) to represent inserting a section divider or item; a horizontal bar next to `+` visually reads as a minus/remove button (`-`). Use a dedicated split/section icon or labeled menu item.
  * **Never Include Literal `"+ "` Prefixes Inside Localized `<string>` Resources**: When a button, chip, or action item renders a leading `Icon(Icons.Default.Add, ...)`, the corresponding `<string>` resource in `strings.xml` **MUST NOT** start with a literal `"+ "` character (which produces duplicate `+ + Pair Sensor` labels on screen). Keep icon graphics in Compose `Icon` slots and keep XML strings purely textual.
* **Vector Header Iconography over Inline Unicode Emojis (ATT-5400)**:
  * Dialogs, alerts, and bottom sheets **MUST** provide dedicated Material or custom vector icons in their composable icon slot (e.g. `AlertDialog(icon = { Icon(...) }, ...)`), rather than peppering localized title or message strings with decorative inline Unicode emojis. Inline emojis create visual clutter, rendering inconsistencies across Android OEM fonts, and icon-to-text redundancy.
* **Semantic Category Icon Tinting (Rule 23)**: When rendering paired or symmetric category items across lists, tables, or tabbed views (e.g. equipment categories like bikes and shoes in `EquipmentSensorMatrixScreen`):
  * **Unified Active Role**: All active entities across categories MUST use the same unified brand color token (`MaterialTheme.colorScheme.primary`). Do NOT assign disparate color roles (e.g. `primary` to bikes and `secondary` to shoes) to differentiate categories; visual distinction is provided by the vector drawables themselves (`ic_equipment_bike` vs `ic_equipment_shoe`) and section/tab titles.
  * **Status Tinting**: Use `MaterialTheme.colorScheme.outline` for retired/inactive entities, and `MaterialTheme.colorScheme.error` for failed/unhealthy states.
* **Directional & Navigation Iconography Family Harmony (ATT-3455 & ATT-3493)**:
  * All directional vector drawables representing an enum family (e.g. `TurnDirection` — straight, slight, normal, sharp, u-turn) must adhere to identical geometric constraints:
    * Standard 24x24 viewport.
    * Base stem rising from a consistent bottom baseline (`y=19..20`) with 2dp uniform stroke width.
    * Symmetric, canonical Material Design arrowheads across complementary directions (left / right).
    * True geometric angle transitions: gentle 45° for slight turns, 90° for normal turns, acute ~135° for sharp turns, 180° loop for u-turns.
  * Any modification to directional iconography **MUST** be verified side-by-side across all enum variants in both Light and Dark themes via a dedicated Compose `@Preview` gallery (e.g. `TurnCueIconsGalleryPreview`) before release.
* **1:1 Visual Fidelity & True Geometric Primitives When Vectorizing Raster Icons (Rule 37 — ATT-2859 / ATT-3679)**:
  * When migrating legacy multi-density raster PNG drawables (such as sport icons `bsport_bike`, `bsport_run`, `bsport_other` or ANT+/BLE sensor badges) to XML `VectorDrawable`s, the vector paths **MUST** faithfully redraw the exact visual design, multi-color geometry, and badge layout of the existing icons—never replace custom multi-color icons or protocol badges with generic monochrome Material Symbols.
  * **Authoritative Original Vector Sources (`/home/rainer/Dropbox/workspace/`)**: Always check `/home/rainer/Dropbox/workspace/` (`aTrainingTracker_Images/`, `Images/`, `Logos/`, `featureGraphic/`) first for original `.fig` (`xfig`), `.svg` (Inkscape), and `.xcf` (GIMP) master files before attempting any manual reconstruction. Convert existing `.svg` / `.fig` master geometry directly into Android `VectorDrawable` paths rather than approximating by hand.
  * **Prohibition of Naive Polygon Auto-Tracing**: Never use naive marching-squares / Ramer-Douglas-Peucker (`M...L...Z` straight-line polygon) auto-tracing scripts on raster icons without `fillType="evenOdd"` or layer reconstruction. Naive colour-mask tracing produces jagged polygonal edges, fills in inner holes (wheels, stopwatch rings, letters like `R`, `P`, `D`, `B`), and severs occluded shapes where foreground layers overlap background wheels/frames. Construct icons from true geometric primitives (circles, `A` arcs, rounded strokes in back-to-front Z-order, and `evenOdd` compound paths) and visually verify a rendered bitmap 1:1 against the `xxxhdpi` reference PNG.

### 5.6 Placement, Entry Points & Screen Headers
* New features are entered from the screen where the athlete expects them (e.g. route selection on the Control Tracking screen, ATT-2458) using the same button/chip style as neighbouring actions — not via new floating or branded elements.
* **Compact Icon Actions in Top Headers (ATT-3184, ATT-3188, ATT-3675)**:
  * Top screen headers (`Row`) **MUST NOT** pack multiple multi-word `TextButton`s alongside a title and status chip on a single horizontal line. Across 9 localized languages (especially German, e.g. `"Auf Standardwerte zurücksetzen"`), horizontal text buttons starve the layout width and wrap vertically 1 character per line across the screen. Use compact `IconButton` symbol actions (with accessible `contentDescription` tooltips) or a wrapping `FlowRow`.
* **No Single-Character or Word Wrapping in Dense Horizontal Chip / SegmentedButton Rows (Rule 38 — ATT-3184, ATT-3676)**:
  * Never pack 5–7 Material 3 `FilterChip`s (especially with `leadingIcon`s) into a single `Row` using `Modifier.weight(1f)`. M3 `FilterChip` reserves ~24dp of internal horizontal padding plus icon spacing, leaving only ~10dp for text on 7-zone rows and wrapping `"Z1"` into `"Z\n1"`. Use compact custom bordered/tinted pills without `leadingIcon` overhead, and always enforce `maxLines = 1, softWrap = false` across all 9 locales (including long German labels like `"Histogramm"` in `SegmentedButton`s).
* **System Navigation Bar Inset Clearance on Scrollable Screens (Rule 41 — ATT-3704)**:
  * Every full-screen scrollable container (`Column(Modifier.verticalScroll(...))` or `LazyColumn`) hosted inside an edge-to-edge Activity (such as `MainActivityWithNavigation`) **MUST** account for the bottom Android system navigation bar insets (via `Modifier.navigationBarsPadding()` after `verticalScroll`, or `WindowInsets.navigationBars.asPaddingValues()` in `LazyColumn(contentPadding = ...)`).
  * Omitting bottom system bar insets causes the bottommost card or action rows to be partially hidden behind the gesture or 3-button Android navigation bar when scrolled all the way down.

### 5.7 In-Ride Navigation Cues & HUD Overlays
* **Top-Level Spatial Overlay**: In-ride navigation hints, turn-by-turn cues (e.g. ATT-1450), and fork-in-the-road decision prompts (e.g. ATT-1955) must float directly on top of the active tracking screen elements (cockpit tiles, map view) rather than displacing or squeezing the cockpit tile layout.
* **Solid Opaque Surfaces for Maximum Contrast**: Overlays must use a permanently solid, 100% opaque surface container background (e.g. `surfaceContainer` / `primaryContainer` / `secondaryContainer` / `surface` at 100% opacity) paired with a subtle Royal Blue border accent (`TTColor.RouteActiveNavigation`), preventing background road lines, contours, and telemetry text bleed-through under direct sunlight and high ambient brightness (ATT-3322).
* **Transient Auto-Dismiss (3–5 Seconds)**: Navigation cues are time-sensitive and ephemeral. Upon being triggered (or once a maneuver instruction is issued), the cue must automatically vanish after a brief, configurable interval (3–5 seconds) via a smooth animation (fade or slide), promptly returning the screen to full telemetry focus without requiring manual dismissal while riding.
* **Expert Settings Customization**: Auto-dismiss duration (e.g. 2–8 seconds or persistent until passed, default 4 seconds) and countdown distance (30–300 m, default 100 m) are exposed as configurable parameters in Expert Settings (*Experteneinstellungen*), allowing athletes to tailor cue timing to their riding speed and sport discipline (ATT-3321, ATT-3322).

### 5.8 Bottom Sheets (`AppModalBottomSheet`, Live Segments & Climbs) & Modal Overlay Composition Retention
* **Modal Overlay Composition Retention — Never Unmount Host Screens When Opening a Sheet or Dialog (Rule 47 — ATT-4285)**:
  * Modal bottom sheets (`AppModalBottomSheet`, `ModalBottomSheet`) and dialogs (`AlertDialog`) **MUST** be rendered as overlays *alongside* the underlying host screen (e.g. `RouteTabbedScreen`, `EquipmentSensorMatrixScreen`) in the Compose tree—**NEVER** inside a mutually exclusive `if (editingItem != null) { Sheet(...) } else { HostScreen(...) }` branch.
  * Unmounting the underlying screen while a modal sheet is open destroys non-saveable composition state and causes `PrimaryScrollableTabRow` (`rememberScrollState(0)`), `LazyListState`, and collapsing header states to visibly jump or reset when the modal closes.
* **No Nested `verticalScroll` Inside `AppModalBottomSheet` (ATT-3274 / ATT-3679)**:
  * `AppModalBottomSheet` defaults to `scrollable = true`, which wraps its content slot in a `Column(Modifier.verticalScroll(rememberScrollState()))`. Child screens hosted inside `AppModalBottomSheet` **MUST NOT** apply a second `Modifier.verticalScroll` (which crashes Compose with `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`), or must explicitly pass `scrollable = false` to `AppModalBottomSheet`.
* **Mandatory Bottom Popup Standard for Interactive Dialogs & Editors (ATT-5399 / REQ-UI-149)**:
  * All user-interactive configuration dialogs, device swap prompts (`SwapOrUpgradeDialog`), fleet upgrade nudges (`GarageUpgradeNudgeDialog`), milestone celebration prompts (`FirstJubileeCelebrationDialog`), and multi-entry history editors (`ZoneHistoryDialog`, `ZoneSnapshotEditDialog`) **MUST** use `AppModalBottomSheet`. When dialogs have long action labels (> 15 chars), they MUST use the Rule 73 stacked full-width button pattern (modeled on `FirstJubileeCelebrationDialog`, `SwapOrUpgradeDialog`, and `GarageUpgradeNudgeDialog`).
  * **Nested LazyColumn Safety**: When a bottom sheet hosts an internal `LazyColumn` or `LazyRow` (such as snapshot history in `ZoneHistoryDialog`), the parent sheet **MUST** be configured with `scrollable = false` to prevent infinite-height measurement exceptions.
* **Default Fully Expanded Presentation**:
  * Live Segment (`LiveSegmentSheet`) and Climb (`LiveClimbSheet`) bottom sheets during active tracking (`SensorGridScreen`) must pop up **fully expanded** (`SheetValue.Expanded`) by default instead of partially expanded (`SheetValue.PartiallyExpanded` / peek height).
  * This provides immediate visibility of the elevation profile graph, instantaneous gradient profile, and live pacing telemetry HUD without requiring manual touch interaction or swipe gestures while riding or running.
* **Dismissal and Collapse Ergonomics**:
  * The bottom sheet retains swipe gestures (`sheetSwipeEnabled = true`), allowing athletes to manually collapse to peek height or dismiss if they intentionally wish to focus on underlying sensor tiles.

### 5.9 Default Tracking Screens Configuration (Fresh Install)
* **Purpose-Driven Screen Archetypes**:
  * On a clean install or database creation (`TrackingViewsDbHelper.onCreate()`), three focused tracking tabs are generated per activity type, each tuned to a distinct athletic perspective:
    1. **Standard** (`R.string.text_default`): Primary telemetry cockpit. Configured with in-ride intelligence enabled (**Navigation Hints**, **Live Segments**, and **Live Climbs**), without map canvas or lap button clutter.
    2. **Map** (`R.string.tab_map`): Spatial guidance perspective. Configured with **Map Canvas**, **Elevation Profile**, and **Navigation Hints** enabled; live climb and segment bottom sheets are disabled to preserve an unobstructed map canvas.
    3. **Laps** (`R.string.tab_laps`): Structured interval perspective. Configured with the **Lap Button** enabled; navigation cues, map, elevation profile, live climbs, and live segments are disabled to keep pure focus on lap splits.
* **Canonical Default Toggle Matrix**:

| Tracking Tab | Map Canvas | Elevation Profile | Lap Button | Live Segments | Live Climbs | Navigation Hints |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Standard** (`Layout 1`) | ❌ Off | ❌ Off | ❌ Off | ✅ **On** | ✅ **On** | ✅ **On** |
| **Map** (`Layout 2`) | ✅ **On** | ✅ **On** | ❌ Off | ❌ Off | ❌ Off | ✅ **On** |
| **Laps** (`Layout 3`) | ❌ Off | ❌ Off | ✅ **On** | ❌ Off | ❌ Off | ❌ Off |

### 5.10 `WorkoutDetails` & Summary Card Grouped Metric Rows (`AltitudeRow` Pattern)
* **Canonical Single-Line Grouped Row Layout (ATT-3858, ATT-3930, ATT-4042)**:
  * Domain-grouped summary metrics rendered inside `WorkoutDetails.kt` (shared by `WorkoutSummary` cards and `TrackOnMapScreen` / `MapDetailLayout`, such as Elevation, Power Training Metrics `NP/IF/TSS`, Aerobic Efficiency `EF & Decoupling`, and Anaerobic Battery `CP/W'/W'bal`) **MUST** follow the single-line `AltitudeRow` layout pattern:
    1. **Left Domain Header Column**: A `Column` displaying the localized domain category label (`style = MaterialTheme.typography.labelMedium`, `color = MaterialTheme.colorScheme.onSurfaceVariant`), a `Spacer(modifier = Modifier.height(4.dp))`, and a `24.dp` domain `Icon` (`tint = MaterialTheme.colorScheme.onSurfaceVariant`), followed by `Spacer(modifier = Modifier.width(20.dp))`.
    2. **Single Horizontal Row of 2–3 Primary `MetricItem`s**: A **single** `Row(modifier = Modifier.fillMaxWidth())` containing **at most 2–3** equal-weight `MetricItem` cells (`layout = MetricLayout.VERTICAL`, `modifier = Modifier.weight(1f)`), where each `MetricItem` displays its short metric label above (`labelMedium`, `onSurfaceVariant`) and bold formatted value (with units) below. Do **NOT** stack multiple `Row`s of metrics under one domain icon in `WorkoutDetails.kt`; select only the 2 or 3 most important metrics for the summary row.
    3. **Clear Visual Separation Between Consecutive Domain Rows**: Separate consecutive domain rows with adequate vertical padding (e.g. `padding(vertical = 4.dp)` or `8.dp` spacing) and a subtle `HorizontalDivider` (`thickness = 0.5.dp`) so adjacent rows do not visually run together.
* **Anti-Patterns (Strictly Forbidden)**:
  * ❌ **Do NOT stack multiple horizontal `Row`s (e.g. 2 rows of 3 metrics) under a single domain header** in `WorkoutDetails`. Keep each domain row strictly to a single line of 2–3 primary metrics.
  * ❌ **Do NOT use `FlowRow` pill/chip badges (`Surface(color = surfaceContainerHighest)` with `"NP: 175 W"`)** for workout summary metrics. Wrapping pill clouds break the tabular visual alignment of `WorkoutDetails` and leave trailing chips orphaned on new lines.
  * ❌ **Do NOT render raw inline `Text("EF: 1,32 W/bpm")` lines** without a domain header column and `MetricItem` structure.

```kotlin
// Canonical Single-Line Grouped Metric Row Pattern in WorkoutDetails.kt (AltitudeRow reference):
Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Column {
        Text(
            text = stringResource(R.string.domain_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_domain_icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.width(20.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        MetricItem(
            iconRes = R.drawable.ic_metric_1, // or optional icon
            label = stringResource(R.string.metric_1_short),
            value = formattedValue1,
            layout = MetricLayout.VERTICAL,
            modifier = Modifier.weight(1f)
        )
        MetricItem(
            iconRes = R.drawable.ic_metric_2,
            label = stringResource(R.string.metric_2_short),
            value = formattedValue2,
            layout = MetricLayout.VERTICAL,
            modifier = Modifier.weight(1f)
        )
    }
}
```

### 5.11 Cockpit & Form Section Dividers: Prominent Contrast & Centered Titles (Rule 49 — ATT-4286)
* **Visual Prominence**:
  * Section dividers inside dense telemetry grids (`SensorGridScreen`) must be clearly distinguishable at a glance while riding, using a thick stroke (`thickness >= 2.dp` or `3.dp`, or a tinted pill/banner container) with high-contrast color tokens (`MaterialTheme.colorScheme.primary` or `onSurfaceVariant` at high opacity) rather than a faint `1.dp` hairline surrounded by empty whitespace.
* **Centered Section Titles**:
  * When a section divider includes a title, the title **MUST** be horizontally centered across the full row width (flanked by symmetric `HorizontalDivider(modifier = Modifier.weight(1f))` segments on both left and right, or centered inside a full-width section header bar), never left-aligned with a single trailing line.

### 5.12 Cockpit Utility Button Availability & Click/Pointer Gesture Isolation (Rules 53 & 58 — ATT-4371, ATT-4431)
* **Availability Across `READY`, `TRACKING`, and `PAUSED` Modes**:
  * Unlike `LapButton` (which records a workout lap split and therefore requires `TrackingMode.TRACKING`), auxiliary Cockpit utility controls such as the **Bike Bell** (`BikeBellButton`) **MUST** remain enabled across `READY`, `TRACKING`, and `PAUSED` modes (`enabled = isConfigureMode || trackingMode in setOf(READY, TRACKING, PAUSED)`) so athletes can test or ring the bell while stationary or before pressing Start.
* **Never Nest `ElevatedButton(onClick)` Inside `Modifier.pointerInput(detectTapGestures)` — Preserve Press-and-Hold via `InteractionSource` (Rules 53 & 58)**:
  * Do not wrap a Material `Button`/`ElevatedButton` with its own `onClick` handler inside a parent `Box(Modifier.pointerInput { detectTapGestures(...) })`, which causes pointer event contention and swallows clicks.
  * When a button supports both single-tap (`onClick`) and press-and-hold / long-press actions (such as `BikeBellButton`'s continuous `"Brriiiing!"` roll), never delete the long-press capability to fix click contention; instead observe `PressInteraction.Press` / `Release` / `Cancel` on the button's `MutableInteractionSource` (or use a `Surface` with `combinedClickable`) so both single-tap and press-and-hold work reliably.

### 5.13 Dialog Width Budgets, Multi-Chip `FlowRow` Wrapping & Long-Locale (`de`) Verification (Rule 54 — ATT-4372)
* **No Non-Wrapping 3+ Chip `Row` Inside `AlertDialog`**:
  * Standard Material 3 `AlertDialog` containers have a narrow horizontal width (`~280.dp–320.dp`). Placing 3 or more `FilterChip`s (e.g., `Cycling`, `Running`, `Other` / `Radfahren`, `Laufen`, `Andere`) inside a single non-wrapping `Row(Modifier.fillMaxWidth())` crushes the trailing chip into a 1-character-per-line vertical stack (`A / n / d / e / r / e`).
  * Multi-chip selectors inside dialogs **MUST** use `FlowRow` (so chips wrap cleanly as intact horizontal pills) or `AppModalBottomSheet`.
* **Verbose Locale (`de`) Segmented Button Width Check**:
  * Avoid squeezing long multi-word labels (`"Auf alle anwenden"`) into a 2-button `SingleChoiceSegmentedButtonRow` inside a narrow `AlertDialog` where labels truncate mid-word (`"Auf alle anwen"`). Prefer concise localized labels, stacked radio/card rows, or a full-width `AppModalBottomSheet`.

### 5.14 Shared Composable Height Constraints Across Bounded vs. Scrollable Callers (Rule 67 — ATT-4878 / ATT-4936)
* **Guard `.fillMaxHeight()` & Vertical `.weight(1f)` Against Unbounded Scroll Containers (`ATT-4878` $\rightarrow$ `ATT-5071`)**:
  * When adding `.fillMaxHeight()` or vertical `Modifier.weight(1f)` inside a shared composable (e.g. `SensorFieldView`) to equalize row heights inside a bounded `Row(Modifier.height(IntrinsicSize.Min))` caller (`SensorGridScreen`), audit **every call site** (`grep -rn "<ComposableName>(" app/src/main/`)—especially standalone previews embedded directly inside a `verticalScroll` `Column` or `LazyColumn` (`AthleteProfileScreen`), where max height is `Constraints.Infinity` unless `Modifier.height(IntrinsicSize.Min)` is preserved on an enclosing container. Verify every call site on-device during Stage 5.

### 5.15 Map Entity Bottom-Sheet Peek & Expansion Parity & Micro-Typography Line-Height Bounding (Rules 68 & 69 — ATT-5075, ATT-5076, ATT-5196 / ATT-5082, ATT-5202)
* **Reuse List-Item Header Composables & `MapDetailLayout` Without Duplicate Metadata (`ATT-5075` $\rightarrow$ `ATT-5196` $\rightarrow$ `ATT-5279`)**:
  * Map bottom sheets for domain entities (`Segments`, `Routes`, `Lieblingsstrecken`) in `MapScreenWithTrack.kt` **MUST** reuse the entity's canonical list-item header/metadata composables (`SegmentHeader`/`SegmentDetails`, `RouteHeader`/`RouteDetails`, `WorkoutClusterIdentityRow`/`WorkoutClusterMetadataBlock`) inside `MapDetailLayout(useStatusBarsPadding = false, showMap = true, onHeaderHeightMeasured = ...)`:
    1. When **peeked** (collapsed), only the `header` slot is visible above the main map.
    2. When **expanded** (popped up), the lower section displays the detailed sub-entity map (`showMap = true`, matching `RouteOnMapScreen` and `SegmentOnMapScreen`), and lower content slots (`metadataContent`) **MUST NEVER** repeat or duplicate statistics already rendered in the `header` (`ATT-5279`).
    3. **Visible Drag-Handle Bar (`Rule 74` — `ATT-5485` $\rightarrow$ `ATT-5564`)**: Every expandable or draggable peek bottom sheet (`BottomSheetScaffold`, `ModalBottomSheet`, or custom peek sheet in `WorkoutClusterHeatmapScreen` / `MapScreenWithTrack`) **MUST** display a visible top drag-handle indicator pill (`BottomSheetDefaults.DragHandle()` or a `32.dp × 4.dp` rounded pill) so the user immediately sees that the popup can be pulled upwards.
* **Explicit `lineHeight` on Micro-Typography (`<= 10.sp`) Inside Compact Fixed-Height Boxes (`ATT-5076` $\rightarrow$ `ATT-5193`)**:
  * When rendering small labels (`fontSize = 9.sp` or `10.sp`, such as day-of-week labels `M`, `W`, `F`, `S` in `YearlyContributionGrid`) inside a compact fixed-height `Box(Modifier.height(11.dp))`, always set an explicit `lineHeight = fontSize` and `PlatformTextStyle(includeFontPadding = false)` on the `TextStyle`. Inheriting `labelSmall`'s default `lineHeight = 16.sp` inside an `11.dp` box clips the bottom 1–2 px of tall glyphs like `F`.

### 5.16 Adaptive Icon `<adaptive-icon>` Mipmap Restriction in Compose & Mandatory Visual Screenshot Inspection (Rule 71 — ATT-1406, ATT-1482, ATT-4853 / ATT-5313)
* **Never Pass `R.mipmap.*` (`<adaptive-icon>`) to Compose `painterResource` / `safePainterResource` (`ATT-1406` $\rightarrow$ `ATT-5372`)**:
  * Jetpack Compose `painterResource` only supports `<vector>` (`VectorDrawable`) and raster bitmaps (`PNG`, `JPG`, `WEBP`). Passing an `<adaptive-icon>` XML resource (`R.mipmap.ic_launcher*`) throws `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported` at runtime. Always pass the inner foreground `VectorDrawable` (`R.drawable.ic_launcher_foreground*`) or load the adaptive icon via `ContextCompat.getDrawable(context, mipmapRes)` + `rememberDrawablePainter`.
* **Preserve Xfig X-Splines (`3 0 ...`) & Full-Bleed `logo_512.png` (`ATT-1482`)**:
  * When converting Xfig `.fig` master files (`logo.fig`), never treat interpolated X-splines (`3 0 ...`) as straight-line polygons (`M...L...`). Always evaluate smooth spline curves with round caps (`strokeLineCap="round"`) and verify 1:1 visual parity against `/home/rainer/Dropbox/workspace/TrainingTracker4/Bilder/logo.png` (`993x872` RGBA), and never overwrite full-bleed `drawable/logo_512.png` or `images/logo_512.png` with padded `108dp` adaptive canvas bitmaps.
* **Mandatory `read_file` Visual Inspection of All Captured Screenshots & Reference Attachments (`Rule 71 & Rule 72` — `ATT-1406`, `ATT-4853`, `ATT-2274`, `ATT-5375`)**:
  * Every screenshot or generated visual asset (`img/*.png`, `screenshots/*.png`, `video/frames/*.png`) **AND** any reference design attachment in `docs/attachments/*.png` must be inspected via `read_file` in Stages 1, 4, and 5 to confirm 1:1 visual parity without mid-word button wrapping (`Suppor\nt`), clipped header pills, untranslated strings, or crash fallbacks. When embedding text in `VectorDrawable` badges/ribbons, convert true font glyphs via `inkscape --export-text-to-path` rather than hand-coding geometric stick polygons.
* **Adaptive Icon (`108dp`) Safe-Zone Compensation in In-App Compose Slots (`Rule 72` — `ATT-5371` / `ATT-5375`)**:
  * `ic_launcher_foreground_*.xml` is a `108dp × 108dp` adaptive icon foreground where the artwork occupies ~55% (`59.27 / 108`) inside the central safe zone. When rendering `ic_launcher_foreground_*.xml` inside an in-app UI slot expecting full-bleed `R.drawable.logo_512` proportions (such as `DrawerHeader` in `AppNavigationDrawer.kt`), scale the foreground by `1.81f` (`graphicsLayer { scaleX = 1.81f; scaleY = 1.81f }` inside `Box(Modifier.size(46.dp))`) and keep `DrawerHeader` strictly to its original single-line layout without text pills below `"a Training Tracker"`.

### 5.17 Workout Summary Visual Cohesion, Advanced Settings Wiring & Stable Detail Pager Host (`Rule 73` — `ATT-43`, `ATT-907`, `ATT-908` / `ATT-5402`)
* **Neutral Section Iconography & Divider Cadence in `WorkoutSummary` (`ATT-43` $\rightarrow$ `ATT-5474`)**:
  * Passive section headers and icons inside `WorkoutSummary.kt` (*Zeit aktiv*, *Distanz*, *Höhenmeter*, *Geschw.*, *Höhe*, *Fotos*) **MUST** use neutral `MaterialTheme.colorScheme.onSurfaceVariant` (dark grey) and standard `HorizontalDivider` separators, reserving `MaterialTheme.colorScheme.primary` (bright blue) strictly for interactive header links (Edit button, Route chip, Date/Time). Never render passive section icons or square action tiles with clipped multi-word German labels (`"Fotos hinzufügen"`) in primary blue.
* **Mandatory Advanced Settings (`WorkoutMasksAndCardsSection`) Integration & Summary Default `false` for New Summary / Detail Sections (`Rule 73, Rule 74 & Rule 75` — `ATT-43` $\rightarrow$ `ATT-5476`, `ATT-5487` $\rightarrow$ `ATT-5563` $\rightarrow$ `ATT-5654`, `ATT-5474` $\rightarrow$ `ATT-5655`)**:
  * Whenever a ticket introduces a new visual section, photo strip, or distribution card into `WorkoutSummary.kt` (Summary Cards) or `WorkoutDetailTabbedContent.kt` (Workout Details), it **MUST** be wired into `WorkoutCardPreferences` / `WorkoutDetailPreferences`, `WorkoutSectionType` / `WorkoutDetailSectionType`, and `WorkoutMasksAndCardsSection.kt` (*Experten-Einstellungen -> Trainingsmasken & Karten*) as a **first-class sortable checkbox entry inside the section lists** (*Summary-Karten* and *Detail-Tabs*) with separate visibility defaults for Workout Details (default `true`) and Workout Summary (default `false` for secondary distribution cards so list summaries remain compact).
  * **No Subordinate Visibility Switches (`ATT-5654`) & No Unconditional `DETAILS` Fallback Rendering (`ATT-5655`)**: Never place a section's visibility toggle as a standalone `Switch` tucked underneath another section's mode selector (e.g. under *Zonenkarten-Anzeigemodus*). Furthermore, `WorkoutDetailTabbedContent.kt` **MUST** render sections strictly 1:1 from `WorkoutDetailTabConfig` — never inject a fallback block (`if (!anyTabHasPhotos) ...`) into `WorkoutDetailSectionType.DETAILS` that keeps the section visible after the user disables it in Expert Settings.
  * **Photo Thumbnail Row Sizing & Proportional Bitmap Decoding (`ATT-5655`, `ATT-5760` / `Rule 76`)**: In `WorkoutPhotoStrip`, `WorkoutPhotoThumbnailSize.LARGE` (*Groß*) renders 1 photo per row at full row width (`fillMaxWidth()`), `MEDIUM` (*Mittel*) renders 2 photos per row at half row width each, and `SMALL` (*Klein*) renders compact tiles (`76.dp`) in a horizontal strip. Whenever an image/thumbnail container scales across size tiers (`SMALL` / `MEDIUM` / `LARGE` or full-width cards), the bitmap downsampling target (`decodeSampledBitmap` `maxDim`) and `remember`/`LaunchedEffect` keys **MUST** scale proportionally with the target pixel width (never reuse a static `maxDim = 240` for half-width or full-width cards).
  * Reserve physiological `"Zonen"` / `"Z1..ZN"` terminology strictly for physiological training zones (Heart Rate, Power); for environmental or physical distributions (such as Grade/Slope), use `"Verteilung"` (*Steigungsverteilung* / *Slope Distribution*) and display the actual category bounds (`<2%`, `2–5%`, ..., `>25%`) on bar and column axes rather than abstract `Z1..Z7` labels (`ATT-5563`).
* **Map Track Color Semantics & Unambiguous Action Icons (`ATT-907` $\rightarrow$ `ATT-5480`)**:
  * Primary workout tracks on aftermath and editor maps (`CreateLapOnMapSheet`, `MoveLapBoundaryOnMapSheet`) **MUST** use `TrackType.BEST` (Royal Blue) rather than `TrackType.GPS` (Green). Never reuse domain-specific telemetry icons (such as `R.drawable.ic_distance` / `max_line_distance`) for unrelated actions like splitting a lap; create or use a semantically matching vector icon (e.g. `Icons.Default.ContentCut` scissors for lap split).

### 5.18 Sensor List Sort Stability, Essential Sensor Protection & Grounded Milestone/Supporter Copy (`Rule 75` — `ATT-5467` $\rightarrow$ `ATT-5658`/`ATT-5659`, `ATT-5470` $\rightarrow$ `ATT-5657`, `ATT-5490` $\rightarrow$ `ATT-5660`)
* **Stable Sensor List Ordering Across Pairing Toggles (`ATT-5660`)**:
  * In `DeviceListViewModel.getFilteredDevices`, sensor rows **MUST** maintain a stable position (grouped by availability/activity and ordered by device type/name) and **MUST NOT** re-sort by `isConnected` (pairing state). Re-sorting by `isConnected` causes sensors to jump vertically in the list while the user is clicking through pairing checkboxes.
* **Never Recommend Unpairing `Google Fused` in `SwapOrUpgradeDialog` (`ATT-5657`)**:
  * `SensorQuotaReconciliationManager.determineRecommendedUnpairDeviceId` **MUST** exclude `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` (and any sole paired location sensor) from unpair recommendations so the swap dialog never prompts the athlete to unpair their primary location provider.
* **Grounded, Modest Copy for Milestones & App Development Support (`ATT-5658`, `ATT-5659`)**:
  * Milestone dialogs (`FirstJubileeCelebrationDialog`) and Supporter screens (`SupporterScreen`) must use modest, grounded wording focused simply on supporting the ongoing development of the app — avoiding marketing slogans (*"15+ years of development"*, *"indie developer"*, *"privacy-first / no ads"*), treating 100 workouts as a nice milestone with more to come, and ordering feedback prompts logically (*Email Feedback $\rightarrow$ Play Store Review $\rightarrow$ Financial Support at the bottom*, visible to both Free and Pioneer users).

### 5.19 Read-Only Picker `OutlinedTextField` Tap Handling & Two-Step Dialog Transition State Independence (`Rule 77` — `ATT-5674` $\rightarrow$ `ATT-5818`, `ATT-5666` $\rightarrow$ `ATT-5824` / `ATT-5762`)
* **Never Rely on Outer `Modifier.clickable` on an `enabled = true` `OutlinedTextField(readOnly = true)` (`ATT-5674` $\rightarrow$ `ATT-5818`)**:
  * In Jetpack Compose Material 3, an `OutlinedTextField` with `enabled = true` (even when `readOnly = true`) attaches an internal pointer-input focus handler inside `BasicTextField` that **consumes tap events** before they bubble up to an outer `Modifier.clickable { ... }` — causing taps on the text field body to be swallowed while only a `trailingIcon` `IconButton` responds.
  * Whenever a read-only `OutlinedTextField` acts as a selector/picker trigger, either wrap it in an `ExposedDropdownMenuBox` (`Modifier.menuAnchor()`) or pass a `MutableInteractionSource` to `OutlinedTextField(interactionSource = interactionSource, ...)` and observe `PressInteraction.Release` in a `LaunchedEffect(interactionSource)` to open the selection dialog.
* **Two-Step Dialog Transition State Independence (`SelectionSheet` $\rightarrow$ `ConfirmationDialog`, `ATT-5666` $\rightarrow$ `ATT-5824`)**:
  * Reusable selection bottom sheets (such as `WorkoutClusterSelectionDialog`) invoke `onSelect(item)` followed immediately by `onDismiss()` when a candidate row is tapped.
  * Therefore, when chaining a second dialog (e.g. a confirmation `AlertDialog`) after candidate selection, the caller **MUST NOT** gate the confirmation dialog on a state variable that `onDismiss()` resets to `null` (such as `if (clusterToMerge != null && mergeTargetCluster != null)` with `onDismiss = { clusterToMerge = null }`). Instead, capture the confirmation payload into an independent state variable inside `onSelect` (`pendingMergePair = source to target`) so `onDismiss` cleanly closes the picker sheet while the confirmation `AlertDialog` opens on the first tap.

### 5.20 Two-Sided `Row` Weighting, Locale/Unit Fidelity & Authentic Anti-Prototype / Anti-Clone UI (`Rule 82` — `ATT-7022` $\rightarrow$ `ATT-7321`)
* **Mandatory `Modifier.weight(1f)` in Two-Sided Header/Action `Row`s**:
  * Every `Row(horizontalArrangement = Arrangement.SpaceBetween)` placing a title/subtitle opposite a pill badge, counter, or action `Button` **MUST** apply `Modifier.weight(1f)` (plus explicit horizontal spacing) to the flexible text column (`ATT-7321`). Omitting `weight(1f)` lets long localized titles (`de`) consume nearly 100% of the row width and crushes the trailing badge/button into 1–5 character mid-word wraps (`"Pass / teile / n"`, `"HF × / Trittfrequen / z"`, `"Verborge / ne / Schätze"`).
  * Never use `FlowRow(maxItemsInEachRow = 4)` with `Modifier.weight(1f)` for multi-word localized badge cards (which overflows into uneven `3 + 3 + 2` rows with mid-word breaks like `"Morgenpatrouill / e"`). Use deterministic 2-column rows (`Modifier.height(IntrinsicSize.Min)`) so sibling cards share identical width and height.
* **Authentic `aTrainingTracker` Identity (No Raw HTML Prototypes or Strava Clones)**:
  * Never port LLM-generated HTML/Tailwind prototypes verbatim into Compose with OS emoji confetti (`🌲`, `📡`, `🔁`, `🔒`), internal technical jargon (*"SQLite"*), AI marketing prose, Strava gamification terminology (*"Local Legend" / "Lokale Legende"*, *"Wrapped"*), hardcoded `String.format(Locale.US, ...)` numbers (which mix `.` and `,` in German UI), or fabricated divisor heuristics (`ritualCount / 20`). Always use Material 3 surfaces, clean vector icons, `Locale.getDefault()` + user unit preferences, and genuine sensor/database telemetry.

---

## 6. Map Viewport & Camera Guidelines

### 6.1 Unified Viewport Anchor Authority for Map Tracking
* **Single Spatial Authority**:
  * Map camera tracking, Follow-Me lookahead offsets, and viewport content paddings MUST share a single spatial authority to prevent conflicting animation loops and viewport jitter.
  * Avoid competing camera updates where Google Maps `contentPadding` recalculation in `BoxWithConstraints` fights with `cameraPositionState.animate(...)`. Keep map paddings symmetric or fixed, and implement forward camera lookahead analytically via target coordinate projection (ATT-3054 / ATT-3102).
