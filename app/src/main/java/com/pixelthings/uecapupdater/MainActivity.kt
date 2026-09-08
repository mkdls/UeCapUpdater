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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.animation.*
import androidx.compose.animation.core.tween

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
    // 將 currentTab 宣告改為支援 rememberSaveable
    var currentTab by rememberSaveable { mutableStateOf(0) }
    var isEn by rememberSaveable {
        mutableStateOf(configuration.locales[0].toLanguageTag().contains("en", ignoreCase = true))
    }
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

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

    val detectedFilesState: MutableState<List<PbFileInfo>> = remember { mutableStateOf(emptyList()) }
    val moduleLogTextState: MutableState<String> = remember { mutableStateOf(if (isEn) "Waiting for modules...\n" else "等待選擇模組...\n") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        // 🚀 標題淡入淡出過渡
                        AnimatedContent(
                            targetState = when (currentTab) {
                                2 -> if (isEn) "Terminal" else "終端除錯"
                                3 -> if (isEn) "Active Configs" else "生效配置"
                                4 -> if (isEn) "Editor" else "編輯器"
                                else -> "UECapUpdater"
                            },
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                        fadeOut(animationSpec = tween(150))
                            },
                            label = "TitleTransition"
                        ) { targetTitle ->
                            Text(targetTitle)
                        }
                    },
                    navigationIcon = {
                        // 🚀 返回按鈕淡入展開動畫
                        AnimatedVisibility(
                            visible = currentTab >= 2,
                            enter = fadeIn(animationSpec = tween(200)) + expandHorizontally(),
                            exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally()
                        ) {
                            IconButton(onClick = { currentTab = 0 }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = { isEn = !isEn }) {
                            Text(
                                text = if (isEn) "EN" else "中",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isEn) "Editor" else "編輯器") },
                                onClick = {
                                    showMenu = false
                                    currentTab = 4
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isEn) "Active Configs" else "生效配置") },
                                onClick = {
                                    showMenu = false
                                    currentTab = 3
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isEn) "Terminal" else "終端除錯") },
                                onClick = {
                                    showMenu = false
                                    currentTab = 2
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (isEn) "About" else "關於") },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                // 🚀 Tab 列展開／垂直縮放收起動畫
                AnimatedVisibility(
                    visible = currentTab < 2,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                ) {
                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        indicator = { tabPositions ->
                            if (currentTab < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier
                                        .tabIndicatorOffset(tabPositions[currentTab])
                                        .padding(horizontal = 48.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(50)),
                                    height = 4.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {}
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
                    }
                }
            }
        },
    ) { innerPadding ->
        // 🚀 核心畫面切換動畫 (滑動 + 淡入淡出)
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(animationSpec = tween(250)) { it / 3 } + fadeIn(animationSpec = tween(250))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = tween(250)) { -it / 3 } + fadeIn(animationSpec = tween(250))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(200)) { it / 3 } + fadeOut(animationSpec = tween(200)))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "TabContentAnimation"
        ) { targetTab ->
            when (targetTab) {
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
                            onRetryClick = { viewModel.checkRootAccess() }
                        )

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

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .heightIn(max = 300.dp)
                                                .verticalScroll(scrollState)
                                                .padding(8.dp)
                                        ) {
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
                        }

                        Button(
                            onClick = {
                                isApplying = true
                                shellLogs = ""
                                applySuccess = null
                                coroutineScope.launch {
                                    val success = applyModemConfigRealTime(context, isEn) { line ->
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
                                    val success = resetModemConfigRealTime(context, isEn) { line ->
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        DebugConsoleScreen(isEn = isEn, rootStatus = rootStatus)
                    }
                }
                3 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        ActiveConfigsScreen(isEn = isEn, rootStatus = rootStatus)
                    }
                }
                4 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        UecapEditorScreen(isEn = isEn)
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(if (isEn) "About" else "關於本程式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "UECapUpdater",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEn)
                            "A tool for dynamically injecting and managing Pixel 5G baseband capabilities."
                        else
                            "動態注入與管理 Pixel 5G 基帶頻段配置的實用工具。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = if (isEn) "Acknowledgments:" else "特別鳴謝：",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        // 標註 HTML 原作者來源
                        text = "Core UI layout and UECAP encoding logic are inspired by nxij/pixel-pb. PB decoding powered by Protocol Buffers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(if (isEn) "Close" else "關閉")
                }
            }
        )
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
suspend fun applyModemConfigRealTime(context: Context, isEn: Boolean, onLogLine: (String) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        suspend fun publish(line: String) {
            withContext(Dispatchers.Main) { onLogLine(line) }
        }
        val privateDir = context.filesDir.absolutePath

        publish("=================================================")
        publish(if (isEn) "          ✨ Loading Custom UECAP Configs ✨      " else "          ✨ 正在載入自定義 UECAP 配置 ✨       ")
        publish("=================================================")

        publish(if (isEn) "[INFO] Resetting Logcat buffer..." else "[INFO] 正在重設 Logcat 緩衝區...")
        Shell.cmd("logcat -b all -c").exec()
        delay(1500) // 🚀 關鍵修復 1：給系統足夠時間把舊日誌沖刷乾淨

        publish(if (isEn) "[INFO] Starting real-time Logcat stream daemon..." else "[INFO] 啟動即時 Logcat 監控守護進程...")
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
                publish(if (isEn) "[INFO] Logcat stream disconnected gracefully." else "[INFO] Logcat 串流已正常斷開。")
            }
        }

        publish(if (isEn) "[INFO] Requesting Root to scan & unmount active overrides in PID 1..." else "[INFO] 請求 Root 掃描並卸載 PID 1 中的活動掛載...")
        publish(if (isEn) "[INFO] (This may take up to 5 seconds depending on Root overhead...)" else "[INFO] (這可能需要長達 5 秒，視 Root 響應速度而定...)")
        val unmountScript = """
            for m in ${'$'}(grep "uecapconfig" /proc/mounts | awk '{print ${'$'}2}'); do
                umount -l "${'$'}m" 2>/dev/null
            done
            for f in /vendor/firmware/uecapconfig/*.binarypb; do
                umount -l "${'$'}f" 2>/dev/null
            done
        """.trimIndent()
        Shell.cmd("cat << 'EOF' > /data/local/tmp/unmount_uecap.sh\n$unmountScript\nEOF").exec()
        Shell.cmd("chmod 755 /data/local/tmp/unmount_uecap.sh && nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh && rm -f /data/local/tmp/unmount_uecap.sh").exec()

        publish(if (isEn) "[INFO] Cleaning cache directories..." else "[INFO] 正在清理緩存目錄...")
        Shell.cmd("rm -rf /data/vendor/radio/ota_uecap/* /data/vendor/radio/modem_temp_file/*").exec()

        publish(if (isEn) "[INFO] Processing and staging configurations..." else "[INFO] 正在處理並暫存配置...")
        val pbFiles = File(privateDir).listFiles { _, name -> name.endsWith(".binarypb") } ?: emptyArray()
        for (file in pbFiles) {
            val targetOta = "/data/vendor/radio/ota_uecap/${file.name}"
            Shell.cmd("cp \"${file.absolutePath}\" \"$targetOta\"").exec()
            Shell.cmd("chown radio:radio \"$targetOta\" && chcon u:object_r:vendor_file:s0 \"$targetOta\" && chmod 644 \"$targetOta\"").exec()
        }

        publish(if (isEn) "[INFO] Executing batch binding mounts..." else "[INFO] 正在執行批量掛載...")
        val mountScript = """
            #!/system/bin/sh
            for TARGET_OTA in /data/vendor/radio/ota_uecap/*.binarypb; do
                if [ -f "${'$'}TARGET_OTA" ]; then
                    FILENAME=${'$'}(basename "${'$'}TARGET_OTA")
                    TARGET_VENDOR="/vendor/firmware/uecapconfig/${'$'}FILENAME"
                    if [ -f "${'$'}TARGET_VENDOR" ]; then
                        mount -o bind "${'$'}TARGET_OTA" "${'$'}TARGET_VENDOR"
                        if [ ${'$'}? -eq 0 ]; then
                            echo "[INFO] 👉 ${'$'}FILENAME globally mounted"
                        else
                            echo "[INFO] ❌ ${'$'}FILENAME bind failed"
                        fi
                    fi
                fi
            done
        """.trimIndent()
        Shell.cmd("cat << 'EOF' > /data/local/tmp/mount_uecap.sh\n$mountScript\nEOF").exec()
        val mountResult = Shell.cmd("chmod 755 /data/local/tmp/mount_uecap.sh && nsenter -t 1 -m -- /data/local/tmp/mount_uecap.sh && rm -f /data/local/tmp/unmount_uecap.sh").exec()
        mountResult.out.forEach { publish(it) }

        publish("-------------------------------------------------")
        publish(if (isEn) "[INFO] Triggering deep hardware modem reset (AT+GOOGCPRESET)..." else "[INFO] 觸發底層硬體數據機重置 (AT+GOOGCPRESET)...")
// 🚀 向底層 umts_router 寫入 AT 指令，強制 CP (Cellular Processor) 徹底斷電冷啟動，強迫重讀 NV 快取！
        Shell.cmd("echo -e 'AT+GOOGCPRESET\\r' > /dev/umts_router").exec()

        publish(if (isEn) "[INFO] Awaiting initial baseband parser process..." else "[INFO] 等待初始基帶解析程序...")
        delay(4000)

        publish(if (isEn) "[INFO] Reviving network and waiting for hardware registration..." else "[INFO] 恢復網路並等待硬體註冊...")
        Shell.cmd("svc data disable").exec()
        delay(1000)
        Shell.cmd("svc data enable").exec()

        // 🚀 呼叫智慧信號守護進程，不到黃河心不死
        val isLocked = awaitSignalLock(isEn) { line -> publish(line) }
        if (!isLocked) {
            publish(if (isEn) "[ERROR] Baseband might be unstable. Please check Terminal for deep errors." else "[錯誤] 基帶可能不穩定。請檢查終端機以獲取深層錯誤。")
        }
        publish("=================================================")
        publish(if (isEn) "=== Done ===" else "=== 執行完畢 ===")

        // 🛑 停止背景 Logcat 監聽
        logcatProcess.destroy()
        logcatJob.cancel()

        ModuleParser.createMagiskModule(isEn)
        return@withContext true
    }

// 🚀 智慧拆解：恢復原廠協程流式實時日誌 (混合 Logcat 即時流)
suspend fun resetModemConfigRealTime(context: Context, isEn: Boolean, onLogLine: (String) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        suspend fun publish(line: String) {
            withContext(Dispatchers.Main) { onLogLine(line) }
        }

        publish("=================================================")
        publish(if (isEn) "          ✨ Restoring Factory Baseband ✨       " else "          ✨ 正在恢復原廠基帶設定 ✨       ")
        publish("=================================================")

        publish(if (isEn) "[INFO] Resetting Logcat buffer..." else "[INFO] 正在重設 Logcat 緩衝區...")
        Shell.cmd("logcat -b all -c").exec()
        delay(1500) // 🚀 關鍵修復 1：給系統足夠時間把舊日誌沖刷乾淨

        publish(if (isEn) "[INFO] Starting real-time Logcat stream daemon..." else "[INFO] 啟動即時 Logcat 監控守護進程...")
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
                publish(if (isEn) "[INFO] Logcat stream disconnected gracefully." else "[INFO] Logcat 串流已正常斷開。")
            }
        }

        publish(if (isEn) "[INFO] Requesting Root to scan & unmount active overrides in PID 1..." else "[INFO] 請求 Root 掃描並卸載 PID 1 中的活動掛載...")
        publish(if (isEn) "[INFO] (This may take up to 5 seconds depending on Root overhead...)" else "[INFO] (這可能需要長達 5 秒，視 Root 響應速度而定...)")
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
        val unmountResult = Shell.cmd("chmod 755 /data/local/tmp/unmount_uecap.sh && nsenter -t 1 -m -- /data/local/tmp/unmount_uecap.sh && rm -f /data/local/tmp/unmount_uecap.sh").exec()
        unmountResult.out.forEach { publish(it) }

        publish(if (isEn) "[INFO] Cleaning cache directories..." else "[INFO] 正在清理緩存目錄...")
        Shell.cmd("rm -rf /data/vendor/radio/ota_uecap/* /data/vendor/radio/modem_temp_file/*").exec()

        publish(if (isEn) "[INFO] Triggering deep hardware modem reset (AT+GOOGCPRESET)..." else "[INFO] 觸發底層硬體數據機重置 (AT+GOOGCPRESET)...")
// 🚀 向底層 umts_router 寫入 AT 指令，強制 CP (Cellular Processor) 徹底斷電冷啟動，強迫重讀 NV 快取！
        Shell.cmd("echo -e 'AT+GOOGCPRESET\\r' > /dev/umts_router").exec()

        publish(if (isEn) "[INFO] Awaiting hardware fallback to pure factory state..." else "[INFO] 等待硬體回退至純淨原廠狀態...")
        delay(4000)

        publish(if (isEn) "[INFO] Reviving factory network and waiting for carrier registration..." else "[INFO] 恢復原廠網路並等待電信商註冊...")
        Shell.cmd("svc data disable").exec()
        delay(1000)
        Shell.cmd("svc data enable").exec()

        // 🚀 呼叫智慧信號守護進程，不到黃河心不死
        val isLocked = awaitSignalLock(isEn) { line -> publish(line) }
        if (!isLocked) {
            publish(if (isEn) "[ERROR] Baseband might be unstable. Please check Terminal for deep errors." else "[錯誤] 基帶可能不穩定。請檢查終端機以獲取深層錯誤。")
        }
        publish("=================================================")
        publish(if (isEn) "=== Factory Reset Complete ===" else "=== 原廠恢復完成 ===")

        logcatProcess.destroy()
        logcatJob.cancel()

        ModuleParser.removeMagiskModule()
        return@withContext true
    }

// 🚀 核心升級：動態信號鎖定守護進程 (過濾歷史日誌 ＋ 正向計時)
suspend fun awaitSignalLock(isEn: Boolean, publish: suspend (String) -> Unit): Boolean {
    publish(if (isEn) "[INFO] Monitoring native Android telephony state..." else "[INFO] 正在監控原生 Android 通訊狀態...")
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
                publish(if (isEn) "[INFO] ⏳ Baseband daemon initialized... Waiting for state flush (Elapsed: ${counter}s)" else "[INFO] ⏳ 基帶守護進程已初始化... 等待狀態刷新 (已耗時: ${counter}秒)")
            } else {
                publish(if (isEn) "[INFO] ⏳ Waiting for baseband hardware power-on... (Elapsed: ${counter}s)" else "[INFO] ⏳ 等待基帶硬體上電... (已耗時: ${counter}秒)")
            }
        } else {
            // 8 秒過後，歷史假訊號已被徹底洗淨。此時檢查到的絕對是當前真實連線狀態！
            if (hasSystemSignal) {
                publish(if (isEn) "[INFO] ✅ Android System Telephony Registry is IN_SERVICE. (Locked at ${counter}s)" else "[INFO] ✅ Android 系統通訊註冊表顯示已在服務中。(鎖定於 ${counter}秒)")
                realSignalConfirmed = true
            } else {
                publish(if (isEn) "[INFO] ⏳ Waiting for network registration... (Elapsed: ${counter}s)" else "[INFO] ⏳ 等待網路註冊... (已耗時: ${counter}秒)")
            }
        }
    }

    if (!realSignalConfirmed) {
        publish(if (isEn) "[WARN] ⚠️ Reached 90s safety timeout. The modem might be stuck or searching for network." else "[警告] ⚠️ 已達到 90秒 安全超時。數據機可能卡住或正在搜索網路。")
    } else {
        // 訊號確定回來後，執行最終日誌打撈 (正向讀秒)
        publish(if (isEn) "[INFO] Capturing final UECAP handshakes & flushing logs..." else "[INFO] 擷取最終 UECAP 握手並刷新日誌...")
        for (i in 1..5) {
            publish(if (isEn) "[INFO] ⏳ Finalizing baseband streams... (Elapsed: ${i}s / 5s)" else "[INFO] ⏳ 正在完成基帶串流... (已耗時: ${i}秒 / 5秒)")
            delay(1000)
        }
    }

    return realSignalConfirmed
}

@Composable
fun ActiveConfigsScreen(isEn: Boolean, rootStatus: RootStatus) {
    val context = LocalContext.current // 取得 Context 供寫入檔案使用
    var systemStatus by remember { mutableStateOf(if (isEn) "Press Refresh to scan system..." else "點擊重新整理以掃描系統...") }
    var isScanning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                isScanning = true
                // 確保雙語切換正常
                systemStatus = if (isEn) "Scanning memory and mounts..." else "正在掃描記憶體與掛載點..."

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // 組合 Shell 腳本：極速版狀態掃描
                        // 組合 Shell 腳本：精準除錯版
                        val script = """
                            echo "[ ⚙️ Shamp Baseband Daemon Status ]"
                            PID=${'$'}(pidof shamp)
                            if [ -z "${'$'}PID" ]; then
                                echo "✅ Process exited normally (One-shot task completed)"
                            else
                                echo "⏳ Running (PID: ${'$'}PID) - Still parsing..."
                            fi
                            
                            echo ""
                            echo "[ 📜 Last Parsed Config (Fast Logcat) ]"
                            # 🚀 將 tail -n 5 改為 tail -n 40，確保完整顯示所有檔案的讀取與 sent 紀錄
                            logcat -d -t 10000 -e 'shamp|binarypb|uecapconfig|successfully sent' | tail -n 40 | sed 's/^/   /' || echo "   (No recent logcat records found)"
                            
                            echo ""
                            echo "[ 📂 Baseband Temp Cache ]"
                            ls -la /data/vendor/radio/modem_temp_file/ 2>/dev/null | grep -E '\.pb|\.binarypb' | awk '{print "   " ${'$'}9}' || echo "   (Empty cache)"
                            
                            echo ""
                            echo "[ 🔗 Global Mounts & Versions (PID 1) ]"
                            MOUNTS=${'$'}(nsenter -t 1 -m -- mount | grep 'uecapconfig' | awk '{print ${'$'}3}')
                            if [ -z "${'$'}MOUNTS" ]; then
                                echo "   No custom mounts found"
                            else
                                for f in ${'$'}MOUNTS; do
                                    if [ -f "${'$'}f" ]; then
                                        FILENAME=${'$'}(basename "${'$'}f")
                                        echo "📄 ${'$'}FILENAME"
                                        stat -c "   Size: %s bytes | Modified: %y" "${'$'}f" 2>/dev/null | cut -d'.' -f1
                                        VERSION=${'$'}(strings "${'$'}f" 2>/dev/null | grep -iE '^[0-9]{4}-[0-9]{2}|v[0-9]+\.[0-9]+' | head -n 1)
                                        if [ -n "${'$'}VERSION" ]; then
                                            echo "   Version Tag: ${'$'}VERSION"
                                        else
                                            echo "   Version Tag: [Hidden/Encrypted]"
                                        fi
                                    fi
                                done
                            fi
                        """.trimIndent()

                        // 🚀 關鍵修復：直接用 Kotlin 原生 API 寫入腳本，徹底避開 Root Shell 的 EOF 阻塞與引號解析錯誤
                        val scriptFile = File(context.cacheDir, "uecap_scan.sh")
                        scriptFile.writeText(script)

                        // 執行這個實體檔案
                        val result = Shell.cmd("sh ${scriptFile.absolutePath}").exec()

                        val output = buildString {
                            if (result.out.isNotEmpty()) result.out.forEach { append(it).append("\n") }
                            if (result.err.isNotEmpty()) {
                                append("\n[Errors]\n")
                                result.err.forEach { append(it).append("\n") }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            systemStatus = output.ifBlank { if (isEn) "No output from system." else "系統沒有返回任何內容。" }
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            systemStatus = if (isEn) "Error occurred: ${e.message}" else "發生錯誤：${e.message}"
                        }
                    } finally {
                        // 確保無論成功或報錯，轉圈圈一定會停止
                        withContext(Dispatchers.Main) {
                            isScanning = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isScanning && rootStatus == RootStatus.Granted
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isEn) "Refresh System Status" else "重新整理系統狀態")
        }

        // 一鍵啟動 Pixel 官方 MDS 診斷工具 (包含進程防衝突機制與統一的按鈕樣式)
        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    // 1. 徹底獵殺舊進程，釋放底層 Socket 佔用
                    Shell.cmd("killall vcd").exec()
                    Shell.cmd("am force-stop com.google.mds").exec()
                    delay(300) // 給予系統回收資源的時間

                    // 2. 啟動全新的底層 VCD 診斷守護進程
                    Shell.cmd("/vendor/bin/vcd &").exec()
                    delay(500) // 等待 Socket 建立

                    // 3. 冷啟動喚醒 MDS App
                    Shell.cmd("am start -n com.google.mds/com.google.mds.startup.HomeActivity").exec()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = rootStatus == RootStatus.Granted
            // 💡 已經移除自訂 colors 參數，它現在會自動套用 MD3 的預設 Primary 顏色，與整體介面完美融合
        ) {
            Text(if (isEn) "Launch Pixel MDS (Hardware Diag)" else "啟動 Pixel 底層基帶診斷 (MDS)")
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = systemStatus,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
