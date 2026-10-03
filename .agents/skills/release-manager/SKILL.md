---
name: release-manager
description: Orchestrates the production release lifecycle based on GitFlow, ATT-170, and ATT-1191. Handles release branch creation, version bumping, signed APK/AAB builds via Gradle, Play Store preparation, GitFlow finish, branch pushes, version incrementation, and continuous Jira release ticket checklist maintenance and audit commenting.
---

# Skill: release-manager

## Overview
This skill governs the end-to-end production release process for aTrainingTracker. It standardizes the workflow defined in template ticket **ATT-170** and active release tickets (e.g. **ATT-1191**).

The lifecycle bridges GitFlow branch management, Gradle build automation, signed artifact verification, Google Play Store distribution, real-time Jira release ticket checklist maintenance, structured audit commenting, and version incrementation for the next development cycle.

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

## The 10-Step Release Checklist (ATT-170 Specification)

1. `Gitflow: new Release` (Start `release/V<versionName>__<versionCode>`)
2. `Gradle: Version: Remove -dev` (Set clean production `versionName`)
3. `Build: Signed APK` (`app-release.apk`)
4. `Build: Signed APP Bundle` (`app-release.aab`)
5. `Jira: Finish Release & Create new one` (Release current version, create next FixVersion)
6. `Play Store: Upload as Testing` (Upload `.aab` to Internal/Closed Testing track)
7. `Gitflow: Finish Release` (Merge into `master` with tags, merge back into `develop`)
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

1. **Keystore Configuration & Verification**:
   Ensure `release.properties` exists in the project root (must be ignored by `.gitignore`):
   ```properties
   keyStore=/home/rainer/Dropbox/workspace/keystore
   keyStorePassword=...
   keyAlias=atrainingtracker
   keyAliasPassword=...
   ```
   *Security Invariant*: Never commit `release.properties` or keystore credentials to version control.

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

1. **Finish GitFlow Release**:
   Once the build is approved, finish the release branch:
   ```bash
   git flow release finish -m "Release V${VERSION_NAME}__${VERSION_CODE}" V${VERSION_NAME}__${VERSION_CODE}
   ```
   *Actions performed by git-flow*:
   * Merges `release/V...` into `master` (`--no-ff`).
   * Tags `master` with tag `V${VERSION_NAME}__${VERSION_CODE}`.
   * Merges `release/V...` back into `develop` (`--no-ff`).
   * Deletes local branch `release/V...`.

2. **Add Clean Major Tag (Repository Invariant)**:
   Historically, the repository tags both `V<Version>__<Code>` and clean `V<Version>` on the release commit:
   ```bash
   git checkout master
   git tag -a "V${VERSION_NAME}" -m "Release V${VERSION_NAME}"
   ```

3. **Push Master, Develop, and Tags to Remote**:
   ```bash
   git push origin master develop --tags
   ```

4. **Update Jira Release Ticket**:
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
   * Transition ticket to `Erledigt`:
     ```bash
     python3 tools/jira_util.py move <RELEASE_TICKET_KEY> "done"
     ```
