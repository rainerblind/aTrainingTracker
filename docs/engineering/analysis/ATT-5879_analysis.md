# Stage 1 Analysis: ATT-5879 - TrackingTabs & Cockpit: Edit-Mode Layout, Localization & FAB Overlap Polish

**Ticket**: [ATT-5879](https://atrainingtracker.atlassian.net/browse/ATT-5879)  
**Parent Epic**: [ATT-5908](https://atrainingtracker.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5879`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Edit-Mode Telemetry & Filter Wrapping**: In 2-column rows, outer and inner `+` (`ColAdder`) buttons compress tile width, causing the 8-character `Uhrzeit` string (`18:29:15`) to wrap across lines (`18:29:1` / `5`, `round4_06_tracking_tabs_editor.png`) and the filter badge (`2 sec Ø`) to wrap into a 3-line vertical stack (`2` / `sec` / `Ø`).
* **Localization & Denglisch Inconsistencies**:
  * Active duration label is lowercase **`aktiv`** (`values-de/strings.xml`), while all other fields use uppercase nouns.
  * `Runden` tab fields are untranslated (`Lap`, `Lap Nr`, `Dist. Lap` instead of `Runde`/`Rundenzeit`, `Runde Nr.`, `Dist. Runde`, `round4_12_start_runden.png`).
  * Bottom editor toggles show English **`Live Segments`** (above `Live-Anstiege`) and Denglisch **`Lap Knopf`** (should be `Runden-Button`, `round5_02_tracking_editor_bottom.png`).
  * Editor action buttons have hardcoded English content descriptions (`"Move Field"`, `"Delete Field"`), and German `R.string.Done` is untranslated (`"Done"` instead of `"Fertig"`).
* **Field Configuration Dialog Polish (`round5_01_tracking_field_dialog.png`)**: Dialog title has title casing (`Anzeige Anpassen` instead of `Anzeige anpassen`); category breadcrumb truncates (`Sensor • Geschwindigkeit, Pace & D...`); `Relative Breite` header is center-aligned above left-aligned chips; smoothing selector button lacks leading icon/trailing chevron and uses English `2 sec` with uppercase `Gleitender Durchschnitt`.
* **Bicycle Bell FAB & Elevation Profile Overlap**: On `Karte` tab (`round4_11_start_karte.png`), the orange bell FAB sits at `Alignment.BottomCenter` directly over the `ElevationProfileEmptyState` banner, obscuring its center text.
* **Missing Sport Context**: The preview/editor header displays only tab name (`Standard`) without the active sport type (e.g., `Radeln`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  1. *Value/Filter Wrapping*: `SensorFieldView.kt` renders telemetry text without `maxLines = 1` or edit-mode font scaling. Filter text lacks single-line constraints, and `units_seconds` is hardcoded to `sec` (translatable=false) rather than SI `s`.
  2. *Resource Gaps*: `values-de/strings.xml` defines `time_active_short` as `"aktiv"`, `time_lap_short` as `"Lap"`, `lap_number_short` as `"Lap Nr"`, `distance_lap_short` as `"Dist. Lap"`, `config_tracking__showLiveSegments` as `"Live Segments"`, `config_tracking__showLapButton` as `"Lap Knopf"`, `Done` as `"Done"`, and `filter_moving_average` as `"Gleitender Durchschnitt"`.
  3. *Dialog Layout*: `EditSensorFieldDialog.kt` column sets `horizontalAlignment = CenterHorizontally`; `Relative Breite` text lacks `fillMaxWidth()` / `Alignment.Start`; category breadcrumb has `maxLines = 1`; smoothing button is a plain text button without icons.
  4. *FAB Overlap*: `SensorGridScreen.kt` places `ElevationProfile` at the bottom of the column with zero bottom padding, while `TrackingTabsScreen.kt` centers `BikeBellButton` at `Alignment.BottomCenter` with `8.dp` bottom padding.
  5. *Header Sport Context*: `TrackingTabPreviewHeader.kt` and `TrackingTabConfigHeader.kt` do not accept or render the active `activityType` sport name.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected all 7 attachments (`round4_06`, `round4_10`, `round4_11`, `round4_12`, `round5_01`, `round5_02`, `round5_03`) via `read_file` confirming visual defects.
  * Verified `activityType` is already collected in `TrackingTabsScreen` (`line 346`) and can be passed directly to preview/config headers.
  * Confirmed applying `68.dp` bottom clearance to `ElevationProfile` when lap or bell buttons are active completely resolves FAB overlap without shifting landscape layouts.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Single-line enforcement (`maxLines = 1`, edit-mode font scaling) for 8-char values like `18:29:15` in edit mode.
  * Single-line filter badge (`2 s Ø`) with localized unit `s` instead of `sec`.
  * German localization updates: `Aktiv`, `Runde`, `Runde Nr.`, `Dist. Runde`, `Live-Segmente`, `Runden-Button`, `Fertig`, move/delete content descriptions across 9 languages.
  * `Anzeige anpassen` polish: sentence-case title, `maxLines = 2` breadcrumb, start-aligned `Relative Breite`, styled smoothing button with leading `Icons.Default.Tune`, trailing chevron, and `gleitender Durchschnitt`.
  * Bottom clearance for `ElevationProfile` on map/cockpit tabs to clear the bell FAB.
  * Sport context (e.g. `Radeln`) displayed in Tracking Tabs preview/config headers.
* **Out-of-Scope**: Altering audio synthesis logic (`REQ-UI-421.5`), modifying database schemas, or changing sensor calculation algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new refinement `REQ-UI-448` (*Cockpit Edit-Mode Typography, Localization Parity & FAB Layout Polish*), refining `REQ-TRK-046`, `REQ-UI-395`, `REQ-UI-421`, `REQ-UI-435`, and `REQ-UI-437`.
* **Historical Origin & Commit Trace**: `4dc064a684` (ATT-52, bell bottom alignment), `ATT-3852` (REQ-UI-395 sensor picker), `ATT-5192` (REQ-UI-437 relative width chips), `ATT-4371` / `ATT-4447` (REQ-UI-421 Bike Bell Button).
* **Root Reason for Existing Formulation**: Rapid iterative additions left English strings in localized XMLs, unconstrained line wrapping in reduced-width edit cards, and lacked bottom clearance between floating action buttons and docked elevation profile.
* **Preservation of Core Invariants**: 100% test pass rate; zero layout oscillation; 9-language translation parity maintained.

---

## 5. Architectural Strategy & High-Level Solution
* **Tile Typography**: In `SensorFieldView.kt`, apply `maxLines = 1` and edit-mode font scaling to `fieldState.value`; enforce `maxLines = 1` on `filterDescription`.
* **String Resources**: Update `values-de/strings.xml`, `values/strings.xml`, `values-de/strings_filters.xml`, and parity files for `Aktiv`, `Runde`, `Runde Nr.`, `Dist. Runde`, `Live-Segmente`, `Runden-Button`, `units_seconds = "s"`, `gleitender Durchschnitt`, `Done = "Fertig"`, and localized content descriptions `cd_move_field` / `cd_delete_field`.
* **Dialog Polish**: In `EditSensorFieldDialog.kt`, set title casing `"Anzeige anpassen"`, add `Modifier.fillMaxWidth()` to `Relative Breite`, allow `maxLines = 2` for category breadcrumb, style smoothing button with leading tune icon and trailing dropdown chevron.
* **FAB Clearance**: In `SensorGridScreen.kt`, add bottom clearance (`68.dp`) to `ElevationProfile` when `state.showLapButton || state.showBikeBell`, ensuring the bell FAB docks cleanly below the profile text.
* **Sport Context**: Update `TrackingTabPreviewHeader.kt` and `TrackingTabConfigHeader.kt` to accept `sportName` and render it as a primary-colored context label above the tab title/input.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% clean-room test pass rate; zero regression in landscape dual-pane tracking; 9-language localization parity strictly maintained.
* **Risk Rating**: **LOW** — Additive layout parameters, resource string refinements, and typography constraints with zero database or business-logic risks.
