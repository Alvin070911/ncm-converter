package com.ncmconverter.data

data class AppSettings(
    val theme: String = "System",
    val outputTreeUri: String? = null,
    val outputRelativePath: String = "Music/NCM Converter",
    val namingMode: String = "Metadata",
    val conflictPolicy: String = "Rename",
    val saveCover: Boolean = false,
    val recordHistory: Boolean = true
)
