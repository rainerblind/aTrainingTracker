---
name: release-manager
description: Orchestrates the production release lifecycle based on GitFlow. Handles release branch creation, version bumping, signed artifact builds, distribution preparation, GitFlow finish, branch pushes, version incrementation, and continuous release ticket checklist maintenance and audit commenting.
---

# Skill: release-manager

## Overview
This skill governs the end-to-end production release process. It standardizes release management across GitFlow branch management, build automation, signed artifact verification, distribution, real-time release ticket checklist maintenance, structured audit commenting, and version incrementation for the next development cycle.

---

## Release Ticket Governance: Real-Time Checklist & Audit Comments

The release ticket in the issue tracker must always reflect the exact live state of the release:

### 1. Checklist Maintenance in Ticket Description
The release ticket description follows a canonical release checklist. Whenever an agent completes one or more steps, the agent **MUST immediately update the ticket description**, marking completed items with a checkmark:

```markdown
* [x] Gitflow: new Release branch
* [x] Build: Update Version (remove -dev)
* [x] Build: Generate Signed Release Artifacts
* [x] Issue Tracker: Finish Release & Create new version
* [ ] Distribution: Upload to App Store / Distribution Channel
* [ ] Gitflow: Finish Release (merge to main & develop)
* [ ] Git: Push main & develop with version tags
* [ ] VCS: Create GitHub/GitLab Release with Changelog
* [ ] Build: Increment Version; add -dev to name for next cycle
```

### 2. Milestone Audit Comments
Whenever an agent executes release actions, the agent **MUST leave a structured progress comment** on the release ticket documenting:
* Executed step names
* Git branch and commit SHAs
* Output artifact file paths, file sizes, and verification checksums
* Any manual human actions required (e.g. store console review, deployment sign-off)

---

## Release Lifecycle Steps
1. **Branch Cut**: Create `release/vX.Y.Z` from `develop`.
2. **Version Bump**: Strip development flags (`-dev`) and synchronize build configs.
3. **Artifact Assembly**: Build release packages in clean-room environment.
4. **Validation**: Verify signature, minification, and regression tests.
5. **GitFlow Finish**: Merge `release/vX.Y.Z` into `main` and back into `develop` with release tag `vX.Y.Z`.
6. **Next Development Cycle**: Bump version to `vX.Y.(Z+1)-dev` on `develop`.
