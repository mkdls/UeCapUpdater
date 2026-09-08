package com.pixelthings.uecapupdater

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

@Composable
fun UecapEditorScreen(isEn: Boolean) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 取得當前系統/應用的 Material 3 動態色彩並轉為 Hex 色碼
    val colorScheme = MaterialTheme.colorScheme
    fun Color.toCssHex(): String = String.format("#%06X", 0xFFFFFF and this.toArgb())

    val mdBg = colorScheme.background.toCssHex()
    val mdSurface = colorScheme.surfaceContainer.toCssHex()
    val mdSurfaceVariant = colorScheme.surfaceContainerHigh.toCssHex()
    val mdOnSurface = colorScheme.onSurface.toCssHex()
    val mdOnSurfaceVariant = colorScheme.onSurfaceVariant.toCssHex()
    val mdOutline = colorScheme.outlineVariant.toCssHex()
    val mdPrimary = colorScheme.primary.toCssHex()
    val mdOnPrimary = colorScheme.onPrimary.toCssHex()
    val mdError = colorScheme.error.toCssHex()
    val mdOnError = colorScheme.onError.toCssHex()

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingFileData by remember { mutableStateOf<ByteArray?>(null) }

    // 處理使用者選擇儲存路徑的 Launcher
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && pendingFileData != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingFileData!!)
                }
                Toast.makeText(context, if (isEn) "Saved successfully" else "儲存成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, if (isEn) "Failed to save" else "儲存失敗", Toast.LENGTH_SHORT).show()
            }
        }
        pendingFileData = null
    }

    // 處理選擇本機 .binarypb 檔案的 Launcher
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true

                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                // 註冊 AndroidBridge 供網頁回傳 Base64 檔案資料
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun saveFile(base64Data: String, filename: String) {
                        coroutineScope.launch {
                            try {
                                pendingFileData = Base64.decode(base64Data, Base64.DEFAULT)
                                saveFileLauncher.launch(filename)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val js = """
                            document.documentElement.style.setProperty('--md-bg', '$mdBg');
                            document.documentElement.style.setProperty('--md-surface', '$mdSurface');
                            document.documentElement.style.setProperty('--md-surface-variant', '$mdSurfaceVariant');
                            document.documentElement.style.setProperty('--md-on-surface', '$mdOnSurface');
                            document.documentElement.style.setProperty('--md-on-surface-variant', '$mdOnSurfaceVariant');
                            document.documentElement.style.setProperty('--md-outline', '$mdOutline');
                            document.documentElement.style.setProperty('--md-primary', '$mdPrimary');
                            document.documentElement.style.setProperty('--md-on-primary', '$mdOnPrimary');
                            document.documentElement.style.setProperty('--md-error', '$mdError');
                            document.documentElement.style.setProperty('--md-on-error', '$mdOnError');
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("UecapEditorWeb", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (${consoleMessage?.lineNumber()})")
                        return true
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallbackInternal: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = filePathCallbackInternal

                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }

                        try {
                            fileChooserLauncher.launch(intent)
                        } catch (e: Exception) {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                            return false
                        }
                        return true
                    }
                }

                loadUrl("file:///android_asset/index_md3.html")
            }
        },
        update = { webView ->
            val js = """
                document.documentElement.style.setProperty('--md-bg', '$mdBg');
                document.documentElement.style.setProperty('--md-surface', '$mdSurface');
                document.documentElement.style.setProperty('--md-surface-variant', '$mdSurfaceVariant');
                document.documentElement.style.setProperty('--md-on-surface', '$mdOnSurface');
                document.documentElement.style.setProperty('--md-on-surface-variant', '$mdOnSurfaceVariant');
                document.documentElement.style.setProperty('--md-outline', '$mdOutline');
                document.documentElement.style.setProperty('--md-primary', '$mdPrimary');
                document.documentElement.style.setProperty('--md-on-primary', '$mdOnPrimary');
                document.documentElement.style.setProperty('--md-error', '$mdError');
                document.documentElement.style.setProperty('--md-on-error', '$mdOnError');
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
}