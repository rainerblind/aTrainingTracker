# Stage 1 Analysis: ATT-4372 - [Bug] Polish BatchImportSportTypeDialog layout (fix truncated segmented buttons and vertical 'Andere' chip wrap) and default to Other

**Ticket**: [ATT-4372](https://rainerblind.atlassian.net/browse/ATT-4372)  
**Sub-task**: [ATT-4392](https://rainerblind.atlassian.net/browse/ATT-4392) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Branch**: `feature/ATT-4372`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

### Current Behavior
In Sprint `2026-43.1`, ticket `ATT-4284` introduced `BatchImportSportTypeDialog` (`ui/routes/BatchImportSportTypeDialog.kt`) to allow athletes to choose between applying a sport type to all imported routes or configuring routes individually during multi-file GPX/TCX route import. During the Sprint `2026-43.1` Joint Review (`ATT-4290`), live on-device testing on a German-locale device revealed two visual layout defects and a domain default discrepancy (see screenshot `att_batch_import_dialog.png` attached to ATT-4372):

1. **Truncated Segmented Buttons**:
   The binary mode switcher (`SingleChoiceSegmentedButtonRow`) inside the standard Material 3 `AlertDialog` horizontally truncated the segmented button labels in German:
   - `"Auf alle anwenden"` was clipped mid-word to `"Auf alle anwen"`.
   - `"Individuell anpassen"` was clipped mid-word to `"Individuell anpa"`.
   In other verbose locales (e.g. Spanish `"Configurar individualmente"` [26 chars], French `"Configurer individuellement"` [26 chars], Polish `"Konfiguruj indywidualnie"` [24 chars]), the text truncation is even more severe.

2. **Vertical Single-Character 'Andere' Chip Wrapping**:
   The 3 sport type selection chips (`BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN`) were placed in a single, rigid horizontal `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))`. In German (`"Radeln"`, `"Laufen"`, `"Andere"`), the first two chips with leading icons consumed nearly all available width within the narrow `AlertDialog`. The remaining width squeezed the third chip (`"Andere"`) into a narrow ~1-character-wide column, forcing the text to break vertically into a 6-line stack:
   ```text
   A
   n
   d
   e
   r
   e
   ```

3. **Suboptimal Default Sport Preselection**:
   `BatchImportSportTypeDialog` hardcoded `selectedSport = BSportType.BIKE`. However, GPX and TCX tracks imported in batch frequently originate from walking, running, hiking, or unspecified outdoor activities. Defaulting to `Bike` risks silent misclassification if the athlete leaves the preselection untouched. When sport type is ambiguous or unspecified, the neutral, safe default is `"Other"` (`BSportType.UNKNOWN` / `"Andere"`), matching the domain fallback used across `GpxRouteImporter` and `TcxCourseParser`.

### Expected Behavior
1. **Clean Responsive Layout**: The batch import prompt adapts smoothly to device width and label length across all 9 canonical locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero mid-word clipping.
2. **Proper Chip Wrapping**: Multi-chip selectors wrap cleanly as intact horizontal pills onto the next line using `FlowRow` per Rule 54 (`docs/design_guidelines.md` Sec 5.13), preventing any 1-character vertical text stacking.
3. **Harmonious Route UI**: Adopt `AppModalBottomSheet` (matching `EditRouteScreen` and `RouteFilterBottomSheet` in `ui/routes/`), providing full screen width, standard sheet styling, top drag handle, and dedicated action slots.
4. **Neutral Default Preselection**: Default `selectedSport` to `BSportType.UNKNOWN` ("Other" / "Andere") when sport type is ambiguous, while keeping `BIKE` and `RUN` readily selectable.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 AlertDialog Width Budget & Segmented Button Clipping
In `BatchImportSportTypeDialog.kt` (lines 90–120):
```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.batch_import_dialog_title_format, routeCount)) },
    text = {
        Column(modifier = Modifier.fillMaxWidth()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == BatchSportMode.ALL,
                    onClick = { selectedMode = BatchSportMode.ALL },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(
                        text = stringResource(R.string.batch_import_mode_all),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                SegmentedButton(
                    selected = selectedMode == BatchSportMode.INDIVIDUAL,
                    onClick = { selectedMode = BatchSportMode.INDIVIDUAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(
                        text = stringResource(R.string.batch_import_mode_individual),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
...
```

**Forensic Breakdown**:
- Material 3 `AlertDialog` applies standard horizontal dialog margins (`dialog_min_width_minor = 280.dp`, typical phone content width ~`232.dp`–`272.dp`).
- Splitting ~`250.dp` across two equal segmented buttons leaves only ~`115.dp`–`125.dp` per button.
- Internal padding in `SegmentedButton` consumes `24.dp` horizontally, leaving ~`90.dp`–`100.dp` for text measurement.
- The implementer applied `maxLines = 1` and `softWrap = false` (mistakenly citing Rule 38, which was intended for short 2-character zone pills `"Z1"`–`"Z7"` and chart labels `≤ 11` characters).
- In German, `"Individuell anpassen"` (20 characters) requires ~`135.dp` at standard `labelLarge` typography. Because `softWrap = false` and `maxLines = 1` were enforced, text rendering was cut off mid-word (`"Individuell anpa"`).
- In Spanish (`"Configurar individualmente"` [26 chars]) and French (`"Configurer individuellement"` [26 chars]), truncation is even more severe.

### 2.2 Rigid Non-Wrapping Horizontal `Row` & Chip Crushing
In `BatchImportSportTypeDialog.kt` (lines 131–150):
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { sport ->
        FilterChip(
            selected = selectedSport == sport,
            onClick = { selectedSport = sport },
            label = { Text(stringResource(sport.stringResId)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = sport.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}
```

**Forensic Breakdown**:
- Compose `Row` does not wrap items to the next line.
- Each `FilterChip` reserves `18.dp` leading icon + `8.dp` icon spacing + `16.dp` chip horizontal padding = `42.dp` baseline per chip before text.
- Three chips together consume `3 × 42.dp = 126.dp` in fixed chip padding plus `2 × 8.dp = 16.dp` row spacing = `142.dp` overhead.
- In German: `"Radeln"` (~`48.dp`) + `"Laufen"` (~`45.dp`) = `93.dp` of text. Total width consumed by the first two chips = `2 × 42.dp + 8.dp + 93.dp = 185.dp`.
- With total dialog content width of ~`232.dp`, only ~`39.dp` remains for the third chip (`"Andere"`).
- Subtracting `42.dp` of chip padding leaves negative horizontal room for `"Andere"`. Compose flex layout shrinks the `Text` composable to its minimum intrinsic width (the width of the widest single character, e.g. `'m'` or `'A'`), causing soft wrapping after every single character:
  `A` / `n` / `d` / `e` / `r` / `e`.
- This anti-pattern was codified as a retrospective learning in Sprint `2026-43.1` (`ATT-4290`) and established as **Rule 54** (`docs/design_guidelines.md` Sec 5.13): *Never place 3+ FilterChips inside a single non-wrapping Row within a standard AlertDialog. Always use FlowRow.*

### 2.3 Hardcoded Initial Sport Preselection
In `BatchImportSportTypeDialog.kt` (line 88):
```kotlin
var selectedSport by remember { mutableStateOf(BSportType.BIKE) }
```
- In `ATT-4284`, the implementer set the initial state to `BSportType.BIKE`.
- Conversely, `GpxRouteImporter.kt` (line 151) and `TcxCourseParser.kt` (line 105) parse unknown tracks with `BSportType.UNKNOWN`.
- `RoutesViewModel.importRoutesBatch` and `GpxImportViewModel.handleBatchUris` also default their method parameter to `sportType = BSportType.UNKNOWN`.
- Initializing `selectedSport` to `BSportType.BIKE` created an inconsistency where batch-imported routes were coerced to Bike unless the user actively toggled to "Andere". Preselecting `BSportType.UNKNOWN` restores neutrality.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Objectives
1. **Layout Migration of `BatchImportSportTypeDialog`**:
   - Migrate `BatchImportSportTypeDialog` to use `AppModalBottomSheet` (from `ui/components/core/AppModalBottomSheet.kt`), matching `EditRouteScreen` and `RouteFilterBottomSheet` in the routes subsystem.
   - Use `FlowRow` (`horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)`) for the sport type `FilterChip` items so pills wrap intact without single-character distortion.
   - Remove rigid `maxLines = 1, softWrap = false` constraints on `SegmentedButton` text, allowing clean multi-line wrapping (`maxLines = 2`, `textAlign = TextAlign.Center`, `overflow = TextOverflow.Ellipsis`, `style = MaterialTheme.typography.labelMedium`) to accommodate long phrases (e.g. Spanish/French/Portuguese 26 chars) across all device viewports.
2. **Default Sport Type Preselection**:
   - Change `selectedSport` initial state in `BatchImportSportTypeDialog` from `BSportType.BIKE` to `BSportType.UNKNOWN` ("Other" / "Andere").
3. **Contract & Architectural Unit Test Updates**:
   - Update `BatchImportSportTypeDialogTest.kt` to verify `AppModalBottomSheet`, `FlowRow`, and `BSportType.UNKNOWN` preselection.
   - Maintain 100% test pass rate across `./gradlew testDebugUnitTest`.

### 3.2 Out-of-Scope Non-Goals (Scope Bounding)
1. **ViewModel & Repository Pipelines**:
   - `RoutesViewModel.importRoutesBatch` and `GpxImportViewModel.handleBatchUris` already default to `BSportType.UNKNOWN` and accept sport override parameters. No changes to ViewModels or `RoutesRepository` are required.
2. **Parser Heuristics**:
   - No auto-detection or XML heuristic guessing for sport types inside `GpxRouteImporter` or `TcxCourseParser`.
3. **Single-File Import Flow**:
   - Single-file import (`uris.size == 1`) opens `EditRouteScreen` directly; no changes to single-file handling.
4. **Call Site Signatures**:
   - `RoutesScreen.kt` and `GpxImportActivity.kt` call signatures for `BatchImportSportTypeDialog(routeCount = ..., onConfirm = ..., onDismiss = ...)` remain 100% unchanged.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  Refines `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*), specifically clause `REQ-MAP-053.6` (*Batch Import Sport Type Dialog Component Architecture*) and its acceptance criteria. Targets:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
  - `docs/requirements.md`
  - `docs/tests.md`
* **Historical Origin & Commit Trace**:
  - `ATT-4049` (commit `8c418181`, Sprint 2026-42.10) created `REQ-MAP-053` with headless batch import.
  - `ATT-4284` (commit `49133ab1`, Sprint 2026-43.1) introduced `REQ-MAP-053.6` and `BatchImportSportTypeDialog.kt` to allow sport selection.
  - Sprint `2026-43.1` Review (`ATT-4290`, commit `7fafb143`) identified the German layout truncation and 'Andere' chip crushing, formulating **Rule 54** (`docs/design_guidelines.md` Sec 5.13).
* **Root Reason for Existing Formulation**:
  - `ATT-4284` implemented `BatchImportSportTypeDialog` as an `AlertDialog` with a 2-segment button and 3 chips in a `Row` under the assumption that short English labels would fit horizontally.
  - The author applied `maxLines = 1` citing Rule 38 without budgeting for verbose localized strings in narrow dialog containers.
  - `BSportType.BIKE` was preselected under the assumption that cycling was the primary use case, rather than preserving domain neutrality.
* **Preservation of Core Invariants**:
  1. Call site contracts in `RoutesScreen` and `GpxImportActivity` remain identical.
  2. Binary mode toggle (`BatchSportMode.ALL` vs `BatchSportMode.INDIVIDUAL`) remains preserved.
  3. All three sport types (`BIKE`, `RUN`, `UNKNOWN`) remain selectable with canonical icons and localized names.
  4. Non-blocking asynchronous batch import on `Dispatchers.IO` remains intact.
  5. Clean-room unit test suite pass rate under `./gradlew testDebugUnitTest` remains 100%.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Presentation Container: `AppModalBottomSheet`
Migrate `BatchImportSportTypeDialog` from `AlertDialog` to `AppModalBottomSheet`:
- **Visual Consistency**: `EditRouteScreen` and `RouteFilterBottomSheet` in `ui/routes/` both use `AppModalBottomSheet`. Using `AppModalBottomSheet` unifies modal presentation in the Routes subsystem.
- **Horizontal Width Budget**: Full screen width (`~360.dp`–`412.dp`) eliminates the narrow `280.dp` dialog margin bottleneck.
- **Standard Styling**: Uses `BottomSheetDesign.SheetShape` (20dp rounded top corners), surface container color tokens, and `MinimumDragHandle()`.
- **Slotted Header & Actions**: Header title with formatted route count (`R.string.batch_import_dialog_title_format`) and close button; bottom actions row for Cancel and Confirm (Import / Continue).

### 5.2 Responsive Segmented Button Mode Switcher
In `SingleChoiceSegmentedButtonRow`:
- Use `modifier = Modifier.fillMaxWidth()`.
- Configure `SegmentedButton` text with `maxLines = 2`, `textAlign = TextAlign.Center`, `overflow = TextOverflow.Ellipsis`, and `style = MaterialTheme.typography.labelMedium`.
- Even in 26-character locales (Spanish `"Configurar individualmente"`, French `"Configurer individuellement"`, Portuguese `"Configurar individualmente"`), text wraps gracefully into 2 centered lines without mid-word clipping.

### 5.3 Multi-Chip `FlowRow` for Sport Selection
In "Apply to all" mode:
- Replace rigid `Row` with `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth())`.
- Render `FilterChip` items for `listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN)`.
- If viewport width or font scaling requires wrapping, chips wrap as complete, intact pills to the next row (Rule 54), eliminating vertical 1-character text crushing.

### 5.4 Neutral Default Sport Preselection
- Change initial state:
  ```kotlin
  var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
  ```
- Defaults to "Other" / "Andere", matching `GpxRouteImporter` and `RoutesViewModel.importRoutesBatch` defaults.

### 5.5 Visual Architecture Comparison

````carousel
```text
BEFORE (ATT-4284 in AlertDialog):
┌──────────────────────────────────────┐
│ Import 5 Routes                      │
│ ┌──────────────────┬───────────────┐ │
│ │ Auf alle anwen   │ Individuell a │ │ <- Mid-word truncation!
│ └──────────────────┴───────────────┘ │
│ Sportart:                            │
│ ┌──────────┐ ┌──────────┐ ┌─┐        │
│ │ 🚴 Radeln│ │ 🏃 Laufen│ │A│        │ <- "Andere" crushed
│ └──────────┘ └──────────┘ │n│        │    into 1 char/line!
│                           │d│        │
│                           │e│        │
│                           │r│        │
│                           │e│        │
│                           └─┘        │
│                  [Abbrechen] [Import]│
└──────────────────────────────────────┘
```
<!-- slide -->
```text
AFTER (ATT-4372 in AppModalBottomSheet):
╭──────────────────────────────────────────────────╮
│                      ───                         │ <- MinimumDragHandle
│ 5 Routen importieren                           ✕ │
│                                                  │
│ ┌───────────────────────┬──────────────────────┐ │
│ │   Auf alle anwenden   │ Individuell anpassen │ │ <- Full width, zero clipping!
│ └───────────────────────┴──────────────────────┘ │
│                                                  │
│ Sportart:                                        │
│ ┌───────────────┐ ┌───────────────┐ ┌──────────┐ │
│ │ ⚪ Andere (●) │ │  🚴 Radeln    │ │ 🏃 Laufen│ │ <- Preselects Other;
│ └───────────────┘ └───────────────┘ └──────────┘ │    FlowRow wraps cleanly!
│                                                  │
│                        [ Abbrechen ]  [ Import ] │
╰──────────────────────────────────────────────────╯
```
````

---

## 6. System Invariants & Risk Assessment

### Core Invariants
1. **Call Site API & Flow Compatibility**: `RoutesScreen` and `GpxImportActivity` invocation syntax remains identical. Mode selection (`BatchSportMode.ALL` vs `BatchSportMode.INDIVIDUAL`) and sport assignment behavior remain unchanged.
2. **Single-File Import Invariant**: Single GPX/TCX file selection continues to bypass the batch prompt and open `EditRouteScreen` directly.
3. **9-Language Localization Parity**: Existing localized tokens (`batch_import_dialog_title_format`, `batch_import_mode_all`, `batch_import_mode_individual`, `batch_import_individual_hint`, `batch_import_action_import`, `batch_import_action_continue`, `cancel`, `route_sport_type`, `sport_type_other`, `sport_type_bike`, `sport_type_run`) across all 9 canonical languages remain preserved with zero missing strings.
4. **Clean-Room Test Pass Rate**: Full unit test suite under `./gradlew testDebugUnitTest` must pass with 100% success rate and zero regressions.
5. **Human Decision Gate**: Parent ticket `ATT-4372` Human Decision Gate remains strictly guarded; terminal state for the agent is `Final Review (Human)`.

### Risk Rating: **LOW**
- **Technical Justification**:
  - Scope is strictly bounded to the presentation layer of `BatchImportSportTypeDialog.kt`.
  - No database schema migrations, entity changes, or ViewModel business logic modifications.
  - Reuses established, battle-tested `AppModalBottomSheet` and `FlowRow` components.
  - Deterministic contract and unit tests in `BatchImportSportTypeDialogTest.kt` provide direct verification.
