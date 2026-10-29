package com.pixelthings.uecapupdater

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.MutableState

@Composable
fun ModuleUpdaterScreen(
    detectedFilesState: MutableState<List<PbFileInfo>>,
    moduleLogTextState: MutableState<String>,
    isEn: Boolean // 🚀 完美接軌首頁傳進來的響應式語系變數
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 🚀 關鍵修復 1：刪除了原本在這裡會強行覆蓋狀態的 LocalConfiguration 舊 isEn 定義！

    var detectedFiles by detectedFilesState
    var logText by moduleLogTextState // 修正狀態綁定變數名

    var isApplying by remember { mutableStateOf(false) }
    var applySuccess by remember { mutableStateOf<Boolean?>(null) }
    var shellLogs by remember { mutableStateOf<String?>(null) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            logText += if (isEn) "Parsing ${uris.size} modules...\n" else "正在解析 ${uris.size} 個模組...\n"
            coroutineScope.launch(Dispatchers.IO) {
                val allFiles = mutableListOf<PbFileInfo>()
                for (uri in uris) {
                    allFiles.addAll(ModuleParser.parseZipModule(context, uri))
                }

                withContext(Dispatchers.Main) {
                    detectedFiles = allFiles
                    if (allFiles.isEmpty()) {
                        logText += if (isEn) "❌ No .binarypb files found in selected modules\n" else "❌ 選中的模組中未找到任何 .binarypb 檔案\n"
                    } else {
                        logText += if (isEn) "✅ Successfully aggregated ${allFiles.size} config files\n" else "✅ 成功匯總！共掃描到 ${allFiles.size} 個配置檔案\n"
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = { zipPickerLauncher.launch("application/zip") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isApplying
        ) {
            // 🚀 關鍵修復 2：拔除殘留 stringResource，全面綁定動態語系
            Text(if (isEn) "Select Magisk/KernelSU Modules (.zip)" else "選擇多個 Magisk/KernelSU 模組 (.zip)")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isEn) "Detected Configuration Files" else "偵測到的配置檔案清單",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(detectedFiles) { fileInfo ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = fileInfo.fileName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${if (isEn) "Source Path" else "來源路徑"}: ${fileInfo.insidePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (shellLogs != null) {
            val scrollState = rememberScrollState()
            LaunchedEffect(shellLogs) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) {
                            if (applySuccess == null) "⏳ Running..." else if (applySuccess == true) "✅ Execution Complete" else "❌ Execution Failed"
                        } else {
                            if (applySuccess == null) "⏳ 執行中..." else if (applySuccess == true) "✅ 執行完成" else "❌ 執行失敗"
                        },
                        color = if (applySuccess == null) Color(0xFFFFC107) else if (applySuccess == true) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 🚀 視覺同步：內層控制台也改用 M3 最低容器色，告別死黑
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                    ) {
                        Box(modifier = Modifier.heightIn(max = 250.dp).verticalScroll(scrollState).padding(8.dp)) {
                            SelectionContainer {
                                Text(
                                    text = shellLogs!!,
                                    color = Color(0xFF00FF00),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                isApplying = true
                shellLogs = if (isEn) "=== Module Extract & Global Mount ===\n" else "=== 模組提取與全域掛載 ===\n"
                applySuccess = null
                logText += if (isEn) "🚀 Batch applying ${detectedFiles.size} configurations...\n" else "🚀 開始批量套用 ${detectedFiles.size} 個配置檔...\n"

                val selectedConfigs = detectedFiles.toList()
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val success = applyModuleConfigsRealTime(context, selectedConfigs, isEn) { line ->
                            shellLogs = ((shellLogs ?: "") + line + "\n").takeLast(100_000)
                        }
                        withContext(Dispatchers.Main) {
                            applySuccess = success
                            logText += if (success) {
                                if (isEn) "Batch apply completed.\n" else "批量套用完成。\n"
                            } else {
                                if (isEn) "Batch apply failed. See stage logs.\n" else "批量套用失敗，請查看階段日誌。\n"
                            }
                        }
                    } finally {
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                            isApplying = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = detectedFiles.isNotEmpty() && !isApplying,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isApplying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (isEn) "Injecting & restarting radio..." else "正在注入並重啟射頻...")
            } else {
                Text(if (isEn) "Batch Apply All Imported Configs" else "一鍵套用所有匯入的配置", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

data class PbFileInfo(
    val fileName: String,
    val insidePath: String,
    val zipUri: Uri,
    val entryName: String
)