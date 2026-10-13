---
name: regression-bisect
description: Systematic regression archaeology and interval bisection skill. Identifies the exact commit that broke a previously working feature by walking backwards through sprint milestones using exponential search (1, 2, 4, 8, 16...) to establish boundaries in O(log N) evaluations, running binary git bisection, and performing Chesterton's Fence forensics before fixing.
---

# Skill: regression-bisect

## Overview
This skill guides the engineering workflow when a previously working feature is discovered to be broken (regression). Rather than guessing root causes or writing ad-hoc workarounds, this skill employs a structured, deterministic approach:
1. **Coarse-Grained Exponential Sprint Search (Backwards)**: Identify sprint milestones backwards using an exponential step sequence ($2^0, 2^1, 2^2, 2^3, \dots$, i.e. 1, 2, 4, 8, 16... sprints back) to rapidly bound the regression between the last-tested broken sprint and the first working sprint in $O(\log N)$ evaluations.
2. **Fine-Grained Interval Bisection (Binary Search)**: Use `git bisect` to systematically divide the commit interval in half until the single culprit commit is pinpointed.
3. **Chesterton's Fence Archaeology**: Understand *why* the culprit commit was made (intent, Jira ticket, requirements) and why the regression occurred as an unintended side effect.
4. **Surgical Fix & Regression Defense**: Implement a minimal fix that preserves the original intent of the culprit commit while restoring the broken functionality, guarded by automated regression tests.

---

## Operational Workflow

### Phase 1: Sprint-Level Coarse Search (Exponential Backwards)
The goal of Phase 1 is to locate two sprint milestones that tightly bracket the regression:
- **$S_{bad}$**: The most recent sprint milestone confirmed to have the regression.
- **$S_{good}$**: The most recent sprint milestone where the feature still worked as expected.

Linear backward search (testing sprint by sprint: $-1, -2, -3, -4\dots$) scales as $O(N)$ and is inefficient when regressions were introduced multiple sprints ago. Instead, Phase 1 uses an **Exponential (Galloping) Backward Search** ($1, 2, 4, 8, 16, \dots$ sprints back), achieving $O(\log N)$ sprint evaluations and simultaneously minimizing the commit range passed into `git bisect`.

#### Step 1.1: List Exponential Sprint Probes
Run the helper tool to display the precomputed exponential sprint probes:
```bash
python3 tools/sprint_bisect.py list-probes
```
*(To view the full table of all historical sprints alongside their probe steps, use `python3 tools/sprint_bisect.py list-sprints`).*

Example output:
```text
================================================================================================
EXPONENTIAL BACKWARD SPRINT SEARCH SEQUENCE (Galloping Search: 1, 2, 4, 8, 16...)
================================================================================================
Probe  | Offset     | Milestone / Sprint | SHA       | Date       | Deploy Command
------------------------------------------------------------------------------------------------
#1     | -1 (2^0)   | Sprint 2026-41.5   | 0773a9f7  | 2026-10-09 | python3 tools/sprint_bisect.py deploy 0773a9f7
#2     | -2 (2^1)   | Sprint 2026-41.4   | 813e94ca  | 2026-10-09 | python3 tools/sprint_bisect.py deploy 813e94ca
#3     | -4 (2^2)   | Sprint 2026-41.2   | 63ae5e87  | 2026-10-08 | python3 tools/sprint_bisect.py deploy 63ae5e87
#4     | -8 (2^3)   | Sprint 2026-40.14  | 81e5ed7a  | 2026-10-04 | python3 tools/sprint_bisect.py deploy 81e5ed7a
#5     | -16 (2^4)  | Sprint 2026-40.6   | f2281000  | 2026-10-01 | python3 tools/sprint_bisect.py deploy f2281000
================================================================================================
```

#### Step 1.2: Establish Initial Broken State ($S_{bad}$)
Confirm that the current commit / sprint branch exhibits the failure.
- Record the current commit SHA as $C_{bad\_initial}$ (e.g. `HEAD`).

#### Step 1.3: Exponential Backward Probing Protocol ($1, 2, 4, 8, 16\dots$)
Evaluate candidate sprint milestones at exponential intervals:

1. **Probe #1 (Offset $-1 = -2^0$ sprints back)**:
   - Deploy Probe #1:
     ```bash
     python3 tools/sprint_bisect.py deploy-probe 1
     ```
   - Test the feature on the device (or run automated test).
   - **Result Evaluation**:
     - **WORKS**: $S_{good} = \text{Probe \#1}$, $S_{bad} = \text{HEAD}$.  
       Interval bounded: `[C_good = Probe #1, C_bad = HEAD]`. Proceed to Phase 2.
     - **BROKEN**: Mark Probe #1 as the latest confirmed bad sprint milestone ($S_{bad} = \text{Probe \#1}$). Advance to Probe #2.

2. **Probe #2 (Offset $-2 = -2^1$ sprints back)**:
   - Deploy Probe #2:
     ```bash
     python3 tools/sprint_bisect.py deploy-probe 2
     ```
   - Test the feature on the device.
   - **Result Evaluation**:
     - **WORKS**: $S_{good} = \text{Probe \#2}$. Since Probe #1 was already confirmed broken, $S_{bad} = \text{Probe \#1}$.  
       Interval bounded: `[C_good = Probe #2, C_bad = Probe #1]`. Proceed to Phase 2.
     - **BROKEN**: Update latest confirmed bad milestone ($S_{bad} = \text{Probe \#2}$). Advance to Probe #3.

3. **Probe #K (Offset $-2^{K-1}$ sprints back, for $K \ge 3$)**:
   - Deploy Probe #K:
     ```bash
     python3 tools/sprint_bisect.py deploy-probe <K>
     ```
   - Test the feature on the device.
   - **Result Evaluation**:
     - **WORKS**: $S_{good} = \text{Probe \#K}$. Since Probe #(K-1) was confirmed broken, $S_{bad} = \text{Probe \#(K-1)}$.  
       Interval bounded: `[C_good = Probe #K, C_bad = Probe #(K-1)]`. Proceed to Phase 2.
     - **BROKEN**: Update latest confirmed bad milestone ($S_{bad} = \text{Probe \#K}$). Advance to Probe #(K+1) (offset $-2^K$).

#### Dual Advantage of Exponential Bisection:
1. **$O(\log N)$ Deployments**: Jumping exponentially bounds even ancient regressions (e.g. 16 or 32 sprints back) in only 5 to 6 physical deployments.
2. **Narrower `git bisect` Window**: When Probe #K works, the upper bound is set to Probe #(K-1) rather than HEAD, pruning away all commits between Probe #(K-1) and HEAD from the subsequent git bisection.

---

### Phase 2: Fine-Grained Interval Bisection (Git Bisect)
With `C_good` and `C_bad` established from Phase 1, Phase 2 isolates the exact commit that broke the feature.

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
- Create a bugfix branch off the current sprint branch:
  `bugfix/<TICKET>-fix-regression`
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
- Always present the candidate sprint milestones to the user using `list-probes` before checking out and deploying.
- Prompt the user to confirm test results on the device at each bisect step, or execute automated test scripts if headless validation is possible.
- Present the forensic analysis findings (culprit commit, original ticket, root cause) to the user before implementing the fix.
