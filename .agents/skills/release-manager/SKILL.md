---
name: release-manager
description: Orchestrates the production release lifecycle based on GitFlow, ATT-170, and ATT-1191. Handles release branch creation, version bumping, signed APK/AAB builds via Gradle, Play Store preparation, GitFlow finish, branch pushes, version incrementation, and continuous Jira release ticket checklist maintenance and audit commenting.
---

# Skill: release-manager

## Overview
This skill governs the end-to-end production release process for aTrainingTracker. It standardizes the workflow defined in template ticket **ATT-170**, active release tickets (e.g. **ATT-1191**), and ASPICE CL2 release governance requirements (**REQ-PRO-072**).

The lifecycle bridges GitFlow branch management, Gradle build automation, signed artifact verification, mandatory system integration and verification evidence gating under `docs/releases/<tag>/` (**ASPICE SYS.4 & SYS.5**), Google Play Store distribution, real-time Jira release ticket checklist maintenance, structured audit commenting, and version incrementation for the next development cycle.

---

## Jira Release Ticket Governance: Real-Time Checklist & Audit Comments

A central mandate of this skill is that **the release ticket in Jira must always reflect the exact live state of the release**:

### 1. Checklist Maintenance in Ticket Description
The release ticket description follows the canonical 10-step checklist from ATT-170. Whenever an agent completes one or more steps, the agent **MUST immediately update the ticket description** in Jira, prepending completed items with the Jira green checkmark `(/)`:

```markdown
* (/) Gitflow: new Release
* (/) Gradle: Version: Remove -dev
* (/) Build: Signed APK
* (/) Build: Signed APP Bundle
* (/) Jira: Finish Release & Create new one
* (/) Play Store: Upload as Testing
* Gitflow: Finish Release
* Git: Push Develop & Master
* GitHub: Create new Release
* Gradle: Increment Version; add -dev to name
```

Update command:
```bash
python3 tools/jira_util.py update-desc <RELEASE_TICKET_KEY> "<UPDATED_CHECKLIST>"
```

### 2. Milestone Audit Comments
Whenever an agent executes release actions, the agent **MUST leave a structured progress comment** on the release ticket documenting:
* Executed step names
* Git branch and commit SHAs
* Output artifact file paths, file sizes, and verification outputs (e.g. `apksigner` results)
* Any manual human actions required (e.g. Play Store upload, test flight)

Comment command:
```bash
python3 tools/jira_util.py comment <RELEASE_TICKET_KEY> "h4. Release Progress: Step X completed\n..."
```

---

## The 10-Step Release Checklist (ATT-170 Specification) & System Verification Gate

1. `Gitflow: new Release` (Start `release/V<versionName>__<versionCode>`)
2. `Gradle: Version: Remove -dev` (Set clean production `versionName`)
3. `Build: Signed APK` (`app-release.apk`)
4. `Build: Signed APP Bundle` (`app-release.aab`)
* **[MANDATORY SYSTEM EVIDENCE GATE - ASPICE SYS.4 & SYS.5]**: Release candidate tag creation (Step 7) and Play Store distribution (Step 6) are **STRICTLY BLOCKED** unless verified system evidence artifacts exist under `docs/releases/<tag>/` and pass all criteria:
  - SYS.4: `docs/releases/<tag>/system_integration_results.json` (from `tools/run_system_integration_tests.py`)
  - SYS.5: `docs/releases/<tag>/sys_verification_record.md` (per `docs/system/sys_verification_protocol.md`)
  - Verification Summary: `docs/releases/<tag>/verification_summary.md` (from `tools/generate_release_verification_summary.py`)
5. `Jira: Finish Release & Create new one` (Release current version, create next FixVersion)
6. `Play Store: Upload as Testing` (Upload `.aab` to Internal/Closed Testing track — blocked if system evidence missing/failing)
7. `Gitflow: Finish Release` (Merge into `master` with tags, merge back into `develop` — tag creation blocked if system evidence missing/failing)
8. `Git: Push Develop & Master` (`git push origin master develop --tags`)
9. `GitHub: Create new Release` (Create GitHub release and attach `.apk`)
10. `Gradle: Increment Version; add -dev to name` (Bump `versionName` to `<next>-dev` & `versionCode` on `develop`)

---

## Detailed Step-by-Step Procedure

### Phase 1: Preparation & GitFlow Initiation

1. **Verify Clean Working Tree on develop**:
   Ensure all sprint tickets are integrated, all tests pass, and working directory is clean:
   ```bash
   git checkout develop
   git pull origin develop
   git status
   ```

2. **Inspect Current Version in `app/build.gradle`**:
   Read `versionName` and `versionCode` from the `defaultConfig` block in `app/build.gradle`:
   * Example: `versionName = "4.9.38-dev"`, `versionCode = 262`.
   * Target production version: `VERSION_NAME="4.9.38"`
   * Target version code: `VERSION_CODE=262`
   * Release tag name: `RELEASE_TAG="V4.9.38__262"`

3. **Start GitFlow Release Branch**:
   ```bash
   git flow release start V${VERSION_NAME}__${VERSION_CODE}
   ```
   *Result*: Switches to new branch `release/V<versionName>__<versionCode>`.

4. **Update `app/build.gradle` to Production Version**:
   Remove the `-dev` suffix:
   ```groovy
   defaultConfig {
       minSdkVersion 26
       targetSdkVersion 37
       multiDexEnabled = true
       versionName = "4.9.38"
       versionCode = 262
   }
   ```

5. **Commit Version Update on Release Branch**:
   ```bash
   git add app/build.gradle
   git commit -m "Release: Update version to ${VERSION_NAME}"
   ```

6. **Update Jira Release Ticket**:
   * Mark steps 1 and 2 as completed `(/)` in description.
   * Post audit comment:
     ```bash
     python3 tools/jira_util.py comment <KEY> "h4. Release Branch Initialized\n* Branch: release/V${VERSION_NAME}__${VERSION_CODE}\n* Commit: $(git rev-parse --short HEAD)\n* Production Version: ${VERSION_NAME} (code: ${VERSION_CODE})"
     ```

---

### Phase 2: Building & Verifying Signed Release Artifacts

1. **Keystore Configuration & Verification (REQ-STP-008)**:
   Workspace `release.properties` must contain strictly non-secret keys (must be ignored by `.gitignore`):
   ```properties
   keyStore=/home/rainer/Dropbox/workspace/keystore
   keyAlias=atrainingtracker
   ```
   Signing passwords are resolved via the prioritized provider chain (never stored in workspace `release.properties`):
   - **Tier 1 (Env Vars)**: `KEYSTORE_PASSWORD` and `KEY_ALIAS_PASSWORD`
   - **Tier 2 (OS Keyring)**:
     ```bash
     secret-tool store --label="aTrainingTracker Keystore Password" service atrainingtracker account keyStorePassword
     secret-tool store --label="aTrainingTracker Key Alias Password" service atrainingtracker account keyAliasPassword
     ```
   - **Tier 3 (User Config)**: `~/.config/atrainingtracker/release.properties` (`chmod 600`):
     ```properties
     keyStorePassword=...
     keyAliasPassword=...
     ```
   *Security Invariant*: Never store or commit passwords in the workspace working tree.

2. **Compile Signed App Bundle & Universal APK**:
   Run the release build tasks with bypass sandbox mode:
   ```bash
   ./gradlew bundleRelease assembleRelease
   ```

3. **Verify Generated Artifacts**:
   * **App Bundle (`.aab`)**: `app/build/outputs/bundle/release/app-release.aab`
     Verify signing via jar signature:
     ```bash
     unzip -l app/build/outputs/bundle/release/app-release.aab | grep -E "ATRAININ\.(RSA|SF)"
     ```
   * **Universal APK (`.apk`)**: `app/build/outputs/apk/release/app-release.apk`
     Verify signature with Android SDK `apksigner`:
     ```bash
     ~/Android/Sdk/build-tools/36.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
     ```
     Ensure `Verified using v2 scheme: true` and `Number of signers: 1`.

4. **Update Jira Release Ticket**:
   * Mark steps 3 and 4 as completed `(/)` in description.
   * Post audit comment:
     ```bash
     python3 tools/jira_util.py comment <KEY> "h4. Signed Release Artifacts Generated\n* AAB: app/build/outputs/bundle/release/app-release.aab\n* APK: app/build/outputs/apk/release/app-release.apk\n* Verification: apksigner verified v2 scheme=true, signer=aTrainingTracker"
     ```

---

### Phase 2.5: Mandatory System Verification Evidence Gate (ASPICE SYS.4, SYS.5, SUP.8, REQ-PRO-072)

Before proceeding to Jira release finalization, Play Store distribution, or GitFlow release candidate tag creation, the release manager **MUST verify and commit both system-level evidence artifacts** under `docs/releases/<tag>/` (where `<tag>` is `V${VERSION_NAME}__${VERSION_CODE}` or `V${VERSION_NAME}`):

```
docs/releases/<tag>/
├── system_integration_results.json   # SYS.4 System Integration Verification Evidence
├── sys_verification_record.md        # SYS.5 System Verification Protocol Sign-Off Record
└── verification_summary.md           # SWE.6 BP5 / REQ-PRO-074 Release Verification Summary per Tag
```

#### 1. SYS.4: System Integration Verification Execution & Logging
Run the automated headless system integration test runner across all 6 interface simulation test cases (`SYS-INT-001` through `SYS-INT-006`):
```bash
python3 tools/run_system_integration_tests.py --headless --output-json docs/releases/V${VERSION_NAME}__${VERSION_CODE}/system_integration_results.json
```
*Verification Checklist*:
* Exit code is `0` and overall status is `"PASS"`.
* All 6 test cases (`SYS-INT-001` through `SYS-INT-006`) pass without assertion failures.
* Telemetry phase conservation drift is $\le 5.0\text{ ms}$.

#### 2. SYS.5: System Verification Protocol Sign-Off Verification
Verify that real-hardware system verification has been executed across the hardware reference matrix and target environmental scenarios per `docs/system/sys_verification_protocol.md`:
```bash
test -f docs/releases/V${VERSION_NAME}__${VERSION_CODE}/sys_verification_record.md
```
*Verification Checklist*:
* Document follows `docs/system/sys_verification_protocol.md` Section 6 template and `docs/releases/TEMPLATE.md`.
* All 8 system verification cases (`SYS-VRF-001` through `SYS-VRF-008`) are marked `PASS`.
* Objective criteria met: sample loss $\le 1.0\%$, RF reconnection latency $\le 10.0\text{ s}$, battery drain rate $\le 8.0\%/\text{hr}$, FIT/TCX integrity valid with zero CRC errors.
* Formal sign-off signatures and approval dates from System Verification Engineer and Quality Gatekeeper are present.

#### 3. Release Verification Summary Generation (ASPICE SWE.6 BP5, REQ-PRO-074)
Generate the consolidated release verification summary markdown document aggregating unit tests, traceability coverage, and system verification results:
```bash
python3 tools/generate_release_verification_summary.py --tag V${VERSION_NAME}__${VERSION_CODE}
```
*Verification Checklist*:
* Output file `docs/releases/V${VERSION_NAME}__${VERSION_CODE}/verification_summary.md` generated.
* Overall status is `PASS (READY FOR RELEASE)`.
* Zero open deviations listed under `Deviations Requiring Human Approval`.
* Link to versioned JUnit archive `build-evidence/junit-test-results-<tag>.zip` verified.

#### 4. Blocking Gate Enforcement (REQ-PRO-072.2, REQ-PRO-074.5)
> [!CAUTION]
> **TAGGING & DISTRIBUTION BLOCKED ON MISSING/FAILING SYSTEM EVIDENCE**:
> Creating the release candidate tag (`V${VERSION_NAME}__${VERSION_CODE}` in Phase 4 `git flow release finish`) and uploading artifacts to the Google Play Store (Phase 3) are **STRICTLY BLOCKED** if:
> 1. `docs/releases/<tag>/system_integration_results.json` is missing or has failing test cases.
> 2. `docs/releases/<tag>/sys_verification_record.md` is missing, incomplete, or contains `REVISION` / `FAIL` verdicts.
> 3. `docs/releases/<tag>/verification_summary.md` is missing or contains unresolved deviations requiring human approval.
>
> If either check fails, the release manager MUST halt the release immediately and post a blocking issue comment on the Jira release ticket.

#### 5. Update Jira Release Ticket:
Post audit comment documenting verified system evidence:
```bash
python3 tools/jira_util.py comment <KEY> "h4. Mandatory System Verification Evidence Verified (ASPICE SYS.4 & SYS.5)\n* SYS.4 Integration Results: docs/releases/V${VERSION_NAME}__${VERSION_CODE}/system_integration_results.json (6/6 PASS)\n* SYS.5 Verification Protocol: docs/releases/V${VERSION_NAME}__${VERSION_CODE}/sys_verification_record.md (8/8 PASS, QA Signed-off)\n* Verification Summary: docs/releases/V${VERSION_NAME}__${VERSION_CODE}/verification_summary.md (PASS)\n* Release candidate tag creation APPROVED."
```

---

### Phase 3: Human Gate – Testing & Play Store Distribution

1. **Jira Release Lifecycle**:
   * Inspect existing Jira project versions:
     ```bash
     python3 tools/jira_util.py versions
     ```
   * Mark current release version (e.g. `V4.9.38`) as **Released** in Jira.
   * Create next FixVersion (e.g. `V4.9.39`) for upcoming sprint cycles.
   * Mark step 5 as completed `(/)` in description.

2. **Google Play Console Upload**:
   * The human user (or CI) uploads `app/build/outputs/bundle/release/app-release.aab` to the **Google Play Console**.
   * Deploy to **Internal Testing** or **Closed Testing** track.
   * Human verifies the build on target physical test devices.
   * Mark step 6 as completed `(/)` in description.
   * Post audit comment:
     ```bash
     python3 tools/jira_util.py comment <KEY> "h4. Play Store Upload Complete\n* Track: Internal / Closed Testing\n* Artifact: app-release.aab (V${VERSION_NAME})\n* Awaiting human verification on test devices."
     ```

---

### Phase 4: GitFlow Finalization & Branch Synchronization

1. **Pre-Tagging System Evidence Verification**:
   Verify that system integration and verification evidence exist and pass before allowing release candidate tags:
   ```bash
   if [ ! -f "docs/releases/V${VERSION_NAME}__${VERSION_CODE}/system_integration_results.json" ] || [ ! -f "docs/releases/V${VERSION_NAME}__${VERSION_CODE}/sys_verification_record.md" ]; then
       echo "FATAL: Release candidate tag creation BLOCKED. Missing mandatory system verification evidence (REQ-PRO-072)." >&2
       exit 1
   fi
   ```

2. **Finish GitFlow Release**:
   Once the build is approved and system evidence is verified, finish the release branch:
   ```bash
   git flow release finish -m "Release V${VERSION_NAME}__${VERSION_CODE}" V${VERSION_NAME}__${VERSION_CODE}
   ```
   *Actions performed by git-flow*:
   * Merges `release/V...` into `master` (`--no-ff`).
   * Tags `master` with tag `V${VERSION_NAME}__${VERSION_CODE}`.
   * Merges `release/V...` back into `develop` (`--no-ff`).
   * Deletes local branch `release/V...`.

3. **Add Clean Major Tag (Repository Invariant)**:
   Historically, the repository tags both `V<Version>__<Code>` and clean `V<Version>` on the release commit:
   ```bash
   git checkout master
   git tag -a "V${VERSION_NAME}" -m "Release V${VERSION_NAME}"
   ```

4. **Push Master, Develop, and Tags to Remote**:
   ```bash
   git push origin master develop --tags
   ```

5. **Update Jira Release Ticket**:
   * Mark steps 7 and 8 as completed `(/)` in description.
   * Post audit comment:
     ```bash
     python3 tools/jira_util.py comment <KEY> "h4. GitFlow Release Finalized & Pushed\n* Master Commit: $(git rev-parse --short master)\n* Tags: V${VERSION_NAME}__${VERSION_CODE}, V${VERSION_NAME}\n* Branches pushed: master, develop, --tags"
     ```

---

### Phase 5: GitHub Release & Next Development Cycle

1. **GitHub Release**:
   * Open GitHub repository release page (`https://github.com/rainerblind/aTrainingTracker/releases/new`).
   * Select tag `V${VERSION_NAME}__${VERSION_CODE}`.
   * Title: `Release V${VERSION_NAME}`.
   * Attach `app/build/outputs/apk/release/app-release.apk` for direct download.
   * Add release notes summarizing completed sprint features and bug fixes.
   * Mark step 9 as completed `(/)` in description.

2. **Increment Version on develop**:
   Switch to `develop` and increment for the next sprint cycle:
   ```bash
   git checkout develop
   ```
   In `app/build.gradle`:
   ```groovy
   defaultConfig {
       minSdkVersion 26
       targetSdkVersion 37
       multiDexEnabled = true
       versionName = "4.9.39-dev"  // Next minor/patch version with -dev
       versionCode = 263           // Next sequential build integer
   }
   ```
   Commit and push:
   ```bash
   git add app/build.gradle
   git commit -m "Release: Increment versions."
   git push origin develop
   ```

3. **Update & Close Jira Release Ticket**:
   * Mark step 10 as completed `(/)` in description.
   * Post final audit comment:
     ```bash
     python3 tools/jira_util.py comment <KEY> "h4. Release 4.9.38 Cycle Complete\n* Develop incremented to 4.9.39-dev (code 263)\n* All 10 release gates verified and completed."
     ```
   * Transition ticket to `Final Review (Human)` for human closure to `Erledigt`.

---

## Phase 6: Formal GitFlow Hotfix & Dual-Branch Protocol

When an emergency production regression or fatal crash occurs in a deployed release:
1. **Hotfix Branching**: Branch directly from `master` (or the affected release tag): `hotfix/V<VERSION>__<BUILD>`.
2. **Streamlined ASPICE Governance**: Hotfixes strictly execute Stages 1 through 5 (Analysis, Test-Spec, Impl-Plan, Implementation, Verification), producing targeted engineering deliverables in `docs/engineering/`.
3. **Dual GitFlow Integration**: Upon Gate 5 sign-off and production APK release, merge the hotfix back into `master` (with a version tag) and back-merge into `develop`.
4. **Active Sprint Rebase / Merge**: If an active sprint branch (`sprint/<ID>`) is in flight during the hotfix, immediately merge the hotfix changes into `sprint/<ID>` so in-flight sprint feature branches build upon the hotfix baseline.
5. **Dual Branch Cherry-Picking for In-Sprint Hotfixes**: When an in-sprint ticket has a `FixVersion` targeting an active hotfix release, backport/cherry-pick the verified code, resources, and deliverables onto the active hotfix branch immediately following Stage 5 verification on the sprint branch.

