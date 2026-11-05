package app.frame.studio.data

import android.content.Context
import android.util.Log
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.ExportRecord
import app.frame.studio.domain.Project
import app.frame.studio.domain.ProjectJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Stores projects as one JSON file each and the export history as a single JSON file.
 * Writes are atomic (temp file + rename) so a crash never leaves a half-written project.
 */
class ProjectRepository(
    context: Context,
    private val defaultExportHeight: ExportHeight = ExportHeight.HD_1080,
) {

    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val historyFile = File(context.filesDir, "export_history.json")
    val exportsDir: File = File(context.filesDir, "exports").apply { mkdirs() }

    private val mutex = Mutex()
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _history = MutableStateFlow<List<ExportRecord>>(emptyList())
    val history: StateFlow<List<ExportRecord>> = _history.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            val projects = root.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
                runCatching { ProjectJson.decode(file.readText()) }
                    .onFailure { Log.w(TAG, "Skipping unreadable project ${file.name}", it) }
                    .getOrNull()
            }
            _projects.value = projects.sortedByDescending { it.updatedAtEpochMs }
            _history.value = if (historyFile.exists()) {
                runCatching { ProjectJson.decodeHistory(historyFile.readText()) }
                    .getOrElse { emptyList() }
                    .filter { File(it.filePath).exists() || it.galleryUri != null }
                    .sortedByDescending { it.createdAtEpochMs }
            } else {
                emptyList()
            }
            loaded = true
        }
    }

    fun get(id: String): Project? = _projects.value.firstOrNull { it.id == id }

    suspend fun create(name: String): Project {
        val now = System.currentTimeMillis()
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Untitled" },
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            exportHeight = defaultExportHeight,
        )
        save(project)
        return project
    }

    /**
     * Saves [project]. Returns false when it could not be written (e.g. storage full); the in-memory
     * copy is still updated so the editor keeps working, and the next save retries the write.
     */
    suspend fun save(project: Project): Boolean = withContext(Dispatchers.IO) {
        val stamped = project.copy(updatedAtEpochMs = System.currentTimeMillis())
        mutex.withLock {
            val written = writeAtomically(File(root, "${stamped.id}.json"), ProjectJson.encode(stamped))
            _projects.update { list ->
                (listOf(stamped) + list.filter { it.id != stamped.id })
                    .sortedByDescending { it.updatedAtEpochMs }
            }
            written
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(root, "$id.json").delete()
            _projects.update { list -> list.filter { it.id != id } }
        }
    }

    suspend fun duplicate(id: String): Project? {
        val source = get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copy",
            createdAtEpochMs = now,
        )
        save(copy)
        return copy
    }

    /** Records a finished export. Returns false when the history file could not be written. */
    suspend fun addExport(record: ExportRecord): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = listOf(record) + _history.value.filter { it.id != record.id }
            val written = writeAtomically(historyFile, ProjectJson.encodeHistory(updated))
            _history.value = updated
            written
        }
    }

    suspend fun deleteExport(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = _history.value.firstOrNull { it.id == id } ?: return@withLock
            File(record.filePath).delete()
            val updated = _history.value.filter { it.id != id }
            writeAtomically(historyFile, ProjectJson.encodeHistory(updated))
            _history.value = updated
        }
    }

    /**
     * Writes via a temp file so a crash or full disk never leaves a half-written [target].
     * Never throws: returns false (and keeps the previous [target]) when the write fails.
     */
    private fun writeAtomically(target: File, content: String): Boolean {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        return try {
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                // Rename over an existing file normally succeeds on Android; fall back to overwriting.
                target.writeText(content)
                tmp.delete()
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not write ${target.name}", e)
            tmp.delete()
            false
        }
    }

    companion object {
        private const val TAG = "ProjectRepository"
    }
}
