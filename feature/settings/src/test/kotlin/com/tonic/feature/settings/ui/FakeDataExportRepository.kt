package com.tonic.feature.settings.ui

import com.tonic.core.data.export.DataExportRepository
import com.tonic.core.data.export.TonicExport

/**
 * The export itself is proven against a real Room database in `:core:data`'s
 * `DataExportRepositoryTest`. What the Settings ViewModel owns is the *handoff* — preparing a payload
 * and reporting an outcome — so this fake only has to return something recognizable and record that it
 * was asked.
 */
internal class FakeDataExportRepository(
    private val json: String = """{"schema_version":1,"attempts":[]}""",
    private val fileName: String = "tonic-export-1234.json",
) : DataExportRepository {
    var buildCount = 0
        private set

    override suspend fun buildExport(): TonicExport =
        TonicExport(
            exportedAtEpochMs = 0L,
            attempts = emptyList(),
            sessions = emptyList(),
            skillStates = emptyList(),
            confusionStates = emptyList(),
            diagnosticResults = emptyList(),
        )

    override suspend fun exportToJson(): String {
        buildCount++
        return json
    }

    override suspend fun suggestedFileName(): String = fileName
}
