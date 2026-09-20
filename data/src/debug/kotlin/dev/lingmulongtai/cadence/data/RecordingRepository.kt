package dev.lingmulongtai.cadence.data

import dev.lingmulongtai.cadence.motion.SensorCsv
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SavedRecording(
    val id: String,
    val label: String,
    val recordedAtEpochMillis: Long,
    val eventCount: Long,
    val durationSeconds: Long?,
)

/** Debug only. Completed files are immutable; exporting never moves or edits the originals. */
class RecordingRepository(private val directory: File) {
    fun list(): List<SavedRecording> {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Cannot read recordings directory")
        return files.asSequence().filter { it.name.endsWith(".csv") }.mapNotNull { file ->
            try { recording(file.name) } catch (_: Exception) { null }
        }.sortedByDescending { it.recordedAtEpochMillis }.toList()
    }

    fun recording(id: String): SavedRecording {
        val csv = csvFile(id)
        val sidecar = File(directory, "$id.json")
        require(csv.isFile && csv.length() > 0) { "Recording is missing or incomplete" }
        require(sidecar.isFile && sidecar.length() in 1..65_536) { "Recording metadata is missing or invalid" }
        val metadata = JSONObject(sidecar.readText(Charsets.UTF_8))
        require(metadata.getInt("schemaVersion") == 1 && metadata.getBoolean("complete")) { "Recording is incomplete" }
        val count = metadata.getLong("eventCount")
        require(count > 0) { "Recording contains no events" }
        return SavedRecording(
            id, metadata.optString("label", "").take(120),
            metadata.optLong("recordedAtEpochMillis", csv.lastModified()), count,
            if (metadata.has("durationSeconds")) metadata.getLong("durationSeconds").coerceAtLeast(0) else null,
        )
    }

    /** Call on an IO dispatcher. Only return a ZIP once validation, writing and close all succeed. */
    fun archive(id: String, exportRoot: File, suggestedName: String): File {
        val recording = recording(id)
        val csv = csvFile(id)
        val count = csv.reader(Charsets.UTF_8).use { SensorCsv.forEach(it) { } }
        require(count == recording.eventCount) { "Recording event count does not match its metadata" }
        val destination = File(exportRoot, UUID.randomUUID().toString())
        check(destination.mkdirs()) { "Cannot create export directory" }
        val completed = File(destination, zipName(suggestedName))
        val pending = File(destination, "export.partial")
        try {
            ZipOutputStream(pending.outputStream().buffered()).use { zip ->
                for (source in listOf(csv, File(directory, "$id.json"))) {
                    zip.putNextEntry(ZipEntry(source.name))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            check(pending.renameTo(completed)) { "Cannot finish export" }
            return completed
        } catch (error: Exception) {
            pending.delete()
            completed.delete()
            destination.delete()
            throw error
        }
    }

    private fun csvFile(id: String): File {
        require(id.endsWith(".csv") && '/' !in id && '\\' !in id) { "Invalid recording identifier" }
        val file = File(directory, id)
        require(file.canonicalFile.parentFile == directory.canonicalFile) { "Invalid recording path" }
        return file
    }

    companion object {
        fun zipName(name: String): String {
            val base = name.trim().replace(Regex("(?i)\\.zip$"), "")
                .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_")
                .trim(' ', '.').take(64).trimEnd(' ', '.')
            return "${base.ifBlank { "Cadence-recording" }}.zip"
        }
    }
}
