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
import java.io.File

@Composable
fun ModuleUpdaterScreen() {
    val context = LocalContext.current
    var detectedFiles by remember { mutableStateOf<List<PbFileInfo>>(emptyList()) }
    var logText by remember { mutableStateOf("等待選擇模組...\n") }

    // 檔案選擇器：允許選擇 zip 檔案
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            logText += "正在解析模組...\n"
            // 在背景或直接解析 Zip 裡面的 pb 檔案
            detectedFiles = ModuleParser.parseZipModule(context, it)
            if (detectedFiles.isEmpty()) {
                logText += "❌ 未在模組中找到任何 .binarypb 檔案\n"
            } else {
                logText += "✅ 成功掃描到 ${detectedFiles.size} 個配置檔案\n"
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

        // 顯示掃描出來的 binarypb 列表
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(detectedFiles) { fileInfo ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = {
                        logText += "🚀 開始套用: ${fileInfo.fileName}\n"
                        val success = ModuleParser.applyPbFile(context, fileInfo)
                        logText += if (success) "🔔 套用成功！請重啟手機。\n" else "❌ 套用失敗，請檢查 Root 權限。\n"
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = fileInfo.fileName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "路徑: ${fileInfo.insidePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 偵錯日誌顯示
        Text("執行狀態：", style = MaterialTheme.typography.titleSmall)
        Card(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Text(text = logText, modifier = Modifier.padding(8.dp).fillMaxSize(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// 用來傳遞檔案資訊的資料結構
data class PbFileInfo(
    val fileName: String,
    val insidePath: String,
    val zipUri: Uri,
    val entryName: String
)