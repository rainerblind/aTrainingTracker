# Stage 1 Analysis: ATT-4286 - Refine Cockpit section dividers: replace minus-like icon, increase divider prominence, and center section title

**Ticket**: [[ATT-4286]](https://rainerblind.atlassian.net/browse/ATT-4286)  
**Sub-task**: [[ATT-4335]](https://rainerblind.atlassian.net/browse/ATT-4335) (`[Analysis]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4286`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

In ticket `ATT-4055` (Sprint `2026-42.10`), configurable horizontal section dividers with optional mini-titles (`REQ-UI-411`) were introduced to the Cockpit (`SensorGridScreen.kt`). This feature enables athletes to partition dense 5–7 row sensor grids into visually distinct logical clusters (such as Primary Telemetry, Power & Cadence, Heart Rate, and Elevation).

During real-world on-device testing on a Google Pixel 10 during the Sprint Review of `ATT-4055` (captured in `docs/attachments/cockpit_divider_review.png`), three usability and visual design issues were observed:

1. **Misleading '-' Icon in Configuration Mode**:
   In `SensorGridScreen.kt` (lines 472–480), between rows in `ScreenMode.CONFIGURATION`, the button to add a section divider uses `Icons.Default.HorizontalRule` (`—`) immediately adjacent to the `RowAdder` button which uses `Icons.Default.Add` (`+`). Visually, a horizontal stroke `—` placed right next to `+` reads unmistakably as a minus / delete button (`+ / -`). Athletes naturally interpret this pair as "Add Row" and "Delete Row". Tapping `—` does not delete a row, but unexpectedly opens the "Add section divider" dialog.
2. **Low Visual Prominence of Dividers**:
   In `CockpitSectionDivider` (`SensorGridScreen.kt` lines 1184–1190), an unlabeled divider is rendered as a thin `1.5.dp` stroke with `MaterialTheme.colorScheme.outlineVariant`. In the Cockpit grid, every sensor cell already has an outer border. Consequently, an unlabeled divider between two bordered cell rows looks like an accidental double hairline border or a rendering glitch with whitespace padding rather than an authoritative, high-contrast semantic section separator. Under direct sunlight while riding, this distinction is lost.
3. **Off-Center Section Title**:
   In `CockpitSectionDivider` (lines 1199–1217), when a section divider includes a title (e.g. `PULS`, `HÖHE`), the layout uses asymmetric flanking weights: `Modifier.weight(0.12f)` on the left and `Modifier.weight(0.88f)` on the right. This pushes the uppercase title heavily to the left (~12% across the screen), creating an unbalanced, lopsided presentation across the cockpit width instead of a clean, horizontally centered section header.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `SensorGridScreen.kt` identified the exact code mechanisms responsible for each issue:

### 2.1 Configuration Mode Icon Ambiguity (`SensorGridScreen.kt:472-480`)
In `SensorGridScreen.kt`, the inter-row controls inside `ScreenMode.CONFIGURATION` are arranged in a horizontal `Row`:
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Box(modifier = Modifier.weight(1f)) {
        RowAdder(onClick = { ... }) // Uses Icons.Default.Add (+)
    }
    IconButton(onClick = { editingDividerRow = rowNr }) {
        Icon(
            imageVector = if (existingDivider != null) Icons.Default.Title else Icons.Default.HorizontalRule,
            contentDescription = stringResource(
                if (existingDivider != null) R.string.cockpit_section_divider_edit
                else R.string.cockpit_section_divider_add
            ),
            tint = if (existingDivider != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}
```
When `existingDivider == null`, the icon evaluates to `Icons.Default.HorizontalRule`. The glyph for `HorizontalRule` in Material Icons is a solid horizontal bar that is visually identical to a mathematical minus sign. Because it is positioned adjacent to `RowAdder`'s plus sign (`+`), athletes perceive a `[ + ] [ - ]` control group. Per ASPICE Governance Rule 49 and Design Guidelines Section 5.5, `HorizontalRule` (`—`) must never be used as an insert action next to `+`.

### 2.2 Stroke Thickness and Color Contrast (`SensorGridScreen.kt:1182-1191`)
In `CockpitSectionDivider`, unlabeled dividers are rendered as:
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
            thickness = 1.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    } else { ... }
}
```
`MaterialTheme.colorScheme.outlineVariant` is intended for subtle decorative boundaries and has low contrast against the cockpit surface. Combined with a thin `1.5.dp` stroke and `4.dp` vertical padding, it blends into the adjacent `1.dp` cell borders. Per Design Guidelines Section 5.11, section dividers inside dense telemetry grids must use a thick stroke (`thickness >= 2.dp` or `2.5.dp`) with a prominent, high-contrast token (such as `MaterialTheme.colorScheme.outline` or `onSurfaceVariant` with sufficient opacity) to clearly demarcate groups at a glance.

### 2.3 Asymmetric Flanking Weights (`SensorGridScreen.kt:1199-1217`)
For titled dividers, `CockpitSectionDivider` renders:
```kotlin
HorizontalDivider(
    modifier = Modifier.weight(0.12f),
    thickness = 1.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant
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
    modifier = Modifier.weight(0.88f),
    thickness = 1.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant
)
```
The hardcoded weights `0.12f` and `0.88f` force the text to sit 12% from the left margin, extending the remaining 88% across the right side of the screen. Per Design Guidelines Section 5.11 and Rule 49, titled section dividers must be centered horizontally with symmetric flanking rules (`Modifier.weight(1f)` on both sides).

---

## 3. User Scope Grounding (ATT-1250)

In accordance with `ATT-1250`, review and implementation expectations are strictly bounded to the user request and parent ticket scope:

* **In-Scope Goals**:
  1. Replace `Icons.Default.HorizontalRule` in `SensorGridScreen.kt` configuration mode with an unambiguous divider/split icon (such as `Icons.Default.HorizontalSplit`) that cannot be confused with a minus / row-delete button.
  2. Increase the visual prominence of unlabeled `CockpitSectionDivider` instances using a thicker stroke (`2.5.dp`) and high-contrast color token (`MaterialTheme.colorScheme.outline` / `onSurfaceVariant` high opacity) while maintaining compact vertical padding ($\le 10\text{ dp}$ total height).
  3. Increase the stroke thickness of flanking lines in labeled `CockpitSectionDivider` instances (`2.dp`) and center the section title horizontally by applying symmetric `Modifier.weight(1f)` to both left and right `HorizontalDivider` elements.
  4. Add unit and contract tests verifying that `HorizontalRule` is not used, the divider title is centered with symmetric weights, and stroke thickness meets the prominence criteria.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No modifications to database tables, schema version, or SQL queries (`FlexibleGenericView.db` schema version 14 remains unchanged).
  2. No changes to `TrackingViewsDatabaseManager`, `TrackingViewsRepository`, or `TrackingViewModel` row shift logic (`REQ-UI-411.2`) or flow emissions (`REQ-UI-411.3`).
  3. No changes to `EditSectionDividerDialog` preset chips, text entry, or deletion workflows (`REQ-UI-411.5`).
  4. No modifications to other screen modes, bottom sheets, or unrelated cockpit components.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

Because this change refines the rendering and configuration controls introduced by `REQ-UI-411`, the 4 mandatory Chesterton's Fence fields are documented below:

* **Original Requirement ID & Target**:
  `REQ-UI-411.4` (Glanceable Cockpit Rendering & Truncation Safety) and `REQ-UI-411.5` (Configuration Mode WYSIWYG Controls) within `REQ-UI-411` (*Configurable Horizontal Section Dividers with Optional Mini-Titles in Cockpit Sensor Grid*).  
  Target file: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`.
* **Historical Origin & Commit Trace**:
  Introduced in ticket `ATT-4055`, Sprint `2026-42.10`, Commit `6199b2a45fcfb81f13a31906b3e2b725ea6c4618` (*"feat(ATT-4055): support configurable horizontal section dividers with optional mini-titles between Cockpit rows"*).
* **Root Reason for Existing Formulation**:
  `ATT-4055` introduced dividers to partition dense grids. `Icons.Default.HorizontalRule` was selected literally because it represents a "horizontal rule" without anticipating that adjacent to `Icons.Default.Add` (`+`), it visually functions as a minus sign (`-`). A conservative `1.5.dp` stroke with `outlineVariant` was chosen to ensure minimal vertical footprint well within the $\le 20\text{ dp}$ ceiling. Asymmetric weights (`0.12f` / `0.88f`) were an exploratory design attempt at a left-accented category lead-in.
* **Preservation of Core Invariants**:
  - Replacing `HorizontalRule` with `HorizontalSplit` removes the deceptive minus-sign affordance while maintaining the identical onClick handler (`editingDividerRow = rowNr`), accessibility content descriptions (`R.string.cockpit_section_divider_add`), and active title indicator (`Icons.Default.Title`).
  - Increasing divider stroke thickness to `2.5.dp` (unlabeled) and `2.dp` (labeled) improves glanceability under direct sunlight while strictly preserving the $\le 20\text{ dp}$ height ceiling constraint.
  - Centering the title via symmetric `Modifier.weight(1f)` flanking lines provides visual balance across all screen sizes and orientations while preserving single-line ellipsis truncation (`maxLines = 1`, `TextOverflow.Ellipsis`).
  - Zero database schema or data model mutations; all existing database records and row-shift operations remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Call Site & Component Blueprint
The proposed changes are isolated to `SensorGridScreen.kt` and its companion unit tests:

1. **Configuration Mode Divider Action (`SensorGridScreen.kt:472-480`)**:
   - Replace `Icons.Default.HorizontalRule` with `Icons.Default.HorizontalSplit` (available in `androidx.compose.material:material-icons-extended`).
   - When `existingDivider != null`, retain `Icons.Default.Title` with `primary` tint to indicate an existing divider with title edit affordance.
   - When `existingDivider == null`, display `Icons.Default.HorizontalSplit` with `outline` tint.
2. **Divider Prominence & Centering (`CockpitSectionDivider`, lines 1178–1220)**:
   - **Unlabeled Divider**:
     - Increase `thickness` from `1.5.dp` to `2.5.dp`.
     - Update color to `MaterialTheme.colorScheme.outline` (or `onSurfaceVariant` with 60% opacity) for crisp contrast against both light and dark cockpit backgrounds.
     - Keep vertical padding compact (`vertical = 3.dp` or `4.dp`), ensuring total height footprint is $\le 10\text{ dp}$.
   - **Labeled Divider**:
     - Change left `HorizontalDivider` modifier to `Modifier.weight(1f)`.
     - Change right `HorizontalDivider` modifier to `Modifier.weight(1f)`.
     - Increase `thickness` of flanking lines to `2.dp` with matching `MaterialTheme.colorScheme.outline`.
     - Keep title typography (`labelSmall`, `FontWeight.Bold`, `primary` color, `maxLines = 1`, `TextOverflow.Ellipsis`).
3. **Automated Layout & Contract Tests**:
   - Author tests in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt` (or dedicated test class) asserting:
     - The configuration mode divider button uses `Icons.Default.HorizontalSplit` and excludes `Icons.Default.HorizontalRule`.
     - `CockpitSectionDivider` uses equal `weight(1f)` for left and right flanking dividers.
     - Unlabeled divider thickness is $\ge 2\text{ dp}$.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Single atomic commit per ticket (Rule 44) — no intermediate git commits during Stage 1–4.
  2. Strict preservation of the vertical height ceiling ($\le 20\text{ dp}$) for Cockpit section dividers.
  3. Single-line title truncation (`maxLines = 1`, `TextOverflow.Ellipsis`) preserved across all 9 supported locales.
  4. Zero database schema migrations or data model changes (`FlexibleGenericView.db` schema version 14 preserved).
  5. 100% clean-room test suite pass rate across all project modules.
* **Risk Rating**: **LOW**
  - Justification: Pure UI presentation refinement in Jetpack Compose layout components (`SensorGridScreen.kt`). No changes to database persistence, background service lifecycles, threading dispatchers, or repository flows.
