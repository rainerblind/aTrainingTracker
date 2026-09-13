# Implementation Plan: Reduce Number of 'Bestzeiten' in Workout Summary (ATT-883)

## 1. Executive Summary & Objective
The objective of **ATT-883** is to provide a streamlined, unit-aware, and collapsible presentation for Strava best efforts (`best_efforts`) in the Workout Summary card ([`StravaActivitySection.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt)):
1. **Prevent Summary Card Bloat**: Running workouts on Strava return best efforts across up to 12 benchmark intervals (400m, 1/2 mi, 1k, 1 mi, 2 mi, 5k, 10k, 15k, 10 mi, 20k, Half-Marathon, Marathon). Unconditionally rendering 7–11 rows pushes segment efforts, elevation profiles, and workout telemetry far off-screen.
2. **Promote Personal Records (PRs)**: Highlight genuine achievements (`prRank != null`) and reduce screen clutter from non-record standard intervals.
3. **Metric Unit Filtering (User Directive)**: When the athlete has selected metric units (`MyUnits.METRIC`), imperial mile intervals (*"1/2 mile"*, *"1 mile"*, *"2 miles"*, *"10 miles"*) are excluded from the collapsed/reduced view.
4. **Longest Distance Guarantee (User Directive)**: In the reduced view, the time of the **longest** distance (e.g. 10k for a 10k run) is guaranteed to be visible, ensuring the primary benchmark pace/time is immediately accessible even when no PR occurred.
5. **In-Place Expandable Accordion**: An intuitive toggle button allows full in-place expansion to inspect all intervals on demand, with expansion state preserved across scroll recycling (`rememberSaveable`).
6. **System Invariants**: Segment efforts, Strava branding guidelines (`PoweredByStrava`), and external activity links remain strictly intact.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
| :--- | :--- | :--- |
| **Requirement** | `REQ-UI-144` | Streamlined & Collapsible Strava Best Efforts Presentation in Workout Summary (`docs/requirements.md`) |
| **Test Specification** | `TST-UI-097` | Strava Best Efforts Collapsible Display, Metric Unit Filtering & Longest Distance Verification (`docs/tests.md`) |
| **Parent Ticket** | `ATT-883` | `[Verbesserung] Reduce the number of 'Bestzeiten' in the Workout Summary` (Epic: `ATT-597`) |
| **Analysis Sub-task** | `ATT-973` | `[Analysis] Reduce the number of 'Bestzeiten' in the Workout Summary` (`Erledigt` - Gate 1 Approved) |
| **Test Sub-task** | `ATT-974` | `[Test-Spec] Reduce the number of 'Bestzeiten' in the Workout Summary` (`Erledigt` - Gate 2 Approved) |
| **Plan Sub-task** | `ATT-975` | `[Impl-Plan] Reduce the number of 'Bestzeiten' in the Workout Summary` (`In Bearbeitung` - Stage 3) |
| **Target Version** | `V4.9.36` | Target Release Version |
| **Target Branch** | `feature/ATT-883` | Git Working Branch off `develop` |

---

## 3. Detailed Software Design & File Changes (SWE.2 / SWE.3)

### 3.1 Data Model Layer ([StravaActivityData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/StravaActivityData.kt))
* **Update `StravaBestEffort` Data Class**:
  Add optional `distanceMeters: Double = 0.0`:
  ```kotlin
  data class StravaBestEffort(
      val name: String,
      val elapsedTimeSec: Int,
      val prRank: Int?,
      val distanceMeters: Double = 0.0
  )
  ```
* **Provide Extension Helpers**:
  ```kotlin
  val StravaBestEffort.isHighlight: Boolean
      get() = prRank != null

  val StravaBestEffort.isMileEffort: Boolean
      get() = name.contains("mile", ignoreCase = true) || name.contains(" mi", ignoreCase = true)

  val StravaBestEffort.effectiveDistanceMeters: Double
      get() {
          if (distanceMeters > 0.0) return distanceMeters
          val lower = name.trim().lowercase()
          return when {
              lower == "400m" -> 400.0
              lower.contains("1/2") && lower.contains("mile") -> 804.672
              lower == "1k" -> 1000.0
              lower == "1 mile" || lower == "1 mi" -> 1609.344
              lower == "2 miles" || lower == "2 mi" -> 3218.688
              lower == "5k" -> 5000.0
              lower == "10k" -> 10000.0
              lower == "15k" -> 15000.0
              lower == "10 miles" || lower == "10 mi" -> 16093.44
              lower == "20k" -> 20000.0
              lower.contains("half") && lower.contains("marathon") -> 21097.5
              lower.contains("marathon") -> 42195.0
              else -> 0.0
          }
      }
  ```
* **Parser Enhancement (`StravaActivityParser.parse`)**:
  Extract `distance` and validate `pr_rank` from `achievements` if top-level `pr_rank` is null:
  ```kotlin
  val bestEfforts = mutableListOf<StravaBestEffort>()
  json.optJSONArray("best_efforts")?.let { array ->
      for (i in 0 until array.length()) {
          val item = array.getJSONObject(i)
          var prRank = if (item.has("pr_rank") && !item.isNull("pr_rank")) item.optInt("pr_rank") else null
          if (prRank == null) {
              val achievements = item.optJSONArray("achievements")
              if (achievements != null) {
                  for (j in 0 until achievements.length()) {
                      val ach = achievements.optJSONObject(j) ?: continue
                      if (ach.optString("type").equals("pr", ignoreCase = true)) {
                          val rank = ach.optInt("rank")
                          if (rank > 0) {
                              prRank = rank
                              break
                          }
                      }
                  }
              }
          }
          val dist = if (item.has("distance")) item.optDouble("distance", 0.0) else 0.0
          bestEfforts.add(
              StravaBestEffort(
                  name = item.optString("name"),
                  elapsedTimeSec = item.optInt("elapsed_time"),
                  prRank = prRank,
                  distanceMeters = dist
              )
          )
      }
  }
  ```

### 3.2 Presentation Layer ([StravaActivitySection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt))
* **Replace Unconditional Best Effort Rendering** (lines 104–120) with unit-filtered, longest-effort-guaranteed collapsible accordion:
  ```kotlin
  // --- Best Efforts (e.g. for runs) ---
  if (activity.bestEfforts.isNotEmpty()) {
      val prCount = activity.bestEfforts.count { it.prRank != null }
      val bestEffortsTitle = if (prCount > 0) {
          pluralStringResource(R.plurals.strava_best_efforts_with_prs_format, prCount, prCount)
      } else {
          stringResource(R.string.strava_best_efforts)
      }
      Text(
          text = bestEffortsTitle,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary
      )

      val isMetric = remember {
          try {
              TrainingApplication.getUnit() == MyUnits.METRIC
          } catch (e: Exception) {
              true
          }
      }

      val totalBestEfforts = activity.bestEfforts.size
      val eligibleEfforts = remember(activity.bestEfforts, isMetric) {
          if (isMetric) {
              val nonMiles = activity.bestEfforts.filter { !it.isMileEffort }
              if (nonMiles.isNotEmpty()) nonMiles else activity.bestEfforts
          } else {
              activity.bestEfforts
          }
      }

      val longestEffort = remember(eligibleEfforts) {
          eligibleEfforts.maxByOrNull { it.effectiveDistanceMeters } ?: eligibleEfforts.lastOrNull()
      }

      val reducedBestEfforts = remember(eligibleEfforts, longestEffort) {
          eligibleEfforts.filter { effort ->
              effort.isHighlight || effort == longestEffort
          }
      }

      val isCollapsible = totalBestEfforts > reducedBestEfforts.size
      var isExpanded by rememberSaveable { mutableStateOf(false) }

      val displayedBestEfforts = if (!isCollapsible || isExpanded) {
          activity.bestEfforts
      } else {
          reducedBestEfforts
      }

      displayedBestEfforts.forEach { effort ->
          BestEffortRow(effort)
      }

      if (isCollapsible) {
          val hiddenCount = totalBestEfforts - displayedBestEfforts.size
          val buttonText = if (!isExpanded) {
              stringResource(R.string.strava_show_all_best_efforts_format, totalBestEfforts, hiddenCount)
          } else {
              stringResource(R.string.strava_show_less_best_efforts_format, reducedBestEfforts.size)
          }

          TextButton(
              onClick = { isExpanded = !isExpanded },
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 2.dp)
          ) {
              Text(
                  text = buttonText,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold
              )
          }
      }
  }
  ```

### 3.3 Localization Layer ([strings.xml across 9 Locales](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/))
Add 3 localized string resources across all 9 supported locales:

```xml
<!-- Default (EN) in res/values/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Show all %1$d best efforts (+%2$d more)</string>
<string name="strava_show_less_best_efforts_format">▲ Show less (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Show less</string>

<!-- German (DE) in res/values-de/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Alle %1$d Bestzeiten anzeigen (+%2$d weitere)</string>
<string name="strava_show_less_best_efforts_format">▲ Weniger anzeigen (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Weniger anzeigen</string>

<!-- Spanish (ES) in res/values-es/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Mostrar los %1$d mejores tiempos (+%2$d más)</string>
<string name="strava_show_less_best_efforts_format">▲ Mostrar menos (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Mostrar menos</string>

<!-- French (FR) in res/values-fr/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Afficher les %1$d meilleurs temps (+%2$d de plus)</string>
<string name="strava_show_less_best_efforts_format">▲ Afficher moins (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Afficher moins</string>

<!-- Italian (IT) in res/values-it/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Mostra tutti i %1$d migliori tempi (+%2$d altri)</string>
<string name="strava_show_less_best_efforts_format">▲ Mostra meno (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Mostra meno</string>

<!-- Japanese (JA) in res/values-ja/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ すべての%1$d件の自己ベストを表示（他%2$d件）</string>
<string name="strava_show_less_best_efforts_format">▲ 折りたたむ（%1$d）</string>
<string name="strava_show_less_best_efforts">▲ 折りたたむ</string>

<!-- Dutch (NL) in res/values-nl/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Toon alle %1$d beste prestaties (+%2$d meer)</string>
<string name="strava_show_less_best_efforts_format">▲ Minder tonen (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Minder tonen</string>

<!-- Polish (PL) in res/values-pl/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Pokaż wszystkie %1$d najlepsze czasy (+%2$d więcej)</string>
<string name="strava_show_less_best_efforts_format">▲ Pokaż mniej (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Pokaż mniej</string>

<!-- Portuguese (PT) in res/values-pt/strings.xml -->
<string name="strava_show_all_best_efforts_format">▼ Mostrar todos os %1$d melhores tempos (+%2$d mais)</string>
<string name="strava_show_less_best_efforts_format">▲ Mostrar menos (%1$d)</string>
<string name="strava_show_less_best_efforts">▲ Mostrar menos</string>
```

---

## 4. System Invariants & Risk Assessment

### 4.1 Invariants
1. **Segment Efforts Independence**: Segment efforts rendering, starred segment queries from `SegmentsDatabaseManager`, and segment accordion logic remain 100% untouched.
2. **Strava Branding Invariant**: The mandatory `PoweredByStrava` logo and attribution guidelines remain anchored at the bottom of the section.
3. **Data Parser Compatibility**: In `StravaActivityParser`, activities lacking `distance` or `best_efforts` parse safely without exceptions.
4. **UI Performance**: `remember` and `rememberSaveable` isolate list reductions to composition without allocating objects during re-renders.

### 4.2 Risk Mitigation
* **Unmocked SharedPreferences in Unit Tests**: `TrainingApplication.getUnit()` is wrapped in a defensive `try-catch` defaulting to `MyUnits.METRIC`, preventing `NullPointerException` in JVM test suites.
* **Fallback for Pure Mile Workouts in Metric Mode**: If an activity solely contains mile intervals (e.g., historical file with only 1 mile), `eligibleEfforts` falls back to `activity.bestEfforts` to guarantee the section is never empty.

---

## 5. Verification & Testing Plan

### 5.1 Unit Tests ([StravaActivitySectionTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySectionTest.kt))
Implement the full suite of unit tests for `TST-UI-097`:
1. `testBestEffortHighlightClassification()`: Verify `prRank = 1, 2, 3` are highlights and `prRank = null` is not.
2. `testBestEffortMileClassification()`: Verify that "1/2 mile", "1 mile", "2 miles", "10 miles" are classified as mile efforts.
3. `testEffectiveDistanceCalculation()`: Verify parsing from distance field and name-based mapping.
4. `testMetricFilteringExcludesMilesInCollapsedView()`: Verify that under Metric mode, mile efforts are excluded from the reduced view.
5. `testLongestEffortAlwaysIncludedWithZeroPRs()`: Verify that in a 10k run with 0 PRs, the 10k time is displayed.
6. `testIntermediatePRAndLongestEffortIncluded()`: Verify that in a 10k run with 1k PR, both 1k and 10k are displayed.
7. `testLongestEffortDeduplicatedWhenPR()`: Verify that when 5k is a PR in a 5k run, it appears exactly once.
8. `testCollapsibleAccordionEligibilityAndExpansion()`: Verify that when total > reduced, accordion expands in-place to all efforts.

### 5.2 Translation Parity ([TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt))
Execute `TranslationParityTest` to guarantee all 3 new string resources have identical format specifiers across all 9 locales.

### 5.3 Full Clean-Room Regression
Run `./gradlew testDebugUnitTest` to guarantee 100% test pass rate across the entire repository.
