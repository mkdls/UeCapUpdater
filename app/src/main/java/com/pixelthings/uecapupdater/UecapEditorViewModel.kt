package com.pixelthings.uecapupdater

import androidx.lifecycle.ViewModel
import com.pixelthings.uecapupdater.UeCapsProto.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream

class UecapEditorViewModel : ViewModel() {
    private val _uecapsData = MutableStateFlow<uecaps?>(null)
    val uecapsData: StateFlow<uecaps?> = _uecapsData.asStateFlow()

    private val _fileName = MutableStateFlow("")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText.asStateFlow()

    fun loadFromStream(inputStream: InputStream, name: String) {
        try {
            val data = uecaps.parseFrom(inputStream)
            _uecapsData.value = data
            _fileName.value = name
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveToStream(outputStream: OutputStream) {
        _uecapsData.value?.writeTo(outputStream)
    }

    fun updateFilter(query: String) {
        _filterText.value = query
    }

    fun toggleBitMaskPatch() {
        val current = _uecapsData.value ?: return
        val isCurrentlyPatched = current.comboGroupsList.firstOrNull()?.comboList?.firstOrNull()?.bitMask == 65535
        val newMask = if (isCurrentlyPatched) 0 else 65535

        val newGroups = current.comboGroupsList.map { group ->
            group.toBuilder().apply {
                clearCombo()
                group.comboList.forEach { combo ->
                    addCombo(combo.toBuilder().setBitMask(newMask).build())
                }
            }.build()
        }
        _uecapsData.value = current.toBuilder().clearComboGroups().addAllComboGroups(newGroups).build()
    }

    fun toggleIntegrity() {
        val current = _uecapsData.value ?: return
        val builder = current.toBuilder()
        if (current.hasUnknown()) builder.clearUnknown() else builder.unknown = 750569868
        _uecapsData.value = builder.build()
    }

    // --- BCS 與 Header 操作邏輯 ---
    fun parseBcs(value: Int): String {
        if (value == -1 || value == 0xFFFFFFFF.toInt()) return "All"
        if (value == 0) return ""
        val binaryString = Integer.toBinaryString(value).padStart(32, '0').reversed()
        val indexes = mutableListOf<Int>()
        for (i in binaryString.indices) {
            if (binaryString[i] == '1') indexes.add(i)
        }
        return indexes.joinToString(",")
    }

    fun bcsStringToInt(bcsStr: String): Int {
        if (bcsStr.equals("all", ignoreCase = true)) return -1
        val indexes = bcsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (indexes.isEmpty()) return 0
        var bitmask = 0
        for (index in indexes) {
            if (index in 0..31) bitmask = bitmask or (1 shl index)
        }
        return bitmask
    }

    fun updateComboHeader(groupIndex: Int, bcsNr: String, bcsIntra: String, bcsEutra: String, pc: Int, intra: Int) {
        val current = _uecapsData.value ?: return
        val group = current.getComboGroups(groupIndex)
        val newHeader = group.comboHeader.toBuilder().apply {
            this.bcsNr = bcsStringToInt(bcsNr)
            this.bcsIntraEndc = bcsStringToInt(bcsIntra)
            this.bcsEutra = bcsStringToInt(bcsEutra)
            this.powerClass = pc
            this.intraBandEnDcSupport = intra
        }.build()
        val newGroup = group.toBuilder().setComboHeader(newHeader).build()
        _uecapsData.value = current.toBuilder().setComboGroups(groupIndex, newGroup).build()
    }

    // --- Feature 格式化邏輯 (完全對齊網頁版) ---
    fun getNrDlFeatureStr(f: ShannonFeatureSetDlPerCCNr): String {
        val scsStr = when(f.maxScs) { 1->"15kHz"; 2->"30kHz"; 3->"60kHz"; 4->"120kHz"; 5->"240kHz"; else->"Unknown" }
        val mimoStr = when(f.maxMimo) { 0->"Not Supported"; 1->"2x2"; 2->"4x4"; 3->"8x8"; else->"Unknown" }
        val modStr = when(f.maxModOrder) { 0->"Not Supported"; 1->"QAM64"; 2->"QAM256"; else->"Unknown" }
        val bw90Str = if (f.bw90MHzSupported) "Y" else "N"
        return "$scsStr, $mimoStr, ${f.maxBw} MHz, $modStr, 90MHz: $bw90Str"
    }

    fun getNrUlFeatureStr(f: ShannonFeatureSetUlPerCCNr): String {
        val scsStr = when(f.maxScs) { 1->"15kHz"; 2->"30kHz"; 3->"60kHz"; 4->"120kHz"; 5->"240kHz"; else->"Unknown" }
        val mimoStr = when(f.maxMimoCb) { 0->"Not Supported"; 1->"No"; 2->"Yes"; else->"Unknown" }
        val modStr = when(f.maxModOrder) { 0->"Not Supported"; 1->"QAM64"; 2->"QAM256"; else->"Unknown" }
        val bw90Str = if (f.bw90MHzSupported) "Y" else "N"
        return "$scsStr, ULMIMO: $mimoStr, ${f.maxBw} MHz, $modStr, 90MHz: $bw90Str"
    }

    fun getLteMimoStr(featureIndex: Int, isDl: Boolean): String {
        if (featureIndex <= 0) return "None"
        return if (isDl) {
            when (featureIndex) {
                5 -> "4+2"
                6 -> "2+2"
                else -> if (featureIndex % 2 == 0) "4x4" else "2x2"
            }
        } else {
            if (featureIndex == 1) "1x1" else "2x2"
        }
    }

    fun updateFeatureId(groupIndex: Int, comboIndex: Int, ccIndex: Int, isDl: Boolean, newFeatureId: Int, isNr: Boolean) {
        val current = _uecapsData.value ?: return
        val group = current.getComboGroups(groupIndex)
        val combo = group.getCombo(comboIndex)
        val cc = combo.getCc(ccIndex).toBuilder()

        if (isNr) {
            if (isDl) cc.dlFeaturePerCCIds = com.google.protobuf.ByteString.copyFrom(byteArrayOf((newFeatureId + 1).toByte()))
            else cc.ulFeaturePerCCIds = com.google.protobuf.ByteString.copyFrom(byteArrayOf((newFeatureId + 1).toByte()))
        } else {
            if (isDl) cc.dlFeatureIndex = newFeatureId else cc.ulFeatureIndex = newFeatureId
        }

        val updatedCombo = combo.toBuilder().setCc(ccIndex, cc.build()).build()
        val updatedGroup = group.toBuilder().setCombo(comboIndex, updatedCombo).build()
        _uecapsData.value = current.toBuilder().setComboGroups(groupIndex, updatedGroup).build()
    }

    // 🚀 新增你提供的 LTE 計算邏輯
    private fun calcLteBwClass(count: Int): Int {
        if (count <= 0) return 0
        if (count == 1) return 1
        return count + 1
    }

    private fun calcLteDlIndex(count: Int, mimo: Int): Int {
        if (count <= 0 || mimo <= 0) return 0
        val base = (count - 1) * 2
        return if (mimo >= 4) base + 2 else base + 1
    }

    fun copyCombo(groupIndex: Int, comboIndex: Int) {
        val current = _uecapsData.value ?: return
        val group = current.getComboGroups(groupIndex)
        val newGroup = ComboGroup.newBuilder().setComboHeader(group.comboHeader).addCombo(group.getCombo(comboIndex)).build()
        _uecapsData.value = current.toBuilder().addComboGroups(0, newGroup).build()
    }

    fun removeCombo(groupIndex: Int, comboIndex: Int) {
        val current = _uecapsData.value ?: return
        val groupBuilder = current.getComboGroups(groupIndex).toBuilder()
        groupBuilder.removeCombo(comboIndex)
        val mainBuilder = current.toBuilder()
        if (groupBuilder.comboCount == 0) mainBuilder.removeComboGroups(groupIndex)
        else mainBuilder.setComboGroups(groupIndex, groupBuilder.build())
        _uecapsData.value = mainBuilder.build()
    }
}