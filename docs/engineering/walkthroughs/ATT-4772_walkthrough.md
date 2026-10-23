# Stage 5: Walkthrough & Verification - ATT-4772: Suppress Redundant Vector Polylines in PeriodSummaryCard Mini-Map

**Ticket**: [ATT-4772](https://rainerblind.atlassian.net/browse/ATT-4772)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10`  
**Requirement Mapping**: `REQ-PER-019.4` (amending `REQ-PER-012`) | **Test Mapping**: `TST-PER-025.7`  
**Branch**: `feature/ATT-4772`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Executive Summary & Verification Overview

`ATT-4772` eliminates redundant vector `Polyline` composable generation underneath raster heatmap layers on `PeriodSummaryCard` mini-maps.
* **Problem**: In Month and Year cards with $> 30$ workouts, drawing hundreds of solid vector `Polyline`s underneath `TileOverlay` increased JNI allocation overhead and washed out translucent heatmap colors.
* **Solution**:
  1. Added `renderVectorPolylines: Boolean = true` to `PeriodCardMapState` in `PeriodMapUtils.kt`, evaluating `visuals?.heatmapProvider == null || activePaths.size <= MAX_PERIOD_VECTOR_TRACKS`.
  2. Gated `allPaths.forEach { Polyline(...) }` in `PeriodMultiWorkoutMap` (`PeriodSummaryCard.kt`) with `shouldRenderPolylines`.
  3. Added unit tests in `PeriodCardMapStateTest.kt` verifying $> 30$ suppression, $\le 30$ retention, Day card retention, and `summary.polylines` priority (`TST-PER-025.7`).
* **Verification**: Targeted unit tests passed (100%), governance verified, clean on-device Pixel 10 rendering with zero crash logcat entries.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PER-019.4` | `TST-PER-025.7a` | Unit Test (`> 30` suppression with heatmap) | **PASSED** | `Verified` |
| `REQ-PER-019.4` | `TST-PER-025.7b` | Unit Test (`<= 30` retention & Day cards) | **PASSED** | `Verified` |
| `REQ-PER-019.4` | `TST-PER-025.7c` | Unit Test (`summary.polylines` priority) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PER-025.6` | Tier 1 Unit Test Suite (`ui.aftermath.periodlist.*`) | **PASSED** (100%) | `Verified` |
| `REQ-UI-001` | `ThemeAudit` | `ThemeColorCentralizationAuditTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

```text
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugJavaWithJavac UP-TO-DATE
> Task :app:testDebugUnitTest FROM-CACHE
BUILD SUCCESSFUL in 1s
32 actionable tasks: 1 from cache, 31 up-to-date
Zero requirement modifications detected.
PASS: Archaeology section is structurally valid.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Deployed debug build (`./gradlew installDebug`) to physical Pixel 10 (Android 16, build `26020DLCR002FL`). Verified `com.atrainingtracker.debug` on Periods screen:
* **Month & Year Cards**: Heatmap renders cleanly across multi-workout periods without solid vector line blowout.
* **Logcat Inspection**: `adb logcat -b crash -d` returned 0 entries.
* **Screenshots Uploaded to ATT-4772**:
  * `docs/engineering/walkthroughs/img/ATT-4772_month_preview.png` (Month card preview)
  * `docs/engineering/walkthroughs/img/ATT-4772_year_card.png` (Year card preview)

### Visual Consistency (Rule 23)
* **Checked against `docs/design_guidelines.md` §5**: Shapes: 12dp card corners; Spacing: standard 16dp horizontal padding; Colors: MaterialTheme surface and TTColor dark canvas; Typography: standard M3 labels; Placement: intact mini-map viewport.
* **Deviations**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Schema Mutations**: No changes to `PeriodSummaries.db` or queries.
2. **Single Source of Truth (DRY)**: Reuses `MAX_PERIOD_VECTOR_TRACKS = 30` from `PeriodData.kt`.
3. **Day Card Polyline Crispness**: Day cards continue to render crisp vector polylines exclusively.
4. **Living Documentation**: `REQ-PER-019.4` and `TST-PER-025.7` verified.
