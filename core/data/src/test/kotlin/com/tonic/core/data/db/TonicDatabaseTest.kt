package com.tonic.core.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.entity.SkillStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

/**
 * docs/05-DATA-MODEL.md §4: `exportSchema = true`, schema JSON committed
 * (`core/data/schemas/`), every real schema change ships a `Migration`
 * with a `MigrationTestHelper` test. There is nothing to migrate *from*
 * yet - this is schema version 1, the first one - so there's no prior
 * version to load. What this test verifies instead is the part that IS
 * already meaningful: a real, file-backed database (not the in-memory one
 * every other test in this module uses) opens cleanly against the
 * committed version-1 schema with no migration, proving the exported
 * schema and the live entity definitions haven't drifted apart.
 */
@RunWith(AndroidJUnit4::class)
class TonicDatabaseTest {
    @Test
    fun `a real file-backed database opens against the committed version-1 schema with no migration needed`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            context.deleteDatabase(TonicDatabase.DATABASE_NAME)
            val db = Room.databaseBuilder(context, TonicDatabase::class.java, TonicDatabase.DATABASE_NAME).build()
            try {
                db.skillStateDao().upsert(
                    SkillStateEntity(
                        skillId = "M2.DEG_SET_1",
                        axisLevelsJson = "{}",
                        staircaseStateJson = "{}",
                        activeAxis = null,
                        masteryStatus = "IN_PROGRESS",
                        masteredAt = null,
                        fsrsStability = 0.0,
                        fsrsDifficulty = 0.0,
                        fsrsLastReview = null,
                        fsrsDue = null,
                        fsrsReps = 0,
                        fsrsLapses = 0,
                        totalAttempts = 1,
                        updatedAt = 0L,
                    ),
                )
                val reloaded = db.skillStateDao().observeAll().first()
                assertEquals(1, reloaded.size)
            } finally {
                db.close()
                context.deleteDatabase(TonicDatabase.DATABASE_NAME)
            }
        }
}
