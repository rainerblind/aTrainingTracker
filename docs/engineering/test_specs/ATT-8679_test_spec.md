# Stage 2: Requirement & Test Specification - ATT-8679

**DOC-ID**: `DOC-TST-ATT-8679` | **Ticket**: [ATT-8679](https://atrainingtracker.atlassian.net/browse/ATT-8679) | **Parent Epic**: [ATT-1286](https://atrainingtracker.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8679` | **Author**: `--as agent1`  
**Requirement Mapping**: `REQ-TRK-062` (refines `.2`/`.3`/`.4`/`.6`), `REQ-TRK-064` (reconciled `.1`/`.2`) | **Test Spec ID**: `TST-TRK-062`, `TST-TRK-064`  

---

## 1. Requirement Specification (`REQ-TRK-062`, `REQ-TRK-064` `[Planned]`)
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit | **Implementation**: `RadarEdgeOverlay.kt`, `ic_device_available.xml`, `values*/strings_radar.xml`
- **Rationale**: PO Ceremony 2 review (`ATT-8109`) requested permanent removal of the green connected circle (`RadarStatusBadgePill` with `ic_device_available`) across Start and Live Tracking tabs ("Der grüne Kreis soll immer raus"), while retaining `DISCONNECTED_ERROR` fail-safe warning/reconnect (`REQ-TRK-062.4`) and active-threat count/snooze badges (`REQ-TRK-064.2`).
1. **`REQ-TRK-062.2/.3` (Clear-State Badge Suppression & Rail Alignment)**:
   - Whenever `!isDisconnectError && !hasActiveThreats` (`CONNECTED` + clear road, including `snoozeState.isSnoozed && isClear`), `RadarStatusBadgePill` SHALL NOT be rendered across `LEFT`, `RIGHT`, or `FULL_SCREEN` (Start tab `page == 0` and live tracking tabs `page > 0`), eliminating every green connected/snooze circle.
   - In `LEFT`/`RIGHT` `Column`, the top `Spacer(8.dp)` and post-badge `Spacer(6.dp)` MUST be guarded on badge visibility (`isDisconnectError || hasActiveThreats`) so `RadarVerticalRail` does not shift downward by `14.dp`. `FULL_SCREEN` clear renders zero rail and zero badge; `AUTO_HIDE_3S` clear fades out only the rail with zero clear-state badge.
2. **`REQ-TRK-062.4` / `REQ-TRK-064.1/.2` (Fail-Safe & Active-Threat Badge Retention)**:
   - `DISCONNECTED_ERROR` SHALL retain the amber `RadarStatusBadgePill` (`ic_device_not_available`) + `onReconnectClick` (`REQUEST_START_SEARCH_FOR_PAIRED_DEVICES`).
   - Active threats (`APPROACHING` / `FAST_APPROACHING`) SHALL retain `RadarStatusBadgePill` with `targets.size` (plus muted-bell/countdown when `audioSnoozeState.isSnoozed`) + `onBadgeClick` (`RadarLightQuickSwitchSheet`).
3. **`REQ-TRK-062.6` (9-Language Parity & Unused Resource Cleanup)**:
   - Orphaned `app/src/main/res/drawable/ic_device_available.xml` and `R.string.radar_overlay_clear` across all 9 `values*/strings_radar.xml` locales (EN/DE/ES/FR/IT/JA/NL/PL/PT) MUST be removed cleanly so `:app:lintDebug` (`UnusedResources`) and `TranslationParityTest` stay 100% compliant.

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. *Original Requirement ID & Target*: `REQ-TRK-062` (`.2`/`.3`/`.4`/`.6`) & `REQ-TRK-064` (`.1`/`.2`) in `req_tracking_cockpit_part2.md` (`RadarEdgeOverlay.kt`, `ic_device_available.xml`, `strings_radar.xml`).
2. *Historical Origin & Commit Trace*: `2a3baf12` (`ATT-5085`), `90e44df9` (`ATT-6703`), `3206a504` (`ATT-7537`), `b7a4de7b` (`ATT-7744`).
3. *Root Reason for Existing Formulation*: `RadarStatusBadgePill` originally served as the clear-road indicator (`BADGE_ONLY`/`AUTO_HIDE_3S`) and quick-switch trigger before `ATT-7972` added `RadarLightButton`.
4. *Preservation of Core Invariants*: Upward threat kinematics ($140\text{ m}/175\text{ m} \to 0\text{ m}$), 100% touch pass-through, `.navigationBarsPadding()`, `DISCONNECTED_ERROR` amber reconnect, active-threat snooze badge, and 9-locale parity remain intact.

## 3. Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `CONNECTED` radar with clear road (`targets.isEmpty()`, `ThreatLevel.NONE`, unsnoozed or `audioSnoozeState.isSnoozed`), *When* `RadarEdgeOverlay` renders in `LEFT`, `RIGHT`, or `FULL_SCREEN`, *Then* `RadarStatusBadge` does NOT exist (zero green circle), and `LEFT`/`RIGHT` `RadarVerticalRail` aligns without a `14.dp` downward shift.
- *Given* `DISCONNECTED_ERROR`, *When* `RadarEdgeOverlay` renders and `RadarStatusBadge` is tapped, *Then* the amber hazard badge (`ic_device_not_available`) renders and invokes `onReconnectClick`.
- *Given* `APPROACHING` or `FAST_APPROACHING` targets (unsnoozed or snoozed), *When* `RadarEdgeOverlay` renders and `RadarStatusBadge` is tapped, *Then* `RadarStatusBadge` displays `targets.size` (plus snooze text when snoozed) and invokes `onBadgeClick`.
- **Invariants**: Zero green connected badge; `14.dp` spacer guard; `DISCONNECTED_ERROR` reconnect & active-threat badge preserved; zero SQLite/DB changes; 9-locale `strings_radar.xml` parity (`radar_overlay_clear` removed across all 9 locales); `REQ-PRO-049.1` zero `src/main` reads in UI tests.

## 4. Test Specification (`TST-TRK-062`, `TST-TRK-064` `[Planned]`)
- **`TST-TRK-062.4` / `TST-TRK-064.2` (`RadarEdgeOverlayTest.kt`, Robolectric Compose `createComposeRule()`, ZERO `src/main` reads per `REQ-PRO-049.1`)**:
  - Assert `RadarStatusBadge` `assertDoesNotExist()` when `CONNECTED` + clear road (unsnoozed and `audioSnoozeState.isSnoozed == true`) across `LEFT`, `RIGHT`, `FULL_SCREEN` (and `isRadarOverlayVisible` for Start/Live tabs).
  - Assert `LEFT`/`RIGHT` clear `SLIM_RAIL` positions `RadarVerticalRail` at top without `14.dp` shift; assert `FULL_SCREEN` clear renders zero rail/badge; assert `DISCONNECTED_ERROR` badge exists + triggers `onReconnectClick`; assert `APPROACHING` / `FAST_APPROACHING` + snoozed threat badge exists + triggers `onBadgeClick`.
- **`TST-TRK-062.6` (`TranslationParityTest.kt`)**: Assert 100% key/format parity across all 9 `values*/strings_radar.xml` locales after removing `radar_overlay_clear` (and `ic_device_available.xml`).
- **`TST-TRK-062.9` (`Rule 8.5` Visual Verification)**: Capture Light/Dark `@Preview` screenshot PNG (`docs/engineering/walkthroughs/img/ATT-8679_radar_edge_overlay_preview.png`) covering `ClearLight` (zero green circle), `ApproachingDark`, `FastThreatAmoled`, and `Disconnected` states; inspect via `read_file`.

## 5. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-062.4` | Compose UI (`createComposeRule()`, 0 `src/main` reads) | `RadarEdgeOverlay` clear suppression (`LEFT`/`RIGHT`/`FULL_SCREEN`) & `14.dp` spacer guard | `REQ-TRK-062.2`, `REQ-TRK-062.3` | Planned |
| `TST-TRK-062.3` / `TST-TRK-064.2` | Compose UI (`createComposeRule()`) + Unit | `DISCONNECTED_ERROR` reconnect & active-threat/snooze badge | `REQ-TRK-062.4`, `REQ-TRK-064.1`, `REQ-TRK-064.2` | Planned |
| `TST-TRK-062.6` | Localization Parity | `TranslationParityTest` (9-locale `strings_radar.xml` sans `radar_overlay_clear`) | `REQ-TRK-062.6` | Planned |
| `TST-TRK-062.9` | `Rule 8.5` Visual Audit | `@Preview` PNG `ATT-8679_radar_edge_overlay_preview.png` | `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.4` | Planned |
