package com.pixelthings.uecapupdater

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.draw.clip

data class ConfigFile(val name: String, val sizeKb: Long)

class MainActivity : androidx.activity.ComponentActivity() {
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
// 🚀 強制接管：首度載入時讀取系統語系，之後完全由本地 Compose 狀態操控，免疫系統 API 失效問題
    var isEn by remember {
        mutableStateOf(configuration.locales[0].toLanguageTag().contains("en", ignoreCase = true))
    }

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
                    errorMessage =
                        if (isEn) "Read failed. Detailed reasons:\n$errorDbg" else "讀取失敗詳細原因：\n$errorDbg"
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
                    title = { Text("UECapUpdater") },
                    actions = {
                        TextButton(onClick = {
                            // 🚀 核心修復：直接反轉布林值，Compose 偵測到狀態改變會瞬間強制刷新整頁文字！
                            isEn = !isEn
                        }) {
                            Text(
                                text = if (isEn) "EN" else "中",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, // ✅ 契合 MD3 頂部列文字標準色
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                TabRow(
                    selectedTabIndex = currentTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    indicator = { tabPositions ->
                        if (currentTab < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[currentTab])
                                    .padding(horizontal = 32.dp) // 🚀 左右往內縮，讓直線變短
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50)), // 🚀 兩側變成極致圓潤
                                height = 4.dp, // 稍微加粗一點更符合 MD3
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {} // 🚀 隱藏原本貫穿整個螢幕的死板灰色底線
                ) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = {
                            Text(
                                if (isEn) "Single-File Mode" else "單檔套用模式",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = {
                            Text(
                                if (isEn) "Module Extract" else "Magisk 模組提取",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    )
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        text = {
                            Text(
                                if (isEn) "Terminal" else "終端除錯",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
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
                    RootStatusCard(
                        rootStatus = rootStatus,
                        isEn = isEn,
                        onRetryClick = { viewModel.checkRootAccess() })

                    FileSelectionCard(
                        files = selectedFiles,
                        isProcessing = isProcessing,
                        errorMessage = errorMessage,
                        onSelectClick = { launcher.launch("*/*") },
                        isEn = isEn
                    )

                    var applySuccess by remember { mutableStateOf<Boolean?>(null) }
                    var shellLogs by remember { mutableStateOf<String?>(null) }

                    if (shellLogs != null) {
                        val scrollState = rememberScrollState()
                        LaunchedEffect(shellLogs) {
                            scrollState.scrollTo(scrollState.maxValue)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                // ✅ 語法修復：精準改用主題內的容器色，拉出原廠設定的漂亮大方塊層次
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (applySuccess == null) "⏳ Running..." else if (applySuccess == true) "✅ Execution Complete" else "❌ Execution Failed",
                                    color = if (applySuccess == null) Color(0xFFFFC107) else if (applySuccess == true) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // 🚀 視覺微調：將日誌終端機的外框背景改為最低容器色，告別死黑，完美契合 M3 風格
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .heightIn(max = 300.dp)
                                            .verticalScroll(scrollState)
                                            .padding(8.dp) // 讓日誌文字與邊框有一點呼吸空間
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = shellLogs!!,
                                                // 🚀 提示：如果你想讓日誌顏色更柔和（仿 Android 終端機），可以把亮綠色改為 MaterialTheme.colorScheme.primary
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
                    }
                    Button(
                        onClick = {
                            isApplying = true
                            shellLogs = ""
                            applySuccess = null
                            coroutineScope.launch {
                                val success = applyModemConfigRealTime(context) { line ->
                                    shellLogs = (shellLogs ?: "") + line + "\n"
                                }
                                applySuccess = success
                                isApplying = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = selectedFiles.isNotEmpty() && !isApplying && !isResetting && rootStatus == RootStatus.Granted,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isEn) "Injecting modem parameters..." else "正在注入基帶參數...")
                        } else {
                            Text(
                                if (isEn) "Apply & Restart Radio" else "套用並重載基帶",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isResetting = true
                            shellLogs = ""
                            applySuccess = null
                            coroutineScope.launch {
                                val success = resetModemConfigRealTime(context) { line ->
                                    shellLogs = (shellLogs ?: "") + line + "\n"
                                }
                                applySuccess = success
                                isResetting = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isApplying && !isResetting && rootStatus == RootStatus.Granted,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isResetting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isEn) "Unmounting global configs..." else "正在卸載全域設定...")
                            // 👉 精準替換為：
                        } else {
                            Text(if (isEn) "Restore Factory (Clear Configs)" else "恢復原廠基帶 (清除配置)")
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
                    ModuleUpdaterScreen(detectedFilesState, moduleLogTextState, isEn)
                }
            }
            2 -> {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)) {
                    DebugConsoleScreen(isEn = isEn, rootStatus = rootStatus)
                }
            }
        }
    }
}

@Composable
fun DebugConsoleScreen(isEn: Boolean, rootStatus: RootStatus) {
    var command by remember { mutableStateOf("") }
    var outputLog by remember { mutableStateOf(if (isEn) "Waiting for command..." else "等待輸入指令...") }
    var isExecuting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedFilterChip(
                selected = false,
                onClick = { command = "logcat -d -b all | grep -iE 'UECAP|shamp' | grep -v 'com.pixelthings.uecapupdater'" },
                label = { Text("Dump UECAP Logs") }
            )
            ElevatedFilterChip(
                selected = false,
                onClick = { command = "grep 'uecapconfig' /proc/mounts | grep -v 'com.pixelthings.uecapupdater'" },
                label = { Text("Check Mounts") }
            )
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
                        outputLog = sb.toString()
                            .ifEmpty { if (isEn) "[Process completed with no output]" else "[執行完畢，無輸出內容]" }
                        isExecuting = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = command.isNotBlank() && !isExecuting && rootStatus == RootStatus.Granted,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            if (isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEn) "Running..." else "執行中...")
            } else {
                Text(if (isEn) "Execute Command" else "執行指令")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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

@Composable
private fun RootStatusCard(rootStatus: RootStatus, isEn: Boolean, onRetryClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
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
private fun FileSelectionCard(
    files: List<ConfigFile>,
    isProcessing: Boolean,
    errorMessage: String?,
    onSelectClick: () -> Unit,
    isEn: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                if (isEn) "Baseband Configuration Files" else "基帶配置檔案",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Text(
                    if (isEn) "Importing files..." else "正在匯入檔案...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (files.isNotEmpty()) {
                Text(
                    text = if (isEn) "✅ ${files.size} files ready" else "✅ 已準備好 ${files.size} 個檔案",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier
                    .heightIn(max = 120.dp)
                    .fillMaxWidth()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(files) { file ->
                            Text(
                                "• ${file.name} (${file.sizeKb} KB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Text(
                    if (isEn) "No files selected" else "尚未選擇任何檔案",
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!errorMessage.isNullOrEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSelectClick,
                enabled = !isProcessing
            ) {
                Text(
                    if (files.isNotEmpty()) {
                        if (isEn) "Reselect multiple files" else "重新選擇多個檔案"
                    } else {
                        if (isEn) "Select files" else "選擇檔案"
                    }
                )
            }
        }
    }
}

suspend fun copyMultipleFiles(
    context: Context,
    uris: List<Uri>,
    isEn: Boolean
): Pair<List<ConfigFile>, String> = withContext(Dispatchers.IO) {
    val results = mutableListOf<ConfigFile>()
    val filesDir = context.filesDir
    val errorLogs = java.lang.StringBuilder()

    try {
        com.topjohnwu.superuser.Shell.cmd("rm -f ${filesDir.absolutePath}/*.binarypb").exec()
    } catch (e: Exception) {
        errorLogs.append(if (isEn) "Failed to clean old files: " else "清理舊檔案失敗: ")
            .append("${e.message}\n")
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
            errorLogs.append(if (isEn) "Exception occurred during processing: " else "處理發生例外錯誤: ")
                .append("${e.message}\n")
        }
    }
    return@withContext Pair(results, errorLogs.toString())
}

// 🚀 智慧拆解：單檔套用協程流式實時日誌 (混合 Logcat 即時流)
suspend fun applyModemConfigRealTime(context: Context, onLogLine: (String) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        suspend fun publish(line: String) {
            withContext(Dispatchers.Main) { onLogLine(line) }
        }
        val privateDir = context.filesDir.absolutePath

        publish("=================================================")
        publish("          ✨ Loading Custom UECAP Configs ✨      ")
        publish("=================================================")

        publish("[INFO] Resetting Logcat buffer...")
        Shell.cmd("logcat -b all -c").exec()
        delay(1500) // 🚀 關鍵修復 1：給系統足夠時間把舊日誌沖刷乾淨

        publish("[INFO] Starting real-time Logcat stream daemon...")
        val logcatProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -v time -b all"))
        val logcatJob = launch(Dispatchers.IO) {
            try {
                logcatProcess.inputStream.bufferedReader().use { reader ->
                    var line: String? = null
                    // 使用 while 搭配 yields 確保協程隨時可被安全釋放，且不阻塞線程
                    while (isActive && reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        if ((currentLine.contains("UECAP", true) || currentLine.contains("shamp", true)) &&
                            !currentLine.contains("com.pixelthings.uecapupdater")) {
                            // 確保 publish 內部安全，若 UI 來不及刷就暫緩 1 毫秒
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
        val unmountScript = """
            for m in ${'$'}(grep "uecapconfig" /proc/mounts | awk '{print ${'$'}2}'); do
                umount -l "${'$'}m" 2>/dev/null
            done
            for f in /vendor/firmware/uecapconfig/*.binarypb; do
                umount -l "${'$'}f" 2>/dev/null
            done
        """.trimIndent()
        Shell.cmd("cat << 'EOF' > /data/local/tmp/unmount_uecap.sh\n$unmountScript\nEOF").exec()
        Shell.cmd("chmod 755 /data/local/tmp/unmount_uecap.sh && nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh > /dev/null 2>&1 & sleep 1 && rm -f /data/local/tmp/unmount_uecap.sh").exec()

        publish("[INFO] Cleaning cache directories...")
        Shell.cmd("rm -rf /data/vendor/radio/ota_uecap/* /data/vendor/radio/modem_temp_file/*").exec()

        publish("[INFO] Processing and staging configurations...")
        val pbFiles = File(privateDir).listFiles { _, name -> name.endsWith(".binarypb") } ?: emptyArray()
        for (file in pbFiles) {
            val targetOta = "/data/vendor/radio/ota_uecap/${file.name}"
            Shell.cmd("cp \"${file.absolutePath}\" \"$targetOta\"").exec()
            Shell.cmd("chown radio:radio \"$targetOta\" && chcon u:object_r:vendor_file:s0 \"$targetOta\" && chmod 644 \"$targetOta\"").exec()
        }

        publish("[INFO] Executing batch binding mounts...")
        val mountScript = """
            #!/system/bin/sh
            for TARGET_OTA in /data/vendor/radio/ota_uecap/*.binarypb; do
                if [ -f "${'$'}TARGET_OTA" ]; then
                    FILENAME=${'$'}(basename "${'$'}TARGET_OTA")
                    TARGET_VENDOR="/vendor/firmware/uecapconfig/${'$'}FILENAME"
                    if [ -f "${'$'}TARGET_VENDOR" ]; then
                        mount -o bind "${'$'}TARGET_OTA" "${'$'}TARGET_VENDOR"
                        if [ ${'$'}? -eq 0 ]; then
                            echo "[INFO] 👉 ${'$'}FILENAME bound globally"
                        else
                            echo "[INFO] ❌ ${'$'}FILENAME bind failed"
                        fi
                    fi
                fi
            done
        """.trimIndent()
        Shell.cmd("cat << 'EOF' > /data/local/tmp/mount_uecap.sh\n$mountScript\nEOF").exec()
        val mountResult = Shell.cmd("chmod 755 /data/local/tmp/mount_uecap.sh && nsenter -t 1 -m -- /data/local/tmp/mount_uecap.sh && rm -f /data/local/tmp/mount_uecap.sh").exec()
        mountResult.out.forEach { publish(it) }

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

        // 🛑 停止背景 Logcat 監聽
        logcatProcess.destroy()
        logcatJob.cancel()

        ModuleParser.createMagiskModule()
        return@withContext true
    }

// 🚀 智慧拆解：恢復原廠協程流式實時日誌 (混合 Logcat 即時流)
suspend fun resetModemConfigRealTime(context: Context, onLogLine: (String) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        suspend fun publish(line: String) {
            withContext(Dispatchers.Main) { onLogLine(line) }
        }

        publish("=================================================")
        publish("          ✨ Restoring Factory Baseband ✨       ")
        publish("=================================================")

        publish("[INFO] Resetting Logcat buffer...")
        Shell.cmd("logcat -b all -c").exec()
        delay(1500) // 🚀 關鍵修復 1：給系統足夠時間把舊日誌沖刷乾淨

        publish("[INFO] Starting real-time Logcat stream daemon...")
        val logcatProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -v time -b all"))
        val logcatJob = launch(Dispatchers.IO) {
            try {
                logcatProcess.inputStream.bufferedReader().use { reader ->
                    var line: String? = null
                    // 使用 while 搭配 yields 確保協程隨時可被安全釋放，且不阻塞線程
                    while (isActive && reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        if ((currentLine.contains("UECAP", true) || currentLine.contains("shamp", true)) &&
                            !currentLine.contains("com.pixelthings.uecapupdater")) {
                            // 確保 publish 內部安全，若 UI 來不及刷就暫緩 1 毫秒
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
        val unmountScript = """
            for m in ${'$'}(grep "uecapconfig" /proc/mounts | awk '{print ${'$'}2}'); do
                umount -l "${'$'}m" 2>/dev/null
                echo "[INFO] 🔄 ${'$'}m successfully unmounted"
            done
            for f in /vendor/firmware/uecapconfig/*.binarypb; do
                umount -l "${'$'}f" 2>/dev/null
            done
        """.trimIndent()
        Shell.cmd("cat << 'EOF' > /data/local/tmp/unmount_uecap.sh\n$unmountScript\nEOF").exec()
        val unmountResult = Shell.cmd("chmod 755 /data/local/tmp/unmount_uecap.sh && nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh > /dev/null 2>&1 & sleep 1 && rm -f /data/local/tmp/unmount_uecap.sh").exec()
        unmountResult.out.forEach { publish(it) }

        publish("[INFO] Cleaning cache directories...")
        Shell.cmd("rm -rf /data/vendor/radio/ota_uecap/* /data/vendor/radio/modem_temp_file/*").exec()

        publish("[INFO] Force killing modem daemons (Raw Boot)...")
        Shell.cmd("pkill -9 -f rild; pkill -9 -f shamp; pkill -9 -f vcd; pkill -9 -f modem").exec()

        publish("[INFO] Awaiting hardware fallback to pure factory state...")
        delay(4000)

        publish("[INFO] Reviving factory network and waiting for carrier registration...")
        Shell.cmd("svc data disable").exec()
        delay(1000)
        Shell.cmd("svc data enable").exec()

        // 🚀 呼叫智慧信號守護進程，不到黃河心不死
        val isLocked = awaitSignalLock { line -> publish(line) }
        if (!isLocked) {
            publish("[ERROR] Baseband might be unstable. Please check Terminal for deep errors.")
        }
        publish("=================================================")
        publish("=== Factory Reset Complete ===")

        logcatProcess.destroy()
        logcatJob.cancel()

        ModuleParser.removeMagiskModule()
        return@withContext true
    }

// 🚀 核心升級：動態信號鎖定守護進程 (過濾歷史日誌 ＋ 正向計時)
suspend fun awaitSignalLock(publish: suspend (String) -> Unit): Boolean {
    publish("[INFO] Monitoring native Android telephony state...")
    var realSignalConfirmed = false
    var counter = 0
    val maxWaitSeconds = 90 // 安全底線

    while (!realSignalConfirmed && counter < maxWaitSeconds) {
        counter++
        delay(1000)

        // 1. 查詢系統通訊註冊表，並「切斷」底部的歷史紀錄 (Local logs) 以防讀取到舊的殘留狀態！
        val rawOutput = Shell.cmd("dumpsys telephony.registry").exec().out
        val activeStateLines = rawOutput.takeWhile { !it.contains("Local logs", ignoreCase = true) && !it.contains("log:", ignoreCase = true) }
        val hasSystemSignal = activeStateLines.any { it.contains("mVoiceRegState=0") || it.contains("mDataRegState=0") }

        val isShampAlive = Shell.cmd("pidof shamp").exec().out.isNotEmpty()

        if (counter <= 8) {
            if (isShampAlive) {
                publish("[INFO] ⏳ Baseband daemon initialized... Waiting for state flush (Elapsed: ${counter}s)")
            } else {
                publish("[INFO] ⏳ Waiting for baseband hardware power-on... (Elapsed: ${counter}s)")
            }
        } else {
            // 8 秒過後，歷史假訊號已被徹底洗淨。此時檢查到的絕對是當前真實連線狀態！
            if (hasSystemSignal) {
                publish("[INFO] ✅ Android System Telephony Registry is IN_SERVICE. (Locked at ${counter}s)")
                realSignalConfirmed = true
            } else {
                publish("[INFO] ⏳ Waiting for network registration... (Elapsed: ${counter}s)")
            }
        }
    }

    if (!realSignalConfirmed) {
        publish("[WARN] ⚠️ Reached 90s safety timeout. The modem might be stuck or searching for network.")
    } else {
        // 訊號確定回來後，執行最終日誌打撈 (正向讀秒)
        publish("[INFO] Capturing final UECAP handshakes & flushing logs...")
        for (i in 1..5) {
            publish("[INFO] ⏳ Finalizing baseband streams... (Elapsed: ${i}s / 5s)")
            delay(1000)
        }
    }

    return realSignalConfirmed
}