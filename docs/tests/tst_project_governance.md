# Living Test Specification: Project Governance & Store Presence

**Domain**: `project_governance`  
**Prefixes**: `STP`  
**Scope**: Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance.  
**Total Tests**: 4

---

### TST-STP-001: 9-Locale Store Presence Character Bound & Formatting Parity [Verified]
- **Requirement(s)**: `REQ-STP-001`
- **Component**: `docs/store_presence/*.md`
- **Jira**: `ATT-4853`, `ATT-490`
- **Test Description**:
  1. Inspect all 9 markdown files in `docs/store_presence/` (`de.md`, `en.md`, `es.md`, `fr.md`, `it.md`, `ja.md`, `nl.md`, `pl.md`, `pt.md`).
  2. Verify that Title length $\le 30$ UTF-16 characters across all files.
  3. Verify that Short Description length $\le 80$ UTF-16 characters across all files.
  4. Verify that Full Description length $\le 4000$ UTF-16 characters across all files.
  5. Verify that character count annotations in headers match actual string lengths.

### TST-STP-002: V5.0 Feature Messaging, Privacy & Sports Science Completeness Audit [Verified]
- **Requirement(s)**: `REQ-STP-002`
- **Component**: `docs/store_presence/*.md`
- **Jira**: `ATT-4853`
- **Test Description**:
  1. Verify each language file highlights: .FIT export/import, Google Drive cloud backup, Coggan/Skiba sports science ($\text{NP}$, $\text{IF}$, $\text{TSS}$, $W'_{\text{bal}}$, $\text{PMC}$), Mader-Heck metabolism & glycogen balance, Periods 2.0 contribution grids, Barometer calibration & Electronic Bike Bell.
  2. Verify privacy sovereignty promises (100% ad-free, local SQLite, no mandatory account) are preserved across all 9 translations.

### TST-STP-003: Automated Play Store Listing Regression Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-001`, `REQ-STP-002`, `REQ-STP-003`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/PlayStoreListingContractTest.kt`
- **Jira**: `ATT-4853`, `ATT-490`
- **Test Description**:
  1. Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PlayStoreListingContractTest"`.
  2. Test asserts existence of all 9 files, parses App Title ($\le 30$), Short Description ($\le 80$), and Full Description ($\le 4000$), and validates section header presence.
  3. Ensure 100% pass rate in CI.

### TST-STP-004: Jetpack Compose Material 3 & AMOLED Device Store Screenshots [Verified]
- **Requirement(s)**: `REQ-STP-004`
- **Component**: `docs/store_presence/screenshots/`, `images/Screenshots/`
- **Jira**: `ATT-4853`
- **Test Description**:
  1. Capture high-resolution $1080 \times 2424$ screenshots via ADB from connected Pixel 10 hardware.
  2. Verify images cover Cockpit, Sensor Matrix, Live Map, Lieblingsstrecken, Physiology/PMC, and Periods 2.0 in Light and AMOLED Dark modes.
  3. Confirm images are saved in `docs/store_presence/screenshots/` and reflected in `images/Screenshots/`.
