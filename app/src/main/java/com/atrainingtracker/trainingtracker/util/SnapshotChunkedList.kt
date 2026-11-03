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

import java.util.RandomAccess

/**
 * An immutable, random-access list structure optimized for append-heavy live tracking telemetry
 * (REQ-TRK-042.1, ATT-4923).
 *
 * Rather than reallocating and copying the entire list on every append ($O(N)$ copies),
 * [SnapshotChunkedList] groups elements into fixed-size chunks ($C = 256$).
 * Appends copy at most the active tail chunk ($\le 256$ references), achieving amortized $O(1)$
 * allocation and time complexity.
 *
 * Exposes an immutable snapshot suitable for concurrent reads across coroutines and Compose
 * without risk of [ConcurrentModificationException].
 *
 * Equality check [equals] evaluates size difference in $O(1)$ before inspecting elements,
 * providing immediate emission triggers for downstream `StateFlow` subscribers.
 */
class SnapshotChunkedList<E> private constructor(
    private val chunks: Array<Array<Any?>>,
    private val activeTail: Array<Any?>,
    private val tailCount: Int,
    override val size: Int
) : AbstractList<E>(), RandomAccess {

    override fun get(index: Int): E {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
        val chunkIndex = index shr SHIFT
        val offset = index and MASK
        @Suppress("UNCHECKED_CAST")
        return if (chunkIndex < chunks.size) {
            chunks[chunkIndex][offset] as E
        } else {
            activeTail[offset] as E
        }
    }

    /**
     * Appends [element] to the end of this list and returns a new [SnapshotChunkedList] snapshot
     * in amortized $O(1)$ time and memory.
     */
    fun append(element: E): SnapshotChunkedList<E> {
        return if (tailCount < CHUNK_SIZE && activeTail.isNotEmpty()) {
            val newTail = activeTail.copyOf()
            newTail[tailCount] = element
            SnapshotChunkedList(chunks, newTail, tailCount + 1, size + 1)
        } else if (activeTail.isEmpty()) {
            val newTail = arrayOfNulls<Any?>(CHUNK_SIZE)
            newTail[0] = element
            SnapshotChunkedList(chunks, newTail, 1, size + 1)
        } else {
            @Suppress("UNCHECKED_CAST")
            val newChunks = java.util.Arrays.copyOf(chunks, chunks.size + 1) as Array<Array<Any?>>
            newChunks[chunks.size] = activeTail
            val newTail = arrayOfNulls<Any?>(CHUNK_SIZE)
            newTail[0] = element
            SnapshotChunkedList(newChunks, newTail, 1, size + 1)
        }
    }

    operator fun plus(element: E): SnapshotChunkedList<E> = append(element)

    operator fun plus(elements: Iterable<E>): SnapshotChunkedList<E> {
        var current = this
        for (item in elements) {
            current = current.append(item)
        }
        return current
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is List<*>) return false
        if (this.size != other.size) return false
        return super.equals(other)
    }

    companion object {
        private const val SHIFT = 8
        private const val CHUNK_SIZE = 1 shl SHIFT // 256
        private const val MASK = CHUNK_SIZE - 1

        private val EMPTY = SnapshotChunkedList<Any?>(emptyArray(), emptyArray(), 0, 0)

        @Suppress("UNCHECKED_CAST")
        fun <E> empty(): SnapshotChunkedList<E> = EMPTY as SnapshotChunkedList<E>

        fun <E> from(collection: Collection<E>): SnapshotChunkedList<E> {
            if (collection.isEmpty()) return empty()
            if (collection is SnapshotChunkedList<E>) return collection

            val totalSize = collection.size
            val numFullChunks = totalSize / CHUNK_SIZE
            val tailCount = totalSize % CHUNK_SIZE

            val chunks = Array(numFullChunks) { arrayOfNulls<Any?>(CHUNK_SIZE) }
            val tail = if (tailCount > 0) arrayOfNulls<Any?>(CHUNK_SIZE) else emptyArray()

            var i = 0
            for (item in collection) {
                val chunkIdx = i shr SHIFT
                val offset = i and MASK
                if (chunkIdx < numFullChunks) {
                    chunks[chunkIdx][offset] = item
                } else {
                    tail[offset] = item
                }
                i++
            }
            return SnapshotChunkedList(chunks, tail, tailCount, totalSize)
        }
    }
}
