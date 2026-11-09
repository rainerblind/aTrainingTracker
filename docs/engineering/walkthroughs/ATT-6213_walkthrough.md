# Stage 5: Walkthrough & Verification - ATT-6213: Reorderable WorkoutDetails

**Ticket**: [ATT-6213](https://rainerblind.atlassian.net/browse/ATT-6213)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-UI-499` (*Reorderable WorkoutDetails & Photos-First Card Ordering*)  
**Test Mapping**: `TST-UI-499`  
**Branch**: `feature/ATT-6213`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
ATT-6213 unifies `WorkoutSectionType` with `WorkoutDetailSectionType`, making `WorkoutDetails` (*Zeit aktiv / Distanz / Höhenmeter*) an independent reorderable section in `WorkoutSummary` cards instead of being hardcoded above the section iteration loop. This allows athletes to position `PHOTOS` directly below `WorkoutHeader` and above `WorkoutDetails`. The implementation provides backward-compatible self-healing preference migration (`PHOTOS` at index 0 $\rightarrow$ `DETAILS` at index 1; otherwise `DETAILS` at index 0), clean single-divider layout, and full settings matrix integration while maintaining `WorkoutMasksAndCardsSection.kt` strictly under 400 lines (395 lines).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-499.1` | `TST-UI-499.1` | Unit: Enum parity & 12-item `DEFAULT_ORDER` | **PASSED** | `Verified` |
| `REQ-UI-499.2` | `TST-UI-499.2` | Unit: Self-healing migration (`PHOTOS` vs `DETAILS`) | **PASSED** | `Verified` |
| `REQ-UI-499.3` | `TST-UI-499.3` | Unit: `showDetails` persistence & toggling | **PASSED** | `Verified` |
| `REQ-UI-499.4` | `TST-UI-499.4` | Contract: `WorkoutSummary` dynamic execution & dividers | **PASSED** | `Verified` |
| `REQ-UI-499.5` | `TST-UI-499.5` | Contract: `WorkoutMasksAndCardsSection` row & line limit | **PASSED** | `Verified` |
| `REQ-UI-499.6` | `TST-UI-499.6` | Contract: `TrackOnMapScreen` exhaustive caller handling | **PASSED** | `Verified` |
| `REQ-LOC-001` | `TST-UI-499.5` | Localization Parity (`TranslationParityTest`) across 9 locales | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-499.6` | Tier 1 Targeted Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Executed via `./gradlew testDebugUnitTest` under POSIX lock without `--rerun-tasks`:
* Scope: `ui.aftermath.*`, `ui.settings.*`, `settings.*`, `localization.TranslationParityTest`, `ui.theme.ThemeColorCentralizationAuditTest`, `ui.components.core.*`, `build.*`
```text
BUILD SUCCESSFUL in 2s
32 actionable tasks: 1 from cache, 31 up-to-date
818 tests completed, 0 failed, 0 skipped
```

### Living Documentation & Governance Check
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Net-new requirement(s) detected: REQ-UI-499. Bypassing archaeology check cleanly.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* No physical device connected in `adb devices`. Emulators are strictly prohibited per Rule 79. Verified via comprehensive automated UI structural contract tests (`WorkoutSummaryDynamicOrderContractTest`, `WorkoutPhotoUiContractTest`, `WorkoutMasksAndCardsLayoutTest`, `TrackOnMapScreenSectionSlottingContractTest`).

---

## 5. Invariant & Governance Verification
1. **Top Identity Anchor Invariant**: `WorkoutHeader` remains permanently anchored at the top of every card.
2. **Visual Fidelity for Existing Installs**: Default cards render identically (`Header -> Details -> ...`). Stored orders without `DETAILS` migrate seamlessly without displacement to the bottom.
3. **Modularity Constraint**: `WorkoutMasksAndCardsSection.kt` is 395 lines (strictly $< 400$ lines).
4. **Zero Production Regressions**: 100% pass rate across all targeted tests and governance checks.
