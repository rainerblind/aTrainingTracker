# Stage 2: Requirement & Test Specification - ATT-5472: PhotoTrackOverlay Supporter Symbol, Portrait Proportions & Stacked Share Buttons

**Ticket**: [ATT-5472](https://rainerblind.atlassian.net/browse/ATT-5472)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5472`  
**Requirement Mapping**: `REQ-UI-447` (*Workout Share Mode & Photo Track Overlay*) | **Test Spec**: `TST-UI-447`  

---

## 1. Requirement Specification (REQ-UI-447.2, REQ-UI-447.3, REQ-UI-447.4, REQ-UI-447.5)
* **Supporter Symbol Only in Branding Footer (`REQ-UI-447.4`)**: In `ShareUtils.kt` (`drawFooter`), when `effectiveTier.isPro`, the system SHALL draw only the standalone vector emblem (`ic_supporter_emblem_*` via `LauncherIconVariant.fromTier`) on the right of the footer, omitting wide text pill badges (`[◆ SPONSOR]`, `[★ GOLD PRO]`).
* **Auto-Scaling Footer Text & Tier-Matched Icon (`REQ-UI-447.4`)**: In `ShareUtils.kt`, the watermark text (*"Aufgezeichnet mit aTrainingTracker"*) SHALL dynamically downscale if needed to fit between the left launcher icon and right emblem without collision or truncation across all 9 locales.
* **Prominent Typography & Compact Anchoring (`REQ-UI-447.3`)**: In `PhotoTrackOverlayComposer.kt`, workout title (scaled to ~48f), date (~26f), and key metrics (~36f) SHALL have increased prominence and anchor compactly directly above `footerTop` (`footerTop - 24f * scaleRef`), eliminating portrait vertical dead space.
* **Constrained Portrait GPS Track Silhouette (`REQ-UI-447.2`)**: In `PhotoTrackOverlayComposer.kt`, on portrait photos (`height > width`), the 2D GPS track bounding box SHALL be constrained to the upper-right quadrant ($\le 22\%$ photo height), preventing encroachment on central photo subjects.
* **Rule 73 Stacked Action Buttons (`REQ-UI-447.5`)**: In `WorkoutShareBottomSheet.kt`, action buttons SHALL be rendered as vertically stacked full-width buttons (`fillMaxWidth()`, 8dp spacing) at the bottom of the sheet content, placing primary "Teilen" `Button` above secondary "Foto auswählen" `OutlinedButton`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-447` (`REQ-UI-447.2`, `REQ-UI-447.3`, `REQ-UI-447.4`, `REQ-UI-447.5`) & `REQ-UI-445.5`. Targets: `ShareUtils.kt`, `PhotoTrackOverlayComposer.kt`, `WorkoutShareBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-30` (`584284ff`, Photo Track Overlay), `ATT-1406`/`ATT-5397` (Supporter Watermarks), `ATT-5496` (`e5f5c754`, Vector Emblems), and `ATT-5399`/`ATT-5468` (Rule 73 Stacked Buttons).
* **Root Reason for Existing Formulation**: `REQ-UI-445.5` used text pills before vector emblems existed; `REQ-UI-447` was tuned for 16:9 landscape photos where 35% scrim and side-by-side buttons had adequate space.
* **Preservation of Core Invariants**: Equirectangular projection aspect ratio, WCAG AAA text contrast ($\ge 7.0:1$), classic share compatibility in `combineWorkoutAndShare`, and 100% 9-language localization parity remain fully preserved.

---

## 2. 9-Language Localization Parity (TST-LOC-001)
* Zero new string resources introduced. Existing strings (`share_preview_title`, `share_workout_title`, `share_cta_button`, `share_choose_photo`, `share_watermark_tracked_with`) remain reused across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 3. Test Specification & Traceability Matrix

| Test ID | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-447.1` | Unit / Math | `PhotoTrackOverlayComposerTest`: Equirectangular projection, portrait track bounding box ($\le 22\%$ height) | `REQ-UI-447.2` | Specified |
| `TST-UI-447.2` | Contract / UI | `PhotoTrackOverlayComposerTest`: Metrics font sizing, anchoring above `footerTop`, WCAG AAA contrast | `REQ-UI-447.3` | Specified |
| `TST-UI-447.3` | Contract / UI | `ShareUtilsTest`: Supporter emblem only (no text pills), auto-scaling text non-overlap, tier-matched icon | `REQ-UI-447.4`, `REQ-UI-445.5` | Specified |
| `TST-UI-447.4` | Contract / UI | `WorkoutShareBottomSheetContractTest`: Rule 73 stacked full-width action buttons | `REQ-UI-447.5` | Specified |
| `TST-UI-447.5` | Localization | `TranslationParityTest`: 100% parity across 9 locales for all share strings | `REQ-UI-447.5`, `TST-LOC-001` | Verified |
| `TST-UI-447.6` | Regression | Full targeted aftermath photo suite & `ThemeColorCentralizationAuditTest` pass | `REQ-UI-447` | Specified |
