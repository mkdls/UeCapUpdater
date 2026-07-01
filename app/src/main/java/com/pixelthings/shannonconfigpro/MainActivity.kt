package com.pixelthings.uecapupdater

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelthings.uecapupdater.ui.theme.ShannonConfigProTheme
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.pixelthings.uecapupdater.R

data class ConfigFile(val name: String, val sizeKb: Long)

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShannonConfigProTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RootStatusViewModel = viewModel()) {
    val rootStatus by viewModel.rootStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val currentLocaleTag = configuration.locales[0].toLanguageTag()
    val isEn = currentLocaleTag.contains("en", ignoreCase = true)

    var selectedFiles by remember { mutableStateOf<List<ConfigFile>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val filesDir = context.filesDir
        val existingFiles = filesDir.listFiles { _, name -> name.endsWith(".binarypb") }
            ?.map { ConfigFile(it.name, it.length() / 1024) }
            ?: emptyList()
        selectedFiles = existingFiles
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            errorMessage = null
            coroutineScope.launch {
                val (results, errorDbg) = copyMultipleFiles(context, uris, isEn)
                selectedFiles = results
                if (results.isEmpty()) {
                    errorMessage = if (isEn) "Read failed. Detailed reasons:\n$errorDbg" else "讀取失敗詳細原因：\n$errorDbg"
                }
                isProcessing = false
            }
        } else {
            isProcessing = false
        }
    }

    var currentTab by remember { mutableStateOf(0) }
    val detectedFilesState: MutableState<List<PbFileInfo>> = remember { mutableStateOf(emptyList()) }
    val moduleLogTextState: MutableState<String> = remember { mutableStateOf(if (isEn) "Waiting for modules...\n" else "等待選擇模組...\n") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(id = R.string.app_name)) },
                    actions = {
                        TextButton(onClick = {
                            val newLocaleTag = if (isEn) "zh-TW" else "en"
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLocaleTag))
                        }) {
                            Text(
                                text = if (isEn) "EN" else "中",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )

                // 🚀 關鍵修改：加入第三個標籤頁
                ScrollableTabRow(
                    selectedTabIndex = currentTab,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text(stringResource(id = R.string.tab_single_file), fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text(stringResource(id = R.string.tab_module_extract), fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        text = { Text(if (isEn) "Terminal" else "終端除錯", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
    ) { innerPadding ->
        when (currentTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RootStatusCard(rootStatus = rootStatus, isEn = isEn, onRetryClick = { viewModel.checkRootAccess() })

                    FileSelectionCard(
                        files = selectedFiles,
                        isProcessing = isProcessing,
                        errorMessage = errorMessage,
                        onSelectClick = { launcher.launch("*/*") }
                    )

                    var applySuccess by remember { mutableStateOf<Boolean?>(null) }
                    var shellLogs by remember { mutableStateOf<String?>(null) }

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
                                Box(modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
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

                    Button(
                        onClick = {
                            isApplying = true
                            shellLogs = null
                            applySuccess = null
                            coroutineScope.launch {
                                val (success, log) = applyModemConfig(context)
                                applySuccess = success
                                shellLogs = log
                                isApplying = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = selectedFiles.isNotEmpty() && !isApplying && !isResetting && rootStatus == RootStatus.Granted,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Injecting modem parameters...")
                        } else {
                            Text(stringResource(id = R.string.btn_apply_reload), style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isResetting = true
                            shellLogs = null
                            applySuccess = null
                            coroutineScope.launch {
                                val (success, log) = resetModemConfig(context)
                                applySuccess = success
                                shellLogs = log
                                isResetting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isApplying && !isResetting && rootStatus == RootStatus.Granted,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isResetting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Unmounting global configs...")
                        } else {
                            Text(stringResource(id = R.string.btn_restore_factory))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            1 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ModuleUpdaterScreen(detectedFilesState, moduleLogTextState)
                }
            }
            2 -> {
                // 🚀 關鍵修改：渲染第三頁終端除錯介面
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    DebugConsoleScreen(isEn = isEn, rootStatus = rootStatus)
                }
            }
        }
    }
}

// 🚀 全新元件：終端除錯介面
@Composable
fun DebugConsoleScreen(isEn: Boolean, rootStatus: RootStatus) {
    var command by remember { mutableStateOf("") }
    var outputLog by remember { mutableStateOf(if (isEn) "Waiting for command..." else "等待輸入指令...") }
    var isExecuting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 快捷指令晶片區
        // 快捷指令晶片區
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedFilterChip(
                selected = false,
                // 🚀 重點優化：抓取 UECAP/shamp 後，再用 grep -v 強制剔除含有包名的行數
                onClick = { command = "logcat -d -b all | grep -iE 'UECAP|shamp' | grep -v 'com.pixelthings.uecapupdater'" },
                label = { Text("Dump UECAP Logs") }
            )
            ElevatedFilterChip(
                selected = false,
                // 🚀 重點優化：檢查掛載時，一樣剔除掉包名的雜訊
                onClick = { command = "grep 'uecapconfig' /proc/mounts | grep -v 'com.pixelthings.uecapupdater'" },
                label = { Text("Check Mounts") }
            )
            // ... 後面保持不變
            ElevatedFilterChip(
                selected = false,
                onClick = { command = "ls -la /vendor/firmware/uecapconfig/" },
                label = { Text("List Vendor Files") }
            )
            ElevatedFilterChip(
                selected = false,
                onClick = { command = "logcat -b all -c" },
                label = { Text("Clear Logcat") }
            )
        }

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (isEn) "Shell Command (Root)" else "輸入 Shell 指令 (Root)") },
            singleLine = true,
            enabled = !isExecuting
        )

        Button(
            onClick = {
                if (command.isBlank()) return@Button
                isExecuting = true
                outputLog = if (isEn) "Executing...\n" else "執行中...\n"

                coroutineScope.launch(Dispatchers.IO) {
                    val result = Shell.cmd(command).exec()
                    val sb = StringBuilder()

                    if (result.out.isNotEmpty()) {
                        result.out.forEach { sb.append(it).append("\n") }
                    }
                    if (result.err.isNotEmpty()) {
                        sb.append("\n[STDERR]\n")
                        result.err.forEach { sb.append(it).append("\n") }
                    }

                    withContext(Dispatchers.Main) {
                        outputLog = sb.toString().ifEmpty { if (isEn) "[Process completed with no output]" else "[執行完畢，無輸出內容]" }
                        isExecuting = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = command.isNotBlank() && !isExecuting && rootStatus == RootStatus.Granted,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            if (isExecuting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEn) "Running..." else "執行中...")
            } else {
                Text(if (isEn) "Execute Command" else "執行指令")
            }
        }

        // 終端機黑框輸出區
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = outputLog,
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

// ---------------- 以下保留原本的元件與腳本邏輯 ----------------

@Composable
private fun RootStatusCard(rootStatus: RootStatus, isEn: Boolean, onRetryClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isEn) "Root Authorization Status" else "Root 授權狀態",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            when (rootStatus) {
                RootStatus.Checking -> {
                    CircularProgressIndicator()
                    Text(
                        text = if (isEn) "Checking Root access…" else "正在檢查 Root 權限…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RootStatus.Granted -> {
                    Text(
                        text = if (isEn) "Root Access Granted" else "Root 權限已獲取",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                RootStatus.Denied -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isEn) "Root Access Denied" else "未取得 Root 權限",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ElevatedButton(
                            onClick = onRetryClick,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                text = if (isEn) "Retry Granting Root" else "重新嘗試獲取 Root 權限",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSelectionCard(files: List<ConfigFile>, isProcessing: Boolean, errorMessage: String?, onSelectClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text(stringResource(id = R.string.card_title_baseband), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Text("Importing files...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (files.isNotEmpty()) {
                Text(text = "✅ ${files.size} files ready", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(files) { file -> Text("• ${file.name} (${file.sizeKb} KB)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } else {
                Text(stringResource(id = R.string.msg_no_file_selected), color = MaterialTheme.colorScheme.error)
            }

            if (!errorMessage.isNullOrEmpty()) { Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSelectClick, enabled = !isProcessing) { Text(if (files.isNotEmpty()) stringResource(id = R.string.btn_reselect_files) else stringResource(id = R.string.btn_select_files)) }
        }
    }
}

suspend fun copyMultipleFiles(context: Context, uris: List<Uri>, isEn: Boolean): Pair<List<ConfigFile>, String> = withContext(Dispatchers.IO) {
    val results = mutableListOf<ConfigFile>()
    val filesDir = context.filesDir
    val errorLogs = java.lang.StringBuilder()

    try { com.topjohnwu.superuser.Shell.cmd("rm -f ${filesDir.absolutePath}/*.binarypb").exec() }
    catch (e: Exception) {
        errorLogs.append(if (isEn) "Failed to clean old files: " else "清理舊檔案失敗: ").append("${e.message}\n")
    }

    for (uri in uris) {
        try {
            var displayName = "unknown_config_${System.currentTimeMillis()}.binarypb"
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) cursor.getString(index)?.let { displayName = it }
                    }
                }
            }
            if (!displayName.endsWith(".binarypb")) displayName += ".binarypb"

            val targetFile = File(filesDir, displayName)
            val inputStream = context.contentResolver.openInputStream(uri)

            if (inputStream == null) {
                errorLogs.append(if (isEn) "[$displayName] Cannot open InputStream\n" else "[$displayName] 無法開啟 InputStream\n")
                continue
            }

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                results.add(ConfigFile(displayName, targetFile.length() / 1024))
            } else {
                errorLogs.append(if (isEn) "[$displayName] File write anomaly\n" else "[$displayName] 檔案寫入異常\n")
            }

        } catch (e: Exception) {
            errorLogs.append(if (isEn) "Exception occurred during processing: " else "處理發生例外錯誤: ").append("${e.message}\n")
        }
    }
    return@withContext Pair(results, errorLogs.toString())
}

// 1. 套用配置核心腳本（訊號完全恢復監聽版）
suspend fun applyModemConfig(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val privateDir = context.filesDir.absolutePath

    val script = """
        echo "================================================="
        echo "          ✨ Loading Custom UECAP Configs ✨      "
        echo "================================================="
        
        OTA_DIR="/data/vendor/radio/ota_uecap"
        VENDOR_DIR="/vendor/firmware/uecapconfig"
        
        mkdir -p "${'$'}{OTA_DIR}"
        
        echo "[INFO] Cleaning previous active mounts in PID 1..."
        cat << 'EOF' > /data/local/tmp/unmount_uecap.sh
        #!/system/bin/sh
        for m in ${'$'}(grep "uecapconfig" /proc/mounts | awk '{print ${'$'}2}'); do
            umount -l "${'$'}m" 2>/dev/null
        done
        for f in /vendor/firmware/uecapconfig/*.binarypb; do
            umount -l "${'$'}f" 2>/dev/null
        done
        EOF
        chmod 755 /data/local/tmp/unmount_uecap.sh
        nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh
        rm -f /data/local/tmp/unmount_uecap.sh
        
        rm -rf "${'$'}{OTA_DIR}"/* 2>/dev/null
        rm -rf /data/vendor/radio/modem_temp_file/* 2>/dev/null

        # 開始重新搬移與全域綁定掛載
        for FILE in "$privateDir"/*.binarypb; do
            if [ -f "${'$'}FILE" ]; then
                FILENAME=${'$'}(basename "${'$'}FILE")
                TARGET_OTA="${'$'}{OTA_DIR}/${'$'}{FILENAME}"
                TARGET_VENDOR="${'$'}{VENDOR_DIR}/${'$'}{FILENAME}"
                
                cp "${'$'}FILE" "${'$'}TARGET_OTA"
                chown radio:radio "${'$'}TARGET_OTA" 2>/dev/null
                chcon u:object_r:vendor_file:s0 "${'$'}TARGET_OTA" 2>/dev/null
                chmod 644 "${'$'}TARGET_OTA"
                
                if [ -f "${'$'}TARGET_VENDOR" ]; then
                    nsenter -t 1 -m -- mount -o bind "${'$'}TARGET_OTA" "${'$'}TARGET_VENDOR"
                    if [ ${'$'}? -eq 0 ]; then
                        echo "  [SUCCESS] 👉 ${'$'}FILENAME bound globally"
                    else
                        echo "  [FAILED] ❌ ${'$'}FILENAME bind failed"
                    fi
                fi
            fi
        done

        echo "-------------------------------------------------"
        echo "[INFO] Resetting Logcat buffer..."
        logcat -b all -c
        sleep 1
        
        echo "[INFO] Force killing modem daemons (Raw Boot)..."
        pkill -9 -f rild 2>/dev/null
        pkill -9 -f shamp 2>/dev/null
        pkill -9 -f vcd 2>/dev/null
        pkill -9 -f modem 2>/dev/null
        
        # 🚀 修正 1：給基帶一點時間啟動初次處理
        echo "[INFO] Awaiting initial baseband parser process..."
        sleep 4
        
        # 🚀 修正 2：在開啟網路數據後，啟動智慧輪詢，直到日誌噴出網路完全復活狀態
        echo "[INFO] Reviving network and waiting for hardware registration..."
        svc data disable
        sleep 1
        svc data enable
        
        echo "[INFO] Monitoring signal sync and data lines connection status..."
        COUNTER=0
        while [ ${'$'}COUNTER -lt 15 ]; do
            sleep 1
            # 🔍 終極追蹤特徵：只有當手機真正抓到網路、跟基地台對上線並寫入快取（Flush Registry）時，此行才會在有訊號時大批湧現
            if logcat -d -b all | grep -i "shamp" | grep -q "Flush Registry to Flash"; then
                echo "[INFO] Signal lock and registration confirmed! Stopping poll."
                break
            fi
            COUNTER=${'$'}((COUNTER + 1))
        done
        
        # 給日誌緩衝區 1.5 秒做最後的刷寫沉澱
        sleep 1.5
        
        echo " "
        echo "================================================="
        echo "=== 🔧 Complete UECAP/shamp Boot Logs ==="
        echo "================================================="
        logcat -d -b all | grep -iE "UECAP|shamp" | grep -v "com.pixelthings.uecapupdater"
        echo "================================================="
        echo "=== Done ==="
        
    """.trimIndent()

    val result = com.topjohnwu.superuser.Shell.cmd(script).exec()
    val bootScriptSuccess = ModuleParser.createMagiskModule()

    val logOutput = buildString {
        if (result.out.isNotEmpty()) { result.out.forEach { append(it).append("\n") } }
        if (result.err.isNotEmpty()) { append("\n--- ERROR LOGS ---\n"); result.err.forEach { append(it).append("\n") } }
        if (bootScriptSuccess) append("\n👉 Magisk boot mount module generated successfully!\n")
    }
    return@withContext Pair(result.isSuccess, logOutput)
}

// 2. 恢復原廠核心腳本（訊號完全恢復監聽版）
suspend fun resetModemConfig(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val script = """
        echo "================================================="
        echo "          ✨ Restoring Factory Baseband ✨       "
        echo "================================================="
        
        echo "[INFO] Scanning and forcefully unmounting active overrides in PID 1..."
        cat << 'EOF' > /data/local/tmp/unmount_uecap.sh
        #!/system/bin/sh
        for m in ${'$'}(grep "uecapconfig" /proc/mounts | awk '{print ${'$'}2}'); do
            umount -l "${'$'}m" 2>/dev/null
            echo "  [UNBOUND GLOBAL] 🔄 ${'$'}m successfully unmounted"
        done
        for f in /vendor/firmware/uecapconfig/*.binarypb; do
            umount -l "${'$'}f" 2>/dev/null
        done
        EOF
        chmod 755 /data/local/tmp/unmount_uecap.sh
        nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh
        rm -f /data/local/tmp/unmount_uecap.sh

        echo "[INFO] Cleaning cache directories..."
        rm -rf /data/vendor/radio/ota_uecap/* 2>/dev/null
        rm -rf /data/vendor/radio/modem_temp_file/* 2>/dev/null
        
        echo "[INFO] Resetting Logcat buffer..."
        logcat -b all -c
        sleep 1
        
        echo "[INFO] Force killing modem daemons (Raw Boot)..."
        pkill -9 -f rild 2>/dev/null
        pkill -9 -f shamp 2>/dev/null
        pkill -9 -f vcd 2>/dev/null
        pkill -9 -f modem 2>/dev/null
        
        sleep 4
        
        echo "[INFO] Reviving factory network and waiting for carrier registration..."
        svc data disable
        sleep 1
        svc data enable
        
        COUNTER=0
        while [ ${'$'}COUNTER -lt 15 ]; do
            sleep 1
            if logcat -d -b all | grep -i "shamp" | grep -q "Flush Registry to Flash"; then
                echo "[INFO] Factory signal lock confirmed! Stopping poll."
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
        echo "=== Factory Reset Complete ==="
    """.trimIndent()

    val result = com.topjohnwu.superuser.Shell.cmd(script).exec()
    ModuleParser.removeMagiskModule()

    val logOutput = buildString {
        if (result.out.isNotEmpty()) { result.out.forEach { append(it).append("\n") } }
        if (result.err.isNotEmpty()) { append("\n--- ERROR LOGS ---\n"); result.err.forEach { append(it).append("\n") } }
        append("\n🗑️ Magisk boot mount module removed successfully.\n")
    }
    return@withContext Pair(result.isSuccess, logOutput)
}