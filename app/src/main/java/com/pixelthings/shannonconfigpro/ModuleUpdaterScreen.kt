package com.pixelthings.shannonconfigpro

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModuleUpdaterScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var detectedFiles by remember { mutableStateOf<List<PbFileInfo>>(emptyList()) }
    var logText by remember { mutableStateOf("等待選擇模組...\n") }

    // 🛠️ 關鍵升級：從 GetContent() 升級為 GetMultipleContents() 支持多選
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            logText += "正在解析 ${uris.size} 個模組...\n"
            coroutineScope.launch(Dispatchers.IO) {
                val allFiles = mutableListOf<PbFileInfo>()

                // 迴圈讀取每一個選中的 zip 檔案
                for (uri in uris) {
                    val filesFromZip = ModuleParser.parseZipModule(context, uri)
                    allFiles.addAll(filesFromZip)
                }

                withContext(Dispatchers.Main) {
                    detectedFiles = allFiles
                    if (allFiles.isEmpty()) {
                        logText += "❌ 選中的模組中未找到任何 .binarypb 檔案\n"
                    } else {
                        logText += "✅ 成功匯總！共掃描到 ${allFiles.size} 個配置檔案\n"
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = { zipPickerLauncher.launch("application/zip") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("選擇多個 Magisk/KernelSU 模組 (.zip)")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("偵測到的配置檔案清單：", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(detectedFiles) { fileInfo ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = {
                        logText += "🚀 開始套用: ${fileInfo.fileName}\n"
                        coroutineScope.launch(Dispatchers.IO) {
                            val success = ModuleParser.applyPbFile(context, fileInfo)
                            withContext(Dispatchers.Main) {
                                logText += if (success) "🔔 [${fileInfo.fileName}] 套用成功！請重啟手機。\n" else "❌ 套用失敗，請檢查 Root 權限。\n"
                            }
                        }
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = fileInfo.fileName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "來源模組路徑: ${fileInfo.insidePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("執行狀態：", style = MaterialTheme.typography.titleSmall)
        Card(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Text(text = logText, modifier = Modifier.padding(8.dp).fillMaxSize(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

data class PbFileInfo(
    val fileName: String,
    val insidePath: String,
    val zipUri: Uri,
    val entryName: String
)