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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.MutableState

@Composable
fun ModuleUpdaterScreen(
    detectedFilesState: MutableState<List<PbFileInfo>>,
    logTextState: MutableState<String>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isEn = configuration.locales[0].toLanguageTag().contains("en", ignoreCase = true)

    var detectedFiles by detectedFilesState
    var logText by logTextState

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
            Text(stringResource(id = R.string.btn_select_modules))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(id = R.string.title_detected_list), style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(detectedFiles) { fileInfo ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (applySuccess == true) "✅ Execution Complete" else "❌ Execution Failed",
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
                logText += if (isEn) "🚀 Batch applying ${detectedFiles.size} configurations...\n" else "🚀 開始批量套用 ${detectedFiles.size} 個配置檔...\n"

                coroutineScope.launch(Dispatchers.IO) {
                    var successCount = 0
                    val aggregatedLogs = StringBuilder()
                    aggregatedLogs.append("=== Module Extract & Global Mount ===\n")

                    // 🚀 1. 批量掛載前，利用暫存腳本徹底清空 PID 1 舊的殘留
                    val d = "$"
                    val cleanPrevMountsScript = """
                        cat << 'EOF' > /data/local/tmp/unmount_uecap.sh
                        #!/system/bin/sh
                        for m in ${d}(grep "uecapconfig" /proc/mounts | awk '{print ${d}2}'); do
                            umount -l "${d}m" 2>/dev/null
                        done
                        for f in /vendor/firmware/uecapconfig/*.binarypb; do
                            umount -l "${d}f" 2>/dev/null
                        done
                        EOF
                        chmod 755 /data/local/tmp/unmount_uecap.sh
                        nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh
                        rm -f /data/local/tmp/unmount_uecap.sh
                    """.trimIndent()
                    Shell.cmd(cleanPrevMountsScript).exec()

                    // 2. 依次提取模組內的檔案並掛載
                    for (fileInfo in detectedFiles) {
                        val (success, fileLog) = ModuleParser.applyPbFile(context, fileInfo)
                        aggregatedLogs.append(fileLog)

                        withContext(Dispatchers.Main) {
                            if (success) {
                                successCount++
                                logText += if (isEn) "✅ [${fileInfo.fileName}] mounted\n" else "✅ [${fileInfo.fileName}] 掛載成功\n"
                            } else {
                                logText += if (isEn) "❌ [${fileInfo.fileName}] failed\n" else "❌ [${fileInfo.fileName}] 掛載失敗\n"
                            }
                        }
                    }

                    // 🚀 3. 同步升級為官方與防斷訊混合重載腳本
                    val restartScript = """
                        echo "-------------------------------------------------"
                        echo "[INFO] Syncing Android framework to safe state..."
                        settings put global airplane_mode_on 1
                        am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null 2>&1
                        sleep 2
                        
                        echo "[INFO] Clearing logcat buffers..."
                        logcat -b all -c
                        
                        echo "[INFO] Killing modem processes to clear RAM cache..."
                        pkill -9 -f rild 2>/dev/null
                        pkill -9 -f shamp 2>/dev/null
                        pkill -9 -f vcd 2>/dev/null
                        pkill -9 -f modem 2>/dev/null
                        sleep 3
                        
                        echo "[INFO] Restoring radio state and reviving network..."
                        settings put global airplane_mode_on 0
                        am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null 2>&1
                        
                        echo "[INFO] Waiting for hardware and data lines (12s)..."
                        sleep 12
                        
                        echo " "
                        echo "================================================="
                        echo "=== 🔧 Raw UECAP Loading Logs (Advanced Debugging) ==="
                        echo "================================================="
                        logcat -d -b all | grep -i "UECAP" | tail -n 100
                        echo "================================================="
                        echo "=== Done ==="
                    """.trimIndent()

                    val restartResult = Shell.cmd(restartScript).exec()
                    if (restartResult.out.isNotEmpty()) { restartResult.out.forEach { aggregatedLogs.append(it).append("\n") } }
                    if (restartResult.err.isNotEmpty()) { restartResult.err.forEach { aggregatedLogs.append(it).append("\n") } }

                    val bootScriptSuccess = ModuleParser.createMagiskModule()
                    if (bootScriptSuccess) {
                        aggregatedLogs.append("\n👉 Magisk boot mount module generated successfully!\n")
                    } else {
                        aggregatedLogs.append("\n⚠️ Magisk module creation failed. Check root access.\n")
                    }

                    withContext(Dispatchers.Main) {
                        shellLogs = aggregatedLogs.toString()
                        applySuccess = (successCount == detectedFiles.size)
                        logText += if (isEn) "🎉 Batch process done! $successCount files applied.\n" else "🎉 批量執行完畢！共成功套用 $successCount 個檔案。\n"
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
                Text("Injecting & restarting radio...")
            } else {
                Text(stringResource(id = R.string.btn_apply_all_modules), style = MaterialTheme.typography.titleMedium)
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