package com.ncmconverter.data

import android.content.Context

object AppGraph {
    lateinit var settings: SettingsRepository
        private set

    lateinit var history: HistoryRepository
        private set

    fun initialize(context: Context) {
        if (!::settings.isInitialized) {
            settings = SettingsRepository(context.applicationContext)
        }
        if (!::history.isInitialized) {
            history = HistoryRepository(context.applicationContext)
        }
    }
}
