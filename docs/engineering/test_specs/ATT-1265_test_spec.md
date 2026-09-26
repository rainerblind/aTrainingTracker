# Test Specification - ATT-1265: [Cockpit] Dynamic zone-based color accenting for Heart Rate and Power metrics

**Parent Ticket**: [ATT-1265](https://rainerblind.atlassian.net/browse/ATT-1265)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1436](https://rainerblind.atlassian.net/browse/ATT-1436) (`[Test-Spec]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-172`  
**Test ID**: `TST-UI-124`  

---

## 1. Traceability & Scope Alignment

| Item | Reference |
|---|---|
| **Parent Feature Ticket** | [ATT-1265](https://rainerblind.atlassian.net/browse/ATT-1265) |
| **Parent Epic** | [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*) |
| **Requirement Specification** | `REQ-UI-172` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) |
| **Test Catalog** | `TST-UI-124` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) |
| **Analysis Deliverable** | [docs/engineering/analysis/ATT-1265_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-1265_analysis.md) |
| **Target Components** | `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `ZonesSettingsActivity.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt` |

---

## 2. Harmonized Requirement Specification (`REQ-UI-172`)

### REQ-UI-172: Configurable Zone Visualization Options and Theme-Aware Live Preview
The system SHALL provide configurable zone visualization options per athletic zone profile (`HR_RUN`, `HR_BIKE`, and `PWR_BIKE`) within `ZonesSettingsActivity` and reflect the chosen visual cues dynamically across workout cockpit sensor tiles (ATT-1265):

1. **Per-Profile Visualization Options (`ZoneDisplayOptions`)**:
   - The system SHALL define four independent, multi-selectable visual presentation cues for each `ZoneType`:
     - *Light background* (`showBackground`: Boolean): Renders the card container with tinted zone color at 12% opacity (`zoneColor.copy(alpha = 0.12f)`). When disabled, the card container remains neutral `MaterialTheme.colorScheme.surface` (pure `#000000` in AMOLED dark mode).
     - *Left bar* (`showLeftBar`: Boolean): Renders a 6dp vertical indicator strip on the left edge of the tile colored with the active `zoneColor`.
     - *Right bar* (`showRightBar`: Boolean): Renders a 6dp vertical indicator strip on the right edge of the tile colored with the active `zoneColor`.
     - *Text color* (`showTextColor`: Boolean): Renders the primary numeric metric reading (`fieldState.value`) using the active `zoneColor`. When disabled, the reading renders in standard `MaterialTheme.colorScheme.onSurface`.
   - The default configuration for all profiles SHALL be: `showBackground = true`, `showLeftBar = true`, `showRightBar = false`, `showTextColor = false`, ensuring 100% backward compatibility.
2. **Persistent Preferences Storage (`SettingsDataStore.kt`)**:
   - The system SHALL store the four visual toggles independently for each `ZoneType` in DataStore preferences:
     - `HR_RUN`: `hr_run_zone_show_background`, `hr_run_zone_show_left_bar`, `hr_run_zone_show_right_bar`, `hr_run_zone_show_text_color`.
     - `HR_BIKE`: `hr_bike_zone_show_background`, `hr_bike_zone_show_left_bar`, `hr_bike_zone_show_right_bar`, `hr_bike_zone_show_text_color`.
     - `PWR_BIKE`: `pwr_bike_zone_show_background`, `pwr_bike_zone_show_left_bar`, `pwr_bike_zone_show_right_bar`, `pwr_bike_zone_show_text_color`.
   - Modifying a toggle SHALL immediately persist the updated setting to DataStore on an IO dispatcher.
3. **Settings UI Integration & Theme-Aware Live Preview (`ZonesSettingsActivity.kt`)**:
   - In `ZonesSettingsActivity.kt`, each profile tab (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) SHALL display a dedicated section titled "Zonendarstellung" / "Zone Visualization" positioned below the five zone threshold cards.
   - The section SHALL present four interactive toggle controls (switches or checkboxes) corresponding to the four display options.
   - Directly below the toggle controls, the section SHALL render an interactive live preview tile (`SensorFieldView`):
     - The preview tile SHALL strictly inherit `MaterialTheme.colorScheme` from the active application/system theme (respecting dark mode or light mode).
     - The preview SHALL feature an interactive 5-zone selector (chips for Zones 1 to 5) allowing the athlete to test all 5 zone colors in real time against their active toggle selections.
4. **Cockpit Telemetry Rendering (`SensorFieldView.kt`)**:
   - In `SensorFieldView.kt`, when `fieldState.zoneColor != Color.Transparent`:
     - Container background SHALL evaluate to `fieldState.zoneColor.copy(alpha = 0.12f)` if `zoneDisplayOptions.showBackground == true`, or `MaterialTheme.colorScheme.surface` if `false`.
     - Left indicator strip SHALL render if `zoneDisplayOptions.showLeftBar == true`.
     - Right indicator strip SHALL render if `zoneDisplayOptions.showRightBar == true`.
     - Primary metric value text color SHALL evaluate to `fieldState.zoneColor` if `zoneDisplayOptions.showTextColor == true`, or `MaterialTheme.colorScheme.onSurface` if `false`.
5. **Preserved Invariants & Guardrails**:
   - The established zone color palette (`R.color.zone_1` to `zone_5`: `#7FFF00`, `#008000`, `#FFA500`, `#FF0000`, `#9400D3`) MUST NOT be altered.
   - If all four toggles are disabled, the tile SHALL render standard unaccented telemetry (neutral container, standard onSurface text, no edge bars) without errors or clipping.
   - Non-zone sensor fields (Speed, Cadence, Elevation, Distance, Clock, etc.) SHALL remain completely unaffected (`zoneColor == Color.Transparent`).
   - Zero disk I/O SHALL occur during 1Hz sensor tick updates; `ZoneDisplayOptions` SHALL be resolved in-memory on `SensorFieldState`.
   - All newly introduced string resources SHALL maintain 100% localization parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
- *Given* an athlete in `ZonesSettingsActivity` on the Run HR tab,
- *When* viewing the "Zonendarstellung" section,
- *Then* four toggle controls (Light background, Left bar, Right bar, Text color) and a live preview tile SHALL be rendered below the zone threshold cards.
- *When* the athlete toggles "Right bar" ON and "Light background" OFF,
- *Then* the live preview tile SHALL immediately update to show a right vertical strip on neutral surface with no background tint, and the preferences SHALL be persisted to DataStore.
- *Given* the host device is in AMOLED Dark Mode,
- *When* viewing the live preview tile in `ZonesSettingsActivity`,
- *Then* the preview tile card background SHALL render in pure black (`#000000`) when background tint is toggled OFF.
- *Given* an active workout with Run HR zone configured with "Text color" ON and "Light background" OFF,
- *When* the heart rate enters Zone 4 (Threshold),
- *Then* the heart rate metric value SHALL render in red (`#FF0000`) on a neutral dark surface with no background tint.
- *Given* all four zone visualization toggles are switched OFF,
- *When* viewing live tracking or the preview tile,
- *Then* the tile SHALL render standard neutral telemetry without accent bars or tint.

**Invariants**: Zone color hex palette (`REQ-UI-101`), AMOLED cockpit theme tokens (`REQ-UI-168`..`171`), zone threshold calculation formulas, and non-zone sensor field rendering MUST NOT be altered.

---

## 3. Detailed Test Specification (`TST-UI-124`)

### TST-UI-124: Configurable Zone Visualization & Theme-Aware Preview Verification

1. **Zone Display Data Model Unit Tests (`ZoneDisplayOptionsTest.kt`)**:
   - *Test 1.1*: Verify default constructor sets `showBackground = true`, `showLeftBar = true`, `showRightBar = false`, `showTextColor = false` (backward compatibility).
   - *Test 1.2*: Verify copy mutations produce expected combinations (e.g. text color only, edge bars only, all active, all inactive).
   - *Test 1.3*: Verify equality and hash code parity for identical options instances.

2. **DataStore Preferences Persistence Unit Tests (`SettingsDataStoreZoneDisplayTest.kt`)**:
   - *Test 2.1*: Verify reading uninitialized DataStore preferences returns default `ZoneDisplayOptions` for `HR_RUN`, `HR_BIKE`, and `PWR_BIKE`.
   - *Test 2.2*: Verify saving custom options for `HR_RUN` persists correctly and updates `getZoneDisplayOptionsFlow(ZoneType.HR_RUN)` without contaminating `HR_BIKE` or `PWR_BIKE`.
   - *Test 2.3*: Verify independent persistence across all 3 zone types with distinct toggle configurations.
   - *Test 2.4*: Verify `SettingsDataStoreJavaHelper.getZoneDisplayOptions` returns the persisted values synchronously with safe fallback and non-blocking caching off main thread.

3. **Cockpit Sensor Tile Rendering Logic Unit Tests (`SensorFieldZoneRenderingTest.kt`)**:
   - *Test 3.1*: Verify container color resolution:
     - `showBackground = true`, `zoneColor = TTColor.Zone1` -> returns `TTColor.Zone1.copy(alpha = 0.12f)`.
     - `showBackground = false`, `zoneColor = TTColor.Zone1` -> returns `surfaceColor`.
     - `zoneColor = Color.Transparent` -> returns `surfaceColor` regardless of `showBackground`.
   - *Test 3.2*: Verify left bar visibility:
     - `showLeftBar = true`, `zoneColor != Transparent` -> `true`.
     - `showLeftBar = false`, `zoneColor != Transparent` -> `false`.
     - `zoneColor == Transparent` -> `false` regardless of `showLeftBar`.
   - *Test 3.3*: Verify right bar visibility:
     - `showRightBar = true`, `zoneColor != Transparent` -> `true`.
     - `showRightBar = false`, `zoneColor != Transparent` -> `false`.
     - `zoneColor == Transparent` -> `false` regardless of `showRightBar`.
   - *Test 3.4*: Verify text color resolution:
     - `showTextColor = true`, `zoneColor = TTColor.Zone4` -> returns `TTColor.Zone4`.
     - `showTextColor = false`, `zoneColor = TTColor.Zone4` -> returns `onSurfaceColor`.
     - `zoneColor == Transparent` -> returns `onSurfaceColor` regardless of `showTextColor`.
   - *Test 3.5*: Verify empty selection guard: when all 4 toggles are false, container is surface, both bars hidden, text is onSurface.
   - *Test 3.6*: Verify theme-aware preview container color resolution:
     - When `darkTheme = true` (AMOLED/Dark), with `showBackground = false`, container color strictly evaluates to dark surface (`Color(0xFF000000)` / `Color(0xFF1B1B1F)`).
     - When `darkTheme = false` (Light), with `showBackground = false`, container color strictly evaluates to light surface (`Color(0xFFFFFFFF)` / `Color(0xFFFEF7FF)`).


4. **Localization Parity Audit (`ZoneDisplayLocalizationTest.kt`)**:
   - Verify all newly added string keys (`zone_display_title`, `zone_display_background`, `zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`, `zone_display_preview_title`) exist across all 9 application locales:
     - `values/strings.xml` (EN)
     - `values-de/strings.xml` (DE)
     - `values-es/strings.xml` (ES)
     - `values-fr/strings.xml` (FR)
     - `values-it/strings.xml` (IT)
     - `values-ja/strings.xml` (JA)
     - `values-nl/strings.xml` (NL)
     - `values-pl/strings.xml` (PL)
     - `values-pt/strings.xml` (PT)

5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate.
