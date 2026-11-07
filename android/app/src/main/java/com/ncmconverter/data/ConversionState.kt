package com.ncmconverter.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ConversionState {
    private val _state = MutableStateFlow(ConversionUiState())
    val state: StateFlow<ConversionUiState> = _state.asStateFlow()

    fun reset(items: List<QueueItemState>) {
        _state.value = ConversionUiState(running = true, items = items, message = "准备转换…")
    }

    fun update(transform: (ConversionUiState) -> ConversionUiState) {
        _state.update(transform)
    }

    fun updateItem(id: String, transform: (QueueItemState) -> QueueItemState) {
        _state.update { state ->
            state.copy(items = state.items.map { item ->
                if (item.id == id) transform(item) else item
            })
        }
    }

    fun finish(message: String) {
        _state.update { state ->
            val completed = state.items.count { it.status == "success" }
            val failed = state.items.count { it.status == "failed" || it.status == "skipped" }
            state.copy(running = false, completed = completed, failed = failed, message = message)
        }
    }

    fun clear() {
        _state.value = ConversionUiState()
    }
}
