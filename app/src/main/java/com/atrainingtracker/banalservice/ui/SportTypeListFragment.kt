/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.ListFragment
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SportTypeListFragment : ListFragment() {

    // Using 'lateinit' as these are initialized in onCreate and non-null afterward.
    private lateinit var cursorAdapter: SimpleCursorAdapter
    private lateinit var speedUnit: String

    // The cursor is nullable because it's closed in onDestroy.
    private var cursor: Cursor? = null

    private val sportTypeChangedFilter = IntentFilter(EditSportTypeDialog.SPORT_TYPE_CHANGED_INTENT)
    private val sportTypeChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        speedUnit = getString(MyHelper.getSpeedUnitNameId())

        val db = SportTypeDatabaseManager.getInstance(requireContext()).database
        cursor = db.query(
            SportType.TABLE,
            FROM_WITH_C_ID,
            null, null, null, null, null
        )

        val fromColumns = arrayOf(SportType.UI_NAME, SportType.MIN_AVG_SPEED, SportType.MAX_AVG_SPEED)
        val toViews = intArrayOf(R.id.st_tvName, R.id.st_tvSpeed, R.id.st_tvSpeed)

        cursorAdapter = SimpleCursorAdapter(context, R.layout.sport_type_row, cursor, fromColumns, toViews, 0)
        cursorAdapter.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, columnIndex ->
            val tv = view as TextView
            when (view.id) {
                R.id.st_tvName -> {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(SportType.UI_NAME))
                    val sportTypeId = cursor.getLong(cursor.getColumnIndexOrThrow(SportType.C_ID))

                    // Use a safe-call for context, which is good practice in fragments.
                    context?.let { ctx ->
                        val icon = SportTypeDatabaseManager.getInstance(ctx).getBSportTypeIcon(ctx, sportTypeId, 0.75)
                        icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                        tv.setCompoundDrawables(icon, null, null, null)
                    }
                    tv.text = name
                    true // Signal that the view was handled
                }
                R.id.st_tvSpeed -> {
                    val minSpeed = MyHelper.mps2userUnit(cursor.getDouble(cursor.getColumnIndexOrThrow(SportType.MIN_AVG_SPEED)))
                    val maxSpeed = MyHelper.mps2userUnit(cursor.getDouble(cursor.getColumnIndexOrThrow(SportType.MAX_AVG_SPEED)))
                    tv.text = getString(R.string.average_speed_range_format, minSpeed, maxSpeed, speedUnit)
                    true // Signal that the view was handled
                }
                else -> false // Let the adapter handle other views
            }
        }
        listAdapter = cursorAdapter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sport_type_list_layout, container, false)
        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            showEditSportTypeDialog(-1)
        }
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, id ->
            showEditSportTypeDialog(id)
        }
        registerForContextMenu(listView)
    }

    override fun onResume() {
        super.onResume()
        // Use the modern, lifecycle-aware way to register a receiver.
        context?.let {
            ContextCompat.registerReceiver(it, sportTypeChangedReceiver, sportTypeChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(sportTypeChangedReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safely close the cursor using the ?.let scope function.
        cursor?.close()
        cursor = null
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (DEBUG) Log.i(TAG, "onCreateContextMenu")
        activity?.menuInflater?.inflate(R.menu.delete, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        // Use 'as?' for safe casting which returns null on failure.
        val info = item.menuInfo as? AdapterView.AdapterContextMenuInfo ?: return super.onContextItemSelected(item)
        val id = info.id
        if (DEBUG) Log.i(TAG, "onContextItemSelected: id=$id")

        return when (item.itemId) {
            R.id.itemDelete -> {
                if (!SportTypeDatabaseManager.canDelete(id)) {
                    Toast.makeText(context, R.string.you_can_not_delete_this_sport_type, Toast.LENGTH_LONG).show()
                } else {
                    showReallyDeleteDialog(id)
                }
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun updateView() {
        // The 'requery()' method is deprecated. The modern way is to get a new cursor.
        val db = SportTypeDatabaseManager.getInstance(requireContext()).database
        val newCursor = db.query(SportType.TABLE, FROM_WITH_C_ID, null, null, null, null, null)
        cursorAdapter.changeCursor(newCursor)

        // Close the old cursor that was swapped out.
        cursor?.close()
        cursor = newCursor
    }

    private fun showReallyDeleteDialog(id: Long) {
        if (DEBUG) Log.i(TAG, "showReallyDeleteDialog, id=$id")
        val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(requireContext())
        val sportTypeUiName = sportTypeDatabaseManager.getUIName(id)

        // Using the Kotlin-friendly AlertDialog builder from Material Components.
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.really_delete_workout_name_scheme, sportTypeUiName))
            .setIcon(android.R.drawable.ic_menu_delete)
            .setPositiveButton(R.string.delete) { dialog, _ ->
                sportTypeDatabaseManager.delete(id)
                updateView()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.Cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditSportTypeDialog(id: Long) {
        // Ensure fragmentManager is not null.
        fragmentManager?.let {
            val editSportTypeDialog = EditSportTypeDialog.newInstance(id)
            editSportTypeDialog.show(it, EditSportTypeDialog.TAG)
        }
    }

    companion object {
        @JvmField
        val TAG: String = SportTypeListFragment::class.java.name

        private val DEBUG = TrainingApplication.getDebug(false)

        // Encapsulating the column arrays within the companion object.
        private val FROM_WITH_C_ID = arrayOf(SportType.C_ID, SportType.MIN_AVG_SPEED, SportType.MAX_AVG_SPEED, SportType.UI_NAME)
    }
}