package com.example.riderapp.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.example.riderapp.data.sync.SyncConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("rider_app_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Provides the sync configuration.
     * Override defaults here, or swap this with values from a remote config service.
     */
    @Provides
    @Singleton
    fun provideSyncConfig(): SyncConfig {
        return SyncConfig(
            batchSize              = 50,
            maxRetriesPerBatch     = 3,
            initialBackoffMs       = 1_000,    // 1 s
            backoffMultiplier      = 2.0,
            maxBackoffMs           = 60_000,   // 60 s cap
            maxRetriesPerAction    = 5,
            periodicSyncIntervalMinutes = 15,
            workerInitialBackoffMs = 30_000,   // 30 s
            maxWorkerRetries       = 5
        )
    }
}
