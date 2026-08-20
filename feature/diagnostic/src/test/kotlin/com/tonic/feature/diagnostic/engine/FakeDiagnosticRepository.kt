package com.tonic.feature.diagnostic.engine

import com.tonic.core.data.repository.DiagnosticRepository
import com.tonic.core.model.state.DiagnosticResult

class FakeDiagnosticRepository : DiagnosticRepository {
    val saved = mutableListOf<DiagnosticResult>()

    override suspend fun save(result: DiagnosticResult) {
        saved += result
    }

    override suspend fun latest(): DiagnosticResult? = saved.lastOrNull()
}
