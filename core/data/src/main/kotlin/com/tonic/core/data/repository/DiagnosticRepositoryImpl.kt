package com.tonic.core.data.repository

import com.tonic.core.data.dao.DiagnosticResultDao
import com.tonic.core.model.state.DiagnosticResult
import javax.inject.Inject

internal class DiagnosticRepositoryImpl
    @Inject
    constructor(
        private val dao: DiagnosticResultDao,
    ) : DiagnosticRepository {
        override suspend fun save(result: DiagnosticResult) {
            dao.insert(result.toEntity())
        }

        override suspend fun latest(): DiagnosticResult? = dao.latest()?.toDomain()
    }
