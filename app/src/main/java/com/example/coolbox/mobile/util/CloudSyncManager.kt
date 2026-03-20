package com.example.coolbox.mobile.util

import android.content.Context
import android.util.Log
import com.example.coolbox.mobile.data.AppDatabase
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object CloudSyncManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun downloadDatabase(context: Context, serverUrl: String, onComplete: (Boolean, String) -> Unit) {
        val request = Request.Builder().url(serverUrl).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onComplete(false, "连接NAS失败: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onComplete(false, "NAS拒绝: ${it.code}")
                        return
                    }
                    try {
                        // 1. 下载到临时文件
                        val tempFile = File(context.cacheDir, "temp_sync.db")
                        FileOutputStream(tempFile).use { output ->
                            it.body?.byteStream()?.copyTo(output)
                        }
                        Log.d("CloudSync", "临时库下载完成: ${tempFile.absolutePath}")

                        // 2. 获取句柄
                        val db = AppDatabase.getDatabase(context).openHelper.writableDatabase

                        // 3. 必须在事务外部 ATTACH
                        try { db.execSQL("DETACH DATABASE temp_db") } catch (e: Exception) {}
                        db.execSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS temp_db")

                        db.beginTransaction()
                        try {
                            // 4. 清空本地
                            db.execSQL("DELETE FROM food_items")

                            // 5. 像素级对齐：显式指定 14 个字段，严禁使用 SELECT *
                            // 这样可以彻底解决日期、总量错位的问题
                            db.execSQL("""
                                INSERT INTO food_items (
                                    id, icon, name, fridgeName, inputDateMs, expiryDateMs, 
                                    quantity, weightPerPortion, portions, category, unit, 
                                    remark, lastModifiedMs, isDeleted
                                ) 
                                SELECT 
                                    id, icon, name, fridgeName, inputDateMs, expiryDateMs, 
                                    quantity, weightPerPortion, portions, category, unit, 
                                    remark, lastModifiedMs, isDeleted 
                                FROM temp_db.food_items
                            """.trimIndent())

                            db.setTransactionSuccessful()
                            Log.d("CloudSync", "数据注入成功")
                        } finally {
                            db.endTransaction()
                            // 6. 必须卸载，否则下次同步会报 "database already in use"
                            try { db.execSQL("DETACH DATABASE temp_db") } catch (e: Exception) {}
                            tempFile.delete()
                        }

                        onComplete(true, "同步成功")

                    } catch (e: Exception) {
                        Log.e("CloudSync", "SQL 注入失败", e)
                        onComplete(false, "数据注入失败: ${e.message}")
                    }
                }
            }
        })
    }
}