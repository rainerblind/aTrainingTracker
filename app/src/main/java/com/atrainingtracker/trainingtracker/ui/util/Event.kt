package com.atrainingtracker.trainingtracker.ui.util

import android.util.Log
import androidx.lifecycle.Observer

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

/**
 * An [Observer] for [Event]s, simplifying the process of checking if the Event's
 * content has already been handled.
 *
 * [onEventUnhandledContent] is *only* called if the Event's contents have not been handled.
 */
class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(event: Event<T>) {
        event.getContentIfNotHandled()?.let { content ->
            onEventUnhandledContent(content)
        }
    }
}