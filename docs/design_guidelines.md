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

### 1.3 Large Option Sets ($> 4$ Options)
* **Standard Control**: Exposed Dropdown Menu (`ExposedDropdownMenuBox`) or Modal Selection Dialog.
* **When to Use**:
  * Options list exceeds horizontal screen space.
  * *Examples*: Sensor device selection, audio cue sound profile selection, map tile source.

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

### 2.4 Continuous Linear Projection for Sparse Geometry Progress Tracking
* **Linear Projection Invariant**:
  * Progress tracking along geometric paths (climbs, segments, routes) MUST NOT rely exclusively on nearest-neighbor discrete vertex quantization (`path[closestIdx]`).
  * On straight road sections or imported GPX tracks where points may be 50–200 m apart, nearest-neighbor snapping causes telemetry metrics (remaining distance, elevation gain, current gradient) and visual rider pins to freeze and jump discontinuously.
  * Progress tracking algorithms MUST project the athlete's current coordinate vector onto the nearest line segment $[P_i, P_{i+1}]$ to determine the fractional progress $s \in [0.0, 1.0]$:
    $$\vec{v} = P_{i+1} - P_i$$
    $$s = \frac{(L - P_i) \cdot \vec{v}}{\|\vec{v}\|^2} \in [0.0, 1.0]$$
    $$\text{dist} = P_i.\text{dist} + s \times (P_{i+1}.\text{dist} - P_i.\text{dist})$$
    $$\text{alt} = P_i.\text{alt} + s \times (P_{i+1}.\text{alt} - P_i.\text{alt})$$
  * Telemetry and visual marker positions along polyline profiles must be interpolated continuously using this projected distance and altitude (ATT-3103 / ATT-3174).

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

---

## 4. Spacing, Typography & Localization Parity

* **Design Tokens**:
  * Always use the existing theme tokens rather than hardcoded raw colors: `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `TTColor` (domain colors: zones, routes, achievements, branding), `TTAlpha` (opacity levels) and `LayoutConstants` (header heights). There is currently no `TTDimens` object — use the spacing scale in Section 5.2.
* **Localization Invariant**:
  * Any user-visible string (titles, subtitles, button labels, descriptions) must be localized across all 9 supported application locales (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

---

## 5. Visual Consistency Baseline ("Look Like the Rest of the App")

> Origin: Retro Sprint 2026-40.16 — ATT-1835 (Quick Route Selector), ATT-1953 (Take Me Home HUD) and ATT-2058 (tile variants) were functionally correct but did not look/feel like the rest of the app (follow-ups ATT-2456 … ATT-2462). The values below are the de-facto standard measured across the existing codebase. Binding for every ticket that adds or changes UI (Governance Rule 23).

### 5.1 Reuse Before Create
* Before designing a new screen, card, sheet, HUD element or button, find the **closest existing equivalent** in the app and copy its structure, shapes, spacing and colors.
* Prefer shared components from `ui/components/` (e.g. `MetricItem`, `MetricBadge`, `DropdownSelector`, `EmptyStatePlaceholder`, `DeleteConfirmationDialog`, `BottomSheetUtils`, `MappableListItem`) and `ui/common/`.
* Tabbed screens follow the existing tabbed layout pattern (`RouteTabbedScreen`, `EquipmentTabsScreen`): collapsing header with `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` title row + standard Material 3 tab row. Do not invent alternative tab/segment visuals for top-level navigation.
* New one-off styles (custom shapes, colors, fonts, icon sets, branding) are only allowed with an explicit justification in the Stage 3 plan (Section "UI Consistency").

### 5.2 Spacing Scale
* Use only: `4.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp` (exceptions require justification).
* Screen / card horizontal content padding: `16.dp` (dominant), compact cards `12.dp`.
* Vertical spacing between list rows / small elements: `4.dp`–`8.dp`.

### 5.3 Shapes
* Cards, sheets, panels, HUD overlays: `RoundedCornerShape(12.dp)` (dominant) or `MaterialTheme.shapes.medium`.
* Small elements (badges, inner tiles, chips): `8.dp`; tiny markers / bars: `4.dp`.
* Dialogs: Material 3 defaults (`MaterialTheme.shapes.extraLarge`).

### 5.4 Color
* Surfaces, text and accents come from `MaterialTheme.colorScheme` so that Light, Dark and **AMOLED** themes all render correctly. Verify new UI in all three.
* Domain semantics come from `TTColor` (zones, routes, start/end points, branding). Add a new `TTColor` entry instead of an inline `Color(0x…)`.
* **Route & Navigation Domain Tints**: Route geometry and trajectories on the map use Royal Blue tones (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`). Consequently, interactive UI elements, cards, and buttons specifically associated with route selection or navigation (e.g. the Route Selection button on the Control Tracking screen in ATT-2458) should incorporate a subtle Royal Blue touch (e.g. subtle Royal Blue border accent, tinted icon, or gentle container tint) to establish an intuitive semantic connection with the map's Royal Blue route visualization, while keeping the effect minimal and harmonious with the theme.
* Never hardcode `Color.White` / `Color.Black` for text or backgrounds in normal app UI (cockpit tiles follow `CockpitThemeMode`).

### 5.5 Typography & Icons
* Text styles from `MaterialTheme.typography`; emphasis via `FontWeight.Bold` / `SemiBold` (no custom font families).
* Icons from Material Icons (`Icons.Default.*`, `Icons.AutoMirrored.*` for directional icons). Map POIs/markers should use a consistent standard icon set (see ATT-2461) rather than ad-hoc drawn shapes.
* **Semantic Category Icon Tinting (Rule 23)**: When rendering paired or symmetric category items across lists, tables, or tabbed views (e.g. equipment categories like bikes and shoes in `EquipmentSensorMatrixScreen`):
  * **Unified Active Role**: All active entities across categories MUST use the same unified brand color token (`MaterialTheme.colorScheme.primary`). Do NOT assign disparate color roles (e.g. `primary` to bikes and `secondary` to shoes) to differentiate categories; visual distinction is provided by the vector drawables themselves (`ic_equipment_bike` vs `ic_equipment_shoe`) and section/tab titles.
  * **Status Tinting**: Use `MaterialTheme.colorScheme.outline` for retired/inactive entities, and `MaterialTheme.colorScheme.error` for failed/unhealthy states.

### 5.6 Placement & Entry Points
* New features are entered from the screen where the athlete expects them (e.g. route selection on the Control Tracking screen, ATT-2458) using the same button/chip style as neighbouring actions — not via new floating or branded elements.

### 5.7 In-Ride Navigation Cues & HUD Overlays
* **Top-Level Spatial Overlay**: In-ride navigation hints, turn-by-turn cues (e.g. ATT-1450), and fork-in-the-road decision prompts (e.g. ATT-1955) must float directly on top of the active tracking screen elements (cockpit tiles, map view) rather than displacing or squeezing the cockpit tile layout.
* **Semi-Transparency for Glanceability**: Overlays must use a semi-transparent surface background (e.g. `surface` / `surfaceContainer` at ~80–85% opacity, `TTAlpha`) paired with a subtle Royal Blue border accent (`TTColor.RouteActiveNavigation`), so that athletes can immediately register the direction cue while still discerning underlying telemetry values and metrics behind the banner.
* **Transient Auto-Dismiss (3–5 Seconds)**: Navigation cues are time-sensitive and ephemeral. Upon being triggered (or once a maneuver instruction is issued), the cue must automatically vanish after a brief, configurable interval (3–5 seconds) via a smooth animation (fade or slide), promptly returning the screen to full telemetry focus without requiring manual dismissal while riding.
* **Expert Settings Customization**: Both overlay transparency (e.g. 50%–100%, default 80%) and auto-dismiss duration (e.g. 2–10 seconds or persistent until passed, default 4 seconds) must be exposed as configurable parameters in the Expert Settings (*Experteneinstellungen*), allowing athletes to tailor cue presentation to their ambient lighting conditions, cockpit layout, and riding speed.

### 5.8 Live Tracking Bottom Sheets (Segments & Climbs)
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


---

## 6. Map Viewport & Camera Guidelines

### 6.1 Unified Viewport Anchor Authority for Map Tracking
* **Single Spatial Authority**:
  * Map camera tracking, Follow-Me lookahead offsets, and viewport content paddings MUST share a single spatial authority to prevent conflicting animation loops and viewport jitter.
  * Avoid competing camera updates where Google Maps `contentPadding` recalculation in `BoxWithConstraints` fights with `cameraPositionState.animate(...)`. Keep map paddings symmetric or fixed, and implement forward camera lookahead analytically via target coordinate projection (ATT-3054 / ATT-3102).



