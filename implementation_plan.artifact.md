# Implementation Plan - SCRUM-125 Refinement: Balanced Sub-Sport Scaling

Refine the visual hierarchy in the Period Summary by using a balanced scale for sub-sport details: `bodySmall` for typography and `14.dp` for icons.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-028` (Hierarchical Detailed Metrics)
- **Test ID**: `TST-UI-038` (Detailed Metric Scale)

## 2. Impact Analysis
- **UI Components**: `PeriodSummaryCard.kt`.
- **Typographic Scale**:
    - Primary aggregates: `titleMedium` (16sp)
    - Sub-sport details: `bodySmall` (12sp) - *Refinement: Larger than labelSmall but smaller than primary.*
- **Icon Scale**: `14.dp` for sub-sports to maintain subordinate status.
- **Side Effects**: None.

## 3. Proposed Changes

### 3.1 Sub-Sport Typography Refinement (`PeriodSummaryCard.kt`)
- Update **`CompactMetricRow`**:
    - Set `valueStyle = MaterialTheme.typography.bodySmall`.
    - Keep `iconSize = 14.dp`.
- Update **Longest Workout Section**:
    - Set `valueStyle = MaterialTheme.typography.bodySmall` for all metric items.
    - Keep `iconSize = 14.dp`.

## 4. Verification Plan
- **Build**: Ensure successful compilation.
- **Visual Audit**:
    1. Verify that sub-sport values (e.g., "35,11 km") are clearly legible but noticeably smaller than the main sport totals.
    2. Confirm icons at 14dp feel subordinate.
- **Compose Previews**: Audit `PreviewPeriodSummary` for hierarchical balance.
