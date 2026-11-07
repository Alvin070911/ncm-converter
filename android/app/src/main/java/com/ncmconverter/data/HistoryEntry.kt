package com.ncmconverter.data

data class HistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String? = null,
    val artists: String? = null,
    val album: String? = null,
    val sourceFormat: String? = null,
    val outputFormat: String = "",
    val sourceSize: Long = 0,
    val outputSize: Long = 0,
    val outputUri: String? = null,
    val finishedAt: Long = System.currentTimeMillis(),
    val elapsedMs: Long = 0,
    val status: String = "success",
    val errorMessage: String? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "未命名"

    val subtitle: String
        get() = listOfNotNull(
            artists?.takeIf { it.isNotBlank() },
            outputFormat.uppercase()
        ).joinToString(" · ")

    val statusText: String
        get() = when (status) {
            "success" -> "完成"
            "skipped" -> "已跳过"
            else -> "失败"
        }
}
