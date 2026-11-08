# Stage 2: Requirement & Test Specification - ATT-5879: TrackingTabs & Cockpit Polish

**Ticket**: [ATT-5879](https://atrainingtracker.atlassian.net/browse/ATT-5879)  
**Parent Epic**: [ATT-5908](https://atrainingtracker.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5879`  
**Requirement Mapping**: `REQ-UI-471` (*Cockpit Edit-Mode Typography, Localization Parity & FAB Layout Polish*)  
**Test Spec ID**: `TST-UI-471` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-471)

### 1.1 Problem Statement & Rationale
Walkthrough inspection identified 6 cockpit defects: (1) 8-character time values (`18:29:15`) wrapping across lines (`18:29:1`/`5`) in 2-column edit mode; (2) filter badges wrapping into a 3-line vertical stack (`2`/`sec`/`Ø`) with non-SI `sec` instead of `s`; (3) German localization gaps (`aktiv` $\rightarrow$ `Aktiv`, `Lap`/`Lap Nr`/`Dist. Lap` $\rightarrow$ `Runde`/`Runde Nr.`/`Dist. Runde`, `Live Segments` $\rightarrow$ `Live-Segmente`, `Lap Knopf` $\rightarrow$ `Runden-Button`, `Done` $\rightarrow$ `Fertig`, untranslated move/delete field content descriptions); (4) `EditSensorFieldDialog` polish (title casing `Anzeige anpassen`, un-truncated `maxLines = 2` category breadcrumb, start-aligned `Relative Breite`, styled smoothing button with leading `Icons.Default.Tune` and trailing chevron); (5) bicycle bell FAB overlapping the elevation profile placeholder banner on the `Karte` tab; (6) missing sport context (e.g., `Radeln`) in preview and editor headers.

### 1.2 Functional & Architectural Requirements
1. **Edit-Mode Telemetry & Filter Typography (`REQ-UI-471.1`)**: In `SensorFieldView.kt`, telemetry value text SHALL enforce `maxLines = 1` and responsive auto-sizing in edit mode to keep 8-character values (`18:29:15`) on a single line. The filter badge SHALL enforce `maxLines = 1` with SI unit `s` (`units_seconds`), preventing 3-line vertical wrapping.
2. **German Localization & Action Parity (`REQ-UI-471.2`)**: Capitalize `time_active_short` as `Aktiv`. Translate German lap labels: `Rundenzeit` (`time_lap`), `Runde` (`time_lap_short`), `Rundennummer` (`lap_number`), `Runde Nr.` (`lap_number_short`), `Rundendistanz` (`distance_lap`), `Dist. Runde` (`distance_lap_short`). Translate `Live-Segmente`, `Runden-Button`, and `Fertig` (`Done`). Add localized content descriptions `cd_move_field` and `cd_delete_field` across all 9 locales.
3. **Field Configuration Dialog Polish (`REQ-UI-471.3`)**: In `EditSensorFieldDialog.kt`, set sentence-case title `Anzeige anpassen`, expand category breadcrumb to `maxLines = 2`, start-align `Relative Breite` (`Modifier.fillMaxWidth()`), and style the smoothing button with a leading tune icon, trailing chevron, and formatted summary (`2 s gleitender Durchschnitt`).
4. **Bicycle Bell FAB & Elevation Profile Clearance (`REQ-UI-471.4`)**: In `SensorGridScreen.kt`, allocate 68dp bottom clearance to `ElevationProfile` in portrait mode when `showBikeBell` or `showLapButton` is active, preventing FAB overlap over the empty-state banner.
5. **Sport Context in Tab Headers (`REQ-UI-471.5`)**: `TrackingTabPreviewHeader` and `TrackingTabConfigHeader` SHALL accept and render the active sport name (e.g. `Radeln`) as a context label above the tab title.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Edit-Mode Single-Line Telemetry)**:
  * *Given* a 2-column sensor grid in edit mode with `Uhrzeit` (`18:29:15`),
  * *When* rendered,
  * *Then* the time value remains strictly on a single line (`maxLines = 1`).
* **Criterion 2 (Single-Line Filter Badge with SI Unit)**:
  * *Given* a sensor field with moving average smoothing (e.g. speed),
  * *When* rendered in edit mode,
  * *Then* the filter badge displays `2 s Ø` on a single line without wrapping into 3 lines.
* **Criterion 3 (German Localization Parity)**:
  * *Given* German locale active,
  * *When* viewing active duration, lap fields, and editor toggles,
  * *Then* labels display `Aktiv`, `Runde`, `Runde Nr.`, `Dist. Runde`, `Live-Segmente`, `Runden-Button`, and `Fertig`.
* **Criterion 4 (Field Configuration Dialog Polish)**:
  * *Given* `EditSensorFieldDialog` opened,
  * *When* rendered,
  * *Then* title displays `Anzeige anpassen`, `Relative Breite` is start-aligned, category breadcrumb does not truncate, and smoothing button includes tune icon and chevron.
* **Criterion 5 (Bell FAB Bottom Clearance)**:
  * *Given* `Karte` tab in portrait mode with `showBikeBell = true` and `showElevationProfile = true`,
  * *When* rendered,
  * *Then* the bell FAB does not obscure the elevation profile empty state banner.
* **Criterion 6 (Header Sport Context)**:
  * *Given* `TrackingTabPreviewHeader` or `TrackingTabConfigHeader` displayed,
  * *When* rendered,
  * *Then* the active sport discipline name (e.g. `Radeln`) is rendered above the tab title.

### 1.4 System Invariants & Chesterton's Fence
* Zero regressions in dual-pane landscape tracking layouts (`REQ-UI-431`).
* 100% 9-language localization parity across all resource files.
* Zero layout oscillation in elevation profile sizing.

---

## 2. Test Specification (TST-UI-471)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-471.1]` | Unit | `SensorFieldViewTest`: 8-char time stays on single line; filter badge single-line `2 s Ø` | `REQ-UI-471.1` | Specified |
| `[TST-UI-471.2]` | Contract | `TranslationParityTest`: 9-locale parity for `cd_move_field`/`cd_delete_field` & German strings | `REQ-UI-471.2` | Specified |
| `[TST-UI-471.3]` | Contract | `EditSensorFieldDialogTest`: Sentence-case title, start-aligned width header, smoothing button | `REQ-UI-471.3` | Specified |
| `[TST-UI-471.4]` | Contract | `SensorGridScreenTest`: Elevation profile 68dp bottom clearance when bell FAB active | `REQ-UI-471.4` | Specified |
| `[TST-UI-471.5]` | Contract | `TrackingTabHeaderTest`: Sport context rendered in preview and config headers | `REQ-UI-471.5` | Specified |
| `[TST-UI-471.6]` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` | `REQ-UI-471.1`..`5` | Specified |
