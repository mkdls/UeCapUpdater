package com.pixelthings.uecapupdater

import android.content.Context
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val modemOperationMutex = Mutex()
internal fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

/** Run one script in the cached root shell, optionally inside PID 1's mount namespace. */
private fun checkedScript(context: Context, script: String, global: Boolean = false): List<String> {
    val file = File.createTempFile("uecap-", ".sh", context.cacheDir)
    try {
        file.writeText("#!/system/bin/sh\nset -e\n$script\n")
        val prefix = if (global) "nsenter -t 1 -m -- " else ""
        val result = Shell.cmd("${prefix}sh ${shellQuote(file.absolutePath)}").exec()
        check(result.isSuccess) {
            "Shell exit ${result.code}: " + (result.out + result.err).takeLast(12).joinToString("\n")
        }
        return result.out
    } finally {
        file.delete()
    }
}

/** One ZIP traversal per selected archive; finish extraction before changing any mounts. */
suspend fun applyModuleConfigsRealTime(
    context: Context,
    selections: List<PbFileInfo>,
    isEn: Boolean,
    onLogLine: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val directory = File.createTempFile("uecap-extract-", "", context.cacheDir)
    check(directory.delete() && directory.mkdir()) { "Cannot create extraction directory" }
    try {
        require(selections.isNotEmpty()) { "No configurations selected" }
        require(selections.map { it.fileName }.distinct().size == selections.size) {
            "Duplicate configuration filenames; select only one version of each file"
        }
        val started = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Main) {
            onLogLine(if (isEn) "[INFO] Extracting selected files once per ZIP..." else "[INFO] 按 ZIP 一次提取所選檔案...")
        }
        val extracted = mutableListOf<File>()
        for ((uri, files) in selections.groupBy { it.zipUri }) {
            val wanted = files.associateBy { it.entryName }.toMutableMap()
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }
            ZipInputStream(input.buffered()).use { zip ->
                while (wanted.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    val selection = wanted.remove(entry.name) ?: continue
                    require(!entry.isDirectory && File(selection.fileName).name == selection.fileName) { "Invalid ZIP filename" }
                    val output = File(directory, selection.fileName)
                    output.outputStream().use { zip.copyTo(it) }
                    require(output.length() > 0) { "Empty configuration: ${selection.fileName}" }
                    extracted.add(output)
                }
            }
            check(wanted.isEmpty()) { "Missing ZIP entries: ${wanted.keys.joinToString()}" }
        }
        withContext(Dispatchers.Main) {
            onLogLine("[TIME] ZIP: ${SystemClock.elapsedRealtime() - started} ms (${extracted.size} files)")
        }
        runModemOperation(context, isEn, false, onLogLine, extracted, moduleFiles = true)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        withContext(Dispatchers.Main) { onLogLine("[ERROR] ${error.message}") }
        false
    } finally {
        directory.deleteRecursively()
    }
}

suspend fun runModemOperation(
    context: Context,
    isEn: Boolean,
    reset: Boolean,
    onLogLine: (String) -> Unit,
    preparedFiles: List<File>? = null,
    moduleFiles: Boolean = false
): Boolean = withContext(Dispatchers.IO) {
    modemOperationMutex.withLock {
        suspend fun publish(line: String) = withContext(Dispatchers.Main) { onLogLine(line) }
        suspend fun stage(en: String, zh: String, action: suspend () -> Unit) {
            val label = if (isEn) en else zh
            publish("[INFO] $label...")
            val started = SystemClock.elapsedRealtime()
            try {
                action()
            } finally {
                publish("[TIME] $label: ${SystemClock.elapsedRealtime() - started} ms")
            }
        }
        val started = SystemClock.elapsedRealtime()
        var logcat: Process? = null
        var readerJob: Job? = null
        var displayJob: Job? = null
        val lines = Channel<String>(256, BufferOverflow.DROP_OLDEST)
        try {
            val files = if (reset) emptyList() else preparedFiles ?: context.filesDir
                .listFiles { _, name -> name.endsWith(".binarypb") }?.toList().orEmpty()
            if (!reset) {
                require(files.isNotEmpty()) { "No configurations selected" }
                require(files.all { it.isFile && it.length() > 0 && it.name.endsWith(".binarypb") }) { "Invalid configuration files" }
                require(files.map { it.name }.distinct().size == files.size) { "Duplicate configuration filenames" }
                // Validate all destinations in PID 1 before removing existing working mounts.
                stage("Validate targets", "檢查目標檔案") {
                    checkedScript(context, files.joinToString("\n") {
                        "[ -f ${shellQuote("/vendor/firmware/uecapconfig/${it.name}")} ] || { echo ${shellQuote("Missing target: ${it.name}")}; exit 1; }"
                    }, global = true)
                }
            }
            stage("Start log capture", "啟動日誌監控") {
                val cleared = Shell.cmd("logcat -b all -c").exec()
                if (!cleared.isSuccess) publish("[WARN] Could not clear Logcat; capture may include older messages")
                // No fixed sleep after the synchronous clear command.
                try {
                    val process = ProcessBuilder("su", "-c", "exec logcat -v time -b all")
                        .redirectErrorStream(true).start()
                    logcat = process
                    readerJob = launch(Dispatchers.IO) {
                        try {
                            process.inputStream.bufferedReader().use { reader ->
                                while (isActive) {
                                    val line = reader.readLine() ?: break
                                    if ((line.contains("UECAP", true) || line.contains("shamp", true)) &&
                                        !line.contains("com.pixelthings.uecapupdater")) lines.trySend("[Logcat] $line")
                                }
                            }
                        } catch (_: java.io.IOException) {
                            // The process is closed when the operation completes or is cancelled.
                        } finally {
                            lines.close()
                        }
                    }
                    displayJob = launch {
                        for (line in lines) {
                            val batch = StringBuilder(line)
                            delay(100)
                            repeat(49) { lines.tryReceive().getOrNull()?.let { batch.append('\n').append(it) } }
                            publish(batch.toString())
                        }
                    }
                } catch (error: java.io.IOException) {
                    publish("[WARN] Logcat unavailable: ${error.message}")
                }
            }
            stage("Unmount active overrides", "卸載實際掛載項目") {
                checkedScript(context, """
                    # Snapshot in reverse order so stacked mounts are removed top first.
                    TARGETS=${'$'}(awk '${'$'}5 ~ /^\/vendor\/firmware\/uecapconfig(\/|${'$'})/ { paths[++n]=${'$'}5 } END { for(i=n;i>0;i--) print paths[i] }' /proc/self/mountinfo)
                    COUNT=0
                    for TARGET in ${'$'}TARGETS; do
                        umount -l "${'$'}TARGET"
                        COUNT=${'$'}((COUNT + 1))
                    done
                    echo "[INFO] Unmounted ${'$'}COUNT active mounts"
                """.trimIndent(), global = true).forEach { publish(it) }
            }
            stage("Prepare configuration files", "準備配置檔案") {
                val script = buildString {
                    appendLine("mkdir -p /data/vendor/radio/ota_uecap")
                    appendLine("rm -rf /data/vendor/radio/ota_uecap/* /data/vendor/radio/modem_temp_file/*")
                    if (moduleFiles) appendLine("chmod 755 /data/vendor/radio/ota_uecap")
                    for (file in files) {
                        val target = shellQuote("/data/vendor/radio/ota_uecap/${file.name}")
                        appendLine("cp ${shellQuote(file.absolutePath)} $target")
                        appendLine("chmod 644 $target")
                        if (moduleFiles) {
                            // Keep the module mode's existing best-effort SELinux label behavior.
                            appendLine("chcon u:object_r:radio_vendor_data_file:s0 $target 2>/dev/null || true")
                        } else {
                            appendLine("chown radio:radio $target")
                            appendLine("chcon u:object_r:vendor_file:s0 $target")
                        }
                    }
                }
                checkedScript(context, script)
            }
            if (!reset) stage("Bind configuration files", "批量掛載配置") {
                val script = files.joinToString("\n") { file ->
                    "mount -o bind ${shellQuote("/data/vendor/radio/ota_uecap/${file.name}")} " +
                        "${shellQuote("/vendor/firmware/uecapconfig/${file.name}")}\n" +
                        "echo ${shellQuote("[INFO] Mounted ${file.name}")}"
                }
                checkedScript(context, script, global = true).forEach { publish(it) }
            }
            publish("[TIME] ${if (isEn) "Before modem reset" else "基帶重置前累計"}: ${SystemClock.elapsedRealtime() - started} ms")
            stage("Reset modem hardware", "重置基帶硬體") {
                checkedScript(context, "printf 'AT+GOOGCPRESET\\r' > /dev/umts_router")
                // Retain the existing device-specific settling periods pending real-device tests.
                delay(4000)
                checkedScript(context, "svc data disable")
                delay(1000)
                checkedScript(context, "svc data enable")
            }
            if (!awaitSignalLock(isEn) { publish(it) }) {
                publish(if (isEn) "[ERROR] Network registration timed out" else "[ERROR] 網路註冊逾時")
                false
            } else {
                stage("Update boot module", "更新開機模組") {
                    if (reset) {
                        checkedScript(context, "rm -rf /data/adb/modules/uecapupdater_live")
                    } else {
                        check(ModuleParser.createMagiskModule(isEn)) { "Could not create boot module" }
                    }
                }
                publish(if (isEn) "=== Completed ===" else "=== 執行完成 ===")
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            publish("[ERROR] ${error.message}")
            false
        } finally {
            logcat?.destroy()
            readerJob?.cancel()
            displayJob?.cancel()
            lines.cancel()
            withContext(NonCancellable + Dispatchers.Main) {
                onLogLine("[TIME] ${if (isEn) "Total" else "總耗時"}: " +
                    String.format(Locale.ROOT, "%.1f s", (SystemClock.elapsedRealtime() - started) / 1000.0))
            }
        }
    }
}
