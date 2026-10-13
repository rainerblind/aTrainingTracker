# Stage 1 Analysis: ATT-2195 - Fix Missing Bullet Point Line Breaks in Cluster Info Explanation Dialog

**Ticket**: [ATT-2195](https://rainerblind.atlassian.net/browse/ATT-2195)  
**Sub-task**: [ATT-2366](https://rainerblind.atlassian.net/browse/ATT-2366) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2195`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Problem Statement

In the workout clustering explanation dialog (`ClusterInfoDialog.kt`), the educational bottom sheet explains the 3D topological clustering algorithm ("Lieblingsstrecken" / Favorite Tracks) across four structured sections:
1. **Was sind Lieblingsstrecken?** (`cluster_info_what_title`, `cluster_info_what_desc`)
2. **Topologischer 3D-Fingerabdruck** (`cluster_info_fingerprint_title`, `cluster_info_fingerprint_desc`)
3. **Sportart-Filter & Lernendes Zentrum** (`cluster_info_sports_title`, `cluster_info_sports_desc`)
4. **Feinabstimmung & Empfindlichkeit** (`cluster_info_tuning_title`, `cluster_info_tuning_desc`)

### The Defect
In Section 2, the algorithm's four spatial criteria (Start & Finish, Apex, Total Distance, and Min/Max Altitude) were formatted with bullet points (`•`). However, in `ClusterInfoDialog`, these bullet points do not appear on separate lines. Instead, the entire description collapses into a single continuous wall of text without any line breaks between the criteria.

This formatting collapse severely harms readability, making it difficult for athletes to read the individual tolerances (200 m, 400 m, 20%) when configuring or tuning route recognition.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic investigation into the resource files across all 9 localized directories (`res/values*/strings.xml`) identified the precise mechanism of the defect:

### 2.1 The Misconception of XML Character Entity `&#10;` in Android Resources
In all 9 localized `strings.xml` files, the string resource `cluster_info_fingerprint_desc` was written using the numeric XML entity `&#10;` (ASCII decimal 10 / line feed) to separate paragraphs and bullet points:
```xml
<string name="cluster_info_fingerprint_desc">Routes are compared using a simple but surprisingly good algorithm based on a multidimensional spatial signature rather than point-by-point GPS traces. This algorithm gives some tolerance to small variations, but separates the workouts when they are really different:&#10;• Start &amp; End: Geographical start and finish locations (default tolerance: 200 m).&#10;• Apex (Furthest Point): The point along the route furthest away from the start (default tolerance: 400 m).&#10;• Total Distance: Overall route length (default tolerance: 20%).&#10;• Min &amp; Max Altitude: Geographical locations of lowest and highest elevation (default tolerance: 400 m, optional). This reliably separates valley routes from mountain ridges with otherwise similar footprints.</string>
```

### 2.2 AAPT2 Whitespace Normalization
Android's Asset Packaging Tool 2 (AAPT2) parses XML files according to Android string resource rules:
1. When an XML character reference such as `&#10;` is parsed without surrounding double quotation marks (`"..."`), AAPT2 treats it as plain whitespace.
2. In accordance with standard Android resource compiler specification, AAPT2 collapses consecutive whitespace characters and newlines into a single space (`0x20`).
3. Consequently, at runtime, `stringResource(R.string.cluster_info_fingerprint_desc)` returns a string where every `&#10;` is replaced with a single space ` `, causing the four bullet items to blend directly into the introductory paragraph.

### 2.3 Verified Established Pattern in the Codebase
To enforce preserved newlines in Android string resources, the standard Android mechanism is escaping the newline character with a backslash: `\n`.
Other multiline dialog messages in the codebase (e.g., `strava_disconnect_dialog_message` in `values/strings.xml` line 1174) successfully use `\n\n• ...\n• ...` to achieve clean, crisp bulleted lists in Compose dialogs.

---

## 3. Proposed Resolution & Architecture

### 3.1 9-Language Localization Update
Replace all occurrences of `&#10;` with literal `\n` in `cluster_info_fingerprint_desc` across all 9 supported application locales:
1. `app/src/main/res/values/strings.xml` (English)
2. `app/src/main/res/values-de/strings.xml` (German)
3. `app/src/main/res/values-es/strings.xml` (Spanish)
4. `app/src/main/res/values-fr/strings.xml` (French)
5. `app/src/main/res/values-it/strings.xml` (Italian)
6. `app/src/main/res/values-ja/strings.xml` (Japanese)
7. `app/src/main/res/values-nl/strings.xml` (Dutch)
8. `app/src/main/res/values-pl/strings.xml` (Polish)
9. `app/src/main/res/values-pt/strings.xml` (Portuguese)

### 3.2 Visual & Structural Verification
In `ClusterInfoDialog.kt`, Compose `Text` automatically renders newline characters `\n` as distinct line breaks. Replacing `&#10;` with `\n` ensures each of the 4 bullet points begins on its own line immediately below the introductory sentence.

---

## 4. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Refines `REQ-UI-137` (*Cluster Algorithm & Lieblingsstrecken Educational Info Modal*).
2. **Historical Origin & Commit Trace**:
   - Sprint `2026-40.4` (`ATT-1422` / `ATT-1449`, commit `f7136067`).
3. **Root Reason for Existing Formulation**:
   - The author used the XML numeric entity `&#10;` under the assumption that XML entities would be preserved literally by the resource compiler. Because AAPT2 normalizes unquoted whitespace, this resulted in an unintended line break collapse.
4. **Preservation of Core Invariants**:
   - The technical explanation, tolerance values (200 m, 400 m, 20%), 3D spatial dimensions, and localized wording across all 9 languages are 100% strictly preserved.
   - `ClusterInfoDialog.kt` composable layout, scrolling behavior, title, icon, and dismiss actions remain completely unchanged.

---

## 5. User Scope Grounding (`ATT-1250`)

* **In-Scope**:
  - Replace `&#10;` with `\n` in `cluster_info_fingerprint_desc` across all 9 `strings.xml` files.
  - Implement automated architectural unit test verifying:
    * `cluster_info_fingerprint_desc` contains zero instances of `&#10;`.
    * Contains at least 4 `\n•` occurrences (or `\n` bullet line breaks) across all 9 language packs.
    * 100% 9-language parity without missing or empty translations.
* **Out-of-Scope**:
  - Altering the mathematical clustering engine or database schema.
  - Changing cluster tuning sliders or sensitivity presets.
  - Modifying other sections of `ClusterInfoDialog`.

---

## 6. Verification & Test Strategy

1. **Automated Unit & Contract Test (`ClusterInfoDialogContractTest.kt`)**:
   - Validate that across all 9 locales:
     * `cluster_info_fingerprint_desc` is present and non-empty.
     * `cluster_info_fingerprint_desc` does not contain `&#10;`.
     * `cluster_info_fingerprint_desc` contains literal `\n` escapes separating each bullet item.
2. **Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
   - Build debug APK via `./gradlew assembleDebug`.
