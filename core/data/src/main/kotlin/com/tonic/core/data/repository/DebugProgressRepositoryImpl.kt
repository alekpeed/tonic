package com.tonic.core.data.repository

import com.tonic.core.data.dao.AttemptDao
import com.tonic.core.data.dao.ConfusionStateDao
import com.tonic.core.data.dao.SkillStateDao
import com.tonic.core.model.attempts.Attempt
import javax.inject.Inject

internal class DebugProgressRepositoryImpl
    @Inject
    constructor(
        private val attemptDao: AttemptDao,
        private val skillStateDao: SkillStateDao,
        private val confusionStateDao: ConfusionStateDao,
    ) : DebugProgressRepository {
        override suspend fun resetProgress() {
            // Attempts last would leave a window where derived state disagrees with the log it is
            // derived from. Order here is: derived state first, then the source of truth.
            skillStateDao.deleteAll()
            confusionStateDao.deleteAll()
            attemptDao.deleteAll()
        }

        override suspend fun recordAttempts(attempts: List<Attempt>) {
            attemptDao.insertAll(attempts.map { it.toEntity() })
        }
    }
