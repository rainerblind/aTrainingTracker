# Stage 2 Requirement & Test Specification: ATT-3771

## 1. Requirement Specification (`REQ-UI-386`)

### `REQ-UI-386`: Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Architecture (Rule 38)
The system SHALL prevent vertical word and character wrapping (`"Histo-\ngramm"`) across all 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` controls in `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` by enforcing compact single-line `SegmentedButton` layout and concise 9-locale chart labels (`<= 11` characters) in strict compliance with Rule 38 (`docs/design_guidelines.md` Section 5.6, `ATT-3771`, amending `REQ-UI-372`):

1. **Checkmark Suppression & Compact Horizontal Padding (`REQ-UI-386.1`)**:
   - Every `SegmentedButton` in the 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` across `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` SHALL specify `icon = {}` and `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`.
   - `SingleChoiceSegmentedButtonRow` in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` SHALL include `Modifier.fillMaxWidth().height(28.dp)` so buttons distribute available card width evenly rather than shrinking to intrinsic width.
2. **Single-Line Typography & Dual Selection Cue (`REQ-UI-386.2`)**:
   - Every `SegmentedButton` label `Text` in the 3-way `ZoneCardDisplayMode` selector across `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` SHALL specify:
     - `style = MaterialTheme.typography.labelSmall`
     - `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`
     - `maxLines = 1`
     - `softWrap = false`
     - `overflow = TextOverflow.Ellipsis`
3. **Concise 9-Locale Chart Mode Labels (`REQ-UI-386.3`)**:
   - Across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` SHALL use concise statistical chart nouns of at most `11` characters (`values`: `"Stacked Bar"`, `values-de`: `"Balken"`, `values-es`: `"Barra"`, `values-fr`: `"Barre"`, `values-it`: `"Barra"`, `values-ja`: `"スタックバー"`, `values-nl`: `"Balk"`, `values-pl`: `"Pasek"`, `values-pt`: `"Barra"`), ensuring zero line wrapping and zero truncation even at 150% system font scale on 360dp viewports.

### Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**: Amends `REQ-UI-372` (*Horizontal Stacked Distribution Bar & 3-Way Mode Switching in Zone Cards*, `ATT-3390` & `ATT-3676`), governed by Rule 38. Tracked as `REQ-UI-386`.
2. **Historical Origin & Commit Trace**: Sprint `2026-42.4` (`ATT-3390`, commit `63ccd164`) and Sprint `2026-42.5` (`ATT-3676`, commit `b989e1ce`).
3. **Root Reason for Existing Formulation**: `WorkoutMasksAndCardsSection.kt` copied the 2-way `LapDisplayMode` `SingleChoiceSegmentedButtonRow` without `icon = {}` or `labelSmall`, and 6 locales used verbose 13–17 char translations for `zone_mode_stacked_bar`.
4. **Preservation of Core Invariants**: 3-way `ZoneCardDisplayMode` switching (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`), WCAG 2.1 AA contrast (`>= 4.5:1` text, `>= 3.0:1` boundary), dual non-color selection cues (`FontWeight.Bold` + `secondaryContainer`), and `< 400` line limit on `WorkoutMasksAndCardsSection.kt` are strictly preserved.

### Acceptance Criteria (Given-When-Then)
- **Given** `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt`,
  **When** rendering the 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` in any of the 9 supported locales (up to 150% system font scale on a 360dp viewport),
  **Then** each `SegmentedButton` renders with `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `style = MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis` on a single horizontal line without wrapping.
- **Given** all 9 `strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`),
  **When** auditing `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram`,
  **Then** all 27 strings are non-empty and have length `<= 11` characters.

---

## 2. Test Specification (`TST-UI-349`)

### `TST-UI-349`: Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Verification Suite
1. **Source Contract Audit (`ZoneDistributionStackedBarLocalizationTest.kt` / `WorkoutSummaryZoneDisplayModeTest.kt`)**:
   - Verify that `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` configure the `ZoneCardDisplayMode` `SegmentedButton`s with `icon = {}`, `PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis`.
   - Verify that `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` apply `Modifier.fillMaxWidth().height(28.dp)` on `SingleChoiceSegmentedButtonRow`.
2. **9-Locale Character Budget & Width Math Audit (`ZoneDistributionStackedBarLocalizationTest.kt`)**:
   - Parse `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
   - Assert every string is non-empty and `value.length <= 11`, guaranteeing zero line wrapping and zero truncation up to 150% system font scale on a 360dp viewport.
3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Test Case ID | Target Production Files | Target Test Files |
| :--- | :--- | :--- | :--- |
| `REQ-UI-386` | `TST-UI-349` | `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `values*/strings.xml` | `ZoneDistributionStackedBarLocalizationTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt` |
