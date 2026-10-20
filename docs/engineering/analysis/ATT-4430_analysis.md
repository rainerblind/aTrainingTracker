# Stage 1 Analysis: ATT-4430 - [Verbesserung] Order sport type chips in BatchImportSportTypeDialog with Other on the far right (Bike, Run, Other)

**Ticket**: [ATT-4430](https://rainerblind.atlassian.net/browse/ATT-4430)  
**Sub-task**: [ATT-4444](https://rainerblind.atlassian.net/browse/ATT-4444) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.3`  
**Branch**: `feature/ATT-4430`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

### Current Behavior
In Sprint `2026-43.2`, ticket `ATT-4372` resolved responsive layout clipping and chip distortion in `BatchImportSportTypeDialog` (`ui/routes/BatchImportSportTypeDialog.kt`):
- Migrated the dialog container from `AlertDialog` to `AppModalBottomSheet`.
- Configured multi-line centered text on `SingleChoiceSegmentedButtonRow` to prevent mid-word truncation in verbose locales.
- Placed sport selection chips into `FlowRow` per **Rule 54** (`docs/design_guidelines.md` Sec 5.13) to prevent single-character vertical text stacking on narrow containers.
- Replaced the hardcoded `selectedSport = BSportType.BIKE` with `BSportType.UNKNOWN` ("Other" / "Andere") to establish a neutral default preselection for batch imports.

However, while making `BSportType.UNKNOWN` the default selection, the author in `ATT-4372` also reordered the visual chip list:
```kotlin
listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN).forEach { sport ->
```
This placed the catch-all `Other` (`"Andere"`) chip as the leftmost option in the row.

During the Sprint `2026-43.2` Joint Review of `ATT-4372`, the human reviewer confirmed that the `AppModalBottomSheet` layout and neutral default selection (`BSportType.UNKNOWN`) were much better, but requested one specific UI refinement:
- The catch-all category `"Other"` (`BSportType.UNKNOWN` / `"Andere"`) must always be positioned on the **far right**, with primary concrete sports (`Bike`, `Run`) appearing first.
- This canonical ordering is already established in `EditRouteScreen.kt`:
  ```kotlin
  listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { bSportType ->
  ```
- The inconsistency between `BatchImportSportTypeDialog` (`[Other, Bike, Run]`) and `EditRouteScreen` (`[Bike, Run, Other]`) was codified into **Rule 57** (`docs/design_guidelines.md` Section 1.2):
  > *Across all sport type selector rows (`FilterChip` rows, segmented buttons), specific primary sports (`Run`, `Bike` / `Bike`, `Run`) MUST appear first, and the catch-all `Other` (`BSportType.UNKNOWN` / `"Andere"`) MUST always be positioned on the far right (`[Bike, Run, Other]` or `[Run, Bike, Other]`), even when `Other` is preselected as the default value. Never move `Other` to the leftmost position just because it is the default selection.*

### Expected Behavior
1. **Canonical Chip Ordering (Rule 57)**: `BatchImportSportTypeDialog.kt` orders its sport type `FilterChip` items as `Bike, Run, Other`:
   ```kotlin
   listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)
   ```
   matching `EditRouteScreen.kt`.
2. **Neutral Default Preselection Preserved**: The initial state of `selectedSport` remains strictly `BSportType.UNKNOWN`, ensuring non-cycling tracks are not silently coerced to Bike rides.
3. **Architectural & Contract Test Enforcement**: `BatchImportSportTypeDialogTest.kt` explicitly verifies the canonical ordering token `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)`.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Conflation of Initial Selection State with Visual Layout Hierarchy
In `ATT-4284` (commit `49133ab1`), `BatchImportSportTypeDialog` originally laid out chips as:
```kotlin
var selectedSport by remember { mutableStateOf(BSportType.BIKE) }
...
listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { sport ->
```
Here, `BIKE` was both the first chip and the initial preselection.

In `ATT-4372` (commit `49c12c7a`), the goal was to change the preselected sport to `BSportType.UNKNOWN`. During that implementation, the developer changed line 95 to:
```kotlin
var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
```
and simultaneously changed line 165 to:
```kotlin
listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN).forEach { sport ->
```
This conflated two orthogonal UI concepts:
1. **Selection State**: Which option is active by default when the dialog appears.
2. **Visual Hierarchy & Scanning Flow**: The logical reading order of options presented to the user.

In Western left-to-right reading conventions, users scan categorical lists expecting concrete, highly specific options first (`Bike`, `Run`), followed by generic catch-all/fallback options (`Other`) at the end of the list. Placing `Other` on the far left violates user mental models, especially since `EditRouteScreen.kt` places `Other` on the far right.

### 2.2 Call Site and Subsystem Audit

1. **`BatchImportSportTypeDialog.kt`** (`ui/routes/`):
   - Line 95:
     ```kotlin
     var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
     ```
     $\rightarrow$ **Keep intact** (preserves domain-neutral default).
   - Line 165:
     ```kotlin
     listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN).forEach { sport ->
     ```
     $\rightarrow$ **Update** to `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { sport ->` per Rule 57.

2. **`EditRouteScreen.kt`** (`ui/routes/`):
   - Line 153:
     ```kotlin
     listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { bSportType ->
     ```
     $\rightarrow$ Canonical reference. Already implements `[Bike, Run, Other]`. No changes needed.

3. **Callers of `BatchImportSportTypeDialog`**:
   - `RoutesScreen.kt` (lines 352–365):
     ```kotlin
     if (pendingBatchUris.isNotEmpty()) {
         BatchImportSportTypeDialog(
             routeCount = pendingBatchUris.size,
             onConfirm = { mode, sport -> ... },
             onDismiss = { pendingBatchUris = emptyList() }
         )
     }
     ```
   - `GpxImportActivity.kt` (lines 142–155):
     ```kotlin
     if (showBatchDialog) {
         BatchImportSportTypeDialog(
             routeCount = uris.size,
             onConfirm = { mode, sport -> ... },
             onDismiss = { finish() }
         )
     }
     ```
   - Both callers interact solely with the public parameters `routeCount`, `onConfirm: (BatchSportMode, BSportType) -> Unit`, and `onDismiss: () -> Unit`.
   - Neither caller depends on chip layout order. Public API and behavioral contracts remain 100% backwards-compatible.

4. **Architectural & Contract Test Suite**:
   - `BatchImportSportTypeDialogTest.kt` (lines 111–116):
     ```kotlin
     assertTrue(
         "Must include BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN",
         content.contains("BSportType.UNKNOWN") &&
                 content.contains("BSportType.BIKE") &&
                 content.contains("BSportType.RUN")
     )
     ```
     Currently tests set membership rather than sequence order.
     $\rightarrow$ **Update** to verify exact order:
     ```kotlin
     assertTrue(
         "Must order sport chips as Bike, Run, Other per Rule 57 and EditRouteScreen",
         content.contains("listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)")
     )
     ```

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Goals
1. **Reorder Sport Type Chips in `BatchImportSportTypeDialog.kt`**:
   - Change the `FlowRow` iteration order from `listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN)` to `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)` (matching `EditRouteScreen.kt` and Rule 57).
2. **Preserve Neutral Default Preselection**:
   - Maintain `selectedSport = BSportType.UNKNOWN` as the initial state in `BatchImportSportTypeDialog.kt`.
3. **Contract Test Enforcement**:
   - Update `BatchImportSportTypeDialogTest.kt` to assert the canonical ordering token `listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)`.
4. **Living Documentation Synchronization**:
   - Update `docs/requirements.md` (`REQ-MAP-053.6` / `REQ-RTE-035`) and `docs/tests.md` (`TST-MAP-056.4` / `TST-RTE-038`) to specify the canonical chip ordering with `Other` on the far right.

### 3.2 Out-of-Scope Non-Goals (Scope Bounding)
1. **No Changes to `EditRouteScreen.kt`**:
   - `EditRouteScreen.kt` already adheres to Rule 57 and serves as the canonical reference.
2. **No ViewModel or Importer Changes**:
   - `RoutesViewModel.importRoutesBatch` and `GpxImportViewModel.handleBatchUris` already process `BSportType` correctly. No business logic or pipeline changes are involved.
3. **No Mode Switcher Changes**:
   - The binary mode switcher (`SingleChoiceSegmentedButtonRow`) between "Apply to all" and "Configure individually" remains unmodified.
4. **No Container or Layout Mechanism Changes**:
   - `AppModalBottomSheet` and `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp))` (Rule 54) remain intact.
5. **No String Resource Changes**:
   - All string resources (`route_sport_type`, `sport_type_bike`, `sport_type_run`, `sport_type_other`) exist with 100% parity across all 9 canonical locales.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - Refines `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*), sub-clause `REQ-MAP-053.6` (*Batch Import Sport Type Dialog Component Architecture*) [referenced in ticket as `REQ-RTE-035` / `TST-RTE-038`].
  - Targets:
    - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt`
    - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialogTest.kt`
    - `docs/requirements.md`
    - `docs/tests.md`
* **Historical Origin & Commit Trace**:
  - `ATT-4049` (commit `8c418181`, Sprint 2026-42.10): Introduced headless multi-file route import.
  - `ATT-4284` (commit `49133ab1`, Sprint 2026-43.1): Added `BatchImportSportTypeDialog` with chips `[BIKE, RUN, UNKNOWN]` but default `selectedSport = BSportType.BIKE` in `AlertDialog`.
  - `ATT-4372` (commit `49c12c7a`, Sprint 2026-43.2): Migrated dialog to `AppModalBottomSheet`, resolved segmented button truncation and vertical chip crushing via `FlowRow` (Rule 54), and set default preselection to `BSportType.UNKNOWN`. In doing so, placed `UNKNOWN` first (`[UNKNOWN, BIKE, RUN]`).
  - Sprint 2026-43.2 Joint Review: User clarified that `Other` must remain preselected by default, but chip ordering must place `Other` on the far right (`[BIKE, RUN, UNKNOWN]`), establishing **Rule 57** (`docs/design_guidelines.md` Section 1.2).
* **Root Reason for Existing Formulation**:
  - The implementer of `ATT-4372` reordered the chip list to put `BSportType.UNKNOWN` first because it was the default selection, erroneously assuming the default selection must always occupy the first visual slot.
* **Preservation of Core Invariants**:
  1. Default preselection remains `BSportType.UNKNOWN` (domain neutrality preserved).
  2. All three sport types (`BIKE`, `RUN`, `UNKNOWN`) remain selectable with canonical icons and localized names.
  3. Responsive `FlowRow` layout (Rule 54) and `AppModalBottomSheet` container remain intact.
  4. Call site signatures in `RoutesScreen` and `GpxImportActivity` remain 100% unchanged.
  5. 9-language translation parity maintained.
  6. 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Component Update in `BatchImportSportTypeDialog.kt`
Update the `FlowRow` chip iteration list on line 165:
```kotlin
// BEFORE (ATT-4372):
listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN).forEach { sport ->

// AFTER (ATT-4430 / Rule 57):
listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { sport ->
```
While preserving initial state on line 95:
```kotlin
var selectedSport by remember { mutableStateOf(BSportType.UNKNOWN) }
```

### 5.2 Contract Assertion in `BatchImportSportTypeDialogTest.kt`
Update test assertion in `testBatchImportSportTypeDialog_sourceArchitectureAndTokens`:
```kotlin
// Assert canonical ordering per Rule 57 and EditRouteScreen parity
assertTrue(
    "Must order sport chips as Bike, Run, Other per Rule 57 and EditRouteScreen",
    content.contains("listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)")
)
```

### 5.3 Visual Architecture Comparison

````carousel
```text
BEFORE (ATT-4372: Other on Far Left):
┌────────────────────────────────────────────────────────────┐
│ Import 5 Routes                                         ✕  │
│ ┌────────────────────────────┬───────────────────────────┐ │
│ │     Auf alle anwenden      │   Individuell anpassen    │ │
│ └────────────────────────────┴───────────────────────────┘ │
│                                                            │
│ Sportart:                                                  │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐      │
│ │ ● ❓ Andere   │ │ ○ 🚴 Radeln   │ │ ○ 🏃 Laufen   │      │
│ └───────────────┘ └───────────────┘ └───────────────┘      │
│ ^ "Andere" at index 0 violates Rule 57 & EditRouteScreen   │
│                                                            │
│                                       [Abbrechen] [Import] │
└────────────────────────────────────────────────────────────┘
```
<!-- slide -->
```text
AFTER (ATT-4430: Other on Far Right, Canonical Rule 57):
┌────────────────────────────────────────────────────────────┐
│ Import 5 Routes                                         ✕  │
│ ┌────────────────────────────┬───────────────────────────┐ │
│ │     Auf alle anwenden      │   Individuell anpassen    │ │
│ └────────────────────────────┴───────────────────────────┘ │
│                                                            │
│ Sportart:                                                  │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐      │
│ │ ○ 🚴 Radeln   │ │ ○ 🏃 Laufen   │ │ ● ❓ Andere   │      │
│ └───────────────┘ └───────────────┘ └───────────────┘      │
│ ^ Specific sports first, "Andere" on far right (selected)  │
│                                                            │
│                                       [Abbrechen] [Import] │
└────────────────────────────────────────────────────────────┘
```
````

---

## 6. System Invariants & Risk Assessment

### Core Invariants
1. **Zero Regression**: 100% clean-room test pass rate across `./gradlew testDebugUnitTest`.
2. **Neutral Preselection**: `BSportType.UNKNOWN` remains the initial preselection state when the dialog is opened.
3. **Subsystem Consistency (Rule 57)**: Sport type chip ordering in `BatchImportSportTypeDialog` matches `EditRouteScreen` (`[Bike, Run, Other]`).
4. **Rule 54 Intact**: `FlowRow` with 8dp spacing prevents any text crushing or single-character wrapping.
5. **Call Contract Compatibility**: Public signature and caller usage in `RoutesScreen` and `GpxImportActivity` remain unchanged.
6. **Parent Ticket Gate Governance**: Parent ticket `ATT-4430` ends at `Final Review (Human)`.

### Risk Rating: LOW
- **Blast Radius**: Strictly localized to a single Composable iteration list (`BatchImportSportTypeDialog.kt`) and its companion contract test (`BatchImportSportTypeDialogTest.kt`).
- **Data & Concurrency Safety**: Zero database schema, Room queries, thread dispatchers, or background coroutines are affected.
- **Localization Safety**: Zero string additions or modifications; existing string tokens maintain 100% parity across all 9 canonical locales.
