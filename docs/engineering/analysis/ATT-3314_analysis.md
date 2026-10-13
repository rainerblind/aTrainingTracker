# Stage 1: Problem Domain & Root Cause Analysis - ATT-3314: Display link icon on equipment matrix tabs in Sensors screen to clarify sensor assignment

**Ticket**: [ATT-3314](https://rainerblind.atlassian.net/browse/ATT-3314)  
**Sub-task**: [ATT-3402](https://rainerblind.atlassian.net/browse/ATT-3402) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-284` (amended & extended)  
**Test Mapping**: `TST-UI-244` (extended)  
**Branch**: `feature/ATT-3314`  
**Author**: AI Agent 1 (Forensic Requirements & Systems Architect)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-284` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation*, `ATT-2465`), targeting `DevicesTabbedScreen.kt`, `EquipmentSensorMatrixScreen.kt`, and `EquipmentTabsScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - Tickets `ATT-2126` (commit `fc10ad81`) and `ATT-2382` (Sprint `2026-40.16`) initially introduced the equipment-to-sensor matrix inside `EquipmentTabsScreen.kt`.
   - Sprint 2026-41.2 (`ATT-2465`, commit `3a18a931`) relocated the matrix into `DevicesTabbedScreen.kt` as Tab 3 ("Räder") and Tab 4 ("Schuhe") using `PrimaryScrollableTabRow` to streamline `EquipmentTabsScreen.kt` back to a clean 2-tab layout.
3. **Root Reason for Existing Formulation**:
   - `DevicesTabbedScreen.kt` rendered Tab 3 and Tab 4 with text-only titles: `stringResource(R.string.devices_tab_bikes)` ("Räder" / "Bikes") and `stringResource(R.string.devices_tab_shoes)` ("Schuhe" / "Shoes").
   - During the Sprint 2026-41.10 Joint Review of `ATT-3166`, the athlete noted that without a visual signifier, the labels "Räder" and "Schuhe" read identically to equipment inventory tabs (like the ones in `EquipmentTabsScreen.kt`), obscuring the fact that these tabs host the interactive sensor-to-equipment assignment matrix.
4. **Preservation of Core Invariants**:
   - 5-Tab Architecture: The sequence (0: Available, 1: Paired, 2: All Known, 3: Bikes Matrix, 4: Shoes Matrix) must remain unchanged.
   - Independent Scroll States: `bikesHorizontalScrollState` and `shoesHorizontalScrollState` must remain independent.
   - Tab Height & Row Alignment: The tab height must remain standardized at 48.dp without vertical inflation or baseline misalignment between text-only tabs (0–2) and matrix tabs (3–4).
   - 1-Tap Checkbox Persistence: Fast interactive sensor-to-equipment linking must remain functional.
   - 100% 9-Language Localization Parity: Parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT must be strictly preserved.
   - Clean-Room Test Pass Rate: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Executive Summary

In `DevicesTabbedScreen.kt`, Tab 3 and Tab 4 host the equipment-to-sensor mapping matrices (`EquipmentSportSensorMatrix`) for Bikes and Shoes. While tabs 0, 1, and 2 host sensor inventory lists (Available, Paired, Known), tabs 3 and 4 serve a distinctly different role: configuring the N:M associative linkages between athlete equipment and hardware sensors.

However, tabs 3 and 4 currently render plain text labels: "Räder" and "Schuhe" (or "Bikes" and "Shoes" in English). During the Sprint 2026-41.10 Joint Review of ATT-3166, user testing revealed cognitive ambiguity: users expected these tabs to display simple equipment lists identical to the main Equipment screen, rather than the sensor linkage matrix.

This deliverable specifies the addition of a visual link/chain symbol (`Icons.Default.Link`) to the tab headers for Tab 3 (Bikes) and Tab 4 (Shoes) in `DevicesTabbedScreen.kt`. By embedding the link icon inline with the localized tab label inside the `Tab` composable's `text` slot, the UI clearly signals the relational assignment purpose of these tabs while preserving uniform tab row height, scroll behavior, and screen reader accessibility.

---

## 2. Problem Statement & Root Cause

### 2.1 The Issue
Athletes opening the Sensors view (`DevicesTabbedScreen`) encounter 5 tabs:
- Tab 0: Available (`@string/devices_tab_available`)
- Tab 1: Paired (`@string/devices_tab_paired`)
- Tab 2: Known (`@string/devices_tab_known`)
- Tab 3: Räder (`@string/devices_tab_bikes`)
- Tab 4: Schuhe (`@string/devices_tab_shoes`)

Tabs 0–2 are sensor device lists. Tabs 3–4 are equipment-sensor mapping matrices. Because tabs 3 and 4 only display the equipment name ("Räder" / "Schuhe"), their label semantics conflict with `EquipmentTabsScreen.kt` (which also has "Räder" and "Schuhe" tabs). Athletes do not immediately understand that tabs 3 and 4 in Sensors are for sensor assignment.

### 2.2 Forensic Inspection of `DevicesTabbedScreen.kt`
Currently, `PrimaryScrollableTabRow` in `DevicesTabbedScreen.kt` (lines 317–331) iterates over `tabTitles`:
```kotlin
tabTitles.forEachIndexed { index, title ->
    Tab(
        selected = pagerState.currentPage == index,
        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
        text = {
            Text(title)
        }
    )
}
```
All tabs receive an identical plain `Text(title)` composable in the `text` slot. There is no icon or visual cue differentiating inventory tabs from assignment matrix tabs.

---

## 3. Technical Solution & UX Design

### 3.1 Inline Leading Icon Pattern
Material 3's `Tab` composable supports an `icon` slot and a `text` slot. However, using `Tab(icon = ..., text = ...)` renders the icon vertically above the text, which expands the tab height to 64.dp/72.dp and creates jarring visual disparity if only some tabs have icons.

Instead, the recommended pattern for inline indicators within `PrimaryScrollableTabRow` is placing a `Row` inside the `text` slot:
```kotlin
tabTitles.forEachIndexed { index, title ->
    val isMatrixTab = index == 3 || index == 4
    Tab(
        selected = pagerState.currentPage == index,
        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
        text = {
            if (isMatrixTab) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LocalContentColor.current
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(title)
                }
            } else {
                Text(title)
            }
        }
    )
}
```

### 3.2 Visual Ergonomics & Invariants
- **Icon Sizing**: 16.dp provides crisp visibility without crowding the text or causing excessive tab width.
- **Spacing**: A 6.dp `Spacer` ensures clear separation between the link glyph and the text label.
- **Color & Tint**: Binding `tint = LocalContentColor.current` ensures the icon dynamically follows Material 3 tab selection state (primary color when selected, on-surface-variant when unselected).
- **Height Uniformity**: Standard 48.dp `PrimaryScrollableTabRow` height is maintained across all 5 tabs.
- **Accessibility**: Screen readers continue announcing the localized tab title cleanly without redundant icon descriptions.

---

## 4. Scope Bounding

### In-Scope
1. Updating `PrimaryScrollableTabRow` in `DevicesTabbedScreen.kt` to render `Icons.Default.Link` on Tab 3 (Bikes) and Tab 4 (Shoes).
2. Updating `DevicesTabbedScreenContractTest.kt` to verify that Tab 3 and Tab 4 incorporate the link icon.
3. Amending living requirement `REQ-UI-284` and test specification `TST-UI-244` in `docs/requirements.md` and `docs/tests.md`.
4. Executing full clean-room unit regression test suite (`./gradlew testDebugUnitTest`).

### Out-of-Scope
- Modifying `EquipmentSportSensorMatrix.kt` internal table cells or sticky header layout.
- Modifying `EquipmentTabsScreen.kt` (Equipment management screen remains 2 tabs).
- Altering database schema or SQLite `LINKS` table persistence.
