package com.atrainingtracker.trainingtracker.fragments

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.ui.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.exporter.ui.ExportStatusUIDataProvider

/**
 * A ViewHolder responsible for binding the export status to the UI
 * in the workout list. It uses ExportStatusUIDataProvider to get the data.
 */
class ExportStatusViewHolder(
    private val context: Context,
    private val separatorView: View,
    private val headerView: TextView,
    private val container: LinearLayout,
    private val fileBaseName: String
) {
    private val uiDataProvider = ExportStatusUIDataProvider(context)

    /**
     * Binds the fetched export status data to the views.
     * It creates a view for each ExportType and adds it to the container.
     */
    fun bind() {
        // Clear previous views and hide the container initially
        container.removeAllViews()
        container.visibility = View.GONE

        // Define the order of export types to display
        val orderedTypes = listOf(ExportType.FILE, ExportType.DROPBOX, ExportType.COMMUNITY)
        var hasAnyContent = false

        for (type in orderedTypes) {
            // Get the structured data from our central provider
            val groupData = uiDataProvider.createGroupData(fileBaseName, type)

            if (groupData.hasContent) {
                // Create a view for this group and add it to the container
                val groupView = createGroupView(groupData)
                container.addView(groupView)
                hasAnyContent = true
            }
        }

        // Only show the separator, header and container if at least one group has content
        separatorView.visibility = if (hasAnyContent) View.VISIBLE else View.GONE
        headerView.visibility = if (hasAnyContent) View.VISIBLE else View.GONE
        container.visibility = if (hasAnyContent) View.VISIBLE else View.GONE
    }

    /**
     * Creates and populates a single group view (e.g., for Dropbox)
     * using the provided data.
     */
    private fun createGroupView(data: ExportStatusGroupData): View {
        // Inflate the same layout we use for notifications
        val view = View.inflate(context, R.layout.export_status__group, null)

        val title: TextView = view.findViewById(R.id.group_title)
        val lineWaiting: TextView = view.findViewById(R.id.line_waiting)
        val lineRunning: TextView = view.findViewById(R.id.line_running)
        val lineSucceeded: TextView = view.findViewById(R.id.line_succeeded)
        val lineFailed: TextView = view.findViewById(R.id.line_failed)

        title.text = data.groupTitle

        // Use a helper to set text and visibility for each line
        updateLine(lineWaiting, data.waitingLine)
        updateLine(lineRunning, data.runningLine)
        updateLine(lineSucceeded, data.succeededLine)
        updateLine(lineFailed, data.failedLine)

        return view
    }

    /**
     * Helper function to set the text and visibility of a TextView.
     */
    private fun updateLine(textView: TextView, text: String?) {
        if (text != null) {
            textView.text = text
            textView.visibility = View.VISIBLE
        } else {
            textView.visibility = View.GONE
        }
    }
}