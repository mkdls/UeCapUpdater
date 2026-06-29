package com.pixelthings.shannonconfigpro

import android.content.Context
import android.net.Uri
import com.topjohnwu.superuser.Shell
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ModuleParser {

    // 系統目標基帶配置路徑
    private const val TARGET_SYSTEM_DIR = "/data/vendor/radio/uecapconfig"

    /**
     * 掃描 Zip 模組包，找出裡面所有隱藏在 system/vendor/... 底下的 .binarypb
     */
    fun parseZipModule(context: Context, zipUri: Uri): List<PbFileInfo> {
        val fileList = mutableListOf<PbFileInfo>()
        runCatching {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        // 只要檔名結尾是 .binarypb
                        if (!entry.isDirectory && entry.name.endsWith(".binarypb", ignoreCase = true)) {
                            val file = File(entry.name)
                            fileList.add(
                                PbFileInfo(
                                    fileName = file.name,
                                    insidePath = entry.name,
                                    zipUri = zipUri,
                                    entryName = entry.name
                                )
                            )
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        }.onFailure { it.printStackTrace() }
        return fileList
    }

    /**
     * 提取選中的 binarypb，利用 libsu 取代並套用到手機系統中
     */
    fun applyPbFile(context: Context, fileInfo: PbFileInfo): Boolean {
        // 1. 先把該 .binarypb 檔案從 Zip 中提取到 App 的快取資料夾
        val cacheFile = File(context.cacheDir, fileInfo.fileName)
        var extractSuccess = false

        runCatching {
            context.contentResolver.openInputStream(fileInfo.zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == fileInfo.entryName) {
                            FileOutputStream(cacheFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            extractSuccess = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        }.onFailure { it.printStackTrace() }

        if (!extractSuccess || !cacheFile.exists()) return false

        // 2. 透過 libsu 執行 root 指令，進行全域取代與套用
        val commands = listOf(
            // 確保目標資料夾存在
            "mkdir -p $TARGET_SYSTEM_DIR",
            // 將快取檔案複製到系統基帶目錄
            "cp ${cacheFile.absolutePath} $TARGET_SYSTEM_DIR/${fileInfo.fileName}",
            // 修改權限，確保基帶 (Radio) 系統進程有權限讀取
            "chmod 755 $TARGET_SYSTEM_DIR",
            "chmod 644 $TARGET_SYSTEM_DIR/${fileInfo.fileName}",
            // 恢復 SELinux 上下文，防止因為安全策略被擋下（Pixel 必備）
            "chcon u:object_r:radio_vendor_data_file:s0 $TARGET_SYSTEM_DIR/${fileInfo.fileName} 2>/dev/null || true"
        )

        val result = Shell.cmd(commands.joinToString("\n")).exec()

        // 刪除快取臨時檔
        cacheFile.delete()

        return result.isSuccess
    }
}