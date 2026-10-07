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
* **Route & Navigation Domain Tints**: Route geometry and trajectories on the map use green tones (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`). Consequently, interactive UI elements, cards, and buttons specifically associated with route selection or navigation (e.g. the Route Selection button on the Control Tracking screen in ATT-2458) should incorporate a subtle green touch (e.g. subtle green border accent, tinted icon, or gentle container tint) to establish an intuitive semantic connection with the map's green route visualization, while keeping the effect minimal and harmonious with the theme.
* Never hardcode `Color.White` / `Color.Black` for text or backgrounds in normal app UI (cockpit tiles follow `CockpitThemeMode`).

### 5.5 Typography & Icons
* Text styles from `MaterialTheme.typography`; emphasis via `FontWeight.Bold` / `SemiBold` (no custom font families).
* Icons from Material Icons (`Icons.Default.*`, `Icons.AutoMirrored.*` for directional icons). Map POIs/markers should use a consistent standard icon set (see ATT-2461) rather than ad-hoc drawn shapes.

### 5.6 Placement & Entry Points
* New features are entered from the screen where the athlete expects them (e.g. route selection on the Control Tracking screen, ATT-2458) using the same button/chip style as neighbouring actions — not via new floating or branded elements.

### 5.7 In-Ride Navigation Cues & HUD Overlays
* **Top-Level Spatial Overlay**: In-ride navigation hints, turn-by-turn cues (e.g. ATT-1450), and fork-in-the-road decision prompts (e.g. ATT-1955) must float directly on top of the active tracking screen elements (cockpit tiles, map view) rather than displacing or squeezing the cockpit tile layout.
* **Semi-Transparency for Glanceability**: Overlays must use a semi-transparent surface background (e.g. `surface` / `surfaceContainer` at ~80–85% opacity, `TTAlpha`) paired with a subtle green border accent (`TTColor.RouteActiveNavigation`), so that athletes can immediately register the direction cue while still discerning underlying telemetry values and metrics behind the banner.
* **Transient Auto-Dismiss (3–5 Seconds)**: Navigation cues are time-sensitive and ephemeral. Upon being triggered (or once a maneuver instruction is issued), the cue must automatically vanish after a brief, configurable interval (3–5 seconds) via a smooth animation (fade or slide), promptly returning the screen to full telemetry focus without requiring manual dismissal while riding.
* **Expert Settings Customization**: Both overlay transparency (e.g. 50%–100%, default 80%) and auto-dismiss duration (e.g. 2–10 seconds or persistent until passed, default 4 seconds) must be exposed as configurable parameters in the Expert Settings (*Experteneinstellungen*), allowing athletes to tailor cue presentation to their ambient lighting conditions, cockpit layout, and riding speed.

