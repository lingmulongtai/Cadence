package dev.lingmulongtai.cadence

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lingmulongtai.cadence.overlay.recording.RecordingController
import dev.lingmulongtai.cadence.overlay.recording.RecordingPhase

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RecordingEntryPoint {
    fun controller(): RecordingController
}

@Composable
internal fun DevelopmentTools() {
    val context = LocalContext.current
    val application = context.applicationContext
    val controller = remember(application) {
        EntryPointAccessors.fromApplication(application, RecordingEntryPoint::class.java).controller()
    }
    val state by controller.state.collectAsState()
    var highPrecision by rememberSaveable { mutableStateOf(false) }
    var gps by rememberSaveable { mutableStateOf(false) }
    var permissionNotice by rememberSaveable { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        permissionNotice = !allowed
        if (allowed) controller.start(highPrecision, gps)
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        gps = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
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
                } else controller.start(highPrecision, gps)
            }) { Text(stringResource(R.string.recording_start)) }
        }
        if (permissionNotice) Text(stringResource(R.string.recording_notification_permission))
        if (state.gpsStatus == "provider_disabled") Text(stringResource(R.string.recording_gps_disabled))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.path?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.sensors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.recording_transport))
    }
}
