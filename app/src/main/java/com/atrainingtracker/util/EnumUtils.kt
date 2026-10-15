package com.atrainingtracker.util

import android.util.Log

/**
 * ASPICE-compliant centralized engine for safe, exception-free enum deserialization
 * from external, untrusted, or evolved data sources (SQLite cursors, IPC Intents/Bundles,
 * JSON objects, and SharedPreferences).
 *
 * Guaranteed Invariants:
 * 1. Zero Uncaught Exception: Handles null, blank strings, whitespace, and unrecognized constants
 *    without throwing [NullPointerException], [IllegalArgumentException], [ClassCastException],
 *    or reflection errors.
 * 2. Forward & Backward Compatibility: Supports future/renamed enum constants gracefully.
 * 3. Observability: Emits structured warning logs on fallback/null deserialization.
 */
object EnumUtils {
    const val DEFAULT_TAG = "EnumUtils"

    /**
     * Safely deserializes [name] into an enum constant of type [enumClass].
     * If [name] is null, blank, or does not match any constant in [enumClass],
     * returns [defaultValue] and logs a diagnostic warning.
     */
    @JvmStatic
    @JvmOverloads
    fun <T : Enum<T>> safeValueOf(
        enumClass: Class<T>,
        name: String?,
        defaultValue: T,
        tag: String = DEFAULT_TAG
    ): T {
        if (name.isNullOrBlank()) {
            logWarning(tag, "Enum string is null or blank for ${enumClass.simpleName}; defaulting to ${defaultValue.name}")
            return defaultValue
        }
        val trimmed = name.trim()
        return try {
            java.lang.Enum.valueOf(enumClass, trimmed)
        } catch (t: Throwable) {
            logWarning(tag, "Unrecognized or evolved enum constant '$trimmed' for ${enumClass.simpleName}; defaulting to ${defaultValue.name}", t)
            defaultValue
        }
    }

    /**
     * Safely deserializes [name] into an enum constant of type [enumClass], or returns `null`
     * if [name] is null, blank, or does not match any constant in [enumClass].
     */
    @JvmStatic
    @JvmOverloads
    fun <T : Enum<T>> safeValueOfOrNull(
        enumClass: Class<T>,
        name: String?,
        tag: String = DEFAULT_TAG
    ): T? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()
        return try {
            java.lang.Enum.valueOf(enumClass, trimmed)
        } catch (t: Throwable) {
            logWarning(tag, "Unrecognized or evolved enum constant '$trimmed' for ${enumClass.simpleName}; returning null", t)
            null
        }
    }

    internal fun logWarning(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) {
                Log.w(tag, msg, tr)
            } else {
                Log.w(tag, msg)
            }
        } catch (_: Throwable) {
            // Fallback for JVM unit tests where android.util.Log is not mocked
        }
    }
}

/**
 * Kotlin-friendly inline reified extension for safe enum deserialization with fallback.
 */
inline fun <reified T : Enum<T>> safeEnumValueOf(
    name: String?,
    default: T,
    tag: String = EnumUtils.DEFAULT_TAG
): T = EnumUtils.safeValueOf(T::class.java, name, default, tag)

/**
 * Kotlin-friendly inline reified extension for safe enum deserialization returning null on mismatch.
 */
inline fun <reified T : Enum<T>> safeEnumValueOfOrNull(
    name: String?,
    tag: String = EnumUtils.DEFAULT_TAG
): T? = EnumUtils.safeValueOfOrNull(T::class.java, name, tag)
