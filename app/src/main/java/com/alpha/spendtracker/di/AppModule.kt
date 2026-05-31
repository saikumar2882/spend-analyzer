package com.alpha.spendtracker.di

import android.content.Context
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AppDatabase
import com.alpha.spendtracker.data.ChatDao
import com.alpha.spendtracker.data.SpendDao
import com.alpha.spendtracker.data.SpendRepository
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideSpendDao(database: AppDatabase): SpendDao {
        return database.spendDao()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideSpendRepository(spendDao: SpendDao): SpendRepository {
        return SpendRepository(spendDao)
    }

    @Provides
    @Singleton
    fun provideAiPreferencesRepository(@ApplicationContext context: Context): AiPreferencesRepository {
        return AiPreferencesRepository(context)
    }
}
