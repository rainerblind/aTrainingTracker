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

package com.atrainingtracker.trainingtracker.exporter.writer

import java.io.Closeable
import java.io.Flushable
import java.io.IOException
import java.io.Writer

/**
 * Minimal RFC-4180 CSV writer replacing vendored 2005 `au.com.bytecode.opencsv.CSVWriter`
 * (`REQ-UI-2802` / `ATT-8130`).
 *
 * Preserves exact wire format parity with the default `CSVWriter(Writer)` configuration:
 * comma-separated fields, double-quoted non-null entries with `""` escaping for embedded quotes,
 * empty unquoted slots for `null` entries, and `\n` line terminators.
 */
class Rfc4180CsvWriter(
    private val writer: Writer
) : Closeable, Flushable {

    @Throws(IOException::class)
    fun writeNext(nextLine: Array<String?>?) {
        if (nextLine == null) return
        val sb = StringBuilder(nextLine.size * 16)
        for (i in nextLine.indices) {
            if (i != 0) {
                sb.append(',')
            }
            val element = nextLine[i] ?: continue
            sb.append('"')
            for (ch in element) {
                if (ch == '"') {
                    sb.append("\"\"")
                } else {
                    sb.append(ch)
                }
            }
            sb.append('"')
        }
        sb.append('\n')
        writer.write(sb.toString())
    }

    @Throws(IOException::class)
    override fun flush() {
        writer.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        writer.flush()
        writer.close()
    }
}
