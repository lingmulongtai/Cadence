package dev.lingmulongtai.cadence.overlay.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.lingmulongtai.cadence.motion.RecordedEvent
import dev.lingmulongtai.cadence.motion.SensorCsv
import dev.lingmulongtai.cadence.sensor.AndroidSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Debug source set only. No recorder service or storage writer is packaged in release. */
@AndroidEntryPoint
class SensorRecordingService : Service() {
    @Inject lateinit var source: AndroidSensorSource
    @Inject lateinit var controller: RecordingController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var events: Channel<RecordedEvent>? = null
    private val stopping = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            requestStop()
            if (recordingJob == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != START) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (recordingJob?.isActive == true) return START_NOT_STICKY
        val highPrecision = intent.getBooleanExtra(HIGH_PRECISION, false)
        val gps = intent.getBooleanExtra(GPS, false)
        try {
            var serviceTypes = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            if (gps && Build.VERSION.SDK_INT >= 29) serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), serviceTypes)
        } catch (error: RuntimeException) {
            controller.mutableState.value = RecordingState(phase = RecordingPhase.FAILED, error = error.message)
            stopSelf()
            return START_NOT_STICKY
        }
        stopping.set(false)
        // About 16 seconds across five 100 Hz streams; tolerate short IO/GC stalls without unbounded RAM.
        val channel = Channel<RecordedEvent>(8192)
        events = channel
        recordingJob = scope.launch { record(channel, highPrecision, gps) }
        return START_NOT_STICKY
    }

    private suspend fun record(channel: Channel<RecordedEvent>, highPrecision: Boolean, gps: Boolean) {
        var file: File? = null
        var count = 0L
        val startNs = SystemClock.elapsedRealtimeNanos()
        var result = RecordingState(phase = RecordingPhase.FAILED)
        try {
            val external = checkNotNull(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)) { "External app storage unavailable" }
            val directory = File(external, "recordings")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create recordings directory" }
            val partial = File.createTempFile("sensors-${System.currentTimeMillis()}-", ".csv.partial", directory)
            file = partial
            val completed = File(partial.path.removeSuffix(".partial"))
            val metadataFile = File("${completed.path}.json")
            val metadata = JSONObject()
                .put("schemaVersion", 1)
                .put("complete", false)
                .put("dataKind", "unreviewed")
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("hardware", Build.HARDWARE)
                .put("requestedRateHz", if (highPrecision) 100 else 50)
                .put("gpsRequested", gps)
            FileOutputStream(partial).use { output ->
                output.bufferedWriter().use { writer ->
                    SensorCsv.writeHeader(writer)
                    val subscription = source.startRecording(highPrecision, gps, emit = { event ->
                        if (!stopping.get() && channel.trySend(event).isFailure && !stopping.get()) {
                            // Never hide loss in evidence used to tune a filter.
                            channel.close(IOException("Recording buffer overflow; this session is incomplete"))
                        }
                    }, onError = { channel.close(it) })
                    subscription.use {
                        metadata.put("gpsStatus", subscription.gpsStatus)
                        metadata.put("sensors", JSONArray().apply {
                            subscription.sensors.forEach { sensor ->
                                put(JSONObject().put("type", sensor.type).put("name", sensor.name)
                                    .put("minDelayUs", sensor.minDelayUs).put("requestedPeriodUs", sensor.requestedPeriodUs))
                            }
                        })
                        writeMetadata(metadataFile, metadata)
                        controller.mutableState.value = RecordingState(
                            phase = if (stopping.get()) RecordingPhase.STOPPING else RecordingPhase.RECORDING,
                            path = partial.path,
                            sensors = subscription.sensors.map { "${it.type} (${it.requestedPeriodUs} µs)" },
                            gpsStatus = subscription.gpsStatus,
                        )
                        var lastUpdateNs = startNs
                        for (event in channel) {
                            SensorCsv.writeEvent(writer, event)
                            count++
                            val nowNs = SystemClock.elapsedRealtimeNanos()
                            if (nowNs - lastUpdateNs >= 500_000_000L) {
                                writer.flush()
                                controller.mutableState.update {
                                    it.copy(eventCount = count, elapsedSeconds = (nowNs - startNs) / 1_000_000_000)
                                }
                                lastUpdateNs = nowNs
                            }
                        }
                    }
                    writer.flush()
                    output.fd.sync()
                }
            }
            check(count > 0) { "No sensor events received; recording is incomplete" }
            metadata.put("complete", true).put("eventCount", count)
            // Publish completion metadata first. Any failure still leaves a .partial data file.
            writeMetadata(metadataFile, metadata)
            check(partial.renameTo(completed)) { "Cannot finalize the recording" }
            file = completed
            result = controller.state.value.copy(
                phase = RecordingPhase.FINISHED, path = completed.path, eventCount = count,
                elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000_000,
            )
        } catch (error: Exception) {
            result = controller.state.value.copy(
                phase = RecordingPhase.FAILED, path = file?.path, eventCount = count,
                error = "${error.javaClass.simpleName}: ${error.message}",
            )
        } finally {
            stopping.set(true)
            channel.close()
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                events = null
                recordingJob = null
                ServiceCompat.stopForeground(this@SensorRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                controller.mutableState.value = result
                stopSelf()
            }
        }
    }

    private fun writeMetadata(file: File, metadata: JSONObject) {
        val pending = File.createTempFile("metadata-", ".partial", file.parentFile)
        try {
            FileOutputStream(pending).use { output ->
                output.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            pending.delete()
        }
    }

    private fun requestStop() {
        stopping.set(true)
        if (events != null) {
            controller.mutableState.update { it.copy(phase = RecordingPhase.STOPPING) }
        }
        events?.close() // Let the writer drain and close before stopping the foreground service.
    }

    override fun onDestroy() {
        requestStop()
        val pending = recordingJob
        if (pending?.isActive == true) pending.invokeOnCompletion { scope.cancel() } else scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Sensor recordings", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1,
            Intent(this, SensorRecordingService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Cadence · センサーログ記録中")
            .setContentText("停止するとCSVを端末内に保存します")
            .setOngoing(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_pause, "停止 / Stop", stop)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(this, 2, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return builder.build()
    }

    companion object {
        const val START = "dev.lingmulongtai.cadence.recording.START"
        const val STOP = "dev.lingmulongtai.cadence.recording.STOP"
        const val HIGH_PRECISION = "high_precision"
        const val GPS = "gps"
        private const val CHANNEL = "sensor-recording"
        private const val NOTIFICATION_ID = 1001
    }
}
