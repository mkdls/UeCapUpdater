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
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.isActive

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
                        text = if (applySuccess == null) "⏳ Running..." else if (applySuccess == true) "✅ Execution Complete" else "❌ Execution Failed",
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
                shellLogs = "=== Module Extract & Global Mount ===\n"
                applySuccess = null
                logText += if (isEn) "🚀 Batch applying ${detectedFiles.size} configurations...\n" else "🚀 開始批量套用 ${detectedFiles.size} 個配置檔...\n"

                coroutineScope.launch(Dispatchers.IO) {
                    var successCount = 0
                    val publish = { line: String -> coroutineScope.launch(Dispatchers.Main) { shellLogs = (shellLogs ?: "") + line + "\n" } }

                    publish("[INFO] Resetting Logcat buffer...")
                    Shell.cmd("logcat -b all -c").exec()
                    delay(1500)

                    publish("[INFO] Starting real-time Logcat stream daemon...")
                    val logcatProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -v time -b all"))
                    val logcatJob = launch(Dispatchers.IO) {
                        try {
                            logcatProcess.inputStream.bufferedReader().use { reader ->
                                var line: String? = null
                                while (coroutineContext.isActive && reader.readLine().also { line = it } != null) {
                                    val currentLine = line ?: continue
                                    if ((currentLine.contains("UECAP", true) || currentLine.contains("shamp", true)) &&
                                        !currentLine.contains("com.pixelthings.uecapupdater")) {
                                        publish("[Logcat] $currentLine")
                                        delay(1)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            publish("[INFO] Logcat stream disconnected gracefully.")
                        }
                    }

                    publish("[INFO] Requesting Root to scan & unmount active overrides in PID 1...")
                    publish("[INFO] (This may take up to 5 seconds depending on Root overhead...)")
                    val cleanPrevMountsScript = """
                        for m in ${'$'}(grep "uecapconfig" /proc/mounts | awk '{print ${'$'}2}'); do
                            umount -l "${'$'}m" 2>/dev/null
                        done
                        for f in /vendor/firmware/uecapconfig/*.binarypb; do
                            umount -l "${'$'}f" 2>/dev/null
                        done
                    """.trimIndent()
                    Shell.cmd("cat << 'EOF' > /data/local/tmp/unmount_uecap.sh\n$cleanPrevMountsScript\nEOF").exec()
                    Shell.cmd("chmod 755 /data/local/tmp/unmount_uecap.sh && nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh > /dev/null 2>&1 & sleep 1 && rm -f /data/local/tmp/unmount_uecap.sh").exec()
                    Shell.cmd("rm -rf /data/vendor/radio/modem_temp_file/*").exec()

                    for (fileInfo in detectedFiles) {
                        val (success, fileLog) = ModuleParser.applyPbFile(context, fileInfo)
                        fileLog.lines().filter { it.isNotBlank() }.forEach { publish("[INFO] $it") }

                        withContext(Dispatchers.Main) {
                            if (success) {
                                successCount++
                                logText += if (isEn) "✅ [${fileInfo.fileName}] mounted\n" else "✅ [${fileInfo.fileName}] 掛載成功\n"
                            } else {
                                logText += if (isEn) "❌ [${fileInfo.fileName}] failed\n" else "❌ [${fileInfo.fileName}] 掛載失敗\n"
                            }
                        }
                    }

                    publish("-------------------------------------------------")
                    publish("[INFO] Force killing modem daemons (Raw Boot)...")
                    Shell.cmd("pkill -9 -f rild; pkill -9 -f shamp; pkill -9 -f vcd; pkill -9 -f modem").exec()

                    publish("[INFO] Awaiting initial baseband parser process...")
                    delay(4000)

                    publish("[INFO] Reviving network and waiting for hardware registration...")
                    Shell.cmd("svc data disable").exec()
                    delay(1000)
                    Shell.cmd("svc data enable").exec()

                    // 🚀 呼叫智慧信號守護進程，不到黃河心不死
                    val isLocked = awaitSignalLock { line -> publish(line) }
                    if (!isLocked) {
                        publish("[ERROR] Baseband might be unstable. Please check Terminal for deep errors.")
                    }
                    publish("=================================================")
                    publish("=== Done ===")

                    logcatProcess.destroy()
                    logcatJob.cancel()

                    val bootScriptSuccess = ModuleParser.createMagiskModule()
                    if (bootScriptSuccess) {
                        publish("[INFO] 👉 Magisk boot mount module generated successfully!")
                    }

                    withContext(Dispatchers.Main) {
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