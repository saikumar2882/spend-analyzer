package com.alpha.spendtracker.di

import android.content.Context
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AppDatabase
import com.alpha.spendtracker.data.ChatDao
import com.alpha.spendtracker.data.RecurringBillDao
import com.alpha.spendtracker.data.SpendDao
import com.alpha.spendtracker.data.SpendRepository
import com.alpha.spendtracker.data.GroqApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideGroqApiService(okHttpClient: OkHttpClient, moshi: Moshi): GroqApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GroqApiService::class.java)
    }

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
    fun provideRecurringBillDao(database: AppDatabase): RecurringBillDao {
        return database.recurringBillDao()
    }

    @Provides
    @Singleton
    fun provideSpendRepository(
        spendDao: SpendDao,
        recurringBillDao: RecurringBillDao,
        chatDao: ChatDao
    ): SpendRepository {
        return SpendRepository(spendDao, recurringBillDao, chatDao)
    }

    @Provides
    @Singleton
    fun provideAiPreferencesRepository(@ApplicationContext context: Context): AiPreferencesRepository {
        return AiPreferencesRepository(context)
    }
}
