package com.ncmconverter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ncmconverter.MainActivity
import com.ncmconverter.R
import com.ncmconverter.core.AudioFormat
import com.ncmconverter.core.ConflictPolicy
import com.ncmconverter.core.DecodeOptions
import com.ncmconverter.core.NamingMode
import com.ncmconverter.core.NcmDecoder
import com.ncmconverter.core.NcmErrorCode
import com.ncmconverter.core.NcmException
import com.ncmconverter.data.AppGraph
import com.ncmconverter.data.ConversionState
import com.ncmconverter.data.HistoryEntry
import com.ncmconverter.data.QueueItemState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

class ConversionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val decoder = NcmDecoder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            markRemainingCancelled()
            ConversionState.finish("已取消")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START) {
            startForegroundCompat(buildNotification(0f, "准备转换…"))
            job?.cancel()
            job = serviceScope.launch {
                runConversion(intent)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private suspend fun runConversion(intent: Intent) {
        val uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
        if (uris.isEmpty()) {
            ConversionState.finish("没有可转换的文件")
            stopSelf()
            return
        }

        val namingMode = if (intent.getStringExtra(EXTRA_NAMING) == "Original") {
            NamingMode.SOURCE_FILE_NAME
        } else {
            NamingMode.METADATA
        }

        val conflictPolicy = when (intent.getStringExtra(EXTRA_CONFLICT)) {
            "Skip" -> ConflictPolicy.SKIP
            "Overwrite" -> ConflictPolicy.OVERWRITE
            else -> ConflictPolicy.RENAME
        }

        val saveCover = intent.getBooleanExtra(EXTRA_SAVE_COVER, false)
        val recordHistory = intent.getBooleanExtra(EXTRA_RECORD_HISTORY, true)
        val outputTree = intent.getStringExtra(EXTRA_OUTPUT_TREE)
        val outputRelative = intent.getStringExtra(EXTRA_OUTPUT_RELATIVE) ?: "Music/NCM Converter"
        val writer = OutputWriter(this, outputTree, outputRelative)

        val queueItems = uris.map { uriString ->
            QueueItemState(
                id = UUID.randomUUID().toString(),
                displayName = displayName(Uri.parse(uriString))
            )
        }
        ConversionState.reset(queueItems)

        val context = currentCoroutineContext()
        val completedIds = mutableSetOf<String>()
        val failedIds = mutableSetOf<String>()

        for ((index, uriString) in uris.withIndex()) {
            if (!context.isActive) {
                break
            }

            val uri = Uri.parse(uriString)
            val item = queueItems[index]
            var inputFile: File? = null
            var outputDirectory: File? = null
            val startedAt = System.currentTimeMillis()

            try {
                ConversionState.updateItem(item.id) {
                    it.copy(status = "converting", progress = 0f)
                }
                notifyProgress(index, uris.size, 0f, "读取 ${item.displayName}")

                inputFile = File(cacheDir, "ncm-${UUID.randomUUID()}.ncm")
                copyToFile(uri, inputFile)

                val info = decoder.inspect(inputFile)
                ConversionState.updateItem(item.id) {
                    it.copy(
                        title = info.metadata.title,
                        artists = info.metadata.artistText,
                        format = info.actualFormat?.displayName
                    )
                }

                outputDirectory = File(cacheDir, "out-${UUID.randomUUID()}")
                if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                    throw IOException("无法创建临时输出目录")
                }

                val options = DecodeOptions(
                    conflictPolicy = conflictPolicy,
                    saveCover = saveCover,
                    namingMode = namingMode
                )

                val result = decoder.decode(
                    file = inputFile,
                    outputDirectory = outputDirectory,
                    options = options,
                    progress = { processed, total ->
                        val progress = if (total > 0) processed.toFloat() / total else 0f
                        ConversionState.updateItem(item.id) { it.copy(progress = progress) }
                        notifyProgress(
                            index, uris.size, progress,
                            info.metadata.title ?: item.displayName
                        )
                    },
                    isCancelled = { !context.isActive }
                )

                val saved = writer.save(File(result.outputPath), result.format)
                ConversionState.updateItem(item.id) {
                    it.copy(status = "success", progress = 1f, outputUri = saved.uri)
                }
                completedIds += item.id

                if (recordHistory) {
                    AppGraph.history.add(
                        HistoryEntry(
                            title = result.metadata.title,
                            artists = result.metadata.artistText,
                            album = result.metadata.album,
                            sourceFormat = info.actualFormat?.displayName,
                            outputFormat = result.format.displayName,
                            sourceSize = inputFile.length(),
                            outputSize = saved.size,
                            outputUri = saved.uri,
                            finishedAt = System.currentTimeMillis(),
                            elapsedMs = System.currentTimeMillis() - startedAt,
                            status = "success"
                        )
                    )
                }
            } catch (exception: CancellationException) {
                ConversionState.updateItem(item.id) { it.copy(status = "cancelled", error = "已取消") }
                markRemainingCancelled()
                break
            } catch (exception: NcmException) {
                if (exception.code == NcmErrorCode.CANCELLED) {
                    ConversionState.updateItem(item.id) {
                        it.copy(status = "cancelled", error = "已取消")
                    }
                    markRemainingCancelled()
                    break
                }

                val skipped = exception.code == NcmErrorCode.CONFLICT_SKIPPED
                val status = if (skipped) "skipped" else "failed"
                ConversionState.updateItem(item.id) {
                    it.copy(status = status, error = exception.message)
                }
                failedIds += item.id

                if (recordHistory) {
                    AppGraph.history.add(
                        HistoryEntry(
                            title = item.title,
                            artists = item.artists,
                            sourceSize = inputFile?.length() ?: 0,
                            sourceFormat = item.format,
                            outputFormat = item.format ?: "未知",
                            finishedAt = System.currentTimeMillis(),
                            elapsedMs = System.currentTimeMillis() - startedAt,
                            status = status,
                            errorMessage = exception.message
                        )
                    )
                }
            } catch (exception: Exception) {
                ConversionState.updateItem(item.id) {
                    it.copy(status = "failed", error = exception.message ?: "转换失败")
                }
                failedIds += item.id

                if (recordHistory) {
                    AppGraph.history.add(
                        HistoryEntry(
                            title = item.title,
                            artists = item.artists,
                            sourceSize = inputFile?.length() ?: 0,
                            sourceFormat = item.format,
                            outputFormat = item.format ?: "未知",
                            finishedAt = System.currentTimeMillis(),
                            elapsedMs = System.currentTimeMillis() - startedAt,
                            status = "failed",
                            errorMessage = exception.message
                        )
                    )
                }
            } finally {
                try {
                    inputFile?.delete()
                } catch (_: Exception) {
                }
                try {
                    outputDirectory?.deleteRecursively()
                } catch (_: Exception) {
                }
            }
        }

        val cancelled = !context.isActive || queueItems.any { item ->
            ConversionState.state.value.items.any { it.id == item.id && it.status == "cancelled" }
        }

        val message = if (cancelled) {
            "已取消"
        } else {
            "转换完成：成功 ${completedIds.size} 个，失败 ${failedIds.size} 个"
        }

        ConversionState.finish(message)
        notifyProgress(uris.size, uris.size, 1f, message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun markRemainingCancelled() {
        ConversionState.update { state ->
            state.copy(
                running = false,
                items = state.items.map { item ->
                    if (item.status == "pending" || item.status == "converting") {
                        item.copy(status = "cancelled", error = "已取消")
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun copyToFile(uri: Uri, target: File) {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取源文件")
        input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        return cursor.getString(index)
                    }
                }
            }

        return uri.lastPathSegment ?: "未知文件.ncm"
    }

    private fun notifyProgress(index: Int, total: Int, itemProgress: Float, text: String) {
        val overall = if (total <= 0) 0f else ((index + itemProgress) / total * 100)
        val notification = buildNotification(overall, text)
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 通知权限被拒绝时仍继续转换
        }
    }

    private fun buildNotification(percent: Float, text: String): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ConversionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NCM 转 FLAC")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.toInt().coerceIn(0, 100), false)
            .addAction(0, "取消", cancelIntent)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "转换进度",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "conversion"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.ncmconverter.action.START"
        const val ACTION_CANCEL = "com.ncmconverter.action.CANCEL"
        const val EXTRA_URIS = "uris"
        const val EXTRA_NAMING = "naming"
        const val EXTRA_CONFLICT = "conflict"
        const val EXTRA_SAVE_COVER = "save_cover"
        const val EXTRA_RECORD_HISTORY = "record_history"
        const val EXTRA_OUTPUT_TREE = "output_tree"
        const val EXTRA_OUTPUT_RELATIVE = "output_relative"

        fun start(context: Context, uris: List<String>, settings: com.ncmconverter.data.AppSettings) {
            val intent = Intent(context, ConversionService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putExtra(EXTRA_NAMING, settings.namingMode)
                putExtra(EXTRA_CONFLICT, settings.conflictPolicy)
                putExtra(EXTRA_SAVE_COVER, settings.saveCover)
                putExtra(EXTRA_RECORD_HISTORY, settings.recordHistory)
                putExtra(EXTRA_OUTPUT_TREE, settings.outputTreeUri)
                putExtra(EXTRA_OUTPUT_RELATIVE, settings.outputRelativePath)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ConversionService::class.java).setAction(ACTION_CANCEL)
            context.startService(intent)
        }
    }
}
