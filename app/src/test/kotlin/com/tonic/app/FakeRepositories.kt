package com.tonic.app

import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.rhythm.CalibrationSlot
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.Session
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.state.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** In-memory stand-ins for `:app`'s own thin ViewModels (Home, Summary) - direct, settable fixtures, not simulations of adaptive-engine behavior. */
class FakeSessionRepository : SessionRepository {
    private val sessions = mutableMapOf<Long, Session>()
    private var nextId = 1L

    override suspend fun create(
        rootSeed: Long,
        plannedItemCount: Int,
        startedAt: Instant,
    ): Session {
        val session =
            Session(
                id = nextId++,
                startedAt = startedAt,
                endedAt = null,
                plannedItemCount = plannedItemCount,
                completedItemCount = 0,
                rootSeed = rootSeed,
                resumeState = null,
            )
        sessions[session.id!!] = session
        return session
    }

    override suspend fun updateResumeState(
        sessionId: Long,
        completedItemCount: Int,
        resumeState: ResumeState?,
    ) {
        val existing = requireNotNull(sessions[sessionId])
        sessions[sessionId] = existing.copy(completedItemCount = completedItemCount, resumeState = resumeState)
    }

    override suspend fun complete(
        sessionId: Long,
        completedItemCount: Int,
        endedAt: Instant,
    ) {
        val existing = requireNotNull(sessions[sessionId])
        sessions[sessionId] =
            existing.copy(completedItemCount = completedItemCount, endedAt = endedAt, resumeState = null)
    }

    override suspend fun discardResumable(now: java.time.Instant): Boolean {
        val resumable = findResumable() ?: return false
        val id = resumable.id ?: return false
        complete(id, resumable.completedItemCount, now)
        return true
    }

    override suspend fun findResumable(): Session? =
        sessions.values.firstOrNull { it.endedAt == null && it.resumeState != null }

    override suspend fun findById(sessionId: Long): Session? = sessions[sessionId]

    override suspend fun recentCompletedSessions(limit: Int): List<Session> =
        sessions.values
            .filter { it.endedAt != null }
            .sortedByDescending { it.startedAt }
            .take(limit)

    /** Directly seeds a completed session at [startedAt], bypassing [create]/[complete] - test convenience for streak fixtures. */
    fun seedCompleted(startedAt: Instant): Session {
        val session =
            Session(
                id = nextId++,
                startedAt = startedAt,
                endedAt = startedAt.plusSeconds(300),
                plannedItemCount = 10,
                completedItemCount = 10,
                rootSeed = 0L,
                resumeState = null,
            )
        sessions[session.id!!] = session
        return session
    }
}

class FakeSkillStateRepository : SkillStateRepository {
    private val states = mutableMapOf<SkillId, SkillState>()
    private val flow = MutableStateFlow<Map<SkillId, SkillState>>(emptyMap())

    override fun observe(skillId: SkillId): Flow<SkillState> =
        flow.asStateFlow().map { it[skillId] ?: SkillState.initial(skillId) }

    override fun observeAll(): Flow<Map<SkillId, SkillState>> = flow.asStateFlow()

    override suspend fun update(state: SkillState) {
        states[state.skillId] = state
        flow.value = states.toMap()
    }

    override suspend fun dueForReview(now: Instant): List<SkillId> = emptyList()

    override suspend fun rebuildFromAttempts() = Unit

    override suspend fun rebuildFromAttempts(skillId: SkillId) = Unit

    fun setState(state: SkillState) {
        states[state.skillId] = state
        flow.value = states.toMap()
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings = state

    override suspend fun setLabelStyle(style: LabelStyle) {
        state.value = state.value.copy(labelStyle = style)
    }

    override suspend fun setReferenceA4Hz(hz: Float) {
        state.value = state.value.copy(referenceA4Hz = hz)
    }

    override suspend fun setSessionLengthMinutes(minutes: Int) {
        state.value = state.value.copy(sessionLengthMinutes = minutes)
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        state.value = state.value.copy(hapticsEnabled = enabled)
    }

    override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        state.value = state.value.copy(soundEffectsEnabled = enabled)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        state.value = state.value.copy(reduceMotion = enabled)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        state.value = state.value.copy(onboardingCompleted = completed)
    }

    override suspend fun setModule10IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module10IntroSeen = seen)
    }

    override suspend fun setModule11IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module11IntroSeen = seen)
    }

    override suspend fun setModule12IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module12IntroSeen = seen)
    }

    override suspend fun setMixedModeIntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(mixedModeIntroSeen = seen)
    }

    override suspend fun setSungResponseEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(sungResponseEnabled = enabled)
    }

    override suspend fun setRhythmCalibration(
        slot: CalibrationSlot,
        calibration: RhythmCalibration,
    ) {
        settings.value =
            settings.value.copy(
                rhythmCalibrations = settings.value.rhythmCalibrations.with(slot, calibration),
            )
    }

    override suspend fun clearRhythmCalibration(slot: CalibrationSlot) {
        settings.value =
            settings.value.copy(
                rhythmCalibrations =
                    when (slot) {
                        CalibrationSlot.SPEAKER -> settings.value.rhythmCalibrations.copy(speaker = null)
                        CalibrationSlot.WIRED -> settings.value.rhythmCalibrations.copy(wired = null)
                    },
            )
    }

    override suspend fun setSungOctaveAgnostic(enabled: Boolean) {
        settings.value = settings.value.copy(sungOctaveAgnostic = enabled)
    }

    override suspend fun setSungResponseIntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(sungResponseIntroSeen = seen)
    }

    override suspend fun setModule9IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module9IntroSeen = seen)
    }

    override suspend fun setModule2IntroSeen(seen: Boolean) {
        state.value = state.value.copy(module2IntroSeen = seen)
    }

    override suspend fun setDiagnosticCompleted(completed: Boolean) {
        state.value = state.value.copy(diagnosticCompleted = completed)
    }

    override suspend fun setDailyReminder(
        enabled: Boolean,
        time: String?,
    ) {
        state.value = state.value.copy(dailyReminderEnabled = enabled, dailyReminderTime = time)
    }
}
