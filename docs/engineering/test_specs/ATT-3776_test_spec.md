# Stage 2 Requirement & Test Specification: ATT-3776

## 1. Requirement Specification (`REQ-UI-384`)

### `REQ-UI-384`: Compact Zone-Tinted Bordered Selector Pills in Zone Settings Preview
- **Parent Ticket**: `ATT-3776`
- **Governing Rule**: **Rule 38** (`docs/design_guidelines.md` Section 5.6 — *No Single-Character or Word Wrapping in Dense Horizontal Chip / SegmentedButton Rows*)
- **Requirement Statement**:
  1. **Elimination of `FilterChip` & `leadingIcon` Overhead (`REQ-UI-384.1`)**:
     - The live preview zone selector row in `ZonesSettingsActivity.kt` SHALL NOT use Material 3 `FilterChip` or a separate `leadingIcon` circle indicator.
     - Instead, it SHALL render a single horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp))` containing `zoneCount` (`5` for Heart Rate, `7` for Cycling Power) equal-weight (`Modifier.weight(1f)`) `ZonePreviewPill` composables.
  2. **Zone-Tinted Border, Subtle Fill Styling, Accessibility Semantics & WCAG AA Contrast (`REQ-UI-384.2`)**:
     - Each `ZonePreviewPill` SHALL use `RoundedCornerShape(8.dp)` and Material 3 selectable `Surface(selected = selected, onClick = onClick, ...)` semantics so TalkBack / accessibility services announce selection state.
     - Background container color SHALL be tinted with the zone's canonical color (`zone1Color`..`zone7Color`): `zoneColor.copy(alpha = if (selected) 0.28f else 0.12f)`, harmonizing with `ZoneRow`'s `0.12f` card background alpha.
     - Border stroke SHALL use the zone's canonical color: `BorderStroke(width = if (selected) 2.dp else 1.dp, color = if (selected) zoneColor else zoneColor.copy(alpha = 0.6f))`.
     - Label text color (`MaterialTheme.colorScheme.onSurface`) over the subtle zone-tinted background (`0.12f` / `0.28f` alpha blended over `surface`) SHALL maintain a WCAG AA contrast ratio $\ge 4.5:1$ in both Light and Dark/AMOLED themes.
  3. **Single-Line Centered Label & Font Scale Guarantee (`REQ-UI-384.3`)**:
     - Each pill SHALL render centered text `"Z1"`..`"Z7"` with `maxLines = 1`, `softWrap = false`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, and compact horizontal padding (`<= 4.dp`), guaranteeing zero vertical line wrapping across all 5-zone and 7-zone tabs even under Large/Largest system font scales (up to 130%).

### Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-384`, complementing `REQ-UI-172` (`ATT-1390`) and `REQ-UI-370` (`ATT-3184`). Targets `ZonesSettingsActivity.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced 5 `FilterChip`s for Material 3 compliance; Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) expanded `PWR_BIKE` to 7 `FilterChip`s.
3. **Root Reason for Existing Formulation**:
   - Material 3 `FilterChip` with a `10.dp` `leadingIcon` circle was initially chosen to show zone color alongside selection state, but its ~32dp internal horizontal padding plus 10dp icon overhead proved incompatible with the density of 7-zone labels on standard 360dp–412dp screen widths.
4. **Preservation of Core Invariants**:
   - Encoding zone color into the pill border and background tint preserves both color identity, WCAG AA contrast ($\ge 4.5:1$), and selection state (`Surface(selected = selected, onClick = onClick)`) while freeing ~32dp of horizontal padding per pill.

### Acceptance Criteria (Given-When-Then)
- **Given** `ZonesSettingsActivity` rendered on either a 5-zone HR tab (`HR_RUN`, `HR_BIKE`) or 7-zone Cycling Power tab (`PWR_BIKE`) including under 130% Large system font scale,
- **When** the live preview zone selector row renders,
- **Then** all `Z1`..`Z5` or `Z1`..`Z7` pills render on a single line (`maxLines = 1`, `softWrap = false`) with zone-colored borders, subtle zone-tinted fills ($\ge 4.5:1$ text contrast), and clicking any pill updates `previewZone` and announces selection state.

### System Invariants
- **Single-Line Layout Invariant (Rule 38)**: Zero vertical line wrapping (`"Z\n1"`..`"Z\n7"`) across 5-zone and 7-zone tabs up to 130% font scale.
- **Accessibility & Contrast Invariant**: Selectable `Surface(selected = selected, onClick = onClick)` semantics and WCAG AA contrast ratio $\ge 4.5:1$ MUST be maintained.
- **Preview Functional Parity**: Clicking any pill (`1..zoneCount`) MUST update `previewZone` and immediately update the live `SensorFieldView` preview tile.
- **Clean-Room Test Suite**: 100% pass rate across `./gradlew testDebugUnitTest` MUST NOT be broken.

## 2. Test Specification (`TST-UI-347`)

### `TST-UI-347`: Compact Zone-Tinted Bordered Preview Pills Verification Suite (`ZonesSettingsPreviewPillsContractTest.kt`)
1. **`testPreviewZoneSelectorUsesCompactZonePreviewPillsWithoutFilterChip`**:
   - Inspect `ZonesSettingsActivity.kt` source and verify `fun ZonePreviewPill(` is declared and invoked inside the `(1..zoneCount).forEach` loop.
   - Verify `ZonesSettingsActivity.kt` contains zero `FilterChip(` invocations.
2. **`testZonePreviewPillAppliesZoneTintedFillBorderSelectableSemanticsAndSingleLineText`**:
   - Verify `ZonePreviewPill` uses selectable `Surface(selected = selected, onClick = onClick, ...)` with `RoundedCornerShape(8.dp)`, `0.28f` and `0.12f` background alphas, `BorderStroke` with `2.dp` / `1.dp` widths, and `Text` with `maxLines = 1` and `softWrap = false`.
3. **`test7ZonePillWidthBudgetUnder130PercentFontScaleAndWcagContrast`**:
   - Verify mathematically that on a 360dp viewport (`328dp` content width, `6 * 6dp = 36dp` spacing, `41.7dp` per pill, `2 * 4dp = 8dp` inner padding -> `33.7dp` inner text width), `"Z7"` at 130% font scale (`12sp * 1.3 = 15.6sp`, ~`20dp` width) fits with $> 13\text{ dp}$ safety margin and zero wrapping.
   - Verify WCAG relative luminance contrast ratio between `onSurface` and alpha-composited `zoneColor.copy(alpha = 0.28f)` / `0.12f` over light and dark surfaces is $\ge 4.5:1$.

## 3. Localization Audit
- Labels `"Z1"`..`"Z7"` are universal physiological zone abbreviations used across all 9 locales; no new localized string keys are required, and existing 9-locale parity is 100% preserved.

## 4. Traceability Matrix

| Requirement ID | Test ID | Test Class | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-384` | `TST-UI-347` | `ZonesSettingsPreviewPillsContractTest.kt` | Planned |
