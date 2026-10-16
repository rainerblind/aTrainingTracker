# Stage 1 Analysis: ATT-3776 — Replace Preview Zone FilterChips in ZonesSettingsActivity with Compact Zone-Tinted Bordered Pills

## 1. Problem Domain & Root Cause Analysis (RCA)

### 1.1 Observable Symptom
In `ZonesSettingsActivity.kt` (`SettingsScreenContent`, lines 777–796), the "Vorschau / Live Preview" section renders an interactive row of zone selector buttons (`Z1`..`Z5` for Heart Rate, `Z1`..`Z7` for Cycling Power). When the Cycling Power (`PWR_BIKE`, 7 zones) tab is selected on standard mobile viewports (`360dp`–`412dp` width, e.g. Google Pixel 10), every chip wraps its 2-character label vertically across two lines (`"Z\n1"` through `"Z\n7"`), distorting the preview selector row height and violating Rule 38 (`docs/design_guidelines.md` Section 5.6).

### 1.2 Technical Root Cause
- In `ZonesSettingsActivity.kt` (lines 777–796), each zone selector item is rendered using Material 3 `FilterChip(...)` inside a single `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp))` with `modifier = Modifier.weight(1f)` and a `leadingIcon` containing a `10.dp` colored circle.
- On a `360dp` viewport with `16dp` outer horizontal padding (`328dp` available width) and `6 × 6dp = 36dp` inter-item spacing, each of the 7 chips receives only `(328 - 36) / 7 ≈ 41.7dp` total width.
- Material 3 `FilterChip` enforces internal horizontal padding (`8dp` start + `8dp` icon-to-label + `16dp` end = `32dp` horizontal overhead) plus `10dp` for the `leadingIcon`, consuming `42dp` before any text is laid out. This leaves `< 0dp` for the `"Z1"`..`"Z7"` label, forcing `Text(zoneLabels[zIndex - 1])` (which lacks `maxLines = 1, softWrap = false`) to wrap `"Z1"` into `"Z\n1"`.

## 2. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Extends `REQ-UI-172` (*Configurable Zone Visual Presentation & Live Theme-Aware Preview*) and `REQ-UI-370` (*Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model*).
   - Target Requirement: Net-new `REQ-UI-384` (*Compact Zone-Tinted Bordered Selector Pills in Zone Settings Preview*), governed by **Rule 38** (`docs/design_guidelines.md` Section 5.6).
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-172` (`ATT-1390`, commit `e3966ebf`) introduced the 5-zone (`Z1`..`Z5`) `FilterChip` preview selector row in `ZonesSettingsActivity.kt`, where 5 chips had `~61dp` each and fit on one line.
   - `REQ-UI-370` (`ATT-3184`, commit `0da639ba`) expanded `PWR_BIKE` from 5 zones to 7 Coggan power zones (`Z1`..`Z7`), reusing the same `FilterChip` with `leadingIcon` row, which starved horizontal text width on 7-zone rows.
3. **Root Reason for Existing Formulation**:
   - `FilterChip` with a `leadingIcon` color dot was originally chosen to communicate both selection state and zone color identity.
4. **Preservation of Core Invariants**:
   - By replacing `FilterChip` + `leadingIcon` with a compact custom bordered/tinted pill (`ZonePreviewPill`) that uses the zone's canonical color (`zone1Color`..`zone7Color`) directly for its border and subtle background fill (`alpha = 0.12f` unselected matching `ZoneRow`, `alpha = 0.28f` selected, border `1.dp` unselected vs `2.dp` selected) and enforces `maxLines = 1, softWrap = false`, we preserve both zone color identity and selection state while providing `~34dp` of unobstructed text width per pill across 7 zones (`"Z1"`..`"Z7"` requires only `~16dp`), eliminating line wrapping across all viewports (`>= 320dp`) and font scales up to `130%`.

## 3. Scope Bounding (`ATT-1250`)

### In Scope
- Replace the `FilterChip` loop in `ZonesSettingsActivity.kt` (lines 777–796) with a dedicated compact composable `ZonePreviewPill` (or inline Surface/Box pill) in a single `Row(horizontalArrangement = Arrangement.spacedBy(6.dp))`.
- Style each pill with:
  - Shape: `RoundedCornerShape(8.dp)` (per `docs/design_guidelines.md` Section 5.3 for small elements/chips).
  - Background tint: `zoneColor.copy(alpha = if (selected) 0.28f else 0.12f)` (harmonized with `ZoneRow`'s `containerColor.copy(alpha = 0.12f)`).
  - Border: `BorderStroke(width = if (selected) 2.dp else 1.dp, color = if (selected) zoneColor else zoneColor.copy(alpha = 0.6f))`.
  - Centered single-line text (`"Z1"`..`"Z7"`, `maxLines = 1`, `softWrap = false`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`).
  - No `leadingIcon` dot.
- Add contract/unit tests verifying the replacement of `FilterChip` with compact single-line zone-tinted bordered pills.

### Out of Scope
- Header cleanup in `ZonesSettingsActivity.kt` (handled separately in `ATT-3777`).
- Changes to `ZoneRow` boundary input cards or `SensorFieldView` preview tile rendering.
