package com.tonic.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.DiagnosticResult
import com.tonic.core.model.state.EntryPoint
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class DiagnosticRepositoryTest {
    private lateinit var db: TonicDatabase
    private lateinit var repository: DiagnosticRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = DiagnosticRepositoryImpl(db.diagnosticResultDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun result(
        completedAt: Instant,
        flag: Boolean = false,
    ) = DiagnosticResult(
        pitchDirectionThresholdCents = 25,
        discriminationDPrime = 1.8,
        tonalMemorySpan = 5,
        amusiaIndicatorFlag = flag,
        recommendedEntry = EntryPoint.M2_STAGE_1,
        initialAxisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 2),
        completedAt = completedAt,
        seed = 99L,
    )

    @Test
    fun `latest is null before any diagnostic has ever completed`() =
        runBlocking {
            assertNull(repository.latest())
        }

    @Test
    fun `history is kept - saving a new result never overwrites the prior one, latest returns the newest`() =
        runBlocking {
            repository.save(result(Instant.EPOCH))
            repository.save(result(Instant.EPOCH.plusSeconds(86400), flag = true))

            val latest = repository.latest()
            assertEquals(Instant.EPOCH.plusSeconds(86400), latest?.completedAt)
            assertEquals(true, latest?.amusiaIndicatorFlag)
        }

    @Test
    fun `every field round-trips, including the internal amusia flag and axis levels`() =
        runBlocking {
            val original = result(Instant.EPOCH, flag = true)
            repository.save(original)
            assertEquals(original, repository.latest())
        }
}
