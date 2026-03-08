package com.example.coolbox.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coolbox.mobile.data.AppDatabase
import com.example.coolbox.mobile.data.FoodEntity
import com.example.coolbox.mobile.util.NlpParser
import com.example.coolbox.mobile.util.ParsedResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.UUID

enum class SortType { NAME, EXPIRY }
enum class SortOrder { ASCENDING, DESCENDING }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _parsedResult = MutableStateFlow<ParsedResult?>(null)
    val parsedResult: StateFlow<ParsedResult?> = _parsedResult.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _dbVersion = MutableStateFlow(0)
    
    private val _sortType = MutableStateFlow(SortType.EXPIRY)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASCENDING)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allItems = combine(_dbVersion, _sortType, _sortOrder) { version, type, order ->
        Triple(version, type, order)
    }.flatMapLatest { (_, type, order) ->
        AppDatabase.getDatabase(application).foodDao().getAllItems().map { list ->
            when (type) {
                SortType.NAME -> if (order == SortOrder.ASCENDING) list.sortedBy { it.name } else list.sortedByDescending { it.name }
                SortType.EXPIRY -> if (order == SortOrder.ASCENDING) list.sortedBy { it.expiryDateMs } else list.sortedByDescending { it.expiryDateMs }
            }
        }
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
    }

    fun syncAndRefresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        
        val serverUrl = SettingsManager.getServerUrl(getApplication())
        com.example.coolbox.mobile.util.CloudSyncManager.syncBidirectional(getApplication(), serverUrl) { success ->
            if (success) {
                // Increment version to trigger flatMapLatest and re-fetch from the merged DB
                _dbVersion.value += 1
                com.example.coolbox.mobile.util.ToastHelper.show(getApplication(), "双向同步完成 ✅")
            } else {
                com.example.coolbox.mobile.util.ToastHelper.show(getApplication(), "同步失败，请检查网络或服务器")
            }
            _isRefreshing.value = false
        }
    }

    fun setRecordingState(isRecording: Boolean) {
        _isRecording.value = isRecording
        if (!isRecording) {
            _partialText.value = ""
        }
    }

    fun updatePartialText(text: String) {
        _partialText.value = text
    }

    fun processVoiceInput(text: String) {
        val parsed = NlpParser.parse(text)
        _parsedResult.value = parsed
    }

    fun clearParsedResult() {
        _parsedResult.value = null
    }

    fun confirmAndSaveFood(result: ParsedResult) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(getApplication()).foodDao()
            val entity = FoodEntity(
                id = UUID.randomUUID().toString(),
                icon = "", 
                name = result.name,
                fridgeName = result.location,
                inputDateMs = System.currentTimeMillis(),
                expiryDateMs = result.expiryMs,
                quantity = result.quantity,
                weightPerPortion = result.quantity,
                portions = result.portions,
                category = "未分类",
                unit = result.unit,
                remark = result.remark,
                lastModifiedMs = System.currentTimeMillis(),
                isDeleted = false
            )
            dao.insertItem(entity)
            _parsedResult.value = null
            
            // Trigger Sync (Upload)
            val serverUrl = SettingsManager.getServerUrl(getApplication())
            com.example.coolbox.mobile.util.CloudSyncManager.uploadDatabase(getApplication(), serverUrl)
        }
    }

    fun deleteFood(entity: FoodEntity) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(getApplication()).foodDao()
            dao.softDelete(entity.id, System.currentTimeMillis())
            
            // Trigger Sync (Upload)
            val serverUrl = SettingsManager.getServerUrl(getApplication())
            com.example.coolbox.mobile.util.CloudSyncManager.uploadDatabase(getApplication(), serverUrl)
        }
    }
}
