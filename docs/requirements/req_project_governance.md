# Living Requirements: Project Governance & Store Presence

**Domain**: `project_governance`  
**Prefixes**: `STP`  
**Scope**: Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance.  
**Total Requirements**: 4

---

### REQ-STP-001: 9-Language Google Play Store Presence Parity and Character Bound Invariants [Verified]
- **Tests**: `TST-STP-001`, `TST-STP-003` | **Tickets**: `ATT-4853`, `ATT-490`
- **Implementation**: `docs/store_presence/*.md`, `PlayStoreListingContractTest.kt`
- **Rationale**: Google Play Console enforces strict character limits on app store listings. Exceeding limits blocks updates and causes store listing rejections (ATT-490). All 9 supported locales must maintain 100% structural parity and stay strictly within character bounds.
- **Description**:
  1. The system SHALL maintain localized store listing files in `docs/store_presence/*.md` for all 9 supported languages: German (`de.md`), English (`en.md`), Spanish (`es.md`), French (`fr.md`), Italian (`it.md`), Japanese (`ja.md`), Dutch (`nl.md`), Polish (`pl.md`), and Portuguese (`pt.md`).
  2. Each store presence markdown file MUST strictly obey the Google Play Console character length limits:
     - **App Title**: $\le 30$ UTF-16 code units (characters).
     - **Short Description**: $\le 80$ UTF-16 code units (characters).
     - **Full Description**: $\le 4000$ UTF-16 code units (characters).
  3. Character count annotations in the markdown headers (e.g. `(29/30)`, `(65/80)`) SHALL reflect exact actual UTF-16 character counts.

### REQ-STP-002: Modernized V5.0 Feature Messaging, Privacy Sovereignty & Sports Science Accuracy [Verified]
- **Tests**: `TST-STP-002`, `TST-STP-003` | **Tickets**: `ATT-4853`
- **Implementation**: `docs/store_presence/*.md`
- **Rationale**: The store presence was last updated in July 2026 and omitted major V5.0 innovations. Product descriptions must accurately highlight the state-of-the-art capabilities of *aTrainingTracker* to prospective athletes.
- **Description**:
  1. Store listing descriptions across all 9 languages SHALL highlight native `.FIT` file export/import (`ATT-1117`) alongside `.TCX`, `.GPX`, and `.CSV`.
  2. Descriptions SHALL feature the automatic cloud backup triad: Strava, Dropbox, and Google Drive.
  3. Descriptions SHALL highlight advanced sports science & physiology telemetry: Coggan Normalized Power ($\text{NP}$), Intensity Factor ($\text{IF}$), Training Stress Score ($\text{TSS}$), Variability Index ($\text{VI}$), Skiba Anaerobic Battery ($W'_{\text{bal}}$) with 2-point $\text{CP}/W'$ estimator, Efficiency Factor ($\text{EF}$), Aerobic Decoupling ($\text{Pw:Hr }\%$), and longitudinal Performance Management Chart ($\text{PMC}$: $\text{CTL}$, $\text{ATL}$, $\text{TSB}$, $\text{ACWR}$).
  4. Descriptions SHALL feature the Mader-Heck Metabolic Control Model (`ATT-4786`) and live athlete energy / carbohydrate & glycogen balance (`ATT-4785`).
  5. Descriptions SHALL feature Periods 2.0 (`ATT-4474`) with multi-year analytics and Yearly Activity Contribution Grids.
  6. Descriptions SHALL feature Barometric altitude calibration with known locations (`ATT-508`) and the integrated Electronic Bike Bell.
  7. Descriptions SHALL emphasize our core values: 100% ad-free, no mandatory account/cloud, local SQLite data sovereignty, and unmonetized core tracking with optional Pay-What-You-Want Supporter tier (`ATT-51`).

### REQ-STP-003: Automated Play Store Listing Regression Contract Test [Verified]
- **Tests**: `TST-STP-003` | **Tickets**: `ATT-4853`, `ATT-490`
- **Implementation**: `PlayStoreListingContractTest.kt`
- **Rationale**: Manual verification of character counts across 9 languages is error-prone and caused the regression reported in ATT-490. An automated CI contract test is required to guarantee that no future copy edit exceeds Google Play limits.
- **Description**:
  1. The test suite SHALL provide an automated JUnit 4 contract test `PlayStoreListingContractTest.kt` located in `app/src/test/java/com/atrainingtracker/trainingtracker/build/`.
  2. The test SHALL load and parse each of the 9 markdown files in `docs/store_presence/` (`de.md`, `en.md`, `es.md`, `fr.md`, `it.md`, `ja.md`, `nl.md`, `pl.md`, `pt.md`).
  3. The test SHALL assert that App Title is non-empty and has length $\le 30$.
  4. The test SHALL assert that Short Description is non-empty and has length $\le 80$.
  5. The test SHALL assert that Full Description is non-empty and has length $\le 4000$.
  6. The test SHALL assert that all mandatory section headings are present and valid HTML formatting tags (e.g. `<b>`, `•`) are well-formed.

### REQ-STP-004: Modernized Jetpack Compose Material 3 & AMOLED Device Store Screenshots [Verified]
- **Tests**: `TST-STP-004` | **Tickets**: `ATT-4853`
- **Implementation**: `docs/store_presence/screenshots/`, `images/Screenshots/`
- **Rationale**: Existing screenshots depicted legacy pre-Compose views. Modern store presence requires high-resolution screenshots of the Jetpack Compose Material 3 and AMOLED Dark UI.
- **Description**:
  1. The project SHALL maintain high-resolution Play Store screenshots in `docs/store_presence/screenshots/` and update `images/Screenshots/`.
  2. Screenshots SHALL capture key app surfaces: Cockpit, Sensor Matrix, Live Map & Elevation Profile, Lieblingsstrecken, Physiology/PMC, and Periods 2.0.
  3. Screenshots SHALL capture both Light and AMOLED Dark modes from connected Android hardware (Pixel 10, $1080 \times 2424$).
  4. Updating store listings and screenshots in the Google Play Console is designated as a Human Prerequisite (Rule 22).
