# Stage 3 Implementation Plan: ATT-3776

## 1. Architectural Overview (SWE.2)
- **Target Component**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
- **Target Requirement & Test**: `REQ-UI-384` / `TST-UI-347`
- **Design Pattern**:
  - Replace the Material 3 `FilterChip` + `leadingIcon` loop in `SettingsScreenContent` (lines 777–796) with a compact, reusable composable `ZonePreviewPill` using Material 3 selectable `Surface(selected = selected, onClick = onClick, ...)`:
    ```kotlin
    @Composable
    fun ZonePreviewPill(
        label: String,
        zoneColor: androidx.compose.ui.graphics.Color,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = modifier.heightIn(min = 36.dp),
            shape = RoundedCornerShape(8.dp),
            color = zoneColor.copy(alpha = if (selected) 0.28f else 0.12f),
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) zoneColor else zoneColor.copy(alpha = 0.6f)
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
    ```

## 2. UI Consistency (Rule 23 & Rule 38)
- **Closest Existing Reference**: `ZoneRow` in `ZonesSettingsActivity.kt` (lines 907–976), which tints each zone card background using `containerColor.copy(alpha = 0.12f)` and accents with the solid zone color.
- **Shapes & Spacing (`docs/design_guidelines.md` Sec 5.2, 5.3, 5.6)**:
  - Small elements / chips use `RoundedCornerShape(8.dp)`.
  - Row inter-item spacing uses `6.dp` (within `4.dp`–`8.dp` scale per Section 5.2).
  - Strictly enforces `maxLines = 1, softWrap = false` with no `leadingIcon` per Rule 38 (`docs/design_guidelines.md` Section 5.6).

## 3. Atomic Implementation Steps

### Step 1: Implement `ZonePreviewPill` & Replace `FilterChip` in `ZonesSettingsActivity.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
- **Action**:
  - Add imports for `BorderStroke`, `RoundedCornerShape`, and `FontWeight`.
  - Replace the `FilterChip` loop in `SettingsScreenContent` with `ZonePreviewPill(...)`.
  - Add KDoc on `ZonePreviewPill` referencing `REQ-UI-384` and Rule 38.

### Step 2: Create `ZonesSettingsPreviewPillsContractTest.kt` (`TST-UI-347`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZonesSettingsPreviewPillsContractTest.kt`
- **Action**:
  - Verify `ZonesSettingsActivity.kt` declares and invokes `ZonePreviewPill`, contains zero `FilterChip(` invocations, and enforces selectable `Surface(selected = selected, onClick = onClick, ...)`, `RoundedCornerShape(8.dp)`, `0.28f` / `0.12f` zone tint alpha, `BorderStroke` (`2.dp` / `1.dp`), `maxLines = 1` + `softWrap = false`, and 130% font-scale width budget + WCAG AA contrast $\ge 4.5:1$.
- **Targeted Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*"
  ```

## 4. Invariants & Rollback Safety
- Clicking any pill (`1..zoneCount`) continues to update `previewZone` and immediately update the `SensorFieldView` preview tile below it.
- Selectable `Surface` semantics preserve accessibility announcements.
- 100% clean-room unit test pass rate preserved.
