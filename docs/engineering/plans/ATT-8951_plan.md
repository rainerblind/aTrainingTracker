# Stage 3 Implementation Plan: ATT-8951 - Move HRV Threshold Test CTA Above Threshold Controls

**DOC-ID**: `DOC-PLN-ATT-8951` | **Ticket**: [ATT-8951](https://rainerblind.atlassian.net/browse/ATT-8951) (`1 SP`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-19_01_Früh` | **Branch**: `feature/ATT-8951`  
**Requirement Mapping**: `REQ-TRK-072.7` (`REQ-TRK-072` `[Planned]`, `REQ-TRK-061.4`) | **Test Mapping**: `TST-TRK-072.9`

---

## 1. Architectural Scope, SWE.2/SWE.3 Impact & UI Consistency
- **UI Layer (`ZonesSettingsActivity.kt` — `HrvDfaA1SettingsContent`)**: Relocate `if (onOpenHrvTest != null)` `OutlinedButton` (`Icons.AutoMirrored.Filled.TrendingUp` + `R.string.hrv_start_guided_ramp_test`) directly after `Text(stringResource(R.string.hrv_zones_guidance_desc))` and before the `HrvZoneBandCard`s / threshold controls.
- **UI Consistency (`docs/design_guidelines.md` §3.2, §5.1, §5.20)**: Preserve standard Material 3 `OutlinedButton` + `Icons.AutoMirrored.Filled.TrendingUp`, rely on `Column` `Arrangement.spacedBy(8.dp)` (no double `Spacer`), and set `maxLines = 1, overflow = TextOverflow.Ellipsis` on the CTA `Text` (`Localization Parity: N/A` — `0` string edits).
- **SWE.2 / SWE.3 / `SYS.2` / `SYS.3`**: `N/A` — zero architecture, service, DB, or external interface changes (`REQ-PRO-131.4`).

## 2. System Invariants & Constraints
1. **Viewport & Existing Tests**: Minimal spacing around the CTA keeps `HrvDfaA1RangeSliderCard` visible inside `680.dp` in `testZoneSettingsScreen_tab3PreservesVerticalScrollAndOutsideSwipe`.
2. **Contract & Feature Invariants**: `NavRoutes.HRV_TEST`, `Icons.AutoMirrored.Filled.TrendingUp` (`HrvRampTestScreenTest.kt:163`), `REQ-TRK-087` 4-toggle + `showBar = true` preview (`HrvDfaA1SettingsContractTest`), `ATT-8105` slider isolation, and $T_1 \ge T_2 + 0.05f$ remain intact.
3. **Hygiene (`REQ-PRO-049.1`, `Rule 7.5`)**: `0` new `src/main` reads in tests (`app/source-reading-baseline.txt` untouched); incremental `--rerun` Gradle only (NEVER `--rerun-tasks` or `-PstampGitHash=true`); 4 dirty `tools/` files untouched.

## 3. Step-by-Step Implementation Sequence (Stage 4 & Stage 5)
### Step 0: Gate 3 Lock Check (`Rule 4`)
- Run `python3 tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` before editing `app/src/`.

### Step 1: Relocate CTA `OutlinedButton` in `ZonesSettingsActivity.kt` (`REQ-TRK-072.7`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent`)
- **Edits**: Remove bottom `if (onOpenHrvTest != null) { Spacer(12.dp); OutlinedButton(...) }` (`:1414-1428`) and insert `if (onOpenHrvTest != null) { OutlinedButton(...) }` directly after `Text(hrv_zones_guidance_desc)` (`:1137`) and before `Spacer(4.dp)` + `HrvZoneBandCard`s (`:1139-1143`), relying solely on `Column` `Arrangement.spacedBy(8.dp)` (no double `Spacer`) and adding `maxLines = 1, overflow = TextOverflow.Ellipsis` on the CTA label.

### Step 2: Behavioral Compose Verification & `Rule 8.5` PNG (`HrvDfaA1RangeSliderGestureTest.kt`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1RangeSliderGestureTest.kt` (`0` `src/main` reads, KDoc tagged `TST-TRK-072.9`, `REQ-TRK-072.7`, `ATT-8951`)
- **Edits**: Add `createComposeRule()` test with non-null `onOpenHrvTest = {}` asserting `guidanceDesc.bottom <= ctaButton.top && ctaButton.bottom <= firstZoneCard.top`, click callback execution, single-line DE `"Geführten Schwellentest starten"` at `360.dp`, and `Rule 8.5` Light/Dark PNG capture (`docs/engineering/walkthroughs/img/ATT-8951_hrv_cta_above_fold.png`) inspected via `read_file`.

### Step 3: Targeted `--rerun` Gradle Verification & Living Doc Promotion
- **Stage 4**: Run `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun --console=plain --tests "*HrvDfaA1RangeSliderGestureTest*" --tests "*HrvDfaA1SettingsContractTest*" --tests "*HrvRampTestScreenTest*" --tests "*SourceTextReadingAuditContractTest*"` and promote `REQ-TRK-072` & `TST-TRK-072` `[Planned] -> [Implemented]`.
- **Stage 5**: Promote `REQ-TRK-072` & `TST-TRK-072` `[Implemented] -> [Verified]`, run `verify_requirement_governance.py --sync-indexes` and `--base-ref refs/heads/sprint/2026-11-19_01_Früh --check-verified-evidence`, reuse Gate 4 verified Gradle results (`Rule 7.5`), and author `docs/engineering/walkthroughs/ATT-8951_walkthrough.md`.

## 4. Verification & Rollback Plan
- **Verification**: All 4 targeted Gradle suites pass 100% (`--rerun`); `Rule 8.5` Light/Dark PNG verified via `read_file`; governance checks exit `0`.
- **Rollback**: Isolated to `feature/ATT-8951` (`sprint/2026-11-19_01_Früh` untouched until Stage 6 `PASS`).
