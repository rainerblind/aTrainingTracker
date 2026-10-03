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
  * Always use standardized tokens from `TTDimens`, `TTColor`, and `TTAlpha` rather than hardcoded magic numbers or raw colors.
* **Localization Invariant**:
  * Any user-visible string (titles, subtitles, button labels, descriptions) must be localized across all 9 supported application locales (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
