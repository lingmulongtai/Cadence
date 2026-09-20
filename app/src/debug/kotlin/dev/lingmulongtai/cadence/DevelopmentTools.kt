package dev.lingmulongtai.cadence

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lingmulongtai.cadence.overlay.recording.RecordingController
import dev.lingmulongtai.cadence.overlay.recording.RecordingPhase
import java.io.File
import java.text.DateFormat
import java.util.Date

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RecordingEntryPoint {
    fun controller(): RecordingController
}

@Composable
internal fun DevelopmentTools(activity: ComponentActivity) {
    val context = LocalContext.current
    val application = context.applicationContext
    val controller = remember(application) {
        EntryPointAccessors.fromApplication(application, RecordingEntryPoint::class.java).controller()
    }
    val state by controller.state.collectAsState()
    val exports = remember(activity) { ViewModelProvider(activity)[RecordingExportsViewModel::class.java] }
    val exportState by exports.state.collectAsState()
    var label by rememberSaveable { mutableStateOf("") }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var highPrecision by rememberSaveable { mutableStateOf(false) }
    var gps by rememberSaveable { mutableStateOf(false) }
    var permissionNotice by rememberSaveable { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        permissionNotice = !allowed
        if (allowed) controller.start(highPrecision, gps, label)
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        gps = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), exports::saveTo)
    LaunchedEffect(state.phase) { if (!state.isActive) exports.refresh() }
    DisposableEffect(activity, exports) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) exports.refresh() }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val shareTitle = stringResource(R.string.recording_share)
    LaunchedEffect(exportState.sharePath) {
        exportState.sharePath?.let { path ->
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.recording.exports", File(path))
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Cadence recording", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, shareTitle))
                exports.shareLaunched()
            } catch (error: Exception) { exports.shareLaunched(error) }
        }
    }
    val view = LocalView.current
    DisposableEffect(view, state.isActive) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.isActive
        onDispose { view.keepScreenOn = previous }
    }
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.recording_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.recording_explanation))
        OutlinedTextField(value = label, onValueChange = { label = it.take(120) },
            label = { Text(stringResource(R.string.recording_name)) },
            placeholder = { Text(stringResource(R.string.recording_name_example)) },
            singleLine = true, enabled = !state.isActive, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = highPrecision, enabled = !state.isActive,
                role = Role.Checkbox, onValueChange = { highPrecision = it })) {
            Checkbox(checked = highPrecision, onCheckedChange = null, enabled = !state.isActive)
            Text(stringResource(R.string.recording_high_precision))
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = gps, enabled = !state.isActive,
                role = Role.Checkbox, onValueChange = { selected ->
                if (!selected) gps = false
                else if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) gps = true
                else location.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            })) {
            Checkbox(checked = gps, enabled = !state.isActive, onCheckedChange = null)
            Text(stringResource(R.string.recording_gps))
        }
        Text(stringResource(when (state.phase) {
            RecordingPhase.IDLE -> R.string.recording_idle
            RecordingPhase.STARTING -> R.string.recording_starting
            RecordingPhase.RECORDING -> R.string.recording_active
            RecordingPhase.STOPPING -> R.string.recording_stopping
            RecordingPhase.FINISHED -> R.string.recording_finished
            RecordingPhase.FAILED -> R.string.recording_failed
        }))
        Text(stringResource(R.string.recording_count, state.eventCount, state.elapsedSeconds))
        if (state.isActive) {
            Button(modifier = Modifier.fillMaxWidth(), enabled = state.phase != RecordingPhase.STOPPING,
                onClick = controller::stop) { Text(stringResource(R.string.recording_stop)) }
        } else {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                permissionNotice = false
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else controller.start(highPrecision, gps, label)
            }) { Text(stringResource(R.string.recording_start)) }
        }
        if (permissionNotice) Text(stringResource(R.string.recording_notification_permission))
        if (state.gpsStatus == "provider_disabled") Text(stringResource(R.string.recording_gps_disabled))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.sensors.isNotEmpty()) {
            TextButton(onClick = { showDetails = !showDetails }) { Text(stringResource(R.string.recording_details)) }
            if (showDetails) state.sensors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.recording_saved_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.recording_transport), style = MaterialTheme.typography.bodyMedium)
        if (exportState.busy) Text(stringResource(R.string.recording_export_busy))
        exportState.message?.let { Text(stringResource(it), color = if (exportState.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        exportState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (exportState.recordings.isEmpty()) Text(stringResource(R.string.recording_saved_empty))
        exportState.recordings.forEach { recording ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(recording.label.ifBlank { stringResource(R.string.recording_unnamed) }, style = MaterialTheme.typography.titleMedium)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(recording.recordedAtEpochMillis)))
                    recording.durationSeconds?.let { Text(stringResource(R.string.recording_count, recording.eventCount, it)) }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !exportState.busy && !state.isActive, onClick = {
                        exports.beginSave(recording)?.let { name ->
                            try { saveDocument.launch(name) } catch (error: Exception) { exports.pickerFailed(error) }
                        }
                    }) { Text(stringResource(R.string.recording_save_as)) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !exportState.busy && !state.isActive,
                        onClick = { exports.prepareShare(recording) }) { Text(stringResource(R.string.recording_share)) }
                }
            }
        }
    }
}
