package com.tonic.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tonic.core.model.rhythm.AudioOutputRoute
import com.tonic.core.model.rhythm.CalibrationSlot
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.rhythm.RhythmCalibrations
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * docs/05-DATA-MODEL.md §3: every key's default, verified explicitly -
 * including `daily_reminder_enabled = false`. Plain JUnit5 (not
 * Robolectric): `PreferenceDataStoreFactory.create` over a real
 * [java.io.File] needs no Android context, unlike Room.
 */
class SettingsRepositoryTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @BeforeTest
    fun setUp() {
        val file = File.createTempFile("tonic_settings_test", ".preferences_pb")
        file.deleteOnExit()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        repository = SettingsRepositoryImpl(dataStore)
    }

    @Test
    fun `every default matches docs 05-DATA-MODEL 3 exactly, before any write`() =
        runTest {
            val settings = repository.settings.first()
            assertEquals(AppSettings(), settings)
            assertEquals(LabelStyle.NUMBERS, settings.labelStyle)
            assertEquals(440.0f, settings.referenceA4Hz)
            assertEquals(5, settings.sessionLengthMinutes)
            assertEquals(true, settings.hapticsEnabled)
            assertEquals(true, settings.soundEffectsEnabled)
            assertEquals(ThemeMode.SYSTEM, settings.themeMode)
            assertEquals(false, settings.reduceMotion)
            assertEquals(false, settings.onboardingCompleted)
            assertEquals(false, settings.diagnosticCompleted)
            assertEquals(false, settings.dailyReminderEnabled, "opt-in only - docs/05-DATA-MODEL.md §3")
            assertNull(settings.dailyReminderTime)
            assertEquals(
                RhythmCalibrations(),
                settings.rhythmCalibrations,
                "no route is calibrated until one is measured - docs/40-PHASE-4-SPEC.md §4.3",
            )
        }

    @Test
    fun `a calibration survives a round trip through storage`() =
        runTest {
            val constant = RhythmCalibration(offsetMs = 47.5, spreadMs = 12.25, tapsUsed = 14)
            repository.setRhythmCalibration(CalibrationSlot.SPEAKER, constant)

            val stored =
                repository.settings
                    .first()
                    .rhythmCalibrations
                    .forSlot(CalibrationSlot.SPEAKER)
            assertEquals(constant, stored)
        }

    @Test
    fun `the two routes are stored separately, not as one constant`() =
        runTest {
            // docs/40-PHASE-4-SPEC.md §4.3: "per output route, not global." A single stored value would
            // pass every other test here and quietly apply the speaker's latency to a headset.
            val speaker = RhythmCalibration(offsetMs = 62.0, spreadMs = 11.0, tapsUsed = 14)
            val wired = RhythmCalibration(offsetMs = 18.5, spreadMs = 9.0, tapsUsed = 16)
            repository.setRhythmCalibration(CalibrationSlot.SPEAKER, speaker)
            repository.setRhythmCalibration(CalibrationSlot.WIRED, wired)

            val book = repository.settings.first().rhythmCalibrations
            assertEquals(speaker, book.forRoute(AudioOutputRoute.SPEAKER))
            assertEquals(wired, book.forRoute(AudioOutputRoute.WIRED))
        }

    @Test
    fun `clearing one route leaves the other in force`() =
        runTest {
            // §4.3's "invalidate the stored constant" half. Invalidating the route you just unplugged
            // must not cost you the one you are about to plug back in.
            val speaker = RhythmCalibration(offsetMs = 62.0, spreadMs = 11.0, tapsUsed = 14)
            val wired = RhythmCalibration(offsetMs = 18.5, spreadMs = 9.0, tapsUsed = 16)
            repository.setRhythmCalibration(CalibrationSlot.SPEAKER, speaker)
            repository.setRhythmCalibration(CalibrationSlot.WIRED, wired)

            repository.clearRhythmCalibration(CalibrationSlot.WIRED)

            val book = repository.settings.first().rhythmCalibrations
            assertEquals(speaker, book.forSlot(CalibrationSlot.SPEAKER))
            assertNull(book.forSlot(CalibrationSlot.WIRED))
            assertEquals(setOf(CalibrationSlot.SPEAKER), book.calibratedSlots)
        }

    @Test
    fun `a cleared route is uncalibrated, not zero`() =
        runTest {
            // The distinction the whole design rests on. Zero is a claim that this device has no output
            // latency; absent is a claim that nobody has looked, which is what blocks and explains.
            repository.setRhythmCalibration(
                CalibrationSlot.SPEAKER,
                RhythmCalibration(offsetMs = 62.0, spreadMs = 11.0, tapsUsed = 14),
            )
            repository.clearRhythmCalibration(CalibrationSlot.SPEAKER)
            assertNull(
                repository.settings
                    .first()
                    .rhythmCalibrations
                    .forSlot(CalibrationSlot.SPEAKER),
            )
        }

    @Test
    fun `each setter changes exactly its own field`() =
        runTest {
            repository.setLabelStyle(LabelStyle.SOLFEGE)
            repository.setThemeMode(ThemeMode.DARK)
            repository.setSessionLengthMinutes(10)
            repository.setReferenceA4Hz(432.0f)
            repository.setHapticsEnabled(false)
            repository.setSoundEffectsEnabled(false)
            repository.setReduceMotion(true)
            repository.setDiagnosticCompleted(true)

            val settings = repository.settings.first()
            assertEquals(LabelStyle.SOLFEGE, settings.labelStyle)
            assertEquals(ThemeMode.DARK, settings.themeMode)
            assertEquals(10, settings.sessionLengthMinutes)
            assertEquals(432.0f, settings.referenceA4Hz)
            assertEquals(false, settings.hapticsEnabled)
            assertEquals(false, settings.soundEffectsEnabled)
            assertEquals(true, settings.reduceMotion)
            assertEquals(true, settings.diagnosticCompleted)
            // Untouched fields stay at their defaults.
            assertEquals(false, settings.dailyReminderEnabled)
        }

    @Test
    fun `setDailyReminder keeps enabled and time consistent`() =
        runTest {
            repository.setDailyReminder(enabled = true, time = "08:30")
            var settings = repository.settings.first()
            assertEquals(true, settings.dailyReminderEnabled)
            assertEquals("08:30", settings.dailyReminderTime)

            repository.setDailyReminder(enabled = false, time = null)
            settings = repository.settings.first()
            assertEquals(false, settings.dailyReminderEnabled)
            assertNull(settings.dailyReminderTime)
        }

    @Test
    fun `settings is a live Flow that reflects new writes`() =
        runTest {
            assertEquals(false, repository.settings.first().onboardingCompleted)
            repository.setOnboardingCompleted(true)
            assertEquals(true, repository.settings.first().onboardingCompleted)
        }
}
