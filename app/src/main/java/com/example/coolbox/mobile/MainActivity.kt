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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    // 核心改动 1：读取本地保存的字体缩放比例，默认 1.0
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1.0f)) }
    
    var showConfigDialog by remember { mutableStateOf(!isSetupComplete) }
    var showSortMenu by remember { mutableStateOf(false) } 

    LaunchedEffect(Unit) {
        if (isSetupComplete) {
            viewModel.fetchInventory()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CoolBox Mobile", fontSize = 18.sp, color = Color.White) },
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
                    items(inventory) { item ->
                        // 核心改动 2：把字体缩放比例传给列表项
                        InventoryItemCard(item, fontScale)
                    }
                }
            }
        }

        if (showConfigDialog) {
            var urlInput by remember { mutableStateOf(SettingsManager.getServerUrl(context)) }
            // 弹窗里专用的滑块状态
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
                        
                        // 核心改动 3：增加字体大小调节滑块
                        Text("全局列表字体大小: ${String.format(Locale.getDefault(), "%.1f", tempFontScale)}x", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = tempFontScale,
                            onValueChange = { tempFontScale = it },
                            valueRange = 0.8f..2.0f,
                            steps = 11, // 支持 0.8 到 2.0 之间的 12 个档位
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("向左拖动变小，向右拖动变大", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        SettingsManager.setServerUrl(context, urlInput)
                        // 保存字体大小设置
                        prefs.edit()
                            .putBoolean("setup_complete", true)
                            .putFloat("font_scale", tempFontScale)
                            .apply()
                        fontScale = tempFontScale // 更新外层 UI
                        
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
    }
}

// 核心改动 4：接收 fontScale 参数，所有的 sp 单位都乘以这个倍数
@Composable
fun InventoryItemCard(item: FoodEntity, fontScale: Float) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val expiryStr = try { sdf.format(Date(item.expiryDateMs)) } catch (e: Exception) { "未知" }
    
    val iconName = item.icon ?: "ic_food_default"
    val iconResId = remember(iconName) {
        context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            
            // 核心改动 5：图标从 48.dp 放大到 60.dp (48 * 1.25)
            val iconModifier = Modifier.size(60.dp).padding(end = 12.dp)
            
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
                    tint = Color.Gray,
                    modifier = iconModifier
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // 名称：保持粗体 (FontWeight.Bold)，字号动态缩放
                Text(
                    text = item.name ?: "未知物品", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = (16 * fontScale).sp
                )
                Text(
                    text = "${item.fridgeName} | ${item.category}", 
                    fontSize = (12 * fontScale).sp, 
                    color = Color.Gray
                )
                
                if (!item.note.isNullOrBlank()) {
                    Text(
                        text = "${item.note}", 
                        fontSize = (12 * fontScale).sp, 
                        color = Color(0xFFE65100),
                        maxLines = 2, 
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    text = "保质期至: $expiryStr", 
                    fontSize = (12 * fontScale).sp, 
                    color = if (item.expiryDateMs < System.currentTimeMillis()) Color.Red else Color(0xFF1A73E8)
                )
            }
            
            // 数量：保持粗体 (FontWeight.Bold)，字号动态缩放
            Text(
                text = "${item.quantity.toInt()} ${item.unit}", 
                fontWeight = FontWeight.Bold, 
                fontSize = (20 * fontScale).sp, 
                color = Color(0xFF1A73E8)
            )
        }
    }
}