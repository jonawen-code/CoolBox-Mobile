// Version: V3.0.0-Pre22
package com.example.coolbox.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.coolbox.mobile.util.SystemASRHelper
import com.example.coolbox.mobile.util.ParsedResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val viewModel: MainViewModel by viewModels()
    private var asrHelper: SystemASRHelper? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Record Audio permission granted")
        } else {
            Toast.makeText(this, "需要麦克风权限才能进行语音识别", Toast.LENGTH_LONG).show()
        }
    }

    private var onSpeechDone: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Attempt to catch all uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("CRASH_HANDLER", "Uncaught exception", throwable)
        }

        try {
            tts = TextToSpeech(this, this)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { onSpeechDone?.invoke() }
                }
                override fun onError(utteranceId: String?) {}
            })
            asrHelper = SystemASRHelper(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Init error", e)
        }

        setContent {
            PremiumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isSetupComplete by viewModel.isSetupComplete.collectAsState()
                    var showSettings by remember { mutableStateOf(false) }

                    if (!isSetupComplete) {
                        SetupScreen(viewModel = viewModel)
                    } else if (showSettings) {
                        SettingsScreen(onBack = { showSettings = false }, viewModel = viewModel)
                    } else {
                        val parsedResult by viewModel.parsedResult.collectAsState()
                        
                        MainScreen(
                            viewModel = viewModel,
                            onSettingsClick = { showSettings = true }
                        )
                        
                        parsedResult?.let { result ->
                            EditFoodDialog(
                                result = result,
                                viewModel = viewModel,
                                onDismiss = { viewModel.clearParsedResult() },
                                onConfirm = { updated -> viewModel.confirmAndSaveFood(updated) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINESE
            isTtsReady = true
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        asrHelper?.release()
        tts?.stop()
        tts?.shutdown()
    }

@Composable
fun PremiumTheme(content: @Composable () -> Unit) {
    val premiumColorScheme = lightColorScheme(
        primary = Color(0xFF03A9F4),
        secondary = Color(0xFF0288D1),
        tertiary = Color(0xFF00897B),
        background = Color(0xFFF8F9FA),
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
    )
    
    MaterialTheme(
        colorScheme = premiumColorScheme,
        typography = Typography(), // Can be customized further
        content = content
    )
}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit
) {
    val allItems by viewModel.allItems.collectAsState(initial = emptyList())
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    var showSearchDialog by remember { mutableStateOf(false) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var entryText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.syncAndRefresh() }
    )

    var selectedItemForIcon by remember { mutableStateOf<com.example.coolbox.mobile.data.FoodEntity?>(null) }

    // Handle back gesture to prevent immediate app exit if a dialog or search is active
    BackHandler(enabled = showSearchDialog || showEntryDialog || searchQuery.isNotBlank() || selectedItemForIcon != null) {
        when {
            showSearchDialog -> showSearchDialog = false
            showEntryDialog -> showEntryDialog = false
            selectedItemForIcon != null -> selectedItemForIcon = null
            searchQuery.isNotBlank() -> searchQuery = ""
        }
    }

    Scaffold(
        topBar = {
            val context = LocalContext.current
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "v?.?.?"
                }
            }
            var showSortMenu by remember { mutableStateOf(false) }
            val sortType by viewModel.sortType.collectAsState()
            val sortOrder by viewModel.sortOrder.collectAsState()

            TopAppBar(
                title = { 
                    Column {
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                        Text("CoolBox Mobile", fontWeight = FontWeight.Black, letterSpacing = 1.sp) 
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(painter = painterResource(id = R.drawable.ic_sort), contentDescription = "排序", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    // ... existing DropdownMenu logic ...
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("按名称 ${if (sortType == SortType.NAME) "✅" else ""}") },
                            onClick = { viewModel.setSortType(SortType.NAME); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("按保质期 ${if (sortType == SortType.EXPIRY) "✅" else ""}") },
                            onClick = { viewModel.setSortType(SortType.EXPIRY); showSortMenu = false }
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("切换升序/降序 (${if (sortOrder == SortOrder.ASCENDING) "当前: 升序" else "当前: 降序"})") },
                            onClick = { viewModel.toggleSortOrder(); showSortMenu = false }
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1A237E)
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).pullRefresh(pullRefreshState)) {


            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Inventory List (Sorted by ViewModel) ---
                val displayedItems = if (searchQuery.isNotBlank()) {
                    allItems.filter { it.name.contains(searchQuery, ignoreCase = true) || it.remark.contains(searchQuery, ignoreCase = true) }
                } else {
                    allItems
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("最近库存 (首页下拉同步)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF444444))
                    if (searchQuery.isNotBlank()) {
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("清除筛选", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                
                if (displayedItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val emptyResId = LocalContext.current.resources.getIdentifier("empty_fridge", "drawable", LocalContext.current.packageName)
                            if (emptyResId != 0) {
                                Image(
                                    painter = painterResource(id = emptyResId),
                                    contentDescription = null,
                                    modifier = Modifier.size(200.dp).padding(bottom = 16.dp),
                                    alpha = 0.7f
                                )
                            }
                            Text(
                                text = if (searchQuery.isBlank()) "您的冰箱空空如也\n尝试点击上方同步或添加食品" else "未找到相关食品",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(displayedItems, key = { it?.id ?: UUID.randomUUID().toString() }) { item ->
                            AnimatedVisibility(
                                visible = true,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                FoodItemCard(item, viewModel, onIconClick = { selectedItemForIcon = item })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- v1.0.30 Premium Action Bar ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search Button (Beige with Orange border)
                    Surface(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFE8EAF6),
                        border = BorderStroke(1.dp, Color(0xFFC5CAE9))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text("🔍", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (searchQuery.isEmpty()) "搜索库存" else "搜索中: $searchQuery", color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Entry Button (Bright Yellow)
                    Button(
                        onClick = { showEntryDialog = true },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎤", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("语音录入", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            // --- Pull Refresh Indicator ---
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )

            // --- Icon Picker Dialog ---
            selectedItemForIcon?.let { entity ->
                IconPicker(
                    onDismiss = { selectedItemForIcon = null },
                    onIconSelected = { newIcon ->
                        viewModel.updateFoodIcon(entity, newIcon)
                        selectedItemForIcon = null
                    }
                )
            }
            
            // --- Search Dialog ---
            if (showSearchDialog) {
                var tempQuery by remember { mutableStateOf(searchQuery) }
                val searchFocusRequester = remember { FocusRequester() }

                AlertDialog(
                    onDismissRequest = { showSearchDialog = false },
                    title = { Text("搜索库存") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = tempQuery,
                                onValueChange = { tempQuery = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                                label = { Text("输入食品名称或备注") },
                                singleLine = true
                            )
                        }
                        LaunchedEffect(Unit) {
                            searchFocusRequester.requestFocus()
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            searchQuery = tempQuery
                            showSearchDialog = false
                        }) {
                            Text("搜索")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            tempQuery = ""
                            searchQuery = ""
                            showSearchDialog = false 
                        }) {
                            Text("清除筛选")
                        }
                    }
                )
            }
            
            // --- Robust Entry Dialog ---
            if (showEntryDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showEntryDialog = false
                        entryText = ""
                    },
                    title = { Text("智能录入") },
                    text = {
                        Column {
                            Text("请在大键盘上点击【语音图标】说话，或直接输入食品信息：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = entryText,
                                onValueChange = { entryText = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                label = { Text("智能文本/语音输入") },
                                placeholder = { Text("例如：买了两瓶高钙牛奶放在大冰箱冷藏室") }
                            )
                        }
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (entryText.isNotBlank()) {
                                val textToParse = entryText
                                showEntryDialog = false
                                entryText = ""
                                viewModel.processVoiceInput(textToParse)
                            }
                        }) {
                            Text("解析存档")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showEntryDialog = false 
                            entryText = ""
                        }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf(SettingsManager.getServerUrl(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        BackHandler(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
        ) {
            Text("远程同步与备份", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("例如: http://192.168.31.94:3000/coolbox") }
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        viewModel.syncAndRefresh() 
                        Toast.makeText(context, "开始同步...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("开始同步")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.toggleSetup(false)
                        onBack()
                    }
                ) {
                    Text("重置并配置设备")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("关于 CoolBox Mobile ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun IconPicker(onDismiss: () -> Unit, onIconSelected: (String) -> Unit) {
    val iconList = listOf(
        "ic_food_beef", "ic_food_pork", "ic_food_chicken", "ic_food_egg",
        "ic_food_fish", "ic_food_shrimp", "ic_food_crab", "ic_food_ribs",
        "ic_food_apple", "ic_food_tomato", "ic_food_watermelon", "ic_food_grapes",
        "ic_food_strawberry", "ic_food_onion", "ic_food_lettuce", "ic_food_milk",
        "ic_food_yogurt", "ic_food_juice", "ic_food_cola", "ic_food_beer",
        "ic_food_icecream", "ic_food_durian", "ic_food_blueberries", "ic_food_broccoli", "ic_food_butter",
        "ic_food_cheese", "ic_food_dumpling", "ic_food_green_apple", "ic_food_pepper",
        "ic_food_lemon", "ic_food_mango", "ic_food_pear", "ic_food_tangerine",
        "ic_food_shellfish", "ic_food_mussel",
        "cat_cooked", "cat_meat", "cat_veg", "cat_drink", "cat_snack"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更换图标") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(400.dp)
            ) {
                items(iconList) { iconName ->
                    val context = LocalContext.current
                    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                    if (resId != 0) {
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = iconName,
                            modifier = Modifier.size(64.dp).padding(8.dp).pointerInput(Unit) {
                                detectTapGestures(onTap = { onIconSelected(iconName) })
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodDialog(
    result: ParsedResult,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onConfirm: (ParsedResult) -> Unit
) {
    var name by remember { mutableStateOf(result.name) }
    var remark by remember { mutableStateOf(result.remark) }
    var quantity by remember { mutableStateOf(result.quantity.toString()) }
    var unit by remember { mutableStateOf(result.unit) }
    var portions by remember { mutableStateOf(result.portions.toString()) }
    
    val deviceZoneMap by viewModel.deviceZoneMap.collectAsState()
    val allDevices = deviceZoneMap.keys.toList().ifEmpty { listOf("我的冰箱", "小冰柜") }
    var selectedDevice by remember { mutableStateOf("") }
    var selectedZone by remember { mutableStateOf("") }
    
    var originalDirtyLocation by remember { mutableStateOf("") }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(deviceZoneMap) {
        if (selectedDevice.isEmpty() && allDevices.isNotEmpty()) {
            selectedDevice = allDevices.first()
            selectedZone = deviceZoneMap[selectedDevice]?.firstOrNull() ?: ""
        }
    }

    LaunchedEffect(Unit, deviceZoneMap) {
        val loc = result.location.trim()
        if (loc.isNotEmpty()) {
            val parts = loc.split(" - ")
            if (parts.size == 2 && allDevices.contains(parts[0]) && deviceZoneMap[parts[0]]?.contains(parts[1]) == true) {
                selectedDevice = parts[0]
                selectedZone = parts[1]
            } else {
                // V1.x Legacy Smart Match
                var matched = false
                for (dev in allDevices) {
                    if (loc.startsWith(dev)) {
                        val zone = loc.removePrefix(dev).trim()
                        if (deviceZoneMap[dev]?.contains(zone) == true) {
                            selectedDevice = dev
                            selectedZone = zone
                            matched = true
                            break
                        }
                    }
                }
                
                if (!matched) {
                    selectedDevice = "历史存量"
                    selectedZone = "未归档"
                    originalDirtyLocation = loc
                    isDirty = true
                }
            }
        }
    }
    var category by remember { mutableStateOf(result.category) }
    
    val categories by viewModel.categories.collectAsState()
    var showCategoryMenu by remember { mutableStateOf(false) }
    
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var expiryStr by remember { mutableStateOf(sdf.format(Date(result.expiryMs))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("📝 核对录入", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { viewModel.syncAndRefresh() }) {
                    Text("🔄", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("食品名称") }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = remark, onValueChange = { remark = it }, label = { Text("备注/规格") }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("总量") }, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("单位") }, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(value = portions, onValueChange = { portions = it }, label = { Text("分割份数") }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                
                // --- Cascading Location Spinners (V2.1) ---
                var showDeviceMenu by remember { mutableStateOf(false) }
                var showZoneMenu by remember { mutableStateOf(false) }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedDevice, onValueChange = {}, readOnly = true, label = { Text("设备") },
                            modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            trailingIcon = { IconButton(onClick = { showDeviceMenu = true }) { Text("▼") } }, isError = isDirty
                        )
                        DropdownMenu(expanded = showDeviceMenu, onDismissRequest = { showDeviceMenu = false }, modifier = Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                            allDevices.forEach { dev ->
                                val isSelected = (selectedDevice == dev)
                                DropdownMenuItem(
                                    text = { Text(dev, color = if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black) },
                                    onClick = { 
                                        selectedDevice = dev
                                        selectedZone = deviceZoneMap[dev]?.firstOrNull() ?: ""
                                        isDirty = false
                                        showDeviceMenu = false 
                                    },
                                    modifier = Modifier.background(if (isSelected) androidx.compose.ui.graphics.Color(0xFF1A73E8) else androidx.compose.ui.graphics.Color.Transparent)
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedZone, onValueChange = {}, readOnly = true, label = { Text("层级/温区") },
                            modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            trailingIcon = { IconButton(onClick = { if (!isDirty) showZoneMenu = true }) { Text("▼") } }, isError = isDirty
                        )
                        DropdownMenu(expanded = showZoneMenu, onDismissRequest = { showZoneMenu = false }, modifier = Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                            val zones = deviceZoneMap[selectedDevice] ?: emptyList()
                            zones.forEach { zone ->
                                val isSelected = (selectedZone == zone)
                                DropdownMenuItem(
                                    text = { Text(zone, color = if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black) },
                                    onClick = { selectedZone = zone; showZoneMenu = false },
                                    modifier = Modifier.background(if (isSelected) androidx.compose.ui.graphics.Color(0xFF1A73E8) else androidx.compose.ui.graphics.Color.Transparent)
                                )
                            }
                        }
                    }
                }
                if (isDirty) {
                    Text("⚠️ 历史位置: $originalDirtyLocation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    Text("(由于配置已更迭，请确认或手动重新分类)", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("食品分类") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { showCategoryMenu = true }) {
                                Text("▼")
                            }
                        }
                    )
                    DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                        categories.forEach { cat ->
                            val isCatSelected = (category == cat)
                            DropdownMenuItem(
                                text = { Text(cat, color = if (isCatSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black) }, 
                                onClick = { category = cat; showCategoryMenu = false },
                                modifier = Modifier.background(if (isCatSelected) androidx.compose.ui.graphics.Color(0xFF1A73E8) else androidx.compose.ui.graphics.Color.Transparent)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = expiryStr, onValueChange = { expiryStr = it }, label = { Text("保质期至 (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                
                Text(
                    "💡 如果解析有误，请修改后点击确认。点右上角 🔄 可刷新云端数据。", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.secondary, 
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalExpiry = try { sdf.parse(expiryStr)?.time ?: result.expiryMs } catch(e: Exception) { result.expiryMs }
                    onConfirm(result.copy(
                        name = name,
                        remark = remark,
                        quantity = quantity.toDoubleOrNull() ?: result.quantity,
                        unit = unit,
                        portions = portions.toIntOrNull() ?: result.portions,
                        location = if (isDirty) originalDirtyLocation else "$selectedDevice - $selectedZone",
                        category = category,
                        expiryMs = finalExpiry
                    ))
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text("确认存档")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(1) } // 1: Basics, 2: Device Config, 3: Layers, 4: Summary

    // Handle system back gesture
    BackHandler(enabled = true) {
        if (step > 1) {
            if (step == 4) step = 2 else step--
        } else {
            // Can't go back from step 1 (mandatory setup)
            Toast.makeText(context, "请先完成基础设置", Toast.LENGTH_SHORT).show()
        }
    }

    var serverUrl by remember { mutableStateOf(SettingsManager.getServerUrl(context)) }
    if (serverUrl.isEmpty()) serverUrl = "http://192.168.31.94:3000/coolbox"
    
    val fridgeBaseNames = remember { mutableStateListOf<String>().apply { addAll(SettingsManager.getFridgeBases(context)) } }
    val categoriesList = remember { mutableStateListOf<String>().apply { addAll(SettingsManager.getCategories(context)) } }
    
    val finalLocations = remember { mutableStateListOf<String>() }
    val capabilities = remember { mutableStateMapOf<String, String>() }
    var currentBaseIdx by remember { mutableStateOf(0) }

    when (step) {
        1 -> {
            Scaffold(topBar = { TopAppBar(title = { Text("CoolBox 初始设置") }) }) { pv ->
                Column(modifier = Modifier.fillMaxSize().padding(pv).padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("欢迎使用 CoolBox Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("请逐个添加您的设备和分类 (V3.0.0-Pre22)", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    var showTakeoverDialog by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showTakeoverDialog = true },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("从 NAS 一键接管已有配置与数据", fontWeight = FontWeight.ExtraBold)
                    }

                    if (showTakeoverDialog) {
                        var tempUrl by remember { mutableStateOf(serverUrl) }
                        AlertDialog(
                            onDismissRequest = { showTakeoverDialog = false },
                            title = { Text("NAS 接管初始化") },
                            text = {
                                Column {
                                    Text("请输入 NAS 后端地址，App 将自动同步数据并完成配置接管。", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(value = tempUrl, onValueChange = { tempUrl = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showTakeoverDialog = false
                                    viewModel.takeoverFromNas(tempUrl) { success ->
                                        if (success) {
                                            Toast.makeText(context, "接管配置成功 🎉", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "连接失败，请检查 URL", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) { Text("立即接管") }
                            },
                            dismissButton = { TextButton(onClick = { showTakeoverDialog = false }) { Text("取消") } }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // --- Fridge Bases Add ---
                    Text("1. 存储设备 (如：大冰箱、小冰柜)", style = MaterialTheme.typography.titleSmall)
                    var newBase by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = newBase,
                        onValueChange = { newBase = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入设备名称") },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (newBase.isNotBlank() && !fridgeBaseNames.contains(newBase.trim())) {
                                    fridgeBaseNames.add(newBase.trim())
                                    newBase = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "添加")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        fridgeBaseNames.forEach { name ->
                            androidx.compose.material3.InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(name) },
                                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp).clickable { fridgeBaseNames.remove(name) }
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // --- Categories Add ---
                    Text("2. 食品分类 (如：蔬菜水果、肉蛋水产)", style = MaterialTheme.typography.titleSmall)
                    var newCat by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = newCat,
                        onValueChange = { newCat = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入分类名称") },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (newCat.isNotBlank() && !categoriesList.contains(newCat.trim())) {
                                    categoriesList.add(newCat.trim())
                                    newCat = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "添加")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categoriesList.forEach { name ->
                            androidx.compose.material3.InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(name) },
                                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp).clickable { categoriesList.remove(name) }
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("3. 远程同步地址", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            if (fridgeBaseNames.isEmpty()) {
                                Toast.makeText(context, "请添加至少一个设备", Toast.LENGTH_SHORT).show()
                            } else {
                                finalLocations.clear()
                                capabilities.clear()
                                currentBaseIdx = 0
                                step = 2
                            }
                        }, 
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("下一步：配置层级细节")
                    }
                }
            }
        }
        2 -> {
            val baseName = fridgeBaseNames[currentBaseIdx]
            var showLayerPicker by remember { mutableStateOf(false) }
            var layerType by remember { mutableStateOf("") } // Cold, Freeze, Both, Three
            var layerCountPrompt by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = {},
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step = step - 1 /* logic handles above */ }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "上一步")
                        }
                        Text("配置设备：$baseName")
                    }
                },
                text = {
                    Column {
                        val options = listOf("冰箱：仅冷藏室", "冰柜：仅冷冻仓 (多仓/层)", "常规冰箱：冷藏 + 冷冻", "三门冰箱：冷藏 + 微冻 + 冷冻")
                        options.forEach { opt ->
                            Button(
                                onClick = {
                                    when (opt) {
                                        "冰箱：仅冷藏室" -> {
                                            val loc = "$baseName 冷藏室"
                                            finalLocations.add(loc); capabilities[loc] = "冷藏"
                                            if (currentBaseIdx < fridgeBaseNames.size - 1) currentBaseIdx++ else step = 4
                                        }
                                        "冰柜：仅冷冻仓 (多仓/层)" -> {
                                            layerType = "ONLY_FREEZE"
                                            layerCountPrompt = "$baseName 有几个仓位/层？"
                                            showLayerPicker = true
                                        }
                                        "常规冰箱：冷藏 + 冷冻" -> {
                                            val loc = "$baseName 冷藏室"
                                            finalLocations.add(loc); capabilities[loc] = "冷藏"
                                            layerType = "NORMAL"
                                            layerCountPrompt = "$baseName 的冷冻室有几层？"
                                            showLayerPicker = true
                                        }
                                        "三门冰箱：冷藏 + 微冻 + 冷冻" -> {
                                            val rLoc = "$baseName 冷藏室"; finalLocations.add(rLoc); capabilities[rLoc] = "冷藏"
                                            val mLoc = "$baseName 微冻室"; finalLocations.add(mLoc); capabilities[mLoc] = "冷冻"
                                            layerType = "THREE_DOOR"
                                            layerCountPrompt = "$baseName 的冷冻室有几层？"
                                            showLayerPicker = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) { Text(opt) }
                        }
                    }
                },
                confirmButton = {}
            )
            
            if (showLayerPicker) {
                AlertDialog(
                    onDismissRequest = { showLayerPicker = false },
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showLayerPicker = false }) {
                                Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                            Text(layerCountPrompt)
                        }
                    },
                    text = {
                        Column {
                            (1..6).forEach { i ->
                                TextButton(onClick = {
                                    showLayerPicker = false
                                    if (i == 1) {
                                        val loc = if (layerType == "ONLY_FREEZE") baseName else "$baseName 冷冻室"
                                        finalLocations.add(loc); capabilities[loc] = "冷冻"
                                    } else {
                                        (1..i).forEach { idx ->
                                            val ord = when(idx) { 1 -> "第一"; 2 -> "第二"; 3 -> "第三"; 4 -> "第四"; 5 -> "第五"; 6 -> "第六"; else -> "$idx" }
                                            val loc = if (layerType == "ONLY_FREEZE") "$baseName ${ord}层" else "$baseName 冷冻室 ${ord}层"
                                            finalLocations.add(loc); capabilities[loc] = "冷冻"
                                        }
                                    }
                                    if (currentBaseIdx < fridgeBaseNames.size - 1) currentBaseIdx++ else step = 4
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${i}层")
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }
        4 -> {
            Scaffold(topBar = { 
                TopAppBar(
                    title = { Text("确认配置") },
                    navigationIcon = {
                        IconButton(onClick = { step = 2 }) {
                            Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "上一步")
                        }
                    }
                ) 
            }) { pv ->
                Column(modifier = Modifier.padding(pv).padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("检测到以下区域：", fontWeight = FontWeight.Bold)
                    finalLocations.forEach { Text("• $it (${capabilities[it]})") }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("食品分类：", fontWeight = FontWeight.Bold)
                    Text(categoriesList.joinToString(","))
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(onClick = {
                        viewModel.completeSetup(
                            finalLocations = finalLocations.toList(),
                            baseNames = fridgeBaseNames.toList(),
                            capabilities = capabilities.toMap(),
                            newCategories = categoriesList.toList(),
                            syncUrl = serverUrl
                        )
                    }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("完成并进入主界面")
                    }
                }
            }
        }
    }
}

@Composable
fun TransferDialog(
    item: com.example.coolbox.mobile.data.FoodEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val fridges by viewModel.fridges.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转移食品：${item.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                fridges.forEach { loc ->
                    TextButton(
                        onClick = {
                            viewModel.transferItem(item, loc)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(loc, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth(), color = if (loc == item.fridgeName) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemCard(item: com.example.coolbox.mobile.data.FoodEntity?, viewModel: MainViewModel, onIconClick: () -> Unit) {
    if (item == null) return // V2.6.2 Absolute Safety
    
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    
    val currentMs = System.currentTimeMillis()
    val expiryMs = item.expiryDateMs
    val isExpired = currentMs > expiryMs
    
    var showTransfer by remember { mutableStateOf(false) }
    
    if (showTransfer) {
        TransferDialog(item = item, viewModel = viewModel, onDismiss = { showTransfer = false })
    }

    val cardBg = if (isExpired) Color(0xFFFFEBEE) else Color.White
    val cardBorder = if (isExpired) Color(0xFFEF9A9A) else Color(0xFFE0E0E0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), // Reduced vertical spacing between cards
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(0.5.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp), // Tighter padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Task 2.1: Unified Icon Plate (No border, light background)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF5F5F5))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onIconClick() })
                    },
                contentAlignment = Alignment.Center
            ) {
                val resId = try {
                    val id = context.resources.getIdentifier(item.icon ?: "ic_food_default", "drawable", context.packageName)
                    if (id != 0) id else context.resources.getIdentifier("ic_food_default", "drawable", context.packageName)
                } catch (e: Exception) {
                    context.resources.getIdentifier("ic_food_default", "drawable", context.packageName)
                }
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Fit
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Task 2.2: Name, Remark, Info (Weight 1 to occupy middle)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name ?: "未命名食品",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isExpired) "已过期" else {
                            try { sdf.format(Date(item.expiryDateMs)) } catch(e: Exception) { "日期未知" }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (isExpired) Color(0xFFD32F2F) else Color(0xFF03A9F4) // Unified Primary Blue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.fridgeName ?: "未知位置",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (item.remark.isNotBlank()) {
                    Text(
                        text = item.remark,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Task 2.3: Quantitative & Actions (End block)
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val qtyStr = if(item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                    Text(
                        text = qtyStr,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFF03A9F4) // Unified Primary Blue
                        )
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = item.unit ?: "个",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.portions > 1) {
                        Surface(
                            onClick = { viewModel.takePortion(item, 1) },
                            color = Color(0xFF03A9F4).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "取1", 
                                color = Color(0xFF03A9F4),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        "转移",
                        modifier = Modifier.clickable { showTransfer = true },
                        color = Color(0xFF03A9F4),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_delete),
                        contentDescription = "全取",
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp).clickable { viewModel.deleteFood(item) }
                    )
                }
            }
        }
    }
}

