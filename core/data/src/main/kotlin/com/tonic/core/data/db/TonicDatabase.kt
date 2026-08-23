package com.tonic.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tonic.core.data.dao.AttemptDao
import com.tonic.core.data.dao.ConfusionStateDao
import com.tonic.core.data.dao.DiagnosticResultDao
import com.tonic.core.data.dao.SessionDao
import com.tonic.core.data.dao.SkillStateDao
import com.tonic.core.data.entity.AttemptEntity
import com.tonic.core.data.entity.ConfusionStateEntity
import com.tonic.core.data.entity.DiagnosticResultEntity
import com.tonic.core.data.entity.SessionEntity
import com.tonic.core.data.entity.SkillStateEntity

/**
 * docs/05-DATA-MODEL.md. `exportSchema = true` so the schema JSON is
 * committed (see `room { schemaDirectory(...) }` in this module's
 * `build.gradle.kts`) — every future schema change ships an explicit
 * [androidx.room.migration.Migration] and a `MigrationTestHelper` test;
 * `fallbackToDestructiveMigration()` is never called anywhere in this
 * codebase (docs/05-DATA-MODEL.md §4: losing a user's ear-training
 * progress is unacceptable).
 */
@Database(
    entities = [
        AttemptEntity::class,
        SkillStateEntity::class,
        ConfusionStateEntity::class,
        SessionEntity::class,
        DiagnosticResultEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
internal abstract class TonicDatabase : RoomDatabase() {
    abstract fun attemptDao(): AttemptDao

    abstract fun skillStateDao(): SkillStateDao

    abstract fun confusionStateDao(): ConfusionStateDao

    abstract fun sessionDao(): SessionDao

    abstract fun diagnosticResultDao(): DiagnosticResultDao

    companion object {
        const val DATABASE_NAME = "tonic.db"
    }
}
