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
     * 提取選中的 binarypb，利用 libsu 取代並套用到手機系統中，並回傳詳細日誌
     */
    fun applyPbFile(context: Context, fileInfo: PbFileInfo): Pair<Boolean, String> {
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

        if (!extractSuccess || !cacheFile.exists()) return Pair(false, "❌ 提取檔案 ${fileInfo.fileName} 失敗\n")

        // 🛠️ 修正點：加上與單檔模式一模一樣的 OTA 路徑與 Vendor 目標路徑
        val targetOta = "/data/vendor/radio/ota_uecap"
        val targetVendor = "/vendor/firmware/uecapconfig"

        val commands = listOf(
            "mkdir -p $targetOta",
            "cp ${cacheFile.absolutePath} $targetOta/${fileInfo.fileName}",
            "chmod 755 $targetOta",
            "chmod 644 $targetOta/${fileInfo.fileName}",
            "chcon u:object_r:radio_vendor_data_file:s0 $targetOta/${fileInfo.fileName} 2>/dev/null || true",

            // 🚀 最關鍵的替換魔法：全域命名空間掛載 (nsenter bind mount)
            "if [ -f $targetVendor/${fileInfo.fileName} ]; then",
            "  nsenter -t 1 -m -- umount $targetVendor/${fileInfo.fileName} 2>/dev/null",
            "  nsenter -t 1 -m -- mount -o bind $targetOta/${fileInfo.fileName} $targetVendor/${fileInfo.fileName}",
            "  echo \"👉 ${fileInfo.fileName} 全域掛載成功\"",
            "else",
            "  echo \"⚠️ 系統原廠路徑找不到 ${fileInfo.fileName}，略過掛載\"",
            "fi"
        )

        val result = Shell.cmd(commands.joinToString("\n")).exec()
        val logOutput = buildString {
            if (result.out.isNotEmpty()) { result.out.forEach { append(it).append("\n") } }
            if (result.err.isNotEmpty()) { append("\n--- 錯誤訊息 ---\n"); result.err.forEach { append(it).append("\n") } }
        }

        cacheFile.delete()
        return Pair(result.isSuccess, logOutput)
    }
}
