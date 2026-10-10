---
name: regression-bisect
description: Systematic regression archaeology and interval bisection skill. Identifies the exact commit that broke a previously working feature by walking backwards through sprint milestones to establish boundaries, running binary git bisection, and performing Chesterton's Fence forensics before fixing.
---

# Skill: regression-bisect

## Overview
This skill guides the engineering workflow when a previously working feature is discovered to be broken (regression). Rather than guessing root causes or writing ad-hoc workarounds, this skill employs a structured, deterministic approach:
1. **Coarse-Grained Sprint Search (Backwards)**: Identify sprint milestones backwards to establish the bounding interval `[C_good, C_bad]`.
2. **Fine-Grained Interval Bisection (Binary Search)**: Use `git bisect` to systematically divide the commit interval in half until the single culprit commit is pinpointed.
3. **Chesterton's Fence Archaeology**: Understand *why* the culprit commit was made (intent, Jira ticket, requirements) and why the regression occurred as an unintended side effect.
4. **Surgical Fix & Regression Defense**: Implement a minimal fix that preserves the original intent of the culprit commit while restoring the broken functionality, guarded by automated regression tests.

---

## Operational Workflow

### Phase 1: Sprint-Level Coarse Search (Backwards)
The goal of Phase 1 is to locate two sprint milestones:
- **$S_{bad}$**: The most recent sprint / version known to have the regression (typically the current sprint or release).
- **$S_{good}$**: The most recent sprint version where the feature still worked as expected.

#### Step 1.1: List Sprint Milestones
Run the helper tool to list recent sprint milestones backwards:
```bash
python3 tools/sprint_bisect.py list-sprints
```
*(Alternatively: `git log --oneline --grep="Sprint Review" --grep="docs(retro):" --grep="Release V"`)*

#### Step 1.2: Establish Current Broken State ($S_{bad}$)
Confirm the current commit / branch exhibits the failure.
- Record the commit SHA as `C_bad`.

#### Step 1.3: Walk Backwards Sprint by Sprint
1. Check out the previous sprint milestone ($S_{-1}$):
   ```bash
   python3 tools/sprint_bisect.py deploy <SHA_OF_PREVIOUS_SPRINT>
   ```
   *(This checks out the ref, builds `./gradlew assembleDebug`, installs on device via ADB, and launches the app).*
2. Test the feature on the device (or run the relevant automated test).
3. **Evaluate Result**:
   - **Feature is BROKEN**: Move back one more sprint ($S_{-2}$), repeat deployment and test.
   - **Feature WORKS**: You have found $S_{good}$! Record the commit SHA as `C_good`.
4. Result: Bounded interval established between `C_good` (working) and `C_bad` (broken).

---

### Phase 2: Fine-Grained Interval Bisection (Git Bisect)
With `C_good` and `C_bad` established, Phase 2 isolates the exact commit that broke the feature.

#### Step 2.1: Initialize Git Bisect
Start the bisect session:
```bash
python3 tools/sprint_bisect.py bisect-start <C_bad> <C_good>
```
*(Or native: `git bisect start <C_bad> <C_good>`)*

Git will automatically calculate the midpoint commit $C_{mid}$, checkout that revision, and display approximately how many steps remain.

#### Step 2.2: Test Midpoint Commit Iteratively
For each iteration:
1. Build and install the midpoint version on the device:
   ```bash
   python3 tools/sprint_bisect.py deploy
   ```
2. Test the specific feature on the device or via tests.
3. Record the outcome:
   - If feature **WORKS**:
     ```bash
     python3 tools/sprint_bisect.py bisect-good
     ```
   - If feature is **BROKEN**:
     ```bash
     python3 tools/sprint_bisect.py bisect-bad
     ```
4. Git will automatically checkout the next midpoint commit.
5. Repeat steps 1–3 until git outputs:
   ```text
   <CULPRIT_SHA> is the first bad commit
   ```

#### Step 2.3: Reset Bisect Session
Once the culprit SHA is noted:
```bash
python3 tools/sprint_bisect.py bisect-reset
```
Return to the active sprint / working branch.

---

### Phase 3: Chesterton's Fence Forensic Archaeology
Do **NOT** simply revert the culprit commit! Code is usually changed for a reason.

#### Step 3.1: Run Culprit Analysis
```bash
python3 tools/sprint_bisect.py analyze <CULPRIT_SHA>
```
*(Or native: `git show --stat <CULPRIT_SHA>`, `git log -1 <CULPRIT_SHA>`)*

#### Step 3.2: Inspect Jira Context & Requirements
- Extract the Jira ticket ID (e.g. `ATT-XXXX`) from the commit message.
- Inspect ticket background via `python3 -c "import tools.jira_util as ju; ju.show_issue('ATT-XXXX')"` (with BypassSandbox if needed).
- Identify:
  1. **Original Intent**: What problem was the developer trying to solve?
  2. **Architectural Change**: What component, lifecycle callback, or data flow was modified?
  3. **Regression Mechanism**: What exact condition, missing listener, or uninitialized object broke the feature?
  4. **Preserved Invariants**: What must continue to work so we do not re-introduce the original bug?

---

### Phase 4: Surgical Fix & Regression Defense

#### Step 4.1: Formulate Targeted Fix
- Create a feature branch off the current sprint branch:
  `feature/<TICKET>-fix-regression`
- Implement a fix that addresses the regression mechanism while maintaining the original invariants.

#### Step 4.2: Add Regression Test
- Write a unit test or instrumentation test that specifically tests the edge case that broke.
- Ensure the test fails without the fix and passes with the fix.

#### Step 4.3: Verification on Device
- Deploy the fixed build to the physical device:
  ```bash
  python3 tools/sprint_bisect.py deploy
  ```
- Verify with the human user that the feature is fully restored.
- Run full regression test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## Interactive Human Pairing Protocol
- Always present the candidate sprint milestones to the user before checking out and deploying.
- Prompt the user to confirm test results on the device at each bisect step, or execute automated test scripts if headless validation is possible.
- Present the forensic analysis findings (culprit commit, original ticket, root cause) to the user before implementing the fix.
