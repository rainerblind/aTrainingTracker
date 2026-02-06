package com.atrainingtracker.trainingtracker.util

import android.util.Log

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * This prevents the event from being fired again on configuration changes (like screen rotation).
 */
open class Event<out T>(private val content: T) {

    private var hasBeenHandled = false

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        Log.i("ASDFEvent", "getContentIfNotHandled")
        return if (hasBeenHandled) {
            Log.i("ASDFEvent", "getContentIfNotHandled: hasBeenHandled")
            null
        } else {
            Log.i("ASDFEvent", "getContentIfNotHandled: !hasBeenHandled")
            hasBeenHandled = true
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content
}