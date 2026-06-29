package com.pixelthings.shannonconfigpro

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
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.runtime.MutableState // 確保最上方有這個 import

@Composable
// 🛠️ 修正：在括號裡接收 MainActivity 傳進來的狀態
fun ModuleUpdaterScreen(
    detectedFilesState: MutableState<List<PbFileInfo>>,
    logTextState: MutableState<String>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 🛠️ 修正：不再用 remember 自己記，而是直接綁定外部傳進來的狀態
    var detectedFiles by detectedFilesState
    var logText by logTextState

    var isApplying by remember { mutableStateOf(false) }

    // 🛠️ 新增：用來控制終端機黑框面板的變數
    var applySuccess by remember { mutableStateOf<Boolean?>(null) }
    var shellLogs by remember { mutableStateOf<String?>(null) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            logText += "正在解析 ${uris.size} 個模組...\n"
            coroutineScope.launch(Dispatchers.IO) {
                val allFiles = mutableListOf<PbFileInfo>()
                for (uri in uris) {
                    allFiles.addAll(ModuleParser.parseZipModule(context, uri))
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
            modifier = Modifier.fillMaxWidth(),
            enabled = !isApplying
        ) {
            Text("選擇多個 Magisk/KernelSU 模組 (.zip)")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("偵測到的配置檔案清單：", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(detectedFiles) { fileInfo ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = fileInfo.fileName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "來源路徑: ${fileInfo.insidePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🛠️ 新增：帥氣的終端機日誌黑框 (與單檔模式完全相同)
        if (shellLogs != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (applySuccess == true) "✅ 執行完成" else "❌ 執行異常",
                        color = if (applySuccess == true) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                isApplying = true
                shellLogs = null
                applySuccess = null
                logText += "🚀 開始批量套用 ${detectedFiles.size} 個配置檔...\n"

                coroutineScope.launch(Dispatchers.IO) {
                    var successCount = 0
                    val aggregatedLogs = StringBuilder()
                    aggregatedLogs.append("=== 模組配置提取與掛載 ===\n")

                    for (fileInfo in detectedFiles) {
                        val (success, fileLog) = ModuleParser.applyPbFile(context, fileInfo)
                        aggregatedLogs.append(fileLog)

                        withContext(Dispatchers.Main) {
                            if (success) {
                                successCount++
                                logText += "✅ [${fileInfo.fileName}] 掛載成功\n"
                            } else {
                                logText += "❌ [${fileInfo.fileName}] 掛載失敗\n"
                            }
                        }
                    }

                    // 🛠️ 優化版基帶重啟與日誌全面監控腳本
                    val restartScript = """
                        echo "-------------------------------------------------"
                        echo "[INFO] 清空舊日誌並安全重啟基帶與 MDS 進程..."
                        logcat -b all -c
                        
                        am force-stop com.google.android.ModemDiagnosticSystem 2>/dev/null
                        pkill -9 -f rild 2>/dev/null
                        pkill -9 -f shamp 2>/dev/null
                        pkill -9 -f vcd 2>/dev/null
                        pkill -9 -f modem 2>/dev/null
                        
                        echo "[INFO] 等待硬體重載設定與數據線路建立 (15秒)..."
                        sleep 15
                        
                        echo " "
                        echo "================================================="
                        echo "=== 🔧 底層 UECAP 原始載入日誌 (供進階除錯) ==="
                        echo "================================================="
                        # 🛠️ 修正：擴大篩選範圍至 config/Modem，並增加截取至 100 行，確保讀檔日誌被捕獲
                        logcat -d -b all | grep -i "UECAP" | tail -n 100
                    """.trimIndent()

                    val restartResult = Shell.cmd(restartScript).exec()
                    if (restartResult.out.isNotEmpty()) { restartResult.out.forEach { aggregatedLogs.append(it).append("\n") } }
                    if (restartResult.err.isNotEmpty()) { restartResult.err.forEach { aggregatedLogs.append(it).append("\n") } }

                    // ... 前面的 restartResult 保持不變 ...

                    // 🚀 修正：呼叫動態生成 Magisk 模組
                    val bootScriptSuccess = ModuleParser.createMagiskModule()
                    if (bootScriptSuccess) {
                        aggregatedLogs.append("👉 Magisk 底層掛載模組已自動生成！\n")
                    } else {
                        aggregatedLogs.append("⚠️ Magisk 模組生成失敗，請確認 Root 權限。\n")
                    }

                    withContext(Dispatchers.Main) {
                        shellLogs = aggregatedLogs.toString()
                        applySuccess = (successCount == detectedFiles.size)
                        logText += "🎉 批量執行完畢！共成功套用 $successCount 個檔案。\n"
                        isApplying = false
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
                Text("正在寫入並重啟基帶 (約 15 秒)...")
            } else {
                Text("一鍵套用所有匯入的配置", style = MaterialTheme.typography.titleMedium)
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