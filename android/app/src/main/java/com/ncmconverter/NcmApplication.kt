package com.ncmconverter

import android.app.Application
import com.ncmconverter.data.AppGraph

class NcmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
    }
}
