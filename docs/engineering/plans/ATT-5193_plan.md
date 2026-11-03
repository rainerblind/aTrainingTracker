# Stage 3: Implementation Plan - ATT-5193: Fix Day-of-Week Label Clipping & Add Sunday in YearlyContributionGrid

**Ticket**: [ATT-5193](https://rainerblind.atlassian.net/browse/ATT-5193)  
**Sub-task**: [ATT-5246](https://rainerblind.atlassian.net/browse/ATT-5246) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PER-025` (`REQ-PER-025.3`)  
**Test Mapping**: `TST-PER-031` (`TST-PER-031.10`)  
**Branch**: `feature/ATT-5193`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Description & Background
In `YearlyContributionGrid.kt`, day-of-week labels inside `Box(Modifier.height(cellSize = 11.dp))` suffer from vertical glyph clipping (e.g. `F` / Friday) because `Text` inherits `labelSmall` default `lineHeight = 16.sp` with Android platform font padding enabled. Additionally, only Mon, Wed, and Fri are labeled, omitting Sunday and leaving the bottom row of the 7-row matrix unanchored. This plan bounds micro-typography line height per Design Guidelines Section 5.15 and extends the label cadence to include Sunday.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-025.3` (*Matrix Dimensions, Micro-Typography Bounding & Day-of-Week Label Cadence*)
* **Test Mapping**: `TST-PER-031.10` in `YearlyContributionGridTest.kt` (pure functional testing per Section 5.4).
* **Chesterton's Fence Audit**: Amends `REQ-PER-025.3`. Preserves $7 \times 53$ matrix dimensions, $11\text{ dp}$ cell height, $2\text{ dp}$ cell spacing, quartile shading, and localized `TextStyle.NARROW` day initial formatting.

---

## 3. System Invariants & Preserved Behavior
1. **Section 1.10 Compliance**: No Compose snapshot state writes during composition.
2. **Section 5.4 Compliance**: Zero source-text grepping in UI tests; cadence tested via pure helper function `shouldShowDayOfWeekLabel(rowIdx)`.
3. **Section 5.15 Compliance**: Explicit `lineHeight = fontSize` ($9\text{ sp}$) and `PlatformTextStyle(includeFontPadding = false)` on labels $\le 10\text{ sp}$ inside fixed-height boxes.
4. **Rule 4 Code Lock**: Zero production code edits before Gate 3 passes `check-gate`.
5. **Geometry Stability**: Zero layout shift; day label column height remains strictly $7 \times 11\text{ dp} + 6 \times 2\text{ dp} = 89\text{ dp}$.

---

## 4. Proposed Architectural Changes

### Component 1: Pure Cadence Helper (`YearlyContributionGrid.kt`)
* Extract pure function:
  ```kotlin
  fun shouldShowDayOfWeekLabel(rowIdx: Int): Boolean = rowIdx in listOf(0, 2, 4, 6)
  ```
  Returns `true` for Monday (0), Wednesday (2), Friday (4), and Sunday (6), establishing an even 2-day cadence.

### Component 2: Micro-Typography Styling (`YearlyContributionGrid.kt`)
* In day label column (lines 283–299), use `shouldShowDayOfWeekLabel(idx)` and style `Text` with `lineHeight = 9.sp`, `PlatformTextStyle(includeFontPadding = false)`, and `wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)`.

### UI Consistency (Rule 23)
* **Reference**: `YearlyContributionGrid.kt` day-of-week label column.
* **Tokens**: `MaterialTheme.typography.labelSmall`, `MaterialTheme.colorScheme.onSurfaceVariant`.
* **New one-off styles**: None; enforces Section 5.15 design standard across micro-typography.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Helper & Update Styling in YearlyContributionGrid.kt
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/YearlyContributionGrid.kt`
* Changes: Add `shouldShowDayOfWeekLabel`, apply `lineHeight = 9.sp`, `PlatformTextStyle(includeFontPadding = false)`, and `wrapContentHeight(unbounded = true)`.

### Step 2: Implement Unit Test in YearlyContributionGridTest.kt
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/YearlyContributionGridTest.kt`
* Changes: Add `testDayOfWeekLabelCadence_showsMonWedFriSun` verifying `shouldShowDayOfWeekLabel` across indices 0..6.

### Step 3: Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.YearlyContributionGridTest" --max-workers=2`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted `YearlyContributionGridTest` and `verify_requirement_governance.py`.
* **Rollback**: Clean git branch revert via `git restore app/ docs/`.
