package com.example.riderapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.riderapp.data.local.dao.TaskActionDao
import com.example.riderapp.data.local.dao.TaskDao
import com.example.riderapp.data.local.entity.TaskActionEntity
import com.example.riderapp.data.local.entity.TaskEntity

@Database(
    entities = [TaskEntity::class, TaskActionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RiderDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskActionDao(): TaskActionDao
}
