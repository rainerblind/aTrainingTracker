package com.atrainingtracker.trainingtracker.ui.components.export

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.exporter.ExportType

/**
 * A ViewHolder responsible for binding the export status to the UI
 * in the workout list. It uses ExportStatusUIDataProvider to get the data.
 */
class ExportStatusViewHolder(val view: View) {

    private val separator: View = view.findViewById(R.id.separator_export_status)
    private val header: TextView = view.findViewById(R.id.export_status_header)
    private val container: LinearLayout = view.findViewById(R.id.export_status_container)
    private val context: Context = view.context

    private val dataProvider = ExportStatusDataProvider(context)

    /**
     * Binds the fetched export status data to the views.
     * It creates a view for each ExportType and adds it to the container.
     */
    fun bind(fileBaseName: String) {
        // If fileBaseName is null or empty, hide the entire component
        if (fileBaseName.isNullOrEmpty()) {
            view.visibility = View.GONE
            return
        }

        // Show the component and proceed with the logic
        view.visibility = View.VISIBLE
        container.removeAllViews()

        // Define the order of export types to display
        val orderedTypes = listOf(ExportType.FILE, ExportType.DROPBOX, ExportType.COMMUNITY)
        var hasAnyContent = false

        for (type in orderedTypes) {
            // Get the structured data from our central provider
            val groupData = dataProvider.createGroupData(fileBaseName, type)

            if (groupData.hasContent) {
                // Create a view for this group and add it to the container
                val groupView = createGroupView(groupData)
                container.addView(groupView)
                hasAnyContent = true
            }
        }

        // Only show the separator, header and container if at least one group has content
        separator.visibility = if (hasAnyContent) View.VISIBLE else View.GONE
        header.visibility = if (hasAnyContent) View.VISIBLE else View.GONE
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