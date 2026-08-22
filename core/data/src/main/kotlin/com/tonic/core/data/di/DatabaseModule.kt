package com.tonic.core.data.di

import android.content.Context
import androidx.room.Room
import com.tonic.core.data.dao.AttemptDao
import com.tonic.core.data.dao.ConfusionStateDao
import com.tonic.core.data.dao.DiagnosticResultDao
import com.tonic.core.data.dao.SessionDao
import com.tonic.core.data.dao.SkillStateDao
import com.tonic.core.data.db.Migrations
import com.tonic.core.data.db.TonicDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TonicDatabase =
        Room
            .databaseBuilder(context, TonicDatabase::class.java, TonicDatabase.DATABASE_NAME)
            // No fallbackToDestructiveMigration() - docs/05-DATA-MODEL.md §4. Every real schema change
            // ships its own androidx.room.migration.Migration, and they are all registered here: a
            // migration that exists but is never added is indistinguishable from no migration at all,
            // except that it looks safe in review.
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideAttemptDao(db: TonicDatabase): AttemptDao = db.attemptDao()

    @Provides
    fun provideSkillStateDao(db: TonicDatabase): SkillStateDao = db.skillStateDao()

    @Provides
    fun provideConfusionStateDao(db: TonicDatabase): ConfusionStateDao = db.confusionStateDao()

    @Provides
    fun provideSessionDao(db: TonicDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideDiagnosticResultDao(db: TonicDatabase): DiagnosticResultDao = db.diagnosticResultDao()
}
