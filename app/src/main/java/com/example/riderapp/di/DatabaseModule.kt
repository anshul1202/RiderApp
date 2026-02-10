package com.example.riderapp.di

import android.content.Context
import androidx.room.Room
import com.example.riderapp.data.local.RiderDatabase
import com.example.riderapp.data.local.dao.TaskActionDao
import com.example.riderapp.data.local.dao.TaskDao
import com.example.riderapp.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RiderDatabase {
        return Room.databaseBuilder(
            context,
            RiderDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTaskDao(database: RiderDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideTaskActionDao(database: RiderDatabase): TaskActionDao {
        return database.taskActionDao()
    }
}
