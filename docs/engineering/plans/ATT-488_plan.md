# Implementation Plan - ATT-488: Message for Empty Periods

## Problem Statement & Objective
In the Periods module (`PeriodsTabsScreen.kt` / `PeriodList.kt`), when a user opens the screen with no recorded workouts in the database or applies a sport type filter yielding zero workouts:
1. `PeriodBarGraph` returns immediately and renders nothing (`PeriodsTabsScreen.kt:213`).
2. `PeriodList.kt` receives an empty list (`periods.isEmpty()`) and unconditionally renders an empty `LazyColumn`, presenting an uninformative completely blank screen below the header and tabs.

This contrasts with all other list screens in the application (`WorkoutList.kt`, `RouteList.kt`, `SegmentList.kt`, `WorkoutClustersList.kt`), which display a standardized, centered `EmptyStatePlaceholder` with a contextual icon and clear user guidance.

The objective of **ATT-488** is to integrate `EmptyStatePlaceholder` into `PeriodList.kt` when `periods.isEmpty()`, provide full 9-language localization parity, and maintain 100% layout and scrolling invariants for non-empty lists.

---

## User Review Required
> [!IMPORTANT]
> - **Zero Disruption to Existing Lists**: When `periods.isNotEmpty()`, the existing `LazyColumn`, card click listeners (`onHeaderClick`, `onMapClick`, `onSportClick`, `onLongestWorkoutClick`), scroll state synchronization, and item keys remain 100% untouched.
> - **Collapsible Header & Inset Alignment**: The empty state placeholder inherits the container's layout constraints (`modifier.fillMaxWidth().weight(1f)`), respects bottom navigation bar insets, and is visually centered within the visible viewport below the collapsible header tabs.
> - **Localization Parity**: Full translations provided across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) conforming to Java Formatter standards.

---

## Proposed Changes

### UI Component Layer

#### [MODIFY] [PeriodList.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodList.kt)
- Import `androidx.compose.material.icons.Icons` and `androidx.compose.material.icons.filled.DateRange`.
- Import `androidx.compose.ui.res.stringResource` and `com.atrainingtracker.R`.
- Import `com.atrainingtracker.trainingtracker.ui.components.EmptyStatePlaceholder`.
- In `PeriodList`:
  ```kotlin
  val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

  if (periods.isEmpty()) {
      EmptyStatePlaceholder(
          modifier = modifier.padding(bottom = bottomPadding),
          icon = Icons.Default.DateRange,
          message = stringResource(R.string.no_periods_available),
          hint = stringResource(R.string.no_periods_available_hint)
      )
  } else {
      LazyColumn(
          state = scrollState,
          modifier = modifier,
          contentPadding = PaddingValues(
              top = 8.dp,
              bottom = bottomPadding + 16.dp,
              start = 8.dp,
              end = 8.dp
          ),
          verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
          items(
              items = periods,
              key = { "${it.periodType.name}_${it.startTimestampS}" }
          ) { periodSummary ->
              PeriodSummaryCard(...)
          }
      }
  }
  ```

---

### Localization Layer (9 Locales)

#### [MODIFY] [values/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml)
- `no_periods_available`: "No period summaries available"
- `no_periods_available_hint`: "Record workouts or adjust sport filters to view period summaries"

#### [MODIFY] [values-de/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-de/strings.xml)
- `no_periods_available`: "Keine Zeiträume verfügbar"
- `no_periods_available_hint`: "Zeichne Einheiten auf oder passe Sportfilter an, um Zeiträume anzuzeigen"

#### [MODIFY] [values-es/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-es/strings.xml)
- `no_periods_available`: "No hay resúmenes de períodos disponibles"
- `no_periods_available_hint`: "Graba entrenamientos o ajusta los filtros de deporte para ver resúmenes"

#### [MODIFY] [values-fr/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-fr/strings.xml)
- `no_periods_available`: "Aucun résumé de période disponible"
- `no_periods_available_hint`: "Enregistrez des séances ou ajustez les filtres pour voir les résumés"

#### [MODIFY] [values-it/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-it/strings.xml)
- `no_periods_available`: "Nessun riepilogo del periodo disponibile"
- `no_periods_available_hint`: "Registra allenamenti o regola i filtri sport per visualizzare i riepiloghi"

#### [MODIFY] [values-ja/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-ja/strings.xml)
- `no_periods_available`: "利用可能な期間の概要はありません"
- `no_periods_available_hint`: "ワークアウトを記録するかフィルターを調整して概要を表示します"

#### [MODIFY] [values-nl/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-nl/strings.xml)
- `no_periods_available`: "Geen periodeoverzichten beschikbaar"
- `no_periods_available_hint`: "Registreer workouts of pas sportfilters aan om overzichten te zien"

#### [MODIFY] [values-pl/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-pl/strings.xml)
- `no_periods_available`: "Brak podsumowań okresów"
- `no_periods_available_hint`: "Zarejestruj treningi lub dostosuj filtry sportowe, aby wyświetlić podsumowania"

#### [MODIFY] [values-pt/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-pt/strings.xml)
- `no_periods_available`: "Nenhum resumo de período disponível"
- `no_periods_available_hint`: "Registe treinos ou ajuste os filtros de desporto para ver resumos"

---

## Call Sites & Interface Audit
- `PeriodList` signature remains:
  `fun PeriodList(scrollState: LazyListState, periods: List<PeriodSummary>, isPlayServiceAvailable: Boolean, onHeaderClick: (PeriodSummary) -> Unit, onMapClick: (PeriodSummary) -> Unit, onSportClick: (PeriodSummary, BSportType) -> Unit, onLongestWorkoutClick: (PeriodSummary, BSportType, Long) -> Unit, modifier: Modifier = Modifier)`
- Sole caller: `PeriodsTabsScreen.kt:153` passes `modifier = Modifier.fillMaxWidth().weight(1f)`.
- Reused component: `EmptyStatePlaceholder.kt` requires no changes.

---

## Traceability Matrix
| Element | Reference | Status |
| :--- | :--- | :--- |
| **Main Ticket** | `ATT-488: [Verbesserung] Message for empty periods` | `In Bearbeitung` |
| **Requirement** | `REQ-UI-125: Period Summaries Empty State Presentation` | `Proposed` |
| **Test Specification** | `TST-PER-017: Period Empty State Placeholder Verification` | `Proposed` |
| **Test Sub-task** | `ATT-662` | `Zu erledigen` |
| **Analysis Sub-task** | `ATT-663` | `Erledigt` (Gate 1 Approved) |
| **Plan Sub-task** | `ATT-664` | `In Bearbeitung` (Stage 2) |

---

## Verification Plan
### Automated Verification
- Execute full test suite:
  `./gradlew testDebugUnitTest`
  Confirm 100% pass rate with zero regressions.

### Manual Verification (Connected Pixel 10 Device)
1. Install debug build on device: `./gradlew installDebug`.
2. Open app, navigate to "Periods".
3. Filter by a sport with no recorded activities (or clear data).
4. Verify `EmptyStatePlaceholder` appears centered below tabs with `DateRange` icon, headline, and hint.
5. Switch between "Woche", "Monat", "Jahr" tabs and verify smooth transition without clipping.
6. Switch back to an unfiltered state or sport with workouts, verify standard list and bar graph appear normally.
