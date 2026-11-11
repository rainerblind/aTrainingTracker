# Stage 5: Walkthrough & Verification - ATT-6689: Fix Graph-Only Sparkline 0px Collapse in IntrinsicSize.Min Rows & Session Reset of Sparkline Buffers

**Ticket**: [ATT-6689](https://rainerblind.atlassian.net/browse/ATT-6689) | **Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6689`  
**Requirement Mapping**: `REQ-TRK-059.10` | **Test Mapping**: `TST-TRK-059.10` (`TST-TRK-059.10a`, `TST-TRK-059.10b`, `TST-TRK-059.10c`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
* **Graph-Only Layout Collapse Fixed**: In `SensorFieldView.kt`, added `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = true)).weight(1f)` when `!fieldState.showValue && fieldState.showGraph`. Ensures non-zero measured height (`24.dp`, `36.dp`, `52.dp`, `68.dp`) in `Row(Modifier.height(IntrinsicSize.Min))` rows (such as `SensorGridScreen.kt:1054`), eliminating the `0px` Canvas height collapse and early draw abort.
* **Pre-Start Sample Bleed Eliminated**: In `BANALServiceRepository.kt`, registered `resetAccumulatorsReceiver` for `BANALService.RESET_ACCUMULATORS_INTENT` and triggered `clearSparklineBuffers()` on `TrackingMode.READY -> TRACKING`. Synchronized `TrackingViewModel.kt` via public `clearSparklineBuffers()` resetting `sparklineSamples = emptyList()` and `sparklineSpanSeconds = 0` in `_uiState`.
* **Clean-Room Testing**: Tier 1 targeted unit test suite and AAPT2 resource checks passed with 100% success.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.10` | `TST-TRK-059.10a` | `SensorFieldViewGraphOnlyLayoutTest` | **PASSED** | `Verified` |
| `REQ-TRK-059.10` | `TST-TRK-059.10b` | `BANALServiceRepositorySparklineTest` | **PASSED** | `Verified` |
| `REQ-TRK-059.10` | `TST-TRK-059.10c` | `TrackingViewModelSparklineTest` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-059.5` | Tier 1 Targeted Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewGraphOnlyLayoutTest" \
  --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositorySparklineTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelSparklineTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"

BUILD SUCCESSFUL in 32s
33 actionable tasks: 1 executed, 32 up-to-date
```

### AAPT2 Resource & Governance Verification
```text
./gradlew processDebugResources
BUILD SUCCESSFUL in 2s (18 up-to-date)

python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
```

---

## 4. Invariant & Governance Verification
1. **Layout & Theme Consistency**: Value+Graph and Bar-only layouts unaffected; MaterialTheme tokens preserved.
2. **Snapshot Safety (Rule 66)**: Zero mutable snapshot state mutations during composition.
3. **Memory Bounds**: $\le 300$ samples / $\le 1\text{ KB}$ buffer per tile strictly maintained.
4. **Living Documentation**: Synchronized in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
