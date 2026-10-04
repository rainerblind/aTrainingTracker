# Stage 2: Requirement & Test Specification - ATT-2059: Reduce Card Spacing and Compact Inactive Items on Equipment Screen

**Ticket**: [ATT-2059](https://rainerblind.atlassian.net/browse/ATT-2059)  
**Sub-task**: [ATT-2221](https://rainerblind.atlassian.net/browse/ATT-2221) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2059`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (`REQ-UI-257`)

| Field | Specification |
| :--- | :--- |
| **Requirement ID** | `REQ-UI-257` |
| **Title** | **Compact Equipment Card Layout & Harmonized Inter-Card Spacing.** |
| **Category** | User Interface / Material 3 / Layout & Typography |
| **Scope** | `EquipmentTabsScreen.kt` |
| **Status** | Specified |

### Clause Breakdown & Architectural Constraints

1. **Harmonized Inter-Card Spacing in `EquipmentList`**:
   - The visual gap between consecutive equipment cards rendered in `LazyColumn` SHALL be standardized to a tight, cohesive spacing between 6.dp and 8.dp.
   - `EquipmentList` SHALL configure `verticalArrangement = Arrangement.spacedBy(6.dp)` (or `8.dp`).
   - The card container in `EquipmentItem` (`MappableListItem`) SHALL eliminate vertical outer margins (`padding(horizontal = 4.dp, vertical = 0.dp)`), eliminating double-margin compounding and preventing 20.dp inter-card gaps.
2. **Adaptive Compact Presentation for Inactive Equipment**:
   - Equipment items without recorded workouts (`item.statsData.totalWorkouts == 0`) and/or retired items (`item.isRetired == true`) SHALL render in a streamlined compact format:
     - The item name SHALL scale proportionally to `MaterialTheme.typography.titleMedium` (16sp) rather than `titleLarge` (22sp).
     - The card container internal vertical padding SHALL be reduced from `12.dp` to `8.dp`.
     - Spacing between metadata rows (Strava, sport types, sensors) SHALL be standardized to `4.dp` (correcting line 481's horizontal spacer bug).
     - The empty stats summary block SHALL remain omitted.
3. **Visual Subordination for Retired Equipment**:
   - When `item.isRetired == true`, `MappableListItem` SHALL render with subtle visual subordination using `alpha = 0.75f` while preserving the localized "Ruhestand" / "Retired" badge.
4. **Touch-Target & Interaction Invariants**:
   - The clickable card container SHALL maintain a minimum vertical touch target height of at least 48.dp (meeting WCAG 2.1 Level AA standards).
   - Single-tap (`onClick = onConfigClick`) SHALL launch `EditEquipmentDialog`.
   - Long-press (`onLongClick`) SHALL anchor `DropdownMenu` strictly at Top-Left (`Alignment.TopStart`) presenting exclusively the deletion action (`REQ-UI-061`).
5. **Preserved System Invariants**:
   - Database persistence (`EquipmentDbHelper`), active gear stats visualization (`StatsSummaryBlock`, `UsageItem`), `REQ-UI-061` delete contract, and 100% clean-room test pass rate MUST NOT be compromised.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement: `REQ-UI-257` (*Compact Equipment Card Layout & Harmonized Inter-Card Spacing*).
   - Complements `REQ-UI-017` (*Standardized list items and placeholders*), `REQ-UI-061` (*Unified Deletion UI & Universal Delete-Only Long-Press Contract*), and `REQ-SET-002` (*Equipment management with odometer*).
2. **Historical Origin & Commit Trace**:
   - Commit `ceaf4070` (ATT-238 / ATT-1479) and commit `48d0bdc8` (ATT-919) introduced `MappableListItem` and list spacing.
3. **Root Reason for Existing Formulation**:
   - `MappableListItem` was originally created with generic 4.dp all-around outer padding. When combined with `LazyColumn`'s `Arrangement.spacedBy(12.dp)`, the two margins unintentionally compounded into 20.dp.
4. **Preservation of Core Invariants**:
   - All interactive callback contracts (`onConfigClick`, `onStatsClick`, `onDelete`) remain identical.
   - 9-language translation parity is preserved; no new translation keys are required.

---

## 3. Acceptance Criteria (Given-When-Then)

### AC-1: Harmonized Inter-Card Spacing
- **Given** multiple equipment cards in the Bikes or Shoes tab of `EquipmentTabsScreen`,
- **When** the list is displayed,
- **Then** the visual spacing between cards SHALL be between 6.dp and 8.dp, and zero cards SHALL exhibit 20.dp gaps.

### AC-2: Adaptive Compact Presentation for Inactive Equipment
- **Given** an equipment item with 0 recorded workouts (`statsData.totalWorkouts == 0`),
- **When** the card is rendered,
- **Then** the title SHALL use `titleMedium`, inner vertical padding SHALL be `8.dp`, and stats summary block SHALL be omitted.

### AC-3: Visual Subordination of Retired Gear
- **Given** an equipment item marked as retired (`isRetired == true`),
- **When** rendered,
- **Then** the card container SHALL render with `0.75f` alpha and retain the localized "Ruhestand" / "Retired" badge.

### AC-4: Touch-Target & Interaction Integrity
- **Given** any equipment card (active, 0-workout, or retired),
- **When** tapped,
- **Then** `onConfigClick` is invoked, and when long-pressed, the Top-Left delete context menu appears with a minimum touch height of at least 48.dp.

---

## 4. Test Specification (`TST-UI-216`)

### Test Case 1: Layout Metrics & Spacing Contract Test (`EquipmentCardSpacingContractTest.kt`)
- Verify `EquipmentList` configures `verticalArrangement` with compact spacing (between 6.dp and 8.dp).
- Verify `MappableListItem` inside `EquipmentItem` has `vertical = 0.dp` margin to prevent double-gap compounding.
- Verify minimum card touch target height satisfies WCAG accessibility (>= 48.dp).

### Test Case 2: Adaptive Compact Presentation Test (`EquipmentCompactCardTest.kt`)
- Verify 0-workout items (`totalWorkouts == 0`) resolve compact typography (`titleMedium`) and vertical padding (`8.dp`).
- Verify active items with workouts (`totalWorkouts > 0`) retain standard typography (`titleLarge`), 12.dp padding, and stats summary block.
- Verify retired items (`isRetired == true`) apply `0.75f` alpha to `MappableListItem`.

### Test Case 3: Interactive Invariants Test
- Verify single-tap triggers `onConfigClick`.
- Verify long-press triggers delete dropdown menu anchored at `TopStart` per `REQ-UI-061`.

### Test Case 4: Clean-Room Full Suite Regression
- Execute `./gradlew testDebugUnitTest` and verify 100% pass rate across the complete test suite.

---

## 5. Traceability Matrix

| Test ID | Method / Scope | Verified Requirement | Status |
| :--- | :--- | :--- | :--- |
| `TST-UI-216.1` | `EquipmentCardSpacingContractTest` (Spacing metrics, zero margin compounding, >= 48dp target) | `REQ-UI-257.1`, `REQ-UI-257.4` | Specified |
| `TST-UI-216.2` | `EquipmentCompactCardTest` (0-workout titleMedium, 8dp padding, 0.75f alpha on retired) | `REQ-UI-257.2`, `REQ-UI-257.3` | Specified |
| `TST-UI-216.3` | `EquipmentItemInteractionTest` (Single tap edit, long press TopStart delete) | `REQ-UI-257.4`, `REQ-UI-061` | Specified |
| `TST-UI-216.4` | Full clean-room regression (`./gradlew testDebugUnitTest`) | `REQ-UI-257`, `REQ-SET-002` | Specified |
