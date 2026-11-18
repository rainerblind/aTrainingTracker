# Stage 3 Implementation Plan - ATT-8749: Enlarge Lieblingsorte Card Preview Map to 175x120 dp & Recalibrate Zoom

**DOC-ID**: `DOC-PLN-ATT-8749` | **Ticket**: [ATT-8749](https://atrainingtracker.atlassian.net/browse/ATT-8749) (`1 SP`) | **Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8749`  
**Requirement Mapping**: `REQ-UI-2785` (`REQ-UI-2785.1`–`REQ-UI-2785.4`) | **Test Mapping**: `TST-UI-2785` (`TST-UI-2785.1`–`TST-UI-2785.4`)  
**Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Traceability, Architecture Scope & System Invariants
* **Living Docs**: `REQ-UI-2785` (`docs/requirements/req_ui_design_system_part5.md`) $\leftrightarrow$ `TST-UI-2785` (`docs/tests/tst_ui_design_system_part3.md`).
* **SWE.2 / SWE.3 Impact**: `N/A` — isolated to UI presentation (`KnownLocationsScreen.kt`) & pure math helper (`KnownLocationZoomMath.kt`); zero DB, Repository, ViewModel, or service state changes (`docs/architecture.md` untouched).
* **Preserved Invariants**:
  1. `RoundedCornerShape(12.dp)`, `GoogleMapOptions().liteMode(true)`, `location_map_preview_${item.id}`, `createCenteredHeartGlyphMarker` (`anchor = Offset(0.5f, 0.5f)`), `LocalInspectionMode` canvas (`size.minDimension * 0.35f`), `IntrinsicSize.Min` bottom alignment, and tap/long-press callbacks stay intact.
  2. `MIN_ZOOM = 9.7f`, `MAX_ZOOM = 11.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, `TARGET_GEOFENCE_DIAMETER_RATIO = 0.35f` remain unchanged; `WorkoutClusterComponents.kt` (`110×75 dp`) & `EditKnownLocationDialog.kt` remain untouched.
  3. Zero `values*/strings*.xml` edits (`N/A - no UI strings`); `app/source-reading-baseline.txt` stays at `105` (`KnownLocationCardLayoutTest.kt:75`) and new tests read `0` `src/main` files (`REQ-PRO-049.1`).

## 2. UI Consistency (`docs/design_guidelines.md`)
* **Spacing & Shape (§5.2–5.3)**: Card inner padding `16.dp` + `Spacer(12.dp)` uses canonical spacing scale (`4/8/12/16/24.dp`); `RoundedCornerShape(12.dp)` retained.
* **Narrow Viewport & Font Scaling (§4 / §5.20)**: `109.dp` left `weight(1f)` metadata column on `360.dp` phones guards against overflow via `maxLines = 1, TextOverflow.Ellipsis` on Altitude text and `FlowRow` wrapping on multi-sport `SportStartsItem`s at `130%` font scale.
* **Previews & Visual Inspection (`Rule 8.5`)**: All `@Preview`s (`L1130`, `L1149`, `L1168`) remain strictly `private fun`; capture Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8749_lieblingsorte_preview.png` and inspect via `read_file`.

## 3. Atomic Step Sequence (Stage 4 Construction)
* **Gate Lock (`Rule 4.1`)**: Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and verify `GATE_PASSED` before any `app/src/` edit.
1. **Step 1 — Zoom Math Calibration (`KnownLocationZoomMath.kt`, `REQ-UI-2785.2`)**:
   * Add `const val DEFAULT_THUMBNAIL_HEIGHT_DP: Double = 120.0` (avoiding Detekt `MagicNumber`), add parameter `thumbnailHeightDp: Double = DEFAULT_THUMBNAIL_HEIGHT_DP` to `calculateThumbnailZoom(...)`, replace hardcoded `80.0` (`L52`) with `thumbnailHeightDp`, and update KDoc (`L38`, `L49–50`).
2. **Step 2 — Enlarged Card Preview & `360.dp` Overflow Guard (`KnownLocationsScreen.kt`, `REQ-UI-2785.1`, `REQ-UI-2785.3`)**:
   * Update `KnownLocationThumbnailMap` (`L666–675`) to `Modifier.size(width = 175.dp, height = 120.dp)` and KDoc (`175x120 dp`).
   * Change card `Spacer` (`L997`) from `16.dp` to `12.dp`; add `maxLines = 1, overflow = TextOverflow.Ellipsis` to Altitude `Text` (`L909–914`); replace `SportStartsItem` `Row` (`L946–994`) with `FlowRow` (preserving `location_starts_badge_${item.id}` and `spacedBy(8.dp)` / `verticalArrangement = Arrangement.spacedBy(4.dp)`).
   * Keep `@Preview`s (`L1130`, `L1149`, `L1168`), `KnownLocationCard`, and `KnownLocationThumbnailMap` strictly `private fun`; expose `KnownLocationsListContent` as `@VisibleForTesting internal fun` for Compose UI testing.
3. **Step 3 — Unit, Layout & Behavioral Compose UI Tests (`TST-UI-2785.1`–`TST-UI-2785.4`)**:
   * `KnownLocationZoomMathTest.kt`: Assert `DEFAULT_THUMBNAIL_HEIGHT_DP == 120.0`, update `500m`/`1000m`/`1500m`/`2000m`/`>= 2700m` assertions (`+0.585` shift at `lat = 48.137`), and test explicit `thumbnailHeightDp = 80.0` vs `120.0`.
   * `KnownLocationCardLayoutTest.kt`: Update `L297` to `.size(width = 175.dp, height = 120.dp)` (keeping baseline count `105`).
   * `KnownLocationCardComposeUiTest.kt` (`0` `src/main` reads, explicit `TST-UI-2785` KDoc tag, `@GraphicsMode(NATIVE)`): Verify `location_map_preview_<id>` bounds (`175.dp × 120.dp`), `360.dp` width + `130%` font scale with 3 multi-digit `SportStartsItem`s + long altitude without clipping/overlap, click callbacks, and Light/Dark PNG capture (`docs/engineering/walkthroughs/img/ATT-8749_lieblingsorte_preview.png`).

## 4. Verification & Rollback Plan
* **Tier-1 Command**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationZoomMathTest" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationCardLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationCardComposeUiTest" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreenTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* **Rollback**: Isolated to `feature/ATT-8749` (`sprint/2026-11-18_01_Früh` untouched until Stage 6 `PASS`).
