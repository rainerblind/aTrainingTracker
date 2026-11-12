# Stage 1 Analysis: SportingJourneySheet & Components Redesign & Polish (ATT-7323)

## 1. Executive Summary & Problem Domain
* **Ticket**: `ATT-7323` (`[Verbesserung] SportingJourneySheet & SportingJourneyComponents: Material 3 redesign, row weight wrapping fixes, grid layout, and locale/unit formatting`)
* **Parent Epic**: `ATT-67` (Zeiträume / Periods & Stats)
* **Target Version**: `V5.0.0`
* **Defect Discovery**: Follow-up split (Part 2 — UI & UX Layer) from `ATT-7321`. During physical device review and UX audit of "The Trophy Vault — My Sporting Journey", four major UI/UX defects were identified across `SportingJourneySheet.kt`, `SportingJourneyComponents.kt`, `SportingJourneyShareHelper.kt`, and associated resources:
  1. **Non-Native Terminology, Ad-Hoc Emojis & SQLite Developer Jargon**:
     - Trademark Strava terminology ("Local Legend" in `sporting_journey_badge_legend_*`) and prototype phrasing ("Your %1$d Season Wrapped").
     - Raw SQLite internal jargon exposed directly to athletes ("Hidden Gems from SQLite" in `sporting_journey_hidden_gems_badge`).
     - Ad-hoc decorative Unicode emojis in badge strings and UI composables (`🌍`, `🏔️`, `🌲`, `🔒`), violating project design standard `ATT-5400` / `REQ-UI-458.3`.
  2. **Row Mid-Word Wrapping Across Headers & Cards**:
     - Top headers and stat cards in `HeroTotalsCard`, `HiddenGemsSection`, `GemCard`, `MilestonesSection`, `AchievementRoomSection`, and `AthletePassportShareCard` lack `Modifier.weight(1f)` constraints across their horizontal arrangement pairs (`Arrangement.SpaceBetween`).
     - In non-English locales or long numerical values, text strings wrap abruptly mid-word or compress trailing badges/buttons off-screen.
  3. **Broken Grid Layout in Achievement Room**:
     - `AchievementRoomSection` renders badges using `FlowRow` with `.weight(1f)` on individual items.
     - In incomplete rows (e.g. 1–3 items in a 4-column layout), items stretch across the entire row width, producing distorted, non-uniform cards of varying widths and heights.
  4. **Hardcoded Locale.US & Metric Units Non-Compliance**:
     - Numerical values (`totalDistanceKm`, `totalElevationM`, `totalActiveHours`, `pedalRevolutions`, etc.) are formatted using hardcoded `Locale.US`, ignoring user locale decimal/thousand separators (`Locale.getDefault()`).
     - Units are hardcoded to `km` and `m`, ignoring athlete preference (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`).
     - `SportingJourneyShareHelper` text is hardcoded in English with US formatting, ignoring device locale and imperial units.

---

## 2. Independent Forensic Code Audit & Root Causes

| Defect Area | Current Flawed Implementation | Root Cause & Code Site | Remediated Design |
| :--- | :--- | :--- | :--- |
| **Terminology & Jargon** | "Local Legend", "Season Wrapped", "Hidden Gems from SQLite" | `strings_sporting_journey.xml` across all 9 locales. | Replace "Local Legend" with "Route Master" (and 9-locale equivalents), "Season Wrapped" with "Season Retrospective", and "Hidden Gems from SQLite" with "Offline Sensor Insights". |
| **Ad-Hoc Emojis (ATT-5400)** | Raw emojis in strings (`🌍`, `🏔️`) and `Text(item.emoji)` in UI | `strings_sporting_journey.xml:10,14` and `SportingJourneyComponents.kt:502,613`. | Strip decorative emojis from XML strings. Replace raw emoji text in `MilestonesSection` and `AchievementRoomSection` with Material 3 vector icons (`Icons.Default.Terrain`, `Speed`, `Timer`, `Bolt`, `Public`, `Lock`, `EmojiEvents`, etc.) within tonal containers. |
| **Row Mid-Word Wrapping** | `Row(SpaceBetween) { Column { ... } Surface { ... } }` without weight | `SportingJourneyComponents.kt:177,284,413,464,559,692`. | Apply `Modifier.weight(1f)` to title/content containers in all header rows, ensuring natural word wrapping and stable trailing badge/button layouts. |
| **Achievement Grid Layout** | `FlowRow(maxItemsInEachRow = 4) { Surface(Modifier.weight(1f)) }` | `SportingJourneyComponents.kt:586-607` distorts incomplete row item widths. | Replace `FlowRow` with a chunked grid (`achievements.chunked(4)`), wrapping each row in a `Row` where every cell has `Modifier.weight(1f)` and empty slots are padded with `Spacer(Modifier.weight(1f))` for uniform width and height. |
| **Locale & Unit Formatting** | `String.format(Locale.US, ...)` and hardcoded `km`/`m` | `SportingJourneyComponents.kt:183,216,221,232,321,327,340` & `SportingJourneyShareHelper.kt:49-60`. | Use `Locale.getDefault()`. Format distance and elevation according to `TrainingApplication.getUnit()` (km vs mi, m vs ft). Localize share passport text with string resources. |

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
* Audited live `sprint/2026-46.4` codebase at `SportingJourneySheet.kt`, `SportingJourneyComponents.kt`, `SportingJourneyShareHelper.kt`, and `strings_sporting_journey.xml` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* Verified `ATT-5400` / `REQ-UI-458.3` test patterns in existing contract tests (`SwapOrUpgradeDialogContractTest`, `DisplaySettingsTest`, `SupporterCopyContractTest`) strictly prohibiting decorative inline emojis in string resources.
* Confirmed that `BANALService.METER_PER_MILE` (1609.344) and `BANALService.METER_PER_FOOT` (0.3048) are the standard conversion constants across `FilterFormattingUtils` and `PeriodBarGraph`.
* Verified that chunking `achievements.chunked(4)` inside the existing scrollable sheet layout guarantees identical item widths across all rows without requiring nested vertical scrolling.

---

## 4. Change Impact Analysis (SUP.10 / REQ-PRO-064)
* **Affected Requirements**: `REQ-SPT-003` (Hidden Gems & Achievement Engine), `REQ-SPT-004` (Athlete Passport & 9-Locale Parity), `REQ-SPT-005` (M3 Hierarchy, Wrapping & Unit System Parity).
* **Affected Architecture Elements**: `SportingJourneySheet.kt`, `SportingJourneyComponents.kt`, `SportingJourneyShareHelper.kt`, `strings_sporting_journey.xml` (all 9 language folders).
* **Verification Impact & Test Surface**:
  - `SportingJourneyComponentsTest.kt`: Unit test suite verifying uniform grid layout, weight distribution, and formatting.
  - `SportingJourneyShareHelperTest.kt`: Unit tests verifying `Locale.getDefault()`, imperial unit conversions (`mi`, `ft`), and localized passport text.
  - Localization parity test across all 9 locales (`TranslationParityTest` / custom XML contract test).
* **Risk Assessment & Invariants**:
  - **Risk Rating**: `LOW` (Pure UI/UX styling, layout structuring, string cleanup, and formatting; zero database schema or calculation logic changes).
  - **System Invariants**:
    - Zero changes to underlying aggregation algorithms or database tables.
    - Milestone navigation callbacks (`onNavigateToWorkout`, `WorkoutNavigationEvents`) must remain 100% operational.
    - Supporter tier badge and share sheet dispatch must remain intact.
