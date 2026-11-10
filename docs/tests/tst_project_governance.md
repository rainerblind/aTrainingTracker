# Living Test Specification: Project Governance & Store Presence

**Domain**: `project_governance`  
**Prefixes**: `STP`  
**Scope**: Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance.  
**Total Tests**: 5

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
  1. Verify each language file highlights: .FIT export/import, Google Drive cloud backup, Coggan/Skiba sports science ($\text{NP}$, $\text{IF}$, $\text{TSS}$, $W'_{\text{bal}}$, $\text{PMC}$), Periods 2.0 contribution grids, Barometer calibration & Electronic Bike Bell.
  2. Verify zero occurrences of unimplemented features (Radar, Mader-Heck, glycogen depletion) across all 9 locales.
  3. Verify privacy sovereignty promises (100% ad-free, local SQLite, no mandatory account) are preserved across all 9 translations.

### TST-STP-003: Automated Play Store Listing Regression Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-001`, `REQ-STP-002`, `REQ-STP-003`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/PlayStoreListingContractTest.kt`
- **Jira**: `ATT-4853`, `ATT-490`
- **Test Description**:
  1. Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PlayStoreListingContractTest"`.
  2. Test asserts existence of all 9 files, parses App Title ($\le 30$), Short Description ($\le 80$), and Full Description ($\le 4000$), and validates section header presence.
  3. Test asserts negative checks: zero occurrences of 'Radar', 'Mader', 'Glykogen', or 'Glycogen' across all 9 files (Rule 71).
  4. Ensure 100% pass rate in CI.

### TST-STP-004: Jetpack Compose Material 3 & AMOLED Device Store Screenshots [Verified]
- **Requirement(s)**: `REQ-STP-004`
- **Component**: `docs/store_presence/screenshots/`, `images/Screenshots/`
- **Jira**: `ATT-4853`
- **Test Description**:
  1. Capture high-resolution $1080 \times 2424$ screenshots via ADB from connected Pixel 10 hardware.
  2. Verify images cover Cockpit, Sensor Matrix, Live Map, Lieblingsstrecken, Physiology/PMC, and Periods 2.0 in Light and AMOLED Dark modes.
  3. Confirm all 6 primary screenshot files (`01_cockpit_tracking.png` through `06_periods_analytics.png`) have distinct SHA-256 hashes (zero duplicate files) and visually verify via `read_file` that each displays its intended screen without mislabeling (Rule 71).
  4. Confirm images are saved in `docs/store_presence/screenshots/` and reflected in `images/Screenshots/`.

### TST-STP-005: README Play Store Badge Asset, Link Integrity, Trademark-Free & Screenshot Showcase Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-005`
- **Component**: `README.md`, `.reviews/README.md`, `images/google-play-badge.svg`, `docs/guide/img/*.png`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReadmePlayStoreBadgeContractTest.kt`
- **Jira**: `ATT-2371`, `ATT-6096`
- **Test Description**:
  1. *Play Store Badge & Logo Integrity (`TST-STP-005.1`)*:
     • Verify root `README.md` and `.reviews/README.md` exist and contain a markdown/HTML image link referencing `images/google-play-badge.svg`.
     • Verify Play Store badge links directly to canonical `https://play.google.com/store/apps/details?id=com.atrainingtracker` without `.trainingtracker` suffix.
     • Verify `images/google-play-badge.svg` exists and contains valid SVG structure and branding.
     • Verify `images/logo_512.png` is embedded and exists on disk.
  2. *User Guide Navigation & Disk Resolution (`TST-STP-005.2`)*:
     • Verify `README.md` links to `docs/index.md`, `docs/guide/en/index.md`, and `docs/guide/de/index.md`, and all target files exist on disk.
  3. *Modern Screenshot Showcase Verification (`TST-STP-005.3`)*:
     • Verify `README.md` embeds the 4 modern Compose screenshots (`01_cockpit_standard.png`, `08_workout_detail.png`, `06_cluster_expanded.png`, `07_telemetry_charts.png`) referencing `docs/guide/img/`, and all 4 files exist on disk with valid PNG headers.
  4. *Trademark-Free & Capability Accuracy Audit (`TST-STP-005.4`)*:
     • Verify zero occurrences of Peaksware trademarks (`Normalized Power`, `NP`, `Intensity Factor`, `IF`, `Training Stress Score`, `TSS`, `PMC`, `CTL`, `ATL`, `TSB`) in `README.md`.
     • Verify presence of BLE Power Meter (`0x1818`), Strava `.zip` import, Full-App ZIP backup, Known Locations (*Lieblingsorte*), and Climb Detection mentions.
  5. *CI Contract Test Execution (`TST-STP-005.5`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReadmePlayStoreBadgeContractTest"` asserting 100% pass rate.

### TST-STP-006: Play Store Promo Video Assets, Storyboard & Generator Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-006`
- **Component**: `docs/store_presence/promo_video_storyboard.md`, `tools/generate_promo_video.py`, `docs/store_presence/video/att_promo_showcase.mp4`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/PromoVideoAssetsContractTest.kt`
- **Jira**: `ATT-2274`
- **Test Description**:
  1. *Storyboard Structure & Scene Timing Audit (`TST-STP-006.1`)*:
  • Verify `docs/store_presence/promo_video_storyboard.md` exists and defines all 5 mandatory scenes spanning 0:00 to 1:00.
  2. *Composite Generator Script & Cockpit Alignment (`TST-STP-006.2`)*:
  • Execute `python3 tools/generate_promo_video.py` and verify exit code 0.
  • Verify carbon stem and out-front mount are centered at $X = 680$ directly under the smartphone, with calibrated B-roll callout crop coordinates.
  • Verify generator supports dual encoding backends (`ffmpeg` with `gst-launch-1.0` / keyframe export fallback).
  3. *Video Asset & Frame Uniqueness Contract (`TST-STP-006.3`)*:
  • Verify existence of `docs/store_presence/video/att_promo_showcase.mp4` with size $> 100\text{ KB}$.
  • Verify scene keyframes exist under `docs/store_presence/video/keyframes/` at $1920 \times 1080$ resolution.
  • Confirm all 5 rendered scene frames (`scene_01_cockpit.png`..`scene_05_privacy_export.png`) have distinct SHA-256 hashes (zero duplicate files), display genuine verified screenshots from `ATT-4853`, and are visually verified via `read_file` (`Rule 71`).
  4. *JUnit 4 Build Contract Suite (`TST-STP-006.4`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PromoVideoAssetsContractTest"` asserting 100% pass rate in CI.

### TST-STP-007: Stale Process Artifacts Excision & Gitignore Hygiene Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-007`
- **Component**: `.gitignore`, `ReleaseCiAndLintConfigContractTest.kt`, `AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6153`
- **Test Description**:
  1. *Stale Artifact Absence Audit (`TST-STP-007.1`)*:
     • Verify that `.active_task.json`, `implementation_plan.artifact.md`, and `.artifacts/` do not exist in the repository root or on disk.
     • Verify that git tracking contains zero entries for `.active_task.json`, `implementation_plan.artifact.md`, or `.artifacts/`.
  2. *.gitignore Pattern Enforcement (`TST-STP-007.2`)*:
     • Inspect `.gitignore` at repository root.
     • Assert presence of ignore rules for `.active_task.json`, `.artifacts/`, `*.artifact.md`, and `.reviews/`.
  3. *Canonical Directory Governance Verification (`TST-STP-007.3`)*:
     • Execute `python3 tools/verify_requirement_governance.py` and verify exit code 0, confirming zero non-canonical directories under `docs/engineering/`.
  4. *Build Contract Suite Execution (`TST-STP-007.4`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate in CI.

### TST-STP-008: Keystore Password Decoupling & Secure Provider Chain Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-008`
- **Component**: `app/build.gradle`, `.github/workflows/android-release.yml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6148`
- **Test Description**:
  1. *Build Signing Configuration Provider Audit (`TST-STP-008.1`)*:
     • Inspect `app/build.gradle` signing configs.
     • Assert that keystore and key alias passwords resolve via environment variables, `secret-tool` keyring lookup, and `~/.config/atrainingtracker/release.properties`.
     • Assert absence of `props.keyStorePassword` and `props.keyAliasPassword` workspace lookups.
  2. *CI Workflow Secret Exposure Audit (`TST-STP-008.2`)*:
     • Inspect `.github/workflows/android-release.yml`.
     • Verify that release signing step injects secrets via environment variables (`KEYSTORE_PASSWORD`, `KEY_ALIAS_PASSWORD`).
     • Verify zero workflow steps write keystore passwords to a repository `release.properties` file.
  3. *Workspace Property Hygiene Guard (`TST-STP-008.3`)*:
     • Inspect repository root `release.properties` if present.
     • Assert that neither `keyStorePassword` nor `keyAliasPassword` is present in any working-tree properties file.
  4. *Build Contract Suite Execution (`TST-STP-008.4`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate in CI.

### TST-STP-009: CM Plan Integrity & Submodule Pinning Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-009`
- **Component**: `docs/management/cm_plan.md`, `.gitmodules`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6152`
- **Test Description**:
  1. *CM Plan Documentation & Section Audit (`TST-STP-009.1`)*:
     • Verify existence of `docs/management/cm_plan.md`.
     • Assert presence of mandatory ASPICE SUP.8 sections: Scope & Configuration Items, Branching & Release Strategy, Baseline Management & Traceability, Submodule Governance, and Disaster Recovery.
  2. *Submodule Pinning Audit (`TST-STP-009.2`)*:
     • Inspect `.gitmodules`.
     • Verify that `[submodule "ANT-Android-SDKs"]` does not contain a `branch =` entry.
     • Verify submodule URL points to canonical repository and gitlink resolves to committed SHA-1.
  3. *Rule 67 Canonical Engineering Boundary Guard (`TST-STP-009.3`)*:
     • Execute `python3 tools/verify_requirement_governance.py` and verify exit code 0, confirming zero non-canonical entries in `docs/engineering/`.
  4. *Build Contract Suite Execution (`TST-STP-009.4`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate in CI.

### TST-STP-010: Project Risk Register & Sprint-Start Governance Contract Test [Verified]
- **Requirement(s)**: `REQ-STP-010`
- **Component**: `docs/management/risk_register.md`, `.agents/skills/sprint-planner/SKILL.md`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6151`
- **Test Description**:
  1. *Risk Register Documentation & Structure Audit (`TST-STP-010.1`)*:
     • Verify existence of `docs/management/risk_register.md`.
     • Assert presence of mandatory sections: Scoring Matrix ($P \times I$), Exposure Thresholds (Low/Medium/High), and Registered Risks Table.
     • Assert presence of initial risks: `PR-01` (Single Human Bottleneck), `PR-02` (AI Model Drift), `PR-03` (Google Play Policy), and `PR-04` (Sensor Fragmentation).
     • Assert all High-exposure risks link to a concrete mitigation ticket.
  2. *Ceremony 1 Sprint-Start Review Checkpoint Audit (`TST-STP-010.2`)*:
     • Inspect `.agents/skills/sprint-planner/SKILL.md`.
     • Verify Ceremony 1 includes an explicit risk register review and last-reviewed date check.
  3. *Rule 67 Canonical Engineering Boundary Guard (`TST-STP-010.3`)*:
     • Execute `python3 tools/verify_requirement_governance.py` and verify exit code 0, confirming zero non-canonical entries in `docs/engineering/`.
  4. *Build Contract Suite Execution (`TST-STP-010.4`)*:
     • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate in CI.





