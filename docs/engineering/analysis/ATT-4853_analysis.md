# Stage 1 Analysis: ATT-4853 - Optimize Play Store Description & Refresh Visual Presence

**Ticket**: [ATT-4853](https://rainerblind.atlassian.net/browse/ATT-4853) (consolidating [ATT-490](https://rainerblind.atlassian.net/browse/ATT-490))  
**Sub-task**: [ATT-5355](https://rainerblind.atlassian.net/browse/ATT-5355) (`[Analysis]`)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-4853`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
In Sprint 2026-45.4 Review, ATT-4853 was rated `n.i.O.` due to two critical regressions requiring thorough remediation in Sprint 2026-45.5:
1. **Unimplemented Backlog Features in Store Copy (Rule 71)**: All 9 locales in `docs/store_presence/*.md` advertise "Radar" and the "Mader-Heck-Stoffwechselmodell" (carbohydrate & glycogen depletion balance). Neither is implemented in `app/src/main/` (Radar is only a supporter voting candidate; Mader-Heck is backlog epic `ATT-4786`).
2. **Mislabeled Screenshots (Rule 71)**: `read_file` inspection revealed severe mislabeling: `01_cockpit_tracking.png` shows the "Trainingseinheit überarbeiten" edit dialog; `02_sensor_management.png` and `03_live_map.png` both show workout details; `05_physiology_zones.png` shows the workout history list.
3. **Character Bounds & V5 Features**: Update store copy across all 9 locales (DE, EN, ES, FR, IT, JA, NL, PL, PT) for V5.0 (.FIT, Drive, BLE/ANT+, Skiba/Coggan, Periods 2.0, Bell) while strictly respecting Google Play character bounds ($\le 30 / \le 80 / \le 4000$, resolving `ATT-490`).

---

## 2. Forensic Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Visual Screenshot Audit (`read_file` — Rule 71)**:
  * `01_cockpit_tracking.png` ($131.9\text{ KB}$): Displays "Trainingseinheit überarbeiten (Zum Bäcker #46)" dialog $\rightarrow$ replace with genuine live cockpit (`docs/guide/img/01_cockpit_standard.png`).
  * `02_sensor_management.png` ($193.5\text{ KB}$): Displays workout details $\rightarrow$ replace with sensor matrix (`images/Screenshots/raw/Screenshot_20260507_064332_a Training Tracker.jpg`).
  * `03_live_map.png` ($193.4\text{ KB}$): Duplicate workout details $\rightarrow$ replace with live map cockpit (`docs/guide/img/05_map_cockpit.png`).
  * `04_lieblingsstrecken.png` ($131.9\text{ KB}$): Duplicate edit dialog $\rightarrow$ replace with cluster heatmap (`docs/guide/img/06_cluster_heatmap.png`).
  * `05_physiology_zones.png` ($590.8\text{ KB}$): Displays workout history list $\rightarrow$ replace with training zones histogram (`docs/guide/img/07_training_zones.png`).
  * `06_periods_analytics.png` ($336.9\text{ KB}$): Verified genuine periods weekly summary.
* **Codebase Feature Parity Audit (`app/src/main/`)**:
  * *Unimplemented*: Grep confirms 0 references to Mader-Heck in `app/src/main/` and Radar only appears as `hardware_vote_bike_radar` (supporter voting). Both must be purged across all 9 locales.
  * *Verified Implemented*: Dual BLE/ANT+, failover, Skiba $W'_{\text{bal}}$, Coggan power suite, PMC ($\text{CTL}/\text{ATL}/\text{TSB}/\text{ACWR}$), EF, Pw:Hr %, route clusters, Strava live segments, barometer calibration, bike bell, Periods 2.0, .FIT, Drive triad, and local SQLite data sovereignty.
* **Independent Peer Review & Verification**:
  * Verified `PlayStoreListingContractTest` executes in 22s and passes character limits; will extend it to assert 0 occurrences of "Radar" and "Mader-Heck" across all 9 files.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**: Purge Radar and Mader-Heck from `docs/store_presence/*.md` (9 locales); replace mislabeled screenshots in `docs/store_presence/screenshots/` (root, `de/`, `en/`) and `images/Screenshots/raw/`; enforce limits and absent backlog claims via `PlayStoreListingContractTest`.
* **Out-of-Scope**: Runtime binary edits; Google Play Console publishing (Rule 22 human prerequisite).
* **Complexity**: 2 SP. Single-package documentation/test/asset scope; well within the $\le 3\text{ SP}$ guardrail (`REQ-PRO-029`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Mapping**: `REQ-STP-001` (9-language parity & character bounds), `REQ-STP-002` (V5 feature messaging, privacy, sports science accuracy & 0 unverified claims), `REQ-STP-003` (contract test assertions), `REQ-STP-004` (visual screenshot authenticity).
* **Chesterton's Fence & Side-Effect Audit (Rule 68)**:
  * Removing unverified features (Radar, Mader-Heck) restores truth-in-advertising and prevents misleading Play Store descriptions without impacting valid V5 marketing points.
  * Screenshot replacement does not touch app layout XML or Compose code; purely updates store assets and markdown docs.

---

## 5. Architectural & Implementation Strategy
1. **Copy Sanitization (`docs/store_presence/*.md`)**:
   - In sensor list (line 16), remove Radar across all 9 locales: DE, EN, ES, FR, IT, JA, NL, PL, PT.
   - In physiology section (line 26), remove the Mader-Heck bullet across all 9 locales.
   - Re-verify character bounds ($\le 30 / \le 80 / \le 4000$) post-sanitization.
2. **Automated Contract Test (`PlayStoreListingContractTest.kt`)**:
   - Add negative contract assertions confirming no store presence file contains "Radar" or "Mader".
3. **Screenshot Replacement & Visual Verification (Rule 71)**:
   - Copy verified genuine screenshots into `docs/store_presence/screenshots/` (root, `de/`, `en/`) and `images/Screenshots/raw/`.
   - Inspect every replaced PNG via `read_file` in Stage 4/5.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Title $\le 30$, Short $\le 80$, Full $\le 4000$; 100% of claimed features backed by verified production code in `app/src/main/`; zero unverified/backlog claims; zero runtime app code modifications.
* **Risk Rating**: **HIGH-RISK for Stage 4 (Store Presence & Visual Assets — Rule 71)**. Store presence is the public-facing storefront; visual inspection via `read_file` and copy verification mandate execution by `subagent_pro`.
