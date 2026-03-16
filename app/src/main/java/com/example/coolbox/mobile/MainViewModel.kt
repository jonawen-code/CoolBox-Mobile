// Version: V3.0.0-Pre23
// Build: 2.7.4 UI Alignment
package com.example.coolbox.mobile

import android.app.Application
import android.util.Log
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
import com.example.coolbox.mobile.util.NaturalSortUtils.sortedNaturally
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SortType { NAME, EXPIRY }
enum class SortOrder { ASCENDING, DESCENDING }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSetupComplete = MutableStateFlow(SettingsManager.isSetupComplete(application))
    val isSetupComplete: StateFlow<Boolean> = _isSetupComplete.asStateFlow()

    private val _fridgeBases = MutableStateFlow(SettingsManager.getFridgeBases(application))
    val fridgeBases: StateFlow<List<String>> = _fridgeBases.asStateFlow()

    private var syncJob: kotlinx.coroutines.Job? = null

    fun scheduleSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // 2 second debounce
            val serverUrl = SettingsManager.getServerUrl(getApplication())
            if (serverUrl.isNotBlank()) {
                com.example.coolbox.mobile.util.CloudSyncManager.uploadDatabase(getApplication(), serverUrl) { success ->
                    if (success) {
                        com.example.coolbox.mobile.util.ToastHelper.show(getApplication(), "后台自动同步成功 ✅")
                    } else {
                        com.example.coolbox.mobile.util.ToastHelper.show(getApplication(), "同步失败，请检查网络")
                    }
                }
            }
        }
    }

    private val _categories = MutableStateFlow(SettingsManager.getCategories(application))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _fridges = MutableStateFlow(SettingsManager.getFridges(application))
    val fridges: StateFlow<List<String>> = _fridges.asStateFlow()

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
            val safeList = list.filterNotNull()
            when (type) {
                SortType.NAME -> if (order == SortOrder.ASCENDING) safeList.sortedBy { it.name } else safeList.sortedByDescending { it.name }
                SortType.EXPIRY -> if (order == SortOrder.ASCENDING) safeList.sortedBy { it.expiryDateMs } else safeList.sortedByDescending { it.expiryDateMs }
            }
        }
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
    }

    // Dynamic Device-Zone Mapping (V2.2)
    private val _deviceZoneMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val deviceZoneMap: StateFlow<Map<String, List<String>>> = _deviceZoneMap

    fun refreshDynamicData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // V3.0.0-Pre25: Force normalize config before mapping to ensure consistency
            SettingsManager.normalizeAllKeys(getApplication())
            
            val bases = SettingsManager.getFridgeBases(getApplication())
            val allFullNames = SettingsManager.getFridges(getApplication())
            
            val mapping = LinkedHashMap<String, MutableList<String>>()
            bases.forEach { base ->
                val zones = allFullNames.filter { it.startsWith(base) }
                    .map { it.removePrefix(base).replace(Regex("^\\s*-\\s*"), "").trim() }
                    .filter { it.isNotEmpty() }
                    .sortedBy { com.example.coolbox.mobile.util.NaturalSortUtils.normalizeForSort(it) } // Double sort safety
                    .sortedNaturally()
                mapping[base] = zones.toMutableList() // Always preserve the base
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _deviceZoneMap.value = mapping
            }
        }
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

    fun refreshConfig() {
        val application = getApplication<Application>()
        _fridges.value = SettingsManager.getFridges(application)
        _categories.value = SettingsManager.getCategories(application)
        _fridgeBases.value = SettingsManager.getFridgeBases(application)
        _dbVersion.value += 1 // Architect's requirement: Trigger UI re-sort
    }

    fun takeoverFromNas(serverUrl: String, onComplete: (Boolean) -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        
        val context = getApplication<Application>()
        SettingsManager.setServerUrl(context, serverUrl)
        
        viewModelScope.launch {
            try {
                // V3.0.0-Pre26: Strict Coroutine Atomic Flow
                // Step 1: Download full configuration first (Suspend)
                val configSuccess = com.example.coolbox.mobile.util.CloudSyncManager.downloadConfigSuspend(context, serverUrl)
                
                if (configSuccess) {
                    // Step 2: Push to local StateFlows immediately
                    withContext(Dispatchers.Main) { refreshConfig() }
                    
                    // Step 3: Now sync the actual items (Wait for completion)
                    // Note: CloudSyncManager.syncBidirectional is still callback-based in some parts, 
                    // but we call it here and wait for its completion logic.
                    // Improving: We'll wrap the item sync in a suspend context as well if needed, 
                    // but for now we follow the atomic chain.
                    com.example.coolbox.mobile.util.CloudSyncManager.syncBidirectional(context, serverUrl) { itemsSuccess ->
                        viewModelScope.launch {
                            _isSetupComplete.value = true
                            refreshConfig() // Refresh again after items arrive to update mapping
                            refreshDynamicData()
                            SettingsManager.clearLegacyKeys(context) // Final cleanup
                            _isRefreshing.value = false
                            onComplete(itemsSuccess)
                        }
                    }
                } else {
                    // Fallback to legacy extraction if config pull fails
                    com.example.coolbox.mobile.util.CloudSyncManager.syncBidirectional(context, serverUrl) { itemsSuccess ->
                        viewModelScope.launch {
                            if (itemsSuccess) {
                                withContext(Dispatchers.IO) {
                                    val dao = AppDatabase.getDatabase(context).foodDao()
                                    val allItems = dao.getAllItemsSync()
                                    val locations = allItems.map { it.fridgeName }.filter { it.isNotBlank() }.distinct()
                                    val categories = allItems.map { it.category }.filter { it.isNotBlank() }.distinct()
                                    val bases = locations.map { if (it.contains(" - ")) it.split(" - ")[0] else it }.distinct()
                                    val caps = locations.associateWith { if (it.contains("冻")) "冷冻" else "冷藏" }
                                    withContext(Dispatchers.Main) {
                                        completeSetup(
                                            finalLocations = locations,
                                            baseNames = bases,
                                            capabilities = caps,
                                            newCategories = categories.ifEmpty { listOf("肉蛋水产", "奶品饮料", "速冻食品", "蔬菜水果", "熟食剩菜") },
                                            syncUrl = serverUrl
                                        )
                                    }
                                }
                            }
                            _isRefreshing.value = false
                            onComplete(itemsSuccess)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CoolBox", "Takeover failed", e)
                _isRefreshing.value = false
                onComplete(false)
            }
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

    fun editFood(entity: FoodEntity) {
        _parsedResult.value = ParsedResult(
            id = entity.id,
            name = entity.name,
            quantity = entity.quantity,
            unit = entity.unit ?: "个",
            location = entity.fridgeName,
            expiryMs = entity.expiryDateMs,
            remark = entity.remark,
            portions = entity.portions,
            category = entity.category
        )
    }

    private val _iconMap = mapOf(
        "牛肉" to "ic_food_beef",
        "猪肉" to "ic_food_pork",
        "排骨" to "ic_food_ribs",
        "鸡" to "ic_food_chicken",
        "鱼" to "ic_food_fish",
        "虾" to "ic_food_shrimp",
        "蟹" to "ic_food_crab",
        "水饺" to "ic_food_dumpling",
        "汤圆" to "ic_food_dumpling",
        "速冻食品" to "ic_food_dumpling",
        "冻榴莲果肉" to "ic_food_durian",
        "鲜奶" to "ic_food_milk",
        "牛奶" to "ic_food_milk",
        "黄油" to "ic_food_butter",
        "奶酪" to "ic_food_cheese",
        "橙汁" to "ic_food_juice",
        "果汁" to "ic_food_juice",
        "柠檬" to "ic_food_lemon",
        "汽水" to "ic_food_cola",
        "啤酒" to "ic_food_beer",
        "苹果" to "ic_food_apple",
        "橙" to "ic_food_tangerine",
        "蓝莓" to "ic_food_blueberries",
        "草莓" to "ic_food_strawberry",
        "西瓜" to "ic_food_watermelon",
        "番茄" to "ic_food_tomato",
        "辣椒" to "ic_food_pepper",
        "白菜" to "ic_food_lettuce",
        "菜心" to "ic_food_broccoli",
        "绿叶菜" to "ic_food_lettuce",
        "剩菜" to "ic_food_cooked",
        "熟食剩菜" to "ic_food_cooked",
        "肉蛋水产" to "ic_food_beef",
        "奶品饮料" to "ic_food_milk",
        "蔬菜水果" to "ic_food_lettuce",
        "鸡蛋" to "ic_food_egg",
        "贝类" to "ic_food_shellfish",
        "青口" to "ic_food_mussel",
        "海鲜" to "ic_food_shellfish"
    )

    fun getIconForName(name: String): String {
        val found = _iconMap.entries.find { name.contains(it.key) }?.value
        return if (found.isNullOrBlank()) com.example.coolbox.mobile.util.Constants.ICON_DEFAULT else found
    }

    fun confirmAndSaveFood(result: ParsedResult) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(getApplication()).foodDao()
            val entity = FoodEntity(
                id = result.id ?: UUID.randomUUID().toString(), 
                icon = getIconForName(result.name), 
                name = result.name,
                fridgeName = result.location,
                inputDateMs = System.currentTimeMillis(),
                expiryDateMs = result.expiryMs,
                quantity = result.quantity,
                weightPerPortion = result.quantity,
                portions = result.portions,
                category = result.category,
                unit = result.unit,
                remark = result.remark,
                lastModifiedMs = System.currentTimeMillis(),
                isDeleted = false
            )
            dao.insertItem(entity)
            _parsedResult.value = null
            
            scheduleSync()
        }
    }

    fun updateFoodIcon(entity: FoodEntity, newIcon: String) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(getApplication()).foodDao()
            val updated = entity.copy(icon = newIcon, lastModifiedMs = System.currentTimeMillis())
            dao.insertItem(updated)
            
            scheduleSync()
        }
    }

    fun deleteFood(entity: FoodEntity) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(getApplication()).foodDao()
            dao.softDelete(entity.id, System.currentTimeMillis())
            
            scheduleSync()
        }
    }

    fun takePortion(entity: FoodEntity, count: Int = 1) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(getApplication()).foodDao()
            if (entity.portions > count) {
                val newQuantity = entity.quantity - (entity.weightPerPortion * count)
                val newPortions = entity.portions - count
                val updated = entity.copy(
                    quantity = newQuantity,
                    portions = newPortions,
                    lastModifiedMs = System.currentTimeMillis()
                )
                dao.insertItem(updated)
            } else {
                // Take all
                dao.softDelete(entity.id, System.currentTimeMillis())
            }
            
            scheduleSync()
        }
    }

    fun transferItem(entity: FoodEntity, targetFridge: String) {
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(getApplication()).foodDao()
            val updated = entity.copy(
                fridgeName = targetFridge,
                lastModifiedMs = System.currentTimeMillis()
            )
            dao.insertItem(updated)
            
            scheduleSync()
        }
    }

    fun completeSetup(
        finalLocations: List<String>,
        baseNames: List<String>,
        capabilities: Map<String, String>,
        newCategories: List<String>,
        syncUrl: String
    ) {
        val context = getApplication<Application>()
        SettingsManager.setSetupComplete(context, true)
        SettingsManager.setFridges(context, finalLocations)
        SettingsManager.setFridgeBases(context, baseNames)
        SettingsManager.setFridgeCaps(context, capabilities)
        SettingsManager.setCategories(context, newCategories)
        SettingsManager.setServerUrl(context, syncUrl)

        _isSetupComplete.value = true
        _fridgeBases.value = baseNames
        _categories.value = newCategories
        _fridges.value = finalLocations
        
        _dbVersion.value += 1 // Ensure instant UI refresh
        refreshDynamicData()
    }

    fun toggleSetup(complete: Boolean) {
        _isSetupComplete.value = complete
    }

    init {
        refreshDynamicData()
        // Migration: Emoji clean (V2.1) and Icon clean (V2.2/ Build 2.0.0)
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(application).foodDao()
            val all = dao.getAllItemsSync()
            val emojiUpdates = all.filter { it.category.contains(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]")) }.map {
                it.copy(
                    category = it.category.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]"), "").trim(),
                    lastModifiedMs = System.currentTimeMillis()
                )
            }
            if (emojiUpdates.isNotEmpty()) {
                dao.insertItems(emojiUpdates)
            }
            
            // V3.0.0-Pre24: Full Configuration & Data Normalization Lock
            SettingsManager.normalizeAllKeys(application)
            
            val hierarchyUpdates = all.map { entity ->
                val oldName = entity.fridgeName
                val newName = com.example.coolbox.mobile.util.NaturalSortUtils.normalizeHierarchyFormat(oldName)
                if (oldName != newName) {
                    entity.copy(fridgeName = newName, lastModifiedMs = System.currentTimeMillis())
                } else null
            }.filterNotNull()
            
            if (hierarchyUpdates.isNotEmpty()) {
                Log.d("MainViewModel", "Normalized ${hierarchyUpdates.size} hierarchy strings to standard [Device] - [Layer] format")
                dao.insertItems(hierarchyUpdates)
            }

            AppDatabase.exportDatabase(application)
            scheduleSync()
        }
    }
}
