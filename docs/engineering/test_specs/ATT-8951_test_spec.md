# Stage 2: Requirement & Test Specification - ATT-8951

**DOC-ID**: `DOC-TST-ATT-8951` | **Ticket**: [ATT-8951](https://rainerblind.atlassian.net/browse/ATT-8951) (`1 SP`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-19_01_Früh` | **Branch**: `feature/ATT-8951`  
**Requirement Mapping**: `REQ-TRK-072.7` (refines `REQ-TRK-072` `[Verified]` / `REQ-TRK-061.4`) | **Test Spec ID**: `TST-TRK-072` (`TST-TRK-072.9`) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-TRK-072.7` Refinement)
- **Attributes**: Priority: `High` (`REQ-TRK-072`) / `Medium` (Ticket) | Type: `Functional` | Verification Method: `Unit` | System Req: `SYSREQ-TRK-004`, `SYSREQ-TRK-006`, `SYSREQ-TRK-012` (`SYS.2`/`SYS.3` = no impact per `REQ-PRO-131.4`)
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent` only)
- **Normative Specification**:
  1. Inside `HrvDfaA1SettingsContent`, when `onOpenHrvTest != null`, the Guided DFA $\alpha_1$ Threshold Test `OutlinedButton` (`Icons.AutoMirrored.Filled.TrendingUp` + `R.string.hrv_start_guided_ramp_test`) SHALL be positioned immediately below `Text(stringResource(R.string.hrv_zones_guidance_desc))` and above the 3 `HrvZoneBandCard`s and threshold configuration controls (`HrvDfaA1RangeSlider`, `HrvThresholdControlRow`).
  2. Vertical spacing around the moved `OutlinedButton` SHALL remain minimal (avoiding double `Spacer` padding on top of `Column` `Arrangement.spacedBy(8.dp)`) so `HrvDfaA1RangeSliderCard` remains visible inside a `680.dp` viewport (`testZoneSettingsScreen_tab3PreservesVerticalScrollAndOutsideSwipe`).
  3. The CTA label SHALL enforce single-line rendering (`maxLines = 1, overflow = TextOverflow.Ellipsis`), keeping verbose German `"Geführten Schwellentest starten"` (31 chars) clean on a single line at `360.dp`–`412.dp` widths.
  4. **9-Locale Localization Parity**: `N/A` — zero UI strings added, modified, or removed (`R.string.hrv_start_guided_ramp_test` already exists in all 9 locales `values/`..`values-pt/`).

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-TRK-072.7` (`docs/requirements/req_tracking_cockpit_part2.md:1525-1527`) & `REQ-TRK-061.4` (`ZonesSettingsActivity.kt`).
- **Historical Origin & Commit Trace**: `ATT-7317` (`fce6777b`) placed the optional CTA at the bottom of `HrvDfaA1SettingsContent`; `ATT-7527` (`b7d3ce84`) consolidated the Guided Threshold Test entry point exclusively into `ZonesSettingsActivity.kt` with `Icons.AutoMirrored.Filled.TrendingUp`.
- **Root Reason for Existing Formulation**: Appended at the bottom as an initial optional CTA; later display/preview sections pushed it below the scroll fold.
- **Preservation of Core Invariants**: Preserves exclusive `ZoneSettingsScreen` -> HRV tab entry (`NavRoutes.HRV_TEST`), `Icons.AutoMirrored.Filled.TrendingUp` (`HrvRampTestScreenTest.kt:163`), `REQ-TRK-087` 4-toggle card + `showBar = true` preview, `ATT-8105` slider gesture isolation, $T_1 \ge T_2 + 0.05f$, and 9-locale parity.

## 3. Acceptance Criteria (Given-When-Then)
- *Given* `HrvDfaA1SettingsContent` rendered with non-null `onOpenHrvTest = {}`,
- *When* measured via `createComposeRule()` in Light/Dark (`412.dp`) and German (`360.dp`) viewports,
- *Then* `guidanceDesc.bottom <= ctaButton.top && ctaButton.bottom <= firstZoneOrThresholdControl.top`, the German CTA text (`"Geführten Schwellentest starten"`) fits on 1 line (`lineCount == 1`) with `Icons.AutoMirrored.Filled.TrendingUp`, clicking the button invokes `onOpenHrvTest`, `HrvDfaA1RangeSliderCard` remains visible within `680.dp`, and `Rule 8.5` Light/Dark PNGs pass visual inspection.

## 4. Test Specification (`TST-TRK-072.9`, Behavioral Compose + Contract)
1. **Behavioral Compose Ordering, Single-Line DE & Click (`HrvDfaA1RangeSliderGestureTest.kt`, `0` `src/main` reads per `REQ-PRO-049.1`)**:
   - Render `HrvDfaA1SettingsContent` with **non-null** `onOpenHrvTest = { clicked++ }` (via `createComposeRule()`, `0` `src/main` reads); assert vertical bounds `guidanceDesc.boundsInRoot.bottom <= ctaButton.boundsInRoot.top && ctaButton.boundsInRoot.bottom <= firstZoneCard.boundsInRoot.top` (and `< sliderCard.top`), click invokes callback, and `Rule 8.5` Light/Dark PNG (`ATT-8951_hrv_cta_above_fold.png`) + `360.dp` German single-line check pass.
   - Re-run existing `testZoneSettingsScreen_tab3PreservesVerticalScrollAndOutsideSwipe` (`680.dp` height, `onNavigateToHrvTest = {}`) confirming `HrvDfaA1RangeSliderCard` stays displayed.
2. **Contract & Governance Regression (`HrvDfaA1SettingsContractTest`, `HrvRampTestScreenTest`, `SourceTextReadingAuditContractTest`)**:
   - Incremental `--rerun` Gradle execution in Stage 4/5 confirms 100% pass across all 4 suites with `0` new `src/main` reads.

## 5. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-072.9` | Compose UI (`createComposeRule()`, `0` `src/main` reads) + `Rule 8.5` | `HrvDfaA1SettingsContent` CTA ordering, DE single-line, `680dp` viewport, click | `REQ-TRK-072.7`, `REQ-TRK-061.4` | Verified |
| `TST-TRK-072.9` | Contract & Regression | `HrvDfaA1SettingsContractTest`, `HrvRampTestScreenTest`, `SourceTextReadingAuditContractTest` | `REQ-TRK-072.7`, `REQ-TRK-087`, `REQ-PRO-049.1` | Verified |
