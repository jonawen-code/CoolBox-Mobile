// Version: V3.0.0-Pre22
package com.example.coolbox.mobile.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.coolbox.mobile.SettingsManager
import com.example.coolbox.mobile.data.AppDatabase
import com.example.coolbox.mobile.data.FoodEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

object CloudSyncManager {
    @Volatile
    private var isSyncing = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Phase 1: Upload (Push)
    fun uploadDatabase(context: Context, serverUrl: String, onComplete: (Boolean) -> Unit = {}) {
        val base = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
        val nasBase = if (base.contains("/coolbox")) base else "$base/coolbox"
        
        Thread {
            try {
                Log.d("CoolBoxSync", "NAS Push starting: $nasBase")
                
                val db = AppDatabase.getDatabase(context)
                val dao = db.foodDao()
                val allItems = dao.getAllItemsSync()
                // V3.0.0-Pre21: 取消增量过滤，确保全量上传
                
                if (allItems.isEmpty()) {
                    Log.d("CoolBoxSync", "No local items to push.")
                    onComplete(true)
                    return@Thread
                }

                val jsonContent = gson.toJson(allItems)
                val timestamp = System.currentTimeMillis()
                val filename = "sync_mobile_$timestamp.json"
                
                val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$nasBase/sync/update")
                    .header("X-Filename", filename)
                    .post(requestBody)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    val isJsonSuccess = try {
                        JSONObject(bodyStr).optString("status") == "success"
                    } catch(e: Exception) { false }

                    if (response.isSuccessful && isJsonSuccess) {
                        SettingsManager.setLastSyncMs(context, timestamp)
                        Log.d("CoolBoxSync", "NAS Push success: $filename")
                        onComplete(true)
                    } else {
                        val err = "NAS Push failed | Code: ${response.code} | URL: ${request.url} | Resp: $bodyStr"
                        Log.e("CoolBoxSync", err)
                        ToastHelper.show(context, "上传失败 (${response.code})\n请检查后端路径或权限")
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("CoolBoxSync", "NAS Push Exception | URL: $nasBase", e)
                ToastHelper.show(context, "网络异常: ${e.localizedMessage}\n目标: $nasBase")
                onComplete(false)
            }
        }.start()
    }

    // Phase 2: Download (Pull) & Merge (V2.6.1 Hardened)
    fun syncBidirectional(context: Context, serverUrl: String, onComplete: (Boolean) -> Unit) {
        if (isSyncing) return
        isSyncing = true
        
        val base = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
        val nasBase = if (base.contains("/coolbox")) base else "$base/coolbox"
        
        // Use CoroutineScope for proper threading and cancellation
        kotlinx.coroutines.MainScope().launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.foodDao()
                
                // 1. Get All Remote Items directly from /sync/list (Spec v1.2)
                val allRemoteItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val url = "$nasBase/sync/list"
                    val listRequest = Request.Builder()
                        .url(url)
                        .get()
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .build()
                        
                    val items = mutableListOf<FoodEntity>()
                    client.newCall(listRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val jsonStr = response.body?.string() ?: ""
                            if (jsonStr.isNotBlank()) {
                                val type = object : TypeToken<List<FoodEntity>>() {}.type
                                try {
                                    val remoteList: List<FoodEntity> = gson.fromJson(jsonStr, type) ?: emptyList()
                                    items.addAll(remoteList.filterNotNull())
                                } catch (e: Exception) {
                                    Log.e("CoolBoxSync", "NAS Parse failed | URL: $url", e)
                                }
                            }
                        } else {
                            Log.e("CoolBoxSync", "NAS List failed | Code: ${response.code} | URL: $url")
                        }
                        Unit
                    }
                    items
                }

                // 3. One Transactional Merge on IO (雷区 1 修复)
                var mergedCount = 0
                if (allRemoteItems.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val localItems = dao.getAllItemsSync()
                        val toUpdate = mutableListOf<FoodEntity>()
                        
                        val localMap = localItems.associateBy { it.id }
                        
                        allRemoteItems.forEach { remote ->
                            val local = localMap[remote.id]
                            if (local == null || remote.lastModifiedMs > local.lastModifiedMs) {
                                toUpdate.add(remote)
                                mergedCount++
                            }
                        }
                        
                        if (toUpdate.isNotEmpty()) {
                            dao.insertItems(toUpdate) // Transactional batch insert
                        }
                    }
                }

                // 4. Final upload on IO
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    uploadDatabase(context, serverUrl) { success ->
                        kotlinx.coroutines.MainScope().launch {
                            if (success) {
                                Toast.makeText(context, "双向同步 V2.6.1 成功 (导入 $mergedCount 条)", Toast.LENGTH_SHORT).show()
                                onComplete(true)
                            } else {
                                Toast.makeText(context, "同步成功，但备份到服务器失败", Toast.LENGTH_SHORT).show()
                                onComplete(true)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("CoolBoxSync", "V2.6.1 Sync Failed", e)
                val errorMsg = when (e) {
                    is java.net.SocketTimeoutException -> "网络请求超时 (熔断)，请稍后重试"
                    is java.net.ConnectException -> "无法连接到服务器"
                    else -> "同步异常: ${e.localizedMessage}"
                }
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                onComplete(false)
            } finally {
                isSyncing = false
            }
        }
    }
}

object ToastHelper {
    fun show(context: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
// Version: V3.0.0-Pre22
