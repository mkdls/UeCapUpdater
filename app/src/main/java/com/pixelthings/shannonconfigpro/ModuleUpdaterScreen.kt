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

                    val d = "$"
                    val cleanPrevMountsScript = """
                        cat << 'EOF' > /data/local/tmp/unmount_uecap.sh
                        #!/system/bin/sh
                        for m in ${'建'} (grep "uecapconfig" /proc/mounts | awk '{print ${'$'}2}'); do
                            umount -l "${'$'}m" 2>/dev/null
                        done
                        for f in /vendor/firmware/uecapconfig/*.binarypb; do
                            umount -l "${'$'}f" 2>/dev/null
                        done
                        EOF
                        chmod 755 /data/local/tmp/unmount_uecap.sh
                        nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh
                        rm -f /data/local/tmp/unmount_uecap.sh
                        
                        rm -rf /data/vendor/radio/modem_temp_file/* 2>/dev/null
                    """.trimIndent()

                    val finalCleanScript = cleanPrevMountsScript.replace("建", "")
                    Shell.cmd(finalCleanScript).exec()

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

                    // 🚀 核心大對齊：批量模式完美注入「有訊號 Flush 機制再中斷輪詢」的終極時間差演算法
                    val restartScript = """
                        echo "-------------------------------------------------"
                        echo "[INFO] Resetting Logcat buffer..."
                        logcat -b all -c
                        sleep 1
                        
                        echo "[INFO] Force killing modem daemons (Raw Boot)..."
                        pkill -9 -f rild 2>/dev/null
                        pkill -9 -f shamp 2>/dev/null
                        pkill -9 -f vcd 2>/dev/null
                        pkill -9 -f modem 2>/dev/null
                        
                        echo "[INFO] Awaiting initial baseband parser process..."
                        sleep 4
                        
                        echo "[INFO] Reviving network and waiting for hardware registration..."
                        svc data disable
                        sleep 1
                        svc data enable
                        
                        echo "[INFO] Monitoring signal sync and data lines connection status..."
                        COUNTER=0
                        while [ ${'$'}COUNTER -lt 15 ]; do
                            sleep 1
                            if logcat -d -b all | grep -i "shamp" | grep -q "Flush Registry to Flash"; then
                                echo "[INFO] Signal lock and registration confirmed! Stopping poll."
                                break
                            fi
                            COUNTER=${'$'}((COUNTER + 1))
                        done
                        
                        sleep 1.5
                        
                        echo " "
                        echo "================================================="
                        echo "=== 🔧 Complete UECAP/shamp Boot Logs ==="
                        echo "================================================="
                        logcat -d -b all | grep -iE "UECAP|shamp" | grep -v "com.pixelthings.uecapupdater"
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