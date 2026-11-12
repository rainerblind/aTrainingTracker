# Stage 3 Implementation Plan: SportingJourney UI & UX Redesign, M3 Hierarchy, Grid Layout & Locale Formatting (ATT-7323)

## 1. Architecture & Layering Overview (SWE.2)
* **Resource & Localization Layer**:
  - `app/src/main/res/values*/strings_sporting_journey.xml` (all 9 supported locales: `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`):
    - Replace Strava trademark terminology "Local Legend" with authentic athletic terminology "Route Master" (and local translations).
    - Replace prototype phrasing "Your %1$d Season Wrapped" with "Your %1$d Season Retrospective".
    - Replace SQLite developer jargon "Hidden Gems from SQLite" with "Offline Sensor Insights" (and local translations).
    - Strip all decorative inline Unicode emojis (`🌍`, `🏔️`, etc.) from XML strings per `ATT-5400` / `REQ-UI-458.3`.
    - Add localized string resource keys for Athlete Passport sharing.
* **UI & Presentation Layer**:
  - `SportingJourneyComponents.kt`:
    - **Row Anti-Wrapping**: In `HeroTotalsCard`, `HiddenGemsSection`, `GemCard`, `MilestonesSection`, `AchievementRoomSection`, and `AthletePassportShareCard`, assign `Modifier.weight(1f)` to title/content containers in horizontal `Arrangement.SpaceBetween` rows so long text wraps cleanly at word boundaries without clipping trailing elements.
    - **Uniform Achievement Grid**: Replace broken `FlowRow` with a chunked grid (`achievements.chunked(4)`). Wrap each chunk in a `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))` where each item has `Modifier.weight(1f)` and incomplete rows are padded with `repeat(4 - rowBadges.size) { Spacer(Modifier.weight(1f)) }`. Enforce uniform minimum height across cards.
    - **Material 3 Vector Iconography**: Replace raw emoji text in `MilestonesSection` and `AchievementRoomSection` with Material 3 vector icons (`Icons.Default.Terrain`, `Speed`, `Timer`, `Bolt`, `Public`, `Lock`, `EmojiEvents`, `Repeat`, `History`, `DirectionsBike`, `DirectionsRun`) within styled tonal surfaces.
    - **Locale & Unit Compliance**: Format numbers with `Locale.getDefault()`. Respect user preferences (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`), converting distance to miles and elevation to feet using `BANALService.METER_PER_MILE` and `BANALService.METER_PER_FOOT`.
  - `SportingJourneySheet.kt`:
    - Update KDoc to eliminate developer jargon ("SQLite Hidden Gems" -> "Hidden Telemetry Gems").
* **Share Helper Layer**:
  - `SportingJourneyShareHelper.kt`:
    - Localize share text using `context.getString(...)`.
    - Apply `Locale.getDefault()` for numbers and dates.
    - Comply with `MyUnits.IMPERIAL` for distance (`mi`) and elevation (`ft`).
    - Eliminate ad-hoc Unicode block dividers and decorative emojis.
* **Verification & Test Layer**:
  - `SportingJourneyLocalizationContractTest.kt`: Verify all 9 locales have zero emojis, zero Strava jargon, zero SQLite jargon.
  - `SportingJourneyComponentsTest.kt`: Verify chunked grid spacer padding and layout weighting.
  - `SportingJourneyShareHelperTest.kt`: Verify `Locale.getDefault()`, Imperial unit conversion, and localized share passport text.

---

## 2. Atomic Implementation Steps

| Step | Scope | Target File | Action & Invariants | Verification Command |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Resources | `strings_sporting_journey.xml` (9 locales) | Terminology cleanup ("Route Master", "Season Retrospective", "Offline Sensor Insights"), strip emojis (ATT-5400), add share string keys. | `python3 tools/parallel_sprint_lock.py ./gradlew compileDebugUnitTestSources` |
| **2** | Components | `SportingJourneyComponents.kt`, `SportingJourneySheet.kt` | Implement `Modifier.weight(1f)` anti-wrapping, chunked 4-column grid with spacer padding, M3 vector icons, and `Locale.getDefault()` / imperial unit formatting. | `python3 tools/parallel_sprint_lock.py ./gradlew compileDebugKotlin` |
| **3** | Share Helper | `SportingJourneyShareHelper.kt` | Localize share text via Android string resources, `Locale.getDefault()`, and imperial unit conversions. | `python3 tools/parallel_sprint_lock.py ./gradlew compileDebugKotlin` |
| **4** | Contract & Unit Tests | `SportingJourneyLocalizationContractTest.kt`, `SportingJourneyComponentsTest.kt`, `SportingJourneyShareHelperTest.kt` | Add `TST-SPT-006.1..006.3` tests verifying 9-locale parity, zero-emoji compliance, grid layout spacer padding, and imperial formatting. | `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "*SportingJourney*"` |

---

## 3. UI Consistency & Invariants
* **Design Token Compliance**: Reuses existing `TTColor.VaultAmber`, `TTColor.VaultCardBackground`, `TTColor.VaultCardBorder`, and standard Material 3 color schemes (`surface`, `surfaceVariant`, `outlineVariant`, `onSurface`, `onSurfaceVariant`).
* **System Invariants**: Zero SQLite schema changes; no database migrations; workout drill-down callbacks (`onNavigateToWorkout`, `WorkoutNavigationEvents`) preserved; 100% 9-language localization parity maintained.
