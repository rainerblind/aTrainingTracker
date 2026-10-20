# Stage 3: Implementation Plan - ATT-4286: Refine Cockpit section dividers: replace minus-like icon, increase divider prominence, and center section title

**Ticket**: [[ATT-4286]](https://rainerblind.atlassian.net/browse/ATT-4286)  
**Sub-task**: [[ATT-4337]](https://rainerblind.atlassian.net/browse/ATT-4337) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*, sub-clauses `REQ-UI-411.4`, `REQ-UI-411.5`)  
**Test Mapping**: `TST-UI-386` (`TST-UI-386.1`..`TST-UI-386.5`)  
**Branch**: `feature/ATT-4286`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In ticket `ATT-4055` (Sprint `2026-42.10`), configurable horizontal section dividers with optional mini-titles (`REQ-UI-411`) were introduced to the Cockpit (`SensorGridScreen.kt`). This feature enables athletes to partition dense 5–7 row sensor grids into visually distinct logical clusters (such as Primary Telemetry, Power & Cadence, Heart Rate, and Elevation).

During real-world on-device testing on a Google Pixel 10 during the Sprint Review of `ATT-4055` (captured in `docs/attachments/cockpit_divider_review.png`), three usability and visual design issues were observed:
1. **Misleading '-' Icon in Configuration Mode**:
   In `SensorGridScreen.kt` (lines 472–480), between rows in `ScreenMode.CONFIGURATION`, the button to add a section divider uses `Icons.Default.HorizontalRule` (`—`) immediately adjacent to the `RowAdder` button which uses `Icons.Default.Add` (`+`). Visually, a horizontal stroke `—` placed right next to `+` reads unmistakably as a minus / delete button (`+ / -`). Athletes naturally interpret this pair as "Add Row" and "Delete Row". Tapping `—` does not delete a row, but unexpectedly opens the "Add section divider" dialog.
2. **Low Visual Prominence of Dividers**:
   In `CockpitSectionDivider` (`SensorGridScreen.kt` lines 1184–1190), an unlabeled divider is rendered as a thin `1.5.dp` stroke with `MaterialTheme.colorScheme.outlineVariant`. In the Cockpit grid, every sensor cell already has an outer border. Consequently, an unlabeled divider between two bordered cell rows looks like an accidental double hairline border or a rendering glitch with whitespace padding rather than an authoritative, high-contrast semantic section separator. Under direct sunlight while riding, this distinction is lost.
3. **Off-Center Section Title**:
   In `CockpitSectionDivider` (lines 1199–1217), when a section divider includes a title (e.g. `PULS`, `HÖHE`), the layout uses asymmetric flanking weights: `Modifier.weight(0.12f)` on the left and `Modifier.weight(0.88f)` on the right. This pushes the uppercase title heavily to the left (~12% across the screen), creating an unbalanced, lopsided presentation across the cockpit width instead of a clean, horizontally centered section header.

Under **Governance Rule 49** and **Design Guidelines Sections 5.5 and 5.11**, `HorizontalRule` (`—`) must never be used as an insert action next to `+`, dividers must possess prominent contrast (`thickness >= 2.dp`), and titled section dividers must have horizontally centered titles with symmetric flanking rules.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*)
  * `REQ-UI-411.4` (*Glanceable Cockpit Rendering, Prominence & Centered Alignment*):
    * Unlabeled section dividers SHALL render with a prominent stroke thickness of `2.5.dp` and color token `MaterialTheme.colorScheme.outline`, maintaining compact vertical padding (`padding(vertical = 4.dp)`) so total height remains $\le 10\text{ dp}$.
    * Titled section dividers SHALL render the uppercase title horizontally centered across the full row width, flanked symmetrically on both left and right by `HorizontalDivider` segments with equal `Modifier.weight(1f)`.
    * Flanking divider lines SHALL use `thickness = 2.dp` and `color = MaterialTheme.colorScheme.outline`.
    * Asymmetric flanking weights (specifically `0.12f` and `0.88f`) are **STRICTLY FORBIDDEN**.
    * Maximum container height ceiling (`heightIn(max = 20.dp)`), title typography (`labelSmall`, `FontWeight.Bold`, `primary` color), and single-line ellipsis truncation (`maxLines = 1`, `TextOverflow.Ellipsis`) SHALL remain strictly enforced.
  * `REQ-UI-411.5` (*Configuration Mode WYSIWYG Controls & Iconography Safety*):
    * In `ScreenMode.CONFIGURATION`, when `existingDivider == null`, the divider action button adjacent to `RowAdder` (`Icons.Default.Add`) SHALL display `Icons.Default.HorizontalSplit` with tint `MaterialTheme.colorScheme.outline`.
    * The use of `Icons.Default.HorizontalRule` (`—`) in `SensorGridScreen.kt` is **STRICTLY FORBIDDEN**.
    * When `existingDivider != null`, the action button SHALL display `Icons.Default.Title` with tint `MaterialTheme.colorScheme.primary` and content description `stringResource(R.string.cockpit_section_divider_edit)`.
    * Accessibility content descriptions (`R.string.cockpit_section_divider_add` and `R.string.cockpit_section_divider_edit`) and click behavior (`editingDividerRow = rowNr`) SHALL remain unchanged.

* **Test Mapping**:
  * `TST-UI-386.1`: `SensorGridScreenLayoutTest.testCockpitSectionDivider_unlabeledProminenceAndContrast` — verifies unlabeled divider stroke thickness $\ge 2.5\text{ dp}$, `outline` color, and compact padding.
  * `TST-UI-386.2`: `SensorGridScreenLayoutTest.testCockpitSectionDivider_labeledCenteredTitleSymmetricWeights` — verifies symmetric `weight(1f)` flanking lines, `2.dp` thickness, `outline` color, and absence of `0.12f`/`0.88f` weights.
  * `TST-UI-386.3`: `SensorGridScreenLayoutTest.testConfigurationMode_dividerButtonUsesHorizontalSplitAndExcludesHorizontalRule` — verifies `HorizontalSplit` icon usage and strict prohibition of `HorizontalRule`.
  * `TST-UI-386.4`: `SensorGridLocalizationTest.testCockpitSectionDividerStringsParity` — verifies 9-locale parity for all divider string keys with zero missing entries.
  * `TST-UI-386.5`: Clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Vertical Space Preservation Invariant**:
   * Section dividers must not crowd out live telemetry tiles or the map canvas. Unlabeled dividers consume $\le 10.5\text{ dp}$ total height (`2.5.dp` stroke + `8.dp` vertical padding), and labeled dividers strictly observe `heightIn(max = 20.dp)`.
2. **Icon Semantics Invariant (Rule 49 & Design Guidelines Sec 5.5)**:
   * Never use `HorizontalRule` (`—`) as an insert action next to `+`. `HorizontalSplit` clearly represents dividing/splitting a section without false minus/delete affordances.
3. **Database Schema & Data Persistence Invariance**:
   * SQLite database schema version for `FlexibleGenericView.db` remains strictly at `DB_VERSION = 14`.
   * `SectionDividersTable` schema, `TrackingViewsDatabaseManager` CRUD and row-shift methods (`REQ-UI-411.2`), and `TrackingViewsRepository` Flow emissions (`REQ-UI-411.3`) remain 100% unaltered.
4. **9-Language Parity & String Resource Integrity**:
   * All existing localized strings across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) are preserved. Zero new string keys required.
5. **Thread Safety & Dispatcher Affinity**:
   * Composable layout functions execute exclusively on `Dispatchers.Main` inside the Jetpack Compose rendering pipeline.
   * State mutations (`editingDividerRow`) remain encapsulated within local Compose state.
6. **Subtask Direct Completion & Synchronous Parent Stage Progression (Rule 3 / Rule 45)**:
   * Subtask `ATT-4337` transitions autonomously to `Erledigt` via transition `freigabe` upon passing Gate 3 audit.
   * Moving parent ticket `ATT-4286` to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user in `Final Review (Human)`.
7. **Strict Code Lock in Stage 3 (Rule 4)**:
   * Zero production source code modifications occur during Stage 3 planning. Production code construction is strictly deferred to Stage 4 following Gate 3 approval (`check-gate`).
8. **Single Atomic Commit Invariant (Rule 44)**:
   * Zero intermediate git commits during Stage 1–4; all deliverables, code changes, and unit tests are integrated in a single atomic commit at the end of Stage 5.

---

## 4. Proposed Architectural Changes

### Architectural Decomposition (SWE.2)
* **UI Layer (`SensorGridScreen.kt`)**:
  - Update `CockpitSectionDivider` composable layout:
    - Unlabeled divider: `thickness = 2.5.dp`, `color = MaterialTheme.colorScheme.outline`, `padding(vertical = 4.dp)`.
    - Labeled divider: Left `HorizontalDivider(modifier = Modifier.weight(1f), thickness = 2.dp, color = MaterialTheme.colorScheme.outline)`, Text centered with `padding(horizontal = 6.dp)`, Right `HorizontalDivider(modifier = Modifier.weight(1f), thickness = 2.dp, color = MaterialTheme.colorScheme.outline)`.
  - Update Configuration Mode inter-row controls:
    - Replace `imageVector = if (existingDivider != null) Icons.Default.Title else Icons.Default.HorizontalRule` with `imageVector = if (existingDivider != null) Icons.Default.Title else Icons.Default.HorizontalSplit`.
    - Update import statements: import `androidx.compose.material.icons.filled.HorizontalSplit`, remove unused `androidx.compose.material.icons.filled.HorizontalRule`.
* **Testing Layer (`SensorGridScreenLayoutTest.kt`)**:
  - Add architectural and layout contract tests verifying `HorizontalSplit` adoption, `HorizontalRule` prohibition, symmetric flanking weights (`1f` / `1f`), and divider stroke prominence.
* **ViewModel, Repository, Database, and Service Layers**:
  - Strictly untouched (Zero change).

### Component 1: `SensorGridScreen.kt` (UI Presentation)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  1. Replace import:
     ```kotlin
     // Remove:
     import androidx.compose.material.icons.filled.HorizontalRule
     // Add:
     import androidx.compose.material.icons.filled.HorizontalSplit
     ```
  2. In `ScreenMode.CONFIGURATION` inter-row divider button (around line 474):
     ```kotlin
     IconButton(onClick = { editingDividerRow = rowNr }) {
         Icon(
             imageVector = if (existingDivider != null) Icons.Default.Title else Icons.Default.HorizontalSplit,
             contentDescription = stringResource(
                 if (existingDivider != null) R.string.cockpit_section_divider_edit
                 else R.string.cockpit_section_divider_add
             ),
             tint = if (existingDivider != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
         )
     }
     ```
  3. In `CockpitSectionDivider` composable (around lines 1178–1220):
     ```kotlin
     @Composable
     fun CockpitSectionDivider(
         divider: SectionDividerInfo,
         modifier: Modifier = Modifier
     ) {
         val title = divider.title?.trim()
         if (title.isNullOrEmpty()) {
             HorizontalDivider(
                 modifier = modifier
                     .fillMaxWidth()
                     .padding(vertical = 4.dp),
                 thickness = 2.5.dp,
                 color = MaterialTheme.colorScheme.outline
             )
         } else {
             Row(
                 modifier = modifier
                     .fillMaxWidth()
                     .heightIn(max = 20.dp)
                 .padding(vertical = 2.dp),
                 verticalAlignment = Alignment.CenterVertically
             ) {
                 HorizontalDivider(
                     modifier = Modifier.weight(1f),
                     thickness = 2.dp,
                     color = MaterialTheme.colorScheme.outline
                 )
                 Text(
                     text = title.uppercase(),
                     style = MaterialTheme.typography.labelSmall,
                     fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.primary,
                     maxLines = 1,
                     overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.padding(horizontal = 6.dp)
                 )
                 HorizontalDivider(
                     modifier = Modifier.weight(1f),
                     thickness = 2.dp,
                     color = MaterialTheme.colorScheme.outline
                 )
             }
         }
     }
     ```

### Component 2: `SensorGridScreenLayoutTest.kt` (Layout & Contract Verification)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Changes**:
  Add three dedicated contract test methods:
  1. `testCockpitSectionDivider_unlabeledProminenceAndContrast()`:
     Asserts that `SensorGridScreen.kt` declares `thickness = 2.5.dp` and `color = MaterialTheme.colorScheme.outline` in the unlabeled divider branch.
  2. `testCockpitSectionDivider_labeledCenteredTitleSymmetricWeights()`:
     Asserts that titled divider uses equal `Modifier.weight(1f)` flanking lines, `thickness = 2.dp`, `color = MaterialTheme.colorScheme.outline`, and that `0.12f` and `0.88f` weights are completely absent (`assertFalse`).
  3. `testConfigurationMode_dividerButtonUsesHorizontalSplitAndExcludesHorizontalRule()`:
     Asserts that configuration mode uses `Icons.Default.HorizontalSplit` and that `Icons.Default.HorizontalRule` is strictly absent from `SensorGridScreen.kt` (`assertFalse`).

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
Per `docs/design_guidelines.md` Section 5:
* **Reference screen / component**:
  * `SensorGridScreen.kt` (`CockpitSectionDivider` and configuration mode inter-row controls).
* **Reused components**:
  * Material 3 `HorizontalDivider`, `IconButton`, `Icon`, `Text`, `Row`.
  * Standard Material icon `Icons.Default.HorizontalSplit` (from `androidx.compose.material:material-icons-extended`).
* **Theme tokens**:
  * **Shapes**: Linear dividers, no custom container shapes.
  * **Spacing**: `vertical = 4.dp` for unlabeled divider, `vertical = 2.dp` for labeled divider row (`heightIn(max = 20.dp)`), `padding(horizontal = 6.dp)` for title text.
  * **Colors**:
    * `MaterialTheme.colorScheme.outline` for prominent, high-contrast dividers across Light, Dark, and AMOLED themes (replacing faint `outlineVariant`).
    * `MaterialTheme.colorScheme.primary` for section title text and active title icon tint.
  * **Typography**: `MaterialTheme.typography.labelSmall`, `FontWeight.Bold` for uppercase section title.
* **New one-off styles & justification**:
  * None. Fully conforms to Material 3 tokens, Design Guidelines Sections 5.5 and 5.11, and Rule 49.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate 3 Approval (Rule 4)
* Verify that Stage 3 subtask `ATT-4337` is `Erledigt` before touching any source code:
  ```bash
  python3 tools/jira_util.py check-gate ATT-4337
  ```

### Step 2: Update Layout & Affordance Contract Tests (TDD Discipline)
* Target File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* Add:
  - `testCockpitSectionDivider_unlabeledProminenceAndContrast` (`TST-UI-386.1`)
  - `testCockpitSectionDivider_labeledCenteredTitleSymmetricWeights` (`TST-UI-386.2`)
  - `testConfigurationMode_dividerButtonUsesHorizontalSplitAndExcludesHorizontalRule` (`TST-UI-386.3`)
* Run targeted tests to verify expected failure against unmodified source:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"
  ```

### Step 3: Implement UI Refinements in `SensorGridScreen.kt`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Changes:
  1. Replace import `HorizontalRule` with `HorizontalSplit`.
  2. In `ScreenMode.CONFIGURATION` (line 474): change `Icons.Default.HorizontalRule` to `Icons.Default.HorizontalSplit`.
  3. In `CockpitSectionDivider` (lines 1184–1217):
     - Unlabeled divider: `thickness = 2.5.dp`, `color = MaterialTheme.colorScheme.outline`.
     - Labeled divider: Left flanking divider `Modifier.weight(1f)`, `thickness = 2.dp`, `color = MaterialTheme.colorScheme.outline`. Right flanking divider `Modifier.weight(1f)`, `thickness = 2.dp`, `color = MaterialTheme.colorScheme.outline`.

### Step 4: Run Targeted Unit & Layout Tests
* Verify all `SensorGridScreen` layout and gesture tests pass:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.SensorGridLocalizationTest"
  ```

### Step 5: Code Quality & KDoc Maintenance
* Confirm that modified methods in `SensorGridScreen.kt` maintain comprehensive KDoc comments describing the prominence rationale, symmetric weights, and `HorizontalSplit` affordance.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - **Stage 4 Construction**: Targeted unit tests (`SensorGridScreenLayoutTest`, `SensorGridLocalizationTest`) pass with 100% success.
  - **Stage 5 Verification**: Clean-room test suite (`./gradlew testDebugUnitTest`) across all project modules, APK build check (`./gradlew assembleDebug`), and living doc synchronization (`docs/requirements.md`, `docs/tests.md`).
* **Rollback Plan**:
  - All changes reside on isolated feature branch `feature/ATT-4286`.
  - In case of unexpected issues, the branch can be cleanly reset (`git reset --hard origin/feature/ATT-4286` or branch re-creation from `sprint/2026-43.1`) without impacting the sprint integration baseline or `develop`.
