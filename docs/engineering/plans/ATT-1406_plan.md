# Stage 3: Implementation Plan - ATT-1406: Pro Supporter Badges

**Ticket**: [ATT-1406](https://rainerblind.atlassian.net/browse/ATT-1406) | **Consolidates**: [ATT-5372](https://rainerblind.atlassian.net/browse/ATT-5372)  
**Sub-task**: [ATT-5352](https://rainerblind.atlassian.net/browse/ATT-5352) (`[Impl-Plan]`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-1406`  
**Requirement Mapping**: `REQ-UI-445` | **Test Mapping**: `TST-UI-445`, `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_pro`) | **Risk**: **HIGH** (Rule 71) | **Date**: 2026-11-04  

---

## 1. Problem Description & Architecture Overview
Sprint 2026-45.4 Review rejected ATT-1406 on 3 items: outdated base geometry across all 5 supporter launcher icons, `SupporterQuickIconPickerDialog.kt:170` crash (`ATT-5372`), and unverified visual rendering. This SWE.2 plan deconstructs the Stage 4 implementation into 4 atomic steps executed by `subagent_pro` (Rule 71), ensuring 100% brand color fidelity, safe-zone compliance ($33.0 \le r \le 34.5\text{dp}$), crash-free Compose rendering, and automated contract testing.

---

## 2. Traceability, Invariants & UI Consistency
* **Traceability**: `REQ-UI-445.1`–`.6` $\rightarrow$ `TST-UI-445.1`–`.5`, `TST-LOC-001`.
* **System Invariants**:
  1. 100% preservation of authentic Cervélo P3C (`#FF0000`/`#000000`), stopwatch (`#FFC700`/`#F0F0F0`/`#000090`/`#009000`), and athlete (`#0000FF`) colors.
  2. Safe-zone containment strictly bounded by $33.0 \le r \le 34.5\text{dp}$ on 108dp canvas.
  3. Non-disruptive deferred alias switching (`DONT_KILL_APP`) on `onStop` when `!isTracking()`.
* **UI Consistency (Rule 23)**:
  * *Reference Screens*: `SupporterScreen.kt`, `SensorStatus.kt` (cockpit top bar).
  * *Reused Components*: Material 3 `Surface(shape = CircleShape)`, `Card`, `safePainterResource`.
  * *Theme Tokens*: `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `8.dp`/`12.dp` rounded shapes, neutral circle container `Color(0xFFF1F5F9)`.

---

## 3. Atomic Implementation Steps (Stage 4 Construction — subagent_pro)

### Step 1: Reconstruct 5 Supporter Foreground Vectors on ATT-1482 Base Geometry (`REQ-UI-445.1`)
* Target Files: `res/drawable/ic_launcher_foreground_<tier>.xml` (`pioneer`, `bronze`, `silver`, `gold`, `diamond`).
* Direct reconstruction synthesizing corrected `ATT-1482` base vector (smooth royal-blue `#0000FF` cubic Bézier `C`/`c` cyclist hip/back at depth 50 covering frame junction, black `#000000` saddle at depth 53, flush pushers on thicker `#FFC700` stems, safe zone $33.0 \le r \le 34.5\text{dp}$) + Option A Top-Right Emblem (`cx=67.6, cy=31.8`) + Bottom Tier Ribbon (`y=74.8..83.0`) + subtle tier border ring.

### Step 2: Multi-Tier Icon Preview Rendering & Visual Inspection (`Rule 71`)
* Target Script & File: `docs/engineering/walkthroughs/img/att1406_reconstructed_tiers_preview.png`.
* Generate 6-variant preview comparison board; perform mandatory visual inspection via `read_file` confirming smooth curves, flush pushers, and proper badge placement.

### Step 3: SupporterQuickIconPickerDialog Crash Fix & Call Site Audit (`REQ-UI-445.3` / `ATT-5372`)
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/branding/SupporterQuickIconPickerDialog.kt`.
* In `LauncherIconItemCard`, replace `painterResource(id = variant.iconRes)` with `safePainterResource(id = variant.foregroundRes)` inside `Surface(shape = CircleShape, color = Color(0xFFF1F5F9))`. Audit all callers to guarantee `<adaptive-icon>` mipmaps are never passed to Compose `painterResource`.

### Step 4: Contract Test Suite & Architecture Assertions (`TST-UI-445.1`, `TST-UI-445.3`)
* Target File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/branding/SupporterBadgeContractTest.kt`.
* Update safe-zone check to $33.0 \le r \le 34.5\text{dp}$ across all 5 tier foregrounds; assert cubic Bézier (`C`/`c`) presence in every tier; add source contract asserting `SupporterQuickIconPickerDialog.kt` passes `variant.foregroundRes` and zero `R.mipmap.*` to `painterResource`.

---

## 4. Verification & Rollback Plan
* **Verification Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.branding.*"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Safety**: Isolated branch `feature/ATT-1406`; changes are purely additive or self-contained UI fixes.
