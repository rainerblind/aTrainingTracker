package com.atrainingtracker.trainingtracker.ui.common

import android.os.Build
import android.util.Log

/**
 * Defensive runtime guard that intercepts and neutralizes platform class linkage failures
 * (`NoClassDefFoundError` on `android.view.inputmethod.SelectGesture`) during Jetpack Compose
 * text input focus on fragmented Android 14+ OEM platforms (ATT-3163, REQ-UI-342).
 *
 * When focusing any non-password text field in Compose, Compose's internal [androidx.compose.foundation.text.input.internal.EditorInfo_androidKt.update]
 * queries `StylusHandwriting_androidKt.isStylusHandwritingSupported` (which naively checks `Build.VERSION.SDK_INT >= 34`).
 * If true, it invokes `EditorInfoApi34.setHandwritingGestures`, referencing `SelectGesture.class`. On fragmented
 * devices where `SelectGesture` was omitted by the OEM from `framework.jar`, ART throws an uncatchable
 * `NoClassDefFoundError` that crashes the application process.
 *
 * This resilience component safely probes `SelectGesture` availability at startup. If missing, it dynamically
 * flips `isStylusHandwritingSupported` to `false`, gracefully instructing Compose to skip handwriting gesture
 * initialization without degrading standard virtual keyboard entry, touch selection, or typing.
 */
object ComposeHandwritingResilience {
    private const val TAG = "ComposeResilience"

    /**
     * Installs the runtime guard. Probes for `android.view.inputmethod.SelectGesture` on Android 14+ (API 34+)
     * and disables Compose stylus handwriting if the framework class is missing.
     */
    @JvmStatic
    fun install() {
        if (Build.VERSION.SDK_INT < 34) {
            // Android versions below API 34 have isStylusHandwritingSupported set to false by default.
            return
        }

        val isGestureClassAvailable = try {
            Class.forName("android.view.inputmethod.SelectGesture")
            true
        } catch (_: Throwable) {
            false
        }

        if (!isGestureClassAvailable) {
            logW(
                "android.view.inputmethod.SelectGesture is missing on this Android ${Build.VERSION.SDK_INT} device. " +
                    "Disabling Compose stylus handwriting to prevent fatal NoClassDefFoundError."
            )
            disableComposeStylusHandwriting()
        } else {
            logD("android.view.inputmethod.SelectGesture verified on platform.")
        }
    }

    /**
     * Safely modifies `StylusHandwriting_androidKt.isStylusHandwritingSupported` to `false`
     * using memory reflection with Unsafe fallback via dynamic reflection.
     *
     * @return `true` if the field was successfully updated to `false`, `false` otherwise.
     */
    @JvmStatic
    fun disableComposeStylusHandwriting(): Boolean {
        return try {
            val clazz = Class.forName("androidx.compose.foundation.text.handwriting.StylusHandwriting_androidKt")
            val field = clazz.getDeclaredField("isStylusHandwritingSupported")
            field.isAccessible = true

            // Attempt 1: Standard reflection (works on ART and pre-Java 17 runtimes)
            try {
                field.setBoolean(null, false)
                logI("Compose isStylusHandwritingSupported disabled via standard reflection.")
                return true
            } catch (_: Throwable) {
                // Fall through to Unsafe reflection
            }

            // Attempt 2: Dynamic sun.misc.Unsafe reflection (bypasses JVM 17+ static final field restrictions without compile-time imports)
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true
            val unsafe = theUnsafeField.get(null)

            val staticFieldBaseMethod = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            val staticFieldOffsetMethod = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            val putBooleanMethod = unsafeClass.getMethod(
                "putBoolean",
                Any::class.java,
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            val base = staticFieldBaseMethod.invoke(unsafe, field)
            val offset = staticFieldOffsetMethod.invoke(unsafe, field) as Long
            putBooleanMethod.invoke(unsafe, base, offset, false)

            logI("Compose isStylusHandwritingSupported disabled via dynamic Unsafe.")
            true
        } catch (t: Throwable) {
            logW("Could not adjust Compose isStylusHandwritingSupported: ${t.message}")
            false
        }
    }

    private fun logW(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
            System.err.println("[$TAG] $message")
        }
    }

    private fun logI(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            println("[$TAG] $message")
        }
    }

    private fun logD(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // No-op in test environments without mocked Log
        }
    }
}
