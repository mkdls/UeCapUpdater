package com.pixelthings.uecapupdater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RootStatus {
    Checking,
    Granted,
    Denied,
}

class RootStatusViewModel : ViewModel() {

    private val _rootStatus = MutableStateFlow(RootStatus.Checking)
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    init {
        checkRootAccess()
    }

    fun checkRootAccess() {
        viewModelScope.launch {
            _rootStatus.value = RootStatus.Checking
            val granted = withContext(Dispatchers.IO) {
                // 修正：主動獲取 Shell 並檢查 isRoot，這會確實喚起 Root 授權視窗
                Shell.getShell().isRoot
            }
            _rootStatus.value = if (granted) RootStatus.Granted else RootStatus.Denied
        }
    }
}