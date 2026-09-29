package com.yuvraj.resumescreener.di

import android.content.Context
import androidx.room.Room
import com.yuvraj.resumescreener.data.local.AppDatabase
import com.yuvraj.resumescreener.data.local.ScreeningDao
import com.yuvraj.resumescreener.data.remote.GeminiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // The raw JSON columns mean schema growth is additive, so destructive
            // migration is acceptable until this app has shipped with real data.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideScreeningDao(db: AppDatabase): ScreeningDao = db.screeningDao()

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = GeminiClient.defaultHttpClient()
}
