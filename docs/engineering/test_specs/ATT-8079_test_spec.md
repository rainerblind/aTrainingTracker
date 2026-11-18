# Stage 2: Requirement & Test Specification - ATT-8079: Persist Unchecked Detail Sections, Decouple Summary Toggles & Reserve Header Width

**Ticket**: [ATT-8079](https://atrainingtracker.atlassian.net/browse/ATT-8079) (`2 SP`) | **Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_02_Spät` (`3652c31c`)  
**Requirement Mapping**: `REQ-UI-2787` | **Test Spec ID**: `TST-UI-2787` | **Branch**: `feature/ATT-8079` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-UI-2787` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit` | **Localization Parity**: `tuning_summary_matrix_tabs_format` across all 9 locales (`values*/strings_workout_details.xml`)
* **Implementation**: `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutHeader.kt`, `WorkoutMasksAndCardsSection.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`
* **Functional & Architectural Requirements**:
  1. **`REQ-UI-2787.1` (`v3|` Schema Persistence & Pre-`v3` Migration)**: `WorkoutDetailTabConfig` SHALL bump `SCHEMA_PREFIX` from `"v2|"` to `"v3|"` and gate legacy auto-migrations (`GRADE_DISTRIBUTION`, `RPE_AND_FEEL`, `HRV`) strictly on `!raw.startsWith("v3|")` (alongside `isV1` for `PHOTOS`) so removed sections/tabs stay removed after `serializeTabs` $\rightarrow$ `deserializeTabs`, while all `"v1|"` and `"v2|"` legacy migrations continue passing (`WorkoutDetailTabsTest`).
  2. **`REQ-UI-2787.2` (Workout Details Summary-Toggle Decoupling)**: `WorkoutDetailTabbedContent` (`RenderWorkoutDetailCardSections`) SHALL pass `true` for `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, and `showCriticalPowerInWorkoutSummary` into `WorkoutDetails(...)` (matching `CardioPowerDynamicsCard`), while `WorkoutSummary.kt` retains `show*InWorkoutSummary` gating on compact list cards.
  3. **`REQ-UI-2787.3` (Dynamic `WorkoutHeader` Custom `actions()` Reservation)**: `WorkoutHeader` SHALL preserve Frame-0 `actionSpacerWidth` (`ATT-8097`) AND dynamically measure `actionButtonsContent()` width via `Modifier.onSizeChanged` (`maxOf`) so custom `actions()` content never collides with 2-line portrait titles.
  4. **`REQ-UI-2787.4` (Tab-Driven Settings Subtitle, Zone Gating & `< 400` LoC Ceiling)**: `WorkoutMasksAndCardsSection` SHALL gate Details `ZoneDisplayModeSelector` on `detailTabs` containing `ZONES` or `GRADE_DISTRIBUTION` rather than stale `workoutDetailPrefs.showZoneAnalysis`, remaining strictly `< 400` lines (`REQ-UI-414.5`, helper extraction if needed). `TuningSubtitleFormatter.formatWorkoutMatrixSubtitle` SHALL retain its 3-arg `(cardPrefs, detailPrefs, context)` overload for `AdvancedTuningAccordionTest` / `AdvancedTuningAftermathContractTest` compatibility and support `detailTabs` (`AdvancedTuningScreen`, `AdvancedTuningDialog`) via 9-locale `tuning_summary_matrix_tabs_format`.

### Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Persistent Removal of Sections & Tabs)**: *Given* default `v3` Workout Detail tabs, *When* the athlete unchecks `RPE_AND_FEEL` or `GRADE_DISTRIBUTION` or deletes `tab_hrv` in `WorkoutDetailTabsConfigSheet` and reopens Workout Details, *Then* removed sections/tabs stay absent after `serializeTabs` $\rightarrow$ `deserializeTabs` (while `"v1|"` / `"v2|"` legacy migrations still inject them once).
* **Criterion 2 (Summary Toggles Scoped to List Cards)**: *Given* `showCogganPowerInWorkoutSummary = false`, `showAerobicEfficiencyInWorkoutSummary = false`, and `showCriticalPowerInWorkoutSummary = false` (`enable* = true`), *When* viewing a workout with power/HR telemetry in Workout Details (`DETAILS`), *Then* Coggan, Aerobic Efficiency, and W'bal remain visible in Workout Details while hidden on compact `WorkoutSummary` list cards.
* **Criterion 3 (Header Reservation & Tab-Driven Settings)**: *Given* a 2-line portrait title with `onOpenTabsConfig` / custom `actions()` in `WorkoutHeader` and modified `detailTabs` in Expert Settings, *When* rendering `WorkoutHeader`, `WorkoutMasksAndCardsSection`, and `AdvancedTuningAccordion`, *Then* zero title/action overlap occurs, the subtitle reflects active `detailTabs`, `ZoneDisplayModeSelector` follows `ZONES`/`GRADE_DISTRIBUTION` tab presence, and `WorkoutMasksAndCardsSection.kt < 400` LoC.

### System Invariants
* Zero regression to `"v1|"`/`"v2|"` migration tests, `WorkoutSummary` summary card toggles, `ATT-8097` Frame-0 `WorkoutHeader` bounds, `formatWorkoutMatrixSubtitle` 3-arg reflection/JVM contract, `< 400` LoC in `WorkoutMasksAndCardsSection.kt`, `WorkoutRepository.kt` untouched, 9-locale parity, `0` `src/main` reads in tests, and `createComposeRule()` behavioral UI tests (`ATT-8198`).

---

## 2. Test Specification (`TST-UI-2787` `[Planned]`) & Traceability Matrix
All Compose UI tests strictly use Robolectric `createComposeRule()` (`0` `src/main` file reads per `SourceTextReadingAuditContractTest` & `UiReflectionOnlyTestAuditContractTest`).

| Test Case | Scope & Target File | Requirement | Verification & Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-UI-2787.1` | Unit (`WorkoutDetailTabsTest.kt`) | `REQ-UI-2787.1` | `serializeTabs` emits `"v3|"`; removed `RPE_AND_FEEL`/`GRADE_DISTRIBUTION`/`PHOTOS`/`tab_hrv` persist absent; legacy `v1`/`v2` migration tests pass |
| `TST-UI-2787.2` | Compose UI (`WorkoutDetailSummaryDecouplingTest.kt`) | `REQ-UI-2787.2` | `WorkoutDetailTabbedContent` displays NP/IF/TSS, EF, W'bal when `show*InWorkoutSummary = false` (`enable* = true`), while `WorkoutSummary` hides them |
| `TST-UI-2787.3` | Compose UI (`WorkoutDetailHeaderActionsComposeTest.kt`) | `REQ-UI-2787.3` | 2-line title + custom `actions()` at `360dp` satisfies `titleBounds.right <= actionsBounds.left`; `ATT-8097` Frame-0 tests & `Rule 8.5` PNG pass |
| `TST-UI-2787.4` | Compose UI, Unit & 9-Locale (`WorkoutMasksAndCardsLayoutTest.kt`, `AdvancedTuningAccordionTest.kt`, `TranslationParityTest.kt`) | `REQ-UI-2787.4` | `ZoneDisplayModeSelector` tracks `detailTabs` regardless of `showZoneAnalysis`; `formatWorkoutMatrixSubtitle` (`detailTabs` & 3-arg); `< 400` LoC; 9-locale parity |
