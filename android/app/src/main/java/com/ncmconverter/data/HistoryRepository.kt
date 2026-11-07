package com.ncmconverter.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class HistoryRepository(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "history.json")
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val lock = Any()

    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    init {
        val loaded = load()
        _entries.value = loaded
    }

    suspend fun add(entry: HistoryEntry) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
            _entries.value = updated
            save(updated)
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val updated = _entries.value.filterNot { it.id == id }
            _entries.value = updated
            save(updated)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            _entries.value = emptyList()
            save(emptyList())
        }
    }

    private fun load(): List<HistoryEntry> {
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val type = object : TypeToken<List<HistoryEntry>>() {}.type
            gson.fromJson<List<HistoryEntry>>(file.readText(), type) ?: emptyList()
        } catch (exception: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<HistoryEntry>) {
        try {
            val temp = File(context.filesDir, "history-${UUID.randomUUID()}.tmp")
            temp.writeText(gson.toJson(entries))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (exception: Exception) {
            // 历史写入失败不影响转换
        }
    }

    companion object {
        private const val MAX_ENTRIES = 300
    }
}
