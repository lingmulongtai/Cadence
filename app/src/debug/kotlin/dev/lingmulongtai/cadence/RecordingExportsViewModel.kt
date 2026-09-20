package dev.lingmulongtai.cadence

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.lingmulongtai.cadence.data.RecordingRepository
import dev.lingmulongtai.cadence.data.SavedRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class RecordingExportsState(
    val recordings: List<SavedRecording> = emptyList(),
    val busy: Boolean = false,
    val message: Int? = null,
    val error: String? = null,
    val sharePath: String? = null,
)

/** Retains ongoing IO across rotation; SavedStateHandle restores the picker target after process death. */
internal class RecordingExportsViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val interruptedWrite = saved.get<String>(WRITING_URI) != null
    private val mutableState = MutableStateFlow(RecordingExportsState(
        busy = !interruptedWrite && saved.get<String>(PENDING_ID) != null,
        message = if (interruptedWrite) R.string.recording_export_interrupted else null,
    ))
    val state = mutableState.asStateFlow()
    init {
        if (interruptedWrite) clearPending()
    }
    private val app get() = getApplication<Application>()
    private fun repository(): RecordingRepository {
        val external = app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: throw IOException("Device storage unavailable")
        return RecordingRepository(File(external, "recordings"))
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val recordings = withContext(Dispatchers.IO) { repository().list() }
                mutableState.update { it.copy(recordings = recordings) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(message = R.string.recording_list_failed, error = error.message) }
            }
        }
    }

    fun beginSave(recording: SavedRecording): String? {
        if (state.value.busy) return null
        saved[PENDING_ID] = recording.id
        saved[PENDING_NAME] = archiveName(recording)
        mutableState.update { it.copy(busy = true, message = null, error = null) }
        return saved[PENDING_NAME]
    }

    fun saveTo(uri: Uri?) {
        val id = saved.get<String>(PENDING_ID)
        val name = saved.get<String>(PENDING_NAME)
        if (uri == null) {
            clearPending()
            mutableState.update { it.copy(busy = false) }
            return
        }
        saved[WRITING_URI] = uri.toString()
        viewModelScope.launch {
            try {
                checkNotNull(id) { "Recording selection was lost; please save again" }
                withContext(Dispatchers.IO) {
                    var archive: File? = null
                    try {
                        archive = repository().archive(id, File(app.cacheDir, "recording-exports"), name.orEmpty())
                        val output = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open selected destination")
                        output.use { target -> archive.inputStream().use { it.copyTo(target) } }
                    } catch (error: Exception) {
                        // This URI was just created by our picker. Best effort cleanup of a failed copy only.
                        runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                        throw error
                    } finally {
                        archive?.let { it.delete(); it.parentFile?.delete() }
                    }
                }
                mutableState.update { it.copy(message = R.string.recording_export_saved, error = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(message = R.string.recording_export_failed, error = error.message) }
            } finally {
                clearPending()
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun prepareShare(recording: SavedRecording) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                val archive = withContext(Dispatchers.IO) {
                    repository().archive(recording.id, File(app.cacheDir, "recording-exports"), archiveName(recording))
                }
                // Keep shared cache files available to the receiver after the chooser closes.
                mutableState.update { it.copy(sharePath = archive.path) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(message = R.string.recording_export_failed, error = error.message) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun shareLaunched(error: Exception? = null) {
        mutableState.update { it.copy(sharePath = null, message = if (error == null) null else R.string.recording_export_failed, error = error?.message) }
    }

    fun pickerFailed(error: Exception) {
        saveTo(null)
        mutableState.update { it.copy(message = R.string.recording_export_failed, error = error.message) }
    }

    private fun archiveName(recording: SavedRecording): String {
        val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(recording.recordedAtEpochMillis))
        return RecordingRepository.zipName("${recording.label.ifBlank { "Cadence" }}-$date")
    }

    private fun clearPending() {
        saved.remove<String>(PENDING_ID)
        saved.remove<String>(PENDING_NAME)
        saved.remove<String>(WRITING_URI)
    }

    companion object {
        private const val PENDING_ID = "exportRecordingId"
        private const val PENDING_NAME = "exportRecordingName"
        private const val WRITING_URI = "exportWritingUri"
    }
}
