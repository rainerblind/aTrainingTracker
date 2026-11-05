# Stage 1 Analysis: ATT-5422 - SensorStatus Top-Bar: 16 dp Randabstand (Option A)

**Ticket**: [ATT-5422](https://rainerblind.atlassian.net/browse/ATT-5422)  
**Sub-task**: [ATT-5457](https://rainerblind.atlassian.net/browse/ATT-5457) (`[Analysis]`)  
**Parent Epic**: None  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5422`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Statement & Motivation
In `SensorStatus.kt` (embedded in `TrackingTabsScreen.kt`), the drawer hamburger menu button (`☰`) and supporter status emblem (e.g. Diamond `💎`) sit only ~4 dp from the display edge. Visual inspection of `top_bar_padding_comparison.png` (per Rule 71) confirms that current 4 dp spacing clings to the edge, whereas Option A (16 dp) provides visual harmony with standard Material 3 `TopAppBar` padding (16 dp) and dashboard tile labels (`aktiv`, `Geschw.`), while enlarging cramped 24 dp touch targets.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause Breakdown**: Outer `Surface` in `TrackingTabsScreen.kt:731` applies `Modifier.padding(4.dp)`, while portrait `Row` in `SensorStatus.kt:160` has `padding(vertical = 4.dp)` with 0 dp horizontal padding, and both `IconButton`s are clamped to `Modifier.size(24.dp)` around 24 dp icons.
* **Independent Peer Review & Verification**:
  * Audited `SensorStatus.kt:160–226`: Setting `Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))` with `IconButton(Modifier.size(32.dp))` housing `Icon(Modifier.size(24.dp))` yields an internal visual inset of `(32 - 24) / 2 = 4.dp`. Total visual edge offset: `4.dp (Surface) + 8.dp (Row) + 4.dp (internal) = 16.dp`, matching Option A.
  * Symmetrical Spacer Audit: Updating fallback spacers from `Modifier.size(24.dp)` to `Modifier.size(32.dp)` guarantees that `Box(Modifier.weight(1f))` holding the 8 sensor icons remains mathematically centered on the screen.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update `SensorStatus.kt` portrait row padding to `padding(horizontal = 8.dp, vertical = 4.dp)`.
  * Enlarge `IconButton` touch targets for hamburger menu and supporter emblem to `32.dp` (preserving 24 dp icon size).
  * Update inactive/fallback `Spacer`s in both branches to `32.dp`.
  * Visual verification via on-device screenshot and `read_file` (Rule 71).
* **Out-of-Scope Non-Goals**:
  * Landscape layout (`isCompactLandscape = true`, governed by `REQ-UI-434.1` with max 28 dp height).
  * Outer `Surface` padding in `TrackingTabsScreen.kt` (maintained at 4 dp).
  * Modifications to sensor source dialogs, telemetries, or tab row layout.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-445.3` (Live Cockpit Status Emblem, `ATT-1406`, `ATT-5397`).
* **Historical Origin & Commit Trace**: Commits `8baf42c8` (`ATT-5397`) and `8f8b8098` (`ATT-1406`).
* **Root Reason for Existing Formulation**: `REQ-UI-445.3` balanced 24 dp hamburger button with 24 dp supporter emblem, but omitted specifying screen margin padding, leading to 4 dp display clinging.
* **Preservation of Core Invariants**: The 8 sensor icons remain centered via equal-width 32 dp outer slots; 24 dp icon assets and click handlers are preserved; touch target increases from 24 dp to 32 dp.

---

## 5. Architectural Strategy & High-Level Solution
* In `SensorStatus.kt` (Portrait branch):
  * Apply `padding(horizontal = 8.dp, vertical = 4.dp)` to outer `Row`.
  * Update hamburger `IconButton` and fallback `Spacer` to `Modifier.size(32.dp)` (icon remains `24.dp`).
  * Update supporter `IconButton` and fallback `Spacer` to `Modifier.size(32.dp)` (emblem remains `24.dp`).
* Verify with unit tests (`TrackingTabsLandscapeLayoutTest`, `SupporterBadgeContractTest`, `SensorStatusLegibilityTest`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Exact mathematical center alignment of 8 sensor icons across all supporter tiers.
  2. Zero visual or structural regression in landscape mode (`isCompactLandscape`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Declarative Compose padding and touch target adjustments; zero database, sensor, or business logic risk).
