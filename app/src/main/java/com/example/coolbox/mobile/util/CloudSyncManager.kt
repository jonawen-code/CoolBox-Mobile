package com.example.coolbox.mobile.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.widget.Toast
import com.example.coolbox.mobile.data.AppDatabase
import com.example.coolbox.mobile.data.FoodEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object CloudSyncManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun uploadDatabase(context: Context, serverUrl: String, onComplete: (Boolean) -> Unit = {}) {
        val base = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
        Thread {
            try {
                Log.d("CoolBoxSync", "Manual upload starting...")
                
                // Flush WAL
                val db = AppDatabase.getDatabase(context)
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)", emptyArray<Any>()).close()
                
                // CRITICAL: Close DB before upload to ensure WAL is merged and file is safe to read
                AppDatabase.closeDatabase()
                Thread.sleep(200)

                val currentDbFile = context.getDatabasePath("coolbox_database")
                if (!currentDbFile.exists()) {
                    onComplete(false); return@Thread
                }

                val requestBody = currentDbFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                val request = Request.Builder().url(base).post(requestBody).cacheControl(CacheControl.FORCE_NETWORK).build()

                client.newCall(request).execute().use { response ->
                    val success = response.isSuccessful
                    onComplete(success)
                }
            } catch (e: Exception) {
                Log.e("CoolBoxSync", "Upload failed", e)
                onComplete(false)
            }
        }.start()
    }

    fun syncBidirectional(context: Context, serverUrl: String, onComplete: (Boolean) -> Unit) {
        val base = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
        Thread {
            try {
                val request = Request.Builder().url(base).get().cacheControl(CacheControl.FORCE_NETWORK).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onComplete(false); return@Thread
                    }
                    val tempFile = File(context.cacheDir, "remote_merge.db")
                    response.body?.byteStream()?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }

                    val mergedCount = mergeDatabases(context, tempFile)
                    tempFile.delete()
                    
                    // Now UPLOAD the merged result
                    uploadDatabase(context, serverUrl) { uploadSuccess ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post { 
                            if (uploadSuccess) {
                                Toast.makeText(context, "双向同步成功 (合并 $mergedCount 条)", Toast.LENGTH_SHORT).show()
                                onComplete(true)
                            } else {
                                Toast.makeText(context, "合并完成，但上传 NAS 失败", Toast.LENGTH_SHORT).show()
                                onComplete(false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CoolBoxSync", "Merge failed", e)
                onComplete(false)
            }
        }.start()
    }

    private fun mergeDatabases(context: Context, remoteFile: File): Int {
        val localDb = AppDatabase.getDatabase(context)
        val remoteDb = SQLiteDatabase.openDatabase(remoteFile.path, null, SQLiteDatabase.OPEN_READONLY)
        var count = 0
        try {
            val cursor = remoteDb.rawQuery("SELECT * FROM food_items", null)
            val remoteItems = mutableListOf<FoodEntity>()
            
            // Robust column index finding
            val idxId = cursor.getColumnIndex("id")
            val idxIcon = cursor.getColumnIndex("icon")
            val idxName = cursor.getColumnIndex("name")
            val idxFridge = cursor.getColumnIndex("fridgeName")
            val idxInput = cursor.getColumnIndex("inputDateMs")
            val idxExpiry = cursor.getColumnIndex("expiryDateMs")
            val idxQty = cursor.getColumnIndex("quantity")
            val idxWeight = cursor.getColumnIndex("weightPerPortion")
            val idxPortions = cursor.getColumnIndex("portions")
            val idxCat = cursor.getColumnIndex("category")
            val idxUnit = cursor.getColumnIndex("unit")
            val idxRemark = cursor.getColumnIndex("remark")
            val idxModified = cursor.getColumnIndex("lastModifiedMs")
            val idxDeleted = cursor.getColumnIndex("isDeleted")

            if (cursor.moveToFirst()) {
                do {
                    remoteItems.add(FoodEntity(
                        id = cursor.getString(idxId),
                        icon = if (idxIcon >= 0) cursor.getString(idxIcon) else "",
                        name = cursor.getString(idxName),
                        fridgeName = cursor.getString(idxFridge),
                        inputDateMs = cursor.getLong(idxInput),
                        expiryDateMs = cursor.getLong(idxExpiry),
                        quantity = cursor.getDouble(idxQty),
                        weightPerPortion = cursor.getDouble(idxWeight),
                        portions = cursor.getInt(idxPortions),
                        category = cursor.getString(idxCat),
                        unit = cursor.getString(idxUnit),
                        remark = if (idxRemark >= 0) cursor.getString(idxRemark) else "",
                        lastModifiedMs = if (idxModified >= 0) cursor.getLong(idxModified) else 0L,
                        isDeleted = if (idxDeleted >= 0) cursor.getInt(idxDeleted) == 1 else false
                    ))
                } while (cursor.moveToNext())
            }
            cursor.close()

            val localDao = localDb.foodDao()
            val localItems = localDao.getAllItemsSync()
            
            val toUpdate = mutableListOf<FoodEntity>()
            remoteItems.forEach { remote ->
                val local = localItems.find { it.id == remote.id }
                if (local == null || remote.lastModifiedMs > local.lastModifiedMs) {
                    toUpdate.add(remote)
                    count++
                }
            }
            
            if (toUpdate.isNotEmpty()) {
                kotlinx.coroutines.runBlocking {
                    localDao.insertItems(toUpdate)
                }
            }
        } finally {
            remoteDb.close()
        }
        return count
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
