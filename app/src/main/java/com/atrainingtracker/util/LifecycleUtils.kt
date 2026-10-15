package com.atrainingtracker.util

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ASPICE-compliant centralized engine for lifecycle-safe service operations and
 * idempotent broadcast receiver unregistration across external lifecycle boundaries.
 *
 * Enforces REQ-STB-016 (ATT-3563 / ATT-235):
 * 1. Crash Immunity: Absorbs IllegalStateException (BackgroundServiceStartNotAllowedException),
 *    SecurityException, and IllegalArgumentException ("Receiver not registered").
 * 2. Idempotence: Allows repeated or speculative receiver unregistration during teardown.
 * 3. Observability: Emits structured WARN logs (Log.w) with caller tag and receiver runtime class.
 * 4. JVM Test Safety: Resilient logging helper falls back gracefully when Log is unmocked.
 */
object LifecycleUtils {
    const val DEFAULT_TAG = "LifecycleUtils"

    /**
     * Safely attempts to start a service via [context].startService([intent]).
     *
     * Catches and absorbs [IllegalStateException] (such as [android.app.BackgroundServiceStartNotAllowedException]
     * or [android.app.ForegroundServiceStartNotAllowedException] on API 31+), [SecurityException],
     * and any runtime platform exception.
     *
     * @return The [ComponentName] if the service was started, or `null` if the OS rejected the launch
     *         or if [context] or [intent] was null.
     */
    @JvmStatic
    @JvmOverloads
    fun safeStartService(
        context: Context?,
        intent: Intent?,
        tag: String = DEFAULT_TAG
    ): ComponentName? {
        if (context == null || intent == null) {
            logWarning(tag, "safeStartService skipped: context ($context) or intent ($intent) is null")
            return null
        }
        return try {
            context.startService(intent)
        } catch (e: IllegalStateException) {
            logWarning(tag, "Failed to start service (background execution restricted by OS): ${e.message}", e)
            null
        } catch (e: SecurityException) {
            logWarning(tag, "Failed to start service (security/permission denied): ${e.message}", e)
            null
        } catch (t: Throwable) {
            logWarning(tag, "Failed to start service (unexpected exception): ${t.message}", t)
            null
        }
    }

    /**
     * Safely unregisters [receiver] from [context].
     *
     * Catches and absorbs [IllegalArgumentException] (thrown when the receiver was not registered
     * or was already unregistered) and [IllegalStateException].
     *
     * @return `true` if unregistration succeeded, or `false` if skipped or caught an exception.
     */
    @JvmStatic
    @JvmOverloads
    fun safeUnregisterReceiver(
        context: Context?,
        receiver: BroadcastReceiver?,
        tag: String = DEFAULT_TAG
    ): Boolean {
        if (context == null || receiver == null) {
            return false
        }
        return try {
            context.unregisterReceiver(receiver)
            true
        } catch (e: IllegalArgumentException) {
            logWarning(tag, "BroadcastReceiver unregistration skipped (receiver was not registered or already unregistered): ${receiver.javaClass.simpleName} - ${e.message}", e)
            false
        } catch (e: IllegalStateException) {
            logWarning(tag, "BroadcastReceiver unregistration failed (lifecycle state error): ${receiver.javaClass.simpleName} - ${e.message}", e)
            false
        } catch (t: Throwable) {
            logWarning(tag, "BroadcastReceiver unregistration failed (unexpected error): ${receiver.javaClass.simpleName} - ${t.message}", t)
            false
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
 * Kotlin-friendly extension function on nullable [Context] for safe service starting.
 */
fun Context?.safeStartService(intent: Intent?, tag: String = LifecycleUtils.DEFAULT_TAG): ComponentName? {
    return LifecycleUtils.safeStartService(this, intent, tag)
}

/**
 * Kotlin-friendly extension function on nullable [Context] for safe receiver unregistration.
 */
fun Context?.safeUnregisterReceiver(receiver: BroadcastReceiver?, tag: String = LifecycleUtils.DEFAULT_TAG): Boolean {
    return LifecycleUtils.safeUnregisterReceiver(this, receiver, tag)
}
