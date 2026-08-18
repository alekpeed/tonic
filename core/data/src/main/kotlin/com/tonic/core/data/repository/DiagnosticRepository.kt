package com.tonic.core.data.repository

import com.tonic.core.model.state.DiagnosticResult

/** M0 diagnostic runs — docs/05-DATA-MODEL.md §5. History is kept; [save] never overwrites a prior run. */
interface DiagnosticRepository {
    suspend fun save(result: DiagnosticResult)

    suspend fun latest(): DiagnosticResult?
}
