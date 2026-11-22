/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.util

import okhttp3.OkHttpClient

/**
 * Shared application-wide base [OkHttpClient] singleton (`REQ-UI-2793`, `ATT-6037`).
 *
 * Centralizes OkHttp's [okhttp3.ConnectionPool] and [okhttp3.Dispatcher] so all production HTTP
 * callers reuse sockets, TLS sessions, and worker threads instead of instantiating isolated pools.
 * Callers requiring custom timeouts MUST derive via [newBuilder], which shallow-clones [shared]
 * while sharing its underlying connection pool and dispatcher.
 */
object AppHttpClient {

    /**
     * Shared default [OkHttpClient] instance for production callers using standard OkHttp timeouts.
     */
    @JvmStatic
    val shared: OkHttpClient by lazy {
        OkHttpClient()
    }

    /**
     * Returns an [OkHttpClient.Builder] derived from [shared] so derived clients share the same
     * [okhttp3.ConnectionPool] and [okhttp3.Dispatcher] while preserving custom timeout settings.
     */
    @JvmStatic
    fun newBuilder(): OkHttpClient.Builder = shared.newBuilder()
}
