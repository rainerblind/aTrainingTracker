# Stage 1 Analysis: ATT-7534 - Replace Broken Google Drive and Unofficial Health Connect Vectors

**Ticket**: [ATT-7534](https://rainerblind.atlassian.net/browse/ATT-7534)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7534` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In the *Synchronisation* section of `AppNavigationDrawer.kt` and *Import & Backup* (`RestoreTabContent.kt`, `GoogleDriveSettingsDialog.kt`, `HealthConnectSettingsDialog.kt`), two third-party service icons are defective:
1. `ic_google_drive.xml` uses 3 hand-approximated polygons with a detached, crooked blue base and an open gap between green and yellow segments.
2. `ic_health_connect.xml` renders an invented 4-color snowman-in-heart graphic instead of the official Google Health Connect interlocking dual-loop brand logo.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  1. *Hand-Crafted Approximate Vectors*: `ic_google_drive.xml` was introduced in `ATT-1306` with 3 coarse path polygons (`M8.01,2.02...`). `ic_health_connect.xml` was introduced in `ATT-6711` using arbitrary circular nodes (`M12,8C...`, `M12,13C...`) violating Google's brand guidelines.
  2. *Dark Mode Contrast Challenge*: The official Health Connect left loop is Dark Teal (`#003144`). On dark surfaces (`#1E1E1E` in drawer and dialogs), `#003144` lacks WCAG AA contrast if rendered unbacked.
* **Independent Peer Review & Verification**:
  * Verified in `app/src/main/res/drawable/ic_google_drive.xml`: Confirmed 3-path polygon geometry with disconnected coordinates.
  * Verified in `app/src/main/res/drawable/ic_health_connect.xml`: Confirmed non-standard 4-color heart and circle nodes.
  * *Official Asset Sourcing*: Verified authentic SVG at `developer.android.com/static/images/health-connect/health-connect.svg`. Retaining inner white `#FFFFFF` fills or providing `drawable-night/ic_health_connect.xml` ensures crisp contrast across both Light and Dark themes.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Replace `ic_google_drive.xml` with the official 6-segment Google Drive vector geometry.
  2. Replace `ic_health_connect.xml` with the official interlocking-loop Health Connect vector geometry (and ensure high dark-theme contrast).
  3. Add vector contract unit tests verifying official color tokens, analytical path commands, and absence of broken polygons.
* **Out-of-Scope Non-Goals**:
  * Modifying Google Drive OAuth or Health Connect API synchronization logic.
  * Changing drawer layout, item IDs (`drawer_google_drive`, `drawer_health_connect`), or route bindings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-476` (*Brand Vector & Icon Asset Visual Fidelity*) and `REQ-SET-103.1` (*Health Connect Drawer Entry*).
* **Historical Origin & Commit Trace**: `931bf45d` (`ATT-1306`) and `8abca7ef` (`ATT-6711`, Sprint 2026-46.2).
* **Root Reason for Existing Formulation**: Placeholders were quickly authored during backend service wiring without importing authentic vector masters.
* **Preservation of Core Invariants**: 24dp sizing, `tintIcon = false` in `AppNavigationDrawer`, and resource ID bindings remain 100% intact.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-476` (amended in `req_ui_design_system.md`), `REQ-SET-103.1` in `req_settings_profiles.md`.
* **Affected Architecture Elements**: `res/drawable/ic_google_drive.xml`, `res/drawable/ic_health_connect.xml`, `res/drawable-night/ic_health_connect.xml` (if needed for dark theme contrast).
* **Verification Impact & Test Surface**: `VectorAssetResolutionContractTest.kt` (or dedicated `BrandIconContractTest.kt`), AAPT2 resource compilation (`./gradlew processDebugResources`).
* **Risk Assessment & Invariants**: LOW risk. Pure vector XML asset update. Zero Java/Kotlin runtime changes; zero database impact.

---

## 6. Architectural Strategy & High-Level Solution
1. **Google Drive Vector**: Update `ic_google_drive.xml` to 6 paths with 2020+ colors (`#0066DA`, `#00AC47`, `#EA4335`, `#00832D`, `#2684FC`, `#FFBA00`) centered in 87.3x87.3 viewport.
2. **Health Connect Vector**: Update `ic_health_connect.xml` with official interlocking loops (`#003144`, `#4285F4`, and node `#4285F4`), with contrast-preserving interior fills.
3. **Contract Test**: Assert official colors and path counts in `BrandIconContractTest.kt`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero change to navigation drawer selection; `tintIcon = false` preserved; resource IDs `R.drawable.ic_google_drive` and `R.drawable.ic_health_connect` stable.
* **Risk Rating**: **LOW** (static vector drawable replacement verified by resource compilation and unit tests).
