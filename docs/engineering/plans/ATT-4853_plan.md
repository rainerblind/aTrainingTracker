# Stage 3: Implementation Plan - ATT-4853: Optimize Play Store Description & Visual Presence

**Ticket**: [ATT-4853](https://rainerblind.atlassian.net/browse/ATT-4853) (consolidating [ATT-490](https://rainerblind.atlassian.net/browse/ATT-490))  
**Sub-task**: [ATT-5357](https://rainerblind.atlassian.net/browse/ATT-5357) (`[Impl-Plan]`)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-4853`  
**Requirement Mapping**: `REQ-STP-001`..`REQ-STP-004` | **Test Mapping**: `TST-STP-001`..`TST-STP-004`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Sprint 2026-45.4 Review issued an `n.i.O.` rating on ATT-4853 due to two critical defects:
1. **Unimplemented Backlog Features in Store Copy (Rule 71)**: Advertising "Radar" and "Mader-Heck-Stoffwechselmodell" (carbohydrate & glycogen depletion balance) across all 9 locales in `docs/store_presence/*.md`, despite neither being implemented in `app/src/main/`.
2. **Mislabeled Screenshots (Rule 71)**: `01_cockpit_tracking.png` shows the workout edit dialog; `02_sensor_management.png` and `03_live_map.png` both show workout details; `05_physiology_zones.png` shows workout history.
This plan details the atomic remediation steps for Stage 4 (`HIGH-RISK` per Rule 71, executed by `subagent_pro`).

---

## 2. Traceability & System Invariants
* **Requirements**: `REQ-STP-001` (Character Bounds), `REQ-STP-002` (V5 Messaging & Zero Unimplemented Claims), `REQ-STP-003` (Contract Test), `REQ-STP-004` (Visual Screenshots & SHA-256 Uniqueness).
* **Test Mapping**: `TST-STP-001` (Bounds audit), `TST-STP-002` (Negative backlog audit), `TST-STP-003` (`PlayStoreListingContractTest`), `TST-STP-004` (Visual `read_file` inspection & SHA-256 uniqueness).
* **Core Invariants**:
  1. Strict character limits: Title $\le 30$, Short Description $\le 80$, Full Description $\le 4000$ UTF-16 code units across all 9 locales.
  2. 100% of claimed features backed by verified production code in `app/src/main/`; zero mentions of Radar, Mader-Heck, or glycogen.
  3. All 6 primary store screenshots in `docs/store_presence/screenshots/` have distinct SHA-256 hashes and genuinely depict target views.
  4. Zero changes to runtime app binary logic, database schemas, or Android permissions.

---

## 3. Atomic Step-by-Step Implementation Sequence (Stage 4 Construction — HIGH-RISK)

### Step 1: Copy Sanitization across all 9 Store Listing Locales (`REQ-STP-001`, `REQ-STP-002`)
* **Files**: `docs/store_presence/{de,en,es,fr,it,ja,nl,pl,pt}.md`
* In sensor list (line 16), purge "Radar" across all 9 languages.
* In physiology section (line 26), delete the Mader-Heck / glycogen depletion bullet across all 9 languages.
* Ensure 100% verified V5 features remain intact (.FIT, Drive, Skiba $W'_{\text{bal}}$, Coggan power suite, PMC, Periods 2.0, Bell, local SQLite privacy).
* Verify exact character bounds ($\le 30 / \le 80 / \le 4000$) and update header count annotations.

### Step 2: Replace Mislabeled Screenshots & Visual Inspection (`REQ-STP-004`, `Rule 71`)
* **Files**: `docs/store_presence/screenshots/` (root, `de/`, `en/`) and `images/Screenshots/raw/`
* Replace `01_cockpit_tracking.png` with authentic live cockpit (`docs/guide/img/01_cockpit_standard.png`).
* Replace `02_sensor_management.png` with sensor matrix (`images/Screenshots/raw/Screenshot_20260507_064332_a Training Tracker.jpg` converted/scaled to PNG).
* Replace `03_live_map.png` with live map cockpit (`docs/guide/img/05_map_cockpit.png`).
* Replace `04_lieblingsstrecken.png` with route cluster heatmap (`docs/guide/img/06_cluster_heatmap.png`).
* Replace `05_physiology_zones.png` with training zones histogram (`docs/guide/img/07_training_zones.png`).
* Retain verified genuine `06_periods_analytics.png`.
* Inspect all replaced images `01`..`06` via `read_file` to confirm 100% visual authenticity without mislabeling (`Rule 71`).

### Step 3: Extend Automated Contract Test (`REQ-STP-003`, `REQ-STP-004`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/PlayStoreListingContractTest.kt`
* Add negative assertions banning `Radar`, `Mader`, `Glykogen`, and `glycogen` (case-insensitive) across all 9 locales.
* Add screenshot uniqueness assertion: compute SHA-256 hashes of all 6 PNG files and assert zero duplicates (6 unique hashes).
* Assert valid image dimensions ($1080 \times 2424$ or $1080 \times 2400$) and file sizes ($> 50\text{ KB}$) for all screenshots.

### Step 4: Verification & Governance
* Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PlayStoreListingContractTest"`.
* Execute `python3 tools/verify_requirement_governance.py` (`exit code 0`).
* Document Play Console publishing as Human Prerequisite (`Rule 22`).

---

## 4. System Invariants & Rollback Plan
* **Risk Classification**: **HIGH-RISK for Stage 4 (Rule 71)** — executed by `subagent_pro`.
* **Rollback**: Branch isolation (`feature/ATT-4853`) ensures instant clean rollback with zero repository side effects.
