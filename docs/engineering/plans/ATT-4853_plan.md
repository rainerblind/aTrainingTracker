# Stage 3: Implementation Plan - ATT-4853: Optimize Play Store Description & Visual Presence

**Ticket**: [ATT-4853](https://rainerblind.atlassian.net/browse/ATT-4853) (consolidating [ATT-490](https://rainerblind.atlassian.net/browse/ATT-490))  
**Sub-task**: [ATT-5357](https://rainerblind.atlassian.net/browse/ATT-5357) (`[Impl-Plan]`)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-4853`  
**Requirement Mapping**: `REQ-STP-001`..`REQ-STP-004` | **Test Mapping**: `TST-STP-001`..`TST-STP-004`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Google Play Store listings in `docs/store_presence/*.md` across all 9 supported languages (DE, EN, ES, FR, IT, JA, NL, PL, PT) lack post-July 2026 V5.0 innovations and violate Google Play character limits across 8 of 9 locales (consolidated bug `ATT-490`). Furthermore, store screenshots in `images/Screenshots/` depict legacy pre-Compose views. This plan coordinates 9-locale copy compression, automated JUnit 4 listing contract verification, and high-resolution Pixel 10 Compose screenshot capture.

---

## 2. Traceability & System Invariants
* **Requirements**: `REQ-STP-001` (Character Bounds), `REQ-STP-002` (V5.0 Messaging), `REQ-STP-003` (Contract Test), `REQ-STP-004` (Pixel 10 Screenshots).
* **Test Mapping**: `TST-STP-001` (Bounds audit), `TST-STP-002` (Messaging audit), `TST-STP-003` (`PlayStoreListingContractTest`), `TST-STP-004` (Visual screenshots).
* **Core Invariants**:
  1. Strict Google Play character limits: Title $\le 30$, Short Description $\le 80$, Full Description $\le 4000$ UTF-16 code units across all 9 languages.
  2. 100% localization parity across all 9 files in `docs/store_presence/`.
  3. Zero alteration to production application logic, database schemas, or runtime permissions.
  4. Privacy and open model: 100% local data sovereignty, zero ads, zero trackers, and unmonetized core tracking.

---

## 3. Proposed Architectural Changes

### Component 1: Automated Listing Regression Contract Test
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/PlayStoreListingContractTest.kt`
* Parses each `docs/store_presence/*.md` file using `findRepoRootFile()`.
* Asserts non-null presence, exact UTF-16 lengths ($\le 30 / \le 80 / \le 4000$), header integrity, and V5.0 feature keyword coverage (`.FIT`, `ANT+`, `Bluetooth`/`BLE`, `Privacy`/`Datenschutz`, `Drive`, `Strava`).

### Component 2: 9-Locale Store Presence Markdown Overhaul
* **Files**: `docs/store_presence/{de,en,es,fr,it,ja,nl,pl,pt}.md`
* Tighten titles to $\le 30$ characters (e.g., `aTrainingTracker: Bike & Sport`).
* Craft punchy short descriptions $\le 80$ characters highlighting dual-protocol computer and privacy.
* Modularize full descriptions into structured bullet sections $\le 4000$ characters:
  1. Smart Sensor Arbitration (BLE + ANT+, speed fallback).
  2. Native `.FIT` & Cloud Triad (Drive, Dropbox, Strava).
  3. Sports Science Suite (Coggan $\text{NP}$/$\text{IF}$/$\text{TSS}$/$\text{PMC}$, Skiba $W'_{\text{bal}}$).
  4. Mader-Heck Metabolism & Live Glycogen Balance.
  5. Route Clusters (*Lieblingsstrecken*), Climbs & Live Segments.
  6. Periods 2.0 Contribution Heatmaps & Electronic Bell.
  7. 100% Privacy Sovereignty & Supporter Model.

### Component 3: Modern Compose Screenshot Refresh
* **Files**: `docs/store_presence/screenshots/` and `images/Screenshots/`
* Capture $1080 \times 2424$ resolution screenshots from connected Pixel 10 (`66020DLCR002FL`) showcasing Jetpack Compose Material 3 & AMOLED Dark modes.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement JUnit 4 Listing Contract Test (`PlayStoreListingContractTest.kt`)
* Author contract test in `app/src/test/java/com/atrainingtracker/trainingtracker/build/PlayStoreListingContractTest.kt`.
* Verify parser rules and validate baseline failure on current unoptimized files.

### Step 2: Refactor & Compress 9-Language Store Presence Listings
* Update `de.md`, `en.md`, `es.md`, `fr.md`, `it.md`, `ja.md`, `nl.md`, `pl.md`, `pt.md` with V5.0 capabilities.
* Ensure character counts strictly obey Title $\le 30$, Short $\le 80$, Full $\le 4000$, and sync count annotations.

### Step 3: Run Contract Test Suite & Requirement Governance
* Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PlayStoreListingContractTest"`.
* Execute `python3 tools/verify_requirement_governance.py` (`exit code 0`).

### Step 4: Capture Refreshed Play Store Screenshots on Pixel 10
* Use ADB screencap on connected Pixel 10 to capture key screens in Light & Dark modes: Cockpit, Sensor Matrix, Live Map, Route Clusters, Physiology/PMC, and Periods 2.0.
* Populate `docs/store_presence/screenshots/` and update `images/Screenshots/`.

### Step 5: Document Google Play Console Release Handoff
* Document manual Play Console submission as Human Prerequisite (Rule 22).

---

## 5. Verification & Rollback Plan
* **Verification**: `PlayStoreListingContractTest` 100% PASS across all 9 locales; image asset integrity verification.
* **Rollback**: Branch isolation (`feature/ATT-4853`) ensures instant clean rollback with zero repository side effects.
