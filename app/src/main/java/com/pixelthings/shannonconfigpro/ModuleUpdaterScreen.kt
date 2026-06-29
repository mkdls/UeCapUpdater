package com.pixelthings.shannonconfigpro // 確保這裡跟你 MainActivity 第一行一樣

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
    val coroutineScope = rememberCoroutineScope() // 🛠️ 召喚協程，讓耗時工作去背景跑

    var detectedFiles by remember { mutableStateOf<List<PbFileInfo>>(emptyList()) }
    var logText by remember { mutableStateOf("等待選擇模組...\n") }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            logText += "正在解析模組...\n"
            // 🛠️ 把讀取 Zip 的工作丟到背景 (IO)
            coroutineScope.launch(Dispatchers.IO) {
                val files = ModuleParser.parseZipModule(context, it)
                // 🛠️ 讀完之後，切回主畫面更新文字
                withContext(Dispatchers.Main) {
                    detectedFiles = files
                    if (files.isEmpty()) {
                        logText += "❌ 未在模組中找到任何 .binarypb 檔案\n"
                    } else {
                        logText += "✅ 成功掃描到 ${files.size} 個配置檔案\n"
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
            Text("選擇 Magisk/KernelSU 模組 (.zip)")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("偵測到的配置檔案：", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(detectedFiles) { fileInfo ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = {
                        logText += "🚀 開始套用: ${fileInfo.fileName}\n"
                        // 🛠️ 把執行 Root 替換的工作丟到背景 (IO)
                        coroutineScope.launch(Dispatchers.IO) {
                            val success = ModuleParser.applyPbFile(context, fileInfo)
                            withContext(Dispatchers.Main) {
                                logText += if (success) "🔔 套用成功！請重啟手機。\n" else "❌ 套用失敗，請檢查 Root 權限。\n"
                            }
                        }
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = fileInfo.fileName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "內部分支: ${fileInfo.insidePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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