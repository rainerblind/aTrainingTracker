# Stage 3: Implementation Plan - ATT-5496: Supporter Emblems & Starter Icons (B5, S4-A, G6, Variant C & PRO/PRO+ Naming)

**Ticket**: [ATT-5496](https://rainerblind.atlassian.net/browse/ATT-5496)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5496`  
**Requirement Mapping**: `REQ-UI-445` (*Supporter Badges & Launcher Icons*) | **Test Mapping**: `TST-UI-445`, `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
* Current Bronze/Silver/Gold medals share identical geometry, starter icons clutter text ribbons, and Silver/Gold share `PRO-SUPPORTER`.
* `SupporterComponents.kt` lacks full-color emblems during plan selection and active tier display.
* Solution replaces emblems with B5 (Rosette), S4-A (Silver Coins), G6 (Gold Bar+Coin), positions emblems outside text capsules on `#FFFFFF` (Variant C), adopts `PRO` / `PRO+` naming, and adds emblems to `SupporterScreen`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-445.1` (B5, S4-A, G6 emblems), `REQ-UI-445.2` (Variant C starter icons), `REQ-UI-445.3` (PRO/PRO+ verbal hierarchy), `REQ-UI-445.4` (`SupporterComponents` emblems).
* **Test Mapping**: `TST-UI-445.1`–`.5` (Safe zone, brand colors, 24dp vectors, components, aliases), `TST-LOC-001` (9-language parity).

---

## 3. System Invariants & Preserved Behavior
1. **Safe-Zone & Brand Fidelity**: All 5 starter foregrounds fit within safe zone radius ($33.0 \le r \le 34.5\text{dp}$) and preserve all 7 authentic brand colors (`#FF0000`, `#000000`, `#FFC700`, `#F0F0F0`, `#000090`, `#009000`, `#0000FF`).
2. **28 Base Logo Paths Intact**: Zero modifications to Cervélo P3C bike, runner, cyclist, or stopwatch paths.
3. **Tracking & Lifecycle Isolation**: Homescreen icon synchronization remains strictly deferred via `DONT_KILL_APP` when `!TrainingApplication.isTracking()`.

---

## 4. Proposed Architectural Changes
* **Emblem Drawables (24dp)**: Update `ic_supporter_emblem_{bronze,silver,gold}.xml` with verified B5, S4-A, and G6 XML vectors.
* **Starter Icon Foregrounds (108dp)**: Replace bottom stadium overlays in `ic_launcher_foreground_{pioneer,bronze,silver,gold,diamond}.xml` with centered compact text blocks flanked by external emblems on `#FFFFFF`.
* **Supporter Screen (Compose)**: In `SupporterComponents.kt`, inject `LauncherIconVariant.fromTier(plan.tier).emblemRes` into `SupporterPlanCard` (44dp, `Color.Unspecified`), `ActiveTierBanner` (36dp), and `PioneerStatusBanner` (`ic_supporter_emblem_pioneer`, 36dp).
* **UI Consistency**: Reuses `SupporterComponents.kt` cards, `RoundedCornerShape(12.dp)`, and `MaterialTheme.colorScheme` containers. Zero new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update 24dp Vector Emblems
* Files: `app/src/main/res/drawable/ic_supporter_emblem_{bronze,silver,gold}.xml`
* Deploy B5 Rosette, S4-A Silver Coins, and G6 Gold Bar+Coin vectors.

### Step 2: Update 108dp Adaptive Foreground Vectors (Variant C)
* Files: `app/src/main/res/drawable/ic_launcher_foreground_{pioneer,bronze,silver,gold,diamond}.xml`
* Implement Variant C with centered stadium text (`PIONEER`, `SUPPORTER`, `PRO SUPPORTER`, `PRO+ SUPPORTER`, `SPONSOR`) flanked by external tier emblems.

### Step 3: Hook Emblems in Supporter Screen & Update LauncherIconVariant KDoc
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterComponents.kt`, `.../ui/branding/LauncherIconVariant.kt`
* Add 44dp leading emblem to `SupporterPlanCard` and 36dp emblems to `ActiveTierBanner` & `PioneerStatusBanner`.

### Step 4: Update Strings Across All 9 Locales
* Files: `app/src/main/res/values*/strings.xml` (all 9 language resource files)
* Update `supporter_tier_*`, `drawer_tier_*`, `launcher_icon_*`, and `garage_detector_dialog_message_gold` per Stage 2 matrix.

### Step 5: Test Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `SupporterBadgeContractTest`, `LauncherIconManagerTest`, and `TranslationParityTest`. Run ADB screenshot inspection in Stage 5.
* **Rollback**: Clean git branch isolation (`feature/ATT-5496`) allows instant discard without impacting other sprint work.
