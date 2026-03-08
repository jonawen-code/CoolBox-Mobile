package com.example.coolbox.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
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
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSettings by remember { mutableStateOf(false) }

                    if (showSettings) {
                        SettingsScreen(onBack = { showSettings = false }, viewModel = viewModel)
                    } else {
                        val parsedResult by viewModel.parsedResult.collectAsState()
                        val asrReady by (asrHelper?.isReady ?: MutableStateFlow(false)).collectAsState()
                        
                        LaunchedEffect(parsedResult) {
                            // Voice confirmation removed in v1.0.22 as per user request
                        }

                        MainScreen(
                            viewModel = viewModel,
                            onSettingsClick = { showSettings = true }
                        )
                        
                        // --- v1.0.21+ Editable Result Dialog ---
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
    
    var showEntryDialog by remember { mutableStateOf(false) }
    var entryText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.syncAndRefresh() }
    )

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
                        Text(versionName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text("CoolBox Mobile") 
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Menu, contentDescription = "排序")
                    }
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
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
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
                }
                
                if (displayedItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isBlank()) "暂无食品数据\n尝试下拉同步云端数据" else "未找到相关食品",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(displayedItems) { item ->
                            FoodItemCard(item)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- v1.0.24 Pixel-Perfect Action Bar ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search Button (Beige with Orange border)
                    Surface(
                        onClick = { /* Search is auto-focused by field above normally, but we use a button here to match image */ },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFDF2E3).copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text("🔍", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("搜索库存", color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
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
                
                // Keep the hidden search field for logic (optional, or just update button to dialog)
                if (showEntryDialog.not()) {
                   // Logic for search exists in searchQuery state
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
                        Text("返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
        ) {
            Text("NAS 同步与备份", style = MaterialTheme.typography.titleMedium)
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
                        // Use the existing /api/food endpoint on NAS to check health
                        val base = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val client = okhttp3.OkHttpClient.Builder()
                                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                val request = okhttp3.Request.Builder()
                                    .url(base)
                                    .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                                    .build()
                                val response = client.newCall(request).execute()
                                withContext(Dispatchers.Main) {
                                    if (response.isSuccessful) {
                                        Toast.makeText(context, "✅ 连接成功! 服务器正常", Toast.LENGTH_LONG).show()
                                    } else if (response.code == 404) {
                                        // 404 means the endpoint exists but DB is empty, so network is actually fine
                                        Toast.makeText(context, "✅ 连接成功! (暂未收到同步数据)", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "❌ 连接失败: HTTP ${response.code}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    if (e is java.net.ConnectException || e is java.net.SocketTimeoutException) {
                                        Toast.makeText(context, "❌ 网络不通: 请检查 NAS IP ($serverUrl) 是否正确，且手机连接在同一局域网", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "❌ 连接异常: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("测试连接")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { 
                    SettingsManager.setServerUrl(context, serverUrl)
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            ) {
                Text("保存地址并返回")
            }
        }
    }
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
    var location by remember { mutableStateOf(result.location) }
    
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
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("存放位置") }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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
                        location = location,
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

@Composable
fun FoodItemCard(item: com.example.coolbox.mobile.data.FoodEntity) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val isExpired = System.currentTimeMillis() > item.expiryDateMs
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2E3)) // Warm Beige
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Icon Area (Notebook Style)
            Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                val resId = context.resources.getIdentifier(item.icon, "drawable", context.packageName).let {
                    if (it != 0) it else context.resources.getIdentifier("ic_food_pork", "drawable", context.packageName)
                }
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    contentScale = ContentScale.Fit
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content Column (Strict Table Alignment)
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Name (Remark) + Quantity Unit
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Row(modifier = Modifier.weight(0.7f), verticalAlignment = Alignment.Bottom) {
                        Text(text = item.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = Color.Black)
                        if (item.remark.isNotBlank()) {
                            Text(text = " (${item.remark})", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Row(modifier = Modifier.weight(0.3f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                        val qtyStr = if(item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                        Text(text = qtyStr, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = item.unit, style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = Color.Black)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Row 2: Location + Expiry
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(0.7f), verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = item.fridgeName, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = Color.Gray)
                    }
                    Text(
                        text = if (isExpired) "已过期" else "保质期至 ${sdf.format(Date(item.expiryDateMs))}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = if (isExpired) Color.Red else Color.Gray,
                        modifier = Modifier.weight(0.3f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
