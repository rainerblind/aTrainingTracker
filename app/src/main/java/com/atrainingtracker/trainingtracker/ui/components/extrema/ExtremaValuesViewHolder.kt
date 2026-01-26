package com.atrainingtracker.trainingtracker.ui.components.extrema

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.atrainingtracker.R

/**
 * A "dumb" ViewHolder responsible *only* for displaying a pre-processed list of ExtremaData objects.
 * It has no knowledge of the database, business rules, or data sources.
 */
class ExtremaValuesViewHolder(val view: View) {
    val tableLayout: TableLayout = view.findViewById(R.id.extrema_values_container)
    private val context: Context = view.context

    /**
     * Binds a list of ExtremaData to the UI by populating the table.
     * If the list is empty, the container's visibility is set to GONE.
     */
    fun bind(extremaList: List<ExtremaData>) {
        // Set visibility of the whole component
        view.visibility = if (extremaList.isEmpty()) View.GONE else View.VISIBLE
        if (extremaList.isEmpty()) {
            return
        }

        // Clear all views EXCEPT the first one (the header)
        val childCount = tableLayout.childCount
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1)
        }

        // Loop through the clean data and add a new row for each item
        extremaList.forEach { data -> addExtremaRow(data) }
    }

    /**
     * Creates and adds a single TableRow for a given ExtremaData object.
     */
    private fun addExtremaRow(data: ExtremaData) {
        val row = TableRow(context)

        // Use Kotlin's 'apply' scope function for cleaner object configuration
        row.apply {
            addView(createTextView(data.sensorLabel, Gravity.START, 2.5f))
            // Use the Elvis operator (?:) for a concise default value
            addView(createTextView(data.minValue ?: "", Gravity.END, 2f))
            addView(createTextView(data.avgValue ?: "", Gravity.END, 2f))
            addView(createTextView(data.maxValue ?: "", Gravity.END, 2f))
            addView(createTextView(data.unitLabel, Gravity.END, 1.5f))
        }

        tableLayout.addView(row)
    }

    /**
     * General purpose helper to create a TextView with standard properties.
     */
    private fun createTextView(text: String, gravity: Int, weight: Float) = TextView(context).apply {
        this.text = text
        this.gravity = gravity
        this.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weight)
    }
}