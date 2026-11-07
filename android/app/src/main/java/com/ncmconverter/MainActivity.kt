package com.ncmconverter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.ncmconverter.ui.MainViewModel
import com.ncmconverter.ui.NcmApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val openFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addUris(uris)
    }

    private val openFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.addFolder(it) }
    }

    private val pickOutput = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                // 某些文件提供方不支持持久化权限
            }
            viewModel.updateOutputTree(uri.toString())
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var pendingStart = false
    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (pendingStart) {
            pendingStart = false
            viewModel.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        setContent {
            NcmApp(
                viewModel = viewModel,
                onPickFiles = {
                    openFiles.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onPickFolder = {
                    openFolder.launch(null)
                },
                onPickOutput = {
                    pickOutput.launch(null)
                },
                onStart = { requestStart() },
                onCancel = { viewModel.cancel(this) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun requestStart() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = true
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.start(this)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }

        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }

            else -> null
        }

        uri?.let { viewModel.addUris(listOf(it)) }
    }
}
