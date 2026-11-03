package app.frame.studio.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProjectJson {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(project: Project): String = json.encodeToString(project)

    fun decode(raw: String): Project = json.decodeFromString(raw)

    fun encodeHistory(records: List<ExportRecord>): String = json.encodeToString(records)

    fun decodeHistory(raw: String): List<ExportRecord> = json.decodeFromString(raw)
}
