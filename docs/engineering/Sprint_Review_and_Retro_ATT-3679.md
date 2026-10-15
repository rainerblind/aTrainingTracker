# Sprint Review & Retrospective: Sprint 2026-42.4

* **Ticket**: [ATT-3679](https://rainerblind.atlassian.net/browse/ATT-3679) (*Review & Retro - Sprint 2026-42.4*)
* **Sprint**: `2026-42.4` (Sprint ID `371`)
* **Branch**: `sprint/2026-42.4` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-15

---

## 1. Executive Summary & Review Outcomes

Sprint **2026-42.4** delivered a comprehensive set of 19 tickets spanning physiological training zones (7-zone Coggan cycling power model, automated baseline calculation, sensor-pairing onboarding, and horizontal stacked bar distributions), real-time cycling power metrics (`W/kg`, `NP`, `IF`, `TSS`, `kJ`, visual zone gauge bar, and pedal power balance indicator), sport-specific Auto-Pause, and codebase-wide stability & database hardening (`EnumUtils`, `LifecycleUtils`, `CursorUtils` with 100% `try-with-resources`, and targeted `LIMIT 1` crash-resume queries).

In accordance with **Rule 10 ("Install Before Review")**, the ceremony started by building and installing the integrated `sprint/2026-42.4` debug APK onto the attached Google Pixel 10 (`66020DLCR002FL`). All 19 tickets in `Final Review (Human)` were evaluated strictly one-by-one in backlog rank order per **Rule 7 ("Single-Ticket Focus & Strict Rank Order")**.

---

### 1.1 Review Evaluation Decisions (19 Evaluated Sprint Tickets)

| # | Ticket | Summary | Review Result | Status & Follow-up Actions |
| :---: | :--- | :--- | :---: | :--- |
| 1 | **[ATT-3387](https://rainerblind.atlassian.net/browse/ATT-3387)** | Show first-time swipe and tab coachmark on cockpit screen to guide stopping tracking | **Retained in Review** | Kept in `Final Review (Human)` for later verification. |
| 2 | **[ATT-3274](https://rainerblind.atlassian.net/browse/ATT-3274)** | Crop start and end points of a recorded workout | **Rejected (n.i.O.)** | Moved back to `Analysis`. App crashed on opening `CropWorkoutScreen` due to nested `Modifier.verticalScroll` inside `AppModalBottomSheet(scrollable = true)`. |
| 3 | **[ATT-3311](https://rainerblind.atlassian.net/browse/ATT-3311)** | Restore visibility of sole elevation profile on tracking screen without map | **Approved (i.O.)** | Verified on device. Fix Version `V4.9.40` set. |
| 4 | **[ATT-3184](https://rainerblind.atlassian.net/browse/ATT-3184)** | Migrate cycling power to 7-zone Coggan model while retaining 5-zone scheme for heart rate | **Retained in Review** | Kept in `Final Review (Human)`. Follow-up bug **[ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)** created with screenshot and ranked at top of backlog to replace header text buttons with compact symbol icons. |
| 5 | **[ATT-3391](https://rainerblind.atlassian.net/browse/ATT-3391)** | Extend elevation profile slope shading and legend to use full 7-color zone palette | **Approved (i.O.)** | Verified on device. Fix Version `V4.9.40` set. |
| 6 | **[ATT-3390](https://rainerblind.atlassian.net/browse/ATT-3390)** | Provide horizontal stacked distribution bar as alternative display mode in workout summary zone cards | **Approved with Follow-Up** | Verified on device. Fix Version `V4.9.40` set. Follow-up **[ATT-3676](https://rainerblind.atlassian.net/browse/ATT-3676)** created to expose default display mode selector in Advanced Settings. |
| 7 | **[ATT-3188](https://rainerblind.atlassian.net/browse/ATT-3188)** | Calculate training zone boundaries automatically from FTP and Heart Rate baselines | **Retained in Review** | Kept in `Final Review (Human)` until header bug **[ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)** is resolved. |
| 8 | **[ATT-3189](https://rainerblind.atlassian.net/browse/ATT-3189)** | Navigate to zone settings upon pairing new heart rate monitor or power meter when unconfigured | **Retained in Review** | Kept in `Final Review (Human)` for clean-install verification after **[ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)**. |
| 9 | **[ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)** | Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets | **Rejected & Reverted (n.i.O.)** | Reverted merge commit `8e218312` on `sprint/2026-42.4`, reinstalled APK, cleared Fix Version, and moved back to `Analysis` with requirement clarification to redraw existing icons 1:1 as vectors rather than substituting generic Material symbols. |
| 10 | **[ATT-45](https://rainerblind.atlassian.net/browse/ATT-45)** | Sport-specific auto-pause with configurable speed thresholds and debounce filtering | **Approved with Follow-Up** | Fix Version `V4.9.40` set. Follow-up **[ATT-3677](https://rainerblind.atlassian.net/browse/ATT-3677)** created to make speed thresholds and debounce windows configurable per sport (`Bike`, `Run`, `Other`). |
| 11 | **[ATT-1289](https://rainerblind.atlassian.net/browse/ATT-1289)** | Real-time power-to-weight ratio (W/kg) cockpit metric | **Approved (i.O.)** | Verified on device via `EditSensorFieldDialog` athlete weight setting. Fix Version `V4.9.40` set. |
| 12 | **[ATT-1290](https://rainerblind.atlassian.net/browse/ATT-1290)** | Live Normalized Power (NP) and Intensity Factor (IF) calculations | **Retained in Review** | Kept in `Final Review (Human)` until FTP is configured after **[ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)**. |
| 13 | **[ATT-1291](https://rainerblind.atlassian.net/browse/ATT-1291)** | Real-time Training Stress Score (TSS) and Mechanical Work (kJ) metrics | **Retained in Review** | Kept in `Final Review (Human)` until FTP is configured after **[ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)**. |
| 14 | **[ATT-1293](https://rainerblind.atlassian.net/browse/ATT-1293)** | Visual zone gauge tile across training zones in cockpit | **Retained in Review** | Kept in `Final Review (Human)` until header bug **[ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)** in `ZonesSettingsActivity` is resolved. |
| 15 | **[ATT-1295](https://rainerblind.atlassian.net/browse/ATT-1295)** | Visual pedal power balance indicator tile in cockpit | **Retained in Review** | Kept in `Final Review (Human)`. Follow-up **[ATT-3678](https://rainerblind.atlassian.net/browse/ATT-3678)** created to add Pedal Power Balance simulation to `tools/ble_power_simulator.py`. |
| 16 | **[ATT-3562](https://rainerblind.atlassian.net/browse/ATT-3562)** | Replace Unguarded `Enum.valueOf` Calls on Cursors, Intents, and Preferences with Safe Deserialization | **Approved (i.O.)** | Verified via walkthrough and direct git diff code review (`7ae40886`). Fix Version `V4.9.40` set. |
| 17 | **[ATT-3563](https://rainerblind.atlassian.net/browse/ATT-3563)** | Harden Service Launches and BroadcastReceiver Unregistration Against Android Lifecycle Exceptions | **Approved (i.O.)** | Verified via walkthrough and direct git diff code review (`cef6aefe`). Fix Version `V4.9.40` set. |
| 18 | **[ATT-3550](https://rainerblind.atlassian.net/browse/ATT-3550)** | Enforce Try-With-Resources and Cursor Bounds Checks Across Legacy Java Database Managers | **Approved (i.O.)** | Verified via walkthrough and direct git diff code review (`b46920f6`). Fix Version `V4.9.40` set. |
| 19 | **[ATT-3549](https://rainerblind.atlassian.net/browse/ATT-3549)** | Replace Unbounded Full-Table Query in `TrackerService` Crash-Resume with Targeted `LIMIT 1` Query | **Approved (i.O.)** | Verified via walkthrough and direct git diff code review (`806938b0`). Fix Version `V4.9.40` set. |

---

### 1.2 Backlog Follow-Up Tickets Created During Joint Review

In compliance with **Rules 8 & 9 ("No Code Changes During Review & Lean Defect Recording")**, four follow-up tickets were created and ranked at the top of the backlog:

1. **[ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)**: `Replace overflowing text buttons in ZonesSettingsActivity header with compact icon actions to prevent vertical screen blowout` (with screenshot `ATT-3184_review_screenshot.png` attached).
2. **[ATT-3676](https://rainerblind.atlassian.net/browse/ATT-3676)**: `Expose default zone card display mode selector in Advanced Settings and wire it to workout summary zone cards`.
3. **[ATT-3677](https://rainerblind.atlassian.net/browse/ATT-3677)**: `Configure auto-pause speed thresholds and debounce windows independently per sport (Bike, Run, Other)`.
4. **[ATT-3678](https://rainerblind.atlassian.net/browse/ATT-3678)**: `Add Pedal Power Balance simulation support to tools/ble_power_simulator.py`.

---

## 2. Retrospective Insights & Permanent Countermeasures

### 2.1 Process Improvement: Mandatory Code-Diff Review During Ceremony 2 (Joint Review)
* **User Feedback**: *"Within the last tickets, you also did a review of the code-changes. I would like to keep this."*
* **Insight**: Presenting only the markdown walkthrough summary during Ceremony 2 leaves out direct verification of the actual commit diff (`git show`). Providing a concise, structured code review of the actual production diff alongside the walkthrough gives immediate transparency into how edge cases, fallbacks, resource management, and architectural invariants were implemented in code—and makes reviewing backend/architectural tickets fast and decisive.
* **Permanent Countermeasure**: Codified in `.agents/rules/aspice_governance.md` (**Rule 7**) and `.agents/skills/sprint-planner/SKILL.md` (**Ceremony 2, Step 3**): For every ticket presented in `Final Review (Human)`, the agent MUST inspect and present a concise **Code Review of the actual git diff** alongside the walkthrough summary.

### 2.2 UI Layout Invariant: Compact Icon Actions in Top Headers (ATT-3184 / ATT-3188 -> ATT-3675)
* **Root Cause**: `ATT-3184` added a `"Reset to Defaults"` `TextButton` and `ATT-3188` added a `"Calculated"` `SuggestionChip` + `"Calculate Zones"` `TextButton` into the same horizontal header `Row` in `ZonesSettingsActivity.kt`. In German (`"Trainings Zonen"`, `"Berechnet"`, `"Zonen berechnen"`, `"Auf Standardwerte zurücksetzen"`), the four horizontal items exceeded the phone viewport width, squeezing the last `TextButton` into a 1-character-wide column that wrapped vertically across ~95% of the screen height (~800dp).
* **Permanent Countermeasure**: Codified in `docs/design_guidelines.md` (**Section 5.6**): Top screen headers (`Row`) must never pack multiple multi-word `TextButton`s alongside a title and status chip; use compact `IconButton` symbol actions (with `contentDescription` tooltips) or a `FlowRow`.

### 2.3 Compose Layout Invariant: No Nested `verticalScroll` Inside `AppModalBottomSheet` (ATT-3274)
* **Root Cause**: `AppModalBottomSheet` defaults to `scrollable = true`, wrapping its content slot in a `Column(Modifier.verticalScroll(rememberScrollState()))`. `CropWorkoutScreen.kt` placed a second `Column(Modifier.verticalScroll(rememberScrollState()))` inside `AppModalBottomSheet` without passing `scrollable = false`, causing Compose to crash immediately at runtime with `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`.
* **Permanent Countermeasure**: Codified in `docs/design_guidelines.md` (**Section 5.8**): Any screen hosted inside `AppModalBottomSheet` must rely on the sheet's built-in scroll container (`scrollable = true`) and NEVER nest a second `Modifier.verticalScroll` (or must explicitly pass `scrollable = false` when managing its own internal scroll state).

### 2.4 Asset Migration Invariant: 1:1 Visual Fidelity When Vectorizing Existing Icons (ATT-2859)
* **Root Cause**: When migrating legacy raster PNG drawables (`bsport_bike`, `bsport_run`, `bsport_other`, ANT+ badges, BLE badges) to XML `VectorDrawable`s, the implementation replaced the custom multi-color icons and square protocol badges with generic monochrome Material Symbols.
* **Permanent Countermeasure**: Codified in `docs/design_guidelines.md` (**Section 5.5**): Vectorizing existing raster icons must faithfully reconstruct the exact visual design, multi-color geometry, and badge layout of the original assets rather than substituting generic Material Design icons.
