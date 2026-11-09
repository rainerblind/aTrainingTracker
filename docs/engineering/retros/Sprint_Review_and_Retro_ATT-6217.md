# Sprint Review & Retrospective: Sprint `2026-45.13` (`ATT-6217`)

- **Sprint**: `2026-45.13` (Jira Sprint ID `437`)
- **Retrospective Ticket**: `ATT-6217`
- **Target Release**: `V5.0.0`
- **Sprint Branch**: `sprint/2026-45.13`

---

## 1. Sprint Velocity & Scope Summary (`REQ-PRO-029`)

| Metric | Value | Notes |
| :--- | :---: | :--- |
| **Initial Planned Sprint Commitment** | **14 Tickets (`21.0 SP`)** | `14 / 14` (`100.0%`) completed and accepted as `i.O.` |
| **Mid-Sprint Added Tickets (`Rule 65`)** | **11 Tickets (`15.0 SP`)** | `3 / 11` (`4.0 SP`) completed & accepted; `8 / 11` (`11.0 SP`) rolled over when sprint was ended early per human instruction |
| **Completed & Reviewed Tickets** | **17 Tickets (`25.0 SP`)** | **`17 / 17` (`100.0%`) accepted as `i.O.`** (`FixVersion = V5.0.0`) |
| **Follow-Up Backlog Tickets Created** | **6 Tickets** | `ATT-6318`, `ATT-6319`, `ATT-6320`, `ATT-6321`, `ATT-6322`, `ATT-6323` |

---

## 2. Quantitative Sprint Process Metrics (`PA 4.1` & `PA 4.2`)

- **Ticket Throughput**: `17/17` completed & reviewed (`65.4%` raw board ratio `17/26` including 8 rolled-over mid-sprint additions and open ceremony ticket `ATT-6217`) | **Velocity**: `25.0 SP`
- **Overall Gate 1–5 FTPR**: **`100.0%`** across `88` stage sub-tasks (`0` revisions)
  - **Gate 1 (`Analysis`)**: `100.0%` (`18/18`)
  - **Gate 2 (`Req & Test Spec`)**: `100.0%` (`18/18`)
  - **Gate 3 (`Implementation Plan`)**: `100.0%` (`18/18`)
  - **Gate 4 (`Implementation`)**: `100.0%` (`17/17`)
  - **Gate 5 (`Verification`)**: `100.0%` (`17/17`)
- **Empirical Swiss Cheese Escape Rates**: $p_1=0.0, p_2=0.0, p_3=0.0, p_4=0.0, p_5=0.0 \rightarrow P(\text{escaped defect}) = 3.2 \times 10^{-9}$
- **Tier 2 Sprint Integration Gate**: `100.0% PASS` (`./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py`)

### PA 4.2 Special Cause Variation RCA (`HUMAN_ACCEPTANCE`: `65.4%` vs. LCL `80.5%`)
- **Root Cause**: Actual human acceptance of the 17 reviewed sprint tickets in Ceremony 2 was **`100.0%` (`17/17` accepted as `i.O.`)**. The automated `sprint-metrics` script computed `17 / 26 = 65.4%` because the denominator (`26`) included **8 unstarted/WIP mid-sprint added tickets** that were rolled over when the human user instructed *"We have to end this sprint now"* plus the open `Review & Retro` ticket `ATT-6217`. No reviewed ticket was rejected (`n.i.O.`).

---

## 3. Joint Sprint Review Verdict Log (`17 / 17 Accepted as i.O.`)

| # | Key | Summary | Epic | SP | Commit | Verdict | Follow-Up Ticket |
| :-: | :--- | :--- | :-: | :-: | :---: | :---: | :--- |
| 1 | **`ATT-6213`** | `WorkoutSummary` & `WorkoutSectionType`: Make `WorkoutDetails` reorderable so `Photos` can be placed above it | `ATT-68` | 1 | `676ad775` | **`i.O.`** (`V5.0.0`) | — |
| 2 | **`ATT-5902`** | `StravaBulkExportImporter` & `LegacyImportEngine`: Pre-load existing workout identifiers in memory | `ATT-281` | 2 | `49d026ee` | **`i.O.`** (`V5.0.0`) | — |
| 3 | **`ATT-5900`** | `WorkoutClusters` (*Lieblingsstrecken*): Add interactive `'Split Cluster'` dialog with sub-corridor detection | `ATT-176` | 3 | `43a25302` | **`i.O.`** (`V5.0.0`) | **`ATT-6318`** |
| 4 | **`ATT-5899`** | `WorkoutClusterEngine`: Discriminate parallel point-to-point (`A->B`) route corridors and avoid `apex=end` degeneracy | `ATT-176` | 2 | `1a103ffd` | **`i.O.`** (`V5.0.0`) | **`ATT-6319`** |
| 5 | **`ATT-6096`** | Root `README.md`: Replace Peaksware trademarks, add BLE Power Meters / Known Locations / Strava ZIP & screenshots | `ATT-2273` | 1 | `d723cb29` | **`i.O.`** (`V5.0.0`) | — |
| 6 | **`ATT-6092`** | Höchstmaß `README` & Swiss Cheese SVG: Rename to `'The AAA Coding Framework'`, inside-out explanation & 2-column SVG | `ATT-232` | 1 | `8936b016` | **`i.O.`** (`V5.0.0`) | — |
| 7 | **`ATT-6216`** | `AppNavigationDrawer` (`DrawerHeader`): Position app icon and `'a Training Tracker'` title higher in the sky area | `ATT-355` | 1 | `ee64d146` | **`i.O.`** (`V5.0.0`) | — |
| 8 | **`ATT-6215`** | `[High] EquipmentDbHelper`, `StravaBulkExportImporter` & `StravaEquipmentSynchronizeThread`: Fix duplicate bikes/shoes | `ATT-5910` | 2 | `be30b8a4` | **`i.O.`** (`V5.0.0`) | — |
| 9 | **`ATT-6214`** | `TrackOnMapScreen` & `MapDetailLayout`: Move floating Layers button up to `top=16.dp` when Share button is hidden | `ATT-355` | 1 | `b185d9b1` | **`i.O.`** (`V5.0.0`) | — |
| 10 | **`ATT-6212`** | `[High] MapBoundsController` & `MapScreenWithTrack`: Prevent `LOCAL_SEGMENTS` / `LOCAL_ROUTES` hemisphere zoom | `ATT-66` | 1 | `f65690e6` | **`i.O.`** (`V5.0.0`) | — |
| 11 | **`ATT-6211`** | `KnownLocationThumbnailMap` (*Lieblingsorte*): Improve centered heart marker geometry (canonical Material heart shape) | `ATT-1396` | 1 | `9e49a75d` | **`i.O.`** (`V5.0.0`) | **`ATT-6320`** |
| 12 | **`ATT-6210`** | `KnownLocationCard` (*Lieblingsorte*): Break down workout starts by `BSportType` (`Bike`, `Run`, `Other`) using app sport logos | `ATT-1396` | 2 | `71f11f9e` | **`i.O.`** (`V5.0.0`) | **`ATT-6321`** |
| 13 | **`ATT-6209`** | `KnownLocationCard` (*Lieblingsorte*): Remove `'Als Heim-Basis festlegen'` entry from card 3-dot context menu | `ATT-1396` | 1 | `9706af73` | **`i.O.`** (`V5.0.0`) | — |
| 14 | **`ATT-6208`** | `[High] WorkoutShareBottomSheet` (*'Karten- & Analyse-Snapshot'*): Directly capture composite map/chart snapshot & share | `ATT-68` | 2 | `62017d86` | **`i.O.`** (`V5.0.0`) | **`ATT-6322`** |
| 15 | **`ATT-6245`** | `MyBTLEDevice`: Fix Public IEEE MAC addresses with `0xC0..0xFC` OUI misclassified as Random Static | `ATT-5910` | 1 | `99164ce7` | **`i.O.`** (`V5.0.0`) | — |
| 16 | **`ATT-6257`** | `[Bug] [High] FilterManager, MySensorManager & BANALService`: Re-bind filters when `ProxySensor`/`MySensor` is re-registered | `ATT-5908` | 2 | `9388cb5e` | **`i.O.`** (`V5.0.0`) | — |
| 17 | **`ATT-6286`** | `SupporterScreen` & `SupporterComponents`: Visually de-emphasize lower tiers with `'Wechseln'` & hide `'Empfehlung'` | `ATT-355` | 1 | `7d72425c` | **`i.O.`** (`V5.0.0`) | **`ATT-6323`** |

---

## 4. Retrospective Findings, Root Cause Analysis & Permanent Countermeasures (`Rule 80`)

1. **`LaunchedEffect` Self-Cancellation When Keyed on `pagerState.currentPage` (`ATT-6208` $\rightarrow$ `ATT-6322`)**:
   - **Root Cause**: `WorkoutDetailTabbedContent` keyed `LaunchedEffect(pendingAutoShare, pagerState.currentPage, mapTabIndex)` while calling `pagerState.animateScrollToPage(mapTabIndex)` inside the effect body. Crossing the 50% page midpoint mutated `pagerState.currentPage`, cancelling the coroutine mid-animation and leaving the `HorizontalPager` visually stuck at 50% between tabs. Additionally, passing `header = snapshotHeader` to `MapDetailLayout` rendered a second visible `WorkoutHeader` inside the Map tab and did not restore the caller's original tab/screen after sharing.
   - **Permanent Countermeasure (`Rule 80` / `docs/coding_standards.md` §1.11)**: Never include `pagerState.currentPage` or `scrollState.value` as a key in a `LaunchedEffect` that calls `animateScrollToPage`/`scrollToPage`; use `scrollToPage` for programmatic snapshot preparation and restore the caller's tab/screen in `onSnapshotComplete`.
2. **Three-Way Parity for Spatial Fingerprint & Metric Formula Changes (`ATT-5899` $\rightarrow$ `ATT-6319`)**:
   - **Root Cause**: Updating `findApexFromPoints(start, points, end)` in `WorkoutClusterEngine` and live `TrackerService` without updating `LegacyImportEngine` and migrating historical $A \to B$ `APEX_LAT`/`APEX_LON` values in `WorkoutSummaries.db` and `WorkoutClusters.db` caused newly recorded $A \to B$ workouts (midpoint apex) to diverge from historical imported/clustered $A \to B$ workouts (endpoint apex).
   - **Permanent Countermeasure (`Rule 80` / `docs/coding_standards.md` §2.4)**: Any change to a spatial fingerprint or persisted summary formula must symmetrically cover (1) live tracking (`TrackerService`), (2) bulk/file import (`LegacyImportEngine`), and (3) self-healing DB migration of existing records.
3. **Cross-Context Map Marker & Card Row Budget Parity (`ATT-6211` $\rightarrow$ `ATT-6320`, `ATT-6286` $\rightarrow$ `ATT-6323`)**:
   - **Root Cause**: Updating the *Lieblingsorte* heart glyph in `KnownLocationThumbnailMap` left `EditKnownLocationScreen` and `MapScreenWithTrack` on the old standing pin marker; replacing the 32dp checkmark icon with a `"Wechseln"` `OutlinedButton` on `SupporterPlanCard` compressed the title column on `'Bronze-Unterstützer'`, causing a single-letter wrap (`Unterstütze\nr`).
   - **Permanent Countermeasure (`Rule 80` / `docs/coding_standards.md` §1.11)**: Verify domain entity markers across all map surfaces (Thumbnail, Detail/Edit, Global Map) and enforce `maxLines = 1` / compact CTA padding on `Row` headers when replacing a `32.dp` icon with a text button.
