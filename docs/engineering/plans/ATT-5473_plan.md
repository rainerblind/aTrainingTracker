# Stage 3: Implementation Plan - ATT-5473: WorkoutPhotoStrip Neutral Styling, Unclipped Add Tile & Stacked Preview Actions

**Ticket**: [ATT-5473](https://rainerblind.atlassian.net/browse/ATT-5473)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5473`  
**Requirement Mapping**: `REQ-UI-448` (*Workout Photos Strip & Viewer Bottom Sheet*) | **Test Mapping**: `TST-UI-448`, `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
* In `WorkoutSummary.kt`, `WorkoutPhotoStrip` uses electric blue (`colorScheme.primary`) for header icons and the "Add Photos" tile, clashing with the card's neutral `onSurfaceVariant` telemetry rows.
* Inside the square add tile, German `"Fotos hinzufügen"` clips to `"Fotos "` under `maxLines = 1`.
* `WorkoutPhotoPreviewBottomSheet.kt` packs `"Foto löschen"` and `"Mit Track-Overlay teilen"` horizontally in `actions`, causing text cramp in German.
* Solution updates photo strip and tile colors to `onSurfaceVariant`, replaces the clipped label with a clean centered `AddPhotoAlternate` icon, adds a standard section `HorizontalDivider`, and refactors preview actions into stacked full-width CTAs per Rule 73.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-448.1` (Photo Strip & Summary Integration), `REQ-UI-448.2` (Preview Bottom Sheet Actions).
* **Test Mapping**: `TST-UI-448.1` (Neutral styling & unclipped iconography contract), `TST-UI-448.2` (Rule 73 stacked preview CTAs contract).

---

## 3. System Invariants & Preserved Behavior
1. **Touch Target Fidelity**: Minimum $\ge 48\text{dp}$ touch targets on the Add Tile and full-width buttons.
2. **Workflow Invariance**: Photo attachment, time picker launch (`ATT-5475`), fullscreen overlay share, and deletion confirmation dialog remain 100% functionally preserved.
3. **9-Language Parity**: Zero new string keys; existing strings in `res/values*/strings.xml` remain fully reused without entity or specifier changes.

---

## 4. Proposed Architectural Changes
* **Photo Strip Neutral Harmonization (`WorkoutPhotoStrip.kt`)**: Tint camera header icon and add tile icon with `MaterialTheme.colorScheme.onSurfaceVariant`. Header text uses `colorScheme.onSurfaceVariant`.
* **Centered Add Tile Iconography (`WorkoutPhotoStrip.kt`)**: Remove truncated single-line `Text` from the square `OutlinedCard` add tile. Render centered `Icons.Default.AddPhotoAlternate` (28dp) with neutral tint and accessible `contentDescription = stringResource(R.string.workout_photos_add)`.
* **Summary Section Divider (`WorkoutSummary.kt`)**: Insert standard `HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)` preceding `WorkoutPhotoStrip` under `WorkoutSectionType.PHOTOS`.
* **Stacked Preview Actions (`WorkoutPhotoPreviewBottomSheet.kt`)**: In `AppModalBottomSheet`, remove horizontal `actions` row and place stacked full-width buttons at the bottom of the `content` Column (`fillMaxWidth()`, 8dp spacing): primary `Button` ("Mit Track-Overlay teilen") above secondary `OutlinedButton` ("Foto löschen", error tint).
* **UI Consistency**: Reuses `MaterialTheme.colorScheme.onSurfaceVariant`, `outlineVariant`, `AppModalBottomSheet`, and standard 8dp/12dp spacing. Follows Rule 73 stacked CTA pattern (modeled on `FirstJubileeCelebrationDialog.kt`). Zero one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Photo Strip Styling & Remove Clipped Label
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoStrip.kt`
* Change header camera icon and add tile icon tint to `onSurfaceVariant`; replace column text in add tile with centered 28dp `AddPhotoAlternate` icon.

### Step 2: Add Section Divider in WorkoutSummary
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* Insert standard `HorizontalDivider` preceding `WorkoutPhotoStrip` inside `WorkoutSectionType.PHOTOS` block.

### Step 3: Implement Stacked Preview Action Buttons
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoPreviewBottomSheet.kt`
* Move share and delete buttons to bottom of `content` slot as stacked `fillMaxWidth()` CTAs; set `actions = null`.

### Step 4: Author Contract Verification Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoUiContractTest.kt`
* Add assertions verifying neutral `onSurfaceVariant` tints, absence of clipped tile text, divider presence in `WorkoutSummary.kt`, and stacked `fillMaxWidth` buttons in `WorkoutPhotoPreviewBottomSheet.kt`.

### Step 5: Test Execution & Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.photos.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit/contract tests, verify AAPT2 resources, and run `verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feature/ATT-5473` allows immediate discard without impacting sprint integration.
