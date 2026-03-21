package com.example.coolbox.mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coolbox.mobile.data.FoodEntity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoolBoxTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F9FA)) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun CoolBoxTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF1A73E8),
        onPrimary = Color.White,
        surface = Color(0xFFF8F9FA),
        onSurface = Color(0xFF1A1A1A)
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun SortIconUI() {
    Column(
        modifier = Modifier.size(24.dp).padding(4.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(2.dp).background(Color.White, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.fillMaxWidth(0.4f).height(2.dp).background(Color.White, RoundedCornerShape(1.dp)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val inventory by viewModel.inventory.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState() 
    
    val currentSortMode by viewModel.currentSortMode.collectAsState()
    val isAscending by viewModel.isAscending.collectAsState()
    
    val prefs = context.getSharedPreferences("coolbox_settings", Context.MODE_PRIVATE)
    val isSetupComplete = prefs.getBoolean("setup_complete", false)
    
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1.0f)) }
    
    var showConfigDialog by remember { mutableStateOf(!isSetupComplete) }
    var showSortMenu by remember { mutableStateOf(false) } 

    // 提醒弹窗相关状态
    var showExpiryAlert by rememberSaveable { mutableStateOf(false) }
    var hasShownAlertSinceEntry by remember { mutableStateOf(false) }

    // 每次回到前台时重置弹窗标记
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 重启提醒标记，使其在 inventory 加载后能重新触发
                hasShownAlertSinceEntry = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (isSetupComplete) {
            viewModel.fetchInventory()
        }
    }

    // 核心逻辑：监听 inventory 变化或已显示标记重置
    LaunchedEffect(inventory, hasShownAlertSinceEntry) {
        if (inventory.isNotEmpty() && !hasShownAlertSinceEntry) {
            val now = System.currentTimeMillis()
            val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
            
            val expired = inventory.filter { it.expiryDateMs < now }
            val nearExpiry = inventory.filter { item ->
                val isExpired = item.expiryDateMs < now
                if (isExpired) return@filter false
                val shelfLife = item.expiryDateMs - item.inputDateMs
                val isWithinLastQuarter = shelfLife > 0 && now >= item.expiryDateMs - (shelfLife / 4)
                val isWithinThreeDays = now + threeDaysMs > item.expiryDateMs
                isWithinLastQuarter || isWithinThreeDays
            }
            
            if (expired.isNotEmpty() || nearExpiry.isNotEmpty()) {
                showExpiryAlert = true
            }
            hasShownAlertSinceEntry = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("CoolBox Mobile", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("V1.2-Pre41", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 2.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.fetchInventory() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { SortIconUI() }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val ascSymbol = if (isAscending) "↑" else "↓"
                            DropdownMenuItem(
                                text = { Text(if (currentSortMode == SortMode.EXPIRY) "按过期时间 ($ascSymbol)" else "按过期时间") },
                                onClick = { viewModel.setSortMode(SortMode.EXPIRY); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentSortMode == SortMode.LOCATION) "按存储位置 ($ascSymbol)" else "按存储位置") },
                                onClick = { viewModel.setSortMode(SortMode.LOCATION); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentSortMode == SortMode.NAME) "按物品名称 ($ascSymbol)" else "按物品名称") },
                                onClick = { viewModel.setSortMode(SortMode.NAME); showSortMenu = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A73E8))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A73E8))
            }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("搜索物品名称、备注或位置...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1A73E8),
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Text(
                text = "当前目标: ${SettingsManager.getServerUrl(context)} | 状态: $statusMessage",
                color = if (statusMessage.contains("失败")) Color.Red else Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            
            if (inventory.isEmpty() && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (searchQuery.isNotEmpty()) {
                            Text("没有找到匹配的物品 🤷‍♂️", fontSize = 18.sp, color = Color.Gray)
                        } else {
                            Text("列表空空如也 🤷‍♂️", fontSize = 20.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showConfigDialog = true }) {
                                Text("去配置 NAS 地址")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(inventory, key = { it.id }) { item ->
                        InventoryItemCard(item, fontScale)
                    }
                }
            }
        }

        if (showConfigDialog) {
            var urlInput by remember { mutableStateOf(SettingsManager.getServerUrl(context)) }
            var tempFontScale by remember { mutableFloatStateOf(fontScale) }
            
            AlertDialog(
                onDismissRequest = { 
                    if (prefs.getBoolean("setup_complete", false)) {
                        showConfigDialog = false 
                    }
                },
                title = { Text("偏好设置") },
                text = {
                    Column {
                        Text("NAS 服务器地址:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
                        )
                        
                        Text("全局列表字体大小: ${String.format(Locale.getDefault(), "%.1f", tempFontScale)}x", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = tempFontScale,
                            onValueChange = { tempFontScale = it },
                            valueRange = 0.8f..2.0f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("向左拖动变小，向右拖动变大", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        SettingsManager.setServerUrl(context, urlInput)
                        prefs.edit()
                            .putBoolean("setup_complete", true)
                            .putFloat("font_scale", tempFontScale)
                            .apply()
                        fontScale = tempFontScale
                        showConfigDialog = false
                        viewModel.fetchInventory() 
                    }) { Text("保存并同步") }
                },
                dismissButton = {
                    if (prefs.getBoolean("setup_complete", false)) {
                        TextButton(onClick = { showConfigDialog = false }) { Text("取消") }
                    }
                }
            )
        }

        if (showExpiryAlert) {
            // 在这一层预先计算一次内容，确定义显示列表
            val now = System.currentTimeMillis()
            val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
            val expired = inventory.filter { it.expiryDateMs < now }
            val nearExpiry = inventory.filter { item ->
                val shelfLife = item.expiryDateMs - item.inputDateMs
                val isExpired = item.expiryDateMs < now
                if (isExpired) return@filter false
                val isWithinLastQuarter = shelfLife > 0 && now >= item.expiryDateMs - (shelfLife / 4)
                val isWithinThreeDays = now + threeDaysMs > item.expiryDateMs
                isWithinLastQuarter || isWithinThreeDays
            }

            ExpiryAlertDialog(
                expiredItems = expired,
                nearExpiryItems = nearExpiry,
                fontScale = fontScale,
                onDismiss = { showExpiryAlert = false }
            )
        }
    }
}

@Composable
fun ExpiryAlertDialog(
    expiredItems: List<FoodEntity>,
    nearExpiryItems: List<FoodEntity>,
    fontScale: Float,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Red, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text("安全与临期提醒", fontWeight = FontWeight.Black, fontSize = (22 * fontScale).sp)
            }
        },
        text = {
            // 采用 Column + Scroll 确保在对话框内更稳健的布局
            Column(modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                if (expiredItems.isNotEmpty()) {
                    Text("以下食品已过期，请勿食用：", color = Color(0xFFA20000), fontWeight = FontWeight.Black, fontSize = (18 * fontScale).sp)
                    Spacer(Modifier.height(8.dp))
                    expiredItems.forEach { item ->
                        Text("• ${item.name} (${item.fridgeName})", color = Color(0xFFA20000), fontWeight = FontWeight.ExtraBold, fontSize = (16 * fontScale).sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                }
                
                if (nearExpiryItems.isNotEmpty()) {
                    Text("以下食品即将过期，请尽快食用：", color = Color(0xFFE65100), fontWeight = FontWeight.Black, fontSize = (18 * fontScale).sp)
                    Spacer(Modifier.height(8.dp))
                    nearExpiryItems.forEach { item ->
                        Text("• ${item.name} (${item.fridgeName})", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = (16 * fontScale).sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                modifier = Modifier.padding(8.dp).fillMaxWidth()
            ) {
                Text("我知道了", fontSize = (18 * fontScale).sp, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
fun InventoryItemCard(item: FoodEntity, fontScale: Float) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val expiryStr = try { sdf.format(Date(item.expiryDateMs)) } catch (e: Exception) { "未知" }
    
    val iconName = item.icon.ifBlank { "ic_food_default" }
    val iconResId = remember(iconName) {
        context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }

    val currentTime = System.currentTimeMillis()
    val shelfLife = item.expiryDateMs - item.inputDateMs
    
    val isExpired = currentTime > item.expiryDateMs
    val isWithinLastQuarter = shelfLife > 0 && currentTime >= item.expiryDateMs - (shelfLife / 4)
    val isWithinThreeDays = currentTime + 3 * 24 * 60 * 60 * 1000L > item.expiryDateMs
    val isNearExpiry = !isExpired && (isWithinLastQuarter || isWithinThreeDays)
    
    val cardBg = when {
        isExpired -> Color(0xFFA20000) // 深红
        isNearExpiry -> Color(0xFFFFFACD) // 淡黄
        else -> Color.White
    }
    
    val primaryTextColor = when {
        isExpired -> Color.White
        else -> Color.Black
    }
    
    val secondaryTextColor = when {
        isExpired -> Color.White
        else -> Color.Gray
    }

    val accentTextColor = when {
        isExpired -> Color.White
        isNearExpiry -> Color.Black
        else -> Color(0xFF1A73E8)
    }

    val remarkTextColor = when {
        isExpired -> Color.White
        isNearExpiry -> Color.Black
        else -> Color(0xFFE65100)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            
            val iconModifier = Modifier
                .size(60.dp)
                .padding(end = 12.dp)
            
            if (iconResId != 0) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = item.name,
                    modifier = iconModifier
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Fallback Icon",
                    tint = secondaryTextColor,
                    modifier = iconModifier
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name, 
                    fontWeight = FontWeight.Black, 
                    fontSize = (16 * fontScale).sp,
                    color = primaryTextColor
                )
                Text(
                    text = "${item.fridgeName} | ${item.category}", 
                    fontSize = (12 * fontScale).sp, 
                    color = secondaryTextColor
                )
                
                if (item.remark.isNotBlank()) {
                    Text(
                        text = item.remark, 
                        fontSize = (12 * fontScale).sp, 
                        color = remarkTextColor,
                        maxLines = 2, 
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    text = "保质期至: $expiryStr", 
                    fontSize = (12 * fontScale).sp, 
                    color = accentTextColor
                )
            }
            
            Text(
                text = "${if (item.quantity % 1.0 == 0.0) item.quantity.toInt() else String.format(Locale.getDefault(), "%.1f", item.quantity)} ${item.unit}", 
                fontWeight = FontWeight.Black, 
                fontSize = (20 * fontScale).sp, 
                color = accentTextColor
            )
        }
    }
}
