package com.atrainingtracker.trainingtracker.helpers

import android.content.Context
import com.atrainingtracker.R

/**
 * Format a list of strings grammatically correct:
 * [] -> ""
 * [a] -> "a"
 * [a, b] -> "a and b"         using R.string.list_connector__and
 * [a, b, c] -> "a, b, and c"  using R.string.list_connector__comma, and R.string.list_connector__and_with_comma
 *
 * @param context  the context
 * @param items    the list of strings that shall be merged
 * @return a String that represents the list.
 */
fun formatListAsString(context: Context, items: List<String>): String {
    return when (items.size) {
        0 -> "" // empty list, return ""
        1 -> items.first() // just one element, simply return this
        2 -> {
            // case [a, b] -> "a and b"
            val connectorAnd = context.getString(R.string.list_connector__and)
            "${items[0]}${connectorAnd}${items[1]}"
        }
        else -> {
            // three or more elements: [a, b, c] -> "a, b, und c"
            val connectorFinalAnd = context.getString(R.string.list_connector__and_with_comma)
            val connectorComma = context.getString(R.string.list_connector__comma)

            val leadingElements = items.dropLast(1).joinToString(separator = connectorComma)
            val lastElement = items.last()

            "$leadingElements${connectorFinalAnd}$lastElement"
        }
    }
}