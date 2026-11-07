package com.ncmconverter.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.ncmconverter.data.AppSettings
import com.ncmconverter.data.ConversionUiState
import com.ncmconverter.data.HistoryEntry
import com.ncmconverter.data.QueueItemState
import com.ncmconverter.ui.theme.NcmConverterTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcmApp(
    viewModel: MainViewModel,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickOutput: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val conversion by viewModel.conversion.collectAsState()
    val history by viewModel.history.collectAsState()

    var screen by rememberSaveable { mutableStateOf("home") }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyFilter by rememberSaveable { mutableStateOf("all") }

    val snackbarHostState = remember { SnackbarHostState() }
    var shownMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(conversion.message) {
        val message = conversion.message
        if (message != null && message != shownMessage) {
            shownMessage = message
            snackbarHostState.showSnackbar(message)
        }
    }

    val outputLabel = remember(settings.outputTreeUri, settings.outputRelativePath) {
        settings.outputTreeUri?.let { raw ->
            try {
                DocumentFile.fromTreeUri(context, Uri.parse(raw))?.name
            } catch (exception: Exception) {
                null
            }
        } ?: settings.outputRelativePath
    }

    NcmConverterTheme(settings.theme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (screen) {
                                "history" -> "转换历史"
                                "settings" -> "设置"
                                "about" -> "关于"
                                else -> "NCM 转 FLAC"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        if (screen != "home") {
                            IconButton(onClick = { screen = "home" }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        }
                    },
                    actions = {
                        if (screen == "home") {
                            IconButton(onClick = { screen = "history" }) {
                                Icon(Icons.Filled.History, contentDescription = "历史")
                            }
                            IconButton(onClick = { screen = "settings" }) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置")
                            }
                            IconButton(onClick = { screen = "about" }) {
                                Icon(Icons.Filled.Info, contentDescription = "关于")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (screen) {
                    "history" -> HistoryScreen(
                        entries = history,
                        query = historyQuery,
                        filter = historyFilter,
                        onQueryChange = { historyQuery = it },
                        onFilterChange = { historyFilter = it },
                        onOpen = { openHistoryEntry(context, it) },
                        onDelete = { viewModel.removeHistory(it.id) },
                        onClear = { viewModel.clearHistory() }
                    )

                    "settings" -> SettingsScreen(
                        settings = settings,
                        outputLabel = outputLabel,
                        onTheme = viewModel::updateTheme,
                        onNaming = viewModel::updateNaming,
                        onConflict = viewModel::updateConflict,
                        onSaveCover = viewModel::updateSaveCover,
                        onRecordHistory = viewModel::updateRecordHistory,
                        onPickOutput = onPickOutput,
                        onResetOutput = { viewModel.updateOutputTree(null) },
                        onClearHistory = { viewModel.clearHistory() }
                    )

                    "about" -> AboutScreen()

                    else -> HomeScreen(
                        context = context,
                        selected = selected,
                        conversion = conversion,
                        outputLabel = outputLabel,
                        onPickFiles = onPickFiles,
                        onPickFolder = onPickFolder,
                        onPickOutput = onPickOutput,
                        onStart = onStart,
                        onCancel = onCancel,
                        onRemove = viewModel::remove,
                        onClearResult = viewModel::clearResult,
                        onViewHistory = { screen = "history" }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    context: Context,
    selected: List<SelectedItem>,
    conversion: ConversionUiState,
    outputLabel: String,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickOutput: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRemove: (Uri) -> Unit,
    onClearResult: () -> Unit,
    onViewHistory: () -> Unit
) {
    val canStart = selected.any { it.error == null } && !conversion.running

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ImportCard(onPickFiles, onPickFolder) }

        if (conversion.running) {
            item { ConversionProgressCard(conversion, onCancel) }
            items(conversion.items, key = { it.id }) { item ->
                QueueRow(item)
            }
        } else if (conversion.items.isNotEmpty()) {
            item {
                SummaryCard(
                    conversion = conversion,
                    onViewHistory = onViewHistory,
                    onClear = onClearResult
                )
            }
            items(conversion.items, key = { it.id }) { item ->
                QueueRow(item)
            }
        } else if (selected.isNotEmpty()) {
            item {
                Text(
                    text = "已选择 ${selected.size} 个文件",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(selected, key = { it.uri.toString() }) { item ->
                SelectedRow(item, onRemove)
            }
        } else {
            item { EmptyState() }
        }

        item { OutputCard(outputLabel, onPickOutput) }

        item {
            if (conversion.running) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("取消转换")
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (selected.isEmpty()) {
                            "开始转换"
                        } else {
                            "开始转换（${selected.count { it.error == null }}）"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCard(onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "NCM 转 FLAC",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                    Text(
                        "完全离线 · 保留原始 FLAC/MP3 · 自动写入标签与封面",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPickFiles,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件")
                }

                OutlinedButton(onClick = onPickFolder) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件夹")
                }
            }
        }
    }
}

@Composable
private fun SelectedRow(item: SelectedItem, onRemove: (Uri) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: item.displayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        item.artists?.takeIf { it.isNotBlank() },
                        formatSize(item.size)
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.error?.let { error ->
                    Text(
                        error,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item.format?.let { format ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        format,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            IconButton(onClick = { onRemove(item.uri) }) {
                Icon(Icons.Filled.Close, contentDescription = "移除")
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItemState) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title ?: item.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(item.artists, item.format).joinToString(" · "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    statusText(item.status, item.progress),
                    fontSize = 12.sp,
                    color = statusColor(item.status),
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (item.status == "converting") {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (item.status == "failed") {
                item.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionProgressCard(conversion: ConversionUiState, onCancel: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "正在转换",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
            Text(
                conversion.currentTitle ?: "准备中…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { conversion.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${conversion.progress.toInt()}% · 完成 ${conversion.items.count { it.status == "success" }} / ${conversion.total}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryCard(
    conversion: ConversionUiState,
    onViewHistory: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                conversion.message ?: "转换完成",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onViewHistory) {
                    Text("查看历史")
                }
                OutlinedButton(onClick = onClear) {
                    Text("清除结果")
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            "把 NCM 文件加入队列",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "支持多选与文件夹递归扫描",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OutputCard(outputLabel: String, onPickOutput: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "输出位置",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    outputLabel,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onPickOutput) {
                Text("更改")
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    entries: List<HistoryEntry>,
    query: String,
    filter: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    val filtered = entries.filter { entry ->
        val matchesQuery = query.isBlank() || listOfNotNull(
            entry.title, entry.artists, entry.album, entry.errorMessage
        ).any { it.contains(query, ignoreCase = true) }

        val matchesFilter = when (filter) {
            "success" -> entry.status == "success"
            "failed" -> entry.status != "success"
            else -> true
        }

        matchesQuery && matchesFilter
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索歌名 / 歌手") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == "all",
                onClick = { onFilterChange("all") },
                label = { Text("全部") }
            )
            FilterChip(
                selected = filter == "success",
                onClick = { onFilterChange("success") },
                label = { Text("成功") }
            )
            FilterChip(
                selected = filter == "failed",
                onClick = { onFilterChange("failed") },
                label = { Text("失败") }
            )
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有转换记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    HistoryRow(entry, onOpen, onDelete)
                }
            }
        }

        TextButton(
            onClick = { showClearDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("清空历史", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史") },
            text = { Text("只会删除转换记录，不会删除音频文件。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClear()
                }) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.displayTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        entry.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    entry.statusText,
                    fontSize = 12.sp,
                    color = statusColor(if (entry.status == "success") "success" else "failed"),
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (entry.status != "success" && !entry.errorMessage.isNullOrBlank()) {
                Text(
                    entry.errorMessage!!,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDate(entry.finishedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onOpen(entry) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("打开")
                }
                TextButton(onClick = { onDelete(entry) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    outputLabel: String,
    onTheme: (String) -> Unit,
    onNaming: (String) -> Unit,
    onConflict: (String) -> Unit,
    onSaveCover: (Boolean) -> Unit,
    onRecordHistory: (Boolean) -> Unit,
    onPickOutput: () -> Unit,
    onResetOutput: () -> Unit,
    onClearHistory: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsCard(title = "外观") {
            SettingDropdown(
                label = "主题",
                description = "默认使用 B站粉色主色调",
                value = settings.theme,
                options = listOf(
                    "System" to "跟随系统",
                    "Light" to "浅色",
                    "Dark" to "深色"
                ),
                onSelect = onTheme
            )
        }

        SettingsCard(title = "转换") {
            SettingDropdown(
                label = "文件命名",
                description = "默认使用歌名与歌手信息",
                value = settings.namingMode,
                options = listOf(
                    "Metadata" to "歌曲信息命名",
                    "Original" to "保留原文件名"
                ),
                onSelect = onNaming
            )
            HorizontalDivider()
            SettingDropdown(
                label = "重名处理",
                description = "目标文件已存在时的行为",
                value = settings.conflictPolicy,
                options = listOf(
                    "Rename" to "自动编号",
                    "Skip" to "跳过",
                    "Overwrite" to "覆盖"
                ),
                onSelect = onConflict
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("输出位置", fontWeight = FontWeight.SemiBold)
                    Text(
                        outputLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onPickOutput) { Text("更改") }
                TextButton(onClick = onResetOutput) { Text("默认") }
            }
            HorizontalDivider()
            SwitchSetting(
                label = "封面另存为图片",
                description = "在音频同目录生成 cover.jpg / cover.png",
                checked = settings.saveCover,
                onCheckedChange = onSaveCover
            )
        }

        SettingsCard(title = "历史与数据") {
            SwitchSetting(
                label = "记录转换历史",
                description = "最多保留最近 300 条",
                checked = settings.recordHistory,
                onCheckedChange = onRecordHistory
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("清空历史", fontWeight = FontWeight.SemiBold)
                    Text(
                        "只删除记录，不会删除音频文件",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showClearDialog = true }) { Text("清空") }
            }
        }

        SettingsCard(title = "关于输出") {
            Text(
                "FLAC 源输出 .flac，MP3 源输出 .mp3；只解密容器，不做二次编码。转换不会修改或删除源 NCM 文件。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史") },
            text = { Text("只会删除转换记录，不会删除音频文件。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearHistory()
                }) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingDropdown(
    label: String,
    description: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second ?: value

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (tag, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(tag)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(14.dp).size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("NCM 转 FLAC", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "版本 0.1.0 · 完全离线的 NCM 音频转换工具",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "把网易云音乐 NCM 文件还原为原始 FLAC/MP3，保留歌曲信息与内嵌封面。零网络请求，不上传任何文件。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsCard(title = "功能") {
            Text("· 仅解密容器，不做二次编码", fontSize = 13.sp)
            Text("· 批量队列、进度、取消与失败重试", fontSize = 13.sp)
            Text("· 自动写入歌名、歌手、专辑与封面", fontSize = 13.sp)
            Text("· 转换历史、深浅主题、设置持久化", fontSize = 13.sp)
        }

        SettingsCard(title = "合规声明") {
            Text(
                "本工具仅用于用户对自己合法获得的个人音频进行本地格式还原与备份。请勿用于传播、分发或任何侵犯版权的行为。与网易云音乐及其关联公司无任何关系，未获其授权或背书。因使用本工具产生的任何法律责任由使用者自行承担。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard(title = "开源与许可") {
            Text(
                "MIT License · 第三方组件：AndroidX / Compose / Gson / DataStore",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Alvin070911/ncm-converter")
                        )
                    )
                } catch (exception: Exception) {
                    Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("github.com/Alvin070911/ncm-converter")
            }
        }
    }
}

private fun openHistoryEntry(context: Context, entry: HistoryEntry) {
    val raw = entry.outputUri ?: run {
        Toast.makeText(context, "没有可打开的输出文件", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = try {
        if (raw.startsWith("content://")) {
            Uri.parse(raw)
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                File(raw)
            )
        }
    } catch (exception: Exception) {
        Toast.makeText(context, "输出文件不存在", Toast.LENGTH_SHORT).show()
        return
    }

    val mime = when (entry.outputFormat.uppercase()) {
        "FLAC" -> "audio/flac"
        "MP3" -> "audio/mpeg"
        "M4A" -> "audio/mp4"
        "OGG" -> "audio/ogg"
        "WAV" -> "audio/wav"
        else -> "audio/*"
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (exception: Exception) {
        Toast.makeText(context, "没有可打开该音频的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun statusText(status: String, progress: Float): String = when (status) {
    "converting" -> "${(progress * 100).toInt()}%"
    "success" -> "完成"
    "failed" -> "失败"
    "cancelled" -> "已取消"
    "skipped" -> "已跳过"
    else -> "等待"
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "success" -> Color(0xFF22C55E)
    "failed" -> MaterialTheme.colorScheme.error
    "cancelled", "skipped" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    if (bytes < 1024L * 1024 * 1024) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    }
    return String.format(Locale.getDefault(), "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))


