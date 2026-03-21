package com.example.coolbox.mobile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coolbox.mobile.data.FoodEntity
import com.example.coolbox.mobile.data.AppDatabase
import com.example.coolbox.mobile.util.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SortMode { EXPIRY, LOCATION, NAME }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _inventory = MutableStateFlow<List<FoodEntity>>(emptyList())
    val inventory: StateFlow<List<FoodEntity>> = _inventory

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _statusMessage = MutableStateFlow<String>("等待确认服务器地址...")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _currentSortMode = MutableStateFlow(SortMode.EXPIRY)
    val currentSortMode: StateFlow<SortMode> = _currentSortMode

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending

    private var rawItems = emptyList<FoodEntity>()

    init {
        loadLocalInventory()
    }

    // 逻辑：只读本地，不碰网络
    fun loadLocalInventory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                val fetched = db.foodDao().getAllVisibleItems()
                rawItems = fetched
                applyFilterAndSort()
                _statusMessage.value = if (rawItems.isEmpty()) "本地暂无数据" else "同步成功"
            } catch (e: Exception) {
                Log.e("CoolBoxDumb", "Load Local Error (Fix with AI)", e)
                // 终极恢复：删除损坏的 DB 文件并重建，确保 App 能正常进入
                try {
                    AppDatabase.deleteAndReset(getApplication())
                    val db = AppDatabase.getDatabase(getApplication())
                    rawItems = db.foodDao().getAllVisibleItems()
                    applyFilterAndSort()
                    _statusMessage.value = "DB已重置，请重新同步"
                } catch (e2: Exception) {
                    Log.e("CoolBoxDumb", "Reset also failed", e2)
                    _statusMessage.value = "加载失败，请重启应用"
                }
            }
        }
    }

    // 逻辑：触发 SQL 注入同步，完成后直接刷新本地数据
    fun fetchInventory() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _statusMessage.value = "正在拉取数据库..."

        val serverUrl = SettingsManager.getServerUrl(getApplication()).trim().removeSuffix("/")

        viewModelScope.launch(Dispatchers.IO) {
            CloudSyncManager.downloadDatabase(getApplication(), serverUrl) { success, msg ->
                viewModelScope.launch {
                    if (success) {
                        _statusMessage.value = "同步成功，刷新中..."
                        loadLocalInventory() // SQL 注入完成后直接读取，无需重启进程
                    } else {
                        _statusMessage.value = "同步失败: $msg"
                    }
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        if (_currentSortMode.value == mode) {
            _isAscending.value = !_isAscending.value
        } else {
            _currentSortMode.value = mode
            _isAscending.value = true
        }
        applyFilterAndSort()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        val query = _searchQuery.value.trim().lowercase()
        var items = if (query.isEmpty()) {
            rawItems
        } else {
            rawItems.filter {
                it.name.lowercase().contains(query) || it.fridgeName.lowercase().contains(query) || it.remark.lowercase().contains(query)
            }
        }

        val now = System.currentTimeMillis()
        
        // 1. 先进行用户指定的排序
        items = when (_currentSortMode.value) {
            SortMode.EXPIRY -> items.sortedBy { it.expiryDateMs }
            SortMode.LOCATION -> items.sortedBy { it.fridgeName }
            SortMode.NAME -> items.sortedBy { it.name }
        }
        
        val sortedByPreference = if (_isAscending.value) items else items.reversed()

        // 2. 强制过期食品置顶 (在用户排序的基础上)
        val expired = sortedByPreference.filter { it.expiryDateMs < now }
        val normal = sortedByPreference.filter { it.expiryDateMs >= now }
        
        _inventory.value = expired + normal
    }
}