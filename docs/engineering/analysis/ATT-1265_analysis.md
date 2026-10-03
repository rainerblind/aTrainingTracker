# Analysis - ATT-1265: [Feature] [Cockpit] Dynamic zone-based color accenting for Heart Rate and Power metrics

**Parent Ticket**: [ATT-1265](https://rainerblind.atlassian.net/browse/ATT-1265)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1435](https://rainerblind.atlassian.net/browse/ATT-1435) (`[Analysis]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-172`  
**Test ID**: `TST-UI-124`  

---

## 1. Executive Summary & Problem Formulation

In the current workout tracking cockpit, athletic intensity zones (Heart Rate and Cycling Power) are visually represented in `SensorFieldView.kt` via a single, hardcoded combination:
1. A tinted card container background (`fieldState.zoneColor.copy(alpha = 0.12f)`).
2. A fixed 6dp vertical accent bar on the left edge of the tile (`Spacer(modifier = Modifier.width(6.dp).fillMaxHeight().background(fieldState.zoneColor))`).
3. Numeric metric values (`fieldState.value`) are rendered in the default foreground color (`MaterialTheme.colorScheme.onSurface`).

While effective in light mode, this rigid presentation creates several limitations for endurance athletes, particularly in the context of AMOLED optimization and high-speed outdoor training:
- **AMOLED Inefficiency**: The 12% alpha tinted background illuminates OLED pixels across the entire sensor tile card, preventing the card from utilizing true pitch black (`#000000`). For athletes prioritizing battery longevity and reduced night glare, an illuminated background defeats the purpose of AMOLED Dark Mode.
- **Metric-Specific Needs (HR vs. Power)**: Cycling power fluctuates rapidly with terrain and pedal cadence, whereas heart rate responds more sluggishly. Athletes frequently desire different visual cues for power (e.g. a prominent edge bar) than for heart rate (e.g. subtle text coloring or background tinting).
- **Layout & Ergonomics**: In multi-column cockpit grids, an edge bar on the right side of a right-hand column tile or on both edges can be more visually intuitive and balanced than a left-only bar.
- **Lack of User Agency**: Athletes cannot currently customize how their training zones are rendered or disable background tinting in favor of minimalist typography coloring.

To resolve these issues, **ATT-1265** introduces granular, user-configurable zone visualization options per sport/metric profile (`HR_RUN`, `HR_BIKE`, and `PWR_BIKE`) within `ZonesSettingsActivity.kt`, offering four independent multi-select presentation cues:
1. **Light background** (`showBackground`: Boolean)
2. **Left bar** (`showLeftBar`: Boolean)
3. **Right bar** (`showRightBar`: Boolean)
4. **Text color** (`showTextColor`: Boolean)

In addition, an interactive live preview tile is placed directly beneath the zone thresholds in `ZonesSettingsActivity.kt`, respecting the active system or user dark-mode selection.

---

## 2. Requirement & Baseline Traceability

| Requirement ID | Standard / Artifact | Alignment Description |
|---|---|---|
| **`REQ-UI-172`** | `docs/requirements.md` | Net-new requirement governing configurable zone visualization options (background, left bar, right bar, text color) per `ZoneType` and interactive preview. |
| **`TST-UI-124`** | `docs/tests.md` | Verification test catalog for persistence, UI toggles, live preview theme adherence, and cockpit tile rendering. |
| **`REQ-UI-101`** | `docs/requirements.md` | Neutral surface and background color compliance. |
| **`REQ-UI-168`** | `docs/requirements.md` | Cockpit independent theme selector (`SYSTEM` vs `ALWAYS_DARK`). |
| **`REQ-UI-169`** | `docs/requirements.md` | AMOLED pure black (`#000000`) cockpit background. |
| **`REQ-UI-170`** | `docs/requirements.md` | Comprehensive cockpit dark theme across system bars and root containers. |
| **`REQ-UI-171`** | `docs/requirements.md` | High-contrast semibold typography and subtle tile dividers (`#262626`). |

---

## 3. Detailed Architectural & Technical Analysis

### 3.1. Zone Color Palette Invariant
The established `aTrainingTracker` zone color palette is strictly preserved across all 5 athletic training zones:
- **Zone 1 (Recovery)**: `#7FFF00` (Chartreuse, `R.color.zone_1`, `TTColor.Zone1`)
- **Zone 2 (Aerobic)**: `#008000` (Green, `R.color.zone_2`, `TTColor.Zone2`)
- **Zone 3 (Tempo)**: `#FFA500` (Orange, `R.color.zone_3`, `TTColor.Zone3`)
- **Zone 4 (Threshold)**: `#FF0000` (Red, `R.color.zone_4`, `TTColor.Zone4`)
- **Zone 5 (Anaerobic)**: `#9400D3` (Dark Violet, `R.color.zone_5`, `TTColor.Zone5`)

No color substitutions or palette shifts will occur. When "Text color" is enabled, `fieldState.value` directly adopts the active zone's hex color.

### 3.2. Data Model: `ZoneDisplayOptions`
A lightweight, immutable data class models the four display toggles:
```kotlin
data class ZoneDisplayOptions(
    val showBackground: Boolean = true,
    val showLeftBar: Boolean = true,
    val showRightBar: Boolean = false,
    val showTextColor: Boolean = false
)
```

#### Backward Compatibility Default Values:
- `showBackground = true`
- `showLeftBar = true`
- `showRightBar = false`
- `showTextColor = false`

These defaults guarantee 100% visual parity with existing installations until explicitly customized by the athlete.

### 3.3. Persistence Architecture (`SettingsDataStore.kt`)
The configuration is stored per `ZoneType` (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) using Jetpack DataStore Preferences:
- Keys per profile:
  - `hr_run_zone_show_background`, `hr_run_zone_show_left_bar`, `hr_run_zone_show_right_bar`, `hr_run_zone_show_text_color`
  - `hr_bike_zone_show_background`, `hr_bike_zone_show_left_bar`, `hr_bike_zone_show_right_bar`, `hr_bike_zone_show_text_color`
  - `pwr_bike_zone_show_background`, `pwr_bike_zone_show_left_bar`, `pwr_bike_zone_show_right_bar`, `pwr_bike_zone_show_text_color`
- DataStore API functions:
  - `fun getZoneDisplayOptionsFlow(zoneType: ZoneType): Flow<ZoneDisplayOptions>`
  - `suspend fun saveZoneDisplayOptions(zoneType: ZoneType, options: ZoneDisplayOptions)`
- Synchronous Java/Kotlin helper (`SettingsDataStoreJavaHelper.kt`):
  - `fun getZoneDisplayOptions(context: Context, zoneType: ZoneType): ZoneDisplayOptions`

### 3.4. Settings UI Integration (`ZonesSettingsActivity.kt`)
`ZonesSettingsActivity.kt` presents a 3-tab `HorizontalPager` corresponding to:
1. `tab_hr_run` (Run HR)
2. `tab_hr_bike` (Bike HR)
3. `tab_pwr_bike` (Bike Power)

Inside `SettingsScreenContent` for each tab, below the five `ZoneRow` threshold cards, a new section **"Zonendarstellung" / "Zone Visualization"** is introduced:
1. **Multi-Select Toggle Controls**: Four interactive Material 3 switches/checkboxes:
   - Light background (`@string/zone_display_background`)
   - Left bar (`@string/zone_display_left_bar`)
   - Right bar (`@string/zone_display_right_bar`)
   - Text color (`@string/zone_display_text_color`)
2. **Interactive Live Preview Tile**:
   - Renders a live sample `SensorFieldView` using the profile's representative metric (e.g. Heart Rate `148 bpm` for HR, Power `245 W` for PWR).
   - Embedded interactive zone selector (interactive chips for Zones 1 to 5) allowing the athlete to test all 5 zone colors in real time against their selected toggles.
   - **Theme Adherence**: The preview tile is rendered within `ZonesSettingsActivity`'s ambient `ATrainingTrackerTheme`, ensuring it seamlessly and dynamically reflects the active system/app dark-mode or light-mode setting without hardcoded container overrides.

### 3.5. Cockpit Telemetry Tile Rendering (`SensorFieldState.kt` & `SensorFieldView.kt`)
To preserve high performance and prevent coroutine or DataStore latency during 1Hz sensor ticks, `SensorFieldState` is extended with `val zoneDisplayOptions: ZoneDisplayOptions = ZoneDisplayOptions()`.

In `SensorFieldView.kt`:
1. **Container Background**:
   ```kotlin
   containerColor = if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showBackground) {
       fieldState.zoneColor.copy(alpha = 0.12f)
   } else {
       MaterialTheme.colorScheme.surface
   }
   ```
2. **Left Accent Bar**:
   ```kotlin
   if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showLeftBar) {
       Spacer(
           modifier = Modifier
               .width(6.dp)
               .fillMaxHeight()
               .background(fieldState.zoneColor)
       )
   }
   ```
3. **Right Accent Bar**:
   ```kotlin
   if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showRightBar) {
       Spacer(
           modifier = Modifier
               .width(6.dp)
               .fillMaxHeight()
               .background(fieldState.zoneColor)
       )
   }
   ```
4. **Primary Numeric Value Text Color**:
   ```kotlin
   val valueColor = if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showTextColor) {
       fieldState.zoneColor
   } else {
       MaterialTheme.colorScheme.onSurface
   }
   ```

### 3.6. View Model Integration (`TrackingViewModel.kt`)
In `TrackingViewModel.kt`:
- Maps `SensorType.HR` (under `RUN` or `BIKE`) and `SensorType.POWER` (under `BIKE`) to their respective `ZoneType`.
- Observes or loads `ZoneDisplayOptions` for the active session's sport type.
- Associates `zoneDisplayOptions` with `SensorFieldState` upon sensor data updates.

---

## 4. Preserved Invariants & Guardrails

1. **Established Zone Colors**: Zone colors (Z1: `#7FFF00`, Z2: `#008000`, Z3: `#FFA500`, Z4: `#FF0000`, Z5: `#9400D3`) are immutable and strictly preserved.
2. **Backward Compatibility**: Default settings (`showBackground = true`, `showLeftBar = true`, `showRightBar = false`, `showTextColor = false`) produce zero visual alteration for existing users until customized.
3. **Empty Selection Guard**: If all four toggles are turned off, the sensor field gracefully displays standard unaccented telemetry (neutral surface container, standard white/dark text, no bars) without throwing errors or clipping layouts.
4. **Performance & Memory Footprint**: No disk I/O occurs on sensor tick updates; `ZoneDisplayOptions` is stored as an in-memory property on `SensorFieldState` and updated reactively.
5. **Localization Parity**: All newly introduced setting labels and preview descriptions will be fully localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. **Non-Zone Sensor Integrity**: Sensor fields without zone bindings (Speed, Cadence, Elevation, Distance, Clock, etc.) remain completely unaffected (`zoneColor == Color.Transparent`).

---

## 5. Affected Components

- `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStore.kt`: Add preferences keys and accessors for `ZoneDisplayOptions`.
- `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt`: Add synchronous accessor.
- `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`: Add "Zonendarstellung" section, 4 multi-select toggles, and live theme-aware preview tile.
- `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt`: Add `zoneDisplayOptions: ZoneDisplayOptions`.
- `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt`: Support dynamic background, left bar, right bar, and text color.
- `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`: Populate `zoneDisplayOptions` on relevant zone fields.
- `app/src/main/res/values*/strings.xml`: Define localized string resources for all 9 locales.
- `docs/requirements.md`: Add `REQ-UI-172`.
- `docs/tests.md`: Add `TST-UI-124`.
