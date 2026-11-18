# Stage 1 Analysis: ATT-8749 - Enlarge Lieblingsorte Card Preview Map to 175x120 dp & Recalibrate Zoom

**DOC-ID**: `DOC-ANA-ATT-8749` | **Ticket**: [ATT-8749](https://atrainingtracker.atlassian.net/browse/ATT-8749)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8749` | **Author**: `--as agent1`

---

## 1. Problem Statement & Motivation
In `KnownLocationsScreen.kt`, `KnownLocationThumbnailMap` (`L666–783`) currently renders at `116.dp × 80.dp` (`L673`), leaving excess whitespace beside the metadata column while cramping the centered heart marker, geofence circle, and Google watermark. Enlarging the preview to `175.dp × 120.dp` and calibrating `KnownLocationZoomMath.calculateThumbnailZoom` to `120.0 dp` improves neighborhood & geofence legibility while preserving the compact card layout.

## 2. Independent Peer Review & Verification
Audited `KnownLocationsScreen.kt`, `KnownLocationZoomMath.kt`, `MappableListItem.kt`, `KnownLocationCardLayoutTest.kt`, `KnownLocationZoomMathTest.kt`, `SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, `app/detekt-baseline.xml`, and `docs/requirements/` / `docs/tests/` on `886eae05`:
* **`KnownLocationsScreen.kt` Layout & Previews**:
  * `KnownLocationThumbnailMap` (`L666`) uses `Modifier.size(width = 116.dp, height = 80.dp)` (`L673`), `RoundedCornerShape(12.dp)` (`L675`), and `testTag("location_map_preview_${item.id}")` (`L674`). `LocalInspectionMode` (`L678–709`) scales dynamically via `size.minDimension * 0.35f` (`L688`). All 3 `@Preview` functions (`L1130`, `L1149`, `L1168`) are confirmed strictly `private fun`.
  * **Narrow-Screen (`360.dp`) Width Budget**: `LazyColumn` `16.dp` padding (`L456–457`) + card `16.dp` padding (`L855`) yields `296.dp` inner width. With `175.dp` map + `12.dp` `Spacer` (reduced from `16.dp`, `L997`), the left `weight(1f)` metadata `Column` receives **`109.dp`**. Title row (`L858–882`) stays full-width (`296.dp`). Altitude `Text` (`L909`) lacks `maxLines = 1` / `TextOverflow.Ellipsis`, and the 3-sport `SportStartsItem` `Row` (`L946–994`, `spacedBy(8.dp)`) can crowd `109.dp` when all 3 sports have multi-digit counts at 130% font scale (evaluate `FlowRow` or compact spacing in Stage 3). Note: `WorkoutClusterComponents.kt:536` still uses `110.dp × 75.dp` (not modified here).
* **`KnownLocationZoomMath.kt` Calibration & Detekt**:
  * `calculateThumbnailZoom` (`L44–56`) hardcodes `80.0` (`L52`), which is recorded in `app/detekt-baseline.xml:1575` (`MagicNumber`). Defining a named `const val DEFAULT_THUMBNAIL_HEIGHT_DP = 120.0` as default parameter `thumbnailHeightDp: Double = DEFAULT_THUMBNAIL_HEIGHT_DP` avoids a new Detekt `MagicNumber` and preserves caller compatibility (`KnownLocationsScreen.kt:725`).
  * Keeping `MIN_ZOOM = 9.7f` and `MAX_ZOOM = 11.7f` while shifting height `80 -> 120 dp` increases unclamped zoom by $+\log_2(1.5) \approx +0.585$: at `lat = 48.137`, `500m` clamps to `11.7f`, `1000m` moves to `~11.10f`, `1500m` to `~10.51f`, and `2000m` to `~10.10f`, requiring `KnownLocationZoomMathTest.kt` (`L55–85`) updates.
* **Tests (`REQ-PRO-049.1`) & Shard Caps**:
  * `KnownLocationCardLayoutTest.kt` (`L297`) is in `app/source-reading-baseline.txt:75` (exact count `105` enforced by `SourceTextReadingAuditContractTest.kt:172`); update `L297` in-place to `.size(width = 175.dp, height = 120.dp)` and add **behavioral `createComposeRule()` UI tests** (zero `src/main` reads) for `175×120 dp` bounds and `360.dp` narrow-width rendering.
  * `req_ui_design_system_part2.md` (`3960` lines), `part4.md` (`3966`), `tst_part1.md` (`3984`), and `tst_part2.md` (`3977`) are near the `4000`-line cap; place net-new `REQ-UI-*` / `TST-UI-*` in `req_ui_design_system_part5.md` (`1749`) & `tst_ui_design_system_part3.md` (`762`).

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (`1 SP`)**: Resize `KnownLocationThumbnailMap` to `175.dp × 120.dp`, reduce row `Spacer` (`16.dp -> 12.dp`), update KDoc, ensure `360.dp` left-column resilience, calibrate `KnownLocationZoomMath` to `120.0 dp` default height, update `KnownLocationCardLayoutTest` + `KnownLocationZoomMathTest` + add Compose UI `createComposeRule()` test, and update living docs.
* **Out-of-Scope**: Resizing `WorkoutClusterComponents.kt` (`110×75 dp`), changing `EditKnownLocationDialog.kt`, or altering `MIN_ZOOM`/`MAX_ZOOM` bounds or geofence marker geometry.

## 4. Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-217`, `REQ-UI-224`, `REQ-UI-244` (`req_ui_design_system_part2.md`), `REQ-UI-465.3`, `REQ-UI-469.4`, `REQ-UI-470.1` (`req_ui_design_system_part4.md`).
* **Historical Origin & Commit Trace**: `b24a3c20` (`ATT-1813`, `80dp`), `d7b00bf8` (`ATT-1878`, `KnownLocationZoomMath` `80.0`), `e29c614b` (`ATT-2051`, `100dp`), `4c095473` (`ATT-5825`, `116×80 dp`), `ce73d561` (`ATT-5884`, `9.7f..11.7f`).
* **Root Reason for Existing Formulation**: `116×80 dp` minimized card height when badges wrapped; `80.0` in `KnownLocationZoomMath` matched the `80 dp` height before enlargement.
* **Preservation of Core Invariants**: Preserve `RoundedCornerShape(12.dp)`, `liteMode(true)`, `createCenteredHeartGlyphMarker` (`anchor = Offset(0.5f, 0.5f)`), `LocalInspectionMode` schematic canvas, `IntrinsicSize.Min` bottom alignment, `MIN_ZOOM = 9.7f`/`MAX_ZOOM = 11.7f`, test tags, and 1-tap drill-down/edit/long-press callbacks.

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-UI-217`, `REQ-UI-224`, `REQ-UI-244`, `REQ-UI-465.3`, `REQ-UI-469.4`, `REQ-UI-470.1` via net-new `REQ-UI-*` in `req_ui_design_system_part5.md` and `TST-UI-*` in `tst_ui_design_system_part3.md` (preserving `<= 4000`-line shard caps and symmetric `REQ<->TST` links).
* **Affected Architecture Elements**: `KnownLocationsScreen.kt`, `KnownLocationZoomMath.kt` (no DB, ViewModel, Repository, or `strings*.xml` changes).
* **Verification Impact & Test Surface**: `KnownLocationCardLayoutTest.kt`, `KnownLocationZoomMathTest.kt`, new `createComposeRule()` UI test (`REQ-PRO-049.1`), and Stage 5 emulator-5554 visual check (`Rule 8.5`).
* **Risk Assessment & Invariants**: **LOW** — localized UI dimension & zoom-math height update; main layout risk is narrow-screen (`360.dp`) metadata wrapping alongside `175.dp` map, mitigated via `12.dp` spacer and Compose UI tests.
