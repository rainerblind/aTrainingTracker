# Stage 2 Test Specification: SportingJourney UI & UX Redesign, M3 Hierarchy, Grid Layout & Locale Formatting (ATT-7323)

## 1. Requirement Specification (REQ-SPT-005)

* **REQ-SPT-005.1** (*Authentic Material 3 Terminology & De-Jargonization*):
  - `strings_sporting_journey.xml` across all 9 supported locales SHALL replace Strava trademark terminology ("Local Legend") with authentic athletic terminology "Route Master" (de: "Routen-Meister", es: "Maestro de rutas", fr: "Maître du parcours", it: "Maestro del percorso", ja: "定番ルートマスター", nl: "Route-meester", pl: "Mistrz tras", pt: "Mestre de percursos").
  - Prototype phrasing "Your %1$d Season Wrapped" SHALL be replaced with "Your %1$d Season Retrospective" (de: "Dein %1$d Saison-Rückblick", etc.).
  - SQLite developer jargon "Hidden Gems from SQLite" SHALL be replaced with user-facing "Offline Sensor Insights" (de: "Verborgene Sensor-Einblicke", es: "Información oculta de sensores", fr: "Aperçus de capteurs hors ligne", it: "Approfondimenti sensori offline", ja: "オフラインセンサーインサイト", nl: "Offline sensorinzichten", pl: "Ukryte wnioski z czujników", pt: "Informações de sensores offline").

* **REQ-SPT-005.2** (*Zero Decorative Emojis & Material 3 Vector Icons (ATT-5400)*):
  - All XML string resources in `strings_sporting_journey.xml` across all 9 locales SHALL strictly comply with ATT-5400 / REQ-UI-458.3, containing zero inline decorative Unicode emojis (`🌍`, `🏔️`, etc.).
  - In `SportingJourneyComponents.kt`, `MilestonesSection` and `AchievementRoomSection` SHALL replace raw unstyled emoji text (`Text(text = item.emoji)`) with styled Material 3 vector iconography using standard Compose `ImageVector`s (`Icons.Default.Terrain`, `Speed`, `Timer`, `Bolt`, `Public`, `Lock`, `EmojiEvents`, `Repeat`, `History`, `DirectionsBike`, `DirectionsRun`, `FitnessCenter`) inside tonal surfaces.

* **REQ-SPT-005.3** (*Row Layout Weighting & Anti-Wrapping Stability*):
  - In `HeroTotalsCard`, `HiddenGemsSection`, `GemCard`, `MilestonesSection`, `AchievementRoomSection`, and `AthletePassportShareCard`, all horizontal arrangement header rows (`Arrangement.SpaceBetween`) SHALL assign `Modifier.weight(1f)` to their title/content containers.
  - This ensures natural word wrapping and prevents trailing badges, metric chips, or action buttons from wrapping mid-word or being clipped off-screen across varying screen densities and languages.

* **REQ-SPT-005.4** (*Uniform Grid Layout for Achievement Room*):
  - In `AchievementRoomSection`, the broken `FlowRow` implementation SHALL be replaced by a deterministic chunked grid (`achievements.chunked(4)`).
  - Every card in each row SHALL occupy an equal `Modifier.weight(1f)`, and incomplete final rows SHALL be padded with `repeat(4 - rowBadges.size) { Spacer(Modifier.weight(1f)) }` to guarantee uniform card widths.
  - Achievement cards SHALL enforce uniform minimum height across the row.

* **REQ-SPT-005.5** (*Locale.getDefault() Formatting, Imperial Units & Localized Sharing*):
  - All numerical values (integers, floating-point numbers, percentages, hours) in UI cards and share generation SHALL be formatted using `Locale.getDefault()` instead of hardcoded `Locale.US`.
  - Distance and elevation formatting SHALL respect user preferences (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`), formatting distance in miles (`(totalDistanceKm * 1000.0) / BANALService.METER_PER_MILE`) and elevation in feet (`totalElevationM / BANALService.METER_PER_FOOT`).
  - In `SportingJourneyShareHelper`, share passport text SHALL format localized strings using Android string resources and `Locale.getDefault()`, complying with the active unit system.

---

## 2. Test Specification (TST-SPT-006)

| Test Spec ID | Target Requirement | Verification Procedure | Verification Target | Expected Result |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SPT-006.1` | `REQ-SPT-005.1`, `REQ-SPT-005.2` | XML regex scan across all 9 locale directories for decorative emojis, "Local Legend", "SQLite", and "Wrapped". | `strings_sporting_journey.xml` (all 9 locales) | 0 emojis detected (ATT-5400 regex); 0 occurrences of "Local Legend", "SQLite", or "Wrapped". |
| `TST-SPT-006.2` | `REQ-SPT-005.3`, `REQ-SPT-005.4` | Render chunked achievement grid with 1, 3, 5, and 8 badges; test layout weighting in header rows. | `SportingJourneyComponentsTest.kt` | All cards maintain identical width; incomplete rows padded with spacers; headers wrap without clipping trailing elements. |
| `TST-SPT-006.3` | `REQ-SPT-005.5` | Format hero totals & share text with `Locale.GERMANY`, `Locale.US`, `MyUnits.METRIC`, and `MyUnits.IMPERIAL`. | `SportingJourneyShareHelperTest.kt` | German uses `,` decimal separator; Imperial outputs `mi` and `ft` using `BANALService` conversion factors; share text matches localized templates. |

---

## 3. Traceability Matrix & Invariants

| Requirement ID | Test Spec ID | Verification Component | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SPT-005.1` | `TST-SPT-006.1` | `SportingJourneyLocalizationContractTest` | Specified |
| `REQ-SPT-005.2` | `TST-SPT-006.1`, `TST-SPT-006.2` | `SportingJourneyLocalizationContractTest`, `SportingJourneyComponentsTest` | Specified |
| `REQ-SPT-005.3` | `TST-SPT-006.2` | `SportingJourneyComponentsTest` | Specified |
| `REQ-SPT-005.4` | `TST-SPT-006.2` | `SportingJourneyComponentsTest` | Specified |
| `REQ-SPT-005.5` | `TST-SPT-006.3` | `SportingJourneyShareHelperTest` | Specified |

* **Chesterton's Fence Audit**: No requirement removals. `REQ-SPT-005` establishes formal UI/UX, accessibility, and localization contracts refining initial `REQ-SPT-003` and `REQ-SPT-004` prototypes.
* **System Invariants**: Zero SQLite schema modifications; exact physical distance, time, and elevation math preserved; 100% 9-locale string parity preserved.
