package com.thep.android

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.thep.core.data.Preferences
import com.thep.core.data.WorkoutRepository
import com.thep.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp class ThePApplication : Application()

private val Context.settings by preferencesDataStore("the_p_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "the_p_v5.db").build()

    @Provides @Singleton fun repository(database: AppDatabase) = WorkoutRepository(database)

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context) = Preferences(context.settings)
}
