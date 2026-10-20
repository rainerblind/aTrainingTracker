# Stage 2: Requirement & Test Specification - ATT-4286: Refine Cockpit section dividers: replace minus-like icon, increase divider prominence, and center section title

**Ticket**: [[ATT-4286]](https://rainerblind.atlassian.net/browse/ATT-4286)  
**Sub-task**: [[ATT-4336]](https://rainerblind.atlassian.net/browse/ATT-4336) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*)  
**Test Spec ID**: `TST-UI-386` (refining `TST-UI-374`)  
**Branch**: `feature/ATT-4286`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification ([REQ-UI-411])

### 1.1 Problem Statement & Rationale
In ticket `ATT-4055` (Sprint `2026-42.10`), configurable horizontal section dividers with optional mini-titles (`REQ-UI-411`) were introduced to the Cockpit (`SensorGridScreen.kt`). This feature enables athletes to partition dense 5–7 row sensor grids into visually distinct logical clusters (such as Primary Telemetry, Power & Cadence, Heart Rate, and Elevation).

During real-world on-device testing on a Google Pixel 10 during the Sprint Review of `ATT-4055` (captured in `docs/attachments/cockpit_divider_review.png`), three usability and visual design issues were observed:
1. **Misleading '-' Icon in Configuration Mode**: In `SensorGridScreen.kt` (lines 472–480), between rows in `ScreenMode.CONFIGURATION`, the button to add a section divider uses `Icons.Default.HorizontalRule` (`—`) immediately adjacent to the `RowAdder` button which uses `Icons.Default.Add` (`+`). Visually, a horizontal stroke `—` placed right next to `+` reads unmistakably as a minus / delete button (`+ / -`). Athletes naturally interpret this pair as "Add Row" and "Delete Row". Tapping `—` does not delete a row, but unexpectedly opens the "Add section divider" dialog.
2. **Low Visual Prominence of Dividers**: In `CockpitSectionDivider` (`SensorGridScreen.kt` lines 1184–1190), an unlabeled divider is rendered as a thin `1.5.dp` stroke with `MaterialTheme.colorScheme.outlineVariant`. In the Cockpit grid, every sensor cell already has an outer border. Consequently, an unlabeled divider between two bordered cell rows looks like an accidental double hairline border or a rendering glitch with whitespace padding rather than an authoritative, high-contrast semantic section separator. Under direct sunlight while riding, this distinction is lost.
3. **Off-Center Section Title**: In `CockpitSectionDivider` (lines 1199–1217), when a section divider includes a title (e.g. `PULS`, `HÖHE`), the layout uses asymmetric flanking weights: `Modifier.weight(0.12f)` on the left and `Modifier.weight(0.88f)` on the right. This pushes the uppercase title heavily to the left (~12% across the screen), creating an unbalanced, lopsided presentation across the cockpit width instead of a clean, horizontally centered section header.

### 1.2 Functional & Architectural Requirements
The system SHALL refine Cockpit section dividers and inter-row configuration controls in `SensorGridScreen.kt` conforming to ASPICE Governance Rule 49 and Design Guidelines Sections 5.5 and 5.11 (`REQ-UI-411.4`, `REQ-UI-411.5`):

1. **Unambiguous Split Iconography in Configuration Mode (`REQ-UI-411.5`)**:
   - In `ScreenMode.CONFIGURATION`, when no divider exists at a row boundary (`existingDivider == null`), the action button adjacent to `RowAdder` (`Icons.Default.Add`) SHALL display `Icons.Default.HorizontalSplit` with tint `MaterialTheme.colorScheme.outline`.
   - The use of `Icons.Default.HorizontalRule` (`—`) in `SensorGridScreen.kt` is **STRICTLY FORBIDDEN**, eliminating any false visual affordance of a minus/row-delete control (`+ / -`).
   - When a divider already exists at that row boundary (`existingDivider != null`), the action button SHALL continue to display `Icons.Default.Title` with tint `MaterialTheme.colorScheme.primary` and content description `stringResource(R.string.cockpit_section_divider_edit)`.
   - Accessibility content descriptions (`R.string.cockpit_section_divider_add` and `R.string.cockpit_section_divider_edit`) and click behavior (`editingDividerRow = rowNr`) SHALL remain unchanged.

2. **Divider Visual Prominence & Contrast (`REQ-UI-411.4`)**:
   - When `divider.title` is null or blank, `CockpitSectionDivider` SHALL render a prominent horizontal stroke with `thickness = 2.5.dp` (conforming to the `thickness >= 2.dp` guideline) and color `MaterialTheme.colorScheme.outline` (providing authoritative non-text contrast against dark/AMOLED and light cockpit surfaces).
   - Vertical padding SHALL remain compact (`padding(vertical = 4.dp)`), guaranteeing that the total vertical height footprint of the divider remains $\le 10\text{ dp}$.

3. **Horizontally Centered Mini-Titles & Flanking Symmetry (`REQ-UI-411.4`)**:
   - When `divider.title` is present, `CockpitSectionDivider` SHALL horizontally center the uppercase title across the full row width.
   - The title SHALL be flanked symmetrically by left and right `HorizontalDivider` segments with equal `Modifier.weight(1f)`.
   - Asymmetric flanking weights (specifically `0.12f` and `0.88f`) are **STRICTLY FORBIDDEN**.
   - Flanking lines SHALL use `thickness = 2.dp` and color `MaterialTheme.colorScheme.outline`.
   - The title typography (`labelSmall`, `FontWeight.Bold`, `primary` color), single-line ellipsis truncation (`maxLines = 1`, `TextOverflow.Ellipsis`), and maximum container height ceiling (`heightIn(max = 20.dp)`) SHALL remain strictly enforced.

4. **Zero Persistence or Schema Mutations**:
   - SQLite database schema version for `FlexibleGenericView.db` remains strictly at `DB_VERSION = 14`.
   - `SectionDividersTable` schema, `TrackingViewsDatabaseManager` CRUD and row-shift methods (`REQ-UI-411.2`), `TrackingViewsRepository` Flow emissions (`REQ-UI-411.3`), and `EditSectionDividerDialog` workflows remain 100% intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Configuration Mode Add-Divider Icon)**:
  * *Given* the Cockpit in `ScreenMode.CONFIGURATION`,
  * *When* viewing the inter-row action controls between sensor rows,
  * *Then* the button to add a section divider displays `Icons.Default.HorizontalSplit` (and not `Icons.Default.HorizontalRule`), preventing false minus/row-delete affordances next to `RowAdder` (`+`).
* **Criterion 2 (Unlabeled Divider Prominence)**:
  * *Given* an unlabeled section divider (`divider.title == null || divider.title.isBlank()`),
  * *When* rendered in `ScreenMode.TRACKING` or `ScreenMode.PREVIEW`,
  * *Then* it renders with a prominent `2.5.dp` stroke in `MaterialTheme.colorScheme.outline`, clearly distinguishable from adjacent 1.dp cell borders with total vertical footprint $\le 10\text{ dp}$.
* **Criterion 3 (Labeled Divider Centering & Symmetric Weights)**:
  * *Given* a titled section divider (`divider.title = "PULS"`),
  * *When* rendered in `ScreenMode.TRACKING` or `ScreenMode.PREVIEW`,
  * *Then* the uppercase title is horizontally centered across the row width, flanked symmetrically on both left and right by `HorizontalDivider` lines with equal `Modifier.weight(1f)` and `thickness = 2.dp`.
* **Criterion 4 (Title Truncation Safety)**:
  * *Given* a long section divider title on a narrow mobile viewport,
  * *When* rendered in `ScreenMode.TRACKING`,
  * *Then* the title truncates cleanly with ellipsis (`maxLines = 1`, `TextOverflow.Ellipsis`) without multi-line wrapping and without exceeding the $20\text{ dp}$ height ceiling.
* **Criterion 5 (9-Language Parity & Zero New String Mutations)**:
  * *Given* all 9 canonical application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`),
  * *When* audited by `SensorGridLocalizationTest`,
  * *Then* zero new string resources are required, and existing section divider strings maintain 100% parity with zero missing keys.

### 1.4 System Invariants
1. **Vertical Space Preservation Invariant**: Section dividers must not crowd out live telemetry tiles or map canvas; unlabeled dividers must consume $\le 10\text{ dp}$ total height and labeled dividers must strictly observe `heightIn(max = 20.dp)`.
2. **Icon Semantics Invariant (Rule 49)**: Never use `HorizontalRule` (`—`) as an insert action next to `+`.
3. **Database & Model Stability**: No database migrations, table alterations, or repository changes.
4. **Single Atomic Commit Invariant (Rule 44)**: Zero intermediate git commits during Stage 1–4; all deliverables, code, and tests are squashed into a single atomic commit at the end of Stage 5.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-411.4` (Glanceable Cockpit Rendering & Truncation Safety) and `REQ-UI-411.5` (Configuration Mode WYSIWYG Controls) within `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*), refined by `ATT-4286`. Targets `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`, `SensorGridScreenLayoutTest.kt`, and `SensorGridLocalizationTest.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-4055` (commit `6199b2a45fcfb81f13a31906b3e2b725ea6c4618`, Sprint `2026-42.10`), refined in `ATT-4286` (Sprint `2026-43.1`).
* **Root Reason for Existing Formulation**: `Icons.Default.HorizontalRule` was selected literally to represent a horizontal divider rule line, but positioned immediately adjacent to `RowAdder`'s `+` icon, it misleadingly functioned as a minus/delete button (`-`). A faint `1.5.dp` stroke with `outlineVariant` visually blended with `1.dp` cell borders, and asymmetric weights (`0.12f`/`0.88f`) were an exploratory left-accent layout that appeared lopsided on device.
* **Preservation of Core Invariants**: Replacing `HorizontalRule` with `HorizontalSplit` removes the deceptive minus affordance while preserving click handlers, accessibility descriptions, and active title indicators (`Title`). Increasing stroke thickness to `2.5.dp` (unlabeled) and `2.dp` (labeled) ensures glanceability while strictly preserving the $\le 20\text{ dp}$ height ceiling. Centering titles via symmetric `Modifier.weight(1f)` flanking lines provides balanced layout across all screen sizes and orientations without mutating database schema (v14) or row-shift logic.

---

## 2. Test Specification ([TST-UI-386])

### Test Case 1: `testCockpitSectionDivider_unlabeledProminenceAndContrast` (`[TST-UI-386.1]`)
* **Scope**: Layout & Styling Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source file resolved.
* **Action**: Parse `CockpitSectionDivider` composable implementation.
* **Expected Result**:
  - Unlabeled divider branch (`title.isNullOrEmpty()`) specifies `thickness = 2.5.dp` (verifying $\ge 2.0\text{ dp}$).
  - Stroke color specifies `MaterialTheme.colorScheme.outline` (not `outlineVariant`).
  - Vertical padding specifies `padding(vertical = 4.dp)` ensuring total height overhead $\le 10\text{ dp}$.

### Test Case 2: `testCockpitSectionDivider_labeledCenteredTitleSymmetricWeights` (`[TST-UI-386.2]`)
* **Scope**: Layout & Symmetry Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source file resolved.
* **Action**: Parse titled divider branch inside `CockpitSectionDivider`.
* **Expected Result**:
  - Left `HorizontalDivider` specifies `Modifier.weight(1f)`.
  - Right `HorizontalDivider` specifies `Modifier.weight(1f)`.
  - Asymmetric weights `0.12f` and `0.88f` are strictly absent (`assertFalse`).
  - Flanking divider lines specify `thickness = 2.dp` and `MaterialTheme.colorScheme.outline`.
  - Uppercase title typography, `maxLines = 1`, `TextOverflow.Ellipsis`, and container `heightIn(max = 20.dp)` are preserved.

### Test Case 3: `testConfigurationMode_dividerButtonUsesHorizontalSplitAndExcludesHorizontalRule` (`[TST-UI-386.3]`)
* **Scope**: Iconography & Action Affordance Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source file resolved.
* **Action**: Inspect inter-row action controls in `ScreenMode.CONFIGURATION`.
* **Expected Result**:
  - Divider action button specifies `Icons.Default.HorizontalSplit` when `existingDivider == null`.
  - `Icons.Default.HorizontalRule` is strictly absent from `SensorGridScreen.kt` (`assertFalse`).
  - Divider action button specifies `Icons.Default.Title` when `existingDivider != null`.
  - Content descriptions reference `R.string.cockpit_section_divider_add` and `R.string.cockpit_section_divider_edit`.

### Test Case 4: 9-Language Localization & Specifier Audit (`[TST-UI-386.4]`)
* **Scope**: 9-Locale Translation Parity Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorGridLocalizationTest.kt`
* **Preconditions**: All 9 locale directories exist (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Action**: Audit all section divider string keys across all 9 locales:
  - `cockpit_section_divider_add`
  - `cockpit_section_divider_edit`
  - `cockpit_section_divider_dialog_title`
  - `cockpit_section_divider_title_label`
  - `cockpit_section_divider_chip_power`
  - `cockpit_section_divider_chip_heart_rate`
  - `cockpit_section_divider_chip_lap`
  - `cockpit_section_divider_chip_pacing`
  - `cockpit_section_divider_chip_navigation`
  - `cockpit_section_divider_chip_total`
  - `cockpit_section_divider_delete`
* **9-Locale Verification Findings**:
  - **New String Resources**: **None**. No new string keys are required; existing accessibility content descriptions (`cockpit_section_divider_add` / `cockpit_section_divider_edit`) and dialog strings already provide accurate, localized labels across all 9 languages.
  - **Parity Status**: 100% parity across all 9 canonical locales with zero missing entries and zero format specifier mismatches.

### Test Case 5: Clean-Room Regression Suite (`[TST-UI-386.5]`)
* **Scope**: Clean-Room Full Suite Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Verification Item | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-386.1]` | Layout | `SensorGridScreenLayoutTest.kt` (Unlabeled stroke thickness $\ge 2.5\text{ dp}$, `outline` color, $\le 10\text{ dp}$ height) | `REQ-UI-411.4` | Specified |
| `[TST-UI-386.2]` | Layout | `SensorGridScreenLayoutTest.kt` (Centered title, symmetric `weight(1f)` flanking lines, `2.dp` thickness) | `REQ-UI-411.4` | Specified |
| `[TST-UI-386.3]` | Iconography | `SensorGridScreenLayoutTest.kt` (`Icons.Default.HorizontalSplit` used, `HorizontalRule` prohibited) | `REQ-UI-411.5` | Specified |
| `[TST-UI-386.4]` | Localization | `SensorGridLocalizationTest.kt` (9-locale parity for all divider keys, zero new strings) | `REQ-UI-411.6`, `REQ-LOC-001` | Specified |
| `[TST-UI-386.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
