package com.ncmconverter.data

data class QueueItemState(
    val id: String,
    val displayName: String,
    val title: String? = null,
    val artists: String? = null,
    val format: String? = null,
    val status: String = "pending",
    val progress: Float = 0f,
    val error: String? = null,
    val outputUri: String? = null
)

data class ConversionUiState(
    val running: Boolean = false,
    val items: List<QueueItemState> = emptyList(),
    val completed: Int = 0,
    val failed: Int = 0,
    val message: String? = null
) {
    val total: Int get() = items.size
    val progress: Float
        get() = if (items.isEmpty()) 0f else items.map { it.progress }.average().toFloat()

    val currentTitle: String?
        get() = items.firstOrNull { it.status == "converting" }?.title
            ?: items.firstOrNull { it.status == "converting" }?.displayName
}
