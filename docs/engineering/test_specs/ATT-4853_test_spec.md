# Stage 2: Requirement & Test Specification - ATT-4853: Optimize Play Store Description & Refresh Visual Presence

**Ticket**: [ATT-4853](https://rainerblind.atlassian.net/browse/ATT-4853) (consolidating [ATT-490](https://rainerblind.atlassian.net/browse/ATT-490))  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: Standalone / None  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-STP-001`..`REQ-STP-004`  
**Test Spec ID**: `TST-STP-001`..`TST-STP-004`  
**Branch**: `feature/ATT-4853`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-STP-001..004)

### 1.1 Scope & Functional Clauses
1. **9-Language Store Presence Parity & Character Bounds (`REQ-STP-001`)**: Maintain localized store listing files in `docs/store_presence/*.md` across all 9 languages (DE, EN, ES, FR, IT, JA, NL, PL, PT). All listings MUST strictly obey Google Play limits: Title $\le 30$, Short Description $\le 80$, Full Description $\le 4000$ UTF-16 code units. Header count annotations SHALL match exact character lengths.
2. **Modernized V5.0 Feature Messaging & Privacy Sovereignty (`REQ-STP-002`)**: Store descriptions across all 9 locales SHALL prominently feature V5.0 capabilities: native `.FIT` export/import (`ATT-1117`), Google Drive backup triad, advanced sports science ($\text{NP}$, $\text{IF}$, $\text{TSS}$, $\text{VI}$, $W'_{\text{bal}}$, $\text{CP}/W'$, $\text{EF}$, $\text{Pw:Hr }\%$, $\text{PMC}$), Mader-Heck metabolism & live carbohydrate balance (`ATT-4786`/`ATT-4785`), Periods 2.0 contribution grids (`ATT-4474`), Barometer/Known Locations, Electronic Bike Bell, and 100% ad-free local SQLite data sovereignty.
3. **Automated Play Store Listing Regression Contract Test (`REQ-STP-003`)**: Provide JUnit 4 contract test `PlayStoreListingContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/` asserting existence, Title $\le 30$, Short $\le 80$, Full $\le 4000$, and presence of mandatory section headers across all 9 locales.
4. **Modernized Jetpack Compose Store Screenshots (`REQ-STP-004`)**: Capture high-resolution screenshots of Cockpit, Sensor Matrix, Live Map, Lieblingsstrecken, Physiology/PMC, and Periods 2.0 in Light and AMOLED Dark modes from connected hardware into `docs/store_presence/screenshots/` and update `images/Screenshots/`. Google Play Console publishing is documented as a Human Prerequisite (Rule 22).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Novel requirements `REQ-STP-001`..`REQ-STP-004`. Targets `docs/store_presence/*.md`, `PlayStoreListingContractTest.kt`, `docs/store_presence/screenshots/`.
* **Historical Origin & Commit Trace**: Sprint 2026-45.4 (`ATT-4853`, resolving `ATT-490`).
* **Root Reason for Existing Formulation**: Store copy previously expanded without automated CI bounds (ATT-490); visual assets predated Compose rewrite.
* **Preservation of Core Invariants**: 100% ad-free, local SQLite sovereignty, unmonetized core tracking, zero changes to runtime app binary logic.

### 1.2 Acceptance Criteria (Given-When-Then)
* **AC-1 (Character Limits)**: *Given* all 9 files in `docs/store_presence/`, *When* evaluated, *Then* Title $\le 30$, Short $\le 80$, and Full $\le 4000$ characters across every locale.
* **AC-2 (V5.0 Parity)**: *Given* store listings in all 9 languages, *When* inspected, *Then* all V5.0 innovations are accurately and concisely represented.
* **AC-3 (CI Contract Test)**: *Given* `PlayStoreListingContractTest.kt`, *When* executed via Gradle, *Then* all 9 files pass validation with 100% pass rate.
* **AC-4 (Modern Screenshots)**: *Given* connected Pixel 10 hardware, *When* captured, *Then* high-resolution Compose Light/AMOLED screenshots are stored in `docs/store_presence/screenshots/` and `images/Screenshots/`.

---

## 2. Test Specification (TST-STP-001..004)
* **TST-STP-001 (Store Presence Character Bound & Parity Audit)**: Multi-locale inspection verifying Title $\le 30$, Short $\le 80$, Full $\le 4000$ across all 9 languages.
* **TST-STP-002 (V5.0 Feature Messaging & Privacy Audit)**: Verification of .FIT, Drive, Sports Science/PMC, Mader-Heck, Periods 2.0, Bell, and local privacy sovereignty.
* **TST-STP-003 (Automated Listing Regression Contract Test)**: JUnit 4 contract test `PlayStoreListingContractTest.kt` asserting bounds and structure across all 9 files.
* **TST-STP-004 (Compose Material 3 & AMOLED Screenshots Inspection)**: Verification of captured Light and AMOLED Dark screenshots from Pixel 10 hardware.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STP-001` | Content / Audit | `docs/store_presence/*.md` | `REQ-STP-001` | Planned |
| `TST-STP-002` | Content / Audit | `docs/store_presence/*.md` | `REQ-STP-002` | Planned |
| `TST-STP-003` | Contract Unit | `PlayStoreListingContractTest.kt` | `REQ-STP-001`, `REQ-STP-003` | Planned |
| `TST-STP-004` | Asset / Visual | `docs/store_presence/screenshots/` | `REQ-STP-004` | Planned |
