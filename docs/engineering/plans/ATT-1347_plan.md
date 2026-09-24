# Implementation Plan - ATT-1347: NoSuchMethodError in AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive

**Ticket**: [ATT-1347](https://rainerblind.atlassian.net/browse/ATT-1347)  
**Sub-task**: [ATT-1358](https://rainerblind.atlassian.net/browse/ATT-1358)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**:
* `androidx.core:core:1.15.0-patched` (`local-repo/androidx/core/core/1.15.0-patched/`)
* `settings.gradle` (Local Maven Repository configuration)
* `app/build.gradle` (Gradle Dependency Substitution & Pinning)
* `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityEventCompatResilienceTest.kt`
**Requirement**: `REQ-UI-164` (Accessibility Event Dispatch Compatibility & Defective Platform Resilience)  
**Test Spec ID**: `TST-UI-116`  
**Branch**: `bugfix/ATT-1347`  

---

## 1. Technical Architecture & Modifications

### 1.1 Local Maven Repository & Patched Core Artifact
To resolve the production crash while strictly avoiding D8 duplicate class merger collisions (`DexArchiveMergerException`), we implement targeted Gradle dependency substitution backed by a committed local Maven repository:

1. **Repository Structure**:
   ```text
   local-repo/
   └── androidx/core/core/1.15.0-patched/
       ├── core-1.15.0-patched.aar
       ├── core-1.15.0-patched.pom
       └── README.md
   ```

2. **Patched Bytecode Modification in `core-1.15.0-patched.aar`**:
   Within `classes.jar` of the artifact, `androidx/core/view/accessibility/AccessibilityEventCompat$Api34Impl.class` is compiled from Java source targeting Java 8 bytecode compatibility:
   ```java
   @RequiresApi(34)
   static class Api34Impl {
       private Api34Impl() {}

       static boolean isAccessibilityDataSensitive(AccessibilityEvent event) {
           try {
               return event.isAccessibilityDataSensitive();
           } catch (LinkageError e) {
               Log.w(TAG, "isAccessibilityDataSensitive failed on platform; suppressing error", e);
               return false;
           }
       }

       static void setAccessibilityDataSensitive(AccessibilityEvent event,
               boolean accessibilityDataSensitive) {
           try {
               event.setAccessibilityDataSensitive(accessibilityDataSensitive);
           } catch (LinkageError e) {
               Log.w(TAG, "setAccessibilityDataSensitive missing on platform framework; suppressing error", e);
           }
       }
   }
   ```
   - **Exception Target**: Specifically catches `LinkageError` (which covers `NoSuchMethodError` and `IncompatibleClassChangeError`).
   - **Safety Boundary**: Fatal non-linkage JVM errors (e.g., `OutOfMemoryError`, `ThreadDeath`) are NOT caught and propagate as required by safety invariants.

### 1.2 Gradle Dependency Resolution Modernization

1. **`settings.gradle`**:
   Declare the project-local Maven repository within `dependencyResolutionManagement`:
   ```groovy
   dependencyResolutionManagement {
       repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
       repositories {
           maven { url uri("${rootDir}/local-repo") }
           google()
           mavenCentral()
           maven { url 'https://jitpack.io' }
       }
   }
   ```

2. **`app/build.gradle`**:
   Configure deterministic dependency substitution and forced resolution:
   ```groovy
   configurations.all {
       resolutionStrategy {
           // ATT-1347: Substitute androidx.core:core:1.15.0 with local patched artifact to eliminate
           // NoSuchMethodError in AccessibilityEventCompat$Api34Impl on Android 14 (API 34)
           dependencySubstitution {
               substitute module('androidx.core:core:1.15.0') using module('androidx.core:core:1.15.0-patched')
           }
           // ATT-1037 / ATT-1347: Pin androidx.core to 1.15.0-patched
           force 'androidx.core:core:1.15.0-patched'
           force 'androidx.core:core-ktx:1.15.0'
       }
   }
   ```

### 1.3 Test Suite Implementation
Implement `AccessibilityEventCompatResilienceTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/`:
- Verifies that `AccessibilityEventCompat.setAccessibilityDataSensitive` intercepts simulated `NoSuchMethodError` cleanly.
- Verifies that `AccessibilityEventCompat.isAccessibilityDataSensitive` returns `false` safely when platform method is missing.
- Verifies that `OutOfMemoryError` is not swallowed.
- Verifies intact platform parity on standard runtimes.

---

## 2. Step-by-Step Implementation Sequence

1. **Step 1: Artifact & Build Configuration Setup** (Completed & Committed: `d00997ed` / `ef1f1883`):
   - Local repository `local-repo/androidx/core/core/1.15.0-patched/` initialized.
   - `settings.gradle` updated with `local-repo` maven repository.
   - `app/build.gradle` updated with `dependencySubstitution` and `resolutionStrategy.force`.
   - `README.md` added documenting patch provenance, Apache-2.0 compliance, and re-upgrade milestone (`ATT-1091`).

2. **Step 2: Exception Handling Refinement** (Completed & Committed: `bde85d7d`):
   - Recompiled `AccessibilityEventCompat$Api34Impl.class` with `catch (LinkageError e)`.
   - Verified bytecode disassembly via `javap -c`.

3. **Step 3: Automated Unit Test Suite Creation**:
   - Create `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityEventCompatResilienceTest.kt`.
   - Execute `./gradlew testDebugUnitTest --tests "*.AccessibilityEventCompatResilienceTest"` to verify all test cases pass.

4. **Step 4: Comprehensive Build Verification**:
   - Run `./gradlew assembleDebug` to verify debug packaging.
   - Run `./gradlew assembleRelease` to verify D8 monolithic dex merging (`mergeDexRelease`), lint vital analysis (`lintVitalRelease`), and release packaging (`packageRelease`).
   - Run `./gradlew testDebugUnitTest` to confirm zero regressions across all 626+ existing unit tests.

---

## 3. System Invariants & Preserved Behavior

* **INV-ACC-01 (Direct Call-Site Safety)**: Exceptions arising from missing OEM platform methods are caught directly at the invocation site in `Api34Impl`, preventing unhandled errors from escaping into Compose UI coroutines or the Main Looper.
* **INV-ACC-02 (Comprehensive Crash Immunity)**: 100% crash immunity on defective Android 14 builds across all build configurations (Debug, Profile, Release) with zero reliance on compiler minification heuristics.
* **INV-ACC-03 (Main Thread & Looper Stability)**: Zero infinite dispatch retry loops, zero UI thread freezing, and zero CPU starvation.
* **INV-ACC-04 (API Contract & Intact Platform Parity)**: On intact Android 14+ devices where the method exists, the platform API is invoked normally without behavioral divergence. Non-linkage fatal errors (`OutOfMemoryError`) are never swallowed.
* **INV-ACC-05 (Build Toolchain Cleanliness)**: Zero duplicate classes across dex archives, no D8/R8 merger conflicts, and no package-spoofing in `app/src/main/java`.

---

## 4. Rollback & Migration Strategy
- When upstream Google AndroidX Core releases a future version (e.g. `1.20.0+`) that defensively guards API 34 calls (tracked in `ATT-1091`):
  1. Remove `dependencySubstitution` from `app/build.gradle`.
  2. Remove `local-repo` maven repository from `settings.gradle`.
  3. Delete `local-repo/`.
  4. Run `./gradlew assembleRelease` and `./gradlew testDebugUnitTest` to verify clean transition.
