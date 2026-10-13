# Stage 3: Architecture & Implementation Plan - ATT-2305: Optimize Layout, Text Wrapping and Reordering Controls in Workout Cards & Details Settings

**Ticket**: [ATT-2305](https://atrainingtracker.atlassian.net/browse/ATT-2305)  
**Sub-task**: [ATT-2330](https://atrainingtracker.atlassian.net/browse/ATT-2330) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization*)  
**Test Mapping**: `TST-UI-223` (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Verification*)  
**Branch**: `feature/ATT-2305`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

In `WorkoutMasksAndCardsSection.kt`, the section title column was constrained to ~112dp due to 64dp reorder buttons and two 64dp checkbox columns, causing mid-word line wraps on German compound words ("Runden-Übersich / t", "Telemetrie-Diagr / amme", "Zonenauswertun / g"). Furthermore, injecting the Lap Display Mode segmented button directly inside the reorderable row broke table alignment and row height rhythm.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization*)
* **Test Mapping**: `TST-UI-223` (`TST-UI-223.1` to `TST-UI-223.4`)
* **Living Documentation**: `docs/requirements.md` (`REQ-UI-264`), `docs/tests.md` (`TST-UI-223`).

---

## 3. System Invariants & Preserved Behavior

1. **Strict File Size Limit (`REQ-UI-262`)**: `WorkoutMasksAndCardsSection.kt` must remain strictly under 400 lines of code.
2. **Preserved Preference & Order Binding**: `workoutCardPrefs`, `workoutDetailPrefs`, and `workoutSectionsOrder` state hoisting contracts must remain 100% intact.
3. **Preserved Swap Mechanics**: `moveSection(index, targetIndex)` logic and boundary checks must operate identically.
4. **9-Language Localization Parity**: All strings in `strings.xml` must exist across all 9 locales.
5. **Mandatory Programmatic Pre-Check**: Before Stage 4 code edits, `python3 tools/jira_util.py check-gate ATT-2330` must exit with code 0 (`GATE_PASSED: ATT-2330 is Erledigt`).

---

## 4. Proposed Architectural & UI Changes (SWE.2)

### Target Component: `WorkoutMasksAndCardsSection.kt`

```
+----+--------------------------------+--------+---------+
|    | Bereich                        | In L.  | In Det. | (Header: Spacer 32dp, Col 50dp, Col 50dp)
+----+--------------------------------+--------+---------+
| [^]| Runden-Übersicht               |  [X]   |   [X]   | (Row: Up/Down 32dp, Text weight 1f, 50dp, 50dp)
| [v]|                                |        |         | (Clean 1-line or 2-line wrap, no mid-word break)
+----+--------------------------------+--------+---------+
| [^]| Telemetrie-Diagramme           |  [X]   |   [X]   |
| [v]|                                |        |         |
+----+--------------------------------+--------+---------+
... (All 8 rows have uniform height and rhythm)
+--------------------------------------------------------+
| Runden-Anzeigemodus (Clean decoupled footer card)      |
| [  Tabelle  |  Visualizer  ]                           |
+--------------------------------------------------------+
```

#### Detailed Modifications:
1. **Vertical Reorder Controls Column**:
   - Refactor `Row(modifier = Modifier.width(64.dp))` to:
     ```kotlin
     Column(
         modifier = Modifier.width(32.dp),
         horizontalAlignment = Alignment.CenterHorizontally,
         verticalArrangement = Arrangement.Center
     ) {
         IconButton(onClick = { moveSection(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(22.dp)) {
             Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up), modifier = Modifier.size(18.dp))
         }
         IconButton(onClick = { moveSection(index, index + 1) }, enabled = index < features.size - 1, modifier = Modifier.size(22.dp)) {
             Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down), modifier = Modifier.size(18.dp))
         }
     }
     ```
2. **Checkbox Column Widths**:
   - Change `Box(modifier = Modifier.width(64.dp))` to `Box(modifier = Modifier.width(50.dp))` in header.
   - Change `Box(modifier = Modifier.size(64.dp, 48.dp))` to `Box(modifier = Modifier.size(50.dp, 44.dp))` in rows.
3. **Header Alignment**:
   - Change leading `Spacer(modifier = Modifier.width(64.dp))` to `Spacer(modifier = Modifier.width(32.dp))` matching the reorder column.
4. **Decoupled Lap Display Mode Sub-Control**:
   - Remove `if (feature.isLaps)` from inside the row mapping and row composable.
   - Render the `LapDisplayMode` segmented button section immediately following the 8-feature loop whenever `workoutCardPrefs.showLaps || workoutDetailPrefs.showLaps`.
5. **Typography & Padding**:
   - Configure title `Text`:
     `style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp)`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, `modifier = Modifier.weight(1f).padding(horizontal = 4.dp)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 sign-off via:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2330
  ```

### Step 2: Refactor `WorkoutMasksAndCardsSection.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt`
* Refactor header row spacer to 32dp and checkbox header boxes to 50dp.
* Refactor row reorder buttons to 32dp vertical `Column`.
* Refactor row checkbox containers to 50dp width.
* Decouple Lap Display Mode segmented button to dedicated footer section.
* Ensure line count remains strictly $< 400$ lines.

### Step 3: Author Layout & Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* Verify reorder width (32.dp), checkbox column widths (50.dp), spacer width (32.dp), and decoupled lap section.

### Step 4: Run Targeted Unit Tests & Verification
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
  ```

### Step 5: Clean-Room Regression Verification (Stage 5)
* Execute full test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Deploy debug APK onto physical test device (Pixel 10):
  ```bash
  ./gradlew installDebug
  ```
* Author walkthrough deliverable `docs/engineering/walkthroughs/ATT-2305_walkthrough.md`.
* Update living documentation `docs/requirements.md` (`REQ-UI-264`) and `docs/tests.md` (`TST-UI-223`) to `Verified`.
