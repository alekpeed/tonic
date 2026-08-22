package com.tonic.core.data.export

/**
 * Reads the whole local database out as one JSON document — docs/20-PHASE-2-SPEC.md §6.
 *
 * Export only. There is no import, deliberately and not as an oversight: importing means merge
 * conflicts, schema-version mismatches, and a route to corrupting a working profile, and the spec
 * defers it until there is a concrete need. Reading data out is safe; writing it back is a design
 * problem in its own right.
 *
 * Nothing here touches the network, and there is nothing to configure — the app has no network
 * permission at all (docs/01-PRODUCT-SPEC.md §4), so an export is a local file and can only ever be
 * one. Where it goes afterwards is the user's decision, made through the system share sheet.
 */
interface DataExportRepository {
    /** Assembles the current database into an export document. */
    suspend fun buildExport(): TonicExport

    /** [buildExport], serialized. Pretty-printed: a person opening the file is a supported use. */
    suspend fun exportToJson(): String

    /** `tonic-export-<epochMs>.json` — stable, sortable, and free of characters any filesystem dislikes. */
    suspend fun suggestedFileName(): String
}
