package com.pixelthings.shannonconfigpro

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelthings.shannonconfigpro.ui.theme.ShannonConfigProTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class ConfigFile(val name: String, val sizeKb: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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

    var selectedFiles by remember { mutableStateOf<List<ConfigFile>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) } // 🛠️ 獨立的重置狀態
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
                val (results, errorDbg) = copyMultipleFiles(context, uris)
                selectedFiles = results
                if (results.isEmpty()) {
                    errorMessage = "讀取失敗詳細原因：\n$errorDbg"
                }
                isProcessing = false
            }
        } else {
            isProcessing = false
        }
    }

    // 🛠️ 新增這行來記住現在切換在哪個標籤頁 (0 = 單檔, 1 = 模組)
    var currentTab by remember { mutableStateOf(0) }

    // 🛠️ 新增：把模組的狀態存在最外層的 Scaffold 上方，讓它永不被銷毀
    val detectedFilesState = remember { mutableStateOf<List<PbFileInfo>>(emptyList()) }
    val moduleLogTextState = remember { mutableStateOf("等待選擇模組...\n") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("UECapUpdater") }, // 順便幫你把標題改成了新名字
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                // 🛠️ 加入標籤頁切換列
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("單檔套用模式", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("Magisk 模組提取", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
    ) { innerPadding ->
        
        // 根據選擇的標籤頁，顯示不同的畫面
        if (currentTab == 0) {
            // ==========================================
            // 🏷️ 這是你原本的單檔操作畫面，完全保持原樣
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RootStatusCard(rootStatus = rootStatus)

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
                                text = if (applySuccess == true) "✅ 執行完成" else "❌ 執行異常",
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

                // 🛠️ 移除原本外層的 if (selectedFiles.isNotEmpty()) 判斷
                // 讓按鈕永遠顯示，但在沒有檔案時反灰不可點擊
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
                    // 🛠️ 這裡加上 selectedFiles.isNotEmpty() 來控制是否可以點擊
                    enabled = selectedFiles.isNotEmpty() && !isApplying && !isResetting && rootStatus == RootStatus.Granted,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在注入基帶參數...")
                    } else {
                        Text("套用並重載基帶", style = MaterialTheme.typography.titleMedium)
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
                        Text("正在解除全域掛載...")
                    } else {
                        Text("恢復原廠基帶 (清除配置)")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            // ==========================================
            // 🏷️ 這是我們新建的模組提取畫面
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding) // 自動避開頂部的 AppBar 和 TabRow
            ) {
                // 🛠️ 修正：把剛剛宣告的記憶體傳遞給模組畫面
                ModuleUpdaterScreen(detectedFilesState, moduleLogTextState)
            }
        }
    }
}

@Composable
private fun RootStatusCard(rootStatus: RootStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Root 授權狀態", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (rootStatus) {
                RootStatus.Checking -> { CircularProgressIndicator(); Text("正在檢查 Root 權限…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                RootStatus.Granted -> Text("Root 權限已獲取", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                RootStatus.Denied -> Text("未取得 Root 權限", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
            Text("基帶配置檔案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Text("正在匯入檔案...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (files.isNotEmpty()) {
                Text("✅ ${files.size} 個檔案已就緒", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(files) { file -> Text("• ${file.name} (${file.sizeKb} KB)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } else { Text("尚未選擇任何 .binarypb 檔案", color = MaterialTheme.colorScheme.error) }
            if (!errorMessage.isNullOrEmpty()) { Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSelectClick, enabled = !isProcessing) { Text(if (files.isNotEmpty()) "重新選擇多個檔案" else "選擇多個檔案") }
        }
    }
}

suspend fun copyMultipleFiles(context: Context, uris: List<Uri>): Pair<List<ConfigFile>, String> = withContext(Dispatchers.IO) {
    val results = mutableListOf<ConfigFile>()
    val filesDir = context.filesDir
    val errorLogs = java.lang.StringBuilder()

    try { com.topjohnwu.superuser.Shell.cmd("rm -f ${filesDir.absolutePath}/*.binarypb").exec() }
    catch (e: Exception) { errorLogs.append("清理舊檔案失敗: ${e.message}\n") }

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
                errorLogs.append("[$displayName] 無法開啟 InputStream\n")
                continue
            }

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                    // 🧙‍♂️ 保持純淨：這裡不再塞入會破壞結構與導致 MDS 閃退的版本號魔法
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                results.add(ConfigFile(displayName, targetFile.length() / 1024))
            } else {
                errorLogs.append("[$displayName] 檔案寫入異常\n")
            }

        } catch (e: Exception) {
            errorLogs.append("處理發生例外錯誤: ${e.message}\n")
        }
    }
    return@withContext Pair(results, errorLogs.toString())
}

// 套用配置核心腳本：包含直觀檔案回顯面板
suspend fun applyModemConfig(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val privateDir = context.filesDir.absolutePath
    val d = "$"

    val script = """
        echo "================================================="
        echo "          ✨ 載入基帶自訂配置清單 ✨          "
        echo "================================================="
        
        OTA_DIR="/data/vendor/radio/ota_uecap"
        VENDOR_DIR="/vendor/firmware/uecapconfig"
        
        mkdir -p "${d}OTA_DIR"
        rm -rf "${d}OTA_DIR"/* 2>/dev/null

        # 🛠️ 修正點：在掛載時直接打印正在處理的自訂檔名，保證新手一定看得到
        for FILE in "$privateDir"/*.binarypb; do
            if [ -f "${d}FILE" ]; then
                FILENAME=${d}(basename "${d}FILE")
                TARGET_OTA="${d}OTA_DIR/${d}FILENAME"
                TARGET_VENDOR="${d}VENDOR_DIR/${d}FILENAME"
                
                cp "${d}FILE" "${d}TARGET_OTA"
                chcon u:object_r:radio_vendor_data_file:s0 "${d}TARGET_OTA" 2>/dev/null
                chmod 644 "${d}TARGET_OTA"
                
                if [ -f "${d}TARGET_VENDOR" ]; then
                    nsenter -t 1 -m -- umount "${d}TARGET_VENDOR" 2>/dev/null
                    nsenter -t 1 -m -- mount -o bind "${d}TARGET_OTA" "${d}TARGET_VENDOR"
                    if [ ${d}? -eq 0 ]; then
                        echo "  [掛載成功] 👉 ${d}FILENAME"
                    else
                        echo "  [掛載失敗] ❌ ${d}FILENAME"
                    fi
                fi
            fi
        done

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
        logcat -d -b all | grep -iE "UECAP|shamp" | tail -n 35
        echo "================================================="
        echo "=== 執行完畢 ==="
        
    """.trimIndent()

    // ... 前面的 script 保持不變 ...
    val result = com.topjohnwu.superuser.Shell.cmd(script).exec()

    // 🚀 修正：這裡應該是「生成模組」，而不是移除！
    val bootScriptSuccess = ModuleParser.createMagiskModule()

    val logOutput = buildString {
        if (result.out.isNotEmpty()) { result.out.forEach { append(it).append("\n") } }
        if (result.err.isNotEmpty()) { append("\n--- 錯誤訊息 ---\n"); result.err.forEach { append(it).append("\n") } }
        if (bootScriptSuccess) append("\n👉 Magisk 底層掛載模組已自動生成！\n")
    }
    return@withContext Pair(result.isSuccess, logOutput)
}

// 恢復原廠核心腳本
suspend fun resetModemConfig(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val otaDir = "/data/vendor/radio/ota_uecap"
    val d = "$"

    val script = """
        echo "================================================="
        echo "          ✨ 正在精準解除配置並恢復原廠 ✨       "
        echo "================================================="
        
        VENDOR_DIR="/vendor/firmware/uecapconfig"
        
        # 🛠️ 根據目前 OTA 目錄有的檔案，精準且快速地解除掛載
        for FILE in "$otaDir"/*.binarypb; do
            if [ -f "${d}FILE" ]; then
                FILENAME=${d}(basename "${d}FILE")
                nsenter -t 1 -m -- umount "${d}VENDOR_DIR/${d}FILENAME" 2>/dev/null
                echo "  [解除掛載] 🔄 ${d}FILENAME"
            fi
        done

        echo "[INFO] 清空配置目錄快取..."
        rm -rf "$otaDir"/* 2>/dev/null
        rm -rf /data/vendor/radio/modem_temp_file/* 2>/dev/null

        echo "[INFO] 清空舊日誌並安全重啟基帶與 MDS 進程..."
        logcat -b all -c
        
        am force-stop com.google.android.ModemDiagnosticSystem 2>/dev/null
        pkill -9 -f rild 2>/dev/null
        pkill -9 -f shamp 2>/dev/null
        pkill -9 -f vcd 2>/dev/null
        pkill -9 -f modem 2>/dev/null
        
        echo "[INFO] 等待硬體載入原廠設定 (15秒)..."
        sleep 15
        
        echo " "
        echo "================================================="
        echo "=== 🔧 底層 UECAP 原始載入日誌 (供進階除錯) ==="
        echo "================================================="
        logcat -d -b all | grep -iE "UECAP|shamp" | tail -n 35
        echo "================================================="
        echo "=== 恢復原廠完畢 ==="
    """.trimIndent()

    // ... 前面的 script 保持不變 ...
    val result = com.topjohnwu.superuser.Shell.cmd(script).exec()

    // 🚀 修正：刪除動態生成的模組
    ModuleParser.removeMagiskModule()

    val logOutput = buildString {
        if (result.out.isNotEmpty()) { result.out.forEach { append(it).append("\n") } }
        if (result.err.isNotEmpty()) { append("\n--- 錯誤訊息 ---\n"); result.err.forEach { append(it).append("\n") } }
        append("\n🗑️ 開機掛載模組已徹底移除。\n")
    }
    return@withContext Pair(result.isSuccess, logOutput)
}
