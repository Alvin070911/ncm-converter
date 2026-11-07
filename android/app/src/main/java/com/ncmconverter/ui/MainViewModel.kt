package com.ncmconverter.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ncmconverter.core.NcmDecoder
import com.ncmconverter.data.AppGraph
import com.ncmconverter.data.AppSettings
import com.ncmconverter.data.ConversionState
import com.ncmconverter.data.QueueItemState
import com.ncmconverter.service.ConversionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class SelectedItem(
    val uri: Uri,
    val displayName: String,
    val title: String? = null,
    val artists: String? = null,
    val format: String? = null,
    val size: Long = 0,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val decoder = NcmDecoder()
    private val resolver = application.contentResolver

    private val _selected = MutableStateFlow<List<SelectedItem>>(emptyList())
    val selected: StateFlow<List<SelectedItem>> = _selected.asStateFlow()

    val settings: StateFlow<AppSettings> = AppGraph.settings.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings()
    )

    val history = AppGraph.history.entries
    val conversion = ConversionState.state

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val existing = _selected.value.map { it.uri.toString() }.toMutableSet()

            uris.forEach { uri ->
                val uriString = uri.toString()
                if (existing.contains(uriString)) {
                    return@forEach
                }

                val (displayName, size) = queryFile(uri)
                if (!displayName.endsWith(".ncm", ignoreCase = true)) {
                    return@forEach
                }

                existing += uriString
                val item = try {
                    if (size == null) {
                        SelectedItem(uri, displayName, error = "无法读取文件大小")
                    } else {
                        val info = resolver.openInputStream(uri)?.use { input ->
                            decoder.inspect(input, size, displayName)
                        } ?: throw IOException("无法读取文件")

                        SelectedItem(
                            uri = uri,
                            displayName = displayName,
                            title = info.metadata.title,
                            artists = info.metadata.artistText,
                            format = info.actualFormat?.displayName,
                            size = info.fileSize
                        )
                    }
                } catch (exception: Exception) {
                    SelectedItem(
                        uri = uri,
                        displayName = displayName,
                        size = size ?: 0,
                        error = exception.message ?: "无法解析文件"
                    )
                }

                _selected.value = _selected.value + item
            }
        }
    }

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(getApplication(), treeUri) ?: return@launch
            val uris = mutableListOf<Uri>()

            fun walk(document: DocumentFile) {
                document.listFiles().forEach { child ->
                    if (child.isDirectory) {
                        walk(child)
                    } else if (child.name?.endsWith(".ncm", ignoreCase = true) == true) {
                        uris += child.uri
                    }
                }
            }

            walk(tree)
            addUris(uris)
        }
    }

    fun remove(uri: Uri) {
        _selected.value = _selected.value.filterNot { it.uri == uri }
    }

    fun clearSelection() {
        _selected.value = emptyList()
    }

    fun start(context: Context) {
        val items = _selected.value.filter { it.error == null }
        if (items.isEmpty()) {
            return
        }

        ConversionService.start(context, items.map { it.uri.toString() }, settings.value)
        _selected.value = emptyList()
    }

    fun cancel(context: Context) {
        ConversionService.cancel(context)
    }

    fun clearResult() {
        ConversionState.clear()
    }

    fun updateTheme(value: String) {
        viewModelScope.launch { AppGraph.settings.updateTheme(value) }
    }

    fun updateNaming(value: String) {
        viewModelScope.launch { AppGraph.settings.updateNaming(value) }
    }

    fun updateConflict(value: String) {
        viewModelScope.launch { AppGraph.settings.updateConflict(value) }
    }

    fun updateSaveCover(value: Boolean) {
        viewModelScope.launch { AppGraph.settings.updateSaveCover(value) }
    }

    fun updateRecordHistory(value: Boolean) {
        viewModelScope.launch { AppGraph.settings.updateRecordHistory(value) }
    }

    fun updateOutputTree(uri: String?) {
        viewModelScope.launch { AppGraph.settings.updateOutputTree(uri) }
    }

    fun removeHistory(id: String) {
        viewModelScope.launch { AppGraph.history.remove(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { AppGraph.history.clear() }
    }

    private fun queryFile(uri: Uri): Pair<String, Long?> {
        var name = uri.lastPathSegment ?: "未知文件.ncm"
        var size: Long? = null

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }

                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }

        return name to size
    }

    fun resultItems(): List<QueueItemState> = conversion.value.items
}
