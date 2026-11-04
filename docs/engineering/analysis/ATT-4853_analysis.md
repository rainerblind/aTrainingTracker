# Stage 1 Analysis: ATT-4853 - Optimize Play Store Description & Refresh Visual Presence

**Ticket**: [ATT-4853](https://rainerblind.atlassian.net/browse/ATT-4853) (consolidating [ATT-490](https://rainerblind.atlassian.net/browse/ATT-490))  
**Sub-task**: [ATT-5355](https://rainerblind.atlassian.net/browse/ATT-5355) (`[Analysis]`)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-4853`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Google Play Store listings in `docs/store_presence/*.md` (9 locales: DE, EN, ES, FR, IT, JA, NL, PL, PT) were last updated in July 2026. Significant V5.0 capabilities are omitted: Jetpack Compose / Material 3 & AMOLED Dark UI, dual-protocol BLE/ANT+ arbitration, native `.FIT` export/import (`ATT-1117`), Google Drive backup triad, Barometer/Known Locations (`ATT-508`), Route Clusters / *Lieblingsstrecken*, Strava Live Segments & Climbs, Coggan/Skiba sports science suite ($\text{NP}$, $\text{IF}$, $\text{TSS}$, $W'_{\text{bal}}$, $\text{PMC}$), Mader-Heck metabolism & live carbohydrate balance (`ATT-4786`/`ATT-4785`), Periods 2.0 contribution grids (`ATT-4474`), Electronic Bike Bell, 100% local privacy sovereignty, and the Pay-What-You-Want Supporter model (`ATT-51`). Furthermore, existing listings violate Google Play character limits across 8 of 9 locales (consolidated bug `ATT-490`), and existing screenshots in `images/Screenshots/` depict legacy pre-Compose views.

---

## 2. Forensic Root Cause Analysis & Independent Hypothesis Verification
* **Character Count Audit (ATT-490 RCA)**:
  * Google Play limits: Title $\le 30$, Short Description $\le 80$, Full Description $\le 4000$ characters.
  * Audit of current `docs/store_presence/*.md`:
    * `de.md`: Title 32/30 (+2), Short 63/80 (PASS), Full 4614/4000 (+614 chars over limit)
    * `en.md`: Title 33/30 (+3), Short 59/80 (PASS), Full 4022/4000 (+22 chars over limit)
    * `es.md`: Title 40/30 (+10), Short 70/80 (PASS), Full 4712/4000 (+712 chars over limit)
    * `fr.md`: Title 41/30 (+11), Short 71/80 (PASS), Full 5002/4000 (+1002 chars over limit)
    * `it.md`: Title 35/30 (+5), Short 65/80 (PASS), Full 4607/4000 (+607 chars over limit)
    * `ja.md`: Title 28/30 (PASS), Short 43/80 (PASS), Full 2407/4000 (PASS)
    * `nl.md`: Title 32/30 (+2), Short 60/80 (PASS), Full 4497/4000 (+497 chars over limit)
    * `pl.md`: Title 35/30 (+5), Short 71/80 (PASS), Full 4550/4000 (+550 chars over limit)
    * `pt.md`: Title 37/30 (+7), Short 67/80 (PASS), Full 4663/4000 (+663 chars over limit)
  * *Root Cause*: Translations expanded organically without an automated CI check; manually annotated counts (e.g. `(30/30)`) were stale approximations.
* **Independent Peer Review & Verification**:
  * Confirmed zero existing contract tests for store metadata in `app/src/test/`.
  * Verified connected Pixel 10 hardware (`66020DLCR002FL`, $1080 \times 2424$) is accessible via ADB for high-resolution Compose UI screenshot capture.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  1. Revise all 9 files in `docs/store_presence/*.md` incorporating all V5.0 feature highlights while strictly complying with Google Play limits ($\le 30 / \le 80 / \le 4000$).
  2. Implement automated JUnit 4 contract test `PlayStoreListingContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/` to prevent regressions.
  3. Capture refreshed high-resolution screenshots from connected Pixel 10 hardware into `docs/store_presence/screenshots/` and update `images/Screenshots/`.
  4. Document Google Play Console upload as a Human Prerequisite (Rule 22).
* **Out-of-Scope Non-Goals**:
  * Modifying app runtime Compose UI or telemetry code.
  * Direct Play Console API automated publishing (requires human developer credentials).
* **Complexity Assessment**: 2 SP is appropriate for copy optimization, 9-language translation, 1 contract test, and device screenshot capture (well within AAA 1/2/3 limit `REQ-PRO-029`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Mapping**:
  * `REQ-STP-001`: 9-Language Google Play Store Presence Parity and Character Bound Invariants ($\le 30 / \le 80 / \le 4000$).
  * `REQ-STP-002`: Modernized V5.0 Feature Messaging, Privacy Sovereignty & Sports Science Accuracy.
  * `REQ-STP-003`: Automated Play Store Listing Regression Contract Test (`PlayStoreListingContractTest.kt`).
  * `REQ-STP-004`: Modernized Jetpack Compose Material 3 & AMOLED Device Store Screenshots.
* **Chesterton's Fence**: Preserves core brand promises: 100% ad-free, no mandatory account/cloud, local SQLite data sovereignty, and unmonetized core tracking.

---

## 5. Architectural & Implementation Strategy
* **Copy Compression & Structural Parity**:
  * Titles: Concisely branded (e.g., `aTrainingTracker: Sport Tracker` $\le 30$ chars across all 9 languages).
  * Short Descriptions: Punchy summary highlighting dual-protocol ANT+/BLE computer, sports science & privacy ($\le 80$ chars).
  * Full Descriptions: Structured Markdown/HTML sections (Features, Sensor Management, Routes/Climbs, Sports Science, Privacy & Cloud) capped under $4000$ chars.
* **Automated CI Contract Test**:
  * Implement `PlayStoreListingContractTest.kt` parsing all 9 files and asserting exact UTF-16 character limits and mandatory section headers.
* **Screenshot Pipeline**:
  * Use ADB to capture key Compose screens on Pixel 10 in Light and AMOLED Dark modes.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Strict character limit compliance: Title $\le 30$, Short $\le 80$, Full $\le 4000$ across all 9 locales.
  2. Zero changes to production app binary logic or runtime permissions.
  3. Core tracking remains completely free and privacy-first.
* **Risk Rating**: **LOW** (Documentation, test, and visual assets only; decoupled from app runtime binaries).
