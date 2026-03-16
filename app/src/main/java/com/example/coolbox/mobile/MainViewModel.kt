package com.example.coolbox.mobile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coolbox.mobile.data.FoodEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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

    // 新增：向 UI 暴露当前的排序模式和方向，用来显示 ↑ 或 ↓
    private val _currentSortMode = MutableStateFlow(SortMode.EXPIRY)
    val currentSortMode: StateFlow<SortMode> = _currentSortMode

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending

    private var rawItems = emptyList<FoodEntity>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // 核心改动：点击同样的维度就反转方向，点击新维度就默认升序
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
        
        val filteredItems = if (query.isEmpty()) {
            rawItems
        } else {
            rawItems.filter {
                (it.name?.lowercase()?.contains(query) == true) ||
                (it.note?.lowercase()?.contains(query) == true) ||
                (it.fridgeName?.lowercase()?.contains(query) == true) ||
                (it.category?.lowercase()?.contains(query) == true)
            }
        }

        val sorted = when (_currentSortMode.value) {
            SortMode.EXPIRY -> filteredItems.sortedBy { it.expiryDateMs }
            SortMode.LOCATION -> filteredItems.sortedWith(compareBy({ it.fridgeName }, { it.category }, { it.expiryDateMs }))
            SortMode.NAME -> filteredItems.sortedBy { it.name }
        }
        
        // 根据升降序标志位，决定要不要反转列表
        _inventory.value = if (_isAscending.value) sorted else sorted.reversed()
    }

    fun fetchInventory() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _statusMessage.value = "正在连接 NAS拉取数据..."

        val serverUrl = SettingsManager.getServerUrl(getApplication())
        val nasBase = if (serverUrl.contains("/coolbox")) serverUrl else "${serverUrl.removeSuffix("/")}/coolbox"
        val url = "$nasBase/sync/list"

        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = response.body?.string() ?: ""
                            val type = object : TypeToken<List<FoodEntity>>() {}.type
                            gson.fromJson<List<FoodEntity>>(json, type) ?: emptyList()
                        } else {
                            throw Exception("NAS 拒绝请求: HTTP ${response.code}")
                        }
                    }
                }
                rawItems = items
                applyFilterAndSort() 
                _statusMessage.value = if (items.isEmpty()) "NAS 返回了空数据" else "拉取成功！"
            } catch (e: Exception) {
                rawItems = emptyList()
                applyFilterAndSort()
                _statusMessage.value = "连接失败: ${e.message ?: "未知网络错误"}"
                Log.e("CoolBoxDumb", "Fetch Exception", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}