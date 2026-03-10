package com.atrainingtracker.banalservice.ui.sporttype

import android.app.Application
import android.content.ContentValues
import androidx.activity.result.launch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SportTypeViewModel(application: Application) : AndroidViewModel(application) {
    private val dbManager = SportTypeDatabaseManager.getInstance(application)

    private val _sportTypes = MutableStateFlow<List<SportTypeItem>>(emptyList())
    val sportTypes: StateFlow<List<SportTypeItem>> = _sportTypes.asStateFlow()

    init {
        loadSportTypes()
    }

    fun loadSportTypes() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<SportTypeItem>()
            val db = dbManager.database
            val cursor = db.query(
                SportTypeDatabaseManager.SportType.TABLE,
                null, null, null, null, null,
                "${SportTypeDatabaseManager.SportType.MIN_AVG_SPEED} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.C_ID))
                    list.add(SportTypeItem(
                        id = id,
                        name = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.UI_NAME)),
                        minSpeed = it.getDouble(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.MIN_AVG_SPEED)),
                        maxSpeed = it.getDouble(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.MAX_AVG_SPEED)),
                        stravaName = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.STRAVA_NAME)),
                        tcxName = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.TCX_NAME)),
                        gcName = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.GOLDEN_CHEETAH_NAME)),
                        isEditable = SportTypeDatabaseManager.canDelete(id)
                    ))
                }
            }
            _sportTypes.value = list
        }
    }

    fun saveSportType(item: SportTypeItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = dbManager.database

            val values = ContentValues().apply {
                put(SportTypeDatabaseManager.SportType.UI_NAME, item.name)
                put(SportTypeDatabaseManager.SportType.MIN_AVG_SPEED, item.minSpeed)
                put(SportTypeDatabaseManager.SportType.MAX_AVG_SPEED, item.maxSpeed)
                put(SportTypeDatabaseManager.SportType.STRAVA_NAME, item.stravaName)
                put(SportTypeDatabaseManager.SportType.TCX_NAME, item.tcxName)
                put(SportTypeDatabaseManager.SportType.GOLDEN_CHEETAH_NAME, item.gcName)
            }

            if (item.id == -1L) {
                db.insert(SportTypeDatabaseManager.SportType.TABLE, null, values)
            } else {
                db.update(
                    SportTypeDatabaseManager.SportType.TABLE,
                    values,
                    "${SportTypeDatabaseManager.SportType.C_ID}=?",
                    arrayOf(item.id.toString())
                )
            }
            loadSportTypes() // Refresh the list
        }
    }

    fun deleteSportType(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbManager.delete(id)
            loadSportTypes()
        }
    }
}