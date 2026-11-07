---
name: regression-bisect
description: Systematic regression archaeology and interval bisection skill. Identifies the exact commit that broke a previously working feature by walking backwards through sprint milestones using exponential search (1, 2, 4, 8, 16...) to establish boundaries in O(log N) evaluations, running binary git bisection, and performing Chesterton's Fence forensics before fixing.
---

# Skill: regression-bisect

## Overview
This skill guides the engineering workflow when a previously working feature is discovered to be broken (regression). Rather than guessing root causes or writing ad-hoc workarounds, this skill employs a structured, deterministic approach:
1. **Coarse-Grained Exponential Sprint Search (Backwards)**: Identify sprint milestones backwards using an exponential step sequence ($1, 2, 4, 8, 16, \dots$ sprints back) to rapidly bound the regression between the last-tested broken sprint and the first working sprint in $O(\log N)$ evaluations.
2. **Fine-Grained Interval Bisection (Binary Search)**: Use `git bisect` to systematically divide the commit interval in half until the single culprit commit is pinpointed.
3. **Chesterton's Fence Archaeology**: Understand *why* the culprit commit was made (intent, issue ticket, requirements) and why the regression occurred as an unintended side effect.
4. **Surgical Fix & Regression Defense**: Implement a minimal fix that preserves the original intent of the culprit commit while restoring the broken functionality, guarded by automated regression tests.

---

## Operational Workflow

### Phase 1: Sprint-Level Coarse Search (Exponential Backwards)
* Identify two sprint milestones that bracket the regression:
  - **$S_{bad}$**: The most recent milestone confirmed to have the regression.
  - **$S_{good}$**: The most recent milestone where the feature still worked as expected.
* Evaluate exponential sprint probes (Offsets: $-1, -2, -4, -8, -16\dots$).

### Phase 2: Git Bisect (Fine-Grained Commit Bisection)
```bash
git bisect start
git bisect bad <SHA_BAD>
git bisect good <SHA_GOOD>
# Test each checked-out commit, then mark:
git bisect good   # or git bisect bad
```
Continue until git identifies the first bad commit.

### Phase 3: Chesterton's Fence Forensic Analysis
Inspect the culprit commit:
1. What was the commit trying to achieve?
2. What ticket or requirement did it implement?
3. Why did the side-effect occur?

### Phase 4: Surgical Fix & Automated Regression Test
1. Author an automated unit or contract test that fails on the regression and passes once fixed.
2. Apply the minimal surgical correction preserving the original commit's intent.
3. Run the full verification test suite.
