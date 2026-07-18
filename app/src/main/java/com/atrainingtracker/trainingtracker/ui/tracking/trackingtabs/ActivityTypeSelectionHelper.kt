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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType

object ActivityTypeSelectionHelper {

    /**
     * Shows a professional AlertDialog for selecting an Activity Type.
     * Extracts the logic previously duplicated in ConfigTrackingTabsActivity and Fragment.
     */
    @JvmStatic
    fun showSelectionDialog(
        context: Context,
        onTypeSelected: (ActivityType) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val types = ActivityType.values()

        val adapter = object : ArrayAdapter<ActivityType>(
            context,
            android.R.layout.select_dialog_item,
            android.R.id.text1,
            types
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val tv = v.findViewById<TextView>(android.R.id.text1)

                val type = getItem(position)
                if (type != null) {
                    tv.text = context.getString(type.titleId)
                    tv.setCompoundDrawablesWithIntrinsicBounds(type.logoId, 0, 0, 0)
                    tv.compoundDrawablePadding = 32
                }
                return v
            }
        }

        val titleView = TextView(context).apply {
            setText(R.string.choose_activity_type)
            setPadding(40, 40, 40, 40)
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(context, R.color.color_primary))
            gravity = Gravity.CENTER
        }

        AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setAdapter(adapter) { _, which ->
                onTypeSelected(types[which])
            }
            .setOnCancelListener { onCancel() }
            .show()
    }
}
