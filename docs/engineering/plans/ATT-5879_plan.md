# Stage 3: Implementation Plan - ATT-5879: TrackingTabs & Cockpit Polish

**Ticket**: [ATT-5879](https://atrainingtracker.atlassian.net/browse/ATT-5879) | **Target Release**: `V5.0.0` | **Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5879`  
**Requirement Mapping**: `REQ-UI-471` | **Test Mapping**: `TST-UI-471` | **Author**: AI Agent 1 (Implementer, `subagent_pro`)  

---

## 1. Problem Description & Background
Walkthrough inspection identified 6 cockpit visual and localization defects:
1. 8-char time values (`18:29:15`) wrapping across lines in 2-column edit mode.
2. Filter badge (`2 sec Ø`) wrapping into a 3-line stack (`2` / `sec` / `Ø`) and using non-SI `sec`.
3. German localization gaps (`aktiv` $\rightarrow$ `Aktiv`, `Lap` $\rightarrow$ `Runde`, `Live Segments` $\rightarrow$ `Live-Segmente`, `Lap Knopf` $\rightarrow$ `Runden-Button`, `Done` $\rightarrow$ `Fertig`, untranslated move/delete content descriptions).
4. `Anzeige anpassen` dialog polish (title casing, truncated category breadcrumb, centered `Relative Breite`, plain smoothing button).
5. Bicycle bell FAB obscuring elevation profile placeholder text on `Karte` tab.
6. Missing sport context (e.g. `Radeln`) in preview and editor headers.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-471` (*Cockpit Edit-Mode Typography, Localization Parity & FAB Layout Polish*)
  * `REQ-UI-471.1`: Edit-mode telemetry single-line auto-scaling (`maxLines = 1`) & single-line filter badge with SI unit `s`.
  * `REQ-UI-471.2`: 9-language localization parity (`Aktiv`, `Runde`, `Runde Nr.`, `Dist. Runde`, `Live-Segmente`, `Runden-Button`, `Fertig`, `cd_move_field`, `cd_delete_field`).
  * `REQ-UI-471.3`: Field configuration dialog polish (sentence-case title, `maxLines = 2` breadcrumb, start-aligned `Relative Breite`, tuned smoothing button).
  * `REQ-UI-471.4`: Elevation profile 68dp bottom clearance on portrait map/cockpit tabs with active bell/lap FABs.
  * `REQ-UI-471.5`: Sport discipline context (e.g., `Radeln`) rendered in preview and config headers.
* **Test Mapping**: `TST-UI-471` (`TST-UI-471.1` through `TST-UI-471.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Landscape Regressions**: Dual-pane map layout (`REQ-UI-431`) and `BottomStart` button alignment remain unaffected.
2. **Recomposition Boundary Isolation**: `@Immutable TrackingMapState` contract tests remain untouched and green.
3. **9-Language Parity**: All new and modified string keys verified via `TranslationParityTest`.
4. **UI Consistency**: Material 3 typography (`labelMedium` for sport context), consistent `Icons.Default.Tune` and `ArrowDropDown`, and theme color tokens.

---

## 4. Proposed Architectural Changes
* **Component 1: Telemetry & Filter Typography (`SensorFieldView.kt`)**: Add `maxLines = 1` and `softWrap = false` to telemetry value `Text`; apply responsive edit-mode font size scaling for compact tiles; constrain filter summary text to `maxLines = 1` and `softWrap = false`; use localized `R.string.cd_move_field` and `R.string.cd_delete_field`.
* **Component 2: Field Configuration Dialog (`EditSensorFieldDialog.kt`)**: Expand category breadcrumb to `maxLines = 2`; add `modifier = Modifier.fillMaxWidth()` to `Relative Breite` header; update smoothing button with leading `Icons.Default.Tune` icon, weight-expanded text, and trailing `Icons.Default.ArrowDropDown`.
* **Component 3: FAB Overlap Clearance (`SensorGridScreen.kt`)**: In portrait mode when `state.showElevationProfile && (state.showLapButton || state.showBikeBell)`, apply `68.dp` bottom padding to the `ElevationProfile` container surface.
* **Component 4: Header Sport Context (`TrackingTabPreviewHeader.kt`, `TrackingTabConfigHeader.kt`, `TrackingTabsScreen.kt`)**: Add `sportName: String` parameter and render primary-colored `labelMedium` sport context above tab title/input.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Telemetry & Filter Typography**: Update `SensorFieldView.kt` with `maxLines = 1`, edit-mode font scaling, single-line filter badge, and localized action content descriptions.
* **Step 2: Field Configuration Dialog Polish**: Update `EditSensorFieldDialog.kt` (`maxLines = 2` category breadcrumb, start-aligned width header, tuned smoothing button).
* **Step 3: FAB Clearance**: Update `SensorGridScreen.kt` to apply 68dp bottom clearance on `ElevationProfile` when bottom FABs are enabled in portrait.
* **Step 4: Header Sport Context**: Update `TrackingTabPreviewHeader.kt`, `TrackingTabConfigHeader.kt`, and `TrackingTabsScreen.kt` to thread and display `sportName`.
* **Step 5: Unit & Contract Tests**: Author tests verifying single-line constraints, dialog alignment, FAB clearance, and header sport context (`SensorFieldViewTest`, `TrackingTabHeaderTest`).
* **Step 6: Verification & Governance Suite**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`ui.tracking.*`, `localization.TranslationParityTest`), requirement governance check, and visual inspection.
* **Rollback**: Clean git revert of commit on `feature/ATT-5879`.
