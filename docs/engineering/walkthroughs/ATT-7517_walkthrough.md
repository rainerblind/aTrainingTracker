# Stage 5: Walkthrough & Verification - ATT-7517: Equal-Width Workout Classification Tiles & Grouped Cloud Sync Card

**Ticket**: [ATT-7517](https://rainerblind.atlassian.net/browse/ATT-7517)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-REV-013` (`REQ-REV-013.1`–`REQ-REV-013.4`)  
**Test Mapping**: `TST-REV-014` (`TST-REV-014.1`–`TST-REV-014.3`)  
**Branch**: `feature/ATT-7517`  
**Author**: AI Agent 2 (Flash Agent)  
**Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview
This ticket modernizes `EditWorkoutScreen.kt` classification metadata and external sync controls:
1. **Equal-Width Classification Tiles (Variant B)**: Replaced two fragmented checkbox rows with a single `Row` hosting 3 equal-width toggle tiles (`Commute`, `Indoor`, `Race`) using `Modifier.weight(1f)`, `RoundedCornerShape(8.dp)`, min height `42.dp`, and full-surface `Modifier.toggleable(role = Role.Checkbox)`. Commute uses `Icons.Default.Commute`, Indoor uses `Icons.Default.Home`, and Race uses `Icons.Filled.EmojiEvents` styled when active with `tertiaryContainer` / `tertiary` border matching `WorkoutHeader.kt`.
2. **ViewModel Mutual Exclusivity**: Enforced mutual exclusivity in `EditWorkoutViewModel` between `Commute` and `Indoor`, while keeping `Race` independently combinable (supporting virtual / Zwift races).
3. **Grouped Cloud Sync Card**: Encapsulated Strava and Health Connect rows inside an outlined `Surface(shape = RoundedCornerShape(10.dp))` with full-row `Modifier.toggleable` tap targets, compact trailing checkboxes, official brand assets (`logo_square_strava` with `0.38f` disabled alpha, `ic_health_connect`), and `Color.Unspecified` tint.
4. **9-Language Localization**: Defined concise `indoor_short` across all 9 canonical locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) with zero emojis.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-013.1` | `TST-REV-014.2` | Contract (`EditWorkoutClassificationLayoutContractTest`) | **PASSED** | `Verified` |
| `REQ-REV-013.2` | `TST-REV-014.1` | Unit Test (`EditWorkoutViewModelTest`) | **PASSED** | `Verified` |
| `REQ-REV-013.3` | `TST-REV-014.2` | Contract (`EditWorkoutClassificationLayoutContractTest`) | **PASSED** | `Verified` |
| `REQ-REV-013.4` | `TST-REV-014.3` | Localization (`EditWorkoutStringsParityContractTest`, `TranslationParityTest`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Suite
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
BUILD SUCCESSFUL in 1m 31s
33 actionable tasks: 12 executed, 21 up-to-date
```

### Living Documentation & Governance Verification
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: All REQ and TST heading IDs are unique across living documentation shards.
PASS: All cited REQ and TST references resolve to authentic headings.
PASS: Zero ungrounded [Verified] requirements detected.
PASS: Zero Composable islands detected.
PASS: Repository encapsulation verified.
```

---

## 4. Invariants & Preserved Behavior
1. **Unconditional Field Presentation**: Preserved unconditional form visibility per `REQ-UI-239` (`EditWorkoutFieldsLayoutTest`).
2. **Data & Dispatch Integrity**: `WorkoutData` persistence, Strava disabled state (`0.38f` alpha when `!stravaMappingAvailable`), and Health Connect manual sync dispatch preserved.
3. **Scope Bounding Invariance**: Zero modifications to `WorkoutRpeFeelSection.kt` (Borg CR10 and Feel cards strictly bounded to ATT-7515).
